package com.byd.clusternav.launcher.voice

/**
 * ═══ V3 pha NGHE · NGẮT CÂU KHI NGƯỜI TA NGỪNG NÓI — **năng lượng, không mô hình** ════════════════════════════
 *
 * Spec `docs/specs/kachi-voice-fast-natural.html` **R2**. Thuần Kotlin (`:core`) ⇒ kiểm off-car bằng một chuỗi
 * rms giả; `:app` chỉ đưa vào từng con số đo được của [VoiceCapture].
 *
 * ## Bệnh nó chữa — [ĐO xe 2026-09-16] `docs/diagnostics/oncar-trace-2026-09-16.md` §2
 * Lượt 09:10: micro mở lúc `14.5`, sherpa ra chữ lúc `28.1` — **nghe hết trần 8,4 s** dù câu nói xong từ lâu.
 * Mọi lượt trên xe đều như vậy, vì bộ nhận dạng offline (`OfflineRecognizer`) **không chốt câu giữa dòng**:
 * `VoiceRecognizer.accept` luôn trả `false` nên vòng nghe chỉ có đúng ba đường thoát — hết trần, người dùng huỷ,
 * micro chết. Tức 8 giây là **giá cố định** của mọi câu, kể cả câu hai từ.
 *
 * ## Vì sao KHÔNG dùng VAD của sherpa
 * sherpa có `SileroVad`, nhưng nó là một **mô hình ONNX thứ hai** phải tải + nạp + chạy trên cùng cái CPU đang
 * chật (máy còn 56–94 MB trống lúc đo). Thứ cần ở đây không phải "đoạn này có phải giọng người không" mà chỉ là
 * *"người ta còn đang nói không"* — một câu hỏi mà mức năng lượng trả lời đủ tốt, bằng số học nguyên, không cấp
 * phát gì.
 *
 * ## Ngưỡng: `max(3 × nền, 120)` — hai nửa, mỗi nửa chữa một ca
 *  • **3 × nền** bám theo chính cabin lúc ấy (nhạc, điều hoà, tốc độ 80 km/h). Ngưỡng tuyệt đối thì hoặc câm
 *    trong xe ồn, hoặc cắt câu trong xe im.
 *  • **sàn 120** giữ cho ca *"nền gần 0"*. [ĐO xe] lượt 09:14 có **rms 30–50** cả phiên (micro gần câm): nền ≈ 40
 *    ⇒ `3 × nền` = 120 — nếu không có sàn thì ở một lượt nền = 2 mọi tiếng lạo xạo rms 8 đã thành "có tiếng".
 *
 * ## Trần vẫn còn, và đó là điều cố ý
 * Bộ này chỉ **rút ngắn** một lượt nghe; nó không thay trần cứng (`VoiceSession.MAX_LISTEN_MS`). Một cabin ồn
 * liên tục làm nó không bao giờ thấy "im" ⇒ đúng ca mà trần sinh ra để đỡ (CLAUDE.md §3: không gate một đường
 * phục hồi bằng dữ liệu mà chỉ chính đường đó mới làm mới được).
 */
class VoiceEndpointer(
    /** Cửa sổ đo nền ở đầu phiên. 300 ms — đủ vài khối 200 ms, ngắn hơn quãng người ta kịp bắt đầu nói. */
    private val floorWindowMs: Int = FLOOR_WINDOW_MS,
    /** Phải có ngần này tiếng (cộng dồn) thì mới được phép chốt câu — chặn "chốt vì một tiếng cạch". */
    private val minSpeechMs: Int = MIN_SPEECH_MS,
    /** Im liên tục ngần này sau khi đã có tiếng ⇒ chốt. */
    private val hangoverMs: Int = HANGOVER_MS,
) {

    /** Bộ này đang ở đâu trong một lượt nghe. */
    enum class Phase {
        /** Đang đo nền, chưa xét gì. */
        FLOOR,

        /** Đã có ngưỡng, đang chờ đủ [minSpeechMs] tiếng. */
        WAITING,

        /** Đã đủ tiếng — từ đây một quãng im đủ dài là chốt. */
        SPEAKING,

        /** Chốt. Chỗ gọi dừng vòng nghe. */
        ENDED,
    }

    var phase: Phase = Phase.FLOOR
        private set

    /** Mức nền đã chốt (trung vị của cửa sổ đầu). `-1` khi chưa đo xong. */
    var floor: Int = -1
        private set

    /** Tổng thời gian đã nghe (ms), cộng theo chính [accept]. */
    var elapsedMs: Int = 0
        private set

    /** Mốc "tiếng bắt đầu" (ms kể từ lúc mở micro), `-1` nếu chưa có tiếng nào. */
    var speechStartMs: Int = -1
        private set

    /** Mốc "tiếng dứt" (ms) = lần cuối còn trên ngưỡng, `-1` nếu chưa có. */
    var speechEndMs: Int = -1
        private set

    private var voicedMs: Int = 0
    private var silenceMs: Int = 0
    private val floorSamples = ArrayList<Int>(8)

    /** Ngưỡng "có tiếng" đang dùng; `-1` khi chưa đo xong nền. */
    fun threshold(): Int = if (floor < 0) -1 else maxOf(floor * FLOOR_FACTOR, ABS_FLOOR)

    /**
     * Nhận một khối đã đo: [rms] của khối, [chunkMs] là độ dài khối ấy. Trả [Phase] **sau** khi nhận.
     *
     * Nhận `chunkMs` chứ không giả định 200 ms: kích thước khối do bộ đệm của ROM quyết (`getMinBufferSize`), và
     * một hằng viết cứng ở đây sẽ làm mọi mốc thời gian sai trên đúng những ROM đọc khối lệch chuẩn.
     */
    fun accept(rms: Int, chunkMs: Int): Phase {
        if (phase == Phase.ENDED) return phase
        elapsedMs += chunkMs
        if (phase == Phase.FLOOR) {
            floorSamples.add(rms)
            if (elapsedMs < floorWindowMs) return phase
            floor = median(floorSamples)
            phase = Phase.WAITING
            return phase
        }
        val loud = rms > threshold()
        if (loud) {
            if (speechStartMs < 0) speechStartMs = elapsedMs - chunkMs
            speechEndMs = elapsedMs
            voicedMs += chunkMs
            silenceMs = 0
            if (voicedMs >= minSpeechMs) phase = Phase.SPEAKING
            return phase
        }
        // Im: chỉ đếm khi ĐÃ đủ tiếng. Đếm từ trước đó thì một phiên mà người lái bấm rồi mới hít hơi sẽ tự chốt
        // trước khi họ mở miệng — đúng ca "cắt lời" mà tính năng này phải tránh hơn cả việc chậm.
        if (phase == Phase.SPEAKING) {
            silenceMs += chunkMs
            if (silenceMs >= hangoverMs) phase = Phase.ENDED
        }
        return phase
    }

    /** Một dòng nhật ký cho `KachiVoiceTiming` — mốc giờ của chính lượt này, không phải một lời kể. */
    fun summary(): String =
        "nen=$floor nguong=${threshold()} tieng_bat_dau=${speechStartMs}ms tieng_dut=${speechEndMs}ms " +
            "chot=${elapsedMs}ms pha=$phase"

    private fun median(v: List<Int>): Int {
        if (v.isEmpty()) return 0
        val s = v.sorted()
        return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2
    }

    companion object {
        /** 300 ms — xem KDoc [floorWindowMs]. */
        const val FLOOR_WINDOW_MS = 300

        /** 400 ms tiếng cộng dồn — ngắn hơn một từ tiếng Việt đọc chậm, đủ để loại một tiếng cạch. */
        const val MIN_SPEECH_MS = 400

        /** 800 ms im ⇒ chốt. Ngắn hơn thì cắt giữa hai vế của một câu ghép (*"bật đèn đọc … và mở kính"*). */
        const val HANGOVER_MS = 800

        /** Hệ số trên nền — xem KDoc lớp. */
        const val FLOOR_FACTOR = 3

        /** Sàn tuyệt đối của ngưỡng (thang rms 0..32767) — xem KDoc lớp. */
        const val ABS_FLOOR = 120
    }
}
