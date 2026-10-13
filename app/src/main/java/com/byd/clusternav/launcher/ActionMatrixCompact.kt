package com.byd.clusternav.launcher

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.SoundEffectConstants
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import com.byd.clusternav.R
import com.byd.clusternav.launcher.KachiTheme.c
import com.byd.clusternav.launcher.KachiSpace as Sp

/**
 * ═══ 3.01 R22·B/C — HÌNH XE MÀ CHÍNH VẬT LÀ NÚT (owner 13/10: *"cả mớ số 100% 50% nhìn rối, đâu có đẹp gì"*) ═════════
 *
 * Mọi cỡ ô đủ ngưỡng ([GroupActionMatrix.compactForm]) đều dùng dạng này. Xe ảnh top-view ở giữa ([CarLevelsLayer]), mỗi
 * kính một CHẤM tròn ([CarDots]); số chính xác ở `contentDescription`.
 *
 * ## 3.01 TAPFIX — chạm thuộc CHẤM gần nhất, đo từ CHÍNH chỗ vẽ (owner 13/10 *"click lên xa trên top… không đúng vùng"*)
 * Bản R22·C chia khung thành phần tư/dải theo neo CHUNG (`GroupActionMatrix.zones`, đã gỡ) nở tới mép ô ⇒ chạm sát mép trên ô (xa
 * mọi chấm) vẫn mở kính trước. Nay view này tự nhận cú chạm: chủ = chấm có tâm VẼ gần nhất ([CarLevelsLayer.centerOf] — cùng
 * hàm lớp vẽ dùng) trong tầm [SpotTouch.reachPx]; xa hơn ⇒ không lệnh. Bắt chủ lúc NHẤN, chỉ bắn khi THẢ vẫn trên cùng chủ.
 * Nút chân là nút thật — chạm rơi vào nút chân không bị bảng giành. Nút trợ năng mỗi chấm một ô vuông riêng không chồng
 * ([SpotTouch.ownSquare]).
 *
 * ## R22·C — chạm chấm = VÒNG đóng → 50 % → mở hẳn → đóng (owner 13/10 *"nhấn vô các hình tròn cho nó mở đóng luôn"*)
 * Công tắc chế độ `Mở hẳn | Hé` và chân ba nút đã GỠ. Bước kế quyết ở `:core` ([WindowCycle.next]) từ MỨC ĐANG CÓ — số đọc
 * `window_*`, hoặc đích LẠC QUAN vừa gửi khi xe chưa kịp báo ([pending], để chạm hai lần nhanh vẫn đi 50 → 100); lệnh chọn
 * theo mức của nút ([WindowCycle.plan]). Chấm hiện ngay đích vừa gửi (vòng nhấn = đang chờ xe), rồi theo số đọc thật khi
 * số tới đích hoặc hết [PENDING_MS].
 *
 * ## Cốp trên cùng hình xe (owner 13/10 *"thêm cả cốp vào đó luôn"*)
 * [ActionMatrix.onCar]: nút cốp SẴN CÓ thành chấm ở đuôi xe, chạm = ô cốp `performClick()` (cùng vòng mở/đóng, cùng đường
 * ghi như nhóm Cửa & khoang). Cốp KHÔNG có cảm biến ([ĐO xe 09-25] — `getHatchDoorStatus` rỗng) ⇒ chấm nói lệnh CUỐI Kachi
 * gửi: đã gửi MỞ ⇒ chấm đầy; còn lại ⇒ vòng đứt nét "chưa biết" — không bao giờ khẳng định "đóng". Ô quá thấp cho thêm
 * một dải chạm ⇒ cốp xuống chân thành nút icon (không mất nút).
 *
 * ## Chân — ba nút hình, không chữ
 * [ActionMatrix.openAll] (`windows_all` đặt BẬT tường minh) · [ActionMatrix.halfAll] (`win_half_all` đặt BẬT tường minh —
 * 3.01 POLISH, owner *"thêm 1 action là 50% tất cả kính"*) · [ActionMatrix.closeAll] (`windows_close_all`): icon vector
 * `ic_win_open_all_g`/`ic_win_half_all_g`/`ic_win_close_all_g` (khung cửa sổ + mũi đôi / mũi đơn), tên ở `contentDescription`.
 *
 * ## Đường ghi — KHÔNG mở đường thứ hai
 * Mỗi việc có một ô nút THẬT dựng bằng [MatrixKit.tile] ([ControlTileFactory]), gắn làm con `GONE`; chấm/nút chân chỉ
 * `performClick()` ô đó hoặc đặt tường minh qua [MatrixKit.set] (CÙNG hàm ghi của ô — [ActionTile.set]).
 */
@SuppressLint("ViewConstructor")
internal class ActionMatrixCompact(context: Context, private val mx: ActionMatrix, private val kit: MatrixKit) : ViewGroup(context) {

    private val carRect = RectF()
    private val ends = mx.onCar
    private val levels = CarLevelsLayer(context, mx.placed, ends.map { it.first })

    /** Ô nút thật theo mã việc — đích `performClick` của vùng chạm và nút chân. */
    private val hidden = LinkedHashMap<String, View>()
    /** Số đọc mới nhất theo mã ô — mức hiện tại của vòng chạm + `contentDescription`. */
    private val cells = HashMap<String, GroupCell>()
    /** Đích lạc quan vừa gửi theo vật: (đích %, hạn `uptimeMillis`). */
    private val pending = HashMap<CarPart, Pair<Int, Long>>()
    private val zones: List<Pair<ActionMatrix.Row, View>> = mx.placed.map { r -> r to zoneView(r.place!!) { cycle(r) } }
    private val endZones: List<View> = ends.map { (p, a) -> zoneView(p) { act(a) } }
    /** Chủ cú chạm đang giữ (bắt lúc NHẤN): vật + việc của nó. */
    private var down: Pair<CarPart, () -> Unit>? = null
    private val bars: List<View> = listOfNotNull(
        mx.openAll?.let { a -> barButton(R.drawable.ic_win_open_all_g, R.string.kwin_open_all) { if (!kit.set(a.id, 1)) act(a) } },
        mx.halfAll?.let { a -> barButton(R.drawable.ic_win_half_all_g, R.string.kwin_half_all) { if (!kit.set(a.id, 1)) act(a) } },
        mx.closeAll?.let { a -> barButton(R.drawable.ic_win_close_all_g, R.string.kwin_close_all) { act(a) } },
    )
    /** Cốp ở chân — chỉ hiện khi ô quá thấp cho dải chạm đuôi xe ([endsOnCar] = false). */
    private val endBars: List<View> = ends.map { (_, m) -> barButton(KachiTheme.iconRes(m.icon), 0) { act(m) }.also { it.contentDescription = m.label } }

    private val gap = kit.px(Sp.S)
    private val touch = kit.px(Sp.TOUCH)
    private var form: GroupActionMatrix.CompactForm? = null
    private var endsOnCar = true

    init {
        (mx.placed.flatMap { it.slots } + mx.whole.flatMap { it.slots } + mx.footer).filterNotNull().forEach { a ->
            if (a.id !in hidden) hidden[a.id] = kit.tile(a, a.verb.ifEmpty { null }, icon = false).also {
                it.visibility = View.GONE; addView(it)
            }
        }
        addView(levels)
        levels.lit = { p -> live(p) != null }
        levels.dotOf = { p, read -> shown(p, read) }
        (zones.map { it.second } + endZones + bars + endBars).forEach { addView(it) }
        mx.placed.mapNotNull { it.cell }.forEach { cells[it.id] = it }
        describeZones()
    }

    /** Cổng chạm vừa chặn MỞ một vật đầu/đuôi (cốp) ⇒ chấm của nó lắc ([TileSafetyGate.refuse]) — ô nút thật đang `GONE`. */
    private val refused: (String) -> Unit = { id -> ends.firstOrNull { it.second.id == id }?.let { levels.shake(it.first); describeZones() } }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); TileSafetyGate.listen(refused) }
    override fun onDetachedFromWindow() { TileSafetyGate.unlisten(refused); super.onDetachedFromWindow() }

    fun refresh(m: GroupBoardModel) {
        m.cells.forEach { c -> cells[c.id] = c }
        // Số đọc đã tới đích ⇒ thôi lạc quan, chấm theo xe từ đây.
        mx.placed.forEach { r -> val p = live(r.place!!); if (p != null && read(r) == p) pending.remove(r.place) }
        levels.set(m.cells)
        levels.invalidate()
        describeZones()
    }

    /** Đo các phần rồi hỏi `:core` dạng nào vừa khung (`null` ⇒ [ActionMatrixView] lùi danh sách gọn). */
    fun fits(w: Int, h: Int): Boolean {
        val withEnds = GroupActionMatrix.compactForm(w, h, metrics(true))
        endsOnCar = withEnds != null || ends.isEmpty()
        form = withEnds ?: GroupActionMatrix.compactForm(w, h, metrics(false))
        return form != null
    }

    private fun metrics(onCar: Boolean = endsOnCar) = GroupActionMatrix.CompactMetrics(
        touchPx = touch, gap = gap, minCarPx = kit.px(MIN_CAR_DP), barPx = touch,
        bars = bars.size + if (onCar) 0 else endBars.size,
        ends = if (onCar) ends.map { CarLayout.part(it.first).y < 0.5f }.distinct().size else 0,
    )

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec); val h = MeasureSpec.getSize(heightMeasureSpec)
        if (form == null) fits(w, h)
        val exact = { px: Int -> MeasureSpec.makeMeasureSpec(px, MeasureSpec.EXACTLY) }
        levels.measure(exact(w), exact(h))
        (bars + endBars + endZones + zones.map { it.second }).forEach { it.measure(exact(touch), exact(touch)) }   // vùng: cỡ thật ở onLayout
        endZones.forEach { it.visibility = if (endsOnCar) View.VISIBLE else View.GONE }
        endBars.forEach { it.visibility = if (endsOnCar) View.GONE else View.VISIBLE }
        setMeasuredDimension(w, h)
    }

    /** Vật trên hình nhận chạm lúc này (kính + cốp khi cốp ở trên hình), cùng việc của mỗi vật. */
    private fun targets(): List<Pair<CarPart, () -> Unit>> =
        mx.placed.map { r -> r.place!! to { cycle(r) } } + if (endsOnCar) ends.map { (p, a) -> p to { act(a) } } else emptyList()

    /** Vật sở hữu `(x, y)` — `:core` quyết ([SpotTouch.owner]) trên tâm VẼ thật ([CarLevelsLayer.centerOf]). */
    private fun ownerAt(x: Float, y: Float): Pair<CarPart, () -> Unit>? {
        val ts = targets()
        return SpotTouch.owner(ts.map { levels.centerOf(it.first) }, x, y, SpotTouch.reachPx(touch))?.let { ts[it] }
    }

    /** Chạm rơi vào nút chân (nút thật, có sóng) ⇒ để nút xử lý; còn lại bảng nhận. */
    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean =
        (bars + endBars).none { it.visibility == View.VISIBLE && ev.x >= it.left && ev.x < it.right && ev.y >= it.top && ev.y < it.bottom }

    /** Không `performClick` ở đây có chủ ý: đường trợ năng là nút của TỪNG chấm ([zones]/[endZones]), không phải cả bảng. */
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> down = ownerAt(ev.x, ev.y)
            MotionEvent.ACTION_UP -> {
                val o = down; down = null
                val hit = o != null && ownerAt(ev.x, ev.y)?.first == o.first
                Log.i("KachiCtl", "tap-board windows (${ev.x.toInt()},${ev.y.toInt()}) → ${if (hit) o!!.first else "không ai"}")
                if (o != null && hit) {
                    playSoundEffect(SoundEffectConstants.CLICK); levels.pulse(o.first); o.second()
                }
            }
            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN -> down = null   // Pass 2 [P3]: đa chạm ⇒ huỷ (như bảng Đèn)
        }
        return true   // vùng xe nuốt cú chạm như bản cũ (vùng phủ kín) — chỗ trống không lọt xuống ô cha
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val w = r - l; val h = b - t
        val f = form ?: GroupActionMatrix.CompactForm.STACK
        val stack = f == GroupActionMatrix.CompactForm.STACK
        val m = metrics()
        var (aw, ah) = GroupActionMatrix.compactCarArea(f, w, h, m)
        var ax = 0; var ay = 0
        val side = gap
        carRect.set((ax + side).toFloat(), ay.toFloat(), (ax + aw - side).toFloat(), (ay + ah).toFloat())
        levels.fit(carRect)
        val content = RectF(levels.content)
        // Gom nhóm: vùng xe thừa theo chiều xếp (ảnh letterbox chừa trống) ⇒ thu vùng về đúng hình xe (vẫn đủ đích chạm)
        // và canh GIỮA cả cụm xe · chân — [ĐO] ô 2×6: chân dính đáy, xe trôi giữa ~200 px trống.
        if (stack) {
            val need = maxOf(content.height().toInt(), (2 + m.ends) * touch)
            val used = need + if (bars.isNotEmpty() || !endsOnCar) touch + gap else 0
            if (need < ah) { ay = (h - used) / 2; ah = need }
        } else {
            val need = maxOf(content.width().toInt() + 2 * side, 2 * touch)
            if (need < aw) { ax += (aw - need) / 2; aw = need }
        }
        carRect.set((ax + side).toFloat(), ay.toFloat(), (ax + aw - side).toFloat(), (ay + ah).toFloat())
        levels.layout(0, 0, w, h)
        levels.fit(carRect)
        // Nút trợ năng: ô vuông riêng quanh TÂM VẼ của từng chấm (không chồng) — chạm tay đi qua [onTouchEvent].
        val placed = zones.map { (row, v) -> row.place!! to v } + if (endsOnCar) ends.map { it.first }.zip(endZones) else emptyList()
        val pts = placed.map { levels.centerOf(it.first) }
        placed.forEachIndexed { i, (_, v) ->
            val z = SpotTouch.ownSquare(pts, i, touch.toFloat(), w, h)
            v.measure(MeasureSpec.makeMeasureSpec(z.width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(z.height, MeasureSpec.EXACTLY))
            v.layout(z.left, z.top, z.right, z.bottom)
        }
        if (changed) logCentres(this, placed.map { it.first.name }, pts)
        val foot = bars + if (endsOnCar) emptyList() else endBars
        if (stack) row(foot, CenteredRow.start(w, foot.size, touch, gap), ay + ah + gap, gap)   // tâm cụm = tâm widget (owner 13/10)
        else row(foot, ax + aw + gap, (h - touch) / 2, gap)
    }

    private fun row(views: List<View>, x0: Int, y: Int, g: Int) {
        var x = x0
        views.forEach { it.layout(x, y, x + touch, y + touch); x += touch + g }
    }

    /** Mức đang có của một kính (%): số đọc; chưa đọc / không phải % ⇒ `null`. */
    private fun read(r: ActionMatrix.Row): Int? = r.cell?.id?.let { cells[it] }?.let { c ->
        WindowCycle.levelOf(c.available, KachiMeter.fractionOf(c.number, c.unit))
    }

    /** Đích lạc quan còn hạn của [p], hoặc `null`. */
    private fun live(p: CarPart): Int? = pending[p]?.takeIf { it.second > SystemClock.uptimeMillis() }?.first

    /** Chạm kính: bước kế của vòng (`:core`), ghi qua ô nút thật, chấm hiện ngay đích vừa gửi. */
    private fun cycle(r: ActionMatrix.Row) {
        val p = r.place ?: return
        val target = WindowCycle.next(live(p) ?: read(r))
        val step = WindowCycle.plan(r.slots, target) ?: return
        if (!kit.set(step.action.id, if (step.on) 1 else 0)) return
        pending[p] = target to SystemClock.uptimeMillis() + PENDING_MS
        levels.invalidate(); describeZones()
    }

    /** Chấm hiển thị: đích lạc quan (kính) · lệnh cuối đã gửi (vật không cảm biến) · không thì đúng số đọc. */
    private fun shown(p: CarPart, read: CarDots.Dot): CarDots.Dot {
        live(p)?.let { return CarDots.ofCell(true, it / WindowCycle.FULL.toFloat(), GroupTone.NEUTRAL) }
        val end = ends.firstOrNull { it.first == p }?.second ?: return read
        return if (ControlTileState.shared.isOn(end.id)) CarDots.ofCell(true, 1f, GroupTone.NEUTRAL) else read
    }

    /** Vùng chạm đọc to: tên vật · số đọc (`35 %`, `—` khi chưa đọc) · việc cú chạm KẾ sẽ làm. */
    private fun describeZones() {
        zones.forEach { (r, v) ->
            val next = WindowCycle.next(live(r.place!!) ?: read(r))
            val say = when (next) {
                0 -> context.getString(R.string.kwin_tap_close)
                WindowCycle.FULL -> context.getString(R.string.kwin_tap_open_full)
                else -> context.getString(R.string.kwin_tap_open_pct, next)
            }
            v.contentDescription = listOfNotNull(r.title, r.cell?.id?.let { cells[it] }?.value, say).joinToString(" · ")
        }
        ends.zip(endZones).forEach { (e, v) ->
            val m = e.second
            val say = context.getString(if (ControlTileState.shared.isOn(m.id)) R.string.kwin_tap_close else R.string.kwin_tap_open)
            v.contentDescription = "${m.label} · $say"
        }
    }

    private fun act(a: GroupActionCell?) {
        hidden[a?.id ?: return]?.performClick()
        levels.invalidate(); describeZones()
    }

    /**
     * Vùng chạm VÔ HÌNH của vật [p] (owner 13/10 *"khi click sao nó có mảng xanh hiện lên vậy"*): KHÔNG nền, không sóng —
     * vùng ≥ 48 dp lớn hơn hẳn hình vẽ nên mọi nền nhấn đều thành một mảng vuông giữa ảnh xe. Phản hồi tay = CHÍNH CHẤM nảy
     * một nhịp ([CarLevelsLayer.pulse]) + đổi trạng thái; tiếng chạm hệ thống giữ như mọi nút khác (`performClick`).
     */
    private fun zoneView(p: CarPart, onTap: () -> Unit): View = View(context).apply {
        isClickable = true; isFocusable = true
        setOnClickListener { levels.pulse(p); onTap() }
    }

    /** Sóng chạm màu nhấn 20 % trên [mask] (chỉ alpha có nghĩa) — phản hồi tay; [content] = nền nghỉ (có thể `null`). */
    private fun ripple(mask: GradientDrawable, content: GradientDrawable?): RippleDrawable =
        RippleDrawable(ColorStateList.valueOf(ColorMath.withAlpha(c(KachiTheme.ACCENT), RIPPLE_ALPHA)), content, mask)

    /**
     * Nút chân CHỈ icon: đĩa TÔNG mềm (mực 8 %, không viền — ZeroBorder) + sóng nhấn khi chạm; icon tint mực [KachiTheme.ICON],
     * NHẤN ⇒ màu nhấn ([KachiTheme.ACCENT_INK]). [say] = chuỗi đọc to (0 = đặt sau). Đích chạm 48 dp, icon [Sp.ICON_M].
     */
    private fun barButton(icon: Int, say: Int, onTap: () -> Unit): View = FrameLayout(context).apply {
        val disc = { color: Int -> GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) } }
        background = ripple(disc(c(KachiTheme.INK)), disc(ColorMath.withAlpha(c(KachiTheme.INK), REST_ALPHA)))
        addView(ImageView(context).apply {
            setImageResource(icon)
            imageTintList = ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_pressed), intArrayOf()), intArrayOf(c(KachiTheme.ACCENT_INK), c(KachiTheme.ICON)),
            )
            isDuplicateParentStateEnabled = true
        }, FrameLayout.LayoutParams(kit.px(Sp.ICON_M), kit.px(Sp.ICON_M), Gravity.CENTER))
        if (say != 0) contentDescription = context.getString(say)
        isClickable = true; isFocusable = true
        setOnClickListener { onTap() }
    }

    private companion object {
        /** Hình xe gọn không thấp hơn chừng này (dp) — dưới đó thân xe thành một vệt. */
        const val MIN_CAR_DP = 72
        /** Sóng chạm: mực nhấn 20 %. */
        const val RIPPLE_ALPHA = 0x33
        /** Đĩa nghỉ của nút chân: mực 8 % — thấy là nút, không thành mảng màu. */
        const val REST_ALPHA = 0x14
        /** Giữ đích lạc quan tối đa chừng này khi xe chưa báo tới đích (kính chạy vài giây — [CtlSafetyPolicy.MOVES_SLOWLY]). */
        const val PENDING_MS = 12_000L
    }
}
