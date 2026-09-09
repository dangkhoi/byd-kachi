package com.byd.clusternav.system

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * [PrioritySerialExecutor] — bằng chứng cho bất biến B2b: one-at-a-time NHƯNG STOP/RESCUE rút TRƯỚC NORMAL
 * đã xếp; FIFO trong CÙNG mức; chặn + trả kết quả + mở-bọc exception. Thuần JVM (chạy trong :core:test).
 */
class PrioritySerialExecutorTest {

    /** Chờ hàng đợi có ĐỦ [n] task đang chờ (xác nhận đã nạp xong TRƯỚC khi thả worker). */
    private fun awaitPending(exec: PrioritySerialExecutor, n: Int) {
        val deadline = System.currentTimeMillis() + 2000
        while (exec.pendingCount() < n && System.currentTimeMillis() < deadline) Thread.sleep(2)
        assertTrue(exec.pendingCount() >= n, "cần >= $n task chờ, đang có ${exec.pendingCount()}")
    }

    @Test
    fun `STOP drains before an already-queued NORMAL while the worker is busy`() {
        val exec = PrioritySerialExecutor("test-prio")
        val order = CopyOnWriteArrayList<String>()
        val release = CountDownLatch(1)
        val started = CountDownLatch(1)

        // 1. Chiếm worker DUY NHẤT bằng một NORMAL chặn tới khi được thả.
        val blocker = Thread {
            exec.submit(MutationPriority.NORMAL) {
                started.countDown()
                release.await(5, TimeUnit.SECONDS)
                order += "blocker"
            }
        }.apply { start() }
        assertTrue(started.await(2, TimeUnit.SECONDS), "blocker phải bắt đầu")

        // 2. Nạp NORMAL TRƯỚC (seq nhỏ hơn), rồi STOP — mỗi cái trên thread riêng vì submit CHẶN.
        val n = Thread { exec.submit(MutationPriority.NORMAL) { order += "normal" } }.apply { start() }
        awaitPending(exec, 1) // NORMAL đã vào hàng đợi (seq < STOP)
        val s = Thread { exec.submit(MutationPriority.STOP) { order += "stop" } }.apply { start() }
        awaitPending(exec, 2) // STOP đã vào hàng đợi (sau NORMAL về seq, nhưng ưu tiên cao hơn về rank)

        // 3. Thả worker: STOP PHẢI chạy trước NORMAL đã xếp trước nó.
        release.countDown()
        blocker.join(2000); n.join(2000); s.join(2000)

        assertEquals(listOf("blocker", "stop", "normal"), order)
    }

    @Test
    fun `same-priority bodies keep FIFO submission order`() {
        val exec = PrioritySerialExecutor("test-fifo")
        val order = CopyOnWriteArrayList<String>()
        val release = CountDownLatch(1)
        val started = CountDownLatch(1)

        val blocker = Thread {
            exec.submit(MutationPriority.NORMAL) { started.countDown(); release.await(5, TimeUnit.SECONDS) }
        }.apply { start() }
        assertTrue(started.await(2, TimeUnit.SECONDS))

        val threads = (1..3).map { i ->
            Thread { exec.submit(MutationPriority.NORMAL) { order += "n$i" } }.apply { start() }
                .also { awaitPending(exec, i) } // ép thứ tự nạp n1 < n2 < n3
        }
        release.countDown()
        threads.forEach { it.join(2000) }
        blocker.join(2000)

        assertEquals(listOf("n1", "n2", "n3"), order, "cùng mức NORMAL phải giữ FIFO — chuỗi reflow không được đảo")
    }

    @Test
    fun `RESCUE drains after STOP but before NORMAL`() {
        val exec = PrioritySerialExecutor("test-rank")
        val order = CopyOnWriteArrayList<String>()
        val release = CountDownLatch(1)
        val started = CountDownLatch(1)

        val blocker = Thread {
            exec.submit(MutationPriority.NORMAL) { started.countDown(); release.await(5, TimeUnit.SECONDS) }
        }.apply { start() }
        assertTrue(started.await(2, TimeUnit.SECONDS))

        // Nạp theo NORMAL, RESCUE, STOP (seq tăng) — thứ tự RÚT phải là STOP, RESCUE, NORMAL (theo rank).
        val n = Thread { exec.submit(MutationPriority.NORMAL) { order += "normal" } }.apply { start() }
        awaitPending(exec, 1)
        val r = Thread { exec.submit(MutationPriority.RESCUE) { order += "rescue" } }.apply { start() }
        awaitPending(exec, 2)
        val s = Thread { exec.submit(MutationPriority.STOP) { order += "stop" } }.apply { start() }
        awaitPending(exec, 3)

        release.countDown()
        blocker.join(2000); n.join(2000); r.join(2000); s.join(2000)

        assertEquals(listOf("stop", "rescue", "normal"), order)
    }

    @Test
    fun `submit returns the body result and propagates exceptions unwrapped`() {
        val exec = PrioritySerialExecutor("test-ret")
        assertEquals(42, exec.submit(MutationPriority.NORMAL) { 42 })
        val ex = assertThrows<IllegalStateException> {
            exec.submit(MutationPriority.NORMAL) { throw IllegalStateException("boom") }
        }
        assertEquals("boom", ex.message)
    }

    @Test
    fun `STOP submitted while idle just runs`() {
        val exec = PrioritySerialExecutor("test-idle")
        assertEquals("ok", exec.submit(MutationPriority.STOP) { "ok" })
        assertTrue(exec.isIdle)
    }
}
