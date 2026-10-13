package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

/**
 * 3.01 TURN — owner 13/10 *"đèn xinhan có nhấp nháy theo xe không?"* → *"làm luôn"*. Khoá: trạng thái socket xi-nhan (nguồn thật,
 * [ĐO xe 09-25/26]) là giá trị QUYẾT ĐỊNH cho `light_left_turn`/`light_right_turn` của bảng Đèn khi có; không có ⇒ giữ NGUYÊN số
 * đọc getter (cùng đối tượng — không thêm lượt vẽ). Chuỗi BẬT/TẮT giả từ nguồn ⇒ tông ô bảng Đèn đi theo đúng từng bước.
 *
 * Thử ĐỎ [ĐO 13/10]: đổi `overlay` thành luôn `return s` ⇒ `chuoi bat tat tu socket` + `socket de so doc getter` ĐỎ.
 */
class LampLiveTest {

    private fun tone(s: CarStatus, id: String): GroupTone =
        GroupBoard.of(CapabilityGroups.LIGHTS, s).cells.first { it.id == id }.tone

    @Test
    fun `khong co socket - giu nguyen so doc, cung doi tuong`() {
        val read = CarStatus(lights = CarStatus.Lights(leftTurn = true))
        assertSame(read, LampLive.overlay(read, null))
        val blank = CarStatus()
        assertSame(blank, LampLive.overlay(blank, null))
    }

    @Test
    fun `socket de so doc getter - getter tra 0 nhung xe dang bat`() {
        // [ĐO xe 09-25] getter luôn 0 ⇒ CarStatus nói TẮT; socket nói trái BẬT ⇒ bảng phải thấy BẬT.
        val getter = CarStatus(lights = CarStatus.Lights(leftTurn = false, rightTurn = false, lowBeam = true))
        val out = LampLive.overlay(getter, LampLive.Lamps(left = true, right = false))
        assertEquals(true, out.lights.leftTurn)
        assertEquals(false, out.lights.rightTurn)
        assertEquals(true, out.lights.lowBeam, "chỉ đụng hai trường xi-nhan")
        assertEquals(GroupTone.ACTIVE, tone(out, "light_left_turn"))
        assertEquals(GroupTone.NEUTRAL, tone(out, "light_right_turn"))
    }

    @Test
    fun `chuoi bat tat tu socket - bang Den theo tung buoc`() {
        val live = LampLive()
        val car = CarStatus()
        val seq = listOf(
            LampLive.Lamps(false, false) to (GroupTone.NEUTRAL to GroupTone.NEUTRAL),   // vừa nối
            LampLive.Lamps(true, false) to (GroupTone.ACTIVE to GroupTone.NEUTRAL),     // gạt trái
            LampLive.Lamps(false, false) to (GroupTone.NEUTRAL to GroupTone.NEUTRAL),   // OFF ⇒ tắt NGAY
            LampLive.Lamps(false, true) to (GroupTone.NEUTRAL to GroupTone.ACTIVE),     // gạt phải
            LampLive.Lamps(true, true) to (GroupTone.ACTIVE to GroupTone.ACTIVE),       // đèn khẩn
        )
        for ((lamps, want) in seq) {
            live.publish(lamps.left, lamps.right)
            val s = LampLive.overlay(car, live.state.value)
            assertEquals(want, tone(s, "light_left_turn") to tone(s, "light_right_turn"), "sau $lamps")
        }
        live.clear()
        assertNull(live.state.value)
        assertSame(car, LampLive.overlay(car, live.state.value), "đứt socket ⇒ lùi về số đọc")
    }

    @Test
    fun `trung so doc - khong tao doi tuong moi`() {
        val s = CarStatus(lights = CarStatus.Lights(leftTurn = true, rightTurn = false))
        assertSame(s, LampLive.overlay(s, LampLive.Lamps(true, false)))
    }

    /** 3.01 LAMPLIVE — đèn khác xi-nhan: trường socket `null` (chưa thấy sự kiện) GIỮ getter; có giá trị ⇒ đè. */
    @Test
    fun `den khac - null giu getter, co gia tri thi de`() {
        val getter = CarStatus(lights = CarStatus.Lights(lowBeam = true, highBeam = false, frontFog = false, drl = true))
        val only = LampLive.overlay(getter, LampLive.Lamps(false, false))
        assertEquals(true, only.lights.lowBeam, "chưa có sự kiện cốt ⇒ giữ getter")
        assertEquals(true, only.lights.drl)
        val ev = LampLive.Lamps(false, false).withType(LampLive.TYPE_LOW, false).withType(LampLive.TYPE_HIGH, true)
            .withType(LampLive.TYPE_FRONT_FOG, true).copy(drl = false)
        val out = LampLive.overlay(getter, ev)
        assertEquals(false, out.lights.lowBeam)
        assertEquals(true, out.lights.highBeam)
        assertEquals(true, out.lights.frontFog)
        assertEquals(false, out.lights.drl)
        assertEquals(GroupTone.ACTIVE, tone(out, "light_high_beam"))
        assertEquals(GroupTone.NEUTRAL, tone(out, "light_low_beam"))
    }

    @Test
    fun `withType - thang getLightStatus, type la tra nguyen`() {
        val z = LampLive.Lamps(false, false)
        assertEquals(true, z.withType(1, true).side)
        assertEquals(true, z.withType(2, true).lowBeam)
        assertEquals(true, z.withType(3, true).highBeam)
        assertEquals(true, z.withType(4, true).left)
        assertEquals(true, z.withType(5, true).right)
        assertEquals(true, z.withType(6, true).frontFog)
        assertEquals(true, z.withType(7, true).rearFog)
        assertSame(z, z.withType(8, true), "LIGHT_FOOT/ lạ ⇒ không đoán")
        assertSame(z, z.withType(0, true))
    }
}
