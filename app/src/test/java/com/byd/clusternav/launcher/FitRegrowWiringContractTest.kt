package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ 2.93 wave 2A · FIT-REGROW — DÂY NỐI phía `:app`: dấu chờ lớn lại GIỮ tới khi ô về ĐẦY ([FitScale.full]) ═══════════════════
 *
 * Luật thuần + máy trạng thái (`99 → 100 → (30 s, vẫn 100) → 99` · `1000 → 100 → 99` · lượt khớp đủ của ô khác) khoá ở
 * `FitRegrowRearmTest` (`:core`). Bài `:core` chép thứ tự nhánh của [FitGridLayout] nhưng không canh chính dòng nối ở `:app` (senior
 * review wave 2A Pass 1 [P3]) — bài này khoá dòng nối (mã đã bỏ chú thích — nhắc tên trong KDoc không tính):
 *  - `regrowOrShrink`: lượt lớn lại chạy luật giá trị TRƯỚC, rồi mới hỏi [FitScale.full] (đổi chỗ = hỏi trạng thái cũ); không xoá
 *    dấu vô điều kiện; ghi chú (b): không còn lấy dấu từ "lượt có đổi không" (lớn MỘT PHẦN 1000 → 100 vẫn đổi);
 *  - `refit`: lượt khớp đủ đặt dấu cùng luật — bản trước `shrunkAt = null` vô điều kiện (lượt khớp do ô KHÁC chạy lúc chữ rộng);
 *  - [FitScale.full] gồm hàng ngang ([FitValueRow.roomy] → [FitValues.roomy]) + mọi chữ ở cỡ lưới; [FitValueRow.roomy] dùng CÙNG
 *    nhu cầu chú thích với phép chia ([FitValueRow.fit]).
 */
class FitRegrowWiringContractTest {

    private fun code(file: String) = SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/$file")

    private val layout by lazy { code("FitGridLayout.kt") }
    private val scale by lazy { code("FitScale.kt") }
    private val row by lazy { code("FitValueRow.kt") }

    @Test
    fun `luot lon lai - chay luat gia tri roi moi hoi da day chua, khong xoa dau vo dieu kien`() {
        val rs = SourceRoots.body(layout, "private fun regrowOrShrink(child: View)")
        val pass = rs.indexOf("fs.fitValues(floor, legible, tick = false)")
        val next = rs.indexOf("it.shrunkAt = FitValues.regrowNext(fs.full(), now)")
        assertTrue(pass >= 0 && next > pass, "luật giá trị chạy TRƯỚC, dấu đặt theo ô đã ĐẦY chưa:\n$rs")
        assertFalse(rs.contains("it.shrunkAt = null"), "xoá dấu vô điều kiện = lỗi FIT-REGROW cũ (99 đứng ở cỡ của 100)")
        assertFalse(rs.contains("regrowNext(fs.fitValues("), "ghi chú (b): 'có đổi' ≠ 'đã đầy' — lớn một phần 1000 → 100 vẫn đổi")
    }

    @Test
    fun `luot khop du - dat dau cung luat, khong xoa vo dieu kien`() {
        val refit = SourceRoots.body(layout, "private fun refit(w: Int, h: Int)")
        val values = refit.indexOf("c.fs.fitValues(floors.textPx, f.legible)")
        val mark = refit.indexOf("it.shrunkAt = it.fs?.let { fs -> FitValues.regrowNext(fs.full(), now) }")
        assertTrue(values >= 0 && mark > values, "dấu đặt SAU lượt luật giá trị cuối của lượt khớp:\n$refit")
        assertFalse(refit.contains("it.shrunkAt = null"), "lượt khớp do ô KHÁC chạy lúc chữ rộng xoá dấu ⇒ 99 đứng ở cỡ của 100")
    }

    @Test
    fun `full - hang ngang khong ai nhuong + moi chu o co luoi`() {
        val full = SourceRoots.body(scale, "fun full(): Boolean")
        assertTrue(full.contains("row?.roomy() != false"), full)
        assertTrue(full.contains("texts().all { autoSized(it) || abs(it.textSize - basePx(it) * scale.toFloat()) <= 0.01f }"), full)
        val roomy = SourceRoots.body(row, "fun roomy(): Boolean")
        assertTrue(roomy.contains("valueW < 0 ||"), "khối dọc ⇒ đầy theo cỡ (FitScale.full)")
        assertTrue(roomy.contains("FitValues.roomy(FitValues.Share(valueW, !yielded), flex(), captionNeed("), "chỗ ĐANG áp, kể cả chỗ nhịp giữ")
        val fit = SourceRoots.body(row, "fun fit(rot: Boolean, fitPx: Float, legible: Boolean, floorPx: Float, tick: Boolean)")
        assertTrue(fit.contains("flex, needAt(px), captionNeed(shown),"), "phép chia và phép hỏi đầy dùng CÙNG nhu cầu chú thích")
    }

    @Test
    fun `ham moi co cho goi that, tep duoi 500 dong`() {
        assertTrue(layout.split("regrowNext(fs.full(), now)").size - 1 == 2, "hai chỗ gọi: lượt lớn lại + lượt khớp đủ")
        assertTrue(scale.contains("row?.roomy()"), "FitValueRow.roomy có chỗ gọi")
        assertTrue(row.contains("FitValues.roomy("), "FitValues.roomy có chỗ gọi")
        listOf("FitGridLayout.kt", "FitScale.kt", "FitValueRow.kt").forEach { f ->
            val n = SourceRoots.text("src/main/java/com/byd/clusternav/launcher/$f").lines().size
            assertTrue(n <= 500, "$f dài $n dòng — trần là 500 (CLAUDE.md §4.1)")
        }
    }
}
