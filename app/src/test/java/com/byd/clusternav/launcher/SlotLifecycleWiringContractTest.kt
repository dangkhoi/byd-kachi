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
            // Soát 2.87 (P1/P2/P3) — hàm mới phải có chỗ gọi production (CLAUDE.md §8).
            "slots::behindUsable" to "KachiHomeActivity.kt",
            "SlotHeadTouch.onDown(" to "SlotHeadAutoHide.kt",
            "SlotHeadTouch.onUp(" to "SlotHeadAutoHide.kt",
            "SlotHeadTouch.onConfirmArmed(" to "SlotHeadAutoHide.kt",
            "SlotCloseConfirm.onTap(" to "SlotActionsCluster.kt",
            // Soát vòng 2 — cửa sổ trợ năng + đầu ô không ẩn giữa lượt chờ + chặn nhấp đúp bằng DOWN₂ − UP₁.
            "SlotCloseConfirm.window(" to "SlotActionsCluster.kt",
            "SlotCloseConfirm.hideAfterMs(e.cluster?.armedLeftMs())" to "SlotHeadAutoHide.kt",
            "observe(it, cluster)" to "SlotActionsCluster.kt",
            // Soát vòng 3 [P3] — ghép click↔lần nhấn theo mốc sự kiện · QA 2.87 — đĩa sau ⇄ ô App + tô lại khi đổi chủ đề tại chỗ.
            // Soát vòng 4 [P3] — ĐỔI GHIM có lý do: `take()` không còn tham số giờ (không bỏ lần nhấn theo tuổi — bỏ theo tuổi lệch
            // cặp ⇒ FIRE, `SlotCloseTouchTest`); mốc lượt `reached` dọn lần nhấn View không ra click (thay ngưỡng tuổi + trần hàng).
            "presses.take()" to "SlotActionsCluster.kt",
            "presses.reached(p)" to "SlotActionsCluster.kt",
            // Soát vòng 5 [P3] — xác nhận chỉ sau khi đĩa đỏ đã được vẽ.
            "SlotCloseConfirm.seen(press?.down, shownAt)" to "SlotActionsCluster.kt",
            "markShown(cell, at)" to "SlotActionsCluster.kt",
            "SlotCloseTouch.inView(" to "SlotActionsCluster.kt",
            "swapDisc(ctx)" to "SlotActionsCluster.kt",
            "e.cluster?.restyle()" to "SlotHeadAutoHide.kt",
            "heads.restyleAll()" to "WorkspaceView.kt",
            "heads.retain(slotViews.size)" to "WorkspaceView.kt",
            "workspace().heads.refreshAll()" to "KachiHomeSlotActions.kt",
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
            // Soát 2.87 · P3 — ĐỔI GHIM có lý do: ô bận suốt lượt gỡ stack (xếp hàng với chuỗi chạy nền của CÙNG ô).
            "stage.pkg != pkg", "busy += index", "submitBg {", "closer.run(sh, stage.vd, pkg)", "main.post {", "busy -= index",
            "if (r.slotFree) revert(index, Event.APP_CLOSED, pkg) else sayIfStill(index, R.string.kachi_slot_close_failed, pkg)",
            "if (!accepted) { busy -= index;")
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
        // Soát 2.87 · P3 — ĐỔI GHIM có lý do: `backing` → `busy` (một việc đầu ô một ô một lúc, chung với *tắt*); BEHIND-HOME đã
        // tắt ⇒ hỏi lại nút, 0 lệnh; câu "không chạy nền được" chỉ khi ô VẪN hiện app; xong chuỗi ⇒ hỏi lại nút mọi ô.
        order(fn, "if (index in busy) return", "if (!behindUsable())", "workspace().heads.refreshAll(); return",
            "InstalledApps.isSystem(activity, pkg)", "R.string.kachi_sc_refuse_system", "return",
            "ShellAccessUi.allowOrPrompt(activity)", "val stage = workspace().hostAt(index)?.stage()", "stage.pkg != pkg", "return",
            "busy += index", "toBack(index, stage.vd, pkg) { left ->", "busy -= index",
            "if (left) revert(index, Event.APP_BACKGROUND, pkg) else sayIfStill(index, R.string.kachi_sc_bg_failed, pkg)",
            "workspace().heads.refreshAll()")
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
        // Soát 2.87 · P2 — ĐỔI GHIM có lý do: cụm nhận thêm móc `::armed` (tắt hai bước giữ đầu ô hiện suốt lượt chờ).
        order(reg, "val head = slot.getChildAt(slot.childCount - 1)", "SlotActionsCluster.attach(slot, index, kind, projector, it, ::armed)")
        assertTrue("slot.addView(row, slot.childCount - 1," in cluster, "cụm chèn DƯỚI ⇄ — ⇄ vẫn là con cuối")
        // Lúc dựng khung, `WorkspaceView.hostAt(index)` còn trỏ KHUNG CŨ (đã nhả) ⇒ hỏi bộ chiếu của CHÍNH khung này.
        val refresh = SourceRoots.body(cluster, "fun refresh()")
        order(refresh, "slot.getChildAt(it) as? VdAppHost", "hostLive = host != null && !host.isReleased")
        assertFalse("hostAt(" in refresh)
        assertTrue("ShellAccessUi.usableNow() && hostLive, behindUsable()" in SourceRoots.body(actions, "override fun buttons("),
            "không kênh / không bộ chiếu ⇒ ô app chỉ còn ⇄; BEHIND-HOME đã tắt ⇒ không nút chạy nền (không nút chết — P3)")
        assertTrue("fun behindUsable(): Boolean = BehindHomeRunner.disabledReason == null" in code("KachiHomeSlots.kt"),
            "cờ đọc từ đúng bên thi hành (một phép đo `ANCHOR_IN_FRONT` đặt nó)")
    }

    /** Soát 2.87 · P3 — *tắt* không được chạy chồng lên chuỗi *chạy nền* (hay lượt *tắt* khác) của CÙNG ô. */
    @Test
    fun `mot viec dau o mot o mot luc - tat cung xep hang voi chay nen`() {
        val close = SourceRoots.body(actions, "private fun close(")
        assertTrue(close.removePrefix("{").trimStart().startsWith("if (index in busy) return"), "chặn TRƯỚC mọi nhánh (app / widget): $close")
        assertFalse("backing" in actions, "một tập bận cho cả hai việc")
        val say = SourceRoots.body(actions, "private fun sayIfStill(")
        assertTrue("(shownAt(index) as? SlotContent.App)?.pkg == pkg" in say, "ô đã đổi ⇒ không báo 'chưa làm được' sai")
    }

    /**
     * Soát 2.87 · P2 (quyết định điều phối) — *tắt* = HAI chạm: luật ở `:core` [SlotCloseConfirm] (bảng `SlotCloseConfirmTest`);
     * ở đây khoá dây nối: chạm nút đi qua `tap` (không gọi cổng thẳng), chạm đầu chỉ đổi trạng thái + mô tả + giữ đầu ô, lần hai
     * mới gọi cổng; mọi lối rời (hết giờ · ẩn · dựng lại · nút thành không làm được) đều về như cũ. *Chạy nền* một chạm.
     */
    @Test
    fun `tat hai buoc - cham dau chi doi trang thai, cham hai moi tat`() {
        val attach = SourceRoots.body(cluster, "fun attach(")
        assertTrue("made.forEach { (b, v) -> v.setOnClickListener { cluster.tap(b) } }" in attach)
        assertFalse("port.onAction" in attach, "không nút nào gọi cổng thẳng từ lúc dựng")
        val tap = SourceRoots.body(cluster, "private fun tap(")
        // Soát vòng 2 [P3] — ĐỔI GHIM có lý do: khoảng nhấp đúp là DOWN₂ − UP₁ (`MotionEvent.eventTime`, đúng phép
        // `GestureDetector.isConsideredDoubleTap`) chứ không phải click-tới-click; cửa sổ của lượt theo trợ năng ([armedWindow]).
        // Soát vòng 3 [P3] — ĐỔI GHIM lần nữa, có lý do: cặp `touchDown/touchUp` "hiện tại" sai khi luồng chính trễ (click được
        // POST — r47 `View.java:14820-14825`; DOWN₂ tới trước click₁ ⇒ click₁ xoá D₂ ⇒ nhấp đúp thành "không phải ngón" ⇒ FIRE).
        // Nay mỗi click lấy ĐÚNG lần nhấn của nó ([SlotCloseTouch], bảng `SlotCloseTouchTest`) và mọi mốc là `eventTime`.
        // Soát vòng 4 [P3] — ĐỔI GHIM có lý do: `take(now)` → `take()` (một lần nhấn mỗi click, không ngưỡng tuổi — lý do ở KDoc
        // `SlotCloseTouch`; ca lệch cặp sau lần kẹt 1,0–1,25 s khoá ở `SlotCloseTouchTest`).
        // Soát vòng 5 [P3] — ĐỔI GHIM có lý do: lần nhấn mà ngón xuống TRƯỚC khung vẽ đầu tiên của đĩa đỏ không thể là xác nhận
        // (luồng chính kẹt ⇒ hai click chạy liền nhau trước mọi khung vẽ ⇒ bản vòng 4 FIRE ở mọi độ dài kẹt) ⇒ `seen` (bảng
        // `SlotCloseConfirmTest`, mô phỏng `SlotCloseTouchTest` "luong chinh ket 2 s"); mốc vẽ ghi ở `markShown` (postOnAnimation).
        order(tap, "if (b != Button.CLOSE) return port.onAction(index, b)", "val press = presses.take()",
            "val at = press?.up ?: now", "val gap = press?.let { p -> lastUp?.let { p.down - it } }",
            "val seen = SlotCloseConfirm.seen(press?.down, shownAt)",
            "SlotCloseConfirm.onTap(armedAt, at, gap, tapGap, armedWindow, seen)",
            "SlotCloseConfirm.Tap.ARM -> arm(at, press?.up)", "SlotCloseConfirm.Tap.WAIT -> lastUp = press?.up",
            "SlotCloseConfirm.Tap.FIRE -> { disarm(); port.onAction(index, Button.CLOSE) }")
        assertEquals(1, Regex("""port\.onAction\(index, Button\.CLOSE\)""").findAll(cluster).count(), "đúng MỘT đường tới *tắt* thật")
        val track = SourceRoots.body(cluster, "private fun track(")
        order(track, "MotionEvent.ACTION_DOWN -> presses.down(ev.eventTime)",
            "if (!SlotCloseTouch.inView(ev.x, ev.y, v.width, v.height, slop)) presses.left()",
            "MotionEvent.ACTION_UP -> presses.up(ev.eventTime)?.let { p -> buttons[Button.CLOSE]?.post { presses.reached(p) } }",
            "MotionEvent.ACTION_CANCEL -> presses.cancel()")
        assertTrue("ViewConfiguration.get(slot.context).scaledTouchSlop" in cluster, "cùng ngưỡng trượt của View (`mTouchSlop`)")
        assertFalse("touchDown" in cluster || "touchUp" in cluster, "không còn cặp chạm 'hiện tại' (đọc lúc click chạy là đọc nhầm lần nhấn)")
        assertTrue("v.setOnTouchListener { _, ev -> cluster.track(ev); false }" in cluster,
            "người nghe chạm chỉ NHÌN — trả false, click + performClick (trợ năng) đi đường cũ")
        assertEquals(1, Regex("""setOnTouchListener""").findAll(cluster).count(), "chỉ nút *tắt* có người nghe chạm")
        val arm = SourceRoots.body(cluster, "private fun arm(")
        // Soát vòng 3 — ĐỔI GHIM có lý do: mốc lượt đầu là `eventTime` của UP (không phải giờ handler) ⇒ hẹn hết giờ tính từ CÙNG
        // mốc đó, để đĩa đỏ tắt đúng lúc [SlotCloseConfirm.onTap] thôi nhận xác nhận (không còn "đỏ mà chạm lại thành ARM").
        order(arm, "armedAt = at", "armedWindow = windowNow()", "lastUp = up", "paint(cell, confirm = true)", "shownAt = null",
            "markShown(cell, at)", "R.string.kachi_slot_close_confirm",
            "slot.postDelayed(disarmTask, (at + armedWindow - SystemClock.uptimeMillis()).coerceAtLeast(0L))", "onArmed(this)")
        val win = SourceRoots.body(cluster, "private fun windowNow(")
        assertTrue("getRecommendedTimeoutMillis(SlotCloseConfirm.WINDOW_MS.toInt(), flags)" in win &&
            "AccessibilityManager.FLAG_CONTENT_CONTROLS or AccessibilityManager.FLAG_CONTENT_ICONS" in win && "SlotCloseConfirm.window(" in win,
            "cửa sổ theo 'Thời gian thực hiện hành động' (API 29), gốc 2 s, không bao giờ ngắn hơn")
        val disarm = SourceRoots.body(cluster, "private fun disarm(")
        order(disarm, "slot.removeCallbacks(disarmTask)", "lastUp = null", "shownAt = null", "armedAt = null", "paint(cell, confirm = false)",
            "describe(Button.CLOSE, kind), index + 1")
        val shown = SourceRoots.body(cluster, "private fun markShown(")
        // Soát vòng 6 [P3] — ĐỔI GHIM có lý do: mốc ghi ở pha animation thì traversal của CHÍNH khung đó (lượt WidgetFit nguội 0,5–0,7 s)
        // chạy SAU mốc ⇒ cú chạm giữa khung (đĩa đỏ chưa lên màn) thành xác nhận ⇒ `am stack remove`. Nay `post` trong callback
        // animation (message đồng bộ sau rào traversal — chạy khi khung xong); mô phỏng `SlotCloseTouchTest` "khung dai ngay sau ARM".
        order(shown, "cell.postOnAnimation {", "if (armedAt != at || shownAt != null) return@postOnAnimation",
            "if (cell.isShown) cell.post { if (armedAt == at && shownAt == null) shownAt = SystemClock.uptimeMillis() } else markShown(cell, at)")
        assertFalse("if (cell.isShown) shownAt =" in shown, "không ghi mốc ngay ở pha animation")
        listOf("fun settle(", "fun hide()", "fun cancel()").forEach { assertTrue("disarm()" in SourceRoots.body(cluster, it), "$it phải gỡ lượt chờ") }
        assertTrue("if (Button.CLOSE !in now) disarm()" in SourceRoots.body(cluster, "fun refresh()"))
        val paint = SourceRoots.body(cluster, "private fun paint(")
        order(paint, "KachiGlass.plain(disc,", "KachiTheme.RED", "KachiTheme.BG", "KachiGlass.apply(disc, Sp.SWAP_DISC / 2, SurfaceTone.NEUTRAL, fade = false)", "KachiTheme.MUT")
    }

    /** Đĩa xác nhận ĐỎ + icon màu nền: ≥ 4.5:1 ở CẢ hai bảng màu (icon là thông tin duy nhất trên đĩa). */
    @Test
    fun `dia xac nhan tat du tuong phan ca hai bang mau`() {
        listOf("TỐI" to KachiPalette.DARK, "SÁNG" to KachiPalette.LIGHT).forEach { (name, p) ->
            val r = ColorMath.ratio(ColorMath.parse(p.bg), ColorMath.parse(p.red))
            assertTrue(r >= 4.5, "bảng $name: icon ${p.bg} trên đĩa ${p.red} chỉ ${"%.2f".format(r)}:1")
        }
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
        // Soát 2.87 · P2 — ĐỔI GHIM có lý do: màu đĩa/icon đi qua `paint` (một chỗ cho trạng thái thường VÀ chờ xác nhận *tắt*).
        assertTrue("KachiGlass.apply(disc, Sp.SWAP_DISC / 2, SurfaceTone.NEUTRAL, fade = false)" in SourceRoots.body(cluster, "private fun paint("),
            "đĩa kính NEUTRAL, tròn, không mờ R-OP")
        order(btn, "addView(disc,", "addView(icon,", "paint(this, confirm = false)")
        assertTrue("val disc = View(ctx).apply { isClickable = false; isFocusable = false }" in btn, "đĩa không nhận chạm")
    }

    /**
     * QA 2.87 [P3] (D-L8-1) — ⇄ của ô App trần trên trang trắng Chrome gần như vô hình ([ĐO máy ảo `l4/s7-chrome-bg-toast.png`])
     * trong khi hai nút cụm có ĐĨA KÍNH thì đọc được. Tương phản của đĩa là hợp đồng có sẵn — kính NEUTRAL không mờ R-OP giữ
     * `MUT ≥ 4.5:1` trên MỌI độ chói ảnh và mọi lựa chọn màu (`ColorChoiceContractTest.lop che kinh du…`) — nên bài này khoá
     * đúng ba mắt xích để ⇄ hưởng hợp đồng đó: (1) ô App dựng đĩa ở ô GIỮA của hàng (dưới ⇄) bằng CÙNG kính NEUTRAL `fade =
     * false` và CÙNG hình học với đĩa của nút; (2) icon ⇄ tô `MUT` (bộ dựng ghim byte — không sửa ở đó); (3) nét chính của icon
     * *chạy nền* mới ở alpha 1.0 (lớp main — tương phản của nó LÀ tương phản `MUT`-trên-kính). Đổi chủ đề TẠI CHỖ: ô App không
     * dựng lại ⇒ ⇄ + cụm được tô lại (`restyleAll`), không giữ màu icon của bảng cũ trên đĩa đã sang bảng mới.
     */
    @Test
    fun `dia kinh sau ⇄ o App, cung hop dong tuong phan voi nut cum, to lai khi doi chu de`() {
        val attach = SourceRoots.body(cluster, "fun attach(")
        assertTrue("b == null && kind == SlotHeadRest.Kind.APP -> swapDisc(ctx)" in attach, "ô giữa của hàng (dưới ⇄) của ô App = đĩa")
        order(attach, "listOf(Button.BACKGROUND, null, Button.CLOSE)", "slot.addView(row, slot.childCount - 1,")
        val disc = SourceRoots.body(cluster, "private fun swapDisc(")
        assertTrue("KachiGlass.apply(disc, Sp.SWAP_DISC / 2, SurfaceTone.NEUTRAL, fade = false)" in disc, "cùng kính NEUTRAL không mờ với đĩa nút")
        assertTrue("addView(disc, discLp(ctx))" in disc && "addView(disc, discLp(ctx))" in SourceRoots.body(cluster, "private fun button("),
            "đĩa ⇄ và đĩa nút dùng CHUNG một hình học (tâm đĩa = tâm icon ⇄)")
        assertTrue("isClickable = false; isFocusable = false" in disc && "IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS" in disc,
            "đĩa không nhận chạm (cú chạm tới khung ⇄ phía trên), không vào cây trợ năng")
        assertFalse("swapDisc" in SourceRoots.body(cluster, "fun hits("), "đĩa không phải nút — không tính vùng nút")
        assertTrue("setColorFilter(Color.parseColor(KachiTheme.MUT))" in code("SlotSwapButton.kt"), "icon ⇄ tô MUT — vai mà hợp đồng kính bảo đảm")
        val icon = SourceRoots.text("src/main/res/drawable/ic_to_back.xml")
        val main = Regex("""<path[^>]*android:strokeWidth="1.8"[^>]*/>""").findAll(icon).toList()
        assertTrue(main.isNotEmpty() && main.none { "strokeAlpha" in it.value }, "nét chính của icon chạy nền ở alpha 1.0: $icon")
        assertTrue("design/glyph/to_back.svg" in icon, "icon sinh bởi gen-icons.py từ nguồn SVG (không vá tay)")
        // Đổi chủ đề tại chỗ: ô App giữ khung ⇒ phải tô lại ⇄ + cụm.
        assertTrue("heads.restyleAll()" in SourceRoots.body(code("WorkspaceView.kt"), "fun restyle("))
        val restyleAll = SourceRoots.body(heads, "fun restyleAll(")
        assertTrue("setColorFilter(Color.parseColor(KachiTheme.MUT))" in restyleAll && "e.cluster?.restyle()" in restyleAll, restyleAll)
        assertTrue("paint(it, confirm = b == Button.CLOSE && armedAt != null)" in SourceRoots.body(cluster, "fun restyle("),
            "cụm tô lại theo bảng mới, nút đang chờ xác nhận giữ đỏ")
    }

    @Test
    fun `chuoi moi du nam tieng`() {
        val keys = listOf("kachi_slot_to_back", "kachi_slot_close_app", "kachi_slot_close_widget", "kachi_slot_close_failed", "kachi_slot_close_confirm")
        listOf("values", "values-en", "values-zh-rCN", "values-th", "values-ms").forEach { f ->
            val xml = SourceRoots.text("src/main/res/$f/strings_kachi.xml")
            keys.forEach { k -> assertTrue("\"$k\"" in xml, "$f thiếu $k") }
        }
    }
}
