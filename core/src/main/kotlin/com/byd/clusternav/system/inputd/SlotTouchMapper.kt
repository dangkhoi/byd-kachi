package com.byd.clusternav.system.inputd

/**
 * Ánh xạ toạ độ chạm từ hệ VIEW của ô (SurfaceView lấp đầy ô) sang hệ toạ độ của VirtualDisplay mà app render
 * lên. PURE JVM (:core) → test off-device.
 *
 * `VdAppHost` tạo VirtualDisplay ĐÚNG cỡ surface của ô (dispW×dispH = viewW×viewH) ⇒ khi [viewW]==[dispW] &&
 * [viewH]==[dispH] hàm này là ĐỒNG NHẤT (map = input). Nhờ đó đường daemon gửi CÙNG toạ độ mà fallback
 * `input -d tap` dùng, KHÔNG lệch pixel — bảo toàn hành vi khi daemon tắt. Khi display khác cỡ view (vd resize
 * theo mật độ) thì scale tuyến tính.
 */
object SlotTouchMapper {
    /**
     * Trả `[dx, dy]` trong hệ display. ĐỒNG NHẤT khi view và display cùng cỡ. Guard chia-0 (cỡ ≤ 0) → trả nguyên
     * `(viewX, viewY)`.
     */
    fun toDisplay(viewX: Int, viewY: Int, viewW: Int, viewH: Int, dispW: Int, dispH: Int): IntArray {
        if (viewW <= 0 || viewH <= 0 || dispW <= 0 || dispH <= 0) return intArrayOf(viewX, viewY)
        val dx = (viewX.toLong() * dispW / viewW).toInt()
        val dy = (viewY.toLong() * dispH / viewH).toInt()
        return intArrayOf(dx, dy)
    }
}
