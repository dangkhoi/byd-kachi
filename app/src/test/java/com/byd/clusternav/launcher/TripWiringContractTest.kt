package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.KotlinSource
import com.byd.clusternav.testsupport.SourceRoots
import java.nio.file.Files
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * V1 (phần nhóm C, spec shortcuts-autostart §5.4) — bài canh TĨNH cho chuyến lên xe F2/F3. Thân hàm đọc bằng
 * [SourceRoots.body] (đếm ngoặc, nổ nếu mốc không có), mã đã bỏ chú thích ([SourceRoots.codeOf]) — không
 * `substringAfter/Before`.
 *
 * Mỗi bài khoá một điều đã ĐO hoặc spec chốt:
 *  - lối vào DUY NHẤT là dòng CUỐI chuỗi SẴN, và lối đó không chặn luồng `kachi-ready` (R2.3, R-nf4);
 *  - sổ chuyến CLAIMED ghi TRƯỚC mọi việc (CLAUDE.md §5) — một lượt mỗi chuyến kể cả khi Kachi tự force-stop;
 *  - nhạc đi qua PHIÊN nhạc trước; L4 · D3(ii): link mà phiên không nhận URI ⇒ ý-định VIEW CHỈ dựng ở `:core`
 *    (`TripMusicPlan.viewCmd`) và chỉ đi qua chuỗi dàn dựng (dấu + K12 khi app thoát lên trước màn nhà — [ĐO `e2e-L4 · m5a` (bằng chứng phiên, ngoài repo)]);
 *  - đọc phiên TRƯỚC khi đụng gì, `null` ⇒ bỏ; L4 · D3(i): nguồn khác đang phát KHÔNG còn chặn khi chọn app cụ thể;
 *  - từ khoá đi qua LÕI giải bài của giọng nói (không đường thứ hai);
 *  - *Mở bình thường* chỉ bằng chuỗi có rào camera (K10).
 */
class TripWiringContractTest {

    private fun code(rel: String) = SourceRoots.codeOf(rel)

    private val early by lazy { code("src/main/java/com/byd/clusternav/EarlyShellChannel.kt") }
    private val start by lazy { code("src/main/java/com/byd/clusternav/launcher/trip/TripStart.kt") }
    private val music by lazy { code("src/main/java/com/byd/clusternav/launcher/trip/TripMusicRun.kt") }
    private val ledger by lazy { code("src/main/java/com/byd/clusternav/launcher/trip/TripLedgerStore.kt") }
    private val glue by lazy { code("src/main/java/com/byd/clusternav/launcher/KachiHomeTrip.kt") }
    private val vm by lazy { code("src/main/java/com/byd/clusternav/launcher/HomeViewModel.kt") }
    private val voice by lazy { code("src/main/java/com/byd/clusternav/launcher/VoiceTargetDispatch.kt") }
    private val intents by lazy { code("src/main/java/com/byd/clusternav/launcher/voice/VoiceAppIntents.kt") }
    private val readyLog by lazy { code("src/main/java/com/byd/clusternav/KachiReadyLog.kt") }
    private val rebind by lazy { code("src/main/java/com/byd/clusternav/RebindReceiver.kt") }

    private fun order(src: String, vararg marks: String) {
        var at = -1
        for (m in marks) {
            val i = src.indexOf(m, at + 1)
            assertTrue(i > at, "thứ tự sai/thiếu: '$m' phải đứng sau mốc trước trong:\n$src")
            at = i
        }
    }

    // ── Lối vào ──────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `chuyen chi vao tu dong CUOI chuoi SAN, dung mot loi goi, sau kiem phim va keep-alive`() {
        val chain = SourceRoots.body(early, "private fun readyChain(")
        order(chain, "WakeEpochPolicy.shouldRun(prev, epoch, ShellReadiness.isUp(), interactive(app))", "BehindHomeRecovery.onReady(app)", "KeyReady.prepare(app)",
            "VoiceKeyKeepAliveService.sync(app)", "TripStart.onReady(app)")
        assertTrue(chain.trimEnd().removeSuffix("}").trimEnd().endsWith("TripStart.onReady(app)"), "phải là dòng CUỐI thân:\n$chain")
        val everywhere = SourceRoots.moduleSourceRoots().flatMap { root ->
            Files.walk(root).use { s -> s.filter { it.toString().endsWith(".kt") }.toList() }
        }.map { it.fileName.toString() to it.toFile().readText() }
        val callers = everywhere.filter { "TripStart.onReady(" in it.second }.map { it.first }
        assertEquals(listOf("EarlyShellChannel.kt"), callers, "lối vào DUY NHẤT của chuyến")
        assertEquals(1, Regex(Regex.escape("TripStart.onReady(")).findAll(early).count())
    }

    @Test
    fun `onReady khong chan luong kachi-ready - chi day viec sang kachi-trip, mot luot cung luc`() {
        val fn = SourceRoots.body(start, "fun onReady(app: Context) {")
        order(fn, "busy.compareAndSet(false, true)", "EXEC.execute {", "TripRun(app.applicationContext).run()", "busy.set(false)")
        listOf("Thread.sleep", "sleep(", "LocalDeviceShell", "await").forEach {
            assertFalse(fn.replace("TripRun(app.applicationContext).run()", "").contains(it), "onReady không được chặn: '$it'")
        }
        assertTrue(start.contains("Thread(r, \"kachi-trip\")"), "luồng riêng của chuyến")
    }

    // ── Một lượt mỗi chuyến ──────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `so CLAIMED ghi TRUOC moi viec, ghi hong thi khong chay, xong thi FIRED`() {
        val fn = SourceRoots.body(start, "fun run() {")
        order(fn, "TripGate.tripId(", "KachiReadyLog.firstWakeAt()", "TripGate.decide(store.ledger()",
            "if (!store.write(d.claim))", "return", "body(boot, firstWake)", "store.close(d.claim.copy(phase = TripGate.Phase.FIRED)")
        assertTrue(fn.contains("Prefs.a11yTatMayAt(app)"), "id chuyến từ claim tắt-máy BỀN của lớp 1 (không cờ RAM)")
        val write = SourceRoots.body(ledger, "fun write(l: TripGate.Ledger): Boolean")
        assertTrue(write.contains(".commit()") && !ledger.contains(".apply()"), "sổ chuyến phải commit() đồng bộ")
        val wake = SourceRoots.body(readyLog, "fun wake(at: Long, src: String) {")
        assertTrue(wake.contains("firstScreenOnAt.compareAndSet(-1L, at)"), "mốc thức ĐẦU TIÊN: đặt một lần, không bị ghi đè")
        val boot = SourceRoots.body(rebind, "Intent.ACTION_BOOT_COMPLETED -> {")
        assertTrue(boot.contains("TripStart.onBootCompleted(context)"), "R2.3a — mốc BOOT_COMPLETED của lần khởi động này")
    }

    @Test
    fun `cho bang su that truoc buoc dau, va moi buoc hoi lai han chuyen`() {
        val fn = SourceRoots.body(start, "private fun body(boot: String, firstWake: Long): TripGate.Code {")
        // A2 · 2.89 — ĐỔI PIN có lý do: lượt chờ nhận cấu hình (chỉ chờ ô dàn dựng khi có app Chạy nền ngoài ô — [ĐO xe 05/10]).
        order(fn, "tripConfig()", "if (cfg.empty)", "awaitReady(boot, firstWake, cfg)", "TripPlan.steps(cfg, facts)",
            "TripGate.withinDeadline(firstWake, now)")
        val wait = SourceRoots.body(start, "private fun awaitReady(boot: String, firstWake: Long, cfg: TripConfig): TripHub.Host? {")
        order(wait, "TripGate.withinDeadline(firstWake, now)", "expiredOn = lastWait?.let { TripWaitMark(it) }", "TripHub.current()",
            "TripGate.bootReady(", "TripPlan.homeTopVisible(entries, homeComps)", "TripPlan.needsStage(cfg, ", "TripPlan.waitFor(",
            "needsStage = needsStage, sinceWakeMs = now - firstWake", "TripStart.setProgress(TripWaitMark(wait))")
    }

    /**
     * L4 · D2 — ĐỔI PIN có lý do: bố cục chỉ có widget từng ra `NO_STAGE` (0 lệnh) [ĐO `e2e/e6-no-app-slot`]. Nay sau đường ô
     * sống (đã đo, LUÔN trước — CLAUDE.md §6) là màn ảo ẨN (`startBehindHidden`), cùng bên thi hành/mutex của màn chính.
     */
    @Test
    fun `chay nen di qua ben thi hanh BEHIND-HOME cua man chinh - o song truoc, man ao an sau, mo binh thuong chi qua K10`() {
        val bg = SourceRoots.body(start, "private fun behind(host: TripHub.Host, pkg: String): BehindHomeSequence.Outcome {")
        order(bg, "host.startBehind(pkg, stages, done)", "host.behindChain(", "kit.seq.startBehindHidden(pkg, kit.hidden)")
        assertEquals("slots().startBehind(pkg, stages, done)", SourceRoots.body(glue, "override fun startBehind(").trim().removePrefix("=").trim())
        // Review 2.89 Pass 1 · behaviour-5 — ĐỔI GHIM có lý do: cổng mang cờ `needsAnchor` tới bên thi hành (KDoc `BehindHomeRunner.chain`).
        assertEquals("slots().behindChain(what, body, done, needsAnchor)", SourceRoots.body(glue, "override fun behindChain(").trim().removePrefix("=").trim())
        val normal = SourceRoots.body(start, "private fun normal(host: TripHub.Host, pkg: String): Pair<TripStepCode, String> {")
        order(normal, "cameraSignature ?: return", "BehindHomePlan.safeComponent(", "TripPlan.normalCmd(sig, homeComps, comp)",
            "BehindHomePlan.LIST_CMD", "TripPlan.normalOutcome(")
        listOf(start, music).forEach { src ->
            assertFalse(src.contains("\"am start") || src.contains("am force-stop") || src.contains("move-task"), "không dựng lệnh cửa sổ tay ở bên thi hành chuyến")
        }
    }

    // ── Nhạc ─────────────────────────────────────────────────────────────────────────────────────────────────

    /**
     * L4 · D3 — ĐỔI PIN có lý do (owner 03/10, xe 2.86: *"auto mở nhạc youtube không chạy? Cả để trống lẫn để link"*): bản cũ
     * chỉ giao nhạc qua PHIÊN — YouTube nguội không có phiên [ĐO máy ảo e3] ⇒ link không bao giờ tới. Nay: phiên trước (0 lệnh
     * cửa sổ, [ĐO] e5/e8), có link mà không phiên nhận URI ⇒ `Play.View` qua cổng [Ports.view] (chuỗi dàn dựng, lệnh dựng ở
     * `:core`). TripMusicRun vẫn KHÔNG tự dựng ý-định / lệnh cửa sổ nào.
     *
     * A2 · 2.89 — ĐỔI PIN có lý do (owner 05/10, xe 2.88: *"youtube nằm ở khung 1 nên nó tự mở chứ có cần phải setting mở app khi
     * nổ máy đâu?"*): "app ở ô" đọc MỚI ([Ports.where]), không từ ảnh chụp đầu chuyến; app ở ô ⇒ CHỜ chính ô đó sống rồi mới đọc
     * phiên; app ngoài ô ⇒ ô 7 ([Ports.park]) TRƯỚC, đường cũ ([Ports.behind]) chỉ khi `TripMusicPlace.fallBack`.
     */
    @Test
    fun `nhac - phien truoc, VIEW chi qua cong cua chuoi dan dung, doc phien TRUOC khi dung gi`() {
        listOf("ACTION_VIEW", "android.intent.action.VIEW", "startActivity", "sendToApp", "VoiceAppIntents.send(", "am start").forEach {
            assertFalse(music.contains(it), "bước nhạc không tự dựng ý-định/lệnh cửa sổ — cấm '$it'")
        }
        // Review 2.89 Pass 1 · behaviour-1 — ĐỔI GHIM có lý do: `where()` = `null` (màn chưa trả lời 2 s) KHÔNG còn là "ngoài ô";
        // ô của ảnh chụp chuyến chỉ là lối lùi qua `TripMusicPlace.entrySlot` (`:core`, có test).
        val fn = SourceRoots.body(music, "slotsAtStart: Map<String, Int> = emptyMap(),\n    ): Done {")
        order(fn, "bridge.sessions()", "TripMusicPlace.entrySlot(ports.where(it), slotsAtStart[it])", "TripMusicPlan.gate(", "TripOutcome.ofGate(gate)",
            "start(pkg, id, slot0, deadlineAt, progress)", "awaitSession(pkg)", "TripMusicPlan.recheck(pkg, bridge.sessions())",
            "return play(id, pkg, target, resume, url0, session, base, slot, deadlineAt, progress)")
        // 2.97 · R2c — ĐỔI GHIM có lý do: thân giao link tách ra `play(…)` (dùng chung đường thường + đường hoãn tới cuối chuyến).
        order(SourceRoots.body(music, "private fun play("), "TripMusicPlan.play(url, session)", "bridge.playFromUri(pkg, p.url)",
            // 2.96 · R9 — ĐỔI GHIM có lý do (log xe 07/10 21:05:14 `view:SLOT_NOT_READY` ⇒ NOOP): giao link + mã qua
            // `TripMusicPlace.viewWhenReady` (`:core`, test `TripMusicViewWaitTest`) — ô chưa sẵn ⇒ chờ ô sống lại rồi giao lại.
            "viewWhenReady(pkg, p.url, slot, deadlineAt, progress, resume.ready, target.watchFullscreenExtra)", "v.code.result == TripStepCode.Result.NOOP")
        // Lỗi E2E (6) [ĐO `c6b-music-slot`]: phiên của app TRONG Ô là phiên Kachi vừa tạo ⇒ không được đi nhánh resume-existing.
        order(fn, "TripMusicPlan.gate(", "TripMusicPlan.preexisting(pkg, before, inSlot = inSlot)", "resume-existing", "start(pkg, id, slot0")
        assertFalse(fn.contains("before.orEmpty().any"), "quyết 'phiên có trước' chỉ ở hàm thuần `TripMusicPlan.preexisting`")
        assertFalse(fn.contains("view.stages") || fn.contains("view.appSlots") || music.contains("TripHub.HomeView"),
            "bước nhạc không được dùng ảnh chụp ô đầu chuyến")
        // A2 (1)(2) — đưa app lên: ở ô ⇒ CHỜ ô (0 lệnh); rời bố cục ⇒ như ngoài ô; ngoài ô ⇒ hệ thống từ chối, ô 7, rồi mới đường cũ.
        val st = SourceRoots.body(music, "private fun start(pkg: String, id: String, slot0: Int?, deadlineAt: Long, progress: (TripWaitMark?) -> Unit): Start {")
        order(st, "if (slot0 != null) {", "progress(TripWaitMark.slotApp(pkg, slot0))", "TripMusicPlace.await(",
            "TripMusicPlace.until(SystemClock.elapsedRealtime(), deadlineAt)", "read = { ports.where(pkg) }", "stacks = ports::stacks",
            "progress(null)", "is TripMusicPlace.Waited.Alive -> return Start.Go(", "TripStepCode.SLOT_WAIT",
            "is TripMusicPlace.Waited.Left ->", "ports.isSystem(pkg)", "TripStepCode.SYSTEM_APP", "ports.park(pkg)",
            "TripMusicPlace.fallBack(parked.result)", "ports.behind(pkg)", "TripOutcome.ofBehind(out.result)")
        assertEquals(1, Regex(Regex.escape("ports.behind(pkg)")).findAll(music).count(), "đường cũ chỉ một chỗ: sau ô 7")
        assertFalse(st.substring(0, st.indexOf("ports.isSystem(pkg)")).contains("ports.park") || st.substring(0, st.indexOf("ports.isSystem(pkg)")).contains("ports.behind"),
            "app ở ô: KHÔNG chạy ngầm / ô 7 khi ô còn đó — hai đường không giành một app")
        // ĐỔI PIN có lý do (review 287 [P2]): bản cũ nhận `slotVd` từ ảnh chụp ô ĐẦU chuyến — ô của app nhạc chưa mở xong lúc đó ⇒
        // `null` ⇒ dàn qua `stageFor` ⇒ K4-VIEW kéo task của app khỏi ô của nó. Nay cổng nhận `inSlot`, đọc ô MỚI, quyết bằng
        // `TripMusicPlan.viewRoute` (`:core`, có test) và ô chưa sẵn ⇒ trả `null` TRƯỚC mọi lệnh / chuỗi. A2: app ở ô 7 ⇒ CHÍNH
        // màn ảo đỗ (`ParkedApps.vdOf`), không bao giờ màn ảo dàn dựng khác.
        // 2.96 · R10 — ĐỔI GHIM có lý do: cổng mang `fullscreenExtra` (chỉ phát tiếp) tới CÙNG lệnh K4-VIEW, mọi tuyến.
        val view = SourceRoots.body(start, "override fun view(pkg: String, url: String, inSlot: Boolean, fullscreenExtra: String?): BehindHomeSequence.Outcome? {")
        // Review 2.89 Pass 1 · behaviour-2/5 — ĐỔI GHIM có lý do: màn chính đọc LẠI mỗi lượt (`live()`), cờ `needsAnchor` theo tuyến.
        order(view, "val h = live()", "h.view()", "TripMusicPlan.viewRoute(inSlot, stages.firstOrNull { it.pkg == pkg }?.vd, ParkedApps.vdOf(pkg))",
            "if (route == TripMusicPlan.ViewRoute.SlotNotReady) return null", "TripMusicPlan.viewCmd(vd, url, pkg, fullscreenExtra)",
            "val anchor = route !is TripMusicPlan.ViewRoute.Slot && route !is TripMusicPlan.ViewRoute.Parked", "h.behindChain(",
            "if (route is TripMusicPlan.ViewRoute.Slot) viewInSlot(kit, pkg, route.vd, url, fullscreenExtra)",
            "else if (route is TripMusicPlan.ViewRoute.Parked) viewInSlot(kit, pkg, route.vd, url, fullscreenExtra)", "BehindHomePlan.stageFor(stages, pkg)",
            "kit.seq.startBehindHidden(pkg, kit.hidden, view = k4)", "kit.seq.startBehind(pkg, st, view = k4)")
        // NOT_IN_SLOT = 0 lệnh ⇒ mã 0-lệnh (`X_NOT_STAGED` ⇒ `SLOT_NOT_READY`), không còn KEPT_UNDER (OK ⇒ "đã gửi").
        assertTrue(start.contains("TripMusicView.Result.NOT_IN_SLOT -> BehindHomeSequence.Result.X_NOT_STAGED"))
        assertTrue(start.contains(".inSlot(pkg, vd, url, fullscreenExtra)"), "R10: extra tới cả lệnh K4-VIEW vào ô")
    }

    /**
     * A2 · 2.89 — cổng của bước nhạc ở `TripStart`: ô 7 đi CÙNG bên thi hành/mutex `kachi-behind` với màn ảo ẩn MỚI của lượt
     * (`Kit.park`, không màn ảo thứ hai), dấu bền TRƯỚC K12; ảnh chụp ô đọc trên luồng chính rồi quyết ở `:core`; đọc thẳng là
     * MỘT lệnh chỉ đọc. Thanh trạng thái Cài đặt chỉ ĐỌC cờ hiển thị.
     */
    @Test
    fun `A2 - cong o 7, cho o, doc thang deu qua dung cho, khong lenh doi cua so tay`() {
        val park = SourceRoots.body(start, "override fun park(pkg: String): BehindHomeSequence.Outcome = await(pkg) { done ->")
        // Review 2.89 Pass 1 · behaviour-2/5 — ĐỔI GHIM có lý do: `live()` thay `host`; ô 7 không dựng giữ chỗ ⇒ `needsAnchor = false`.
        order(park, "live().behindChain(\"park X=\$pkg\"", "BehindMarksStore(kit.app)", "HiddenPark(kit.sh, kit.app.packageName, AccessibilityRebind.GO_HOME_UNLESS_CAMERA",
            "marks.add(id, p)", ".park(pkg, kit.park)", "needsAnchor = false")
        order(SourceRoots.body(start, "override fun where(pkg: String): TripMusicPlace.Where? {"), "val h = live()", "h.view()",
            "TripMusicPlace.where(pkg, v.slots, v.stages)")
        val stacks = SourceRoots.body(start, "override fun stacks(): List<StackEntry>? {")
        assertTrue(stacks.contains("sh(BehindHomePlan.LIST_CMD)") && !stacks.contains("am "), "đọc thẳng = đúng một lệnh chỉ đọc")
        val runner = code("src/main/java/com/byd/clusternav/launcher/behind/BehindHomeRunner.kt")
        assertTrue(runner.contains("Kit(seq, hidden, sh, app, park = hidden)"), "ô 7 dùng CHÍNH màn ảo ẩn của lượt")
        val view = SourceRoots.body(glue, "override fun view(): TripHub.HomeView {")
        order(view, "shown.take(count).withIndex()", "appSlots = apps.map { it.first }", "slots = apps.distinctBy { it.first }.toMap()")
        val run = SourceRoots.body(start, "fun run() {")
        order(run, "val code = try { body(boot, firstWake) } finally { TripStart.setProgress(null) }",
            "if (code == TripGate.Code.EXPIRED) expiredOn else null", "steps.toList(), wait)")
    }
    @Test
    fun `tu khoa di qua LOI giai bai chung voi giong noi - mot cho dung Handoff watch`() {
        val url = SourceRoots.body(music, "private fun urlFor(")
        assertTrue(url.contains("VoiceAppIntents.watchHandoff(target, pkg, s.q, VoiceYoutubeResolver::firstVideoIdBounded)"))
        assertTrue(url.contains("TripMusicPlan.safeWatchUrl("), "URL nào cũng qua rào trước khi tới phiên nhạc")
        val q = SourceRoots.body(voice, "private fun runMediaQuery(")
        assertTrue(q.contains("VoiceAppIntents.watchHandoff(target, pkg, i.query, resolveVideo)"), "giọng nói dùng CHÍNH lõi đó")
        assertFalse(q.contains("VoiceAppIntents.Handoff("), "không còn bản dựng Handoff watch thứ hai")
        assertEquals(1, Regex(Regex.escape("Handoff(pkg, watch, vid, null, null)")).findAll(intents).count())
    }

    // ── Cài đặt + ViewModel ──────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `cau hinh chuyen ghi qua MOT duong ViewModel, ngan keo che do PICK_TRIP`() {
        val set = SourceRoots.body(vm, "fun setTripConfig(cfg: TripConfig) {")
        order(set, "TripAppCodec.sanitize(cfg.apps)", "_uiState.update", "repository.setTripConfig(clean)")
        assertEquals(1, Regex("repository\\.setTripConfig\\(").findAll(vm).count(), "một đường ghi")
        assertTrue(SourceRoots.body(glue, "override fun save(").contains("viewModel.setTripConfig(cfg)"))
        assertTrue(SourceRoots.body(glue, "override fun openPicker(").contains("AppDrawer.Mode.PICK_TRIP"))
        val view = SourceRoots.body(glue, "override fun view(): TripHub.HomeView {")
        assertTrue(view.contains("workspace().stagingCandidates(shown, count)"), "CÙNG bộ chọn ô dàn dựng với lối tắt (A5)")
    }

    @Test
    fun `moi ham moi deu co call site`() {
        val all = SourceRoots.moduleSourceRoots().flatMap { root ->
            Files.walk(root).use { s -> s.filter { it.toString().endsWith(".kt") }.toList() }
        }.map { p ->
            p.fileName.toString() to KotlinSource.stripComments(p.toFile().readText())
        }
        assertTrue(all.size > 300, "quét được quá ít tệp (${all.size}) — đường dẫn sai thì bài này là test giả")
        mapOf(
            "TripStart.onReady(" to "EarlyShellChannel.kt",
            "TripStart.onBootCompleted(" to "RebindReceiver.kt",
            "TripStart.describe(" to "DiagActivity.kt",
            "TripStart.last(" to "SettingsSectionsTrip.kt",
            "KachiReadyLog.firstWakeAt()" to "TripStart.kt",
            "TripHub.bind(" to "KachiHomeTrip.kt",
            "KachiHomeTrip(" to "KachiHomeActivity.kt",
            "TripMusicRun(" to "TripStart.kt",
            "TripOutcome.tripCode(" to "TripStart.kt",
            "tripResultRows(" to "SettingsSectionsTrip.kt",
            "TripStart.now(" to "SettingsSectionsTrip.kt",
            "StagingDisplay(" to "BehindHomeRunner.kt",
            "TripMusicView(" to "TripStart.kt",
            "TripMusicPlan.viewCmd(" to "TripStart.kt",
            "BehindHomePlan.stageFor(" to "TripStart.kt",
            "TripMusicPlan.viewRoute(" to "TripStart.kt",
            "TripMusicPlace.viewWhenReady(" to "TripMusicRun.kt",   // 2.96 · R9 — ĐỔI GHIM: `TripOutcome.ofView` nay gọi trong `:core`
            "HiddenStageReclaim.run(" to "BehindHomeRunner.kt",
            "InstalledApps.isSystem(" to "SettingsSectionsTrip.kt",
            "bridge.sessions()" to "TripMusicRun.kt",
            "bridge.playPackage(" to "TripMusicRun.kt",
            "bridge.playFromUri(" to "TripMusicRun.kt",
            "VoiceAppIntents.urlOf(" to "TripMusicRun.kt",
            "prefs.tripConfig()" to "PrefsWorkspaceRepository.kt",
            "prefs.setTripConfig(" to "PrefsWorkspaceRepository.kt",
            "WorkspacePrefs(app).tripConfig()" to "TripStart.kt",
            "SettingsTripAppsSection(" to "SettingsSections.kt",
            "SettingsTripMusicSection(" to "SettingsSections.kt",   // 2.89 · A5(a) — ĐỔI GHIM: sang Hệ thống › Khởi động
            "SettingsDialogs.askText(" to "SettingsSectionsTrip.kt",
            "TripAppCodec.apply(" to "SettingsSectionsTrip.kt",
            "TripAppCodec.setMode(" to "SettingsSectionsTrip.kt",
            "TripAppCodec.remove(" to "SettingsSectionsTrip.kt",
            // A2 · 2.89 (TRIP-MUSIC-IN-SLOT) — hàm mới phải có call site thật (CLAUDE.md §8).
            "TripMusicPlace.await(" to "TripMusicRun.kt",
            "TripMusicPlace.fallBack(" to "TripMusicRun.kt",
            "TripMusicPlace.entrySlot(" to "TripMusicRun.kt",   // review 2.89 Pass 1 · behaviour-1
            "TripMusicPlace.where(" to "TripStart.kt",
            "TripPlan.needsStage(" to "TripStart.kt",
            "HiddenPark(" to "TripStart.kt",
            "ParkedApps.adoptHidden(" to "StagingDisplay.kt",
            "ParkedApps.vdOf(" to "TripStart.kt",
            "TripStart.progress()" to "SettingsSectionsTrip.kt",
            "tripWaitText(" to "SettingsSectionsTrip.kt",
            "TripWaitMark.slotApp(" to "TripMusicRun.kt",
            "putAll(TripGate.DEVICE_KEYS)" to "ProfileScope.kt",
        ).forEach { (call, file) ->
            assertTrue(all.any { it.first == file && it.second.contains(call) }, "'$call' phải được gọi trong $file")
        }
    }

    @Test
    fun `chu moi du hai ban, ban EN khong con dau tieng Viet`() {
        val vi = SourceRoots.text("src/main/res/values/strings_kachi.xml")
        val en = SourceRoots.text("src/main/res/values-en/strings_kachi.xml")
        val keys = Regex("name=\"(kachi_trip_[a-z_]+|kachi_drawer_(title|hint)_trip)\"").findAll(vi).map { it.groupValues[1] }.toList()
        assertTrue(keys.size >= 25, "bộ quét thấy quá ít chuỗi chuyến (${keys.size})")
        val marks = "àáảãạăằắẳẵặâầấẩẫậèéẻẽẹêềếểễệìíỉĩịòóỏõọôồốổỗộơờớởỡợùúủũụưừứửữựỳýỷỹỵđ"
        keys.forEach { k ->
            val line = Regex("<string name=\"$k\">([^<]*)</string>").find(en)?.groupValues?.get(1)
            assertTrue(line != null, "thiếu bản EN của $k")
            assertTrue(line!!.lowercase().none { it in marks }, "bản EN của $k còn dấu tiếng Việt: $line")
        }
    }

    /**
     * Review 2.89 Pass 1 · behaviour-1/2/5 — ba lỗi của cổng bước nhạc:
     *  - (1) `where()` = `null` (luồng chính bận > 2 s) bị coi là "ngoài ô" ⇒ ô 7 + BEHIND-HOME giành app đang mở trong ô. Chuyến
     *    nay truyền ảnh chụp ô (`view.slots`) làm lối lùi.
     *  - (2) cổng bắt `host` của `awaitReady` rồi gọi 90 s — màn dựng lại (`uiMode` / ngôn ngữ ⇒ `recreate()`) thì host đó ĐÃ CHẾT
     *    (`stage()` = `null`) ⇒ `SLOT_WAIT` dù app chạy trong ô của màn mới. Mọi cổng nay đọc `TripHub.current()` mỗi lượt.
     *  - (5) công tắc tắt BEHIND-HOME (do GIỮ CHỖ) chặn cả ô 7 / K4-VIEW vào ô — hai chuỗi không dựng giữ chỗ.
     */
    @Test
    fun `cong buoc nhac - anh chup lam loi lui, man song moi luot, o 7 khong chiu cong tat giu cho`() {
        val body = SourceRoots.body(start, "private fun body(boot: String, firstWake: Long): TripGate.Code {")
        assertTrue(body.contains(".run(step.music, installed, firstWake + TripGate.TRIP_DEADLINE_MS, TripStart::setProgress, view.slots)"),
            "chuyến phải truyền ảnh chụp ô cho bước nhạc (lối lùi khi màn chưa trả lời)")
        val ports = SourceRoots.body(start, "private fun musicPorts(host: TripHub.Host) = object : TripMusicRun.Ports {")
        assertTrue(ports.contains("private fun live(): TripHub.Host = TripHub.current() ?: host"), "màn đang sống, lùi về host cũ")
        assertFalse(Regex("""\bhost\.""").containsMatchIn(ports), "không cổng nào được gọi thẳng host đã bắt (màn có thể đã chết):\n$ports")
        listOf("override fun behind(pkg: String) = behind(live(), pkg)", "live().behindChain(", "val sh = live().shell()").forEach {
            assertTrue(ports.contains(it), "thiếu '$it'")
        }
        assertEquals(2, Regex(Regex.escape("val sh = live().shell()")).findAll(ports).count(), "stacks() + facts()")
        // (5) bên thi hành: công tắc tắt chỉ chặn chuỗi dựng giữ chỗ; mặc định = hành vi cũ.
        val runner = code("src/main/java/com/byd/clusternav/launcher/behind/BehindHomeRunner.kt")
        val once = SourceRoots.body(runner, "private fun runOnce(what: String, body: (Kit) -> BehindHomeSequence.Outcome, needsAnchor: Boolean): BehindHomeSequence.Outcome {")
        assertTrue(once.contains("if (needsAnchor) disabledReason?.let"), "DISABLED chỉ cho chuỗi dựng giữ chỗ")
        assertTrue(runner.contains("needsAnchor: Boolean = true,\n        body: (Kit) -> BehindHomeSequence.Outcome,\n    ) = submit(what, done, needsAnchor, body)"),
            "chain() mặc định needsAnchor = true")
        val hub = code("src/main/java/com/byd/clusternav/launcher/trip/TripHub.kt")
        assertTrue(hub.contains("needsAnchor: Boolean = true,"), "cổng TripHub mặc định = có giữ chỗ (an toàn)")
        assertEquals(2, Regex("needsAnchor = (false|anchor)").findAll(ports).count(), "đúng hai chuỗi không giữ chỗ: ô 7 + K4-VIEW vào ô")
    }
}
