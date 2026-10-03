package com.byd.clusternav.launcher

import com.byd.clusternav.launcher.FitRules.Lp
import com.byd.clusternav.launcher.FitRules.MATCH
import com.byd.clusternav.launcher.FitRules.WRAP
import com.byd.clusternav.launcher.GridFit.Form
import com.byd.clusternav.launcher.GridFit.Shape
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * L5 WIDGET-FIT-ALL — các quyết định thuần của tầng vẽ ([FitRules]), soát vòng 1 (2.87).
 *
 * Khoá cái gì:
 *  - [P1] dạng NGANG không để con `MATCH_PARENT` nuốt hết hàng (ô nén mất dòng phụ + dấu "chưa kiểm", hàng nút nhạc
 *    và dải vạch mức rộng 0). Kiểm bằng MÔ HÌNH phân bề rộng của `LinearLayout` ngang r47 (`measureHorizontal`
 *    `LinearLayout.java:1162-1215` lượt 1, `:1339-1375` chia `weight`) chứ không chỉ so số ra của [FitRules.lp];
 *  - [P2] chữ tự do một dòng dài (tên bài) không quyết cỡ cả lưới; ngắn thì vẫn phải hiện trọn;
 *  - [P2] con cỡ cố định tràn khung cha (nút nhạc 48dp, thanh tiến trình) bị bắt;
 *  - [P2] đo dò lại theo chữ đổi: kịp khi chữ mới bị cắt, thưa khi không, phục hồi được khi lưới không đọc được,
 *    không giật vì dao động nhỏ.
 */
class FitRulesTest {

    // ── mô hình LinearLayout NGANG đo EXACTLY [w] (r47) ─────────────────────────────────────────────────────────────

    /** Một con: LayoutParams + bề rộng tự nhiên (WRAP). */
    private data class Kid(val lp: Lp, val natural: Int)

    /**
     * Bề rộng từng con khi hàng ngang được đo EXACTLY [w]. Lượt 1 (`:1162-1215`): con `0 + weight` bỏ qua (đo sau);
     * con khác đo với chỗ còn lại — `usedWidth = totalWeight == 0 ? mTotalLength : 0` (`:1203`) — cỡ cố định giữ cỡ,
     * MATCH lấy trọn chỗ còn lại (`getChildMeasureSpec` EXACTLY), WRAP lấy `min(tự nhiên, chỗ)`. Lượt 2 (`:1339-1375`):
     * phần dư chia theo `weight`, con `width = 0` nhận đúng phần chia.
     */
    private fun row(w: Int, kids: List<Kid>): List<Int> {
        val out = IntArray(kids.size)
        var used = 0
        var weight = 0f
        kids.forEachIndexed { i, k ->
            if (k.lp.width == 0 && k.lp.weight > 0f) { weight += k.lp.weight; return@forEachIndexed }
            weight += k.lp.weight
            val room = (if (weight == 0f) w - used else w).coerceAtLeast(0)
            out[i] = when {
                k.lp.width > 0 -> k.lp.width
                k.lp.width == MATCH -> room
                else -> minOf(k.natural, room)
            }
            used += out[i]
        }
        var excess = w - used
        var sum = weight
        kids.forEachIndexed { i, k ->
            if (k.lp.weight > 0f) {
                val share = (k.lp.weight * excess / sum).toInt()
                excess -= share; sum -= k.lp.weight
                out[i] = (if (k.lp.width == 0) share else out[i] + share).coerceAtLeast(0)
            }
        }
        return out.toList()
    }

    /** Ô nén `MiniCard` (dựng trong `LinearLayout` dọc, `addView` không LP ⇒ MATCH × WRAP — `:1929-1936`). */
    private val miniCard = listOf(
        Kid(Lp(30, 30), 30),               // icon ICON_S cỡ cố định
        Kid(Lp(MATCH, WRAP), 40),          // số lớn
        Kid(Lp(MATCH, WRAP), 30),          // dòng phụ (km/h)
        Kid(Lp(WRAP, WRAP), 50),           // dấu "chưa kiểm"
    )

    private fun rotated(kids: List<Kid>, k: Double = 1.0) = kids.map { Kid(FitRules.lp(it.lp, k, rotated = true), it.natural) }

    @Test
    fun `P1 - lat ngang giu MATCH_PARENT thi moi con sau rong 0 (mo hinh xac nhan loi cu)`() {
        // Đúng điều soát vòng 1 tả: giữ nguyên LP của khối dọc ⇒ số lớn lấy hết, dòng phụ + dấu = 0.
        val old = row(300, miniCard)
        assertEquals(270, old[1]); assertEquals(0, old[2]); assertEquals(0, old[3])
    }

    @Test
    fun `P1 - lat ngang moi con co noi dung deu co cho khi hang du rong`() {
        val kids = rotated(miniCard)
        // Con MATCH ⇒ 0 + weight 1 (bề rộng TĨNH: đổi chữ không requestLayout); cỡ cố định + WRAP giữ nguyên.
        assertEquals(Lp(0, WRAP, 1f), kids[1].lp)
        assertEquals(Lp(0, WRAP, 1f), kids[2].lp)
        assertEquals(Lp(30, 30), kids[0].lp)
        assertEquals(Lp(WRAP, WRAP), kids[3].lp)
        // Hàng đủ rộng cho mọi con ⇒ không con nào dưới bề rộng tự nhiên của nó.
        val w = 30 + 50 + 2 * 40
        row(w, kids).zip(kids).forEach { (got, kid) -> assertTrue(got >= kid.natural, "con $kid chỉ được $got px ở hàng $w") }
        // Hàng hẹp hơn ⇒ có con THIẾU chỗ (tầng vẽ thấy chữ cắt ⇒ dạng này cần rộng hơn) chứ không âm thầm = 0 mãi.
        assertTrue(row(w - 10, kids).zip(kids).any { (got, kid) -> got < kid.natural })
    }

    @Test
    fun `P1 - hang nut nhac va dai vach (ViewGroup MATCH) cung chia hang, khong bi ep 0`() {
        val media = listOf(Kid(Lp(120, 120), 120), Kid(Lp(MATCH, WRAP), 60), Kid(Lp(MATCH, WRAP), 240))
        assertEquals(0, row(600, media)[2], "lỗi cũ: hàng nút nhạc rộng 0 ⇒ không bấm Play được từ widget")
        val got = row(600, rotated(media))
        assertTrue(got[2] >= 240, "hàng nút nhạc phải có đủ 3×48dp: $got")
    }

    @Test
    fun `lp - doi dang ve DOC tra lai dung goc nhan k, con weight doi truc khi lat`() {
        assertEquals(Lp(60, 45, 0f), FitRules.lp(Lp(40, 30), 1.5, rotated = false))
        assertEquals(Lp(MATCH, WRAP, 0f), FitRules.lp(Lp(MATCH, WRAP), 2.0, rotated = false))
        assertEquals(Lp(1, 1, 0f), FitRules.lp(Lp(1, 1), 0.1, rotated = false), "cỡ cố định không về 0")
        // Con `weight` của khối dọc (MATCH × 0, w) ⇒ ngang (0 × MATCH, w): vẫn chia phần còn lại, nay theo chiều ngang.
        assertEquals(Lp(0, MATCH, 2f), FitRules.lp(Lp(MATCH, 0, 2f), 1.0, rotated = true))
        // Không lật ⇒ weight gốc trở lại (ngang → dọc khôi phục trọn vẹn).
        assertEquals(Lp(MATCH, WRAP, 0f), FitRules.lp(Lp(MATCH, WRAP, 0f), 1.0, rotated = false))
    }

    // ── chữ tự do một dòng ────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `P2 - chu tu do ngan thi phai hien tron, dai thi duoc cat sau ngan sach`() {
        val px = 25.5f                                   // 17sp @1.5
        val budget = (FitRules.FREE_TEXT_EM * px).toFloat()
        // "2.3–2.5" ≈ 3,4 em mà bị `…` ⇒ chỗ < tự nhiên < ngân sách ⇒ CẮT (R-WF2: còn bố cục đọc được thì không cắt).
        assertTrue(FitRules.freeTextCut(availPx = 3.0f * px, textPx = px))
        // Tên bài 100 ký tự bị `…` khi đã có đủ ngân sách ⇒ không tính là cắt ⇒ không kéo cả lưới.
        assertFalse(FitRules.freeTextCut(availPx = budget, textPx = px))
        assertFalse(FitRules.freeTextCut(availPx = budget + 40f, textPx = px))
        // Đơn điệu theo chỗ (phép tìm nhị phân của FitProbe cần điều này).
        var was = true
        for (a in 0..400) {
            val cut = FitRules.freeTextCut(a.toFloat(), px)
            assertTrue(was || !cut, "freeTextCut phải đơn điệu: đổi lại thành cắt ở $a px")
            was = cut
        }
        // Ngân sách theo em ⇒ nhân k cùng cỡ chữ (khớp tuyến tính ở :core vẫn đúng).
        assertFalse(FitRules.freeTextCut(availPx = 2 * budget, textPx = 2 * px))
    }

    // ── tràn khung ───────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `P2 - con co dinh tran khung cha bi bat (nut nhac, thanh tien trinh)`() {
        // Hàng 3 nút 48dp + 2 khe 8dp @1.5 = 240px trong ô còn 202dp·1.5 = 303px ⇒ vừa; trong 220px ⇒ tràn.
        val buttons = listOf(72 + 12, 72 + 12, 72)
        assertFalse(FitRules.spills(parentPx = 303, paddingPx = 0, childPx = buttons, stacked = true))
        assertTrue(FitRules.spills(parentPx = 220, paddingPx = 0, childPx = buttons, stacked = true))
        // Trục chéo / khung chồng: xét TỪNG con (thanh tiến trình 228px trong cột 200px có lề 2×12).
        assertTrue(FitRules.spills(parentPx = 200, paddingPx = 24, childPx = listOf(60, 228), stacked = false))
        assertFalse(FitRules.spills(parentPx = 260, paddingPx = 24, childPx = listOf(60, 228), stacked = false))
        // Sai số làm tròn 1px không báo nhầm; không con nào ⇒ không tràn.
        assertFalse(FitRules.spills(parentPx = 100, paddingPx = 0, childPx = listOf(50, 51), stacked = true))
        assertTrue(FitRules.spills(parentPx = 100, paddingPx = 0, childPx = listOf(50, 52), stacked = true))
        assertFalse(FitRules.spills(parentPx = 0, paddingPx = 10, childPx = emptyList(), stacked = true))
    }

    @Test
    fun `P1 - dang khong o nao ve tron duoc thi khong lam ung vien voi hop gia`() {
        val ok = mapOf(0 to true, 1 to true, 2 to false, 3 to true)
        assertEquals(listOf(0, 1, 3), FitRules.usable(listOf(0, 1, 2, 3)) { ok.getValue(it) })
        // Không dạng nào đạt (phép kiểm báo nhầm) ⇒ giữ nguyên, dạng gốc đứng đầu — vẫn vẽ được.
        assertEquals(listOf(0, 1, 2), FitRules.usable(listOf(0, 1, 2)) { false })
    }

    // ── đo dò lại theo chữ đổi ───────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `P2 - chu moi bi cat thi do lai ngay nhip ke, con lai thi thua`() {
        val g = FitRules.GROW_GAP_MS
        val r = FitRules.RECHECK_MS
        // Áp suất về giữa chuyến, ô đang "2.…" ⇒ nhịp kế tiếp.
        assertTrue(FitRules.reprobe(clipped = true, legible = true, stuck = false, sinceProbeMs = g))
        assertFalse(FitRules.reprobe(clipped = true, legible = true, stuck = false, sinceProbeMs = g - 1))
        // Lượt trước không chữa được vết cắt ⇒ không đo dò mỗi nhịp 1 Hz (R-WF6).
        assertFalse(FitRules.reprobe(clipped = true, legible = true, stuck = true, sinceProbeMs = r - 1))
        assertTrue(FitRules.reprobe(clipped = true, legible = true, stuck = true, sinceProbeMs = r))
        // Lưới KHÔNG đọc được vẫn PHỤC HỒI được (bản cũ chặn vĩnh viễn) — chỉ thưa.
        assertFalse(FitRules.reprobe(clipped = true, legible = false, stuck = false, sinceProbeMs = r - 1))
        assertTrue(FitRules.reprobe(clipped = true, legible = false, stuck = false, sinceProbeMs = r))
        // Chữ đổi mà vừa ⇒ hộp có thể đã nhỏ đi ⇒ cho lưới giãn lại, thưa.
        assertFalse(FitRules.reprobe(clipped = false, legible = true, stuck = false, sinceProbeMs = r - 1))
        assertTrue(FitRules.reprobe(clipped = false, legible = true, stuck = false, sinceProbeMs = r))
        assertTrue(g < r)
    }

    @Test
    fun `P2 - so do moi cat thi nhan, nho di ro thi nhan, dao dong nho thi giu (khong giat)`() {
        val old = Shape(Form.VERTICAL, 100.0, 80.0, 0.9, 2)
        val bigger = old.copy(widthPx = 140.0)
        val slightly = old.copy(widthPx = 92.0)              // 99 → 100 km/h: < 15 %
        val much = old.copy(widthPx = 60.0, heightPx = 78.0)  // tên bài dài → ngắn
        assertSame(bigger, FitRules.settle(old, bigger, grow = true))
        assertSame(slightly, FitRules.settle(old, slightly, grow = true))
        assertSame(old, FitRules.settle(old, bigger, grow = false), "đang vừa thì không bóp cả lưới")
        assertSame(old, FitRules.settle(old, slightly, grow = false), "dao động nhỏ không đổi cỡ lưới")
        assertSame(much, FitRules.settle(old, much, grow = false), "hộp nhỏ đi rõ ⇒ lưới giãn lại (R-WF1)")
        assertSame(old, FitRules.settle(old, old.copy(widthPx = 50.0, heightPx = 90.0), grow = false))
    }
}
