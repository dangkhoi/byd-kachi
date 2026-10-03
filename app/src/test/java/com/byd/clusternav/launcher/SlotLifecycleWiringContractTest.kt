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
 *  - *chạy nền* (L8, mọi ô app): lớp che của Kachi trên màn ảo ô → move-task → bản đọc cuối thấy app rời ô → MỚI luật hoàn
 *    ô (host thả app trước ⇒ không force-stop app vừa ra sau màn nhà); chuỗi dựng ở `:core`, lớp keo không chạm BehindHome*;
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
            "toBack(index, stage.vd, pkg)" to "KachiHomeSlotActions.kt",
            "slots::toBack" to "KachiHomeActivity.kt",
            "kit.seq.evictCovered(vd, pkg, kit.hidden)" to "KachiHomeSlots.kt",
            "kit.seq.startBehindHidden(pkg, kit.hidden)" to "KachiHomeSlots.kt",
            "slots().startBehindHidden(sc.pkg)" to "KachiHomeShortcuts.kt",
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
            // L8 — ĐỔI GHIM có lý do: không còn ngoại lệ đổi-tại-chỗ (ShowSaved hết `swapInPlace`) ⇒ MỌI sự kiện app thả host.
            "if (pkg != null) workspace().hostAt(index)?.relinquish(pkg)",
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

    /**
     * L8 — ĐỔI GHIM có lý do (owner 03/10: nút chạy nền ở MỌI ô app; D-L6-1 mở khoá): bài cũ khoá đường đổi-tại-chỗ của L6
     * (chỉ khi ô có app LƯU khác). Nay: app hệ thống ⇒ lý do, 0 lệnh (R0.6) → cổng kênh → ô sẵn (đúng gói, có màn ảo) →
     * chuỗi lớp che trên ĐÚNG màn ảo của ô (`:core` `evictCovered`, mutex `kachi-behind`) → CHỈ khi bản đọc cuối thấy app đã
     * rời ô mới luật hoàn ô; không ⇒ ô giữ app + một câu. Luật hoàn ô không còn đặt mốc đổi-tại-chỗ.
     */
    @Test
    fun `chay nen - lop che tren man ao o, roi ra sau man nha, roi moi luat hoan o`() {
        val fn = SourceRoots.body(actions, "private fun background(")
        order(fn, "if (index in backing) return", "InstalledApps.isSystem(activity, pkg)", "R.string.kachi_sc_refuse_system", "return",
            "ShellAccessUi.allowOrPrompt(activity)", "val stage = workspace().hostAt(index)?.stage()", "stage.pkg != pkg", "return",
            "backing += index", "toBack(index, stage.vd, pkg) { left ->", "backing -= index",
            "if (left) revert(index, Event.APP_BACKGROUND, pkg) else say(R.string.kachi_sc_bg_failed, pkg)")
        val slotsSrc = code("KachiHomeSlots.kt")
        val toBack = SourceRoots.body(slotsSrc, "fun toBack(")
        order(toBack, "behind.chain(", "done(out.outOfStage)", "kit.seq.evictCovered(vd, pkg, kit.hidden)")
        assertFalse("swapNonce" in SourceRoots.body(code("HomeViewModel.kt"), "fun applySlotRevert("),
            "luật hoàn ô dựng lại ô (app đã rời màn ảo) — không mốc đổi-tại-chỗ")
        assertTrue("onAppSwapped = { i, vd, a, b -> slots.evictBehind(i, vd, a, b) }" in code("KachiHomeActivity.kt"),
            "đặt TẠM (lối tắt / giọng nói) vẫn đi đường đổi-tại-chỗ R0.1 sẵn có — L8 không đụng")
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
        // L8 · D-L6-3 [ĐO máy ảo 03/10]: icon trần trên nội dung app 1.73:1 / 2.35:1 ⇒ đĩa kính CÙNG hợp đồng ⇄ ô trống, sau icon.
        assertTrue("KachiGlass.apply(disc, Sp.SWAP_DISC / 2, SurfaceTone.NEUTRAL, fade = false)" in btn, "đĩa kính NEUTRAL, tròn, không mờ R-OP")
        order(btn, "addView(disc,", "addView(icon,")
        assertTrue("val disc = View(ctx).apply { isClickable = false; isFocusable = false }" in btn, "đĩa không nhận chạm")
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
