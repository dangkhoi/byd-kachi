package com.byd.clusternav.launcher

/**
 * ═══ A5(b) · SLOT-CORNER-ROUND (2.89) — hình cắt của khung ô (thuần) ══════════════════════════════════════════════════════
 *
 * Khung ô cắt nội dung (app trên màn ảo · widget) theo MỘT hình chữ nhật bo góc của chính nó — `SlotFrameClip` ở `:app` dựng
 * `Outline` từ đây. Lý do + nguồn AOSP: KDoc `SlotFrameClip`.
 */
object SlotFrameShape {

    /**
     * Bán kính bo thực cho khung [w]×[h] px với bán kính thiết kế [r] px: kẹp vào `[0, min(w, h) / 2]` (khung hẹp hơn hai lần
     * bán kính vẫn là viên thuốc, không bao giờ "lật" góc); `NaN` / âm ⇒ 0 (góc vuông, vẫn cắt theo mép). Khung chưa có cỡ
     * (≤ 0) ⇒ `null` = không có hình để cắt (lượt bố trí đầu — `View` gọi lại khi có cỡ).
     */
    fun radius(w: Int, h: Int, r: Float): Float? {
        if (w <= 0 || h <= 0) return null
        if (r.isNaN() || r <= 0f) return 0f
        return minOf(r, minOf(w, h) / 2f)
    }
}
