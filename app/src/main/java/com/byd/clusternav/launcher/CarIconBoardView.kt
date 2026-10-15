package com.byd.clusternav.launcher

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.util.Log
import android.view.MotionEvent
import android.view.SoundEffectConstants
import android.view.View
import android.view.ViewGroup
import com.byd.clusternav.R
import kotlin.math.PI
import kotlin.math.sin
import com.byd.clusternav.launcher.KachiSpace as Sp

/**
 * ═══ 3.01 R24 — BẢNG ICON TRÊN HÌNH XE ([GroupLayout.ICONS] + ảnh có điểm; owner 13/10 *"cái widget đèn cũng làm vậy luôn, có
 * hình xe và chấm tròn hay icon đèn xung quanh, đúng vị trí đèn, nhấn tắt, nhấn mở"*) ═══════════════════════════════════════
 *
 * Ảnh xe dựng sẵn (BYD nếu có, [CarImageLayer]) + mỗi vật một ĐIỂM icon tại chỗ đo trên CHÍNH ảnh đó ([CarArt.spot] →
 * [CarIconBoard.place]); điểm vẽ bằng [StateDotPainter.drawIcon] (tắt = icon mờ trong vòng · bật = icon nhấn + quầng · chưa
 * biết = vòng đứt nét). Không chữ trên màn: tên + việc cú chạm sẽ làm ở `contentDescription` của vùng chạm.
 *
 * ## Chạm — KHÔNG mở đường thứ hai
 * Vật có nút: ô nút THẬT ([MatrixKit.tile] → [ControlTileFactory], cùng đường ghi + cổng chạm `gated`) gắn `GONE`; phản hồi =
 * điểm nảy. 3.01 TAPFIX (owner 13/10 *"nhấn nút này nó xanh nút khác"*): MỌI điểm vẽ ra — cả điểm chỉ-xem — sở hữu vùng quanh
 * nó ([SpotTouch.owner]: tâm gần nhất, trong tầm [SpotTouch.reachPx]); view này tự nhận cú chạm (không vùng chữ nhật con nào nở
 * đè điểm khác), bắt chủ lúc NHẤN và chỉ bắn khi THẢ vẫn trên cùng chủ. Điểm chỉ-xem nuốt cú chạm: lắc nhẹ + đọc to
 * "<tên> · chỉ xem", không lệnh. Nút trợ năng mỗi điểm một ô vuông riêng không chồng ([SpotTouch.ownSquare]).
 * 3.01 POLISH: điểm có nút = icon trong ĐĨA TÔNG (vòng khi bật) · điểm chỉ-xem = ĐÈN BÁO trần màu loại đèn ([LampTone],
 * [StateDotPainter.drawLamp]); xi-nhan bật ⇒ nháy tại chỗ ~1,5 Hz (không đồng bộ pha bóng thật — [LampTone]).
 * 3.01 TURN/LAMPLIVE: BẬT/TẮT đèn theo xe THẬT khi socket helper đang chạy — nay tự giữ khi bảng này hiện + màn sáng (`HalSignalHub`,
 * [LampLive], đè ở `collectHome`); socket không nối ⇒ điểm theo getter `getLightStatus` (xi-nhan: trên trim đã đo luôn TẮT).
 *
 * ## Lùi theo chỗ đo
 * Hình xe ([CarIconBoard.fits] ở cỡ điểm lớn → nhỏ) → lưới icon ([IconBoardView], dạng cũ) → ô tóm tắt
 * (khung ngoài, khi lưới báo tràn). Chi phí: bitmap cache theo cỡ; `onDraw` không cấp phát; vẽ lại khi số/ảnh ĐỔI hoặc khi nảy.
 */
@SuppressLint("ViewConstructor")
internal class CarIconBoardView(
    context: Context, private val tiles: List<IconTile>, data: WidgetData,
) : ViewGroup(context), GroupBody {

    private val grid = IconBoardView(context, tiles, data)
    override val consumed: Set<String> = grid.consumed

    private val kit = MatrixKit(context, data)
    private val touch = kit.px(Sp.TOUCH)
    private val gap = kit.px(Sp.XS).toFloat()
    private val d = context.resources.displayMetrics.density
    private val cells = HashMap<String, GroupCell>().apply { tiles.forEach { t -> t.cell?.let { put(it.id, it) } } }

    private val layer = Layer()
    private var art: CarArt = CarImageStore.artOf(CarImageStore.builtinSignature(context))
    private var spots: List<CarIconBoard.Spot> = CarIconBoard.place(tiles, art).orEmpty()
    private val icons = HashMap<String, Drawable?>()
    private val hidden = HashMap<String, View>()
    /** Nút trợ năng của TỪNG ô (cả ô chỉ-xem) — theo CHÍNH ô, không theo vị trí trong danh sách điểm. Không nền/sóng. */
    private val nodes = LinkedHashMap<IconTile, View>()
    private var disc = 0f
    private var onCar = false
    /** Chủ của cú chạm đang giữ (bắt lúc NHẤN). */
    private var down: IconTile? = null

    init {
        tiles.mapNotNull { it.action }.forEach { a ->
            hidden[a.id] = kit.tile(a, null, icon = false).also { it.visibility = View.GONE; addView(it) }
        }
        tiles.forEach { t ->
            nodes[t] = View(context).apply {
                isClickable = true; isFocusable = true
                setOnClickListener { press(t) }
                addView(this)
            }
        }
        addView(layer); addView(grid)
        describe()
    }

    /** Toạ độ px các điểm trong khung hiện tại (cùng thứ tự [spots]). */
    private fun points(): List<Pair<Float, Float>> {
        val f = layer.frame
        return spots.map { CarIconBoard.centerIn(it, f.left, f.top, f.width(), f.height()) }
    }

    /** Ô của điểm sở hữu `(x, y)` — `:core` quyết ([SpotTouch.owner]); `null` = xa mọi điểm. */
    private fun ownerAt(x: Float, y: Float): IconTile? = SpotTouch.owner(points(), x, y, SpotTouch.reachPx(touch))?.let { spots[it].tile }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean = onCar

    /** Không `performClick` ở đây có chủ ý: đường trợ năng là nút của TỪNG điểm ([nodes]), không phải cả bảng. */
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (!onCar) return super.onTouchEvent(ev)
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> down = ownerAt(ev.x, ev.y)
            MotionEvent.ACTION_UP -> {
                val t = down; down = null
                val hit = t != null && ownerAt(ev.x, ev.y) === t
                Log.i(TAP_TAG, "tap-board lights (${ev.x.toInt()},${ev.y.toInt()}) → ${if (hit) t!!.action?.id ?: "chỉ xem ${t.icon}" else "không ai"}")
                if (hit) { playSoundEffect(SoundEffectConstants.CLICK); press(t!!) }
            }
            // Review 3.1 Pass 2 [P3]: ngón thứ hai chạm xuống ⇒ huỷ cú chạm (không đoán ngón nào là "cú bấm"); ACTION_UP sau đó
            // mang toạ độ ngón CÒN LẠI — không huỷ thì ngón 1 nhấn chấm A, ngón 2 thả trên A vẫn bắn A.
            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN -> down = null
        }
        return true   // bảng nuốt mọi cú chạm như bản cũ (vùng phủ kín) — chỗ trống không lọt xuống ô cha
    }

    /** Chạm điểm [t]: có nút ⇒ đảo; chỉ-xem ⇒ KHÔNG lệnh, lắc nhẹ + đọc to "chỉ xem" (không bao giờ chuyển cho láng giềng). */
    private fun press(t: IconTile) {
        val a = t.action
        if (a == null) {
            layer.shake(t)
            announceForAccessibility("${t.name} · ${context.getString(R.string.klight_view_only)}")
            return
        }
        layer.pulse(t); tap(a); layer.invalidate(); describe()
    }

    override fun refresh(m: GroupBoardModel) {
        grid.refresh(m)
        var dirty = false
        m.cells.forEach { c -> if (cells[c.id] != null && cells[c.id] != c) { cells[c.id] = c; dirty = true } }
        // 3.1.7 STALE-AUDIT: điểm KHÔNG ô đọc (đèn đọc…) sáng theo bảng lệnh cuối/đích giữ — phím/giọng nói đổi nó mà không ô đọc nào
        // đổi ⇒ so trạng thái điểm với cái ĐANG VẼ, không chỉ so ô đọc (thiếu nó: bật đèn bằng phím, điểm vẫn tắt tới khi một ô đọc đổi).
        val now = tiles.map(::kind)
        if (now != drawnKinds) { drawnKinds = now; dirty = true }
        if (dirty) { layer.invalidate(); describe() }
    }

    /** Trạng thái điểm ở lượt vẽ lại gần nhất do [refresh] xin — mốc so của STALE-AUDIT. */
    private var drawnKinds: List<CarDots.Kind> = emptyList()

    /**
     * Chạm = ĐẢO cái người lái ĐANG THẤY (owner *"nhấn tắt, nhấn mở"*): đặt tường minh qua CÙNG hàm ghi của ô ([MatrixKit.set]
     * → `ActionTile.set`, qua cổng chạm) — không dựa cờ RAM của ô ẩn (ô ẩn không được nhịp poll làm mới ⇒ đèn đang sáng mà cờ
     * nói tắt, `performClick` sẽ gửi BẬT lần nữa). Ô không đặt tường minh được (không phải TOGGLE) ⇒ `performClick`.
     */
    private fun tap(a: GroupActionCell) {
        val t = tiles.first { it.action?.id == a.id }
        val on = kind(t) != CarDots.Kind.OPEN
        if (!kit.set(a.id, if (on) 1 else 0)) hidden[a.id]?.performClick()
        // POLISH2: giữ đích vừa gửi tới khi xe xác nhận / ~3 s ([LampHold]); hẹn ĐÚNG MỘT lần vẽ lại lúc hết hạn (số đọc thắng).
        LampHold.shared.put(a.id, on, SystemClock.uptimeMillis())
        layer.postInvalidateDelayed(LampHold.HOLD_MS + 1); postDelayed(::describe, LampHold.HOLD_MS + 1)
    }

    /**
     * Trạng thái điểm: số đọc, hoặc lệnh Kachi vừa gửi — đích lạc quan [LampHold] (tới khi xe xác nhận / ~3 s), hoặc nút không có
     * số mà đã gửi BẬT — `:core` quyết.
     */
    private fun kind(t: IconTile): CarDots.Kind {
        val st = ControlTileState.shared
        val cell = t.cell?.id?.let { cells[it] }
        val read = cell?.takeIf { it.available }?.let { it.tone != GroupTone.NEUTRAL }
        val sent = t.action?.id?.let { id ->
            LampHold.shared.held(id, read, SystemClock.uptimeMillis()) ?: if (cell == null && st.isOn(id)) true else null
        }
        return CarIconBoard.kind(cell, sent)
    }

    /** Đọc to mỗi điểm: tên · (số) · việc cú chạm KẾ làm (`chạm: bật/tắt`) hoặc `chỉ xem`. */
    private fun describe() {
        nodes.forEach { (t, v) ->
            val say = when {
                t.action == null -> R.string.klight_view_only
                kind(t) == CarDots.Kind.OPEN -> R.string.klight_tap_off
                else -> R.string.klight_tap_on
            }
            val value = t.cell?.id?.let { cells[it] }?.value
            v.contentDescription = listOfNotNull(t.name, value, context.getString(say)).joinToString(" · ")
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val bounded = MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.UNSPECIFIED
        val h = MeasureSpec.getSize(heightMeasureSpec)
        onCar = bounded && plan(w, h)
        layer.visibility = if (onCar) View.VISIBLE else View.GONE
        grid.visibility = if (onCar) View.GONE else View.VISIBLE
        val placed = spots.mapTo(HashSet()) { it.tile }
        nodes.forEach { (t, v) -> v.visibility = if (onCar && t in placed) View.VISIBLE else View.GONE }
        val exact = { px: Int -> MeasureSpec.makeMeasureSpec(px, MeasureSpec.EXACTLY) }
        if (onCar) {
            layer.measure(exact(w), exact(h))
            nodes.values.forEach { it.measure(exact(touch), exact(touch)) }   // cỡ thật ở onLayout
            setMeasuredDimension(w, h)
        } else {
            grid.measure(exact(w), heightMeasureSpec)
            setMeasuredDimension(w, grid.measuredHeight)
        }
    }

    /** Khung xe + cỡ điểm vừa [w]×[h] (thử lớn → nhỏ); `false` ⇒ lưới icon. Ghi [Layer.frame] + [disc]. */
    private fun plan(w: Int, h: Int): Boolean {
        if (spots.size < CarIconBoard.MIN_SPOTS) return false
        for (dp in DISC_DP) {
            val px = dp * d
            val f = frameIn(w, h, px)
            if (f.height() < MIN_CAR_DP * d) continue
            val pts = spots.map { CarIconBoard.centerIn(it, f.left, f.top, f.width(), f.height()) }
            if (!CarIconBoard.fits(pts, px, gap, w.toFloat(), h.toFloat())) continue
            layer.frame.set(f); disc = px
            return true
        }
        return false
    }

    /** Khung ảnh đúng tỉ lệ [CarArt.aspect] lớn nhất trong `[w]×[h]` chừa một điểm [px] (nửa mỗi mép), canh GIỮA view. */
    private fun frameIn(w: Int, h: Int, px: Float): RectF {
        val s = minOf((w - px) / art.aspect, h - px)
        val fw = s * art.aspect
        return RectF((w - fw) / 2f, (h - s) / 2f, (w + fw) / 2f, (h + s) / 2f)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val w = r - l; val h = b - t
        if (!onCar) { grid.layout(0, 0, w, grid.measuredHeight); return }
        layer.layout(0, 0, w, h)
        val pts = points()
        spots.forEachIndexed { i, s ->
            val v = nodes[s.tile] ?: return@forEachIndexed
            val z = SpotTouch.ownSquare(pts, i, touch.toFloat(), w, h)
            v.measure(MeasureSpec.makeMeasureSpec(z.width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(z.height, MeasureSpec.EXACTLY))
            v.layout(z.left, z.top, z.right, z.bottom)
        }
        if (changed) logCentres(this, spots.map { it.tile.action?.id ?: it.tile.icon }, pts)
    }

    /** Lớp vẽ: ảnh xe + điểm icon. */
    private inner class Layer : View(context) {
        val frame = RectF()
        private val car = CarImageLayer(context, defaultArt = true) { invalidate() }
        private val dots = StateDotPainter(context)
        private var pulsed: IconTile? = null
        private var pulseAt = 0L
        private var shaken: IconTile? = null
        private var shakeAt = 0L
        /** Đèn đang nháy → mốc thấy BẬT (`uptimeMillis`). Rỗng ⇒ không hẹn vẽ lại nào. */
        private val blinkSince = HashMap<IconTile, Long>()
        private var blinkAt = 0L

        fun pulse(t: IconTile) { pulsed = t; pulseAt = SystemClock.uptimeMillis(); postInvalidateOnAnimation() }
        fun shake(t: IconTile) { shaken = t; shakeAt = SystemClock.uptimeMillis(); postInvalidateOnAnimation() }

        override fun onDraw(canvas: Canvas) {
            if (frame.isEmpty) return
            val now = SystemClock.uptimeMillis()
            if (car.ensure(frame.width().toInt(), frame.height().toInt()) && car.art !== art) {
                // Ảnh BYD hỏng lúc giải mã ⇒ kho rơi về ảnh mặc định: điểm theo ẢNH THẬT đang vẽ, đo lại.
                art = car.art; spots = CarIconBoard.place(tiles, art).orEmpty(); this@CarIconBoardView.requestLayout(); return
            }
            car.draw(canvas, frame)
            spots.forEach { s ->
                val (x, y) = CarIconBoard.centerIn(s, frame.left, frame.top, frame.width(), frame.height())
                val icon = icons.getOrPut(s.tile.icon) { KachiTheme.iconRes(s.tile.icon).takeIf { it != 0 }?.let { context.getDrawable(it)?.mutate() } }
                val scale = if (s.tile === pulsed) pulseScale() else 1f
                val dx = if (s.tile === shaken) shakeDx() else 0f
                val k = kind(s.tile)
                if (s.tile.action != null) dots.drawIcon(canvas, x + dx, y, disc, k, icon, scale, tappable = true)
                else dots.drawLamp(canvas, x + dx, y, disc, blinkKind(s.tile, k, now), icon, LampTone.of(s.tile.cell?.id)?.argb, scale)
            }
            scheduleBlink(now)
        }

        /**
         * 3.01 POLISH — đèn tự nháy ([LampTone.blinks]: xi-nhan) đang BẬT ⇒ pha nháy TẠI CHỖ ~1,5 Hz từ lúc thấy bật. ⚠ KHÔNG đồng
         * bộ pha bóng thật: số đọc là TRẠNG THÁI (socket [LampLive] hoặc getter 10 s), không phải pha bóng. Tắt / chưa biết ⇒ xoá mốc.
         */
        private fun blinkKind(t: IconTile, k: CarDots.Kind, now: Long): CarDots.Kind {
            if (k != CarDots.Kind.OPEN || !LampTone.blinks(t.cell?.id)) { blinkSince.remove(t); return k }
            val since = blinkSince.getOrPut(t) { now }
            return if (LampTone.litAt(now - since)) CarDots.Kind.OPEN else CarDots.Kind.CLOSED
        }

        /**
         * Hẹn ĐÚNG MỘT lần vẽ lại ở mép pha kế — chỉ khi có đèn đang nháy VÀ bảng đang hiện trên cửa sổ hiện (màn tắt ⇒ Activity
         * dừng ⇒ cửa sổ ẩn ⇒ không hẹn). Không có đèn nháy ⇒ không nhịp nào (luật hiệu năng: không vòng hoạt hoạ lúc nghỉ).
         */
        private fun scheduleBlink(now: Long) {
            if (blinkSince.isEmpty() || !isAttachedToWindow || !isShown || windowVisibility != View.VISIBLE) return
            val at = now + blinkSince.values.minOf { LampTone.nextEdgeIn(now - it) }
            if (at == blinkAt) return   // mép này đã hẹn (vẽ thêm do nảy/số đổi không chồng thêm hẹn)
            blinkAt = at; postInvalidateDelayed(at - now)
        }

        /** Cửa sổ hiện lại (màn bật / về HOME) ⇒ vẽ lại để nhịp nháy nối tiếp nếu đèn còn bật. */
        override fun onWindowVisibilityChanged(visibility: Int) {
            super.onWindowVisibilityChanged(visibility)
            if (visibility == View.VISIBLE && blinkSince.isNotEmpty()) invalidate()
        }

        private fun pulseScale(): Float {
            val t = (SystemClock.uptimeMillis() - pulseAt) / PULSE_MS.toFloat()
            if (t >= 1f) { pulsed = null; return 1f }
            postInvalidateOnAnimation()
            return 1f + PULSE_GROW * sin(PI.toFloat() * t)
        }

        /** Lắc ngang nhẹ (chỉ-xem: "không có gì để bật"): tắt dần trong [SHAKE_MS]; hết nhịp ⇒ thôi vẽ lại. */
        private fun shakeDx(): Float {
            val t = (SystemClock.uptimeMillis() - shakeAt) / SHAKE_MS.toFloat()
            if (t >= 1f) { shaken = null; return 0f }
            postInvalidateOnAnimation()
            return SHAKE_DP * d * (1f - t) * sin(2f * PI.toFloat() * SHAKE_CYCLES * t)
        }

        override fun onDetachedFromWindow() { super.onDetachedFromWindow(); car.release(); blinkSince.clear(); blinkAt = 0L }
    }

    private companion object {
        /** Cỡ điểm thử từ lớn tới nhỏ (dp) — điểm phải chứa được icon đèn đọc ra ở tầm nhìn lái. */
        val DISC_DP = floatArrayOf(28f, 24f, 20f)
        /** Hình xe không thấp hơn chừng này (dp) — cùng sàn bảng Kính ([ActionMatrixCompact]). */
        const val MIN_CAR_DP = 72
        const val PULSE_MS = 120L
        const val PULSE_GROW = 0.25f
        /** Cùng thẻ nhật ký lệnh — một bộ lọc logcat thấy cả cú chạm lẫn lệnh nó sinh ra. */
        const val TAP_TAG = "KachiCtl"
        /** Lắc điểm chỉ-xem: nhẹ hơn lắc "bị chặn" của chấm cốp (4 dp) — chỉ là "chỗ này chỉ để xem". */
        const val SHAKE_MS = 240L
        const val SHAKE_DP = 2f
        const val SHAKE_CYCLES = 2f
    }
}

/**
 * 3.01 TAPFIX — ghi một dòng mỗi lần bảng đặt lại khung: tâm VẼ của từng chấm theo toạ độ MÀN HÌNH (chạm thử đúng chỗ vẽ, đối
 * chiếu cú chạm `tap-board … →` cùng thẻ). Chỉ lúc `onLayout(changed)` — không ở nhịp vẽ.
 */
internal fun logCentres(v: View, names: List<String>, pts: List<Pair<Float, Float>>) {
    val o = IntArray(2); v.getLocationOnScreen(o)
    Log.i("KachiCtl", "tap-board centres " + names.zip(pts).joinToString(" ") { (n, p) -> "$n=(${(o[0] + p.first).toInt()},${(o[1] + p.second).toInt()})" })
}
