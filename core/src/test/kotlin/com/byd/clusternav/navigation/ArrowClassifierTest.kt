package com.byd.clusternav.navigation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Bằng chứng cho việc tách port có giá trị: thuật toán nhận dạng hướng rẽ giờ test được **không cần
 * Android, không cần thiết bị**. Trước 2026-07-27 nó bị khoá vào `android.graphics.Bitmap` nên 66 dòng logic
 * này chưa từng có một test nào.
 */
class ArrowClassifierTest {

    private fun frame(width: Int, height: Int, ink: (Int, Int) -> Boolean): PixelFrame {
        val px = IntArray(width * height) { index ->
            val x = index % width
            val y = index / width
            if (ink(x, y)) 0xFF101010.toInt() else 0x00000000
        }
        return ArrayPixelFrame(width, height, px)
    }

    @Test
    fun `khung qua nho thi khong ket luan`() {
        assertNull(ArrowClassifier.classify(frame(4, 4) { _, _ -> true }))
    }

    @Test
    fun `khung rong thi khong ket luan`() {
        assertNull(ArrowClassifier.classify(frame(32, 32) { _, _ -> false }))
    }

    @Test
    fun `khung null thi khong ket luan`() {
        assertNull(ArrowClassifier.classify(null))
    }

    @Test
    fun `cung dau vao thi cung ket qua`() {
        // Khung tổng hợp chưa chắc thoả điều kiện hình học của thuật toán, nên test này chỉ khẳng định tính
        // tất định — điều duy nhất đầu vào bịa được chứng minh. Muốn kiểm ĐÚNG hướng rẽ thì cần ảnh mũi
        // thật chụp từ app dẫn đường, lưu thành fixture; đó là việc tiếp theo, và tôi không giả vờ test này
        // đã phủ nó.
        val f = frame(32, 32) { x, y -> x >= 16 && y >= 8 }
        assertEquals(ArrowClassifier.classify(f), ArrowClassifier.classify(f))
        assertEquals(ArrowClassifier.classify(frame(32, 32) { _, _ -> false }), null)
    }
}
