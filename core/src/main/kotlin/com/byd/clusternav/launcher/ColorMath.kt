package com.byd.clusternav.launcher

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * ═══ VISUAL-REFRESH P1b · R8 — SỐ HỌC MÀU THUẦN (`:core`, không Android) ═══════════════════════════════════
 *
 * Spec `docs/specs/kachi-visual-refresh.html` §R8 (AC8.5) + §4.10. Mọi phép tính ở đây nhận/trả **`Int` ARGB**
 * (cùng bố cục bit với `android.graphics.Color`, nên `:app` chỉ việc đưa thẳng vào `Paint`), và tuyệt đối
 * **không** có mã màu nào — mã màu chỉ sống ở `KachiPalette` (`:app`). Tệp này chỉ là *phép tính* trên màu.
 *
 * ## Vì sao ở `:core` chứ không ở `:app`
 * Ba việc của P1b phải **kiểm được off-car bằng số**: (1) tự bảo vệ tương phản khi người dùng chọn màu nhấn
 * (AC8.5), (2) lớp che của thẻ kính chọn theo độ chói **đo được** của ảnh dưới thẻ (§4.10 mục 5), (3) màu trội
 * của ảnh nền. Cả ba là số học, không cần `Bitmap` hay `Context`; để ở `:app` thì chỉ test được bằng máy ảo.
 *
 * ## Cùng công thức với `Wcag` (testFixtures)
 * [over]/[luminance]/[ratio] lặp lại đúng công thức của `com.byd.clusternav.testsupport.Wcag` (WCAG 2.x, trộn
 * **cắt số** như `Wcag.over`) — cố ý, để bài canh so hai bên khớp nhau tới từng đơn vị (`ColorMathTest`). Bản
 * testFixtures giữ nguyên vì nó nhận chuỗi hex và đã là hợp đồng của ~10 bài canh; bản này nhận `Int` vì nó chạy
 * **lúc vẽ** trên xe, mỗi nhịp, và không được cấp phát chuỗi.
 */
object ColorMath {

    fun alpha(c: Int): Int = (c ushr 24) and 0xff
    fun red(c: Int): Int = (c ushr 16) and 0xff
    fun green(c: Int): Int = (c ushr 8) and 0xff
    fun blue(c: Int): Int = c and 0xff

    fun argb(a: Int, r: Int, g: Int, b: Int): Int =
        (a.coerceIn(0, 255) shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)

    /** `#RRGGBB` hoặc `#AARRGGBB` → ARGB. Chuỗi hỏng ⇒ ném — mã màu đi vào đây chỉ đến từ bảng màu, không từ người dùng. */
    fun parse(hex: String): Int {
        val h = hex.removePrefix("#")
        require(h.length == 6 || h.length == 8) { "mã màu không hợp lệ: $hex" }
        val v = h.toLong(16)
        return if (h.length == 6) (0xff000000L or v).toInt() else v.toInt()
    }

    /** ARGB → `#rrggbb` (alpha 255) hoặc `#aarrggbb` — cùng dạng chữ thường mà `KachiPalette` dùng. */
    fun hex(c: Int): String =
        if (alpha(c) == 0xff) "#%02x%02x%02x".format(red(c), green(c), blue(c))
        else "#%02x%02x%02x%02x".format(alpha(c), red(c), green(c), blue(c))

    fun withAlpha(c: Int, a: Int): Int = (c and 0x00ffffff) or (a.coerceIn(0, 255) shl 24)

    /** Nhân kênh alpha với [f] (0..1) — dùng để "hạ đục" một vai đã có alpha mà không đổi sắc. */
    fun scaleAlpha(c: Int, f: Double): Int = withAlpha(c, (alpha(c) * f.coerceIn(0.0, 1.0)).roundToInt())

    /** Trộn [fg] (có alpha) lên [bg] **đục** → màu đục. Cắt số như `Wcag.over` để hai bên khớp từng đơn vị. */
    fun over(fg: Int, bg: Int): Int {
        val a = alpha(fg) / 255.0
        fun mix(f: Int, b: Int) = (f * a + b * (1 - a)).toInt().coerceIn(0, 255)
        return argb(255, mix(red(fg), red(bg)), mix(green(fg), green(bg)), mix(blue(fg), blue(bg)))
    }

    /** Độ chói tương đối WCAG 2.x (bỏ qua alpha — màu có alpha phải [over] trước, xem KDoc `Wcag`). */
    fun luminance(c: Int): Double {
        fun ch(v: Int): Double {
            val s = v / 255.0
            return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * ch(red(c)) + 0.7152 * ch(green(c)) + 0.0722 * ch(blue(c))
    }

    /** Tỉ số tương phản; [fg] có alpha thì trộn lên [bg] trước. */
    fun ratio(fg: Int, bg: Int): Double {
        val f = luminance(if (alpha(fg) < 255) over(fg, bg) else fg)
        val b = luminance(bg)
        return (max(f, b) + 0.05) / (min(f, b) + 0.05)
    }

    /** Trộn tuyến tính RGB `a·(1−t) + b·t`, **giữ alpha của [a]** — dùng để nhuộm nhẹ một bề mặt. */
    fun mix(a: Int, b: Int, t: Double): Int {
        val k = t.coerceIn(0.0, 1.0)
        fun m(x: Int, y: Int) = (x + (y - x) * k).roundToInt()
        return argb(alpha(a), m(red(a), red(b)), m(green(a), green(b)), m(blue(a), blue(b)))
    }

    /** Xám đục có độ chói WCAG đúng bằng [l] — để mô hình hoá "vùng ảnh dưới thẻ" bằng một con số đo được. */
    fun grayOfLuminance(l: Double): Int {
        val y = l.coerceIn(0.0, 1.0)
        val s = if (y <= 0.0031308) y * 12.92 else 1.055 * y.pow(1 / 2.4) - 0.055
        val v = (s * 255).roundToInt().coerceIn(0, 255)
        return argb(255, v, v, v)
    }

    // ── HSL — để "chuyển sắc" một vai màu sang họ màu nhấn khác mà giữ nguyên bậc sáng đã đo ──────────────

    /** (h 0..360, s 0..1, l 0..1). */
    fun hsl(c: Int): DoubleArray {
        val r = red(c) / 255.0; val g = green(c) / 255.0; val b = blue(c) / 255.0
        val mx = max(r, max(g, b)); val mn = min(r, min(g, b))
        val l = (mx + mn) / 2
        val d = mx - mn
        if (d < 1e-9) return doubleArrayOf(0.0, 0.0, l)
        val s = if (l > 0.5) d / (2 - mx - mn) else d / (mx + mn)
        var h = when (mx) {
            r -> (g - b) / d + (if (g < b) 6 else 0)
            g -> (b - r) / d + 2
            else -> (r - g) / d + 4
        }
        h = (h * 60.0) % 360.0
        if (h < 0) h += 360.0
        return doubleArrayOf(h, s, l)
    }

    fun fromHsl(h: Double, s: Double, l: Double, a: Int = 255): Int {
        val hh = ((h % 360.0) + 360.0) % 360.0 / 360.0
        val ss = s.coerceIn(0.0, 1.0); val ll = l.coerceIn(0.0, 1.0)
        if (ss < 1e-9) { val v = (ll * 255).roundToInt(); return argb(a, v, v, v) }
        val q = if (ll < 0.5) ll * (1 + ss) else ll + ss - ll * ss
        val p = 2 * ll - q
        fun ch(tt: Double): Double {
            var t = tt
            if (t < 0) t += 1.0
            if (t > 1) t -= 1.0
            return when {
                t < 1.0 / 6 -> p + (q - p) * 6 * t
                t < 0.5 -> q
                t < 2.0 / 3 -> p + (q - p) * (2.0 / 3 - t) * 6
                else -> p
            }
        }
        return argb(a, (ch(hh + 1.0 / 3) * 255).roundToInt(), (ch(hh) * 255).roundToInt(), (ch(hh - 1.0 / 3) * 255).roundToInt())
    }

    /**
     * **Chuyển sắc** một vai màu [role] (đã chỉnh tay cho họ màu nhấn [base]) sang họ màu [seed].
     *
     * Giữ: alpha · **khoảng cách sắc** so với gốc (accent2 lệch +30° so với accent thì vẫn lệch +30°) · bậc sáng
     * (dịch theo hiệu sáng seed−base, hệ số 0.6 để màu nhấn sáng như *trắng ấm* kéo nút lên sáng thật, nhưng
     * không kéo mọi vai lên trắng tinh). Độ bão hoà nhân theo tỉ lệ `sat(seed)/sat(base)` để họ *bạc* / *trắng
     * ấm* ra bạc thật chứ không ra một sắc xanh nhạt. Đây là **cách duy nhất** đổi màu nhấn trong dự án: đổi
     * gốc, không đổi từng màn (spec §R8 câu đầu).
     */
    fun recolor(role: Int, base: Int, seed: Int): Int {
        val r = hsl(role); val b = hsl(base); val s = hsl(seed)
        val hueOffset = r[0] - b[0]
        val satScale = if (b[1] < 1e-6) 1.0 else min(1.25, s[1] / b[1])
        val l = (r[2] + (s[2] - b[2]) * 0.6).coerceIn(0.04, 0.96)
        return fromHsl(s[0] + hueOffset, min(1.0, r[1] * satScale), l, alpha(role))
    }

    /** Khoảng cách sắc (0..180) — để hai màu trội của ảnh không trùng họ. */
    fun hueDistance(a: Int, b: Int): Double {
        val d = abs(hsl(a)[0] - hsl(b)[0]) % 360.0
        return if (d > 180) 360 - d else d
    }
}
