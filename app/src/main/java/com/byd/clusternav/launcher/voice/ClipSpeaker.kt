package com.byd.clusternav.launcher.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.SystemClock
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * ═══ T7 · MÁY ĐỌC BẰNG CLIP GHÉP — "Giọng Kachi bé" ═════════════════════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-voice-clone.html` **T7 · R4**. Nhận **chuỗi GỐC** (chưa qua [TtsPronunciation.normalise]),
 * tra [VoiceClipInventory]:
 *  • **trúng** ⇒ giải mã các clip AAC → PCM → nối → phát bằng một `AudioTrack`;
 *  • **trượt** (câu động, tên riêng, số ngoài dải, gói thiếu clip) ⇒ **nhường Piper** ([fallback]) — đọc khác
 *    giọng, nhưng ĐÚNG (§4.5). Trước khi nhường mới gọi `TtsPronunciation.normalise`, vì Piper cần chữ đã phiên âm.
 *
 * ## Vì sao KHÔNG chặn luồng gọi + luôn gọi `onDone` (hợp đồng [VoiceSpeaker.speak])
 * Cùng cơ chế đã chứng minh ở [SherpaTtsSpeaker]: một luồng-đơn (`KachiClip`) giải mã + phát; số thế hệ
 * ([generation]) cho [stop] cắt câu cũ; một `Waiter(gen, onDone)` bảo đảm `onDone` chạy **đúng một lần** ở MỌI
 * đường thoát (phát hết · bị cắt · giải mã hỏng · câu lỗi thời). Cổng xác nhận của `VoiceSession` chờ mốc ấy —
 * nuốt nó là cổng chết im.
 *
 * ## Sở hữu [fallback]
 * [fallback] (một [SherpaTtsSpeaker] Piper) do **[VoiceSpeakerRouter] sở hữu vòng đời**: Router `stop()`/`shutdown()`
 * nó trực tiếp. `ClipSpeaker` chỉ **dùng** nó cho ca trượt, và `stop()` ở đây có gọi `fallback.stop()` (idempotent)
 * để lớp này vẫn đúng khi dùng độc lập; nhưng KHÔNG `shutdown` nó (đó là việc của chủ).
 */
class ClipSpeaker(
    ctx: Context,
    private val fallback: VoiceSpeaker,
) : VoiceSpeaker {

    /** Chỉ để BÁO CÁO (nhật ký/cầu kiểm thử) — đây là một giọng đọc offline tại máy như Piper. */
    override val kind: VoiceSpeakerKind = VoiceSpeakerKind.SHERPA_OFFLINE

    private val app = ctx.applicationContext
    private val root = File(app.filesDir, KachiClipVoiceCatalog.DIR)
    private val indexFile = File(root, KachiClipVoiceCatalog.INDEX_FILE)
    private val numFile = File(root, KachiClipVoiceCatalog.NUM_FILE)

    private val dead = AtomicBoolean(false)
    private val generation = AtomicInteger(0)
    private val track = AtomicReference<AudioTrack?>(null)
    private val focus = AtomicReference<AudioFocusRequest?>(null)

    @Volatile private var worker: ExecutorService? = null
    @Volatile private var inventory: VoiceClipInventory? = null
    @Volatile private var inventoryStamp: Long = Long.MIN_VALUE

    /**
     * Gói đã có trên đĩa chưa — kiểm **hai tệp bảng tra** (index + num), không tin một cờ nào (CLAUDE.md §5).
     * Đủ để [VoiceSpeakerRouter] quyết có rẽ sang đây không; thiếu clip lẻ thì [resolve] trượt → nhường Piper.
     */
    override fun available(): Boolean = !dead.get() && indexFile.isFile && numFile.isFile

    override fun speak(text: String): Boolean = speakInternal(text, null)

    override fun speak(text: String, onDone: () -> Unit): Boolean = speakInternal(text, onDone)

    private fun speakInternal(text: String, onDone: (() -> Unit)?): Boolean {
        fun toFallback(): Boolean {
            // §4.7 — CHỈ tới đây mới phiên âm; đường clip trúng dùng chuỗi gốc.
            val said = TtsPronunciation.normalise(text)
            return if (onDone == null) fallback.speak(said) else fallback.speak(said, onDone)
        }
        if (dead.get() || text.isBlank()) { onDone?.let { runCatching { it() } }; return false }
        val inv = inventory() ?: return toFallback()
        val hit = inv.resolve(text) as? VoiceClipInventory.Resolution.Hit ?: return toFallback()
        val exec = ensureWorker() ?: return toFallback()

        val my = generation.incrementAndGet()
        // Câu cũ (nếu ai chờ) sắp bị đè ⇒ đóng sổ cho nó ngay (cùng cách [SherpaTtsSpeaker]).
        pending.getAndSet(onDone?.let { Waiter(my, it) })?.let { runCatching { it.done() } }
        return runCatching {
            exec.execute { playClips(hit.clips, my, text) }
            true
        }.onFailure {
            Log.w(TAG, "không xếp được câu vào luồng phát clip", it)
            settle(my)
        }.getOrDefault(false)
    }

    // ── hợp đồng "đọc xong" (gương [SherpaTtsSpeaker]) ───────────────────────────────────────────

    private class Waiter(val gen: Int, val done: () -> Unit)

    private val pending = AtomicReference<Waiter?>(null)

    /** Đóng sổ cho lượt [gen] (hoặc mọi lượt khi `null` — [stop]/[shutdown]); `compareAndSet` giữ *nhiều nhất một lần*. */
    private fun settle(gen: Int?) {
        while (true) {
            val w = pending.get() ?: return
            if (gen != null && w.gen != gen) return
            if (pending.compareAndSet(w, null)) { runCatching { w.done() }; return }
        }
    }

    override fun stop() {
        generation.incrementAndGet()
        track.get()?.let { t -> runCatching { t.pause(); t.flush() } }
        abandonFocus()
        settle(null)
        // Ca trượt đã giao câu cho Piper ⇒ dừng nó luôn (idempotent). Router cũng dừng Piper trực tiếp — gọi thừa vô hại.
        runCatching { fallback.stop() }
    }

    override fun shutdown() {
        dead.set(true)
        stop()
        val w = worker
        worker = null
        runCatching { w?.shutdown() }
        settle(null)
        // KHÔNG shutdown [fallback]: [VoiceSpeakerRouter] sở hữu vòng đời của nó.
    }

    // ── bảng tra: dựng lười + nhớ theo dấu vết tệp ──────────────────────────────────────────────

    /**
     * [VoiceClipInventory] của gói hiện trên đĩa; `null` nếu gói vắng/hỏng ⇒ chỗ gọi nhường Piper.
     *
     * Dựng MỘT lần rồi nhớ; dựng lại chỉ khi `index.tsv` đổi (người dùng cài lại gói giữa phiên). Đọc + phân tích
     * ~500 dòng là rẻ và chạy trên luồng gọi đúng một lần.
     */
    private fun inventory(): VoiceClipInventory? {
        if (!indexFile.isFile || !numFile.isFile) return null
        val stamp = indexFile.lastModified() * 31 + numFile.lastModified()
        inventory?.let { if (inventoryStamp == stamp) return it }
        return runCatching {
            VoiceClipInventory.parse(indexFile.readText(), numFile.readText()).also {
                inventory = it
                inventoryStamp = stamp
            }
        }.onFailure { Log.w(TAG, "không đọc được bảng tra clip", it) }.getOrNull()
    }

    private fun ensureWorker(): ExecutorService? {
        worker?.let { return it }
        return runCatching {
            Executors.newSingleThreadExecutor { r -> Thread(r, "KachiClip") }.also { worker = it }
        }.onFailure { Log.w(TAG, "không dựng được luồng phát clip", it) }.getOrNull()
    }

    // ── luồng nền: giải mã → nối → phát ─────────────────────────────────────────────────────────

    private class Pcm(val samples: ShortArray, val sampleRate: Int, val channels: Int)

    /**
     * Giải mã từng clip, nối PCM, phát. Bọc `Throwable` như [SherpaTtsSpeaker]: `MediaCodec`/native ném cả `Error`,
     * và một launcher không được chết vì một câu trả lời.
     *
     * Giải mã hỏng (gói tải dở, clip lẻ mất) ⇒ **nhường Piper** với chính `onDone` của lượt này — claim `Waiter`
     * bằng `compareAndSet` để `finally` không gọi `onDone` lần thứ hai.
     */
    @Suppress("TooGenericExceptionCaught", "ReturnCount")
    private fun playClips(clips: List<VoiceClipInventory.Clip>, my: Int, origText: String) {
        var handedOff = false
        try {
            if (my != generation.get()) return
            var rate = -1
            var channels = -1
            val parts = ArrayList<ShortArray>(clips.size)
            for (c in clips) {
                if (my != generation.get()) return
                val pcm = decodeAac(File(root, c.file))
                if (pcm == null || pcm.samples.isEmpty()) { handedOff = handOffToPiper(my, origText); return }
                if (rate < 0) { rate = pcm.sampleRate; channels = pcm.channels }
                // Các clip cùng gói cùng 24 kHz mono; lệch ⇒ cần lấy mẫu lại (không làm ở đây) ⇒ nhường Piper.
                if (pcm.sampleRate != rate || pcm.channels != channels) { handedOff = handOffToPiper(my, origText); return }
                parts.add(pcm.samples)
            }
            if (rate <= 0) { handedOff = handOffToPiper(my, origText); return }
            if (my != generation.get()) return
            play(concat(parts), rate, channels, my)
        } catch (t: Throwable) {
            Log.w(TAG, "phát clip hỏng", t)
            abandonFocus()
        } finally {
            if (!handedOff) settle(my)
        }
    }

    /** Nhường Piper cho lượt [my], mang theo đúng `onDone` của nó (nếu có). Trả `true` nếu đã claim `Waiter`. */
    private fun handOffToPiper(my: Int, origText: String): Boolean {
        val superseded = my != generation.get()
        val w = pending.get()
        val mine = w != null && w.gen == my && pending.compareAndSet(w, null)
        if (superseded) { if (mine) runCatching { w!!.done() }; return mine }
        val said = TtsPronunciation.normalise(origText)
        if (mine) runCatching { fallback.speak(said, w!!.done) } else runCatching { fallback.speak(said) }
        return mine
    }

    /** Nối các đoạn PCM thành một mảng. Các clip AAC đã mang sẵn khoảng lặng đệm ở hai đầu ⇒ nối thẳng, không click. */
    private fun concat(parts: List<ShortArray>): ShortArray {
        val total = parts.sumOf { it.size }
        val out = ShortArray(total)
        var at = 0
        for (p in parts) { System.arraycopy(p, 0, out, at, p.size); at += p.size }
        return out
    }

    /** Đẩy PCM 16-bit ra loa; **CHẶN** tới khi phát xong ⇒ chỉ gọi trên luồng `KachiClip`. Gương [SherpaTtsSpeaker.play]. */
    private fun play(samples: ShortArray, sampleRate: Int, channels: Int, my: Int) {
        if (samples.isEmpty()) return
        requestFocus()
        val mask = if (channels >= 2) AudioFormat.CHANNEL_OUT_STEREO else AudioFormat.CHANNEL_OUT_MONO
        val minBuf = AudioTrack.getMinBufferSize(sampleRate, mask, AudioFormat.ENCODING_PCM_16BIT)
        val bytes = maxOf(minBuf, samples.size * Short.SIZE_BYTES)
        val t = AudioTrack.Builder()
            .setAudioAttributes(speechAttributes())
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(mask)
                    .build(),
            )
            .setBufferSizeInBytes(bytes)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        track.set(t)
        try {
            t.play()
            val written = t.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
            if (written > 0 && my == generation.get()) {
                runCatching { t.stop() }
                drain(t, written / maxOf(1, channels), sampleRate, my)
            }
        } finally {
            track.compareAndSet(t, null)
            runCatching { t.release() }
            abandonFocus()
        }
    }

    /** Chờ đầu đọc chạy hết [frames] khung rồi mới nhả `AudioTrack` — nếu không `release()` cắt cụt câu. Xem [SherpaTtsSpeaker.drain]. */
    private fun drain(t: AudioTrack, frames: Int, sampleRate: Int, my: Int) {
        if (sampleRate <= 0 || frames <= 0) return
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

    /**
     * Giải mã một tệp AAC (ADTS) → PCM 16-bit qua [MediaExtractor] + [MediaCodec]; `null` nếu hỏng.
     *
     * Vòng đồng bộ chuẩn (`dequeueInputBuffer`/`queueInputBuffer`/`dequeueOutputBuffer`), API 21+. Chỉ nhận đầu ra
     * 16-bit (mặc định của bộ giải mã AAC trên API 29); dạng khác ⇒ `null` ⇒ nhường Piper.
     */
    @Suppress("ReturnCount", "NestedBlockDepth", "TooGenericExceptionCaught")
    private fun decodeAac(file: File): Pcm? {
        if (!file.isFile) return null
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(file.absolutePath)
            var trackFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                if ((f.getString(MediaFormat.KEY_MIME) ?: "").startsWith("audio/")) {
                    extractor.selectTrack(i); trackFormat = f; break
                }
            }
            val format = trackFormat ?: return null
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
            var rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val out = ByteArrayOutputStream()
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            while (!outputDone) {
                if (!inputDone) {
                    val inIdx = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (inIdx >= 0) {
                        val buf = codec.getInputBuffer(inIdx)
                        val size = if (buf == null) -1 else extractor.readSampleData(buf, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inIdx, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val outIdx = codec.dequeueOutputBuffer(info, TIMEOUT_US)
                if (outIdx >= 0) {
                    val buf = codec.getOutputBuffer(outIdx)
                    if (buf != null && info.size > 0) {
                        val chunk = ByteArray(info.size)
                        buf.position(info.offset)
                        buf.limit(info.offset + info.size)
                        buf.get(chunk)
                        out.write(chunk)
                    }
                    codec.releaseOutputBuffer(outIdx, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                } else if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val of = codec.outputFormat
                    rate = runCatching { of.getInteger(MediaFormat.KEY_SAMPLE_RATE) }.getOrDefault(rate)
                    channels = runCatching { of.getInteger(MediaFormat.KEY_CHANNEL_COUNT) }.getOrDefault(channels)
                }
            }
            val raw = out.toByteArray()
            if (raw.isEmpty()) return null
            val shorts = ShortArray(raw.size / 2)
            ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)
            return Pcm(shorts, rate, channels)
        } catch (t: Throwable) {
            Log.w(TAG, "giải mã AAC hỏng: ${file.name}", t)
            return null
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }

    // ── tiêu điểm âm thanh (gương [SherpaTtsSpeaker]) ───────────────────────────────────────────

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
        if (ok) focus.set(req) else Log.i(TAG, "ROM từ chối tiêu điểm âm thanh — vẫn phát, không ducking")
    }

    private fun abandonFocus() {
        val req = focus.getAndSet(null) ?: return
        val am = audio() ?: return
        runCatching { am.abandonAudioFocusRequest(req) }
    }

    private companion object {
        const val TAG = "KachiVoiceClip"
        const val TIMEOUT_US = 10_000L
        const val DRAIN_POLL_MS = 20L
        const val DRAIN_MARGIN_MS = 300L
        const val MS_PER_SECOND = 1_000L
    }
}
