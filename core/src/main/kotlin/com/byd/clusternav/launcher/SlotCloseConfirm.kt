package com.byd.clusternav.launcher

/**
 * ═══ L6 · *tắt* app/widget ở đầu ô = HAI BƯỚC (soát 2.87 · P2, quyết định điều phối) — thuần, `:core` ══════════════════
 *
 * Vì sao: sau mỗi cú chạm vào app trong ô, hàng đầu ô hiện ra 3 s ([SlotHeadTouch]) và nút *tắt* nằm ngay cạnh ⇄ ở giữa-mép
 * trên — đúng chỗ ô tìm kiếm của Google Maps. *Tắt* app là `am stack remove` (không hoàn tác được — KDoc `SlotClosePlan`);
 * trước L6 cú chạm nhầm tệ nhất chỉ mở bảng chọn. Một chạm nhầm KHÔNG được gỡ app khỏi ô lúc đang lái.
 *
 * ## Luật
 *  - Chạm lần đầu ⇒ [Tap.ARM]: nút đổi sang trạng thái xác nhận (đĩa đỏ + mô tả trợ năng *"Chạm lần nữa để tắt"* đủ 5 tiếng)
 *    trong [WINDOW_MS] = 2 s; đầu ô giữ hiện suốt lượt chờ ([SlotHeadTouch.onConfirmArmed]); hết 2 s ⇒ về như cũ.
 *  - Chạm lần hai trong 2 s ⇒ [Tap.FIRE] (*tắt* thật). Quá 2 s ⇒ lại là lượt đầu ([Tap.ARM]).
 *  - Chạm lần hai NHANH hơn nhịp nhấp đúp của máy ([minGapMs], `ViewConfiguration.getDoubleTapTimeout` ≈ 300 ms) ⇒ [Tap.WAIT]:
 *    đó là một cú NHẤP ĐÚP (vd nhấp đúp phóng to bản đồ đúng chỗ nút), không phải người đã thấy nút đổi màu rồi xác nhận —
 *    giữ trạng thái chờ, không tắt. (Tinh chỉnh của bên sửa, ghi ở spec §4.6 D-L6-6 để owner bỏ nếu không muốn.)
 *  - Mốc thời gian lùi (đồng hồ bị đặt lại) ⇒ coi như lượt đầu — không bao giờ FIRE nhờ một hiệu âm.
 *  - *Chạy nền* vẫn MỘT chạm (đảo được: app ra sau màn nhà, khởi động lại / ⇄ là về).
 */
object SlotCloseConfirm {

    /** Cửa sổ chờ chạm lần hai (quyết định điều phối: 2 s). Phải < [SlotHeadRest.HIDE_AFTER_MS] (hàng nút không ẩn giữa chừng). */
    const val WINDOW_MS = 2_000L

    enum class Tap {
        /** Vào trạng thái chờ xác nhận (chưa tắt gì). */
        ARM,

        /** Chạm lần hai quá nhanh (nhấp đúp) — giữ trạng thái chờ, không tắt. */
        WAIT,

        /** Xác nhận ⇒ tắt. */
        FIRE,
    }

    /**
     * [armedAt] = mốc lượt chạm đầu (`null` = không chờ) · [now] cùng đồng hồ đơn điệu · [minGapMs] = nhịp nhấp đúp, kẹp vào
     * `[0, WINDOW_MS / 2]` (máy đặt nhịp nhấp đúp dài bất thường thì nút vẫn xác nhận được, không thành nút chết).
     */
    fun onTap(armedAt: Long?, now: Long, minGapMs: Long): Tap {
        if (armedAt == null) return Tap.ARM
        val dt = now - armedAt
        return when {
            dt < 0 || dt >= WINDOW_MS -> Tap.ARM
            dt < minGapMs.coerceIn(0L, WINDOW_MS / 2) -> Tap.WAIT
            else -> Tap.FIRE
        }
    }
}
