package com.byd.clusternav.modules.voicekey

import android.content.Context
import android.hardware.input.InputManager
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import com.byd.clusternav.launcher.HalGateway
import com.byd.clusternav.voicekey.KeyDeviceInfo
import com.byd.clusternav.voicekey.KeySample
import com.byd.clusternav.voicekey.KeySourceFailure
import com.byd.clusternav.voicekey.KeySourceJournal
import com.byd.clusternav.voicekey.KeySourceLog
import com.byd.clusternav.voicekey.KeySourceProbeSpec
import com.byd.clusternav.voicekey.KeySourceProbes
import com.byd.clusternav.voicekey.KeySourceReading
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * ═══ L7 · KEY-SOURCE-SPLIT tầng 1 — BỘ ĐO nguồn phím (chỉ ĐO, không đổi cách gán/khớp) ═══════════════════════════
 *
 * Owner 03/10: núm xoay yên ngựa và nút âm lượng vô-lăng cùng ra mã 291/292. Bộ này ghi, cho MỖI lần bấm DOWN, chữ ký
 * đầy đủ của phím + (với phím thuộc bảng [KeySourceProbes]) giá trị feature HAL đánh dấu nguồn, rồi:
 *  - một dòng `KachiKey` vào logcat ⇒ `usage-*.log` (KachiLog chụp theo pid — CLAUDE.md §11, anh em không cần adb);
 *  - một dòng vào vòng đệm RAM [journal] ⇒ hộp "Học phím mới" hiện chi tiết để chụp màn hình.
 *
 * ## Luồng — vì sao ba tầng
 *  1. **Luồng nhận phím** ([onDown], [sampleOf]): [ĐO AOSP r47 `AccessibilityService.java:1622,1729,1873-1890`]
 *     `onKeyEvent` chạy trên MAIN looper của app (cũng là luồng vẽ launcher) và framework chờ kết quả tối đa 500 ms
 *     (`KeyEventDispatcher.java:51,153`) — quá hạn là phím lọt sang hệ thống. Ở đây chỉ chép field nguyên thuỷ +
 *     một lượt `post`: không binder, không I/O, không reflection.
 *  2. **Luồng đo `kachi-keysrc`** ([settle]): tra `InputDevice` (lần đầu mỗi id có thể là binder —
 *     `InputManager.getInputDevice` r47 :250-271; nhớ theo id, bỏ nhớ khi [deviceListener] báo đổi/gỡ), đợi lượt đọc
 *     HAL tối đa [KeySourceProbeSpec.budgetMs], ghi nhật ký.
 *  3. **Luồng HAL `kachi-keysrc-hal`** ([halExec]): lượt đọc reflection/binder THẬT. Không ngắt được một lời gọi binder
 *     treo, nên quá hạn thì luồng đo bỏ chờ (ghi `timeout`) và các lần bấm sau ghi `busy` NGAY cho tới khi lượt treo
 *     tự xong — không bao giờ xếp chồng lượt đọc mới lên một HAL đang treo (không bão thử lại).
 *
 * ## Phạm vi lệnh (CLAUDE.md §4/§5)
 * Chỉ ĐỌC: `BYDAutoAudioDevice.get(int[], Class)` — [ĐO fw src `AbsBYDAutoDevice.java:320-345`] không ghi gì xuống
 * HAL. Không đăng ký listener HAL (tầng 1 không mở thêm đăng ký nào trên xe khi chưa có số đo — §14). Đăng ký duy nhất
 * là `InputDeviceListener` của chính tiến trình, gỡ ở [stop].
 */
class KeySourceRecorder(
    private val app: Context,
    /** Gateway HAL DUY NHẤT của tiến trình (`AppContainer.halGateway`) — gọi trên luồng HAL, không trên luồng phím. */
    private val gateway: () -> HalGateway,
) {
    private val thread = HandlerThread(THREAD_NAME)
    @Volatile private var handler: Handler? = null
    @Volatile private var halExec: ExecutorService? = null
    @Volatile private var inFlight: Future<KeySourceReading>? = null
    private var started = false

    private val devices = ConcurrentHashMap<Int, KeyDeviceInfo>()
    private val deviceListener = object : InputManager.InputDeviceListener {
        override fun onInputDeviceAdded(deviceId: Int) = Unit
        override fun onInputDeviceRemoved(deviceId: Int) { devices.remove(deviceId) }
        override fun onInputDeviceChanged(deviceId: Int) { devices.remove(deviceId) }
    }

    /** Dựng luồng + đăng ký nghe thiết bị nhập. Gọi từ `onServiceConnected` (main); lần gọi thứ hai không làm gì. */
    fun start() {
        if (started) return
        started = true
        thread.start()
        val h = Handler(thread.looper)
        handler = h
        halExec = Executors.newSingleThreadExecutor { r -> Thread(r, HAL_THREAD_NAME).apply { isDaemon = true } }
        // Listener chỉ để bỏ nhớ InputDevice khi thiết bị đổi; hỏng thì vẫn đo được (mất làm mới) — KHÔNG để một bộ ĐO
        // làm hỏng onServiceConnected của dịch vụ giữ phím gán.
        try {
            inputManager()?.registerInputDeviceListener(deviceListener, h)
        } catch (e: RuntimeException) {
            Log.w(KeySourceLog.TAG, "không đăng ký được InputDeviceListener — tên thiết bị có thể cũ", e)
        }
    }

    /** Gỡ đăng ký + dừng luồng. Gọi nhiều lần an toàn. */
    fun stop() {
        try {
            inputManager()?.unregisterInputDeviceListener(deviceListener)
        } catch (e: RuntimeException) {
            Log.w(KeySourceLog.TAG, "gỡ InputDeviceListener hỏng", e)
        }
        handler = null
        thread.quitSafely()
        halExec?.shutdownNow()
        halExec = null
    }

    /**
     * LUỒNG NHẬN PHÍM — ghi chữ ký vào [journal] rồi đẩy phần đo sang luồng `kachi-keysrc`. O(1), không I/O.
     *
     * @param learned lần bấm này đang được HỌC (hộp đặt tên sẽ tìm dòng này qua [KeySourceJournal.lastLearned]).
     */
    fun onDown(sample: KeySample, learned: Boolean) {
        val seq = journal.begin(sample, learned)
        val h = handler
        if (h == null || !h.post { settle(seq, sample) }) {
            journal.complete(seq, null, notRunning(sample), sample)
        }
    }

    /** LUỒNG ĐO. Không bao giờ ném ra ngoài: một lỗi đo không được làm chết tiến trình đang giữ dịch vụ phím. */
    private fun settle(seq: Long, sample: KeySample) {
        val entry = try {
            val device = deviceInfo(sample.deviceId)
            val spec = KeySourceProbes.forKey(sample.keyCode)
            val reading = if (spec == null) KeySourceReading.NOT_MEASURED else readBounded(spec, sample)
            journal.complete(seq, device, reading, sample)
        } catch (e: RuntimeException) {
            Log.w(KeySourceLog.TAG, "đo nguồn phím seq=$seq hỏng", e)
            journal.complete(seq, null, notRunning(sample), sample)
        }
        Log.i(KeySourceLog.TAG, KeySourceLog.line(entry))
    }

    /** Tra `InputDevice` theo id — nhớ lại; bỏ nhớ khi framework báo thiết bị đổi/gỡ ([deviceListener]). */
    private fun deviceInfo(id: Int): KeyDeviceInfo? {
        devices[id]?.let { return it }
        // `getInputDevice` ném lại RemoteException dạng RuntimeException khi system_server lỗi — mất tên thiết bị
        // thì dòng nhật ký vẫn phải ra (có deviceId), không bỏ cả lượt đo.
        val d = try { InputDevice.getDevice(id) } catch (e: RuntimeException) { null } ?: return null
        return KeyDeviceInfo(d.name.orEmpty(), d.descriptor.orEmpty(), d.isVirtual, d.vendorId, d.productId)
            .also { devices[id] = it }
    }

    /** Một lượt đọc có TRẦN thời gian; lượt trước còn treo ⇒ `busy`, không chồng lượt mới. */
    private fun readBounded(spec: KeySourceProbeSpec, sample: KeySample): KeySourceReading {
        if (inFlight?.isDone == false) return KeySourceReading.failed(spec, KeySourceFailure.BUSY, readMs = 0)
        val exec = halExec ?: return KeySourceReading.failed(spec, KeySourceFailure.NOT_RUNNING)
        val f = try {
            exec.submit(Callable { KeySourceProbes.read(spec, gateway(), SystemClock::uptimeMillis, sample.eventTime) })
        } catch (e: RejectedExecutionException) {
            return KeySourceReading.failed(spec, KeySourceFailure.NOT_RUNNING)
        }
        inFlight = f
        return try {
            f.get(spec.budgetMs, TimeUnit.MILLISECONDS)
        } catch (e: TimeoutException) {
            KeySourceReading.failed(spec, KeySourceFailure.TIMEOUT, readMs = spec.budgetMs)
        } catch (e: ExecutionException) {
            KeySourceReading.failed(spec, KeySourceFailure.READ_ERROR, errorClass = (e.cause ?: e).javaClass.simpleName)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            KeySourceReading.failed(spec, KeySourceFailure.NOT_RUNNING)
        }
    }

    private fun notRunning(sample: KeySample): KeySourceReading =
        KeySourceProbes.forKey(sample.keyCode)?.let { KeySourceReading.failed(it, KeySourceFailure.NOT_RUNNING) }
            ?: KeySourceReading.NOT_MEASURED

    private fun inputManager(): InputManager? = app.getSystemService(InputManager::class.java)

    companion object {
        private const val THREAD_NAME = "kachi-keysrc"
        private const val HAL_THREAD_NAME = "kachi-keysrc-hal"

        /**
         * Vòng đệm RAM của TIẾN TRÌNH (sống qua các lần dịch vụ Hỗ trợ bind lại) — cầu Cài đặt đọc dòng vừa học ở đây
         * (`ClusterNavBridge.learnedKeySource`).
         */
        val journal = KeySourceJournal()

        /**
         * Chép chữ ký phím — CHỈ getter field nguyên thuỷ (an toàn trên luồng nhận phím). Phải chép NGAY vì framework
         * `recycle()` event sau khi `onKeyEvent` trả về ([ĐO] `AccessibilityService.java:1884-1890`).
         */
        fun sampleOf(e: KeyEvent): KeySample = KeySample(
            keyCode = e.keyCode,
            action = e.action,
            downTime = e.downTime,
            eventTime = e.eventTime,
            scanCode = e.scanCode,
            deviceId = e.deviceId,
            source = e.source,
            flags = e.flags,
            repeatCount = e.repeatCount,
            metaState = e.metaState,
        )
    }
}
