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

    // 2.83 (owner chốt 2026-09-29, quyết định A): cổng "không app khách" + hạn mức một-lần-mỗi-lần-nổ-máy của 2.79
    // đã GỠ khỏi healStep — [ĐO xe 29/09] cổng app khách chặn đúng ca cần chữa (ô đã có YouTube khi lượt chữa tới nơi,
    // nhật ký 11:34:29) và kẹt sinh ra ở MỖI lần tắt máy. Thay bằng PHA: đang chạy thì KHÔNG tự giết (người dùng bấm
    // nút); tắt máy / mở xe thì giết, hạn mức một-lượt-mỗi-sự-kiện nằm ở tầng trên (A11yLifecycleGatesTest khoá).
    private fun step(
        bound: Boolean = false,
        stuck: Boolean = false,
        wanted: Boolean = true,
        userAsked: Boolean = false,
        phase: AccessibilityHealGates.HealPhase = AccessibilityHealGates.HealPhase.RUNNING,
    ) = AccessibilityHealGates.healStep(bound, stuck, wanted, userAsked, phase)

    private val allPhases = AccessibilityHealGates.HealPhase.entries

    @Test
    fun `da gan roi thi KHONG lam gi`() {
        allPhases.forEach { p ->
            assertEquals(AccessibilityHealGates.HealStep.NONE, step(bound = true, stuck = true, phase = p),
                "[$p] đã gắn thì kể cả dump còn sót mục kẹt cũng KHÔNG được giết launcher")
            assertEquals(AccessibilityHealGates.HealStep.NONE, step(bound = true, stuck = true, userAsked = true, phase = p),
                "[$p] kể cả bấm tay: đã gắn thì không có gì để chữa")
        }
    }

    @Test
    fun `chua gan ma KHONG ket thi di duong re nhu cu`() {
        allPhases.forEach { p ->
            assertEquals(AccessibilityHealGates.HealStep.TOGGLE, step(stuck = false, phase = p),
                "[$p] ca thường sau khi nổ máy: ghi lại settings là hệ gọi bindLocked thật — nấc TOGGLE giữ nguyên")
        }
    }

    @Test
    fun `lop 3 dang chay ket thi KHONG tu giet launcher`() {
        assertEquals(AccessibilityHealGates.HealStep.NONE, step(stuck = true, phase = AccessibilityHealGates.HealPhase.RUNNING),
            "owner 2026-09-29: 'lúc đang chạy mà lỗi thì user tự chữa ok' — watchdog/alarm/Preflight thấy kẹt thì chỉ " +
                "ghi nhận, KHÔNG force-stop (giết launcher giữa lúc lái = mảng đen phủ nhà, [ĐO xe 28/09])")
    }

    @Test
    fun `lop 1 va lop 2 ket thi leo thang force-stop`() {
        listOf(AccessibilityHealGates.HealPhase.TAT_MAY, AccessibilityHealGates.HealPhase.MO_XE).forEach { p ->
            assertEquals(AccessibilityHealGates.HealStep.FORCE_STOP, step(stuck = true, phase = p),
                "[$p] [ĐO AOSP :1630-1631] ca kẹt thì toggle bị continue bỏ qua ⇒ phải leo; màn tắt / vừa mở xe " +
                    "(ô có thể ĐÃ có app — [ĐO c2]) ⇒ bỏ qua cổng app khách (owner chấp nhận màn nhà load lại một nhịp)")
        }
    }

    @Test
    fun `ngoai cong cua watchdog thi KHONG tu giet launcher`() {
        allPhases.forEach { p ->
            assertEquals(AccessibilityHealGates.HealStep.NONE, step(stuck = true, wanted = false, phase = p),
                "[$p] R-nf5: đường TỰ ĐỘNG chỉ được leo trong đúng cổng của watchdog 30 s (phím-thoại bật) — vì chính " +
                    "watchdog đó là thứ lắp lại enabled_accessibility_services nếu nửa sau của lệnh tách rời không " +
                    "chạy. Leo ngoài cổng ấy = giết xong không ai lắp lại = phím chết HẲN, tệ hơn bệnh đang chữa")
            assertEquals(AccessibilityHealGates.HealStep.FORCE_STOP, step(stuck = true, wanted = false, userAsked = true, phase = p),
                "[$p] nhưng người dùng tự bấm thì vẫn được: họ đang ngồi đó, và vẫn còn đường bấm lại")
        }
    }

    @Test
    fun `nguoi dung tu bam thi leo o moi pha`() {
        allPhases.forEach { p ->
            assertEquals(AccessibilityHealGates.HealStep.FORCE_STOP, step(stuck = true, userAsked = true, phase = p),
                "[$p] bấm tay là đồng ý rõ ràng: họ đang ngồi đó và chủ động yêu cầu — nút 'Kiểm tra / Sửa ngay' giữ nguyên")
        }
    }

    @Test
    fun `nhan biet da leo trong lan no may nay`() {
        assertFalse(AccessibilityHealGates.escalatedThisBoot(-1L, 60_000L), "chưa từng leo")
        assertTrue(AccessibilityHealGates.escalatedThisBoot(10_000L, 60_000L), "đã leo lúc máy chạy được 10 s")
        assertFalse(AccessibilityHealGates.escalatedThisBoot(9_000_000L, 60_000L), "mốc cũ hơn cả đồng hồ ⇒ đã reboot")
    }
}
