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
                proc.inputStream.bufferedReader().use { r ->
                    out.bufferedWriter().use { w ->
                        var line = r.readLine()
                        var lastFlush = System.currentTimeMillis()
                        while (line != null) {
                            w.write(line); w.newLine()
                            written += line.length + 1
                            KachiPerf.add(KachiPerf.Counter.LOG_BYTES, (line.length + 1).toLong())
                            val now = System.currentTimeMillis()
                            if (mustFlushNow(line, now - lastFlush)) { w.flush(); lastFlush = now }
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

    /** Trần thời gian giữa hai lần xả cho dòng D/I — cửa sổ mất mát tối đa khi app chết đột ngột. */
    const val FLUSH_EVERY_MS = 2_000L

    /** Chụp một phát toàn bộ logcat gần đây (gồm hệ thống). Trả tệp hoặc `null`. */
    fun snapshot(ctx: Context): File? = runCatching {
        val f = File(dir(ctx) ?: return null, "snapshot-${System.currentTimeMillis()}.log")
        val proc = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-v", "time"))
        f.outputStream().use { proc.inputStream.copyTo(it) }
        f
    }.onFailure { Log.w(TAG, "snapshot failed: ${it.message}") }.getOrNull()
}
