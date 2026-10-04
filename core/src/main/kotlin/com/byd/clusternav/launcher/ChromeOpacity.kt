package com.byd.clusternav.launcher

import kotlin.math.roundToInt

/**
 * ═══ R-OP — ĐỘ ĐỤC NỀN CHUNG cho thanh trên · thanh nút xe · widget (`kachi-287-look-and-keys` §3, §4.1 + §4.1.2) ══
 *
 * Owner 03/10: *"Cho chỉnh độ transparent của header bar, taskbar, widget được không? Chỉnh chung, không cần riêng
 * từng cái."* ⇒ MỘT con số theo hồ sơ (lưu trong [ColorChoice.surfaceOpacity]), mặc định [DEFAULT] = hôm nay. Tệp này là
 * phần **số học thuần** (`:core`, không Android): giá trị nào hợp lệ, vị trí nào của thanh kéo ứng với giá trị nào, và
 * alpha được VẼ là bao nhiêu. Chỗ áp lên `Drawable` là `KachiChrome` (`:app`).
 *
 * ## 2.88 — thanh kéo 0–100 %, áp ĐÚNG số người chọn (owner 04/10)
 * Owner 04/10: *"Cho trong suốt lên 100% luôn, tùy user chọn, có thay kéo từ 0-100%"*. Hai đổi so với 2.87:
 *  1. Năm bậc 100 · 85 · 70 · 55 · 40 → mọi bội của [STEP] (5) trong `[0, 100]` — 21 vị trí, đủ thưa để ngón tay đặt
 *     trúng trên xe. Năm bậc cũ đều là bội của 5 ⇒ hồ sơ 2.87 giữ đúng từng byte dáng cũ ([snap] trả lại y số).
 *  2. **Bỏ sàn đọc được** (2.87 R-OP3: `effective(base, f, needed)` + bộ giải chồng lớp `ChromeStack`). Sàn ấy giữ nền đục
 *     hơn số người chọn ở chỗ ảnh nền sáng ⇒ thanh kéo nói 60 % mà mắt thấy 30 % — đúng điều owner bác bằng *"tùy user
 *     chọn"*. Nay độ đục được vẽ = độ đục gốc × [fraction], ở MỌI nền. Chữ khó đọc trên ảnh sáng ở mức trong cao là
 *     lựa chọn của người dùng (ghi chú dưới thanh kéo nói rõ điều đó), không phải thứ Kachi tự sửa ngầm.
 *
 * Bất biến còn lại (bài `ChromeOpacityTest`): [DEFAULT] đi đường tắt không chạm gì (hôm nay từng byte); alpha được vẽ
 * không bao giờ ĐỤC hơn gốc, đơn điệu theo hệ số, và lệch phép nhân đúng `gốc × f` không quá 2/255.
 */
object ChromeOpacity {

    /** Mặc định = hôm nay, từng byte (chỗ áp đi đường tắt khi gặp nó). Cũng là độ đục LỚN nhất. */
    const val DEFAULT = 100

    /** Bước của thanh kéo, phần trăm. 0..100 chia hết ⇒ [POSITIONS] + 1 vị trí. */
    const val STEP = 5

    /** Vị trí cuối của thanh kéo (0 = trong suốt 0 % = hôm nay … [POSITIONS] = trong suốt 100 %). */
    const val POSITIONS = DEFAULT / STEP

    /**
     * Bội của [STEP] gần nhất với [pct], kẹp vào `[0, 100]` — số nguyên không bao giờ cách đều hai bội của 5 nên không có
     * ca hoà. Nhập/giải mã rác (`o250`, `o-3` không khớp regex) đi qua đây ⇒ không bao giờ ra số ngoài dải.
     *
     * Không cấp phát: `ThemeHost.sync` gọi qua [fraction] mỗi nhịp 1 Hz.
     */
    fun snap(pct: Int): Int = (pct.coerceIn(0, DEFAULT) + STEP / 2) / STEP * STEP

    /** Hệ số 0..1 của một độ đục (đã [snap]). */
    fun fraction(pct: Int): Double = snap(pct) / 100.0

    /**
     * Số HIỆN cho người dùng = độ TRONG SUỐT (100 − độ đục): owner nói *"chỉnh độ transparent"*, nên Cài đặt đọc
     * "0 % = như hôm nay, số càng lớn nền càng trong". Chỉ là cách hiện — giá trị lưu vẫn là độ đục.
     */
    fun transparencyPct(pct: Int): Int = DEFAULT - snap(pct)

    /** Vị trí thanh kéo (0..[POSITIONS]) của độ đục [pct]. */
    fun position(pct: Int): Int = transparencyPct(pct) / STEP

    /** Độ đục ứng với vị trí thanh kéo [pos] (kẹp vào `0..[POSITIONS]`) — nghịch đảo của [position]. */
    fun ofPosition(pos: Int): Int = DEFAULT - pos.coerceIn(0, POSITIONS) * STEP

    /** `Drawable.alpha` (0..255) cho hệ số [f] — làm tròn gần nhất; `f ≥ 1` ⇒ 255. */
    fun alphaByte(f: Double): Int = (f * 255).roundToInt().coerceIn(0, 255)

    /**
     * Alpha màu SAU khi `Drawable.alpha = alphaByte(f)` nhân vào — phép `GradientDrawable.modulateAlpha` [ĐO AOSP r47
     * `GradientDrawable.java:628-631`]: `alpha × (a + (a >> 7)) >> 8`. Số đo phải nói về cái được VẼ, không về phép nhân
     * lý tưởng. Lớp kính tự vẽ (`WallWindowDrawable`) dùng CÙNG phép này cho lớp ảnh mờ/lớp che/lớp nhuộm để mọi lớp của
     * một nền mờ cùng một luật. `f ≥ 1` ⇒ trả đúng [alpha], không qua phép tính nào.
     */
    fun drawnAlpha(alpha: Int, f: Double): Int {
        if (f >= 1.0) return alpha
        val a = alphaByte(f)
        return (alpha * (a + (a shr 7))) shr 8
    }
}
