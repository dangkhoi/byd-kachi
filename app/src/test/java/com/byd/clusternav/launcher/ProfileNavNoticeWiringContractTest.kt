package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 2.97 · R3 — dây nối phía `:app` (luật thuần ở `ProfileNavSwitchTest`): `switchProfile` đọc công tắc "Dẫn đường lên cụm" TRƯỚC
 * lượt chụp–áp và SAU `reapplyAll`, giao cho [ProfileNavNotice]; màn chính lấy ra một lần và nhắc — kể cả khi đổi hồ sơ đổi luôn
 * ngôn ngữ (`recreate`: lấy ở lượt render kế). Mọi lối đổi hồ sơ (chip · giọng nói · cầu kiểm thử) đi qua `switchProfile`.
 */
class ProfileNavNoticeWiringContractTest {

    private fun code(name: String) = SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/$name")

    private fun order(src: String, vararg parts: String) {
        var at = -1
        for (p in parts) {
            val i = src.indexOf(p, at + 1)
            assertTrue(i > at, "thứ tự sai/thiếu: '$p' phải đứng sau mốc trước trong:\n$src")
            at = i
        }
    }

    @Test
    fun `doi ho so doc cong tac truoc va sau, giao cho notice`() {
        val fn = SourceRoots.body(code("PrefsWorkspaceRepository.kt"), "override fun switchProfile(name: String): HomeUiState {")
        order(fn, "val navBefore = com.byd.clusternav.Prefs.enabled(app)", "prefs.snapshotClusterNav(", "prefs.applyClusterNav(name)",
            "bridge.reapplyAll()", "ProfileNavNotice.record(name, navBefore, com.byd.clusternav.Prefs.enabled(app))", "return load()")
    }

    @Test
    fun `man chinh nhac mot lan, ca sau recreate`() {
        val render = SourceRoots.body(code("KachiHomeRender.kt"), "internal fun KachiHomeActivity.render(state: HomeUiState) {")
        order(render, "recreate(); return }", "ProfileNavNotice.take()?.let", "R.string.kachi_profile_nav_off", "workspace.render(")
        val notice = code("ProfileNavNotice.kt")
        assertTrue("ProfileNavSwitch.shouldNotice(before, after)" in notice && "pending.getAndSet(null)" in notice, notice)
    }
}
