package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 3.01 DOT — khoá ngôn ngữ chấm tròn thay lớp "kính hạ" trên ảnh xe. Owner 13/10: *"thay vì vẽ hẳn 1 cái gạch màu xanh,
 * mình vẽ chấm tròn thôi, như bọn kinex, kimlauncher"*. Khoá: bốn trạng thái tách bạch (chưa đọc ≠ đóng), mở một phần
 * tô quạt theo tỉ lệ (kẹp để không lẫn đóng/đầy — owner 13/10 "bỏ số luôn em", chấm là chỉ báo duy nhất), bảng cửa
 * dùng cùng ngôn ngữ.
 */
class CarDotsTest {

    @Test
    fun `bon trang thai, chua doc khong thanh dong`() {
        assertEquals(CarDots.Kind.UNKNOWN, CarDots.ofCell(false, 0.5f, GroupTone.ACTIVE).kind)
        assertEquals(CarDots.Kind.CLOSED, CarDots.ofCell(true, 0f, GroupTone.NEUTRAL).kind)
        assertEquals(CarDots.Kind.PARTIAL, CarDots.ofCell(true, 0.35f, GroupTone.ACTIVE).kind)
        assertEquals(CarDots.Kind.OPEN, CarDots.ofCell(true, 1f, GroupTone.ACTIVE).kind)
        // Không có số `%` ⇒ theo sắc thái (luật R22 cũ giữ nguyên).
        assertEquals(CarDots.Kind.CLOSED, CarDots.ofCell(true, null, GroupTone.NEUTRAL).kind)
        assertEquals(CarDots.Kind.OPEN, CarDots.ofCell(true, null, GroupTone.ACTIVE).kind)
        assertTrue(CarDots.ofCell(true, 1f, GroupTone.ALERT).warn)
    }

    @Test
    fun `quat theo ti le, nhan chi canh cham mo`() {
        val p35 = CarDots.ofCell(true, 0.35f, GroupTone.ACTIVE)
        val p50 = CarDots.ofCell(true, 0.5f, GroupTone.ACTIVE)
        assertEquals(126f, CarDots.sweep(p35), 1e-3f)
        assertEquals(180f, CarDots.sweep(p50), 1e-3f)
        assertEquals(360f, CarDots.sweep(CarDots.ofCell(true, 1f, GroupTone.ACTIVE)), 1e-3f)
        assertEquals(0f, CarDots.sweep(CarDots.ofCell(true, 0f, GroupTone.NEUTRAL)), 1e-3f)
        // Không còn số trên màn ⇒ mở-một-phần phải tách rõ khỏi đóng (0°) và đầy (360°) cả ở mép.
        assertEquals(CarDots.MIN_SWEEP, CarDots.sweep(CarDots.ofCell(true, 0.03f, GroupTone.ACTIVE)), 1e-3f)
        assertEquals(360f - CarDots.MIN_SWEEP, CarDots.sweep(CarDots.ofCell(true, 0.97f, GroupTone.ACTIVE)), 1e-3f)
    }

    @Test
    fun `bang cua cung ngon ngu cham`() {
        fun part(open: Boolean, available: Boolean) =
            CarPartState(CarPart.TAILGATE, "", "", "", if (open) GroupTone.ACTIVE else GroupTone.NEUTRAL, open, available)
        assertEquals(CarDots.Kind.OPEN, CarDots.ofPart(part(true, true)).kind)
        assertEquals(CarDots.Kind.CLOSED, CarDots.ofPart(part(false, true)).kind)
        assertEquals(CarDots.Kind.UNKNOWN, CarDots.ofPart(part(false, false)).kind)
        // Vật không có kính ⇒ neo bộ phận của chính ảnh.
        assertEquals(CarArtCatalog.SEAL_TOP.part(CarPart.TAILGATE), CarDots.center(CarPart.TAILGATE, CarArtCatalog.SEAL_TOP))
    }
}
