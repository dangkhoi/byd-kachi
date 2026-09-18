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

    /**
     * **Bộ dây của cầu giọng nói**, không phải *một tệp*.
     *
     * Từ voice pha 2 (2026-09-16) vai *"giao chữ/toạ độ cho app đích"* nằm ở `VoiceTargetDispatch.kt` (tách vì trần 500 dòng — xem
     * KDoc lớp đó). Bài này canh **dây nối**, nên phạm vi quét phải đi theo vai chứ không theo tên tệp: ghim một
     * tệp là biến mọi lượt tách tệp hợp lệ thành một lượt đỏ giả, và cách chữa đỏ giả ấy thường là gỡ assert.
     */
    private val dispatcher by lazy {
        code("src/main/java/com/byd/clusternav/launcher/VoiceDispatcher.kt") + "\n" +
            code("src/main/java/com/byd/clusternav/launcher/VoiceTargetDispatch.kt")
    }
    private val console by lazy { code("src/main/java/com/byd/clusternav/launcher/VoiceTextConsole.kt") }
    private val sections by lazy { code("src/main/java/com/byd/clusternav/launcher/SettingsSections.kt") }
    private val panels by lazy { code("src/main/java/com/byd/clusternav/launcher/HomePanels.kt") }
    private val wiring by lazy { code("src/main/java/com/byd/clusternav/launcher/KachiHomeWiring.kt") }
    private val activity by lazy { code("src/main/java/com/byd/clusternav/launcher/KachiHomeActivity.kt") }

    // ══ (1) MỌI nhánh ý định có đích thật ═════════════════════════════════════════════════════════════════

    /**
     * MƯỜI nhánh của `VoiceIntent` (sealed ⇒ `when` exhaustive ở tầng Kotlin), và **mỗi nhánh phải chạm một
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
            // Sổ địa chỉ (docs/specs/kachi-voice-addresses.html R2) — nhánh THỨ MƯỜI. Nó phải có đích riêng chứ
            // không gộp vào `runNav`: điểm đến ở đây là dữ liệu ĐÃ LƯU (không geocode, không hỏi lại) và app đích
            // chọn theo dữ liệu của mục, không theo thứ tự ưu tiên trần.
            "is VoiceIntent.NavigateSaved ->" to "runNavSaved(intent, labels)",
            "is VoiceIntent.Media ->" to "runMedia(intent, labels)",
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
            // V1.1 — hai đường mới, cùng một luật: dùng lại đường đã có, không dựng cơ chế thứ hai.
            "assignAppToSlot(" to "gắn app vào ô phải đi đường của NGĂN KÉO, không gọi thẳng ViewModel",
            "VoiceAppTargets" to "app đích phải tra từ bảng ở `:core`, không rẽ nhánh theo tên gói",
        ).forEach { (needle, why) -> assertTrue(dispatcher.contains(needle), why) }
        assertFalse(dispatcher.contains("viewModel."),
            "cầu giọng nói KHÔNG được cầm ViewModel — mọi thứ đi qua lambda mà một cú chạm đang dùng")
    }

    /**
     * **V1.1 (R15) — *"mở YouTube vào ô số 2"* đi ĐÚNG đường mà ngăn kéo dùng.**
     *
     * Đây là bài chống một đường thứ hai tới ô. `KachiHomeSlots.assignApp` làm ba việc, không phải một: ghi state
     * (qua ViewModel), gỡ app cũ khỏi sổ vị trí, và đặt cửa sổ app mới vào khung ô. Gọi thẳng
     * `viewModel.assignApp` sẽ chỉ làm việc đầu — ô đổi app mà cửa sổ cũ còn nguyên, đúng lỗi đã có thật.
     */
    @Test
    fun `duong gan app vao o la dung duong cua ngan keo`() {
        assertTrue(
            activity.contains("assignAppToSlot = { idx, pkg -> slots.assignApp(idx, pkg); true }"),
            "Activity phải truyền CHÍNH `slots.assignApp` — cùng hàm mà `onPickApp` của ngăn kéo gọi",
        )
        assertTrue(activity.contains("onPickApp = { idx, pkg -> slots.assignApp(idx, pkg) }"),
            "…và ngăn kéo vẫn phải dùng đúng hàm ấy (nếu dòng này đổi, dòng trên không còn là *cùng đường*)")
        assertTrue(wiring.contains("assignAppToSlot = assignAppToSlot"),
            "khối nối dây phải chuyển tiếp xuống cả phiên NGHE lẫn bảng Cài đặt")
        assertTrue(console.contains("assignAppToSlot = deps.assignAppToSlot"),
            "ô *Gõ lệnh chữ* cũng phải gắn thật vào ô, không được mở toàn màn thay thế")

        val fn = SourceRoots.body(dispatcher, "private fun runOpenApp(")
        assertTrue(fn.contains("EffectiveLayout.slotCount("),
            "số ô phải đọc từ bố cục ĐANG dùng (bố cục tự vẽ đổi được giữa hai câu), không phải một hằng")
        assertTrue(fn.contains("assignAppToSlot(slot - 1, pkg)"),
            "phép đổi 1-based (người nói) → 0-based (mảng ô) phải nằm ở ĐÚNG một chỗ, là chỗ này")
    }

    /**
     * Lệnh *"tăng/giảm"* phải cộng vào **số THẬT của xe**, và chỉ lùi về bảng của Kachi khi xe không trả lời.
     *
     * ## Bài này đã SIẾT ở 1.69 — và lý do siết là một phép đo, không phải một ý thích
     * Tới 1.68 nó chỉ đòi mốc lấy từ `ControlTileState.shared`. Đúng so với 1.66 (bảng ấy ít ra còn nhớ những gì
     * chính Kachi đã bấm), nhưng [ĐO xe 2026-09-16] cho thấy nó vẫn sai ở ca thường gặp nhất: bảng khởi tạo bằng
     * `ControlDef.value` (gió **4** · nhiệt **22**) và **không hề biết** người lái vừa chỉnh gì trên màn BYD gốc,
     * nên *"tăng gió"* lúc xe đang ở **gió 1** bắn ra **5** — đúng câu tester tả: *"quất một phát như lò heo quay"*.
     *
     * ⇒ Thứ tự bắt buộc: hỏi xe ([CarControlPort.readState]) **trước**, `st.value(def)` là **đường lùi**. Ghim cả
     * hai vế: thiếu vế đầu thì bệnh cũ quay lại; thiếu vế sau thì máy ảo/off-car mất luôn hành vi 1.68 (bịa một
     * con số còn tệ hơn dùng một con số cũ).
     */
    @Test
    fun `lenh tuong doi cong vao so THAT cua xe, chi lui ve bang cua Kachi khi doc khong duoc`() {
        val fn = SourceRoots.body(dispatcher, "private fun runControl(")
        assertTrue(
            fn.contains("control().readState(def.id)"),
            "phải HỎI XE trước khi cộng — đọc qua `ControlDef.readKey` (khoá ĐỌC), không phải `bindingKey` (khoá GHI)",
        )
        assertTrue(
            fn.contains("?: st.value(def)"),
            "đọc không được (`null`) thì phải lùi về mức đang hiển thị — đúng hành vi 1.68, không được bịa số",
        )
        assertTrue(
            fn.contains("i.relative * def.step"),
            "bước nhảy phải là `def.step` của chính nút đó, không phải một hằng 1",
        )
        assertTrue(fn.contains("def.clamp("), "giá trị mới phải kẹp bằng chính `ControlDef.clamp`")
    }

    /**
     * ═══ [SOÁT 1.69 · P2] …và NỬA KIA của H1: cú **CHẠM** ô −/+ phải đi đúng đường ấy ══════════════════════
     *
     * H1 có hai bề mặt cho cùng một phép cộng: câu nói (`VoiceDispatcher.runControl`, bài ngay trên) và cú chạm
     * (`ControlTileFactory.nudge`). Tới lượt soát này chỉ bề mặt thứ nhất được ghim. Bề mặt thứ hai mang **đúng
     * cùng một bệnh** — mốc lấy từ `ControlTileState` là bảng lạc quan, không biết người lái vừa chỉnh gì trên
     * màn BYD gốc — và nó là bề mặt người ta dùng nhiều hơn hẳn.
     *
     * Vì sao một bài canh chứ không tin vào mã đang đúng: `nudge` là **bốn dòng nằm giữa một hàm dựng view dài**,
     * đúng hình dạng mà CLAUDE.md §8 kể (`CastShell.evictVd` mất call site vì một lượt thay theo dải dòng). Mất
     * dòng `readState` ở đây thì compile vẫn xanh, bài đơn vị vẫn xanh, và **chỉ chiếc xe** biết.
     */
    @Test
    fun `cham o cong tru cung cong vao so THAT cua xe`() {
        val factory = code("src/main/java/com/byd/clusternav/launcher/ControlTileFactory.kt")
        val fn = SourceRoots.body(factory, "fun nudge(")
        assertTrue(
            fn.contains("control().readState(def.id)"),
            "cú chạm −/+ cũng phải HỎI XE trước khi cộng — cùng đường với câu nói (H1)",
        )
        assertTrue(
            fn.contains("?: state.value(def)"),
            "đọc không được (`null`) thì lùi về mức đang hiển thị — đúng hành vi 1.68, không bịa số",
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
     *  • ~~**`TextToSpeech`**~~ — **cổng này ĐÃ MỞ** (owner 2026-09-15: *"Sau khi làm xong việc thì có phản hồi
     *    lại bằng voice cho user chưa?"*), và nó mở **đúng cách mà bài này đòi**: spec trước
     *    (`docs/specs/kachi-voice-feedback.html`), mã sau. Lệnh cấm không biến mất mà **hẹp lại** — xem
     *    [chi tiep tieng chi duoc mo o DUNG MOT TEP]: cấm **dựng** máy đọc ở bất kỳ tệp `Voice*` nào; tên lớp
     *    trong KDoc thì không cấm (chặn cả tên là chặn nhầm đúng chỗ đang giải thích luật).
     *  • **`MediaRecorder(`** — ghi âm ra TỆP. Nhận dạng tại máy không cần một tệp âm thanh nào tồn tại; có tệp
     *    là có thứ để rò rỉ. Cấm **dựng** lớp đó, không cấm nhắc tên nó: `MediaRecorder.AudioSource.VOICE_RECOGNITION`
     *    chỉ là bảng hằng NGUỒN ÂM mà `AudioRecord` đọc — chặn cả tên là chặn nhầm đúng thứ ta muốn dùng.
     */
    @Test
    fun `khong dung ASR tren may chu, khong ghi am ra tep`() {
        val banned = listOf("SpeechRecognizer", "RecognitionListener", "MediaRecorder(")
        voiceSources().forEach { (name, src) ->
            banned.forEach { token ->
                assertFalse(
                    src.contains(token),
                    "$name dùng `$token` — pha NGHE chốt là nhận dạng TẠI MÁY (spec §4.4). Đổi quyết định đó " +
                        "phải sửa spec trước, không sửa mã trước.",
                )
            }
        }
    }

    /**
     * **Cổng ra TIẾNG chỉ được mở ở ĐÚNG MỘT TỆP** — spec `docs/specs/kachi-voice-feedback.html` R2.
     *
     * Bài này thay chỗ cho lệnh cấm `TextToSpeech` cũ, và nó **chặt hơn** chứ không lỏng hơn. Lệnh cấm cũ trả
     * lời câu hỏi *"có đọc không"*; câu hỏi ấy owner đã trả lời rồi. Câu hỏi còn lại — và là câu dễ hỏng hơn —
     * là *"đọc bằng mấy đường"*: hai tệp cùng dựng một `TextToSpeech` nghĩa là hai kết nối dịch vụ, hai lần xin
     * tiêu điểm âm thanh, và hai câu phát chồng lên nhau mà **không tệp nào biết tệp kia tồn tại**. Đúng họ lỗi
     * *"đường thứ hai"* mà KDoc `VoiceDispatcher`/`VoiceWiring` dựng ra để chặn.
     *
     * ⇒ Mọi tệp `Voice*` phải đi qua giao diện `VoiceSpeaker`; chỉ `AndroidTtsSpeaker.kt` (không mang tiền tố
     * `Voice`, cố ý) được chạm thẳng API nền tảng. Tên lớp trong KDoc **không** bị chặn — chặn cả tên là chặn
     * nhầm đúng chỗ đang giải thích luật.
     */
    @Test
    fun `chi tiep tieng chi duoc mo o DUNG MOT TEP`() {
        val banned = listOf("TextToSpeech(", "import android.speech.tts")
        voiceSources().forEach { (name, src) ->
            banned.forEach { token ->
                assertFalse(
                    src.contains(token),
                    "$name chạm thẳng máy đọc của nền tảng (`$token`) — phải đi qua `VoiceSpeaker`; " +
                        "đường ra tiếng chỉ được dựng ở `AndroidTtsSpeaker.kt`",
                )
            }
        }
        // Chốt ngược: tệp được phép phải THẬT SỰ còn đó và còn gác ngưỡng ngôn ngữ. Thiếu vế này thì ngày ai đó
        // xoá `AndroidTtsSpeaker.kt`, bài trên vẫn xanh trong khi tính năng đã chết.
        val speaker = code("src/main/java/com/byd/clusternav/launcher/voice/AndroidTtsSpeaker.kt")
        assertTrue(speaker.contains("isLanguageAvailable"), "phải hỏi nền tảng có giọng vi-VN không, không đoán")
        assertTrue(
            speaker.contains("VoiceSpeakerSelector.LANG_AVAILABLE"),
            "ngưỡng phải đọc từ luật thuần (kiểm off-car), không viết một con số trần ở tầng Android",
        )
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

    /**
     * **V1.1 — NGOẠI LỆ MẠNG THỨ HAI, và nó phải ở lại đúng kích cỡ hiện tại.**
     *
     * [VoiceGeocoder] gửi một **tên địa điểm** đi để lấy về toạ độ ([ĐO] VietMap chỉ nhận toạ độ — nguồn Kiki,
     * xem KDoc `VoiceAppTargets`). Lời hứa *"tiếng nói không rời khỏi xe"* vẫn nguyên: thứ đi ra là cùng loại
     * chữ mà người ta gõ vào ô tìm kiếm bản đồ mười lần một ngày. Bài này khoá đúng ba chốt của KDoc lớp ấy —
     * ngày ai đó gửi thêm gì khác, hoặc gửi từ một tệp khác, nó đỏ.
     */
    @Test
    fun `chi dung mot cua mang cho tra cuu dia diem, va no khong cham toi tieng`() {
        val geo = code("src/main/java/com/byd/clusternav/launcher/voice/VoiceGeocoder.kt")
        assertTrue(geo.contains("HttpConn.open("), "phải đi qua cửa HTTPS duy nhất của dự án")
        assertFalse(geo.contains("outputStream"), "chỉ GET — không gửi thân yêu cầu nào")
        listOf("AudioRecord", "ShortArray", "pcm", "Recognizer").forEach {
            assertFalse(geo.contains(it), "lớp tra cứu địa điểm chạm tới tiếng (`$it`) — hai việc này phải tách hẳn")
        }
        // Và chỉ ĐÚNG BA tệp Voice* được phép có `HttpConn`: tải mô hình (xuống) · tra cứu địa điểm (chữ) ·
        // giải video_id nhạc (chữ). YoutubeResolver gửi một **chuỗi tìm** đi lấy về `video_id` để "phát luôn"
        // (owner 2026-09-18, cơ chế Kiki) — cùng loại chữ như geocoder, KHÔNG gửi tiếng; degrade-safe.
        val yt = code("src/main/java/com/byd/clusternav/launcher/voice/VoiceYoutubeResolver.kt")
        assertFalse(yt.contains("outputStream"), "VoiceYoutubeResolver chỉ GET — không gửi thân yêu cầu nào")
        listOf("AudioRecord", "ShortArray", "pcm", "Recognizer").forEach {
            assertFalse(yt.contains(it), "VoiceYoutubeResolver chạm tới tiếng (`$it`) — phải tách hẳn")
        }
        val users = voiceSources().filter { (_, src) -> src.contains("HttpConn") }.map { it.first }.sorted()
        assertEquals(
            listOf("VoiceGeocoder.kt", "VoiceModelStore.kt", "VoiceYoutubeResolver.kt"),
            users, "có tệp Voice* thứ tư ra mạng: $users",
        )
    }

    /** Và bộ nhận dạng phải là **sherpa-onnx tại máy**, giải mã tự do + biasing — không gửi tiếng ra mạng. */
    @Test
    fun `bo nhan dang la sherpa tai may, giai ma tu do co biasing`() {
        val rec = code("src/main/java/com/byd/clusternav/launcher/voice/VoiceRecognizer.kt")
        assertTrue(rec.contains("com.k2fsa.sherpa.onnx.OfflineRecognizer"), "phải dùng sherpa-onnx `OfflineRecognizer` (tại máy)")
        assertTrue(rec.contains("createStream(hotwords)"),
            "phải bơm hotwords per-stream — đó là biasing kéo giải mã tự do về tập lệnh (thay ngữ pháp FST của Vosk)")
        // ⚠ Needle bỏ dấu ngoặc ĐÓNG (A1, 2026-09-15): `hotwordsFile` nay nhận nhãn **sổ địa chỉ** của hồ sơ đang
        // dùng (`hotwordsFile(places)` — docs/specs/kachi-voice-addresses.html R6). Tính chất bài canh KHÔNG đổi:
        // hotwords vẫn phải sinh từ [SherpaBiasing], không phải một nguồn thứ hai.
        assertTrue(rec.contains("SherpaBiasing.hotwordsFile("),
            "hotwords phải sinh từ danh mục control ([SherpaBiasing]) — cùng NGUỒN với tầng chữ")
        assertFalse(rec.contains("AudioRecord"),
            "bộ nhận dạng KHÔNG tự mở micro — micro chỉ ở [VoiceCapture] (trong trần 8 s + tầm bài canh mạng)")
    }

    /**
     * **V1.1 (R16) — bộ giải mã TỰ DO tồn tại, nhưng chỉ cho LƯỢT 2 và chỉ sau một cụm kích hoạt.**
     *
     * Cam kết *"lượt 1 ràng bằng ngữ pháp"* không đổi một chữ: câu lệnh xe vẫn dựng từ tập đóng. Cái bài này
     * khoá là **điều kiện chạy** của lượt 2 — nếu ai đó gỡ cổng `VoiceOpenVocab.triggerOf` thì mọi câu nói sẽ
     * đi qua một bộ giải mã 19.529 từ, tức đúng thứ pha NGHE bỏ công tránh, mà **không có gì báo**.
     */
    @Test
    fun `bo giai ma tu do chi chay o luot 2, sau mot cum kich hoat`() {
        val rec = code("src/main/java/com/byd/clusternav/launcher/voice/VoiceRecognizer.kt")
        assertTrue(rec.contains("fun openFree("), "R16 cần một bộ giải mã tự do cho phần đuôi từ vựng mở")
        assertTrue(rec.contains("VoiceRecognizer(rec, \"\")"), "bộ giải mã tự do dựng KHÔNG kèm hotwords (biasing rỗng)")

        // ⚠ voice pha 2 (2026-09-16) — khối này tách khỏi `VoiceSession.kt` sang `VoiceFreeTail.kt` (trần 500 dòng, xem KDoc lớp đó).
        // Bài canh đi theo VAI, nên nó quét đúng nơi vai ấy đang ở; tính chất được canh không đổi một chữ.
        val fn = SourceRoots.body(
            code("src/main/java/com/byd/clusternav/launcher/voice/VoiceFreeTail.kt"),
            "fun decode(",
        )
        assertTrue(
            fn.indexOf("VoiceOpenVocab.triggerOf(") in 0 until fn.indexOf("VoiceRecognizer.openFree("),
            "phải hỏi cụm kích hoạt TRƯỚC khi dựng bộ giải mã tự do — thứ tự này chính là cái cổng",
        )
        assertTrue(fn.contains("?: return plain"), "không có cụm kích hoạt ⇒ lùi về bản ngữ pháp, không chạy lượt 2")
        assertTrue(fn.contains("heard.pcm"), "lượt 2 chạy trên khúc PCM ĐÃ THU, không mở micro lần nữa")

        // Chỉ hai chỗ được gọi: phiên nghe thật và đường đo bằng WAV (phải đi cùng một con đường — R14).
        val users = voiceSources().filter { (_, src) -> src.contains("openFree(") }.map { it.first }.sorted()
        assertEquals(listOf("VoiceFreeTail.kt", "VoiceRecognizer.kt", "VoiceWavProbe.kt"), users,
            "bộ giải mã tự do bị gọi ở chỗ lạ: $users")
    }

    /**
     * **[SOÁT Pass 3 · P1] — cổng hỏi-lại VỀ MUỘN không được mở thêm một lượt nghe.**
     *
     * Tới 1.49 mọi lượt `confirm` xảy ra **đồng bộ** trong `execute`, tức chắc chắn còn trong phiên. V1.1 mở một
     * đường bất đồng bộ: câu dẫn đường tới app chỉ-nhận-toạ-độ đi tra cứu mạng (tới ~20 s với `HttpConn`) rồi
     * MỚI hỏi lại. Không có khoá thế hệ thì lượt về muộn sẽ đặt lại `pendingConfirm` của phiên đang chạy, vẽ câu
     * hỏi cũ đè lên tấm chữ mới, và mở `AudioRecord` **thứ hai** trong lúc micro phiên mới còn đang mở.
     */
    @Test
    fun `hoi lai ve muon sau khi phien da qua thi khong mo them luot nghe`() {
        val session = code("src/main/java/com/byd/clusternav/launcher/voice/VoiceSession.kt")
        val fn = SourceRoots.body(session, "internal fun execute(")
        assertTrue("val my = generation.get()" in fn, "phải ghim THẾ HỆ của phiên tại lúc thi hành")
        assertTrue(
            "if (stale(my) || overlay == null) onNo() else confirm(question, onYes, onNo)" in fn,
            "phiên đã qua / tấm chữ đã đóng ⇒ trả lời KHÔNG, tuyệt đối không mở thêm một lượt nghe xác nhận",
        )
    }

    /**
     * **OQ4 (voice pha 2) — ĐỌC XONG câu hỏi rồi mới mở micro, và hạn 4 giây phải là một lưới an toàn THẬT.**
     *
     * Hai tính chất, mỗi cái chặn một cách hỏng ngược nhau:
     *  • mở micro **trong** `onDone` — không thì Kachi nghe chính mình đọc câu hỏi (hazard đã ghi ở KDoc
     *    `VoiceSession.speakLines`), và cổng *"đồng ý"* nhận nhầm một lần là một cánh cửa mở giữa bãi đỗ;
     *  • vẫn mở micro khi **hết hạn** — CLAUDE.md §3: *"không gate một đường phục hồi bằng dữ liệu mà chỉ chính
     *    đường đó mới làm mới được"*. Engine đọc chết giữa chừng không được phép khoá cổng an toàn vĩnh viễn.
     */
    @Test
    fun `cau hoi xac nhan doc xong moi mo micro, va het han thi van mo`() {
        // ⚠ 1.66 — các lượt nghe NỐI của một phiên (hỏi lại R8 · hội thoại R9 · cổng xác nhận) đã rời sang
        // `VoiceSessionTurns.kt` dưới dạng hàm mở rộng của chính [VoiceSession] (trần 500 dòng, CLAUDE.md §4.1).
        val session = code("src/main/java/com/byd/clusternav/launcher/voice/VoiceSession.kt")
        val turns = code("src/main/java/com/byd/clusternav/launcher/voice/VoiceSessionTurns.kt")
        val fn = SourceRoots.body(turns, "internal fun VoiceSession.askAloudThenListen(")
        assertTrue(fn.contains("speaker.speak(question) { openMic() }"),
            "micro phải mở TRONG mốc 'đọc xong' của chính câu hỏi, không phải ngay sau khi xếp câu")
        assertTrue(fn.contains("ASK_ALOUD_CAP_MS"),
            "phải có hạn cứng — engine đọc treo không được khoá cổng xác nhận vĩnh viễn")
        assertTrue(fn.contains("!speakReplies() || !speaker.available()"),
            "tắt công tắc R4 / máy không có giọng ⇒ mở micro NGAY như 1.65, không chờ gì")
        assertTrue(fn.contains("listening.compareAndSet(false, true)") || fn.contains("!listening.compareAndSet"),
            "hai đường (đọc xong · hết hạn) có thể cùng về ⇒ phải chốt để chỉ mở ĐÚNG MỘT lượt nghe")
        // 1.70 — `confirm` tách sang `VoiceSessionTurns.kt` (extension VoiceSession) theo VAI, trần 500 dòng.
        val turnsSrc = code("src/main/java/com/byd/clusternav/launcher/voice/VoiceSessionTurns.kt")
        assertTrue(SourceRoots.body(turnsSrc, "internal fun VoiceSession.confirm(").contains("askAloudThenListen("),
            "cổng xác nhận phải đi qua đường đọc-rồi-nghe; gọi thẳng listenForConfirm là bỏ qua OQ4")
    }

    /**
     * **R4 (voice pha 2) — hai công tắc của đường ra tiếng phải THẬT SỰ gác, và gác đúng chỗ.**
     *
     * *"Đọc phản hồi"* tắt mà vẫn tổng hợp giọng là tốn hàng trăm ms CPU trên đầu xe cho một câu không ai nghe;
     * *"Ưu tiên giọng offline"* phải là **lambda** (đọc lại mỗi câu) chứ không phải một giá trị chụp lúc dựng
     * phiên — chụp một lần thì bật công tắc xong phải khởi động lại launcher mới thấy tác dụng.
     */
    @Test
    fun `hai cong tac doc phan hoi gac that`() {
        val session = code("src/main/java/com/byd/clusternav/launcher/voice/VoiceSession.kt")
        assertTrue(session.contains("preferOffline = { Prefs.voicePreferOffline(ctx) }"),
            "công tắc 'ưu tiên giọng offline' phải truyền dạng lambda để đọc lại ở MỖI câu")
        // 1.70 (voice-clone T7) — giọng phản hồi (Piper/giọng bé) cũng là lambda: đổi lựa chọn trong Cài đặt là
        // câu tiếp theo đã đi đường mới, không phải khởi động lại launcher.
        assertTrue(session.contains("feedbackVoice = { Prefs.voiceFeedbackVoice(ctx) }"),
            "giọng phản hồi phải truyền dạng lambda để đọc lại ở MỖI câu")
        // ⚠ 1.66: [speakLines] nay phải gọi `onDone` ở MỌI đường thoát (hội thoại R9 treo trên mốc đó), nên cổng
        // không còn là một `return` trần. Thứ phải canh vẫn y nguyên — **THỨ TỰ**: công tắc chặn TRƯỚC phép gộp.
        // 1.70: speakLines tách sang VoiceSessionTurns (extension) theo VAI, trần 500 dòng.
        val turns = code("src/main/java/com/byd/clusternav/launcher/voice/VoiceSessionTurns.kt")
        val body = SourceRoots.body(turns, "internal fun VoiceSession.speakLines(")
        val gate = body.indexOf("!speakReplies()")
        val merge = body.indexOf("VoiceFeedbackPhrase.merge(")
        assertTrue(gate in 0 until merge, "tắt 'Đọc phản hồi' phải chặn TRƯỚC khi gộp/tổng hợp câu, không phải sau")
        assertTrue(body.contains("onDone()"), "mọi đường thoát của speakLines phải gọi onDone — hội thoại chờ mốc đó")
        val settings = code("src/main/java/com/byd/clusternav/launcher/voice/VoiceModelSettings.kt")
        assertTrue(settings.contains("deps.bridge.setVoiceSpeakReplies(") &&
            settings.contains("deps.bridge.setVoicePreferOffline("),
            "hai công tắc phải có một hàng THẬT trong Cài đặt — khoá không ai chạm tới được là khoá chết")
    }

    /**
     * **T8 (voice pha 2) — gói giọng ĐỌC đi qua CHÍNH đường cài của gói NGHE, không phải một bản sao.**
     *
     * Chép `VoiceModelStore` thành `VoiceTtsStore` là nhân đôi bốn tính chất tinh tế (staging · băm trong lúc
     * tải · hỏng thì xoá · từ chối gói chưa ghim), và bản sao sẽ lệch ở đúng lần ai đó vá một tính chất.
     */
    @Test
    fun `goi giong doc dung chung duong cai voi goi nghe`() {
        val settings = code("src/main/java/com/byd/clusternav/launcher/voice/VoiceModelSettings.kt")
        assertTrue(settings.contains("VoiceModelStore.install(context, ttsPack)"),
            "gói ĐỌC phải gọi đúng `VoiceModelStore.install`, không dựng đường cài thứ hai")
        assertTrue(settings.contains("VoiceModelStore.remove(context, ttsPack)"), "phải có đường GỠ cho gói đọc")
        assertTrue(settings.contains("SherpaTtsCatalog.PIPER_VI_VAIS1000"),
            "gói đọc phải lấy từ danh mục `:core`, không viết cứng đường dẫn/URL ở tầng vẽ")
        val store = code("src/main/java/com/byd/clusternav/launcher/voice/VoiceModelStore.kt")
        assertTrue(store.contains("pack: VoicePack"), "đường cài phải nhận hợp đồng chung VoicePack")
        assertTrue(store.contains("target.parentFile?.mkdirs()"),
            "gói đọc mang cây thư mục nhiều tầng ⇒ phải tạo thư mục cha, không thì FileNotFoundException")
    }

    /**
     * **V1.1 (R17) — mọi ý-định giao cho app đích phải `setPackage`.**
     *
     * [ĐO] máy ảo 2026-09-14: cả Google Maps lẫn Waze đều bắt `geo:`, nên một ý-định trần bung hộp *"Open with"*.
     * Giữa lúc lái, một hộp chọn app còn tệ hơn không làm gì.
     */
    @Test
    fun `y dinh giao cho app dich luon co setPackage`() {
        val intents = code("src/main/java/com/byd/clusternav/launcher/voice/VoiceAppIntents.kt")
        val fn = SourceRoots.body(intents, "fun build(")
        assertTrue(fn.contains("setPackage(pkg)"), "nhánh action phải ghim gói")
        assertTrue(fn.contains(".setPackage(pkg)"), "nhánh URI phải ghim gói")
        assertTrue(intents.contains("FLAG_ACTIVITY_NEW_TASK"), "mở từ launcher cần NEW_TASK")
        assertFalse(intents.contains("CLEAR_TOP"),
            "[ĐO] VietMap là singleTask — CLEAR_TOP là đụng vào ngăn xếp app khác mà chưa kiểm được hậu quả")
        assertTrue(intents.contains("resolveActivity("), "hỏi trước khi bắn, để còn lùi sang đường dự phòng")
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
        assertTrue(store.contains("got.bytes != mf.bytes") && store.contains("got.sha256.equals(mf.sha256"),
            "MỖI tệp tải về phải qua CẢ hai phép kiểm (sha256 + cỡ) trước khi đặt vào chỗ")
        assertTrue(store.contains("requireSafe("),
            "tên MỖI tệp (từ mạng) phải qua luật chống leo thư mục (CLAUDE.md §4.1)")
        assertTrue(store.contains("fun remove("), "phải có đường GỠ — vài trăm MB không được ở lại vĩnh viễn")
        val settings = code("src/main/java/com/byd/clusternav/launcher/voice/VoiceModelSettings.kt")
        assertTrue(settings.contains("VoiceModelStore.install(context)"), "màn Cài đặt phải gọi đường cài THẬT")
        assertTrue(settings.contains("VoiceEngine.release()"),
            "gỡ phải trả mô hình khỏi bộ nhớ TRƯỚC khi xoá tệp, không thì mã native còn giữ bản cũ")
        assertTrue(
            code("src/main/java/com/byd/clusternav/launcher/SettingsSections.kt").contains("VoiceModelSettings(context, rows, deps).build(body)"),
            "hàng tải mô hình phải có mặt trong màn Cài đặt — một lớp không ai dựng là mã chết",
        )
    }

    /**
     * Đường **side-load** (xe không internet, owner 2026-09-15) phải CÒN ĐƯỢC GỌI và phải qua ĐÚNG phép kiểm của
     * đường mạng. Năm bài JVM của `VoiceModelSideloadTest` kiểm phần thuần — nhưng chúng vẫn XANH nếu một lần viết
     * lại `fetch`/`install` nuốt mất call site (đúng bệnh `CastShell.evictVd`, CLAUDE.md §8). Bài này khoá dây nối.
     */
    @Test
    fun `duong side-load duoc goi that va khong am tham roi ve mang`() {
        val store = code("src/main/java/com/byd/clusternav/launcher/voice/VoiceModelStore.kt")
        assertTrue(store.contains("VoiceModelSideload.candidate(importDir, safe)"),
            "install phải hỏi tệp side-load bằng ĐÚNG tên đã qua requireSafe, không phải tên thô")
        assertTrue(store.contains("VoiceModelSideload.IMPORT_SUBDIR") && store.contains("getExternalFilesDir"),
            "thư mục đặt tệp phải là thư mục ngoài của app (adb push / USB), không phải một chỗ cần quyền")
        assertTrue(store.contains("VoiceModelSideload.copyVerified("),
            "có tệp side-load ⇒ fetch phải đi đường chép-và-băm, không có nhánh nhận tệp mà không kiểm")
        val sideload = code("src/main/java/com/byd/clusternav/launcher/voice/VoiceModelSideload.kt")
        assertTrue(sideload.contains("got.bytes != expectedBytes") &&
            sideload.contains("got.sha256.equals(expectedSha256"),
            "tệp chép từ USB phải qua CẢ hai phép kiểm (sha256 + cỡ) y như tệp tải từ mạng")
        assertTrue(sideload.contains("out.delete()"),
            "tệp side-load sai phải bị XOÁ — không để lại tệp hỏng cho bước Verifying/onnxruntime")
        // Sai ⇒ báo thẳng. Nếu ai đó cho nó rơi về `download(...)` thì trên xe không mạng người chép USB sẽ chỉ
        // thấy "lỗi mạng" và không bao giờ biết tệp mình chép sai.
        assertFalse(sideload.contains("download("),
            "lớp side-load KHÔNG được biết tới đường mạng — sai là báo sai, không âm thầm rơi về mạng")
    }

    /** Phiên nghe có TRẦN thời gian, và hộp xác nhận mặc định là KHÔNG. */
    @Test
    fun `phien nghe co tran thoi gian va cong xac nhan mac dinh la KHONG`() {
        val session = code("src/main/java/com/byd/clusternav/launcher/voice/VoiceSession.kt")
        assertTrue(session.contains("MAX_LISTEN_MS = 8_000L"), "phải có trần cứng cho một phiên nghe")
        assertTrue(session.contains("capture.listen(it, MAX_LISTEN_MS"), "và trần đó phải được TRUYỀN vào vòng nghe")
        // ⚠ 1.66 — lượt nghe "đồng ý/huỷ" nằm ở `VoiceSessionTurns.kt` (xem chú thích ở bài OQ4 phía trên).
        val turns = code("src/main/java/com/byd/clusternav/launcher/voice/VoiceSessionTurns.kt")
        assertTrue(turns.contains("answerConfirm(answer == true)"),
            "nghe không ra `đồng ý` ⇒ KHÔNG. Im lặng không bao giờ được hiểu là đồng ý.")
        assertTrue(turns.contains("VoiceLexicon.confirmAnswer("),
            "câu trả lời có/không phải đọc bằng bảng ở `:core` (kiểm off-car), không bằng một `if` ở tầng vẽ")
        // 1.70 — vai tiêu điểm âm thanh tách khỏi VoiceCapture sang VoiceAudioFocus (trần 500 dòng).
        val audioFocus = code("src/main/java/com/byd/clusternav/launcher/voice/VoiceAudioFocus.kt")
        assertTrue(audioFocus.contains("AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK"),
            "chỉ HẠ tiếng nhạc, không dừng hẳn — xem KDoc VoiceAudioFocus")
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
