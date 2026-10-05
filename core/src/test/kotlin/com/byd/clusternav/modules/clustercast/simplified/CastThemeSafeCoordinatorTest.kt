package com.byd.clusternav.modules.clustercast.simplified

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * CLUSTER-THEME-SAFE (2.89, P0) — khoá ĐƯỜNG NỐI của cổng theme trong coordinator (mở · tắt · mở lại · màn ảo có app lạ)
 * và việc chuỗi lệnh đi theo hồ sơ đời xe.
 *
 * Lỗi xe khoá ở đây [ĐO 05/10, `docs/diagnostics/oncar-2026-10-05-slot-cluster.md` §4]: Kachi gửi `30` MỖI lần mở chiếu
 * trong khi `ClusterBlackActivity` của nó nằm lại trên màn ảo cụm sau khi tắt chiếu; gửi theme khi màn ảo còn lớp ⇒
 * `system_server` khởi động lại. Bản cũ (`ProjectionManager` ghi cứng 30→16→35) không đọc gì trước khi gửi.
 *
 * B1a (ĐỔI GHIM có lý do): theme CHỈ gửi khi CHƯA có màn ảo cụm (mức A — [ĐO F2] đường đã chạy từ 08/02). Màn ảo còn mà trống
 * ⇒ `VD_PRESENT` (chưa đo — bước V4b; DashCast: tạo lại màn ảo làm hỏng sổ display ATM). Các bài mức B (gỡ ClusterBlack rồi
 * gửi, FOREIGN, đọc cửa sổ hỏng) giữ nguyên ý nhưng chạy với `themeOnVacantVd = true` — luật vẫn sống cho ngày V4b xanh.
 */
class CastThemeSafeCoordinatorTest {

    private lateinit var shell: FakeShell
    private lateinit var coordinator: SimpleCastCoordinator

    private val standardConfig =
        " configuration={1.0 winConfig={ mBounds=Rect(0, 0 - 1920, 720) mWindowingMode=freeform mDisplayWindowingMode=fullscreen mActivityType=standard mAlwaysOnTop=undefined mRotation=ROTATION_0} s.3}"

    private val levelB = ProjectionRecipe.SEAL_DL3.copy(themeOnVacantVd = true)

    @BeforeEach
    fun setup() {
        shell = FakeShell().apply { clusterStackConfig = standardConfig }
    }

    private fun build(recipe: ProjectionRecipe = ProjectionRecipe.SEAL_DL3, ledger: ThemeLedger.Store = ThemeLedger.InMemory()) {
        coordinator = SimpleCastCoordinator(
            ProjectionManager(shell, sleepMs = {}, recipe = recipe),
            DisplayConfigurator(shell), AppMover(shell, sleepMs = {}), FakePrefs(), shell,
            displayId = 1, detectSleepMs = {}, themeLedger = ledger,
        )
    }

    private fun autoContainer(from: Int = 0) = shell.history.drop(from).filter { it.contains(" 2 i32 1000 i32 ") }
    private fun ops(cmds: List<String>) = cmds.map { it.substringAfter("i32 1000 i32 ").substringBefore(" ").toInt() }

    // ── B1a · mức A (đường đã chạy) ────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `mo lan dau sau no may - CHUA co man ao cum - 30 16 35, chi doc display truoc 30`() {
        shell.vdAbsentUntilCast = true
        build()
        coordinator.openProjection()
        awaitState<SimpleCastState.Idle>()
        assertEquals(listOf(30, 16, 35), ops(autoContainer()))
        val send30 = shell.history.indexOfFirst { it.contains("i32 30 ") }
        val detect = shell.history.indexOf(ClusterDisplayResolver.DETECT_CMD)
        assertTrue(detect in 0 until send30, "phải đọc display TRƯỚC khi gửi 30: ${shell.history}")
        val before30 = shell.history.take(send30)
        assertTrue(ClusterThemeGuard.WINDOWS_CMD !in before30, "chưa có màn ảo ⇒ không đọc cửa sổ (không lớp nào): $before30")
    }

    @Test
    fun `mo lan dau - so theme ghi pending TRUOC lenh 30 va ok sau`() {
        shell.vdAbsentUntilCast = true
        val seen = mutableListOf<String>()
        val ledger = object : ThemeLedger.Store {
            var v: String? = null
            override fun read() = v
            override fun write(value: String): Boolean {
                seen += "${value.substringAfter(';').substringBefore(';')}@${shell.history.count { it.contains("i32 30 ") }}"
                v = value
                return true
            }
        }
        build(ledger = ledger)
        coordinator.openProjection()
        awaitState<SimpleCastState.Idle>()
        assertEquals(listOf("pending@0", "ok@1"), seen, "pending khi CHƯA gửi 30, ok khi đã gửi")
        assertEquals(ThemeLedger.State.OK, ThemeLedger.decode(ledger.v)?.state)
        assertEquals(30, ThemeLedger.decode(ledger.v)?.op)
    }

    // ── B1a · màn ảo còn ⇒ VD_PRESENT (mặc định mọi hồ sơ) ──────────────────────────────────────────────────────────────

    @Test
    fun `man ao cum con ma TRONG - VD_PRESENT, chi 16 35, khong doc stack-cua so`() {
        build()
        coordinator.openProjection()
        awaitState<SimpleCastState.Idle>()
        assertEquals(listOf(16, 35), ops(autoContainer()))
        val firstAc = shell.history.indexOfFirst { it.contains(" 2 i32 1000 i32 ") }
        assertTrue(shell.history.take(firstAc).none { it == ClusterThemeGuard.WINDOWS_CMD }, "cờ tắt ⇒ không đọc cửa sổ")
        assertTrue(coordinator.themeGuard.lastVerdict!!.contains("VD_PRESENT"), coordinator.themeGuard.lastVerdict)
    }

    @Test
    fun `tien trinh moi gap ClusterBlack con nam tren cum - KHONG go, KHONG 30, chi 16 35`() {
        shell.execute("am start --display 1 --windowingMode 5 -n 'com.byd.clusternav/com.byd.clusternav.modules.clustercast.ClusterBlackActivity'")
        build()
        val mark = shell.history.size
        coordinator.openProjection()
        awaitState<SimpleCastState.Idle>()
        val h = shell.history.drop(mark)
        assertEquals(listOf(16, 35), ops(autoContainer(mark)), "B1a: gỡ ClusterBlack KHÔNG còn mở khoá theme: $h")
        assertTrue(h.none { it.startsWith("am stack remove") }, "không gỡ gì trước lượt mở: $h")
    }

    @Test
    fun `restoreCluster - dong roi mo lai khi ClusterBlack con tren man ao - 0 lenh theme`() {
        shell.vdAbsentUntilCast = true
        build()
        coordinator.openProjection()
        awaitState<SimpleCastState.Idle>()
        coordinator.closeProjection()
        awaitState<SimpleCastState.Off>()
        // Lượt gỡ lúc tắt hỏng (giả lập ClusterBlack nằm lại) — đúng cảnh [ĐO 05/10].
        shell.execute("am start --display 1 --windowingMode 5 -n 'com.byd.clusternav/com.byd.clusternav.modules.clustercast.ClusterBlackActivity'")
        val mark = shell.history.size
        coordinator.openProjection()
        awaitState<SimpleCastState.Idle>()
        assertEquals(listOf(16, 35), ops(autoContainer(mark)), "restoreCluster không bao giờ gửi theme khi màn ảo còn: ${shell.history.drop(mark)}")
    }

    @Test
    fun `tat chieu - 18 roi 0 roi GO ClusterBlack (ve sinh, duong moi xuong cuoi)`() {
        build()
        coordinator.openProjection()
        awaitState<SimpleCastState.Idle>()
        val mark = shell.history.size
        coordinator.closeProjection()
        awaitState<SimpleCastState.Off>()
        val h = shell.history.drop(mark)
        val i18 = h.indexOfFirst { it.contains("i32 18 ") }
        val i0 = h.indexOfFirst { it.contains("i32 0 ") }
        val remove = h.indexOf("am stack remove 2")
        assertTrue(i18 in 0 until i0 && i0 < remove, "thứ tự phải là 18 → 0 → gỡ ClusterBlack: $h")
    }

    @Test
    fun `mo lai trong cung tien trinh - man ao con - CHI 16 35`() {
        shell.vdAbsentUntilCast = true
        build()
        coordinator.openProjection()
        awaitState<SimpleCastState.Idle>()
        coordinator.closeProjection()
        awaitState<SimpleCastState.Off>()
        val mark = shell.history.size
        coordinator.openProjection()
        awaitState<SimpleCastState.Idle>()
        assertEquals(listOf(16, 35), ops(autoContainer(mark)), "không gửi lại theme: ${shell.history.drop(mark)}")
    }

    @Test
    fun `khong doc duoc dumpsys display - DUNG luot mo, KHONG 16 35, loi co ly do`() {
        shell.failCommands += ClusterDisplayResolver.DETECT_CMD
        build()
        coordinator.openProjection()
        awaitState<SimpleCastState.Error>()
        assertEquals(emptyList<String>(), autoContainer(), "không lệnh AutoContainer nào: ${shell.history}")
        val msg = (coordinator.state as SimpleCastState.Error).message
        assertTrue(msg.contains("theme 30") && msg.contains("UNREADABLE") && msg.contains("DỪNG"), msg)
        assertTrue(shell.history.none { it.contains("ClusterBlackActivity") }, "không đặt ClusterBlack khi chưa mở")
    }

    @Test
    fun `chua co man ao cum, so cung tien trinh ghi 30 ok vua xong - TOO_SOON nhung cum da Bo tron - 16 35 di tiep`() {
        shell.clusterDetectOut = "  Display 0:\n"
        val now = ThemeLedger.JVM_CLOCK.now()
        val ledger = ThemeLedger.InMemory(ThemeLedger.encode(ThemeLedger.Entry(30, ThemeLedger.State.OK, now.elapsedMs, now.boot)))
        build(ledger = ledger)
        coordinator.openProjection()
        awaitState<SimpleCastState.Error>()     // không dò ra màn ảo sau khi mở (fake) — điều kiểm là chuỗi lệnh
        assertEquals(listOf(16, 35, 18, 0), ops(autoContainer()), "bỏ 30 (TOO_SOON), 16/35 đi tiếp: ${shell.history}")
        assertTrue(coordinator.themeGuard.lastVerdict!!.contains("TOO_SOON"), coordinator.themeGuard.lastVerdict)
    }

    /**
     * Review 2.89 Pass 2 · whole-r1-5 — lượt mở DỪNG vì khoảng 15 s (sổ `30;pending` vừa ghi, chưa có màn ảo) ⇒ coordinator báo
     * còn bao lâu ([themeGapRetryMs]) để vòng tự mở lúc nổ máy (`BubbleAutostart`) chờ hết khoảng thay vì đốt lượt thử.
     */
    @Test
    fun `Pass 2 - luot mo dung vi TOO_SOON - themeGapRetryMs bao con bao lau, luot dung vi ly do khac thi null`() {
        shell.clusterDetectOut = "  Display 0:\n"
        val now = ThemeLedger.JVM_CLOCK.now()
        build(ledger = ThemeLedger.InMemory(ThemeLedger.encode(ThemeLedger.Entry(30, ThemeLedger.State.PENDING, now.elapsedMs, now.boot))))
        coordinator.openProjection()
        awaitState<SimpleCastState.Error>()
        assertEquals(emptyList<String>(), autoContainer(), "pending ⇒ kiểu UNKNOWN ⇒ DỪNG, 0 lệnh: ${shell.history}")
        val gap = coordinator.themeGapRetryMs()
        assertTrue(gap != null && gap in 1..ThemeLedger.MIN_GAP_MS, "còn khoảng: $gap")

        shell = FakeShell().apply { failCommands += ClusterDisplayResolver.DETECT_CMD }
        build()
        coordinator.openProjection()
        awaitState<SimpleCastState.Error>()
        assertEquals(null, coordinator.themeGapRetryMs(), "dừng vì đọc hỏng (không phải khoảng 15 s) ⇒ thử lại như thường")
    }

    // ── Mức B (themeOnVacantVd = true — chờ đo V4b): luật A1 giữ nguyên ──────────────────────────────────────────────────

    @Test
    fun `muc B - chi ClusterBlack - go no TRUOC roi moi gui 30`() {
        shell.execute("am start --display 1 --windowingMode 5 -n 'com.byd.clusternav/com.byd.clusternav.modules.clustercast.ClusterBlackActivity'")
        build(levelB)
        val mark = shell.history.size
        coordinator.openProjection()
        awaitState<SimpleCastState.Idle>()
        val h = shell.history.drop(mark)
        val remove = h.indexOf("am stack remove 2")
        val send30 = h.indexOfFirst { it.contains("i32 30 ") }
        assertTrue(remove in 0 until send30, "phải gỡ ClusterBlack TRƯỚC 30: $h")
        assertEquals(listOf(30, 16, 35), ops(autoContainer(mark)))
    }

    @Test
    fun `muc B - man ao cum con app la (CarPlay) - BO 30, van mo 16 35, khong go gi`() {
        shell.execute("am start --display 1 -n 'com.byd.carplay.ui/.VideoActivity'")
        build(levelB)
        val mark = shell.history.size
        coordinator.openProjection()
        awaitState<SimpleCastState.CastingFull>()
        val h = shell.history.drop(mark)
        assertEquals(listOf(16, 35), ops(autoContainer(mark)), "có lớp lạ ⇒ không gửi theme: $h")
        assertTrue(h.none { it.startsWith("am stack remove") }, "không gỡ gì khi đằng nào cũng bỏ theme: $h")
        assertTrue(coordinator.themeGuard.lastVerdict!!.contains("FOREIGN"), coordinator.themeGuard.lastVerdict)
    }

    /**
     * Review 2.89 Pass 1 · safety-1 (giữ ở mức B): đọc cửa sổ hỏng ⇒ cổng ABORT; sổ trống ⇒ không chứng minh được kiểu ⇒ DỪNG,
     * 0 lệnh AutoContainer (16/35 trong theme gốc Seal = mất km/h [ĐO xe 05/10]).
     */
    @Test
    fun `muc B - khong doc duoc cua so - DUNG luot mo, KHONG 16 35`() {
        shell.failCommands += ClusterThemeGuard.WINDOWS_CMD
        build(levelB)
        coordinator.openProjection()
        awaitState<SimpleCastState.Error>()
        assertEquals(emptyList<String>(), autoContainer(), "không lệnh AutoContainer nào: ${shell.history}")
        val msg = (coordinator.state as SimpleCastState.Error).message
        assertTrue(msg.contains("theme 30") && msg.contains("UNREADABLE"), msg)
    }

    // ── Hồ sơ đời xe ─────────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `chuoi lenh theo HO SO - DiLink5 (auto_container, chi 16) khong co theme, khong doc cong`() {
        build(ProjectionRecipe.of("auto_container", listOf(16), listOf(18, 0), emptyMap(), null))
        coordinator.openProjection()
        awaitState<SimpleCastState.Idle>()
        assertEquals(listOf("service call auto_container 2 i32 1000 i32 16 s16 \"\""), autoContainer())
        assertTrue(shell.history.none { it == ClusterThemeGuard.WINDOWS_CMD }, "không opcode theme ⇒ không đọc cổng")
        val mark = shell.history.size
        coordinator.closeProjection()
        awaitState<SimpleCastState.Off>()
        assertEquals(listOf(18, 0), ops(autoContainer(mark)))
        assertTrue(autoContainer().all { it.startsWith("service call auto_container ") })
    }

    @Test
    fun `moi lenh AutoContainer deu giu duoi s16 rong (thieu la EX_NULL_POINTER tren xe)`() {
        shell.vdAbsentUntilCast = true
        build()
        coordinator.openProjection()
        awaitState<SimpleCastState.Idle>()
        coordinator.closeProjection()
        awaitState<SimpleCastState.Off>()
        val cmds = autoContainer()
        assertTrue(cmds.isNotEmpty())
        assertTrue(cmds.all { it.endsWith(" s16 \"\"") }, "thiếu `s16 \"\"`: $cmds")
    }

    @Test
    fun `do lai cong thuc MOT lan o luot mo dau (car type qua dadb) roi moi quyet`() {
        shell.vdAbsentUntilCast = true
        var probes = 0
        coordinator = SimpleCastCoordinator(
            ProjectionManager(shell, sleepMs = {}), DisplayConfigurator(shell), AppMover(shell, sleepMs = {}), FakePrefs(), shell,
            displayId = 1, detectSleepMs = {},
            recipeProbe = { probes++; ProjectionRecipe.SEAL_DL3.copy(nativeStyle = CastStyle.RECT) },
        )
        coordinator.openProjection()
        awaitState<SimpleCastState.Idle>()
        coordinator.closeProjection()
        awaitState<SimpleCastState.Off>()
        coordinator.openProjection()
        awaitState<SimpleCastState.Idle>()
        assertEquals(1, probes, "dò đúng một lần mỗi tiến trình")
        assertEquals(CastStyle.RECT, coordinator.projection.recipe.nativeStyle)
        // Bo tròn trên xe gốc chữ nhật vẫn là 30 lần đầu (desired ≠ native).
        assertEquals(30, ops(autoContainer()).first())
    }

    private inline fun <reified T : SimpleCastState> awaitState(timeoutMs: Long = 4000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (coordinator.state !is T && System.currentTimeMillis() < deadline) Thread.sleep(10)
        assertTrue(coordinator.state is T, "Expected ${T::class.simpleName} but got ${coordinator.state}; history=${shell.history}")
    }
}
