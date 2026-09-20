package com.byd.clusternav.launcher.automation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * ═══ AUTOMATION #2 · DẪN ĐƯỜNG THEO LỊCH · LUẬT QUYẾT ĐỊNH ════════════════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-automation.html` R2.3 · R2.4. Thuần (giờ/ngày/GPS bơm vào) ⇒ chạy off-car.
 *
 * ## Bài này khoá lại BỐN bài học
 *  1. **Usecase HẦM (spec R2.4)** — vẫn trong khung 7–9h, chưa có GPS ⇒ **chờ**, ra khỏi hầm có GPS thì mới dẫn.
 *     Đây là lý do `lastFiredDay` tính theo NGÀY chứ không phải "đã thử trong khung này".
 *  2. **Đúng MỘT lần mỗi ngày** — dẫn rồi thì các nhịp sau trong cùng khung phải im (nhịp 60s ⇒ nếu không, app dẫn
 *     đường bị mở lại mỗi phút suốt 2 tiếng).
 *  3. **Sang ngày mới thì được dẫn lại** — và phải tự hết hiệu lực, không cần ai xoá cờ lúc nửa đêm (xe tắt máy).
 *  4. **Một nhịp chỉ một luật** — hai khung chồng nhau không được bắn hai ý-định dẫn đường cùng lúc.
 */
class ScheduledNavPolicyTest {

    private val today = "2026-09-21"
    private val yesterday = "2026-09-20"
    private val never = ""

    private fun rule(
        id: String = "r1",
        enabled: Boolean = true,
        startMin: Int = 7 * 60,
        endMin: Int = 9 * 60,
        days: Set<Int> = ScheduledNavRules.WEEKDAYS,
        requireGps: Boolean = true,
        placeId: String = "công ty",
    ) = ScheduledNavRules.of(id, enabled, startMin, endMin, days, requireGps, placeId, "gmaps")
        ?: error("luật mẫu phải hợp lệ")

    private fun decide(
        rule: ScheduledNavRule = rule(),
        nowMinOfDay: Int = 8 * 60,
        dow: Int = ScheduledNavRules.MON,
        gpsOk: Boolean = true,
        lastFiredDay: String = never,
        today: String = this.today,
    ) = ScheduledNavPolicy.decide(rule, nowMinOfDay, dow, gpsOk, lastFiredDay, today)

    private fun skip(reason: NavSkipReason) = NavAutomationDecision.Skip(reason)

    // ══ (1) Ca dẫn được ══════════════════════════════════════════════════════════════════════════════════

    @Test
    fun `dung ngay dung gio co gps chua dan thi DAN`() {
        assertEquals(NavAutomationDecision.Launch, decide())
    }

    @Test
    fun `hai dau khung gio deu dan duoc`() {
        assertEquals(NavAutomationDecision.Launch, decide(nowMinOfDay = 7 * 60))
        assertEquals(NavAutomationDecision.Launch, decide(nowMinOfDay = 9 * 60))
    }

    /** Không tick "chỉ khi có GPS" ⇒ dẫn đúng giờ kể cả khi chưa có định vị (spec R2.2). */
    @Test
    fun `khong doi gps thi thieu gps van dan`() {
        assertEquals(NavAutomationDecision.Launch, decide(rule = rule(requireGps = false), gpsOk = false))
    }

    @Test
    fun `dan lai duoc khi lan gan nhat la hom qua`() {
        assertEquals(NavAutomationDecision.Launch, decide(lastFiredDay = yesterday))
    }

    // ══ (2) Từng lý do không dẫn ═════════════════════════════════════════════════════════════════════════

    @Test
    fun `luat tat thi khong dan`() {
        assertEquals(skip(NavSkipReason.DISABLED), decide(rule = rule(enabled = false)))
    }

    @Test
    fun `sai ngay thi khong dan`() {
        assertEquals(skip(NavSkipReason.WRONG_DAY), decide(dow = ScheduledNavRules.SUN))
        assertEquals(skip(NavSkipReason.WRONG_DAY), decide(dow = ScheduledNavRules.SAT))
        assertEquals(
            NavAutomationDecision.Launch,
            decide(rule = rule(days = setOf(ScheduledNavRules.SUN)), dow = ScheduledNavRules.SUN),
        )
    }

    @Test
    fun `ngoai khung gio thi khong dan`() {
        assertEquals(skip(NavSkipReason.OUTSIDE_WINDOW), decide(nowMinOfDay = 7 * 60 - 1), )
        assertEquals(skip(NavSkipReason.OUTSIDE_WINDOW), decide(nowMinOfDay = 9 * 60 + 1))
    }

    @Test
    fun `da dan hom nay thi khong dan nua`() {
        assertEquals(skip(NavSkipReason.ALREADY_FIRED), decide(lastFiredDay = today))
    }

    @Test
    fun `doi gps ma chua co gps thi CHO`() {
        assertEquals(skip(NavSkipReason.NO_GPS), decide(gpsOk = false))
    }

    /** Không dựng được khoá ngày ⇒ thà không dẫn (nếu cho qua, app dẫn đường mở lại mỗi nhịp). */
    @Test
    fun `khong co khoa ngay thi khong dan`() {
        assertEquals(skip(NavSkipReason.NO_DAY_KEY), decide(today = ""))
        assertEquals(skip(NavSkipReason.NO_DAY_KEY), decide(today = "   "))
    }

    /**
     * Thứ tự kiểm là hợp đồng: khi nhiều điều kiện cùng sai, câu nhật ký phải là cái ĐÚNG VIỆC đang xảy ra.
     * Đáng chú ý nhất là `ALREADY_FIRED` **trước** `NO_GPS` — đã dẫn rồi thì GPS không còn là câu hỏi.
     */
    @Test
    fun `thu tu ly do khi nhieu dieu kien cung sai`() {
        assertEquals(
            skip(NavSkipReason.DISABLED),
            decide(rule = rule(enabled = false), dow = ScheduledNavRules.SUN, nowMinOfDay = 0, gpsOk = false),
        )
        assertEquals(
            skip(NavSkipReason.WRONG_DAY),
            decide(dow = ScheduledNavRules.SUN, nowMinOfDay = 0, gpsOk = false),
        )
        assertEquals(skip(NavSkipReason.OUTSIDE_WINDOW), decide(nowMinOfDay = 0, gpsOk = false))
        assertEquals(
            skip(NavSkipReason.ALREADY_FIRED),
            decide(gpsOk = false, lastFiredDay = today),
            "đã dẫn rồi thì báo 'chờ GPS' là nói sai việc đang xảy ra",
        )
    }

    // ══ (3) Chuỗi nhịp thật ══════════════════════════════════════════════════════════════════════════════

    /**
     * ⚠ USECASE HẦM (spec R2.4): 7:05 trong hầm chưa có GPS ⇒ chờ; 7:20 ra khỏi hầm có GPS ⇒ dẫn; các nhịp sau im.
     */
    @Test
    fun `ham roi ra - cho tới khi co gps trong cung khung gio`() {
        val r = rule()
        var fired = never

        // Trong hầm, vẫn trong khung.
        assertEquals(skip(NavSkipReason.NO_GPS), decide(r, nowMinOfDay = 7 * 60 + 5, gpsOk = false, lastFiredDay = fired))
        assertEquals(skip(NavSkipReason.NO_GPS), decide(r, nowMinOfDay = 7 * 60 + 10, gpsOk = false, lastFiredDay = fired))

        // Ra khỏi hầm — có GPS.
        assertEquals(
            NavAutomationDecision.Launch,
            decide(r, nowMinOfDay = 7 * 60 + 20, gpsOk = true, lastFiredDay = fired),
        )
        fired = today // chỗ gọi đóng dấu sau khi dẫn

        // Các nhịp còn lại của khung: im.
        assertEquals(
            skip(NavSkipReason.ALREADY_FIRED),
            decide(r, nowMinOfDay = 7 * 60 + 21, gpsOk = true, lastFiredDay = fired),
        )
        assertEquals(
            skip(NavSkipReason.ALREADY_FIRED),
            decide(r, nowMinOfDay = 8 * 60 + 59, gpsOk = true, lastFiredDay = fired),
        )
    }

    /**
     * Nhịp 60s suốt khung 7–9h = 121 nhịp. Đúng **một** lần dẫn — nếu sai, app dẫn đường mở lại mỗi phút suốt hai
     * tiếng, ngay trước mặt người đang lái.
     */
    @Test
    fun `suot ca khung gio chi dan dung mot lan`() {
        val r = rule()
        var fired = never
        var launches = 0
        for (minute in (7 * 60)..(9 * 60)) {
            if (decide(r, nowMinOfDay = minute, lastFiredDay = fired) == NavAutomationDecision.Launch) {
                launches++
                fired = today
            }
        }
        assertEquals(1, launches)
    }

    /** Sang ngày mới thì hết hiệu lực **tự nó** — không cần ai xoá cờ lúc nửa đêm (lúc đó xe đã tắt máy). */
    @Test
    fun `sang ngay moi duoc dan lai ma khong can ai xoa co`() {
        val r = rule()
        assertEquals(skip(NavSkipReason.ALREADY_FIRED), decide(r, lastFiredDay = today, today = today))
        assertEquals(NavAutomationDecision.Launch, decide(r, lastFiredDay = today, today = "2026-09-22"))
    }

    // ══ (4) Một nhịp chỉ một luật ════════════════════════════════════════════════════════════════════════

    @Test
    fun `hai khung chong nhau chi dan luat dau tien trong so`() {
        val a = rule(id = "r1", startMin = 7 * 60, endMin = 9 * 60, placeId = "công ty")
        val b = rule(id = "r2", startMin = 8 * 60, endMin = 8 * 60 + 30, placeId = "trường con")
        val pick = ScheduledNavPolicy.firstToLaunch(
            rules = listOf(a, b),
            nowMinOfDay = 8 * 60 + 10,
            dow = ScheduledNavRules.MON,
            gpsOk = true,
            firedDays = emptyMap(),
            today = today,
        )
        assertEquals("r1", pick?.id, "thứ tự trong sổ = thứ tự người dùng tạo, là thứ duy nhất họ nhìn thấy")
    }

    /** Luật kia vẫn nổ ở nhịp sau (nó còn trong khung, sổ đã-dẫn của nó vẫn trống) — không bị mất lượt. */
    @Test
    fun `luat thu hai no o nhip sau, khong bi mat luot`() {
        val a = rule(id = "r1", startMin = 7 * 60, endMin = 9 * 60)
        val b = rule(id = "r2", startMin = 8 * 60, endMin = 8 * 60 + 30, placeId = "trường con")
        val pick = ScheduledNavPolicy.firstToLaunch(
            rules = listOf(a, b),
            nowMinOfDay = 8 * 60 + 11,
            dow = ScheduledNavRules.MON,
            gpsOk = true,
            firedDays = mapOf("r1" to today),
            today = today,
        )
        assertEquals("r2", pick?.id)
    }

    @Test
    fun `khong luat nao du dieu kien thi khong chon gi`() {
        assertNull(
            ScheduledNavPolicy.firstToLaunch(
                rules = listOf(rule(id = "r1"), rule(id = "r2")),
                nowMinOfDay = 22 * 60,
                dow = ScheduledNavRules.MON,
                gpsOk = true,
                firedDays = emptyMap(),
                today = today,
            ),
        )
        assertNull(
            ScheduledNavPolicy.firstToLaunch(
                rules = emptyList(),
                nowMinOfDay = 8 * 60,
                dow = ScheduledNavRules.MON,
                gpsOk = true,
                firedDays = emptyMap(),
                today = today,
            ),
        )
    }

    /** Sổ đã-dẫn thiếu khoá = chưa bao giờ dẫn (không được đọc thành "đã dẫn" rồi im mãi). */
    @Test
    fun `so da dan thieu khoa nghia la chua bao gio dan`() {
        val pick = ScheduledNavPolicy.firstToLaunch(
            rules = listOf(rule(id = "r7")),
            nowMinOfDay = 8 * 60,
            dow = ScheduledNavRules.MON,
            gpsOk = true,
            firedDays = mapOf("r1" to today),
            today = today,
        )
        assertEquals("r7", pick?.id)
    }
}
