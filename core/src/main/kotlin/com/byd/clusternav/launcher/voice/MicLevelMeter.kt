package com.byd.clusternav.launcher.voice

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Đo mức tín hiệu của MỘT lượt nghe — đỉnh biên độ + RMS (thang 0..32767) + số mẫu.
 *
 * [ĐO bug voice 2026-09-15] chốt "câm / không nghe được" ngay trong một lượt nói: đỉnh gần 0 ⇒ mic câm; đỉnh kịch
 * 32767 liên tục ⇒ méo/clip; đỉnh vừa mà bộ nhận dạng ra rỗng ⇒ định dạng. Tách khỏi `VoiceCapture` (2026-09-25,
 * trần 500 dòng) — thuần Kotlin, test off-car; `:app` chỉ in [line] ra logcat.
 */
class MicLevelMeter(private val sampleRate: Int = 16_000) {
    var peak: Int = 0
        private set
    private var sumSq = 0.0
    var samples: Long = 0
        private set

    /** Nạp [n] mẫu đầu của [buf] (PCM 16-bit mono); trả RMS của riêng khúc này (cho bộ ngắt câu + waveform). */
    fun feed(buf: ShortArray, n: Int): Int {
        var chunkSq = 0.0
        for (i in 0 until n) {
            val a = abs(buf[i].toInt())
            if (a > peak) peak = a
            chunkSq += a.toDouble() * a
        }
        sumSq += chunkSq
        samples += n
        return if (n > 0) sqrt(chunkSq / n).toInt() else 0
    }

    val rms: Int get() = if (samples > 0) sqrt(sumSq / samples).toInt() else 0

    /** Một dòng đọc được ngay trong logcat trên xe. */
    fun line(): String = "mức micro: đỉnh $peak/32767 · rms $rms · $samples mẫu (${samples * 1000 / sampleRate}ms)"
}
