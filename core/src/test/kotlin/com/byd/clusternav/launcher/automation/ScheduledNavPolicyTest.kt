package com.byd.clusternav.launcher.automation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Lịch dẫn đường QUYẾT MỘT LẦN MỖI CHUYẾN (owner 27/10, spec `kachi-nav-schedule-per-trip.html`). Ví dụ chung: luật
 * "về nhà" 16:00–18:00 mọi ngày trong tuần. Mỗi bài mang mã tình huống của bảng owner duyệt (A1…D3).
 */
class ScheduledNavPolicyTest {

    private val trip = "n42.t1000"
    private fun rule(
        id: String = "home", enabled: Boolean = true, start: Int = 16 * 60, end: Int = 18 * 60,
        days: Set<Int> = ScheduledNavRules.WEEKDAYS, gps: Boolean = true,
    ) = ScheduledNavRules.of(id, enabled, start, end, days, gps, "Nhà", "gmaps") ?: error("luật mẫu hợp lệ")

    private fun step(
        saved: NavTripPlan? = null, t: String = trip, rules: List<ScheduledNavRule> = listOf(rule()),
        now: Int = 16 * 60 + 20, dow: Int = ScheduledNavRules.MON, since: Long? = 30_000L, gps: Boolean = true,
    ) = ScheduledNavPolicy.step(saved, t, rules, now, dow, since, gps)

    private fun hold(s: NavTripStep) = (s as NavTripStep.Hold).reason

    @Test fun `A1 no may trong khung - gan lich va dan`() {
        val s = step()
        assertTrue(s is NavTripStep.Launch)
        assertEquals(NavTripPlan(trip, "home", false), s.plan)
    }

    @Test fun `A2 C1 no may ngoai khung - ca chuyen khong lich, dang chay toi 16h cung khong dan`() {
        val first = step(now = 15 * 60 + 40)
        assertEquals(NavHoldReason.NO_RULE, hold(first))
        // Đang chạy, đồng hồ vào khung — đúng lỗi owner gặp 27/10: không chen giữa chuyến.
        val later = step(saved = first.plan, now = 16 * 60 + 5)
        assertEquals(NavHoldReason.NO_RULE, hold(later))
        assertEquals(first.plan, later.plan)
    }

    @Test fun `A4 hai khung trung nhau - lay luat dau tien trong so`() {
        val s = step(rules = listOf(rule("a"), rule("b")))
        assertEquals("a", (s as NavTripStep.Launch).rule.id)
    }

    @Test fun `A5 luat tat hoac sai thu - khong lich`() {
        assertEquals(NavHoldReason.NO_RULE, hold(step(rules = listOf(rule(enabled = false)))))
        assertEquals(NavHoldReason.NO_RULE, hold(step(dow = ScheduledNavRules.SUN)))
    }

    @Test fun `B2 B5 doi GPS - cho, khong chon lai, ra ham co GPS thi dan du da qua khung`() {
        val wait = step(gps = false)
        assertEquals(NavHoldReason.NO_GPS, hold(wait))
        val after = step(saved = wait.plan, now = 18 * 60 + 30, gps = true)   // quá 18:00 vẫn là điểm đến của chuyến
        assertTrue(after is NavTripStep.Launch)
    }

    @Test fun `B1 khong doi GPS thi dan ngay`() {
        assertTrue(step(rules = listOf(rule(gps = false)), gps = false) is NavTripStep.Launch)
    }

    @Test fun `C2 C3 da dan - tien trinh bat lai giua duong khong ban lai`() {
        val done = NavTripPlan(trip, "home", fired = true)
        assertEquals(NavHoldReason.FIRED, hold(step(saved = done, since = 5_000L)))
    }

    @Test fun `D1 D3 E1 chuyen moi trong khung duoc dan lai cung ngay`() {
        val done = NavTripPlan(trip, "home", fired = true)
        val next = step(saved = done, t = "n42.t2000", now = 16 * 60 + 40)
        assertTrue(next is NavTripStep.Launch, "mỗi chuyến nổ máy trong khung đều dẫn (owner chọn E1)")
    }

    @Test fun `D2 chuyen moi ngoai khung khong dan`() {
        val done = NavTripPlan(trip, "home", fired = true)
        assertEquals(NavHoldReason.NO_RULE, hold(step(saved = done, t = "n42.t2000", now = 18 * 60 + 10)))
    }

    @Test fun `thay chuyen lan dau khi da di qua 5 phut - khong biet gio no may, khong dan`() {
        val s = step(since = ScheduledNavPolicy.START_GRACE_MS + 1)
        assertEquals(NavHoldReason.LATE, hold(s))
        assertEquals(NavTripPlan(trip, "", false), s.plan, "lưu 'không lịch' để nhịp sau không chọn lại")
        assertTrue(step(since = null) is NavTripStep.Launch, "không biết mốc thức ⇒ coi như vừa nổ máy")
    }

    @Test fun `luat cua chuyen bi xoa hoac tat giua chuyen - bo`() {
        val plan = NavTripPlan(trip, "home", false)
        assertEquals(NavHoldReason.RULE_GONE, hold(step(saved = plan, rules = listOf(rule(enabled = false)))))
        assertEquals(NavTripPlan(trip, "", false), ScheduledNavPolicy.pruned(plan, setOf("other")))
        assertEquals(plan, ScheduledNavPolicy.pruned(plan, setOf("home")))
    }

    @Test fun `ma chuyen rong - khong dan`() {
        assertEquals(NavHoldReason.NO_TRIP, hold(step(t = " ")))
    }

    @Test fun `ma hoa ke hoach`() {
        val p = NavTripPlan("n3.b", "r2", true)
        assertEquals(p, ScheduledNavPolicy.decode(ScheduledNavPolicy.encode(p)))
        assertEquals(NavTripPlan("n3.b", "", false), ScheduledNavPolicy.decode("n3.b||0"))
        listOf(null, "", "x", "|r|1", "n|r|2", "a|b|c|d").forEach { assertNull(ScheduledNavPolicy.decode(it), "hỏng: $it") }
    }
}
