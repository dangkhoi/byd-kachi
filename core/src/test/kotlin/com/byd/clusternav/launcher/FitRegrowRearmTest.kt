package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

/**
 * ═══ 2.93 wave 2A · FIT-REGROW — dấu chờ lớn lại GIỮ tới khi ô về ĐẦY (senior review WIDGET Pass 1 [P3] + wave 2A Pass 1 ghi chú b) ═══
 *
 * Lỗi khoá [SUY đọc mã, soát 7 + hai lượt review]: nhịp đổ tại chỗ chỉ CO ([FitValues.holds] · [FitValues.wouldClip]) ⇒ ô bị co ở
 * chữ rộng mà dấu chờ lớn lại bị xoá trước khi ô về cỡ LƯỚI thì chữ hẹp đến sau (trùng dấu đã dò ⇒ không lượt dò lại) đứng mãi ở
 * cỡ của chữ rộng. Ba đường xoá dấu sớm, cả ba khoá ở đây:
 *  1. lượt lớn lại gặp đúng số rộng `100` ⇒ không đổi gì (review WIDGET Pass 1);
 *  2. lượt lớn lại lớn MỘT PHẦN — co ở `1000`, tới hạn gặp `100` (wave 2A ghi chú b — bản "đổi ⇒ xong" vẫn xoá);
 *  3. lượt khớp đủ do ô KHÁC chạy lúc chữ đang rộng (bản trước: `refit` xoá dấu vô điều kiện).
 * Nay [FitValues.regrowNext] hỏi "đã về ĐẦY chưa" (`FitScale.full`), không hỏi "có đổi không"; hàng NGANG chưa đầy khi chú thích
 * còn nhường ([FitValues.roomy]).
 *
 * Máy trạng thái dưới đây chép ĐÚNG thứ tự nhánh của `FitGridLayout.regrowOrShrink` + đoạn đặt dấu cuối `refit` (`:app`, ghim chuỗi
 * ở `FitRegrowWiringContractTest`) trên các phép thuần của [FitValues] — không lượt đo view nào. `full` của mô hình = chữ ở đúng cỡ
 * lưới (cùng phép so 0,01 px của `FitScale.full`).
 */
class FitRegrowRearmTest {

    /** Bề rộng giả: mọi ký tự 0,6 em, '8' rộng nhất 0,62 em (cùng mô hình `FitValuesMemoTest`). */
    private fun em(c: Char) = if (c == '8') 0.62f else 0.6f

    private fun need(text: String, px: Float): Int = FitValues.needPx(FitValues.headroom(text, '8').fold(0f) { s, c -> s + em(c) } * px)

    /** Một ô khối dọc: cỡ lưới [fit], sàn [floor], chỗ [avail] px; lượt khớp đầu ở `99` (vừa cỡ lưới). */
    private inner class Cell(val fit: Float = 30f, val floor: Float = 15f, val avail: Int = 50) {
        var px = FitValues.valuePx(fit, floor, avail) { need("99", it) }
        var shrunkAt: Long? = null
        var shown = "99"

        /** `FitScale.full` của mô hình: chữ ở đúng cỡ lưới. */
        val full: Boolean get() = abs(px - fit) <= 0.01f

        /** Một nhịp đổ tại chỗ (1 Hz) với chữ [text] lúc [now] — cùng hai nhánh của `regrowOrShrink`. */
        fun tick(text: String, now: Long) {
            val stale = text != shown
            shown = text
            if (FitValues.regrowDue(shrunkAt, now)) {
                px = FitValues.valuePx(fit, floor, avail) { need(text, it) }          // luật giá trị tick = false
                shrunkAt = FitValues.regrowNext(full, now)
            } else if (stale && !FitValues.holds(px, avail) { need(text, it) }) {
                px = FitValues.valuePx(fit, floor, avail) { need(text, it) }          // nhịp chỉ CO
                if (shrunkAt == null) shrunkAt = now
            }
        }

        /** Lượt khớp ĐỦ lúc [now] do ô khác / đổi khung (ô này KHÔNG đo dò lại — dấu chữ đã dò giữ nguyên): luật giá trị + đặt dấu. */
        fun refit(now: Long) {
            px = FitValues.valuePx(fit, floor, avail) { need(shown, it) }
            shrunkAt = FitValues.regrowNext(full, now)
        }
    }

    @Test
    fun `luot lon lai gap so rong - giu dau, hen lai, roi lon lai khi chu hep`() {
        val c = Cell()
        val full = c.px
        c.tick("100", 1_000L)
        val small = c.px
        assertTrue(small < full, "100 không vừa ⇒ co")
        assertEquals(1_000L, c.shrunkAt)
        // Tới hạn đúng lúc chữ lại là 100 ⇒ không có gì để lớn ⇒ GIỮ dấu, hẹn từ mốc này (bản trước: xoá dấu).
        val due = 1_000L + FitRules.RECHECK_MS
        c.tick("100", due)
        assertEquals(small, c.px)
        assertEquals(due, c.shrunkAt, "chưa đầy ⇒ hẹn lại, không xoá")
        // Về 99 trước hạn mới ⇒ nhịp GIỮ cỡ (không nhảy cỡ mỗi nhịp — luật soát 6).
        c.tick("99", due + 5_000L)
        assertEquals(small, c.px)
        // Hạn mới tới, chữ đang là 99 ⇒ lớn lại đúng cỡ lưới, dấu xoá.
        c.tick("99", due + FitRules.RECHECK_MS)
        assertEquals(full, c.px, "99 không còn đứng mãi ở cỡ của 100")
        assertNull(c.shrunkAt)
    }

    /**
     * Wave 2A Pass 1 ghi chú (b) [P3]: số đo của mô hình — cỡ lưới 30 px; `100` vừa ở 28/32 (26,25 px); `1000` vừa ở 21/32
     * (19,6875 px). Lượt lớn lại ở `100` ĐỔI cỡ (19,6875 → 26,25) nhưng chưa về 30 ⇒ bản "đổi ⇒ xong" xoá dấu ⇒ `99` đứng mãi ở
     * 26,25. Thử đỏ: trả [FitValues.regrowNext] về "đổi ⇒ null" ⇒ bài này đỏ ở `assertEquals(31_000L, …)`.
     */
    @Test
    fun `1000 - 100 - 99 - luot lon lai MOT PHAN giu dau, 99 ve du co luoi`() {
        val c = Cell()
        assertEquals(30f, c.px, "99 vừa cỡ lưới")
        c.tick("1000", 1_000L)
        assertEquals(30f * 21 / 32, c.px, 1e-4f, "1000 co tới bậc 21/32")
        c.tick("100", 2_000L)
        assertEquals(30f * 21 / 32, c.px, 1e-4f, "100 còn vừa cỡ của 1000 ⇒ nhịp GIỮ (chỉ co)")
        // Tới hạn đúng lúc chữ là 100: lớn MỘT PHẦN — đổi cỡ nhưng chưa ĐẦY.
        c.tick("100", 1_000L + FitRules.RECHECK_MS)
        assertEquals(30f * 28 / 32, c.px, 1e-4f, "lớn lại tới cỡ tự nhiên của 100")
        assertFalse(c.full)
        assertEquals(31_000L, c.shrunkAt, "lớn một phần ⇒ GIỮ dấu, hẹn lại (bản cũ: đổi ⇒ xoá)")
        c.tick("99", 32_000L)
        assertEquals(30f * 28 / 32, c.px, 1e-4f, "99 trước hạn ⇒ nhịp giữ cỡ của 100")
        c.tick("99", 31_000L + FitRules.RECHECK_MS)
        assertEquals(30f, c.px, "99 về ĐÚNG cỡ lưới")
        assertNull(c.shrunkAt, "đầy ⇒ xong, không còn lượt thừa")
    }

    /**
     * Đường xoá dấu thứ 3 [SUY đọc mã `FitGridLayout.refit` + `FitRules.Cell.check`]: ô dò ở `99`, nhịp `100` co; lượt khớp ĐỦ do ô
     * khác chạy lúc chữ còn `100` (chưa tới lượt dò lại ô này ⇒ dấu chữ đã dò vẫn của `99`) — bản trước xoá dấu ⇒ chữ về `99` trùng dấu
     * đã dò ⇒ không lượt dò lại, không lượt lớn lại ⇒ đứng ở cỡ của `100` tới lượt khớp kế (khung không có ô nào khác đổi chữ: mãi).
     */
    @Test
    fun `luot khop du do o khac luc chu rong - giu dau, 99 ve du co luoi`() {
        val c = Cell()
        c.tick("100", 1_000L)
        val small = c.px
        assertTrue(small < 30f)
        c.refit(5_000L)
        assertEquals(small, c.px, "lượt khớp đủ ở 100: cỡ tự nhiên của 100")
        assertEquals(5_000L, c.shrunkAt, "chưa đầy ⇒ lượt khớp GIỮ dấu (bản cũ: null)")
        c.tick("99", 6_000L)
        assertEquals(small, c.px, "nhịp giữ cỡ")
        c.tick("99", 5_000L + FitRules.RECHECK_MS)
        assertEquals(30f, c.px)
        assertNull(c.shrunkAt)
    }

    @Test
    fun `luot khop du o da day - khong dat dau, khong ton luot thua`() {
        val c = Cell()
        c.tick("100", 1_000L)
        c.tick("99", 2_000L)
        c.refit(3_000L)
        assertEquals(30f, c.px)
        assertNull(c.shrunkAt, "ô đầy ⇒ không chờ lớn lại")
    }

    @Test
    fun `regrowNext - day thi xong, chua day thi hen lai tu bay gio`() {
        assertNull(FitValues.regrowNext(full = true, nowMs = 42L))
        assertEquals(42L, FitValues.regrowNext(full = false, nowMs = 42L))
    }

    @Test
    fun `chu rong mai - thu lai thua moi RECHECK_MS, khong moi nhip`() {
        val c = Cell()
        c.tick("100", 0L)
        var runs = 0
        for (t in 1L..(3 * FitRules.RECHECK_MS / 1_000L)) {
            val before = c.shrunkAt
            c.tick("100", t * 1_000L)
            if (c.shrunkAt != before) runs++
        }
        assertEquals(3, runs, "một lượt lớn lại (rẻ, không đo view) mỗi ${FitRules.RECHECK_MS} ms — không phải mỗi nhịp 1 Hz")
    }

    /**
     * [FitValues.roomy] = nhánh 1 của [FitValues.share] (chú thích nhận TRỌN nhu cầu) trên MỌI ô của lưới tham số. Đối chiếu hộp đen:
     * chú thích còn hiện khi đòi sàn = chính nhu cầu của nó (`captionMin = captionNeed` ⇒ nhánh 2 không xảy ra); không có chú thích có
     * nhu cầu ⇒ luôn `true`.
     */
    @Test
    fun `roomy - dung nhanh 1 cua share tren moi o tham so`() {
        for (flex in -5..160) for (v in listOf(0, 12, 40, 57, 90, 170)) for (cn in listOf(0, 9, 40, 55)) for (min in listOf(0, 18, 30))
            for (gap in listOf(0, 4, 8)) {
                val s = FitValues.share(flex, v, cn, min, gap)
                val oracle = cn <= 0 || FitValues.share(flex, v, cn, cn, gap).captions
                assertEquals(oracle, FitValues.roomy(s, flex, cn, gap), "flex=$flex v=$v cn=$cn min=$min gap=$gap ⇒ $s")
            }
    }

    /**
     * Hàng NGANG — chỗ nhịp đang GIỮ (cùng cách `FitValueRow.roomy` dựng `Share(valueW, !yielded)`): giá trị `1000` đã chiếm chỗ,
     * chữ về `99` không trả chỗ lại (nhịp chỉ chia lại khi SẼ cắt) ⇒ chú thích vẫn `…`/ẩn ⇒ CHƯA đầy; chia lại cho `99` ⇒ đầy.
     */
    @Test
    fun `roomy - cho dang giu cua so rong lam chu thich nhuong thi chua day`() {
        val flex = 100; val cap = 40; val min = 30; val gap = 8
        val wide = FitValues.share(flex, 60, cap, min, gap)              // 1000: còn 32 ≥ 30 ⇒ chú thích `…`
        assertEquals(FitValues.Share(60, true), wide)
        assertFalse(FitValues.roomy(wide, flex, cap, gap), "chú thích chỉ còn 32 < 40 ⇒ chưa đầy")
        assertFalse(FitValues.wouldClip(wide.valueW, 40), "99 vừa chỗ của 1000 ⇒ nhịp KHÔNG chia lại (giữ chỗ)")
        val narrow = FitValues.share(flex, 40, cap, min, gap)            // lượt lớn lại ở 99
        assertTrue(FitValues.roomy(narrow, flex, cap, gap), "chia lại cho 99 ⇒ chú thích trọn ⇒ đầy")
        assertFalse(FitValues.roomy(FitValues.Share(flex, captions = false), flex, cap, gap), "chú thích đang NHƯỜNG (ẩn) ⇒ chưa đầy")
        assertTrue(FitValues.roomy(FitValues.Share(flex, captions = false), flex, 0, gap), "không chú thích có nhu cầu ⇒ đầy")
    }
}
