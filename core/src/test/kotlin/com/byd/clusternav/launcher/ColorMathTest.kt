package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.Wcag
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * P1b · R8 — [ColorMath] phải cho **cùng con số** với `Wcag` (testFixtures): hai bản của một công thức mà lệch nhau là
 * bảng đo nói một đằng, màn hình vẽ một nẻo.
 */
class ColorMathTest {

    private val samples = listOf(
        "#0a0d13", "#eaf0f8", "#4c7dff", "#9daabe", "#ffffff", "#000000", "#cc232a37", "#804c7dff", "#1a34d399",
    )

    @Test
    fun `parse va hex di vong tron`() {
        samples.forEach { assertEquals(it, ColorMath.hex(ColorMath.parse(it)), it) }
    }

    @Test
    fun `luminance ratio over khop Wcag tung don vi`() {
        samples.forEach { a ->
            assertEquals(Wcag.luminance(a), ColorMath.luminance(ColorMath.parse(a)), 1e-12, "luminance $a")
            samples.forEach { b ->
                assertEquals(Wcag.over(a, b), ColorMath.hex(ColorMath.over(ColorMath.parse(a), ColorMath.parse(b))), "over $a/$b")
                if (Wcag.argb(b)[0] == 255) {
                    assertEquals(Wcag.ratio(a, b), ColorMath.ratio(ColorMath.parse(a), ColorMath.parse(b)), 1e-9, "ratio $a/$b")
                }
            }
        }
    }

    @Test
    fun `withAlpha va scaleAlpha chi dong vao kenh alpha`() {
        val c = ColorMath.parse("#4c7dff")
        assertEquals("#804c7dff", ColorMath.hex(ColorMath.withAlpha(c, 0x80)))
        assertEquals("#cc4c7dff", ColorMath.hex(ColorMath.scaleAlpha(c, 0.8)))
        assertEquals("#664c7dff", ColorMath.hex(ColorMath.scaleAlpha(ColorMath.parse("#804c7dff"), 0.8)))
    }

    @Test
    fun `grayOfLuminance dao nguoc duoc luminance`() {
        listOf(0.0, 0.05, 0.18, 0.5, 0.9, 1.0).forEach { l ->
            assertEquals(l, ColorMath.luminance(ColorMath.grayOfLuminance(l)), 0.01, "l=$l")
        }
    }

    @Test
    fun `hsl di vong tron va recolor giu alpha`() {
        samples.forEach { s ->
            val c = ColorMath.parse(s)
            val h = ColorMath.hsl(c)
            val back = ColorMath.fromHsl(h[0], h[1], h[2], ColorMath.alpha(c))
            listOf(ColorMath.red(c) to ColorMath.red(back), ColorMath.green(c) to ColorMath.green(back), ColorMath.blue(c) to ColorMath.blue(back))
                .forEach { (a, b) -> assertTrue(kotlin.math.abs(a - b) <= 2, "$s: $a vs $b") }
        }
        val base = ColorMath.parse("#4c7dff")
        val role = ColorMath.parse("#804c7dff")
        val seed = ColorMath.parse("#f5b73d")
        val out = ColorMath.recolor(role, base, seed)
        assertEquals(0x80, ColorMath.alpha(out), "alpha của vai phải giữ nguyên")
        assertTrue(ColorMath.hueDistance(out, seed) < 5.0, "sắc phải theo hạt giống")
        // Hạt giống trùng gốc ⇒ vai gần như không đổi (sai số làm tròn HSL).
        val same = ColorMath.recolor(role, base, base)
        assertTrue(kotlin.math.abs(ColorMath.red(same) - ColorMath.red(role)) <= 2)
    }

    @Test
    fun `mix 0 va 1 tra ve hai dau`() {
        val a = ColorMath.parse("#232a37"); val b = ColorMath.parse("#ff9a4a")
        assertEquals(a, ColorMath.mix(a, b, 0.0))
        assertEquals(ColorMath.withAlpha(b, 255), ColorMath.mix(a, b, 1.0))
    }
}
