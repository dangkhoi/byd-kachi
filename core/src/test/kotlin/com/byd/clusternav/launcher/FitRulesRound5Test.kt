package com.byd.clusternav.launcher

import com.byd.clusternav.launcher.GridFit.Form
import com.byd.clusternav.launcher.GridFit.Shape
import kotlin.math.ceil
import kotlin.math.max
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * QA3 + soát vòng 5 (2.87, 04/10) — quyết định thuần mới:
 *  - [GridFit] bước 2c "giá trị TRỌN": khung 2×1 có dock [đồng hồ, PM2.5, tốc độ] — [ĐO QA3] `cell=84x99 HORIZONTAL/1
 *    k=0.952 legible=false`, mọi giá trị thành `…` (mỗi chữ 13–14px). Ngay cả một mình ở sàn 10sp giờ không vừa ô NGANG
 *    ⇒ bộ giải phải xếp DỌC (số trên chú thích). Khung 3×1 có dock (ô ngang 136×99) và 2×1 không dock (dọc 84×124) giữ
 *    nguyên bố cục như nhật ký QA3;
 *  - [FitRules.iconCap]: icon cùng lưới cùng cỡ ([ĐO QA3 `a/th-dock.json`] 26 vs 29px);
 *  - [FitRules.Cell.keep]: dạng phụ đo lười giữ số cũ cũng nhận số thật khi ô còn cắt;
 *  - [FitLabels.spoken]: nhãn ngắn ⇒ nhãn đầy làm mô tả trợ năng của CHÍNH chữ tên.
 *
 * Hộp đo của ô nén `MiniCard` dựng lại từ số dp của bộ dựng (mật độ 1,5 — máy ảo QA): lề 8dp, icon 20dp + lề 4dp, số 17sp,
 * chú thích 10.5sp; bề rộng chữ theo mô hình của `FitValuesTest` (khớp hai sự thật đo QA3), dòng ≈ 1,37 em ([ĐO QA3]: TextView
 * 15px cao 21, 24,3px cao 33). [SUY] — bài khoá LUẬT chọn bố cục, không khoá từng px.
 */
class FitRulesRound5Test {

    private fun em(c: Char): Float = when {
        c == '1' -> 0.50f
        c.isDigit() -> 0.585f
        c == ':' -> 0.30f
        c == '/' -> 0.40f
        c == '—' -> 1.0f
        else -> 0.52f
    }

    private fun need(text: String, px: Float): Int {
        val widest = FitValues.widestDigit { em(it) * px }
        return FitValues.needPx(FitValues.headroom(text, widest).fold(0f) { s, c -> s + em(c) } * px)
    }

    private fun lineH(px: Float): Int = ceil(1.37 * px).toInt()

    private val m = 10.0 / 10.5

    /** Ba dạng CHÍNH (dọc-2, dọc-1, ngang-1) của ô nén [value]/[caption]; [whole] = có hộp "chỉ giá trị" (bản vá) hay không. */
    private fun mini(value: String, caption: String, whole: Boolean): List<Shape> {
        val vPx = 17f * 1.5f
        val cPx = 10.5f * 1.5f
        val wPx = (15.0 / m).toFloat()
        val vN = need(value, vPx); val cN = need(caption, cPx); val wN = need(value, wPx)
        val vert = Shape(
            Form.VERTICAL, (24 + max(30, vN)).toDouble(), (24 + 30 + 6 + lineH(vPx) + lineH(cPx)).toDouble(), m, 2,
            wholeWidthPx = if (whole) (24 + max(30, wN)).toDouble() else null,
            wholeHeightPx = if (whole) (24 + 30 + 6 + lineH(wPx)).toDouble() else null,
        )
        val row = Shape(
            Form.HORIZONTAL, (24 + 30 + 6 + vN + cN).toDouble(), (24 + max(30, lineH(vPx))).toDouble(), m, 1,
            wholeWidthPx = if (whole) (24 + 30 + 6 + wN).toDouble() else null,
            wholeHeightPx = if (whole) (24 + max(30, lineH(wPx))).toDouble() else null,
        )
        return listOf(vert, vert.copy(lines = 1), row)
    }

    /** `FitGridLayout.spec` của lưới ô nén (không mục bấm được ⇒ không xét đích chạm): khe 8dp, chừa 2px, trần 2, bậc 1/32. */
    private val spec = GridFit.Spec(gapPx = 12, slackPx = 2, maxScale = 2.0, quantum = 1.0 / 32)

    @Test
    fun `2x1 co dock - gia tri khong vua o ngang ke ca o san thi xep doc`() {
        val fixed = GridFit.fit(3, 301, 123, mini("06:57", "04/10", whole = true), spec)
        assertEquals(Form.VERTICAL, fixed.shape?.form, "QA3: ngang 84×99 ⇒ `…` mọi giá trị; dọc giữ được giờ ở sàn")
        assertEquals(3 to 1, fixed.cols to fixed.rows)
        assertFalse(fixed.legible, "vẫn là tầng không đọc được (chú thích nhường — FitValues.stackYields)")
        // Bản lỗi (không hộp "chỉ giá trị"): bước 2b "vừa cao ở sàn" chọn NGANG — đúng nhật ký QA3.
        val old = GridFit.fit(3, 301, 123, mini("06:57", "04/10", whole = false), spec)
        assertEquals(Form.HORIZONTAL, old.shape?.form)
        assertEquals(3 to 1, old.cols to old.rows)
    }

    @Test
    fun `3x1 co dock va 2x1 khong dock giu bo cuc nhu nhat ky QA3`() {
        val dock3 = GridFit.fit(3, 458, 123, mini("06:57", "04/10", whole = true), spec)
        assertEquals(Form.HORIZONTAL, dock3.shape?.form, "ô ngang 136×99 giữ được giờ ⇒ không đổi (giờ trọn nhờ FitValues.share)")
        assertEquals(3 to 1, dock3.cols to dock3.rows)
        assertEquals(m, dock3.scale, 1e-9)
        val nodock2 = GridFit.fit(3, 301, 148, mini("06:59", "04/10", whole = true), spec)
        assertEquals(Form.VERTICAL, nodock2.shape?.form)
        assertEquals(3 to 1, nodock2.cols to nodock2.rows)
    }

    @Test
    fun `tang doc duoc khong doi - hop chi gia tri chi xet o tang khong doc duoc`() {
        listOf(615 to 123, 615 to 259, 301 to 259).forEach { (w, h) ->
            for (n in 1..3) {
                val a = GridFit.fit(n, w, h, mini("06:57", "04/10", whole = true), spec)
                val b = GridFit.fit(n, w, h, mini("06:57", "04/10", whole = false), spec)
                if (a.legible || b.legible) {
                    assertEquals(b.shape?.form, a.shape?.form, "$w×$h n=$n")
                    assertEquals(b.cols to b.rows, a.cols to a.rows, "$w×$h n=$n")
                    assertEquals(b.scale, a.scale, 1e-9, "$w×$h n=$n")
                }
            }
        }
    }

    @Test
    fun `tran icon chung - icon bi chan nho nhat lam tran cho ca luoi`() {
        // [ĐO QA3 a/th-dock.json] ô 189×43, k=0,969: icon nút kính bị chặn còn 26px, icon gói lệnh 29px (≈ k × 30px).
        val k = 0.969
        assertEquals(26.0, FitRules.iconCap(listOf(k * 30 to 26.0, k * 30 to k * 30, k * 30 to 26.4))!!, 1e-9)
        assertNull(FitRules.iconCap(listOf(k * 30 to k * 30, 24.0 to 24.0)), "không icon nào bị chặn ⇒ không trần chung")
        assertNull(FitRules.iconCap(emptyList()))
    }

    @Test
    fun `dang phu do luoi giu so cu thi o con cat van nhan so that`() {
        val c = FitRules.Cell()
        c.probed(nowMs = 0, kept = false)
        assertFalse(c.fitted(clipped = true), "không giữ số cũ ⇒ không có gì để nhận")
        c.probed(nowMs = 30_000, kept = false)
        c.keep()
        assertEquals(30_000, c.probedAt, "keep không đổi mốc đo dò")
        assertTrue(c.fitted(clipped = true), "dạng phụ giữ số cũ mà ô còn cắt ⇒ nhận số thật ngay (R-WF2)")
        assertFalse(c.fitted(clipped = true), "một lần")
    }

    @Test
    fun `luot thua chu ngan di 1-2 ky tu - hop dang nhan ngan giu nguyen, k giu nguyen`() {
        // Ô nút nhãn NGẮN (dạng phụ) cạnh ô số: hộp đo lại nhỏ đi 4–6 % (100 → 99 km/h) ở lượt THƯA.
        val old = Shape(Form.HORIZONTAL, 150.0, 60.0, 0.9, 1, short = true)
        val fresh = old.copy(widthPx = 142.0)
        val kept = FitRules.settle(old, fresh, grow = false)
        assertTrue(kept === old, "lượt thưa, nhỏ đi < 15 % ⇒ giữ hộp cũ")
        val s = GridFit.Spec(gapPx = 12, slackPx = 2, maxScale = 2.0, quantum = 1.0 / 32)
        val before = GridFit.fit(4, 615, 123, listOf(old), s)   // 4×1, ô 136px: bề rộng quyết k
        assertEquals(before.scale, GridFit.fit(4, 615, 123, listOf(kept), s).scale, 1e-12, "k không đổi ⇒ cả lưới không 'thở'")
        assertTrue(GridFit.fit(4, 615, 123, listOf(fresh), s).scale > before.scale, "nhận số mới thì k nhảy một bậc — điều settle chặn")
        assertTrue(FitRules.settle(old, old.copy(widthPx = 120.0), grow = false) !== old, "nhỏ đi > 15 % thì nhận (giãn lại được)")
    }

    @Test
    fun `tro nang - nhan ngan thi ban day tren chinh chu ten, ban day thi tra mo ta cua bo dung`() {
        assertEquals("Kính sau trái", FitLabels.spoken(true, "Kính sau trái", null))
        assertNull(FitLabels.spoken(false, "Kính sau trái", null), "bản đầy (kể cả `…`) — TextView tự báo chữ của nó")
        assertEquals("mô tả", FitLabels.spoken(false, "Kính sau trái", "mô tả"))
    }
}
