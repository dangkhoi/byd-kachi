package com.byd.clusternav.launcher

import com.byd.clusternav.launcher.SlotHeadRest.Rest
import com.byd.clusternav.launcher.SlotHeadTouch.Act
import com.byd.clusternav.launcher.SlotHeadTouch.Gesture
import com.byd.clusternav.launcher.SlotHeadTouch.Head
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Soát 2.87 · P2 — máy trạng thái chạm của đầu ô ([SlotHeadTouch]), bảng viết TAY (không suy từ hàm đang thử).
 *
 * Trước bản này luật chỉ được canh bằng grep mã `:app`; mỗi dòng dưới đây khoá một hồi quy mà grep để lọt:
 *  - đổi chỗ hai nhánh `!consumed` (khung trống ⇄ khe) · hiện cả ô ALWAYS · quên GIỮ khi ngón đang đặt;
 *  - bỏ lượt GIỮ ở DOWN thứ hai ⇒ cú nhấp đúp làm ⇄ hiện giữa cử chỉ (R-AH2 *"nhấp đúp vẫn tới app"*);
 *  - DOWN trúng chỗ nút đang ẩn mà vẫn hẹn hiện ⇒ chạm lần hai vào ô tìm kiếm Google Maps trúng ⇄ / nút *tắt*;
 *  - đầu ô đang hiện mà UP không tính lại 3 s; khung trống (không ai nhận DOWN) mà UP lại hẹn hiện lần nữa;
 *  - *tắt* đang chờ xác nhận mà hàng nút ẩn mất giữa chừng (2 s phải < 3 s).
 */
class SlotHeadTouchTest {

    private val hidden = Head(Rest.AUTO_HIDE, visible = false)
    private val shown = Head(Rest.AUTO_HIDE, visible = true)
    private val always = Head(Rest.ALWAYS, visible = true)

    /** Một hàng của bảng: DOWN (ô, có con nhận, trúng vùng nút) trên [heads] ⇒ việc ở DOWN + việc ở UP. */
    private data class Row(
        val name: String,
        val slot: Int,
        val consumed: Boolean,
        val inHead: Boolean,
        val heads: Map<Int, Head>,
        val atDown: List<Act>,
        val atUp: List<Act>,
    )

    private val rows = listOf(
        // ── chạm / kéo vào app trong ô (có con nhận DOWN) ──
        Row("chạm app, đầu ô ẩn", 0, true, false, mapOf(0 to hidden), listOf(Act.Hold(0)), listOf(Act.RevealAfterDoubleTap(0))),
        Row("kéo bản đồ (MOVE không đổi gì), đầu ô ẩn", 1, true, false, mapOf(1 to hidden, 0 to hidden), listOf(Act.Hold(1)), listOf(Act.RevealAfterDoubleTap(1))),
        Row("chạm app, đầu ô đang hiện ⇒ tính lại 3 s", 0, true, false, mapOf(0 to shown), listOf(Act.Hold(0)), listOf(Act.Reveal(0))),
        Row("chạm CHỖ nút đang ẩn (ô tìm kiếm Maps)", 0, true, true, mapOf(0 to hidden), listOf(Act.Hold(0)), emptyList()),
        Row("chạm nút đang hiện ⇒ hiện tiếp", 0, true, true, mapOf(0 to shown), listOf(Act.Hold(0)), listOf(Act.Reveal(0))),
        Row("ô ALWAYS (công tắc tắt / TalkBack / ActivityView)", 0, true, false, mapOf(0 to always), emptyList(), emptyList()),
        Row("ô ALWAYS, chạm chỗ nút", 0, true, true, mapOf(0 to always), emptyList(), emptyList()),
        Row("ô chưa đăng ký / khung đã dựng lại", 2, true, false, mapOf(0 to hidden), emptyList(), emptyList()),
        // ── DOWN không ai nhận ──
        Row("khung trống: hiện NGAY đúng khung đó", 1, false, false, mapOf(0 to hidden, 1 to hidden), listOf(Act.Reveal(1)), emptyList()),
        Row("khung trống ALWAYS", 1, false, false, mapOf(1 to always), emptyList(), emptyList()),
        Row("khe giữa khung: hiện MỌI đầu ô tự ẩn", -1, false, false, mapOf(2 to hidden, 0 to shown, 1 to always),
            listOf(Act.Reveal(0), Act.Reveal(2)), emptyList()),
        Row("khe giữa khung, không ô nào", -1, false, false, emptyMap(), emptyList(), emptyList()),
    )

    @Test
    fun `bang cham - DOWN roi UP (CANCEL cung luat)`() {
        rows.forEach { r ->
            val step = SlotHeadTouch.onDown(r.slot, r.consumed, r.inHead, r.heads)
            assertEquals(r.atDown, step.acts, "${r.name}: DOWN")
            assertEquals(r.atUp, SlotHeadTouch.onUp(step.gesture, r.heads), "${r.name}: UP/CANCEL")
        }
        assertEquals(12, rows.size, "thêm ca ⇒ viết tay cả hai cột")
    }

    @Test
    fun `nhap dup - DOWN thu hai GIU (huy hen hien dang cho), khong hien giua cu chi`() {
        val heads = mapOf(0 to hidden)
        val first = SlotHeadTouch.onDown(0, consumed = true, inHead = false, heads)
        assertEquals(listOf(Act.RevealAfterDoubleTap(0)), SlotHeadTouch.onUp(first.gesture, heads), "UP lần 1: chỉ HẸN")
        val second = SlotHeadTouch.onDown(0, consumed = true, inHead = false, heads)
        assertEquals(listOf(Act.Hold(0)), second.acts, "DOWN lần 2 trong nhịp nhấp đúp phải huỷ lượt hẹn của lần 1")
    }

    @Test
    fun `cu chi nho tu DOWN - khung trong khong de lai cu chi, vung nut chi nho khi o co dau`() {
        assertEquals(Gesture.NONE, SlotHeadTouch.onDown(1, consumed = false, inHead = true, mapOf(1 to hidden)).gesture)
        assertEquals(Gesture(3, false), SlotHeadTouch.onDown(3, consumed = true, inHead = true, emptyMap()).gesture)
        assertEquals(Gesture(0, true), SlotHeadTouch.onDown(0, consumed = true, inHead = true, mapOf(0 to hidden)).gesture)
        assertEquals(emptyList<Act>(), SlotHeadTouch.onUp(Gesture.NONE, mapOf(0 to shown)), "UP không có DOWN ⇒ không làm gì")
    }

    @Test
    fun `luat nghi doi giua DOWN va UP - UP doc trang thai MOI`() {
        val step = SlotHeadTouch.onDown(0, consumed = true, inHead = false, mapOf(0 to hidden))
        assertEquals(emptyList<Act>(), SlotHeadTouch.onUp(step.gesture, mapOf(0 to always)), "TalkBack bật giữa chừng ⇒ không hẹn")
        assertEquals(emptyList<Act>(), SlotHeadTouch.onUp(step.gesture, emptyMap()), "khung dựng lại giữa chừng ⇒ không đụng view cũ")
    }

    @Test
    fun `tat hai buoc - dau o hien tiep suot luot cho, cua so cho ngan hon hen an`() {
        assertEquals(listOf(Act.Reveal(2)), SlotHeadTouch.onConfirmArmed(2, mapOf(2 to shown)))
        assertEquals(listOf(Act.Reveal(2)), SlotHeadTouch.onConfirmArmed(2, mapOf(2 to hidden)), "nhấn bằng trợ năng khi hàng đang mờ ⇒ hiện lại")
        assertEquals(emptyList<Act>(), SlotHeadTouch.onConfirmArmed(2, mapOf(2 to always)), "luôn hiện ⇒ không có hẹn giờ nào")
        assertEquals(emptyList<Act>(), SlotHeadTouch.onConfirmArmed(2, emptyMap()))
        assertTrue(SlotCloseConfirm.WINDOW_MS < SlotHeadRest.HIDE_AFTER_MS, "hàng nút không được ẩn giữa lúc chờ xác nhận")
    }
}
