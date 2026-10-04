package com.byd.clusternav.voicekey

import com.byd.clusternav.launcher.FakeHalGateway
import com.byd.clusternav.launcher.HalFeatureRead
import com.byd.clusternav.launcher.HalGateway
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * ═══ 2.87 · SOÁT vòng 1 · P2 + P3 — bộ đo nguồn phím CHẠY THẬT (bộ thi hành thật, gateway chặn bằng chốt) ═══════════════
 *
 * Trước bản này luật busy/trần/quá hạn chỉ được khoá bằng grep mã `KeySourceRecorder` (`:app`): dời `inFlight = f` xuống
 * sau `f.get(…)` hay xoá nó khi quá hạn thì bài canh vẫn xanh, còn trên xe một HAL treo bị xếp chồng thêm một lượt đọc MỖI
 * lần bấm (núm xoay ~3 ms/DOWN, fixture `keysrc-0917`). Ở đây khoá HÀNH VI:
 *  - P2: quá hạn → `timeout`; bấm tiếp khi lượt cũ còn treo → `busy` NGAY, gateway KHÔNG bị gọi thêm; lượt treo xong →
 *    đọc lại bình thường; bộ đo dừng (`null` / đã `shutdown`) → `not_running`; gateway ném → `read_error:<lớp>`.
 *  - P3: lượt không đọc (`busy`) có `readMs = -1`; tra thiết bị SAU khi HAL đã chạy; trần tính từ lúc GỬI; thiết bị "không
 *    có" được nhớ, ném thì không nhớ, `forget` tra lại.
 */
class KeySourceMeterTest {

    private val spec = KeySourceProbes.AUDIO_VOLUME_CTRL_MODE

    /** Gateway chặn trong `featureRead` tới khi [release] (trần an toàn 5 s để bài hỏng không treo cả bộ test). */
    private class Gate(private val value: String = "int=2 float=- buf=-") : HalGateway by FakeHalGateway() {
        val calls = AtomicInteger(0)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        override fun featureIdByName(constName: String): Int? = -1728053170
        override fun featureMapAvailable(): Boolean = true
        override fun featureRead(deviceFqn: String, id: Int): HalFeatureRead {
            calls.incrementAndGet()
            entered.countDown()
            release.await(5, TimeUnit.SECONDS)
            return HalFeatureRead.Value(value)
        }
    }

    private val pools = mutableListOf<ExecutorService>()

    private fun halThread(): ExecutorService =
        Executors.newSingleThreadExecutor { r -> Thread(r, "test-keysrc-hal").apply { isDaemon = true } }.also { pools += it }

    @AfterEach
    fun after() = pools.forEach { it.shutdownNow() }

    private fun sample(code: Int = 291) = KeySample(
        keyCode = code, action = 0, downTime = 1_000, eventTime = 1_000, scanCode = 115, deviceId = 7,
        source = 0x101, flags = 0x8, repeatCount = 0,
    )

    private val noDevices = KeyDeviceCache { null }

    /** Chờ mọi việc trước đó trên luồng HAL một-luồng xong (FIFO) — tức lượt treo đã trả về. */
    private fun drain(ex: ExecutorService) = ex.submit {}.get(5, TimeUnit.SECONDS)

    @Test
    fun `HAL treo - qua han ra timeout, bam tiep ra busy KHONG goi gateway, xong thi doc lai binh thuong`() {
        val gw = Gate()
        val ex = halThread()
        val fast = spec.copy(budgetMs = 60)
        val meter = KeySourceMeter({ gw }, { ex }, System::currentTimeMillis, probes = listOf(fast))

        val first = meter.measure(sample(), noDevices).reading
        assertTrue(gw.entered.await(5, TimeUnit.SECONDS), "lượt đọc đầu phải tới gateway")
        assertEquals(KeySourceFailure.TIMEOUT, first.failure)
        assertEquals(fast.budgetMs, first.readMs, "quá hạn: thời lượng = trần đã chờ")

        repeat(3) {
            val busy = meter.measure(sample(), noDevices).reading
            assertEquals(KeySourceFailure.BUSY, busy.failure, "lượt trước còn treo ⇒ busy NGAY")
            assertEquals(-1, busy.readMs, "P3: busy không đọc gì ⇒ readMs = -1 (không phải 'đọc 0 ms')")
        }
        assertEquals(1, gw.calls.get(), "không bao giờ chồng lượt đọc mới lên một HAL đang treo")

        gw.release.countDown()
        drain(ex)
        val ok = meter.measure(sample(), noDevices).reading
        assertNull(ok.failure, "lượt treo đã xong ⇒ đọc lại bình thường")
        assertEquals(2, ok.value)
        assertTrue(ok.readMs >= 0)
        assertEquals(2, gw.calls.get())
    }

    @Test
    fun `bo do dung - khong co luong HAL hoac da shutdown ra not_running, khong goi gateway`() {
        val gw = Gate().also { it.release.countDown() }
        val stopped = KeySourceMeter({ gw }, { null }, System::currentTimeMillis).measure(sample(), noDevices).reading
        assertEquals(KeySourceFailure.NOT_RUNNING, stopped.failure)
        assertEquals(-1, stopped.readMs)
        val ex = halThread().also { it.shutdown() }
        val rejected = KeySourceMeter({ gw }, { ex }, System::currentTimeMillis).measure(sample(), noDevices).reading
        assertEquals(KeySourceFailure.NOT_RUNNING, rejected.failure, "bộ thi hành đã dừng ⇒ RejectedExecution ⇒ not_running")
        assertEquals(0, gw.calls.get())
    }

    @Test
    fun `gateway nem ra read_error kem lop ngoai le, phim ngoai bang khong ton luot HAL`() {
        val ex = halThread()
        val boom = KeySourceMeter({ throw SecurityException("no permission") }, { ex }, System::currentTimeMillis)
        val r = boom.measure(sample(), noDevices).reading
        assertEquals(KeySourceFailure.READ_ERROR, r.failure)
        assertEquals("SecurityException", r.errorClass)
        val gw = Gate().also { it.release.countDown() }
        val other = KeySourceMeter({ gw }, { ex }, System::currentTimeMillis).measure(sample(code = 328), noDevices)
        assertSame(KeySourceReading.NOT_MEASURED, other.reading)
        assertEquals(0, gw.calls.get())
    }

    /** P3 — HAL đi trước, tra thiết bị trong lúc HAL chạy; trần tính từ lúc GỬI (không cộng thêm một trần sau lượt tra). */
    @Test
    fun `doc HAL truoc roi moi tra thiet bi, tran tinh tu luc gui`() {
        val gw = Gate()
        val ex = halThread()
        val slow = spec.copy(budgetMs = 2_000)
        val clock = AtomicLong(50_000)
        var halStartedBeforeLookup = false
        val devices = KeyDeviceCache { id ->
            // Tra thiết bị chỉ được chạy khi lượt đọc HAL ĐÃ tới gateway (đọc trước); rồi giả lượt tra tốn quá trần.
            halStartedBeforeLookup = gw.entered.await(1, TimeUnit.SECONDS)
            clock.addAndGet(5_000)
            KeyDeviceInfo("simulate-keys#$id", "d", isVirtual = true, vendorId = 0, productId = 0)
        }
        val meter = KeySourceMeter({ gw }, { ex }, clock::get, probes = listOf(slow))
        val t0 = System.nanoTime()
        val m = meter.measure(sample(), devices)
        val waitedMs = (System.nanoTime() - t0) / 1_000_000
        gw.release.countDown()
        assertTrue(halStartedBeforeLookup, "P3: lượt đọc HAL phải được GỬI trước khi tra InputDevice")
        assertEquals("simulate-keys#7", m.device?.name)
        assertEquals(KeySourceFailure.TIMEOUT, m.reading.failure, "lượt tra đã ăn hết trần ⇒ không chờ thêm")
        assertTrue(waitedMs < 1_000, "trần tính từ lúc gửi: không chờ thêm ${slow.budgetMs} ms sau lượt tra (chờ $waitedMs ms)")
    }

    @Test
    fun `bo nho thiet bi - nho ca ca khong co, nem thi khong nho, forget thi tra lai`() {
        val lookups = AtomicInteger(0)
        val cache = KeyDeviceCache { id ->
            lookups.incrementAndGet()
            when (id) {
                7 -> null
                8 -> throw IllegalStateException("system_server")
                else -> KeyDeviceInfo("dev$id", "d$id", isVirtual = false, vendorId = 1, productId = 2)
            }
        }
        assertNull(cache.get(7)); assertNull(cache.get(7))
        assertEquals(1, lookups.get(), "id 'không có' được nhớ — không tra lại mỗi lần bấm")
        cache.forget(7)
        assertNull(cache.get(7))
        assertEquals(2, lookups.get(), "thiết bị thêm/đổi ⇒ forget ⇒ tra lại")
        assertNull(cache.get(8)); assertNull(cache.get(8))
        assertEquals(4, lookups.get(), "ném = lỗi tạm thời ⇒ KHÔNG nhớ")
        assertEquals("dev9", cache.get(9)?.name); cache.get(9)
        assertEquals(5, lookups.get())
    }

    /**
     * 2.88 · R-nf2 — đường gán ĐÃ đọc nguồn đồng bộ cho lần nhấn này ⇒ nhật ký tầng 1 dùng lại số đọc đó: gateway KHÔNG
     * bị gọi lần hai (một lượt HAL mỗi lần nhấn), thiết bị vẫn được tra cho dòng `KachiKey`.
     */
    @Test
    fun `co so doc san thi khong doc HAL lan hai, van tra thiet bi`() {
        val gw = Gate().also { it.release.countDown() }
        val ex = halThread()
        val pre = KeySourceReading(spec, value = 1, readMs = 2, ageMs = 2)
        val devices = KeyDeviceCache { id -> KeyDeviceInfo("simulate-keys", "d$id", isVirtual = true, vendorId = 0, productId = 0) }
        val m = KeySourceMeter({ gw }, { ex }, System::currentTimeMillis).measure(sample(), devices, preRead = pre)
        assertSame(pre, m.reading)
        assertEquals("simulate-keys", m.device?.name)
        assertEquals(0, gw.calls.get(), "R-nf2: một lượt đọc HAL mỗi lần nhấn")
    }
}
