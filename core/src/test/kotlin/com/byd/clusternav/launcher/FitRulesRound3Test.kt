package com.byd.clusternav.launcher

import com.byd.clusternav.launcher.FitRules.Clip
import com.byd.clusternav.launcher.FitRules.Verdict
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.roundToInt

/**
 * L5 WIDGET-FIT-ALL — soát vòng 3 + QA 04/10 (làn H1), quyết định thuần của tầng vẽ:
 *  - [P3 soát 3] [FitRules.Cell.fitted] chốt "kẹt" cả khi vết cắt đến từ chữ MỚI chưa đo dò (lượt khớp do ô khác /
 *    đổi khung chạy trước khi ô này đến lượt) ⇒ nhịp nở 1 s thành chờ 30 s;
 *  - [QA P2] icon cỡ cố định không bao giờ to hơn ô, giữ tỉ lệ ([FitRules.iconScale]) — icon 60px trong ô 36px bị khung
 *    lề cắt thành dải 12px (`icononly-zoom.png` (bằng chứng phiên, ngoài repo)).
 */
class FitRulesRound3Test {

    @Test
    fun `P3 - vet cat cua chu chua do do khong chot ket, nhip no 1 s giu nguyen`() {
        val c = FitRules.Cell()
        c.probed(nowMs = 0, kept = false)
        c.fitted(clipped = false, probedContent = true)
        // t = 500 ms: số đổi (99 → 100) và đã cắt, nhưng chưa tới GROW_GAP ⇒ chưa đo dò.
        assertEquals(Verdict.NONE, c.check(sigSame = false, legible = true, nowMs = 500) { Clip.YES })
        // Một lượt khớp do Ô KHÁC (hoặc đổi khung) chạy ngay sau: ô này vẫn cắt, nhưng chữ đang hiện KHÔNG phải chữ đã
        // đo dò ⇒ không được chốt "lượt đo dò không chữa được".
        c.fitted(clipped = true, probedContent = false)
        assertFalse(c.stuck, "chữ chưa đo dò mà cắt không phải 'kẹt'")
        // Lần xét sau (≥ 1 s từ lượt đo dò trước) ⇒ đến lượt NỞ ngay, không chờ 30 s.
        assertEquals(Verdict.DUE, c.check(sigSame = false, legible = true, nowMs = FitRules.GROW_GAP_MS) { Clip.YES })
        assertTrue(c.grow)
    }

    @Test
    fun `P3 - vet cat cua chu DA do do van chot ket (khung qua nho khong vong lap)`() {
        val c = FitRules.Cell()
        c.probed(nowMs = 0, kept = false)
        c.fitted(clipped = true, probedContent = true)
        assertTrue(c.stuck)
        assertEquals(Verdict.NONE, c.check(sigSame = false, legible = true, nowMs = FitRules.GROW_GAP_MS) { Clip.YES })
        assertEquals(Verdict.DUE, c.check(sigSame = false, legible = true, nowMs = FitRules.RECHECK_MS) { Clip.YES })
        // Mặc định (chỗ gọi cũ) = chữ đã đo dò — hành vi soát vòng 2 không đổi.
        val d = FitRules.Cell().apply { probed(nowMs = 0, kept = false) }
        d.fitted(clipped = true)
        assertTrue(d.stuck)
    }

    @Test
    fun `QA - icon khong to hon o, giu ti le, khong doi gi khi vua`() {
        // Ô 36×99px, lề 12px ngang (k = 2) ⇒ chỗ 24px: icon 30px × 2 = 60px phải về 24px (không bị khung lề cắt).
        val k = FitRules.iconScale(2.0, 30, 30, 36 - 12, 99 - 12)
        assertEquals(24, (30 * k).roundToInt())
        // Icon không vuông: CÙNG một hệ số hai trục ⇒ tỉ lệ giữ nguyên (không bóp một chiều).
        val r = FitRules.iconScale(2.0, 30, 20, 24, 200)
        assertEquals(0.8, r, 1e-9)
        assertEquals(24 to 16, (30 * r).roundToInt() to (20 * r).roundToInt())
        // Còn chỗ ⇒ đúng k của lưới (lưới đọc được: chặn này không đổi gì).
        assertEquals(1.25, FitRules.iconScale(1.25, 30, 30, 200, 200), 1e-9)
        // Chỗ âm/0 (khung chưa đo) ⇒ tối thiểu 1px, không âm.
        assertTrue(FitRules.iconScale(2.0, 30, 30, -5, 0) * 30 >= 1.0 - 1e-9)
        // Không có cỡ cố định ⇒ không đụng.
        assertEquals(2.0, FitRules.iconScale(2.0, 0, 30, 10, 10), 1e-9)
    }
}
