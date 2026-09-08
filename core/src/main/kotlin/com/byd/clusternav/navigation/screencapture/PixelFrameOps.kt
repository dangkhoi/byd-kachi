package com.byd.clusternav.navigation.screencapture

import com.byd.clusternav.navigation.ArrayPixelFrame
import com.byd.clusternav.navigation.PixelFrame

/**
 * Cắt (crop) một [PixelFrame] về một [CropRect] — THUẦN, không Android. Cho phép chuỗi
 * capture → CaptureRouter bounds → crop → `ManeuverSignature`/`VietMapCameraMatcher` chạy & test off-car.
 *
 * Trên xe lớp :app crop thẳng `Bitmap` (rẻ hơn) rồi bọc `BitmapPixelFrame`; hàm này là đường THUẦN tương
 * đương, đủ để khoá logic pipeline mà không cần thiết bị.
 */
object PixelFrameOps {

    /**
     * Trả [PixelFrame] chứa đúng pixel trong [rect] của [src] (clamp về biên [src]). null nếu:
     *   - [src].argb() null (không đọc được), hoặc
     *   - vùng giao rỗng (rect ngoài khung / bề rộng-cao ≤ 0).
     */
    fun crop(src: PixelFrame, rect: CropRect): PixelFrame? {
        val srcPx = src.argb() ?: return null
        val bound = rect.clampTo(CropRect(0, 0, src.width, src.height))
        if (bound.isEmpty()) return null
        val w = bound.width
        val h = bound.height
        val out = IntArray(w * h)
        var di = 0
        var y = bound.top
        while (y < bound.bottom) {
            val rowBase = y * src.width
            var x = bound.left
            while (x < bound.right) {
                out[di++] = srcPx[rowBase + x]
                x++
            }
            y++
        }
        return ArrayPixelFrame(w, h, out)
    }

    /**
     * Đảo màu ([255] − mỗi kênh RGB, giữ alpha) — dùng để **chuẩn hoá light↔dark** cho glyph locator.
     *
     * VÌ SAO (owner 2026-08-24 + [ĐO]): [NavGlyphLocator.locate] chỉ dò **mực SÁNG trên nền TỐI**. App dẫn ở
     * **light/day theme** vẽ mũi tên TỐI trên nền SÁNG ⇒ locate trả null ⇒ "Waze never appears / VietMap dark
     * ban ngày". Đảo màu một khung day-theme biến nó thành night-theme tương đương ⇒ locator + registry
     * (đều quy ước sáng-trên-tối) chạy y nguyên. Tất định; đảo hai lần = chính nó.
     */
    fun invert(src: PixelFrame): PixelFrame? {
        val px = src.argb() ?: return null
        val out = IntArray(px.size) { i ->
            val c = px[i]
            (c and -0x1000000) or
                ((255 - ((c ushr 16) and 0xFF)) shl 16) or
                ((255 - ((c ushr 8) and 0xFF)) shl 8) or
                (255 - (c and 0xFF))
        }
        return ArrayPixelFrame(src.width, src.height, out)
    }
}
