package com.byd.clusternav.launcher.escape

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowManager
import com.byd.clusternav.launcher.WallView
import com.byd.clusternav.modules.navaccess.A11yOverlayPort

/**
 * ═══ 2.98 · R7 — LỚP CHE: GƯƠNG của chính màn nhà Kachi đặt lên trên thanh hệ thống + thanh tiêu đề freeform ═══════════
 *
 * Vì sao phải che: có stack freeform hiện trên display 0 thì AOSP ép hiện thanh trạng thái + thanh điều hướng và xoá cờ ẩn của
 * mọi cửa sổ — không cờ nào gỡ được ([ĐO nguồn fw 2606], evidence §4). Thanh tiêu đề freeform (□ ✕) của app thường cũng không
 * tắt được. Cửa sổ duy nhất nằm TRÊN cả ba mà app thường thêm được là `TYPE_ACCESSIBILITY_OVERLAY` của dịch vụ trợ năng
 * Kachi đã bật sẵn ([A11yOverlayPort]).
 *
 * ## Chọn GƯƠNG (vẽ lại đúng phần màn nhà Kachi ở chỗ đó) thay vì dải màu đục — vì sao
 * Dưới thanh trạng thái/điều hướng chính là thanh trên + thanh nút của Kachi ([ĐO máy ảo 09/10]: cửa sổ KachiHome
 * `LAYOUT_IN_SCREEN FULLSCREEN`, không đổi bố cục khi thanh bị ép hiện — thanh trên vẫn ở `y=14..76`, thanh trạng thái đè lên
 * `0..36`); dưới thanh tiêu đề là đầu khung ô (thẻ app + nút ⇄). Vẽ lại đúng vùng đó của cây view Kachi thì:
 *  - nhìn y như màn nhà khi không có freeform (đồng hồ, chip, góc bo của khung ô, nền tường…) — không phải mô phỏng màu;
 *  - chạm được: cú chạm trên lớp che được dời toạ độ rồi giao thẳng cho cây view Kachi (`dispatchTouchEvent`) ⇒ nút thanh trên,
 *    thanh nút, ⇄ của ô hoạt động như thường;
 *  - không phải bản sao thứ hai của thanh trên/thanh nút (không đồng bộ state hai nơi).
 * Cách vẽ: `decor.draw()` vào một `Bitmap` phần mềm cỡ đúng dải (như `View.drawToBitmap`), làm mới khi chính cửa sổ Kachi vẽ
 * lại (`OnDrawListener`), dồn nhịp tối đa [REFRESH_MIN_MS]. KHÔNG dùng chung RenderNode giữa hai cửa sổ (không có hợp đồng công
 * khai). SurfaceView (ô app khác) vẽ ra trong suốt trong bản phần mềm — ở các dải này không có ô nào đang chiếu.
 *
 * Thêm dải [EscapeCoverPlan.Kind.EDGE] quanh cửa sổ app: [ĐO máy ảo 09/10] vùng chạm của cửa sổ freeform rộng hơn khung 30 dp
 * (tay nắm đổi cỡ) ⇒ không che thì nút thanh trên/thanh nút sát ô bị app nuốt. Dải gương giao chạm về Kachi.
 *
 * Owner 09/10 (#2 "thanh xám", #3 "bo 2 góc dưới hỏng") — hai loại vùng KHÔNG gương cả cây view:
 *  - [EscapeCoverPlan.Kind.GAP] (phần thanh tiêu đề lọt vào ô): vẽ [WallView] (nền sau ô) + nút ⇄ của ô, bỏ khay ô ⇒ ô trông bắt đầu
 *    đúng đỉnh nội dung app. Chạm vẫn giao cho cây Kachi (⇄ bấm được).
 *  - [EscapeCoverPlan.Kind.CORNER] (dải góc trên/dưới): vẽ [WallView] rồi XOÁ phần trong khung bo ⇒ chỉ còn 4 mảnh ngoài cung, đúng
 *    như ô thường (`SlotFrameClip`: ngoài cung là nền sau khung). `FLAG_NOT_TOUCHABLE` ⇒ chạm rơi xuống app.
 * Phần mềm vẽ `decor.draw` không áp `clipToOutline` (chỉ có ở RenderNode) — vì vậy góc phải tự vẽ, không gương được.
 *
 * Không state bền: cửa sổ chết theo tiến trình / theo token dịch vụ. Luồng chính.
 */
internal object EscapeCoverOverlay {

    private const val TAG = "KachiEscape"

    /** `LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS` — hằng public từ API 30, cùng giá trị ở A10 (ẩn). */
    private const val CUTOUT_ALWAYS = 3

    /** Nhịp làm mới gương tối thiểu — chỉ khi Kachi tự vẽ lại; Kachi đứng yên ⇒ 0 lần vẽ. */
    const val REFRESH_MIN_MS = 500L

    private val shown = ArrayList<MirrorView>()
    private var wm: WindowManager? = null
    private var host: Activity? = null
    private var drawListener: ViewTreeObserver.OnDrawListener? = null
    private val ui = Handler(Looper.getMainLooper())

    init { A11yOverlayPort.onDetach { ui.post { drop("a11y-detach") } } }

    val isShown: Boolean get() = shown.isNotEmpty()

    /**
     * Hiện lớp che [covers] gương của [activity]. Dịch vụ trợ năng chưa nối ⇒ `false` (không che, thanh hệ thống hiện — vẫn
     * dùng được). Gọi lại với cùng vùng ⇒ không dựng lại.
     */
    fun show(activity: Activity, covers: List<EscapeCoverPlan.Cover>, heads: (Int) -> List<View> = { emptyList() }): Boolean {
        val svc = A11yOverlayPort.service ?: run { hide("no-a11y"); Log.i(TAG, "lớp che: dịch vụ trợ năng chưa nối ⇒ không che"); return false }
        if (host === activity && shown.map { it.cover } == covers) return true
        hide("replace")
        val w = svc.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return false
        val decor = activity.window?.decorView ?: return false
        val wall = findWall(decor)
        for (c in covers) {
            val v = MirrorView(svc, decor, c, wall, if (c.kind == EscapeCoverPlan.Kind.GAP) heads(c.slot) else emptyList())
            val touch = if (c.kind == EscapeCoverPlan.Kind.CORNER) WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE else 0
            val lp = WindowManager.LayoutParams(
                c.rect.width, c.rect.height, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or touch,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.START; x = c.rect.left; y = c.rect.top; title = "KachiEscape-${c.kind}"
                // [ĐO xe 09/10] display 0 có DisplayCutout trên 80 px (785..1135 × 0..80) và `StatusBar` khai cutoutMode=always. Mặc định
                // (DEFAULT) thì cửa sổ không toàn màn bị đẩy khỏi vùng cutout ⇒ dải che thanh trạng thái sẽ tụt xuống y=80, hở thanh.
                // ALWAYS (= 3; public từ API 30, có sẵn dạng ẩn ở A10 — cùng giá trị StatusBar đang dùng) giữ đúng y=0. 🚗 xác nhận.
                layoutInDisplayCutoutMode = CUTOUT_ALWAYS
            }
            val ok = runCatching { w.addView(v, lp) }.onFailure { Log.w(TAG, "lớp che ${c.kind}: addView hỏng ${it.javaClass.simpleName}") }.isSuccess
            if (ok) { shown += v; v.refresh(); v.post { v.logPlaced() } }
        }
        wm = w; host = activity
        val l = ViewTreeObserver.OnDrawListener { scheduleRefresh() }
        runCatching { decor.viewTreeObserver.addOnDrawListener(l) }.onSuccess { drawListener = l }
        Log.i(TAG, "lớp che: ${shown.joinToString { "${it.cover.kind}${it.cover.rect}" }}")
        return shown.isNotEmpty()
    }

    /** Gỡ mọi lớp che (thôi quản / app không còn ở đỉnh / màn nhà khuất / bảng Kachi mở). Idempotent. */
    fun hide(why: String) {
        if (shown.isEmpty() && host == null) return
        val decor = host?.window?.decorView
        drawListener?.let { l -> decor?.viewTreeObserver?.let { if (it.isAlive) runCatching { it.removeOnDrawListener(l) } } }
        drawListener = null
        ui.removeCallbacks(refreshRun)
        shown.forEach { v -> runCatching { wm?.removeViewImmediate(v) }; v.recycle() }
        if (shown.isNotEmpty()) Log.i(TAG, "lớp che gỡ ($why)")
        shown.clear(); wm = null; host = null
    }

    /** Gỡ lớp che chỉ khi đang gương [owner] (màn nhà khác đang che thì giữ — hai màn nhà cùng sống, R3). */
    fun hideIf(owner: Activity, why: String) { if (host === owner) hide(why) }

    /** Dịch vụ rời: cửa sổ đã chết theo token — chỉ bỏ tham chiếu. */
    private fun drop(why: String) {
        shown.forEach { it.recycle() }
        shown.clear(); wm = null
        host?.window?.decorView?.viewTreeObserver?.let { o -> drawListener?.let { if (o.isAlive) runCatching { o.removeOnDrawListener(it) } } }
        drawListener = null; host = null
        Log.i(TAG, "lớp che bỏ ($why)")
    }

    private var lastRefresh = 0L
    private val refreshRun = Runnable { lastRefresh = SystemClock.uptimeMillis(); shown.forEach { it.refresh() } }

    private fun scheduleRefresh() {
        if (shown.isEmpty()) return
        ui.removeCallbacks(refreshRun)
        val wait = (lastRefresh + REFRESH_MIN_MS - SystemClock.uptimeMillis()).coerceAtLeast(0L)
        ui.postDelayed(refreshRun, wait)
    }

    /** [WallView] (nền sau mọi khung ô) trong cây của màn nhà; không thấy ⇒ `null` (GAP/CORNER lùi về gương cả cây). */
    private fun findWall(v: View): View? {
        if (v is WallView) return v
        val g = v as? ViewGroup ?: return null
        for (i in 0 until g.childCount) findWall(g.getChildAt(i))?.let { return it }
        return null
    }

    /** Vẽ [v] (không phải cả cây) vào [c] đúng chỗ của nó trên màn, gốc toạ độ của [c] = ([ox],[oy]) trên màn. */
    private fun drawAt(c: Canvas, v: View, ox: Int, oy: Int) {
        if (!v.isAttachedToWindow) return
        val loc = IntArray(2); v.getLocationOnScreen(loc)
        val save = if (v.alpha < 1f) c.saveLayerAlpha(null, (v.alpha * 255).toInt()) else c.save()
        c.translate((loc[0] - ox).toFloat(), (loc[1] - oy).toFloat())
        v.draw(c)
        c.restoreToCount(save)
    }

    private val clearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) }

    /**
     * Một dải che: vẽ vùng [cover] — gương [decor] (thanh · tiêu đề trên ô · viền), hoặc [wall] + [heads] (GAP), hoặc [wall] trừ
     * khung bo (CORNER); chạm (trừ CORNER — cửa sổ không nhận chạm) ⇒ giao cho [decor] đúng toạ độ màn.
     */
    @SuppressLint("ViewConstructor")
    private class MirrorView(
        ctx: Context,
        private val decor: View,
        val cover: EscapeCoverPlan.Cover,
        private val wall: View?,
        private val heads: List<View>,
    ) : View(ctx) {
        private var bmp: Bitmap? = null
        private val inner = RectF()

        fun refresh() {
            val r = cover.rect
            if (r.isEmpty || !decor.isAttachedToWindow) return
            val b = bmp ?: runCatching { Bitmap.createBitmap(r.width, r.height, Bitmap.Config.ARGB_8888) }.getOrNull()?.also { bmp = it } ?: return
            val loc = IntArray(2); decor.getLocationOnScreen(loc)
            b.eraseColor(0)
            val c = Canvas(b)
            val behind = wall?.takeIf { cover.kind == EscapeCoverPlan.Kind.GAP || cover.kind == EscapeCoverPlan.Kind.CORNER }
            runCatching {
                if (behind == null) {
                    c.translate((loc[0] - r.left).toFloat(), (loc[1] - r.top).toFloat())
                    decor.draw(c)
                } else {
                    drawAt(c, behind, r.left, r.top)
                    heads.forEach { drawAt(c, it, r.left, r.top) }
                    val f = cover.frame
                    if (cover.kind == EscapeCoverPlan.Kind.CORNER && f != null) {
                        inner.set((f.left - r.left).toFloat(), (f.top - r.top).toFloat(), (f.right - r.left).toFloat(), (f.bottom - r.top).toFloat())
                        c.drawRoundRect(inner, cover.radiusPx.toFloat(), cover.radiusPx.toFloat(), clearPaint)
                    }
                }
            }.onFailure { Log.w(TAG, "gương ${cover.kind}: vẽ hỏng ${it.javaClass.simpleName}") }
            invalidate()
        }

        fun recycle() { bmp?.recycle(); bmp = null }

        /** Một dòng nhật ký: cửa sổ che nằm ĐÚNG khung mong muốn không (cutout / ROM lạ dời nó đi ⇒ thấy ngay trên log xe). */
        fun logPlaced() {
            val loc = IntArray(2); getLocationOnScreen(loc)
            val at = PxRect(loc[0], loc[1], loc[0] + width, loc[1] + height)
            if (at != cover.rect) Log.w(TAG, "lớp che ${cover.kind}: hệ đặt ở $at ≠ ${cover.rect}")
        }

        override fun onDraw(canvas: Canvas) { bmp?.takeUnless { it.isRecycled }?.let { canvas.drawBitmap(it, 0f, 0f, null) } }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(e: MotionEvent): Boolean {
            if (!decor.isAttachedToWindow) return true
            val loc = IntArray(2); decor.getLocationOnScreen(loc)
            val ev = MotionEvent.obtain(e)
            ev.offsetLocation((cover.rect.left - loc[0]).toFloat(), (cover.rect.top - loc[1]).toFloat())
            runCatching { decor.dispatchTouchEvent(ev) }
            ev.recycle()
            return true   // nuốt: không lọt xuống thanh hệ thống / nút □ ✕ của thanh tiêu đề
        }
    }
}
