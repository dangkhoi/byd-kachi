package com.byd.clusternav.launcher.voice

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * WakeAsrMatcher đo trên GOLDEN on-car thật (2026-09-23, owner nói "Hey Kachi" + câu thường vào xe;
 * text = ASR zipformer-vi ra). Positive phải khớp CAO, negative (câu thường) = 0 false-accept.
 */
class WakeAsrGoldenTest {

    // Text ASR ra khi owner NÓI "Hey Kachi"/"Kachi ơi"/"OK Kachi" (từ log on-car).
    private val positive = listOf(
        "cá chì hay cá", "cá chì ok cá", "các chị các chị ơi các chị ơi", "chi hay các chị các chị ơi",
        "chị ơi các chị ơi các chị", "hay cá hay cá", "hay ka chê hay ca", "hay kach hay cá",
        "hay kach hay kach hay", "hay kach hay kach", "kach hay kach hai", "kacha cá ok", "ke hay kach hay ka",
    )
    // Text ASR câu THƯỜNG (giờ giấc / lệnh khác) — KHÔNG được khớp.
    private val negative = listOf(
        "ba giờ năm chín đi bốn giờ", "bây giờ", "bốn giờ cái ngày em buồn", "bốn giờ năm triệu mới đưa về nhà",
        "cái ngày em buồn nếu như mà", "chín đi bốn giờ năm rưỡi", "đó thì bao tiêu", "đường về nhà mình bắt a",
        "em buồn nếu như mà tuyệt đối", "giờ đúng không tám giờ đêm hai mươi bốn", "hai mươi ba giờ năm chín đi",
        "hai mươi đến hai mươi", "mình bán ấy", "mở nhạc bật điều hòa", "mười đến hai mươi ba giờ năm",
        "nếu như mà tuyệt đối thì ông", "nó không trưởng đường về nhà mình bắt", "tám giờ đêm tới hai mươi bốn giờ",
        "tháng a ấy", "tới hai mươi bốn giờ có ngày", "tối thì bao tiêu", "từ hồi năm hai mươi sáu",
    )

    @Test fun `positive khop cao`() {
        val hit = positive.count { WakeAsrMatcher.isWake(it) }
        assertTrue(hit >= positive.size * 75 / 100, "hit $hit/${positive.size} — phải ≥ 75% (golden Hey Kachi)")
    }

    @Test fun `negative KHONG false-accept`() {
        val bad = negative.filter { WakeAsrMatcher.isWake(it) }
        assertEquals(emptyList<String>(), bad, "câu thường bị khớp nhầm (false-accept → loop): $bad")
    }
}
