package com.byd.clusternav.launcher.automation

import com.byd.clusternav.launcher.automation.ScheduledNavRules

import com.byd.clusternav.launcher.telegram.PendingDestinations
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PendingNavPolicyTest {

    private fun pending() =
        PendingDestinations.of("Office", "Keangnam 72", listOf("gmaps", "vietmap"), "21.0, 105.8")!!

    @Test
    fun `khong pending ⇒ roi ve lich`() {
        val d = PendingNavPolicy.resolve(pending = null, requireGps = false, gpsOk = null)
        assertEquals(PendingNavPolicy.Decision.FallThroughToSchedule, d)
    }

    @Test
    fun `co pending khong doi GPS ⇒ ban ngay (uu tien)`() {
        val d = PendingNavPolicy.resolve(pending(), requireGps = false, gpsOk = null)
        assertTrue(d is PendingNavPolicy.Decision.FireNav)
        assertEquals("Office", (d as PendingNavPolicy.Decision.FireNav).pending.place.name)
    }

    @Test
    fun `doi GPS ma chua co ⇒ cho (khong dan mu)`() {
        assertEquals(PendingNavPolicy.Decision.WaitForGps,
            PendingNavPolicy.resolve(pending(), requireGps = true, gpsOk = null))
        assertEquals(PendingNavPolicy.Decision.WaitForGps,
            PendingNavPolicy.resolve(pending(), requireGps = true, gpsOk = false))
    }

    @Test
    fun `doi GPS va co dinh vi ⇒ ban`() {
        val d = PendingNavPolicy.resolve(pending(), requireGps = true, gpsOk = true)
        assertTrue(d is PendingNavPolicy.Decision.FireNav)
    }

    @Test
    fun `overwrite lich — chuyen hien tai khong con lich`() {
        val b = ScheduledNavPolicy.blocked("n7.t123")
        assertEquals(NavTripPlan("n7.t123", "", true), b)
        val r1 = ScheduledNavRules.of("r1", true, 420, 540, setOf(1), false, "office", "gmaps")!!
        val step = ScheduledNavPolicy.step(b, "n7.t123", listOf(r1), 430, 1, 0L, true)
        assertEquals(NavHoldReason.NO_RULE, (step as NavTripStep.Hold).reason, "điểm đến chủ động đã dẫn ⇒ lịch nhường cả chuyến")
    }
}
