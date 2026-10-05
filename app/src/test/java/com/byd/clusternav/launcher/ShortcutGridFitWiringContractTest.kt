package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 2.87 R-SI1 (spec `docs/specs/kachi-287-look-and-keys.html` §4.4) — bài canh TĨNH nối dây lưới lối tắt tự co giãn.
 * Hình học (cỡ lớn nhất, khe đều, hàng cuối căn giữa, kẹp) khoá bằng test thuần `ShortcutGridFitTest` ở `:core`; bài này
 * khoá chỗ NỐI: lưới widget (ô to + ô nén) đặt icon bằng `ShortcutGridFit`, còn khối thanh nút KHÔNG đổi một dòng hành vi
 * (owner 03/10: khối thanh nút đã đúng — dài theo số app).
 */
class ShortcutGridFitWiringContractTest {

    private fun code(file: String) = SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/$file")

    private val view by lazy { code("ShortcutIconsView.kt") }
    private val layout by lazy { code("ShortcutGridLayout.kt") }

    @Test
    fun `luoi widget dat icon bang ShortcutGridFit qua ShortcutGridLayout`() {
        val grid = SourceRoots.body(view, "private fun buildGrid(items: List<AppShortcut>)")
        assertTrue(grid.contains("ShortcutGridLayout(context) { px -> if (gen == generation) fitIcons(px) }"),
            "lưới dựng MỘT khung khớp, báo cỡ của lượt cũ bị bỏ")
        assertTrue(grid.contains("items.forEach { box.addView(cell(it)) }"))
        assertTrue(grid.contains("LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)"), "khung lấp ô widget")
        listOf("LinearLayout(", "cellLp()", "chunked(", "gridCols").forEach {
            assertFalse(grid.contains(it), "lưới còn '$it' ⇒ quay lại ô cố định/LinearLayout lồng (trước R-SI1)")
        }
        // Khung khớp: tính ở lượt ĐO theo (rộng, cao, số icon), đặt theo vị trí của `:core`.
        val refit = SourceRoots.body(layout, "private fun refit(w: Int, h: Int, minIcon: Int)")
        assertTrue(refit.contains("ShortcutGridFit.fit("), "phép khớp đi qua :core")
        assertTrue(refit.contains("ShortcutGridFit.GAP_RATIO") && refit.contains("Bars.SHORTCUT_GRID_MAX_ICON"))
        assertTrue(refit.contains("if (it.widthPx == w && it.heightPx == h && it.count == childCount) return it"),
            "khớp lại CHỈ khi khung hoặc số icon đổi — không thrash")
        assertTrue(refit.contains("post { onIconPx(f.iconPx) }"), "đổi drawable SAU lượt bố trí, không giữa lượt đo")
        val measure = SourceRoots.body(layout, "override fun onMeasure(")
        assertTrue(measure.contains("Bars.SHORTCUT_GRID_MIN_ICON") && measure.contains("refit("))
        assertTrue(measure.contains("MeasureSpec.makeMeasureSpec(f.iconPx + 2 * padX, MeasureSpec.EXACTLY)"), "mọi icon CÙNG cỡ")
        val place = SourceRoots.body(layout, "override fun onLayout(")
        assertTrue(place.contains("f.left(i)") && place.contains("f.top(i)"), "vị trí (kể cả hàng cuối căn giữa) từ :core")
    }

    @Test
    fun `hinh chung cua app da go nap lai dung co khop`() {
        val fit = SourceRoots.body(view, "private fun fitIcons(iconPx: Int)")
        assertTrue(fit.contains("cells.forEach { if (it.generic) genericIcon(it.view) }"))
        assertTrue(SourceRoots.body(view, "private fun genericIcon(v: ImageView)").contains("KachiIcons.res(GENERIC_ICON, iconSizeDp())"))
        assertTrue(SourceRoots.body(view, "private fun load(gen: Int)").contains("cell.generic = icon == null"))
        assertTrue(SourceRoots.body(view, "private fun rebuild()").contains("fittedDp = 0"), "lượt dựng mới không mang cỡ khớp cũ")
    }

    @Test
    fun `khoi thanh nut khong dung phep khop - khe co dinh nhu 2_86`() {
        val rebuild = SourceRoots.body(view, "private fun rebuild()")
        assertTrue(rebuild.contains("if (grid) buildGrid(items) else items.forEach { addView(cell(it), cellLp()) }"))
        // 2.89 · B3 — đổi chân có chủ ý: khe đi qua `shortcutSlotPx` (= `SHORTCUT_CELL`, sàn 48 dp THẬT khi thanh co) —
        // CÙNG hàm với `shortcutStripLength`; vẫn KHÔNG qua lưới khớp (vế dưới).
        assertTrue(SourceRoots.body(view, "private fun cellPx()").contains("shortcutSlotPx(context)"))
        assertTrue(SourceRoots.body(view, "private fun baseIconDp()")
            .contains("if (grid && !compact) Bars.SHORTCUT_GRID_ICON else Bars.SHORTCUT_ICON"))
        val cell = SourceRoots.body(view, "private fun cell(sc: AppShortcut)")
        assertTrue(cell.contains("if (!grid) {") && cell.contains("val pad = (cellPx() - dpi(context, iconSizeDp())) / 2"),
            "khối thanh nút giữ lề khe cố định (SHORTCUT_CELL − SHORTCUT_ICON)/2")
        listOf("ShortcutGridFit", "ShortcutGridLayout").forEach {
            assertFalse(SourceRoots.body(view, "internal fun shortcutStripLength(ctx: Context, n: Int)").contains(it))
            assertFalse(SourceRoots.body(code("ControlDockView.kt"), "private fun shortcutStrip()").contains(it),
                "khối thanh nút không được đi qua lưới khớp ($it)")
        }
        // Cỡ khớp chỉ đến từ khung lưới — khối thanh nút không có khung đó nên `fittedDp` luôn 0 ⇒ luôn cỡ gốc.
        assertEquals(1, Regex("""fitIcons\(""").findAll(view).count() - 1, "fitIcons chỉ một chỗ gọi (khung lưới)")
    }

    @Test
    fun `kep 28 va 120 dp, dich cham cua luoi gom ca khe`() {
        assertEquals(28, KachiBars.SHORTCUT_GRID_MIN_ICON, "spec §4.4: icon tối thiểu 28 dp")
        assertEquals(120, KachiBars.SHORTCUT_GRID_MAX_ICON, "spec §4.4: icon tối đa 120 dp")
        assertTrue(ShortcutGridFit.GAP_RATIO in 0.25..0.35)
        // Vùng chạm = icon + nửa khe mỗi bên: lề trong của con = nửa khe (khung đặt), không phải lề cố định.
        val refit = SourceRoots.body(layout, "private fun refit(w: Int, h: Int, minIcon: Int)")
        assertTrue(refit.contains("getChildAt(i).setPadding(padX, padY, padX, padY)"))
        assertFalse(SourceRoots.text("src/main/java/com/byd/clusternav/launcher/KachiSpaceBars.kt").contains("SHORTCUT_GRID_CELL"),
            "khe cố định 64 dp của lưới đã gỡ (R-SI1) — còn nó là còn chỗ để ai đó dùng lại")
    }
}
