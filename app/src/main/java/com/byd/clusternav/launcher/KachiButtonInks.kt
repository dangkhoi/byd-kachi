package com.byd.clusternav.launcher

import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.byd.clusternav.launcher.ContrastBackdrop.Layer
import java.util.WeakHashMap

/**
 * ═══ 3.1.x LIGHT-CONTRAST — mực của NÚT TRÒN CHỈ-ICON (chân bảng Kính 2D · nút cốp/chân xe 3D · hàng chân xe 3D) ═══════════
 *
 * Owner 14/10 (xe chạy chủ đề SÁNG ban ngày, log `chủ đề=sáng`): *icon và nút 3.1 "mờ", sai màu*. [ĐO máy ảo emulator-5556,
 * diagnostics `widget-visual-301` §10] ba bản chép tay của cùng một nút (đĩa `INK` 8 % + glyph `ICON`) cho ở bảng SÁNG:
 * đĩa/nền **1,16:1** (168 vs 181), glyph/đĩa **2,2–3,3:1** (−/+ mảnh chỉ 1,7). Gốc: đĩa "mực 8 %" là công thức của bảng TỐI
 * (mực sáng nhấc đĩa lên khỏi nền tối); ở bảng sáng nền dưới nút là ẢNH/nền mặc định xám trung (L ≈ 0,45–0,55) — mực tối 8 % chỉ
 * làm xám thêm một bậc, glyph xám `ICON` thì nằm giữa hai mức xám đó.
 *
 * Một chỗ quyết ([of]), thuần số (chạy được off-device — bài `Widget301LightContrastTest`):
 *  • bảng TỐI ⇒ GIỮ NGUYÊN từng byte (đĩa `INK` 8 %, glyph `ICON`, nhấn/bật `ACCENT_INK`) — đường đang ổn (CLAUDE.md §6);
 *  • bảng SÁNG ⇒ đĩa KÍNH TỐI (`wallScrim` 60 %, cùng họ đĩa `scrimBtn` của icon ghế xe 3D) + glyph `ON_ACCENT`; BẬT ⇒ đĩa
 *    nhấn đậm (`GRAD_FROM` trộn `INK_ON_ACCENT`) + glyph `ON_ACCENT` (bật/tắt khác nhau ở MẶT đĩa, không chỉ ở màu nét).
 * Chọn theo ĐỘ SÁNG NỀN của chính bảng màu (`bg`), không theo cờ đêm — bảng nào nền sáng thì dùng công thức nền sáng.
 *
 * 3.1.6 R29 (làm lại 15/10) — nút là BỘ VẼ TỰ TÔ của mặt chứa nó ([AdaptiveInk.probe]): công thức đĩa/glyph theo HỌ mực của mặt
 * ([of] (p, họ)), không theo từng nút ⇒ mọi nút trên một mặt cùng công thức, đổi cùng chữ của mặt. Nút chỉ GÓP điểm (glyph trên
 * đĩa trên nền ĐO dưới nút) vào phép chọn họ; KHÔNG còn "đĩa đậm thêm" theo từng nút (owner 15/10: không vá từng phần tử). Hai
 * công thức là hai mặt nút đã thiết kế: tông mềm (bảng Tối, mực sáng — giữ nguyên byte) và kính tối (mọi trường hợp khác).
 */
internal data class KachiButtonInks(
    /** Đĩa nghỉ · đĩa BẬT (ARGB). */
    val rest: Int, val on: Int,
    /** Glyph nghỉ · glyph BẬT/NHẤN (hex vai — cùng kiểu `KachiIcons.tint`). */
    val glyph: String, val glyphOn: String,
    /** Sóng chạm (ARGB). */
    val ripple: Int,
) {
    companion object {
        /** Đĩa nghỉ bảng tối: mực 8 % (giữ nguyên 3.1.4). */
        const val DARK_REST_ALPHA = 0x14
        /** Đĩa kính tối bảng sáng: 60 % — [TÍNH] trên nền trắng tinh glyph trắng 5,6:1 (50 % chỉ 3,95 — KDoc `scrimBtn`). */
        const val LIGHT_REST_ALPHA = 0x99
        const val RIPPLE_ALPHA = 0x33
        /** Bảng sáng: đĩa BẬT = `GRAD_FROM` trộn `INK_ON_ACCENT` theo tỉ lệ này (0 = GRAD_FROM). */
        const val LIGHT_ON_DEEPEN = 0.5
        /** Nền bảng sáng hơn mức này ⇒ công thức nền sáng. */
        const val LIGHT_BG_LUMINANCE = 0.5

        fun lightSurface(p: KachiPalette): Boolean = ColorMath.luminance(ColorMath.parse(p.bg)) > LIGHT_BG_LUMINANCE

        /** Mặc định CHƯA ĐO (nút vừa dựng): theo độ sáng nền của bảng — xem KDoc lớp. */
        fun of(p: KachiPalette): KachiButtonInks = if (!lightSurface(p)) soft(p) else glass(p)

        fun current(): KachiButtonInks = of(KachiTheme.palette)

        /** Công thức bảng TỐI (đĩa mực 8 % · glyph `ICON` · BẬT `ACCENT_INK`). */
        private fun soft(p: KachiPalette): KachiButtonInks {
            val rest = ColorMath.withAlpha(ColorMath.parse(p.ink), DARK_REST_ALPHA)
            return KachiButtonInks(rest, rest, p.icon, p.accentInk, ColorMath.withAlpha(ColorMath.parse(p.accent), RIPPLE_ALPHA))
        }

        /**
         * Công thức nền SÁNG (đĩa kính tối [restAlpha] · glyph `ON_ACCENT` · BẬT nhấn đậm = nửa `GRAD_FROM` + nửa `INK_ON_ACCENT`:
         * `GRAD_FROM` đặc chỉ 1,9:1 trên nền xám trung L 0,30 [TÍNH]).
         */
        private fun glass(p: KachiPalette, restAlpha: Int = LIGHT_REST_ALPHA): KachiButtonInks {
            val mixed = ColorMath.mix(ColorMath.parse(p.gradFrom), ColorMath.parse(p.inkOnAccent), LIGHT_ON_DEEPEN)
            // Đĩa BẬT đục ⇒ không phụ thuộc nền: glyph trắng chưa đạt (bảng Tối: `INK_ON_ACCENT` sáng làm nhấn nhạt — [ĐO bài
            // AdaptiveContrastContractTest] 2,25) ⇒ phủ kính tối lên đĩa tới mức nhỏ nhất đạt. Bảng Sáng đạt sẵn ⇒ giữ nguyên byte.
            val scrim = ColorMath.withAlpha(ColorMath.parse(p.wallScrim), 255)
            // Glyph trên kính TỐI phải là mực SÁNG: bảng suy theo màu nhấn sáng (vàng…) có `ON_ACCENT` tối ⇒ lấy `ON_ACCENT` của
            // bảng đối cực ([ĐO bài AdaptiveContrastContractTest] glyph tối trên kính tối 1,41).
            val glyph = p.onAccent.takeIf { Luma.of(ColorMath.parse(it)) > LIGHT_INK_LUMA } ?: InkModel.other(p).onAccent
            val white = ColorMath.withAlpha(ColorMath.parse(glyph), 255)
            val on = onFace(mixed, scrim, white)
            return KachiButtonInks(ColorMath.withAlpha(ColorMath.parse(p.wallScrim), restAlpha), on, glyph, glyph,
                ColorMath.withAlpha(ColorMath.parse(p.accent), RIPPLE_ALPHA))
        }

        /**
         * Công thức nút dưới HỌ [f] của mặt, kiểu đĩa [alt] (CHUNG cho mọi nút của mặt — [AdaptiveInk.Probe.group]): bảng Tối + họ
         * mực SÁNG có hai kiểu đĩa đã thiết kế, cùng glyph sáng — 0 = tông mềm (đúng byte 3.1.4, cho mặt tối đồng màu), 1 = kính tối
         * (đĩa đọc được trên ảnh — [ĐO máy ảo 15/10] tông mềm trên ảnh kéo cả lớp phủ 3D sang mực tối); mọi trường hợp khác (bảng
         * Sáng — mặc định 3.1.x; hoặc mặt đổi sang họ mực tối vì nền sáng) ⇒ kính tối + glyph sáng.
         */
        fun of(p: KachiPalette, f: InkFamily, alt: Int = 0): KachiButtonInks = if (!lightSurface(p) && f.lightInk && alt == 0) soft(p) else glass(p)

        /** Điểm của công thức [k] trên nền ĐO [under]: min(glyph nghỉ / [GLYPH_FLOOR], glyph BẬT / [GLYPH_ON_FLOOR]). */
        fun score(k: KachiButtonInks, under: ContrastBackdrop): Double = minOf(
            under.with(Layer.Flat(k.rest)).worst(ColorMath.parse(k.glyph)) / GLYPH_FLOOR,
            under.with(Layer.Flat(k.on)).worst(ColorMath.parse(k.glyphOn)) / GLYPH_ON_FLOOR,
        )

        /** Mực đang áp trên nút [button] ([adapt]); chưa chọn họ ⇒ mặc định bảng ([current]). */
        fun at(button: View): KachiButtonInks = applied[button] ?: current()

        /**
         * Nút [button] (nền = [background]) theo HỌ của mặt chứa nó: mặt lớp ([AdaptiveInk.Scope.LAYER], đo cả nền DƯỚI nút) +
         * bộ vẽ tự tô góp điểm công thức và nhận họ đã chọn. Lớp của mặt = đĩa nghỉ đang áp ⇒ nét khác trong nút (icon lấy gió)
         * đo trên đúng đĩa. [glyph] nhận bảng tint mới; [after] = chỗ gọi vẽ lại phần nó tự tô (icon lấy gió đọc [at]).
         */
        fun adapt(button: View, glyph: View?, after: () -> Unit = {}) {
            AdaptiveInk.surface(button, listen = true, scope = AdaptiveInk.Scope.LAYER, variant = ADAPT, under = true) {
                listOf(listOf(listOf(Layer.Flat(at(button).rest))))
            }
            AdaptiveInk.probe(button, object : AdaptiveInk.Probe {
                override val alts get() = 2
                override val group get() = KachiButtonInks
                override fun score(f: InkFamily, alt: Int): Double =
                    AdaptiveInk.under(button)?.let { score(of(KachiTheme.palette, f, alt), it) } ?: Double.MAX_VALUE
                override fun apply(f: InkFamily, alt: Int) {
                    val k = of(KachiTheme.palette, f, alt)
                    if (applied[button] == k) return
                    applied[button] = k
                    button.background = background(k)
                    when (glyph) { is ImageView -> glyph.imageTintList = glyphTint(k); is TextView -> glyph.setTextColor(glyphTint(k)) }
                    after()
                }
            })
        }

        /** Mực đã áp theo nút (khoá yếu — nút gỡ thì rơi). CHỈ luồng chính. */
        private val applied = WeakHashMap<View, KachiButtonInks>()
        private const val ADAPT = "button-inks"
        /**
         * Mặt đĩa BẬT (đục, cố định theo bảng — không theo nền): nhấn [mixed] phủ kính tối [scrim] ở alpha NHỎ NHẤT (bậc 5 %) để
         * glyph [white] đạt [GLYPH_FLOOR]. Đây là MÀU của mặt nút (cả đĩa), tính một lần theo bảng; bảng Sáng đạt sẵn ⇒ giữ byte.
         */
        private fun onFace(mixed: Int, scrim: Int, white: Int): Int {
            if (ColorMath.ratio(white, mixed) >= GLYPH_FLOOR) return mixed
            for (k in 1..20) {
                val face = ColorMath.over(ColorMath.withAlpha(scrim, ContrastInk.alphaByte(k * 0.05)), mixed)
                if (ColorMath.ratio(white, face) >= GLYPH_FLOOR) return face
            }
            return ColorMath.withAlpha(scrim, 255)
        }

        /** Mực "sáng" (độ chói > ngưỡng này) — cùng ngưỡng [InkModel.light] dùng để chia cực bảng màu. */
        private const val LIGHT_INK_LUMA = 0.18
        /** Sàn glyph — sàn CHỮ (4,5), cao hơn sàn nét 3:1: cùng bảo đảm các bài nút đã khoá. */
        const val GLYPH_FLOOR = 4.5
        val GLYPH_ON_FLOOR = InkRole.ACCENT_ICON.floor

        /**
         * Nền nút tròn: đĩa nghỉ / đĩa BẬT theo `state_activated` + sóng nhấn. Mặt nạ sóng = đĩa đặc (chỉ alpha có nghĩa).
         * Dựng MỘT lần lúc tạo nút (theo bảng đang chọn — đổi chủ đề dựng lại màn, như mọi nền khác).
         */
        fun background(k: KachiButtonInks = current()): Drawable {
            fun oval(color: Int) = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) }
            val face = StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_activated), oval(k.on))
                addState(intArrayOf(), oval(k.rest))
            }
            return RippleDrawable(ColorStateList.valueOf(k.ripple), face, oval(ColorMath.withAlpha(k.rest, 0xff)))
        }

        /** Tint glyph: nhấn ⇒ [glyphOn], nghỉ ⇒ [glyph] (dùng với `isDuplicateParentStateEnabled`). */
        fun glyphTint(k: KachiButtonInks = current()): ColorStateList = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_pressed), intArrayOf(android.R.attr.state_activated), intArrayOf()),
            intArrayOf(KachiTheme.c(k.glyphOn), KachiTheme.c(k.glyphOn), KachiTheme.c(k.glyph)),
        )
    }
}
