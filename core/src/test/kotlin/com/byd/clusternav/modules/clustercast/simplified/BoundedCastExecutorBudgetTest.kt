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

    /** Lượt mở dài hơn hạn chung KHÔNG bị ngắt khi gửi với hạn riêng — và bị ngắt nếu gửi với hạn chung (đường cũ). */
    @Test
    fun `han rieng giu luot dai song qua han chung, han chung thi ngat`() {
        val timeouts = CopyOnWriteArrayList<String>()
        val ex = BoundedCastExecutor(castTimeoutMs = 150L, stopTimeoutMs = 100L, onTimeout = { timeouts += it })
        try {
            val longOk = AtomicReference<String>("chưa chạy")
            val a = CountDownLatch(1)
            ex.submit("open", 2_000L) {
                try { Thread.sleep(400); longOk.set("xong") } catch (e: InterruptedException) { longOk.set("bị ngắt") }
                a.countDown()
            }
            assertTrue(a.await(5, TimeUnit.SECONDS))
            assertEquals("xong", longOk.get())
            assertFalse("open" in timeouts)

            val shortRun = AtomicReference<String>("chưa chạy")
            val b = CountDownLatch(1)
            ex.submit("cast") {
                try { Thread.sleep(400); shortRun.set("xong") } catch (e: InterruptedException) { shortRun.set("bị ngắt") }
                b.countDown()
            }
            assertTrue(b.await(5, TimeUnit.SECONDS))
            assertEquals("bị ngắt", shortRun.get(), "hạn chung 150 ms ⇒ ngắt")
            assertTrue("cast" in timeouts)
        } finally {
            ex.shutdown()
        }
    }

    @Test
    fun `thao tac qua han ma than con chay noc - van tinh la BAN`() {
        val ex = BoundedCastExecutor(castTimeoutMs = 100L)
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
            Thread.sleep(300)                                    // đã quá hạn 100 ms ⇒ future bị huỷ
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
