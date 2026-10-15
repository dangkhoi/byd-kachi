package com.byd.clusternav.launcher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable

/**
 * 3.01 DOT — bộ tô CHẤM TRÒN trạng thái trên ảnh xe, dùng chung cho bảng kính ([CarLevelsLayer]) và bảng cửa
 * ([DoorBoardView]). Owner 13/10: *"vẽ chấm tròn thôi, như bọn kinex, kimlauncher thấy nó đẹp hơn"*. Trạng thái quyết ở
 * `:core` ([CarDots]); tệp này chỉ tô:
 *  • nền chấm: đĩa tối mờ ([Inks.backing]) ngay dưới chấm (quầng mở nằm NGOÀI nền này) — ảnh xe có chỗ sáng (nóc bạc, phản chiếu) nên chấm cần một
 *    nền ổn định để giữ ≥ 3:1 [ĐO diagnostics `widget-visual-301` §9], không phải viền/khung;
 *  • CLOSED: vòng rỗng mảnh mực sáng · UNKNOWN: vòng ĐỨT nét mờ hơn · PARTIAL: vòng nhấn + quạt nhấn theo tỉ lệ ·
 *    OPEN: chấm đặc nhấn + quầng nhấn mờ (chỉ mở hẳn có quầng). [lit] (có lệnh đang bật ở chế độ hiện tại) ⇒ vòng mang màu nhấn.
 *
 * Màu theo ẢNH (ảnh xe không đổi theo chủ đề) chứ không theo thẻ: bảng sáng nâng nhấn về phía trắng ([ON_PHOTO_LIFT]) như
 * lớp kính R22·R đã đo (nhấn bảng sáng chỉ 1,7:1 trên ảnh). Mọi `Paint` cấp phát một lần; vẽ không cấp phát.
 */
internal class StateDotPainter(context: Context) {

    /** Bộ màu trên ảnh — thuần số (ColorMath) để bài canh tương phản tính được off-device. */
    data class Inks(
        val accent: Int, val closed: Int, val unknown: Int, val backing: Int, val openGlow: Int,
        /** 3.01 POLISH — mặt nút (đĩa tông) · nét đèn báo TẮT · viền bóng tối của đèn báo. */
        val tonal: Int = 0, val lampOff: Int = 0, val shadow: Int = 0,
        /** 3.1.x LIGHT-CONTRAST — hệ số nhân alpha của CHÍNH nét icon (lớp phụ 0,38/0,16 của glyph hai tông) · 1 = như cũ. */
        val glyphBoost: Float = 1f,
        /**
         * Đèn báo chỉ-xem ([drawLamp]): viền · mực "chưa đọc" · màu khi không có màu loại · bảng màu loại đèn → mực (ARGB → ARGB).
         * Tối = chính [shadow]/[unknown]/[accent], màu loại giữ nguyên. Sáng = viền SÁNG + mực TỐI (đèn báo đứng trên thẻ/ảnh sáng)
         * và màu loại đổi sang vai màu-mang-nghĩa bản sáng (`green`/`amber`/`accentInk` — đã tối sẵn, ≥ 4,5:1 trên trắng).
         */
        val lampEdge: Int = shadow, val lampUnknown: Int = unknown, val lampAccent: Int = accent,
        val lampTones: Map<Int, Int> = emptyMap(),
    ) {
        /** Mực đèn BẬT theo màu loại [argb] (`null` ⇒ [lampAccent]). */
        fun lampTone(argb: Int?): Int = argb?.let { lampTones[it] ?: it } ?: lampAccent

        /** Màu VẼ THẬT của nét icon [c]: alpha × [glyphBoost] kẹp 255 — đúng phép của bộ lọc ma trận ([ink]) cho nét chính. */
        fun drawnGlyph(c: Int): Int =
            if (glyphBoost == 1f) c else ColorMath.withAlpha(c, minOf(255, Math.round(ColorMath.alpha(c) * glyphBoost)))

        /**
         * 3.1.6 R29 (làm lại) — điểm của bộ màu NÀY cho một điểm vẽ ở hình [shape] trên nền ĐO [under] (ảnh nền xe 3D ∪ màu sơn dưới
         * điểm — `Car3dDotsLayer`): MIN (tỉ số tệ nhất / 3) trên mọi nét của hình, mỗi nét trên ĐÚNG các lớp bộ vẽ đặt dưới nó (chấm =
         * nền tối [backing]; nút = nền [shadow] + mặt tông [tonal]; đèn báo = hợp (ảnh, ảnh + viền [lampEdge]) (+ quầng khi BẬT)).
         * KHÔNG lớp đỡ: bộ màu nào (họ nào của mặt 3D) đọc được thì cả lớp phủ dùng nó. Thuần số — bài off-device chạy được.
         */
        fun score(under: ContrastBackdrop, shape: Shape, warn: Int): Double {
            fun m(ground: ContrastBackdrop, ink: Int) = ground.worst(ink) / InkRole.ICON.floor
            fun ContrastBackdrop.over(c: Int) = with(ContrastBackdrop.Layer.Flat(c))
            return when (shape) {
                Shape.DOT -> {   // [draw]: (quầng mở) → nền tối → vòng/quạt/chấm
                    val g = under.over(backing)
                    minOf(m(g, unknown), m(g, closed), m(g, accent), m(under.over(openGlow).over(backing), accent), m(g, warn),
                        m(under.over(ColorMath.withAlpha(warn, OPEN_GLOW_ALPHA)).over(backing), warn))
                }
                Shape.BUTTON -> {   // [drawButton]: (quầng ngoài + quầng khi BẬT) → nền nút → mặt tông → vòng + icon
                    val g = under.over(shadow).over(tonal)
                    val lit = under.over(ColorMath.withAlpha(accent, GLOW_OUTER_ALPHA)).over(openGlow).over(shadow).over(tonal)
                    minOf(m(g, drawnGlyph(unknown)), m(g, drawnGlyph(closed)), m(lit, drawnGlyph(accent)))
                }
                Shape.LAMP -> {   // [drawLamp]: (quầng khi BẬT) → viền bóng (9 bản lệch: chỗ có, chỗ không) → nét
                    fun edged(b: ContrastBackdrop) = ContrastBackdrop(b.ground().union(b.over(lampEdge).ground()))
                    val g = edged(under)
                    val tone = lampTone(null)
                    minOf(m(g, drawnGlyph(lampUnknown)), m(g, drawnGlyph(lampOff)),
                        m(edged(under.over(ColorMath.withAlpha(tone, LAMP_GLOW_ALPHA))), drawnGlyph(tone)))
                }
            }
        }

        /** Ba hình bộ vẽ tô — mỗi hình một chồng lớp dưới nét ([score]). */
        enum class Shape { DOT, BUTTON, LAMP }

        companion object {
            fun of(p: KachiPalette, night: Boolean): Inks {
                val ink = ColorMath.parse(p.accentInk)
                val light = ColorMath.parse(p.onAccent)
                val accent = if (night) ink else ColorMath.mix(light, ink, ON_PHOTO_LIFT)
                val base = Inks(
                    accent = accent,
                    closed = ColorMath.withAlpha(light, CLOSED_ALPHA),
                    unknown = ColorMath.withAlpha(light, UNKNOWN_ALPHA),
                    backing = ColorMath.withAlpha(ColorMath.parse(p.wallScrim), BACKING_ALPHA),
                    openGlow = ColorMath.withAlpha(accent, OPEN_GLOW_ALPHA),
                    tonal = ColorMath.withAlpha(light, TONAL_ALPHA),
                    lampOff = ColorMath.withAlpha(light, LAMP_OFF_ALPHA),
                    shadow = ColorMath.withAlpha(ColorMath.parse(p.wallScrim), SHADOW_ALPHA),
                    glyphBoost = if (night) 1f else LIGHT_GLYPH_BOOST,
                )
                if (night) return base
                return base.copy(
                    // Mặt nút đứng trên thẻ/nền SÁNG (tới trắng tinh) ⇒ nền nút đậm hơn: 80 % đen trên trắng còn 51 ⇒ glyph BẬT 4,34:1.
                    shadow = ColorMath.withAlpha(ColorMath.parse(p.wallScrim), LIGHT_SHADOW_ALPHA),
                    lampEdge = ColorMath.withAlpha(light, SHADOW_ALPHA),
                    lampOff = ColorMath.parse(p.ink),
                    lampUnknown = ColorMath.parse(p.mut),
                    lampAccent = ink,
                    lampTones = mapOf(
                        LampTone.Tone.AMBER.argb to ColorMath.parse(p.amber),
                        LampTone.Tone.GREEN.argb to ColorMath.parse(p.green),
                        LampTone.Tone.BLUE.argb to ink,
                    ),
                )
            }
        }
    }

    private val d = context.resources.displayMetrics.density
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = RING_DP * d }
    private val dash = DashPathEffect(floatArrayOf(DASH_DP * d, DASH_DP * d), 0f)
    private val arc = RectF()
    private var tinted: KachiPalette? = null
    private var inks = Inks.of(KachiTheme.palette, KachiTheme.night)
    private val boosted = HashMap<Int, ColorFilter>()

    /**
     * Tô chấm [dot] tâm ([cx],[cy]) đường kính [diameter] px. [warnColor] = màu sắc thái cảnh báo của vật (chỉ dùng khi
     * `dot.warn`).
     */
    fun draw(canvas: Canvas, cx: Float, cy: Float, diameter: Float, dot: CarDots.Dot, lit: Boolean, warnColor: Int) {
        retint()
        val r = diameter / 2f
        val tone = if (dot.warn) warnColor else inks.accent
        val open = dot.kind == CarDots.Kind.OPEN   // quầng CHỈ khi mở hẳn ⇒ 100 % không lẫn quạt lớn
        // Quầng (chỉ khi mở) nằm NGOÀI nền tối: chấm luôn đứng trên nền tối ⇒ tương phản không phụ thuộc quầng [ĐO §9].
        if (open) { fill.color = if (dot.warn) ColorMath.withAlpha(warnColor, OPEN_GLOW_ALPHA) else inks.openGlow; canvas.drawCircle(cx, cy, r + OPEN_GLOW_DP * d, fill) }
        fill.color = inks.backing
        canvas.drawCircle(cx, cy, r + BACKING_DP * d, fill)
        val inner = r - ring.strokeWidth / 2f
        when (dot.kind) {
            CarDots.Kind.OPEN -> { fill.color = tone; canvas.drawCircle(cx, cy, r, fill) }
            CarDots.Kind.PARTIAL -> {
                fill.color = tone
                val q = r - ring.strokeWidth - PIE_GAP_DP * d   // quạt thụt vào trong vòng một khe ⇒ đọc ra "miếng bánh"
                arc.set(cx - q, cy - q, cx + q, cy + q)
                canvas.drawArc(arc, START_DEG, CarDots.sweep(dot), true, fill)
                ring.pathEffect = null; ring.color = tone; canvas.drawCircle(cx, cy, inner, ring)
            }
            CarDots.Kind.CLOSED -> { ring.pathEffect = null; ring.color = if (lit) tone else inks.closed; canvas.drawCircle(cx, cy, inner, ring) }
            CarDots.Kind.UNKNOWN -> { ring.pathEffect = dash; ring.color = if (lit) tone else inks.unknown; canvas.drawCircle(cx, cy, inner, ring) }
        }
    }

    /** Bộ màu đang dùng (theo họ của mặt nếu có [family], không thì bảng hiện tại) — chỗ vẽ chấm điểm theo sự kiện, không trong `onDraw`. */
    fun inks(): Inks { retint(); return inks }

    /**
     * 3.01 R24 — ĐIỂM ICON (đèn trên hình xe). [scale] = nhịp nảy khi chạm. Không cấp phát. Chữ ký giữ nguyên (bảng 3D gọi).
     *
     * 3.01 POLISH (owner 13/10 *"phân biệt ra dạng icon cho hiểu"*) — hai HÌNH khác hẳn, không chỉ khác cỡ/độ mờ:
     *  • [tappable] = NÚT ([drawButton]): icon trong ĐĨA TÔNG đặc (nền tối + lớp mực mềm); BẬT ⇒ vòng nhấn + icon nhấn + quầng;
     *    TẮT ⇒ đĩa không vòng; CHƯA BIẾT ⇒ vòng đứt nét.
     *  • chỉ-xem = ĐÈN BÁO ([drawLamp]): chỉ hình đèn, KHÔNG đĩa/vòng (viền bóng tối sát nét để đọc được trên ảnh).
     */
    fun drawIcon(
        canvas: Canvas, cx: Float, cy: Float, diameter: Float, kind: CarDots.Kind, icon: Drawable?, scale: Float = 1f,
        tappable: Boolean = true,
    ) {
        if (tappable) drawButton(canvas, cx, cy, diameter * scale, kind, icon) else drawLamp(canvas, cx, cy, diameter, kind, icon, null, scale)
    }

    private fun drawButton(canvas: Canvas, cx: Float, cy: Float, diameter: Float, kind: CarDots.Kind, icon: Drawable?) {
        retint()
        val r = diameter / 2f
        val on = kind == CarDots.Kind.OPEN
        if (on) {
            fill.color = ColorMath.withAlpha(inks.accent, GLOW_OUTER_ALPHA); canvas.drawCircle(cx, cy, r + GLOW_DP * d, fill)
            fill.color = inks.openGlow; canvas.drawCircle(cx, cy, r + OPEN_GLOW_DP * d / 2f, fill)
        }
        // Nền nút tối hơn nền chấm ([Inks.shadow] 80 % thay 55 %): lớp tông sáng phủ lên làm nhạt mặt nút ⇒ [ĐO bài canh
        // LampIndicatorContractTest] icon nhấn trên mặt tông chỉ 2,55:1 ở ảnh sáng P95 khi nền 55 %.
        fill.color = inks.shadow
        canvas.drawCircle(cx, cy, r + BACKING_DP * d, fill)
        fill.color = inks.tonal                                    // mặt nút: đĩa tông đặc — thấy là NÚT kể cả khi tắt
        canvas.drawCircle(cx, cy, r, fill)
        val ink = when (kind) { CarDots.Kind.OPEN -> inks.accent; CarDots.Kind.UNKNOWN -> inks.unknown; else -> inks.closed }
        if (kind != CarDots.Kind.CLOSED) {                         // vòng chỉ khi BẬT (liền) hoặc CHƯA BIẾT (đứt)
            ring.pathEffect = if (kind == CarDots.Kind.UNKNOWN) dash else null
            ring.color = ink
            canvas.drawCircle(cx, cy, r - ring.strokeWidth / 2f, ring)
        }
        icon ?: return
        val ir = (r * ICON_OF_DISC).toInt()
        icon.setBounds((cx - ir).toInt(), (cy - ir).toInt(), (cx + ir).toInt(), (cy + ir).toInt())
        ink(icon, ink)
        icon.draw(canvas)
    }

    /**
     * 3.01 POLISH — ĐÈN BÁO chỉ-xem: hình đèn trần, không đĩa, không vòng. BẬT ⇒ sáng màu [lamp] (màu loại đèn — [LampTone];
     * `null` ⇒ màu nhấn) + quầng nhỏ cùng màu · TẮT ⇒ nét mờ [Inks.lampOff] · CHƯA BIẾT ⇒ mực "chưa đọc". Viền bóng tối
     * ([Inks.shadow]) = chính hình đèn tô tối vẽ 9 lần lệch [SHADOW_DP] (ảnh xe có chỗ sáng — giữ ≥ 3:1 mà không thành đĩa).
     * Tô lại tint KHÔNG làm VectorDrawable dựng lại bitmap (tint là bộ lọc lúc vẽ), bounds giữ nguyên ⇒ 9 lần chép bitmap.
     */
    fun drawLamp(
        canvas: Canvas, cx: Float, cy: Float, diameter: Float, kind: CarDots.Kind, icon: Drawable?, lamp: Int?, scale: Float = 1f,
    ) {
        retint()
        icon ?: return
        val ir = (diameter * scale / 2f * LAMP_OF_DISC).toInt()
        val on = kind == CarDots.Kind.OPEN
        val tone = inks.lampTone(lamp)
        if (on) { fill.color = ColorMath.withAlpha(tone, LAMP_GLOW_ALPHA); canvas.drawCircle(cx, cy, ir * LAMP_GLOW_OF_ICON, fill) }
        icon.setBounds((cx - ir).toInt(), (cy - ir).toInt(), (cx + ir).toInt(), (cy + ir).toInt())
        ink(icon, inks.lampEdge)
        val o = SHADOW_DP * d
        for (dx in -1..1) for (dy in -1..1) {
            canvas.save(); canvas.translate(dx * o, dy * o); icon.draw(canvas); canvas.restore()
        }
        ink(icon, when (kind) { CarDots.Kind.OPEN -> tone; CarDots.Kind.UNKNOWN -> inks.lampUnknown; else -> inks.lampOff })
        icon.draw(canvas)
    }

    /**
     * 3.1.x LIGHT-CONTRAST — tô [icon] bằng [color]. [Inks.glyphBoost] = 1 ⇒ `setTint` như cũ (bảng tối giữ từng byte). > 1 (bảng
     * sáng) ⇒ bộ lọc ma trận: RGB = mực, alpha = min(1, boost × alpha mực × alpha nét) — [ĐO máy ảo 14/10] glyph hai tông (thân xe
     * 0,16 · phụ 0,38 · chỉ phần ĐÈN 1,0) ở ~21 px chỉ còn vệt xám 1,4–2,6:1; nhân alpha đưa lớp phụ lên ~0,6–0,76, nét chính kẹp 1
     * (nên mực 80 %/60 % thành đặc — bật/tắt vẫn khác ở MÀU + vòng/quầng). Bộ lọc giữ theo màu ⇒ vẽ không cấp phát sau lần đầu.
     */
    private fun ink(icon: Drawable, color: Int) {
        if (inks.glyphBoost == 1f) { icon.colorFilter = null; icon.setTint(color); return }
        icon.colorFilter = boosted.getOrPut(color) {
            ColorMatrixColorFilter(ColorMatrix(floatArrayOf(
                0f, 0f, 0f, 0f, Color.red(color).toFloat(),
                0f, 0f, 0f, 0f, Color.green(color).toFloat(),
                0f, 0f, 0f, 0f, Color.blue(color).toFloat(),
                0f, 0f, 0f, inks.glyphBoost * Color.alpha(color) / 255f, 0f,
            )))
        }
    }

    /**
     * 3.1.6 R29 — HỌ mực của mặt chứa bộ vẽ (lớp phủ xe 3D: `Car3dDotsLayer` nhận từ [AdaptiveInk.probe]); `null` = theo chủ đề như
     * cũ (bảng 2D). Họ mực sáng ⇒ bộ màu kiểu Tối của bảng họ, họ mực tối ⇒ kiểu Sáng — cùng phép [Inks.of].
     */
    var family: InkFamily? = null
        set(v) { field = v; tinted = null }

    private fun retint() {
        if (tinted !== KachiTheme.palette) {
            tinted = KachiTheme.palette
            val f = family
            inks = if (f == null) Inks.of(KachiTheme.palette, KachiTheme.night) else Inks.of(f.palette, f.lightInk)
            boosted.clear()
        }
    }

    companion object {
        /** R24 — icon chiếm chừng này bán kính điểm · quầng ngoài của đèn BẬT (dp, mực nhấn 18 %). */
        const val ICON_OF_DISC = 0.58f
        const val GLOW_DP = 7f
        const val GLOW_OUTER_ALPHA = 0x2E
        /**
         * 3.01 POLISH — mặt nút: mực sáng 14 % trên nền tối · đèn báo: cạnh hình = 52 % đường kính điểm (KHÔNG đĩa
         * — 28 dp ⇒ hình đèn ~15 dp, nhỏ hơn icon trong nút 16 dp) · TẮT mực 60 % · viền bóng đen 80 % lệch 1 dp · quầng BẬT 25 %, bán
         * kính 1,25 × nửa cạnh hình.
         */
        const val TONAL_ALPHA = 0x24
        const val LAMP_OF_DISC = 0.52f
        const val LAMP_OFF_ALPHA = 0x99
        const val SHADOW_ALPHA = 0xCC
        const val SHADOW_DP = 1f
        const val LAMP_GLOW_ALPHA = 0x40
        const val LAMP_GLOW_OF_ICON = 1.25f
        /** 3.1.x LIGHT-CONTRAST — bảng sáng: alpha nét icon × 2 (lớp phụ 0,38 → 0,76; thân 0,16 → 0,32; nét chính kẹp 1). */
        const val LIGHT_GLYPH_BOOST = 2f
        /** Bảng sáng: nền nút 90 % (bảng tối 80 % — [SHADOW_ALPHA]). */
        const val LIGHT_SHADOW_ALPHA = 0xE6

        /**
         * Đường kính chấm (px) theo cạnh nhỏ khung xe [frameMinPx]: [RATIO] × cạnh, kẹp [MIN_DP]..[MAX_DP] (10–12 dp) — theo ô
         * mà không thành mảng màu ở ô lớn. Sàn 10 dp (không 8): owner 13/10 bỏ số ⇒ quạt 35 % phải tự đọc được ở ô 2×3 [ĐO máy
         * ảo: ở 8 dp quạt chỉ còn vài px].
         */
        fun diameterPx(frameMinPx: Float, density: Float): Float =
            (frameMinPx * RATIO).coerceIn(MIN_DP * density, MAX_DP * density)

        const val MIN_DP = 10f
        const val MAX_DP = 12f
        const val RATIO = 0.14f

        /** Vòng 1,25 dp · nét đứt 1,5 dp (chưa đọc) · nền tối thò 1,5 dp quanh chấm · quầng mở (trạng thái MỞ, không phải lớp đỡ) tới 4 dp. */
        const val RING_DP = 1.25f
        const val DASH_DP = 1.5f
        const val BACKING_DP = 1.5f
        const val OPEN_GLOW_DP = 4f
        /** Khe giữa vòng và quạt (mở một phần). */
        const val PIE_GAP_DP = 0.75f
        /** Mực vòng đóng 80 % · chưa đọc 55 % (cộng nét đứt) · nền tối 55 % · quầng nhấn 35 %. */
        const val CLOSED_ALPHA = 0xCC
        const val UNKNOWN_ALPHA = 0x8C
        const val BACKING_ALPHA = 0x8C
        const val OPEN_GLOW_ALPHA = 0x59
        /** Quạt bắt đầu ở 12 giờ. */
        const val START_DEG = -90f
        /** Bảng sáng: nhấn trên ảnh = 40 % nhấn + 60 % trắng (`ColorMath.mix(trắng, nhấn, t)`). 3.1.x LIGHT-CONTRAST: 0,45 → 0,40 — glyph BẬT trên mặt nút ở ảnh sáng đỉnh chỉ 4,48:1. */
        const val ON_PHOTO_LIFT = 0.40
    }
}
