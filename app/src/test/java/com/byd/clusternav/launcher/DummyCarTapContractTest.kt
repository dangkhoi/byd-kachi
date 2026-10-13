package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import java.nio.file.Files
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 3.01 R20 — SỐ GIẢ chỉ cắm được ở bản `vehicleTest` (luật dự án: không dữ liệu giả trên UI phát hành — R3/OQ1).
 *
 * [CarStatusTap] nằm ở `:core` (thuần — LayeringRules) và được gọi ở `main` (chỗ thu trạng thái xe phải gọi nó), nhưng CHỈ mã ở `src/vehicleTest/` được GÁN nó. Bài này
 * quét văn bản mọi `.kt` của `src/main` + `src/debug` + `src/release`: không chỗ nào gọi `CarStatusTap.set(`, không có
 * `DummyCarReceiver`, và manifest chính không khai receiver đó.
 */
class DummyCarTapContractTest {

    private fun kotlinUnder(dir: String): List<java.nio.file.Path> {
        if (!SourceRoots.exists(dir)) return emptyList()
        val root = SourceRoots.path(dir)
        return Files.walk(root).use { s -> s.filter { it.toString().endsWith(".kt") }.toList() }
    }

    @Test
    fun `chi vehicleTest gan duoc so gia`() {
        val shipped = listOf("src/main", "src/debug", "src/release").flatMap { kotlinUnder(it) }
        assertTrue(shipped.isNotEmpty())
        val setters = shipped.filter { Files.readString(it).contains("CarStatusTap.set(") }
        assertEquals(emptyList<java.nio.file.Path>(), setters, "mã phát hành không được cắm số giả")
        assertFalse(shipped.any { it.fileName.toString() == "DummyCarReceiver.kt" })
        assertFalse(Files.readString(SourceRoots.path("src/main/AndroidManifest.xml")).contains("DummyCarReceiver"))
        val vt = kotlinUnder("src/vehicleTest").filter { Files.readString(it).contains("CarStatusTap.set(") }
        assertEquals(listOf("DummyCarReceiver.kt"), vt.map { it.fileName.toString() }, "chỗ cắm duy nhất")
        assertTrue(Files.readString(SourceRoots.path("src/vehicleTest/AndroidManifest.xml")).contains(".DummyCarReceiver"))
    }

    /** POLISH2 — tiếng vọng lệnh ghi của xe giả ([CtlWriteTap]) cũng chỉ vehicleTest cắm được; mặc định không cắm. */
    @Test
    fun `chi vehicleTest cam duoc tieng vong lenh ghi`() {
        val shipped = listOf("src/main", "src/debug", "src/release").flatMap { kotlinUnder(it) }
        assertEquals(emptyList<java.nio.file.Path>(), shipped.filter { Files.readString(it).contains("CtlWriteTap.set(") })
        val vt = kotlinUnder("src/vehicleTest").filter { Files.readString(it).contains("CtlWriteTap.set(") }
        assertEquals(listOf("DummyCarReceiver.kt"), vt.map { it.fileName.toString() })
        assertTrue(CtlWriteTap.echo == null, "không cắm ⇒ đường ghi không gọi gì thêm")
    }

    @Test
    fun `cho thu trang thai xe di qua cho cam - mac dinh khong doi gi`() {
        val wiring = SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/KachiHomeWiring.kt")
        assertTrue(wiring.contains("viewModel.setCarStatus(CarStatusTap.apply(it))"))
        val s = CarStatus()
        assertTrue(CarStatusTap.apply(s) === s, "không cắm ⇒ trả nguyên trạng thái đọc được")
    }
}
