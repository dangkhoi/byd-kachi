package com.byd.clusternav.launcher.voice

import com.byd.clusternav.launcher.ActionMacros
import com.byd.clusternav.launcher.CarCapabilities
import com.byd.clusternav.launcher.ClimateAuto
import com.byd.clusternav.launcher.ControlRegistry
import com.byd.clusternav.launcher.Lang
import com.byd.clusternav.launcher.LauncherActions
import com.byd.clusternav.launcher.ProfileNames
import com.byd.clusternav.launcher.Strings
import com.byd.clusternav.launcher.TelemetryRegistry

/**
 * ═══ V1 · CÂU PHẢN HỒI — DỰNG Ở `:core`, SONG NGỮ ═════════════════════════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-voice-command.html` R5. `:core` thuần ⇒ kiểm off-car.
 *
 * ## Vì sao câu trả lời nằm CẠNH bộ phân tích, không nằm ở `:app`
 * Mọi câu ở đây gọi tên một khả năng bằng **nhãn của chính bộ đăng ký** (`displayLabel`). Dựng chúng ở `:app` thì
 * tầng vẽ phải tra lại `ControlRegistry`/`TelemetryRegistry`/`ActionMacros`/`LauncherActions` một lần nữa — bốn
 * đường tra thứ hai, và đó đúng là chỗ hai bề mặt bắt đầu gọi một cái nút bằng hai cái tên. Ở đây thì câu trả lời
 * **không thể** lệch khỏi chữ trên nút, vì nó đọc cùng một trường.
 *
 * ## Hôm nay ra CHỮ, mai ra TIẾNG — cùng một hàm
 * Chưa có TTS (R8: chờ số đo §2.14 trên xe). Nhưng thứ TTS cần là **một câu tiếng Việt tử tế**, chính là thứ hàm
 * này trả về. Khi tầng tiếng bật lên, nó đọc đúng chuỗi này; không có gì phải viết lại.
 *
 * ## `lang` — ngôn ngữ của GIỌNG NÓI, không phải của màn (spec `kachi-i18n-zh-th-ms.html` R6)
 * Mọi hàm nhận `lang` (mặc định [Strings.current] — bề mặt HIỂN THỊ như cột *"Kachi làm gì"* giữ tiếng giao diện).
 * Đường NÓI (`VoiceDispatcher`/`VoiceSession`, cả tiến trình `:wake`) truyền `voiceLangOf(giao diện)`: EN ⇒ EN, còn
 * lại ⇒ VI — giọng Piper tiếng Việt không đọc được chữ Hán/Thái. Thân câu xem-trước + câu không-hiểu ở
 * [VoiceReplyPreview]/[VoiceReplyUnknown] (tách vì trần 500 dòng).
 */
object VoiceReply {

    /** Nhãn của bất kỳ mã nào trong bốn bộ đăng ký, theo [lang]; mã lạ ⇒ trả chính mã (không sập). */
    fun labelOf(id: String, lang: Lang = Strings.current): String =
        ControlRegistry.byId(id)?.labelIn(lang)
            ?: TelemetryRegistry.byId(id)?.labelIn(lang)
            ?: ActionMacros.byId(id)?.labelIn(lang)
            ?: LauncherActions.byId(id)?.labelIn(lang)
            ?: id

    /**
     * Câu mô tả **việc sắp làm** — dùng cho hộp xác nhận và cho dòng "đã hiểu là…" của màn thử.
     *
     * Luôn nói ra **tên nút + giá trị**, không nói mã: người lái không biết `win_lf` là gì, và một hộp xác nhận mà
     * người ta không đọc hiểu thì chỉ là một cú chạm thừa.
     */
    @Suppress("CyclomaticComplexMethod")
    fun preview(i: VoiceIntent, lang: Lang = Strings.current): String = when (i) {
        is VoiceIntent.Control -> VoiceReplyPreview.control(i, lang)
        is VoiceIntent.Macro -> Strings.t("Chạy gói ", "Run pack ", lang) + labelOf(i.id, lang)
        is VoiceIntent.Launcher -> Strings.t("Mở ", "Open ", lang) + labelOf(i.id, lang)
        is VoiceIntent.Profile -> Strings.t("Đổi sang hồ sơ ", "Switch to profile ", lang) + ProfileNames.display(i.name, lang)
        is VoiceIntent.Read -> Strings.t("Xem ", "Show ", lang) + labelOf(i.datumId, lang)
        is VoiceIntent.Nav -> Strings.t("Dẫn đường tới ", "Navigate to ", lang) + i.query + VoiceReplyPreview.by(i.app, lang)
        // Sổ địa chỉ: đọc **nhãn**, không đọc địa chỉ. Người lái nói *"về nhà"* thì câu trả lời phải nói *"Nhà"* —
        // đọc lại nguyên dòng "123 Nguyễn Trãi, Hà Nội" là bắt họ đọc một thứ họ đã tự gõ và đã biết.
        is VoiceIntent.NavigateSaved -> Strings.t("Dẫn đường tới ", "Navigate to ", lang) +
            VoicePlaces.displayLabel(i.placeName, lang) + VoiceReplyPreview.by(i.app, lang)
        is VoiceIntent.OpenApp -> Strings.t("Mở ứng dụng ", "Open app ", lang) + i.appName + VoiceReplyPreview.inSlot(i.slot, lang)
        // L7 — đọc **nhãn của chính enum** (`LayoutPreset.labelIn`), không dựng một bảng chữ thứ hai: chip bố cục
        // ở Cài đặt đang vẽ đúng chuỗi đó, nên câu nói và màn hình không thể gọi một bố cục bằng hai cái tên.
        is VoiceIntent.Layout -> Strings.t("Bố cục ", "Layout ", lang) + i.preset.labelIn(lang)
        is VoiceIntent.Media -> VoiceReplyPreview.media(i, lang)
        VoiceIntent.EndSession -> bye(lang)
        is VoiceIntent.Unknown -> unknown(i, lang)
    }

    /** Việc đã làm xong. */
    fun done(i: VoiceIntent, lang: Lang = Strings.current): String = "✓ " + preview(i, lang) + unverified(i, lang)

    /** Req2 (owner 2026-09-24) — câu tạm biệt ngắn khi kết thúc phiên voice. */
    fun bye(lang: Lang = Strings.current): String = Strings.t("Tạm biệt", "Bye", lang)

    /** Câu ĐỌC khi lượt nghe không ra chữ nào — tấm chữ giữ `R.string` của màn; xem [VoiceReplyUnknown.nothingHeard]. */
    fun nothingHeard(lang: Lang = Strings.current): String = VoiceReplyUnknown.nothingHeard(lang)

    /**
     * ═══ E (owner test xe 2026-09-19) · ĐÃ **ĐỌC LẠI XÁC NHẬN** ⇒ BỎ ĐUÔI *"chưa kiểm trên xe"* ════════════════
     *
     * Khác [done] ở đúng một chỗ: **không** gọi [unverified]. Đuôi *"chưa kiểm trên xe"* nói về mức bằng chứng
     * **tĩnh** của registry (`EvidenceTier`) — nó trả lời câu *"nút này từng chạy thật chưa"*. Nhưng khi chỗ gọi
     * vừa ghi xong **rồi đọc lại đường ĐỌC của chính xe** và thấy đúng mức mong muốn, thì câu hỏi ấy đã được trả
     * lời **tại chỗ, trên chiếc xe này, giây vừa rồi** — bằng chứng mạnh hơn hẳn một mức khai trong mã. Đọc thêm
     * *"chưa kiểm"* vào đó là nói sai: nó vừa được kiểm.
     *
     * ⇒ Chỉ dùng khi lượt đọc lại **khớp**. Đọc không được (`null`) ⇒ chỗ gọi giữ [done] (còn nguyên đuôi hedge —
     * đó là sự thành thật); đọc được mà **lệch** ⇒ [failed] (xe không nhận lệnh). Ba nhánh, ba câu khác nhau.
     */
    fun doneConfirmed(i: VoiceIntent, lang: Lang = Strings.current): String = "✓ " + preview(i, lang)

    /**
     * ═══ C (owner test xe 2026-09-19) · TỪ CHỐI MỞ CỐP/CA-PÔ KHI XE ĐANG CHẠY ═════════════════════════════════
     *
     * Xem [com.byd.clusternav.launcher.CtlSafetyPolicy.REQUIRES_STATIONARY] về vì sao chỉ hai mã ấy bị gate.
     *
     * Nói **điều kiện mở được** (*"khi xe đang dừng"*) thay vì một lời từ chối trơn: người lái vừa nói một câu
     * hoàn toàn hợp lệ, thứ chặn nó là một điều kiện họ **giải được trong mười giây** (đạp phanh, về P). Câu
     * *"xe không nhận lệnh"* ở đây sẽ làm họ nói lại lần hai, lần ba giữa lúc đang chạy — đúng thứ gate này sinh
     * ra để tránh.
     */
    fun notWhileMoving(i: VoiceIntent, lang: Lang = Strings.current): String =
        "✗ " + preview(i, lang) + " — " + Strings.t("chỉ mở được khi xe đang dừng", "only while the car is stopped", lang)

    /**
     * ═══ R5 · CÂU TRẢ LỜI ĐỌC LẠI **GIÁ TRỊ THẬT** MÀ XE BÁO ═══════════════════════════════════════════════
     *
     * Spec `docs/specs/kachi-voice-feedback.html` **R5 · T10**. Chỉ dùng cho nút [ControlKind.STEP], và chỉ khi
     * [CarControlPort.readStep] đọc được một con số (`null` ⇒ chỗ gọi giữ nguyên [done] — **không bịa số**).
     *
     * ## Hai câu, vì đây là hai việc khác nhau
     *  • **Khớp** ([actual] == giá trị đã gửi) ⇒ y hệt [done]: *"✓ Đặt Nhiệt độ = 24"*. Không thêm chữ nào —
     *    một câu dài hơn cho cùng một kết quả chỉ tốn thêm hai giây của người đang lái.
     *  • **Lệch** ⇒ nói ra **cả hai** con số: *"✓ Đã gửi Nhiệt độ 24 — xe báo 23"*. Không sửa câu thành *"đã đặt
     *    23"* (lệnh đã gửi là 24, nói khác đi là giấu mất việc vừa xảy ra), cũng không đổi thành *"✗"* (lệnh
     *    KHÔNG hỏng — nó được nhận, xe chỉ đang ở một con số khác).
     *
     * ## ⚠ Lệch KHÔNG có nghĩa là xe từ chối
     * Ba nguyên nhân [SUY] có thể cho cùng một chỗ lệch, và câu trên đúng với cả ba: (a) xe **kẹp** giá trị vào
     * dải của nó; (b) xe **chưa kịp áp** — đường đọc trả lại số CŨ vì lượt đọc chạy vài ms sau lượt ghi; (c) nút
     * này thật sự không ăn trên trim đó. Phân biệt ba ca ấy cần một phép đo trên xe (spec §7 **OQ6**), nên câu trả
     * lời chỉ **thuật lại** hai con số và để người lái nhìn thanh nút — nó không suy diễn nguyên nhân.
     */
    fun doneActual(i: VoiceIntent.Control, actual: Int, lang: Lang = Strings.current): String {
        if (i.value == actual) return done(i, lang)
        val name = ControlRegistry.byId(i.id)?.labelIn(lang) ?: labelOf(i.id, lang)
        return "✓ " + Strings.fIn(
            lang,
            "Đã gửi {0} {1} — xe báo {2}",
            "Sent {0} {1} — the car reports {2}", name, i.value, actual,
        ) + unverified(i, lang)
    }

    /**
     * ═══ UX4 — NẤC ĐÁY của thang gió tên là **AUTO**, không phải **0** ════════════════════════════════════════
     *
     * Dùng cho CẢ HAI ca mà [ClimateAuto.stepIntent] cho ra ở nấc đáy: vừa BẬT gió tự động (`EnableAuto`) và đã ở
     * auto rồi nên không bắn gì (`NoOp`). Câu chỉ **thuật lại trạng thái** — đúng với cả hai ca — chứ không khẳng
     * định *"đã đặt mức 0"*: [ĐO xe 2026-09-20] lệnh mức 0 bị xe **bỏ qua**, nên câu ấy sẽ là một lời nói dối.
     * Không chữ tự nhiên nào để dịch (nhãn đã theo ngôn ngữ qua [labelOf]; `AUTO` là bốn chữ của màn AC gốc).
     */
    fun autoLevel(id: String, lang: Lang = Strings.current): String = "✓ " + labelOf(id, lang) + ": " + ClimateAuto.AUTO

    /**
     * Đuôi *"chưa kiểm trên xe"* cho việc mà mức bằng chứng chưa phải [com.byd.clusternav.launcher.EvidenceTier.PROVEN].
     *
     * ## [SOÁT P2] Vì sao GIỌNG NÓI vẫn bắn, chỉ nói thêm một câu
     * Thanh nút bấm được mọi nút ở mọi mức bằng chứng — mức thấp chỉ đeo **dấu** (`CarCapabilities.needsBadge`,
     * `ActionMacro.needsBadge`). Cho giọng nói một luật khác (chặn, hoặc hỏi lại) là dựng **luật thứ hai** cho
     * cùng một cái nút: người dùng bấm thì chạy, nói thì không — không ai giải thích nổi, và nó cũng không an
     * toàn hơn (nút chưa kiểm phần lớn là *không ăn*, chứ không phải *nguy hiểm*; thứ nguy hiểm nằm ở
     * [VoiceRiskTable]). Nhưng câu trả lời thì **phải** nói ra, vì ở đây không có dấu nào để nhìn: một chữ "✓"
     * trơn cho một nút chưa từng chạy trên xe là hứa hão.
     */
    private fun unverified(i: VoiceIntent, lang: Lang): String {
        val needs = when (i) {
            is VoiceIntent.Control -> CarCapabilities.needsBadge(i.id)
            is VoiceIntent.Macro -> ActionMacros.byId(i.id)?.needsBadge() ?: false
            else -> false
        }
        return if (needs) " — " + Strings.t("chưa kiểm trên xe", "not yet checked on this car", lang) else ""
    }

    /**
     * Việc KHÔNG làm được — xe từ chối lệnh, hoặc app đích không có mặt.
     *
     * Cùng giọng với `MacroResult.notice`: nói ra **tên việc** rồi mới tới lý do. Thành công thì im lặng được, thất
     * bại thì không — người lái nói một câu và không thấy gì xảy ra sẽ nói lại lần hai, lần ba.
     */
    fun failed(i: VoiceIntent, why: String? = null, lang: Lang = Strings.current): String =
        "✗ " + preview(i, lang) + (why?.let { " — $it" } ?: " — " + Strings.t("xe không nhận lệnh", "the car refused", lang))

    /**
     * ═══ Câu cho các ca HỎNG CỤ THỂ ═══════════════════════════════════════════════════════════════════════════
     *
     * Chúng ở đây chứ không ở `:app` vì hai lý do, lý do thứ hai là lý do **máy kiểm được**:
     *  1. cùng giọng, cùng chỗ, cùng cách gọi tên khả năng như mọi câu khác trong lớp này;
     *  2. `:app` **không được** chứa chuỗi tiếng Việt viết cứng (`LauncherI18nContractTest`) — ở đó chữ phải đi qua
     *     `res/values…/strings_kachi.xml`. Nhưng những câu này ghép **nhãn của bộ đăng ký** vào giữa, mà nhãn thì
     *     sống ở `:core`; nhét chúng vào tài nguyên Android sẽ tách câu khỏi cái tên nó đang nói tới.
     */
    fun busy(i: VoiceIntent, lang: Lang = Strings.current): String =
        failed(i, Strings.t("việc trước còn đang chạy", "the previous run is still going", lang), lang)

    /** Xe chưa trả về số cho datum này (off-car là ca bình thường). [datumLabel] phải đã theo cùng [lang]. */
    fun noReading(datumLabel: String, lang: Lang = Strings.current): String =
        datumLabel + ": " + Strings.t("chưa đọc được", "no reading", lang)

    /**
     * H4 — nút **có trên màn nhưng xe này không có đường điều khiển** (route sentinel / absent on trim).
     *
     * ## Vì sao câu này phải tồn tại, và vì sao phép QUYẾT ĐỊNH không nằm ở đây
     * [ĐO xe 2026-09-16] `ac_auto`: owner xác nhận **xe CÓ** điều hoà auto, nhưng mã HAL khai trong registry
     * (`1324355606`) không có trong `BYDAutoFeatureIds` của đời xe này ⇒ lượt gọi trả sentinel *"absent on trim"*
     * (xem ghi chú ở `ControlRegistry.ac_auto`). Người lái nói *"bật điều hoà"* và **không thấy gì xảy ra** —
     * tệ hơn cả một lời từ chối, vì họ sẽ nói lại lần hai, lần ba.
     *
     * `:core` **không thể** tự biết điều đó: sentinel là kết quả **lúc chạy** của tầng HAL. Nên ở đây chỉ có hai
     * thứ thuần: [uncontrollable] (hình dạng của ca) và câu chữ. Ai trả lời được câu *"mã này có đường thật
     * không"* thì người đó gọi — cùng lệ với `freshCar`/`confirmIds` của `VoiceWiring`.
     *
     * @param routeAbsent chỗ gọi trả `true` khi mã không có đường điều khiển trên **chiếc xe này**.
     */
    fun uncontrollable(i: VoiceIntent, routeAbsent: (String) -> Boolean): Boolean =
        i is VoiceIntent.Control && routeAbsent(i.id)

    /** Câu đi kèm [uncontrollable] — nói thẳng là *chiếc xe này*, không nói *"lỗi"* (nút vẫn đúng, xe mới thiếu). */
    fun notOnThisCar(i: VoiceIntent, lang: Lang = Strings.current): String =
        failed(i, Strings.t("chưa điều khiển được trên xe này", "not controllable on this car", lang), lang)

    /** Không có app dẫn đường nào trên máy. */
    fun noNavApp(i: VoiceIntent, lang: Lang = Strings.current): String =
        failed(i, Strings.t("chưa có app dẫn đường nào trên xe", "no navigation app on this car", lang), lang)

    /**
     * Đã mở app dẫn đường **nhưng chưa chuyển điểm đến** — nói thẳng phần chưa làm được.
     *
     * [ĐO] RE Kiki §8.2: điểm đến thuộc từ vựng mở; đường đẩy chữ sang Kiki (`text_command`) mới ở mức **[SUY]**,
     * phải chốt bằng phép đo K2 trên xe (CLAUDE.md §14). Hứa hơn thế là hứa một thứ chưa ai đo.
     */
    fun navOpenedWithoutDestination(i: VoiceIntent, lang: Lang = Strings.current): String = done(i, lang) + " — " + Strings.t(
        "đã mở app dẫn đường; nhập lại điểm đến trong app (đường chuyển giao chưa đo trên xe)",
        "navigation app opened; enter the destination there (hand-over not measured on-car yet)",
        lang,
    )

    /** Không có phiên nhạc nào để điều khiển (chưa mở app nhạc, hoặc chưa cấp quyền đọc thông báo). */
    fun noMediaSession(i: VoiceIntent, lang: Lang = Strings.current): String = failed(i, Strings.t(
        "chưa có phiên nhạc nào — mở app nhạc rồi nói lại",
        "no active music session — open a music app first",
        lang,
    ), lang)

    /** Tên bài / ca sĩ / thể loại — Kachi cố ý không tìm offline. */
    fun openVocabMedia(i: VoiceIntent, lang: Lang = Strings.current): String = failed(i, Strings.t(
        "Kachi không tìm bài hát offline — mở app nhạc rồi nói lại ở đó",
        "Kachi does not search songs offline — open a music app and ask there",
        lang,
    ), lang)

    // ═══ V1.1 · Ô + APP ĐÍCH ═════════════════════════════════════════════════════════════════════════════════

    /**
     * Câu nêu một ô **không có trong bố cục đang dùng**.
     *
     * Nói ra **con số thật** thay vì *"số ô không hợp lệ"*: người lái không nhớ bố cục hiện tại có mấy ô, và câu
     * trả lời biết điều đó. Đây cũng là chỗ duy nhất trong cả đường lệnh mà số ô thật được kiểm — `:core` cố ý
     * không kẹp (xem KDoc [VoiceIntent.OpenApp.slot]).
     */
    fun slotOutOfRange(i: VoiceIntent, slotCount: Int, lang: Lang = Strings.current): String = failed(i, Strings.fIn(
        lang,
        "bố cục hiện chỉ có {0} ô",
        "the current layout only has {0} slot(s)", slotCount,
    ), lang)

    /** Câu nêu đích danh một app mà xe **chưa cài**. Nói tên app, không nói tên gói. */
    fun appNotInstalled(i: VoiceIntent, appKey: String, lang: Lang = Strings.current): String =
        failed(i, Strings.fIn(lang, "chưa cài {0} trên xe", "{0} is not installed", VoiceAppTargets.labelOf(appKey)), lang)

    /**
     * *"Phát nhạc"* khi **chưa có phiên nhạc nào** ⇒ đã MỞ app nhạc, và nói rõ phần chưa làm được.
     *
     * [ĐO] `docs/diagnostics/emulator-voice-e2e-2026-09-15.md` §3 L2 (t46/t50): câu *"mở nhạc trên YouTube
     * Music"* phân tích **đúng** (`app=ytmusic`) nhưng `runMedia` vứt trường `app` và rơi thẳng vào transport ⇒
     * trả lời *"chưa có phiên nhạc nào"* và **không app nào lên màn**. Mở app là phần chắc chắn làm được; còn
     * *"tự phát"* thì [ĐO] máy ảo 2026-09-14 cho thấy app dừng ở nút Play, nên câu này nói đúng thế.
     */
    fun musicAppOpened(i: VoiceIntent, target: VoiceAppTarget, lang: Lang = Strings.current): String = done(i, lang) + " — " + Strings.fIn(
        lang,
        "đã mở {0}; chưa có phiên nhạc nào để điều khiển — bấm Play trong app",
        "opened {0}; no music session to control yet — press Play in the app", target.label,
    )

    /** Không có app nhạc nào trong bảng đích có mặt trên xe. */
    fun noMusicApp(i: VoiceIntent, lang: Lang = Strings.current): String = failed(i, Strings.t(
        "chưa có app nhạc nào trên xe",
        "no music app on this car",
        lang,
    ), lang)

    /**
     * Đã **giao chuỗi chữ** cho app đích. Đuôi câu nói đúng thứ [ĐO] được, không hơn.
     *
     * [ĐO] máy ảo 2026-09-14: YT Music mở đúng màn kết quả nhưng **dừng ở nút Play** — không tự phát. Báo
     * *"✓ Tìm bài «X» trên YouTube Music"* rồi im là để người lái ngồi chờ một bài hát không bao giờ kêu.
     *
     * ## ⚠ [autoplay] — từ 1.75 có MỘT đường nữa, và đuôi câu của nó phải khác
     * Hàm này được gọi từ **hai** chỗ trong `VoiceTargetDispatch`: đường `deliver` (ý-định
     * `MEDIA_PLAY_FROM_SEARCH` mang TÊN bài — vẫn dừng ở nút Play, đúng phép đo trên) và đường **watch** (`runMediaQuery`
     * giải `video_id` rồi mở `watch?v=<id>`, app **tự phát**). [ĐO xe 2026-09-20 §5] owner báo câu trả lời vẫn nhắc
     * *"bấm Play"* trong khi nhạc **đã phát** — tức một câu đúng cho đường kia bị đọc cho đường này. Sửa bằng một
     * tham số ở chỗ gọi (nơi BIẾT đường nào đã đi), **không** bằng cách bỏ câu cũ: nó vẫn đúng cho `deliver`, và
     * cho Spotify/Zing (`watch == null`) thì `deliver` là đường duy nhất.
     */
    fun handedOver(i: VoiceIntent, target: VoiceAppTarget, autoplay: Boolean = false, lang: Lang = Strings.current): String {
        val tail = when {
            // Đường watch: video_id đã giải + ý-định đã nhận ⇒ app đang phát. Đứng TRƯỚC nhánh `evidence` vì
            // YouTube còn là AWAITING_CAR mà đường watch của nó vẫn là đường tự-phát (xem KDoc trên).
            autoplay -> Strings.t("đang phát", "playing now", lang)
            target.evidence == VoiceAppEvidence.UNKNOWN -> Strings.fIn(
                lang,
                "chưa kiểm cửa nhận chữ của {0}",
                "{0}'s hand-over door is not verified yet", target.label,
            )
            target.kind == VoiceAppKind.MUSIC -> Strings.t(
                "đã mở kết quả tìm — bấm Play để phát",
                "search results opened — press Play",
                lang,
            )
            else -> return done(i, lang)
        }
        return done(i, lang) + " — " + tail
    }

    /**
     * *"Đang tra…"* — nói ra trước một lượt chờ có thể mất vài giây (giải tên địa điểm thành toạ độ).
     *
     * Im lặng ở đây là ca tệ nhất của cả đường lệnh: người lái vừa nói xong, màn hình không đổi gì, và họ sẽ nói
     * lại lần hai — trong khi lượt thứ nhất vẫn đang chạy.
     */
    fun resolving(i: VoiceIntent, lang: Lang = Strings.current): String =
        preview(i, lang) + " — " + Strings.t("đang tra điểm đến…", "looking the place up…", lang)

    /** *"Đang tìm bài…"* — đọc ngay khi bắt đầu giải video_id (YouTube/YT Music), vì lượt tải HTML mất 1–3 s. */
    fun searchingMusic(i: VoiceIntent, lang: Lang = Strings.current): String =
        preview(i, lang) + " — " + Strings.t("đang tìm bài…", "finding the track…", lang)

    /**
     * Đọc lại **tên nơi mà bên tra cứu trả về** trước khi bắn.
     *
     * Tên ấy KHÁC câu người ta nói (*"chợ bến thành"* → *"Chợ Bến Thành"*, hoặc một nơi trùng tên ở tỉnh khác),
     * và từ đây là app dẫn đường **bắt đầu dẫn luôn**. Một cú chạm để xác nhận rẻ hơn ba mươi cây số sai hướng.
     */
    fun confirmPlace(i: VoiceIntent, target: VoiceAppTarget, place: String, lang: Lang = Strings.current): String = Strings.fIn(
        lang,
        "Dẫn đường tới «{0}» trên {1}?",
        "Navigate to «{0}» on {1}?", place, target.label,
    ) + "\n" + Strings.t(
        "tên này do bên tra cứu trả về, không phải nguyên văn câu vừa nói",
        "this name came from the lookup service, not from what you said",
        lang,
    )

    /**
     * App đích **không có cửa nào** nhận điểm đến ⇒ chỉ mở được app.
     *
     * [ĐO] VietMap Live 3.4.0 (máy ảo 2026-09-14): không đăng ký `geo:`, `vietmaplive://` không mang tham số.
     * Đây là một kết luận đã đo, nên câu trả lời nói thẳng *"gõ tay trong app"* thay vì hứa lần sau sẽ được.
     */
    fun navOpenedNoHandover(i: VoiceIntent, target: VoiceAppTarget, lang: Lang = Strings.current): String = done(i, lang) + " — " + Strings.fIn(
        lang,
        "{0} chưa nhận điểm đến bằng giọng; gõ tay trong app",
        "{0} takes no destination from outside; type it in the app", target.label,
    )

    /**
     * **Không tra ra được điểm đến** (mất mạng, máy chủ tra cứu im, tên không có trong dữ liệu) ⇒ mở app trơn.
     *
     * [SOÁT Pass 3 · P2] Tách khỏi [navOpenedNoHandover] vì hai câu nói hai chuyện khác hẳn: câu kia là *"app
     * này không có cửa"* (một kết luận **đã đo**, đúng mãi), còn câu này là *"lượt tra cứu vừa rồi hỏng"* (thử
     * lại có thể được). Dùng chung một câu là đổ lỗi cho app về một lần mất sóng — và người lái sẽ thôi không
     * bao giờ thử lại nữa.
     */
    fun navNoPlace(i: VoiceIntent, target: VoiceAppTarget, lang: Lang = Strings.current): String = done(i, lang) + " — " + Strings.fIn(
        lang,
        "chưa tra được điểm đến (mạng?), mới chỉ mở {0}",
        "could not look the place up (network?) — only opened {0}", target.label,
    )

    /** App có tên nhưng không mở được (đã gỡ, hoặc ROM chặn mở từ launcher). */
    fun cannotOpen(i: VoiceIntent, lang: Lang = Strings.current): String =
        failed(i, Strings.t("không mở được", "could not open", lang), lang)

    /**
     * L7 — bề mặt đang nói **không nối được** đường đổi bố cục (ô *"Gõ lệnh chữ"* trong Cài đặt là một ca thật:
     * ở đó màn chính có thể chưa dựng).
     *
     * Nói ra thay vì im lặng, và nói ra **chỗ làm được** thay vì một câu chung chung: người dùng vừa nói một câu
     * hoàn toàn hợp lệ, thứ thiếu là dây nối — mà đó không phải lỗi của họ và cũng không phải thứ họ sửa được.
     */
    fun layoutNotHere(i: VoiceIntent, lang: Lang = Strings.current): String = failed(i, Strings.t(
        "chưa đổi được bố cục từ đây — nói ở màn chính, hoặc đổi trong Cài đặt › Màn hình chính",
        "cannot change the layout from here — say it on the home screen, or use Settings › Home screen",
        lang,
    ), lang)

    // ═══ SỔ ĐỊA CHỈ (spec `kachi-voice-addresses.html` R4) ════════════════════════════════════════════════════

    /**
     * Nhãn **chưa có trong sổ** của hồ sơ đang dùng.
     *
     * Nói ra **đúng nhãn còn thiếu và chỗ thêm nó**, không phải *"không hiểu"*: câu người lái vừa nói hoàn toàn
     * hợp lệ, thứ thiếu là dữ liệu — mà đó là thứ họ bổ sung được trong mười giây. Một câu *"không hiểu"* ở đây
     * làm người ta nói lại lần hai, lần ba cho một việc không bao giờ chạy được.
     */
    fun placeNotSaved(i: VoiceIntent, label: String, lang: Lang = Strings.current): String = failed(i, Strings.fIn(
        lang,
        "chưa lưu địa chỉ «{0}» — thêm ở Cài đặt › Dẫn đường › Sổ địa chỉ",
        "no address saved for «{0}» — add it in Settings › Navigation › Address book", label,
    ), lang)

    /**
     * Mục **chỉ có chữ** mà app đích lại chỉ nhận toạ độ ⇒ chỉ mở được app.
     *
     * [ĐO] VietMap Live 3.4.0 không có cửa nhận chữ ([VoiceLaunch.OpenOnly]). Tách khỏi [navOpenedNoHandover] vì
     * ở đây lỗi **sửa được bằng một việc cụ thể**: thêm toạ độ cho mục đó (dán từ app bản đồ). Câu chung chung
     * *"app này không nhận điểm đến"* thì đúng về cơ chế nhưng bỏ mất đúng phần người dùng làm được.
     */
    fun placeNeedsCoords(i: VoiceIntent, target: VoiceAppTarget, lang: Lang = Strings.current): String = done(i, lang) + " — " + Strings.fIn(
        lang,
        "{0} chỉ nhận toạ độ; thêm lat/lng cho mục này trong Sổ địa chỉ",
        "{0} only takes coordinates — add lat/lng to this entry in the address book", target.label,
    )

    /** Câu hỏi lại cho việc [VoiceRisk.CONFIRM] — kèm cả dấu *"chưa kiểm trên xe"* nếu có (xem [unverified]). */
    fun confirmQuestion(i: VoiceIntent, lang: Lang = Strings.current): String {
        val why = VoiceRiskTable.reason(i, lang)
        return preview(i, lang) + unverified(i, lang) + "?" + (why?.let { "\n" + it } ?: "")
    }

    /**
     * Người dùng bấm **Huỷ** ở hộp hỏi lại.
     *
     * [remaining] = số vế **sau** vế bị huỷ trong một câu ghép; chúng **không chạy** (xem KDoc
     * `VoiceDispatcher.submit`). Phải nói ra: im lặng ở đây nghĩa là người ta tưởng nửa câu sau đã chạy rồi.
     */
    fun cancelled(i: VoiceIntent, remaining: Int, lang: Lang = Strings.current): String {
        val head = "✗ " + preview(i, lang) + " — " + Strings.t("đã huỷ", "cancelled", lang)
        if (remaining <= 0) return head
        return head + Strings.fIn(
            lang,
            ", {0} việc sau không chạy",
            ", {0} later step(s) not run", remaining,
        )
    }

    /**
     * Không hiểu — nói rõ **không hiểu ở đâu**, kèm câu gốc để người dùng thấy máy nghe ra cái gì.
     *
     * D3 — ca tính năng đã bỏ gọi ĐÚNG TÊN tính năng và **thay cả câu** (không kèm câu gốc): tra lại bảng thuần
     * bằng chính câu gốc, không mang thêm trường nào vào [VoiceIntent.Unknown]. Thân hai vế ở [VoiceReplyUnknown].
     */
    fun unknown(u: VoiceIntent.Unknown, lang: Lang = Strings.current): String {
        VoiceReplyUnknown.gone(u, lang)?.let { return it }
        val head = VoiceReplyUnknown.head(u, lang)
        return if (u.text.isBlank()) head else "$head: \"${u.text}\""
    }
}
