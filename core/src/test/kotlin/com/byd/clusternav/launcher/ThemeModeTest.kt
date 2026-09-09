package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ThemeModeTest {

    @Test
    fun `night is always night and day is never night`() {
        assertTrue(ThemeMode.NIGHT.isNight(12))
        assertFalse(ThemeMode.DAY.isNight(2))
    }

    @Test
    fun `auto is night before 6 and from 18, day in between`() {
        assertTrue(ThemeMode.AUTO.isNight(5))
        assertTrue(ThemeMode.AUTO.isNight(23))
        assertTrue(ThemeMode.AUTO.isNight(18))
        assertFalse(ThemeMode.AUTO.isNight(6))
        assertFalse(ThemeMode.AUTO.isNight(12))
    }

    @Test
    fun `next cycles DAY to NIGHT to AUTO to DAY`() {
        assertEquals(ThemeMode.NIGHT, ThemeMode.DAY.next())
        assertEquals(ThemeMode.AUTO, ThemeMode.NIGHT.next())
        assertEquals(ThemeMode.DAY, ThemeMode.AUTO.next())
    }
}
