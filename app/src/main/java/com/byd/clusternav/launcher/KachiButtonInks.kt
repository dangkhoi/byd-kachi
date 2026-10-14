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
 * 3.1.5 R29 T29-6 — nhánh cố định trên chỉ còn là MẶC ĐỊNH LÚC CHƯA ĐO (nút vừa dựng, chưa gắn cửa sổ). Đo được nền dưới nút
 * ([adapt] — mặt dò của [AdaptiveInk]) ⇒ [of] (p, nền) CHỌN công thức theo nền đó: công thức của bảng trước, công thức kia sau
 * (thứ tự giữ bảng Tối nguyên byte khi nền tối); không công thức nào đạt ⇒ đĩa kính tối đậm thêm tới mức [ContrastInk] tìm ra
 * (= lớp đỡ của glyph — đĩa CHÍNH LÀ lớp đỡ). Sàn glyph = sàn CHỮ 4,5 (không 3) — giữ đúng bảo đảm `Widget301LightContrastTest`.
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
            val deepen = ContrastInk.resolve(InkRole.TEXT, ContrastBackdrop(ToneBox.span(mixed)), listOf(InkCandidate(white, scrim))).haloAlpha
            val on = if (deepen > 0) ColorMath.over(ColorMath.withAlpha(scrim, ContrastInk.alphaByte(deepen)), mixed) else mixed
            return KachiButtonInks(ColorMath.withAlpha(ColorMath.parse(p.wallScrim), restAlpha), on, glyph, glyph,
                ColorMath.withAlpha(ColorMath.parse(p.accent), RIPPLE_ALPHA))
        }

        /**
         * Glyph nghỉ đạt [GLYPH_FLOOR] trên đĩa nghỉ, glyph BẬT/NHẤN đạt [GLYPH_ON_FLOOR] trên đĩa BẬT — cả hai trên nền ĐO [under].
         * Sàn BẬT = vai icon nhấn ([InkRole.ACCENT_ICON], 3:1) — cùng sàn "icon nhấn ≥ 3" `WindowControlIconContractTest` đã khoá.
         */
        private fun reads(k: KachiButtonInks, under: ContrastBackdrop): Boolean =
            under.with(Layer.Flat(k.rest)).worst(ColorMath.parse(k.glyph)) >= GLYPH_FLOOR &&
                under.with(Layer.Flat(k.on)).worst(ColorMath.parse(k.glyphOn)) >= GLYPH_ON_FLOOR

        /**
         * R29 T29-6 — mực nút trên nền ĐO [under] (nền dưới nút, chưa gồm đĩa). Công thức của bảng [p] trước ([of] (p) — bảng Tối
         * trên nền tối ra đúng từng byte cũ), công thức kia sau; không cái nào đạt ⇒ đĩa kính tối với alpha nhỏ nhất glyph trắng đạt
         * sàn ([ContrastInk.resolve] — đĩa là lớp đỡ), không dưới [LIGHT_REST_ALPHA].
         */
        fun of(p: KachiPalette, under: ContrastBackdrop): KachiButtonInks {
            val first = of(p)
            val other = if (lightSurface(p)) soft(p) else glass(p)
            if (reads(first, under)) return first
            if (reads(other, under)) return other
            val g = glass(p)
            val white = ColorMath.withAlpha(ColorMath.parse(g.glyph), 255)
            val r = ContrastInk.resolve(InkRole.TEXT, under, listOf(InkCandidate(white, ColorMath.withAlpha(g.rest, 255))))
            return glass(p, maxOf(LIGHT_REST_ALPHA, ContrastInk.alphaByte(r.haloAlpha)))
        }

        /** Mực đang áp trên nút [button] ([adapt]); chưa đo ⇒ mặc định bảng ([current]). */
        fun at(button: View): KachiButtonInks = applied[button] ?: current()

        /**
         * R29 T29-6 — nút [button] (nền = [background]) tự chọn đĩa/glyph theo nền ĐO dưới nó: mặt dò ([AdaptiveInk.surface]
         * `onUnder`) — chỉ khi nền dưới nút đổi (vùng · ảnh · chủ đề · độ đục · ảnh nền xe 3D). Lớp của mặt = đĩa nghỉ đang áp ⇒ nét
         * khác nằm trong nút (icon lấy gió) giải trên đúng đĩa. [glyph] (ImageView/TextView) nhận bảng tint mới; [after] = chỗ gọi
         * vẽ lại phần nó tự tô (icon lấy gió đọc [at]).
         */
        fun adapt(button: View, glyph: View?, after: () -> Unit = {}) =
            AdaptiveInk.surface(button, listen = true, variant = ADAPT, onUnder = { under ->
                val k = of(KachiTheme.palette, under)
                if (applied[button] != k) {
                    applied[button] = k
                    button.background = background(k)
                    when (glyph) { is ImageView -> glyph.imageTintList = glyphTint(k); is TextView -> glyph.setTextColor(glyphTint(k)) }
                    after()
                }
            }) { listOf(listOf(listOf(Layer.Flat(at(button).rest)))) }

        /** Mực đã áp theo nút (khoá yếu — nút gỡ thì rơi). CHỈ luồng chính. */
        private val applied = WeakHashMap<View, KachiButtonInks>()
        private const val ADAPT = "button-inks"
        /** Mực "sáng" (độ chói > ngưỡng này) — cùng ngưỡng chọn lớp đỡ của [InkModel.haloFor]. */
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
