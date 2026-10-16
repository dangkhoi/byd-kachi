package com.byd.clusternav.modules.clustercast.simplified

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * ═══ 2.93 · CAST-OPEN-TIMEOUT — hạn RIÊNG từng thao tác + phần hạn còn lại đọc được từ trong thân ═══════════════════════════
 *
 * Khoá: (1) [BoundedCastExecutor.submit] nhận hạn riêng (lượt mở chiếu 25 s, các thao tác khác giữ 15 s) — [ĐO log xe 06/10 13:49 ·
 * 15:13] `TIMEOUT: openProjection` ở hạn chung 15 s; (2) [BoundedCastExecutor.remainingMs] chỉ có giá trị TRONG thân một thao tác
 * và phản ánh đúng hạn của thao tác đó — cổng theme dùng nó để không chờ bóng nổi ẩn quá phần hạn còn dư; (3) thao tác đã quá hạn
 * mà thân còn chạy nốt vẫn tính là BẬN (`isIdle` sai) — mối nguy READY-RESTART-MID-CAST đọc đúng sự thật.
 */
class BoundedCastExecutorBudgetTest {

    @Test
    fun `phan han con lai chi co trong than thao tac, dung han RIENG cua thao tac do`() {
        val ex = BoundedCastExecutor(castTimeoutMs = 15_000L, stopTimeoutMs = 5_000L)
        try {
            assertNull(BoundedCastExecutor.remainingMs(), "ngoài thao tác ⇒ không có hạn")
            val seen = CopyOnWriteArrayList<Long?>()
            val firstTwo = CountDownLatch(2)
            ex.submit("normal") { seen += BoundedCastExecutor.remainingMs(); firstTwo.countDown() }
            ex.submit("open", BoundedCastExecutor.OPEN_TIMEOUT_MS) { seen += BoundedCastExecutor.remainingMs(); firstTwo.countDown() }
            assertTrue(firstTwo.await(5, TimeUnit.SECONDS))     // xong cả hai TRƯỚC khi Dừng (Dừng huỷ thao tác đang chạy/chờ)
            val stopped = CountDownLatch(1)
            ex.submitStop("stop") { seen += BoundedCastExecutor.remainingMs(); stopped.countDown() }
            assertTrue(stopped.await(5, TimeUnit.SECONDS))
            val normal = seen[0]!!
            val open = seen[1]!!
            val stop = seen[2]!!
            // Dung sai 4 s cho máy chạy test bận — ba hạn (5 · 15 · 25 s) vẫn tách nhau rõ.
            assertTrue(normal in 11_000L..15_000L, "thao tác thường: hạn chung 15 s ($normal)")
            assertTrue(open in 21_000L..BoundedCastExecutor.OPEN_TIMEOUT_MS, "lượt mở: hạn riêng 25 s ($open)")
            assertTrue(stop in 1_000L..5_000L, "dừng: hạn 5 s ($stop)")
            assertNull(BoundedCastExecutor.remainingMs(), "luồng gọi vẫn ngoài thao tác")
        } finally {
            ex.shutdown()
        }
    }

    /**
     * Bộ hẹn giờ TAY — hạn chỉ bắn khi test gọi [advance], và bắn ĐỒNG BỘ trên luồng test. 3.1.10 · TEST-FLAKY-CAST-BUDGET [ĐO,
     * tái hiện 3/200 dưới tải 48 luồng quay + rác trong JVM]: bản cũ dùng giờ thật, và luồng hẹn giờ làm HAI bước `cancel(true)` rồi
     * `onTimeout` — bước 1 ngắt thân ⇒ thân đếm `b` ⇒ luồng test đọc `timeouts` TRƯỚC khi luồng hẹn giờ (bị tải đẩy ra) kịp làm bước
     * 2 ⇒ `"cast" in timeouts` sai. Cùng họ, chưa thấy nhưng cùng gốc: worker nhận việc trễ > 150 ms (việc hết hạn khi còn chờ) hoặc
     * luồng dừng qua cả hai mốc (thân ngủ xong trước khi bị ngắt). Nay thứ tự do test quyết, không do bộ lập lịch của máy.
     */
    private class ManualTimer : BoundedCastExecutor.CastTimer {
        private var now = 0L
        private val due = ArrayList<Pair<Long, () -> Unit>>()

        override fun after(delayMs: Long, action: () -> Unit) {
            synchronized(this) { due += (now + delayMs) to action }
        }

        /** Tiến đồng hồ [ms] và chạy (trên luồng test) mọi hạn đã tới, theo thứ tự hạn. */
        fun advance(ms: Long) {
            val fire = synchronized(this) {
                now += ms
                due.filter { it.first <= now }.sortedBy { it.first }.also { due.removeAll(it.toSet()) }
            }
            fire.forEach { it.second() }
        }
    }

    /** Lượt mở dài hơn hạn chung KHÔNG bị ngắt khi gửi với hạn riêng — và bị ngắt nếu gửi với hạn chung (đường cũ). */
    @Test
    fun `han rieng giu luot dai song qua han chung, han chung thi ngat`() {
        val timeouts = CopyOnWriteArrayList<String>()
        val timer = ManualTimer()
        val ex = BoundedCastExecutor(castTimeoutMs = 150L, stopTimeoutMs = 100L, onTimeout = { timeouts += it }, timer = timer)
        try {
            val longOk = AtomicReference<String>("chưa chạy")
            val aRunning = CountDownLatch(1)
            val aRelease = CountDownLatch(1)
            val a = CountDownLatch(1)
            ex.submit("open", 2_000L) {
                aRunning.countDown()
                try { aRelease.await(); longOk.set("xong") } catch (e: InterruptedException) { longOk.set("bị ngắt") }
                a.countDown()
            }
            assertTrue(aRunning.await(5, TimeUnit.SECONDS))
            timer.advance(400L)                                  // quá hạn chung 150 ms, chưa tới hạn riêng 2 s
            aRelease.countDown()
            assertTrue(a.await(5, TimeUnit.SECONDS))
            assertEquals("xong", longOk.get())
            assertFalse("open" in timeouts)

            val shortRun = AtomicReference<String>("chưa chạy")
            val bRunning = CountDownLatch(1)
            val b = CountDownLatch(1)
            ex.submit("cast") {
                bRunning.countDown()
                try { CountDownLatch(1).await(); shortRun.set("xong") } catch (e: InterruptedException) { shortRun.set("bị ngắt") }
                b.countDown()
            }
            assertTrue(bRunning.await(5, TimeUnit.SECONDS))     // thân ĐANG chạy rồi mới cho hạn bắn — không đua với worker
            timer.advance(400L)
            assertTrue(b.await(5, TimeUnit.SECONDS))
            assertEquals("bị ngắt", shortRun.get(), "hạn chung 150 ms ⇒ ngắt")
            assertTrue("cast" in timeouts)
        } finally {
            ex.shutdown()
        }
    }

    /**
     * 3.1.10 · TEST-FLAKY-CAST-BUDGET — đường (a) của bài trên là lỗi THẬT của mã chạy: thao tác hết hạn khi còn chờ bị huỷ IM
     * LẶNG (không TIMEOUT, không DISCARDED). Nay: báo [BoundedCastExecutor] `onDiscard`, vẫn không TIMEOUT giả (F4), thân không
     * chạy. Thử ĐỎ: bỏ nhánh `else reportDiscard(...)` trong `armTimeout`.
     */
    @Test
    fun `thao tac het han khi con cho thi bao BO, khong TIMEOUT gia, khong chay`() {
        val timeouts = CopyOnWriteArrayList<String>()
        val discards = CopyOnWriteArrayList<String>()
        val timer = ManualTimer()
        val ex = BoundedCastExecutor(
            castTimeoutMs = 15_000L, onTimeout = { timeouts += it }, onDiscard = { tag, why -> discards += "$tag|$why" }, timer = timer,
        )
        val running = CountDownLatch(1)
        val release = CountDownLatch(1)
        val ranB = AtomicReference(false)
        try {
            ex.submit("openProjection", BoundedCastExecutor.OPEN_TIMEOUT_MS) { running.countDown(); release.await(5, TimeUnit.SECONDS) }
            assertTrue(running.await(5, TimeUnit.SECONDS))
            ex.submit("cast-slot") { ranB.set(true) }           // chờ sau lượt mở; hạn 15 s tính từ lúc gửi
            timer.advance(16_000L)                               // lượt mở vẫn trong hạn 25 s; cast-slot quá hạn khi còn chờ
            assertEquals(1, discards.size, "phải có ĐÚNG một dấu bỏ: $discards")
            assertTrue(discards.single().startsWith("cast-slot|hết hạn 15000 ms khi còn chờ"), discards.single())
            assertTrue(timeouts.isEmpty(), "chưa từng chạy ⇒ không TIMEOUT: $timeouts")
            release.countDown()
            val deadline = System.currentTimeMillis() + 2_000
            while (!ex.isIdle && System.currentTimeMillis() < deadline) Thread.sleep(10)
            assertTrue(ex.isIdle)
            assertFalse(ranB.get(), "thao tác đã bỏ không được chạy muộn")
        } finally {
            release.countDown()
            ex.shutdown()
        }
    }

    /**
     * Soát Pass 11 [P1] — thao tác hết hạn khi còn chờ phải RỜI hàng, không nằm lại như "ma": hàng 1 chỗ, lần gửi kế đẩy nó ra qua
     * `DiscardOldestLogged` ⇒ dấu bỏ THỨ HAI cùng tag ⇒ `SimpleCastCoordinator.onDiscard` gọi `resizeQueue.dropped(tag)` ⇒ yêu cầu −/+
     * vừa xếp của chính lần gửi ấy bị DROPPED dù nó sẽ chạy. Thử ĐỎ: bỏ `executor.remove(future)` ⇒ `discards.size == 2`.
     */
    @Test
    fun `thao tac het han khi con cho roi khoi hang - lan gui ke khong bi bao bo lan hai va van chay`() {
        val discards = CopyOnWriteArrayList<String>()
        val timer = ManualTimer()
        val ex = BoundedCastExecutor(castTimeoutMs = 15_000L, onDiscard = { tag, why -> discards += "$tag|$why" }, timer = timer)
        val running = CountDownLatch(1)
        val release = CountDownLatch(1)
        val ranC = AtomicReference(false)
        try {
            ex.submit("openProjection", BoundedCastExecutor.OPEN_TIMEOUT_MS) { running.countDown(); release.await(5, TimeUnit.SECONDS) }
            assertTrue(running.await(5, TimeUnit.SECONDS))
            ex.submit("resize") { }                              // chờ sau lượt mở
            timer.advance(16_000L)                               // hết hạn khi còn chờ ⇒ một dấu bỏ
            assertEquals(1, discards.size, discards.toString())
            ex.submit("resize") { ranC.set(true) }               // cú bấm kế: KHÔNG được đẩy "ma" ra và báo bỏ lần hai cùng tag
            assertEquals(1, discards.size, "thao tác đã hết hạn phải rời hàng — không 'bị thay bởi' lần hai: $discards")
            release.countDown()
            val deadline = System.currentTimeMillis() + 2_000
            while (!ex.isIdle && System.currentTimeMillis() < deadline) Thread.sleep(10)
            assertTrue(ex.isIdle)
            assertTrue(ranC.get(), "cú bấm kế chạy bình thường sau lượt mở")
            assertEquals(1, discards.size, discards.toString())
        } finally {
            release.countDown()
            ex.shutdown()
        }
    }

    @Test
    fun `thao tac qua han ma than con chay noc - van tinh la BAN`() {
        val timer = ManualTimer()
        val ex = BoundedCastExecutor(castTimeoutMs = 100L, timer = timer)
        val release = CountDownLatch(1)
        val running = CountDownLatch(1)
        try {
            ex.submit("stuck-shell") {
                running.countDown()
                // Lệnh shell KHÔNG ngắt được: nuốt cờ ngắt, chạy tiếp tới khi được thả.
                while (release.count > 0) {
                    try { release.await(50, TimeUnit.MILLISECONDS) } catch (e: InterruptedException) { /* không ngắt được */ }
                }
            }
            assertTrue(running.await(2, TimeUnit.SECONDS))
            timer.advance(300L)                                  // đã quá hạn 100 ms ⇒ future bị huỷ (bắn đồng bộ trên luồng này)
            assertFalse(ex.isIdle, "thân còn gửi lệnh tới cụm ⇒ BẬN")
            release.countDown()
            val deadline = System.currentTimeMillis() + 2000
            while (!ex.isIdle && System.currentTimeMillis() < deadline) Thread.sleep(10)
            assertTrue(ex.isIdle)
        } finally {
            release.countDown()
            ex.shutdown()
        }
    }

    @Test
    fun `viec hen gio khong co han thao tac`() {
        val ex = BoundedCastExecutor()
        val seen = AtomicReference<Long?>(-1L)
        val done = CountDownLatch(1)
        try {
            ex.schedule(10L) { seen.set(BoundedCastExecutor.remainingMs()); done.countDown() }
            assertTrue(done.await(2, TimeUnit.SECONDS))
            assertNull(seen.get())
        } finally {
            ex.shutdown()
        }
    }

    @Test
    fun `luot mo chieu cua coordinator dung han rieng 25 s, cac thao tac khac giu han chung`() {
        assertEquals(25_000L, BoundedCastExecutor.OPEN_TIMEOUT_MS)
        val code = SourceRoots.codeOf("src/main/kotlin/com/byd/clusternav/modules/clustercast/simplified/SimpleCastCoordinator.kt")
        assertTrue(code.contains("executor.submit(\"openProjection\", openTimeoutMs) {"), "openProjection phải gửi với hạn riêng")
        assertTrue(code.contains("private val openTimeoutMs: Long = BoundedCastExecutor.OPEN_TIMEOUT_MS"))
        assertEquals(1, Regex("openTimeoutMs\\)").findAll(code).count(), "CHỈ lượt mở dùng hạn riêng")
    }
}
