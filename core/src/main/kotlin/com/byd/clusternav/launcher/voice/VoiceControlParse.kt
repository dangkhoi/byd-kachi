package com.byd.clusternav.launcher.voice

import com.byd.clusternav.launcher.ControlDef
import com.byd.clusternav.launcher.ControlKind
import com.byd.clusternav.launcher.ControlRegistry
import com.byd.clusternav.launcher.voice.VoiceLexicon.Token

/**
 * ═══ Diễn giải ĐỐI SỐ của một câu lệnh điều khiển ════════════════════════════════════════════════════════════
 *
 * Tách khỏi [VoiceIntentParser] (trần 500 dòng) theo **VAI**: [VoiceIntentParser] quyết *"đây là câu điều khiển
 * nút X với động từ V"* rồi giao xuống đây; ở đây bốn kiểu nút đọc số/nhãn/bước từ phần đuôi tokens:
 *  • TOGGLE/COVER — bật/tắt/mở/đóng (SET có số ⇒ >0 là bật);
 *  • BUTTON — bấm-một-phát (không có mặt "tắt" để nói dối);
 *  • SELECT — khớp nhãn lựa chọn (VI/EN) hoặc số thứ tự;
 *  • STEP — [step] (nhiệt độ / gió / âm lượng): số-trong-dải của control min-cao = SETPOINT tuyệt đối, còn lại
 *    là số-bước tương đối — xem [isSetpoint], gốc [ĐO xe 2026-09-17 · log 81 lượt].
 *
 * `:core`, thuần, cấm `android.*`.
 */
internal object VoiceControlParse {

    /** Câu điều khiển nút [id] với động từ [verb] + đuôi [after] ⇒ [VoiceIntent.Control] (hoặc Unknown/MISMATCH). */
    @Suppress("ReturnCount")
    fun control(id: String, verb: VoiceVerb, after: List<Token>, original: String, half: Boolean = false): VoiceIntent {
        val def = ControlRegistry.byId(id) ?: return VoiceIntent.Unknown(VoiceUnknownReason.NO_OBJECT, original)
        val num = firstNumber(after)
        // (c) [ĐO log xe 1.79] "mở/chỉnh điều hòa 25 độ" — "điều hòa"/"máy lạnh" khớp `ac_auto` (TOGGLE) nên MẤT
        // số → bật AUTO thay vì đặt nhiệt. Có cụm "<số> độ" ⇒ ý người dùng là ĐẶT nhiệt độ AC → chuyển sang nút
        // `temp` (setpoint, [ĐO] ghi được trên xe). "bật/tắt điều hòa" (KHÔNG số) vẫn là ac_auto.
        if (id == "ac_auto") {
            degreesSetpoint(after)?.let { deg ->
                ControlRegistry.byId("temp")?.let { return VoiceIntent.Control("temp", it.clamp(deg)) }
            }
        }
        return when (def.kind) {
            ControlKind.TOGGLE, ControlKind.COVER -> when (verb) {
                // COVER (kính) + «mở … một nửa / 50%» ⇒ mức NỬA (value 2 → HAL state 4 = ~50%). [ĐO] enum
                // WINDOW_OPEN_HALF=4 proven per-window; TOGGLE không có mức nửa nên cờ half bị bỏ qua.
                VoiceVerb.ON, VoiceVerb.OPEN ->
                    if (half && def.kind == ControlKind.COVER) VoiceIntent.Control(id, 2) else VoiceIntent.Control(id, 1)
                // "dừng chiếu cụm" = tắt nút `cast`. Không có nhánh này thì đúng câu người ta hay nói nhất cho
                // việc **dừng** một thứ đang chạy lại rơi vào MISMATCH.
                VoiceVerb.OFF, VoiceVerb.CLOSE, VoiceVerb.PAUSE -> VoiceIntent.Control(id, 0)
                VoiceVerb.SET -> num?.let { VoiceIntent.Control(id, if (it > 0) 1 else 0) }
                    ?: VoiceIntent.Unknown(VoiceUnknownReason.MISMATCH, original)
                else -> VoiceIntent.Unknown(VoiceUnknownReason.MISMATCH, original)
            }
            // Nút BẤM-một-phát: không có mặt "tắt" nào để nói dối, nên mọi động từ hành động đều là "bấm".
            ControlKind.BUTTON -> VoiceIntent.Control(id, null)
            ControlKind.SELECT -> selectIndex(def, after)?.let { VoiceIntent.Control(id, it) }
                ?: offOnSelect(def, verb)
                ?: VoiceIntent.Unknown(VoiceUnknownReason.MISMATCH, original)
            ControlKind.STEP -> step(def, verb, num, original)
        }
    }

    /**
     * Câu có nhắc *"nửa / một nửa / 50% / 50 phần trăm"* không — để mở kính (COVER) tới mức NỬA.
     *
     * Dò trên **cả câu** (không chỉ phần đuôi sau object): người ta nói *"mở **một nửa** kính"* — chữ *"nửa"* đứng
     * TRƯỚC object nên không bao giờ vào `after`. Chỉ COVER dùng cờ này (kính), control khác bỏ qua.
     */
    fun mentionsHalf(tokens: List<Token>): Boolean =
        tokens.any { it.norm == "nua" } || tokens.indices.any { VoiceLexicon.readNumber(tokens, it)?.value == 50 }

    /**
     * Bước nhảy (nhiệt độ / gió / âm lượng / độ sáng).
     *
     * *"tăng/giảm"* KHÔNG số ⇒ **tương đối** ±1 (xem KDoc [VoiceIntent.Control.relative]). Nêu số:
     *  • với **SET/đặt/chỉnh** ⇒ luôn TUYỆT ĐỐI (đã kẹp trong `min..max` bằng chính [ControlDef.clamp]);
     *  • với **tăng/giảm** ⇒ [ĐO xe 2026-09-17 · log 81 lượt]: *"tăng nhiệt độ hai mươi bốn độ"* PHẢI là **đặt =
     *    24**, không phải **+24** (cộng 24 vào 22 = 46, kẹt trần 33 — đúng lỗi owner báo *"chỉnh máy lạnh 24 độ
     *    không hiểu"*). Phân biệt bằng [isSetpoint]: nhiệt độ có dải 17..33 nên **không** trùng với số-bước (1..5),
     *    nên một số trong dải là SETPOINT. Gió/âm lượng (dải bắt đầu từ 0) thì *"tăng 2"* thật sự nhập nhằng nên
     *    GIỮ tương đối (không phá *"giảm âm lượng hai nấc"* = −2 đang đúng trong log).
     */
    private fun step(def: ControlDef, verb: VoiceVerb, num: Int?, original: String): VoiceIntent = when (verb) {
        VoiceVerb.UP -> when {
            num == VoiceLexicon.MAX -> VoiceIntent.Control(def.id, def.max)
            num != null && isSetpoint(def, num) -> VoiceIntent.Control(def.id, def.clamp(num))
            else -> VoiceIntent.Control(def.id, null, relative = num?.takeIf { plainStep(it) } ?: 1)
        }
        VoiceVerb.DOWN -> when {
            num == VoiceLexicon.MIN -> VoiceIntent.Control(def.id, def.min)
            num != null && isSetpoint(def, num) -> VoiceIntent.Control(def.id, def.clamp(num))
            else -> VoiceIntent.Control(def.id, null, relative = -(num?.takeIf { plainStep(it) } ?: 1))
        }
        VoiceVerb.SET, VoiceVerb.ON, VoiceVerb.OPEN -> when (num) {
            null -> VoiceIntent.Unknown(VoiceUnknownReason.MISMATCH, original)
            VoiceLexicon.MAX -> VoiceIntent.Control(def.id, def.max)
            VoiceLexicon.MIN -> VoiceIntent.Control(def.id, def.min)
            else -> VoiceIntent.Control(def.id, def.clamp(num))
        }
        VoiceVerb.OFF, VoiceVerb.CLOSE -> VoiceIntent.Control(def.id, def.min)
        else -> VoiceIntent.Unknown(VoiceUnknownReason.MISMATCH, original)
    }

    /**
     * Số [num] sau *"tăng/giảm"* có phải một SETPOINT tuyệt đối không (thay vì số-bước tương đối).
     *
     * Đúng khi control có dải **không trùng với số-bước thông thường**: nhiệt độ 17..33 — không ai *"tăng 17 nấc"*,
     * nên *"tăng nhiệt độ 24"* chỉ có thể là *đặt 24*. Ngưỡng `min ≥ 10` tách nhiệt độ khỏi gió/âm lượng (dải từ
     * 0, ở đó *"tăng 2"* nhập nhằng bước↔đích nên giữ tương đối).
     */
    private fun isSetpoint(def: ControlDef, num: Int): Boolean =
        def.min >= ABS_SETPOINT_MIN && num in def.min..def.max

    /** Số đi kèm "tăng/giảm" phải là số bước thật, không phải sentinel *"hết cỡ"*. */
    private fun plainStep(n: Int): Boolean = n != VoiceLexicon.MAX && n != VoiceLexicon.MIN

    /** Ngưỡng `min` để coi số-trong-dải là SETPOINT (nhiệt độ 17..33), không phải số-bước — xem [isSetpoint]. */
    private const val ABS_SETPOINT_MIN = 10

    /** Khớp một nhãn lựa chọn của nút SELECT (VI hoặc EN), hoặc số thứ tự nói thẳng. */
    private fun selectIndex(def: ControlDef, after: List<Token>): Int? {
        val lists = listOf(def.args, def.argsEn).filter { it.isNotEmpty() }
        lists.forEach { args ->
            args.forEachIndexed { idx, label ->
                val words = VoiceLexicon.tokenize(label).map { it.norm }
                if (words.isNotEmpty() && after.indices.any { VoiceLexicon.phraseAt(after, it, words) }) return idx
            }
        }
        val n = firstNumber(after) ?: return null
        return n.takeIf { it in def.args.indices }
    }

    private fun firstNumber(after: List<Token>): Int? {
        after.indices.forEach { i -> VoiceLexicon.readNumber(after, i)?.let { return it.value } }
        return null
    }

    /** Câu có chứa một con số không (dùng ở pre-rule verbless "điều hòa X độ" của VoiceIntentParser). */
    fun hasNumber(tokens: List<Token>): Boolean = firstNumber(tokens) != null

    /**
     * SELECT có mức **"Tắt"/"Off" ở index 0** (ghế mát/sưởi) chấp nhận **bật/tắt trần** khi không nêu mức:
     * *"bật ghế mát"* → mức 1 (bật mặc định) · *"tắt ghế mát"* → 0. Nút SELECT khác (màu viền, EV/HEV…) không có
     * "Tắt" ở đầu ⇒ trả `null`, giữ nguyên (chỉ chọn theo nhãn/số). *"ghế mát mức 1/mức 2"* đã do [selectIndex] lo.
     */
    private fun offOnSelect(def: ControlDef, verb: VoiceVerb): VoiceIntent? {
        val offFirst = def.args.firstOrNull()?.let { VoiceLexicon.tokenize(it).map { t -> t.norm } } == listOf("tat")
        if (!offFirst) return null
        return when (verb) {
            VoiceVerb.ON, VoiceVerb.OPEN -> VoiceIntent.Control(def.id, 1)
            VoiceVerb.OFF, VoiceVerb.CLOSE -> VoiceIntent.Control(def.id, 0)
            else -> null
        }
    }

    /**
     * Số nói **liền ngay trước** chữ *"độ"* (*"hai mươi lăm **độ**"*) — dấu hiệu duy nhất của một setpoint nhiệt.
     *
     * ## Vì sao phải LIỀN KỀ, không phải "câu có số và có chữ độ ở đâu đó" ([SOÁT 2026-09-19])
     * Bản đầu của fix (c) hỏi `num != null && after.any { it.norm == "do" }`. Nhưng `do` (bỏ dấu) cũng là **âm
     * tiết thứ hai** của những cụm chẳng liên quan gì tới nhiệt độ — *"chế độ"*, *"mức độ"* — nên [ĐO off-car]
     * *"bật điều hòa **chế độ** hai"* ra `Control(temp, 17)`: người lái nói một chế độ, xe đặt nhiệt xuống **mức
     * thấp nhất** (số 2 bị `clamp` vào dải 17..33), và đó là một lệnh GHI không ai xin. Đúng họ lỗi mà chính fix
     * (c) sinh ra để đóng.
     *
     * Phép liền kề khớp đúng cách người ta nói một nhiệt độ (*"<số> độ"*) và bỏ đúng cách người ta nói một chế độ
     * (*"chế độ <số>"* — chữ `do` đứng TRƯỚC số). Không cần danh sách từ cấm nào.
     */
    private fun degreesSetpoint(after: List<Token>): Int? {
        after.indices.forEach { i ->
            val n = VoiceLexicon.readNumber(after, i) ?: return@forEach
            if (after.getOrNull(i + n.consumed)?.norm == DEGREE_WORD) return n.value
        }
        return null
    }

    /** Chữ *"độ"* đã bỏ dấu — đơn vị đi kèm một setpoint nhiệt (xem [degreesSetpoint]). */
    private const val DEGREE_WORD = "do"
}
