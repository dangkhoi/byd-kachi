package com.byd.clusternav.launcher

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.byd.clusternav.KachiReadyLog
import com.byd.clusternav.R
import com.byd.clusternav.ShellReadiness
import com.byd.clusternav.carexec.AccessCardVariant
import com.byd.clusternav.carexec.ShellChannelPhase
import com.byd.clusternav.carexec.ShellReadinessPolicy
import com.byd.clusternav.carexec.ShellReadinessState
import com.byd.clusternav.carexec.SlotTapStep
import com.byd.clusternav.carexec.TileHint
import java.lang.ref.WeakReference
import com.byd.clusternav.launcher.KachiSpace as Sp

/**
 * ═══ READY-AT-HOME R1 — "KHÔNG CÓ QUYỀN THÌ KHÔNG DÙNG ĐƯỢC APP, VÀ NÓI RÕ VÌ SAO" (spec §4.9) ═══════════════════
 *
 * Owner 2026-10-01: *"Xe chưa từng cho phép phải hiện popup yêu cầu, không có quyền, không dùng đc app"*. Popup THẬT là
 * hộp hệ thống "Cho phép gỡ lỗi USB?" (F4 dựng khi HOME ở trước + có tiêu điểm). Thẻ ở đây GIẢI THÍCH và có nút
 * *Hỏi lại* — nó là view trong cửa sổ của HOME nên LUÔN nằm dưới hộp hệ thống, không bao giờ đè lên nó.
 *
 * ## Ba tính chất giữ y như dải nhắc F4 (`ShellChannelGate.approvalBanner`)
 *  - WRAP_CONTENT, căn giữa — một mẩu tin, không phải lớp phủ: không nền phủ toàn màn; chạm ra ngoài khung tới thẳng ô và
 *    thanh nút (xe đang lăn bánh). Trong khung thì thẻ nhận chạm (`isClickable`) để cú chạm nhầm không lọt xuống ô dưới.
 *  - Chữ lấy từ `res` (VI/EN), màu từ [KachiTheme].
 *  - Tự biến mất khi kênh lên.
 *
 * ## Khi nào hiện
 *  - Tự hiện MỘT lần mỗi tiến trình khi HOME có tiêu điểm (= hộp hệ thống không ở trên) và kênh đã ĐO là cần duyệt
 *    (hoặc lỗi môi trường mà không có dấu tươi) — luật [ShellReadinessPolicy.autoShowCard].
 *  - Mỗi lần người dùng chạm một tính năng bị chặn ([slotTap], [allowOrPrompt]).
 *
 * ⚠ Mọi field chỉ chạm trên luồng CHÍNH.
 */
internal object ShellAccessUi {

    private const val TAG = "KachiAccess"

    /**
     * Màn chính đang sống — mọi trường là tham chiếu YẾU (object này sống bằng tiến trình; màn chính thì không). Bản
     * thân [Host] được giữ MẠNH (giữ yếu thì nó bị thu gom ngay, vì không ai khác trỏ tới nó) và được gỡ ở [detach].
     */
    private class Host(activity: Activity, root: FrameLayout, gate: ShellChannelGate) {
        val activity = WeakReference(activity)
        val root = WeakReference(root)
        val gate = WeakReference(gate)
        fun alive(): Activity? = activity.get()?.takeIf { !it.isFinishing && !it.isDestroyed }
    }

    private var host: Host? = null
    /** Thẻ đang hiện — tham chiếu YẾU: object sống bằng tiến trình không được giữ View của một màn đã chết. */
    private var card: WeakReference<View>? = null
    private var autoShown = false
    /** Lượt chạm ô đang chờ kênh (mốc), `-1` = không có. */
    private var tapWaitSince = -1L
    private val main = Handler(Looper.getMainLooper())
    private val tapCheck = Runnable { checkPendingTap() }
    @Volatile private var embeddedNow: () -> Boolean = { false }
    private var lastToastAt = Long.MIN_VALUE / 2

    /** Gắn màn chính đang sống (`wireReadyAtHome`). */
    fun attach(activity: Activity, root: FrameLayout, gate: ShellChannelGate) {
        host = Host(activity, root, gate)
    }

    /** Màn chính huỷ ⇒ gỡ thẻ + quên host (không giữ Activity chết). */
    fun detach(activity: Activity) {
        val h = host
        if (h != null && h.activity.get() !== activity) return
        hideCard()
        main.removeCallbacks(tapCheck)
        main.removeCallbacks(focusCheck)
        tapWaitSince = -1L
        // Review lượt 1 [P3]: lambda này đóng trên `LauncherWindows` của màn vừa huỷ (giữ Activity chết tới lần chạm sau).
        embeddedNow = { false }
        host = null
    }

    /** Trạng thái kênh đổi (luồng chính). Lên ⇒ thẻ biến mất. Đã ĐO là cần duyệt + màn có tiêu điểm ⇒ tự hiện một lần. */
    fun onState(s: ShellReadinessState, focused: Boolean) {
        if (s.phase == ShellChannelPhase.UP) {
            fadeOutCard(); tapWaitSince = -1L; main.removeCallbacks(tapCheck)
            return
        }
        maybeAutoShow(s, focused)
        if (tapWaitSince >= 0 && ShellReadinessPolicy.slotTap(s.phase, 0) == SlotTapStep.PROMPT) checkPendingTap()
    }

    /**
     * HOME vừa lấy lại tiêu điểm (hộp hệ thống vừa đóng) — luồng chính. KHÔNG hiện ngay: rất có thể người dùng vừa bấm
     * Cho phép và lượt F4 (1,5 s yên + một lượt dò) sắp báo kênh lên — hiện ngay là thẻ "cần quyền" nháy lên ngay sau
     * khi họ vừa cấp. Hỏi lại sau [FOCUS_SETTLE_MS]: kênh đã lên ⇒ thôi; hộp lại bung (mất tiêu điểm) ⇒ thôi.
     */
    fun onFocus(focused: Boolean) {
        main.removeCallbacks(focusCheck)
        if (focused) main.postDelayed(focusCheck, FOCUS_SETTLE_MS)
    }

    private val focusCheck = Runnable {
        maybeAutoShow(ShellReadiness.state(), host?.root?.get()?.hasWindowFocus() == true)
    }

    private fun maybeAutoShow(s: ShellReadinessState, focused: Boolean) {
        val fresh = s.phase == ShellChannelPhase.ENVIRONMENT && ShellReadiness.ledgerFresh()
        val interactive = host?.alive()?.let { (it.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isInteractive } == true
        if (!ShellReadinessPolicy.autoShowCard(s.phase, fresh, focused, interactive, autoShown)) return
        autoShown = true
        showCard()
    }

    /**
     * R1.3/R2.4 — chạm ô CHƯA có bộ chiếu (thay cho mở cửa sổ nổi). State ô đã được lưu trước đó (`assignApp`), nên
     * kênh lên là `applyEmbedSeam` tự nhúng; ở đây chỉ quyết: chờ (ô "Đang kết nối…") hay hiện thẻ ngay.
     */
    fun slotTap(embedded: () -> Boolean) {
        embeddedNow = embedded
        when (ShellReadinessPolicy.slotTap(ShellReadiness.phase(), 0)) {
            SlotTapStep.PROMPT -> prompt(null)
            SlotTapStep.WAIT -> {
                if (tapWaitSince < 0) tapWaitSince = SystemClock.elapsedRealtime()
                main.removeCallbacks(tapCheck)
                main.postDelayed(tapCheck, ShellReadinessPolicy.SLOT_WAIT_MS)
                KachiReadyLog.line("slot tap queued phase=${ShellReadiness.phase()}")
            }
        }
    }

    private fun checkPendingTap() {
        if (tapWaitSince < 0) return
        if (embeddedNow()) { tapWaitSince = -1L; return }
        val waited = SystemClock.elapsedRealtime() - tapWaitSince
        if (ShellReadinessPolicy.slotTap(ShellReadiness.phase(), waited) == SlotTapStep.PROMPT) {
            tapWaitSince = -1L
            main.removeCallbacks(tapCheck)
            prompt(null)
        }
    }

    /**
     * Điểm vào của một tính năng CẦN kênh (chiếu cụm, DPI, Đặt HOME…): dùng được ngay ⇒ `true`; không ⇒ lời nhắc + `false`.
     * Quên gọi ở một điểm vào thì cổng thi hành (`LocalShellAdmission`) vẫn chặn — đây chỉ là phần NÓI.
     */
    fun allowOrPrompt(ctx: Context): Boolean {
        if (usableNow()) return true
        main.post { prompt(ctx.applicationContext) }
        return false
    }

    /**
     * Kênh dùng được NGAY (cùng phép [allowOrPrompt], KHÔNG nhắc) — cho chỗ chỉ VẼ theo kênh (icon lối tắt mờ khi kênh
     * không dùng được, spec shortcuts-autostart R1.5) và cho bảng chạm `ShortcutPlan` (`usable`). Tách ra để hai chỗ
     * dùng MỘT phép, không chép hai dòng điều kiện (global §4.1 DRY).
     */
    fun usableNow(): Boolean {
        val p = ShellReadiness.phase()
        val fresh = p != ShellChannelPhase.UP && p != ShellChannelPhase.NEEDS_APPROVAL && ShellReadiness.ledgerFresh()
        return ShellReadinessPolicy.usable(p, fresh)
    }

    /** Hiện thẻ nếu màn chính đang sống; không thì một thông báo ngắn (tiết chế 5 s). */
    private fun prompt(ctx: Context?) {
        if (host?.alive() != null) { showCard(); return }
        val c = ctx ?: return
        val now = SystemClock.elapsedRealtime()
        if (now - lastToastAt < TOAST_GAP_MS) return
        lastToastAt = now
        try {
            Toast.makeText(c, R.string.kachi_access_feature_blocked, Toast.LENGTH_LONG).show()
        } catch (e: RuntimeException) {
            Log.w(TAG, "không hiện được lời nhắc quyền: ${e.message}")
        }
    }

    private fun showCard() {
        val h = host ?: return
        val activity = h.alive() ?: return
        val root = h.root.get() ?: return
        val variant = ShellReadinessPolicy.cardVariant(ShellReadiness.state(), ShellReadiness.hadRecord())
        hideCard()
        val v = accessCard(activity, variant, onLater = { hideCard() }, onRetry = { hideCard(); h.gate.get()?.retryNow() })
        card = WeakReference(v)
        root.addView(
            v,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER,
            ),
        )
        KachiReadyLog.line("card show variant=$variant")
    }

    private fun hideCard() {
        val v = card?.get()
        card = null
        (v?.parent as? FrameLayout)?.removeView(v)
    }

    /**
     * Kênh TỰ lên (không do người dùng bấm) ⇒ thẻ mờ dần rồi mới gỡ. Review lượt 1 [P3] (E2E 02/10 ca 3b): thẻ biến mất
     * tức thì lúc kênh lên, cú chạm *Hỏi lại* đang trên đường tới (≈ 0,5 s sau) rơi xuống ô đồng hồ bên dưới và mở bảng
     * "Đặt widget…". Trong lúc mờ, khung thẻ VẪN nhận chạm (`isClickable`) nên cú chạm muộn bị nuốt; hai nút lúc đó vô
     * hại (*Để sau* = gỡ; *Hỏi lại* = `retryNow`, kênh đã lên ⇒ không làm gì).
     */
    private fun fadeOutCard() {
        val v = card?.get() ?: return
        card = null
        v.animate().alpha(0f).setDuration(FADE_OUT_MS).withEndAction { (v.parent as? FrameLayout)?.removeView(v) }.start()
    }

    /** Chữ theo trạng thái thay "Chạm để mở" trên ô chưa có bộ chiếu — TỰ cập nhật khi trạng thái kênh đổi. */
    fun tileHint(ctx: Context): TextView = TextView(ctx).apply {
        setTextColor(KachiTheme.c(KachiTheme.MUT)); KachiType.apply(this, KachiType.CAPTION)
        gravity = Gravity.CENTER; setPadding(0, KachiTheme.dpi(ctx, Sp.XS), 0, 0)
        val set = { s: ShellReadinessState -> setText(hintRes(ShellReadinessPolicy.tileHint(s.phase))) }
        set(ShellReadiness.state())
        // Review lượt 3 [P3]: đọc trạng thái MỚI NHẤT lúc vẽ (bên nghe có thể tới ngược thứ tự từ hai luồng).
        val listener: (ShellReadinessState) -> Unit = { _ -> post { set(ShellReadiness.state()) } }
        addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) { ShellReadiness.addListener(listener); set(ShellReadiness.state()) }
            override fun onViewDetachedFromWindow(v: View) { ShellReadiness.removeListener(listener) }
        })
    }

    private fun hintRes(h: TileHint): Int = when (h) {
        TileHint.CONNECTING -> R.string.kachi_slot_connecting
        TileHint.NEEDS_ACCESS -> R.string.kachi_slot_needs_access
        TileHint.NO_CHANNEL -> R.string.kachi_slot_no_channel
    }

    private const val TOAST_GAP_MS = 5_000L

    /** Thẻ mờ dần khi kênh tự lên — đủ dài để nuốt cú chạm đang trên đường tới (≈ 0,5 s ở E2E 3b), xem [fadeOutCard]. */
    private const val FADE_OUT_MS = 700L

    /** `FirstOpenApproval.SETTLE_MS` (1,5 s) + một lượt dò bằng khoá đã duyệt (~0,5 s) + lề. */
    private const val FOCUS_SETTLE_MS = 2_500L
}

/**
 * Thẻ xin quyền — dựng thuần mã (khuôn `approvalBanner`). Ba biến thể lời (§4.9); nút phải nối [onRetry] (= F4 dò
 * ngay, VẪN qua cổng tiêu điểm) và [onLater] (thu về dải nhắc đáy sẵn có của F4).
 */
private fun accessCard(ctx: Context, variant: AccessCardVariant, onLater: () -> Unit, onRetry: () -> Unit): View =
    LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        background = KachiTheme.surface(ctx, Sp.RADIUS_XL)
        isClickable = true   // chạm TRONG khung không lọt xuống ô bên dưới; ngoài khung không bị đụng tới
        val pad = KachiTheme.dpi(ctx, Sp.XL)
        setPadding(pad, pad, pad, pad)
        val (title, body, retry) = when (variant) {
            AccessCardVariant.NEVER -> Triple(R.string.kachi_access_title, R.string.kachi_access_never_body, R.string.kachi_access_ask_again)
            AccessCardVariant.LOST -> Triple(R.string.kachi_access_lost_title, R.string.kachi_access_lost_body, R.string.kachi_access_ask_again)
            AccessCardVariant.ENVIRONMENT -> Triple(R.string.kachi_access_env_title, R.string.kachi_access_env_body, R.string.kachi_shell_approval_retry)
        }
        addView(TextView(ctx).apply {
            setText(title); setTextColor(KachiTheme.c(KachiTheme.INK)); KachiType.apply(this, KachiType.SECTION, bold = true)
            maxWidth = KachiTheme.dpi(ctx, Sp.NOTE_MAX_W)
        })
        addView(TextView(ctx).apply {
            setText(body); setTextColor(KachiTheme.c(KachiTheme.AMBER)); KachiType.apply(this, KachiType.BODY)
            maxWidth = KachiTheme.dpi(ctx, Sp.NOTE_MAX_W)
            setPadding(0, KachiTheme.dpi(ctx, Sp.S), 0, KachiTheme.dpi(ctx, Sp.M))
        })
        val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.END }
        row.addView(cardButton(ctx, R.string.kachi_access_later, bold = false, onClick = onLater))
        row.addView(
            cardButton(ctx, retry, bold = true, onClick = onRetry),
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .also { it.marginStart = KachiTheme.dpi(ctx, Sp.M) },
        )
        addView(row, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
    }

private fun cardButton(ctx: Context, text: Int, bold: Boolean, onClick: () -> Unit): TextView = TextView(ctx).apply {
    setText(text)
    setTextColor(KachiTheme.c(KachiTheme.INK))
    KachiType.apply(this, KachiType.BODY, bold = bold)
    gravity = Gravity.CENTER
    background = KachiTheme.surface(ctx, Sp.RADIUS_XL)
    val bx = KachiTheme.dpi(ctx, Sp.L)
    val by = KachiTheme.dpi(ctx, Sp.S)
    setPadding(bx, by, bx, by)
    minHeight = KachiTheme.dpi(ctx, Sp.TOUCH)
    setOnClickListener { onClick() }
}
