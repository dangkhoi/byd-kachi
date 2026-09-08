package com.byd.clusternav.carexec

import java.nio.file.Files
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Ba tính chất khiến phiên trên xe không bị mất công: dừng ở đúng chỗ cần người, dừng khi lệnh lỗi thay
 * vì chạy tiếp, và nối lại được từ giữa chuỗi.
 */
class RunScenarioTest {

    private val ok: (String) -> ShellOutcome = { ShellOutcome(it, 0, "", 1) }
    private val fails: (String) -> ShellOutcome = { ShellOutcome(it, 1, "boom", 1) }

    private fun ledgerWithAllStepsOk(): String {
        val path = Files.createTempFile("verdicts", ".tsv").toString()
        CarExecCatalog.steps.forEach { step ->
            CarExecCommands.recordVerdict(path, step.candidates.first().id, Verdict.OK, "test", "")
        }
        return path
    }

    private val values = mapOf(
        CarExecCatalog.PLACEHOLDER_PACKAGE to "com.example.a",
        CarExecCatalog.PLACEHOLDER_COMPONENT to "com.example.a/.Main",
        CarExecCatalog.PLACEHOLDER_DISPLAY to "1",
        CarExecCatalog.PLACEHOLDER_TASK to "42",
        CarExecCatalog.PLACEHOLDER_SERVICE to "AutoContainer",
        CarExecCatalog.PLACEHOLDER_LEFT to "0",
        CarExecCatalog.PLACEHOLDER_TOP to "0",
        CarExecCatalog.PLACEHOLDER_RIGHT to "1920",
        CarExecCatalog.PLACEHOLDER_BOTTOM to "720",
        CarExecCatalog.PLACEHOLDER_DPI to "320",
    )

    @Test
    fun `dung lai o moc can nhin cum va noi ro cach chay tiep`() {
        val report = CarExecCommands.runScenario(ledgerWithAllStepsOk(), "cast.cold-first", values, 1, ok)
        assertTrue(report.contains("⏸ CẦN NHÌN"), report)
        assertTrue(report.contains("--from"), "phải nói cách chạy tiếp")
        assertFalse(report.contains("hết kịch bản"), "không được chạy hết khi còn mốc chưa xác nhận")
    }

    @Test
    fun `step chua OK thi dung ngay chu khong chay bua`() {
        val emptyLedger = Files.createTempFile("verdicts", ".tsv").toString()
        val report = CarExecCommands.runScenario(emptyLedger, "cast.cold-first", values, 1, ok)
        assertTrue(report.contains("chưa có candidate nào OK"), report)
    }

    @Test
    fun `lenh that bai thi dung va goi y ghi verdict fail`() {
        val report = CarExecCommands.runScenario(ledgerWithAllStepsOk(), "cast.cold-first", values, 1, fails)
        assertTrue(report.contains("DỪNG: lệnh thất bại"), report)
        assertTrue(report.contains("verdict"), "phải chỉ cách ghi lại thất bại")
    }

    @Test
    fun `noi lai duoc tu giua chuoi`() {
        val report = CarExecCommands.runScenario(ledgerWithAllStepsOk(), "cast.cold-first", values, 4, ok)
        assertTrue(report.contains("từ bước 4"), report)
        assertFalse(report.contains("1. probe-profile"), "không được chạy lại bước đã xong")
    }

    @Test
    fun `thieu gia tri placeholder thi dung chu khong gui lenh nua vo`() {
        val report = CarExecCommands.runScenario(ledgerWithAllStepsOk(), "cast.geometry-persist", emptyMap(), 1, ok)
        assertTrue(report.contains("DỪNG"), report)
    }
}
