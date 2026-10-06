package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ 2.93 wave 2A · SHORTCUT-SCROLL-REBUILD — DÂY NỐI: vị trí cuộn `w_apps` qua lượt dựng VIEW MỚI của ô ═════════════════
 *
 * Luật thuần + số QA khoá ở `ShortcutScrollMemoryTest` (`:core`). Bài này khoá đường NỐI phía `:app` (không Robolectric ⇒ canh
 * mã đã bỏ chú thích): (1) ô widget truyền khoá Ô (chỉ số + tổ hợp) vào bộ dựng; (2) cả hai đường dựng `w_apps` (ô to · ô nén)
 * nhận khoá; (3) view mới đọc bản nhớ khi KHÔNG có khung cũ; (4) chỉ cú cuộn của NGƯỜI LÁI ghi bản nhớ (cùng chỗ duy nhất ghi
 * `wanted`). Lỗi khoá: senior review SLOT Pass 2 mục 4 [SUY đọc mã] — đổi Sáng/Tối / đơn vị / Activity dựng lại ⇒ dải về đầu.
 */
class ShortcutScrollRebuildWiringContractTest {

    private fun code(file: String) = SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/$file")

    @Test
    fun `o widget truyen khoa o, ca hai duong dung w_apps nhan khoa`() {
        val slot = SourceRoots.body(code("WorkspaceView.kt"), "private fun makeSlot(index: Int, content: SlotContent): View")
        assertTrue(slot.contains("WidgetViews.buildGrid(context, content.ids, widgetData(), ShortcutScrollMemory.slotKey(index, content.ids))"))
        val views = code("WidgetViews.kt")
        assertEquals(2, Regex("""ShortcutIconsView\(ctx, grid = true(, compact = true)?, scrollKey = scrollKey\)""").findAll(views).count(),
            "ô to + ô nén đều nhận khoá")
        val grid = SourceRoots.body(views, "fun buildGrid(ctx: Context, ids: List<String>, data: WidgetData, scrollKey: String? = null): View")
        assertTrue(grid.contains("build(ctx, list[0], data, scrollKey)") && grid.contains("mini(ctx, id, data, scrollKey)"))
    }

    @Test
    fun `view moi doc ban nho, chi cu cuon cua nguoi lai ghi`() {
        val view = code("ShortcutIconsView.kt")
        assertTrue(SourceRoots.body(view, "private fun rebuild()").contains("scrollKey?.let(ShortcutScrollMemory::recall)"))
        assertTrue(SourceRoots.body(view, "private fun buildGrid(items: List<AppShortcut>, keep: ShortcutScrollKeep.Wanted)")
            .contains("ShortcutScrollMemory.remember(it, w)"))
        val layout = code("ShortcutGridLayout.kt")
        val scroll = SourceRoots.body(layout, "private fun scrollAlongTo(p: Int)")
        assertTrue(scroll.indexOf("wanted = ShortcutScrollKeep.userScrolled(axis, c)") < scroll.indexOf("onUserScroll(wanted)"),
            "báo SAU khi ghi lựa chọn mới")
        assertEquals(1, Regex("""onUserScroll\(""").findAll(layout).count(), "chỉ cú cuộn của người lái báo (không lượt đo nào)")
        assertEquals(1, Regex("""ShortcutScrollMemory\.remember\(""").findAll(view).count())
    }
}
