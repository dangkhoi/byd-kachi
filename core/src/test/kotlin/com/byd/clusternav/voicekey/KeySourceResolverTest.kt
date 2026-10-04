package com.byd.clusternav.voicekey

import com.byd.clusternav.launcher.FakeHalGateway
import com.byd.clusternav.launcher.HalFeatureRead
import com.byd.clusternav.launcher.HalGateway
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * ═══ 2.88 · KEY-SOURCE-SPLIT tầng 2 — TRA NGUỒN ĐỒNG BỘ chạy THẬT (bộ thi hành thật, gateway chặn bằng chốt) ═══════════
 *
 * [KeySourceResolver] chạy trên LUỒNG NHẬN PHÍM (main, framework chờ tối đa 500 ms — `KeyEventDispatcher.java:51` r47).
 * Khoá HÀNH VI (spec kachi-288 R-nf1, T3):
 *  - đọc được ⇒ đúng nút theo bảng đầu dò (1 = núm, 2 = vô-lăng — số đo xe 04/10);
 *  - HAL treo ⇒ chờ đúng ~[KeySourceResolver.SYNC_BUDGET_MS] RỒI bỏ (`?(timeout)`), các lần sau `?(busy)` NGAY, không chặn,
 *    không chồng lượt; lượt treo xong ⇒ đọc lại bình thường;
 *  - mọi hụt (ném, giá trị lạ, bộ dừng, phím ngoài bảng) ⇒ KHÔNG BIẾT nguồn (`kind = null`) kèm lý do — không bao giờ ném;
 *  - đọc mồi chiếm luồng sync như một lượt thật (lần nhấn trong lúc mồi ra `busy` ngay, không xếp hàng sau nó).
 */
class KeySourceResolverTest {

    /** Gateway trả [value]; nếu [block] thì chặn trong `featureRead` tới khi [release] (trần an toàn 5 s). */
    private class Gate(private val value: String, private val block: Boolean = false) : HalGateway by FakeHalGateway() {
        val calls = AtomicInteger(0)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        override fun featureIdByName(constName: String): Int? = -1728053170
        override fun featureMapAvailable(): Boolean = true
        override fun featureRead(deviceFqn: String, id: Int): HalFeatureRead {
            calls.incrementAndGet()
            entered.countDown()
            if (block) release.await(5, TimeUnit.SECONDS)
            return HalFeatureRead.Value(value)
        }
    }

    private val pools = mutableListOf<ExecutorService>()

    private fun syncThread(): ExecutorService =
        Executors.newSingleThreadExecutor { r -> Thread(r, "test-keysrc-sync").apply { isDaemon = true } }.also { pools += it }

    @AfterEach
    fun after() = pools.forEach { it.shutdownNow() }

    private fun sample(code: Int = 291) = KeySample(
        keyCode = code, action = 0, downTime = 1_000, eventTime = System.currentTimeMillis(), scanCode = 115, deviceId = 8,
        source = 0x101, flags = 0x8, repeatCount = 0,
    )

    private fun resolver(gw: HalGateway, ex: ExecutorService?) = KeySourceResolver({ gw }, { ex }, System::currentTimeMillis)

    private fun msOf(block: () -> Unit): Long { val t0 = System.nanoTime(); block(); return (System.nanoTime() - t0) / 1_000_000 }

    @Test
    fun `doc duoc - 1 la num, 2 la vo-lang, kem so doc de nhat ky dung lai`() {
        val ex = syncThread()
        val knob = resolver(Gate("int=1 float=- buf=-"), ex).lookup(sample(292))
        assertEquals(KeySourceKind.CONSOLE_KNOB, knob.kind)
        assertEquals("knob", knob.reason)
        assertEquals(1, knob.reading?.value)
        assertTrue((knob.reading?.readMs ?: -1) >= 0, "có lượt đọc thật ⇒ readMs ≥ 0 (nhật ký tầng 1 in lại)")
        val wheel = resolver(Gate("int=2 float=- buf=-"), ex).lookup(sample(291))
        assertEquals(KeySourceKind.STEERING_WHEEL, wheel.kind)
        assertEquals("wheel", wheel.reason)
    }

    @Test
    fun `HAL treo - cho dung tran 100 ms roi bo, lan sau busy NGAY khong goi gateway, xong thi doc lai`() {
        assertEquals(100L, KeySourceResolver.SYNC_BUDGET_MS, "trần R-nf1: ≈30× mức đo 1–3 ms, ≪ 500 ms của framework")
        val gw = Gate("int=1 float=- buf=-", block = true)
        val ex = syncThread()
        val r = resolver(gw, ex)

        lateinit var first: KeySourceLookup
        val waited = msOf { first = r.lookup(sample()) }
        assertTrue(gw.entered.await(5, TimeUnit.SECONDS))
        assertNull(first.kind, "quá trần ⇒ KHÔNG BIẾT nguồn (không đoán)")
        assertEquals("?(timeout)", first.reason)
        assertTrue(waited in 80..400, "phải chờ ≈ trần 100 ms rồi bỏ — đã chờ $waited ms")

        repeat(3) {
            lateinit var busy: KeySourceLookup
            val t = msOf { busy = r.lookup(sample()) }
            assertEquals("?(busy)", busy.reason)
            assertTrue(t < 50, "lượt trước còn treo ⇒ busy NGAY, không chặn luồng phím ($t ms)")
        }
        assertEquals(1, gw.calls.get(), "không bao giờ chồng lượt đọc mới lên một HAL đang treo")

        gw.release.countDown()
        ex.submit {}.get(5, TimeUnit.SECONDS)
        assertEquals(KeySourceKind.CONSOLE_KNOB, r.lookup(sample()).kind, "lượt treo xong ⇒ đọc lại bình thường")
        assertEquals(2, gw.calls.get())
    }

    @Test
    fun `gateway nem thi khong biet nguon kem lop ngoai le`() {
        val r = KeySourceResolver({ throw SecurityException("no permission") }, { syncThread() }, System::currentTimeMillis)
        val l = r.lookup(sample())
        assertNull(l.kind)
        assertEquals("?(read_error:SecurityException)", l.reason)
    }

    /** Giá trị ngoài {1, 2} KHÔNG bị ép thành núm/vô-lăng (đời xe khác có thể khác nghĩa — [CHƯA BIẾT]). */
    @Test
    fun `gia tri la thi khong biet nguon, giu nguyen so`() {
        val l = resolver(Gate("int=7 float=- buf=-"), syncThread()).lookup(sample())
        assertNull(l.kind)
        assertEquals("?(value=7)", l.reason)
        assertEquals(7, l.reading?.value)
    }

    @Test
    fun `bo dung hoac da shutdown thi not_running, khong goi gateway`() {
        val gw = Gate("int=1 float=- buf=-")
        assertEquals("?(not_running)", resolver(gw, null).lookup(sample()).reason)
        val dead = syncThread().also { it.shutdown() }
        assertEquals("?(not_running)", resolver(gw, dead).lookup(sample()).reason)
        assertEquals(0, gw.calls.get())
    }

    /** Phím ngoài bảng đầu dò ⇒ không tốn lượt HAL nào (lọc thứ hai sau `needsSource`). */
    @Test
    fun `phim ngoai bang dau do khong ton luot HAL`() {
        val gw = Gate("int=1 float=- buf=-")
        val l = resolver(gw, syncThread()).lookup(sample(328))
        assertNull(l.kind)
        assertEquals("?(not_measured)", l.reason)
        assertSame(KeySourceReading.NOT_MEASURED, l.reading)
        assertEquals(0, gw.calls.get())
    }

    /** Trần đồng bộ là BẢN SAO đầu dò — bảng gốc giữ trần 250 ms của nhật ký tầng 1. */
    @Test
    fun `tran dong bo khong sua bang dau do goc`() {
        assertEquals(KeySourceProbes.DEFAULT_BUDGET_MS, KeySourceProbes.AUDIO_VOLUME_CTRL_MODE.budgetMs)
        val l = resolver(Gate("int=1 float=- buf=-"), syncThread()).lookup(sample())
        assertEquals(KeySourceResolver.SYNC_BUDGET_MS, l.reading?.probe?.budgetMs)
        assertEquals(KeySourceProbes.AUDIO_VOLUME_CTRL_MODE.featureName, l.reading?.probe?.featureName)
    }

    /**
     * Đọc mồi: gửi rồi trả về NGAY (main không chờ); trong lúc mồi còn chạy, lần nhấn ra `busy` ngay thay vì xếp hàng
     * sau nó; mồi xong ⇒ đọc bình thường. Bộ dừng ⇒ không gửi được.
     */
    @Test
    fun `doc moi khong chan, lan nhan trong luc moi ra busy ngay`() {
        val gw = Gate("int=2 float=- buf=-", block = true)
        val ex = syncThread()
        val r = resolver(gw, ex)
        var sent = false
        assertTrue(msOf { sent = r.prime() } < 50, "mồi chỉ gửi việc, không chờ HAL")
        assertTrue(sent)
        assertTrue(gw.entered.await(5, TimeUnit.SECONDS))
        assertEquals("?(busy)", r.lookup(sample()).reason)
        gw.release.countDown()
        ex.submit {}.get(5, TimeUnit.SECONDS)
        assertEquals(KeySourceKind.STEERING_WHEEL, r.lookup(sample()).kind)
        assertEquals(2, gw.calls.get())
        assertFalse(resolver(gw, null).prime(), "bộ dừng ⇒ không gửi được")
    }
}
