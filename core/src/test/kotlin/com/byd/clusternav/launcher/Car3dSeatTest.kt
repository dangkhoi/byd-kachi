package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

/** POLISH2 — một widget 3D (một Engine) · cổng cỡ ô lúc dựng · hàng nút chân canh GIỮA. */
class Car3dSeatTest {

    @Test
    fun `nguoi moi thang - chu cu bi day ve 2D truoc khi nguoi moi dung Engine`() {
        // [ĐO xe 14/10 08:01:38] bật màn ⇒ HOME dựng lại ô: widget mới xin chỗ khi widget cũ còn giữ ⇒ luật cũ đẩy widget ĐANG HIỆN về 2D.
        val seat = Car3dSeat(); val a = Any(); val b = Any(); val log = ArrayList<String>()
        assertTrue(seat.claim(a) { log += "a evicted" }); assertTrue(seat.claim(a), "chủ xin lại vẫn được, không tự đuổi")
        assertTrue(log.isEmpty())
        assertTrue(seat.claim(b) { log += "b evicted" }); log += "b builds Engine"
        assertEquals(listOf("a evicted", "b builds Engine"), log, "chủ cũ huỷ Engine TRƯỚC khi người mới dựng ⇒ tối đa một Engine")
        assertTrue(seat.holds(b)); assertFalse(seat.holds(a))
        seat.release(a); assertTrue(seat.holds(b), "không phải chủ thì không trả được chỗ")
        seat.release(b); assertFalse(seat.holds(b))
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
