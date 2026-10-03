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
    private val renderKt by lazy { SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/KachiHomeRender.kt") }   // `render`/`applyThemeInPlace` tách ra (L6-debt 2026-09-27)
    private val workspace by lazy { SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/WorkspaceView.kt") }

    @Test
    fun `theme doi thi restyle tai cho, KHONG recreate`() {
        val render = SourceRoots.body(renderKt, "fun KachiHomeActivity.render(")
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

    /**
     * QA 2.87 — hai ghi chú Cài đặt (`kachi_theme_note` · `kachi_color_note`) nói *"đổi bảng màu/màu thì màn hình dựng lại một
     * lượt"* ở cả 5 tiếng, trong khi mã (bài ngay trên) đổi màu TẠI CHỖ từ #10 — người dùng được dặn chờ một lượt dựng lại không
     * bao giờ xảy ra. Ghi chú NGÔN NGỮ (`kachi_lang_note`) vẫn nói dựng lại — đúng, nhánh `LangHost.changed` ⇒ `recreate()`.
     */
    @Test
    fun `ghi chu bang mau va mau nhan khong con hua dung lai man hinh`() {
        val claimsRebuild = mapOf(
            "values" to "dựng lại", "values-en" to "rebuild", "values-zh-rCN" to "重建",
            "values-th" to "สร้างหน้าจอใหม่หนึ่งครั้ง", "values-ms" to "membina semula",
        )
        claimsRebuild.forEach { (dir, phrase) ->
            val xml = SourceRoots.text("src/main/res/$dir/strings_kachi.xml")
            listOf("kachi_theme_note", "kachi_color_note").forEach { key ->
                val v = Regex("""<string name="$key">(.*?)</string>""").find(xml)?.groupValues?.get(1)
                assertTrue(v != null, "$dir thiếu $key")
                assertFalse(phrase in v!!, "$dir/$key còn hứa dựng lại màn hình (đổi màu là TẠI CHỖ): $v")
            }
            val lang = Regex("""<string name="kachi_lang_note">(.*?)</string>""").find(xml)?.groupValues?.get(1).orEmpty()
            assertTrue(phrase in lang, "$dir/kachi_lang_note: đổi ngôn ngữ VẪN dựng lại (recreate) — ghi chú đó phải giữ: $lang")
        }
    }

    @Test
    fun `restyle GIU o App (khong nha VD, app khong restart)`() {
        val fn = SourceRoots.body(workspace, "fun restyle(")
        assertTrue(fn.contains("is SlotContent.App") && fn.contains("continue"),
            "restyle phải BỎ QUA ô App (giữ VdAppHost) — nếu không app trong ô bị dựng lại = restart")
        assertFalse(fn.contains("releaseAppHosts"), "restyle KHÔNG được nhả app-host (đó là đường recreate/rebuild)")
    }
}
