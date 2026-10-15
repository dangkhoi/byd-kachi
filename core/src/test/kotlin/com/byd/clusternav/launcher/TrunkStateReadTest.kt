package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * 3.1.6 · R32 — giải mã trạng thái CỐP: `BYDAutoBodyworkDevice.getDoorState(6)` (fw 2606 `BYDAutoBodyworkDevice.java:207,239-241,
 * 715-716`: area 6 = `BODYWORK_CMD_DOOR_LUGGAGE_DOOR`; `CLOSED 0 · OPEN 1 · UNDEFINED 255`). [ĐO logcat xe 14/10] tiến trình BYD đọc
 * `getDoorState(6)` ⇒ 1 sau lệnh mở / 0 sau lệnh đóng của Kachi. Khoá cả hai đường: datum `tailgate_status` (`readBool`) và nút `trunk`
 * (`readState`) — đi CÙNG getter + gateway của bốn cửa, không đường thứ hai. Đỏ khi: area khác 6 · bỏ lọc 255 (255 ⇒ "đang mở") ·
 * bỏ `BOOL_ENUM` (mã lạ 2 ⇒ "đang mở") · nút mất `readKey`.
 */
class TrunkStateReadTest {

    private fun table(raw: String?, seen: MutableList<Int?> = mutableListOf()) = HalBindingTable(object : HalGateway by FakeHalGateway() {
        override fun getter(deviceFqn: String, method: String, arg: Int?): String? {
            if (method == "getDoorState") seen += arg
            return if (method == "getDoorState" && arg == HalReadTables.TRUNK_DOOR_AREA) raw else null
        }
    })

    @Test
    fun `cop doc getDoorState 6 - 0 dong, 1 mo, 255 va ma la la chua biet`() {
        val seen = mutableListOf<Int?>()
        assertEquals(false, table("0", seen).readBool("tailgate_status"))
        assertEquals(listOf<Int?>(6), seen, "đúng area 6, cùng getter của bốn cửa")
        assertEquals(true, table("1").readBool("tailgate_status"))
        assertNull(table("255").readBool("tailgate_status"), "BODYWORK_STATE_UNDEFINED ⇒ chưa biết, KHÔNG phải mở")
        assertNull(table("2").readBool("tailgate_status"), "mã ngoài thang ⇒ chưa biết")
        assertNull(table(null).readBool("tailgate_status"), "off-car ⇒ chưa biết")
        // Nút: cùng đường, số 0/1; 255 ⇒ null (Đảo cốp ⇒ Unreadable, không bắn).
        assertEquals(0, table("0").readState("trunk"))
        assertEquals(1, table("1").readState("trunk"))
        assertNull(table("255").readState("trunk"))
        assertEquals(ReadPath("tailgate_status", "BYDAutoBodyworkDevice.getDoorState", Domain.BODY, 6), readPathOf("trunk"))
    }
}
