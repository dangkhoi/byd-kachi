package com.byd.clusternav.modules.clustercast.simplified

import com.byd.clusternav.testsupport.KotlinSource
import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

/**
 * CLUSTER-THEME-SAFE B1a · test C.3 mục 6 (R3) — bài canh MÃ: opcode theme, tên service AutoContainer và phím 309 chỉ được
 * viết chữ ở MỘT chỗ.
 *
 *  • `i32 29|30|31` — [ĐO xe 05/10, hai lần] gửi theme khi màn ảo cụm còn lớp ⇒ `system_server` khởi động lại. Mọi lệnh theme
 *    phải dựng từ [ProjectionRecipe.command] sau cổng [ThemeGate]; literal ở chỗ khác = một đường gửi theme mù.
 *  • chuỗi chứa `AutoContainer` — tên service khác theo đời DiLink (DL5 = `auto_container`); gõ cứng ⇒ DL5 câm, và là chỗ
 *    dựng lệnh thứ hai ngoài [ProjectionRecipe.svcCall] (lệnh thiếu `s16 ""` ⇒ EX_NULL_POINTER [ĐO 05/10]).
 *  • bơm phím 309 — [ĐO `PhoneWindowManager.java:3486-3490`, nghiên cứu 05/10 F8] không thu ADAS mà CÚP cuộc gọi Bluetooth.
 *
 * Ngoại lệ có tên: `ProjectionRecipe.kt` (chỗ dựng duy nhất) và catalog đo tay `CarExec*` (lệnh thô cho buổi đo trên xe,
 * `runCandidate` không có lời gọi nào trong `app/main`). Quét MÃ đã bỏ chú thích ([SourceRoots.codeOf] cùng luật).
 */
class ThemeOpcodeLiteralContractTest {

    private fun allowed(file: Path): Boolean {
        val n = file.fileName.toString()
        return n == "ProjectionRecipe.kt" || n.startsWith("CarExec")
    }

    private fun sources(): List<Path> = SourceRoots.moduleSourceRoots().flatMap { root ->
        Files.walk(root).use { s -> s.filter { it.toString().endsWith(".kt") || it.toString().endsWith(".java") }.collect(java.util.stream.Collectors.toList()) }
    }

    /**
     * Bỏ chú thích khối + dòng — ĐÚNG bộ quét của [SourceRoots.codeOf] ([KotlinSource.stripComments]); `keepLines` để
     * `tệp:dòng` trong lời báo là dòng THẬT của tệp (bản regex cũ gộp comment khối thành một khoảng trắng ⇒ số dòng trôi).
     */
    private fun code(file: Path): String = KotlinSource.stripComments(file.toFile().readText(), keepLines = true)

    private fun offenders(rule: Regex, skipAllowed: Boolean = true): List<String> = sources()
        .filter { !skipAllowed || !allowed(it) }
        .flatMap { f -> code(f).lines().withIndex().filter { rule.containsMatchIn(it.value) }.map { "${f.fileName}:${it.index + 1}: ${it.value.trim()}" } }

    private val themeLiteral = Regex("""\bi32\s+(29|30|31)\b""")
    private val svcLiteral = Regex(""""[^"\n]*\bAutoContainer\b""")
    private val key309 = Regex("""(?i)(keyevent|keycode|sendkey|injectkey|injectinputevent)[^\n]*\b309\b|\b309\b[^\n]*(keyevent|keycode)""")

    @Test
    fun `quet du cay nguon - khong tu tat`() {
        val files = sources()
        assertTrue(files.size > 300, "bộ quét phải thấy đủ tệp nguồn (đang thấy ${files.size})")
        assertTrue(files.any { it.fileName.toString() == "ProjectionRecipe.kt" } && files.any { it.fileName.toString() == "ClusterProfile.kt" })
        // Mẫu quét thật sự bắt được dạng cần cấm (không phải regex chết).
        assertTrue(themeLiteral.containsMatchIn("service call X 2 i32 1000 i32 30 s16 \"\""))
        assertTrue(svcLiteral.containsMatchIn("val c = \"service call AutoContainer 2\""))
        assertTrue(key309.containsMatchIn("shell(\"input keyevent 309\")"))
    }

    @Test
    fun `khong literal opcode theme i32 29-30-31 ngoai ProjectionRecipe va catalog CarExec`() {
        assertEquals(emptyList<String>(), offenders(themeLiteral))
    }

    @Test
    fun `khong chuoi AutoContainer ngoai ProjectionRecipe va catalog CarExec`() {
        assertEquals(emptyList<String>(), offenders(svcLiteral))
    }

    @Test
    fun `khong bom phim 309 o bat ky dau (ke ca catalog)`() {
        assertEquals(emptyList<String>(), offenders(key309, skipAllowed = false))
    }

    @Test
    fun `opcode cam 17 41 va 6-9 34 211 nam trong FORBIDDEN_OPS`() {
        assertEquals(setOf(6, 7, 8, 9, 17, 34, 41, 211), ProjectionRecipe.FORBIDDEN_OPS)
    }
}
