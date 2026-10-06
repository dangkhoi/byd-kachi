package com.byd.clusternav.launcher

import android.util.Log
import com.byd.clusternav.launcher.voice.VoiceIntent
import com.byd.clusternav.launcher.voice.VoiceIntentParser
import com.byd.clusternav.launcher.voice.VoiceReply
import com.byd.clusternav.launcher.voice.VoiceAppIntents
import com.byd.clusternav.launcher.voice.VoiceAppPhonetics
import com.byd.clusternav.launcher.voice.VoiceAppTargets
import com.byd.clusternav.launcher.voice.VoiceAppAlias
import com.byd.clusternav.launcher.voice.VoiceTeachHint
import com.byd.clusternav.launcher.voice.VoiceTeachHintCounter
import com.byd.clusternav.launcher.voice.VoicePlaces
import com.byd.clusternav.launcher.voice.VoiceRisk
import com.byd.clusternav.launcher.voice.VoiceRiskTable
import com.byd.clusternav.launcher.voice.SlotPlaceOutcome
import com.byd.clusternav.launcher.voice.VoiceSlotPlace
import com.byd.clusternav.launcher.voice.VoiceWriteLane

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
    /**
     * 2.91 VOICE-APP-NAMES — tên app đã dạy còn sống của ĐÚNG bảng [appsByLabel] vừa đọc ([VoiceAppIndex.aliasesOf]).
     * Mặc định rỗng: bài test/bề mặt chưa nối giữ nguyên hành vi (không có tên đã dạy). `VoiceWiring.dispatcher` nối thật.
     */
    private val appAliases: (Map<String, String>) -> List<VoiceAppAlias> = { emptyList() },
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
     * V1.1 — đưa một app vào **ô số [slot]** (0-based). `true` = đã đưa.
     *
     * ⚠ Đính chính owner 01/10: đây là lượt ĐẶT TẠM (`KachiHomeSlots.placeTemporary`) — không ghi `slot_n`, app cũ
     * của ô ra sau màn nhà thay vì bị force-stop. Vẫn phải đi qua `KachiHomeSlots`, không phải một ViewModel gọi
     * thẳng: lớp đó làm CẢ HAI nửa (state + side-effect cửa sổ `LauncherWindows.placeApp`), cùng khuôn với đường
     * LƯU của ngăn kéo (`KachiHomeSlots.assignApp`). Bỏ một nửa là lỗi *"ô thay app khác mà app cũ còn nguyên trong
     * sổ vị trí"* đã có thật (xem KDoc `KachiHomeSlots`).
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
    /**
     * [SOÁT P1-1 · 2026-09-16] Đọc **TƯƠI** một datum trước khi đọc số cho người dùng nghe; `null` = *"ảnh chụp
     * hiện có đã tươi"* ⇒ dùng [state] như cũ.
     *
     * Vì sao phải có: từ 1.67 vòng poll chỉ đọc datum **đang hiện trên màn** (`CarDataDemand`) và **giữ giá trị
     * cũ** cho phần còn lại. Câu hỏi bằng giọng thì hỏi được **mọi** datum, kể cả thứ không có trên màn — nên nếu
     * chỉ đọc [state] thì Kachi sẽ đọc to một con số của lần cuối cái ô ấy còn trên màn, nghe như đang sống.
     *
     * Mặc định `{ null }` để mọi bài test (và mọi bề mặt chưa nối) giữ NGUYÊN hành vi cũ: đọc ảnh chụp.
     */
    private val freshCar: (String) -> CarStatus? = { null },
    /**
     * Mã app dẫn đường MẶC ĐỊNH (`voice_nav_default_app`), truyền xuống [VoiceTargetDispatch]. Mặc định `{ null }`
     * để mọi test/bề mặt chưa nối giữ hành vi cũ (thứ tự [VoiceTargetDispatch.NAV_PREFERENCE]).
     */
    private val navDefault: () -> String? = { null },
    /** App nhạc mặc định (owner 2026-09-21) — truyền xuống [VoiceTargetDispatch]; `null` giữ hành vi cũ. */
    private val musicDefault: () -> String? = { null },
    /**
     * Giải `video_id` bài đầu từ YouTube (owner 2026-09-18 "phát luôn"), truyền xuống [VoiceTargetDispatch].
     * Mặc định `{ null }` ⇒ test/bề mặt chưa nối giữ đường search-play cũ.
     */
    private val resolveVideo: (String) -> String? = { null },
    /**
     * VOICE-WAKE-SLOTCOUNT — *"mở X vào ô N"* giao cho NƠI GIỮ BỐ CỤC THẬT. `null` (mặc định) = bề mặt này chung
     * tiến trình với màn chính ⇒ kiểm dải bằng [state] rồi [assignAppToSlot], y nguyên 2.85. `:wake` PHẢI truyền:
     * [state] của nó là `VoiceGrammarSnapshot.homeState()`, bố cục trong đó là mặc định (3 ô), không phải màn thật.
     */
    placeInSlot: ((Int, String) -> SlotPlaceOutcome)? = null,
    /**
     * Ngôn ngữ của MỌI câu trả lời của cầu này (spec `kachi-i18n-zh-th-ms.html` R6): `voiceLangOf(giao diện)` — EN ⇒ EN,
     * còn lại ⇒ VI (giọng đọc Piper chỉ có tiếng Việt). Giá trị chụp lúc dựng: cầu dựng lại mỗi lượt nói, nên một lượt
     * nói một tiếng. Mặc định đọc [Strings.current] — đúng cho tiến trình chính; `:wake` PHẢI truyền tiếng từ ảnh chụp
     * ngữ pháp (`Strings.current` ở đó luôn là VI mặc định).
     */
    private val lang: Lang = voiceLangOf(Strings.current),
) {

    /** Xem tham số `placeInSlot`. Đây là chỗ DUY NHẤT của lớp này đọc bố cục từ [state]. */
    private val place: (Int, String) -> SlotPlaceOutcome = placeInSlot ?: { idx, pkg ->
        VoiceSlotPlace.decide(idx, VoiceSlotPlace.slotCountOf(state())) { assignAppToSlot(idx, pkg) }
    }

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
        navDefault = navDefault,
        musicDefault = musicDefault,
        resolveVideo = resolveVideo,
        lang = lang,
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
     *
     * ## [P1 · SOÁT Opus 2026-09-27] [onSettled] — *"cả câu đã ghi xong, hoặc đang chờ NGƯỜI LÁI"*
     * Từ R5, hàm này **trả về trước** khi câu chạy xong: vế bất đồng bộ (rời-AUTO của [VoiceClimateStep], gói lệnh)
     * đi xuống luồng nền, nên lúc nó trả về chưa có một lời `say` nào. Chỗ gọi ([VoiceSession.execute]) mà chốt lượt
     * nói ở đó thì gom được một mảng **RỖNG**: không đọc gì cả, mở micro nối ngay, rồi hai câu trả lời về muộn bị
     * cổng `micOpen` bỏ — kể cả câu *"xe không nhận lệnh"*. Vì vậy: [onSettled] gọi **đúng một lần**, khi
     * [runFrom] đã đi hết câu (`done`) HOẶC đã dừng ở một hộp hỏi lại (phần còn lại chờ người lái, y 2.75 — nếu đợi
     * tiếp thì tấm chữ treo suốt lượt hỏi/đáp).
     */
    fun execute(intents: List<VoiceIntent>, onSettled: () -> Unit = {}) {
        val labels = appsByLabel()
        val fired = java.util.concurrent.atomic.AtomicBoolean(false)
        val settled = { if (fired.compareAndSet(false, true)) onSettled() }
        lane.submit { done -> runFrom(intents, 0, labels, done, settled) }
    }

    /**
     * ═══ VOICE-WRITE-LANE (2.76 · spec kachi-276-closing R5) · MỘT LÀN GHI, vế sau chờ vế trước **ghi xong** ═══
     *
     * Mọi lệnh ghi HAL từ giọng nói của một câu đi qua đúng một [VoiceWriteLane]; các vế **nối tiếp bằng lời gọi
     * lại** (`next`), không bằng vòng `while` trên luồng gọi. Vì sao — review Pass 1 của 2.74 ([P2]): vế rời-AUTO
     * của [VoiceControlDispatch] là hai lệnh + nhịp 400 ms trên luồng nền, hàm trả về **trước** lệnh thứ hai; vòng
     * `while` cũ chạy vế kế tiếp ngay ⇒ *"tăng gió rồi tắt điều hoà"* ghi `ac_auto=OFF` của vế 2 **vào giữa nhịp
     * chờ** của vế 1, và theo tiền đề [ĐO] của `DEFAULT_GAP_MS` thì lệnh mức gió có thể bị xe bỏ mà Kachi vẫn đọc ✓.
     *
     * Với vế đồng bộ, `next` được gọi ngay trong lượt gọi ⇒ thứ tự lệnh và lời đáp **y nguyên** 2.75 (bài canh cũ
     * giữ xanh). Làn là của **riêng cầu này** (dựng lại mỗi lượt nói) — lý do ở KDoc [VoiceWriteLane].
     */
    private val lane = VoiceWriteLane()

    /**
     * Chạy từ vế [from] tới hết, DỪNG tại vế đầu tiên phải hỏi lại; [done] khi cả câu đã xong (hoặc bị huỷ).
     *
     * [settled] = *"chỗ gọi được chốt lượt nói"* (xem KDoc [execute]): hết câu, hoặc đứng ở hộp hỏi lại. Gọi **sau**
     * [confirm] ở nhánh hỏi lại: nếu hộp ấy tự trả lời KHÔNG ngay trong lượt gọi (phiên đã qua) thì `done` đã bắn
     * trước và câu *"đã huỷ"* nằm sẵn trong mảng lời đáp.
     */
    private fun runFrom(
        intents: List<VoiceIntent>,
        from: Int,
        labels: Map<String, String>,
        done: () -> Unit,
        settled: () -> Unit,
    ) {
        if (from >= intents.size) { settled(); done(); return }
        val intent = intents[from]
        val next = { runFrom(intents, from + 1, labels, done, settled) }
        if (VoiceRiskTable.of(intent, confirmIds()) == VoiceRisk.CONFIRM) {
            val remaining = intents.size - (from + 1)
            confirm(
                VoiceReply.confirmQuestion(intent, lang),
                { run(intent, labels, next) },
                { say(VoiceReply.cancelled(intent, remaining, lang)); done() },
            )
            settled()
            return
        }
        run(intent, labels, next)
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
            aliases = appAliases(labels),
        )

    // ── Thi hành ─────────────────────────────────────────────────────────────────────────────────

    /**
     * Thi hành MỘT vế rồi gọi [next] **đúng một lần** khi vế ấy đã ghi xong (VOICE-WRITE-LANE).
     *
     * Chỉ hai nhánh ghi HAL **có thể** bất đồng bộ giữ [next] lại: nút xe ([VoiceControlDispatch] — nhánh rời-AUTO)
     * và gói lệnh ([runMacro] — cả gói chạy nền). Mọi nhánh còn lại không ghi HAL (mở app · nhạc · hồ sơ · đọc số
     * · bố cục…) nên gọi [next] ngay sau khi làm — y nguyên thứ tự 2.75. Đường dẫn đường có tra toạ độ + hộp hỏi
     * ([VoiceTargetDispatch.runNav]) cũng vậy: nó không ghi gì xuống xe, và bắt vế sau chờ một lượt mạng là đổi
     * hành vi đã chạy hiện trường ngoài phạm vi phép đo (CLAUDE.md §6).
     */
    private fun run(intent: VoiceIntent, labels: Map<String, String>, next: () -> Unit) {
        when (intent) {
            is VoiceIntent.Control -> { runControl(intent, next); return }
            is VoiceIntent.Macro -> { runMacro(intent, next); return }
            is VoiceIntent.Launcher -> runLauncher(intent)
            is VoiceIntent.Profile -> { onSwitchProfile(intent.name); say(VoiceReply.done(intent, lang)) }
            is VoiceIntent.Read -> runRead(intent)
            is VoiceIntent.Nav -> targets.runNav(intent, labels)
            is VoiceIntent.NavigateSaved -> targets.runNavSaved(intent, labels)
            is VoiceIntent.Media -> targets.runMedia(intent, labels)
            is VoiceIntent.OpenApp -> runOpenApp(intent, labels)
            // L7 — một lambda, không có tầng logic nào ở đây: bố cục là state của màn chính, và `HomeViewModel`
            // đã là nơi ghi bền DUY NHẤT của nó.
            is VoiceIntent.Layout ->
                say(if (runCatching { onLayout(intent.preset) }.getOrDefault(false)) {
                    VoiceReply.done(intent, lang)
                } else {
                    VoiceReply.layoutNotHere(intent, lang)
                })
            is VoiceIntent.Unknown -> say(VoiceReply.unknown(intent, lang) + teachTail(intent))
            // Req2 (owner 2026-09-24) — câu kết thúc: nói ngắn rồi để phiên tự đóng (không mở hội thoại nối).
            VoiceIntent.EndSession -> say(VoiceReply.bye(lang))
        }
        next()
    }

    /**
     * Một nút xe — vai *"ghi gì, chờ ở đâu, nói gì, và lúc nào thì XONG"* nằm trọn ở [VoiceControlDispatch] (tách
     * 2.76 vì trần 500 dòng). Dựng **một lần** cho cả đời cầu, cùng lẽ với [targets].
     */
    private val controls = VoiceControlDispatch(
        control = control, state = state, say = say, freshCar = freshCar, onUi = onUi, background = background, lang = { lang },
    )

    private fun runControl(i: VoiceIntent.Control, next: () -> Unit) = controls.run(i, next)

    /**
     * Gói lệnh chạy **cả gói** trên luồng nền (nhiều lệnh HAL, có ngủ giữa các bước) ⇒ [next] chỉ được gọi khi gói
     * đã xong — về luồng VẼ qua `onUi`, cùng lẽ [VoiceClimateStep] — kể cả khi gói ném (`finally`). Hai lối ra sớm
     * (mã lạ · gói đang chạy) gọi [next] ngay: không có gì để chờ.
     */
    private fun runMacro(i: VoiceIntent.Macro, next: () -> Unit) {
        val macro = ActionMacros.byId(i.id)
        if (macro == null) { say(VoiceReply.failed(i, lang = lang)); next(); return }
        if (!ControlTileState.shared.beginRun(macro.id)) {
            say(VoiceReply.busy(i, lang))
            next()
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
                say(res.notice(macro.labelIn(lang), lang) ?: VoiceReply.done(i, lang))
            } catch (t: Throwable) {
                Log.w(TAG, "gói ${macro.id} hỏng giữa lượt chạy", t)
                say(VoiceReply.failed(i, lang = lang))
            } finally {
                ControlTileState.shared.endRun(macro.id)
                onUi(next)
            }
        }
    }

    private fun runLauncher(i: VoiceIntent.Launcher) {
        when (i.id) {
            LauncherActions.APPS -> openAppList()
            LauncherActions.SETTINGS -> openSettings()
            LauncherActions.VOICE -> onListen()
            // Mã launcher tương lai mà bản này chưa biết: im lặng mở nhầm một màn còn tệ hơn nói thẳng là chưa có.
            else -> { say(VoiceReply.failed(i, lang = lang)); return }
        }
        say(VoiceReply.done(i, lang))
    }

    private fun runRead(i: VoiceIntent.Read) {
        val spec = TelemetryRegistry.byId(i.datumId)
        // [SOÁT P1-1] Cổng hiệu năng H1 giữ giá trị CŨ cho datum không hiện trên màn ⇒ hỏi một lượt TƯƠI trước
        // khi nói. Hụt/không cần ⇒ `null` ⇒ dùng ảnh chụp như bản 1.66. Xem KDoc [freshCar].
        val car = runCatching { freshCar(i.datumId) }.getOrNull() ?: state().carStatus
        // Nhãn + CHỮ giá trị theo tiếng GIỌNG NÓI ([lang]), không theo màn: câu này được đọc lên (spec R6).
        val view = TelemetryReadout.of(i.datumId, car, lang)
        val value = view?.displayWithUnit()
        say(
            when {
                spec == null -> VoiceReply.failed(i, lang = lang)
                value.isNullOrBlank() || value == NO_VALUE -> VoiceReply.noReading(spec.labelIn(lang), lang)
                else -> spec.labelIn(lang) + ": " + value
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
     * Số ô KHÔNG tính ở đây: chỉ nơi giữ bố cục thật biết bố cục đang dùng có mấy ô (bố cục tự vẽ đổi được giữa hai
     * câu nói, và `:wake` không có bố cục thật) — xem [place]. Ngoài dải ⇒ nói ra **con số thật** mà nơi ấy trả về,
     * xem [VoiceReply.slotOutOfRange].
     */
    private fun runOpenApp(i: VoiceIntent.OpenApp, labels: Map<String, String>) {
        // Nhãn thật trước; chỉ câu gọi app bằng **cách nói tiếng Việt** mới tra bảng đích (§3 L6: *"mở bản đồ"*).
        val key = i.appKey
        val pkg = labels[i.appName] ?: key?.let { VoiceAppTargets.byKey(it)?.packageIn(labels.values.toSet()) }
        if (pkg == null) {
            say(if (key != null) VoiceReply.appNotInstalled(i, key, lang) else VoiceReply.cannotOpen(i, lang))
            return
        }
        // H3 · [ĐO máy ảo 2026-09-16, ca t73–t76] Câu khớp bằng **dạng ĐỌC** (*"mở du túp"*) mang theo đúng chuỗi
        // đã khớp, nên câu trả lời đọc lên là *"Mở ứng dụng du túp"* — một cái tên người dùng chưa từng viết, và
        // họ không có cách nào biết máy đã mở đúng app chưa. Từ khi owner chốt Kachi **đọc phản hồi thành tiếng**
        // (D-C3) thì đó không còn là chuyện thẩm mỹ. Đổi lại đúng nhãn người dùng nhìn thấy trước khi nói.
        val shown = displayName(pkg, labels)?.takeIf { it != i.appName }?.let { i.copy(appName = it) } ?: i
        val slot = i.slot
        if (slot == null) {
            say(if (openApp(pkg)) VoiceReply.done(shown, lang) else VoiceReply.cannotOpen(shown, lang))
            return
        }
        // 1-based (như người ta nói) → 0-based (như mảng ô). Phép đổi nằm ở ĐÚNG MỘT chỗ, là chỗ này.
        say(
            when (val out = place(slot - 1, pkg)) {
                SlotPlaceOutcome.Placed -> VoiceReply.done(shown, lang)
                SlotPlaceOutcome.Failed -> VoiceReply.cannotOpen(shown, lang)
                is SlotPlaceOutcome.OutOfRange -> VoiceReply.slotOutOfRange(shown, out.slotCount, lang)
            },
        )
    }

    /**
     * Nhãn **người dùng nhìn thấy** của một gói, chọn trong số mọi khoá cùng trỏ về nó.
     *
     * Phép chọn nằm ở `:core` ([VoiceAppPhonetics.canonicalLabel]) vì nó thuần và phải kiểm off-car; ở đây chỉ
     * gom nhóm. `null` khi không khoá nào trỏ về gói ấy (đường bảng đích [VoiceAppTargets] — lúc đó tên hiện đã
     * là nhãn chính thức của app rồi, không cần đổi).
     */
    private fun displayName(pkg: String, labels: Map<String, String>): String? =
        VoiceAppPhonetics.canonicalLabel(labels.filterValues { it == pkg }.keys.toList())

    /**
     * 2.91 VOICE-APP-NAMES (OQ5) — *"mở &lt;tên lạ&gt;"* không hiểu ⇒ đọc thêm *"nếu là tên app, dạy Kachi…"* ở
     * [VoiceTeachHint.SPOKEN_TIMES] lần đầu (đếm RAM theo tiến trình — KDoc [VoiceTeachHintCounter]; không đọc `state()`).
     */
    private fun teachTail(u: VoiceIntent.Unknown): String =
        if (VoiceTeachHint.pendingOf(listOf(u), u.text) != null && VoiceTeachHintCounter.takeSpoken(TEACH_HINT_SCOPE)) {
            " — " + VoiceTeachHint.spokenTail(lang)
        } else ""

    private companion object {
        const val TAG = "KachiVoice"

        /** Khoá đếm của [teachTail] — một bộ đếm cho cả tiến trình (xem KDoc). */
        const val TEACH_HINT_SCOPE = "process"

        /** Chuỗi `TelemetryReadout` trả về khi xe chưa có số — cùng ký hiệu mà ô đọc đang vẽ. */
        const val NO_VALUE = "—"

        /**
         * R5 — hằng chờ của lượt đọc lại đã theo vai *"đọc lại xe rồi mới nói"* sang [VoiceReadback] (lượt E
         * 2026-09-19). Giữ một bản sao ở đây là dựng hai hằng cho cùng một khoảng chờ, và bản không ai đọc sẽ
         * lặng lẽ lệch — đúng họ lỗi mà ghi chú `NAV_PREFERENCE` dưới đây nói tới.
         */

        // ⚠ [SOÁT Pass 4 · P2] `NAV_PREFERENCE` đã theo [VoiceTargetDispatch] sang tệp kia cùng ba hàm dùng nó.
        // Lượt tách để lại ở đây một **bản sao y nguyên** mà không còn ai đọc (companion này `private`) — đúng
        // họ lỗi CLAUDE.md §4.1 cấm: hai bảng thứ tự app, sửa một bảng thì bảng kia lặng lẽ lệch. Một bảng, ở
        // chỗ dùng nó.
    }
}
