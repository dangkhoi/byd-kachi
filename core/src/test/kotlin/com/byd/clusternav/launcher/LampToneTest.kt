package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 3.01 POLISH — owner 13/10 *"phân biệt ra dạng icon cho hiểu, với cả cái đèn xinhan có nhấp nháy theo xe không?"* · *"phần kính
 * thêm 1 action là 50% tất cả kính"*. Khoá: màu đèn báo theo loại đèn (dữ liệu, không theo ô) · CHỈ xi-nhan nháy · nhịp ~1,5 Hz
 * bắt đầu SÁNG · việc "hé tất cả" lấy từ MỨC của nút nhóm Kính thật (`win_half_all`), nằm giữa mở/đóng tất cả.
 */
class LampToneTest {

    @Test
    fun `mau den bao theo loai den, ma la thi khong doan`() {
        assertEquals(LampTone.Tone.AMBER, LampTone.of("light_left_turn"))
        assertEquals(LampTone.Tone.AMBER, LampTone.of("light_right_turn"))
        assertEquals(LampTone.Tone.AMBER, LampTone.of("light_rear_fog"))
        assertEquals(LampTone.Tone.GREEN, LampTone.of("light_low_beam"))
        assertEquals(LampTone.Tone.GREEN, LampTone.of("light_front_fog"))
        assertEquals(LampTone.Tone.BLUE, LampTone.of("light_high_beam"))
        assertNull(LampTone.of("headlight_feedback"))
        assertNull(LampTone.of(null))
        // Mọi đèn đọc của nhóm Đèn có màu, trừ chế độ đèn (không phải một bóng).
        CapabilityGroups.LIGHTS.reads.filter { it.startsWith("light_") }
            .forEach { assertTrue(LampTone.of(it) != null, "$it thiếu màu đèn báo") }
    }

    @Test
    fun `chi xi-nhan nhay`() {
        assertTrue(LampTone.blinks("light_left_turn") && LampTone.blinks("light_right_turn"))
        listOf("light_low_beam", "light_high_beam", "light_front_fog", "light_rear_fog", "light_side", "light_drl", null)
            .forEach { assertFalse(LampTone.blinks(it), "$it không được nháy") }
    }

    @Test
    fun `nhip 1,5 Hz bat dau sang, hen dung mep pha ke`() {
        assertTrue(LampTone.litAt(0), "bật lên là sáng ngay")
        assertTrue(LampTone.litAt(332))
        assertFalse(LampTone.litAt(333))
        assertFalse(LampTone.litAt(665))
        assertTrue(LampTone.litAt(666))
        assertEquals(1.5, 1000.0 / (2 * LampTone.HALF_MS), 0.01)
        assertEquals(333, LampTone.nextEdgeIn(0))
        assertEquals(1, LampTone.nextEdgeIn(332))
        assertEquals(333, LampTone.nextEdgeIn(333))
        assertTrue((0L..2000L).all { LampTone.nextEdgeIn(it) in 1..LampTone.HALF_MS })
        // Pha đổi ĐÚNG tại mép đã hẹn — không vẽ thừa giữa pha.
        (0L..1500L step 37).forEach { t -> assertEquals(!LampTone.litAt(t), LampTone.litAt(t + LampTone.nextEdgeIn(t)), "t=$t") }
    }

    @Test
    fun `he tat ca kinh = nut ca xe muc 50, giua mo va dong tat ca`() {
        val mx = GroupActionMatrix.of(GroupBoard.of(CapabilityGroups.WINDOWS, CarStatus()))!!
        assertEquals("windows_all", mx.openAll?.id)
        assertEquals("win_half_all", mx.halfAll?.id, "dùng ControlDef sẵn có — không đường HAL mới")
        assertEquals(50, mx.halfAll?.level)
        assertEquals("windows_close_all", mx.closeAll?.id)
        assertEquals(intArrayOf(4, 4, 4, 4).toList(), HalWriteArgs.writeArgs(ControlRegistry.byId("win_half_all")!!, 1).toList(), "setAllWindowState(HALF×4)")
    }
}
