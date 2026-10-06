package com.byd.clusternav.launcher.voice

/**
 * ═══ CẮT CỬA SỔ MIC TẠI ĐIỂM HẾT TIẾNG — số học thuần, kiểm off-car ══════════════════════════════════════════
 *
 * Bằng chứng: `docs/diagnostics/voice-stream-eval-2026-09-16.md` **§6 · §8**.
 *
 * ## ⚠ Đây KHÔNG phải một phép tối ưu tốc độ — nó là một phép sửa ĐỘ CHÍNH XÁC
 * [ĐO host §6] Cùng mô hình đang ship, cùng tệp hotword, cùng 25 câu; **chỉ đổi độ dài đuôi im lặng nối thêm**:
 *
 * | đuôi im lặng | đúng nguyên văn |
 * |---|---|
 * | 0,00 s | **22/25** |
 * | 0,40 s | 18/25 |
 * | 0,75 s | 15/25 |
 * | 1,50 s | 11/25 |
 * | 4,00 s | **6/25** |
 *
 * Đường đang chạy trên xe nạp **nguyên cửa sổ mic tới trần** vào bộ giải mã: [ĐO xe] `chot=4200ms` với
 * `tieng_dut` thường ở 1–2 s ⇒ **2–3 giây đuôi không phải tiếng nói** đi thẳng vào mô hình, ở **mọi** lượt.
 * Đó đúng vùng mà bảng trên nói là 11–15/25. Và log xe có những chuỗi *"**ừ** bật đèn đọc"* · *"**đang đọc
 * sách**"* · *"mở cửa sổ **bật**"* — dạng token mọc thêm ở đầu/cuối, hệt như khi nạp đuôi rỗng ([SUY] — quan hệ
 * *đuôi dài ⇒ sai nhiều* là [ĐO host], còn việc nó giải thích các chuỗi lạ trong log xe thì chưa chốt trên xe).
 *
 * ⇒ Cắt đuôi vừa **giết luôn nguồn của ảo giác `ừ`/`ừm`** mà bản vá [P0-1] đang chặn ở đầu kia (coi chuỗi toàn
 * từ đệm là im lặng). Hai bản vá đánh cùng một con bệnh từ hai phía.
 *
 * ## Ba cách cắt đã đo, và vì sao chọn `head` ([ĐO host §8], 25 WAV)
 * | cách | điểm | vì sao |
 * |---|---|---|
 * | `segment` — chỉ đoạn VAD giữ lại | **8/25** | VAD mở đoạn **muộn** ⇒ nuốt mất từ đầu câu (*"bật đèn đọc"* → *"ĐÈN ĐỌC SÁCH"*) |
 * | `window` — từ đầu cửa sổ tới lúc VAD **chốt** | 18/25 | dính thêm cả `min_silence` ⇒ rơi lại đúng bẫy §6 |
 * | **`head` — từ đầu cửa sổ tới HẾT đoạn VAD, margin 0** | **21/25** | giữ trọn đầu câu, cắt sát đuôi |
 *
 * ## Vì sao số học này ở `:core` và tách khỏi VAD
 * VAD chỉ làm **cái đồng hồ**; thứ quyết định độ chính xác là **cắt ở đâu**, và đó là ba dòng số học. Tách ra thì
 * chúng kiểm được off-car bằng vài con số giả — kể cả ca hai đoạn (người ta ngắt giữa câu) mà một phép thử trên
 * xe gần như không bao giờ dựng lại được đúng lúc. Phần chạm ONNX nằm ở `:app` (`VoiceVad`).
 *
 * ⚠ 2.93: `scripts/voice/hotword-matrix.py --trim vad` (mặc định) chép tay [THRESHOLD] · [MIN_SPEECH_MS] ·
 * [MIN_SILENCE_MS] · [PRE_ROLL_MS] · [HEAD_SILENCE_CUT_MS] để đo hotword ĐÚNG đường app — đổi số ở đây thì đổi cả ở đó.
 * [ĐO host 2026-10-07] qua phép cắt này: 208 WAV có đuôi (im lặng / ồn máy · đường · nhạc 2 s) ⇒ 0 ca làm-nhầm do đuôi;
 * giải mã nguyên cửa sổ (đường lùi RMS) ⇒ 8 ca không hotword · 2 ca tệp tĩnh (spec `kachi-293-voice.html` §4.11).
 */
object VoiceVadTrim {

    /**
     * Một đoạn tiếng VAD đã chốt: vị trí bắt đầu (mẫu, tính từ đầu cửa sổ) + độ dài (mẫu).
     *
     * Gương của `com.k2fsa.sherpa.onnx.SpeechSegment` (`start: Int` + `samples: FloatArray`) nhưng **không mang
     * mẫu nào** — số học ở đây chỉ cần hai con số, và kéo cả mảng float vào `:core` là kéo theo cả bộ nhớ của
     * một lượt nghe vào một lớp lẽ ra chỉ để cộng trừ.
     */
    data class Segment(val startSample: Int, val lengthSamples: Int) {
        val endSample: Int get() = startSample + lengthSamples
    }

    // ── Tham số đã CHỐT BẰNG LƯỚI ([ĐO host §8]) ─────────────────────────────────────────────────

    /**
     * Ngưỡng xác suất của Silero. 0.35 — hạ từ 0.5 (mặc định Silero) sau on-car 2026-09-23: mic xe/giọng nhỏ nhiều câu doan=0 → hết trần. Chỉnh pref voice_vad_threshold nếu false-accept.
     *
     * Với bộ tham số này [ĐO host §5]: endpoint **p50 660 ms** · p90 780 ms · **0/1 899 cắt giữa câu** ·
     * **0/1 899 không nổ**. So với bộ RMS đang chạy trên xe (`chot=4200ms` ở 165/299 lượt, có lượt 8 400 ms).
     *
     * ⚠ Hai con số endpoint ấy đo với `min_silence = 0,15 s`. Từ [ĐO xe 2026-09-18] núm đó lên **0,60 s**
     * ([MIN_SILENCE_MS]) ⇒ cộng thẳng ~450 ms vào cả p50 lẫn p90 (≈ 1 110 / 1 230 ms). Ngưỡng xác suất và hai
     * tính chất *"cắt giữa câu"* / *"không nổ"* thì không đụng tới.
     */
    const val THRESHOLD = 0.45f

    /** Phải có ngần này tiếng thì VAD mới mở một đoạn. 0,10 s — điểm lưới. */
    const val MIN_SPEECH_MS = 100

    /**
     * Im ngần này thì VAD **đóng** đoạn ⇒ đó là điểm ngắt câu. **0,60 s** sau [ĐO xe 2026-09-18].
     *
     * ## ⚠ 150 ms là con số của corpus THU SẴN, và giọng lái xe thật đã bác nó
     * Lưới off-car §8 chốt 150 ms trên **câu thu sẵn** — loại câu không có quãng ngừng lấy hơi nào — nên kết luận
     * *"0/1 899 cắt giữa câu"* chỉ đúng **cho corpus đó**. [ĐO xe 2026-09-18, 53 phiên thật] `silence_ms` =
     * **150–174 ms ở 90/90 lượt**, tức VAD chốt đúng tại trần dưới của chính nó ở **mọi** lượt; và những câu cụt
     * lộ ra ngay: *"gập gương chiếu hậu"* → **`gặp gu`** (tiếng chỉ 814 ms) · *"chỉ số bụi mịn là bao nhiêu"* →
     * cụt còn `chỉ số bụi mịn`. Người lái ngừng 200–500 ms giữa câu để nghĩ; 200 ms > 150 ms ⇒ đoạn đóng ⇒
     * `VoiceCapture` thoát vòng nghe **ngay tại quãng ngừng**. Bằng chứng:
     * `docs/diagnostics/oncar-voice-cases-findings-2026-09-18.md` §A.
     *
     * ## Vì sao nâng lên KHÔNG hại độ chính xác — [ĐO nguồn sherpa-onnx v1.13.8]
     * Nỗi lo hợp lý là *"chờ im lâu hơn ⇒ nạp thêm im vào mô hình"*, tức rơi lại đúng cái bẫy §6. Nó **không xảy
     * ra**: `sherpa-onnx/csrc/voice-activity-detector.cc` cắt đuôi hangover **ra khỏi đoạn** trước khi đẩy vào
     * hàng đợi — `int32_t end = buffer_.Tail() - model_->MinSilenceDurationSamples();`. Nghĩa là
     * `segment.start + samples.size` = **điểm hết tiếng thật**, không phụ thuộc núm này, và [headTrimSamples]
     * (chế độ `head`, margin 0) cắt đúng ở đó. Thứ duy nhất tăng là **độ trễ chốt câu**: +450 ms (p50 ~660 →
     * ~1 110 ms), còn rất xa trần cứng `VoiceSession.MAX_LISTEN_MS` = 8 s.
     *
     * Đây cũng chính là lý do §8 đo `head` (21/25) hơn `window` (18/25): `window` cắt ở **lúc chốt** nên nó mới
     * là chế độ dính `min_silence` — `head` thì không, ở mọi giá trị của núm.
     *
     * ⚠ Vẫn nhỏ hơn `VoiceEndpointer.HANGOVER_MS` (800 ms), và vế *"Silero biết có phải giọng người không nên
     * dám chờ ít hơn bộ RMS"* vẫn nguyên — chỉ biên độ *"nhỏ hơn nhiều"* thì hết đúng. Núm
     * `voice_vad_min_silence_ms` (80–1 200 từ 2026-09-26, xem [MAX_MIN_SILENCE_MS]) chỉnh được trên xe, không cần build.
     */
    const val MIN_SILENCE_MS = 600

    /** Cửa sổ Silero v4 = 512 mẫu @16 kHz. Cũng đúng mặc định của `SileroVadModelConfig` trong AAR 1.13.8 [ĐO javap]. */
    const val WINDOW_SIZE = 512

    /** `margin 0` — điểm lưới đã chọn. Chừa thêm đuôi là đi ngược đúng phát hiện §6. */
    const val MARGIN_MS = 0

    // ── CẮT ĐẦU (im lặng DẪN ĐẦU) — VOICE-HEAD-SILENCE, [ĐO xe 2026-09-27] ──────────────────────

    /**
     * Giữ lại ngần này **trước** mốc bắt đầu tiếng khi phải cắt đầu. **0,30 s.**
     *
     * ## ⚠ Con số này là cái chặn đúng phát hiện §8 đã đo, không phải một biên "cho chắc"
     * Bảng §8 đo **ba** cách cắt, và cách tệ nhất là `segment` — *"chỉ giữ đoạn VAD"*, tức cắt **đúng tại** mốc
     * bắt đầu tiếng: **8/25**, vì *"VAD mở đoạn muộn ⇒ nuốt mất từ đầu câu"* (*"bật đèn đọc"* → *"ĐÈN ĐỌC SÁCH"*).
     * Nghĩa là mốc `segment.start` của Silero **đã** trễ so với âm đầu tiên của từ đầu — phụ âm bật hơi (`m` của
     * *"mở"*, `v` của *"vietmap"*) có năng lượng thấp nên nó chỉ vượt ngưỡng 0,45 sau khi nguyên âm đã vào.
     *
     * 0,30 s = **3×** [MIN_SPEECH_MS] (100 ms — lượng tiếng tối thiểu để Silero **mở** một đoạn), nên nó phủ trọn
     * phần trễ do chính luật mở đoạn gây ra, cộng biên. Và nó rất xa vùng độc của §6 (đuôi/đầu im lặng 0,75 s đã
     * kéo 22/25 xuống 15/25).
     */
    const val PRE_ROLL_MS = 300

    /**
     * Chỉ cắt đầu khi im lặng dẫn đầu **dài hơn** ngần này. **1,20 s.**
     *
     * ## Vì sao có một cổng, thay vì cắt đầu ở mọi lượt
     * Chế độ `head` (giữ **nguyên** phần đầu cửa sổ) là thứ §8 đo được **21/25** — cao nhất trong ba cách. Cắt đầu
     * ở mọi lượt là **đổi** đường đã đo tốt lấy một đường chưa đo, đúng điều CLAUDE.md §6 cấm (*"đường mới luôn
     * xuống cuối, và phải tự đo xem đường cũ có thật sự hụt không rồi mới leo"*). Cổng này là phép *"tự đo"* ấy:
     * lượt nào im lặng dẫn đầu còn ngắn thì **không có gì đổi** — cùng một mảng mẫu, cùng một chuỗi chữ.
     *
     * ## Con số 1,20 s đến từ đâu — [ĐO xe 2026-09-27, 22 lượt + 30 bản thu 26–27/09]
     * | im lặng dẫn đầu (`tieng_bat_dau`) | lượt | kết quả |
     * |---|---|---|
     * | 220 · 412 · 636 ms | phần lớn | nghe đúng |
     * | 1 020 ms | 10:31:03 | nghe đúng |
     * | **1 212 · 1 436 · 1 820 ms** | 10:36:15 · 10:42:44 ×2 · 10:36:56 | vế *"vào ô"* rụng |
     * | **6 012 ms** (chạm trần 8 s) | 10:36:52 | *"mở vietmap **hai**"* — mất cả *"vào ô số"* |
     *
     * Trần đặt **trên** mọi giá trị đã nghe đúng (1 020 ms) và **dưới** mọi giá trị đã nghe sai (1 212 ms) ⇒ trên
     * bộ 30 bản thu thật 26–27/09, **0 bản** đổi điểm cắt (mốc bắt đầu tiếng của chúng: 220…1 020 ms) và mọi lượt
     * bệnh đều được cắt. Đây là thứ làm bản vá này **không thể** làm hụt một ca đang chạy.
     *
     * ⚠ 6 giây im lặng nạp vào bộ giải mã không phải một khoản CPU thừa — nó là **độ chính xác**: bảng §6 đo đúng
     * quan hệ *"im lặng nối thêm ⇒ sai nhiều hơn"* (22/25 → 6/25 với 4 s). Bản này chỉ cắt ở **đầu** thứ mà
     * [headTrimSamples] đã cắt ở **đuôi** từ 2026-09-16, bằng cùng một lẽ.
     */
    const val HEAD_SILENCE_CUT_MS = 1_200

    // ── Dải cho phép của ba núm ẩn (`prefs_set`) ────────────────────────────────────────────────

    /** Dưới 0,20 thì tiếng ồn cabin thành "giọng"; trên 0,90 thì giọng nhỏ bị bỏ. */
    const val MIN_THRESHOLD = 0.20f
    const val MAX_THRESHOLD = 0.90f

    /** Dưới 40 ms thì một tiếng cạch mở được đoạn; trên 500 ms thì một từ đơn (*"tắt"*) không mở nổi đoạn nào. */
    const val MIN_MIN_SPEECH_MS = 40
    const val MAX_MIN_SPEECH_MS = 500

    /**
     * Dưới 80 ms thì một quãng ngắt hơi giữa câu cũng đóng đoạn (**cắt giữa câu** — đúng lỗi [ĐO xe 2026-09-18]
     * bắt được ở mức 150 ms); trên 1 200 ms thì **độ trễ chốt câu** ăn quá sâu vào trần cứng 8 s và người lái tưởng
     * máy thôi nghe.
     *
     * ⚠ Trần trên **KHÔNG** phải để chặn *"đuôi im lặng nạp vào mô hình dài bằng bản RMS cũ"* như bản đầu ghi:
     * sherpa tự cắt đuôi hangover khỏi đoạn nên đuôi ấy chưa bao giờ vào bộ giải mã — xem [MIN_SILENCE_MS].
     *
     * ## Trần 800 → **1 200 ms** (VAD-SILENCE-CAP, 2026-09-26) — vì sao nới TRẦN mà KHÔNG đổi mặc định
     * [ĐO xe 2026-09-26] owner gặp câu ghép *"mở &lt;app&gt; vào ô số 2"* bị cụt và thử `prefs_set
     * voice_vad_min_silence_ms 1100` **trên xe**: trần cũ 800 ⇒ giá trị bị từ chối (`TestBridgePrefsSet` không
     * kẹp im lặng, nó trả `bad_prefs_value`) ⇒ núm chỉnh-trên-xe **không tới được** vùng cần đo. Đó là cái trần
     * sai, không phải cái mặc định sai — và đây là phép đo chứng minh:
     *
     * | quãng ngừng đo được trên 30 bản thu thật 26/09 | ca |
     * |---|---|
     * | 120–260 ms — **trong cùng một vế** (*"mở youtube ⟨200 ms⟩ vào ô số hai"*) | 8 |
     * | 720–1 060 ms — **ngừng để nghĩ giữa hai vế** | 4 |
     *
     * Mặc định 600 ms đã **≥ 2,3×** quãng ngừng trong-vế lớn nhất, và [ĐO] 13/13 bản thu câu-có-ô đều chốt **sau**
     * điểm hết tiếng (điểm cắt ≥ điểm hết tiếng) ⇒ mặc định chưa từng cắt cụt một câu ghép nào trong bộ này; cái
     * làm mất vế *"vào ô số N"* là **bộ giải mã**, xem KDoc [VoiceSlotPhrases]. Còn nhóm ngừng-để-nghĩ 720–1 060 ms
     * thì **không** giá trị nào ≤ 800 đỡ được — đúng vùng mà trần mới mở ra để đo tiếp trên xe (🚗).
     *
     * ⚠ Nâng **mặc định** lên ≥ 800 sẽ phá bất biến *"VAD chốt sớm hơn bộ RMS"* ([VoiceEndpointer.HANGOVER_MS] =
     * 800 ms, bài `VoiceVadTrimTest` canh) và cộng ≥ 200 ms vào **mọi** lượt nói, kể cả câu một vế. Nên mặc định ở
     * lại 600; muốn hơn thì chỉnh núm, và chỉnh xong phải đo lại độ trễ chốt câu trên xe.
     */
    const val MIN_MIN_SILENCE_MS = 80
    const val MAX_MIN_SILENCE_MS = 1200

    /**
     * ═══ `head` — CẮT TỚI HẾT ĐOẠN TIẾNG CUỐI CÙNG ĐÃ CHỐT ═══════════════════════════════════════════════
     *
     * @param segments các đoạn VAD đã chốt **tại thời điểm dừng nghe** (thứ tự bất kỳ).
     * @param windowSamples tổng số mẫu đã thu trong cửa sổ mic.
     * @param marginSamples chừa thêm sau đuôi đoạn (mặc định 0 — xem [MARGIN_MS]).
     * @return số mẫu **đầu cửa sổ** được đưa vào bộ giải mã: `pcm[0 until kếtQuả]`.
     *
     * ## Ngang bằng với `scripts/voice/stream-matrix.py` (chế độ `head`)
     * Script host — thứ đã sinh ra con số 21/25 ở §8 — tính đúng thế này:
     * ```python
     * end = vad.front.start + len(vad.front.samples) + int(margin * sr)
     * seg = audio[:max(1, min(len(audio), end))]
     * ```
     * Hàm này là **cùng một phép tính**, với một khác biệt có chủ ý ở ca nhiều đoạn (dưới).
     *
     * ## ⚠ Ca NHIỀU ĐOẠN: lấy đoạn **CUỐI**, không lấy đoạn đầu
     * Script host đọc `front` — đoạn **đầu** hàng đợi — vì nó quyết định ngay ở khối đầu tiên mà hàng đợi khác
     * rỗng, nên ở đó `front` **chính là** đoạn duy nhất. Trên xe thì một khối 200 ms có thể làm chốt nhiều hơn
     * một đoạn (người ta ngắt giữa câu đúng lúc), và lúc ấy hai cách cho ra hai kết quả rất khác nhau: lấy đoạn
     * đầu là **cắt mất nửa sau câu** — im lặng, không lỗi, và người lái chỉ thấy Kachi hiểu sai. Lấy đoạn cuối
     * thì ca một-đoạn (gần như mọi lượt) ra **đúng y** con số của script, còn ca nhiều-đoạn thì an toàn hơn.
     * Nói cách khác: bằng nhau ở chỗ đã đo, chặt hơn ở chỗ chưa đo.
     *
     * ## Không có đoạn nào ⇒ trả NGUYÊN cửa sổ
     * Cùng nhánh `if seg is None: seg = audio` của script. Đây là ca *"VAD không nổ"* ([ĐO] 0/1 899 trên host,
     * nhưng cabin thật thì chưa ai đo): thà giải mã thừa như bản cũ còn hơn giải mã một mảng rỗng.
     */
    fun headTrimSamples(segments: List<Segment>, windowSamples: Int, marginSamples: Int = 0): Int {
        if (windowSamples <= 0) return 0
        val lastEnd = segments.maxOfOrNull { it.endSample } ?: return windowSamples
        // `max(1, …)` giữ đúng script: một đoạn chốt ở mẫu 0 vẫn phải cho bộ giải mã một mẫu, không phải mảng rỗng.
        return maxOf(1, minOf(windowSamples, lastEnd + marginSamples))
    }

    /**
     * ═══ VOICE-HEAD-SILENCE — mẫu ĐẦU TIÊN được đưa vào bộ giải mã ═══════════════════════════════════════════
     *
     * @param segments các đoạn VAD đã chốt (thứ tự bất kỳ).
     * @param windowSamples tổng số mẫu đã thu.
     * @param preRollSamples giữ lại ngần này trước mốc bắt đầu tiếng ([PRE_ROLL_MS]).
     * @param cutAboveSamples chỉ cắt khi im lặng dẫn đầu **vượt** ngần này ([HEAD_SILENCE_CUT_MS]).
     * @return chỉ số mẫu đầu: bộ giải mã đọc `pcm[kếtQuả until headTrimSamples(...)]`.
     *
     * Ba lẽ của ba dòng thân hàm đều nằm ở KDoc [PRE_ROLL_MS] và [HEAD_SILENCE_CUT_MS]. Riêng ca **không có đoạn
     * nào** ⇒ trả `0`, cùng nhánh *"VAD không nổ ⇒ giải mã nguyên cửa sổ"* của [headTrimSamples]: không biết tiếng
     * bắt đầu ở đâu thì không được cắt ở đâu cả.
     */
    fun headStartSamples(
        segments: List<Segment>,
        windowSamples: Int,
        preRollSamples: Int,
        cutAboveSamples: Int,
    ): Int {
        if (windowSamples <= 0) return 0
        val onset = segments.minOfOrNull { it.startSample } ?: return 0
        if (onset <= cutAboveSamples) return 0
        return (onset - preRollSamples).coerceIn(0, windowSamples)
    }

    /**
     * ═══ VOICE-OPEN-TURN — khúc mẫu của **vế SAU**, khi lượt nghe được giữ mở qua một quãng ngừng ═══════════
     *
     * @param segments **toàn bộ** đoạn đã chốt của lượt (kể cả các đoạn của vế trước).
     * @param fromIndex số đoạn đã có **tại điểm ngắt câu** của vế trước ⇒ đoạn thứ `fromIndex` là đoạn đầu của
     *   vế sau. Bằng `segments.size` ⇒ chưa có vế sau nào ⇒ `null`.
     * @param windowSamples tổng số mẫu đã thu.
     * @param marginSamples chừa thêm sau đuôi (mặc định 0 — cùng lẽ [MARGIN_MS]).
     * @return dải `[đầu .. cuối)` để giải mã RIÊNG vế sau, hoặc `null` khi không có vế sau.
     *
     * ## Vì sao giải mã RIÊNG vế sau chứ không nối hai khúc rồi giải mã một lần
     * [ĐO 2026-09-26, bản thu thật `…-184201`] cắt tay rồi giải mã từng khúc: `[0..1300]` ⇒ *"mở vietmap"*,
     * `[900..2600]` ⇒ *"áp vào ô số một"*; **nguyên cửa sổ** ⇒ *"mở vietmap **một**"* — bộ giải mã bỏ ba chữ ở
     * giữa. Đó là chính cái bệnh VOICE-SLOT-TAIL-CUT (KDoc [VoiceSlotPhrases] §2), và một khoảng lặng
     * 720–1 060 ms ở giữa chỉ làm nó nặng thêm. ⇒ Hai khúc, hai lượt giải mã, ghép ở tầng CHỮ
     * ([VoiceOpenTurn.join]).
     *
     * **Đầu dải lấy đúng mốc bắt đầu của đoạn**, không lấy điểm ngắt của vế trước: kẹp từ điểm ngắt là kéo theo
     * trọn quãng ngừng 720–1 060 ms vào bộ giải mã — rơi lại đúng bảng §6 ở KDoc lớp (đuôi im lặng 0,75 s hạ
     * 22/25 xuống 15/25), chỉ là im lặng ở **đầu** thay vì ở đuôi.
     */
    fun tailRange(
        segments: List<Segment>,
        fromIndex: Int,
        windowSamples: Int,
        marginSamples: Int = 0,
        /**
         * Giữ lại ngần này **trước** mốc bắt đầu của vế sau ([PRE_ROLL_MS]) — cùng lẽ [headStartSamples]: mốc
         * `segment.start` của Silero trễ so với phụ âm đầu, và §8 đo được cắt đúng tại mốc ấy chỉ cho **8/25**.
         *
         * ⚠ Chặn dưới là **điểm hết tiếng của vế TRƯỚC**: lùi quá mốc đó là kéo lại tiếng của vế trước vào lượt
         * giải mã vế sau, tức [VoiceOpenTurn.join] phải gỡ một phần trùng dài hơn — đúng thứ nó đã phải làm, nhưng
         * không cần thiết. Không có vế trước (fromIndex = 0) ⇒ chặn dưới là 0.
         */
        preRollSamples: Int = 0,
    ): IntRange? {
        if (windowSamples <= 0 || fromIndex < 0 || fromIndex >= segments.size) return null
        val tail = segments.subList(fromIndex, segments.size)
        val floor = segments.take(fromIndex).maxOfOrNull { it.endSample } ?: 0
        val onset = tail.minOf { it.startSample }
        val start = maxOf(floor, onset - preRollSamples).coerceIn(0, windowSamples)
        val end = minOf(windowSamples, tail.maxOf { it.endSample } + marginSamples)
        return if (end <= start) null else start until end
    }

    /** Đổi mili-giây → giây cho `SileroVadModelConfig` (nó nhận `Float` GIÂY). Một chỗ đổi, không rải rác. */
    fun msToSeconds(ms: Int): Float = ms / 1000f

    /** Đổi mili-giây → số mẫu @16 kHz — dùng cho [headTrimSamples] và cho các mốc giờ trong nhật ký. */
    fun msToSamples(ms: Int, sampleRate: Int): Int = (ms.toLong() * sampleRate / 1000L).toInt()

    /** Số mẫu → mili-giây, cho nhật ký `KachiVoiceTiming`. */
    fun samplesToMs(samples: Int, sampleRate: Int): Int =
        if (sampleRate <= 0) 0 else (samples.toLong() * 1000L / sampleRate).toInt()
}
