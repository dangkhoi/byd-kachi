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
 * 3.1.6 R29 (làm lại 15/10) — lớp chấm là BỘ VẼ TỰ TÔ của mặt khung 3D ([AdaptiveInk.probe], khai ở [Car3dInk.declare]): nó
 * đo nền dưới từng điểm ĐANG HIỆN ([AdaptiveInk.backdropAt]: ảnh nền xe 3D ∪ màu sơn ∪ thẻ phía sau), GÓP điểm của bộ màu mỗi
 * họ vào phép chọn họ của cả lớp phủ, rồi vẽ bằng ĐÚNG họ đã chọn ([StateDotPainter.family]) — chấm đổi cùng chữ/icon của lớp
 * phủ, không lớp đỡ riêng. Theo SỰ KIỆN ([Car3dInk]: nạp/đổi ảnh nền · đổi màu sơn · xoay DỪNG · chủ đề/ảnh/độ đục); `onDraw`
 * không đo gì.
 */
@SuppressLint("ViewConstructor")
internal class Car3dDotsLayer(context: Context) : View(context) {

    /** Một điểm đang hiện: toạ độ px trong lớp này + trạng thái. [icon] ≠ null ⇒ điểm icon (đèn). */
    class Spot(
        var x: Float = 0f, var y: Float = 0f, var shown: Boolean = false, var dot: CarDots.Dot = UNKNOWN, var lit: Boolean = false,
        var icon: Drawable? = null, var tappable: Boolean = true,
    ) {
        /** Nền đo dưới điểm (lượt cuối) + điểm theo họ trên nền đó — nền không đổi (xoay mà điểm đứng trên cùng vùng ảnh) ⇒ giữ. */
        internal var under: ContrastBackdrop? = null
        internal val scores = HashMap<InkFamily, Double>()
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

    /** Gắn bộ vẽ tự tô vào cây khi lên cửa sổ (mặt khung 3D tìm nó khi đi cây) — không trong `init`: [probe] khai sau. */
    override fun onAttachedToWindow() { super.onAttachedToWindow(); AdaptiveInk.probe(this, probe) }

    fun pulse(s: Spot) { pulsed = s; pulseAt = SystemClock.uptimeMillis(); postInvalidateOnAnimation() }
    fun shake(s: Spot) { shaken = s; shakeAt = SystemClock.uptimeMillis(); postInvalidateOnAnimation() }

    override fun onDraw(canvas: Canvas) {
        val warn = painter.family?.let { ColorMath.parse(it.palette.amber) } ?: c(KachiTheme.AMBER)   // cùng họ với bộ màu
        for (s in spots) {
            if (!s.shown) continue
            val k = if (s === pulsed) pulseScale() else 1f
            val x = s.x + if (s === shaken) shakeDx() else 0f
            if (s.icon != null) painter.drawIcon(canvas, x, s.y, disc, s.dot.kind, s.icon, k, s.tappable)
            else painter.draw(canvas, x, s.y, diameter * k, s.dot, s.lit, warn)
        }
    }

    private val box = Rect()
    private val loc = IntArray(2)
    private val throttle = LogLineThrottle()

    /**
     * R29 — bộ vẽ tự tô của mặt khung 3D. [Probe.prepare]: đo lại nền dưới các điểm ĐANG HIỆN tại chỗ đang chiếu (vùng = đĩa lớn
     * nhất bộ vẽ đặt dưới điểm); nền như lần trước ⇒ giữ điểm đã tính ([ĐO máy ảo 14/10] đo lại cả 15 điểm mỗi lần: trung vị 3,5 ms).
     * [Probe.score]: MIN điểm mọi điểm đang hiện dưới bộ màu của họ ([StateDotPainter.Inks.score]). [Probe.apply]: vẽ bằng họ đó.
     * Ranh giới fail-soft: lỗi đo ⇒ log qua [LogLineThrottle], điểm không ràng buộc (vẽ theo họ của mặt như mọi nét khác).
     */
    val probe = object : AdaptiveInk.Probe {
        override fun prepare() = guard("dot-measure") {
            if (!isAttachedToWindow || width <= 0) return@guard
            val t0 = SystemClock.elapsedRealtimeNanos()
            getLocationInWindow(loc)
            val half = (maxOf(disc, diameter) / 2f + StateDotPainter.BACKING_DP * d).toInt()
            var moved = 0
            for (s in spots) {
                if (!s.shown) continue
                box.set(loc[0] + s.x.toInt() - half, loc[1] + s.y.toInt() - half, loc[0] + s.x.toInt() + half, loc[1] + s.y.toInt() + half)
                val under = AdaptiveInk.backdropAt(this@Car3dDotsLayer, box)
                if (AdaptiveInk.sameBackdrop(under, s.under)) continue
                s.under = under; s.scores.clear(); moved++
            }
            // perf §8.1: chỉ ghi khi có điểm đổi nền (lượt dừng xoay) — không mỗi lượt chọn.
            if (moved > 0) Log.d(TAG, "R29 chấm: đo lại $moved/${spots.size} điểm · ${(SystemClock.elapsedRealtimeNanos() - t0) / 1000} µs")
        }

        override fun score(f: InkFamily, alt: Int): Double {
            var min = Double.MAX_VALUE
            guard("dot-score") {
                val inks = inksOf(f); val warn = ColorMath.parse(f.palette.amber)
                for (s in spots) {
                    val u = s.under ?: continue
                    if (!s.shown) continue
                    val v = s.scores.getOrPut(f) { inks.score(u, shapeOf(s), warn) }
                    if (v < min) min = v
                }
            }
            return min
        }

        override fun apply(f: InkFamily, alt: Int) { if (painter.family != f) { painter.family = f; invalidate() } }
    }

    private fun shapeOf(s: Spot) = when { s.icon == null -> StateDotPainter.Inks.Shape.DOT; s.tappable -> StateDotPainter.Inks.Shape.BUTTON; else -> StateDotPainter.Inks.Shape.LAMP }

    /** Bộ màu của họ [f] — cùng phép [StateDotPainter.family] vẽ (giữ theo họ: họ là data class, bảng màu đổi ⇒ khoá mới). */
    private fun inksOf(f: InkFamily): StateDotPainter.Inks {
        if (familyInks.size > 8) familyInks.clear()   // đổi bảng màu ⇒ họ mới; trần giữ bộ nhớ có hạn
        return familyInks.getOrPut(f) { StateDotPainter.Inks.of(f.palette, f.lightInk) }
    }
    private val familyInks = HashMap<InkFamily, StateDotPainter.Inks>()

    private inline fun guard(what: String, block: () -> Unit) {
        try { block() } catch (e: RuntimeException) {
            throttle.suppressedBefore("$what: $e", elapsedRealtime())?.let { Log.w(TAG, "lỗi $what — chấm vẽ theo họ của mặt: $e" + LogLineThrottle.repeatSuffix(it)) }
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
