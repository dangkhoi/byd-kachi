package com.byd.clusternav.launcher.voice

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** 1.70 [ĐO xe 2026-09-17]: lượt CHÍNH trên đường VAD không có tiếng ⇒ không giải mã; đường RMS giữ hành vi cũ. */
class VoiceSilenceGateTest {
    @Test
    fun `vad khong thay tieng thi bo giai ma ke ca luot chinh`() {
        assertTrue(VoiceSilenceGate.skipDecode(decodeOnlyIfSpeech = false, route = "vad", sawSpeech = false))
        assertTrue(VoiceSilenceGate.skipDecode(decodeOnlyIfSpeech = true, route = "vad", sawSpeech = false))
        assertTrue(VoiceSilenceGate.skipDecode(decodeOnlyIfSpeech = true, route = "rms", sawSpeech = false))
    }

    @Test
    fun `co tieng thi luon giai ma, duong rms o luot chinh van giai ma`() {
        assertFalse(VoiceSilenceGate.skipDecode(decodeOnlyIfSpeech = false, route = "vad", sawSpeech = true))
        assertFalse(VoiceSilenceGate.skipDecode(decodeOnlyIfSpeech = true, route = "vad", sawSpeech = true))
        assertFalse(VoiceSilenceGate.skipDecode(decodeOnlyIfSpeech = false, route = "rms", sawSpeech = false))
        assertFalse(VoiceSilenceGate.skipDecode(decodeOnlyIfSpeech = false, route = "khong co bo ngat cau", sawSpeech = true))
    }
}
