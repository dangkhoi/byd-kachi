package com.byd.clusternav.modules.navaccess

import com.byd.clusternav.modules.clustercast.simplified.ThemeLedger
import com.byd.clusternav.modules.navaccess.AccessibilityHealGates.HealPhase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ 2.93 wave 2A · HEAL-DEFER-GATE-RECHECK — cổng cuối của lượt tắt-máy HỎI LẠI chiếu cụm ngay trước lệnh tách rời ═══════════
 *
 * Khoá OQ5 spec `kachi-293-cast.html` (D1: lượt chờ đứng TRƯỚC nấc leo ⇒ một thao tác chiếu BẮT ĐẦU trong ~1–2,5 s đọc của nấc leo
 * vẫn bị `am force-stop` giết) — ca thật nhất [SUY đọc mã `BubbleAutostart` + `themeGapRetryMs`]: lượt tự chiếu bị `TOO_SOON` thử
 * lại ĐÚNG lúc khoảng 15 s hết, tức đúng lúc lượt chờ vừa xong. Nấc leo giả ở đây chạy y thứ tự `NavConnect.escalateIfStuck`:
 * đọc (đồng hồ trôi [READS_MS]) → hỏi cổng → bắn / không. Đồng hồ giả, không luồng thật.
 */
class HealCastGateRecheckTest {

    private class World(var now: Long) {
        val fired = ArrayList<Long>()
        var attempts = 0
        val sleep: (Long) -> Unit = { now += it }

        /** Nấc leo giả: phiên đọc tốn [READS_MS] rồi hỏi cổng; cổng mở ⇒ "RESTARTING", đóng ⇒ "NOT_BOUND". */
        fun fire(gate: () -> Boolean): String {
            attempts++
            now += READS_MS
            return if (gate()) { fired += now; "RESTARTING" } else "NOT_BOUND"
        }
    }

    private fun run(w: World, phase: HealPhase = HealPhase.TAT_MAY, inPhase: () -> Boolean = { true }, hazard: () -> String?) =
        HealCastDeferral.escalate(
            phase, HealCastDeferral.waitUntil(phase, -1L, w.now), hazard, inPhase, { w.now }, w.sleep,
            onWait = {}, onRecheck = { _, _ -> },
        ) { gate -> w.fire(gate) }

    @Test
    fun `thao tac chieu bat dau TRONG luc doc cua nac leo - khong ban, cho het roi leo lai voi ban doc moi`() {
        val start = 42_751L                                     // lượt chờ vừa SETTLED: khoảng 15 s của theme vừa hết
        val retryOpenAt = start + 1_000L                        // BubbleAutostart thử lại mở chiếu giữa lúc nấc leo đang đọc
        val retryDoneAt = retryOpenAt + 9_000L                  // lượt mở mới xong (không gửi theme) [SUY, cỡ log xe]
        val w = World(start)
        val r = run(w) { if (w.now in retryOpenAt until retryDoneAt) "OP_IN_FLIGHT" else null }
        assertEquals("RESTARTING", r)
        assertEquals(2, w.attempts, "lượt 1 bị cổng chặn (0 lệnh ghi), lượt 2 leo với phiên + bản đọc MỚI")
        assertEquals(1, w.fired.size)
        assertTrue(w.fired.single() >= retryDoneAt, "bắn SAU khi thao tác chiếu mới xong (bắn lúc ${w.fired.single()})")
    }

    @Test
    fun `chieu yen ca luc doc - mot lan leo, ban ngay (duong cu)`() {
        val w = World(10_000L)
        assertEquals("RESTARTING", run(w) { null })
        assertEquals(1, w.attempts)
        assertEquals(listOf(10_000L + READS_MS), w.fired)
    }

    @Test
    fun `nguy lien tuc - toi da MAX_GATE_RECHECKS lan chan roi VAN ban (khong bo doi luot chua phim)`() {
        val w = World(0L)
        // Mối nguy chỉ BẬT đúng lúc cổng hỏi (lượt chờ luôn thấy yên) — kịch bản xấu nhất cho số lượt leo.
        var asks = 0
        val r = run(w) { if (w.attempts > asks) { asks = w.attempts; "OP_IN_FLIGHT" } else null }
        assertEquals("RESTARTING", r)
        assertEquals(HealCastDeferral.MAX_GATE_RECHECKS + 1, w.attempts, "≤ 1 + ${HealCastDeferral.MAX_GATE_RECHECKS} phiên dadb")
        assertEquals(1, w.fired.size)
    }

    @Test
    fun `tran chung cua ca luot - qua until thi cong ban du con nguy`() {
        val w = World(0L)
        val r = run(w) { "THEME_GAP" }                           // kẹt mãi (sổ theme hỏng chẳng hạn)
        assertEquals("RESTARTING", r, "hết trần ⇒ bắn như bản cũ (D3)")
        assertEquals(1, w.attempts, "lượt chờ đã chạm trần ⇒ cổng không chặn thêm vòng nào")
        assertTrue(w.fired.single() >= HealCastDeferral.MAX_DEFER_MS)
    }

    @Test
    fun `man bat trong luc cho lai - CAT (null), khong ban`() {
        val w = World(0L)
        var screenOn = false
        val r = run(w, inPhase = { !screenOn }) {
            if (w.attempts == 1 && !screenOn) { "OP_IN_FLIGHT".also { if (w.now > READS_MS + 1_000L) screenOn = true } } else null
        }
        assertNull(r, "pha qua trong lúc chờ lại ⇒ CẮT như nhánh chờ đo lại (lớp 2 lo)")
        assertTrue(w.fired.isEmpty())
    }

    @Test
    fun `pha khac TAT_MAY - khong cho, cong = pha, khong hoi moi nguy (duong cu tung buoc)`() {
        listOf(HealPhase.MO_XE, HealPhase.KHOI_DONG).forEach { phase ->
            val w = World(5_000L)
            var asked = 0
            val r = run(w, phase = phase) { asked++; "OP_IN_FLIGHT" }
            assertEquals("RESTARTING", r, "$phase")
            assertEquals(0, asked, "$phase: không hỏi mối nguy ở cả lượt chờ lẫn cổng")
            assertEquals(1, w.attempts)
        }
    }

    @Test
    fun `nhip cho lai dung khoang theme cua log 15-17`() {
        // Lượt chờ xong lúc khoảng hết (42 751); nấc leo đọc 2 s; giữa lúc đọc lượt mở mới GỬI theme (khoảng 15 s mới).
        val w = World(42_751L)
        val newThemeAt = 43_500L
        val r = run(w) {
            when {
                w.now < newThemeAt -> null
                w.now < newThemeAt + ThemeLedger.MIN_GAP_MS -> "THEME_GAP"
                else -> null
            }
        }
        assertEquals("RESTARTING", r)
        assertTrue(w.fired.single() >= newThemeAt + ThemeLedger.MIN_GAP_MS, "tiến trình kế không TOO_SOON (bắn ${w.fired.single()})")
    }

    private companion object {
        /** Phiên dadb + 4 lệnh đọc của nấc leo (`dumpsys accessibility` · `dumpsys display` · `am stack list` · `settings get`). */
        const val READS_MS = 2_000L
    }
}
