package com.byd.clusternav.system.inputd

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * [GestureFallback] — bài canh cho bệnh *"vuốt trong ô chỉ nhận tap, mà tap lại lệch"*
 * ([ĐO xe 2026-09-16] `docs/diagnostics/oncar-trace-2026-09-16b/README.md` §9.1).
 *
 * Mỗi bài khoá **một** hành vi mà bản trước 1.69 làm sai; xoá dòng vá tương ứng là bài đó đỏ.
 */
class GestureFallbackTest {

    /** Cùng cỡ với một máy mdpi thường (`scaledTouchSlop` 8–24 px tuỳ mật độ) + trần giữ chuẩn Android. */
    private fun fb(slop: Int = 16, longPress: Long = 500L) = GestureFallback(slop, longPress)

    /** Ba hằng của nền tảng — lệch một số là mọi bài dưới đây đo nhầm sự kiện mà vẫn xanh. */
    @Test
    fun `hang action la guong dung cua MotionEvent`() {
        assertEquals(0, GestureFallback.ACTION_DOWN)
        assertEquals(1, GestureFallback.ACTION_UP)
        assertEquals(2, GestureFallback.ACTION_MOVE)
        assertEquals(3, GestureFallback.ACTION_CANCEL)
        assertEquals(5, GestureFallback.ACTION_POINTER_DOWN)
        assertEquals(6, GestureFallback.ACTION_POINTER_UP)
    }

    /**
     * KHOÁ: chạm tại chỗ ⇒ **ĐÚNG MỘT** lệnh tap, phát tại UP.
     *
     * Bản trước 1.69 bắn `input … tap` ở **cả** DOWN **lẫn** UP ⇒ hai cú tap cho một cú chạm — đúng triệu chứng
     * *"tap không chính xác lắm"* owner báo trên xe.
     */
    @Test
    fun `tap - DOWN roi UP tai cho chi ban DUNG MOT lenh`() {
        val g = fb()
        assertNull(g.feed(GestureFallback.ACTION_DOWN, 7, 640, 360, 1_000L), "DOWN KHÔNG được bắn lệnh nào")
        assertNull(g.feed(GestureFallback.ACTION_MOVE, 7, 641, 361, 1_020L), "MOVE trong slop cũng không bắn")
        assertEquals(
            "input -d 7 tap 640 360",
            g.feed(GestureFallback.ACTION_UP, 7, 642, 359, 1_080L),
            "cả cử chỉ chỉ ra MỘT lệnh, toạ độ lấy ở điểm NHẤN (không phải điểm nhả, vốn lệch vài px do rung tay)",
        )
        assertFalse(g.isTracking())
    }

    /**
     * KHOÁ: vuốt DỌC ⇒ `swipe`, thời gian thật của cử chỉ.
     *
     * Đây chính là cú cuộn YouTube mà xe không làm được: trước 1.69 MOVE bị bỏ và UP chỉ sinh thêm một `tap`.
     */
    @Test
    fun `vuot doc - qua touch slop thi ban swipe voi thoi gian that`() {
        val g = fb()
        g.feed(GestureFallback.ACTION_DOWN, 3, 1000, 850, 0L)
        g.feed(GestureFallback.ACTION_MOVE, 3, 1000, 700, 120L)
        g.feed(GestureFallback.ACTION_MOVE, 3, 1000, 500, 260L)
        assertEquals(
            "input -d 3 swipe 1000 850 1000 350 400",
            g.feed(GestureFallback.ACTION_UP, 3, 1000, 350, 400L),
            "đúng chuỗi mà phép đo trên xe đã dùng tay chạy được (`input swipe 1000 850 1000 350 400`)",
        )
    }

    /** KHOÁ: vuốt NGANG cũng đi đường swipe — không có nhánh nào chỉ chạy cho trục dọc. */
    @Test
    fun `vuot ngang - cung ra swipe, khong phai tap`() {
        val g = fb()
        g.feed(GestureFallback.ACTION_DOWN, 2, 100, 400, 5_000L)
        g.feed(GestureFallback.ACTION_MOVE, 2, 300, 404, 5_100L)
        assertEquals(
            "input -d 2 swipe 100 400 700 402 250",
            g.feed(GestureFallback.ACTION_UP, 2, 700, 402, 5_250L),
        )
    }

    /** KHOÁ: cử chỉ nhanh hơn sàn vẫn được kéo lên [GestureFallback.MIN_SWIPE_MS] (0 ms ⇒ app nhận cú nhảy, không phải cuộn). */
    @Test
    fun `vuot nhanh hon san thi thoi gian bi keo len 60 ms`() {
        val g = fb()
        g.feed(GestureFallback.ACTION_DOWN, 1, 10, 10, 900L)
        assertEquals(
            "input -d 1 swipe 10 10 400 10 60",
            g.feed(GestureFallback.ACTION_UP, 1, 400, 10, 910L),
            "10 ms thật ⇒ kéo lên 60 ms",
        )
    }

    /** KHOÁ: đúng ngưỡng slop thì **chưa** phải vuốt (so sánh phải là `>`, không phải `>=`). */
    @Test
    fun `dung bang touch slop van la tap`() {
        val g = fb(slop = 16)
        g.feed(GestureFallback.ACTION_DOWN, 4, 0, 0, 0L)
        assertEquals("input -d 4 tap 0 0", g.feed(GestureFallback.ACTION_UP, 4, 16, 0, 50L))
        g.feed(GestureFallback.ACTION_DOWN, 4, 0, 0, 0L)
        assertEquals("input -d 4 swipe 0 0 17 0 60", g.feed(GestureFallback.ACTION_UP, 4, 17, 0, 50L))
    }

    /**
     * KHOÁ: GIỮ LÂU tại chỗ ⇒ `swipe` cùng điểm đầu-cuối với đúng quãng giữ.
     *
     * `input` không có lệnh long-press; swipe cùng điểm là cách duy nhất nó diễn đạt một cú giữ. Trả về `tap` ở
     * đây là mất hẳn mọi menu ngữ cảnh trong ô (đổi tên, xoá, chọn chữ…).
     */
    @Test
    fun `giu lau - swipe cung mot diem voi quang giu`() {
        val g = fb(longPress = 500L)
        g.feed(GestureFallback.ACTION_DOWN, 9, 320, 240, 2_000L)
        g.feed(GestureFallback.ACTION_MOVE, 9, 322, 241, 2_300L)
        assertEquals(
            "input -d 9 swipe 320 240 320 240 900",
            g.feed(GestureFallback.ACTION_UP, 9, 323, 239, 2_900L),
        )
    }

    /** KHOÁ: đúng bằng trần giữ thì vẫn là tap (`>`, không `>=`) — biên của nhánh trên. */
    @Test
    fun `dung bang tran giu van la tap`() {
        val g = fb(longPress = 500L)
        g.feed(GestureFallback.ACTION_DOWN, 9, 5, 5, 0L)
        assertEquals("input -d 9 tap 5 5", g.feed(GestureFallback.ACTION_UP, 9, 5, 5, 500L))
    }

    /** KHOÁ: ACTION_CANCEL ⇒ **không** lệnh nào. Cử chỉ bị hệ thống thu hồi không được biến thành một cú chạm thật. */
    @Test
    fun `ACTION_CANCEL khong ban lenh nao`() {
        val g = fb()
        g.feed(GestureFallback.ACTION_DOWN, 1, 10, 10, 0L)
        g.feed(GestureFallback.ACTION_MOVE, 1, 300, 300, 100L)
        assertNull(g.feed(GestureFallback.ACTION_CANCEL, 1, 300, 300, 120L))
        assertFalse(g.isTracking())
        assertNull(g.feed(GestureFallback.ACTION_UP, 1, 300, 300, 140L), "UP sau CANCEL không được hồi sinh cử chỉ")
    }

    /**
     * KHOÁ: ngón THỨ HAI bị bỏ qua, cử chỉ của ngón đầu vẫn ra **đúng một** lệnh.
     *
     * `input` không diễn đạt được đa điểm chạm. Huỷ cả cử chỉ khi có ngón thứ hai thì một cú chạm hờ của bàn tay
     * cầm vô-lăng sẽ nuốt mất cú vuốt thật của ngón kia.
     */
    @Test
    fun `da diem cham - ngon thu hai bi bo qua, ngon dau van ra dung mot lenh`() {
        val g = fb()
        g.feed(GestureFallback.ACTION_DOWN, 5, 100, 800, 0L)
        assertNull(g.feed(GestureFallback.ACTION_POINTER_DOWN, 5, 900, 100, 40L, pointerId = 1))
        assertNull(g.feed(GestureFallback.ACTION_MOVE, 5, 100, 600, 80L))
        assertNull(g.feed(GestureFallback.ACTION_POINTER_UP, 5, 900, 120, 120L, pointerId = 1))
        assertTrue(g.isTracking(), "ngón thứ hai lên/xuống KHÔNG được làm rơi cử chỉ đang gom")
        assertEquals(
            "input -d 5 swipe 100 800 100 300 200",
            g.feed(GestureFallback.ACTION_UP, 5, 100, 300, 200L),
        )
    }

    /** KHOÁ: UP của một ngón KHÁC (ngón đầu đã rời trước) ⇒ không bịa ra cử chỉ theo toạ độ ngón kia. */
    @Test
    fun `UP cua ngon khac khong sinh lenh`() {
        val g = fb()
        g.feed(GestureFallback.ACTION_DOWN, 5, 100, 800, 0L)
        assertNull(g.feed(GestureFallback.ACTION_UP, 5, 900, 100, 200L, pointerId = 1))
        assertFalse(g.isTracking())
    }

    /** KHOÁ: MOVE/UP mồ côi (daemon rớt GIỮA cử chỉ ⇒ đường lùi vào cuộc từ giữa chừng) không được bắn gì. */
    @Test
    fun `MOVE hoac UP khong co DOWN thi im lang`() {
        val g = fb()
        assertNull(g.feed(GestureFallback.ACTION_MOVE, 1, 10, 10, 0L))
        assertNull(g.feed(GestureFallback.ACTION_UP, 1, 10, 10, 20L))
    }

    /** KHOÁ: [GestureFallback.reset] bỏ cử chỉ dở dang (daemon sống lại giữa chừng ⇒ không phát thêm lệnh lùi). */
    @Test
    fun `reset bo cu chi do dang`() {
        val g = fb()
        g.feed(GestureFallback.ACTION_DOWN, 1, 10, 10, 0L)
        g.reset()
        assertNull(g.feed(GestureFallback.ACTION_UP, 1, 10, 10, 20L))
    }

    /** KHOÁ: đồng hồ lùi (đổi giờ giữa cử chỉ) không sinh thời lượng ÂM trong chuỗi lệnh. */
    @Test
    fun `dong ho lui khong sinh thoi luong am`() {
        val g = fb()
        g.feed(GestureFallback.ACTION_DOWN, 1, 0, 0, 5_000L)
        assertEquals("input -d 1 tap 0 0", g.feed(GestureFallback.ACTION_UP, 1, 0, 0, 4_000L))
    }

    /** KHOÁ: mỗi cử chỉ dùng `displayId` của **chính** nó (hai ô ⇒ hai màn ảo; một bộ gom mỗi ô). */
    @Test
    fun `display id lay tu su kien DOWN cua chinh cu chi do`() {
        val g = fb()
        g.feed(GestureFallback.ACTION_DOWN, 11, 0, 0, 0L)
        assertEquals("input -d 11 tap 0 0", g.feed(GestureFallback.ACTION_UP, 11, 0, 0, 10L))
    }
}
