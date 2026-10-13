package com.byd.clusternav.launcher.automation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** KACHI-301 R12 — phép tính bộ chọn giờ trong hộp lịch: quay vòng, bước 5, mốc nhanh, chip thứ nhanh. */
class TimeWheelMathTest {

    private fun t(h: Int, m: Int) = h * 60 + m

    @Test
    fun `cot gio quay vong va giu phut`() {
        assertEquals(t(8, 30), TimeWheelMath.stepHour(t(7, 30), +1))
        assertEquals(t(0, 15), TimeWheelMath.stepHour(t(23, 15), +1))
        assertEquals(t(23, 45), TimeWheelMath.stepHour(t(0, 45), -1))
    }

    @Test
    fun `cot phut buoc 5, quay vong trong gio, gio khong nhay`() {
        assertEquals(t(7, 5), TimeWheelMath.stepMinute(t(7, 0), +1))
        assertEquals(t(7, 0), TimeWheelMath.stepMinute(t(7, 55), +1))
        assertEquals(t(7, 55), TimeWheelMath.stepMinute(t(7, 0), -1))
        assertEquals(t(23, 0), TimeWheelMath.stepMinute(t(23, 55), +1))
    }

    @Test
    fun `phut le bat ve luoi mot buoc, khong nhay hai`() {
        assertEquals(t(7, 35), TimeWheelMath.stepMinute(t(7, 32), +1))
        assertEquals(t(7, 30), TimeWheelMath.stepMinute(t(7, 32), -1))
        assertEquals(t(7, 0), TimeWheelMath.stepMinute(t(7, 58), +1))
        assertEquals(t(7, 55), TimeWheelMath.stepMinute(t(7, 58), -1))
    }

    @Test
    fun `di het mot vong 12 nac phut ve cho cu, 24 nac gio ve cho cu`() {
        var m = t(9, 10)
        repeat(60 / TimeWheelMath.MINUTE_STEP) { m = TimeWheelMath.stepMinute(m, +1) }
        assertEquals(t(9, 10), m)
        var h = t(9, 10)
        repeat(24) { h = TimeWheelMath.stepHour(h, -1) }
        assertEquals(t(9, 10), h)
    }

    @Test
    fun `gia tri rac bi kep, khong hien so am`() {
        assertEquals(0, TimeWheelMath.hourOf(-1))
        assertEquals(0, TimeWheelMath.minuteOf(-1))
        assertEquals(23, TimeWheelMath.hourOf(99_999))
        assertEquals(ScheduledNavRules.MAX_MIN, TimeWheelMath.clamp(99_999))
    }

    @Test
    fun `moc nhanh dung spec`() {
        assertEquals(listOf(t(6, 0), t(7, 0), t(8, 0), t(17, 0), t(18, 0)), TimeWheelMath.PRESETS)
    }

    @Test
    fun `chip thu nhanh sang khi tap thu DUNG BANG`() {
        assertEquals(DayPreset.WEEKDAYS, DayPreset.matching(setOf(1, 2, 3, 4, 5)))
        assertEquals(DayPreset.WEEKEND, DayPreset.matching(setOf(6, 7)))
        assertEquals(DayPreset.EVERYDAY, DayPreset.matching(ScheduledNavRules.ALL_DAYS))
        assertNull(DayPreset.matching(setOf(1, 2, 3, 4, 5, 6)))
        assertNull(DayPreset.matching(emptySet()))
    }
}
