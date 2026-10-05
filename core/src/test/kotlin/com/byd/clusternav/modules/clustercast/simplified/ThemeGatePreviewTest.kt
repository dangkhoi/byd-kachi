package com.byd.clusternav.modules.clustercast.simplified

import com.byd.clusternav.modules.clustercast.CastDisplayFixtures2026_09_15
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * CLUSTER-THEME-SAFE B1a — "Cổng theme (chỉ đọc)" của `ClusterDiag`: chạy đúng các lượt đọc + quyết của lượt mở chiếu, in ra,
 * KHÔNG một lệnh ghi (kênh chỉ đọc chặn ở tầng thi hành), KHÔNG ghi sổ — kể cả khi luật mức B muốn gỡ ClusterBlack.
 */
class ThemeGatePreviewTest {

    private val self = CastDisplayFixtures2026_09_15.LAUNCHER_PKG
    private val black = "$self/com.byd.clusternav.modules.clustercast.ClusterBlackActivity"
    private val stacks = """
        Stack id=0 bounds=[0,0][1920,1080] displayId=0 userId=0
         configuration={1.0 winConfig={ mWindowingMode=fullscreen mDisplayWindowingMode=fullscreen mActivityType=home} s.38}
          taskId=4: com.byd.launcher/com.byd.clusternav.launcher.KachiHome bounds=[0,0][1920,1080] userId=0 visible=true
        Stack id=57 bounds=[0,0][1920,720] displayId=2 userId=0
         configuration={1.0 winConfig={ mWindowingMode=freeform mDisplayWindowingMode=fullscreen mActivityType=standard} s.3}
          taskId=58: $black bounds=[0,0][1920,720] userId=0 visible=true
    """.trimIndent()

    private class Device(private val detect: String, private val stacks: String) : SimpleCastShell {
        val calls = mutableListOf<String>()
        override fun execute(command: String): ShellResult {
            calls += command
            return when (command) {
                ClusterDisplayResolver.DETECT_CMD -> ShellResult(0, detect, "")
                ClusterThemeGuard.STACK_CMD -> ShellResult(0, stacks, "")
                ClusterThemeGuard.WINDOWS_CMD -> ShellResult(0, "  Window #0 Window{a u0 $BLACK}:\n    mDisplayId=2 stackId=57", "")
                else -> ShellResult(0, "", "")
            }
        }

        companion object { const val BLACK = "com.byd.launcher/com.byd.clusternav.modules.clustercast.ClusterBlackActivity" }
    }

    private class Recording : ThemeLedger.Store {
        var value: String? = null
        var writes = 0
        override fun read() = value
        override fun write(value: String): Boolean { writes++; this.value = value; return true }
    }

    @Test
    fun `B1a mac dinh - man ao cum co - in VD_PRESENT, chi doc display, 0 lenh ghi, 0 ghi so`() {
        val dev = Device(CastDisplayFixtures2026_09_15.DETECT_OUT_WITH_SLOT, stacks)
        val store = Recording()
        val out = ThemeGatePreview.report(dev, self, ProjectionRecipe.SEAL_DL3, store, ThemeLedger.JVM_CLOCK)
        assertTrue(out.contains("VD_PRESENT") && out.contains("SKIP_KNOWN"), out)
        assertTrue(out.trimEnd().endsWith("lệnh ghi bị chặn: 0"), out)
        assertEquals(listOf(ClusterDisplayResolver.DETECT_CMD), dev.calls)
        assertEquals(0, store.writes)
    }

    @Test
    fun `muc B - luat muon go ClusterBlack - preview van KHONG go (khong am stack remove toi thiet bi)`() {
        val dev = Device(CastDisplayFixtures2026_09_15.DETECT_OUT_WITH_SLOT, stacks)
        val store = Recording()
        val out = ThemeGatePreview.report(
            dev, self, ProjectionRecipe.SEAL_DL3.copy(themeOnVacantVd = true), store, ThemeLedger.JVM_CLOCK,
        )
        assertTrue(out.contains("RemovePlaceholder"), out)
        assertTrue(dev.calls.none { it.startsWith("am stack remove") || it.startsWith("service call") }, "${dev.calls}")
        assertEquals(0, store.writes)
    }

    @Test
    fun `chua co man ao cum - in ke hoach gui 30 roi 16 35 (khong gui that)`() {
        val dev = Device(CastDisplayFixtures2026_09_15.DETECT_OUT_SLOT_ONLY, stacks)
        val out = ThemeGatePreview.report(dev, self, ProjectionRecipe.SEAL_DL3, Recording(), ThemeLedger.JVM_CLOCK, header = listOf("car.type: x"))
        assertTrue(out.contains("kế hoạch: gửi 30 rồi 16 → 35"), out)
        assertTrue(out.lines()[1] == "car.type: x", out)
        assertTrue(dev.calls.none { it.startsWith("service call") }, "${dev.calls}")
    }

    @Test
    fun `kenh chi doc - lenh ngoai danh sach doc bi tu choi o tang thi hanh va ghi lai`() {
        val dev = Device("", "")
        val ro = ThemeGatePreview.ReadOnlyShell(dev)
        val r = ro.execute("service call AutoContainer 2 i32 1000 i32 30 s16 \"\"")
        assertEquals(1, r.exitCode)
        assertEquals(emptyList<String>(), dev.calls, "không tới thiết bị")
        assertEquals(1, ro.refused.size)
        assertEquals(0, ro.execute(ClusterCarType.CMD).exitCode)
        assertTrue(ThemeGatePreview.ReadOnlyLedger(Recording()).write("30;ok;1;1").not())
    }
}
