package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 2.93 `WIDGET-ICON-OFF-FAINT` — owner 03/10 (ảnh): icon ô TẮT ở cỡ nhỏ gần như vô hình trên chủ đề sáng. Hằng mờ 0,72 cũ
 * đưa lớp chính của icon (mực `#4f5b6d`) xuống 3,29:1 trên thẻ sáng `#eef1f8`. Luật mới ([IconFade]): mờ tới 0,72 nhưng lớp
 * chính giữ ≥ 4,5:1 trên MỌI nền của ô tắt. Màu dưới đây là bảng màu 2.93 (`KachiPalette` LIGHT/DARK — `Widget293ContrastTest`
 * ở `:app` chạy lại trên bảng THẬT).
 */
class IconFadeTest {

    private fun c(hex: String) = ColorMath.parse(hex)

    @Test
    fun `chu de toi mat nho giu 0,72 nhu cu - da du tuong phan`() {
        val a = IconFade.offAlpha(c("#aeb8c8"), intArrayOf(c("#141b30")), 0.72)
        assertEquals(0.72, a, 1e-9, "đường đang ổn không đổi (CLAUDE.md §6)")
    }

    @Test
    fun `chu de sang - mo it hon toi khi lop chinh dat 4,5`() {
        val ink = c("#4f5b6d"); val grounds = intArrayOf(c("#eef1f8"), c("#f6f8fc"))
        assertFalse(IconFade.passes(ink, grounds, 0.72), "bệnh: 0,72 dưới sàn trên thẻ sáng")
        val a = IconFade.offAlpha(ink, grounds, 0.72)
        assertTrue(a > 0.72 && a < 1.0, "vẫn mờ hơn BẬT, nhưng ít hơn: $a")
        assertTrue(IconFade.passes(ink, grounds, a), "đạt sàn ở mức đã chọn")
        assertFalse(IconFade.passes(ink, grounds, a - 0.011), "và là mức mờ NHẤT còn đạt (bước 0,01)")
    }

    @Test
    fun `bo loc da mo san thi view chi mo phan con lai`() {
        val ink = c("#aeb8c8"); val grounds = intArrayOf(c("#141b30"))
        // Mặt lớn chủ đề tối: bộ lọc "chưa chọn" đã nhân 0,72 — bản cũ nhân thêm 0,72 ⇒ 0,52.
        assertFalse(IconFade.passes(ink, grounds, 0.72 * 0.72), "bệnh: hai lần mờ")
        val a = IconFade.offAlpha(ink, grounds, 0.72, inner = 0.72)
        assertTrue(a > 0.72 && IconFade.passes(ink, grounds, 0.72 * a), "view mờ ít hơn để tổng vẫn đạt sàn: $a")
    }

    @Test
    fun `khong muc nao dat thi dam nhat - khong bao gio mo them`() {
        assertEquals(1.0, IconFade.offAlpha(c("#eef1f8"), intArrayOf(c("#eef1f8")), 0.72))
        // Sàn cao hơn ⇒ không mờ hơn (đơn điệu).
        val ink = c("#4f5b6d"); val g = intArrayOf(c("#eef1f8"))
        assertTrue(IconFade.offAlpha(ink, g, 0.72, floor = 5.5) >= IconFade.offAlpha(ink, g, 0.72, floor = 4.5))
    }
}
