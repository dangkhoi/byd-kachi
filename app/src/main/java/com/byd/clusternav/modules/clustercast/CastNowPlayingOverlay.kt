package com.byd.clusternav.modules.clustercast

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.TextUtils
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import com.byd.clusternav.K_CAST_NOW_PLAYING
import com.byd.clusternav.K_CAST_NOW_PLAYING_CORNER
import com.byd.clusternav.Prefs
import com.byd.clusternav.castNowPlayingCorner
import com.byd.clusternav.castNowPlayingEnabled
import com.byd.clusternav.launcher.MediaBridge
import com.byd.clusternav.modules.clustercast.nowplaying.CastNowPlayingContent
import com.byd.clusternav.modules.clustercast.nowplaying.CastNowPlayingPlace
import com.byd.clusternav.modules.clustercast.nowplaying.CastNowPlayingPlan
import com.byd.clusternav.modules.clustercast.nowplaying.CastNowPlayingStyle
import com.byd.clusternav.modules.clustercast.nowplaying.CastNowPlayingText
import com.byd.clusternav.modules.clustercast.simplified.SimpleCastCoordinator
import com.byd.clusternav.modules.clustercast.simplified.SimpleCastRuntime
import com.byd.clusternav.modules.clustercast.simplified.SimpleCastState

/**
 * ═══ 3.1.12 · CAST-NOW-PLAYING — dải "♪ tên bài — nghệ sĩ" trên MẶT CHIẾU cụm (phần chạm Android) ═══════════════════════════
 *
 * Luật thuần ở `:core` (`CastNowPlayingStrip.kt`). Đường vẽ = đường CHUẨN của lớp phủ cụm, cùng badge tốc độ
 * (`SpeedBadgeOverlay`): display từ [ClusterOverlayDisplays.resolve] (id cụm sống do coordinator công bố), `TYPE_APPLICATION_OVERLAY`
 * qua `createDisplayContext`, không chạm, không tiêu điểm; [ClusterOverlayDisplays.paused] (cổng theme đang "dọn cụm") ⇒ gỡ.
 *
 * Chi phí:
 *  • KHÔNG chiếu / công tắc TẮT ⇒ 0 việc: chỉ hai bộ nghe đã đăng ký ([ClusterOverlayDisplays] + prefs), không nhịp, không cửa sổ.
 *  • Đang chiếu + BẬT ⇒ MỘT bộ nghe phiên nhạc ([MediaBridge.watchAll], callback khi đổi bài/trạng thái) + bộ nghe trạng thái
 *    chiếu (đặt lại góc khi khung đổi). Sự kiện trùng ⇒ plan trả `changed=false` ⇒ 0 thao tác cửa sổ. Dừng nhạc ⇒ MỘT lượt hẹn.
 *  • Cửa sổ chỉ gắn khi có bài để hiện; ẩn = gỡ (không để lớp trong suốt nằm lại trên cụm).
 *
 * CLAUDE.md §4: display = cụm sống (không hằng 1, không VD riêng tư); không app, không stack, không lệnh `am`/`wm`, không opcode;
 * hoàn tác = `removeView` của chính tiến trình (cửa sổ chết theo tiến trình — §5 không có gì sống dai hơn tiến trình).
 * Mọi thao tác trên luồng chính. Không log tên bài (chỉ độ dài).
 */
object CastNowPlayingOverlay {
    private const val TAG = "KachiNpStrip"

    /** Lượt kiểm sau tạm dừng đến muộn hơn hạn một chút (tránh trượt mép `>=`). */
    private const val PAUSE_SLACK_MS = 200L

    private val main = Handler(Looper.getMainLooper())
    private val plan = CastNowPlayingPlan()

    @Volatile private var app: Context? = null
    private var unwatch: (() -> Unit)? = null
    private var coordinator: SimpleCastCoordinator? = null
    private var wm: WindowManager? = null
    private var view: StripView? = null
    private var attachedDisplayId = -1
    private var loggedNoAccess = false

    private val pauseCheck = Runnable { apply(plan.onPauseCheck(SystemClock.elapsedRealtime())) }
    private val clusterListener: () -> Unit = { main.post { reconcile() } }
    private val stateListener: (SimpleCastState) -> Unit = { main.post { if (view != null) place() } }
    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || key == K_CAST_NOW_PLAYING || key == K_CAST_NOW_PLAYING_CORNER) main.post { reconcile() }
    }

    /** `KachiApplication.onCreate` (tiến trình chính) — chỉ đăng ký bộ nghe. */
    fun install(ctx: Context) {
        val a = ctx.applicationContext
        if (app != null) return
        app = a
        ClusterOverlayDisplays.addListener(clusterListener)
        Prefs.sp(a).registerOnSharedPreferenceChangeListener(prefsListener)   // bộ giữ tham chiếu mạnh = object này
        main.post { reconcile() }
    }

    /** Công tắc · đang chiếu · cụm không bị dọn ⇒ bám phiên nhạc; ngược lại gỡ hết. Rẻ, gọi lại bao nhiêu lần cũng được. */
    private fun reconcile() {
        val a = app ?: return
        val want = Prefs.castNowPlayingEnabled(a) && ClusterOverlayDisplays.castLiveId != null && !ClusterOverlayDisplays.paused
        if (want) watch(a) else { unwatchAll(); apply(plan.onMedia(null, SystemClock.elapsedRealtime())) }   // không mang phiên cũ sang lượt chiếu sau
        apply(plan.onActive(want, SystemClock.elapsedRealtime()))
        if (want) {
            evaluate(a)
            if (view != null) place()      // id cụm / góc có thể vừa đổi
        }
    }

    private fun watch(a: Context) {
        if (unwatch == null) {
            unwatch = MediaBridge(a).watchAll(main) { evaluate(a) }
            if (unwatch == null && !loggedNoAccess) { loggedNoAccess = true; Log.w(TAG, "np strip: chua co quyen doc thong bao — khong doc duoc phien") }
            if (unwatch != null) Log.i(TAG, "np strip: bam phien nhac (dang chieu)")
        }
        val c = SimpleCastRuntime.existing()
        if (c !== coordinator) {
            coordinator?.removeStateListener(stateListener)
            coordinator = c
            c?.addStateListener(stateListener)
        }
    }

    private fun unwatchAll() {
        main.removeCallbacks(pauseCheck)
        unwatch?.let { it(); Log.i(TAG, "np strip: go bo nghe phien") }
        unwatch = null
        coordinator?.removeStateListener(stateListener)
        coordinator = null
    }

    private fun evaluate(a: Context) {
        if (unwatch == null) return
        apply(plan.onMedia(MediaBridge(a).nowPlaying(), SystemClock.elapsedRealtime()))
    }

    private fun apply(step: CastNowPlayingPlan.Step) {
        step.checkInMs?.let { main.removeCallbacks(pauseCheck); main.postDelayed(pauseCheck, it + PAUSE_SLACK_MS) }
        if (!step.changed) return
        val c = step.content
        if (c == null) { main.removeCallbacks(pauseCheck); detach() } else show(c)
    }

    /** Vẽ lại thứ đang hiện ở đúng chỗ (khung chiếu / góc / display / sáng-tối có thể đã đổi). */
    private fun place() { plan.shown?.let { show(it) } }

    private fun show(c: CastNowPlayingContent) {
        val a = app ?: return
        runCatching {
            val dm = a.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager ?: return
            val display = ClusterOverlayDisplays.resolve(dm) ?: run { detach(); return }
            if (view != null && attachedDisplayId != display.displayId) detach()
            val size = Point().also { @Suppress("DEPRECATION") display.getRealSize(it) }
            if (size.x <= 0 || size.y <= 0) return
            val frame = CastNowPlayingPlace.frame(
                size.x, size.y, CastNowPlayingPlace.castFrame(SimpleCastRuntime.existing()?.state), ClusterProfile.resolveCached(a).band,
            )
            val anchor = CastNowPlayingPlace.anchor(Prefs.castNowPlayingCorner(a), frame, size.x, size.y)
            val v = view ?: run {
                val dctx = a.createDisplayContext(display)
                val w = dctx.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
                StripView(dctx).also { wm = w; view = it }
            }
            v.text = CastNowPlayingText.display(c)        // chữ ĐẦY ĐỦ — tràn khung thì marquee chạy (owner 17/10: không cắt)
            v.setTextSize(TypedValue.COMPLEX_UNIT_PX, anchor.textPx.toFloat())
            v.maxWidth = anchor.maxWidth
            val padH = anchor.textPx * 6 / 10
            val padV = anchor.textPx * 3 / 10
            v.setPadding(padH, padV, padH, padV)
            paint(v, anchor.textPx)
            val lp = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = (if (anchor.right) Gravity.RIGHT else Gravity.LEFT) or (if (anchor.bottom) Gravity.BOTTOM else Gravity.TOP)
                x = anchor.x
                y = anchor.y
            }
            v.marqueeWanted = c.playing                    // chữ chỉ chạy khi đang PHÁT và đang hiện; dừng ⇒ đứng yên (đỡ GPU cụm)
            if (attachedDisplayId < 0) {
                wm?.addView(v, lp)
                attachedDisplayId = display.displayId
                Log.i(TAG, "np strip: gan len display ${display.displayId} (${size.x}x${size.y}) len=${c.text.length} playing=${c.playing}")
            } else {
                wm?.updateViewLayout(v, lp)
            }
        }.onFailure { Log.w(TAG, "np strip: ve loi (${it.javaClass.simpleName}: ${it.message}) — thu lai o su kien sau"); detach() }
    }

    /** Sáng/tối theo HỆ THỐNG xe (cụm theo `uiMode` hệ thống [ĐO 07/10]), cùng nguồn `ClusterBlackActivity.paintBackdrop`. */
    private fun paint(v: TextView, textPx: Int) {
        val night = (Resources.getSystem().configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val col = CastNowPlayingStyle.colors(night)
        v.setTextColor(col.text)
        v.background = GradientDrawable().apply { cornerRadius = textPx.toFloat(); setColor(col.background) }
    }

    private fun detach() {
        val v = view
        if (v != null && attachedDisplayId >= 0) {
            runCatching { wm?.removeView(v) }.onFailure { Log.w(TAG, "np strip: removeView loi: ${it.message}") }
            Log.i(TAG, "np strip: go khoi display $attachedDisplayId")
        }
        view = null
        wm = null
        attachedDisplayId = -1
    }

    /**
     * Một dòng, chữ ĐẦY ĐỦ; tràn khung ([maxWidth]) ⇒ marquee CHUẨN của `TextView` (Choreographer, chỉ chạy khi chữ dài hơn view
     * — `TextView.canMarquee` A10 r47 :10367; vừa khung thì không khung hình nào). Cửa sổ KHÔNG nhận tiêu điểm, nên marquee bám
     * `setSelected` (`startMarquee` :10383 đòi `isFocused() || isSelected()`); `onWindowFocusChanged(false)` gọi `stopMarquee`
     * (:10793-10798) ⇒ chọn lại để chữ chạy tiếp. Đổi sáng/tối giữa lúc hiện ⇒ tô lại ngay (ROM BYD không dựng lại).
     */
    private class StripView(ctx: Context) : TextView(ctx) {
        init {
            setSingleLine(true)
            ellipsize = TextUtils.TruncateAt.MARQUEE
            marqueeRepeatLimit = -1
            setHorizontalFadingEdgeEnabled(true)
            typeface = Typeface.DEFAULT_BOLD
            includeFontPadding = false
        }

        /** `true` ⇒ chạy chữ (khi tràn); `false` ⇒ dừng marquee (tạm dừng nhạc). Gỡ cửa sổ ⇒ `TextView` tự dừng khi tách. */
        var marqueeWanted: Boolean = false
            set(v) { field = v; if (isSelected != v) isSelected = v }

        override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
            super.onWindowFocusChanged(hasWindowFocus)
            if (!hasWindowFocus && marqueeWanted) { isSelected = false; isSelected = true }
        }

        override fun onConfigurationChanged(newConfig: Configuration?) {
            super.onConfigurationChanged(newConfig)
            paint(this, textSize.toInt())
        }
    }
}
