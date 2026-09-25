package com.byd.clusternav.modules.navaccess

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * B1 (BG-11/BG-14): đã bound theo AccessibilityManager ⇒ **0 lệnh shell**; chưa bound / không hỏi được ⇒ đường shell
 * chạy ĐỦ (không mất tự-heal 1.78). Alarm chỉ heal khi FGS keep-alive KHÔNG sống.
 *
 * Đỏ→xanh: đổi `boundPerManager == true` thành `!= false` ⇒ ca `null` bỏ shell ⇒ ĐỎ; bỏ `!inProcessWatchdogAlive`
 * ⇒ ca "FGS sống" ĐỎ.
 */
class AccessibilityHealGatesTest {

    private class Counter { var shell = 0; var skipped = 0 }

    private fun run(bound: Boolean?): Counter {
        val c = Counter()
        AccessibilityHealGates.grantOrSkip(bound, skipped = { c.skipped++ }, shell = { c.shell++ })
        return c
    }

    @Test
    fun `da bound theo AccessibilityManager thi 0 lenh shell`() {
        val c = run(true)
        assertEquals(0, c.shell, "đã bound ⇒ không mở phiên dadb, không ghi Secure Settings")
        assertEquals(1, c.skipped)
    }

    @Test
    fun `chua bound thi duong shell cu chay du`() {
        val c = run(false)
        assertEquals(1, c.shell, "chưa bound ⇒ đường dadb (get → put → verify dumpsys → toggle) chạy như cũ")
        assertEquals(0, c.skipped)
    }

    @Test
    fun `binder khong tra loi duoc (null) thi KHONG duoc bo shell`() {
        // Không được coi "không hỏi được" là "đã bound" — mất đường tự-heal là lỗi on-car 1.78 quay lại.
        val c = run(null)
        assertEquals(1, c.shell)
        assertEquals(0, c.skipped)
    }

    @Test
    fun `alarm chi heal khi phim-thoai BAT va FGS keep-alive KHONG song`() {
        assertTrue(AccessibilityHealGates.alarmShouldHeal(voiceKeyEnabled = true, inProcessWatchdogAlive = false), "FGS chết ⇒ alarm là lưới cuối")
        assertFalse(AccessibilityHealGates.alarmShouldHeal(voiceKeyEnabled = true, inProcessWatchdogAlive = true), "FGS sống ⇒ watchdog 30 s đã lo, alarm no-op")
        assertFalse(AccessibilityHealGates.alarmShouldHeal(voiceKeyEnabled = false, inProcessWatchdogAlive = false), "phím-thoại tắt ⇒ không heal")
        assertFalse(AccessibilityHealGates.alarmShouldHeal(voiceKeyEnabled = false, inProcessWatchdogAlive = true))
    }
}
