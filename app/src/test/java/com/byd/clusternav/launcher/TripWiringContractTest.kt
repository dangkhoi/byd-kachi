package com.byd.clusternav.launcher

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
 *  - nhạc KHÔNG dùng ý-định VIEW [ĐO máy ảo 02/10 `trip/tm3-ytmusic.txt`: che màn nhà] — đi qua phiên nhạc;
 *  - "không đè": đọc phiên TRƯỚC khi đụng gì, `null` ⇒ bỏ (R3.5);
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
        order(chain, "interactive(app) != true", "BehindHomeRecovery.onReady(app)", "KeyReady.prepare(app)",
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
        order(fn, "tripConfig()", "if (cfg.empty)", "awaitReady(boot, firstWake)", "TripPlan.steps(cfg, facts)",
            "TripGate.withinDeadline(firstWake, now)")
        val wait = SourceRoots.body(start, "private fun awaitReady(boot: String, firstWake: Long): TripHub.Host? {")
        order(wait, "TripGate.withinDeadline(firstWake, now)", "TripHub.current()", "TripGate.bootReady(",
            "TripPlan.homeTopVisible(entries, homeComps)", "TripPlan.waitFor(")
    }

    @Test
    fun `chay nen di qua ben thi hanh BEHIND-HOME cua man chinh, mo binh thuong chi qua K10`() {
        val bg = SourceRoots.body(start, "private fun behind(host: TripHub.Host, pkg: String): BehindHomeSequence.Outcome? {")
        assertTrue(bg.contains("host.startBehind(pkg, stages)"))
        assertEquals("slots().startBehind(pkg, stages, done)", SourceRoots.body(glue, "override fun startBehind(").trim().removePrefix("=").trim())
        val normal = SourceRoots.body(start, "private fun normal(host: TripHub.Host, pkg: String): String {")
        order(normal, "cameraSignature ?: return", "BehindHomePlan.safeComponent(", "TripPlan.normalCmd(sig, homeComps, comp)",
            "BehindHomePlan.LIST_CMD", "TripPlan.normalOutcome(")
        listOf(start, music).forEach { src ->
            assertFalse(src.contains("\"am start") || src.contains("am force-stop") || src.contains("move-task"), "không dựng lệnh cửa sổ tay ở bên thi hành chuyến")
        }
    }

    // ── Nhạc ─────────────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `nhac khong co y-dinh VIEW nao - di qua phien nhac, va doc phien TRUOC khi dung gi`() {
        listOf("ACTION_VIEW", "android.intent.action.VIEW", "startActivity", "sendToApp", "VoiceAppIntents.send(").forEach {
            assertFalse(music.contains(it), "ý-định VIEW vào màn ảo ô che màn nhà [ĐO trip/tm3-ytmusic.txt] — cấm '$it'")
        }
        val fn = SourceRoots.body(music, "fun run(music: TripMusic, installed: Set<String>, view: TripHub.HomeView): String {")
        order(fn, "bridge.sessions()", "TripMusicPlan.gate(", "if (gate != TripMusicPlan.Gate.GO", "behind(pkg)",
            "awaitSession(pkg)", "TripMusicPlan.recheck(pkg, bridge.sessions()", "TripMusicPlan.play(url, hasSession)",
            "bridge.playFromUri(pkg, p.url)")
        assertTrue(fn.contains("if (pkg in view.appSlots) \"in-slot\""), "app đã ở ô ⇒ ô tự mở nó, KHÔNG lệnh thêm (R3.3)")
        // Lỗi E2E (6) [ĐO `c6b-music-slot`]: phiên của app TRONG Ô là phiên Kachi vừa tạo ⇒ không được đi nhánh resume-existing.
        order(fn, "TripMusicPlan.gate(", "TripMusicPlan.preexisting(pkg, before, inSlot = pkg in view.appSlots)", "resume-existing", "behind(pkg)")
        assertFalse(fn.contains("before.orEmpty().any"), "quyết 'phiên có trước' chỉ ở hàm thuần `TripMusicPlan.preexisting`")
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
            p.fileName.toString() to p.toFile().readText().replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), " ")
                .lines().joinToString("\n") { it.substringBefore("//") }
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
            "bridge.sessions()" to "TripMusicRun.kt",
            "bridge.playPackage(" to "TripMusicRun.kt",
            "bridge.playFromUri(" to "TripMusicRun.kt",
            "VoiceAppIntents.urlOf(" to "TripMusicRun.kt",
            "prefs.tripConfig()" to "PrefsWorkspaceRepository.kt",
            "prefs.setTripConfig(" to "PrefsWorkspaceRepository.kt",
            "WorkspacePrefs(app).tripConfig()" to "TripStart.kt",
            "SettingsTripAppsSection(" to "SettingsSections.kt",
            "SettingsTripMusicSection(" to "SettingsVoiceSection.kt",
            "SettingsDialogs.askText(" to "SettingsSectionsTrip.kt",
            "TripAppCodec.apply(" to "SettingsSectionsTrip.kt",
            "TripAppCodec.setMode(" to "SettingsSectionsTrip.kt",
            "TripAppCodec.remove(" to "SettingsSectionsTrip.kt",
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
}
