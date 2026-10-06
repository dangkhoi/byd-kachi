package com.byd.clusternav.launcher

/**
 * PHÉP KẸP cho trình vẽ bố cục — **thuần**, không biết gì về Android.
 *
 * ## Vì sao tách ra khỏi tầng vẽ
 * Lượt soát độc lập 2026-09-11 [ĐO]: `GridEditorView` (:app) chứa phép di/đổi-cỡ khung — logic **thuần** nhưng nằm
 * trong `onTouchEvent` nên **0 test, 0 guard**. Đúng chỗ đó đã từng có một lỗi **SẬP**: `coerceIn` ném lỗi khi
 * `min > max` (bố cục hỏng ⇒ `COLS - cols` âm) nên *kéo một khung là sập*. Bản vá lúc đó đúng, nhưng không có bài
 * nào khoá — sửa nhầm lần sau là sập lại y như cũ.
 *
 * Ở đây thì kiểm được off-car bằng bảng số, kể cả những ca không dựng nổi bằng tay trên máy ảo (khung rộng hơn lưới,
 * góc âm, khung nằm hẳn ngoài lưới…).
 */
object GridEditorLogic {

    /**
     * Di khung [f] tới ô ([col], [row]) — kẹp trong lưới, **không bao giờ ném lỗi** kể cả với khung hỏng.
     *
     * Khung rộng/cao hơn lưới thì trần kẹp âm; ta hạ trần về đúng sàn (0) thay vì để `coerceIn` ném — người dùng
     * kéo một bố cục hỏng thì phải thấy nó *không di được*, KHÔNG phải thấy launcher chết.
     */
    fun move(f: GridFrame, col: Int, row: Int): GridFrame = f.copy(
        col = col.coerceIn(0, (WorkspaceGrid.COLS - f.cols).coerceAtLeast(0)),
        row = row.coerceIn(0, (WorkspaceGrid.ROWS - f.rows).coerceAtLeast(0)),
    )

    /** Hộp vẽ của bảng lưới trong view (px): góc trái-trên [x],[y] + cỡ [w]×[h]. */
    data class Box(val x: Float, val y: Float, val w: Float, val h: Float)

    /**
     * 2.93 `GRID-EDITOR-ASPECT` — hộp vẽ lưới 12×6 trong view [viewW]×[viewH] (lề [pad] mỗi phía), giữ tỉ lệ của VÙNG Ô THẬT
     * [areaW]×[areaH] (khung `WorkspaceView` đã đo — màn trừ thanh trên + thanh nút theo cạnh/cỡ %), căn giữa. Vùng chưa đo
     * (≤ 0) ⇒ tỉ lệ [fallbackAspect] (màn). [ĐO mã] bản cũ co theo tỉ lệ CẢ MÀN ⇒ hình vẽ khác vùng ô thật (thanh nút cỡ
     * theo % lệch thêm tới ±9 % chiều cao — `DOCK-SCALE`).
     */
    fun drawBox(viewW: Int, viewH: Int, areaW: Int, areaH: Int, pad: Float, fallbackAspect: Float): Box {
        val aspect = if (areaW > 0 && areaH > 0) areaW.toFloat() / areaH else fallbackAspect.takeIf { it > 0f } ?: (16f / 9f)
        var bw = (viewW - pad * 2).coerceAtLeast(0f)
        var bh = bw / aspect
        if (bh > viewH - pad * 2) { bh = (viewH - pad * 2).coerceAtLeast(0f); bw = bh * aspect }
        return Box((viewW - bw) / 2f, (viewH - bh) / 2f, bw, bh)
    }

    /**
     * Đổi cỡ khung [f] thành [cols]×[rows] — kẹp trong lưới và **không nhỏ hơn khung tối thiểu**.
     *
     * Khung có góc nằm ngoài lưới thì `COLS - col` có thể nhỏ hơn cỡ tối thiểu ⇒ trần < sàn ⇒ cùng lỗi sập; nâng
     * trần lên đúng cỡ tối thiểu.
     */
    fun resize(f: GridFrame, cols: Int, rows: Int): GridFrame = f.copy(
        cols = cols.coerceIn(
            WorkspaceGrid.MIN_COLS,
            (WorkspaceGrid.COLS - f.col).coerceAtLeast(WorkspaceGrid.MIN_COLS),
        ),
        rows = rows.coerceIn(
            WorkspaceGrid.MIN_ROWS,
            (WorkspaceGrid.ROWS - f.row).coerceAtLeast(WorkspaceGrid.MIN_ROWS),
        ),
    )
}
