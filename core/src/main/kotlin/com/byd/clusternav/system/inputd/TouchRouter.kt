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

    /** true ⇒ caller PHẢI chạy [fallbackTapCmd] vì daemon KHÔNG nhận sự kiện này ([daemonRouted] = false). */
    fun shouldFallback(daemonRouted: Boolean): Boolean = !daemonRouted
}
