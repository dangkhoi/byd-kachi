package com.byd.clusternav.launcher.testbridge

import android.content.Context
import android.os.SystemClock
import com.byd.clusternav.launcher.voice.VoiceWakeKws
import com.byd.clusternav.launcher.voice.VoiceWavProbe
import com.byd.clusternav.launcher.voice.WakeModelCatalog
import java.io.File

/**
 * Lệnh `kws` — feed WAV qua ĐÚNG bộ nghe wake mặc định (`KeywordSpotter`, streaming) rồi báo có nổ không.
 *
 * KHÁC `wav` (dùng ASR tự-do làm fallback): đây là engine THẬT chạy trên máy khi bật "Hey Kachi". Đo đúng
 * thứ owner nghe: KWS bắt được không + sau bao nhiêu ms tiếng (fed cumulative). Dùng để tune ngưỡng
 * `keywords.txt` (#threshold / :boost) mà không phải nói tay trên xe.
 */
internal object TestBridgeKws {
    private const val CHUNK = 1600  // 100ms @16k — như luồng nghe thật cấp cho feed

    fun run(app: Context, cmd: TestBridgeCommand, reply: TestBridgeReply) {
        val err = stage(app, cmd.path)
        if (err != null) { reply.fail(err, "path" to cmd.path); return }
        Thread {
            val dir = File(app.filesDir, WakeModelCatalog.dir)
            val kws = VoiceWakeKws.build(
                dir, WakeModelCatalog.ENCODER, WakeModelCatalog.DECODER, WakeModelCatalog.JOINER,
                WakeModelCatalog.TOKENS, keywordsFileName = WakeModelCatalog.KEYWORDS,
            )
            if (kws == null) { reply.fail("no_kws_model"); return@Thread }
            try {
                val (pcm, n) = VoiceWavProbe.readPcmFile(File(app.getExternalFilesDir(null) ?: app.filesDir, VoiceWavProbe.FILE_NAME))
                val t0 = SystemClock.elapsedRealtime()
                var fired = false; var firedAtMs = -1L; var fedMs = 0L
                var i = 0
                while (i < n) {
                    val take = minOf(CHUNK, n - i)
                    val chunk = FloatArray(take) { pcm[i + it].toFloat() / 32768f }
                    fedMs += take * 1000L / 16000
                    if (kws.feed(chunk, take)) { fired = true; firedAtMs = fedMs; break }
                    i += take
                }
                reply.ok(
                    "wake" to fired,
                    "fired_at_audio_ms" to firedAtMs,   // vị trí trong tiếng (ms) mà KWS nổ
                    "compute_ms" to (SystemClock.elapsedRealtime() - t0),
                    "samples" to n,
                )
            } finally { kws.release() }
        }.apply { isDaemon = true; start() }
    }

    private fun stage(app: Context, path: String): String? {
        if (path.isBlank()) return null
        if (path.contains("..")) return "bad_path"
        val src = File(path)
        if (!src.isFile || !src.canRead()) return "wav_not_found"
        val dir = app.getExternalFilesDir(null) ?: app.filesDir
        return runCatching { src.copyTo(File(dir, VoiceWavProbe.FILE_NAME), overwrite = true); null }
            .getOrDefault("stage_failed")
    }
}
