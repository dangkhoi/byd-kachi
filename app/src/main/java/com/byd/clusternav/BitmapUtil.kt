package com.byd.clusternav

import com.byd.clusternav.navigation.NavFormat
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable

/** Tiện ích đồ hoạ thuần — chỉ phục vụ card fallback (ClusterNavActivity). Tách khỏi nav-domain (NavFormat). */
object BitmapUtil {

    /** Đổi Drawable (mũi tên từ notification) -> Bitmap copy an toàn. null nếu không dựng được. */
    fun drawableToBitmap(d: Drawable): Bitmap? {
        // Copy: bitmap gốc của notification có thể bị recycle sau callback -> giữ ref sẽ crash khi vẽ.
        if (d is BitmapDrawable && d.bitmap != null) {
            // HARDWARE bitmap (API 29+) không vẽ/đọc pixel được -> ép về software config khi copy.
            val cfg = d.bitmap.config?.takeIf { it != Bitmap.Config.HARDWARE } ?: Bitmap.Config.ARGB_8888
            return d.bitmap.copy(cfg, false)
        }
        val w = if (d.intrinsicWidth > 0) d.intrinsicWidth else 96
        val h = if (d.intrinsicHeight > 0) d.intrinsicHeight else 96
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        d.setBounds(0, 0, c.width, c.height)
        d.draw(c)
        return bmp
    }
}
