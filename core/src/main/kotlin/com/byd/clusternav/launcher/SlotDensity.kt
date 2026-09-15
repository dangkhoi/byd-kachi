package com.byd.clusternav.launcher

/**
 * ĐÒN BẨY MẬT ĐỘ cho màn ảo của ô — hàm thuần, test off-car (KHÔNG import android.*).
 *
 * ## Bệnh (spec `kachi-hal187-cast-remediation` §4.4 / R5, [ĐO xe 2026-09-15 + source AOSP r47])
 * Ô `kachi-slot-1` = 1401×748 @density 200 ⇒ `smallestScreenWidthDp = 748×160/200 = 598.4` — thiếu **1.6dp**
 * so ngưỡng tablet `sw600dp`. App có layout điện thoại/tablet tách nhau (YouTube là ca đo được) coi ô là ĐIỆN
 * THOẠI ⇒ màn phát video đòi **PORTRAIT** (fixed-orientation) ⇒ display ô đã khoá ngang không xoay được ⇒ WM
 * đẩy activity vào *Size-Compat Mode* (`ActivityRecord`: `!isResizeable() && isFixedOrientation()`) ⇒ app bị
 * thu thành dải dọc giữa ô. Ba đường khác đều trượt trên Android 10: `wm set-fix-to-user-rotation` chỉ khoá
 * DISPLAY; `force_resizable_activities` không được `isResizeable()` đọc; `wm set-ignore-orientation-request`
 * mới có từ A12.
 *
 * ## Cơ chế
 * Hạ `densityDpi` của màn ảo (truyền NGAY lúc `createVirtualDisplay`/`resize`) sao cho cạnh NGẮN quy ra dp
 * **≥ [thresholdDp]** ⇒ app nhận cấu hình tablet ⇒ không đòi portrait ⇒ không rơi size-compat. Generic: tính từ
 * kích thước ô, không hỏi tên gói, không hỏi "to hay bé". `smallestScreenWidthDp` là min qua mọi hướng xoay
 * (`DisplayContent.computeSizeRangesAndScreenLayout`, r47) nên chỉ cạnh ngắn quyết định.
 *
 * ## Kỳ vọng — nói thẳng (CLAUDE §2)
 * App **đầy khung, hết dải dọc**. Nhưng khung video 16:9 chỉ phủ kín pixel ở *fullscreen player* (một cú tap
 * của user; không ép generic được). Mọi thứ trong ô sẽ hiện **bé hơn một chút** (200→199 gần như không thấy;
 * ô thấp hơn thì hạ sâu hơn).
 *
 * ## Số ví dụ (`defaultDpi = 200`, ngưỡng 600dp)
 *  - cạnh ngắn 748 ⇒ `floor(748×160/600) = 199` ⇒ 601dp (đủ). Trên xe đã đo tay `wm density 192` = 623dp cũng
 *    đạt; công thức chọn mức hạ **ít nhất** đủ qua ngưỡng để app hiện gần cỡ cũ nhất.
 *  - cạnh ngắn 458 ⇒ 122 ⇒ 600.6dp (đủ, sát sàn 120).
 *  - cạnh ngắn < 450 ⇒ mục tiêu < [floorDpi] 120 ⇒ **giữ [defaultDpi]** (KHÔNG hạ). Lý do: dưới 120dpi mọi thứ
 *    bé li ti mà vẫn *không* đạt tablet ⇒ tệ hơn giữ nguyên. Giữ hành vi cũ = không tệ hơn.
 *  - đã ≥ 600dp ở density mặc định ⇒ giữ nguyên (không đụng ô đang tốt, CLAUDE §6).
 */
object SlotDensity {
    /** Hằng của Android (`DisplayMetrics.DENSITY_DEFAULT`): 1dp = 1px ở 160dpi. */
    const val DENSITY_DEFAULT = 160

    /**
     * Ngưỡng qualifier cấu hình tablet của Android (`sw600dp`, `Configuration.smallestScreenWidthDp`). Đây là
     * ngưỡng PHÂN LOẠI THIẾT BỊ, không phải khoảng cách bố cục ⇒ không thuộc thang `KachiSpace` (bài canh
     * `SpacingScaleContractTest.core khong giu so dp` chỉ nhắm hằng bố cục `*_DP`).
     */
    const val TABLET_SW_QUALIFIER = 600

    /** Sàn mật độ: dưới mức này UI bé đến mức vô dụng, không đáng đổi. */
    const val FLOOR_DPI = 120

    /**
     * Mật độ nên đặt cho màn ảo có cạnh ngắn [shortSidePx] để cạnh đó quy ra ≥ [thresholdDp].
     *
     * Bất biến: kết quả **luôn ≤ [defaultDpi]** (chỉ hạ, không nâng) và **≥ [floorDpi]** hoặc = [defaultDpi].
     * Đầu vào không hợp lệ (`shortSidePx ≤ 0`, `defaultDpi ≤ 0`) ⇒ trả [defaultDpi] (fail-safe: không đổi gì).
     */
    fun forTablet(
        shortSidePx: Int,
        defaultDpi: Int,
        floorDpi: Int = FLOOR_DPI,
        thresholdDp: Int = TABLET_SW_QUALIFIER,
    ): Int {
        if (shortSidePx <= 0 || defaultDpi <= 0 || thresholdDp <= 0) return defaultDpi
        if (dpOf(shortSidePx, defaultDpi) >= thresholdDp) return defaultDpi        // ô đã là "tablet" — giữ nguyên
        val target = shortSidePx * DENSITY_DEFAULT / thresholdDp                   // floor (int division, dương)
        if (target < floorDpi) return defaultDpi                                     // không thể đạt ⇒ không hạ
        return minOf(target, defaultDpi)
    }

    /** Cạnh [px] quy ra dp ở [dpi] — cùng phép tính WM dùng (`px / (dpi/160)`), giữ số thực để test đọc được. */
    fun dpOf(px: Int, dpi: Int): Double = px * DENSITY_DEFAULT.toDouble() / dpi
}
