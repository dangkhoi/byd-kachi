package com.byd.clusternav.launcher.behind

/**
 * ═══ LÝ DO NGẮN của một lượt BEHIND-HOME — để thấy được trên ẢNH CHỤP MÀN HÌNH (thuần, `:core`) ═══════════════════════
 *
 * Lỗi xe 2.87 (owner 04/10): *"Nút chạy nền … không chạy, bấm vào nó đen cái khung, xong rồi lại lòi lên lại"* — máy ảo
 * chạy đúng [ĐO 04/10: `slot-back … → MOVED`], trên xe [CHƯA BIẾT] vì sao, và lúc cắm CarPlay/AA đầu xe tắt WiFi ⇒ không
 * adb. CLAUDE.md §11: app tự chụp, anh em chỉ gửi ẢNH. Câu báo "chưa chạy ngầm được" vì thế phải mang theo CHỖ chuỗi dừng.
 *
 * Nguồn là CHÍNH dòng [BehindHomeSequence.Outcome.line] (một nguồn sự thật — không dựng lý do song song với chuỗi). Dạng dòng
 * (đọc ở `BehindHomeSequence`):
 *  - dừng sớm `"<tag> → <lý do>, 0 lệnh"` / `", 0 move-task"`; bên thi hành `"<what> -> <lý do>, 0 cmd"` (ASCII);
 *  - đi qua `afterStage`: `"<tag> chờ=…ms · <dòng trong>"`, dòng trong của `evict`/`moveBehind` là
 *    `"… → <MÃ> (<lý do>) <số đếm k=v…>"` hoặc `"… → <MÃ> task=… kiểm=<MÃ KIỂM> …"`;
 *  - đuôi kết luận nối bằng `" · "` có `⇒` (bản đọc cuối: `"đọc lại: A còn task trên màn ảo ô ⇒ ô giữ app"`,
 *    `"đọc lại hỏng ⇒ chưa rõ …"`) — đuôi đó LÀ lý do cuối cùng, nó thắng mọi `→` phía trước.
 *
 * Ra: `"<Result> · <lý do>"`, ≤ [MAX] ký tự (cắt bằng `…`). Lý do giữ nguyên chữ của chuỗi (nhật ký, không dịch — cùng
 * luật `PermissionReport.logLine`: hai lần đo phải so được với nhau); câu bao quanh là chuỗi tài nguyên đã dịch.
 */
object BehindReason {

    /** Trần ký tự của lý do ngắn — một dòng toast trên màn đầu xe. */
    const val MAX = 80

    /** Mũi tên "→ lý do" của chuỗi `:core` và "-> lý do" (ASCII) của bên thi hành `BehindHomeRunner`. */
    private val ARROWS = listOf("→ ", "-> ")

    /** Dấu kết luận của đuôi bản đọc cuối (`evictCovered` / `afterStage` / màn ảo ẩn). */
    private const val VERDICT = "⇒"

    /** Đuôi đếm "không làm gì" (`, 0 lệnh` · `, 0 move-task` · `, 0 cmd`) — mã kết quả đã nói điều đó. */
    private val ZERO_TAIL = Regex(""",\s*0 (?:lệnh|move-task|cmd)\b.*$""")

    /** Token `khoá=MÃ_KIỂM` (vd `kiểm=NOT_MOVED` của `moveBehind`) — chỗ hỏng khi dòng chỉ còn số đếm `k=v`. */
    private val CHECK_TOKEN = Regex("""\S+=[A-Z][A-Z_]+""")
    private val RESULT_NAMES = BehindHomeSequence.Result.entries.map { it.name }.toSet()

    /**
     * Kết quả một lượt *chạy nền* đầu ô cho lớp keo `:app` (không chạm kiểu BehindHome*): [left] = bản đọc cuối thấy app đã
     * RỜI màn ảo ô ([BehindHomeSequence.Outcome.outOfStage]) · [why] = [short] · [line] = dòng `KachiBehind` đầy đủ (sổ).
     */
    data class Report(val left: Boolean, val why: String, val line: String)

    fun report(out: BehindHomeSequence.Outcome): Report = Report(out.outOfStage, short(out), out.line)

    /** Lý do ngắn của [out] — xem KDoc lớp. Không bao giờ ném; dòng lạ ⇒ chỉ mã kết quả. */
    fun short(out: BehindHomeSequence.Outcome): String {
        val why = reason(out.line)
        val text = if (why.isEmpty()) out.result.name else "${out.result.name} · $why"
        return if (text.length <= MAX) text else text.take(MAX - 1).trimEnd() + "…"
    }

    /** Lý do (không kèm mã) rút từ một dòng `KachiBehind`; rỗng = không rút được. */
    fun reason(line: String): String {
        val segments = line.split(" · ")
        val verdict = segments.drop(1).lastOrNull { VERDICT in it && ARROWS.none { a -> a in it } }
        if (verdict != null) return verdict.trim()
        val cut = ARROWS.maxOf { line.lastIndexOf(it).let { i -> if (i < 0) -1 else i + it.length } }
        if (cut < 0) return ""
        return tidy(line.substring(cut).substringBefore(" · ").trim())
    }

    /** `"KEPT_UNDER (A_ON_TOP) đọc=4 dọn=0"` ⇒ `"A_ON_TOP"` · `"…, 0 lệnh"` ⇒ bỏ đuôi đếm · toàn `k=v` ⇒ token mã kiểm. */
    private fun tidy(raw: String): String {
        var s = raw
        val head = s.substringBefore(' ')
        if (head in RESULT_NAMES) s = s.removePrefix(head).trimStart()
        if (s.startsWith("(")) return inParens(s) ?: s
        s = s.replace(ZERO_TAIL, "").trim()
        val tokens = s.split(' ').filter { it.isNotBlank() }
        if (tokens.isNotEmpty() && tokens.all { '=' in it }) {
            CHECK_TOKEN.find(s)?.let { return it.value }
        }
        return s
    }

    /** Nội dung cặp ngoặc ĐẦU (có lồng — `(đọc lại trước lệnh: Stop(why=…))`); ngoặc không đóng ⇒ `null`. */
    private fun inParens(s: String): String? {
        var depth = 0
        s.forEachIndexed { i, c ->
            if (c == '(') depth++
            if (c == ')' && --depth == 0) return s.substring(1, i).trim()
        }
        return null
    }
}
