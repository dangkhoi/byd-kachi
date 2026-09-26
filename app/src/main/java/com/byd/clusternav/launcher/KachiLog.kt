package com.byd.clusternav.launcher

import android.content.Context
import android.os.Process
import android.util.Log
import java.io.File

/**
 * ═══ GHI LOG RA THẺ NHỚ (owner 2026-09-15) ════════════════════════════════════════════════════════════════
 *
 * Owner: *"đưa log ra 1 folder ở SDCard cho khỏi nặng head unit; toàn bộ log — test chức năng + log trong quá
 * trình dùng — để có nhiều thông tin patch nếu lỗi."*
 *
 * Thư mục: `getExternalFilesDir(null)/kachi-logs/` =
 * `/sdcard/Android/data/com.byd.launcher/files/kachi-logs/`. Vì sao chỗ này:
 *  - **Trên thẻ (external)**, KHÔNG phải bộ nhớ trong app ⇒ nhẹ đầu xe.
 *  - Đọc qua `adb pull` **KHÔNG cần root** (thư mục external của chính app).
 *  - Tự xoá khi gỡ app; [DiagStorageCap] quét cả cây external nên không phình vô hạn.
 *
 * Ba tệp:
 *  - `captest-report.txt` — kết quả kiểm tra từng nút (tự lưu mỗi lần chấm + khi bấm Xuất báo cáo).
 *  - `usage-<ts>.log` — logcat CỦA CHÍNH app (theo pid) chạy nền suốt phiên = "log trong quá trình dùng".
 *  - `snapshot-<ts>.log` — chụp một phát toàn bộ logcat gần đây (gồm cả hệ thống) khi bấm — cho lỗi cần ngữ cảnh rộng.
 *
 * **Lấy về:** `adb pull /sdcard/Android/data/com.byd.launcher/files/kachi-logs/ ./kachi-logs/`
 */
object KachiLog {
    private const val TAG = "KachiLog"
    private const val FOLDER = "kachi-logs"

    /** Trần một tệp usage MỖI PHIÊN — chặn log chạy vòng làm phình thẻ (DiagStorageCap dọn giữa các phiên). */
    private const val USAGE_CAP_BYTES = 8L * 1024 * 1024

    @Volatile private var capturing = false

    /** Thư mục log trên thẻ (tạo nếu chưa có). `null` = máy không gắn được external. */
    fun dir(ctx: Context): File? =
        ctx.applicationContext.getExternalFilesDir(null)?.let { File(it, FOLDER).apply { mkdirs() } }

    /** Đường dẫn để hướng dẫn `adb pull`. Vắng thẻ ⇒ trả đường dẫn quy ước (ASCII, không dịch — ca hiếm). */
    fun pullPath(ctx: Context): String =
        dir(ctx)?.absolutePath ?: "/sdcard/Android/data/com.byd.launcher/files/$FOLDER"

    /** Lệnh adb lấy log về (hiện trên màn để owner sao chép). */
    fun pullCommand(ctx: Context): String = "adb pull ${pullPath(ctx)}/ ./kachi-logs/"

    /** Lưu báo cáo kiểm tra từng nút ra thẻ (ghi đè). Trả tệp hoặc `null` nếu ghi hỏng. */
    fun saveCaptestReport(ctx: Context, report: String): File? = runCatching {
        val f = File(dir(ctx) ?: return null, "captest-report.txt")
        f.writeText(report)
        f
    }.onFailure { Log.w(TAG, "save report failed: ${it.message}") }.getOrNull()

    /**
     * Bắt đầu ghi logcat CỦA CHÍNH app ra thẻ (idempotent — gọi nhiều lần chỉ chạy một luồng). Gọi lúc mở app.
     *
     * `--pid` = pid của mình: Android 10 cho app đọc log của CHÍNH nó mà KHÔNG cần `READ_LOGS` (chỉ log app khác
     * mới cần quyền đó). Đọc từng dòng, flush ngay để log sống sót cả khi app chết (đúng thứ cần khi "patch nếu lỗi").
     */
    fun startCapture(ctx: Context) {
        if (capturing) return
        val d = dir(ctx) ?: return
        capturing = true
        Thread({
            runCatching {
                val out = File(d, "usage-${System.currentTimeMillis()}.log")
                val proc = Runtime.getRuntime().exec(arrayOf("logcat", "-v", "time", "--pid=${Process.myPid()}"))
                var written = 0L
                val throttle = LogLineThrottle()
                proc.inputStream.bufferedReader().use { r ->
                    out.bufferedWriter().use { w ->
                        var line = r.readLine()
                        var lastFlush = System.currentTimeMillis()
                        while (line != null) {
                            val src = line
                            val now = System.currentTimeMillis()
                            val toWrite = throttled(src, now, throttle)
                            if (toWrite != null) {
                                w.write(toWrite); w.newLine()
                                written += toWrite.length + 1
                                KachiPerf.add(KachiPerf.Counter.LOG_BYTES, (toWrite.length + 1).toLong())
                            }
                            // Nhịp xả tính trên dòng GỐC: một dòng bị tiết chế vẫn là một dòng vừa trôi qua, nên
                            // cửa sổ mất-mát-tối-đa của H3 không được giãn ra vì ta bỏ bớt dòng.
                            if (mustFlushNow(src, now - lastFlush)) { w.flush(); lastFlush = now }
                            if (written >= USAGE_CAP_BYTES) { runCatching { proc.destroy() }; break }
                            line = r.readLine()
                        }
                        runCatching { w.flush() }
                    }
                }
            }.onFailure { Log.w(TAG, "usage capture stopped: ${it.message}") }
            capturing = false
        }, "KachiLogCapture").apply { isDaemon = true }.start()
    }

    /**
     * ═══ LOG-41KB — dòng nào thật sự phải xuống thẻ ═══════════════════════════════════════════════════════════
     *
     * Trả dòng cần ghi (có thể kèm hậu tố *"[+N lặp]"*), hoặc `null` = bỏ. Luật + số đo ở KDoc [LogLineThrottle];
     * ở đây chỉ hai quyết định thuộc về tầng này:
     *
     *  1. **W/E/F/A không bao giờ bị bỏ.** Đây đúng là tập dòng mà [startCapture] tồn tại để cứu (*"có ngữ cảnh
     *     khi patch lỗi trên xe"*, xem [mustFlushNow]); và một cảnh báo lặp lại 100 lần là **thông tin** (nó
     *     đang lặp), không phải rác. Dòng W/E còn có `ShellRunFailureLog` tiết chế ở chính chỗ sinh ra nó.
     *  2. **Khoá = phần sau dấu thời gian** ([SEVERITY_COL] là cột đầu của phần đó trong `logcat -v time`): cùng
     *     một câu ở hai giây khác nhau phải ra cùng một khoá, nếu không thì không có gì trùng để mà tiết chế.
     *  3. **Dòng KHÔNG phải đầu bản ghi thì đi thẳng** ([isRecordHead]) — [SOÁT Pass 3 · P2 · 2026-09-26]. Một
     *     `Log.w(TAG, msg, throwable)` ra **nhiều dòng**: dòng đầu có mức ở [SEVERITY_COL], còn mọi dòng
     *     `\tat com.byd…` của vết gọi thì **không có dấu thời gian, không có mức**. Đưa chúng vào bộ tiết chế là
     *     đúng cái bẫy mà luật 1 lập ra để tránh: hai ngoại lệ khác nhau trong cùng 10 s thường **trùng phần
     *     đuôi vết gọi** (`at java.lang.Thread.run(…)`) ⇒ dòng W qua được mà vết gọi bị cắt giữa, và người đọc
     *     log không có cách nào biết nó bị cắt ở đâu. Một dòng phụ **thuộc về** bản ghi phía trên nó, nên nó
     *     thừa hưởng mức của bản ghi ấy: không khoá, không đếm, luôn ghi. Dòng mốc của logcat
     *     (`--------- beginning of main`) cũng đi qua đường này — nó là một dữ kiện về phiên, không phải rác.
     *
     * THUẦN (đồng hồ truyền vào) ⇒ `KachiLogThrottleTest` khoá cả bốn nhánh off-device.
     */
    fun throttled(line: String, nowMs: Long, throttle: LogLineThrottle): String? {
        if (!isRecordHead(line)) return line
        if (line[SEVERITY_COL] in SEVERITY_FLUSH_NOW) return line
        val skipped = throttle.suppressedBefore(line.substring(SEVERITY_COL), nowMs) ?: return null
        return line + LogLineThrottle.repeatSuffix(skipped)
    }

    /**
     * Dòng này có phải **đầu một bản ghi** `logcat -v time` (`09-26 18:12:30.100 D/Tag(17149): …`) hay không.
     *
     * Hình dạng đủ chặt và đủ rẻ: ký tự mức ở [SEVERITY_COL] thuộc [SEVERITY_ALL] **và** ngay sau nó là `/` (tên
     * tag). Mọi dòng khác là dòng phụ của bản ghi trên nó (vết gọi ngoại lệ, chuỗi nhiều dòng) hoặc dòng mốc của
     * chính logcat — xem luật 3 ở KDoc [throttled].
     */
    private fun isRecordHead(line: String): Boolean =
        line.length > SEVERITY_COL + 1 && line[SEVERITY_COL] in SEVERITY_ALL && line[SEVERITY_COL + 1] == '/'

    /**
     * ═══ H3 (PERF 2026-09-16) — có phải ghi xuống thẻ NGAY ở dòng này không ═══════════════════════════════════
     *
     * [ĐO xe 2026-09-16]: app tự ghi **79 KB/phút** ra thẻ, và bản cũ gọi `flush()` sau **MỖI dòng** ⇒ mỗi dòng
     * log là một lượt `write(2)` thật xuống thẻ (≈1 400 lượt/phút lúc cao điểm), phá sạch tác dụng của
     * `BufferedWriter`.
     *
     * Nhưng KHÔNG được bỏ hẳn `flush`: lý do tệp này tồn tại là *"có ngữ cảnh khi patch lỗi trên xe"* — mà lỗi
     * hay gặp nhất là app CHẾT, và một bộ đệm chưa xả thì đúng phần quan trọng nhất (những dòng ngay trước khi
     * chết) là phần biến mất.
     *
     * Đường giữa, hai điều kiện:
     *  • dòng mức **W/E/F** (`logcat -v time` đặt ký tự mức ở cột [SEVERITY_COL]) ⇒ xả NGAY — cảnh báo/lỗi/chết
     *    là thứ phải sống sót qua một cú tắt máy đột ngột;
     *  • còn lại ⇒ xả theo thời gian, tối đa mất [FLUSH_EVERY_MS] dòng mức D/I nếu app chết ngay sau đó.
     *
     * THUẦN (không đụng tệp/đồng hồ) ⇒ `KachiLogFlushTest` khoá cả hai nhánh off-device.
     */
    fun mustFlushNow(line: String, sinceLastFlushMs: Long): Boolean =
        line.getOrNull(SEVERITY_COL) in SEVERITY_FLUSH_NOW || sinceLastFlushMs >= FLUSH_EVERY_MS

    /** Cột ký tự mức trong `logcat -v time` (`09-16 09:01:53.708 W/Tag(pid): …`). */
    const val SEVERITY_COL = 19

    /** Mức phải xả ngay: Warning · Error · Fatal (Assert). */
    private val SEVERITY_FLUSH_NOW = setOf('W', 'E', 'F', 'A')

    /** Mọi ký tự mức mà `logcat -v time` in ra — dùng để nhận DẠNG một đầu bản ghi (xem [isRecordHead]). */
    private val SEVERITY_ALL = setOf('V', 'D', 'I', 'W', 'E', 'F', 'A')

    /** Trần thời gian giữa hai lần xả cho dòng D/I — cửa sổ mất mát tối đa khi app chết đột ngột. */
    const val FLUSH_EVERY_MS = 2_000L

    /**
     * Ghi **đồng bộ** một ngoại lệ chưa bắt ra `crash-<ts>-<pid>.log` (hardening 2026-09-25 · audit F23).
     *
     * Vì sao không dựa vào [startCapture]: luồng chụp logcat đọc **bất đồng bộ** trong cùng tiến trình — tiến
     * trình chết ngay sau `Log.e` thì dòng ấy có thể chưa kịp qua pipe. Đường này ghi thẳng + `flush` trước khi
     * handler mặc định của Android kill tiến trình. Dùng được ở MỌI tiến trình (`:tts`/`:wake` không chạy
     * [startCapture] nhưng `getExternalFilesDir` thì tiến trình nào cũng gọi được, không cần init).
     * Trả tệp, hoặc `null` nếu không ghi được (vắng thẻ) — không ném, không log (đang ở giữa một crash).
     */
    fun writeCrash(ctx: Context, thread: Thread, error: Throwable): File? = runCatching {
        val f = File(dir(ctx) ?: return null, "crash-${System.currentTimeMillis()}-${Process.myPid()}.log")
        f.printWriter().use { w ->
            w.println("process=${runCatching { android.app.Application.getProcessName() }.getOrDefault("?")} pid=${Process.myPid()} thread=${thread.name}")
            error.printStackTrace(w)
            w.flush()
        }
        f
    }.getOrNull()

    /** Chụp một phát toàn bộ logcat gần đây (gồm hệ thống). Trả tệp hoặc `null`. */
    fun snapshot(ctx: Context): File? = runCatching {
        val f = File(dir(ctx) ?: return null, "snapshot-${System.currentTimeMillis()}.log")
        val proc = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-v", "time"))
        f.outputStream().use { proc.inputStream.copyTo(it) }
        f
    }.onFailure { Log.w(TAG, "snapshot failed: ${it.message}") }.getOrNull()
}
