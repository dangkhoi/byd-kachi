package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ P1-main (2026-09-25 · spec `kachi-closeout-hardening` R4(b), audit F10 [P1]) — ô ĐƠN không ghi HAL trên luồng vẽ ═══
 *
 * Bệnh: `ControlTileFactory` gọi `control().toggle/step/coverLevel/select/press` **ngay trong `setOnClickListener`**
 * ⇒ binder HAL đồng bộ trên luồng chính (gói lệnh và giọng nói đã xuống nền từ lâu; ô đơn là chỗ cuối). Bài canh
 * nguồn này ghim đường nối ở `:app` (hành vi của đường ghi đo ở `:core` — `ControlTileWriteTest`, `MacroExecTest`):
 *  • cả năm hàm dựng ô hành động đưa cú ghi qua `writer.submit(` — không còn `control().<ghi>(` nào đứng ngoài nó;
 *  • gói lệnh vẫn đi `MacroExec.submit(` (không đổi hành vi gói); ô đơn đi làn tuần tự `MacroExec.submitSerial`;
 *  • phần VẼ của hoàn nguyên đi qua `tile.post` (revert chạy trên luồng nền, không được chạm view trực tiếp).
 *
 * Vì sao canh nguồn: CLAUDE.md §8 — một lượt thay theo dải dòng có thể trả `control().toggle(` về chỗ cũ mà compile
 * vẫn xanh và chỉ chiếc xe biết (giật khung khi bấm liên tiếp).
 */
class ControlTileOffMainWiringContractTest {

    private val factory by lazy { SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/ControlTileFactory.kt") }

    private val builders = mapOf(
        "private fun tileToggle(" to "control().toggle(",
        "private fun tileStep(" to "control().step(",
        "private fun tileCover(" to "control().coverLevel(",
        "private fun tileSelect(" to "control().select(",
        "private fun tileButton(" to "control().press(",
    )

    @Test
    fun `ca nam kieu o dua cu ghi qua writer submit`() {
        builders.forEach { (sig, write) ->
            val fn = SourceRoots.body(factory, sig)
            assertTrue(fn.contains("writer.submit("), "$sig phải ghi HAL qua `writer.submit(` (làn nền), không gọi thẳng")
            val submitAt = fn.indexOf("writer.submit(")
            val writeAt = fn.indexOf(write)
            assertTrue(writeAt > submitAt, "$sig: `$write` phải nằm TRONG lượt `writer.submit(`, không đứng trước nó trên luồng vẽ")
        }
    }

    @Test
    fun `cu ghi dung mot cua — khong con control() ghi nao ngoai writer`() {
        // Mỗi kiểu ghi xuất hiện đúng MỘT lần trong cả tệp (và bài trên đã ghim lần đó nằm trong `writer.submit(`).
        builders.values.forEach { write ->
            assertEquals(1, Regex(Regex.escape(write)).findAll(factory).count(), "`$write` phải có đúng một chỗ gọi")
        }
    }

    @Test
    fun `hoan nguyen ve luong ve qua post, con goi lenh van di MacroExec submit`() {
        listOf("private fun tileToggle(", "private fun tileStep(", "private fun tileCover(", "private fun tileSelect(").forEach { sig ->
            val fn = SourceRoots.body(factory, sig)
            assertTrue(fn.contains("tile.post {"), "$sig: phần VẼ của hoàn nguyên phải `post` về luồng chính")
        }
        val macro = SourceRoots.body(factory, "fun macroTile(")
        assertTrue(macro.contains("MacroExec.submit("), "gói lệnh giữ nguyên đường cũ")
        assertFalse(macro.contains("writer.submit("), "gói lệnh KHÔNG đi làn ô đơn — nó có chờ giữa các bước, sẽ chặn làn")
        val write = SourceRoots.codeOf("src/main/kotlin/com/byd/clusternav/launcher/ControlTileWrite.kt")
        assertTrue(write.contains("MacroExec.submitSerial("), "đường mặc định của ô đơn là làn TUẦN TỰ trên pool dùng chung (DRY)")
    }

    /**
     * [SOÁT Pass 2 · 2026-09-26] Cổng hoàn nguyên phải hỏi `writeFailureIsReal`, **KHÔNG** `wiredOnThisCar`: hàm sau
     * trả `true` cho cả *"xe có nút"* lẫn *"không biết"* nên off-car/máy ảo mọi ô bấm xong vẫn nảy về (spec OQ3).
     * Hành vi đo ở `:core` (`CarControlAdapterTest`, `ControlTileWriteTest`); bài này ghim đúng CÂU HỎI ở chỗ gọi.
     */
    @Test
    fun `bon o co trang thai hoi writeFailureIsReal, khong hoi wiredOnThisCar`() {
        listOf("private fun tileToggle(", "private fun tileStep(", "private fun tileCover(", "private fun tileSelect(").forEach { sig ->
            val fn = SourceRoots.body(factory, sig)
            assertTrue(
                fn.contains("control().writeFailureIsReal(def.id)"),
                "$sig: cổng hoàn nguyên phải hỏi `writeFailureIsReal` (off-car ⇒ giữ lạc quan)",
            )
        }
        assertFalse(
            factory.contains("wiredOnThisCar"),
            "`wiredOnThisCar` = 'có HOẶC không biết' ⇒ không dùng được làm cổng hoàn nguyên; nó thuộc đường CÂU NÓI của voice",
        )
    }
}
