package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ 3.1.6 · Owner 15/10 (xe): *"tắt đèn ban ngày trên 3D, đèn tắt rồi 3D tự sáng lại"* ═══════════════════════════════
 *
 * Khoá phép GIẢI số đọc DRL. Fixture là chuỗi thô NGUYÊN VĂN của xe (`sweep-2026092x`: `"int=2 float=- buf=-"` / `"int=1 …"`) —
 * feature `985661476` (`LIGHT_DAY_RUNNING_LIGHT_AUTO_STATE`), thang `DAYTIME_LIGHT_INVALID=0 · OPEN=1 · CLOSE=2 · UNDEFINED=3`
 * (`BYDAutoLightDevice.java:8-11`). Trước bản vá `coerceBool` = `> 0` ⇒ `2` thành BẬT ⇒ bài `2 la TAT` ĐỎ.
 */
class DrlReadDecodeTest {

    private fun table(raw: String) = HalBindingTable(FakeHalGateway(features = mapOf(985661476 to raw)))

    @Test fun `2 la TAT - khong phai BAT nhu coerceBool`() {
        assertEquals(false, table("int=2 float=- buf=-").readBool("light_drl"))
    }

    @Test fun `1 la BAT`() {
        assertEquals(true, table("int=1 float=- buf=-").readBool("light_drl"))
    }

    @Test fun `0 INVALID va 3 UNDEFINED la chua biet - khong doan`() {
        assertNull(table("int=0 float=- buf=-").readBool("light_drl"))
        assertNull(table("int=3 float=- buf=-").readBool("light_drl"))
    }

    /** Cả chuỗi tới [CarStatus.Lights.drl] mà bảng Đèn 2D + xe 3D đọc (`GroupBoard` `light_drl`) — không chỉ hàm giải. */
    @Test fun `nhip cham dua TAT xuong CarStatus - 3D khong sang lai sau khi het giu lac quan`() {
        val off = CarDataAdapter(table("int=2 float=- buf=-"), demand = { setOf("light_drl") }).readSlow(CarStatus())
        assertFalse(off.lights.drl!!, "xe báo 2 (TẮT) ⇒ đèn 3D phải TẮT")
        val on = CarDataAdapter(table("int=1 float=- buf=-"), demand = { setOf("light_drl") }).readSlow(CarStatus())
        assertTrue(on.lights.drl!!)
    }

    @Test fun `datum bool thuong khong doi - coerceBool van giai 1-0`() {
        assertNull(HalReadTables.enumBool("light_side", 1), "datum ngoài bảng ⇒ không đi đường enum")
        assertEquals(true, HalBindingTable.coerceBool("1"))
        assertEquals(false, HalBindingTable.coerceBool("0"))
    }
}
