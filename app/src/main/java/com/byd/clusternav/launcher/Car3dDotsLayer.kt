package com.byd.clusternav.launcher

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.os.SystemClock.elapsedRealtime
import android.util.Log
import android.view.View
import com.byd.clusternav.launcher.KachiTheme.c
import kotlin.math.PI
import kotlin.math.sin

/**
 * 3.01 R25 — lớp CHẤM trên xe 3D: tô bằng CHÍNH bộ tô của bảng Kính/Đèn 2D ([StateDotPainter]) tại điểm chiếu của từng điểm
 * chạm — kính/cửa/cốp = chấm mức ([StateDotPainter.draw], vòng đóng · quạt · đặc · vòng đứt chưa đọc), đèn = điểm icon
 * ([StateDotPainter.drawIcon]). Phản hồi chạm = NẢY ([pulse]); bị chặn / chỉ xem = LẮC ([shake]) — cùng nhịp với
 * `CarIconBoardView`. Chỉ vẽ View thường (không vẽ lại Filament); `onDraw` không cấp phát.
 *
 * 3.1.5 R29 T29-6 — lớp đỡ của từng điểm ([Spot.halos]) giải trên nền ĐO dưới chính điểm ([adapt]: ảnh nền xe 3D ∪ màu sơn ∪
 * thẻ phía sau — [AdaptiveInk.backdropAt]) theo SỰ KIỆN ([Car3dInk]: nạp/đổi ảnh nền · đổi màu sơn · xoay DỪNG · chủ đề/ảnh/độ
 * đục), không mỗi khung; `onDraw` chỉ tra ô ([StateDotPainter.Inks.dotSlot]).
 */
@SuppressLint("ViewConstructor")
internal class Car3dDotsLayer(context: Context) : View(context) {

    /** Một điểm đang hiện: toạ độ px trong lớp này + trạng thái. [icon] ≠ null ⇒ điểm icon (đèn). */
    class Spot(
        var x: Float = 0f, var y: Float = 0f, var shown: Boolean = false, var dot: CarDots.Dot = UNKNOWN, var lit: Boolean = false,
        var icon: Drawable? = null, var tappable: Boolean = true,
    ) {
        /** Lớp đỡ đo được theo ô ([StateDotPainter.Inks.halos]); `null` = chưa đo (vẽ như cũ). */
        var halos: IntArray? = null
        /** Nền + bộ màu của lần giải cuối — nền không đổi (xoay mà điểm đứng trên cùng vùng ảnh) ⇒ không giải lại. */
        internal var under: ContrastBackdrop? = null
        internal var inks: StateDotPainter.Inks? = null
    }

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
            val h = s.halos
            if (s.icon != null) painter.drawIcon(canvas, x, s.y, disc, s.dot.kind, s.icon, k, s.tappable, h?.get(s.dot.kind.ordinal) ?: 0)
            else painter.draw(canvas, x, s.y, diameter * k, s.dot, s.lit, warn, h?.get(StateDotPainter.Inks.dotSlot(s.dot, s.lit)) ?: 0)
        }
    }

    private val box = Rect()
    private val loc = IntArray(2)
    private val throttle = LogLineThrottle()

    /**
     * R29 T29-6 — giải lớp đỡ cho các điểm ĐANG HIỆN tại chỗ đang chiếu (điểm ẩn: chỉ hiện lại khi chiếu lại ⇒ lượt dừng xoay kế
     * giải nó). Chỉ [Car3dInk] gọi, theo sự kiện. Vùng đo = đĩa lớn nhất bộ vẽ đặt dưới điểm (nền tối thò ra
     * [StateDotPainter.BACKING_DP]). Nền + bộ màu như lần trước ⇒ giữ kết quả ([ĐO máy ảo 14/10] giải lại cả 15 điểm mỗi lần:
     * trung vị 3,5 ms, đỉnh 15 ms trên luồng chính).
     */
    fun adapt() {
        if (!isAttachedToWindow || width <= 0) return
        // Pass 5: ranh giới fail-soft như mọi cửa của AdaptiveInk — `adapt` còn được gọi ngoài `guarded`/`onFrame` (`paintChanged` →
        // `hero.reproject` → `Car3dInk.settle`) ⇒ một lỗi đo nền không được lên luồng chính của HOME. Hỏng ⇒ chấm vẽ như chưa đo.
        try {
            val t0 = SystemClock.elapsedRealtimeNanos()
            val inks = painter.inks(); val p = KachiTheme.palette; val warn = c(KachiTheme.AMBER)
            getLocationInWindow(loc)
            val half = (maxOf(disc, diameter) / 2f + StateDotPainter.BACKING_DP * d).toInt()
            var solved = 0
            for (s in spots) {
                if (!s.shown) continue
                box.set(loc[0] + s.x.toInt() - half, loc[1] + s.y.toInt() - half, loc[0] + s.x.toInt() + half, loc[1] + s.y.toInt() + half)
                val under = AdaptiveInk.backdropAt(this, box)
                if (s.halos != null && s.inks == inks && AdaptiveInk.sameBackdrop(under, s.under)) continue
                val shape = when { s.icon == null -> StateDotPainter.Inks.Shape.DOT; s.tappable -> StateDotPainter.Inks.Shape.BUTTON; else -> StateDotPainter.Inks.Shape.LAMP }
                s.halos = inks.halos(under, shape, warn, p) { line ->
                    throttle.suppressedBefore(line, elapsedRealtime())?.let { Log.w(TAG, "dưới sàn: $line" + LogLineThrottle.repeatSuffix(it)) }
                }
                s.under = under; s.inks = inks; solved++
            }
            Log.d(TAG, "R29 chấm: giải $solved/${spots.size} điểm · ${(SystemClock.elapsedRealtimeNanos() - t0) / 1000} µs")
            if (solved > 0) invalidate()
        } catch (e: RuntimeException) {
            throttle.suppressedBefore("adapt: $e", elapsedRealtime())?.let { Log.w(TAG, "lỗi giải lớp đỡ chấm — vẽ như chưa đo: $e" + LogLineThrottle.repeatSuffix(it)) }
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
        private const val TAG = "KachiContrast"
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
