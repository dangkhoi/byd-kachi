package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ KACHI-301 R11 (hộp Địa chỉ) + R12 (hộp Lịch dẫn đường) — giao kèo nguồn ═══════════════════════════════════
 *
 * Khoá những điều R11/R12 hứa mà bài thuần (`PlaceFormTest`, `TimeWheelMathTest`) không chạm tới được:
 *  1. Không còn `AlertDialog`; hộp lịch KHÔNG có ô gõ giờ / hộp con chọn giờ (bài học tiêu điểm 2026-09-12: một hộp).
 *  2. Mỗi hộp kiểm bằng ĐÚNG luật của đường lưu (`PlaceForm` ↔ `SavedPlaces.of`; `ScheduledNavRules.of`).
 *  3. Hàm mới có chỗ gọi thật (CLAUDE.md §8): askPlace → SettingsPlaceDialog → khuôn Biểu mẫu; TimeWheel trong hộp lịch;
 *     nút ▲▼ nhấn giữ qua `SettingsRepeatTouch`.
 *  4. Chữ đủ 5 ngôn ngữ, cùng tập khoá, cùng tham số định dạng, đều được dùng; 0 luồng/nhịp nền.
 */
class KachiFormContractTest {

    private val dir = "src/main/java/com/byd/clusternav/launcher"
    private val nav by lazy { SourceRoots.codeOf("$dir/SettingsNavAutomationDialog.kt") }
    private val place by lazy { SourceRoots.codeOf("$dir/SettingsPlaceDialog.kt") }
    private val wheel by lazy { SourceRoots.codeOf("$dir/TimeWheel.kt") }
    private val controls by lazy { SourceRoots.codeOf("$dir/KachiFormControls.kt") }
    private val settings by lazy { SourceRoots.codeOf("$dir/SettingsDialogs.kt") }
    private val all get() = listOf(nav, place, wheel, controls)

    @Test
    fun `khong AlertDialog, khong o go gio, khong hop con chon gio`() {
        all.forEach { assertFalse("AlertDialog" in it, "khung BYD quay lại") }
        listOf(nav, wheel).forEach { code ->
            listOf("EditText", "InputType", "TimePickerDialog", "TimePicker", "NumberPicker").forEach {
                assertFalse(it in code, "hộp lịch có `$it` — R12: chọn giờ trong hộp, không bàn phím, không hộp con")
            }
        }
        assertTrue("TimeWheel(" in nav, "hộp lịch phải dùng TimeWheel")
        assertTrue("KachiDialogs.form(" in nav && "KachiDialogs.form(" in place, "hai hộp đi qua khuôn Biểu mẫu")
    }

    @Test
    fun `kiem bang dung luat cua duong luu`() {
        val navAsk = SourceRoots.body(nav, "fun ask(")
        assertTrue("ScheduledNavRules.of(" in navAsk, "Lưu của hộp lịch khoá theo ScheduledNavRules.of — không chép luật")
        assertTrue("PlaceForm.check(" in SourceRoots.body(place, "fun ask("), "hộp địa chỉ kiểm qua PlaceForm (:core)")
        assertTrue("coords.error =" in place, "toạ độ hỏng ⇒ lỗi TẠI CHỖ dưới ô")
        assertTrue("SettingsPlaceDialog.ask(" in SourceRoots.body(settings, "fun askPlace("), "askPlace uỷ quyền R11")
    }

    @Test
    fun `bo chon gio dung phep tinh core va nhan giu lap`() {
        assertTrue("TimeWheelMath.stepHour(" in wheel && "TimeWheelMath.stepMinute(" in wheel)
        assertTrue("TimeWheelMath.PRESETS" in wheel, "chip mốc nhanh lấy từ :core")
        assertTrue("SettingsRepeatTouch.attach(" in wheel, "▲▼ nhấn giữ lặp (R12)")
        assertTrue("dpi(context, Sp.TOUCH), dpi(context, Sp.TOUCH)" in SourceRoots.body(wheel, "private fun arrow("), "▲▼ ≥ 48dp")
    }

    @Test
    fun `dieu khien moi deu co cho goi`() {
        val users = nav + place + wheel
        listOf("chip(", "roundChip(", "flow(", "heading(", "selectCard(", "toggle(").forEach {
            assertTrue("KachiFormControls.$it" in users, "KachiFormControls.$it khai mà không ai gọi (CLAUDE.md §8)")
        }
    }

    @Test
    fun `khong luong, nhip hay listener nen`() {
        val banned = Regex("""\bThread\b|\bTimer\b|postDelayed|\bHandler\(|Executors|registerReceiver|addOnGlobalLayoutListener""")
        all.forEach { assertEquals(emptyList<String>(), banned.findAll(it).map { m -> m.value }.toList()) }
        all.forEach { assertFalse("setStroke" in it, "WP1 · R1.1 — không viền") }
    }

    @Test
    fun `chu du 5 ngon ngu, cung tap khoa, cung tham so, deu duoc dung`() {
        val key = Regex("""<string name="(kform_\w+)">([^<]*)</string>""")
        val folders = listOf("values", "values-en", "values-zh-rCN", "values-th", "values-ms")
        val maps = folders.associateWith { f ->
            key.findAll(SourceRoots.text("src/main/res/$f/strings_kachi_form301.xml"))
                .associate { it.groupValues[1] to it.groupValues[2] }
        }
        val base = maps.getValue("values")
        assertTrue(base.isNotEmpty())
        val arg = Regex("""%\d\$[sd]""")
        val viMarks = Regex("""[ăâđêôơưạảấầẩẫậắằẳẵặẹẻẽếềểễệỉịọỏốồổỗộớờởỡợụủứừửữựỳỵỷỹ]""", RegexOption.IGNORE_CASE)
        folders.forEach { f ->
            val m = maps.getValue(f)
            assertEquals(base.keys, m.keys, "$f lệch tập khoá với values/")
            base.forEach { (k, v) ->
                assertEquals(arg.findAll(v).map { it.value }.toSet(), arg.findAll(m.getValue(k)).map { it.value }.toSet(), "$f/$k lệch tham số")
            }
            if (f != "values") m.forEach { (k, v) -> assertFalse(viMarks.containsMatchIn(v), "$f/$k còn dấu tiếng Việt: $v") }
        }
        val code = all.joinToString("\n")
        base.keys.forEach { assertTrue("R.string.$it" in code, "khoá $it khai mà không dùng") }
    }
}
