package com.byd.clusternav

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View

/**
 * Trình đặt VỊ TRÍ bong bóng VietMap trên cụm bằng KÉO-THẢ — bản sinh đôi của
 * [com.byd.clusternav.modules.clustercast.BadgePlacementView] cho bong bóng nav (thay bộ nút ←→↑↓ cũ mà
 * owner chê). Vẽ một cụm-proxy HÌNH CHỮ NHẬT (tỉ lệ `clusterWidth:clusterHeight`, letterbox vào View để
 * marker giữ đúng tỉ lệ dù View méo) + một marker HÌNH CHỮ NHẬT tỉ lệ đúng bong bóng
 * (`bubbleWidth×bubbleHeight`, **KHÔNG có slider cỡ** — bong bóng VietMap cố định cỡ). Chạm/kéo dời TÂM bong
 * bóng theo ngón tay, clamp trong cụm bằng góc-trên-trái; ACTION_UP báo **toạ độ TUYỆT ĐỐI góc-trên-trái**
 * (cluster px) qua [onMoved] để phía gọi convert sang offset-từ-tâm khi lưu
 * (spec `docs/specs/vietmap-overlay-position-ui.html`).
 *
 * Degrade-safe: không vẽ trước khi View được đo; mọi giá trị đều clamp nên pref hỏng / container tí hon
 * không thể vẽ marker ra ngoài proxy. Chặn chạm khi `isEnabled == false` (Cast OFF).
 */
class VmBubblePlacementView(
    context: Context,
    private val clusterWidth: Int = 1920,
    private val clusterHeight: Int = 720,
    private val bubbleWidth: Int = 371,
    private val bubbleHeight: Int = 158,
    private val onMoved: (absLeftXcluster: Int, absTopYcluster: Int) -> Unit,
) : View(context) {

    // Góc-trên-trái bong bóng trong toạ độ CỤM (px). Mặc định = nửa phải, giữa theo chiều dọc.
    private var leftXCluster = ((clusterWidth - bubbleWidth) * 3 / 4).coerceIn(0, maxLeft())
    private var topYCluster = ((clusterHeight - bubbleHeight) / 2).coerceIn(0, maxTop())

    private var dragging = false

    private val bgPaint = Paint().apply { color = Color.argb(255, 40, 44, 48) }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 3f
    }
    private val bubbleFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(210, 45, 140, 240); style = Paint.Style.FILL
    }
    private val bubbleBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 2f
    }
    private val bubbleTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER
    }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(150, 220, 220, 220); textSize = 24f; textAlign = Paint.Align.CENTER
    }
    private val hintText = Lang.t("Kéo để đặt bong bóng", "Drag to place the bubble")
    private val bubbleLabel = Lang.t("Bong bóng", "Bubble")

    // ── Letterboxed proxy geometry (giữ tỉ lệ cụm để marker đúng hình chữ nhật bong bóng) ────────────
    private fun scale(): Float {
        if (width == 0 || height == 0) return 1f
        return minOf(width.toFloat() / clusterWidth, height.toFloat() / clusterHeight)
    }
    private fun proxyLeft(): Float = (width - clusterWidth * scale()) / 2f
    private fun proxyTop(): Float = (height - clusterHeight * scale()) / 2f
    private fun toViewX(cx: Int): Float = proxyLeft() + cx * scale()
    private fun toViewY(cy: Int): Float = proxyTop() + cy * scale()
    private fun toClusterX(vx: Float): Int = ((vx - proxyLeft()) / scale()).toInt()
    private fun toClusterY(vy: Float): Int = ((vy - proxyTop()) / scale()).toInt()

    private fun maxLeft(): Int = (clusterWidth - bubbleWidth).coerceAtLeast(0)
    private fun maxTop(): Int = (clusterHeight - bubbleHeight).coerceAtLeast(0)

    /** Đặt góc-trên-trái bong bóng theo toạ độ CỤM (px), clamp trong cụm. Gọi lúc dựng + sau preset/reset. */
    fun setBubbleTopLeftCluster(x: Int, y: Int) {
        leftXCluster = x.coerceIn(0, maxLeft())
        topYCluster = y.coerceIn(0, maxTop())
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return
        val s = scale()
        val pl = proxyLeft()
        val pt = proxyTop()
        val pr = pl + clusterWidth * s
        val pb = pt + clusterHeight * s

        // Cụm proxy nền + viền.
        canvas.drawRect(pl, pt, pr, pb, bgPaint)
        canvas.drawRect(pl, pt, pr, pb, borderPaint)

        // Gợi ý mờ gần đáy proxy.
        canvas.drawText(hintText, (pl + pr) / 2f, pb - 10f, hintPaint)

        // Marker bong bóng (hình chữ nhật tỉ lệ đúng) tại góc-trên-trái đã clamp.
        val bl = toViewX(leftXCluster)
        val btp = toViewY(topYCluster)
        val br = toViewX(leftXCluster + bubbleWidth)
        val bb = toViewY(topYCluster + bubbleHeight)
        canvas.drawRect(bl, btp, br, bb, bubbleFillPaint)
        canvas.drawRect(bl, btp, br, bb, bubbleBorderPaint)
        bubbleTextPaint.textSize = (bubbleHeight * s * 0.30f).coerceAtLeast(10f)
        val fm = bubbleTextPaint.fontMetrics
        canvas.drawText(bubbleLabel, (bl + br) / 2f, (btp + bb) / 2f - (fm.ascent + fm.descent) / 2f, bubbleTextPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (!insideProxy(event.x, event.y)) return false
                dragging = true
                parent?.requestDisallowInterceptTouchEvent(true)
                updateFromTouch(event.x, event.y)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!dragging) return false
                updateFromTouch(event.x, event.y)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!dragging) return false
                dragging = false
                onMoved(leftXCluster, topYCluster)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun insideProxy(x: Float, y: Float): Boolean {
        val pl = proxyLeft()
        val pt = proxyTop()
        val pr = pl + clusterWidth * scale()
        val pb = pt + clusterHeight * scale()
        return x in pl..pr && y in pt..pb
    }

    /** Đưa TÂM bong bóng về ngón tay (cluster px), rồi clamp góc-trên-trái trong cụm. */
    private fun updateFromTouch(x: Float, y: Float) {
        val centerX = toClusterX(x)
        val centerY = toClusterY(y)
        leftXCluster = (centerX - bubbleWidth / 2).coerceIn(0, maxLeft())
        topYCluster = (centerY - bubbleHeight / 2).coerceIn(0, maxTop())
        invalidate()
    }
}
