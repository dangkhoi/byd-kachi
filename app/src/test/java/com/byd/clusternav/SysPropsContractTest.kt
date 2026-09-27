package com.byd.clusternav

import com.byd.clusternav.testsupport.SourceRoots
import java.nio.file.Files
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ MỘT cửa reflection vào `SystemProperties` — khoá nợ DRY 2.75 ═══
 *
 * Bối cảnh [ĐO đọc mã 2.75]: `AvmCamera.systemProp` · `SeatComfortApplier.systemProp` · `ClusterProfile.getProp` là ba
 * bản sao `private` của cùng một phép `Class.forName(...)` + `getMethod("get")` (KDoc `AvmCamera` 2.75 tự ghi nợ này vì
 * hai tệp kia thuộc làn khác). L6-debt 2026-09-27 gộp về [SysProps.get]. Bài này canh cả hai chiều:
 *  1. đúng **MỘT** chuỗi tên lớp đầy đủ trong `app/src/main` + `core/src/main` (kể cả chú thích — ai ghi lại tên lớp
 *     ở chỗ khác là lại mở cửa cho bản sao thứ tư), và chuỗi đó nằm ở `SysProps.kt`;
 *  2. ba nơi cũ **thật sự gọi** `SysProps.get(` (CLAUDE.md §8: hàm mới phải có chỗ gọi), không giữ reflection riêng.
 */
class SysPropsContractTest {

    private val fqn = "android.os." + "SystemProperties"   // ghép để chính bài này không tự cộng thêm một lần đếm

    @Test
    fun `dung mot chuoi ten lop SystemProperties, nam o SysProps`() {
        val hits = SourceRoots.moduleSourceRoots().flatMap { root ->
            Files.walk(root).use { s -> s.filter { it.toString().endsWith(".kt") }.toList() }
        }.flatMap { f -> List(Files.readString(f).split(fqn).size - 1) { f } }
        assertEquals(
            listOf("SysProps.kt"), hits.map { it.fileName.toString() },
            "chuỗi `$fqn` phải xuất hiện ĐÚNG MỘT lần trong app/src/main + core/src/main (dòng Class.forName của SysProps); thấy: $hits",
        )
        val sysProps = SourceRoots.codeOf("src/main/java/com/byd/clusternav/SysProps.kt")
        assertTrue(sysProps.contains("Class.forName(\"$fqn\")"), "SysProps phải là chỗ giữ phép reflection")
        assertTrue(sysProps.contains("getOrDefault(\"\")"), "lỗi/off-car ⇒ \"\" (hợp đồng cũ của cả ba bản sao)")
    }

    @Test
    fun `ba noi cu uy quyen xuong SysProps get`() {
        mapOf(
            "src/main/java/com/byd/clusternav/launcher/camera/AvmCamera.kt" to "fun systemProp(key: String): String = SysProps.get(key)",
            "src/main/java/com/byd/clusternav/comfort/SeatComfortApplier.kt" to "SysProps.get(key).takeIf { it.isNotBlank() }",
            "src/main/java/com/byd/clusternav/modules/clustercast/ClusterProfile.kt" to "private fun getProp(key: String): String = SysProps.get(key)",
        ).forEach { (rel, call) ->
            val code = SourceRoots.codeOf(rel)
            assertTrue(code.contains(call), "$rel phải uỷ quyền xuống SysProps: thiếu `$call`")
            assertTrue(!code.contains("Class.forName(\"$fqn\")"), "$rel không được giữ reflection riêng nữa")
        }
    }
}
