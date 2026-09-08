package com.byd.clusternav.navigation

import com.byd.clusternav.vietmapwidget.VietMapRoadAlert
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Khoá logic thuần [RoadAlertChipDecision] (B3.20). Không Android — chạy off-car.
 */
class RoadAlertChipDecisionTest {

    private fun alert(limit: Int? = null, distM: Int? = null, distText: String? = null, icon: Boolean = false) =
        VietMapRoadAlert(speedLimitKph = limit, distanceText = distText, distanceMeters = distM, imageVisible = icon, imageHash = null)

    @Test
    fun `khong tuoi thi an`() {
        assertEquals(RoadAlertChip.HIDDEN, RoadAlertChipDecision.decide(listOf(alert(limit = 60, distM = 300)), fresh = false))
    }

    @Test
    fun `rong thi an`() {
        assertFalse(RoadAlertChipDecision.decide(emptyList(), fresh = true).show)
    }

    @Test
    fun `camera co gioi han + cu ly → hien du field`() {
        val d = RoadAlertChipDecision.decide(listOf(alert(limit = 60, distM = 300, distText = "300 m", icon = true)), fresh = true)
        assertTrue(d.show)
        assertEquals(60, d.limitKph)
        assertEquals(300, d.distanceMeters)
        assertEquals("300 m", d.distanceText)
        assertTrue(d.hasIcon)
    }

    @Test
    fun `chon alert GAN NHAT theo cu ly`() {
        val d = RoadAlertChipDecision.decide(
            listOf(alert(limit = 80, distM = 900), alert(limit = 50, distM = 200), alert(limit = 60, distM = 500)),
            fresh = true,
        )
        assertEquals(50, d.limitKph, "phải chọn alert gần nhất (200m)")
        assertEquals(200, d.distanceMeters)
    }

    @Test
    fun `alert da qua (cu ly biet am hoac 0) bi bo`() {
        // Cái duy nhất còn hạn là 400m; cái -5 (đã qua) phải bị loại dù đứng trước.
        val d = RoadAlertChipDecision.decide(listOf(alert(limit = 40, distM = -5), alert(limit = 70, distM = 400)), fresh = true)
        assertTrue(d.show)
        assertEquals(70, d.limitKph)
        assertEquals(400, d.distanceMeters)
    }

    @Test
    fun `alert TRONG (khong limit, khong icon) → an`() {
        assertFalse(RoadAlertChipDecision.decide(listOf(alert(limit = null, distM = 300, icon = false)), fresh = true).show)
    }

    @Test
    fun `chi co icon (khong limit) van hien`() {
        val d = RoadAlertChipDecision.decide(listOf(alert(limit = null, distM = 250, icon = true)), fresh = true)
        assertTrue(d.show)
        assertEquals(0, d.limitKph, "không có limit ⇒ 0 (renderer bỏ vòng tròn limit)")
        assertTrue(d.hasIcon)
    }

    @Test
    fun `cu ly null (chua biet) van hien voi distanceMeters 0`() {
        val d = RoadAlertChipDecision.decide(listOf(alert(limit = 60, distM = null, icon = true)), fresh = true)
        assertTrue(d.show)
        assertEquals(0, d.distanceMeters, "cự ly chưa biết ⇒ 0 (renderer bỏ đếm lùi), vẫn hiện chip")
    }
}
