package com.byd.clusternav.launcher.voice

import com.byd.clusternav.launcher.TelemetryRegistry
import com.byd.clusternav.launcher.TelemetrySpec
import com.byd.clusternav.launcher.TyreIds

/**
 * ═══ 2.88 · DATUM NÓI ĐƯỢC BẰNG GIỌNG — MỘT danh sách cho cả năm bộ từ vựng giọng nói ═══════════════════════════
 *
 * [VoiceGrammar] (`STATIC_HEAD`) · [VoicePhrases] (ngữ pháp Sherpa) · [SherpaPhraseHotwords] · [VoicePhoneticMatch]
 * · [VoiceCommandCatalog] đọc [SPOKEN], KHÔNG đọc thẳng [TelemetryRegistry.ALL]. Thêm một datum vào bộ đăng ký vẫn
 * tự nhiên nói được (luật V1 "từ vựng sinh từ bộ đăng ký" không đổi); chỉ mã nằm trong [NOT_SPOKEN] bị giữ ngoài.
 *
 * ## Vì sao 13 mã trạng thái THÔ của lốp không nói được (soát 2.88 regress-3)
 * Chúng là ĐẦU VÀO cho phép phán màu ([TyreIds.HIDDEN_WHY]), không phải một thứ để hỏi:
 *  • nhãn của chúng thêm 13 cụm + 37 mục và các từ *rò · hệ · màu · sát · thống · giám · trạng · thái* vào ngữ pháp
 *    ASR ⇒ đổi cách nhận dạng MỌI câu, mà `scripts/emulator/voice-e2e.sh` chưa đo (luật owner: đổi giọng nói là chạy
 *    lại harness);
 *  • và câu trả lời SAI: bánh mà cụm nói *trắng* thì R7 cố ý không đọc mã TPMS (`CarDataAdapter.readTyres`), mà mã ấy
 *    đã nằm trong nhu cầu qua `CarDataDemand.COMPANION` nên câu hỏi đọc ảnh chụp ⇒ *"chưa đọc được"* trong khi xe CÓ
 *    giá trị (một "không đọc được" giả — CLAUDE.md §2).
 * Câu hỏi về lốp vẫn trả lời bằng `tyre_p_*` (áp suất) như trước 2.88. Muốn mở lại: chạy `voice-e2e.sh` trước, và cho
 * `runRead` ép đọc tươi mã thô dù nó đang trong nhu cầu.
 */
object VoiceTelemetry {

    /** Mã KHÔNG đưa vào bất kỳ bộ từ vựng giọng nói nào — xem KDoc lớp. */
    val NOT_SPOKEN: Set<String> = TyreIds.RAW_STATES.toSet()

    /** [TelemetryRegistry.ALL] trừ [NOT_SPOKEN], giữ nguyên thứ tự. */
    val SPOKEN: List<TelemetrySpec> by lazy { TelemetryRegistry.ALL.filterNot { it.id in NOT_SPOKEN } }
}
