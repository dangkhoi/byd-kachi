package com.byd.clusternav.launcher.voice

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ spec `kachi-i18n-zh-th-ms.html` R4 · R6 · R9 (T2) — DÂY NỐI của *"giọng nói chỉ hiểu tiếng Việt"* ══════════════
 *
 * Phần THUẦN (câu nói theo `lang` tường minh, ảnh chụp mang `uiLang`) đã khoá ở `:core` (`VoiceSpokenLangTest`,
 * `VoiceGrammarSnapshotLangTest`). Bài này khoá phần mà chỉ ĐỌC NGUỒN mới thấy — dự án không chạy Robolectric, và
 * mỗi điểm dưới đây là một chỗ mà một lượt sửa sau có thể **gỡ im lặng** (compile vẫn xanh, chỉ chiếc xe biết):
 *  1. mọi lời gọi dựng câu NÓI trên đường nói đều truyền ngôn ngữ — một lời gọi rơi về mặc định `Strings.current` là
 *     giao diện tiếng Trung đưa chữ Hán cho giọng Piper tiếng Việt;
 *  2. giọng đọc + geocoder hỏi theo `LangHost.voiceLocale(…)` (giao diện zh/th/ms ⇒ vi), và tiếng ấy chảy từ PHIÊN;
 *  3. `:wake` lấy tiếng từ ảnh chụp ngữ pháp — không ghi `Strings.current`, không gọi `langMode()` (tự ghi khi migrate);
 *     thông báo + toast của nó theo tiếng người dùng, không theo locale máy;
 *  4. tiến trình chính ghi tiếng giao diện ĐÃ GIẢI NGHĨA vào ảnh chụp, kể cả lúc đổi tiếng;
 *  5. dòng *"chỉ hiểu tiếng Việt"* có ở cả ba chỗ R4 (đầu nhóm Giọng nói · đầu danh sách câu lệnh · ô gõ thử).
 *
 * Quét qua `SourceRoots.codeOf` (đã bỏ chú thích) — một nhắc-tới trong KDoc không thoả hợp đồng.
 */
class VoiceLangWiringContractTest {

    private fun code(rel: String): String = SourceRoots.codeOf(rel)
    private val l = "src/main/java/com/byd/clusternav/launcher/"

    // ══ (1) Mọi câu NÓI trên đường nói mang ngôn ngữ ═════════════════════════════════════════════════════════

    /** Tệp của đường nói — nơi câu trả lời được dựng rồi hiện/đọc cho người lái. */
    private val voicePath = listOf(
        "VoiceDispatcher.kt", "VoiceTargetDispatch.kt", "VoiceControlDispatch.kt", "VoiceReadback.kt",
        "VoiceTextConsole.kt", "voice/VoiceSession.kt", "voice/VoiceSessionTurns.kt", "voice/VoiceSessionListen.kt",
        "testbridge/KachiTestBridge.kt",
    )

    /** Hàm dựng câu có tham số ngôn ngữ (mặc định = tiếng MÀN — đúng cho Cài đặt, SAI cho đường nói). */
    private val builder = Regex(
        """\b(VoiceReply\.(?!uncontrollable\b)\w+|VoiceClarify\.(?:ask|giveUp|vague)|VoiceFeedbackPhrase\.merge|""" +
            """VoiceRiskTable\.reason|TelemetryReadout\.of|VoiceFeatureGone\.reply|VoicePlaces\.displayLabel|""" +
            """ProfileNames\.display|\w+\.notice)\(""",
    )

    /** Đối số mang ngôn ngữ của PHIÊN/cầu: `lang` · `l` (chụp một lần mỗi câu) · `voiceLang()` · `….voice`. */
    private val langArg = Regex("""\b(lang|l|voiceLang)\b|\bvoiceLang\(\)|Strings\.current\.voice\b""")

    @Test
    fun `moi loi goi dung cau noi tren duong noi deu truyen ngon ngu`() {
        val missing = ArrayList<String>()
        var seen = 0
        voicePath.forEach { rel ->
            val src = code(l + rel)
            builder.findAll(src).forEach { m ->
                seen++
                val args = argsAt(src, m.range.last)
                if (!langArg.containsMatchIn(args)) missing += "$rel: ${m.value}$args)"
            }
        }
        assertTrue(seen >= 60, "chỉ thấy $seen lời gọi — bộ quét đang đọc vùng SAI (bài canh giả)")
        assertEquals(
            emptyList<String>(), missing,
            "lời gọi dựng câu NÓI rơi về mặc định Strings.current (tiếng MÀN) ⇒ giao diện zh/th/ms đưa chữ Hán/Thái cho giọng Việt",
        )
    }

    /** Thân đối số của lời gọi có ngoặc mở ở [open] (đếm ngoặc, bỏ qua chuỗi) — không gồm ngoặc đóng. */
    private fun argsAt(src: String, open: Int): String {
        var depth = 0
        var i = open
        var inStr = false
        while (i < src.length) {
            val c = src[i]
            if (inStr) {
                if (c == '\\') i++ else if (c == '"') inStr = false
            } else when (c) {
                '"' -> inStr = true
                '(' -> depth++
                ')' -> { depth--; if (depth == 0) return src.substring(open + 1, i) }
            }
            i++
        }
        error("không đóng được ngoặc tại $open")
    }

    @Test
    fun `cau truc tieng cua cau va phien la MOT nguon`() {
        val wiring = code(l + "voice/VoiceWiring.kt")
        assertTrue(Regex("""\blang: Lang,\s*\)\s*:\s*VoiceDispatcher""").containsMatchIn(wiring), "VoiceWiring.dispatcher phải nhận `lang` KHÔNG mặc định")
        val fn = SourceRoots.body(wiring, "fun dispatcher(")
        assertTrue(fn.contains("lang = lang,"), "…và đưa nó vào VoiceDispatcher")
        assertTrue(fn.contains("VoiceGeocoder.resolveBounded(ctx, place, lang)"), "geocoder theo tiếng giọng nói của lượt")
        val dispatcher = code(l + "VoiceDispatcher.kt")
        assertTrue(dispatcher.contains("lang = { lang },"), "nút xe (VoiceControlDispatch) phải nói cùng tiếng của cầu")
        assertTrue(dispatcher.contains("lang = lang,"), "app đích (VoiceTargetDispatch) phải nói cùng tiếng của cầu")
        assertTrue(dispatcher.contains("TelemetryReadout.of(i.datumId, car, lang)"), "đọc số liệu: chữ giá trị theo tiếng giọng nói")
        val home = SourceRoots.body(code(l + "KachiHomeWiring.kt"), "internal fun Activity.voiceSession(")
        assertTrue(home.contains("lang = session.voiceLang()"), "màn chính: dispatcher lấy tiếng của CHÍNH phiên")
        val turns = code(l + "voice/VoiceSessionTurns.kt")
        assertTrue(turns.contains("VoiceFeedbackPhrase.merge(lines, voiceLang())"), "gộp câu đọc cùng tiếng với dòng (lỗi 'Đã đã')")
        val listen = SourceRoots.body(code(l + "voice/VoiceSessionListen.kt"), "internal fun VoiceSession.runListen(")
        assertTrue(listen.contains("speakLines(listOf(VoiceReply.nothingHeard(voiceLang())))"), "câu 'không nghe rõ' ĐỌC theo tiếng giọng nói")
        assertFalse(listen.contains("speakLines(listOf(ctx.getString("), "không đọc chuỗi tài nguyên (tiếng màn) bằng giọng nói")
    }

    // ══ (2) Giọng đọc + geocoder theo `voiceLocale`, nguồn tiếng là PHIÊN ══════════════════════════════════════

    @Test
    fun `giong doc va geocoder hoi theo voiceLocale, khong theo locale cua man`() {
        val tts = code(l + "voice/AndroidTtsSpeaker.kt")
        // [soát 2.87 · voice P2] Locale nay ĐỌC LẠI cho TỪNG câu (lambda của `TtsVoiceLang`, `:core`) — phiên `:wake` dùng
        // lại phải theo tiếng mới — không còn tính một lần ở `configure` (lúc onInit). Pin dời theo; hành vi khoá ở
        // `TtsVoiceLangTest` (:core), dây nối ở bài `may doc he thong theo tieng o moi cua…` dưới.
        assertTrue(
            tts.contains("TtsVoiceLang { LangHost.voiceLocale(runCatching { voiceLang() }.getOrDefault(Lang.VI)) }"),
            "giọng đọc phải hỏi theo tiếng GIỌNG NÓI của phiên, đọc lại mỗi lượt",
        )
        assertFalse(tts.contains("LangHost.locale()"), "giọng đọc không được hỏi theo tiếng MÀN (zh/th/ms ⇒ giọng Trung/Thái cho câu Việt)")
        val geo = code(l + "voice/VoiceGeocoder.kt")
        assertTrue(geo.contains("Geocoder(ctx, LangHost.voiceLocale(voiceLang))"), "geocoder theo tiếng giọng nói")
        assertFalse(geo.contains("LangHost.locale()"), "geocoder không theo tiếng màn")
        assertTrue(code(l + "voice/VoiceSpeakerRouter.kt").contains("AndroidTtsSpeaker(ctx, voiceLang)"), "bộ chọn đường đọc chuyền tiếng xuống máy đọc")
        assertTrue(
            code(l + "voice/VoiceSession.kt").contains("VoiceSpeakerRouter(ctx, preferOffline = { Prefs.voicePreferOffline(ctx) }, voiceLang = voiceLang)"),
            "phiên chuyền CHÍNH lambda tiếng của nó cho máy đọc (`:wake` ⇒ tiếng từ ảnh chụp, không phải Strings.current)",
        )
        val host = SourceRoots.body(code(l + "LangHost.kt"), "fun voiceLocale(")
        assertTrue(host.contains("localeOf(lang.voice)"), "voiceLocale = locale của voiceLangOf(…) — EN⇒EN, còn lại⇒VI")
    }

    // ══ (3) `:wake` — tiếng từ ảnh chụp, không ghi toàn cục, không langMode() ════════════════════════════════

    private val wakeFiles = listOf(
        "VoiceWakeService.kt", "VoiceWakeSessionFactory.kt", "VoiceWakeHold.kt", "VoiceWakeHomeRelay.kt",
        "VoiceWakeSessions.kt", "VoiceWakeListener.kt", "VoiceWakeKws.kt",
    )

    @Test
    fun `wake lay tieng tu anh chup va dua cung mot lambda cho phien va dispatcher`() {
        val build = SourceRoots.body(code(l + "voice/VoiceWakeSessionFactory.kt"), "internal fun VoiceWakeService.buildSession(): VoiceSession {")
        assertTrue(build.contains("val voiceLang = { grammar().voiceLang }"), "`:wake` đọc tiếng từ ảnh chụp, MỖI lần (không cache)")
        assertTrue(build.contains("lang = voiceLang()"), "dispatcher của `:wake` phải nhận tiếng từ ảnh chụp")
        assertTrue(build.contains("voiceLang = voiceLang"), "phiên của `:wake` (gộp câu · hỏi lại · máy đọc) dùng CÙNG lambda")
        wakeFiles.forEach { f ->
            val src = code(l + "voice/" + f)
            assertFalse(Regex("""Strings\.current\s*=(?!=)""").containsMatchIn(src), "$f ghi Strings.current — chỉ LangHost.wrap được ghi")
            assertFalse(src.contains("langMode("), "$f gọi langMode() — hàm đọc ấy TỰ GHI khi migrate (clobber prefs từ cache cũ)")
        }
    }

    @Test
    fun `thong bao va toast cua wake theo tieng nguoi dung, khong theo locale may`() {
        val hold = code(l + "voice/VoiceWakeHold.kt")
        val res = SourceRoots.body(hold, "fun uiRes(ctx: Context): Context")
        assertTrue(res.contains("VoiceGrammarSnapshotStore.read(ctx).uiLang"), "tiếng lấy từ ảnh chụp")
        assertTrue(res.contains("LangHost.localized(ctx, it)"), "đặt locale bằng đúng một chỗ dựng cấu hình (LangHost)")
        val notif = SourceRoots.body(hold, "fun notification(ctx: Context, mode: VoiceWakeMode): Notification")
        assertTrue(notif.contains("val res = uiRes(ctx)") && notif.contains("res.getString("), "thông báo FGS đọc chữ qua uiRes")
        assertFalse(notif.contains("ctx.getString("), "thông báo FGS không được đọc chữ theo locale máy")
        val service = code(l + "voice/VoiceWakeService.kt")
        assertTrue(service.contains("VoiceWakeHold.uiRes(this).getString(R.string.kachi_wake_auto_off)"), "toast cầu chì theo tiếng người dùng")
    }

    // ══ (4) Tiến trình chính ghi tiếng ĐÃ GIẢI NGHĨA, cả lúc đổi tiếng ═══════════════════════════════════════

    @Test
    fun `tien trinh chinh ghi tieng giao dien vao anh chup, ca luc doi tieng`() {
        val store = code(l + "voice/VoiceGrammarSnapshotStore.kt")
        val write = SourceRoots.body(store, "fun write(prefs: WorkspacePrefs)")
        assertTrue(write.contains("uiLang = runCatching { LangHost.resolved(prefs) }"), "ảnh chụp phải mang tiếng đã giải nghĩa")
        assertTrue(write.indexOf("isMainProcess(ctx)") in 0 until write.indexOf("LangHost.resolved(prefs)"), "chỉ tiến trình chính đọc langMode()")
        val lang = SourceRoots.body(code(l + "WorkspacePrefsLang.kt"), "fun WorkspacePrefs.setLangMode(mode: LangMode)")
        val apply = lang.indexOf(".apply()")
        val snap = lang.indexOf("VoiceGrammarSnapshotStore.write(this)")
        assertTrue(apply >= 0 && snap > apply, "đổi tiếng phải chụp lại ảnh SAU apply() — không thì `:wake` giữ tiếng cũ")
        val host = code(l + "LangHost.kt")
        assertTrue(host.contains("private fun chosen(base: Context): Lang = resolved(WorkspacePrefs(base), base)"),
            "LangHost.wrap và ảnh chụp giải nghĩa bằng CÙNG một phép (không bản sao)")
    }

    // ══ (5) R4 — dòng "chỉ hiểu tiếng Việt" ở ba chỗ ══════════════════════════════════════════════════════════

    @Test
    fun `dong chi hieu tieng Viet co o ca ba cho`() {
        val section = code(l + "SettingsVoiceSection.kt")
        assertTrue(SourceRoots.body(section, "fun build(body: LinearLayout)").contains("R.string.kachi_voice_lang_only"), "đầu nhóm Giọng nói")
        assertTrue(SourceRoots.body(section, "private fun commandListRows(body: LinearLayout)").contains("R.string.kachi_voice_lang_only"),
            "đầu danh sách câu lệnh")
        assertTrue(SourceRoots.body(code(l + "VoiceTextConsole.kt"), "fun build(body: LinearLayout)").contains("R.string.kachi_voice_lang_only"),
            "ô gõ thử lệnh")
    }

    /**
     * [soát 2.87 · voice P2] MỌI cửa đọc/chọn của máy đọc hệ thống đi qua lượt theo tiếng ([TtsVoiceLang.sync]):
     * `available()` (cửa của `speak`) và `languageStatus()` (cửa của `VoiceSpeakerRouter.probe` — chọn Piper/Android).
     * Không còn chỗ đọc tiếng một lần: `voiceLang()` chỉ còn trong lambda của `TtsVoiceLang`.
     */
    @Test
    fun `may doc he thong theo tieng o moi cua doc va chon`() {
        val tts = code(l + "voice/AndroidTtsSpeaker.kt")
        assertTrue(SourceRoots.body(tts, "override fun available()").contains("follow("), "speak đi qua available() ⇒ theo tiếng")
        assertTrue(SourceRoots.body(tts, "fun languageStatus()").contains("follow("), "bộ chọn đọc số của tiếng HIỆN TẠI")
        assertTrue(SourceRoots.body(tts, "private fun follow(").contains("voice.sync("))
        assertEquals(1, Regex("""voiceLang\(\)""").findAll(tts).count(), "đọc tiếng ĐÚNG một chỗ: lambda đọc lại mỗi lượt")
        assertTrue(code(l + "voice/VoiceSpeakerRouter.kt").contains("androidLangStatus = android.languageStatus(),"),
            "bộ chọn đường đọc lấy số qua languageStatus() (đã theo tiếng)")
        // Soát vòng 2 [P3] — vòng khoá: `onInit` chạy khi luồng chính GIỮ `TextToSpeech.mStartLock` (AOSP r47 `:2220-2228`,
        // `:832-838`); mở [inited] trước `follow` thì một luồng phiên vào `voice.sync` (giữ khoá `TtsVoiceLang`) rồi chờ
        // `mStartLock`, còn luồng chính chờ khoá `TtsVoiceLang` ⇒ ANR. `follow` phải XONG trước khi mở cổng.
        val configure = SourceRoots.body(tts, "private fun configure(engine: TextToSpeech)")
        val follow = configure.indexOf("follow(engine)")
        val gate = configure.indexOf("inited.set(true)")
        assertTrue(follow in 0 until gate, "follow(engine) TRƯỚC inited.set(true): $configure")
        assertEquals(1, Regex("""inited\.set\(true\)""").findAll(tts).count(), "đúng MỘT chỗ mở cổng")
    }

    // ══ (6) Harness E2E giọng nói kiểm tiếng TRƯỚC khi so preview tiếng Việt (soát 2.87 · voice P3) ═══════════════

    /**
     * `voice-cases.tsv` mong preview tiếng Việt; `previewOf` dựng theo `Strings.current.voice`. Harness phải đọc CHÍNH
     * giá trị đó (cầu phơi `look.voice_lang` bằng CÙNG biểu thức) và dừng khi nó không phải `vi` — trước lượt chạy và
     * sau mỗi ca đổi hồ sơ (ngôn ngữ theo hồ sơ). Không có kiểm này thì Kachi để English ⇒ mọi ca FAIL "preview thiếu"
     * hàng loạt mà không chỉ ra gốc.
     */
    @Test
    fun `voice-e2e kiem tieng giong noi la tieng Viet truoc khi so preview`() {
        val bridge = code(l + "testbridge/KachiTestBridge.kt")
        assertTrue(bridge.contains("VoiceReply.preview(intent, com.byd.clusternav.launcher.Strings.current.voice)"), "preview theo tiếng giọng nói")
        assertTrue(code(l + "testbridge/TestBridgeState.kt").contains("\"voice_lang\" to Strings.current.voice.code"),
            "cầu phơi ĐÚNG tiếng mà preview dùng")
        val root = com.byd.clusternav.testsupport.I18nCallScanner.repoRoot()
        val sh = root.resolve("scripts/emulator/voice-e2e.sh").toFile().readText()
        // 2.93 DEBT-VOICE-COMMON-SH (đổi chốt có lý do): thân `require_voice_vi` dời NGUYÊN sang voice-common.sh (pure move) để
        // voice-audio-e2e.sh dùng CHUNG (VOICE-AUDIO-E2E-LANG). Chốt canh: một bản ở tệp chung · voice-e2e.sh source nó, không
        // còn bản riêng · hai harness gọi nó TRƯỚC khi đo.
        val common = root.resolve("scripts/emulator/voice-common.sh").toFile().readText()
        val fn = common.substringAfter("require_voice_vi() {").substringBefore("\n}\n")
        assertTrue(fn.contains("get look.voice_lang") && fn.contains("[ \"\$vl\" = \"vi\" ] || die"), "đọc look.voice_lang, khác vi ⇒ dừng")
        assertTrue(sh.contains(". \"\$HERE/voice-common.sh\"") && !sh.contains("require_voice_vi() {"), "voice-e2e.sh dùng bản chung, không bản riêng")
        val dispatch = sh.indexOf("case \"\$ONLY\" in")
        val pre = sh.indexOf("require_voice_vi \"trước lượt chạy\"")
        assertTrue(pre in 0 until dispatch, "kiểm TRƯỚC khi chạy T1/T2")
        assertTrue(sh.contains("profile:*) require_voice_vi "), "kiểm lại sau ca đổi hồ sơ")
        // Soát vòng 2 [P3] — câu dừng nêu ĐÚNG TÊN hồ sơ mang ngôn ngữ sai (đọc từ cùng bản state). Sau ca đổi hồ sơ, `cleanup`
        // đã trả máy về hồ sơ lúc bắt đầu ⇒ "hồ sơ đang dùng" chỉ QA sửa nhầm chỗ (CLAUDE.md §2: chẩn đoán sai địa chỉ).
        assertTrue(fn.contains("get profile.active") && fn.contains("where=\"cho hồ sơ «\${prof:-?}»\""), "câu dừng nêu tên hồ sơ")
        // Bản chung đọc ORIG_PROFILE qua biến cục bộ `orig` có mặc định rỗng (harness audio không đặt hồ sơ lúc bắt đầu, `set -u`).
        assertTrue(fn.contains("orig=\"\${ORIG_PROFILE:-}\"") && fn.contains("[ \"\$prof\" != \"\$orig\" ]") &&
            fn.contains("harness đã trả máy về"), "nói rõ hồ sơ đã được trả về")
        assertFalse(fn.contains("cho hồ sơ đang dùng"), "không còn câu chỉ nhầm hồ sơ")
    }

    /**
     * 2.93 VOICE-AUDIO-E2E-LANG — harness audio so `kind` nhưng đưa `preview` qua Piper tiếng Việt để đo `synth_ms`: Kachi để
     * English thì số synth đo trên câu tiếng Anh — sai đại lượng, im lặng. Chốt: harness audio source bản chung và gọi
     * `require_voice_vi` SAU khi bật cầu kiểm thử, TRƯỚC vòng đo đầu tiên.
     */
    @Test
    fun `voice-audio-e2e kiem tieng giong noi la tieng Viet truoc khi do`() {
        val sh = com.byd.clusternav.testsupport.I18nCallScanner.repoRoot().resolve("scripts/emulator/voice-audio-e2e.sh").toFile().readText()
        assertTrue(sh.contains(". \"\$HERE/voice-common.sh\""), "harness audio phải dùng bản kiểm chung")
        val on = sh.indexOf("\nenable_test_mode")
        val check = sh.indexOf("\nrequire_voice_vi \"")
        val loop = sh.indexOf("while IFS=")
        assertTrue(on in 0 until check, "kiểm ngôn ngữ cần cầu kiểm thử đã BẬT (đọc `state`)")
        assertTrue(check in 0 until loop, "kiểm TRƯỚC vòng đo đầu tiên")
        assertFalse(sh.contains("u0_a163"), "không uid ghi cứng — bật cầu qua `detect_data_mode`/`enable_test_mode` chung")
        assertTrue(sh.contains("trap cleanup_audio EXIT") && sh.contains("disable_test_mode"), "đóng cửa cầu khi thoát")
    }
}
