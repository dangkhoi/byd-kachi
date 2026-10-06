package com.byd.clusternav.launcher

/**
 * ═══ 2.93 `WIDGET-CAR-STRIP-LAYOUT` — widget *Trạng thái xe*: hình xe TRÊN chú thích hay CẠNH chú thích (thuần, px) ═════════
 *
 * Bệnh [ĐO máy ảo 06/10 sau vá lề 2.92]: khung rộng thấp 1558×123 px — chú thích *"Cửa đóng"* xếp DƯỚI hình nên hình xe
 * (nhìn từ trên, rộng:cao ≈ 0,46) chỉ còn ~52 px cao, trong khi bề ngang thừa hơn 1 400 px.
 *
 * Luật (đo, không theo mã widget hay tên khung — CLAUDE.md §7): tính cỡ hình xe VẼ ĐƯỢC ở hai cách xếp rồi chọn cách cho
 * hình TO hơn rõ rệt:
 *  - [Arrange.STACK] (như 2.92): hộp hình = (W − 2p) × (H − 2p − khe − cao chú thích), chú thích dưới, căn giữa;
 *  - [Arrange.SIDE]: hộp hình cao trọn (H − 2p), rộng vừa đúng hình (≤ phần còn lại sau chú thích + khe); cả cụm hình + khe
 *    + chú thích căn giữa ngang, chú thích căn giữa dọc.
 * Hình xe giữ tỉ lệ (letterbox) ⇒ cỡ vẽ = min(cao hộp, rộng hộp ÷ tỉ lệ). SIDE chỉ thắng khi hình cao hơn ít nhất [GAIN]
 * (khung gần vuông/đứng giữ đúng dáng 2.92 — CLAUDE.md §6). Bề ngang chú thích do tầng vẽ đo trên chữ DÀI NHẤT trong các chữ
 * có thể hiện ⇒ đổi chữ (cửa mở/đóng) không lật cách xếp.
 */
object CarStripFit {

    enum class Arrange { STACK, SIDE }

    /** Cách xếp + hộp dành cho hình xe ([artW]×[artH], px) + cỡ hình vẽ được ([carH], px cao). */
    data class Plan(val arrange: Arrange, val artW: Int, val artH: Int, val carH: Double)

    /** SIDE phải cho hình cao hơn STACK ít nhất 25 % mới đổi cách xếp. [ĐỀ XUẤT — owner chốt]. */
    const val GAIN = 0.25

    /**
     * Khung [w]×[h], lề trong [pad], khe hình–chú thích [gap], chú thích rộng [capW] × cao [capH] (chữ dài nhất), tỉ lệ
     * rộng:cao của hình xe [aspect] (> 0). Khung không dương ⇒ STACK rỗng.
     */
    fun plan(w: Int, h: Int, pad: Int, gap: Int, capW: Int, capH: Int, aspect: Double): Plan {
        val iw = (w - 2 * pad).coerceAtLeast(0)
        val ih = (h - 2 * pad).coerceAtLeast(0)
        val a = if (aspect > 0.0) aspect else 1.0
        val stackH = (ih - gap - capH).coerceAtLeast(0)
        val stackCar = minOf(stackH.toDouble(), iw / a)
        val stack = Plan(Arrange.STACK, iw, stackH, stackCar)
        val sideRoom = iw - gap - capW
        if (sideRoom <= 0 || ih <= 0) return stack
        val sideCar = minOf(ih.toDouble(), sideRoom / a)
        if (sideCar < stackCar * (1 + GAIN)) return stack
        val artW = minOf(sideRoom, kotlin.math.ceil(sideCar * a).toInt())
        return Plan(Arrange.SIDE, artW, ih, sideCar)
    }
}
