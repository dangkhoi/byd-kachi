package com.byd.clusternav.modules.clustercast

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.Window
import android.view.WindowManager
import com.byd.clusternav.ThemeMode

/**
 * Black full-screen placeholder activity for the cluster display.
 *
 * Launched on display 1 immediately after projection opens (30/16/35) to ensure the OEM firmware
 * keeps projection active. Without content on display 1, the firmware auto-closes projection
 * after a short timeout — measured on vehicle 2026-08-03.
 *
 * When a real app is cast, it replaces this activity on display 1.
 * When cast stops, this activity remains to keep projection alive (cluster stays black/ready).
 */
class ClusterBlackActivity : Activity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(ThemeMode.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            WindowManager.LayoutParams.FLAG_FULLSCREEN or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
        )
        val view = View(this)
        view.setBackgroundColor(Color.BLACK)
        setContentView(view)
    }
}
