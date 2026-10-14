package com.byd.clusternav.launcher

import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import com.byd.clusternav.R
import com.byd.clusternav.launcher.KachiSpace as Sp

/**
 * ═══ 3.01 R25 — điểm chạm của widget xe 3D: trạng thái chấm · cú chạm · nút mở/đóng hết · nút trợ năng ═══════════
 *
 * Tách khỏi [Car3dWidgetView] (trần 500 dòng). Quyết định thuần ở `:core` ([Car3dHotspots] chọn ô nút + chấm, [WindowCycle]
 * bước kính, [Car3dPick] chạm THẲNG vào bộ phận — owner 13/10: bộ phận mà hình 3D tự nói trạng thái thì không chấm). Ghi qua ô nút THẬT
 * gắn `GONE` ([MatrixKit.tile]): kính = đặt tường minh ([MatrixKit.set]) theo vòng 50 → 100 → 0; đèn = đảo cái đang thấy;
 * cốp = `performClick` ô cốp (qua cổng chạm `TileSafetyGate` — xe chưa chứng minh đứng yên ⇒ chặn, chấm lắc); cửa = chỉ xem.
 * Mọi cú chạm ghi log `Car3d tap <điểm> → <mã nút>` (đo trên máy ảo/xe bằng logcat).
 */
internal class Car3dTaps(private val host: FrameLayout, private val kit: MatrixKit, private val dots: Car3dDotsLayer) {

    private val ctx = host.context
    private var model: Car3dModel? = null
    private val hotspots = ArrayList<Car3dHotspot>()
    private val resolved = HashMap<String, Car3dHotspots.Resolved>()
    private val spots = LinkedHashMap<String, Car3dDotsLayer.Spot>()
    private val nodes = LinkedHashMap<String, View>()
    private val hidden = HashMap<String, View>()
    private val icons = HashMap<String, Drawable?>()
    private val cells = HashMap<String, GroupCell>()
    /** Đích lạc quan của kính vừa gửi: (đích %, hạn `uptimeMillis`). */
    private val pending = HashMap<String, Pair<Int, Long>>()
    /** Lúc chạm gần nhất từng điểm (chạm đúp — [WindowCycle.isDouble]) · thời hạn chạm đúp của hệ thống. */
    private val lastTap = HashMap<String, Long>()
    private val doubleMs = android.view.ViewConfiguration.getDoubleTapTimeout().toLong()
    private var groups: Map<String, GroupBoardModel> = emptyMap()
    private val touch = kit.px(Sp.TOUCH)
    private var down: String? = null
    private var order: List<String> = emptyList()

    /** Cổng chạm vừa chặn mã [id] (cốp khi xe chưa chứng minh đứng yên) ⇒ chấm cốp lắc. */
    val refused: (String) -> Unit = { id ->
        hotspots.filter { h -> resolved[h.id]?.actions?.any { it.id == id } == true }.forEach { h ->
            spots[h.id]?.takeIf { it.shown }?.let(dots::shake) ?: shakeHost()
        }
    }

    /** Bộ phận không chấm bị từ chối/chỉ xem ⇒ cả khung lắc nhẹ (cùng nhịp lắc chấm: 4 dp, 240 ms). */
    private fun shakeHost() {
        val d = Car3dDotsLayer.SHAKE_DP * ctx.resources.displayMetrics.density
        android.animation.ObjectAnimator.ofFloat(host, View.TRANSLATION_X, 0f, d, -d, d / 2, -d / 2, 0f)
            .setDuration(Car3dDotsLayer.SHAKE_MS).start()
    }

    /** Ba nút mở hết · hé 50 % hết · đóng hết kính (ô nút SẴN CÓ của nhóm Kính, icon chân bảng Kính 2D). */
    fun buttons(): List<View> {
        val w = GroupBoard.of(Car3dCatalog.GROUPS.first(), CarStatus()) ?: return emptyList()
        return Car3dCatalog.PILLS.mapNotNull { id ->
            val a = w.actions.firstOrNull { it.id == id } ?: return@mapNotNull null
            val close = id == Car3dCatalog.PILLS.last()
            val half = !close && id != Car3dCatalog.PILLS.first()
            val (icon, say) = when {
                close -> R.drawable.ic_win_close_all_g to R.string.kwin_close_all
                half -> R.drawable.ic_win_half_all_g to R.string.kwin_half_all
                else -> R.drawable.ic_win_open_all_g to R.string.kwin_open_all
            }
            button(icon, say) {
                Log.i(TAG, "tap nút → ${a.id}")
                // Mở hết / hé hết: đặt BẬT tường minh (như chân bảng Kính 2D); đóng hết: bấm ô.
                if (close || !kit.set(a.id, 1)) tile(a)?.performClick()
            }
        }
    }

    /** Đích chạm 3D (hộp thế giới của bộ phận) — [Car3dPick]. */
    private val targets = LinkedHashMap<String, Car3dPick.Target>()
    private var cam: Car3dCamera? = null
    private var camBounds: Car3dBounds? = null
    private var camW = 0; private var camH = 0

    fun bind(m: Car3dModel, placed: Set<String>, boxes: Map<String, Car3dPick.Target>, rest: (Car3dHotspot) -> Pair<V3, V3>?) {
        model = m
        targets.clear(); targets += boxes
        hotspots.clear(); hotspots += m.hotspots.filter { it.id in placed }
        restBox.clear(); dotAt.clear()
        hotspots.forEach { h -> rest(h)?.let { restBox[h.id] = it } }
        lampOf.clear()
        hotspots.filter { it.group == Car3dCatalog.GROUPS[1] && it.target.isNotEmpty() && it.shownBy != null }.forEach { h ->
            m.variants.filter { v -> v.read.isNotEmpty() && (v.open == h.shownBy || Car3dNames.matches(h.shownBy!!, v.open)) }
                .forEach { v -> lampOf.putIfAbsent(v.read, h.target to h.read) }
        }
        order = hotspots.map { it.id }
        hotspots.forEach { h ->
            spots.getOrPut(h.id) { Car3dDotsLayer.Spot() }
            nodes.getOrPut(h.id) {
                View(ctx).apply {
                    isClickable = true; isFocusable = true; visibility = View.INVISIBLE   // KHÔNG nền/sóng (owner 13/10)
                    setOnClickListener { press(h) }
                    host.addView(this, FrameLayout.LayoutParams(touch, touch))
                }
            }
        }
        dots.spots = spots.values.toList()
    }

    /** Toạ độ khung (px) đang bị nút khác chiếm (icon ghế — [Car3dHeroLayer.owns]); chủ gán. Bán kính chấm để dời. */
    var blocked: (Float, Float) -> Boolean = { _, _ -> false }
    private val dotR = StateDotPainter.diameterPx(Float.MAX_VALUE, ctx.resources.displayMetrics.density) / 2f

    /** Hộp NGHỈ (thế giới) của bộ phận mỗi điểm · chỗ đặt chấm theo tư thế đang vẽ ([Car3dPick.dotPoint]; vắng ⇒ điểm neo). */
    private val restBox = HashMap<String, Pair<V3, V3>>()
    private val dotAt = HashMap<String, V3>()

    /** Cửa/nắp/kính vừa đổi tư thế ⇒ chấm đi theo bộ phận ([now] = hộp đang vẽ), kẹp trong đích chạm của chính nó. */
    fun redot(now: (Car3dHotspot) -> Pair<V3, V3>?) {
        for (h in hotspots) {
            val rest = restBox[h.id] ?: continue; val t = targets[h.id] ?: continue
            now(h)?.let { dotAt[h.id] = Car3dPick.dotPoint(t.anchor, rest, it, t) }
        }
    }

    /** POLISH2 — đích chạm của điểm [t] đổi theo tư thế bộ phận (nắp cốp đang/đã mở: chạm vào nắp ĐANG giơ lên để đóng). */
    fun retarget(t: Car3dPick.Target) { if (t.id in targets) targets[t.id] = t }

    /** POLISH2 — có đèn khoang ([Car3dHotspot.glow]) đang BẬT (đích lạc quan / lệnh cuối) ⇒ nội thất hắt sáng. */
    fun glowing(): Boolean = hotspots.any { it.glow && spots[it.id]?.dot?.kind == CarDots.Kind.OPEN }

    fun clear() { model = null; hotspots.clear(); spots.clear(); restBox.clear(); dotAt.clear(); nodes.clear(); hidden.clear(); targets.clear(); order = emptyList() }

    /** Mức kính đang thấy của điểm [id] (lệnh vừa gửi thắng số đọc; chưa đọc ⇒ `null`) — kính 3D tụt theo nó. */
    fun level(id: String): Int? = live(id) ?: resolved[id]?.let(::read)

    /** Đổ trạng thái xe tại chỗ (không dựng view mới ngoài ô nút ẩn lần đầu). */
    fun fill(d: WidgetData) {
        last = d
        groups = Car3dCatalog.GROUPS.mapNotNull { g -> GroupBoard.of(g, d.car, d.units)?.let { g to it } }.toMap()
        groups.values.forEach { gm -> gm.cells.forEach { cells[it.id] = it } }
        for (h in hotspots) {
            val gm = groups[h.group] ?: continue
            val r = Car3dHotspots.resolve(gm, h).also { resolved[h.id] = it }
            r.actions.forEach { tile(it) }
            val s = spots[h.id] ?: continue
            s.tappable = r.actions.isNotEmpty()
            s.dot = dot(h, r)
            s.lit = s.dot.kind == CarDots.Kind.OPEN
            s.icon = if (h.group == Car3dCatalog.GROUPS[1]) icon(r.actions.firstOrNull()?.icon ?: r.cell?.icon) else null
        }
        describe()
        dots.invalidate()
    }

    /** Biến thể ([Car3dVariant]) đang "khác nghỉ": ô đọc [read] (một hoặc nhiều `a|b`) của nhóm [group]; rỗng ⇒ không bao giờ. */
    fun active(group: String, read: String): Boolean {
        val cells = groups[group]?.cells ?: return false
        val now = Car3dHotspots.activeAny(read, ControlTileState.shared::isOn) { id -> cells.firstOrNull { it.id == id } }
        // POLISH2: đèn vừa chạm — đích lạc quan [LampHold] thắng số đọc cũ tới khi Ô ĐỌC CỦA NÚT xác nhận / ~3 s (cùng bộ với bảng
        // Đèn 2D). Xác nhận theo ô của nút (`light_drl`), không theo cả biểu thức biến thể (`light_drl|light_side`).
        val (id, own) = lampOf[read] ?: return now
        val mine = own?.let { k -> cells.firstOrNull { it.id == k } }?.takeIf { it.available }?.let { Car3dHotspots.active(it) }
        return LampHold.shared.held(id, mine, SystemClock.uptimeMillis()) ?: now
    }

    /**
     * Ô đọc của biến thể đèn → mã nút đèn điều khiển nó: biến thể có mẫu MỞ trùng/khớp [Car3dHotspot.shownBy] của điểm đèn có nút.
     * Mọi biến thể cùng ô đọc đi chung (chạm DRL trái ⇒ cả hai bên DRL theo đích lạc quan, như xe thật).
     */
    private val lampOf = HashMap<String, Pair<String, String?>>()   // ô đọc biến thể → (mã nút, ô đọc của chính điểm đèn)

    private fun dot(h: Car3dHotspot, r: Car3dHotspots.Resolved): CarDots.Dot {
        live(h.id)?.let { return CarDots.ofCell(true, it / WindowCycle.FULL.toFloat(), GroupTone.NEUTRAL) }
        r.actions.singleOrNull()?.takeIf { h.target != TRUNK && it.level == null }?.let { a ->
            val read = r.cell?.takeIf { it.available }?.let { it.tone != GroupTone.NEUTRAL }
            LampHold.shared.held(a.id, read, SystemClock.uptimeMillis())?.let { return CarDots.ofCell(true, if (it) 1f else 0f, GroupTone.NEUTRAL) }
        }
        val cell = r.cell
        if (cell == null && r.actions.isNotEmpty()) {   // vật không cảm biến (cốp/đèn đọc): lệnh cuối Kachi đã gửi
            val on = r.actions.any { ControlTileState.shared.isOn(it.id) }
            return CarDots.ofCell(true, if (on) 1f else 0f, GroupTone.NEUTRAL)
        }
        return Car3dHotspots.dot(r, cell?.let { KachiMeter.fractionOf(it.number, it.unit) })
    }

    // ── Chạm ──────────────────────────────────────────────────────────────────────────────────────────────────────

    /** Bộ phận dưới ngón tay ([Car3dPick] — chạm thẳng vào kính/đèn/cửa; đích còn chấm thì là ô 48 dp quanh chấm). */
    private fun ownerAt(x: Float, y: Float): String? {
        val c = cam ?: return null; val b = camBounds ?: return null
        val live = hotspots.mapNotNull { h -> targets[h.id] }
        return Car3dPick.pick(c, live, b, camW, camH, x, y, touch.toFloat()) { id -> resolved[id]?.let(Car3dHotspots::actionable) == true }
    }

    fun down(x: Float, y: Float) { down = ownerAt(x, y) }
    fun cancel() { down = null }

    /** Thả tay: cùng bộ phận lúc nhấn ⇒ chạm bộ phận đó. `true` = đã xử lý. */
    fun up(x: Float, y: Float): Boolean {
        val id = down; down = null
        if (id == null || ownerAt(x, y) != id) return false
        hotspots.firstOrNull { it.id == id }?.let(::press) ?: return false
        return true
    }

    private fun press(h: Car3dHotspot) {
        val r = resolved[h.id] ?: return
        val s = spots[h.id]
        if (r.actions.isEmpty()) {
            Log.i(TAG, "tap ${h.id} → chỉ xem")
            s?.takeIf { it.shown }?.let(dots::shake) ?: shakeHost()   // chấm đang hiện thì chấm lắc, không thì cả khung
            host.announceForAccessibility("${label(h, r)} · ${ctx.getString(R.string.klight_view_only)}")
            return
        }
        s?.takeIf { it.shown }?.let(dots::pulse)
        val level = live(h.id) ?: read(r)
        // 3.1.5 — chạm ĐÚP mở hẳn: cú đơn KHÔNG hoãn; cú thứ hai cùng điểm trong thời hạn chạm đúp ⇒ đích 100 % ([WindowCycle.tapTarget]).
        val now = SystemClock.uptimeMillis()
        val double = WindowCycle.isDouble(lastTap[h.id], now, doubleMs)
        if (double) lastTap.remove(h.id) else lastTap[h.id] = now
        val t = Car3dHotspots.tap(r, level, double, live(h.id))   // `:core` quyết: ô MỨC ⇒ bước kế WindowCycle / mở hẳn; không thì bấm ô đầu
        when (t) {
            is Car3dHotspots.Tap.Set -> {
                val target = WindowCycle.tapTarget(level, double, live(h.id)) ?: WindowCycle.next(level)
                Log.i(TAG, "tap ${h.id}${if (double) " (đúp)" else ""} mức=$level → ${t.action.id} ${if (t.on) "BẬT" else "TẮT"} (đích $target %)")
                if (!kit.set(t.action.id, if (t.on) 1 else 0)) tile(t.action)?.performClick()
                pending[h.id] = target to SystemClock.uptimeMillis() + PENDING_MS
            }
            is Car3dHotspots.Tap.Press -> {
                val on = s?.dot?.kind == CarDots.Kind.OPEN
                Log.i(TAG, "tap ${h.id} → ${t.action.id} ${if (on) "TẮT" else "BẬT"}")
                // Cốp: performClick ô cốp như chấm cốp bảng Kính 2D (qua cổng chạm). Đèn: đặt tường minh theo cái đang thấy.
                if (h.target == TRUNK || !kit.set(t.action.id, if (on) 0 else 1)) tile(t.action)?.performClick()
                if (h.target != TRUNK) { LampHold.shared.put(t.action.id, !on, SystemClock.uptimeMillis()); host.postDelayed({ fill(last); changed() }, LampHold.HOLD_MS + 1) }
            }
            Car3dHotspots.Tap.None -> Log.i(TAG, "tap ${h.id}${if (double) " (đúp)" else ""} → không có bước")
        }
        fill(last)
        changed()
    }

    private var last: WidgetData = WidgetData()

    /** Chủ gán: một cú chạm vừa gửi lệnh ⇒ áp lại biến thể ngay (cốp mở theo lệnh cuối — không chờ nhịp đổ số). */
    var changed: () -> Unit = {}

    private fun read(r: Car3dHotspots.Resolved): Int? = r.cell?.let { WindowCycle.levelOf(it.available, KachiMeter.fractionOf(it.number, it.unit)) }
    private fun live(id: String): Int? = pending[id]?.takeIf { it.second > SystemClock.uptimeMillis() }?.first

    private fun tile(a: GroupActionCell): View? = hidden[a.id] ?: kit.tile(a, null, icon = false).also {
        it.visibility = View.GONE; host.addView(it, FrameLayout.LayoutParams(0, 0)); hidden[a.id] = it
    }

    // ── Chiếu điểm + trợ năng ─────────────────────────────────────────────────────────────────────────────────────

    /**
     * Đặt chấm (điểm có việc) + nút trợ năng (MỌI điểm, phủ chữ nhật chạm của bộ phận) theo camera VỪA vẽ (cùng phép
     * chiếu với Filament). Phía khuất thân xe ẩn. Nhớ camera cho [ownerAt].
     */
    fun project(cam: Car3dCamera, anchors: Map<String, V3>, bounds: Car3dBounds, w: Int, h: Int) {
        this.cam = cam; camBounds = bounds; camW = w; camH = h
        for ((id, a0) in anchors) {
            val s = spots[id] ?: continue
            val a = dotAt[id] ?: a0
            val p = cam.project(a, w, h)?.let { p0 ->
                // Không để chấm nằm dưới icon ghế ([blocked]) — dời trong chữ nhật đích của chính nó, hết chỗ ⇒ ẩn.
                val rect = targets[id]?.let { Car3dPick.dotRect(cam, it, w, h, dotR) } ?: return@let p0
                Car3dPick.freeY(p0.x, p0.y, dotR, rect, blocked)?.let { p0.copy(y = it) }
            }
            // Chấm CHỈ ở điểm có việc ([Car3dHotspots.shows] = cờ nhường chạm của [Car3dPick]) — owner 14/10: cửa không chấm.
            s.shown = p != null && cam.facing(a, Car3dMath.outward(a, bounds)) && p.x in 0f..w.toFloat() &&
                p.y in 0f..h.toFloat() && resolved[id]?.let(Car3dHotspots::shows) != false
            if (p != null) { s.x = p.x; s.y = p.y }
        }
        for (id in order) {
            val v = nodes[id] ?: continue
            val t = targets[id]
            val r = t?.takeIf { Car3dPick.visible(cam, it, bounds) }?.let { Car3dPick.rect(cam, it, w, h, touch.toFloat()) }
            if (r == null || r.r < 0 || r.b < 0 || r.l > w || r.t > h) { v.visibility = View.INVISIBLE; continue }
            val l = r.l.coerceAtLeast(0f).toInt(); val tp = r.t.coerceAtLeast(0f).toInt()
            val rw = (r.r.coerceAtMost(w.toFloat()) - l).toInt().coerceAtLeast(1); val rh = (r.b.coerceAtMost(h.toFloat()) - tp).toInt().coerceAtLeast(1)
            v.visibility = View.VISIBLE
            v.translationX = l.toFloat(); v.translationY = tp.toFloat()
            (v.layoutParams as FrameLayout.LayoutParams).let { lp -> if (lp.width != rw || lp.height != rh) { lp.width = rw; lp.height = rh; v.layoutParams = lp } }
        }
        dots.invalidate()
        if (!logged) { logged = true; dump("dots (frame px ${w}x$h)") }
    }

    /** Ghi vị trí chấm MỘT lần sau khi nạp (đo trên máy ảo/xe bằng logcat), không ghi mỗi khung. */
    private var logged = false

    /** Một dòng log vị trí chấm hiện tại (gọi khi nạp xong và khi một đợt xoay dừng — không mỗi khung). */
    fun dump(head: String) = Log.i(TAG, "$head: " + order.joinToString { id ->
        // `dot` = chấm đang hiện · `part`/`part-hidden` = không chấm (chỉ xem / khuất), chạm thẳng bộ phận (thấy/khuất).
        val vis = cam?.let { c -> camBounds?.let { b -> targets[id]?.let { Car3dPick.visible(c, it, b) } } } == true
        val kind = if (spots[id]?.shown == true) "dot" else if (vis) "part" else "part-hidden"
        spots[id]?.let { "$id@${it.x.toInt()},${it.y.toInt()}:$kind" } ?: id
    })

    private fun describe() {
        hotspots.forEach { h ->
            val r = resolved[h.id] ?: return@forEach
            val say = when {
                r.actions.isEmpty() -> ctx.getString(R.string.klight_view_only)
                r.actions.any { it.level != null } -> when (val next = WindowCycle.next(live(h.id) ?: read(r))) {
                    0 -> ctx.getString(R.string.kwin_tap_close)
                    WindowCycle.FULL -> ctx.getString(R.string.kwin_tap_open_full)
                    else -> ctx.getString(R.string.kwin_tap_open_pct, next)
                }
                spots[h.id]?.dot?.kind == CarDots.Kind.OPEN -> ctx.getString(R.string.klight_tap_off)
                else -> ctx.getString(R.string.klight_tap_on)
            }
            nodes[h.id]?.contentDescription = listOfNotNull(label(h, r), r.cell?.value, say).joinToString(" · ")
        }
    }

    /** Tên vật đọc to — nhãn ô của `GroupBoard` (đã dịch ở `:core`), không có ô thì nhãn nút. */
    private fun label(h: Car3dHotspot, r: Car3dHotspots.Resolved): String {
        val cell = r.cell
        if (cell != null) return cell.label
        val m = r.actions.firstOrNull() ?: return h.id
        return m.label
    }

    private fun icon(name: String?): Drawable? = name?.let { n ->
        icons.getOrPut(n) { KachiTheme.iconRes(n).takeIf { it != 0 }?.let { ctx.getDrawable(it)?.mutate() } }
    }

    /** Nút tròn cùng dáng chân bảng Kính 2D (mực đĩa/glyph theo bảng — [KachiButtonInks]; đây là NÚT, không phải điểm trên xe). */
    private fun button(icon: Int, say: Int, onTap: () -> Unit): View = FrameLayout(ctx).apply {
        background = KachiButtonInks.background()
        addView(ImageView(ctx).apply {
            setImageResource(icon)
            imageTintList = KachiButtonInks.glyphTint()
            isDuplicateParentStateEnabled = true
        }, FrameLayout.LayoutParams(kit.px(Sp.ICON_M), kit.px(Sp.ICON_M), Gravity.CENTER))
        KachiButtonInks.adapt(this, getChildAt(0))   // R29 T29-6: đĩa/glyph theo nền ĐO dưới nút (mặc định trên = lúc chưa đo)
        contentDescription = ctx.getString(say)
        isClickable = true; isFocusable = true
        setOnClickListener { onTap() }
    }

    private companion object {
        const val TAG = "Car3d"
        const val TRUNK = "trunk"
        /** Cùng hạn đích lạc quan với bảng Kính 2D (`ActionMatrixCompact.PENDING_MS`). */
        const val PENDING_MS = 12_000L
    }
}
