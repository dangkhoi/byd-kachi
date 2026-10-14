package com.byd.clusternav.launcher

import com.byd.clusternav.launcher.ContrastBackdrop.Layer

/**
 * ═══ 3.1.5 R29 pha 2 — GHÉP CHỒNG LỚP thành nền cho bộ giải (`:core`, thuần, không mã màu) ════════════════════════════
 *
 * Spec `kachi-301-cast-steps-dialog-ia.html` §4.7 (a). Mã vẽ thật có lớp KHÔNG phẳng một màu: bề mặt thẻ chuyển sắc dọc (hai
 * đầu), dải che đỉnh/đáy màn chuyển sắc tới trong suốt. Một phần tử nằm đâu đó trong các lớp ấy ⇒ mô tả mỗi lớp bằng các
 * BIẾN THỂ (mọi màu nó có thể có dưới phần tử), rồi [compose] ghép. Mã màu do `:app` truyền vào (`InkModel`); đây chỉ là
 * phép ghép + phép lấy mẫu — test off-device ở `ContrastStackTest`.
 */
object ContrastStack {

    /**
     * Ghép NỀN cuối: [base] (hộp ảnh sắc) × tích các nhóm biến thể [groups] (theo đúng thứ tự vẽ: làm tối · dải che · thẻ ngoài ·
     * thẻ trong…). Một tổ hợp ⇒ chồng CHÍNH XÁC (giữ tương quan góc tối/sáng giữa ảnh sắc và ảnh mờ). Nhiều ⇒ HỢP hộp nền ghép
     * của mọi tổ hợp — mỗi lớp đơn điệu từng kênh nên mọi điểm ảnh thật nằm trong hợp (bảo thủ: có thể xin lớp đỡ dư, không bao
     * giờ khai đạt khi không đạt). Nhóm rỗng bị bỏ qua.
     */
    fun compose(base: ToneBox, groups: List<List<List<Layer>>>): ContrastBackdrop {
        var combos: List<List<Layer>> = listOf(emptyList())
        for (g in groups) if (g.isNotEmpty()) combos = combos.flatMap { c -> g.map { c + it } }
        if (combos.size == 1) return ContrastBackdrop(base, combos[0])
        return ContrastBackdrop(combos.map { ContrastBackdrop(base, it).ground() }.reduce { a, b -> a.union(b) })
    }

    /**
     * Bề mặt chuyển sắc dọc ([surfaces] = các đầu, ĐÃ nhân độ đục) + lớp phẳng phía trên [over] (0 = không có) ⇒ một biến thể
     * mỗi đầu. Hai đầu cùng alpha ⇒ mọi điểm giữa nằm giữa hai biến thể (phép trộn tuyến tính theo kênh). Trùng ⇒ một.
     */
    fun surfaceVariants(surfaces: IntArray, over: Int = 0): List<List<Layer>> =
        surfaces.distinct().map { s -> listOfNotNull(Layer.Flat(s), over.takeIf { ColorMath.alpha(it) > 0 }?.let { Layer.Flat(it) }) }

    /**
     * Chồng của THẺ KÍNH, đúng thứ tự `WallWindowDrawable.draw` rồi bề mặt: ảnh mờ [glass] × [fade] → lớp che ĐANG VẼ
     * ([veilDrawn], đã nhân độ đục) → nhuộm ([tintDrawn], alpha 0 = không) → bề mặt ([surfaces] + [domainDrawn]).
     * [fade] = 0 ⇒ `draw` không vẽ lớp nào của cửa sổ ⇒ chỉ còn bề mặt.
     * @param photoAlpha alpha THẬT của lớp ảnh (0..1) — byte `Paint.alpha` chỗ vẽ đặt, chia 255.
     */
    fun glassVariants(
        glass: ToneBox?, fade: Double, photoAlpha: Double, veilDrawn: Int, tintDrawn: Int, surfaces: IntArray, domainDrawn: Int,
    ): List<List<Layer>> {
        val window = if (fade <= 0.0) emptyList() else listOfNotNull(
            glass?.let { Layer.Photo(it, photoAlpha) },
            Layer.Flat(veilDrawn),
            tintDrawn.takeIf { ColorMath.alpha(it) > 0 }?.let { Layer.Flat(it) },
        )
        return surfaceVariants(surfaces, domainDrawn).map { window + it }
    }

    /**
     * Hai dải che đỉnh/đáy màn (`WallView`: cao [fraction] × [screenH], màu [bar] (đã nhân độ đục) → trong suốt) dưới vùng
     * `[top, bottom)`. Trả các BIẾN THỂ lớp dải; vùng không chạm dải ⇒ đúng một biến thể rỗng. `LinearGradient` nội suy KHÔNG nhân
     * trước alpha (Skia mặc định) ⇒ kênh màu cũng về 0 cùng alpha — không đơn điệu theo kênh nên lấy mẫu mỗi [BAND_STEP] trên
     * khoảng vùng chạm (gồm hai đầu), thay vì suy từ hai đầu.
     */
    fun bandVariants(bar: Int, top: Int, bottom: Int, screenH: Int, fraction: Float): List<List<Layer>> {
        val bandH = screenH * fraction.toDouble()
        if (screenH <= 0 || bandH <= 0.0 || ColorMath.alpha(bar) == 0 || bottom <= top) return listOf(emptyList())
        // Vị trí trong dải: 0 = mép màn (đậm nhất), 1 = mép trong (trong suốt). Đỉnh: y/bandH; đáy: (H − y)/bandH.
        val spans = ArrayList<ClosedFloatingPointRange<Double>>()
        if (top < bandH) spans += (top / bandH).coerceIn(0.0, 1.0)..(minOf(bottom.toDouble(), bandH) / bandH).coerceIn(0.0, 1.0)
        if (bottom > screenH - bandH) {
            spans += ((screenH - bottom) / bandH).coerceIn(0.0, 1.0)..((screenH - maxOf(top.toDouble(), screenH - bandH)) / bandH).coerceIn(0.0, 1.0)
        }
        if (spans.isEmpty()) return listOf(emptyList())
        val out = LinkedHashSet<List<Layer>>()
        if (top < screenH - bandH && bottom > bandH) out += emptyList<Layer>()   // phần vùng nằm giữa, dải không phủ
        for (s in spans) {
            var q = s.start
            while (true) {
                out += listOf(Layer.Flat(towardClear(bar, q)))
                if (q >= s.endInclusive) break
                q = minOf(s.endInclusive, q + BAND_STEP)
            }
        }
        return out.toList()
    }

    const val BAND_STEP = 0.05

    /** Màu dải ở vị trí [q] (0 = [c], 1 = `0x00000000`) — nội suy từng kênh, không nhân trước (như `LinearGradient`). */
    fun towardClear(c: Int, q: Double): Int {
        val k = (1 - q).coerceIn(0.0, 1.0)
        fun ch(v: Int) = Math.round(v * k).toInt()
        return ColorMath.argb(ch(ColorMath.alpha(c)), ch(ColorMath.red(c)), ch(ColorMath.green(c)), ch(ColorMath.blue(c)))
    }
}
