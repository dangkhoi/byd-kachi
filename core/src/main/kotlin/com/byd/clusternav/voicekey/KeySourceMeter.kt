package com.byd.clusternav.voicekey

import com.byd.clusternav.launcher.HalGateway
import java.util.concurrent.Callable
import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * ═══ L7 · KEY-SOURCE-SPLIT tầng 1 — PHẦN ĐO của một lần bấm, THUẦN (2.87 · SOÁT vòng 1 · P2 + P3) ══════════════════════
 *
 * Trước bản này phần đo nằm trong `KeySourceRecorder` (`:app`) và chỉ được khoá bằng grep mã nguồn: dời `inFlight = f`
 * xuống dưới `f.get(…)` hay xoá `inFlight` khi quá hạn thì bài canh vẫn xanh, mà một HAL treo sẽ bị xếp chồng thêm một
 * lượt đọc MỖI lần bấm (núm xoay bắn DOWN cách nhau ~3 ms — fixture `keysrc-0917`) — đúng cơn bão spec cấm. Nay luật
 * chạy được off-device với bộ thi hành THẬT + gateway chặn bằng chốt (`KeySourceMeterTest`).
 *
 * ## Luật
 *  1. **Đọc HAL TRƯỚC, tra thiết bị SAU** (P3): gửi lượt đọc lên luồng HAL rồi mới tra `InputDevice` (lần đầu mỗi id có
 *     thể là một lượt binder) — tra trước là cộng thời gian tra vào `ageMs` và nới cửa sổ cho một phím KHÁC ghi đè
 *     `AUDIO_VOLUME_CTRL_MODE` trước khi đọc (tầng 2 · §4.5.4 dựa đúng cửa sổ ấy).
 *  2. **Trần tính từ lúc GỬI**: chờ phần còn lại `budgetMs − (đã trôi)`, không cộng thêm một trần nữa sau lượt tra.
 *  3. **Lượt trước còn treo ⇒ `BUSY` ngay, KHÔNG gọi gateway** (không chồng lượt, không bão thử lại); `inFlight` chỉ
 *     được thay khi lượt mới THẬT SỰ được gửi — quá hạn KHÔNG xoá nó, nên lần bấm sau vẫn thấy lượt treo.
 *  4. `BUSY`/`NOT_RUNNING` không đọc gì ⇒ `readMs = -1` (P3: hộp học phím từng hiện *"đọc 0 ms"* cho một lượt không có).
 *
 * ## Luồng
 * [measure] / [read] / [prime] của MỘT thực thể chỉ được gọi từ MỘT luồng (thực thể của nhật ký: luồng đo `kachi-keysrc`
 * của `KeySourceRecorder`; thực thể của [KeySourceResolver]: luồng nhận phím) — phép kiểm-rồi-gán `inFlight` vì thế
 * không cần khoá. Lượt đọc (`gateway()` + [KeySourceProbes.read]) chạy trên luồng của [exec]; không ngắt được một
 * lời gọi binder treo, nên quá hạn chỉ là BỎ CHỜ.
 *
 * @param exec bộ thi hành HAL (một luồng) — `null` = bộ đo đã dừng ⇒ `NOT_RUNNING`.
 * @param clockMs cùng gốc với [KeySample.eventTime] (`SystemClock.uptimeMillis` ở `:app`).
 * @param probes bảng đầu dò — mặc định [KeySourceProbes.ALL] (cùng tham số của [KeySourceProbes.forKey]).
 */
class KeySourceMeter(
    private val gateway: () -> HalGateway,
    private val exec: () -> ExecutorService?,
    private val clockMs: () -> Long,
    private val probes: List<KeySourceProbeSpec> = KeySourceProbes.ALL,
) {
    @Volatile private var inFlight: Future<KeySourceReading>? = null

    /** Kết quả một lần đo: thiết bị (có thể `null` — tra hỏng/không có) + số đọc nguồn. */
    data class Measurement(val device: KeyDeviceInfo?, val reading: KeySourceReading)

    /**
     * Đo một lần bấm theo luật ở KDoc lớp. Không ném vì lỗi HAL (mọi lỗi đọc thành [KeySourceFailure]).
     *
     * @param preRead số đọc nguồn ĐÃ CÓ cho đúng lần bấm này (2.88 · R-nf2: đường gán đã đọc đồng bộ qua
     *   [KeySourceResolver]) ⇒ KHÔNG đọc HAL lần hai, chỉ tra thiết bị. `null` ⇒ đo đủ như 2.87.
     */
    fun measure(sample: KeySample, devices: KeyDeviceCache, preRead: KeySourceReading? = null): Measurement {
        if (preRead != null) return Measurement(devices.get(sample.deviceId), preRead)
        val spec = KeySourceProbes.forKey(sample.keyCode, probes)
        val started = spec?.let { start(it, sample.eventTime) }      // 1. HAL đi trước
        val device = devices.get(sample.deviceId)                      //    tra thiết bị trong lúc HAL chạy
        val reading = started?.let { await(it) } ?: KeySourceReading.NOT_MEASURED
        return Measurement(device, reading)
    }

    /**
     * CHỈ phần HAL (không tra thiết bị) — đường gán đọc nguồn đồng bộ ([KeySourceResolver], 2.88). Cùng luật 2–4 của
     * KDoc lớp; phím ngoài bảng ⇒ [KeySourceReading.NOT_MEASURED], không tốn lượt HAL nào.
     */
    fun read(sample: KeySample): KeySourceReading =
        KeySourceProbes.forKey(sample.keyCode, probes)?.let { await(start(it, sample.eventTime)) } ?: KeySourceReading.NOT_MEASURED

    /**
     * Gửi MỘT lượt đọc mồi của đầu dò đầu bảng rồi trả về NGAY (không chờ) — nạp sẵn bảng feature-id + `getInstance`
     * của device để lần bấm thật đầu tiên không trả giá khởi tạo (2.88 · spec §4.3). Lượt mồi là một lượt như mọi lượt:
     * còn treo thì lần bấm kế ra `BUSY` ngay (luật 3) chứ không xếp hàng sau nó. `true` ⇔ đã gửi được.
     */
    fun prime(): Boolean {
        val spec = probes.firstOrNull() ?: return false
        return start(spec, clockMs()).future != null
    }

    /** Lượt đọc đã gửi ([future] đang chạy) hoặc đã có kết quả ngay ([immediate]: `BUSY` / `NOT_RUNNING`). */
    private class Started(
        val spec: KeySourceProbeSpec,
        val future: Future<KeySourceReading>? = null,
        val sentAt: Long = 0,
        val immediate: KeySourceReading? = null,
    )

    private fun start(spec: KeySourceProbeSpec, keyEventTime: Long): Started {
        if (inFlight?.isDone == false) return Started(spec, immediate = KeySourceReading.failed(spec, KeySourceFailure.BUSY))
        val ex = exec() ?: return Started(spec, immediate = KeySourceReading.failed(spec, KeySourceFailure.NOT_RUNNING))
        val sentAt = clockMs()
        val f = try {
            ex.submit(Callable { KeySourceProbes.read(spec, gateway(), clockMs, keyEventTime) })
        } catch (e: RejectedExecutionException) {
            return Started(spec, immediate = KeySourceReading.failed(spec, KeySourceFailure.NOT_RUNNING))
        }
        inFlight = f
        return Started(spec, f, sentAt)
    }

    private fun await(s: Started): KeySourceReading {
        s.immediate?.let { return it }
        val f = s.future ?: return KeySourceReading.failed(s.spec, KeySourceFailure.NOT_RUNNING)
        val left = (s.spec.budgetMs - (clockMs() - s.sentAt)).coerceAtLeast(0)
        return try {
            f.get(left, TimeUnit.MILLISECONDS)
        } catch (e: TimeoutException) {
            KeySourceReading.failed(s.spec, KeySourceFailure.TIMEOUT, readMs = s.spec.budgetMs)
        } catch (e: ExecutionException) {
            KeySourceReading.failed(s.spec, KeySourceFailure.READ_ERROR, errorClass = (e.cause ?: e).javaClass.simpleName)
        } catch (e: CancellationException) {
            // Bộ đo dừng (`shutdownNow`) giữa chừng — không phải lỗi HAL.
            KeySourceReading.failed(s.spec, KeySourceFailure.NOT_RUNNING)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            KeySourceReading.failed(s.spec, KeySourceFailure.NOT_RUNNING)
        }
    }
}

/**
 * Nhớ `InputDevice` theo id (2.87 · SOÁT vòng 1 · P3). Tra lần đầu mỗi id có thể là một lượt binder
 * (`InputManager.getInputDevice` r47 :250-271) nên nhớ lại — **kể cả ca "không có thiết bị"** (trước bản này `null` không
 * được nhớ ⇒ một id lạ tốn một lượt tra MỖI lần bấm). Ném (system_server lỗi, dạng RuntimeException) = lỗi tạm thời ⇒
 * KHÔNG nhớ, lần sau tra lại. [forget] khi framework báo thiết bị thêm/đổi/gỡ (`InputDeviceListener` ở `:app`).
 */
class KeyDeviceCache(private val lookup: (Int) -> KeyDeviceInfo?) {
    private val known = ConcurrentHashMap<Int, KeyDeviceInfo>()
    private val missing: MutableSet<Int> = ConcurrentHashMap.newKeySet()

    fun get(id: Int): KeyDeviceInfo? {
        known[id]?.let { return it }
        if (id in missing) return null
        val d = try {
            lookup(id)
        } catch (e: RuntimeException) {
            return null   // tạm thời — dòng nhật ký vẫn ra (có deviceId), không bỏ cả lượt đo
        }
        if (d == null) missing += id else known[id] = d
        return d
    }

    /** Bỏ nhớ id [id] (cả ca đã nhớ "không có") — thiết bị vừa thêm/đổi/gỡ. */
    fun forget(id: Int) {
        known.remove(id)
        missing.remove(id)
    }
}
