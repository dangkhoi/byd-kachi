package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.ceil

/**
 * 2.93 — hai việc tách từ soát 6–7 của `WIDGET-FIT-ALL` (spec 287 §4.5), luật thuần ở [FitValues]:
 *  - `FIT-WIDEST-CACHE`: mỗi lần đo bề rộng một giá trị tốn 11 `measureText` + 10 chuỗi [ĐO mã]; nay chữ số rộng nhất nhớ theo
 *    bút ([FitValues.WidestMemo]) + chuỗi đệm nhớ một lần ([FitValues.HeadroomMemo]) — kết quả Y HỆT, số lần đo giảm;
 *  - `FIT-REGROW`: khối DỌC chỉ co ở nhịp ⇒ `99 → 100 → 99` đứng mãi ở cỡ của `100` [ĐO mã, soát 7]; nay
 *    [FitValues.regrowDue] mở một lượt không-phải-nhịp sau [FitRules.RECHECK_MS].
 */
class FitValuesMemoTest {

    /** Bề rộng giả: mọi ký tự 0,6 em, riêng '8' rộng nhất 0,62 em (chữ số rộng nhất của phông thật thường là 0/8). */
    private fun em(c: Char) = if (c == '8') 0.62f else 0.6f

    @Test
    fun `nho chu so rong nhat theo but - ket qua y het, chi do mot lan moi but`() {
        var calls = 0
        val memo = FitValues.WidestMemo<Pair<String, Float>>(capacity = 2)
        fun widest(face: String, px: Float) = memo.get(face to px) { c -> calls++; em(c) * px }
        assertEquals(FitValues.widestDigit { em(it) * 20f }, widest("a", 20f))
        assertEquals(10, calls, "lần đầu: đo 10 chữ số")
        repeat(100) { widest("a", 20f) }
        assertEquals(10, calls, "nhịp sau cùng bút: 0 lần đo chữ số")
        widest("a", 21f); widest("b", 20f)
        assertEquals(30, calls)
        widest("a", 20f)
        assertEquals(40, calls, "LRU 2 khoá: ('a',20) đã bị đẩy ra ⇒ đo lại (không lớn vô hạn)")
        assertEquals(4, memo.misses)
    }

    @Test
    fun `chuoi dem nho mot lan gan nhat`() {
        val m = FitValues.HeadroomMemo()
        val a = m.of("06:59", '8')
        assertEquals("88:88", a)
        assertSame(a, m.of("06:59", '8'), "cùng chữ + cùng chữ số rộng ⇒ cùng chuỗi, không dựng lại")
        assertEquals("888", m.of("100", '8'))
        assertEquals("000", m.of("100", '0'))
    }

    private fun need(text: String, px: Float): Int = FitValues.needPx(FitValues.headroom(text, '8').fold(0f) { s, c -> s + em(c) } * px)

    @Test
    fun `99 - 100 - 99 khong con dung mai o co cua 100`() {
        val fit = 30f; val floor = 15f; val avail = 50
        var px = FitValues.valuePx(fit, floor, avail) { need("99", it) }
        assertEquals(fit, px, "99 vừa ở cỡ lưới")
        // Nhịp: 100 không còn vừa ⇒ CO, ghi mốc.
        assertFalse(FitValues.holds(px, avail) { need("100", it) })
        px = FitValues.valuePx(fit, floor, avail) { need("100", it) }
        assertTrue(px < fit && need("100", px) <= avail)
        val shrunkAt = 1_000L
        // Nhịp: về 99 — vừa ở cỡ đang có ⇒ GIỮ (luật soát 6, không nhảy cỡ mỗi nhịp).
        assertTrue(FitValues.holds(px, avail) { need("99", it) })
        assertFalse(FitValues.regrowDue(shrunkAt, shrunkAt + FitRules.RECHECK_MS - 1), "chưa tới hạn ⇒ vẫn giữ")
        assertFalse(FitValues.regrowDue(null, Long.MAX_VALUE), "không bị co ⇒ không bao giờ chạy")
        // Tới hạn ⇒ một lượt không-phải-nhịp ⇒ lớn lại đúng cỡ lưới.
        assertTrue(FitValues.regrowDue(shrunkAt, shrunkAt + FitRules.RECHECK_MS))
        assertEquals(fit, FitValues.valuePx(fit, floor, avail) { need("99", it) }, "lớn lại")
        assertEquals(ceil(2 * 0.62 * 30).toInt() + 1, need("99", fit))
    }
}
