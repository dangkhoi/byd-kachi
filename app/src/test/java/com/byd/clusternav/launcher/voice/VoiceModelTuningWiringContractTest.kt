package com.byd.clusternav.launcher.voice

import com.byd.clusternav.launcher.testbridge.TestBridgeCommands
import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ H2 · H5 · H6 — BÀI CANH DÂY NỐI (CLAUDE.md §8: compile xanh ≠ code chạy) ═════════════════════════════════
 *
 * Ba workstream này thêm **bảy cơ chế** mà phần lớn nằm sau một công tắc hoặc một hàng Cài đặt, tức chúng có thể
 * biên dịch sạch, có KDoc đầy đủ, và **không chạy lần nào** — đúng hình dạng `CastShell.evictVd`. Bài này khoá
 * từng call site, và khoá cả **hợp đồng "không đổi hành vi mặc định"** của H5: mọi mặc định mới phải BẰNG hằng
 * đang chạy, nên một máy chưa ai chỉnh vẫn nghe y hệt bản trước.
 */
class VoiceModelTuningWiringContractTest {

    private fun code(rel: String) = SourceRoots.codeOf(rel)

    private val prefs by lazy { code("src/main/java/com/byd/clusternav/PrefsVoiceV3.kt") }
    private val capture by lazy { code("src/main/java/com/byd/clusternav/launcher/voice/VoiceCapture.kt") }
    private val engine by lazy { code("src/main/java/com/byd/clusternav/launcher/voice/VoiceRecognizer.kt") }
    private val session by lazy { code("src/main/java/com/byd/clusternav/launcher/voice/VoiceSession.kt") }
    private val turns by lazy { code("src/main/java/com/byd/clusternav/launcher/voice/VoiceSessionTurns.kt") }
    private val log by lazy { code("src/main/java/com/byd/clusternav/launcher/voice/VoiceUtteranceLog.kt") }
    private val settings by lazy { code("src/main/java/com/byd/clusternav/launcher/voice/VoiceModelSettings.kt") }
    private val prefsSet by lazy { code("src/main/java/com/byd/clusternav/launcher/testbridge/TestBridgePrefsSet.kt") }
    private val state by lazy { code("src/main/java/com/byd/clusternav/launcher/testbridge/TestBridgeState.kt") }
    private val bridge by lazy { code("src/main/java/com/byd/clusternav/launcher/testbridge/KachiTestBridge.kt") }
    private val dump by lazy { code("src/main/java/com/byd/clusternav/launcher/testbridge/TestBridgeVoiceDump.kt") }

    // ══ H5 · (a) KHÔNG đổi hành vi mặc định ═══════════════════════════════════════════════════════════════

    /**
     * Mặc định của cả bốn núm **phải là hằng đang chạy**, đọc thẳng từ `:core` — không phải một literal chép lại.
     *
     * Chép số vào `PrefsVoiceV3.kt` thì hai nơi khai cùng một mặc định, và bản sao sẽ lệch ở đúng lần ai đó chỉnh
     * hằng gốc — lúc ấy "không đổi hành vi" trở thành một câu trong KDoc chứ không còn là một tính chất.
     */
    @Test
    fun `mac dinh bon num bang dung hang dang chay, doc tu core`() {
        assertTrue(
            prefs.contains("K_VOICE_ENDPOINT_SILENCE_MS, VoiceEndpointer.HANGOVER_MS"),
            "mặc định ngưỡng im phải LÀ `VoiceEndpointer.HANGOVER_MS`, không phải số 800 chép tay",
        )
        assertTrue(
            prefs.contains("K_VOICE_ENDPOINT_MIN_SPEECH_MS, VoiceEndpointer.MIN_SPEECH_MS"),
            "mặc định tối-thiểu-tiếng phải LÀ `VoiceEndpointer.MIN_SPEECH_MS`",
        )
        assertTrue(
            prefs.contains("K_VOICE_BEAM, SherpaModelCatalog.MAX_ACTIVE_PATHS"),
            "mặc định beam phải LÀ `SherpaModelCatalog.MAX_ACTIVE_PATHS`",
        )
        assertTrue(
            prefs.contains("K_VOICE_HOTWORD_SCORE, SherpaModelCatalog.HOTWORDS_SCORE"),
            "mặc định điểm hotword phải LÀ `SherpaModelCatalog.HOTWORDS_SCORE`",
        )
        // Và những hằng ấy vẫn đúng bằng con số 1.68 đang chạy trên xe.
        assertEquals(800, VoiceEndpointer.HANGOVER_MS)
        assertEquals(400, VoiceEndpointer.MIN_SPEECH_MS)
        assertEquals(4, SherpaModelCatalog.MAX_ACTIVE_PATHS)
        assertEquals(3.0f, SherpaModelCatalog.HOTWORDS_SCORE)
    }

    /** Dải hợp lệ khai ở `:core`, và hai đầu dải phải thật sự loại được giá trị ngoài dải. */
    @Test
    fun `dai hop le cua bon num nam o core va chan dung hai dau`() {
        assertEquals(600, VoiceEndpointer.MIN_HANGOVER_MS)
        assertEquals(1_500, VoiceEndpointer.MAX_HANGOVER_MS)
        val hang = VoiceEndpointer.MIN_HANGOVER_MS..VoiceEndpointer.MAX_HANGOVER_MS
        assertTrue(600 in hang && 1_500 in hang, "hai đầu dải phải NẰM TRONG dải (đóng, không hở)")
        assertFalse(599 in hang || 1_501 in hang, "ngoài dải phải bị loại")
        assertTrue(VoiceEndpointer.HANGOVER_MS in hang, "mặc định phải nằm trong chính dải của nó")

        val speech = VoiceEndpointer.MIN_MIN_SPEECH_MS..VoiceEndpointer.MAX_MIN_SPEECH_MS
        assertTrue(VoiceEndpointer.MIN_SPEECH_MS in speech)
        assertFalse(199 in speech || 1_001 in speech)

        assertEquals(setOf(4, 8), SherpaModelCatalog.BEAM_CHOICES, "beam chỉ hai lựa chọn — xem KDoc")
        val score = SherpaModelCatalog.MIN_HOTWORDS_SCORE..SherpaModelCatalog.MAX_HOTWORDS_SCORE
        assertTrue(SherpaModelCatalog.HOTWORDS_SCORE in score)
        assertFalse(1.9f in score || 4.1f in score)
    }

    // ══ H5 · (b) Bốn núm phải THẬT SỰ tới được nơi quyết định ═════════════════════════════════════════════

    /**
     * Hai núm của bộ ngắt câu đi vào **hàm dựng** [VoiceEndpointer] (nó ở `:core` và phải thuần), không đi vào
     * bên trong nó dưới dạng một lượt đọc prefs.
     */
    @Test
    fun `hai num ngat cau di vao ham dung VoiceEndpointer o VoiceCapture`() {
        assertTrue(capture.contains("endpointerFromPrefs()"), "phải có hàm dựng theo prefs")
        assertTrue(
            capture.contains("endpointer: VoiceEndpointer? = endpointerFromPrefs()"),
            "mặc định của tham số `endpointer` phải là bản dựng theo prefs — không thì núm không ai đọc",
        )
        val body = SourceRoots.body(capture, "private fun endpointerFromPrefs()")
        assertTrue(body.contains("minSpeechMs =") && body.contains("hangoverMs ="), "phải truyền CẢ HAI ngưỡng")
        assertTrue(
            body.contains("Prefs.voiceEndpointMinSpeechMs(ctx)") && body.contains("Prefs.voiceEndpointSilenceMs(ctx)"),
            "hai ngưỡng phải đọc từ prefs",
        )
        // `:core` vẫn phải THUẦN — không có lượt đọc prefs nào len vào VoiceEndpointer.
        val core = code("src/main/kotlin/com/byd/clusternav/launcher/voice/VoiceEndpointer.kt")
        assertFalse(core.contains("Prefs") || core.contains("Context"), "VoiceEndpointer phải thuần Kotlin")
    }

    /** `voice_beam` / `voice_hotword_score` phải tới đúng hai trường của `OfflineRecognizerConfig`. */
    @Test
    fun `beam va diem hotword di vao cau hinh giai ma that`() {
        val build = SourceRoots.body(engine, "private fun build(")
        assertTrue(build.contains("Prefs.voiceHotwordScore(ctx)"), "điểm hotword phải đọc prefs")
        assertTrue(build.contains("Prefs.voiceBeam(ctx)"), "beam phải đọc prefs")
        assertTrue(build.contains("hotwordsScore =") && build.contains("maxActivePaths ="), "phải gán đúng hai trường")
        assertFalse(
            build.contains("maxActivePaths = 4"),
            "beam không được còn là literal — nó đã về danh mục `:core` (SherpaModelCatalog.MAX_ACTIVE_PATHS)",
        )
    }

    /** Ba con số của H5 phải hiện ra trong `KachiVoiceTiming`, mỗi con số một nhãn **grep được**. */
    @Test
    fun `ba con so ngat cau hien trong nhat ky timing`() {
        val core = code("src/main/kotlin/com/byd/clusternav/launcher/voice/VoiceEndpointer.kt")
        val summary = SourceRoots.body(core, "fun summary()")
        listOf("ngat_o=", "tieng=", "im=").forEach {
            assertTrue(summary.contains(it), "dòng timing thiếu nhãn `$it` — ba con số phải đứng riêng, grep được")
        }
        assertTrue(summary.contains("nguong_im=") && summary.contains("toi_thieu_tieng="),
            "phải in cả hai NGƯỠNG đang áp, không thì đọc log không biết núm đang đặt bao nhiêu")
        // Và tóm tắt phải in ở CẢ BA đường thoát (chốt câu · hết trần · bỏ-giải-mã). Từ 1.69 nó đi qua bề mặt
        // chung [VoiceTurnEndpoint] nên dòng log nói được lượt ấy chạy bằng VAD hay bằng bộ RMS lùi (`duong=`).
        assertTrue(capture.contains("\"ngắt câu: \${ep.summary(fed)}\""), "đường chốt câu phải in")
        assertTrue(capture.contains("\"hết trần: \${ep.summary(fed)}\""), "đường hết trần cũng phải in")
        val turn = code("src/main/java/com/byd/clusternav/launcher/voice/VoiceTurnEndpoint.kt")
        assertTrue(turn.contains("duong=\$route"), "dòng tóm tắt phải nói lượt này chạy bằng đường nào")
        // Ngân sách log ([ĐO xe 1.68] 9,8 KB/phút): tóm tắt chỉ in ở **đường thoát của một lượt**, mỗi đường một
        // lần. Ghim con số là thứ chặn ca "thêm một dòng cho mỗi khối 200 ms" (5 dòng/giây) len vào mà không ai
        // thấy; một lượt in tối đa 2 dòng tóm tắt + 1 dòng `cắt:`, không phải 40 dòng.
        assertEquals(
            3, Regex("""ep\.summary\(fed\)""").findAll(capture).count(),
            "chỉ được in tóm tắt ở các đường THOÁT của một lượt nghe, không phải mỗi khối micro",
        )
        assertEquals(
            1, Regex("""\"cắt: tieng_bat_dau=""").findAll(capture).count(),
            "đúng MỘT dòng `cắt:` mỗi lượt — ba con số của hợp đồng (bắt đầu · hết tiếng · còn lại sau cắt)",
        )
    }

    // ══ H5 · (c) Cầu kiểm thử: bốn khoá, kẹp theo ĐÚNG dải của `:core` ════════════════════════════════════

    @Test
    fun `bon khoa moi nam trong danh sach trang va duoc noi day o prefs_set`() {
        listOf(
            "voice_endpoint_silence_ms", "voice_endpoint_min_speech_ms", "voice_beam", "voice_hotword_score",
        ).forEach { key ->
            assertTrue(key in TestBridgeCommands.WRITABLE_PREFS_KEYS, "khoá $key chưa vào danh sách trắng")
            assertTrue(prefsSet.contains("\"$key\" ->"), "khoá $key có trong danh sách trắng mà KHÔNG ai thi hành")
            assertTrue(
                SourceRoots.body(prefsSet, "private fun readBack(").contains("\"$key\" ->"),
                "khoá $key phải đọc lại được — lời đáp nói giá trị THẬT sau lượt ghi",
            )
        }
        // Dải kẹp phải trỏ về `:core`, không phải số chép tay.
        assertTrue(prefsSet.contains("VoiceEndpointer.MIN_HANGOVER_MS..VoiceEndpointer.MAX_HANGOVER_MS"))
        assertTrue(prefsSet.contains("VoiceEndpointer.MIN_MIN_SPEECH_MS..VoiceEndpointer.MAX_MIN_SPEECH_MS"))
        assertTrue(prefsSet.contains("SherpaModelCatalog.BEAM_CHOICES"))
        assertTrue(prefsSet.contains("SherpaModelCatalog.MIN_HOTWORDS_SCORE..SherpaModelCatalog.MAX_HOTWORDS_SCORE"))
        // Ngoài dải ⇒ `bad_prefs_value:` (không kẹp im lặng — xem chú thích tại chỗ dùng).
        assertTrue(prefsSet.contains("ERR_BAD_VALUE"), "ngoài dải phải trả mã lỗi, không phải một giá trị đã kẹp")
    }

    // ══ H2 · nhật ký lượt nói ═════════════════════════════════════════════════════════════════════════════

    /** Công tắc `voice_keep_log` **mặc định BẬT** — owner muốn dữ liệu thật của mọi chuyến đi. */
    @Test
    fun `cong tac giu nhat ky mac dinh BAT`() {
        assertTrue(
            prefs.contains("getBoolean(K_VOICE_KEEP_LOG, true)"),
            "mặc định phải là `true` — một chuyến đi không ghi lại là một chuyến phải lái lại",
        )
        assertTrue(log.contains("fun enabled("), "phải có một chỗ DUY NHẤT hỏi công tắc")
        assertTrue(
            SourceRoots.body(log, "fun record(").contains("if (!enabled(ctx)) return null"),
            "tắt công tắc thì không được ghi gì — cổng đặt ngay đầu `record`",
        )
    }

    /** Cả hai hàm của nhật ký phải có call site THẬT trong phiên nghe (CLAUDE.md §8). */
    @Test
    fun `nhat ky duoc goi that tu phien nghe, hai nua dung cho`() {
        assertTrue(turns.contains("VoiceUtteranceLog.record("), "nửa ĐẦU (tiếng + số đo) phải được gọi")
        assertTrue(turns.contains("VoiceUtteranceLog.update("), "nửa SAU (ý định + câu trả lời) phải được gọi")
        assertTrue(session.contains("logHeard(it, heard, sentence)"), "phiên nghe phải gọi `logHeard`")
        assertTrue(session.contains("logDone(intents, batch)"), "phiên nghe phải gọi `logDone`")
        // Ghi TRƯỚC đường thoát "nghe ra rỗng" — đó là ca đáng nghe lại nhất.
        val run = SourceRoots.body(session, "private fun runSession(")
        assertTrue(
            run.indexOf("logHeard(") in 0 until run.indexOf("kachi_voice_nothing_heard"),
            "phải ghi tiếng TRƯỚC khi thoát vì câu rỗng",
        )
        // Và `logDone` phải chạy TRƯỚC khi `clarifyRound` bị đặt lại — nếu không cờ `clarify` luôn là false.
        val exec = SourceRoots.body(session, "internal fun execute(")
        assertTrue(
            exec.indexOf("logDone(") in 0 until exec.indexOf("clarifyRound = 0"),
            "logDone phải đọc `clarifyRound` TRƯỚC khi nó về 0",
        )
    }

    /**
     * Ngân sách hiệu năng ([ĐO xe 1.68] `KachiPerf`): mọi I/O của nhật ký nằm trên luồng NỀN, và nó ghi **một lần
     * cho một phiên** chứ không phải một lần cho một khối micro (5 khối/giây).
     */
    @Test
    fun `nhat ky khong cham dia tren luong goi va khong ghi theo khoi`() {
        assertTrue(log.contains("Executors.newSingleThreadExecutor"), "phải có luồng nền riêng")
        assertTrue(log.contains("isDaemon = true"), "luồng nền không được giữ tiến trình sống")
        assertTrue(
            SourceRoots.body(log, "fun record(").contains("submit("),
            "`record` phải đẩy việc ghi sang luồng nền",
        )
        // Vòng đọc micro (5 khối/giây) KHÔNG được biết gì về nhật ký: [VoiceCapture] trả số đo ra ngoài, và
        // phiên nghe ghi **một lần** sau khi lượt đã xong (xem `logHeard`).
        assertFalse(capture.contains("VoiceUtteranceLog"), "không được ghi nhật ký từ bên trong vòng đọc micro")
    }

    /** Lời hứa hạng nhất: nhật ký KHÔNG mở đường ra mạng nào. */
    @Test
    fun `nhat ky khong cham mang`() {
        listOf("Socket(", "WebSocket", "OkHttp", "Retrofit", "URLConnection", "HttpConn", "URL(").forEach {
            assertFalse(log.contains(it), "VoiceUtteranceLog chạm `$it` — tiếng phải ở lại trong xe")
        }
        // Tệp nằm trong bộ nhớ RIÊNG của app, không phải thẻ dùng chung.
        assertTrue(log.contains("filesDir"), "thư mục nhật ký phải nằm trong `filesDir`")
    }

    /** Nút xuất + ô tích phải có mặt THẬT trong Cài đặt, và câu chữ phải nói rõ tiếng không rời khỏi xe. */
    @Test
    fun `cai dat co o tich va nut xuat nhat ky`() {
        assertTrue(settings.contains("VoiceUtteranceLog.enabled(context)"), "ô tích phải đọc trạng thái thật")
        assertTrue(settings.contains("Prefs.setVoiceKeepLog(context, on)"), "ô tích phải GHI được")
        assertTrue(settings.contains("VoiceUtteranceLog.exportZip(context)"), "nút xuất phải gọi đường nén thật")
        assertTrue(settings.contains("R.string.kachi_voice_log_exported"), "xong phải hiện ĐƯỜNG DẪN, không im lặng")
        val vi = SourceRoots.text("src/main/res/values/strings_kachi.xml")
        val en = SourceRoots.text("src/main/res/values-en/strings_kachi.xml")
        assertTrue(vi.contains("chỉ lưu trên xe, không gửi đi"), "câu chữ VI phải nói thẳng tiếng không rời khỏi xe")
        assertTrue(en.contains("never sent anywhere"), "câu chữ EN phải nói thẳng điều đó")
    }

    /** Lệnh cầu `voice_dump` đi qua ĐÚNG hàm nén của nút Cài đặt, và có dây nối trong receiver. */
    @Test
    fun `lenh voice_dump co day noi va dung chung duong nen`() {
        assertTrue(dump.contains("VoiceUtteranceLog.exportZip(app)"), "không được dựng đường nén thứ hai")
        assertTrue(
            bridge.contains("TestBridgeCommands.VOICE_DUMP -> { TestBridgeVoiceDump.run(app, cmd, reply); return }"),
            "receiver phải điều phối `voice_dump` (kèm `cmd` để lệnh tự kiểm cổng auto_confirm) — không có dòng này thì lệnh im lặng không tồn tại",
        )
        // [SCAN §6 1.69, W5] Xuất TIẾNG CABIN ra Download/ công khai qua receiver exported ⇒ phải có cổng auto_confirm
        // ngay trong lệnh (không dựa vào receiver nhớ hộ) và để dấu AUTO-CONFIRM như mọi lượt qua cổng.
        assertTrue(dump.contains("if (!cmd.autoConfirm)"), "voice_dump phải từ chối khi thiếu --ez auto_confirm true")
        assertTrue(dump.contains("AUTO-CONFIRM: voice_dump"), "lượt xuất tiếng cabin phải để dấu AUTO-CONFIRM trong logcat")
        assertTrue(dump.contains("Thread("), "nén vài chục MB phải ở luồng nền, không phải luồng nhận broadcast")
    }

    // ══ H6 · đổi sang mô hình nhẹ ═════════════════════════════════════════════════════════════════════════

    /** Bản nhẹ tính từ **danh mục**, không viết cứng tên mô hình nào ở tầng vẽ (CLAUDE.md §7). */
    @Test
    fun `ban nhe tinh tu danh muc, khong viet cung ten mo hinh`() {
        val fp32 = SherpaModelCatalog.ZIPFORMER_VI
        val light = SherpaModelCatalog.lighterThan(fp32)
        assertNotNull(light, "fp32 (266 MB) phải có một bản nhẹ hơn tải được")
        assertTrue(light!!.totalBytes < fp32.totalBytes, "bản đề nghị phải thật sự nhẹ hơn")
        assertTrue(light.downloadable, "không được đề nghị một gói chưa ghim sha256 (fail-safe)")
        // Đã ở bản nhẹ nhất ⇒ không còn gì để đề nghị (đó là thứ `state.alt_available` sẽ báo sau khi đổi xong).
        assertNull(SherpaModelCatalog.lighterThan(light), "bản nhẹ nhất không được tự đề nghị chính nó")
        // Gói chưa mirror (URL rỗng) không bao giờ được đề nghị, dù nó nhẹ hơn.
        assertFalse(SherpaModelCatalog.HATAPHU_VI.downloadable, "gói gated phải là `downloadable = false`")
        assertTrue(
            settings.contains("SherpaModelCatalog.lighterThan(current)"),
            "tầng vẽ phải HỎI danh mục, không tự quyết `nếu là int8 thì…`",
        )
        assertFalse(settings.contains("int8"), "không tên mô hình nào được viết cứng ở tầng vẽ")
    }

    /** Chỉ đổi lựa chọn khi **cả bốn tệp** đã xong + đã kiểm; và không bao giờ tự xoá bản nặng. */
    @Test
    fun `chi doi lua chon o nhanh Done, va khong tu go ban nang`() {
        val fn = SourceRoots.body(settings, "private fun switchToLight(")
        assertTrue(fn.contains("VoiceModelStore.install(context, light)"), "phải dùng ĐƯỜNG CÀI có sẵn, không viết mới")
        val done = fn.indexOf("is VoiceModelStore.Step.Done")
        val select = fn.indexOf("VoiceModelStore.select(context, light.id)")
        assertTrue(done in 0 until select, "`select` phải nằm TRONG nhánh `Done` — không đổi sớm khi mất sóng giữa chừng")
        assertTrue(fn.contains("VoiceEngine.release()"), "đổi mô hình phải nhả bản cũ khỏi bộ nhớ native")
        assertFalse(fn.contains("VoiceModelStore.remove("), "tuyệt đối KHÔNG tự xoá bản nặng sau khi đổi")
        // Gỡ là một hàng RIÊNG, và nó từ chối khi bản nặng vẫn đang được chọn (guard ở tầng thi hành).
        val drop = SourceRoots.body(settings, "private fun dropHeavy(")
        assertTrue(drop.contains("VoiceModelStore.selected(context).id == heavy.id"), "guard phải ở tầng thi hành")
        assertTrue(drop.contains("R.string.kachi_voice_model_drop_blocked"), "từ chối phải NÓI RA lý do")
    }

    /** Thiếu RAM là một **ghi chú**, không bao giờ là một lượt tự đổi mô hình. */
    @Test
    fun `thieu RAM chi hien ghi chu, khong tu doi mo hinh`() {
        assertTrue(engine.contains("var lastPreloadSkip: String?"), "phải giữ lại lý do bỏ qua nạp sẵn")
        assertTrue(
            SourceRoots.body(engine, "fun preload(").contains("lastPreloadSkip = why"),
            "chỗ bỏ qua phải ghi lý do lại, không chỉ in logcat",
        )
        assertTrue(settings.contains("VoiceEngine.lastPreloadSkip"), "Cài đặt phải hiện ghi chú đó")
        assertTrue(settings.contains("R.string.kachi_voice_model_preload_skipped"))
        // Và không có đường nào tự gọi `select` ngoài cú bấm của người dùng.
        assertEquals(
            1, Regex("""VoiceModelStore\.select\(""").findAll(settings).count(),
            "chỉ được có ĐÚNG MỘT chỗ đổi lựa chọn mô hình trong Cài đặt",
        )
    }

    /** `state.voice_model` phải mang ba trường mới, và `id` là id ĐANG CHỌN (không phải hằng mặc định). */
    @Test
    fun `state voice_model mang id bytes va alt_available`() {
        val fn = SourceRoots.body(state, "private fun voiceModel(")
        listOf("\"ready\"", "\"id\"", "\"bytes\"", "\"alt_available\"").forEach {
            assertTrue(fn.contains(it), "state.voice_model thiếu trường $it")
        }
        assertTrue(fn.contains("VoiceModelStore.selected(ctx)"), "`id` phải là mô hình ĐANG CHỌN")
        assertFalse(fn.contains("DEFAULT_ID"), "`id` không được lấy từ hằng mặc định — hai thứ khác nhau từ 1.66")
        assertTrue(fn.contains("SherpaModelCatalog.lighterThan("), "`alt_available` phải tính từ danh mục")
        assertTrue(fn.contains("VoiceModelStore.sizeOnDisk(ctx)"), "`bytes` phải là cỡ THẬT trên đĩa")
    }
}
