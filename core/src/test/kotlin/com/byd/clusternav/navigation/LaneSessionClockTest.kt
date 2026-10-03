package com.byd.clusternav.navigation

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * FIX286 R-HUD (S9) — khoá phép bỏ "khung cũ hơn lúc mở phiên" của `ClusterBroadcaster.emitLane` khi đồng hồ tường xe đổi.
 * Phản biện HUD H7b: phép so cũ dùng hai giờ TƯỜNG ⇒ đồng hồ lùi sau khi mở phiên là mọi khung mới bị bỏ.
 */
class LaneSessionClockTest {

    private val startWall = 1_759_450_000_000L
    private val startElapsed = 5_000_000L

    @Test
    fun `dong ho dung yen - y het phep so cu`() {
        val nowWall = startWall + 2_000
        val nowElapsed = startElapsed + 2_000
        assertFalse(LaneSessionClock.isBeforeSession(startWall + 1_500, startWall, startElapsed, nowWall, nowElapsed))
        assertFalse(LaneSessionClock.isBeforeSession(startWall, startWall, startElapsed, nowWall, nowElapsed), "cùng ms mở phiên ⇒ giữ")
        assertTrue(LaneSessionClock.isBeforeSession(startWall - 1, startWall, startElapsed, nowWall, nowElapsed))
    }

    @Test
    fun `dong ho tuong LUI 1 gio sau khi mo phien - khung moi KHONG bi bo`() {
        val nowElapsed = startElapsed + 60_000
        val nowWall = startWall + 60_000 - 3_600_000   // xe đồng bộ giờ, lùi 1 giờ
        val fresh = nowWall - 50                       // khung vừa nhận, theo giờ tường MỚI
        assertTrue(fresh < startWall, "điều kiện của lỗi cũ: giờ tường khung < mốc phiên")
        assertFalse(LaneSessionClock.isBeforeSession(fresh, startWall, startElapsed, nowWall, nowElapsed))
    }

    @Test
    fun `khung luu tu luot truoc van bi bo ke ca khi dong ho lui`() {
        val nowElapsed = startElapsed + 60_000
        val nowWall = startWall + 60_000 - 3_600_000
        val persistedYesterday = startWall - 86_400_000
        assertTrue(LaneSessionClock.isBeforeSession(persistedYesterday, startWall, startElapsed, nowWall, nowElapsed))
    }

    @Test
    fun `dong ho tuong TIEN - khong noi long cung khong bo nham`() {
        val nowElapsed = startElapsed + 10_000
        val nowWall = startWall + 10_000 + 3_600_000
        assertFalse(LaneSessionClock.isBeforeSession(nowWall - 10, startWall, startElapsed, nowWall, nowElapsed))
        assertTrue(LaneSessionClock.isBeforeSession(startWall - 1, startWall, startElapsed, nowWall, nowElapsed))
    }

    @Test
    fun `khung khong co moc thi khong bo`() {
        assertFalse(LaneSessionClock.isBeforeSession(0L, startWall, startElapsed, startWall, startElapsed))
        assertFalse(LaneSessionClock.isBeforeSession(-5L, startWall, startElapsed, startWall, startElapsed))
    }
}
