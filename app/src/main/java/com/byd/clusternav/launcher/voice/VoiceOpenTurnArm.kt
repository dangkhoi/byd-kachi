package com.byd.clusternav.launcher.voice

import android.util.Log
import java.util.concurrent.FutureTask

/**
 * ═══ VOICE-OPEN-TURN · pha THI HÀNH — giữ lượt nghe mở qua một quãng ngừng-để-nghĩ ═══════════════════════════
 *
 * Quyết định *"câu này còn dở không"* và phép ghép ở `:core` ([VoiceOpenTurn], kiểm off-car). Tệp này là phần
 * **không thuần**: nó biết đồng hồ, biết micro đang mở, và biết bộ giải mã. Tách khỏi [VoiceCapture] theo VAI
 * (CLAUDE.md §4.1 · trần 500 dòng) — vòng đọc micro ở kia chỉ gọi bốn lời: [arm] · [stopReading] · [result] · [armed].
 *
 * ## ⚠ Vì sao giải mã vế trước trên MỘT LUỒNG RIÊNG — và vì sao đó là điều kiện để yêu cầu số 1 đúng
 * Yêu cầu của owner: *"câu đủ nghĩa KHÔNG được thêm một mili-giây nào"*. Nhưng muốn biết câu có dở không thì
 * phải có **chữ**, mà mô hình đang ship là **OFFLINE** — `VoiceRecognizer.partial()` trả chuỗi rỗng (KDoc bên
 * đó: *"không có chữ đang nghe dở ở mô hình offline"*) nên chữ chỉ có sau một lượt giải mã, [ĐO xe] **1,3–2 s**.
 *
 * Ba cách đã cân, và vì sao hai cách đầu **sai bằng số**:
 *
 * | cách | điều gì xảy ra |
 * |---|---|
 * | đóng mic → giải mã → thấy dở → **mở lại** mic | vế sau bắt đầu ở +120…460 ms sau điểm ngắt ([ĐO] §1.4) mà chữ chỉ có ở +1 300…2 000 ms ⇒ micro đóng đúng lúc người ta đang nói ⇒ **không bao giờ** bắt được vế sau |
 * | giải mã **trên luồng đọc**, giữ mic mở | `AudioRecord` chỉ đệm `VoiceCaptureDevice.MIN_BUFFER_MS` = 1 s; không đọc 1,3–2 s ⇒ tràn đệm ⇒ mất đúng khúc tiếng của vế sau, không lỗi nào báo |
 * | **giữ vòng đọc chạy, giải mã ở luồng nền** | vòng đọc giữ nhịp 200 ms như cũ; phần chờ **trùng** với lượt giải mã mà hôm nay vẫn phải chạy ⇒ câu đủ nghĩa về đúng cùng thời điểm như trước bản này |
 *
 * ⇒ Cái luồng nền này không phải để *"cho nhanh"*: nó là cách duy nhất vừa có chữ vừa còn nghe. Và nó không đảo
 * thứ tự đường cũ (CLAUDE.md §6): lượt giải mã vẫn đúng một lượt, trên đúng khúc `[headStart, trimA)` mà lượt
 * KHÔNG giữ của [VoiceCapture] cũng giải mã — chỉ **thời điểm** nó bắt đầu là sớm hơn, còn kết quả từng-ký-tự thì
 * bằng nhau. (`headStart` = 0 ở gần như mọi lượt — xem [VoiceTurnEndpoint.headStart].)
 *
 * ## Tranh chấp dữ liệu — vì sao an toàn, không phải vì sao chắc là an toàn
 * Luồng nền đọc `buffer[headStart, trimA)` của [VoiceRecognizer]; vòng đọc tiếp tục `accept()` **ghi vào sau** chỉ số
 * `filled ≥ trimA`. Hai vùng **không chồng nhau**. `Thread.start()` là một mốc happens-before nên luồng nền thấy
 * trọn phần đã gom tại lúc [arm]; các lượt ghi sau đó có thấy hay không cũng không đổi kết quả (`minOf(filled,
 * trimA)` luôn ra `trimA`). Không có lượt giải mã nào chạy song song với lượt khác: [result] `get()` xong vế
 * trước rồi mới giải mã vế sau.
 *
 * ## Đường thoát — không có nhánh nào giữ micro vô hạn
 *  1. chữ vế trước về và **đủ nghĩa** ⇒ thoát NGAY (đây là đường của gần như mọi lượt);
 *  2. vế sau đã chốt (VAD đóng đoạn mới) ⇒ thoát, giải mã vế sau, ghép;
 *  3. hết [VoiceOpenTurn.OPEN_JOIN_WINDOW_MS] mà **chưa có tiếng nào** ⇒ thoát, dùng vế trước (đúng hành vi hôm
 *     nay: phân tích phần đã có ⇒ hỏi lại *"ô nào"*);
 *  4. trần cứng [VoiceOpenTurn.OPEN_MAX_EXTRA_MS];
 *  5. trần cứng `VoiceSession.MAX_LISTEN_MS` = 8 s và cờ huỷ — **không đụng tới**, chúng vẫn gác vòng đọc như cũ.
 *
 * Phiên bị huỷ giữa pha chờ ⇒ [result] không được gọi ⇒ luồng nền tự chạy hết rồi chết (daemon). Nó chỉ tốn đúng
 * lượt giải mã mà bản cũ cũng đã tốn.
 */
internal class VoiceOpenTurnArm(
    private val rec: VoiceRecognizer,
    private val ep: VoiceTurnEndpoint,
    /** Chữ vế trước để tấm chữ hiện **trong lúc chờ** — đi qua đúng đường `onPartial` đã có, không thêm UI/TTS. */
    private val onPartial: (String) -> Unit,
) {

    private var worker: FutureTask<String>? = null
    private var armedAt = 0L
    private var segmentsAtEndpoint = 0
    private var partialShown = false

    /** Đã giữ lượt chưa — vòng đọc dùng để biết mình đang ở pha A hay pha chờ. */
    val armed: Boolean get() = worker != null

    /**
     * Giữ lượt nghe tại **điểm ngắt câu đầu tiên** và cho chạy lượt giải mã vế trước ở luồng nền.
     *
     * @param fedSamples số mẫu đã đọc được tới điểm ngắt — mốc để cắt vế trước (`head`, [VoiceVadTrim]).
     * @return `true` khi đã giữ (vòng đọc **tiếp tục**); `false` khi không giữ được ⇒ chỗ gọi thoát như cũ.
     *   `false` ở hai ca: đường ngắt câu là RMS (xem [VoiceTurnEndpoint.openTurnReady]) và lượt đã giữ rồi.
     */
    fun arm(fedSamples: Int): Boolean {
        if (worker != null || !ep.openTurnReady()) return false
        val trim = ep.trimSamples(fedSamples)
        if (trim <= 0) return false
        segmentsAtEndpoint = ep.segmentCount()
        armedAt = System.currentTimeMillis()
        // VOICE-HEAD-SILENCE: vế TRƯỚC cũng bỏ im lặng dẫn đầu (0 ở gần như mọi lượt) — cùng khúc mà [VoiceCapture]
        // đưa vào bộ giải mã ở lượt không giữ, nên hai đường vẫn nghe đúng một thứ.
        val start = ep.headStart(fedSamples)
        val task = FutureTask { rec.rangeResult(start, trim) }
        worker = task
        Thread(task, "KachiOpenTurnDecode").apply { isDaemon = true }.start()
        Log.i(VoiceEngine.TIMING_TAG, "noi-tiep: giữ micro trong lúc giải mã vế trước (mẫu $start..$trim)")
        return true
    }

    /**
     * Gọi **mỗi khối** trong pha chờ. `true` ⇒ thoát vòng đọc ngay.
     *
     * @param segmentClosed bộ ngắt câu vừa chốt thêm đoạn nào trong khối này không (giá trị `stop` của vòng đọc).
     */
    fun stopReading(segmentClosed: Boolean): Boolean {
        if (segmentClosed && ep.segmentCount() > segmentsAtEndpoint) {
            Log.i(VoiceEngine.TIMING_TAG, "noi-tiep: vế sau đã chốt sau ${sinceArm()} ms")
            return true
        }
        val head = headIfDone()
        if (head != null) {
            if (!VoiceOpenTurn.isOpen(head)) {
                // Đường của gần như mọi lượt: chữ về, câu đủ nghĩa ⇒ thoát ngay. Phần đã chờ nằm TRỌN trong lượt
                // giải mã mà bản cũ cũng phải chạy, nên độ trễ tới người lái không đổi.
                Log.i(VoiceEngine.TIMING_TAG, "noi-tiep: vế trước đủ nghĩa ⇒ đóng lượt (chờ ${sinceArm()} ms)")
                return true
            }
            if (!partialShown) {
                partialShown = true
                onPartial(head)
                Log.i(
                    VoiceEngine.TIMING_TAG,
                    "noi-tiep: vế dở \"$head\" ⇒ chờ vế sau tối đa ${VoiceOpenTurn.OPEN_JOIN_WINDOW_MS} ms",
                )
            }
        }
        if (ep.speaking()) return false          // đang có tiếng ⇒ để VAD chốt, chỉ trần 8 s gác
        val waited = sinceArm()
        if (head != null && waited >= VoiceOpenTurn.OPEN_JOIN_WINDOW_MS) {
            Log.i(VoiceEngine.TIMING_TAG, "noi-tiep: hết cửa sổ ${VoiceOpenTurn.OPEN_JOIN_WINDOW_MS} ms, không ai nói tiếp")
            return true
        }
        // Chữ vế trước còn chưa về mà đã hết trần ⇒ thôi giữ micro; [result] vẫn chờ nốt lượt giải mã (bản cũ cũng chờ).
        if (waited >= VoiceOpenTurn.OPEN_MAX_EXTRA_MS) {
            Log.i(VoiceEngine.TIMING_TAG, "noi-tiep: trần ${VoiceOpenTurn.OPEN_MAX_EXTRA_MS} ms — thôi chờ")
            return true
        }
        return false
    }

    /**
     * Chữ cuối cùng của lượt: vế trước, hoặc vế trước **đã ghép** vế sau.
     *
     * **CHẶN** cho tới khi lượt giải mã vế trước xong — đúng lượt chờ mà bản cũ cũng phải chờ, chỉ là nó đã chạy
     * được một đoạn. Phải gọi **trước** `VoiceTurnEndpoint.close()` (nó cần [VoiceTurnEndpoint.tailRange]) và
     * **sau** `VoiceTurnEndpoint.flush()` — một vế sau còn đang mở lúc thoát vòng đọc chỉ vào hàng đợi đoạn nhờ
     * `flush`, nên [VoiceCapture] chốt nốt ngay khi ra khỏi vòng (`if (!ended || arm?.armed == true) ep.flush()`).
     *
     * @param fedSamples tổng số mẫu đã đọc trong cả cửa sổ.
     * @return `null` khi lượt chưa từng được giữ ⇒ chỗ gọi đi đường cũ.
     */
    fun result(fedSamples: Int): Outcome? {
        val task = worker ?: return null
        val head = runCatching { task.get() }
            .onFailure { Log.w(VoiceEngine.TIMING_TAG, "noi-tiep: lượt giải mã vế trước hỏng", it) }
            .getOrNull().orEmpty()
        if (!VoiceOpenTurn.isOpen(head)) return Outcome(head, head, "")
        val range = ep.tailRange(segmentsAtEndpoint, fedSamples)
            ?: return Outcome(head, head, "").also {
                Log.i(VoiceEngine.TIMING_TAG, "noi-tiep: không có vế sau ⇒ giữ nguyên \"$head\"")
            }
        val tail = rec.rangeResult(range.first, range.last + 1)
        val joined = VoiceOpenTurn.join(head, tail)
        Log.i(VoiceEngine.TIMING_TAG, "noi-tiep: ghép \"$head\" + \"$tail\" ⇒ \"$joined\"")
        return Outcome(joined, head, tail)
    }

    /** Chữ vế trước nếu lượt giải mã đã xong, `null` khi còn đang chạy. Không chặn. */
    private fun headIfDone(): String? {
        val task = worker ?: return null
        if (!task.isDone) return null
        return runCatching { task.get() }.getOrNull().orEmpty()
    }

    private fun sinceArm(): Long = System.currentTimeMillis() - armedAt

    /**
     * Kết quả một lượt có giữ.
     *
     * @property text chữ đưa xuống bộ phân tích (đã ghép nếu có vế sau).
     * @property head chữ của riêng vế trước · [tail] chữ của riêng vế sau (rỗng = không có) — hai cột này vào
     *   nhật ký và vào cầu kiểm thử `wav` để một lượt đo đọc được **cả hai vế**, không chỉ kết quả trộn.
     */
    data class Outcome(val text: String, val head: String, val tail: String)
}
