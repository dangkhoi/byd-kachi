package com.byd.clusternav.modules.clustercast.simplified

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * V-CLUSTER · VC-R6 — GHIM hình học của PHIÊN (spec `kachi-profiles-are-everything.html` §11.4.6, V-2 · V-3 ở §11.6).
 *
 * Khoá lỗi refute **B6** (repin đọc lại prefs ⇒ DPI/khung của hồ sơ MỚI tự nổ lên cụm giữa chuyến, không ai bấm) và
 * **C2** (`CastingSplit` không mang tỉ lệ ⇒ ô thứ hai, −/+, chip DPI, repin đều theo tỉ lệ của hồ sơ mới trong khi cụm
 * vẫn chia theo tỉ lệ cũ). "Đổi hồ sơ" ở tầng này = prefs sống đổi giá trị (đúng thứ `applyClusterNav` làm) mà
 * coordinator không được báo gì — K2: dự án không có listener prefs nào.
 *
 * Shell giả ghi mọi lệnh (khuôn E2E command-log). Lượt repin gọi thẳng `doRepinEscapedCastApps` (hàm `internal`) hai lần
 * — đúng hai nhịp debounce — thay vì chờ vòng 2 s + trần 4 s của `repinEscapedCastApps`.
 */
class CastSessionPinTest : SimpleCastCoordinatorHarness() {

    private val app = "com.test.app"
    private val profileA = DisplayConfig("1920x720", "0,0,0,0", "320", CastBounds(100, 50, 900, 600))
    private val profileB = DisplayConfig("1920x720", "0,0,0,0", "240", CastBounds(0, 0, 1920, 720))

    private fun castFull(pkg: String = app) {
        coordinator.dispatch(SimpleCastIntent.CastFull(pkg, AppType.NORMAL))
        awaitTrue { (coordinator.state as? SimpleCastState.CastingFull)?.targetPkg == pkg }
    }

    /** App bị kéo khỏi cụm (xoá lịch sử ⇒ shell giả không còn thấy nó trên VD) rồi hai nhịp watchdog. */
    private fun escapeAndRepin() {
        shell.history.clear()
        coordinator.doRepinEscapedCastApps()
        coordinator.doRepinEscapedCastApps()
    }

    /** V-2 — đổi hồ sơ lúc ĐANG CHIẾU: 0 lệnh lúc đổi; repin dùng bản của A; chiếu lại mới ra bản của B. */
    @Test
    fun `repin sau khi doi ho so giua phien dung ban GHIM cua A, khong phai prefs cua B`() {
        prefs.saveDisplayConfig(app, CastProfile.FULL, profileA)
        coordinator.openProjection()
        awaitState<SimpleCastState.Idle>()
        castFull()
        awaitTrue { shell.history.any { it == "wm density 320 -d 1" } }
        assertEquals(profileA, (coordinator.state as SimpleCastState.CastingFull).pinned, "phiên ghim bản của A lúc bắt đầu")

        val mark = shell.history.size
        prefs.saveDisplayConfig(app, CastProfile.FULL, profileB)          // ← lượt áp hồ sơ B vào prefs sống
        Thread.sleep(100)
        assertEquals(mark, shell.history.size, "đổi hồ sơ KHÔNG được chạy lệnh nào lên cụm: ${shell.history.drop(mark)}")

        escapeAndRepin()
        assertTrue(shell.history.any { it == "wm density 320 -d 1" }, "repin phải áp DPI đã ghim 320: ${shell.history}")
        assertTrue(shell.history.any { it.startsWith("am task resize") && it.endsWith(" 100 50 900 600") }, "khung của A: ${shell.history}")
        assertTrue(shell.history.none { it.contains("density 240") }, "DPI của hồ sơ B không được tự nổ lên cụm: ${shell.history}")

        coordinator.dispatch(SimpleCastIntent.Stop())
        awaitState<SimpleCastState.Idle>()
        shell.history.clear()
        castFull()
        awaitTrue { shell.history.any { it == "wm density 240 -d 1" } }
        assertEquals(profileB, (coordinator.state as SimpleCastState.CastingFull).pinned, "chiếu lại ⇒ ghim bản của hồ sơ đang dùng")
    }

    /** V-3 — đang CHIA ĐÔI 30, prefs đổi sang 50: ô phải, −/+, chip DPI, repin đều theo 30; chỉ lượt đổi tỉ lệ tường minh sang 50. */
    @Test
    fun `chia doi giu ti le CUA PHIEN khi ho so doi ti le (sua C2)`() {
        prefs.setSplitRatioLeftPercent(30)
        coordinator.openProjection()
        awaitState<SimpleCastState.Idle>()
        coordinator.dispatch(SimpleCastIntent.CastSlot("com.test.left", ClusterSlotSide.LEFT))
        awaitTrue { (coordinator.state as? SimpleCastState.CastingSplit)?.left != null }
        assertEquals(30, (coordinator.state as SimpleCastState.CastingSplit).leftPercent)

        prefs.setSplitRatioLeftPercent(50)                                 // ← hồ sơ B chia 5:5
        coordinator.dispatch(SimpleCastIntent.CastSlot("com.test.right", ClusterSlotSide.RIGHT))
        awaitTrue { (coordinator.state as? SimpleCastState.CastingSplit)?.right != null }
        assertTrue(shell.history.any { it.startsWith("am task resize") && it.endsWith(" 576 0 1920 720") }, "ô phải ở tỉ lệ PHIÊN 30: ${shell.history}")
        assertEquals(30, (coordinator.state as SimpleCastState.CastingSplit).leftPercent)

        coordinator.resizeActiveSlot(ClusterSlotSide.LEFT, 0, 0, 500, 720)
        awaitTrue { prefs.displayConfigFor("com.test.left", CastProfile.of(ClusterSlotSide.LEFT, 30))?.bounds == CastBounds(0, 0, 500, 720) }
        coordinator.setDensitySplit(200)
        awaitTrue { prefs.displayConfigFor("com.test.right", CastProfile.of(ClusterSlotSide.RIGHT, 30))?.density == "200" }
        assertNull(prefs.displayConfigFor("com.test.left", CastProfile.of(ClusterSlotSide.LEFT, 50)), "không lưu dưới ô nhớ của tỉ lệ không có trên cụm")
        assertNull(prefs.displayConfigFor("com.test.right", CastProfile.of(ClusterSlotSide.RIGHT, 50)))
        val split = coordinator.state as SimpleCastState.CastingSplit
        assertEquals(CastBounds(0, 0, 500, 720), split.left?.pinned?.bounds, "−/+ cập nhật bản ghim")
        assertEquals("200", split.left?.pinned?.density)
        assertEquals("200", split.right?.pinned?.density)

        escapeAndRepin()
        assertTrue(shell.history.none { it.contains(" 960 ") || it.endsWith(" 960 720") }, "repin không được mang ranh giới 50%: ${shell.history}")
        assertTrue(shell.history.any { it.startsWith("am task resize") && it.endsWith(" 0 0 500 720") }, "repin áp khung đã ghim: ${shell.history}")

        coordinator.applySplitRatioLive(50)
        awaitTrue { (coordinator.state as? SimpleCastState.CastingSplit)?.leftPercent == 50 }
        awaitTrue { shell.history.any { it.startsWith("am task resize") && it.endsWith(" 0 0 960 720") } }
    }

    /**
     * Chỉnh tay sau khi đã đổi hồ sơ giữa phiên: lưu cho hồ sơ ĐANG DÙNG (B), nhưng bản ghim chỉ đổi ĐÚNG trường vừa áp —
     * DPI trên cụm vẫn là DPI của phiên, nên lượt repin sau đó không được đổi DPI của cụm (không ai chạm chip DPI).
     */
    @Test
    fun `chinh khung sau khi doi ho so chi cap nhat truong vua ap cua ban ghim`() {
        prefs.saveDisplayConfig(app, CastProfile.FULL, profileA)
        coordinator.openProjection()
        awaitState<SimpleCastState.Idle>()
        castFull()
        prefs.saveDisplayConfig(app, CastProfile.FULL, profileB)          // đổi hồ sơ giữa phiên

        coordinator.resizeActiveTarget(10, 20, 1000, 700)
        awaitTrue { (coordinator.state as? SimpleCastState.CastingFull)?.pinned?.bounds == CastBounds(10, 20, 1000, 700) }
        assertEquals("320", (coordinator.state as SimpleCastState.CastingFull).pinned?.density, "DPI của phiên giữ nguyên")
        assertEquals(profileB.copy(bounds = CastBounds(10, 20, 1000, 700)), prefs.displayConfigFor(app, CastProfile.FULL), "lưu cho hồ sơ ĐANG DÙNG")

        coordinator.setDensity(160)
        awaitTrue { (coordinator.state as? SimpleCastState.CastingFull)?.pinned?.density == "160" }
        escapeAndRepin()
        assertTrue(shell.history.any { it == "wm density 160 -d 1" }, "repin áp DPI vừa chọn: ${shell.history}")
        assertTrue(shell.history.none { it.contains("density 240") }, "không nổ DPI của hồ sơ: ${shell.history}")
    }

    /** Phiên không có bản lưu nào ⇒ bản ghim `null` ⇒ repin chỉ đặt lại app, KHÔNG resize/`wm density` theo prefs mới. */
    @Test
    fun `phien khong co ban luu thi repin khong ap gi theo ho so moi`() {
        coordinator.openProjection()
        awaitState<SimpleCastState.Idle>()
        castFull()
        assertNull((coordinator.state as SimpleCastState.CastingFull).pinned)
        prefs.saveDisplayConfig(app, CastProfile.FULL, profileB)
        escapeAndRepin()
        assertTrue(shell.history.any { it.startsWith("am start") && it.contains(app) }, "repin vẫn đặt lại app: ${shell.history}")
        assertTrue(shell.history.none { it.startsWith("wm density") }, "không có gì ghim ⇒ không đổi DPI: ${shell.history}")
    }

    // ─── closeOrphanProjection (VC-R7) ──────────────────────────────────────────────────────────────────────────

    /** Có VD cụm sống từ tiến trình trước ⇒ reset wm trên ĐÚNG VD đó + đóng projection (18 → 0); trạng thái vẫn Off. */
    @Test
    fun `closeOrphanProjection co VD thi reset wm va dong projection tren dung VD`() {
        coordinator.closeOrphanProjection()
        awaitTrue { shell.history.any { it.contains("AutoContainer") && it.contains("i32 0 ") } }
        val h = shell.history.toList()
        assertTrue(h.contains("wm size reset -d 1") && h.contains("wm density reset -d 1"), "hoàn tác wm trên VD dò live: $h")
        val close18 = h.indexOfFirst { it.contains("AutoContainer") && it.contains("i32 18 ") }
        val reset = h.indexOf("wm size reset -d 1")
        assertTrue(reset in 0 until close18, "reset wm TRƯỚC khi đóng (VD còn): $h")
        assertTrue(h.none { it.startsWith("am start") }, "không mở/đặt app nào: $h")
        assertEquals(SimpleCastState.Off, coordinator.state)
    }

    /** Không dò thấy VD ⇒ 0 lệnh GHI (chỉ lệnh dò), không rơi về seed. */
    @Test
    fun `closeOrphanProjection khong co VD thi khong mot lenh ghi nao`() {
        shell.clusterDetectOut = ""
        coordinator.closeOrphanProjection()
        awaitTrue { shell.history.contains(ClusterDisplayResolver.DETECT_CMD) }
        Thread.sleep(100)
        assertTrue(shell.history.all { it == ClusterDisplayResolver.DETECT_CMD }, "chỉ được dò: ${shell.history}")
    }

    /** Đã có phiên mới (người lái vừa BẬT lại) ⇒ không đóng chồng lên phiên đó. */
    @Test
    fun `closeOrphanProjection bo qua khi da co phien moi`() {
        coordinator.openProjection()
        awaitState<SimpleCastState.Idle>()
        val mark = shell.history.size
        coordinator.closeOrphanProjection()
        Thread.sleep(150)
        assertEquals(mark, shell.history.size, "không lệnh nào: ${shell.history.drop(mark)}")
        assertEquals(SimpleCastState.Idle, coordinator.state)
    }
}

/**
 * `applyPinned` kẹp khung vào khung logic ĐO ĐƯỢC của VD (ưu tiên *Override*) — một bản ghi lưu cho cụm lớn hơn (tệp nhập
 * từ xe khác) không đẩy task ra ngoài màn; và `null` ⇒ 0 lệnh.
 */
class CastApplyPinnedTest {

    private class Shell(private val wmSize: String) : SimpleCastShell {
        val history = mutableListOf<String>()
        override fun execute(command: String): ShellResult {
            history += command
            val out = when {
                command == "am stack list" -> "Stack id=2 bounds=[0,0][1280,480] displayId=2 userId=0\n  taskId=100: vn.vietmap.live/.Main visible=true\n"
                command.startsWith("wm size -d") -> wmSize
                else -> ""
            }
            return ShellResult(0, out, "")
        }
    }

    @Test
    fun `khung vuot khung do duoc bi kep, uu tien Override`() {
        val shell = Shell("Physical size: 1920x720\nOverride size: 1280x480")
        CastGeometryController(shell, FakePrefs(), { 2 }) {}
            .applyPinned("vn.vietmap.live", DisplayConfig("1920x720", "0,0,0,0", "reset", CastBounds(100, 0, 1920, 720)))
        assertTrue(shell.history.contains("am task resize 100 100 0 1280 480"), "kẹp theo 1280×480: ${shell.history}")
        assertTrue(shell.history.none { it.startsWith("wm density") }, "density reset ⇒ không chạm DPI")
    }

    @Test
    fun `ban ghim null thi khong mot lenh nao`() {
        val shell = Shell("Physical size: 1920x720")
        CastGeometryController(shell, FakePrefs(), { 2 }) {}.applyPinned("vn.vietmap.live", null)
        assertTrue(shell.history.isEmpty(), "${shell.history}")
    }
}
