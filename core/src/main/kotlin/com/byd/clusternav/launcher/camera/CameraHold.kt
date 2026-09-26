package com.byd.clusternav.launcher.camera

import com.byd.clusternav.launcher.camera.CameraSignalPolicy.Turn

/**
 * Quyết định bên xi-nhan nào đang "bật" theo dòng sự kiện của nguồn tin — THUẦN (không Android), test off-car với
 * đồng hồ giả.
 *
 * ## Luật (2.70) — chịu được CẢ HAI kiểu nguồn
 * 1. **Trạng thái** [ĐO xe 2026-09-26, 2.69]: helper HAL báo MỘT `trái=true` lúc bật và MỘT `trái=false` lúc tắt
 *    (log `KachiHalSignal`, cách nhau 4,2 s), không nháy theo bóng ⇒ bên nào đang ở trạng thái ON (chưa thấy OFF)
 *    thì **GIỮ, không hết hạn**. Luật cũ "ON trong [holdMs]" đóng camera sau 1,2 s dù đèn còn bật (owner: *"camera
 *    lên 1-2s là bị tắt"*).
 * 2. **Nhấp nháy** [ĐO xe 2026-09-24]: nguồn báo cả hai pha của bóng (~1.5 Hz, sáng/tắt ~340 ms). Nhìn pha TẮT mà
 *    đóng thì camera nháy theo đèn ⇒ sau MỘT sự kiện OFF vẫn giữ thêm [holdMs] (> chu kỳ nháy) kể từ ON cuối.
 *
 * Hai luật gộp lại: [heldSide] = `đang ON` **hoặc** `ON cuối còn trong [holdMs]`. Nguồn trạng thái ⇒ OFF cách ON
 * cuối 4,2 s > [holdMs] ⇒ đóng NGAY khi đèn tắt hẳn. Nguồn nháy ⇒ pha OFF cách ON cuối ~170 ms ⇒ giữ.
 *
 * ⚠ Hệ quả của luật 1: **nguồn tin phải báo được OFF**. Nếu nguồn chết giữa lúc ON thì không bên nào hết hạn ⇒
 * camera treo. Lưới an toàn nằm ở tầng vận chuyển: `HalSignalClient.announceOffIfDropped` báo `(false,false)` khi
 * đứt dây (soát Pass 2 · `HalSignalClientDropTest`).
 *
 * BG-13 (2026-09-25): trước đây vòng automation quay 250 ms chỉ để gọi `observe(null, null)` cho HOLD hết hạn.
 * [expiresInMs] cho biết ĐÚNG mốc hết hạn gần nhất ⇒ `CameraSignalController` hẹn một `postDelayed` sau mỗi sự
 * kiện thay vì poll; bên đang ON không có mốc nào để hẹn (đợi sự kiện OFF), nên cũng không có nhịp nào.
 */
class CameraHold(private val holdMs: Long = HOLD_MS) {
    private var lastLeftOnMs = NEVER
    private var lastRightOnMs = NEVER
    // [ĐO xe 2026-09-26, 2.69] HAL helper báo xi-nhan theo TRẠNG THÁI đèn báo: MỘT sự kiện ON khi bật, MỘT sự kiện OFF khi
    // tắt (log `KachiHalSignal: trái=true` … 4,2 s sau `trái=false`), KHÔNG nháy theo bóng. Với luật cũ "ON trong
    // HOLD_MS" camera đóng sau 1,2 s dù đèn còn bật (owner: "camera lên 1-2s là bị tắt"). Luật đúng cho CẢ hai kiểu
    // nguồn: bên nào đang ở trạng thái ON (chưa thấy OFF) thì GIỮ; HOLD_MS chỉ nối khoảng tắt của nháy SAU một OFF.
    private var leftOn = false
    private var rightOn = false

    /** Ghi nhận trạng thái (null = không có tin) và trả bên đang giữ tại [nowMs]. */
    fun observe(left: Boolean?, right: Boolean?, nowMs: Long): Turn {
        if (left == true) lastLeftOnMs = nowMs
        if (right == true) lastRightOnMs = nowMs
        if (left != null) leftOn = left
        if (right != null) rightOn = right
        return CameraSignalPolicy.turnOf(heldSide(leftOn, lastLeftOnMs, nowMs), heldSide(rightOn, lastRightOnMs, nowMs))
    }

    /**
     * Số ms tới mốc hết hạn GẦN NHẤT của các bên đang giữ (≥ 1 — gọi lại `observe(null,null)` lúc đó thì bên ấy
     * đã hết hạn), `null` khi không bên nào đang giữ (không có gì để hẹn).
     */
    fun expiresInMs(nowMs: Long): Long? {
        // Bên đang ON không có mốc hết hạn (đợi sự kiện OFF); chỉ bên đã OFF mà còn trong HOLD mới hẹn.
        val candidates = listOf(leftOn to lastLeftOnMs, rightOn to lastRightOnMs)
            .filter { (on, last) -> !on && held(last, nowMs) }
            .map { (_, last) -> last + holdMs - nowMs + 1 }
        return candidates.minOrNull()
    }

    fun reset() {
        lastLeftOnMs = NEVER
        lastRightOnMs = NEVER
        leftOn = false
        rightOn = false
    }

    /** Đang ON (chưa thấy OFF) ⇒ giữ; đã OFF ⇒ còn giữ nếu ON cuối trong [holdMs] (khoảng tắt của nháy). */
    private fun heldSide(on: Boolean, lastOnMs: Long, nowMs: Long): Boolean = on || held(lastOnMs, nowMs)

    private fun held(lastOnMs: Long, nowMs: Long): Boolean = lastOnMs != NEVER && nowMs - lastOnMs <= holdMs

    companion object {
        /** > chu kỳ nháy (~700 ms) đủ để một lần nháy không đóng; đủ ngắn để tắt hẳn xi-nhan thì camera đóng nhanh. [ĐO xe: nháy ~1.5 Hz]. */
        const val HOLD_MS = 1_200L
        private const val NEVER = Long.MIN_VALUE
    }
}
