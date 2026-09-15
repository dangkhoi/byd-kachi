package com.byd.clusternav.launcher.voice

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.byd.clusternav.R
import com.byd.clusternav.launcher.VoiceDispatcher
import java.util.concurrent.atomic.AtomicBoolean

/**
 * ═══ V1 pha NGHE · MỘT PHIÊN "BẤM ĐỂ NÓI" ════════════════════════════════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-voice-command.html` **R11**. Lớp này nối bốn thứ đã có: micro ([VoiceCapture]) → bộ
 * nhận dạng ([VoiceRecognizer]) → bộ phân tích (`:core`, qua [VoiceDispatcher]) → tấm chữ ([VoiceOverlay]).
 * Nó **không** biết câu lệnh nào có nghĩa gì — đúng như tầng chữ, mọi việc đó nằm ở `:core`.
 *
 * ## KHÔNG có wake word — có chủ ý, không phải thiếu
 * Nghe thường trực nghĩa là micro mở suốt chuyến. Kachi là **launcher**: nó sống trong mọi phút xe chạy, nên
 * một lỗi ở vòng nghe thường trực là một lỗi không bao giờ tự khỏi. Bấm-để-nói thì mỗi phiên có điểm bắt đầu,
 * có **trần cứng [MAX_LISTEN_MS]**, và có ba đường thoát (nghe xong · hết giờ · người dùng huỷ). Nút mic 328
 * trên vô-lăng của owner vẫn thuộc Kiki (CLAUDE.md §6 — không đảo thứ đang chạy tốt); ai muốn đổi thì tự gán
 * `Prefs.VK_TARGET_KACHI_VOICE` trong bộ gán phím.
 *
 * ## Cổng XÁC NHẬN: nói *"đồng ý"* hoặc bấm — nhưng KHÔNG BAO GIỜ tự đồng ý
 * `VoiceRisk.CONFIRM` (mở khoá · hạ hết kính · dừng chiếu cụm · đổi hồ sơ) đi qua [confirm]: tấm chữ hiện câu
 * hỏi, để một nút bấm được, **và** mở một lượt nghe ngắn ([CONFIRM_LISTEN_MS]) chỉ nhận đúng hai câu trả lời
 * ([VoiceLexicon.confirmAnswer]). Mặc định khi hết giờ / nghe không rõ / huỷ là **KHÔNG** — xem KDoc [confirm].
 *
 * ## Một phiên tại một thời điểm
 * [running] là chốt duy nhất. Ba lối vào (ô *Nói với xe* · pill mic · phím vô-lăng) có thể kích gần nhau; hai
 * `AudioRecord` cùng mở trên đầu xe cho ra hai luồng tranh nhau micro và hai tấm chữ chồng lên nhau.
 */
class VoiceSession(
    private val ctx: Context,
    /** Danh sách hồ sơ tài xế đang có — ngữ pháp cần, và nó ĐỔI được giữa hai lần nói. */
    private val profiles: () -> List<String>,
    /** Nhãn app → tên gói (danh sách động; CLAUDE.md §7 — không tên gói nào viết cứng). */
    private val appsByLabel: () -> Map<String, String>,
    /**
     * Nhãn trong **sổ địa chỉ** của hồ sơ đang dùng (spec `kachi-voice-addresses.html` R6) — đi vào hotwords để
     * *"về nhà"* / *"đến công ty"* nghe ra được. Cũng ĐỔI được giữa hai lần nói (người dùng vừa thêm một mục),
     * nên là lambda chứ không phải một danh sách chụp sẵn — cùng lẽ [profiles].
     */
    private val places: () -> List<String>,
    /** Dựng cầu sang các đường đã có. Nhận `say` + `confirm` của chính phiên này. */
    private val dispatcher: (
        say: (String) -> Unit,
        confirm: (String, () -> Unit, () -> Unit) -> Unit,
    ) -> VoiceDispatcher,
    /** Mở *Cài đặt › Hệ thống & quyền* — lối sửa khi thiếu quyền micro hoặc chưa tải mô hình. */
    private val openPermissions: () -> Unit,
    /** Chạy một việc dài trên luồng NỀN — tách ra để đo/kiểm được, mặc định là một Thread. */
    private val background: (() -> Unit) -> Unit = { block -> Thread(block, "KachiListen").start() },
) {

    private val ui = Handler(Looper.getMainLooper())
    private val capture = VoiceCapture(ctx)

    /**
     * ═══ V1 pha NÓI · đường ra TIẾNG (spec `kachi-voice-feedback.html` R2) ═══════════════════════
     *
     * Dựng **ngay lúc dựng phiên**, không `by lazy`: máy đọc của hệ thống khởi tạo bất đồng bộ (vài trăm ms tới
     * vài giây trên đầu xe), nên dựng nó ở lần nói ĐẦU TIÊN nghĩa là đúng câu trả lời đầu tiên không ai nghe
     * thấy — mà đó lại là câu người ta dùng để kết luận *"tính năng này không chạy"*.
     *
     * Không có giọng Việt nào trên máy ⇒ [VoiceSpeakerRouter] tự lùi về im lặng; xem [speakLines].
     */
    private val speaker: VoiceSpeaker = runCatching { VoiceSpeakerRouter(ctx) }
        .onFailure { Log.w(TAG, "không dựng được đường ra tiếng — chỉ còn chữ", it) }
        .getOrDefault(SilentSpeaker)

    private val running = AtomicBoolean(false)
    private val cancelled = AtomicBoolean(false)

    /** Micro có đang mở không — [cancel] đọc để biết ai chịu trách nhiệm đóng phiên (xem KDoc [cancel]). */
    private val capturing = AtomicBoolean(false)

    /**
     * Đang có một hộp **hỏi lại** mở, tức một lượt nghe thứ hai sắp/đang chạy (spec `kachi-voice-feedback.html` R3).
     *
     * ## Vì sao KHÔNG đủ nếu chỉ nhìn [capturing]
     * [confirm] chạy **đồng bộ** bên trong `d.execute(intents)`, còn lượt nghe của nó bắt đầu trên luồng nền vài
     * ms sau đó — nên ở đúng khoảnh khắc [speakLines] chạy, [capturing] **vẫn còn `false`** dù micro sắp mở.
     * Đọc vào khe ấy là Kachi nói *"Đã mở khoá cửa?"* thẳng vào chính cái micro nó vừa mở để nghe *"đồng ý"*.
     */
    private val confirmOpen = AtomicBoolean(false)

    /**
     * Số thứ tự phiên. Mọi việc chạy trên luồng nền mang theo số của phiên sinh ra nó và **im lặng rút lui** nếu
     * số ấy không còn là số hiện hành ([stale]).
     *
     * ## [SOÁT Pass 2 · P1] Vì sao cần, đúng từ lúc [cancel] được phép đóng phiên sớm
     * Trước đây chỉ có [running] và nó đủ, vì phiên chỉ kết thúc ở đúng một chỗ: chính luồng nền. Từ khi huỷ
     * đóng được phiên ngay (xem [cancel]) thì có một khe: huỷ → [running] nhả → người lái bấm mic lần nữa →
     * **phiên mới** dựng tấm chữ mới, trong khi luồng nền CŨ vẫn đang chạy và sắp gọi `close()`/`fail()`/
     * `render()`. Không có số thế hệ, luồng cũ sẽ đóng tấm chữ của phiên mới và nhả [running] của nó — tức bấm
     * mic ra một tấm chữ tự biến mất sau nửa giây, không dấu vết nào trong nhật ký.
     */
    private val generation = java.util.concurrent.atomic.AtomicInteger(0)

    @Volatile private var overlay: VoiceOverlay? = null

    /** Câu trả lời cho hộp xác nhận đang mở, `null` khi không có hộp nào. Chỉ đụng trên luồng vẽ. */
    private var pendingConfirm: (Boolean) -> Unit = {}

    /** Bắt đầu nghe. Gọi từ luồng vẽ. Đang có phiên ⇒ **không làm gì** (xem KDoc lớp). */
    fun start() {
        if (!running.compareAndSet(false, true)) {
            Log.i(TAG, "đã có một phiên nghe đang chạy — bỏ qua")
            return
        }
        cancelled.set(false)
        val ov = VoiceOverlay(ctx) { cancel() }
        overlay = ov
        ov.show()
        ov.render(R.string.kachi_voice_preparing, "")
        val my = generation.incrementAndGet()
        background { runSession(my) }
    }

    /**
     * Người dùng thoát (chạm ra ngoài / Back / phiên mới đè lên).
     *
     * ## [SOÁT Pass 2 · P2] Vì sao huỷ phải TỰ ĐÓNG khi không có vòng nghe nào đang chạy
     * Bản đầu chỉ đặt cờ [cancelled] và để vòng nghe tự thấy. Nhưng tấm chữ còn sống ở ba trạng thái **không có
     * vòng nghe nào**: đang báo thiếu quyền / chưa tải mô hình ([FAIL_LINGER_MS] = 8 s), và đang nán lại sau câu
     * trả lời ([LINGER_MS]). Ở ba trạng thái ấy, chạm ra ngoài **không làm gì** — trong khi tấm chữ vẫn ghi
     * *"Chạm ra ngoài hoặc bấm Back để huỷ"*, và [running] còn khoá nên bấm mic lần nữa cũng im. Một nút mic
     * bấm không ra gì trong 8 giây là đúng thứ người lái sẽ bấm lại lần thứ ba.
     *
     * Chỉ đóng ngay khi [capturing] tắt: micro còn mở thì phải để chính vòng nghe đóng, nếu không [running] nhả
     * sớm và phiên tiếp theo mở `AudioRecord` **thứ hai** trong lúc cái thứ nhất chưa buông.
     */
    fun cancel() {
        cancelled.set(true)
        // Người lái đã bỏ đi ⇒ câu xác nhận đang đọc dở **không còn nghĩa gì**. Cắt trước khi đụng tới tấm chữ:
        // đường ra tiếng không dính gì tới luồng vẽ, và để nó nói nốt là để loa mô tả một việc người ta vừa huỷ.
        runCatching { speaker.stop() }
        ui.post {
            // Hộp xác nhận đang mở thì huỷ **cũng là một câu trả lời**, và câu đó là KHÔNG.
            answerConfirm(false)
            if (!capturing.get()) close()
        }
    }

    /**
     * Màn chính huỷ ⇒ phiên phải chết theo **ngay**, không đợi trần 8 giây.
     *
     * [SOÁT Pass 2 · P1] [VoiceOverlay] là một cửa sổ `TYPE_APPLICATION_OVERLAY` — nó **không** chết cùng
     * activity (đúng họ với `DrawerController` mà `KachiHomeActivity.onDestroy` đã phải đóng tay, xem chú thích
     * ở đó). Không gọi hàm này thì sau khi màn chính chết vẫn còn: một cửa sổ phủ toàn màn ăn mọi cú chạm, một
     * `AudioRecord` đang mở, và một [VoiceDispatcher] trỏ vào activity đã huỷ.
     */
    fun stop() {
        cancelled.set(true)
        // Bỏ luôn số thế hệ: mọi việc nền còn treo của phiên này thành [stale] ⇒ không vẽ, không đóng, không thi
        // hành gì nữa. Đóng ngay ở đây được (khác [cancel]) vì không còn phiên nào để bàn giao — màn đang chết.
        generation.incrementAndGet()
        // Nhả hẳn đường ra tiếng — cùng họ với `AudioRecord` ở KDoc trên: một `TextToSpeech` không `shutdown`
        // giữ một kết nối dịch vụ sống lâu hơn cả màn hình đã chết, và một `AudioTrack` chưa `release` giữ luôn
        // tiêu điểm âm thanh ⇒ nhạc của cả xe kẹt ở mức nhỏ mà không ai biết tại sao.
        runCatching { speaker.shutdown() }
        ui.post { answerConfirm(false); close() }
    }

    // ── một phiên, chạy trên luồng nền ───────────────────────────────────────────────────────────

    @Suppress("ReturnCount")
    private fun runSession(my: Int) {
        try {
            if (!capture.hasPermission()) { fail(my, R.string.kachi_voice_no_mic, openSettingsAction = true); return }
            if (!VoiceModelStore.isReady(ctx)) { fail(my, R.string.kachi_voice_no_model, openSettingsAction = true); return }

            val labels = appsByLabel()
            val rec = VoiceRecognizer.open(ctx, profiles(), labels.keys.toList(), labels.values.toSet(), places())
            if (rec == null) { fail(my, R.string.kachi_voice_engine_failed, openSettingsAction = true); return }

            rec.use {
                // Huỷ trong lúc đang dựng bộ nhận dạng (vài trăm ms đầu) ⇒ **không mở micro nữa**. Thiếu dòng
                // này thì một cú chạm huỷ vẫn cho ra một tiếng bíp + một lượt mở micro rồi tắt ngay.
                if (cancelled.get() || stale(my)) { closeIfMine(my); return }
                post { if (!stale(my)) overlay?.render(R.string.kachi_voice_listening, "") }
                // `whileCapturing` KHÔNG nhận tham số ⇒ `it` bên trong vẫn là recognizer của `rec.use` (một
                // lambda không tham số không dựng `it` riêng, nên không che `it` của lambda ngoài).
                val heard = whileCapturing {
                    capture.listen(it, MAX_LISTEN_MS, cancelled::get, keepPcm = true) { partial ->
                        post { if (!stale(my)) overlay?.render(R.string.kachi_voice_listening, partial) }
                    }
                }
                if (cancelled.get() || stale(my)) { closeIfMine(my); return }
                Log.i(TAG, "lượt 1 (ngữ pháp) nghe được: \"${heard.text}\"")
                // LƯỢT 2 — chỉ chạy khi lượt 1 có cụm MỞ TỪ VỰNG; xem KDoc [freeTail] và [VoiceOpenVocab].
                val sentence = freeTail(heard)
                if (cancelled.get() || stale(my)) { closeIfMine(my); return }
                if (sentence.isBlank()) { fail(my, R.string.kachi_voice_nothing_heard, openSettingsAction = false); return }
                post { if (!stale(my)) execute(sentence) }
            }
        } catch (t: Throwable) {
            // Một launcher KHÔNG được chết vì tính năng phụ: mã native của Kaldi có thể ném `Error`.
            Log.e(TAG, "phiên nghe hỏng", t)
            fail(my, R.string.kachi_voice_engine_failed, openSettingsAction = false)
        }
    }

    /**
     * ═══ V1.1 · LƯỢT 2 — bộ giải mã **TỰ DO** trên đúng khúc tiếng vừa thu (R16) ══════════════════════════
     *
     * **CHẶN** ⇒ gọi trên luồng nền (nó đang ở trong `runSession`).
     *
     * ## Điều kiện chạy — hai vế, và bài canh đọc được cả hai từ mã
     * Chỉ chạy khi [VoiceOpenVocab.triggerOf] khác `null`, tức lượt 1 nghe ra *"&lt;cụm kích hoạt&gt; `[unk]`…"*.
     * Câu lệnh xe thường (*"bật đèn đọc"*) không có `[unk]` nào ⇒ **không tốn lượt giải mã nào**, và cũng không
     * có một chuỗi tự do kém chính xác nào len được vào đường điều khiển xe.
     *
     * Hỏng ở bất kỳ đâu (không dựng được bộ giải mã, không nghe ra đuôi) ⇒ **lùi về bản ngữ pháp** đã bỏ `[unk]`.
     * Người lái nhận được *"Phát nhạc"* thay vì *"Tìm bài «Diễm Xưa»"* — ít hơn, nhưng không sai.
     *
     * @return câu hoàn chỉnh để đưa xuống [VoiceDispatcher].
     */
    private fun freeTail(heard: VoiceCapture.Heard): String {
        val plain = VoiceOpenVocab.stripUnk(heard.text)
        val trigger = VoiceOpenVocab.triggerOf(heard.text) ?: return plain
        if (heard.samples <= 0) return plain
        val t0 = System.currentTimeMillis()
        val free = runCatching {
            VoiceRecognizer.openFree(ctx)?.use { it.decodeAll(heard.pcm, heard.samples) }
        }.onFailure { Log.w(TAG, "lượt 2 hỏng", it) }.getOrNull().orEmpty()
        val merged = VoiceOpenVocab.merge(heard.text, free)
        Log.i(
            TAG,
            "lượt 2 (tự do) sau cụm \"${trigger.joinToString(" ")}\" trong ${System.currentTimeMillis() - t0} ms: " +
                "\"$free\" ⇒ \"${merged.text}\"",
        )
        return merged.text.ifBlank { plain }
    }

    /** Việc nền này có còn thuộc phiên đang chạy không — xem KDoc [generation]. */
    private fun stale(my: Int): Boolean = my != generation.get()

    private fun closeIfMine(my: Int) { if (!stale(my)) close() }

    /**
     * Thi hành câu vừa nghe — trên luồng VẼ, vì [VoiceDispatcher] đụng tới view/hộp thoại.
     *
     * ## [SOÁT Pass 3 · P1] Vì sao cổng hỏi-lại phải có khoá THẾ HỆ ở đây
     * Tới 1.49 mọi lượt [confirm] đều xảy ra **đồng bộ** trong lời gọi này, tức chắc chắn còn trong phiên. V1.1
     * mở một đường mới: câu dẫn đường tới một app chỉ nhận toạ độ đi **tra cứu mạng** rồi mới hỏi lại — có thể
     * mất tới ~20 s (cửa mạng của dự án: 15 s nối + 5 s đọc). Trong khoảng ấy người lái hoàn toàn có thể đã huỷ phiên,
     * rời màn chính, hoặc **bấm nói lần nữa**. Không có khoá này thì lượt tra cứu cũ về muộn sẽ: đặt lại
     * `pendingConfirm` của phiên ĐANG chạy (câu trả lời "đồng ý" của người dùng rơi vào việc CŨ), vẽ câu hỏi cũ
     * đè lên tấm chữ mới, và **mở thêm một `AudioRecord` thứ hai** trong lúc micro của phiên mới còn đang mở —
     * đúng cái hazard đã ghi ở KDoc [cancel].
     *
     * Phiên đã qua ⇒ coi như **KHÔNG** (cùng mặc định với hết giờ / nghe không rõ, xem KDoc [confirm]).
     */
    private fun execute(heard: String) {
        val my = generation.get()
        // ─── V1 pha NÓI (spec `kachi-voice-feedback.html` R1) ────────────────────────────────────
        // Gom mọi dòng `say` của lượt này rồi đọc MỘT câu. Không gom thì một câu ghép ("đặt nhiệt độ 24 và gió
        // mức 3") cho hai lượt `say` ⇒ hai lượt đọc nối đuôi, mà máy đọc nào cũng QUEUE_FLUSH khi bị gọi lại ⇒
        // người lái chỉ nghe được nửa sau. Xem KDoc [VoiceFeedbackPhrase].
        val batch = ArrayList<String>()
        var flushed = false
        val d = dispatcher(
            { line ->
                post {
                    overlay?.render(R.string.kachi_voice_heard, line)
                    // Dòng về SAU khi đã đọc (đường tra cứu điểm đến mất tới ~20 s) thì đọc riêng — gom vào một
                    // mảng không ai đọc nữa là im lặng đúng chỗ câu trả lời thật sự tới.
                    if (stale(my)) return@post
                    if (flushed) speakLines(listOf(line)) else batch.add(line)
                }
            },
            { question, onYes, onNo ->
                if (stale(my) || overlay == null) onNo() else confirm(question, onYes, onNo)
            },
        )
        val intents = d.preview(heard)
        overlay?.render(R.string.kachi_voice_heard, heard)
        d.execute(intents)
        // `say` chạy ĐỒNG BỘ bên trong `d.execute` cho mọi vế không phải tra mạng (cả hai đều trên luồng vẽ, và
        // `post` chạy thẳng khi đã ở luồng vẽ) ⇒ tới đây [batch] đã đủ. Mở cổng cho các dòng về muộn.
        flushed = true
        speakLines(batch)
        // Tấm chữ ở lại [LINGER_MS] để đọc được câu trả lời rồi tự biến. Nếu một vế đang hỏi xác nhận thì
        // `confirm` đã dời hẹn giờ ra sau — xem [confirm].
        scheduleClose(LINGER_MS)
    }

    /**
     * Đọc thành tiếng câu xác nhận — **R1 + R3** của spec `kachi-voice-feedback.html`.
     *
     * ## Vì sao [capturing] là cổng, không phải một tuỳ chọn
     * Micro đang mở mà loa nói thì Kachi **nghe chính mình**: lượt nghe xác nhận (`listenForConfirm`) sẽ nhận
     * được câu vừa đọc làm đầu vào. Đó không phải chuyện thẩm mỹ — cổng *"đồng ý / huỷ"* nhận nhầm một lần là
     * một cánh cửa mở giữa bãi đỗ (xem KDoc [confirm]).
     *
     * Không có giọng nào trên máy ⇒ [VoiceSpeaker.speak] trả `false` và **không có gì xảy ra**: tấm chữ + âm báo
     * vẫn y như 1.63 (R2c — degrade, không ném).
     */
    private fun speakLines(lines: List<String>) {
        if (lines.isEmpty() || capturing.get() || confirmOpen.get()) return
        val sentence = VoiceFeedbackPhrase.merge(lines) ?: return
        runCatching { speaker.speak(sentence) }
            .onFailure { Log.w(TAG, "không đọc được câu xác nhận", it) }
    }

    /**
     * Hỏi lại trước khi bắn.
     *
     * ## Vì sao mặc định là KHÔNG, và vì sao chỉ nhận đúng hai câu
     * Cổng này tồn tại vì *"mở khoá cửa"* nghe nhầm một lần là xe mở khoá giữa bãi đỗ. Một lượt nghe ngắn chỉ
     * để bắt *"đồng ý"* / *"huỷ"* ([VoiceLexicon.confirmAnswer] — cố ý không nhận `ừ`/`vâng`, xem KDoc ở đó):
     * nghe được gì khác, hết giờ, hay người dùng bỏ đi ⇒ **[onNo]**. Im lặng không bao giờ được hiểu là đồng ý.
     *
     * Nút bấm vẫn còn nguyên cho người không muốn nói lần thứ hai — hai đường, một quyết định, và cả hai đều
     * gọi đúng một trong hai lambda đúng một lần ([answerConfirm] giữ điều đó).
     */
    private fun confirm(question: String, onYes: () -> Unit, onNo: () -> Unit) {
        val answered = AtomicBoolean(false)
        confirmOpen.set(true)
        pendingConfirm = { yes ->
            if (answered.compareAndSet(false, true)) {
                pendingConfirm = {}
                confirmOpen.set(false)
                if (yes) onYes() else onNo()
                scheduleClose(LINGER_MS)
            }
        }
        overlay?.render(
            R.string.kachi_voice_confirm_title,
            question,
            ctx.getString(R.string.kachi_voice_confirm_yes),
        ) { answerConfirm(true) }
        scheduleClose(CONFIRM_LISTEN_MS + LINGER_MS)
        val my = generation.get()
        background { listenForConfirm(answered, my) }
    }

    /** Lượt nghe thứ hai, **chỉ** để lấy một câu trả lời có/không. */
    private fun listenForConfirm(answered: AtomicBoolean, my: Int) {
        val heard = runCatching {
            val labels = appsByLabel()
            VoiceRecognizer.open(ctx, profiles(), labels.keys.toList(), labels.values.toSet())?.use {
                whileCapturing {
                    // `keepPcm = false`: lượt này chỉ bắt *"đồng ý"/"huỷ"* — không bao giờ cần lượt giải mã thứ hai.
                    capture.listen(it, CONFIRM_LISTEN_MS, { cancelled.get() || answered.get() || stale(my) }) { partial ->
                        post { if (!stale(my)) overlay?.render(R.string.kachi_voice_confirm_title, partial) }
                    }.text
                }
            }.orEmpty()
        }.onFailure { Log.w(TAG, "lượt nghe xác nhận hỏng", it) }.getOrDefault("")
        val answer = VoiceLexicon.confirmAnswer(heard)
        Log.i(TAG, "câu trả lời xác nhận: \"$heard\" ⇒ $answer")
        // `null` (không phải câu trả lời) cũng là KHÔNG — xem KDoc [confirm].
        post { if (!stale(my)) answerConfirm(answer == true) }
    }

    private fun answerConfirm(yes: Boolean) = pendingConfirm(yes)

    /**
     * Chạy [block] với cờ [capturing] BẬT — micro đang mở thì [cancel] biết là có vòng nghe sẽ tự đóng phiên.
     *
     * `finally` chứ không phải hai dòng quanh lời gọi: vòng nghe có đường thoát bằng ngoại lệ (mã native của
     * Kaldi ném `Error`), mà một cờ kẹt ở `true` sẽ làm mọi lần huỷ sau đó không đóng được tấm chữ nữa.
     */
    private fun <T> whileCapturing(block: () -> T): T {
        capturing.set(true)
        return try { block() } finally { capturing.set(false) }
    }

    // ── tiện ích ─────────────────────────────────────────────────────────────────────────────────

    private fun fail(my: Int, msgRes: Int, openSettingsAction: Boolean) = post {
        if (stale(my)) return@post
        overlay?.render(
            msgRes,
            "",
            if (openSettingsAction) ctx.getString(R.string.kachi_voice_open_settings) else null,
        ) { close(); openPermissions() }
        scheduleClose(if (openSettingsAction) FAIL_LINGER_MS else LINGER_MS)
    }

    private fun scheduleClose(delayMs: Long) {
        ui.removeCallbacks(closeTask)
        ui.postDelayed(closeTask, delayMs)
    }

    private val closeTask = Runnable { close() }

    // Kiểu trả về khai TƯỜNG MINH: `closeTask` gọi `close()` còn `close()` đọc `closeTask` ⇒ thân-biểu-thức làm
    // bộ suy kiểu đi vòng tròn ("recursive problem"). Một chữ `Unit` rẻ hơn tách đôi một hàm 5 dòng.
    private fun close(): Unit = post {
        ui.removeCallbacks(closeTask)
        pendingConfirm = {}
        // Hộp hỏi lại (nếu có) đã hết đời cùng tấm chữ ⇒ mở lại cổng đọc cho phiên sau. KHÔNG cắt câu đang đọc ở
        // đây: tấm chữ tự biến sau [LINGER_MS] = 2,5 s, còn một câu ~12 từ đọc mất ~3–4 s — cắt là cụt đúng vế
        // cuối ("…, 2 việc khác đã xong"), tức cụt đúng phần người lái cần.
        confirmOpen.set(false)
        overlay?.dismiss()
        overlay = null
        running.set(false)
    }

    private fun post(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else ui.post(block)
    }

    private companion object {
        const val TAG = "KachiVoiceSession"

        /**
         * TRẦN CỨNG cho một phiên nghe.
         *
         * Vosk tự chốt câu sớm hơn nhiều trong ca thường (bảng ngắt câu của mô hình: 0,5 / 1,0 / 2,0 giây im
         * lặng). Trần này là cho ca **không** thường: micro bị nhiễu liên tục, người lái bấm nhầm rồi quên, một
         * app khác giành micro. Không có trần thì một trong ba ca đó giữ micro mở tới hết chuyến.
         */
        const val MAX_LISTEN_MS = 8_000L

        /** Lượt nghe câu trả lời có/không — ngắn, vì câu trả lời chỉ dài một hai từ. */
        const val CONFIRM_LISTEN_MS = 5_000L

        /** Tấm chữ nán lại bao lâu sau khi đã trả lời (đủ đọc một dòng, không đủ để vướng mắt). */
        const val LINGER_MS = 2_500L

        /** Ca thiếu quyền/chưa tải mô hình nán lâu hơn: nó có một nút phải bấm được. */
        const val FAIL_LINGER_MS = 8_000L
    }
}
