package com.byd.clusternav.modules.clustercast.simplified

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.atomic.AtomicBoolean

/**
 * ═══ 2.93 · review CAST Pass 1 F2/F4 — Dừng ngắt đúng thao tác ĐANG CHẠY; hạn của Dừng tính từ lúc nó chạy; không `TIMEOUT` giả ═══
 *
 * Lỗi có từ trước 2.93, hạn 25 s của lượt mở (CAST-OPEN-TIMEOUT) làm nặng thêm: có thao tác chờ thì `activeFuture` là thao tác
 * CHỜ ⇒ Dừng chỉ huỷ nó, lượt mở đang chạy không bị ngắt; Dừng xếp sau với hạn 5 s tính từ lúc GỬI ⇒ lượt mở còn chạy > 5 s là
 * Dừng bị huỷ trước khi chạy (người lái bấm Dừng mà không dừng). Thao tác bị bỏ khỏi hàng vẫn báo `TIMEOUT: <tag>` giả trong log.
 */
class BoundedCastExecutorStopTest {

    /** Thử ĐỎ: bỏ dòng `runningFuture.get()?.cancel(true)` trong `submitStop`. */
    @Test
    fun `Dung ngat thao tac DANG CHAY ke ca khi co thao tac cho sau no`() {
        val ex = BoundedCastExecutor(castTimeoutMs = 30_000L, stopTimeoutMs = 5_000L)
        val started = CountDownLatch(1)
        val interrupted = CountDownLatch(1)
        val pendingRan = AtomicBoolean(false)
        val stopped = CountDownLatch(1)
        ex.submit("openProjection") {
            started.countDown()
            try {
                Thread.sleep(20_000L)
            } catch (e: InterruptedException) {
                interrupted.countDown()
                throw e
            }
        }
        assertTrue(started.await(5, SECONDS))
        ex.submit("castFull") { pendingRan.set(true) }
        ex.submitStop("stop") { stopped.countDown() }
        assertTrue(interrupted.await(5, SECONDS), "lượt mở ĐANG CHẠY phải bị ngắt, không chỉ thao tác chờ")
        assertTrue(stopped.await(5, SECONDS), "Dừng phải chạy")
        assertFalse(pendingRan.get(), "thao tác chờ bị dọn khỏi hàng")
        ex.shutdown()
    }

    /** Thử ĐỎ: tính hạn của Dừng từ lúc GỬI (bản cũ) ⇒ Dừng bị huỷ khi còn chờ, không bao giờ chạy. */
    @Test
    fun `han cua Dung tinh tu luc no CHAY - doi lenh khong ngat duoc chay not roi van chay`() {
        val timeouts = CopyOnWriteArrayList<String>()
        val ex = BoundedCastExecutor(castTimeoutMs = 30_000L, stopTimeoutMs = 300L, onTimeout = { timeouts += it })
        val started = CountDownLatch(1)
        val stopped = CountDownLatch(1)
        ex.submit("openProjection") {
            started.countDown()
            // 1 s KHÔNG ngắt được — như một lệnh shell đang đọc (cờ ngắt bị bỏ qua).
            val end = System.nanoTime() + 1_000_000_000L
            while (System.nanoTime() < end) Thread.onSpinWait()
        }
        assertTrue(started.await(5, SECONDS))
        ex.submitStop("stop") { stopped.countDown() }
        assertTrue(stopped.await(5, SECONDS), "Dừng phải được chạy sau khi thao tác trước chạy nốt")
        assertFalse("stop" in timeouts, "Dừng không được bị huỷ trước khi kịp chạy: $timeouts")
        ex.shutdown()
    }

    /** Thử ĐỎ: bỏ điều kiện `started.get()` trước `onTimeout` trong `submit`. */
    @Test
    fun `thao tac bi bo khoi hang (chua tung chay) khong bao TIMEOUT gia`() {
        val timeouts = CopyOnWriteArrayList<String>()
        val ex = BoundedCastExecutor(castTimeoutMs = 30_000L, onTimeout = { timeouts += it })
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        ex.submit("A") {
            started.countDown()
            release.await(5, SECONDS)
        }
        assertTrue(started.await(5, SECONDS))
        ex.submit("B", timeoutMs = 300L) { }      // chờ trong hàng
        ex.submit("C") { }                        // sức chứa 1 + DiscardOldest ⇒ B bị bỏ, không bao giờ chạy
        Thread.sleep(800L)                        // quá hạn 300 ms của B
        release.countDown()
        assertFalse("B" in timeouts, "B chưa từng chạy ⇒ không có TIMEOUT giả: $timeouts")
        ex.shutdown()
    }
}
