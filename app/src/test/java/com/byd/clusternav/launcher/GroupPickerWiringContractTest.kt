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
    private val dock by lazy { code("src/main/java/com/byd/clusternav/launcher/ControlDockView.kt") }

    /** Đọc source rồi **bỏ chú thích**: bài này canh CODE, không canh văn xuôi (KDoc nhắc chính token đang soi). */
    private fun code(relative: String): String = SourceRoots.codeOf(relative)

    /**
     * VÙNG DỰNG BỘ CHỌN của ngăn kéo = `init` + hai hàm mục.
     *
     * ⚠⚠ T6 (R-UI (m)) tách thân bảng thành `groupSection` / `singlesSection` để **chế độ thứ ba** (chọn nút cho
     * thanh nút xe) dùng lại được thay vì chép — nếu chép thì hai thân bảng sẽ lệch đúng lúc ai đó thêm một lĩnh
     * vực, tức là đẻ lại chính bệnh mà cả tệp này đi canh. Bài vì thế phải nối ba vùng thay vì đọc mỗi `init`.
     *
     * **Không** dùng vùng nối này để so THỨ TỰ: thứ tự trong chuỗi nối là thứ tự tôi ghép, không phải thứ tự chạy.
     * Thứ tự thật do `ngan keo dat muc NHOM TRUOC moi linh vuc` chốt trên chính lời gọi trong `init`.
     */
    private fun drawerPicker(): String =
        listOf("init {", "private fun groupSection(", "private fun singlesSection(")
            .joinToString("\n") { SourceRoots.body(drawer, it) }

    // ── 1 · Cả hai màn chọn đều BÀY NHÓM ─────────────────────────────────────────────────────────

    @Test
    fun `ngan keo bay muc NHOM`() {
        val fn = drawerPicker()
        assertTrue(fn.contains("CapabilityPicker.groupPicks()"), "ngăn kéo phải bày 12 ô nhóm")
        assertTrue(fn.contains("CapabilityPicker.GROUPS_TITLE"), "và có tiêu đề mục nói rõ 'xem cả cụm cùng lúc'")
        assertTrue(fn.contains("CapabilityPicker.SINGLES_TITLE"), "và tiêu đề phần 'Từng mục riêng' cho phần sau")
    }


    // ── 2 · NHÓM ĐỨNG TRƯỚC (đây là bất biến chính của T4) ───────────────────────────────────────

    /**
     * ⚠ T6 — thứ tự nay đọc ở **lời gọi trong `init`** (`groupSection` → `singlesSection` → danh sách app), rồi mới
     * xác nhận từng hàm đúng là mục nó mang tên. Đây là bằng chứng MẠNH HƠN bản cũ (so hai chỉ số trong một thân
     * hàm dài): nó chốt cả thứ tự LẪN việc hai mục không bị hoán nội dung cho nhau.
     */
    @Test
    fun `ngan keo dat muc NHOM TRUOC moi linh vuc`() {
        val init = SourceRoots.body(drawer, "init {")
        // ⚠⚠ Cắt đúng NHÁNH gán-ô, không đọc cả `init`: từ T6 `init` có hai nhánh cùng gọi hai hàm mục (nhánh dock
        // đứng trước), nên `indexOf` trên cả thân sẽ luôn tìm thấy lời gọi của nhánh KIA và bài không thể đỏ.
        // [ĐO] thử phá: đổi lời gọi đầu nhánh gán-ô thành `singlesSection` ⇒ bản đọc-cả-init vẫn XANH.
        val marker = "} else if (assign) {"
        assertTrue(marker in init, "không còn nhánh gán-ô trong init — bài đang quét vùng KHÔNG tồn tại")
        val branch = init.substringAfter(marker)
        val groupAt = branch.indexOf("groupSection(body)")
        val singlesAt = branch.indexOf("singlesSection(body)")
        val appsAt = branch.indexOf("apps.load()")
        assertTrue(groupAt >= 0 && singlesAt >= 0 && appsAt >= 0, "phải có cả ba phần")
        assertTrue(
            groupAt < singlesAt,
            "mục Nhóm phải dựng TRƯỚC vòng lặp lĩnh vực — nằm sau là người dùng phải cuộn qua hàng chục ô rời mới thấy",
        )
        // #7 (owner 2026-09-21): App đứng ĐẦU (App → Widget app → Thông tin khác: nhóm/thẻ dựng tay/mục lẻ).
        assertTrue(appsAt < groupAt, "App phải đứng TRƯỚC khối Thông tin khác (nhóm/mục lẻ)")
        assertTrue(
            SourceRoots.body(drawer, "private fun groupSection(").contains("CapabilityPicker.groupPicks()"),
            "và `groupSection` đúng là mục NHÓM",
        )
        assertTrue(
            SourceRoots.body(drawer, "private fun singlesSection(").contains("CapabilityCatalog.byDomain()"),
            "còn `singlesSection` đúng là vòng lặp LĨNH VỰC",
        )
    }

    /**
     * ⚠ T4 · IA v2 R-UI (m) — bài `man Cai dat dat muc NHOM TRUOC moi linh vuc` đã **XOÁ**, không phải làm yếu đi:
     * màn Cài đặt không còn dựng lưới ô nào cho thanh nút xe (nó mở `AppDrawer.Mode.PICK_DOCK`), nên thứ tự "nhóm
     * trước lĩnh vực" ở đó không còn đối tượng để canh. Tính chất ấy vẫn được canh — ở ngăn kéo, bài
     * `ngan keo dat muc NHOM TRUOC moi linh vuc` ngay phía trên, trên đúng bề mặt người dùng thật sự thấy.
     */
    @Test
    fun `man Cai dat mo bo chon cua ngan keo, khong dung luoi thu hai`() {
        val bars = code("src/main/java/com/byd/clusternav/launcher/SettingsSectionsBars.kt")
        assertTrue(bars.contains("deps.openDockPicker("), "nhóm thanh nút phải MỞ bộ chọn của ngăn kéo")
        assertFalse(bars.contains("CapabilityPicker.groupPicks()"), "và KHÔNG được tự dựng lưới ô nhóm lần nữa")
        assertFalse(bars.contains("CapabilityCatalog.byDomain()"), "cũng không tự duyệt lĩnh vực lần nữa")
    }

    @Test
    fun `tieu de phan muc roi nam GIUA nhom va linh vuc dau tien`() {
        // Không có nó thì 12 ô nhóm và lĩnh vực đầu tiên dán liền nhau và người dùng không biết đã sang phần khác.
        // ⚠ T6: ở ngăn kéo, "Từng mục riêng" và vòng lặp lĩnh vực nay cùng nằm trong `singlesSection`, còn mục Nhóm
        // ở `groupSection` được gọi TRƯỚC (bài `ngan keo dat muc NHOM TRUOC moi linh vuc`). Nên chỉ còn phải chốt
        // rằng TIÊU ĐỀ đứng trước vòng lặp bên trong hàm đó.
        // T4 · R-UI (m): chỉ còn ngăn kéo dựng lưới này (xem bài ngay trên).
        listOf(
            "ngăn kéo" to SourceRoots.body(drawer, "private fun singlesSection("),
        ).forEach { (who, fn) ->
            val singlesAt = fn.indexOf("CapabilityPicker.SINGLES_TITLE")
            val domainsAt = fn.indexOf("CapabilityCatalog.byDomain()")
            assertTrue(singlesAt >= 0 && domainsAt >= 0, "$who: phải có cả tiêu đề lẫn vòng lặp lĩnh vực")
            assertTrue(singlesAt < domainsAt, "$who: thứ tự phải là 'Từng mục riêng' → lĩnh vực")
        }
    }

    // ── 3 · Không màn nào bày nhóm HAI LẦN ───────────────────────────────────────────────────────

    @Test
    fun `hai man chon deu LOC nhom khoi linh vuc`() {
        // ⚠ Bày nhóm ở mục riêng RỒI vẫn để nó trong lĩnh vực = **hai ô cùng một mã** ⇒ `widgetTiles[id]` /
        // `tiles[id]` bị ghi đè ⇒ chỉ ô sau được tô sáng, ô trước nói SAI cấu hình. Đúng ba lỗi cùng lúc của RW0.
        val dr = drawerPicker()
        assertTrue(
            dr.contains("CapabilityPicker.singlesOf(picks)"),
            "ngăn kéo phải lọc nhóm khỏi lĩnh vực, không thì cùng một mã có hai ô",
        )
        assertFalse(
            Regex("""addPickGrid\(body,\s*picks\s*,""").containsMatchIn(dr),
            "ngăn kéo không được dựng lưới từ danh sách CHƯA lọc",
        )
        // T4 · R-UI (m): nhánh "màn Cài đặt" đã bỏ — nó không còn lưới nào để bày nhóm hai lần.
    }

    // ── 4 · Ô nhóm nói nó GỒM GÌ ─────────────────────────────────────────────────────────────────

    @Test
    fun `o nhom hien dong phu o CA HAI man chon`() {
        // U6: đọc `displaySub` chứ không đọc `sub` GỐC — cùng một dòng chữ nay chở thêm gợi ý loại ("xem"/"bấm")
        // vừa được chuyển ra khỏi NHÃN CHÍNH, và `displaySub` là chỗ duy nhất ghép hai mảnh đó (ở `:core`).
        assertTrue(
            drawer.contains("pick.displaySub"),
            "ngăn kéo phải hiện dòng phụ của nhóm — không thì người dùng thấy ô 'Lốp' mà vẫn phải đoán bên trong có gì",
        )
        // ⚠ T4 · R-UI (m): nhánh "lưới của màn Cài đặt" đã bỏ — `CapabilityGridSection` bị XOÁ cùng lúc với lưới
        // 123 ô trong Settings. Ngăn kéo nay là bề mặt DUY NHẤT bày ô nhóm, nên nó cũng là chỗ duy nhất phải canh.
        // Dòng phụ đến từ `:core`; tầng vẽ KHÔNG được tự ghép số thành viên (đó là bản sao thứ hai).
        listOf("drawer" to drawer).forEach { (who, src) ->
            assertFalse(src.contains(".reads.size"), "$who không được tự đếm thành viên")
            assertFalse(src.contains(".writes.size"), "$who không được tự đếm nút")
            assertFalse(src.contains("contentLine"), "$who chỉ đọc pick.displaySub, phép ghép nằm ở :core")
        }
    }

    @Test
    fun `goi y nhom hien o dau tung linh vuc, KHONG hien trong tung o`() {
        // T4 · R-UI (m): chỉ còn ngăn kéo bày lưới ô nhóm; nhánh "Cài đặt" (`SettingsSectionsHome.dock`) đã bỏ.
        listOf("ngăn kéo" to drawerPicker()).forEach { (who, fn) ->
            assertTrue(fn.contains("CapabilityPicker.groupHint(picks)"), "$who phải gợi ý nhóm chứa mục rời")
        }
        // Trong Ô thì KHÔNG: [ĐO] 88/123 datum thuộc nhóm ⇒ thêm một dòng vào từng ô là 88 dòng chữ trong lưới ô nhỏ.
        listOf("drawer" to drawer).forEach { (who, src) ->
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
