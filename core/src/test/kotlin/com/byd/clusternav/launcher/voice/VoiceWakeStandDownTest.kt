package com.byd.clusternav.launcher.voice

import com.byd.clusternav.launcher.voice.VoiceWakeStandDown.Decision
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * BG-20 (2026-09-25 · wake) — quyết định THUẦN đứng xuống của `:wake` sau phiên headless khi "Hey Kachi" TẮT.
 * Dời từ `:app` sang `:core` 2026-09-26 cùng object (CLOSE-3); phần dây `VoiceWakeService` ở
 * `app/.../VoiceWakeStandDownWiringContractTest`.
 */
class VoiceWakeStandDownTest {

    // ── Quyết định thuần đứng xuống ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `wake BAT thi KEEP - vong doi thuong so huu service, du phien dang o pha nao`() {
        VoiceTurnPhase.values().forEach { ph ->
            assertEquals(Decision.KEEP, VoiceWakeStandDown.decide(wakeEnabled = true, sessionPhase = ph, waitedMs = 0))
            assertEquals(Decision.KEEP, VoiceWakeStandDown.decide(wakeEnabled = true, sessionPhase = ph, waitedMs = 999_999))
        }
    }

    @Test
    fun `wake TAT va phien da IDLE thi STAND_DOWN ngay`() {
        assertEquals(Decision.STAND_DOWN, VoiceWakeStandDown.decide(false, VoiceTurnPhase.IDLE, waitedMs = 0))
    }

    @Test
    fun `wake TAT va phien con chay thi WAIT - khong cat giua cau nguoi lai`() {
        listOf(VoiceTurnPhase.LISTENING, VoiceTurnPhase.DECODING, VoiceTurnPhase.EXECUTING).forEach { ph ->
            assertEquals(Decision.WAIT, VoiceWakeStandDown.decide(false, ph, waitedMs = 60_000), ph.name)
        }
    }

    @Test
    fun `phien ket qua tran thi van dung xuong - FGS treo mai khong phai cach che loi`() {
        val max = VoiceWakeStandDown.MAX_WAIT_MS
        assertEquals(Decision.WAIT, VoiceWakeStandDown.decide(false, VoiceTurnPhase.LISTENING, waitedMs = max - 1))
        assertEquals(Decision.STAND_DOWN, VoiceWakeStandDown.decide(false, VoiceTurnPhase.LISTENING, waitedMs = max))
    }
}
