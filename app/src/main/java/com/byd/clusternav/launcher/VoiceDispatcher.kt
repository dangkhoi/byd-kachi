package com.byd.clusternav.launcher

import android.util.Log
import com.byd.clusternav.launcher.voice.VoiceIntent
import com.byd.clusternav.launcher.voice.VoiceIntentParser
import com.byd.clusternav.launcher.voice.VoiceMediaOp
import com.byd.clusternav.launcher.voice.VoiceReply
import com.byd.clusternav.launcher.voice.VoiceRisk
import com.byd.clusternav.launcher.voice.VoiceRiskTable
import com.byd.clusternav.navigation.NavApps

/**
 * ═══ V1 · TỪ Ý ĐỊNH TỚI **ĐƯỜNG ĐÃ CÓ** ═══════════════════════════════════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-voice-command.html` R6. Lớp này là **cầu**, không phải một tầng điều khiển thứ hai.
 *
 * ## Ràng buộc số một: KHÔNG mở đường thứ hai tới bất cứ thứ gì
 * Mỗi nhánh dưới đây đi đúng con đường mà một cú **chạm** đang đi hôm nay:
 *  • nút xe → [actByKind] + ghi lại vào [ControlTileState.shared] — y hệt `ControlTileFactory`;
 *  • gói lệnh → [MacroRunner] trên thread nền — y hệt `ControlTileFactory.macroTile`;
 *  • hành động launcher → hai lambda mà `KachiHomeWiring.controlDock` đã nối;
 *  • đổi hồ sơ → intent của `HomeViewModel` (tầng UI **0 lần** ghi bền — luật kiến trúc đang có);
 *  • nhạc → [MediaBridge]; mở app → [AppOpener].
 * Dựng một đường riêng cho giọng nói là cách chắc chắn để hai bề mặt lệch nhau (ô "Đèn đọc" vẫn sáng sau khi nói
 * *"tắt đèn đọc"*) — đúng lỗi mà `ControlTileState.shared` sinh ra để chặn.
 *
 * ## KHÔNG có mic, KHÔNG có ASR, KHÔNG có TTS ở đây (R8)
 * Vào là **chữ**, ra là **chữ**. Tầng tiếng chờ số đo trên xe (playbook §2.14 + K1–K3, CLAUDE.md §14). Nhờ ranh
 * giới đó, mọi thứ trong tệp này kiểm được off-car và sẽ **không phải viết lại** khi tầng tiếng bật lên.
 */
class VoiceDispatcher(
    private val control: () -> CarControlPort,
    private val state: () -> HomeUiState,
    private val media: () -> MediaBridge,
    /** Nhãn app → tên gói. Danh sách động (app đã cài) ⇒ KHÔNG gói nào bị viết cứng (CLAUDE.md §7). */
    private val appsByLabel: () -> Map<String, String>,
    private val openApp: (String) -> Boolean,
    private val openAppList: () -> Unit,
    private val openSettings: () -> Unit,
    private val onSwitchProfile: (String) -> Unit,
    /**
     * Hỏi lại trước khi bắn ([VoiceRisk.CONFIRM]): `(câu hỏi, đồng ý, huỷ)`.
     *
     * Phải gọi **đúng một** trong hai lambda — `onYes` khi người dùng đồng ý, `onNo` khi huỷ (hoặc đóng hộp).
     * Nuốt cả hai là treo phần còn lại của câu ghép vĩnh viễn (xem KDoc [submit]).
     */
    private val confirm: (String, () -> Unit, () -> Unit) -> Unit,
    /** Nói một câu cho người dùng (hôm nay: hiện chữ). */
    private val say: (String) -> Unit,
    /** Chạy một việc dài trên thread NỀN (gói lệnh) — tách ra để test/đo được, mặc định là một Thread. */
    private val background: (() -> Unit) -> Unit = { block -> Thread(block, "KachiVoice").start() },
) {

    /**
     * Nhận một câu, phân tích, rồi thi hành **theo đúng thứ tự nói** (R3).
     *
     * ## [SOÁT P0] Câu ghép có một vế phải hỏi lại thì **cả chuỗi dừng ở đó** (spec §7 OQ4, quyết theo hướng an toàn)
     * Bản đầu bắn tiếp các vế sau **ngay trong lúc hộp hỏi còn đang mở**, mượn luật `MacroRunner` (*"một bước hỏng
     * không giết cả gói"*). Hai thứ đó không cùng một bài toán: các bước trong một gói lệnh đã được **owner duyệt
     * sẵn** khi khai gói, còn các vế của một câu ghép là thứ vừa **nghe được** — mà vế phải hỏi lại đứng đó chính
     * vì có thể đã nghe nhầm. *"Mở khoá cửa rồi mở hết kính"* nghe nhầm một lần là xe **mở toang** trong lúc người
     * lái mới đang đọc câu hỏi cho vế đầu. Thứ tự nói cũng là thứ tự nhân quả: chạy vế sau trước khi vế trước được
     * đồng ý là đảo nhân quả.
     *
     * ⇒ Gặp vế [VoiceRisk.CONFIRM]: hỏi, **không chạy gì thêm**; đồng ý ⇒ chạy vế đó rồi đi tiếp; huỷ ⇒ dừng hẳn
     * và **nói ra** còn mấy vế không chạy ([VoiceReply.cancelled]) — im lặng thì người ta tưởng nửa sau đã chạy.
     */
    fun submit(text: String) = execute(preview(text))

    /**
     * Thi hành một danh sách ý định đã phân tích.
     *
     * Tách khỏi [submit] để màn thử hiện *"đã hiểu là…"* rồi chạy **CHÍNH** danh sách vừa hiện — chứ không phân
     * tích lần thứ hai. Hai lần phân tích là hai kết quả có thể lệch (danh sách app/hồ sơ đổi giữa hai lần), tức
     * màn hình nói một đằng và xe làm một nẻo; và nó cũng nhân đôi công vô ích trên thread giao diện.
     */
    fun execute(intents: List<VoiceIntent>) = runFrom(intents, 0, appsByLabel())

    /** Chạy từ vế [from] tới hết, DỪNG tại vế đầu tiên phải hỏi lại. */
    private fun runFrom(intents: List<VoiceIntent>, from: Int, labels: Map<String, String>) {
        var i = from
        while (i < intents.size) {
            val intent = intents[i]
            if (VoiceRiskTable.of(intent) == VoiceRisk.CONFIRM) {
                val next = i + 1
                val remaining = intents.size - next
                confirm(
                    VoiceReply.confirmQuestion(intent),
                    { run(intent, labels); runFrom(intents, next, labels) },
                    { say(VoiceReply.cancelled(intent, remaining)) },
                )
                return
            }
            run(intent, labels)
            i++
        }
    }

    /** Phân tích **không thi hành** — để màn thử hiện "đã hiểu là…" trước khi người dùng bấm chạy. */
    fun preview(text: String): List<VoiceIntent> = parse(text, appsByLabel())

    /**
     * Cửa DUY NHẤT của `:app` vào bộ phân tích.
     *
     * Hai chỗ gọi thẳng [VoiceIntentParser.parse] là hai bộ tham số có thể lệch (danh sách hồ sơ, danh sách app),
     * tức *"đã hiểu là…"* hiện một đằng mà thi hành một nẻo — thứ khó lần ra nhất vì màn hình nói nó hiểu đúng.
     */
    private fun parse(text: String, labels: Map<String, String>): List<VoiceIntent> =
        VoiceIntentParser.parse(text, state().profiles, labels.keys.toList())

    // ── Thi hành ─────────────────────────────────────────────────────────────────────────────────

    private fun run(intent: VoiceIntent, labels: Map<String, String>) {
        when (intent) {
            is VoiceIntent.Control -> runControl(intent)
            is VoiceIntent.Macro -> runMacro(intent)
            is VoiceIntent.Launcher -> runLauncher(intent)
            is VoiceIntent.Profile -> { onSwitchProfile(intent.name); say(VoiceReply.done(intent)) }
            is VoiceIntent.Read -> runRead(intent)
            is VoiceIntent.Nav -> runNav(intent, labels)
            is VoiceIntent.Media -> runMedia(intent)
            is VoiceIntent.OpenApp -> runOpenApp(intent, labels)
            is VoiceIntent.Unknown -> say(VoiceReply.unknown(intent))
        }
    }

    /**
     * Một nút.
     *
     * Lệnh **tương đối** (*"tăng gió"*) được quy về tuyệt đối **ở đây**, bằng mức đang hiển thị trong
     * [ControlTileState.shared] — đúng bảng mà thanh nút và ô giữa màn đang đọc. `:core` cố ý không làm việc này
     * (xem KDoc [VoiceIntent.Control.relative]): nó không biết xe đang ở mức nào.
     */
    private fun runControl(i: VoiceIntent.Control) {
        val def = ControlRegistry.byId(i.id)
        if (def == null) { say(VoiceReply.failed(i)); return }
        val st = ControlTileState.shared
        val arg = when {
            i.relative != 0 -> def.clamp(st.value(def) + i.relative * def.step)
            else -> i.value ?: 1
        }
        val ok = runCatching { control().actByKind(def.id, arg) }.getOrDefault(false)
        // Ghi lại trạng thái lạc quan y như cú chạm: hai bề mặt phải nói cùng một điều về MỘT cái xe.
        if (ok) when (def.kind) {
            ControlKind.TOGGLE -> st.setOn(def.id, arg > 0)
            ControlKind.STEP -> st.setValue(def.id, arg)
            ControlKind.SELECT -> st.setSel(def.id, arg)
            else -> Unit
        }
        val shown = if (i.relative != 0) VoiceIntent.Control(def.id, arg) else i
        say(if (ok) VoiceReply.done(shown) else VoiceReply.failed(shown))
    }

    private fun runMacro(i: VoiceIntent.Macro) {
        val macro = ActionMacros.byId(i.id)
        if (macro == null) { say(VoiceReply.failed(i)); return }
        if (!ControlTileState.shared.beginRun(macro.id)) {
            say(VoiceReply.busy(i))
            return
        }
        val port = control()
        background {
            try {
                val res = MacroRunner.run(
                    macro,
                    emit = { id, arg -> runCatching { port.actByKind(id, arg) }.getOrDefault(false) },
                    sleep = { ms -> runCatching { Thread.sleep(ms) } },
                )
                res.results.forEach { r ->
                    if (r.ok && ControlRegistry.byId(r.controlId)?.kind == ControlKind.TOGGLE) {
                        ControlTileState.shared.setOn(r.controlId, macro.steps.first { it.controlId == r.controlId }.arg > 0)
                    }
                }
                say(res.notice(macro.displayLabel) ?: VoiceReply.done(i))
            } catch (t: Throwable) {
                Log.w(TAG, "gói ${macro.id} hỏng giữa lượt chạy", t)
                say(VoiceReply.failed(i))
            } finally {
                ControlTileState.shared.endRun(macro.id)
            }
        }
    }

    private fun runLauncher(i: VoiceIntent.Launcher) {
        when (i.id) {
            LauncherActions.APPS -> openAppList()
            LauncherActions.SETTINGS -> openSettings()
            // Mã launcher tương lai mà bản này chưa biết: im lặng mở nhầm một màn còn tệ hơn nói thẳng là chưa có.
            else -> { say(VoiceReply.failed(i)); return }
        }
        say(VoiceReply.done(i))
    }

    private fun runRead(i: VoiceIntent.Read) {
        val spec = TelemetryRegistry.byId(i.datumId)
        val view = TelemetryReadout.of(i.datumId, state().carStatus)
        val value = view?.displayWithUnit()
        say(
            when {
                spec == null -> VoiceReply.failed(i)
                value.isNullOrBlank() || value == NO_VALUE -> VoiceReply.noReading(spec.displayLabel)
                else -> spec.displayLabel + ": " + value
            },
        )
    }

    /**
     * Dẫn đường — **mở app, KHÔNG hứa chuyển điểm đến**.
     *
     * [ĐO] `docs/diagnostics/kiki-car-RE-2026-09-14.md` §8.2: điểm đến là **từ vựng mở**, phần việc đã chốt cho
     * Kiki (phương án C). Đường đẩy một câu chữ sang Kiki (`text_command`) mới ở mức **[SUY]** và phải chốt bằng
     * phép đo **K2 trên xe** — CLAUDE.md §14 cấm viết code cho một khả năng chưa có bằng chứng shell. Nên ở đây ta
     * làm đúng phần chắc chắn: mở app dẫn đường đang có, và **nói rõ** phần chưa làm được thay vì im lặng.
     */
    private fun runNav(i: VoiceIntent.Nav, labels: Map<String, String>) {
        val installed = labels.values.toSet()
        val pkg = NAV_PREFERENCE.firstOrNull { it in installed }
        if (pkg == null || !openApp(pkg)) {
            say(VoiceReply.noNavApp(i))
            return
        }
        say(VoiceReply.navOpenedWithoutDestination(i))
    }

    /**
     * Nhạc.
     *
     * ## [SOÁT P1] Vì sao phải đọc kết quả của transport, không bắn rồi báo "✓"
     * [MediaBridge] chỉ điều khiển được **phiên đang hoạt động** mà nó đã thấy; chưa ai thấy phiên nào thì mọi lệnh
     * transport là no-op **im lặng** (KDoc `MediaBridge`: degrade-safe). Bản đầu bắn xong báo `✓ Phát nhạc` bất kể
     * có phiên hay không — tức nói dối đúng cái ca hay gặp nhất (chưa cấp quyền notification-listener, hoặc chưa
     * app nhạc nào mở). Nay `play/pause/next/prev` trả `Boolean`, và câu trả lời nói đúng thứ đã xảy ra.
     */
    private fun runMedia(i: VoiceIntent.Media) {
        // Tên bài / ca sĩ / thể loại = từ vựng MỞ. Nói thẳng là không làm offline, KHÔNG đoán bừa một bài.
        if (i.op == VoiceMediaOp.QUERY) { say(VoiceReply.openVocabMedia(i)); return }
        val bridge = media()
        val ok = when (i.op) {
            VoiceMediaOp.PLAY -> bridge.play()
            VoiceMediaOp.PAUSE -> bridge.pause()
            VoiceMediaOp.NEXT -> bridge.next()
            VoiceMediaOp.PREV -> bridge.prev()
            VoiceMediaOp.QUERY -> false
        }
        say(if (ok) VoiceReply.done(i) else VoiceReply.noMediaSession(i))
    }

    private fun runOpenApp(i: VoiceIntent.OpenApp, labels: Map<String, String>) {
        val pkg = labels[i.appName]
        val ok = pkg != null && openApp(pkg)
        say(if (ok) VoiceReply.done(i) else VoiceReply.cannotOpen(i))
    }

    private companion object {
        const val TAG = "KachiVoice"

        /** Chuỗi `TelemetryReadout` trả về khi xe chưa có số — cùng ký hiệu mà ô đọc đang vẽ. */
        const val NO_VALUE = "—"

        /**
         * Thứ tự ƯU TIÊN app dẫn đường — **danh sách roster dùng chung** ở `:core`, không phải tên gói viết cứng
         * mới. VietMap đứng đầu vì đó là app đang nuôi badge tốc độ trên xe owner; Google Maps rồi Waze theo sau.
         */
        val NAV_PREFERENCE: List<String> =
            listOf(NavApps.VIETMAP_LIVE) + NavApps.GMAPS.toList() + NavApps.WAZE.toList()
    }
}
