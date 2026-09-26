package com.byd.clusternav.launcher.voice

/**
 * ═══ VOICE-WAKE-SESSION-OWNER (2.69) — CHỦ SỞ HỮU DUY NHẤT của phiên nghe trong một tiến trình, phần THUẦN ═══════
 *
 * [SUY, review Pass 3 2.68] `VoiceWakeService` giữ phiên trong **trường của instance service**: nhánh "để phiên nói
 * nốt" của `onDestroy` (owner duyệt) + một `listenNow` ngay sau dựng **instance service mới** với trường riêng ⇒ trong
 * vài giây `:wake` có hai phiên (phiên cũ đang đọc + phiên mới mở overlay). Mic vẫn một (`VoiceSingleFlight` tĩnh
 * cùng tiến trình) nên triệu chứng là **chồng tiếng / hai tấm chữ**. Chủ sở hữu phải ở mức TIẾN TRÌNH, không ở service.
 *
 * ## Máy trạng thái (theo phiên đang giữ, không theo cờ riêng)
 *  • [State.NONE]   — chưa có phiên ⇒ dựng.
 *  • [State.READY]  — phiên đã đóng hẳn (pha IDLE, `running` nhả) ⇒ **dùng lại** (một phiên dùng lại được; chỉ
 *    `stop()` mới là một chiều, và ai gọi `stop()` ở đây cũng phải **bỏ tham chiếu** — [release]).
 *  • [State.LISTENING] — mic đang mở chờ người lái ⇒ bấm thêm lần nữa **không đổi gì** (cùng nghĩa với in-process:
 *    `VoiceSession.start()` no-op khi `running`).
 *  • [State.BUSY]   — mic đã đóng, phiên đang giải mã / thi hành / đọc / nán ⇒ `preempt = true` (phím-thoại, nút mic:
 *    *bấm = muốn nói NGAY*) **cắt** phiên cũ rồi dựng mới; `preempt = false` ("Hey Kachi" giữa câu trả lời — có thể
 *    là false-accept, CLAUDE.md §6 không đảo đường đang chạy) ⇒ giữ phiên cũ.
 *
 * Generic theo `S` vì `VoiceSession` là lớp Android; `:app` (`VoiceWakeSessions`) truyền hai phép đo thật (`micOpen()`
 * · `phase`). Mọi lượt gọi trong `:wake` đi trên luồng main; `@Synchronized` là lưới thứ hai, không phải thiết kế.
 */
class VoiceSessionOwner<S : Any>(
    /** Phiên đang ở pha nào — đo từ chính phiên (cờ `capturing`/`phase`), không từ cờ RAM của owner. */
    private val classify: (S) -> Phase,
    /** Cắt phiên (`VoiceSession.stop()` — một chiều). Owner luôn bỏ tham chiếu ngay sau. */
    private val stop: (S) -> Unit,
) {
    /** Pha đo được của một phiên đang giữ. */
    enum class Phase { IDLE, LISTENING, BUSY }

    enum class State { NONE, READY, LISTENING, BUSY }

    /** Kết quả một lượt xin phiên — `preempted` = đã cắt phiên cũ (có log ở chỗ gọi). */
    data class Acquired<S>(val session: S, val reused: Boolean, val preempted: Boolean)

    private var current: S? = null

    @Synchronized fun current(): S? = current

    @Synchronized fun state(): State = when (current?.let(classify)) {
        null -> State.NONE
        Phase.IDLE -> State.READY
        Phase.LISTENING -> State.LISTENING
        Phase.BUSY -> State.BUSY
    }

    /**
     * Xin phiên để `start()`. Trả về phiên **đang giữ** khi dùng lại được (IDLE) hoặc khi đang nghe/đang bận mà không
     * cắt — chỗ gọi cứ `start()`: `VoiceSession` tự no-op khi `running`. Chỉ [Phase.BUSY] + `preempt` mới dựng mới.
     */
    @Synchronized fun acquire(preempt: Boolean, build: () -> S): Acquired<S> {
        val cur = current
        if (cur != null) {
            val cut = preempt && classify(cur) == Phase.BUSY
            if (!cut) return Acquired(cur, reused = true, preempted = false)
            current = null
            stop(cur)
            return Acquired(build().also { current = it }, reused = false, preempted = true)
        }
        return Acquired(build().also { current = it }, reused = false, preempted = false)
    }

    /** `stop()` + bỏ tham chiếu (đứng xuống BG-20 · `onDestroy` khi phiên IDLE). `false` = không có gì để nhả. */
    @Synchronized fun release(): Boolean {
        val cur = current ?: return false
        current = null
        stop(cur)
        return true
    }
}
