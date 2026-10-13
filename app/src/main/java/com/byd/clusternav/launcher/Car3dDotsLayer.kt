package com.byd.clusternav.launcher

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.view.View
import com.byd.clusternav.launcher.KachiTheme.c
import kotlin.math.PI
import kotlin.math.sin

/**
 * 3.01 R25 — lớp CHẤM trên xe 3D: tô bằng CHÍNH bộ tô của bảng Kính/Đèn 2D ([StateDotPainter]) tại điểm chiếu của từng điểm
 * chạm — kính/cửa/cốp = chấm mức ([StateDotPainter.draw], vòng đóng · quạt · đặc · vòng đứt chưa đọc), đèn = điểm icon
 * ([StateDotPainter.drawIcon]). Phản hồi chạm = NẢY ([pulse]); bị chặn / chỉ xem = LẮC ([shake]) — cùng nhịp với
 * `CarIconBoardView`. Chỉ vẽ View thường (không vẽ lại Filament); `onDraw` không cấp phát.
 */
@SuppressLint("ViewConstructor")
internal class Car3dDotsLayer(context: Context) : View(context) {

    /** Một điểm đang hiện: toạ độ px trong lớp này + trạng thái. [icon] ≠ null ⇒ điểm icon (đèn). */
    class Spot(
        var x: Float = 0f, var y: Float = 0f, var shown: Boolean = false, var dot: CarDots.Dot = UNKNOWN, var lit: Boolean = false,
        var icon: Drawable? = null, var tappable: Boolean = true,
    )

    private val painter = StateDotPainter(context)
    private val d = resources.displayMetrics.density
    private val diameter = StateDotPainter.diameterPx(Float.MAX_VALUE, d)
    private val disc = ICON_DISC_DP * d
    var spots: List<Spot> = emptyList()
    private var pulsed: Spot? = null
    private var pulseAt = 0L
    private var shaken: Spot? = null
    private var shakeAt = 0L

    init { isClickable = false; importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }

    fun pulse(s: Spot) { pulsed = s; pulseAt = SystemClock.uptimeMillis(); postInvalidateOnAnimation() }
    fun shake(s: Spot) { shaken = s; shakeAt = SystemClock.uptimeMillis(); postInvalidateOnAnimation() }

    override fun onDraw(canvas: Canvas) {
        val warn = c(KachiTheme.AMBER)
        for (s in spots) {
            if (!s.shown) continue
            val k = if (s === pulsed) pulseScale() else 1f
            val x = s.x + if (s === shaken) shakeDx() else 0f
            if (s.icon != null) painter.drawIcon(canvas, x, s.y, disc, s.dot.kind, s.icon, k, s.tappable)
            else painter.draw(canvas, x, s.y, diameter * k, s.dot, s.lit, warn)
        }
    }

    private fun pulseScale(): Float {
        val t = (SystemClock.uptimeMillis() - pulseAt) / PULSE_MS.toFloat()
        if (t >= 1f) { pulsed = null; return 1f }
        postInvalidateOnAnimation()
        return 1f + PULSE_GROW * sin(PI.toFloat() * t)
    }

    private fun shakeDx(): Float {
        val t = (SystemClock.uptimeMillis() - shakeAt) / SHAKE_MS.toFloat()
        if (t >= 1f) { shaken = null; return 0f }
        postInvalidateOnAnimation()
        return SHAKE_DP * d * (1f - t) * sin(2f * PI.toFloat() * SHAKE_CYCLES * t)
    }

    companion object {
        private val UNKNOWN = CarDots.Dot(CarDots.Kind.UNKNOWN, 0f, false)
        /** Điểm icon đèn: 24 dp — giữa dải 20–28 dp của bảng Đèn 2D (`CarIconBoardView.DISC_DP`). */
        const val ICON_DISC_DP = 24f
        /** Nảy rõ hơn bảng 2D (chấm 3D nhỏ, nằm trên hình động). */
        const val PULSE_MS = 180L
        const val PULSE_GROW = 0.5f
        const val SHAKE_MS = 240L
        const val SHAKE_DP = 4f
        const val SHAKE_CYCLES = 2f
    }
}
