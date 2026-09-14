package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import java.nio.file.Files
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ V1 · DÂY NỐI CỦA ĐƯỜNG LỆNH — CHỮ (R6) **VÀ TIẾNG** (R9–R14) ════════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-voice-command.html` R6 · R9–R14. Quét SOURCE vì dự án không dựng Activity/View trong JVM
 * thuần (không Robolectric) — cùng lệ [SettingsScreenWiringContractTest], và mọi phép cắt vùng đi qua
 * [SourceRoots.body] (nó **nổ** nếu mốc không còn, thay vì âm thầm quét tới hết tệp).
 *
 * ## Bài THẬT ở đây là hai bài
 *  • *"mỗi nhánh ý định có một đích thật"* — chống đúng bệnh `CastShell.evictVd` (CLAUDE.md §8): một bộ phân
 *    tích 194 mã compile sạch mà **không ai gọi**, hoặc gọi tới một nhánh rỗng.
 *  • *"tầng tiếng nằm đúng chỗ và không ra mạng"* — R13 là một **cam kết về phạm vi**, không phải một câu trong
 *    tài liệu. Ngày ai đó thêm `SpeechRecognizer` (đường của Google, cần mạng) hoặc mở một socket trong một tệp
 *    `Voice*`, bài này đỏ. Bản trước của khối này cấm cả `AudioRecord` vì pha ấy CHƯA có micro; owner mở cổng
 *    2026-09-14 nên câu hỏi đổi, **phạm vi kiểm thì không được mất**.
 */
class VoiceCommandWiringContractTest {

    private fun code(relative: String): String = SourceRoots.codeOf(relative)

    private val dispatcher by lazy { code("src/main/java/com/byd/clusternav/launcher/VoiceDispatcher.kt") }
    private val console by lazy { code("src/main/java/com/byd/clusternav/launcher/VoiceTextConsole.kt") }
    private val sections by lazy { code("src/main/java/com/byd/clusternav/launcher/SettingsSections.kt") }
    private val panels by lazy { code("src/main/java/com/byd/clusternav/launcher/HomePanels.kt") }
    private val wiring by lazy { code("src/main/java/com/byd/clusternav/launcher/KachiHomeWiring.kt") }
    private val activity by lazy { code("src/main/java/com/byd/clusternav/launcher/KachiHomeActivity.kt") }

    // ══ (1) MỌI nhánh ý định có đích thật ═════════════════════════════════════════════════════════════════

    /**
     * Chín nhánh của `VoiceIntent` (sealed ⇒ `when` exhaustive ở tầng Kotlin), và **mỗi nhánh phải chạm một
     * đường có thật**. Danh sách đích viết ra ở đây chính là bản kê *"giọng nói làm được gì"* — đọc bài này là
     * biết, không phải đi lần từng tệp.
     */
    @Test
    fun `moi nhanh VoiceIntent deu co dich that`() {
        val run = SourceRoots.body(dispatcher, "private fun run(")
        mapOf(
            "is VoiceIntent.Control ->" to "runControl(intent)",
            "is VoiceIntent.Macro ->" to "runMacro(intent)",
            "is VoiceIntent.Launcher ->" to "runLauncher(intent)",
            "is VoiceIntent.Profile ->" to "onSwitchProfile(intent.name)",
            "is VoiceIntent.Read ->" to "runRead(intent)",
            "is VoiceIntent.Nav ->" to "runNav(intent, labels)",
            "is VoiceIntent.Media ->" to "runMedia(intent)",
            "is VoiceIntent.OpenApp ->" to "runOpenApp(intent, labels)",
            "is VoiceIntent.Unknown ->" to "VoiceReply.unknown(intent)",
        ).forEach { (branch, target) ->
            assertTrue(run.contains(branch), "thiếu nhánh $branch — ý định bị nuốt im lặng")
            assertTrue(run.contains(target), "nhánh $branch phải gọi `$target`, không được để rỗng")
        }
    }

    /** Từng nhánh đi ĐÚNG đường mà một cú chạm đang đi — không dựng cơ chế thứ hai (KDoc [VoiceDispatcher]). */
    @Test
    fun `tung nhanh di dung duong da co`() {
        listOf(
            "actByKind(" to "nút xe phải qua bảng định tuyến dùng chung, không tự chọn cửa (xem KDoc actByKind)",
            "ControlTileState.shared" to "phải ghi lại trạng thái vào bảng DÙNG CHUNG, không thì thanh nút nói khác",
            "MacroRunner.run(" to "gói lệnh phải qua bộ chạy thuần ở `:core`",
            "TelemetryReadout.of(" to "đọc số phải qua đúng bộ định dạng mà ô đọc đang dùng",
            // SOÁT 2026-09-14: chỗ gọi đổi từ `media().play()` sang `val bridge = media()` + `bridge.play()` để
            // ĐỌC được kết quả transport (bắn vào phiên rỗng là no-op im lặng — xem KDoc `runMedia`). Tính chất
            // được bảo vệ KHÔNG đổi: nhạc vẫn phải đi qua MediaBridge, không tự bắn intent.
            "val bridge = media()" to "nhạc phải qua MediaBridge (MediaSession), không tự bắn intent",
            "bridge.play()" to "lệnh phát phải là transport của MediaSession",
            "LauncherActions.APPS" to "hành động launcher phải tra theo mã của `:core`",
            "NavApps" to "app dẫn đường phải lấy từ roster dùng chung, không viết cứng tên gói",
        ).forEach { (needle, why) -> assertTrue(dispatcher.contains(needle), why) }
    }

    /** Lệnh *"tăng/giảm"* phải cộng vào mức ĐANG dùng — `:core` cố ý không biết mức đó. */
    @Test
    fun `lenh tuong doi cong vao muc dang dung, khong lay mac dinh cua registry`() {
        val fn = SourceRoots.body(dispatcher, "private fun runControl(")
        assertTrue(
            fn.contains("st.value(def) + i.relative * def.step"),
            "phải lấy mức đang hiển thị trong ControlTileState.shared; lấy `def.value` là mốc MẶC ĐỊNH của registry " +
                "⇒ 'tăng gió' khi xe đang ở mức 7 sẽ thành GIẢM",
        )
        assertTrue(fn.contains("def.clamp("), "giá trị mới phải kẹp bằng chính `ControlDef.clamp`")
    }

    // ══ (2) Cổng vào: hàng trong Cài đặt › Hệ thống & quyền › Nâng cao ════════════════════════════════════

    @Test
    fun `hang go lenh nam trong muc Nang cao cua nhom He thong`() {
        val fn = SourceRoots.body(sections, "private fun system(")
        val advanced = fn.substringAfter("R.string.kachi_sub_advanced")
        assertTrue(advanced.contains("VoiceTextConsole(context, rows, deps).build(body)"),
            "hàng gõ lệnh phải nằm SAU tiêu đề *Nâng cao* — cùng chỗ với hai màn chẩn đoán kia")
        assertTrue(advanced.contains("R.string.kachi_voice_title"), "hàng phải có tiêu đề đọc được")
    }

    /** Ba cổng mới của [SettingsDeps] phải được nối THẬT tới Activity — mặc định rỗng là một nút chết. */
    @Test
    fun `ba cong cua duong thu lenh duoc noi that`() {
        assertTrue(panels.contains("openAppList = openAppList"), "HomePanels phải chuyển tiếp openAppList")
        assertTrue(panels.contains("openAppByPackage = openAppByPackage"), "HomePanels phải chuyển tiếp openAppByPackage")
        assertTrue(panels.contains("openSettingsGroup = { g -> openSettings(g) }"),
            "nhảy nhóm Cài đặt phải dùng chính `openSettings` của HomePanels, không mở bảng thứ hai")
        assertTrue(wiring.contains("openAppList = openAppList") && wiring.contains("openAppByPackage = openAppByPackage"),
            "khối nối dây phải truyền cả hai xuống HomePanels")
        assertTrue(activity.contains("openAppList = { drawerController.openAppList() }"),
            "Activity phải truyền CHÍNH lambda ngăn kéo (cùng biểu thức với thanh trên và thanh nút)")
        assertTrue(activity.contains("openAppByPackage = { pkg -> appOpener.openByIntent(pkg) }"),
            "mở app phải đi qua `AppOpener` — đường mà ngăn kéo đang dùng")
    }

    @Test
    fun `console khong tu mo cua vao noi luu ben`() {
        listOf("SharedPreferences", "WorkspacePrefs", "Prefs.").forEach {
            assertFalse(console.contains(it), "tầng UI 0 lần ghi bền trực tiếp — `$it` không được có mặt")
        }
    }

    // ══ (3) R13 — TẦNG TIẾNG NẰM ĐÚNG CHỖ, VÀ KHÔNG RA MẠNG ══════════════════════════════════════════════

    /**
     * **Bài canh phạm vi, bản pha NGHE.**
     *
     * R8 cũ nói *"chưa có mic"* và bài canh này từ chối mọi `AudioRecord`. Từ 1.49 owner đã mở cổng đó
     * (2026-09-14: *"Kiki nó chạy được, Gemini chạy được trên xe thì app mình cũng chạy được"* — micro cho app
     * thường được coi là [ĐO] qua Kiki, `docs/diagnostics/kiki-car-RE-2026-09-14.md`), nên câu hỏi phải đổi. Nó
     * KHÔNG được biến mất: cam kết mới hẹp hơn và kiểm được y như cũ.
     *
     * Ba điều còn bị cấm, mỗi điều một lý do khác nhau:
     *  • **`SpeechRecognizer` / `RecognitionListener`** — đường của Google, cần mạng và cần dịch vụ Google trên
     *    đầu xe. Cả hai đều là thứ Kachi cố ý không phụ thuộc (RE Kiki §2.1: điều khiển xe không được phụ thuộc
     *    4G). Có nó trong mã là tính năng **âm thầm** đổi từ tại-máy sang trên-mây.
     *  • **`TextToSpeech`** — giọng nói tiếng Việt trên xe này còn [CHƯA BIẾT] (spec §4.4). Trả lời bằng âm báo
     *    + chữ là quyết định đã ghi, không phải thứ ai đó tiện tay nâng cấp giữa một bản vá.
     *  • **`MediaRecorder(`** — ghi âm ra TỆP. Nhận dạng tại máy không cần một tệp âm thanh nào tồn tại; có tệp
     *    là có thứ để rò rỉ. Cấm **dựng** lớp đó, không cấm nhắc tên nó: `MediaRecorder.AudioSource.VOICE_RECOGNITION`
     *    chỉ là bảng hằng NGUỒN ÂM mà `AudioRecord` đọc — chặn cả tên là chặn nhầm đúng thứ ta muốn dùng.
     */
    @Test
    fun `khong dung ASR tren may chu, khong TTS, khong ghi am ra tep`() {
        val banned = listOf("SpeechRecognizer", "RecognitionListener", "TextToSpeech", "MediaRecorder(")
        voiceSources().forEach { (name, src) ->
            banned.forEach { token ->
                assertFalse(
                    src.contains(token),
                    "$name dùng `$token` — pha NGHE chốt là nhận dạng TẠI MÁY, trả lời bằng âm báo + chữ " +
                        "(spec §4.4). Đổi quyết định đó phải sửa spec trước, không sửa mã trước.",
                )
            }
        }
    }

    /**
     * **TIẾNG NÓI KHÔNG RỜI KHỎI XE** — lời hứa hạng nhất của cả pha này, nên nó có một bài canh riêng.
     *
     * Quét mọi tệp `Voice*` của cả ba module tìm dấu vết đi ra ngoài: socket, HTTP, WebSocket, OkHttp, Retrofit.
     * Một dòng như thế là tính năng đổi bản chất — từ *"xe tự nghe"* thành *"xe gửi giọng bạn đi đâu đó"* — mà
     * người dùng không có cách nào biết. Đây đúng là loại thay đổi phải **không biên dịch được**, chứ không phải
     * loại chờ ai đó soát ra.
     *
     * ⚠ [VoiceModelStore] **có** tải mô hình qua mạng và đó là ngoại lệ ĐÚNG: nó tải **xuống** một tệp đã ghim
     * sha256, không gửi gì **lên**. Nên nó đi qua `HttpConn` (cửa duy nhất, chỉ HTTPS) và bài canh dưới chỉ tha
     * đúng một tên đó, không tha cả tệp.
     */
    @Test
    fun `khong tep Voice nao gui tieng noi ra mang`() {
        val banned = listOf("Socket(", "DatagramSocket", "WebSocket", "OkHttp", "Retrofit", "URLConnection", "URL(")
        voiceSources().forEach { (name, src) ->
            banned.forEach { token ->
                assertFalse(src.contains(token), "$name mở một đường ra mạng (`$token`) — tiếng nói phải ở lại trong xe")
            }
        }
        val store = code("src/main/java/com/byd/clusternav/launcher/voice/VoiceModelStore.kt")
        assertTrue(store.contains("HttpConn.open("), "đường TẢI MÔ HÌNH phải đi qua cửa HTTPS duy nhất `HttpConn`")
        assertFalse(store.contains("outputStream.write(") && store.contains("conn.outputStream"),
            "cửa tải mô hình chỉ được ĐỌC xuống, không gửi gì lên")
    }

    /** Và bộ nhận dạng phải là **Vosk tại máy**, ràng bằng ngữ pháp — không phải giải mã tự do. */
    @Test
    fun `bo nhan dang la Vosk tai may va co rang ngu phap`() {
        val rec = code("src/main/java/com/byd/clusternav/launcher/voice/VoiceRecognizer.kt")
        assertTrue(rec.contains("org.vosk.Model"), "phải dùng `org.vosk.Model` (tại máy)")
        assertTrue(rec.contains("Recognizer(model, SAMPLE_RATE, grammar.json())"),
            "phải dựng Recognizer KÈM ngữ pháp — thiếu nó là giải mã tự do 19.529 từ, đúng thứ pha này tránh")
        assertTrue(rec.contains("grammar.phrasesKept == 0"),
            "ngữ pháp rỗng phải bị TỪ CHỐI: Vosk lặng lẽ quay về giải mã tự do, không báo lỗi nào")
        assertFalse(rec.contains("SpeechService") || rec.contains("SpeechStreamService"),
            "KHÔNG dùng vòng ghi âm của thư viện — nó nằm ngoài trần 8 s và ngoài tầm bài canh mạng")
    }

    /** Tệp chạm micro phải đúng MỘT — hai chỗ mở `AudioRecord` là hai câu trả lời cho "mic đang bật tới bao giờ". */
    @Test
    fun `chi mot tep duy nhat mo micro`() {
        val users = voiceSources().filter { (_, src) -> src.contains("AudioRecord(") }.map { it.first }
        assertEquals(listOf("VoiceCapture.kt"), users.sorted(), "chỉ `VoiceCapture` được mở micro; thấy: $users")
        val all = SourceRoots.moduleSourceRoots().flatMap { root ->
            root.toFile().walkTopDown().filter { it.isFile && it.name.endsWith(".kt") }
                .filter { it.readText().contains("AudioRecord(") }.map { it.name }.toList()
        }
        assertEquals(listOf("VoiceCapture.kt"), all.sorted(), "không tệp nào NGOÀI `Voice*` được mở micro; thấy: $all")
    }

    /** Manifest phải xin `RECORD_AUDIO` — đảo đúng bài canh cũ của R8, cùng một chỗ, cùng một tệp. */
    @Test
    fun `manifest xin quyen ghi am va khai micro la khong bat buoc`() {
        val text = Files.readString(SourceRoots.path("src/main/AndroidManifest.xml"))
        assertTrue(text.contains("android.permission.RECORD_AUDIO"), "pha NGHE cần quyền micro")
        assertTrue(
            text.contains("""android:name="android.hardware.microphone" android:required="false""""),
            "đầu xe không có micro vẫn phải CÀI được app — chỉ mất đúng tính năng này",
        )
    }

    // ══ (3b) R12 — BA LỐI VÀO PHIÊN NGHE, MỘT BỘ DÂY ═══════════════════════════════════════════════════

    /**
     * Ba lối vào phải trỏ về **cùng một** phiên.
     *
     * Đây là bài chống đúng bệnh `CastShell.evictVd` (CLAUDE.md §8) ở dạng nguy hiểm hơn: một ô/nút **hiện ra
     * trên màn hình** mà bấm không ra gì. Ba dòng dưới là ba chỗ người dùng chạm được.
     */
    @Test
    fun `ba loi vao phien nghe deu noi that`() {
        assertTrue(wiring.contains("LauncherActions.VOICE -> onVoice()"),
            "ô *Nói với xe* trên thanh nút phải có nhánh thật trong `controlDock`")
        assertTrue(activity.contains("onVoice = { voice.start() }"), "nút mic trên thanh trên phải gọi phiên nghe")
        assertTrue(activity.contains("{ voice.start() },"), "ô thanh nút phải nhận CÙNG lambda với nút mic")
        assertTrue(activity.contains("startVoiceIfRequested(intent, voice)"),
            "đích phím vô-lăng *Kachi nghe* vào màn chính qua extra ⇒ phải có chỗ đọc extra đó")
        val launcher = code("src/main/java/com/byd/clusternav/modules/voicekey/AssistantLauncher.kt")
        assertTrue(launcher.contains("spec == TARGET_KACHI_VOICE"), "bộ gán phím phải nhận đích *Kachi nghe*")
        assertTrue(launcher.contains("EXTRA_START_VOICE"), "và phải đi qua extra của màn chính, không tự bật micro")
    }

    /** Nút mic chỉ hiện khi **mô hình đã có** — hứa một việc chưa làm được là tệ hơn không hứa. */
    @Test
    fun `nut mic tren thanh tren gate boi mo hinh da tai`() {
        assertTrue(
            activity.contains("Prefs.voiceMicPill(this) && VoiceModelStore.isReady(this)"),
            "nút mic phải gate bởi CẢ công tắc người dùng LẪN việc mô hình đã tải",
        )
        val bars = code("src/main/java/com/byd/clusternav/launcher/SettingsSectionsBars.kt")
        assertTrue(bars.contains("deps.bridge.setVoiceMicPill(on)"),
            "công tắc nút mic phải ghi qua cầu, không mở cửa riêng vào nơi lưu bền")
    }

    /** Quyền micro phải nằm trong vòng kiểm, và tự cấp đúng bằng `pm grant`. */
    @Test
    fun `quyen micro nam trong vong kiem va tu cap duoc`() {
        val preflight = code("src/main/java/com/byd/clusternav/launcher/PermissionPreflight.kt")
        assertTrue(preflight.contains("LauncherRequirements.MICROPHONE.id -> micGranted(ctx)"),
            "vòng kiểm phải ĐỌC được trạng thái quyền micro")
        assertTrue(preflight.contains("\"pm grant \$PKG android.permission.RECORD_AUDIO\""),
            "phải tự cấp bằng ĐÚNG câu lệnh mà máy ảo dùng — một câu lệnh cho cả hai môi trường")
        assertEquals(FixBy.SELF, LauncherRequirements.MICROPHONE.fixBy, "tự cấp được ⇒ không hỏi người dùng")
        assertFalse(LauncherRequirements.MICROPHONE.coreFeature,
            "thiếu micro KHÔNG làm mất tính năng lõi ⇒ không được nổ toast ở màn chính mỗi lần mở")
    }

    /** Mô hình tải riêng: ghim sha256 + cỡ, và có đường GỠ. */
    @Test
    fun `mo hinh tai rieng co ghim sha va co duong go`() {
        val store = code("src/main/java/com/byd/clusternav/launcher/voice/VoiceModelStore.kt")
        assertTrue(store.contains("VoiceModelManifest.matches(got.sha256, got.bytes)"),
            "gói tải về phải qua CẢ hai phép kiểm (sha256 + cỡ) trước khi giải nén")
        assertTrue(store.contains("VoiceModelManifest.safeEntryPath(entry.name)"),
            "mỗi mục trong gói phải qua luật chống leo thư mục (CLAUDE.md §4.1)")
        assertTrue(store.contains("fun remove("), "phải có đường GỠ — 53 MB không được ở lại vĩnh viễn")
        val settings = code("src/main/java/com/byd/clusternav/launcher/voice/VoiceModelSettings.kt")
        assertTrue(settings.contains("VoiceModelStore.install(context)"), "màn Cài đặt phải gọi đường cài THẬT")
        assertTrue(settings.contains("VoiceEngine.release()"),
            "gỡ phải trả mô hình khỏi bộ nhớ TRƯỚC khi xoá tệp, không thì mã native còn giữ bản cũ")
        assertTrue(
            code("src/main/java/com/byd/clusternav/launcher/SettingsSections.kt").contains("VoiceModelSettings(context, rows).build(body)"),
            "hàng tải mô hình phải có mặt trong màn Cài đặt — một lớp không ai dựng là mã chết",
        )
    }

    /** Phiên nghe có TRẦN thời gian, và hộp xác nhận mặc định là KHÔNG. */
    @Test
    fun `phien nghe co tran thoi gian va cong xac nhan mac dinh la KHONG`() {
        val session = code("src/main/java/com/byd/clusternav/launcher/voice/VoiceSession.kt")
        assertTrue(session.contains("MAX_LISTEN_MS = 8_000L"), "phải có trần cứng cho một phiên nghe")
        assertTrue(session.contains("capture.listen(it, MAX_LISTEN_MS"), "và trần đó phải được TRUYỀN vào vòng nghe")
        assertTrue(session.contains("answerConfirm(answer == true)"),
            "nghe không ra `đồng ý` ⇒ KHÔNG. Im lặng không bao giờ được hiểu là đồng ý.")
        assertTrue(session.contains("VoiceLexicon.confirmAnswer("),
            "câu trả lời có/không phải đọc bằng bảng ở `:core` (kiểm off-car), không bằng một `if` ở tầng vẽ")
        val capture = code("src/main/java/com/byd/clusternav/launcher/voice/VoiceCapture.kt")
        assertTrue(capture.contains("AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK"),
            "chỉ HẠ tiếng nhạc, không dừng hẳn — xem KDoc VoiceCapture")
    }

    // ══ (3b) SOÁT Pass 2 — ba bài khoá đúng ba lỗi đã vá ═════════════════════════════════════════════════

    /**
     * [SOÁT Pass 2 · P1] **Phiên nghe phải chết theo màn chính.**
     *
     * Tấm chữ là một cửa sổ `TYPE_APPLICATION_OVERLAY` ⇒ nó KHÔNG chết cùng activity (đúng họ với
     * `DrawerController`, xem chú thích trong `onDestroy`). Thiếu lời gọi này thì sau khi màn chính huỷ vẫn còn
     * một cửa sổ phủ toàn màn ăn mọi cú chạm, một `AudioRecord` đang mở, và một `VoiceDispatcher` trỏ vào
     * activity đã huỷ — thứ chỉ hết bằng cách khởi động lại đầu xe.
     */
    @Test
    fun `phien nghe chet theo man chinh va huy duoc khi khong con vong nghe`() {
        val session = code("src/main/java/com/byd/clusternav/launcher/voice/VoiceSession.kt")
        assertTrue(session.contains("fun stop()"), "`VoiceSession` phải có đường tắt phiên ngay lập tức")
        val destroy = SourceRoots.body(activity, "override fun onDestroy()")
        assertTrue(destroy.contains("voice.stop()"), "`onDestroy` phải tắt phiên nghe — cửa sổ overlay sống lâu hơn màn")
        assertTrue(destroy.contains("voiceLazy.isInitialized()"),
            "và hỏi `isInitialized` trước: chạm vào `voice` ở đây sẽ DỰNG một phiên ngay lúc màn đang chết")
        // Huỷ ở trạng thái KHÔNG có vòng nghe (đang báo thiếu quyền / đang nán lại sau câu trả lời) phải đóng
        // được tấm chữ; nếu không, `running` khoá tới 8 giây và nút mic bấm không ra gì.
        assertTrue(SourceRoots.body(session, "fun cancel()").contains("if (!capturing.get()) close()"),
            "chạm ra ngoài phải đóng được tấm chữ khi không có vòng nghe nào tự đóng nó")
    }

    /**
     * [SOÁT Pass 2 · P1/P2] **Đường tải mô hình: một lượt tại một thời điểm · đủ chỗ · không rơi sang `http`.**
     *
     * Nút trong Cài đặt tự khoá khi đang tải, nhưng nó chỉ là một `TextView` của **trang đang dựng**: mở lại màn
     * Cài đặt cho ra một nút mới bật sẵn trong khi luồng cũ còn chạy ⇒ hai luồng cùng ghi một `model.zip`.
     */
    @Test
    fun `tai mo hinh co chot mot luot, kiem cho trong va chot HTTPS sau chuyen huong`() {
        val store = code("src/main/java/com/byd/clusternav/launcher/voice/VoiceModelStore.kt")
        assertTrue(store.contains("installing.compareAndSet(false, true)"),
            "phải có chốt một-lượt-cài: hai lượt song song ghi đè nhau rồi cùng hỏng sha")
        assertTrue(store.contains("usableSpace"),
            "phải hỏi chỗ trống TRƯỚC khi tải 32 MB — hết đĩa giữa chừng là mất tiền mạng 4G mà không ai biết vì sao")
        assertTrue(store.contains("conn.url.protocol"),
            "sau chuyển hướng phải kiểm lại giao thức: `HttpConn` chỉ chốt được địa chỉ ta GÕ VÀO")
    }

    /**
     * [SOÁT Pass 2 · P1] Trần 500 dòng (CLAUDE.md §4.1 · spec R-nf4) cho **những tệp pha NGHE đã chạm**.
     *
     * Không quét cả repo: vài tệp đã trên trần từ lâu và là nợ riêng. Bài này chỉ giữ đúng một lời hứa — thêm
     * tính năng thì thêm **tệp**, không thêm dòng vào hai tệp vốn đã sát trần.
     */
    @Test
    fun `pha NGHE khong day tep nao qua tran 500 dong`() {
        val touched = listOf(
            "src/main/java/com/byd/clusternav/launcher/KachiHomeActivity.kt",
            "src/main/java/com/byd/clusternav/launcher/KachiHomeWiring.kt",
            "src/main/java/com/byd/clusternav/launcher/KachiHomeSlots.kt",
            "src/main/java/com/byd/clusternav/launcher/ClusterNavBridge.kt",
            "src/main/java/com/byd/clusternav/launcher/ClusterNavBridgeKeys.kt",
        )
        touched.forEach { rel ->
            val lines = code(rel).lines().size
            assertTrue(lines <= 500, "$rel dài $lines dòng — trần là 500 (CLAUDE.md §4.1); tách theo VAI, đừng nén dòng")
        }
        voiceSources().forEach { (name, src) ->
            assertTrue(src.lines().size <= 500, "$name dài ${src.lines().size} dòng — trần là 500")
        }
    }

    /** Mọi tệp `Voice*` của `:app` và `:core` — một chỗ dựng danh sách cho mọi bài quét. */
    private fun voiceSources(): List<Pair<String, String>> {
        val out = SourceRoots.moduleSourceRoots().flatMap { root ->
            root.toFile().walkTopDown()
                .filter { it.isFile && it.name.endsWith(".kt") && it.name.startsWith("Voice") }
                .map { it.name to it.readText() }.toList()
        }
        assertTrue(out.size >= 14, "không tìm thấy đủ tệp Voice*.kt để quét; thấy: ${out.map { it.first }}")
        return out
    }

    // ══ (4) Bộ phân tích KHÔNG mọc bản sao ở `:app` ═══════════════════════════════════════════════════════

    /**
     * Ngữ pháp sống ở `:core` (test off-car). `:app` chỉ được **gọi** nó. Một bảng từ khoá thứ hai ở tầng vẽ là
     * đúng họ lỗi `unitPrefs` ×4 mà dự án đã dọn.
     */
    @Test
    fun `app khong tu dung bang tu khoa thu hai`() {
        listOf(dispatcher, console).forEach { src ->
            assertFalse(Regex("""listOf\(\s*"bat"""").containsMatchIn(src), "bảng động từ phải ở `:core`")
            assertFalse(src.contains("ControlRegistry.ALL.filter"), "đừng dựng lại từ vựng ở tầng vẽ")
        }
        assertTrue(dispatcher.contains("VoiceIntentParser.parse("), "phải gọi bộ phân tích của `:core`")
        assertEquals(
            1, Regex(Regex.escape("VoiceIntentParser.parse(")).findAll(dispatcher + console).count(),
            "đúng MỘT chỗ gọi bộ phân tích trong `:app` — hai chỗ là hai luật tách câu ghép",
        )
    }
}
