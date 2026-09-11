package com.byd.clusternav.launcher

/**
 * GÓI LỆNH — một nút chạy NHIỀU việc (W2). Thuần Kotlin (`:core`, cấm `android.*`) ⇒ test off-car tất định.
 * Spec: `docs/specs/kachi-action-macros.html`.
 *
 * ## Vì sao lớp này tồn tại
 * [ĐO] 2026-09-11: trong 64 nút của [ControlRegistry], **mọi nút là MỘT lệnh**. Không có khái niệm nào cho
 * "một nút chạy nhiều việc" ⇒ yêu cầu của owner *"mở cửa + tắt/mở đèn"* hiện **không thể có**. Đây là phần thiếu.
 *
 * ## Vì sao lớp này KHÔNG có luồng
 * Nó chỉ trả lời hai câu: *gói gồm bước nào, thứ tự nào* và *gộp kết quả các bước thì gói hỏng ở đâu*. Việc **chờ**
 * và **bơm lệnh** nằm ở `:app` (dùng lại đúng lối `SeatComfortApplier` / `Pm25FilterApplier` / `RecircApplier`).
 * Nhờ vậy test off-car **không phải chờ thời gian thật** mà vẫn khoá được thứ tự (ràng buộc C1).
 *
 * ## Bất biến
 *  • Bước chỉ **trỏ tới nút ĐÃ KHAI** trong [ControlRegistry] — gói lệnh KHÔNG tự viết lệnh xe mới (R3).
 *  • Mức bằng chứng của gói = **THẤP NHẤT** trong các bước (R5): gói có một bước chưa kiểm thì cả gói mang dấu
 *    chưa-kiểm. Lấy mức cao nhất sẽ hứa quá — đúng cái bẫy dự án này đã trả giá nhiều lần.
 *  • Một bước hỏng **KHÔNG** làm chết cả gói (R2): xe từ chối một lệnh là chuyện thường, và bỏ dở giữa gói còn tệ
 *    hơn (vd đóng 2 kính rồi dừng).
 */

/**
 * Một bước trong gói lệnh.
 *
 * @property controlId mã nút trong [ControlRegistry]. Mã lạ ⇒ gói bị coi là khai SAI ([ActionMacro.invalidSteps]).
 * @property arg tham số chính, cùng nghĩa với [CarControlAdapter.act]: bật/tắt 1/0 · bước nhảy = giá trị ·
 *   đóng/mở 1/0 · chọn = chỉ số · bấm-một-phát bỏ qua.
 * @property waitAfterMs chờ bao lâu SAU bước này rồi mới sang bước kế. 0 = không chờ. Xe cần thời gian phản hồi;
 *   bắn liên tiếp không chờ thì lệnh sau có thể bị bỏ.
 */
data class MacroStep(
    val controlId: String,
    val arg: Int,
    val waitAfterMs: Long = ActionMacros.DEFAULT_GAP_MS,
)

/**
 * Một gói lệnh định sẵn.
 *
 * @property id mã gói — nằm CÙNG không gian mã phẳng với datum/nút/widget (xem [CapabilityCatalog]); tiền tố
 *   `mac_` để người đọc code nhận ra ngay, và để test khoá xung đột dễ chỉ đích danh.
 * @property steps các bước, **theo thứ tự chạy**. Rỗng = gói vô nghĩa (bị [invalidSteps] bắt).
 */
data class ActionMacro(
    val id: String,
    val label: String,
    val icon: String,
    val domain: Domain,
    val steps: List<MacroStep>,
) {
    /** Mã bước trỏ tới nút KHÔNG tồn tại. Phải rỗng — bị test khoá (R3). */
    fun invalidSteps(): List<String> = steps.map { it.controlId }.filter { ControlRegistry.byId(it) == null }

    /**
     * Mức bằng chứng của gói = **THẤP NHẤT** trong các bước (R5). Gói rỗng ⇒ coi như chỉ-xác-nhận-được-trên-xe
     * (không có gì chứng minh nó chạy).
     *
     * Thứ hạng lấy từ **thứ tự khai của [EvidenceTier]** (KDoc của nó khai đúng thứ tự tin cậy giảm dần: PROVEN →
     * OVERDRIVE → DASHCAST → NEEDS_CAR) nên phép so **luôn phủ đủ mọi giá trị enum**. Bản đầu dùng một danh sách
     * xếp hạng viết tay + `indexOf`: thêm một tier mới mà quên thêm vào danh sách thì `indexOf` trả `-1` và tier đó
     * bị coi là yếu nhất một cách âm thầm. Thứ tự khai bị test khoá (`thu tu khai cua EvidenceTier LA thu hang`).
     */
    fun tier(): EvidenceTier {
        val tiers = steps.mapNotNull { ControlRegistry.byId(it.controlId)?.tier }
        return tiers.maxByOrNull { it.ordinal } ?: EvidenceTier.NEEDS_CAR
    }

    /**
     * Có cần dấu "chưa kiểm trên xe" không.
     *
     * ⚠ **KHÔNG** dùng [EvidenceTier.needsBadge] ở đây. Cờ đó chỉ đúng cho OVERDRIVE/DASHCAST và **trả `false` cho
     * NEEDS_CAR** — vì với mục ĐỌC, NEEDS_CAR đã tự lộ ra bằng "—" + mờ (không có số thì người xem biết ngay). Gói
     * lệnh là **NÚT**: không có con số nào để mờ, nên dùng cờ đó thì gói yếu nhất ([EvidenceTier.NEEDS_CAR], vd
     * `mac_door_light` có bước `door` chưa xác nhận) hiện **y như** gói đã chạy thật — hứa quá đúng chỗ nguy hiểm
     * nhất. Ở đây: **không PROVEN thì phải mang dấu**.
     */
    fun needsBadge(): Boolean = tier() != EvidenceTier.PROVEN
}

/** Kết quả chạy MỘT bước — để chỗ gọi biết chính xác bước nào hỏng, không chỉ "gói hỏng". */
data class MacroStepResult(val controlId: String, val ok: Boolean)

/**
 * Kết quả chạy cả gói.
 * @property results theo ĐÚNG thứ tự các bước.
 */
data class MacroResult(val macroId: String, val results: List<MacroStepResult>) {
    val total: Int get() = results.size
    val okCount: Int get() = results.count { it.ok }
    val failed: List<String> get() = results.filter { !it.ok }.map { it.controlId }

    /** Mọi bước đều ăn. */
    val allOk: Boolean get() = results.isNotEmpty() && results.all { it.ok }

    /** KHÔNG bước nào ăn — off-car thì đây là ca bình thường (mọi lệnh no-op). */
    val allFailed: Boolean get() = results.isNotEmpty() && results.none { it.ok }

    /**
     * Câu báo CHO NGƯỜI DÙNG, hoặc `null` khi mọi bước đều ăn (thành công thì im lặng — không ai muốn bị thông báo
     * mỗi lần bấm đúng).
     *
     * Vì sao cần: trước đây kết quả gói chỉ đi vào nhật ký, mà người lái **không bao giờ đọc nhật ký**. Hệ quả thật:
     * bấm "Rời xe" xong xe khoá nhưng kính chưa đóng — và không có gì nói cho người ta biết. Luật "một bước hỏng
     * không dừng gói" (R2) là đúng, nhưng nó KHÔNG cho phép im lặng về việc đã hỏng.
     *
     * Gọi bước hỏng bằng **nhãn** chứ không bằng mã, vì đây là câu cho người đọc.
     */
    fun notice(macroLabel: String): String? {
        if (results.isEmpty()) return null
        if (allOk) return null
        val names = failed.map { ControlRegistry.byId(it)?.label ?: it }.distinct()
        return if (allFailed) "$macroLabel: xe không nhận lệnh nào"
        else "$macroLabel: chưa làm được — ${names.joinToString(", ")}"
    }

    /** Một câu ngắn cho nhật ký / thông báo. */
    fun summary(): String = when {
        results.isEmpty() -> "gói rỗng"
        allOk -> "đủ $total bước"
        allFailed -> "không bước nào ăn ($total bước)"
        else -> "$okCount/$total bước ăn; hỏng: ${failed.joinToString(", ")}"
    }
}

/**
 * BỘ CHẠY THUẦN — nhận một hàm bơm lệnh, chạy tuần tự các bước, gộp kết quả.
 *
 * [emit] trả `true` nếu xe nhận lệnh. Chỗ gọi ở `:app` truyền `CarControlAdapter::act`; test truyền hàm giả để
 * kiểm thứ tự. [sleep] tách ra để test **không phải chờ thật** (mặc định no-op ⇒ test chạy tức thời).
 *
 * KHÔNG dừng khi một bước hỏng (R2).
 */
object MacroRunner {
    fun run(
        macro: ActionMacro,
        emit: (id: String, arg: Int) -> Boolean,
        sleep: (Long) -> Unit = {},
    ): MacroResult {
        val out = ArrayList<MacroStepResult>(macro.steps.size)
        macro.steps.forEachIndexed { i, step ->
            val ok = runCatching { emit(step.controlId, step.arg) }.getOrDefault(false)
            out.add(MacroStepResult(step.controlId, ok))
            // Chờ SAU bước, và không chờ sau bước CUỐI (chờ xong rồi chẳng làm gì nữa là phí).
            if (step.waitAfterMs > 0 && i < macro.steps.lastIndex) sleep(step.waitAfterMs)
        }
        return MacroResult(macro.id, out)
    }
}

/**
 * CÁC GÓI ĐỊNH SẴN. Chỉ đưa vào gói **làm được tử tế** và **đảo lại được** (ràng buộc C5).
 *
 * ⚠ Owner đã bỏ mọi cổng an toàn ("tự dùng tự chịu") ⇒ chỗ ĐỊNH NGHĨA gói là nơi duy nhất còn tiết chế được:
 * một nút chạy nhiều lệnh thì hậu quả nhân lên. Vì vậy KHÔNG gộp việc không đảo lại được, và KHÔNG gộp việc
 * ảnh hưởng an toàn khi xe đang lăn bánh.
 */
object ActionMacros {

    /** Nhịp chờ mặc định giữa hai bước (spec OQ1). Đủ để xe xử lý lệnh trước, không lâu tới mức tưởng treo. */
    const val DEFAULT_GAP_MS = 400L

    /** 4 kính theo thứ tự trước-trái, trước-phải, sau-trái, sau-phải — đều ở mức ĐÃ CHẠY TRÊN XE. */
    private val WINDOW_IDS = listOf("win_lf", "win_rf", "win_lr", "win_rr")

    private fun windows(open: Boolean): List<MacroStep> =
        WINDOW_IDS.map { MacroStep(it, if (open) 1 else 0) }

    val ALL: List<ActionMacro> = listOf(
        // Vì sao gộp 4 nút riêng thay vì dùng nút "Tất cả kính" đã có: nút gộp đó ở mức CHƯA KIỂM, còn 4 nút riêng
        // đều ĐÃ CHẠY trên xe owner ⇒ gói này khả năng ăn cao hơn. Đây là giá trị cụ thể của lớp gộp lệnh.
        ActionMacro("mac_win_open_all", "Mở hết kính", "ic-window", Domain.BODY, windows(open = true)),
        ActionMacro("mac_win_close_all", "Đóng hết kính", "ic-window", Domain.BODY, windows(open = false)),
        // Yêu cầu số 7 của owner: "mở cửa + tắt/mở đèn".
        ActionMacro(
            "mac_door_light", "Mở cửa + đèn đọc", "ic-door", Domain.BODY,
            listOf(MacroStep("door", 1), MacroStep("readl", 1, waitAfterMs = 0)),
        ),
        // Ca dùng thật khi rời xe. Mọi bước đảo lại được (C5).
        ActionMacro(
            "mac_leave", "Rời xe", "ic-lock", Domain.BODY,
            windows(open = false) + listOf(MacroStep("readl", 0), MacroStep("lock", 1, waitAfterMs = 0)),
        ),
    )

    fun byId(id: String): ActionMacro? = ALL.firstOrNull { it.id == id }

    /** Gói khai SAI (bước trỏ mã lạ, hoặc rỗng). Phải rỗng — bị test khoá. */
    fun invalid(): List<String> = ALL.filter { it.steps.isEmpty() || it.invalidSteps().isNotEmpty() }.map { it.id }
}
