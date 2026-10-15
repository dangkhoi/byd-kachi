package com.byd.clusternav.launcher

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * ═══ 3.1 R29 — ĐỘ TƯƠNG PHẢN THÍCH ỨNG · tầng ĐO: "hộp màu" của một vùng ảnh (`:core`, thuần) ═══════════════════════════
 *
 * Owner 14/10: *"các text, màu nét icon cần phải đo được độ tương phản với nền … hiện tại hên xui, lúc thấy lúc không vì
 * nó bị fix theo 1 giá trị cố định"*. Spec `docs/specs/kachi-301-cast-steps-dialog-ia.html` §R29.
 *
 * ## Vì sao KHOẢNG (p10/p90) chứ không TRUNG BÌNH
 * 2.86 R-OQ8 phương án A (`SwapTint`, đã gỡ) chọn mực theo độ chói **trung bình** lưới 16×9 ⇒ [ĐO 03/10] trên ảnh sọc 2 px
 * trung bình ra xám giữa, mực chọn cho "xám" chìm trên nửa số điểm ảnh (ô xấu nhất 2.60:1). Chữ nằm trên TỪNG điểm ảnh.
 *
 * ## Vì sao HỘP theo từng kênh chứ không khoảng độ chói
 * [ĐO R29, `ContrastBackdropTest`] thay một điểm ảnh có sắc bằng xám cùng độ chói rồi trộn lớp bề mặt bán trong suốt lên
 * (phép trộn chạy trên giá trị sRGB, độ chói thì không tuyến tính) sai tỉ số tới **29 %** — đủ để khai 4.5 khi thật là 3.5.
 * Hộp `[lo, hi]` THEO TỪNG KÊNH (p10 R/G/B · p90 R/G/B) thì không cần giả định nào: phép trộn đơn điệu theo từng kênh, độ
 * chói đơn điệu theo từng kênh ⇒ MỌI điểm ảnh nằm trong hộp cho nền ghép có độ chói nằm giữa nền ghép của hai góc hộp.
 * Cái giá: ảnh rực màu (đỏ cạnh lam) ra hộp rộng hơn khoảng độ chói thật ⇒ đôi khi đổi họ mực sớm hơn cần — bảo thủ, không im lặng sai.
 *
 * ## Chi phí (owner 08/10 *perf-every-fix*)
 * Lưới ≤ [MAX_COLS] cột (1920×720 ⇒ 64×24 ô 30 px); thứ được LƯU chỉ là 2 mảng `Int` + 1 mảng `Float` cỡ lưới. Đọc ảnh MỘT
 * lượt, từng hàng ([Builder.addRow], không giữ cả ảnh), trên luồng nạp ảnh SẴN CÓ, chỉ khi ảnh đổi. Mỗi điểm ảnh: 3 lần
 * tăng histogram 8-bit (p10/p90 CHÍNH XÁC, không chia ngăn) + 1 lần tra bảng độ chói ([Luma]); 0 `pow` trong vòng lặp.
 */
object Luma {
    /** Kênh sRGB 8-bit → tuyến tính, CÙNG công thức `ColorMath.luminance` (ngưỡng 0.03928) để hai bên khớp từng bit. */
    private val LIN = DoubleArray(256) {
        val s = it / 255.0
        if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
    }

    /** Độ chói tương đối WCAG của một ARGB (bỏ alpha) — bằng đúng `ColorMath.luminance`, nhưng không `pow`. */
    fun of(c: Int): Double = 0.2126 * LIN[(c ushr 16) and 0xff] + 0.7152 * LIN[(c ushr 8) and 0xff] + 0.0722 * LIN[c and 0xff]
}

/**
 * Hộp màu của một vùng: góc tối [lo] và góc sáng [hi] (ARGB ĐỤC, mỗi kênh `lo ≤ hi`), [mean] = độ chói trung bình (CHỈ để
 * ghi log — không dùng để chọn mực). [at] đi từ góc tối (`t = 0`) tới góc sáng (`t = 1`) — đường đơn điệu theo mọi kênh.
 */
data class ToneBox(val lo: Int, val hi: Int, val mean: Double = (Luma.of(lo) + Luma.of(hi)) / 2) {
    init {
        require(ColorMath.red(lo) <= ColorMath.red(hi) && ColorMath.green(lo) <= ColorMath.green(hi) && ColorMath.blue(lo) <= ColorMath.blue(hi)) {
            "hộp màu hỏng: ${ColorMath.hex(lo)} … ${ColorMath.hex(hi)}"
        }
    }

    val lumLo: Double get() = Luma.of(lo)
    val lumHi: Double get() = Luma.of(hi)

    fun at(t: Double): Int = when {
        t <= 0.0 -> lo
        t >= 1.0 -> hi
        else -> {
            fun m(a: Int, b: Int) = (a + (b - a) * t).roundToInt()
            ColorMath.argb(255, m(ColorMath.red(lo), ColorMath.red(hi)), m(ColorMath.green(lo), ColorMath.green(hi)), m(ColorMath.blue(lo), ColorMath.blue(hi)))
        }
    }

    fun union(o: ToneBox): ToneBox = ToneBox(corner(lo, o.lo, ::min), corner(hi, o.hi, ::max), (mean + o.mean) / 2)

    companion object {
        private fun corner(a: Int, b: Int, f: (Int, Int) -> Int) =
            ColorMath.argb(255, f(ColorMath.red(a), ColorMath.red(b)), f(ColorMath.green(a), ColorMath.green(b)), f(ColorMath.blue(a), ColorMath.blue(b)))

        /** Hộp phủ các màu ĐỤC biết trước (nền chủ đề + vầng sáng · nền màu của xe 3D …). */
        fun span(vararg colors: Int): ToneBox {
            require(colors.isNotEmpty()) { "cần ít nhất một màu" }
            var a = colors[0]; var z = colors[0]
            for (c in colors) { a = corner(a, c, ::min); z = corner(z, c, ::max) }
            return ToneBox(ColorMath.withAlpha(a, 255), ColorMath.withAlpha(z, 255), colors.map { Luma.of(it) }.average())
        }

        /** Hộp XÁM từ độ chói [yLo] tới [yHi], làm tròn RA NGOÀI (góc tối không sáng hơn [yLo], góc sáng không tối hơn [yHi]). */
        fun gray(yLo: Double, yHi: Double = yLo): ToneBox {
            val a = yLo.coerceIn(0.0, 1.0); val z = yHi.coerceIn(0.0, 1.0)
            require(a <= z) { "độ chói hỏng: [$yLo, $yHi]" }
            return ToneBox(grayOut(a, up = false), grayOut(z, up = true))
        }

        private fun grayOut(y: Double, up: Boolean): Int {
            val g = ColorMath.grayOfLuminance(y.coerceIn(0.0, 1.0))
            val v = ColorMath.red(g)
            val o = if (!up && v > 0 && Luma.of(g) > y) v - 1 else if (up && v < 255 && Luma.of(g) < y) v + 1 else return g
            return ColorMath.argb(255, o, o, o)
        }
    }
}

/**
 * Lưới [cols]×[rows] trên một ảnh [width]×[height]: mỗi ô giữ hộp p[LOW_PCT]…p[HIGH_PCT] từng kênh + độ chói trung bình.
 * Dựng bằng [Builder] (từng hàng) hoặc [of] (mảng đủ).
 */
class LumaField internal constructor(
    val cols: Int,
    val rows: Int,
    val width: Int,
    val height: Int,
    private val lo: IntArray,
    private val hi: IntArray,
    private val mean: FloatArray,
) {
    fun cell(c: Int, r: Int): ToneBox = (r * cols + c).let { ToneBox(lo[it], hi[it], mean[it].toDouble()) }

    /**
     * Hộp của vùng `[l, r) × [t, b)` trong hệ toạ độ của bên gọi; [scale] = hệ số đổi sang điểm ảnh của lưới (ảnh 1920 vẽ ra
     * màn 1280 ⇒ `scale = 1.5`). Ô chạm một phần vẫn tính trọn ô; hộp vùng = hợp các hộp ô (bảo thủ).
     *
     * @return `null` khi vùng rỗng hoặc nằm NGOÀI ảnh — KHÔNG trả một con số giả (bản cũ `WallArt.luminanceOf` trả 0.5:
     *   im lặng sai). Bên gọi tự quyết nền ở đó (vd viền "vừa khung" = nền chủ đề).
     */
    fun range(l: Int, t: Int, r: Int, b: Int, scale: Double = 1.0): ToneBox? {
        val x0 = kotlin.math.floor(l * scale).toInt().coerceAtLeast(0)
        val y0 = kotlin.math.floor(t * scale).toInt().coerceAtLeast(0)
        val x1 = ceil(r * scale).toInt().coerceAtMost(width)
        val y1 = ceil(b * scale).toInt().coerceAtMost(height)
        if (x1 <= x0 || y1 <= y0) return null
        val c0 = colOf(x0); val c1 = colOf(x1 - 1)
        val r0 = rowOf(y0); val r1 = rowOf(y1 - 1)
        var box: ToneBox? = null
        var sum = 0.0; var n = 0
        for (rr in r0..r1) for (cc in c0..c1) {
            val cb = cell(cc, rr)
            box = box?.union(cb) ?: cb
            sum += cb.mean; n++
        }
        return box!!.copy(mean = sum / n)
    }

    private fun colOf(x: Int) = (x.toLong() * cols / width).toInt().coerceIn(0, cols - 1)
    private fun rowOf(y: Int) = (y.toLong() * rows / height).toInt().coerceIn(0, rows - 1)

    /** Dạng chữ cho bộ đệm đĩa (tệp `.txt` trong `.kachi-art`) — số nguyên chính xác, đọc lại y hệt. */
    fun encode(): String = buildString {
        append(VERSION).append(';').append(cols).append(';').append(rows).append(';').append(width).append(';').append(height)
        append(';'); lo.joinTo(this, ",") { Integer.toHexString(it and 0xffffff) }
        append(';'); hi.joinTo(this, ",") { Integer.toHexString(it and 0xffffff) }
        append(';'); mean.joinTo(this, ",") { (it * Q).roundToInt().toString() }
    }

    /** Dồn từng hàng điểm ảnh vào lưới — không giữ ảnh. Hàng phải đến theo `y` TĂNG dần; ô nào không có mẫu ⇒ [build] ném. */
    class Builder(val width: Int, val height: Int, maxCols: Int = MAX_COLS) {
        init { require(width > 0 && height > 0 && maxCols > 0) { "cỡ ảnh/lưới hỏng: ${width}×$height / $maxCols" } }

        val cols = min(maxCols, width)
        val rows = ((height.toLong() * cols + width / 2) / width).toInt().coerceIn(1, height)
        private val lo = IntArray(cols * rows)
        private val hi = IntArray(cols * rows)
        private val mean = FloatArray(cols * rows)
        private val hist = IntArray(cols * 3 * 256)
        private val count = IntArray(cols)
        private val sum = DoubleArray(cols)
        private var cellRow = 0
        private var lastY = -1

        /** Hàng [y]: [px] từ [offset], [width] điểm, lấy mỗi [stride] điểm (stride rộng hơn ô ⇒ [build] ném). */
        fun addRow(y: Int, px: IntArray, offset: Int = 0, stride: Int = 1) {
            require(y in 0 until height && y > lastY) { "hàng $y không theo thứ tự (trước: $lastY)" }
            require(stride >= 1 && offset >= 0 && offset + width <= px.size) { "hàng $y: offset/stride hỏng" }
            lastY = y
            val target = (y.toLong() * rows / height).toInt()
            while (cellRow < target) flush()
            var x = 0
            while (x < width) {
                val p = px[offset + x]
                val c = (x.toLong() * cols / width).toInt()
                val h = c * 768
                hist[h + ((p ushr 16) and 0xff)]++
                hist[h + 256 + ((p ushr 8) and 0xff)]++
                hist[h + 512 + (p and 0xff)]++
                count[c]++; sum[c] += Luma.of(p)
                x += stride
            }
        }

        private fun pct(h: Int, need: Int, fromTop: Boolean): Int {
            var acc = 0
            if (!fromTop) { for (v in 0..255) { acc += hist[h + v]; if (acc >= need) return v } }
            else { for (v in 255 downTo 0) { acc += hist[h + v]; if (acc >= need) return v } }
            error("histogram rỗng")
        }

        private fun flush() {
            for (c in 0 until cols) {
                val i = cellRow * cols + c
                val n = count[c]
                check(n > 0) { "ô ($c,$cellRow) không có mẫu nào — stride quá thưa hoặc thiếu hàng" }
                val needLo = max(1, ceil(n * LOW_PCT - 1e-9).toInt())
                val needHi = max(1, ceil(n * (1 - HIGH_PCT) - 1e-9).toInt())
                val h = c * 768
                lo[i] = ColorMath.argb(255, pct(h, needLo, false), pct(h + 256, needLo, false), pct(h + 512, needLo, false))
                hi[i] = ColorMath.argb(255, pct(h, needHi, true), pct(h + 256, needHi, true), pct(h + 512, needHi, true))
                mean[i] = (sum[c] / n).toFloat()
                hist.fill(0, h, h + 768)
                count[c] = 0; sum[c] = 0.0
            }
            cellRow++
        }

        fun build(): LumaField {
            while (cellRow < rows) flush()
            return LumaField(cols, rows, width, height, lo, hi, mean)
        }
    }

    companion object {
        /** Lưới tối đa 64 cột — đủ mịn cho một nhãn 100 px (≈ 3–4 ô), đủ nhỏ để lưu kèm ảnh mờ. */
        const val MAX_COLS = 64
        const val LOW_PCT = 0.10
        const val HIGH_PCT = 0.90
        private const val VERSION = "T1"
        private const val Q = 100_000f

        /** Dựng từ mảng ARGB đủ [w]×[h] (ảnh nhỏ: ảnh mờ ¼, nền xe 3D đã hạ cỡ). */
        fun of(px: IntArray, w: Int, h: Int, maxCols: Int = MAX_COLS, stride: Int = 1): LumaField {
            require(px.size >= w * h) { "mảng điểm ảnh ngắn hơn ${w}×$h" }
            val b = Builder(w, h, maxCols)
            for (y in 0 until h) b.addRow(y, px, y * w, stride)
            return b.build()
        }

        /** Đọc lại [encode]; hỏng/khác phiên bản ⇒ `null` (bên gọi dựng lại, không đoán). */
        fun decode(s: String?): LumaField? = runCatching {
            val p = s!!.split(';')
            if (p.size != 8 || p[0] != VERSION) return null
            val cols = p[1].toInt(); val rows = p[2].toInt(); val w = p[3].toInt(); val h = p[4].toInt()
            require(cols in 1..w && rows in 1..h)
            fun colors(x: String) = x.split(',').map { (it.toInt(16) and 0xffffff) or (0xff shl 24) }.toIntArray().also { require(it.size == cols * rows) }
            val lo = colors(p[5]); val hi = colors(p[6])
            val mean = p[7].split(',').map { it.toInt() / Q }.toFloatArray().also { require(it.size == cols * rows) }
            for (i in lo.indices) { ToneBox(lo[i], hi[i]); require(mean[i] in 0f..1f) }
            LumaField(cols, rows, w, h, lo, hi, mean)
        }.getOrNull()

        /**
         * Hộp p10/p90 của MỘT vùng `[l, r) × [t, b)` đọc thẳng trên [px] ([w]×[h]) — cho ảnh nhỏ đọc một lần (nền xe 3D đã
         * hạ cỡ ≤ 64 px), khi không cần cả lưới. `null` khi vùng nằm ngoài ảnh.
         */
        fun sample(px: IntArray, w: Int, h: Int, l: Int, t: Int, r: Int, b: Int, stride: Int = 1): ToneBox? {
            val x0 = l.coerceAtLeast(0); val y0 = t.coerceAtLeast(0)
            val x1 = r.coerceAtMost(w); val y1 = b.coerceAtMost(h)
            if (x1 <= x0 || y1 <= y0) return null
            val sub = Builder(x1 - x0, y1 - y0, maxCols = 1)
            val row = IntArray(x1 - x0)
            for (y in y0 until y1) {
                System.arraycopy(px, y * w + x0, row, 0, row.size)
                sub.addRow(y - y0, row, 0, stride)
            }
            return sub.build().let { it.range(0, 0, it.width, it.height) }
        }
    }
}
