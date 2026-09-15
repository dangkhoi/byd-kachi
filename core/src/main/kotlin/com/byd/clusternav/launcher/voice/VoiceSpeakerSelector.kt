package com.byd.clusternav.launcher.voice

/**
 * ═══ V1 pha NÓI · CHỌN MÁY ĐỌC THEO PHÉP **ĐO**, KHÔNG THEO TÊN MÁY ══════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-voice-feedback.html` **R2**. Tệp này **thuần** (không `import android.*`) đúng để bài
 * kiểm chạy được off-car trên JVM — luật chọn là thứ dễ sai và dễ rữa nhất, nên nó phải là thứ kiểm được.
 *
 * ## CLAUDE.md §7 — đo, đừng viết cứng tên đời xe
 * Cấm mọi nhánh kiểu *"DiLink 5 thì dùng sherpa"*. Đầu vào duy nhất là [VoiceSpeakerProbe]: máy đọc của hệ thống
 * **trả lời thế nào** về `vi-VN`, và gói giọng offline **có trên đĩa chưa**. Xe nào đo ra gì thì đi nhánh đó.
 */
object VoiceSpeakerSelector {

    /**
     * Ngưỡng của `TextToSpeech.isLanguageAvailable` — chép thành **hằng có tên** ở tầng thuần để bài kiểm khoá
     * được đúng cái luật này, thay vì để một con số 0 trần nằm giữa mã Android không ai kiểm.
     *
     * Bảng của nền tảng (`android.speech.tts.TextToSpeech`):
     * `LANG_NOT_SUPPORTED = -2` · `LANG_MISSING_DATA = -1` · `LANG_AVAILABLE = 0` ·
     * `LANG_COUNTRY_AVAILABLE = 1` · `LANG_COUNTRY_VAR_AVAILABLE = 2`.
     *
     * ⚠ `LANG_MISSING_DATA` (−1) là ca **thường gặp nhất** trên máy chưa tải gói giọng — [ĐO] 2026-09-15 máy ảo
     * `emulator-5554`: `com.google.android.tts` có mặt nhưng `app_voices/` **rỗng**. Coi −1 là "có" thì
     * `speak()` trả `SUCCESS` mà **không phát ra tiếng nào**: một công tắc bật, một câu không ai nghe, không lỗi.
     */
    const val LANG_AVAILABLE = 0

    /**
     * Kết quả đo tại máy, không phải lời khai.
     *
     * @param androidLangStatus số nguyên `isLanguageAvailable(…)` trả về cho **ngôn ngữ đang dùng** (`:app` hỏi
     *   qua `LangHost.locale()` — một chỗ map ngôn ngữ→`Locale` duy nhất của dự án); `null` = chưa dựng được
     *   dịch vụ đọc nào (không có engine, hoặc `onInit` báo `ERROR`).
     * @param sherpaVoiceReady thư mục gói giọng offline đã đủ tệp trên đĩa chưa (`:app` tự kiểm, không tin pref).
     * @param preferOffline người dùng đã chọn ưu tiên gói offline (pha 2 — công tắc trong Cài đặt). Mặc định
     *   `false`: khi cả hai cùng dùng được thì máy đọc của hệ thống thắng, vì nó **0 MB** đĩa và **0 MB** RAM,
     *   trong khi gói offline ngốn 61 MB tệp `.onnx` phải nạp vào bộ nhớ của một launcher sống suốt chuyến.
     */
    data class VoiceSpeakerProbe(
        val androidLangStatus: Int?,
        val sherpaVoiceReady: Boolean,
        val preferOffline: Boolean = false,
    )

    /** Máy đọc của hệ thống có giọng Việt **thật sự phát ra tiếng** không — xem KDoc [LANG_AVAILABLE]. */
    fun androidUsable(p: VoiceSpeakerProbe): Boolean = (p.androidLangStatus ?: Int.MIN_VALUE) >= LANG_AVAILABLE

    /**
     * Chọn đường ra tiếng.
     *
     * Thứ tự: (a) máy đọc hệ thống nếu có `vi-VN` thật · (b) gói sherpa offline nếu đã lắp · (c) **không đọc**.
     * Ca (c) **không** phải lỗi: tấm chữ + âm báo vẫn chạy y như 1.63 (R2c — degrade, không ném).
     */
    fun choose(p: VoiceSpeakerProbe): VoiceSpeakerKind {
        if (p.preferOffline && p.sherpaVoiceReady) return VoiceSpeakerKind.SHERPA_OFFLINE
        if (androidUsable(p)) return VoiceSpeakerKind.ANDROID_TTS
        if (p.sherpaVoiceReady) return VoiceSpeakerKind.SHERPA_OFFLINE
        return VoiceSpeakerKind.NONE
    }
}

/** Ba đường ra tiếng. Tên này đi vào cầu kiểm thử (`TestBridgeState.tts`) nên **không đổi tuỳ tiện**. */
enum class VoiceSpeakerKind {
    /** `android.speech.tts.TextToSpeech` — giọng của hệ thống, cần gói `vi-VN` đã tải. */
    ANDROID_TTS,

    /** `com.k2fsa.sherpa.onnx.OfflineTts` — gói VITS/Piper nằm trong `filesDir`, chạy hoàn toàn tại máy. */
    SHERPA_OFFLINE,

    /** Không có giọng Việt nào ⇒ chỉ chữ + âm báo, đúng như 1.63. */
    NONE,
}
