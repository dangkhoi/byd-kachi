package com.byd.clusternav.launcher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.view.View

/** Nền "wall": tối + 2 vầng sáng (xanh trên-trái, tím dưới-phải) — khớp prototype kachi-workspace.html. */
class WallView(context: Context) : View(context) {
    private val base = Paint().apply { color = Color.parseColor(KachiTheme.BG) }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        canvas.drawRect(0f, 0f, w, h, base)
        val r = maxOf(w, h)
        glow.shader = RadialGradient(w * 0.13f, h * -0.06f, r * 0.42f,
            Color.parseColor("#112036"), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, glow)
        glow.shader = RadialGradient(w * 0.94f, h * 1.08f, r * 0.40f,
            Color.parseColor("#160f28"), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, glow)
    }
}
