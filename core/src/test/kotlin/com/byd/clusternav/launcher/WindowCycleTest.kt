package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * 3.01 R22·C — khoá vòng chạm chấm kính (owner 13/10: *"click vô từng chấm tròn thì mở 50%, nhấn lần 2 mở hết, nhấn lần 3
 * thì đóng hết"*). Bước kế đọc từ MỨC đang có (không đếm lần chạm) và lệnh chọn theo mức DỮ LIỆU của nút nhóm Kính thật.
 */
class WindowCycleTest {

    private val windows = GroupActionMatrix.of(GroupBoard.of(CapabilityGroups.WINDOWS, CarStatus()))!!
    private val lf = windows.placed.first { it.place == CarPart.DOOR_LF }

    @Test
    fun `vong dong - 50 - mo han - dong, tu muc dang co`() {
        assertEquals(50, WindowCycle.next(0), "đóng ⇒ hé 50 %")
        assertEquals(50, WindowCycle.next(null), "chưa đọc được ⇒ bước an toàn nhất: hé 50 %, không mở hẳn")
        assertEquals(50, WindowCycle.next(35), "hé dưới 50 % (vd 35 %) ⇒ lên nấc 50 %, không nhảy cóc")
        assertEquals(100, WindowCycle.next(50))
        assertEquals(100, WindowCycle.next(80))
        assertEquals(0, WindowCycle.next(100), "mở hẳn ⇒ đóng")
        // Ba lần chạm liên tiếp (dùng đích lạc quan làm mức) đi đúng vòng owner tả.
        assertEquals(listOf(50, 100, 0), generateSequence(WindowCycle.next(0)) { WindowCycle.next(it) }.take(3).toList())
    }

    @Test
    fun `lenh chon theo muc cua nut, ghi bang nut san co`() {
        assertEquals(WindowCycle.Step(lf.slots.first { it?.id == "win_half_lf" }!!, true), WindowCycle.plan(lf.slots, 50))
        assertEquals(WindowCycle.Step(lf.slots.first { it?.id == "win_lf" }!!, true), WindowCycle.plan(lf.slots, 100))
        assertEquals(WindowCycle.Step(lf.slots.first { it?.id == "win_lf" }!!, false), WindowCycle.plan(lf.slots, 0), "đóng = TẮT nút mở hẳn")
        assertNull(WindowCycle.plan(lf.slots, 70), "không có nút cho mức 70 ⇒ không bịa lệnh")
        assertNull(WindowCycle.plan(emptyList(), 50))
    }

    @Test
    fun `muc tu o xem - chua doc la null`() {
        assertNull(WindowCycle.levelOf(false, 0.35f))
        assertNull(WindowCycle.levelOf(true, null))
        assertEquals(35, WindowCycle.levelOf(true, 0.35f))
        assertEquals(100, WindowCycle.levelOf(true, 1.4f))
    }
}
