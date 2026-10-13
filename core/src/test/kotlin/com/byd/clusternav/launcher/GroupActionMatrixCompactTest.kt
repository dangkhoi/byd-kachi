package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * 3.01 R22·B — HÌNH XE GỌN cho ô nhỏ ([GroupActionMatrix.compactForm]). Vùng chạm: [CarDotTouchTest] (3.01 TAPFIX).
 *
 * Khoá lỗi [ĐO] 13/10: ô Kính 2×3 của owner (vùng bảng 267×312 px @1,5×) lùi về danh sách gọn và BỎ `Kính SP` + hàng
 * `4 kính` vì thiếu cao cho đích chạm 48 dp. Ở cỡ đó hình xe gọn phải VỪA.
 * Số đo lấy từ máy ảo (1920×1080 @240 dpi): đích chạm 72 px, khe 6 px. 3.01 R22·C: công tắc chế độ gỡ, chân 2 nút, cốp
 * thành dải chạm ở đuôi xe ([GroupActionMatrix.CompactMetrics.ends]). 3.01 DOT (owner 13/10 "bỏ số luôn em"): không còn
 * cột nhãn `%` cạnh xe ⇒ bỏ `labelPx` khỏi số đo.
 */
class GroupActionMatrixCompactTest {

    private val m = GroupActionMatrix.CompactMetrics(
        touchPx = 72, gap = 6, minCarPx = 108, bars = 2, barPx = 72, ends = 1,
    )
    private val windows = GroupActionMatrix.of(GroupBoard.of(CapabilityGroups.WINDOWS, CarStatus()))!!

    @Test
    fun `chan hai nut mo tat ca - dong tat ca theo muc du lieu, coc len hinh xe`() {
        // 3.01 R22·C (owner 13/10 "thêm 1 icon mở all, 1 icon đóng all là xong… thêm cả cốp vào đó luôn"): chân đúng HAI việc
        // chọn theo GroupActionCell.level (dữ liệu ControlDef.verbLevel) — không tên nút trong mã vẽ.
        assertEquals("windows_all", windows.openAll?.id, "mở tất cả = việc cả xe mức cao nhất")
        assertEquals(100, windows.openAll?.level)
        assertEquals("windows_close_all", windows.closeAll?.id, "đóng tất cả = việc mức 0")
        assertEquals(listOf(CarPart.TAILGATE to "trunk"), windows.onCar.map { it.first to it.second.id }, "cốp = chấm ở đuôi xe")
        windows.placed.forEach { r -> assertEquals(listOf(100, 50), r.slots.map { it?.level }, r.title) }
    }

    @Test
    fun `o Kinh 2x3 cua owner vua hinh xe gon dung - khong con phai bo kinh nao`() {
        assertEquals(GroupActionMatrix.CompactForm.STACK, GroupActionMatrix.compactForm(267, 312, m))
        val (w, h) = GroupActionMatrix.compactCarArea(GroupActionMatrix.CompactForm.STACK, 267, 312, m)
        assertEquals(267 to 234, w to h, "R22·C: không còn hàng công tắc chế độ ⇒ vùng xe = khung − một hàng chân")
        // 3.01 TAPFIX: vùng chạm chữ nhật đã gỡ — chủ cú chạm = chấm gần nhất trên tâm vẽ thật (CarDotTouchTest).
    }

    @Test
    fun `o rong thap 6x2 khong du dai cop thi coc xuong chan, o cao 2x6 xep dung, o qua nho thi lui danh sach`() {
        assertNull(GroupActionMatrix.compactForm(870, 170, m), "6×2: cao 170 < 3 đích chạm (2 hàng cửa + dải cốp)")
        assertEquals(
            GroupActionMatrix.CompactForm.ROW, GroupActionMatrix.compactForm(870, 170, m.copy(ends = 0, bars = 3)),
            "6×2: cốp xuống chân thành nút thứ ba ⇒ xe xếp ngang vẫn vừa",
        )
        assertEquals(GroupActionMatrix.CompactForm.STACK, GroupActionMatrix.compactForm(267, 700, m))
        assertNull(GroupActionMatrix.compactForm(200, 200, m), "vùng xe còn 122 px cao ⇒ không đủ 3 đích chạm ⇒ danh sách")
        assertNull(GroupActionMatrix.compactForm(140, 600, m), "hẹp: chia đôi bề ngang < đích chạm")
        assertEquals(
            GroupActionMatrix.CompactForm.STACK,
            GroupActionMatrix.compactForm(267, 156, m.copy(bars = 0, ends = 0)),
            "không chân, không dải ⇒ cả khung là vùng xe",
        )
    }
}
