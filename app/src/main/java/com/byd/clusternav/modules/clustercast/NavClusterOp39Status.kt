package com.byd.clusternav.modules.clustercast

import android.app.Activity
import android.widget.TextView
import com.byd.clusternav.Lang
import com.byd.clusternav.R
import com.byd.clusternav.modules.clustercast.ClusterNavLaneWidget.Op39Status

/**
 * Nav card wiring for the op-39 status line (docs/specs/nav-cluster-op39-selfdiagnose.html · D3).
 *
 * op 39 "simple navigation" (Giữa + ETA) is the ONLY nav-on-cluster mode (owner 2026-08-12); the
 * mode selector and the one-tap self-test button were removed, so this class is now just the honest
 * status line. Kept separate from [MainActivity] to keep it under its LOC budget.
 *
 * Belongs to the NAV track: it only READS the widget's published outcome ([ClusterNavLaneWidget.status])
 * and reflects it on the card. It never touches Cast state and issues no shell command.
 */
internal class NavClusterOp39Status(private val activity: Activity) {

    private var statusView: TextView? = null

    fun bind() {
        statusView = activity.findViewById(R.id.txt_cluster_op39_status)
        refresh()
    }

    /** Reflect the widget's current op-39 outcome on the Nav card. Cheap; safe to call every second. */
    fun refresh() {
        val view = statusView ?: return
        val (text, color) = when (ClusterNavLaneWidget.status) {
            Op39Status.ASSERTED ->
                Lang.t("Cụm: đang hiện Giữa + ETA", "Cluster: showing Centre + ETA") to R.color.ok_green
            Op39Status.GATED_CAST ->
                Lang.t("Cụm: Cast đang bật — nav nhường cụm", "Cluster: Cast on — nav yields the cluster") to R.color.warn_amber
            Op39Status.SHELL_UNREACHABLE ->
                Lang.t("Cụm: chưa gửi được lệnh (kiểm tra kết nối 5555)", "Cluster: command not delivered (check the 5555 link)") to R.color.err_red
            Op39Status.IDLE ->
                Lang.t("Cụm: chờ dẫn đường", "Cluster: waiting for navigation") to R.color.text_secondary
        }
        view.text = text
        view.setTextColor(activity.getColor(color))
    }
}
