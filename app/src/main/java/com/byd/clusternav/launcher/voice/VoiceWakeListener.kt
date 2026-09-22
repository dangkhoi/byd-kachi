package com.byd.clusternav.launcher.voice

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import android.util.Log
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.math.min
import kotlin.math.sqrt

/**
 * ═══ "Hey Kachi" — VÒNG NGHE NỀN (mic liên tục → cổng rẻ → KWS), driven bởi [VoiceWakeController] đã test ══════
 *
 * Lớp GLUE Android: mở micro, đọc khung, tính RMS, đọc `/proc/loadavg`, hỏi [VoiceWakeController] (bộ não đã
 * kiểm off-car), chạy KWS khi được phép. Mọi lý lẽ chống-hang-CPU nằm ở controller; đây chỉ thi hành quyết định
 * — cộng bốn chốt mà **chỉ tầng Android có thể sai** (đọc mic lỗi · luồng · nhả mic · nhường mic).
 *
 * ## MỘT luồng cho cả vòng đời service, đỗ xe khi không nghe (soát 2026-09-18)
 * Bản đầu dựng/huỷ một [VoiceWakeListener] **mỗi lần màn bật/tắt**. Đó là đúng họ lỗi *"đường sống lâu hơn thứ
 * nó phục vụ"* của dự án: `stop()` không `join`, nên bật–tắt màn nhanh tay để lại **hai** luồng, luồng cũ đang
 * cuộn nốt vòng của nó và sẽ `release()` chốt micro **của lượt mới** khi thoát ⇒ ngay sau đó một phiên lệnh xin
 * được mic **cùng lúc** với bộ nghe mới ⇒ hai `AudioRecord`. Nay: **một** luồng, tạo ở [start], kết ở [stop]
 * (có `join` có trần); màn bật/tắt chỉ là [setListening] — luồng **đỗ** trong `wait()` (0 % CPU, thức ngay khi
 * gọi lại). Kèm hai lớp nữa: nhãn chốt micro **riêng cho từng lượt chạy** (`wake#n`) và
 * [VoiceSingleFlight.release] theo nhãn ⇒ một luồng cũ **không thể** nhả chốt của ai khác.
 *
 * ## Cấu trúc ngoài/trong (một mic, nhả đúng lúc)
 * Vòng NGOÀI: đỗ tới khi được nghe → xin mic ([VoiceSingleFlight.acquireWake]) → vòng TRONG đọc khung. Vòng
 * trong trả về khi phải nhả mic:
 *  • `PAUSED` (màn tắt / tắt công tắc) → nhả mic, về chỗ đỗ.
 *  • `SUSPEND` (hệ nóng) → **NHẢ mic** + nghỉ (nghỉ **tăng dần**, xem [backoffMs]) rồi xin lại.
 *  • `YIELD` (có phiên lệnh đang xin mic) → **NHẢ mic** ngay trong một khung; xem [VoiceSingleFlight.requestYield].
 *  • `WOKE` → **NHẢ mic** rồi [onWake] + nghỉ [REARM_MS] cho phiên lệnh dùng mic.
 *  • `ERROR` (mic bị giành / ROM từ chối / KWS ném) → nhả + nghỉ tăng dần, **KHÔNG chết hẳn**.
 *  • `FUSED` → cầu chì false-accept ⇒ [onAutoDisable] + dừng.
 * ⇒ KHÔNG bao giờ hai mic mở cùng lúc, và mic được nhả trên **mọi** đường ra (kể cả ngoại lệ: `finally`).
 *
 * ## Degrade khi thiếu model KWS
 * `kws == null` (owner chưa đăng model, hoặc chưa có câu gọi đã tokenize) ⇒ vòng vẫn chạy (RMS + load +
 * controller) nhưng `RUN_KWS` không làm gì — đo được baseline CPU của "mic nền + gating" trên xe mà chưa bắt câu
 * gọi. Ở chế độ ấy một khung tốn đúng một phép RMS trên 1 600 mẫu.
 *
 * ⚠ Chạy trên luồng NỀN riêng (`MIN_PRIORITY`, daemon). [onWake]/[onAutoDisable] được gọi TỪ luồng này — chủ gọi
 * ([VoiceWakeService]) tự post về main. Đây là tệp thứ HAI mở `AudioRecord` (sau `VoiceCapture`); an toàn vì
 * [VoiceSingleFlight] bảo đảm không đồng thời (bài canh mic-opener đã nới cho đúng hai tệp này).
 */
class VoiceWakeListener(
    private val ctx: Context,
    private val onWake: () -> Unit,
    private val onAutoDisable: () -> Unit,
    private val kwsFactory: (Context) -> WakeEngine? = { defaultEngine(it) },
) {
    private val controller = VoiceWakeController()

    /** Nhãn chốt micro **của riêng lượt chạy này** — xem KDoc lớp (chống nhả chốt của lượt khác). */
    private val label = "${VoiceSingleFlight.LABEL_WAKE}#${SEQ.incrementAndGet()}"

    /** Ổ khoá chỗ đỗ — [park] chờ ở đây, [setListening] đánh thức. */
    private val gateLock = ReentrantLock()
    private val gateWake = gateLock.newCondition()

    @Volatile private var running = false
    @Volatile private var listening = false
    @Volatile private var thread: Thread? = null

    /** Số khung đọc được của lượt mic vừa rồi — dùng để xoá bậc nghỉ khi vòng đã chạy khoẻ (xem [backoffMs]). */
    @Volatile private var lastSessionFrames = 0

    fun start() {
        if (running) return
        running = true
        listening = true
        controller.reset()
        thread = Thread({ runOuter() }, "kachi-wake").apply {
            isDaemon = true // luồng nền không được giữ tiến trình sống
            priority = Thread.MIN_PRIORITY
            start()
        }
    }

    /**
     * Bật/tắt việc NGHE mà **không** dựng lại luồng (gate màn-sáng). Tắt ⇒ vòng trong nhả mic trong một khung
     * rồi đỗ; bật ⇒ thức ngay (không phải chờ hết một nhịp nghỉ).
     */
    fun setListening(on: Boolean) {
        if (!running || listening == on) return
        listening = on
        gateLock.withLock { gateWake.signalAll() }
    }

    /**
     * Kết luồng — **có `join` có trần** (mặc định [JOIN_MS]).
     *
     * Gọi từ luồng main (`onDestroy` / cầu chì), nên trần phải nhỏ: vòng trong thoát sau đúng một khung đọc
     * (~100 ms) cộng `stop`/`release`, và chỗ đỗ thức ngay bằng `interrupt`. Hết trần mà luồng còn sống thì
     * **không** nhả chốt micro hộ nó: chốt còn nghĩa là `AudioRecord` của nó có thể còn mở, và cướp chốt lúc ấy
     * chính là cách tạo ra hai mic. Nó tự nhả bằng `finally` của chính nó (nhả theo nhãn ⇒ không đụng ai).
     */
    fun stop() {
        running = false
        listening = false
        gateLock.withLock { gateWake.signalAll() }
        val t = thread
        thread = null
        t?.interrupt()
        runCatching { t?.join(JOIN_MS) }
        if (t == null || !t.isAlive) {
            VoiceSingleFlight.release(label) // lưới an toàn: luồng đã chết thật thì chốt không được kẹt
        } else {
            Log.w(TAG, "luồng wake chưa dừng sau $JOIN_MS ms — để nó tự nhả chốt (không cướp, tránh hai mic)")
        }
    }

    fun isRunning(): Boolean = running

    fun isListening(): Boolean = running && listening

    // ═══ VÒNG NGOÀI ══════════════════════════════════════════════════════════════════════════════════════════

    private fun runOuter() {
        var kws: WakeEngine? = null
        var kwsTried = false
        var idleSteps = 0
        try {
            while (alive()) {
                if (!park()) break
                // Nạp model **lần đầu được nghe**, không phải lúc dựng luồng: máy khởi động với màn tắt thì
                // không có lý gì nạp ~4 MB ONNX. Chỉ thử MỘT lần cho cả vòng đời luồng — thiếu model là trạng
                // thái bền (degrade chỉ-RMS), thử lại mỗi vòng chỉ là đọc đĩa vô ích.
                if (!kwsTried) { kwsTried = true; kws = runCatching { kwsFactory(ctx) }.getOrNull() }
                if (VoiceSingleFlight.acquireWake(label) !is VoiceSingleFlight.Grant.Ok) {
                    // Phiên lệnh đang giữ mic — chuyện thường, không phải lỗi. Nghỉ một nhịp rồi xin lại.
                    nap(BUSY_NAP_MS)
                    continue
                }
                val action = try {
                    inner(kws)
                } catch (t: Throwable) {
                    Log.w(TAG, "wake inner lỗi — nhả mic, thử lại sau", t)
                    Inner.ERROR
                } finally {
                    VoiceSingleFlight.release(label)
                }
                if (!alive()) break
                when (action) {
                    Inner.WOKE -> { idleSteps = 0; safe(onWake); nap(REARM_MS) } // nhường mic cho phiên lệnh
                    Inner.YIELD -> { idleSteps = 0; nap(YIELD_NAP_MS) }          // phiên lệnh xin mic
                    Inner.PAUSED -> idleSteps = 0                                // về chỗ đỗ
                    Inner.SUSPEND -> nap(backoffMs(++idleSteps))                 // hệ nóng
                    Inner.ERROR -> nap(backoffMs(++idleSteps))                   // mic/KWS hỏng: lùi, không chết
                    Inner.FUSED -> { safe(onAutoDisable); running = false }       // cầu chì false-accept
                    Inner.STOP -> running = false
                }
            }
        } finally {
            VoiceSingleFlight.release(label)
            runCatching { kws?.release() }
            running = false
        }
    }

    private enum class Inner { WOKE, YIELD, PAUSED, SUSPEND, ERROR, FUSED, STOP }

    /** Còn được chạy không (đã dừng / đã bị interrupt). */
    private fun alive(): Boolean = running && !Thread.currentThread().isInterrupted

    /** Đỗ tới khi được nghe. Trả `false` ⇒ phải kết luồng. `wait` có trần để một `notify` bị mất không đỗ mãi. */
    private fun park(): Boolean {
        gateLock.withLock {
            while (running && !listening) {
                try {
                    gateWake.await(PARK_WAIT_MS, TimeUnit.MILLISECONDS)
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return false
                }
            }
        }
        return alive() && listening
    }

    /**
     * Nghỉ **tăng dần** rồi thôi tăng ở [MAX_NAP_MS].
     *
     * Hai ca cần nó, cùng một lẽ: hệ đang nóng ([VoiceLoadGuard] cắt) và mic/KWS đang hỏng. Ở cả hai, xin lại
     * đều đặn 2 s nghĩa là mở/đóng `AudioRecord` 30 lần/phút **suốt thời gian sự cố** — vô ích và không rẻ. Lùi
     * dần cũng là cách để một sự cố không tự hết không bao giờ trở thành một vòng lặp nóng. Vòng đã chạy khoẻ
     * (đọc được ≥ [HEALTHY_FRAMES] khung) thì xoá bậc — sự cố cũ không được tính vào lần sau.
     */
    private fun backoffMs(steps: Int): Long {
        if (lastSessionFrames >= HEALTHY_FRAMES) return SUSPEND_NAP_MS
        return min(SUSPEND_NAP_MS * steps, MAX_NAP_MS)
    }

    // ═══ VÒNG TRONG (mic đang giữ) ════════════════════════════════════════════════════════════════════════════

    /** Đọc khung với mic đang giữ tới khi có sự kiện phải nhả mic. Mic được nhả trên MỌI đường ra (`finally`). */
    private fun inner(kws: WakeEngine?): Inner {
        // Không mở được mic (app khác giữ phần cứng dù chốt đã cho) ⇒ lượt này **không** khoẻ: xoá mốc khoẻ để
        // bậc nghỉ ở vòng ngoài tăng dần, thay vì xin lại đều đặn 2 s suốt thời gian sự cố.
        val rec = openRecord() ?: run { lastSessionFrames = 0; return Inner.ERROR }
        var frames = 0
        try {
            runCatching { rec.startRecording() }.onFailure {
                Log.w(TAG, "startRecording hỏng — nhả mic, thử lại sau", it)
                return Inner.ERROR
            }
            // ROM này dựng được `AudioRecord` rồi vẫn từ chối ghi **mà không ném** (đã gặp ở `VoiceCapture`).
            // Không kiểm ở đây thì mọi `read` trả mã lỗi và vòng dưới phải gánh — kiểm một lần rẻ hơn.
            if (rec.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                Log.w(TAG, "micro không vào được trạng thái ghi — ROM từ chối? nhả mic, thử lại sau")
                return Inner.ERROR
            }
            val buf = ShortArray(FRAME)
            val fbuf = FloatArray(FRAME)
            var load = 0.0
            var lastLoadAt = 0L
            var zeros = 0
            while (alive() && listening) {
                // Nhường mic cho phiên lệnh — hỏi TRƯỚC khi đọc để một cú bấm nút mic chỉ phải chờ ~một khung.
                if (VoiceSingleFlight.yieldRequested()) return Inner.YIELD
                val n = rec.read(buf, 0, FRAME)
                // ═══ CHỐT CHỐNG VÒNG NÓNG ════════════════════════════════════════════════════════════════
                // `read` âm = `ERROR_INVALID_OPERATION`/`ERROR_DEAD_OBJECT`… và nó trả về **NGAY** (không chặn).
                // `continue` ở đây là một vòng lặp 100 % CPU **vĩnh viễn** trên đầu xe — đúng thứ owner lo nhất,
                // và đúng cái bẫy `VoiceCapture` đã trả giá để biết. Nhả mic, lùi, xin lại.
                if (n < 0) { Log.w(TAG, "đọc micro trả $n — nhả mic, xin lại sau"); return Inner.ERROR }
                if (n == 0) {
                    // Không có mã lỗi mà cũng không có mẫu: nghỉ một nhịp ngắn (KHÔNG quay vòng trần), và nếu
                    // cứ thế thì coi như mic hỏng.
                    if (++zeros > MAX_ZERO_READS) { Log.w(TAG, "micro trả 0 mẫu liên tục — nhả mic"); return Inner.ERROR }
                    nap(ZERO_READ_NAP_MS)
                    continue
                }
                zeros = 0
                frames++
                var sum = 0.0
                for (i in 0 until n) { val s = buf[i].toDouble(); sum += s * s }
                val rms = sqrt(sum / n)
                val now = SystemClock.elapsedRealtime()
                if (now - lastLoadAt >= LOAD_EVERY_MS) { load = readLoad1(); lastLoadAt = now }
                when (controller.onFrame(rms, load, now)) {
                    VoiceWakeController.Frame.SUSPENDED -> return if (controller.isFused()) Inner.FUSED else Inner.SUSPEND
                    VoiceWakeController.Frame.IDLE -> Unit
                    VoiceWakeController.Frame.RUN_KWS -> if (kws != null) {
                        for (i in 0 until n) fbuf[i] = buf[i] / 32768f
                        if (kws.feed(fbuf, n)) {
                            when (controller.onKwsResult(true, now)) {
                                VoiceWakeController.Wake.FIRE -> return Inner.WOKE
                                VoiceWakeController.Wake.FUSED -> return Inner.FUSED
                                VoiceWakeController.Wake.NONE -> Unit
                            }
                        }
                    }
                }
            }
            return if (alive()) Inner.PAUSED else Inner.STOP
        } finally {
            lastSessionFrames = frames
            runCatching { rec.stop() }
            runCatching { rec.release() }
        }
    }

    private fun openRecord(): AudioRecord? = runCatching {
        val min = AudioRecord.getMinBufferSize(VoiceWakeKws.SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        // Sàn đệm **một giây tiếng** (cùng lẽ với `VoiceCapture.MIN_BUFFER_MS`): một lượt suy diễn KWS hoặc một
        // lượt GC dài hơn thời lượng đệm là mất mẫu, và mất kiểu đó không có lỗi nào báo — chỉ là "sao gọi mãi
        // không nghe". 32 KB.
        val floor = VoiceWakeKws.SAMPLE_RATE * 2
        val size = maxOf(min, floor)
        AudioRecord(MediaRecorder.AudioSource.MIC, VoiceWakeKws.SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, size)
            .let { r ->
                if (r.state == AudioRecord.STATE_INITIALIZED) r
                else { Log.w(TAG, "AudioRecord chưa init"); runCatching { r.release() }; null }
            }
    }.getOrElse { Log.w(TAG, "mở mic wake lỗi", it); null }

    private fun readLoad1(): Double = runCatching {
        File("/proc/loadavg").readText().trim().substringBefore(' ').toDouble()
    }.getOrDefault(0.0)

    private fun nap(ms: Long) = runCatching { Thread.sleep(ms) }.getOrElse { Thread.currentThread().interrupt() }
    private fun safe(f: () -> Unit) = runCatching { f() }.onFailure { Log.w(TAG, "callback wake lỗi", it) }

    companion object {
        private const val TAG = "WakeListen"
        private const val FRAME = 1_600            // 100 ms @ 16 kHz
        private const val LOAD_EVERY_MS = 1_000L   // đọc /proc/loadavg mỗi giây (không mỗi khung)
        private const val SUSPEND_NAP_MS = 2_000L  // bậc nghỉ đầu khi hệ nóng / mic hỏng
        private const val MAX_NAP_MS = 30_000L     // trần bậc nghỉ — sự cố dài không thành vòng xin lại đều đặn
        private const val BUSY_NAP_MS = 2_000L     // phiên lệnh đang giữ mic
        private const val YIELD_NAP_MS = 3_000L    // vừa nhường mic: đủ để phiên lệnh mở được mic của nó
        private const val REARM_MS = 4_000L        // sau khi nổ wake: nhường mic cho phiên lệnh
        private const val PARK_WAIT_MS = 5_000L    // trần một nhịp đỗ (lưới an toàn cho `notify` bị mất)
        private const val ZERO_READ_NAP_MS = 20L
        private const val MAX_ZERO_READS = 50      // ~1 s toàn khung rỗng ⇒ coi như mic hỏng
        private const val HEALTHY_FRAMES = 50      // ≥ 5 s đọc được ⇒ xoá bậc nghỉ
        private const val JOIN_MS = 700L           // trần chờ luồng kết (gọi từ main — xem KDoc `stop`)

        /** Đếm lượt chạy, chỉ để nhãn chốt micro của mỗi lượt là **duy nhất** (xem [label]). */
        private val SEQ = AtomicInteger(0)

        /**
         * Model KWS ở `filesDir/`[WakeModelCatalog.dir]; thiếu ⇒ null (chỉ-RMS).
         *
         * Thư mục **và** 5 tên tệp lấy từ [WakeModelCatalog] — cùng bảng mà đường tải OTA dùng để ghim sha256.
         * Chép tên tệp lần thứ hai ở đây là mở đúng cái khe im lặng: gói tải về đủ 5 tệp, engine soi một tên
         * khác, `build` trả `null`, và "Hey Kachi" chạy mà không bao giờ nhận — không log nào nói vì sao.
         */
        /**
         * Chọn engine wake theo pref (owner 2026-09-22, mặc định **ASR** no-train). ASR nghe "kachi" bằng chính
         * mô hình tiếng Việt (không train); KWS gigaspeech là đường lùi. Engine nào null (chưa có model) ⇒ thử
         * engine kia; cả hai null ⇒ degrade chỉ-RMS (vòng vẫn chạy).
         */
        private fun defaultEngine(ctx: Context): WakeEngine? {
            val preferAsr = com.byd.clusternav.Prefs.wakeEngineAsr(ctx)
            val asr = { VoiceWakeAsr.create(ctx) }
            val kws = { defaultKws(ctx) }
            return if (preferAsr) (asr() ?: kws()) else (kws() ?: asr())
        }

        private fun defaultKws(ctx: Context): VoiceWakeKws? {
            val dir = File(ctx.filesDir, WakeModelCatalog.dir)
            return VoiceWakeKws.build(
                dir,
                encoder = WakeModelCatalog.ENCODER,
                decoder = WakeModelCatalog.DECODER,
                joiner = WakeModelCatalog.JOINER,
                tokens = WakeModelCatalog.TOKENS,
                keywordsFileName = WakeModelCatalog.KEYWORDS,
            )
        }
    }
}
