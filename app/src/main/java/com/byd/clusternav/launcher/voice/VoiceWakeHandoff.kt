package com.byd.clusternav.launcher.voice

import android.media.AudioRecord
import android.os.SystemClock
import android.util.Log

/**
 * ═══ GIAO MICRO "Hey Kachi" → phiên lệnh, KHÔNG mở lại — không hở một mẩu tiếng nào sau câu gọi ══════════════════
 *
 * VOICE-WAKE-HANDOFF-GAP (2026-10-15). [ĐO xe 15/10 18:37:32–35, 3.1.8] `WAKE khớp` 32.650 → bộ nghe `stop()` micro
 * 32.659 → phiên mở micro MỚI 35.513 ⇒ **2,9 s không ai nghe**. Người lái nói lệnh ngay sau câu gọi ⇒ phiên chỉ còn
 * nghe *"trên youtube"* ⇒ NO_VERB ⇒ *"Chưa rõ — nói lại giúp"*.
 *
 * ## Cơ chế (cùng một tiến trình `:wake` — bộ nghe và phiên lệnh sống chung, xem `VoiceWakeSessions`)
 *  1. Bộ nghe khớp câu gọi ⇒ **không** nhả micro: báo `onWake` (phiên bắt đầu dựng) rồi ĐỌC TIẾP vào một đệm (trần
 *     [MAX_PREROLL_MS]).
 *  2. Phiên tới chỗ mở micro ⇒ xin nhường như mọi lượt ([VoiceMicPreempt]) ⇒ bộ nghe thấy `yieldRequested`, **gửi**
 *     `AudioRecord` đang ghi + đệm vào đây ([park]) rồi thoát mà KHÔNG `stop/release`.
 *  3. `VoiceCapture.listenGranted` lấy ra ([takeOver]) thay cho lượt mở thiết bị mới: nạp đệm trước, rồi đọc tiếp
 *     chính `AudioRecord` ấy ⇒ một dòng tiếng liền từ ngay sau câu gọi. Phiên sở hữu nó từ đây (đóng ở `finally` của
 *     nó, như micro tự mở).
 *
 * Bất biến một-micro giữ nguyên: lúc nào cũng chỉ MỘT `AudioRecord`, và chỉ MỘT luồng đọc nó (giao qua [lock] —
 * bộ nghe đã thoát vòng đọc trước khi [park]).
 *
 * ## Không bao giờ rò micro
 * Gửi rồi mà không ai lấy trong [PARK_MAX_MS] (phiên bị huỷ / nền tảng chặn mở phiên) ⇒ luồng dọn tự `stop/release`.
 * Đệm cũ hơn [PARK_MAX_MS] cũng bị vứt ở [takeOver] (không bao giờ nạp tiếng của một câu gọi cũ vào lượt sau).
 */
internal object VoiceWakeHandoff {

    private const val TAG = "KachiWakeHandoff"

    /** Trần đệm sau câu gọi: phiên chậm nhất [ĐO xe 15/10] 2,9 s; 6 s đủ dư mà vẫn ≤ trần giữ PCM của phiên (9 s). */
    const val MAX_PREROLL_MS = 6_000L
    const val MAX_PREROLL_SAMPLES = 16_000 * 6   // [MAX_PREROLL_MS] @16 kHz

    /** Gửi rồi mà không ai lấy trong ngần này ⇒ tự đóng micro. Đệm `AudioRecord` 1 s ⇒ lấy muộn hơn 1 s thì hụt tiếng. */
    const val PARK_MAX_MS = 3_000L

    class Parked(val record: AudioRecord, val pcm: ShortArray, val n: Int, val atMs: Long)

    private val lock = Any()
    private var parked: Parked? = null

    /** Bộ nghe gửi micro đang ghi + đệm. Có món cũ chưa ai lấy ⇒ đóng món cũ (không bao giờ hai micro). */
    fun park(record: AudioRecord, pcm: ShortArray, n: Int) {
        val p = Parked(record, pcm, n, SystemClock.elapsedRealtime())
        val old = synchronized(lock) { parked.also { parked = p } }
        old?.let { close(it, "superseded") }
        Log.i(TAG, "giữ micro cho phiên lệnh: đệm ${n * 1000L / VoiceCapture.SAMPLE_RATE} ms tiếng sau câu gọi")
        Thread({
            runCatching { Thread.sleep(PARK_MAX_MS) }
            val stale = synchronized(lock) { if (parked === p) { parked = null; true } else false }
            if (stale) close(p, "no-taker-${PARK_MAX_MS}ms")
        }, "kachi-wake-handoff").apply { isDaemon = true }.start()
    }

    /** Phiên lấy micro đã giữ (hoặc `null` ⇒ mở như thường). Món quá hạn bị đóng, không giao. */
    fun takeOver(): Parked? {
        val p = synchronized(lock) { parked.also { parked = null } } ?: return null
        if (SystemClock.elapsedRealtime() - p.atMs > PARK_MAX_MS ||
            p.record.recordingState != AudioRecord.RECORDSTATE_RECORDING
        ) { close(p, "expired-or-not-recording"); return null }
        return p
    }

    /** Có món đang chờ không (để `VoiceCapture` quyết định bỏ bíp mở micro — micro đã ghi từ trước). */
    fun pending(): Boolean = synchronized(lock) { parked != null }

    private fun close(p: Parked, why: String) {
        Log.i(TAG, "đóng micro đã giữ: $why")
        runCatching { p.record.stop() }
        runCatching { p.record.release() }
    }
}
