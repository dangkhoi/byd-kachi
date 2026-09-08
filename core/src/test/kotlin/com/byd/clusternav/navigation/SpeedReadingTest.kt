package com.byd.clusternav.navigation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Bất biến W1-3: **"không đọc được" phải là giá trị RIÊNG, không được giả dạng 0.**
 *
 * Trước 2026-07-27 logic này nằm trong `SpeedProvider` ở `:app`, dính reflection nên không ai kiểm được —
 * 80 dòng, 0 test, dù chú thích ghi rõ hậu quả: trên đời xe mà HAL tốc độ không trả số, hàm trả 0.0 vĩnh
 * viễn và mọi cổng an toàn kiểu `speed < 2.0` với ý "xe đang đỗ" sẽ LUÔN mở, kể cả lúc đang chạy.
 */
class SpeedReadingTest {

    @Test
    fun `khong doc duoc thi tra khong biet, khong tra 0`() {
        val reading = SpeedReading()
        assertNull(reading.acceptKmh(null), "null phải giữ nguyên nghĩa KHÔNG BIẾT")
        assertEquals(0.0, reading.lastGoodMps, "chưa từng đọc được thì không có giá trị tốt nào")
    }

    @Test
    fun `so am va so vo ly la sentinel, khong phai toc do`() {
        val reading = SpeedReading()
        listOf(-1.0, -0.001, 400.001, 1_000.0, Double.NaN, Double.POSITIVE_INFINITY).forEach { raw ->
            assertNull(reading.acceptKmh(raw), "$raw phải bị coi là không đọc được")
        }
        assertEquals(0.0, reading.lastGoodMps, "giá trị vô lý không được ghi vào lastGood")
    }

    @Test
    fun `doi don vi dung`() {
        val reading = SpeedReading()
        assertEquals(10.0, reading.acceptKmh(36.0)!!, 1e-9)
        assertEquals(0.0, reading.acceptKmh(0.0)!!, 1e-9, "0 km/h là tốc độ THẬT, phải nhận")
    }

    @Test
    fun `hien thi suy bien ve gia tri tot gan nhat`() {
        val reading = SpeedReading()
        reading.acceptKmh(72.0)
        assertEquals(20.0, reading.mpsForDisplay(null), 1e-9)
        assertEquals(20.0, reading.lastGoodMps, 1e-9, "một lần đọc lỗi không được xoá giá trị tốt")
    }

    @Test
    fun `mot lan doc loi khong lam mat kha nang phan biet`() {
        // Đây là ca thật trên xe: HAL chập chờn. Hiển thị được phép suy biến, nhưng cổng an toàn vẫn phải
        // thấy "không biết" — hai hàm khác nhau, và đó là điểm của cả lớp này.
        val reading = SpeedReading()
        reading.acceptKmh(50.0)
        assertNull(reading.acceptKmh(null), "cổng an toàn vẫn phải thấy KHÔNG BIẾT")
        assertTrue(reading.mpsForDisplay(null) > 0.0, "hiển thị vẫn có số để vẽ")
    }

    @Test
    fun `nguong hop ly co the doi theo doi xe`() {
        val reading = SpeedReading(maxPlausibleKmh = 180.0)
        assertNull(reading.acceptKmh(200.0))
        assertEquals(50.0, reading.acceptKmh(180.0)!!, 1e-9)
    }
}
