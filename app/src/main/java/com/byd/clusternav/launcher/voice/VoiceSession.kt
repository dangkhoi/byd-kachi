package com.byd.clusternav.launcher.voice

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.byd.clusternav.Prefs
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
    internal val ctx: Context,
    /** Danh sách hồ sơ tài xế đang có — ngữ pháp cần, và nó ĐỔI được giữa hai lần nói. */
    internal val profiles: () -> List<String>,
    /** Nhãn app → tên gói (danh sách động; CLAUDE.md §7 — không tên gói nào viết cứng). */
    internal val appsByLabel: () -> Map<String, String>,
    /**
     * Nhãn trong **sổ địa chỉ** của hồ sơ đang dùng (spec `kachi-voice-addresses.html` R6) — đi vào hotwords để
     * *"về nhà"* / *"đến công ty"* nghe ra được. Cũng ĐỔI được giữa hai lần nói (người dùng vừa thêm một mục),
     * nên là lambda chứ không phải một danh sách chụp sẵn — cùng lẽ [profiles].
     */
    internal val places: () -> List<String>,
    /** Dựng cầu sang các đường đã có. Nhận `say` + `confirm` của chính phiên này. */
    private val dispatcher: (
        say: (String) -> Unit,
        confirm: (String, () -> Unit, () -> Unit) -> Unit,
    ) -> VoiceDispatcher,
    /** Mở *Cài đặt › Hệ thống & quyền* — lối sửa khi thiếu quyền micro hoặc chưa tải mô hình. */
    internal val openPermissions: () -> Unit,
    /** Chạy một việc dài trên luồng NỀN — tách ra để đo/kiểm được, mặc định là một Thread. */
    internal val background: (() -> Unit) -> Unit = { block -> Thread(block, "KachiListen").start() },
    /** CLOSE-3 — lối vào có thể GIAO cho `:wake` (một mô hình cho cả máy); `null` = luôn in-process (phiên của chính `:wake`). */
    internal val entry: VoiceEntry? = null,
) {

    internal val ui = Handler(Looper.getMainLooper())
    internal val capture = VoiceCapture(ctx)

    /**
     * ═══ V1 pha NÓI · đường ra TIẾNG (spec `kachi-voice-feedback.html` R2) ═══════════════════════
     *
     * Dựng **ngay lúc dựng phiên**, không `by lazy`: máy đọc của hệ thống khởi tạo bất đồng bộ (vài trăm ms tới
     * vài giây trên đầu xe), nên dựng nó ở lần nói ĐẦU TIÊN nghĩa là đúng câu trả lời đầu tiên không ai nghe
     * thấy — mà đó lại là câu người ta dùng để kết luận *"tính năng này không chạy"*.
     *
     * Không có giọng Việt nào trên máy ⇒ [VoiceSpeakerRouter] tự lùi về im lặng; xem [speakLines].
     *
     * R4 (T9) — công tắc *"Ưu tiên giọng offline"* truyền vào dưới dạng **lambda**, không phải giá trị: nó đọc
     * lại prefs ở **mỗi câu** (`VoiceSpeakerRouter.probe`), nên người dùng vừa bật công tắc trong Cài đặt là câu
     * tiếp theo đã đi đường mới — không phải khởi động lại launcher. Cùng lẽ với `profiles`/`appsByLabel`.
     */
    internal val speaker: VoiceSpeaker =
        runCatching { VoiceSpeakerRouter(ctx, preferOffline = { Prefs.voicePreferOffline(ctx) }) }
            .onFailure { Log.w(TAG, "không dựng được đường ra tiếng — chỉ còn chữ", it) }
            .getOrDefault(SilentSpeaker)

    /**
     * R4 (T9) — công tắc *"Đọc phản hồi bằng giọng"*, mặc định BẬT.
     *
     * Đọc mỗi lần dùng (không chụp lúc dựng phiên): một phiên nghe sống tới hết chuyến, còn công tắc thì đổi
     * được giữa chừng. `runCatching` + mặc định `true` vì một tính năng phụ không được giết launcher, và vì
     * *"không đọc được prefs"* là ca hiếm mà hành vi đúng là **giữ nguyên mặc định**, không phải im lặng.
     */
    internal fun speakReplies(): Boolean =
        runCatching { Prefs.voiceSpeakReplies(ctx) }.getOrDefault(true)

    /** OQ4 — *"đọc to CÂU HỎI xác nhận"*, mặc định **TẮT** (owner 2026-09-16; lý do ở [Prefs.voiceAskAloud]). */
    internal fun askAloud(): Boolean = runCatching { Prefs.voiceAskAloud(ctx) }.getOrDefault(false)

    private val running = AtomicBoolean(false)
    internal val cancelled = AtomicBoolean(false)

    /** Micro có đang mở không — [cancel] đọc để biết ai chịu trách nhiệm đóng phiên (xem KDoc [cancel]). */
    private val capturing = AtomicBoolean(false)
    private val stopped = AtomicBoolean(false)   // [SOÁT 2.68 · P1] màn huỷ ⇒ chết VĨNH VIỄN — vì sao: KDoc [VoiceEntry]

    /**
     * Micro của phiên này có đang mở không — **chỉ đọc**, cho các lượt nghe NỐI ở `VoiceSessionTurns.kt`.
     *
     * Một hàm đọc thay vì mở [capturing] thành `internal`: cờ ấy chỉ được **ghi** ở đúng một chỗ ([whileCapturing])
     * và đó là tính chất giữ cho nó không kẹt ở `true`. Phơi cả ô nhớ ra là mở đường cho một chỗ ghi thứ hai.
     */
    internal fun micOpen(): Boolean = capturing.get()

    /**
     * Đang có một hộp **hỏi lại** mở, tức một lượt nghe thứ hai sắp/đang chạy (spec `kachi-voice-feedback.html` R3).
     *
     * ## Vì sao KHÔNG đủ nếu chỉ nhìn [capturing]
     * [confirm] chạy **đồng bộ** bên trong `d.execute(intents)`, còn lượt nghe của nó bắt đầu trên luồng nền vài
     * ms sau đó — nên ở đúng khoảnh khắc [speakLines] chạy, [capturing] **vẫn còn `false`** dù micro sắp mở.
     * Đọc vào khe ấy là Kachi nói *"Đã mở khoá cửa?"* thẳng vào chính cái micro nó vừa mở để nghe *"đồng ý"*.
     */
    internal val confirmOpen = AtomicBoolean(false)

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

    @Volatile internal var overlay: VoiceOverlay? = null

    /** Câu trả lời cho hộp xác nhận đang mở, `null` khi không có hộp nào. Chỉ đụng trên luồng vẽ.
     * `internal`: `confirm`/`answerConfirm` là hàm mở rộng ở `VoiceSessionTurns.kt` (tách theo VAI, trần 500). */
    internal var pendingConfirm: (Boolean) -> Unit = {}

    /** V3 · R8 — lượt hỏi lại thứ mấy trong phiên này; về 0 mỗi khi một câu được hiểu. Xem `VoiceSessionTurns.kt`. */
    internal var clarifyRound = 0

    /** V3 · R9 — đã nối bao nhiêu lượt hội thoại trong phiên này (trần [MAX_FOLLOW_UPS]). */
    internal var followUps = 0

    /** H2 — mốc + phần mô tả của lượt nói đang ghi; hai hàm dùng chúng ở `VoiceSessionTurns.kt`. `null` = tắt. */
    internal var utteranceStamp: String? = null
    internal var utteranceMeta: VoiceUtteranceLog.Meta? = null

    /** 1.70 — quyết định của bộ phân tích cho lượt `execute` gần nhất (một chuỗi, cùng chuỗi in ra logcat). */
    internal var lastDecision: String = ""

    /**
     * ═══ B1 (1.70) — PHA hiện tại của phiên (nguồn sự thật thay việc suy từ chùm 6 cờ) ══════════════════════
     * Máy chuyển ở `:core` ([VoiceTurnMachine], kiểm off-car). Cờ cũ (`running`/`capturing`/…) GIỮ NGUYÊN (nghĩa
     * cơ chế thread/Android, đã có test); pha là **lớp bất biến** đứng trên, đặc biệt cổng
     * [VoiceTurnMachine.canOpenMic] (chỉ LISTENING mở mic — chống vòng lặp hoang OQ5). Chuyển qua `go()`.
     */
    internal val phase = java.util.concurrent.atomic.AtomicReference(VoiceTurnPhase.IDLE)
    // `go(to)` — cổng chuyển pha, là hàm mở rộng ở `VoiceSessionTurns.kt` (tách theo VAI, trần 500 dòng).

    /**
     * Bắt đầu nghe. Gọi từ luồng vẽ. Đang có phiên ⇒ **không làm gì** (xem KDoc lớp).
     *
     * CLOSE-3: "Hey Kachi" BẬT ⇒ giao lối vào cho `:wake` ([VoiceEntry.tryWake] — KHÔNG dựng recognizer ở đây);
     * `:wake` không ack trong hạn ⇒ [VoiceEntry] gọi lại với `local = true` để mở in-process như cũ (V3 R4).
     */
    fun start(local: Boolean = false) {
        if (stopped.get()) { Log.i(TAG, "màn chính đã huỷ — bỏ lượt mở phiên (kể cả đường lùi đã hẹn của `:wake`)"); return }
        if (running.get()) { Log.i(TAG, "đã có một phiên nghe đang chạy — không giao `:wake`"); return }
        if (!local && entry?.tryWake { start(local = true) } == true) return
        if (!running.compareAndSet(false, true)) {
            Log.i(TAG, "đã có một phiên nghe đang chạy — bỏ qua")
            return
        }
        cancelled.set(false)
        // ⚠ [SOÁT Pass 1 · P1 · 2026-09-16] Hai bộ đếm của V3 phải về 0 ở ĐÂY, không chỉ ở đường thành công.
        // [VoiceSession] sống theo TIẾN TRÌNH (một phiên cho cả launcher — xem KDoc lớp), còn [followUps] chỉ
        // tăng và [clarifyRound] chỉ về 0 khi một câu được hiểu. Thiếu hai dòng này thì: một phiên dùng hết 5
        // lượt hội thoại ⇒ **mọi phiên sau tới hết chuyến** không bao giờ giữ micro nữa; một phiên kết thúc ở
        // lượt hỏi thứ 2 ⇒ mọi phiên sau không bao giờ hỏi lại nữa. Cả hai tắt **im lặng**, không lỗi nào.
        clarifyRound = 0
        followUps = 0
        phase.set(VoiceTurnPhase.IDLE); go(VoiceTurnPhase.LISTENING)   // B1: IDLE → LISTENING
        val ov = VoiceOverlay(ctx) { cancel() }
        overlay = ov
        ov.show()
        ov.render(R.string.kachi_voice_preparing, "")
        // Warm máy đọc NGAY khi mở voice (nền): câu trả lời đầu bỏ được ~500ms spin-up :tts (owner 2026-09-24).
        background { runCatching { speaker.warm() } }
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
        stopped.set(true)           // [SOÁT 2.68 · P1] khoá mọi `start()` về sau — xem KDoc [stopped]
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
        val tStart = System.currentTimeMillis()
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
                // 1.70 [ĐO xe 2026-09-17] mốc "bấm phím → micro mở" từng mất **1,5 s** ở lượt đầu; in ra để lượt
                // xe sau biết phần nào (nhãn app · dựng recognizer · tấm chữ) ăn thời gian đó.
                Log.i(VoiceEngine.TIMING_TAG, "sẵn sàng nghe sau ${System.currentTimeMillis() - tStart} ms kể từ lúc bấm")
                val heard = whileCapturing {
                    capture.listen(it, MAX_LISTEN_MS, cancelled::get, keepPcm = true,
                        onLevel = { rms -> post { if (!stale(my)) overlay?.level(rms) } }) { partial ->
                        post { if (!stale(my)) overlay?.render(R.string.kachi_voice_listening, partial) }
                    }
                }
                if (cancelled.get() || stale(my)) { closeIfMine(my); return }
                post { if (!stale(my)) overlay?.setPhase(false) }   // R3: hết nghe → waveform đứng yên (đang hiểu)
                Log.i(TAG, "lượt 1 (ngữ pháp) nghe được: \"${heard.text}\"")
                // LƯỢT 2 — chỉ chạy khi lượt 1 có cụm MỞ TỪ VỰNG; xem KDoc [VoiceFreeTail] và [VoiceOpenVocab].
                val sentence = VoiceFreeTail.decode(ctx, heard)
                // H2 — ghi tiếng + số đo NGAY, trước mọi đường thoát dưới đây: ca *"nghe ra rỗng"* chính là ca
                // đáng nghe lại nhất, và nó thoát ở dòng sau. Ghi chạy trên luồng nền (xem [VoiceUtteranceLog]).
                logHeard(it, heard, sentence)
                if (cancelled.get() || stale(my)) { closeIfMine(my); return }
                if (sentence.isBlank()) {
                    // 1.70 — im lặng ở lượt chính được NÓI RA (chữ + giọng), không chỉ hiện chữ: [ĐO xe
                    // 2026-09-17] owner không nhìn màn khi lái, và một phiên kết thúc câm là *"không làm được gì"*.
                    Log.i(TAG, "quyết định: \"\" ⇒ không nghe thấy tiếng nào (không giải mã)")
                    fail(my, R.string.kachi_voice_nothing_heard, openSettingsAction = false)
                    post { if (!stale(my)) speakLines(listOf(ctx.getString(R.string.kachi_voice_nothing_heard))) }
                    return
                }
                post { if (!stale(my)) execute(sentence) }
            }
        } catch (t: Throwable) {
            // Một launcher KHÔNG được chết vì tính năng phụ: mã native của Kaldi có thể ném `Error`.
            Log.e(TAG, "phiên nghe hỏng", t)
            fail(my, R.string.kachi_voice_engine_failed, openSettingsAction = false)
        }
    }

    /** Việc nền này có còn thuộc phiên đang chạy không — xem KDoc [generation]. */
    internal fun stale(my: Int): Boolean = my != generation.get()

    /** Số thế hệ hiện hành — cho `VoiceSessionTurns.confirm` (extension file) lấy `my` cho lượt nghe xác nhận. */
    internal fun generationNow(): Int = generation.get()

    internal fun closeIfMine(my: Int) { if (!stale(my)) close() }

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
    internal fun execute(heard: String) {
        val my = generation.get()
        // B1: một lượt nghe vừa xong ⇒ LISTENING → DECODING → EXECUTING. (Chuyển không hợp lệ chỉ log, không phá
        // luồng — pha là lớp bất biến/chẩn đoán, cờ cũ vẫn giữ nghĩa cơ chế.)
        go(VoiceTurnPhase.DECODING); go(VoiceTurnPhase.EXECUTING)
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
                    // Dòng về muộn (tra mạng): đọc riêng, và khi đọc XONG thì nán [LINGER_MS] rồi đóng — không
                    // để tấm chữ tắt cụt giữa câu trả lời thật (cùng lỗi owner báo 2026-09-17, nhánh mạng).
                    if (flushed) speakLines(listOf(line)) { post { if (!stale(my)) scheduleClose(LINGER_MS) } }
                    else batch.add(line)
                }
            },
            { question, onYes, onNo ->
                if (stale(my) || overlay == null) onNo() else confirm(question, onYes, onNo)
            },
        )
        val intents = d.preview(heard)
        // 1.70 — MỘT dòng quyết định mỗi lượt: [ĐO xe 2026-09-17] log xe có *"nghe được: …"* mà không có dòng nào
        // nói bộ phân tích đã hiểu ra gì ⇒ không tra được vì sao *"chỉnh lại hai mươi lăm độ nhiệt độ"* không
        // làm gì. Cùng chuỗi đi vào nhật ký lượt nói (`decision`, xem [logDone]).
        lastDecision = VoiceDecision.describe(intents)
        Log.i(TAG, "quyết định: \"$heard\" ⇒ $lastDecision")
        overlay?.render(R.string.kachi_voice_heard, heard)
        // ═══ V3 · R8 — CẢ CÂU không hiểu ⇒ HỎI LẠI, không đóng phiên ═════════════════════════════
        // Đặt TRƯỚC `d.execute`: một câu chỉ có [VoiceIntent.Unknown] thì không có gì để thi hành, và để nó
        // chạy qua đường thường là để `VoiceReply.unknown` phát ra một dòng *"không hiểu"* rồi phiên chết —
        // đúng chỗ người lái phải bấm lại và nói lại **cả câu** (xem KDoc [VoiceClarify]).
        clarifyAsk(intents)?.let { ask -> askAgain(ask, my); return }
        // ⚠ [SOÁT Pass 1 · P2 · 2026-09-16] Hết trần hỏi ⇒ **bỏ cuộc lịch sự**, không rơi về câu *"không hiểu"*
        // thường. [VoiceClarify.ask] trả `null` ở hai ca khác hẳn nhau (không nên hỏi · đã hỏi đủ 2 lượt) và tới
        // bản này cả hai rơi vào cùng một chỗ ⇒ [VoiceClarify.giveUp] — câu nêu một ví dụ có thật, đúng thứ spec
        // R8 hứa — **chưa từng chạy** ở đường hết-trần. Xem `clarifyExhausted`.
        if (clarifyGaveUp(intents, my)) return
        d.execute(intents)
        // `say` chạy ĐỒNG BỘ bên trong `d.execute` cho mọi vế không phải tra mạng (cả hai đều trên luồng vẽ, và
        // `post` chạy thẳng khi đã ở luồng vẽ) ⇒ tới đây [batch] đã đủ. Mở cổng cho các dòng về muộn.
        flushed = true
        // Có vế nào còn **đang tra mạng** không (dòng tạm `…`) — câu trả lời thật về sau tới 20 s nữa. Không mở
        // hội thoại ở ca đó: micro sẽ đóng trước khi người lái biết việc xong hay hỏng (owner D1 nói về lệnh đã
        // xong, không phải lệnh đang chạy).
        val pending = batch.any { VoiceFeedbackPhrase.isInterim(it) }
        val endSession = intents.any { it is VoiceIntent.EndSession }   // Req2: câu kết thúc ⇒ không mở hội thoại nối.
        // Lưới an toàn = ước theo ĐỘ DÀI CÂU (không hằng cố định); mốc ĐỌC XONG (onReplyDone) mới nán. Xem VoiceSpeakBudget.
        scheduleClose(VoiceSpeakBudget.estimateMs(batch, SPEAK_SAFETY_MS))
        logDone(intents, batch)
        clarifyRound = 0
        VoiceChime.success()   // R1 voice-ux: earcon "đã hiểu/xong"
        speakLines(batch) { post { onReplyDone(my, pending, endSession) } }
    }

    // `speakLines(...)` (gom N dòng → đọc MỘT câu; ba cổng + luôn gọi `onDone`) và `confirm`/`answerConfirm`/
    // `askAloudThenListen` (hỏi lại trước khi bắn) là hàm mở rộng ở `VoiceSessionTurns.kt` (tách theo VAI, trần 500).

    /**
     * Chạy [block] với cờ [capturing] BẬT — micro đang mở thì [cancel] biết là có vòng nghe sẽ tự đóng phiên.
     *
     * `finally` chứ không phải hai dòng quanh lời gọi: vòng nghe có đường thoát bằng ngoại lệ (mã native của
     * Kaldi ném `Error`), mà một cờ kẹt ở `true` sẽ làm mọi lần huỷ sau đó không đóng được tấm chữ nữa.
     */
    internal fun <T> whileCapturing(block: () -> T): T {
        ui.removeCallbacks(closeTask); capturing.set(true)   // [FIX 2026-09-25] mở mic ⇒ huỷ hẹn-đóng cũ (chống overlay biến mất giữa lượt nghe + taskbar trồi)
        return try { block() } finally { capturing.set(false) }
    }

    // ── tiện ích ─────────────────────────────────────────────────────────────────────────────────

    // `fail(...)` — báo lỗi phiên, hàm mở rộng ở `VoiceSessionTurns.kt` (tách theo VAI, trần 500 dòng).
    internal fun scheduleClose(delayMs: Long) {
        ui.removeCallbacks(closeTask)
        ui.postDelayed(closeTask, delayMs)
    }

    private val closeTask = Runnable { close() }

    // Kiểu trả về khai TƯỜNG MINH: `closeTask` gọi `close()` còn `close()` đọc `closeTask` ⇒ thân-biểu-thức làm
    // bộ suy kiểu đi vòng tròn ("recursive problem"). Một chữ `Unit` rẻ hơn tách đôi một hàm 5 dòng.
    internal fun close(): Unit = post {
        ui.removeCallbacks(closeTask)
        go(VoiceTurnPhase.CLOSING)   // B1: từ pha bất kỳ → CLOSING
        pendingConfirm = {}
        // Hộp hỏi lại (nếu có) đã hết đời cùng tấm chữ ⇒ mở lại cổng đọc cho phiên sau. KHÔNG cắt câu đang đọc ở
        // đây: tấm chữ tự biến sau [LINGER_MS] = 2,5 s, còn một câu ~12 từ đọc mất ~3–4 s — cắt là cụt đúng vế
        // cuối ("…, 2 việc khác đã xong"), tức cụt đúng phần người lái cần.
        confirmOpen.set(false)
        overlay?.dismiss()
        overlay = null
        running.set(false)
        go(VoiceTurnPhase.IDLE)   // B1: CLOSING → IDLE (phiên đã đóng hẳn)
    }

    internal fun post(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else ui.post(block)
    }

    internal companion object {
        const val TAG = "KachiVoiceSession"

        /**
         * TRẦN CỨNG cho một phiên nghe.
         *
         * Vosk tự chốt câu sớm hơn nhiều trong ca thường (bảng ngắt câu của mô hình: 0,5 / 1,0 / 2,0 giây im
         * lặng). Trần này là cho ca **không** thường: micro bị nhiễu liên tục, người lái bấm nhầm rồi quên, một
         * app khác giành micro. Không có trần thì một trong ba ca đó giữ micro mở tới hết chuyến.
         */
        const val MAX_LISTEN_MS = 8_000L

        /**
         * Lượt nghe câu trả lời có/không — ngắn, vì câu trả lời chỉ dài một hai từ.
         *
         * V3 · R2 hạ **5 s → 4 s**: từ 1.66 lượt này có bộ ngắt câu ([VoiceEndpointer]) nên nó tự dừng khi người
         * ta nói xong; con số ở đây trở lại đúng vai **trần an toàn** (cabin ồn liên tục), và trần thì đặt sát
         * hơn được. [ĐO xe 2026-09-16] lượt xác nhận 09:10 chờ đủ 5,4 s cho một tiếng *"ừ"*.
         */
        // 1.70: 4 s → 5 s. Từ khi có Silero VAD, lượt này tự dừng khi người ta nói xong; con số chỉ là trần an
        // toàn, và [ĐO xe 2026-09-17] trần 4 s ở lượt hỏi-lại đã cắt cụt một câu đang nói.
        const val CONFIRM_LISTEN_MS = 5_000L

        /**
         * OQ4 — trần chờ *"đã đọc xong câu hỏi"* trước khi mở micro. **6 giây, owner chốt 2026-09-16.**
         *
         * Đây là trần **AN TOÀN**, không phải một phép đo thời lượng câu: hết hạn thì vẫn mở micro (không huỷ
         * cổng), nên đặt hụt chỉ làm Kachi nghe nốt phần đuôi câu hỏi của chính nó — đặt thừa thì người lái ngồi
         * chờ một micro đã sẵn sàng.
         *
         * ## Vì sao 6 s chứ không 4 s (đổi ở lượt soát 2026-09-16)
         * [ĐO host] Piper đọc câu 11 từ ra **2,11 s** audio ⇒ [SUY] câu xác nhận dài nhất của
         * [VoiceReply.confirmQuestion] (~20 từ, gồm dòng lý do) rơi vào **~3,8 s** — 4 s **không còn dư** chút
         * nào, mà hai thứ chưa ai đo đều đẩy nó lên: CPU đầu xe chậm hơn (lượt tổng hợp nằm TRƯỚC tiếng đầu
         * tiên), và máy đọc hệ thống có thể đọc chậm hơn Piper. Hai đầu của phép chọn **không đối xứng**: thừa
         * 2 s chỉ tốn 2 s chờ trong ca engine chết (hiếm); hụt thì Kachi nghe chính mình ở **mọi** câu hỏi dài.
         * **[CHƯA BIẾT]** thời lượng thật trên xe — phép chốt ở spec §7 **OQ7**.
         */
        const val ASK_ALOUD_CAP_MS = 6_000L

        /** Tấm chữ nán lại bao lâu sau khi đã trả lời (đủ đọc một dòng, không đủ để vướng mắt). */
        const val LINGER_MS = 1_200L   // owner 2026-09-24: nán NGẮN sau khi ĐỌC XONG (2500→1200), "đừng chờ lâu".

        /**
         * V3 · R8 — lượt nghe câu trả lời cho một câu **hỏi lại**. 4 giây: câu trả lời là một-hai từ (*"kính
         * lái"*, *"đèn đọc"*), và chờ lâu hơn chỉ làm người ta tưởng máy treo.
         */
        // 1.70: 4 s → [MAX_LISTEN_MS]. [ĐO xe 2026-09-17] owner trả lời câu hỏi lại bằng CẢ câu (*"chỉnh lại hai
        // mươi lăm độ nhiệt độ"*) và bị trần 4 s cắt giữa chừng (`hết trần … cua_so=4000`). VAD tự ngắt khi nói
        // xong nên trần dài không tốn thời gian ở ca thường; nó chỉ là trần an toàn như lượt chính.
        const val CLARIFY_LISTEN_MS = MAX_LISTEN_MS

        /**
         * V3 · R9 — trần số lượt nối trong MỘT phiên.
         *
         * Không có trần thì mỗi câu trả lời lại mở một lượt nghe mới, và một cabin ồn (hoặc một đài đang nói) đủ
         * để vòng ấy tự nuôi tới hết chuyến — đúng thứ mà KDoc lớp nêu là lý do KHÔNG làm wake word.
         */
        const val MAX_FOLLOW_UPS = 5

        /** Ca thiếu quyền/chưa tải mô hình nán lâu hơn: nó có một nút phải bấm được. */
        const val FAIL_LINGER_MS = 8_000L

        /**
         * 1.70 — lưới an toàn: tấm chữ sống qua CẢ lượt đọc kể cả khi máy đọc treo (onDone không về).
         *
         * [ĐO xe 2026-09-18] Từ bản này nó là **SÀN**, không phải toàn bộ lưới: `execute` cộng thêm theo độ dài
         * câu qua [VoiceSpeakBudget.estimateMs]. 10 s → **15 s** vì chính con số cũ là thứ cắt tấm chữ giữa câu
         * dưới tải CPU của xe (load 14) — lý do đầy đủ ở KDoc [VoiceSpeakBudget].
         *
         * Giá trị đọc từ `:core` để có **đúng một con số** (cùng luật với ba núm VAD): tên này ở lại vì nó là
         * cách cả spec lẫn nhật ký gọi lưới ấy.
         */
        const val SPEAK_SAFETY_MS = VoiceSpeakBudget.FLOOR_MS
        /** 1.70 — vế tra mạng: câu trả lời thật tới ~20 s (15 s nối + 5 s đọc), tấm chữ chờ tới đó. */
        const val NETWORK_WAIT_MS = 22_000L
    }
}
