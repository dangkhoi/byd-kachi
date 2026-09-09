package com.byd.clusternav.launcher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import kotlin.math.min

/**
 * Sơ đồ xe mini cho widget "Trạng thái xe" — bám prototype `carMiniSvg` (viewport 150×250):
 * thân bo góc + kính trước (xanh) + kính sau + 2 gương xanh lá + vạch đuôi vàng. Vẽ Canvas an toàn (drawRoundRect).
 */
class CarMiniView(context: Context) : View(context) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    override fun onDraw(canvas: Canvas) {
        val s = min(width / 150f, height / 250f)
        if (s <= 0f) return
        val ox = (width - 150f * s) / 2f
        val oy = (height - 250f * s) / 2f
        fun rect(x: Float, y: Float, w: Float, h: Float) = RectF(ox + x * s, oy + y * s, ox + (x + w) * s, oy + (y + h) * s)
        // thân xe
        fill.color = Color.parseColor("#0DFFFFFF"); canvas.drawRoundRect(rect(30f, 20f, 90f, 210f), 34f * s, 34f * s, fill)
        stroke.color = Color.parseColor("#29FFFFFF"); stroke.strokeWidth = 2f * s; canvas.drawRoundRect(rect(30f, 20f, 90f, 210f), 34f * s, 34f * s, stroke)
        // kính trước (xanh)
        fill.color = Color.parseColor("#2E4C7DFF"); canvas.drawRoundRect(rect(42f, 40f, 66f, 46f), 12f * s, 12f * s, fill)
        stroke.color = Color.parseColor("#8078A0FF"); stroke.strokeWidth = 1.5f * s; canvas.drawRoundRect(rect(42f, 40f, 66f, 46f), 12f * s, 12f * s, stroke)
        // kính sau
        fill.color = Color.parseColor("#0DFFFFFF"); canvas.drawRoundRect(rect(42f, 150f, 66f, 52f), 12f * s, 12f * s, fill)
        stroke.color = Color.parseColor("#29FFFFFF"); stroke.strokeWidth = 1.5f * s; canvas.drawRoundRect(rect(42f, 150f, 66f, 52f), 12f * s, 12f * s, stroke)
        // gương (xanh lá)
        fill.color = Color.parseColor("#FF34D399")
        canvas.drawRoundRect(rect(24f, 96f, 7f, 40f), 3f * s, 3f * s, fill)
        canvas.drawRoundRect(rect(119f, 96f, 7f, 40f), 3f * s, 3f * s, fill)
        // vạch đuôi (vàng)
        fill.color = Color.parseColor("#FFFBBF24"); canvas.drawRoundRect(rect(52f, 212f, 46f, 8f), 4f * s, 4f * s, fill)
    }
}
