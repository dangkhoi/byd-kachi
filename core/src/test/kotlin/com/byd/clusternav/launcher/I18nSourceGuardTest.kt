package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.I18nCallScanner
import com.byd.clusternav.testsupport.I18nCallScanner.ArgKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ BÀI CANH NGUỒN cho bảng dịch ZH/TH/MS ═════════════════════════════════════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-i18n-zh-th-ms.html` §4.2. Bảng dịch khoá theo CẶP CHỮ ở chỗ gọi, nên ba luật sau giữ cho mọi
 * khoá là chữ TĨNH đọc được bằng máy:
 *  (i)   Chữ nguồn của `t`/`pick` không được có `$` — câu có biến dùng `Strings.f`/`Lang.f` với `{0}`…`{9}`. Một
 *        chữ `"$n nấc"` sinh ra một khoá mới cho MỖI giá trị của `n` ⇒ không bao giờ dịch được, và lặng lẽ hiện tiếng Anh.
 *  (ii)  Lời gọi có đối số không-phải-chữ (biến, `if`…) phải nằm trong `i18n/nonliteral-allow.tsv` kèm lý do — dữ
 *        liệu của chúng phải được `I18nPairs.runtime` duyệt lúc chạy. Dòng thừa (không còn lời gọi nào) cũng đỏ.
 *  (iii) `{n}` trong mẫu của `f`/`fIn` phải có đối số tương ứng; `t`/`pick` không được chứa `{n}` (sẽ hiện thô).
 *
 * `i18n/template-pending.tsv` là danh sách CHỜ của `:app` (T1b chuyển `Lang.t("…$x…")` sang `Lang.f`): đếm theo
 * TỆP, phải KHỚP ĐÚNG số chỗ còn lại — chuyển xong chỗ nào thì hạ số/xoá dòng, không thì bài đỏ (chỉ được giảm).
 */
class I18nSourceGuardTest {

    private val calls = I18nPairs.calls

    @Test
    fun `bo quet thay du loi goi dich, khong quet rong`() {
        // [ĐO] kiểm kê 2026-10-03: 307 lời gọi t/pick (core 220 · app 87). Sàn thấp hơn để chuyển t→f không đỏ oan.
        assertTrue(calls.size >= 280, "quét được quá ít lời gọi (${calls.size}) — bộ quét hỏng hay cây nguồn dời chỗ?")
        assertTrue(calls.any { it.site == "core:launcher/Lang.kt" && it.vi?.value == "Theo xe" }, "mốc Lang.kt mất")
        assertTrue(calls.any { it.site.startsWith("app:") && it.fn == "Lang.t" }, "không thấy lời gọi Lang.t nào ở :app")
        assertTrue(calls.any { it.fn == "Strings.f" } && calls.any { it.fn == "Strings.fIn" }, "không thấy Strings.f/fIn")
        assertTrue(calls.any { it.fn == "s" }, "không thấy hàm bọc s(…) của KeyCtlTarget")
    }

    @Test
    fun `i - chu nguon cua t va pick khong co dau dola`() {
        val pending = readTsv("/i18n/template-pending.tsv").associate { it[0] to (it[1].toInt() to it.getOrElse(2) { "" }) }
        val bySite = calls.filter { it.hasTemplate }.groupBy { it.site }
        val bad = bySite.filter { (site, cs) -> pending[site]?.first != cs.size }
            .flatMap { (_, cs) -> cs.map { "${it.where} ${it.fn}(…) — dùng Strings.f/Lang.f với {0}…" } }
        val stale = pending.filter { (site, v) -> bySite[site]?.size != v.first }
            .map { (site, v) -> "$site: khai ${v.first}, còn ${bySite[site]?.size ?: 0}" }
        assertTrue(pending.values.all { it.second.isNotBlank() }, "dòng chờ phải có lý do")
        assertTrue(
            bad.isEmpty() && stale.isEmpty(),
            "chữ nguồn có \$ (khoá không tĩnh ⇒ không dịch được):\n  ${bad.joinToString("\n  ")}" +
                "\ntemplate-pending.tsv lệch (chuyển xong thì hạ số/xoá dòng):\n  ${stale.joinToString("\n  ")}",
        )
    }

    @Test
    fun `ii - doi so khong phai chu phai co trong danh sach cho phep`() {
        val allow = readTsv("/i18n/nonliteral-allow.tsv").associate { it[0] to it.getOrElse(1) { "" } }
        val nonLiteral = calls.filter { !it.isLiteralPair && !it.hasTemplate }
        val keys = nonLiteral.map { "${it.site}:${it.symbol}" }.toSet()
        val missing = nonLiteral.filter { "${it.site}:${it.symbol}" !in allow }
            .map { "${it.site}:${it.symbol}\t# ${it.where} ${it.fn}(${it.vi?.text}, ${it.en?.text})" }
        val stale = allow.keys - keys
        val noReason = allow.filterValues { it.isBlank() }.keys
        assertTrue(
            missing.isEmpty() && stale.isEmpty() && noReason.isEmpty(),
            "lời gọi dịch có đối số không phải chữ chưa khai (thêm vào nonliteral-allow.tsv kèm lý do + duyệt dữ liệu " +
                "của nó ở I18nPairs.runtime):\n  ${missing.joinToString("\n  ")}" +
                "\ndòng thừa (không còn lời gọi): $stale\ndòng thiếu lý do: $noReason",
        )
    }

    @Test
    fun `iii - cho trong cua mau khop so doi so`() {
        val placeholder = Regex("""\{(\d)}""")
        val bad = ArrayList<String>()
        calls.filter { it.isLiteralPair }.forEach { c ->
            val used = listOf(c.vi!!.value!!, c.en!!.value!!).flatMap { s -> placeholder.findAll(s).map { it.groupValues[1].toInt() } }
            when (c.fn) {
                "Strings.f", "Strings.fIn", "Lang.f" -> {
                    if (used.any { it >= c.args }) bad.add("${c.where}: {n} vượt ${c.args} đối số")
                    if ((0 until c.args).any { it !in used }) bad.add("${c.where}: có đối số không mẫu nào dùng")
                }
                else -> if (used.isNotEmpty()) bad.add("${c.where}: ${c.fn} chứa {n} — sẽ hiện thô, dùng f()")
            }
        }
        assertTrue(bad.isEmpty(), bad.joinToString("\n"))
    }

    // ── Thử-phá chính bộ quét: mỗi cấu trúc là một chỗ regex ngây thơ từng mù ────────────────────────────────────

    @Test
    fun `bo quet bo qua chu thich va chuoi, thay loi goi trong template`() {
        val src = """
            // Strings.t("trong chú thích", "in a comment")
            /* Strings.t("khối /* lồng */ vẫn chú thích", "nested") */
            val a = "Strings.t(\"trong chuỗi\", \"in a string\")"
            val b = "x ${'$'}{Strings.t("trong template", "in a template")} y"
            val c = Strings.t("thường", "plain")
        """.trimIndent()
        val got = I18nCallScanner.scanSource("core/src/main/kotlin/com/byd/clusternav/launcher/X.kt", src)
        assertEquals(listOf("trong template", "thường"), got.map { it.vi?.value })
        assertEquals("core:launcher/X.kt", got.first().site)
    }

    @Test
    fun `bo quet phan loai chu, mau, ghep chu, ten doi so, fIn va s`() {
        val src = """
            fun g(n: Int, lang: Lang) {
                Strings.t("a \"q\" A", "b\tc")
                Strings.t("${'$'}n nấc", "by ${'$'}n")
                Strings.pick(label, labelEn)
                Strings.t(vi = "nối " +
                    "dòng", en = "joined " + "line")
                Strings.fIn(lang, "{0} giây", "{0} s", n)
                Strings.t("giá ${'$'}", "price \${'$'}")
            }
            fun s(vi: String, en: String) = Strings.t(vi, en, lang)
            val x = s("Bật ", "Turn on ") + n
        """.trimIndent()
        val got = I18nCallScanner.scanSource("core/src/main/kotlin/com/byd/clusternav/launcher/KeyCtlTarget.kt", src)
        fun at(i: Int) = got[i].vi!!.kind to got[i].en!!.kind
        assertEquals("a \"q\" A" to "b\tc", got[0].vi!!.value to got[0].en!!.value)
        assertEquals(ArgKind.TEMPLATE to ArgKind.TEMPLATE, at(1))
        assertEquals(ArgKind.EXPR to ArgKind.EXPR, at(2))
        assertEquals("nối dòng" to "joined line", got[3].vi!!.value to got[3].en!!.value)
        assertEquals("{0} giây" to "{0} s", got[4].vi!!.value to got[4].en!!.value)
        assertEquals(1, got[4].args, "fIn: một đối số sau mẫu")
        assertEquals(ArgKind.LIT to ArgKind.LIT, at(5), "dấu \$ trần / đã thoát không phải template")
        assertEquals("g" to "s", got[1].symbol to got[6].symbol)
        assertEquals("Bật " to "Turn on ", got[7].vi!!.value to got[7].en!!.value)
        assertEquals(8, got.size, "định nghĩa `fun s(` không phải lời gọi: $got")
    }

    private fun readTsv(resource: String): List<List<String>> {
        val text = requireNotNull(javaClass.getResourceAsStream(resource)) { "thiếu $resource" }
            .use { it.readBytes().toString(Charsets.UTF_8) }
        return text.lines().filter { it.isNotBlank() && !it.startsWith("#") }.map { it.split('\t') }
    }
}
