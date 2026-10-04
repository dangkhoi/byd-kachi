package com.byd.clusternav.launcher

import android.graphics.drawable.Drawable

/**
 * ═══ R-OP — ĐỘ ĐỤC NỀN CHUNG của màn chính: ĐÚNG năm chỗ dựng + ba chỗ vẽ Canvas ═════════════════════════════════
 *
 * Spec `docs/specs/kachi-287-look-and-keys.html` §3 R-OP1..3 · §4.1 · §4.1.2 (2.88). Owner 03/10: *"Cho chỉnh độ
 * transparent của header bar, taskbar, widget được không? Chỉnh chung, không cần riêng từng cái."* — 04/10: *"Cho trong
 * suốt lên 100% luôn, tùy user chọn, có thay kéo từ 0-100%"*.
 *
 * ## Vì sao không nhân alpha vào bảng màu
 * `BAR_TOP` · `surface` · `slot` · `CARD2`… dùng CHUNG với Cài đặt, hộp thoại, ngăn kéo, lớp phủ giọng nói. Nhân vào bảng
 * là làm trong suốt cả những chỗ owner không hỏi — và làm số đo của `ThemePaletteContractTest.textOn` (bỏ qua alpha của
 * nền) nói về một nền không còn được vẽ. Nên hệ số sống ở đây và chỉ năm chỗ dựng `Drawable` của màn chính gọi [fade]
 * (bài `KachiChromeContractTest` ghim đủ năm, cấm mọi chỗ khác):
 *  1. `KachiGlass.paint` — thẻ/khay kính tone NEUTRAL · WELL (widget, ô nhóm, khay ô làm việc), cả lớp kính trên ảnh;
 *  2. `ControlTileFactory.applyBg` nhánh TẮT — ô nút (thanh nút + ô giữa màn);
 *  3. `readTileOf` — ô đọc số của thanh nút;
 *  4. `KachiTopStrip` — nền thanh trên;
 *  5. `ControlDockView` — nền thanh nút xe.
 * Cộng ba chỗ VẼ Canvas đọc thẳng [fraction] (không có `Drawable` để [fade]) — cùng phép `modulateAlpha`
 * ([ChromeOpacity.drawnAlpha]), cũng ghim trong bài canh: lớp cửa sổ kính trên ảnh (`WallWindowDrawable`, `WallGlass.kt`),
 * bốn thẻ bánh THƯỜNG của bảng lốp (`TyreBoardView`, soát Pass 10), và hai dải che đỉnh/đáy của hình nền (`WallView` —
 * nền sau thanh trên/thanh nút; owner 04/10 *"trong suốt lên 100%"* ⇒ 100 % không vẽ dải).
 *
 * KHÔNG mờ: chữ/icon (chỉ alpha của nền, không bao giờ `View.alpha`), ô đang BẬT, ô cảnh báo (cả thẻ bánh cảnh báo),
 * nền widget bên thứ ba, đĩa ⇄ + đĩa nút đầu ô, nút/chip trên thanh trên, ảnh/nhạc, lớp làm tối ảnh nền (người dùng tự
 * đặt), và mọi thứ ngoài màn chính.
 *
 * ## 2.88 — hệ số người chọn = hệ số được vẽ (bỏ sàn đọc được của 2.87 R-OP3)
 * 2.87 giữ nền đục hơn bậc người chọn ở chỗ nền phía sau làm chữ hụt 4.5:1 (sàn theo chồng lớp `ChromeStack`, theo vùng
 * không-ảnh, theo ô ảnh tệ nhất dưới thanh, lớp che kính nâng tới 100 %). Hệ quả: trên ảnh sáng thanh kéo nói 60 % mà mắt
 * thấy gần như không đổi. Owner 04/10 *"tùy user chọn"* ⇒ gỡ cả bộ sàn: mọi nền mờ vẽ ĐÚNG `alpha gốc × [fraction]`
 * (phép `modulateAlpha`, [ChromeOpacity.drawnAlpha]) trên mọi nền, có hay không ảnh. Chữ khó đọc ở mức trong cao trên
 * ảnh sáng là lựa chọn của người dùng — ghi chú dưới thanh kéo nói thẳng điều đó.
 *
 * ## Một chỗ ghi
 * [apply] chỉ được gọi từ `ThemeHost.sync` (cùng chỗ áp bảng màu) ⇒ đổi độ đục đi đúng đường đổi màu: `render` →
 * `applyThemeInPlace` dựng lại nền tại chỗ, KHÔNG `recreate` (ô app + màn ảo sống tiếp). Trả `false` khi không đổi
 * vì `sync` chạy mỗi nhịp trạng thái (1 Hz) — trả `true` vô điều kiện là dựng lại thanh nút + ô mỗi giây.
 */
object KachiChrome {

    /** Hệ số người chọn, 0..1 (1 = như hôm nay) — cũng chính là hệ số được vẽ (2.88). Chỉ [apply] ghi. */
    var fraction: Double = 1.0
        private set

    /**
     * Đặt độ đục [pct] (đi qua [ChromeOpacity.snap]). `true` nếu thứ được vẽ ĐỔI. Không phụ thuộc bảng màu hay ảnh nền
     * (2.88 không còn sàn theo nền) ⇒ bật/tắt ảnh hay đổi màu không làm hàm này báo đổi — việc đó của `applyTheme` /
     * `KachiGlass.refresh`, và lớp kính tự đọc [fraction] mỗi lượt dựng.
     */
    internal fun apply(pct: Int): Boolean {
        val was = fraction
        fraction = ChromeOpacity.fraction(pct)
        return fraction != was
    }

    /** Tone nào được làm trong: chỉ bề mặt TRUNG TÍNH (thẻ) và KHAY. BẬT/LÕM mang thông tin hoặc là ô nhập. */
    fun fades(tone: SurfaceTone): Boolean = tone == SurfaceTone.NEUTRAL || tone == SurfaceTone.WELL

    /**
     * Hạ alpha của nền [d] theo [fraction] rồi trả lại chính nó (để bọc cùng dòng ở chỗ dựng). Ở 100 % KHÔNG chạm gì —
     * đầu ra từng byte như hôm nay. Ở 0 % (trong suốt 100 %) alpha = 0 ⇒ `GradientDrawable` không tô gì
     * (`haveFill = modulateAlpha(…) > 0` [ĐO AOSP r47 `GradientDrawable.java:727-732`]).
     *
     * Không cần `mutate()`: mỗi lời gọi `KachiTheme.surface`/`card` dựng một `Drawable` MỚI (KDoc ở đó), và
     * `GradientDrawable.setAlpha` chỉ nhân vào màu tô lúc vẽ — không lớp offscreen vì dự án 0 viền [ĐO AOSP r47
     * GradientDrawable.java:741-742]. `LayerDrawable.setAlpha` chuyền xuống mọi lớp con (LayerDrawable.java:1358-1367),
     * nên bề mặt có lớp sắc lĩnh vực cũng mờ đều.
     */
    fun fade(d: Drawable): Drawable {
        if (fraction < 1.0) d.alpha = ChromeOpacity.alphaByte(fraction)
        return d
    }
}
