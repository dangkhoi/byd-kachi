package com.byd.clusternav.launcher

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.os.PowerManager
import com.byd.clusternav.AdbKeys
import com.byd.clusternav.EarlyShellChannel
import com.byd.clusternav.KachiReadyLog
import com.byd.clusternav.KeyReady
import com.byd.clusternav.R
import com.byd.clusternav.ShellReadiness
import com.byd.clusternav.carexec.FirstOpenApproval
import com.byd.clusternav.carexec.FirstOpenStep
import com.byd.clusternav.carexec.LocalDeviceShell
import com.byd.clusternav.carexec.LocalShellAdmission
import com.byd.clusternav.carexec.LocalShellFailure
import com.byd.clusternav.carexec.LocalShellResult
import com.byd.clusternav.carexec.LocalShellRetry
import com.byd.clusternav.carexec.ShellChannelPhase
import com.byd.clusternav.carexec.ShellReadinessPolicy
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
    private val probe: () -> LocalShellFailure? = { ShellApprovalProbe.probeForHome(activity) },
    /** Đường nối dây ĐÃ CÓ từ trước F4 — chạy trên thread nền, một lần mỗi lần nhận kênh (hỏng ⇒ [wiringFailed]). */
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
            adopt()   // READY-AT-HOME §4.7 — kênh đã SẴN ở tầng tiến trình ⇒ nhận ngay, không dò lại
            schedule(FirstOpenApproval.SETTLE_MS)
        }
    }

    /** `onStart` — màn hiện lại thì vòng dò chạy tiếp (người dùng có thể vừa bấm Cho phép ở hộp thoại). */
    fun onShown() {
        showing = true
        adopt()   // READY-AT-HOME §4.7 — màn bật lại sau lượt tắt máy ⇒ kênh đã sẵn từ lúc màn tắt
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
        adopt()   // READY-AT-HOME §4.7
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
        // READY-AT-HOME · review lượt 1 [P2]: màn ĐÃ nối dây (`channelUp`) mà kênh mức tiến trình rơi khỏi UP (một phiên
        // HỎI sau đó — vd phím mic — gặp adbd hỏi lại / từ chối ⇒ NEEDS_APPROVAL, thẻ MẤT DUYỆT hiện) thì [attempt] thoát
        // ngay ở `channelUp` ⇒ nút *Hỏi lại* CHẾT. Người dùng vừa bấm trong HOME (có tiêu điểm — R1.1 nơi thứ hai) ⇒ hỏi
        // bằng một phiên hạn đọc 30 s ([ShellApprovalProbe.askUser]); KHÔNG chạy lại [onChannelUp] (dây đã nối rồi).
        if (channelUp) {
            if (ShellReadiness.isUp() || inFlight) return
            inFlight = true
            val queued = submitBg {
                try {
                    ShellApprovalProbe.askUser(activity)
                } finally {
                    handler.post { inFlight = false }
                }
            }
            if (!queued) inFlight = false
            return
        }
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
                // READY-AT-HOME · review lượt 2 (ân hạn khởi động) [P3]: giữ ô như [adopt] — tiến trình DỰNG LẠI lúc màn sáng
                // nay cũng có lượt có thể force-stop (KHOI_DONG, R2.7); F4 thắng đua khi lượt sớm chậm hơn tiêu điểm + 1,5 s.
                submitBg { KeyReady.holdTileIfEscalating(activity.applicationContext); onChannelUp() }
                ShellReadiness.reportUp("f4")   // READY-AT-HOME §4.7 — phép đo của F4 cũng là sự thật mức tiến trình
                KachiReadyLog.line("home f4")
            }
            is FirstOpenStep.AwaitingUser -> {
                ShellReadiness.reportNeedsApproval("f4")   // READY-AT-HOME §4.5 — thẻ xin quyền + ô nói đúng việc
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
                ShellReadiness.reportEnvironment(step.reason, "f4")   // READY-AT-HOME §4.5 (đã UP thì giữ UP)
                awaitingApproval = false
                hideBanner()
                if (!reportedEnvironment) {
                    reportedEnvironment = true
                    submitBg { onReport(false) }
                }
                // READY-AT-HOME · review lượt 1–2 [P3]: cổng 5555 đóng ⇒ F4 tự hẹn dò lại (luật thuần
                // [ShellReadinessPolicy.envReprobeMs] — chỉ PORT_CLOSED: không có kết nối tới adbd ⇒ không thể dựng hộp).
                val again = ShellReadinessPolicy.envReprobeMs(step.reason)
                if (again > 0L) schedule(again)
            }
        }
        // READY-AT-HOME · review lượt 2 [P3]: kênh có thể đã lên ở tầng tiến trình TRONG lúc lượt này bay (một phiên nền
        // khác bắt tay xong) — bên nghe gọi [adopt] lúc đó bị hoãn vì `inFlight`, và không có sự kiện đổi trạng thái nào
        // gọi lại. Lượt này trả MÔI TRƯỜNG (lỗi lệnh không hạ UP — bảng §4.5) ⇒ nhận ngay ở đây. ChannelUp/AwaitingUser ⇒
        // [adopt] tự bỏ (đã nhận / không còn UP).
        adopt()
    }

    // ── READY-AT-HOME §4.7 — nhận kênh ĐÃ SẴN ở tầng tiến trình ─────────────────────────────────────────

    /**
     * Kênh đã ĐO là lên trong tiến trình này ([ShellReadiness], vd lượt sớm lúc màn tắt) ⇒ chạy NGAY đường nối dây cũ
     * ([onChannelUp] — thân `bringUpShellChannel` không đổi), không chờ tiêu điểm, không chờ 1,5 s, không dò lại.
     *
     * Vì sao bỏ được tiêu điểm + 1,5 s: F4 cần hai điều đó CHỈ để hộp "Cho phép gỡ lỗi USB?" sống ([ĐO] 14/09). Khi
     * kênh đã lên, khoá vừa được nhận trong tiến trình ⇒ không lần nối nào sinh hộp nữa (adbd chỉ hỏi khi nhận khoá công
     * khai — [ĐO AOSP] spec §2.5). Chưa lên ⇒ hàm này không làm gì, F4 y như cũ.
     *
     * Điều kiện ở [ShellReadinessPolicy.adoptAllowed] (thuần, có test): kênh UP + khung đã vẽ + màn đang hiện + màn
     * TƯƠNG TÁC (lượt tắt máy: HOME tạo rồi dừng trong 0,3 s [ĐO] — nhận lúc đó là app ô có tiếng chạy lúc xe tắt) +
     * chưa nhận + không có lượt F4 đang bay (lượt đó tự chạy [onChannelUp] — nhận thêm là chạy HAI lần).
     *
     * Trước [onChannelUp]: [KeyReady.holdTileIfEscalating] — lớp 2 (2.83) đang xét một lần mở xe mà phím chưa gắn thì ô
     * CHƯA gắn app (tối đa 8 s, fail-open) ⇒ lượt force-stop của lớp 2 không làm app ô mở hai lần (R-A2 của 2.83).
     */
    fun adopt() {
        if (!ShellReadinessPolicy.adoptAllowed(ShellReadiness.isUp(), framed, showing, interactiveNow(), channelUp, inFlight)) return
        channelUp = true
        awaitingApproval = false
        hideBanner()
        handler.removeCallbacks(attemptRunnable)
        scheduled = false
        val app = activity.applicationContext
        EarlyShellChannel.homeVisible(app)   // mốc màn bật sớm nhất + chuỗi SẴN (kiểm phím) chạy song song, không chặn ô
        KachiReadyLog.line("home adopt")
        val queued = submitBg { KeyReady.holdTileIfEscalating(app); onChannelUp() }
        if (!queued) channelUp = false   // thread nền đã tắt (màn huỷ) ⇒ không kẹt cờ
    }

    /**
     * READY-AT-HOME · review lượt 3 [P2] — đường nối dây ([onChannelUp], thân `bringUpShellChannel` không đổi) đã chạy mà
     * màn chính vẫn KHÔNG có kênh (nhánh `dadb.probe()` hỏng: cổng 5555 chưa mở lúc màn bật, kết nối cũ chết sau giấc
     * ngủ, hoặc cổng thi hành vừa chặn vì kênh rơi khỏi UP). Trước bản vá cờ [channelUp] kẹt `true` ⇒ không lượt F4 nào
     * chạy lại, [adopt] tự bỏ, nút *Thử lại* của thẻ thoát ở nhánh `channelUp` ⇒ ô nằm "Đang kết nối…" tới khi màn chính
     * bị dựng lại. Khoảng hở rộng ra từ READY-AT-HOME: [adopt] nối dây lúc màn BẬT dựa trên phép đo của lượt sớm có thể từ
     * lúc màn TẮT (trước đó F4 dò xong là nối dây ngay). Có xảy ra trên xe không: [CHƯA BIẾT].
     *
     * Nay: nhả cờ rồi hẹn lượt F4 sau [FirstOpenApproval.RETRY_EVERY_MS] — cùng nhịp vòng F4, cùng cổng vòng đời + tiêu
     * điểm, [onHidden] dừng. Kênh vẫn UP ⇒ [ShellApprovalProbe.probeForHome] không dò lại ⇒ lượt đó chạy lại đúng
     * [onChannelUp]; kênh đã rơi khỏi UP ⇒ F4 hỏi như thường. Gọi từ thread nền (cuối `onChannelUp` của màn chính).
     */
    fun wiringFailed() {
        handler.post {
            if (!channelUp) return@post
            channelUp = false
            KachiReadyLog.line("home wiring failed -> f4 in=${FirstOpenApproval.RETRY_EVERY_MS}ms")
            schedule(FirstOpenApproval.RETRY_EVERY_MS)
        }
    }

    private fun interactiveNow(): Boolean =
        (activity.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isInteractive == true

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

    /**
     * READY-AT-HOME §4.7 — lượt dò của MÀN CHÍNH (F4). Lượt sớm của tiến trình đang bay ⇒ chờ nó (≤ 8 s) thay vì mở
     * kết nối thứ hai (với khoá đã bị thu hồi, hai kết nối = hộp chồng hộp [CHƯA BIẾT F2 §4.3]). Kênh đã ĐO là lên trong
     * tiến trình ⇒ `null` (sự thật đo được, không dò lại). Còn lại ⇒ đúng lượt dò cũ [probe].
     */
    fun probeForHome(ctx: Context): LocalShellFailure? {
        val s = ShellReadiness.awaitSettled(ShellReadinessPolicy.EARLY_WAIT_MS)
        if (s.phase == ShellChannelPhase.UP) return null
        return LocalShellAdmission.labeled("f4") { probe(ctx) }
    }

    /**
     * READY-AT-HOME — lượt HỎI do người dùng BẤM (*Hỏi lại* / *Thử lại*) khi màn chính ĐÃ nối dây ([ShellChannelGate.retryNow]
     * nhánh `channelUp`): một phiên [LocalShellRetry.USER_READ_CAP] (hạn đọc 30 s, nối lười) để hộp "Cho phép gỡ lỗi
     * USB?" bung ra và được trả lời TRONG cùng phiên — không có vòng F4 nào chạy lại ở trạng thái này. Kết quả tự báo về
     * `ShellReadiness` qua cổng thi hành (bắt tay xong ⇒ UP) — nên hàm không trả gì. ⚠ CHẶN — chỉ gọi trên thread nền.
     */
    fun askUser(ctx: Context) {
        val keys = runCatching { AdbKeys.ensure(ctx) }
            .onFailure { android.util.Log.w("ShellApprovalProbe", "AdbKeys.ensure: ${it.javaClass.simpleName}: ${it.message}") }
            .getOrNull() ?: return
        val result = LocalShellAdmission.labeled("ask") {
            LocalDeviceShell.sessionResult(keys, LocalShellRetry.USER_READ_CAP) { sh -> sh(PROBE_CMD) }
        }
        android.util.Log.i("ShellApprovalProbe", "hỏi lại (màn đã nối dây): ${if (result is LocalShellResult.Failed) result.reason else "UP"}")
    }

    /** `null` = kênh lên được; khác `null` = lý do đã phân loại. ⚠ CHẶN — chỉ gọi trên thread nền. */
    fun probe(ctx: Context): LocalShellFailure? {
        // Audit F7 (2026-09-25): "sinh khoá hỏng" từng bị gộp im vào UNKNOWN — cùng bệnh F4 (đi sửa nhầm kênh).
        val keys = runCatching { AdbKeys.ensure(ctx) }
            .onFailure { android.util.Log.w("ShellApprovalProbe", "AdbKeys.ensure: ${it.javaClass.simpleName}: ${it.message}") }
            .getOrNull() ?: return LocalShellFailure.UNKNOWN
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
