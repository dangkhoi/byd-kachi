package com.byd.clusternav.launcher.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager

/**
 * ═══ Tiêu điểm âm thanh cho một lượt nghe — tách khỏi [VoiceCapture] theo VAI (1.70) ═════════════════════════
 *
 * Vì sao tách: [VoiceCapture] chạm trần 500 dòng (CLAUDE.md §4.1) sau khi 1.70 dời âm báo sang [VoiceChime] và
 * thêm đệm ≥ 1 s + no-decode-on-silence. Ba hàm tiêu điểm dưới đây là một **vai độc lập**: xin/nhả quyền hạ
 * tiếng nhạc trong lúc micro mở, KHÔNG đụng trạng thái phiên, KHÔNG giữ gì sống lâu hơn một lượt nghe.
 *
 * ## Vì sao chỉ `TRANSIENT_MAY_DUCK`, không dừng nhạc
 * Dừng hẳn nhạc cho một câu 3 giây là cắt ngang thứ người ta đang nghe rồi trả lại ở chỗ khác. Hạ tiếng thì đủ
 * để micro nghe rõ mà bài hát không đứt. Và **không nghe đổi tiêu điểm**: phiên dài tối đa 8 s và tự kết thúc;
 * đăng ký một listener chỉ để bỏ qua mọi sự kiện của nó là thêm một đường sống lâu hơn phiên (§5 CLAUDE.md).
 */
internal object VoiceAudioFocus {

    private fun audio(ctx: Context): AudioManager? = ctx.getSystemService(AudioManager::class.java)

    /** Xin quyền hạ tiếng nhạc trong lúc nghe. Trả `null` nếu không xin được (khi ấy [abandon] là no-op). */
    fun request(ctx: Context): AudioFocusRequest? {
        val am = audio(ctx) ?: return null
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setWillPauseWhenDucked(false)
            .build()
        return if (runCatching { am.requestAudioFocus(req) }.getOrNull() == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) req
        else null
    }

    /** Trả tiêu điểm — chạy trên luồng nền của [VoiceCapture.closeRecord], không ai chờ. */
    fun abandon(ctx: Context, req: AudioFocusRequest?) {
        val am = audio(ctx) ?: return
        req?.let { runCatching { am.abandonAudioFocusRequest(it) } }
    }
}
