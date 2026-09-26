package com.byd.clusternav.launcher.voice

import android.util.Log

/**
 * ═══ VOICE-WAKE-SESSION-OWNER (2.69) — chủ sở hữu phiên nghe của TIẾN TRÌNH `:wake` ═══════════════════════════════
 *
 * `object` = sống theo tiến trình, không theo instance `VoiceWakeService` (service có thể chết-rồi-dựng-lại trong lúc
 * một phiên "nói nốt" — review Pass 3 2.68: hai instance ⇒ hai phiên ⇒ chồng tiếng). Máy trạng thái thuần ở `:core`
 * ([VoiceSessionOwner], kiểm off-device); ở đây chỉ hai phép ĐO thật của [VoiceSession] và log.
 *
 * Mọi lượt gọi đi trên luồng main của `:wake` (`onStartCommand` · `main.post` · `standDownTask` · `onDestroy`); owner
 * có `@Synchronized` làm lưới thứ hai. `build` chỉ chạy khi thật sự cần phiên mới.
 */
internal object VoiceWakeSessions {

    private const val TAG = "WakeSessions"

    private val owner = VoiceSessionOwner<VoiceSession>(
        classify = { s ->
            when {
                s.micOpen() -> VoiceSessionOwner.Phase.LISTENING            // đo cờ `capturing` thật, không đoán từ pha
                s.phase.get() != VoiceTurnPhase.IDLE -> VoiceSessionOwner.Phase.BUSY
                else -> VoiceSessionOwner.Phase.IDLE
            }
        },
        stop = { s -> runCatching { s.stop() }.onFailure { Log.w(TAG, "stop() phiên cũ lỗi", it) } },
    )

    /**
     * ═══ [SOÁT 2.69 · P2] SỐ HIỆU PHIÊN của tiến trình — tăng mỗi lần một phiên MỚI được dựng ════════════════════
     *
     * Vì sao cần: từ 2.69 chủ sở hữu phiên ở mức TIẾN TRÌNH, còn lượt chờ đứng xuống (BG-20) vẫn là hẹn giờ của một
     * **instance service**. Ca thật: `onDestroy` giữa phiên headless ⇒ instance A hẹn `standDownTask` (mốc `T0`);
     * người lái bấm phím-thoại lần nữa ⇒ instance B + phiên MỚI. `main.removeCallbacks(standDownTask)` của B chỉ gỡ
     * Runnable **của B** (đối tượng khác), nên hẹn của A còn sống và vẫn đo `waited` từ `T0` — tới
     * [VoiceWakeStandDown.MAX_WAIT_MS] (3 phút) nó **cắt phiên đang nói của người lái** rồi nhả recognizer, vì trước
     * 2.69 `release()` chỉ chạm phiên của chính A.
     *
     * Số hiệu này để mỗi lượt chờ nhận ra "phiên đã đổi" và **đếm lại từ đầu** — đúng nghĩa mà KDoc `MAX_WAIT_MS` nói
     * (*"trần chờ MỘT phiên về IDLE"*), chứ không phải trần cho một chuỗi phiên nối đuôi. Mọi lượt đọc/ghi ở luồng
     * main của `:wake`; `@Volatile` là lưới thứ hai.
     */
    @Volatile private var epoch = 0

    fun epoch(): Int = epoch

    /** "Hey Kachi" ([VoiceWakeService.fireWake]): dùng lại / dựng mới, **không cắt** phiên đang chạy. */
    fun acquire(build: () -> VoiceSession): VoiceSession = mark(owner.acquire(preempt = false, build)).session

    /** Phím-thoại / nút mic (`ACTION_LISTEN_NOW`): bấm = muốn nói NGAY ⇒ phiên đang đọc/nán bị **cắt**, phiên đang nghe thì giữ. */
    fun preempt(build: () -> VoiceSession): VoiceSession {
        val a = mark(owner.acquire(preempt = true, build))
        if (a.preempted) Log.i(TAG, "LISTEN_NOW giữa lúc phiên cũ đang nói/nán — cắt phiên cũ, mở phiên mới")
        return a.session
    }

    /** Phiên mới (không dùng lại) ⇒ tăng [epoch]. Một chỗ duy nhất để không có đường dựng phiên nào quên tăng. */
    private fun mark(a: VoiceSessionOwner.Acquired<VoiceSession>): VoiceSessionOwner.Acquired<VoiceSession> {
        if (!a.reused) epoch++
        return a
    }

    /** Pha của phiên đang giữ (cho `VoiceWakeStandDown.decide`); không có phiên = IDLE. */
    fun phase(): VoiceTurnPhase = owner.current()?.phase?.get() ?: VoiceTurnPhase.IDLE

    /** Có phiên đang nghe hoặc đang bận không (nhánh "nói nốt" của `onDestroy`). */
    fun isRunning(): Boolean = owner.state().let { it == VoiceSessionOwner.State.LISTENING || it == VoiceSessionOwner.State.BUSY }

    /** `stop()` + bỏ tham chiếu — đứng xuống BG-20 và `onDestroy` khi phiên IDLE (nhả TTS, xem KDoc `VoiceSession.stop`). */
    fun release(): Boolean = owner.release()
}
