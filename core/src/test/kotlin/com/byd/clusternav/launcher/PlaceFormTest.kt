package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * KACHI-301 R11 — luật kiểm của hộp địa chỉ ([PlaceForm]) phải là ĐÚNG luật của đường lưu ([SavedPlaces.of]).
 * Lệch nhau ⇒ nút Lưu sáng mà lưu bị từ chối (hoặc khoá oan) — bài này khoá sự trùng khớp đó.
 */
class PlaceFormTest {

    private val names = listOf("", " ", "Nhà", " | ", "\n", "Công ty", "a|b")
    private val addresses = listOf("", "   ", "12 Nguyễn Huệ", "|\r\n", "Q.1")
    private val goodCoords = listOf("", "  ", "10.7769, 106.7009", "10.7769 106.7009", "-33.9,151.2")

    @Test
    fun `ok khop dung duong luu SavedPlaces of khi toa do trong hoac dung`() {
        for (n in names) for (a in addresses) for (c in goodCoords) {
            val ok = PlaceForm.check(n, a, c).ok
            val saved = SavedPlaces.of(n, a, c)
            assertEquals(saved != null, ok, "lệch luật lưu: name='$n' address='$a' coords='$c'")
            if (saved != null) assertEquals(c.isNotBlank(), saved.hasCoords, "toạ độ hợp lệ phải được lưu: '$c'")
        }
    }

    @Test
    fun `toa do hong bi bao tai cho, khong lang le bo`() {
        listOf("10.77, abc", "200, 300", "10.7", "1,2,3", "nan, 5").forEach {
            val chk = PlaceForm.check("Nhà", "12 Nguyễn Huệ", it)
            assertTrue(chk.coordsBad, "'$it' phải bị báo hỏng")
            assertFalse(chk.ok)
        }
    }

    @Test
    fun `thieu ten hoac dia chi chi danh dau dung o`() {
        val c = PlaceForm.check(" ", "12 Nguyễn Huệ", "")
        assertTrue(c.nameMissing); assertFalse(c.addressMissing); assertFalse(c.coordsBad)
        val d = PlaceForm.check("Nhà", "|", "")
        assertFalse(d.nameMissing); assertTrue(d.addressMissing)
    }

    @Test
    fun `chip nhan nhanh khop theo khoa cua so, khong khop thi Khac`() {
        val presets = listOf("Nhà", "Công ty", "Trường")
        assertEquals(0, PlaceForm.presetIndex("nhà", presets))
        assertEquals(1, PlaceForm.presetIndex("  Công ty ", presets))
        assertEquals(-1, PlaceForm.presetIndex("Nhà ngoại", presets))
        assertEquals(-1, PlaceForm.presetIndex("Nha", presets), "không bỏ dấu — Nha ≠ Nhà (keyOf)")
        assertEquals(-1, PlaceForm.presetIndex("", presets))
    }
}
