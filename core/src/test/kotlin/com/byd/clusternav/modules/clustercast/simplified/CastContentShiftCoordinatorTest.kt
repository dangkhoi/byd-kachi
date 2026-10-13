package com.byd.clusternav.modules.clustercast.simplified

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 3.1 · R6 — "Dịch nội dung" qua coordinator THẬT. Ca nghiệm thu của owner (13/10): *"đang bị ở app Waze, khi chiếu qua cụm nó
 * dư tầm 20px trên top, chứ gmaps hay vietmap chiếu qua cụm ok"* ⇒ Waze −20 lưu RIÊNG cho Waze, chỉ áp khi Waze chiếu toàn cụm,
 * chuyển sang VietMap thì VietMap mang giá trị của chính nó (0) và overscan về 0; Dừng/khởi động lại/đổi hồ sơ đều trả về 0.
 *
 * Shell giả mô phỏng hai KIỂU app bằng phép đo, không phải bằng tên trong mã sản phẩm: gói [MOVER] có cửa sổ dời theo overscan
 * (khung ăn TOP âm), gói [STILL] là activity thường (khung đứng yên — đúng như Maps/Settings trên máy ảo).
 */
class CastContentShiftCoordinatorTest : SimpleCastCoordinatorHarness() {

    private var top = 0

    @BeforeEach
    fun withOverscanRecipe() {
        // Đời xe CÓ `wm overscan` (DiLink 3, API 29) — mặc định của harness (SEAL_DL3) là TẮT.
        val projection = ProjectionManager(shell, sleepMs = {}, recipe = ProjectionRecipe.SEAL_DL3.copy(supportsOverscan = true))
        coordinator = SimpleCastCoordinator(projection, DisplayConfigurator(shell), AppMover(shell, sleepMs = {}), prefs, shell, displayId = 1)
        shell.responders += { cmd ->
            Regex("""^wm overscan (-?\d+),(-?\d+),(-?\d+),(-?\d+) -d 1$""").find(cmd)?.let { top = it.groupValues[2].toInt() }
            if (cmd == "wm overscan reset -d 1") top = 0
            when (cmd) {
                CastContentShift.DISPLAYS_CMD -> ShellResult(0, "Display: mDisplayId=1\n    mDisplayInfoOverscan=[0,$top][0,0]\n", "")
                CastContentShift.WINDOWS_CMD -> ShellResult(0, window(MOVER, if (top < 0) top else 0) + window(STILL, 0), "")
                else -> null
            }
        }
    }

    private fun window(pkg: String, frameTop: Int) = """
        |  Window #1 Window{1 u0 $pkg/$pkg.MainActivity}:
        |    mDisplayId=1 stackId=2 mSession=Session{0 0:u0a1} mClient=x
        |    mAttrs={(0,0)(fillxfill) ty=BASE_APPLICATION
        |    mFrame=[0,$frameTop][1920,720] last=[0,$frameTop][1920,720]
        |    Cur insets: overscan=[0,${maxOf(top, 0)}][0,0] content=[0,0][0,0]
        |""".trimMargin() + "\n"

    private fun castFull(pkg: String) {
        coordinator.dispatch(SimpleCastIntent.CastFull(pkg, AppType.NORMAL))
        awaitTrue(5_000) { (coordinator.state as? SimpleCastState.CastingFull)?.targetPkg == pkg && coordinator.executor.isIdle }
    }

    private fun shift(pkg: String, px: Int): CastShiftResult {
        val outcomes = CopyOnWriteArrayList<CastShiftResult>()
        val off = coordinator.shiftQueue.addListener { _, r -> outcomes += r }
        coordinator.shiftContent(pkg, px)
        awaitTrue(5_000) { outcomes.isNotEmpty() && coordinator.executor.isIdle }
        off()
        return outcomes.single()
    }

    private fun overscans(from: Int = 0) = shell.history.drop(from).filter { it.startsWith("wm overscan") }

    @Test
    fun `Waze -20 luu rieng cho Waze, chuyen VietMap thi ve 0, quay lai Waze ap -20 CUOI CUNG, Dung tra 0`() {
        coordinator.openProjection()
        awaitState<SimpleCastState.Idle>()
        castFull(MOVER)
        assertEquals(CastShiftResult.APPLIED, shift(MOVER, -20))
        assertEquals(-20, top)
        assertEquals("0,-20,0,0", prefs.displayConfigFor(MOVER, CastProfile.FULL)?.overscan, "lưu vào trường overscan sẵn có")
        assertEquals(true, prefs.contentShiftVerdict(MOVER))
        assertEquals("0,-20,0,0", (coordinator.state as SimpleCastState.CastingFull).pinned?.overscan)

        // Chuyển sang app khác: giá trị của CHÍNH nó (chưa lưu ⇒ 0), overscan cụm về 0, không có lệnh −20 nào.
        val mark = shell.history.size
        castFull(STILL)
        assertEquals(0, top)
        assertTrue(overscans(mark).contains("wm overscan 0,0,0,0 -d 1"), "${overscans(mark)}")
        assertFalse(overscans(mark).any { it.contains(",-20,") })
        assertNull(prefs.displayConfigFor(STILL, CastProfile.FULL))

        // Quay lại Waze: −20 áp lại SAU khung + DPI (đường mới xuống cuối — CLAUDE.md §6) và tự đo lại.
        val mark2 = shell.history.size
        castFull(MOVER)
        awaitTrue(5_000) { top == -20 }
        val h = shell.history.drop(mark2)
        val shiftAt = h.indexOf("wm overscan 0,-20,0,0 -d 1")
        assertTrue(shiftAt > h.indexOfLast { it.startsWith("wm density") || it.startsWith("am task resize") }, "$h")
        assertFalse(h.take(shiftAt).any { it.startsWith("wm overscan") && it.contains(",-20,") }, "lượt áp đầu KHÔNG mang độ dịch: $h")

        coordinator.dispatch(SimpleCastIntent.Stop())
        awaitTrue(5_000) { coordinator.state is SimpleCastState.Idle && coordinator.executor.isIdle }
        assertEquals(0, top, "Dừng chiếu ⇒ TOP overscan về 0")
    }

    @Test
    fun `app khong doi - tra ngay, danh dau khong nhan, khong luu gia tri`() {
        coordinator.openProjection()
        awaitState<SimpleCastState.Idle>()
        castFull(STILL)
        val mark = shell.history.size
        assertEquals(CastShiftResult.INEFFECTIVE, shift(STILL, -20))
        assertEquals(listOf("wm overscan 0,-20,0,0 -d 1", "wm overscan 0,0,0,0 -d 1"), overscans(mark))
        assertEquals(false, prefs.contentShiftVerdict(STILL))
        assertNull(prefs.displayConfigFor(STILL, CastProfile.FULL)?.overscan?.takeIf { it != "0,0,0,0" })
    }

    @Test
    fun `doi hoi so giua phien khong cham cum - lan chieu sau theo ho so moi (0)`() {
        coordinator.openProjection()
        awaitState<SimpleCastState.Idle>()
        castFull(MOVER)
        shift(MOVER, -20)
        val mark = shell.history.size
        // Đổi hồ sơ = prefs đổi (ảnh chụp hồ sơ mới: Waze chưa dịch). Cụm KHÔNG đổi theo cú chạm (VC-R5).
        prefs.saveDisplayConfig(MOVER, CastProfile.FULL, DisplayConfig.NORMAL_DEFAULT.copy(bounds = null))
        Thread.sleep(100)
        assertTrue(overscans(mark).isEmpty())
        coordinator.dispatch(SimpleCastIntent.Stop())
        awaitTrue(5_000) { coordinator.state is SimpleCastState.Idle && coordinator.executor.isIdle }
        castFull(MOVER)
        assertEquals(0, top)
        assertFalse(overscans(mark).any { it.contains(",-20,") }, "${overscans(mark)}")
    }

    @Test
    fun `chieu lai app DANG tren cum (nhanh nhan lai) sau doi ho so ve 0 - cum ve 0, khong do-tra`() {
        coordinator.openProjection()
        awaitState<SimpleCastState.Idle>()
        castFull(MOVER)
        shift(MOVER, -20)
        prefs.saveDisplayConfig(MOVER, CastProfile.FULL, DisplayConfig.NORMAL_DEFAULT.copy(bounds = null))
        val mark = shell.history.size
        coordinator.dispatch(SimpleCastIntent.CastFull(MOVER, AppType.NORMAL))
        awaitTrue(5_000) { top == 0 && coordinator.executor.isIdle }
        assertEquals(listOf("wm overscan 0,0,0,0 -d 1"), overscans(mark).filter { it.contains(",0,0,0") || it.contains(",-") })
    }

    @Test
    fun `tien trinh chet giua luc dich - luot mo chieu ke tiep tra overscan ve 0`() {
        top = -20   // cụm còn mang độ dịch (display_settings.xml sống qua reboot)
        coordinator.openProjection()
        awaitState<SimpleCastState.Idle>()
        assertTrue(overscans().contains("wm overscan 0,0,0,0 -d 1"), "${overscans()}")
        assertEquals(0, top)
    }

    @Test
    fun `doi xe khong co wm overscan - bo, 0 lenh`() {
        setup()   // harness gốc: SEAL_DL3 (supportsOverscan = false)
        coordinator.openProjection()
        awaitState<SimpleCastState.Idle>()
        castFull(MOVER)
        val mark = shell.history.size
        assertEquals(CastShiftResult.DROPPED, shift(MOVER, -20))
        assertTrue(overscans(mark).isEmpty())
        assertFalse(coordinator.contentShiftSupported)
    }

    @Test
    fun `chuoi bam -5 x4 khi executor ban ra MOT lan ap mang gia tri cuoi`() {
        coordinator.openProjection()
        awaitState<SimpleCastState.Idle>()
        castFull(MOVER)
        val gate = java.util.concurrent.CountDownLatch(1)
        coordinator.executor.submit("busy") { gate.await(2, java.util.concurrent.TimeUnit.SECONDS) }
        val outcomes = CopyOnWriteArrayList<CastShiftResult>()
        coordinator.shiftQueue.addListener { _, r -> outcomes += r }
        val mark = shell.history.size
        listOf(-5, -10, -15, -20).forEach { coordinator.shiftContent(MOVER, it) }
        gate.countDown()
        awaitTrue(5_000) { outcomes.isNotEmpty() && coordinator.executor.isIdle }
        assertEquals(listOf("wm overscan 0,-20,0,0 -d 1"), overscans(mark))
        assertEquals(listOf(CastShiftResult.APPLIED), outcomes)
    }

    private companion object {
        const val MOVER = "com.waze"
        const val STILL = "vn.vietmap.live"
    }
}
