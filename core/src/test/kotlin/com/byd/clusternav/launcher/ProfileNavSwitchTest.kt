package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 2.97 · R3 — khoá lỗi [ĐO log SL6 07/10 16:51]: đổi sang hồ sơ có công tắc "Dẫn đường lên cụm" TẮT ⇒ HUD/cụm mất dẫn đường
 * mà người lái không biết vì sao. Nhắc CHỈ khi BẬT → TẮT. Thử ĐỎ: cho `shouldNotice` trả true ở TURNED_ON hoặc SAME.
 */
class ProfileNavSwitchTest {

    @Test
    fun `bat sang tat - nhac, con lai khong`() {
        assertTrue(ProfileNavSwitch.shouldNotice(before = true, after = false))
        assertFalse(ProfileNavSwitch.shouldNotice(before = false, after = true))
        assertFalse(ProfileNavSwitch.shouldNotice(before = true, after = true))
        assertFalse(ProfileNavSwitch.shouldNotice(before = false, after = false))
    }

    @Test
    fun `nhat ky noi ro truoc sau`() {
        assertEquals(ProfileNavSwitch.Change.TURNED_OFF, ProfileNavSwitch.change(true, false))
        assertEquals(ProfileNavSwitch.Change.TURNED_ON, ProfileNavSwitch.change(false, true))
        assertEquals(ProfileNavSwitch.Change.SAME, ProfileNavSwitch.change(true, true))
        val line = ProfileNavSwitch.logLine("YOUTUBE", true, false)
        assertTrue(line.contains("BAT -> TAT") && line.contains("TURNED_OFF") && line.contains("«YOUTUBE»"), line)
    }
}
