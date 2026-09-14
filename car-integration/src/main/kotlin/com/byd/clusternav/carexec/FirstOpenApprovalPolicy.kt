package com.byd.clusternav.carexec

/**
 * ═══ F4 — LẦN DÒ KÊNH SHELL ĐẦU TIÊN CỦA MỘT PHIÊN LAUNCHER ═══════════════════════════════════════════════════
 *
 * ## Bệnh (đo được, không suy luận)
 * [ĐO] xe DiLink3.0 2026-09-14 (`docs/diagnostics/carlog-kachi-20260914-2044/`):
 *  - `20:49:12` Kachi cài xong và mở. Màn chính vừa `onCreate` đã nối dadb `localhost:5555` bằng khoá MỚI sinh
 *    ⇒ hệ thống bung `UsbDebuggingActivity` ("Cho phép gỡ lỗi USB?").
 *  - `20:49:16.986` `WindowManager: removeWindow … UsbDebuggingActivity` — **đúng** lúc `KachiHomeActivity` resume
 *    toàn màn. Hộp thoại chết trước khi người lái kịp thấy, nên **không ai bấm được gì**.
 *  - Ổ cắm `127.0.0.1:40794→5555` ở `ESTABLISHED`, `Recv-Q` 24→48: phiên dadb treo chờ một câu trả lời sẽ không
 *    bao giờ tới. Hàng quyền lại hiện *"Hạn chế của môi trường"* — **sai**: môi trường không hạn chế gì cả, hệ
 *    thống đang **chờ người dùng bấm**.
 *
 * ## Vì sao cần một chính sách RIÊNG, không dùng lại [LocalShellRetry.AWAIT_ADB_APPROVAL]
 * Cái kia sinh cho đường **owner vừa ra lệnh** (giữ phím mic): chờ ~31 s rồi bỏ cuộc là đúng, vì sau 31 s cú bấm
 * đã cũ. Lần mở đầu thì ngược lại — **không có cú bấm nào để mà cũ**: màn chính cứ đứng đó suốt chuyến, và người
 * lái có thể tích "Luôn cho phép" ở phút thứ năm. Bỏ cuộc sau 4 lần là quay lại đúng bệnh F4 (owner phải tắt/mở
 * lại app). Nên ở đây: **thử lại KHÔNG giới hạn số lần, chừng nào màn còn hiện** (CLAUDE.md §6 — đường mới xuống
 * cuối, đường cũ giữ nguyên: mọi bên gọi của [LocalShellRetry] không đổi một byte).
 *
 * ## Ba tính chất mà tầng trên PHẢI giữ (canh bằng test ở cả hai module)
 *  1. **Hoãn lần dò đầu** tới khi Activity đã vẽ khung đầu + có tiêu điểm + yên [SETTLE_MS] — để hộp thoại hệ
 *     thống nổi LÊN TRÊN màn Kachi thay vì bị lượt resume của nó gỡ mất.
 *  2. **Mỗi lần thử là một kết nối MỚI** ([PROBE] có `attempts = 1` ⇒ [LocalShellSessions.run] mở-và-đóng đúng một
 *     phiên mỗi lượt) — phiên cũ đang kẹt `Recv-Q` phải được đóng, không tái dùng.
 *  3. **Hạn đọc** [PROBE_READ_TIMEOUT_MS] < nhịp thử lại [RETRY_EVERY_MS]: không có hạn đọc thì `Dadb.create` đọc
 *     VÔ HẠN (xem [LocalShellFailure]) ⇒ luồng nền của launcher treo vĩnh viễn và mọi lượt thử sau xếp hàng sau nó.
 */
object FirstOpenApproval {

    /**
     * Khoảng YÊN sau khung hình đầu trước khi dò lần đầu.
     *
     * Không phải "ngủ cho chắc": nó là khoảng để lượt `resume` + `setContentView` + lượt vẽ đầu của màn chính
     * **kết thúc hẳn**, vì chính lượt đó gỡ cửa sổ của `UsbDebuggingActivity` ([ĐO] 20:49:16.986). Tầng trên đo
     * mốc bắt đầu bằng `decorView.post` + `onWindowFocusChanged(true)` — tức bằng SỰ KIỆN THẬT của vòng đời cửa
     * sổ — rồi mới cộng khoảng này; không đo bằng một giấc ngủ cứng từ lúc `onCreate`.
     */
    const val SETTLE_MS = 1_500L

    /**
     * Nhịp thử lại khi đang chờ người dùng bấm. 20 s đủ thưa để không dựng hộp thoại liên tục, đủ dày để người
     * vừa bấm xong không phải chờ lâu mới thấy launcher tự lành.
     */
    const val RETRY_EVERY_MS = 20_000L

    /**
     * Hạn ĐỌC cho một lượt dò — cùng con số 6 s đã chạy thật ở [LocalShellRetry.AWAIT_ADB_APPROVAL] (F2), vì đây
     * là cùng một hiện tượng: adbd **im lặng** trong lúc hộp thoại treo. Phải nhỏ hơn [RETRY_EVERY_MS] để hai lượt
     * dò không bao giờ chồng lên nhau.
     */
    const val PROBE_READ_TIMEOUT_MS = 6_000

    /**
     * Chính sách transport cho MỘT lượt dò: một lần thử, có hạn đọc, ép bắt tay, **không** thử lại ở tầng dưới.
     *
     * Vòng thử lại nằm ở tầng trên (Handler của màn chính) chứ không ở đây, vì nó phải dừng/chạy theo **vòng đời
     * màn hình** — thứ mà `:car-integration` không được biết. `eagerHandshake = true` để lỗi xác thực lộ ra
     * TRƯỚC khi lượt dò gửi lệnh nào (xem KDoc [LocalShellRetry.eagerHandshake]).
     */
    val PROBE = LocalShellRetry(
        attempts = 1,
        socketTimeoutMs = PROBE_READ_TIMEOUT_MS,
        eagerHandshake = true,
        retryOn = emptySet(),
    )

    /**
     * Một lượt dò vừa xong ⇒ làm gì tiếp. Thuần, không đụng thiết bị ⇒ test off-car được.
     *
     * @param reason `null` = kênh lên được; còn lại là lý do [LocalShellFailures.classify] đã phân loại được.
     * @param screenShowing màn chính còn ở trên STARTED hay không (dừng ở `onStop` — CLAUDE.md §5: không để một
     *   vòng lặp sống lâu hơn thứ nó phục vụ).
     */
    fun step(reason: LocalShellFailure?, screenShowing: Boolean): FirstOpenStep = when {
        reason == null -> FirstOpenStep.ChannelUp
        // ⚠ CHỈ [LocalShellFailure.AWAITING_APPROVAL] mới là "đang chờ người bấm" — đó là lý do ĐÃ ĐO trên xe
        // (adbd im lặng, `Recv-Q` dâng). [LocalShellFailure.AUTH_REJECTED] (máy trả `AUTH` = đã bấm Từ chối / khoá
        // bị gỡ) KHÔNG được gộp vào đây: gộp là cứ 20 s lại dựng một hộp thoại cho người vừa nói "không".
        // [LocalShellFailure.PORT_CLOSED] là adbd không lắng nghe — chờ bao lâu cũng vô ích. Cả hai đi nhánh
        // [FirstOpenStep.Environment], giữ nguyên câu chữ "hạn chế môi trường" mà bản trước đã dùng.
        reason == LocalShellFailure.AWAITING_APPROVAL ->
            FirstOpenStep.AwaitingUser(if (screenShowing) RETRY_EVERY_MS else NO_RETRY)
        else -> FirstOpenStep.Environment(reason)
    }

    /**
     * Có được phép bắn một lượt dò NGAY BÂY GIỜ không.
     *
     * `windowFocused = false` nghĩa là **một cửa sổ khác đang ở trên** — và ở đúng ca này, cửa sổ đó nhiều khả
     * năng CHÍNH LÀ hộp thoại "Cho phép gỡ lỗi USB?" mà ta vừa dựng ra. Mở thêm một kết nối lúc ấy là dựng chồng
     * thêm một hộp thoại nữa lên cái người dùng đang đọc dở (việc "mở lại kết nối trong lúc hộp thoại đang treo
     * có bung thêm hộp thoại không" là [CHƯA BIẾT] từ F2 §4.3 — nên chọn đường KHÔNG thử). Bỏ lượt này, lượt sau
     * 20 s nữa lại hỏi; hoặc người dùng bấm "Thử lại" ở dải nhắc.
     */
    fun attemptAllowed(screenShowing: Boolean, windowFocused: Boolean): Boolean = screenShowing && windowFocused

    /** Giá trị "không hẹn lượt sau" của [FirstOpenStep.AwaitingUser.retryAfterMs]. */
    const val NO_RETRY = 0L
}

/** Kết quả quyết định của [FirstOpenApproval.step]. */
sealed interface FirstOpenStep {

    /** Kênh shell lên được ⇒ tầng trên chạy ĐÚNG đường nối dây đã có từ trước (không có đường thứ hai). */
    object ChannelUp : FirstOpenStep

    /**
     * Hệ thống **đang hỏi người dùng**. Tầng trên: hàng quyền đổi sang *người dùng làm* (không phải "môi trường"),
     * hiện dải nhắc, và hẹn lượt sau [retryAfterMs] ms ([FirstOpenApproval.NO_RETRY] = không hẹn, vì màn đã khuất).
     */
    data class AwaitingUser(val retryAfterMs: Long) : FirstOpenStep

    /** Không phải chuyện người dùng bấm được (cổng 5555 câm, đứt, không rõ) ⇒ nói ĐÚNG là hạn chế môi trường. */
    data class Environment(val reason: LocalShellFailure) : FirstOpenStep
}
