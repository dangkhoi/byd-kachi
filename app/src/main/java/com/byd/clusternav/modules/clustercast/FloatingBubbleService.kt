package com.byd.clusternav.modules.clustercast

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import com.byd.clusternav.Lang
import com.byd.clusternav.cast.platform.CastAppCatalog
import com.byd.clusternav.modules.clustercast.simplified.BubbleGesturePlanner
import com.byd.clusternav.modules.clustercast.simplified.BubblePresence
import com.byd.clusternav.modules.clustercast.simplified.SimpleCastRuntime
import com.byd.clusternav.modules.clustercast.simplified.SimpleCastState

/**
 * Presentation-only overlay host for the canonical Cast model. ONE app-icon glyph, three gestures.
 *
 * Delegates rendering to [BubbleRenderer], gesture disambiguation to [BubbleGestureHandler], action
 * dispatch to [BubbleActionDispatcher], and the long-press menu to [BubbleSubmenuOverlay]. This file
 * owns only: service lifecycle, window management, gesture→action wiring, state listener.
 *
 * ⚠ WP6 (2026-09-20) — tệp này từng giữ thêm ba khối KHÔNG thuộc bốn vai trên, và đã 537 dòng > trần 500
 * (CLAUDE.md §4.1). Ba khối đó nay ở tệp riêng, **không đổi một bước nào**: bộ tự-chiếu-khi-nổ-máy
 * ([BubbleAutostart], driver duy nhất R1) · chặn/trả PiP của GMaps-YouTube ([BubblePipGuard]) · thông báo thường
 * trú của FGS ([BubbleForegroundNotice]). Sau lượt tách, KDoc trên mô tả đúng thứ tệp này thật sự làm.
 *
 * Công tắc **HIỆN nút nổi** (WP6 · R6.1) gác đúng một thứ: có dựng cửa sổ hay không ([syncBubbleWindow]). Dịch vụ
 * vẫn chạy khi bị ẩn — nó còn là driver của tự-chiếu, nhịp giữ-cụm và nhịp áp lại bong bóng VietMap.
 */
class FloatingBubbleService : Service() {

    private val catalog: CastAppCatalog by lazy { CastAppCatalog(applicationContext) }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var renderer: BubbleRenderer
    private lateinit var gestureHandler: BubbleGestureHandler
    private lateinit var actionDispatcher: BubbleActionDispatcher

    private var windowManager: WindowManager? = null
    private var bubble: View? = null
    private var params: WindowManager.LayoutParams? = null
    /** Long-press submenu overlay (Trái/Phải/Cấu hình); created lazily, dismissed on destroy. */
    private var submenu: BubbleSubmenuOverlay? = null

    @Volatile private var destroyed = false
    private var foregroundStarted = false
    /** One-shot: the overlay settings screen is launched at most once per service start. */
    private var overlayRequested = false
    /**
     * Chặn PiP của GMaps/YouTube trong lúc dịch vụ sống, trả lại lúc chết — thân ở [BubblePipGuard] (tách ở WP6 vì
     * trần 500 dòng; khối đó không đọc trường nào của dịch vụ nên là chỗ cắt an toàn nhất).
     */
    private val pipGuard = BubblePipGuard()

    /**
     * Bộ **tự chiếu khi nổ máy** — driver DUY NHẤT (R1); thân ở [BubbleAutostart] (tách ở WP6 vì trần 500 dòng).
     * Dùng CHUNG [handler] với dịch vụ, nên `handler.removeCallbacksAndMessages(null)` ở [onDestroy] vẫn huỷ đúng
     * lượt mở-chiếu đang chờ; `detach` gỡ hai bộ nghe còn treo.
     */
    private val autostart by lazy { BubbleAutostart(applicationContext, handler) { destroyed } }

    /** State listener reference — stored so we can remove it on destroy. */
    private val stateListener: (SimpleCastState) -> Unit = { _ ->
        handler.post { refreshBubbleState() }
    }

    // Fade: dim when idle, bright on interaction or state change.
    private val fade = Runnable { setBubbleAlpha(IDLE_ALPHA) }
    private fun setBubbleAlpha(a: Float) {
        val layout = params ?: return
        val view = bubble ?: return
        if (layout.alpha == a) return
        layout.alpha = a
        runCatching { windowManager?.updateViewLayout(view, layout) }
    }
    private fun wakeBubble() {
        handler.removeCallbacks(fade)
        setBubbleAlpha(ACTIVE_ALPHA)
        handler.postDelayed(fade, FADE_DELAY_MS)
    }

    /** Periodic refresh as fallback; primary repaint is driven by state listener. */
    private val refresh = object : Runnable {
        override fun run() {
            // WP6 · R6.1 — công tắc "Hiện nút nổi" đọc lại mỗi nhịp (xem [syncBubbleWindow]); đặt TRƯỚC
            // [refreshBubbleState] để cửa sổ vừa dựng lại có nhãn trạng thái đúng ngay trong cùng nhịp.
            syncBubbleWindow()
            refreshBubbleState()
            // TRIAL (2026-08-14): re-pin a cast app that an external trigger (e.g. Kiki starting GMaps
            // navigation) pulled off the cluster. Cheap-gated + serial-executed inside the coordinator.
            runCatching { SimpleCastRuntime.coordinator(applicationContext).repinEscapedCastApps() }
            // Item 4: re-áp vị trí bong bóng VietMap ĐÃ LƯU mỗi nhịp (nếu VietMap-mod dựng lại bong bóng lúc
            // đang lái / ClusterNav chạy nền) → luôn TỰ về đúng vị trí owner đã chỉnh. send() tự gate Cast ON.
            runCatching { com.byd.clusternav.VmOverlayPosition.applyOnOpen(applicationContext) }
            if (!destroyed) handler.postDelayed(this, REFRESH_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        // Started via startForegroundService(): Android requires startForeground() within ~5s or it
        // kills us with RemoteServiceException. Call it FIRST, before any early stopSelf(); a clean
        // install's first launch has no overlay grant yet, so the overlay gate below would otherwise
        // bail before we ever went foreground. stopSelf() AFTER startForeground() is legal.
        if (!startForegroundOnce()) { stopSelf(); return }
        // Master OFF (default OFF, opt-in 2026-08-11) → stand down after startForeground (stopSelf legal): no projection/bubble/autostart; nav→cluster + HUD stay independent.
        if (!castEnabledNow()) { stopSelf(); return }
        // WP6 · R6.1 — ba nhánh, ba hậu quả khác nhau (lý do từng nhánh ở KDoc [BubblePresence]). Chỉ nhánh
        // NEEDS_OVERLAY_PERMISSION được đứng xuống: nhánh HIDDEN phải để dịch vụ SỐNG, vì nó còn là driver DUY
        // NHẤT của tự-chiếu-khi-nổ-máy (R1) + nhịp giữ-cụm + nhịp áp lại vị trí bong bóng VietMap.
        val presence = bubblePresence()
        if (presence == BubblePresence.NEEDS_OVERLAY_PERMISSION) { requestOverlayIfMissing(); stopSelf(); return }

        renderer = BubbleRenderer(this)
        gestureHandler = BubbleGestureHandler(
            context = this,
            handler = handler,
            onDragEnd = { x, y -> saveBubblePosition(x, y) },
            onWake = { wakeBubble() },
            onTap = { onBubbleTap() },
            onLongPress = { onBubbleLongPress() },
        )
        actionDispatcher = BubbleActionDispatcher(applicationContext, handler, ::toast)

        // ⚠ Dựng ba bộ trên VÔ ĐIỀU KIỆN, kể cả nhánh HIDDEN: owner bật lại công tắc giữa chuyến thì [syncBubbleWindow]
        // gọi `showBubble()` từ nhịp 2 giây, và nó cần `renderer`/`gestureHandler` đã có (`lateinit`).
        if (presence == BubblePresence.SHOW) showBubble()

        // Register named state listener (removable on destroy).
        val coordinator = SimpleCastRuntime.coordinator(applicationContext)
        coordinator.addStateListener(stateListener)
        handler.post(refresh)

        // Block PiP for GMaps/YouTube — prevents foreground detection confusion
        pipGuard.block(coordinator)

        // Auto-cast configured app if enabled (works without Activity open)
        autostart.dispatch(coordinator)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // startForeground() before the overlay gate — same startForegroundService() contract as onCreate.
        if (!startForegroundOnce()) { stopSelf(startId); return START_NOT_STICKY }
        if (!castEnabledNow()) { stopSelf(startId); return START_NOT_STICKY }
        // WP6 · R6.1 — cùng ba nhánh như [onCreate] (đây là lời gọi LẶP LẠI: mỗi `startForegroundService`). Nhánh
        // HIDDEN gỡ cửa sổ nếu còn sót rồi GIỮ dịch vụ; chỉ nhánh thiếu quyền mới đứng xuống.
        when (bubblePresence()) {
            BubblePresence.HIDDEN -> { hideBubble(); return START_STICKY }
            BubblePresence.NEEDS_OVERLAY_PERMISSION -> {
                if (!requestOverlayIfMissing()) { stopSelf(startId); return START_NOT_STICKY }
            }
            BubblePresence.SHOW -> Unit
        }
        // ⚠ [onCreate] có thể đã DỪNG SỚM (`castEnabledNow` false, hoặc chưa có quyền overlay) và `return`
        // TRƯỚC khi dựng `renderer`/`gestureHandler`. Nếu cổng lật giữa `onCreate` và lượt này — owner vừa bấm
        // "Cho phép" ở màn hệ thống mà [requestOverlayIfMissing] vừa mở, hoặc Cast vừa được bật — thì
        // `showBubble()` deref lateinit ⇒ `UninitializedPropertyAccessException` ⇒ sập dịch vụ nổi.
        // [onDestroy] đã canh đúng hai trường này (`::gestureHandler.isInitialized`); đường VÀO cũng phải canh.
        if (!::renderer.isInitialized || !::gestureHandler.isInitialized) {
            Log.w(TAG, "onStartCommand trước khi onCreate dựng xong — bỏ lượt dựng bong bóng (lượt start sau sẽ làm)")
            return START_STICKY
        }
        showBubble()
        return START_STICKY
    }

    override fun onDestroy() {
        destroyed = true
        // Remove all delayed work
        handler.removeCallbacksAndMessages(null)
        // Remove state listener explicitly
        val coordinator = SimpleCastRuntime.coordinator(applicationContext)
        coordinator.removeStateListener(stateListener)
        // Detach any still-pending autostart listeners (projection may never have reached Idle).
        autostart.detach(coordinator)
        // Restore PiP permissions for blocked apps
        pipGuard.restore(coordinator)
        // Shutdown gesture executor. Guard the lateinits: onCreate() may stopSelf() and return
        // BEFORE these are initialized (overlay permission or startForeground denied — e.g. the
        // very first launch after a clean install), and stopSelf() still runs onDestroy(). Touching
        // an uninitialized lateinit here would throw UninitializedPropertyAccessException.
        if (::gestureHandler.isInitialized) gestureHandler.shutdown()
        // Remove the long-press submenu overlay if it is still showing.
        submenu?.dismiss()
        submenu = null
        // Remove overlay
        bubble?.let { view -> runCatching { windowManager?.removeView(view) } }
        bubble = null
        if (::renderer.isInitialized) renderer.clearViews()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun showBubble() {
        if (bubble != null) return
        val manager = getSystemService(WINDOW_SERVICE) as WindowManager
        windowManager = manager

        val root = renderer.buildBubble()
        measureBubble(root)

        val type = if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        val saved = catalog.bubblePosition()
        val layout = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            alpha = IDLE_ALPHA
            x = clampX(saved?.first ?: (resources.displayMetrics.widthPixels - bubbleWidthPx(root) - dp(EDGE_MARGIN_DP)), root)
            y = clampY(saved?.second ?: ((resources.displayMetrics.heightPixels - bubbleHeightPx(root)) / 2), root)
        }
        params = layout

        gestureHandler.attachDragToEveryTouchSurface(root, root, layout, manager, ::clampX, ::clampY)
        bubble = root
        runCatching { manager.addView(root, layout) }
        handler.postDelayed(fade, FADE_DELAY_MS)
    }

    /**
     * Single tap on the icon = toggle full (cast the foreground / return to gauges). Token-gated so
     * only one cast/stop runs at a time; a rejected duplicate shows a toast. [detectForeground] does
     * shell I/O, so the work runs on the tap executor.
     */
    private fun onBubbleTap() {
        if (!gestureHandler.submitTapAction("bubble-tap") { actionDispatcher.onTap() }) {
            toast(Lang.t("Đang xử lý thao tác trước…", "Previous operation in progress…"))
        }
    }

    /**
     * Long-press = show the Trái/Phải/Cấu hình submenu (runs on the main thread — GestureDetector
     * callback). A second long-press toggles it closed. Trái/Phải cast a slot (shell I/O → tap
     * executor, token-gated); Cấu hình opens the app on the main thread (not gated by the cast token).
     */
    private fun onBubbleLongPress() {
        val manager = windowManager ?: return
        val overlay = submenu ?: BubbleSubmenuOverlay(this, manager).also { submenu = it }
        if (overlay.isShowing()) {
            overlay.dismiss()
            return
        }
        wakeBubble()
        // Anchor the submenu BESIDE the bubble (its current window rect), not centred on screen.
        val anchor = params
        overlay.show(anchor?.x ?: 0, anchor?.y ?: 0, bubbleWidthPx(), bubbleHeightPx()) { action ->
            if (BubbleGesturePlanner.slotFor(action) == null) {
                actionDispatcher.onSubmenuAction(action) // Cấu hình → open app
            } else if (!gestureHandler.submitTapAction("submenu-$action") {
                    actionDispatcher.onSubmenuAction(action)
                }
            ) {
                toast(Lang.t("Đang xử lý thao tác trước…", "Previous operation in progress…"))
            }
        }
    }

    private fun refreshBubbleState() {
        if (destroyed || bubble == null) return
        val state = SimpleCastRuntime.coordinator(applicationContext).state
        renderer.refreshFromState(state)
        wakeBubble()
    }

    private fun toast(message: String) {
        runCatching { Toast.makeText(this, message, Toast.LENGTH_SHORT).show() }
    }

    private fun saveBubblePosition(x: Int, y: Int) {
        gestureHandler.submitTapAction("bubble-position") {
            runCatching { catalog.setBubblePosition(x, y) }
        }
    }

    private fun requestOverlayIfMissing(): Boolean {
        if (Settings.canDrawOverlays(this)) return true
        // Launch the system overlay screen at most ONCE per service start. onCreate() and
        // onStartCommand() both gate on the permission and, on a startForegroundService() start,
        // run back-to-back before the user can grant it — without this guard the settings screen
        // opens twice (the "asked twice / asks again right after allowing" bug).
        if (!overlayRequested) {
            overlayRequested = true
            CastBubbleControl.requestOverlay(this)
        }
        return false
    }

    private fun startForegroundOnce(): Boolean = foregroundStarted || runCatching {
        startForeground(BubbleForegroundNotice.ID, BubbleForegroundNotice.build(this))
        foregroundStarted = true
        true
    }.getOrElse {
        android.util.Log.e(TAG, "startForeground denied", it)
        false
    }

    // Master Cast enable, read fresh each lifecycle entry (persisted via prefs). Fail-safe FALSE so a transient coordinator read error never silently STARTS casting (Cast is opt-in / default off, 2026-08-11).
    private fun castEnabledNow(): Boolean =
        runCatching { SimpleCastRuntime.coordinator(applicationContext).prefs.castEnabled() }.getOrDefault(false)

    /**
     * WP6 · R6.1 — *"cửa sổ nút nổi có được dựng không"*, đọc MỚI mỗi lần (cờ bền, CLAUDE.md §5) rồi để
     * [BubblePresence] phân ba nhánh.
     *
     * ⚠ Fail-safe **TRUE**, NGƯỢC với [castEnabledNow] ngay trên — chủ ý: ở kia đọc hỏng mà đoán BẬT là tự ý đi
     * giành mặt cụm trước mặt người lái; ở đây đọc hỏng mà đoán TẮT là **xoá lối vào chính của việc chiếu** mà
     * không nói gì. Hai fail-safe ngược nhau vì hậu quả ngược nhau.
     */
    private fun bubblePresence(): BubblePresence = BubblePresence.decide(
        visible = runCatching { SimpleCastRuntime.coordinator(applicationContext).prefs.bubbleVisible() }
            .getOrDefault(true),
        overlayGranted = runCatching { Settings.canDrawOverlays(this) }.getOrDefault(false),
    )

    /**
     * Đưa cửa sổ về khớp công tắc — gọi từ nhịp 2 giây ([refresh]) nên gạt công tắc trong Cài đặt là thấy ngay.
     *
     * ⚠⚠ Cố ý KHÔNG stop-rồi-start dịch vụ (cách `CastBubbleControl.apply` dùng cho công tắc cũ): [onCreate] là
     * nơi chạy [dispatchBootAutoStart], nên dựng lại dịch vụ ⇒ `autoStartDispatched` về false ⇒ **tự chiếu nổ lại
     * giữa chuyến**, tức gạt một công tắc trình bày lại đẩy một app lên cụm trước mặt người lái.
     *
     * Nhánh thiếu quyền vẫn phải xin (một lần mỗi lượt chạy, [overlayRequested]): im lặng ở đây = nút nổi không
     * bao giờ hiện mà không ai biết vì sao.
     */
    private fun syncBubbleWindow() {
        when (bubblePresence()) {
            BubblePresence.SHOW -> if (bubble == null) showBubble()
            BubblePresence.HIDDEN -> hideBubble()
            BubblePresence.NEEDS_OVERLAY_PERMISSION -> if (bubble != null) hideBubble() else requestOverlayIfMissing()
        }
    }

    /**
     * Gỡ cửa sổ nút nổi (và bảng con neo vào nó) — **không** dừng dịch vụ.
     *
     * Bảng con phải đi theo: nó đặt bằng toạ độ của bong bóng ([onBubbleLongPress]), để lại là một thẻ lơ lửng
     * không còn gì neo vào. `::renderer.isInitialized` vì [onStartCommand] có thể tới đây trước khi [onCreate]
     * dựng xong — cùng ca mà lượt soát ngoài 2026-09-16 đã bắt ở đường vào.
     */
    private fun hideBubble() {
        submenu?.dismiss()
        submenu = null
        val view = bubble ?: return
        handler.removeCallbacks(fade)
        runCatching { windowManager?.removeView(view) }
        bubble = null
        params = null
        if (::renderer.isInitialized) renderer.clearViews()
    }

    private fun measureBubble(view: View) {
        val unspecified = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        runCatching { view.measure(unspecified, unspecified) }
    }

    private fun bubbleWidthPx(view: View? = bubble): Int =
        view?.width?.takeIf { it > 0 } ?: view?.measuredWidth?.takeIf { it > 0 } ?: dp(BubbleRenderer.ICON_SIZE_DP)

    private fun bubbleHeightPx(view: View? = bubble): Int =
        view?.height?.takeIf { it > 0 } ?: view?.measuredHeight?.takeIf { it > 0 } ?: dp(BubbleRenderer.ICON_SIZE_DP)

    private fun clampX(value: Int, view: View? = bubble): Int {
        val width = resources.displayMetrics.widthPixels
        return value.coerceIn(0, (width - bubbleWidthPx(view)).coerceAtLeast(0))
    }

    private fun clampY(value: Int, view: View? = bubble): Int {
        val height = resources.displayMetrics.heightPixels
        return value.coerceIn(0, (height - bubbleHeightPx(view)).coerceAtLeast(0))
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density + .5f).toInt()

    companion object {
        private const val TAG = "ClusterCastBubble"
        private const val REFRESH_INTERVAL_MS = 2_000L
        private const val EDGE_MARGIN_DP = 28
        private const val IDLE_ALPHA = 0.35f
        private const val ACTIVE_ALPHA = 1.0f
        private const val FADE_DELAY_MS = 2_500L
    }
}
