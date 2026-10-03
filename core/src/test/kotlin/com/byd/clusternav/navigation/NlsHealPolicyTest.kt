package com.byd.clusternav.navigation

import com.byd.clusternav.carexec.LocalShellFailure
import com.byd.clusternav.navigation.NlsHealPolicy.Facts
import com.byd.clusternav.navigation.NlsHealPolicy.Ledger
import com.byd.clusternav.navigation.NlsHealPolicy.Outcome
import com.byd.clusternav.navigation.NlsHealPolicy.Step
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * FIX286 R-HUD — khoá cổng lượt TỰ GẮN LẠI (owner 03/10: *"phải kiểm tra là có enable chưa trong setting mới đi bind"*),
 * "một lần mỗi tiến trình + trần thử lại", và lệnh cặp chỉ cho component của mình.
 */
class NlsHealPolicyTest {

    private val ready = Facts(
        navEnabled = true, granted = true, interactive = true, shellUp = true,
        boundInProcess = false, sinceProcessStartMs = 60_000L,
    )
    private val now = 1_000_000L

    @Test
    fun `cong tac TAT thi khong lam gi - dung truoc moi cong khac`() {
        assertEquals(Step.OFF, NlsHealPolicy.step(ready.copy(navEnabled = false), Ledger(), now))
        // Kể cả khi mọi điều kiện khác đều "nên chữa".
        assertEquals(
            Step.OFF,
            NlsHealPolicy.step(ready.copy(navEnabled = false, granted = null, interactive = null, shellUp = false), Ledger(), now),
        )
    }

    @Test
    fun `chua cap quyen hoac doc khong duoc thi khong tu cap`() {
        assertEquals(Step.NO_ACCESS, NlsHealPolicy.step(ready.copy(granted = false), Ledger(), now))
        assertEquals(Step.NO_ACCESS, NlsHealPolicy.step(ready.copy(granted = null), Ledger(), now))
    }

    @Test
    fun `man tat - kenh chua len - vua bat tien trinh thi cho`() {
        assertEquals(Step.SCREEN_OFF, NlsHealPolicy.step(ready.copy(interactive = false), Ledger(), now))
        assertEquals(Step.SCREEN_OFF, NlsHealPolicy.step(ready.copy(interactive = null), Ledger(), now))
        assertEquals(Step.NO_SHELL, NlsHealPolicy.step(ready.copy(shellUp = false), Ledger(), now))
        assertEquals(
            Step.SETTLING,
            NlsHealPolicy.step(ready.copy(sinceProcessStartMs = NlsHealPolicy.SETTLE_AFTER_START_MS - 1), Ledger(), now),
        )
        assertEquals(
            Step.CHECK,
            NlsHealPolicy.step(ready.copy(sinceProcessStartMs = NlsHealPolicy.SETTLE_AFTER_START_MS), Ledger(), now),
        )
    }

    @Test
    fun `callback trong tien trinh noi da gan thi khoi doc dump`() {
        assertEquals(Step.BOUND, NlsHealPolicy.step(ready.copy(boundInProcess = true), Ledger(), now))
    }

    @Test
    fun `mot lan moi tien trinh - chua xong thi thu lai co tran va gian cach`() {
        var l = Ledger()
        assertEquals(Step.CHECK, NlsHealPolicy.step(ready, l, now))
        // Lần 1 bắn mà đọc lại vẫn chưa Live ⇒ chờ RETRY_GAP rồi mới được thử lại.
        l = NlsHealPolicy.record(l, Outcome.STILL_NOT_LIVE, fired = true, nowMs = now)
        assertEquals(1, l.fires)
        assertEquals(Step.BACKOFF, NlsHealPolicy.step(ready, l, now + NlsHealPolicy.RETRY_GAP_MS - 1))
        assertEquals(Step.CHECK, NlsHealPolicy.step(ready, l, now + NlsHealPolicy.RETRY_GAP_MS))
        // Tới trần ⇒ thôi hẳn trong tiến trình này.
        repeat(NlsHealPolicy.MAX_FIRES_PER_PROCESS - 1) { i ->
            l = NlsHealPolicy.record(l, Outcome.STILL_NOT_LIVE, fired = true, nowMs = now + (i + 1) * NlsHealPolicy.RETRY_GAP_MS)
        }
        assertEquals(NlsHealPolicy.MAX_FIRES_PER_PROCESS, l.fires)
        assertEquals(Step.CAPPED, NlsHealPolicy.step(ready, l, now + 100 * NlsHealPolicy.RETRY_GAP_MS))
    }

    @Test
    fun `da thanh cong mot lan la thoi - khong ban lan hai trong tien trinh`() {
        val l = NlsHealPolicy.record(Ledger(), Outcome.HEALED, fired = true, nowMs = now)
        assertTrue(l.healed)
        // NLS lại rớt trong cùng tiến trình (RAM nói chưa gắn) ⇒ vẫn KHÔNG tự bắn lần hai.
        assertEquals(Step.HEALED, NlsHealPolicy.step(ready, l, now + 10 * NlsHealPolicy.RECHECK_GAP_MS))
    }

    @Test
    fun `doc thay Live ma khong ban thi khong tinh vao tran nhung gian cach lan doc`() {
        val l = NlsHealPolicy.record(Ledger(), Outcome.LIVE_ALREADY, fired = false, nowMs = now)
        assertEquals(0, l.fires)
        assertFalse(l.healed)
        assertEquals(Step.BACKOFF, NlsHealPolicy.step(ready, l, now + NlsHealPolicy.RECHECK_GAP_MS - 1))
        assertEquals(Step.CHECK, NlsHealPolicy.step(ready, l, now + NlsHealPolicy.RECHECK_GAP_MS))
    }

    @Test
    fun `lenh cap chi cho component cua chinh minh va chay tach roi`() {
        val comp = "com.byd.launcher/com.byd.clusternav.NavNotificationListener"
        val cmd = NlsHealPolicy.toggleCommand(comp, "com.byd.launcher")
        assertEquals(
            "nohup sh -c 'cmd notification disallow_listener $comp ; sleep 1.5 ; " +
                "cmd notification allow_listener $comp' >/dev/null 2>&1 </dev/null &",
            cmd,
        )
        assertTrue(cmd.indexOf("disallow_listener") < cmd.indexOf("allow_listener $comp'"), "disallow TRƯỚC, allow SAU")
        assertEquals("", NlsHealPolicy.toggleCommand("com.other/com.other.L", "com.byd.launcher"), "gói lạ ⇒ không dựng")
        assertEquals("", NlsHealPolicy.toggleCommand("com.byd.launcher/x';reboot;'", "com.byd.launcher"))
        assertEquals("", NlsHealPolicy.toggleCommand("com.byd.launcher/a.B\$C", "com.byd.launcher"), "`$` bị shell lồng nội suy")
        assertEquals("", NlsHealPolicy.toggleCommand(comp, ""))
    }

    @Test
    fun `phien shell hong anh xa dung hai nhom`() {
        listOf(LocalShellFailure.NOT_APPROVED, LocalShellFailure.AWAITING_APPROVAL, LocalShellFailure.AUTH_REJECTED)
            .forEach { assertEquals(Outcome.NO_SHELL, NlsHealPolicy.fromShellFailure(it), "$it") }
        listOf(LocalShellFailure.PORT_CLOSED, LocalShellFailure.IO_ERROR, LocalShellFailure.UNKNOWN)
            .forEach { assertEquals(Outcome.SHELL_FAILED, NlsHealPolicy.fromShellFailure(it), "$it") }
        assertEquals(
            setOf(Outcome.LIVE_ALREADY, Outcome.BOUND_IN_PROCESS, Outcome.HEALED),
            Outcome.entries.filter { it.ok }.toSet(),
            "chỉ ba kết quả được nói là 'đang gắn' — NO_SHELL/BUSY/UNREADABLE không bao giờ là 'xong'",
        )
    }
}
