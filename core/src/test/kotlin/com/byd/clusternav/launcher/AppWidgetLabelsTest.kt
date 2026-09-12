package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * [SOÁT P3-5] Gỡ TRÙNG TÊN cho widget bên thứ ba — xem KDoc [AppWidgetLabels].
 *
 * Hai ca trong bài đầu là **hai ca thật đo được** trên `emulator-5554`, không phải ca dựng tay: hai widget VietMap
 * cùng tên *"Cảnh báo"* (cùng gói) và hai *"Google Play Music"*.
 */
class AppWidgetLabelsTest {

    private fun titles(vararg e: Pair<String, String>) = AppWidgetLabels.titles(e.toList())

    /** ⚠ Ca đo được #1: hai widget **cùng gói** ⇒ gói không phân biệt được ⇒ gợi ý là tên lớp gọn. */
    @Test
    fun `hai widget VietMap cung ten cung goi thi goi y bang ten lop`() {
        val out = titles(
            "Cảnh báo" to "vn.vietmap.live/vn.vietmap.live.widget.VMAlertWidgetProvider",
            "Cảnh báo" to "vn.vietmap.live/vn.vietmap.live.widget.VMOnlyStickyAlertWidgetProvider",
        )
        assertEquals(listOf("Cảnh báo · VMAlert", "Cảnh báo · VMOnlyStickyAlert"), out)
    }

    /** Ca đo được #2: hai mục cùng tên **khác gói**, tên lớp cũng khác ⇒ tên lớp đã đủ (ngắn hơn gói). */
    @Test
    fun `hai muc cung ten khac goi thi van goi y bang thu ngan nhat con phan biet duoc`() {
        val out = titles(
            "Google Play Music" to "com.google.android.music/com.google.android.music.ui.WidgetProvider4x2",
            "Google Play Music" to "com.google.android.music/com.google.android.music.ui.WidgetProvider4x1",
        )
        assertEquals(listOf("Google Play Music · WidgetProvider4x2", "Google Play Music · WidgetProvider4x1"), out)
    }

    /** Tên lớp TRÙNG nhau thì phải rơi xuống **gói** — không thì gợi ý vô nghĩa (hai mục gợi ý y hệt). */
    @Test
    fun `ten lop trung nhau thi roi xuong goi`() {
        val out = titles(
            "Đồng hồ" to "com.a.clock/.ClockWidgetProvider",
            "Đồng hồ" to "com.b.clock/.ClockWidgetProvider",
        )
        assertEquals(listOf("Đồng hồ · com.a.clock", "Đồng hồ · com.b.clock"), out)
        assertTrue(out.distinct().size == 2, "hai mục phải phân biệt được — gợi ý trùng nhau thì vô nghĩa")
    }

    /**
     * ⚠ Nhãn **KHÔNG trùng** thì giữ NGUYÊN VĂN — thêm gợi ý cho mọi mục là nhiễu.
     *
     * Cùng luật đã chọn ở RW0 (18 nhãn trùng ⇒ chỉ thêm ở chỗ trùng, không thêm cho cả 187 mục).
     */
    @Test
    fun `nhan khong trung thi giu nguyen van`() {
        val out = titles(
            "Đồng hồ" to "com.a/.W",
            "Lịch" to "com.b/.W",
            "Thời tiết" to "com.c/.W",
        )
        assertEquals(listOf("Đồng hồ", "Lịch", "Thời tiết"), out)
    }

    /** Chỉ nhóm TRÙNG bị thêm gợi ý; mục đứng một mình trong cùng danh sách vẫn nguyên văn. */
    @Test
    fun `chi nhom trung bi them goi y`() {
        val out = titles(
            "Cảnh báo" to "vn.vietmap.live/.VMAlertWidgetProvider",
            "Tốc độ" to "vn.vietmap.live/.VMSpeedWidgetProvider",
            "Cảnh báo" to "vn.vietmap.live/.VMStickyWidgetProvider",
        )
        assertEquals("Tốc độ", out[1], "mục không trùng phải giữ nguyên văn")
        assertTrue(out[0].startsWith("Cảnh báo · ") && out[2].startsWith("Cảnh báo · "))
    }

    /** Thứ tự vào = thứ tự ra (màn chọn đã sắp theo nhãn; đảo thứ tự ở đây là đổi bố cục lưới). */
    @Test
    fun `thu tu ra dung bang thu tu vao`() {
        val entries = listOf("B" to "com.b/.W", "A" to "com.a/.W", "B" to "com.c/.W")
        val out = AppWidgetLabels.titles(entries)
        assertEquals(3, out.size)
        assertEquals("A", out[1], "mục thứ hai vẫn phải là mục thứ hai")
    }

    /** Danh sách rỗng ⇒ rỗng. Và chuỗi provider rác (ROM lạ) không được làm mất mục hay ném. */
    @Test
    fun `danh sach rong va provider rac khong nem`() {
        assertEquals(emptyList<String>(), AppWidgetLabels.titles(emptyList()))
        val out = titles("X" to "", "X" to "rác", "X" to "com.a/.W")
        assertEquals(3, out.size, "không mục nào được mất")
        assertTrue(out.all { it.startsWith("X") }, "nhãn gốc phải còn ở đầu: $out")
        assertEquals(3, out.distinct().size, "ba mục vẫn phải phân biệt được: $out")
    }

    /** Hậu tố quy ước bỏ hết mà rỗng thì phải lùi về tên lớp — gợi ý KHÔNG được là chuỗi trắng. */
    @Test
    fun `ten lop chi gom hau to quy uoc thi khong ra goi y trang`() {
        val out = titles(
            "X" to "com.a/.WidgetProvider",
            "X" to "com.b/.AppWidgetProvider",
        )
        assertTrue(out.none { it.endsWith("· ") || it.endsWith("·") }, "gợi ý trắng thì mục nhìn như bị lỗi: $out")
        assertEquals(2, out.distinct().size, "và hai mục vẫn phải phân biệt được: $out")
    }
}
