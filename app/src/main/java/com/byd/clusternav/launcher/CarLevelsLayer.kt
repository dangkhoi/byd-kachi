package com.byd.clusternav.launcher

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import android.os.SystemClock
import android.view.View
import kotlin.math.PI
import kotlin.math.sin

/**
 * 3.01 R22 — hình xe của bảng kính vật-là-nút ([ActionMatrixCompact]).
 *
 * ## 3.01 DOT (owner 13/10: *"thay vì vẽ hẳn 1 cái gạch màu xanh, mình vẽ chấm tròn thôi, như bọn kinex, kimlauncher
 * thấy nó đẹp hơn"*)
 * Lớp "kính hạ" của R22·R (khoang tối cắt theo mặt nạ + nét nhấn mép kính + đường bao quanh mảnh) đã GỠ HẲN. Nay mỗi
 * kính là MỘT chấm tròn ([StateDotPainter], trạng thái quyết ở `:core` [CarDots]) đặt tại tâm mảnh kính ĐO trên chính
 * ảnh đang vẽ ([CarDots.center] ← [CarArtPanes.paneCenter] ← [CarArt]) — ảnh mặc định lẫn ảnh BYD dùng cùng một đường.
 * Owner 13/10 *"đẹp, bỏ số luôn em"* ⇒ chấm là chỉ báo DUY NHẤT (không nhãn `%` cạnh xe; số ở `contentDescription`).
 *
 * R23: "ảnh mặc định" là ảnh DỰNG SẴN — ảnh xe chính hãng BYD nếu có ([OemCarArt]), hình học theo [CarArt] của chính ảnh
 * đó; ảnh BYD hỏng lúc giải mã ⇒ đổi sang hình học ảnh mặc định rồi đặt lại khung.
 *
 * Chi phí: bitmap tĩnh cache theo cỡ (kho [CarImageStore]); `onDraw` không cấp phát; chỉ `invalidate` khi số ĐỔI ([set])
 * hoặc ảnh vừa nạp; 0 blur/bóng/vòng hoạt hình.
 */
internal class CarLevelsLayer(
    context: Context,
    private val rows: List<ActionMatrix.Row>,
    /** 3.01 R22·C — vật ở đầu/đuôi xe có nút mà không có số đọc ([ActionMatrix.onCar]: cốp) — chấm gốc là "chưa biết". */
    private val ends: List<CarPart> = emptyList(),
) : View(context) {
    /** Khung px của ảnh xe (đúng tỉ lệ ảnh dựng sẵn [CarArt.aspect]) — chỗ chứa đặt ở `onLayout` ([fit]). */
    val content = RectF()
    /** Vòng nhấn quanh chấm (R22·C: lệnh vừa gửi, đang chờ xe báo). */
    var lit: (CarPart) -> Boolean = { false }
    /** Chấm hiển thị cuối cùng từ chấm đọc được (R22·C: đích lạc quan vừa gửi · trí nhớ lệnh cuối của vật không cảm biến). */
    var dotOf: (CarPart, CarDots.Dot) -> CarDots.Dot = { _, dot -> dot }
    private val cells = HashMap<String, GroupCell>()
    private val car = CarImageLayer(context, defaultArt = true) { invalidate() }
    /** R23 — hình học của ảnh dựng sẵn (BYD nếu có, không thì mặc định); biết TRƯỚC khi ảnh nạp ⇒ khung đúng tỉ lệ ngay. */
    private var art: CarArt = CarImageStore.artOf(CarImageStore.builtinSignature(context))
    private val area = RectF()
    private val dots = StateDotPainter(context)
    private val d = context.resources.displayMetrics.density

    init { set(rows.mapNotNull { it.cell }) }

    fun set(list: List<GroupCell>) {
        var dirty = false
        list.forEach { c -> if (cells[c.id] != c) { cells[c.id] = c; dirty = true } }
        if (dirty) invalidate()
    }

    /** Khung ảnh xe (giữ tỉ lệ, canh giữa) trong vùng [area]. */
    fun fit(area: RectF) {
        this.area.set(area)
        val (l, t, w, s) = SpotTouch.fitCenter(area.left, area.top, area.right, area.bottom, art.aspect)
        val next = RectF(l, t, l + w, t + s)
        if (next == content) return
        content.set(next)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (content.isEmpty) return
        if (car.ensure(content.width().toInt(), content.height().toInt()) && car.art !== art) {
            // Ảnh BYD hỏng lúc giải mã ⇒ kho rơi về ảnh mặc định: đổi hình học theo ẢNH THẬT đang vẽ, đặt lại khung + nhãn.
            art = car.art
            content.setEmpty(); fit(RectF(area)); (parent as? View)?.requestLayout()
            return
        }
        car.draw(canvas, content)
        val size = StateDotPainter.diameterPx(minOf(content.width(), content.height()), d)
        rows.forEach { row ->
            // Vật không có datum đọc ⇒ chưa biết (vòng đứt nét), không bịa "đóng".
            val cell = row.cell?.id?.let { cells[it] } ?: row.cell
            val dot = if (cell == null) CarDots.ofCell(false, null, GroupTone.NEUTRAL)
            else CarDots.ofCell(cell.available, KachiMeter.fractionOf(cell.number, cell.unit), cell.tone)
            val warn = if (cell != null && dot.warn) Color.parseColor(GroupTileView.tintOf(cell.tone)) else 0
            draw(canvas, row.place!!, dot, size, warn)
        }
        ends.forEach { p -> draw(canvas, p, CarDots.ofCell(false, null, GroupTone.NEUTRAL), size, 0) }
    }

    private fun draw(canvas: Canvas, p: CarPart, read: CarDots.Dot, size: Float, warn: Int) {
        val (x, y) = centerOf(p)
        val s = if (p == pulsed) size * pulseScale() else size
        val dx = if (p == shaken) shakeDx() else 0f
        dots.draw(canvas, x + dx, y, s, dotOf(p, read), lit(p), warn)
    }

    /**
     * 3.01 TAPFIX — tâm px (hệ toạ độ của view này = của bảng cha, đặt ở `(0, 0)`) của chấm [p]: MỘT hàm ánh xạ cho cả lớp vẽ
     * lẫn bộ nhận chạm ([SpotTouch.owner]) — vùng chạm không thể lệch khỏi chấm (owner 13/10 *"click lên xa trên top…
     * không đúng vùng"*). Theo [art] ĐANG vẽ (đổi khi ảnh BYD hỏng ⇒ ảnh mặc định).
     */
    fun centerOf(p: CarPart): Pair<Float, Float> = CarDots.center(p, art).let { a ->
        (content.left + a.x * content.width()) to (content.top + a.y * content.height())
    }

    // ── R22·C — phản hồi chạm = chấm nảy MỘT nhịp (owner 13/10: bỏ "mảng xanh" nền nhấn của vùng chạm) ──────────────
    private var pulsed: CarPart? = null
    private var pulseAt = 0L

    /** Chấm của [p] phồng lên rồi về cỡ cũ trong [PULSE_MS] — một lần, không lặp; chạm mới thay nhịp cũ. */
    fun pulse(p: CarPart) {
        pulsed = p; pulseAt = SystemClock.uptimeMillis(); postInvalidateOnAnimation()
    }

    /** Hệ số cỡ của nhịp nảy: `1 + PULSE_GROW · sin(π t)`, t 0..1; hết nhịp ⇒ thôi vẽ lại. */
    private fun pulseScale(): Float {
        val t = (SystemClock.uptimeMillis() - pulseAt) / PULSE_MS.toFloat()
        if (t >= 1f) { pulsed = null; return 1f }
        postInvalidateOnAnimation()
        return 1f + PULSE_GROW * sin(PI.toFloat() * t)
    }

    // ── Cổng chạm chặn (owner 13/10 "Chặn MỞ cốp khi xe chạy") — chấm LẮC ngang một nhịp: "không", trạng thái giữ nguyên ──
    private var shaken: CarPart? = null
    private var shakeAt = 0L

    fun shake(p: CarPart) { shaken = p; shakeAt = SystemClock.uptimeMillis(); postInvalidateOnAnimation() }

    /** Lệch ngang px: 3 chu kỳ tắt dần trong [SHAKE_MS]; hết nhịp ⇒ thôi vẽ lại. */
    private fun shakeDx(): Float {
        val t = (SystemClock.uptimeMillis() - shakeAt) / SHAKE_MS.toFloat()
        if (t >= 1f) { shaken = null; return 0f }
        postInvalidateOnAnimation()
        return SHAKE_DP * d * (1f - t) * sin(2f * PI.toFloat() * SHAKE_CYCLES * t)
    }

    private companion object {
        const val SHAKE_MS = 300L
        const val SHAKE_DP = 4f
        const val SHAKE_CYCLES = 3f
        const val PULSE_MS = 120L
        const val PULSE_GROW = 0.3f
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        car.release()
    }
}
