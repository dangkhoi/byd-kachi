package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.KotlinSource
import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files

/**
 * ═══ 2.93 · EDGE-H-DEAD — mọi hằng của THANG cỡ (`KachiSpace` + `KachiBars`) phải có ít nhất MỘT chỗ đọc ═══════════
 *
 * [ĐO grep app+core 05/10] `KachiSpace.EDGE_H = 13` (lề ngang 80 %, lời giao owner 20/09) khai từ 1.87 mà không ai đọc —
 * KDoc của nó còn kể một bài canh `HomeEdgeInsetContractTest` không tồn tại (2.93 wave 2A · HOME-EDGE-80: nay nối thật
 * + có bài). Soát cùng lượt thấy thêm ba hằng cùng bệnh (`DASH_ON`, `DASH_OFF`, `CAPTION_COVER`). Hằng chết trong thang là lời hứa sai về màn hình: người đọc tin lề/nét đó
 * đang được vẽ. Bài này đỏ khi thang có hằng 0 chỗ dùng (ngoài chính dòng khai), kể cả hằng mới thêm mà quên nối.
 *
 * Quét mã đã bỏ chú thích bằng bộ quét DÙNG CHUNG [KotlinSource.stripComments] (giữ string literal), trên mọi gốc mã của
 * [SourceRoots.moduleSourceRoots] — một hằng chỉ được nhắc trong KDoc không tính là "được dùng".
 */
class ScaleConstantsUsedContractTest {

    private val scaleFiles = listOf("KachiSpace.kt", "KachiSpaceBars.kt")

    private val code: Map<String, String> by lazy {
        SourceRoots.moduleSourceRoots().flatMap { root ->
            Files.walk(root).use { s ->
                s.filter { it.toString().endsWith(".kt") }
                    .map { it.toAbsolutePath().normalize().toString() to KotlinSource.stripComments(it.toFile().readText()) }
                    .toList()
            }
        }.toMap()
    }

    private fun declared(file: String): List<String> =
        Regex("""const val ([A-Z][A-Z0-9_]*)\s*=""")
            .findAll(KotlinSource.stripComments(SourceRoots.text("src/main/java/com/byd/clusternav/launcher/$file")))
            .map { it.groupValues[1] }.toList()

    /** Số lần [name] xuất hiện như một định danh trong toàn bộ mã — trừ đúng một lần của dòng khai. */
    private fun uses(name: String): Int =
        code.values.sumOf { Regex("""\b$name\b""").findAll(it).count() } - 1

    @Test
    fun `moi hang cua thang deu co it nhat mot cho doc`() {
        val dead = scaleFiles.flatMap { f -> declared(f).filter { uses(it) <= 0 }.map { "$f:$it" } }
        assertEquals(emptyList<String>(), dead, "hằng của thang không ai đọc — xoá, hoặc nối nó vào chỗ vẽ thật")
    }

    /**
     * 2.93 wave 2A · HOME-EDGE-80 (đổi ghim CÓ LÝ DO): `EDGE_H` được KHAI LẠI cùng chỗ đọc thật (lề ngang khung màn chính,
     * `KachiHomeActivity`) — đúng điều kiện câu thông báo cũ đặt ra ("không khai lại khi CHƯA có chỗ vẽ"). Bài `moi hang…` ở trên
     * vẫn đỏ nếu nó mất chỗ đọc; vị trí + giá trị khoá ở `HomeEdgeInsetContractTest`. Ba hằng chết còn lại vẫn cấm khai lại.
     */
    @Test
    fun `ba hang chet cua dot 2_93 da xoa khoi thang`() {
        val all = scaleFiles.flatMap(::declared)
        assertTrue(all.size >= 20, "đọc thiếu hằng của thang (${all.size}) — đường dẫn tệp thang đổi? sửa bài này")
        listOf("DASH_ON", "DASH_OFF", "CAPTION_COVER").forEach {
            assertTrue(it !in all, "$it là hằng chết (EDGE-H-DEAD) — đã xoá, không khai lại khi chưa có chỗ vẽ")
        }
        assertTrue("EDGE_H" in all && uses("EDGE_H") > 0, "EDGE_H (HOME-EDGE-80) phải có chỗ đọc thật — HomeEdgeInsetContractTest")
    }
}
