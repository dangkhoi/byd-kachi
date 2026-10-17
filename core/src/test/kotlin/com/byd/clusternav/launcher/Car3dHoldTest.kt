package com.byd.clusternav.launcher

import com.byd.clusternav.launcher.Car3dHold.Why
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 3.1.8 · `CAR3D-FALLBACK-STICKY` — owner 15/10 *"có nhiều lúc đã lên 3D rồi, nhưng lại bị trả về 2D"* → *"không bao giờ lùi về
 * 2D … không chạy thì bỏ qua, coi như không khả thi"*. Khoá: lý do THOÁNG QUA không bao giờ chốt vĩnh viễn; lý do VĨNH VIỄN im
 * lặng sau lần đầu và không thử lại; lỗi thử lại có lùi dần, có trần.
 */
class Car3dHoldTest {

    @Test
    fun `phan loai - chi may khong kham, thieu thu vien, khong mo hinh, mo hinh rong la vinh vien`() {
        assertEquals(setOf(Why.INCAPABLE, Why.NO_LIBS, Why.NO_MODEL, Why.EMPTY_MODEL), Why.values().filter { it.permanent }.toSet())
        // [ĐO máy ảo 15/10, 3.1.6] hai ca thoáng qua từng chốt bảng 2D vĩnh viễn: tệp đệm dở (gltfio ⇒ FAULT) · thua chỗ Engine (SEAT).
        for (w in listOf(Why.SLOT, Why.SEAT, Why.FAULT)) assertFalse(w.permanent, "$w là thoáng qua")
    }

    @Test
    fun `thoang qua - van duoc dung lai, o nho va thua cho cho su kien khong hen gio`() {
        for (w in listOf(Why.SLOT, Why.SEAT)) {
            val h = Car3dHold()
            val n = h.park(w)
            assertTrue(n.log); assertNull(n.retryMs, "$w chờ sự kiện (đổi cỡ / Car3dSeat đánh thức / hiện lại), không vòng hẹn giờ")
            assertTrue(h.canStart(), "$w không chốt — lượt hiện/đổi cỡ sau dựng lại 3D")
            assertFalse(h.park(w).log, "$w lặp (mỗi lượt đổi cỡ) không ghi log lặp")
            h.started(); assertNull(h.why); assertTrue(h.park(w).log, "dựng lại rồi dừng lại ⇒ log lại")
        }
    }

    @Test
    fun `loi - thu lai lui dan co tran, qua tran thi thoi va ghi log`() {
        val h = Car3dHold()
        val retries = (1..Car3dHold.MAX_FAULTS).map { h.park(Why.FAULT).also { n -> assertTrue(n.log && !n.gaveUp) }.retryMs }
        assertEquals(listOf(2_000L, 8_000L, 32_000L), retries, "lùi dần 2 s · 8 s · 32 s")
        assertTrue(h.canStart(), "còn trong trần ⇒ lượt hẹn còn được dựng")
        val last = h.park(Why.FAULT)
        assertTrue(last.gaveUp && last.log && last.retryMs == null, "quá trần ⇒ thôi, có log")
        assertFalse(h.canStart(), "quá trần ⇒ không dựng nữa cho đời view này (lỗi lặp không đốt CPU — dựng đệm ~35 s trên xe)")
    }

    @Test
    fun `loi sau khi dung lai van tinh tran - khong vong lap nap-hong-nap`() {
        val h = Car3dHold()
        repeat(Car3dHold.MAX_FAULTS) { h.park(Why.FAULT); h.started() }
        assertTrue(h.park(Why.FAULT).gaveUp, "started() không xoá đếm lỗi ⇒ lỗi tất định (vd nhịp đổ số ném mỗi lượt) dừng sau trần")
    }

    @Test
    fun `vinh vien - log mot lan, khong thu lai, khong bi ly do khac de len`() {
        for (w in Why.values().filter { it.permanent }) {
            val h = Car3dHold()
            val n = h.park(w)
            assertTrue(n.log); assertNull(n.retryMs); assertFalse(h.canStart())
            assertFalse(h.park(w).log, "$w: log MỘT lần")
            val other = h.park(Why.FAULT)
            assertFalse(other.log || other.retryMs != null, "$w: đã không khả thi ⇒ lỗi sau không hẹn thử lại")
            h.started(); assertEquals(w, h.why, "$w: started() không xoá lý do vĩnh viễn")
        }
    }

    /**
     * 3.1.14 PERF-SL6-3113 — [ĐO log Seal 17/10, 3.1.11] 3/7 lượt dựng lại 3D đến sau lượt khuất 0,12–0,15 s, 2/7 sau 10–22 s; mỗi
     * lượt đơ luồng chính 1–2,3 s. Hạn nhả phải phủ cả hai nhóm; màn tắt nhả ngay (owner 14/10: không giữ RAM khi không ai nhìn).
     */
    @Test
    fun `khuat thoang qua khong nha Engine - man tat nha ngay`() {
        assertEquals(0L, Car3dHold.releaseAfterMs(screenOff = true), "màn tắt ⇒ nhả ngay")
        val grace = Car3dHold.releaseAfterMs(screenOff = false)
        assertTrue(grace >= 22_000L, "hạn $grace ms phải phủ lượt khuất 22 s đo trên xe (về HOME / đổi màn)")
        assertTrue(grace <= 60_000L, "hạn $grace ms quá dài — giữ RAM khi rời HOME lâu (owner 14/10)")
    }

    /**
     * 3.1.14 PERF-SL6-3113 R4 — trần lỗi là của TIẾN TRÌNH: view mới (dựng lại ô · đổi hồ sơ) dùng chung sổ ⇒ không nhân số lượt dựng
     * GLB nguội (~33 s CPU/lượt trên Seal). ĐỎ khi sổ đếm quay về đời view.
     */
    @Test
    fun `tran loi theo tien trinh - view moi noi tiep so cu`() {
        val shared = Car3dHold.Faults()
        val first = Car3dHold(budget = shared)
        repeat(Car3dHold.MAX_FAULTS - 1) { first.park(Why.FAULT) }
        val rebuilt = Car3dHold(budget = shared)   // ô dựng lại ⇒ view mới
        assertTrue(rebuilt.canStart(), "còn trong trần")
        rebuilt.park(Why.FAULT)
        assertEquals(Car3dHold.MAX_FAULTS, rebuilt.faults, "view mới nối tiếp đếm cũ")
        assertTrue(rebuilt.park(Why.FAULT).gaveUp, "quá trần chung ⇒ thôi")
        assertFalse(Car3dHold(budget = shared).canStart(), "view thứ ba cũng không dựng nữa")
        assertEquals(0, Car3dHold().faults, "sổ riêng (mặc định) không dính sổ chung")
    }
}
