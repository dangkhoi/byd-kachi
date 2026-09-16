package com.byd.clusternav.launcher.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.SystemClock
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * ═══ V1 pha NÓI · ĐƯỜNG (b) — GIỌNG ĐỌC **TẠI MÁY**, KHÔNG CẦN MẠNG, KHÔNG CẦN GOOGLE ════════════════════════
 *
 * Spec `docs/specs/kachi-voice-feedback.html` **R2b**. Đường này tồn tại cho đúng chiếc xe mà đường (a) không
 * chạy được: đầu xe không có Google TTS (ROM Trung Quốc), hoặc có mà chưa bao giờ tải gói `vi-VN` và cũng không
 * có 4G để tải.
 *
 * ## [ĐO] AAR **đã có sẵn** lớp TTS — không phải nâng phiên bản gì
 * `sherpa-onnx v1.13.8` (đang là dependency cho đường NGHE) chở cả họ TTS. Đo 2026-09-15 trên chính AAR trong
 * `~/.gradle/caches`:
 *  • `unzip -l classes.jar | grep -i tts` ⇒ `OfflineTts`, `OfflineTtsConfig`, `OfflineTtsModelConfig`,
 *    `OfflineTtsVitsModelConfig`, `GeneratedAudio` (lớp `TtsKt`);
 *  • `strings jni/arm64-v8a/libsherpa-onnx-jni.so` ⇒ `Java_com_k2fsa_sherpa_onnx_OfflineTts_newFromFile`,
 *    `…_generateImpl`, `…_getSampleRate` — tức **phần native cũng có**, không phải một lớp Kotlin rỗng.
 * `abiFilters` hiện chỉ arm64-v8a, và `.so` arm64 chính là cái vừa đo ⇒ không phát sinh dung lượng APK nào.
 *
 * ## ⚠ NHƯNG gói giọng thì CHƯA lắp được — xem KDoc [SherpaTtsCatalog]
 * Gói Piper vi_VN chỉ phát hành dạng `tar.bz2` kèm thư mục `espeak-ng-data` 393 tệp, mà `VoiceModelStore` tải
 * **từng tệp có ghim sha256**. Nên tới 1.63 [available] gần như luôn `false`, và lớp này nằm im. Nó **không**
 * phải mã chết: thư mục đã lắp bằng tay (`adb push`) là chạy ngay, và đó chính là phép đo V-oncar của spec.
 *
 * ## Vì sao tự đẩy `AudioTrack` chứ không nhờ ai phát hộ
 * `OfflineTts.generate` trả về [FloatArray] PCM thô + tần số mẫu; không có đường nào khác. Ghi ra tệp `.wav` rồi
 * nhờ `MediaPlayer` là thêm một lượt ghi đĩa (~90 KB mỗi câu) và một tiến trình giải mã cho một thứ vốn đã là PCM.
 */
class SherpaTtsSpeaker(
    ctx: Context,
    private val voice: SherpaTtsCatalog.TtsVoice = SherpaTtsCatalog.PIPER_VI_VAIS1000,
) : VoiceSpeaker {

    override val kind: VoiceSpeakerKind = VoiceSpeakerKind.SHERPA_OFFLINE

    private val app = ctx.applicationContext
    private val root = File(app.filesDir, voice.dir)

    private val dead = AtomicBoolean(false)
    private val focus = AtomicReference<AudioFocusRequest?>(null)
    private val track = AtomicReference<AudioTrack?>(null)

    /**
     * Số thế hệ câu đọc — [stop] tăng nó lên, và luồng nền đang đọc **tự rút** khi thấy số đã đổi.
     *
     * Cùng cơ chế với `VoiceSession.generation`, và vì đúng lý do: một lượt tổng hợp mất hàng trăm ms, trong
     * khoảng đó người lái hoàn toàn có thể đã huỷ phiên hoặc bấm nói lần nữa. Không có số thế hệ thì câu cũ về
     * muộn sẽ phát đè lên lượt nghe mới — tức Kachi nói vào chính cái micro nó vừa mở.
     */
    private val generation = AtomicInteger(0)

    @Volatile private var engine: OfflineTts? = null
    @Volatile private var worker: ExecutorService? = null

    override fun available(): Boolean = !dead.get() && filesPresent()

    /**
     * Gói đã lắp đủ chưa — kiểm **trên đĩa**, không tin một cờ nào.
     *
     * CLAUDE.md §5: *"cấm quyết định bằng cờ RAM; kiểm bằng sự thật"*. Một pref *"đã tải xong"* sống sót qua cả
     * lần người dùng vào Cài đặt ứng dụng bấm *Xoá dữ liệu*.
     */
    private fun filesPresent(): Boolean =
        File(root, voice.model).isFile && File(root, voice.tokens).isFile && File(root, voice.dataDir).isDirectory

    override fun speak(text: String): Boolean = speakInternal(text, null)

    /**
     * OQ4 — đọc rồi báo *"xong"*.
     *
     * [onDone] chạy trên luồng `KachiSpeak` (vế (2) của hợp đồng [VoiceSpeaker.speak]) ở **mọi** đường thoát của
     * một lượt: phát hết, bị [stop] cắt, tổng hợp ném, hay câu đã lỗi thời. Khối `finally` là chỗ duy nhất gọi
     * nó, nên không đường nào bỏ sót — và `getAndSet(null)` giữ đúng *"nhiều nhất một lần"*.
     */
    override fun speak(text: String, onDone: () -> Unit): Boolean = speakInternal(text, onDone)

    private fun speakInternal(text: String, onDone: (() -> Unit)?): Boolean {
        fun fail(): Boolean { onDone?.let { runCatching { it() } }; return false }
        if (!available() || text.isBlank()) return fail()
        val my = generation.incrementAndGet()
        val exec = ensureWorker() ?: return fail()
        // Một ô nhớ, đúng như [AndroidTtsSpeaker]: `speak` mới luôn đè câu cũ (số thế hệ vừa tăng), nên việc chờ
        // của câu cũ hết nghĩa ⇒ đóng sổ ngay thay vì để chỗ gọi kia chờ hết hạn.
        pending.getAndSet(onDone?.let { Waiter(my, it) })?.let { runCatching { it.done() } }
        return runCatching {
            exec.execute { synthesizeAndPlay(text, my) }
            true
        }.onFailure {
            Log.w(TAG, "không xếp được câu vào luồng đọc", it)
            // Không xếp được ⇒ `synthesizeAndPlay` sẽ KHÔNG chạy ⇒ `finally` của nó không tồn tại để đóng sổ.
            settle(my)
        }.getOrDefault(false)
    }

    /**
     * Việc phải làm khi lượt đọc **của đúng thế hệ [gen]** kết thúc.
     *
     * ## [SOÁT Pass 4 · P1] Vì sao phải mang số thế hệ, không chỉ là một lambda trần
     * Luồng đọc là **một luồng duy nhất** ([ensureWorker]), nên câu B xếp vào lúc câu A còn đang `generate()` sẽ
     * chỉ chạy SAU khi `finally` của A đã chạy. Bản đầu để `pending` là một lambda trần và `finally` của A gọi
     * `getAndSet(null)` ⇒ nó vớ đúng việc chờ **của B** và bắn ngay — tức [VoiceSession] mở micro **trong lúc
     * câu hỏi xác nhận của B còn chưa bắt đầu đọc**, đúng cái hazard mà OQ4 sinh ra để chặn. Ca vào: người lái
     * bấm nói lần nữa khi câu trả lời trước còn đang đọc.
     */
    private class Waiter(val gen: Int, val done: () -> Unit)

    /** Việc phải làm khi lượt đọc hiện hành kết thúc; `null` = không ai chờ. Xem [synthesizeAndPlay]. */
    private val pending = AtomicReference<Waiter?>(null)

    /**
     * Đóng sổ cho lượt [gen] — hoặc cho **bất kỳ** lượt nào khi [gen] = `null` ([stop] · [shutdown], nơi mọi câu
     * đều chấm dứt. `compareAndSet` giữ *"nhiều nhất một lần"* (vế (1) của hợp đồng [VoiceSpeaker.speak]).
     */
    private fun settle(gen: Int?) {
        while (true) {
            val w = pending.get() ?: return
            if (gen != null && w.gen != gen) return
            if (pending.compareAndSet(w, null)) { runCatching { w.done() }; return }
        }
    }

    /**
     * Cắt câu đang đọc.
     *
     * ⚠ CHỈ `pause()` + `flush()`, **không** `release()`. Luồng `KachiSpeak` lúc này gần như chắc chắn đang nằm
     * trong `AudioTrack.write(…, WRITE_BLOCKING)` của chính đối tượng ấy, và nhả tài nguyên native dưới chân một
     * lời gọi đang chạy là use-after-free. Đường vào không hiếm chút nào: [VoiceSpeakerRouter.speak] gọi `stop()`
     * trước **mỗi** câu, và `VoiceSession.cancel` cũng gọi — tức mỗi lần người lái bấm nói lần nữa giữa câu.
     * Hai lệnh này đủ để `write` trả về ngay; chủ sở hữu **duy nhất** của `release()` là khối `finally` của [play].
     */
    override fun stop() {
        generation.incrementAndGet()
        track.get()?.let { t -> runCatching { t.pause(); t.flush() } }
        abandonFocus()
        // Cắt câu = câu ấy *"hết đời"* ⇒ đóng sổ NGAY, không đợi `finally` của luồng nền: [VoiceSpeakerRouter]
        // gọi `stop()` trước MỖI câu, nên chỗ đang chờ phải được mở trước khi câu mới đặt việc chờ của nó.
        settle(null)
    }

    /**
     * Nhả hẳn — màn chính đã chết.
     *
     * ⚠ `OfflineTts.release()` chạy **trên chính luồng đã dựng engine**, và được xếp SAU việc đang chạy:
     * `generate()` là một lời gọi native **không ngắt được**, nên `shutdownNow()` chỉ đặt cờ ngắt chứ không kéo
     * luồng ra khỏi native. Nhả con trỏ native từ luồng gọi (luồng vẽ, trong `onDestroy`) trong lúc `generate`
     * còn chạy là use-after-free đúng vào lúc khó lần ra nhất.
     */
    override fun shutdown() {
        dead.set(true)
        stop()
        val w = worker
        worker = null
        if (w == null) { releaseEngine(); return }
        // Từ chối xếp việc ⇒ pool đã đóng ⇒ không còn lượt `generate` nào đang chạy ⇒ nhả tại chỗ được.
        runCatching { w.execute { releaseEngine() } }.onFailure { releaseEngine() }
        // `shutdown()` (không phải `shutdownNow()`): việc vừa xếp PHẢI chạy, nếu không thì engine không bao giờ
        // được nhả. Pool tự kết thúc ngay sau đó.
        runCatching { w.shutdown() }
        // Chốt cuối cho OQ4: nếu vì lý do gì lượt đọc không bao giờ chạy tới `finally` của nó (pool từ chối việc
        // đã xếp), chỗ đang chờ vẫn phải được mở. Gọi thừa vô hại — [settle] giữ *"nhiều nhất một lần"*.
        settle(null)
    }

    private fun releaseEngine() {
        engine?.let { runCatching { it.release() } }
        engine = null
    }

    // ── luồng nền: tổng hợp rồi phát ─────────────────────────────────────────────────────────────

    private fun ensureWorker(): ExecutorService? {
        worker?.let { return it }
        return runCatching {
            Executors.newSingleThreadExecutor { r -> Thread(r, "KachiSpeak") }.also { worker = it }
        }.onFailure { Log.w(TAG, "không dựng được luồng đọc", it) }.getOrNull()
    }

    /**
     * Một câu: dựng engine (lần đầu) → tổng hợp → phát.
     *
     * Bọc toàn bộ trong `runCatching` bắt `Throwable`: mã native của onnxruntime ném `Error` (`UnsatisfiedLinkError`
     * khi thiếu `.so`, `OutOfMemoryError` khi nạp 61 MB trên đầu xe đang chật) — và một launcher **không** được
     * chết vì một câu xác nhận. Cùng lý do đã ghi ở `VoiceSession.runSession`.
     */
    @Suppress("TooGenericExceptionCaught")
    private fun synthesizeAndPlay(text: String, my: Int) {
        try {
            if (my != generation.get()) return
            val tts = ensureEngine() ?: return
            val audio = tts.generate(text, 0, SherpaTtsCatalog.DEFAULT_SPEED)
            // Câu đã lỗi thời trong lúc tổng hợp ⇒ **không phát**. Xem KDoc [generation].
            if (my != generation.get()) return
            play(audio.samples, audio.sampleRate, my)
        } catch (t: Throwable) {
            Log.w(TAG, "đọc offline hỏng", t)
            abandonFocus()
        } finally {
            // OQ4 — MỘT chỗ đóng sổ cho MỌI đường thoát (phát hết · bị cắt · ném · lỗi thời). Đặt ở `finally`
            // chứ không sau `play()`: ba trong bốn đường thoát ở trên là `return` hoặc `catch`.
            // ⚠ `settle(my)` chứ không `settle(null)`: xem KDoc [Waiter] — lượt này chỉ được đóng sổ cho CHÍNH
            // việc chờ của nó, không được vớ việc chờ của câu xếp sau.
            settle(my)
        }
    }

    private fun ensureEngine(): OfflineTts? {
        engine?.let { return it }
        val vits = OfflineTtsVitsModelConfig(
            File(root, voice.model).absolutePath,
            "",
            File(root, voice.tokens).absolutePath,
            File(root, voice.dataDir).absolutePath,
            "",
            NOISE_SCALE,
            NOISE_SCALE_W,
            LENGTH_SCALE,
        )
        val cfg = OfflineTtsConfig(
            model = OfflineTtsModelConfig(
                vits = vits,
                numThreads = SherpaTtsCatalog.NUM_THREADS,
                debug = false,
                provider = "cpu",
            ),
        )
        val t0 = System.currentTimeMillis()
        val built = OfflineTts(null, cfg)
        Log.i(TAG, "dựng máy đọc offline trong ${System.currentTimeMillis() - t0} ms (${voice.id})")
        engine = built
        return built
    }

    /** Đẩy PCM float ra loa. **CHẶN** cho tới khi phát xong ⇒ chỉ gọi trên luồng `KachiSpeak`. */
    private fun play(samples: FloatArray, sampleRate: Int, my: Int) {
        if (samples.isEmpty()) return
        requestFocus()
        val minBuf = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT,
        )
        val bytes = maxOf(minBuf, samples.size * Float.SIZE_BYTES)
        val t = AudioTrack.Builder()
            .setAudioAttributes(speechAttributes())
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(bytes)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        track.set(t)
        try {
            t.play()
            val written = t.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
            // ⚠ `write` chỉ **CHÉP** mẫu vào đệm, không phát. Đệm ở đây được đặt đủ chứa CẢ câu ([bytes]) nên nó
            // trả về khi loa mới kịp phát vài mili-giây. `AudioTrack.stop()` bảo phần còn trong đệm được phát nốt,
            // nhưng `release()` ngay sau đó thì cắt đứt — tức gần như **cả câu** không bao giờ ra tiếng. Phải chờ
            // đầu đọc chạy hết số khung đã ghi rồi mới nhả (xem [drain]).
            if (written > 0 && my == generation.get()) {
                runCatching { t.stop() }
                drain(t, written, sampleRate, my)
            }
        } finally {
            track.compareAndSet(t, null)
            runCatching { t.release() }
            abandonFocus()
        }
    }

    /**
     * Chờ đầu đọc chạy hết [frames] khung đã ghi, rồi mới cho [play] nhả `AudioTrack`.
     *
     * Trần = đúng **thời lượng của chính câu đó** + [DRAIN_MARGIN_MS], nên một `AudioTrack` chết giữa chừng (ROM
     * lạ, mất tiêu điểm) không giữ luồng `KachiSpeak` lại quá một câu. [stop] tăng số thế hệ ⇒ vòng lặp thoát
     * ngay, đúng nghĩa *"cắt câu đang đọc"*.
     */
    private fun drain(t: AudioTrack, frames: Int, sampleRate: Int, my: Int) {
        if (sampleRate <= 0) return
        val capMs = frames.toLong() * MS_PER_SECOND / sampleRate + DRAIN_MARGIN_MS
        val deadline = SystemClock.uptimeMillis() + capMs
        while (my == generation.get() && SystemClock.uptimeMillis() < deadline) {
            val head = runCatching { t.playbackHeadPosition }.getOrNull() ?: return
            if (head >= frames) return
            try {
                Thread.sleep(DRAIN_POLL_MS)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                return
            }
        }
    }

    // ── tiêu điểm âm thanh (gương của VoiceCapture / AndroidTtsSpeaker) ──────────────────────────

    private fun speechAttributes(): AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANT)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    private fun audio(): AudioManager? = app.getSystemService(AudioManager::class.java)

    private fun requestFocus() {
        if (focus.get() != null) return
        val am = audio() ?: return
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(speechAttributes())
            .setWillPauseWhenDucked(false)
            .build()
        val ok = runCatching { am.requestAudioFocus(req) }.getOrNull() == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        if (ok) focus.set(req) else Log.i(TAG, "ROM từ chối tiêu điểm âm thanh — vẫn đọc, không ducking")
    }

    private fun abandonFocus() {
        val req = focus.getAndSet(null) ?: return
        val am = audio() ?: return
        runCatching { am.abandonAudioFocusRequest(req) }
    }

    private companion object {
        const val TAG = "KachiVoiceTtsOffline"

        // Ba số của chính gói Piper ([ĐO] `vi_VN-vais1000-medium.onnx.json`: noise_scale 0.667 · noise_w 0.8 ·
        // length_scale 1). Viết ra đây vì `OfflineTtsVitsModelConfig` KHÔNG đọc tệp `.json` ấy — mặc định của
        // sherpa là số khác, và dùng số khác thì giọng nghe méo đúng kiểu "máy nói" mà ai cũng tắt ngay.
        const val NOISE_SCALE = 0.667f
        const val NOISE_SCALE_W = 0.8f
        const val LENGTH_SCALE = 1.0f

        /** Nhịp hỏi lại đầu đọc trong [drain] — đủ nhỏ để không nghe thấy khoảng lặng nối câu. */
        const val DRAIN_POLL_MS = 20L

        /** Dôi ra ngoài thời lượng câu: đệm phần cứng + nhịp nạp của ROM. */
        const val DRAIN_MARGIN_MS = 300L

        const val MS_PER_SECOND = 1_000L
    }
}
