package com.byd.clusternav.launcher.voice

import com.byd.clusternav.launcher.ActionMacros
import com.byd.clusternav.launcher.CarCapabilities
import com.byd.clusternav.launcher.ControlKind
import com.byd.clusternav.launcher.ControlRegistry
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
 */
object VoiceReply {

    /** Nhãn của bất kỳ mã nào trong bốn bộ đăng ký, theo ngôn ngữ đang dùng; mã lạ ⇒ trả chính mã (không sập). */
    fun labelOf(id: String): String =
        ControlRegistry.byId(id)?.displayLabel
            ?: TelemetryRegistry.byId(id)?.displayLabel
            ?: ActionMacros.byId(id)?.displayLabel
            ?: LauncherActions.byId(id)?.displayLabel
            ?: id

    /**
     * Câu mô tả **việc sắp làm** — dùng cho hộp xác nhận và cho dòng "đã hiểu là…" của màn thử.
     *
     * Luôn nói ra **tên nút + giá trị**, không nói mã: người lái không biết `win_lf` là gì, và một hộp xác nhận mà
     * người ta không đọc hiểu thì chỉ là một cú chạm thừa.
     */
    @Suppress("CyclomaticComplexMethod")
    fun preview(i: VoiceIntent): String = when (i) {
        is VoiceIntent.Control -> controlPreview(i)
        is VoiceIntent.Macro -> Strings.t("Chạy gói ", "Run pack ") + labelOf(i.id)
        is VoiceIntent.Launcher -> Strings.t("Mở ", "Open ") + labelOf(i.id)
        is VoiceIntent.Profile -> Strings.t("Đổi sang hồ sơ ", "Switch to profile ") + ProfileNames.display(i.name)
        is VoiceIntent.Read -> Strings.t("Xem ", "Show ") + labelOf(i.datumId)
        is VoiceIntent.Nav -> Strings.t("Dẫn đường tới ", "Navigate to ") + i.query + by(i.app)
        is VoiceIntent.OpenApp -> Strings.t("Mở ứng dụng ", "Open app ") + i.appName + inSlot(i.slot)
        is VoiceIntent.Media -> mediaPreview(i)
        is VoiceIntent.Unknown -> unknown(i)
    }

    private fun controlPreview(i: VoiceIntent.Control): String {
        val def = ControlRegistry.byId(i.id) ?: return labelOf(i.id)
        val name = def.displayLabel
        if (i.relative != 0) {
            val dir = if (i.relative > 0) Strings.t("Tăng ", "Increase ") else Strings.t("Giảm ", "Decrease ")
            val n = kotlin.math.abs(i.relative)
            return dir + name + " " + Strings.t("$n nấc", "by $n")
        }
        return when (def.kind) {
            ControlKind.TOGGLE ->
                if (i.value == 1) Strings.t("Bật ", "Turn on ") + name else Strings.t("Tắt ", "Turn off ") + name
            ControlKind.COVER -> when {
                i.value == 1 -> Strings.t("Mở ", "Open ") + name
                // T7: mức ≥2 (Nửa…) đọc nhãn từ registry — giọng nói hiện không phát mức này, nhưng câu xem-trước
                // (CapTest/dock) phải nói đúng thứ sẽ gửi, không nói "Mở" cho một lệnh "Nửa".
                (i.value ?: 0) >= 2 && def.displayArgs.getOrNull(i.value!!) != null -> def.displayArgs[i.value!!] + " " + name
                else -> Strings.t("Đóng ", "Close ") + name
            }
            ControlKind.BUTTON -> Strings.t("Bấm ", "Press ") + name
            ControlKind.SELECT -> {
                val arg = i.value?.let { def.displayArgs.getOrNull(it) }
                name + (arg?.let { ": $it" } ?: "")
            }
            ControlKind.STEP -> Strings.t("Đặt ", "Set ") + name + " = " + (i.value ?: "?")
        }
    }

    private fun mediaPreview(i: VoiceIntent.Media): String = when (i.op) {
        VoiceMediaOp.PLAY -> Strings.t("Phát nhạc", "Play") + by(i.app)
        VoiceMediaOp.PAUSE -> Strings.t("Dừng nhạc", "Pause")
        VoiceMediaOp.NEXT -> Strings.t("Bài tiếp theo", "Next track")
        VoiceMediaOp.PREV -> Strings.t("Bài trước", "Previous track")
        // V1.1 — đọc lại tên bài trong ngoặc kép nhọn. Phần này do nhận dạng **tự do** đọc ra (R16), tức chỗ dễ
        // sai nhất trong cả câu; để nó lẫn vào câu trơn thì người nghe không biết máy đang hỏi về đoạn nào.
        VoiceMediaOp.QUERY -> Strings.t("Tìm bài ", "Search ") + "«" + i.query + "»" + by(i.app)
    }

    /** Đuôi *"bằng &lt;app&gt;"* — rỗng khi câu không nêu app. */
    private fun by(appKey: String?): String =
        appKey?.let { Strings.t(" trên ", " on ") + VoiceAppTargets.labelOf(it) } ?: ""

    /** Đuôi *"vào ô N"* — rỗng khi câu không nêu ô. Số giữ **đúng như người ta nói** (1-based). */
    private fun inSlot(slot: Int?): String =
        slot?.let { Strings.t(" vào ô $it", " in slot $it") } ?: ""

    /** Việc đã làm xong. */
    fun done(i: VoiceIntent): String = "✓ " + preview(i) + unverified(i)

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
    private fun unverified(i: VoiceIntent): String {
        val needs = when (i) {
            is VoiceIntent.Control -> CarCapabilities.needsBadge(i.id)
            is VoiceIntent.Macro -> ActionMacros.byId(i.id)?.needsBadge() ?: false
            else -> false
        }
        return if (needs) " — " + Strings.t("chưa kiểm trên xe", "not yet checked on this car") else ""
    }

    /**
     * Việc KHÔNG làm được — xe từ chối lệnh, hoặc app đích không có mặt.
     *
     * Cùng giọng với `MacroResult.notice`: nói ra **tên việc** rồi mới tới lý do. Thành công thì im lặng được, thất
     * bại thì không — người lái nói một câu và không thấy gì xảy ra sẽ nói lại lần hai, lần ba.
     */
    fun failed(i: VoiceIntent, why: String? = null): String =
        "✗ " + preview(i) + (why?.let { " — $it" } ?: " — " + Strings.t("xe không nhận lệnh", "the car refused"))

    /**
     * ═══ Câu cho các ca HỎNG CỤ THỂ ═══════════════════════════════════════════════════════════════════════════
     *
     * Chúng ở đây chứ không ở `:app` vì hai lý do, lý do thứ hai là lý do **máy kiểm được**:
     *  1. cùng giọng, cùng chỗ, cùng cách gọi tên khả năng như mọi câu khác trong lớp này;
     *  2. `:app` **không được** chứa chuỗi tiếng Việt viết cứng (`LauncherI18nContractTest`) — ở đó chữ phải đi qua
     *     `res/values…/strings_kachi.xml`. Nhưng những câu này ghép **nhãn của bộ đăng ký** vào giữa, mà nhãn thì
     *     sống ở `:core`; nhét chúng vào tài nguyên Android sẽ tách câu khỏi cái tên nó đang nói tới.
     */
    fun busy(i: VoiceIntent): String = failed(i, Strings.t("việc trước còn đang chạy", "the previous run is still going"))

    /** Xe chưa trả về số cho datum này (off-car là ca bình thường). */
    fun noReading(datumLabel: String): String =
        datumLabel + ": " + Strings.t("chưa đọc được", "no reading")

    /** Không có app dẫn đường nào trên máy. */
    fun noNavApp(i: VoiceIntent): String =
        failed(i, Strings.t("chưa có app dẫn đường nào trên xe", "no navigation app on this car"))

    /**
     * Đã mở app dẫn đường **nhưng chưa chuyển điểm đến** — nói thẳng phần chưa làm được.
     *
     * [ĐO] RE Kiki §8.2: điểm đến thuộc từ vựng mở; đường đẩy chữ sang Kiki (`text_command`) mới ở mức **[SUY]**,
     * phải chốt bằng phép đo K2 trên xe (CLAUDE.md §14). Hứa hơn thế là hứa một thứ chưa ai đo.
     */
    fun navOpenedWithoutDestination(i: VoiceIntent): String = done(i) + " — " + Strings.t(
        "đã mở app dẫn đường; nhập lại điểm đến trong app (đường chuyển giao chưa đo trên xe)",
        "navigation app opened; enter the destination there (hand-over not measured on-car yet)",
    )

    /** Không có phiên nhạc nào để điều khiển (chưa mở app nhạc, hoặc chưa cấp quyền đọc thông báo). */
    fun noMediaSession(i: VoiceIntent): String = failed(i, Strings.t(
        "chưa có phiên nhạc nào — mở app nhạc rồi nói lại",
        "no active music session — open a music app first",
    ))

    /** Tên bài / ca sĩ / thể loại — Kachi cố ý không tìm offline. */
    fun openVocabMedia(i: VoiceIntent): String = failed(i, Strings.t(
        "Kachi không tìm bài hát offline — mở app nhạc rồi nói lại ở đó",
        "Kachi does not search songs offline — open a music app and ask there",
    ))

    // ═══ V1.1 · Ô + APP ĐÍCH ═════════════════════════════════════════════════════════════════════════════════

    /**
     * Câu nêu một ô **không có trong bố cục đang dùng**.
     *
     * Nói ra **con số thật** thay vì *"số ô không hợp lệ"*: người lái không nhớ bố cục hiện tại có mấy ô, và câu
     * trả lời biết điều đó. Đây cũng là chỗ duy nhất trong cả đường lệnh mà số ô thật được kiểm — `:core` cố ý
     * không kẹp (xem KDoc [VoiceIntent.OpenApp.slot]).
     */
    fun slotOutOfRange(i: VoiceIntent, slotCount: Int): String = failed(i, Strings.t(
        "bố cục hiện chỉ có $slotCount ô",
        "the current layout only has $slotCount slot(s)",
    ))

    /** Câu nêu đích danh một app mà xe **chưa cài**. Nói tên app, không nói tên gói. */
    fun appNotInstalled(i: VoiceIntent, appKey: String): String =
        failed(i, Strings.t("chưa cài ${VoiceAppTargets.labelOf(appKey)} trên xe", "${VoiceAppTargets.labelOf(appKey)} is not installed"))

    /** Không có app nhạc nào trong bảng đích có mặt trên xe. */
    fun noMusicApp(i: VoiceIntent): String = failed(i, Strings.t(
        "chưa có app nhạc nào trên xe",
        "no music app on this car",
    ))

    /**
     * Đã **giao chuỗi chữ** cho app đích. Đuôi câu nói đúng thứ [ĐO] được, không hơn.
     *
     * [ĐO] máy ảo 2026-09-14: YT Music mở đúng màn kết quả nhưng **dừng ở nút Play** — không tự phát. Báo
     * *"✓ Tìm bài «X» trên YouTube Music"* rồi im là để người lái ngồi chờ một bài hát không bao giờ kêu.
     */
    fun handedOver(i: VoiceIntent, target: VoiceAppTarget): String {
        val tail = when {
            target.evidence == VoiceAppEvidence.UNKNOWN -> Strings.t(
                "chưa kiểm cửa nhận chữ của ${target.label}",
                "${target.label}'s hand-over door is not verified yet",
            )
            target.kind == VoiceAppKind.MUSIC -> Strings.t(
                "đã mở kết quả tìm — bấm Play để phát",
                "search results opened — press Play",
            )
            else -> return done(i)
        }
        return done(i) + " — " + tail
    }

    /**
     * *"Đang tra…"* — nói ra trước một lượt chờ có thể mất vài giây (giải tên địa điểm thành toạ độ).
     *
     * Im lặng ở đây là ca tệ nhất của cả đường lệnh: người lái vừa nói xong, màn hình không đổi gì, và họ sẽ nói
     * lại lần hai — trong khi lượt thứ nhất vẫn đang chạy.
     */
    fun resolving(i: VoiceIntent): String = preview(i) + " — " + Strings.t("đang tra điểm đến…", "looking the place up…")

    /**
     * Đọc lại **tên nơi mà bên tra cứu trả về** trước khi bắn.
     *
     * Tên ấy KHÁC câu người ta nói (*"chợ bến thành"* → *"Chợ Bến Thành"*, hoặc một nơi trùng tên ở tỉnh khác),
     * và từ đây là app dẫn đường **bắt đầu dẫn luôn**. Một cú chạm để xác nhận rẻ hơn ba mươi cây số sai hướng.
     */
    fun confirmPlace(i: VoiceIntent, target: VoiceAppTarget, place: String): String = Strings.t(
        "Dẫn đường tới «$place» trên ${target.label}?",
        "Navigate to «$place» on ${target.label}?",
    ) + "\n" + Strings.t(
        "tên này do bên tra cứu trả về, không phải nguyên văn câu vừa nói",
        "this name came from the lookup service, not from what you said",
    )

    /**
     * App đích **không có cửa nào** nhận điểm đến ⇒ chỉ mở được app.
     *
     * [ĐO] VietMap Live 3.4.0 (máy ảo 2026-09-14): không đăng ký `geo:`, `vietmaplive://` không mang tham số.
     * Đây là một kết luận đã đo, nên câu trả lời nói thẳng *"gõ tay trong app"* thay vì hứa lần sau sẽ được.
     */
    fun navOpenedNoHandover(i: VoiceIntent, target: VoiceAppTarget): String = done(i) + " — " + Strings.t(
        "${target.label} chưa nhận điểm đến bằng giọng; gõ tay trong app",
        "${target.label} takes no destination from outside; type it in the app",
    )

    /**
     * **Không tra ra được điểm đến** (mất mạng, máy chủ tra cứu im, tên không có trong dữ liệu) ⇒ mở app trơn.
     *
     * [SOÁT Pass 3 · P2] Tách khỏi [navOpenedNoHandover] vì hai câu nói hai chuyện khác hẳn: câu kia là *"app
     * này không có cửa"* (một kết luận **đã đo**, đúng mãi), còn câu này là *"lượt tra cứu vừa rồi hỏng"* (thử
     * lại có thể được). Dùng chung một câu là đổ lỗi cho app về một lần mất sóng — và người lái sẽ thôi không
     * bao giờ thử lại nữa.
     */
    fun navNoPlace(i: VoiceIntent, target: VoiceAppTarget): String = done(i) + " — " + Strings.t(
        "chưa tra được điểm đến (mạng?), mới chỉ mở ${target.label}",
        "could not look the place up (network?) — only opened ${target.label}",
    )

    /** App có tên nhưng không mở được (đã gỡ, hoặc ROM chặn mở từ launcher). */
    fun cannotOpen(i: VoiceIntent): String = failed(i, Strings.t("không mở được", "could not open"))

    /** Câu hỏi lại cho việc [VoiceRisk.CONFIRM] — kèm cả dấu *"chưa kiểm trên xe"* nếu có (xem [unverified]). */
    fun confirmQuestion(i: VoiceIntent): String {
        val why = VoiceRiskTable.reason(i)
        return preview(i) + unverified(i) + "?" + (why?.let { "\n" + it } ?: "")
    }

    /**
     * Người dùng bấm **Huỷ** ở hộp hỏi lại.
     *
     * [remaining] = số vế **sau** vế bị huỷ trong một câu ghép; chúng **không chạy** (xem KDoc
     * `VoiceDispatcher.submit`). Phải nói ra: im lặng ở đây nghĩa là người ta tưởng nửa câu sau đã chạy rồi.
     */
    fun cancelled(i: VoiceIntent, remaining: Int): String {
        val head = "✗ " + preview(i) + " — " + Strings.t("đã huỷ", "cancelled")
        if (remaining <= 0) return head
        return head + Strings.t(
            ", $remaining việc sau không chạy",
            ", $remaining later step(s) not run",
        )
    }

    /** Không hiểu — nói rõ **không hiểu ở đâu**, kèm câu gốc để người dùng thấy máy nghe ra cái gì. */
    fun unknown(u: VoiceIntent.Unknown): String {
        val head = when (u.reason) {
            VoiceUnknownReason.EMPTY -> Strings.t("Chưa có câu lệnh nào", "No command yet")
            VoiceUnknownReason.NO_VERB -> Strings.t(
                "Chưa rõ cần làm gì — thử \"bật…\", \"mở…\", \"xem…\"",
                "No action word — try \"turn on…\", \"open…\", \"show…\"",
            )
            VoiceUnknownReason.NO_OBJECT -> Strings.t(
                "Không tìm thấy thứ đó trong xe hay trong launcher",
                "No such thing on this car or in the launcher",
            )
            VoiceUnknownReason.MISMATCH -> Strings.t(
                "Việc đó không đi với thứ đó — thử nêu mức, hoặc đổi động từ",
                "That action does not fit that thing — give a level, or use another verb",
            )
            VoiceUnknownReason.OPEN_VOCAB -> Strings.t(
                "Phần này Kachi không tự làm offline (tên bài hát / điểm đến)",
                "Kachi does not do this offline (song names / destinations)",
            )
        }
        return if (u.text.isBlank()) head else "$head: \"${u.text}\""
    }
}
