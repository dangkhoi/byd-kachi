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

/** Lượt nghe chính của [VoiceSession] — [commandRecognizer] với nguồn động của phiên. CHẶN ⇒ luồng nền. */
internal fun VoiceSession.openCommandRecognizer(): VoiceRecognizer? =
    commandRecognizer(ctx, profiles(), appsByLabel(), places())
