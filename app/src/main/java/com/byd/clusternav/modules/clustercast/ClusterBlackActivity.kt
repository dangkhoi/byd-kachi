package com.byd.clusternav.modules.clustercast

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.Window
import android.view.WindowManager
import com.byd.clusternav.ThemeMode
import com.byd.clusternav.modules.clustercast.simplified.ClusterBackdrop
import com.byd.clusternav.modules.clustercast.simplified.SimpleCastRuntime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Black full-screen placeholder activity for the cluster display.
 *
 * Launched on display 1 immediately after projection opens (30/16/35) to ensure the OEM firmware
 * keeps projection active. Without content on display 1, the firmware auto-closes projection
 * after a short timeout — measured on vehicle 2026-08-03.
 *
 * When a real app is cast, it replaces this activity on display 1.
 * When cast stops, this activity remains to keep projection alive (cluster stays black/ready).
 *
 * Review 2.89 Pass 1 · safety-5: [finishOwn] gỡ placeholder của CHÍNH tiến trình bằng `finishAndRemoveTask` (đường không giết
 * tiến trình nào — nguồn AOSP trích ở KDoc `ClusterThemeGuard`), thay cho `am stack remove` (giết `:tts` khi Kachi là HOME).
 */
class ClusterBlackActivity : Activity() {
    /** Nền đang hiện (luồng chính) — [onNewIntent] tô lại. */
    private var backdrop: View? = null

    override fun attachBaseContext(newBase: Context) {
        // Locale của NGƯỜI DÙNG (không phải của máy) cho tài nguyên — spec kachi-i18n-zh-th-ms R9.
        super.attachBaseContext(com.byd.clusternav.launcher.LangHost.localized(ThemeMode.wrap(newBase)))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LIVE.add(this)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            WindowManager.LayoutParams.FLAG_FULLSCREEN or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
        )
        val view = View(this)
        backdrop = view
        paintBackdrop(view, "tạo")
        setContentView(view)
    }

    /**
     * Review 2.94 Pass 1 · r2-backdrop: `singleInstance` ⇒ lượt mở chiếu sau `am start` vào thực thể CÒN SỐNG (vd tiến trình bị
     * giết rồi dựng lại Activity khi phiên chưa ghim ⇒ `castFrame()` = `null` ⇒ màu Bo tròn) — tô lại theo phiên vừa ghim
     * (`ProjectionManager.open` ghim kiểu TRƯỚC `am start`). Không đổi gì khác của placeholder.
     */
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        backdrop?.let { paintBackdrop(it, "mở lại") }
    }

    /**
     * 2.94 · CLUSTER-BACKDROP-DAY: sáng/tối của HỆ THỐNG xe (không qua ThemeMode.wrap của Kachi — cụm theo xe, [ĐO 07/10]);
     * đổi chế độ ⇒ Activity dựng lại (không khai configChanges; A10 `ActivityRecord.java:3291-3334`, Activity đang STOPPED dưới
     * app chiếu dựng lại khi hiện ra — `:3202`) ⇒ màu mới. `Resources.getSystem()` nhận cấu hình toàn cục ở
     * `ResourcesManager.java:1028` (`updateSystemConfiguration`). Luật màu thuần ở :core ClusterBackdrop.
     */
    private fun paintBackdrop(view: View, why: String) {
        val night = (Resources.getSystem().configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        val color = ClusterBackdrop.color(night, SimpleCastRuntime.castFrame())
        Log.i(TAG, "nền màn chiếu ($why): hệ thống ${if (night) "tối" else "sáng"} ⇒ #%08X".format(color))
        view.setBackgroundColor(color)
    }

    override fun onDestroy() {
        LIVE.remove(this)
        super.onDestroy()
    }

    companion object {
        private const val TAG = "ClusterBlack"

        /** Chờ luồng chính nhận lượt gỡ tối đa chừng này; quá ⇒ huỷ lượt, bên gọi về đường shell (mồ côi). */
        private const val FINISH_WAIT_MS = 1_000L
        private const val PENDING = 0
        private const val RUNNING = 1
        private const val CANCELLED = 2

        /** Thực thể đang sống trong TIẾN TRÌNH này (luồng chính thêm ở `onCreate`, bớt ở `onDestroy`). */
        private val LIVE: MutableSet<ClusterBlackActivity> = ConcurrentHashMap.newKeySet()

        private val MAIN = Handler(Looper.getMainLooper())

        /**
         * Gỡ ClusterBlack của tiến trình này có `taskId` thuộc [taskIds] bằng `finishAndRemoveTask` (luồng chính). Trả các task
         * id đã gỡ (hoặc đang gỡ dở). Không thực thể nào khớp / luồng chính không nhận kịp [FINISH_WAIT_MS] / bị ngắt ⇒ tập rỗng
         * hoặc thiếu ⇒ bên gọi (`ClusterThemeGuard`) dùng `am stack remove` như trước. Gọi từ executor của coordinator.
         */
        fun finishOwn(taskIds: Set<Int>): Set<Int> {
            if (taskIds.isEmpty() || LIVE.isEmpty()) return emptySet()
            val done: MutableSet<Int> = ConcurrentHashMap.newKeySet()
            val state = AtomicInteger(PENDING)
            val latch = CountDownLatch(1)
            val work = Runnable {
                if (!state.compareAndSet(PENDING, RUNNING)) return@Runnable
                try {
                    for (a in LIVE) {
                        val id = a.taskId
                        if (id !in taskIds) continue
                        if (!a.isFinishing) a.finishAndRemoveTask()
                        done += id
                    }
                } finally {
                    latch.countDown()
                }
            }
            if (Looper.myLooper() == Looper.getMainLooper()) {
                work.run()
                return done.toSet()
            }
            if (!MAIN.post(work)) return emptySet()
            try {
                if (!latch.await(FINISH_WAIT_MS, TimeUnit.MILLISECONDS)) {
                    // Luồng chính bận: huỷ nếu CHƯA chạy (không để hai đường gỡ cùng lúc); đang chạy thì chờ nó xong.
                    if (state.compareAndSet(PENDING, CANCELLED)) {
                        Log.w(TAG, "finishOwn: main thread busy > ${FINISH_WAIT_MS}ms -> shell path")
                        return emptySet()
                    }
                    latch.await(FINISH_WAIT_MS, TimeUnit.MILLISECONDS)
                }
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                state.compareAndSet(PENDING, CANCELLED)
            }
            return done.toSet()
        }
    }
}
