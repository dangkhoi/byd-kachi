package com.byd.clusternav.launcher.camera

import com.byd.clusternav.launcher.camera.CameraDemand.Outcome
import java.util.concurrent.atomic.AtomicBoolean

/**
 * ═══ 2.93 wave 2C · CAM-WAKE-COLD-MAIN — MỘT lượt hỏi `:wake` → tiến trình chính, GỬI LẠI MỘT LẦN khi chính vừa khởi động ═══
 *
 * Spec `docs/specs/kachi-293-wave2c.html` R6. Wave 2B (spec 293-cam §4.9 a) cho phiên giọng nói ở `:wake` hỏi controller qua
 * broadcast CÓ THỨ TỰ; receiver của tiến trình chính được đăng ký ở `KachiApplication.onCreate`. Lỗ đã ghi ở §10 Pass 2
 * ("giới hạn đã biết") [SUY đọc mã]: tiến trình chính ĐANG khởi động — đã có trong danh sách tiến trình của gói
 * (`isMainProcessAlive` = true) nhưng `Application.onCreate` chưa tới dòng đăng ký — thì broadcast không gặp receiver nào,
 * kết quả về NGAY với mã khởi đầu ⇒ [Outcome.UNREACHABLE] ⇒ *"✗ … chưa liên lạc được màn hình chính"*, kể cả cho *"tắt
 * camera"* mà lẽ ra rơi về Camera 360 (tiến trình chính mới chưa thể có camera theo yêu cầu nào).
 *
 * ## Luật (thuần, đồng hồ + bộ hẹn giờ tiêm vào ⇒ test off-car: `CameraWakeAskTest`)
 *  • receiver trả lời (mã ≠ mã khởi đầu) ⇒ kết quả đó, ngay;
 *  • KHÔNG receiver nào trả lời (mã khởi đầu về) ở lượt ĐẦU, còn đủ hạn ⇒ chờ [RETRY_DELAY_MS] rồi gửi lại ĐÚNG MỘT lần — lượt
 *    gửi lại chỉ đi khi tiến trình chính VẪN sống; nó đã tắt trong lúc chờ ⇒ [CameraDemand.withoutMain] (TẮT = không có gì để
 *    tắt ⇒ *"tắt camera"* rơi về Camera 360 như ≤ 2.92; MỞ = không tới được) — cùng luật của lượt hỏi đầu (`fireForResult`);
 *  • lượt gửi lại cũng không ai trả lời ⇒ [Outcome.UNREACHABLE] — KHÔNG đoán: `VoiceCameraTurn` nói *"✗ …"* và không rơi về
 *    Camera 360 (không biết camera theo yêu cầu có đang mở không — receiver có thể đã hỏng đăng ký trên một tiến trình đang
 *    chạy, nơi camera theo yêu cầu đang mở được);
 *  • hết hạn TỔNG [totalMs] mà chưa có kết quả (tiến trình chính treo luồng chính) ⇒ [Outcome.UNREACHABLE]; gửi hỏng ⇒ như vậy,
 *    không gửi lại. Kết quả về MUỘN chỉ ghi log — câu đã nói không rút lại được ([onResult] đúng MỘT lần).
 *
 * ## Vì sao an toàn để gửi lại (không áp lệnh hai lần)
 * Receiver luôn đặt mã ≠ mã khởi đầu sau khi áp ([CameraDemand.outcome] không bao giờ ra [Outcome.UNREACHABLE]) ⇒ mã khởi
 * đầu về = CHẮC CHẮN chưa receiver nào áp lệnh. Lượt gửi lại chỉ bắn SAU khi lượt đầu đã về như vậy (không bao giờ hai lượt
 * cùng bay), nên lệnh được áp tối đa MỘT lần. Lượt hết hạn thì KHÔNG gửi lại (lượt đầu có thể còn đang bay). Lệnh tới receiver
 * SAU hạn tổng thì receiver bỏ, không áp ([expired] — câu ✗ đã nói ⇒ không được có hiệu lực muộn), và giữ mã khởi đầu ⇒ bất
 * biến *"mã khởi đầu = chưa áp"* vẫn đúng.
 *
 * ## Vì sao trần tổng không tăng (≤ hạn chờ cũ, không phải 3 s)
 * Lượt gửi lại nằm TRONG hạn chờ cũ `CameraDemandDispatch.RESULT_TIMEOUT_MS` (2 s): bài canh `hạn × 2 ≤ lưới an toàn lượt
 * nói` (`VoiceSession.TURN_SETTLE_MS` 4 s) giữ chỗ cho đường Camera 360 chạy SAU kết quả. Nới trần là ăn vào chỗ ấy.
 */
object CameraWakeAsk {

    /**
     * Chờ trước lượt gửi lại DUY NHẤT. [ĐOÁN] — `Application.onCreate` của tiến trình chính chạy từ đầu tới dòng đăng ký
     * receiver chỉ gồm vài lời gọi không I/O ([SUY] đọc `KachiApplication.onCreate`), còn lượt về NGAY của mã khởi đầu đi
     * hết vài chục ms; 1 s cho tiến trình nhiều thời gian nhất mà lượt gửi lại vẫn còn ≥ [MIN_RETRY_WINDOW_MS] trong hạn 2 s.
     * 🚗 Số thật: `logcat -s KachiCamDemand` — dòng *"gửi lại sau …"* rồi *"kết quả … sau N ms"*.
     */
    const val RETRY_DELAY_MS = 1_000L

    /** Lượt gửi lại phải còn ít nhất chừng này hạn — dưới đó thì gửi cũng không kịp về, báo luôn (đường một chiều ~52 ms máy ảo). */
    const val MIN_RETRY_WINDOW_MS = 300L

    /**
     * Soát senior wave 2B/2C [P2] — lệnh tới receiver của tiến trình chính SAU hạn [deadlineMs] của bên gửi ⇒ KHÔNG áp (mã giữ
     * mã khởi đầu): bên `:wake` đã báo [Outcome.UNREACHABLE] (*"✗ … chưa liên lạc được màn hình chính"*). [ĐO nguồn r47]
     * receiver đăng ký động chạy bằng một runnable đẩy vào Handler của nó (`LoadedApk.java:1628-1648` — ở đây là luồng chính)
     * ⇒ luồng chính bận (vừa khởi động: dựng HOME) thì lệnh được áp MUỘN, cả sau khi câu ✗ đã nói. Lệnh MỞ là *"sự kiện mới
     * nhất"* ([CameraDemandState.apply]) nên một lệnh muộn ĐÈ được camera xi-nhan bật trong lúc chờ — ca thật: nói *"mở camera
     * sau"* lúc vừa nổ máy (lượt gửi lại xếp sau lượt dựng HOME) rồi bật xi-nhan ra khỏi chỗ đỗ ⇒ camera sau che camera điểm mù.
     * [deadlineMs] · [nowMs] cùng đồng hồ khởi động `SystemClock.elapsedRealtime` — [ĐO nguồn r47] JNI
     * `android_os_SystemClock.cpp:89` → `libutils/SystemClock.cpp:49-61` `clock_gettime(CLOCK_BOOTTIME)`: đồng hồ của nhân,
     * CHUNG mọi tiến trình. [deadlineMs] ≤ 0 = không có hạn (không bỏ gì).
     */
    fun expired(deadlineMs: Long, nowMs: Long): Boolean = deadlineMs > 0 && nowMs > deadlineMs

    /**
     * Hỏi controller của tiến trình chính về lệnh [op], gửi lại tối đa MỘT lần (xem KDoc lớp).
     *
     * @param totalMs trần TỔNG từ lúc gửi lượt đầu tới lúc báo kết quả (bản thật: `RESULT_TIMEOUT_MS`).
     * @param now đồng hồ đơn điệu (ms).
     * @param schedule hẹn một việc sau N ms, trên CÙNG luồng mà [send] gọi lại (bản thật: `Handler` luồng chính).
     * @param mainAlive tiến trình chính còn sống không — đọc lại ngay trước lượt gửi lại.
     * @param send gửi MỘT lượt; gọi lại ĐÚNG một lần với mã receiver đặt (mã khởi đầu = [Outcome.UNREACHABLE].code = không
     *   receiver nào trả lời), hoặc `null` = gửi hỏng.
     * @param log một dòng nhật ký cho buổi xe.
     * @param onResult nhận kết quả ĐÚNG MỘT lần.
     */
    @Suppress("LongParameterList")
    fun run(
        op: CameraDemand.Op,
        totalMs: Long,
        now: () -> Long,
        schedule: (Long, () -> Unit) -> Unit,
        mainAlive: () -> Boolean,
        send: ((Int?) -> Unit) -> Unit,
        log: (String) -> Unit,
        onResult: (Outcome) -> Unit,
    ) {
        val t0 = now()
        val done = AtomicBoolean(false)
        fun deliver(o: Outcome, why: String) {
            if (done.compareAndSet(false, true)) {
                log("kết quả $o sau ${now() - t0} ms ($why)")
                onResult(o)
            } else {
                log("kết quả về MUỘN sau hạn chờ, bỏ: $o ($why)")
            }
        }
        // Hạn TỔNG hẹn TRƯỚC lượt gửi đầu — một lượt gửi treo không bao giờ giữ được lượt nói.
        schedule(totalMs) { if (!done.get()) deliver(Outcome.UNREACHABLE, "tiến trình chính không trả lời trong $totalMs ms") }
        fun attempt(n: Int) {
            send { code ->
                when {
                    code == null -> deliver(Outcome.UNREACHABLE, "gửi hỏng")
                    code != Outcome.UNREACHABLE.code ->
                        deliver(CameraDemand.outcomeOf(code), if (n == 0) "receiver trả lời" else "receiver trả lời ở lượt gửi lại")
                    n == 0 && !done.get() && now() - t0 + RETRY_DELAY_MS + MIN_RETRY_WINDOW_MS <= totalMs -> {
                        log("chưa receiver nào trả lời (tiến trình chính có thể đang khởi động) ⇒ gửi lại sau $RETRY_DELAY_MS ms")
                        schedule(RETRY_DELAY_MS) {
                            when {
                                done.get() -> Unit
                                !mainAlive() -> deliver(CameraDemand.withoutMain(op), "tiến trình chính đã tắt trong lúc chờ")
                                else -> attempt(1)
                            }
                        }
                    }
                    else -> deliver(Outcome.UNREACHABLE, if (n == 0) "không receiver nào trả lời" else "lượt gửi lại cũng không ai trả lời")
                }
            }
        }
        attempt(0)
    }
}
