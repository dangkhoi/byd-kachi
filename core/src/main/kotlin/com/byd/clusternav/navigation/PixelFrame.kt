package com.byd.clusternav.navigation

/**
 * Một khung ảnh chỉ đủ để đọc điểm ảnh. Không biết Android.
 *
 * Vì sao cần: `ManeuverSignature` (226 LOC) và `ArrowClassifier` (66 LOC) là thuật toán thuần — chúng chỉ
 * cần `width`, `height` và mảng ARGB — nhưng bị khoá vào `android.graphics.Bitmap`, nên không test được
 * ngoài Android và không thể sống trong `:core`. Đây là dạng lỗi mà checklist chưa có quy tắc: code cần một
 * **kiểu** của Android nhưng không cần **hành vi** nào của Android. Cách chữa giống port `AtomicBytes`:
 * khai báo cái tối thiểu ở `:core`, để lớp bọc Bitmap ở `:app`.
 */
interface PixelFrame {
    val width: Int
    val height: Int

    /** Đọc toàn bộ khung theo hàng, định dạng ARGB_8888. Trả null nếu không đọc được. */
    fun argb(): IntArray?
}

/** Khung dựng sẵn từ mảng — dùng cho test và cho nguồn không phải Bitmap. */
class ArrayPixelFrame(
    override val width: Int,
    override val height: Int,
    private val pixels: IntArray,
) : PixelFrame {
    init {
        require(pixels.size >= width * height) { "mảng điểm ảnh nhỏ hơn $width×$height" }
    }

    override fun argb(): IntArray = pixels
}
