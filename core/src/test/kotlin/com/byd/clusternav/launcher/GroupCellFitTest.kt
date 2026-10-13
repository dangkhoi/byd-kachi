package com.byd.clusternav.launcher

import com.byd.clusternav.launcher.GroupCellFit.Form
import com.byd.clusternav.launcher.GroupCellFit.Need
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 3.01 R20 — ô con của ô NHÓM chọn cột/dạng theo bề rộng đo được ([GroupCellFit]).
 *
 * Số đo gốc [ĐO uiautomator kachi_play 13/10, tiếng Việt, mật độ 1,5 — `docs/diagnostics/widget-fit-301-2026-10-13.md`]:
 *  - *Chuyến đi* ở khung 2×3: hộp nội dung **267 px**; hàng số phụ chia cố định 3 cột ⇒ nhãn `Thời gian chuyến` **38 px**
 *    (cao 52 = 2 dòng, vỡ `Th/ời…`), giá trị `—` 21×39;
 *  - nhãn 13,5sp 2 dòng cao **52 px**, giá trị 19sp đậm cao **39 px** ⇒ [metrics] dưới (1 dòng nhãn ≈ 27 px theo phông).
 * Bề rộng từ [SUY] Roboto 20,25 px ≈ 0,5 em/ký tự — bài khoá LUẬT, không khoá phông.
 */
class GroupCellFitTest {

    private val m = GroupCellFit.Metrics(
        labelLine = 27, labelLine2 = 51, valueLine = 38, icon = 24, padX = 6, padY = 6, inner = 6, margin = 6,
    )

    /** Nhu cầu từ các bề rộng TỪ (px) của nhãn + bề rộng giá trị. */
    private fun need(words: List<Int>, value: Int = 30, space: Int = 5) =
        Need(words.sum() + space * (words.size - 1) + 1, GroupCellFit.twoLineWidth(words, space) + 1, value)

    // Chuyến đi — số phụ: Thời gian chuyến · Tiêu thụ 50km · Odo tổng
    private val tripRest = listOf(need(listOf(41, 40, 64)), need(listOf(38, 32, 55)), need(listOf(38, 52)))

    @Test
    fun `hai dong chi ngat o dau cach, khong bao gio hep hon tu dai nhat`() {
        assertEquals(86, GroupCellFit.twoLineWidth(listOf(41, 40, 64), 5), "Thời gian / chuyến")
        assertEquals(64, GroupCellFit.twoLineWidth(listOf(64), 5), "một từ ⇒ chính nó, không ngắt giữa từ")
        assertEquals(0, GroupCellFit.twoLineWidth(emptyList(), 5))
        assertTrue(GroupCellFit.twoLineWidth(listOf(10, 90, 10), 5) >= 90, "≥ từ dài nhất")
    }

    /**
     * Ca owner (ô 3, 267 px): bản cũ 3 cột ⇒ ô con 77 px, nhãn vỡ. Nay: không dạng nào vừa 3 cột ⇒ 1 cột, nhãn·số MỘT dòng
     * (danh sách gọn), cả ba mục còn hiện, mọi ô con đủ rộng cho chữ thật cần.
     *
     * RED-PROOF: thay vòng `for (cols in minOf(shown, maxCols) downTo 1)` bằng `val cols = minOf(shown, maxCols)` (số cột
     * cố định — đúng bệnh cũ) ⇒ bài này đỏ (không cột nào vừa ⇒ rơi xuống nhánh `fits = false`).
     */
    @Test
    fun `chuyen di 267 px - mot cot nhan so mot dong, khong vo chu`() {
        val p = GroupCellFit.plan(tripRest, 267, null, m, maxCols = 3, icons = false)
        assertTrue(p.fits)
        assertEquals(3, p.shown, "không bỏ mục khi không bị ép cao")
        assertEquals(1, p.cols)
        assertEquals(Form.SIDE, p.option.form)
        assertEquals(1, p.option.lines)
        val cellW = 267 / p.cols - 2 * m.margin
        tripRest.forEach { assertTrue(GroupCellFit.cellWidth(it, p.option, m) <= cellW) }
    }

    @Test
    fun `o rong giu tran cot cu - khong doi bo cuc o lon`() {
        val p = GroupCellFit.plan(tripRest, 1200, null, m, maxCols = 3, icons = false)
        assertEquals(3, p.cols)
        assertEquals(Form.SIDE, p.option.form)
        assertEquals(1, p.option.lines)
    }

    @Test
    fun `bi ep cao thi bo muc CUOI, khong cat chu`() {
        val five = tripRest + listOf(need(listOf(50, 40)), need(listOf(70, 50, 60)))
        val free = GroupCellFit.plan(five, 267, null, m, 3, false)
        assertEquals(5, free.shown)
        val tight = GroupCellFit.plan(five, 267, free.heightPx - 1, m, 3, false)
        assertTrue(tight.shown < 5, "không vừa ⇒ bớt mục")
        assertTrue(tight.heightPx <= free.heightPx - 1)
        assertTrue(tight.fits)
    }

    @Test
    fun `gia tri luon du cho - nhan nhuong truoc`() {
        val wide = listOf(need(listOf(30), value = 200))
        val p = GroupCellFit.plan(wide, 230, null, m, 3, false)
        val cellW = 230 / p.cols - 2 * m.margin
        assertTrue(GroupCellFit.cellWidth(wide[0], p.option, m) <= cellW, "giá trị 200 px phải vừa ô con")
        assertEquals(Form.STACK, p.option.form, "ngang không đủ (30+6+200+12) ⇒ dọc")
    }

    @Test
    fun `dai STRIP co icon - hep thi bo icon truoc khi bo muc`() {
        val lights = List(9) { need(listOf(30, 45, 30)) }
        val withIcon = GroupCellFit.plan(lights, 618, null, m, 5, icons = true)
        assertTrue(withIcon.option.icon)
        val tight = GroupCellFit.plan(lights, 618, 200, m, 5, icons = true)
        assertEquals(9, tight.shown, "còn bỏ icon được thì chưa bỏ mục")
        assertFalse(tight.option.icon)
    }

    @Test
    fun `hang nut - cot theo cho nhan that can, tran cu giu cho o rong`() {
        assertEquals(3, GroupCellFit.actionCols(11, 267, 72, 6, 6), "Kính 11 nút ở 267 px: 3 cột (cũ 6 cột ⇒ nút 32 px)")
        assertEquals(6, GroupCellFit.actionCols(11, 1200, 72, 6, 6), "ô rộng giữ trần 6")
        assertEquals(1, GroupCellFit.actionCols(3, 50, 72, 6, 6))
        assertEquals(0, GroupCellFit.actionCols(0, 300, 72, 6, 6))
    }

    /**
     * Owner 13/10 (số giả trên máy ảo): *"chỉ số lệch phải, nằm chơi vơi"*. Dạng ngang dùng HAI CỘT CHUNG: mọi ô con cùng bề
     * rộng cột nhãn + cột số ⇒ số mọi hàng thẳng trục; cột số = số DÀI NHẤT (`45678 km`), không ô nào cắt số.
     */
    @Test
    fun `dang ngang - hai cot chung, cot so theo so dai nhat`() {
        val real = listOf(
            need(listOf(41, 40, 64), value = 52),     // Thời gian chuyến · 1:25
            need(listOf(38, 32, 55), value = 118),    // Tiêu thụ 50km · 18.2 kWh
            need(listOf(38, 52), value = 125),        // Odo tổng · 45678 km
        )
        // Ô 267 px: nhãn 1 dòng không vừa cạnh `45678 km`. ⚠ 3.01 R22 (owner 13/10 "nhìn thô" → brief Kim §2.2) ĐẢO luật cũ
        // "bậc 1 chọn DỌC": hàng `nhãn · số` thắng mọi dạng dọc ⇒ NGANG nhãn HAI dòng (`Thời gian / chuyến … 1:25`).
        val narrow = GroupCellFit.plan(real, 267, null, m, 3, false)
        assertEquals(Form.SIDE, narrow.option.form)
        assertEquals(2, narrow.option.lines)
        val p = GroupCellFit.plan(real, 400, null, m, 1, false)
        val c = p.columns
        assertTrue(c != null && p.option.form == Form.SIDE)
        assertEquals(125, c!!.valuePx, "cột số = số dài nhất")
        val cellW = 400 / p.cols - 2 * m.margin
        assertTrue(2 * m.padX + c.labelPx + m.sideGap + c.valuePx <= cellW, "cả cặp cột vừa ô con")
        assertEquals(real.maxOf { it.label1 }, c.labelPx, "cột nhãn = nhãn một dòng dài nhất")
    }

    /**
     * 3.01 R22 — KHOÁ thứ tự bậc mới: mọi dạng NGANG (1 rồi 2 dòng nhãn) ở mọi số cột đứng TRƯỚC dạng DỌC. Đảo lại thứ tự cũ
     * (ngang/dọc chung một bậc) thì ô hai cột DỌC thắng một cột NGANG ⇒ bài này đỏ.
     */
    @Test
    fun `hang ngang thang moi dang doc - R22`() {
        val two = listOf(need(listOf(60), value = 40), need(listOf(60), value = 40))
        // 220 px: hai cột DỌC vừa (mỗi ô ~100 px ≥ 60), một cột NGANG cũng vừa (60 + khe + 40) ⇒ phải chọn NGANG.
        val p = GroupCellFit.plan(two, 220, null, m, 3, false)
        assertEquals(Form.SIDE, p.option.form)
        val plain = GroupCellFit.tiers(false)
        assertTrue(plain.indexOfFirst { g -> g.any { it.form == Form.STACK } } > plain.indexOfLast { g -> g.any { it.form == Form.SIDE } })
    }
}
