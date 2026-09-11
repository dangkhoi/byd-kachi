package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Khoá HÀNH VI của phép cấp quyền trợ năng — ca "**kênh shell lỗi**".
 *
 * ## ⚠ Vì sao tệp này tồn tại (lượt soát độc lập 2026-09-11, mức P1)
 * `ShellTransport.run` trả **chuỗi rỗng** khi cả hai lượt thử thất bại, và cổng lệnh trả rỗng khi bị từ chối —
 * **không ném ngoại lệ**. Bản trước lọc `takeIf { it != "null" }` nên chuỗi rỗng đi qua như *"danh sách trợ năng
 * đang rỗng"* ⇒ sinh lệnh ghi danh sách **chỉ có Kachi** ⇒ **xoá trợ năng của mọi app khác**, kể cả trình đọc màn
 * hình của người khiếm thị. Đúng hậu quả mà KDoc của hàm đó tuyên bố đã chặn: bộ lọc theo dạng component chặn được
 * việc *chèn rác*, nhưng không chặn được việc *mất bản gốc*.
 *
 * Guard cũ chỉ quét mã nguồn (và một assert của nó **không thể đỏ**). Đây là bài kiểm HÀNH VI thật.
 */
class AccessibilityGrantBehaviourTest {

    private val kachi = "com.byd.launcher/com.byd.clusternav.modules.navaccess.NavAccessibilityService"
    private val other = "com.other.app/com.other.app.TalkBackLike"

    private fun writeListCmd(cmds: List<String>): String? =
        cmds.firstOrNull { it.contains("enabled_accessibility_services") }

    @Test
    fun `khong doc duoc danh sach thi TUYET DOI khong ghi lai danh sach`() {
        // Ba dạng "không đọc được" đều phải im: null (không gọi được), rỗng (lệnh lỗi), toàn khoảng trắng.
        listOf(null, "", "   ", "\n").forEach { raw ->
            val cmds = PermissionPreflight.accessibilityGrantCommands(raw, flagOn = false)
            assertEquals(
                null, writeListCmd(cmds),
                "với lượt đọc '${raw?.replace("\n", "\\n")}' thì KHÔNG được ghi danh sách — ghi là xoá trợ năng app khác",
            )
            assertTrue(
                cmds.any { it.contains("accessibility_enabled 1") },
                "vẫn được bật CỜ (không xoá gì của ai)",
            )
        }
    }

    @Test
    fun `chuoi la khong co token dung dang cung bi coi la khong doc duoc`() {
        // Một câu lỗi của ROM lọc ra rỗng — không phân biệt được với "danh sách rỗng thật" ⇒ phải im.
        val cmds = PermissionPreflight.accessibilityGrantCommands("Error: permission denied", flagOn = true)
        assertEquals(null, writeListCmd(cmds), "câu lỗi KHÔNG được coi là danh sách rỗng")
    }

    @Test
    fun `danh sach rong THAT thi ghi duoc, va chi co Kachi`() {
        // `settings get` báo "chưa đặt" bằng đúng chữ "null" — đây là ca rỗng-thật, ghi được.
        val cmd = writeListCmd(PermissionPreflight.accessibilityGrantCommands("null", flagOn = true))
        assertTrue(cmd != null, "rỗng thật thì phải ghi")
        assertTrue(cmd!!.contains(kachi), "phải thêm Kachi")
    }

    @Test
    fun `co app khac thi phai GIU nguyen roi noi them Kachi`() {
        val cmd = writeListCmd(PermissionPreflight.accessibilityGrantCommands(other, flagOn = true))
        assertTrue(cmd != null)
        assertTrue(cmd!!.contains(other), "PHẢI giữ trợ năng của app khác")
        assertTrue(cmd.contains(kachi), "và nối thêm Kachi")
        assertTrue(cmd.indexOf(other) < cmd.indexOf(kachi), "nối THÊM vào cuối, không chen lên đầu")
    }

    @Test
    fun `da co Kachi va co dang bat thi khong lam gi`() {
        assertEquals(
            emptyList<String>(),
            PermissionPreflight.accessibilityGrantCommands("$other:$kachi", flagOn = true),
            "đủ rồi thì im — không ghi lại cấu hình dùng chung vô cớ",
        )
    }

    @Test
    fun `da co Kachi nhung CO dang tat thi chi bat co`() {
        val cmds = PermissionPreflight.accessibilityGrantCommands("$other:$kachi", flagOn = false)
        assertEquals(null, writeListCmd(cmds), "đã có trong danh sách thì không cần ghi lại danh sách")
        assertEquals(1, cmds.size)
        assertTrue(cmds.single().contains("accessibility_enabled 1"), "chỉ bật cờ")
    }

    @Test
    fun `token rac trong danh sach bi loc, phan hop le duoc giu`() {
        val raw = "$other:không-phải-component:rác rưởi có khoảng trắng"
        val cmd = writeListCmd(PermissionPreflight.accessibilityGrantCommands(raw, flagOn = true))
        assertTrue(cmd != null)
        assertTrue(cmd!!.contains(other), "token hợp lệ được giữ")
        assertFalse(cmd.contains("rác"), "token sai dạng bị lọc — nối vào là làm hỏng cấu hình dùng chung")
        assertFalse(cmd.contains("không-phải-component"))
    }

    @Test
    fun `gia tri ghi vao luon duoc boc nhay`() {
        val cmd = writeListCmd(PermissionPreflight.accessibilityGrantCommands(other, flagOn = true))!!
        val value = cmd.substringAfter("enabled_accessibility_services ")
        assertTrue(value.startsWith("'") && value.trimEnd().endsWith("'"),
            "giá trị là DỮ LIỆU, không phải mã lệnh ⇒ phải bọc nháy: $value")
    }
}
