package com.byd.clusternav.modules.clustercast

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import com.byd.clusternav.Lang
import com.byd.clusternav.R
import com.byd.clusternav.cast.platform.CastAppCatalog
import com.byd.clusternav.modules.clustercast.simplified.AppMover
import com.byd.clusternav.modules.clustercast.simplified.ClusterSlotSide
import com.byd.clusternav.modules.clustercast.simplified.SimpleCastIntent
import com.byd.clusternav.modules.clustercast.simplified.SimpleCastRuntime
import com.byd.clusternav.modules.clustercast.simplified.SimpleCastState

/**
 * Cast control panel in Home activity — simplified architecture only.
 *
 * Delegates autostart to [CastAutostart] and geometry editing to [CastGeometryEditor].
 * This file owns: main UI binding, state observation, zone buttons, stop button.
 */
internal class MainActivityCastController(private val activity: Activity) {
    private lateinit var catalog: CastAppCatalog
    @Volatile private var destroyed = false

    private lateinit var status: TextView
    private lateinit var statusDot: View
    private lateinit var stopButton: Button
    private lateinit var diagnosticsButton: Button

    private lateinit var autostart: CastAutostart
    private lateinit var geometryEditor: CastGeometryEditor
    private lateinit var splitRatioButtons: CastSplitRatioButtons
    private lateinit var badgePlacement: BadgePlacementController

    /** Named state listener for cleanup. */
    private val stateListener: (SimpleCastState) -> Unit = { state ->
        postUi { refresh() }
    }

    /** Handler for delayed work — all delayed posts tracked for cleanup. */
    private val handler = Handler(Looper.getMainLooper())
    private val clearClusterReopen = Runnable {
        SimpleCastRuntime.coordinator(activity.applicationContext).openProjection()
    }

    fun onCreate() {
        catalog = CastAppCatalog(activity.applicationContext)
        val coordinator = SimpleCastRuntime.coordinator(activity.applicationContext)

        // Open projection on start — only when the master Cast switch is ON (default OFF — opt-in). When OFF
        // the cluster stays on native gauges; nav→cluster (broadcast) and HUD are independent tracks
        // and keep running regardless. The switch itself is wired via CastEnableSwitch below.
        if (coordinator.prefs.castEnabled()) coordinator.openProjection()

        status = activity.findViewById(R.id.txt_cast_status)
        statusDot = activity.findViewById(R.id.dot_cast)
        stopButton = activity.findViewById<Button>(R.id.cast_stop).apply {
            setOnClickListener { executeStop() }
        }
        diagnosticsButton = activity.findViewById<Button>(R.id.cast_diagnostics).apply {
            setOnClickListener { activity.startActivity(Intent(activity, DiagActivity::class.java)) }
        }

        // Recovery toggle
        val recoveryToggle = activity.findViewById<TextView>(R.id.cast_recovery_toggle)
        val recoveryActions = activity.findViewById<View>(R.id.cast_recovery_actions)
        recoveryToggle.setOnClickListener {
            recoveryActions.visibility = if (recoveryActions.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        // Clear cluster button
        activity.findViewById<Button>(R.id.cast_clear_cluster).apply {
            isEnabled = true
            setOnClickListener {
                coordinator.dispatch(SimpleCastIntent.Stop())
                coordinator.closeProjection()
                Toast.makeText(activity, Lang.t("Đã trả cụm về đồng hồ · đang mở lại…", "Cluster reset · reopening…"), Toast.LENGTH_SHORT).show()
                handler.postDelayed(clearClusterReopen, 2000)
            }
        }

        // Deep rescue (owner 2026-08-11): the DashCast-conflict clean the plain rescue (Stop+reopen)
        // can't do — stand ClusterNav fully down (no reopen), force-stop DashCast, reset the cluster VD.
        CastDeepRescueAction(
            activity = activity,
            coordinator = coordinator,
            background = { block -> Thread(block, "deep-rescue").start() },
            postUi = ::postUi,
            toast = { msg -> Toast.makeText(activity, msg, Toast.LENGTH_LONG).show() },
            stopOwnServices = { activity.stopService(Intent(activity, FloatingBubbleService::class.java)) },
        ).bind(activity.findViewById(R.id.cast_deep_rescue))

        // Zone buttons
        setupZoneButtons(coordinator)

        // Delegates
        autostart = CastAutostart(activity, coordinator)
        autostart.setup()

        // Split-ratio visual buttons (Feature 2) — replaces the old spinner_split_ratio; wires to the
        // LIVE simplified prefs via coordinator.applySplitRatioLive (persist + in-place re-resize).
        splitRatioButtons = CastSplitRatioButtons(activity, coordinator)
        splitRatioButtons.bind()

        geometryEditor = CastGeometryEditor(activity, coordinator)

        // Visual speed-badge placement editor + VietMap bind-status line (spec speed-badge-placement-
        // vietmap-logging §4.2/§4.3). Relocated into this Cast card from the Nav+HUD card; extracted to its
        // own controller so this file stays a thin renderer. Degrade-safe (null-tolerant view lookups).
        badgePlacement = BadgePlacementController(activity).also { it.bind() }

        // Master Cast enable switch — reveals/hides the Cast body and drives projection + bubble.
        CastEnableSwitch(activity, coordinator).bind(
            switch = activity.findViewById(R.id.switch_cast_enabled),
            castBody = activity.findViewById(R.id.cast_body),
            disabledHint = activity.findViewById(R.id.txt_cast_disabled_hint),
        )

        // Initial render
        postUi { refresh() }
    }

    fun onResume() {
        refresh()
        // Bound state can change while away (e.g. returning from the VietMap diag bind flow) — refresh
        // the status line here rather than every tick (bindingStatuses enumerates widget providers).
        if (::badgePlacement.isInitialized) badgePlacement.refreshBindStatus()
    }
    fun tick() { refresh() }

    fun onDestroy() {
        destroyed = true
        // Cancel all delayed work
        handler.removeCallbacksAndMessages(null)
        // Remove state listener
        // (no coordinator listener registered directly here; autostart handles its own)
        autostart.destroy()
        if (::splitRatioButtons.isInitialized) splitRatioButtons.destroy()
        // Projection ownership (R10): when autostart is enabled, FloatingBubbleService owns the
        // projection (opened on boot, kept alive so the auto-cast survives closing this Home screen).
        // Only tear down here when autostart is OFF — the classic Activity-scoped projection.
        val coordinator = SimpleCastRuntime.coordinator(activity.applicationContext)
        val autostartOwns = coordinator.prefs.autoStartEnabled() || coordinator.prefs.autoStartSplitEnabled()
        // Only tear down a projection we actually opened. When Cast is disabled we never opened one,
        // so skip the close entirely (avoids needless shell work on the cluster VD).
        if (coordinator.prefs.castEnabled() && !autostartOwns) coordinator.closeProjection()
    }

    private fun setupZoneButtons(coordinator: com.byd.clusternav.modules.clustercast.simplified.SimpleCastCoordinator) {
        activity.findViewById<Button>(R.id.cast_zone_full).apply {
            contentDescription = Lang.t("Chiếu full cụm", "Cast full cluster")
        }.setOnClickListener {
            val state = coordinator.state
            if (state is SimpleCastState.CastingFull || state is SimpleCastState.CastingSplit) {
                coordinator.dispatch(SimpleCastIntent.Stop())
            } else {
                showAppPicker(null)
            }
        }
        activity.findViewById<Button>(R.id.cast_zone_left).apply {
            contentDescription = Lang.t("Chiếu nửa trái", "Cast left half")
        }.setOnClickListener {
            val state = coordinator.state
            if (state is SimpleCastState.CastingSplit && state.left != null) {
                coordinator.dispatch(SimpleCastIntent.Stop(ClusterSlotSide.LEFT))
            } else {
                showAppPicker(ClusterSlotSide.LEFT)
            }
        }
        activity.findViewById<Button>(R.id.cast_zone_right).apply {
            contentDescription = Lang.t("Chiếu nửa phải", "Cast right half")
        }.setOnClickListener {
            val state = coordinator.state
            if (state is SimpleCastState.CastingSplit && state.right != null) {
                coordinator.dispatch(SimpleCastIntent.Stop(ClusterSlotSide.RIGHT))
            } else {
                showAppPicker(ClusterSlotSide.RIGHT)
            }
        }
    }

    private fun executeStop() {
        SimpleCastRuntime.coordinator(activity.applicationContext).dispatch(SimpleCastIntent.Stop())
        status.text = Lang.t("Đang trả app về…", "Returning app…")
    }

    private fun showAppPicker(slot: ClusterSlotSide?) {
        val launchIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolveInfos = activity.packageManager.queryIntentActivities(launchIntent, 0)
        val excluded = setOf(activity.packageName, "com.android.launcher", "com.android.launcher3")
        val apps = resolveInfos
            .filter { it.activityInfo.packageName !in excluded }
            .map { it.loadLabel(activity.packageManager).toString() to it.activityInfo.packageName }
            .distinctBy { it.second }
            .sortedBy { it.first.lowercase() }

        val labels = apps.map { it.first }.toTypedArray()
        AlertDialog.Builder(activity)
            .setTitle(Lang.t("Chọn app chiếu", "Choose app to cast"))
            .setItems(labels) { dialog, which ->
                val pkg = apps[which].second
                val coordinator = SimpleCastRuntime.coordinator(activity.applicationContext)
                if (slot == null) {
                    coordinator.dispatch(SimpleCastIntent.CastFull(pkg, AppMover.classifyApp(pkg)))
                } else {
                    coordinator.dispatch(SimpleCastIntent.CastSlot(pkg, slot))
                }
                dialog.dismiss()
            }
            .show()
    }

    private fun refresh() {
        if (destroyed || activity.isFinishing || activity.isDestroyed) return
        val state = SimpleCastRuntime.coordinator(activity.applicationContext).state
        status.text = when (state) {
            is SimpleCastState.Off -> Lang.t("Tắt", "Off")
            is SimpleCastState.Opening -> Lang.t("Đang mở cụm…", "Opening cluster…")
            is SimpleCastState.Idle -> Lang.t("Sẵn sàng · bấm nút nổi để chiếu", "Ready · tap bubble to cast")
            is SimpleCastState.CastingFull -> Lang.t(
                "Đang chiếu: ${state.targetPkg.substringAfterLast('.')}",
                "Casting: ${state.targetPkg.substringAfterLast('.')}"
            )
            is SimpleCastState.CastingSplit -> {
                val l = state.left?.pkg?.substringAfterLast('.') ?: "—"
                val r = state.right?.pkg?.substringAfterLast('.') ?: "—"
                Lang.t("Chia đôi: $l | $r", "Split: $l | $r")
            }
            is SimpleCastState.Stopping -> Lang.t("Đang trả app về…", "Returning app…")
            is SimpleCastState.Closing -> Lang.t("Đang đóng cụm…", "Closing cluster…")
            is SimpleCastState.Error -> Lang.t("Lỗi: ${state.message}", "Error: ${state.message}")
            else -> state.toString()
        }
        statusDot.tint(
            when (state) {
                is SimpleCastState.CastingFull, is SimpleCastState.CastingSplit -> R.color.ok_green
                is SimpleCastState.Error -> R.color.err_red
                else -> R.color.warn_amber
            },
        )
        stopButton.isEnabled = state is SimpleCastState.CastingFull || state is SimpleCastState.CastingSplit
        diagnosticsButton.isEnabled = true

        updateZoneButtons(state)
        geometryEditor.updateVisibility(state)
    }

    private fun updateZoneButtons(state: SimpleCastState) {
        val btnFull = activity.findViewById<Button>(R.id.cast_zone_full)
        val btnLeft = activity.findViewById<Button>(R.id.cast_zone_left)
        val btnRight = activity.findViewById<Button>(R.id.cast_zone_right)
        when (state) {
            is SimpleCastState.CastingFull -> {
                btnFull.text = Lang.t("Đang chiếu: ${state.targetPkg.substringAfterLast('.')}", "Casting: ${state.targetPkg.substringAfterLast('.')}")
                btnFull.setBackgroundResource(R.drawable.btn_warning_outline)
                btnLeft.isEnabled = false
                btnRight.isEnabled = false
            }
            is SimpleCastState.CastingSplit -> {
                btnFull.text = Lang.t("Chiếu full cụm", "Cast full")
                btnFull.setBackgroundResource(R.drawable.btn_primary)
                btnFull.isEnabled = false
                val leftSlot = state.left
                btnLeft.text = if (leftSlot != null)
                    Lang.t("${leftSlot.pkg.substringAfterLast('.')}", "${leftSlot.pkg.substringAfterLast('.')}")
                else Lang.t("Trái", "Left")
                val rightSlot = state.right
                btnRight.text = if (rightSlot != null)
                    Lang.t("${rightSlot.pkg.substringAfterLast('.')}", "${rightSlot.pkg.substringAfterLast('.')}")
                else Lang.t("Phải", "Right")
                btnLeft.isEnabled = true
                btnRight.isEnabled = true
            }
            is SimpleCastState.Idle -> {
                btnFull.text = Lang.t("Chiếu full cụm", "Cast full")
                btnFull.setBackgroundResource(R.drawable.btn_primary)
                btnFull.isEnabled = true
                btnLeft.text = Lang.t("Trái", "Left")
                btnLeft.isEnabled = true
                btnLeft.setBackgroundResource(R.drawable.btn_outline)
                btnRight.text = Lang.t("Phải", "Right")
                btnRight.isEnabled = true
                btnRight.setBackgroundResource(R.drawable.btn_outline)
            }
            else -> {
                btnFull.text = Lang.t("Chiếu full cụm", "Cast full")
                btnFull.isEnabled = false
                btnLeft.text = Lang.t("Trái", "Left")
                btnLeft.isEnabled = false
                btnRight.text = Lang.t("Phải", "Right")
                btnRight.isEnabled = false
            }
        }
    }

    private fun View.tint(color: Int) {
        backgroundTintList = android.content.res.ColorStateList.valueOf(activity.getColor(color))
    }

    private fun postUi(block: () -> Unit) =
        activity.runOnUiThread { if (!destroyed && !activity.isFinishing && !activity.isDestroyed) block() }
}
