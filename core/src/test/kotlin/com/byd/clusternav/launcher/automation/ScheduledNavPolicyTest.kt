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
    private val t0 = 1_000_000L   // elapsedRealtime lúc nổ máy
    private fun rule(
        id: String = "home", enabled: Boolean = true, start: Int = 16 * 60, end: Int = 18 * 60,
        days: Set<Int> = ScheduledNavRules.WEEKDAYS, gps: Boolean = true, place: String = "Nhà",
    ) = ScheduledNavRules.of(id, enabled, start, end, days, gps, place, "gmaps") ?: error("luật mẫu hợp lệ")

    private fun step(
        saved: NavTripPlan? = null, t: String = trip, rules: List<ScheduledNavRule> = listOf(rule()),
        now: Int = 16 * 60 + 20, dow: Int = ScheduledNavRules.MON, elapsed: Long = t0 + 30_000, wake: Long = t0, gps: Boolean = true,
    ) = ScheduledNavPolicy.step(saved, t, rules, now, dow, elapsed, wake, gps)

    private fun hold(s: NavTripStep) = (s as NavTripStep.Hold).reason
    private val home get() = rule()

    @Test fun `A1 no may trong khung - gan lich va dan`() {
        val s = step()
        assertTrue(s is NavTripStep.Launch)
        assertEquals(NavTripPlan(trip, "home", false, wakeAt = t0, ruleSig = ScheduledNavPolicy.sig(home)), s.plan)
    }

    @Test fun `A2 C1 no may ngoai khung - ca chuyen khong lich, dang chay toi 16h cung khong dan`() {
        val first = step(now = 15 * 60 + 40)
        assertEquals(NavHoldReason.NO_RULE, hold(first))
        // Đang chạy, đồng hồ vào khung — đúng lỗi owner gặp 27/10: không chen giữa chuyến.
        val later = step(saved = first.plan, now = 16 * 60 + 5, elapsed = t0 + 25 * 60_000)
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
        val after = step(saved = wait.plan, now = 18 * 60 + 30, elapsed = t0 + 2 * 3_600_000, gps = true)
        assertTrue(after is NavTripStep.Launch, "quá 18:00 vẫn là điểm đến của chuyến")
    }

    @Test fun `B1 khong doi GPS thi dan ngay`() {
        assertTrue(step(rules = listOf(rule(gps = false)), gps = false) is NavTripStep.Launch)
    }

    @Test fun `C2 C3 da dan - tien trinh bat lai giua duong khong ban lai`() {
        val done = NavTripPlan(trip, "home", fired = true, wakeAt = t0, ruleSig = ScheduledNavPolicy.sig(home))
        assertEquals(NavHoldReason.FIRED, hold(step(saved = done, elapsed = t0 + 3_600_000, wake = t0 + 3_590_000)))
    }

    @Test fun `D1 D3 E1 chuyen moi trong khung duoc dan lai cung ngay`() {
        val done = NavTripPlan(trip, "home", fired = true, wakeAt = t0)
        val next = step(saved = done, t = "n42.t2000", now = 16 * 60 + 40, elapsed = t0 + 3_600_000, wake = t0 + 3_590_000)
        assertTrue(next is NavTripStep.Launch, "mỗi chuyến nổ máy trong khung đều dẫn (owner chọn E1)")
    }

    @Test fun `D2 chuyen moi ngoai khung khong dan`() {
        val done = NavTripPlan(trip, "home", fired = true, wakeAt = t0)
        assertEquals(NavHoldReason.NO_RULE, hold(step(saved = done, t = "n42.t2000", now = 18 * 60 + 10, elapsed = t0 + 3_600_000, wake = t0 + 3_590_000)))
    }

    @Test fun `thay chuyen lan dau khi da di qua 5 phut - khong biet gio no may, khong dan`() {
        val s = step(elapsed = t0 + ScheduledNavPolicy.START_GRACE_MS + 1)
        assertEquals(NavHoldReason.LATE, hold(s))
        assertEquals(NavTripPlan(trip, "", false, wakeAt = t0), s.plan, "lưu 'không lịch' để nhịp sau không chọn lại")
        assertTrue(step(wake = -1L, elapsed = t0 + 3_600_000) is NavTripStep.Launch, "không mốc thức nào ⇒ coi như vừa nổ máy")
    }

    /** Review 27/10 P1: mốc thức của chuyến (ghi lúc nổ máy) THẮNG mốc thức của tiến trình vừa bật lại giữa đường. */
    @Test fun `tien trinh bat lai giua duong - mot thuc cua chuyen thang, khong chon lai theo gio hien tai`() {
        val noted = ScheduledNavPolicy.noteWake(null, trip, nowElapsed = t0)!!   // màn chính thức lúc 15:40, chưa nhịp lịch nào
        assertEquals(NavTripPlan(trip, "", false, decided = false, wakeAt = t0), noted)
        assertNull(ScheduledNavPolicy.noteWake(noted, trip, t0 + 60_000), "cùng chuyến ⇒ giữ mốc cũ")
        // 16:05: tiến trình MỚI (vừa bị giết) thức lại, nhịp lịch đầu tiên của chuyến tới. Nếu tin mốc tiến trình ⇒ chọn "về nhà".
        val restart = t0 + 25 * 60_000
        val s = step(saved = noted, now = 16 * 60 + 5, elapsed = restart + 10_000, wake = restart)
        assertEquals(NavHoldReason.LATE, hold(s))
        assertTrue(s.plan!!.decided && !s.plan!!.hasRule)
    }

    /** Mốc thức có, nhịp đầu tới trong 5 phút nhưng đồng hồ đã bước qua khung ⇒ vẫn chọn theo GIỜ NỔ MÁY. */
    @Test fun `chon theo gio no may, khong theo gio nhip`() {
        val noted = ScheduledNavPolicy.noteWake(null, trip, t0)!!            // nổ máy 15:58
        val s = step(saved = noted, now = 16 * 60 + 2, elapsed = t0 + 4 * 60_000, wake = t0)
        assertEquals(NavHoldReason.NO_RULE, hold(s), "15:58 ngoài khung ⇒ không lịch dù nhịp rơi vào 16:02")
        val s2 = step(saved = ScheduledNavPolicy.noteWake(null, trip, t0)!!, now = 18 * 60 + 2, elapsed = t0 + 4 * 60_000, wake = t0)
        assertTrue(s2 is NavTripStep.Launch, "nổ máy 17:58 trong khung ⇒ dẫn dù nhịp là 18:02")
    }

    /** Review 27/10 P2: `newId` cấp lại id ⇒ luật MỚI mang id cũ không được thi hành thay luật đã gắn. */
    @Test fun `id cap lai cho luat khac - khong thi hanh nham`() {
        val plan = (step() as NavTripStep.Launch).plan
        val other = rule(id = "home", place = "Công ty")
        assertEquals(NavHoldReason.RULE_GONE, hold(step(saved = plan, rules = listOf(other))))
        assertEquals(plan.copy(ruleId = "", ruleSig = ""), ScheduledNavPolicy.pruned(plan, listOf(other)))
        assertEquals(plan, ScheduledNavPolicy.pruned(plan, listOf(home)))
    }

    @Test fun `luat cua chuyen bi xoa hoac tat giua chuyen - bo`() {
        val plan = (step() as NavTripStep.Launch).plan
        assertEquals(NavHoldReason.RULE_GONE, hold(step(saved = plan, rules = listOf(rule(enabled = false)))))
        assertEquals(plan.copy(ruleId = "", ruleSig = ""), ScheduledNavPolicy.pruned(plan, emptyList()))
    }

    @Test fun `ma chuyen rong - khong dan`() {
        assertEquals(NavHoldReason.NO_TRIP, hold(step(t = " ")))
        assertNull(ScheduledNavPolicy.noteWake(null, "", t0))
    }

    @Test fun `ma hoa ke hoach - dang cu id=ngay ra null`() {
        val p = NavTripPlan("n3.b", "r2", true, decided = true, wakeAt = 77L, ruleSig = "a1b2")
        assertEquals(p, ScheduledNavPolicy.decode(ScheduledNavPolicy.encode(p)))
        assertEquals(NavTripPlan("n3.b", "", false, decided = false, wakeAt = 5), ScheduledNavPolicy.decode("n3.b||0|0|5|"))
        listOf(null, "", "r1=2026-10-26", "n3.b|r2|1", "|r|1|1|0|", "n|r|2|1|0|", "n|r|1|1|x|").forEach {
            assertNull(ScheduledNavPolicy.decode(it), "hỏng/cũ: $it")
        }
    }
}
