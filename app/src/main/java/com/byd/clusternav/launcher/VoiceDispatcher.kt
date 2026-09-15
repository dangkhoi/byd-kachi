package com.byd.clusternav.launcher

import android.util.Log
import com.byd.clusternav.launcher.voice.VoiceIntent
import com.byd.clusternav.launcher.voice.VoiceIntentParser
import com.byd.clusternav.launcher.voice.VoiceMediaOp
import com.byd.clusternav.launcher.voice.VoiceReply
import com.byd.clusternav.launcher.voice.VoiceAppIntents
import com.byd.clusternav.launcher.voice.VoiceAppTarget
import com.byd.clusternav.launcher.voice.VoiceAppTargets
import com.byd.clusternav.launcher.voice.VoicePlaces
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
            is VoiceIntent.Nav -> runNav(intent, labels)
            is VoiceIntent.NavigateSaved -> runNavSaved(intent, labels)
            is VoiceIntent.Media -> runMedia(intent, labels)
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

    // ══ V1.1 · TỪ VỰNG MỞ → APP ĐÍCH ════════════════════════════════════════════════════════════

    /**
     * Dẫn đường.
     *
     * ## Cái gì đã đổi ở 1.50, và vì sao nó KHÔNG phá ranh giới cũ
     * Tới 1.49 nhánh này chỉ **mở app** rồi nói thẳng là chưa chuyển được điểm đến — đúng với bằng chứng có lúc
     * đó. Owner 2026-09-14 hỏi lại, và [ĐO] trên máy ảo + nguồn Kiki cho thấy ba app đều **có cửa** (bảng
     * [VoiceAppTargets]). Ranh giới phương án C không đổi một chữ: Kachi vẫn **không tìm đường, không hiểu địa
     * điểm** — nó chuyển nguyên văn chuỗi chữ (hoặc cặp toạ độ) cho app dẫn đường rồi đứng ra ngoài.
     *
     * Ba đường ra, mỗi đường nói một câu khác nhau vì chúng **là** ba chuyện khác nhau:
     *  • app đích nhận CHỮ (Google Maps · Waze) ⇒ bắn thẳng;
     *  • app đích chỉ nhận TOẠ ĐỘ (VietMap) ⇒ giải toạ độ ở luồng nền, **đọc lại tên nơi giải ra** rồi mới bắn;
     *  • không giải được / không ai nhận ⇒ mở app trơn và **nói rõ là chưa giao được** (không có dấu ✓ rỗng).
     */
    private fun runNav(i: VoiceIntent.Nav, labels: Map<String, String>) {
        val installed = labels.values.toSet()
        val asked = i.app
        val target = pickNav(asked, installed)
        if (target == null) { say(if (asked != null) VoiceReply.appNotInstalled(i, asked) else VoiceReply.noNavApp(i)); return }
        val pkg = target.packageIn(installed) ?: run { say(VoiceReply.appNotInstalled(i, target.key)); return }

        if (!target.needsCoords) { deliver(i, target, pkg, i.query, null); return }
        // Cần toạ độ ⇒ lượt mạng/dịch vụ: **luồng nền**, và người lái phải biết là máy đang làm gì.
        say(VoiceReply.resolving(i))
        background {
            val coords = runCatching { geocode(i.query) }.getOrNull()
            onUi {
                // [SOÁT Pass 3 · P2] Tra cứu hỏng ≠ *"app không có cửa"*. Nói đúng cái vừa xảy ra, xem
                // [VoiceReply.navNoPlace] — dùng chung một câu là đổ lỗi cho app về một lần mất sóng.
                if (coords == null) {
                    say(if (openApp(pkg)) VoiceReply.navNoPlace(i, target) else VoiceReply.cannotOpen(i))
                    return@onUi
                }
                // Tên do bên giải trả về KHÁC câu người ta nói (*"chợ bến thành"* → *"Chợ Bến Thành"*, hoặc một
                // nơi trùng tên). Đọc lại rồi mới bắn — cùng lý do với cổng CONFIRM của từ vựng mở.
                confirm(
                    VoiceReply.confirmPlace(i, target, coords.place),
                    { deliver(i, target, pkg, coords.place, coords) },
                    { say(VoiceReply.cancelled(i, 0)) },
                )
            }
        }
    }

    /**
     * Dẫn đường tới một nơi **ĐÃ LƯU** (spec `docs/specs/kachi-voice-addresses.html` R3 · R4).
     *
     * ## Ba khác biệt so với [runNav], mỗi cái có lý do riêng
     *  1. **Không geocode, không hộp đọc-lại.** Đường kia phải tra mạng rồi hỏi lại vì điểm đến do nhận dạng tự
     *     do đọc ra. Ở đây dữ liệu là thứ chính người dùng đã gõ và đã có sẵn trên đĩa — thêm một lượt chờ mạng
     *     và một cú chạm cho câu người ta nói mỗi ngày là làm hỏng đúng thứ tính năng này sinh ra để chữa.
     *  2. **Chọn app theo DỮ LIỆU của mục**, không theo thứ tự ưu tiên trần ([VoiceAppTargets.navFor]): mục chỉ
     *     có chữ mà đẩy vào VietMap (chỉ nhận toạ độ) là mở app rồi bảo người ta tự gõ, trong khi Google Maps
     *     ngay dưới nhận được nguyên văn địa chỉ ấy.
     *  3. **Tra sổ lúc THI HÀNH**, không lúc phân tích: ý định chỉ mang nhãn (xem KDoc
     *     [VoiceIntent.NavigateSaved]) nên nếu người dùng vừa sửa địa chỉ xong, lượt này đi theo bản mới.
     */
    private fun runNavSaved(i: VoiceIntent.NavigateSaved, labels: Map<String, String>) {
        val place = SavedPlaces.find(state().savedPlaces, i.placeName)
        if (place == null) { say(VoiceReply.placeNotSaved(i, VoicePlaces.displayLabel(i.placeName))); return }
        val installed = labels.values.toSet()
        val asked = i.app
        val target = if (asked != null) {
            VoiceAppTargets.byKey(asked)?.takeIf { it.packageIn(installed) != null }
        } else {
            VoiceAppTargets.navFor(place.hasCoords, NAV_PREFERENCE, installed)
        }
        if (target == null) {
            say(if (asked != null) VoiceReply.appNotInstalled(i, asked) else VoiceReply.noNavApp(i))
            return
        }
        val pkg = target.packageIn(installed) ?: run { say(VoiceReply.appNotInstalled(i, target.key)); return }
        // Mục KHÔNG toạ độ + app chỉ nhận toạ độ ⇒ mở app trơn, và nói ra **việc người dùng làm được** (thêm
        // lat/lng) thay vì câu chung chung "app này không nhận điểm đến" — xem [VoiceReply.placeNeedsCoords].
        if (!place.hasCoords && target.needsCoords) {
            say(if (openApp(pkg)) VoiceReply.placeNeedsCoords(i, target) else VoiceReply.cannotOpen(i))
            return
        }
        val coords = if (place.hasCoords) {
            VoiceAppIntents.Coords(place.lat!!, place.lng!!, place.query)
        } else {
            null
        }
        deliver(i, target, pkg, place.query, coords)
    }

    /**
     * Nhạc — cùng hình dạng với [runNav], trừ việc không có app nhạc nào cần toạ độ.
     *
     * ## [SOÁT P1] *"phát nhạc"* khi CHƯA CÓ PHIÊN NÀO ⇒ phải MỞ app, không phải báo lỗi
     * [ĐO] `docs/diagnostics/emulator-voice-e2e-2026-09-15.md` §3 L2 (t46/t50): bản trước mở đầu bằng
     * `if (i.op != QUERY) { runTransport(i); return }` ⇒ mọi lệnh không phải QUERY rơi thẳng vào transport và
     * trường [VoiceIntent.Media.app] **bị vứt** — *"mở nhạc trên YouTube Music"* trả lời *"chưa có phiên nhạc"*
     * mà không app nào lên màn. Luật mới: **PLAY** + (nêu đích danh app **hoặc** chưa có phiên) ⇒ mở app đó.
     * PAUSE/NEXT/PREV giữ transport — ở đó *"chưa có phiên nhạc"* là câu ĐÚNG, và mở một app nhạc lên để "dừng"
     * nó là làm việc khác hẳn việc được bảo.
     */
    private fun runMedia(i: VoiceIntent.Media, labels: Map<String, String>) {
        if (i.op == VoiceMediaOp.QUERY) { runMediaQuery(i, labels); return }
        val playing = runCatching { mediaPackage() }.getOrNull()
        if (i.op == VoiceMediaOp.PLAY && (i.app != null || playing == null)) { runPlayInApp(i, labels, playing); return }
        runTransport(i)
    }

    /** *"phát bài &lt;tên&gt;"* — giao chuỗi chữ cho app nhạc (từ vựng mở, R17). */
    private fun runMediaQuery(i: VoiceIntent.Media, labels: Map<String, String>) {
        val (target, pkg) = musicTarget(i, labels) ?: return
        deliver(i, target, pkg, i.query, null)
    }

    /**
     * *"phát nhạc [trên &lt;app&gt;]"* — mở app nhạc rồi nói đúng thứ đã xảy ra. App đích **đang phát** ⇒ transport
     * (bắn `play` vào chính phiên đó), không mở đè: mở lại app đang phát là một lượt chuyển màn thừa lúc đang lái.
     */
    private fun runPlayInApp(i: VoiceIntent.Media, labels: Map<String, String>, playing: String?) {
        val (target, pkg) = musicTarget(i, labels) ?: return
        if (playing == pkg) { runTransport(i); return }
        say(if (openApp(pkg)) VoiceReply.musicAppOpened(i, target) else VoiceReply.cannotOpen(i))
    }

    /** App nhạc đích + gói của nó. `null` ⇒ **đã nói ra** lý do (chưa cài / không có app nhạc nào). */
    private fun musicTarget(i: VoiceIntent.Media, labels: Map<String, String>): Pair<VoiceAppTarget, String>? {
        val installed = labels.values.toSet(); val asked = i.app
        val target = pickMusic(asked, installed)
        if (target == null) { say(if (asked != null) VoiceReply.appNotInstalled(i, asked) else VoiceReply.noMusicApp(i)); return null }
        val pkg = target.packageIn(installed) ?: run { say(VoiceReply.appNotInstalled(i, target.key)); return null }
        return target to pkg
    }

    /** Bắn một lượt giao việc và nói đúng thứ đã xảy ra. */
    private fun deliver(
        i: VoiceIntent,
        target: VoiceAppTarget,
        pkg: String,
        query: String,
        coords: VoiceAppIntents.Coords?,
    ) {
        val launch = target.destinationLaunch(coords != null)
        val ok = launch != null && sendToApp(
            VoiceAppIntents.Handoff(pkg, launch, query, coords, target.fallback),
        )
        if (ok) { say(VoiceReply.handedOver(i, target)); return }
        openPlain(i, target, pkg)
    }

    /** Không giao được chữ ⇒ vẫn **mở app** (đó là phần chắc chắn làm được) rồi nói ra phần chưa làm được. */
    private fun openPlain(i: VoiceIntent, target: VoiceAppTarget, pkg: String) {
        say(if (openApp(pkg)) VoiceReply.navOpenedNoHandover(i, target) else VoiceReply.cannotOpen(i))
    }

    /**
     * Chọn app dẫn đường.
     *
     * Câu nêu đích danh ⇒ đúng app đó (không có nó thì nói *"chưa cài"*, **không** lặng lẽ đổi sang app khác —
     * người ta nói *"bằng Waze"* là có lý do). Không nêu ⇒ [NAV_PREFERENCE], tức thứ tự đã có từ 1.49.
     */
    private fun pickNav(key: String?, installed: Set<String>): VoiceAppTarget? {
        VoiceAppTargets.byKey(key)?.let { return it.takeIf { t -> t.packageIn(installed) != null } }
        return NAV_PREFERENCE.firstNotNullOfOrNull { pkg ->
            VoiceAppTargets.NAV.firstOrNull { pkg in it.packages && it.packageIn(installed) != null }
        }
    }

    /**
     * Chọn app nhạc: **phiên đang phát trước**, rồi mới tới thứ tự của bảng.
     *
     * Owner 2026-09-14 nói *"mở nhạc bằng yt music, youtube"* — tức app là một lựa chọn, không phải một hằng số.
     * Khi câu không nêu app thì đích đúng nhất là **app người ta đang nghe**: mở YouTube Music đè lên Spotify
     * đang phát là hai luồng nhạc cùng lúc, và đó là thứ người lái phải dừng xe mới dẹp được.
     */
    private fun pickMusic(key: String?, installed: Set<String>): VoiceAppTarget? {
        VoiceAppTargets.byKey(key)?.let { return it.takeIf { t -> t.packageIn(installed) != null } }
        val playing = runCatching { mediaPackage() }.getOrNull()
        VoiceAppTargets.MUSIC.firstOrNull { playing != null && playing in it.packages }?.let { return it }
        return VoiceAppTargets.MUSIC.firstOrNull { it.packageIn(installed) != null }
    }

    /**
     * Nhạc — phần TRANSPORT (phát / dừng / bài tiếp / bài trước).
     *
     * ## [SOÁT P1] Vì sao phải đọc kết quả của transport, không bắn rồi báo "✓"
     * [MediaBridge] chỉ điều khiển được **phiên đang hoạt động** mà nó đã thấy; chưa ai thấy phiên nào thì mọi
     * lệnh transport là no-op **im lặng** (KDoc `MediaBridge`: degrade-safe). Bản đầu bắn xong báo `✓ Phát nhạc`
     * bất kể có phiên hay không — tức nói dối đúng cái ca hay gặp nhất.
     */
    private fun runTransport(i: VoiceIntent.Media) {
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
         * Thứ tự ƯU TIÊN app dẫn đường — **danh sách roster dùng chung** ở `:core`, không phải tên gói viết cứng
         * mới. VietMap đứng đầu vì đó là app đang nuôi badge tốc độ trên xe owner; Google Maps rồi Waze theo sau.
         */
        val NAV_PREFERENCE: List<String> =
            listOf(NavApps.VIETMAP_LIVE) + NavApps.GMAPS.toList() + NavApps.WAZE.toList()
    }
}
