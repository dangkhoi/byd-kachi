package com.byd.clusternav.launcher.voice

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * FIX286 · VK3 (S1) — khoá hồi quy **"huỷ trong lúc nạp → bấm lại"** bằng luồng THẬT, builder giả chặn bằng latch.
 *
 * Kịch bản gốc ([ĐO mã 02/10], phản biện R-VK B1): phiên 1 đang nạp (giữ khoá dựng 9–34 s) → người lái huỷ → nhịp
 * đứng xuống trên luồng chính gọi nhả → 2.85 CHỜ khoá ⇒ luồng chính `:wake` treo; bấm lại ⇒ không gì hiện, rồi nạp
 * lại từ đầu. Ở đây: nhả trong lúc nạp trả [ModelHolder.Release.BUSY] NGAY (không chặn), lượt lấy thứ hai nhận ĐÚNG
 * bản lượt một vừa dựng, builder chạy MỘT lần.
 */
@Timeout(10)
class ModelHolderTest {

    private class Model(val id: Int) {
        @Volatile var closed = false
    }

    private fun holder(closes: AtomicInteger = AtomicInteger()) =
        ModelHolder<String, Model>(close = { it.closed = true; closes.incrementAndGet() })

    @Test
    fun `huy trong luc nap roi bam lai - nha tra BUSY ngay, lan lay 2 nhan dung ban vua dung, builder chay 1 lan`() {
        val h = holder()
        val entered = CountDownLatch(1)
        val gate = CountDownLatch(1)
        val built = AtomicInteger()
        val builder = {
            built.incrementAndGet(); entered.countDown(); gate.await(5, TimeUnit.SECONDS); Model(built.get())
        }
        // Phiên 1: đang nạp trên luồng nền.
        val first = AtomicReference<Model?>()
        val t1 = Thread { first.set(h.get("int8", builder)) }.apply { start() }
        assertTrue(entered.await(2, TimeUnit.SECONDS))
        assertTrue(h.loading(), "đang dựng ⇒ loading() đọc từ khoá phải là true")

        // Người lái huỷ ⇒ nhịp đứng xuống (luồng "chính" = luồng test) thử nhả: KHÔNG được chặn.
        val t0 = System.nanoTime()
        assertEquals(ModelHolder.Release.BUSY, h.tryRelease())
        assertTrue((System.nanoTime() - t0) / 1_000_000 < 500, "tryRelease phải trả ngay, không chờ lượt nạp")

        // Bấm lại: phiên 2 xin mô hình trên luồng nền của nó ⇒ chờ lượt 1, không dựng lần hai.
        val second = AtomicReference<Model?>()
        val t2 = Thread { second.set(h.get("int8", builder)) }.apply { start() }
        gate.countDown()
        t1.join(); t2.join()
        assertSame(first.get(), second.get(), "lượt 2 phải nhận ĐÚNG bản lượt 1 vừa dựng")
        assertEquals(1, built.get(), "builder chỉ được chạy MỘT lần")
        assertEquals(1, h.builds())
        assertFalse(h.loading())
        assertFalse(first.get()!!.closed, "bản vừa nạp không được bị nhả dưới chân phiên 2")
    }

    @Test
    fun `nap xong roi moi nha - precheck dung thi RELEASED, close chay mot lan, lay lai thi dung moi`() {
        val closes = AtomicInteger()
        val h = holder(closes)
        val m = h.get("int8") { Model(1) }!!
        assertEquals(ModelHolder.Release.RELEASED, h.tryRelease { true })
        assertTrue(m.closed)
        assertEquals(1, closes.get())
        assertNull(h.current())
        assertEquals(ModelHolder.Release.EMPTY, h.tryRelease())
        val m2 = h.get("int8") { Model(2) }!!
        assertEquals(2, m2.id)
        assertEquals(2, h.builds())
    }

    @Test
    fun `precheck sai thi SKIPPED - pha hoac epoch da doi trong luc quyet, khong nha`() {
        val h = holder()
        val m = h.get("int8") { Model(1) }!!
        assertEquals(ModelHolder.Release.SKIPPED, h.tryRelease { false })
        assertSame(m, h.current())
        assertFalse(m.closed)
    }

    @Test
    fun `precheck chay KHI DA GIU khoa dung - nha giua chung khong lot qua`() {
        val h = holder()
        h.get("int8") { Model(1) }
        var heldDuringPrecheck = false
        h.tryRelease { heldDuringPrecheck = h.loading(); true }
        assertTrue(heldDuringPrecheck, "precheck phải chạy dưới khoá dựng — không thì một lượt lấy chen vào giữa")
    }

    @Test
    fun `dang co luot giai ma thi tryRelease BUSY, giai ma xong moi nha duoc - khong use-after-free`() {
        val h = holder()
        val m = h.get("int8") { Model(1) }!!
        val decoding = CountDownLatch(1)
        val done = CountDownLatch(1)
        val t = Thread { h.withUse(m) { decoding.countDown(); done.await(5, TimeUnit.SECONDS); "x" } }.apply { start() }
        assertTrue(decoding.await(2, TimeUnit.SECONDS))
        assertEquals(ModelHolder.Release.BUSY, h.tryRelease(), "giải mã đang giữ khoá đọc ⇒ không nhả dưới chân nó")
        assertFalse(m.closed)
        done.countDown(); t.join()
        assertEquals(ModelHolder.Release.RELEASED, h.tryRelease())
        assertNull(h.withUse(m) { "không được chạy" }, "bản đã nhả ⇒ withUse không chạm")
    }

    @Test
    fun `release co cho - doi khoa thi nha ban cu, builder null thi khong nho gi`() {
        val closes = AtomicInteger()
        val h = holder(closes)
        val a = h.get("fp32") { Model(1) }!!
        val b = h.get("int8") { Model(2) }!!
        assertTrue(a.closed, "đổi gói ⇒ bản cũ phải được nhả")
        assertEquals("int8", h.currentKey())
        h.release()
        assertTrue(b.closed)
        assertNull(h.get("int8") { null })
        assertNull(h.current())
        assertEquals(2, closes.get())
    }

    @Test
    fun `lastBuildMs do bang dong ho tiem vao`() {
        var now = 0L
        val h = ModelHolder<String, Model>(close = {}, nanoTime = { now })
        assertEquals(-1L, h.lastBuildMs())
        h.get("int8") { now += 21_345_000_000L; Model(1) }
        assertEquals(21_345L, h.lastBuildMs())
    }

    @Test
    fun `close nem van bo tham chieu - ban hong khong duoc dung lai`() {
        val h = ModelHolder<String, Model>(close = { error("native hỏng") })
        h.get("int8") { Model(1) }
        runCatching { h.tryRelease() }
        assertNull(h.current())
        assertFalse(h.loading(), "khoá phải được trả dù close ném")
    }
}
