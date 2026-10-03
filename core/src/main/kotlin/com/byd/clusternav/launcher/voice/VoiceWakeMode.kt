package com.byd.clusternav.launcher.voice

import com.byd.clusternav.voicekey.VoiceKeyBinding

/**
 * ═══ FIX286 · VK1 — MÔ HÌNH NGHE NẰM Ở ĐÂU: một luật cho mọi lối vào (thuần, `:core`) ══════════════════════════════
 *
 * Spec `docs/specs/kachi-286-field-fixes.html` R-VK. [ĐO mã 02/10] Trước 2.86 hai lối vào chọn tiến trình theo hai luật
 * khác nhau: nút mic màn chính hỏi [VoiceEntryRoute] (wake TẮT ⇒ tiến trình chính, mô hình nạp sẵn ở đó), còn phím
 * vô-lăng gán *"Kachi nghe"* **luôn** đi `:wake` (`AssistantLauncher.launchKachiVoice` → `VoiceWakeService.listenNow`)
 * — nơi wake TẮT thì không có mô hình, và sau mỗi phiên lại nhả (BG-20). ⇒ mỗi lần bấm phím là một lần nạp nguội
 * ([SUY log xe cũ] 9–34 s). Anh em báo 03/10: *"phím vô-lăng kẹt Getting ready, bấm mic trên màn thì được"*.
 *
 * Luật mới (phương án S2 B′ — phiên điều phối chọn): **mô hình nằm ở `:wake` khi `wakeEnabled ∨ keyHold`**, với
 * `keyHold` = có phím đang gán [kachiTarget] *và* công tắc "Nhận nút vật lý" đang bật (tắt công tắc ⇒ phím không bắn,
 * giữ mô hình cho nó là RAM thừa). Một bản mô hình cho cả máy, đúng như 2.66 muốn: khi mô hình ở `:wake` thì tiến
 * trình chính không nạp sẵn ([VoicePreloadPolicy.shouldPreloadInMain]) và nút mic màn cũng đi `:wake`
 * ([VoiceEntryRoute.decide]); khi không thì y như 2.85.
 *
 * Phần native (onnxruntime) của đường phím vì thế vẫn nằm NGOÀI tiến trình chứa dịch vụ phím (`NavAccessibilityService`)
 * — lý do phương án A (mở phiên ngay ở tiến trình chính) bị loại: SIGSEGV/LMK ở đó giết luôn phím (họ lỗi 29/09).
 */
enum class VoiceWakeMode {
    /** Không service `:wake`; mô hình nạp sẵn ở tiến trình CHÍNH (hành vi 2.85 khi wake TẮT). */
    OFF,

    /** MỚI 2.86: FGS `:wake` giữ mô hình nạp sẵn cho phím vô-lăng — KHÔNG mở micro, KHÔNG bộ nghe câu gọi. */
    HOLD,

    /** "Hey Kachi" BẬT — vòng đời có sẵn từ 2.62, không đổi (§6): FGS + bộ nghe câu gọi + mô hình ở `:wake`. */
    WAKE,
    ;

    /** Mô hình có nằm ở `:wake` không (HOLD hoặc WAKE). */
    val modelInWake: Boolean get() = this != OFF

    companion object {
        /** Wake bật thắng phím (bộ nghe câu gọi cần chính mô hình đó); không wake mà có phím ⇒ HOLD. */
        fun of(wakeEnabled: Boolean, keyHold: Boolean): VoiceWakeMode = when {
            wakeEnabled -> WAKE
            keyHold -> HOLD
            else -> OFF
        }

        /**
         * Có phím nào thật sự sẽ mở phiên nghe Kachi không. [voiceKeyEnabled] = công tắc "Nhận nút vật lý"
         * (`NavAccessibilityService.onKeyEvent` thoát sớm khi tắt — phím không bao giờ bắn); [kachiTarget] =
         * `Prefs.VK_TARGET_KACHI_VOICE` (truyền vào để `:core` không phải biết hằng của tầng lưu).
         */
        fun keyHold(voiceKeyEnabled: Boolean, bindings: List<VoiceKeyBinding>, kachiTarget: String): Boolean =
            voiceKeyEnabled && bindings.any { it.targetSpec == kachiTarget }
    }
}
