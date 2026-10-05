package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ 2.89 · B3 DOCK-SCALE — phần số học thuần của cỡ thanh nút ([BarScale]) + [DockConfig.scalePct] ════════════════════
 *
 * Khoá ba điều mà mọi chỗ khác dựa vào: (1) 100 % là ĐƯỜNG CŨ từng bit (mật độ trả lại chính số gốc, khoá lưu bị xoá);
 * (2) bề dày thanh tính ở `DockAreaLayout` và ô dựng bằng `Context` ghi đè ra CÙNG mật độ (cùng phép `ResourcesImpl`);
 * (3) luật vùng chạm theo trục thanh: ≥ 48 dp mỗi đích, trần = cỡ hình ở 100 %.
 */
class BarScaleTest {

    @Test
    fun `dai 50-150 buoc 5 - 21 vi tri, owner 05-10`() {
        assertEquals(50, BarScale.MIN); assertEquals(150, BarScale.MAX); assertEquals(5, BarScale.STEP)
        assertEquals(100, BarScale.DEFAULT)
        assertEquals(20, BarScale.POSITIONS, "21 vị trí")
        assertEquals((50..150 step 5).toList(), (0..BarScale.POSITIONS).map(BarScale::ofPosition))
        (0..BarScale.POSITIONS).forEach { assertEquals(it, BarScale.position(BarScale.ofPosition(it)), "nghịch đảo ở vị trí $it") }
        assertEquals(10, BarScale.position(100), "100 % đứng giữa thanh kéo")
        assertEquals(50, BarScale.ofPosition(-3)); assertEquals(150, BarScale.ofPosition(99))
    }

    @Test
    fun `snap ve boi 5 gan nhat va kep dai`() {
        mapOf(49 to 50, 50 to 50, 52 to 50, 53 to 55, 87 to 85, 88 to 90, 100 to 100, 149 to 150, 250 to 150, -3 to 50, 0 to 50)
            .forEach { (raw, want) -> assertEquals(want, BarScale.snap(raw), "snap($raw)") }
        assertTrue(BarScale.isIdentity(100) && BarScale.isIdentity(101) && !BarScale.isIdentity(95))
    }

    @Test
    fun `ma hoa - 100 la xoa khoa, rac khong nem`() {
        assertNull(BarScale.encode(100), "100 % ⇒ xoá khoá: prefs người chưa chạm giữ nguyên từng byte")
        assertEquals("85", BarScale.encode(85)); assertEquals("50", BarScale.encode(10))
        mapOf(null to 100, "" to 100, "abc" to 100, " 85 " to 85, "250" to 150, "-3" to 50, "87" to 85, "1.5" to 100)
            .forEach { (raw, want) -> assertEquals(want, BarScale.decode(raw), "decode($raw)") }
        (0..BarScale.POSITIONS).map(BarScale::ofPosition).forEach {
            assertEquals(it, BarScale.decode(BarScale.encode(it)), "khứ hồi $it %")
        }
    }

    @Test
    fun `100 phan tram tra lai chinh mat do goc, khong tinh lai`() {
        listOf(1.0f, 1.33125f, 1.5f, 2.0f, 3.0f).forEach { d -> assertEquals(d.toRawBits(), BarScale.scaledDensity(d, 100).toRawBits()) }
        listOf(160, 213, 240, 320, 480).forEach { assertEquals(it, BarScale.scaledDpi(it, 100)) }
    }

    /**
     * Thanh (`DockAreaLayout`: `(v × scaledDensity).toInt()`) và ô (`KachiTheme.dpi` trên `Context` có `densityDpi` ghi đè:
     * `ResourcesImpl` đặt `density = densityDpi × DENSITY_DEFAULT_SCALE`) phải ra CÙNG số — lệch 1 px là ô tràn/hở thanh.
     */
    @Test
    fun `mat do thanh trung bit voi mat do Context ghi de`() {
        listOf(160, 213, 240, 320, 480).forEach { base ->
            val baseDensity = base * (1.0f / 160)
            (0..BarScale.POSITIONS).map(BarScale::ofPosition).filter { it != 100 }.forEach { p ->
                val dpi = BarScale.scaledDpi(base, p)
                assertEquals(dpi * (1.0f / 160), BarScale.scaledDensity(baseDensity, p), "@$base dpi $p %")
            }
        }
        assertEquals(120, BarScale.scaledDpi(240, 50)); assertEquals(204, BarScale.scaledDpi(240, 85))
        assertEquals(360, BarScale.scaledDpi(240, 150))
        // 240 dpi (máy ảo + xe theo dump cũ): mỗi bước đúng 12 dpi ⇒ không làm tròn.
        (0..BarScale.POSITIONS).map(BarScale::ofPosition).forEach { assertEquals(240 * it / 100, BarScale.scaledDpi(240, it)) }
    }

    @Test
    fun `mat do don dieu theo phan tram`() {
        listOf(160, 213, 240, 320, 480).forEach { base ->
            val dpis = (0..BarScale.POSITIONS).map { BarScale.scaledDpi(base, BarScale.ofPosition(it)) }
            assertEquals(dpis.sorted(), dpis, "@$base dpi")
            assertTrue(dpis.zipWithNext().all { (a, b) -> b > a }, "@$base dpi: mỗi bước phải đổi mật độ")
        }
    }

    /** 240 dpi: 48 dp = 72 px. Ô ngang 71 dp = 106 px ở 100 %; ô dọc 60 dp = 90 px. */
    @Test
    fun `vung cham theo truc - san 48dp, tran la co hinh o 100`() {
        val touch = 72
        // Ô một-đích ở 50 %: hình 53 px + lề 2×3 = 59 < 72 ⇒ khung 72 px (48 dp).
        assertEquals(72, BarScale.cellAlongPx(visualPx = 53, marginPx = 3, touchPx = touch, targets = 1, len100Px = 106))
        // ≥ ~61 %: khung = hình + lề (thanh co đều, không sàn).
        assertEquals(84 + 10, BarScale.cellAlongPx(84, 5, touch, 1, 106))
        // Ô STEP ngang (2 đích): sàn 2×72 = 144 bị trần 106 (cỡ ở 100 %) ⇒ mỗi nửa 53 px = 35,5 dp như hôm nay, không tệ hơn.
        assertEquals(106, BarScale.cellAlongPx(53, 3, touch, 2, 106))
        // Ô STEP ở 150 %: hình 159 + lề 2×9 = 177 ⇒ mỗi nửa ≥ 48 dp tự nhiên.
        assertEquals(177, BarScale.cellAlongPx(159, 9, touch, 2, 106))
        // targets ≤ 0 coi như 1 (không bao giờ bỏ sàn).
        assertEquals(72, BarScale.cellAlongPx(10, 0, touch, 0, 106))
    }

    @Test
    fun `kep toa do cham vao hinh o`() {
        assertEquals(0f, BarScale.clampInto(-12f, 50)); assertEquals(49f, BarScale.clampInto(80f, 50))
        assertEquals(20.5f, BarScale.clampInto(20.5f, 50)); assertEquals(0f, BarScale.clampInto(5f, 0))
    }

    @Test
    fun `moi duong copy cua DockConfig giu co thanh`() {
        val c = DockConfig(edge = DockEdge.BOTTOM, scalePct = 70)
        assertEquals(70, c.withEdge(DockEdge.RIGHT).scalePct)
        assertEquals(70, c.withVisible(false).scalePct)
        assertEquals(70, c.setEnabled("fan", false).scalePct)
        assertEquals(70, c.moveEnabled(c.enabled.first(), 1).scalePct)
        assertEquals(70, DockSelection.apply(c, setOf("fan", "temp")).scalePct)
        assertEquals(85, c.withScale(87).scalePct, "withScale đi qua snap")
        assertEquals(100, DockConfig().scalePct, "mặc định = hôm nay")
        assertEquals(DockConfig(), DockConfig().withScale(100), "100 % ⇒ cấu hình bằng hệt mặc định (đường cũ)")
    }
}
