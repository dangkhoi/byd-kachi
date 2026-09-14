package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * S5 — khoá phần THUẦN của "đặt/đọc màn hình chính": chuỗi lệnh + phép xác nhận từ output resolve-activity.
 * Đây là phần mà `LocalDeviceShell.setHomeActivity` và `KachiAutostart.ensureHomeActivity` cùng dùng.
 */
class HomeActivityCmdTest {

    private val comp = "com.byd.launcher/com.byd.clusternav.launcher.KachiHomeActivity"

    @Test
    fun `set dung dinh dang set-home-activity`() {
        assertEquals("cmd package set-home-activity $comp", HomeActivityCmd.set(comp))
    }

    @Test
    fun `RESOLVE doc dung category HOME kem --brief`() {
        assertTrue(HomeActivityCmd.RESOLVE.startsWith("cmd package resolve-activity"), HomeActivityCmd.RESOLVE)
        assertTrue(HomeActivityCmd.RESOLVE.contains("--brief"), "cần --brief để in đúng component")
        assertTrue(HomeActivityCmd.RESOLVE.contains("android.intent.category.HOME"), HomeActivityCmd.RESOLVE)
    }

    @Test
    fun `isHome dung khi resolve tra ve dung component (co trim)`() {
        assertTrue(HomeActivityCmd.isHome(comp, comp))
        assertTrue(HomeActivityCmd.isHome("  $comp  ", comp), "parseComponent phải trim khoảng trắng")
    }

    @Test
    fun `isHome false khi HOME la launcher khac`() {
        assertFalse(HomeActivityCmd.isHome("com.android.launcher3/com.android.launcher3.Launcher", comp))
    }

    @Test
    fun `isHome false khi output rong hoac la cau loi co khoang trang`() {
        assertFalse(HomeActivityCmd.isHome("", comp))
        assertFalse(HomeActivityCmd.isHome("No activity found for Intent", comp))
    }
}
