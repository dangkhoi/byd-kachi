package com.byd.clusternav.launcher.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.ToneGenerator
import android.util.Log
import com.byd.clusternav.Prefs
import com.byd.clusternav.voiceEndpointFloorCap
import com.byd.clusternav.voiceEndpointMinSpeechMs
import com.byd.clusternav.voiceEndpointSilenceMs
import com.byd.clusternav.voiceMicSource

/**
 * ═══ V1 pha NGHE · MICRO → PCM → BỘ NHẬN DẠNG ════════════════════════════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-voice-command.html` **R11**. Đây là **nơi duy nhất** trong dự án mở micro.
 *
 * ## Mức bằng chứng cho việc "app thường mở được micro trên xe này" (CLAUDE.md §2)
 * **[ĐO], gián tiếp nhưng là số đo thật**: Kiki Car (`ai.zalo.kiki.car`) là **app thường** — không phải app hệ
 * thống, không ký khoá nhà sản xuất — và nó chạy wake word tại máy trên chính đầu xe của owner, tức nó giữ
 * micro mở liên tục qua `AudioSource.MIC` (`docs/diagnostics/kiki-car-RE-2026-09-14.md`). Owner 2026-09-14:
 * *"Kiki nó chạy được, Gemini chạy được trên xe thì app mình cũng chạy được, đâu cần chứng minh gì nữa"* ⇒ cổng
 * tầng 1 của CLAUDE.md §14 được owner **miễn cho riêng mục micro**, và lý do miễn được ghi lại ở đây chứ không
 * chỉ trong một câu chat.
 *
 * **[CHƯA BIẾT]** và cố ý không đụng tới ở pha này: phát tiếng nói ra (TTS tiếng Việt). Phản hồi là **âm báo +
 * chữ** ([tone] + tấm chữ ở [VoiceOverlay]).
 *
 * ## Ba quyết định về âm thanh, mỗi cái là một lựa chọn có thể sai theo hướng khác
 *  1. **`MIC` trước** (V3 · R1, đổi 2026-09-16 — trước đó là `VOICE_RECOGNITION` trước). Lập luận cũ *"nguồn 6
 *     bỏ qua AGC/khử ồn nên sạch hơn cho mô hình"* đúng về cơ chế nhưng **bị [ĐO xe] bác về quy kết**: trên ROM
 *     này nguồn 6 cho tiếng gần câm 3/4 lượt. Thứ tự + lý do đầy đủ ở [VoiceMicSource]; ROM vẫn có thể dựng ra
 *     `AudioRecord` `STATE_UNINITIALIZED` **mà không ném**, nên vẫn phải kiểm trạng thái rồi mới lùi nguồn sau.
 *  2. **KHÔNG tắt nhạc — chỉ xin `TRANSIENT_MAY_DUCK`.** Dừng hẳn nhạc cho một câu 3 giây là cắt ngang thứ
 *     người ta đang nghe rồi trả lại ở chỗ khác. Hạ tiếng thì đủ để micro nghe rõ mà bài hát không đứt.
 *  3. **Âm báo, không phải giọng nói.** Tiếng "bíp" đầu/cuối trả lời đúng câu hỏi duy nhất người lái có lúc ấy
 *     (*"nó bắt đầu/kết thúc nghe chưa"*) trong 80 ms, không cần nhìn màn hình, và **không cần TTS** — thứ còn
 *     [CHƯA BIẾT] trên xe này.
 */
internal class VoiceCapture(private val ctx: Context) {

    /** Micro đã được cấp quyền chưa. */
    fun hasPermission(): Boolean =
        ctx.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    /**
     * Chữ nghe được + **khúc PCM đã thu** của chính lượt ấy.
     *
     * ## Vì sao giữ lại tiếng, trong một dự án mà cả một bài canh sinh ra để tiếng KHÔNG rời khỏi xe
     * V1.1 cần một lượt giải mã **thứ hai** (tự do) trên đúng khúc tiếng vừa nghe, để đọc tên bài / điểm đến —
     * xem KDoc [VoiceOpenVocab]. Khúc ấy sống **trong RAM của tiến trình** (không tệp, không mạng — bài canh R14
     * vẫn nguyên hiệu lực), có **trần cứng** [MAX_KEPT_SAMPLES] = 9 giây ≈ 288 KB (dài hơn trần một phiên đúng
     * một giây để không cắt cụt câu cuối), và chết cùng lượt nghe.
     */
    data class Heard(
        val text: String,
        val pcm: ShortArray,
        val samples: Int,
        /**
         * ═══ H2/H5 — SỐ ĐO của chính lượt nghe này, đi kèm chữ ═══════════════════════════════════════
         *
         * Sáu trường dưới đây là thứ [VoiceUtteranceLog] ghi vào tệp JSON và thứ `KachiVoiceTiming` in ra. Chúng
         * đi **cùng** kết quả chứ không nằm trong một ô nhớ dùng chung của lớp: một phiên có tới ba lượt nghe
         * (chính · xác nhận · hội thoại) và hai trong ba chạy trên luồng nền chồng lấn với luồng vẽ — một trường
         * `lastEndpointMs` của lớp sẽ bị lượt sau ghi đè trước khi lượt trước kịp được ghi ra tệp, và tệp JSON
         * sẽ mô tả **lượt khác** với tệp WAV cạnh nó. Kiểu sai đó không có gì báo và không ai phát hiện ra khi
         * đọc lại nhật ký ba tháng sau.
         *
         * Tất cả có **mặc định 0/false** để ba đường thoát sớm của [listen] (không mở được micro, ROM từ chối
         * ghi, `startRecording` ném) không phải bịa ra một con số nào.
         */
        val endpointMs: Int = 0,
        val speechMs: Int = 0,
        val silenceMs: Int = 0,
        /** `true` = bộ ngắt câu chốt; `false` = chạm trần cứng của phiên (hai ca rất khác nhau khi chỉnh núm). */
        val endpointFired: Boolean = false,
        /** Micro mở bao lâu (ms) · lượt giải mã mất bao lâu (ms). */
        val listenMs: Long = 0L,
        val decodeMs: Long = 0L,
        /** Hằng `MediaRecorder.AudioSource` **thật sự** mở được (không phải cái được ưu tiên). */
        val micSource: Int = 0,
    ) {
        // `data class` mang `ShortArray` ⇒ `equals`/`hashCode` sinh sẵn so theo THAM CHIẾU. Khai lại tường minh
        // để không ai vô tình dựa vào một phép so sai; lớp này không bao giờ cần so bằng.
        override fun equals(other: Any?): Boolean = this === other
        override fun hashCode(): Int = System.identityHashCode(this)
    }

    /**
     * Nghe MỘT lượt: mở micro, đẩy từng khối vào [rec], dừng khi Vosk chốt câu / hết [maxMs] / [cancelled].
     *
     * **CHẶN** ⇒ luồng nền. Trả chữ nghe được (có thể rỗng) kèm khúc PCM — xem [Heard].
     *
     * @param onPartial chữ đang nghe dở — gọi **trên luồng nền**, chỗ gọi tự đẩy lên luồng vẽ.
     * @param cancelled hỏi mỗi vòng; `true` ⇒ dừng ngay và trả phần đã nghe.
     * @param keepPcm giữ lại khúc tiếng hay không. `false` cho lượt nghe câu *"đồng ý/huỷ"* — nó không bao giờ
     *   cần lượt giải mã thứ hai, nên giữ tiếng ở đó là giữ một thứ không ai dùng.
     */
    @Suppress("ReturnCount", "LongParameterList", "LongMethod", "CyclomaticComplexMethod")
    fun listen(
        rec: VoiceRecognizer,
        maxMs: Long,
        cancelled: () -> Boolean,
        keepPcm: Boolean = false,
        /**
         * H5 — mặc định KHÔNG còn là `VoiceEndpointer()` trần mà là [endpointerFromPrefs]: hai ngưỡng của nó
         * (`voice_endpoint_silence_ms` · `voice_endpoint_min_speech_ms`) chỉnh được trên xe. Mặc định của hai
         * khoá ấy **bằng đúng** hai hằng cũ ⇒ máy chưa ai chỉnh chạy y hệt 1.68.
         */
        endpointer: VoiceEndpointer? = endpointerFromPrefs(),
        /**
         * V3 · R9 — có kêu tiếng bíp **đầu** lượt không.
         *
         * `false` cho lượt nối của hội thoại: tiếng bíp trả lời câu *"nó bắt đầu nghe chưa"* của một phiên do
         * người dùng vừa mở; trong một vòng hội thoại thì micro chỉ **chưa đóng**, và một tiếng bíp sau mỗi câu
         * trả lời là thứ làm người ta tắt tính năng.
         *
         * ⚠ Từ [P0-2] cờ này gác **cả tiếng bíp CUỐI** (xem [closeRecord]): [ĐO xe] nó mất tới **4955 ms** để
         * phát xong, còn lượt nối mở micro ~50 ms sau khi đóng ⇒ bíp cũ rơi vào cửa sổ đo nền của lượt sau.
         */
        beep: Boolean = true,
        /**
         * [P0-1a] Chỉ giải mã khi bộ ngắt câu **đã từng nghe thấy tiếng**.
         *
         * `true` cho mọi lượt NỐI: [ĐO xe] 189/300 lượt không nghe thấy gì mà vẫn chạy giải mã 1,3–2 s để mô
         * hình **bịa** ra `"ừ"`/`"ừm"` — chuỗi ấy lại mở lại micro. `false` cho lượt CHÍNH: người lái vừa bấm
         * mic, nên im lặng ở đó là câu trả lời cần nói ra (*"Không nghe rõ"*), không phải lượt bỏ qua âm thầm.
         */
        decodeOnlyIfSpeech: Boolean = false,
        /** Nhãn lượt cho chốt một-micro + nhật ký (`chinh` · `hoi-thoai` · `hoi-lai` · `xac-nhan`). */
        label: String = LABEL_MAIN,
        onPartial: (String) -> Unit,
    ): Heard {
        val kept = if (keepPcm) ShortArray(MAX_KEPT_SAMPLES) else EMPTY
        // ═══ [P0-1d/e] CHỐT MỘT-MICRO + CẦU CHÌ — trước cả `AudioRecord` ═══════════════════════════
        // Đặt ở đây vì đây là chỗ CUỐI trước phần cứng: mọi đường mở micro (kể cả đường ai đó thêm sau này) đều
        // đi qua, không phải nhớ gọi thêm gì. Xem KDoc [VoiceSingleFlight].
        when (val grant = VoiceSingleFlight.acquire(label)) {
            is VoiceSingleFlight.Grant.Busy -> {
                // ═══ [SOÁT 2026-09-18] BỘ NGHE "Hey Kachi" GIỮ MIC LIÊN TỤC ⇒ PHẢI XIN NÓ NHƯỜNG ═════════
                // Bộ nghe wake không phải một phiên: nó giữ chốt **suốt thời gian màn sáng**. Không có bước này
                // thì mọi lối vào phiên lệnh (nút mic · ô thanh nút · phím vô-lăng · cầu kiểm thử) chỉ nhận
                // `Busy("wake")` — tức **bật Hey Kachi làm chết nút mic**, một tính năng mặc định-TẮT giết tính
                // năng chính. Chờ có **trần cứng** ([VoiceMicPreempt], chia thành nhịp nhỏ): bộ nghe hỏi cờ
                // nhường mỗi khung (~100 ms) rồi nhả trong một khung + `stop/release`, nên trần này rộng gấp
                // nhiều lần thời gian thật cần. Hết trần thì cư xử y như trước (một dòng nhật ký rồi rút) —
                // KHÔNG có đường chờ vô hạn nào ở đây. Lượt bị chắn bởi một phiên khác thì **không** chờ: chốt
                // một-lượt sinh ra để từ chối NGAY (4 mic trong 300 ms), giữ nguyên hành vi ấy.
                val preempted = VoiceSingleFlight.isWakeLabel(grant.holder) && VoiceMicPreempt.preempt(label)
                if (!preempted) {
                    Log.w(TAG, "chắn lượt '$label': micro đang thuộc lượt '${grant.holder}' — bỏ qua")
                    return Heard("", kept, 0)
                }
            }
            is VoiceSingleFlight.Grant.Fused -> {
                Log.w(
                    TAG,
                    "CẦU CHÌ: đã ${grant.opens} lượt mở micro trong 60 s (trần " +
                        "${VoiceSingleFlight.MAX_OPENS_PER_MINUTE}) — từ chối lượt '$label'",
                )
                return Heard("", kept, 0)
            }
            VoiceSingleFlight.Grant.Ok -> Unit
        }
        try {
            return listenGranted(rec, maxMs, cancelled, keepPcm, endpointer, beep, decodeOnlyIfSpeech, kept, onPartial)
        } finally {
            VoiceSingleFlight.release()
        }
    }

    /** Thân thật của một lượt nghe, **sau khi** đã cầm chốt micro. Tách ra để `finally` nhả chốt không lẫn vào. */
    @Suppress("ReturnCount", "LongParameterList", "LongMethod", "CyclomaticComplexMethod")
    private fun listenGranted(
        rec: VoiceRecognizer,
        maxMs: Long,
        cancelled: () -> Boolean,
        keepPcm: Boolean,
        endpointer: VoiceEndpointer?,
        beep: Boolean,
        decodeOnlyIfSpeech: Boolean,
        kept: ShortArray,
        onPartial: (String) -> Unit,
    ): Heard {
        var keptN = 0
        val tOpen = System.currentTimeMillis()
        val opened = openRecord() ?: return Heard("", kept, keptN)
        val record = opened.record
        val focus = VoiceAudioFocus.request(ctx)
        // Bộ ngắt câu của lượt này: Silero VAD nếu dựng được, lùi về bộ RMS (xem [VoiceTurnEndpoint]). Dựng SAU
        // khi micro đã mở: nó tốn vài chục–vài trăm ms nạp ONNX, và trả cái giá ấy trước khi biết có mở được
        // micro hay không là trả cho một lượt có thể không bao giờ chạy.
        val ep = VoiceTurnEndpoint.open(ctx, endpointer)
        try {
            runCatching { record.startRecording() }.onFailure {
                Log.w(TAG, "startRecording hỏng", it); return Heard("", kept, keptN)
            }
            if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                Log.w(TAG, "micro không vào được trạng thái ghi — ROM từ chối?")
                return Heard("", kept, keptN)
            }
            Log.i(VoiceEngine.TIMING_TAG, "mic mở sau ${System.currentTimeMillis() - tOpen} ms")
            // [P0-2] Bíp **SAU** cửa sổ đo nền của đường lùi, không phải trước — lý do + số đo ở KDoc
            // [VoiceTurnEndpoint.floorWindowOpen]. Đường VAD không có cửa sổ ấy nên nó bíp ngay như cũ.
            // ⚠ 1.70: [tone] trả về NGAY (luồng riêng, xem [VoiceChime]) — [ĐO xe 2026-09-17] bản cũ chặn 3,0 s
            // ở đúng dòng này và làm rơi 3 s tiếng đầu của MỌI lượt chính.
            var beepPending = beep
            if (beepPending && !ep.floorWindowOpen()) { tone(ToneGenerator.TONE_PROP_BEEP, TONE_START_MS); beepPending = false }
            val buf = ShortArray(CHUNK_SAMPLES)
            val tListen = System.currentTimeMillis()
            val deadline = tListen + maxMs
            var lastPartial = ""
            // [ĐO bug voice 2026-09-15] Mức tín hiệu micro — chốt "câm/không nghe được" TRONG MỘT lượt nói: đỉnh
            // gần 0 ⇒ mic câm; đỉnh kịch 32767 liên tục ⇒ méo/clip; đỉnh vừa mà sherpa ra rỗng ⇒ định dạng.
            var peak = 0
            var sumSq = 0.0
            var samples = 0L
            // Số mẫu đã đọc được trong cửa sổ — mốc để đổi mẫu ↔ ms và là trần trên của phép cắt đuôi.
            // Tách khỏi `samples` (Long, dùng cho phép đo mức) vì phép cắt làm việc trên chỉ số mảng (Int).
            var fed = 0
            var ended = false
            while (!cancelled() && System.currentTimeMillis() < deadline) {
                val n = record.read(buf, 0, buf.size)
                if (n <= 0) {
                    // `ERROR_INVALID_OPERATION`/`ERROR_DEAD_OBJECT`: micro bị một app khác giành mất giữa chừng.
                    // Trả phần đã nghe thay vì quay vòng bận — một vòng lặp nóng trên đầu xe là quạt kêu và pin tụt.
                    if (n < 0) { Log.w(TAG, "đọc micro trả $n — dừng phiên"); break }
                    continue
                }
                var chunkSq = 0.0
                for (i in 0 until n) {
                    val a = kotlin.math.abs(buf[i].toInt())
                    if (a > peak) peak = a
                    chunkSq += a.toDouble() * a
                }
                sumSq += chunkSq
                samples += n
                fed += n
                // Chép TRƯỚC khi giải mã: `accept` có thể chốt câu và thoát ngay ở dòng dưới.
                if (keepPcm && keptN < kept.size) {
                    val room = minOf(n, kept.size - keptN)
                    System.arraycopy(buf, 0, kept, keptN, room)
                    keptN += room
                }
                if (rec.accept(buf, n)) {
                    logLevel(peak, sumSq, samples)
                    return Heard(
                        rec.finalResult(ep.trimSamples(fed)), kept, keptN,   // [SOÁT 1.69 · P2] xem KDoc `finalResult`
                        speechMs = ep.speechEndMs() - maxOf(0, ep.speechStartMs()),
                        listenMs = System.currentTimeMillis() - tListen,
                        micSource = opened.source,
                    )
                }
                // Ngắt câu — đặt SAU `rec.accept` (khối đã vào bộ gom) và TRƯỚC `partial`: thoát ở đây thì
                // khúc tiếng đã đầy đủ, phép cắt + giải mã dưới kia làm việc trên đúng thứ vừa nói.
                val rmsChunk = kotlin.math.sqrt(chunkSq / n).toInt()
                val stop = ep.accept(buf, n, rmsChunk, n * 1000 / SAMPLE_RATE)
                // Cửa sổ đo nền (chỉ đường LÙI có) đã đóng ⇒ giờ bíp mới an toàn — xem [P0-2] ở trên.
                if (beepPending && !ep.floorWindowOpen()) {
                    tone(ToneGenerator.TONE_PROP_BEEP, TONE_START_MS)
                    beepPending = false
                }
                if (stop) {
                    Log.i(VoiceEngine.TIMING_TAG, "ngắt câu: ${ep.summary(fed)}")
                    ended = true
                    break
                }
                val p = rec.partial()
                if (p.isNotEmpty() && p != lastPartial) { lastPartial = p; onPartial(p) }
            }
            // Chạm trần mà đoạn tiếng còn đang mở ⇒ chốt nốt, nếu không "nói dài" bị coi là "không ai nói".
            if (!ended) { ep.flush(); Log.i(VoiceEngine.TIMING_TAG, "hết trần: ${ep.summary(fed)}") }
            val listenMs = System.currentTimeMillis() - tListen
            Log.i(VoiceEngine.TIMING_TAG, "nghe $listenMs ms")
            logLevel(peak, sumSq, samples)
            val speechStart = ep.speechStartMs()
            val speechEnd = ep.speechEndMs()
            // ═══ [P0-1a] KHÔNG GIẢI MÃ một lượt chưa bao giờ nghe thấy tiếng ═══════════════════════════
            // Đây là chỗ cắt ~200 lượt giải mã/12 phút và cắt luôn nhiên liệu của vòng lặp (xem KDoc tham số
            // `decodeOnlyIfSpeech`). Câu trả lời rỗng ⇒ chỗ gọi đóng phiên êm, không có gì để "hiểu".
            // ═══ 1.70 · lượt CHÍNH cũng bỏ — khi bộ ngắt câu là VAD ═════════════════════════════════════
            // [ĐO xe 2026-09-17] 7/10 lượt chính `vad doan=0` vẫn nạp 8,2 s im lặng vào mô hình: 4,3 s CPU mỗi
            // lượt để ra chữ BỊA (*"chúng ta xây"* · *"vâng giấc mơ"* · *"ừm"* · rỗng) rồi phiên đi hỏi lại về
            // một câu không ai nói. Chỗ gọi nói ra *"Không nghe rõ"* (chữ + giọng) — im lặng ở lượt chính vẫn
            // được **nói ra**, chỉ không còn được **giải mã**. Đường lùi RMS giữ hành vi cũ (nó không đủ tin để
            // kết luận "không có tiếng", xem KDoc [VoiceTurnEndpoint.sawSpeech]).
            if (VoiceSilenceGate.skipDecode(decodeOnlyIfSpeech, ep.route, ep.sawSpeech())) {
                Log.i(VoiceEngine.TIMING_TAG, "bỏ giải mã: lượt này không có tiếng nào (${ep.summary(fed)})")
                return Heard(
                    "", kept, keptN, endpointFired = ended, listenMs = listenMs, micSource = opened.source,
                )
            }
            // ═══ CẮT ĐUÔI (`head`) rồi mới giải mã — §6: đuôi im lặng kéo 22/25 xuống 6/25 ═══════════════
            // `fed` = số mẫu đã đọc được trong cửa sổ; `trim` = tới HẾT đoạn tiếng cuối. Đường LÙI trả nguyên
            // cửa sổ (xem KDoc [VoiceTurnEndpoint.trimSamples]) nên nhánh ấy giữ đúng hành vi 1.68.
            val trim = ep.trimSamples(fed)
            Log.i(
                VoiceEngine.TIMING_TAG,
                "cắt: tieng_bat_dau=${speechStart}ms tieng_dut=${speechEnd}ms " +
                    "con_lai=${VoiceVadTrim.samplesToMs(trim, SAMPLE_RATE)}ms " +
                    "(bo=${VoiceVadTrim.samplesToMs(fed - trim, SAMPLE_RATE)}ms · duong=${ep.route})",
            )
            val tDecode = System.currentTimeMillis()
            val text = rec.finalResult(trim)
            val decodeMs = System.currentTimeMillis() - tDecode
            Log.i(VoiceEngine.TIMING_TAG, "giải mã $decodeMs ms")
            return Heard(
                text, kept, keptN,
                endpointMs = VoiceVadTrim.samplesToMs(trim, SAMPLE_RATE),
                speechMs = if (speechStart >= 0) speechEnd - speechStart else 0,
                silenceMs = VoiceVadTrim.samplesToMs(fed - trim, SAMPLE_RATE),
                endpointFired = ended,
                listenMs = listenMs,
                decodeMs = decodeMs,
                micSource = opened.source,
            )
        } finally {
            ep.close()
            closeRecord(record, focus, tailBeep = beep)
        }
    }

    /**
     * ═══ V3 · R3 — ĐÓNG micro, và **đo từng bước** ════════════════════════════════════════════════════════
     *
     * ## Vì sao hàm này tồn tại riêng
     * [ĐO xe 2026-09-16] có một **lỗ 3,1 giây** lặp lại ở MỌI lượt, nằm đúng giữa mốc *"sherpa ra: …"* và mốc
     * *"lượt 1 (ngữ pháp) nghe được"* (4 lần đo: 28.065→31.163 · 43.853→46.951 · 18.511→21.626 · 53.268→56.378).
     * [SUY] đọc mã: khoảng đó **chỉ có** `finally` của [listen] — `stop` · `release` · tiếng bíp cuối ·
     * `abandonAudioFocus`. Bốn việc, và trước bản này không có cách nào biết cái nào.
     *
     * ## Hai việc bản này làm, và cái thứ hai KHÔNG phải một phỏng đoán
     *  1. **Đo từng bước** (`KachiVoiceTiming`) ⇒ lượt xe sau đọc một dòng là biết thủ phạm.
     *  2. **Đẩy tiếng bíp cuối + nhả tiêu điểm sang luồng nền.** Đây không phải đoán mò mà là một quan sát đúng
     *     về **phân công**: cả hai việc ấy không ai chờ kết quả — tiếng bíp là phản hồi cho tai, nhả tiêu điểm là
     *     phép lịch sự với app nhạc. Giữ chúng trên đường về của câu trả lời là bắt người lái chờ hai việc không
     *     liên quan tới câu họ vừa nói. `stop`/`release` thì **ở lại** đúng chỗ: chúng phải xong trước khi phiên
     *     sau mở `AudioRecord` thứ hai (hazard đã ghi ở KDoc `VoiceSession.cancel`).
     */
    private fun closeRecord(record: AudioRecord, focus: AudioFocusRequest?, tailBeep: Boolean) {
        val t0 = System.currentTimeMillis()
        runCatching { record.stop() }
        val tStop = System.currentTimeMillis()
        runCatching { record.release() }
        val tRelease = System.currentTimeMillis()
        Log.i(
            VoiceEngine.TIMING_TAG,
            "đóng mic: stop ${tStop - t0} ms · release ${tRelease - tStop} ms (bíp + nhả tiêu điểm chạy nền)",
        )
        Thread({
            val t1 = System.currentTimeMillis()
            // ═══ [P0-2] Tiếng bíp CUỐI chỉ còn ở lượt CHÍNH ═══════════════════════════════════════════
            // [ĐO xe 2026-09-16] luồng này mất tới **4955 ms** để phát xong một tiếng bíp 60 ms, trong khi lượt
            // nối mở micro chỉ ~50 ms sau khi đóng ⇒ tiếng bíp của lượt TRƯỚC rơi thẳng vào cửa sổ đo nền của
            // lượt SAU. Đó là nguồn nhiễm mà trần nền [VoiceEndpointer.FLOOR_CAP] chỉ *giới hạn hậu quả*; bịt
            // nguồn thì tốt hơn giới hạn hậu quả.
            // Vì sao gắn vào cùng cờ `beep` của đầu lượt: chúng mô tả **cùng một loại lượt**. Lượt chính (người
            // lái vừa bấm) cần cả hai mốc "tôi bắt đầu"/"tôi thôi nghe"; lượt nối thì micro chỉ *chưa đóng*, và
            // một tiếng bíp sau mỗi câu trả lời vốn đã là thứ làm người ta tắt tính năng (KDoc tham số `beep`).
            // 1.70: [tone] không chặn nữa ⇒ số "bíp … ms" ở dòng dưới đo lượt XẾP HÀNG, không đo lượt phát.
            if (tailBeep) tone(ToneGenerator.TONE_PROP_ACK, TONE_END_MS)
            val t2 = System.currentTimeMillis()
            VoiceAudioFocus.abandon(ctx, focus)
            Log.i(
                VoiceEngine.TIMING_TAG,
                "nền: bíp ${t2 - t1} ms · nhả tiêu điểm ${System.currentTimeMillis() - t2} ms" +
                    if (VoiceChime.disabled()) " · âm báo ĐÃ TẮT (cầu chì)" else "",
            )
        }, "KachiMicTail").apply { isDaemon = true }.start()
    }

    /**
     * Ghi mức tín hiệu của một lượt nghe (bug voice 2026-09-15) — đỉnh biên độ + RMS, cả hai theo thang 0..32767.
     * Một dòng, đọc được ngay trong logcat trên xe để phân biệt câm / clip / chất-lượng mà không cần lưu tệp.
     */
    private fun logLevel(peak: Int, sumSq: Double, samples: Long) {
        val rms = if (samples > 0) kotlin.math.sqrt(sumSq / samples).toInt() else 0
        Log.i(TAG, "mức micro: đỉnh $peak/32767 · rms $rms · $samples mẫu (${samples / 16}ms)")
    }

    /**
     * Dựng [AudioRecord]: thử `VOICE_RECOGNITION` rồi mới `MIC` — xem KDoc lớp, quyết định (1).
     *
     * Bộ đệm lấy **gấp đôi** mức tối thiểu của ROM: mức tối thiểu là ngưỡng *"không tràn nếu đọc đúng nhịp"*,
     * mà luồng nghe của ta còn phải chạy giải mã Kaldi giữa hai lượt đọc. Thiếu chỗ đệm ⇒ mất mẫu ⇒ mất chữ,
     * và mất kiểu đó không có lỗi nào báo.
     */
    private fun openRecord(): Opened? {
        val min = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        // 1.70 — đệm ≥ [MIN_BUFFER_MS] tiếng: [ĐO xe 2026-09-17] `min*2` = **2 560 byte = 80 ms** trên DL3, và
        // một lượt chặn 3 s (âm báo) trên luồng đọc làm rơi **toàn bộ** tiếng của khoảng đó, không lỗi nào báo.
        // Âm báo đã dời khỏi luồng đọc; đệm rộng là lớp thứ hai cho mọi lượt chặn chưa ai đo (GC, giải mã
        // partial, một app khác giành CPU).
        val floor = SAMPLE_RATE * MIN_BUFFER_MS / 1000 * 2
        val size = maxOf(if (min > 0) min * 2 else CHUNK_SAMPLES * 2 * 8, floor)
        // V3 · R1 — thứ tự lấy từ `:core` ([VoiceMicSource]) + lựa chọn của người dùng. Xem KDoc ở đó để biết
        // vì sao MIC đứng trước ([ĐO xe 2026-09-16]) và vì sao ép một nguồn vẫn còn đường lùi.
        val pref = runCatching { Prefs.voiceMicSource(ctx) }.getOrDefault(VoiceMicSource.PREF_AUTO)
        for (source in VoiceMicSource.order(pref)) {
            val r = runCatching {
                AudioRecord(source, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, size)
            }.getOrNull() ?: continue
            if (r.state == AudioRecord.STATE_INITIALIZED) {
                Log.i(TAG, "micro mở bằng nguồn ${VoiceMicSource.sourceName(source)} (đệm $size byte · pref=$pref)")
                return Opened(r, source)
            }
            Log.w(TAG, "nguồn $source dựng ra AudioRecord chưa khởi tạo — thử nguồn sau")
            runCatching { r.release() }
        }
        Log.w(TAG, "không mở được micro bằng nguồn nào")
        return null
    }

    /**
     * Micro đã mở + **nguồn thật sự thắng**.
     *
     * H2 cần con số ấy trong tệp JSON, và nó không suy lại được từ prefs: `pref` chỉ nói *"thử cái này trước"*,
     * còn vòng lùi ở trên có thể đã rơi sang nguồn thứ hai/thứ ba mà không có gì khác ghi lại. Đúng câu hỏi mà
     * một buổi so *"vì sao lượt này nghe rõ, lượt kia câm"* phải trả lời được.
     */
    private data class Opened(val record: AudioRecord, val source: Int)

    /**
     * H5 — bộ ngắt câu dựng theo **prefs của chiếc xe này**.
     *
     * `:core` phải thuần nên nó KHÔNG đọc prefs; hai con số đi vào bằng tham số dựng, đúng chỗ này. `runCatching`
     * + mặc định = hằng cũ: một lượt đọc prefs hỏng không được phép làm phiên nghe chạy **không** có bộ ngắt câu
     * (tức lùi về cái giá cố định 8 giây cho mọi câu — xem KDoc [VoiceEndpointer]).
     */
    private fun endpointerFromPrefs(): VoiceEndpointer = VoiceEndpointer(
        minSpeechMs = runCatching { Prefs.voiceEndpointMinSpeechMs(ctx) }
            .getOrDefault(VoiceEndpointer.MIN_SPEECH_MS),
        hangoverMs = runCatching { Prefs.voiceEndpointSilenceMs(ctx) }
            .getOrDefault(VoiceEndpointer.HANGOVER_MS),
        floorCap = runCatching { Prefs.voiceEndpointFloorCap(ctx) }
            .getOrDefault(VoiceEndpointer.FLOOR_CAP),
    )

    /**
     * Một tiếng báo ngắn.
     *
     * 1.70 — KHÔNG còn [ToneGenerator]: [ĐO xe 2026-09-17] `startTone` chặn **3,0 s** trên ROM DL3 (`status
     * -110`) ngay trên luồng đọc và không phát ra tiếng nào. Hai tiếng của [VoiceChime] là PCM dựng sẵn qua
     * `AudioTrack` thường, phát trên luồng riêng, có cầu chì. Hằng `TONE_PROP_*` chỉ còn là **nhãn** chọn tiếng
     * đầu/cuối; giữ chữ ký `(type, ms)` để hai chỗ gọi + bài canh không đổi. Trả về NGAY.
     */
    private fun tone(type: Int, @Suppress("UNUSED_PARAMETER") ms: Int) {
        if (type == ToneGenerator.TONE_PROP_BEEP) VoiceChime.start() else VoiceChime.end()
    }

    companion object {
        private const val TAG = "KachiVoiceMic"

        /**
         * Nhãn lượt cho [VoiceSingleFlight] — ASCII, cố ý không dịch: chúng chỉ đi vào logcat, và hai lượt đo
         * trên hai máy khác ngôn ngữ phải grep được bằng MỘT chuỗi (cùng luật `PermissionReport.logLine`).
         */
        // Một nguồn cho nhãn lượt do NGƯỜI mở: cầu chì [VoiceSingleFlight] miễn cầu-chì cho đúng nhãn này
        // (vá "seri ngu"), nên hai bên phải là CÙNG một chuỗi — trỏ thẳng về đó thay vì chép "chinh" lần hai.
        val LABEL_MAIN = VoiceSingleFlight.LABEL_COMMAND
        const val LABEL_FOLLOW_UP = "hoi-thoai"
        const val LABEL_CLARIFY = "hoi-lai"
        const val LABEL_CONFIRM = "xac-nhan"

        /** 16 kHz — cùng số với mô hình (`conf/mfcc.conf`) và với [VoiceRecognizer.SAMPLE_RATE]. */
        const val SAMPLE_RATE = 16_000

        /**
         * 200 ms mỗi khối: đủ lớn để không gọi `read` liên tục, đủ nhỏ để chữ partial hiện gần như tức thì.
         *
         * Công khai từ bản VAD: [VoiceWavProbe] nạp tệp WAV theo **đúng nhịp khối này** để phép đo giống phiên
         * thật tới cả cách Silero nhìn dòng audio — nạp một phát cả tệp là đo một đường mà micro không đi.
         */
        const val CHUNK_SAMPLES = SAMPLE_RATE / 5

        /**
         * Trần khúc tiếng giữ lại cho lượt giải mã thứ hai — **9 giây** (≈ 288 KB PCM16 @16 kHz).
         *
         * Dài hơn trần một phiên (`VoiceSession.MAX_LISTEN_MS` = 8 s) đúng một giây: trần phiên đếm theo đồng hồ
         * treo tường còn mảng này đếm theo mẫu, hai thứ không bao giờ khớp tuyệt đối. */
        const val MAX_KEPT_SAMPLES = SAMPLE_RATE * 9

        /** Không cấp phát gì khi chỗ gọi không cần tiếng (lượt nghe *"đồng ý/huỷ"*). */
        private val EMPTY = ShortArray(0)

        private const val TONE_START_MS = VoiceChime.START_MS
        private const val TONE_END_MS = VoiceChime.END_MS

        /** Sàn đệm `AudioRecord` — một giây tiếng (xem [openRecord]). */
        const val MIN_BUFFER_MS = 1_000
    }
}
