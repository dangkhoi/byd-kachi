package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ V3 · R13 — ĐỔI TÊN HỒ SƠ (owner 2026-09-16 · E5) ════════════════════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-voice-fast-natural.html` R13. Bốn ca hỏng đều **im lặng** nếu không ai chặn — xem KDoc
 * [ProfileRename]. Bài này là chỗ chúng có tiếng.
 */
class ProfileRenameTest {

    private val list = listOf("Mặc định", "Vợ", "Khách")

    private fun ok(old: String, new: String): ProfileRename.Plan {
        val r = ProfileRename.plan(old, new, list)
        assertTrue(r is ProfileRename.Result.Ok, "kỳ vọng chấp nhận, nhận $r")
        return (r as ProfileRename.Result.Ok).plan
    }

    private fun no(old: String, new: String): ProfileRename.Err {
        val r = ProfileRename.plan(old, new, list)
        assertTrue(r is ProfileRename.Result.No, "kỳ vọng từ chối, nhận $r")
        return (r as ProfileRename.Result.No).err
    }

    @Test
    fun `doi ten giu NGUYEN thu tu danh sach`() {
        val p = ok("Vợ", "Nhà tôi")
        assertEquals(listOf("Mặc định", "Nhà tôi", "Khách"), p.profiles, "thứ tự là thứ người dùng thấy ở bộ chọn")
        assertEquals("Vợ", p.from)
        assertEquals("Nhà tôi", p.to)
    }

    @Test
    fun `moi hau to theo ho so deu phai doi — sinh tu ProfileScope, khong chep tay`() {
        val p = ok("Vợ", "Nhà tôi")
        assertEquals(ProfileScope.LAUNCHER_SUFFIXES, p.suffixes)
        // Ba thứ dễ quên nhất khi chép tay: bố cục, nội dung ô, và ảnh chụp cấu hình ClusterNav.
        assertTrue("preset" in p.suffixes)
        assertTrue(p.suffixes.any { it.startsWith(SettingsCatalog.SLOT_KEY_PREFIX) })
        assertTrue(p.suffixes.any { it.startsWith(ProfileScope.SNAPSHOT_INFIX) })
        // V3 · R14 — công tắc nhãn chip là hậu tố theo hồ sơ MỚI ⇒ đổi tên phải kéo theo nó.
        assertTrue("top_strip_labels" in p.suffixes, "khoá theo-hồ-sơ mới mà quên ở đây là một khoá mồ côi")
    }

    @Test
    fun `ten rong hoac chi khoang trang bi tu choi`() {
        assertEquals(ProfileRename.Err.EMPTY, no("Vợ", ""))
        assertEquals(ProfileRename.Err.EMPTY, no("Vợ", "   "))
        assertEquals(ProfileRename.Err.EMPTY, no("Vợ", "\n"))
    }

    @Test
    fun `ten trung HO SO KHAC bi tu choi — hai dong y het trong bo chon la hai bo khoa tron vao nhau`() {
        assertEquals(ProfileRename.Err.DUPLICATE, no("Vợ", "Khách"))
    }

    @Test
    fun `doi ten thanh CHINH NO la hop le va khong lam gi`() {
        val p = ok("Vợ", "Vợ")
        assertEquals(list, p.profiles)
        assertEquals(p.from, p.to)
    }

    @Test
    fun `ho so khong ton tai thi tu choi`() {
        assertEquals(ProfileRename.Err.UNKNOWN, no("Không có", "Gì đó"))
    }

    @Test
    fun `xuong dong bi thay bang dau cach — danh sach luu ngan bang ky tu xuong dong`() {
        // `profiles` lưu thành một chuỗi ngăn bằng `\n`; một tên có xuống dòng sẽ tách hồ sơ làm hai, im lặng.
        assertEquals("Vợ hai", ProfileRename.clean("Vợ\nhai"))
        assertEquals("Vợ hai", ok("Vợ", " Vợ\r\nhai ").to.replace("  ", " "))
    }
}
