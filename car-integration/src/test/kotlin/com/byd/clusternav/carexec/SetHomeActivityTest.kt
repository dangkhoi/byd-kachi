package com.byd.clusternav.carexec

import com.byd.clusternav.launcher.HomeActivityCmd
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * S5 — khoá chuỗi lệnh + phép xác nhận + phân nhánh kết quả của `LocalDeviceShell.setHomeActivity`, off-car.
 *
 * Không chạm dadb thật: `setHomeBlock` nhận một `sh` giả nên khoá được **đúng hai lệnh, đúng thứ tự** (set trước,
 * resolve sau — đọc lại để đo, không tin `set` trả Success — CLAUDE.md §2/§8). `mapSetHome` khoá ba nhánh kết quả.
 */
class SetHomeActivityTest {

    private val comp = "com.byd.launcher/com.byd.clusternav.launcher.KachiHomeActivity"

    private fun text(out: String) = LocalShellText(out, "", 0)

    @Test
    fun `setHomeBlock chay set roi resolve dung thu tu, xac nhan bang resolve`() {
        val cmds = mutableListOf<String>()
        val (isHome, resolved) = LocalDeviceShell.setHomeBlock(comp) { c ->
            cmds += c
            if (c.startsWith("cmd package resolve-activity")) text(comp) else text("Success")
        }
        assertEquals(listOf(HomeActivityCmd.set(comp), HomeActivityCmd.RESOLVE), cmds, "phải set TRƯỚC, resolve SAU")
        assertTrue(isHome, "resolve trả đúng component ⇒ đã là HOME")
        assertEquals(comp, resolved)
    }

    @Test
    fun `setHomeBlock bao chua phai HOME khi resolve tra launcher khac (du set in Success)`() {
        val (isHome, _) = LocalDeviceShell.setHomeBlock(comp) { c ->
            if (c.startsWith("cmd package resolve-activity")) {
                text("com.android.launcher3/com.android.launcher3.Launcher")
            } else {
                text("Success") // set "thành công" nhưng ROM không đổi HOME — đúng ca phải đo lại
            }
        }
        assertTrue(!isHome, "set in Success mà resolve vẫn launcher khác ⇒ CHƯA phải HOME")
    }

    @Test
    fun `mapSetHome — Ok khi da la HOME`() {
        val outcome = LocalDeviceShell.mapSetHome(LocalShellResult.Ok(true to comp, attempts = 1))
        assertInstanceOf(LocalSetHomeOutcome.Ok::class.java, outcome)
    }

    @Test
    fun `mapSetHome — Failed voi output resolve khi chua doi duoc HOME`() {
        val outcome = LocalDeviceShell.mapSetHome(LocalShellResult.Ok(false to "other/launcher", attempts = 1))
        assertInstanceOf(LocalSetHomeOutcome.Failed::class.java, outcome)
        assertEquals("other/launcher", (outcome as LocalSetHomeOutcome.Failed).resolveOutput)
    }

    @Test
    fun `mapSetHome — NoShellChannel giu nguyen ly do khi phien hong`() {
        val outcome = LocalDeviceShell.mapSetHome(
            LocalShellResult.Failed(LocalShellFailure.AWAITING_APPROVAL, attempts = 1, cause = null),
        )
        assertInstanceOf(LocalSetHomeOutcome.NoShellChannel::class.java, outcome)
        assertEquals(
            LocalShellFailure.AWAITING_APPROVAL,
            (outcome as LocalSetHomeOutcome.NoShellChannel).reason,
        )
    }
}
