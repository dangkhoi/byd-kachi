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
 * ## 3.0 R4 `VOICE-DUCK-MUSIC` — lượt nghe xin `GAIN_TRANSIENT_EXCLUSIVE` (TẠM DỪNG nhạc), không còn `MAY_DUCK` (hạ tiếng)
 * Tới 2.99 lượt nghe chỉ xin `TRANSIENT_MAY_DUCK` (*"dừng hẳn nhạc cho một câu 3 giây là cắt ngang"*). [ĐO máy ảo kachi_play 10/10,
 * `dumpsys audio`] cơ chế ấy CÓ chạy — YouTube nhận `LOSS_TRANSIENT_CAN_DUCK`, khung `ducking player … / unducking` khớp từng lượt mic —
 * nhưng nền tảng chỉ hạ tiếng (không dừng), và [ĐO xe 10/10, Seal fw 2606, 2.99] YouTube đang phát ⇒ micro vẫn thu lời bài hát
 * (*"may mắn thuộc về"* · *"yêu thích và"* · *"ừ"* · *"ừm"*) ⇒ nhiều câu rác; owner: *"khi nghe có thể phải giảm âm nhạc lại"*.
 * `GAIN_TRANSIENT_EXCLUSIVE` là loại tiêu điểm tài liệu Android dành cho nhận dạng tiếng nói: app nhạc nhận `LOSS_TRANSIENT` ⇒ TẠM DỪNG,
 * và tự phát lại khi lượt nghe nhả tiêu điểm (số đo trước/sau ở `docs/diagnostics/voice-300-r4-duck-2026-10-10.md`).
 *
 * Không đổi gì khác: cùng thuộc tính âm thanh (`USAGE_ASSISTANCE_SONIFICATION` + `CONTENT_TYPE_SPEECH` — tuyến âm của xe đã chạy với
 * nó), cùng vòng đời MỘT lượt nghe (xin sau khi mở micro, nhả ở `closeRecord` trên luồng nền) ⇒ không có đường sống lâu hơn phiên. Tiến
 * trình chết giữa lượt ⇒ AudioService tự gỡ tiêu điểm theo binder chết của chỗ xin [SUY AOSP `MediaFocusControl` death handler].
 * Câu trả lời đọc ra vẫn chỉ HẠ tiếng (`AndroidTtsSpeaker`/`SherpaTtsSpeaker` giữ `MAY_DUCK`): giữa hai lượt nghe nhạc phát lại nhỏ.
 * Lượt chờ từ đánh thức (`VoiceWakeListener`) KHÔNG đi qua đây ⇒ "Hey Kachi" vẫn nghe trong lúc nhạc chạy như cũ.
 */
internal object VoiceAudioFocus {

    private fun audio(ctx: Context): AudioManager? = ctx.getSystemService(AudioManager::class.java)

    /** Xin quyền TẠM DỪNG nhạc trong lúc nghe (R4). Trả `null` nếu không xin được (khi ấy [abandon] là no-op). */
    fun request(ctx: Context): AudioFocusRequest? {
        val am = audio(ctx) ?: return null
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
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
