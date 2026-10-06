package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.KotlinSource
import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 2.93 nhóm WIDGET (spec `docs/specs/kachi-293-widget.html`) — bài canh NỐI DÂY: luật thuần đã có bài hành vi ở `:core`; ở
 * đây khoá rằng tầng vẽ GỌI đúng luật ấy (CLAUDE.md §8 — "hàm mới có chỗ gọi") và các quyết định tầng vẽ không lùi.
 */
class Widget293WiringContractTest {

    private fun code(name: String) = SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/$name")
    private fun strip(t: String) = KotlinSource.stripComments(t)

    /** Có ít nhất một chỗ gọi [token] ở mã :app/:core đã bỏ chú thích, ngoài tệp khai báo [except]. */
    private fun called(token: String, except: String): Boolean = SourceRoots.moduleSourceRoots().any { root ->
        java.nio.file.Files.walk(root).use { s ->
            s.filter { it.toString().endsWith(".kt") && it.fileName.toString() != except }.anyMatch { strip(it.toFile().readText()).contains(token) }
        }
    }

    @Test
    fun `ham moi co cho goi that - khong ham nao chet`() {
        mapOf(
            "StepA11y.speak(" to "StepA11y.kt", "StepSpoken.of(" to "StepSpoken.kt", "GlyphSpan.lead(" to "GlyphSpan.kt",
            "CarStripFit.plan(" to "CarStripFit.kt", "WallArtCachePolicy.victims(" to "WallArtCachePolicy.kt",
            "AppWidgetSize.legacyDp(" to "AppWidgetSize.kt", "AppWidgetSize.modernDp(" to "AppWidgetSize.kt",
            "GridEditorLogic.drawBox(" to "GridEditorLogic.kt", "FitValues.regrowDue(" to "FitValues.kt",
            "WidgetCapacity.of(" to "WidgetCapacity.kt", "WidgetCapacity.text(" to "WidgetCapacity.kt",
            "scheduleFitHint(" to "AppDrawerFitHint.kt", "slotFrame(" to "WorkspaceViewSlotDomain.kt", "MediaFit.plan(" to "MediaFit.kt",
            "KachiIcons.fadeOff(" to "KachiIcons.kt", "IconFade.offAlpha(" to "IconFade.kt", "progressDrawable = sliderRail(context)" to "-",
            ".measureFit(" to "FitGridLayout.kt", "GroupFitFrame(" to "GroupFitFrame.kt", "MediaFitLayout(" to "MediaFitLayout.kt",
            "CarStateLayout(" to "CarStateLayout.kt", "gapMargin(" to "FitValueRow.kt", "regrowOrShrink(" to "-",
            "FitValues.WidestMemo<" to "FitValues.kt", "FitValues.HeadroomMemo(" to "FitValues.kt",
        ).forEach { (token, file) -> assertTrue(called(token, file), "$token chưa có chỗ gọi ngoài $file") }
    }

    @Test
    fun `suc chua o - bo chon noi, drawer nhan co khung that, chu du 5 tieng`() {
        assertNull(WidgetCapacity.say(null, 6))
        assertNull(WidgetCapacity.say(WidgetCapacity.Hint(fits = true, capacity = 8), 6), "vừa ⇒ im lặng")
        assertNull(WidgetCapacity.say(WidgetCapacity.Hint(fits = false, capacity = 6), 6), "sức chứa ≥ số chọn ⇒ không nói câu tự mâu thuẫn")
        assertEquals(4, WidgetCapacity.say(WidgetCapacity.Hint(fits = false, capacity = 4), 6))
        assertEquals(0, WidgetCapacity.say(WidgetCapacity.Hint(fits = false, capacity = 0), 6), "không nổi một mục")
        val open = SourceRoots.body(code("DrawerController.kt"), "fun open(index: Int)")
        assertTrue("fitOf = slotFrame(index)?.let { (w, h) -> { ids: List<String> -> WidgetCapacity.of(activity, ids, w, h) } }" in open)
        assertTrue("slotFrame = { workspace.slotFrame(it) }" in code("KachiHomeActivity.kt"))
        assertTrue("if (selected.size < cap) scheduleFitHint(v)" in SourceRoots.body(code("AppDrawer.kt"), "private fun refreshPlaceBtn()"))
        listOf("values", "values-en", "values-zh-rCN", "values-th", "values-ms").forEach { dir ->
            val xml = SourceRoots.text("src/main/res/$dir/strings_kachi.xml")
            val hint = Regex("""<string name="kachi_drawer_fit_hint">([^<]+)</string>""").find(xml)?.groupValues?.get(1)
            assertTrue(hint != null && "%1\$d" in hint && "%2\$d" in hint, "$dir: kachi_drawer_fit_hint")
            assertTrue("<string name=\"kachi_drawer_fit_none\">" in xml, "$dir: kachi_drawer_fit_none")
        }
    }

    @Test
    fun `nhom khung thap, nhac khung nho, xe khung det - tu xep theo do`() {
        val group = SourceRoots.body(code("GroupTiles.kt"), "fun build(ctx: Context, id: String, data: WidgetData")
        assertTrue("GroupFitFrame(ctx, tile, FitGridLayout.single(ctx, mini))" in group && "tile.refresh(d); WidgetRefreshers.refresh(mini, d)" in group)
        val frame = code("GroupFitFrame.kt")
        assertTrue("FitRules.spills(h, g.paddingTop + g.paddingBottom, heights, stacked = true) || cramped(g)" in frame)
        assertTrue("MinUsefulHeight" in code("TyreBoardView.kt") && "override val minUsefulHeightPx" in code("TyreBoardView.kt"))
        val self = SourceRoots.body(code("FitGridLayout.kt"), "fun selfFitting(v: View)")
        assertTrue("is MediaFitLayout" in self, "khung nhạc tự xếp — không co lần hai bằng FitScale")
        assertTrue("MediaFit.plan(w, h, box)" in code("MediaFitLayout.kt"))
        assertTrue("CarStripFit.plan(w, h, pad, gap, capW, capH, CAR_ASPECT)" in code("CarStateLayout.kt"))
        assertTrue("addView(caption, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))" in code("CarStateLayout.kt"),
            "chú thích bề ngang TĨNH — không requestLayout mỗi nhịp")
        val clock = SourceRoots.body(code("WidgetViews.kt"), "private fun clock(ctx: Context, data: WidgetData)")
        assertTrue("GlyphSpan.lead(it, line)" in clock)
        assertFalse("setCompoundDrawablesRelative" in clock, "☀ không còn là drawable đầu dòng (dạt mép trái)")
    }

    /** Senior review 2.93 Pass 1 [P3] — chú thích xe đo cao THẬT ở bề ngang khối dọc; bộ chọn đã đóng thì không đo bản nháp. */
    @Test
    fun `soat senior - chu thich xe do cao that, goi y suc chua bo khi bo chon da dong`() {
        val measure = SourceRoots.body(code("CarStateLayout.kt"), "override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int)")
        assertTrue("MeasureSpec.makeMeasureSpec(iw, MeasureSpec.AT_MOST)" in measure, "đo chú thích ở bề ngang khối dọc (xuống dòng như col 2.92)")
        assertTrue(measure.indexOf("caption.measure(capSpec") in 0 until measure.indexOf("CarStripFit.plan("), "cao thật có TRƯỚC kế hoạch")
        val hint = SourceRoots.body(code("AppDrawerFitHint.kt"), "internal fun AppDrawer.scheduleFitHint(v: TextView)")
        assertTrue("!v.isAttachedToWindow" in hint, "bộ chọn đã đóng ⇒ không dựng bản nháp lưới")
    }

    @Test
    fun `buoc, anh nen, ban ve, khe gia tri`() {
        val step = SourceRoots.body(code("ControlTileFactory.kt"), "private fun tileStep(")
        assertEquals(4, Regex("""\bsay\(""").findAll(step).count(), "khai + vẽ khi bấm + lần đầu + đọc lại xe")
        val build = SourceRoots.body(code("WallArt.kt"), "fun build(ctx: Context, path: String, source: Bitmap")
        assertTrue(build.indexOf("saveCached(dir, key, art)") < build.indexOf("prune(dir, keep = key)"), "dọn SAU khi ghi khoá mới")
        val prune = SourceRoots.body(code("WallArt.kt"), "private fun prune(dir: File?, keep: String)")
        assertTrue("File(dir, \"\$k.png\").delete(); File(dir, \"\$k.txt\").delete()" in prune && "dir.listFiles()" in prune, "chỉ tệp trong .kachi-art")
        assertTrue("area = workspaceArea(rootFrame)" in code("HomePanels.kt") && "editor.area = area" in code("LayoutEditorPanel.kt"))
        assertTrue("GridEditorLogic.drawBox(" in SourceRoots.body(code("GridEditorView.kt"), "private fun place(w: Int, h: Int)"))
    }
}
