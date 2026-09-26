package com.byd.clusternav.speedbadge

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
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
import com.byd.clusternav.speedbadge.SpeedBadgeOverlay.Companion.TAG
import com.byd.clusternav.speedbadge.SpeedBadgeOverlay.Companion.UPCOMING_CONTAINER_W_FRAC
import com.byd.clusternav.speedbadge.SpeedBadgeOverlay.Companion.UPCOMING_DIAG_FRAC
import com.byd.clusternav.speedbadge.SpeedBadgeOverlay.Companion.UPCOMING_LABEL_FRAC
import com.byd.clusternav.speedbadge.SpeedBadgeOverlay.Companion.UPCOMING_SCALE

/**
 * ═══ VAI "badge sắp tới" (cửa sổ THỨ HAI) của [SpeedBadgeOverlay] ═══════════════════════════════════════════
 *
 * Tách khỏi `SpeedBadgeOverlay.kt` (600 dòng → trần 500, CLAUDE.md §4.1) theo VAI, đúng khuôn `DEBT-500`
 * (`VoiceSession`→`VoiceSessionTurns`): là **hàm mở rộng của chính [SpeedBadgeOverlay]**, dùng lại ĐÚNG các
 * trường của overlay (`clusterWm` · `badgeView` · `clusterW/H` · `upcoming*` · `lastUpcoming*`, nay `internal`),
 * nên bề mặt gọi trong overlay (`doSetUpcoming(...)` · `teardownUpcoming()` · `applyUpcomingMetrics()` ·
 * `buildUpcomingLayoutParams()`) không đổi một ký tự và không có bản sao trạng thái. Thân từng hàm chép NGUYÊN
 * VĂN — cổng `Prefs.badgeEnabled && Prefs.showUpcomingBadge`, phép đặt 45° dưới-trái (B2), thứ tự
 * `initOverlay()` thử lại → `addView`/`updateViewLayout` đều y như trước.
 *
 * Ba vai còn lại vẫn ở tệp overlay: cửa sổ chính (init/show/teardown + `DisplayListener`), chip cảnh báo B3.20
 * (bài canh `AlertChipWiringContractTest` đếm cổng ở đó), và API công khai (`setUpcoming`/`applyUpcomingEnabled`).
 */
// ─── Upcoming "speed-limit ahead" badge (spec upcoming-speed-limit-badge) ──────────────────────────────
// A SECOND window on display 1 holding a vertical container [SpeedBadgeView 80% MUTED] + [distance label],
// placed DIAGONALLY at 45° to the LOWER-LEFT of the main badge (B2). It is ADDITIVE — the current-limit
// badge window is never touched — and fully degrade-safe (runCatching, never throws to the caller). Gated by
// BOTH the master badge gate AND the "Hiện giới hạn sắp tới" toggle.

internal fun SpeedBadgeOverlay.doSetUpcoming(limitKph: Int?, distanceMeters: Int?, distanceText: String?) {
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
internal fun SpeedBadgeOverlay.ensureUpcomingContainer(ctx: Context): LinearLayout? {
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
internal fun SpeedBadgeOverlay.applyUpcomingMetrics() {
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
internal fun SpeedBadgeOverlay.buildUpcomingLayoutParams(): WindowManager.LayoutParams {
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
internal fun SpeedBadgeOverlay.upcomingLabelText(distanceText: String?, distanceMeters: Int?): String {
    val raw = distanceText?.trim()
    if (!raw.isNullOrEmpty()) return raw
    if (distanceMeters != null && distanceMeters > 0) return NavParse.formatMeters(distanceMeters)
    return ""
}

internal fun SpeedBadgeOverlay.hideUpcoming() {
    if (!upcomingAttached) return
    upcomingContainer?.visibility = View.INVISIBLE
}

/** Detach + drop the upcoming window/views so a fresh [ensureUpcomingContainer] rebuilds cleanly. */
internal fun SpeedBadgeOverlay.teardownUpcoming() {
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
