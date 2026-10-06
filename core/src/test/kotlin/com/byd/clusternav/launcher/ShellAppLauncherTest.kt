package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Kiểm bộ adapter ON-CAR [ShellAppLauncher] hoàn toàn off-car: cắm một `sh` giả trả output resolve tuỳ lệnh.
 *
 * 2.93 · SLOT-DEAD-OPENSLOT (spec `kachi-293-wave2a.html` §4.4): năm bài của `openInSlot` (chọn task theo display · một lượt
 * đọc stack · không lên · resize bị từ chối · không phân giải được) GỠ cùng hàm — đường ấy 0 chỗ gọi sản phẩm. Bài cuối khoá
 * việc gỡ: hợp đồng [AppLauncher] không còn hai hàm mở nổi (thêm lại = cần spec + chỗ gọi, không phải khối chết).
 *
 * 2.93 wave 2C · SLOT-DEAD-FREEFORM-REST (spec `kachi-293-wave2c.html` R2): bài `isFreeformAvailable reads the global flag` GỠ
 * cùng hàm (0 chỗ gọi sản phẩm [ĐO grep 07/10]); bài khoá hợp đồng nay đòi ĐÚNG một hàm `closeSlot`.
 */
class ShellAppLauncherTest {

    private fun fakeShell(
        component: String = "com.foo/.Main",
        calls: MutableList<String> = mutableListOf(),
    ): (String) -> String = { cmd ->
        calls += cmd
        if (cmd.startsWith("cmd package resolve-activity")) "priority=0\n$component" else ""
    }

    @Test
    fun `closeSlot sends the fullscreen return recipe for the resolved component`() {
        val calls = mutableListOf<String>()
        ShellAppLauncher(fakeShell(calls = calls)).closeSlot("com.foo")
        assertTrue(calls.any { it.contains("--windowingMode 1") && it.contains("0x20000000") && it.contains("com.foo/.Main") })
    }

    @Test
    fun `closeSlot voi ten goi la - 0 lenh shell (chuoi tu dia khong vao shell)`() {
        val calls = mutableListOf<String>()
        ShellAppLauncher(fakeShell(calls = calls)).closeSlot("com.foo; reboot")
        assertEquals(emptyList<String>(), calls, "tên gói lạ phải dừng trước shell (CLAUDE.md §4.1)")
    }

    @Test
    fun `hop dong AppLauncher khong con duong mo cua so noi - SLOT-DEAD-OPENSLOT + FREEFORM-REST`() {
        val names = AppLauncher::class.java.methods.map { it.name }.toSet()
        assertEquals(setOf("closeSlot"), names - Any::class.java.methods.map { it.name }.toSet(),
            "hợp đồng chỉ còn closeSlot — isFreeformAvailable (0 chỗ gọi) đã gỡ ở wave 2C")
        // Bộ mở có kênh không còn hỏi cờ freeform: lượt đóng duy nhất KHÔNG chạm `settings … enable_freeform_support`.
        val calls = mutableListOf<String>()
        ShellAppLauncher(fakeShell(calls = calls)).closeSlot("com.foo")
        assertFalse(calls.any { "enable_freeform_support" in it }, "không còn lệnh đọc cờ freeform: $calls")
        val builders = FreeformLaunch::class.java.methods.map { it.name }.toSet()
        listOf("launchCmd", "parseTaskId").forEach { assertFalse(it in builders, "FreeformLaunch.$it là khối chết đã gỡ") }
        assertTrue("parseTaskIdOnDisplay" in builders, "bộ đo ô (SlotLiveProbe) còn dùng parseTaskIdOnDisplay")
    }
}
