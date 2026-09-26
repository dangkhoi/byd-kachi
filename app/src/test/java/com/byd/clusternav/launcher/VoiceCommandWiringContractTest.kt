package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ V1 · DÂY NỐI CỦA ĐƯỜNG LỆNH — CHỮ (R6) **VÀ TIẾNG** (R9–R14) · vai DÂY-NỐI-Ý-ĐỊNH ═════════════════════════
 *
 * Spec `docs/specs/kachi-voice-command.html` R6 · R9–R14. Quét SOURCE vì dự án không dựng Activity/View trong JVM
 * thuần (không Robolectric) — cùng lệ [SettingsScreenWiringContractTest], và mọi phép cắt vùng đi qua
 * [SourceRoots.body] (nó **nổ** nếu mốc không còn, thay vì âm thầm quét tới hết tệp).
 *
 * ## DEBT-500-TEST (2026-09-26): tệp 761 dòng tách theo VAI, **di chuyển nguyên văn, không nới assert**
 *  • tệp này — *"mỗi nhánh ý định có một đích thật"* (chống bệnh `CastShell.evictVd`, CLAUDE.md §8: một bộ phân tích
 *    194 mã compile sạch mà không ai gọi) + cổng vào bằng chữ + ý-định giao app đích + bộ phân tích không mọc bản sao;
 *  • [VoiceListenWiringContractTest] — tầng NGHE: phạm vi R13 (tại máy, không ra mạng), micro, mô hình, ba lối vào, phiên;
 *  • [VoiceSpeakWiringContractTest] — tầng NÓI: một cửa ra tiếng, đọc-xong-mới-mở-mic, hai công tắc, gói giọng đọc.
 */
class VoiceCommandWiringContractTest {

    private fun code(relative: String): String = SourceRoots.codeOf(relative)

    /**
     * **Bộ dây của cầu giọng nói**, không phải *một tệp*.
     *
     * Từ voice pha 2 (2026-09-16) vai *"giao chữ/toạ độ cho app đích"* nằm ở `VoiceTargetDispatch.kt` (tách vì trần 500 dòng — xem
     * KDoc lớp đó), và từ lượt E (2026-09-19) vai *"ghi xong thì đọc lại xe rồi mới nói"* nằm ở `VoiceReadback.kt`
     * (cùng lý do). Bài này canh **dây nối**, nên phạm vi quét phải đi theo vai chứ không theo tên tệp: ghim một
     * tệp là biến mọi lượt tách tệp hợp lệ thành một lượt đỏ giả, và cách chữa đỏ giả ấy thường là gỡ assert.
     */
    private val dispatcher by lazy {
        code("src/main/java/com/byd/clusternav/launcher/VoiceDispatcher.kt") + "\n" +
            code("src/main/java/com/byd/clusternav/launcher/VoiceTargetDispatch.kt") + "\n" +
            code("src/main/java/com/byd/clusternav/launcher/VoiceReadback.kt")
    }
    private val console by lazy { code("src/main/java/com/byd/clusternav/launcher/VoiceTextConsole.kt") }
    private val sections by lazy { code("src/main/java/com/byd/clusternav/launcher/SettingsSections.kt") }
    private val panels by lazy { code("src/main/java/com/byd/clusternav/launcher/HomePanels.kt") }
    private val wiring by lazy { code("src/main/java/com/byd/clusternav/launcher/KachiHomeWiring.kt") }
    private val activity by lazy { code("src/main/java/com/byd/clusternav/launcher/KachiHomeActivity.kt") }

    // ══ (1) MỌI nhánh ý định có đích thật ══════════════════════════════════════════════════════════════════

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

    /**
     * Hàng *Gõ lệnh chữ* đã **GỠ** khỏi *Cài đặt › Hệ thống › Nâng cao* (owner 2026-09-21, bản release production:
     * dọn hết dev/debug UI, chỉ giữ công tắc *Chế độ kiểm thử qua adb*).
     *
     * Bài cũ ghim chiều ngược lại. Nó **đảo chiều** thay vì bị xoá vì `VoiceTextConsole` còn trong cây nguồn (owner
     * giữ lại), nên không có gì ngăn nó được `build(body)` lại ở một lượt sửa sau — mà trên xe đó là một ô nhập chữ
     * giữa màn của người đang lái. Bề mặt rộng hơn (cả năm thứ đã gỡ) do `DevSurfaceGateContractTest` canh.
     */
    @Test
    fun `hang go lenh chu khong con trong Cai dat`() {
        assertFalse(
            sections.contains("VoiceTextConsole("),
            "ô gõ lệnh chữ được dựng lại trong Cài đặt — owner chốt gỡ mọi bề mặt dev khỏi màn",
        )
    }

    /** Nửa còn lại: gỡ BỀ MẶT không được gỡ theo KHẢ NĂNG — `say` vẫn đi đúng đường của console cũ. */
    @Test
    fun `duong go lenh chu chi con di qua cau kiem thu`() {
        val bridge = code("src/main/java/com/byd/clusternav/launcher/testbridge/KachiTestBridge.kt")
        assertTrue(bridge.contains("TestBridgeCommands.SAY -> runSay("), "receiver phải còn điều phối `say`")
        val runSay = SourceRoots.body(bridge, "private fun runSay(")
        // Cùng cặp `preview` → `execute` mà `VoiceTextConsole` gọi: phân tích MỘT lần rồi thi hành chính danh sách
        // vừa phân tích. Dựng đường thứ hai ở đây là hai bộ hiểu câu lệch nhau âm thầm.
        assertTrue(runSay.contains("dispatcher.preview("), "`say` phải phân tích qua chính VoiceDispatcher")
        assertTrue(runSay.contains("dispatcher.execute("), "…rồi thi hành chính danh sách vừa phân tích")
        assertTrue(console.contains("dispatcher.preview(") && console.contains("dispatcher.execute("),
            "console còn trong cây nguồn thì nó phải vẫn là cùng một cặp — bài trên so với nó")
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
