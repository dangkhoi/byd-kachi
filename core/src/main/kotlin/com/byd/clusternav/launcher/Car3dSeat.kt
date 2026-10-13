package com.byd.clusternav.launcher

/**
 * 3.01 POLISH2 — soát O-3D-O1 (rủi ro): tối đa MỘT widget xe 3D sống cùng lúc (một Filament `Engine`, một tệp đệm GLB ánh xạ,
 * ~+105 MB PSS [ĐO máy ảo]). Ô thứ hai cũng chọn `w_car3d` ⇒ bảng 2D. Và cổng cỡ ô ngay lúc DỰNG: bố cục cũ đặt widget vào ô
 * nhỏ hơn [WidgetRegistry] `minSlotDp` (trước khi bộ chọn có luật cỡ) ⇒ bảng 2D, không dựng Filament.
 */
class Car3dSeat {
    private var holder: Any? = null

    /** [who] xin chỗ; `true` nếu chỗ trống hoặc đã là của nó. */
    @Synchronized fun claim(who: Any): Boolean {
        if (holder == null || holder === who) { holder = who; return true }
        return false
    }

    /** Trả chỗ (chỉ chủ trả được). */
    @Synchronized fun release(who: Any) { if (holder === who) holder = null }

    companion object {
        val shared = Car3dSeat()

        /** Ô [wDp]×[hDp] đủ cỡ cho widget [id] (không đòi cỡ ⇒ luôn đủ; chưa đo ⇒ chưa đủ). */
        fun fits(id: String, wDp: Int?, hDp: Int?): Boolean {
            val min = WidgetRegistry.byId(id)?.minSlotDp ?: return true
            return wDp != null && hDp != null && wDp >= min.first && hDp >= min.second
        }
    }
}
