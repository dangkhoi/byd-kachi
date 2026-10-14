package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ R30 — BUNG Ô (owner 14/10) — DÂY NỐI ═══ spec `kachi-301-cast-steps-dialog-ia.html` R30 / §4.8.
 *
 * Phần thuần (máy trạng thái, khung, luật có nút) có bài ở `:core` (`SlotExpandTest` · `SlotHeadActionsTest`). `:app` không có
 * Robolectric ⇒ bài này canh MÃ (đã bỏ chú thích, [SourceRoots.codeOf]) — mỗi khẳng định là một mắt xích bài thuần không thấy:
 *  - hàm mới có chỗ gọi thật (CLAUDE.md §8);
 *  - khung đi qua ĐÚNG `effectiveRects` ⇒ `onMeasure`/`onLayout`/`pushSlotSize` → `VdAppHost.resize` (không đường đổi cỡ thứ hai);
 *  - mọi đường dựng lại khung + đổi hồ sơ + Back + HOME thu TRƯỚC khi tháo view (R30.3);
 *  - đường bung / thu: 0 lệnh shell, 0 ghi prefs / ViewModel / kho, không luồng, không hẹn giờ (R30.4–R30.5);
 *  - khung ẩn che CHÍNH mặt vẽ màn ảo (SurfaceView không theo cha) và không nhận chạm đầu ô.
 */
class SlotExpandWiringContractTest {

    private fun code(name: String) = SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/$name")

    private val ws by lazy { code("WorkspaceView.kt") }
    private val expand by lazy { code("WorkspaceExpand.kt") }

    private fun order(src: String, vararg parts: String) {
        var at = -1
        parts.forEach { p ->
            val i = src.indexOf(p, at + 1)
            assertTrue(i > at, "thứ tự sai / thiếu '$p' trong: ${src.take(400)}")
            at = i
        }
    }

    @Test
    fun `moi ham moi co cho goi production`() {
        mapOf(
            "workspace().expand.toggle(index)" to "KachiHomeSlotActions.kt",
            "workspace().expand.expanded == index" to "KachiHomeSlotActions.kt",
            "multi = workspace().slotViews.size > 1" to "KachiHomeSlotActions.kt",
            "workspace.expand.drop(SlotExpand.Why.BACK)" to "KachiHomeActivity.kt",
            "workspace.expand.drop(SlotExpand.Why.HOME)" to "KachiHomeActivity.kt",
            "expand.rects(" to "WorkspaceView.kt",
            "expand.laidOut()" to "WorkspaceView.kt",
            "state.toggle(index, ws.slotViews.size)" to "WorkspaceExpand.kt",
            "state.clear(why)" to "WorkspaceExpand.kt",
            "state.rects(base, w, h)" to "WorkspaceExpand.kt",
            "h.veil(true)" to "WorkspaceExpand.kt",
            "it.veil(false)" to "WorkspaceExpand.kt",
            "port.expanded(index)" to "SlotActionsCluster.kt",
            "glyph(it, index, port.expanded(index))" to "SlotActionsCluster.kt",
            "SlotExpand.offered(kind, projector)" to "SlotHeadActions.kt",
            "\"ic-expand\" -> R.drawable.ic_expand" to "KachiTheme.kt",
            "\"ic-collapse\" -> R.drawable.ic_collapse" to "KachiTheme.kt",
        ).forEach { (needle, file) ->
            val src = if (file == "SlotHeadActions.kt") SourceRoots.codeOf("src/main/kotlin/com/byd/clusternav/launcher/$file") else code(file)
            assertTrue(needle in src, "$file thiếu '$needle'")
        }
    }

    /** Một đường đổi cỡ: ô bung đi qua khung của `effectiveRects` ⇒ `onLayout` → `pushSlotSize` → `VdAppHost.resize` (trùng cỡ = no-op). */
    @Test
    fun `khung bung di qua dung duong do bo cuc san co`() {
        val rects = SourceRoots.body(ws, "private fun effectiveRects(")
        assertTrue("expand.rects(EffectiveLayout.rects(displayed.preset, customLayout, w, h, gapPx), w, h)" in rects)
        order(SourceRoots.body(ws, "override fun onLayout("), "val rects = effectiveRects(w, h)", "pushSlotSize(i, rect.width, rect.height)", "expand.laidOut()")
        assertTrue("val rects = effectiveRects(w, h)" in SourceRoots.body(ws, "override fun onMeasure("), "ô bung được ĐO bằng khung mới (widget khớp lại)")
        assertFalse(".resize(" in expand, "WorkspaceExpand không tự đổi cỡ màn ảo — chỉ requestLayout")
        assertTrue("ws.requestLayout()" in expand)
    }

    /** R30.3 — thu TRƯỚC khi tháo/dựng view ở mọi đường; khai `expand` trước `init` (rebuild gọi drop). */
    @Test
    fun `moi duong dung lai khung deu thu truoc`() {
        assertTrue(ws.indexOf("internal val expand = WorkspaceExpand(this)") in 0 until ws.indexOf("init { rebuild() }"), "khai trước init")
        order(SourceRoots.body(ws, "private fun rebuild()"), "expand.drop(SlotExpand.Why.REBUILD)", "releaseAppHosts()")
        order(SourceRoots.body(ws, "fun restyle()"), "expand.drop(SlotExpand.Why.REBUILD)", "removeView(")
        order(SourceRoots.body(ws, "private fun rebuildWidgetSlots("), "expand.drop(SlotExpand.Why.REBUILD)", "removeView(")
        order(SourceRoots.body(ws, "fun setCustomLayout("), "expand.drop(SlotExpand.Why.LAYOUT)", "customLayout = layout")
        val render = SourceRoots.body(ws, "private fun renderInternal(")
        order(render, "if (profileSwitch) expand.drop(SlotExpand.Why.PROFILE)", "WorkspaceRenderPlanner.decide(")
        order(render, "expand.drop(SlotExpand.Why.REBUILD)", "releaseSlotHost(i)", "removeView(slotViews[i])")
        // Lượt chỉ làm mới số (refreshRead) KHÔNG thu — nó trả sớm TRƯỚC dòng thu (nhịp 1 s của trạng thái xe).
        order(render, "WidgetViews.refreshRead(slotViews[i], widgetData()) > 0", "return@forEach", "expand.drop(SlotExpand.Why.REBUILD)")
        val back = SourceRoots.body(code("KachiHomeActivity.kt"), "override fun onBackPressed()")
        order(back, "drawerController.isOpen()", "workspace.expand.expanded != null -> workspace.expand.drop(SlotExpand.Why.BACK)")
        assertTrue("intent?.hasCategory(android.content.Intent.CATEGORY_HOME) == true" in SourceRoots.body(code("KachiHomeActivity.kt"), "override fun onNewIntent("))
    }

    /** R30.4/R30.5 — 0 lệnh shell, 0 ghi bền, không luồng/hẹn giờ trong đường bung/thu. */
    @Test
    fun `duong bung thu khong shell, khong ghi prefs, khong luong`() {
        listOf("shell", "\"am ", "\"wm ", "Runtime", "ProcessBuilder", "SharedPreferences", ".edit(", "putInt", "putString", "viewModel",
            "Repository", "Prefs", "Thread", "Handler", "postDelayed", "Timer", "Executor", "submitBg").forEach { bad ->
            assertFalse(bad in expand, "WorkspaceExpand.kt có '$bad'")
        }
        val onAction = SourceRoots.body(code("KachiHomeSlotActions.kt"), "override fun onAction(")
        val line = onAction.lines().single { "Button.EXPAND ->" in it }
        assertFalse("shell" in line || "allowOrPrompt" in line || "viewModel" in line, "nút bung không đi qua kênh / ViewModel: $line")
        val veil = SourceRoots.body(code("VdAppHost.kt"), "fun veil(")
        assertTrue("surface.visibility" in veil && "shell" !in veil && "release" !in veil.replace("released", ""), "veil chỉ đổi mặt vẽ")
    }

    /** Khung ẩn không nhận cú chạm đầu ô của ô bung (khung ẩn vẫn giữ chỗ cũ, nằm DƯỚI ô bung). */
    @Test
    fun `cham dau o chi xet khung dang hien`() {
        val slotAt = SourceRoots.body(code("SlotHeadAutoHide.kt"), "private fun slotAt(")
        assertTrue("it.visibility == View.VISIBLE" in slotAt)
        val apply = SourceRoots.body(expand, "private fun apply(")
        order(apply, "v.visibility = View.INVISIBLE", "h.veil(true)", "veiled.forEach { it.veil(false) }", "it.visibility = View.VISIBLE", "ws.heads.refreshAll()")
    }

    @Test
    fun `chuoi moi du nam tieng va icon sinh tu glyph`() {
        listOf("values", "values-en", "values-zh-rCN", "values-th", "values-ms").forEach { f ->
            val xml = SourceRoots.text("src/main/res/$f/strings_kachi.xml")
            listOf("kachi_slot_expand", "kachi_slot_collapse").forEach { k ->
                val m = Regex("<string name=\"$k\">([^<]*)</string>").find(xml)
                assertTrue(m != null, "$f thiếu $k")
                assertEquals(1, Regex("%1\\\$d").findAll(m!!.groupValues[1]).count(), "$f/$k phải mang số ô")
            }
        }
        listOf("ic_expand", "ic_collapse").forEach { n ->
            assertTrue("SINH BỞI scripts/design/gen-icons.py" in SourceRoots.text("src/main/res/drawable/$n.xml"), "$n sinh bằng gen-icons.py")
        }
    }
}
