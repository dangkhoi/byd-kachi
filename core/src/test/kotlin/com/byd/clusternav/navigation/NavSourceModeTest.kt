package com.byd.clusternav.navigation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Hai con số này đã nằm trong SharedPreferences trên máy người dùng.
 *
 * Đổi chúng là làm sai dữ liệu đã lưu mà không có lỗi nào báo: người đang chọn "ưu tiên Google Maps" sẽ
 * lặng lẽ thành "tự động". Bài kiểm ghim giá trị để lần đổi nào cũng phải cố ý.
 */
class NavSourceModeTest {

    @Test
    fun `gia tri luu tren may khong duoc doi`() {
        assertEquals(0, NavSourceMode.AUTO)
        assertEquals(2, NavSourceMode.PREFER_GMAPS)
    }
}
