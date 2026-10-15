package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

/** POLISH2 — một widget 3D (một Engine) · cổng cỡ ô lúc dựng · hàng nút chân canh GIỮA · 3.1.8 bên thua CHỜ, không lùi 2D. */
class Car3dSeatTest {

    @Test
    fun `chu khuat thi nguoi moi thang - chu cu bi day TRUOC khi nguoi moi dung Engine, roi cho`() {
        // [ĐO xe 14/10 08:01:38] bật màn ⇒ HOME dựng lại ô: bản mới xin chỗ khi bản cũ (khuất) còn giữ ⇒ bản mới phải thắng.
        val seat = Car3dSeat(); val a = Any(); val b = Any(); val log = ArrayList<String>()
        var aShown = true
        assertTrue(seat.claim(a, { aShown }, { log += "a woken" }) { log += "a evicted" }); assertTrue(seat.claim(a), "chủ xin lại vẫn được, không tự đuổi")
        assertTrue(log.isEmpty())
        aShown = false
        assertTrue(seat.claim(b, wake = { log += "b woken" }) { log += "b evicted" }); log += "b builds Engine"
        assertEquals(listOf("a evicted", "b builds Engine"), log, "chủ cũ huỷ Engine TRƯỚC khi người mới dựng ⇒ tối đa một Engine")
        assertTrue(seat.holds(b)); assertFalse(seat.holds(a)); assertTrue(seat.waits(a), "bị đẩy ⇒ CHỜ (3.1.8), không lùi 2D")
        seat.release(a); assertTrue(seat.holds(b), "không phải chủ thì không trả được chỗ")
        seat.release(b); assertFalse(seat.holds(b))
        assertEquals("a woken", log.last(), "chỗ trống ⇒ bản bị đẩy được đánh thức để dựng lại 3D")
    }

    @Test
    fun `chu dang hien thi nguoi xin cho - khong bao gio day widget dang hien, cho trong thi danh thuc`() {
        // 3.1.8 CAR3D-FALLBACK-STICKY [ĐO máy ảo 15/10, 3.1.6]: hai ô w_car3d ⇒ ô chính thành bảng 2D, bung ô chính vẫn 2D (không ai đánh thức).
        val seat = Car3dSeat(); val main = Any(); val second = Any(); val log = ArrayList<String>()
        assertTrue(seat.claim(main, { true }, { log += "main woken" }) { log += "main evicted" })
        assertFalse(seat.claim(second, { true }, { log += "second woken" }, evict = { log += "second evicted" }), "chủ đang hiện ⇒ người xin chờ")
        assertTrue(seat.holds(main) && seat.waits(second) && log.isEmpty(), "không đẩy chủ đang hiện: $log")
        seat.release(main)   // ô chính khuất (ô kia bung R30) ⇒ nhả Engine
        assertEquals(listOf("second woken"), log, "chỗ trống ⇒ bản chờ được đánh thức")
        assertFalse(seat.waits(second), "đánh thức xong ⇒ rời hàng chờ (xin lại qua claim)")
        assertTrue(seat.claim(second, { true }, { log += "second woken" }))
    }

    @Test
    fun `roi cua so thi ra khoi hang cho - khong danh thuc view chet`() {
        val seat = Car3dSeat(); val a = Any(); val gone = Any(); val log = ArrayList<String>()
        assertTrue(seat.claim(a))
        assertFalse(seat.claim(gone, wake = { log += "gone woken" }))
        seat.leave(gone); assertFalse(seat.waits(gone))
        seat.release(a)
        assertTrue(log.isEmpty(), "view đã rời cửa sổ không được đánh thức: $log")
        seat.leave(a); assertFalse(seat.holds(a))
    }

    @Test
    fun `o nho hon minSlotDp hoac chua do thi khong dung 3D`() {
        val (w, h) = WidgetRegistry.LARGE_SLOT_DP
        assertTrue(Car3dSeat.fits("w_car3d", w, h))
        assertFalse(Car3dSeat.fits("w_car3d", w - 1, h)); assertFalse(Car3dSeat.fits("w_car3d", w, h - 1))
        assertFalse(Car3dSeat.fits("w_car3d", null, null))
        assertTrue(Car3dSeat.fits("w_clock", 10, 10), "widget không đòi cỡ ⇒ luôn đủ")
    }

    @Test
    fun `hang nut chan canh giua khung o moi co`() {
        for (total in listOf(267, 600, 640, 901, 1200)) for (n in 1..4) {
            val item = 72; val gap = 12
            val x0 = CenteredRow.start(total, n, item, gap)
            val groupCentre = x0 + CenteredRow.width(n, item, gap) / 2.0
            assertTrue(abs(groupCentre - total / 2.0) <= 1.0, "khung $total, $n nút: tâm cụm $groupCentre ≠ tâm khung ${total / 2.0}")
            // Xếp bằng lề trái từng nút: tổng đúng bằng bề rộng cụm (không khe thừa ở đầu ⇒ không lệch nửa khe).
            assertEquals(CenteredRow.width(n, item, gap), (0 until n).sumOf { CenteredRow.leading(it, gap) + item })
        }
    }
}
