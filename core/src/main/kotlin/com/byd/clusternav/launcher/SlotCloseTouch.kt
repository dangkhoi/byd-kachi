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
 * Nay mỗi lần nhấn XONG (DOWN…UP, không trượt khỏi nút) vào HÀNG ĐỢI theo thứ tự UP; mỗi click lấy ĐÚNG MỘT lần nhấn ở đầu hàng
 * — đúng thứ tự View xếp `PerformClick` (một UP → một post, FIFO của `Handler`). Mọi phép đo (lượt đầu, khoảng DOWN₂ − UP₁, lượt
 * hai) dùng `MotionEvent.eventTime` của chính lần nhấn ⇒ trễ handler không đổi kết quả.
 *
 * ## Lần nhấn nào SINH click — chép đúng luật của `View.onTouchEvent` (r47)
 *  - UP chỉ ra click khi nút còn PRESSED/PREPRESSED (`View.java:14794-14795`); MOVE ra ngoài `pointInView(x, y, touchSlop)` gỡ
 *    cả hai (`:14931-14941`) ⇒ [left]; CANCEL gỡ (`:14889-14898`) ⇒ [cancel]. Nút không LONG_CLICKABLE ⇒ không có nhánh nhấn-giữ
 *    nuốt click (`checkForLongClick` chỉ chạy khi LONG_CLICKABLE/TOOLTIP — `:25730-25731`).
 *  - [inView] = `View.pointInView(localX, localY, slop)` (`:17080-17083`, @hide ⇒ chép công thức).
 *
 * ## Không bỏ lần nhấn theo TUỔI (soát vòng 4 [P3])
 * Bản vòng 3 bỏ mọi lần nhấn đầu hàng đã chờ quá 1 s rồi trả lần KẾ ⇒ luồng chính kẹt 1,0–1,25 s sau UP₁ của một cú nhấp đúp
 * (UP₁ = 1000, DOWN₂ = 1150, UP₂ = 1250, hai click chạy ở 2100/2101) lệch cả cặp: click₁ nhận lần nhấn của click₂ (ARM ở 1250),
 * click₂ nhận `null` (mốc = giờ handler 2101, cách 851 ms) ⇒ FIRE. Trần hàng (bỏ lần CŨ NHẤT khi đầy) lệch đúng như thế sau ≥ 5
 * cú chạm dồn trong một lần kẹt. Bỏ theo tuổi mà KHÔNG lệch (trả `null` cho đúng click ấy) vẫn hỏng: hai lần nhấn cùng quá tuổi,
 * giữa hai click chen một khung 0,5 s ⇒ hai mốc giờ handler cách ≥ nhịp nhấp đúp ⇒ FIRE. Mốc `eventTime` của lần nhấn là sự
 * thật, cũ mấy cũng đúng; "giờ handler" mới là thứ bị trễ ⇒ nay KHÔNG có ngưỡng tuổi, KHÔNG có trần.
 *
 * ## Lần nhấn mà View KHÔNG ra click ⇒ [reached] dọn (thay cho ngưỡng tuổi)
 * `SlotActionsCluster.track` thấy trọn DOWN…UP nhưng View không post click khi: cửa sổ mất tiêu điểm giữa lúc nhấn (`View.java:13716-13719` gỡ
 * PRESSED ⇒ UP không qua `:14794-14795`), hoặc click đã post bị gỡ (`removePerformClickCallback` — tháo khỏi cửa sổ `:19406-19413`,
 * `onCancelPendingInputEvents` `:19705-19706`). Lần nhấn ấy nằm đầu hàng sẽ lệch mọi cặp sau. Chặn bằng MỐC LƯỢT: ở UP, người
 * nghe chạm (chạy TRƯỚC `onTouchEvent` — `View.java:13424-13430`) post `reached(p)` qua CHÍNH `View.post` của nút (`:17850-17859`,
 * cùng handler với `post(mPerformClick)`); `MessageQueue.enqueueMessage` xếp theo `when` rồi theo thứ tự vào (r47
 * `MessageQueue.java:569-591`, cả hai là message đồng bộ ⇒ rào đồng bộ chặn/nhả cả hai như nhau) ⇒ mốc của lần nhấn P chạy NGAY
 * TRƯỚC click của P (nếu có). Lúc ấy click của mọi lần nhấn xếp trước P đã chạy xong ⇒ lần nhấn nào còn nằm trước P là lần View
 * không ra click ⇒ bỏ. Hàng chỉ còn lần nhấn có click đang chờ (cỡ = số cú chạm dồn trong một lần kẹt), cộng tối đa MỘT lần nhấn
 * không ra click ở cuối, nằm tới mốc lượt của lần nhấn kế.
 *
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

    /** UP: lần nhấn trọn vào hàng và được trả về để người gọi post mốc lượt [reached] của nó; `null` = lần nhấn không ra click. */
    fun up(eventTime: Long): Press? {
        val d = downAt ?: return null
        downAt = null
        return Press(d, eventTime).also { done.addLast(it) }
    }

    /**
     * Mốc lượt của [p] (post ở UP, chạy NGAY TRƯỚC click của [p]): mọi lần nhấn còn nằm TRƯỚC [p] là lần View không ra click ⇒
     * bỏ. [p] đã được lấy (click chạy đồng bộ trong `onTouchEvent` khi `post` hỏng — `View.java:14823-14825`) ⇒ không đụng gì.
     */
    fun reached(p: Press) {
        if (done.none { it === p }) return
        while (done.first() !== p) done.removeFirst()
    }

    /**
     * Lần nhấn của click đang chạy: ĐÚNG MỘT lần ở đầu hàng (cũ mấy cũng dùng — xem KDoc lớp); `null` = click không đến từ ngón
     * (trợ năng `ACTION_CLICK`, bàn phím).
     */
    fun take(): Press? = done.removeFirstOrNull()

    companion object {
        /** `View.pointInView(localX, localY, slop)` (r47 `View.java:17080-17083`). */
        fun inView(x: Float, y: Float, width: Int, height: Int, slop: Float): Boolean =
            x >= -slop && y >= -slop && x < width + slop && y < height + slop
    }
}
