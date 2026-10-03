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
 *    trong cửa sổ chờ ([window]: [WINDOW_MS] = 2 s, dài hơn nếu người dùng đặt *"Thời gian thực hiện hành động"* — soát vòng 2);
 *    đầu ô giữ hiện suốt lượt chờ ([SlotHeadTouch.onConfirmArmed] + [hideAfterMs]); hết cửa sổ ⇒ về như cũ.
 *  - Chạm lần hai trong cửa sổ ⇒ [Tap.FIRE] (*tắt* thật). Quá cửa sổ ⇒ lại là lượt đầu ([Tap.ARM]).
 *  - Chạm lần hai mà DOWN của nó tới SAU UP của cú trước ít hơn nhịp nhấp đúp ([minGapMs], `ViewConfiguration.getDoubleTapTimeout`
 *    ≈ 300 ms) ⇒ [Tap.WAIT]: đó là một cú NHẤP ĐÚP (vd nhấp đúp phóng to bản đồ đúng chỗ nút), không phải người đã thấy nút
 *    đổi màu rồi xác nhận — giữ trạng thái chờ, không tắt. Soát vòng 2: khoảng đo là **DOWN₂ − UP₁** đúng như
 *    `GestureDetector.isConsideredDoubleTap` [ĐO nguồn android-10.0.0_r47: `deltaTime = secondDown.getEventTime() −
 *    firstUp.getEventTime()`] — bản trước đo click-tới-click (click nổ ở UP) nên một cú nhấp đúp thật (DOWN₂ sau UP₁ 200 ms,
 *    nhấn 120 ms ⇒ hai click cách 320 ms) vẫn TẮT. Cú chạm không đến từ ngón (trợ năng, bàn phím) ⇒ không có khoảng đo ⇒ không
 *    chặn. (Tinh chỉnh của bên sửa, đề xuất ghi spec §4.6 D-L6-6 để owner bỏ nếu không muốn.)
 *  - Mốc thời gian lùi (đồng hồ bị đặt lại) ⇒ coi như lượt đầu — không bao giờ FIRE nhờ một hiệu âm; khoảng DOWN₂ − UP₁ âm
 *    (không thể có từ một ngón) ⇒ WAIT, không FIRE trên một số đo vô nghĩa.
 *  - *Chạy nền* vẫn MỘT chạm (đảo được: app ra sau màn nhà, khởi động lại / ⇄ là về).
 */
object SlotCloseConfirm {

    /**
     * Cửa sổ chờ chạm lần hai — GỐC (quyết định điều phối: 2 s); [window] chỉ được kéo DÀI nó. Gốc phải < [SlotHeadRest.HIDE_AFTER_MS]
     * (hàng nút không ẩn giữa chừng); cửa sổ dài hơn thì [hideAfterMs] lùi lượt ẩn theo.
     */
    const val WINDOW_MS = 2_000L

    /** Lề giữa lúc hết chờ và lúc đầu ô tự ẩn — đúng nhịp hôm nay (3 s − 2 s), để ở cửa sổ gốc mọi số không đổi. */
    const val HIDE_MARGIN_MS = SlotHeadRest.HIDE_AFTER_MS - WINDOW_MS

    /**
     * Cửa sổ chờ thực của một lượt: [recommendedMs] = `AccessibilityManager.getRecommendedTimeoutMillis(WINDOW_MS, CONTROLS|ICONS)`
     * (API 29 = minSdk: trả cài đặt *"Thời gian thực hiện hành động"* của người dùng nếu DÀI hơn, không thì trả lại gốc). Không
     * bao giờ NGẮN hơn gốc 2 s.
     */
    fun window(recommendedMs: Long): Long = maxOf(WINDOW_MS, recommendedMs)

    /**
     * Hẹn ẩn đầu ô khi nó (hiện) lại: [armedLeftMs] = phần còn lại của lượt chờ xác nhận (`null` = không chờ). Không bao giờ ẩn
     * TRƯỚC khi lượt chờ hết + [HIDE_MARGIN_MS] (ẩn = gỡ lượt chờ ⇒ cửa sổ trợ năng dài sẽ bị cắt về 3 s). Không chờ / cửa sổ
     * gốc ⇒ đúng [SlotHeadRest.HIDE_AFTER_MS] như hôm nay.
     */
    fun hideAfterMs(armedLeftMs: Long?): Long =
        if (armedLeftMs == null) SlotHeadRest.HIDE_AFTER_MS else maxOf(SlotHeadRest.HIDE_AFTER_MS, armedLeftMs + HIDE_MARGIN_MS)

    enum class Tap {
        /** Vào trạng thái chờ xác nhận (chưa tắt gì). */
        ARM,

        /** Chạm lần hai quá nhanh (nhấp đúp) — giữ trạng thái chờ, không tắt. */
        WAIT,

        /** Xác nhận ⇒ tắt. */
        FIRE,
    }

    /**
     * [armedAt] = mốc lượt chạm đầu (`null` = không chờ) · [now] cùng đồng hồ đơn điệu · [downGapMs] = DOWN của cú chạm NÀY
     * trừ UP của cú chạm trước trong lượt chờ (`MotionEvent.eventTime`; `null` = cú chạm không đến từ ngón) · [minGapMs] = nhịp
     * nhấp đúp, kẹp vào `[0, WINDOW_MS / 2]` (máy đặt nhịp nhấp đúp dài bất thường thì nút vẫn xác nhận được, không thành nút
     * chết) · [windowMs] = cửa sổ của lượt ([window]; ngắn hơn gốc ⇒ dùng gốc).
     */
    fun onTap(armedAt: Long?, now: Long, downGapMs: Long?, minGapMs: Long, windowMs: Long = WINDOW_MS): Tap {
        if (armedAt == null) return Tap.ARM
        val dt = now - armedAt
        return when {
            dt < 0 || dt >= window(windowMs) -> Tap.ARM
            downGapMs != null && downGapMs < minGapMs.coerceIn(0L, WINDOW_MS / 2) -> Tap.WAIT
            else -> Tap.FIRE
        }
    }
}
