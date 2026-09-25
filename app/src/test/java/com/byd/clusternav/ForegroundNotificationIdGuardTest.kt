package com.byd.clusternav

import com.byd.clusternav.testsupport.SourceRoots
import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.io.path.name

/**
 * B1 (2026-09-25, `kachi-closeout-hardening` R4 · kiểm kê BG-09/12/23/10): **mọi ID thông báo FGS trong `app/src/main`
 * phải DUY NHẤT**. Hai FGS cùng id ⇒ `stopForeground(STOP_FOREGROUND_REMOVE)` của service này gỡ thông báo của service
 * kia (và hạ foreground im lặng). [ĐO trước vá] 1044 (`VietMapAutostartService` vs `AutomationService`) và 1045
 * (`KachiAutostartService` vs `VoiceKeyKeepAliveService`) trùng.
 *
 * Khuôn quét mã nguồn như [com.byd.clusternav.launcher.GridSeamGuardTest]: không danh sách cứng — quét MỌI tệp có gọi
 * `startForeground(` và lấy hằng numeric `NOTIFICATION_ID` / `NOTIF_ID` / `ID` trong tệp đó (kể cả tệp notice tách riêng).
 * Đỏ→xanh: đổi `KachiAutostartService.NOTIFICATION_ID` về 1045 ⇒ ĐỎ với thông điệp nêu cả hai tệp.
 */
class ForegroundNotificationIdGuardTest {

    private val idRegex = Regex("""(?:NOTIFICATION_ID|NOTIF_ID|\bID)\s*=\s*(\d+)""")

    private fun strip(src: String): String = src
        .replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), " ")
        .lines().joinToString("\n") { it.substringBefore("//") }

    private fun appMainKotlin(): List<Path> {
        val root = SourceRoots.moduleSourceRoots().first { it.toString().contains("app/src/main") }
        Files.walk(root).use { s -> return s.filter { it.name.endsWith(".kt") }.toList() }
    }

    /** `tệp → id` cho mọi tệp khai ID thông báo (tệp gọi `startForeground(` hoặc tệp notice có `const val ID`). */
    private fun declaredIds(): Map<String, List<Int>> = appMainKotlin().mapNotNull { p ->
        val code = strip(p.toFile().readText())
        val ids = idRegex.findAll(code).map { it.groupValues[1].toInt() }.toList()
        if (ids.isEmpty()) null else p.name to ids
    }.toMap()

    @Test
    fun `moi ID thong bao FGS trong app la DUY NHAT`() {
        val byFile = declaredIds()
        val owners = mutableMapOf<Int, MutableList<String>>()
        byFile.forEach { (file, ids) -> ids.forEach { id -> owners.getOrPut(id) { mutableListOf() }.add(file) } }
        val dup = owners.filterValues { it.size > 1 }
        assertTrue(dup.isEmpty(), "ID thông báo TRÙNG (stopForeground(REMOVE) bên này gỡ thông báo bên kia): $dup")
    }

    @Test
    fun `bang ID trong KDoc VoiceKeyKeepAliveService khop voi ma nguon`() {
        // Chống "đạt test" bằng cách đổi ID mà quên bảng nguồn-duy-nhất; và chống quét rỗng (test giả).
        val byFile = declaredIds()
        assertTrue(byFile.size >= 7, "quét phải thấy ≥ 7 tệp FGS (thấy ${byFile.keys})")
        val doc = SourceRoots.text("src/main/java/com/byd/clusternav/VoiceKeyKeepAliveService.kt")
        val tableIds = Regex("""\|\s*(\d{4})\s*\|""").findAll(doc).map { it.groupValues[1].toInt() }.toSet()
        val codeIds = byFile.values.flatten().toSet()
        assertEquals(codeIds, tableIds, "bảng ID trong KDoc VoiceKeyKeepAliveService phải khớp đúng các ID trong mã")
    }
}
