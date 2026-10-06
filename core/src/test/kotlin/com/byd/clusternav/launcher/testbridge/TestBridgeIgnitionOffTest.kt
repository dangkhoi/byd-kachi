package com.byd.clusternav.launcher.testbridge

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ 2.93 · TEST-MODE-ACC-OFF (⚠ bảo mật) — cửa sổ Chế độ kiểm thử ĐÓNG khi tắt máy ═══════════════════════════════
 *
 * [ĐO xe 29/09, spec 2.83 §2.10] `test_mode_minutes_left` 30 → 29 → 22 qua hai lượt BYD giết lúc tắt máy (`am_kill`
 * 11:20:50 và 11:33:25): tắt máy BYD KHÔNG khởi động lại máy ⇒ `boot_id` giữ nguyên ⇒ cửa sổ 60 phút sống qua tắt máy,
 * kể cả lúc xe đỗ không người — trái câu UI. Mà cửa sổ là công tắc DUY NHẤT chắn receiver exported `KachiTestBridge`
 * (`ctl`/`hal set` chạm thân xe khi kèm `auto_confirm`). Quyết định (kế hoạch 2.93, hướng an toàn): đóng ở lần tắt máy
 * đầu tiên sau khi mở, bằng claim tắt-máy bền của lớp 1 (2.83). Thử ĐỎ: bỏ dòng `closedByIgnitionOff` ở `remainingMs`.
 */
class TestBridgeIgnitionOffTest {

    private val boot = "8c1d2f3a-boot"
    private val min = 60_000L
    private val openedAt = 4_000_000L                       // elapsedRealtime lúc bấm bật (≈ 66 phút sau khi máy lên)
    private val stored = TestBridgeWindow.encode(boot, openedAt)

    @Test
    fun `ca xe 29_09 - hai lan tat may thi cua so dong ngay tu lan dau`() {
        val kill1 = openedAt + 11 * min + 50_000L           // ≈ 11:20:50 khi bật ≈ 11:09
        val claim1 = kill1 + 350L                           // HOME dựng lại 0,3 s sau `am_kill` lúc màn tắt ⇒ lớp 1 claim
        val check = kill1 + 2 * min                         // lệnh đến sau đó (vd app khác bắn vào lúc xe đỗ)
        // Bệnh (mô tả): không có đầu vào tắt-máy thì cửa sổ còn ~46 phút.
        assertTrue(TestBridgeWindow.remainingMinutes(stored, boot, check) in 45..47)
        assertEquals(0L, TestBridgeWindow.remainingMs(stored, boot, check, claim1))
        assertFalse(TestBridgeWindow.isOn(stored, boot, check, claim1))
        assertEquals(0, TestBridgeWindow.remainingMinutes(stored, boot, check, claim1), "màn Cài đặt hiện TẮT")
    }

    @Test
    fun `tat may TRUOC luc bat khong dong cua so moi mo`() {
        val earlierClaim = openedAt - 10 * min               // lần tắt máy của chuyến trước, cùng lần khởi động máy
        assertTrue(TestBridgeWindow.isOn(stored, boot, openedAt + 5 * min, earlierClaim))
        assertTrue(TestBridgeWindow.isOn(stored, boot, openedAt + 5 * min, openedAt), "claim TRÙNG mốc bật không tính là sau")
    }

    @Test
    fun `chua tung tat may - giu nguyen luat 60 phut cu`() {
        assertTrue(TestBridgeWindow.isOn(stored, boot, openedAt + 59 * min, TestBridgeWindow.NEVER))
        assertFalse(TestBridgeWindow.isOn(stored, boot, openedAt + 61 * min, TestBridgeWindow.NEVER))
        assertEquals(TestBridgeWindow.isOn(stored, boot, openedAt + min), TestBridgeWindow.isOn(stored, boot, openedAt + min, -1L))
    }

    @Test
    fun `claim o tuong lai la doi may truoc - bo qua`() {
        // `elapsedRealtime` về 0 khi khởi động lại; claim cũ lớn hơn "bây giờ" ⇒ của đời máy trước (luật escalatedThisBoot).
        val now = openedAt + 5 * min
        assertTrue(TestBridgeWindow.isOn(stored, boot, now, now + 1))
        assertFalse(TestBridgeWindow.closedByIgnitionOff(openedAt, now, now + 1))
        assertTrue(TestBridgeWindow.closedByIgnitionOff(openedAt, now, now))
    }

    @Test
    fun `bat lai sau khi tat may thi mo cua so moi`() {
        val claim = openedAt + 20 * min
        val reopenedAt = claim + 5 * min                     // người trong xe bấm bật lại sau khi nổ máy
        val again = TestBridgeWindow.encode(boot, reopenedAt)
        assertTrue(TestBridgeWindow.isOn(again, boot, reopenedAt + min, claim), "cửa sổ MỚI mở sau claim ⇒ mở")
    }
}
