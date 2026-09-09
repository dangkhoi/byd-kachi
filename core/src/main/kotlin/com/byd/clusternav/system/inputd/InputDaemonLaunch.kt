package com.byd.clusternav.system.inputd

/**
 * Bộ DỰNG LỆNH khởi động input-daemon (scrcpy-style, KHÔNG build jar riêng). PURE JVM (:core) → golden test khoá
 * byte off-device.
 *
 * Daemon = một class NẰM TRONG chính APK ([MAIN_CLASS]) chạy bằng `app_process` ở tiến trình uid-2000 (shell) —
 * nó cần quyền `INJECT_EVENTS` của shell mà uid app KHÔNG có. `app_process` nạp lớp từ `CLASSPATH` = đường APK đã
 * cài (base.apk), rồi gọi `main()` của [MAIN_CLASS] với 1 tham số = tên socket localabstract.
 *
 * Lệnh chạy NỀN (`nohup … &`, redirect stdin/stdout/stderr về /dev/null) để lời gọi shell (qua `ShellTransport`,
 * BLOCKING trên 1 owner-thread) TRẢ VỀ NGAY — daemon thường trú sống tiếp mà KHÔNG treo owner-thread của
 * ShellTransport (một tiến trình không-kết-thúc chạy foreground sẽ khoá cả hàng đợi cửa sổ/cast).
 */
object InputDaemonLaunch {
    /** Lớp có `main()` chạy bằng app_process (đóng gói trong dex của APK :app). */
    const val MAIN_CLASS: String = "com.byd.clusternav.system.inputd.InputDaemonMain"

    /** Tên socket localabstract mặc định (client kết nối cùng tên). */
    const val DEFAULT_SOCKET: String = "kachi_input"

    /**
     * Lệnh shell khởi động daemon:
     * `CLASSPATH=<apk> nohup app_process / <MAIN_CLASS> <socket> </dev/null >/dev/null 2>&1 &`
     *
     *  • [apkPath] = đường base.apk đã cài (PackageManager `sourceDir`) — shell uid đọc được (/data/app
     *    world-readable). Prefix `CLASSPATH=` áp cho `nohup`, được kế thừa xuống `app_process`.
     *  • `/` = cmd-dir giả (đối số bắt buộc của app_process, như scrcpy) — lớp nạp từ `CLASSPATH`.
     *  • `nohup … &` + redirect = chạy nền, sống qua khi shell thoát ⇒ `ShellTransport.run` trả về ngay.
     */
    fun launchCmd(apkPath: String, socketName: String = DEFAULT_SOCKET): String =
        "CLASSPATH=$apkPath nohup app_process / $MAIN_CLASS $socketName </dev/null >/dev/null 2>&1 &"
}
