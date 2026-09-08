package com.byd.clusternav.navigation

/**
 * Hysteresis GIỮ HƯỚNG RẼ chống nháy HUD (2026-08-15, owner báo "HUD kính lái nháy khi có noti GMaps, lâu lâu bị").
 *
 * VÌ SAO: mỗi noti GMaps được phân loại hướng rẽ LẠI TỪ ĐẦU (small-icon → chữ ký large-icon → verb chữ →
 * ArrowClassifier). Thỉnh thoảng MỘT noti không đọc được (GMaps không đính large-icon ở bản tin đó / chữ ký
 * lệch ngưỡng / không có động từ) → phân loại ra `null`. Trước đây null → maneuver null → HUD encode `?: 11`
 * = ĐI THẲNG. Noti kế đọc lại đúng → mũi tên nháy "rẽ → thẳng → rẽ".
 *
 * CÁCH SỬA: khi frame hiện tại KHÔNG phân loại được, GIỮ hướng rẽ hợp lệ gần nhất thay vì rớt về -1/straight.
 * AN TOÀN — không "dính" sai: hướng rẽ THẬT đổi (tới khúc rẽ mới) = large-icon MỚI phân loại THÀNH CÔNG
 * (fresh != null), nên [resolve] trả fresh và cập nhật mốc; nhánh giữ chỉ ăn đúng frame LỖI đọc. Reset ở ranh
 * giới phiên (đến nơi / gỡ noti / rớt binding) để tuyến mới không kế thừa hướng của tuyến cũ.
 *
 * Thuần (không state, không Android) để test được; caller (NavNotificationListener) giữ `last` theo phiên.
 */
object ManeuverHold {

    /** Dải mã AMAP NEW_ICON hợp lệ (khớp gate `it in 0..28` ở pipeline phân loại). */
    private val VALID = 0..28

    /**
     * @param fresh mã vừa phân loại của frame này (null = không đọc được frame này).
     * @param last  mã hợp lệ gần nhất trong phiên (hoặc -1 nếu chưa có).
     * @return fresh nếu hợp lệ; nếu không thì last nếu hợp lệ; nếu không thì -1 ("chưa có hướng").
     */
    fun resolve(fresh: Int?, last: Int): Int =
        fresh?.takeIf { it in VALID } ?: last.takeIf { it in VALID } ?: -1
}
