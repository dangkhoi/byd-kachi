package com.byd.clusternav.modules.clustercast.simplified

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 2.90 · R1 — cổng theme mức B trên Seal: màn ảo cụm có TỪ LÚC đầu máy khởi động, bóng nổi VietMap giữ cửa sổ phủ trên nó.
 *
 * Nguồn [ĐO xe 06/10, `docs/diagnostics/oncar-2026-10-06-cluster-rect.md`]: F1 màn ảo cụm `fission_bg_xdjaVirtualSurface` = display 4
 * từ lúc khởi động; F5 bóng nổi mod VietMap giữ 3 cửa sổ `TYPE_APPLICATION_OVERLAY` của `vn.vietmap.live` trên display 4 kể cả khi
 * tắt chiếu; F4 0 task + 0 cửa sổ ⇒ gửi `31` an toàn, màn ảo dựng lại thành display 9.
 * Dạng dòng cửa sổ = dạng `dumpsys window windows | grep -E 'Window #|mDisplayId='` của xe (`Window{<hash> u0 vn.vietmap.live}` —
 * cửa sổ phủ = gói trần, `ClusterThemeWindowsFixtureTest` 14/09); số hash là giả (phiên 06/10 không lưu dump đầy đủ).
 */
class ClusterThemeBubbleTest {

    private val self = "com.byd.launcher"
    private val black = "$self/com.byd.clusternav.modules.clustercast.ClusterBlackActivity"

    private val home = com.byd.clusternav.modules.clustercast.StackParse.parse(
        "Stack id=0 bounds=[0,0][1920,1080] displayId=0 userId=0\n" +
            "  taskId=4: com.android.launcher3/com.android.launcher3.Launcher visible=true",
    )

    /** 3 cửa sổ phủ VietMap trên display 4 + cửa sổ thường ở display 0 (F5). */
    private val vietmapBubbleOn4 = """
        |  Window #0 Window{739f3cf u0 InputMethod}:
        |    mDisplayId=0 stackId=0 mSession=Session{b250230 3982:u0a10062} mClient=android.os.BinderProxy@bbadc2e
        |  Window #1 Window{a4544b6 u0 vn.vietmap.live}:
        |    mDisplayId=4 stackId=0 mSession=Session{93ef6e6 11258:u0a10134} mClient=android.os.BinderProxy@6aec578
        |  Window #2 Window{a4544b7 u0 vn.vietmap.live}:
        |    mDisplayId=4 stackId=0 mSession=Session{93ef6e6 11258:u0a10134} mClient=android.os.BinderProxy@6aec579
        |  Window #3 Window{a4544b8 u0 vn.vietmap.live}:
        |    mDisplayId=4 stackId=0 mSession=Session{93ef6e6 11258:u0a10134} mClient=android.os.BinderProxy@6aec57a
        |  Window #4 Window{3de0c0d u0 com.android.launcher3/com.android.launcher3.Launcher}:
        |    mDisplayId=0 stackId=4 mSession=Session{3ca6afa 2650:1000} mClient=android.os.BinderProxy@38756a4
    """.trimMargin()

    private fun decide(windows: String, tasks: List<com.byd.clusternav.modules.clustercast.StackEntry> = home) =
        ClusterThemePlan.decide(31, setOf(4), 4, null, tasks, ClusterThemePlan.parseWindows(windows), self, themeOnVacantVd = true)

    @Test
    fun `chi con cua so phu cua VietMap tren cum - BUBBLE, ke ten VietMap, khong gui`() {
        val d = decide(vietmapBubbleOn4) as ClusterThemePlan.Decision.Skip
        assertEquals(ClusterThemePlan.Reason.BUBBLE, d.reason, d.detail)
        assertEquals(listOf("VietMap"), d.apps)
        assertEquals(ThemeVerdict.SKIP_KNOWN, ClusterThemePlan.verdict(d, vdBefore = true), "màn ảo có từ trước ⇒ bỏ theme, 16/35 đi tiếp")
    }

    @Test
    fun `cua so phu cua app LA - FOREIGN, khong phai BUBBLE`() {
        val other = vietmapBubbleOn4.replace("u0 vn.vietmap.live}", "u0 com.example.other}")
        val d = decide(other) as ClusterThemePlan.Decision.Skip
        assertEquals(ClusterThemePlan.Reason.FOREIGN, d.reason)
        assertEquals(emptyList<String>(), d.apps)
    }

    @Test
    fun `VietMap co TASK tren cum - FOREIGN (khong phai chi bong noi)`() {
        val tasks = home + com.byd.clusternav.modules.clustercast.StackParse.parse(
            "Stack id=7 bounds=[0,0][1920,720] displayId=4 userId=0\n  taskId=70: vn.vietmap.live/vn.vietmap.live.MainActivity visible=true",
        )
        val d = decide(vietmapBubbleOn4, tasks) as ClusterThemePlan.Decision.Skip
        assertEquals(ClusterThemePlan.Reason.FOREIGN, d.reason)
    }

    @Test
    fun `0 task 0 cua so tren cum co san tu luc khoi dong - GUI (F4)`() {
        val clean = vietmapBubbleOn4.lines().chunked(2).filterNot { it.getOrNull(1)?.contains("mDisplayId=4") == true }
            .flatten().joinToString("\n")
        assertEquals(ClusterThemePlan.Decision.Send, decide(clean))
    }

    // ── bộ thi hành: gỡ ClusterBlack TRONG tiến trình rồi mới quyết; bóng nổi ⇒ 0 lệnh ghi + lastBlockers ─────────────────

    private val detect4 = """
        |  Display 0:
        |  Display 4:
        |    mPrimaryDisplayDevice=fission_bg_xdjaVirtualSurface
        |    mBaseDisplayInfo=DisplayInfo{"fission_bg_xdjaVirtualSurface, displayId 4", uniqueId "virtual:com.xdja.containerservice,1000,fission_bg_xdjaVirtualSurface,0", app 1920 x 720, real 1920 x 720}
    """.trimMargin()

    private inner class Shell(var stacks: String, var windows: String) : SimpleCastShell {
        val calls = mutableListOf<String>()
        override fun execute(command: String): ShellResult {
            calls += command
            return when (command) {
                ClusterDisplayResolver.DETECT_CMD -> ShellResult(0, detect4, "")
                ClusterThemeGuard.STACK_CMD -> ShellResult(0, stacks, "")
                ClusterThemeGuard.WINDOWS_CMD -> ShellResult(0, windows, "")
                else -> ShellResult(0, "", "")
            }
        }
    }

    private val homeOut = "Stack id=0 bounds=[0,0][1920,1080] displayId=0 userId=0\n" +
        "  taskId=4: com.android.launcher3/com.android.launcher3.Launcher visible=true"
    private val blackOn4 = "\nStack id=5 bounds=[0,0][1920,720] displayId=4 userId=0\n" +
        " configuration={1.0 winConfig={ mWindowingMode=freeform mDisplayWindowingMode=fullscreen mActivityType=standard} s.3}\n" +
        "  taskId=50: $black bounds=[0,0][1920,720] userId=0 visible=true"
    private val baseWindows = "  Window #0 Window{739f3cf u0 InputMethod}:\n    mDisplayId=0 stackId=0 mSession=Session{0 0:u0a1}"
    private val blackWindow = "\n  Window #9 Window{b1 u0 $black}:\n    mDisplayId=4 stackId=5 mSession=Session{0 0:u0a1}"

    @Test
    fun `chi con ClusterBlack cua Kachi - go TRONG tien trinh, doc lai trong roi moi GUI`() {
        val sh = Shell(homeOut + blackOn4, baseWindows + blackWindow)
        val finished = mutableListOf<Set<Int>>()
        val own = ClusterThemeGuard.OwnPlaceholder { ids ->
            finished += ids
            sh.stacks = homeOut; sh.windows = baseWindows      // finishAndRemoveTask ⇒ task + cửa sổ biến mất
            ids
        }
        val g = ClusterThemeGuard(sh, self, own, sleepMs = {}, vacantVdAllowed = { true })
        assertEquals(ThemeVerdict.SEND, g.admit(31))
        assertEquals(listOf(setOf(50)), finished, "gỡ đúng task ClusterBlack bằng API trong tiến trình")
        assertTrue(sh.calls.none { it.startsWith("am stack remove") || it.startsWith("am force-stop") }, "0 lệnh shell ghi: ${sh.calls}")
        assertEquals(emptyList<String>(), g.lastBlockers)
    }

    @Test
    fun `bong noi VietMap con tren cum - bo theme, 0 lenh ghi, KHONG dung app, Cai dat biet ten`() {
        val sh = Shell(homeOut + blackOn4, vietmapBubbleOn4 + blackWindow)
        val g = ClusterThemeGuard(sh, self, sleepMs = {}, vacantVdAllowed = { true })
        assertEquals(ThemeVerdict.SKIP_KNOWN, g.admit(31))
        assertEquals(listOf("VietMap"), g.lastBlockers)
        assertTrue(
            sh.calls.none { it.startsWith("am stack remove") || it.contains("force-stop") || it.startsWith("service call") },
            "không gỡ ClusterBlack vô ích, không force-stop app bên thứ ba: ${sh.calls}",
        )
        // Tắt bóng VietMap ⇒ lượt sau không còn bị chặn, Cài đặt hết câu nhắc.
        sh.stacks = homeOut; sh.windows = baseWindows
        assertEquals(ThemeVerdict.SEND, g.admit(31))
        assertEquals(emptyList<String>(), g.lastBlockers)
    }
}
