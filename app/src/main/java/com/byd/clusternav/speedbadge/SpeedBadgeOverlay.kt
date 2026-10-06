package com.byd.clusternav.speedbadge

import android.content.Context
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.byd.clusternav.Prefs
import com.byd.clusternav.badgeEnabled
import com.byd.clusternav.showUpcomingBadge
import com.byd.clusternav.showAlertChip
import com.byd.clusternav.badgeSizeDp
import com.byd.clusternav.badgeCenterX
import com.byd.clusternav.badgeCenterY
import com.byd.clusternav.contracts.SpeedSignType
import com.byd.clusternav.modules.clustercast.ClusterOverlayDisplays

/**
 * TYPE_APPLICATION_OVERLAY on display 1 (cluster) showing the speed-limit badge.
 *
 * LIFECYCLE (2026-08-18 fix, owner note "HƯỚNG FIX"): the cluster/cast display 1 can appear LONG after this
 * overlay is constructed (the app opens before Cast projects), and it can come and go while driving. So init
 * is **event-driven + retryable**, NOT one-shot:
 *  - [initOverlay] is IDEMPOTENT (no-op once `clusterWm != null`) and is retried from [doShow] whenever the
 *    display was not yet ready (`clusterWm == null`) — there is NO permanent one-way kill anymore.
 *  - a [DisplayManager.DisplayListener] (re)initializes on `onDisplayAdded(1)` and re-shows the pending value,
 *    and TEARS DOWN on `onDisplayRemoved(1)` (detach + drop the display WM/view) so it cleanly re-attaches
 *    when display 1 returns (e.g. Cast toggled off→on).
 * Off-car (emulator / no display 1) stays a cheap no-op: init finds no display and simply stays uninitialized.
 *
 * All WindowManager ops run on the main handler and are degrade-safe (runCatching, never throw to the caller).
 * Absolute-centre positioning ([BadgeLayout.clampCenter]) is unchanged. The badge is GATED by
 * [Prefs.badgeEnabled] (default ON): when disabled, [show] detaches and never attaches — this gate covers both
 * the real speed-sign pipeline and the debug force-show, since both call [show].
 *
 * 2.90 · R10 (spec `kachi-290-cluster-rect-fix.html` §4.5): display = [ClusterOverlayDisplays.resolve] — id cụm SỐNG do coordinator
 * công bố, rồi luật tên fission/xdja; KHÔNG còn thử hằng display một trước (sau khởi động nguội display 1 = VD ô của Kachi [ĐO 15/09]), không
 * VD riêng tư. Mọi thay đổi display / id cụm / dọn cụm ⇒ [reconcile]: id chọn ≠ id đang gắn ⇒ gỡ + gắn lại + phát lại giá trị cuối.
 * R9: [ClusterOverlayDisplays.paused] (cổng theme đang dọn cụm) ⇒ gỡ hết, không gắn tới khi trả.
 */
class SpeedBadgeOverlay(internal val appContext: Context) : AutoCloseable {

    companion object {
        internal const val TAG = "SpeedBadgeOverlay"
        // ── Upcoming "speed-limit ahead" badge (spec upcoming-speed-limit-badge) ──
        // B2 (owner 2026-08-19): 80% size + GRAY (muted) badge + 45° LOWER-LEFT of the main badge (was ~70% +
        // red/black + straight-below). See §Nhật ký triển khai in the spec.
        internal const val UPCOMING_SCALE = 0.8f          // B2: upcoming badge is 80% of the main badge (was 0.7)
        internal const val UPCOMING_DIAG_FRAC = 0.70f     // B2: 45° offset per axis (× main size) — left + down
        internal const val UPCOMING_CONTAINER_W_FRAC = 1.8f // window width (× main size) so the distance text never clips
        internal const val UPCOMING_LABEL_FRAC = 0.42f    // distance label text size (× upcoming badge size)
        // ── Road-alert / speed-camera chip (B3.20) ──
        // A THIRD window: a horizontal pill (camera glyph + limit + distance) placed to the RIGHT of the main
        // badge at its vertical centre — the upcoming badge is 45° lower-LEFT, so the two never overlap.
        private const val ALERT_HEIGHT_FRAC = 0.52f      // chip height (× main badge size)
        private const val ALERT_GAP_FRAC = 0.20f         // gap from the main badge's right edge (× main size)
    }

    private val handler = Handler(Looper.getMainLooper())
    internal var clusterWm: WindowManager? = null
    internal var badgeView: SpeedBadgeView? = null
    private var attached = false
    // Last value seen, remembered so a re-attach (display 1 added, or badge re-enabled) can re-show it without
    // waiting for the next pipeline emission. Null = nothing to show yet.
    private var lastSpeedKph: Int? = null
    private var lastSignType: SpeedSignType? = null
    // 2.90 · review Pass 3 [P1]: [hide] (khung "xoá" — đường không có biển) GIỮ [lastSpeedKph]; phát lại sau gắn lại display /
    // dọn-trả cụm KHÔNG được làm hiện lại giá trị cũ đã bị xoá ⇒ nhớ trạng thái ẩn, [replayLast] ẩn lại.
    private var lastHidden = false
    // ── Upcoming "speed-limit ahead" badge: a SECOND SpeedBadgeView (80%, MUTED gray) + a countdown distance
    // label in a vertical container, anchored DIAGONALLY at 45° to the LOWER-LEFT of the main badge (B2, owner
    // 2026-08-19; was ~70% straight-below). Its own window (additive) so the current-limit
    // badge window is never touched. Last values remembered so a re-attach (display 1 added / re-enabled) can
    // re-show without waiting for the next VietMap emission.
    internal var upcomingContainer: LinearLayout? = null
    internal var upcomingBadgeView: SpeedBadgeView? = null
    internal var upcomingDistLabel: TextView? = null
    internal var upcomingAttached = false
    internal var lastUpcomingLimit: Int? = null
    internal var lastUpcomingDist: Int? = null
    internal var lastUpcomingText: String? = null
    // ── Road-alert / speed-camera chip (B3.20): a THIRD window (AlertChipView) placed to the RIGHT of the main
    // badge. ADDITIVE — never touches the main or upcoming windows — gated by master badge AND Prefs.showAlertChip
    // (default OFF). Last values remembered for a fast re-attach (display 1 added / re-enabled).
    private var alertChipView: AlertChipView? = null
    private var alertAttached = false
    private var lastAlertShow = false
    private var lastAlertLimit = 0
    private var lastAlertText: String? = null
    private var lastAlertIcon = false
    // Real display-1 size in px for on-screen clamping (BadgeLayout.clampCenter). Falls back to the Seal
    // cluster 1920×720 when the real size can't be read, so placement math never divides by a bogus extent.
    internal var clusterW = 1920
    internal var clusterH = 720
    // The display the overlay ACTUALLY attached to (`-1` = not attached). 2.90 · R10: no constant default — the cluster
    // id is only known by resolving ([ClusterOverlayDisplays.resolve]); the listener re-resolves on every change.
    private var resolvedDisplayId = -1

    private val displayListener = object : DisplayManager.DisplayListener {
        // 2.90 · R10: the rebuilt cluster VD can be ADDED before the old one is REMOVED ([ĐO 06/10] 4 → 9): the old gate
        // `if (clusterWm != null && displayId != resolvedDisplayId) return` dropped that add and nothing re-attached until the
        // next value. Every add/remove now re-resolves ([reconcile] is idempotent + cheap).
        override fun onDisplayAdded(displayId: Int) { handler.post { reconcile() } }

        override fun onDisplayRemoved(displayId: Int) {
            handler.post {
                if (displayId == resolvedDisplayId) teardown()
                reconcile()
            }
        }

        override fun onDisplayChanged(displayId: Int) { /* size/rotation handled at next show via initOverlay */ }
    }

    /** 2.90 · R9/R10 — id cụm công bố đổi, hoặc cổng theme dọn/trả cụm ⇒ chọn lại trên luồng chính. */
    private val clusterStateListener: () -> Unit = { handler.post { reconcile() } }

    init {
        handler.post {
            initOverlay()
            runCatching {
                (appContext.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager)
                    ?.registerDisplayListener(displayListener, handler)
            }.onFailure { Log.w(TAG, "registerDisplayListener failed: ${it.message}") }
            ClusterOverlayDisplays.addListener(clusterStateListener)
        }
    }

    /** Cluster display: [ClusterOverlayDisplays.resolve] (id cụm sống → luật tên → PRESENTATION off-car). Never display 1 by constant. */
    private fun resolveClusterDisplay(dm: DisplayManager): android.view.Display? = ClusterOverlayDisplays.resolve(dm)

    /**
     * 2.90 · R9/R10 — đưa lớp phủ về đúng display hiện tại. Đang dọn cụm ⇒ gỡ hết (giữ giá trị cuối). Display chọn được ≠ display
     * đang gắn ⇒ gỡ rồi gắn lại trên display mới và phát lại giá trị cuối. Cùng display ⇒ không làm gì. Chạy trên luồng chính.
     */
    private fun reconcile() {
        if (ClusterOverlayDisplays.paused) {
            if (clusterWm != null) { val was = resolvedDisplayId; teardown(); Log.i(TAG, "dọn cụm: gỡ badge khỏi display $was") }
            return
        }
        val dm = appContext.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager ?: return
        val target = resolveClusterDisplay(dm)?.displayId ?: -1
        if (clusterWm != null && target == resolvedDisplayId) return
        if (clusterWm != null) {
            Log.i(TAG, "display cụm đổi $resolvedDisplayId → $target ⇒ gắn lại badge")
            teardown()
        }
        initOverlay()
        replayLast()
    }

    /**
     * Phát lại giá trị cuối sau khi gắn lại (respects the enabled gate). Badge đang ẩn ([doHide] — khung xoá) thì vẫn ẩn: cùng
     * một lượt trên luồng chính nên không có khung hình nào vẽ giá trị cũ (review Pass 3 [P1]).
     */
    private fun replayLast() {
        val hidden = lastHidden
        lastSpeedKph?.let { doShow(it, lastSignType); if (hidden) doHide() }
        lastUpcomingLimit?.let { doSetUpcoming(it, lastUpcomingDist, lastUpcomingText) }
        if (lastAlertShow) doSetAlert(lastAlertLimit, lastAlertText, lastAlertIcon)
    }

    /**
     * IDEMPOTENT + retryable init. No-op if already initialized (`clusterWm != null`). If display 1 is absent
     * (off-car, or Cast not yet projecting) it stays UN-initialized and returns — the next [doShow] /
     * onDisplayAdded retries. Never sets a permanent degrade. Degrade-safe (runCatching).
     */
    internal fun initOverlay() {
        if (clusterWm != null) return
        if (ClusterOverlayDisplays.paused) return   // 2.90 · R9: cổng theme đang dọn cụm — gắn lại ở lượt trả
        runCatching {
            val dm = appContext.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
            val display = dm?.let { resolveClusterDisplay(it) }
            if (display == null) {
                Log.d(TAG, "cluster display not ready (cast id ${ClusterOverlayDisplays.castLiveId} · fission/xdja · PRESENTATION) — will retry")
                return
            }
            val size = android.graphics.Point()
            @Suppress("DEPRECATION") display.getRealSize(size)
            if (size.x > 0 && size.y > 0) {
                clusterW = size.x
                clusterH = size.y
            }
            val clusterCtx = appContext.createDisplayContext(display)
            val wm = clusterCtx.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            if (wm == null) {
                Log.d(TAG, "WindowManager null for display ${display.displayId} — will retry")
                return
            }
            // Build the view BEFORE publishing either field: if SpeedBadgeView construction throws, clusterWm
            // stays null so the next doShow() / onDisplayAdded retries — never a half-initialized state
            // (clusterWm set, badgeView null) that the `clusterWm == null` retry guard could not recover from.
            val view = SpeedBadgeView(clusterCtx)
            clusterWm = wm
            badgeView = view
            resolvedDisplayId = display.displayId   // track the display we attached to (may be ≠ 1 on this car)
            Log.i(TAG, "overlay initialized for display ${display.displayId} (${clusterW}x$clusterH)")
        }.onFailure { Log.w(TAG, "initOverlay failed: ${it.message}") }
    }

    fun show(speedKph: Int, signType: SpeedSignType?) {
        handler.post { doShow(speedKph, signType) }
    }

    fun hide() {
        handler.post { doHide() }
    }

    /**
     * Re-evaluate the [Prefs.badgeEnabled] gate after the toggle changes: detach when disabled, or re-show the
     * last known value when re-enabled. Posted to the main handler; degrade-safe.
     */
    fun applyEnabled() {
        handler.post {
            if (!Prefs.badgeEnabled(appContext)) {
                teardown()
            } else {
                replayLast()
            }
        }
    }

    /**
     * Public API — set (or clear) the "upcoming speed-limit ahead" badge shown BELOW the main badge. Passing a
     * null/<=0 [limitKph] hides it. Posted to the main handler; degrade-safe. [distanceText] (VietMap's raw
     * "300 m" / "1,2 km") is preferred for the countdown label, falling back to formatting [distanceMeters].
     */
    fun setUpcoming(limitKph: Int?, distanceMeters: Int?, distanceText: String? = null) {
        handler.post { doSetUpcoming(limitKph, distanceMeters, distanceText) }
    }

    /**
     * Re-evaluate the upcoming-badge gate (master [Prefs.badgeEnabled] AND [Prefs.showUpcomingBadge]) after the
     * "Hiện giới hạn sắp tới" toggle changes: detach when off, or re-show the last value when on. Degrade-safe.
     */
    fun applyUpcomingEnabled() {
        handler.post {
            if (!Prefs.badgeEnabled(appContext) || !Prefs.showUpcomingBadge(appContext)) {
                teardownUpcoming()
            } else {
                lastUpcomingLimit?.let { doSetUpcoming(it, lastUpcomingDist, lastUpcomingText) }
            }
        }
    }

    /**
     * Public API — set (or clear) the road-alert / speed-camera chip (B3.20) shown to the RIGHT of the main
     * badge. [limitKph] ≤ 0 draws no limit circle; [distanceText] is the countdown ("300 m"); [hasIcon] draws
     * the camera glyph. Passing show=false hides it. Posted to the main handler; degrade-safe.
     */
    fun setAlert(show: Boolean, limitKph: Int, distanceText: String?, hasIcon: Boolean) {
        handler.post {
            if (!show) { doClearAlert(); return@post }
            doSetAlert(limitKph, distanceText, hasIcon)
        }
    }

    /**
     * Re-evaluate the alert-chip gate (master [Prefs.badgeEnabled] AND [Prefs.showAlertChip]) after the toggle
     * changes: detach when off, or re-show the last value when on. Degrade-safe.
     */
    fun applyAlertChipEnabled() {
        handler.post {
            if (!Prefs.badgeEnabled(appContext) || !Prefs.showAlertChip(appContext)) {
                teardownAlert()
            } else if (lastAlertShow) {
                doSetAlert(lastAlertLimit, lastAlertText, lastAlertIcon)
            }
        }
    }

    override fun close() {
        handler.post {
            runCatching {
                (appContext.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager)
                    ?.unregisterDisplayListener(displayListener)
            }
            ClusterOverlayDisplays.removeListener(clusterStateListener)
            teardown()
        }
    }

    private fun doShow(speedKph: Int, signType: SpeedSignType?) {
        lastSpeedKph = speedKph
        lastSignType = signType
        lastHidden = false
        // Gate: badge disabled → make sure nothing is on the cluster and never attach.
        if (!Prefs.badgeEnabled(appContext)) {
            teardown()
            return
        }
        // Retry init if display 1 was not ready when we were constructed (or after a teardown).
        if (clusterWm == null) initOverlay()
        val view = badgeView ?: return   // still no display 1 (off-car) → cheap no-op
        view.speedValue = speedKph
        view.signType = signType
        if (!attached) {
            val lp = buildLayoutParams()
            runCatching { clusterWm?.addView(view, lp) }
                .onFailure { Log.w(TAG, "addView failed (will retry next show): ${it.message}"); return }
            attached = true
        }
        view.visibility = android.view.View.VISIBLE
    }

    /**
     * Build the overlay LayoutParams from the persisted badge prefs (absolute centre + size). Read on the
     * main handler (doShow / doRefreshLayout both run there); SharedPreferences is memory-cached so this is
     * cheap and never hits the notification thread. Uses `gravity = TOP|LEFT` and sets `x`/`y` to the badge's
     * top-left, computed from the persisted CENTRE via the tested pure [BadgeLayout] in :core: the centre is
     * first clamped on-screen ([BadgeLayout.clampCenter]) against the real display-1 size, then converted to a
     * top-left ([BadgeLayout.topLeftFromCenter]). Size clamps to 60..240 dp inside [Prefs].
     */
    private fun buildLayoutParams(): WindowManager.LayoutParams {
        val density = appContext.resources.displayMetrics.density
        val sizePx = (Prefs.badgeSizeDp(appContext) * density).toInt().coerceAtLeast(1)
        val (cx, cy) = BadgeLayout.clampCenter(
            Prefs.badgeCenterX(appContext), Prefs.badgeCenterY(appContext), sizePx, clusterW, clusterH,
        )
        val (left, top) = BadgeLayout.topLeftFromCenter(cx, cy, sizePx)
        return WindowManager.LayoutParams(
            sizePx, sizePx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            x = left
            y = top
        }
    }

    /**
     * Re-read the badge prefs and, if the badge is currently attached, apply the new position/size LIVE via
     * [WindowManager.updateViewLayout] on the main handler. Degrade-safe (runCatching, never throws to the
     * caller) and a no-op before attach — the next [show] then picks up the fresh prefs.
     * Called by DiagActivity / the placement UI so the driver can force-show the badge and watch it move.
     */
    fun refreshLayout() {
        handler.post { doRefreshLayout() }
    }

    private fun doRefreshLayout() {
        if (upcomingAttached) {
            applyUpcomingMetrics()
            runCatching { clusterWm?.updateViewLayout(upcomingContainer, buildUpcomingLayoutParams()) }
                .onFailure { Log.w(TAG, "updateViewLayout(upcoming) failed: ${it.message}") }
        }
        if (alertAttached) {
            runCatching { clusterWm?.updateViewLayout(alertChipView, buildAlertLayoutParams()) }
                .onFailure { Log.w(TAG, "updateViewLayout(alert) failed: ${it.message}") }
        }
        if (!attached) return
        val view = badgeView ?: return
        runCatching { clusterWm?.updateViewLayout(view, buildLayoutParams()) }
            .onFailure { Log.w(TAG, "updateViewLayout failed: ${it.message}") }
    }

    private fun doHide() {
        lastHidden = true
        if (!attached) return
        badgeView?.visibility = android.view.View.INVISIBLE
    }

    /**
     * Full teardown: detach the view from display 1 and DROP the display WindowManager + view so a fresh
     * [initOverlay] rebuilds them against the display that comes back. Used on `onDisplayRemoved(1)`, on the
     * disabled gate, and on [close]. Degrade-safe and idempotent (safe when nothing is attached).
     */
    private fun teardown() {
        teardownUpcoming()
        teardownAlert()
        val view = badgeView
        if (attached && view != null) {
            runCatching { clusterWm?.removeView(view) }
                .onFailure { Log.w(TAG, "removeView failed: ${it.message}") }
        }
        attached = false
        clusterWm = null
        badgeView = null
        resolvedDisplayId = -1
    }

    // ─── Upcoming "speed-limit ahead" badge: xem SpeedBadgeOverlayUpcoming.kt (hàm mở rộng, tách theo VAI — DEBT-500) ───

    // ─── Road-alert / speed-camera chip (B3.20) ────────────────────────────────────────────────────────
    // A THIRD window on display 1 holding an [AlertChipView] (camera glyph + limit + distance), placed to the
    // RIGHT of the main badge at its vertical centre (the upcoming badge is 45° lower-LEFT ⇒ no overlap).
    // ADDITIVE, degrade-safe, gated by BOTH the master badge gate AND the "Hiện cảnh báo/camera" toggle
    // (Prefs.showAlertChip, default OFF so it never disturbs the existing badge layout unless opted in).

    private fun doSetAlert(limitKph: Int, distanceText: String?, hasIcon: Boolean) {
        lastAlertShow = true
        lastAlertLimit = limitKph
        lastAlertText = distanceText
        lastAlertIcon = hasIcon
        if (!Prefs.badgeEnabled(appContext) || !Prefs.showAlertChip(appContext)) { teardownAlert(); return }
        if (clusterWm == null) initOverlay()
        val wm = clusterWm ?: return                 // still no display 1 (off-car) → cheap no-op
        val ctx = badgeView?.context ?: return
        val chip = ensureAlertChipView(ctx) ?: return
        chip.limitKph = limitKph
        chip.distanceText = distanceText?.trim().orEmpty()
        chip.hasIcon = hasIcon
        val lp = buildAlertLayoutParams()
        if (!alertAttached) {
            runCatching { wm.addView(chip, lp) }
                .onFailure { Log.w(TAG, "addView(alert) failed (will retry next set): ${it.message}"); return }
            alertAttached = true
        } else {
            runCatching { wm.updateViewLayout(chip, lp) }
                .onFailure { Log.w(TAG, "updateViewLayout(alert) failed: ${it.message}") }
        }
        chip.visibility = View.VISIBLE
    }

    /** Nothing to show right now → cheap hide (keep the window for a fast re-show). */
    private fun doClearAlert() {
        lastAlertShow = false
        if (alertAttached) alertChipView?.visibility = View.INVISIBLE
    }

    private fun ensureAlertChipView(ctx: Context): AlertChipView? {
        alertChipView?.let { return it }
        return runCatching { AlertChipView(ctx).also { alertChipView = it } }
            .getOrElse { Log.w(TAG, "ensureAlertChipView failed: ${it.message}"); null }
    }

    /**
     * Position the chip window to the RIGHT of the main badge's (clamped) centre, vertically centred on it.
     * Height = [ALERT_HEIGHT_FRAC] × main size; width = the chip's measured content width (so text never
     * clips). Left edge = main badge right edge + [ALERT_GAP_FRAC] gap.
     */
    private fun buildAlertLayoutParams(): WindowManager.LayoutParams {
        val density = appContext.resources.displayMetrics.density
        val mainSizePx = (Prefs.badgeSizeDp(appContext) * density).toInt().coerceAtLeast(1)
        val chipH = (mainSizePx * ALERT_HEIGHT_FRAC).toInt().coerceAtLeast(1)
        val chipW = (alertChipView?.contentWidthPx(chipH) ?: chipH).coerceAtLeast(chipH)
        val (mcx, mcy) = BadgeLayout.clampCenter(
            Prefs.badgeCenterX(appContext), Prefs.badgeCenterY(appContext), mainSizePx, clusterW, clusterH,
        )
        val gap = (mainSizePx * ALERT_GAP_FRAC).toInt()
        return WindowManager.LayoutParams(
            chipW, chipH,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            x = mcx + mainSizePx / 2 + gap
            y = mcy - chipH / 2
        }
    }

    /** Detach + drop the alert window/view so a fresh [ensureAlertChipView] rebuilds cleanly. */
    private fun teardownAlert() {
        val chip = alertChipView
        if (alertAttached && chip != null) {
            runCatching { clusterWm?.removeView(chip) }
                .onFailure { Log.w(TAG, "removeView(alert) failed: ${it.message}") }
        }
        alertAttached = false
        alertChipView = null
    }
}
