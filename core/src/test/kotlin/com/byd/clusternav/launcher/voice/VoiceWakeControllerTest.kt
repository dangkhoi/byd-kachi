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

    /**
     * ═══ TẦNG 4 — trần THỜI LƯỢNG suy diễn ═══════════════════════════════════════════════════════════════
     * Ca hỏng mà ba tầng đầu KHÔNG bịt: **nhạc trong cabin** = RMS nằm trên ngưỡng cổng năng lượng **liên tục**,
     * mà nền của cổng ấy cố ý chỉ học lúc im ⇒ `voiced()` trả `true` mọi khung ⇒ KWS chạy 100 % thời gian. Trần
     * này cho một chặn trên chứng minh được, không phụ thuộc phổ âm của cabin.
     */
    @Test fun `on lien tuc tren nguong van khong cho KWS chay qua tran thoi luong`() {
        val c = VoiceWakeController(
            gate = VoiceWakeGate(),
            loadGuard = VoiceLoadGuard(suspendAbove = 6.0, resumeBelow = 4.0, resumeStableReads = 1),
            maxKwsFramesPerWindow = 5, kwsWindowMs = 1_000L,
        )
        // 5 khung đầu (trong cùng cửa sổ) được chạy KWS…
        for (i in 0 until 5) {
            assertEquals(VoiceWakeController.Frame.RUN_KWS, c.onFrame(800.0, 1.0, 100L + i * 10), "khung #${i + 1}")
        }
        // …khung thứ 6 trong cùng cửa sổ bị hạ về IDLE dù tiếng vẫn to (đây là chỗ CPU được cắt).
        assertEquals(VoiceWakeController.Frame.IDLE, c.onFrame(800.0, 1.0, 160L), "quá trần ⇒ IDLE, chỉ còn toán RMS")
        assertEquals(VoiceWakeController.Frame.IDLE, c.onFrame(800.0, 1.0, 900L))
        // Cửa sổ trượt qua ⇒ lại được chạy (không phải một lần cắt vĩnh viễn).
        assertEquals(VoiceWakeController.Frame.RUN_KWS, c.onFrame(800.0, 1.0, 1_200L), "cửa sổ trượt ⇒ có suất mới")
    }

    @Test fun `tran thoi luong khong chan mot cau goi binh thuong`() {
        val c = controller() // mặc định: 40 khung / 10 s
        // Câu gọi "Hey Kachi" ≈ 1 s ≈ 10 khung 100 ms — phải qua hết, không khung nào bị bỏ.
        for (i in 0 until 10) {
            assertEquals(VoiceWakeController.Frame.RUN_KWS, c.onFrame(800.0, 1.0, i * 100L), "khung #${i + 1} của câu gọi")
        }
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
