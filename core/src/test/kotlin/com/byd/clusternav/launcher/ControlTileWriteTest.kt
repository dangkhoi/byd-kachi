package com.byd.clusternav.launcher

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ P1-main (2026-09-25 · spec `kachi-closeout-hardening` R4(b), audit F10) — ô ĐƠN ghi HAL ngoài luồng chính ═══
 *
 * Bệnh bài này khoá: tới bản trước, `ControlTileFactory` gọi `control().toggle/step/…` **ngay trong
 * `setOnClickListener`** ⇒ binder HAL (≈23 ms/lượt [ĐO xe 09-16], STEP còn đọc trước khi ghi) chạy trên luồng vẽ.
 * Bốn tính chất phải giữ khi đưa xuống nền, mỗi cái một bài:
 *  1. cú ghi **không chạy trên luồng gọi** (giả làm "main");
 *  2. **thứ tự** nộp = thứ tự ghi;
 *  3. `false`/ném ⇒ **một dòng W** + hoàn nguyên — không nuốt im;
 *  4. cú bấm SAU đã đè giá trị lạc quan ⇒ lượt trước hỏng **không** kéo ngược (compare-and-revert).
 * Và bài cuối đo đường mặc định thật: làn [ControlTileWrite.LANE] của [MacroExec] — daemon, mang tên làn.
 */
class ControlTileWriteTest {

    private val warns = mutableListOf<Pair<String, Throwable?>>()

    /** Bản nền GIẢ nhưng ĐỒNG BỘ: chạy thân trên luồng riêng tên "bg" rồi chờ xong — luồng gọi không bao giờ chạy thân. */
    private val syncBg: (() -> Unit) -> Unit = { body -> Thread(body, "bg").also { it.start() }.join() }

    private fun writer() = ControlTileWrite(bg = syncBg, warn = { m, t -> warns += m to t })

    @Test
    fun `cu ghi KHONG chay tren luong goi`() {
        val caller = Thread.currentThread()
        val ranOn = AtomicReference<Thread?>(null)
        writer().submit("ac", act = { ranOn.set(Thread.currentThread()); true }) { error("không được hoàn nguyên khi ghi ăn") }
        val t = requireNotNull(ranOn.get()) { "cú ghi phải được chạy, không bị nuốt" }
        assertTrue(t !== caller, "ghi HAL vẫn chạy trên luồng gọi (= luồng vẽ) — đúng bệnh F10")
        assertEquals("bg", t.name)
    }

    @Test
    fun `thu tu nop = thu tu ghi`() {
        val order = mutableListOf<String>()
        val w = writer()
        listOf("a", "b", "c", "d", "e").forEach { id -> w.submit(id, act = { order += id; true }) {} }
        assertEquals(listOf("a", "b", "c", "d", "e"), order)
    }

    @Test
    fun `ghi tra false thi mot dong W va hoan nguyen`() {
        val reverted = AtomicBoolean(false)
        writer().submit("ac", act = { false }) { reverted.set(true) }
        assertTrue(reverted.get(), "ghi trả false phải hoàn nguyên ô — không thì ô nói 'bật' mà xe không nhận")
        assertEquals(1, warns.size, "đúng MỘT dòng W, không im, không lặp")
        assertTrue(warns[0].first.contains("ac"), "dòng W phải nêu đích danh nút: ${warns[0].first}")
        assertNull(warns[0].second, "trả false không có ngoại lệ kèm")
    }

    @Test
    fun `ghi nem thi W kem ngoai le, hoan nguyen, va KHONG lan ra ngoai`() {
        val reverted = AtomicBoolean(false)
        val boom = IllegalStateException("binder chết")
        writer().submit("ac", act = { throw boom }) { reverted.set(true) }   // ném ra là bài đỏ ở đây
        assertTrue(reverted.get())
        assertEquals(1, warns.size)
        assertTrue(warns[0].second === boom, "ngoại lệ phải đi kèm dòng W để nhật ký có stack")
    }

    @Test
    fun `ghi an thi im lang, khong hoan nguyen`() {
        val reverted = AtomicBoolean(false)
        writer().submit("ac", act = { true }) { reverted.set(true) }
        assertFalse(reverted.get())
        assertTrue(warns.isEmpty(), "thành công thì không có gì để nói (không ai muốn log mỗi cú bấm đúng)")
    }

    @Test
    fun `cu bam sau da de thi luot truoc hong KHONG keo nguoc`() {
        val reverted = AtomicBoolean(false)
        writer().submit("ac", act = { false }, stillMine = { false }) { reverted.set(true) }
        assertFalse(reverted.get(), "giá trị lạc quan đã thuộc cú bấm sau — hoàn nguyên là xoá cú bấm của người dùng")
        assertEquals(1, warns.size, "nhưng lỗi vẫn phải lên nhật ký")
    }

    @Test
    fun `duong mac dinh la lan tuan tu cua MacroExec — daemon, mang ten lan`() {
        val done = CountDownLatch(1)
        val ranOn = AtomicReference<Thread?>(null)
        val name = AtomicReference("")   // đọc TRONG lượt chạy: làn trả tên luồng lại ngay sau khi thân xong
        ControlTileWrite(warn = { _, _ -> }).submit("ac", act = {
            ranOn.set(Thread.currentThread()); name.set(Thread.currentThread().name); done.countDown(); true
        }) {}
        assertTrue(done.await(5, TimeUnit.SECONDS), "cú ghi phải được chạy trên nền thật")
        val t = requireNotNull(ranOn.get())
        assertTrue(t !== Thread.currentThread())
        assertTrue(t.isDaemon, "luồng ghi ô đơn phải là daemon như luồng gói lệnh")
        assertEquals(ControlTileWrite.LANE, name.get(), "tên luồng lúc chạy = tên làn, để dumpsys đọc ra 'ô đơn đang ghi'")
    }

    /** Off-car / nút xe không có: `false` = "không biết" ⇒ cảnh báo nhưng KHÔNG kéo ô về (review Pass 1 [P2]). */
    @Test
    fun `khong biet xe co nut hay khong thi khong hoan nguyen nhung van canh bao`() {
        val warns = mutableListOf<String>()
        val w = ControlTileWrite(bg = { it() }, warn = { m, _ -> warns += m })
        var reverted = false
        w.submit("fan", act = { false }, failureIsReal = { false }) { reverted = true }
        assertFalse(reverted)
        assertEquals(2, warns.size)
        assertTrue(warns[1].contains("không hoàn nguyên"))
        // xe thật từ chối ⇒ hoàn nguyên như cũ
        w.submit("fan", act = { false }, failureIsReal = { true }) { reverted = true }
        assertTrue(reverted)
    }

    /**
     * [SOÁT Pass 2 · 2026-09-26] Câu hỏi *"cú `false` này có thật không"* đi qua reflection HAL ⇒ nó NÉM được. Ném =
     * **không biết**, và không biết thì KHÔNG hoàn nguyên (trước Pass 2, một ngoại lệ ở đây bị coi là "xe có nút").
     */
    @Test
    fun `cau hoi failureIsReal nem thi coi la khong biet — khong hoan nguyen`() {
        val warns = mutableListOf<String>()
        var reverted = false
        ControlTileWrite(bg = { it() }, warn = { m, _ -> warns += m })
            .submit("fan", act = { false }, failureIsReal = { error("binder chết giữa lúc hỏi") }) { reverted = true }
        assertFalse(reverted, "một ngoại lệ không phải bằng chứng xe đã từ chối")
        assertEquals(2, warns.size, "vẫn phải nói: cú ghi trả false + lý do không hoàn nguyên")
    }
}
