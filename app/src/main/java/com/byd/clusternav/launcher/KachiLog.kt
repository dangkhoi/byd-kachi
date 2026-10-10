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
 *  - Tự xoá khi gỡ app; [DiagStorageCap] dọn `kachi-logs/` (có trong danh sách cho phép `DiagFiles` — 2.92 bộ
 *    dọn CHỈ chạm danh sách ấy, không chạm ảnh/hồ sơ/gói giọng của người dùng) nên không phình vô hạn.
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
     * Bắt đầu ghi logcat CỦA CHÍNH tiến trình ra thẻ (idempotent — gọi nhiều lần chỉ chạy một luồng mỗi tiến trình).
     *
     * `--pid` = pid của mình: Android 10 cho app đọc log của CHÍNH nó mà KHÔNG cần `READ_LOGS` (chỉ log app khác
     * mới cần quyền đó). Đọc từng dòng, flush theo [mustFlushNow] để log sống sót cả khi app chết.
     *
     * ═══ 2.99 R7 KACHILOG-CAPTURE-SILENT-STOP ═══════════════════════════════════════════════════════════════════
     * [ĐO xe 10/10, Seal 2.98] tiến trình chính pid 7374 còn sống (dòng `KachiCtl( 7374)` lúc 18:27:44 trong logcat) mà
     * `usage-1791618956803.log` dừng ở 17:44:13 — hai lượt `adb pull` (17:49 · 18:27) cùng 62 201 byte. Đúng phút xe vừa
     * thức (`A11yJournal … sleep=11942s note=mo-xe` 17:44:08). Bản ≤ 2.98 có hai lỗ, cả hai đã [ĐO]/[SUY] từ source:
     *  1. tiến trình `logcat` con kết thúc (EOF / IOException) ⇒ vòng đọc thoát **im lặng** (không một dòng log), cờ về
     *     `false`, và chỉ `KachiHomeActivity.onCreate` dựng lại — tiến trình sống tiếp mà không ai ghi. Nay: hết dòng mà
     *     chưa chạm trần ⇒ in một dòng W rồi **nối lại** ([shouldResume], lùi dần [resumeDelayMs], trần [MAX_RESUMES]) từ
     *     mốc của dòng cuối đã ghi (`-T`, [captureCommand]) để không chép lại cả bộ đệm, cùng tệp, cùng trần [USAGE_CAP_BYTES].
     *  2. phiên giọng nói gọi bằng từ đánh thức chạy ở tiến trình `:wake` (pid 7453 ở log 10/10) — tiến trình ấy chưa bao
     *     giờ có tệp usage, nên dòng `KachiVoiceSession` chỉ còn trong bộ đệm logcat 256 KiB của xe (trôi sau vài phút).
     *     Nay `KachiApplication` gọi hàm này ở MỌI tiến trình trừ `:tts` ([captureTag]), tệp riêng `usage-wake-<ts>.log`.
     *
     * Chi phí thêm: tiến trình chính — 0 lúc bình thường (chỉ khác khi `logcat` chết); `:wake` — +1 luồng daemon + 1 tiến
     * trình `logcat --pid` (cùng cái tiến trình chính đã có từ 1.6x), ghi chỉ dòng của chính `:wake`, qua cùng bộ tiết chế.
     *
     * @param tag hậu tố tên tệp của tiến trình phụ ([captureTag]); rỗng = tiến trình chính (tên tệp cũ, công cụ cũ đọc được).
     */
    fun startCapture(ctx: Context, tag: String = "") {
        if (capturing) return
        capturing = true
        val app = ctx.applicationContext
        Thread({
            runCatching {
                // `getExternalFilesDir` + `mkdirs` là I/O đĩa ⇒ trên luồng nền (2.99: chỗ gọi mới là `Application.onCreate`).
                val d = dir(app) ?: return@runCatching
                File(d, usageFileName(System.currentTimeMillis(), tag)).bufferedWriter().use { w ->
                    val c = Capture()
                    while (true) {
                        val capped = pumpOnce(w, c)
                        if (!shouldResume(capped, c.resumes)) {
                            Log.w(TAG, "usage capture kết thúc (trần=$capped, đã nối lại ${c.resumes} lần, ${c.written} byte)")
                            break
                        }
                        c.resumes++
                        Log.w(TAG, "usage capture: logcat hết dòng — nối lại lần ${c.resumes} từ ${c.lastStamp ?: "đầu bộ đệm"}")
                        Thread.sleep(resumeDelayMs(c.resumes))
                    }
                }
            }.onFailure { Log.w(TAG, "usage capture stopped: ${it.message}") }
            capturing = false
        }, "KachiLogCapture").apply { isDaemon = true }.start()
    }

    /** Trạng thái một luồng chụp — sống qua các lượt nối lại (trần byte là của CẢ tiến trình, không của từng lượt). */
    private class Capture {
        var written = 0L
        var resumes = 0
        var lastStamp: String? = null
        val throttle = LogLineThrottle()
    }

    /** Một lượt `logcat` tới khi hết dòng. Trả `true` khi dừng vì chạm [USAGE_CAP_BYTES] (không được nối lại). */
    private fun pumpOnce(w: java.io.BufferedWriter, c: Capture): Boolean {
        val proc = Runtime.getRuntime().exec(captureCommand(Process.myPid(), c.lastStamp))
        try {
            proc.inputStream.bufferedReader().use { r ->
                var line = r.readLine()
                var lastFlush = System.currentTimeMillis()
                while (line != null) {
                    val src = line
                    val now = System.currentTimeMillis()
                    val toWrite = throttled(src, now, c.throttle)
                    if (toWrite != null) {
                        w.write(toWrite); w.newLine()
                        c.written += toWrite.length + 1
                        KachiPerf.add(KachiPerf.Counter.LOG_BYTES, (toWrite.length + 1).toLong())
                    }
                    stampOf(src)?.let { c.lastStamp = it }
                    // Nhịp xả tính trên dòng GỐC: một dòng bị tiết chế vẫn là một dòng vừa trôi qua, nên
                    // cửa sổ mất-mát-tối-đa của H3 không được giãn ra vì ta bỏ bớt dòng.
                    if (mustFlushNow(src, now - lastFlush)) { w.flush(); lastFlush = now }
                    if (c.written >= USAGE_CAP_BYTES) return true
                    line = r.readLine()
                }
            }
        } catch (e: java.io.IOException) {
            // `logcat` con bị giết giữa lúc đọc ⇒ ống gãy: cùng nghĩa với hết dòng (nối lại), không phải lỗi của tệp.
            Log.w(TAG, "usage capture: ống logcat gãy — ${e.message}")
        } finally {
            runCatching { w.flush() }
            runCatching { proc.destroy() }
        }
        return false
    }

    /**
     * Lệnh `logcat` của một lượt chụp. Lượt đầu ([resumeFrom] `null`) giữ y lệnh ≤ 2.98; lượt NỐI LẠI thêm `-T <mốc>` (in
     * từ mốc đó, KHÔNG thoát — khác `-t`) để không chép lại cả bộ đệm của pid vào tệp. Thuần ⇒ `KachiLogResumeTest`.
     */
    internal fun captureCommand(pid: Int, resumeFrom: String?): Array<String> =
        if (resumeFrom == null) arrayOf("logcat", "-v", "time", "--pid=$pid")
        else arrayOf("logcat", "-v", "time", "-T", resumeFrom, "--pid=$pid")

    /** Mốc `MM-DD hh:mm:ss.mmm` của một dòng ĐẦU bản ghi `logcat -v time`; dòng phụ / dòng mốc ⇒ `null`. */
    internal fun stampOf(line: String): String? =
        if (isRecordHead(line) && line[STAMP_LEN] == ' ') line.substring(0, STAMP_LEN) else null

    /** Hết dòng mà chưa chạm trần ⇒ nối lại, tối đa [MAX_RESUMES] lần mỗi tiến trình (logd hỏng hẳn thì không quay mãi). */
    internal fun shouldResume(capped: Boolean, resumes: Int): Boolean = !capped && resumes < MAX_RESUMES

    /** Lùi dần 2 s · 4 s · 8 s … trần [RESUME_MAX_MS] (lần thứ [resumes], ≥ 1). */
    internal fun resumeDelayMs(resumes: Int): Long =
        minOf(RESUME_BASE_MS shl (resumes - 1).coerceIn(0, 10), RESUME_MAX_MS)

    /** `usage-<ts>.log` (tiến trình chính — tên ≤ 2.98) · `usage-<tag>-<ts>.log` (tiến trình phụ). */
    internal fun usageFileName(nowMs: Long, tag: String): String =
        if (tag.isEmpty()) "usage-$nowMs.log" else "usage-$tag-$nowMs.log"

    /**
     * Hậu tố tệp usage cho tiến trình tên [processName]: `""` = tiến trình chính · `"wake"` = `:wake` (phiên giọng nói gọi
     * bằng từ đánh thức) · `null` = KHÔNG chụp (`:tts` chỉ đọc câu, không có lệnh/ý định nào; tên không đọc được ⇒ không
     * đoán). [ĐO] `KachiApplication` chạy ở cả ba tiến trình (KDoc của nó).
     */
    internal fun captureTag(processName: String?, packageName: String, wakeSuffix: String, ttsSuffix: String): String? =
        when {
            processName == null -> null
            processName == packageName -> ""
            processName.endsWith(wakeSuffix) -> wakeSuffix.trimStart(':')
            processName.endsWith(ttsSuffix) -> null
            else -> null
        }

    /** Số lần nối lại tối đa mỗi tiến trình. */
    internal const val MAX_RESUMES = 20
    private const val RESUME_BASE_MS = 2_000L
    private const val RESUME_MAX_MS = 60_000L

    /** Độ dài mốc `MM-DD hh:mm:ss.mmm` ở đầu dòng `logcat -v time` (= [SEVERITY_COL] − 1). */
    private const val STAMP_LEN = 18

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
