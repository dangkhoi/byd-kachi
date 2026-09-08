package com.byd.clusternav.modules.clustercast

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
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
import com.byd.clusternav.R
import com.byd.clusternav.cast.platform.CastAppCatalog
import com.byd.clusternav.modules.clustercast.simplified.AppMover
import com.byd.clusternav.modules.clustercast.simplified.BubbleGesturePlanner
import com.byd.clusternav.modules.clustercast.simplified.ClusterSlotSide
import com.byd.clusternav.modules.clustercast.simplified.SimpleCastCoordinator
import com.byd.clusternav.modules.clustercast.simplified.SimpleCastIntent
import com.byd.clusternav.modules.clustercast.simplified.SimpleCastRuntime
import com.byd.clusternav.modules.clustercast.simplified.SimpleCastState
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Presentation-only overlay host for the canonical Cast model. ONE nav-arrow icon, three gestures.
 *
 * Delegates rendering to [BubbleRenderer], gesture disambiguation to [BubbleGestureHandler], action
 * dispatch to [BubbleActionDispatcher], and the long-press menu to [BubbleSubmenuOverlay]. This file
 * owns only: service lifecycle, window management, gesture→action wiring, state listener.
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
    private val pipPreviousModes = mutableMapOf<String, String>()

    /**
     * Autostart is driven by this service ONLY (sole driver, R1). [autoStartDispatched] is an
     * ATOMIC one-shot: [addStateListener] emits the current state on the caller thread while
     * [SimpleCastCoordinator.setState] iterates listeners on the executor thread, so the idle
     * trigger can be invoked from two threads at once. A plain check-then-set would let both pass
     * and dispatch the split twice → the 2nd CastSlot hits SLOT_OCCUPIED → setError wipes
     * CastingSplit (the exact R1 failure). `compareAndSet` guarantees a single dispatch.
     * [autoStartRightDispatched] gives the split-right handoff the same guarantee. The two listener
     * refs are held so [onDestroy] can detach them if projection never reached Idle (or the
     * split-right handoff never completed).
     */
    private val autoStartDispatched = AtomicBoolean(false)
    private val autoStartRightDispatched = AtomicBoolean(false)
    @Volatile private var autoStartIdleListener: ((SimpleCastState) -> Unit)? = null
    @Volatile private var autoStartSplitRightListener: ((SimpleCastState) -> Unit)? = null

    /**
     * Boot autostart needs the projection OPEN to ever reach [SimpleCastState.Idle] (the trigger).
     * On a pure boot there is no Activity to open it, so this service opens it. adb loopback
     * (localhost:5555) may not be ready the instant we boot, so retry a few times until the
     * coordinator leaves Off/Error. Cancelled once autostart is claimed or the service is destroyed
     * (via [handler].removeCallbacksAndMessages in [onDestroy]). Only armed when autostart is enabled.
     */
    @Volatile private var autoStartOpenAttempts = 0
    private val autoStartOpenProjection = object : Runnable {
        override fun run() {
            if (destroyed || autoStartDispatched.get()) return
            val coordinator = SimpleCastRuntime.coordinator(applicationContext)
            when (coordinator.state) {
                is SimpleCastState.Off, is SimpleCastState.Error -> coordinator.openProjection()
                else -> Unit // already opening/idle/casting — the idle listener will fire
            }
            if (++autoStartOpenAttempts < AUTOSTART_OPEN_MAX_ATTEMPTS && !autoStartDispatched.get()) {
                handler.postDelayed(this, AUTOSTART_OPEN_RETRY_MS)
            }
        }
    }

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
        if (!requestOverlayIfMissing()) { stopSelf(); return }

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

        showBubble()

        // Register named state listener (removable on destroy).
        val coordinator = SimpleCastRuntime.coordinator(applicationContext)
        coordinator.addStateListener(stateListener)
        handler.post(refresh)

        // Block PiP for GMaps/YouTube — prevents foreground detection confusion
        blockPipForKnownApps(coordinator)

        // Auto-cast configured app if enabled (works without Activity open)
        dispatchBootAutoStart(coordinator)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // startForeground() before the overlay gate — same startForegroundService() contract as onCreate.
        if (!startForegroundOnce()) { stopSelf(startId); return START_NOT_STICKY }
        if (!castEnabledNow()) { stopSelf(startId); return START_NOT_STICKY }
        if (!requestOverlayIfMissing()) { stopSelf(startId); return START_NOT_STICKY }
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
        autoStartIdleListener?.let { coordinator.removeStateListener(it) }
        autoStartSplitRightListener?.let { coordinator.removeStateListener(it) }
        autoStartIdleListener = null
        autoStartSplitRightListener = null
        // Restore PiP permissions for blocked apps
        restorePipForKnownApps(coordinator)
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
        startForeground(NOTIFICATION_ID, notification())
        foregroundStarted = true
        true
    }.getOrElse {
        android.util.Log.e(TAG, "startForeground denied", it)
        false
    }

    // Master Cast enable, read fresh each lifecycle entry (persisted via prefs). Fail-safe FALSE so a transient coordinator read error never silently STARTS casting (Cast is opt-in / default off, 2026-08-11).
    private fun castEnabledNow(): Boolean =
        runCatching { SimpleCastRuntime.coordinator(applicationContext).prefs.castEnabled() }.getOrDefault(false)

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

    private fun notification(): android.app.Notification {
        val channel = "cluster_cast_v2"
        if (Build.VERSION.SDK_INT >= 26) {
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(
                NotificationChannel(channel, "Cluster Cast", NotificationManager.IMPORTANCE_LOW),
            )
        }
        val pending = PendingIntent.getActivity(
            this, 0, Intent(this, com.byd.clusternav.MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        @Suppress("DEPRECATION")
        return android.app.Notification.Builder(this, channel)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("Cluster Cast")
            .setContentText(Lang.t("Nhấn để mở điều khiển", "Tap to open controls"))
            .setOngoing(true)
            .setContentIntent(pending)
            .build()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density + .5f).toInt()

    /**
     * Block PiP for known apps (GMaps, YouTube) via appops when our service runs.
     * This prevents them from entering PiP mode which confuses foreground detection.
     * Original mode is saved and restored on service destroy.
     */
    private fun blockPipForKnownApps(coordinator: SimpleCastCoordinator) {
        Thread({
            PIP_BLOCK_PACKAGES.forEach { pkg ->
                val prev = queryPipMode(coordinator, pkg)
                if (prev != null && prev != "deny") {
                    pipPreviousModes[pkg] = prev
                    coordinator.executeShell("appops set $pkg PICTURE_IN_PICTURE deny")
                    Log.i(TAG, "blocked PiP for $pkg (was: $prev)")
                }
            }
            // Also dismiss any currently active PiP
            runCatching { coordinator.dismissPipOnDisplay(0) }
        }, "pip-block").start()
    }

    private fun restorePipForKnownApps(coordinator: SimpleCastCoordinator) {
        Thread({
            pipPreviousModes.forEach { (pkg, mode) ->
                coordinator.executeShell("appops set $pkg PICTURE_IN_PICTURE $mode")
                Log.i(TAG, "restored PiP for $pkg → $mode")
            }
            pipPreviousModes.clear()
        }, "pip-restore").start()
    }

    private fun queryPipMode(coordinator: SimpleCastCoordinator, pkg: String): String? {
        val result = coordinator.executeShell("appops get $pkg PICTURE_IN_PICTURE")
        if (!result.success) return null
        // Output: "PICTURE_IN_PICTURE: allow" or "No operations."
        val match = Regex("PICTURE_IN_PICTURE:\\s*(\\w+)").find(result.stdout)
        return match?.groupValues?.get(1) ?: "allow" // default is allow if not set
    }

    /**
     * Sole autostart driver (R1). Waits for the coordinator to reach [SimpleCastState.Idle], then
     * dispatches the saved autostart intent. The Activity path ([CastAutostart]) no longer
     * dispatches, so there is exactly ONE driver and the old two-driver SLOT_OCCUPIED race — which
     * pushed the coordinator into Error → auto-recover Idle and wiped [SimpleCastState.CastingSplit]
     * — is gone. Works without the Activity open (this is a boot-started foreground service).
     *
     * [autoStartDispatched] guards against re-entry (onStartCommand/onCreate running again): once a
     * sequence is launched it is never launched twice within this service instance.
     */
    private fun dispatchBootAutoStart(coordinator: SimpleCastCoordinator) {
        if (autoStartDispatched.get()) return
        val prefs = coordinator.prefs
        val autoFull = prefs.autoStartEnabled()
        val autoSplit = prefs.autoStartSplitEnabled()
        if (!autoFull && !autoSplit) return

        val idleListener = object : (SimpleCastState) -> Unit {
            override fun invoke(state: SimpleCastState) {
                if (state !is SimpleCastState.Idle) return
                // Detach the idle trigger and claim the one-shot BEFORE dispatching.
                coordinator.removeStateListener(this)
                autoStartIdleListener = null
                if (!autoStartDispatched.compareAndSet(false, true)) return
                if (autoFull) dispatchAutoFull(coordinator) else dispatchAutoSplit(coordinator)
            }
        }
        autoStartIdleListener = idleListener
        coordinator.addStateListener(idleListener)

        // Pure boot has no Activity, so nothing else opens the projection and the idle trigger above
        // would never fire. Open it here (idempotent, R10) with a bounded retry for adb-loopback boot
        // timing. When autostart is off this method already returned, so gauges are untouched.
        autoStartOpenAttempts = 0
        handler.post(autoStartOpenProjection)
    }

    /** Full autostart: cast the saved package to the whole cluster. */
    private fun dispatchAutoFull(coordinator: SimpleCastCoordinator) {
        val pkg = coordinator.prefs.autoStartPackage()
        if (pkg.isNullOrBlank()) return
        Log.i(TAG, "boot auto-cast full: $pkg")
        coordinator.dispatch(SimpleCastIntent.CastFull(pkg, AppMover.classifyApp(pkg)))
    }

    /**
     * Split autostart: cast LEFT, then cast RIGHT ONLY after the coordinator confirms
     * [SimpleCastState.CastingSplit] with a non-null left slot (verified landing). This replaces
     * the old blind postDelayed(2000) that let RIGHT race LEFT into SLOT_OCCUPIED. If LEFT fails
     * (transient Error → Idle), RIGHT is simply never dispatched — no wipe, no collision. If only
     * one side is configured, only that side is cast.
     */
    private fun dispatchAutoSplit(coordinator: SimpleCastCoordinator) {
        val leftPkg = coordinator.prefs.autoStartLeftPackage()?.takeIf(String::isNotBlank)
        val rightPkg = coordinator.prefs.autoStartRightPackage()?.takeIf(String::isNotBlank)

        if (leftPkg == null) {
            // No left configured → cast right (if any) directly from Idle.
            rightPkg?.let {
                Log.i(TAG, "boot auto-cast split right-only: $it")
                coordinator.dispatch(SimpleCastIntent.CastSlot(it, ClusterSlotSide.RIGHT))
            }
            return
        }

        Log.i(TAG, "boot auto-cast split left: $leftPkg")
        coordinator.dispatch(SimpleCastIntent.CastSlot(leftPkg, ClusterSlotSide.LEFT))
        if (rightPkg == null) return // left-only split

        val rightListener = object : (SimpleCastState) -> Unit {
            override fun invoke(state: SimpleCastState) {
                // Fire only once LEFT has verifiably landed. Ignore Idle/Opening/Error transients
                // (a failed LEFT never reaches CastingSplit, so RIGHT is never dispatched).
                if (state !is SimpleCastState.CastingSplit || state.left == null) return
                coordinator.removeStateListener(this)
                autoStartSplitRightListener = null
                if (state.right != null) return // RIGHT already present — nothing to do
                if (!autoStartRightDispatched.compareAndSet(false, true)) return
                Log.i(TAG, "boot auto-cast split right: $rightPkg")
                coordinator.dispatch(SimpleCastIntent.CastSlot(rightPkg, ClusterSlotSide.RIGHT))
            }
        }
        autoStartSplitRightListener = rightListener
        coordinator.addStateListener(rightListener)
    }

    companion object {
        private const val TAG = "ClusterCastBubble"
        private const val NOTIFICATION_ID = 1042
        private const val REFRESH_INTERVAL_MS = 2_000L
        private const val EDGE_MARGIN_DP = 28
        private const val IDLE_ALPHA = 0.35f
        private const val ACTIVE_ALPHA = 1.0f
        private const val FADE_DELAY_MS = 2_500L
        private const val AUTOSTART_OPEN_MAX_ATTEMPTS = 5
        private const val AUTOSTART_OPEN_RETRY_MS = 3_000L
        private val PIP_BLOCK_PACKAGES = listOf(
            "com.google.android.apps.maps",
            "app.revanced.android.apps.maps",
            "com.google.android.youtube",
            "app.revanced.android.youtube",
            "app.revanced.android.apps.youtube",
        )
    }
}
