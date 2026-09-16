package com.byd.clusternav.launcher

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.byd.clusternav.AdbKeys
import com.byd.clusternav.R
import com.byd.clusternav.carexec.FirstOpenApproval
import com.byd.clusternav.carexec.FirstOpenStep
import com.byd.clusternav.carexec.LocalDeviceShell
import com.byd.clusternav.carexec.LocalShellFailure
import com.byd.clusternav.carexec.LocalShellResult
import com.byd.clusternav.launcher.KachiSpace as Sp

/**
 * ═══ F4 — CỔNG LẦN DÒ KÊNH SHELL ĐẦU TIÊN CỦA MÀN CHÍNH ═══════════════════════════════════════════════════════
 *
 * ## Bệnh (đo được trên xe, không suy luận — CLAUDE.md §2)
 * [ĐO] DiLink3.0 2026-09-14 (`docs/diagnostics/carlog-kachi-20260914-2044/session-findings.md`):
 *  - `20:49:12` Kachi mở lần đầu. `onCreate` nối dadb `localhost:5555` bằng khoá vừa sinh ⇒ hệ thống bung
 *    `UsbDebuggingActivity` ("Cho phép gỡ lỗi USB?").
 *  - `20:49:16.986` `WindowManager: removeWindow … UsbDebuggingActivity` **đúng lúc** `KachiHomeActivity` resume
 *    toàn màn ⇒ hộp thoại chết trước khi người lái thấy. Ổ cắm `ESTABLISHED`, `Recv-Q` 24→48 (dadb treo).
 *  - Hệ quả: không quyền nào tự cấp được, và hàng quyền lại nói *"Hạn chế của môi trường"* — sai người, sai việc.
 *  - Sau khi owner tự tích "Luôn cho phép" (lúc Kachi đã đứng yên), **mọi quyền tự cấp thành công**.
 *
 * ## Cách chữa — ba nửa, thiếu nửa nào cũng hỏng
 *  1. **Hoãn lần dò đầu** tới khi cửa sổ đã vẽ khung đầu (`decorView.post`), đã có tiêu điểm
 *     (`onWindowFocusChanged(true)`), rồi yên thêm [FirstOpenApproval.SETTLE_MS]. Không đo bằng một giấc ngủ cứng
 *     từ `onCreate`: mốc phải là **sự kiện thật của vòng đời cửa sổ**, vì chính lượt resume/vẽ đó là thứ gỡ hộp
 *     thoại đi.
 *  2. **Thử lại đều đặn, không giới hạn số lần** chừng nào màn còn hiện — người lái có quyền tích ô đó ở phút thứ
 *     năm. Dừng ở `onStop` ([onHidden]) vì một vòng lặp không được sống lâu hơn thứ nó phục vụ (CLAUDE.md §5).
 *  3. **Nói ĐÚNG lý do**: chỉ khi tầng transport phân loại được là [LocalShellFailure.AWAITING_APPROVAL] thì hàng
 *     quyền mới đổi sang *việc của người dùng* + hiện dải nhắc. Mọi lý do khác giữ nguyên câu "hạn chế môi trường".
 *
 * ## Vì sao đường cũ KHÔNG bị đụng (CLAUDE.md §6)
 * [onChannelUp] chính là **nguyên khối** `if (dadb.probe()) { … } else { … }` đã chạy tốt trước F4 — cổng này chỉ
 * quyết định **KHI NÀO** gọi nó, không viết lại nó. Lượt dò của cổng đi qua [ShellApprovalProbe] (một phiên
 * [LocalDeviceShell] rời, có hạn đọc) và **không** đụng tới `ShellTransport` — chủ duy nhất của kết nối lệnh cửa sổ.
 *
 * ⚠ Mọi field ở đây chỉ được chạm trên **thread chính** (Handler của màn chính), trừ [awaitingApproval] —
 * `@Volatile` vì màn Cài đặt đọc nó khi dựng trang.
 */
internal class ShellChannelGate(
    private val activity: Activity,
    /** Khung gốc của màn chính — nơi gắn dải nhắc. */
    private val host: FrameLayout,
    private val handler: Handler,
    /** Cửa đẩy việc xuống thread nền của màn chính (`KachiHomeActivity.submitBg`); `false` = đã huỷ. */
    private val submitBg: (() -> Unit) -> Boolean,
    /** MỘT lượt dò (chặn, thread nền). `null` = kênh lên được. Tiêm vào để test off-device. */
    private val probe: () -> LocalShellFailure? = { ShellApprovalProbe.probe(activity) },
    /** Đường nối dây ĐÃ CÓ từ trước F4 — chạy trên thread nền, đúng một lần. */
    private val onChannelUp: () -> Unit,
    /** Vòng kiểm quyền khi CHƯA có kênh; `awaiting` = đang chờ người dùng bấm. Thread nền, một lần mỗi ca. */
    private val onReport: (awaiting: Boolean) -> Unit,
) {

    /**
     * F4 — hệ thống đang hỏi *"Cho phép gỡ lỗi USB?"*. Màn Cài đặt đọc cờ này để hàng *Kênh điều khiển cửa sổ* nói
     * **việc người dùng cần làm** thay cho "hạn chế môi trường" (xem `LauncherRequirements.SHELL_CHANNEL_AWAITING_APPROVAL`).
     */
    @Volatile var awaitingApproval: Boolean = false
        private set

    private var framed = false
    private var focused = false
    private var showing = true
    private var scheduled = false
    private var inFlight = false
    private var channelUp = false
    private var reportedAwaiting = false
    private var reportedEnvironment = false
    private var banner: View? = null

    private val attemptRunnable = Runnable { attempt() }

    /**
     * Gọi ở cuối `onCreate`. `decorView.post` chạy sau khi cây view được gắn và lượt dựng đầu đã lên hàng đợi —
     * tức mốc "đã có khung đầu", không phải một con số đoán.
     */
    fun arm() {
        activity.window.decorView.post {
            framed = true
            schedule(FirstOpenApproval.SETTLE_MS)
        }
    }

    /** `onStart` — màn hiện lại thì vòng dò chạy tiếp (người dùng có thể vừa bấm Cho phép ở hộp thoại). */
    fun onShown() {
        showing = true
        schedule(FirstOpenApproval.SETTLE_MS)
    }

    /**
     * `onStop`/`onDestroy` — dừng hẳn: không hẹn lượt mới, và lượt đã hẹn bị gỡ.
     *
     * Lượt đang chạy dở (nếu có) vẫn kết thúc trên thread nền rồi báo về; [attempt] kiểm [showing] lần nữa nên nó
     * không hẹn tiếp. **Không** đóng ép kết nối đang treo: hộp thoại cấp quyền gắn với chính kết nối đó.
     */
    fun onHidden() {
        showing = false
        scheduled = false
        handler.removeCallbacks(attemptRunnable)
    }

    /**
     * `onWindowFocusChanged`. Mất tiêu điểm = một cửa sổ khác đang ở trên — ở ca này rất có thể chính là hộp thoại
     * ta vừa dựng ⇒ [FirstOpenApproval.attemptAllowed] cấm bắn lượt mới (không dựng hộp thoại chồng hộp thoại).
     * Lấy lại tiêu điểm ⇒ soi lại NGAY (người dùng vừa bấm xong là dải nhắc biến mất trong ~1,5 s).
     */
    fun onFocus(hasFocus: Boolean) {
        focused = hasFocus
        if (!hasFocus) return
        // Lấy lại tiêu điểm = cửa sổ nằm trên vừa biến mất — rất có thể chính là hộp thoại người dùng vừa trả lời.
        // Dời lượt ĐÃ HẸN lên sớm (thay vì ngồi hết phần còn lại của nhịp 20 s) chính là thứ làm dải nhắc biến mất
        // ngay sau khi họ bấm. Chỉ DỜI lịch, không tự bắn: [attempt] vẫn phải qua cổng vòng đời + tiêu điểm. Không
        // đụng vào lượt đang chạy dở (`inFlight`) — nó sẽ tự báo kết quả về.
        if (!inFlight) {
            handler.removeCallbacks(attemptRunnable)
            scheduled = false
        }
        schedule(FirstOpenApproval.SETTLE_MS)
    }

    /** Nút *Thử lại* trên dải nhắc — bỏ qua phần còn lại của nhịp 20 s, không bỏ qua cổng vòng đời. */
    fun retryNow() {
        handler.removeCallbacks(attemptRunnable)
        scheduled = true
        handler.post(attemptRunnable)
    }

    private fun schedule(delayMs: Long) {
        if (channelUp || scheduled || !showing || !framed) return
        scheduled = true
        handler.postDelayed(attemptRunnable, delayMs)
    }

    private fun attempt() {
        scheduled = false
        if (channelUp || inFlight) return
        // Chưa được phép bắn (hộp thoại đang ở trên / màn vừa khuất) ⇒ hẹn lượt sau, không bỏ cuộc.
        if (!FirstOpenApproval.attemptAllowed(showing, focused)) {
            if (showing) schedule(FirstOpenApproval.RETRY_EVERY_MS)
            return
        }
        inFlight = true
        val queued = submitBg {
            val reason = probe()
            handler.post { settle(reason) }
        }
        if (!queued) inFlight = false   // thread nền đã tắt (màn huỷ) ⇒ nhả chốt, không kẹt vĩnh viễn
    }

    private fun settle(reason: LocalShellFailure?) {
        inFlight = false
        when (val step = FirstOpenApproval.step(reason, showing)) {
            is FirstOpenStep.ChannelUp -> {
                channelUp = true
                awaitingApproval = false
                hideBanner()
                handler.removeCallbacks(attemptRunnable)
                scheduled = false
                submitBg { onChannelUp() }
            }
            is FirstOpenStep.AwaitingUser -> {
                awaitingApproval = true
                showBanner()
                // Vòng kiểm quyền chạy MỘT lần cho ca này: nó chỉ đọc trạng thái (không cần kênh shell) nên vẫn có
                // ích (nhật ký + hàng quyền), nhưng chạy lại mỗi 20 s là ghi log rác suốt chuyến.
                if (!reportedAwaiting) {
                    reportedAwaiting = true
                    submitBg { onReport(true) }
                }
                if (step.retryAfterMs > FirstOpenApproval.NO_RETRY) schedule(step.retryAfterMs)
            }
            is FirstOpenStep.Environment -> {
                awaitingApproval = false
                hideBanner()
                if (!reportedEnvironment) {
                    reportedEnvironment = true
                    submitBg { onReport(false) }
                }
            }
        }
    }

    // ── Dải nhắc: nói đúng một việc, không chặn thao tác nào ─────────────────────────────────────────

    private fun showBanner() {
        if (banner != null || activity.isFinishing || activity.isDestroyed) return
        val view = approvalBanner(activity) { retryNow() }
        banner = view
        host.addView(
            view,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL,
            ).also { it.bottomMargin = KachiTheme.dpi(activity, Sp.XL) },
        )
    }

    private fun hideBanner() {
        banner?.let { host.removeView(it) }
        banner = null
    }
}

/**
 * Dải nhắc "hệ thống đang hỏi Cho phép gỡ lỗi USB".
 *
 * ## Ba tính chất, mỗi cái vì một lý do
 *  - **WRAP_CONTENT + neo đáy**: nó là một mẩu tin, không phải một lớp phủ. Không có nền phủ toàn màn, không
 *    `setOnTouchListener{true}` — mọi cú chạm ra ngoài khung chữ vẫn tới thẳng màn chính. Đây là xe đang lăn bánh:
 *    một dải nhắc **không bao giờ** được đứng giữa người lái và cái nút họ định bấm.
 *  - **Không có nút đóng**: điều kiện chưa được giải quyết thì tin vẫn đúng; đóng nó đi chỉ giấu mất lý do launcher
 *    không đưa app vào ô được. Nó tự biến mất đúng lúc kênh lên ([ShellChannelGate.settle]).
 *  - **Chữ lấy từ `res`** (VI/EN) và màu lấy từ [KachiTheme] — không chuỗi cứng, không mã màu cứng.
 */
private fun approvalBanner(ctx: Context, onRetry: () -> Unit): View = LinearLayout(ctx).apply {
    orientation = LinearLayout.HORIZONTAL
    gravity = Gravity.CENTER_VERTICAL
    background = KachiTheme.surface(ctx, Sp.RADIUS_XL)
    val padX = KachiTheme.dpi(ctx, Sp.L)
    val padY = KachiTheme.dpi(ctx, Sp.M)
    setPadding(padX, padY, padX, padY)
    addView(
        TextView(ctx).apply {
            text = ctx.getString(R.string.kachi_shell_approval_msg)
            setTextColor(KachiTheme.c(KachiTheme.AMBER))
            KachiType.apply(this, KachiType.BODY)
            maxWidth = KachiTheme.dpi(ctx, Sp.NOTE_MAX_W)
        },
    )
    addView(
        TextView(ctx).apply {
            text = ctx.getString(R.string.kachi_shell_approval_retry)
            setTextColor(KachiTheme.c(KachiTheme.INK))
            KachiType.apply(this, KachiType.BODY, bold = true)
            gravity = Gravity.CENTER
            background = KachiTheme.surface(ctx, Sp.RADIUS_XL)
            val bx = KachiTheme.dpi(ctx, Sp.L)
            val by = KachiTheme.dpi(ctx, Sp.S)
            setPadding(bx, by, bx, by)
            minHeight = KachiTheme.dpi(ctx, Sp.TOUCH)
            setOnClickListener { onRetry() }
        },
        LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).also { it.marginStart = KachiTheme.dpi(ctx, Sp.M) },
    )
}

/**
 * MỘT lượt dò kênh shell, có hạn đọc và **kết nối mới mỗi lượt**.
 *
 * Đi qua [LocalDeviceShell.sessionResult] (không qua `ShellTransport`) vì hai lý do:
 *  1. `ShellTransport` cố ý dùng `Dadb.create(host, port, keys)` **không hạn đọc** — đúng cho lệnh cửa sổ, nhưng ở
 *     lần mở đầu thì đó chính là thứ làm luồng nền treo vĩnh viễn khi adbd im lặng ([ĐO] `Recv-Q` dâng).
 *  2. Nó **tái dùng** một kết nối; lượt dò thì cần bỏ hẳn phiên cũ đang kẹt và mở phiên mới (mỗi lượt = một lần
 *     hỏi mới). [FirstOpenApproval.PROBE] có `attempts = 1` nên mỗi lời gọi mở-và-đóng đúng một phiên.
 *
 * Lệnh dò dùng lại đúng câu `echo kachi_ok` mà `ShellTransport.probe()` dùng — một câu, hai chỗ, cùng ý nghĩa
 * "shell thật sự chạy" (không phát minh phép thử thứ hai).
 */
internal object ShellApprovalProbe {

    const val PROBE_CMD = "echo kachi_ok"
    private const val TOKEN = "kachi_ok"

    /** `null` = kênh lên được; khác `null` = lý do đã phân loại. ⚠ CHẶN — chỉ gọi trên thread nền. */
    fun probe(ctx: Context): LocalShellFailure? {
        val keys = runCatching { AdbKeys.ensure(ctx) }.getOrNull() ?: return LocalShellFailure.UNKNOWN
        val result = LocalDeviceShell.sessionResult(keys, FirstOpenApproval.PROBE) { sh ->
            sh(PROBE_CMD).output.contains(TOKEN)
        }
        return when (result) {
            // Nối được mà `echo` không vọng lại ⇒ có kênh nhưng không chạy được lệnh: đó là hỏng ở tầng IO, KHÔNG
            // phải "đang chờ người bấm" (bắt tay đã xong thì hộp thoại đã được trả lời).
            is LocalShellResult.Ok -> if (result.value) null else LocalShellFailure.IO_ERROR
            is LocalShellResult.Failed -> result.reason
        }
    }
}
