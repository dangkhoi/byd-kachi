package com.byd.clusternav.launcher.voice

/**
 * ═══ V3 · R1 — THỨ TỰ NGUỒN MICRO, khai một chỗ và **kiểm được off-car** ═════════════════════════════════════
 *
 * Spec `docs/specs/kachi-voice-fast-natural.html` **R1**. Thuần Kotlin (`:core`): ba con số dưới đây là hằng của
 * `android.media.MediaRecorder.AudioSource`, nhưng chúng là **số**, nên luật *"thử cái nào trước"* kiểm được mà
 * không cần Android — và đó chính là phần đã sai trên xe.
 *
 * ## [ĐO xe 2026-09-16] vì sao đảo thứ tự (`docs/diagnostics/oncar-trace-2026-09-16.md` §2)
 * Bản 1.64 mở micro bằng [VOICE_RECOGNITION] trước. Ba phiên liên tiếp lúc 09:14–09:15 cho **đỉnh 136–255/32767 ·
 * rms 30–50** — gần câm, sherpa chỉ ra *"ừm"*; owner nói *"nói bằng mức nói Kiki, Kiki nhận rất tốt"*. `dumpsys
 * audio` cho thấy Kiki ghi bằng **`src:MIC`**, Kachi bằng `VOICE_RECOGNITION`; không có `setRecordSilenced` nào,
 * và chỉ có một đầu vào (`AUDIO_DEVICE_IN_BUILTIN_MIC`). Bản thử `1.65-mic` đảo sang MIC trước ⇒ **đỉnh 4429 ·
 * rms 237**, sherpa nghe đúng *"mở cửa sổ"*.
 *
 * ⇒ Lập luận cũ (*"VOICE_RECOGNITION bỏ qua AGC/khử ồn nên sạch hơn cho mô hình"*) đúng về **cơ chế** nhưng bị số
 * đo trên chính chiếc xe này bác về **quy kết** (CLAUDE.md §2): ROM này gắn nguồn 6 vào một đường khuếch đại
 * khác, và kết quả là gần câm.
 *
 * ## Vì sao [VOICE_COMMUNICATION] chen vào GIỮA, không bỏ hẳn
 * Nó là nguồn duy nhất trong ba cái có **AEC + khử ồn** của ROM. Owner **D5**: *"phải nhận được khi đang mở
 * nhạc"*. Xe đang chạy 60–80 km/h + nhạc là ca mà MIC thô có thể thua — nhưng **[CHƯA BIẾT]**, chưa ai đo. Nên nó
 * đứng thứ hai (đường lùi tự động) và có [PREF_AUTO] để ép thử từng nguồn ở lượt xe sau mà không phải build lại.
 */
object VoiceMicSource {

    /** `MediaRecorder.AudioSource.MIC` — micro thô, đúng nguồn Kiki dùng ([ĐO] xe 2026-09-16). */
    const val MIC = 1

    /** `MediaRecorder.AudioSource.VOICE_RECOGNITION` — nguồn 1.49–1.65 dùng; gần câm trên ROM này. */
    const val VOICE_RECOGNITION = 6

    /** `MediaRecorder.AudioSource.VOICE_COMMUNICATION` — có AEC/NS của ROM; chưa đo trên xe. */
    const val VOICE_COMMUNICATION = 7

    /** Giá trị pref `voice_mic_source` nghĩa *"để Kachi tự chọn"* (mặc định). */
    const val PREF_AUTO = 0

    /** Thứ tự mặc định: MIC → VOICE_COMMUNICATION → VOICE_RECOGNITION. Xem KDoc lớp. */
    val DEFAULT_ORDER: List<Int> = listOf(MIC, VOICE_COMMUNICATION, VOICE_RECOGNITION)

    /** Mọi giá trị chọn được trong Cài đặt (0 = tự chọn). */
    val CHOICES: List<Int> = listOf(PREF_AUTO) + DEFAULT_ORDER

    /**
     * Thứ tự thử cho một giá trị pref.
     *
     * Nguồn được ép vẫn có **đường lùi** phía sau: một ROM có thể dựng `AudioRecord` ở trạng thái
     * `STATE_UNINITIALIZED` cho đúng nguồn ấy mà **không ném** (đã thấy, xem `VoiceCapture.openRecord`) — ép cứng
     * một nguồn rồi bỏ cuộc nghĩa là một lần chọn sai trong Cài đặt làm câm hẳn tính năng, mà người dùng không có
     * cách nào biết vì sao. Ép ⇒ *"thử cái này TRƯỚC"*, không phải *"chỉ dùng cái này"*.
     */
    fun order(pref: Int): List<Int> {
        if (pref == PREF_AUTO || pref !in DEFAULT_ORDER) return DEFAULT_ORDER
        return listOf(pref) + DEFAULT_ORDER.filter { it != pref }
    }

    /**
     * TÊN HẰNG Android của một nguồn, cho Cài đặt / nhật ký.
     *
     * ⚠ Cố ý KHÔNG đặt tên `label`: `LauncherI18nContractTest` quét mọi lần đọc `.label` ở `:app` để bắt chỗ
     * dùng nhãn GỐC thay cho `displayLabel`. Chuỗi này không phải một nhãn để dịch — nó là tên hằng của
     * Android, và nó phải grep được y hệt giữa hai máy đo. Không dịch: đây là tên hằng của Android, grep được giữa hai máy. */
    fun sourceName(source: Int): String = when (source) {
        PREF_AUTO -> "auto"
        MIC -> "MIC (1)"
        VOICE_RECOGNITION -> "VOICE_RECOGNITION (6)"
        VOICE_COMMUNICATION -> "VOICE_COMMUNICATION (7)"
        else -> "source $source"
    }
}
