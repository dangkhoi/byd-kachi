package com.byd.clusternav.modules.clustercast;

import android.content.ComponentName;
import android.graphics.Rect;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * ★ T3 (RE DashCast 2026-07-19) — GIỮ STATE + RENDER ĐÚNG cho app khó (GMaps) khi T1(shell) + T2(XDJA mirror) không đủ.
 *
 * ONE-SHOT daemon chạy qua app_process ở uid-2000 (shell), KHÔNG cần ký platform:
 *   dadb: `CLASSPATH=$(pm path com.byd.clusternav) app_process64 /system/bin
 *          com.byd.clusternav.modules.clustercast.T3Daemon <pkg> <displayId> <l> <t> <r> <b>`
 * Vì sao cần app_process (không dùng lệnh `am`): reflection vào IActivityTaskManager (moveStackToDisplay /
 * setTaskWindowingMode / resizeTask) chỉ gọi được trong 1 tiến trình Java uid-2000; `am` shell KHÔNG có
 * lệnh set-windowing-mode cho task ĐANG CHẠY. app_process cho ta uid-2000 + JVM để reflection.
 *
 * Cascade (đúng DashCast Phase4TaskVerbs — A10/DiLink3 safe, KHÔNG có moveTaskToDisplay(int,int) trực tiếp):
 *   ① findTaskId(pkg)                 — IActivityTaskManager.getTasks(...) → task có topActivity.pkg == pkg
 *   ② setTaskWindowingMode(taskId,5,true)  — FREEFORM (bắt buộc trước moveStack + resize)
 *   ③ getAllStackInfos() → stackId chứa taskId + displayId hiện tại
 *   ④ moveStackToDisplay(stackId, displayId)  — bỏ qua nếu đã ở display đích
 *   ⑤ resizeTask(taskId, Rect, RESIZE_MODE_FORCED=1)  — ép composite lại (hết trắng)
 * Task ĐANG CHẠY được relocate (không kill → GIỮ phiên dẫn). Reflection thuần → compile không cần hidden-API;
 * app_process standalone không bị hidden-API enforcement (khác app qua ActivityThread).
 *
 * KHÔNG import class app-specific → chạy được ngoài context ứng dụng. In kết quả ra stdout để dadb đọc.
 */
public final class T3Daemon {

    private static final int WINDOWING_MODE_FREEFORM = 5;
    private static final int RESIZE_MODE_FORCED = 1;

    public static void main(String[] argv) {
        try {
            if (argv.length < 6) {
                System.out.println("T3 ERR args: <pkg> <displayId> <l> <t> <r> <b>");
                System.exit(2);
                return;
            }
            String pkg = argv[0];
            int displayId = Integer.parseInt(argv[1]);
            int l = Integer.parseInt(argv[2]);
            int t = Integer.parseInt(argv[3]);
            int r = Integer.parseInt(argv[4]);
            int b = Integer.parseInt(argv[5]);

            int taskId = findTaskId(pkg);
            System.out.println("T3 taskId=" + taskId + " pkg=" + pkg + " display=" + displayId
                    + " bounds=[" + l + "," + t + "," + r + "," + b + "]");
            if (taskId < 0) {
                System.out.println("T3 ERR: khong co task dang chay cho " + pkg + " (mo app + dan truoc)");
                System.exit(1);
                return;
            }
            System.out.println("T3 move: " + moveViaStack(taskId, displayId));
            // ★★ GUARD P0 (v0.36): resize CHỈ khi task ĐÚNG là đang ở display đích. Bản cũ in kết quả move rồi
            //   resize BẤT CHẤP — sau khi xe được tắt-mở (freeform sống thật), lệnh đó sẽ resize một task còn
            //   nằm ở display 0 = MÀN HÌNH GIỮA của tài xế, với toạ độ tính cho cụm. Đọc lại display thật rồi mới quyết.
            int after = displayOfTask(taskId);
            if (after != displayId) {
                System.out.println("T3 SKIP resize: task " + taskId + " dang o display " + after
                        + " (≠ " + displayId + ") — KHONG dung man giua");
            } else {
                System.out.println("T3 resize: " + resizeTask(taskId, l, t, r, b));
            }
            System.out.println("T3 DONE");
        } catch (Throwable e) {
            System.out.println("T3 FATAL: " + e.getClass().getSimpleName() + " — " + e.getMessage());
        } finally {
            // System.out là autoflush (println) nhưng flush tường minh trước exit cho chắc:
            // dadb đọc stdout của app_process; System.exit halt runtime ngay → tránh mất dòng cuối.
            System.out.flush();
            System.exit(0);
        }
    }

    // ── IActivityTaskManager qua reflection ──
    private static Object iAtm() throws Exception {
        Class<?> atm = Class.forName("android.app.ActivityTaskManager");
        return atm.getMethod("getService").invoke(null);
    }

    private static Object readField(Object target, String name) {
        try {
            Field f = target.getClass().getField(name);
            f.setAccessible(true);
            return f.get(target);
        } catch (Throwable ignore) {
            return null;
        }
    }

    private static Throwable unwrap(Throwable t) {
        return (t instanceof InvocationTargetException && t.getCause() != null) ? t.getCause() : t;
    }

    /** taskId host pkg (bất kỳ activity). -1 nếu không có. Duyệt mọi overload getTasks (biến thể BYD). */
    static int findTaskId(String pkg) {
        try {
            Object atm = iAtm();
            Method getTasks = null;
            for (Method cand : atm.getClass().getMethods()) {
                if ("getTasks".equals(cand.getName())) { getTasks = cand; break; }
            }
            if (getTasks == null) return -1;
            Class<?>[] pt = getTasks.getParameterTypes();
            Object[] args = new Object[pt.length];
            for (int i = 0; i < pt.length; i++) {
                if (pt[i] == int.class) args[i] = (i == 0 ? 64 : 0);
                else if (pt[i] == boolean.class) args[i] = false;
                else args[i] = null;
            }
            Object res = getTasks.invoke(atm, args);
            if (!(res instanceof List)) return -1;
            for (Object task : (List<?>) res) {
                if (task == null) continue;
                ComponentName top = (ComponentName) readField(task, "topActivity");
                ComponentName base = (ComponentName) readField(task, "baseActivity");
                String p = top != null ? top.getPackageName() : base != null ? base.getPackageName() : null;
                if (pkg.equals(p)) {
                    Object id = readField(task, "taskId");
                    if (id == null) id = readField(task, "id");
                    if (id instanceof Integer) return (Integer) id;
                }
            }
        } catch (Throwable ignore) {}
        return -1;
    }

    /** ② FREEFORM → ③ tìm stackId/display → ④ moveStackToDisplay. Trả log chuỗi. */
    static String moveViaStack(int taskId, int displayId) {
        StringBuilder log = new StringBuilder();
        try {
            Object atm = iAtm();
            // ② FREEFORM (thử setTaskWindowingMode rồi setCustomTaskWindowingMode)
            try {
                Method setWm;
                String which;
                try {
                    setWm = atm.getClass().getMethod("setTaskWindowingMode", int.class, int.class, boolean.class);
                    which = "setTaskWindowingMode";
                } catch (NoSuchMethodException nsm) {
                    setWm = atm.getClass().getMethod("setCustomTaskWindowingMode", int.class, int.class, boolean.class);
                    which = "setCustomTaskWindowingMode";
                }
                setWm.invoke(atm, taskId, WINDOWING_MODE_FREEFORM, true);
                log.append("OK ").append(which).append("(FREEFORM)");
            } catch (Throwable wm) {
                Throwable c = unwrap(wm);
                log.append("WARN setWindowingMode: ").append(c.getClass().getSimpleName()).append(" — ").append(c.getMessage());
            }
            log.append(" ; ");

            // ③ stackId + display hiện tại
            int stackId = -1, curDisplay = -1;
            try {
                Object res = atm.getClass().getMethod("getAllStackInfos").invoke(atm);
                List<?> stacks;
                if (res instanceof List) stacks = (List<?>) res;
                else if (res != null && res.getClass().isArray()) stacks = Arrays.asList((Object[]) res);
                else stacks = Collections.emptyList();
                for (Object si : stacks) {
                    if (si == null) continue;
                    Object sid = readField(si, "stackId");
                    Object did = readField(si, "displayId");
                    Object tids = readField(si, "taskIds");
                    if (tids instanceof int[]) {
                        for (int tt : (int[]) tids) {
                            if (tt == taskId) {
                                stackId = sid instanceof Integer ? (Integer) sid : -1;
                                curDisplay = did instanceof Integer ? (Integer) did : -1;
                                break;
                            }
                        }
                    }
                    if (stackId != -1) break;
                }
            } catch (Throwable look) {
                log.append("WARN getAllStackInfos: ").append(unwrap(look).getClass().getSimpleName()).append(" ; ");
            }
            log.append("stackId=").append(stackId).append(" curDisplay=").append(curDisplay).append(" ; ");
            if (stackId < 0) return log.append("ERR no stack for task=").append(taskId).toString();

            // ④ moveStackToDisplay (bỏ qua nếu đã ở đích)
            if (curDisplay == displayId) return log.append("SKIP (da o display ").append(displayId).append(")").toString();
            atm.getClass().getMethod("moveStackToDisplay", int.class, int.class).invoke(atm, stackId, displayId);
            log.append("OK moveStackToDisplay(").append(stackId).append(",").append(displayId).append(")");
            return log.toString();
        } catch (Throwable t) {
            Throwable c = unwrap(t);
            return log.append("ERR moveViaStack: ").append(c.getClass().getSimpleName()).append(" — ").append(c.getMessage()).toString();
        }
    }

    /**
     * displayId THẬT của [taskId] ngay lúc này (đọc lại getAllStackInfos sau khi move). -1 = không tìm thấy.
     * Dùng để GATE resizeTask: không bao giờ resize một task còn nằm ở display 0 (màn hình giữa của tài xế).
     */
    static int displayOfTask(int taskId) {
        try {
            Object atm = iAtm();
            Object res = atm.getClass().getMethod("getAllStackInfos").invoke(atm);
            List<?> stacks;
            if (res instanceof List) stacks = (List<?>) res;
            else if (res != null && res.getClass().isArray()) stacks = Arrays.asList((Object[]) res);
            else stacks = Collections.emptyList();
            for (Object si : stacks) {
                if (si == null) continue;
                Object did = readField(si, "displayId");
                Object tids = readField(si, "taskIds");
                if (tids instanceof int[]) {
                    for (int tt : (int[]) tids) {
                        if (tt == taskId) return did instanceof Integer ? (Integer) did : -1;
                    }
                }
            }
        } catch (Throwable ignore) {
            // đọc không được → trả -1 → phía gọi BỎ QUA resize (an toàn hơn resize mù)
        }
        return -1;
    }

    /** ⑤ resizeTask(taskId, Rect, FORCED). bounds toàn 0 = null = full display. Duyệt biến thể signature. */
    static String resizeTask(int taskId, int l, int t, int r, int b) {
        try {
            Object atm = iAtm();
            Rect bounds = (l == 0 && t == 0 && r == 0 && b == 0) ? null : new Rect(l, t, r, b);
            Method m = null;
            for (Method cand : atm.getClass().getMethods()) {
                if (!cand.getName().equals("resizeTask")) continue;
                Class<?>[] pt = cand.getParameterTypes();
                if (pt.length == 3 && pt[0] == int.class && pt[1] == Rect.class && pt[2] == int.class) { m = cand; break; }
                if (pt.length == 2 && pt[0] == int.class && pt[1] == Rect.class) m = cand;
            }
            if (m == null) return "ERR resizeTask: khong tim thay signature";
            Object[] args = (m.getParameterTypes().length == 3)
                    ? new Object[]{taskId, bounds, RESIZE_MODE_FORCED}
                    : new Object[]{taskId, bounds};
            m.invoke(atm, args);
            return "OK resizeTask(" + taskId + "," + (bounds == null ? "full" : bounds.toShortString()) + ")";
        } catch (Throwable ex) {
            Throwable c = unwrap(ex);
            return "ERR resizeTask: " + c.getClass().getSimpleName() + " — " + c.getMessage();
        }
    }

    private T3Daemon() {}
}
