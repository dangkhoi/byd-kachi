package com.byd.clusternav.launcher.voice

import com.byd.clusternav.launcher.Strings

/**
 * Mức rủi ro của một ý định.
 *  • [SAFE]    — chỉ ĐỌC, không đổi gì ngoài màn hình.
 *  • [NORMAL]  — đổi trạng thái xe/launcher, đảo lại được bằng đúng một câu ngược lại.
 *  • [CONFIRM] — phải có một cú chạm "đồng ý" nữa mới bắn.
 */
enum class VoiceRisk { SAFE, NORMAL, CONFIRM }

/**
 * ═══ V1 · BẢNG AN TOÀN ════════════════════════════════════════════════════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-voice-command.html` R4. `:core` thuần ⇒ kiểm off-car.
 *
 * ## Vì sao voice cần một cổng mà nút bấm KHÔNG cần
 * Owner đã bỏ mọi cổng an toàn cho nút bấm (2026-09-10, *"tự dùng tự chịu"*) và điều đó đúng với **nút**: muốn mở
 * khoá xe thì phải nhìn đúng ô, chạm đúng chỗ — bản thân thao tác đã là một xác nhận. Câu nói thì không có tính
 * chất đó: nó **kích hoạt được bằng tai nghe nhầm**. Một câu của người ngồi ghế sau, một dòng thoại trên đài, một
 * lần nhận dạng sai — cả ba đều dựng ra đúng cùng một lệnh mà không ai chạm vào màn.
 *
 * ⇒ Cổng này **không** phải cổng an toàn kiểu cũ (không đọc tốc độ, không chặn theo số). Nó chỉ hỏi lại **đúng bốn
 * việc** mà hậu quả không tự đảo lại được trong vài giây, và hỏi bằng một cú chạm — người lái vẫn toàn quyền.
 *
 * ## Bảng khai TƯỜNG MINH, không suy ra từ `domain`/`tier`
 * Suy từ `domain = BODY` sẽ kéo theo cả gạt mưa và gập gương (vô hại), còn bỏ sót *"dừng chiếu cụm"* (thuộc
 * INFOTAINMENT nhưng làm **tắt màn hình đồng hồ đang dẫn đường**). Mức rủi ro là một thuộc tính của **việc**, không
 * phải của nhóm — nên nó được viết ra, và mỗi dòng có lý do đọc được.
 */
object VoiceRiskTable {

    /**
     * Nút cần hỏi lại, kèm **điều kiện giá trị** và lý do.
     *
     * `value == null` ⇒ mọi giá trị đều hỏi (nút BẤM một chiều).
     */
    data class Rule(val controlId: String, val value: Int?, val whyVi: String, val whyEn: String) {
        /** Lý do theo ngôn ngữ đang dùng — nó HIỆN trong hộp xác nhận, nên phải dịch như mọi chữ khác. */
        fun why(): String = Strings.t(whyVi, whyEn)
    }

    val CONTROL_RULES: List<Rule> = listOf(
        Rule("door", null,
            "mở khoá toàn xe — nghe nhầm một câu là xe mở khoá giữa bãi đỗ",
            "unlocks the whole car — one misheard sentence opens it in a car park"),
        Rule("lock", 0,
            "tắt khoá nghĩa là MỞ khoá — cùng hậu quả với nút \"Mở khoá cửa\"",
            "turning the lock off means UNLOCKING — same consequence as the unlock button"),
        Rule("windows_all", 1,
            "hạ HẾT 4 kính — mưa, bụi, hoặc đồ để trên ghế; đóng lại mất nhiều giây",
            "lowers ALL four windows — rain, dust, or belongings on the seats; closing takes seconds"),
        Rule("cast", 0,
            "dừng chiếu cụm khi đang dẫn đường = mất màn chỉ đường giữa đường",
            "stopping the cluster cast mid-route removes the turn-by-turn screen"),
        // V3 · R7 — hai dòng THÊM 2026-09-16: owner liệt kê chúng trong bảng B (B5 cốp · B7 cửa sổ trời) như
        // những việc *có thể* muốn hỏi. Chúng vào đây để **hiện ra trong danh sách chọn**, không phải để bật —
        // mặc định vẫn là KHÔNG hỏi gì (xem [of]).
        Rule("trunk", 1,
            "mở cốp khi xe đang đỗ nơi công cộng — đồ trong cốp phơi ra cho tới khi có người đóng lại",
            "opens the boot in a public car park — whatever is inside stays exposed until someone closes it"),
        Rule("sunroof", 1,
            "mở cửa sổ trời — mưa và bụi vào thẳng khoang, đóng lại mất nhiều giây",
            "opens the sunroof — rain and dust go straight in, and closing takes seconds"),
    )

    /** Gói lệnh cần hỏi lại — gói *"mở hết kính"* có đúng hậu quả với nút `windows_all`. */
    val MACRO_IDS: Set<String> = setOf("mac_win_open_all")

    // ── V3 · R7 — mã của MỘT VIỆC CÓ THỂ HỎI, và tập đang được bật ───────────────────────────────

    /** Mã của việc *"đổi hồ sơ"* trong tập `voice_confirm_ids`. */
    const val ID_PROFILE = "profile"

    /** Mã của việc *"dẫn đường tới một chuỗi do nhận dạng tự do đọc ra"*. */
    const val ID_NAV_QUERY = "nav_query"

    /** Mã của việc *"mở bài/nghệ sĩ do nhận dạng tự do đọc ra"*. */
    const val ID_MEDIA_QUERY = "media_query"

    /** Tiền tố mã cho một NÚT · một GÓI LỆNH. Tách tiền tố vì `trunk` có thể vừa là nút vừa là tên gói lệnh. */
    const val PREFIX_CONTROL = "control:"
    const val PREFIX_MACRO = "macro:"

    /**
     * Mã *"việc này là việc gì"* để tra trong tập `voice_confirm_ids`, hoặc `null` khi việc ấy **không hỏi được**
     * (chỉ đọc / không hiểu / sổ địa chỉ / lệnh nhạc thường).
     *
     * ⚠ Giá trị (bật hay tắt) **không** vào mã: người dùng tích *"Kính (tất cả)"* là tích cả hai chiều. Nhét giá
     * trị vào mã sẽ đẻ ra hai dòng `windows_all` trong màn chọn, và không ai đọc được khác nhau ở đâu.
     */
    fun confirmId(intent: VoiceIntent): String? = when (intent) {
        is VoiceIntent.Profile -> ID_PROFILE
        is VoiceIntent.Control ->
            if (CONTROL_RULES.any { it.controlId == intent.id }) PREFIX_CONTROL + intent.id else null
        is VoiceIntent.Macro -> if (intent.id in MACRO_IDS) PREFIX_MACRO + intent.id else null
        is VoiceIntent.Nav -> if (intent.query.isBlank()) null else ID_NAV_QUERY
        is VoiceIntent.Media -> if (intent.op == VoiceMediaOp.QUERY && intent.query.isNotBlank()) ID_MEDIA_QUERY else null
        else -> null
    }

    /** Mọi mã có thể bật trong Cài đặt, theo thứ tự hiện ra. Sinh từ hai bảng trên — không chép tay. */
    fun askableIds(): List<String> =
        CONTROL_RULES.map { PREFIX_CONTROL + it.controlId } +
            MACRO_IDS.sorted().map { PREFIX_MACRO + it } +
            listOf(ID_PROFILE, ID_NAV_QUERY, ID_MEDIA_QUERY)

    /**
     * ═══ V3 · R7 — MẶC ĐỊNH **KHÔNG HỎI GÌ CẢ** ═════════════════════════════════════════════════════════════
     *
     * ## ⚠⚠ Đổi hành vi 2026-09-16 — owner chốt, và đây là lý do
     * Tới 1.65 bốn dòng [CONTROL_RULES] + gói kính + đổi hồ sơ + dẫn đường **luôn** hỏi lại. Owner sau lượt xe
     * 09-16: *"cái nào nguy hiểm lái xe mới hỏi, chứ mở cửa hỏi làm gì? cần document lại cái nào cần đồng ý để
     * tôi chọn"*, và bảng B trả lời B1–B12 = **chạy luôn**, B13 = *"muốn có mục trong Cài đặt để tự bật"*.
     *
     * [ĐO xe 2026-09-16] cái giá thật của cổng mặc-định-bật: câu *"mở kính lái"* rơi vào nút gộp `windows_all`
     * ⇒ hộp *"Hạ hết 4 kính?"* ⇒ mở micro **5,4 s** ⇒ người lái nói *"ừ"* ⇒ bị bỏ ⇒ **huỷ, không nói gì**. Tức
     * một câu đúng, nghe đúng, và kết quả là im lặng sau 20 giây. Cổng an toàn ấy không chặn được lỗi nào trong
     * lượt đó; nó chỉ chặn đúng thứ nó phải cho qua.
     *
     * ⇒ Cơ chế **giữ nguyên** (bảng lý do, hộp hỏi, cổng "không bao giờ tự đồng ý"); thứ đổi là **ai bật nó**.
     * Tập rỗng ⇒ mọi việc là [VoiceRisk.NORMAL]. Cầu kiểm thử (`--ez auto_confirm`) không đổi một dòng nào.
     *
     * @param confirmIds tập mã đang bật, đọc từ prefs `voice_confirm_ids` (device-level). Mặc định **rỗng**.
     */
    fun of(intent: VoiceIntent, confirmIds: Set<String> = emptySet()): VoiceRisk = when (intent) {
        is VoiceIntent.Read -> VoiceRisk.SAFE
        is VoiceIntent.Unknown -> VoiceRisk.SAFE
        // Sổ địa chỉ (spec `kachi-voice-addresses.html` §4.2) — **KHÔNG** hỏi lại, và đó là một quyết định, không
        // phải một chỗ bỏ sót: nhãn đến từ một tập ĐÓNG mà chính người dùng đã gõ trong Cài đặt, địa chỉ thì họ
        // đã đọc lại lúc lưu, và đi nhầm đường thì quay đầu được.
        is VoiceIntent.NavigateSaved -> VoiceRisk.NORMAL
        else -> if (confirmId(intent)?.let { it in confirmIds } == true) VoiceRisk.CONFIRM else VoiceRisk.NORMAL
    }

    /**
     * Tên bài / điểm đến ⇒ [VoiceRisk.CONFIRM], **khác** mọi việc khác trong bảng này.
     *
     * ## Vì sao một việc VÔ HẠI lại phải hỏi lại
     * Ba dòng trên hỏi vì **hậu quả** không đảo lại được (xe mở khoá, kính hạ hết). Dòng này hỏi vì **nguồn**:
     * từ 1.50 phần đuôi của câu do bộ nhận dạng **TỰ DO** đọc ra (R16 — không ngữ pháp, không tập đóng), tức
     * chính xác kém hơn hẳn phần còn lại của câu. Máy nghe *"Diễm Xưa"* thành *"điểm xưa"* thì:
     *  • nếu là bài hát — app mở nhầm kết quả, người lái phải sửa tay **giữa lúc đang lái**;
     *  • nếu là điểm đến — [ĐO] Google Maps/Waze **bắt đầu dẫn đường luôn** (`navigate=yes`), tức xe được chỉ
     *    sang một hướng khác mà không ai kịp đọc.
     * Một cú chạm "Đồng ý" sau khi nghe máy đọc lại *"Tìm bài «Diễm Xưa» trên YouTube Music"* rẻ hơn hẳn cả hai.
     *
     * Câu rỗng thì không có gì để đọc lại ⇒ [VoiceRisk.NORMAL] (*"phát nhạc"* vẫn là một cú chạm như trước).
     */
    private fun openVocabWhy(): String = Strings.t(
        "đoạn trong ngoặc do nhận dạng tự do đọc ra — kém chính xác hơn phần còn lại của câu",
        "the quoted part came from free-form recognition — less accurate than the rest",
    )

    /**
     * Nhãn + lý do của MỘT mã hỏi-được, cho màn Cài đặt bày ra. `null` = mã lạ (pref cũ) ⇒ chỗ gọi bỏ qua.
     *
     * Nhãn lấy từ chính bộ đăng ký ([ControlRegistry] / [ActionMacros]) — thêm một nút vào [CONTROL_RULES] là
     * màn chọn tự có dòng mới với đúng chữ đang hiện trên nút, không phải sửa hai chỗ (CLAUDE.md §7).
     */
    fun askableLabel(id: String): Pair<String, String>? = when {
        id.startsWith(PREFIX_CONTROL) -> {
            val cid = id.removePrefix(PREFIX_CONTROL)
            val rule = CONTROL_RULES.firstOrNull { it.controlId == cid } ?: return null
            val label = com.byd.clusternav.launcher.ControlRegistry.byId(cid)?.displayLabel ?: cid
            label to rule.why()
        }
        id.startsWith(PREFIX_MACRO) -> {
            val mid = id.removePrefix(PREFIX_MACRO)
            if (mid !in MACRO_IDS) return null
            val label = com.byd.clusternav.launcher.ActionMacros.ALL.firstOrNull { it.id == mid }?.displayLabel ?: mid
            label to Strings.t("hạ hết kính", "lowers every window")
        }
        id == ID_PROFILE -> Strings.t("Đổi hồ sơ", "Switch profile") to Strings.t(
            "đổi hồ sơ thay toàn bộ bố cục và cấu hình đang dùng",
            "switching profile replaces the whole layout and current settings",
        )
        id == ID_NAV_QUERY -> Strings.t("Dẫn đường tới nơi vừa đọc", "Navigate to a spoken place") to openVocabWhy()
        id == ID_MEDIA_QUERY -> Strings.t("Mở bài vừa đọc", "Play a spoken track") to openVocabWhy()
        else -> null
    }

    /** Vì sao việc này phải hỏi lại — hiện thẳng trong hộp xác nhận, không giấu trong mã. */
    fun reason(intent: VoiceIntent): String? = when (intent) {
        is VoiceIntent.Profile -> Strings.t(
            "đổi hồ sơ thay toàn bộ bố cục và cấu hình đang dùng",
            "switching profile replaces the whole layout and current settings",
        )
        is VoiceIntent.Control -> CONTROL_RULES
            .firstOrNull { it.controlId == intent.id && (it.value == null || it.value == intent.value) }?.why()
        is VoiceIntent.Macro -> if (intent.id in MACRO_IDS) {
            Strings.t("hạ hết kính", "lowers every window")
        } else {
            null
        }
        is VoiceIntent.Nav -> openVocabReason(intent.query)
        is VoiceIntent.Media -> if (intent.op == VoiceMediaOp.QUERY) openVocabReason(intent.query) else null
        else -> null
    }

    /** Lý do cho dòng TỪ VỰNG MỞ — xem KDoc [askableLabel]. Câu rỗng thì không có gì để đọc lại ⇒ không lý do. */
    private fun openVocabReason(query: String): String? = if (query.isBlank()) null else openVocabWhy()
}
