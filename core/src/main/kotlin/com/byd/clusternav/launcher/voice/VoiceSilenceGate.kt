package com.byd.clusternav.launcher.voice

/**
 * Cổng **bỏ giải mã** một lượt nghe không có tiếng — luật thuần (:core), một chỗ, để `VoiceCapture` hỏi.
 *
 * ## Hai vế, hai nguồn
 *  • `decodeOnlyIfSpeech` — chỗ gọi (lượt NỐI) tự tuyên bố *"im lặng là bình thường"* ([P0-1a] 1.69).
 *  • **Đường VAD ở lượt CHÍNH** (1.70) — [ĐO xe 2026-09-17] 7/10 lượt chính `vad doan=0` vẫn nạp 8,2 s im lặng
 *    vào mô hình: 4,3 s CPU để ra chữ BỊA (*"chúng ta xây"* · *"vâng giấc mơ"* · *"ừm"*), rồi phiên hỏi lại về
 *    một câu không ai nói. Silero VAD đủ tin để nói "không có tiếng"; bộ RMS thì không (ngưỡng theo nền, xem
 *    `VoiceEndpointer`) nên đường lùi giữ hành vi cũ — giải mã rồi để chỗ gọi nói *"Không nghe rõ"*.
 */
object VoiceSilenceGate {
    /** Tên đường ngắt câu bằng VAD — khớp `VoiceTurnEndpoint.route`. */
    const val ROUTE_VAD = "vad"

    /** `true` ⇒ không đưa cửa sổ vào mô hình. */
    fun skipDecode(decodeOnlyIfSpeech: Boolean, route: String, sawSpeech: Boolean): Boolean =
        !sawSpeech && (decodeOnlyIfSpeech || route == ROUTE_VAD)
}
