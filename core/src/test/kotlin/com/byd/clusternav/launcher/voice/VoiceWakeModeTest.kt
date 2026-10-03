package com.byd.clusternav.launcher.voice

import com.byd.clusternav.voicekey.VoiceKeyBinding
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * FIX286 · VK1 — một luật "mô hình nằm ở đâu" (`modelInWake = wakeEnabled ∨ keyHold`). Bảng 4 ca + luật keyHold.
 */
class VoiceWakeModeTest {

    private val kachi = "__KACHI_VOICE__"

    @Test
    fun `bang 4 ca wake x phim`() {
        assertEquals(VoiceWakeMode.OFF, VoiceWakeMode.of(wakeEnabled = false, keyHold = false))
        assertEquals(VoiceWakeMode.HOLD, VoiceWakeMode.of(wakeEnabled = false, keyHold = true))
        assertEquals(VoiceWakeMode.WAKE, VoiceWakeMode.of(wakeEnabled = true, keyHold = false))
        assertEquals(VoiceWakeMode.WAKE, VoiceWakeMode.of(wakeEnabled = true, keyHold = true), "wake bật thắng: bộ nghe câu gọi cần mô hình đó")
        assertFalse(VoiceWakeMode.OFF.modelInWake)
        assertTrue(VoiceWakeMode.HOLD.modelInWake)
        assertTrue(VoiceWakeMode.WAKE.modelInWake)
    }

    @Test
    fun `keyHold - chi khi cong tac nhan nut BAT va co phim tro Kachi nghe`() {
        val kachiKey = listOf(VoiceKeyBinding(328, kachi))
        val otherKey = listOf(VoiceKeyBinding(328, "ai.zalo.kiki.car"), VoiceKeyBinding(231, "__VOICEKEY231__"))
        assertTrue(VoiceWakeMode.keyHold(voiceKeyEnabled = true, bindings = kachiKey, kachiTarget = kachi))
        assertTrue(VoiceWakeMode.keyHold(true, otherKey + VoiceKeyBinding(88, kachi), kachi), "một trong nhiều dòng gán là đủ")
        assertFalse(VoiceWakeMode.keyHold(voiceKeyEnabled = false, bindings = kachiKey, kachiTarget = kachi), "công tắc tắt ⇒ phím không bắn ⇒ không giữ RAM cho nó")
        assertFalse(VoiceWakeMode.keyHold(true, otherKey, kachi), "phím gán app khác ⇒ không HOLD")
        assertFalse(VoiceWakeMode.keyHold(true, emptyList(), kachi))
    }
}
