package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import java.nio.file.Files
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ V1 · DÂY NỐI CỦA ĐƯỜNG LỆNH BẰNG CHỮ ═════════════════════════════════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-voice-command.html` R6 · R8. Quét SOURCE vì dự án không dựng Activity/View trong JVM
 * thuần (không Robolectric) — cùng lệ [SettingsScreenWiringContractTest], và mọi phép cắt vùng đi qua
 * [SourceRoots.body] (nó **nổ** nếu mốc không còn, thay vì âm thầm quét tới hết tệp).
 *
 * ## Bài THẬT ở đây là hai bài
 *  • *"mỗi nhánh ý định có một đích thật"* — chống đúng bệnh `CastShell.evictVd` (CLAUDE.md §8): một bộ phân
 *    tích 194 mã compile sạch mà **không ai gọi**, hoặc gọi tới một nhánh rỗng.
 *  • *"chưa có mic/ASR/TTS"* — R8 là một **cam kết về phạm vi**, không phải một câu trong tài liệu. Ngày ai đó
 *    thêm `SpeechRecognizer` mà chưa có số đo trên xe (CLAUDE.md §14), bài này đỏ.
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

    // ══ (3) R8 — CHƯA có mic / ASR / TTS / wake word ══════════════════════════════════════════════════════

    /**
     * CLAUDE.md §14: tính năng mới phải đi 4 tầng, tầng 1 là **phép đo shell thật trên xe**. Với mic/ASR/TTS,
     * tầng 1 còn 🔲 (playbook §2.14 + K1–K3). Bài này là cái chốt cửa: viết code cho tầng tiếng trước khi có số
     * đo ⇒ đỏ, kèm đúng câu nhắc phải đo gì.
     */
    @Test
    fun `chua co mic ASR TTS wake word trong pha nay`() {
        val banned = listOf(
            "SpeechRecognizer", "RecognitionListener", "TextToSpeech", "AudioRecord", "MediaRecorder",
            "RECORD_AUDIO", "VoiceInteractionService", "text_command",
        )
        val voiceSources = SourceRoots.moduleSourceRoots().flatMap { root ->
            root.toFile().walkTopDown()
                .filter { it.isFile && it.name.endsWith(".kt") && it.name.startsWith("Voice") }
                .map { it.name to it.readText() }.toList()
        }
        assertTrue(voiceSources.size >= 7, "không tìm thấy đủ tệp Voice*.kt để quét; thấy: ${voiceSources.map { it.first }}")
        voiceSources.forEach { (name, src) ->
            banned.forEach { token ->
                assertFalse(
                    Regex("""(?<![A-Za-z])${Regex.escape(token)}\s*[(.\[]""").containsMatchIn(src),
                    "$name gọi `$token` — pha off-car KHÔNG được có tầng tiếng (R8). Đo playbook §2.14 + K1–K3 " +
                        "trên xe trước (CLAUDE.md §14), rồi mới mở phạm vi trong spec.",
                )
            }
        }
    }

    /** Và không xin thêm quyền nào cho V1 — `RECORD_AUDIO` vào manifest là dấu hiệu tầng tiếng đã lẻn vào. */
    @Test
    fun `manifest khong xin quyen ghi am`() {
        val manifest = SourceRoots.path("src/main/AndroidManifest.xml")
        val text = Files.readString(manifest)
        assertFalse(text.contains("android.permission.RECORD_AUDIO"), "V1 pha off-car không xin quyền mic")
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
