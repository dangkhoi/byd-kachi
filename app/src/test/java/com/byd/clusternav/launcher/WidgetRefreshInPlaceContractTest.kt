package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Khoá bản vá **[SOÁT P1-1] ô TRỘN không bị dựng lại theo nhịp trạng thái xe**.
 *
 * ## Bệnh
 * Trên xe trạng thái đổi ~1 lần/giây. Luật cũ: ô widget có **bất kỳ** mục ĐỌC ⇒ dựng lại **CẢ Ô**. Với ô TRỘN
 * (ví dụ áp suất lốp + nút "Đóng hết kính", hoặc trình chiếu ảnh + tốc độ):
 *  • nút bị tháo/gắn giữa cú chạm ⇒ **mất cú bấm**, và chốt chống-bấm-kép (nếu nằm trong View) bị đặt lại ⇒ hai lượt
 *    gói lệnh chạy chồng nhau;
 *  • widget trình chiếu bị dựng lại ⇒ trạng thái quay vòng đặt lại (**ảnh đứng một tấm**) + mỗi giây một lượt đọc
 *    tệp & giải mã ảnh **trên thread chính**.
 * Bản vá trước chỉ cứu ô mà **mọi** mục là trình chiếu ⇒ ô trộn vẫn hỏng.
 *
 * ## Vì sao khoá bằng quét mã
 * Ca này **không quan sát được off-car** (không xe ⇒ trạng thái luôn rỗng ⇒ "trạng thái đổi" luôn false) và dự án
 * không dùng Robolectric nên không dựng được cây View trong test JVM. Vậy khoá phần **nối dây** — thứ off-car
 * chứng minh được.
 */
class WidgetRefreshInPlaceContractTest {

    private val widgets by lazy { SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/WidgetViews.kt") }
    private val view by lazy { SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/WorkspaceView.kt") }
    private val factory by lazy { SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/ControlTileFactory.kt") }

    @Test
    fun `moi o con mang THE id de doi duoc dung con can doi`() {
        assertTrue(widgets.contains("WidgetTag("), "phải gắn thẻ cho từng ô con")
        val grid = SourceRoots.body(widgets, "fun buildGrid(")
        assertEquals(
            2, Regex("WidgetTag\\(").findAll(grid).count(),
            "cả hai nhánh dựng (một-widget và lưới nhiều-widget) đều phải gắn thẻ, không thì nhánh thiếu thẻ sẽ " +
                "âm thầm rơi về dựng lại cả ô",
        )
    }

    @Test
    fun `lam moi TAI CHO chi thay o con la muc DOC`() {
        val fn = SourceRoots.body(widgets, "fun refreshRead(")
        assertTrue(fn.contains("CapabilityCatalog.isWrite("), "phải GIỮ view của nút HÀNH ĐỘNG")
        assertTrue(
            fn.contains("WorkspaceRenderPlanner.selfDriven("),
            "phải GIỮ view của widget tự-lo-nội-dung (trình chiếu ảnh) — thay nó là đặt lại vòng quay ảnh",
        )
        assertTrue(fn.contains("removeViewAt") && fn.contains("addView("), "phải thay ĐÚNG CHỖ, giữ thứ tự")
        assertTrue(fn.contains("indexOfChild"), "phải chèn lại vào đúng chỉ số cũ")
    }

    @Test
    fun `nhip trang thai xe di duong LAM MOI truoc, chi lui ve dung lai khi khong the`() {
        val fn = SourceRoots.body(view, "private fun renderInternal(")
        assertTrue(
            fn.contains("WorkspaceRenderPlanner.sameContent("),
            "phải phân biệt 'nội dung ô đổi' với 'chỉ số liệu đổi' — dùng luật ở :core, KHÔNG chép lại",
        )
        assertTrue(fn.contains("WidgetViews.refreshRead("), "phải thử làm mới tại chỗ")
        val refreshAt = fn.indexOf("WidgetViews.refreshRead(")
        val removeAt = fn.indexOf("removeView(slotViews[i])")
        assertTrue(
            refreshAt in 1 until removeAt,
            "làm mới tại chỗ phải được thử TRƯỚC khi tháo view — đảo thứ tự là mất cú bấm như cũ",
        )
    }

    @Test
    fun `chot chong bam kep khong nam trong View`() {
        // Nếu chốt sống trong ô thì mọi lần dựng lại ô là một lần mở lại cửa cho cú bấm thứ hai.
        val tile = SourceRoots.body(factory, "fun macroTile(")
        assertFalse(tile.contains("AtomicBoolean("), "chốt KHÔNG được tạo trong thân ô")
        assertTrue(tile.contains("state.beginRun("), "phải dùng chốt ở bảng trạng thái dùng chung")
        assertTrue(tile.contains("state.endRun("), "và nhả nó")
    }

    @Test
    fun `luat tu-lo-noi-dung chi khai o MOT cho`() {
        val plan = SourceRoots.codeOf("src/main/kotlin/com/byd/clusternav/launcher/WorkspaceRenderPlan.kt")
        assertEquals(
            1, Regex("SELF_DRIVEN = ").findAll(plan).count(),
            "danh sách widget tự-lo-nội-dung phải khai đúng một chỗ (:core)",
        )
        assertFalse(
            widgets.contains("SELF_DRIVEN"),
            "tầng vẽ KHÔNG được có bản danh sách riêng — phải hỏi qua WorkspaceRenderPlanner.selfDriven",
        )
    }
}
