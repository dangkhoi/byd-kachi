package com.byd.clusternav.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * 2.98 · R6-F — `nav_notif_{log,raw}_*.csv` không có trần từng tệp (perf-inventory-2026-10-08 §4 F). Khoá: mỗi tệp
 * ≤ trần, xoay khi vượt, không vòng xoay vô tận với dòng lớn, không nuốt dòng nào.
 */
class FileByteBudgetTest {

    /** Mô phỏng một writer: trả kích thước từng tệp đã tạo. */
    private fun simulate(cap: Long, header: Long, rows: List<Long>): List<Long> {
        val b = FileByteBudget(cap)
        val files = mutableListOf<Long>()
        rows.forEach { n ->
            if (files.isEmpty() || b.mustRotateBefore(n)) { files += header; b.startFile(header) }
            files[files.lastIndex] = files.last() + n
            b.record(n)
        }
        return files
    }

    @Test
    fun `moi tep khong vuot tran, xoay sang tep moi`() {
        val files = simulate(cap = 1_000, header = 50, rows = List(100) { 100L })
        assertTrue(files.size > 1, "phải xoay")
        files.forEach { assertTrue(it <= 1_000, "tệp $it B vượt trần") }
        assertEquals(50L * files.size + 100L * 100, files.sum(), "không mất dòng nào")
    }

    @Test
    fun `dong lon hon tran van duoc ghi, khong xoay vo tan`() {
        val files = simulate(cap = 100, header = 10, rows = listOf(500L, 500L, 5L))
        assertEquals(listOf(510L, 510L, 15L), files, "mỗi dòng quá cỡ một tệp riêng, tệp chỉ-tiêu-đề không bao giờ xoay")
    }

    @Test
    fun `tep chi co tieu de khong xoay`() {
        val b = FileByteBudget(100)
        b.startFile(90)
        assertFalse(b.mustRotateBefore(50))
        b.record(50)
        assertTrue(b.mustRotateBefore(1))
    }

    @Test
    fun `tran mac dinh bang tran usage log 8 MiB`() {
        assertEquals(8L * 1024 * 1024, FileByteBudget.PER_FILE_CAP_BYTES)
        assertThrows<IllegalArgumentException> { FileByteBudget(0) }
    }
}
