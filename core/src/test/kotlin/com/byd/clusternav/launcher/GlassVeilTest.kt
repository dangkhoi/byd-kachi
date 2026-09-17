package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** P1b · §4.10 mục (5) — lớp che chọn theo độ chói đo được: đơn điệu, trong dải, và đủ cho mọi độ chói. */
class GlassVeilTest {

    private val veil = ColorMath.parse("#0a0d13")
    private val surfaces = intArrayOf(ColorMath.parse("#cc232a37"), ColorMath.parse("#cc0f131a"))
    private val inks = intArrayOf(ColorMath.parse("#9daabe"), ColorMath.parse("#99a4b6"), ColorMath.parse("#eaf0f8"))

    @Test
    fun `anh toi thi lop che o day dai, anh sang thi day hon`() {
        val darkArt = GlassVeil.alphaFor(0.02, veil, surfaces, inks)
        val brightArt = GlassVeil.alphaFor(0.95, veil, surfaces, inks)
        assertEquals(GlassVeil.MIN, darkArt, 1e-9)
        assertTrue(brightArt > darkArt, "ảnh sáng phải cần lớp che dày hơn: $brightArt vs $darkArt")
    }

    @Test
    fun `don dieu theo do choi va luon trong dai`() {
        var prev = 0.0
        var l = 0.0
        while (l <= 1.0) {
            val a = GlassVeil.alphaFor(l, veil, surfaces, inks)
            assertTrue(a >= GlassVeil.MIN - 1e-9 && a <= GlassVeil.MAX + 1e-9, "l=$l a=$a")
            assertTrue(a >= prev - 1e-9, "không đơn điệu ở l=$l: $a < $prev")
            prev = a; l += 0.05
        }
    }

    @Test
    fun `alpha tim duoc thuc su dua muc te nhat qua san, ke ca anh trang tinh`() {
        var l = 0.0
        while (l <= 1.0) {
            val a = GlassVeil.alphaFor(l, veil, surfaces, inks)
            val worst = GlassVeil.worstRatio(a, ColorMath.grayOfLuminance(l), veil, surfaces, inks)
            assertTrue(worst >= 4.5, "l=$l a=$a worst=$worst")
            l += 0.05
        }
    }
}
