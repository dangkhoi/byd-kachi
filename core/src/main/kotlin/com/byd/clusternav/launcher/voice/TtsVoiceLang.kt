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
 *
 * ## Câu trả lời "không dùng được" KHÔNG phải câu trả lời cuối (soát vòng 2 [P3])
 * `isLanguageAvailable` trả `LANG_NOT_SUPPORTED` cả khi engine CHƯA NỐI / đang nối lại [ĐO nguồn android-10.0.0_r47
 * `TextToSpeech.java:1786-1808` (errorResult = LANG_NOT_SUPPORTED) + `:2300-2321` (`mService == null` / chưa established /
 * RemoteException ⇒ errorResult, kèm nối lại không đồng bộ)]. Bản trước nhớ số đó như đáp án cuối cho Locale ⇒ đổi tiếng
 * đúng lúc engine đang nối lại là đường Android chết tới lần đổi tiếng sau / tới khi tiến trình `:wake` chết (VI lùi Piper,
 * EN im lặng). Nay: số dưới [VoiceSpeakerSelector.LANG_AVAILABLE] (hoặc hỏi/đặt hỏng) được HỎI LẠI sau [RECHECK_MS]; số
 * dùng được là đáp án cuối (một phép so mỗi câu như cũ). Giá: tối đa một lời gọi binder mỗi [RECHECK_MS] khi tiếng thật sự
 * không có giọng.
 *
 * ## Khoá
 * [sync] giữ monitor của lớp này qua lời gọi engine (cần: hỏi + đặt + ghi phải là MỘT bước, không thì hai luồng đặt hai tiếng
 * lệch nhau). Bên gọi KHÔNG được gọi [sync] trong lúc giữ `TextToSpeech.mStartLock` khi luồng khác có thể vào [sync] — đó là
 * vòng khoá (`AndroidTtsSpeaker.configure` mở cổng SAU lượt đầu).
 */
class TtsVoiceLang(
    /** Đồng hồ ĐƠN ĐIỆU (ms) cho nhịp hỏi lại — tiêm được trong test. Đứng TRƯỚC [want] để `TtsVoiceLang { … }` vẫn là [want]. */
    private val nowMs: () -> Long = { System.nanoTime() / 1_000_000L },
    private val want: () -> Locale,
) {

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
     * @property changed kết quả vừa ĐỔI (tiếng đổi, lượt đầu, hoặc lượt hỏi lại ra số khác) — chỗ gọi ghi log đúng lúc ấy;
     *   lượt hỏi lại ra đúng số cũ ⇒ `false` (không lặp một dòng log mỗi [RECHECK_MS]).
     */
    data class Result(val locale: Locale?, val status: Int?, val changed: Boolean) {
        /** Đạt ngưỡng phát ra tiếng thật — CÙNG luật với [VoiceSpeakerSelector.androidUsable]. */
        val usable: Boolean get() = (status ?: Int.MIN_VALUE) >= VoiceSpeakerSelector.LANG_AVAILABLE
    }

    private var configured: Locale? = null
    private val status = AtomicReference<Int?>(null)

    /** Mốc ([nowMs]) được hỏi lại engine cho [configured]; `null` = số đang nhớ là đáp án cuối (dùng được). */
    private var recheckAt: Long? = null

    /** Số thô của lượt hỏi gần nhất (`null` = chưa hỏi lần nào / hỏng) — cầu kiểm thử + bộ chọn đọc. */
    fun status(): Int? = status.get()

    /**
     * Đưa [port] về đúng tiếng muốn. `@Synchronized`: `onInit` (luồng chính) và lượt đọc (luồng phiên) có thể cùng gọi.
     * Tiếng chưa có giọng ⇒ KHÔNG `setLanguage` (giữ nguyên engine), chỉ ghi số để bộ chọn chuyển sang Piper/im lặng — và
     * hẹn hỏi lại sau [RECHECK_MS] (số đó có thể chỉ là engine đang nối lại). Lambda [want] hỏng ⇒ giữ nguyên lượt đặt trước
     * (không đoán một tiếng thứ ba).
     */
    @Synchronized
    fun sync(port: Port): Result {
        val now = runCatching { want() }.getOrNull() ?: return Result(configured, status.get(), changed = false)
        val at = nowMs()
        if (now == configured && recheckAt.let { it == null || at < it }) return Result(now, status.get(), changed = false)
        var st = runCatching { port.isLanguageAvailable(now) }.getOrNull()
        if (st != null && st >= VoiceSpeakerSelector.LANG_AVAILABLE && runCatching { port.setLanguage(now) }.isFailure) st = null
        val changed = now != configured || st != status.get()
        configured = now
        status.set(st)
        recheckAt = if (st != null && st >= VoiceSpeakerSelector.LANG_AVAILABLE) null else at + RECHECK_MS
        return Result(now, st, changed)
    }

    companion object {
        /**
         * Nhịp hỏi lại một câu trả lời "không dùng được". Ngắn hơn một lượt hội thoại (câu hỏi → câu trả lời) để lượt kế
         * đã thấy engine nối lại; đủ dài để tiếng thật sự không có giọng chỉ tốn một lời gọi binder mỗi lượt.
         */
        const val RECHECK_MS = 5_000L
    }
}
