package com.byd.clusternav.launcher.voice

/**
 * ═══ 2.93 VOICE-OPEN-TURN-DYNVOCAB — TỪ VỰNG ĐỘNG của một phiên nghe, gom làm MỘT giá trị ════════════════════════════
 *
 * Bốn nguồn động mà [VoiceIntentParser.parse] nhận (tên hồ sơ · khoá bảng gọi app · nhãn sổ địa chỉ · tên app đã dạy —
 * spec `kachi-290-voice-app-names.html` §4.6 gợi ý đúng giá trị này). Sinh ra để chở chúng xuống TẦNG NGHE: phép ghép vế
 * sau quãng ngừng ([VoiceOpenTurn.refine]) tới 2.92 phân tích bằng từ vựng TĨNH ⇒ *"mở &lt;tên đã dạy / nhãn app máy&gt;"*
 * ⟨ngừng⟩ *"vào ô số hai"* không ghép được (cả hai vế ra `Unknown`) — OQ10 của spec ấy.
 *
 * [STATIC] = đúng hành vi 2.92 (mọi chỗ gọi cũ không truyền gì vẫn y nguyên). Thuần Kotlin ⇒ kiểm off-car.
 */
data class VoiceDynVocab(
    val profiles: List<String> = emptyList(),
    val apps: List<String> = emptyList(),
    val places: List<String> = emptyList(),
    val aliases: List<VoiceAppAlias> = emptyList(),
) {
    /** Phân tích MỘT vế với đúng bốn nguồn này — cùng cửa [VoiceIntentParser.parseOne] mà [VoiceOpenTurn] vẫn dùng. */
    fun parseOne(text: String): VoiceIntent = VoiceIntentParser.parseOne(text, profiles, apps, places, aliases)

    companion object {
        /** Không nguồn động nào — hành vi trước 2.93 của tầng nghe. */
        val STATIC = VoiceDynVocab()
    }
}
