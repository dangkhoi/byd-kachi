package com.byd.clusternav.launcher.voice

import org.junit.jupiter.api.Assertions.assertEquals

/**
 * Bộ trợ giúp dùng CHUNG của [VoiceIntentParserTest] và [VoiceIntentParserEverydayTest] — tách THUẦN khi tệp gốc vượt trần
 * 500 dòng (L6-debt 2026-09-27): hai danh sách mồi (hồ sơ · app), `one`/`all` gọi parser, bảng ca `expect`, `unknown`. Một
 * nguồn để hai lớp không thể mồi hai bộ dữ liệu khác nhau (DRY, CLAUDE.md §4.1).
 */
internal object VoiceIntentParserHarness {
    val profiles = listOf("Mặc định", "Vợ")
    val apps = listOf("VTV Go", "YouTube", "Zing MP3", "VietMap Live")

    fun one(s: String): VoiceIntent = VoiceIntentParser.parseOne(s, profiles, apps)
    fun all(s: String): List<VoiceIntent> = VoiceIntentParser.parse(s, profiles, apps)

    /** Bảng ca: câu → ý định mong đợi. Thông báo lỗi kèm nguyên văn câu để đọc là biết ca nào đỏ. */
    fun expect(vararg cases: Pair<String, VoiceIntent>) =
        cases.forEach { (s, want) -> assertEquals(want, one(s), "câu: \"$s\"") }

    fun unknown(s: String, reason: VoiceUnknownReason) =
        assertEquals(reason, (one(s) as? VoiceIntent.Unknown)?.reason, "câu: \"$s\" phải là Unknown($reason)")

}
