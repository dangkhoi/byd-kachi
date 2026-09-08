package com.byd.clusternav.modules.clustercast

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log

/**
 * V2 boot automation service — disabled since 2026-08-03.
 *
 * Simplified Cast architecture owns projection lifecycle now; V2 CastAndroidRuntime is no longer
 * instantiated on boot. This service remains registered in the manifest only to avoid
 * ClassNotFoundException from pending intents that may still be queued from previous boots.
 * It stops itself immediately on any start command.
 */
class CastAutomationService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "V2 automation disabled — simplified coordinator active. Stopping.")
        stopSelf(startId)
        return START_NOT_STICKY
    }

    companion object {
        private const val TAG = "CastAutomation"

        /**
         * Post-unlock boot entry — now a no-op.
         *
         * 2026-08-03: V2 automation removed. Simplified Cast handles auto-start via
         * SimpleCastPrefs.autoStartEnabled() in MainActivityCastController.onCreate().
         */
        @Suppress("unused", "UNUSED_PARAMETER")
        fun recordAndEnqueue(context: Context): Any? {
            Log.i(TAG, "V2 boot automation disabled — simplified coordinator active")
            return null
        }
    }
}
