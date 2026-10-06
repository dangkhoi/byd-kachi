package com.byd.clusternav

import com.byd.clusternav.testsupport.KotlinSource
import com.byd.clusternav.testsupport.SourceRoots
import java.nio.file.Files
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ READY-AT-HOME — BÀI CANH DÂY NỐI (spec `docs/specs/kachi-ready-at-home.html` §6.1 V-static) ═══════════════════
 *
 * Luật thuần đã khoá ở `:car-integration` (`ShellReadinessPolicyTest`, `LocalShellAdmissionTest`) và `:core`
 * (`KeyReadyPlanTest`). Bài này canh phần chỉ Android mới có: AI GỌI luật đó, ở ĐÂU, theo THỨ TỰ nào. Mọi vùng cắt bằng
 * [SourceRoots.body] (nổ nếu mốc không còn — không quét tràn) trên mã đã bỏ chú thích ([SourceRoots.codeOf]).
 */
class ReadyAtHomeWiringContractTest {

    private fun code(rel: String) = SourceRoots.codeOf(rel)
    private val app by lazy { code("src/main/java/com/byd/clusternav/KachiApplication.kt") }
    private val early by lazy { code("src/main/java/com/byd/clusternav/EarlyShellChannel.kt") }
    private val readiness by lazy { code("src/main/java/com/byd/clusternav/ShellReadiness.kt") }
    private val keyReady by lazy { code("src/main/java/com/byd/clusternav/KeyReady.kt") }
    private val gate by lazy { code("src/main/java/com/byd/clusternav/launcher/ShellChannelGate.kt") }
    private val windows by lazy { code("src/main/java/com/byd/clusternav/launcher/LauncherWindows.kt") }
    private val home by lazy { code("src/main/java/com/byd/clusternav/launcher/KachiHomeActivity.kt") }
    private val wiring by lazy { code("src/main/java/com/byd/clusternav/launcher/ReadyAtHomeWiring.kt") }

    private fun order(src: String, vararg tokens: String) {
        val at = tokens.map { t -> src.indexOf(t).also { assertTrue(it >= 0, "thiếu '$t' trong vùng quét") } }
        assertEquals(at.sorted(), at, "thứ tự sai: ${tokens.joinToString(" → ")}")
    }

    // ── R2.1 · nối sớm ở tầng tiến trình, đường mới xuống CUỐI ─────────────────────────────────────────────

    @Test
    fun `cong thi hanh cai TRUOC lop 1, luot som la dong CUOI cua onCreate`() {
        val onCreate = SourceRoots.body(app, "override fun onCreate() {")
        order(
            onCreate,
            "if (isBackgroundVoiceProcess()) return",
            "ShellReadiness.install(this)",
            "A11yLifecycleHeal.install(this)",
            "AppContainer.get(this)",
            "EarlyShellChannel.start(this)",
        )
        val tail = onCreate.substring(onCreate.indexOf("EarlyShellChannel.start(this)") + "EarlyShellChannel.start(this)".length)
        assertEquals("}", tail.trim(), "EarlyShellChannel.start phải là lời gọi CUỐI (CLAUDE.md §6 — đường mới xuống cuối)")
    }

    @Test
    fun `luot som CHI noi khi co dau tuoi va dung luot do cua F4, khong ShellTransport`() {
        val fn = SourceRoots.body(early, "private fun earlyBody(app: Context) {")
        order(fn, "ShellReadinessPolicy.earlyPlan(", "if (plan != EarlyPlan.CONNECT) {", "return", "ShellApprovalProbe.probe(app)")
        listOf("ShellTransport", "AWAIT_ADB_APPROVAL", "USER_READ_CAP", "Dadb").forEach {
            assertFalse(early.contains(it), "lượt sớm không được dùng '$it' (hai lượt hỏi / kết nối dùng chung / hỏi người dùng từ nền)")
        }
    }

    @Test
    fun `luot som gap thu hoi thi xoa dau va KHONG thu lai tu nen`() {
        val fn = SourceRoots.body(early, "private fun earlyBody(app: Context) {")
        assertTrue(fn.contains("EarlyStep.Revoked -> { ShellReadiness.reportRevoked(\"early\"); return }"))
        assertTrue(fn.contains("ShellReadinessPolicy.afterEarly(failure, attempt)"), "thử lại do luật thuần quyết (chỉ lỗi môi trường)")
        val apply = SourceRoots.body(readiness, "fun apply(event: ReadyEvent, src: String) {")
        assertTrue(apply.contains("LedgerOp.FORGET -> forgetLedger(src)"), "thu hồi ⇒ xoá dấu")
    }

    // ── §4.6 · cổng thi hành ở đủ BA cửa, trước khi mở kết nối ─────────────────────────────────────────────

    @Test
    fun `ba cua transport deu hoi cong TRUOC khi mo ket noi`() {
        val retry = code("src/main/kotlin/com/byd/clusternav/carexec/LocalShellRetryPolicy.kt")
        order(SourceRoots.body(retry, "fun <T> run("), "LocalShellAdmission.admit(kind)", "connector.open(")
        val shell = code("src/main/kotlin/com/byd/clusternav/carexec/LocalDeviceShell.kt")
        order(SourceRoots.body(shell, "fun installApk("), "LocalShellAdmission.admit(ShellSessionKind.BACKGROUND)", "Dadb.create(")
        val transport = code("src/main/java/com/byd/clusternav/system/ShellTransport.kt")
        order(SourceRoots.body(transport, "fun exec("), "LocalShellAdmission.admit(ShellSessionKind.BACKGROUND)", "attempt(cmd)")
        assertTrue(readiness.contains("LocalShellAdmission.install(this)"), "móc thật phải được cài")
    }

    @Test
    fun `dau ben chi ghi o markUp, chi xoa o forget, va chi ShellReadiness duoc goi`() {
        val store = code("src/main/java/com/byd/clusternav/ShellApprovalStore.kt")
        assertEquals(2, Regex("\\.putString\\(KEY,").findAll(store).count(), "đúng hai đường ghi")
        assertTrue(SourceRoots.body(store, "fun markUp(").contains(".putString(KEY,"))
        assertTrue(SourceRoots.body(store, "fun forget(").contains(".putString(KEY,"))
        val offenders = mainFiles().filter { (name, src) ->
            name != "ShellReadiness.kt" && name != "ShellApprovalStore.kt" &&
                (src.contains(".markUp(") || src.contains("ShellApprovalStore(") || src.contains("ShellApprovalStore.KEY"))
        }.map { it.first }
        assertEquals(emptyList<String>(), offenders, "dấu bền chỉ được chạm qua ShellReadiness (một chủ)")
    }

    // ── §4.7 · HOME nhận kênh đã sẵn ───────────────────────────────────────────────────────────────────────

    @Test
    fun `adopt doi man TUONG TAC, khung da ve, man hien, khong F4 dang bay - roi giu o truoc onChannelUp`() {
        val fn = SourceRoots.body(gate, "fun adopt() {")
        assertTrue(fn.contains("ShellReadinessPolicy.adoptAllowed(ShellReadiness.isUp(), framed, showing, interactiveNow(), channelUp, inFlight)"))
        order(fn, "channelUp = true", "KeyReady.holdTileIfEscalating(app)", "onChannelUp()")
        assertTrue(SourceRoots.body(gate, "private fun interactiveNow()").contains("isInteractive"))
        listOf("fun arm()", "fun onShown()", "fun onFocus(").forEach { sig ->
            val b = SourceRoots.body(gate, sig)
            assertTrue(b.contains("adopt()"), "$sig phải thử nhận kênh sẵn")
        }
        order(SourceRoots.body(gate, "fun onShown()"), "adopt()", "schedule(FirstOpenApproval.SETTLE_MS)")
        assertTrue(wiring.contains("gate.adopt()"), "kênh lên ở tầng tiến trình ⇒ màn đang hiện nhận ngay")
        assertTrue(home.contains("wireReadyAtHome(this, shellGate, rootFrame, handler)"))
    }

    @Test
    fun `F4 bao ket qua do duoc len tang tien trinh, va khong do chong len luot som`() {
        val settle = SourceRoots.body(gate, "private fun settle(")
        assertTrue(settle.contains("ShellReadiness.reportUp(\"f4\")"))
        assertTrue(settle.contains("ShellReadiness.reportNeedsApproval(\"f4\")"))
        val probe = SourceRoots.body(gate, "fun probeForHome(ctx: Context)")
        order(probe, "ShellReadiness.awaitSettled(", "if (s.phase == ShellChannelPhase.UP) return null", "LocalShellAdmission.labeled(\"f4\") { probe(ctx) }")
    }

    // ── §4.8 · kiểm phím TRƯỚC khi dựng ô, dùng lại 2.83 ───────────────────────────────────────────────────

    @Test
    fun `chuoi chuan bi kiem phim truoc - binder, cho lop 2, roi moi cap bang ham B1`() {
        val fn = SourceRoots.body(keyReady, "fun prepare(app: Context): String {")
        order(fn, "NavConnect.boundPerAccessibilityManager(app)", "A11yLifecycleHeal.awaitMoXeVerdict(", "NavConnect.grantAccessibilityDetailed(app)")
        listOf("LocalDeviceShell", "ShellTransport", "force-stop", "settings put").forEach {
            assertFalse(keyReady.contains(it), "không có đường chữa thứ hai ('$it') — DRY, dùng lại 2.83")
        }
        val chain = SourceRoots.body(early, "private fun readyChain(")
        order(chain, "WakeEpochPolicy.shouldRun(prev, epoch, ShellReadiness.isUp(), interactive(app))", "KeyReady.prepare(app)", "VoiceKeyKeepAliveService.sync(app)")
    }

    // ── Review lượt 1 (02/10) — mỗi bài khoá một lỗi E2E/review đã thấy ─────────────────────────────────────

    /**
     * [P2] E2E 02/10 ca 1: `keys=GRANT` in lúc lượt cấp VỪA BẮT ĐẦU, kết quả thật (NOT_BOUND — kẹt, pha RUNNING) không
     * bao giờ tới nhật ký/màn Chẩn đoán. Kết quả phải được ghi lại khi lượt cấp xong, và màn Chẩn đoán đọc kết luận MỚI NHẤT.
     */
    @Test
    fun `ket qua cap phim THAT duoc ghi lai, khong chi nhan luc bat dau`() {
        val fn = SourceRoots.body(keyReady, "fun prepare(app: Context): String {")
        order(fn, "KachiReadyLog.keys(label)", "NavConnect.grantAccessibilityDetailed(app) { r -> KachiReadyLog.keys(\"GRANT->")
        assertFalse(fn.contains("NavConnect.grantAccessibility(app)"), "vỏ Boolean nuốt mất kết quả ba ca")
        val log = code("src/main/java/com/byd/clusternav/KachiReadyLog.kt")
        assertTrue(SourceRoots.body(log, "fun summaryForDiag()").contains("keys(now)=\$keys"))
    }

    /**
     * [P2] Màn đã nối dây (`channelUp`) mà kênh mức tiến trình rơi khỏi UP ⇒ `attempt` thoát ngay ⇒ *Hỏi lại* chết. Nhánh
     * mới phải HỎI bằng phiên người-dùng-bấm, KHÔNG chạy lại `onChannelUp` (nối dây hai lần).
     */
    @Test
    fun `Hoi lai khi man da noi day van hoi duoc, khong noi day lan hai`() {
        val fn = SourceRoots.body(gate, "fun retryNow()")
        order(fn, "if (channelUp) {", "ShellReadiness.isUp()", "ShellApprovalProbe.askUser(activity)", "handler.post(attemptRunnable)")
        assertFalse(fn.contains("onChannelUp"), "dây đã nối — chỉ thiếu khoá được nhận")
        val ask = SourceRoots.body(gate, "fun askUser(ctx: Context)")
        assertTrue(ask.contains("LocalShellRetry.USER_READ_CAP"), "đường HỎI do người dùng bấm (hạn đọc 30 s, R1.1)")
    }

    /**
     * [P3] E2E 02/10 ca 3a: cổng 5555 đóng ⇒ F4 phải tự hẹn dò lại (luật thuần, chỉ PORT_CLOSED). Review lượt 2: kể cả có
     * dấu tươi — dấu chỉ mở đường nền, không đưa màn chính lên.
     */
    @Test
    fun `F4 gap cong dong thi tu hen do lai`() {
        val settle = SourceRoots.body(gate, "private fun settle(")
        order(settle, "is FirstOpenStep.Environment ->", "ShellReadinessPolicy.envReprobeMs(step.reason)", "schedule(again)")
        assertFalse(settle.contains("ledgerFresh"), "dấu tươi không được miễn dò lại — HOME chỉ nối dây qua F4/adopt")
    }

    /**
     * [P3] review lượt 2: kênh lên ở tầng tiến trình TRONG lúc lượt F4 bay ⇒ [adopt] của bên nghe bị hoãn (`inFlight`) và
     * không có sự kiện nào gọi lại; lượt F4 trả MÔI TRƯỜNG ⇒ HOME không bao giờ nối dây. `settle` phải thử nhận SAU khi nhả
     * `inFlight` và sau mọi nhánh.
     */
    @Test
    fun `F4 tra ve thi thu nhan kenh da len trong luc no bay`() {
        val settle = SourceRoots.body(gate, "private fun settle(")
        order(settle, "inFlight = false", "is FirstOpenStep.Environment ->", "schedule(again)", "adopt()")
    }

    /**
     * [P2] review lượt 3: nối dây hỏng (nhánh `dadb.probe()` false của `bringUpShellChannel` — vd cổng 5555 chưa mở lúc
     * màn bật, sau khi lượt sớm đo UP lúc màn tắt) mà cờ `channelUp` kẹt `true` ⇒ không lượt F4 nào chạy lại, `adopt` tự
     * bỏ, *Thử lại* trên thẻ thoát ở nhánh `channelUp` ⇒ ô "Đang kết nối…" tới khi màn chính bị dựng lại. Nối dây xong mà
     * màn vẫn không có kênh ⇒ nhả cờ + hẹn lượt F4 (cùng nhịp, cùng cổng vòng đời — không đếm số lần).
     */
    @Test
    fun `noi day hong thi nha co va hen F4, khong ket o da nhan`() {
        order(SourceRoots.body(home, "onChannelUp = {"), "bringUpShellChannel(", "if (shell == null) shellGate.wiringFailed()")
        val fn = SourceRoots.body(gate, "fun wiringFailed()")
        order(fn, "handler.post", "if (!channelUp) return@post", "channelUp = false", "schedule(FirstOpenApproval.RETRY_EVERY_MS)")
    }

    /**
     * [P3] review lượt 3: `ShellReadiness.apply` gọi bên nghe NGOÀI khoá ⇒ hai lần chuyển từ hai luồng có thể tới bên nghe
     * ngược thứ tự. Bên vẽ (thẻ, chữ ô) phải đọc trạng thái MỚI NHẤT lúc chạy, không dùng giá trị chụp lúc báo.
     */
    @Test
    fun `ben ve doc trang thai moi nhat, khong dung gia tri chup luc bao`() {
        assertTrue(wiring.contains("ShellAccessUi.onState(ShellReadiness.state(), root.hasWindowFocus())"))
        val card = code("src/main/java/com/byd/clusternav/launcher/ShellAccessCard.kt")
        assertTrue(SourceRoots.body(card, "fun tileHint(ctx: Context)").contains("post { set(ShellReadiness.state()) }"))
    }

    /** [P3] E2E 02/10 ca 3b: thẻ biến mất tức thì lúc kênh tự lên ⇒ cú chạm *Hỏi lại* rơi xuống ô bên dưới. */
    @Test
    fun `kenh tu len thi the mo dan, khong bien mat tuc thi`() {
        val card = code("src/main/java/com/byd/clusternav/launcher/ShellAccessCard.kt")
        val onState = SourceRoots.body(card, "fun onState(s: ShellReadinessState, focused: Boolean)")
        assertTrue(onState.contains("fadeOutCard()") && !onState.contains("hideCard()"))
        assertTrue(SourceRoots.body(card, "private fun fadeOutCard()").contains(".withEndAction"))
    }

    /** [P3] Nguồn của dòng `up src=` là ĐƯỜNG (`early`/`f4`), không phải tên luồng (spec §4.10). */
    @Test
    fun `luot som va F4 gan nhan nguon cho phien do`() {
        assertTrue(SourceRoots.body(early, "private fun earlyBody(app: Context) {")
            .contains("LocalShellAdmission.labeled(\"early\") { ShellApprovalProbe.probe(app) }"))
    }

    // ── R1.3 · chưa có quyền thì không dùng được app — KHÔNG mở cửa sổ nổi ─────────────────────────────────

    @Test
    fun `placeApp khong con duong mo cua so noi nao, chi cho kenh hoac hien the`() {
        val fn = SourceRoots.body(windows, "fun placeApp(")
        listOf("openInSlot", "moveToSlot", "force-stop", "markOpened", "submit", "launcher").forEach {
            assertFalse(fn.contains(it), "placeApp không được còn '$it' (R1.3: không quyền thì không mở nổi)")
        }
        order(fn, "if (embedding()) return", "ShellAccessUi.slotTap { embedding() }")
    }

    /**
     * 2.93 · READY-AT-HOME-OQ6 (spec `docs/specs/kachi-293-slot.html` R7) — đường cửa sổ nổi của bộ mở chưa-có-kênh GỠ HẲN:
     * `IntentAppLauncher` (startActivity + khung khởi chạy + freeform qua phản chiếu) không còn trong cây nguồn; màn nhà chưa có
     * kênh dùng [com.byd.clusternav.launcher.NoCar] (cùng hành vi: `closeSlot` rỗng, không mở gì). KHÔNG tệp nguồn nào của sản
     * phẩm gọi `openInSlot`/`moveToSlot` (gỡ an toàn — không đổi hành vi). Đỏ khi ai đó nối lại đường nổi.
     */
    @Test
    fun `OQ6 bo mo chua co kenh khong con duong cua so noi, khong ai goi openInSlot moveToSlot`() {
        val files = mainFiles()
        assertTrue(files.size > 100, "quét được quá ít tệp (${files.size}) — đường dẫn sai thì bài này là test giả")
        assertFalse(files.any { it.first == "IntentAppLauncher.kt" }, "IntentAppLauncher phải gỡ hẳn (OQ6)")
        assertFalse(files.any { (_, src) -> Regex("""\bIntentAppLauncher\b""").containsMatchIn(src) }, "không ai còn dựng IntentAppLauncher")
        assertTrue(home.contains("@Volatile private var appLauncher: AppLauncher = NoCar"), "chưa có kênh ⇒ bộ mở no-op")
        val callers = files.filter { (_, src) -> Regex("""\.(openInSlot|moveToSlot)\(""").containsMatchIn(src) }.map { it.first }
        // 2.93 wave 2A · SLOT-DEAD-OPENSLOT: hai hàm ấy đã GỠ khỏi AppLauncher/ShellAppLauncher (ShellAppLauncherTest khoá việc gỡ);
        // bài này vẫn giữ — ai nối lại đường nổi dưới tên đó thì đỏ.
        assertEquals(emptyList<String>(), callers, "không mã sản phẩm nào được gọi openInSlot/moveToSlot (READY-AT-HOME R1.3)")
    }

    // ── §8 · hàm mới phải có call site ngoài định nghĩa ────────────────────────────────────────────────────

    @Test
    fun `moi ham moi deu co call site`() {
        val all = mainFiles()
        mapOf(
            "EarlyShellChannel.start(" to "KachiApplication.kt",
            "ShellReadiness.install(" to "KachiApplication.kt",
            "wireReadyAtHome(" to "KachiHomeActivity.kt",
            "ShellAccessUi.slotTap" to "LauncherWindows.kt",
            "ShellAccessUi.tileHint(" to "WorkspaceViewCards.kt",
            "ShellAccessUi.allowOrPrompt(" to "ClusterNavBridgeCast.kt",
            "KachiReadyLog.tile(" to "VdAppHost.kt",
            "KeyReady.holdTileIfEscalating(" to "ShellChannelGate.kt",
            "KeyReady.prepare(" to "EarlyShellChannel.kt",
            "EarlyShellChannel.homeVisible(" to "ShellChannelGate.kt",
            "A11yLifecycleHeal.moXePending(" to "KeyReady.kt",
            "LocalShellRetry.USER_READ_CAP" to "NavConnect.kt",
        ).forEach { (call, file) ->
            assertTrue(all.any { it.first == file && it.second.contains(call) }, "'$call' phải được gọi trong $file")
        }
    }

    // ── R-nf7 · chữ song ngữ ───────────────────────────────────────────────────────────────────────────────

    @Test
    fun `chu moi du hai ban, ban EN khong con dau tieng Viet`() {
        val vi = SourceRoots.text("src/main/res/values/strings_kachi.xml")
        val en = SourceRoots.text("src/main/res/values-en/strings_kachi.xml")
        val keys = listOf(
            "kachi_access_title", "kachi_access_never_body", "kachi_access_lost_title", "kachi_access_lost_body",
            "kachi_access_env_title", "kachi_access_env_body", "kachi_access_ask_again", "kachi_access_later",
            "kachi_slot_connecting", "kachi_slot_needs_access", "kachi_slot_no_channel", "kachi_access_feature_blocked",
        )
        keys.forEach { k ->
            assertTrue(vi.contains("name=\"$k\""), "thiếu $k ở values/")
            val m = Regex("<string name=\"$k\">(.*?)</string>", RegexOption.DOT_MATCHES_ALL).find(en)
            assertTrue(m != null, "thiếu $k ở values-en/")
            assertEquals(emptyList<Char>(), m!!.groupValues[1].filter { it in VI_MARKS }.toList(), "$k bản EN còn dấu")
        }
        assertFalse(vi.contains("kachi_slot_tap_to_open"), "chữ 'Chạm để mở' không còn chỗ dùng sau R1.3")
    }

    /**
     * Mọi tệp mã đã bỏ chú thích bằng bộ quét DÙNG CHUNG [KotlinSource.stripComments] (giữ nguyên string literal). Review
     * lượt 3 [P3]: bản tự chép cắt mỗi dòng ở `//` đầu tiên ⇒ chuỗi `"https://…"` nuốt phần mã phía sau trên cùng dòng ⇒
     * bài quét toàn cây (một chủ của dấu bền, call site §8) im lặng thôi gác.
     */
    private fun mainFiles(): List<Pair<String, String>> = SourceRoots.moduleSourceRoots().flatMap { root ->
        Files.walk(root).use { s ->
            s.filter { it.toString().endsWith(".kt") }
                .map { it.fileName.toString() to KotlinSource.stripComments(it.toFile().readText()) }.toList()
        }
    }

    private companion object {
        const val VI_MARKS = "àáảãạăằắẳẵặâầấẩẫậèéẻẽẹêềếểễệìíỉĩịòóỏõọôồốổỗộơờớởỡợùúủũụưừứửữựỳýỷỹỵđ"
    }
}
