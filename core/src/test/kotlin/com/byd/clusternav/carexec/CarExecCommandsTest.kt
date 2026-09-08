package com.byd.clusternav.carexec

import java.nio.file.Files
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CarExecCommandsTest {

    @Test
    fun `thieu gia tri placeholder thi bao loi chu khong gui lenh nua vo`() {
        val failure = runCatching {
            CarExecCommands.resolve("am start --display {display} -n {comp}", mapOf("{display}" to "1"))
        }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException, "phải chặn trước khi gửi lệnh")
        assertTrue(failure!!.message!!.contains("{comp}"), failure.message)
    }

    @Test
    fun `resolve thay het placeholder da cho`() {
        val resolved = CarExecCommands.resolve(
            "service call {svc} 2 i32 1000 i32 18 s16 \"\"",
            mapOf("{svc}" to "AutoContainer"),
        )
        assertEquals("service call AutoContainer 2 i32 1000 i32 18 s16 \"\"", resolved)
    }

    @Test
    fun `ledger la append only nen lich su that bai khong bi xoa`() {
        val path = Files.createTempFile("verdicts", ".tsv").toString()
        CarExecCommands.recordVerdict(path, "place.freeform-only", Verdict.FAIL, "car:5555", "lần một") {
            Instant.parse("2026-07-27T10:00:00Z")
        }
        CarExecCommands.recordVerdict(path, "place.freeform-only", Verdict.OK, "car:5555", "lần hai") {
            Instant.parse("2026-07-27T11:00:00Z")
        }
        val text = java.io.File(path).readText()
        assertTrue(text.contains("lần một"), "dòng FAIL cũ phải còn")
        assertTrue(text.contains("lần hai"))
        assertEquals(Verdict.OK, CarExecCommands.ledger(path).verdictFor("place.freeform-only"))
    }

    @Test
    fun `e2e khong am tham di qua step chua chung minh`() {
        val path = Files.createTempFile("verdicts", ".tsv").toString()
        CarExecCommands.recordVerdict(path, "observe.dumpsys", Verdict.OK, "car:5555", "")
        val report = CarExecCommands.e2e(path)
        assertTrue(report.contains("CHƯA ĐỦ"), report)
        assertTrue(report.contains("open-projection"), "phải nêu tên step còn thiếu")
    }

    @Test
    fun `candidate la ma ma khong ton tai thi noi ro`() {
        assertTrue(CarExecCommands.recordVerdict("/tmp/never.tsv", "khong-co", Verdict.OK, "x", "").contains("không có"))
    }

    @Test
    fun `steps in ra day du candidate va nguon verdict`() {
        val text = CarExecCommands.steps()
        CarExecCatalog.steps.flatMap { it.candidates }.forEach { assertTrue(text.contains(it.id), it.id) }
        assertTrue(text.contains("HUMAN"), "phải nói rõ step nào cần người nhìn")
    }

    @Test
    fun `app khong duoc dung shell tho`() {
        val roots = listOf("app/src/main/java", "../app/src/main/java").map(java.nio.file.Paths::get)
        val root = roots.firstOrNull(Files::exists) ?: return
        val offenders = Files.walk(root).use { paths ->
            paths.filter { Files.isRegularFile(it) && it.toString().endsWith(".kt") }
                .filter { it.toFile().readText().contains("CarExecShell") }
                .toList()
        }
        assertTrue(offenders.isEmpty(), "shell thô chỉ dành cho runner đánh giá: $offenders")
        assertFalse(offenders.isNotEmpty())
    }
}
