package com.byd.clusternav.carexec

import com.byd.clusternav.navigation.NlsHealPolicy
import com.byd.clusternav.navigation.NlsHealPolicy.Outcome
import com.byd.clusternav.navigation.NlsLiveDump
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * FIX286 R-HUD — khoá CHUỖI LỆNH của một phiên gắn lại nguồn thông báo, off-car (mẫu `SetHomeActivityTest`: `sh` giả).
 *
 * Dump giả dựng đúng KHUÔN nguyên văn của máy ảo (`core/src/test/resources/diagnostics/dumpsys-notification-emulator-
 * 2026-10-03-*.txt`, khoá ở `NlsLiveDumpTest`): chỉ khối bộ nghe là đủ cho bộ đọc.
 */
class NlsHealShellTest {

    private val pkg = "com.byd.launcher"
    private val comp = "$pkg/com.byd.clusternav.NavNotificationListener"
    private val dumpCmd = "dumpsys notification p $pkg"

    private fun dump(live: Boolean) = LocalShellText(
        "  Notification listeners:\n" +
            "    All notification listeners (1) enabled for current profiles:\n" +
            "      ComponentInfo{$comp}\n" +
            "    Live notification listeners (${if (live) 1 else 0}):\n" +
            (if (live) "      ComponentInfo{$comp} (user 0): android.service.notification.INotificationListener\$Stub\$Proxy@1\n" else "") +
            "    Snoozed notification listeners (0):\n",
        "", 0,
    )

    /** `sh` giả: ghi lại lệnh; dump trả theo hàng đợi [dumps]. */
    private class FakeShell(private val dumps: ArrayDeque<LocalShellText>) {
        val cmds = mutableListOf<String>()
        fun sh(c: String): LocalShellText {
            cmds += c
            return if (c.startsWith("dumpsys notification")) dumps.removeFirst() else LocalShellText("", "", 0)
        }
    }

    private fun run(
        dumps: List<LocalShellText>,
        checkFirst: Boolean = true,
        gate: Boolean = true,
        component: String = comp,
    ): Pair<NlsHealShell.Run, FakeShell> {
        val f = FakeShell(ArrayDeque(dumps))
        var fired = false
        val r = NlsHealShell.block(component, pkg, checkFirst, { gate }, { true }, {}, { fired = true }, f::sh)
        assertEquals(r.fired, fired, "cờ đếm trần phải khớp kết quả")
        return r to f
    }

    @Test
    fun `NOT_LIVE thi doc - ban cap tach roi - doc lai, dung thu tu`() {
        val (r, f) = run(listOf(dump(false), dump(true)))
        assertEquals(listOf(dumpCmd, NlsHealPolicy.toggleCommand(comp, pkg), dumpCmd), f.cmds)
        assertEquals(Outcome.HEALED, r.outcome)
        assertTrue(r.fired)
        assertEquals(NlsLiveDump.Verdict.NOT_LIVE, r.before)
        assertEquals(NlsLiveDump.Verdict.LIVE, r.after)
    }

    @Test
    fun `da LIVE thi chi doc - 0 lenh ghi`() {
        val (r, f) = run(listOf(dump(true)))
        assertEquals(listOf(dumpCmd), f.cmds)
        assertEquals(Outcome.LIVE_ALREADY, r.outcome)
        assertFalse(r.fired)
    }

    @Test
    fun `khong doc duoc khoi Live thi khong hanh dong`() {
        val (r, f) = run(listOf(LocalShellText("Can't find service: notification", "", 0)))
        assertEquals(listOf(dumpCmd), f.cmds)
        assertEquals(Outcome.UNREADABLE, r.outcome)
    }

    @Test
    fun `cong phut chot dong thi khong ban`() {
        val (r, f) = run(listOf(dump(false)), gate = false)
        assertEquals(listOf(dumpCmd), f.cmds)
        assertEquals(Outcome.SKIPPED, r.outcome)
        assertFalse(r.fired)
    }

    @Test
    fun `ban xong doc lai van chua Live thi bao THAT - khong xong`() {
        val (r, _) = run(listOf(dump(false), dump(false)))
        assertEquals(Outcome.STILL_NOT_LIVE, r.outcome)
        assertFalse(r.outcome.ok)
    }

    @Test
    fun `luot nguoi dung bam - doc truoc de ghi nhat ky nhung BAN LUON ke ca khi da Live, van doc lai`() {
        val (r, f) = run(listOf(dump(true), dump(true)), checkFirst = false)
        assertEquals(listOf(dumpCmd, NlsHealPolicy.toggleCommand(comp, pkg), dumpCmd), f.cmds)
        assertEquals(Outcome.HEALED, r.outcome)
        assertEquals(NlsLiveDump.Verdict.LIVE, r.before)
    }

    @Test
    fun `kenh hong ngay lenh doc thi CHUA ban - fired dung su that`() {
        val f = FakeShell(ArrayDeque())
        var fired = false
        val boom: (String) -> LocalShellText = { c -> f.cmds += c; throw java.io.IOException("connect refused") }
        runCatching {
            NlsHealShell.block(comp, pkg, false, { true }, { true }, {}, { fired = true }, boom)
        }
        assertEquals(listOf(dumpCmd), f.cmds, "phiên nối lười hỏng ở lệnh ĐẦU — phải là lệnh chỉ-đọc")
        assertFalse(fired, "cặp lệnh chưa gửi ⇒ không được đếm là đã bắn")
    }

    @Test
    fun `component goi la thi khong dung lenh`() {
        val (r, f) = run(listOf(dump(false)), component = "com.other/com.other.L")
        assertEquals(listOf(dumpCmd), f.cmds)
        assertEquals(Outcome.SKIPPED, r.outcome)
    }

    @Test
    fun `luot chi doc cho Cai dat khong bao gio ghi`() {
        val f = FakeShell(ArrayDeque(listOf(dump(false))))
        val r = NlsHealShell.readBlock(comp, pkg, f::sh)
        assertEquals(listOf(dumpCmd), f.cmds)
        assertEquals(Outcome.NOT_LIVE, r.outcome)
        assertFalse(r.fired)
    }
}
