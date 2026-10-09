package com.byd.clusternav.launcher.escape

import com.byd.clusternav.launcher.SlotLiveness
import com.byd.clusternav.launcher.escape.EscapeCoverPlan.Kind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 2.98 · R7 — phản hồi owner 09/10 (máy ảo, Waze + VietMap):
 *  #1 nhận chậm 10–14 s ⇒ mốc đo có trần sau lượt mở app vào ô ([SlotLiveness.LAUNCH_BURST_MS]);
 *  #2 "thanh xám" che đầu app ⇒ khung task cao hơn ô một thanh tiêu đề, hệ dời xuống dưới đỉnh ổn định ⇒ học + trừ ([EscapeFit]);
 *     phần còn lọt vào ô là GAP (nền sau ô, không khay);
 *  #3 "bo 2 góc dưới hỏng" ⇒ hai dải CORNER (trên: đỉnh nội dung app · dưới: đáy ô).
 * Số đo: [ĐO máy ảo 09/10 14:45] `mStable=[0,36][1920,1080]`; `am task resize 366 19 25 1901 985` ⇒ hệ đặt `[19,36][1901,996]`
 * (dời 11, giữ cỡ — khớp AOSP Q `TaskRecord.java:2242-2246`). Xe: `StatusBar [0,0][1920,84]` [ĐO xe 14/09], `status_bar_height` 56dp.
 */
class EscapeFitTest {

    private val slot = PxRect(19, 89, 1901, 985)

    @Test
    fun `khong lop che thi khung task dung khung o`() {
        assertEquals(slot, EscapeFit.taskRect(slot, 0, null))
        assertEquals(slot, EscapeFit.taskRect(slot, 0, 36))
    }

    @Test
    fun `co lop che thi day thanh tieu de len tren o, khong vuot dinh on dinh da hoc`() {
        assertEquals(PxRect(19, 24, 1901, 985), EscapeFit.taskRect(slot, 65, null))
        assertEquals(PxRect(19, 36, 1901, 985), EscapeFit.taskRect(slot, 65, 36), "máy ảo")
        assertEquals(PxRect(19, 84, 1901, 985), EscapeFit.taskRect(slot, 65, 84), "xe [SUY] đỉnh ổn định 84")
        assertEquals(slot, EscapeFit.taskRect(slot, 65, 120), "đỉnh ổn định thấp hơn đỉnh ô ⇒ không kéo task xuống dưới đỉnh ô")
        // Soát Pass 9 [P3]: ô sát mép màn (bố cục ẩn thanh trên), chưa học ⇒ sàn 0, không xin đỉnh âm.
        assertEquals(PxRect(19, 0, 1901, 985), EscapeFit.taskRect(PxRect(19, 40, 1901, 985), 65, null))
    }

    @Test
    fun `hoc dinh on dinh chi khi he doi khung xuong giu co`() {
        // [ĐO máy ảo] xin [19,25][1901,985] ⇒ hệ đặt [19,36][1901,996].
        assertEquals(36, EscapeFit.learnedMinTop(PxRect(19, 25, 1901, 985), PxRect(19, 36, 1901, 996)))
        assertNull(EscapeFit.learnedMinTop(slot, slot), "khớp ⇒ không học")
        assertNull(EscapeFit.learnedMinTop(PxRect(19, 25, 1901, 985), PxRect(19, 36, 1901, 985)), "đổi cỡ (kẹp) ⇒ không phải dời")
        assertNull(EscapeFit.learnedMinTop(PxRect(19, 25, 1901, 985), PxRect(40, 36, 1922, 996)), "dời ngang ⇒ không học")
        assertNull(EscapeFit.learnedMinTop(PxRect(19, 25, 1901, 985), null))
    }

    @Test
    fun `vung che tren xe - tieu de tren o, GAP nen sau o, hai dai goc`() {
        val screen = PxRect(0, 0, 1920, 1080)
        val bars = EscapeCoverPlan.Bars(PxRect(0, 0, 1920, 84), PxRect(0, 990, 1920, 1080))
        val c = EscapeCoverPlan.covers(PxRect(19, 84, 1901, 985), bars, 240, screen, slot, 0, 24)
        assertEquals(listOf(Kind.CAPTION, Kind.GAP, Kind.STATUS, Kind.NAV, Kind.CORNER, Kind.CORNER, Kind.EDGE, Kind.EDGE, Kind.EDGE, Kind.EDGE), c.map { it.kind })
        assertEquals(PxRect(19, 84, 1901, 89), c[0].rect, "phần thanh tiêu đề trên đỉnh ô: gương đầu màn nhà")
        assertEquals(PxRect(19, 89, 1901, 149), c[1].rect, "~60 px lọt vào ô: nền sau ô, không khay xám")
        assertEquals(0, c[1].slot)
        val frame = PxRect(19, 149, 1901, 985)
        assertEquals(listOf(PxRect(19, 149, 1901, 173), PxRect(19, 961, 1901, 985)), c.filter { it.kind == Kind.CORNER }.map { it.rect })
        assertTrue(c.filter { it.kind == Kind.CORNER }.all { it.frame == frame && it.radiusPx == 24 })
    }

    @Test
    fun `vung che may ao - GAP chi con 12 px, khong lop che ma khung task = o thi khong CAPTION`() {
        val c = EscapeCoverPlan.covers(PxRect(19, 36, 1901, 985), EscapeCoverPlan.Bars(PxRect(0, 0, 1920, 36), null), 240, PxRect(0, 0, 1920, 1080), slot, 0, 24)
        assertEquals(PxRect(19, 36, 1901, 89), c.first { it.kind == Kind.CAPTION }.rect)
        assertEquals(PxRect(19, 89, 1901, 101), c.first { it.kind == Kind.GAP }.rect)
        // Dấu cũ (khung task = ô): không còn vùng tiêu đề trên ô; toàn bộ tiêu đề là GAP.
        val old = EscapeCoverPlan.covers(slot, EscapeCoverPlan.Bars.NONE, 240, PxRect(0, 0, 1920, 1080), slot, 0, 24)
        assertTrue(old.none { it.kind == Kind.CAPTION })
        assertEquals(PxRect(19, 89, 1901, 154), old.first { it.kind == Kind.GAP }.rect)
        assertEquals(0, EscapeCoverPlan.frameRadius(PxRect(0, 0, 10, 10), 0))
        assertEquals(5, EscapeCoverPlan.frameRadius(PxRect(0, 0, 10, 100), 24), "kẹp min(w,h)/2 như SlotFrameShape")
    }

    @Test
    fun `moc do sau luot mo app vao o co tran va du som`() {
        val b = SlotLiveness.LAUNCH_BURST_MS
        assertTrue(b.size <= 6, "trần số lượt đọc mỗi lần mở")
        assertTrue(b.zipWithNext().all { (x, y) -> y > x })
        assertTrue(b[SlotLiveness.ELSEWHERE_SWEEPS - 1] <= 2_000L, "hai mốc đầu (đủ kết luận 'ở chỗ khác') trong 2 s")
        assertTrue(b.last() <= 10_000L)
    }
}
