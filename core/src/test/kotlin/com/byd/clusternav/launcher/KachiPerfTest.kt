package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Khoá hợp đồng của công cụ ĐO (PERF 2026-09-16): nó phải **chuẩn hoá theo cửa sổ đo được**, không nhân với một
 * hằng số giả định — nhịp gọi là `postDelayed(10 s)` trên luồng chính và sẽ TRÔI đúng vào lúc máy bận, tức đúng
 * lúc con số quan trọng nhất.
 */
class KachiPerfTest {

    @Test
    fun `lan dau chi dat moc, khong in cua so rac`() {
        KachiPerf.reset()
        assertNull(KachiPerf.dueLine(1_000_000), "lần đầu chưa có mốc trước ⇒ không có cửa sổ để chia")
    }

    @Test
    fun `chua du mot phut thi im`() {
        KachiPerf.reset()
        KachiPerf.dueLine(0)
        assertNull(KachiPerf.dueLine(59_000))
    }

    @Test
    fun `chuan hoa theo cua so THAT chu khong theo hang so`() {
        KachiPerf.reset()
        KachiPerf.dueLine(0)
        repeat(60) { KachiPerf.add(KachiPerf.Counter.HAL_READ) }
        // Cửa sổ trôi thành 120 s ⇒ 60 lượt là 30/phút, KHÔNG phải 60/phút.
        val line = KachiPerf.dueLine(120_000)
        assertNotNull(line)
        assertTrue(line!!.contains("HAL đọc=30/phút"), "sai chuẩn hoá: $line")
    }

    @Test
    fun `dong ho lui thi dat lai moc, khong chia cho so am`() {
        KachiPerf.reset()
        KachiPerf.dueLine(600_000)
        assertNull(KachiPerf.dueLine(100_000), "đồng hồ lùi ⇒ đặt lại mốc, không in số bịa")
        KachiPerf.add(KachiPerf.Counter.HAL_READ, 60)
        val line = KachiPerf.dueLine(160_000)
        assertNotNull(line)
        assertTrue(line!!.contains("HAL đọc=60/phút"), "sau khi đặt lại mốc, cửa sổ phải tính từ mốc mới: $line")
    }

    @Test
    fun `xa bo dem sau moi bao cao`() {
        KachiPerf.reset()
        KachiPerf.dueLine(0)
        KachiPerf.add(KachiPerf.Counter.SHELL_CMD, 10)
        KachiPerf.dueLine(60_000)
        val line = KachiPerf.dueLine(120_000)
        assertNotNull(line)
        assertTrue(line!!.contains("shell=0.0/phút"), "bộ đếm không được cộng dồn qua hai cửa sổ: $line")
    }
}
