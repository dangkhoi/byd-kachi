package com.byd.clusternav.launcher.camera

import com.byd.clusternav.launcher.camera.CameraSignalPolicy.Turn
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Khoá BG-13 (`perf-inventory-2026-09-25.md`): HOLD hết hạn phải được HẸN đúng mốc (postDelayed) thay vì vòng
 * automation 250 ms gọi `tick(false,false)` 4 lần/giây. Hành vi tắt KHÔNG đổi: tắt sau HOLD_MS kể từ ON cuối;
 * pha TẮT của nháy (~340 ms) không đóng camera ([ĐO xe 2026-09-24] nháy ~1.5 Hz).
 */
class CameraHoldTest {

    @Test
    fun `ON at 0, 340, 680 - tat dung tai 680 + HOLD, khong tat o pha OFF giua chung`() {
        val hold = CameraHold(1_200L)
        assertEquals(Turn.LEFT, hold.observe(left = true, right = null, nowMs = 0))
        assertEquals(Turn.LEFT, hold.observe(left = false, right = null, nowMs = 170))   // pha OFF của nháy
        assertEquals(Turn.LEFT, hold.observe(left = true, right = null, nowMs = 340))
        assertEquals(Turn.LEFT, hold.observe(left = false, right = null, nowMs = 510))
        assertEquals(Turn.LEFT, hold.observe(left = true, right = null, nowMs = 680))
        // Không còn ON: giữ tới đúng 680 + 1200 = 1880, sang 1881 mới tắt.
        assertEquals(Turn.LEFT, hold.observe(null, null, nowMs = 1_880))
        assertEquals(Turn.NONE, hold.observe(null, null, nowMs = 1_881))
    }

    @Test
    fun `expiresInMs hen dung moc - moi ON day lui, khong ON thi null`() {
        val hold = CameraHold(1_200L)
        assertNull(hold.expiresInMs(nowMs = 0), "chưa ON ⇒ không có gì để hẹn")
        hold.observe(true, null, 0);   assertEquals(1_201L, hold.expiresInMs(0))
        hold.observe(true, null, 340); assertEquals(1_201L, hold.expiresInMs(340))
        hold.observe(false, null, 510); assertEquals(1_031L, hold.expiresInMs(510))   // 340+1200+1-510
        hold.observe(true, null, 680); assertEquals(1_201L, hold.expiresInMs(680))
        // Hẹn đúng 1201 ms từ 680 ⇒ runnable chạy ở 1881 ⇒ observe(null,null,1881) = NONE, và không còn gì để hẹn.
        assertEquals(Turn.NONE, hold.observe(null, null, 1_881))
        assertNull(hold.expiresInMs(1_881))
    }

    /**
     * Mô phỏng đúng cơ chế trong `CameraSignalController.tickMain`: mỗi nhịp `removeCallbacks` + `postDelayed(expiresInMs)`.
     * Đồng hồ giả + hàng đợi một mục. Chứng minh: (a) số lần "tick" = số sự kiện + 1 (không có 4 Hz), (b) camera tắt
     * ĐÚNG tại 680 + HOLD + 1 (= mốc đầu tiên mà HOLD đã trôi qua), không sớm hơn.
     */
    @Test
    fun `mo phong postDelayed - 3 su kien ON tao dung 4 nhip, tat tai 1881`() {
        val hold = CameraHold(1_200L)
        var dueAt: Long? = null
        var ticks = 0
        var turnedOffAt: Long? = null
        var current = Turn.NONE
        fun tick(left: Boolean?, now: Long) {
            ticks++
            val turn = hold.observe(left, null, now)
            dueAt = hold.expiresInMs(now)?.let { now + it }   // removeCallbacks + postDelayed
            if (turn != current) { current = turn; if (turn == Turn.NONE) turnedOffAt = now }
        }
        val events = listOf(0L, 340L, 680L)
        events.forEach { tick(true, it) }
        // Không còn sự kiện: chạy runnable hẹn giờ cho tới khi không còn gì hẹn.
        var now = events.last()
        while (dueAt != null) { now = dueAt!!; tick(null, now) }
        assertEquals(4, ticks, "3 sự kiện + 1 lần hết hạn — trước vá là ~8 nhịp/2 s ở 250 ms")
        assertEquals(1_881L, turnedOffAt)
        assertEquals(Turn.NONE, current)
    }

    @Test
    fun `hai ben - hen theo ben het han TRUOC, sau do hen lai cho ben con lai`() {
        val hold = CameraHold(1_200L)
        hold.observe(true, null, 0)        // trái ON @0
        hold.observe(null, true, 500)      // phải ON @500 ⇒ cả hai ⇒ NONE (turnOf)
        assertEquals(701L, hold.expiresInMs(500), "mốc gần nhất = trái hết hạn @1201")
        assertEquals(Turn.RIGHT, hold.observe(null, null, 1_201), "trái hết hạn ⇒ chỉ còn phải")
        assertEquals(500L, hold.expiresInMs(1_201), "hẹn lại cho phải @1701")
        assertEquals(Turn.NONE, hold.observe(null, null, 1_701))
    }
}
