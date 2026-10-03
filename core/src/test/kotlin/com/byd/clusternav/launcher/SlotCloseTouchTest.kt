package com.byd.clusternav.launcher

import com.byd.clusternav.launcher.SlotCloseConfirm.Tap
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Soát vòng 3 [P3] — nút *tắt* đầu ô: nhấp đúp thật KHÔNG được tắt app kể cả khi luồng chính trễ. Mô phỏng đúng thứ tự [ĐO nguồn
 * android-10.0.0_r47] của luồng chính: sự kiện chạm vào TRƯỚC message đang xếp hàng (`MessageQueue.java:330-336`), click được
 * POST sau UP (`View.java:14820-14825`). Đồng hồ = `SystemClock.uptimeMillis` (cùng gốc `MotionEvent.eventTime`).
 */
class SlotCloseTouchTest {

    /** Một nút *tắt* thu nhỏ: chính phép ghép của `SlotActionsCluster.tap` (lấy lần nhấn → mốc → [SlotCloseConfirm.onTap]). */
    private class Button {
        val touch = SlotCloseTouch()
        var armedAt: Long? = null
        var lastUp: Long? = null
        val out = ArrayList<Tap>()
        fun click(handlerNow: Long) {
            val p = touch.take(handlerNow)
            val at = p?.up ?: handlerNow
            val g = p?.let { q -> lastUp?.let { q.down - it } }
            val r = SlotCloseConfirm.onTap(armedAt, at, g, 300L)   // 300 = ViewConfiguration.DOUBLE_TAP_TIMEOUT (AOSP)
            when (r) {
                Tap.ARM -> { armedAt = at; lastUp = p?.up }
                Tap.WAIT -> lastUp = p?.up
                Tap.FIRE -> armedAt = null
            }
            out += r
        }
    }

    /**
     * Ca hỏng của soát vòng 3: UP₁ = 1000, luồng chính kẹt ⇒ DOWN₂ (1200) được phát TRƯỚC click₁ (chạy lúc 1230), UP₂ = 1320,
     * click₂ chạy 1330. Bản vòng 2 (cặp "hiện tại"): click₁ thấy (D₂, —) ⇒ "không phải ngón" ⇒ xoá D₂ ⇒ click₂ không khoảng đo ⇒
     * click-tới-click 100 ms vẫn qua cửa sổ ⇒ FIRE = `am stack remove`. Nay: ARM rồi WAIT.
     */
    @Test
    fun `nhap dup khi luong chinh tre - click1 chay SAU DOWN2 - khong tat`() {
        val b = Button()
        b.touch.down(900); b.touch.up(1_000)
        b.touch.down(1_200)               // DOWN₂ tới trước click₁ (luồng chính trễ)
        b.click(1_230)                    // click₁
        b.touch.up(1_320)
        b.click(1_330)                    // click₂
        assertEquals(listOf(Tap.ARM, Tap.WAIT), b.out)
    }

    /** Trễ cả hai click tới sau UP₂ (đã nhận đủ hai lần nhấn) ⇒ mỗi click vẫn lấy ĐÚNG lần nhấn của nó, theo thứ tự UP. */
    @Test
    fun `tre ca hai click - ghep dung thu tu UP`() {
        val b = Button()
        b.touch.down(900); b.touch.up(1_000)
        b.touch.down(1_150); b.touch.up(1_260)
        b.click(1_400); b.click(1_401)
        assertEquals(listOf(Tap.ARM, Tap.WAIT), b.out)
    }

    /** Xác nhận CÓ CHỦ Ý (thấy đĩa đỏ, chạm lại sau 700 ms) vẫn tắt — dù click chạy trễ. */
    @Test
    fun `xac nhan co chu y van tat`() {
        val b = Button()
        b.touch.down(900); b.touch.up(1_000); b.click(1_010)
        b.touch.down(1_700); b.touch.up(1_790); b.click(1_950)
        assertEquals(listOf(Tap.ARM, Tap.FIRE), b.out)
    }

    /** Trượt khỏi nút (View không ra click) ⇒ lần nhấn KHÔNG vào hàng; click trợ năng không có lần nhấn ⇒ `null`. */
    @Test
    fun `truot khoi nut va click khong den tu ngon`() {
        val t = SlotCloseTouch()
        t.down(100); t.left(); t.up(200)
        assertNull(t.take(210), "trượt khỏi nút ⇒ không có click để ghép")
        t.down(300); t.cancel(); t.up(400)
        assertNull(t.take(410), "CANCEL ⇒ không click")
        t.down(500); t.up(600)
        assertEquals(SlotCloseTouch.Press(500, 600), t.take(610))
        assertNull(t.take(620), "mỗi lần nhấn dùng một lần")
        // Lần nhấn chờ quá [STALE_MS] (luồng chính kẹt cỡ ANR) ⇒ bỏ, click đi đường "không ngón".
        t.down(700); t.up(800)
        assertNull(t.take(800 + SlotCloseTouch.STALE_MS + 1))
        // Hàng có trần.
        repeat(SlotCloseTouch.KEEP + 3) { i -> t.down(1_000L + i * 10); t.up(1_005L + i * 10) }
        var n = 0
        while (t.take(1_100) != null) n++
        assertEquals(SlotCloseTouch.KEEP, n)
    }

    /** `View.pointInView(x, y, slop)` (r47 `View.java:17080-17083`): biên trái/trên bao gồm, phải/dưới loại trừ. */
    @Test
    fun `trong nut theo cong thuc cua View`() {
        assertTrue(SlotCloseTouch.inView(-8f, -8f, 72, 72, 8f))
        assertFalse(SlotCloseTouch.inView(-8.1f, 0f, 72, 72, 8f))
        assertTrue(SlotCloseTouch.inView(79.9f, 79.9f, 72, 72, 8f))
        assertFalse(SlotCloseTouch.inView(80f, 10f, 72, 72, 8f))
        assertFalse(SlotCloseTouch.inView(10f, 80f, 72, 72, 8f))
    }
}
