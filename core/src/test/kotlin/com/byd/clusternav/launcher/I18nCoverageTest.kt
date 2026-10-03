package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ BÀI PHỦ BẢNG DỊCH ZH/TH/MS ═════════════════════════════════════════════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-i18n-zh-th-ms.html` R3 + §4.2 (c)(d). Với MỖI tiếng ([I18nPairs.audit]):
 *  1. mọi cặp xuất ra ([I18nPairs.pairs] — quét nguồn + dữ liệu lúc chạy) đều có dòng (cặp hoặc chỉ-en);
 *  2. chỗ trống `{n}` của bản dịch = đúng tập của bản Anh (thiếu ⇒ mất số liệu; thừa ⇒ hiện `{1}` thô);
 *  3. không dấu tiếng Việt (dịch nửa vời / dán nhầm cột);
 *  4. zh có ≥ 1 chữ Hán, th có ≥ 1 chữ Thái — trừ bản chỉ gồm tên Latin ([I18nPairs.nameOnly]);
 *  5. nhãn ngắn trong trần chữ theo tiếng ([I18nPairs.SHORT_CAPS], đếm ký tự HIỂN THỊ);
 *  6. không dòng hỏng, không trùng khoá;
 *  7. không dòng MỒ CÔI (khoá không còn cặp nào dùng — chữ nguồn đã đổi ⇒ bản dịch cũ chết im lặng).
 *
 * ⚠ Ba bài `bang … phu du moi cap` ĐỎ CÓ CHỦ Ý cho tới khi bản dịch T3 được ráp (T4): thông báo nêu số đếm + đường
 * tệp `missing-<code>.tsv`. Bài `moi phep soat deu bat duoc loi` thì luôn XANH — nó chứng minh bảy phép soát CÓ cắn.
 */
class I18nCoverageTest {

    @Test
    fun `bang zh phu du moi cap`() = check(Lang.ZH)

    @Test
    fun `bang th phu du moi cap`() = check(Lang.TH)

    @Test
    fun `bang ms phu du moi cap`() = check(Lang.MS)

    private fun check(lang: Lang) {
        val pairs = I18nPairs.pairs
        val missingFile = I18nPairs.writeMissing(lang)
        val a = I18nPairs.audit(lang, I18nCatalog.table(lang), pairs)

        fun sample(xs: List<String>) = xs.take(SAMPLE).joinToString("") { "\n    $it" } +
            if (xs.size > SAMPLE) "\n    … +${xs.size - SAMPLE}" else ""
        assertTrue(
            a.ok,
            "${lang.code}: thiếu ${a.missing.size}/${pairs.size} cặp — danh sách: $missingFile" +
                " · lỗi chất ${a.quality.size} · dòng mồ côi ${a.orphans.size} · dòng hỏng/trùng ${a.broken.size}" +
                "\n  thiếu:${sample(a.missing.map { "«${it.vi}» / «${it.en}» (${it.where})" })}" +
                "\n  lỗi chất:${sample(a.quality)}\n  mồ côi:${sample(a.orphans)}\n  hỏng/trùng:${sample(a.broken)}",
        )
    }

    /** Thử-phá: một bảng giả cài sẵn đúng một lỗi mỗi loại — mỗi phép soát phải bắt ĐÚNG lỗi của nó. */
    @Test
    fun `moi phep soat deu bat duoc loi`() {
        val rows = I18nPairs.merge(
            listOf(
                I18nPairs.Row("{0} nấc", "by {0}", I18nPairs.Kind.INLINE, "a"),
                I18nPairs.Row("Mở", "Open", I18nPairs.Kind.ARGS, "b"),
                I18nPairs.Row("Lốp TT", "Tyre FL", I18nPairs.Kind.SHORT, "c", I18nPairs.SHORT_CAPS),
                I18nPairs.Row("Tốt", "Good", I18nPairs.Kind.LABEL, "d"),
                I18nPairs.Row("Đang mở", "Open", I18nPairs.Kind.DATA, "e"),
                I18nPairs.Row("Bản đồ", "VietMap HUD", I18nPairs.Kind.LABEL, "f"),
            ),
        )
        val zh = I18nCatalog.parse(
            "{0} nấc\tby {0}\t调节 {1}\n" +                 // chỗ trống lệch
                "Mở\tOpen\tMở\n" +                          // dấu tiếng Việt + không chữ Hán
                "Lốp TT\tTyre FL\t左前轮胎压力值\n" +           // 7 > trần 6
                "Tốt\tGood\tGood\n" +                       // tiếng Anh thường ⇒ không chữ Hán
                "Bản đồ\tVietMap HUD\tVietMap HUD\n" +      // tên Latin ⇒ được miễn
                "Cũ\tOld\t旧\n" +                            // mồ côi
                "\tGone\t没了\n" +                           // mồ côi (chỉ-en)
                "Tốt\tGood\t好\n",                           // trùng khoá
        )
        val a = I18nPairs.audit(Lang.ZH, zh, rows)
        assertEquals(listOf("Đang mở"), a.missing.map { it.vi }, "«Đang mở/Open» không có dòng (không dòng chỉ-en)")
        val q = a.quality.joinToString("\n")
        assertTrue("chỗ trống {n} lệch" in q && "«by {0}»" in q, q)
        assertTrue("còn dấu tiếng Việt" in q && "không có chữ HAN: «Open»" in q, q)
        assertTrue("dài 7 > trần 6" in q, q)
        assertTrue("không có chữ HAN: «Good»" in q, q)
        assertTrue("VietMap HUD» → «VietMap HUD" !in q, "tên Latin phải được miễn: $q")
        assertEquals(5, a.quality.size, q)
        assertEquals(listOf("«Cũ» / «Old»", "(chỉ-en) «Gone»"), a.orphans)
        assertEquals(1, a.broken.size, a.broken.toString())

        val th = I18nCatalog.parse("Mở\tOpen\tเปิด\n\tOpen\tเปิด\nTốt\tGood\tดี\n")
        val t = I18nPairs.audit(Lang.TH, th, rows.filter { it.en in setOf("Open", "Good") })
        assertTrue(t.ok, "bảng Thái hợp lệ (dòng chỉ-en phủ «Đang mở/Open») mà soát ra lỗi: $t")
    }

    private companion object {
        const val SAMPLE = 12
    }
}
