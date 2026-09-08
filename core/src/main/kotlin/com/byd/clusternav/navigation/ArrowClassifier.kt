package com.byd.clusternav.navigation


/**
 * Suy HƯỚNG RẼ từ ảnh mũi tên GMaps (notification largeIcon ~54×54). KHÔI PHỤC sau khi test xe xác nhận
 * small-icon của ReVanced GMaps LUÔN là logo (adaptiveproduct_maps_launcher) → cách DashCast không ăn,
 * hướng rẽ CHỈ ở large-icon bitmap. Heuristic: vị trí đầu mũi tên + trọng tâm "mực" lệch trái/phải.
 *
 * ⚠️ Ngưỡng (THRESH_*) là ƯỚC LƯỢNG — tune bằng bitmap thật (đã lưu ra file CollectStore mỗi cua, pull về).
 * Trả mã AMAP NEW_ICON (2 trái,3 phải,4 chếch trái,5 chếch phải,9 thẳng) hoặc null nếu quá mờ.
 */
object ArrowClassifier {
    private const val TAG = "ArrowClassifier"
    private const val THRESH_TURN = 0.14    // |off| ≥ -> rẽ hẳn (trái/phải)
    private const val THRESH_SLIGHT = 0.05  // |off| ≥ -> chếch
    private const val INK_ALPHA = 80
    private const val INK_LUM = 140

    fun classify(bmp: PixelFrame?): Int? {
        if (bmp == null) return null
        val w = bmp.width; val h = bmp.height
        if (w < 8 || h < 8) return null
        val px = bmp.argb() ?: return null

        var sumX = 0.0; var n = 0
        var headSumX = 0.0; var headN = 0
        var firstInkRow = -1
        loop@ for (y in 0 until h) {
            for (x in 0 until w) if (isInk(px[y * w + x])) { firstInkRow = y; break@loop }
        }
        if (firstInkRow < 0) return null
        val headCut = firstInkRow + (h - firstInkRow) * 35 / 100
        for (y in 0 until h) for (x in 0 until w) {
            if (!isInk(px[y * w + x])) continue
            sumX += x; n++
            if (y <= headCut) { headSumX += x; headN++ }
        }
        if (n < 10) return null
        val cx = (w - 1) / 2.0
        val comOff = (sumX / n - cx) / w
        val headOff = if (headN > 0) (headSumX / headN - cx) / w else comOff
        val off = headOff * 0.65 + comOff * 0.35   // đầu mũi tên quan trọng hơn thân

        val icon = when {
            off <= -THRESH_TURN -> 2
            off >= THRESH_TURN -> 3
            off <= -THRESH_SLIGHT -> 4
            off >= THRESH_SLIGHT -> 5
            else -> 9
        }
        return icon
    }

    private fun isInk(c: Int): Boolean {
        val a = (c ushr 24) and 0xff
        if (a < INK_ALPHA) return false
        val r = (c ushr 16) and 0xff; val g = (c ushr 8) and 0xff; val b = c and 0xff
        return (r * 299 + g * 587 + b * 114) / 1000 > INK_LUM
    }

    private fun fmt(d: Double): String = String.format(java.util.Locale.US, "%.3f", d)
}
