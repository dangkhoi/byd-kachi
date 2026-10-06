package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 2.93 `WIDGET-CAR-STRIP-LAYOUT` — [ĐO máy ảo 06/10] khung rộng thấp 1558×123 px: hình xe chỉ ~52 px cao vì chú thích xếp
 * DƯỚI hình. Số ở mật độ 1,5 như đầu xe: lề + khe 8 dp = 12 px, chú thích 13 sp ≈ 23 px cao, chữ dài nhất ≈ 120 px.
 */
class CarStripFitTest {

    private val pad = 12; private val gap = 12; private val capW = 120; private val capH = 23; private val a = 0.46

    private fun plan(w: Int, h: Int) = CarStripFit.plan(w, h, pad, gap, capW, capH, a)

    @Test
    fun `dai rong thap - hinh CANH chu thich, cao tron khung`() {
        val p = plan(1558, 123)
        assertEquals(CarStripFit.Arrange.SIDE, p.arrange)
        assertEquals(99.0, p.carH, 1e-9, "cao trọn 123 − 2×12 (trước: 99 − 12 − 23 = 64)")
        assertEquals(46, p.artW, "hộp hình vừa đúng hình (⌈99 × 0,46⌉) ⇒ cụm hình + chữ căn giữa")
        assertEquals(CarStripFit.Arrange.SIDE, plan(1872, 123).arrange)
        assertEquals(CarStripFit.Arrange.SIDE, plan(929, 148).arrange)
    }

    @Test
    fun `khung dung, vuong, to - giu dang 2_92`() {
        listOf(301 to 804, 262 to 956, 400 to 400, 1558 to 668, 929 to 395, 640 to 360).forEach { (w, h) ->
            val p = plan(w, h)
            assertEquals(CarStripFit.Arrange.STACK, p.arrange, "${w}x$h")
            assertEquals(w - 2 * pad, p.artW, "${w}x$h: hộp hình rộng trọn như col 2.92")
            assertEquals(h - 2 * pad - gap - capH, p.artH, "${w}x$h: chú thích dưới")
        }
    }

    @Test
    fun `tinh chat - SIDE chi khi hinh to hon ro ret, khong bao gio nho di`() {
        for (w in 60..2000 step 17) for (h in 40..1100 step 13) {
            val p = plan(w, h)
            val stackCar = minOf((h - 2 * pad - gap - capH).coerceAtLeast(0).toDouble(), (w - 2 * pad).coerceAtLeast(0) / a)
            if (p.arrange == CarStripFit.Arrange.SIDE) {
                assertTrue(p.carH >= stackCar * (1 + CarStripFit.GAIN) - 1e-9, "${w}x$h: SIDE phải cho hình to hơn ≥ 25 %")
                assertTrue(p.artW + gap + capW <= w - 2 * pad, "${w}x$h: cụm không tràn")
                assertTrue(p.artH <= h - 2 * pad, "${w}x$h")
            } else {
                assertEquals(stackCar, p.carH, 1e-9, "${w}x$h")
            }
        }
        assertEquals(CarStripFit.Arrange.STACK, CarStripFit.plan(0, 0, pad, gap, capW, capH, a).arrange, "khung chưa đo")
    }
}
