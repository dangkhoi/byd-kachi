package com.byd.clusternav.launcher.camera

import com.byd.clusternav.launcher.camera.CameraSignalPolicy.Turn

/**
 * Giữ camera qua pha TẮT của xi-nhan nhấp nháy — THUẦN (không Android), test off-car với đồng hồ giả.
 *
 * ⚠ [ĐO xe 2026-09-24] Xi-nhan NHẤP NHÁY (~1.5 Hz, sáng/tắt ~340 ms). Nhìn pha TẮT mà đóng camera thì camera nháy
 * theo đèn. Giữ MỐC lần thấy ON gần nhất mỗi bên; bên nào ON trong [holdMs] (> chu kỳ nháy) thì coi như ĐANG bật;
 * chỉ đóng khi không thấy ON quá [holdMs] (tắt hẳn).
 *
 * BG-13 (2026-09-25): trước đây vòng automation quay 250 ms chỉ để gọi `observe(null, null)` cho HOLD hết hạn.
 * [expiresInMs] cho biết ĐÚNG mốc hết hạn gần nhất ⇒ `CameraSignalController` hẹn một `postDelayed` sau mỗi sự
 * kiện thay vì poll. Hành vi tắt KHÔNG đổi: tắt sau [holdMs] kể từ ON cuối.
 */
class CameraHold(private val holdMs: Long = HOLD_MS) {
    private var lastLeftOnMs = NEVER
    private var lastRightOnMs = NEVER

    /** Ghi nhận trạng thái (null = không có tin) và trả bên đang giữ tại [nowMs]. */
    fun observe(left: Boolean?, right: Boolean?, nowMs: Long): Turn {
        if (left == true) lastLeftOnMs = nowMs
        if (right == true) lastRightOnMs = nowMs
        return CameraSignalPolicy.turnOf(held(lastLeftOnMs, nowMs), held(lastRightOnMs, nowMs))
    }

    /**
     * Số ms tới mốc hết hạn GẦN NHẤT của các bên đang giữ (≥ 1 — gọi lại `observe(null,null)` lúc đó thì bên ấy
     * đã hết hạn), `null` khi không bên nào đang giữ (không có gì để hẹn).
     */
    fun expiresInMs(nowMs: Long): Long? {
        val candidates = listOf(lastLeftOnMs, lastRightOnMs)
            .filter { held(it, nowMs) }
            .map { it + holdMs - nowMs + 1 }
        return candidates.minOrNull()
    }

    fun reset() {
        lastLeftOnMs = NEVER
        lastRightOnMs = NEVER
    }

    private fun held(lastOnMs: Long, nowMs: Long): Boolean = lastOnMs != NEVER && nowMs - lastOnMs <= holdMs

    companion object {
        /** > chu kỳ nháy (~700 ms) đủ để một lần nháy không đóng; đủ ngắn để tắt hẳn xi-nhan thì camera đóng nhanh. [ĐO xe: nháy ~1.5 Hz]. */
        const val HOLD_MS = 1_200L
        private const val NEVER = Long.MIN_VALUE
    }
}
