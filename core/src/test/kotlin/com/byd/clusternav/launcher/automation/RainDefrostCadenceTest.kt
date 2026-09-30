package com.byd.clusternav.launcher.automation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ kachi-automation V8.1 · NHỊP MƯA (R-V8.8 · OQ-V8.6) — chạy THẬT mọi mốc thời gian ═════════════════════════
 *
 * Khoá ba điều owner cần ở lô V8.1 và một bất biến cũ:
 *  • **nhịp đầu chạy ngay** khi vòng bắt đầu, kể cả uptime vài chục giây ([ĐO git] `79be642` làm nó chờ tới uptime 5′);
 *  • **đọc lỗi thì thử lại đúng 5 lần**, mỗi lần 60 s, rồi về 5′ (không thử lại vô hạn — trim không cảm biến);
 *  • **không bao giờ hai nhịp cách nhau < 60 s**, kể cả vòng hỏi dày mỗi giây và có yêu cầu đổi lựa chọn;
 *  • bất biến của `79be642`: nhịp 5′ theo THỜI GIAN TRÔI, không theo số lượt thức.
 */
class RainDefrostCadenceTest {

    private val s = 1_000L
    private val min = 60_000L

    /** Mô phỏng vòng hỏi mỗi [stepMs] từ 0 tới [untilMs]; trả mốc các nhịp đã chạy. */
    private fun drive(
        cadence: RainDefrostCadence,
        untilMs: Long,
        stepMs: Long,
        ready: (Long) -> Boolean = { true },
        request: (Long) -> Boolean = { false },
    ): List<Long> {
        val runs = mutableListOf<Long>()
        var now = 0L
        while (now <= untilMs) {
            if (cadence.shouldRun(now, request(now))) {
                runs += now
                cadence.ran(now, ready(now))
            }
            now += stepMs
        }
        return runs
    }

    // ══ Nhịp đầu ════════════════════════════════════════════════════════════════════════════════════════════

    /**
     * OQ-V8.6 — hồi quy của `79be642`: `nowMs - 0L >= 5′` sai khi uptime < 5′. Vòng dựng lúc uptime 30 s phải chạy
     * nhịp mưa ở lượt hỏi ĐẦU.
     */
    @Test
    fun `nhip dau chay ngay o luot dau cua vong, ke ca uptime 30 giay`() {
        val c = RainDefrostCadence()
        c.loopStarted()
        assertTrue(c.shouldRun(30 * s, request = false), "uptime 30 s: nhịp mưa đầu phải chạy NGAY")
        assertFalse(30 * s - 0L >= RainDefrostCadence.PERIOD_MS, "đối chứng: công thức cũ của 79be642 chặn đúng ca này")
    }

    /** Vòng dựng lại (sync sau khi dừng) giữa lúc nhịp kế còn xa ⇒ vẫn chạy ở lượt hỏi đầu của vòng MỚI. */
    @Test
    fun `vong moi chay nhip dau ngay du nhip ke cu con xa`() {
        val c = RainDefrostCadence()
        c.loopStarted()
        c.ran(0, halReady = true)                               // nhịp kế cũ: 300 s
        assertFalse(c.shouldRun(120 * s, request = false), "đối chứng: chưa tới mốc, vòng CŨ không chạy")
        c.loopStarted()
        assertTrue(c.shouldRun(120 * s, request = false), "vòng MỚI: nhịp đầu chạy ở lượt hỏi đầu")
    }

    /** Vòng mới dựng lại trong < 60 s sau nhịp trước ⇒ sàn vẫn giữ, nhịp đầu chạy ngay khi qua sàn. */
    @Test
    fun `vong moi trong 60 giay sau nhip truoc van giu san`() {
        val c = RainDefrostCadence()
        c.ran(100 * s, halReady = true)
        c.loopStarted()
        assertFalse(c.shouldRun(130 * s, request = false), "30 s sau nhịp trước: sàn chặn")
        assertTrue(c.shouldRun(160 * s, request = false), "qua sàn: nhịp đầu của vòng mới chạy")
    }

    // ══ Thử lại có trần ═════════════════════════════════════════════════════════════════════════════════════

    /** HAL chưa sẵn MÃI (trim không cảm biến): 1 nhịp + đúng 5 lần thử lại cách 60 s, rồi về 5′. */
    @Test
    fun `HAL null mai thi thu lai dung 5 lan cach 60 giay roi ve 5 phut`() {
        val c = RainDefrostCadence()
        c.loopStarted()
        val runs = drive(c, untilMs = 20 * min, stepMs = s, ready = { false })
        assertEquals(
            listOf(0L, 1, 2, 3, 4, 5, 10, 15, 20).map { it * min },
            runs,
            "0 · 60 · 120 · 180 · 240 · 300 s (5 lần thử lại) rồi mỗi 5′",
        )
        assertEquals(5, RainDefrostCadence.MAX_RETRIES)
    }

    /** Đọc được đủ ⇒ trần về 0: lần hỏng SAU đó lại được thử lại 60 s. */
    @Test
    fun `mot nhip doc duoc thi tran thu lai ve 0`() {
        val c = RainDefrostCadence()
        c.loopStarted()
        val bad = setOf(0L, 1 * min, 7 * min)                  // boot hỏng 2 nhịp · ổn · hỏng lại lúc 7′
        val runs = drive(c, untilMs = 13 * min, stepMs = s, ready = { it !in bad })
        assertEquals(listOf(0L, 1, 2, 7, 8, 13).map { it * min }, runs)
    }

    /** Vòng mới trả trần thử lại về 0 — mỗi lần nổ máy được thử lại lại từ đầu. */
    @Test
    fun `vong moi tra tran thu lai ve 0`() {
        val c = RainDefrostCadence(maxRetries = 1)
        c.loopStarted()
        c.ran(0, halReady = false)                              // dùng hết 1 lần
        c.ran(1 * min, halReady = false)                        // hết trần ⇒ +5′
        assertFalse(c.shouldRun(2 * min, request = false))
        c.loopStarted()
        assertTrue(c.shouldRun(2 * min, request = false))
        c.ran(2 * min, halReady = false)
        assertTrue(c.shouldRun(3 * min, request = false), "trần về 0 ⇒ lại được thử lại sau 60 s")
    }

    // ══ Sàn 60 s ════════════════════════════════════════════════════════════════════════════════════════════

    /**
     * Vòng hỏi dày mỗi 250 ms (nhịp camera từng có trước BG-13), yêu cầu đổi lựa chọn dồn dập, HAL chập chờn ⇒ KHÔNG
     * hai nhịp nào cách nhau < 60 s. Đây là thứ `79be642` cần: nhịp mưa gác theo thời gian, không theo lượt thức.
     */
    @Test
    fun `hoi day 250 ms, yeu cau don dap, khong bao gio hai nhip duoi 60 giay`() {
        val c = RainDefrostCadence()
        c.loopStarted()
        val runs = drive(
            c, untilMs = 40 * min, stepMs = 250,
            ready = { (it / 17_000) % 3 != 0L },
            request = { it % 7_000 == 0L },
        )
        assertTrue(runs.size > 20, "phải có nhịp chạy: ${runs.size}")
        runs.zipWithNext().forEach { (a, b) -> assertTrue(b - a >= min, "hai nhịp cách ${b - a} ms: $a → $b") }
    }

    /** Yêu cầu tới lúc chưa qua sàn ⇒ GIỮ (chỗ gọi đã đọc-và-xoá cờ của nó), chạy ngay lượt đầu qua sàn. */
    @Test
    fun `yeu cau chua qua san thi duoc giu toi luot dau qua san`() {
        val c = RainDefrostCadence()
        c.loopStarted()
        c.ran(0, halReady = true)                               // nhịp kế thường: 300 s
        assertFalse(c.shouldRun(10 * s, request = true), "10 s sau nhịp trước: sàn chặn")
        assertFalse(c.shouldRun(59 * s + 999, request = false))
        assertTrue(c.shouldRun(60 * s, request = false), "yêu cầu phải còn — nhịp chạy ở 60 s, không đợi tới 300 s")
        c.ran(60 * s, halReady = true)
        assertFalse(c.shouldRun(121 * s, request = false), "yêu cầu đã tiêu ở nhịp 60 s — không chạy thêm")
    }

    /** R-V8.5 — yêu cầu khi đã qua sàn ⇒ chạy ở chính lượt hỏi đó (lượt thức kế, ≤ 60 s). */
    @Test
    fun `yeu cau khi da qua san thi chay ngay luot do`() {
        val c = RainDefrostCadence()
        c.loopStarted()
        c.ran(0, halReady = true)
        assertFalse(c.shouldRun(120 * s, request = false))
        assertTrue(c.shouldRun(120 * s, request = true))
    }

    // ══ Nhịp thường 5′ theo thời gian trôi ══════════════════════════════════════════════════════════════════

    /** Vòng thật ngủ 60 s (+ vài chục ms việc) ⇒ nhịp mưa đúng mỗi 5 lượt thức — y hệt hành vi đếm nhịp cũ. */
    @Test
    fun `HAL on thi nhip 5 phut theo thoi gian troi`() {
        val c = RainDefrostCadence()
        c.loopStarted()
        val wake = 60_050L
        val runs = drive(c, untilMs = 30 * wake, stepMs = wake)
        assertEquals((0..30 step 5).map { it * wake }, runs)
        val dense = RainDefrostCadence().also { it.loopStarted() }
        assertEquals((0L..30L step 5).map { it * min }, drive(dense, untilMs = 30 * min, stepMs = s), "hỏi dày vẫn 5′")
    }

    // ══ Giờ nhịp kế (cho dòng tình trạng) ═══════════════════════════════════════════════════════════════════

    @Test
    fun `nextDueMs - chua tung co vong thi khong biet`() {
        assertNull(RainDefrostCadence().nextDueMs(10 * s))
    }

    @Test
    fun `nextDueMs - vong vua bat dau la ngay, sau nhip la moc ke, yeu cau ton san`() {
        val c = RainDefrostCadence()
        c.loopStarted()
        assertEquals(5 * s, c.nextDueMs(5 * s), "chưa chạy: nhịp kế là NGAY")
        c.ran(10 * s, halReady = true)
        assertEquals(310 * s, c.nextDueMs(20 * s), "ổn ⇒ +5′")
        c.shouldRun(30 * s, request = true)
        assertEquals(70 * s, c.nextDueMs(30 * s), "có yêu cầu ⇒ ngay khi qua sàn 60 s")
        c.ran(70 * s, halReady = false)
        assertEquals(130 * s, c.nextDueMs(80 * s), "đọc lỗi ⇒ +60 s")
    }

    /** Ngay sau cú chạm Cài đặt, cờ `due` còn ở applier (vòng chưa đọc) ⇒ giờ nhịp kế phải là ≤ 60 s, không phải +5′. */
    @Test
    fun `nextDueMs - tinh ca yeu cau vong chua doc toi`() {
        val c = RainDefrostCadence()
        c.loopStarted()
        c.ran(0, halReady = true)
        assertEquals(300 * s, c.nextDueMs(120 * s), "không yêu cầu: mốc 5′")
        assertEquals(120 * s, c.nextDueMs(120 * s, request = true), "có yêu cầu, đã qua sàn: lượt thức kế")
        assertEquals(60 * s, c.nextDueMs(30 * s, request = true), "có yêu cầu, chưa qua sàn: khi qua sàn")
        assertFalse(c.shouldRun(30 * s, request = false), "hỏi giờ KHÔNG tiêu/không đặt yêu cầu")
    }

    @Test
    fun `nextDueMs - moc qua han hon mot luot thuc thi khong hua`() {
        val c = RainDefrostCadence()
        c.loopStarted()
        c.ran(0, halReady = true)
        assertEquals(330 * s, c.nextDueMs(330 * s), "trễ ≤ 60 s: vòng chạy ở lượt thức này — kẹp về hiện tại, không hứa giờ đã qua")
        assertNull(c.nextDueMs(361 * s), "trễ > 60 s ⇒ vòng không chạy ⇒ không biết")
    }

    // ══ halReady · đồng hồ · cấu hình ═══════════════════════════════════════════════════════════════════════

    private fun outcome(glass: RainGlass, speed: Int?, read: Boolean?): RainGlassOutcome {
        val plan = RainDefrostGlasses.plan(glass, RainDefrostState(), speed, read)
        return RainGlassOutcome(plan, null, plan.step.state, committed = true)
    }

    @Test
    fun `halReady - chi doc loi moi tinh`() {
        assertTrue(RainDefrostCadence.halReady(emptyList()), "không chọn kính nào: không có gì để thử lại")
        assertTrue(RainDefrostCadence.halReady(listOf(outcome(RainGlass.FRONT, 3, false), outcome(RainGlass.REAR, 3, true))))
        assertFalse(RainDefrostCadence.halReady(listOf(outcome(RainGlass.FRONT, null, null))), "mưa không đọc được")
        assertFalse(RainDefrostCadence.halReady(listOf(outcome(RainGlass.FRONT, 65535, null))), "sentinel = không đọc được")
        assertFalse(
            RainDefrostCadence.halReady(listOf(outcome(RainGlass.FRONT, 3, false), outcome(RainGlass.REAR, 3, null))),
            "MỘT kính đọc lỗi là đủ để thử lại",
        )
    }

    @Test
    fun `toWallMs doi moc don dieu sang dong ho tuong`() {
        assertEquals(1_000_300_000L, RainDefrostCadence.toWallMs(elapsedAt = 400_000, nowElapsed = 100_000, nowWall = 1_000_000_000))
    }

    @Test
    fun `cau hinh sai thi no ngay`() {
        assertThrows(IllegalArgumentException::class.java) { RainDefrostCadence(periodMs = 1_000, retryMs = 60_000) }
        assertThrows(IllegalArgumentException::class.java) { RainDefrostCadence(retryMs = 0) }
        assertThrows(IllegalArgumentException::class.java) { RainDefrostCadence(maxRetries = -1) }
    }
}
