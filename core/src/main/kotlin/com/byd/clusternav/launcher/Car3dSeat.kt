package com.byd.clusternav.launcher

/**
 * 3.01 POLISH2 — soát O-3D-O1 (rủi ro): tối đa MỘT widget xe 3D sống cùng lúc (một Filament `Engine`, một tệp đệm GLB ánh xạ,
 * ~+105 MB PSS [ĐO máy ảo]). Ô thứ hai cũng chọn `w_car3d` ⇒ bảng 2D. Và cổng cỡ ô ngay lúc DỰNG: bố cục cũ đặt widget vào ô
 * nhỏ hơn [WidgetRegistry] `minSlotDp` (trước khi bộ chọn có luật cỡ) ⇒ bảng 2D, không dựng Filament.
 */
class Car3dSeat {
    private var holder: Any? = null
    private var evictHolder: (() -> Unit)? = null

    /**
     * [who] xin chỗ — NGƯỜI MỚI THẮNG. 3.1.4 [ĐO xe 14/10 08:01:38]: bật màn ⇒ HOME dựng lại ô ⇒ widget 3D MỚI xin chỗ trong
     * khi widget CŨ (vẫn bám cây view, không bao giờ `dừng`) còn giữ ⇒ luật cũ "người giữ thắng" đẩy widget ĐANG HIỆN về 2D vĩnh
     * viễn, còn Engine nằm ở view ẩn. Nay chủ cũ nhận [evict] của nó (lùi 2D + huỷ Engine) NGAY TRONG lời gọi này, TRƯỚC khi người
     * mới dựng Engine ⇒ vẫn tối đa một Engine. Chủ cũ đã lùi 2D thì không xin lại (không giành qua lại).
     */
    fun claim(who: Any, evict: () -> Unit = {}): Boolean {
        val old: (() -> Unit)?
        synchronized(this) {
            if (holder === who) return true   // chủ xin lại: giữ [evict] đã đăng ký
            old = if (holder != null) evictHolder else null
            holder = who; evictHolder = evict
        }
        old?.invoke()
        return true
    }

    /** Trả chỗ (chỉ chủ trả được). */
    @Synchronized fun release(who: Any) { if (holder === who) { holder = null; evictHolder = null } }

    /** Chủ hiện tại là [who]? */
    @Synchronized fun holds(who: Any): Boolean = holder === who

    companion object {
        val shared = Car3dSeat()

        /** Ô [wDp]×[hDp] đủ cỡ cho widget [id] (không đòi cỡ ⇒ luôn đủ; chưa đo ⇒ chưa đủ). */
        fun fits(id: String, wDp: Int?, hDp: Int?): Boolean {
            val min = WidgetRegistry.byId(id)?.minSlotDp ?: return true
            return wDp != null && hDp != null && wDp >= min.first && hDp >= min.second
        }
    }
}
