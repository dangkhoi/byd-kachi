package com.byd.clusternav.launcher

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * POLISH2 — [CtlWriteTap] ở đường ghi duy nhất: mỗi LỆNH (kể cả lệnh HAL hỏng — máy ảo không có HAL) gọi người nghe đúng một lần
 * với (nút, tham số chính); lệnh bị cổng có-mặt từ chối thì KHÔNG (xe không nhận gì để nhại); người nghe ném không làm hỏng lệnh.
 */
class CtlWriteTapTest {

    @AfterEach fun off() = CtlWriteTap.set(null)

    private fun flow(getter: Int? = null, rc: Long? = null) = ControlWriteFlow(
        WriteReleaseScheduler.NONE, CtlJournal.NONE, getterInt = { _, _ -> getter }, readState = { null },
        send = { _, _, _ -> rc }, readable = { false },
    )

    @Test
    fun `moi lenh goi nguoi nghe mot lan voi tham so chinh`() {
        val seen = ArrayList<Pair<String, Int>>()
        CtlWriteTap.set { d, p -> seen += d.id to p }
        val drl = ControlRegistry.byId("drl")!!
        flow().write(drl, 0, intArrayOf(2), "named")
        flow(rc = 0).write(ControlRegistry.byId("win_half_lf")!!, 1, intArrayOf(1, 4), "named")
        assertEquals(listOf("drl" to 0, "win_half_lf" to 1), seen)
    }

    @Test
    fun `khong cam thi khong lam gi, nguoi nghe nem khong hong lenh`() {
        assertEquals(7L, flow(rc = 7).write(ControlRegistry.byId("drl")!!, 1, intArrayOf(1), "named"))
        CtlWriteTap.set { _, _ -> error("hỏng") }
        assertEquals(7L, flow(rc = 7).write(ControlRegistry.byId("drl")!!, 1, intArrayOf(1), "named"))
    }

    @Test
    fun `lenh bi cong co-mat tu choi thi khong nhai`() {
        val def = ControlRegistry.ALL.firstOrNull { it.presence != null } ?: return
        val seen = ArrayList<String>()
        CtlWriteTap.set { d, _ -> seen += d.id }
        flow(getter = def.presence!!.absentWhen.first()).write(def, 1, intArrayOf(1), "named")
        assertEquals(emptyList<String>(), seen)
    }
}
