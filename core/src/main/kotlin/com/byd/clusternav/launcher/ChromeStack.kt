package com.byd.clusternav.launcher

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * ═══ 2.87 · R-OP3 — "THỨ NẰM TRÊN NỀN" và sàn đọc được của nó (`:core`, số học thuần) ══════════════════════════════
 *
 * Bản kiểm kê đọc-mã chỉ xét CHỮ nằm thẳng trên nền mờ (và chỉ với bảng màu mặc định). Bài quét
 * `ChromeOpacityContrastContractTest` [ĐO 2026-10-03, bản sao ColorMath — chưa đo pixel] tìm ra ba họ hỏng mà phép
 * xét đó không thấy:
 *  1. **vầng sáng của nền vẽ sẵn** (không ảnh nền) nhận màu nhấn ⇒ ở một số màu/tông (VD tối · Trắng ấm, sáng ·
 *     Lục ngọc) tâm vầng sáng đủ sáng/tối để chữ trên thanh/khay mờ 40–70 % tụt dưới 4.5:1;
 *  2. **thứ KHÔNG mờ nằm trên nền mờ** — ô BẬT (nền nhấn bán trong suốt), ô cảnh báo — đọc được hôm nay nhờ nền đục
 *     dưới nó; nền dưới trong đi thì chúng hỏng dù chính chúng không đổi;
 *  3. **ô TẮT mờ trên thanh nút** trên ảnh rất sáng: thanh đã giữ đúng hôm nay (trần), ô thì trong hơn.
 *
 * Nên sàn không phải một con số cho "chữ trên nền" mà cho **mọi chồng lớp** mà màn chính thật sự vẽ: [Stack] = các
 * lớp từ dưới lên ([Layer], có/không mờ theo bậc) + mực trên cùng. Luật chung: **chồng nào hôm nay (100 %) đạt 4.5:1
 * thì ở bậc mới vẫn đạt** — đúng nghiệm thu R-OP3.
 *
 * Mô hình alpha của một lớp mờ = phép nhân của `GradientDrawable.modulateAlpha` [ĐO AOSP r47
 * GradientDrawable.java]: `alpha × (a + (a >> 7)) >> 8` với `a = round(255·t)` — số đo phải nói về cái được VẼ
 * (bản đầu dùng `scaleAlpha` làm tròn khác 1/255 và hụt đúng ở mép 4.5).
 */
object ChromeStack {

    /** Sàn tương phản (WCAG AA chữ thường) — cùng số với mọi bài canh tương phản của dự án. */
    const val FLOOR = 4.5

    /** Bước lưới của mọi phép tìm sàn (hệ số và độ đục) — cùng bước với [GlassVeil.STEP]. */
    const val STEP = 0.05

    /** Lưới độ chói 0..1 (bước [STEP]) — "mọi ảnh có thể" khi một sàn phải đúng không phụ thuộc vùng ảnh nào. */
    val LUMS: List<Double> = List((1.0 / STEP).roundToInt() + 1) { it * STEP }

    /** Một lớp: các màu (đầu/cuối chuyển sắc — xét MỌI tổ hợp, thận trọng) + có mờ theo bậc hay không. */
    class Layer(val colors: IntArray, val fades: Boolean = false)

    /** Một thứ vẽ trên nền: các lớp từ DƯỚI lên + mực nằm trên cùng. */
    class Stack(val layers: List<Layer>, val inks: IntArray)

    /** `Drawable.alpha` cho hệ số [t] (0..1). */
    fun byteOf(t: Double): Int = (t * 255).roundToInt().coerceIn(0, 255)

    /** Alpha màu SAU khi `Drawable.alpha = byteOf(t)` nhân vào (modulateAlpha r47). `t ≥ 1` ⇒ không đổi. */
    fun drawnAlpha(alpha: Int, t: Double): Int {
        if (t >= 1.0) return alpha
        val a = byteOf(t)
        return (alpha * (a + (a shr 7))) shr 8
    }

    /** Màu [c] được vẽ với hệ số [t]. */
    fun faded(c: Int, t: Double): Int = ColorMath.withAlpha(c, drawnAlpha(ColorMath.alpha(c), t))

    /** Mực TỆ NHẤT của [stack] khi các lớp mờ vẽ ở hệ số [t], đặt trên nền ĐỤC [ground]. */
    fun worst(stack: Stack, t: Double, ground: Int): Double {
        var grounds = intArrayOf(ground)
        for (layer in stack.layers) {
            val next = IntArray(grounds.size * layer.colors.size)
            var i = 0
            for (g in grounds) for (c in layer.colors) next[i++] = ColorMath.over(if (layer.fades) faded(c, t) else c, g)
            grounds = next
        }
        var w = Double.MAX_VALUE
        for (g in grounds) for (ink in stack.inks) w = min(w, ColorMath.ratio(ink, g))
        return w
    }

    /**
     * Hệ số nhỏ nhất ≥ [f] (trên lưới [STEP], cuối cùng là 1.0) để mọi cặp (chồng × nền) ĐẠT ở 1.0 vẫn đạt — nền là
     * các màu ĐỤC đã biết trước ([grounds]: nền màn + tâm hai vầng sáng khi không có ảnh; nền thanh trên mọi độ chói
     * khi có ảnh). Cặp hôm nay đã hụt KHÔNG kéo sàn lên (đó là việc của bảng màu, không phải của độ đục).
     */
    fun floor(f: Double, stacks: List<Stack>, grounds: List<Int>): Double {
        if (f >= 1.0) return 1.0
        val live = ArrayList<Pair<Stack, Int>>()
        for (s in stacks) for (g in grounds) if (worst(s, 1.0, g) >= FLOOR) live += s to g
        var t = f
        while (t < 1.0 - 1e-9) {
            if (live.all { (s, g) -> worst(s, t, g) >= FLOOR }) return t
            t = nextStep(t)
        }
        return 1.0
    }

    /**
     * Độ đục nhỏ nhất (0..1) của nền ĐỤC [veil] trải trên vùng xám độ chói [lums] để mọi [stacks] (lớp mờ ở hệ số
     * [t]) không tệ hơn hôm nay — hôm nay = nền ở độ đục gốc [base], hệ số 1. Ô nào có chồng HÔM NAY đã hụt ⇒ ô đó
     * cần đúng [base] (không làm tệ thêm một chỗ vốn đã khó đọc — cùng hành vi trần của [GlassVeil.alphaFor]).
     * Vùng TỆ NHẤT quyết. Rỗng ⇒ `null`.
     */
    fun neededOver(lums: DoubleArray, veil: Int, base: Double, stacks: List<Stack>, t: Double): Double? {
        if (lums.isEmpty()) return null
        var need = 0.0
        for (l in lums) need = max(need, neededAt(l, veil, base, stacks, t))
        return need
    }

    private fun neededAt(l: Double, veil: Int, base: Double, stacks: List<Stack>, t: Double): Double {
        val art = ColorMath.grayOfLuminance(l)
        fun ground(a: Double) = ColorMath.over(ColorMath.withAlpha(veil, (a.coerceIn(0.0, 1.0) * 255).toInt()), art)
        val today = ground(base)
        if (stacks.any { worst(it, 1.0, today) < FLOOR }) return base
        var a = 0.0
        while (a < base - 1e-9) {
            val g = ground(a)
            if (stacks.all { worst(it, t, g) >= FLOOR }) return a
            a = nextStep(a)
        }
        return base
    }

    /**
     * Thẻ KÍNH trên ảnh (khay ô làm việc): lớp che nhỏ nhất trong [[min], [max]] để mọi chồng [overlays] nằm TRÊN bề
     * mặt kính (ô TẮT mờ ở hệ số [t], ô BẬT, ô cảnh báo) vẫn đạt sàn ở chỗ hôm nay đạt. Hôm nay = bề mặt
     * [todaySurfaces] trên lớp che [todayVeil]; bây giờ = [surfaces] (đã mờ) trên lớp che đang tìm. Bộ giải chữ
     * ([GlassVeil.alphaFor]) chỉ biết chữ nằm thẳng trên kính — thứ bán trong suốt nằm trên kính thì nó không thấy.
     * Không chồng nào đạt hôm nay ⇒ [min] (không ràng buộc); tới [max] vẫn hụt ⇒ [max].
     */
    fun overlayVeil(
        l: Double, veil: Int, surfaces: IntArray, todaySurfaces: IntArray, todayVeil: Double,
        overlays: List<Stack>, t: Double, min: Double, max: Double = GlassVeil.MAX,
    ): Double {
        val art = ColorMath.grayOfLuminance(l)
        fun grounds(a: Double, surfs: IntArray): List<Int> {
            val veiled = ColorMath.over(ColorMath.withAlpha(veil, (a.coerceIn(0.0, 1.0) * 255).toInt()), art)
            return surfs.map { ColorMath.over(it, veiled) }
        }
        val today = grounds(todayVeil, todaySurfaces)
        val live = overlays.filter { s -> today.all { worst(s, 1.0, it) >= FLOOR } }
        if (live.isEmpty()) return min
        var a = min
        while (a <= max + 1e-9) {
            val now = grounds(a, surfaces)
            if (live.all { s -> now.all { worst(s, t, it) >= FLOOR } }) return a
            a = nextStep(a)
        }
        return max
    }

    /** Bước kế trên lưới [STEP] (làm tròn để cộng dồn không trôi số). */
    private fun nextStep(x: Double): Double = (((x / STEP) + 1e-9).toInt() + 1) * STEP
}
