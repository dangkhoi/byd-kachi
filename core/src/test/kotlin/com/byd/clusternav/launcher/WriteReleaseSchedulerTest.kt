package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * FIX286 · SR2 — bộ hẹn THẬT ([WriteReleaseScheduler.Jvm]) giữ đúng hợp đồng `Handler` của app OEM:
 * `sendEmptyMessageDelayed` (hẹn) và `removeMessages` (huỷ), trên luồng daemon.
 *
 * Khoảng chờ ngắn (≤ 300 ms) + chốt bằng latch, không so thời gian tuyệt đối — bài phải xanh cả khi máy test chậm.
 */
class WriteReleaseSchedulerTest {

    @Test
    fun `hen thi chay dung mot lan, tren luong daemon`() {
        val s = WriteReleaseScheduler.Jvm(threadName = "test-release")
        val done = CountDownLatch(1)
        val daemon = AtomicInteger(-1)
        s.schedule("sunroof", 20) { daemon.set(if (Thread.currentThread().isDaemon) 1 else 0); done.countDown() }
        assertTrue(done.await(2, TimeUnit.SECONDS), "việc đã hẹn phải chạy")
        assertEquals(1, daemon.get(), "luồng daemon — không giữ tiến trình sống")
        assertFalse(s.cancel("sunroof"), "đã chạy xong thì không còn gì để huỷ")
    }

    @Test
    fun `huy truoc gio thi viec KHONG BAO GIO chay, va cancel noi that`() {
        val s = WriteReleaseScheduler.Jvm()
        val ran = AtomicInteger(0)
        s.schedule("sunroof", 200) { ran.incrementAndGet() }
        assertTrue(s.cancel("sunroof"), "huỷ được ⇒ true")
        assertFalse(s.cancel("sunroof"), "huỷ lần hai ⇒ false")
        Thread.sleep(400)
        assertEquals(0, ran.get())
    }

    @Test
    fun `hen lai cung khoa thi THAY viec cu - chi viec moi chay`() {
        val s = WriteReleaseScheduler.Jvm()
        val order = Collections.synchronizedList(mutableListOf<String>())
        val done = CountDownLatch(1)
        s.schedule("sunroof", 150) { order += "cu" }
        s.schedule("sunroof", 30) { order += "moi"; done.countDown() }
        assertTrue(done.await(2, TimeUnit.SECONDS))
        Thread.sleep(250)
        assertEquals(listOf("moi"), order.toList())
    }

    @Test
    fun `khoa khac nhau doc lap, viec nem khong lam chet luong`() {
        val errors = AtomicInteger(0)
        val s = WriteReleaseScheduler.Jvm(onError = { _, _ -> errors.incrementAndGet() })
        val done = CountDownLatch(2)
        s.schedule("a", 10) { throw IllegalStateException("hal") }
        s.schedule("b", 40) { done.countDown() }
        s.schedule("c", 60) { done.countDown() }
        assertTrue(done.await(2, TimeUnit.SECONDS), "việc sau một việc ném vẫn chạy")
        assertEquals(1, errors.get(), "lỗi được báo, không nuốt im")
    }
}
