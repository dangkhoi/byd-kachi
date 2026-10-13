package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.KotlinSource
import com.byd.clusternav.testsupport.SourceRoots
import java.nio.file.Files
import kotlin.streams.toList
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ KACHI-301 R13 (giai đoạn 2 · D4) — mọi hộp thoại SỐNG đi qua `KachiDialog` ═══════════════════════════════════
 *
 * Khoá bốn điều:
 *  1. `AlertDialog` bị CẤM trong MÃ (đã bóc chú thích) của toàn `app/src/main/java` — trừ danh sách ngoại lệ tường minh
 *     dưới đây, mỗi mục kèm lý do. Giai đoạn 2 xong: `SettingsNavAutomationDialog` đã bỏ AlertDialog (D3) nên không còn trong danh sách.
 *  2. Các hộp D4 chuyển giữ HÀNH VI: Lưu của hộp dạy không tự đóng + bật/khoá qua `setPrimaryEnabled`; đóng hộp dạy/thử ⇒
 *     huỷ lượt nghe; xoá tên đã dạy = xác nhận PHÁ HUỶ; bản mới = xác nhận với nhãn "Để sau"; miễn trừ đặt cờ TRƯỚC khi hiện.
 *  3. Chuỗi gán phím (#8) đi qua MỘT chỗ dựng hộp Chọn có dòng "Bước n/3".
 *  4. Chữ mới (`kmisc_`) đủ 5 thư mục, cùng tập khoá, đều được dùng.
 */
class KachiDialogMigrationContractTest {

    private val root = "src/main/java/com/byd/clusternav"
    private val dir = "$root/launcher"
    private fun code(rel: String) = SourceRoots.codeOf(rel)

    /**
     * Ngoại lệ còn `AlertDialog` — TÊN TỆP → lý do. Thêm mục mới vào đây là một quyết định có lý do, không phải lối tắt.
     */
    private val allowed = mapOf(
        "CapTestConsole.kt" to "hộp chết (không chỗ gọi) — owner OQ3: giữ nguyên, không đụng",
        "VoiceTextConsole.kt" to "hộp chết (không chỗ gọi) — owner OQ3: giữ nguyên, không đụng",
    )

    @Test
    fun `khong con AlertDialog trong ma song ngoai danh sach ngoai le`() {
        val files = Files.walk(SourceRoots.path(root)).use { s -> s.toList() }
            .filter { it.fileName.toString().endsWith(".kt") }
        assertTrue(files.size > 100, "quét hụt thư mục nguồn (${files.size} tệp)")
        val offenders = files.filter { it.fileName.toString() !in allowed }
            .filter { "AlertDialog" in KotlinSource.stripComments(it.toFile().readText()) }
            .map { it.fileName.toString() }
        assertEquals(emptyList<String>(), offenders, "dựng hộp bằng AlertDialog (khung ROM BYD) — dùng KachiDialog/KachiDialogs")
    }

    @Test
    fun `hop day giu hanh vi luu va huy luot nghe`() {
        val dlg = code("$dir/SettingsVoiceNamesDialog.kt")
        val open = SourceRoots.body(dlg, "fun open(")
        assertTrue("autoDismiss = false) { save() }" in open, "Lưu không đóng hộp (như nút dương gắn lại click cũ)")
        assertTrue(".onDismiss { relay.cancel(); onClosed(changed) }" in open, "đóng hộp ⇒ huỷ lượt nghe + báo trang")
        assertTrue("dialog?.setPrimaryEnabled(canSave && !port.readOnly())" in SourceRoots.body(dlg, "private fun repaint("))
        assertTrue("dialog?.setPrimaryEnabled(false)" in SourceRoots.body(dlg, "private fun commit("), "khoá Lưu khi đang soát")
        val tryOnce = SourceRoots.body(dlg, "fun tryOnce(")
        assertTrue(".onDismiss { relay.cancel() }" in tryOnce, "đóng hộp thử ⇒ huỷ lượt nghe")
        assertTrue(tryOnce.indexOf(".show() ?: return") in 0 until tryOnce.indexOf("relay.listen("),
            "hộp không mở được ⇒ không bắt đầu nghe")
    }

    @Test
    fun `trang day ten giu khung moi va xoa la pha huy`() {
        val page = code("$dir/SettingsVoiceNamesPage.kt")
        val open = SourceRoots.body(page, "fun open(")
        assertTrue(
            "KachiDialog(context)" in open && ".fullScreen()" in open && ".pinned(root)" in open,
            "trang toàn màn trên khung Kachi, ô tìm + chip ghim",
        )
        assertTrue(".onDismiss { if (dirty) deps.refreshSettings() }" in open, "có ghi ⇒ dựng lại trang Cài đặt khi đóng")
        assertFalse("ScrollView" in page, "thân KachiDialog đã cuộn — ScrollView lồng không cuộn được")
        assertTrue("destructive = true" in SourceRoots.body(page, "private fun confirmDelete("), "#19 xoá = phá huỷ")
    }

    @Test
    fun `ban moi va mien tru giu hanh vi`() {
        val update = code("$root/UpdateFlow.kt")
        assertTrue("KachiDialogs.confirm(" in update && "cancelLabel = Lang.t(\"Để sau\", \"Later\")" in update)
        assertTrue("{ doUpdate(activity, url, setStatus) }" in update, "Tải & cài ⇒ đúng luồng tải cũ")
        val disc = SourceRoots.body(code("$dir/KachiHomeDisclaimer.kt"), "internal fun Activity.maybeShowDisclaimer(")
        assertTrue(disc.indexOf("Prefs.setDisclaimerShown(this, true)") in 0 until disc.indexOf("KachiDialog(this)"),
            "cờ đặt TRƯỚC khi hiện — xoay màn không hiện lại")
        assertTrue("R.string.kachi_disclaimer_ok" in disc, "giữ nhãn nút riêng")
    }

    /**
     * QA-301 D3 (spec §4.3 #8 *"một hộp có thanh bước 1-2-3 + nút ←"*): cả chuỗi là MỘT hộp [KachiDialogs.steps] — không
     * còn hộp Chọn rời nào (bản trước: 5 lượt `keyPick` = 3 hộp chồng nhau, Back ở bước 3 đóng cả chuỗi).
     */
    @Test
    fun `chuoi gan phim la mot hop nhieu buoc`() {
        val keys = code("$dir/SettingsSectionsKeys.kt")
        assertFalse("SettingsDialogs.pick(" in keys || "KachiDialogs.pick(" in keys || "keyPick(" in keys, "không còn hộp Chọn rời")
        assertEquals(1, Regex("""KachiDialogs\.steps\(""").findAll(keys).count(), "đúng MỘT hộp nhiều bước")
        assertEquals(5, Regex("""KachiDialogs\.Step\(""").findAll(keys).count(), "5 bước: nút · loại đích · app · camera · việc xe")
        assertTrue("R.string.kmisc_keys_step" in SourceRoots.body(keys, "private fun addBinding("), "câu đọc của thanh bước")
        // Ghi CHỈ ở bước cuối ⇒ huỷ/lùi giữa chừng không ghi gì.
        assertEquals(1, Regex("""bridge\.addBinding\(""").findAll(keys).count())
    }

    @Test
    fun `chu kmisc du 5 ngon ngu cung tap khoa deu duoc dung`() {
        val key = Regex("""<string name="(kmisc_\w+)">([^<]*)</string>""")
        val folders = listOf("values", "values-en", "values-zh-rCN", "values-th", "values-ms")
        val maps = folders.associateWith { f ->
            key.findAll(SourceRoots.text("src/main/res/$f/strings_kachi_misc301.xml")).associate { it.groupValues[1] to it.groupValues[2] }
        }
        val base = maps.getValue("values").keys
        assertTrue(base.isNotEmpty())
        folders.forEach { assertEquals(base, maps.getValue(it).keys, "$it lệch tập khoá với values/") }
        val viMarks = Regex("""[ăâđêôơưạảấầẩẫậắằẳẵặẹẻẽếềểễệỉịọỏốồổỗộớờởỡợụủứừửữựỳỵỷỹ]""", RegexOption.IGNORE_CASE)
        maps.filterKeys { it != "values" }.forEach { (f, m) ->
            m.forEach { (k, v) -> assertFalse(viMarks.containsMatchIn(v), "$f/$k còn dấu tiếng Việt: $v") }
        }
        val all = Files.list(SourceRoots.path(dir)).use { s -> s.toList() }.filter { it.fileName.toString().endsWith(".kt") }
            .joinToString("\n") { code("$dir/${it.fileName}") }
        base.forEach { assertTrue("R.string.$it" in all, "khoá $it khai mà không dùng") }
    }
}
