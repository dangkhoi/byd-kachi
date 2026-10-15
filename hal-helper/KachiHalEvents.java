package com.byd.clusternav.hal;

import android.content.Context;
import android.hardware.IBYDAutoEvent;
import android.hardware.IBYDAutoListener;
import android.hardware.bydauto.BYDAutoEventValue;
import android.os.Handler;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * ═══ 3.1.6 · HAL-EVENTS — phần SỰ KIỆN của helper (tách khỏi {@link KachiHalMain} — trần 500 dòng, soát Pass 6) ═══════════════
 *
 * Cùng tiến trình {@code app_process}, cùng ràng buộc môi trường như {@link KachiHalMain} (Java thuần · không {@code com.byd.clusternav.*}
 * khác · chỉ {@code java.*} + framework). Socket, danh sách client, ảnh chụp cho client mới và đường đèn ({@code light.*}) vẫn ở
 * {@link KachiHalMain}; lớp này giữ: dòng {@code sub} của client → HỢP tập xin → đăng ký/huỷ listener CHUNG trên từng device (main
 * looper) → phát {@code hal.reg} / {@code hal.ev}. Khoá dùng chung: {@code KachiHalMain.CLIENTS} (LAST / REG_LINES đọc trong ảnh chụp).
 */
final class KachiHalEvents {

    private KachiHalEvents() {
    }

    // ── 3.1.6 HAL-EVENTS: listener CHUNG cho mọi device, theo feature-id mà app XIN ─────────────────────────────────
    //
    // [ĐO nguồn fw 2606] `AbsBYDAutoDevice.registerListener(IBYDAutoListener, int[])` là phương thức CÔNG KHAI của lớp cha
    // (`AbsBYDAutoDevice.java:276-292`), không kiểm quyền Java nào; mỗi lần `postEvent` của device, `onPostEvent` gọi
    // `onDataChanged(IBYDAutoEvent)` cho đúng các listener đã xin id đó (`:221-236`). ⇒ MỘT lớp listener cài giao diện
    // `IBYDAutoListener`, dùng cho mọi device — không cần stub `AbsBYDAuto<X>Listener` cho từng loại (và không dính bẫy "lớp
    // thật khai abstract mà stub không khai"). `checkDeviceFeatures` (`:43-56`) bỏ CẢ LƯỢT nếu một id không thuộc device ⇒ lọc
    // trước bằng `BYDAutoDeviceFeaturesMap.getFeatureIdsFromDevice(type)` và báo id bị từ chối.
    //
    // Giao thức (thêm, tương thích hai chiều — client cũ bỏ qua topic lạ, helper cũ không đọc gì từ client):
    //   client → helper : `sub <fqn>=<id>,<id>;<fqn>=<id>…\n`  (rỗng sau `sub` = thôi xin hết). Mỗi dòng THAY tập cũ của client.
    //   helper → client : `{"topic":"hal.hello","type":2}` ngay khi nối (helper hiểu `sub`);
    //                     `{"topic":"hal.reg","type":<deviceType>,"dev":"<fqn>","ok":"<hex…>","rej":"<hex…>","err":"…"}` mỗi lần
    //                     tập đăng ký của một device ĐỔI;
    //                     `{"topic":"hal.ev","type":<featureId>,"dev":"<fqn>","v":<int>,"d":<double>}` mỗi lần GIÁ TRỊ đổi.
    // Tập đăng ký của mỗi device = HỢP các client đang xin; client đứt ⇒ rút phần của nó ⇒ hết người xin ⇒ huỷ listener (không
    // để bus đẩy sự kiện vô ích vào tiến trình này).

    static final int PROTOCOL = 2;
    static final String TOPIC_HELLO = "hal.hello";
    private static final String TOPIC_REG = "hal.reg";
    private static final String TOPIC_EV = "hal.ev";
    private static final String ABS_DEVICE = "android.hardware.bydauto.AbsBYDAutoDevice";
    private static final String FEATURES_MAP = "android.hardware.bydauto.BYDAutoDeviceFeaturesMap";

    static volatile Handler MAIN;

    /** Tập xin của từng client: fqn → id. Chỉ đọc/ghi trong {@code synchronized (SUBS)}. */
    private static final Map<OutputStream, Map<String, Set<Integer>>> SUBS = new HashMap<OutputStream, Map<String, Set<Integer>>>();

    /** Đăng ký đang giữ theo device (chỉ luồng main đọc/ghi). */
    private static final Map<String, DevReg> REGS = new HashMap<String, DevReg>();

    /** Giá trị cuối theo "fqn#id" (chống trùng + ảnh chụp cho client mới). Trong {@code synchronized (KachiHalMain.CLIENTS)}. */
    static final Map<String, String> LAST = new HashMap<String, String>();

    /** Giá trị thô cuối theo "fqn#id" ("v|d") — khoá CHỐNG TRÙNG (soát Pass 6: so với DÒNG là không bao giờ trùng ⇒ phát mọi sự kiện). Trong {@code synchronized (KachiHalMain.CLIENTS)}. */
    static final Map<String, String> LAST_VAL = new HashMap<String, String>();

    /** Dòng hal.reg cuối theo fqn (ảnh chụp cho client mới). Trong {@code synchronized (KachiHalMain.CLIENTS)}. */
    static final Map<String, String> REG_LINES = new HashMap<String, String>();

    private static final class DevReg {
        final String fqn;
        Object dev;
        int type = -1;
        Set<Integer> features;
        EventListener listener;
        Set<Integer> ids = new TreeSet<Integer>();
        String err = "";

        DevReg(String fqn) {
            this.fqn = fqn;
        }
    }

    /** Luồng đọc dòng `sub` của một client; EOF/lỗi = client đi ⇒ rút phần xin của nó. */
    static void startReader(final Socket s, final OutputStream os) {
        Thread t = new Thread(new Runnable() {
            @Override public void run() {
                try {
                    InputStream in = s.getInputStream();
                    BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"));
                    String line;
                    while ((line = r.readLine()) != null) {
                        if (line.startsWith("sub")) onSub(os, parseSub(line.substring(3).trim()));
                    }
                } catch (Throwable e) {
                    // đứt dây — xử lý chung ở dưới
                }
                synchronized (SUBS) {
                    SUBS.remove(os);
                }
                KachiHalMain.drop(os);
                postReconcile();
            }
        }, "KachiHalRead");
        t.setDaemon(true);
        t.start();
    }

    /** `fqn=id,id;fqn=id` → map. Mục hỏng bị bỏ (log), không làm hỏng cả dòng. */
    static Map<String, Set<Integer>> parseSub(String body) {
        Map<String, Set<Integer>> out = new HashMap<String, Set<Integer>>();
        if (body.isEmpty()) return out;
        for (String part : body.split(";")) {
            int eq = part.indexOf('=');
            if (eq <= 0) continue;
            String fqn = part.substring(0, eq).trim();
            if (!fqn.startsWith("android.hardware.bydauto.")) {
                KachiHalMain.log("sub: bỏ device lạ '" + fqn + "'");
                continue;
            }
            Set<Integer> ids = new TreeSet<Integer>();
            for (String n : part.substring(eq + 1).split(",")) {
                try {
                    if (!n.trim().isEmpty()) ids.add(Integer.parseInt(n.trim()));
                } catch (NumberFormatException e) {
                    KachiHalMain.log("sub: bỏ id lạ '" + n + "'");
                }
            }
            if (!ids.isEmpty()) out.put(fqn, ids);
        }
        return out;
    }

    private static void onSub(OutputStream os, Map<String, Set<Integer>> want) {
        synchronized (SUBS) {
            SUBS.put(os, want);
        }
        KachiHalMain.log("sub nhận: " + want.size() + " device");
        postReconcile();
    }

    private static void postReconcile() {
        Handler h = MAIN;
        if (h == null) return;
        h.post(new Runnable() {
            @Override public void run() {
                try {
                    reconcile();
                } catch (Throwable t) {
                    KachiHalMain.log("reconcile ném (bỏ qua): " + t);
                }
            }
        });
    }

    /** Luồng main: đưa đăng ký của từng device về đúng HỢP các tập xin. */
    private static void reconcile() {
        Map<String, Set<Integer>> union = new HashMap<String, Set<Integer>>();
        synchronized (SUBS) {
            for (Map<String, Set<Integer>> m : SUBS.values()) {
                for (Map.Entry<String, Set<Integer>> e : m.entrySet()) {
                    Set<Integer> u = union.get(e.getKey());
                    if (u == null) union.put(e.getKey(), u = new TreeSet<Integer>());
                    u.addAll(e.getValue());
                }
            }
        }
        Set<String> all = new HashSet<String>(union.keySet());
        all.addAll(REGS.keySet());
        for (String fqn : all) {
            Set<Integer> want = union.get(fqn);
            apply(fqn, want == null ? new TreeSet<Integer>() : want);
        }
    }

    private static void apply(String fqn, Set<Integer> want) {
        DevReg reg = REGS.get(fqn);
        if (reg == null) {
            if (want.isEmpty()) return;
            reg = new DevReg(fqn);
            REGS.put(fqn, reg);
            resolve(reg);
        }
        if (reg.dev == null) {
            publishReg(reg, new TreeSet<Integer>(), want);
            return;
        }
        Set<Integer> ok = new TreeSet<Integer>();
        Set<Integer> rej = new TreeSet<Integer>();
        for (Integer id : want) {
            if (reg.features == null || reg.features.contains(id)) ok.add(id);
            else rej.add(id);
        }
        if (ok.equals(reg.ids) && rej.isEmpty()) return;
        // Id thôi đăng ký ⇒ quên giá trị cuối của nó: không được gửi lại cho client sau như thể vẫn còn đúng.
        synchronized (KachiHalMain.CLIENTS) {
            for (Integer id : reg.ids) if (!ok.contains(id)) { LAST.remove(fqn + "#" + id); LAST_VAL.remove(fqn + "#" + id); }
        }
        try {
            Class<?> abs = Class.forName(ABS_DEVICE);
            if (!reg.ids.isEmpty()) {
                abs.getMethod("unregisterListener", IBYDAutoListener.class).invoke(reg.dev, reg.listener);
                reg.ids = new TreeSet<Integer>();
            }
            if (!ok.isEmpty()) {
                // Cả HỢP trong MỘT lượt: native `enableDevice(type, ids)` thêm hay thay tập [CHƯA BIẾT] — một lượt đủ tập đúng cả hai.
                abs.getMethod("registerListener", IBYDAutoListener.class, int[].class).invoke(reg.dev, reg.listener, toArray(ok));
                reg.ids = ok;
            }
            reg.err = "";
        } catch (Throwable t) {
            reg.err = String.valueOf(root(t));
            synchronized (KachiHalMain.CLIENTS) {
                for (Integer id : want) { LAST.remove(fqn + "#" + id); LAST_VAL.remove(fqn + "#" + id); }
            }
            reg.ids = new TreeSet<Integer>();
            ok = new TreeSet<Integer>();
            rej = new TreeSet<Integer>(want);
        }
        publishReg(reg, ok, rej);
    }

    /** Tay cầm device + loại + tập feature của nó. Hỏng ⇒ {@code reg.dev == null}, lý do ở {@code reg.err}. */
    private static void resolve(DevReg reg) {
        try {
            Class<?> cls = Class.forName(reg.fqn);
            reg.dev = cls.getMethod("getInstance", Context.class).invoke(null, KachiHalMain.systemContext());
            if (reg.dev == null) {
                reg.err = "getInstance=null";
                return;
            }
            reg.type = ((Integer) Class.forName(ABS_DEVICE).getMethod("getDevicetype").invoke(reg.dev)).intValue();
            reg.listener = new EventListener(reg.fqn);
            try {
                Object set = Class.forName(FEATURES_MAP).getMethod("getFeatureIdsFromDevice", int.class).invoke(null, reg.type);
                if (set instanceof Set && !((Set<?>) set).isEmpty()) {
                    Set<Integer> f = new HashSet<Integer>();
                    for (Object o : (Set<?>) set) if (o instanceof Integer) f.add((Integer) o);
                    reg.features = f;
                }
            } catch (Throwable t) {
                KachiHalMain.log("không đọc được bảng feature của " + reg.fqn + " (" + t + ") — để HAL tự lọc");
            }
        } catch (Throwable t) {
            reg.dev = null;
            reg.err = String.valueOf(root(t));
        }
    }

    private static void publishReg(DevReg reg, Set<Integer> ok, Set<Integer> rej) {
        String line = "{\"topic\":\"" + TOPIC_REG + "\",\"type\":" + reg.type + ",\"dev\":\"" + reg.fqn + "\",\"ok\":\""
            + hex(ok) + "\",\"rej\":\"" + hex(rej) + "\",\"err\":\"" + clean(reg.err) + "\"}\n";
        synchronized (KachiHalMain.CLIENTS) {
            REG_LINES.put(reg.fqn, line);
        }
        KachiHalMain.log("đăng ký " + reg.fqn + " type=" + reg.type + " ok=[" + hex(ok) + "] rej=[" + hex(rej) + "] err=" + reg.err);
        KachiHalMain.broadcastRaw(KachiHalMain.bytes(line));
    }

    /** Listener chung — một bản mỗi device (để biết fqn). Cài ĐỦ ba phương thức của giao diện thật. */
    private static final class EventListener implements IBYDAutoListener {
        private final String fqn;

        EventListener(String fqn) {
            this.fqn = fqn;
        }

        @Override public void onDataChanged(IBYDAutoEvent e) {
            if (e == null) return;
            int id = e.getEventType();
            int v = e.getValue();
            double d = e.getDoubleValue();
            String key = fqn + "#" + id;
            String val = v + "|" + d;
            String line = "{\"topic\":\"" + TOPIC_EV + "\",\"type\":" + id + ",\"dev\":\"" + fqn + "\",\"v\":" + v
                + ",\"d\":" + (Double.isNaN(d) || Double.isInfinite(d) ? "0" : String.valueOf(d)) + "}\n";
            boolean first;
            synchronized (KachiHalMain.CLIENTS) {
                String prev = LAST_VAL.get(key);
                if (val.equals(prev)) return;   // chống trùng: chỉ phát khi giá trị ĐỔI
                first = prev == null;
                LAST_VAL.put(key, val);
                LAST.put(key, line);
            }
            if (first) KachiHalMain.log("sự kiện đầu " + fqn + " id=0x" + Integer.toHexString(id) + " v=" + v + " d=" + d);
            KachiHalMain.broadcastRaw(KachiHalMain.bytes(line));
        }

        @Override public void onDataEventChanged(int featureId, BYDAutoEventValue value) {
            // cùng sự kiện với onDataChanged (`AbsBYDAutoDevice.onPostEvent` gọi cả hai) — bỏ để không phát đôi
        }

        @Override public void onError(int code, String message) {
            KachiHalMain.log("onError " + fqn + " code=" + code + " msg=" + message);
        }
    }

    private static int[] toArray(Set<Integer> s) {
        int[] a = new int[s.size()];
        int i = 0;
        for (Integer x : s) a[i++] = x.intValue();
        return a;
    }

    private static String hex(Set<Integer> s) {
        StringBuilder b = new StringBuilder();
        for (Integer x : s) {
            if (b.length() > 0) b.append(',');
            b.append(Integer.toHexString(x.intValue()));
        }
        return b.toString();
    }

    private static String clean(String s) {
        return s == null ? "" : s.replace('"', '\'').replace('\\', '/').replace('\n', ' ');
    }

    private static Throwable root(Throwable t) {
        Throwable r = t;
        while (r.getCause() != null && r.getCause() != r) r = r.getCause();
        return r;
    }

}
