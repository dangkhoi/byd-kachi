package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ 2.98 · R7 · SLOT-ESCAPE-POLICY — DÂY NỐI ══════════════════════════════════════════════════════════════════════════════
 *
 * Quyết định + chuỗi lệnh khoá ở `:core` (`SlotEscapePlanTest` · `SlotEscapeRunTest` · `EscapeCoverPlanTest`, bản đọc máy ảo
 * nguyên văn). `:app` không có Robolectric ⇒ bài này canh MÃ (đã bỏ chú thích) — mỗi khẳng định là một mắt xích (CLAUDE.md §8:
 * hàm mới phải CÓ chỗ gọi):
 *  - app RA KHỎI ô (`elsewhere`) ⇒ thử nhận TRƯỚC, đường 2.93 (câu báo + `APP_ELSEWHERE`) chỉ là đường lùi; app chết ⇒ y như cũ;
 *  - host ô hỏi `SlotEscape.claimsLive` TRƯỚC mọi lệnh mở vào màn ảo;
 *  - nhịp đo ân hạn lúc màn khuất mặc định 0 (R7 tắt = hành vi trước 2.98);
 *  - bảng/kênh shell/dịch vụ trợ năng nối đúng chỗ; DL5 không có bảng mã; không lệnh nào giết app.
 */
class SlotEscapeWiringContractTest {

    private fun code(path: String) = SourceRoots.codeOf("src/main/java/com/byd/clusternav/$path")

    @Test
    fun `app ra khoi o thi thu nhan truoc, duong 2_93 chi la duong lui`() {
        val actions = code("launcher/KachiHomeSlotActions.kt")
        val gone = SourceRoots.body(actions, "override fun onAppGone(")
        assertTrue("if (!elsewhere) return revert(index, Event.APP_DIED, pkg)" in gone, "app chết ⇒ đúng đường cũ")
        val adopt = gone.substring(gone.indexOf("escape.tryAdopt(index, pkg)"))
        assertTrue(adopt.indexOf("sayIfStill(index, R.string.kachi_slot_app_elsewhere, pkg)") > 0, "câu báo 2.93 nằm TRONG đường lùi")
        assertTrue(adopt.indexOf("revert(index, Event.APP_ELSEWHERE, pkg)") > 0)
        assertTrue("SlotEscapeHome(activity, viewModel, workspace, shell)" in actions)
        assertTrue("workspace().hostAt(i)?.holdsEmpty(p) == true" in actions, "task mất chỉ hoàn ô khi host đang giữ màn ảo TRỐNG")
    }

    @Test
    fun `host o hoi truoc moi lenh mo vao man ao`() {
        val launch = SourceRoots.body(code("launcher/VdAppHost.kt"), "private fun launchInto(")
        val claim = launch.indexOf("SlotEscape.claimsLive(context, slot, p, sh)")
        assertTrue(claim >= 0)
        assertTrue(claim < launch.indexOf("FreeformLaunch.resolveComponent"), "hỏi trước cả lệnh đọc component")
        assertTrue(claim < launch.indexOf("am force-stop"), "không bao giờ force-stop app đang được quản")
    }

    @Test
    fun `nhip an han luc man khuat mac dinh 0 va chi SlotEscapeHome dat`() {
        val probe = code("launcher/SlotLiveProbe.kt")
        assertTrue("@Volatile var pausedGraceSweeps = 0" in probe)
        assertTrue("if (ticking || (paused && !graceLeft())) return" in probe)
        assertEquals(1, Regex("shell\\(\"am stack list\"\\)").findAll(SourceRoots.body(probe, "private fun sweep()")).count(), "vẫn MỘT lệnh mỗi nhịp")
        assertTrue("SlotLiveProbe.pausedGraceSweeps = if (SlotEscape.codes(activity) != null) SlotEscape.PAUSED_GRACE_SWEEPS else 0" in
            code("launcher/escape/SlotEscapeHome.kt"))
    }

    @Test
    fun `bang, kenh shell, dich vu tro nang noi dung cho`() {
        val windows = code("launcher/LauncherWindows.kt")
        assertTrue("if (reason == \"shell-up\") SlotEscape.shellUp()" in windows, "quét khởi động khi kênh vừa lên")
        assertTrue("SlotEscape.panels(activity, drawerOpen())" in SourceRoots.body(windows, "fun updateOverlayHeads()"))
        assertTrue("SlotEscape.panels(activity, open = true)" in SourceRoots.body(windows, "fun drawerShown()"))
        assertTrue("onClearOverlays = { windows.drawerShown() }" in code("launcher/KachiHomeActivity.kt"))
        val svc = code("modules/navaccess/NavAccessibilityService.kt")
        assertTrue("A11yOverlayPort.attach(this)" in SourceRoots.body(svc, "override fun onServiceConnected()"))
        assertTrue("A11yOverlayPort.detach(this)" in SourceRoots.body(svc, "override fun onUnbind("))
        assertTrue("A11yOverlayPort.detach(this)" in SourceRoots.body(svc, "override fun onDestroy()"))
    }

    /**
     * Soát Fable Pass 7 (09/10) — ba mắt xích vá: (a) hai màn nhà cùng sống (R3): onStop của màn CŨ chỉ gỡ lớp che CỦA NÓ
     * (`hideIf`), không gỡ mù lớp che màn MỚI vừa dựng; (b) `requestReconcile` gọi được từ luồng nền (host ô `claimsLive`) ⇒ nhảy
     * về luồng chính trước khi đụng `pendingRefront`; (c) dịch vụ trợ năng nối lại (`AccessibilityRebind`) ⇒ đối chiếu để che lại;
     * (d) lớp che sau lệnh nhận chỉ khi lệnh đã đưa task lên đỉnh (`Adopted.onTop`).
     */
    @Test
    fun `soat Pass 7 - hai man nha, luong nen, tro nang noi lai, che khi len dinh`() {
        val home = code("launcher/escape/SlotEscapeHome.kt")
        assertTrue("EscapeCoverOverlay.hideIf(activity, \"home-stop\")" in SourceRoots.body(home, "override fun onStop("), "onStop chỉ gỡ lớp che của chính màn này")
        assertFalse("EscapeCoverOverlay.hide(\"home-stop\")" in home)
        val req = SourceRoots.body(home, "fun requestReconcile(")
        assertTrue(req.indexOf("if (Looper.myLooper() != Looper.getMainLooper()) { main.post { requestReconcile(refront, why) }; return }") <
            req.indexOf("pendingRefront = pendingRefront || refront"), "nhảy về luồng chính TRƯỚC khi đụng state")
        assertTrue("if (started && !shade.open && r.onTop) showCovers(" in SourceRoots.body(home, "fun tryAdopt("))
        val overlay = code("launcher/escape/EscapeCoverOverlay.kt")
        assertTrue("fun hideIf(owner: Activity, why: String) { if (host === owner) hide(why) }" in overlay)
        val port = code("modules/navaccess/A11yOverlayPort.kt")
        assertTrue("attachListeners.forEach { runCatching { it() } }" in SourceRoots.body(port, "fun attach("))
        val escape = code("launcher/escape/SlotEscape.kt")
        assertTrue("A11yOverlayPort.onAttach { current?.requestReconcile(refront = false, why = \"a11y-up\") }" in escape)
        // (e) GO_HOME mở home MẶC ĐỊNH ⇒ mọi bước "Home trước" của R7 chỉ khi Kachi là home mặc định (cài kiểu thường ⇒ không mở launcher BYD).
        assertTrue("fun homeAllowed(ctx: Context): Boolean = DefaultHome.isCurrent(ctx) == true" in escape)
        assertTrue("homeAllowed = homeAllowed(ctx)," in SourceRoots.body(escape, "fun run("))
        val panels = SourceRoots.body(home, "private fun onShade(")
        assertTrue(panels.indexOf("if (!SlotEscape.homeAllowed(activity)) {") in 0 until panels.indexOf("SlotEscapePlan.homeCmd("), "cổng home mặc định đứng TRƯỚC lệnh Home")
    }

    @Test
    fun `DL5 khong co bang ma, dau ben theo xe, khong giet app, khong bat tro nang`() {
        val profile = code("modules/clustercast/ClusterProfile.kt")
        val dl5 = profile.substring(profile.indexOf("val DL5 = ClusterProfile("), profile.indexOf("val GENERIC_FALLBACK"))
        assertTrue("taskBinder = taskBinderFor(5)," in dl5, "Android 12 chưa đo ⇒ R7 tắt")
        assertTrue("fun taskBinderFor(diLink: Int): TaskBinderCodes? = if (diLink >= 5) null else TaskBinderCodes.ANDROID_10_R47" in profile)
        assertTrue("taskBinder = taskBinderFor(diLink)" in profile, "chuỗi override (parse) cũng theo đời DiLink")
        val escape = listOf("SlotEscape.kt", "SlotEscapeHome.kt", "EscapeCoverOverlay.kt", "EscapeMarkerStore.kt")
            .joinToString("\n") { code("launcher/escape/$it") }
        listOf("stack remove", "force-stop", "removeTask", "enabled_accessibility_services", "settings put").forEach {
            assertFalse(it in escape, "R7 không được: $it")
        }
        val store = code("launcher/escape/EscapeMarkerStore.kt")
        assertTrue("getSharedPreferences(FreeformSeedStore.PREF" in store && ".commit()" in store && ".apply()" !in store)
        assertTrue("TYPE_ACCESSIBILITY_OVERLAY" in code("launcher/escape/EscapeCoverOverlay.kt"))
    }

    /**
     * Owner 09/10 (máy ảo, Waze + VietMap): #1 nhận chậm ⇒ mốc đo có trần sau lượt mở app vào ô, CHỈ khi R7 bật; #2 khung task cao hơn
     * ô một thanh tiêu đề CHỈ khi có lớp che; GAP vẽ nền sau ô + ⇄; #3 góc bo không nhận chạm.
     */
    @Test
    fun `owner 09-10 - moc do sau khi mo, day tieu de len, GAP va goc`() {
        val probe = code("launcher/SlotLiveProbe.kt")
        assertTrue("@Volatile var launchBurstMs: List<Long> = emptyList()" in probe, "mặc định rỗng = như trước 2.98")
        assertTrue("if (onMissing != null) kick() else subs.lastOrNull { it.key == key }?.let(::burst)" in SourceRoots.body(probe, "fun watch("))
        assertTrue("if (sub in subs && !running) { burstNow = true; sweep() }" in SourceRoots.body(probe, "private fun burst("))
        val home = code("launcher/escape/SlotEscapeHome.kt")
        assertTrue("SlotLiveProbe.launchBurstMs = if (SlotEscape.codes(activity) != null) SlotLiveness.LAUNCH_BURST_MS else emptyList()" in home)
        assertTrue("SlotEscape.run(activity, sh, codes, lift).adopt(" in SourceRoots.body(home, "fun tryAdopt("))
        assertTrue("SlotEscape.run(activity, sh, codes, lift)" in SourceRoots.body(home, "private fun reconcileNow("))
        assertTrue("EscapeCoverOverlay.show(activity, covers, ::slotHeads)" in home)
        val escape = code("launcher/escape/SlotEscape.kt")
        assertTrue("if (com.byd.clusternav.modules.navaccess.A11yOverlayPort.service != null) EscapeCoverPlan.captionPx(densityDpi) else 0" in escape,
            "không lớp che ⇒ không đẩy thanh tiêu đề lên đầu màn nhà")
        val overlay = code("launcher/escape/EscapeCoverOverlay.kt")
        assertTrue("val touch = if (c.kind == EscapeCoverPlan.Kind.CORNER) WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE else 0" in overlay)
        assertTrue("heads(c.slot)" in overlay)
    }

    /**
     * 2.98 · R12–R16 (owner 09/10 "làm hết") — mắt xích của các vá sau R7: R12 GONE vào luật sống/chết · R13 bản đọc dùng lại mang mốc
     * BẮT ĐẦU đọc + nhận lại ô 7 chỉ tin bản chụp sau lượt nhận · R15 màn chính hiện lại ⇒ nhịp về 5 s · R14 ô bị lấy ⇒ gỡ bộ đo host
     * cũ · R16 hộp thoại Kachi qua MỘT móc ở các cửa dựng hộp thoại dùng chung.
     */
    @Test
    fun `R12-R16 - gone, ban doc moi, nhip khi hien lai, go bo do host cu, hop thoai`() {
        val probe = code("launcher/SlotLiveProbe.kt")
        val sweep = SourceRoots.body(probe, "private fun sweep()")
        assertTrue("if (sub.liveness.observe(alive, away, othersInSlot, gone)) {" in sweep, "R12")
        assertTrue("val gone = presence == SlotPresence.GONE" in sweep, "R12")
        assertTrue("StackListSnapshot.record(it, atMs = startedAt)" in sweep, "R13: mốc lúc BẮT ĐẦU đọc")
        assertTrue("val since = maxOf(lastSweepAt, freshAfter)" in sweep, "R13")
        assertTrue(sweep.indexOf("val startedAt = StackListSnapshot.nowMs()") < sweep.indexOf("shell(\"am stack list\")"))
        assertTrue("freshAfter = StackListSnapshot.nowMs()" in SourceRoots.body(probe, "private fun kick()"), "R13")
        val resume = SourceRoots.body(probe, "fun resume()")
        assertTrue("unchangedSweeps = 0" in resume && "ui.removeCallbacks(tick)" in resume, "R15")
        val cast = code("modules/clustercast/simplified/SimpleCastCoordinatorOps.kt")
        assertTrue("StackListSnapshot.record(stackOut, atMs = readAt)" in cast, "R13: lượt dò repin cũng ghi mốc bắt đầu")
        val owner = code("launcher/SlotVdOwner.kt")
        assertTrue("SlotLiveProbe.unwatch(SlotVdLedger.keyOf(e.owner, e.slot))" in SourceRoots.body(owner, "private fun taken("), "R14")
        assertEquals(2, Regex("stale\\.forEach \\{ taken\\(it\\) \\}").findAll(owner).count(), "R14: adopt + move")
        assertTrue("private val probeKey = SlotVdLedger.keyOf(owner, slot)" in code("launcher/VdAppHost.kt"), "R14: cùng khoá")
        val hook = ".show().let(com.byd.clusternav.launcher.escape.SlotEscape::shade)"
        assertEquals(6, code("launcher/SettingsDialogs.kt").split(hook).size - 1, "R16: mọi hộp thoại dùng chung")
        assertTrue(hook in code("UpdateFlow.kt") && hook in code("launcher/KachiHomeDisclaimer.kt"), "R16")
        val home = code("launcher/escape/SlotEscapeHome.kt")
        assertTrue("fun onDialog(shown: Boolean) = onShade(shade.dialog(shown), \"dialog\")" in home, "R16")
        assertTrue("val refront = pendingRefront && resumed && !shade.open" in home, "R16: không đưa app lên khi hộp thoại còn mở")
        val escape = code("launcher/escape/SlotEscape.kt")
        assertTrue("decor.addOnAttachStateChangeListener(" in SourceRoots.body(escape, "fun shade("), "R16: không đè listener của bên gọi")
    }
}
