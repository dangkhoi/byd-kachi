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
    fun control(id: String, verb: VoiceVerb, after: List<Token>, original: String): VoiceIntent {
        val def = ControlRegistry.byId(id) ?: return VoiceIntent.Unknown(VoiceUnknownReason.NO_OBJECT, original)
        val num = firstNumber(after)
        return when (def.kind) {
            ControlKind.TOGGLE, ControlKind.COVER -> when (verb) {
                VoiceVerb.ON, VoiceVerb.OPEN -> VoiceIntent.Control(id, 1)
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
                ?: VoiceIntent.Unknown(VoiceUnknownReason.MISMATCH, original)
            ControlKind.STEP -> step(def, verb, num, original)
        }
    }

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
}
