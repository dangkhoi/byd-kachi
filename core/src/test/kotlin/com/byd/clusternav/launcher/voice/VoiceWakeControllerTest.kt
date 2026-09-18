package com.byd.clusternav.launcher.voice

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Bộ não bộ nghe "Hey Kachi" — kiểm 3 tầng cắt CPU + cooldown + cầu chì false-accept (thuần, off-car). */
class VoiceWakeControllerTest {

    private fun controller() = VoiceWakeController(
        gate = VoiceWakeGate(),
        loadGuard = VoiceLoadGuard(suspendAbove = 6.0, resumeBelow = 4.0, resumeStableReads = 1),
        cooldownMs = 3_000L, maxWakesPerWindow = 6, wakeWindowMs = 60_000L,
    )

    @Test fun `he nong thi SUSPENDED (nha mic, thoi KWS)`() {
        val c = controller()
        assertEquals(VoiceWakeController.Frame.SUSPENDED, c.onFrame(rms = 800.0, load1 = 9.0, nowMs = 0),
            "load cao ⇒ dừng dù có giọng to")
    }

    @Test fun `im thi IDLE, co giong thi RUN_KWS`() {
        val c = controller()
        assertEquals(VoiceWakeController.Frame.IDLE, c.onFrame(rms = 80.0, load1 = 1.0, nowMs = 0), "im ⇒ không chạy KWS")
        assertEquals(VoiceWakeController.Frame.RUN_KWS, c.onFrame(rms = 800.0, load1 = 1.0, nowMs = 100), "có giọng ⇒ chạy KWS")
    }

    @Test fun `no wake xong thi cooldown IDLE roi moi chay lai`() {
        val c = controller()
        assertEquals(VoiceWakeController.Frame.RUN_KWS, c.onFrame(800.0, 1.0, 100))
        assertEquals(VoiceWakeController.Wake.FIRE, c.onKwsResult(matched = true, nowMs = 100))
        // trong cooldown (100..3100): dù có giọng to vẫn IDLE
        assertEquals(VoiceWakeController.Frame.IDLE, c.onFrame(800.0, 1.0, 1_000), "đang cooldown ⇒ không chạy KWS")
        // sau cooldown: chạy lại
        assertEquals(VoiceWakeController.Frame.RUN_KWS, c.onFrame(800.0, 1.0, 3_200))
    }

    @Test fun `KWS khong khop thi NONE, khong cooldown`() {
        val c = controller()
        assertEquals(VoiceWakeController.Wake.NONE, c.onKwsResult(matched = false, nowMs = 100))
        assertEquals(VoiceWakeController.Frame.RUN_KWS, c.onFrame(800.0, 1.0, 200), "không khớp ⇒ không nghỉ")
    }

    @Test fun `cau chi false-accept tu tat sau qua nhieu wake`() {
        val c = controller()
        // 6 wake đầu trong cửa sổ ⇒ FIRE; wake thứ 7 ⇒ FUSED
        for (i in 0 until 6) assertEquals(VoiceWakeController.Wake.FIRE, c.onKwsResult(true, i * 100L), "wake #${i + 1}")
        assertEquals(VoiceWakeController.Wake.FUSED, c.onKwsResult(true, 700L), "wake #7 vượt trần ⇒ tự tắt")
        assertTrue(c.isFused())
        assertEquals(VoiceWakeController.Frame.SUSPENDED, c.onFrame(800.0, 1.0, 800L), "đã cầu chì ⇒ ngừng hẳn")
        c.reset(); assertFalse(c.isFused(), "bật lại công tắc ⇒ reset")
    }
}
