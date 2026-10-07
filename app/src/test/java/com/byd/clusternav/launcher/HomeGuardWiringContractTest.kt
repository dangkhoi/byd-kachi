package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 2.96 · R8 — bài canh TĨNH cho dây nối giữ HOME (luật thuần ở `:core` `HomeGuardPolicyTest`).
 *
 * Khoá cái gì: [ĐO xe 07/10] launcher khác giành HOME ~17 s sau khi Kachi lên, lượt `KachiAutostart` (~6 s) thua ⇒ phải
 * có nhịp TIẾN TRÌNH (không phải nhịp màn chính — nó gỡ ở onPause, đúng lúc HOME đã mất) gọi luật và đặt lại qua CÙNG
 * đường với nút Cài đặt; gác bởi lựa chọn bền của người dùng; không shell khi chỉ đọc; không tên gói nào (CLAUDE.md §7);
 * mọi hàm mới có call site thật (CLAUDE.md §8).
 */
class HomeGuardWiringContractTest {

    private val guard by lazy { SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/HomeGuard.kt") }
    private val application by lazy { SourceRoots.codeOf("src/main/java/com/byd/clusternav/KachiApplication.kt") }
    private val autostart by lazy { SourceRoots.codeOf("src/main/java/com/byd/clusternav/KachiAutostart.kt") }

    @Test
    fun `cai o tien trinh chinh, truoc dong chot cuoi EarlyShellChannel`() {
        val onCreate = SourceRoots.body(application, "override fun onCreate()")
        val i = onCreate.indexOf("HomeGuard.install(this)")
        assertTrue(i >= 0, "HomeGuard phải được cài ở KachiApplication.onCreate")
        assertTrue(i < onCreate.indexOf("EarlyShellChannel.start(this)"), "đường mới đứng TRƯỚC dòng chốt cuối")
        assertTrue(i > onCreate.indexOf("isBackgroundVoiceProcess()"), "chỉ tiến trình launcher (sau cổng tiến trình nền)")
    }

    @Test
    fun `nhip goi luat thuan voi su that doc moi nhip`() {
        val tick = SourceRoots.body(guard, "private fun tick(")
        assertTrue(tick.contains("HomeGuardPolicy.decide("), "quyết định qua luật thuần")
        assertTrue(
            tick.contains("HomeGuardPolicy.wantsKachiHome(prefs.homeChosen(), prefs.keepHomeOnBoot())"),
            "gác bởi lựa chọn BỀN của người dùng (cùng điều kiện với KachiAutostart)",
        )
        assertTrue(tick.contains("DefaultHome.currentPackage(app)"), "đọc HOME trong tiến trình (PackageManager), không shell")
        assertTrue(tick.contains("ShellReadiness.isUp()"), "kênh chưa lên ⇒ bỏ, không tự mở kênh")
        assertTrue(tick.contains("HomeGuardPolicy.startsNewTrip("), "bộ đếm về đầu khi mở xe")
        assertTrue(tick.contains("HomeGuardPolicy.shouldLog("), "log khi đổi, không mỗi nhịp")
        assertTrue(tick.contains("d.taker"), "log gói đã giành HOME")
        val loop = SourceRoots.body(guard, "private fun loop(")
        assertTrue(loop.contains("HomeGuardPolicy.nextDelayMs("), "nhịp dày đầu chuyến rồi thưa — theo luật")
        assertTrue(loop.contains("finally"), "lỗi một nhịp không làm đứt chuỗi hẹn giờ")
    }

    @Test
    fun `dat lai qua CUNG duong voi nut Cai dat`() {
        val reassert = SourceRoots.body(guard, "private fun reassert(")
        assertTrue(reassert.contains("DefaultHome.enableHomeEntry(app)"), "bật alias trước (alias có thể tắt sau nâng cấp)")
        assertTrue(
            reassert.contains("LocalDeviceShell.setHomeActivity(AdbKeys.ensure(app), DefaultHome.component(app))"),
            "đúng thân ClusterNavBridge.setDefaultHome (HomeActivityCmd.set + đọc lại xác nhận)",
        )
        assertTrue(
            reassert.indexOf("enableHomeEntry") < reassert.indexOf("setHomeActivity"),
            "alias bật TRƯỚC set-home-activity",
        )
        assertTrue(SourceRoots.body(guard, "private fun tick(").contains("reassert(app)"), "call site của reassert (§8)")
    }

    @Test
    fun `khong ten goi nao, khong lenh shell tu dung`() {
        listOf("launcher3", "\"com.", "\"cmd ", "\"am ", "\"wm ").forEach {
            assertFalse(guard.contains(it), "HomeGuard không được chứa '$it' (generic, CLAUDE.md §7; lệnh qua HomeActivityCmd)")
        }
    }

    @Test
    fun `KachiAutostart dung chung dieu kien`() {
        assertTrue(autostart.contains("HomeGuardPolicy.wantsKachiHome(prefs.homeChosen(), prefs.keepHomeOnBoot())"))
    }
}
