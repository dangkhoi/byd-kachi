package com.byd.clusternav.launcher.voice

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.byd.clusternav.launcher.testbridge.TestBridgeStore
import java.util.concurrent.atomic.AtomicBoolean

/**
 * ═══ 2.91 VOICE-APP-NAMES · A4 — MỘT LƯỢT NGHE **DẠY** (không thi hành, không tấm chữ, không đọc) ════════════════
 *
 * Spec §4.4 (+ ràng buộc Pass 1). Phần NGHE dùng ĐÚNG đường của lệnh thật: [commandRecognizer] (cùng hotword · cùng
 * recognizer của tiến trình) + [VoiceCapture.listen] với CÙNG tham số của lượt chính (`MAX_LISTEN_MS` · `keepPcm` ·
 * `openTurn = true`, VAD cắt hai đầu) + [VoiceFreeTail.decode]. Phần VỎ thì KHÁC hẳn `VoiceSession.start()`:
 *  • **không `VoiceOverlay`** — tấm chữ `MATCH_PARENT` sẽ che chính hộp dạy và một chạm ra ngoài là huỷ lượt;
 *  • **không TTS** — loa không được nói câu lệnh-thất-bại giữa trang Cài đặt;
 *  • **không thi hành** — chữ nghe được về [Listener.onResult], hộp dạy tự chạy `TeachSample` + `TeachGuard`;
 *  • **không ghi nhật ký H2** trừ khi test-mode bật (OQ13 — H2 mặc định BẬT, vòng 30 mục: một buổi dạy 10 app sẽ đẩy
 *    sạch tiếng chuyến đi thật; test-mode thì CẦN đúng các khúc ấy để phát lại bằng `replay-car-log.py`).
 * Mọi đường lỗi (thiếu quyền mic · thiếu mô hình · engine hỏng · không nghe thấy · đang có phiên lệnh) trả MÃ LỖI.
 *
 * Micro chỉ qua [VoiceCapture] (chốt một-micro [VoiceSingleFlight], nhãn [LABEL]) — không `AudioRecord` thứ ba.
 * Một đối tượng cho một tiến trình: tiến trình chính (trang Cài đặt, khi mô hình ở đây) và `:wake` ([VoiceTeachRelay]).
 */
internal class VoiceTeachSession(
    private val ctx: Context,
    private val profiles: () -> List<String>,
    private val appsByLabel: () -> Map<String, String>,
    private val places: () -> List<String>,
    private val background: (() -> Unit) -> Unit = { block -> Thread(block, "KachiTeach").start() },
) {

    enum class State { LOADING_MODEL, LISTENING, DECODING }

    /** Mã lỗi ổn định (ASCII) — đi qua broadcast `:wake` → chính, UI dịch theo mã. */
    enum class Error { BUSY, NO_MIC, NO_MODEL, ENGINE, NOTHING_HEARD, CANCELLED }

    data class Result(val heard: String, val error: Error?)

    interface Listener {
        fun onState(state: State) {}
        fun onLevel(rms: Int) {}
        fun onResult(result: Result)
    }

    private val ui = Handler(Looper.getMainLooper())
    private val capture by lazy { VoiceCapture(ctx) }
    private val active = AtomicBoolean(false)
    private val cancelled = AtomicBoolean(false)

    fun isActive(): Boolean = active.get()

    /** Bắt đầu một lượt; `false` = đang có lượt dạy khác (lượt này không chạy, không gọi [Listener]). */
    fun start(listener: Listener): Boolean {
        if (!active.compareAndSet(false, true)) return false
        cancelled.set(false)
        background { run(listener) }
        return true
    }

    /** Huỷ lượt đang chạy (đóng hộp giữa chừng — cùng bài học `SettingsSectionsKeys.dispose`: không để phiên treo). */
    fun cancel() { cancelled.set(true) }

    @Suppress("ReturnCount")
    private fun run(listener: Listener) {
        fun done(r: Result) { active.set(false); ui.post { listener.onResult(r) } }
        try {
            // Đang có phiên LỆNH (nút mic · phím · Hey Kachi) ⇒ từ chối, không giành micro của người đang lái nói.
            if (VoiceSession.anyRunning()) { done(Result("", Error.BUSY)); return }
            if (!capture.hasPermission()) { done(Result("", Error.NO_MIC)); return }
            if (!VoiceModelStore.isReady(ctx)) { done(Result("", Error.NO_MODEL)); return }
            if (!VoiceEngine.loaded()) ui.post { listener.onState(State.LOADING_MODEL) }
            val rec = commandRecognizer(ctx, profiles(), appsByLabel(), places())
                ?: run { done(Result("", Error.ENGINE)); return }
            rec.use {
                if (cancelled.get()) { done(Result("", Error.CANCELLED)); return }
                ui.post { listener.onState(State.LISTENING) }
                val heard = capture.listen(
                    it, VoiceSession.MAX_LISTEN_MS, cancelled::get, keepPcm = true,
                    label = LABEL,
                    onLevel = { rms -> ui.post { listener.onLevel(rms) } },
                    openTurn = true,
                    // 2.93 VOICE-OPEN-TURN-DYNVOCAB — CÙNG phép ghép vế sau với lượt chính (lười, chỉ khi có vế sau).
                    openVocab = { dynVocabOf(ctx, profiles(), appsByLabel(), places()) },
                ) { }
                if (cancelled.get()) { done(Result("", Error.CANCELLED)); return }
                ui.post { listener.onState(State.DECODING) }
                val sentence = VoiceFreeTail.decode(ctx, heard)
                logIfTestMode(it, heard, sentence)
                // Chỉ ĐẾM ra logcat — chữ người dùng nói đi về hộp dạy, không vào log mới của tệp này (R-nf3).
                Log.i(TAG, "lượt dạy xong: ${sentence.length} ký tự, ${heard.speechMs} ms tiếng")
                done(if (sentence.isBlank()) Result("", Error.NOTHING_HEARD) else Result(sentence, null))
            }
        } catch (t: Throwable) {
            // Một launcher KHÔNG được chết vì tính năng phụ: mã native của sherpa có thể ném `Error`.
            Log.e(TAG, "lượt dạy hỏng", t)
            done(Result("", Error.ENGINE))
        }
    }

    /** OQ13 — chỉ khi test-mode bật: ghi cùng định dạng H2 (cùng [utteranceMetaOf] với lượt chính). */
    private fun logIfTestMode(rec: VoiceRecognizer, heard: VoiceCapture.Heard, sentence: String) {
        if (!runCatching { TestBridgeStore.isOn(ctx) }.getOrDefault(false)) return
        runCatching { VoiceUtteranceLog.record(ctx, heard.pcm, heard.samples, utteranceMetaOf(ctx, rec, heard, sentence)) }
            .onFailure { Log.w(TAG, "không ghi được nhật ký lượt dạy", it) }
    }

    companion object {
        private const val TAG = "KachiVoiceTeach"

        /** Nhãn của chốt một-micro + nhật ký mic (cùng họ `chinh`/`hoi-thoai`/`hoi-lai`/`xac-nhan`). */
        const val LABEL = "day-ten"
    }
}
