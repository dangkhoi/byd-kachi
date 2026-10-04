package com.byd.clusternav.launcher

import com.byd.clusternav.launcher.FitRules.Clip
import com.byd.clusternav.launcher.FitRules.Verdict
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * L5 WIDGET-FIT-ALL — soát vòng 4 + QA2 04/10 (làn J1), quyết định thuần của tầng vẽ:
 *  - [P3 soát 4] [FitRules.Cell.fitted]: lượt khớp KHÔNG đo dò ô này không được xoá cờ "kẹt" của nó — bản vòng 3 cho hai ô
 *    cùng kẹt mà lệch nhịp xoá cờ của nhau ⇒ đo dò 1 Hz mãi mãi (R-WF6);
 *  - [P2 QA2] luật GIÁ TRỊ (đồng hồ trong khung 3 mục hiện `0…`, TextView `05:10` rộng 39px, nhật ký `n=3 frame=458x123
 *    -> 3x1 cell=136x99 HORIZONTAL/1 k=0.952 raw=0.719 legible=false`) — nay ở `FitValuesTest` (QA3: luật cũ chưa chữa được).
 */
class FitRulesRound4Test {

    // ── P3 · hai ô kẹt không xoá cờ của nhau ────────────────────────────────────────────────────────────────

    @Test
    fun `P3 - hai o ket lech nhip khong do do nhau moi giay`() {
        val x = FitRules.Cell(); val y = FitRules.Cell()
        x.probed(nowMs = 0, kept = false); y.probed(nowMs = 0, kept = false)
        x.fitted(clipped = true, probedContent = true); y.fitted(clipped = true, probedContent = true)
        assertTrue(x.stuck && y.stuck)
        // t = 30 s: chữ X trùng lúc đo (NONE), Y đến lượt thưa ⇒ chỉ Y đo dò.
        assertEquals(Verdict.NONE, x.check(sigSame = true, legible = true, nowMs = 30_000) { Clip.YES })
        assertEquals(Verdict.DUE, y.check(sigSame = false, legible = true, nowMs = 30_000) { Clip.YES })
        y.probed(nowMs = 30_000, kept = false); y.fitted(clipped = true, probedContent = true)
        // Lượt khớp đó đọc X: còn cắt, chữ X đã đổi từ lúc X đo dò ⇒ KHÔNG được xoá cờ kẹt của X.
        x.fitted(clipped = true, probedContent = false)
        assertTrue(x.stuck, "lượt đo dò của Y xoá cờ kẹt của X ⇒ X đo dò lại sau 1 s")
        // t = 31 s: X đến lượt THƯA của chính nó (đo dò lúc 0) — hợp lệ.
        assertEquals(Verdict.DUE, x.check(sigSame = false, legible = true, nowMs = 31_000) { Clip.YES })
        x.probed(nowMs = 31_000, kept = false); x.fitted(clipped = true, probedContent = true)
        y.fitted(clipped = true, probedContent = false)
        assertTrue(y.stuck, "lượt đo dò của X không xoá cờ kẹt của Y")
        // Y không đo dò lại cho tới 30 s sau lượt của CHÍNH nó (30 s ⇒ 60 s) — không còn bóng bàn 1 Hz.
        for (t in 32_000L until 60_000L step 1_000L) {
            assertEquals(Verdict.NONE, y.check(sigSame = false, legible = true, nowMs = t) { Clip.YES }, "t=$t")
        }
        assertEquals(Verdict.DUE, y.check(sigSame = false, legible = true, nowMs = 60_000) { Clip.YES })
    }

    @Test
    fun `P3 - ca de xuat cua soat 4 - ket roi luot khop ngoai thi van ket, het cat thi het ket`() {
        val c = FitRules.Cell()
        c.probed(nowMs = 0, kept = false)
        c.fitted(clipped = true, probedContent = true)
        assertTrue(c.stuck)
        assertEquals(Verdict.NONE, c.check(sigSame = false, legible = true, nowMs = 1_000) { Clip.YES })
        c.fitted(clipped = true, probedContent = false)
        assertTrue(c.stuck)
        assertEquals(Verdict.NONE, c.check(sigSame = false, legible = true, nowMs = 29_999) { Clip.YES })
        // Hết cắt thật (chữ ngắn lại) ⇒ hết kẹt, kể cả ở lượt khớp ngoài — đường nở 1 s mở lại cho lần cắt sau.
        c.fitted(clipped = false, probedContent = false)
        assertFalse(c.stuck)
        // Ô CHƯA kẹt thì lượt khớp ngoài vẫn không chốt kẹt (vá soát 3 giữ nguyên).
        c.fitted(clipped = true, probedContent = false)
        assertFalse(c.stuck)
    }

    // ── P2 QA2 · luật giá trị: CHUYỂN sang `FitValuesTest` (QA3 + soát vòng 5) ───────────────────────────────────────────
    // ĐỔI GHIM có lý do: `FitRules.valuePx(fit, need, avail, floor)` (nội suy tuyến tính + sai số 1px) đã GỠ. [ĐO QA3] nó coi
    // `06:58` ở sàn 15px (cần > 39px trên máy ảo) là vừa 39px (ước lượng PIL 38,7 + 1px) ⇒ giờ vẫn `06:…`. Thay bằng
    // `FitValues.valuePx` đo LẠI chữ ở chính cỡ thử, làm tròn lên + 1px, cộng luật giá trị ưu tiên hơn chú thích
    // (`FitValues.share`) — các ca cũ (vừa ⇒ cỡ lưới, sàn, chú thích ở sàn, chữ rỗng, bậc 1/32) ở `FitValuesTest`.
}
