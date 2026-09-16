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
 * ## ⚠⚠ Ngưỡng: `max(2 × min(nền, 90), 120)` — ĐỔI 2026-09-16 sau một lỗi hiện trường [P0]
 *
 * ### Cái sai của bản cũ (`max(3 × nền, 120)`, nền không có trần)
 * [ĐO xe 2026-09-16] `voice-1.68-real.txt`, 12 phút trên xe owner: **189/300** lượt nghe báo `tieng_bat_dau=-1`
 * — *chưa bao giờ nghe thấy tiếng* — và **187** trong số đó chạy hết trần 8,4 s ở `pha=WAITING`. Trong 189 lượt
 * ấy có **68 lượt** mang một ngưỡng vô lý: nền đo được 531 · 602 · 888, có lượt tới **4317**, trong khi giọng
 * thật của chính bản ghi ấy chỉ **rms 206–627**. Ngưỡng nằm cao gấp 8 lần giọng ⇒ không âm nào vượt nổi.
 *
 * Nền bị nhiễm vì cửa sổ đo nền (300 ms đầu) **trùng đúng với tiếng bíp**: bíp đầu lượt 90 ms, và tệ hơn — tiếng
 * bíp CUỐI của lượt trước còn đang phát trên luồng `KachiMicTail` (nhật ký thật: `nền: bíp 4955 ms`) trong khi
 * lượt nối mở micro chỉ ~50 ms sau đó. Bộ đo nền nghe tiếng bíp của chính Kachi rồi kết luận "cabin này ồn 888".
 *
 * ### Ba thay đổi, và vì sao đúng ba con số này
 * Mọi số dưới đây chọn từ chính bản ghi ấy: **giọng thật rms 206–627** (đỉnh 2500–5900), **im thật rms 30–36**
 * (cá biệt 66), **nền nhiễm 531 / 602 / 888 / 4317**.
 *  1. **Trần nền [FLOOR_CAP] = 90** — một cửa sổ nhiễm không được phép đẩy ngưỡng ra khỏi tầm giọng nói. 90 nằm
 *     trên mức im thật cao nhất quan sát được (66) với một khoảng thở, và dưới hẳn mọi nền nhiễm.
 *  2. **Hệ số [FLOOR_FACTOR] = 2** (trước là 3) — với trần 90, ngưỡng lớn nhất có thể là `max(180, 120) = 180`,
 *     tức **luôn thấp hơn** giọng yếu nhất đo được (206). Hệ số 3 sẽ cho 270 > 206 ⇒ vẫn điếc ở đúng ca này.
 *  3. **sàn [ABS_FLOOR] = 120 giữ nguyên** — vẫn là thứ đỡ ca *"nền gần 0"*: [ĐO xe] lượt 09:14 có rms 30–50 cả
 *     phiên, nếu không có sàn thì ở một lượt nền = 2 mọi tiếng lạo xạo rms 8 đã thành "có tiếng".
 *
 * ### Đánh đổi, nói thẳng: bộ này nay THIÊN VỀ "nghe thấy"
 * Trần nền làm bộ đo mất khả năng bám theo một cabin **thật sự** ồn hơn 90. Đó là một đánh đổi có chủ ý và không
 * đối xứng: một lần nhận nhầm tiếng ồn thành tiếng nói chỉ khiến lượt ấy chờ hết quãng im rồi giải mã — đúng
 * hành vi cũ, mất vài trăm ms. Một lần **không bao giờ** nhận ra tiếng nói thì lượt ấy chắc chắn ăn trọn 8,4 s,
 * và [ĐO] cho thấy ca đó xảy ra ở 63% số lượt. Nên khi phải chọn, chọn nhầm về phía nghe thấy.
 *
 * Trần nền chỉnh được trên xe (`voice_endpoint_floor_cap`) — xem [floorCap].
 *
 * ## Trần vẫn còn, và đó là điều cố ý
 * Bộ này chỉ **rút ngắn** một lượt nghe; nó không thay trần cứng (`VoiceSession.MAX_LISTEN_MS`). Một cabin ồn
 * liên tục làm nó không bao giờ thấy "im" ⇒ đúng ca mà trần sinh ra để đỡ (CLAUDE.md §3: không gate một đường
 * phục hồi bằng dữ liệu mà chỉ chính đường đó mới làm mới được).
 *
 * ## ⚠⚠ HỢP ĐỒNG VỚI CHỖ GỌI — [Phase.ENDED] **KHÔNG** phải một lời hứa "lượt nào cũng kết thúc"
 * [SOÁT 2026-09-16 · P3] Ở pha [Phase.WAITING] một khối **im** không cộng vào [silenceMs] (chỉ pha
 * [Phase.SPEAKING] mới cộng — xem nhánh cuối [accept]), nên một lượt **không ai nói gì** đứng ở `WAITING`
 * **mãi mãi**: không có nhánh nào đưa `WAITING → ENDED`. Đó là **cố ý** và là điều đúng — đếm im lặng từ trước
 * khi có tiếng thì người lái bấm mic rồi hít một hơi là lượt đã tự chốt, tức cắt lời ngay trước khi họ mở miệng
 * (xem chú thích trong [accept]). Nhưng nó đặt một **nghĩa vụ lên chỗ gọi**, và nghĩa vụ ấy phải được viết ra
 * chứ không để ai đó suy ra: **ai đóng phiên dựa vào `ENDED` thì PHẢI tự có trần thời gian.**
 *
 * [ĐO mã 2026-09-16] Chỗ gọi thật **đang** giữ đúng nghĩa vụ đó, bằng hai lớp:
 *  1. `VoiceCapture.kt:203,214` — vòng nghe chạy dưới `deadline = tListen + maxMs`, và `maxMs` của phiên thật là
 *     `VoiceSession.MAX_LISTEN_MS = 8_000` (`VoiceSession.kt:231,448`) ⇒ lượt câm dừng ở trần, không treo;
 *  2. `VoiceCapture.kt:273` — `decodeOnlyIfSpeech && !ep.sawSpeech()` ⇒ lượt chưa từng có tiếng **không** đi
 *     giải mã (đó là lý do [sawSpeech] tồn tại, xem KDoc của nó).
 *
 * `VoiceEndpointerTest` khoá lại vế của **bộ này**: im mãi ⇒ vẫn `WAITING`, `silenceMs` không nhúc nhích,
 * [sawSpeech] vẫn `false`. Ngày ai đó thêm nhánh tự chốt ở `WAITING` (hoặc bỏ trần ở chỗ gọi), bài ấy đỏ trước
 * khi một người lái nào đó bị cắt lời.
 */
class VoiceEndpointer(
    /** Cửa sổ đo nền ở đầu phiên. 300 ms — đủ vài khối 200 ms, ngắn hơn quãng người ta kịp bắt đầu nói. */
    private val floorWindowMs: Int = FLOOR_WINDOW_MS,
    /** Phải có ngần này tiếng (cộng dồn) thì mới được phép chốt câu — chặn "chốt vì một tiếng cạch". */
    private val minSpeechMs: Int = MIN_SPEECH_MS,
    /** Im liên tục ngần này sau khi đã có tiếng ⇒ chốt. */
    private val hangoverMs: Int = HANGOVER_MS,
    /**
     * **Trần** cho mức nền đo được — xem KDoc lớp, thay đổi (1). Một cửa sổ đo nhiễm (tiếng bíp của chính Kachi)
     * không được phép đẩy ngưỡng ra khỏi tầm giọng nói. Chỉnh trên xe bằng `voice_endpoint_floor_cap`.
     */
    private val floorCap: Int = FLOOR_CAP,
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

    /**
     * H5 — **tổng thời gian CÓ TIẾNG** (cộng dồn, ms). Công khai chỉ-đọc vì nó là một trong ba con số mà
     * `KachiVoiceTiming` phải in ra mỗi lượt nghe: không có nó thì một lượt chốt ở 1,4 s đọc giống hệt nhau
     * trong hai ca trái ngược — *người ta nói 600 ms rồi im* và *người ta chưa nói gì, một tiếng cạch chen vào*.
     */
    var voicedMs: Int = 0
        private set

    /**
     * H5 — quãng im **liên tục** tính tới lúc này (ms); về 0 mỗi khi có tiếng trở lại. Đây là con số so thẳng
     * với [hangoverMs], nên nó là thứ trả lời *"ngưỡng im có đang đặt sát quá không"* trên cabin thật.
     */
    var silenceMs: Int = 0
        private set

    private val floorSamples = ArrayList<Int>(8)

    /** Ngưỡng "có tiếng" đang dùng; `-1` khi chưa đo xong nền. Công thức + lý do từng con số: KDoc lớp. */
    fun threshold(): Int = if (floor < 0) -1 else maxOf(floor * FLOOR_FACTOR, ABS_FLOOR)

    /**
     * Lượt này **đã từng** nghe thấy tiếng chưa.
     *
     * Đây là câu hỏi mà `VoiceCapture` phải trả lời được TRƯỚC khi quyết định có giải mã hay không: [ĐO xe
     * 2026-09-16] 189/300 lượt trả lời *"chưa"*, và mỗi lượt như thế vẫn chạy một lượt giải mã 1,3–2 s để rồi
     * mô hình **bịa ra** `"ừ"`/`"ừm"` (102 lần mỗi chuỗi trong 12 phút). Hỏi ở đây, không suy lại ở chỗ gọi:
     * `speechStartMs` là trạng thái của bộ này, và một phép suy chép sang nơi khác là bản sao thứ hai của một
     * luật (CLAUDE.md §4.1).
     */
    fun sawSpeech(): Boolean = speechStartMs >= 0

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
            // ⚠ Trần nền áp ở ĐÂY, ngay lúc chốt — không áp trong [threshold]. Hai lý do: nhật ký in ra `nen=`
            // phải là con số **thật sự đang dùng** (một `nen=888` in ra rồi bị kẹp ngầm ở chỗ khác là một dòng
            // log nói dối), và mọi phép đọc sau này chỉ có một nguồn.
            floor = minOf(median(floorSamples), floorCap)
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

    /**
     * Một dòng nhật ký cho `KachiVoiceTiming` — mốc giờ của chính lượt này, không phải một lời kể.
     *
     * ## H5 — ba con số phải ĐỨNG RIÊNG và **grep được**
     * Bản trước in `chot=…` rồi để người đọc tự suy phần còn lại từ `tieng_bat_dau`/`tieng_dut`. Nhưng hai mốc ấy
     * là **thời điểm**, còn thứ cần để chỉnh núm là **thời lượng**: một lượt `tieng_bat_dau=600ms tieng_dut=2400ms`
     * KHÔNG nói được trong 1,8 giây đó có bao nhiêu là tiếng thật (người nói ngắt giữa chừng thì `voicedMs` nhỏ
     * hơn hẳn hiệu hai mốc). Nên ba con số của hợp đồng H5 — **ngắt ở / tiếng / im** — được in thẳng, cạnh đúng
     * hai ngưỡng đang áp ([minSpeechMs] · [hangoverMs]) để một dòng log đọc ra ngay *"đặt bao nhiêu, đo được bao
     * nhiêu"* mà không phải mở mã hay hỏi prefs.
     */
    fun summary(): String =
        "nen=$floor nguong=${threshold()} tieng_bat_dau=${speechStartMs}ms tieng_dut=${speechEndMs}ms " +
            "chot=${elapsedMs}ms pha=$phase · ngat_o=${elapsedMs}ms · tieng=${voicedMs}ms · im=${silenceMs}ms " +
            "(nguong_im=${hangoverMs}ms · toi_thieu_tieng=${minSpeechMs}ms)"

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

        /**
         * H5 — dải cho phép của núm `voice_endpoint_silence_ms`. **Một** bản khai cho cả prefs, cầu kiểm thử và
         * bài canh; ba bản là ba con số sẽ lệch nhau ở lần vá đầu tiên (CLAUDE.md §4.1 DRY).
         *
         * Sàn 600 ms: dưới mức đó là cắt giữa hai vế của một câu ghép — đúng ca [HANGOVER_MS] sinh ra để tránh.
         * Trần 1500 ms: trên mức đó thì bộ ngắt câu không còn rút ngắn được gì đáng kể so với trần cứng 8 s của
         * một phiên, tức núm mất tác dụng mà người chỉnh vẫn tưởng mình đang chỉnh.
         */
        const val MIN_HANGOVER_MS = 600
        const val MAX_HANGOVER_MS = 1_500

        /**
         * H5 — dải cho phép của núm `voice_endpoint_min_speech_ms`.
         *
         * Sàn 200 ms ≈ MỘT khối đọc micro (`VoiceCapture.CHUNK_SAMPLES` = 200 ms): thấp hơn thì cổng *"phải có
         * đủ tiếng mới được chốt"* không còn chặn được một tiếng cạch nào, tức nó thành một cổng trang trí.
         * Trần 1000 ms: cao hơn thì một câu hai từ (*"mở kính"*) không bao giờ đủ điều kiện ⇒ mọi lượt lại chạy
         * hết trần 8 s, đúng cái bệnh mà cả [VoiceEndpointer] sinh ra để chữa.
         */
        const val MIN_MIN_SPEECH_MS = 200
        const val MAX_MIN_SPEECH_MS = 1_000

        /** Hệ số trên nền — **2** từ 2026-09-16 (trước là 3); xem KDoc lớp, thay đổi (2). */
        const val FLOOR_FACTOR = 2

        /** Sàn tuyệt đối của ngưỡng (thang rms 0..32767) — xem KDoc lớp. */
        const val ABS_FLOOR = 120

        /**
         * **Trần** của mức nền — 90. Xem KDoc lớp, thay đổi (1): trên mức im thật cao nhất quan sát được (66),
         * dưới hẳn mọi nền nhiễm ([ĐO] 531 · 602 · 888 · 4317), và `2 × 90 = 180` vẫn dưới giọng yếu nhất (206).
         */
        const val FLOOR_CAP = 90

        /**
         * Dải cho phép của núm `voice_endpoint_floor_cap`.
         *
         * Sàn 40: dưới mức im thật (30–36) thì trần nền vô nghĩa — ngưỡng luôn rơi về [ABS_FLOOR].
         * Trần 400: `2 × 400 = 800` đã cao hơn phần lớn dải giọng đo được (206–627), tức cao hơn nữa là tự bật
         * lại đúng cái lỗi bản này sinh ra để chữa; ai cần hơn thì phải sửa mã và ghi lại lý do.
         */
        const val MIN_FLOOR_CAP = 40
        const val MAX_FLOOR_CAP = 400
    }
}
