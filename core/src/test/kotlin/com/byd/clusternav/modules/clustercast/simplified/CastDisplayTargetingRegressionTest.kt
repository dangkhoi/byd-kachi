package com.byd.clusternav.modules.clustercast.simplified

import com.byd.clusternav.modules.clustercast.CastDisplayFixtures2026_09_15
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Regression lock (c) — spec `kachi-hal187-cast-remediation` §4.1, R1/R2 (P0, an toàn vận hành).
 *
 * [ĐO] xe 2026-09-15 sau reboot: seed = 1, nhưng display 1 = `kachi-slot-0` (VD của CHÍNH launcher), cụm thật =
 * display 2 (`fission_bg_xdjaVirtualSurface`). Bản cũ dò TRƯỚC khi mở projection (VD fission chưa tồn tại) → hụt
 * → rơi về seed → `am start --display 1` ClusterBlack + GMaps vào ô của launcher.
 *
 * Khoá: với fake shell trả đúng grep thật (fixture nguyên văn), coordinator dựng với seed **1** phải
 *   1. dò (`DETECT_CMD`) SAU chuỗi mở projection (profile 35) và TRƯỚC lệnh đặt ClusterBlack;
 *   2. mọi `--display N` / `-d N` (N ≠ 0) = 2, KHÔNG có = 1 — cả lúc mở projection lẫn lúc cast app;
 *   3. dò hụt (không có fission) → KHÔNG đặt gì, trả đồng hồ (đóng projection), không rơi về seed.
 */
class CastDisplayTargetingRegressionTest {

    private lateinit var shell: FakeShell
    private lateinit var prefs: FakePrefs
    private lateinit var coordinator: SimpleCastCoordinator
    private val self = CastDisplayFixtures2026_09_15.LAUNCHER_PKG

    @BeforeEach
    fun setup() {
        shell = FakeShell().apply {
            clusterDisplayId = 2
            selfPackage = self
            clusterDetectOut = CastDisplayFixtures2026_09_15.DETECT_OUT_WITH_SLOT
            runningTasks["com.google.android.apps.maps"] = 6
        }
        prefs = FakePrefs()
        coordinator = SimpleCastCoordinator(
            ProjectionManager(shell, sleepMs = {}),
            DisplayConfigurator(shell),
            AppMover(shell, sleepMs = {}),
            prefs, shell,
            displayId = 1,              // seed sai — đúng cảnh boot log "Cluster display seed = 1"
            selfPackage = self,
            detectSleepMs = {},
        )
    }

    /** Mọi id nhắm tới trong lệnh `am … --display N` / `wm … -d N`, bỏ display 0 (màn giữa — đường trả app). */
    private fun targetedDisplays(cmds: List<String>): List<Pair<String, Int>> =
        cmds.mapNotNull { c ->
            Regex("""(?:--display|-d) (\d+)""").find(c)?.groupValues?.get(1)?.toInt()?.takeIf { it != 0 }?.let { c to it }
        }

    @Test
    fun `openProjection - dò SAU khi mở projection, TRƯỚC khi đặt ClusterBlack - mọi lệnh nhắm display 2, không có 1`() {
        coordinator.openProjection()
        awaitState<SimpleCastState.Idle>()
        val h = shell.history.toList()

        val open35 = h.indexOfFirst { it.contains("AutoContainer") && it.contains("i32 35") }
        val black = h.indexOfFirst { it.startsWith("am start") && it.contains("ClusterBlackActivity") }
        assertTrue(open35 >= 0, "phải mở projection (profile 35); history=$h")
        assertTrue(black >= 0, "phải đặt ClusterBlack; history=$h")
        val detectAfterOpen = h.withIndex().any { (i, c) -> c == ClusterDisplayResolver.DETECT_CMD && i in (open35 + 1) until black }
        assertTrue(detectAfterOpen, "phải có DETECT_CMD SAU profile 35 và TRƯỚC am start ClusterBlack; history=$h")

        val targets = targetedDisplays(h)
        assertTrue(targets.isNotEmpty(), "phải có lệnh nhắm display cụm; history=$h")
        assertTrue(targets.all { it.second == 2 }, "mọi lệnh phải nhắm display 2 (cụm thật), không phải seed 1: $targets")
        assertTrue(h.none { it.contains("--display 1 ") || it.endsWith("-d 1") || it.contains("-d 1 ") },
            "KHÔNG được có lệnh nào nhắm display 1 (slot của launcher): $h")
        assertEquals(2, prefs.lastDisplayId(), "id dò live phải được persist")
    }

    @Test
    fun `cast app NORMAL - dò live trước khi đặt, task đi tới display 2`() {
        coordinator.openProjection()
        awaitState<SimpleCastState.Idle>()
        val mark = shell.history.size

        coordinator.dispatch(SimpleCastIntent.CastFull("com.google.android.apps.maps", AppType.NORMAL))
        awaitState<SimpleCastState.CastingFull>()
        val h = shell.history.drop(mark)

        val firstPlacement = h.indexOfFirst { targetedDisplays(listOf(it)).isNotEmpty() }
        val detect = h.indexOfFirst { it == ClusterDisplayResolver.DETECT_CMD }
        assertTrue(detect in 0 until firstPlacement, "DETECT_CMD phải chạy TRƯỚC lệnh đặt đầu tiên; cmds=$h")
        val targets = targetedDisplays(h)
        assertTrue(targets.isNotEmpty() && targets.all { it.second == 2 }, "cast phải nhắm display 2: $targets")
        assertTrue(h.none { it.contains("--display 1 ") || it.contains("-d 1 ") || it.endsWith("-d 1") }, "không nhắm display 1: $h")
    }

    @Test
    fun `dò hụt (chưa có fission sau khi mở) → KHÔNG đặt ClusterBlack, không wm -d, đóng projection, không rơi về seed`() {
        shell.clusterDetectOut = CastDisplayFixtures2026_09_15.DETECT_OUT_SLOT_ONLY   // chỉ có slot 1 của launcher
        coordinator.openProjection()
        awaitState<SimpleCastState.Error>()
        val h = shell.history.toList()

        assertTrue(h.none { it.startsWith("am start") && it.contains("ClusterBlackActivity") }, "không được đặt ClusterBlack: $h")
        assertTrue(targetedDisplays(h).isEmpty(), "không được có lệnh -d/--display nào (kể cả seed 1): ${targetedDisplays(h)}")
        assertTrue(h.any { it.contains("AutoContainer") && it.contains("i32 18") }, "phải trả đồng hồ (đóng projection): $h")
        assertEquals(null, prefs.lastDisplayId(), "không persist id nào khi hụt")
        // Đã lặp dò nhiều lần (VD fission tạo bất đồng bộ) chứ không bỏ cuộc sau 1 lần.
        val detects = h.count { it == ClusterDisplayResolver.DETECT_CMD }
        assertTrue(detects >= ClusterDisplayResolver.AWAIT_ATTEMPTS, "phải lặp dò ≥ ${ClusterDisplayResolver.AWAIT_ATTEMPTS} lần, thấy $detects")
    }

    @Test
    fun `chưa openProjection → cast bị từ chối, không lệnh đặt nào theo seed`() {
        coordinator.dispatch(SimpleCastIntent.CastFull("com.google.android.apps.maps", AppType.NORMAL))
        awaitState<SimpleCastState.Error>()
        assertTrue(targetedDisplays(shell.history.toList()).isEmpty(), "không được đặt gì: ${shell.history}")
    }

    private inline fun <reified T : SimpleCastState> awaitState(timeoutMs: Long = 4000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (coordinator.state !is T && System.currentTimeMillis() < deadline) Thread.sleep(10)
        assertTrue(coordinator.state is T, "Expected ${T::class.simpleName} but got ${coordinator.state}; history=${shell.history}")
    }
}
