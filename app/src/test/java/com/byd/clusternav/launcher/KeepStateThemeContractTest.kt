package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * #10 (owner 2026-09-23) — GIỮ STATE khi đổi theme. Launcher: "dù đổi gì màn cũng chạy tiếp, không restart".
 * Đổi light↔dark chỉ là ĐỔI MÀU ⇒ restyle TẠI CHỖ, KHÔNG recreate (recreate giết ô app đang chiếu). Kiểm bằng
 * quét nguồn (dự án test JVM, không Robolectric — View không dựng được ở đây).
 */
class KeepStateThemeContractTest {

    private val activity by lazy { SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/KachiHomeActivity.kt") }
    private val workspace by lazy { SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/WorkspaceView.kt") }

    @Test
    fun `theme doi thi restyle tai cho, KHONG recreate`() {
        val render = SourceRoots.body(activity, "private fun render(")
        assertTrue(render.contains("applyThemeInPlace()"), "theme đổi phải restyle tại chỗ (applyThemeInPlace)")
        // recreate() chỉ được ở nhánh NGÔN NGỮ (LangHost.changed), KHÔNG ở nhánh themeChanged.
        val recreateIdx = render.indexOf("recreate()")
        val langIdx = render.indexOf("LangHost.changed")
        assertTrue(recreateIdx > 0 && langIdx > 0, "recreate còn cho ngôn ngữ")
        assertTrue(langIdx < recreateIdx, "recreate phải nằm ở nhánh LangHost (ngôn ngữ), không phải theme")
        // themeChanged KHÔNG được kéo recreate.
        val themeLine = render.lines().firstOrNull { it.contains("themeChanged") && it.contains("recreate") }
        assertTrue(themeLine == null, "nhánh themeChanged KHÔNG được recreate: $themeLine")
    }

    @Test
    fun `restyle GIU o App (khong nha VD, app khong restart)`() {
        val fn = SourceRoots.body(workspace, "fun restyle(")
        assertTrue(fn.contains("is SlotContent.App") && fn.contains("continue"),
            "restyle phải BỎ QUA ô App (giữ VdAppHost) — nếu không app trong ô bị dựng lại = restart")
        assertFalse(fn.contains("releaseAppHosts"), "restyle KHÔNG được nhả app-host (đó là đường recreate/rebuild)")
    }
}
