package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.KotlinSource
import com.byd.clusternav.testsupport.SourceRoots
import java.nio.file.Files
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ 2.91 · F1 — công tắc bóng VietMap phải NÓI THẬT (spec `docs/specs/kachi-291-small-fixes.html` §4.1) ═══════════════════
 *
 * Lỗi khoá ở đây [ĐO nguồn 2.90]: hàng *"Hiện bong bóng VietMap trên cụm"* đọc `vm_bubble_enabled` (nghĩa: tự mở VietMap, mặc
 * định TẮT) trong khi bóng thật đi theo `vm_bubble_hidden` (mặc định false = hiện) ⇒ người chưa từng chạm thấy "tắt" mà bóng
 * đang hiện, phải bật→tắt mới ẩn được. Bản vá: công tắc đọc/ghi CHÍNH cờ bóng; tự mở VietMap thành hàng riêng.
 *
 * Bất biến chuyển tiếp (không có bước migrate nào ghi prefs): cả hai khoá giữ nguyên giá trị người dùng đang có ⇒ cái người lái
 * THẤY trên cụm và việc VietMap có tự mở hay không đều KHÔNG đổi khi nâng cấp — chỉ công tắc thôi nói sai.
 * Thử ĐỎ: trả `on = bridge.vmBubbleShown()` về `bridge.vmBubbleEnabled()`.
 */
class VmBubbleSwitchHonestyContractTest {

    private val nav by lazy { SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/SettingsSectionsNav.kt") }
    private val bridge by lazy { SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/ClusterNavBridge.kt") }

    /** (tên tệp, mã đã bỏ chú thích) của mọi tệp `.kt` production. */
    private fun mainFiles(): List<Pair<String, String>> = SourceRoots.moduleSourceRoots().flatMap { root ->
        Files.walk(root).use { s ->
            s.filter { it.toString().endsWith(".kt") }.map { f ->
                f.fileName.toString() to KotlinSource.stripComments(f.toFile().readText())
            }.toList()
        }
    }

    @Test
    fun `cong tac hien bong doc dung co bong that, khong doc co tu mo VietMap`() {
        assertTrue("fun vmBubbleShown(): Boolean = !Prefs.vmBubbleHidden(app)" in bridge)
        val bubble = SourceRoots.body(nav, "private fun bubble(body: LinearLayout)")
        assertTrue("on = bridge.vmBubbleShown()," in bubble, bubble)
        assertTrue("on = bridge.vmBubbleAutostart()," in bubble, "tự mở VietMap là hàng RIÊNG, có nhãn riêng")
        assertTrue("R.string.kachi_bubble_autostart_title" in bubble)
        assertFalse("vmBubbleEnabled" in nav, "trang Dẫn đường không còn đọc cờ tự mở dưới tên 'bật bóng'")
        assertTrue("val enabled = bridge.vmBubbleShown()" in SourceRoots.body(nav, "private fun applyBubbleGate()"),
            "câu nhắc 'bật công tắc bong bóng' nói theo đúng công tắc đang hiện")
        assertTrue("fun vmBubbleAdjustable(): Boolean = vmBubbleShown() && VmOverlayPosition.castOn(app)" in bridge)
    }

    @Test
    fun `chi cong tac hien bong ghi co an - khong buoc migrate nao doi gia tri dang co`() {
        val writers = mainFiles().filter { (_, src) -> "setVmBubbleHidden(" in src && "fun Prefs.setVmBubbleHidden(" !in src }
            .map { it.first }
        assertEquals(listOf("ClusterNavBridge.kt"), writers, "một cửa ghi cờ ẩn bóng")
        assertEquals(1, Regex("""Prefs\.setVmBubbleHidden\(""").findAll(bridge).count())
        val enabledWriters = mainFiles()
            .filter { (_, src) -> src.replace("fun Prefs.setVmBubbleEnabled(", "").contains("Prefs.setVmBubbleEnabled(") }.map { it.first }
        assertEquals(listOf("ClusterNavBridge.kt"), enabledWriters, "một cửa ghi cờ tự mở VietMap")
    }

    @Test
    fun `5 ngon ngu co nhan hang tu mo VietMap`() {
        listOf("values", "values-en", "values-zh-rCN", "values-th", "values-ms").forEach { d ->
            val xml = SourceRoots.text("src/main/res/$d/strings_kachi.xml")
            listOf("kachi_bubble_autostart_title", "kachi_bubble_autostart_sub").forEach {
                assertTrue("name=\"$it\"" in xml, "$d thiếu $it")
            }
        }
    }

    /**
     * Review 2.91 Pass 1 [P3] — từ 2.91 có HAI công tắc bong bóng ở trên bộ chỉnh vị trí, mà câu nhắc cũ chỉ nói *"bật công tắc bong
     * bóng ở trên"* ⇒ người lái bật nhầm "Tự mở VietMap" (không mở khoá gì). Câu nhắc phải gọi ĐÚNG tên công tắc mở khoá (công tắc
     * hiện bóng — `applyBubbleGate` đọc `vmBubbleShown()`), ở cả 5 ngôn ngữ. Thử ĐỎ: đổi tên công tắc mà quên câu nhắc.
     */
    @Test
    fun `cau nhac khoa vi tri goi dung ten cong tac hien bong, du 5 ngon ngu`() {
        fun string(xml: String, name: String): String =
            Regex("""<string name="$name">(.*?)</string>""", RegexOption.DOT_MATCHES_ALL).find(xml)?.groupValues?.get(1) ?: error("thiếu $name")
        listOf("values", "values-en", "values-zh-rCN", "values-th", "values-ms").forEach { d ->
            val xml = SourceRoots.text("src/main/res/$d/strings_kachi.xml")
            val hint = string(xml, "kachi_bubble_need_toggle")
            assertTrue(string(xml, "kachi_bubble_enabled_title") in hint, "$d: câu nhắc phải gọi tên công tắc hiện bóng: $hint")
            assertFalse(string(xml, "kachi_bubble_autostart_title") in hint, "$d: công tắc tự mở VietMap không mở khoá vị trí: $hint")
        }
    }
}
