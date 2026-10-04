package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ QA3 + soát vòng 5 (2.87, 04/10) — chỗ NỐI của luật giá trị, hộp "chỉ giá trị", trần icon chung, `settle` dạng phụ, mô tả
 * trợ năng ═══
 *
 * Luật thuần đã khoá ở `:core` (`FitValuesTest` — giá trị ưu tiên hơn chú thích; `FitRulesRound5Test` — bộ giải bước 2c trên các
 * khung QA3, trần icon, `Cell.keep`, `FitLabels.spoken`). Dự án không dùng Robolectric ⇒ bài này khoá rằng tầng vẽ ĐI QUA các
 * luật đó (CLAUDE.md §8: hàm mới có chỗ gọi thật):
 *  1. [P2 QA3] đồng hồ `06:…` ở khung 3×1 có dock: cặp giá trị/chú thích ([FitValueRow]) — hàng ngang giá trị nhận bề rộng
 *     TĨNH theo nhu cầu (chữ hiện tại đã chừa chữ số, không `WRAP`), chú thích phần còn lại / nhường; đổ tại chỗ chỉ chia lại
 *     khi giá trị sẽ bị cắt;
 *  2. [P3 QA3] 2×1 có dock mọi giá trị `…`: đo dò hộp "chỉ giá trị" ⇒ gộp ⇒ [GridFit] bước 2c;
 *  3. [P3 QA3] icon cùng lưới hai cỡ (26 vs 29px): trần chung [FitRules.iconCap];
 *  4. [P3 soát 5] dạng phụ đo lười cũng qua `FitRules.settle`;
 *  5. [P3 soát 5] nhãn ngắn ⇒ nhãn đầy trên CHÍNH chữ tên (không trên ô bấm).
 */
class WidgetFitR5ContractTest {

    private fun code(file: String) = SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/$file")

    private val scale by lazy { code("FitScale.kt") }
    private val row by lazy { code("FitValueRow.kt") }
    private val probe by lazy { code("FitProbe.kt") }
    private val layout by lazy { code("FitGridLayout.kt") }

    @Test
    fun `1 - cap gia tri chu thich - be rong tinh theo nhu cau, chia lai chi khi se cat`() {
        val params = SourceRoots.body(scale, "private fun params(")
        assertTrue(params.contains("FitRules.lp(FitRules.Lp(b.lpW, b.lpH, b.weight), k, rot).let { row?.lp(b.v, it, rot) ?: it }"),
            "LayoutParams của cặp đi qua FitValueRow.lp sau FitRules.lp")
        val lp = SourceRoots.body(row, "fun lp(v: View, t: FitRules.Lp, rot: Boolean)")
        assertTrue(lp.contains("hides(v) -> FitRules.Lp(0, 0, 0f)"), "chú thích nhường = LayoutParams 0×0, KHÔNG đụng visibility của bộ dựng")
        assertTrue(lp.contains("rot && v === value && valueW >= 0 -> FitRules.Lp(valueW, t.height, 0f)"), "giá trị: bề rộng TĨNH")
        assertFalse(row.contains("WRAP_CONTENT") || row.contains("FitRules.WRAP"), "R-WF7: không WRAP cho chữ đổi mỗi nhịp")
        assertFalse(row.contains(".visibility = "), "không ghi visibility (bộ dựng sở hữu — MiniCard.set mỗi nhịp)")
        val fit = SourceRoots.body(row, "fun fit(rot: Boolean, fitPx: Float, legible: Boolean, floorPx: Float, tick: Boolean)")
        // Soát QA4 — ĐỔI GHIM có lý do: khe giá trị/chú thích (`gap`, FitValues.gapPx) nằm TRONG bề rộng giá trị ⇒ chỗ của CHỮ là
        // `valueW - gap`; chữ dài ra ăn vào khe cũng chia lại (không dán sát `08:3104/10`). WidgetFitR6ContractTest khoá phần khe.
        assertTrue(fit.indexOf("if (tick && !FitValues.wouldClip(valueW - gap, needAt(value.textSize))) return false") in
            0 until fit.indexOf("FitValues.share("), "đổ tại chỗ: chỉ chia lại khi giá trị SẼ bị cắt")
        assertTrue(fit.contains("val px = if (legible) fitPx else FitValues.valuePx(fitPx, floorPx, flex, ::needAt)"),
            "lưới không đọc được: giá trị co theo CẢ phần hàng (chú thích nhường trước)")
        assertTrue(fit.contains("FitValues.stackYields(stackHeight(), main.measuredHeight)"), "khối dọc: chú thích nhường khi khối cao hơn ô")
        val need = SourceRoots.body(row, "fun contentNeed(tv: TextView, px: Float)")
        assertTrue(need.contains("FitValues.widestDigit {") && need.contains("FitValues.headroom(tv.text, widest)") &&
            need.contains("FitValues.needPx("), "chữ hiện tại đã chừa chữ số, đo bằng Paint của nó, làm tròn lên")
        val apply = SourceRoots.body(scale, "fun apply(k: Double, f: Form, n: Int, cw: Int = Int.MAX_VALUE, ch: Int = Int.MAX_VALUE)")
        assertTrue(apply.contains("row?.let { r -> r.probe(f == Form.HORIZONTAL); changed = rowParams(f == Form.HORIZONTAL) or changed }"),
            "đo dò: giá trị đúng nhu cầu, chú thích phần còn lại ⇒ bề rộng nhỏ nhất = icon + giá trị + chú thích")
        assertTrue(SourceRoots.body(scale, "fun visibleInTile(v: View)").contains("if (row?.hides(v) == true) return false"),
            "chú thích nhường không vào phép kiểm cắt chữ / chữ tên / sàn")
        val fv = SourceRoots.body(scale, "fun fitValues(floorPx: Float, legible: Boolean, tick: Boolean = false)")
        assertTrue(fv.contains("if (rot && tv === row?.value) continue"), "hàng ngang: giá trị không co theo nửa hàng của nó")
        assertTrue(fv.contains("r.fit(rot, fit, legible, floorPx, tick)") && fv.contains("rowParams(rot)"))
    }

    @Test
    fun `2 - hop chi gia tri - do do, gop, vao bo giai`() {
        val shape = SourceRoots.body(probe, "private fun shape(")
        assertTrue(shape.indexOf("val m = minScale(fs, floors)") in 0 until shape.indexOf("fs.whole("),
            "sàn tính TRƯỚC khi chú thích nhường (nhường thì chữ ấy không còn tính là hiện)")
        assertTrue(shape.contains("fs.whole((floors.textPx / m).toFloat())"), "giá trị ở cỡ mà k = m đưa về đúng sàn")
        assertTrue(shape.contains("wholeWidthPx = whole?.first, wholeHeightPx = whole?.second"))
        val whole = SourceRoots.body(row, "fun <T> whole(")
        listOf("yielded = true", "relayout()", "val out = measure()", "probe(rot)").forEach { assertTrue(whole.contains(it), it) }
        assertTrue(whole.indexOf("val out = measure()") < whole.indexOf("probe(rot)"), "đo xong mới trả lại phân chia gốc")
        val combine = SourceRoots.body(layout, "private fun combine(")
        assertTrue(combine.contains("wholeWidthPx = if (whole) s.maxOf { it.wholeWidthPx ?: it.widthPx } else null"),
            "ô không có cặp góp hộp ĐẦY của nó")
    }

    @Test
    fun `3 - tran icon chung cho ca luoi`() {
        val all = SourceRoots.body(layout, "private fun applyAll(")
        // Soát vòng 6 — ĐỔI GHIM có lý do: chỗ của icon tính theo DẠNG SẮP áp (`opt.form` — dạng ngang xoay lề, FitRules.insetOf);
        // `fs.form` lúc này còn là dạng của lượt trước.
        assertTrue(all.contains("FitRules.iconCap(kids.flatMap { v -> item(v).fs?.iconSides(f.scale, opt.form, f.cellW, f.cellH).orEmpty() })"))
        assertTrue(all.indexOf("iconCapPx =") in 0 until all.indexOf("apply(f.scale, opt.form, opt.lines, f.cellW, f.cellH)"),
            "trần đặt TRƯỚC khi áp")
        val iconK = SourceRoots.body(scale, "private fun iconK(b: Base, k: Double)")
        // Soát vòng 6 — ĐỔI GHIM có lý do: trần chung chỉ kéo icon GẦN trần (≤ 15 %, FitRules.iconShared); lưới trộn không kéo icon
        // nút kính 29px về 14–20px theo ô nén (FitRulesRound6Test).
        assertTrue(iconK.contains("FitRules.iconShared(own * it, iconCapPx.toDouble()) / it"))
        assertTrue(SourceRoots.body(scale, "fun apply(k: Double, f: Form, n: Int, cw: Int = Int.MAX_VALUE, ch: Int = Int.MAX_VALUE)")
            .contains("iconCapPx == appliedCap"), "đổi trần ⇒ áp lại (không trả no-op)")
    }

    @Test
    fun `4 - dang phu do luoi qua settle`() {
        val refit = SourceRoots.body(layout, "private fun refit(w: Int, h: Int)")
        assertTrue(refit.indexOf("it.prev = it.need; it.grew = it.cell.grow") in
            0 until refit.indexOf("it.cell.probed(SystemClock.elapsedRealtime(), kept = got !== raw)"),
            "chụp số cũ + cờ grow TRƯỚC khi probed() xoá cờ")
        assertTrue(refit.contains("it.fresh = null; it.prev = null"), "đã nhận số thật ⇒ dạng phụ đo lại không settle nữa")
        assertTrue(refit.contains("kids.forEach { v -> item(v).prev = null }"), "số cũ chỉ sống trong một lượt khớp")
        assertTrue(SourceRoots.body(layout, "private fun more(").contains("settledMore(it, n, FitProbe.more(v, fs, floors, n, icon))"))
        val sm = SourceRoots.body(layout, "private fun settledMore(")
        // Soát vòng 6 — ĐỔI GHIM có lý do: phép gộp thuần dời về `:core` (FitRules.settleMore — FitRulesRound6Test có bài hành vi:
        // bản sao bằng giá trị, lượt thưa < 15 %, cờ dùng được cũ, `fresh` chỉ dạng chính); ở đây chỉ còn phần nối vào ô.
        assertTrue(sm.contains("FitRules.settleMore(item.prev?.forms(), n.shapes.map { it != null }, got.forms(), item.grew) ?: return got"))
        assertTrue(sm.indexOf("?: return got") < sm.indexOf("item.cell.keep()"), "chỉ đánh dấu giữ khi có dạng giữ số cũ")
    }

    @Test
    fun `5 - mo ta tro nang tren chinh chu ten`() {
        val variant = SourceRoots.body(scale, "fun variant(v: FitLabels.Variant)")
        assertTrue(variant.contains("FitLabels.spoken(use, fullText(tv), b.text?.desc)"))
        assertFalse(variant.contains("descHost"), "không ghi lên ô bấm (THAY cả cây con ⇒ mất chữ chọn / con số)")
        assertTrue(SourceRoots.body(scale, "private fun desc(dropLabels: Boolean)").contains("if (dropLabels)"), "ô bấm: chỉ dạng chỉ-icon")
    }

    @Test
    fun `ham moi co cho goi that va tep duoi 500 dong`() {
        val app = SourceRoots.moduleSourceRoots().filter { it.toString().contains("app") }
        fun strip(t: String) = t.replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), " ")
            .lines().joinToString("\n") { it.substringBefore("//") }
        fun uses(token: String, except: String): Boolean = app.any { root ->
            java.nio.file.Files.walk(root).use { s ->
                s.filter { it.toString().endsWith(".kt") && it.fileName.toString() != except }
                    .anyMatch { strip(it.toFile().readText()).contains(token) }
            }
        }
        listOf(
            "FitValues.share(", "FitValues.wouldClip(", "FitValues.stackYields(", "FitValues.captionMinPx(", "FitValues.valuePx(",
            "FitValues.headroom(", "FitValues.widestDigit", "FitValues.needPx(", "FitRules.iconCap(", "FitLabels.spoken(",
        ).forEach { assertTrue(uses(it, "-"), "$it chưa có chỗ gọi ở :app") }
        assertTrue(uses("FitValueRow.of(", "FitValueRow.kt"))
        assertTrue(uses("FitValueRow.needOf(", "FitValueRow.kt"))
        assertTrue(uses("FitTree.main(", "FitTree.kt") && uses("FitTree.clickableAncestor(", "FitTree.kt"))
        assertTrue(uses("fs.whole(", "FitScale.kt"))
        assertTrue(uses(".iconSides(", "FitScale.kt"))
        assertTrue(uses("settledMore(", "-"))
        listOf(
            "src/main/java/com/byd/clusternav/launcher/FitValueRow.kt",
            "src/main/java/com/byd/clusternav/launcher/FitTree.kt",
            "src/main/java/com/byd/clusternav/launcher/FitScale.kt",
            "src/main/java/com/byd/clusternav/launcher/FitProbe.kt",
            "src/main/java/com/byd/clusternav/launcher/FitGridLayout.kt",
            "src/main/java/com/byd/clusternav/launcher/SlotActionsCluster.kt",
            "src/main/kotlin/com/byd/clusternav/launcher/FitValues.kt",
            "src/main/kotlin/com/byd/clusternav/launcher/FitRules.kt",
            "src/main/kotlin/com/byd/clusternav/launcher/GridFit.kt",
            "src/main/kotlin/com/byd/clusternav/launcher/FitLabels.kt",
            "src/main/kotlin/com/byd/clusternav/launcher/SlotCloseConfirm.kt",
        ).forEach { rel ->
            val n = SourceRoots.text(rel).lines().size
            assertTrue(n <= 500, "$rel dài $n dòng — trần là 500 (CLAUDE.md §4.1)")
        }
    }
}
