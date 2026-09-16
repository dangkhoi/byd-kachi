package com.byd.clusternav.launcher

import android.util.Log
import com.byd.clusternav.launcher.voice.VoiceIntent
import com.byd.clusternav.launcher.voice.VoiceIntentParser
import com.byd.clusternav.launcher.voice.VoiceReply
import com.byd.clusternav.launcher.voice.VoiceAppIntents
import com.byd.clusternav.launcher.voice.VoiceAppTargets
import com.byd.clusternav.launcher.voice.VoicePlaces
import com.byd.clusternav.launcher.voice.VoiceRisk
import com.byd.clusternav.launcher.voice.VoiceRiskTable

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
 * ## Vào là **CHỮ**, và pha NGHE không đổi điều đó (R9–R14)
 * Từ 1.49 Kachi đã nghe được (`launcher/voice/VoiceSession`), nhưng ranh giới giữ nguyên: micro và bộ nhận dạng
 * nằm **trên** lớp này và chỉ đưa xuống một chuỗi chữ — đúng chuỗi mà ô *"Gõ lệnh chữ"* đưa xuống. Nhờ vậy lời
 * hứa cũ thành hiện thực đúng như đã viết: tầng tiếng bật lên mà **không một dòng nào** trong tệp này phải viết
 * lại, và mọi bài kiểm của nó vẫn chạy off-car.
 *
 * Ra vẫn là **chữ + âm báo**, chưa có TTS: giọng nói tiếng Việt tại máy còn [CHƯA BIẾT] trên xe này (spec §4.4).
 * [VoiceIntent.Read.aloud] vẫn giữ sẵn ý định *"đọc to"* cho ngày đo xong.
 */
class VoiceDispatcher(
    private val control: () -> CarControlPort,
    private val state: () -> HomeUiState,
    private val media: () -> MediaTransport,
    /** Nhãn app → tên gói. Danh sách động (app đã cài) ⇒ KHÔNG gói nào bị viết cứng (CLAUDE.md §7). */
    private val appsByLabel: () -> Map<String, String>,
    private val openApp: (String) -> Boolean,
    private val openAppList: () -> Unit,
    private val openSettings: () -> Unit,
    private val onSwitchProfile: (String) -> Unit,
    /**
     * V1 pha NGHE — mở một **phiên nghe** ([LauncherActions.VOICE]).
     *
     * ⚠ KHÔNG có giá trị mặc định, có chủ ý: mã `launcher_voice` đặt được lên thanh nút như mọi khả năng khác,
     * nên một chỗ gọi quên nối sẽ cho ra một ô **bấm không ra gì** — đúng hình dạng `CastShell.evictVd` mà
     * CLAUDE.md §8 nói tới, chỉ khác là lần này người dùng thấy nó trên màn hình. Bắt buộc truyền thì chỗ quên
     * **không biên dịch được**.
     *
     * Từ trong một phiên nghe mà lại nói *"nói với xe"* thì đây là đường mở phiên tiếp theo; `VoiceSession` tự
     * chặn phiên chồng phiên bằng chốt `running`, nên chỗ này không cần biết gì về điều đó.
     */
    private val onListen: () -> Unit,
    /**
     * Hỏi lại trước khi bắn ([VoiceRisk.CONFIRM]): `(câu hỏi, đồng ý, huỷ)`.
     *
     * Phải gọi **đúng một** trong hai lambda — `onYes` khi người dùng đồng ý, `onNo` khi huỷ (hoặc đóng hộp).
     * Nuốt cả hai là treo phần còn lại của câu ghép vĩnh viễn (xem KDoc [submit]).
     */
    private val confirm: (String, () -> Unit, () -> Unit) -> Unit,
    /**
     * V3 · R7 — tập mã việc **đang được bật** để hỏi lại (`voice_confirm_ids`, mặc định RỖNG).
     *
     * Là **lambda** chứ không phải một giá trị: người dùng tích một ô trong Cài đặt rồi nói ngay câu sau, và
     * `VoiceDispatcher` được dựng lại cho MỖI lượt nói nhưng cũng sống qua một câu ghép có hộp hỏi ở giữa. Đọc
     * lại ở mỗi vế là cách duy nhất không giữ một bản chụp cũ — cùng lẽ với `appsByLabel`/`state`.
     *
     * Mặc định rỗng để mọi chỗ gọi trong bài test (và cầu kiểm thử) giữ nguyên nghĩa *"không hỏi gì cả"*, đúng
     * mặc định owner chốt.
     */
    private val confirmIds: () -> Set<String> = { emptySet() },
    /** Nói một câu cho người dùng (hôm nay: hiện chữ). */
    private val say: (String) -> Unit,
    /**
     * V1.1 — gắn một app vào **ô số [slot]** (0-based). `true` = đã gắn.
     *
     * ⚠ Phải là **chính** lambda mà ngăn kéo dùng khi người ta chọn app cho một ô (`KachiHomeSlots.assignApp`),
     * không phải một `viewModel.assignApp` gọi thẳng: đường của ngăn kéo còn làm hai việc nữa mà state không
     * làm hộ được — gỡ app cũ khỏi sổ vị trí (`windowDispatcher.remove`) và đặt cửa sổ app mới vào đúng khung ô
     * (`LauncherWindows.placeApp`). Bỏ một trong hai là lỗi *"ô thay app khác mà app cũ còn nguyên trong sổ vị
     * trí"* đã có thật (xem KDoc `KachiHomeSlots`).
     */
    private val assignAppToSlot: (Int, String) -> Boolean,
    /**
     * L7 — đổi **bố cục màn chính**. `true` = đã đổi.
     *
     * Mặc định **từ chối** (trả `false`), cùng lẽ với [assignAppToSlot]: bề mặt nào không nối được đường bố cục
     * thì phải NÓI RA ([VoiceReply.layoutNotHere]) chứ không được lặng lẽ báo ✓ cho một việc chưa xảy ra.
     *
     * ⚠ Phải là **chính** đường mà chip bố cục ở Cài đặt dùng (`KachiHomeActivity.selectPreset` →
     * `HomeViewModel.setPreset`), không phải một `viewModel.setPreset` gọi thẳng: đường kia còn **bỏ bố cục tự
     * vẽ** trước khi đặt preset — thiếu bước đó thì người dùng nói *"bố cục 4 ô"*, state đổi, mà **màn hình
     * không đổi gì** (bố cục tự vẽ vẫn thắng). Lỗi ấy đã có thật một lần, xem KDoc `selectPreset` ([ĐO] P9).
     */
    private val onLayout: (LayoutPreset) -> Boolean = { false },
    /** V1.1 — giao một chuỗi chữ/toạ độ cho app đích ([VoiceAppIntents.send]). `true` = đã bắn đi được. */
    private val sendToApp: (VoiceAppIntents.Handoff) -> Boolean,
    /** V1.1 — tên địa điểm → toạ độ; **CHẶN** ⇒ lớp này luôn gọi trong [background]. `null` = không giải được. */
    private val geocode: (String) -> VoiceAppIntents.Coords?,
    /** V1.1 — gói của phiên nhạc ĐANG chạy (`MediaBridge.activePackage`), `null` khi chưa có phiên nào. */
    private val mediaPackage: () -> String?,
    /**
     * V1.1 — đẩy một việc về luồng VẼ. Cần vì đường điểm-đến-cần-toạ-độ phải: chạy nền (mạng) → **hỏi lại**
     * (hộp thoại/tấm chữ) → bắn ý-định (`startActivity`). Hai việc sau chỉ làm được trên luồng vẽ.
     *
     * Mặc định chạy thẳng: bài kiểm thuần cần thứ tự tất định, và nó không có `Looper` nào.
     */
    private val onUi: (() -> Unit) -> Unit = { it() },
    /** Chạy một việc dài trên thread NỀN (gói lệnh) — tách ra để test/đo được, mặc định là một Thread. */
    private val background: (() -> Unit) -> Unit = { block -> Thread(block, "KachiVoice").start() },
) {

    /**
     * Vai *"giao chữ/toạ độ cho một app ngoài"* — tách tệp ở voice pha 2 (2026-09-16) vì trần 500 dòng, xem KDoc
     * [VoiceTargetDispatch]. Dựng **một lần** cho cả đời cầu: nó chỉ cầm chính những lambda mà cầu này đã cầm,
     * nên dựng lại mỗi câu là công vô ích trên luồng vẽ.
     */
    private val targets = VoiceTargetDispatch(
        state = state,
        media = media,
        openApp = openApp,
        confirm = confirm,
        say = say,
        sendToApp = sendToApp,
        geocode = geocode,
        mediaPackage = mediaPackage,
        onUi = onUi,
        background = background,
    )

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
            if (VoiceRiskTable.of(intent, confirmIds()) == VoiceRisk.CONFIRM) {
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
        VoiceIntentParser.parse(
            text,
            state().profiles,
            labels.keys.toList(),
            // Sổ địa chỉ của hồ sơ ĐANG dùng (spec `kachi-voice-addresses.html` R2). Đọc từ state — cùng giá trị
            // mà bảng Cài đặt đang vẽ; mở một cửa `WorkspacePrefs` thứ hai ở đây là dựng đường đọc bền song song
            // ([SOÁT P1-1]), và hai đường thì màn hình hiện một sổ còn câu *"về nhà"* đi theo sổ khác.
            VoicePlaces.labelsOf(state().savedPlaces),
        )

    // ── Thi hành ─────────────────────────────────────────────────────────────────────────────────

    private fun run(intent: VoiceIntent, labels: Map<String, String>) {
        when (intent) {
            is VoiceIntent.Control -> runControl(intent)
            is VoiceIntent.Macro -> runMacro(intent)
            is VoiceIntent.Launcher -> runLauncher(intent)
            is VoiceIntent.Profile -> { onSwitchProfile(intent.name); say(VoiceReply.done(intent)) }
            is VoiceIntent.Read -> runRead(intent)
            is VoiceIntent.Nav -> targets.runNav(intent, labels)
            is VoiceIntent.NavigateSaved -> targets.runNavSaved(intent, labels)
            is VoiceIntent.Media -> targets.runMedia(intent, labels)
            is VoiceIntent.OpenApp -> runOpenApp(intent, labels)
            // L7 — một lambda, không có tầng logic nào ở đây: bố cục là state của màn chính, và `HomeViewModel`
            // đã là nơi ghi bền DUY NHẤT của nó.
            is VoiceIntent.Layout ->
                say(if (runCatching { onLayout(intent.preset) }.getOrDefault(false)) {
                    VoiceReply.done(intent)
                } else {
                    VoiceReply.layoutNotHere(intent)
                })
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
        if (!ok) { say(VoiceReply.failed(shown)); return }
        if (def.kind == ControlKind.STEP) sayStepResult(shown, st) else say(VoiceReply.done(shown))
    }

    /**
     * ═══ R5 · ĐỌC LẠI GIÁ TRỊ THẬT trước khi nói *"xong"* (spec `kachi-voice-feedback.html` T10) ═══════════
     *
     * Tới 1.65 câu trả lời dựng từ **con số vừa gửi**, nên *"đặt nhiệt độ 24"* trên một chiếc xe kẹp về 17 vẫn
     * nghe là *"✓ Đặt Nhiệt độ = 24"*. Nay: ghi xong thì hỏi lại xe ([CarControlPort.readStep]).
     *
     * ## Ba nhánh, và nhánh thứ ba là chỗ khó
     *  1. **Không đọc được** (`null` — off-car, máy ảo, trim không provision) ⇒ giữ nguyên câu cũ. **Không bịa
     *     số**: một con số đọc được là một lời hứa, còn `null` thì không có gì để hứa.
     *  2. **Khớp** ⇒ [VoiceReply.doneActual] trả đúng câu cũ (xem KDoc ở đó về vì sao không thêm chữ nào).
     *  3. **Lệch** ⇒ [ĐO chưa có, xem OQ5] có thể xe **chưa kịp áp**: lượt đọc chạy vài ms sau lượt ghi và bus
     *     còn mang số cũ. Nói ngay *"xe báo 23"* trong ca đó là báo một cái sai. ⇒ đọc lại **đúng MỘT lần** sau
     *     [READBACK_SETTLE_MS] trên luồng NỀN (không chặn luồng vẽ — xe đang chạy), rồi mới nói. Một lần, không
     *     phải một vòng lặp: nếu sau chừng ấy vẫn lệch thì đó là chỗ lệch THẬT, và người lái cần nghe nó.
     *
     * Trạng thái ô ([ControlTileState]) cũng được sửa theo số thật, để thanh nút và câu nói không nói hai điều
     * khác nhau về một cái xe — đúng bất biến mà `ControlTileState.shared` sinh ra để giữ.
     */
    private fun sayStepResult(shown: VoiceIntent.Control, st: ControlTileState) {
        val port = control()
        // Câu không nêu đích (`value == null`) thì không có gì để so — giữ nguyên câu cũ. Bộ phân tích không
        // sinh ra ca này cho STEP (xem `VoiceIntentParser.step`), nhưng một nhánh mới mai sau thì có thể.
        val want = shown.value ?: run { say(VoiceReply.done(shown)); return }
        val first = runCatching { port.readStep(shown.id) }.getOrNull()
        if (first == null || first == want) {
            say(if (first == null) VoiceReply.done(shown) else VoiceReply.doneActual(shown, first))
            return
        }
        background {
            runCatching { Thread.sleep(READBACK_SETTLE_MS) }
            val again = runCatching { port.readStep(shown.id) }.getOrNull() ?: first
            onUi {
                st.setValue(shown.id, again)
                say(VoiceReply.doneActual(shown, again))
            }
        }
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
            LauncherActions.VOICE -> onListen()
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

    // ══ V1.1 · TỪ VỰNG MỞ → APP ĐÍCH — đã tách sang [VoiceTargetDispatch] (trần 500 dòng) ═══════
    //
    // Vai "giao chữ/toạ độ cho một app ngoài rồi nói đúng thứ đã xảy ra" nằm trọn ở tệp kia; ở đây chỉ còn ba
    // lời gọi. KHÔNG có logic nào bị nhân đôi — xem KDoc [VoiceTargetDispatch] về vì sao cắt đúng chỗ này.

    /**
     * Mở app — và từ 1.50, mở **vào một ô** nếu câu nêu ô (*"mở YouTube vào ô số 2"*).
     *
     * Số ô kiểm ở ĐÂY chứ không ở `:core`: chỉ tầng này biết bố cục đang dùng có mấy ô (bố cục tự vẽ đổi được
     * giữa hai câu nói). Ngoài dải ⇒ nói ra **con số thật**, xem [VoiceReply.slotOutOfRange].
     */
    private fun runOpenApp(i: VoiceIntent.OpenApp, labels: Map<String, String>) {
        // Nhãn thật trước; chỉ câu gọi app bằng **cách nói tiếng Việt** mới tra bảng đích (§3 L6: *"mở bản đồ"*).
        val key = i.appKey
        val pkg = labels[i.appName] ?: key?.let { VoiceAppTargets.byKey(it)?.packageIn(labels.values.toSet()) }
        if (pkg == null) {
            say(if (key != null) VoiceReply.appNotInstalled(i, key) else VoiceReply.cannotOpen(i))
            return
        }
        val slot = i.slot
        if (slot == null) {
            say(if (openApp(pkg)) VoiceReply.done(i) else VoiceReply.cannotOpen(i))
            return
        }
        val st = state()
        val count = EffectiveLayout.slotCount(st.workspace.preset, st.customLayout)
        if (slot !in 1..count) { say(VoiceReply.slotOutOfRange(i, count)); return }
        // 1-based (như người ta nói) → 0-based (như mảng ô). Phép đổi nằm ở ĐÚNG MỘT chỗ, là chỗ này.
        say(if (assignAppToSlot(slot - 1, pkg)) VoiceReply.done(i) else VoiceReply.cannotOpen(i))
    }

    private companion object {
        const val TAG = "KachiVoice"

        /** Chuỗi `TelemetryReadout` trả về khi xe chưa có số — cùng ký hiệu mà ô đọc đang vẽ. */
        const val NO_VALUE = "—"

        /**
         * R5 — chờ bao lâu rồi đọc lại khi lượt đọc ĐẦU báo một số khác số vừa gửi.
         *
         * 300 ms là một **giả định có chủ ý**, chưa phải phép đo: [CHƯA BIẾT] xe mất bao lâu từ lúc nhận lệnh
         * tới lúc bus mang số mới (spec §7 **OQ6** ghi cách chốt — bấm giờ giữa `write` và lần `readInt` đầu tiên
         * trả số mới, trên xe thật). Chọn số này vì nó nằm dưới ngưỡng người ta cảm thấy là *"máy treo"* (~500 ms)
         * mà vẫn dôi so với một nhịp CAN thường. Đặt hụt ⇒ câu trả lời thỉnh thoảng thuật lại số cũ — vẫn đúng
         * theo nghĩa *"xe đang báo thế"*, và đó là lý do câu nói không suy diễn nguyên nhân (KDoc
         * [VoiceReply.doneActual]).
         */
        const val READBACK_SETTLE_MS = 300L

        // ⚠ [SOÁT Pass 4 · P2] `NAV_PREFERENCE` đã theo [VoiceTargetDispatch] sang tệp kia cùng ba hàm dùng nó.
        // Lượt tách để lại ở đây một **bản sao y nguyên** mà không còn ai đọc (companion này `private`) — đúng
        // họ lỗi CLAUDE.md §4.1 cấm: hai bảng thứ tự app, sửa một bảng thì bảng kia lặng lẽ lệch. Một bảng, ở
        // chỗ dùng nó.
    }
}
