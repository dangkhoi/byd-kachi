package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.I18nScripts
import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/**
 * ═══ NĂM THƯ MỤC TÀI NGUYÊN — values · values-en · values-zh-rCN · values-th · values-ms ══════════════════════════
 *
 * Spec `docs/specs/kachi-i18n-zh-th-ms.html` §4.3. Tách từ `LauncherI18nContractTest` (trần 500 dòng): bản cũ so
 * CỨNG hai tệp `VI_XML`/`EN_XML`, nên ba thư mục mới sẽ KHÔNG được canh gì cả. Nay mỗi phép so chạy cho từng thư mục
 * dịch, và JUnit báo từng thư mục một dòng riêng — `values-en` xanh không bị lấp bởi `values-th` còn đỏ.
 *
 * ⚠ ĐỎ CÓ CHỦ Ý cho tới khi bản dịch về (T3/T4): ba thư mục `values-zh-rCN/-th/-ms` chưa có trong repo — người dịch
 * giao. Thông báo đỏ nêu đúng TỆP + KHOÁ để người ráp làm theo. `values-en` phải xanh từ đầu đến cuối.
 *
 * Vì sao từng phép kiểm (mỗi phép một loại lỗi chỉ người dùng tiếng ĐÓ gặp, Android lùi IM LẶNG):
 *  • tập khoá — thiếu ⇒ Android lùi về `values/` = TIẾNG VIỆT giữa màn tiếng Thái; thừa ⇒ lint `ExtraTranslation` FATAL;
 *  • hạng plurals — zh/th/ms không chia số (CLDR chỉ có `other`); có `one` ⇒ lint `UnusedQuantity`;
 *  • tham số định dạng — lệch ⇒ `IllegalFormatException` LÚC CHẠY; bản cũ chỉ thấy `%1$s` có vị trí, nay thấy cả
 *    `%s`/`%d` trần (5 khoá `kachi_captest_*` dùng chúng);
 *  • dấu tiếng Việt ngoài `values/` — dán nhầm bản gốc;
 *  • chữ viết — zh phải có chữ Hán, th phải có chữ Thái (trừ chuỗi chỉ gồm tên Latin, luật [I18nScripts.nameOnly]);
 *  • Mã Lai trùng nguyên văn tiếng Anh — Mã Lai dùng chữ Latin nên phép kiểm chữ viết mù, đây là lưới thay thế.
 */
class LauncherI18nLocalesContractTest {

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = ["values-en", "values-zh-rCN", "values-th", "values-ms"])
    fun `tep dich co cung tap khoa voi values`(folder: String) {
        val vi = res(VI).keys
        assertTrue(vi.size >= 100, "chỉ đọc được ${vi.size} khoá ở values/ — nghi chính bộ đọc XML hỏng")
        val xx = resOrNull(folder)
        assertTrue(xx != null, "${rel(folder)}: CHƯA CÓ tệp — thiếu cả ${vi.size} khoá (Android lùi về tiếng Việt)")
        val missing = (vi - xx!!.keys).sorted()
        val extra = (xx.keys - vi).sorted()
        assertTrue(
            missing.isEmpty() && extra.isEmpty(),
            "${rel(folder)}: THIẾU ${missing.size} khoá (Android lùi về values/ = tiếng Việt, im lặng): $missing\n" +
                "THỪA ${extra.size} khoá (lint ExtraTranslation FATAL, R.string không có ở cấu hình mặc định): $extra",
        )
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = ["values", "values-en", "values-zh-rCN", "values-th", "values-ms"])
    fun `plurals dung hang theo tieng`(folder: String) {
        val want = when (folder) {
            "values-en" -> setOf("one", "other")
            else -> setOf("other")   // vi · zh · th · ms: CLDR không chia số ⇒ chỉ `other`
        }
        val xx = resOrNull(folder)
        assertTrue(xx != null, "${rel(folder)}: CHƯA CÓ tệp")
        assertTrue(xx!!.plurals.isNotEmpty(), "${rel(folder)}: không thấy <plurals> nào — bộ đọc hỏng?")
        val bad = xx.plurals.filterValues { it.keys != want }.map { (k, v) -> "$k: ${v.keys.sorted()}" }
        assertEquals(emptyList<String>(), bad, "${rel(folder)}: hạng plurals phải đúng $want")
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = ["values-en", "values-zh-rCN", "values-th", "values-ms"])
    fun `tham so dinh dang khop voi ban Viet ke ca tham so tran`(folder: String) {
        val vi = res(VI)
        val xx = resOrNull(folder)
        assertTrue(xx != null, "${rel(folder)}: CHƯA CÓ tệp")
        // Chỉ so khoá CÓ Ở CẢ HAI: khoá thiếu là việc của bài tập khoá (đỏ ở đây sẽ nói sai nguyên nhân).
        val bad = xx!!.strings.filter { (k, v) -> k in vi.strings && args(v) != args(vi.strings.getValue(k)) }.keys +
            xx.plurals.flatMap { (k, items) ->
                val base = vi.plurals[k]?.get("other") ?: return@flatMap emptyList()
                items.filter { (q, v) -> if (q == "other") args(v) != args(base) else !args(base).containsAll(args(v)) }
                    .map { "$k[${it.key}]" }
            }
        assertEquals(
            emptyList<String>(), bad.sorted(),
            "${rel(folder)}: số/loại tham số lệch bản Việt ⇒ `getString(...)` ném `IllegalFormatException` LÚC CHẠY, chỉ ở " +
                "tiếng này",
        )
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = ["values-en", "values-zh-rCN", "values-th", "values-ms"])
    fun `khong con dau tieng Viet ngoai values`(folder: String) {
        val xx = resOrNull(folder)
        assertTrue(xx != null, "${rel(folder)}: CHƯA CÓ tệp")
        val bad = xx!!.allTexts().filterValues { I18nScripts.VIETNAMESE_MARK.containsMatchIn(it) }.keys.sorted()
        assertEquals(
            emptyList<String>(), bad,
            "${rel(folder)}: câu tiếng Việt còn trong bản dịch ⇒ sai IM LẶNG: chỉ người dùng tiếng này gặp, và người soát " +
                "bản Việt không có cách nào thấy",
        )
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = ["values-zh-rCN", "values-th"])
    fun `chu viet dung tieng`(folder: String) {
        val script = if (folder == "values-th") Character.UnicodeScript.THAI else Character.UnicodeScript.HAN
        val xx = resOrNull(folder)
        assertTrue(xx != null, "${rel(folder)}: CHƯA CÓ tệp")
        val bad = xx!!.allTexts().filterValues { v ->
            !I18nScripts.hasScript(v, script) && !I18nScripts.nameOnly(bare(v))
        }.map { (k, v) -> "$k = «$v»" }.sorted()
        assertEquals(
            emptyList<String>(), bad,
            "${rel(folder)}: không có chữ $script ⇒ nghi chưa dịch (tên Latin được phép: VIẾT HOA, CamelCase, hoặc thêm " +
                "vào I18nScripts.NAME_WORDS kèm lý do)",
        )
    }

    /** Mã Lai viết chữ Latin ⇒ phép kiểm chữ viết mù; câu Mã Lai Y HỆT câu Anh là dấu hiệu chép chưa dịch. */
    @Test
    fun `ban Ma Lai khong trung nguyen van ban Anh`() {
        val ms = resOrNull("values-ms")
        assertTrue(ms != null, "${rel("values-ms")}: CHƯA CÓ tệp")
        val en = res("values-en").allTexts()
        val same = ms!!.allTexts().filter { (k, v) -> en[k] == v && !I18nScripts.nameOnly(bare(v)) && k !in MS_SAME_AS_EN }
            .keys.sorted()
        assertEquals(emptyList<String>(), same, "${rel("values-ms")}: trùng nguyên văn tiếng Anh — dịch, hoặc khai MS_SAME_AS_EN kèm lý do")
        assertEquals(emptyList<String>(), MS_SAME_AS_EN.keys.filterNot { en[it] != null && ms.allTexts()[it] == en[it] }, "mục MS_SAME_AS_EN chết")
    }

    /**
     * Tệp `strings.xml` MỚI của ba thư mục (5 khoá còn sống của tệp niêm phong — tên app, nhãn dịch vụ trợ năng) chỉ
     * được chứa khoá CÓ ở `values/strings.xml`: khoá thừa = lint `ExtraTranslation` FATAL, chặn `assembleRelease`.
     * Chưa có tệp thì không có khoá thừa nào (xanh) — tệp đó là tuỳ chọn, Android lùi về bản gốc.
     */
    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = ["values-zh-rCN", "values-th", "values-ms"])
    fun `strings xml moi chi chua khoa co trong values`(folder: String) {
        val r = "src/main/res/$folder/strings.xml"
        if (!SourceRoots.exists(r)) return
        val base = parse(SourceRoots.text("src/main/res/values/strings.xml")).keys
        assertEquals(emptyList<String>(), (parse(SourceRoots.text(r)).keys - base).sorted(), "$r: khoá không có ở values/strings.xml")
    }

    // ── Hạ tầng ──────────────────────────────────────────────────────────────────────────────────

    private class Res(val strings: Map<String, String>, val plurals: Map<String, Map<String, String>>) {
        val keys: Set<String> get() = strings.keys + plurals.keys

        /** Mọi chữ hiện ra: chuỗi + từng hạng plurals (`khoá[hạng]`). */
        fun allTexts(): Map<String, String> =
            strings + plurals.flatMap { (k, items) -> items.map { (q, v) -> "$k[$q]" to v } }
    }

    private companion object {
        const val VI = "values"

        /** Khoá mà câu Mã Lai được PHÉP trùng nguyên văn câu Anh — mỗi mục một lý do (T3/T4 khai). */
        val MS_SAME_AS_EN: Map<String, String> = emptyMap()

        /** `%1$s` / `%s` / `%.1f` — KHÔNG nhận cờ dấu cách (`50 % của` không phải tham số). `%%` bị bỏ qua ở [args]. */
        val FORMAT = Regex("""%%|%(?:(\d+)\$)?[-#+0,(]*\d*(?:\.\d+)?([sdfxXc])""")
    }

    private fun rel(folder: String) = "src/main/res/$folder/strings_kachi.xml"

    private fun res(folder: String): Res = parse(SourceRoots.text(rel(folder)))

    private fun resOrNull(folder: String): Res? = if (SourceRoots.exists(rel(folder))) res(folder) else null

    /** Bỏ chú thích XML trước (một `<string>` nằm trong chú thích không phải khoá), rồi đọc chuỗi + plurals. */
    private fun parse(xml: String): Res {
        val t = xml.replace(Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL), "")
        val strings = Regex("""<string name="([^"]+)"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL).findAll(t)
            .associate { it.groupValues[1] to it.groupValues[2] }
        val plurals = Regex("""<plurals name="([^"]+)"[^>]*>(.*?)</plurals>""", RegexOption.DOT_MATCHES_ALL).findAll(t)
            .associate { m ->
                m.groupValues[1] to Regex("""<item quantity="(\w+)">(.*?)</item>""", RegexOption.DOT_MATCHES_ALL)
                    .findAll(m.groupValues[2]).associate { it.groupValues[1] to it.groupValues[2] }
            }
        return Res(strings, plurals)
    }

    /**
     * Tham số định dạng: có vị trí (`1$s`) là một TẬP — bản dịch được đổi chỗ; trần (`s`) là một DÃY — thứ tự là
     * nghĩa của nó, đổi chỗ hai `%s` là đổi nghĩa câu.
     */
    private fun args(v: String): List<String> {
        val positional = sortedSetOf<String>()
        val bare = ArrayList<String>()
        FORMAT.findAll(v).filter { it.value != "%%" }.forEach { m ->
            if (m.groupValues[1].isNotEmpty()) positional += "${m.groupValues[1]}$${m.groupValues[2]}" else bare += m.groupValues[2]
        }
        return positional.toList() + listOf("|") + bare
    }

    /** Chữ cho người đọc, đã bỏ tham số định dạng + thoát của Android + thực thể XML — để xét "chỉ gồm tên Latin". */
    private fun bare(v: String): String = FORMAT.replace(v, " ")
        .replace(Regex("""\\[nt'"@?\\]"""), " ")
        .replace(Regex("""&(?:#\d+|#x[0-9a-fA-F]+|[a-z]+);"""), " ")
}
