package com.byd.clusternav.launcher

/**
 * R29 — ẢNH CHỤP bảng màu Kachi (Sáng/Tối) ngày 14/10 cho bài thuần `:core`. Mã màu THẬT chỉ sống ở `KachiPalette` (`:app`);
 * bản chụp này chỉ để kiểm bộ giải trên các vai có thật. Pha 2 (§R29 T29-9) chạy cùng tính chất trên bảng màu SỐNG ở `:app`
 * (`AdaptiveContrastContractTest`), nên bảng đổi mà bản chụp này lệch cũng không che được lỗi.
 *
 * Nguồn: `KachiPalette.kt` (DARK :317–:398 · LIGHT :419–:490) + `KachiPaletteSeeds` (`DARK_RAMP`/`LIGHT_RAMP`).
 */
internal class ThemeFixture(
    val name: String,
    val bg: Int,
    val ink: Int,
    val mut: Int,
    val mut2: Int,
    val accentInk: Int,
    val wallScrim: Int,
    val surfFromOverArt: Int,
    val surfToOverArt: Int,
) {
    companion object {
        private fun c(hex: String) = ColorMath.parse(hex)
        private val DARK_RAMP = SurfaceRamp.of(base = "#141b30", lift = "#c6d0ff", sink = "#000000", step = 0.04)
        private val LIGHT_RAMP = SurfaceRamp.of(base = "#eef1f8", lift = "#ffffff", sink = "#d9dfe9", step = 0.5)

        val DARK = ThemeFixture(
            "TỐI", c(DARK_RAMP.at(0)), c("#eaf0f8"), c("#9daabe"), c("#99a4b6"), c("#8ab2ff"), c("#000000"),
            c(DARK_RAMP.at(2, 0xcc)), c(DARK_RAMP.at(-5, 0xcc)),
        )
        val LIGHT = ThemeFixture(
            "SÁNG", c(LIGHT_RAMP.at(0)), c("#0f1620"), c("#4c5869"), c("#54606f"), c("#2b52d1"), c("#000000"),
            c(LIGHT_RAMP.at(2, 0xcc)), c(LIGHT_RAMP.at(1, 0xcc)),
        )
        val BOTH = listOf(DARK, LIGHT)
    }

    /** Bảng kia (để lấy mực đối cực). */
    val other: ThemeFixture get() = if (this === DARK) LIGHT else DARK

    /** Mực TỐI hay SÁNG của bảng này — quyết định màu lớp đỡ đi kèm. */
    private fun haloFor(ink: Int): Int = if (Luma.of(ink) > 0.18) DARK.bg else LIGHT.bg

    private fun cand(ink: Int) = InkCandidate(ink, haloFor(ink))

    /**
     * Ứng viên theo vai — đúng thứ tự pha 2 sẽ dựng ở `:app` (§R29 Design b): mực cùng vai của chủ đề → mực chính của chủ
     * đề → cùng vai của bảng kia → mực chính bảng kia. Chữ phụ chỉ "lên" mực chính khi chính nó không đạt.
     */
    fun candidates(role: InkRole): List<InkCandidate> = when (role) {
        InkRole.TEXT, InkRole.LARGE_TEXT, InkRole.ICON -> listOf(cand(ink), cand(other.ink))
        InkRole.SECONDARY_TEXT -> listOf(cand(mut), cand(ink), cand(other.mut), cand(other.ink))
        InkRole.ACCENT_TEXT, InkRole.ACCENT_ICON -> listOf(cand(accentInk), cand(other.accentInk), cand(ink), cand(other.ink))
    }

    /** Lớp làm tối ảnh nền ở mức [dimPct] % (cùng phép `WallArtBuilder`/`WallView`: `wallScrim` alpha = dim·255/100). */
    fun scrim(dimPct: Int) = ContrastBackdrop.Layer.Flat(ColorMath.withAlpha(wallScrim, dimPct.coerceIn(0, 90) * 255 / 100))

    /**
     * Chồng của THẺ KÍNH (`WallWindowDrawable.draw` + bề mặt): ảnh mờ × [fade] → lớp che `bg`@[veil]×[fade] → bề mặt
     * `surf*OverArt` × [fade]. Bỏ lớp nhuộm màu trội (alpha nhỏ, sắc — bài pha 2 đo trên chồng thật).
     */
    fun card(glass: ToneBox, fade: Double, veil: Double = GlassVeil.MIN, top: Boolean = true): List<ContrastBackdrop.Layer> {
        val surf = if (top) surfFromOverArt else surfToOverArt
        return listOf(
            ContrastBackdrop.Layer.Photo(glass, fade),
            ContrastBackdrop.Layer.Flat(ColorMath.withAlpha(bg, ContrastInk.alphaByte(veil * fade))),
            ContrastBackdrop.Layer.Flat(ColorMath.scaleAlpha(surf, fade)),
        )
    }
}
