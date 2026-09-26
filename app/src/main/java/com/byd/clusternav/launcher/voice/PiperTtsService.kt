package com.byd.clusternav.launcher.voice

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.util.Log
import com.byd.clusternav.launcher.perf.KachiMem

/**
 * ═══ #0 · PIPER SỐNG Ở TIẾN TRÌNH RIÊNG `:tts` — MỘT LẦN NỔ NATIVE KHÔNG ĐƯỢC GIẾT LAUNCHER ═══════════════════
 *
 * Doc gốc `docs/diagnostics/oncar-piper-crash-binding-2026-09-18.md`.
 *
 * ## Vì sao phải TÁCH TIẾN TRÌNH, không phải bắt lỗi cho tốt hơn
 * [ĐO tombstone xe 2026-09-18, Kachi 1.76]:
 * ```
 * F/libc: Fatal signal 11 (SIGSEGV), code 1 (SEGV_MAPERR) … tid (KachiSpeak), pid (com.byd.launcher)
 *   #21 com.k2fsa.sherpa.onnx.OfflineTts.generate   (native onnxruntime)
 *   #24 SherpaTtsSpeaker.synthesizeAndPlay+164
 * ```
 * Hệ quả **không** dừng ở "câu trả lời bị cụt": cả tiến trình launcher chết ⇒ `WIN DEATH KachiHome` ⇒
 * `NavAccessibilityService` unbind ⇒ lượt bind sau kẹt ở *"Binding"* ⇒ **phím gán trên vô-lăng chết**, và owner
 * phải force-stop + bật lại trợ năng bằng tay. Tức một lỗi của **tính năng phụ** (đọc câu trả lời) đang giết
 * **tính năng chính** (điều khiển bằng phím).
 *
 * Và nó **không vá được trong cùng tiến trình**: `SIGSEGV` là abort ở tầng native, `runCatching { Throwable }`
 * của [SherpaTtsSpeaker] không bao giờ thấy nó. Đường duy nhất còn lại là một **ranh giới tiến trình** — đúng
 * cái mà `android:process=":tts"` cho, miễn phí, do nền tảng gác.
 *
 * ## Hợp đồng (3 tin, cố ý ít nhất có thể)
 *  • **MSG_SPEAK** — `data = {`[KEY_ID]`:Int, `[KEY_TEXT]`:String}`, `msg.replyTo` = Messenger của launcher.
 *    Đọc xong ⇒ gửi **MSG_DONE** mang lại đúng [KEY_ID] ấy về `replyTo`.
 *  • **MSG_STOP** — cắt câu đang đọc ([SherpaTtsSpeaker.stop], idempotent).
 *  • không có tin *"bạn sẵn sàng chưa"*: launcher tự kiểm gói trên đĩa qua
 *    [SherpaTtsSpeaker.voiceFilesPresent] — hỏi qua IPC là bind cả tiến trình `:tts` chỉ để vẽ một cái công tắc.
 *
 * ## Vì sao Messenger, không phải AIDL
 * Cả hai chiều đều là *"một tin, không chờ trả lời tại chỗ"* — `Messenger` cho đúng thế và **xếp hàng trên một
 * luồng**, nên không có lời gọi nào chặn luồng của launcher (ràng buộc số 1 của [VoiceSpeaker]). AIDL đồng bộ sẽ
 * mời đúng thứ phải tránh: một `generate()` 2 giây làm đơ luồng vẽ.
 *
 * ## Vòng đời
 * [SherpaTtsSpeaker] dựng **lười** và giữ nguyên engine giữa các câu — y như trước khi tách (nạp gói 61 MB mất
 * hàng trăm ms, nạp lại mỗi câu là đổi một lỗi bằng một lỗi khác). `:tts` là **tiến trình của một dịch vụ được
 * bind**, nên nó thừa hưởng độ quan trọng của launcher trong lúc còn bind ⇒ LMK không dọn nó giữa câu; và khi
 * launcher `unbindService` ([RemotePiperSpeaker.shutdown]) thì [onDestroy] nhả engine rồi tiến trình tự tắt.
 *
 * ⚠ Service này **KHÔNG** foreground và **KHÔNG** exported: nó không có việc gì để làm khi không ai bind.
 */
class PiperTtsService : Service() {

    /**
     * MỘT máy đọc cho cả tiến trình. Dựng lười (`by lazy`) nên tiến trình `:tts` khởi động xong vẫn chưa nạp gì —
     * 61 MB chỉ vào RAM ở câu đầu tiên.
     */
    private val sherpa by lazy { SherpaTtsSpeaker(this) }

    /**
     * Hộp thư của tiến trình `:tts`, đặt trên **luồng chính của chính nó**.
     *
     * Luồng chính ở đây rỗng việc (không có view, không có animation), nên nó chỉ làm đúng một chuyện: nhận tin
     * rồi chuyển cho [SherpaTtsSpeaker] — lớp đó tự đẩy việc nặng sang luồng `KachiSpeak` của nó. Không có gì
     * chặn ở đây, và cũng không cần một luồng thứ hai để chứng minh điều đó.
     */
    private val inbox by lazy {
        Messenger(
            Handler(Looper.getMainLooper()) { msg ->
                when (msg.what) {
                    MSG_SPEAK -> onSpeak(msg)
                    MSG_STOP -> runCatching { sherpa.stop() }
                        .onFailure { Log.w(TAG, "cắt câu hỏng", it) }
                    else -> Log.i(TAG, "tin lạ what=${msg.what} — bỏ qua")
                }
                true
            },
        )
    }

    override fun onBind(intent: Intent?): IBinder = inbox.binder

    /**
     * Đọc một câu rồi báo về đúng chỗ đã hỏi.
     *
     * ⚠ [SherpaTtsSpeaker.speak] hứa gọi `onDone` **đúng một lần ở mọi đường thoát** (phát hết · bị cắt · tổng
     * hợp ném · câu đã lỗi thời · và cả khi nó trả `false`) — xem hợp đồng ba vế ở KDoc [VoiceSpeaker.speak]. Nên
     * ở đây **không** cần thêm một đường báo xong thứ hai; thêm vào là mở cửa cho hai MSG_DONE cùng id.
     *
     * Đường duy nhất hợp đồng ấy KHÔNG phủ được là chính cái lỗi đang vá: tiến trình này chết giữa `generate`.
     * Ca đó do [RemotePiperSpeaker] bắt bằng `onServiceDisconnected` — chỗ duy nhất còn sống để bắt.
     */
    private fun onSpeak(msg: Message) {
        val data: Bundle? = msg.data
        val id = data?.getInt(KEY_ID, NO_ID) ?: NO_ID
        val text = data?.getString(KEY_TEXT).orEmpty()
        val reply = msg.replyTo
        if (id == NO_ID) {
            // Không biết id thì không báo xong được cho ai. Chỉ xảy ra nếu tin bị dựng sai ở đầu kia ⇒ ghi lại
            // để lần sau tìm được, và KHÔNG đọc (đọc một câu mà chỗ gọi đang chờ mãi là tệ hơn không đọc).
            Log.w(TAG, "MSG_SPEAK thiếu id — bỏ qua")
            return
        }
        runCatching { sherpa.speak(text) { sendDone(reply, id) } }
            .onFailure {
                Log.w(TAG, "không nhận được câu để đọc", it)
                // Ném ở tầng JVM (không phải native) ⇒ `onDone` của lớp kia có thể chưa chạy ⇒ tự đóng sổ.
                sendDone(reply, id)
            }
    }

    /**
     * Báo *"đọc xong câu [id]"*. Đầu kia chết trước ⇒ `RemoteException` ⇒ bỏ qua (không có ai để báo nữa).
     *
     * ## CLOSE-4 — trim SAU khi đã báo xong, không trước
     * Đây là mốc pha duy nhất của `:tts` (một câu = một lượt `generate` cấp phát rồi nhả PCM float + buffer nội bộ
     * của onnxruntime). Trim **sau** `to.send(msg)` có chủ ý: `madvise` mất vài ms, và đặt nó trước lời báo là cộng
     * thẳng vài ms vào độ trễ mà phía launcher đang chờ để đóng phiên. Hàm này chạy trên luồng `KachiSpeak`
     * ([ĐO] `SherpaTtsSpeaker.kt:105` — *"[onDone] chạy trên luồng `KachiSpeak`"*), tức **đúng luồng đã cấp phát**
     * ⇒ `thread.tcache.flush` của `M_PURGE` xả đúng cache cần xả (xem `kachimem.c`).
     */
    private fun sendDone(reply: Messenger?, id: Int) {
        val to = reply ?: return
        val msg = Message.obtain(null, MSG_DONE).apply {
            data = Bundle().apply { putInt(KEY_ID, id) }
        }
        runCatching { to.send(msg) }.onFailure { Log.i(TAG, "không báo được 'đọc xong' — launcher đã đi", it) }
        KachiMem.trim("sau đọc xong câu")
    }

    override fun onDestroy() {
        // Nhả phiên ONNX + `AudioTrack`. [SherpaTtsSpeaker.shutdown] tự xếp lượt nhả SAU `generate` đang chạy
        // (nhả con trỏ native dưới chân một lời gọi đang chạy là use-after-free) — xem KDoc của nó.
        runCatching { sherpa.shutdown() }.onFailure { Log.w(TAG, "nhả máy đọc hỏng", it) }
        super.onDestroy()
    }

    companion object {
        private const val TAG = "KachiVoiceTtsProc"

        /**
         * Hậu tố tên tiến trình, khai **MỘT chỗ** — `AndroidManifest.xml` viết `android:process=":tts"` và
         * `KachiApplication` so tên tiến trình bằng chính hằng này (bài canh khoá cả hai khớp nhau).
         */
        const val PROCESS_SUFFIX = ":tts"

        /** launcher → `:tts`: đọc câu này. `data` = [KEY_ID] + [KEY_TEXT]; `replyTo` = hộp thư launcher. */
        const val MSG_SPEAK = 1

        /** launcher → `:tts`: cắt câu đang đọc. */
        const val MSG_STOP = 2

        /** `:tts` → launcher: câu [KEY_ID] đã đọc xong (hoặc bị cắt / hỏng — hợp đồng *"luôn báo"*). */
        const val MSG_DONE = 3

        /** Số thế hệ câu, do launcher cấp ([RemotePiperSpeaker]); đi sang rồi quay về nguyên vẹn. */
        const val KEY_ID = "id"

        /** Chuỗi ĐÃ qua `TtsPronunciation.normalise` ở launcher — `:tts` chỉ đọc, không phiên âm lại. */
        const val KEY_TEXT = "text"

        /** Giá trị không-thể-là-thế-hệ-thật: [RemotePiperSpeaker] đếm từ 1 lên. */
        private const val NO_ID = 0
    }
}
