package com.byd.clusternav.testsupport

/**
 * Phép đo TƯƠNG PHẢN theo WCAG 2.x — dùng chung cho mọi bài canh bảng màu.
 *
 * ## Vì sao tách ra khỏi bài canh
 * [ĐO] 2026-09-16 (VISUAL-REFRESH P1): bốn hàm này đang nằm **private** trong `ThemePaletteContractTest`, và bài
 * canh thứ hai về bề mặt cần đúng bốn hàm ấy. Chép sang là mở đúng con đường mà `ChipTone` đã đi — hai bản sao của
 * một phép tính, rồi một bản được sửa còn bản kia thì không. CLAUDE.md §4.1 (DRY): *"pattern lặp ≥ 2 lần → extract
 * shared module NGAY"*.
 *
 * ## ⚠ [over] không phải chi tiết phụ
 * Màu có kênh trong suốt (`#AARRGGBB`) **không tồn tại trên màn** cho tới khi nó trộn lên một nền. Đo thẳng một mã
 * alpha là đo một con số không ai nhìn thấy bao giờ. Mọi hàm ở đây vì thế trộn trước, đo sau.
 */
object Wcag {

    /** `#RRGGBB` hoặc `#AARRGGBB` → (a, r, g, b). */
    fun argb(hex: String): IntArray {
        val h = hex.removePrefix("#")
        require(h.length == 6 || h.length == 8) { "mã màu không hợp lệ: $hex" }
        fun at(i: Int) = h.substring(i, i + 2).toInt(16)
        return if (h.length == 6) intArrayOf(255, at(0), at(2), at(4))
        else intArrayOf(at(0), at(2), at(4), at(6))
    }

    /** Trộn [fg] (có thể có kênh trong suốt) lên [bg] → mã đặc `#RRGGBB`. */
    fun over(fg: String, bg: String): String {
        val f = argb(fg)
        val b = argb(bg)
        val a = f[0] / 255.0
        fun mix(i: Int) = (f[i] * a + b[i] * (1 - a)).toInt().coerceIn(0, 255)
        return "#%02x%02x%02x".format(mix(1), mix(2), mix(3))
    }

    /** Độ chói tương đối theo WCAG 2.x. */
    fun luminance(hex: String): Double {
        val c = argb(hex)
        fun ch(v: Int): Double {
            val s = v / 255.0
            return if (s <= 0.03928) s / 12.92 else Math.pow((s + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * ch(c[1]) + 0.7152 * ch(c[2]) + 0.0722 * ch(c[3])
    }

    /** Tỉ số tương phản. [fg] có kênh trong suốt thì được TRỘN lên [bg] trước — xem KDoc của object. */
    fun ratio(fg: String, bg: String): Double {
        val f = luminance(if (argb(fg)[0] < 255) over(fg, bg) else fg)
        val b = luminance(bg)
        val hi = maxOf(f, b)
        val lo = minOf(f, b)
        return (hi + 0.05) / (lo + 0.05)
    }

    /** Hai chữ số thập phân — dạng số duy nhất được ghi vào thông điệp lỗi và vào bảng đo của tài liệu. */
    fun fmt(v: Double): String = String.format("%.2f", v)
}
