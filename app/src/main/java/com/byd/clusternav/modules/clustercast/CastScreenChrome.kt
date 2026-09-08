package com.byd.clusternav.modules.clustercast

import android.app.Activity
import android.view.View
import android.widget.TextView
import com.byd.clusternav.Lang
import com.byd.clusternav.R

/**
 * Screen chrome for Cluster Cast: the parts that are presentation only.
 *
 * The recovery actions are five controls that matter in rare states and are dead weight the rest of
 * the time. On the vehicle they rendered as a permanent wall of eight greyed boxes competing with the
 * one button the driver actually wants, so they now live behind a disclosure row that starts closed.
 * Nothing about their behaviour or their enabled state changes here — that stays owned by the
 * projected UI state — only whether the group is on screen.
 *
 * Kept out of the activity so the screen's control flow stays readable.
 */
object CastScreenChrome {

    private val CLOSED get() = Lang.t("Khắc phục sự cố", "Troubleshooting")
    private val OPEN get() = Lang.t("Khắc phục sự cố — đang mở", "Troubleshooting — open")

    fun bind(activity: Activity) {
        val toggle = activity.findViewById<TextView>(R.id.cast_recovery_toggle) ?: return
        val group = activity.findViewById<View>(R.id.cast_recovery_actions) ?: return
        apply(toggle, group, group.visibility == View.VISIBLE)
        toggle.setOnClickListener { apply(toggle, group, group.visibility != View.VISIBLE) }
    }

    private fun apply(toggle: TextView, group: View, open: Boolean) {
        group.visibility = if (open) View.VISIBLE else View.GONE
        toggle.text = if (open) OPEN else CLOSED
        toggle.contentDescription =
            if (open) Lang.t("Đóng nhóm hành động khắc phục sự cố", "Close troubleshooting actions") else Lang.t("Mở nhóm hành động khắc phục sự cố", "Open troubleshooting actions")
    }
}
