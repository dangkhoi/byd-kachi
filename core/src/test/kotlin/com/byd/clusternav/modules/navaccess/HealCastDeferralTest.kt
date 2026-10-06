package com.byd.clusternav.modules.navaccess

import com.byd.clusternav.modules.clustercast.simplified.BoundedCastExecutor
import com.byd.clusternav.modules.clustercast.simplified.ThemeLedger
import com.byd.clusternav.modules.navaccess.AccessibilityHealGates.HealPhase
import com.byd.clusternav.modules.navaccess.HealCastDeferral.Outcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ 2.93 · READY-RESTART-MID-CAST — luật HOÃN lệnh tự force-stop của lượt chữa phím khi chiếu cụm đang dở ═══════════════
 *
 * Khoá lỗi [ĐO log xe 06/10, xe 2.91]: 15:17:27.751 `theme 31 → SEND` · 15:17:29.855 `16` · 15:17:30.725
 * `keys=STUCK(tat-may)->ESCALATE` · 15:17:31.392 `->RESTARTING` ⇒ tiến trình mới 15:17:34.286 `TOO_SOON (còn 8468 ms)`.
 * Đồng hồ giả: mỗi lần ngủ đẩy đồng hồ đúng chừng đó; mối nguy là hàm của đồng hồ (dựng lại từ mốc log).
 */
class HealCastDeferralTest {

    /** Đồng hồ giả + bộ đếm lượt ngủ / lượt hỏi pha. */
    private class Clock(var now: Long) {
        var sleeps = 0
        var phaseAsks = 0
        val sleep: (Long) -> Unit = { ms -> sleeps++; now += ms }
    }

    @Test
    fun `chieu yen - di tiep NGAY, khong ngu, khong hoi pha (duong cu y nguyen)`() {
        val c = Clock(30_725L)
        val r = HealCastDeferral.await(
            hazard = { null }, inPhase = { c.phaseAsks++; true }, nowMs = { c.now }, sleepMs = c.sleep,
            until = HealCastDeferral.waitUntil(HealPhase.TAT_MAY, -1L, c.now),
        )
        assertEquals(Outcome.CLEAR, r.outcome)
        assertTrue(r.outcome.fire)
        assertNull(r.hazard)
        assertEquals(0L, r.waitedMs)
        assertEquals(0, c.sleeps, "không chờ nhịp nào")
        assertEquals(0, c.phaseAsks, "không hỏi pha — cổng cuối của nấc leo vẫn hỏi như cũ")
    }

    @Test
    fun `log xe 15-17 - TAT_MAY, mo chieu dang bay roi khoang 15 s cua theme - cho toi khi het roi moi leo`() {
        val themeSentAt = 27_751L                                   // `theme 31 cụm=[2] → SEND`
        val openDoneAt = 39_000L                                    // [SUY] lượt mở xong (35 + ClusterBlack) — ước lượng
        val gapEnd = themeSentAt + ThemeLedger.MIN_GAP_MS           // 42 751
        val c = Clock(30_725L)                                      // `keys=STUCK(tat-may)->ESCALATE`
        val hazard = {
            when {
                c.now < openDoneAt -> "OP_IN_FLIGHT"
                c.now < gapEnd -> "THEME_GAP"
                else -> null
            }
        }
        val r = HealCastDeferral.await(
            hazard = hazard, inPhase = { true /* màn vẫn tắt */ }, nowMs = { c.now }, sleepMs = c.sleep,
            until = HealCastDeferral.waitUntil(HealPhase.TAT_MAY, -1L, c.now),
        )
        assertEquals(Outcome.SETTLED, r.outcome)
        assertEquals("OP_IN_FLIGHT", r.hazard, "nhãn = mối nguy THẤY ĐẦU TIÊN")
        val firedAt = 30_725L + r.waitedMs
        assertTrue(firedAt >= gapEnd, "leo SAU khi khoảng 15 s hết ⇒ tiến trình mới không còn TOO_SOON (bắn lúc $firedAt)")
        assertTrue(firedAt < gapEnd + HealCastDeferral.POLL_MS + 1, "không chờ thừa quá một nhịp ($firedAt)")
        assertTrue(r.waitedMs < HealCastDeferral.MAX_DEFER_MS, "trong trần")
    }

    @Test
    fun `tran tuyet doi - chieu ket mai - VAN leo sau MAX_DEFER_MS (khong bo doi luot chua phim)`() {
        val c = Clock(1_000L)
        val r = HealCastDeferral.await(
            hazard = { "OP_IN_FLIGHT" }, inPhase = { true }, nowMs = { c.now }, sleepMs = c.sleep,
            until = HealCastDeferral.waitUntil(HealPhase.TAT_MAY, -1L, c.now),
        )
        assertEquals(Outcome.LIMIT, r.outcome)
        assertTrue(r.outcome.fire, "hết trần ⇒ leo như bản cũ")
        assertEquals(HealCastDeferral.MAX_DEFER_MS, r.waitedMs)
    }

    @Test
    fun `man bat giua luc cho (TAT_MAY) - CAT, khong leo (lop 2 lo theo luat cua no)`() {
        val c = Clock(0L)
        val screenOnAt = 3_000L
        val r = HealCastDeferral.await(
            hazard = { "OP_IN_FLIGHT" }, inPhase = { c.now < screenOnAt }, nowMs = { c.now }, sleepMs = c.sleep,
            until = HealCastDeferral.waitUntil(HealPhase.TAT_MAY, -1L, c.now),
        )
        assertEquals(Outcome.PHASE_GONE, r.outcome)
        assertFalse(r.outcome.fire)
        assertEquals(screenOnAt, r.waitedMs)
    }

    @Test
    fun `MO_XE - toi day o +8 s cua an han 10 s - KHONG cho chut nao (hanh vi cu)`() {
        val screenOnAt = 100_000L
        val now = screenOnAt + 8_000L
        val until = HealCastDeferral.waitUntil(HealPhase.MO_XE, screenOnAt, now)
        assertEquals(now, until, "mép − dự trữ nấc leo đã qua ⇒ trần = bây giờ")
        val c = Clock(now)
        val r = HealCastDeferral.await(
            hazard = { "OP_IN_FLIGHT" }, inPhase = { true }, nowMs = { c.now }, sleepMs = c.sleep, until = until,
        )
        assertEquals(Outcome.LIMIT, r.outcome)
        assertEquals(0L, r.waitedMs)
        assertEquals(0, c.sleeps)
    }

    /**
     * Senior review CAST Pass 1 F1 [P2] — KHOI_DONG KHÔNG chờ: chờ ở đây đụng lượt giữ 8 s của chuỗi sẵn sàng ⇒ app ô mở hai lần
     * (2.83 R-A2), mà lượt chờ thường chạm trần nên tiến trình kế vẫn `TOO_SOON`. Thử ĐỎ: bỏ điều kiện `phase != TAT_MAY`.
     */
    @Test
    fun `KHOI_DONG - KHONG cho (dung luot giu 8 s cua chuoi san sang), leo ngay nhu ban cu`() {
        val startedAt = 50_000L
        val now = startedAt + 9_000L                                // đo-chờ-đo xong ở +9 s
        val until = HealCastDeferral.waitUntil(HealPhase.KHOI_DONG, startedAt, now)
        assertEquals(now, until, "chỉ lượt tắt-máy được chờ")
        val c = Clock(now)
        val r = HealCastDeferral.await(
            hazard = { "OP_IN_FLIGHT" }, inPhase = { true }, nowMs = { c.now }, sleepMs = c.sleep, until = until,
        )
        assertEquals(Outcome.LIMIT, r.outcome)
        assertEquals(0L, r.waitedMs)
        assertEquals(0, c.sleeps)
    }

    @Test
    fun `mep cua so lay tu CUNG nguon so voi cong cuoi`() {
        val a = 7_000L
        assertEquals(a + AccessibilityHealGates.MO_XE_GRACE_MS, AccessibilityHealGates.fireWindowEnd(HealPhase.MO_XE, a))
        assertEquals(a + AccessibilityHealGates.BOOT_GRACE_MS, AccessibilityHealGates.fireWindowEnd(HealPhase.KHOI_DONG, a))
        assertNull(AccessibilityHealGates.fireWindowEnd(HealPhase.TAT_MAY, a), "lớp 1: cổng chỉ là 'màn còn tắt'")
        assertNull(AccessibilityHealGates.fireWindowEnd(HealPhase.RUNNING, a))
        listOf(HealPhase.MO_XE, HealPhase.KHOI_DONG).forEach { p ->
            val end = AccessibilityHealGates.fireWindowEnd(p, a)!!
            assertTrue(AccessibilityHealGates.lifecycleFireAllowed(p, true, a, end), "$p: mép là mốc CUỐI còn cho bắn")
            assertFalse(AccessibilityHealGates.lifecycleFireAllowed(p, true, a, end + 1), "$p: quá mép một ms là không")
        }
    }

    @Test
    fun `RUNNING khong bao gio cho, tran khong bao gio truoc bay gio`() {
        assertEquals(5_000L, HealCastDeferral.waitUntil(HealPhase.RUNNING, 0L, 5_000L))
        assertEquals(90_000L, HealCastDeferral.waitUntil(HealPhase.MO_XE, 0L, 90_000L), "quá ân hạn từ lâu ⇒ trần = bây giờ")
        assertEquals(1_000L + HealCastDeferral.MAX_DEFER_MS, HealCastDeferral.waitUntil(HealPhase.TAT_MAY, -1L, 1_000L))
    }

    @Test
    fun `luong bi ngat - CAT, giu co ngat`() {
        val c = Clock(0L)
        try {
            val r = HealCastDeferral.await(
                hazard = { "OP_IN_FLIGHT" }, inPhase = { true }, nowMs = { c.now },
                sleepMs = { throw InterruptedException("tiến trình đóng") },
                until = HealCastDeferral.waitUntil(HealPhase.TAT_MAY, -1L, c.now),
            )
            assertEquals(Outcome.INTERRUPTED, r.outcome)
            assertFalse(r.outcome.fire)
            assertTrue(Thread.currentThread().isInterrupted, "cờ ngắt phải được đặt lại")
        } finally {
            Thread.interrupted()   // dọn cờ cho bài sau
        }
    }

    @Test
    fun `tran phu tron mot luot mo chieu va khoang 15 s cua theme`() {
        assertEquals(BoundedCastExecutor.OPEN_TIMEOUT_MS, HealCastDeferral.MAX_DEFER_MS, "một nguồn số với hạn lượt mở")
        assertTrue(HealCastDeferral.MAX_DEFER_MS >= ThemeLedger.MIN_GAP_MS)
    }
}
