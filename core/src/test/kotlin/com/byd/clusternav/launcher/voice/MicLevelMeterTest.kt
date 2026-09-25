package com.byd.clusternav.launcher.voice

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MicLevelMeterTest {
    @Test
    fun `dinh va rms tinh tren toan bo mau da nap`() {
        val m = MicLevelMeter(16_000)
        assertEquals(3, m.feed(shortArrayOf(3, -4, 0, 0), 2))   // chỉ 2 mẫu đầu; sqrt((9+16)/2)=3.5→3
        assertEquals(9, m.feed(shortArrayOf(-12, 5), 2))        // sqrt((144+25)/2)=9.19→9
        assertEquals(12, m.peak)
        assertEquals(4, m.samples)
        // sqrt((9+16+144+25)/4) = sqrt(48.5) = 6.96 → 6
        assertEquals(6, m.rms)
        assertEquals("mức micro: đỉnh 12/32767 · rms 6 · 4 mẫu (0ms)", m.line())
    }

    @Test
    fun `rong thi rms 0 khong chia cho 0 va ms theo sample rate`() {
        val m = MicLevelMeter(16_000)
        assertEquals(0, m.rms)
        assertEquals(0, m.feed(ShortArray(0), 0))
        m.feed(ShortArray(16_000), 16_000)
        assertEquals("mức micro: đỉnh 0/32767 · rms 0 · 16000 mẫu (1000ms)", m.line())
    }
}
