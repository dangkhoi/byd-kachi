package com.byd.clusternav

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.byd.clusternav.contracts.SpeedLimitSource
import com.byd.clusternav.contracts.SpeedSignType
import com.byd.clusternav.navigation.SpeedSignLifecycleCoordinator
import com.byd.clusternav.navigation.SpeedSignOutput
import com.byd.clusternav.speedbadge.ClusterSpeedBadgePort
import com.byd.clusternav.speedbadge.HalSpeedSignPort
import com.byd.clusternav.speedbadge.SpeedBadgeOverlay

/**
 * Android lifecycle facade for the typed speed-sign coordinator.
 *
 * Cluster port renders a speed-limit badge overlay on display 1 (TYPE_APPLICATION_OVERLAY).
 * HUD port writes STATISTICS_ISA_CURRENT_ROAD_SPEED_LIMIT_SET (0x4B40001C) via BydHal reflection.
 * Both ports degrade gracefully to no-op off-car (no display 1 / no HAL device).
 */
class NavigationSpeedSignOwner private constructor(private val appContext: Context) : AutoCloseable {
    private val processEpoch = SystemClock.elapsedRealtimeNanos().coerceAtLeast(1L)

    // BUG-1 (2026-08-17): exactly ONE SpeedBadgeOverlay on display 1, shared by the real cluster port AND the
    // debug force-show below — so there is a single badge WINDOW, not two fighting overlays (the old code had
    // ClusterSpeedBadgePort's own overlay PLUS a separate lazy debug overlay, giving two badges on-car and only
    // the debug one was adjustable). Declared BEFORE `coordinator` so it is initialized first (Kotlin property
    // init order) and can be injected into the port. Degrade-safe to no-op off-car (no display 1).
    private val badgeOverlay = SpeedBadgeOverlay(appContext)

    private val coordinator = SpeedSignLifecycleCoordinator(
        clusterPort = ClusterSpeedBadgePort(badgeOverlay),
        hudPort = HalSpeedSignPort(appContext),
        monotonicNowMs = SystemClock::elapsedRealtime,
    ).also { it.onProcessRestart(processEpoch) }

    fun syncFromPrefs() {
        onSourceSelected(Prefs.speedLimitSource(appContext))
        onOutputEnabled(SpeedSignOutput.CLUSTER, Prefs.lane(appContext))
        onOutputEnabled(SpeedSignOutput.HUD, Prefs.hud(appContext))
        onMasterEnabled(Prefs.enabled(appContext))
    }

    fun onMasterEnabled(enabled: Boolean) {
        coordinator.onMasterEnabled(enabled)
    }

    fun onSourceSelected(source: SpeedLimitSource) {
        coordinator.onSourceSelected(source)
    }

    fun onOutputEnabled(output: SpeedSignOutput, enabled: Boolean) {
        coordinator.onOutputEnabled(output, enabled)
    }

    fun onSpeedLimit(
        source: SpeedLimitSource,
        valueKph: Int,
        observedAtMonotonicMs: Long = SystemClock.elapsedRealtime(),
    ): Boolean = coordinator.onSpeedLimit(source, valueKph, observedAtMonotonicMs, processEpoch)

    fun onProviderDisconnected(source: SpeedLimitSource) {
        coordinator.onProviderDisconnected(source)
    }

    fun onSourceStopped(source: SpeedLimitSource) {
        coordinator.onSourceStopped(source)
    }

    // ─── T5 (telemetry): force-show badge ────────────────────────────────────
    // Isolates the OVERLAY-RENDER question ("does the badge draw over cast GMaps at all?") from the
    // VietMap-DATA question. Operates on the SAME shared [badgeOverlay] as the real speed-sign pipeline
    // (BUG-1 unify) so the forced badge and the real badge are literally one window — what you tune with the
    // placement UI is exactly what force-show displays. The coordinator's ports, generation fencing and
    // lifecycle are untouched; this only calls the overlay's own show/hide/refresh directly.

    /** Debug-only: force a fixed badge (e.g. 50) on the cluster (display 1). Shares the live overlay window. */
    fun debugForceBadge(valueKph: Int) {
        runCatching { badgeOverlay.show(valueKph, SpeedSignType.REGULATORY) }
            .onFailure { Log.w(TAG, "debugForceBadge failed", it) }
    }

    /** Debug-only: hide the forced badge. */
    fun debugHideBadge() {
        runCatching { badgeOverlay.hide() }
            .onFailure { Log.w(TAG, "debugHideBadge failed", it) }
    }

    /**
     * Debug-only: re-apply the badge layout (absolute centre / size from Prefs) to the shared badge LIVE, so
     * the driver moving the placement controls sees it move/resize on the cluster immediately. Degrade-safe.
     * Because the real pipeline uses the SAME overlay, this is the one and only badge window — no divergence
     * between a "debug" badge and the "real" badge.
     */
    fun debugRefreshBadgeLayout() {
        runCatching { badgeOverlay.refreshLayout() }
            .onFailure { Log.w(TAG, "debugRefreshBadgeLayout failed", it) }
    }

    /**
     * Owner 2026-08-18: the cluster-badge on/off toggle changed ([Prefs.badgeEnabled]). Re-evaluate the shared
     * overlay's enabled gate — detach when disabled, re-show the last value when re-enabled. Degrade-safe.
     */
    fun onBadgeEnabledChanged() {
        runCatching { badgeOverlay.applyEnabled() }
            .onFailure { Log.w(TAG, "onBadgeEnabledChanged failed", it) }
    }

    /**
     * Set (or clear) the "upcoming speed-limit ahead" badge on the shared cluster overlay (spec
     * `upcoming-speed-limit-badge`). A null/<=0 [limitKph] hides it. [distText] is VietMap's raw distance
     * label ("300 m" / "1,2 km") for the countdown, with [distM] the parsed metres as a fallback. Degrade-safe.
     */
    fun setUpcomingBadge(limitKph: Int?, distM: Int?, distText: String?) {
        runCatching { badgeOverlay.setUpcoming(limitKph, distM, distText) }
            .onFailure { Log.w(TAG, "setUpcomingBadge failed", it) }
    }

    /** The "Hiện giới hạn sắp tới" toggle changed ([Prefs.showUpcomingBadge]) — re-evaluate the shared overlay. */
    fun onUpcomingBadgeEnabledChanged() {
        runCatching { badgeOverlay.applyUpcomingEnabled() }
            .onFailure { Log.w(TAG, "onUpcomingBadgeEnabledChanged failed", it) }
    }

    /**
     * B3.20 — set (or clear) the road-alert / speed-camera chip on the cluster (VietMap sticky ALERTS slot).
     * [show]=false hides it; [limitKph] ≤ 0 draws no limit circle; [distText] = countdown; [hasIcon] = draw the
     * camera glyph. Gated inside the overlay by master badge AND [Prefs.showAlertChip]. Degrade-safe.
     */
    fun setRoadAlertChip(show: Boolean, limitKph: Int, distText: String?, hasIcon: Boolean) {
        runCatching { badgeOverlay.setAlert(show, limitKph, distText, hasIcon) }
            .onFailure { Log.w(TAG, "setRoadAlertChip failed", it) }
    }

    /** The "Hiện cảnh báo/camera" toggle changed ([Prefs.showAlertChip]) — re-evaluate the shared overlay. */
    fun onAlertChipEnabledChanged() {
        runCatching { badgeOverlay.applyAlertChipEnabled() }
            .onFailure { Log.w(TAG, "onAlertChipEnabledChanged failed", it) }
    }

    override fun close() {
        coordinator.close()
    }

    companion object {
        private const val TAG = "NavigationSpeedSign"
        @Volatile private var instance: NavigationSpeedSignOwner? = null

        fun get(context: Context): NavigationSpeedSignOwner = instance ?: synchronized(this) {
            instance ?: NavigationSpeedSignOwner(context.applicationContext).also {
                instance = it
                it.syncFromPrefs()
                Log.i(TAG, "typed lifecycle active with ClusterSpeedBadge + HalSpeedSign ports")
            }
        }
    }
}
