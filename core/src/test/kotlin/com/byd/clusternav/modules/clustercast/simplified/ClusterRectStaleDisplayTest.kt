package com.byd.clusternav.modules.clustercast.simplified

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 2.90 · R1 + R5 — mức B trên Seal và id màn ảo cụm SAU khi theme đổi.
 *
 * [ĐO xe 06/10, `docs/diagnostics/oncar-2026-10-06-cluster-rect.md`]:
 *  • F1 màn ảo cụm có từ lúc đầu máy khởi động (display 4);
 *  • F4 0 task + 0 cửa sổ ⇒ `31` không sập, màn ảo dựng lại thành display **9**;
 *  • F7 bước đọc lại khung vẫn hỏi display cũ: `wm size -d 4` → `Physical size: 0x0` ⇒ "đọc được ?" ⇒ "VẪN lệch".
 * Bài khoá: sau khi màn ảo dời 4 → 9, KHÔNG lệnh nào nhắm `-d 4` / `--display 4`.
 */
class ClusterRectStaleDisplayTest {

    private val app = "com.google.android.apps.maps"
    private val sealB = ProjectionRecipe.SEAL_DL3.copy(nativeStyle = CastStyle.RECT, themeOnVacantVd = true)

    private fun coordinator(shell: FakeShell, prefs: FakePrefs = FakePrefs(), recipe: ProjectionRecipe = sealB) =
        SimpleCastCoordinator(
            ProjectionManager(shell, sleepMs = {}, recipe = recipe),
            DisplayConfigurator(shell), AppMover(shell, sleepMs = {}), prefs, shell,
            displayId = 4, detectSleepMs = {}, desiredStyle = { CastStyle.RECT },
        )

    private fun targets4(h: List<String>) = h.filter { it.contains("-d 4") || it.contains("--display 4 ") }

    private fun ops(h: List<String>) =
        h.filter { it.contains(" 2 i32 1000 i32 ") }.map { it.substringAfter("i32 1000 i32 ").substringBefore(" ").toInt() }

    private fun seal(): FakeShell = FakeShell().apply {
        clusterDisplayId = 4
        vdAfterTheme = 9
        reportWmSize = true
        reportTaskBounds = true
    }

    /**
     * Mức B: màn ảo 4 có sẵn và trống ⇒ gửi 31, màn ảo dời sang 9; bản dò đầu sau khi gửi còn thấy CẢ 4 (đứng trước) lẫn 9. Mã cũ
     * nhận 4 (lượt dò lấy màn đầu tiên) ⇒ ClusterBlack `--display 4`, khung `wm size -d 4` → 0x0. Thử ĐỎ: bỏ `exclude` trong
     * `openProjectionBody` VÀ bỏ `newestSameName` trong `ClusterDisplayResolver.resolve`.
     */
    @Test
    fun `muc B - gui 31 tren man ao co san, man ao doi 4 sang 9 - moi lenh sau do nham 9`() {
        val shell = seal().apply { lingerOldVdReads = 2 }
        val c = coordinator(shell)
        c.openProjection()
        awaitState<SimpleCastState.Idle>(c, shell)
        assertEquals(listOf(31, 16, 35), ops(shell.history), "màn ảo có sẵn mà trống ⇒ mức B gửi 31: ${shell.history}")
        assertEquals(9, c.liveDisplayId, "dò id MỚI sau khi gửi theme")
        assertEquals(CastSessionStyle(CastStyle.RECT, BelievedStyle.RECT, CastStyle.RECT), c.castSession, "theme đã gửi ⇒ kiểu biết")
        val sent = shell.history.indexOfFirst { it.contains(" i32 1000 i32 31 ") }
        assertEquals(emptyList<String>(), targets4(shell.history.drop(sent)), "sau khi gửi theme không lệnh nào nhắm display 4")
        assertTrue(shell.history.any { it.startsWith("am start --display 9 ") && it.contains("ClusterBlackActivity") })

        val mark = shell.history.size
        c.dispatch(SimpleCastIntent.CastFull(app, AppType.NORMAL))
        awaitState<SimpleCastState.CastingFull>(c, shell)
        Thread.sleep(200)
        val after = shell.history.drop(mark)
        assertEquals(emptyList<String>(), targets4(after), "đường đặt + đọc lại khung không hỏi display cũ: $after")
        assertTrue("wm size -d 9" in after, "đo kích trên id sống: $after")
    }

    /**
     * Dò khi `dumpsys display` còn liệt kê màn ảo cụm cũ CÙNG TÊN đứng trước màn mới: lấy id LỚN nhất (AOSP cấp id tăng dần,
     * không tái dùng). Thử ĐỎ: trả `resolve` về `DisplayParse.clusterDisplayId` trơn.
     */
    @Test
    fun `dumpsys con ca 4 va 9 cung ten - chon 9, cast khong nham 4`() {
        val shell = seal().apply { vdAfterTheme = null; clusterDisplayId = 9 }
        shell.clusterDetectOut = shell.detectOutFor(listOf(4, 9))
        val c = coordinator(shell, recipe = ProjectionRecipe.SEAL_DL3)
        c.openProjection()
        awaitState<SimpleCastState.Idle>(c, shell)
        c.dispatch(SimpleCastIntent.CastFull(app, AppType.NORMAL))
        awaitState<SimpleCastState.CastingFull>(c, shell)
        assertEquals(9, c.liveDisplayId)
        assertEquals(emptyList<String>(), targets4(shell.history), "không lệnh nào nhắm id cũ: ${shell.history}")
    }

    /** Tên KHÁC nhau (DL5 `…_0/_1`) ⇒ giữ đúng thứ tự cũ: màn đầu tiên (CLAUDE.md §6). */
    @Test
    fun `nhieu man fission khac ten - giu man dau tien nhu cu`() {
        val out = """
            |  Display 0:
            |  Display 3:
            |    mPrimaryDisplayDevice=fission_bg_XDJAScreenProjection_0
            |    mBaseDisplayInfo=DisplayInfo{"fission_bg_XDJAScreenProjection_0, displayId 3", uniqueId "virtual:x,1000,a,0"}
            |  Display 5:
            |    mPrimaryDisplayDevice=fission_bg_XDJAScreenProjection_1
            |    mBaseDisplayInfo=DisplayInfo{"fission_bg_XDJAScreenProjection_1, displayId 5", uniqueId "virtual:x,1000,b,0"}
        """.trimMargin()
        assertEquals(3, ClusterDisplayResolver.resolve(out, "com.byd.launcher"))
    }

    /**
     * Tầng thi hành: id hiện tại đã chết (`wm size -d 4` → `0x0`, AOSP `WindowManagerService.getInitialDisplaySize`) ⇒ dò lại MỘT
     * lần rồi đo + tra task + resize trên id mới. Thử ĐỎ: bỏ nhánh `displayGone` trong `CastGeometryController.queryDisplaySize`.
     */
    @Test
    fun `wm size -d id cu tra 0x0 - do lai id, resize tren man moi`() {
        val shell = seal().apply { vdAfterTheme = null; clusterDisplayId = 9 }
        var live = 4
        val g = CastGeometryController(shell, FakePrefs(), { live }, redetect = { live = 9; 9 }) {}
        shell.execute("am start --display 9 --windowingMode 5 -n '$app/.MainActivity'")
        val asked = g.applyPinned(app, DisplayConfig.NORMAL_DEFAULT.copy(bounds = CastBounds(0, 0, 1920, 720)))
        assertEquals(CastBounds(0, 0, 1920, 720), asked)
        val h = shell.history
        val gone = h.indexOf("wm size -d 4")
        assertTrue(gone >= 0 && h.indexOf("wm size -d 9") > gone, "đo lại trên id mới: $h")
        assertTrue(h.drop(gone + 1).none { it.contains("-d 4") }, "sau khi biết display 4 đã mất không hỏi lại nó: $h")
        assertTrue(g.verifyFrame(app, CastBounds(0, 0, 1920, 720)), "đọc lại khung trên id mới khớp: $h")
    }

    /** Ca F7 NGUYÊN DẠNG: hết lượt chờ mà màn ảo KHÔNG dựng lại (chỉ thấy id cũ) ⇒ nhận id cũ, không đóng chiếu (OQ1). */
    @Test
    fun `gui theme ma man ao khong dung lai - nhan id cu, khong dong chieu`() {
        val shell = seal().apply { vdAfterTheme = 4 }
        val c = coordinator(shell)
        c.openProjection()
        awaitState<SimpleCastState.Idle>(c, shell)
        assertEquals(4, c.liveDisplayId)
        assertTrue(shell.history.none { it.contains(" i32 1000 i32 18 ") }, "không trả đồng hồ: ${shell.history}")
    }

    private inline fun <reified T : SimpleCastState> awaitState(c: SimpleCastCoordinator, shell: FakeShell, timeoutMs: Long = 8000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (c.state !is T && System.currentTimeMillis() < deadline) Thread.sleep(10)
        assertTrue(c.state is T, "Expected ${T::class.simpleName} but got ${c.state}; history=${shell.history}")
    }
}
