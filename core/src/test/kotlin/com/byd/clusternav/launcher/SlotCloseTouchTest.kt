package com.byd.clusternav.launcher

import com.byd.clusternav.launcher.SlotCloseConfirm.Tap
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Soát vòng 3/4 [P3] — nút *tắt* đầu ô: nhấp đúp thật KHÔNG được tắt app kể cả khi luồng chính trễ. Mô phỏng đúng thứ tự [ĐO nguồn
 * android-10.0.0_r47] của luồng chính: sự kiện chạm vào TRƯỚC message đang xếp hàng (`MessageQueue.java:330-336`) ⇒ ở đây chạm
 * gọi thẳng, còn message chạy khi bài gọi [Button.run]; ở UP người nghe chạm post mốc lượt TRƯỚC (`View.java:13424-13430`), View
 * post click SAU (`:14820-14825`); hàng message FIFO khi cùng `when` (`MessageQueue.java:569-591`). Đồng hồ =
 * `SystemClock.uptimeMillis` (cùng gốc `MotionEvent.eventTime`).
 */
class SlotCloseTouchTest {

    /** Một nút *tắt* thu nhỏ: chính phép ghép của `SlotActionsCluster.track`/`tap` (lần nhấn → mốc → [SlotCloseConfirm.onTap]). */
    private class Button {
        val touch = SlotCloseTouch()
        private val queue = ArrayDeque<(Long) -> Unit>()
        var armedAt: Long? = null
        var lastUp: Long? = null

        /** Soát vòng 5: lúc đĩa đỏ lên khung vẽ đầu tiên (`SlotActionsCluster.markShown`); `null` = chưa vẽ. */
        var shownAt: Long? = null
        val out = ArrayList<Tap>()
        val pressOf = ArrayList<SlotCloseTouch.Press?>()

        fun down(t: Long) = touch.down(t)

        /** UP: mốc lượt vào hàng TRƯỚC; [viewClicks] = false ⇔ View không ra click (vd cửa sổ mất tiêu điểm giữa lúc nhấn). */
        fun up(t: Long, viewClicks: Boolean = true) {
            touch.up(t)?.let { p -> queue.addLast { touch.reached(p) } }
            if (viewClicks) queue.addLast { now -> click(now) }
        }

        /**
         * Luồng chính rảnh: chạy [n] message kế (mặc định hết hàng), message thứ i chạy ở giờ handler [at] + i. Dọn HẾT hàng ⇒
         * khung vẽ kế chạy ⇒ trạng thái chờ (nếu có) lên màn (soát vòng 5). Còn message (luồng chính vẫn bận) ⇒ chưa vẽ.
         * Soát vòng 6: khung ấy dài [frameMs] (traversal là một lượt WidgetFit nguội) và mốc được ghi ở CUỐI khung — `markShown`
         * `post` trong callback animation (message đồng bộ sau rào traversal ⇒ chạy khi `doFrame` xong).
         */
        fun run(at: Long, n: Int = Int.MAX_VALUE, frameMs: Long = 0) {
            var i = 0
            while (i < n && queue.isNotEmpty()) queue.removeFirst()(at + i++)
            if (queue.isEmpty() && armedAt != null && shownAt == null) shownAt = at + i + frameMs
        }

        private fun click(handlerNow: Long) {
            val p = touch.take()
            pressOf += p
            val at = p?.up ?: handlerNow
            val g = p?.let { q -> lastUp?.let { q.down - it } }
            // 300 = ViewConfiguration.DOUBLE_TAP_TIMEOUT (AOSP)
            val r = SlotCloseConfirm.onTap(armedAt, at, g, 300L, seen = SlotCloseConfirm.seen(p?.down, shownAt))
            when (r) {
                Tap.ARM -> { armedAt = at; lastUp = p?.up; shownAt = null }
                Tap.WAIT -> lastUp = p?.up
                Tap.FIRE -> { armedAt = null; shownAt = null }
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
        b.down(900); b.up(1_000)
        b.down(1_200)                     // DOWN₂ tới trước click₁ (luồng chính trễ)
        b.run(1_230)                      // mốc₁ + click₁
        b.up(1_320)
        b.run(1_330)                      // mốc₂ + click₂
        assertEquals(listOf(Tap.ARM, Tap.WAIT), b.out)
    }

    /** Trễ cả hai click tới sau UP₂ (đã nhận đủ hai lần nhấn) ⇒ mỗi click vẫn lấy ĐÚNG lần nhấn của nó, theo thứ tự UP. */
    @Test
    fun `tre ca hai click - ghep dung thu tu UP`() {
        val b = Button()
        b.down(900); b.up(1_000)
        b.down(1_150); b.up(1_260)
        b.run(1_400)
        assertEquals(listOf(Tap.ARM, Tap.WAIT), b.out)
    }

    /**
     * Soát vòng 4 [P3] — ca của người soát: luồng chính kẹt từ ngay sau UP₁ tới 2100 (khung WidgetFit nguội 0,5–0,7 s/khung trên máy
     * ảo QA). Bản vòng 3 bỏ lần nhấn₁ (quá 1 s) rồi đưa lần nhấn₂ cho click₁ ⇒ ARM ở 1250, click₂ nhận `null` ⇒ mốc 2101, cách 851
     * ms ⇒ FIRE. Nay mỗi click lấy đúng lần nhấn của nó, cũ mấy cũng dùng mốc `eventTime`.
     */
    @Test
    fun `luong chinh ket 1,1 s sau UP1 cua nhap dup - khong tat`() {
        val b = Button()
        b.down(900); b.up(1_000)
        b.down(1_150); b.up(1_250)        // cả hai tới trong lúc kẹt, được phát ngay khi hết kẹt
        b.run(2_100)                      // mốc₁ 2100 · click₁ 2101 · mốc₂ 2102 · click₂ 2103
        assertEquals(listOf(Tap.ARM, Tap.WAIT), b.out)
        assertEquals(listOf(SlotCloseTouch.Press(900, 1_000), SlotCloseTouch.Press(1_150, 1_250)), b.pressOf)
    }

    /**
     * Soát vòng 4 — vì sao KHÔNG trả `null` cho lần nhấn quá tuổi (bản sửa gợi ý ban đầu): kẹt tới 2100, rồi một khung 0,6 s chen
     * GIỮA click₁ và click₂ ⇒ cả hai lần nhấn đều "quá 1 s"; mốc giờ handler 2101 và 2702 cách 601 ms ⇒ FIRE. Mốc `eventTime`
     * (UP₁ 1000 · DOWN₂ 1150) ⇒ WAIT.
     */
    @Test
    fun `them mot khung dai chen giua hai click - van khong tat`() {
        val b = Button()
        b.down(900); b.up(1_000)
        b.down(1_150); b.up(1_250)
        b.run(2_100, n = 2)               // mốc₁ + click₁
        b.run(2_701)                      // khung 0,6 s xong ⇒ mốc₂ + click₂
        assertEquals(listOf(Tap.ARM, Tap.WAIT), b.out)
    }

    /**
     * Soát vòng 4 — trần hàng cũ (4, bỏ lần CŨ NHẤT) lệch cặp sau ≥ 5 cú chạm dồn trong một lần kẹt: click₁ nhận lần nhấn₃ (ARM ở
     * 1350), click₅ hết lần nhấn ⇒ mốc giờ handler 1908, cách 558 ms ⇒ FIRE. Nay không trần: sáu click, sáu lần nhấn đúng thứ tự.
     */
    @Test
    fun `sau cu cham don trong mot lan ket - khong tat`() {
        val b = Button()
        repeat(6) { i -> b.down(1_000L + i * 150); b.up(1_050L + i * 150) }   // DOWN cách UP trước 100 ms < nhịp nhấp đúp
        b.run(1_900)
        assertEquals(listOf(Tap.ARM) + List(5) { Tap.WAIT }, b.out)
        assertEquals((0 until 6).map { i -> SlotCloseTouch.Press(1_000L + i * 150, 1_050L + i * 150) }, b.pressOf)
    }

    /**
     * Soát vòng 4 — lần nhấn mà View KHÔNG ra click (cửa sổ mất tiêu điểm giữa lúc nhấn — r47 `View.java:13716-13719`) không được
     * nằm đầu hàng lệch mọi cặp sau: mốc lượt của lần nhấn kế dọn nó. Thiếu [SlotCloseTouch.reached] ⇒ click của cú chạm kế nhận
     * lần nhấn ma (mốc 200) ⇒ xác nhận có chủ ý 700 ms sau không tắt.
     */
    @Test
    fun `lan nhan View khong ra click bi don o moc luot ke`() {
        val b = Button()
        b.down(100); b.up(200, viewClicks = false)
        b.run(210)                        // chỉ mốc lượt, không click
        b.down(5_000); b.up(5_090); b.run(5_100)
        b.down(5_700); b.up(5_790); b.run(5_800)
        assertEquals(listOf(SlotCloseTouch.Press(5_000, 5_090), SlotCloseTouch.Press(5_700, 5_790)), b.pressOf)
        assertEquals(listOf(Tap.ARM, Tap.FIRE), b.out)
    }

    /**
     * Soát vòng 5 [P3] — ca của người soát: Google Maps trong ô, người lái chạm ô tìm kiếm (nằm dưới *tắt*, UP₁ = 1000), luồng
     * chính kẹt ~2 s (khung WidgetFit nguội 0,5–0,7 s/khung trên máy ảo QA), "không thấy gì xảy ra" nên chạm lại sau 600 ms
     * (DOWN₂ 1600, UP₂ 1700). Hết kẹt: click₁ ARM (mốc 1000) và click₂ chạy CÙNG lượt dọn hàng, trước mọi khung vẽ ⇒ khoảng
     * DOWN₂ − UP₁ = 600 ms ≥ nhịp nhấp đúp, cách lượt đầu 700 ms < 2 s ⇒ bản vòng 4 FIRE (`am stack remove`) ở MỌI độ dài kẹt.
     * Nay: chưa vẽ đĩa đỏ ⇒ WAIT; thấy đỏ rồi chạm lại ⇒ tắt.
     */
    @Test
    fun `luong chinh ket 2 s, cham lai truoc khi dia do duoc ve - khong tat`() {
        for (stallEnd in listOf(2_500L, 2_900L, 3_000L)) {
            val b = Button()
            b.down(900); b.up(1_000)
            b.down(1_600); b.up(1_700)
            b.run(stallEnd)
            assertEquals(listOf(Tap.ARM, Tap.WAIT), b.out, "kẹt tới $stallEnd")
        }
        // Thấy đĩa đỏ (khung vẽ ngay sau lượt dọn hàng 2500) rồi chạm lại trong cửa sổ ⇒ tắt.
        val b = Button()
        b.down(900); b.up(1_000)
        b.down(1_600); b.up(1_700)
        b.run(2_500)
        b.down(2_800); b.up(2_880); b.run(2_890)
        assertEquals(listOf(Tap.ARM, Tap.WAIT, Tap.FIRE), b.out)
    }

    /**
     * Soát vòng 6 [P3] — ca của người soát: Kachi vừa khởi động (khung WidgetFit nguội 0,5–0,7 s). UP₁ = 1000; khung A kẹt tới 1600,
     * click₁ ARM lúc 1601; khung B bắt đầu ngay sau, traversal của nó là một lượt khớp nguội nữa (600 ms) ⇒ đĩa đỏ lên màn ~2200.
     * Người lái không thấy gì nên chạm lại GIỮA khung B (DOWN₂ 1900, UP₂ 1980), click₂ chạy 2205. Mốc ở pha ANIMATION của khung B
     * (bản vòng 5 — đầu khung) ⇒ DOWN₂ ≥ mốc ⇒ FIRE (`am stack remove`) dù đĩa đỏ chưa từng lên màn. Mốc ở CUỐI khung ⇒ WAIT.
     */
    @Test
    fun `khung dai ngay sau ARM - cham lai giua khung chua ve xong khong tat`() {
        val b = Button()
        b.down(900); b.up(1_000)
        b.run(1_601, frameMs = 600)       // mốc₁ + click₁ (ARM); khung B 1603 → 2203
        assertTrue(b.shownAt!! > 1_980, "mốc ở cuối khung B, sau cú chạm giữa khung")
        b.down(1_900); b.up(1_980)        // chạm giữa khung B — được phát khi khung xong
        b.run(2_205)
        assertEquals(listOf(Tap.ARM, Tap.WAIT), b.out)
        // Thấy đĩa đỏ rồi chạm lại trong cửa sổ ⇒ tắt.
        b.down(2_400); b.up(2_480); b.run(2_490)
        assertEquals(listOf(Tap.ARM, Tap.WAIT, Tap.FIRE), b.out)
        // Bản vòng 5 (mốc ở pha animation = ĐẦU khung B) ⇒ cùng chuỗi chạm thành FIRE — điều bài này khoá.
        val old = Button()
        old.down(900); old.up(1_000)
        old.run(1_601)
        old.down(1_900); old.up(1_980); old.run(2_205)
        assertEquals(listOf(Tap.ARM, Tap.FIRE), old.out)
    }

    /** Xác nhận CÓ CHỦ Ý (thấy đĩa đỏ, chạm lại sau 700 ms) vẫn tắt — dù click chạy trễ. */
    @Test
    fun `xac nhan co chu y van tat`() {
        val b = Button()
        b.down(900); b.up(1_000); b.run(1_010)
        b.down(1_700); b.up(1_790); b.run(1_950)
        assertEquals(listOf(Tap.ARM, Tap.FIRE), b.out)
    }

    /** Trượt khỏi nút (View không ra click) ⇒ lần nhấn KHÔNG vào hàng; click trợ năng không có lần nhấn ⇒ `null`. */
    @Test
    fun `truot khoi nut va click khong den tu ngon`() {
        val t = SlotCloseTouch()
        t.down(100); t.left()
        assertNull(t.up(200), "trượt khỏi nút ⇒ không có lần nhấn")
        assertNull(t.take(), "trượt khỏi nút ⇒ không có click để ghép")
        t.down(300); t.cancel()
        assertNull(t.up(400), "CANCEL ⇒ không click")
        assertNull(t.take())
        t.down(500)
        val p = t.up(600)
        assertEquals(SlotCloseTouch.Press(500, 600), p)
        assertEquals(p, t.take())
        assertNull(t.take(), "mỗi lần nhấn dùng một lần")
        // Lần nhấn cũ bao lâu cũng vẫn là lần nhấn của click kế (không ngưỡng tuổi): luồng chính kẹt 10 s vẫn ghép đúng.
        t.down(700); t.up(800)
        assertEquals(SlotCloseTouch.Press(700, 800), t.take())
    }

    /** Mốc lượt của lần nhấn ĐÃ được lấy (click chạy đồng bộ khi `post` hỏng — r47 `View.java:14823-14825`) không đụng hàng. */
    @Test
    fun `moc luot cua lan nhan da lay khong xoa hang`() {
        val t = SlotCloseTouch()
        t.down(100); val p1 = t.up(200)!!
        assertEquals(p1, t.take())
        t.down(300); t.up(400)
        t.reached(p1)
        assertEquals(SlotCloseTouch.Press(300, 400), t.take())
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
