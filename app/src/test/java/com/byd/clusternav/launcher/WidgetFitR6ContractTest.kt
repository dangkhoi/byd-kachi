package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ Soát vòng 6 + QA4 (2.87, 04/10) — chỗ NỐI của các luật thuần mới ═══
 *
 * Luật đã khoá ở `:core` (`FitValuesTest` — khe giá trị/chú thích, nhịp chỉ co, chữ trợ năng; `FitRulesRound6Test` — lề icon theo
 * dạng, trần chung không kéo ô loại khác, gộp dạng phụ). Dự án không dùng Robolectric ⇒ bài này khoá rằng tầng vẽ ĐI QUA các luật đó
 * (CLAUDE.md §8: hàm mới có chỗ gọi thật):
 *  1. [P3 QA4] khe giữa giá trị và chú thích trên hàng ngang (`08:3104/10`);
 *  2. [P3 soát 6] lề icon theo DẠNG (dạng ngang xoay lề) + trần chung chỉ cho icon gần trần;
 *  3. [P3 soát 6] khối dọc: nhịp đổ tại chỗ chỉ CO;
 *  4. [P3 soát 6 + QA4] chú thích nhường ⇒ đơn vị/tên datum lên nút giá trị (trợ năng);
 *  5. [P3 soát 6] nhịp đổ cùng chữ không chạy lại luật giá trị;
 *  6. [P3 soát 6] gộp dạng phụ qua `FitRules.settleMore`.
 */
class WidgetFitR6ContractTest {

    private fun code(file: String) = SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/$file")

    private val scale by lazy { code("FitScale.kt") }
    private val row by lazy { code("FitValueRow.kt") }
    private val layout by lazy { code("FitGridLayout.kt") }

    @Test
    fun `1 - khe gia tri chu thich - giu trong be rong gia tri, ca o luot do do`() {
        val fit = SourceRoots.body(row, "fun fit(rot: Boolean, fitPx: Float, legible: Boolean, floorPx: Float, tick: Boolean)")
        assertTrue(fit.contains("val r = reserve(shown)"))
        assertTrue(fit.contains("shown.maxOfOrNull { FitValues.captionMinPx(it.textSize) } ?: 0, r,"), "share nhận khe")
        assertTrue(fit.indexOf("FitValues.share(") < fit.indexOf("gap = if (share.captions) r else 0"), "khe chỉ giữ khi chú thích hiện")
        assertTrue(fit.contains("gap = 0"), "khối dọc: không bề rộng tĩnh ⇒ không khe")
        val probe = SourceRoots.body(row, "fun probe(rot: Boolean)")
        assertTrue(probe.indexOf("gap = if (rot) reserve(") < probe.indexOf("valueW = if (rot) needAt(value.textSize) + gap else -1"),
            "đo dò gồm cả khe ⇒ lưới 'đọc được' không `…` chú thích ở bề rộng nhỏ nhất")
        assertTrue(SourceRoots.body(row, "private fun reserve(shown: List<TextView>)").contains("FitValues.gapPx(it.textSize)"))
    }

    @Test
    fun `2 - le icon theo dang + tran chung chi cho icon gan tran`() {
        val init = SourceRoots.body(scale, "init {")
        assertTrue(init.indexOf("main = FitTree.main(root)") in 0 until init.indexOf("insets()"), "lề xoay cần biết khối chính TRƯỚC")
        val insets = SourceRoots.body(scale, "private fun insets()")
        assertTrue(insets.contains("FitRules.insetOf(p[0], p[1], p[2], p[3], false)") && insets.contains("FitRules.insetOf(p[0], p[1], p[2], p[3], rotated)"))
        // Điều kiện xoay phải TRÙNG điều kiện xoay thật của bộ áp (lề ngoài — params; lề trong — padding).
        assertTrue(insets.contains("b.margins?.let { add(it, b.v.parent === main && !b.relative) }"))
        assertTrue(insets.contains("byView[cur]?.pad?.let { p -> add(p, cur.parent === main && cur.background == null) }"))
        assertTrue(SourceRoots.body(scale, "private fun params(").contains("if (rot && !b.relative && l == 0 && r == 0) { l = t; r = bt; t = 0; bt = 0 }"))
        assertTrue(SourceRoots.body(scale, "private fun padding(").contains("if (rot && l == 0 && r == 0 && b.v.background == null) { l = t; r = bt; t = 0; bt = 0 }"))
        assertTrue(SourceRoots.body(scale, "private fun room(b: Base, f: Form, k: Double, cw: Int, ch: Int)")
            .contains("val (x, y) = if (f == Form.HORIZONTAL) b.rotInset else b.inset"))
        assertTrue(SourceRoots.body(scale, "fun iconSides(k: Double, f: Form, cw: Int, ch: Int)").contains("room(b, f, k, cw, ch)"),
            "trần chung tính theo dạng SẮP áp")
        assertFalse(scale.contains("insetX") || scale.contains("insetY"), "không còn lề một-dạng")
    }

    @Test
    fun `3 - khoi doc - nhip do tai cho chi co`() {
        val fv = SourceRoots.body(scale, "fun fitValues(floorPx: Float, legible: Boolean, tick: Boolean = false)")
        val hold = fv.indexOf("if (tick && FitValues.holds(tv.textSize, avail, FitValueRow.needOf(tv))) continue")
        assertTrue(hold in 0 until fv.indexOf("FitValues.valuePx("), "chữ còn vừa cỡ đang có ⇒ giữ, trước khi tính cỡ mới")
    }

    @Test
    fun `4 - chu thich nhuong - don vi va ten datum len nut gia tri`() {
        val init = SourceRoots.body(row, "init {")
        assertTrue(init.contains("if (value.accessibilityDelegate.let { it == null || it is Spoken }) value.accessibilityDelegate = Spoken()"),
            "cài delegate cho nút GIÁ TRỊ; bộ dựng đã có delegate ⇒ không đè; delegate của bộ áp cũ ⇒ thay (không đọc trạng thái chết)")
        val spoken = SourceRoots.body(row, "override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo)")
        assertTrue(spoken.indexOf("super.onInitializeAccessibilityNodeInfo(host, info)") < spoken.indexOf("if (!yielded) return"))
        assertTrue(spoken.contains("partition { it.getTag(R.id.kachi_fit_full_text) != null }"), "chữ TÊN (FitScale.named) tách khỏi đơn vị")
        assertTrue(spoken.contains("info.contentDescription = FitValues.spoken(info.contentDescription ?: value.text, units.map(::say), names.map(::say))"))
        assertFalse(row.contains("value.contentDescription ="), "tính lúc hỏi — không ghi mô tả mỗi nhịp (chữ cũ)")
    }

    @Test
    fun `5 - nhip do cung chu khong chay lai luat gia tri`() {
        val hook = SourceRoots.body(layout, "private fun onContentChanged(")
        assertTrue(hook.contains("if (valuesStale(child)) items[child]?.fs?.fitValues(FitProbe.Floors.of(context).textPx, legible, tick = true)"))
        val stale = SourceRoots.body(layout, "private fun valuesStale(v: View)")
        assertTrue(stale.contains("val sig = it.fs?.let(FitProbe::signature) ?: return false"))
        assertTrue(stale.contains("return (sig != it.valuesSig).also { _ -> it.valuesSig = sig }"))
        val refit = SourceRoots.body(layout, "private fun refit(w: Int, h: Int)")
        assertTrue(refit.indexOf("c.fs.fitValues(floors.textPx, f.legible)") < refit.indexOf("it.valuesSig = it.fs?.let(FitProbe::signature)"),
            "lượt khớp ghi dấu SAU khi chạy luật giá trị")
    }

    @Test
    fun `6 - gop dang phu qua FitRules settleMore`() {
        val sm = SourceRoots.body(layout, "private fun settledMore(")
        assertTrue(sm.contains("FitRules.primaryOnly(n.forms(), FitProbe.OPTIONS.map { !it.secondary })"), "`fresh` chỉ dạng chính")
        assertFalse(sm.contains("FitRules.settle("), "phép gộp nằm ở :core (một nơi duy nhất)")
        assertEquals(listOf(false, false, false, true, true, true, true, true), FitProbe.OPTIONS.map { it.secondary },
            "FitRulesRound6Test dựng 3 dạng chính + 5 dạng phụ — cùng thứ tự OPTIONS")
    }

    @Test
    fun `ham moi co cho goi that`() {
        val app = SourceRoots.moduleSourceRoots().filter { it.toString().contains("app") }
        fun strip(t: String) = t.replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), " ")
            .lines().joinToString("\n") { it.substringBefore("//") }
        fun uses(token: String): Boolean = app.any { root ->
            java.nio.file.Files.walk(root).use { s -> s.filter { it.toString().endsWith(".kt") }.anyMatch { strip(it.toFile().readText()).contains(token) } }
        }
        listOf(
            "FitValues.gapPx(", "FitValues.holds(", "FitValues.spoken(", "FitRules.insetOf(", "FitRules.iconShared(",
            "FitRules.settleMore(", "FitRules.primaryOnly(", "valuesStale(child)", "reserve(shown)", "room(b, form, k, cellW, cellH)",
        ).forEach { assertTrue(uses(it), "$it chưa có chỗ gọi ở :app") }
        assertTrue(SourceRoots.text("src/main/java/com/byd/clusternav/launcher/FitValueRow.kt").lines().size <= 500)
    }
}
