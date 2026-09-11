package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

/**
 * Khoá **phép kẹp của trình vẽ bố cục** và **trần ô ở giá trị MẶC ĐỊNH**.
 *
 * ## Hai lỗ hổng mà tệp này bịt (lượt soát độc lập 2026-09-11 tìm ra)
 *  1. `GridEditorView` có **0 test 0 guard**, dù chính nó từng chứa một lỗi **SẬP**: `coerceIn` ném lỗi khi trần <
 *     sàn (bố cục hỏng ⇒ `COLS - cols` âm) nên *kéo một khung là launcher chết*. Bản vá lúc đó không có bài nào
 *     khoá ⇒ sửa nhầm lần sau là sập lại y như cũ. Logic nay ở [GridEditorLogic] (:core, thuần) nên kiểm được.
 *  2. **Trần ô**: mọi bài về trần đều truyền `cap` **tường minh** (`cap = 4`), nên đổi giá trị MẶC ĐỊNH thành 99
 *     (⇒ bố cục 7 khung được nhận trên 6 ô = hình dạng P-bug2) mà **không bài nào đỏ**.
 */
class GridEditorLogicTest {

    private fun f(col: Int, row: Int, cols: Int, rows: Int) = GridFrame(col, row, cols, rows)

    // ── Di khung ─────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `di khung binh thuong thi bam dung o duoc yeu cau`() {
        assertEquals(f(3, 2, 4, 3), GridEditorLogic.move(f(0, 0, 4, 3), 3, 2))
    }

    @Test
    fun `di khung ra ngoai luoi thi bi kep vao trong`() {
        val moved = GridEditorLogic.move(f(0, 0, 4, 3), 99, 99)
        assertEquals(WorkspaceGrid.COLS - 4, moved.col, "không được vượt mép phải")
        assertEquals(WorkspaceGrid.ROWS - 3, moved.row, "không được vượt mép dưới")
        val back = GridEditorLogic.move(f(5, 4, 4, 3), -7, -7)
        assertEquals(0, back.col)
        assertEquals(0, back.row)
    }

    @Test
    fun `khung RONG HON luoi thi khong sap - chi la khong di duoc`() {
        // Đây là ca đã từng làm sập: trần kẹp = COLS - cols < 0.
        val huge = f(0, 0, WorkspaceGrid.COLS + 5, WorkspaceGrid.ROWS + 5)
        val moved = assertDoesNotThrow { GridEditorLogic.move(huge, 4, 4) }
        assertEquals(0, moved.col, "khung rộng hơn lưới ⇒ kẹp về 0, KHÔNG ném lỗi")
        assertEquals(0, moved.row)
    }

    // ── Đổi cỡ khung ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun `doi co khung ton trong co toi thieu`() {
        val small = GridEditorLogic.resize(f(0, 0, 6, 4), 0, 0)
        assertEquals(WorkspaceGrid.MIN_COLS, small.cols)
        assertEquals(WorkspaceGrid.MIN_ROWS, small.rows)
    }

    @Test
    fun `doi co khung khong duoc vuot mep luoi`() {
        val big = GridEditorLogic.resize(f(8, 4, 2, 1), 99, 99)
        assertEquals(WorkspaceGrid.COLS - 8, big.cols)
        assertEquals(WorkspaceGrid.ROWS - 4, big.rows)
    }

    @Test
    fun `khung co goc NGOAI luoi thi khong sap`() {
        // `COLS - col` nhỏ hơn cỡ tối thiểu ⇒ trần < sàn ⇒ đúng ca ném lỗi cũ.
        val outside = f(WorkspaceGrid.COLS + 3, WorkspaceGrid.ROWS + 3, 2, 1)
        val r = assertDoesNotThrow { GridEditorLogic.resize(outside, 5, 5) }
        assertEquals(WorkspaceGrid.MIN_COLS, r.cols, "kẹp về cỡ tối thiểu, KHÔNG ném lỗi")
        assertEquals(WorkspaceGrid.MIN_ROWS, r.rows)
    }

    @Test
    fun `moi to hop keo tren luoi deu KHONG nem loi`() {
        // Quét thô: mọi khung (kể cả hỏng) × mọi đích kéo → không được có ngoại lệ nào.
        var n = 0
        for (col in -2..WorkspaceGrid.COLS + 2) for (row in -2..WorkspaceGrid.ROWS + 2) {
            for (cols in 0..WorkspaceGrid.COLS + 2 step 3) for (rows in 0..WorkspaceGrid.ROWS + 2 step 2) {
                val frame = f(col, row, cols, rows)
                assertDoesNotThrow { GridEditorLogic.move(frame, col + 3, row + 2) }
                assertDoesNotThrow { GridEditorLogic.resize(frame, cols + 3, rows + 2) }
                n++
            }
        }
        assertTrue(n > 500, "phải quét đủ rộng để có ý nghĩa, đã quét $n tổ hợp")
    }

    // ── Trần ô ở giá trị MẶC ĐỊNH (không truyền tường minh) ─────────────────────────────────────

    @Test
    fun `bo cuc nhieu khung hon TRAN MAC DINH thi bi bo qua`() {
        // ⚠ KHÔNG truyền `cap` — đó chính là điểm mù: mọi bài cũ đều truyền tường minh nên đổi mặc định không ai đỏ.
        val frames = (0 until WorkspaceState.SLOT_CAP + 1).map { f(it % WorkspaceGrid.COLS, 0, 2, 1) }
        val tooMany = GridLayout(frames)
        assertEquals(
            WorkspaceState.SLOT_CAP + 1, tooMany.frames.size,
            "tiền đề: bố cục này nhiều khung hơn trần ô",
        )
        assertTrue(
            !EffectiveLayout.usable(tooMany),
            "nhiều khung hơn TRẦN Ô ⇒ phải bị bỏ qua (lùi về bố cục sẵn), không thì có khung không bao giờ dựng",
        )
        assertEquals(
            LayoutPreset.QUAD.slotCount, EffectiveLayout.slotCount(LayoutPreset.QUAD, tooMany),
            "bị bỏ qua thì số ô phải là của bố cục sẵn",
        )
        assertTrue(
            EffectiveLayout.ignoredReason(tooMany)?.isNotBlank() == true,
            "phải NÓI lý do bỏ qua, không im lặng",
        )
    }

    @Test
    fun `bo cuc dung bang TRAN MAC DINH thi dung duoc`() {
        val frames = (0 until WorkspaceState.SLOT_CAP).map { f((it * 2) % WorkspaceGrid.COLS, it / 6, 2, 1) }
        val exact = GridLayout(frames)
        assertTrue(EffectiveLayout.usable(exact), "đúng bằng trần ô thì phải dùng được: ${exact.frames}")
        assertEquals(WorkspaceState.SLOT_CAP, EffectiveLayout.slotCount(LayoutPreset.QUAD, exact))
    }
}
