package com.byd.clusternav.launcher.voice

import android.content.Context

/**
 * ═══ MỘT CHỖ DỰNG bộ nhận dạng của PHIÊN LỆNH — dùng chung cho lượt nghe chính và lượt DẠY ═════════════════════════
 *
 * 2.91 VOICE-APP-NAMES · A4 (spec §4.4, R2 AC: *"lượt thu đi qua `VoiceRecognizer.open` với CÙNG tham số hotword của
 * phiên lệnh"*). Dời nguyên văn khỏi [runListen] (pure move): hồ sơ · bảng gọi app (khoá + gói đang cài) · sổ địa chỉ —
 * ⇒ cùng tệp hotword (tĩnh + hồ sơ + nơi + tên đã dạy), cùng recognizer chung của tiến trình. Mẫu thu bằng cấu hình
 * KHÁC sẽ in ra chữ khác lúc dùng thật (spec §2.1 điều kiện 2) — nên lượt dạy không được dựng recognizer theo cách khác.
 */
internal fun commandRecognizer(
    ctx: Context,
    profiles: List<String>,
    labels: Map<String, String>,
    places: List<String>,
): VoiceRecognizer? = VoiceRecognizer.open(ctx, profiles, labels.keys.toList(), labels.values.toSet(), places)

/**
 * Lượt nghe chính của [VoiceSession] — [commandRecognizer] với nguồn động của phiên. CHẶN ⇒ luồng nền.
 *
 * VOICE-SESSION-READY-CACHE — in mốc giờ: [ĐO xe 15/10] *"sẵn sàng nghe sau 2204 ms"* mà không dòng nào nói
 * phần nào ăn 2,2 s đó. Một dòng `KachiVoiceTiming` mỗi phiên, không đổi hành vi.
 */
internal fun VoiceSession.openCommandRecognizer(): VoiceRecognizer? {
    val t0 = System.currentTimeMillis()
    return commandRecognizer(ctx, profiles(), appsByLabel(), places()).also {
        // Phần bảng app (hỏi PM · nhãn · dựng) có dòng riêng `bảng app:` của [VoiceWiring.appIndex].
        android.util.Log.i(VoiceEngine.TIMING_TAG, "dựng nghe: ${System.currentTimeMillis() - t0} ms (hồ sơ + bảng app + nơi + recognizer)")
    }
}
