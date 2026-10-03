package com.byd.clusternav.launcher.voice

import java.util.Locale
import java.util.concurrent.atomic.AtomicReference

/**
 * ═══ GIỌNG CỦA MÁY ĐỌC HỆ THỐNG ĐI THEO TIẾNG GIỌNG NÓI CỦA **TỪNG CÂU** (spec `kachi-i18n-zh-th-ms.html` R6) ══════
 *
 * [ĐO mã · soát 2.87 voice P2] `AndroidTtsSpeaker` (`:app`) từng hỏi `isLanguageAvailable` + `setLanguage` **một lần**
 * lúc `onInit`. Ở tiến trình chính điều đó đủ (đổi ngôn ngữ ⇒ `recreate()` ⇒ phiên mới ⇒ máy đọc mới), nhưng phiên
 * `:wake` (WAKE/HOLD) được DÙNG LẠI suốt đời tiến trình (`VoiceSessionOwner` trả lại phiên IDLE; chỉ OFF/`onDestroy`
 * mới nhả). Người dùng đổi English → Tiếng Việt thì câu trả lời đã sang tiếng Việt (bộ dựng câu đọc ảnh chụp ngữ pháp
 * mỗi lượt) mà máy đọc vẫn giọng Anh, và `VoiceSpeakerRouter.probe` vẫn đọc số `isLanguageAvailable` của tiếng Anh ⇒
 * chọn Piper/Android theo tiếng cũ.
 *
 * Lớp này giữ đúng một câu hỏi: *"máy đọc đang đặt cho Locale nào, và nền tảng trả lời gì cho Locale ấy"*. [sync] được
 * gọi ở MỌI cửa trước khi đọc/chọn: Locale muốn ([want] — lambda của phiên, map qua `LangHost.voiceLocale` ở `:app`)
 * khác Locale đã đặt ⇒ hỏi lại + đặt lại; trùng ⇒ một phép so, không gọi gì xuống engine. Thuần (không `android.*`) ⇒
 * kiểm off-device với một [Port] giả (`TtsVoiceLangTest`). Cấu hình CHUNG (thuộc tính âm thanh, người nghe sự kiện)
 * không ở đây — nó không phụ thuộc tiếng.
 */
class TtsVoiceLang(private val want: () -> Locale) {

    /** Hai thao tác của `TextToSpeech` phụ thuộc tiếng — tách ra để thay bằng bản giả trong test. */
    interface Port {
        fun isLanguageAvailable(locale: Locale): Int
        fun setLanguage(locale: Locale)
    }

    /**
     * Kết quả một lượt [sync].
     *
     * @property locale Locale đang đặt (`null` = chưa đặt lần nào — lambda hỏng ngay lượt đầu).
     * @property status số thô `isLanguageAvailable` cho [locale]; `null` = chưa hỏi / hỏi-đặt hỏng (engine chết giữa chừng).
     * @property changed lượt này VỪA hỏi lại engine (tiếng đổi, hoặc lượt đầu) — chỗ gọi ghi log đúng lúc ấy.
     */
    data class Result(val locale: Locale?, val status: Int?, val changed: Boolean) {
        /** Đạt ngưỡng phát ra tiếng thật — CÙNG luật với [VoiceSpeakerSelector.androidUsable]. */
        val usable: Boolean get() = (status ?: Int.MIN_VALUE) >= VoiceSpeakerSelector.LANG_AVAILABLE
    }

    private var configured: Locale? = null
    private val status = AtomicReference<Int?>(null)

    /** Số thô của lượt hỏi gần nhất (`null` = chưa hỏi lần nào / hỏng) — cầu kiểm thử + bộ chọn đọc. */
    fun status(): Int? = status.get()

    /**
     * Đưa [port] về đúng tiếng muốn. `@Synchronized`: `onInit` (luồng chính) và lượt đọc (luồng phiên) có thể cùng gọi.
     * Tiếng chưa có giọng ⇒ KHÔNG `setLanguage` (giữ nguyên engine), chỉ ghi số để bộ chọn chuyển sang Piper/im lặng.
     * Lambda [want] hỏng ⇒ giữ nguyên lượt đặt trước (không đoán một tiếng thứ ba).
     */
    @Synchronized
    fun sync(port: Port): Result {
        val now = runCatching { want() }.getOrNull() ?: return Result(configured, status.get(), changed = false)
        if (now == configured) return Result(now, status.get(), changed = false)
        var st = runCatching { port.isLanguageAvailable(now) }.getOrNull()
        if (st != null && st >= VoiceSpeakerSelector.LANG_AVAILABLE && runCatching { port.setLanguage(now) }.isFailure) st = null
        configured = now
        status.set(st)
        return Result(now, st, changed = true)
    }
}
