package com.byd.clusternav.launcher.camera

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Khoá BG-15 (`perf-inventory-2026-09-25.md`): luồng `KachiHalSignal` (a) không nhân đôi khi `start` gọi hai lần,
 * (b) dừng được bằng `stop()`, (c) khi KHÔNG BAO GIỜ nối được (máy ảo / xe không helper) thì thưa nhịp thử về trần
 * 60 s thay vì 8 s vĩnh viễn — nhưng KHÔNG dừng hẳn.
 */
class HalSignalClientBackoffTest {

    /** Mô phỏng lịch thử nối trong [horizonMs] với hàm backoff cho sẵn — đếm số lần connect. */
    private fun attemptsWithin(horizonMs: Long, next: (Long, Int) -> Long): Int {
        var t = 0L
        var backoff = HalSignalClient.BACKOFF_START_MS
        var failures = 0
        var attempts = 0
        while (t < horizonMs) {
            attempts++            // connect thất bại ngay
            failures++
            t += backoff
            backoff = next(backoff, failures)
        }
        return attempts
    }

    @Test
    fun `khong noi duoc 2 phut gia - so lan connect co tran, va tran cu 8 s thi vuot`() {
        val fixed = attemptsWithin(120_000L, HalSignalClient::nextBackoffMs)
        // Ngủ 1+2+4+8+8+8+8+8 = 47 s (8 lần thử) → 16 (63 s) → 32 (95 s) → 60: lần thứ 11 rơi ở 95 s, lần 12 mới ở 155 s.
        assertEquals(11, fixed)
        assertTrue(fixed <= 12, "≤ 12 lần connect / 2 phút khi helper vắng (đo: $fixed)")
        // Bằng chứng ĐỎ: hàm cũ (trần 8 s vĩnh viễn) cho ~18 lần trong 2 phút, ~77 lần trong 10 phút.
        val legacy = attemptsWithin(120_000L) { cur, _ -> (cur * 2).coerceAtMost(HalSignalClient.BACKOFF_CAP_MS) }
        assertTrue(legacy > 12, "trần cũ phải vượt ngưỡng để test này có ý nghĩa (đo: $legacy)")
        assertTrue(attemptsWithin(600_000L, HalSignalClient::nextBackoffMs) <= 20, "10 phút: ≤ 20 lần")
    }

    @Test
    fun `backoff nhan doi toi 8 s, sau 8 lan that bai lien tiep tran len 60 s, khong vo han`() {
        assertEquals(2_000L, HalSignalClient.nextBackoffMs(1_000L, 1))
        assertEquals(8_000L, HalSignalClient.nextBackoffMs(8_000L, 7))
        assertEquals(16_000L, HalSignalClient.nextBackoffMs(8_000L, HalSignalClient.IDLE_AFTER_FAILURES))
        assertEquals(60_000L, HalSignalClient.nextBackoffMs(60_000L, 50))
        assertEquals(60_000L, HalSignalClient.BACKOFF_CAP_IDLE_MS, "vẫn thử mỗi 60 s — không vĩnh viễn")
    }

    private fun freePort(): Int = ServerSocket(0).use { it.localPort }   // đóng ngay ⇒ không ai lắng nghe

    private fun signalThreads(): List<Thread> =
        Thread.getAllStackTraces().keys.filter { it.name == "KachiHalSignal" && it.isAlive }

    @Test
    fun `start hai lan chi co MOT luong, stop thi luong chet`() {
        val before = signalThreads().size
        val client = HalSignalClient(port = freePort())
        client.start { _, _ -> }
        client.start { _, _ -> }
        Thread.sleep(150)   // cho luồng vào vòng thử nối (connect refused tức thì → sleep 1 s)
        assertEquals(before + 1, signalThreads().size, "start×2 ⇒ đúng một luồng KachiHalSignal")
        client.stop()
        val deadline = System.currentTimeMillis() + 3_000
        while (signalThreads().size > before && System.currentTimeMillis() < deadline) Thread.sleep(20)
        assertEquals(before, signalThreads().size, "stop() phải bẻ được luồng (interrupt lúc ngủ / đóng socket)")
    }

    @Test
    fun `luong that voi dong ho gia - 2 phut khong helper thi connect it hon 12 lan`() {
        val fakeNow = AtomicLong(0)
        val horizon = CountDownLatch(1)
        val client = HalSignalClient(port = freePort()) { ms ->
            if (fakeNow.addAndGet(ms) >= 120_000L) { horizon.countDown(); throw InterruptedException("hết 2 phút giả") }
        }
        client.start { _, _ -> }
        assertTrue(horizon.await(10, TimeUnit.SECONDS), "vòng thử nối phải chạy tới mốc 2 phút giả")
        client.stop()
        assertTrue(client.connectAttempts.get() in 2..12, "connect ${client.connectAttempts.get()} lần / 2 phút giả")
    }
}
