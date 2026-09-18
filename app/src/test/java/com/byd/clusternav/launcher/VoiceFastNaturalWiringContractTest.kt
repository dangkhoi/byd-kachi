package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ V3 (1.66) *"nhanh + tự nhiên"* — DÂY NỐI của những thứ MỚI ══════════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-voice-fast-natural.html`. Quét SOURCE vì dự án không dựng Activity/View trong JVM thuần
 * (không Robolectric) — cùng lệ [VoiceCommandWiringContractTest], và mọi phép cắt vùng đi qua [SourceRoots.body]
 * (nó **nổ** nếu mốc không còn, thay vì âm thầm quét tới hết tệp).
 *
 * ## Bài thật ở đây
 * Đúng bệnh `CastShell.evictVd` (CLAUDE.md §8): 1.66 thêm **bảy** cơ chế mới (`VoiceEndpointer` · `VoiceClarify` ·
 * nạp sẵn mô hình · hội thoại · bind theo tên hằng · đổi tên hồ sơ · đo lại ô khi inset đổi). Mỗi cái compile sạch
 * được mà **không ai gọi**, và trên xe thì cái đó nhìn y hệt *"tính năng không chạy"*.
 */
class VoiceFastNaturalWiringContractTest {

    private fun code(relative: String): String = SourceRoots.codeOf(relative)

    /**
     * Tệp ở GỐC repo (vd `scripts/…`) — [SourceRoots] chỉ biết cây source của module, còn `:app:test` chạy với
     * cwd = thư mục module. Cùng khuôn `TestBridgeSafetyContractTest.repoText`, và **nổ** nếu không thấy tệp.
     */
    private fun repoText(rel: String): String {
        val tries = listOf(rel, "../$rel", "../../$rel").map(java.nio.file.Paths::get)
        val hit = tries.firstOrNull { java.nio.file.Files.exists(it) }
            ?: error("không tìm thấy $rel; đã thử: ${tries.joinToString()}")
        return hit.toFile().readText()
    }

    private val capture by lazy { code("src/main/java/com/byd/clusternav/launcher/voice/VoiceCapture.kt") }
    private val recognizer by lazy { code("src/main/java/com/byd/clusternav/launcher/voice/VoiceRecognizer.kt") }
    private val session by lazy { code("src/main/java/com/byd/clusternav/launcher/voice/VoiceSession.kt") }
    private val turns by lazy { code("src/main/java/com/byd/clusternav/launcher/voice/VoiceSessionTurns.kt") }
    private val app by lazy { code("src/main/java/com/byd/clusternav/KachiApplication.kt") }
    private val workspace by lazy { code("src/main/java/com/byd/clusternav/launcher/WorkspaceView.kt") }
    private val host by lazy { code("src/main/java/com/byd/clusternav/launcher/VdAppHost.kt") }
    private val gateway by lazy { code("src/main/java/com/byd/clusternav/launcher/BydHalGateway.kt") }
    private val bridge by lazy { code("src/main/java/com/byd/clusternav/launcher/testbridge/KachiTestBridge.kt") }
    private val wiring by lazy { code("src/main/java/com/byd/clusternav/launcher/voice/VoiceWiring.kt") }

    // ══ R1/R2/R3 — NHANH ═════════════════════════════════════════════════════════════════════════════════

    @Test
    fun `nguon micro doc tu core va tu pref, khong con hang viet cung`() {
        assertTrue(capture.contains("VoiceMicSource.order(pref)"), "thứ tự nguồn phải lấy từ `:core`, có bài canh")
        assertTrue(capture.contains("Prefs.voiceMicSource(ctx)"), "phải đọc lựa chọn của người dùng")
        // Hằng cũ viết cứng phải biến mất — không thì bản vá này chỉ thêm một đường thứ hai.
        assertTrue(
            !capture.contains("MediaRecorder.AudioSource.VOICE_RECOGNITION"),
            "bảng nguồn viết cứng cũ phải rời khỏi tầng micro",
        )
    }

    @Test
    fun `bo ngat cau duoc NOI vao vong nghe, khong phai mot lop mo coi`() {
        // ⚠ 1.69: thân vòng nghe dời từ `listen(` sang `listenGranted(`. Không phải một lượt dọn dẹp — `listen(`
        // nay chỉ còn làm một việc: **xin chốt một-micro** ([VoiceSingleFlight]) rồi nhả trong `finally`, vì
        // [ĐO xe 2026-09-16] có 4 `AudioRecord` mở trong 300 ms. Tách ra để chốt không thể bị một đường thoát
        // sớm nào nhảy qua. Bài này vì thế phải soi đúng thân mới, nếu không nó xanh trên một hàm rỗng.
        // ⚠ 1.69 vòng hai (Silero VAD): vòng nghe không còn gọi thẳng `VoiceEndpointer` nữa mà gọi
        // `VoiceTurnEndpoint` — MỘT bề mặt phủ cả hai đường (VAD chính · RMS lùi). Đó là chủ ý: để đường lùi đi
        // qua **đúng** các lời gọi của đường chính thay vì mục rữa trong một nhánh `else` không ai chạy. Bài này
        // vì thế soi lời gọi mới, và vẫn ghim đủ hai tính chất cũ: **từng khối** được đưa vào, và vòng nghe
        // **dừng** khi bộ ngắt chốt.
        val fn = SourceRoots.body(capture, "private fun listenGranted(")
        assertTrue(
            fn.contains("ep.accept(buf, n, rmsChunk"),
            "phải đưa TỪNG KHỐI (cả mẫu thô cho VAD lẫn rms cho đường lùi) vào bộ ngắt",
        )
        assertTrue(
            fn.contains("if (stop)") && fn.contains("break"),
            "và phải DỪNG vòng nghe khi bộ ngắt chốt",
        )
        val turn = code("src/main/java/com/byd/clusternav/launcher/voice/VoiceTurnEndpoint.kt")
        assertTrue(
            turn.contains("VoiceEndpointer.Phase.ENDED"),
            "đường LÙI vẫn phải đọc `Phase.ENDED` của `VoiceEndpointer` — bỏ nó là bỏ luôn đường lùi",
        )
        // Trần cứng của phiên vẫn còn: một cabin ồn liên tục không bao giờ cho ENDED (CLAUDE.md §3).
        assertTrue(session.contains("MAX_LISTEN_MS = 8_000L"), "trần cứng của phiên KHÔNG được bỏ")
    }

    @Test
    fun `moc gio tung chang co mot tag duy nhat, va bit lo 3,1 giay duoc DO`() {
        assertTrue(recognizer.contains("const val TIMING_TAG = \"KachiVoiceTiming\""), "một tag cho mọi mốc giờ")
        listOf("mic mở sau", "nghe ", "giải mã ").forEach {
            assertTrue(capture.contains(it), "thiếu mốc giờ '$it' — lượt xe sau lại không chốt được gì")
        }
        // [ĐO xe 2026-09-16] lỗ 3,1 s nằm trong `finally` của `listen`; 1.66 đo TỪNG bước của nó.
        val close = SourceRoots.body(capture, "private fun closeRecord(")
        assertTrue(close.contains("stop ") && close.contains("release "), "phải đo riêng `stop` và `release`")
        assertTrue(close.contains("KachiMicTail"), "bíp cuối + nhả tiêu điểm phải rời khỏi đường về của câu trả lời")
    }

    @Test
    fun `nap san mo hinh chay tu Application, nen va uu tien thap`() {
        assertTrue(app.contains("VoiceEngine.preload(this)"), "nạp sẵn phải có chỗ gọi THẬT (CLAUDE.md §8)")
        val fn = SourceRoots.body(recognizer, "fun preload(")
        assertTrue(fn.contains("Thread.MIN_PRIORITY"), "nạp mô hình không được tranh CPU với lượt dựng màn chính")
        assertTrue(fn.contains("VoiceModelStore.isReady(app)"), "chưa tải mô hình thì im lặng rút lui")
    }

    @Test
    fun `so luong giai ma theo may, khong con hang 2`() {
        assertTrue(recognizer.contains("numThreads = threadsForDecode()"), "số luồng phải tính theo máy")
        assertTrue(recognizer.contains("availableProcessors"), "và phải đọc số lõi thật")
    }

    // ══ R7/R8/R9 — TỰ NHIÊN ══════════════════════════════════════════════════════════════════════════════

    @Test
    fun `tap ma hoi-lai duoc DOC LAI o moi ve, khong chup mot lan`() {
        assertTrue(wiring.contains("confirmIds = {"), "phải truyền dạng lambda — người dùng vừa tích một ô là câu sau đã theo")
        assertTrue(wiring.contains("Prefs.voiceConfirmIds(ctx)"), "và đọc từ đúng khoá prefs")
        val dispatcher = code("src/main/java/com/byd/clusternav/launcher/VoiceDispatcher.kt")
        assertTrue(
            dispatcher.contains("VoiceRiskTable.of(intent, confirmIds())"),
            "cổng hỏi-lại phải hỏi tập ĐANG BẬT, không phải bảng cứng cũ",
        )
    }

    @Test
    fun `hoi lai duoc goi TRUOC khi thi hanh, va co tran luot`() {
        val fn = SourceRoots.body(session, "internal fun execute(")
        assertTrue(fn.contains("clarifyAsk(intents)"), "câu không hiểu phải rẽ sang đường hỏi lại")
        assertTrue(fn.indexOf("clarifyAsk(intents)") < fn.indexOf("d.execute(intents)"), "hỏi TRƯỚC khi thi hành")
        assertTrue(turns.contains("VoiceClarify.ask(only, clarifyRound)"), "trần lượt hỏi do `:core` giữ")
        assertTrue(turns.contains("VoiceClarify.combine("), "câu trả lời phải được GHÉP với ngữ cảnh")
        assertTrue(turns.contains("VoiceClarify.giveUp()"), "hết lượt thì nói một câu có ích, không im")
    }

    @Test
    fun `hoi thoai cho DOC XONG moi mo mic lai, va co bon cong`() {
        val fn = SourceRoots.body(session, "internal fun execute(")
        // 1.70 — fix "overlay tắt giữa câu": execute gọi onReplyDone ở mốc ĐỌC XONG (onDone), onReplyDone mới
        // nán overlay + mở hội thoại. Micro chỉ mở lại SAU mốc đọc xong (mở sớm là Kachi nghe chính mình).
        assertTrue(fn.contains("speakLines(batch) { post { onReplyDone(my, pending) } }"),
            "micro chỉ mở lại SAU mốc 'đọc xong' — qua onReplyDone (onDone của speakLines)")
        // [ĐO xe 2026-09-18 §B] Lưới an toàn phải theo ĐỘ DÀI CÂU. Hằng 10 s cũ đóng tấm chữ giữa lúc Piper còn
        // đang đọc (load 14) — tức chính lưới an toàn thành thủ phạm của lỗi nó sinh ra để phòng.
        assertTrue(fn.contains("VoiceSpeakBudget.estimateMs(batch, SPEAK_SAFETY_MS)"),
            "hẹn đóng phải ước lượng theo độ dài batch, không phải một hằng số cho mọi câu")
        assertFalse(fn.contains("scheduleClose(SPEAK_SAFETY_MS)"),
            "hằng trần trơn đã quay lại — câu dài sẽ bị cắt giữa chừng như 1.78")
        val done = SourceRoots.body(turns, "internal fun VoiceSession.onReplyDone(")
        assertTrue(done.contains("followUp(my, pending)"), "onReplyDone (sau đọc xong) mới mở hội thoại")
        assertTrue(fn.contains("VoiceFeedbackPhrase.isInterim("), "lệnh còn đang tra mạng ⇒ KHÔNG mở hội thoại")
        val f = SourceRoots.body(turns, "internal fun VoiceSession.followUp(")
        assertTrue(f.contains("confirmOpen.get()"), "đang có hộp xác nhận ⇒ micro đã có chủ, không mở thêm")
        assertTrue(f.contains("VoiceSession.MAX_FOLLOW_UPS"), "phải có trần số lượt nối trong một phiên")
        assertTrue(f.contains("beep = false"), "lượt nối KHÔNG kêu bíp — xem KDoc")
        assertTrue(f.contains("Prefs.voiceFollowUpMs"), "quãng giữ mic đọc từ prefs, không viết cứng")
    }

    // ══ R11 — HAL theo TÊN HẰNG ══════════════════════════════════════════════════════════════════════════

    @Test
    fun `gateway that noi day hai phep tra moi, va cau co lenh featmap`() {
        assertTrue(gateway.contains("BydFeatureIds.idByName("), "tra tên hằng phải đi qua gateway thật")
        assertTrue(gateway.contains("BydFeatureIds.deviceFqnForFeature("), "và tra device theo bảng của framework")
        assertTrue(bridge.contains("TestBridgeFeatMap.run(app, reply)"), "lệnh `featmap` phải có chỗ gọi thật")
        val hal = code("src/main/java/com/byd/clusternav/launcher/testbridge/TestBridgeHal.kt")
        assertTrue(hal.contains("OP_SETEV"), "đầu dò `setev` (khoá/cốp) phải tồn tại cho lượt xe sau")
        assertTrue(hal.contains("cmd.autoConfirm"), "và nó vẫn đi qua cổng CONFIRM như mọi lượt GHI")
    }

    // ══ R13/R15 — hồ sơ · ô ══════════════════════════════════════════════════════════════════════════════

    @Test
    fun `doi ten ho so di het bon tang, va co phep kiem o core`() {
        val repo = code("src/main/java/com/byd/clusternav/launcher/PrefsWorkspaceRepository.kt")
        val vm = code("src/main/java/com/byd/clusternav/launcher/HomeViewModel.kt")
        val profiles = code("src/main/java/com/byd/clusternav/launcher/SettingsSectionsProfiles.kt")
        assertTrue(profiles.contains("deps.onRenameProfile("), "màn Cài đặt phải có đường gọi")
        assertTrue(profiles.contains("ProfileRename.plan("), "và phải NÓI RA khi bị từ chối, không im lặng")
        assertTrue(vm.contains("repository.renameProfile("), "ViewModel chỉ uỷ quyền")
        assertTrue(repo.contains("prefs.renameProfile("), "nơi lưu bền làm phép DỜI khoá")
        val ext = code("src/main/java/com/byd/clusternav/launcher/WorkspacePrefsProfile.kt")
        assertTrue(ext.contains("WorkspacePrefs.K_ACTIVE") && ext.contains("WorkspacePrefs.K_BOOT_PROFILE"),
            "ba con trỏ theo tên phải đi theo — quên một cái là một con trỏ treo, im lặng")
        assertEquals(1, Regex("\\bfun WorkspacePrefs\\.renameProfile\\(").findAll(ext).count())
    }

    @Test
    fun `o do lai khi inset doi, va di qua DUNG mot duong resize`() {
        assertTrue(workspace.contains("override fun onApplyWindowInsets("), "phải nghe được lúc thanh ROM ẩn/hiện")
        assertTrue(workspace.contains("pushSlotSize(i, rect.width, rect.height)"), "và đẩy cỡ mới xuống từng ô")
        assertTrue(workspace.contains("VdAppHost)?.resize("), "đi qua ĐÚNG hàm resize có đòn bẩy mật độ")
        val fn = SourceRoots.body(host, "fun resize(")
        assertTrue(fn.contains("if (w == dispW && h == dispH) return"),
            "trùng cỡ ⇒ không làm gì; nếu không thì mỗi lượt bố trí lại đẩy một lượt đổi cấu hình vào app trong ô")
        assertTrue(fn.contains("slotDensity(w, h)"), "đổi cỡ phải tính lại mật độ — quên là chữ to gấp rưỡi ô bên cạnh")
    }

    // ══ [SOÁT Pass 1 — 2026-09-16 · Opus] bốn chỗ vá của lượt soát ═══════════════════════════════════════

    /**
     * ⚠⚠ [P1] Bệnh: [VoiceSession] sống theo **tiến trình** (một phiên cho cả launcher), còn `followUps` chỉ
     * TĂNG và `clarifyRound` chỉ về 0 ở đường một câu được hiểu. Không đặt lại ở [VoiceSession.start] thì:
     * một phiên dùng hết 5 lượt hội thoại ⇒ **mọi phiên sau tới hết chuyến** không giữ micro nữa; một phiên
     * chết ở lượt hỏi thứ 2 ⇒ không phiên nào hỏi lại nữa. Cả hai tắt **im lặng** — đúng họ lỗi mà
     * `CastShell.evictVd` để lại (CLAUDE.md §8): mã còn nguyên, chỉ là không bao giờ chạy.
     */
    @Test
    fun `hai bo dem cua V3 ve 0 o dau moi phien`() {
        val fn = SourceRoots.body(session, "fun start(")
        assertTrue(fn.contains("clarifyRound = 0"), "lượt hỏi lại phải về 0 mỗi phiên")
        assertTrue(fn.contains("followUps = 0"), "số lượt hội thoại phải về 0 mỗi phiên")
    }

    /**
     * [P2] `speakLines` bỏ qua lượt đọc ở vài ca (không có giọng · công tắc tắt) và khi đó `onDone` bắn
     * **ngay**, đồng bộ. `confirmOpen` chỉ bắt ca *"hộp xác nhận sắp mở"*; ca *"micro của lượt trước còn
     * đang mở"* phải do chính cờ micro bắt, nếu không có hai `AudioRecord` cùng sống.
     */
    @Test
    fun `hoi thoai khong mo them mic khi mic dang mo`() {
        val f = SourceRoots.body(turns, "internal fun VoiceSession.followUp(")
        assertTrue(f.contains("micOpen()"), "phải hỏi cờ micro THẬT, không chỉ cờ hộp xác nhận")
        assertTrue(session.contains("internal fun micOpen(): Boolean = capturing.get()"), "và cờ đó chỉ ĐỌC được")
    }

    /**
     * [P2] `VoiceClarify.ask` trả `null` ở hai ca khác hẳn nhau (*"hỏi cũng không giúp gì"* · *"đã hỏi đủ 2
     * lượt"*). Bản 1.66 đầu gộp cả hai về đường `VoiceReply.unknown` ⇒ [VoiceClarify.giveUp] — câu nêu một ví
     * dụ có thật, đúng thứ spec R8 hứa — chỉ chạy khi người ta **im lặng**, không bao giờ chạy khi hết trần.
     */
    @Test
    fun `het tran hoi thi noi cau bo cuoc, khong roi ve cau khong hieu`() {
        val fn = SourceRoots.body(session, "internal fun execute(")
        assertTrue(fn.contains("clarifyGaveUp(intents, my)"), "hết trần phải rẽ sang câu bỏ cuộc")
        assertTrue(
            fn.indexOf("clarifyGaveUp(intents, my)") < fn.indexOf("d.execute(intents)"),
            "và rẽ TRƯỚC khi thi hành",
        )
        val g = SourceRoots.body(turns, "internal fun VoiceSession.clarifyGaveUp(")
        assertTrue(g.contains("VoiceClarify.MAX_ROUNDS"), "trần vẫn do `:core` giữ, không chép một bản thứ hai")
        assertTrue(g.contains("VoiceClarify.ask(only, 0)"), "phân biệt hai ca `null` bằng chính luật của `:core`")
        assertTrue(g.contains("clarifyRound = 0"), "bỏ cuộc rồi thì lượt sau được hỏi lại từ đầu")
        // [ĐO xe 2026-09-18 §B] Đường này cũng ĐỌC một câu ⇒ cũng phải có lưới theo độ dài câu. Bản 1.78 hẹn đóng
        // bằng LINGER_MS (2,5 s) TRƯỚC lượt đọc, mà câu bỏ cuộc dài ~40 ký tự ⇒ tấm chữ đi trước khi loa nói hết.
        assertTrue(
            g.contains("VoiceSpeakBudget.estimateMs(listOf(line), VoiceSession.SPEAK_SAFETY_MS)"),
            "câu bỏ cuộc cũng phải được lưới theo độ dài, không phải LINGER_MS đặt trước lượt đọc",
        )
        assertTrue(
            g.contains("speakLines(listOf(line)) { post { if (!stale(my)) scheduleClose(VoiceSession.LINGER_MS) } }"),
            "và mốc ĐỌC XONG mới rút về LINGER_MS — kèm chốt thế hệ để không rút tấm chữ của phiên khác",
        )
    }

    /**
     * [P2] Từ 1.66 mặc định là *"không hỏi gì cả"*, nên một ca `confirm=1` của E2E chỉ có nghĩa khi harness
     * **bật được** mã của nó. Không có đường ghi prefs thì 8 ca ấy phải hạ về 0 — tức lớp canh E2E của cổng an
     * toàn quan trọng nhất biến mất trong cùng lượt đổi mặc định.
     *
     * Cổng của chính lệnh ghi: danh sách trắng ở `:core`, kiểm ngay ở **tầng phân tích** (receiver là
     * `exported=true` — xem KDoc `KachiTestBridge`).
     */
    @Test
    fun `cau kiem thu ghi duoc pref, nhung chi trong danh sach trang`() {
        assertTrue(bridge.contains("TestBridgePrefsSet.run(app, cmd, KachiTestHooks.get(), reply)"),
            "lệnh `prefs_set` phải có chỗ gọi THẬT (CLAUDE.md §8)")
        val exec = code("src/main/java/com/byd/clusternav/launcher/testbridge/TestBridgePrefsSet.kt")
        assertTrue(exec.contains("Prefs.setVoiceConfirmIds("), "phải ghi qua ĐÚNG hàm mà màn Cài đặt ghi")
        assertTrue(exec.contains("h.setTopStripLabels(on)"),
            "nhãn chip theo HỒ SƠ ⇒ phải đi qua màn chính, không ghi thẳng prefs dưới chân màn hình")
        assertTrue(exec.contains("readBack("), "lời đáp phải nói giá trị THẬT sau lượt ghi, không phải giá trị vừa nhận")
        // Bộ ca E2E phải THẬT SỰ dùng cột mới — không thì lệnh có mà lớp canh vẫn trống.
        val cases = repoText("scripts/emulator/voice-cases.tsv")
        assertTrue(cases.contains("voice_confirm_ids=control:door"), "ca `mở khoá cửa` phải tự bật mã của nó")
        assertEquals(
            8,
            cases.lines().count { it.startsWith("t") && it.contains("\tvoice_confirm_ids=") },
            "đủ 8 ca confirm đã được trả lại (t08 t09 t13 t26 t51 t55 t57 t62)",
        )
    }
}
