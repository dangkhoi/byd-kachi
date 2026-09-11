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
    private val panel by lazy { code("src/main/java/com/byd/clusternav/launcher/CustomizePanel.kt") }

    /** Chỗ GỌI bảng Tuỳ biến — tách khỏi Activity sang [HomePanels] (Activity vượt trần 500 dòng). */
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
        assertTrue(fn.contains("if (msg != null)"), "đủ (hoặc chỉ thiếu mục nhỏ) ⇒ im lặng")
    }

    @Test
    fun `bang Tuy bien la cho xem DU buc tranh`() {
        assertTrue(panel.contains("permissionRow("), "phải có hàng cho từng quyền thiếu")
        assertTrue(panel.contains("losesWhatIfMissing"), "phải nói mất gì, không chỉ tên quyền")
        assertTrue(panel.contains("!it.allOk"), "đủ thì KHÔNG hiện mục nào")
        assertTrue(panel_caller.contains("permissions = PermissionPreflight.check("), "chỗ gọi phải truyền báo cáo vào")
    }

    @Test
    fun `dung LAI cong thuc cap da proven, khong phat minh lenh moi`() {
        assertTrue(pre.contains("cmd notification allow_listener"), "đúng công thức đã chạy trên xe từ bản 1.13")
        assertTrue(pre.contains("appops set"), "đúng công thức cấp quyền vẽ overlay")
        assertTrue(pre.contains("enable_freeform_support"), "đúng khoá cấu hình cửa sổ tự do")
    }
}
