package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * 2.93 `APPWIDGET-SIZE-API31` — hai đường khai cỡ widget app khác ([AppWidgetSize]) cho nhà cung cấp CÙNG số MIN/MAX nguyên.
 * Phép trừ của framework mô phỏng đúng số học của nguồn AOSP 12.1.0_r27 (`int` ở bản 5 tham số L416-422; `float` ở bản
 * `List<SizeF>` L371-372 · L381 · L395). Vét cạn: nội dung 0–3 000 px × lề mặc định thường gặp × 7 mật độ.
 */
class AppWidgetSizeTest {

    private val densities = floatArrayOf(1f, 1.25f, 1.5f, 1.75f, 2f, 2.625f, 3f)
    private val pads = intArrayOf(0, 12, 24, 36, 37, 45, 48, 60)

    @Test
    fun `hai duong API cho nha cung cap cung so nguyen - dung vung ve that`() {
        var n = 0
        for (d in densities) for (p in pads) for (c in 0..3000) {
            val legacy = AppWidgetSize.legacyReceived(AppWidgetSize.legacyDp(c, p, d), p, d)
            val modern = AppWidgetSize.modernReceived(AppWidgetSize.modernDp(c, p, d), p, d)
            val truth = (c / d).toInt()
            if (legacy != truth || modern != truth) throw AssertionError("c=$c p=$p d=$d: cũ $legacy · mới $modern · thật $truth")
            n++
        }
        assertEquals(densities.size * pads.size * 3001, n)
    }

    /**
     * Soát senior 2.93 Pass 1 [P3] — MỌI mật độ nguyên 100–720 dpi (`wm density` đặt được số lẻ): bản Pass 0 khai
     * `nội dung/d + lề/d + EPS` báo HƠN đường cũ 1 dp ở 2 427 ca (vd 232 dpi, 87 px: 87/1,45 = 59,999996f ⇒ EPS đẩy lên 60).
     */
    @Test
    fun `moi mat do nguyen - hai duong van cung so, ke ca khi float chia hut so nguyen`() {
        for (dpi in 100..720) {
            val d = dpi / 160f
            for (p in intArrayOf(0, 36, 48)) for (c in 0..3000) {
                val legacy = AppWidgetSize.legacyReceived(AppWidgetSize.legacyDp(c, p, d), p, d)
                val modern = AppWidgetSize.modernReceived(AppWidgetSize.modernDp(c, p, d), p, d)
                if (legacy != modern) throw AssertionError("dpi=$dpi c=$c p=$p: cũ $legacy · mới $modern")
            }
        }
        assertEquals(59, AppWidgetSize.modernReceived(AppWidgetSize.modernDp(87, 0, 1.45f), 0, 1.45f), "ca 232 dpi của bản Pass 0")
    }

    @Test
    fun `ca may ao 2_92 - noi dung 905x780 px, le mac dinh sw720dp 18+18 va 6+30 px`() {
        assertEquals(627, AppWidgetSize.legacyDp(905, 36, 1.5f), "= 2.92 (603 + 24)")
        assertEquals(603, AppWidgetSize.legacyReceived(627, 36, 1.5f), "[ĐO máy ảo 2.92] cỡ báo 603×520 dp")
        assertEquals(520, AppWidgetSize.modernReceived(AppWidgetSize.modernDp(780, 36, 1.5f), 36, 1.5f))
    }
}
