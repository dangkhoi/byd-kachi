package com.byd.clusternav.launcher.voice

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ 3.0 R5 VOICE-OVERLAY-FLICKER-SLOW — khoá luật "chỉ vẽ khi có gì thay đổi" của vòng sóng ═════════════════════════
 *
 * [ĐO máy ảo 10/10] bản ≤ 2.99 vẽ lại tấm chữ phủ toàn màn 60 khung/giây suốt phiên ⇒ `RenderThread` của `:wake` 3 780 ms CPU
 * cho một phiên ~11 s (`docs/diagnostics/voice-300-r5-overlay-bisect-2026-10-10.md`). Bài này mô phỏng đúng nhịp thật: mức âm
 * tới mỗi 200 ms (khúc `VoiceCapture.CHUNK_SAMPLES`), 12 khung vẽ giữa hai khúc (60 Hz), và đếm số khung được hẹn.
 */
class VoiceWaveMotionTest {

    /** Số khung vẽ được hẹn cho một chuỗi mức âm, theo đúng hai chỗ hẹn của `VoiceWaveView` (setLevel + onDraw). */
    private fun framesFor(levels: List<Float>): Int {
        var level = 0f
        var frames = 0
        for (t in levels) {
            var pending = VoiceWaveMotion.moving(level, t)          // setLevel
            var budget = FRAMES_PER_CHUNK
            while (pending && budget-- > 0) {                       // onDraw nối nhau tới khi tới đích hoặc khúc mới tới
                frames++
                level = VoiceWaveMotion.step(level, t)
                pending = VoiceWaveMotion.moving(level, t)
            }
        }
        return frames
    }

    @Test
    fun `im lang deu - khong ve khung nao (ban 2_99 ve 60 khung moi giay)`() {
        // 8 s nghe im lặng ở mức ồn nền đều (lệch < EPS giữa hai khúc).
        val quiet = List(40) { 0.05f + (it % 2) * 0.004f }
        val frames = framesFor(quiet)
        assertTrue(frames <= 12, "chỉ được vẽ lúc vòng nhích lên mức ồn nền một lần, được $frames khung")
        // Đối chứng bản cũ: 40 khúc × 12 khung = 480 khung cho đúng chuỗi ấy.
        assertEquals(480, 40 * FRAMES_PER_CHUNK)
    }

    @Test
    fun `dang noi - van nhuc theo muc am (do muot khong doi)`() {
        val speech = listOf(0.1f, 0.6f, 0.3f, 0.8f, 0.2f, 0.7f)
        assertTrue(framesFor(speech) >= speech.size * 6, "mức đổi mỗi khúc ⇒ phải vẽ đủ để nội suy")
    }

    @Test
    fun `toi dich thi dung, con lech thi ve tiep`() {
        assertFalse(VoiceWaveMotion.moving(0.5f, 0.505f))
        assertTrue(VoiceWaveMotion.moving(0.5f, 0.52f))
        // Hệ số nội suy giữ đúng bản ≤ 2.99 (0,25 quãng còn lại mỗi khung).
        assertEquals(0.25f, VoiceWaveMotion.step(0f, 1f), 1e-6f)
    }

    @Test
    fun `mo dan khi het nghe roi dung han`() {
        var level = 0.8f
        var n = 0
        while (VoiceWaveMotion.moving(level, 0f) && n < 100) { level = VoiceWaveMotion.step(level, 0f); n++ }
        assertTrue(n in 1..20, "mờ dần phải kết thúc trong ~${n} khung, không vẽ mãi")
    }

    private companion object { const val FRAMES_PER_CHUNK = 12 }
}
