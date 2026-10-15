package com.byd.clusternav.launcher

/**
 * 3.01 POLISH2 — soát O-3D-O1 (rủi ro): tối đa MỘT widget xe 3D sống cùng lúc (một Filament `Engine`, một tệp đệm GLB ánh xạ,
 * ~+105 MB PSS [ĐO máy ảo]). Và cổng cỡ ô ngay lúc DỰNG: ô nhỏ hơn [WidgetRegistry] `minSlotDp` ⇒ không dựng Filament.
 *
 * 3.1.8 · `CAR3D-FALLBACK-STICKY` (owner 15/10 *"không bao giờ lùi về 2D … không chạy thì bỏ qua"*): bên thua chỗ KHÔNG còn lùi
 * bảng 2D vĩnh viễn — nó CHỜ (khung 3D trống) và được [release] đánh thức khi chỗ trống. [ĐO máy ảo 15/10, 3.1.6] hai ô `w_car3d`
 * ⇒ ô chính lùi 2D; bung ô chính (ô kia khuất ⇒ nhả Engine) ⇒ ô chính VẪN bảng 2D (luật cũ không ai đánh thức).
 */
class Car3dSeat {
    private class Who(val token: Any, val shown: () -> Boolean, val wake: () -> Unit, val evict: () -> Unit)
    private var holder: Who? = null
    /** Bản đang chờ chỗ (thua chỗ / bị đẩy) → hàm đánh thức. Không giữ bản đã [release] (rời cửa sổ ⇒ không đánh thức view chết). */
    private val waiting = LinkedHashMap<Any, () -> Unit>()

    /**
     * [who] xin chỗ. Trả `true` = được dựng Engine; `false` = chờ ([wake] gọi khi chỗ trống).
     *  • chủ xin lại ⇒ `true` (giữ [evict] đã đăng ký);
     *  • chủ ĐANG HIỆN ([shown] của chủ) ⇒ người xin CHỜ — không bao giờ đẩy widget đang hiện (owner 15/10: hai ô `w_car3d` trước
     *    đây ⇒ ô chính thành bảng 2D; tắt/bật màn ⇒ hai bên giành nhau mỗi lượt hiện);
     *  • chủ KHÔNG hiện (bản cũ còn bám cây view mà khuất — 3.1.4 [ĐO xe 14/10 08:01:38]: bật màn ⇒ HOME dựng lại ô) ⇒ NGƯỜI MỚI
     *    THẮNG: chủ cũ nhận [evict] (huỷ Engine) NGAY TRONG lời gọi này, TRƯỚC khi người mới dựng ⇒ vẫn tối đa một Engine; chủ cũ
     *    vào hàng chờ (hiện lại ⇒ tự xin lại).
     */
    fun claim(who: Any, shown: () -> Boolean = { true }, wake: () -> Unit = {}, evict: () -> Unit = {}): Boolean {
        val old: Who?
        synchronized(this) {
            val h = holder
            if (h != null && h.token === who) return true
            if (h != null && h.shown()) { waiting[who] = wake; return false }
            waiting.remove(who)
            old = h
            if (h != null) waiting[h.token] = h.wake
            holder = Who(who, shown, wake, evict)
        }
        old?.evict?.invoke()
        return true
    }

    /** Trả chỗ (chỉ chủ trả được; bản đang chờ vẫn chờ). Chỗ vừa trống ⇒ đánh thức MỌI bản đang chờ (bản đầu xin được, bản sau lại chờ). */
    fun release(who: Any) {
        val woken: List<() -> Unit>
        synchronized(this) {
            if (holder?.token !== who) return
            holder = null
            woken = waiting.values.toList(); waiting.clear()
        }
        woken.forEach { it() }
    }

    /** [who] rời hẳn (rời cửa sổ / không khả thi): ra khỏi hàng chờ (không đánh thức view chết) + trả chỗ nếu đang giữ. */
    fun leave(who: Any) { synchronized(this) { waiting.remove(who) }; release(who) }

    /** Chủ hiện tại là [who]? */
    @Synchronized fun holds(who: Any): Boolean = holder?.token === who

    /** [who] đang chờ chỗ? */
    @Synchronized fun waits(who: Any): Boolean = who in waiting

    companion object {
        val shared = Car3dSeat()

        /** Ô [wDp]×[hDp] đủ cỡ cho widget [id] (không đòi cỡ ⇒ luôn đủ; chưa đo ⇒ chưa đủ). */
        fun fits(id: String, wDp: Int?, hDp: Int?): Boolean {
            val min = WidgetRegistry.byId(id)?.minSlotDp ?: return true
            return wDp != null && hDp != null && wDp >= min.first && hDp >= min.second
        }
    }
}
