package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Khoá DÂY NỐI của vòng kiểm quyền (P8 — spec `kachi-permission-preflight.html`).
 *
 * Quét SOURCE (không dựng được Activity trong JVM thuần); mọi phép quét đi qua [code] để **bỏ chú thích trước khi
 * kiểm** — nếu không thì viết tên hàm vào comment là test xanh, tức test tự lừa mình.
 */
class PermissionPreflightWiringContractTest {

    private fun code(relative: String): String =
        SourceRoots.text(relative)
            .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), " ")
            .lines().joinToString("\n") { it.substringBefore("//") }

    private val pre by lazy { code("src/main/java/com/byd/clusternav/launcher/PermissionPreflight.kt") }
    private val act by lazy { code("src/main/java/com/byd/clusternav/launcher/KachiHomeActivity.kt") }
    /** Nội dung nhóm "Hệ thống & quyền" của màn Cài đặt (S1·T3) — hàng quyền chuyển tới đây từ bảng "Tuỳ biến" cũ. */
    private val panel by lazy { code("src/main/java/com/byd/clusternav/launcher/SettingsSections.kt") }

    /** Dòng quyền — chuyển sang bộ dựng dòng dùng chung (S1·T2) để màn Cài đặt dùng cùng một hàng. */
    private val rows by lazy { code("src/main/java/com/byd/clusternav/launcher/SettingsRows.kt") }

    /** Chỗ GỌI màn Cài đặt — tách khỏi Activity sang [HomePanels] (Activity vượt trần 500 dòng). */
    private val panel_caller by lazy { code("src/main/java/com/byd/clusternav/launcher/HomePanels.kt") }

    // ── C4: đọc thì KHÔNG mở kênh shell ──────────────────────────────────────────────────────────

    @Test
    fun `doc trang thai KHONG duoc mo kenh shell`() {
        val readFns = SourceRoots.body(pre, "fun check(")
        assertFalse(readFns.contains("sh("),
            "Đọc trạng thái phải đọc THẲNG cấu hình hệ thống (mọi app đọc được). Mở phiên kênh shell chỉ để đọc là " +
                "tốn — code cũ đã ghi bài học này.")
        assertTrue(pre.contains("Settings.Secure.getString"), "phải đọc thẳng cấu hình hệ thống")
    }

    @Test
    fun `doc khong duoc thi tra null chu KHONG tra false`() {
        // Trả false ⇒ bị coi là THIẾU ⇒ đi xin lại vô cớ ⇒ nhiễu đúng lúc đang test trên xe.
        listOf("notificationListenerGranted", "accessibilityGranted", "overlayGranted", "freeformEnabled",
            "isDefaultHome").forEach { fn ->
            assertTrue(pre.contains("fun $fn"), "phải có hàm đọc $fn")
        }
        assertTrue(pre.contains(".getOrNull()"),
            "mọi phép đọc phải bọc lỗi thành null (ROM thiếu API) — không suy ra là thiếu")
    }

    // ── R4: tự xin lại, không hỏi ────────────────────────────────────────────────────────────────

    @Test
    fun `tu xin lai chi khi CO kenh shell va dang THIEU`() {
        val fn = SourceRoots.body(pre, "fun runAndReport(")
        assertTrue(fn.contains("selfFixable.isNotEmpty()"), "chỉ cấp khi thật sự đang thiếu")
        assertTrue(fn.contains("sh != null"), "chỉ cấp khi có kênh shell")
        assertTrue(fn.contains("selfGrant("), "phải dùng đường tự cấp tập trung")
    }

    @Test
    fun `tro nang phai APPEND chu khong ghi de danh sach`() {
        // Ghi đè sẽ TẮT trợ năng của app khác — kể cả của người khuyết tật đang dùng.
        assertTrue(pre.contains("fun accessibilityGrantCommands("), "phải có đường đọc-sửa-ghi riêng")
        val fn = SourceRoots.body(pre, "fun accessibilityGrantCommands(")
        assertTrue(fn.contains("existing"), "phải đọc danh sách đang có")
        assertTrue(fn.contains("+ comp") || fn.contains("existing + comp"), "phải APPEND vào danh sách đang có")
        assertTrue(pre.contains("READ_ACCESSIBILITY_CMD"), "chỗ gọi phải đọc trước khi ghi")
    }

    // ── R5: KHÔNG chặn launcher ──────────────────────────────────────────────────────────────────

    @Test
    fun `KHONG chan launcher vi thieu quyen`() {
        val fn = SourceRoots.body(pre, "fun runAndReport(")
        listOf("finish()", "startActivityForResult", "setContentView").forEach {
            assertFalse(fn.contains(it),
                "Launcher là màn hình CHÍNH của xe — chặn nó vì thiếu quyền là làm xe không dùng được ('$it')")
        }
    }

    @Test
    fun `van kiem quyen ngay ca khi KHONG co kenh shell`() {
        // Không có kênh shell là đúng ca người dùng cần biết NHẤT (app không vào được ô).
        assertTrue(act.contains("runAndReport(this, shellUsable = false"),
            "nhánh không có kênh shell vẫn phải chạy vòng kiểm")
        assertTrue(act.contains("runAndReport(this, shellUsable = true"), "nhánh có kênh shell cũng phải chạy")
    }

    // ── R2/R3: đủ thì im lặng, thiếu thì nói rõ ──────────────────────────────────────────────────

    @Test
    fun `chi bao khi thieu thu anh huong tinh nang loi`() {
        val fn = SourceRoots.body(pre, "fun runAndReport(")
        // [SOÁT P3] Trước đây tầng UI tự ghép chuỗi từ `missingCore` ⇒ `notice()` ở :core thành mã chết và câu chữ
        // người dùng đọc nằm ở tầng UI. Nay lấy câu từ :core, chế độ chỉ-mục-lõi. ⚠ Tôi đã thử gọi `notice()` KHÔNG
        // tham số và test bắt ngay: nó nói RỘNG hơn missingCore ⇒ launcher ồn hơn thiết kế.
        assertTrue(fn.contains("notice(coreOnly = true)"),
            "thiếu mục nhỏ mà báo mỗi lần mở là nhiễu — đúng thứ việc này đi dọn")
        // ⚠ Mốc là "if (msg != null" KHÔNG có ngoặc đóng: U8b thêm vế thứ hai (cổng một-lần) vào cùng câu `if`.
        assertTrue(fn.contains("if (msg != null"), "đủ (hoặc chỉ thiếu mục nhỏ) ⇒ im lặng")
    }

    /**
     * Nhóm "Hệ thống & quyền" là chỗ xem ĐỦ bức tranh — và **không bao giờ là một trang trắng**.
     *
     * ⚠ Bài này thay bài cũ *"đủ thì KHÔNG hiện mục nào"*. Luật cũ đúng cho bảng "Tuỳ biến": mục quyền chỉ là một
     * đoạn giữa một trang dài, ẩn đi khi đủ là hợp lý. Nay nó là **một nhóm người dùng chủ động bấm vào**, nên đủ mà
     * hiện trang trắng thì trả lời sai câu họ vừa hỏi và trông y như app hỏng. Luật *"đủ thì im lặng"* vẫn được canh ở
     * chỗ nó thuộc về — thông báo lúc mở launcher, `notice(coreOnly = true)` (bài `chi bao khi thieu…` phía trên).
     */
    @Test
    fun `nhom He thong la cho xem DU buc tranh va khong bao gio trang`() {
        assertTrue(panel.contains("permissionRow("), "phải có hàng cho từng quyền thiếu")
        // U5·T3 — nay đọc `displayLoses` (chính `losesWhatIfMissing` đã chọn theo ngôn ngữ), không đọc field thô.
        assertTrue(rows.contains("displayLoses"), "phải nói mất gì, không chỉ tên quyền")
        val fn = SourceRoots.body(panel, "private fun system(")
        assertTrue(fn.contains("rep.allOk"), "phải rẽ nhánh theo trạng thái đủ/thiếu")
        assertTrue(fn.contains("rows.note("), "ca ĐỦ phải nói 'đủ quyền', không để trống")
        assertTrue(fn.contains("rep.missing.forEach"), "ca THIẾU phải liệt kê đích danh")
        assertTrue(panel_caller.contains("permissions = {"), "chỗ gọi phải truyền đường đọc báo cáo vào")
        assertTrue(panel_caller.contains("PermissionPreflight.check("), "và đường đó phải là vòng kiểm thật")
    }

    /**
     * **U8b — thông báo thiếu quyền chỉ nổ MỘT LẦN mỗi phiên tiến trình.**
     *
     * [ĐO] máy ảo 2026-09-13: toast *"Kênh điều khiển cửa sổ…"* nổ mỗi lần mở Home, che thanh nút xe ~3 giây
     * (`LENGTH_LONG`). Trên xe, mỗi lần thoát app là một lần mở lại Home ⇒ một câu đúng lặp N lần thành nhiễu.
     *
     * Bài này canh CẢ HAI nửa của cách chữa, vì bỏ nửa nào cũng hỏng:
     *  - **có cổng** ⇒ không nhiễu; và cổng là **cờ RAM** (chết theo tiến trình), KHÔNG phải prefs — ghi prefs sẽ
     *    làm câu này im vĩnh viễn kể cả khi ROM cập nhật thu hồi quyền thật.
     *  - **vẫn còn chỗ xem đầy đủ** ⇒ *Cài đặt › Hệ thống & quyền* liệt kê từng mục thiếu (bài
     *    `nhom He thong la cho xem DU buc tranh…` phía trên canh nội dung trang đó).
     */
    @Test
    fun `thong bao thieu quyen chi no MOT LAN moi phien tien trinh`() {
        val fn = SourceRoots.body(pre, "fun runAndReport(")
        assertTrue(fn.contains("noticeShown.getAndSet(true)"),
            "toast phải qua cổng một-lần; không có cổng thì nó nổ mỗi lần mở Home (U8b)")
        assertTrue(fn.contains("msg != null &&"),
            "cờ chỉ được bật khi THẬT SỰ có câu để nói — bật sớm sẽ nuốt mất lần thiếu quyền xuất hiện muộn")
        assertTrue(pre.contains("private val noticeShown = AtomicBoolean(false)"),
            "cờ phải là AtomicBoolean trong object (runAndReport chạy trên thread nền, 2 Activity có thể cùng gọi)")
        assertFalse(SourceRoots.body(pre, "fun runAndReport(").contains("getSharedPreferences"),
            "cổng phải là cờ RAM: ghi prefs sẽ làm câu này im vĩnh viễn kể cả khi quyền bị thu hồi thật")
    }

    @Test
    fun `dung LAI cong thuc cap da proven, khong phat minh lenh moi`() {
        assertTrue(pre.contains("cmd notification allow_listener"), "đúng công thức đã chạy trên xe từ bản 1.13")
        assertTrue(pre.contains("appops set"), "đúng công thức cấp quyền vẽ overlay")
        assertTrue(pre.contains("enable_freeform_support"), "đúng khoá cấu hình cửa sổ tự do")
    }
}
