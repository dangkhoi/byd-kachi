package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import java.nio.file.Files
import kotlin.streams.toList
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ KACHI-301 R8/R9 — khung hộp thoại DUY NHẤT (`KachiDialog`) + bốn khuôn (`KachiDialogs`) ═══════════════════
 *
 * Khoá năm điều mà giai đoạn 1 hứa:
 *  1. Khung + khuôn + `SettingsDialogs` KHÔNG còn `AlertDialog` (khung BYD, bỏ qua bảng Kachi). Phạm vi chỉ ba tệp
 *     này — 6 tệp còn lại chuyển ở giai đoạn 2 rồi bài này mới mở rộng ra toàn gói (spec §4.3).
 *  2. An toàn R8: Activity đang đóng/đã huỷ ⇒ không show; `BadTokenException` bị bắt.
 *  3. Vẽ theo bảng Kachi đang dùng: thẻ `KachiTheme.surface` bo `RADIUS_XXL`, cửa sổ trong suốt, nút ≥ `Sp.TOUCH`.
 *  4. Mọi hàm của `SettingsDialogs` đi qua khuôn, và mỗi khuôn CÓ chỗ gọi (CLAUDE.md §8 — hàm viết xong mà không ai
 *     gọi là hàm chết).
 *  5. Chữ của khung đủ 5 thư mục ngôn ngữ, cùng tập khoá; 0 luồng/nhịp nền (memory perf-every-fix).
 */
class KachiDialogContractTest {

    private val dir = "src/main/java/com/byd/clusternav/launcher"
    private val frame by lazy { SourceRoots.codeOf("$dir/KachiDialog.kt") }
    private val patterns by lazy { SourceRoots.codeOf("$dir/KachiDialogPatterns.kt") }
    private val settings by lazy { SourceRoots.codeOf("$dir/SettingsDialogs.kt") }

    @Test
    fun `khung, khuon va SettingsDialogs khong con AlertDialog`() {
        mapOf("KachiDialog.kt" to frame, "KachiDialogPatterns.kt" to patterns, "SettingsDialogs.kt" to settings)
            .forEach { (name, code) -> assertFalse("AlertDialog" in code, "$name còn AlertDialog — khung BYD quay lại") }
    }

    @Test
    fun `khong show khi Activity da dong va bat BadTokenException`() {
        val show = SourceRoots.body(frame, "fun show(")
        assertTrue("act.isFinishing" in show && "act.isDestroyed" in show, "Activity đang đóng/đã huỷ ⇒ không show")
        assertTrue("catch (e: WindowManager.BadTokenException)" in show, "token chết giữa chừng ⇒ bắt, không văng")
        assertTrue(Regex("""isDestroyed\)\) return null""").containsMatchIn(show), "kiểm TRƯỚC khi dựng Dialog")
    }

    @Test
    fun `ve theo bang Kachi dang dung, khong theo khung ROM`() {
        assertTrue("KachiTheme.surface(ctx, Sp.RADIUS_XXL)" in frame, "thẻ hộp = bề mặt Kachi bo RADIUS_XXL")
        assertTrue("setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))" in frame, "cửa sổ trong suốt — bỏ khung ROM")
        assertTrue("minHeight = dpi(ctx, Sp.TOUCH)" in SourceRoots.body(frame, "private fun button("), "nút ≥ 48 dp")
        assertTrue("KachiTheme.gradient(ctx, Sp.RADIUS_PILL)" in frame, "nút chính = gradient")
        assertTrue("KachiTheme.pill(ctx, KachiTheme.RED_SOFT)" in frame, "nút nguy hiểm = nền đỏ nhạt")
        assertTrue("KachiTheme.night" in SourceRoots.body(frame, "fun show("), "theme cửa sổ theo Sáng/Tối của Kachi")
        assertFalse("setStroke" in frame || "setStroke" in patterns, "WP1 · R1.1 — không viền")
    }

    @Test
    fun `moi ham SettingsDialogs di qua khuon KachiDialogs`() {
        listOf("fun pick(", "fun notice(", "fun confirm(", "fun offer(", "fun askName(", "fun askText(")
            .forEach { sig ->
                assertTrue("KachiDialogs." in SourceRoots.body(settings, sig), "SettingsDialogs.$sig phải đi qua khuôn")
            }
        // R11 (giai đoạn 2): askPlace uỷ quyền cho SettingsPlaceDialog — tệp đó đi qua khuôn Biểu mẫu
        // (KachiFormContractTest canh phía bên kia).
        assertTrue("SettingsPlaceDialog.ask(" in SourceRoots.body(settings, "fun askPlace("), "askPlace phải uỷ quyền R11")
    }

    @Test
    fun `bon khuon deu co cho goi`() {
        val sources = launcherSources().filter { it.fileName.toString() != "KachiDialogPatterns.kt" }
            .joinToString("\n") { SourceRoots.codeOf("$dir/${it.fileName}") }
        listOf("pick", "form", "confirm", "notice", "field").forEach { fn ->
            assertTrue("KachiDialogs.$fn(" in sources, "KachiDialogs.$fn khai mà không ai gọi (CLAUDE.md §8)")
        }
        assertTrue("validate?.let { d.validate(it) }" in patterns, "biểu mẫu chuyển hàm kiểm vào khung")
        assertTrue("watchEdits(bodyViews)" in frame, "nút chính khoá/bật theo chữ trong ô")
    }

    @Test
    fun `khong luong, nhip hay listener nen`() {
        val banned = Regex("""\bThread\b|\bTimer\b|postDelayed|\bHandler\(|Executors|registerReceiver|addOnGlobalLayoutListener""")
        listOf(frame, patterns).forEach { assertEquals(emptyList<String>(), banned.findAll(it).map { m -> m.value }.toList()) }
    }

    @Test
    fun `chu cua khung du 5 ngon ngu, cung tap khoa, deu duoc dung`() {
        val key = Regex("""<string name="(kdlg_\w+)">([^<]*)</string>""")
        val folders = listOf("values", "values-en", "values-zh-rCN", "values-th", "values-ms")
        val maps = folders.associateWith { f ->
            key.findAll(SourceRoots.text("src/main/res/$f/strings_kachi_dialog301.xml"))
                .associate { it.groupValues[1] to it.groupValues[2] }
        }
        val base = maps.getValue("values").keys
        assertTrue(base.isNotEmpty())
        folders.forEach { assertEquals(base, maps.getValue(it).keys, "$it lệch tập khoá với values/") }
        val viMarks = Regex("""[ăâđêôơưạảấầẩẫậắằẳẵặẹẻẽếềểễệỉịọỏốồổỗộớờởỡợụủứừửữựỳỵỷỹ]""", RegexOption.IGNORE_CASE)
        maps.filterKeys { it != "values" }.forEach { (f, m) ->
            m.forEach { (k, v) -> assertFalse(viMarks.containsMatchIn(v), "$f/$k còn dấu tiếng Việt: $v") }
        }
        val code = frame + patterns
        base.forEach { assertTrue("R.string.$it" in code, "khoá $it khai mà không dùng") }
    }

    /**
     * Stage 2-I: hai tính năng khung + chỗ gọi THẬT (CLAUDE.md §8).
     *  - `Handle.setSubtitle` ⇒ hộp Lịch dẫn đường cập nhật tóm tắt sống qua API, không còn dò TextView theo nội dung
     *    (`findText`: dòng phụ trùng chữ ở chỗ khác ⇒ sửa nhầm view).
     *  - `fullScreen()` + `pinned(...)` ⇒ #14 Dạy tên trở lại như trước R13: ô tìm + chip lọc ĐỨNG YÊN, chỉ danh sách
     *    cuộn, trang cao hết màn (D4 làm hồi quy: cả cột cuộn, cao theo nội dung tới 0,86).
     */
    @Test
    fun `dong phu song va trang toan man co cho goi that`() {
        assertTrue("fun setSubtitle(" in frame && "fun pinned(" in frame && "fun fullScreen(" in frame)
        val show = SourceRoots.body(frame, "fun show(")
        assertTrue("ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT" in show, "trang = cửa sổ phủ màn")
        val auto = SourceRoots.codeOf("$dir/SettingsNavAutomationDialog.kt")
        assertTrue("handle?.setSubtitle(summary())" in auto, "tóm tắt sống qua Handle")
        assertFalse("findText" in auto, "không dò TextView theo nội dung nữa")
        val open = SourceRoots.body(SourceRoots.codeOf("$dir/SettingsVoiceNamesPage.kt"), "fun open(")
        assertTrue(".fullScreen()" in open && ".pinned(root)" in open && ".body(list)" in open,
            "#14: ô tìm + chip ghim, chỉ danh sách cuộn, trang toàn màn")
        assertFalse("root.addView(list)" in open, "danh sách không được nằm trong vùng ghim")
    }

    private fun launcherSources() = Files.list(SourceRoots.path(dir)).toList()
        .filter { it.fileName.toString().endsWith(".kt") }
}
