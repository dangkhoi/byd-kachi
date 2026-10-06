package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ 2.93 · SETTINGS-RETHEME-INPLACE — đổi Sáng/Tối (hay màu) khi màn Cài đặt ĐANG MỞ thì bảng đổi màu TẠI CHỖ ═══════
 *
 * [ĐO máy ảo QA 04/10] màn chính đổi ngay (`applyThemeInPlace` tô lại nền · thanh trên · thanh nút · ô) nhưng bảng Cài đặt
 * giữ bảng màu cũ tới khi đóng-mở lại: mọi view của bảng tô màu LÚC DỰNG và không ai tô lại. View Android không chạy được
 * trên JVM ⇒ bài khoá DÂY NỐI: đường tô-lại-tại-chỗ có gọi bảng, bảng dựng lại vỏ + trang bằng bảng màu mới, và mọi chỗ đọc
 * [KachiTheme] của vỏ nằm trên đường dựng lại đó (một chỗ đọc màu mới ngoài đường ấy = một mảng giữ màu cũ).
 * Thử ĐỎ: bỏ dòng `panels.restyleSettings()` ở `applyThemeInPlace`.
 */
class SettingsRethemeInPlaceContractTest {

    private fun code(file: String) = SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/$file")
    private val render by lazy { code("KachiHomeRender.kt") }
    private val panels by lazy { code("HomePanels.kt") }
    private val panel by lazy { code("SettingsPanel.kt") }

    @Test
    fun `to lai tai cho goi ca bang Cai dat dang mo`() {
        val fn = SourceRoots.body(render, "internal fun KachiHomeActivity.applyThemeInPlace()")
        assertTrue(fn.contains("panels.restyleSettings()"), fn)
        val restyle = SourceRoots.body(panels, "fun restyleSettings()")
        assertTrue(restyle.contains("panel.post {"), "không dựng lại cây view giữa lượt phát chạm")
        assertTrue(restyle.contains("settingsPanel === panel && panel.isAttachedToWindow") && restyle.contains("panel.restyle()"))
    }

    @Test
    fun `bang dung lai vo va trang dang xem, giu nhom va cho cuon`() {
        val fn = SourceRoots.body(panel, "fun restyle()")
        listOf("sections.dispose()", "pages.clear()", "buildChrome()", "show(current)", "scrollTo(0, pageY)", "scrollTo(0, railY)")
            .forEach { assertTrue(fn.contains(it), "restyle thiếu `$it`:\n$fn") }
        val init = SourceRoots.body(panel, "    init {")
        assertTrue(init.contains("buildChrome()"), "lượt dựng đầu và lượt tô lại đi CÙNG một đường")
        assertFalse(init.contains("KachiTheme."), "init không được tự đọc bảng màu (sẽ không được tô lại)")
    }

    /** Mọi hàm đọc [KachiTheme] trong vỏ bảng phải nằm trên đường mà [SettingsPanel.restyle] chạy lại. */
    @Test
    fun `moi cho doc bang mau cua vo nam tren duong dung lai`() {
        val names = Regex("""(?:private |internal |override )?fun (\w+)\(""").findAll(panel).map { it.groupValues[1] }.toSet()
        val readers = names.filter { n ->
            runCatching { SourceRoots.body(panel, "fun $n(") }.getOrNull()?.contains("KachiTheme.") == true
        }.toSet()
        assertEquals(setOf("buildChrome", "head", "railCell", "paintRail"), readers, "hàm đọc màu ngoài đường dựng lại")
        val chrome = SourceRoots.body(panel, "private fun buildChrome()")
        assertTrue(chrome.contains("head()") && chrome.contains("rail()"))
        assertTrue(SourceRoots.body(panel, "private fun rail()").contains("railCell("))
        assertTrue(SourceRoots.body(panel, "fun show(group: SettingsGroup)").contains("paintRail("))
    }
}
