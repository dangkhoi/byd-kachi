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
    fun `overwrite lich — dong dau CHI luat trong khung gio chuyen nay`() {
        val r1 = ScheduledNavRules.of("r1", true, 420, 540, setOf(1), false, "office", "gmaps")!!   // 07:00–09:00 T2
        val r2 = ScheduledNavRules.of("r2", true, 1020, 1080, setOf(1), false, "home", "gmaps")!!   // 17:00–18:00 T2
        val existing = mapOf("r9" to "2026-10-25")
        // pending nổ 07:10 T2 ⇒ chỉ r1 (đang trong khung) bị chặn; r2 (17:00) vẫn chạy chiều nay
        val out = PendingNavPolicy.blockScheduleFired(existing, listOf(r1, r2), nowMinOfDay = 430, dow = 1, todayKey = "2026-10-26")
        assertEquals("2026-10-26", out["r1"])
        assertEquals(null, out["r2"], "luật khung giờ khác KHÔNG bị chặn (một lần, không cả ngày)")
        assertEquals("2026-10-25", out["r9"], "dấu ngày khác giữ nguyên")
    }

    @Test
    fun `todayKey rong ⇒ khong dong dau (khong chan vinh vien)`() {
        val r1 = ScheduledNavRules.of("r1", true, 420, 540, setOf(1), false, "office", "gmaps")!!
        assertEquals(mapOf("r1" to "x"), PendingNavPolicy.blockScheduleFired(mapOf("r1" to "x"), listOf(r1), 430, 1, ""))
    }
}
