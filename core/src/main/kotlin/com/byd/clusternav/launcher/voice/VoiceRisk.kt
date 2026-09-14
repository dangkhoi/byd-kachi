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
    )

    /** Gói lệnh cần hỏi lại — gói *"mở hết kính"* có đúng hậu quả với nút `windows_all`. */
    val MACRO_IDS: Set<String> = setOf("mac_win_open_all")

    /**
     * Mức rủi ro của [intent].
     *
     * Đổi HỒ SƠ là [VoiceRisk.CONFIRM] vì từ S4 *"hồ sơ là tất cả"*: một câu đổi hồ sơ thay **toàn bộ** bố cục,
     * thanh nút, chip, chủ đề và cả cấu hình ClusterNav của màn đang dùng. Đó là thay đổi lớn nhất mà một câu nói
     * gây ra được trong app này.
     */
    fun of(intent: VoiceIntent): VoiceRisk = when (intent) {
        is VoiceIntent.Read -> VoiceRisk.SAFE
        is VoiceIntent.Unknown -> VoiceRisk.SAFE
        is VoiceIntent.Profile -> VoiceRisk.CONFIRM
        is VoiceIntent.Control ->
            if (CONTROL_RULES.any { it.controlId == intent.id && (it.value == null || it.value == intent.value) }) {
                VoiceRisk.CONFIRM
            } else {
                VoiceRisk.NORMAL
            }
        is VoiceIntent.Macro -> if (intent.id in MACRO_IDS) VoiceRisk.CONFIRM else VoiceRisk.NORMAL
        else -> VoiceRisk.NORMAL
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
        else -> null
    }
}
