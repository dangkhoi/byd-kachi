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

    // ─── Thang chữa (2026-09-28) — khoá bài học: nấc FORCE_STOP GIẾT LAUNCHER, chỉ được dùng đúng ca KẸT ───

    private fun step(
        bound: Boolean = false,
        stuck: Boolean = false,
        wanted: Boolean = true,
        userAsked: Boolean = false,
        guestVisible: Boolean = false,
        escalatedAt: Long = -1L,
        now: Long = 60_000L,
    ) = AccessibilityHealGates.healStep(bound, stuck, wanted, userAsked, guestVisible, escalatedAt, now)

    @Test
    fun `da gan roi thi KHONG lam gi`() {
        assertEquals(AccessibilityHealGates.HealStep.NONE, step(bound = true, stuck = true),
            "đã gắn thì kể cả dump còn sót mục kẹt cũng KHÔNG được giết launcher")
    }

    @Test
    fun `chua gan ma KHONG ket thi di duong re nhu cu`() {
        assertEquals(AccessibilityHealGates.HealStep.TOGGLE, step(stuck = false),
            "ca thường sau khi nổ máy: ghi lại settings là hệ gọi bindLocked thật")
    }

    @Test
    fun `ket thi leo thang force-stop`() {
        assertEquals(AccessibilityHealGates.HealStep.FORCE_STOP, step(stuck = true),
            "[ĐO AOSP :1630-1631] ca kẹt thì toggle bị continue bỏ qua ⇒ đi tiếp là phí, phải leo")
    }

    @Test
    fun `ngoai cong cua watchdog thi KHONG tu giet launcher`() {
        assertEquals(AccessibilityHealGates.HealStep.NONE, step(stuck = true, wanted = false),
            "R-nf5: đường TỰ ĐỘNG chỉ được leo trong đúng cổng của watchdog 30 s (phím-thoại bật) — vì chính " +
                "watchdog đó là thứ lắp lại enabled_accessibility_services nếu nửa sau của lệnh tách rời không " +
                "chạy. Leo ngoài cổng ấy = giết xong không ai lắp lại = phím chết HẲN, tệ hơn bệnh đang chữa")
        assertEquals(AccessibilityHealGates.HealStep.FORCE_STOP, step(stuck = true, wanted = false, userAsked = true),
            "nhưng người dùng tự bấm thì vẫn được: họ đang ngồi đó, và vẫn còn đường bấm lại")
    }

    @Test
    fun `app khach dang hien tren man chinh hay trong o thi KHONG tu y giet`() {
        assertEquals(AccessibilityHealGates.HealStep.NONE, step(stuck = true, guestVisible = true),
            "[ĐO xe 2026-09-28] giết launcher lúc đang chứa app ⇒ màn ảo chết ⇒ cửa sổ rơi lại thành mảng đen " +
                "phủ kín nhà. Phát hiện muộn thì báo thật, để người dùng chọn")
    }

    @Test
    fun `nguoi dung tu bam thi bo qua moi cong giu`() {
        assertEquals(AccessibilityHealGates.HealStep.FORCE_STOP,
            step(stuck = true, userAsked = true, guestVisible = true, escalatedAt = 10_000L),
            "bấm tay là đồng ý rõ ràng: họ đang ngồi đó và chủ động yêu cầu")
    }

    @Test
    fun `moi lan no may chi leo MOT lan`() {
        assertEquals(AccessibilityHealGates.HealStep.NONE, step(stuck = true, escalatedAt = 10_000L, now = 60_000L),
            "đã leo lần này rồi mà vẫn kẹt ⇒ giết thêm cũng vô ích, chỉ tổ lặp vô hạn")
    }

    @Test
    fun `khoi dong lai may thi duoc leo lai`() {
        assertEquals(AccessibilityHealGates.HealStep.FORCE_STOP, step(stuck = true, escalatedAt = 9_000_000L, now = 60_000L),
            "mốc lưu LỚN HƠN đồng hồ hiện tại ⇒ elapsedRealtime đã về 0 ⇒ máy đã khởi động lại ⇒ cho leo lại")
    }

    @Test
    fun `nhan biet da leo trong lan no may nay`() {
        assertFalse(AccessibilityHealGates.escalatedThisBoot(-1L, 60_000L), "chưa từng leo")
        assertTrue(AccessibilityHealGates.escalatedThisBoot(10_000L, 60_000L), "đã leo lúc máy chạy được 10 s")
        assertFalse(AccessibilityHealGates.escalatedThisBoot(9_000_000L, 60_000L), "mốc cũ hơn cả đồng hồ ⇒ đã reboot")
    }
}
