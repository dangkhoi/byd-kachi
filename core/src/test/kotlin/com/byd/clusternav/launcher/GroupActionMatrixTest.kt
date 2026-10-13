package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 3.01 R22 — bảng VẬT × VIỆC ([GroupActionMatrix]) dựng từ danh sách nút THẬT của [CapabilityGroups].
 *
 * Khoá ảnh owner 13/10 *"rối mà"*: nhóm *Kính* = 11 nút phẳng ⇒ phải ra 5 hàng (4 kính + 4 kính chung) × 2 cột
 * (`100%` · `50%`) + chân `Đóng cả cụm`, và số của từng kính GỘP vào hàng của nó. Nhóm không có dạng đó ⇒ `null`.
 */
class GroupActionMatrixTest {

    private fun model(g: CapabilityGroup, s: CarStatus = CarStatus()) = GroupBoard.of(g, s)

    @Test
    fun `nhom Kinh ra 5 hang x 2 cot va chan Dong ca cum`() {
        val mx = GroupActionMatrix.of(model(CapabilityGroups.WINDOWS))!!
        assertEquals(listOf("Mở hẳn", "Hé"), mx.verbs, "cột = động từ lặp ở ≥2 vật, theo thứ tự khai (CHỮ — owner bác số 100%/50%)")
        assertEquals(
            listOf("win_lf", "win_rf", "win_lr", "win_rr", "windows_all"),
            mx.rows.map { it.slots[0]?.id },
        )
        assertEquals(
            listOf("win_half_lf", "win_half_rf", "win_half_lr", "win_half_rr", "win_half_all"),
            mx.rows.map { it.slots[1]?.id },
        )
        assertEquals(listOf("windows_close_all", "trunk"), mx.footer.map { it.id }, "nút lẻ (không động từ) xuống chân bảng")
        // Mọi nút của nhóm có MỘT chỗ — không mất, không lặp.
        val placed = mx.rows.flatMap { r -> r.slots.mapNotNull { it?.id } } + mx.footer.map { it.id }
        assertEquals(CapabilityGroups.WINDOWS.writes.sorted(), placed.sorted())
    }

    @Test
    fun `so cua tung kinh gop vao hang cua no, hang chung khong co so`() {
        val s = CarStatus(body = CarStatus.Body(windowLfPct = 0, windowRfPct = 35))
        val mx = GroupActionMatrix.of(model(CapabilityGroups.WINDOWS, s))!!
        assertEquals(listOf("window_lf", "window_rf", "window_lr", "window_rr", null), mx.rows.map { it.cell?.id })
        assertEquals(setOf("window_lf", "window_rf", "window_lr", "window_rr"), mx.mergedCellIds)
        assertTrue(mx.hasValues)
        // Nhãn hàng = nhãn ô XEM (một dòng, ngắn); hàng chung lấy nhãn nút cột đầu.
        assertEquals(model(CapabilityGroups.WINDOWS, s).cells[1].label, mx.rows[1].title)
        assertEquals(ControlRegistry.byId("windows_all")!!.displayShortLabel, mx.rows[4].title)
        assertTrue(mx.rows[1].cell!!.available)
    }

    @Test
    fun `nhom khong co nut khai dong tu thi khong dung bang - Den, Cua, Lop, Khi hau`() {
        listOf(CapabilityGroups.LIGHTS, CapabilityGroups.DOORS, CapabilityGroups.TYRES).forEach { g ->
            assertNull(GroupActionMatrix.of(model(g)), "${g.id}: không có cấu trúc vật × việc ⇒ đường cũ")
        }
        // Quét cả bộ: chỉ nhóm có ≥2 động từ lặp ở ≥2 vật mới thành bảng (hôm nay đúng một nhóm).
        val with = CapabilityGroups.ALL.filter { GroupActionMatrix.of(model(it)) != null }.map { it.id }
        assertEquals(listOf("g_windows"), with)
    }

    @Test
    fun `mot dong tu chi mot vat co thi khong thanh cot`() {
        val a = GroupActionCell("a1", "A", "ic-a", false, verb = "X")
        val b = GroupActionCell("b1", "B", "ic-b", false, verb = "X")
        val lone = GroupActionCell("a2", "A2", "ic-a", false, verb = "Y")
        val m = GroupBoardModel("g", "G", "", WidgetShape.STRIP, emptyList(), listOf(a, b, lone))
        assertNull(GroupActionMatrix.of(m), "một cột (X) chưa phải bảng")
        val b2 = GroupActionCell("b2", "B2", "ic-b", false, verb = "Y")
        val mx = GroupActionMatrix.of(m.copy(actions = listOf(a, b, lone, b2)))!!
        assertEquals(listOf("X", "Y"), mx.verbs)
        assertTrue(mx.footer.isEmpty())
    }

    // ── plan: thứ tự nhường ───────────────────────────────────────────────────────────────────────────────

    /** Số đo xấp xỉ ô 1,5× (240 dpi) — nhãn `Kính lái` ~75 px, số `100 %` ~60 px, nút 72 px (48 dp). */
    private val m = GroupActionMatrix.Metrics(
        labelPx = 75, valuePx = 60, labelLine = 27, valueLine = 33,
        buttonPx = 72, footerPx = 140, touchPx = 72, gap = 9, blockGap = 24,
    )

    @Test
    fun `o rong du cho thi so canh nhan, du cot, mot khoi`() {
        val p = GroupActionMatrix.plan(5, 2, footer = true, widthPx = 600, heightPx = 600, m = m)
        assertFalse(p.stacked); assertEquals(2, p.cols); assertEquals(1, p.blocks); assertEquals(5, p.rowsShown)
        assertTrue(p.fits)
    }

    @Test
    fun `o hep - so xuong duoi nhan truoc khi bo cot`() {
        // side = 75+9+60 + 2*(72+9) = 306 > 240; stacked = 75 + 162 = 237 ≤ 240.
        val p = GroupActionMatrix.plan(5, 2, footer = true, widthPx = 240, heightPx = null, m = m)
        assertTrue(p.stacked); assertEquals(2, p.cols); assertTrue(p.fits)
    }

    @Test
    fun `o rong ma thap - chia khoi canh nhau thay vi bo hang`() {
        // 6 ô (5 hàng + chân), cao 160 px = 2 hàng ⇒ cần 3 khối.
        val p = GroupActionMatrix.plan(5, 2, footer = true, widthPx = 900, heightPx = 160, m = m)
        assertEquals(3, p.blocks); assertEquals(2, p.perBlock); assertEquals(5, p.rowsShown); assertTrue(p.fits)
    }

    @Test
    fun `o hep va thap - bo hang tu cuoi, chan giu, nut khong bao gio xuong dong`() {
        val p = GroupActionMatrix.plan(5, 2, footer = true, widthPx = 240, heightPx = 300, m = m)
        assertEquals(2, p.cols, "bề ngang đủ ⇒ giữ đủ việc")
        assertEquals(4, 300 / p.rowPx); assertEquals(3, p.rowsShown, "4 chỗ = 3 hàng vật + chân")
        assertFalse(p.fits)
    }

    @Test
    fun `cuc hep - bot cot tu phai roi moi kep nhan`() {
        val p = GroupActionMatrix.plan(5, 2, footer = false, widthPx = 160, heightPx = null, m = m)
        assertEquals(1, p.cols, "75 + 81 = 156 ≤ 160 ⇒ giữ cột việc chính")
        val q = GroupActionMatrix.plan(5, 2, footer = false, widthPx = 120, heightPx = null, m = m)
        assertEquals(1, q.cols); assertEquals(120 - 81, q.labelPx, "nhãn kẹp, nút giữ đủ bề rộng chạm")
    }

    // ── bảng hình xe ───────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `bon kinh dat dung bon cua tren hinh xe, hang 4 kinh la viec ca xe`() {
        val mx = GroupActionMatrix.of(model(CapabilityGroups.WINDOWS))!!
        assertEquals(
            listOf(CarPart.DOOR_LF, CarPart.DOOR_RF, CarPart.DOOR_LR, CarPart.DOOR_RR, null),
            mx.rows.map { it.place },
        )
        assertTrue(mx.boardable)
        assertEquals(listOf("windows_all"), mx.whole.map { it.slots[0]?.id })
        assertNull(GroupActionMatrix.placeOf("ic-car-top-window-all"))
        // 3.01 R22·C — hậu tố là KHOÁ VẬT ([CarPart.key]): rèm có vị trí, nhưng nằm GIỮA thân ⇒ không lên hình xe gọn
        // (dải chạm sẽ đè cửa); cốp ở đuôi ⇒ lên.
        assertEquals(CarPart.SUNSHADE, GroupActionMatrix.placeOf("ic-car-top-sunshade"))
        assertFalse(GroupActionMatrix.atCarEnd(CarPart.SUNSHADE))
        assertEquals(CarPart.TAILGATE, GroupActionMatrix.placeOf("ic-car-top-trunk"))
        assertTrue(GroupActionMatrix.atCarEnd(CarPart.TAILGATE))
        assertFalse(GroupActionMatrix.atCarEnd(CarPart.DOOR_RR))
        assertNull(GroupActionMatrix.placeOf("ic-car-top-window-all"))
    }

    @Test
    fun `hai vat cung mot goc thi khong ve len hinh xe`() {
        val a = GroupActionCell("a1", "A", "ic-x-lf", false, verb = "X")
        val a2 = GroupActionCell("a2", "A", "ic-x-lf", false, verb = "Y")
        val b = GroupActionCell("b1", "B", "ic-y-lf", false, verb = "X")
        val b2 = GroupActionCell("b2", "B", "ic-y-lf", false, verb = "Y")
        val mx = GroupActionMatrix.of(GroupBoardModel("g", "G", "", WidgetShape.STRIP, emptyList(), listOf(a, a2, b, b2)))!!
        assertFalse(mx.boardable, "hai thẻ đè một cửa ⇒ hình xe nói sai ⇒ lùi về danh sách")
    }
}
