package com.byd.clusternav.system

import java.io.File
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Hardening 2026-09-25 · audit F1 [P1] — wiring (CLAUDE.md §8): `ShellTransport.run()` phải đi qua
 * `ShellRunFailureLog` (luật thuần ở `:core`, canh bởi `ShellRunFailureLogTest`) và ghi W; không còn `getOrDefault("")`
 * nuốt im. `ShellTransport` cần `Context`/`Dadb` nên chỉ canh được bằng văn bản ở đây.
 */
class ShellTransportFailureLogWiringTest {

    @Test fun `ShellTransport run phai di qua ShellRunFailureLog`() {
        val src = source("app/src/main/java/com/byd/clusternav/system/ShellTransport.kt")
        val run = src.substringAfter("fun run(cmd: String").substringBefore("val seam")
        assertTrue(run.contains("ShellRunFailureLog.line(cmd, t"), "nhánh hỏng của run() phải dựng dòng log qua ShellRunFailureLog")
        assertTrue(run.contains("Log.w(TAG"), "…và ghi ra logcat mức W")
        assertFalse(run.contains("getOrDefault(\"\")"), "không còn nuốt im")
        assertTrue(run.contains("\"\"\n"), "hợp đồng cũ giữ nguyên: hỏng ⇒ chuỗi rỗng")
    }

    /** Mã nguồn ĐÃ BỎ comment `//` — để một dòng bị comment-out không còn làm bài canh xanh giả (thử-làm-đỏ 2026-09-25). */
    private fun source(rel: String): String {
        val cwd = File(System.getProperty("user.dir"))
        val text = listOf(cwd.resolve(rel), cwd.resolve("../$rel"), cwd.resolve(rel.removePrefix("app/"))).first { it.isFile }.readText()
        return text.lines().joinToString("\n") { it.substringBefore("//") }
    }
}
