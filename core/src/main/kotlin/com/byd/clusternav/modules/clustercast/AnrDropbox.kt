package com.byd.clusternav.modules.clustercast

/**
 * 2.96 · R14 — lọc báo cáo ANR gần nhất của CHÍNH Kachi từ `dumpsys dropbox --print data_app_anr` (JVM thuần, test off-device).
 *
 * Vì sao: [ĐO log 21:04 07/10] ANR ~16 s trên SCREEN_ON, gốc [CHƯA BIẾT] — thiếu đúng dump luồng chính. `ClusterDiag` chạy
 * [CMD] qua kênh dadb sẵn có rồi đưa output vào [latestFor].
 *
 * Định dạng [ĐO nguồn android-10.0.0_r47]: `DropBoxManagerService.java:598-612` — với `--print` mỗi mục mở bằng
 * `========================================`, rồi `yyyy-MM-dd HH:mm:ss <tag> (text, N bytes)`, rồi nội dung; mục duyệt theo
 * `mAllFiles.contents` (TreeSet theo thời gian ⇒ mới nhất ở cuối). Tiêu đề ANR do `ActivityManagerService.java:9459-9496`
 * (`appendDropBoxProcessHeaders`) ghi: `Process: <tên tiến trình>`, `PID:`, `UID:`, `Flags:`, `Package: <gói> v… (…)` …, dòng trống,
 * thân (CPU + traces). Bộ lọc vì thế BẢO THỦ: chỉ nhận mục có dòng ngày-giờ + thẻ `data_app_anr`, và gói khớp CHÍNH XÁC ở dòng
 * `Process:` (gói hoặc `gói:tiến-trình-con`) hay token đầu của `Package:` trong phần tiêu đề — dòng `Activity:`/`Subject:` hay
 * thân mục nhắc gói khác/gói Kachi đều KHÔNG tính. Định dạng khác ⇒ trả `null` (không bao giờ lọt mục của app khác).
 */
object AnrDropbox {

    /** Trần đọc thô trên xe: mục mới nhất ở CUỐI dump ⇒ `tail -c` giữ phần mới; mục đầu bị cắt mất dòng ngày-giờ ⇒ tự bị loại. */
    const val TAIL_BYTES = 2 * 1024 * 1024

    /** Lệnh chỉ ĐỌC (không display, không app, không stack — không có gì để hoàn tác). */
    const val CMD = "dumpsys dropbox --print data_app_anr | tail -c $TAIL_BYTES"

    /** Trần một mục giữ lại (giữ ĐẦU mục: tiêu đề + luồng `main` đứng đầu traces). */
    const val MAX_BYTES = 64 * 1024

    private const val TAG = "data_app_anr"
    private val SEPARATOR = Regex("""^=+\s*$""")
    private val ENTRY_HEAD = Regex("""^(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}) $TAG\b.*""")
    private const val HEADER_SCAN_LINES = 25
    private const val MARK_RESERVE = 64

    /**
     * Mục `data_app_anr` MỚI NHẤT (theo dấu thời gian của dòng mở mục) mà tiêu đề nêu đúng [pkg]; cắt còn ≤ [maxBytes] byte UTF-8.
     * `null` khi [pkg] rỗng hoặc không có mục nào của [pkg].
     */
    fun latestFor(dump: String, pkg: String, maxBytes: Int = MAX_BYTES): String? {
        if (pkg.isBlank()) return null
        val best = entries(dump).filter { ownedBy(it.second, pkg) }.maxByOrNull { it.first } ?: return null
        return truncateUtf8(best.second, maxBytes)
    }

    /** Tách dump thành (dấu thời gian, văn bản mục). Chỉ khối mở bằng dòng [ENTRY_HEAD] mới được tính. */
    internal fun entries(dump: String): List<Pair<String, String>> {
        val blocks = mutableListOf<MutableList<String>>()
        var cur = mutableListOf<String>()
        dump.lineSequence().forEach { line ->
            if (SEPARATOR.matches(line)) { blocks += cur; cur = mutableListOf() } else cur += line
        }
        blocks += cur
        return blocks.mapNotNull { lines ->
            val body = lines.dropWhile { it.isBlank() }
            val head = body.firstOrNull()?.let { ENTRY_HEAD.matchEntire(it.trimEnd()) } ?: return@mapNotNull null
            head.groupValues[1] to body.joinToString("\n").trimEnd()
        }
    }

    /** Gói khớp CHÍNH XÁC trong phần tiêu đề (trước dòng trống đầu tiên sau dòng mở mục). */
    internal fun ownedBy(entry: String, pkg: String): Boolean =
        entry.lineSequence().drop(1).take(HEADER_SCAN_LINES).takeWhile { it.isNotBlank() }.any { raw ->
            val line = raw.trim()
            when {
                line.startsWith("Process:") -> line.removePrefix("Process:").trim().let { it == pkg || it.startsWith("$pkg:") }
                line.startsWith("Package:") -> line.removePrefix("Package:").trim().substringBefore(' ') == pkg
                else -> false
            }
        }

    internal fun truncateUtf8(text: String, maxBytes: Int): String {
        val bytes = text.encodeToByteArray()
        if (bytes.size <= maxBytes) return text
        // Cắt theo byte; ký tự đa byte bị chẻ ở biên rơi thành U+FFFD — chấp nhận cho một tệp chẩn đoán.
        // Chừa [MARK_RESERVE] byte cho dòng đánh dấu ⇒ tổng LUÔN ≤ [maxBytes].
        val keep = (maxBytes - MARK_RESERVE).coerceAtLeast(0)
        return bytes.copyOf(keep).decodeToString().trimEnd('�') + "\n…[cắt: ${bytes.size} byte]"
    }
}
