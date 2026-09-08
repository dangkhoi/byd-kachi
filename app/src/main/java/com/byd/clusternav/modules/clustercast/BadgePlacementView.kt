package com.byd.clusternav.modules.clustercast

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import com.byd.clusternav.Lang
import com.byd.clusternav.speedbadge.BadgeLayout

/**
 * Visual badge-placement editor — the WYSIWYG twin of [CastResizeView] for the speed-limit badge.
 *
 * Draws a **cluster-proxy rectangle** (aspect `clusterWidth:clusterHeight`, letterboxed into the view so
 * the badge marker stays perfectly circular regardless of the view's own aspect) and a **draggable badge
 * marker** rendered like [com.byd.clusternav.speedbadge.SpeedBadgeView] (white fill · red ring · bold
 * number "50") at the badge CENTRE (cluster px → view px). Touch DOWN/MOVE drag the centre to the finger,
 * clamped inside the cluster with the tested pure [BadgeLayout.clampCenter]; ACTION_UP reports the new
 * centre in CLUSTER pixel coordinates via [onMoved] (spec speed-badge-placement-vietmap-logging §4.3, B2).
 *
 * Degrade-safe: no paint before the view is measured; every value is clamped so a corrupt pref or a tiny
 * container can never draw the marker off the proxy.
 */
class BadgePlacementView(
    context: Context,
    private val clusterWidth: Int = 1920,
    private val clusterHeight: Int = 720,
    private val onMoved: (centerXcluster: Int, centerYcluster: Int) -> Unit,
) : View(context) {

    // Badge CENTRE + SIZE in CLUSTER pixel coords (size = dp * density, matching SpeedBadgeOverlay).
    private var centerXCluster = (clusterWidth - 140).coerceAtLeast(0)
    private var centerYCluster = 80.coerceIn(0, clusterHeight)
    private var badgeSizePxCluster = 120

    private var dragging = false

    private val bgPaint = Paint().apply { color = Color.argb(255, 40, 44, 48) }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 3f
    }
    private val badgeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.FILL }
    private val badgeRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.RED; style = Paint.Style.STROKE }
    private val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER
    }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(150, 220, 220, 220); textSize = 24f; textAlign = Paint.Align.CENTER
    }
    private val hintText = Lang.t("Kéo để đặt biển báo", "Drag to place the badge")

    // ── Letterboxed proxy geometry (preserve cluster aspect so the badge stays circular) ────────────
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

    /** Set the badge CENTRE in cluster px (raw; drawn clamped against the current size + cluster). */
    fun setBadgeCenterCluster(cx: Int, cy: Int) {
        centerXCluster = cx
        centerYCluster = cy
        invalidate()
    }

    /** Set the badge SIZE in cluster px (dp*density) so the marker reflects the slider LIVE. */
    fun setBadgeSizeCluster(px: Int) {
        badgeSizePxCluster = px.coerceAtLeast(1)
        invalidate()
    }

    /** Centre clamped for the current size + cluster (each axis independent; oversize → centred). */
    private fun clampedCentre(): Pair<Int, Int> =
        BadgeLayout.clampCenter(centerXCluster, centerYCluster, badgeSizePxCluster, clusterWidth, clusterHeight)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return
        val s = scale()
        val pl = proxyLeft()
        val pt = proxyTop()
        val pr = pl + clusterWidth * s
        val pb = pt + clusterHeight * s

        // Cluster proxy background + border.
        canvas.drawRect(pl, pt, pr, pb, bgPaint)
        canvas.drawRect(pl, pt, pr, pb, borderPaint)

        // Faint hint watermark near the bottom of the proxy.
        canvas.drawText(hintText, (pl + pr) / 2f, pb - 10f, hintPaint)

        // Badge marker (WYSIWYG SpeedBadgeView render) at the clamped centre.
        val (ccx, ccy) = clampedCentre()
        val bcx = toViewX(ccx)
        val bcy = toViewY(ccy)
        val radius = (badgeSizePxCluster * s) / 2f
        if (radius > 1f) {
            val ringWidth = radius * 0.15f
            canvas.drawCircle(bcx, bcy, radius - ringWidth / 2f, badgeBgPaint)
            badgeRingPaint.strokeWidth = ringWidth
            canvas.drawCircle(bcx, bcy, radius - ringWidth / 2f, badgeRingPaint)
            badgeTextPaint.textSize = radius * 0.9f
            val fm = badgeTextPaint.fontMetrics
            canvas.drawText("50", bcx, bcy - (fm.ascent + fm.descent) / 2f, badgeTextPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (!insideProxy(event.x, event.y)) return false
                dragging = true
                parent?.requestDisallowInterceptTouchEvent(true)
                updateCentreFromTouch(event.x, event.y)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!dragging) return false
                updateCentreFromTouch(event.x, event.y)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!dragging) return false
                dragging = false
                val (ccx, ccy) = clampedCentre()
                onMoved(ccx, ccy)
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

    private fun updateCentreFromTouch(x: Float, y: Float) {
        val (cx, cy) = BadgeLayout.clampCenter(
            toClusterX(x), toClusterY(y), badgeSizePxCluster, clusterWidth, clusterHeight,
        )
        centerXCluster = cx
        centerYCluster = cy
        invalidate()
    }
}
