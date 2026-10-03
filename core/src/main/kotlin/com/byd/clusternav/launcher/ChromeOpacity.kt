package com.byd.clusternav.launcher

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * ═══ 2.87 · R-OP — ĐỘ ĐỤC NỀN CHUNG cho thanh trên · thanh nút xe · widget (`kachi-287-look-and-keys` §3, §4.1) ══
 *
 * Owner 03/10: *"Cho chỉnh độ transparent của header bar, taskbar, widget được không? Chỉnh chung, không cần riêng
 * từng cái."* ⇒ MỘT con số theo hồ sơ (lưu trong [ColorChoice.surfaceOpacity]), năm bậc [STEPS], mặc định [DEFAULT]
 * = hôm nay. Tệp này là phần **số học thuần** (`:core`, không Android): bậc nào hợp lệ, và độ đục THỰC của một thanh
 * nằm trên ảnh nền là bao nhiêu. Sàn đọc được (mức "cần") do [ChromeStack] tính; chỗ áp lên `Drawable` là
 * `KachiChrome` (`:app`).
 *
 * ## Vì sao bậc thấp nhất là 40 % chứ không thấp hơn
 * [SUY — tính bằng bản sao ColorMath/GlassVeil, chưa đo pixel] thẻ kính trên ảnh nền giữ mực tệ nhất ≥ 4.5:1 tới
 * khoảng 20 %; dưới đó bảng SÁNG hụt. 40 % chừa biên an toàn gấp đôi và giữ được viền nhận ra vùng chạm của ô.
 * Owner muốn đổi thì đổi [STEPS] — bài `ChromeOpacityTest` + bài quét độ chói ở `:app` chấm lại mọi bậc.
 */
object ChromeOpacity {

    /** Năm bậc người dùng chọn, phần trăm độ ĐỤC (100 = đục như hôm nay). Thứ tự = thứ tự chip ở Cài đặt. */
    val STEPS: List<Int> = listOf(100, 85, 70, 55, 40)

    /** Mặc định = hôm nay, từng byte (chỗ áp đi đường tắt khi gặp nó). */
    const val DEFAULT = 100

    /**
     * Bậc gần nhất với [pct]; ngoài dải thì kẹp vào hai đầu. Hai bậc cách đều (không xảy ra với số nguyên và bước 15,
     * nhưng giữ luật cho chắc) ⇒ chọn bậc ĐỤC hơn — sai về phía đọc được.
     */
    fun snap(pct: Int): Int {
        var best = DEFAULT   // vòng tay, không cấp phát: `ThemeHost.sync` gọi qua [fraction] mỗi nhịp 1 Hz
        for (s in STEPS) {
            val d = abs(s - pct); val bd = abs(best - pct)
            if (d < bd || (d == bd && s > best)) best = s
        }
        return best
    }

    /** Hệ số 0..1 của một bậc (đã [snap]). */
    fun fraction(pct: Int): Double = snap(pct) / 100.0

    /**
     * Số HIỆN trên chip = độ TRONG SUỐT (100 − độ đục): owner nói *"chỉnh độ transparent"*, nên Cài đặt đọc
     * "0 % = như hôm nay, số càng lớn nền càng trong". Chỉ là cách hiện — giá trị lưu vẫn là độ đục ([STEPS]).
     */
    fun transparencyPct(pct: Int): Int = 100 - snap(pct)

    /**
     * Độ đục THỰC (0..1) của một nền có độ đục gốc [base] khi người dùng chọn hệ số [f], với [needed] là độ đục tối
     * thiểu để chữ trên nền ấy còn đọc được (`null` = không có ảnh nền ⇒ nền dưới đã biết trước và đã đạt sàn).
     *
     * **Bất biến** (bài `ChromeOpacityTest`): `min(base, needed) ≤ kết quả ≤ base` — không bao giờ ĐỤC hơn hôm nay,
     * và không bao giờ trong hơn mức cần để đọc, trừ khi chính hôm nay cũng chưa tới mức đó (khi ấy giữ đúng hôm nay:
     * thanh này không làm tệ thêm một ca vốn đã hụt). `f ≥ 1` ⇒ trả đúng [base], không qua phép tính nào.
     */
    fun effective(base: Double, f: Double, needed: Double?): Double =
        if (f >= 1.0) base else max(base * f.coerceAtLeast(0.0), min(base, needed ?: 0.0))
}
