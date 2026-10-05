package com.byd.clusternav.modules.clustercast.simplified

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 2.89 · B2 VM-PREREQ-TRUTH — lượt mở chiếu cụm không còn cờ một-lần `doze_whitelist_applied`: mỗi lượt mở gọi móc
 * điều kiện nền ([SimpleCastCoordinator.appPrereqs]) qua CHÍNH shell của coordinator, và móc hỏng không chặn lượt mở.
 * Kèm luật gói tự chiếu của hồ sơ ([CastAutoStartPkgs]) — cùng luật `BubbleAutostart.dispatch`.
 */
class CastAppPrereqHookTest {

    private fun coordinator(shell: FakeShell, prefs: FakePrefs = FakePrefs(), hook: (SimpleCastShell) -> Unit) =
        SimpleCastCoordinator(
            ProjectionManager(shell, sleepMs = {}),
            DisplayConfigurator(shell),
            AppMover(shell, sleepMs = {}),
            prefs, shell, displayId = 1, detectSleepMs = {},
            appPrereqs = hook,
        )

    private fun awaitNotOpening(c: SimpleCastCoordinator) {
        val deadline = System.currentTimeMillis() + 4_000
        while ((c.state is SimpleCastState.Off || c.state is SimpleCastState.Opening) && System.currentTimeMillis() < deadline) {
            Thread.sleep(10)
        }
    }

    @Test
    fun `moi luot mo chieu goi moc dieu kien nen qua CHINH shell, khong cho lenh deviceidle ghi cung`() {
        val shell = FakeShell()
        val seen = mutableListOf<SimpleCastShell>()
        val c = coordinator(shell) { seen += it }
        c.openProjection()
        awaitNotOpening(c)
        assertEquals(1, seen.size, "một lượt mở = một lượt đọc sự thật")
        assertSame(shell, seen.single())
        assertTrue(shell.history.none { it.contains("deviceidle") }, "không còn lệnh ghi cứng `+vn.vietmap.live`: ${shell.history}")
        c.shutdown()
    }

    @Test
    fun `mo lai lan hai van doc lai (khong co co mot-lan)`() {
        val shell = FakeShell()
        var calls = 0
        val c = coordinator(shell) { calls++ }
        c.openProjection()
        awaitNotOpening(c)
        c.closeProjection()
        val deadline = System.currentTimeMillis() + 4_000
        while (c.state !is SimpleCastState.Off && System.currentTimeMillis() < deadline) Thread.sleep(10)
        c.openProjection()
        awaitNotOpening(c)
        assertEquals(2, calls)
        c.shutdown()
    }

    @Test
    fun `moc nem loi - luot mo chieu van di tiep`() {
        val shell = FakeShell()
        val c = coordinator(shell) { throw IllegalStateException("kênh đóng") }
        c.openProjection()
        awaitNotOpening(c)
        assertTrue(c.state is SimpleCastState.Idle, "móc hỏng không được chặn mở chiếu: ${c.state}")
        c.shutdown()
    }

    // ── Gói tự chiếu của hồ sơ ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun `cast tat - khong goi nao`() {
        val p = FakePrefs().apply { setAutoStartEnabled(true); setAutoStartPackage("com.byd.androidauto") }
        assertTrue(CastAutoStartPkgs.of(p).isEmpty())
    }

    @Test
    fun `toan cum thang chia doi, bo rong`() {
        val p = FakePrefs().apply {
            setCastEnabled(true)
            setAutoStartEnabled(true); setAutoStartPackage(" com.byd.androidauto ")
            setAutoStartSplitEnabled(true); setAutoStartLeftPackage("vn.vietmap.live"); setAutoStartRightPackage("x.y")
        }
        assertEquals(listOf("com.byd.androidauto"), CastAutoStartPkgs.of(p))
        p.setAutoStartEnabled(false)
        assertEquals(listOf("vn.vietmap.live", "x.y"), CastAutoStartPkgs.of(p))
        p.setAutoStartRightPackage(" ")
        assertEquals(listOf("vn.vietmap.live"), CastAutoStartPkgs.of(p))
        p.setAutoStartSplitEnabled(false)
        assertFalse(CastAutoStartPkgs.of(p).isNotEmpty())
    }
}
