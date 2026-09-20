package com.byd.clusternav.launcher.automation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ SỔ LUẬT DẪN ĐƯỜNG TỰ ĐỘNG · DANH SÁCH + MÃ HOÁ ═══════════════════════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-automation.html` R2.1 (usecase gốc: *"sáng đến cty, chiều về nhà"* = HAI luật). Thuần ⇒
 * chạy off-car.
 *
 * ## Bài này khoá lại bất biến nguy hiểm nhất: `id` KHÔNG TRÙNG
 * `id` là khoá của sổ đã-dẫn. Trùng `id` ⇒ luật nổ trước đóng dấu *"hôm nay đã dẫn"* cho **cả hai** ⇒ luật buổi
 * chiều **không bao giờ chạy**, mà Cài đặt vẫn hiện đủ hai dòng và test từng luật vẫn xanh. Đúng họ lỗi *"cấu hình
 * đúng, hành vi sai, im lặng"*.
 */
class NavAutomationBookTest {

    private fun rule(id: String, placeId: String = "công ty", startMin: Int = 450, endMin: Int = 540) =
        ScheduledNavRules.of(
            id = id,
            enabled = true,
            startMin = startMin,
            endMin = endMin,
            days = ScheduledNavRules.WEEKDAYS,
            requireGps = true,
            placeId = placeId,
            navApp = "gmaps",
        ) ?: error("luật mẫu phải hợp lệ")

    private val morning = rule("r1", "công ty")
    private val evening = rule("r2", "nhà", startMin = 17 * 60, endMin = 19 * 60)

    // ══ (1) Mã hoá nhiều luật ════════════════════════════════════════════════════════════════════════════

    @Test
    fun `ma hoa roi giai ma tra lai dung hai luat dung thu tu`() {
        val book = listOf(morning, evening)
        assertEquals(book, NavAutomationBook.decode(NavAutomationBook.encode(book)))
    }

    @Test
    fun `chuoi rong hoac null ra so rong`() {
        assertEquals(emptyList<ScheduledNavRule>(), NavAutomationBook.decode(null))
        assertEquals(emptyList<ScheduledNavRule>(), NavAutomationBook.decode(""))
        assertEquals(emptyList<ScheduledNavRule>(), NavAutomationBook.decode("   \n  "))
    }

    /** Dòng hỏng ⇒ bỏ đúng dòng đó; các luật lành vẫn về (chuỗi đọc từ đĩa, sửa tay được). */
    @Test
    fun `dong hong bi bo, khong nem va khong mat ca so`() {
        val raw = listOf(
            ScheduledNavRules.encodeLine(morning),
            "thiếu|trường",
            "r9|1|450|540||1|nhà|gmaps",
            ScheduledNavRules.encodeLine(evening),
        ).joinToString("\n")
        assertEquals(listOf("r1", "r2"), NavAutomationBook.decode(raw).map { it.id })
    }

    /** ⚠⚠ Dòng TRÙNG `id` bị bỏ — giữ dòng ĐẦU (thứ người dùng tạo trước), phép chọn ổn định giữa các lần đọc. */
    @Test
    fun `dong trung id bi bo, giu dong dau`() {
        val clash = rule("r1", "nhà")
        val raw = listOf(morning, clash, evening).joinToString("\n") { ScheduledNavRules.encodeLine(it) }
        val out = NavAutomationBook.decode(raw)
        assertEquals(listOf("r1", "r2"), out.map { it.id })
        assertEquals("công ty", out[0].placeId, "giữ dòng đầu")
    }

    @Test
    fun `khong doc qua tran`() {
        val many = (1..NavAutomationBook.MAX + 3).map { rule("r$it") }
        assertEquals(NavAutomationBook.MAX, NavAutomationBook.decode(NavAutomationBook.encode(many)).size)
    }

    // ══ (2) Thêm / sửa / xoá ═════════════════════════════════════════════════════════════════════════════

    @Test
    fun `them luat moi vao cuoi`() {
        assertEquals(listOf("r1", "r2"), NavAutomationBook.upsert(listOf(morning), evening).map { it.id })
    }

    /** Sửa = thêm-đè cùng `id`, và GIỮ NGUYÊN vị trí: sửa giờ luật buổi sáng không làm nó nhảy xuống cuối. */
    @Test
    fun `sua giu nguyen vi tri`() {
        val book = listOf(morning, evening)
        val edited = morning.copy(startMin = 8 * 60)
        val out = NavAutomationBook.upsert(book, edited)
        assertEquals(listOf("r1", "r2"), out.map { it.id })
        assertEquals(8 * 60, out[0].startMin)
        assertEquals(2, out.size, "ghi đè, không thêm bản thứ hai cùng id")
    }

    /** Vượt trần mà là luật MỚI ⇒ sổ không đổi (không cắt luật cũ); chỗ gọi so độ dài để nói ra. */
    @Test
    fun `vuot tran thi tu choi them chu khong cat luat cu`() {
        val full = (1..NavAutomationBook.MAX).map { rule("r$it") }
        val out = NavAutomationBook.upsert(full, rule("rX"))
        assertEquals(full, out)
    }

    /** Vượt trần vẫn SỬA được luật đang có — nếu không thì sổ đầy là sổ không sửa nổi. */
    @Test
    fun `vuot tran van sua duoc luat dang co`() {
        val full = (1..NavAutomationBook.MAX).map { rule("r$it") }
        val out = NavAutomationBook.upsert(full, full[0].copy(enabled = false))
        assertEquals(NavAutomationBook.MAX, out.size)
        assertFalse(out[0].enabled)
    }

    @Test
    fun `xoa va tra ve theo id`() {
        val book = listOf(morning, evening)
        assertEquals(listOf("r2"), NavAutomationBook.remove(book, "r1").map { it.id })
        assertEquals(book, NavAutomationBook.remove(book, "khong-co"))
        assertEquals(morning, NavAutomationBook.find(book, "r1"))
        assertNull(NavAutomationBook.find(book, "khong-co"))
    }

    @Test
    fun `bat tat mot luat tai cho`() {
        val out = NavAutomationBook.setEnabled(listOf(morning, evening), "r2", false)
        assertTrue(out[0].enabled)
        assertFalse(out[1].enabled)
        assertEquals(listOf(morning, evening), NavAutomationBook.setEnabled(listOf(morning, evening), "lạ", false))
    }

    // ══ (3) Cấp `id` mới ═════════════════════════════════════════════════════════════════════════════════

    @Test
    fun `id moi tang dan tu so rong`() {
        assertEquals("r1", NavAutomationBook.newId(emptyList()))
        assertEquals("r2", NavAutomationBook.newId(listOf(morning)))
        assertEquals("r3", NavAutomationBook.newId(listOf(morning, evening)))
    }

    /**
     * ⚠ Ca XOÁ-RỒI-THÊM: bỏ `r1` rồi thêm luật mới. Phép `"r" + (size + 1)` sẽ cho `r2` — **trùng** luật đang còn
     * ⇒ sổ đã-dẫn của `r2` cũ đóng dấu cho luật mới ⇒ luật vừa tạo không chạy hôm nay, im lặng.
     */
    @Test
    fun `id moi khong bao gio trung luat dang con sau khi xoa`() {
        val afterDelete = NavAutomationBook.remove(listOf(morning, evening), "r1")
        val fresh = NavAutomationBook.newId(afterDelete)
        assertEquals("r1", fresh)
        assertFalse(afterDelete.any { it.id == fresh }, "id mới không được trùng luật đang còn")
    }

    @Test
    fun `id moi nhay qua moi so da dung`() {
        val book = listOf(rule("r1"), rule("r2"), rule("r4"))
        assertEquals("r3", NavAutomationBook.newId(book))
    }
}
