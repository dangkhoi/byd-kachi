package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Khoá bài học R5 (spec `kachi-hal187-cast-remediation` §4.4): ô 1401×748 @200dpi = 598.4dp < 600 ⇒ app coi là
 * điện thoại ⇒ đòi portrait ⇒ size-compat dải dọc. Fixture = đúng kích thước ô đo trên xe 2026-09-15.
 */
class SlotDensityTest {

    /** [ĐO xe 2026-09-15] `kachi-slot-1` 1401×748, density 200 — ca gốc của bug YouTube. */
    @Test
    fun `o 748px o 200dpi bi thieu 1_6dp — phai ha xuong duoi 200 va qua nguong 600dp`() {
        assertTrue(SlotDensity.dpOf(748, 200) < 600.0, "fixture phải tái hiện bệnh: 598.4dp < 600")
        val dpi = SlotDensity.forTablet(748, 200)
        assertTrue(dpi <= 199, "phải hạ (được $dpi)")
        assertTrue(SlotDensity.dpOf(748, dpi) >= 600.0, "sau hạ phải ≥ 600dp (được ${SlotDensity.dpOf(748, dpi)})")
        assertEquals(199, dpi, "hạ ÍT NHẤT đủ qua ngưỡng — không hạ sâu vô cớ làm UI bé")
    }

    @Test
    fun `o thap 458px — ha sat san 122`() {
        val dpi = SlotDensity.forTablet(458, 200)
        assertEquals(122, dpi)
        assertTrue(dpi >= SlotDensity.FLOOR_DPI)
        assertTrue(SlotDensity.dpOf(458, dpi) >= 600.0)
    }

    @Test
    fun `o qua thap 300px — muc tieu duoi san ⇒ GIU default, khong ha`() {
        // 300×160/600 = 80 < 120 ⇒ có hạ cũng không thành tablet ⇒ giữ hành vi cũ (không tệ hơn).
        assertEquals(200, SlotDensity.forTablet(300, 200))
    }

    @Test
    fun `o da la tablet o density mac dinh ⇒ giu nguyen`() {
        // 1080×160/200 = 864dp ≥ 600 ⇒ không đụng.
        assertEquals(200, SlotDensity.forTablet(1080, 200))
        // Đúng biên: 750×160/200 = 600.0 ⇒ giữ.
        assertEquals(200, SlotDensity.forTablet(750, 200))
    }

    @Test
    fun `ket qua luon nho hon hoac bang default — khong bao gio nang density`() {
        for (px in listOf(1, 100, 300, 449, 450, 458, 598, 748, 749, 750, 1000, 4000)) {
            for (def in listOf(120, 160, 200, 240, 320)) {
                val r = SlotDensity.forTablet(px, def)
                assertTrue(r <= def, "px=$px def=$def → $r > default")
                assertTrue(r == def || r >= SlotDensity.FLOOR_DPI, "px=$px def=$def → $r dưới sàn")
                if (r != def) assertTrue(SlotDensity.dpOf(px, r) >= 600.0, "px=$px def=$def → hạ mà vẫn <600dp")
            }
        }
    }

    @Test
    fun `dau vao rac ⇒ tra default (fail-safe khong doi gi)`() {
        assertEquals(200, SlotDensity.forTablet(0, 200))
        assertEquals(200, SlotDensity.forTablet(-5, 200))
        assertEquals(0, SlotDensity.forTablet(748, 0))
        assertEquals(200, SlotDensity.forTablet(748, 200, thresholdDp = 0))
    }

    @Test
    fun `nguong va san tuy chinh duoc`() {
        // Ngưỡng 720dp: 748×160/720 = 166.
        assertEquals(166, SlotDensity.forTablet(748, 200, thresholdDp = 720))
        // Sàn cao hơn kết quả ⇒ giữ default.
        assertEquals(200, SlotDensity.forTablet(458, 200, floorDpi = 160))
    }
}
