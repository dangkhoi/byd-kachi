package com.byd.clusternav.launcher

/**
 * ═══ 2.93 `WIDGET-ICON-OFF-FAINT` — độ đục ICON của ô điều khiển ĐANG TẮT, theo TƯƠNG PHẢN đo được (thuần, `:core`) ═══════
 *
 * Owner 03/10 (ảnh): icon ô TẮT ở cỡ nhỏ gần như vô hình trên chủ đề SÁNG. [ĐO mã] WP2 · R2.1 hạ MỌI icon TẮT về một hằng
 * 0,72 (`ControlTileFactory.ICON_OFF_ALPHA`) chồng lên thang alpha vốn có của icon xe (thân 0,16 · viền 0,38 · chính 1,0 —
 * `ic_car_top_window_*.xml`). [SUY tính tay từ bảng màu] mực icon chủ đề sáng `#4f5b6d` trên thẻ ≈ `#eef1f8`: lớp CHÍNH ở 0,72
 * chỉ còn ≈ 3,3:1 (đủ 1,0 ≈ 6,1:1), viền 0,38 × 0,72 ≈ 1,5:1.
 *
 * Luật (một luật cho mọi chủ đề + mọi màu người dùng chọn — không rẽ nhánh theo tên chủ đề): mờ TỐI ĐA tới [dim] nhưng lớp
 * CHÍNH của icon (alpha 1,0 trong hình) vẫn đạt [floor] trên MỌI nền của ô tắt (hai đầu chuyển sắc). [inner] = alpha mà
 * đường vẽ đã nhân sẵn trước view (bộ lọc "chưa chọn" của mặt lớn chủ đề tối = 0,72) ⇒ view chỉ được mờ thêm phần còn lại.
 * Hệ quả [ĐO bảng 2.93 THẬT, `Widget293ContrastTest`]: chủ đề tối mặt nhỏ 0,72 → 0,73 (0,72 thiếu sàn rất ít ở đầu chuyển sắc
 * sáng hơn — gần như không đổi); chủ đề sáng 0,72 → 0,85.
 * Trạng thái TẮT vẫn đọc ra bằng NỀN ô (không phải nền nhấn) + mực — độ mờ chỉ là lớp thứ hai.
 */
object IconFade {

    /** Sàn tương phản của lớp chính: 4,5:1 — icon trên ô thanh nút chỉ 20–24 dp, nét chính vài px ⇒ dùng sàn của CHỮ. */
    const val FLOOR = 4.5

    /** Bước dò alpha (đủ mịn: 1/100 ≈ dưới ngưỡng mắt phân biệt độ đục). */
    private const val STEP = 0.01

    /**
     * Alpha view cho icon TẮT: nhỏ nhất trong `[dim, 1]` mà mực [ink] (đục) ở `inner × alpha` trên MỌI nền [grounds] đạt
     * [floor]; không mức nào đạt ⇒ 1,0 (đậm nhất có thể — không bao giờ mờ thêm một icon đã thiếu tương phản).
     */
    fun offAlpha(ink: Int, grounds: IntArray, dim: Double, inner: Double = 1.0, floor: Double = FLOOR): Double {
        require(grounds.isNotEmpty()) { "cần ít nhất một nền" }
        var a = dim.coerceIn(0.0, 1.0)
        while (a < 1.0 - 1e-9) {
            if (passes(ink, grounds, inner * a, floor)) return a
            a = minOf(1.0, a + STEP)
        }
        return 1.0
    }

    /** Lớp chính ở alpha thật [alpha] đạt [floor] trên mọi nền không. */
    fun passes(ink: Int, grounds: IntArray, alpha: Double, floor: Double = FLOOR): Boolean =
        grounds.all { g ->
            val ground = ColorMath.withAlpha(g, 255)
            ColorMath.ratio(ColorMath.over(ColorMath.scaleAlpha(ColorMath.withAlpha(ink, 255), alpha), ground), ground) >= floor - 1e-9
        }
}
