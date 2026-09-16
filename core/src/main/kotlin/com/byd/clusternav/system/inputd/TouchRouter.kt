package com.byd.clusternav.system.inputd

/**
 * Quyết định định tuyến chạm + lệnh FALLBACK. PURE JVM (:core) → test off-device + golden-lock chuỗi fallback.
 *
 * Đường ưu tiên = input-daemon (socket, `InputDaemonClient.sendTouch`). Nếu daemon KHÔNG sẵn (chưa lên / rớt),
 * `VdAppHost` FALLBACK về ĐÚNG lệnh cũ `input -d <display> tap x y` — [fallbackTapCmd] khoá byte chuỗi này để nó
 * GIỐNG HỆT hành vi trước B4 ⇒ KHÔNG hồi quy chạm khi daemon tắt.
 */
object TouchRouter {
    /**
     * Lệnh tap fallback — BYTE-KHỚP chuỗi `VdAppHost` phát trước B4: `input -d <displayId> tap <x> <y>`
     * (với `x = e.x.toInt()`, `y = e.y.toInt()`). ĐỪNG đổi format — golden test khoá byte tại đây.
     */
    fun fallbackTapCmd(displayId: Int, x: Int, y: Int): String = "input -d $displayId tap $x $y"

    /**
     * Lệnh VUỐT của đường lùi (1.69) — `input -d <displayId> swipe <x0> <y0> <x1> <y1> <ms>`.
     *
     * Đây là thứ đường lùi trước 1.69 **không có**, và vì thế mọi cú cuộn trong ô đều rơi mất ([ĐO xe 2026-09-16]
     * §9.1: `input swipe` vào ô YouTube ⇒ app nhận một cú tap). Ai quyết định lúc nào phát lệnh này là
     * [GestureFallback]; ở đây chỉ có **cách viết chuỗi**, để cả hai dạng lệnh của đường lùi nằm CÙNG một chỗ và
     * cùng được golden-lock (bản sao thứ hai của một chuỗi lệnh là bản sẽ lệch).
     *
     * `input swipe` cùng điểm đầu-cuối = một cú **giữ** — đó là cách duy nhất `input` diễn đạt long-press.
     */
    fun fallbackSwipeCmd(displayId: Int, x0: Int, y0: Int, x1: Int, y1: Int, durationMs: Long): String =
        "input -d $displayId swipe $x0 $y0 $x1 $y1 $durationMs"

    /** true ⇒ caller PHẢI chạy [fallbackTapCmd] vì daemon KHÔNG nhận sự kiện này ([daemonRouted] = false). */
    fun shouldFallback(daemonRouted: Boolean): Boolean = !daemonRouted
}
