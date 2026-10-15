package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * FAN-STEP 3.1.7 — khoá lỗi owner 15/10 *"từ 2 lăn lên 3 vẫn hiện 2, từ 1 lăn xuống thì hiện AUTO ngay"*.
 *
 * Chuỗi dựng từ log xe 14/10 16:54:29 (`docs/diagnostics/fan-step-2026-10-15.md` §2): ô đang hiện **2** (tay) · con lăn
 * `KeyCtl ctl:fan:+1` ⇒ `KachiCtl id=fan args=[3] before=2` ⇒ đường phím ghi bảng dùng chung = 3 (không vẽ ô) · xe đọc lại **3**
 * (`ctl-after … after=3`). Luật cũ so số đọc với BẢNG (3 == 3 ⇒ không vẽ) ⇒ kẹt "2"; luật mới so với HÌNH.
 */
class StepTileSyncTest {

    /** Luật trước 3.1.7 (`v != state.value(def) || a != auto`) — giữ lại trong test để thấy vì sao nó kẹt. */
    private fun oldRule(table: Int, auto: Boolean?, read: Int, readAuto: Boolean?) = read != table || readAuto != auto

    @Test
    fun `lan len 2 to 3 bang phim - xe doc 3 thi o phai ve lai`() {
        val drawn = 2; val table = 3   // phím đã ghi bảng 3, ô vẫn vẽ 2
        assertFalse(oldRule(table, false, read = 3, readAuto = false), "luật cũ: bảng = số đọc ⇒ không vẽ ⇒ kẹt 2 (đúng lỗi owner)")
        assertTrue(StepTileSync.needsRedraw(drawn, false, read = 3, readAuto = false), "hình 2 ≠ xe 3 ⇒ vẽ lại")
    }

    @Test
    fun `lan xuong 3 to 2 cung khong duoc ket`() {
        assertTrue(StepTileSync.needsRedraw(3, false, read = 2, readAuto = false))
    }

    @Test
    fun `1 xuong AUTO van hien ngay`() {
        assertTrue(StepTileSync.needsRedraw(1, false, read = 1, readAuto = true), "cờ AUTO đổi ⇒ vẽ lại (hành vi owner thấy đúng)")
    }

    @Test
    fun `hinh khop xe thi khong ve thua`() {
        assertFalse(StepTileSync.needsRedraw(3, false, read = 3, readAuto = false))
        assertFalse(StepTileSync.needsRedraw(2, true, read = 2, readAuto = true))
        assertFalse(StepTileSync.needsRedraw(22, null, read = 22, readAuto = null), "nút không có autoId (nhiệt) y như cũ")
    }

    @Test
    fun `cham o lac quan roi xe xac nhan thi khong ve thua`() {
        // Chạm "+" ⇒ ô tự vẽ 3 (drawn = 3); xe xác nhận 3 ⇒ không vẽ lại.
        assertFalse(StepTileSync.needsRedraw(3, false, read = 3, readAuto = false))
    }
}
