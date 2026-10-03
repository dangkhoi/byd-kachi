package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 2.87 · R-OP — số học của độ đục nền chung ([ChromeOpacity]): bậc, phép kẹp, và bất biến "không bao giờ đục hơn hôm
 * nay, không bao giờ trong hơn mức cần để đọc".
 */
class ChromeOpacityTest {

    @Test
    fun `nam bac, mac dinh 100 dung dau, giam dan, khong duoi 40`() {
        assertEquals(listOf(100, 85, 70, 55, 40), ChromeOpacity.STEPS)
        assertEquals(100, ChromeOpacity.DEFAULT)
        assertEquals(ChromeOpacity.DEFAULT, ChromeOpacity.STEPS.first(), "chip đầu = hôm nay")
        assertTrue(ChromeOpacity.STEPS.zipWithNext().all { (a, b) -> a > b }, "giảm dần, không trùng")
        assertTrue(ChromeOpacity.STEPS.min() >= 40, "OQ2: bậc thấp nhất 40 % (dưới ~20 % bảng sáng hụt 4.5:1)")
    }

    @Test
    fun `snap ve bac gan nhat va kep hai dau`() {
        val table = mapOf(
            100 to 100, 85 to 85, 70 to 70, 55 to 55, 40 to 40,
            999 to 100, 101 to 100, 93 to 100, 92 to 85, 78 to 85, 77 to 70, 63 to 70, 62 to 55, 48 to 55, 47 to 40,
            0 to 40, -5 to 40,
        )
        table.forEach { (pct, want) -> assertEquals(want, ChromeOpacity.snap(pct), "snap($pct)") }
        ChromeOpacity.STEPS.forEach { assertEquals(it / 100.0, ChromeOpacity.fraction(it), 1e-12) }
        // Chip hiện độ TRONG SUỐT (owner: "chỉnh độ transparent"): 0 % = hôm nay, tăng dần theo thứ tự chip.
        assertEquals(listOf(0, 15, 30, 45, 60), ChromeOpacity.STEPS.map { ChromeOpacity.transparencyPct(it) })
        assertEquals(0.4, ChromeOpacity.fraction(7), 1e-12, "fraction đi qua snap")
    }

    @Test
    fun `f bang 1 tra dung base, khong qua phep tinh nao`() {
        listOf(0.0, 0.33, 0.6, 0.85, 0.9, 1.0).forEach { base ->
            listOf(null, 0.0, 0.5, 0.95, 1.0).forEach { needed ->
                assertEquals(base, ChromeOpacity.effective(base, 1.0, needed), "base=$base needed=$needed")
                assertEquals(base, ChromeOpacity.effective(base, 1.5, needed), "f > 1 cũng là hôm nay")
            }
        }
    }

    @Test
    fun `bat bien min(base, needed) le ket qua le base, moi bac moi muc can`() {
        val bases = listOf(0.0, 0.3, 0.6, 0.85, 0.9, 1.0)
        val needs = listOf<Double?>(null, 0.0, 0.05, 0.3, 0.45, 0.6, 0.65, 0.9, 1.0)
        ChromeOpacity.STEPS.forEach { pct ->
            val f = ChromeOpacity.fraction(pct)
            bases.forEach { base ->
                needs.forEach { needed ->
                    val e = ChromeOpacity.effective(base, f, needed)
                    val floor = minOf(base, needed ?: 0.0)
                    assertTrue(e <= base + 1e-12, "đục hơn hôm nay: pct=$pct base=$base needed=$needed e=$e")
                    assertTrue(e >= floor - 1e-12, "trong hơn mức cần: pct=$pct base=$base needed=$needed e=$e")
                    assertTrue(e >= base * f - 1e-12, "không trong hơn lựa chọn: pct=$pct base=$base e=$e")
                }
            }
        }
        // Không có ảnh nền (needed = null) ⇒ đúng tỉ lệ người dùng chọn.
        assertEquals(0.6 * 0.7, ChromeOpacity.effective(0.6, 0.7, null), 1e-12)
        // Ảnh sáng cần nhiều hơn hôm nay đang có ⇒ giữ ĐÚNG hôm nay (không đục hơn, không làm tệ thêm ca vốn hụt).
        assertEquals(0.6, ChromeOpacity.effective(0.6, 0.4, 0.75), 1e-12)
    }
}
