package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 2.93 nhóm WIDGET (spec `docs/specs/kachi-293-widget.html`) — hai việc TƯƠNG PHẢN đo trên bảng màu THẬT (`KachiPalette`
 * TỐI + SÁNG), không trên số chép tay:
 *  - `WIDGET-ICON-OFF-FAINT` — icon ô TẮT: chủ đề tối mặt nhỏ GIỮ 0,72 (đường đang ổn), chủ đề sáng mờ ít hơn để lớp chính
 *    ≥ 4,5:1 trên CẢ HAI đầu chuyển sắc của ô tắt ([ĐO mã] 0,72 cũ = 3,29:1 trên thẻ sáng);
 *  - `SLIDER-RAIL-CONTRAST` — rãnh thanh kéo ≥ 3:1 trên PANEL ở cả hai chủ đề ([ĐO QA 04/10] cũ 1,6:1 / 1,81:1);
 *  - `BOARD-TYRE-MINI-GREY` — ô lốp nén chưa phán được dùng MUT2 như bảng lốp.
 */
class Widget293ContrastTest {

    private fun c(hex: String) = ColorMath.parse(hex)
    private val palettes = listOf("TỐI" to KachiPalette.DARK, "SÁNG" to KachiPalette.LIGHT)

    @Test
    fun `icon o TAT - toi giu 0,72, sang mo it hon va lop chinh dat 4,5 tren ca hai dau`() {
        val got = palettes.associate { (name, p) ->
            val grounds = intArrayOf(c(p.surfFrom), c(p.surfTo))
            val a = IconFade.offAlpha(c(p.icon), grounds, 0.72)
            assertTrue(IconFade.passes(c(p.icon), grounds, a), "$name: lớp chính ở $a phải ≥ 4,5:1")
            assertTrue(a in 0.72..1.0, "$name: $a")
            name to a
        }
        // [ĐO bảng thật, bài này] tối: 0,73 (0,72 cũ chỉ thiếu sàn rất ít trên đầu chuyển sắc sáng hơn) · sáng: in ở dòng dưới.
        println("WIDGET-ICON-OFF-FAINT offAlpha $got")
        assertEquals(0.72, got.getValue("TỐI"), 0.0101, "chủ đề tối mặt nhỏ gần như không đổi (CLAUDE.md §6)")
        assertTrue(got.getValue("SÁNG") > 0.72 + 0.05, "chủ đề sáng phải mờ ÍT hơn hẳn 0,72")
    }

    @Test
    fun `ranh thanh keo dat 3 tren PANEL o ca hai chu de, doan da keo cung vay`() {
        palettes.forEach { (name, p) ->
            val a = IconFade.offAlpha(c(p.mut2), intArrayOf(c(p.panel)), 0.0, floor = RAIL_FLOOR)
            assertTrue(a < 1.0 && IconFade.passes(c(p.mut2), intArrayOf(c(p.panel)), a, RAIL_FLOOR), "$name: rãnh ở $a")
            assertTrue(ColorMath.ratio(c(p.accent), c(p.panel)) >= RAIL_FLOOR, "$name: đoạn đã kéo ACCENT trên PANEL")
            println("SLIDER-RAIL-CONTRAST $name rail alpha=$a accent=${"%.2f".format(ColorMath.ratio(c(p.accent), c(p.panel)))}:1")
        }
    }

    @Test
    fun `fadeOff noi vao tint cua o dieu khien va nho de refit tinh lai`() {
        val icons = SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/KachiIcons.kt")
        val fade = SourceRoots.body(icons, "fun fadeOff(img: ImageView, sizeDp: Int, active: Boolean, dim: Float)")
        assertTrue("IconFade.offAlpha(" in fade && "UNSELECTED_ALPHA" in fade, "mặt lớn chủ đề tối: bộ lọc đã mờ ⇒ inner")
        assertTrue("d.offDim?.let { fadeOff(img, dp, active = false, dim = it) }" in SourceRoots.body(icons, "fun refit(img: ImageView, sidePx: Int)"))
        val factory = SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/ControlTileFactory.kt")
        val macro = SourceRoots.body(factory, "fun macroTile(macro: ActionMacro): View")
        assertTrue(Regex("""applyBg\(tile, false\); tint\(icon, label, false\)""").findAll(macro).count() == 2,
            "gói lệnh lúc nghỉ: nền tắt + mực tắt (trước: mực 'đang bật' trên nền tắt)")
    }

    @Test
    fun `o lop nen chua phan duoc la MUT2`() {
        assertEquals(KachiTheme.MUT2, WidgetViews.tyreInk(TyreSeverity.NONE))
        assertEquals(KachiTheme.INK, WidgetViews.tyreInk(TyreSeverity.OK))
        assertEquals(KachiTheme.RED, WidgetViews.tyreInk(TyreSeverity.ALERT))
        assertEquals(KachiTheme.AMBER, WidgetViews.tyreInk(TyreSeverity.WARN))
    }
}
