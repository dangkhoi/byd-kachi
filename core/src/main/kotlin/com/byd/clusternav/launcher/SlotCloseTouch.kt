package com.byd.clusternav.launcher

/**
 * ═══ Soát vòng 3 [P3] — ghép mỗi CLICK của nút *tắt* với ĐÚNG lần nhấn sinh ra nó, theo MỐC SỰ KIỆN (không theo giờ handler) ═══
 *
 * Bản vòng 2 giữ một cặp `touchDown/touchUp` "hiện tại" và đọc nó lúc click chạy. [ĐO nguồn android-10.0.0_r47] click KHÔNG chạy
 * ở UP mà được POST (`View.java:14820-14825` `post(mPerformClick)`), còn sự kiện chạm vào trước mọi message đang xếp hàng
 * (`MessageQueue.java:330-336` · `ViewRootImpl.java:7629-7630`) ⇒ khi luồng chính trễ, DOWN₂ của một cú nhấp đúp tới TRƯỚC
 * click₁: cặp "hiện tại" đã là (D₂, —) ⇒ click₁ thấy "không có UP" (coi như trợ năng) và XOÁ D₂ ⇒ click₂ cũng không có khoảng đo
 * ⇒ FIRE = `am stack remove` (không hoàn tác) cho một cú nhấp đúp phóng to bản đồ.
 *
 * Nay mỗi lần nhấn XONG (DOWN…UP, không trượt khỏi nút) vào HÀNG ĐỢI theo thứ tự UP; mỗi click lấy lần nhấn CŨ NHẤT chưa dùng —
 * đúng thứ tự View xếp `PerformClick` (một UP → một post, FIFO của `Handler`). Mọi phép đo (lượt đầu, khoảng DOWN₂ − UP₁, lượt
 * hai) dùng `MotionEvent.eventTime` của chính lần nhấn ⇒ trễ handler không đổi kết quả.
 *
 * ## Lần nhấn nào SINH click — chép đúng luật của `View.onTouchEvent` (r47)
 *  - UP chỉ ra click khi nút còn PRESSED/PREPRESSED (`View.java:14794-14795`); MOVE ra ngoài `pointInView(x, y, touchSlop)` gỡ
 *    cả hai (`:14931-14941`) ⇒ [left]; CANCEL gỡ (`:14889-14898`) ⇒ [cancel]. Nút không LONG_CLICKABLE ⇒ không có nhánh nhấn-giữ
 *    nuốt click (`checkForLongClick` chỉ chạy khi LONG_CLICKABLE/TOOLTIP — `:25730-25731`).
 *  - [inView] = `View.pointInView(localX, localY, slop)` (`:17080-17083`, @hide ⇒ chép công thức).
 *
 * Lưới an toàn: lần nhấn đã nằm hàng đợi quá [STALE_MS] (luồng chính trễ cả giây — cỡ ANR) bị bỏ ⇒ click ấy đi đường "không ngón"
 * (khoảng đo vắng) — và [SlotCloseConfirm.onTap] còn chốt click-tới-click, nên lệch ghép chỉ rơi về WAIT/ARM, không về FIRE.
 * Thuần; gọi tuần tự từ luồng chính.
 */
class SlotCloseTouch {

    /** Một lần nhấn trọn của ngón: mốc DOWN và UP (`MotionEvent.eventTime`, cùng đồng hồ `SystemClock.uptimeMillis`). */
    data class Press(val down: Long, val up: Long)

    private var downAt: Long? = null
    private val done = ArrayDeque<Press>()

    fun down(eventTime: Long) { downAt = eventTime }

    /** Ngón trượt khỏi nút (+ touch slop) ⇒ lần nhấn này KHÔNG ra click (View gỡ PRESSED). */
    fun left() { downAt = null }

    fun cancel() { downAt = null }

    fun up(eventTime: Long) {
        val d = downAt ?: return
        downAt = null
        done.addLast(Press(d, eventTime))
        while (done.size > KEEP) done.removeFirst()
    }

    /**
     * Lần nhấn của click đang chạy LÚC [now] (cũ nhất chưa dùng); `null` = click không đến từ ngón (trợ năng `ACTION_CLICK`,
     * bàn phím) hoặc lần nhấn đã quá cũ.
     */
    fun take(now: Long): Press? {
        while (done.isNotEmpty() && now - done.first().up > STALE_MS) done.removeFirst()
        return done.removeFirstOrNull()
    }

    companion object {
        /** Hàng đợi tối đa — hai click chờ là đủ cho một cú nhấp đúp; thêm một chút lề. */
        const val KEEP = 4

        /** Một lần nhấn chờ click quá lâu ⇒ coi như không ghép được (luồng chính đã kẹt cỡ ANR). */
        const val STALE_MS = 1_000L

        /** `View.pointInView(localX, localY, slop)` (r47 `View.java:17080-17083`). */
        fun inView(x: Float, y: Float, width: Int, height: Int, slop: Float): Boolean =
            x >= -slop && y >= -slop && x < width + slop && y < height + slop
    }
}
