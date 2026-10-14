package com.byd.clusternav.launcher

import com.byd.clusternav.launcher.ContrastBackdrop.Layer

/**
 * ═══ 3.1.5 R29 pha 2 — PHẦN THUẦN của mực thích ứng ở `:app`: vai mực của bảng màu SỐNG · ứng viên · chồng lớp ═════════
 *
 * Spec `kachi-301-cast-steps-dialog-ia.html` §4.7 (b)(c) · §5.1 T29-4/5/7. Lõi đo/giải ở `:core` ([LumaField] ·
 * [ContrastBackdrop] · [ContrastInk]); tệp này chỉ nói hai điều mà `:core` không được biết: (1) MÃ MÀU — mực ứng viên lấy từ
 * [KachiPalette] đang dùng + bảng kia, (2) CHỒNG LỚP — dựng đúng thứ tự các lớp mà mã vẽ thật vẽ (`WallView` +
 * `WallWindowDrawable.draw` + bề mặt). Không Android (chỉ [ColorMath.parse]) ⇒ `AdaptiveContrastContractTest` chạy nguyên
 * hàm này trên bảng màu SỐNG, không trên một bản chụp. Phép GHÉP chồng (không mã màu) ở `:core` ([ContrastStack]).
 *
 * Phần dính view (đánh dấu · lưu kết quả trên view · lớp đỡ · log) ở [AdaptiveInk].
 */
enum class InkKey(val pick: (KachiPalette) -> String, val semantic: Boolean) {
    INK({ it.ink }, false),
    INK2({ it.ink2 }, false),
    MUT({ it.mut }, false),
    MUT2({ it.mut2 }, false),
    ICON({ it.icon }, false),
    INK_ON_ACCENT({ it.inkOnAccent }, false),
    ACCENT_INK({ it.accentInk }, true),
    GREEN({ it.green }, true),
    AMBER({ it.amber }, true),
    RED({ it.red }, true),
    CYAN({ it.cyan }, true),
    ORANGE({ it.orange }, true);

    companion object {
        /**
         * Vai của một mã màu VỪA đọc từ bảng [p] (chỗ gọi truyền `KachiTheme.MUT`… — chính chuỗi của bảng). Không khớp vai nào
         * (màu tự trộn alpha, màu của app khác) ⇒ `null` ⇒ chỗ gọi tô đúng mã đó, không đoán vai.
         */
        fun of(hex: String, p: KachiPalette): InkKey? = entries.firstOrNull { it.pick(p).equals(hex, ignoreCase = true) }
    }
}

object InkModel {

    /** Sàn "chữ lớn" WCAG 1.4.3: ≥ 18 pt hoặc ≥ 14 pt đậm — quy về sp như CSS px (bảo thủ: ít chữ được coi là lớn). */
    const val LARGE_SP = 24f
    const val LARGE_BOLD_SP = 18.66f

    /** Vai WCAG của mực [key] trên một phần tử: [icon] = nét đồ hoạ (1.4.11, 3:1); [large] = chữ lớn (3:1). */
    fun role(key: InkKey, icon: Boolean, large: Boolean): InkRole = when {
        icon -> if (key == InkKey.ACCENT_INK) InkRole.ACCENT_ICON else InkRole.ICON
        large -> InkRole.LARGE_TEXT
        key == InkKey.MUT || key == InkKey.MUT2 -> InkRole.SECONDARY_TEXT
        key == InkKey.ACCENT_INK -> InkRole.ACCENT_TEXT
        else -> InkRole.TEXT
    }

    /** Bảng đối cực của [p] (Sáng ↔ Tối) — theo độ chói nền, không theo cờ (bảng suy theo màu nhấn vẫn đúng cực). */
    fun other(p: KachiPalette): KachiPalette = if (light(p)) KachiPalette.DARK else KachiPalette.LIGHT

    fun light(p: KachiPalette): Boolean = Luma.of(ColorMath.parse(p.bg)) > 0.18

    /**
     * Ứng viên theo thứ tự ưu tiên (spec §4.7 (b), cùng thứ tự `ContrastThemeFixtures.candidates` của bài `:core`):
     *  • vai trung tính: mực cùng vai của chủ đề → mực chính của chủ đề → cùng vai bảng kia → mực chính bảng kia
     *    (chữ phụ chỉ "lên" mực chính khi chính nó không đạt — giữ phân cấp);
     *  • vai MANG NGHĨA (nhấn · xanh/hổ phách/đỏ…): cùng vai bảng kia đứng TRƯỚC mực chính — đổi cực mà giữ nghĩa (đỏ vẫn là đỏ)
     *    tốt hơn giữ cực mà mất nghĩa.
     * Lớp đỡ đi kèm: mực sáng ⇒ nền bảng Tối, mực tối ⇒ nền bảng Sáng (đục). Trùng mã ⇒ giữ lần đầu.
     */
    fun candidates(key: InkKey, p: KachiPalette): List<InkCandidate> {
        val o = other(p)
        val order = if (key.semantic) listOf(key.pick(p), key.pick(o), p.ink, o.ink) else listOf(key.pick(p), p.ink, key.pick(o), o.ink)
        return order.map { ColorMath.withAlpha(ColorMath.parse(it), 255) }.distinct().map { InkCandidate(it, haloFor(it, p)) }
    }

    /** Lớp đỡ đi kèm mực [ink]: mực sáng ⇒ nền bảng Tối, mực tối ⇒ nền bảng Sáng (đục) — một luật cho mọi chỗ đỡ. */
    fun haloFor(ink: Int, p: KachiPalette): Int {
        val o = other(p)
        val dark = if (light(p)) o.bg else p.bg
        val bright = if (light(p)) p.bg else o.bg
        return ColorMath.withAlpha(ColorMath.parse(if (Luma.of(ColorMath.withAlpha(ink, 255)) > 0.18) dark else bright), 255)
    }

    // ═══ 3.1.5 R29 pha 2b · T29-6 — lớp phủ xe 3D (spec §5.1 T29-6) ═══════════════════════════════════════════════════

    /**
     * Hộp màu SƠN xe trên màn: thân xe không phải một màu phẳng — shader AVM [ĐO KDoc `Car3dPaint`] tô `màu·(0,4 + 0,5·khuếch tán)`
     * + phản chiếu ⇒ từ [PAINT_SHADE] × màu (mặt khuất) tới màu nhích về trắng [PAINT_LIFT] (vệt sáng) [SUY — Filament khác AVM,
     * đo lại ở máy ảo T29-10]. Đây là `span(màu sơn)` của spec §4.7 T29-6, nới theo bóng đổ để không khai đạt ở mặt khuất.
     */
    fun paintSpan(argb: Int): ToneBox {
        val c = ColorMath.withAlpha(argb, 255)
        return ToneBox.span(ColorMath.mix(c, ColorMath.argb(255, 0, 0, 0), 1 - PAINT_SHADE), c, ColorMath.mix(c, ColorMath.argb(255, 255, 255, 255), PAINT_LIFT))
    }

    const val PAINT_SHADE = 0.4
    const val PAINT_LIFT = 0.25

    /**
     * Biến thể lớp của khung 3D dưới một vùng (thứ tự vẽ `Car3dWidgetView`: ảnh nền xe → bóng đất → xe):
     *  • có ảnh ([photo] = hộp ảnh dưới vùng, đục) ⇒ lớp ẢNH ĐỤC = [photo] ∪ [paint] (nền ① spec: hộp vùng ∪ span(màu sơn));
     *    ảnh không phủ trọn vùng ([covers] = false) ⇒ thêm biến thể "thẻ lộ ra" (không lớp) và "xe trên thẻ" ([paint] đục);
     *  • không ảnh ⇒ thẻ lộ ra · xe trên thẻ (vùng chạm khung xe — [paint] ≠ `null`);
     *  • vùng chạm bóng đất ([shadow] ≠ 0) ⇒ mỗi biến thể thêm một bản có bóng (đen [shadow] → trong suốt, đơn điệu ⇒ hai đầu đủ).
     */
    fun car3dVariants(photo: ToneBox?, covers: Boolean, paint: ToneBox?, shadow: Int): List<List<Layer>> {
        val out = ArrayList<List<Layer>>()
        if (photo != null) out += listOf(Layer.Photo(if (paint != null) photo.union(paint) else photo, 1.0))
        if (photo == null || !covers) {
            out += emptyList<Layer>()
            if (paint != null) out += listOf(Layer.Photo(paint, 1.0))
        }
        if (ColorMath.alpha(shadow) > 0) out += out.map { it + Layer.Flat(shadow) }
        return out
    }

    /**
     * Lớp đỡ (ARGB, 0 = không cần) cho một nét [ink] vai [role] vẽ trên nền [ground] của một bộ vẽ CANVAS (chấm/đèn/nút trên xe
     * 3D): cùng bộ giải [ContrastInk.resolve], một ứng viên = chính mực thiết kế (giữ nghĩa màu), lớp đỡ theo [haloFor].
     * Trả cả [Resolution]: `ok = false` (không mức đỡ nào đạt) ⇒ bên gọi log — không im lặng dưới sàn.
     */
    fun overlayHalo(role: InkRole, ground: ContrastBackdrop, ink: Int, p: KachiPalette): Resolution =
        ContrastInk.resolve(role, ground, listOf(InkCandidate(ink, haloFor(ink, p))))

    /** Lớp làm tối ảnh nền ở [dimPct] % — cùng phép `WallView.drawPhoto`/`WallArtBuilder` (alpha = dim·255/100). */
    fun scrim(p: KachiPalette, dimPct: Int): Layer? {
        val d = dimPct.coerceIn(0, 90)
        return if (d <= 0) null else Layer.Flat(ColorMath.withAlpha(ColorMath.parse(p.wallScrim), d * 255 / 100))
    }

    /**
     * Hai dải che đỉnh/đáy của `WallView` dưới vùng `[top, bottom)` — màu dải = `BAR_TOP` của bảng [p] × độ đục chung [fade]
     * (cùng phép `WallView.onSizeChanged`); phép lấy mẫu ở [ContrastStack.bandVariants].
     */
    fun bandVariants(p: KachiPalette, fade: Double, top: Int, bottom: Int, screenH: Int, fraction: Float): List<List<Layer>> {
        if (fade <= 0.0) return listOf(emptyList())
        val raw = ColorMath.parse(p.barTop)
        return ContrastStack.bandVariants(ColorMath.withAlpha(raw, ChromeOpacity.drawnAlpha(ColorMath.alpha(raw), fade)), top, bottom, screenH, fraction)
    }

    /** Hộp nền KHÔNG ảnh (`WallView` vẽ `bg` + hai vầng sáng `glow1/glow2` → trong suốt): phủ nền + mỗi vầng + cả hai chồng. */
    fun plainWall(p: KachiPalette): ToneBox {
        val bg = ColorMath.withAlpha(ColorMath.parse(p.bg), 255)
        val g1 = ColorMath.over(ColorMath.parse(p.glow1), bg)
        val g2 = ColorMath.over(ColorMath.parse(p.glow2), bg)
        return ToneBox.span(bg, g1, g2, ColorMath.over(ColorMath.parse(p.glow2), g1))
    }
}
