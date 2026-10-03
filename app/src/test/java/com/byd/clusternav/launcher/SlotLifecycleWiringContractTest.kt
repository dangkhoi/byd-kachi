package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ L6 · vòng đời ô (owner 03/10) — DÂY NỐI: (a) app chết ⇒ trong suốt · (b) hết đặt tạm ⇒ về hồ sơ · (c) chạy nền / tắt ═══
 *
 * Phần THUẦN có bảng đủ ô ở `:core` (`SlotRevertPlanTest` 240 ô · `SlotHeadActionsTest` 60 ô · `SlotCloseTest` trên dump
 * thật). `:app` không có Robolectric ⇒ bài này canh MÃ (đã bỏ chú thích, [SourceRoots.codeOf]) — mỗi khẳng định là một mắt
 * xích mà bài thuần không thấy:
 *  - hàm mới phải có chỗ gọi thật (CLAUDE.md §8 — `CastShell.evictVd` compile sạch mà 0 call site);
 *  - host thôi giữ app TRƯỚC khi state đổi (thiếu ⇒ lượt render nhả ô ⇒ `release()` `am force-stop` cả gói);
 *  - *tắt* = lệnh dựng ở `:core` (`FloatingOrphanPlan.removeCmd`), chạy trên luồng nền, sau cổng kênh, KHÔNG force-stop;
 *  - *chạy nền* đi đúng đường công khai sẵn có (đổi tại chỗ → `onAppSwapped` → `evictBehind`), không chạm BehindHome*;
 *  - nút đi cùng nhịp nghỉ của ⇄ và giữ luật cancel-trước-animate; đích chạm 48 dp; mô tả đủ năm tiếng.
 */
class SlotLifecycleWiringContractTest {

    private fun code(name: String) = SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/$name")
    private fun core(name: String) = SourceRoots.codeOf("src/main/kotlin/com/byd/clusternav/launcher/$name")

    private val actions by lazy { code("KachiHomeSlotActions.kt") }
    private val cluster by lazy { code("SlotActionsCluster.kt") }
    private val heads by lazy { code("SlotHeadAutoHide.kt") }
    private val host by lazy { code("VdAppHost.kt") }

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
            "SlotRevertPlan.next(" to "HomeViewModel.kt",
            "SlotRevertPlan.overlayAfter(" to "HomeViewModel.kt",
            "SlotRevertPlan.backgroundable(" to "KachiHomeSlotActions.kt",
            "SlotHeadActions.of(" to "KachiHomeSlotActions.kt",
            "viewModel.slotRevert(" to "KachiHomeSlotActions.kt",
            "viewModel.applySlotRevert(" to "KachiHomeSlotActions.kt",
            "?.relinquish(pkg)" to "KachiHomeSlotActions.kt",
            "closer.run(sh, stage.vd, pkg)" to "KachiHomeSlotActions.kt",
            "SlotHeadActions.possible(" to "SlotActionsCluster.kt",
            "SlotActionsCluster.attach(" to "SlotHeadAutoHide.kt",
            "heads.actions = slotActions" to "KachiHomeActivity.kt",
            "heads.actions?.onAppGone(index, p)" to "WorkspaceView.kt",
            "VdTouchExec.TOUCH_FALLBACK.execute" to "VdAppHost.kt",
        ).forEach { (call, file) -> assertTrue(call in code(file), "'$call' phải được gọi trong $file") }
        assertTrue("StackReads.settle(" in core("FloatingOrphanSweep.kt") && "StackReads.settle(" in core("SlotClose.kt"),
            "một vòng đọc-lại cho cả hai lượt gỡ stack (DRY)")
    }

    @Test
    fun `mot cua luat hoan o - quyet, host tha app, roi moi doi state`() {
        val fn = SourceRoots.body(actions, "private fun revert(")
        order(fn, "viewModel.slotRevert(index, event, pkg)", "if (next == Next.Keep) return",
            "if (pkg != null && !(next is Next.ShowSaved && next.swapInPlace)) workspace().hostAt(index)?.relinquish(pkg)",
            "viewModel.applySlotRevert(index, next)")
        assertTrue("revert(index, Event.APP_DIED, pkg)" in SourceRoots.body(actions, "override fun onAppGone("))
        val vm = code("HomeViewModel.kt")
        assertFalse("persist" in SourceRoots.body(vm, "fun applySlotRevert("), "luật hoàn ô chỉ đổi lớp TẠM (owner 01/10)")
        assertFalse("persist" in SourceRoots.body(vm, "fun slotRevert("))
    }

    @Test
    fun `host tha app thi release khong force-stop, khong mo lai, khong do`() {
        val fn = SourceRoots.body(host, "fun relinquish(expect: String)")
        order(fn, "if (pkg != expect) return", "SlotLiveProbe.unwatch(probeKey)", "full.reset()", "pkg = null", "launched = false")
        val release = SourceRoots.body(host, "fun release()")
        assertTrue("if (wasLaunched && p != null && sh != null && !full.isDetached)" in release,
            "force-stop của release() phải hỏi gói còn giữ — host đã thả (pkg = null) ⇒ 0 lệnh")
        assertTrue("val p = pkg ?: return" in SourceRoots.body(host, "private fun maybeLaunch()"), "đã thả ⇒ không mở lại")
    }

    @Test
    fun `tat app - cong kenh, luong nen, lenh dung o core, khong force-stop`() {
        val fn = SourceRoots.body(actions, "private fun closeApp(")
        order(fn, "ShellAccessUi.allowOrPrompt(activity)", "val stage = host?.stage()",
            // Chưa mở vào màn ảo (chưa có task của nó ở đó) ⇒ 0 lệnh, chỉ thả host + luật hoàn ô — nút không chết lúc đang mở.
            "if (stage == null && host?.holds(pkg) == true)", "return revert(index, Event.APP_CLOSED, pkg)",
            "stage.pkg != pkg", "submitBg {", "closer.run(sh, stage.vd, pkg)", "main.post {", "if (r.slotFree) revert(index, Event.APP_CLOSED, pkg)")
        assertTrue("fun holds(p: String): Boolean = !released && pkg == p" in host, "host đã nhả / giữ gói khác ⇒ không phải ô của app này")
        listOf(actions, cluster).forEach { src ->
            listOf("force-stop", "stack remove", "\"am ", "move-task", "BehindHome").forEach {
                assertFalse(it in src, "'$it' — lệnh / chuỗi chạy nền chỉ dựng ở :core + đường sẵn có")
            }
        }
        val close = core("SlotClose.kt")
        order(SourceRoots.body(close, "fun run(sh: (String) -> String, vd: Int, pkg: String): Report"),
            "if (vd < 1 || pkg.isBlank() || pkg == selfPkg)", "StackReads.read(sh)", "SlotClosePlan.targets(before, vd, pkg, selfPkg)",
            "if (!SlotClosePlan.admissible(id, before, vd, pkg, selfPkg)) continue", "sh(FloatingOrphanPlan.removeCmd(id))", "StackReads.settle(")
        assertFalse("force-stop" in close)
    }

    @Test
    fun `chay nen di dung duong doi-tai-cho san co roi evictBehind`() {
        val fn = SourceRoots.body(actions, "private fun background(")
        order(fn, "ShellAccessUi.allowOrPrompt(activity)", "workspace().hostAt(index)?.stage()?.pkg != shown.pkg", "revert(index, Event.APP_BACKGROUND, shown.pkg)")
        assertTrue("onAppSwapped = { i, vd, a, b -> slots.evictBehind(i, vd, a, b) }" in code("KachiHomeActivity.kt"),
            "đổi tại chỗ xong ⇒ app vừa rời ô ra sau màn nhà qua BehindHomeRunner.evict (đường công khai sẵn có)")
        assertTrue("it.swapNonce + (slot to nextSwapNonce())" in SourceRoots.body(code("HomeViewModel.kt"), "fun applySlotRevert("),
            "ShowSaved(swapInPlace) phải đặt mốc đổi-tại-chỗ ⇒ WorkspaceView.swapInPlace, không dựng lại ô (dựng lại = force-stop)")
    }

    @Test
    fun `nut di cung nhip nghi cua dau o`() {
        assertTrue("e.cluster?.settle(hidden)" in SourceRoots.body(heads, "private fun settle("))
        assertTrue("e.cluster?.show()" in SourceRoots.body(heads, "private fun reveal("))
        assertTrue("e.cluster?.hide()" in SourceRoots.body(heads, "private fun hide("))
        assertTrue("e.cluster?.cancel()" in SourceRoots.body(heads, "private fun drop("))
        assertTrue("e.cluster?.hits(x - slot.left, y - slot.top)" in SourceRoots.body(heads, "private fun inHit("),
            "chạm vào CHỖ một nút đang ẩn ⇒ không hiện (điều 4 của ⇄)")
        val reg = SourceRoots.body(heads, "fun register(")
        order(reg, "val head = slot.getChildAt(slot.childCount - 1)", "SlotActionsCluster.attach(slot, index, kind, projector, it)")
        assertTrue("slot.addView(row, slot.childCount - 1," in cluster, "cụm chèn DƯỚI ⇄ — ⇄ vẫn là con cuối")
        // Lúc dựng khung, `WorkspaceView.hostAt(index)` còn trỏ KHUNG CŨ (đã nhả) ⇒ hỏi bộ chiếu của CHÍNH khung này.
        val refresh = SourceRoots.body(cluster, "fun refresh()")
        order(refresh, "slot.getChildAt(it) as? VdAppHost", "hostLive = host != null && !host.isReleased")
        assertFalse("hostAt(" in refresh)
        assertTrue("ShellAccessUi.usableNow() && hostLive" in SourceRoots.body(actions, "override fun buttons("),
            "không kênh / không bộ chiếu ⇒ ô app chỉ còn ⇄ (không nút chết)")
    }

    @Test
    fun `cum nut - cancel truoc moi animate, an la INVISIBLE, dich cham 48dp, mo ta tai nguyen`() {
        val animating = listOf("fun show()", "fun hide()")
        animating.forEach { sig ->
            val fn = SourceRoots.body(cluster, sig)
            assertTrue(fn.indexOf("row.animate().cancel()") in 0 until fn.indexOf("animate().alpha("), "$sig: cancel() TRƯỚC animate")
        }
        assertEquals(2, Regex("""animate\(\)\.alpha\(""").findAll(cluster).count(), "chỉ hai chỗ animate — chỗ mới phải được bài này soi")
        assertFalse("View.GONE" in cluster, "GONE ⇒ đo lại cả workspace (nháy dựt)")
        assertTrue("withEndAction { row.visibility = View.INVISIBLE }" in SourceRoots.body(cluster, "fun hide()"))
        assertTrue("LinearLayout.LayoutParams(touch, touch)" in cluster && "KachiTheme.dpi(ctx, Sp.TOUCH)" in cluster, "đích chạm 48×48 dp")
        val btn = SourceRoots.body(cluster, "private fun button(")
        assertTrue("isClickable = true" in btn && "contentDescription = ctx.getString(describe(b, kind), index + 1)" in btn)
        assertTrue("isClickable = false" in btn, "icon không tự nhận chạm (một cú chạm, một lớp)")
    }

    @Test
    fun `chuoi moi du nam tieng`() {
        val keys = listOf("kachi_slot_to_back", "kachi_slot_close_app", "kachi_slot_close_widget", "kachi_slot_close_failed")
        listOf("values", "values-en", "values-zh-rCN", "values-th", "values-ms").forEach { f ->
            val xml = SourceRoots.text("src/main/res/$f/strings_kachi.xml")
            keys.forEach { k -> assertTrue("\"$k\"" in xml, "$f thiếu $k") }
        }
    }
}
