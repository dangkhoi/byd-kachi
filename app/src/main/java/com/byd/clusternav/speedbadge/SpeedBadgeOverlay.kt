package com.byd.clusternav.speedbadge

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.byd.clusternav.Prefs
import com.byd.clusternav.contracts.SpeedSignType
import com.byd.clusternav.navigation.NavParse

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
 */
class SpeedBadgeOverlay(private val appContext: Context) : AutoCloseable {

    companion object {
        private const val TAG = "SpeedBadgeOverlay"
        private const val CLUSTER_DISPLAY_ID = 1
        // ── Upcoming "speed-limit ahead" badge (spec upcoming-speed-limit-badge) ──
        // B2 (owner 2026-08-19): 80% size + GRAY (muted) badge + 45° LOWER-LEFT of the main badge (was ~70% +
        // red/black + straight-below). See §Nhật ký triển khai in the spec.
        private const val UPCOMING_SCALE = 0.8f          // B2: upcoming badge is 80% of the main badge (was 0.7)
        private const val UPCOMING_DIAG_FRAC = 0.70f     // B2: 45° offset per axis (× main size) — left + down
        private const val UPCOMING_CONTAINER_W_FRAC = 1.8f // window width (× main size) so the distance text never clips
        private const val UPCOMING_LABEL_FRAC = 0.42f    // distance label text size (× upcoming badge size)
        // ── Road-alert / speed-camera chip (B3.20) ──
        // A THIRD window: a horizontal pill (camera glyph + limit + distance) placed to the RIGHT of the main
        // badge at its vertical centre — the upcoming badge is 45° lower-LEFT, so the two never overlap.
        private const val ALERT_HEIGHT_FRAC = 0.52f      // chip height (× main badge size)
        private const val ALERT_GAP_FRAC = 0.20f         // gap from the main badge's right edge (× main size)
    }

    private val handler = Handler(Looper.getMainLooper())
    private var clusterWm: WindowManager? = null
    private var badgeView: SpeedBadgeView? = null
    private var attached = false
    // Last value seen, remembered so a re-attach (display 1 added, or badge re-enabled) can re-show it without
    // waiting for the next pipeline emission. Null = nothing to show yet.
    private var lastSpeedKph: Int? = null
    private var lastSignType: SpeedSignType? = null
    // ── Upcoming "speed-limit ahead" badge: a SECOND SpeedBadgeView (80%, MUTED gray) + a countdown distance
    // label in a vertical container, anchored DIAGONALLY at 45° to the LOWER-LEFT of the main badge (B2, owner
    // 2026-08-19; was ~70% straight-below). Its own window (additive) so the current-limit
    // badge window is never touched. Last values remembered so a re-attach (display 1 added / re-enabled) can
    // re-show without waiting for the next VietMap emission.
    private var upcomingContainer: LinearLayout? = null
    private var upcomingBadgeView: SpeedBadgeView? = null
    private var upcomingDistLabel: TextView? = null
    private var upcomingAttached = false
    private var lastUpcomingLimit: Int? = null
    private var lastUpcomingDist: Int? = null
    private var lastUpcomingText: String? = null
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
    private var clusterW = 1920
    private var clusterH = 720

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) {
            if (displayId != CLUSTER_DISPLAY_ID) return
            handler.post {
                initOverlay()
                // If a value is pending, re-show it now that display 1 is back (respects the enabled gate).
                lastSpeedKph?.let { doShow(it, lastSignType) }
                lastUpcomingLimit?.let { doSetUpcoming(it, lastUpcomingDist, lastUpcomingText) }
                if (lastAlertShow) doSetAlert(lastAlertLimit, lastAlertText, lastAlertIcon)
            }
        }

        override fun onDisplayRemoved(displayId: Int) {
            if (displayId != CLUSTER_DISPLAY_ID) return
            handler.post { teardown() }
        }

        override fun onDisplayChanged(displayId: Int) { /* size/rotation handled at next show via initOverlay */ }
    }

    init {
        handler.post {
            initOverlay()
            runCatching {
                (appContext.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager)
                    ?.registerDisplayListener(displayListener, handler)
            }.onFailure { Log.w(TAG, "registerDisplayListener failed: ${it.message}") }
        }
    }

    /**
     * Cluster display resolver. Tries the known [CLUSTER_DISPLAY_ID] (1 — the car cluster) FIRST so the on-car
     * path stays byte-for-byte unchanged; only if that display is absent (e.g. off-car emulator where the
     * secondary is display 2) does it fall back to the first `CATEGORY_PRESENTATION` display with id != 0.
     * Additive: cannot change on-car behaviour (the car always has display 1).
     */
    private fun resolveClusterDisplay(dm: DisplayManager): android.view.Display? =
        dm.getDisplay(CLUSTER_DISPLAY_ID)
            ?: runCatching {
                dm.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION).firstOrNull { it.displayId != 0 }
            }.getOrNull()

    /**
     * IDEMPOTENT + retryable init. No-op if already initialized (`clusterWm != null`). If display 1 is absent
     * (off-car, or Cast not yet projecting) it stays UN-initialized and returns — the next [doShow] /
     * onDisplayAdded retries. Never sets a permanent degrade. Degrade-safe (runCatching).
     */
    private fun initOverlay() {
        if (clusterWm != null) return
        runCatching {
            val dm = appContext.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
            val display = dm?.let { resolveClusterDisplay(it) }
            if (display == null) {
                Log.d(TAG, "cluster display not ready (tried id $CLUSTER_DISPLAY_ID + PRESENTATION) — will retry")
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
                Log.d(TAG, "WindowManager null for display $CLUSTER_DISPLAY_ID — will retry")
                return
            }
            // Build the view BEFORE publishing either field: if SpeedBadgeView construction throws, clusterWm
            // stays null so the next doShow() / onDisplayAdded retries — never a half-initialized state
            // (clusterWm set, badgeView null) that the `clusterWm == null` retry guard could not recover from.
            val view = SpeedBadgeView(clusterCtx)
            clusterWm = wm
            badgeView = view
            Log.i(TAG, "overlay initialized for display $CLUSTER_DISPLAY_ID (${clusterW}x$clusterH)")
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
                lastSpeedKph?.let { doShow(it, lastSignType) }
                lastUpcomingLimit?.let { doSetUpcoming(it, lastUpcomingDist, lastUpcomingText) }
                if (lastAlertShow) doSetAlert(lastAlertLimit, lastAlertText, lastAlertIcon)
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
            teardown()
        }
    }

    private fun doShow(speedKph: Int, signType: SpeedSignType?) {
        lastSpeedKph = speedKph
        lastSignType = signType
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
    }

    // ─── Upcoming "speed-limit ahead" badge (spec upcoming-speed-limit-badge) ──────────────────────────────
    // A SECOND window on display 1 holding a vertical container [SpeedBadgeView 80% MUTED] + [distance label],
    // placed DIAGONALLY at 45° to the LOWER-LEFT of the main badge (B2). It is ADDITIVE — the current-limit
    // badge window is never touched — and fully degrade-safe (runCatching, never throws to the caller). Gated by
    // BOTH the master badge gate AND the "Hiện giới hạn sắp tới" toggle.

    private fun doSetUpcoming(limitKph: Int?, distanceMeters: Int?, distanceText: String?) {
        lastUpcomingLimit = limitKph
        lastUpcomingDist = distanceMeters
        lastUpcomingText = distanceText
        // Gate: master badge OFF or the upcoming toggle OFF → fully detach and never attach.
        if (!Prefs.badgeEnabled(appContext) || !Prefs.showUpcomingBadge(appContext)) {
            teardownUpcoming()
            return
        }
        // Nothing upcoming right now → cheap hide (keep the window for a fast re-show on the next emission).
        if (limitKph == null || limitKph <= 0) {
            hideUpcoming()
            return
        }
        if (clusterWm == null) initOverlay()
        val wm = clusterWm ?: return                 // still no display 1 (off-car) → cheap no-op
        val ctx = badgeView?.context ?: return       // cluster display context (badgeView built with it)
        val container = ensureUpcomingContainer(ctx) ?: return
        upcomingBadgeView?.speedValue = limitKph
        upcomingBadgeView?.signType = SpeedSignType.REGULATORY
        applyUpcomingMetrics()
        upcomingDistLabel?.let { label ->
            val text = upcomingLabelText(distanceText, distanceMeters)
            label.text = text
            label.visibility = if (text.isEmpty()) View.GONE else View.VISIBLE
        }
        val lp = buildUpcomingLayoutParams()
        if (!upcomingAttached) {
            runCatching { wm.addView(container, lp) }
                .onFailure { Log.w(TAG, "addView(upcoming) failed (will retry next set): ${it.message}"); return }
            upcomingAttached = true
        } else {
            runCatching { wm.updateViewLayout(container, lp) }
                .onFailure { Log.w(TAG, "updateViewLayout(upcoming) failed: ${it.message}") }
        }
        container.visibility = View.VISIBLE
    }

    /** Build the upcoming container (badge + label) ONCE against the cluster display context. Degrade-safe. */
    private fun ensureUpcomingContainer(ctx: Context): LinearLayout? {
        upcomingContainer?.let { return it }
        return runCatching {
            val badge = SpeedBadgeView(ctx).apply { muted = true }   // B2: gray ring + gray number (upcoming)
            val label = TextView(ctx).apply {
                setTextColor(Color.WHITE)
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setSingleLine(true)
            }
            val container = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
            }
            container.addView(badge, LinearLayout.LayoutParams(1, 1))
            container.addView(
                label,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
            upcomingContainer = container
            upcomingBadgeView = badge
            upcomingDistLabel = label
            container
        }.getOrElse { Log.w(TAG, "ensureUpcomingContainer failed: ${it.message}"); null }
    }

    /** Size the upcoming badge (80% of the main) + the label text from the CURRENT badge-size pref (live). */
    private fun applyUpcomingMetrics() {
        val density = appContext.resources.displayMetrics.density
        val mainSizePx = (Prefs.badgeSizeDp(appContext) * density).toInt().coerceAtLeast(1)
        val badgeSizePx = (mainSizePx * UPCOMING_SCALE).toInt().coerceAtLeast(1)
        upcomingBadgeView?.let { b ->
            val lp = b.layoutParams
            if (lp != null && (lp.width != badgeSizePx || lp.height != badgeSizePx)) {
                lp.width = badgeSizePx
                lp.height = badgeSizePx
                b.layoutParams = lp
            }
        }
        upcomingDistLabel?.apply {
            setTextSize(TypedValue.COMPLEX_UNIT_PX, badgeSizePx * UPCOMING_LABEL_FRAC)
            setShadowLayer(badgeSizePx * 0.10f, 0f, 0f, Color.BLACK)
        }
    }

    /**
     * Position the upcoming window DIAGONALLY at 45° to the LOWER-LEFT of the main badge (B2, owner 2026-08-19;
     * was straight-below). The upcoming badge CENTRE is offset from the main badge's (clamped, on-screen) centre
     * by an equal amount left and down ([UPCOMING_DIAG_FRAC] × main size on each axis = a 45° vector). The window
     * is wider than the main badge ([UPCOMING_CONTAINER_W_FRAC]) so the distance text never clips; the vertical
     * LinearLayout centres the 80% badge (at the window's top) + label, so the window x/y are derived from the
     * badge centre: x = upcomingCx − containerW/2, y = upcomingCy − badgeSize/2.
     */
    private fun buildUpcomingLayoutParams(): WindowManager.LayoutParams {
        val density = appContext.resources.displayMetrics.density
        val mainSizePx = (Prefs.badgeSizeDp(appContext) * density).toInt().coerceAtLeast(1)
        val badgeSizePx = (mainSizePx * UPCOMING_SCALE).toInt().coerceAtLeast(1)
        // Main badge centre, clamped on-screen (same source as the main badge window).
        val (mcx, mcy) = BadgeLayout.clampCenter(
            Prefs.badgeCenterX(appContext), Prefs.badgeCenterY(appContext), mainSizePx, clusterW, clusterH,
        )
        // B2: 45° LOWER-LEFT — equal offset left (−x) and down (+y) from the main badge centre.
        val diag = (mainSizePx * UPCOMING_DIAG_FRAC).toInt()
        val upcomingCx = mcx - diag
        val upcomingCy = mcy + diag
        val containerW = (mainSizePx * UPCOMING_CONTAINER_W_FRAC).toInt().coerceAtLeast(mainSizePx)
        return WindowManager.LayoutParams(
            containerW,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            // The vertical container centres the badge horizontally at its top; place that badge centre at
            // (upcomingCx, upcomingCy) — 45° lower-left of the main badge.
            x = upcomingCx - containerW / 2
            y = upcomingCy - badgeSizePx / 2
        }
    }

    /** Countdown label text: prefer VietMap's raw text ("300 m" / "1,2 km"); else format the metres; else "". */
    private fun upcomingLabelText(distanceText: String?, distanceMeters: Int?): String {
        val raw = distanceText?.trim()
        if (!raw.isNullOrEmpty()) return raw
        if (distanceMeters != null && distanceMeters > 0) return NavParse.formatMeters(distanceMeters)
        return ""
    }

    private fun hideUpcoming() {
        if (!upcomingAttached) return
        upcomingContainer?.visibility = View.INVISIBLE
    }

    /** Detach + drop the upcoming window/views so a fresh [ensureUpcomingContainer] rebuilds cleanly. */
    private fun teardownUpcoming() {
        val container = upcomingContainer
        if (upcomingAttached && container != null) {
            runCatching { clusterWm?.removeView(container) }
                .onFailure { Log.w(TAG, "removeView(upcoming) failed: ${it.message}") }
        }
        upcomingAttached = false
        upcomingContainer = null
        upcomingBadgeView = null
        upcomingDistLabel = null
    }

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
