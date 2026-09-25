package com.byd.clusternav.launcher

/**
 * Một việc hẹn giờ **huỷ được** và **hỏi lại cổng ngay trước khi chạy**.
 *
 * Hardening 2026-09-25 · audit F14 [P2]: `restoreCluster()` hẹn `openProjection()` sau 2 s bằng
 * `Handler.postDelayed` trần — không giữ token, không huỷ khi người dùng gạt TẮT Cluster Cast trong 2 s ấy ⇒ hẹn
 * giờ vẫn nổ và **giành lại cụm sau khi người dùng đã tắt** (CLAUDE.md §4/§5: lệnh đổi state hệ thống phải có
 * đường hoàn tác; §5: quyết định bằng sự thật lúc chạy, không bằng cờ RAM lúc hẹn).
 *
 * Hai lá chắn, cả hai đều cần:
 *  • [cancel] — đường tắt cast gọi để rút hẹn;
 *  • `gate` — đọc lại công tắc **tại thời điểm chạy**: kể cả khi ai đó quên gọi [cancel], việc vẫn không chạy
 *    nếu cổng đã đóng.
 *
 * Chỉ giữ **một** việc chờ: hẹn lần hai thay lần một (bấm "Trả cụm" hai lần không mở chiếu hai lần).
 * THUẦN (post/remove tiêm vào) ⇒ `DelayedGatedRunTest` khoá off-device; production nối `Handler(mainLooper)`.
 */
class DelayedGatedRun(
    private val post: (Runnable, Long) -> Unit,
    private val remove: (Runnable) -> Unit,
) {
    private var pending: Runnable? = null

    @Synchronized
    fun schedule(delayMs: Long, gate: () -> Boolean, action: () -> Unit) {
        cancel()
        val r = object : Runnable {
            override fun run() {
                synchronized(this@DelayedGatedRun) {
                    if (pending !== this) return   // đã bị huỷ/thay: hẹn cũ không được chạy
                    pending = null
                }
                if (gate()) action()
            }
        }
        pending = r
        post(r, delayMs)
    }

    @Synchronized
    fun cancel() {
        pending?.let { remove(it) }
        pending = null
    }

    /** Có việc đang chờ không (cho test/hiển thị — không dùng để quyết định, xem CLAUDE.md §5). */
    @Synchronized
    fun isPending(): Boolean = pending != null
}
