package com.byd.clusternav.launcher

import android.content.Context
import android.graphics.Canvas
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
        val accent: Int, val closed: Int, val unknown: Int, val backing: Int, val halo: Int,
        /** 3.01 POLISH — mặt nút (đĩa tông) · nét đèn báo TẮT · viền bóng tối của đèn báo. */
        val tonal: Int = 0, val lampOff: Int = 0, val shadow: Int = 0,
    ) {
        companion object {
            fun of(p: KachiPalette, night: Boolean): Inks {
                val ink = ColorMath.parse(p.accentInk)
                val light = ColorMath.parse(p.onAccent)
                val accent = if (night) ink else ColorMath.mix(light, ink, ON_PHOTO_LIFT)
                return Inks(
                    accent = accent,
                    closed = ColorMath.withAlpha(light, CLOSED_ALPHA),
                    unknown = ColorMath.withAlpha(light, UNKNOWN_ALPHA),
                    backing = ColorMath.withAlpha(ColorMath.parse(p.wallScrim), BACKING_ALPHA),
                    halo = ColorMath.withAlpha(accent, HALO_ALPHA),
                    tonal = ColorMath.withAlpha(light, TONAL_ALPHA),
                    lampOff = ColorMath.withAlpha(light, LAMP_OFF_ALPHA),
                    shadow = ColorMath.withAlpha(ColorMath.parse(p.wallScrim), SHADOW_ALPHA),
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

    /**
     * Tô chấm [dot] tâm ([cx],[cy]) đường kính [diameter] px. [warnColor] = màu sắc thái cảnh báo của vật (chỉ dùng khi
     * `dot.warn`).
     */
    fun draw(canvas: Canvas, cx: Float, cy: Float, diameter: Float, dot: CarDots.Dot, lit: Boolean, warnColor: Int) {
        if (tinted !== KachiTheme.palette) { tinted = KachiTheme.palette; inks = Inks.of(KachiTheme.palette, KachiTheme.night) }
        val r = diameter / 2f
        val tone = if (dot.warn) warnColor else inks.accent
        val open = dot.kind == CarDots.Kind.OPEN   // quầng CHỈ khi mở hẳn ⇒ 100 % không lẫn quạt lớn
        // Quầng (chỉ khi mở) nằm NGOÀI nền tối: chấm luôn đứng trên nền tối ⇒ tương phản không phụ thuộc quầng [ĐO §9].
        if (open) { fill.color = if (dot.warn) ColorMath.withAlpha(warnColor, HALO_ALPHA) else inks.halo; canvas.drawCircle(cx, cy, r + HALO_DP * d, fill) }
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
            fill.color = inks.halo; canvas.drawCircle(cx, cy, r + HALO_DP * d / 2f, fill)
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
        icon.setTint(ink)
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
        val tone = lamp ?: inks.accent
        if (on) { fill.color = ColorMath.withAlpha(tone, LAMP_GLOW_ALPHA); canvas.drawCircle(cx, cy, ir * LAMP_GLOW_OF_ICON, fill) }
        icon.setBounds((cx - ir).toInt(), (cy - ir).toInt(), (cx + ir).toInt(), (cy + ir).toInt())
        icon.setTint(inks.shadow)
        val o = SHADOW_DP * d
        for (dx in -1..1) for (dy in -1..1) {
            canvas.save(); canvas.translate(dx * o, dy * o); icon.draw(canvas); canvas.restore()
        }
        icon.setTint(when (kind) { CarDots.Kind.OPEN -> tone; CarDots.Kind.UNKNOWN -> inks.unknown; else -> inks.lampOff })
        icon.draw(canvas)
    }

    private fun retint() {
        if (tinted !== KachiTheme.palette) { tinted = KachiTheme.palette; inks = Inks.of(KachiTheme.palette, KachiTheme.night) }
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

        /** Vòng 1,25 dp · nét đứt 1,5 dp (chưa đọc) · nền tối thò 1,5 dp quanh chấm · quầng mở tới 4 dp. */
        const val RING_DP = 1.25f
        const val DASH_DP = 1.5f
        const val BACKING_DP = 1.5f
        const val HALO_DP = 4f
        /** Khe giữa vòng và quạt (mở một phần). */
        const val PIE_GAP_DP = 0.75f
        /** Mực vòng đóng 80 % · chưa đọc 55 % (cộng nét đứt) · nền tối 55 % · quầng nhấn 35 %. */
        const val CLOSED_ALPHA = 0xCC
        const val UNKNOWN_ALPHA = 0x8C
        const val BACKING_ALPHA = 0x8C
        const val HALO_ALPHA = 0x59
        /** Quạt bắt đầu ở 12 giờ. */
        const val START_DEG = -90f
        /** Bảng sáng: nhấn trên ảnh = 45 % nhấn + 55 % trắng (`ColorMath.mix(trắng, nhấn, t)`), cùng số lớp kính R22·R. */
        const val ON_PHOTO_LIFT = 0.45
    }
}
