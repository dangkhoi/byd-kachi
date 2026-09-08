package com.byd.clusternav.modules.clustercast

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.byd.clusternav.cast.platform.CastAppCatalog

/**
 * Single owner of the floating-bubble opt-in so the Cluster Cast screen toggle and the app-manager
 * dialog cannot drift apart.
 *
 * Presentation only: enabling never starts anything without overlay permission, and disabling only
 * stops the overlay host. Neither path sends a Cast request, so this can never move the cluster.
 *
 * The opt-in is durable, so the user grants overlay once and the bubble returns by itself on the next
 * launch and on boot (see RebindReceiver, which requires both the opt-in and the permission).
 */
object CastBubbleControl {

    fun optedIn(context: Context): Boolean = CastAppCatalog(context).bubbleEnabled()

    fun overlayGranted(context: Context): Boolean = Settings.canDrawOverlays(context)

    /** True when the bubble is both wanted and permitted, which is what the toggle should show. */
    fun effective(context: Context): Boolean = optedIn(context) && overlayGranted(context)

    /**
     * Records the opt-in and brings the overlay host in line with it. Returns whether the host is
     * actually running afterwards, which is false when the opt-in is recorded but the permission is
     * still missing.
     */
    fun apply(context: Context, enabled: Boolean): Boolean {
        CastAppCatalog(context).setBubbleEnabled(enabled)
        val intent = Intent(context, FloatingBubbleService::class.java)
        if (!enabled) {
            runCatching { context.stopService(intent) }
            return false
        }
        if (!overlayGranted(context)) return false
        runCatching { context.startForegroundService(intent) }
        return true
    }

    /**
     * Owns the toggle wiring too, so the Activity does not grow a second copy of this policy: show
     * the effective state, record the opt-in on change, and send the user to the permission screen
     * exactly once when the opt-in cannot take effect yet.
     */
    fun bind(context: Context, toggle: android.widget.Switch, notify: (String) -> Unit) {
        toggle.setOnCheckedChangeListener(null)
        toggle.isChecked = effective(context)
        toggle.setOnCheckedChangeListener { _, wanted ->
            if (!apply(context, wanted) && wanted) {
                notify("Cần cho phép hiển thị trên ứng dụng khác — bật xong nút nổi tự chạy")
                requestOverlay(context)
            }
        }
    }

    /** Re-applies a durable opt-in after the user returns from the system permission screen. */
    fun rebind(context: Context, toggle: android.widget.Switch, notify: (String) -> Unit) {
        if (optedIn(context)) apply(context, true)
        bind(context, toggle, notify)
    }

    /** Sends the user to the one system screen that can grant the overlay permission. */
    fun requestOverlay(context: Context) {
        runCatching {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + context.packageName),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}
