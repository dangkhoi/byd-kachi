package com.byd.clusternav.launcher.voice

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ 2.91 VOICE-APP-NAMES — BÀI NỐI DÂY (quét nguồn) ═══════════════════════════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-290-voice-app-names.html` §6 V-unit (`:app`) + CLAUDE.md §8 (hàm mới phải CÓ chỗ gọi). Mỗi bài
 * khoá một dây mà test `:core` không thấy được: tên đã dạy tới CẢ hai tiến trình · phiên lệnh và lượt dạy dùng CÙNG bộ
 * nhận dạng · lượt dạy không tấm chữ/không đọc/không thi hành · mọi hàm ghi cập nhật ảnh chụp · ba lối vào nối thật.
 */
class VoiceAppNamesWiringContractTest {

    private fun code(rel: String) = SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/$rel")

    @Test
    fun `ten da day toi ca tien trinh chinh lan wake qua mot nguon`() {
        val src = code("voice/VoiceTaughtSource.kt")
        val names = SourceRoots.body(src, "fun names(")
        assertTrue("VoiceGrammarSnapshotStore.isMainProcess(ctx)" in names, "phải rẽ theo TIẾN TRÌNH thật")
        assertTrue("WorkspacePrefs(ctx).voiceAppNames()" in names, "tiến trình chính đọc prefs")
        assertTrue("VoiceGrammarSnapshotStore.read(ctx).aliases" in names, "`:wake` đọc ảnh chụp (cache prefs của nó không nạp lại)")
        val wiring = code("voice/VoiceWiring.kt")
        assertTrue("fun appsByLabel(ctx: Context): Map<String, String> = appIndex(ctx).keys" in wiring,
            "mọi bề mặt đọc bảng gọi app qua appIndex (nhãn thật › tên đã dạy › nhãn phụ + dạng đọc)")
        assertTrue("VoiceAppIndex.build(picked.labels, installed, taught, alt)" in wiring && "val alt = AppAltLabels.cached(installed)" in wiring)
        assertTrue("appAliases = { keys -> aliases(ctx, keys) }" in wiring, "dispatcher nhận tên đã dạy của ĐÚNG bảng vừa đọc")
        val factory = code("voice/VoiceWakeSessionFactory.kt")
        assertTrue("VoiceWiring.dispatcher(" in factory && "VoiceWiring.appsByLabel(app)" in factory,
            "`:wake` dựng dispatcher bằng CÙNG VoiceWiring ⇒ tên đã dạy (ảnh chụp) tới phím vô-lăng")
    }

    @Test
    fun `parser va cau hoi lai nhan ten da day`() {
        val d = code("VoiceDispatcher.kt")
        assertTrue("aliases = appAliases(labels)" in SourceRoots.body(d, "private fun parse("), "cửa DUY NHẤT vào parser mang tên đã dạy")
        assertTrue("VoiceWiring.aliases(ctx, labels)" in code("voice/VoiceSessionTerms.kt"), "từ vựng của phiên (hỏi lại) mang tên đã dạy")
        assertTrue("aliases = VoiceWiring.aliases(context, appMap)" in code("SettingsVoiceSection.kt"),
            "danh sách Câu lệnh nói được dùng CÙNG bốn nguồn động với parser")
    }

    @Test
    fun `luot day dung cung bo nhan dang voi phien lenh va khong tam chu khong doc khong thi hanh`() {
        val listen = code("voice/VoiceSessionListen.kt")
        assertTrue("val rec = openCommandRecognizer()" in listen, "lượt chính dựng recognizer qua hàm chung")
        assertTrue("commandRecognizer(ctx, profiles(), appsByLabel(), places())" in code("voice/VoiceCommandHearing.kt"))
        val teach = code("voice/VoiceTeachSession.kt")
        val run = SourceRoots.body(teach, "private fun run(")
        assertTrue("commandRecognizer(ctx, profiles(), appsByLabel(), places())" in run, "lượt dạy: CÙNG recognizer + hotword")
        assertTrue("VoiceSession.MAX_LISTEN_MS" in run && "keepPcm = true" in run && "openTurn = true" in run,
            "lượt dạy nghe bằng CÙNG tham số lượt chính")
        assertTrue("VoiceFreeTail.decode(ctx, heard)" in run)
        listOf("VoiceOverlay(", "speak", "execute(", "openFree(", "AudioRecord(").forEach { banned ->
            assertFalse(banned in teach, "lượt dạy không được có `$banned` (spec §4.4 Pass 1)")
        }
        assertTrue("TestBridgeStore.isOn(ctx)" in SourceRoots.body(teach, "private fun logIfTestMode("),
            "OQ13 — lượt dạy chỉ vào nhật ký H2 khi test-mode bật")
        assertTrue("VoiceSession.anyRunning()" in run, "đang có phiên lệnh ⇒ từ chối lượt dạy")
    }

    @Test
    fun `luot day o wake co han im lang dat lai theo tin va huy phia wake khi bo cho`() {
        // Senior review 2.91 Pass 1 (P2): bản đầu đặt trần CỐ ĐỊNH 25 s cho cả lượt ở `:wake` — `:wake` vừa dựng lạnh còn nạp
        // mô hình 9–34 s ⇒ hộp báo lỗi trong khi `:wake` vẫn chạy tiếp và MỞ MICRO không ai chờ.
        val relay = code("voice/VoiceTeachRelay.kt")
        assertFalse("WAKE_CAP_MS" in relay, "không còn trần cố định cho cả lượt")
        val onEvent = SourceRoots.body(relay, "private fun onEvent(")
        assertTrue("arm(p, if (state == VoiceTeachSession.State.LOADING_MODEL) WAKE_LOADING_MS else WAKE_IDLE_MS)" in onEvent,
            "mỗi tin của `:wake` đặt lại hạn; đang nạp mô hình ⇒ hạn dài")
        val silent = SourceRoots.body(relay, "private fun onSilent(")
        assertTrue("sendCancel(p)" in silent && "finish(p)" in silent, "hết hạn ⇒ bảo `:wake` huỷ rồi mới trả lỗi")
        assertTrue("ui.removeCallbacks(p.watchdog)" in SourceRoots.body(relay, "private fun finish("), "xong ⇒ gỡ hạn")
    }

    /**
     * 2.93 VOICE-TEACH-CONTEXT (R2) + VOICE-TEACH-SHORT-HOMOGRAPH (R7) — hộp dạy nói ĐÚNG câu cho lượt kế (lượt câu có ô) và
     * hiện cảnh báo đồng hình (senior review VOICE Pass 1 (c): hai dây này chưa có ghim). Thử ĐỎ: bỏ nhánh SLOT trong `paintPrompt`.
     */
    @Test
    fun `hop day noi cau goi y cho luot ke va hien canh bao dong hinh`() {
        val dlg = code("SettingsVoiceNamesDialog.kt")
        val paint = SourceRoots.body(dlg, "private fun paintPrompt(")
        assertTrue("TeachSample.promptFor(next) == TeachSample.Prompt.SLOT" in paint, "lượt kế là lượt câu có ô ⇒ câu gợi ý riêng")
        assertTrue("R.string.kachi_vn_take_slot" in paint && "R.string.kachi_vn_take_plain" in paint)
        assertTrue(Regex("""\bpaintPrompt\(\)""").findAll(dlg).count() >= 2, "gọi ngay khi số lượt đổi (cả đường điền sẵn)")
        assertTrue("TeachGuard.Code.HOMOGRAPH -> context.getString(R.string.kachi_vn_r_homograph, r.detail)" in dlg,
            "cảnh báo đồng hình phải hiện câu ví dụ cho người lái")
    }

    @Test
    fun `hop day gop luot theo chuoi chuan hoa va ghi tren du lieu doc lai`() {
        val dlg = code("SettingsVoiceNamesDialog.kt")
        // Quyết định điều phối 2.91 (OQ4): tên giọng 3 chữ cái cần ≥ 2 lượt ra CÙNG chuỗi chuẩn hoá ⇒ gộp theo norm + phán lại.
        val repeat = SourceRoots.body(dlg, "private fun repeatOf(")
        assertTrue("it.norm == norm" in repeat && "TeachGuard.check(page.teachContext(), pkg, s.accented, s.source, takes)" in repeat)
        // Senior review Pass 1 (P3): ghi = gộp vào danh sách ĐỌC LẠI trên luồng vẽ, chỉ khi hồ sơ còn là hồ sơ lúc mở hộp.
        val store = SourceRoots.body(dlg, "private fun store(")
        assertTrue("var cur = port.names()" in store && "port.save(cur)" in store, "không ghi đè bằng bản chụp cũ")
        assertTrue("deps.state().activeProfile != profile" in store, "đổi hồ sơ giữa chừng ⇒ không lưu vào hồ sơ khác")
        assertTrue("TeachGuard.check(ctx, cmd.pkg, it.accented, source, takes)" in code("testbridge/TestBridgeTeach.kt"),
            "cầu kiểm thử đếm lượt CÙNG thước với hộp dạy")
    }

    @Test
    fun `luot day chay o tien trinh giu mo hinh`() {
        val relay = code("voice/VoiceTeachRelay.kt")
        assertTrue("VoiceEntryRoute.decide(modelInWake(), wakeAlive())" in relay, "cùng phép chọn tiến trình với VoiceEntry (R-nf1)")
        assertTrue("ContextCompat.RECEIVER_NOT_EXPORTED" in relay && "setPackage(app.packageName)" in relay,
            "chữ người dùng nói không rời gói")
        assertTrue("if (!mode.modelInWake)" in relay, "`:wake` không giữ mô hình ⇒ không ack ⇒ chính tự làm")
        assertTrue("VoiceTeachRelay.onWakeStart(this, intent, mode)" in code("voice/VoiceWakeService.kt"))
    }

    @Test
    fun `moi ham ghi cap nhat anh chup va anh chup mang ten da day`() {
        val prefs = code("WorkspacePrefsVoiceNames.kt")
        val set = SourceRoots.body(prefs, "fun WorkspacePrefs.setVoiceAppNames(")
        assertTrue("VoiceGrammarSnapshotStore.write(this)" in set, "ghi tên ⇒ ảnh chụp `:wake` đổi theo")
        assertTrue("readOnly" in set, "phiên bản lạ ⇒ không ghi đè")
        assertTrue("aliases = runCatching { prefs.voiceAppNames() }" in code("voice/VoiceGrammarSnapshotStore.kt"))
        assertTrue("repository.setVoiceAppNames(names)" in code("HomeViewModel.kt"), "UI ghi qua ViewModel")
        assertTrue("viewModel.voiceNamesPort()" in code("KachiHomeWiring.kt"))
    }

    @Test
    fun `hotword phien lenh mang ten da day nguon giong`() {
        val rec = code("voice/VoiceRecognizer.kt")
        // ĐỔI GHIM có lý do (2.93 soát giọng Pass 2 · P3 a): tên đã dạy đọc MỘT lần mỗi phiên (đúng tiến trình) rồi dùng
        // chung cho tên bias lẫn lọc nhãn — tính chất cũ (nguồn là VoiceTaughtSource, chọn bằng hàm thuần :core) giữ nguyên.
        assertTrue("val names = VoiceTaughtSource.names(ctx)" in rec)
        assertTrue("VoiceTaughtSource.forHotwords(apps, installed, names)" in rec)
        // Senior review Pass 1 (P3) — chọn tên bias là hàm THUẦN ở :core (cùng luật sống của từ vựng, có test hành vi).
        assertTrue("VoiceAppIndex.hotwordNames(apps, installed, names)" in code("voice/VoiceTaughtSource.kt"))
        val pick = SourceRoots.body(
            SourceRoots.codeOf("src/main/java/com/byd/clusternav/launcher/voice/VoiceAppIndex.kt"), "fun hotwordNames(",
        )
        assertTrue("TaughtSource.SPEECH" in pick, "OQ3 — tên gõ không vào hotword")
    }

    @Test
    fun `ba loi vao noi that`() {
        assertTrue("SettingsVoiceNamesPage(context, deps).open()" in code("SettingsVoiceSection.kt"), "(a) Cài đặt › Giọng nói")
        val drawer = code("AppDrawer.kt")
        assertTrue("onLongPressApp = if (mode == Mode.OPEN_APP) AppDrawerApps.teachMenu(context) { onClose() } else null" in drawer,
            "(b) nhấn giữ CHỈ ở ngăn kéo thường (OQ6)")
        assertTrue("VoiceTeachHome.open(context" in code("AppDrawerApps.kt"))
        assertTrue("armTeachHint(intents, heard)" in code("voice/VoiceSession.kt"), "(c) nút trên tấm chữ sau câu mở không hiểu")
        assertTrue("VoiceHomeAction.TEACH_APP -> { teachApp(VoiceTeachHint.decode(arg)); DONE }" in code("voice/VoiceEntry.kt"))
        val panels = SourceRoots.body(code("HomePanels.kt"), "fun openSettings(")
        assertTrue("VoiceTeachPending.take()" in panels && "SettingsVoiceNamesPage(activity" in panels,
            "TEACH_APP ⇒ nhóm Giọng nói + mở trang")
    }

    @Test
    fun `cau kiem thu co ba lenh day`() {
        assertTrue("in TestBridgeTeachCommands.NAMES -> TestBridgeTeach.run(app, cmd, hooks, reply)" in code("testbridge/KachiTestBridge.kt"))
        val t = code("testbridge/TestBridgeTeach.kt")
        assertTrue("TestBridgeWav.stage(app, cmd.path)" in t && "VoiceWavProbe.run(" in t, "teach đi ĐÚNG đường giải mã của `wav`")
        assertTrue("TeachGuard.regression(ctx, name)" in t && "port.save(" in t, "save qua máy dò hồi quy + cổng ViewModel")
    }
}
