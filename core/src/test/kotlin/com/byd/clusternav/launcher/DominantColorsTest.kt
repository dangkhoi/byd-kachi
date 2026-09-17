package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** P1b · §4.10 mục (4) — màu trội: ưu tiên màu rực, không trùng sắc, không bịa. */
class DominantColorsTest {

    private fun fill(n: Int, c: Int) = IntArray(n) { c }

    @Test
    fun `anh rong tra rong, anh mot mau tra mot mau`() {
        assertEquals(0, DominantColors.of(IntArray(0)).size)
        val one = DominantColors.of(fill(100, ColorMath.parse("#3f6ae0")))
        assertEquals(1, one.size, "không bịa màu thứ hai")
        assertTrue(ColorMath.hueDistance(one[0], ColorMath.parse("#3f6ae0")) < 8.0)
    }

    @Test
    fun `mau ruc it diem van thang mau xam nhieu diem`() {
        val sand = ColorMath.parse("#c9b79c")      // 65 % ảnh: cát (bão hoà thấp)
        val sea = ColorMath.parse("#1e6fd9")       // 35 % ảnh: biển
        val px = fill(650, sand) + fill(350, sea)
        val dom = DominantColors.of(px, 3)
        assertTrue(dom.size >= 2)
        assertTrue(ColorMath.hueDistance(dom[0], sea) < 8.0, "màu nhớ được là biển, không phải cát")
        // 85/15 thì ảnh đó LÀ ảnh cát: cát đứng đầu, biển vẫn có mặt (hạt giống màu nhấn lấy màu RỰC nhất).
        val mostlySand = DominantColors.of(fill(850, sand) + fill(150, sea), 3)
        assertTrue(ColorMath.hueDistance(mostlySand[0], sand) < 8.0)
        assertTrue(mostlySand.any { ColorMath.hueDistance(it, sea) < 8.0 })
        val seed = DominantColors.accentSeed(dom, dark = true)
        assertNotNull(seed)
        assertTrue(ColorMath.hueDistance(seed!!, sea) < 8.0)
        val l = ColorMath.hsl(seed)[2]
        // Dải kẹp là 0.55..0.72; ±0.02 cho làm tròn HSL → RGB 8-bit → HSL.
        assertTrue(l in 0.53..0.74, "hạt giống bảng tối phải ở bậc sáng đọc được: $l")
    }

    @Test
    fun `hai bac cua cung mot mau khong duoc thanh hai mau troi`() {
        val a = ColorMath.parse("#2f5ae0"); val b = ColorMath.parse("#4c7dff")
        val dom = DominantColors.of(fill(500, a) + fill(500, b), 3)
        assertEquals(1, dom.size, "cùng sắc (lệch < 30°) ⇒ một màu")
    }

    @Test
    fun `accentSeed rong khi khong co mau`() {
        assertNull(DominantColors.accentSeed(IntArray(0), dark = true))
    }
}
