package com.byd.clusternav.modules.clustercast

import com.byd.clusternav.modules.clustercast.simplified.ClusterDisplayResolver
import com.byd.clusternav.modules.clustercast.simplified.ClusterThemeGuard
import com.byd.clusternav.modules.clustercast.simplified.ProjectionManager
import com.byd.clusternav.modules.clustercast.simplified.ShellResult
import com.byd.clusternav.modules.clustercast.simplified.SimpleCastShell
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 2.95 · CLUSTER-VACANT-THEME-DL3 — khoá lỗi SL6 (cụm cong) [ĐO log SL6 07/10]: màn ảo cụm (display 2) có sẵn từ lúc nổ máy, chỉ
 * có HOME trên màn chính ⇒ 2.89–2.94 bỏ `30` (`Skip(VD_PRESENT)` ⇒ SKIP_KNOWN), chỉ gửi `16 → 35` ⇒ cụm không vào chế độ chiếu.
 * Đi trọn đường hồ sơ → công thức → cổng → [ProjectionManager]: hồ sơ dò bằng `detectSeed` với `car.type` KHÁC 138 (và không đọc
 * được) phải gửi `30 → 16 → 35`. Thử ĐỎ: trả lại điều kiện mã xe trong `ClusterProfile.forCarType`.
 */
class ClusterSl6VacantThemeTest {

    /** Màn ảo cụm của AutoContainer còn đó, KHÔNG có task/cửa sổ nào trên nó (dạng dòng `dumpsys display` thật, rút gọn). */
    private val detect = """
        |  DisplayDeviceInfo{"fission_bg_xdjaVirtualSurface": uniqueId="virtual:com.xdja.containerservice,1000,fission_bg_xdjaVirtualSurface,0", 1920 x 720, type VIRTUAL, state ON, owner com.xdja.containerservice (uid 1000), FLAG_PRESENTATION, FLAG_OWN_CONTENT_ONLY}
        |    mUniqueId=virtual:com.xdja.containerservice,1000,fission_bg_xdjaVirtualSurface,0
        |  Display 0:
        |  Display 2:
        |    mPrimaryDisplayDevice=fission_bg_xdjaVirtualSurface
        |    mBaseDisplayInfo=DisplayInfo{"fission_bg_xdjaVirtualSurface, displayId 2", uniqueId "virtual:com.xdja.containerservice,1000,fission_bg_xdjaVirtualSurface,0", app 1920 x 720, real 1920 x 720, ...}
        |""".trimMargin()

    private val home = """
        Stack id=0 bounds=[0,0][1920,1080] displayId=0 userId=0
         configuration={1.0 winConfig={ mWindowingMode=fullscreen mDisplayWindowingMode=fullscreen mActivityType=home} s.38}
          taskId=4: com.byd.launcher/com.byd.clusternav.launcher.KachiHome bounds=[0,0][1920,1080] userId=0 visible=true
    """.trimIndent()

    private inner class Shell : SimpleCastShell {
        val calls = mutableListOf<String>()
        override fun execute(command: String): ShellResult {
            calls += command
            return when (command) {
                ClusterDisplayResolver.DETECT_CMD -> ShellResult(0, detect, "")
                ClusterThemeGuard.STACK_CMD -> ShellResult(0, home, "")
                ClusterThemeGuard.WINDOWS_CMD ->
                    ShellResult(0, "  Window #0 Window{a0 u0 com.byd.launcher/com.byd.clusternav.launcher.KachiHome}:\n    mDisplayId=0 stackId=0", "")
                else -> ShellResult(0, "", "")
            }
        }
    }

    private fun openOn(profile: ClusterProfile): Pair<Boolean, List<String>> {
        val sh = Shell()
        val pm = ProjectionManager(sh, sleepMs = {}, recipe = profile.projectionRecipe())
        val gate = ClusterThemeGuard(sh, "com.byd.launcher", sleepMs = {}, vacantVdAllowed = { pm.recipe.themeOnVacantVd })
        val ok = pm.open(displayId = 2, gate = gate)
        val r = pm.recipe
        val sent = sh.calls.filter { it == r.command(30) || it == r.command(16) || it == r.command(35) }
        return ok to sent.map { c -> listOf(30, 16, 35).first { r.command(it) == c }.toString() }
    }

    @Test fun `SL6 - car type khac 138 hoac khong doc duoc - man ao cum trong - gui 30 truoc 16 35`() {
        for (t in listOf("162", "137", null)) {
            val (ok, sent) = openOn(ClusterProfile.detectSeed("BYD AUTO", "byd", "BYD", "", t))
            assertTrue(ok, "car.type=$t")
            assertEquals(listOf("30", "16", "35"), sent, "car.type=$t")
        }
    }

    @Test fun `co che loi cu - co muc B tat thi man ao co san lam bo 30 (ghim de khong ai bat lai nham)`() {
        val (ok, sent) = openOn(ClusterProfile.SEAL_DL3)   // seed thô, chưa qua forCarType ⇒ cờ tắt như 2.89–2.94 trên SL6
        assertTrue(ok)
        assertEquals(listOf("16", "35"), sent)
    }
}
