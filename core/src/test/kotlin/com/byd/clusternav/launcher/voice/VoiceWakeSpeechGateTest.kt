package com.byd.clusternav.launcher.voice

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * VOICE-WAKE-CPU-BUDGET — cổng tiếng người trước giải mã ASR wake. Khoá: nhạc không lời (VAD thấp) ⇒ cổng ĐÓNG; câu
 * gọi vừa dứt ⇒ cổng còn MỞ đủ lâu cho lượt giải mã chứa trọn câu; VAD hỏng ⇒ MỞ (không điếc).
 * Red-proof: cho [VoiceWakeSpeechGate.open] luôn `true` ⇒ bài 1 đỏ; bỏ nhánh `broken` ⇒ bài 3 đỏ.
 */
class VoiceWakeSpeechGateTest {

    private fun frame() = FloatArray(1_600) { 0.1f }   // khung micro 100 ms

    @Test fun `nhac khong loi - VAD thap - cong dong sau khi het do tre`() {
        val g = VoiceWakeSpeechGate(threshold = 0.45f, holdSamples = 11_200)
        repeat(20) { g.accept(frame(), 1_600) { 0.1f } }   // 2 s nhạc không lời
        assertFalse(g.open(), "không có cửa sổ tiếng người ⇒ không giải mã")
        assertEquals(62L, g.windows, "2 s = 32 000 mẫu = 62 cửa sổ 512 (phần lẻ giữ lại)")
        assertEquals(0L, g.speechWindows)
    }

    @Test fun `cau goi vua dut - con mo trong hold roi dong`() {
        val g = VoiceWakeSpeechGate(threshold = 0.45f, holdSamples = 11_200)
        repeat(8) { g.accept(frame(), 1_600) { 0.9f } }     // 0,8 s tiếng người
        assertTrue(g.open())
        repeat(6) { g.accept(frame(), 1_600) { 0.05f } }    // 600 ms im ⇒ vẫn trong hold 700 ms
        assertTrue(g.open(), "lượt giải mã ngay sau khi câu dứt phải còn chạy")
        repeat(2) { g.accept(frame(), 1_600) { 0.05f } }    // 800 ms ⇒ quá hold
        assertFalse(g.open())
    }

    @Test fun `VAD hong thi cong MO vinh vien, chua co du lieu cung MO`() {
        val g = VoiceWakeSpeechGate()
        assertTrue(g.open(), "chưa chấm cửa sổ nào ⇒ không đoán thay dữ liệu")
        repeat(10) { g.accept(frame(), 1_600) { 0.0f } }
        assertFalse(g.open())
        g.accept(frame(), 1_600) { null }                  // VAD ném
        assertTrue(g.broken)
        assertTrue(g.open(), "VAD hỏng ⇒ rơi về cổng RMS cũ, không điếc")
    }

    @Test fun `phan le duoc giu - cat dung cua so 512`() {
        val sizes = ArrayList<Int>()
        val g = VoiceWakeSpeechGate()
        g.accept(FloatArray(1_600), 1_600) { sizes += it.size; 0f }
        g.accept(FloatArray(1_600), 1_600) { sizes += it.size; 0f }
        assertEquals(6, sizes.size, "3 200 mẫu = 6 cửa sổ 512 + 128 lẻ")
        assertTrue(sizes.all { it == 512 })
    }
}
