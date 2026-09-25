package com.byd.clusternav.hal;

import android.content.Context;
import android.content.ContextWrapper;
import android.hardware.bydauto.light.AbsBYDAutoLightListener;
import android.os.Looper;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * ═══ KACHI HAL HELPER — đọc xi-nhan dưới **uid shell**, phát ra socket 127.0.0.1 ═════════════════════════════
 *
 * Chạy NGOÀI tiến trình app, bằng {@code app_process} qua kênh dadb uid-2000:
 * <pre>
 * CLASSPATH=/data/local/tmp/kachi_hal.jar app_process / com.byd.clusternav.hal.KachiHalMain 19322
 * </pre>
 *
 * ── VÌ SAO PHẢI RA TIẾN TRÌNH RIÊNG ─────────────────────────────────────────────────────────────────────────
 * [ĐO xe 2026-09-25] {@code BYDAutoLightDevice.registerListener} gọi từ **uid app** ném
 * {@code SecurityException: android.permission.BYDAUTO_LIGHT_GET} — quyền đó {@code protectionLevel=signature},
 * chỉ {@code com.byd.*} có. Còn {@code getLightStatus(4/5)} thì gọi được nhưng trim này LUÔN trả 0 ⇒ không có
 * đường poll. Đường duy nhất còn lại: đăng ký listener dưới uid shell (privileged) rồi đẩy sự kiện về app qua
 * socket loopback — đúng mô hình **kinex** đã chạy được trên xe.
 *
 * ── RÀNG BUỘC MÔI TRƯỜNG (đọc kỹ trước khi sửa) ─────────────────────────────────────────────────────────────
 * Lớp này nạp bằng {@code app_process}, KHÔNG có Application/Activity của Kachi, KHÔNG có kotlin-stdlib trong
 * {@code CLASSPATH}. Vì thế:
 *   • viết bằng **Java thuần** (Kotlin sẽ kéo kotlin-stdlib vào dex — thêm ~1MB và một điểm hỏng runtime),
 *   • CẤM import bất cứ gì thuộc {@code com.byd.clusternav.*} khác (những lớp đó không có trong jar này),
 *   • chỉ dùng {@code java.*} + {@code android.*} của framework + {@code android.hardware.bydauto.*}.
 *
 * ── BỐN ĐƯỜNG TÍN HIỆU, MỘT HỢP ĐỒNG RA ─────────────────────────────────────────────────────────────────────
 * [ĐO 2026-09-25 · giải mã {@code kinex_hal_api_service.jar} → {@code HalLightListener}] xi-nhan tới qua **bốn**
 * callback khác nhau, không chỉ {@code onLightOn}:
 * <pre>
 *   onLightOn/Off(type)                → type 4=trái · 5=phải
 *   onTurnLightStateChanged(value)     → GỘP: 0/1=tắt cả hai · 2/3=trái · 4/5=phải
 *   onTurnLightStateChanged(id,value)  → TỪNG BÊN: id 1=trái · 2=phải; value!=0 ⇒ bật
 *   onTurnLightFlashStateChanged(value)→ cùng thang GỘP
 * </pre>
 * Phủ cả bốn là **có chủ ý**: trim này đã chứng minh không phơi state qua getter, nên không ai biết trước nó
 * chọn đường nào; bỏ ba đường kia là chấp nhận rủi ro "trên xe im lặng không chạy". Kinex phủ cả bốn và nó
 * chạy được — đây là sao chép một đường đã có bằng chứng, không phải phát minh.
 *
 * **Hợp đồng RA thì KHÔNG đổi**: mọi đường vào đều quy về đúng một dạng dòng cho client —
 * {@code {"topic":"light.onLightOn","type":4}\n} (hoặc {@code light.onLightOff}), type 4=trái/5=phải.
 *
 * ── GIAO THỨC ───────────────────────────────────────────────────────────────────────────────────────────────
 * ServerSocket trên {@code 127.0.0.1:<port>} (mặc định 19322), nhiều client, mỗi dòng một sự kiện JSON kết bằng
 * {@code \n}. Client vừa nối được gửi NGAY **ảnh chụp** trạng thái hiện tại — nếu không, client nối vào giữa lúc
 * xi-nhan đang bật sẽ tưởng là tắt cho tới lần nháy sau.
 *
 * Chỉ phát khi trạng thái ĐỔI (chống trùng: một lượt rẽ có thể fire cả {@code onLightOn(4)} lẫn
 * {@code onTurnLightStateChanged(2)} — cùng nghĩa, hai callback).
 */
public final class KachiHalMain {

    private static final String TAG = "KachiHal";
    private static final int DEFAULT_PORT = 19322;

    private static final String LIGHT_DEVICE = "android.hardware.bydauto.light.BYDAutoLightDevice";
    private static final String LIGHT_LISTENER = "android.hardware.bydauto.light.AbsBYDAutoLightListener";

    /** type của xi-nhan trong thang {@code onLightOn/Off} — [ĐO] 4=trái, 5=phải. */
    private static final int TYPE_LEFT = 4;
    private static final int TYPE_RIGHT = 5;

    private static final String TOPIC_ON = "light.onLightOn";
    private static final String TOPIC_OFF = "light.onLightOff";

    /** Danh sách client đang nối. Mọi truy cập trong {@code synchronized (CLIENTS)}. */
    private static final List<OutputStream> CLIENTS = new ArrayList<OutputStream>();

    private static volatile boolean left;
    private static volatile boolean right;

    private KachiHalMain() {
    }

    public static void main(String[] args) {
        final int port = parsePort(args);
        log("khởi động, port=" + port);

        // Looper TRƯỚC khi đăng ký: callback của HAL tới qua Binder/Handler, không có Looper thì đăng ký được
        // mà KHÔNG bao giờ fire — [ĐO] kinex HalApiServiceMain cũng prepareMainLooper() trước rồi loop().
        Looper.prepareMainLooper();

        if (!startServer(port)) {
            // Cổng đã có người giữ ⇒ gần như chắc chắn là một bản helper khác đang chạy. Thoát để bên gọi
            // (HalHelperLauncher) thấy cổng vẫn mở và coi như đã sẵn sàng.
            log("không bind được cổng " + port + " — có helper khác đang chạy? thoát.");
            System.exit(3);
            return;
        }

        registerLight();

        // Vòng vô hạn có nuốt ngoại lệ: một callback HAL ném lỗi KHÔNG được giết cả daemon (nếu chết thì
        // camera-theo-xi-nhan im lặng ngừng chạy tới lần nổ máy sau).
        while (true) {
            try {
                Looper.loop();
                log("Looper.loop() trả về — dừng.");
                return;
            } catch (Throwable t) {
                log("nuốt ngoại lệ từ main looper, tiếp tục: " + t);
            }
        }
    }

    private static int parsePort(String[] args) {
        if (args == null || args.length < 1 || args[0] == null) return DEFAULT_PORT;
        try {
            int p = Integer.parseInt(args[0].trim());
            return (p > 0 && p <= 65535) ? p : DEFAULT_PORT;
        } catch (NumberFormatException e) {
            log("port không hợp lệ '" + args[0] + "', dùng " + DEFAULT_PORT);
            return DEFAULT_PORT;
        }
    }

    // ── Đăng ký HAL ─────────────────────────────────────────────────────────────────────────────────────────

    /**
     * Đăng ký listener xi-nhan. Hỏng ⇒ CHỈ log: socket vẫn phải phục vụ để bên app phân biệt được
     * "helper không lên" với "helper lên nhưng HAL từ chối" (hai việc phải làm khác nhau).
     */
    private static void registerLight() {
        try {
            Context ctx = systemContext();
            Class<?> cls = Class.forName(LIGHT_DEVICE);
            Object dev = cls.getMethod("getInstance", Context.class).invoke(null, ctx);
            if (dev == null) {
                log("BYDAutoLightDevice.getInstance trả null — HAL chưa cấp device.");
                return;
            }
            Method register = cls.getMethod("registerListener", Class.forName(LIGHT_LISTENER));
            register.invoke(dev, new TurnListener());
            log("đăng ký AbsBYDAutoLightListener OK (dev=" + dev.getClass().getName() + ")");
        } catch (Throwable t) {
            log("đăng ký listener THẤT BẠI: " + t);
        }
    }

    /**
     * Context hệ thống qua {@code ActivityThread.systemMain().getSystemContext()}, bọc trong
     * {@link PermissiveContext}.
     *
     * [ĐO] kinex bọc y hệt (lớp {@code a.u}): device HAL tự gọi {@code checkPermission}/{@code enforce*} trên
     * Context được truyền vào, nên bọc để nó không tự chặn. Lấy không được ⇒ trả {@code null}: kinex cũng có
     * nhánh truyền {@code null} cho {@code getInstance(Context)} và device vẫn cấp.
     */
    private static Context systemContext() {
        try {
            Class<?> at = Class.forName("android.app.ActivityThread");
            Object thread = at.getMethod("systemMain").invoke(null);
            Context ctx = (Context) at.getMethod("getSystemContext").invoke(thread);
            if (ctx == null) {
                log("getSystemContext trả null — truyền null cho getInstance.");
                return null;
            }
            Context app = ctx.getApplicationContext();
            return new PermissiveContext(app != null ? app : ctx);
        } catch (Throwable t) {
            log("không lấy được system context (" + t + ") — truyền null.");
            return null;
        }
    }

    /** Context nói "cho phép" với mọi phép kiểm quyền — xem {@link #systemContext()}. */
    private static final class PermissiveContext extends ContextWrapper {
        PermissiveContext(Context base) {
            super(base);
        }

        @Override public int checkCallingOrSelfPermission(String permission) { return 0; }
        @Override public int checkCallingPermission(String permission) { return 0; }
        @Override public int checkSelfPermission(String permission) { return 0; }
        @Override public int checkPermission(String permission, int pid, int uid) { return 0; }
        @Override public void enforceCallingOrSelfPermission(String permission, String message) { }
        @Override public void enforceCallingPermission(String permission, String message) { }
        @Override public void enforcePermission(String permission, int pid, int uid, String message) { }
    }

    // ── Listener: bốn đường vào, một hợp đồng ra ────────────────────────────────────────────────────────────

    private static final class TurnListener extends AbsBYDAutoLightListener {

        @Override public void onLightOn(int type) {
            if (type == TYPE_LEFT || type == TYPE_RIGHT) set(type, true);
        }

        @Override public void onLightOff(int type) {
            if (type == TYPE_LEFT || type == TYPE_RIGHT) set(type, false);
        }

        @Override public void onTurnLightStateChanged(int value) {
            combined(value);
        }

        @Override public void onTurnLightFlashStateChanged(int value) {
            combined(value);
        }

        @Override public void onTurnLightStateChanged(int id, int value) {
            if (id == 1) set(TYPE_LEFT, value != 0);
            else if (id == 2) set(TYPE_RIGHT, value != 0);
        }

        // Ba cái dưới không dùng, nhưng PHẢI override: lớp thật có thể khai abstract ⇒ thiếu là
        // InstantiationError lúc chạy trong khi compile vẫn xanh. Xem KDoc của stub.
        @Override public void onLightAutoSwitchOn() { }
        @Override public void onLightAutoSwitchOff() { }
        @Override public void onAFSSwitchStateChange(int state) { }
    }

    /**
     * Giá trị GỘP → trạng thái hai bên. [ĐO] 0/1=tắt cả hai · 2/3=trái · 4/5=phải.
     *
     * ⚠ Sao chép ĐÚNG kinex, kể cả chỗ bất đối xứng: giá trị "trái bật" KHÔNG phát kèm "phải tắt". Không tự
     * thêm suy luận vào một bảng đã có bằng chứng chạy được; các đường {@code off} kia lo việc tắt.
     */
    private static void combined(int value) {
        if (value == 0 || value == 1) {
            set(TYPE_LEFT, false);
            set(TYPE_RIGHT, false);
        } else if (value == 2 || value == 3) {
            set(TYPE_LEFT, true);
        } else if (value == 4 || value == 5) {
            set(TYPE_RIGHT, true);
        } else {
            log("giá trị GỘP lạ: " + value);
        }
    }

    /** Cập nhật trạng thái một bên; chỉ phát khi ĐỔI (chống trùng giữa bốn đường callback). */
    private static void set(int type, boolean on) {
        if (type == TYPE_LEFT) {
            if (left == on) return;
            left = on;
        } else if (type == TYPE_RIGHT) {
            if (right == on) return;
            right = on;
        } else {
            return;
        }
        broadcast(on ? TOPIC_ON : TOPIC_OFF, type);
    }

    // ── Socket ─────────────────────────────────────────────────────────────────────────────────────────────

    /** @return true nếu bind + mở được vòng accept. */
    private static boolean startServer(final int port) {
        final ServerSocket server;
        try {
            server = new ServerSocket();
            server.setReuseAddress(true);
            server.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), port), 16);
        } catch (IOException e) {
            log("bind 127.0.0.1:" + port + " lỗi: " + e);
            return false;
        }
        Thread t = new Thread(new Runnable() {
            @Override public void run() {
                acceptLoop(server);
            }
        }, "KachiHalAccept");
        // KHÔNG daemon: vòng accept là lý do tồn tại của tiến trình này.
        t.setDaemon(false);
        t.start();
        log("đang phục vụ 127.0.0.1:" + port);
        return true;
    }

    private static void acceptLoop(ServerSocket server) {
        while (true) {
            try {
                Socket s = server.accept();
                try {
                    s.setTcpNoDelay(true);
                } catch (IOException ignored) {
                    // Nagle không tắt được thì vẫn chạy, chỉ trễ hơn vài chục ms.
                }
                OutputStream os = s.getOutputStream();
                synchronized (CLIENTS) {
                    CLIENTS.add(os);
                }
                log("client nối, tổng=" + clientCount());
                sendSnapshot(os);
            } catch (Throwable t) {
                log("accept lỗi: " + t);
                // Ngủ ngắn để một lỗi lặp lại không quay vòng 100% CPU.
                try {
                    Thread.sleep(200L);
                } catch (InterruptedException ie) {
                    return;
                }
            }
        }
    }

    /** Ảnh chụp trạng thái cho client vừa nối — xem KDoc lớp (lý do). */
    private static void sendSnapshot(OutputStream os) {
        boolean l = left;
        boolean r = right;
        try {
            os.write(line(l ? TOPIC_ON : TOPIC_OFF, TYPE_LEFT));
            os.write(line(r ? TOPIC_ON : TOPIC_OFF, TYPE_RIGHT));
            os.flush();
        } catch (IOException e) {
            drop(os);
        }
    }

    private static void broadcast(String topic, int type) {
        byte[] data = line(topic, type);
        synchronized (CLIENTS) {
            Iterator<OutputStream> it = CLIENTS.iterator();
            while (it.hasNext()) {
                OutputStream os = it.next();
                try {
                    os.write(data);
                    os.flush();
                } catch (IOException e) {
                    it.remove();
                    close(os);
                }
            }
        }
        log("phát " + topic + " type=" + type + " → " + clientCount() + " client");
    }

    /** Một dòng giao thức. {@code topic} là hằng nội bộ nên không cần escape. */
    private static byte[] line(String topic, int type) {
        String s = "{\"topic\":\"" + topic + "\",\"type\":" + type + "}\n";
        try {
            return s.getBytes("UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            return s.getBytes();
        }
    }

    private static void drop(OutputStream os) {
        synchronized (CLIENTS) {
            CLIENTS.remove(os);
        }
        close(os);
    }

    private static void close(OutputStream os) {
        try {
            os.close();
        } catch (IOException ignored) {
            // Đóng hỏng thì cũng đã bỏ khỏi danh sách; không còn gì làm thêm.
        }
    }

    private static int clientCount() {
        synchronized (CLIENTS) {
            return CLIENTS.size();
        }
    }

    private static void log(String msg) {
        System.out.println(TAG + ": " + msg);
        System.out.flush();
    }
}
