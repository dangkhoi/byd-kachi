package com.byd.clusternav.launcher

import java.util.concurrent.ConcurrentHashMap

/**
 * ═══ BẢNG DỊCH ZH/TH/MS CỦA `:core` ═════════════════════════════════════════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-i18n-zh-th-ms.html` §4.2. VI/EN vẫn là cặp chữ NGAY TẠI CHỖ GỌI ([Strings.t]/[Strings.pick]);
 * ba tiếng mới tra bảng này theo đúng cặp đó — chỗ gọi không phải đổi gì, và không có thứ tiếng thứ ba nào phải nhét
 * vào mã.
 *
 * ## Khoá là CẶP `(vi, en)`, không chỉ `en`
 * Từ đồng hình: `"Open"` là LỆNH ở `ControlRegistry` (Mở) và là TRẠNG THÁI ở `TelemetryReadout` (Đang mở) — tiếng
 * Mã Lai là `Buka` ≠ `Terbuka`. Mọi chỗ gọi đều có sẵn cả hai chữ nên khoá theo cặp không tốn gì. Dòng có cột `vi`
 * RỖNG là dòng chỉ-en: đường lùi chung cho mọi cặp cùng chữ Anh mà chưa có dòng riêng.
 *
 * Thứ tự tra: cặp đúng → dòng chỉ-en → `null` (chỗ gọi tự lùi về **tiếng Anh**, không bao giờ về tiếng Việt).
 *
 * ## Định dạng tệp (cố định — người dịch đang làm theo)
 * `/i18n/<code>.tsv` trên classpath của `:core` (đóng vào APK như tài nguyên Java). UTF-8; dòng mở bằng `#` và dòng
 * trắng bị bỏ qua; mỗi dòng `vi⇥en⇥bản-dịch`. Thoát trong một ô: `\t` = tab, `\n` = xuống dòng, `\\` = gạch chéo
 * ngược; không thoát gì khác. Câu có biến khoá theo MẪU (`{0}`…`{9}`), không theo câu đã ghép — xem [Strings.f].
 *
 * Không dùng `mapOf` Kotlin: ~1000 chuỗi × 3 tiếng vượt giới hạn 64 KB/phương thức của JVM và luật 500 dòng/tệp.
 *
 * ## Nạp lười, một lần mỗi tiếng, an toàn đa luồng
 * [Strings.current] được đọc cả trên thread nền (`CarStatusRepository` nhịp 1 giây) ⇒ [lookup] có thể chạy song song
 * với thread chính. [ConcurrentHashMap.computeIfAbsent] chạy hàm nạp đúng một lần cho mỗi khoá dù bao nhiêu thread
 * cùng hỏi. Người dùng VI/EN không bao giờ chạm tệp nào.
 *
 * Thiếu tệp/tệp hỏng ⇒ bảng rỗng (mọi chữ lùi về tiếng Anh), KHÔNG ném: launcher trên xe không được sập vì bảng dịch.
 * Lỗi định dạng được gom vào [Table.problems] để `I18nCoverageTest` đỏ off-car.
 */
object I18nCatalog {

    /**
     * Một bảng dịch đã phân tích.
     *
     * @property pairs khoá `(vi, en)` → bản dịch.
     * @property enOnly khoá `en` (dòng có cột `vi` rỗng) → bản dịch.
     * @property duplicates khoá xuất hiện nhiều lần (giữ dòng ĐẦU, các dòng sau ghi vào đây).
     * @property problems dòng sai định dạng (`<số dòng>: <lý do>`), không nạp.
     */
    class Table(
        val pairs: Map<Pair<String, String>, String>,
        val enOnly: Map<String, String>,
        val duplicates: List<String>,
        val problems: List<String>,
    ) {
        /** Số dòng đã nạp được (cặp + chỉ-en). */
        val size: Int get() = pairs.size + enOnly.size

        /** Tra theo thứ tự: cặp đúng `(vi, en)` → dòng chỉ-en → `null`. */
        fun lookup(vi: String, en: String): String? = pairs[vi to en] ?: enOnly[en]

        companion object {
            val EMPTY = Table(emptyMap(), emptyMap(), emptyList(), emptyList())
        }
    }

    private val tables = ConcurrentHashMap<Lang, Table>()

    /**
     * Bản dịch của cặp `(vi, en)` sang [lang], hoặc `null` khi chưa có (chỗ gọi lùi về [en]).
     * VI/EN luôn `null` — hai tiếng đó không có bảng (bản dịch nằm tại chỗ gọi).
     */
    fun lookup(lang: Lang, vi: String, en: String): String? =
        if (lang == Lang.VI || lang == Lang.EN) null else table(lang).lookup(vi, en)

    /** Bảng của [lang] (nạp lười một lần). VI/EN ⇒ [Table.EMPTY]. Công khai để bài canh đọc được bảng THẬT. */
    fun table(lang: Lang): Table =
        if (lang == Lang.VI || lang == Lang.EN) Table.EMPTY else tables.computeIfAbsent(lang, ::load)

    /** Đường tài nguyên của bảng [lang] trên classpath. */
    fun resourcePath(lang: Lang): String = "/i18n/${lang.code}.tsv"

    private fun load(lang: Lang): Table {
        val stream = I18nCatalog::class.java.getResourceAsStream(resourcePath(lang)) ?: return Table.EMPTY
        return stream.use { parse(it.readBytes().toString(Charsets.UTF_8)) }
    }

    /**
     * Phân tích văn bản một bảng dịch (định dạng ở KDoc lớp). Thuần — test dựng chuỗi rồi gọi thẳng.
     *
     * Dòng sai định dạng (khác 3 ô, ô `en` rỗng, ô bản dịch rỗng) bị BỎ và ghi vào [Table.problems]; trùng khoá giữ
     * dòng đầu và ghi vào [Table.duplicates]. Không bao giờ ném.
     */
    fun parse(text: String): Table {
        val pairs = LinkedHashMap<Pair<String, String>, String>()
        val enOnly = LinkedHashMap<String, String>()
        val duplicates = ArrayList<String>()
        val problems = ArrayList<String>()
        text.removePrefix("\uFEFF").split('\n').forEachIndexed { index, rawLine ->
            val line = rawLine.removeSuffix("\r")
            if (line.isBlank() || line.startsWith("#")) return@forEachIndexed
            val no = index + 1
            val cells = line.split('\t')
            if (cells.size != 3) {
                problems.add("$no: cần 3 ô (vi⇥en⇥dịch), thấy ${cells.size}")
                return@forEachIndexed
            }
            val (vi, en, value) = cells.map(::unescape)
            when {
                en.isEmpty() -> problems.add("$no: ô en rỗng")
                value.isEmpty() -> problems.add("$no: ô bản dịch rỗng (chưa dịch thì đừng thêm dòng)")
                vi.isEmpty() ->
                    if (enOnly.putIfAbsent(en, value) != null) duplicates.add("$no: chỉ-en «$en»")
                else ->
                    if (pairs.putIfAbsent(vi to en, value) != null) duplicates.add("$no: «$vi» / «$en»")
            }
        }
        return Table(pairs, enOnly, duplicates, problems)
    }

    /** `\t` → tab, `\n` → xuống dòng, `\\` → `\`; mọi gạch chéo ngược khác giữ nguyên chữ. */
    fun unescape(cell: String): String {
        if (cell.indexOf('\\') < 0) return cell
        val out = StringBuilder(cell.length)
        var i = 0
        while (i < cell.length) {
            val c = cell[i]
            if (c == '\\' && i + 1 < cell.length) {
                when (cell[i + 1]) {
                    't' -> { out.append('\t'); i += 2; continue }
                    'n' -> { out.append('\n'); i += 2; continue }
                    '\\' -> { out.append('\\'); i += 2; continue }
                }
            }
            out.append(c)
            i++
        }
        return out.toString()
    }

    /** Ngược của [unescape] — dùng khi XUẤT cặp cần dịch (bài `I18nExportTest`). */
    fun escape(cell: String): String =
        cell.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n")
}
