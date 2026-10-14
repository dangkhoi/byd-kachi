package com.byd.clusternav.modules.clustercast.simplified

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 3.1 · R26 — khoá lỗi hiện trường Seal 13/10 (Kachi 3.0 (204)): Waze chiếu lên cụm bị nhốt trong 1920×520.
 *
 * [ĐO xe] `docs/diagnostics/oncar-2026-10-13/10-recast-waze-2.txt`: Dừng rồi chiếu lại Waze ⇒ `wm size -d 1` =
 * `Physical 1920x720 / Override 1920x520`, task + `parent=[0,0][1920,520]` — Kachi áp lại `wmSize` 1920×520 đã lưu cho Waze ở MỖI
 * lần chiếu (`pinFull` → `DisplayConfigurator.apply`). `07-after-reset.txt`: `wm size reset` + `wm overscan reset` ⇒ 720, owner
 * xác nhận. `01-read.txt`: màn cụm `mDisplayWindowingMode=freeform`. Luật khôi phục: V1 `CastShell.resetDisplayGeometry`.
 */
class CastFreeformGeometryTest : SimpleCastCoordinatorHarness() {

    private fun fixture(name: String): String =
        requireNotNull(javaClass.getResourceAsStream("/diagnostics/cluster-freeform-2026-10-13/$name")) { "thiếu fixture $name" }
            .bufferedReader().readText()

    /** Dòng cấu hình stack cụm NGUYÊN VĂN từ `01-read.txt` (dòng 40, stack ClusterBlack trên display 1). */
    private val freeformConfigLine: String
        get() = fixture("01-read.txt").lines().first { it.contains("mDisplayWindowingMode=freeform") }

    private var override = "1920x520"

    private fun freeformCar() {
        shell.clusterStackConfig = freeformConfigLine
        shell.responders += { cmd ->
            when (cmd) {
                "wm size -d 1" -> ShellResult(0, "Physical size: 1920x720\n" + (override.let { "Override size: $it\n" }), "")
                "wm size reset -d 1" -> { override = "1920x720"; ShellResult(0, "", "") }
                else -> Regex("""^wm size (\d+x\d+) -d 1$""").find(cmd)?.let { override = it.groupValues[1]; ShellResult(0, "", "") }
            }
        }
        // Bản ghi CŨ của người dùng thật: đường lùi `wm size` đã lưu 1920×520 cho app (bounds như lúc lưu).
        prefs.saveDisplayConfig(WAZE, CastProfile.FULL, DisplayConfig("1920x520", "0,0,0,0", "240", CastBounds(0, 60, 1920, 520)))
    }

    private fun castWaze() {
        coordinator.openProjection()
        awaitState<SimpleCastState.Idle>()
        coordinator.dispatch(SimpleCastIntent.CastFull(WAZE, AppType.NORMAL))
        awaitTrue(5_000) { (coordinator.state as? SimpleCastState.CastingFull)?.targetPkg == WAZE && coordinator.executor.isIdle }
    }

    // ─── Bộ đọc thuần (fixture xe) ───────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `doc che do man tu am stack list that cua xe - cum freeform, man launcher va man chinh khong`() {
        val out = fixture("01-read.txt")
        assertEquals(true, ClusterDisplayFacts.displayFreeform(out, 1))
        assertEquals(false, ClusterDisplayFacts.displayFreeform(out, 2), "kachi-slot-0 fullscreen")
        assertEquals(false, ClusterDisplayFacts.displayFreeform(out, 0))
        assertNull(ClusterDisplayFacts.displayFreeform(out, 7), "không có stack ⇒ chưa biết")
        assertNull(ClusterDisplayFacts.displayFreeform(fixture("10-recast-waze-2.txt"), 1), "bản grep không có dòng cấu hình ⇒ chưa biết")
    }

    @Test
    fun `lam sach wmSize chi khi man freeform va biet kich vat ly`() {
        val stale = DisplayConfig("1920x520", "0,0,0,0", "240", CastBounds(0, 60, 1920, 520))
        val phys = 1920 to 720
        assertEquals("1920x720", ClusterDisplayFacts.sanitize(stale, ClusterDisplayFacts.Facts(1, true, phys)).wmSize)
        assertEquals("240", ClusterDisplayFacts.sanitize(stale, ClusterDisplayFacts.Facts(1, true, phys)).density, "giữ DPI")
        assertEquals("1920x520", ClusterDisplayFacts.sanitize(stale, ClusterDisplayFacts.Facts(1, false, phys)).wmSize, "không freeform ⇒ đường lùi thật")
        assertEquals("1920x520", ClusterDisplayFacts.sanitize(stale, ClusterDisplayFacts.Facts(1, null, phys)).wmSize, "chưa biết ⇒ giữ cũ")
        assertEquals("1920x520", ClusterDisplayFacts.sanitize(stale, ClusterDisplayFacts.Facts(1, true, null)).wmSize)
    }

    @Test
    fun `dai mep -+ dung kich vat ly, khong dung wmSize cu`() {
        assertEquals(1920 to 720, ClusterDisplayFacts.editFrameSize(1920 to 720, "1920x520"))
        assertEquals(1920 to 520, ClusterDisplayFacts.editFrameSize(null, "1920x520"), "chưa đo ⇒ lùi như cũ")
        assertEquals(1920 to 720, ClusterDisplayFacts.editFrameSize(null, null))
    }

    // ─── Coordinator thật + shell giả (nhật ký lệnh) ─────────────────────────────────────────────────────────────────

    @Test
    fun `ca Waze - ban ghi wmSize 1920x520 + cum freeform, chieu KHONG ap 1920x520, resize an thi go ep hinh hoc, giu DPI`() {
        freeformCar()
        val mark = shell.history.size
        castWaze()
        val cmds = shell.history.drop(mark)
        assertFalse(cmds.any { it.startsWith("wm size 1920x520") }, "không bao giờ áp lại wmSize cũ: $cmds")
        val resize = cmds.indexOfFirst { it.startsWith("am task resize") && it.endsWith(" 0 60 1920 520") }
        assertTrue(resize >= 0, "khung đã lưu vẫn áp bằng am task resize: $cmds")
        val after = cmds.drop(resize + 1)
        assertTrue("wm size reset -d 1" in after, "luật V1: resize ăn ⇒ wm size reset: $after")
        assertTrue("wm overscan reset -d 1" in after, "luật V1: resize ăn ⇒ wm overscan reset: $after")
        assertFalse(after.any { it.startsWith("wm density reset") }, "GIỮ DPI người lái: $after")
        assertEquals("1920x720", override, "màn cụm về kích vật lý")
        val s = coordinator.state as SimpleCastState.CastingFull
        assertEquals("1920x720", s.displayConfig.wmSize, "cấu hình phiên đã làm sạch (màn Cài đặt đọc từ đây)")
        // Dải −/+ (R26 + R7): trần dưới = 720 vật lý, tràn +50 % = 1080 — không phải 520/780.
        val (w, h) = ClusterDisplayFacts.editFrameSize(coordinator.clusterPhysicalSize, s.displayConfig.wmSize)
        assertEquals(1920 to 720, w to h)
        assertEquals(1080, CastGeometryGuard.overflowMax(h))
        assertEquals(true, coordinator.clusterFreeform)
    }

    @Test
    fun `go ep hinh hoc lap lai = 0 lenh, va -+ an tren freeform don wmSize cu cua ban ghi`() {
        freeformCar()
        castWaze()
        val mark = shell.history.size
        assertTrue(resize(0, 60, 1920, 700))
        val cmds = shell.history.drop(mark)
        assertFalse(cmds.any { it.startsWith("wm size") && it != "wm size -d 1" }, "đã gỡ ⇒ không lặp lệnh: $cmds")
        val saved = prefs.displayConfigFor(WAZE, CastProfile.FULL)!!
        assertEquals(CastBounds(0, 60, 1920, 700), saved.bounds)
        assertEquals("1920x720", saved.wmSize, "bản ghi sạch dần theo lần chỉnh")
    }

    @Test
    fun `resize hong tren man freeform KHONG lui wm size va khong luu`() {
        freeformCar()
        castWaze()
        shell.failCommands += "am task resize"
        val mark = shell.history.size
        assertFalse(resize(0, 60, 1920, 700))
        val cmds = shell.history.drop(mark)
        assertFalse(cmds.any { Regex("""^wm size \d+x\d+""").containsMatchIn(it) }, "không đường lùi wm size: $cmds")
        assertEquals(CastBounds(0, 60, 1920, 520), prefs.displayConfigFor(WAZE, CastProfile.FULL)?.bounds, "không lưu gì")
    }

    @Test
    fun `R6 - cum freeform an hang Dich noi dung va bo do dich da luu (0 lenh wm overscan co dau)`() {
        val projection = ProjectionManager(shell, sleepMs = {}, recipe = ProjectionRecipe.SEAL_DL3.copy(supportsOverscan = true))
        coordinator = SimpleCastCoordinator(projection, DisplayConfigurator(shell), AppMover(shell, sleepMs = {}), prefs, shell, displayId = 1)
        freeformCar()
        prefs.saveDisplayConfig(WAZE, CastProfile.FULL, DisplayConfig("1920x520", "0,-20,0,0", "240", CastBounds(0, 60, 1920, 520)))
        castWaze()
        assertFalse(coordinator.contentShiftSupported, "freeform bỏ qua overscan [ĐO xe 04-waze-overscan.txt] ⇒ ẩn hàng")
        assertFalse(shell.history.any { it.startsWith("wm overscan 0,-20") }, "không áp độ dịch đã lưu")
        val outcomes = CopyOnWriteArrayList<CastShiftResult>()
        val off = coordinator.shiftQueue.addListener { _, r -> outcomes += r }
        val mark = shell.history.size
        coordinator.shiftContent(WAZE, -20)
        awaitTrue(5_000) { outcomes.isNotEmpty() && coordinator.executor.isIdle }
        off()
        assertEquals(CastShiftResult.DROPPED, outcomes.single())
        assertFalse(shell.history.drop(mark).any { it.startsWith("wm overscan") })
    }

    @Test
    fun `man khong freeform (chua co dong cau hinh) - giu hanh vi cu, khong go`() {
        shell.reportWmSize = true
        prefs.saveDisplayConfig(WAZE, CastProfile.FULL, DisplayConfig("1920x520", "0,0,0,0", "240", CastBounds(0, 60, 1920, 520)))
        castWaze()
        assertNull(coordinator.clusterFreeform)
        assertTrue(shell.history.contains("wm size 1920x520 -d 1"), "đường cũ (đường lùi thật khi chưa đo được freeform)")
    }

    @Test
    fun `CarPlay tren cum freeform - khong go wm size cua no`() {
        freeformCar()
        coordinator.openProjection()
        awaitState<SimpleCastState.Idle>()
        coordinator.dispatch(SimpleCastIntent.CastFull("com.byd.autolink.carplay", AppType.CARPLAY))
        awaitTrue(5_000) { coordinator.state is SimpleCastState.CastingFull && coordinator.executor.isIdle }
        assertTrue(shell.history.contains("wm size 1422x800 -d 1"), "${shell.history}")
        assertFalse(shell.history.contains("wm size reset -d 1"), "CP giữ cấu hình đã đo: ${shell.history}")
    }

    private fun resize(l: Int, t: Int, r: Int, b: Int): Boolean {
        val outcomes = CopyOnWriteArrayList<CastResizeOutcome>()
        val off = coordinator.resizeQueue.addListener { o -> outcomes += o }
        coordinator.resizeActiveTarget(l, t, r, b)
        awaitTrue(5_000) { outcomes.isNotEmpty() && coordinator.executor.isIdle }
        off()
        return outcomes.single().result == CastResizeResult.APPLIED
    }

    private companion object {
        const val WAZE = "com.waze"
    }
}
