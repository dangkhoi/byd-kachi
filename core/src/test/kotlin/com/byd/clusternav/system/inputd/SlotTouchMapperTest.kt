package com.byd.clusternav.system.inputd

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Test

/**
 * [SlotTouchMapper] — map view→display: ĐỒNG NHẤT khi view và display cùng cỡ (đảm bảo daemon dùng cùng toạ độ
 * với fallback `input -d`), scale tuyến tính khi khác cỡ, guard chia-0 trả nguyên toạ độ.
 */
class SlotTouchMapperTest {

    @Test
    fun `identity when view and display are the same size (matches input -d fallback)`() {
        // VdAppHost tạo VD đúng cỡ surface ⇒ đường daemon phải cho ra ĐÚNG (x, y) như fallback input -d dùng.
        assertArrayEquals(intArrayOf(640, 360), SlotTouchMapper.toDisplay(640, 360, 1280, 720, 1280, 720))
        assertArrayEquals(intArrayOf(0, 0), SlotTouchMapper.toDisplay(0, 0, 1280, 720, 1280, 720))
        assertArrayEquals(intArrayOf(1279, 719), SlotTouchMapper.toDisplay(1279, 719, 1280, 720, 1280, 720))
    }

    @Test
    fun `scales linearly when display differs from view`() {
        // view 640×360, display 1280×720 → hệ số 2×.
        assertArrayEquals(intArrayOf(200, 100), SlotTouchMapper.toDisplay(100, 50, 640, 360, 1280, 720))
        // thu nhỏ: view 1000×1000, display 500×500 → nửa.
        assertArrayEquals(intArrayOf(150, 300), SlotTouchMapper.toDisplay(300, 600, 1000, 1000, 500, 500))
    }

    @Test
    fun `guards against zero or negative sizes by returning the raw coords`() {
        assertArrayEquals(intArrayOf(10, 20), SlotTouchMapper.toDisplay(10, 20, 0, 720, 1280, 720))
        assertArrayEquals(intArrayOf(10, 20), SlotTouchMapper.toDisplay(10, 20, 1280, 0, 1280, 720))
        assertArrayEquals(intArrayOf(10, 20), SlotTouchMapper.toDisplay(10, 20, 1280, 720, 0, 720))
        assertArrayEquals(intArrayOf(10, 20), SlotTouchMapper.toDisplay(10, 20, 1280, 720, 1280, -1))
    }
}
