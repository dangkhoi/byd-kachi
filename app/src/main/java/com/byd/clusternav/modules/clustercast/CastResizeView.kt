package com.byd.clusternav.modules.clustercast

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View

class CastResizeView(
    context: Context,
    private val clusterWidth: Int = 1920,
    private val clusterHeight: Int = 720,
    private val onBoundsChanged: (left: Int, top: Int, right: Int, bottom: Int) -> Unit,
) : View(context) {

    // Current rect in cluster coordinates
    var rectLeft = 0
    var rectTop = 0
    var rectRight = 1920
    var rectBottom = 720

    // Horizontal slot band in cluster coords (R5). Default = whole cluster → full-mode behavior.
    private var bandMinX = 0
    private var bandMaxX = clusterWidth

    private val bgPaint = Paint().apply { color = Color.DKGRAY }
    private val rectPaint = Paint().apply { color = Color.argb(80, 66, 133, 244); style = Paint.Style.FILL }
    private val borderPaint = Paint().apply { color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 3f; isAntiAlias = true }
    private val handlePaint = Paint().apply { color = Color.WHITE; isAntiAlias = true }
    private val textPaint = Paint().apply { color = Color.WHITE; textSize = 28f; isAntiAlias = true; textAlign = Paint.Align.CENTER }
    private val outOfBandPaint = Paint().apply { color = Color.argb(130, 0, 0, 0); style = Paint.Style.FILL }

    private enum class DragTarget { NONE, LEFT, RIGHT, TOP, BOTTOM, TL, TR, BL, BR, CENTER }
    private var dragTarget = DragTarget.NONE
    private var dragStartX = 0f
    private var dragStartY = 0f
    private var dragStartRect = RectF()

    private val HANDLE_RADIUS = 16f
    private val HIT_AREA = 40f

    // Scale factor: view pixels to cluster pixels
    private fun scaleX(): Float = clusterWidth.toFloat() / width.toFloat()
    private fun scaleY(): Float = clusterHeight.toFloat() / height.toFloat()
    // Cluster coords to view coords
    private fun toViewX(cx: Int): Float = cx / scaleX()
    private fun toViewY(cy: Int): Float = cy / scaleY()
    // View coords to cluster coords
    private fun toClusterX(vx: Float): Int = (vx * scaleX()).toInt().coerceIn(bandMinX, bandMaxX)
    private fun toClusterY(vy: Float): Int = (vy * scaleY()).toInt().coerceIn(0, clusterHeight)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return
        // Background = cluster physical area
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
        // Shade the out-of-band area so the draggable slot half is visually clear (R5)
        if (bandMinX > 0) canvas.drawRect(0f, 0f, toViewX(bandMinX), height.toFloat(), outOfBandPaint)
        if (bandMaxX < clusterWidth) canvas.drawRect(toViewX(bandMaxX), 0f, width.toFloat(), height.toFloat(), outOfBandPaint)
        // App rect
        val vl = toViewX(rectLeft); val vt = toViewY(rectTop)
        val vr = toViewX(rectRight); val vb = toViewY(rectBottom)
        canvas.drawRect(vl, vt, vr, vb, rectPaint)
        canvas.drawRect(vl, vt, vr, vb, borderPaint)
        // Corner handles
        canvas.drawCircle(vl, vt, HANDLE_RADIUS, handlePaint)
        canvas.drawCircle(vr, vt, HANDLE_RADIUS, handlePaint)
        canvas.drawCircle(vl, vb, HANDLE_RADIUS, handlePaint)
        canvas.drawCircle(vr, vb, HANDLE_RADIUS, handlePaint)
        // Size label
        val w = rectRight - rectLeft; val h = rectBottom - rectTop
        val sizeStr = "${w}\u00d7${h}"
        canvas.drawText(sizeStr, (vl + vr) / 2, (vt + vb) / 2 - 10, textPaint)
        canvas.drawText("[$rectLeft,$rectTop,$rectRight,$rectBottom]", (vl + vr) / 2, (vt + vb) / 2 + 30, textPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                dragTarget = detectTarget(event.x, event.y)
                if (dragTarget != DragTarget.NONE) {
                    dragStartX = event.x; dragStartY = event.y
                    dragStartRect = RectF(toViewX(rectLeft), toViewY(rectTop), toViewX(rectRight), toViewY(rectBottom))
                    parent.requestDisallowInterceptTouchEvent(true)
                    return true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (dragTarget == DragTarget.NONE) return false
                val dx = event.x - dragStartX; val dy = event.y - dragStartY
                applyDrag(dx, dy)
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragTarget != DragTarget.NONE) {
                    dragTarget = DragTarget.NONE
                    onBoundsChanged(rectLeft, rectTop, rectRight, rectBottom)
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }

    private fun detectTarget(x: Float, y: Float): DragTarget {
        val vl = toViewX(rectLeft); val vt = toViewY(rectTop)
        val vr = toViewX(rectRight); val vb = toViewY(rectBottom)
        val h = HIT_AREA
        // Corners first (higher priority)
        if (dist(x, y, vl, vt) < h) return DragTarget.TL
        if (dist(x, y, vr, vt) < h) return DragTarget.TR
        if (dist(x, y, vl, vb) < h) return DragTarget.BL
        if (dist(x, y, vr, vb) < h) return DragTarget.BR
        // Edges
        if (x in (vl - h)..(vl + h) && y in vt..vb) return DragTarget.LEFT
        if (x in (vr - h)..(vr + h) && y in vt..vb) return DragTarget.RIGHT
        if (y in (vt - h)..(vt + h) && x in vl..vr) return DragTarget.TOP
        if (y in (vb - h)..(vb + h) && x in vl..vr) return DragTarget.BOTTOM
        // Center
        if (x in vl..vr && y in vt..vb) return DragTarget.CENTER
        return DragTarget.NONE
    }

    private fun applyDrag(dx: Float, dy: Float) {
        val minW = 480; val minH = 180
        when (dragTarget) {
            DragTarget.LEFT -> rectLeft = toClusterX(dragStartRect.left + dx).coerceAtMost(rectRight - minW)
            DragTarget.RIGHT -> rectRight = toClusterX(dragStartRect.right + dx).coerceAtLeast(rectLeft + minW)
            DragTarget.TOP -> rectTop = toClusterY(dragStartRect.top + dy).coerceAtMost(rectBottom - minH)
            DragTarget.BOTTOM -> rectBottom = toClusterY(dragStartRect.bottom + dy).coerceAtLeast(rectTop + minH)
            DragTarget.TL -> {
                rectLeft = toClusterX(dragStartRect.left + dx).coerceAtMost(rectRight - minW)
                rectTop = toClusterY(dragStartRect.top + dy).coerceAtMost(rectBottom - minH)
            }
            DragTarget.TR -> {
                rectRight = toClusterX(dragStartRect.right + dx).coerceAtLeast(rectLeft + minW)
                rectTop = toClusterY(dragStartRect.top + dy).coerceAtMost(rectBottom - minH)
            }
            DragTarget.BL -> {
                rectLeft = toClusterX(dragStartRect.left + dx).coerceAtMost(rectRight - minW)
                rectBottom = toClusterY(dragStartRect.bottom + dy).coerceAtLeast(rectTop + minH)
            }
            DragTarget.BR -> {
                rectRight = toClusterX(dragStartRect.right + dx).coerceAtLeast(rectLeft + minW)
                rectBottom = toClusterY(dragStartRect.bottom + dy).coerceAtLeast(rectTop + minH)
            }
            DragTarget.CENTER -> {
                val w = (dragStartRect.right - dragStartRect.left)
                val h = (dragStartRect.bottom - dragStartRect.top)
                // Keep the whole box inside the active band (full view when no band set)
                val minVx = toViewX(bandMinX)
                val maxVx = toViewX(bandMaxX)
                val nl = (dragStartRect.left + dx).coerceIn(minVx, (maxVx - w).coerceAtLeast(minVx))
                val nt = (dragStartRect.top + dy).coerceIn(0f, height.toFloat() - h)
                rectLeft = toClusterX(nl); rectTop = toClusterY(nt)
                rectRight = toClusterX(nl + w); rectBottom = toClusterY(nt + h)
            }
            else -> {}
        }
        // Clamp X into the active band (whole cluster if none set), Y into cluster height
        rectLeft = rectLeft.coerceIn(bandMinX, bandMaxX)
        rectTop = rectTop.coerceIn(0, clusterHeight)
        rectRight = rectRight.coerceIn(bandMinX, bandMaxX)
        rectBottom = rectBottom.coerceIn(0, clusterHeight)
    }

    private fun dist(x1: Float, y1: Float, x2: Float, y2: Float): Float =
        Math.sqrt(((x1 - x2) * (x1 - x2) + (y1 - y2) * (y1 - y2)).toDouble()).toFloat()

    /** Set the rect in cluster coords. X is clamped into the active slot band (R5). */
    fun setBounds(l: Int, t: Int, r: Int, b: Int) {
        rectLeft = l.coerceIn(bandMinX, bandMaxX)
        rectTop = t.coerceIn(0, clusterHeight)
        rectRight = r.coerceIn(bandMinX, bandMaxX)
        rectBottom = b.coerceIn(0, clusterHeight)
        invalidate()
    }

    /**
     * Constrain horizontal dragging to a slot band [minX, maxX] in cluster coords (R5).
     * Left slot → [0, split]; right slot → [split, clusterWidth]. Re-clamps the current rect
     * into the band. Without a band the view spans the whole cluster (full-mode default).
     */
    fun setSlotBand(minX: Int, maxX: Int) {
        bandMinX = minX.coerceIn(0, clusterWidth)
        bandMaxX = maxX.coerceIn(bandMinX + 1, clusterWidth)
        rectLeft = rectLeft.coerceIn(bandMinX, bandMaxX)
        rectRight = rectRight.coerceIn(bandMinX, bandMaxX)
        if (rectRight <= rectLeft) rectRight = bandMaxX
        invalidate()
    }
}
