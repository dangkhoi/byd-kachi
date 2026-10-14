package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 3.01 R22·C — icon hai nút chân ô Kính (owner 13/10: *"mấy cái cửa sổ cũng làm cho nó đẹp nha, tinh vi hơn"* → *"thêm 1 icon
 * mở all, 1 icon đóng all là xong"*). Khoá: icon là VectorDrawable SINH (đường ống `gen-icons.py`, không Canvas tự vẽ), và nét
 * chính của icon đạt ≥ 3:1 (WCAG 1.4.11 — đồ hoạ điều khiển) trên đĩa nghỉ của nút ở cả bảng Tối lẫn Sáng, cả lúc NHẤN.
 */
class WindowControlIconContractTest {

    private val compact = SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/ActionMatrixCompact.kt")

    @Test
    fun `icon chan la vector sinh tu nguon glyph`() {
        listOf("win_open_all_g", "win_half_all_g", "win_close_all_g").forEach { id ->
            assertTrue(SourceRoots.exists("../design/glyph/$id.svg"), "$id: thiếu nguồn design/glyph")
            assertTrue(SourceRoots.text("src/main/res/drawable/ic_$id.xml").contains("SINH BỞI scripts/design/gen-icons.py"), id)
            assertTrue(compact.contains("R.drawable.ic_$id"), "$id chưa được nút chân dùng")
        }
    }

    /**
     * 3.1.x LIGHT-CONTRAST (owner 14/10 "mờ" ở chủ đề sáng): mực đĩa/glyph nay ở [KachiButtonInks] (một chỗ cho chân Kính 2D, nút
     * xe 3D, hàng chân 3D). Glyph ≥ 4,5:1 trên đĩa (nghỉ + nhấn) trên mặt thẻ/ô — chi tiết theo nền ảnh ở `Widget301LightContrastTest`.
     */
    @Test
    fun `net icon dat 4,5 tren 1 tren dia nut o bang toi va sang, ca luc nhan`() {
        assertTrue(compact.contains("KachiButtonInks.background()") && compact.contains("KachiButtonInks.glyphTint()"), "chân Kính đi qua mực chung")
        listOf(KachiPalette.DARK to "Tối", KachiPalette.LIGHT to "Sáng").forEach { (p, name) ->
            val k = KachiButtonInks.of(p)
            listOf(p.card, p.tile).forEach { surface ->
                val rest = ColorMath.over(k.rest, ColorMath.parse(surface))
                val pressed = ColorMath.over(k.ripple, rest)
                val idle = ColorMath.ratio(ColorMath.parse(k.glyph), rest)
                val down = ColorMath.ratio(ColorMath.parse(k.glyphOn), pressed)
                assertTrue(idle >= 4.5, "$name: icon nghỉ ${"%.2f".format(idle)}:1 < 4,5:1")
                assertTrue(down >= 3.0, "$name: icon nhấn ${"%.2f".format(down)}:1 < 3:1")
            }
        }
        // Bảng tối giữ từng byte 3.1.4 (CLAUDE.md §6): đĩa mực 8 %, sóng nhấn 20 %.
        val dark = KachiButtonInks.of(KachiPalette.DARK)
        assertTrue(dark.rest == ColorMath.withAlpha(ColorMath.parse(KachiPalette.DARK.ink), REST.toInt(16)))
        assertTrue(dark.ripple == ColorMath.withAlpha(ColorMath.parse(KachiPalette.DARK.accent), RIPPLE.toInt(16)))
        assertTrue(dark.glyph == KachiPalette.DARK.icon && dark.glyphOn == KachiPalette.DARK.accentInk)
    }

    private companion object {
        const val REST = "14"
        const val RIPPLE = "33"
    }
}
