package com.byd.clusternav.modules.clustercast

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.byd.clusternav.Lang
import com.byd.clusternav.R

/**
 * Thông báo thường trú của [FloatingBubbleService] — tách khỏi dịch vụ ở WP6 vì tệp đó **537 dòng > trần 500**
 * (CLAUDE.md §4.1) trước lượt này. Lượt tách không đổi một byte nội dung: cùng kênh, cùng mức, cùng đích chạm.
 *
 * Dịch vụ được khởi bằng `startForegroundService()`, nên Android đòi `startForeground()` trong ~5 giây — đó là lý do
 * cái thông báo này tồn tại. Nó KHÔNG phải một bề mặt thông tin: mức `IMPORTANCE_LOW`, `setOngoing`, không âm,
 * không số.
 */
internal object BubbleForegroundNotice {

    const val ID = 1042

    /**
     * Chạm thông báo ⇒ mở thẳng nhóm *Chiếu cụm* của Kachi Settings (S3 · R1). Màn ClusterNav cũ — đích của
     * `PendingIntent` này trước 2026-09-13 — đã gỡ; "điều khiển" mà câu chữ nói tới nay nằm đúng ở nhóm đó.
     */
    fun build(context: Context): Notification {
        val channel = "cluster_cast_v2"
        if (Build.VERSION.SDK_INT >= 26) {
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(
                    NotificationChannel(channel, "Cluster Cast", NotificationManager.IMPORTANCE_LOW),
                )
        }
        val pending = PendingIntent.getActivity(
            context, 0,
            Intent(context, com.byd.clusternav.launcher.KachiHomeActivity::class.java)
                .putExtra(
                    com.byd.clusternav.launcher.EXTRA_OPEN_SETTINGS_GROUP,
                    com.byd.clusternav.launcher.SettingsGroup.CAST.id,
                ),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        @Suppress("DEPRECATION")
        return Notification.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("Cluster Cast")
            .setContentText(Lang.t("Nhấn để mở điều khiển", "Tap to open controls"))
            .setOngoing(true)
            .setContentIntent(pending)
            .build()
    }
}
