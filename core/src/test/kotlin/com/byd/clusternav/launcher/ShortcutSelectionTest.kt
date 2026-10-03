package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * F1 · U1–U4 (spec shortcuts-autostart R1.1 · R1.2 · R1.4) — phép sửa danh sách lối tắt + hình học khối thanh nút. Tầng
 * UI (ngăn kéo chọn app, trang Cài đặt, khối thanh nút, widget `w_apps`) chỉ gọi vào đây, nên luật nằm ở bài này.
 */
class ShortcutSelectionTest {

    private val a = AppShortcut("com.a", ShortcutMode.Slot(2))
    private val b = AppShortcut("com.b", ShortcutMode.Background)
    private val c = AppShortcut("com.c", ShortcutMode.Full)

    @Test
    fun `chot o ngan keo - giu cho va kieu cu, noi moi theo thu tu cham, bo muc da bo tich`() {
        val out = ShortcutSelection.apply(listOf(a, b, c), listOf("com.e", "com.c", "com.a", "com.d"))
        assertEquals(
            listOf(a, c, AppShortcut("com.e", ShortcutMode.Full), AppShortcut("com.d", ShortcutMode.Full)), out,
            "a·c giữ chỗ + kiểu; b bỏ tích ⇒ rời; e·d mới ⇒ cuối, theo thứ tự chạm, kiểu mặc định Toàn màn",
        )
    }

    @Test
    fun `kieu mac dinh cua app moi la Toan man - kieu duy nhat khong dung cua so nao khac`() {
        assertEquals(ShortcutMode.Full, ShortcutSelection.DEFAULT_MODE)
        assertFalse(ShortcutSelection.DEFAULT_MODE.needsChannel, "chạm thử ngay sau khi chọn không được cần kênh")
    }

    @Test
    fun `chot qua tran 8 - cat phan cuoi, khong bao gio qua tran`() {
        val picked = (1..11).map { "com.p$it" }
        val out = ShortcutSelection.apply(emptyList(), picked)
        assertEquals(AppShortcutCodec.MAX, out.size)
        assertEquals(picked.take(AppShortcutCodec.MAX), out.map { it.pkg })
    }

    @Test
    fun `chot goi la hoac trung - bi loc (ten goi con di vao lenh shell o tang sau)`() {
        val out = ShortcutSelection.apply(emptyList(), listOf("com.ok", "x' ; reboot", "com.ok", ""))
        assertEquals(listOf("com.ok"), out.map { it.pkg })
    }

    @Test
    fun `doi kieu - dung goi do, khong doi gi thi tra CHINH danh sach`() {
        val list = listOf(a, b, c)
        assertEquals(listOf(a, b.copy(mode = ShortcutMode.Slot(1)), c), ShortcutSelection.setMode(list, "com.b", ShortcutMode.Slot(1)))
        assertSame(list, ShortcutSelection.setMode(list, "com.b", ShortcutMode.Background), "cùng kiểu ⇒ không ghi bền vô nghĩa")
        assertSame(list, ShortcutSelection.setMode(list, "com.zz", ShortcutMode.Full), "gói lạ ⇒ không đổi gì")
        assertEquals(
            ShortcutMode.Full, ShortcutSelection.setMode(list, "com.a", ShortcutMode.Slot(99)).first().mode,
            "ô ngoài trần ⇒ codec ép về Toàn màn (giống đường đọc)",
        )
    }

    @Test
    fun `doi cho - cung phep BarOrder, bien thi tra CHINH danh sach`() {
        val list = listOf(a, b, c)
        assertEquals(listOf(b, a, c), ShortcutSelection.move(list, "com.a", +1))
        assertSame(list, ShortcutSelection.move(list, "com.a", -1), "đầu danh sách ⇒ không dời được")
        assertSame(list, ShortcutSelection.move(list, "com.zz", +1))
    }

    @Test
    fun `chip o - bo cuc dang dung, o dang chon nam ngoai bo cuc thi van hien nhung danh dau ngoai`() {
        assertEquals(listOf(1 to true, 2 to true, 3 to true), ShortcutSelection.slotChips(3, ShortcutMode.Full))
        assertEquals(
            listOf(1 to true, 2 to true, 5 to false), ShortcutSelection.slotChips(2, ShortcutMode.Slot(5)),
            "lựa chọn hiện tại không được bị giấu đi khi đổi sang bố cục ít ô hơn",
        )
        assertEquals(listOf(1 to true), ShortcutSelection.slotChips(1, ShortcutMode.Slot(1)))
        assertEquals(AppShortcutCodec.MAX_SLOT, ShortcutSelection.slotChips(99, ShortcutMode.Full).size, "trần ô")
    }

    /** R1.2 / E7 — khối có ĐÚNG n khe cho n app (bề dài dp = khe × số khe + lề, tính ở tầng vẽ); rỗng vẫn một khe. */
    @Test
    fun `so khe cua khoi thanh nut - dong theo so app, ron mot khe, khong qua tran`() {
        mapOf(1 to 1, 4 to 4, 8 to 8).forEach { (n, cells) -> assertEquals(cells, ShortcutStrip.cells(n), "$n app") }
        assertEquals(1, ShortcutStrip.cells(0), "rỗng = một khe (ô chọn lối tắt)")
        assertEquals(AppShortcutCodec.MAX, ShortcutStrip.cells(20), "không dài quá trần")
    }

    // Bài "lưới widget tối đa bốn cột" (`ShortcutStrip.gridCols`) đã GỠ cùng hàm của nó — 2.87 R-SI1: số cột nay do
    // phép khớp theo khung thật quyết (`ShortcutGridFit`); "8 app khung 2:1 = hai hàng bốn" sống tiếp ở ShortcutGridFitTest.

    @Test
    fun `kieu can kenh - O n va Chay ngam, Toan man thi khong`() {
        assertTrue(ShortcutMode.Slot(1).needsChannel)
        assertTrue(ShortcutMode.Background.needsChannel)
        assertFalse(ShortcutMode.Full.needsChannel)
    }

    @Test
    fun `ma kieu - mot bang ma cho codec va chip Cai dat, khu hoi`() {
        listOf(ShortcutMode.Slot(1), ShortcutMode.Slot(6), ShortcutMode.Full, ShortcutMode.Background).forEach {
            assertEquals(it, AppShortcutCodec.modeOf(AppShortcutCodec.modeCode(it)))
        }
        assertEquals(ShortcutMode.Full, AppShortcutCodec.modeOf("S9"), "ô ngoài trần ⇒ Toàn màn")
        assertEquals(ShortcutMode.Full, AppShortcutCodec.modeOf("?"))
    }
}
