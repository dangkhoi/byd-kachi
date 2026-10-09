package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 2.98 · OTA-UPDATE-DOT — dây nối chấm *có bản mới* (luật ở `:core` `UpdateDotTest`). CLAUDE.md §8: hàm mới phải có
 * chỗ gọi thật; bài này canh (1) mọi lượt dò ghi bản ghi, (2) ba bề mặt gắn chấm, (3) chấm không tự mở mạng / hẹn giờ.
 */
class UpdateDotWiringContractTest {

    private fun code(relative: String): String = SourceRoots.codeOf(relative)

    private val flow by lazy { code("src/main/java/com/byd/clusternav/UpdateFlow.kt") }
    private val store by lazy { code("src/main/java/com/byd/clusternav/UpdateDotStore.kt") }
    private val badge by lazy { code("src/main/java/com/byd/clusternav/launcher/UpdateDotBadge.kt") }
    private val strip by lazy { code("src/main/java/com/byd/clusternav/launcher/KachiTopStrip.kt") }
    private val sections by lazy { code("src/main/java/com/byd/clusternav/launcher/SettingsSections.kt") }
    private val panel by lazy { code("src/main/java/com/byd/clusternav/launcher/SettingsPanel.kt") }

    @Test
    fun `moi luot do ghi ban ghi ngay sau check`() {
        val start = SourceRoots.body(flow, "fun start(")
        val check = start.indexOf("UpdateChecker.check(")
        val rec = start.indexOf("UpdateDotStore.record(")
        assertTrue(check >= 0 && rec > check, "UpdateFlow.start phải ghi bản ghi SAU check (đường tự động + bấm tay)")
        assertEquals(1, Regex("""UpdateChecker\.check\(""").findAll(flow).count(), "một lượt dò, không thêm lượt mạng")
    }

    @Test
    fun `ba be mat gan cham`() {
        val build = SourceRoots.body(strip, "private fun build(")
        assertTrue("UpdateDotBadge.bind(" in build, "nút ⚙ thanh trên")
        // Senior review Pass 10 [P2] — đổi chủ đề tại chỗ (`configChanges=uiMode`) không dựng lại view ⇒ chấm phải tô lại qua `themed`.
        assertTrue("themed { UpdateDotBadge.refresh(it) }" in build, "chấm tô lại khi đổi chủ đề tại chỗ (restyle)")
        assertTrue("fun refresh(v: View)" in badge && "as? Dot ?: return" in SourceRoots.body(badge, "fun refresh("), "refresh chỉ khi đã bind")
        assertTrue("UpdateDotBadge.bind(update)" in SourceRoots.body(sections, "private fun system("), "hàng Kiểm tra cập nhật")
        assertTrue("UpdateDotBadge.bind(" in SourceRoots.body(panel, "private fun railCell("), "ô rail nhóm Hệ thống")
    }

    @Test
    fun `cham khong mo mang khong hen gio`() {
        listOf(store, badge).forEach { src ->
            listOf("HttpConn", "UpdateChecker.check(", "postDelayed", "Timer(", "AlarmManager", "WorkManager").forEach {
                assertFalse(it in src, "chấm chỉ ăn theo lượt dò sẵn có — thấy '$it'")
            }
        }
        assertTrue("UpdateDot.afterCheck(" in store && "UpdateDot.shows(" in store, "luật quyết ở :core, không viết lại ở :app")
    }

    @Test
    fun `badge go listener khi thao khoi cua so`() {
        val bind = SourceRoots.body(badge, "fun bind(")
        assertTrue("UpdateDotStore.unlisten(" in bind && "onViewDetachedFromWindow" in bind, "không giữ view chết")
    }
}
