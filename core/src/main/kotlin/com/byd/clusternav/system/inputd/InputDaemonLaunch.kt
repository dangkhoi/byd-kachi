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
     *
     * ## 1.69 — hai tham số MỚI, và vì sao chúng tồn tại
     * [ĐO xe 2026-09-16] (`docs/diagnostics/oncar-trace-2026-09-16b/README.md` §9.1): daemon **KHÔNG lên** trên xe
     * ở mọi lần mở app, và **không ai biết vì sao** — vì đúng dòng lệnh này ném stderr vào `/dev/null`. Một cơ
     * chế hỏng mà tự xoá bằng chứng hỏng của mình thì mỗi lượt xe lại bắt đầu từ con số không.
     *  • [logPath] ⇒ stdout+stderr của daemon vào một tệp THẬT. Đường dẫn do `:app` truyền vào và nó nằm ở thư
     *    mục log **ngoài thẻ** (`getExternalFilesDir/kachi-logs/`, xem `KachiLog`) — chỗ mà tiến trình shell
     *    uid-2000 GHI ĐƯỢC. (Thư mục riêng của app `/data/data/<pkg>/files` là 0700 của app-uid; đổ redirect vào
     *    đó là chính lệnh khởi động chết vì *Permission denied* ⇒ biến một đầu dò thành một hồi quy.)
     *  • [useNohup] ⇒ biến thể KHÔNG `nohup`. [CHƯA BIẾT] toybox trên DL3 có `nohup` hay không; nếu không có thì
     *    cả dòng lệnh chết ở từ đầu tiên. `… & ` một mình vẫn tách tiến trình khỏi phiên shell của dadb (dadb
     *    không cấp tty nên không có SIGHUP để mà né) — chốt bằng `which nohup` ([hasNohup]) chứ không đoán.
     *
     * Mặc định của hàm giữ NGUYÊN chuỗi trước 1.69 (golden test `InputDaemonLaunchTest` khoá byte).
     */
    fun launchCmd(
        apkPath: String,
        socketName: String = DEFAULT_SOCKET,
        logPath: String? = null,
        useNohup: Boolean = true,
    ): String {
        val runner = if (useNohup) "nohup app_process" else "app_process"
        val out = logPath?.takeIf { it.isNotBlank() } ?: "/dev/null"
        return "CLASSPATH=$apkPath $runner / $MAIN_CLASS $socketName </dev/null >$out 2>&1 &"
    }

    /**
     * Lệnh dò `nohup`. Chạy MỘT lần mỗi tiến trình (kết quả nhớ ở `InputDaemonClient`).
     *
     * Trả về **một từ** (`yes`/`no`) thay vì đường dẫn, vì lượt dò phải đúng cả khi chính `which` không có trên
     * ROM: khi đó vế `||` chạy và câu trả lời là `no` — chứ không phải một dòng lỗi mà bộ đọc phải đoán nghĩa.
     */
    const val WHICH_NOHUP: String = "which nohup >/dev/null 2>&1 && echo yes || echo no"

    /**
     * Máy đích có `nohup` không, đọc từ đầu ra của [WHICH_NOHUP]. PURE ⇒ test off-device.
     *
     * **Chỉ** `yes` mới là "có". Mọi đầu ra khác (rỗng · lỗi · shell không chạy được) ⇒ dùng biến thể `… &`:
     * dadb không cấp tty nên không có SIGHUP để mà né, tức biến thể ấy không mất gì; còn đoán nhầm theo hướng
     * *"chắc là có nohup"* thì cả dòng lệnh chết ngay ở **từ đầu tiên** và daemon không bao giờ lên — đúng triệu
     * chứng đang phải điều tra trên xe ([CHƯA BIẾT] cho tới khi có tệp log).
     */
    fun hasNohup(probeOutput: String): Boolean =
        probeOutput.lineSequence().map { it.trim() }.lastOrNull { it.isNotEmpty() } == "yes"

    /** Tên tệp nhật ký của MỘT lượt khởi động daemon (đặt cạnh `usage-*.log` trong `kachi-logs/`). */
    fun logFileName(stamp: Long): String = "inputd-$stamp.log"
}
