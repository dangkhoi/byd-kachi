package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** P1b · AC8.5 — [ContrastGuard]: đổi mực trước, đẩy nền sau, không có nhánh im lặng. */
class ContrastGuardTest {

    private val white = ColorMath.parse("#ffffff")
    private val dark = ColorMath.parse("#0a0d13")
    private val ink = ColorMath.parse("#eaf0f8")

    @Test
    fun `cap da dat san thi giu nguyen`() {
        val g = intArrayOf(ColorMath.parse("#3f6ae0"), ColorMath.parse("#6b4ce6"))
        val fit = ContrastGuard.fit(white, g, pushToward = dark, altInks = listOf(dark))
        assertTrue(fit.ok); assertEquals(0, fit.steps); assertFalse(fit.swapped)
        assertTrue(fit.grounds.contentEquals(g))
    }

    @Test
    fun `nen sang thi doi muc truoc, khong day nen`() {
        // "Trắng ấm" làm nền nút: chữ trắng không đọc được ⇒ mực đậm thay thế, nền GIỮ NGUYÊN (màu người dùng chọn).
        val warm = intArrayOf(ColorMath.parse("#f3e9dc"), ColorMath.parse("#e6d9c8"))
        val fit = ContrastGuard.fit(white, warm, pushToward = dark, altInks = listOf(dark))
        assertTrue(fit.ok, "worst=${fit.worst}")
        assertTrue(fit.swapped); assertEquals(dark, fit.ink); assertEquals(0, fit.steps)
    }

    @Test
    fun `khong muc nao du thi day nen tung bac toi khi dat`() {
        // Xám giữa #797979 (L≈0.191): trắng 4.36 · nền màn tối 4.43 — KHÔNG mực nào đủ ⇒ phải đẩy nền.
        val mid = intArrayOf(ColorMath.parse("#797979"))
        val fit = ContrastGuard.fit(white, mid, pushToward = dark, altInks = listOf(dark))
        assertTrue(fit.ok, "worst=${fit.worst}")
        assertTrue(fit.steps in 1..ContrastGuard.MAX_STEPS)
        assertTrue(ColorMath.luminance(fit.grounds[0]) < ColorMath.luminance(mid[0]), "nền phải tối đi về phía nền màn")
    }

    @Test
    fun `het bac ma chua dat thi noi ra, khong im lang`() {
        val mid = intArrayOf(ColorMath.parse("#797979"))
        val fit = ContrastGuard.fit(white, mid, pushToward = ColorMath.parse("#7a7a7a"), maxSteps = 2)
        assertFalse(fit.ok)
        assertTrue(fit.worst > 0)
    }

    @Test
    fun `fitAlpha doi it nhat co the`() {
        val under = ColorMath.parse("#242a34")
        val role = ColorMath.parse("#804c7dff")
        val okFit = ContrastGuard.fitAlpha(ColorMath.parse("#e7ecff"), role, under)
        assertTrue(okFit.ok); assertEquals(role, okFit.role)
        // Vai quá sáng cho mực sáng ⇒ hoặc đổi mực (đen) hoặc hạ alpha; kết quả phải đạt sàn.
        val bright = ColorMath.parse("#f0f3e9dc")
        val fix = ContrastGuard.fitAlpha(ink, bright, under, altInks = listOf(dark))
        assertTrue(fix.ok, "worst=${fix.worst}")
    }

    @Test
    fun `fitInk keo muc ve phia muc chinh cho toi khi dat`() {
        val grounds = intArrayOf(ColorMath.parse("#242a34"), ColorMath.parse("#141922"))
        val weak = ColorMath.parse("#5a6a99")
        val fixed = ContrastGuard.fitInk(weak, grounds, toward = ink)
        assertTrue(grounds.all { ColorMath.ratio(fixed, it) >= 4.5 })
        val strong = ColorMath.parse("#7ba0ff")
        assertEquals(strong, ContrastGuard.fitInk(strong, grounds, toward = ink), "mực đã đạt thì không được chạm")
    }
}
