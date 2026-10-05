package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * A5(b) · SLOT-CORNER-ROUND (spec `kachi-289-field-fixes.html` §A5) — bán kính cắt của khung ô. Khoá lỗi [ĐO ảnh xe 05/10]:
 * app trong ô không bị cắt bo (góc vuông) vì khung mượn đường viền của nền (chữ nhật khi có ảnh nền — KDoc `SlotFrameClip`).
 */
class SlotFrameShapeTest {

    @Test
    fun `ban kinh thiet ke giu nguyen khi khung du lon`() {
        assertEquals(16f * 1.5f, SlotFrameShape.radius(1129, 804, 16f * 1.5f))   // ô 1129×804 (máy ảo), 16 dp @ 1.5
        assertEquals(32f, SlotFrameShape.radius(1872, 956, 32f))                // ô YouTube trên xe 05/10 (màn ảo 1872×956)
    }

    @Test
    fun `khung hep - kep nua canh ngan, khong lat goc`() {
        assertEquals(20f, SlotFrameShape.radius(300, 40, 32f))
        assertEquals(0.5f, SlotFrameShape.radius(1, 500, 32f))
    }

    @Test
    fun `ban kinh hong - goc vuong nhung van cat theo mep`() {
        assertEquals(0f, SlotFrameShape.radius(800, 480, -4f))
        assertEquals(0f, SlotFrameShape.radius(800, 480, Float.NaN))
        assertEquals(0f, SlotFrameShape.radius(800, 480, 0f))
    }

    @Test
    fun `khung chua co co - khong hinh de cat`() {
        assertNull(SlotFrameShape.radius(0, 480, 24f))
        assertNull(SlotFrameShape.radius(800, 0, 24f))
        assertNull(SlotFrameShape.radius(-1, -1, 24f))
    }
}
