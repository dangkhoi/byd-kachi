package com.byd.clusternav.modules.clustercast

import android.app.Activity
import android.widget.FrameLayout
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import com.byd.clusternav.Lang
import com.byd.clusternav.NavigationSpeedSignOwner
import com.byd.clusternav.Prefs
import com.byd.clusternav.R
import com.byd.clusternav.VietMapAutostartService
import com.byd.clusternav.modules.clustercast.simplified.SimpleCastRuntime
import com.byd.clusternav.modules.clustercast.simplified.SimpleCastState
import com.byd.clusternav.speedbadge.BadgeLayout
import com.byd.clusternav.vietmapwidget.VietMapWidgetBridge

/**
 * Wires the Cluster-Cast card's "speed badge on the cluster" block: the visual [BadgePlacementView],
 * the size slider, and the VietMap bind-status line. Extracted from
 * [MainActivityCastController] so both files stay thin (< 500 LOC) — this owns ONLY the badge-placement +
 * bind-status UI; the speed-source spinner and the VietMap-diag button keep their existing MainActivity
 * listeners (same ids, just relocated into this card).
 *
 * Persistence + live apply: a drag writes the clamped centre via [Prefs.setBadgeCenterX]/[Prefs.setBadgeCenterY]
 * and the slider writes [Prefs.setBadgeSizeDp]; both then call
 * [NavigationSpeedSignOwner.debugRefreshBadgeLayout] so the ONE shared badge overlay (BUG-1) moves/resizes
 * on the cluster immediately. Everything is null-tolerant so a layout variant missing an id can't crash Home.
 */
internal class BadgePlacementController(private val activity: Activity) {

    private var view: BadgePlacementView? = null
    private var bindStatus: TextView? = null

    private val density: Float get() = activity.resources.displayMetrics.density

    fun bind() {
        val container = activity.findViewById<FrameLayout>(R.id.badge_placement_container) ?: return
        val (clusterW, clusterH) = clusterSize()

        // Badge on/off toggle (owner 2026-08-18, default ON). Detach listener before setting the persisted
        // state so restoring isChecked never fires a spurious enable/disable, then persist + re-evaluate the
        // shared overlay gate on user changes. Null-tolerant so a layout variant missing the id can't crash.
        activity.findViewById<Switch>(R.id.switch_badge_enabled)?.apply {
            setOnCheckedChangeListener(null)
            isChecked = Prefs.badgeEnabled(activity)
            setOnCheckedChangeListener { _, checked ->
                Prefs.setBadgeEnabled(activity, checked)
                // Giống toggle bong bóng VietMap: bật badge ⇒ auto-start VietMap MỘT LẦN (dedup pidof) để widget
                // có nguồn tốc-độ ngay, không đợi lần mở app kế tiếp. Chạy trong FGS riêng (VietMapAutostartService,
                // độc lập + không block). Chỉ khi bật (checked) — tắt không đụng gì.
                if (checked) VietMapAutostartService.startForAppOpen(activity)
                NavigationSpeedSignOwner.get(activity.applicationContext).onBadgeEnabledChanged()
            }
        }

        // "Hiện giới hạn sắp tới" toggle (spec upcoming-speed-limit-badge, default ON). Same detach-before-restore
        // pattern so restoring isChecked never fires a spurious toggle; persist + re-evaluate the shared overlay.
        activity.findViewById<Switch>(R.id.switch_upcoming_badge)?.apply {
            setOnCheckedChangeListener(null)
            isChecked = Prefs.showUpcomingBadge(activity)
            setOnCheckedChangeListener { _, checked ->
                Prefs.setShowUpcomingBadge(activity, checked)
                NavigationSpeedSignOwner.get(activity.applicationContext).onUpcomingBadgeEnabledChanged()
            }
        }

        // "Hiện cảnh báo/camera VietMap" toggle (B3.20, default OFF — opt-in, không phá bố trí badge). Same
        // detach-before-restore pattern; null-tolerant so a layout variant missing the id can't crash onCreate.
        activity.findViewById<Switch>(R.id.switch_alert_chip)?.apply {
            setOnCheckedChangeListener(null)
            isChecked = Prefs.showAlertChip(activity)
            setOnCheckedChangeListener { _, checked ->
                Prefs.setShowAlertChip(activity, checked)
                NavigationSpeedSignOwner.get(activity.applicationContext).onAlertChipEnabledChanged()
            }
        }

        val placement = BadgePlacementView(activity, clusterW, clusterH) { cx, cy ->
            // Persist the dragged centre (re-clamped on the ACTUAL cluster) + apply the shared badge LIVE.
            val (ccx, ccy) = BadgeLayout.clampCenter(cx, cy, badgeSizePx(), clusterW, clusterH)
            Prefs.setBadgeCenterX(activity, ccx)
            Prefs.setBadgeCenterY(activity, ccy)
            NavigationSpeedSignOwner.get(activity.applicationContext).debugRefreshBadgeLayout()
        }
        placement.setBadgeSizeCluster(badgeSizePx())
        placement.setBadgeCenterCluster(Prefs.badgeCenterX(activity), Prefs.badgeCenterY(activity))
        container.removeAllViews()
        container.addView(
            placement,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        view = placement

        // Size slider: SeekBar progress 0..(MAX-MIN) maps to dp SIZE_MIN_DP..SIZE_MAX_DP (60..240).
        activity.findViewById<SeekBar>(R.id.seek_badge_size)?.apply {
            max = BadgeLayout.SIZE_MAX_DP - BadgeLayout.SIZE_MIN_DP
            progress = (Prefs.badgeSizeDp(activity) - BadgeLayout.SIZE_MIN_DP).coerceIn(0, max)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    Prefs.setBadgeSizeDp(activity, BadgeLayout.SIZE_MIN_DP + progress)
                    view?.setBadgeSizeCluster(badgeSizePx())
                    NavigationSpeedSignOwner.get(activity.applicationContext).debugRefreshBadgeLayout()
                }
                override fun onStartTrackingTouch(seekBar: SeekBar) {}
                override fun onStopTrackingTouch(seekBar: SeekBar) {}
            })
        }

        bindStatus = activity.findViewById(R.id.txt_speed_source_bind)
        refreshBindStatus()
    }

    /**
     * Reflect the VietMap widget bind state in [R.id.txt_speed_source_bind]. Cheap read (prefs + widget
     * manager), safe without an active listening session; wrapped in runCatching so a widget-manager hiccup
     * never crashes Home. Called on bind() and whenever Home resumes (e.g. returning from the VietMap diag).
     */
    fun refreshBindStatus() {
        val label = bindStatus ?: return
        label.text = runCatching {
            val statuses = VietMapWidgetBridge.get(activity.applicationContext).bindingStatuses()
            when {
                statuses.any { !it.providerAvailable } ->
                    Lang.t("Nguồn: chưa cài VietMap", "Source: VietMap not installed")
                statuses.isNotEmpty() && statuses.all { it.bound } ->
                    Lang.t("Nguồn: đã kết nối (tốc độ + cảnh báo)", "Source: connected (speed + alerts)")
                statuses.any { it.bound } ->
                    Lang.t("Nguồn: kết nối một phần", "Source: partially connected")
                else -> Lang.t("Nguồn: chưa kết nối", "Source: not connected")
            }
        }.getOrDefault(Lang.t("Nguồn: chưa kết nối", "Source: not connected"))
    }

    private fun badgeSizePx(): Int = (Prefs.badgeSizeDp(activity) * density).toInt().coerceAtLeast(1)

    /** Cluster W/H from the active cast display config (fallback 1920×720, the Seal cluster). */
    private fun clusterSize(): Pair<Int, Int> {
        val wmSize = when (val state = SimpleCastRuntime.coordinator(activity.applicationContext).state) {
            is SimpleCastState.CastingFull -> state.displayConfig.wmSize
            is SimpleCastState.CastingSplit -> state.left?.displayConfig?.wmSize ?: state.right?.displayConfig?.wmSize
            else -> null
        }
        val parts = wmSize?.split("x")
        val w = parts?.getOrNull(0)?.toIntOrNull() ?: 1920
        val h = parts?.getOrNull(1)?.toIntOrNull() ?: 720
        return w to h
    }
}
