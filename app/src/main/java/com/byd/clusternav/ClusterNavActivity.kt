package com.byd.clusternav

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView

/**
 * Card dẫn đường vẽ lên cụm (display 1920×720). Launch:
 *   am start --display <clusterId> -n com.byd.clusternav2/.ClusterNavActivity
 * Thêm extra demo=true để chạy dữ liệu giả (test trên emulator).
 */
class ClusterNavActivity : Activity() {

    private lateinit var arrow: ImageView
    private lateinit var distance: TextView
    private lateinit var road: TextView
    private lateinit var eta: TextView
    private lateinit var idle: TextView

    private val listener: (NavState) -> Unit = { render(it) }
    private var demo = false
    private val demoHandler = Handler(Looper.getMainLooper())
    private var demoDist = 800

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(ThemeMode.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
        setContentView(R.layout.activity_cluster_nav)
        arrow = findViewById(R.id.arrow)
        distance = findViewById(R.id.distance)
        road = findViewById(R.id.road)
        eta = findViewById(R.id.eta)
        idle = findViewById(R.id.idle)
        demo = intent?.getBooleanExtra("demo", false) == true
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        demo = intent?.getBooleanExtra("demo", false) == true
    }

    override fun onResume() {
        super.onResume()
        hideSystemUi()
        // Luôn observe NavRepository để vẽ. Demo chỉ BƠM THÊM data giả vào repo (qua listener mà vẽ).
        // Demo (emulator) và live (xe có Google Maps) không bao giờ chạy đồng thời.
        NavRepository.addListener(listener)
        if (demo) startDemo()
    }

    override fun onPause() {
        super.onPause()
        NavRepository.removeListener(listener)
        demoHandler.removeCallbacksAndMessages(null)
    }

    private fun render(s: NavState) {
        if (!s.active) {
            idle.visibility = View.VISIBLE
            arrow.visibility = View.GONE
            distance.text = ""
            road.text = ""
            eta.text = ""
            return
        }
        idle.visibility = View.GONE
        when {
            s.arrowRes != 0 -> { arrow.setImageResource(s.arrowRes); arrow.visibility = View.VISIBLE }
            s.arrow != null -> { arrow.setImageBitmap(s.arrow); arrow.visibility = View.VISIBLE }
            else -> arrow.visibility = View.INVISIBLE
        }
        distance.text = s.distance
        road.text = s.road
        eta.text = s.eta
    }

    @Suppress("DEPRECATION")
    private fun hideSystemUi() {
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            )
    }

    // Demo: luân phiên các maneuver để minh hoạ turn-by-turn (thật ra dùng icon của Google Maps).
    private data class DemoStep(val arrowRes: Int, val road: String, val start: Int)
    private val demoSteps = listOf(
        DemoStep(R.drawable.ic_turn_straight, "Đi thẳng · Lê Lợi", 800),
        DemoStep(R.drawable.ic_turn_right, "Rẽ phải vào Nguyễn Huệ", 350),
        DemoStep(R.drawable.ic_turn_left, "Rẽ trái vào Đồng Khởi", 500)
    )
    private var demoStep = 0

    private fun startDemo() {
        demoHandler.removeCallbacksAndMessages(null)
        demoStep = 0
        demoDist = demoSteps[0].start
        val tick = object : Runnable {
            override fun run() {
                val step = demoSteps[demoStep]
                val d = demoDist
                NavRepository.update(
                    NavState(
                        active = true,
                        arrowRes = step.arrowRes,
                        distance = if (d >= 1000) String.format("%.1f km", d / 1000.0) else "$d m",
                        road = step.road,
                        eta = "10:32 · 5.2 km · 8 phút",
                        updatedAt = System.currentTimeMillis()
                    )
                )
                demoDist -= 50
                if (demoDist < 30) {
                    demoStep = (demoStep + 1) % demoSteps.size
                    demoDist = demoSteps[demoStep].start
                }
                demoHandler.postDelayed(this, 1000)
            }
        }
        demoHandler.post(tick)
    }
}
