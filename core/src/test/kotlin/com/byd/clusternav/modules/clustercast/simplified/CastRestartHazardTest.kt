package com.byd.clusternav.modules.clustercast.simplified

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * ═══ 2.93 · READY-RESTART-MID-CAST — `restartHazard()`: khởi động lại tiến trình LÚC NÀY có làm hỏng chiếu cụm không ═══════
 *
 * Dựng lại chuỗi log xe 06/10 (xe 2.91, Seal mức B): tiến trình C mở chiếu ⇒ `theme 31 → SEND` 15:17:27.751 ⇒ lượt chữa phím tự
 * force-stop 15:17:31.392 ⇒ tiến trình D mở lại 15:17:34.286 ⇒ `TOO_SOON (còn 8468 ms)` ⇒ phiên "chưa rõ kiểu". Đồng hồ của sổ là
 * đồng hồ giả (ms `elapsedRealtime` lấy từ giờ log, bỏ phần giờ-phút); mỗi "tiến trình" là một coordinator riêng có mốc bật riêng,
 * chung MỘT sổ theme bền (phạm vi XE).
 */
class CastRestartHazardTest {

    private val sealB = ProjectionRecipe.SEAL_DL3.copy(nativeStyle = CastStyle.RECT, themeOnVacantVd = true)

    /** Đồng hồ giả của sổ — chung cho mọi "tiến trình" (elapsedRealtime của đầu xe), mốc bật tiến trình riêng. */
    private var now = 0L

    private fun clock(procStart: Long) = ThemeLedger.Clock { ThemeLedger.Now(now, boot = 7, processStartMs = procStart) }

    private fun coordinator(shell: SimpleCastShell, vd: Int, ledger: ThemeLedger.Store, procStart: Long) = SimpleCastCoordinator(
        ProjectionManager(shell, sleepMs = {}, recipe = sealB), DisplayConfigurator(shell), AppMover(shell, sleepMs = {}), FakePrefs(),
        shell, displayId = vd, detectSleepMs = {}, desiredStyle = { CastStyle.RECT }, themeLedger = ledger, themeClock = clock(procStart),
    )

    private fun ops(h: List<String>) =
        h.filter { it.contains(" 2 i32 1000 i32 ") }.map { it.substringAfter("i32 1000 i32 ").substringBefore(" ").toInt() }

    private fun awaitQuiet(c: SimpleCastCoordinator) {
        val deadline = System.currentTimeMillis() + 8000
        while (!(c.executor.isIdle && c.state == SimpleCastState.Idle) && System.currentTimeMillis() < deadline) Thread.sleep(10)
        assertTrue(c.executor.isIdle && c.state == SimpleCastState.Idle, "state=${c.state}")
    }

    /** Chặn ĐÚNG lệnh khớp [blockOn] tới khi [release] — giữ một lượt mở chiếu "đang bay". */
    private class BlockingShell(val inner: FakeShell, val blockOn: (String) -> Boolean) : SimpleCastShell {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        override fun execute(command: String): ShellResult {
            if (blockOn(command) && entered.count > 0) {
                entered.countDown()
                release.await(10, TimeUnit.SECONDS)
            }
            return inner.execute(command)
        }
    }

    @Test
    fun `chua mo chieu - khong co moi nguy`() {
        now = 5_000L
        val c = coordinator(FakeShell(), 1, ThemeLedger.InMemory(), procStart = 1_000L)
        try {
            assertNull(c.restartHazard())
        } finally {
            c.shutdown()
        }
    }

    @Test
    fun `luot mo dang bay giua 16 va 35 - OP_IN_FLIGHT, xong roi - THEME_GAP toi du 15 s, sau do null`() {
        now = 27_751L
        val fake = FakeShell().apply { clusterDisplayId = 2; vdAfterTheme = 3 }
        val gate = BlockingShell(fake) { it.contains(" i32 1000 i32 16 ") }   // [ĐO log] tiến trình chết ngay sau `16`
        val c = coordinator(gate, 2, ThemeLedger.InMemory(), procStart = 25_000L)
        try {
            c.openProjection()
            assertTrue(gate.entered.await(5, TimeUnit.SECONDS), "lượt mở phải tới lệnh 16: ${fake.history}")
            assertEquals(listOf(31), ops(fake.history), "31 đã gửi, 16 đang bay")
            assertEquals(CastRestartHazard.OP_IN_FLIGHT, c.restartHazard(), "giết lúc này = chuỗi mở dở dang")
            gate.release.countDown()
            awaitQuiet(c)
            assertEquals(listOf(31, 16, 35), ops(fake.history))
            now = 30_725L                                                    // `keys=STUCK(tat-may)->ESCALATE`
            assertEquals(CastRestartHazard.THEME_GAP, c.restartHazard(), "khởi động lại lúc này ⇒ tiến trình mới TOO_SOON")
            now = 27_751L + ThemeLedger.MIN_GAP_MS - 1
            assertEquals(CastRestartHazard.THEME_GAP, c.restartHazard())
            now = 27_751L + ThemeLedger.MIN_GAP_MS
            assertNull(c.restartHazard(), "đủ 15 s ⇒ khởi động lại không còn hại gì cho chiếu cụm")
        } finally {
            gate.release.countDown()
            c.shutdown()
        }
    }

    /**
     * Khoá BÀI HỌC của log 15:17 bằng chính cổng theme: tiến trình mới mở chiếu TRONG khoảng 15 s ⇒ bỏ theme, kiểu "chưa rõ" (bản
     * cũ); mở SAU khi mối nguy hết (2.93 hoãn lượt chữa phím tới lúc `restartHazard() == null`) ⇒ gửi được theme, kiểu RECT.
     */
    @Test
    fun `log xe 15-17 dung lai - khoi dong lai giua khoang 15 s thi tien trinh moi TOO_SOON, doi het moi nguy thi gui duoc theme`() {
        val ledger = ThemeLedger.InMemory()
        now = 27_751L
        val cShell = FakeShell().apply { clusterDisplayId = 2; vdAfterTheme = 3 }
        val c = coordinator(cShell, 2, ledger, procStart = 25_000L)
        val afterC: String?
        try {
            c.openProjection()
            awaitQuiet(c)
            assertEquals(listOf(31, 16, 35), ops(cShell.history), "tiến trình C: SEND 31")
            afterC = ledger.read()
        } finally {
            c.shutdown()
        }

        // Bản cũ: lượt chữa phím bắn 15:17:31.392 ⇒ tiến trình D bật 15:17:32, mở chiếu 15:17:34.286.
        now = 34_286L
        val dShell = FakeShell().apply { clusterDisplayId = 3 }
        val d = coordinator(dShell, 3, ThemeLedger.InMemory(afterC), procStart = 32_000L)
        try {
            d.openProjection()
            awaitQuiet(d)
            assertEquals(listOf(16, 35), ops(dShell.history), "TOO_SOON ⇒ bỏ theme, 16/35 đi tiếp")
            assertTrue(d.themeVerdict!!.contains("TOO_SOON") && d.themeVerdict!!.contains("còn 8465 ms"), d.themeVerdict)
            assertEquals(BelievedStyle.UNKNOWN, d.castSession?.believed, "đúng triệu chứng log: phiên chưa rõ kiểu")
        } finally {
            d.shutdown()
        }

        // 2.93: lượt chữa phím chờ tới khi `restartHazard()` của C hết (≥ 27.751 + 15 s) rồi mới bắn ⇒ tiến trình E mở SAU khoảng.
        now = 45_000L
        val eShell = FakeShell().apply { clusterDisplayId = 3; vdAfterTheme = 4 }
        val e = coordinator(eShell, 3, ThemeLedger.InMemory(afterC), procStart = 43_500L)
        try {
            e.openProjection()
            awaitQuiet(e)
            assertEquals(listOf(31, 16, 35), ops(eShell.history), "đủ 15 s, màn ảo trống ⇒ gửi theme")
            assertEquals(BelievedStyle.RECT, e.castSession?.believed, "phiên xác nhận kiểu — không còn 'chưa rõ'")
        } finally {
            e.shutdown()
        }
    }
}
