package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import com.byd.clusternav.voicekey.KeySourceKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ 2.93 · KEY-LABEL-PRESET-SHADOW — tên người dùng tự đặt không bị preset cùng mã che ═══════════════════════════
 *
 * [ĐO máy ảo QA 04/10] học phím 88 đặt tên *"MEDIA PREVIOUS"* ⇒ dòng gán hiện *"Bài trước (PREVIOUS · 88)"* (tên preset):
 * danh sách nút = preset TRƯỚC + nút tự học SAU (`ClusterNavBridge.buttonOptions`), phép tra cũ lấy mục khớp ĐẦU TIÊN.
 * Thử ĐỎ: đổi [labelOwner] về `firstOrNull { it.code == code && it.source == source }` ⇒ bài đầu đỏ.
 */
class ButtonOptionLabelTest {

    /** Đúng hình dạng `buttonOptions()`: 9 preset theo thứ tự `MainActivity`, rồi nút tự học. */
    private val presets = listOf(328, 231, 219, 85, 88, 87, 79, 5, 84).map { ButtonOption(it) }

    @Test
    fun `nut tu hoc trung ma preset - nhan la ten nguoi dung dat`() {
        val buttons = presets + ButtonOption(88, "MEDIA PREVIOUS")
        assertEquals("MEDIA PREVIOUS", buttons.labelOwner(88, null)?.customName)
    }

    @Test
    fun `khong co nut tu hoc trung ma - van ra preset`() {
        val owner = (presets + ButtonOption(200, "Nút lạ")).labelOwner(88, null)
        assertEquals(ButtonOption(88), owner)
        assertTrue(owner!!.isPreset)
    }

    @Test
    fun `nguon khac nhau la hai dong gan khac nhau`() {
        val buttons = presets +
            ButtonOption(88, "Núm lùi", KeySourceKind.CONSOLE_KNOB) +
            ButtonOption(88, "Vô-lăng lùi", KeySourceKind.STEERING_WHEEL)
        assertEquals("Núm lùi", buttons.labelOwner(88, KeySourceKind.CONSOLE_KNOB)?.customName)
        assertEquals("Vô-lăng lùi", buttons.labelOwner(88, KeySourceKind.STEERING_WHEEL)?.customName)
        assertTrue(buttons.labelOwner(88, null)!!.isPreset, "dòng không nguồn ⇒ preset (không mượn tên nút có nguồn)")
    }

    @Test
    fun `hai nut tu hoc cung ma, cung nguon - nut hoc truoc`() {
        val buttons = presets + ButtonOption(87, "Bài sau A") + ButtonOption(87, "Bài sau B")
        assertEquals("Bài sau A", buttons.labelOwner(87, null)?.customName)
    }

    @Test
    fun `ma khong ai khai - null de cho goi ghep ten hang framework`() {
        assertNull(presets.labelOwner(999, null))
        assertNull(presets.labelOwner(88, KeySourceKind.CONSOLE_KNOB), "preset không có nguồn")
    }

    /** Dây nối: dòng gán ở Cài đặt tra nhãn qua [labelOwner], không còn phép `firstOrNull` cũ. */
    @Test
    fun `dong gan o Cai dat tra nhan qua labelOwner`() {
        val keys = SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/SettingsSectionsKeys.kt")
        val fn = SourceRoots.body(keys, "private fun buttonLabel(code: Int, source: KeySourceKind?, buttons: List<ButtonOption>): String")
        assertTrue(fn.contains("buttons.labelOwner(code, source)"), fn)
        assertTrue(!fn.contains("firstOrNull"), "phép tra lấy mục khớp ĐẦU TIÊN (preset) quay lại:\n$fn")
    }
}
