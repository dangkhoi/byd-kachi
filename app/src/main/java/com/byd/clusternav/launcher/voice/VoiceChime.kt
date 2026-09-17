package com.byd.clusternav.launcher.voice

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.PI
import kotlin.math.sin

/**
 * ═══ 1.70 · ÂM BÁO đầu/cuối lượt nghe — KHÔNG BAO GIỜ chặn luồng nghe ═══════════════════════════════════════
 *
 * ## Bệnh — [ĐO xe 2026-09-17, hai lượt thử có kiểm soát của owner]
 * `ToneGenerator.startTone` trên ROM DL3 in `E/ToneGenerator: --- Immediate start timed out, status -110` và
 * **chặn đúng 3,0 s** — trên chính luồng nghe, ngay SAU khi micro mở và TRƯỚC vòng đọc. `AudioRecord` khi đó chỉ
 * có 80 ms đệm ⇒ **3 s tiếng đầu tiên rơi mất**, đúng lúc người lái nói (họ bấm rồi nói ngay). Hai tệp WAV của
 * hai lượt chính **phẳng hoàn toàn** (đỉnh 207/32767); tiếng chỉ còn ở các lượt nối (không bíp). Và owner
 * **không nghe thấy tiếng bíp nào** — cái giá 3 s không đổi lấy gì.
 *
 * ## Ba luật của tệp này
 *  1. **Luồng riêng, không chờ.** Một executor một luồng; [play] trả về ngay. Lượt nghe không biết tiếng bíp
 *     có phát được hay không, và không cần biết.
 *  2. **PCM dựng sẵn qua `AudioTrack` thường** (không `ToneGenerator`, không cờ FAST): một sóng sin ngắn có
 *     fade — đường mà loa Piper của chính app vẫn đang đi được trên ROM này ([ĐO] `AudioTrack` 22 050 Hz của
 *     máy đọc chạy bình thường trong cùng log).
 *  3. **Cầu chì cho cả tiến trình.** `AudioTrack` không khởi tạo được / `play` ném / mất > [MAX_START_MS] để
 *     bắt đầu ⇒ tắt hẳn âm báo tới hết đời tiến trình, ghi **một** dòng. Một tiếng bíp không được phép tốn
 *     hơn cái nó mang lại lần thứ hai.
 */
internal object VoiceChime {
    private const val TAG = "KachiVoiceChime"

    /** Tần số/độ dài hai tiếng: đầu lượt cao-ngắn, cuối lượt thấp-ngắn — đủ khác nhau để nghe ra "bắt đầu/thôi". */
    private const val START_HZ = 1_320.0
    private const val END_HZ = 880.0
    const val START_MS = 90
    const val END_MS = 60
    private const val RATE = 16_000
    private const val FADE_MS = 8
    private const val AMPLITUDE = 0.35

    /** Quá ngần này để `play()` bắt đầu là coi như đường ra tiếng hỏng — không bao giờ thử lại. */
    const val MAX_START_MS = 400L

    private val disabled = AtomicBoolean(false)
    private val io = Executors.newSingleThreadExecutor { r -> Thread(r, "KachiVoiceChime").apply { isDaemon = true } }

    private val startPcm: ShortArray by lazy { render(START_HZ, START_MS) }
    private val endPcm: ShortArray by lazy { render(END_HZ, END_MS) }

    /** Tiếng "tôi bắt đầu nghe". Trả về ngay. */
    fun start() = enqueue(startPcm, START_MS)

    /** Tiếng "tôi thôi nghe". Trả về ngay. */
    fun end() = enqueue(endPcm, END_MS)

    /** Cầu chì đã ngắt chưa — cho nhật ký/cầu kiểm thử; không có bề mặt người dùng. */
    fun disabled(): Boolean = disabled.get()

    private fun enqueue(pcm: ShortArray, ms: Int) {
        if (disabled.get()) return
        runCatching { io.execute { playBlocking(pcm, ms) } }
    }

    /** Chạy trên luồng riêng — chặn ở đây là chặn đúng chỗ, không ai chờ. */
    private fun playBlocking(pcm: ShortArray, ms: Int) {
        if (disabled.get()) return
        val t0 = System.currentTimeMillis()
        val track = runCatching {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(RATE)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(pcm.size * 2)
                .build()
        }.getOrNull()
        if (track == null || track.state != AudioTrack.STATE_INITIALIZED) {
            trip("AudioTrack không khởi tạo được")
            runCatching { track?.release() }
            return
        }
        val ok = runCatching {
            track.write(pcm, 0, pcm.size)
            track.play()
            true
        }.getOrDefault(false)
        val startMs = System.currentTimeMillis() - t0
        if (!ok || startMs > MAX_START_MS) {
            trip(if (!ok) "play() ném" else "play() mất $startMs ms (> $MAX_START_MS)")
            runCatching { track.release() }
            return
        }
        // Đợi đúng độ dài tiếng rồi thả — không giữ một đường ra tiếng mở vĩnh viễn (bài học ToneGenerator cũ).
        runCatching { Thread.sleep(ms.toLong() + RELEASE_PAD_MS) }
        runCatching { track.stop() }
        runCatching { track.release() }
    }

    private fun trip(why: String) {
        if (disabled.compareAndSet(false, true)) Log.w(TAG, "CẦU CHÌ âm báo: $why — tắt âm báo tới hết tiến trình")
    }

    private const val RELEASE_PAD_MS = 40L

    /** Sóng sin [hz] dài [ms] ở [RATE], fade vào/ra [FADE_MS] để không "cạch" ở hai đầu. */
    private fun render(hz: Double, ms: Int): ShortArray {
        val n = RATE * ms / 1000
        val fade = RATE * FADE_MS / 1000
        val out = ShortArray(n)
        for (i in 0 until n) {
            val env = when {
                i < fade -> i.toDouble() / fade
                i >= n - fade -> (n - 1 - i).toDouble() / fade
                else -> 1.0
            }
            out[i] = (sin(2.0 * PI * hz * i / RATE) * AMPLITUDE * env * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }
}
