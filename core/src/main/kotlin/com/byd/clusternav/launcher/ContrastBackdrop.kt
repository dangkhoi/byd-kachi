package com.byd.clusternav.launcher

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * ═══ 3.1 R29 — NỀN THẬT dưới một phần tử: mô hình CHỒNG LỚP (`:core`, thuần) ═════════════════════════════════════════════
 *
 * Chữ/icon không nằm trên "màu nền của chủ đề" — nó nằm trên một CHỒNG: ảnh nền sắc nét ([base], hộp đo bởi [LumaField])
 * → lớp làm tối `wallScrim` × mức người dùng → (trên thẻ kính) ảnh mờ × độ đục chung → lớp che `bg` theo [GlassVeil] →
 * nhuộm màu trội → bề mặt thẻ × độ đục người dùng. Đúng thứ tự `WallView` + `WallWindowDrawable`
 * vẽ. Bản cũ đo một lớp rồi coi các lớp kia cố định ⇒ đổi o20/o70, đổi ảnh, đổi chủ đề là tương phản "hên xui".
 *
 * ## Vì sao hai góc hộp là ĐỦ
 * `t ∈ [0,1]` đi từ góc tối tới góc sáng của MỌI hộp ảnh cùng lúc. `over` đơn điệu không giảm theo từng kênh, độ chói đơn
 * điệu theo từng kênh ⇒ với mọi tổ hợp điểm ảnh nằm trong các hộp (kể cả điểm tối ở ảnh sắc + sáng ở ảnh mờ), độ chói nền
 * ghép nằm giữa `groundAt(0)` và `groundAt(1)`. Không giả định "xám thay sắc" — bài `ContrastBackdropTest.moi diem anh…`
 * kiểm điều này trên điểm ảnh ngẫu nhiên, chính xác tới từng đơn vị.
 */
class ContrastBackdrop(val base: ToneBox, val layers: List<Layer> = emptyList()) {

    /** Một lớp trong chồng. */
    sealed interface Layer {
        /** Trộn lớp lên nền ĐỤC [under] tại vị trí [t] của các hộp ảnh. */
        fun over(under: Int, t: Double): Int

        /** Lớp phẳng một màu (alpha nằm trong chính [color]): lớp làm tối · lớp che · nhuộm · bề mặt thẻ · mặt đĩa nút. */
        data class Flat(val color: Int) : Layer {
            override fun over(under: Int, t: Double): Int = if (ColorMath.alpha(color) == 0) under else ColorMath.over(color, under)
        }

        /** Lớp ẢNH có hộp [box] vẽ ở độ đục [alpha] (0..1) — ảnh mờ của thẻ kính × độ đục chung. */
        data class Photo(val box: ToneBox, val alpha: Double) : Layer {
            private val a = (alpha.coerceIn(0.0, 1.0) * 255).roundToInt()
            override fun over(under: Int, t: Double): Int = if (a == 0) under else ColorMath.over(ColorMath.withAlpha(box.at(t), a), under)
        }
    }

    /** Nền đục tại [t] (0 = góc tối của mọi hộp ảnh, 1 = góc sáng). */
    fun groundAt(t: Double): Int {
        var g = base.at(t)
        for (l in layers) g = l.over(g, t)
        return g
    }

    /** Thêm một lớp TRÊN cùng (mặt đĩa / nền của chính nút vẽ dưới nét). */
    fun with(layer: Layer): ContrastBackdrop = ContrastBackdrop(base, layers + layer)

    /** Hộp của nền ghép (hai góc `t = 0/1`). */
    fun ground(): ToneBox = ToneBox(groundAt(0.0), groundAt(1.0))

    /**
     * Tỉ số tương phản TỆ NHẤT của [ink] trên mọi nền trong chồng.
     *  • mực ĐỤC: tỉ số chỉ phụ thuộc độ chói nền, hình chữ V quanh độ chói của mực ⇒ chính xác bằng hai góc; độ chói của
     *    mực nằm TRONG khoảng nền thì có chỗ mực tan vào nền ⇒ 1.0 (đúng sự thật, không làm tròn đẹp).
     *  • mực có alpha: lấy mẫu [SAMPLES] + 1 điểm rồi thu hẹp quanh điểm tệ nhất (tam phân) — đáy chữ V nhọn, mẫu đều bỏ sót
     *    được nên phải dò thêm (bài vét 2001 điểm khoá sai số).
     */
    fun worst(ink: Int): Double {
        if (ColorMath.alpha(ink) == 255) {
            val a = Luma.of(groundAt(0.0)); val b = Luma.of(groundAt(1.0))
            val li = Luma.of(ink)
            if (li >= min(a, b) && li <= max(a, b)) return 1.0
            return min(ColorMath.ratio(ink, groundAt(0.0)), ColorMath.ratio(ink, groundAt(1.0)))
        }
        fun at(t: Double) = ColorMath.ratio(ink, groundAt(t))
        var bestI = 0; var w = Double.MAX_VALUE
        for (i in 0..SAMPLES) { val v = at(i.toDouble() / SAMPLES); if (v < w) { w = v; bestI = i } }
        var lo = max(0, bestI - 1).toDouble() / SAMPLES
        var hi = min(SAMPLES, bestI + 1).toDouble() / SAMPLES
        repeat(REFINE) {
            val m1 = lo + (hi - lo) / 3; val m2 = hi - (hi - lo) / 3
            val v1 = at(m1); val v2 = at(m2)
            w = min(w, min(v1, v2))
            if (v1 < v2) hi = m2 else lo = m1
        }
        return w
    }

    override fun toString(): String =
        "ContrastBackdrop(base=${ColorMath.hex(base.lo)}…${ColorMath.hex(base.hi)}, layers=${layers.size})"

    companion object {
        const val SAMPLES = 32
        private const val REFINE = 24
    }
}
