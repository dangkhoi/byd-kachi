package com.byd.clusternav.launcher.voice

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ FIX286 · R-VK — phím vô-lăng gán Kachi nghe: bài canh NỐI DÂY (quét source, SourceRoots.body) ═══════════════════
 *
 * Phần THUẦN đã có bài thật ở `:core` (VoiceWakeModeTest · VoiceWakeStandDownTest · ModelHolderTest · VoiceWakePrefsTest
 * · WakeSessionJournalTest). Ở đây khoá các mắt xích Android mà JVM không dựng được (Service, Activity, prefs):
 *  VK1 — một luật "mô hình ở đâu" cho MỌI lối vào (phím · mic màn · nạp sẵn · bật/tắt FGS);
 *  VK2 — HOLD: `:wake` giữ mô hình cho phím, không mic, không bộ nghe câu gọi; sync khi bảng gán phím/hồ sơ đổi;
 *  VK3 — đứng xuống/huỷ không bao giờ chờ khoá mô hình trên luồng chính;
 *  VK4 — `:wake` đọc prefs tươi từ ảnh chụp, mọi setter của các khoá ấy công bố ảnh chụp;
 *  VK5 — tấm chữ "Đang nạp giọng nói…" khi mô hình chưa nằm sẵn (vi/en);
 *  VK6 — nhật ký phiên `:wake` (pending → started → ready → closed) + cầu `wakelog` chỉ đọc.
 */
class VoiceKeyHoldFix286WiringContractTest {

    private fun code(rel: String) = SourceRoots.codeOf(rel)
    private val base = "src/main/java/com/byd/clusternav"
    private val service by lazy { code("$base/launcher/voice/VoiceWakeService.kt") }
    private val hold by lazy { code("$base/launcher/voice/VoiceWakeHold.kt") }
    private val main by lazy { code("$base/launcher/voice/VoiceWakePrefsMain.kt") }
    private val engine by lazy { code("$base/launcher/voice/VoiceRecognizer.kt") }
    private val entry by lazy { code("$base/launcher/voice/VoiceEntry.kt") }
    private val session by lazy { code("$base/launcher/voice/VoiceSession.kt") }
    private val listen by lazy { code("$base/launcher/voice/VoiceSessionListen.kt") }
    private val factory by lazy { code("$base/launcher/voice/VoiceWakeSessionFactory.kt") }
    private val wiring by lazy { code("$base/launcher/voice/VoiceWiring.kt") }
    private val journal by lazy { code("$base/launcher/voice/WakeSessionLog.kt") }
    private val prefs by lazy { code("$base/Prefs.kt") }
    private val prefsV3 by lazy { code("$base/PrefsVoiceV3.kt") }
    private val keys by lazy { code("$base/launcher/ClusterNavBridgeKeys.kt") }
    private val reapply by lazy { code("$base/launcher/ClusterNavBridgeReapply.kt") }
    private val assistant by lazy { code("$base/modules/voicekey/AssistantLauncher.kt") }

    // ══ VK1 — một luật cho mọi lối vào ════════════════════════════════════════════════════════════════════════

    @Test
    fun `VK1 - nut mic man, nap san va bat tat FGS deu hoi VoiceWakePrefsMain mode, khong con rieng wakeEnabled`() {
        val m = SourceRoots.body(main, "fun mode(ctx: Context): VoiceWakeMode")
        assertTrue(m.contains("VoiceWakeMode.of(Prefs.wakeEnabled(ctx), keyHold(ctx))"), "chế độ = wake HIỆU LỰC ∨ phím gán Kachi nghe")
        assertTrue(SourceRoots.body(main, "fun keyHold(ctx: Context): Boolean").contains("Prefs.VK_TARGET_KACHI_VOICE"))
        assertTrue(entry.contains("VoiceEntryRoute.decide(modelInWake(), wakeAlive())"), "nút mic màn đi theo modelInWake")
        assertTrue(entry.contains("VoiceWakePrefsMain.mode(ctx).modelInWake"))
        assertFalse(entry.contains("Prefs.wakeEnabled"), "VoiceEntry không được quyết route bằng riêng wake (gốc lỗi 2.62–2.85)")
        val pre = SourceRoots.body(engine, "fun preload(ctx: Context, delayMs: Long = PRELOAD_DELAY_MS, inWake: Boolean = false)")
        assertTrue(pre.contains("VoicePreloadPolicy.shouldPreloadInMain(mode.modelInWake)"), "nạp sẵn ở chính hỏi chế độ thật")
        assertFalse(pre.contains("Prefs.wakeEnabled"), "nạp sẵn không còn hỏi riêng wake")
        val sync = SourceRoots.body(service, "fun sync(ctx: Context, reloadModel: Boolean = false)")
        assertTrue(sync.contains("VoiceWakePrefsMain.mode(ctx).modelInWake"), "FGS bật khi mô hình ở `:wake` (WAKE ∨ HOLD)")
        assertFalse(sync.contains("Prefs.wakeEnabled"))
    }

    @Test
    fun `VK1 - phim van di wake va mang loi vao KEY`() {
        val fn = SourceRoots.body(assistant, "private fun launchKachiVoice(ctx: Context): Boolean")
        assertTrue(fn.contains("VoiceWakeService.listenNow(app, WakeSessionJournal.Entry.KEY)"), "phím = lối vào KEY của nhật ký")
    }

    // ══ VK2 — HOLD ════════════════════════════════════════════════════════════════════════════════════════════

    @Test
    fun `VK2 - onStartCommand che do doc TUOI, HOLD khong bo nghe va giu mo hinh, WAKE giu nguyen duong cu`() {
        val cmd = SourceRoots.body(service, "override fun onStartCommand(")
        assertTrue(cmd.contains("val mode = mode()"), "chế độ đọc một lần mỗi lượt start")
        val off = cmd.indexOf("if (mode == VoiceWakeMode.OFF) { stopListening(); stopSelf(); return START_NOT_STICKY }")
        val holdAt = cmd.indexOf("if (mode == VoiceWakeMode.HOLD) { stopListening(); VoiceWakeHold.holdModel(this); return START_STICKY }")
        val ensure = cmd.indexOf("ensureListener()")
        assertTrue(off in 0 until holdAt, "OFF đứng xuống TRƯỚC")
        assertTrue(holdAt in 0 until ensure, "HOLD trả về TRƯỚC khi dựng bộ nghe câu gọi (không micro)")
        assertTrue(cmd.contains("setListening(screenOn() && !handoffActive())"), "đường WAKE không đổi (§6)")
        val modeFn = SourceRoots.body(service, "private fun mode(): VoiceWakeMode")
        assertTrue(modeFn.contains("VoiceWakeHold.modeInWake(this)"), "`:wake` quyết chế độ từ ảnh chụp, không từ cache prefs của nó")
        assertTrue(service.contains("private fun enabled(): Boolean = mode() == VoiceWakeMode.WAKE"), "bộ nghe câu gọi chỉ ở WAKE")
        val hm = SourceRoots.body(hold, "fun holdModel(ctx: Context)")
        assertTrue(hm.contains("VoiceEngine.preload(ctx, inWake = true)"), "HOLD nạp sẵn ở `:wake`, chỉ cổng RAM")
    }

    @Test
    fun `VK2 - man sang tat va cau chi khong giet HOLD`() {
        val screen = SourceRoots.body(service, "private fun onScreen(on: Boolean)")
        val holdRet = screen.indexOf("if (m == VoiceWakeMode.HOLD) return")
        assertTrue(holdRet >= 0 && holdRet < screen.indexOf("ensureListener()"), "HOLD: SCREEN_ON/OFF không dựng bộ nghe, không stopSelf")
        val fuse = SourceRoots.body(service, "private fun autoDisable()")
        assertTrue(fuse.contains("if (mode() == VoiceWakeMode.HOLD)"), "cầu chì chỉ tắt wake; còn phím ⇒ ở lại HOLD")
    }

    @Test
    fun `VK2 - doi bang gan phim, cong tac nhan nut va doi ho so deu sync wake`() {
        listOf(
            "fun ClusterNavBridge.setVoiceKeyEnabled(on: Boolean, onDone: (Boolean) -> Unit = {})",
            "fun ClusterNavBridge.addBinding(keyCode: Int, targetSpec: String): String?",
            "fun ClusterNavBridge.removeBinding(keyCode: Int)",
        ).forEach { sig -> assertTrue(SourceRoots.body(keys, sig).contains("VoiceWakeService.sync(app)"), "`$sig` phải sync `:wake`") }
        assertTrue(SourceRoots.body(reapply, "internal fun ClusterNavBridge.reapplyAll()").contains("step(\"voice.wake\") { VoiceWakeService.sync(app) }"),
            "bảng gán phím theo HỒ SƠ ⇒ đổi hồ sơ phải sync")
        val sync = SourceRoots.body(service, "fun sync(ctx: Context, reloadModel: Boolean = false)")
        val handOver = sync.indexOf("val handOver = isProcessAlive(ctx)")
        assertTrue(handOver in 0 until sync.indexOf("ctx.stopService(i)"), "đo `:wake` còn sống TRƯỚC khi dừng nó (sự thật, không cờ RAM)")
        assertTrue(sync.contains("if (handOver && !VoiceEngine.loaded()) VoiceEngine.preload(ctx)"),
            "HOLD/WAKE → OFF (gỡ phím cuối / tắt công tắc) ⇒ mô hình về lại tiến trình chính, nạp sẵn như 2.85")
        val pre = SourceRoots.body(engine, "fun preload(ctx: Context, delayMs: Long = PRELOAD_DELAY_MS, inWake: Boolean = false)")
        assertTrue(pre.contains("if (mode == VoiceWakeMode.HOLD) VoiceWakeService.sync(app)"),
            "chính bỏ nạp sẵn vì HOLD ⇒ phải bảo `:wake` giữ (tiến trình chính vừa được dựng lại, không ai sync)")
    }

    /**
     * [Senior review FIX286 Pass 2 · P2] chiều OFF → HOLD/WAKE giữa đời tiến trình: bản đã nạp sẵn ở chính phải được TRẢ
     * (VK1 "một bản cho cả máy"), chỉ sau khi `:wake` đã lên, không bao giờ chờ khoá, không bao giờ dưới chân một phiên.
     * Luật thuần ở `VoicePreloadPolicyTest.tra ban cua tien trinh chinh …`; đây khoá các mắt xích nối.
     */
    @Test
    fun `VK1 - OFF sang HOLD hoac WAKE giua doi tien trinh thi chinh tra ban mo hinh, khong chan, khong duoi chan phien`() {
        val sync = SourceRoots.body(service, "fun sync(ctx: Context, reloadModel: Boolean = false)")
        val startAt = sync.indexOf("ctx.startForegroundService(i)")
        val handAt = sync.indexOf("if (started) VoiceWakePrefsMain.handOverToWake(ctx)")
        assertTrue(handAt > startAt && startAt >= 0, "trả bản SAU khi bật `:wake`, và chỉ khi bật được (không lên ⇒ giữ bản ở chính)")
        assertTrue(handAt < sync.indexOf("} else {"), "chỉ ở nhánh mô hình ở `:wake`")
        val hand = SourceRoots.body(main, "fun handOverToWake(ctx: Context)")
        assertTrue(hand.contains("VoiceEngine.tryRelease {"), "nhả KHÔNG CHẶN")
        assertFalse(hand.contains("VoiceEngine.release()"), "release() chờ cả lượt nạp/giải mã")
        assertTrue(hand.contains("VoicePreloadPolicy.shouldHandOverToWake("), "luật thuần ở :core (đã test)")
        assertTrue(hand.contains("mode(app).modelInWake") && hand.contains("sessionRunning = VoiceSession.anyRunning()"),
            "điều kiện đọc lại DƯỚI khoá dựng: chế độ thật + không phiên nào đang chạy")
        assertTrue(hand.contains("handingOver.compareAndSet(false, true)") && hand.contains("Thread({"), "một lượt, luồng nền")
        // [Senior review FIX286 Pass 3 · P2] cổng vào tính cả ĐANG NẠP, và bận/bỏ thì HỎI LẠI trên chính luồng nền ấy (luật
        // thuần `VoicePreloadPolicyTest.tra ban khong xong thi hoi lai …`) — không trông vào một `onResume` có thể không tới.
        assertTrue(hand.contains("if (!(VoiceEngine.loaded() || VoiceEngine.loading())"), "lượt nạp đang chạy cũng là bản sắp nằm lại")
        val tryAt = hand.indexOf("VoiceEngine.tryRelease {")
        val retryAt = hand.indexOf("VoicePreloadPolicy.shouldRetryHandOver(r,")
        val sleepAt = hand.indexOf("Thread.sleep(VoiceWakeStandDown.POLL_MS)")
        assertTrue(hand.contains("while (true)") && tryAt in 0 until retryAt && retryAt < sleepAt,
            "thử → hỏi luật → ngủ một nhịp → thử lại (không một lần rồi thôi)")
        assertTrue(hand.contains("catch (e: InterruptedException)"), "`Thread.sleep` ném checked — không bắt là sập tiến trình chính")
        val start = SourceRoots.body(session, "fun start(")
        assertTrue(start.indexOf("live.incrementAndGet()") > start.indexOf("running.compareAndSet(false, true)"), "chỉ phiên đã giành `running` mới được đếm")
        assertTrue(SourceRoots.body(session, "internal fun close(): Unit = post {").contains("if (running.getAndSet(false)) live.decrementAndGet()"),
            "đếm lùi đúng một lần mỗi phiên")
        // [P3] `:wake` HOLD: lượt nạp sẵn ngủ 3 s rồi mới nạp — chế độ phải được hỏi lại sau giấc ngủ ấy.
        val pre = SourceRoots.body(engine, "fun preload(ctx: Context, delayMs: Long = PRELOAD_DELAY_MS, inWake: Boolean = false)")
        val recheck = pre.indexOf("if (inWake && !runCatching { VoiceWakeHold.modeInWake(app).modelInWake }")
        assertTrue(recheck > pre.indexOf("Thread.sleep(delayMs)") && recheck < pre.indexOf("recognizer(app)"),
            "HOLD đã hết trong lúc ngủ chờ ⇒ không nạp 74 MB vào một `:wake` không còn service")
    }

    // ══ VK3 — không chờ khoá mô hình trên luồng chính ═════════════════════════════════════════════════════════

    @Test
    fun `VK3 - khong mot tep wake nao goi release co cho, onDestroy cung khong chan`() {
        listOf("VoiceWakeService.kt" to service, "VoiceWakeHold.kt" to hold, "WakeSessionLog.kt" to journal).forEach { (n, src) ->
            assertFalse(src.contains("VoiceEngine.release()"), "$n gọi release() CÓ CHỜ — chờ cả lượt nạp 9–34 s trên luồng chính")
        }
        val d = SourceRoots.body(service, "override fun onDestroy() {")
        assertTrue(d.contains("VoiceWakeHold.releaseModel(VoiceWakeSessions.epoch())"), "onDestroy nhả không chặn")
        assertTrue(d.contains("scheduleStandDown()"), "bận lúc onDestroy ⇒ để nhịp đứng xuống nhả khi xong (không bỏ 74 MB lại)")
    }

    // ══ VK4 — prefs tươi ═════════════════════════════════════════════════════════════════════════════════════

    @Test
    fun `VK4 - moi setter cua khoa wake doc deu cong bo anh chup`() {
        listOf(
            prefs to "fun setVoiceNavDefaultApp(ctx: Context, key: String)",
            prefs to "fun setVoiceMusicDefaultApp(ctx: Context, key: String)",
            prefs to "fun setWakeEnabled(ctx: Context, on: Boolean)",
            prefs to "fun setVoiceKeyEnabled(ctx: Context, v: Boolean)",
            prefs to "fun addVoiceKeyBinding(ctx: Context, keyCode: Int, targetSpec: String): String?",
            prefs to "fun removeVoiceKeyBinding(ctx: Context, keyCode: Int)",
            prefsV3 to "fun Prefs.setVoiceConfirmIds(ctx: Context, ids: Set<String>)",
            prefsV3 to "fun Prefs.resetVoiceConfirmIds(ctx: Context)",
        ).forEach { (src, sig) ->
            val b = SourceRoots.body(src, sig)
            val apply = maxOf(b.lastIndexOf(".apply()"), b.lastIndexOf("VoiceKeyBinding(sp(ctx)"))
            val pub = b.lastIndexOf("VoiceWakePrefsMain.publish(ctx)")
            assertTrue(pub >= 0, "`$sig` không công bố ảnh chụp ⇒ `:wake` giữ giá trị cũ (VOICE-WAKE-PREFS-STALE)")
            assertTrue(pub > apply, "`$sig`: công bố SAU khi ghi, không thì chụp giá trị cũ")
        }
        val sync = SourceRoots.body(service, "fun sync(ctx: Context, reloadModel: Boolean = false)")
        assertTrue(sync.indexOf("VoiceWakePrefsMain.publish(ctx)") in 0 until sync.indexOf("startForegroundService(i)"),
            "sync công bố TRƯỚC khi bật `:wake` — nó quyết chế độ từ ảnh chụp")
        // [Senior review FIX286 Pass 1 · P3] từ 2.86 có người ghi ở luồng nền (preload → sync → publish) ⇒ ĐỌC prefs phải
        // nằm trong khoá so–ghi, nếu không luồng đọc state cũ có thể ghi đè ảnh chụp mới.
        val storeWrite = SourceRoots.body(code("$base/launcher/voice/VoiceGrammarSnapshotStore.kt"), "fun write(prefs: WorkspacePrefs)")
        val lockAt = storeWrite.indexOf("synchronized(lock)")
        assertTrue(lockAt >= 0 && lockAt < storeWrite.indexOf("VoiceWakePrefsMain.collect(ctx)") &&
            lockAt < storeWrite.indexOf("prefs.profiles()"), "đọc prefs + collect nằm TRONG khoá so–ghi")
        val collect = SourceRoots.body(main, "fun collect(ctx: Context): VoiceWakePrefs")
        listOf("wakeSwitch =", "keyHold =", "confirmIds =", "navDefault =", "musicDefault =").forEach { assertTrue(collect.contains(it), it) }
    }

    @Test
    fun `VK4 - dispatcher cua wake doc tap hoi va app mac dinh tu anh chup, man chinh khong doi`() {
        val build = SourceRoots.body(factory, "internal fun VoiceWakeService.buildSession(): VoiceSession {")
        assertTrue(build.contains("fresh = { VoiceWakeHold.prefs(app) }"), "`:wake` truyền nguồn tươi")
        assertTrue(wiring.contains("confirmIds = { fresh?.invoke()?.confirmIds ?: runCatching { Prefs.voiceConfirmIds(ctx) }"))
        // [Senior review FIX286 Pass 1 · P3] đọc prefs ném ⇒ MẶC ĐỊNH 2.86 (mở nóc hỏi), không phải tập rỗng (không hỏi gì).
        assertTrue(wiring.contains("Prefs.voiceConfirmIds(ctx) }.getOrDefault(VoiceRiskTable.defaultIds())"),
            "lỗi đọc prefs chỉ được nghiêng về phía hỏi thêm")
        assertFalse(wiring.contains("Prefs.voiceConfirmIds(ctx) }.getOrDefault(emptySet())"))
        assertTrue(wiring.contains("navDefault = { fresh?.invoke()?.navDefault ?:"))
        assertTrue(wiring.contains("musicDefault = { (fresh?.invoke()?.musicDefault ?:"))
        assertTrue(wiring.contains("fresh: (() -> VoiceWakePrefs)? = null"), "mặc định null ⇒ màn chính y nguyên")
    }

    /**
     * Ràng buộc tiến trình `:wake` (cùng lẽ `VoiceWakeIsolationContractTest`) áp cho HAI tệp mới chạy ở `:wake`: không DI
     * của launcher, không ghi `clusternav_prefs`, không READER tự ghi, không ghi ảnh chụp (chỉ tiến trình chính ghi).
     */
    @Test
    fun `VK4 - hai tep moi chay o wake khong cham DI, khong ghi prefs, khong ghi anh chup`() {
        listOf("VoiceWakeHold.kt" to hold, "WakeSessionLog.kt" to journal).forEach { (n, src) ->
            listOf("AppContainer", "ShellTransport", "WorkspacePrefs", "SharedPreferences", "Prefs.voiceKeyBindings",
                "VoiceGrammarSnapshotStore.write", "VoiceWakePrefsMain").forEach { banned ->
                assertFalse(src.contains(banned), "$n chạm `$banned` — tệp này chạy ở `:wake`")
            }
            assertEquals(emptySet<String>(), Regex("""Prefs\.set[A-Za-z0-9_]*""").findAll(src).map { it.value }.toSet(), "$n ghi prefs")
        }
    }

    // ══ VK5 — tấm chữ đang nạp ═══════════════════════════════════════════════════════════════════════════════

    @Test
    fun `VK5 - tam chu noi dang nap khi mo hinh chua nam san, co du vi va en`() {
        val start = SourceRoots.body(session, "fun start(")
        assertTrue(start.contains("if (VoiceEngine.loaded()) R.string.kachi_voice_preparing else R.string.kachi_voice_loading_model"))
        val vi = SourceRoots.text("src/main/res/values/strings_kachi.xml")
        val en = SourceRoots.text("src/main/res/values-en/strings_kachi.xml")
        listOf("kachi_voice_loading_model", "kachi_wake_hold_notif_title", "kachi_wake_hold_notif_text").forEach {
            assertTrue(vi.contains("name=\"$it\"") && en.contains("name=\"$it\""), "$it phải có cả vi lẫn en")
        }
    }

    // ══ VK6 — nhật ký phiên ═══════════════════════════════════════════════════════════════════════════════════

    @Test
    fun `VK6 - phieu o service, moc o phien, ghi qua DiagRingFile tren lan nen`() {
        val cmd = SourceRoots.body(service, "override fun onStartCommand(")
        assertTrue(cmd.indexOf("WakeSessionLog.pending(") in 0 until cmd.indexOf("VoiceWakeSessions.preempt"), "phiếu đặt TRƯỚC khi phiên bắt đầu")
        assertTrue(SourceRoots.body(service, "private fun fireWake()").contains("WakeSessionLog.pending(WakeSessionJournal.Entry.WAKE_WORD"))
        val start = SourceRoots.body(session, "fun start(")
        assertTrue(start.indexOf("marks?.started()") > start.indexOf("running.compareAndSet(false, true)"), "chỉ phiên THẬT SỰ bắt đầu mới nhận phiếu")
        val close = SourceRoots.body(session, "internal fun close(): Unit = post {")
        assertTrue(close.indexOf("marks?.closed(cancelled.get())") > close.indexOf("go(VoiceTurnPhase.IDLE)"), "đóng hẳn rồi mới ghi")
        assertTrue(listen.indexOf("marks?.ready()") > listen.indexOf("sẵn sàng nghe sau"), "mốc micro mở = ngay sau dòng 'sẵn sàng nghe'")
        assertTrue(SourceRoots.body(factory, "internal fun VoiceWakeService.buildSession(): VoiceSession {").contains("marks = WakeSessionLog.Marks(app)"))
        val flush = SourceRoots.body(journal, "private fun flush(app: Context, r: Rec, outcome: Outcome)")
        assertTrue(flush.indexOf("Log.i(TAG, full)") in 0 until flush.indexOf("MacroExec.submitSerial(LANE)"), "logcat TRƯỚC, tệp sau, trên làn nền")
        assertTrue(flush.contains("WakeSessionJournal.line("), "định dạng ở :core (đã test)")
        assertTrue(journal.contains("DiagRingFile(NAME, WakeSessionJournal.MAX_LINES, TAG)"), "một lớp tệp vòng cho mọi nhật ký bền (DRY)")
    }

    @Test
    fun `VK6 - cau wakelog di cua khong-can-man-chinh va CHI DOC`() {
        val noHome = code("$base/launcher/testbridge/TestBridgeNoHome.kt")
        assertTrue(SourceRoots.body(noHome, "fun handle(").contains("TestBridgeCommands.WAKELOG -> TestBridgeWakeLog.run(app, cmd, reply)"))
        val run = SourceRoots.body(code("$base/launcher/testbridge/TestBridgeWakeLog.kt"), "fun run(app: Context, cmd: TestBridgeCommand, reply: TestBridgeReply)")
        assertTrue(run.contains("WakeSessionLog.read(app)"))
        listOf("append(", "appendIf(", "writeText(", "delete(").forEach { assertFalse(run.contains(it), "wakelog CHỈ ĐỌC — thấy `$it`") }
    }

    @Test
    fun `cac tep cua luot nay khong vuot tran 500 dong`() {
        listOf(
            "$base/launcher/voice/VoiceWakeService.kt", "$base/launcher/voice/VoiceRecognizer.kt", "$base/launcher/voice/VoiceSession.kt",
            "$base/launcher/voice/VoiceWakeHold.kt", "$base/launcher/voice/WakeSessionLog.kt", "$base/launcher/voice/VoiceWakePrefsMain.kt",
            "$base/Prefs.kt", "$base/PrefsVoiceV3.kt", "$base/launcher/ClusterNavBridgeKeys.kt",
        ).forEach { rel -> val n = SourceRoots.text(rel).lines().size; assertTrue(n <= 500, "$rel dài $n dòng — trần là 500") }
    }
}
