package com.byd.clusternav.modules.clustercast

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.RectF
import android.graphics.Typeface
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import com.byd.clusternav.SpeedProvider
import com.byd.clusternav.ThemeMode
import com.byd.clusternav.modules.clustercast.simplified.ClusterRectLayout
import com.byd.clusternav.modules.clustercast.simplified.SimpleCastCoordinator
import com.byd.clusternav.modules.clustercast.simplified.SimpleCastState
import com.byd.clusternav.modules.clustercast.simplified.SpeedReadoutPolicy
import com.byd.clusternav.modules.clustercast.simplified.speedReadoutInputs

/**
 * ═══ B1b · CLUSTER-RECT-OPTION (D1) — SỐ KM/H DO KACHI VẼ trên cụm Chữ nhật (phần Android) ═════════════════════════════════
 *
 * Ở theme2 FULL (Chữ nhật) cụm Seal mất số km/h gốc [ĐO xe 05/10 §4]. Lớp này vẽ một viên số 240×120 px ở
 * [ClusterRectLayout.SPEED_BOX] trên ĐÚNG màn ảo cụm của phiên. Mọi quyết định (hiện/gỡ · chữ · màu) ở [SpeedReadoutPolicy]
 * (`:core`, có test); ở đây chỉ còn cửa sổ, luồng đọc và vẽ. Chỉ tái dùng KHUÔN vòng đời của `SpeedBadgeOverlay`
 * (DisplayListener gỡ khi display mất) — KHÔNG tái dùng cách chọn display của nó (display 1 gõ cứng — backlog
 * SPEEDBADGE-DISPLAY1, P2).
 *
 * ## Bốn ràng buộc (CLAUDE.md §4 — display nào · app nào · loại nào · hoàn tác)
 *  1. **Display**: chỉ `coordinator.liveDisplayId` (qua [speedReadoutInputs]) — id cụm ĐÃ XÁC MINH bởi `ClusterDisplayResolver`
 *     (loại display 0 và màn ảo của ô Kachi; `-1` ⇒ không gắn); lượt dò gần nhất hụt ⇒ id đã xác minh của PHIÊN (review 2.89
 *     Pass 2 · cluster-r1-4, xoá khi tắt chiếu). Mở bằng `createDisplayContext`. Không bao giờ display 1 gõ cứng, display 0,
 *     hay màn ảo của ô.
 *  2. **Cửa sổ**: `TYPE_APPLICATION_OVERLAY` + `FLAG_NOT_FOCUSABLE` + `FLAG_NOT_TOUCHABLE` — không nhận chạm, không giành
 *     tiêu điểm, không là task (không lọt vào `am stack list`, không ảnh hưởng lượt dọn VD).
 *  3. **Khi nào**: [SpeedReadoutPolicy.visible] mỗi nhịp + ngay khi trạng thái chiếu đổi (bộ nghe của coordinator) ⇒ gỡ ở
 *     Opening/Closing/Off/Error, khi latch theme bật, khi kiểu phiên không phải Chữ nhật, khi CarPlay/Android Auto (D5); gỡ
 *     ngay khi display bị gỡ/đổi (DisplayListener).
 *  4. **Hoàn tác**: cửa sổ overlay chết theo tiến trình (không state ngoài hệ nào). Kachi chết ⇒ cụm mất km/h tới khi tắt
 *     chiếu (D1, owner chấp nhận).
 *
 * ## Luồng
 * Đọc `SpeedProvider.mpsOrNull()` (HAL qua reflection — có thể chậm) trên [HandlerThread] RIÊNG, 4 Hz khi đang hiện, 1 Hz khi
 * ẩn (chỉ đọc trạng thái coordinator, không chạm HAL). Mọi thao tác `WindowManager` trên luồng chính, bọc `runCatching` — lỗi
 * cửa sổ không bao giờ ném ra dịch vụ chiếu. Review 2.89 Pass 3 · cluster-r2-4: luật "không giữ số cũ" và gỡ khi trạng thái đổi
 * KHÔNG phụ thuộc luồng HAL — canh gác luồng chính ([SpeedReadoutPolicy.watchdog]) mờ số cũ / gỡ khi luồng đọc treo, bộ nghe
 * trạng thái kiểm luật hiện ngay trên luồng chính.
 */
class ClusterSpeedReadoutOverlay(
    private val appContext: Context,
    private val coordinator: SimpleCastCoordinator,
) : AutoCloseable {

    private val main = Handler(Looper.getMainLooper())
    private val thread = HandlerThread("kachi-speed-readout").apply { start() }
    private val worker = Handler(thread.looper)
    @Volatile private var closed = false

    // ── ghi trên luồng worker; `lastGoodAtMs` còn ĐỌC ở canh gác luồng chính (Pass 3 · cluster-r2-4) ──
    private var lastGoodKmh: Int? = null
    @Volatile private var lastGoodAtMs: Long? = null

    // ── chỉ luồng chính ──
    private var wm: WindowManager? = null
    private var view: ReadoutView? = null
    private var attachedDisplayId = -1

    /** Gắn hỏng (thiếu quyền overlay, kích display sai…) ⇒ không thử lại trước mốc này — không spam log/WM ở 4 Hz. */
    private var nextAttachAtMs = 0L

    /** Lần CUỐI luồng worker đẩy được một nhịp vẽ (luồng chính ghi khi [render] chạy) — đầu vào canh gác. */
    private var lastRenderAtMs: Long? = null
    private var lastPalette = SpeedReadoutPolicy.palette(night = false)

    /**
     * Review 2.89 Pass 3 · cluster-r2-4 — canh gác LUỒNG CHÍNH, chạy mỗi [SpeedReadoutPolicy.TICK_MS] khi cửa sổ đang gắn. Luồng worker
     * gọi HAL (`getCurrentSpeed`, binder phản chiếu) — treo là nó không đẩy nhịp vẽ nào nữa và mọi kiểm tra cũ (số cũ quá 1 s, gỡ khi
     * trạng thái đổi) cùng đứng theo [SUY]. Ở đây không gọi HAL: mờ số cũ / gỡ cửa sổ theo [SpeedReadoutPolicy.watchdog] + kiểm luật
     * hiện bằng trường sống của coordinator.
     */
    private val watchdog = object : Runnable {
        override fun run() {
            if (closed || view == null) return
            when (SpeedReadoutPolicy.watchdog(lastGoodAtMs, lastRenderAtMs, SystemClock.elapsedRealtime())) {
                SpeedReadoutPolicy.Watch.KEEP -> Unit
                SpeedReadoutPolicy.Watch.DIM -> view?.update(SpeedReadoutPolicy.Face(SpeedReadoutPolicy.NO_VALUE, dim = true), lastPalette)
                SpeedReadoutPolicy.Watch.DETACH -> {
                    detach("luồng đọc HAL im quá ${SpeedReadoutPolicy.WORKER_STALL_MS} ms — không giữ số cũ")
                    return
                }
            }
            checkVisible()
            if (view != null) main.postDelayed(this, SpeedReadoutPolicy.TICK_MS)
        }
    }

    private val tick = object : Runnable {
        override fun run() {
            if (closed) return
            val shown = step()
            worker.postDelayed(this, if (shown) SpeedReadoutPolicy.TICK_MS else HIDDEN_TICK_MS)
        }
    }

    // Pass 3 · cluster-r2-4: + kiểm luật hiện trên LUỒNG CHÍNH (không HAL) ⇒ Closing/Off/Error gỡ NGAY dù worker đang kẹt trong HAL.
    private val stateListener: (SimpleCastState) -> Unit = { _ ->
        if (!closed) {
            main.post { checkVisible() }
            worker.post { step() }
        }
    }

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) { if (displayId == attachedDisplayId) detach("display $displayId bị gỡ") }
        override fun onDisplayChanged(displayId: Int) {
            // Kích/overscan đổi ⇒ toạ độ 1:1 có thể không còn: gỡ, nhịp sau đo lại rồi mới gắn.
            if (displayId == attachedDisplayId) detach("display $displayId đổi cấu hình")
        }
    }

    /** Bắt đầu: bộ nghe trạng thái + bộ nghe display + nhịp đọc. Gọi MỘT lần (dịch vụ chiếu `onCreate`). */
    fun start() {
        coordinator.addStateListener(stateListener)
        runCatching {
            (appContext.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager)?.registerDisplayListener(displayListener, main)
        }.onFailure { Log.w(TAG, "registerDisplayListener: ${it.message}") }
        worker.post(tick)
    }

    override fun close() {
        closed = true
        runCatching { coordinator.removeStateListener(stateListener) }
        runCatching { (appContext.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager)?.unregisterDisplayListener(displayListener) }
        worker.removeCallbacksAndMessages(null)
        thread.quitSafely()
        main.post { detach("đóng") }
    }

    /** Một nhịp trên worker: quyết định thuần → đọc HAL chỉ khi hiện → đẩy sang luồng chính. Trả `true` nếu đang hiện. */
    private fun step(): Boolean {
        val inp = runCatching { coordinator.speedReadoutInputs() }.getOrNull()
        if (inp == null || !SpeedReadoutPolicy.visible(inp)) {
            main.post { detach(null) }
            return false
        }
        val now = SystemClock.elapsedRealtime()
        SpeedReadoutPolicy.kmh(runCatching { SpeedProvider.mpsOrNull() }.getOrNull())?.let { lastGoodKmh = it; lastGoodAtMs = now }
        val face = SpeedReadoutPolicy.face(lastGoodKmh, lastGoodAtMs, now)
        val palette = SpeedReadoutPolicy.palette(night())
        main.post { render(inp, face, palette) }
        return true
    }

    /** Ngày/đêm theo nguồn Kachi đang dùng (`ThemeMode`): chọn tay thắng; "Theo xe" ⇒ bit đêm của cấu hình hệ thống. */
    private fun night(): Boolean = when (runCatching { ThemeMode.choice(appContext) }.getOrDefault(ThemeMode.Choice.SYSTEM)) {
        ThemeMode.Choice.DARK -> true
        ThemeMode.Choice.LIGHT -> false
        ThemeMode.Choice.SYSTEM ->
            (appContext.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }

    /** Luồng chính, KHÔNG gọi HAL: cửa sổ đang gắn mà luật hiện không còn (hoặc id cụm đổi) ⇒ gỡ ngay. */
    private fun checkVisible() {
        if (closed || view == null) return
        val inp = runCatching { coordinator.speedReadoutInputs() }.getOrNull()
        if (inp == null || !SpeedReadoutPolicy.visible(inp) || inp.liveDisplayId != attachedDisplayId) detach("trạng thái chiếu đổi")
    }

    private fun render(inp: SpeedReadoutPolicy.Inputs, face: SpeedReadoutPolicy.Face, palette: SpeedReadoutPolicy.Palette) {
        if (closed) return
        lastRenderAtMs = SystemClock.elapsedRealtime()
        lastPalette = palette
        // Đọc lại luật trên luồng chính ngay trước khi chạm cửa sổ (trạng thái có thể đã đổi giữa hai luồng).
        val fresh = runCatching { coordinator.speedReadoutInputs() }.getOrNull()
        if (fresh == null || !SpeedReadoutPolicy.visible(fresh) || fresh.liveDisplayId != inp.liveDisplayId) {
            detach(null)
            return
        }
        val vd = inp.liveDisplayId
        if (attachedDisplayId != vd) detach(if (attachedDisplayId >= 1) "cụm đổi id $attachedDisplayId → $vd" else null)
        if (view == null) {
            val now = SystemClock.elapsedRealtime()
            if (now < nextAttachAtMs) return
            if (!attach(inp, vd)) {
                nextAttachAtMs = now + ATTACH_RETRY_MS
                return
            }
        }
        view?.update(face, palette)
    }

    private fun attach(inp: SpeedReadoutPolicy.Inputs, vd: Int): Boolean {
        return runCatching { attachOrThrow(inp, vd) }.getOrElse {
            Log.w(TAG, "addView lên cụm $vd hỏng (nhịp sau thử lại): ${it.message}")
            false
        }
    }

    private fun attachOrThrow(inp: SpeedReadoutPolicy.Inputs, vd: Int): Boolean {
        val dm = appContext.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager ?: return false
        val display = dm.getDisplay(vd) ?: return false
        val size = Point()
        @Suppress("DEPRECATION") display.getRealSize(size)
        // Sự thật đo được (CLAUDE.md §5): kích display ≠ 1920×720 ⇒ hộp số sẽ lệch chỗ ⇒ không gắn.
        if (!SpeedReadoutPolicy.visible(inp, size.x to size.y)) {
            Log.i(TAG, "không gắn: display $vd kích ${size.x}x${size.y} (cần ${ClusterRectLayout.WIDTH}x${ClusterRectLayout.HEIGHT})")
            return false
        }
        val ctx = appContext.createDisplayContext(display)
        val w = ctx.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return false
        val v = ReadoutView(ctx)
        w.addView(v, layoutParams())
        wm = w
        view = v
        attachedDisplayId = vd
        main.removeCallbacks(watchdog)
        main.postDelayed(watchdog, SpeedReadoutPolicy.TICK_MS)
        Log.i(TAG, "gắn số km/h lên cụm $vd tại ${ClusterRectLayout.SPEED_BOX}")
        return true
    }

    private fun detach(why: String?) {
        main.removeCallbacks(watchdog)
        val v = view ?: return
        runCatching { wm?.removeView(v) }.onFailure { Log.w(TAG, "removeView: ${it.message}") }
        if (why != null) Log.i(TAG, "gỡ số km/h khỏi cụm $attachedDisplayId — $why")
        view = null
        wm = null
        attachedDisplayId = -1
        nextAttachAtMs = 0L
    }

    private fun layoutParams(): WindowManager.LayoutParams {
        val box = ClusterRectLayout.SPEED_BOX
        return WindowManager.LayoutParams(
            box.width, box.height,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            x = box.left
            y = box.top
            title = "KachiClusterSpeed"
        }
    }

    /**
     * Viên số: nền bo [SpeedReadoutPolicy.CORNER_PX], số [SpeedReadoutPolicy.DIGITS_PX] đậm chữ số đều độ rộng (`tnum`), nhãn
     * [SpeedReadoutPolicy.UNIT_PX]. Mọi kích thước là px CỤM (không dp) — toạ độ cụm 1:1 ở cấu hình NORMAL_DEFAULT, DPI của màn
     * ảo (240/320) không được đổi cỡ viên số. Bố cục dọc là [ĐOÁN thẩm mỹ], chỉnh trên xe.
     */
    private class ReadoutView(ctx: Context) : View(ctx) {
        private var face = SpeedReadoutPolicy.Face(SpeedReadoutPolicy.NO_VALUE, dim = true)
        private var palette = SpeedReadoutPolicy.palette(night = false)
        private val rect = RectF()
        private val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private val digits = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = SpeedReadoutPolicy.DIGITS_PX.toFloat()
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            fontFeatureSettings = "tnum"
            textAlign = Paint.Align.CENTER
        }
        private val unit = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = SpeedReadoutPolicy.UNIT_PX.toFloat()
            textAlign = Paint.Align.CENTER
        }

        fun update(f: SpeedReadoutPolicy.Face, p: SpeedReadoutPolicy.Palette) {
            if (f == face && p == palette) return
            face = f
            palette = p
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            val r = SpeedReadoutPolicy.CORNER_PX.toFloat()
            rect.set(0f, 0f, width.toFloat(), height.toFloat())
            bg.color = palette.background
            canvas.drawRoundRect(rect, r, r, bg)
            digits.color = if (face.dim) SpeedReadoutPolicy.dimmed(palette.digits) else palette.digits
            unit.color = palette.unit
            val cx = width / 2f
            canvas.drawText(face.text, cx, height * DIGITS_BASELINE, digits)
            canvas.drawText(SpeedReadoutPolicy.UNIT, cx, height * UNIT_BASELINE, unit)
        }
    }

    private companion object {
        const val TAG = "ClusterSpeedReadout"

        /** Nhịp khi đang ẩn: chỉ đọc trạng thái coordinator (không chạm HAL) — bộ nghe trạng thái lo phản ứng tức thì. */
        const val HIDDEN_TICK_MS = 1_000L

        /** Gắn cửa sổ hỏng ⇒ chờ ngần này mới thử lại. */
        const val ATTACH_RETRY_MS = 5_000L

        /** Đường chân chữ số / nhãn theo tỉ lệ chiều cao viên (120 px ⇒ 80 / 108) — [ĐOÁN thẩm mỹ]. */
        const val DIGITS_BASELINE = 0.67f
        const val UNIT_BASELINE = 0.90f
    }
}
