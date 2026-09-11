package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Khoá phần CẤP quyền trợ năng — chỗ nguy hiểm nhất của P8.
 *
 * Vì sao đáng một lớp test riêng: lệnh cấp phải **ghi lại cả danh sách dùng chung của hệ thống**. Làm sai một chút
 * là **xoá trợ năng của app khác** — kể cả của người khuyết tật đang dùng. Ba rủi ro cụ thể đã được lượt quét bảo
 * mật chỉ ra và đây là chỗ khoá chúng lại.
 *
 * Test quét SOURCE (hàm cấp nằm ở `:app`, cần Android context để chạy thật); mọi phép quét bỏ chú thích trước khi
 * kiểm để không đạt-test-bằng-cách-viết-vào-comment.
 */
class AccessibilityGrantSafetyContractTest {

    private fun code(relative: String): String =
        SourceRoots.text(relative)
            .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), " ")
            .lines().joinToString("\n") { it.substringBefore("//") }

    private val pre by lazy { code("src/main/java/com/byd/clusternav/launcher/PermissionPreflight.kt") }
    private val act by lazy { code("src/main/java/com/byd/clusternav/launcher/KachiHomeActivity.kt") }

    @Test
    fun `chi giu token dung DANG component truoc khi ghi lai danh sach`() {
        // Lệnh đọc có thể trả về "null" HOẶC một câu lỗi. Ghép câu lỗi vào rồi ghi đè cấu hình dùng chung = xoá
        // trợ năng của app khác. Lọc theo DẠNG an toàn kể cả khi ROM khác đổi định dạng trả về.
        assertTrue(pre.contains("COMPONENT_SHAPE"), "phải có phép lọc theo dạng component")
        val fn = pre.substringAfter("fun accessibilityGrantCommands(").substringBefore("private val COMPONENT_SHAPE")
        assertTrue(fn.contains("COMPONENT_SHAPE.matches("), "phải LỌC danh sách đọc về, không tin nguyên văn")
        assertFalse(
            fn.contains("filter { it.isNotEmpty() }") && !fn.contains("COMPONENT_SHAPE"),
            "lọc 'không rỗng' là KHÔNG đủ — câu lỗi cũng không rỗng",
        )
    }

    @Test
    fun `gia tri ghi vao phai duoc BOC NHAY`() {
        // Token chứa khoảng trắng làm `settings put` chỉ nhận phần đầu ⇒ mất phần còn lại của danh sách.
        val fn = pre.substringAfter("fun accessibilityGrantCommands(").substringBefore("private val COMPONENT_SHAPE")
        assertTrue(
            fn.contains("'\$merged'"),
            "giá trị là DỮ LIỆU, không phải mã lệnh ⇒ phải bọc nháy khi nội suy vào lệnh shell",
        )
    }

    @Test
    fun `co trong danh sach nhung CO tat thi van phai bat co`() {
        // [ĐO] 2026-09-11: chính cờ này bị hệ thống đưa về 0 khi tiến trình chết ⇒ đây là ca THẬT.
        val fn = pre.substringAfter("fun accessibilityGrantCommands(").substringBefore("private val COMPONENT_SHAPE")
        assertTrue(fn.contains("flagOn"), "phải nhận trạng thái cờ")
        assertTrue(
            fn.contains("if (already && flagOn) return emptyList()"),
            "chỉ được bỏ qua khi VỪA có trong danh sách VỪA đã bật cờ",
        )
        assertTrue(fn.contains("if (!flagOn)"), "cờ tắt thì phải bật, dù component đã có trong danh sách")
    }

    @Test
    fun `phep kiem DU phai tinh ca co, khong chi danh sach`() {
        val fn = pre.substringAfter("private fun accessibilityGranted(").substringBefore("private fun overlayGranted")
        assertTrue(fn.contains("accessibility_enabled"), "thiếu phép kiểm cờ ⇒ báo ĐỦ trong khi trợ năng đang tắt")
        assertTrue(fn.contains("listed && flagOn"), "phải cần CẢ HAI")
    }

    @Test
    fun `cho goi phai doc ca co truoc khi cap`() {
        assertTrue(act.contains("READ_ACCESSIBILITY_FLAG_CMD"), "chỗ gọi phải đọc cờ")
        assertTrue(
            act.contains("accessibilityGrantCommands(cur, flagOn)"),
            "phải truyền cả danh sách và cờ — thiếu cờ thì hàm cấp không biết có phải bật cờ hay không",
        )
    }

    @Test
    fun `khong cap quyen rong hon can thiet`() {
        // Mọi lệnh cấp phải nhắm ĐÚNG gói của chính app; không dấu sao, không grant-all, không leo quyền.
        listOf("pm grant", "appops set * ", "--uid", "reset_all", "su -c", "allow-all").forEach {
            assertFalse(pre.contains(it), "lệnh cấp quá rộng: '$it'")
        }
        // Lệnh dùng hằng số nội suy (`$PKG`) nên phải kiểm HAI thứ: lệnh nhắm vào hằng số đó, và hằng số đó ĐÚNG là
        // gói của chính app. Bản đầu của test này chỉ khớp chuỗi literal ⇒ báo nhầm chính code đúng — sửa cho khoá
        // đúng Ý ĐỊNH thay vì cách viết.
        val appopsTargets = Regex("""appops set (\S+)""").findAll(pre).map { it.groupValues[1] }.toList()
        assertTrue(appopsTargets.isNotEmpty(), "phải có lệnh cấp quyền overlay")
        appopsTargets.forEach {
            assertTrue(it == "\$PKG" || it == "com.byd.launcher", "lệnh cấp nhắm vào '$it' — phải là gói của chính app")
        }
        assertTrue(
            pre.contains("""const val PKG = "com.byd.launcher""""),
            "hằng số gói phải đúng là gói của app này",
        )
    }
}
