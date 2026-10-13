package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import java.nio.file.Files
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 3.01 R25 — ẢNH NỀN xe 3D (owner 13/10 duyệt): app KHÔNG chở ảnh nào; ảnh chỉ đến từ thư mục hình nền SẴN CÓ của người dùng
 * ([WallpaperStore]), tên tệp lọc ở `:core` ([Car3dBackdrop.validName]) + kiểm thư mục cha tại chỗ đọc.
 */
class Car3dBackdropContractTest {

    private val src = "src/main/java/com/byd/clusternav/launcher/Car3dBackdropView.kt"

    @Test
    fun `khong anh nen nao duoc dong goi - lop nen chi doc thu muc hinh nen`() {
        val shipped = listOf("src/main", "src/debug", "src/release").filter(SourceRoots::exists).flatMap { d ->
            Files.walk(SourceRoots.path(d)).use { s -> s.filter { Files.isRegularFile(it) }.toList() }
        }
        val bundled = shipped.filter { p -> val n = p.fileName.toString().lowercase(); Regex("(car3d|backdrop)").containsMatchIn(n) && Regex("\\.(png|jpe?g|webp)$").containsMatchIn(n) }
        assertEquals(emptyList<Any>(), bundled)
        val code = Files.readString(SourceRoots.path(src))
        assertTrue(code.contains("WallpaperStore.folder(") && code.contains("WallpaperStore.loadScaled("), "nguồn ảnh duy nhất = thư mục hình nền")
        assertFalse(Regex("""assets\.open|R\.drawable|getIdentifier|decodeResource""").containsMatchIn(code), "không ảnh đóng gói / tài nguyên")
        assertTrue(code.contains("canonicalPath"), "kiểm thư mục cha tại chỗ đọc (không đi ra ngoài thư mục ảnh)")
    }

    @Test
    fun `chi giai ma mot lan cho moi tep va co khung - can anh chi doi ma tran`() {
        val code = Files.readString(SourceRoots.path(src))
        assertTrue(code.contains("if (key == loadedKey || key == loading) return"), "giải mã một lần cho (tệp, cỡ khung)")
        val draw = code.substringAfter("override fun onDraw").substringBefore("// ── Chế độ căn")
        assertFalse(Regex("loadScaled|decodeFile|Thread\\(").containsMatchIn(draw), "onDraw không giải mã / không dựng luồng")
    }
}
