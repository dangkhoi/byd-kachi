package com.byd.clusternav.modules.clustercast

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log

/** Cast-owned same-boot revalidation trigger. It observes durable truth and never emits mutation. */
class CastLifecycleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_REVALIDATE) return
        // 2026-08-03: V2 lifecycle disabled — simplified coordinator owns projection now.
        Log.i(TAG, "V2 lifecycle disabled — simplified coordinator active")
    }

    companion object {
        const val ACTION_REVALIDATE = "com.byd.clusternav.action.CAST_V2_REVALIDATE"
        private const val TAG = "CastLifecycle"
        private const val REQUEST_CODE = 2207
        private const val INTERVAL_MS = 60_000L

        fun schedule(context: Context) {
            val alarm = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(context, CastLifecycleReceiver::class.java).setAction(ACTION_REVALIDATE)
            val pending = PendingIntent.getBroadcast(
                context, REQUEST_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            runCatching {
                alarm.setInexactRepeating(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    SystemClock.elapsedRealtime() + INTERVAL_MS,
                    INTERVAL_MS,
                    pending,
                )
            }.onFailure { Log.e(TAG, "Unable to schedule Cast lifecycle revalidation", it) }
        }
    }
}
