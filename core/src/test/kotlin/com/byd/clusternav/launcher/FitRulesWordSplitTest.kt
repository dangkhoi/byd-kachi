package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 3.01 R20 — [FitRules.splitsWord]: xuống dòng GIỮA một từ của chữ viết có dấu cách là chữ bị vỡ (không đọc được).
 * Ca gốc [ĐO uiautomator 13/10, `docs/diagnostics/widget-fit-301-2026-10-13.md`]: `w_board` khung 2×6 hiện chú thích
 * `Áp suất lốp (bar)` thành 5 dòng `Áp/suất/lốp/(bar/)`.
 */
class FitRulesWordSplitTest {

    @Test
    fun `be giua tu tieng Viet, tieng Anh, ngoac la vo chu`() {
        assertTrue(FitRules.splitsWord("Thời gian", 2), "Th / ời gian")
        assertTrue(FitRules.splitsWord("Áp suất lốp (bar)", 16), "(bar / )")
        assertTrue(FitRules.splitsWord("Window FL", 3))
        assertTrue(FitRules.splitsWord("PM2.5", 3), "PM2 / .5")
    }

    @Test
    fun `ngat o dau cach, sau gach noi, sau gach cheo la hop le`() {
        assertFalse(FitRules.splitsWord("Thời gian", 5), "dòng mới bắt đầu sau dấu cách")
        assertFalse(FitRules.splitsWord("Thời gian", 4), "dòng mới bắt đầu bằng dấu cách")
        assertFalse(FitRules.splitsWord("trước-trái", 6), "sau gạch nối")
        assertFalse(FitRules.splitsWord("km/h", 3), "sau gạch chéo")
    }

    @Test
    fun `chu Thai, chu Han xuong dong giua hai chu cai la binh thuong`() {
        assertFalse(FitRules.splitsWord("ความดันลมยาง", 3))
        assertFalse(FitRules.splitsWord("胎压监测", 2))
    }

    @Test
    fun `bien - khong vo`() {
        assertFalse(FitRules.splitsWord("ab", 0))
        assertFalse(FitRules.splitsWord("ab", 2))
        assertFalse(FitRules.splitsWord("", 0))
    }
}

/** 3.01 R20 — [FitRules.tooManyLines]: chú thích tự do tối đa 2 dòng (ca `w_board` 4 dòng trong 55 px). */
class FitRulesFreeLinesTest {
    @Test
    fun `chu tu do qua hai dong la vo, chu da khai maxLines khong xet`() {
        assertTrue(FitRules.tooManyLines(Int.MAX_VALUE, 4))
        assertFalse(FitRules.tooManyLines(Int.MAX_VALUE, 2))
        assertFalse(FitRules.tooManyLines(3, 3))
    }
}
