package com.byd.clusternav.launcher

/**
 * ═══ VISUAL-REFRESH P1b · §4.10 mục (4) — MÀU TRỘI CỦA ẢNH NỀN, tính MỘT LẦN ═══════════════════════════════════
 *
 * *"Lấy 2–3 màu trội của ảnh, MỘT LẦN (cùng lượt tính ở mục 1) để nhuộm rất nhẹ bề mặt và màu nhấn ⇒ giao diện
 * 'ăn' theo ảnh thay vì nằm cạnh ảnh. Dùng chung hạ tầng với R8 (ô 'theo ảnh nền')."*
 *
 * Lượng tử hoá **4 bit/kênh** (4 096 ô), đếm điểm ảnh, **nhân trọng số bão hoà** (một bức ảnh cát có 60 % điểm
 * xám be và 5 % điểm xanh biển: màu nhấn *theo ảnh* nên là xanh biển — thứ mắt nhớ — chứ không phải be), rồi
 * chọn lần lượt các ô có điểm cao nhất mà **cách sắc ≥ 30°** với các ô đã chọn (ba màu trội không được là ba
 * bậc của cùng một màu). Không thư viện, không `Bitmap`: nhận mảng ARGB thuần nên test off-car.
 *
 * Ảnh ít màu (đen trắng, sương mù) ⇒ trả ít hơn [n] màu; **không bịa** thêm.
 */
object DominantColors {

    /** Khoảng cách sắc tối thiểu giữa hai màu trội. */
    const val MIN_HUE_GAP = 30.0

    /**
     * @param pixels ARGB (alpha bỏ qua). Nên là ảnh đã thu nhỏ (¼ màn hình là đủ và đúng tinh thần "tính một lần").
     * @param n số màu tối đa.
     * @return màu trội, đục, xếp theo điểm giảm dần; rỗng nếu [pixels] rỗng.
     */
    fun of(pixels: IntArray, n: Int = 3): IntArray {
        if (pixels.isEmpty() || n <= 0) return IntArray(0)
        val count = IntArray(4096)
        val sumR = LongArray(4096); val sumG = LongArray(4096); val sumB = LongArray(4096)
        for (p in pixels) {
            val r = (p ushr 16) and 0xff; val g = (p ushr 8) and 0xff; val b = p and 0xff
            val key = ((r shr 4) shl 8) or ((g shr 4) shl 4) or (b shr 4)
            count[key]++; sumR[key] += r; sumG[key] += g; sumB[key] += b
        }
        // Điểm = số điểm ảnh × (0.1 + 1.5 × bão hoà): màu xám vẫn có cơ hội (ảnh đen trắng), màu rực được ưu tiên
        // mạnh — [ĐO] bài `DominantColorsTest`: 35 % biển xanh thắng 65 % cát be, còn 15 % thì thua (ảnh đó LÀ ảnh cát).
        val scored = ArrayList<Pair<Int, Double>>()
        for (k in 0 until 4096) {
            val c = count[k]
            if (c == 0) continue
            val avg = ColorMath.argb(255, (sumR[k] / c).toInt(), (sumG[k] / c).toInt(), (sumB[k] / c).toInt())
            val sat = ColorMath.hsl(avg)[1]
            scored += avg to c * (0.1 + 1.5 * sat)
        }
        scored.sortByDescending { it.second }
        val out = ArrayList<Int>(n)
        for ((c, _) in scored) {
            if (out.size >= n) break
            val distinct = out.all { chosen ->
                // Hai màu gần xám thì so theo độ sáng thay vì sắc — sắc của xám là nhiễu.
                val s1 = ColorMath.hsl(c)[1]; val s2 = ColorMath.hsl(chosen)[1]
                if (s1 < 0.12 && s2 < 0.12) kotlin.math.abs(ColorMath.hsl(c)[2] - ColorMath.hsl(chosen)[2]) >= 0.25
                else ColorMath.hueDistance(c, chosen) >= MIN_HUE_GAP || (s1 < 0.12) != (s2 < 0.12)
            }
            if (distinct) out += c
        }
        return out.toIntArray()
    }

    /**
     * Hạt giống màu nhấn *theo ảnh nền* (R8 · AC8.1): màu trội **rực nhất** trong tối đa ba màu, kéo về bậc sáng
     * đọc được cho chủ đề ([dark] ⇒ sáng vừa, ngược lại ⇒ đậm vừa) để phần chuyển sắc của bảng màu có chỗ đứng.
     * Ảnh không có màu (bão hoà < 0.12 ở mọi màu trội) ⇒ vẫn trả màu đầu — accent bạc, đúng như ảnh.
     */
    fun accentSeed(dominant: IntArray, dark: Boolean): Int? {
        if (dominant.isEmpty()) return null
        val pick = dominant.maxByOrNull { ColorMath.hsl(it)[1] } ?: return null
        val h = ColorMath.hsl(pick)
        val l = if (dark) h[2].coerceIn(0.55, 0.72) else h[2].coerceIn(0.32, 0.48)
        return ColorMath.fromHsl(h[0], h[1].coerceAtMost(0.9), l)
    }
}
