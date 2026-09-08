package com.byd.clusternav

import android.graphics.Bitmap
import com.byd.clusternav.navigation.PixelFrame

/**
 * Lớp bọc `android.graphics.Bitmap` cho port [PixelFrame].
 *
 * Đây là toàn bộ phần Android của việc đọc điểm ảnh. Nhờ nó mà thuật toán nhận dạng hướng rẽ sống được
 * trong `:core` và test được off-car; trước 2026-07-27 nó nằm trong `:app` chỉ vì kiểu Bitmap.
 */
class BitmapPixelFrame(private val bitmap: Bitmap) : PixelFrame {
    override val width: Int get() = bitmap.width
    override val height: Int get() = bitmap.height

    override fun argb(): IntArray? {
        val w = width
        val h = height
        if (w <= 0 || h <= 0) return null
        val out = IntArray(w * h)
        return runCatching { bitmap.getPixels(out, 0, w, 0, 0, w, h); out }.getOrNull()
    }
}

/** Tiện cho call site cũ: `bmp.asPixelFrame()`. */
fun Bitmap.asPixelFrame(): PixelFrame = BitmapPixelFrame(this)
