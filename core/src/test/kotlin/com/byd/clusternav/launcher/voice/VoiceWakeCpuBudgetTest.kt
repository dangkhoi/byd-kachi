package com.byd.clusternav.launcher.voice

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

/**
 * VOICE-WAKE-CPU-BUDGET — khoá bài học [ĐO xe 15/10 tối]: `:wake` 105 % TB / 263 % đỉnh khi nhạc phát, vì trần cũ
 * đếm khung chứ không đếm CPU. Các bài dưới bơm giá giải mã thật của xe (≈ 1 350 ms CPU / lượt).
 * Red-proof: cho [VoiceWakeCpuBudget.mayRun] luôn `true` (= không có ngân sách, hành vi cũ) ⇒ bài 1, 3, 4 đỏ.
 */
class VoiceWakeCpuBudgetTest {

    /** Mô phỏng: engine muốn giải mã mỗi [everyMs] (nhịp 500 ms của `VoiceWakeAsr`), mỗi lượt tốn [costMs] CPU. */
    private fun simulate(b: VoiceWakeCpuBudget, durationMs: Long, everyMs: Long, cost: (Int) -> Long): List<Pair<Long, Long>> {
        val runs = ArrayList<Pair<Long, Long>>()
        var t = 0L
        while (t < durationMs) {
            if (b.mayRun(t)) { val c = cost(runs.size); b.charge(c, t); runs += t to c }
            t += everyMs
        }
        return runs
    }

    @Test fun `con qua ngan sach thi luot giai ma bi gian ra`() {
        val b = VoiceWakeCpuBudget(duty = 0.25, burstMs = 4_000.0)
        val runs = simulate(b, 120_000L, 500L) { 1_350L }
        val cpu = runs.sumOf { it.second }
        // Trước: 240 lượt × 1 350 = 324 s CPU trong 120 s (2,7 lõi). Nay ≤ burst + 25 % × 120 s + một lượt.
        assertTrue(cpu <= 4_000 + 30_000 + 1_350, "CPU = $cpu ms vượt chặn trên")
        assertTrue(cpu >= 30_000 - 1_350, "không được cắt quá tay: CPU = $cpu ms (phải dùng gần đủ 25 %)")
        // Pha ổn định (sau khi xô cạn): khoảng cách giữa hai lượt ≈ giá / duty = 5,4 s (± một nhịp 500 ms).
        val steady = runs.map { it.first }.filter { it > 30_000 }.zipWithNext { a, c -> c - a }
        assertTrue(steady.isNotEmpty())
        steady.forEach { assertTrue(it in 5_000L..6_000L, "khoảng cách ổn định $it ms ∉ [5 000, 6 000]") }
    }

    @Test fun `phuc hoi - sau quang yen du dai thi chay lien mot chuoi lai`() {
        val b = VoiceWakeCpuBudget(duty = 0.25, burstMs = 4_000.0)
        simulate(b, 30_000L, 500L) { 1_350L }                 // làm cạn xô
        assertFalse(b.mayRun(30_000L) && b.creditMs() > 1_350.0, "vừa nhạc liên tục ⇒ xô không thể còn đầy")
        // 20 s yên (không ai gọi engine) ⇒ xô đầy lại ⇒ 3 lượt giải mã liền nhau được chạy (trọn một câu gọi).
        val t0 = 50_000L
        var n = 0
        for (k in 0 until 3) { val t = t0 + k * 500L; assertTrue(b.mayRun(t), "lượt #${k + 1} sau quãng yên"); b.charge(1_350L, t); n++ }
        assertEquals(3, n)
    }

    @Test fun `chan tren dung tren MOI khoang - gia ngau nhien`() {
        val rnd = Random(20261015)
        val b = VoiceWakeCpuBudget(duty = 0.25, burstMs = 4_000.0)
        val runs = simulate(b, 600_000L, 100L) { rnd.nextLong(0L, 3_000L) }
        val maxCost = 3_000L
        // Mọi cửa sổ trượt T ∈ {10 s, 30 s, 60 s}: Σ ≤ burst + duty·T + một lượt.
        for (win in listOf(10_000L, 30_000L, 60_000L)) {
            var j = 0; var sum = 0L
            for (i in runs.indices) {
                sum += runs[i].second
                while (runs[i].first - runs[j].first >= win) { sum -= runs[j].second; j++ }
                assertTrue(sum <= 4_000 + 0.25 * win + maxCost, "cửa sổ $win ms tại ${runs[i].first}: $sum ms CPU")
            }
        }
    }

    @Test fun `tran duoc ap - no thi khong chay, so am khong nap xo`() {
        val b = VoiceWakeCpuBudget(duty = 0.25, burstMs = 1_000.0)
        assertTrue(b.mayRun(0L))
        b.charge(5_000L, 0L)                                  // một lượt đắt bất thường ⇒ nợ 4 000
        assertFalse(b.mayRun(10_000L), "nợ 4 000, nạp 2 500 sau 10 s ⇒ vẫn chưa được chạy")
        b.charge(-9_999L, 10_000L)                            // số rác không được *nạp* xô
        assertFalse(b.mayRun(10_000L))
        assertTrue(b.mayRun(16_100L), "trả hết nợ (16 s × 25 %) ⇒ chạy lại")
        // Đồng hồ lùi không nạp xô.
        val c = VoiceWakeCpuBudget(duty = 0.25, burstMs = 1_000.0)
        c.mayRun(100_000L); c.charge(1_000L, 100_000L)
        assertFalse(c.mayRun(50_000L), "đồng hồ lùi ⇒ không được coi là thời gian đã trôi")
    }

    @Test fun `mac dinh la hang so duy nhat 25 phan tram, burst 4 giay CPU`() {
        assertEquals(0.25, VoiceWakeCpuBudget.DUTY)
        val b = VoiceWakeCpuBudget()
        assertEquals(4_000.0, b.creditMs(), 1e-9)
    }
}
