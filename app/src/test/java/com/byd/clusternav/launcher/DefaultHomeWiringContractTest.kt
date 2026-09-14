package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ S5 — dây nối "MÀN HÌNH CHÍNH" (nút Cài đặt · công tắc nổ máy · đọc-không-shell · một nguồn lệnh) ══════════
 *
 * Spec `docs/specs/kachi-settings-ia-v2.html` §9 (S5). Bài này canh **hành vi qua dấu vết mã** cho những ràng buộc
 * mà một test đơn vị chạy-thật không với tới (đọc HAL/PackageManager, đường dadb): xem CLAUDE.md §8 (hàm mới phải
 * có call site) và §2/§4 (đọc không mở shell; đổi state hệ thống phải tường minh + gác công tắc).
 */
class DefaultHomeWiringContractTest {

    private fun code(rel: String): String = SourceRoots.codeOf(rel)

    private val bridgeHome by lazy { code("src/main/java/com/byd/clusternav/launcher/ClusterNavBridgeHome.kt") }
    private val defaultHome by lazy { code("src/main/java/com/byd/clusternav/launcher/DefaultHome.kt") }
    private val autostart by lazy { code("src/main/java/com/byd/clusternav/KachiAutostart.kt") }
    private val sections by lazy { code("src/main/java/com/byd/clusternav/launcher/SettingsSections.kt") }

    @Test
    fun `setDefaultHome chay nen, qua transport tap trung, component tu nguon duy nhat`() {
        val body = SourceRoots.body(bridgeHome, "fun ClusterNavBridge.setDefaultHome(")
        assertTrue(body.contains("Thread("), "phải chạy trên thread NỀN (spec N5) — không chặn luồng vẽ")
        assertTrue(body.contains("LocalDeviceShell.setHomeActivity("), "phải đi qua transport tập trung của :car-integration")
        assertTrue(body.contains("DefaultHome.component("), "component lấy từ nguồn DUY NHẤT (DefaultHome), không ghép tay")
        assertTrue(body.contains("ui(Runnable"), "kết quả phải post về luồng vẽ")
    }

    @Test
    fun `doc trang thai HOME KHONG duoc mo kenh shell (rang buoc C4)`() {
        assertTrue(
            SourceRoots.body(bridgeHome, "fun ClusterNavBridge.isDefaultHome(").contains("DefaultHome.isCurrent("),
            "isDefaultHome phải đọc qua DefaultHome (PackageManager), không tự dựng lại",
        )
        assertTrue(defaultHome.contains("PackageQueries.resolveActivity("), "đọc HOME qua cửa PackageQueries")
        assertFalse(
            defaultHome.contains("LocalDeviceShell") || defaultHome.contains("AdbKeys"),
            "ĐỌC trạng thái không được mở kênh dadb — cùng luật PermissionPreflight",
        )
    }

    @Test
    fun `cong tac keep-home di qua WorkspacePrefs (theo XE)`() {
        assertTrue(bridgeHome.contains("WorkspacePrefs(app).keepHomeOnBoot()"), "đọc cờ theo XE ở WorkspacePrefs")
        assertTrue(bridgeHome.contains("WorkspacePrefs(app).setKeepHomeOnBoot("), "ghi cờ theo XE ở WorkspacePrefs")
    }

    @Test
    fun `dat HOME luc no may phai gac sau cong tac keep-home (mac dinh TAT)`() {
        val runBoot = SourceRoots.body(autostart, "fun runBoot(")
        assertTrue(
            runBoot.contains("keepHomeOnBoot()") && runBoot.contains("ensureHomeActivity("),
            "set-home lúc khởi động phải nằm SAU cổng keepHomeOnBoot — đổi HOME cả xe không tự làm sau lưng (CLAUDE.md §4)",
        )
        assertTrue(autostart.contains("HomeActivityCmd.set("), "dùng builder lệnh chung, không chuỗi thô")
        assertTrue(autostart.contains("HomeActivityCmd.RESOLVE"), "đọc HOME hiện tại qua builder chung")
    }

    @Test
    fun `chuoi lenh set-home-activity chi nam o MOT nguon (HomeActivityCmd)`() {
        // codeOf đã bỏ chú thích ⇒ chỉ còn LITERAL trong CODE. Rải chuỗi này ra nhiều nơi là cách hai đường
        // (Cài đặt vs khởi động) lệch nhau một ngày nào đó — đúng bẫy DRY mà HomeActivityCmd sinh ra để chặn.
        listOf(bridgeHome, defaultHome, autostart, sections).forEach { src ->
            assertFalse(
                src.contains("set-home-activity"),
                "chuỗi lệnh set-home-activity chỉ được khai ở HomeActivityCmd (:core), không viết thô nơi khác",
            )
        }
    }

    @Test
    fun `nut set-home trong Cai dat goi dung cau va cong tac ghi dung cau`() {
        val home = SourceRoots.body(sections, "private fun homeScreen(")
        assertTrue(home.contains("deps.bridge.setDefaultHome"), "nút phải gọi cầu, không tự chạy lệnh")
        assertTrue(home.contains("deps.bridge.isDefaultHome()"), "trạng thái đọc qua cầu (không shell)")
        assertTrue(home.contains("deps.bridge.setKeepHomeOnBoot("), "công tắc ghi qua cầu")
        // Nút ẩn khi đã là home (task item 2) — có nhánh đặt visibility theo isHome.
        assertTrue(home.contains("View.GONE"), "nút phải ẩn khi Kachi đã là màn hình chính")
    }
}
