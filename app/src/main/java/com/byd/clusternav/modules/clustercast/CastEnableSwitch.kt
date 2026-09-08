package com.byd.clusternav.modules.clustercast

import android.app.Activity
import android.content.Intent
import android.view.View
import android.widget.CompoundButton
import android.widget.Toast
import com.byd.clusternav.Lang
import com.byd.clusternav.modules.clustercast.simplified.SimpleCastCoordinator
import com.byd.clusternav.modules.clustercast.simplified.SimpleCastIntent

/**
 * Master "Bật Cluster Cast" switch (owner request 2026-08-11).
 *
 * Persists one boolean ([SimpleCastPrefs.castEnabled], default FALSE — Cast is opt-in; nav-only is
 * the default as of 2026-08-11) and, on toggle:
 *  - **ON**  → reveal the Cast body, open the projection (cluster ready) and start the floating-bubble
 *              service. Both calls are idempotent, so re-enabling is safe.
 *  - **OFF** → hide the Cast body (show a short hint), Stop any active cast, close the projection
 *              (cluster returns to native gauges) and stop the bubble service — with **no reopen**.
 *              This mirrors [CastDeepRescueAction]'s "stand down, do not reopen", minus the DashCast
 *              force-stop: this is a user preference, not a conflict rescue.
 *
 * **Why navigation still reaches the cluster when OFF:** nav→cluster is an independent broadcast
 * pipeline (`ClusterBroadcaster`) and HUD is a separate output; neither uses the Cast projection.
 * This switch ONLY gates the Cast track (projection + bubble + cast-autostart).
 *
 * **Why the coordinator singleton is left alive** (only the projection is closed): the coordinator
 * owns the shared privileged shell gateway (`coordinator.executeShell`), so it must survive a
 * Cast-off rather than be torn down with the projection.
 */
internal class CastEnableSwitch(
    private val activity: Activity,
    private val coordinator: SimpleCastCoordinator,
) {
    /**
     * Binds the header switch to the collapsible [castBody] and the [disabledHint] shown in its place.
     * The persisted value is applied WITHOUT firing the change listener (listener detached first), so
     * re-opening Home never re-triggers enable/disable side effects.
     */
    fun bind(switch: CompoundButton, castBody: View, disabledHint: View) {
        val enabled = coordinator.prefs.castEnabled()
        switch.setOnCheckedChangeListener(null)
        switch.isChecked = enabled
        applyVisibility(enabled, castBody, disabledHint)
        switch.setOnCheckedChangeListener { _, isChecked ->
            coordinator.prefs.setCastEnabled(isChecked)
            applyVisibility(isChecked, castBody, disabledHint)
            if (isChecked) enable() else disable()
        }
    }

    private fun applyVisibility(enabled: Boolean, castBody: View, disabledHint: View) {
        castBody.visibility = if (enabled) View.VISIBLE else View.GONE
        disabledHint.visibility = if (enabled) View.GONE else View.VISIBLE
    }

    private fun enable() {
        // Cluster ready + bubble back. openProjection is idempotent (R10); startForegroundService is
        // idempotent when the service already runs.
        coordinator.openProjection()
        runCatching {
            activity.startForegroundService(Intent(activity, FloatingBubbleService::class.java))
        }
        Toast.makeText(activity, Lang.t("Đã bật Cluster Cast", "Cluster Cast on"), Toast.LENGTH_SHORT).show()
    }

    private fun disable() {
        // Stand down and DO NOT reopen: return any cast app, close the projection (cluster → native
        // gauges), stop the bubble service. dispatch()/closeProjection() self-queue on the
        // coordinator's serial executor, so the shell work runs off the UI thread.
        runCatching { coordinator.dispatch(SimpleCastIntent.Stop()) }
        runCatching { coordinator.closeProjection() }
        runCatching { activity.stopService(Intent(activity, FloatingBubbleService::class.java)) }
        Toast.makeText(
            activity,
            Lang.t("Đã tắt Cluster Cast · cụm về đồng hồ", "Cluster Cast off · cluster back to gauges"),
            Toast.LENGTH_SHORT,
        ).show()
    }
}
