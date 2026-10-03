package com.byd.clusternav.navigation

/**
 * ═══ FIX286 R-HUD (S9) — mốc phiên làn AMAP theo ĐỒNG HỒ ĐƠN ĐIỆU (thuần, `:core`) ══════════════════════════════════
 *
 * `ClusterBroadcaster.emitLane` bỏ khung "cũ hơn lúc mở phiên" để khung lưu từ lượt trước (rehydrate) không chen vào
 * phiên mới. Trước 2.86 phép so là `updatedAt < sessionSelectedAtEpochMs` — CẢ HAI là giờ tường. Đồng hồ xe lùi (đồng bộ
 * GPS/mạng) sau khi phiên mở ⇒ MỌI khung mới có giờ tường nhỏ hơn mốc ⇒ bị bỏ tới khi đồng hồ đuổi kịp: đường AmapService
 * câm trong khi đường HAL vẫn ghi (phản biện HUD H7b, [ĐO mã] `ClusterBroadcaster.kt:65-89` gốc `c45d326`).
 *
 * Sửa: mốc phiên giữ thêm giờ ĐƠN ĐIỆU lúc mở; lúc so, độ lệch giờ tường so với giờ đơn điệu kể từ lúc mở
 * (`wallDrift`) — nếu ÂM (đồng hồ tường đã lùi) — dời ngưỡng xuống đúng chừng đó. Không lệch / lệch dương ⇒ ngưỡng
 * = giờ tường lúc mở = ĐÚNG phép so cũ (không đổi hành vi khi đồng hồ đứng yên — CLAUDE.md §6). Nhiễu làm tròn ms giữa
 * hai đồng hồ chỉ có thể làm ngưỡng THẤP hơn ⇒ khung hợp lệ không bao giờ bị bỏ oan.
 */
object LaneSessionClock {

    /**
     * @param frameWallMs giờ tường của khung (`NavState.updatedAt`); `<= 0` = không có mốc ⇒ không bỏ.
     * @param startWallMs / [startElapsedMs] giờ tường / đơn điệu lúc mở phiên (cùng một khoảnh khắc).
     * @param nowWallMs / [nowElapsedMs] giờ tường / đơn điệu lúc so.
     * @return `true` ⇔ khung thuộc về TRƯỚC lúc mở phiên ⇒ bỏ.
     */
    fun isBeforeSession(
        frameWallMs: Long,
        startWallMs: Long,
        startElapsedMs: Long,
        nowWallMs: Long,
        nowElapsedMs: Long,
    ): Boolean {
        if (frameWallMs <= 0L) return false
        val wallDrift = (nowWallMs - startWallMs) - (nowElapsedMs - startElapsedMs)
        return frameWallMs < startWallMs + minOf(0L, wallDrift)
    }
}
