package com.byd.clusternav.navigation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Heuristic đọc màn dẫn đường — đoạn quyết định con số tài xế thấy trên cụm.
 *
 * Trước 2026-07-27 nó nằm trong `NavAccessibilityService` nên **không có bài kiểm nào**, dù ba ngưỡng
 * trong đó (dải trên 55%, dải đáy 78%, "đường là chuỗi dài nhất") là chỗ dễ sai âm thầm nhất: chỉ cần
 * thanh ETA trôi lên trên dải, hoặc tên đường dài hơn dòng lệnh rẽ, là số trên cụm đổi mà không ai biết.
 */
class NavScreenScanTest {

    private val h = 1000

    private fun item(text: String, top: Int, left: Int = 0) = ScreenTextItem(text, top, left)

    @Test
    fun `khong co chu gi thi khong biet gi`() {
        val r = NavScreenScan.scan(emptyList(), h)
        assertEquals(NavScreenReading.UNKNOWN_METERS, r.turnMeters)
        assertEquals("", r.road)
        assertEquals("", r.bottomInfo)
    }

    @Test
    fun `cu ly toi re lay token cao nhat o dai tren`() {
        val r = NavScreenScan.scan(
            listOf(item("250 m", top = 120), item("1.2 km", top = 300), item("800 m", top = 900)),
            h,
        )
        assertEquals(250, r.turnMeters, "phải lấy token cao nhất, không phải token đầu danh sách")
    }

    @Test
    fun `token thoi gian khong bi nham la cu ly`() {
        val r = NavScreenScan.scan(listOf(item("12 phút", top = 100), item("400 m", top = 200)), h)
        assertEquals(400, r.turnMeters)
    }

    @Test
    fun `cu ly o dai duoi khong duoc dung lam cu ly toi re`() {
        // Thanh đích ở đáy cũng có cự ly ("còn 3,5 km"). Lấy nhầm nó là chỉ sai cả màn cụm.
        val r = NavScreenScan.scan(listOf(item("3,5 km", top = 950)), h)
        assertEquals(NavScreenReading.UNKNOWN_METERS, r.turnMeters)
    }

    @Test
    fun `duong la chuoi dai nhat o dai tren, khong phai cu ly hay gio`() {
        val r = NavScreenScan.scan(
            listOf(item("250 m", top = 100), item("Rẽ phải vào Nguyễn Huệ", top = 150), item("Lê Lợi", top = 200)),
            h,
        )
        assertEquals("Rẽ phải vào Nguyễn Huệ", r.road)
    }

    @Test
    fun `chuoi qua ngan khong duoc coi la duong`() {
        val r = NavScreenScan.scan(listOf(item("A", top = 100), item("Hai", top = 120)), h)
        assertEquals("Hai", r.road, "ngưỡng 3 ký tự loại nhãn một chữ")
    }

    @Test
    fun `thong tin day sap theo thu tu tu trai sang phai`() {
        val r = NavScreenScan.scan(
            listOf(item("08:30", top = 950, left = 300), item("3,5 km", top = 960, left = 100)),
            h,
        )
        assertEquals("3,5 km · 08:30", r.bottomInfo, "phải theo trái→phải, không theo thứ tự node")
    }

    @Test
    fun `cu ly vo ly bi loai`() {
        val r = NavScreenScan.scan(listOf(item("999 km", top = 100)), h)
        assertEquals(NavScreenReading.UNKNOWN_METERS, r.turnMeters, "trên 50 km là đọc nhầm thứ khác")
    }

    @Test
    fun `khong doc duoc chieu cao man thi dung mac dinh`() {
        // top 500 nằm dưới 55% của 1080 (=594) nên vẫn thuộc dải trên.
        val r = NavScreenScan.scan(listOf(item("300 m", top = 500)), screenHeight = 0)
        assertEquals(300, r.turnMeters)
    }

    @Test
    fun `cu ly bang 0 la da toi cho re, khong phai khong biet`() {
        val r = NavScreenScan.scan(listOf(item("0 m", top = 100)), h)
        assertEquals(0, r.turnMeters)
    }
}
