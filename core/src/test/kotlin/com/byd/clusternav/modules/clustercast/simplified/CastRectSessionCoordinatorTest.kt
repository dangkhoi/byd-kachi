package com.byd.clusternav.modules.clustercast.simplified

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * B1b · CLUSTER-RECT-OPTION — đường nối coordinator: kiểu GHIM theo phiên (R5) · khung `__RECT` + đọc lại (R7, F4b) · đầu vào
 * của lớp km/h (R8).
 *
 * Xe giả = Seal 10.25" (`car.type=138` ⇒ kiểu gốc Chữ nhật — [ĐO 14/09 + 29/09] getprop; [ĐO-gv 05/10] nổ máy ⇒ theme2 gốc).
 * FakeShell in khung THẬT của task (`reportTaskBounds`, dạng dump `carlog-kachi-20260914-2044/10-am-stack-list.txt`) và có thể
 * "nuốt" một lệnh resize (exit 0 mà WM không áp) — đúng ca [ĐO-gv 05/10] app mở freeform lên màn ảo cụm ra cửa sổ dọc.
 */
class CastRectSessionCoordinatorTest {

    private lateinit var shell: FakeShell
    private lateinit var prefs: FakePrefs
    private lateinit var coordinator: SimpleCastCoordinator
    @Volatile private var chosen = CastStyle.RECT

    private val seal138 = ProjectionRecipe.SEAL_DL3.copy(nativeStyle = CastStyle.RECT)
    private val app = "vn.vietmap.live"
    private val frame = "50 128 1285 555"

    @BeforeEach
    fun setup() {
        shell = FakeShell().apply { vdAbsentUntilCast = true; reportTaskBounds = true }
        prefs = FakePrefs()
        coordinator = SimpleCastCoordinator(
            ProjectionManager(shell, sleepMs = {}, recipe = seal138),
            DisplayConfigurator(shell), AppMover(shell, sleepMs = {}), prefs, shell,
            displayId = 1, detectSleepMs = {}, desiredStyle = { chosen },
        )
    }

    private fun ops(from: Int = 0) =
        shell.history.drop(from).filter { it.contains(" 2 i32 1000 i32 ") }.map { it.substringAfter("i32 1000 i32 ").substringBefore(" ").toInt() }

    private fun resizesTo(bounds: String, from: Int = 0) = shell.history.drop(from).count { it.startsWith("am task resize ") && it.endsWith(" $bounds") }

    private fun openRect() {
        coordinator.openProjection()
        awaitState<SimpleCastState.Idle>()
    }

    private fun castFull(): Int {
        val mark = shell.history.size
        coordinator.dispatch(SimpleCastIntent.CastFull(app, AppType.NORMAL))
        awaitState<SimpleCastState.CastingFull>()
        awaitTrue { shell.history.drop(mark).any { it.startsWith("am task resize ") && it.endsWith(" $frame") } }
        Thread.sleep(150)   // lượt đọc lại chạy ngay sau lệnh khung, cùng executor
        return mark
    }

    @Test
    fun `mo chieu Chu nhat lan dau sau no may - 31 16 35, phien ghim RECT`() {
        openRect()
        assertEquals(listOf(31, 16, 35), ops(), "kiểu gốc Chữ nhật, sổ trống ⇒ ép 31 ở mức A (vô hại, cùng điều kiện F2)")
        assertEquals(CastSessionStyle(CastStyle.RECT, BelievedStyle.RECT, CastStyle.RECT), coordinator.castSession)
        assertTrue(SpeedReadoutPolicy.visible(coordinator.speedReadoutInputs()), "Idle + Chữ nhật ⇒ số km/h hiện")
        assertEquals(1, coordinator.speedReadoutInputs().liveDisplayId, "lớp km/h nhắm đúng id cụm đã xác minh")
    }

    @Test
    fun `chieu app trong Chu nhat - resize vao vung trong roi doc lai, khop thi khong thu lai`() {
        openRect()
        val mark = castFull()
        assertEquals(1, resizesTo(frame, mark), "đúng một lệnh khung: ${shell.history.drop(mark)}")
        val resize = shell.history.drop(mark).indexOfFirst { it.endsWith(" $frame") }
        assertTrue(shell.history.drop(mark + resize + 1).contains("am stack list"), "phải ĐỌC LẠI sau lệnh khung")
        val st = coordinator.state as SimpleCastState.CastingFull
        assertEquals(ClusterRectLayout.FREE_AREA, st.pinned?.bounds, "bản ghim phiên = khung mặc định D2")
        assertTrue(SpeedReadoutPolicy.visible(coordinator.speedReadoutInputs()))
    }

    @Test
    fun `khung lech - thu lai DUNG MOT lan, khop thi dung`() {
        openRect()
        shell.swallowResizeBounds += frame
        val mark = castFull()
        awaitTrue { resizesTo(frame, mark) >= 2 }
        Thread.sleep(150)
        assertEquals(2, resizesTo(frame, mark), "lệch ⇒ resize lại MỘT lần: ${shell.history.drop(mark)}")
    }

    @Test
    fun `khung van lech sau mot lan thu lai - chi log, khong lap, khong loi`() {
        openRect()
        shell.swallowResizeBounds += listOf(frame, frame, frame)
        val mark = castFull()
        awaitTrue { resizesTo(frame, mark) >= 2 }
        Thread.sleep(200)
        assertEquals(2, resizesTo(frame, mark), "không vòng lặp: ${shell.history.drop(mark)}")
        assertTrue(coordinator.state is SimpleCastState.CastingFull, "khung lệch không phải lỗi chiếu")
    }

    @Test
    fun `doi lua chon giua phien - 0 lenh AutoContainer, phien giu Chu nhat`() {
        openRect()
        val mark = shell.history.size
        chosen = CastStyle.CURVED                         // người lái chạm "Bo tròn" khi đang chiếu
        castFull()
        assertEquals(emptyList<Int>(), ops(mark), "đổi kiểu giữa phiên không được gửi lệnh nào tới cụm")
        assertEquals(CastStyle.RECT, coordinator.castSession?.frame, "kiểu GHIM theo phiên (đọc một lần lúc mở)")
        assertEquals(1, resizesTo(frame, mark))
    }

    @Test
    fun `phien Bo tron - khong khung Chu nhat, khong so km-h`() {
        chosen = CastStyle.CURVED
        openRect()
        assertEquals(listOf(30, 16, 35), ops(), "Bo tròn trên xe gốc chữ nhật = đúng chuỗi đã chạy từ 08/02")
        val mark = shell.history.size
        coordinator.dispatch(SimpleCastIntent.CastFull(app, AppType.NORMAL))
        awaitState<SimpleCastState.CastingFull>()
        Thread.sleep(150)
        assertEquals(0, resizesTo(frame, mark), "Bo tròn không bao giờ dùng khung Chữ nhật")
        assertFalse(SpeedReadoutPolicy.visible(coordinator.speedReadoutInputs()), "Bo tròn còn km/h gốc ⇒ không vẽ")
    }

    @Test
    fun `chinh tay o Chu nhat luu vao khoa RECT, khong dong vao khoa Bo tron`() {
        openRect()
        castFull()
        coordinator.resizeActiveTarget(60, 140, 1270, 540)
        awaitTrue { prefs.displayConfigFor(app, CastProfile.FULL.inStyle(CastStyle.RECT)) != null }
        assertEquals(CastBounds(60, 140, 1270, 540), prefs.displayConfigFor(app, CastProfile.FULL.inStyle(CastStyle.RECT))?.bounds)
        assertNull(prefs.displayConfigFor(app, CastProfile.FULL), "khoá Bo tròn không bị đè")
        assertTrue("${app}__RECT" in prefs.savedRecordKeys)
    }

    @Test
    fun `chia doi o Chu nhat - nua trai vao nua vung trong`() {
        openRect()
        val mark = shell.history.size
        coordinator.dispatch(SimpleCastIntent.CastSlot(app, ClusterSlotSide.LEFT))
        awaitState<SimpleCastState.CastingSplit>()
        awaitTrue { resizesTo("50 128 667 555", mark) >= 1 }
        val st = coordinator.state as SimpleCastState.CastingSplit
        assertEquals(ClusterRectLayout.slotFrame(ClusterSlotSide.LEFT, 50), st.left?.pinned?.bounds)
    }

    @Test
    fun `mo lai khi man ao con, doi sang Bo tron - KHONG gui theme, phien theo cum (Chu nhat)`() {
        openRect()
        coordinator.closeProjection()
        awaitState<SimpleCastState.Off>()
        assertNull(coordinator.castSession, "tắt chiếu ⇒ không còn phiên")
        assertFalse(SpeedReadoutPolicy.visible(coordinator.speedReadoutInputs()))
        chosen = CastStyle.CURVED
        val mark = shell.history.size
        openRect()                                         // "Áp ngay" = restoreCluster ⇒ lượt mở đi qua cổng
        assertEquals(listOf(16, 35), ops(mark), "màn ảo cụm còn ⇒ VD_PRESENT ⇒ 0 lệnh theme (D4)")
        assertEquals(CastSessionStyle(CastStyle.CURVED, BelievedStyle.RECT, CastStyle.RECT), coordinator.castSession,
            "sổ (cùng tiến trình) nói cụm vẫn Chữ nhật ⇒ khung + km/h theo CỤM, không theo lựa chọn")
        assertTrue(SpeedReadoutPolicy.visible(coordinator.speedReadoutInputs()))
    }

    // ── Review 2.89 Pass 2 · cluster-r1-2/3/4 ─────────────────────────────────────────────────────────────────────────────

    /** `wm size` của Bo tròn (đường lùi cũ / bản V0.36) — thứ không bao giờ được đi vào cụm Chữ nhật. */
    private val scaled = DisplayConfig(wmSize = "1235x427", overscan = "0,0,0,0", density = "200")

    private fun wmSizes(from: Int = 0) = shell.history.drop(from).filter { it.startsWith("wm size ") && !it.startsWith("wm size -d") }

    /**
     * cluster-r1-2 — chia đôi từ Idle ở phiên Chữ nhật KHÔNG đọc khoá Bo tròn: bản FULL Bo tròn có `wmSize` riêng mà lượt chia
     * vẫn chỉ `wm size 1920x720`, ô mang cấu hình 1:1 ⇒ số km/h còn. Thử ĐỎ: trả `handleCastSlot` về
     * `configurator.resolveConfig(...)` (đọc thẳng khoá Bo tròn).
     */
    @Test
    fun `Pass 2 - chia doi o Chu nhat khong lay wm size cua ban ghi Bo tron`() {
        prefs.saveDisplayConfig(app, CastProfile.FULL, scaled)
        openRect()
        val mark = shell.history.size
        coordinator.dispatch(SimpleCastIntent.CastSlot(app, ClusterSlotSide.LEFT))
        awaitState<SimpleCastState.CastingSplit>()
        awaitTrue { resizesTo("50 128 667 555", mark) >= 1 }
        assertTrue(wmSizes(mark).all { it == "wm size 1920x720 -d 1" }, "chỉ wm size 1:1: ${wmSizes(mark)}")
        val st = coordinator.state as SimpleCastState.CastingSplit
        assertTrue(SpeedReadoutPolicy.oneToOne(st.left!!.displayConfig), "ô mang cấu hình 1:1: ${st.left?.displayConfig}")
        assertTrue(SpeedReadoutPolicy.visible(coordinator.speedReadoutInputs()), "km/h còn trong phiên chia đôi")
    }

    /** cluster-r1-3 — bản `__RECT` đã bẩn (`wmSize` co) không còn làm vỡ 1:1: ép `wm size`/overscan về mặc định, DPI giữ. */
    @Test
    fun `Pass 2 - ban RECT bi lam ban wm size - van ap 1920x720, giu DPI, km-h con`() {
        prefs.saveDisplayConfig(app, CastProfile.FULL.inStyle(CastStyle.RECT), scaled)
        openRect()
        val mark = castFull()
        assertFalse(wmSizes(mark).any { it.contains("1235x427") }, "không bao giờ áp wm size bẩn: ${wmSizes(mark)}")
        val st = coordinator.state as SimpleCastState.CastingFull
        assertEquals("1920x720", st.displayConfig.wmSize)
        assertEquals("200", st.displayConfig.density, "DPI người lái chọn vẫn giữ")
        assertTrue(SpeedReadoutPolicy.visible(coordinator.speedReadoutInputs()))
    }

    /**
     * cluster-r1-3 — −/+ ở Chữ nhật mà `am task resize` bị từ chối (freeform chưa sống): KHÔNG lùi `wm size`, không lưu gì vào
     * khoá `__RECT`. Thử ĐỎ: bỏ nhánh `style == CastStyle.RECT` trong `CastGeometryController.resizeFull`.
     */
    @Test
    fun `Pass 2 - chinh tay o Chu nhat bi tu choi - khong lui wm size, khong luu`() {
        openRect()
        castFull()
        val mark = shell.history.size
        shell.failCommands += "am task resize"
        coordinator.resizeActiveTarget(60, 140, 1270, 540)
        awaitTrue { shell.history.drop(mark).any { it.startsWith("am task resize ") && it.endsWith(" 60 140 1270 540") } }
        Thread.sleep(150)
        assertEquals(emptyList<String>(), wmSizes(mark), "không đường lùi wm size ở Chữ nhật")
        assertNull(prefs.displayConfigFor(app, CastProfile.FULL.inStyle(CastStyle.RECT)), "hỏng ⇒ không lưu")
        assertTrue(SpeedReadoutPolicy.visible(coordinator.speedReadoutInputs()))
    }

    /**
     * cluster-r1-4 — MỘT lượt dò hụt giữa phiên (shell chớp lúc chiếu ô) không tắt số km/h tới hết phiên: lớp km/h rơi về id ĐÃ
     * XÁC MINH của phiên; tắt chiếu xoá id đó. Thử ĐỎ: trả `speedReadoutInputs` về `liveDisplayId = liveDisplayId`.
     */
    @Test
    fun `Pass 2 - mot luot do hut giua phien - km-h van nham id cua phien, tat chieu thi xoa`() {
        openRect()
        shell.failCommands += ClusterDisplayResolver.DETECT_CMD
        coordinator.dispatch(SimpleCastIntent.CastSlot(app, ClusterSlotSide.LEFT))
        awaitState<SimpleCastState.Error>()
        assertEquals(-1, coordinator.liveDisplayId, "lượt dò hụt ⇒ lệnh đặt vẫn bị chặn (R1)")
        assertEquals(1, coordinator.speedReadoutInputs().liveDisplayId, "lớp km/h giữ id của phiên")
        awaitState<SimpleCastState.Idle>()
        assertTrue(SpeedReadoutPolicy.visible(coordinator.speedReadoutInputs()), "về Idle ⇒ số km/h hiện lại ngay")
        coordinator.closeProjection()
        awaitState<SimpleCastState.Off>()
        assertEquals(-1, coordinator.projection.sessionDisplayId)
        assertFalse(SpeedReadoutPolicy.visible(coordinator.speedReadoutInputs()))
    }

    // ── Review 2.89 Pass 3 · cluster-r2-2 / r2-3 ──────────────────────────────────────────────────────────────────────────

    private fun stopCarPlay(): Int {
        coordinator.dispatch(SimpleCastIntent.CastFull("com.byd.autolink.carplay", AppType.CARPLAY))
        awaitState<SimpleCastState.CastingFull>()
        assertFalse(SpeedReadoutPolicy.visible(coordinator.speedReadoutInputs()), "D5: CarPlay trên cụm ⇒ không vẽ km/h")
        val mark = shell.history.size
        coordinator.dispatch(SimpleCastIntent.Stop())
        awaitState<SimpleCastState.Idle>()
        return mark
    }

    /**
     * cluster-r2-2 — dừng CarPlay ở phiên Chữ nhật: màn ảo cụm về lại 1920×720 / overscan 0 SAU bước trả DPI ⇒ Idle 1:1 ⇒ km/h
     * hiện lại (kích đo được 1920×720). Thử ĐỎ: bỏ nhánh `frameStyle == CastStyle.RECT` trong `handleStop`.
     */
    @Test
    fun `Pass 3 - dung CarPlay o Chu nhat - man ao ve 1920x720 overscan 0, km-h hien lai`() {
        openRect()
        val mark = stopCarPlay()
        val after = shell.history.drop(mark)
        val reset = after.indexOf("wm density reset -d 1")
        assertTrue(reset >= 0, "bước trả DPI cũ giữ nguyên: $after")
        assertTrue(after.indexOf("wm size 1920x720 -d 1") > reset, "wm size 1:1 SAU bước trả DPI: $after")
        assertTrue(after.indexOf("wm overscan 0,0,0,0 -d 1") > reset, "overscan 0 SAU bước trả DPI: $after")
        assertTrue(SpeedReadoutPolicy.visible(coordinator.speedReadoutInputs(), 1920 to 720), "Idle 1:1 ⇒ km/h hiện lại")
    }

    /** cluster-r2-2 — Bo tròn: dừng CarPlay y nguyên đường cũ (CLAUDE.md §6) — không lệnh `wm size` / overscan nào. */
    @Test
    fun `Pass 3 - dung CarPlay o Bo tron - khong them lenh wm size hay overscan`() {
        chosen = CastStyle.CURVED
        openRect()
        val mark = stopCarPlay()
        val after = shell.history.drop(mark)
        assertTrue("wm density reset -d 1" in after, after.toString())
        assertTrue(after.none { it.startsWith("wm size ") || it.startsWith("wm overscan ") }, "Bo tròn không thêm lệnh: $after")
    }

    /**
     * cluster-r2-3 — sổ `31;ok` từ tiến trình TRƯỚC (Kachi khởi động lại giữa một lần nổ máy, màn ảo cụm còn), lựa chọn nay Bo tròn:
     * cụm vẫn theme2 FULL [ĐO-gv F3: lần nổ máy mới cũng về chữ nhật gốc] ⇒ phiên tin RECT, khung RECT, km/h hiện. Thử ĐỎ: trả
     * `ThemeLedger.believed` về luật "chỉ cùng tiến trình".
     */
    @Test
    fun `Pass 3 - so 31 ok tu tien trinh truoc, chon Bo tron, man ao con - phien tin Chu nhat, km-h hien`() {
        val prev = ThemeLedger.InMemory(ThemeLedger.encode(ThemeLedger.Entry(31, ThemeLedger.State.OK, 100_000, 4)))
        val clock = ThemeLedger.Clock { ThemeLedger.Now(elapsedMs = 500_000, boot = 4, processStartMs = 400_000) }
        chosen = CastStyle.CURVED
        coordinator = SimpleCastCoordinator(
            ProjectionManager(shell, sleepMs = {}, recipe = seal138),
            DisplayConfigurator(shell), AppMover(shell, sleepMs = {}), prefs, shell,
            displayId = 1, detectSleepMs = {}, desiredStyle = { chosen }, themeLedger = prev, themeClock = clock,
        )
        shell.vdAbsentUntilCast = false                    // màn ảo cụm còn từ tiến trình trước ⇒ VD_PRESENT
        openRect()
        assertEquals(listOf(16, 35), ops(), "màn ảo còn ⇒ 0 lệnh theme")
        assertEquals(CastSessionStyle(CastStyle.CURVED, BelievedStyle.RECT, CastStyle.RECT), coordinator.castSession)
        assertTrue(SpeedReadoutPolicy.visible(coordinator.speedReadoutInputs()), "cụm Chữ nhật không km/h gốc ⇒ Kachi vẽ")
    }

    private inline fun <reified T : SimpleCastState> awaitState(timeoutMs: Long = 6000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (coordinator.state !is T && System.currentTimeMillis() < deadline) Thread.sleep(10)
        assertTrue(coordinator.state is T, "Expected ${T::class.simpleName} but got ${coordinator.state}; history=${shell.history}")
    }

    private fun awaitTrue(timeoutMs: Long = 4000, cond: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!cond() && System.currentTimeMillis() < deadline) Thread.sleep(10)
        assertTrue(cond(), "điều kiện không đạt; history=${shell.history}")
    }
}
