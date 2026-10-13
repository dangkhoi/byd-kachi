package com.byd.clusternav.launcher

/**
 * 3.01 R22·B — một ô của BẢNG ICON ([GroupLayout.ICONS]). [cell] = trạng thái đọc được (sáng khi khác NEUTRAL), [action]
 * = nút điều khiển CÙNG vật (chạm ô = nút đó); có thể chỉ một trong hai. [name] đã theo ngôn ngữ (nhãn ngắn của ô đọc/nút — `:core` điền từ displayShortLabel).
 */
data class IconTile(val icon: String, val name: String, val cell: GroupCell?, val action: GroupActionCell?)

/**
 * ═══ 3.01 R22·B — BẢNG ICON của ô nhóm (owner 13/10: *"lưới nút chữ lặp nhìn rối"* → icon là nút) ═══════════════
 *
 * Gộp ô XEM và NÚT của cùng một vật thành MỘT ô icon. "Cùng vật" = cùng [GroupCell.icon]/[GroupActionCell.icon] — đúng
 * quy ước [GroupActionMatrix] đã dùng (nút `headl` mang `ic-car-front-highbeam` như ô đọc `light_high_beam`). Không có bảng
 * tra theo mã; nhóm nào khai [GroupLayout.ICONS] là có. Nút không có ô đọc cùng icon ⇒ ô riêng (sáng theo lệnh cuối).
 */
object GroupIconBoard {

    fun of(m: GroupBoardModel): List<IconTile>? {
        if (m.layout != GroupLayout.ICONS) return null
        val tiles = m.cells.map { IconTile(it.icon, it.label, it, null) }.toMutableList()
        m.actions.forEach { a ->
            val i = tiles.indexOfFirst { it.icon == a.icon && it.action == null }
            if (i >= 0) tiles[i] = tiles[i].copy(action = a) else tiles += IconTile(a.icon, a.label, null, a)
        }
        return tiles
    }

    /**
     * Lưới cho [n] ô trong khung [widthPx] × [heightPx]: chọn số cột cho ô VUÔNG nhất mà cạnh ngắn vẫn ≥ [minPx] (đích
     * chạm). Trả `(cột, hàng)` hoặc `null` khi không cách chia nào đủ đích chạm (khung ngoài lùi ô tóm tắt).
     */
    fun grid(n: Int, widthPx: Int, heightPx: Int, minPx: Int): Pair<Int, Int>? {
        if (n <= 0) return null
        var best: Pair<Int, Int>? = null
        var bestScore = -1f
        for (cols in 1..n) {
            val rows = (n + cols - 1) / cols
            val tw = widthPx / cols; val th = heightPx / rows
            if (minOf(tw, th) < minPx) continue
            // Vuông nhất (tỉ số cạnh gần 1), trừ điểm mỗi chỗ trống (9×2 cho 10 ô vuông hơn nhưng bỏ trống 8 chỗ).
            val score = minOf(tw, th).toFloat() / maxOf(tw, th) - (cols * rows - n) * EMPTY_COST
            if (score > bestScore) { bestScore = score; best = cols to rows }
        }
        return best
    }

    /** Điểm trừ cho mỗi chỗ trống của lưới (so với độ vuông 0..1 của ô). */
    private const val EMPTY_COST = 0.1f
}
