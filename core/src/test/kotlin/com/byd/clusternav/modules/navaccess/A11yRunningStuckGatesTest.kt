package com.byd.clusternav.modules.navaccess

import com.byd.clusternav.modules.navaccess.A11yBindJournal.State
import com.byd.clusternav.modules.navaccess.AccessibilityHealGates.BindObservation
import com.byd.clusternav.modules.navaccess.AccessibilityHealGates.WatchdogStep
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Khoá lớp 3 của 2.83 — KẸT LÚC ĐANG CHẠY, watchdog 30 s không được lặp vô ích.
 *
 * Bài học [ĐO máy ảo 29/09, E2E 2.83 ca 4, `a11ylog` 13:33:42→13:34:42]: kẹt lúc xe đang chạy (lớp 3 — 2.83 không tự
 * force-stop, chờ người dùng bấm) ⇒ MỖI 30 s watchdog ghi NOT_BOUND, lượt grant (có dump) ghi STUCK, và TẮT/BẬT
 * `enabled_accessibility_services` — toggle không bao giờ chữa được ca kẹt ([ĐO AOSP android-10.0.0_r47
 * `AccessibilityManagerService.java:1630-1631`: `mBindingServices.contains` ⇒ `continue` trước cả bind lẫn unbind),
 * đổi cài đặt hệ thống 30 s/lần, và nhật ký (trần 200 dòng) đầy sau ~50 phút, đẩy mất dòng `tat-may`/`mo-xe`.
 *
 * Mô phỏng dưới đây chạy ĐÚNG các hàm thuần mà watchdog + đường grant dùng ([AccessibilityHealGates.watchdogStep],
 * [AccessibilityHealGates.watchdogState], [AccessibilityHealGates.stuckMarkOf], [A11yBindJournal.shouldAppend]); phần
 * nối dây (các hàm này thật sự được gọi) khoá ở `A11yRunningStuckWiringContractTest` (:app).
 */
class A11yRunningStuckGatesTest {

    private val comp = "com.byd.launcher/com.byd.clusternav.modules.navaccess.NavAccessibilityService"
    private val recheck = AccessibilityHealGates.STUCK_RECHECK_MS

    /** = `A11yBindJournalStore.HEARTBEAT_MS` (:app, private) — nhịp tim 1 giờ, owner giữ. */
    private val heartbeat = 3_600_000L

    /** Nhịp watchdog in-process (`VoiceKeyKeepAliveService.WATCHDOG_MS`). */
    private val tick = 30_000L

    private fun fixture(name: String): String {
        val text = javaClass.getResourceAsStream("/diagnostics/a11y-0929/$name")?.bufferedReader()?.readText()
        assertNotNull(text, "thiếu fixture $name — bài test đang quét thứ không tồn tại")
        return text!!
    }

    // ─── Bảng ca của một nhịp ───────────────────────────────────────────────────────────────────────────

    @Test
    fun `bang ca watchdogStep`() {
        val t0 = 1_000_000L
        data class Case(val bound: Boolean, val seenAt: Long, val now: Long, val want: WatchdogStep, val why: String)
        listOf(
            Case(true, -1L, t0, WatchdogStep.NONE, "đã gắn ⇒ không làm gì"),
            Case(true, t0, t0 + 1, WatchdogStep.NONE, "đã gắn ⇒ không làm gì, kể cả mốc kẹt còn sót"),
            Case(false, -1L, t0, WatchdogStep.GRANT, "chưa gắn, KHÔNG biết là kẹt ⇒ đường grant cũ (toggle) giữ nguyên"),
            Case(false, t0, t0 + tick, WatchdogStep.NONE, "đã ĐO kẹt 30 s trước ⇒ KHÔNG re-grant/toggle"),
            Case(false, t0, t0 + recheck - 1, WatchdogStep.NONE, "chưa tới nhịp kiểm lại"),
            Case(false, t0, t0 + recheck, WatchdogStep.RECHECK, "tới nhịp ⇒ MỘT lần dump chỉ đọc, không toggle"),
            Case(false, t0, t0 + 5 * recheck, WatchdogStep.RECHECK, "lỡ nhiều nhịp (máy ngủ) ⇒ vẫn chỉ kiểm lại"),
            Case(false, t0 + 9_000_000L, t0, WatchdogStep.GRANT, "mốc của đời máy trước (lớn hơn bây giờ) ⇒ coi như không biết"),
        ).forEach { c ->
            assertEquals(c.want, AccessibilityHealGates.watchdogStep(c.bound, c.seenAt, c.now), c.why)
        }
    }

    @Test
    fun `bang ca watchdogState - nhip tim khi dang ket ghi STUCK, khong ghi NOT_BOUND gia`() {
        val t0 = 1_000_000L
        assertEquals(State.BOUND, AccessibilityHealGates.watchdogState(true, t0, t0 + 1))
        assertEquals(State.STUCK, AccessibilityHealGates.watchdogState(false, t0, t0 + tick))
        assertEquals(State.STUCK, AccessibilityHealGates.watchdogState(false, t0, t0 + recheck), "chờ kiểm lại vẫn là STUCK đã đo")
        assertEquals(State.NOT_BOUND, AccessibilityHealGates.watchdogState(false, -1L, t0))
    }

    @Test
    fun `nhip kiem lai cham hon han watchdog, du day de bat trong cung chuyen`() {
        assertEquals(10 * 60_000L, recheck)
        assertTrue(recheck >= 10 * tick, "thưa hơn watchdog ít nhất 10 lần — không thì chẳng khác vòng cũ")
        assertTrue(recheck < heartbeat, "dày hơn nhịp tim — hết kẹt được ghi trước dòng nhịp tim kế tiếp")
    }

    @Test
    fun `kiem lai tren dump that - ket giu moc, da gan hay doc hong thi nha moc`() {
        val stuck = AccessibilityHealGates.observe(fixture("k1-acc-stuck.txt"), 42_000L, comp)
        assertEquals(42_000L, AccessibilityHealGates.stuckMarkOf(stuck), "k1 [ĐO xe 29/09]: vẫn kẹt ⇒ mốc = lúc đo")
        val bound = AccessibilityHealGates.observe(fixture("fix2-acc-bound.txt"), 42_000L, comp)
        assertEquals(-1L, AccessibilityHealGates.stuckMarkOf(bound), "fix2: đã gắn ⇒ nhả")
        assertEquals(-1L, AccessibilityHealGates.stuckMarkOf(null), "phiên dadb hỏng ⇒ nhả ⇒ đường grant cũ, không im")
        assertEquals(-1L, AccessibilityHealGates.stuckMarkOf(AccessibilityHealGates.observe("", 42_000L, comp)),
            "dump rỗng ⇒ không khẳng định kẹt")
        assertEquals(-1L, AccessibilityHealGates.stuckMarkOf(BindObservation(42_000L, bound = false, inBinding = false)),
            "hết kẹt mà chưa gắn ⇒ nhả ⇒ nhịp sau toggle (ca toggle chữa được)")
    }

    // ─── Mô phỏng nhiều giờ ─────────────────────────────────────────────────────────────────────────────

    /** Kết quả dump của đường grant (sau toggle hỏng) và của lượt kiểm lại, theo thời điểm. */
    private class Sim(
        val boundAt: (Long) -> Boolean,
        val stuckAt: (Long) -> Boolean,
    ) {
        var grants = 0
        var rechecks = 0
        val lines = mutableListOf<Pair<Long, State>>()
        private var prev: State? = State.BOUND
        private var lastLineAt = -60_000L
        private var seenAt = -1L

        private fun record(s: State, at: Long, binderOnly: Boolean) {
            if (A11yBindJournal.shouldAppend(prev, s, at - lastLineAt, 3_600_000L, binderOnly)) {
                lines += at to s; prev = s; lastLineAt = at
            }
        }

        private fun dump(at: Long): State = when {
            boundAt(at) -> State.BOUND
            stuckAt(at) -> State.STUCK
            else -> State.NOT_BOUND
        }

        fun run(untilMs: Long, tickMs: Long) {
            var t = 5_000L
            while (t <= untilMs) {
                val bound = boundAt(t)
                if (bound) seenAt = -1L                                    // A11yLifecycleHeal.runningStuckSeenAt
                record(AccessibilityHealGates.watchdogState(bound, seenAt, t), t, binderOnly = true)
                when (AccessibilityHealGates.watchdogStep(bound, seenAt, t)) {
                    WatchdogStep.GRANT -> {                                // toggle (hỏng) rồi escalateIfStuck đọc dump
                        grants++
                        val at = t + 10_000L
                        val s = dump(at)
                        seenAt = if (s == State.STUCK) at else -1L        // A11yLifecycleHeal.noteStuckDump(stuck)
                        record(s, at, binderOnly = false)
                    }
                    WatchdogStep.RECHECK -> {                              // recheckRunningStuck — chỉ đọc
                        rechecks++
                        val at = t + 1_000L
                        val s = dump(at)
                        seenAt = AccessibilityHealGates.stuckMarkOf(
                            BindObservation(at, bound = s == State.BOUND, inBinding = s == State.STUCK),
                        )
                        record(s, at, binderOnly = false)
                    }
                    WatchdogStep.NONE -> Unit
                }
                t += tickMs
            }
        }
    }

    @Test
    fun `ket suot 5 gio - MOT lan toggle, kiem lai 10 phut, nhat ky KHONG co cap NOT_BOUND-STUCK`() {
        val hours = 5L
        val sim = Sim(boundAt = { false }, stuckAt = { true })
        sim.run(hours * 3_600_000L, tick)
        assertEquals(1, sim.grants, "chỉ MỘT lượt grant (toggle) — lượt đo ra kẹt; trước bản vá là ${hours * 120} lượt")
        // Mốc = lúc ĐO (sau nhịp 1 s) ⇒ nhịp kiểm lại thật là nhịp 30 s đầu tiên ≥ 10 phút sau đó (~10,5 phút).
        val minRechecks = (hours * 3_600_000L / (recheck + tick)).toInt() - 1
        assertTrue(sim.rechecks in minRechecks..(hours * 6).toInt(), "kiểm lại mỗi ~10 phút: ${sim.rechecks}")
        val states = sim.lines.map { it.second }
        assertEquals(listOf(State.NOT_BOUND, State.STUCK), states.take(2), "một bước đổi: chưa gắn → (dump) kẹt")
        assertTrue(states.drop(1).all { it == State.STUCK }, "sau dòng STUCK không có NOT_BOUND nào: $states")
        assertTrue(sim.lines.size <= 2 + hours.toInt(), "chỉ thêm nhịp tim ~1 dòng/giờ: ${sim.lines}")
        assertTrue(sim.lines.size < A11yBindJournal.MAX_LINES / 20, "5 giờ kẹt không được ăn quá 5% trần nhật ký")
    }

    @Test
    fun `het ket - binder thay NGAY, ghi MOT dong BOUND, lan dut sau la su kien moi`() {
        val recoverAt = 40 * 60_000L
        val breakAgainAt = 70 * 60_000L
        val sim = Sim(
            boundAt = { it in recoverAt until breakAgainAt },
            stuckAt = { true },
        )
        sim.run(90 * 60_000L, tick)
        val bounds = sim.lines.filter { it.second == State.BOUND }
        assertEquals(1, bounds.size, "hết kẹt ⇒ đúng MỘT dòng chuyển trạng thái: ${sim.lines}")
        assertTrue(bounds.single().first in recoverAt until recoverAt + tick, "binder thấy ở nhịp 30 s kế tiếp, không chờ 10 phút")
        assertEquals(2, sim.grants, "kẹt lần hai (sau khi đã gắn) là sự kiện mới ⇒ đi lại đường grant cũ MỘT lần")
    }

    @Test
    fun `het ket ma chua gan - kiem lai thay, nhip sau quay ve toggle nhu cu`() {
        val unstuckAt = 25 * 60_000L
        val sim = Sim(boundAt = { false }, stuckAt = { it < unstuckAt })
        sim.run(40 * 60_000L, tick)
        val nb = sim.lines.last()
        assertEquals(State.NOT_BOUND, nb.second, "lượt kiểm lại (dump) thấy hết kẹt ⇒ ghi bước đổi thật")
        assertTrue(nb.first in unstuckAt..unstuckAt + recheck + tick, "bắt được trong một nhịp kiểm lại: ${sim.lines}")
        val ticksAfter = ((40 * 60_000L - nb.first) / tick).toInt()
        assertTrue(sim.grants >= 1 + ticksAfter - 1, "ca 'bật mà chưa gắn, KHÔNG kẹt' ⇒ toggle mỗi nhịp NHƯ CŨ (${sim.grants})")
    }

    @Test
    fun `bat ma chua gan, KHONG ket - van toggle moi nhip nhu cu`() {
        val sim = Sim(boundAt = { false }, stuckAt = { false })
        sim.run(10 * 60_000L, tick)
        assertEquals(((10 * 60_000L - 5_000L) / tick + 1).toInt(), sim.grants, "đường cũ (fix 1.78) không được mất nhịp nào")
        assertEquals(0, sim.rechecks)
    }
}
