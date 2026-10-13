package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import java.nio.file.Files
import kotlin.streams.toList
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ QA-301 (máy ảo 2026-10-13) — khoá các lỗi hộp thoại đã sửa ở TẦNG KHUNG (`docs/diagnostics/qa-301-ui-2026-10-13.md`) ═══
 *
 *  - D1 [P1] lưới hồ sơ mất thẻ cuối khi số lẻ: [KachiDialogs.GridPlan] — mọi thẻ có mặt, mọi ô cùng chiều cao `MATCH_PARENT`.
 *  - D2 [P1] ô chữ vô hình ở bảng Sáng: khung tô MỌI `EditText`; không ô chữ trần nào ngoài [KachiDialogs.field].
 *  - P3 nút phụ mất hình ở bảng Sáng: [dialogControlFill] đo tách khỏi thẻ hộp ở cả hai bảng.
 *  - P3 tiêu đề mang "…" của nhãn nút: [KachiDialogSupport.cleanTitle].
 *  - P3 biểu mẫu mất chữ khi chạm ngoài; bàn phím rò; "Gõ tên…" không mở bàn phím.
 */
class KachiDialogQa301FixTest {

    private val dir = "src/main/java/com/byd/clusternav/launcher"
    private fun code(name: String) = SourceRoots.codeOf("$dir/$name")

    // ── D1 ────────────────────────────────────────────────────────────────────────────────────────────────

    /**
     * Lỗi thật: 5 hồ sơ ⇒ 4 thẻ hiện. Hàng lẻ = thẻ `MATCH_PARENT` + ô đệm cao 0 ⇒ trong `ScrollView` hàng cao 0. Bài này đỏ
     * khi một ô (thẻ hay đệm) của bất kỳ hàng nào lệch chiều cao với ô khác, hoặc khi có thẻ rơi khỏi kế hoạch.
     */
    @Test
    fun `luoi 2 cot giu moi the voi moi so luong, hang le cung chieu cao`() {
        for (count in 1..13) {
            val rows = KachiDialogs.GridPlan.rows(count, 2)
            assertEquals((0 until count).toList(), rows.flatten().mapNotNull { it.index }, "count=$count: thẻ rơi/lặp")
            rows.forEach { row ->
                assertEquals(2, row.size, "count=$count: hàng thiếu ô đệm")
                assertEquals(setOf(KachiDialogs.GridPlan.CELL_HEIGHT), row.map { it.height }.toSet(),
                    "count=$count: ô cùng hàng khác chiều cao ⇒ hàng lẻ co về 0 trong ScrollView")
            }
        }
        assertEquals(android.view.ViewGroup.LayoutParams.MATCH_PARENT, KachiDialogs.GridPlan.CELL_HEIGHT)
    }

    @Test
    fun `luoi dung ke hoach thuan, khong tu che o dem`() {
        val patterns = code("KachiDialogPatterns.kt")
        assertTrue("GridPlan.rows(" in patterns, "bộ dựng lưới phải đi qua kế hoạch thuần (bài trên canh nó)")
        assertTrue("LinearLayout.LayoutParams(0, cell.height, 1f)" in SourceRoots.body(patterns, "private fun gridRow("))
        assertFalse("repeat(perRow" in patterns, "không dựng ô đệm riêng ngoài kế hoạch")
    }

    // ── D2 ────────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `moi o chu trong hop theo bang Kachi`() {
        val show = SourceRoots.body(code("KachiDialog.kt"), "fun show(")
        assertTrue("themeEdits(pinnedViews + bodyViews)" in show, "khung tô mọi ô chữ (cả vùng ghim)")
        val support = SourceRoots.body(code("KachiDialogSupport.kt"), "fun themeEdits(")
        listOf("setTextColor(c(KachiTheme.INK))", "setHintTextColor(c(KachiTheme.MUT))", "textCursorDrawable").forEach {
            assertTrue(it in support, "themeEdits thiếu $it")
        }
        // Ô chữ chỉ dựng ở đường chuẩn; VoiceTextConsole là hộp chết chờ owner gỡ (OQ3, KachiDialogMigrationContractTest).
        val allowed = setOf("KachiDialogPatterns.kt", "VoiceTextConsole.kt")
        val offenders = Files.list(SourceRoots.path(dir)).use { it.toList() }
            .filter { it.fileName.toString().endsWith(".kt") && it.fileName.toString() !in allowed }
            .filter { Regex("""\bEditText\(""").containsMatchIn(code(it.fileName.toString())) }
            .map { it.fileName.toString() }
        assertEquals(emptyList<String>(), offenders, "ô chữ trần — dùng KachiDialogs.field")
        assertTrue("KachiDialogs.field(" in SourceRoots.body(code("SettingsVoiceNamesPage.kt"), "fun open("))
    }

    // ── nền điều khiển phụ ─────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `nen dieu khien phu thay hinh tren the hop, chu doc duoc, ca hai bang`() {
        val bad = mutableListOf<String>()
        listOf("TỐI" to KachiPalette.DARK, "SÁNG" to KachiPalette.LIGHT).forEach { (name, p) ->
            val fill = ColorMath.parse(dialogControlFill(p))
            listOf("surfFrom" to p.surfFrom, "surfTo" to p.surfTo).forEach { (role, hex) ->
                val card = ColorMath.parse(hex)
                val onCard = ColorMath.over(fill, card)
                val step = ColorMath.ratio(onCard, card)
                if (step < CONTROL_MIN_STEP) bad += "$name: điều khiển/$role = ${"%.3f".format(step)}"
                listOf("ink" to p.ink, "mut" to p.mut).forEach { (ink, inkHex) ->
                    val r = ColorMath.ratio(ColorMath.parse(inkHex), onCard)
                    if (r < 4.5) bad += "$name: $ink trên điều khiển ($role) = ${"%.2f".format(r)}"
                }
            }
        }
        assertEquals(emptyList<String>(), bad)
        // Hồi quy QA: bảng Sáng KHÔNG được lấy cardFill (#ffffff = đúng màu thẻ hộp).
        assertNotEquals(KachiPalette.LIGHT.cardFill, dialogControlFill(KachiPalette.LIGHT))
    }

    @Test
    fun `dieu khien phu trong hop dung nen do duoc`() {
        assertTrue("KachiTheme.pill(ctx, controlFill())" in SourceRoots.body(code("KachiDialog.kt"), "private fun button("))
        listOf("KachiDialogPatterns.kt", "KachiFormControls.kt", "TimeWheel.kt", "SettingsVoiceNamesPage.kt").forEach {
            assertTrue("KachiDialogSupport.controlFill()" in code(it), "$it chưa dùng nền điều khiển phụ")
        }
    }

    // ── tiêu đề · biểu mẫu · bàn phím ─────────────────────────────────────────────────────────────────────

    @Test
    fun `tieu de bo dau ba cham cua nhan nut`() {
        assertEquals("Thêm địa chỉ", KachiDialogSupport.cleanTitle("Thêm địa chỉ…").toString())
        assertEquals("Add place", KachiDialogSupport.cleanTitle("Add place... ").toString())
        assertEquals("Dạy tên app", KachiDialogSupport.cleanTitle("Dạy tên app").toString())
        assertTrue("title = cleanTitle(text)" in code("KachiDialog.kt"))
    }

    @Test
    fun `bieu mau khong dong khi cham ngoai, Back van huy`() {
        val frame = code("KachiDialog.kt")
        assertTrue("dialog.setCanceledOnTouchOutside(cancelable && !form)" in frame)
        assertTrue("dialog.setCancelable(cancelable)" in frame, "Back vẫn huỷ")
        assertTrue("d.form()" in SourceRoots.body(code("KachiDialogPatterns.kt"), "fun form("))
        assertTrue(".form()" in SourceRoots.body(code("SettingsVoiceNamesDialog.kt"), "fun open("), "hộp Dạy tên là biểu mẫu")
    }

    @Test
    fun `ban phim khong ro va mo ngay khi nguoi dung chon go`() {
        val frame = code("KachiDialog.kt")
        assertTrue("SOFT_INPUT_STATE_ALWAYS_HIDDEN" in SourceRoots.body(frame, "fun show("), "hộp không ô chữ ⇒ ẩn bàn phím")
        assertTrue("hideSoftInputFromWindow" in frame, "đóng hộp ⇒ gỡ bàn phím")
        assertTrue("other.requestInput()" in code("SettingsPlaceDialog.kt"), "chip Khác ⇒ bàn phím lên")
        assertTrue("showKeyboard = true" in SourceRoots.body(code("SettingsVoiceNamesDialog.kt"), "private fun typeName("))
        assertTrue("showKeyboard = showKeyboard" in SourceRoots.body(code("SettingsDialogs.kt"), "fun askName("))
    }

    @Test
    fun `the dang dung khong lap chu dang dung`() {
        assertFalse("kachi_profile_pick_active" in code("ProfileChip.kt"))
        assertTrue("maxLines = 1" in SourceRoots.body(code("KachiDialogPatterns.kt"), "private fun pickCard("))
    }
}
