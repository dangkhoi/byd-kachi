package com.byd.clusternav.testsupport

import com.byd.clusternav.launcher.ColorMath
import com.byd.clusternav.launcher.GlassVeil

/**
 * ═══ FIX286 · OQ8 phương án B (chốt 2026-10-03) — mô hình THUẦN của nút ⇄ ô trống nằm trên ĐĨA KÍNH ═══════════════
 *
 * Spec `docs/specs/kachi-286-field-fixes.html` · R-OQ8. Hai bài canh đo CÙNG một con số qua đây (không chép công thức
 * hai lần): `ThemePaletteContractTest.o trong nhan ra duoc…` (sàn 3:1 — WCAG 1.4.11, thành phần không phải chữ) và
 * `SurfaceContrastContractTest.sinh bang do…` (dòng bảng đo tài liệu).
 *
 * Chồng lớp đúng như máy vẽ (`SlotSwapButton.centered(disc = true)` → `KachiGlass.apply(…, NEUTRAL)`):
 *  • **có ảnh** ⇒ `WallWindowDrawable`: ảnh mờ → lớp che `veil @ GlassVeil.alphaFor(L)` → nhuộm màu trội
 *    [TINT_ALPHA] → bề mặt `surf*OverArt` (80 %). Ảnh mô hình bằng xám cùng độ chói (cùng lẽ [GlassVeil]); màu nhuộm
 *    lấy hai CỰC trắng/đen vì màu trội là của ảnh người dùng chọn;
 *  • **không ảnh** ⇒ `KachiTheme.surface(NEUTRAL)` trên nền `WallView` (nền chủ đề + hai vầng sáng).
 *
 * Giới hạn đã biết: ảnh dưới đĩa là ảnh MỜ nhưng không đồng đều tuyệt đối — phần dư ấy chỉ đo được trên máy (bảng
 * `docs/diagnostics/fix286-oq8-disc-emulator-2026-10-03/`).
 */
object SwapDiscModel {

    /** Alpha lớp nhuộm màu trội — cùng số với `WallWindowDrawable.TINT_ALPHA` (bài nối dây ghim nguyên văn bên đó). */
    const val TINT_ALPHA = 0x0f

    /** Tương phản TỆ NHẤT của [ink] trên đĩa có ảnh: mọi L ∈ [0, 1] bước 0,01 × nhuộm ∈ {không, trắng, đen}. */
    fun worstOverArt(ink: Int, veil: Int, surfaces: IntArray, veilInks: IntArray): Double {
        val tints = listOf(null, ColorMath.withAlpha(0xffffff, TINT_ALPHA), ColorMath.withAlpha(0x000000, TINT_ALPHA))
        var worst = Double.MAX_VALUE
        for (i in 0..100) {
            val l = i / 100.0
            val alpha = GlassVeil.alphaFor(l, veil, surfaces, veilInks)
            val veiled = ColorMath.over(ColorMath.withAlpha(veil, (alpha * 255).toInt()), ColorMath.grayOfLuminance(l))
            for (t in tints) {
                val under = if (t == null) veiled else ColorMath.over(t, veiled)
                for (s in surfaces) worst = minOf(worst, ColorMath.ratio(ink, ColorMath.over(s, under)))
            }
        }
        return worst
    }

    /** Tương phản TỆ NHẤT của [ink] trên đĩa không ảnh: mọi bề mặt [surfaces] trên mọi nền [grounds] của `WallView`. */
    fun worstNoArt(ink: Int, surfaces: IntArray, grounds: List<Int>): Double =
        grounds.minOf { g -> surfaces.minOf { s -> ColorMath.ratio(ink, ColorMath.over(s, g)) } }
}
