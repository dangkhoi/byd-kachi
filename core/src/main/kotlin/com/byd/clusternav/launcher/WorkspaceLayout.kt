package com.byd.clusternav.launcher

/**
 * Bố cục workspace (landscape) — MIRROR chính xác prototype đã duyệt (docs/prototypes/kachi-workspace.html).
 * Pure JVM (:core) → tính khung ô test được off-car/emulator. UI (:app WorkspaceView) chỉ đặt view theo các Rect này.
 *
 * 5 preset: 1 ô · 2 cột · 2 hàng · 3 (trái to + phải chia trên/dưới) · 4 ô.
 */
enum class LayoutPreset(val slotCount: Int) {
    ONE(1),
    TWO_COL(2),
    TWO_ROW(2),
    THREE(3),
    QUAD(4);

    /**
     * Nhãn cho người đọc (S1) — thanh trên chỉ có **icon**, còn màn Cài đặt bày cả 5 bố cục nên phải có chữ.
     *
     * Đặt ở `:core` theo đúng lối các enum khác của launcher ([Quantity.label] · [Domain.label] · [ImageFit.label] ·
     * [ThemeMode.label]): câu chữ người dùng đọc thì kiểm được off-car, còn `:app` chỉ giữ **bảng màu**. Khai bằng
     * `get()` chứ không thêm tham số hàm dựng ⇒ 5 dòng khai ở trên **không đổi một ký tự**, nên không kéo theo sửa
     * ở mọi chỗ đang dựng enum này.
     */
    val label: String
        get() = when (this) {
            ONE -> "1 ô"
            TWO_COL -> "2 cột"
            TWO_ROW -> "2 hàng"
            THREE -> "3 ô"
            QUAD -> "4 ô"
        }
}

/** Khung 1 ô theo px thiết bị: [left,top,right,bottom]. index 0..3 (khớp thứ tự slot). */
data class SlotRect(
    val index: Int,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

object WorkspaceLayout {
    /** Tỉ lệ cột trái của preset THREE — khớp prototype grid 1.55fr : 1fr. */
    const val THREE_LEFT_FRACTION: Double = 1.55 / 2.55

    /**
     * Tính danh sách [SlotRect] cho [preset] trong vùng [width]x[height] với [gap] px giữa các ô.
     * Trả về đúng [LayoutPreset.slotCount] ô, KHÔNG chồng nhau, lấp đầy vùng (trừ khe gap).
     */
    fun slots(preset: LayoutPreset, width: Int, height: Int, gap: Int = 0): List<SlotRect> {
        require(width > 0 && height > 0) { "width/height must be > 0 (was $width x $height)" }
        val g = gap.coerceAtLeast(0)
        return when (preset) {
            LayoutPreset.ONE -> listOf(SlotRect(0, 0, 0, width, height))

            LayoutPreset.TWO_COL -> {
                val colW = (width - g) / 2
                listOf(
                    SlotRect(0, 0, 0, colW, height),
                    SlotRect(1, colW + g, 0, width, height),
                )
            }

            LayoutPreset.TWO_ROW -> {
                val rowH = (height - g) / 2
                listOf(
                    SlotRect(0, 0, 0, width, rowH),
                    SlotRect(1, 0, rowH + g, width, height),
                )
            }

            LayoutPreset.THREE -> {
                val usableW = width - g
                val leftW = (usableW * THREE_LEFT_FRACTION).toInt()
                val rowH = (height - g) / 2
                val rightLeft = leftW + g
                listOf(
                    SlotRect(0, 0, 0, leftW, height),               // trái to, cao full
                    SlotRect(1, rightLeft, 0, width, rowH),          // phải-trên
                    SlotRect(2, rightLeft, rowH + g, width, height), // phải-dưới
                )
            }

            LayoutPreset.QUAD -> {
                val colW = (width - g) / 2
                val rowH = (height - g) / 2
                val col1 = colW + g
                val row1 = rowH + g
                listOf(
                    SlotRect(0, 0, 0, colW, rowH),          // TL
                    SlotRect(1, col1, 0, width, rowH),       // TR
                    SlotRect(2, 0, row1, colW, height),      // BL
                    SlotRect(3, col1, row1, width, height),  // BR
                )
            }
        }
    }
}
