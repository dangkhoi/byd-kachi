package com.byd.clusternav.launcher.testbridge

import android.content.Context
import android.os.Process
import android.os.SystemClock
import com.byd.clusternav.launcher.voice.VoiceWakeAsr
import com.byd.clusternav.launcher.voice.VoiceWakeController
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
 *
 * ## `--es name asr` / `asr-base` — engine ASR cửa-sổ ĐANG SHIP, qua đúng [VoiceWakeController] (VOICE-WAKE-CPU-BUDGET)
 * Đẩy WAV theo khung 100 ms qua `onFrame` → `feed`/`observe` → `onInferenceCost` y như `VoiceWakeListener.inner`,
 * đồng hồ = **thời lượng tiếng đã nạp** (chạy nhanh hơn thời gian thực mà ngân sách vẫn tính theo tiếng thật).
 *  • `asr` = bản ship: cổng tiếng người (VAD) + ngân sách CPU.
 *  • `asr-base` = trước bản vá: chỉ cổng RMS + trần cũ 40 khung / 10 s (dựng lại tại chỗ để so trên CÙNG bộ WAV).
 *  • `--ei v <ms>` = tính mỗi lượt GIẢI MÃ đúng `<ms>` CPU thay cho số đo máy ảo (máy ảo nhanh hơn xe nhiều lần ⇒
 *    muốn thấy ngân sách cắt ở giá xe thì bơm giá xe, vd 1350 — [ĐO xe 15/10]). Không có ⇒ giá đo thật.
 */
internal object TestBridgeKws {
    private const val CHUNK = 1600  // 100ms @16k — như luồng nghe thật cấp cho feed

    fun run(app: Context, cmd: TestBridgeCommand, reply: TestBridgeReply) {
        val err = stage(app, cmd.path)
        if (err != null) { reply.fail(err, "path" to cmd.path); return }
        if (cmd.arg == ASR || cmd.arg == ASR_BASE) { Thread { runAsr(app, cmd, reply) }.apply { isDaemon = true; start() }; return }
        Thread {
            val dir = File(app.filesDir, WakeModelCatalog.dir)
            val kws = VoiceWakeKws.build(
                dir, WakeModelCatalog.ENCODER, WakeModelCatalog.DECODER, WakeModelCatalog.JOINER,
                WakeModelCatalog.TOKENS, keywordsFileName = WakeModelCatalog.KEYWORDS,
            )
            if (kws == null) { reply.fail("no_kws_model"); return@Thread }
            try {
                val (pcm, n) = VoiceWavProbe.readPcmFile(staged(app))
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

    private fun runAsr(app: Context, cmd: TestBridgeCommand, reply: TestBridgeReply) {
        val base = cmd.arg == ASR_BASE
        val asr = VoiceWakeAsr.create(app, speechGate = !base) ?: run { reply.fail("no_asr_model"); return }
        try {
            val (pcm, n) = VoiceWavProbe.readPcmFile(staged(app), ASR_MAX_SAMPLES)
            val ctl = if (base) VoiceWakeController(tailMs = 0L) else VoiceWakeController()   // base: không đuôi câu
            val oldCap = ArrayDeque<Long>()   // asr-base: trần khung cũ (40 / 10 s) — bản sao tại chỗ để so
            val t0 = SystemClock.elapsedRealtime()
            var fires = 0; var firstFire = -1L; var run = 0; var obs = 0; var idle = 0; var fin = 0; var cpu = 0L
            var i = 0
            while (i < n) {
                val take = minOf(CHUNK, n - i)
                var sum = 0.0
                for (k in 0 until take) { val s = pcm[i + k].toDouble(); sum += s * s }
                val now = (i + take) * 1000L / 16000
                val chunk = FloatArray(take) { pcm[i + it] / 32768f }
                var frame = ctl.onFrame(Math.sqrt(sum / take), 0.0, now)
                if (base && frame == VoiceWakeController.Frame.FINISH) frame = VoiceWakeController.Frame.IDLE   // trước bản vá: không có đuôi
                if (base && frame != VoiceWakeController.Frame.IDLE) {
                    while (oldCap.isNotEmpty() && now - oldCap.first() >= 10_000L) oldCap.removeFirst()
                    frame = if (oldCap.size >= 40) VoiceWakeController.Frame.IDLE
                    else { oldCap.addLast(now); VoiceWakeController.Frame.RUN_KWS }
                }
                val c0 = Process.getElapsedCpuTime(); val d0 = asr.decodes
                var hit = false
                when (frame) {
                    VoiceWakeController.Frame.RUN_KWS -> { run++; hit = asr.feed(chunk, take) }
                    VoiceWakeController.Frame.OBSERVE -> { obs++; asr.observe(chunk, take) }
                    VoiceWakeController.Frame.FINISH -> { fin++; hit = asr.finish() }
                    else -> idle++
                }
                val measured = Process.getElapsedCpuTime() - c0
                cpu += measured
                val fake = cmd.v
                ctl.onInferenceCost(if (fake != null && asr.decodes > d0) fake.toLong() else measured, now)
                if (hit && ctl.onKwsResult(true, now) == VoiceWakeController.Wake.FIRE) { fires++; if (firstFire < 0) firstFire = now }
                i += take
            }
            reply.ok(
                "engine" to cmd.arg,
                "wake" to (fires > 0),
                "fires" to fires,
                "fired_at_audio_ms" to firstFire,
                "decodes" to asr.decodes,
                "skipped_no_speech" to asr.skippedNoSpeech,
                "skipped_quiet" to asr.skippedQuiet,
                "frames_run" to run, "frames_observe" to obs, "frames_idle" to idle, "frames_finish" to fin,
                "finish_decodes" to asr.finishes,
                "cpu_ms" to cpu,                       // CPU đo thật trên máy này (cả tiến trình)
                "charged_ms" to ctl.chargedCpuMs(),    // CPU ngân sách đã tính (= cpu_ms, hoặc giá giả `v`)
                "audio_ms" to n * 1000L / 16000,
                "compute_ms" to (SystemClock.elapsedRealtime() - t0),
            )
        } finally { asr.release() }
    }

    private const val ASR = "asr"
    private const val ASR_BASE = "asr-base"
    private const val ASR_MAX_SAMPLES = 16_000 * 60   // 60 s — đủ để ngân sách tới pha ổn định (xô 16 s)

    /** Tệp WAV đã chép vào bởi [stage] — MỘT chỗ đọc cho cả hai engine (bài canh `DiagStorageCapWiringContractTest`). */
    private fun staged(app: Context): File = File(app.getExternalFilesDir(null) ?: app.filesDir, VoiceWavProbe.FILE_NAME)

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
