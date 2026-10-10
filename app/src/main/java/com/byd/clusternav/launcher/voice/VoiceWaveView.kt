package com.byd.clusternav.launcher.voice

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import com.byd.clusternav.launcher.KachiTheme
import kotlin.math.min

/**
 * ═══ VOICE WAVE — vòng tròn phập phồng theo mức âm (owner chốt 2026-09-23: kiểu Siri) ════════════════════════
 *
 * Spec `kachi-voice-ux.html` R2: khi ĐANG NGHE, hiện tín hiệu THẤY được là máy đang nghe. Vòng tròn co giãn theo
 * RMS thời gian thực + hai vòng mờ bao quanh (ripple) cho cảm giác sống. Mờ/đứng yên khi rời trạng thái nghe.
 *
 * Thuần Canvas, không bitmap, tự làm mượt (level đi tới `target` bằng nội suy) ⇒ mức âm giật vẫn hiện mượt.
 * [setLevel] gọi từ luồng nghe (RMS 0..32767) — chỉ ghi `target`, `invalidate` tự chạy vòng vẽ trên main.
 */
class VoiceWaveView(ctx: Context) : View(ctx) {

    /** Mức mục tiêu 0..1 (đã chuẩn hoá từ RMS). Cập nhật từ [setLevel]. */
    @Volatile private var target = 0f
    @Volatile private var level = 0f   // mức đang vẽ (nội suy về target); đọc ở [setLevel] (luồng nghe) ⇒ volatile
    // 3.0 soát Pass 1: [setLevel] (luồng nghe) nay đọc cờ này để quyết có hẹn khung hay không ⇒ volatile như `target`/`level`.
    @Volatile private var listening = true    // false ⇒ mờ dần, không phập phồng

    private val core = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = KachiTheme.c(KachiTheme.ACCENT) }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = KachiTheme.c(KachiTheme.ACCENT)
    }

    /** Cấp mức âm (RMS 0..32767) từ luồng nghe. Chuẩn hoá phi tuyến để mức nói thường (200–2000) thấy rõ. */
    fun setLevel(rms: Int) {
        // sqrt-scale: rms nhỏ vẫn nhích vòng; trần ~4000 (nói to) = đầy.
        val t = min(1f, kotlin.math.sqrt((rms.coerceAtLeast(0)).toFloat() / 4000f))
        target = t
        // 3.0 R5 — mức mới lệch không thấy được so với vòng đang vẽ ⇒ không đánh thức RenderThread ([VoiceWaveMotion]).
        if (VoiceWaveMotion.moving(level, if (listening) t else 0f)) postInvalidateOnAnimation()
    }

    /** LISTENING = phập phồng; false = mờ dần đứng yên (DECODING/nói). */
    fun setListening(on: Boolean) { listening = on; postInvalidateOnAnimation() }

    override fun onDraw(canvas: Canvas) {
        // Nội suy mượt về target (hoặc về 0 khi không nghe).
        val goal = if (listening) target else 0f
        level = VoiceWaveMotion.step(level, goal)
        val cx = width / 2f; val cy = height / 2f
        val half = min(width, height) / 2f
        val base = half * 0.28f                              // lõi khi im — nhỏ để ripple KHÔNG tràn bounds (đè chữ)
        val r = base * (1f + level * 0.8f)                   // phồng theo mức
        // Hai vòng ripple mờ (chỉ khi đang nghe) — bán kính giữ ≤ half để không vẽ ra ngoài view.
        if (listening) {
            ring.strokeWidth = base * 0.06f
            ring.alpha = (60 * (1f - level * 0.3f)).toInt().coerceIn(20, 90)
            canvas.drawCircle(cx, cy, (r + base * 0.9f).coerceAtMost(half * 0.98f), ring)
            ring.alpha = (30 * (1f - level * 0.3f)).toInt().coerceIn(10, 60)
            canvas.drawCircle(cx, cy, (r + base * 1.6f).coerceAtMost(half * 0.99f), ring)
        }
        core.alpha = if (listening) 255 else (level * 255).toInt().coerceIn(0, 160)
        canvas.drawCircle(cx, cy, r, core)
        // 3.0 R5 VOICE-OVERLAY-FLICKER-SLOW — chỉ hẹn khung kế khi còn nội suy dở. Bản ≤ 2.99 hẹn VÔ ĐIỀU KIỆN khi đang nghe
        // (`listening || …`) ⇒ cửa sổ phủ toàn màn vẽ lại 60 khung/giây cả khi mức âm đứng yên — [ĐO máy ảo] RenderThread
        // 3 780 ms CPU / phiên ~11 s. Mức âm mới đã tự hẹn khung ở [setLevel]. Luật + số đo: KDoc [VoiceWaveMotion].
        if (VoiceWaveMotion.moving(level, goal)) postInvalidateOnAnimation()
    }
}
