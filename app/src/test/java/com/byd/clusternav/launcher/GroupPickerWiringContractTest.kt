package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * G1 · T4 — khoá **DÂY NỐI** của "người dùng gặp nhóm trước": hai màn chọn phải bày mục Nhóm **TRƯỚC** mục rời, và
 * không màn nào được bày nhóm hai lần.
 *
 * ## Vì sao khoá THỨ TỰ chứ không chỉ khoá sự tồn tại
 * Nhóm nằm đâu đó trong danh sách thì yêu cầu R2 coi như KHÔNG đạt: [ĐO] lĩnh vực đầu tiên (Năng lượng) một mình đã
 * có 32 ô rời, nên một mục Nhóm nằm sau nó là *"phải cuộn qua cả trăm ô mới thấy"* — đúng cái owner phàn nàn. Vì thế
 * bài này so **vị trí** trong mã nguồn, không chỉ hỏi "có gọi hàm đó không".
 *
 * ## Vì sao vẫn phải quét mã nguồn
 * Thứ tự dựng view là hành vi của Android View, mà dự án không dùng Robolectric. Phần quyết định được (danh sách,
 * thứ tự trong danh sách, câu chữ) đã có `CapabilityPickerTest` ở `:core` canh — bài này chỉ canh chỗ **ghép**.
 *
 * ⚠ Mọi phép cắt vùng đi qua [SourceRoots.body] — nó **tự nổ** nếu mốc không tồn tại. Bản cũ dùng
 * `substringAfter/Before` với mốc kết không nằm sau mốc đầu nên quét tràn tới hết tệp (14 bài đã chứng minh là test
 * giả).
 */
class GroupPickerWiringContractTest {

    private val drawer by lazy { code("src/main/java/com/byd/clusternav/launcher/AppDrawer.kt") }
    private val home by lazy { code("src/main/java/com/byd/clusternav/launcher/SettingsSectionsHome.kt") }
    private val grid by lazy { code("src/main/java/com/byd/clusternav/launcher/CapabilityGridSection.kt") }
    private val dock by lazy { code("src/main/java/com/byd/clusternav/launcher/ControlDockView.kt") }

    /** Đọc source rồi **bỏ chú thích**: bài này canh CODE, không canh văn xuôi (KDoc nhắc chính token đang soi). */
    private fun code(relative: String): String = SourceRoots.codeOf(relative)

    // ── 1 · Cả hai màn chọn đều BÀY NHÓM ─────────────────────────────────────────────────────────

    @Test
    fun `ngan keo bay muc NHOM`() {
        val fn = SourceRoots.body(drawer, "init {")
        assertTrue(fn.contains("CapabilityPicker.groupPicks()"), "ngăn kéo phải bày 12 ô nhóm")
        assertTrue(fn.contains("CapabilityPicker.GROUPS_TITLE"), "và có tiêu đề mục nói rõ 'xem cả cụm cùng lúc'")
        assertTrue(fn.contains("CapabilityPicker.SINGLES_TITLE"), "và tiêu đề phần 'Từng mục riêng' cho phần sau")
    }

    @Test
    fun `man Cai dat bay muc NHOM`() {
        val fn = SourceRoots.body(home, "private fun dock(")
        assertTrue(fn.contains("CapabilityPicker.groupPicks()"), "màn Cài đặt phải bày 12 ô nhóm")
        assertTrue(fn.contains("CapabilityPicker.GROUPS_TITLE"), "và cùng tiêu đề với ngăn kéo (một nguồn câu chữ)")
        assertTrue(fn.contains("CapabilityPicker.SINGLES_TITLE"), "và cùng tiêu đề phần mục rời")
    }

    // ── 2 · NHÓM ĐỨNG TRƯỚC (đây là bất biến chính của T4) ───────────────────────────────────────

    @Test
    fun `ngan keo dat muc NHOM TRUOC moi linh vuc`() {
        val fn = SourceRoots.body(drawer, "init {")
        val groupAt = fn.indexOf("CapabilityPicker.groupPicks()")
        val domainsAt = fn.indexOf("CapabilityCatalog.byDomain()")
        val appsAt = fn.indexOf("loadApps()")
        assertTrue(groupAt >= 0 && domainsAt >= 0, "phải có cả hai phần")
        assertTrue(
            groupAt < domainsAt,
            "mục Nhóm phải dựng TRƯỚC vòng lặp lĩnh vực — nằm sau là người dùng phải cuộn qua hàng chục ô rời mới thấy",
        )
        assertTrue(groupAt < appsAt, "và trước cả danh sách ứng dụng")
    }

    @Test
    fun `man Cai dat dat muc NHOM TRUOC moi linh vuc`() {
        val fn = SourceRoots.body(home, "private fun dock(")
        val groupAt = fn.indexOf("CapabilityPicker.groupPicks()")
        val domainsAt = fn.indexOf("CapabilityCatalog.byDomain()")
        assertTrue(groupAt >= 0 && domainsAt >= 0, "phải có cả hai phần")
        assertTrue(groupAt < domainsAt, "mục Nhóm phải dựng TRƯỚC vòng lặp lĩnh vực")
    }

    @Test
    fun `tieu de phan muc roi nam GIUA nhom va linh vuc dau tien`() {
        // Không có nó thì 12 ô nhóm và lĩnh vực đầu tiên dán liền nhau và người dùng không biết đã sang phần khác.
        listOf("ngăn kéo" to SourceRoots.body(drawer, "init {"), "Cài đặt" to SourceRoots.body(home, "private fun dock(")).forEach { (who, fn) ->
            val groupAt = fn.indexOf("CapabilityPicker.groupPicks()")
            val singlesAt = fn.indexOf("CapabilityPicker.SINGLES_TITLE")
            val domainsAt = fn.indexOf("CapabilityCatalog.byDomain()")
            assertTrue(groupAt < singlesAt && singlesAt < domainsAt, "$who: thứ tự phải là Nhóm → 'Từng mục riêng' → lĩnh vực")
        }
    }

    // ── 3 · Không màn nào bày nhóm HAI LẦN ───────────────────────────────────────────────────────

    @Test
    fun `hai man chon deu LOC nhom khoi linh vuc`() {
        // ⚠ Bày nhóm ở mục riêng RỒI vẫn để nó trong lĩnh vực = **hai ô cùng một mã** ⇒ `widgetTiles[id]` /
        // `tiles[id]` bị ghi đè ⇒ chỉ ô sau được tô sáng, ô trước nói SAI cấu hình. Đúng ba lỗi cùng lúc của RW0.
        val dr = SourceRoots.body(drawer, "init {")
        assertTrue(
            dr.contains("CapabilityPicker.singlesOf(picks)"),
            "ngăn kéo phải lọc nhóm khỏi lĩnh vực, không thì cùng một mã có hai ô",
        )
        assertFalse(
            Regex("""addPickGrid\(body,\s*picks\s*,""").containsMatchIn(dr),
            "ngăn kéo không được dựng lưới từ danh sách CHƯA lọc",
        )
        val hm = SourceRoots.body(home, "private fun dock(")
        assertTrue(hm.contains("CapabilityPicker.singlesOf(picks)"), "màn Cài đặt cũng phải lọc")
        assertFalse(
            Regex("""addGrid\(body,\s*picks\s*,""").containsMatchIn(hm),
            "màn Cài đặt không được dựng lưới từ danh sách CHƯA lọc",
        )
    }

    // ── 4 · Ô nhóm nói nó GỒM GÌ ─────────────────────────────────────────────────────────────────

    @Test
    fun `o nhom hien dong phu o CA HAI man chon`() {
        assertTrue(
            drawer.contains("pick.sub"),
            "ngăn kéo phải hiện dòng phụ của nhóm — không thì người dùng thấy ô 'Lốp' mà vẫn phải đoán bên trong có gì",
        )
        assertTrue(grid.contains("pick.sub"), "lưới của màn Cài đặt cũng vậy")
        // Dòng phụ đến từ `:core`; tầng vẽ KHÔNG được tự ghép số thành viên (đó là bản sao thứ hai).
        listOf("drawer" to drawer, "grid" to grid).forEach { (who, src) ->
            assertFalse(src.contains(".reads.size"), "$who không được tự đếm thành viên")
            assertFalse(src.contains(".writes.size"), "$who không được tự đếm nút")
            assertFalse(src.contains("contentLine"), "$who chỉ đọc pick.sub, phép ghép nằm ở :core")
        }
    }

    @Test
    fun `goi y nhom hien o dau tung linh vuc, KHONG hien trong tung o`() {
        listOf("ngăn kéo" to SourceRoots.body(drawer, "init {"), "Cài đặt" to SourceRoots.body(home, "private fun dock(")).forEach { (who, fn) ->
            assertTrue(fn.contains("CapabilityPicker.groupHint(picks)"), "$who phải gợi ý nhóm chứa mục rời")
        }
        // Trong Ô thì KHÔNG: [ĐO] 88/123 datum thuộc nhóm ⇒ thêm một dòng vào từng ô là 88 dòng chữ trong lưới ô nhỏ.
        listOf("drawer" to drawer, "grid" to grid).forEach { (who, src) ->
            assertFalse(
                src.contains("groupsContaining"),
                "$who không được tra nhóm cho TỪNG ô — gợi ý đặt ở tiêu đề lĩnh vực để không làm ô chật thêm",
            )
        }
    }

    // ── 5 · Bày ra thì phải DÙNG ĐƯỢC (chống "lựa chọn chết") ────────────────────────────────────

    @Test
    fun `nhom dat len thanh nut thi o hien TOM TAT, khong hien dau gach mai mai`() {
        // ⚠⚠ Lưới của màn Cài đặt bật/tắt **thanh nút xe**. Mã nhóm không có trong TelemetryRegistry, nên nếu thanh
        // nút chỉ hỏi `TelemetryReadout.of` thì ô hiện "—" MÃI MÃI ⇒ T4 vừa bày ra một lựa chọn chết. Đây là chiều
        // ngược của bài học RW0: lần này đặt được, nhưng thứ đặt ra thì vô dụng.
        val fn = SourceRoots.body(dock, "private fun readout(")
        assertTrue(fn.contains("GroupBoard.summaryView("), "thanh nút phải hiểu mã nhóm")
        assertTrue(fn.contains("TelemetryReadout.of("), "và đường telemetry cũ phải còn nguyên")
        assertTrue(
            fn.indexOf("GroupBoard.summaryView(") < fn.indexOf("TelemetryReadout.of("),
            "nhóm xét TRƯỚC — cùng thứ tự với CapabilityCatalog.kindOf, không dựa vào việc mã tình cờ không trùng",
        )
        // Và tóm tắt phải do `:core` quyết; tầng vẽ không được tự nghĩ ra câu chữ thứ hai.
        assertFalse(dock.contains("cảnh báo"), "câu tóm tắt thuộc :core (GroupBoardModel.summary), không viết ở đây")
        assertEquals(
            1, Regex("""GroupBoard\.""").findAll(dock).count(),
            "thanh nút chỉ được hỏi :core ĐÚNG một chỗ",
        )
    }
}
