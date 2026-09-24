package com.byd.clusternav.launcher.testbridge

import android.content.Context
import android.os.SystemClock
import com.byd.clusternav.launcher.voice.VoiceSpeakerRouter
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Lệnh `tts` — đo ĐƯỜNG RA TIẾNG (Piper offline) từ lúc gọi `speak` tới `onDone`.
 *
 * Profiling bước TTS: đây là nghi phạm chính của cảm giác *"phản hồi chậm"* (owner 2026-09-24), vì
 * decode+parse đã đo được là 40–160ms. Piper phải nối tiến trình `:tts` (lần đầu tốn bind) rồi tổng hợp
 * sóng — hai chi phí này chỉ đo được khi model đã nằm trên máy (`sherpa-tts/piper-vi_VN-vais1000-medium`).
 *
 * Trả `synth_ms` = thời gian từ `speak` tới `onDone` (bind lần đầu + tổng hợp). Ưu tiên offline (Piper),
 * không rơi về Android TTS, để con số phản ánh đúng đường xe dùng.
 */
internal object TestBridgeTts {
    private const val WAIT_CAP_MS = 12_000L

    fun run(ctx: Context, cmd: TestBridgeCommand, reply: TestBridgeReply) {
        val text = cmd.text
        if (text.isBlank()) { reply.fail("missing_text"); return }
        // ⚠ speak+await PHẢI ở thread NỀN: onReceive chạy trên main looper, mà onDone của Piper/Android TTS
        // post LÊN main looper — await trên chính main = deadlock, luôn timeout 12s (đã [ĐO] 2026-09-24).
        Thread {
            val router = runCatching { VoiceSpeakerRouter(ctx, preferOffline = { true }) }.getOrNull()
            if (router == null) { reply.fail("no_speaker"); return@Thread }
            val kind = runCatching { router.kind.name }.getOrDefault("?")
            val latch = CountDownLatch(1)
            val t0 = SystemClock.elapsedRealtime()
            val accepted = runCatching { router.speak(text) { latch.countDown() } }.getOrDefault(false)
            val done = latch.await(WAIT_CAP_MS, TimeUnit.MILLISECONDS)
            val synthMs = SystemClock.elapsedRealtime() - t0
            reply.ok(
                "text" to text,
                "engine" to kind,
                "accepted" to accepted,
                "done" to done,
                "synth_ms" to synthMs,
            )
        }.apply { isDaemon = true; start() }
    }
}
