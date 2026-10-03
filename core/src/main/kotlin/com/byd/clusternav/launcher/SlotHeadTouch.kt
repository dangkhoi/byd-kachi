package com.byd.clusternav.launcher

import com.byd.clusternav.launcher.SlotHeadRest.Rest

/**
 * ═══ 2.87 · R-AH — cú chạm nào làm đầu ô (⇄ + chạy nền / tắt) HIỆN, GIỮ hay HẸN HIỆN (thuần, `:core`) ═══════════════════
 *
 * Soát 2.87 · P2: máy trạng thái này trước nằm trong `SlotHeadAutoHide.observe` (`:app`, không Robolectric) và chỉ được canh
 * bằng grep mã nguồn — đổi chỗ hai nhánh `!consumed`, bỏ lượt huỷ hẹn hiện ở DOWN thứ hai (nhấp đúp làm ⇄ hiện giữa cử chỉ),
 * hay đọc "DOWN trong vùng nút" sai thời điểm đều lọt. Nay quyết định ở đây (bảng đủ ô `SlotHeadTouchTest`); `:app` chỉ đo
 * (ô nào, có ai nhận chạm, có rơi vào vùng nút, nút đang hiện không) rồi THI HÀNH các [Act] trả về.
 *
 * ## Luật (spec `kachi-287-look-and-keys.html` §4.2 R-AH1/R-AH2 · §4.6 L6-c2)
 *  - **DOWN không ai nhận** (khung trống / khe giữa khung — `WorkspaceView` sẽ không thấy UP): trong một khung ⇒ hiện đầu ô
 *    khung đó NGAY; ngoài mọi khung ⇒ hiện MỌI đầu ô (owner: *"nhấn đại vào màn nó lại lòi ra"*). Hẹn ẩn tính từ lúc hiện.
 *  - **DOWN có con nhận** (app / widget / nút): ngón đang đặt ⇒ GIỮ nguyên (huỷ hẹn hiện + hẹn ẩn) — đây cũng là chỗ DOWN
 *    thứ hai của một cú nhấp đúp huỷ lượt hiện đang chờ, nên nhấp đúp vẫn tới app.
 *  - **UP / CANCEL** của cử chỉ đó: đầu ô đang hiện ⇒ hiện tiếp, tính lại [SlotHeadRest.HIDE_AFTER_MS] từ lần nhấc tay;
 *    đang ẩn ⇒ hẹn hiện sau nhịp nhấp đúp — TRỪ khi DOWN rơi vào CHỖ một nút đầu ô (ô tìm kiếm Google Maps nằm giữa-trên,
 *    đúng chỗ ⇄: hiện ở đó thì cú chạm thứ hai trúng nút thay vì app).
 *  - Đầu ô [Rest.ALWAYS] (công tắc tắt · TalkBack · ActivityView · không bộ chiếu — [SlotHeadRest.rest]) không có hẹn giờ ⇒
 *    không [Act] nào.
 *  - L6 · *tắt* hai bước (soát 2.87 · P2, [SlotCloseConfirm]): lượt chạm đầu đưa nút *tắt* vào trạng thái chờ xác nhận ⇒
 *    [onConfirmArmed] hiện tiếp đầu ô, tính lại 3 s — hàng nút không được ẩn giữa lúc chờ (2 s < 3 s, `SlotHeadTouchTest`).
 */
object SlotHeadTouch {

    /** Đầu ô đã đăng ký VÀ còn đúng khung đang hiện (bên gọi lọc): luật nghỉ + đang `VISIBLE` không. */
    data class Head(val rest: Rest, val visible: Boolean)

    /** Nhớ giữa DOWN và UP của MỘT cử chỉ: ô có con nhận DOWN (`-1` = không ai nhận / ngoài khung) + DOWN có trúng vùng nút. */
    data class Gesture(val slot: Int, val inHead: Boolean) {
        companion object {
            val NONE = Gesture(-1, false)
        }
    }

    /** Việc bên thi hành làm với đầu ô [slot]. */
    sealed interface Act {
        val slot: Int

        /** Hiện NGAY (mờ vào) + hẹn ẩn sau [SlotHeadRest.HIDE_AFTER_MS] (huỷ mọi hẹn cũ của ô trước). */
        data class Reveal(override val slot: Int) : Act

        /** Hẹn hiện sau nhịp nhấp đúp của máy (`ViewConfiguration.getDoubleTapTimeout`, đọc tại chỗ thi hành). */
        data class RevealAfterDoubleTap(override val slot: Int) : Act

        /** Ngón đang đặt: huỷ hẹn hiện + hẹn ẩn, trạng thái đứng yên. */
        data class Hold(override val slot: Int) : Act
    }

    /** Kết quả của một DOWN: cử chỉ cần nhớ tới UP + việc làm ngay. */
    data class Step(val gesture: Gesture, val acts: List<Act>)

    /**
     * DOWN ở ô [slot] (`-1` = khe / ngoài mọi khung). [consumed] = có con nhận (kết quả `super.dispatchTouchEvent`).
     * [inHead] = DOWN rơi vào khung chạm ⇄ hoặc một nút đầu ô đang làm được (bên gọi đo SAU khi hỏi lại nút). [heads] = mọi
     * đầu ô hợp lệ theo chỉ số.
     */
    fun onDown(slot: Int, consumed: Boolean, inHead: Boolean, heads: Map<Int, Head>): Step {
        val h = if (slot >= 0) heads[slot] else null
        val gesture = if (consumed) Gesture(slot, h != null && inHead) else Gesture.NONE
        val acts = when {
            !consumed && slot >= 0 -> listOfNotNull(h?.takeIf { it.rest == Rest.AUTO_HIDE }?.let { Act.Reveal(slot) })
            !consumed -> heads.keys.sorted().filter { heads.getValue(it).rest == Rest.AUTO_HIDE }.map { Act.Reveal(it) }
            h?.rest == Rest.AUTO_HIDE -> listOf(Act.Hold(slot))
            else -> emptyList()
        }
        return Step(gesture, acts)
    }

    /** UP hoặc CANCEL kết thúc cử chỉ [gesture] (cùng luật — CANCEL là cử chỉ bị cha cướp, ⇄ vẫn phải hiện lại được). */
    fun onUp(gesture: Gesture, heads: Map<Int, Head>): List<Act> {
        val h = if (gesture.slot >= 0) heads[gesture.slot] else null
        if (h == null || h.rest != Rest.AUTO_HIDE) return emptyList()
        return when {
            h.visible -> listOf(Act.Reveal(gesture.slot))
            !gesture.inHead -> listOf(Act.RevealAfterDoubleTap(gesture.slot))
            else -> emptyList()
        }
    }

    /** Nút *tắt* của ô [slot] vừa vào trạng thái chờ xác nhận ([SlotCloseConfirm]) ⇒ giữ đầu ô hiện suốt lượt chờ. */
    fun onConfirmArmed(slot: Int, heads: Map<Int, Head>): List<Act> =
        if (heads[slot]?.rest == Rest.AUTO_HIDE) listOf(Act.Reveal(slot)) else emptyList()
}
