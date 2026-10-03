package com.byd.clusternav.launcher

import com.byd.clusternav.launcher.FitRules.Clip
import com.byd.clusternav.launcher.FitRules.Lp
import com.byd.clusternav.launcher.FitRules.MATCH
import com.byd.clusternav.launcher.FitRules.Verdict
import com.byd.clusternav.launcher.FitRules.WRAP
import com.byd.clusternav.launcher.GridFit.Form
import com.byd.clusternav.launcher.GridFit.Shape
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * L5 WIDGET-FIT-ALL — các quyết định thuần thêm ở soát vòng 2 (2.87), khoá bằng TRÌNH TỰ sự kiện thật của tầng vẽ.
 *
 * Khoá cái gì:
 *  - [P1] số bề rộng `WRAP` (ô tốc độ, ô đọc `AxisRow`, viên thuốc) dài thêm một chữ số ≥ 30 s sau lượt đo dò trước:
 *    lúc đổ tại chỗ `TextView` đã BỎ bố cục (`TextView.java:9686-9691` r47) ⇒ trạng thái cắt CHƯA BIẾT ⇒ không được
 *    chốt "lượt thưa"; lượt đo kế thấy cắt ⇒ lượt nhận số mới. Bản trước chốt `grow = false` ⇒ [FitRules.settle] giữ
 *    hộp cũ ⇒ `100` kẹt `10`/`0` tới lần dựng lại; số bị bẻ đôi qua hai dòng cũng phải tính là cắt;
 *  - [P2] dấu chữ chụp lúc đo dò (ô để ở dạng chỉ-icon, nhãn `GONE`) phải bằng dấu ở dạng đang hiện ⇒ ô nút/gói lệnh
 *    không bị đo dò lại mỗi 30 s mãi mãi;
 *  - [P3] bộ áp không kéo weight bộ dựng đổi lúc chạy (thanh tiến trình nhạc) về số lúc chụp;
 *  - [P3] ngân sách `…` chỉ cho chữ tự do được KHAI (tên bài/nghệ sĩ); giá trị/chú thích phải hiện trọn (R-WF2).
 */
class FitRulesRound2Test {

    private val old = Shape(Form.VERTICAL, 100.0, 80.0, 0.9, 2)

    // ── P1: trình tự đổ tại chỗ → lượt đo → lượt khớp ──────────────────────────────────────────────────────────────

    /** Ô vừa được đo dò ở `t = 0` và khớp xong không cắt. */
    private fun fresh(): FitRules.Cell = FitRules.Cell().apply { probed(nowMs = 0, kept = false); fitted(clipped = false) }

    @Test
    fun `P1 - chu WRAP doi chu luc bo cuc null khong bi chot luot thua, luot do sau thay cat thi nhan so moi`() {
        val c = fresh()
        val t = FitRules.RECHECK_MS + 5_000        // tầm xa ⇒ `99 → 100 km` đến sau nhịp thưa 30 s
        // Đổ tại chỗ: TextView WRAP vừa `nullLayouts()` ⇒ chưa biết. Bản cũ đọc "không cắt" ⇒ stale + grow=false.
        assertEquals(Verdict.WAIT, c.check(sigSame = false, legible = true, nowMs = t) { Clip.UNKNOWN })
        assertFalse(c.stale, "chưa biết thì KHÔNG được chốt gì — lượt đo kế quyết trên bố cục thật")
        // Lượt đo (grew sau measureAll): `100` hiện `10`/`0` ⇒ cắt thật.
        assertEquals(Verdict.DUE, c.check(sigSame = false, legible = true, nowMs = t + 16) { Clip.YES })
        assertTrue(c.grow, "lượt đo dò này phải NHẬN số mới; grow=false ⇒ settle giữ hộp cũ ⇒ kẹt cắt")
        val bigger = old.copy(widthPx = 140.0)
        assertSame(bigger, FitRules.settle(old, bigger, c.grow))
    }

    @Test
    fun `P1 - chua biet chi co nghia truoc luot do, sau luot do khong duoc chan mai viec xet lai`() {
        val c = fresh()
        val t = FitRules.RECHECK_MS + 1
        // Sau measureAll, chữ vẫn không bố cục = khung cha không đo nó (không vẽ) ⇒ không cắt ⇒ lượt thưa vẫn chạy.
        assertEquals(Clip.NO, FitRules.known(Clip.UNKNOWN, measured = true))
        assertEquals(Verdict.DUE, c.check(sigSame = false, legible = true, nowMs = t) { FitRules.known(Clip.UNKNOWN, measured = true) })
        // Trước lượt đo (đổ tại chỗ) thì vẫn là chưa biết; số đã biết không bị đổi.
        assertEquals(Clip.UNKNOWN, FitRules.known(Clip.UNKNOWN, measured = false))
        assertEquals(Clip.YES, FitRules.known(Clip.YES, measured = true))
        assertEquals(Clip.NO, FitRules.known(Clip.NO, measured = false))
    }

    @Test
    fun `P1 - o da den luot (luot thua) ma lan xet sau thay cat thi nang thanh luot nhan so moi`() {
        val c = fresh()
        val t = FitRules.RECHECK_MS + 1
        // Chữ bề rộng tĩnh: bố cục thay tại chỗ, đọc được "không cắt" ⇒ lượt thưa.
        assertEquals(Verdict.DUE, c.check(sigSame = false, legible = true, nowMs = t) { Clip.NO })
        assertFalse(c.grow)
        // Trước lượt khớp, lượt đo thấy cắt (một chữ WRAP khác của ô) ⇒ lượt thưa KHÔNG được nuốt vết cắt.
        assertEquals(Verdict.DUE, c.check(sigSame = false, legible = true, nowMs = t + 16) { Clip.YES })
        assertTrue(c.grow)
        // Chưa biết / không cắt ở lần xét sau không hạ cấp quyết định đã có.
        assertEquals(Verdict.DUE, c.check(sigSame = false, legible = true, nowMs = t + 32) { Clip.UNKNOWN })
        assertEquals(Verdict.DUE, c.check(sigSame = false, legible = true, nowMs = t + 48) { Clip.NO })
        assertTrue(c.grow && c.stale)
        c.probed(nowMs = t + 48, kept = false)
        assertFalse(c.stale || c.grow, "lượt khớp đã đo dò ⇒ hết lượt")
    }

    @Test
    fun `P1 - luot thua giu hop cu ma o con cat thi nhan so do that ngay, mot lan`() {
        val c = FitRules.Cell()
        c.probed(nowMs = 0, kept = true)                       // settle giữ hộp cũ ở ≥ 1 dạng
        assertTrue(c.fitted(clipped = true), "còn cắt ⇒ chỗ gọi nhận số đo thật đã có rồi khớp lại")
        assertTrue(c.stuck)
        assertFalse(c.fitted(clipped = true), "chỉ MỘT lần — đã nhận số thật mà vẫn cắt (khung quá nhỏ) ⇒ không lặp")
        // Số giữ lại vẫn chờ: không cắt bây giờ, nhưng khung đổi sau đó làm ô cắt ⇒ lúc đó mới nhận.
        c.probed(nowMs = 0, kept = true)
        assertFalse(c.fitted(clipped = false))
        assertTrue(c.kept)
        assertTrue(c.fitted(clipped = true))
        // Lượt nhận số mới (không giữ gì) mà vẫn cắt ⇒ không có gì để nhận.
        c.probed(nowMs = 0, kept = false)
        assertFalse(c.fitted(clipped = true))
    }

    @Test
    fun `P1 - chu khong doi thi khong doc bo cuc, khong lam gi (khong vong lap)`() {
        val c = fresh()
        var reads = 0
        assertEquals(Verdict.NONE, c.check(sigSame = true, legible = true, nowMs = 10 * FitRules.RECHECK_MS) { reads++; Clip.YES })
        assertEquals(0, reads, "dấu trùng ⇒ không cần đọc bố cục")
        // Kẹt + cắt ⇒ thưa (R-WF6), kể cả khi giờ đã biết.
        c.probed(nowMs = 0, kept = false); c.fitted(clipped = true)
        assertEquals(Verdict.NONE, c.check(sigSame = false, legible = true, nowMs = FitRules.GROW_GAP_MS) { Clip.YES })
        assertEquals(Verdict.DUE, c.check(sigSame = false, legible = true, nowMs = FitRules.RECHECK_MS) { Clip.YES })
    }

    @Test
    fun `P1 - so bi be doi qua hai dong tinh la cat, xuong dong hop le thi khong`() {
        // `100` trong ô WRAP hẹp hơn chính nó: bộ ngắt dòng bẻ ở mọi ranh giới chữ (minikin desperate break).
        assertTrue(FitRules.splitsNumber("100", 2))
        assertTrue(FitRules.splitsNumber("100", 1))
        assertTrue(FitRules.splitsNumber("12:34", 3), "giờ bẻ sau dấu hai chấm")
        assertTrue(FitRules.splitsNumber("12:34", 2))
        assertTrue(FitRules.splitsNumber("1,234", 2))
        assertTrue(FitRules.splitsNumber("๑๐๐", 1), "chữ số Thái cũng là số")
        // Xuống dòng ở chỗ hợp lệ: sau khoảng trắng, sau gạch nối, giữa chữ cái (Thái/Hán xuống dòng giữa hai chữ cái).
        assertFalse(FitRules.splitsNumber("418 km", 4))
        assertFalse(FitRules.splitsNumber("2.3–2.5", 4))
        assertFalse(FitRules.splitsNumber("Tốc độ hiện tại", 7))
        assertFalse(FitRules.splitsNumber("abc,1", 4), "dấu phẩy không kẹp giữa hai chữ số không phải số")
        assertFalse(FitRules.splitsNumber("ความเร็ว", 3))
        assertFalse(FitRules.splitsNumber("100", 0))
        assertFalse(FitRules.splitsNumber("100", 3))
    }

    // ── P2: dấu chữ không phụ thuộc dạng ─────────────────────────────────────────────────────────────────────────

    private data class T(val text: String, val visibility: Int, val label: Boolean)

    private fun sig(vararg t: T): Int = t.fold(17) { h, x -> FitRules.sigStep(h, x.text, x.visibility, x.label) }

    @Test
    fun `P2 - dau chup luc do do (chi-icon, nhan GONE) bang dau o dang dang hien`() {
        val visible = 0
        val gone = 8
        // Ô nút "Sấy kính" + dòng chọn "Bật": đo dò để ô ở dạng chỉ-icon (nhãn GONE), lưới hiện dạng DỌC (nhãn hiện).
        val probe = sig(T("Sấy kính", gone, label = true), T("Bật", visible, label = false))
        val shown = sig(T("Sấy kính", visible, label = true), T("Bật", visible, label = false))
        assertEquals(probe, shown, "dấu khác nhau ⇒ ô bị đo dò lại mỗi 30 s mãi mãi + đo lại cả màn")
        // Nội dung đổi thật thì dấu phải đổi.
        assertNotEquals(shown, sig(T("Sấy kính", visible, label = true), T("Tắt", visible, label = false)))
        assertNotEquals(shown, sig(T("Sấy kính sau", visible, label = true), T("Bật", visible, label = false)))
        // Hiện/ẩn của chữ KHÔNG phải nhãn là của bộ dựng (dòng phụ GONE khi rỗng) ⇒ vẫn vào dấu.
        assertNotEquals(
            sig(T("418", visible, label = false), T("km", visible, label = false)),
            sig(T("418", visible, label = false), T("km", gone, label = false)),
        )
    }

    // ── P3: weight ───────────────────────────────────────────────────────────────────────────────────────────────

    /** Một con của ô qua nhiều lượt áp — mô hình đúng phần weight của `FitScale.params` (ghi + giữ). */
    private class Kid(val base: Lp) {
        var weight = base.weight
        var held: Float? = null
        fun apply(k: Double, rotated: Boolean) {
            val t = FitRules.lp(base, k, rotated)
            FitRules.weight(base.weight, t.weight, weight, held)?.let { weight = it }
            held = t.weight.takeIf { it != base.weight }
        }
    }

    @Test
    fun `P3 - weight bo dung doi luc chay (thanh tien trinh nhac) khong bi keo ve so luc chup`() {
        // `done` của MediaWidgetView: LP(0, MATCH, 0.001) lúc dựng (chưa có phiên), con của HÀNG tiến trình (không lật).
        val done = Kid(Lp(0, MATCH, 0.001f))
        done.weight = 0.4f                               // fillMedia: bài đang ở 40 %
        done.apply(1.25, rotated = false)                // lượt đo dò / lượt áp k mới
        done.apply(1.0, rotated = false)
        assertEquals(0.4f, done.weight, "thanh tiến trình nhảy về vị trí lúc dựng tới nhịp sau")
    }

    @Test
    fun `P3 - bo ap chi so huu weight khi lat ngang doi no, ve doc thi tra lai`() {
        val match = Kid(Lp(MATCH, WRAP))                 // con MATCH của khối dọc ⇒ 0 + weight 1 khi lật
        match.apply(1.0, rotated = true)
        assertEquals(1f, match.weight)
        match.apply(1.5, rotated = true)
        assertEquals(1f, match.weight)
        match.apply(1.5, rotated = false)
        assertEquals(0f, match.weight, "về DỌC ⇒ trả weight gốc")
        // Con có weight (hình xe, LP(MATCH, 0, 1)): lật chỉ đổi trục, weight không đổi ⇒ bộ áp không bao giờ ghi.
        assertNull(FitRules.weight(1f, FitRules.lp(Lp(MATCH, 0, 1f), 1.0, rotated = true).weight, 0.7f, null))
        // Đang giữ mà bộ dựng đã đổi weight ⇒ không giành lại.
        assertNull(FitRules.weight(0f, 0f, 0.5f, 1f))
    }

    // ── P3: ngân sách chữ tự do ──────────────────────────────────────────────────────────────────────────────────

    /** Bề rộng nhỏ nhất mà chữ một dòng [naturalPx] (`…` khi chỗ < tự nhiên) không còn bị tính là cắt — phép tìm của FitProbe. */
    private fun minWidth(naturalPx: Int, textPx: Float, free: Boolean): Int =
        (0..4_000).first { w -> !FitRules.cut(ellipsized = w < naturalPx, free = free, availPx = w.toFloat(), textPx = textPx) }

    @Test
    fun `P3 - gia tri va chu thich phai hien tron, chi chu tu do duoc khai moi duoc cat sau ngan sach`() {
        val px = 25.5f                                   // 17sp @1.5 — số to của ô nén
        val vin = (9.5 * px).toInt()                     // VIN ≈ 9,5 em
        assertEquals(vin, minWidth(vin, px, FitRules.freeText(optIn = false, maxLines = 1, ellipsized = true)),
            "giá trị không khai ⇒ đo dò đòi TRỌN chữ (R-WF2), không dừng ở ngân sách 6 em")
        val caption = (7.5 * 15.75f).toInt()             // "Chế độ vận hành" ≈ 7,5 em @10.5sp
        assertEquals(caption, minWidth(caption, 15.75f, FitRules.freeText(optIn = false, maxLines = 1, ellipsized = true)))
        // Tên bài 20 em, bộ dựng khai tự do ⇒ dừng ở ngân sách, không kéo cả lưới.
        val title = (20 * px).toInt()
        val got = minWidth(title, px, FitRules.freeText(optIn = true, maxLines = 1, ellipsized = true))
        assertTrue(got < title && got <= (FitRules.FREE_TEXT_EM * px).toInt() + 1, "tên bài dừng ở ngân sách: $got")
        // Khai mà không phải một dòng có `…` ⇒ không phải chữ tự do.
        assertFalse(FitRules.freeText(optIn = true, maxLines = 2, ellipsized = true))
        assertFalse(FitRules.freeText(optIn = true, maxLines = 1, ellipsized = false))
        assertFalse(FitRules.cut(ellipsized = false, free = false, availPx = 0f, textPx = px), "không `…` ⇒ không cắt")
    }
}
