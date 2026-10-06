package com.byd.clusternav.modules.clustercast.simplified

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * B1b · CLUSTER-RECT-OPTION — đường nối coordinator: kiểu GHIM theo phiên (R5) · khung `__RECT` + đọc lại (R7, F4b). 2.90: khung
 * mặc định = trọn cụm (R4), không còn lớp km/h (R3), sổ của tiến trình trước không suy được kiểu (R2). 2.92 · CLUSTER-FRAME-CHOSEN:
 * kiểu chưa rõ ⇒ khung + khoá lưu theo kiểu người lái CHỌN (khung đã lưu được ghim, chỉnh tay lưu đúng ô).
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
    private val frame = "0 0 1920 720"
    /** Khung người lái tự lưu (khoá `__RECT`) — dùng cho các ca đọc-lại lệch (khung trọn cụm trùng khung mặc định của task giả). */
    private val custom = "60 140 1270 540"
    private fun saveCustom() =
        prefs.saveDisplayConfig(app, CastProfile.FULL.inStyle(CastStyle.RECT), DisplayConfig.NORMAL_DEFAULT.copy(bounds = CastBounds(60, 140, 1270, 540)))

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

    private fun castFull(want: String = frame): Int {
        val mark = shell.history.size
        coordinator.dispatch(SimpleCastIntent.CastFull(app, AppType.NORMAL))
        awaitState<SimpleCastState.CastingFull>()
        awaitTrue { shell.history.drop(mark).any { it.startsWith("am task resize ") && it.endsWith(" $want") } }
        Thread.sleep(150)   // lượt đọc lại chạy ngay sau lệnh khung, cùng executor
        return mark
    }

    @Test
    fun `mo chieu Chu nhat lan dau sau no may - 31 16 35, phien ghim RECT`() {
        openRect()
        assertEquals(listOf(31, 16, 35), ops(), "kiểu gốc Chữ nhật, sổ trống ⇒ ép 31 ở mức A (vô hại, cùng điều kiện F2)")
        assertEquals(CastSessionStyle(CastStyle.RECT, BelievedStyle.RECT, CastStyle.RECT), coordinator.castSession)
    }

    @Test
    fun `290 - chieu app trong Chu nhat - mac dinh tron cum roi doc lai, khop thi khong thu lai`() {
        openRect()
        val mark = castFull()
        val st = coordinator.state as SimpleCastState.CastingFull
        assertEquals(ClusterRectLayout.FULL, st.pinned?.bounds, "2.90 · R4: bản ghim phiên = trọn cụm (không vùng chừa)")
        val asked = shell.history.drop(mark).indexOfLast { it.startsWith("am task resize ") && it.endsWith(" $frame") }
        assertTrue(shell.history.drop(mark + asked + 1).contains("am stack list"), "phải ĐỌC LẠI sau lệnh khung")
        assertEquals(0, shell.history.drop(mark).count { it.startsWith("am task resize ") && it.endsWith(" 50 128 1285 555") },
            "không còn khung FREE_AREA của 2.89")
    }

    @Test
    fun `khung lech - thu lai DUNG MOT lan, khop thi dung`() {
        saveCustom()
        openRect()
        shell.swallowResizeBounds += custom
        val mark = castFull(custom)
        awaitTrue { resizesTo(custom, mark) >= 2 }
        Thread.sleep(150)
        assertEquals(2, resizesTo(custom, mark), "lệch ⇒ resize lại MỘT lần: ${shell.history.drop(mark)}")
    }

    @Test
    fun `khung van lech sau mot lan thu lai - chi log, khong lap, khong loi`() {
        saveCustom()
        openRect()
        shell.swallowResizeBounds += listOf(custom, custom, custom)
        val mark = castFull(custom)
        awaitTrue { resizesTo(custom, mark) >= 2 }
        Thread.sleep(200)
        assertEquals(2, resizesTo(custom, mark), "không vòng lặp: ${shell.history.drop(mark)}")
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
        assertTrue(resizesTo(frame, mark) >= 1)
    }

    @Test
    fun `phien Bo tron - khong ghim khung Chu nhat`() {
        saveCustom()
        chosen = CastStyle.CURVED
        openRect()
        assertEquals(listOf(30, 16, 35), ops(), "Bo tròn trên xe gốc chữ nhật = đúng chuỗi đã chạy từ 08/02")
        val mark = shell.history.size
        coordinator.dispatch(SimpleCastIntent.CastFull(app, AppType.NORMAL))
        awaitState<SimpleCastState.CastingFull>()
        Thread.sleep(150)
        assertEquals(0, resizesTo(custom, mark), "Bo tròn không bao giờ đọc khoá Chữ nhật")
        assertNull((coordinator.state as SimpleCastState.CastingFull).pinned, "Bo tròn chưa lưu gì ⇒ không ghim")
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
        awaitTrue { resizesTo("0 0 960 720", mark) >= 1 }
        val st = coordinator.state as SimpleCastState.CastingSplit
        assertEquals(CastBounds(0, 0, 960, 720), st.left?.pinned?.bounds, "2.90 · R4: nửa của trọn cụm")
    }

    @Test
    fun `mo lai khi man ao con, doi sang Bo tron - KHONG gui theme, phien theo cum (Chu nhat)`() {
        openRect()
        coordinator.closeProjection()
        awaitState<SimpleCastState.Off>()
        assertNull(coordinator.castSession, "tắt chiếu ⇒ không còn phiên")
        chosen = CastStyle.CURVED
        val mark = shell.history.size
        openRect()                                         // "Áp ngay" = restoreCluster ⇒ lượt mở đi qua cổng
        assertEquals(listOf(16, 35), ops(mark), "màn ảo cụm còn ⇒ VD_PRESENT ⇒ 0 lệnh theme (D4)")
        assertEquals(CastSessionStyle(CastStyle.CURVED, BelievedStyle.RECT, CastStyle.RECT), coordinator.castSession,
            "sổ (cùng tiến trình) nói cụm vẫn Chữ nhật ⇒ khung theo CỤM, không theo lựa chọn")
    }

    // ── Review 2.89 Pass 2 · cluster-r1-2/3/4 ─────────────────────────────────────────────────────────────────────────────

    /** `wm size` của Bo tròn (đường lùi cũ / bản V0.36) — thứ không bao giờ được đi vào cụm Chữ nhật. */
    private val scaled = DisplayConfig(wmSize = "1235x427", overscan = "0,0,0,0", density = "200")

    private fun wmSizes(from: Int = 0) = shell.history.drop(from).filter { it.startsWith("wm size ") && !it.startsWith("wm size -d") }

    /**
     * cluster-r1-2 — chia đôi từ Idle ở phiên Chữ nhật KHÔNG đọc khoá Bo tròn: bản FULL Bo tròn có `wmSize` riêng mà lượt chia
     * vẫn chỉ `wm size 1920x720`, ô mang cấu hình 1:1. Thử ĐỎ: trả `handleCastSlot` về
     * `configurator.resolveConfig(...)` (đọc thẳng khoá Bo tròn).
     */
    @Test
    fun `Pass 2 - chia doi o Chu nhat khong lay wm size cua ban ghi Bo tron`() {
        prefs.saveDisplayConfig(app, CastProfile.FULL, scaled)
        openRect()
        val mark = shell.history.size
        coordinator.dispatch(SimpleCastIntent.CastSlot(app, ClusterSlotSide.LEFT))
        awaitState<SimpleCastState.CastingSplit>()
        awaitTrue { resizesTo("0 0 960 720", mark) >= 1 }
        assertTrue(wmSizes(mark).all { it == "wm size 1920x720 -d 1" }, "chỉ wm size 1:1: ${wmSizes(mark)}")
        val st = coordinator.state as SimpleCastState.CastingSplit
        assertEquals(DisplayConfig.NORMAL_DEFAULT.wmSize, st.left!!.displayConfig.wmSize, "ô mang cấu hình 1:1: ${st.left?.displayConfig}")
        assertEquals(DisplayConfig.NORMAL_DEFAULT.overscan, st.left!!.displayConfig.overscan)
    }

    /** cluster-r1-3 — bản `__RECT` đã bẩn (`wmSize` co) không còn làm vỡ 1:1: ép `wm size`/overscan về mặc định, DPI giữ. */
    @Test
    fun `Pass 2 - ban RECT bi lam ban wm size - van ap 1920x720, giu DPI`() {
        prefs.saveDisplayConfig(app, CastProfile.FULL.inStyle(CastStyle.RECT), scaled)
        openRect()
        val mark = castFull()
        assertFalse(wmSizes(mark).any { it.contains("1235x427") }, "không bao giờ áp wm size bẩn: ${wmSizes(mark)}")
        val st = coordinator.state as SimpleCastState.CastingFull
        assertEquals("1920x720", st.displayConfig.wmSize)
        assertEquals("200", st.displayConfig.density, "DPI người lái chọn vẫn giữ")
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
    }

    // ── Review 2.89 Pass 3 · cluster-r2-2 / r2-3 ──────────────────────────────────────────────────────────────────────────

    private fun stopCarPlay(): Int {
        coordinator.dispatch(SimpleCastIntent.CastFull("com.byd.autolink.carplay", AppType.CARPLAY))
        awaitState<SimpleCastState.CastingFull>()
        val mark = shell.history.size
        coordinator.dispatch(SimpleCastIntent.Stop())
        awaitState<SimpleCastState.Idle>()
        return mark
    }

    /**
     * cluster-r2-2 — dừng CarPlay ở phiên Chữ nhật: màn ảo cụm về lại 1920×720 / overscan 0 SAU bước trả DPI ⇒ Idle 1:1. Thử ĐỎ: bỏ nhánh `frameStyle == CastStyle.RECT` trong `handleStop`.
     */
    @Test
    fun `Pass 3 - dung CarPlay o Chu nhat - man ao ve 1920x720 overscan 0`() {
        openRect()
        val mark = stopCarPlay()
        val after = shell.history.drop(mark)
        val reset = after.indexOf("wm density reset -d 1")
        assertTrue(reset >= 0, "bước trả DPI cũ giữ nguyên: $after")
        assertTrue(after.indexOf("wm size 1920x720 -d 1") > reset, "wm size 1:1 SAU bước trả DPI: $after")
        assertTrue(after.indexOf("wm overscan 0,0,0,0 -d 1") > reset, "overscan 0 SAU bước trả DPI: $after")
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
     * 2.90 · R2 (gỡ cluster-r2-3 của 2.89) — sổ `31;ok` từ tiến trình TRƯỚC, màn ảo cụm còn: kiểu tin vẫn UNKNOWN (sổ của tiến trình
     * trước không chứng minh được kiểu cụm). 2.92 · CLUSTER-FRAME-CHOSEN — khung thì theo kiểu người lái CHỌN: ghim ĐÚNG khung Chữ
     * nhật đã lưu ([ĐO log xe 06/10 15:13/15:17]: luật cũ "trọn cụm" bỏ khung đã lưu mỗi lần nổ máy). Thử ĐỎ: trả nhánh UNKNOWN của
     * `CastSessionStyle.of` về Bo tròn.
     */
    @Test
    fun `292 - so 31 ok tu tien trinh truoc, man ao con - kieu CHUA RO, khung DA LUU cua kieu chon`() {
        saveCustom()
        openUnknownRect()
        assertEquals(listOf(16, 35), ops(), "màn ảo còn ⇒ 0 lệnh theme")
        assertEquals(CastSessionStyle(CastStyle.RECT, BelievedStyle.UNKNOWN, CastStyle.RECT), coordinator.castSession)
        val mark = castFull(custom)
        assertEquals(CastBounds(60, 140, 1270, 540), (coordinator.state as SimpleCastState.CastingFull).pinned?.bounds,
            "chưa rõ ⇒ khung Chữ nhật ĐÃ LƯU")
        assertTrue(resizesTo(custom, mark) >= 1, "khung đã lưu được áp: ${shell.history.drop(mark)}")
    }

    /**
     * 2.92 · CLUSTER-FRAME-CHOSEN · R2 — chỉnh tay (−/+, chip DPI) trong phiên CHƯA RÕ kiểu lưu vào khoá của kiểu người lái chọn
     * (`__RECT`), không đụng khoá Bo tròn; chiếu lại trong CÙNG phiên ghim đúng khung + DPI vừa chỉnh — vế "không lưu vị trí, kích
     * thước, chiếu qua lại bị loạn" owner tả 06/10 (luật 2.90 lưu vào khoá Bo tròn, lượt Chữ nhật sau không thấy). Thử ĐỎ: trả nhánh
     * UNKNOWN của `CastSessionStyle.of` về Bo tròn.
     */
    @Test
    fun `292 - chinh tay trong phien CHUA RO luu vao khoa kieu chon, chieu lai van dung khung`() {
        openUnknownRect()
        castFull()
        coordinator.resizeActiveTarget(60, 140, 1270, 540)
        awaitTrue { prefs.displayConfigFor(app, CastProfile.FULL.inStyle(CastStyle.RECT))?.bounds == CastBounds(60, 140, 1270, 540) }
        coordinator.setDensity(160)
        awaitTrue { prefs.displayConfigFor(app, CastProfile.FULL.inStyle(CastStyle.RECT))?.density == "160" }
        assertNull(prefs.displayConfigFor(app, CastProfile.FULL), "khoá Bo tròn không bị đè: ${prefs.savedRecordKeys}")
        coordinator.dispatch(SimpleCastIntent.Stop())
        awaitState<SimpleCastState.Idle>()
        val mark = castFull(custom)
        val pinned = (coordinator.state as SimpleCastState.CastingFull).pinned
        assertEquals(CastBounds(60, 140, 1270, 540), pinned?.bounds, "chiếu lại ⇒ khung vừa chỉnh")
        assertEquals("160", pinned?.density, "chiếu lại ⇒ DPI vừa chỉnh")
        assertTrue(shell.history.drop(mark).any { it.startsWith("wm density 160 -d ") }, "DPI vừa chỉnh được áp: ${shell.history.drop(mark)}")
    }

    /**
     * Phiên CHƯA RÕ kiểu, dựng lại từ log xe 06/10 15:13/15:17: sổ `31;ok` của tiến trình TRƯỚC (BYD giết Kachi mỗi lần tắt máy),
     * màn ảo cụm còn ⇒ cổng bỏ theme (VD_PRESENT, SKIP_KNOWN), sổ khác tiến trình không chứng minh được kiểu ⇒ UNKNOWN.
     */
    private fun openUnknownRect() {
        val prev = ThemeLedger.InMemory(ThemeLedger.encode(ThemeLedger.Entry(31, ThemeLedger.State.OK, 100_000, 4)))
        val clock = ThemeLedger.Clock { ThemeLedger.Now(elapsedMs = 500_000, boot = 4, processStartMs = 400_000) }
        coordinator = SimpleCastCoordinator(
            ProjectionManager(shell, sleepMs = {}, recipe = seal138),
            DisplayConfigurator(shell), AppMover(shell, sleepMs = {}), prefs, shell,
            displayId = 1, detectSleepMs = {}, desiredStyle = { chosen }, themeLedger = prev, themeClock = clock,
        )
        shell.vdAbsentUntilCast = false                    // màn ảo cụm còn từ tiến trình trước ⇒ VD_PRESENT
        openRect()
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
