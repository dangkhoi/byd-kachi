package com.byd.clusternav.modules.clustercast.simplified

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * 2.96 · R13 — khoá lỗi [ĐO log 07/10]: mỗi lần khởi động có 4 lệnh `settings put global …freeform…/force_resizable…`
 * dù cờ đã là 1 (một lệnh `exit=-1`). Đường cast [CastGeometryController.ensureFreeformFlags] giờ ĐỌC trước, chỉ ghi khoá
 * chưa = 1; đọc hỏng (exit ≠ 0) ⇒ ghi như cũ (fail-safe). Chuỗi ghi byte-identical với trước R13.
 */
class FreeformFlagsReadFirstTest {

    private class Shell(private val values: Map<String, ShellResult>) : SimpleCastShell {
        val history = mutableListOf<String>()
        override fun execute(command: String): ShellResult {
            history += command
            return values[command] ?: ShellResult(0, "", "")
        }
    }

    private fun ctl(shell: Shell) = CastGeometryController(shell, FakePrefs(), { 2 }) {}

    private val getFf = "settings get global enable_freeform_support"
    private val getRa = "settings get global force_resizable_activities"
    private val putFf = "settings put global enable_freeform_support 1"
    private val putRa = "settings put global force_resizable_activities 1"

    @Test
    fun `both flags already 1 - only two reads, zero writes`() {
        val shell = Shell(mapOf(getFf to ShellResult(0, "1\n", ""), getRa to ShellResult(0, "1\n", "")))
        assertEquals(emptyList<String>(), ctl(shell).ensureFreeformFlags())
        assertEquals(listOf(getFf, getRa), shell.history)
    }

    @Test
    fun `flag unset (null) is written, the other left alone`() {
        val shell = Shell(mapOf(getFf to ShellResult(0, "null\n", ""), getRa to ShellResult(0, "1", "")))
        assertEquals(listOf(putFf), ctl(shell).ensureFreeformFlags())
        assertEquals(listOf(getFf, putFf, getRa), shell.history)
    }

    @Test
    fun `failed read writes as before - even if stdout says 1`() {
        val shell = Shell(mapOf(getFf to ShellResult(-1, "1", "boom"), getRa to ShellResult(1, "", "err")))
        assertEquals(listOf(putFf, putRa), ctl(shell).ensureFreeformFlags())
        assertEquals(listOf(getFf, putFf, getRa, putRa), shell.history)
    }

    // ── soát 2.96 Pass 1 [P2]: đọc TRONG tiến trình ⇒ 0 lệnh shell khi cờ đã bật ─────────────────────────────────

    private fun ctlInProcess(shell: Shell, values: Map<String, String?>) =
        CastGeometryController(shell, FakePrefs(), { 2 }, readGlobalSetting = { key -> values[key] }) {}

    @Test
    fun `in-process reader - both flags 1 - ZERO shell commands`() {
        val shell = Shell(emptyMap())
        val ctl = ctlInProcess(shell, mapOf("enable_freeform_support" to "1", "force_resizable_activities" to "1"))
        assertEquals(emptyList<String>(), ctl.ensureFreeformFlags())
        assertEquals(emptyList<String>(), shell.history, "đọc trong tiến trình thì kênh shell không nhận một lệnh nào")
    }

    @Test
    fun `in-process reader - absent (null) flag is written via shell, no shell READ fallback`() {
        val shell = Shell(emptyMap())
        val ctl = ctlInProcess(shell, mapOf("enable_freeform_support" to null, "force_resizable_activities" to "1"))
        assertEquals(listOf(putFf), ctl.ensureFreeformFlags())
        assertEquals(listOf(putFf), shell.history, "null của bộ đọc = ghi, KHÔNG rơi về `settings get`")
    }
}
