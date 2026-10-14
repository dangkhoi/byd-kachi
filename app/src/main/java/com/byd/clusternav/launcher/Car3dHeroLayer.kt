package com.byd.clusternav.launcher

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.byd.clusternav.launcher.KachiTheme.c
import com.byd.clusternav.launcher.KachiTheme.dpi
import com.byd.clusternav.launcher.KachiSpace as Sp

/**
 * ═══ 3.01 R28 — lớp "hero" trên hình xe 3D: icon sưởi/mát NGAY Ở GHẾ + số lốp DƯỚI TỪNG BÁNH ═══════════════════════════
 *
 * Owner 13/10: *"thêm icon sấy, sưởi ghế ở đúng vị trí ghế… nhấn vô thì chuyển sang mát 1, mát 2, tắt, xoay vòng, icon lấy y chang
 * chip trên header"* · *"[lốp] đưa luôn vào từng bánh xe, có số áp suất và nhiệt độ, ngay dưới từng bánh xe… cho option ẩn hiện"*.
 *
 *  • GHI không đường thứ hai: mỗi icon ghế bấm ô SELECT THẬT ẩn (`ControlTileFactory`, `seath`/`seatc`/`seath_r`/`seatc_r`) —
 *    cùng vòng Tắt → 1 → 2 → Tắt, cùng làn ghi nền, cùng hoàn nguyên khi hỏng. Ô ẩn được đồng bộ mức THẬT mỗi nhịp đổ số
 *    ([Car3dHero.seatControls] — datum ghế của chip thanh trên) nên vòng xoay bắt đầu từ đúng mức xe đang ở.
 *  • HÌNH: cùng nguồn icon với chip ([CapabilityIcons.forLevel] qua [KachiIcons.byLevel]: một/hai làn nhiệt, một/hai bông tuyết),
 *    bật = mực nhấn, tắt = mờ. Không chữ (tên + mức vào `contentDescription`). Đích chạm 48 dp.
 *  • LỐP: cùng nguồn với widget Lốp ([TyreBoard.readings] + định dạng đơn vị của [WidgetViews]): áp suất nét LIGHT, nhiệt nhỏ lùi
 *    màu; cảnh báo CHỈ tô màu số (hổ phách/đỏ). Không chạm được (chỉ đọc).
 *  • Vị trí: chủ ([Car3dWidgetView]) chiếu điểm thế giới theo camera VỪA vẽ ([project]); ghế/bánh phía khuất ẩn ([Car3dHero]).
 *
 * Chi phí: 8 view nhỏ + 4 ô nút ẩn dựng MỘT lần; [project] chỉ chạy khi Filament vẽ một khung (rảnh = 0); [fill] theo nhịp đổ số
 * sẵn có. Không nhịp, không luồng mới.
 *
 * 3.1.5 R29 T29-6 — mực theo NỀN ĐO ([Car3dInk]): mỗi nhãn lốp là một mặt không lớp riêng ([inkRegion]) ⇒ số giải trên ảnh nền
 * xe dưới CHÍNH nhãn; mỗi đĩa ghế là một mặt mang màu đĩa ([AdaptiveInk.fill]) ⇒ icon giải trên đĩa ∪ nền dưới đĩa. Giải lại khi
 * xoay DỪNG ([Car3dInk.settle] — so vùng, chỉ nhãn đã dời), không trong [project] (mỗi khung).
 */
@SuppressLint("ViewConstructor")
internal class Car3dHeroLayer(context: Context, data: WidgetData) : FrameLayout(context) {

    private val factory = ControlTileFactory(context, control = { data.control }, size = TileSize.GROUP)
    private val tiles = HashMap<String, ActionTile>()
    private val seatViews = LinkedHashMap<String, ImageView>()          // mã nút → nút icon (đĩa + icon)
    private val seatOf = HashMap<String, Car3dHero.Seat>()
    private val tyreViews = LinkedHashMap<TyreCorner, LinearLayout>()
    private var seats: Map<Car3dHero.Seat, V3> = emptyMap()
    private var wheels: Map<TyreCorner, V3> = emptyMap()
    private val touch = dpi(context, Sp.TOUCH) * SEAT_PCT / 100   // owner 14/10: icon ghế còn ~70 % (48 → 34 dp)
    private val icon = dpi(context, Sp.ICON_M)

    init {
        clipChildren = false
        for (s in Car3dHero.Seat.values()) for (id in listOf(s.heat, s.vent)) {
            val def = ControlRegistry.byId(id) ?: continue
            val t = factory.actionTile(def)
            t.view.visibility = GONE; addView(t.view, LayoutParams(0, 0)); tiles[id] = t
            seatOf[id] = s
            seatViews[id] = ImageView(context).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER; visibility = INVISIBLE; (touch / ICON_PAD_DIV).let { setPadding(it, it, it, it) }
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(c(KachiTheme.SCRIM_BTN)) }
                isClickable = true; isFocusable = true
                setOnClickListener { tap(id) }
                this@Car3dHeroLayer.addView(this, LayoutParams(touch, touch))
            }
        }
        for (corner in TyreCorner.values()) tyreViews[corner] = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; visibility = INVISIBLE
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES; inkRegion("car3d-tyre")   // R29 T29-6
            addView(text(KachiWidgetType.VALUE_SP + 2f, light = true)); addView(text(KachiWidgetType.LABEL_SP - 1f, light = false))
            this@Car3dHeroLayer.addView(this, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
            addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ -> under[corner]?.let { place(v, it) } }
        }
    }

    private fun text(sp: Float, light: Boolean) = TextView(context).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp); maxLines = 1; includeFontPadding = false
        if (light) typeface = KachiWidgetType.heroFace
        // Không bóng chữ (bài canh `SurfaceMaterialContractTest`: 0 blur/shadow — GPU TRINKET), không hộp nền (brief Kim).
    }

    /** Điểm thế giới của ghế/bánh (sau khi nạp mô hình). Rỗng ⇒ không vẽ phần đó. */
    fun bind(seats: Map<Car3dHero.Seat, V3>, wheels: Map<TyreCorner, V3>) { this.seats = seats; this.wheels = wheels }

    /** Nhịp đổ số: đồng bộ ô ẩn với mức THẬT, vẽ lại icon + số lốp tại chỗ. */
    fun fill(d: WidgetData) {
        val car = d.car.copy(controls = d.car.controls + Car3dHero.seatControls(d.car))
        tiles.values.forEach { it.refresh(car) }
        if (drawnNight != KachiTheme.night) { drawnNight = KachiTheme.night; drawn.clear() }   // đổi ngày/đêm ⇒ tô lại
        seatViews.keys.forEach { id -> if (drawn[id] != level(id)) draw(id) }   // chỉ vẽ lại khi mức ĐỔI (nhịp poll 1 Hz)
        val before = tyreViews.values.map { it.tag }
        val readings = TyreBoard.readings(d.car.tyres)
        val tUnit = d.units.unitFor(Quantity.TEMPERATURE)
        for (rd in readings) {
            val box = tyreViews[rd.corner] ?: continue
            val p = box.getChildAt(0) as TextView; val t = box.getChildAt(1) as TextView
            val pv = rd.pressureKpa?.let { WidgetViews.formatPressure(it, d.units) }
            if (p.text.toString() != (pv ?: "")) p.text = pv ?: ""
            AdaptiveInk.text(p, when (rd.severity) { TyreSeverity.ALERT -> KachiTheme.RED; TyreSeverity.WARN -> KachiTheme.AMBER; TyreSeverity.OK -> KachiTheme.INK; TyreSeverity.NONE -> KachiTheme.MUT })
            val tv = rd.tempC?.let { WidgetViews.formatTemp(it.toDouble(), d.units) + tUnit }
            if (t.text.toString() != (tv ?: "")) t.text = tv ?: ""
            AdaptiveInk.text(t, KachiTheme.MUT); t.visibility = if (tv == null) GONE else VISIBLE
            box.tag = pv != null   // không có áp suất ⇒ không nhãn (không bày bốn dấu "—" dưới bánh)
            box.contentDescription = listOfNotNull(rd.corner.displayLabel, pv?.let { "$it ${d.units.unitFor(Quantity.PRESSURE)}" }, tv, rd.reason).joinToString(" · ")
        }
        // Có/mất số lốp giữa hai khung vẽ (rảnh = 0 khung) ⇒ đặt lại nhãn theo camera cũ, KHÔNG xin khung Filament. [ĐO máy ảo 13/10]
        // thiếu bước này ⇒ số về sau lượt vẽ cuối thì nhãn không bao giờ hiện tới khi xoay xe.
        if (before != tyreViews.values.map { it.tag }) reproject()
    }

    private var cam: Car3dCamera? = null; private var pw = 0; private var ph = 0

    /** Đặt lại icon/nhãn theo camera của khung vẽ cuối (đổi công tắc số lốp · số lốp mới có) — không vẽ lại xe. */
    fun reproject() { cam?.let { project(it, pw, ph); (parent as? View)?.let(Car3dInk::settle) } }   // R29: đặt lại KHÔNG vẽ khung ⇒ giải lại

    /** Mức đã vẽ của từng icon ghế — nhịp đổ số chỉ vẽ lại khi khác (soát R28 P2). */
    private val drawn = HashMap<String, Int>()
    private var drawnNight: Boolean? = null

    private fun level(id: String): Int {
        val n = ControlRegistry.byId(id)?.displayArgs?.size ?: return 0
        return ControlTileState.shared.sel(id).coerceIn(0, n - 1)
    }

    private fun draw(id: String) {
        val v = seatViews[id] ?: return
        val def = ControlRegistry.byId(id) ?: return
        val lv = level(id); drawn[id] = lv
        KachiIcons.byLevel(v, def.icon, lv, Sp.ICON_M)
        // BẬT nói bằng đĩa tông nhấn + icon mực nhấn (như chip bật); TẮT = đĩa mờ + icon mực thường mờ nhẹ. [ĐO máy ảo 13/10, QA ảnh]
        // bản đầu chỉ đổi màu icon ⇒ hai trạng thái khó phân biệt trên kính tối. R29 T29-6: đĩa = MẶT (màu đĩa trên nền đo dưới nó)
        // ⇒ đặt màu đĩa + khai mặt TRƯỚC khi tô icon (nền gốc qua [AdaptiveInk.base] — lớp đỡ có thể bọc nó).
        val disc = if (lv > 0) ColorMath.withAlpha(c(KachiTheme.ACCENT), ON_DISC_ALPHA) else c(KachiTheme.SCRIM_BTN)
        (AdaptiveInk.base(v) as? GradientDrawable)?.setColor(disc)
        AdaptiveInk.fill(v, fades = false, variant = disc) { intArrayOf(disc) }
        // TẮT: glyph [KachiTheme.ON_ACCENT] (trắng) — đĩa [KachiTheme.SCRIM_BTN] ĐEN 50 % cố định (không theo chủ đề, KDoc palette);
        // [ĐO máy ảo 14/10, chủ đề SÁNG] mực `ICON` của bản sáng (79,91,109) trên đĩa (8,15,19) chỉ ~2,7:1 — icon "bạc màu" ban ngày.
        KachiIcons.tint(v, Sp.ICON_M, lv > 0, if (lv > 0) KachiTheme.ACCENT_INK else KachiTheme.ON_ACCENT)
        v.contentDescription = "${def.displayLabel} · ${def.displayArgs.getOrElse(lv) { "" }}"
    }

    /** Chạm icon ghế: bấm ô SELECT thật (bước kế từ mức đang thấy — [Car3dHero.nextSeat], cùng phép của ô). */
    private fun tap(id: String) {
        val t = tiles[id] ?: return
        android.util.Log.i(TAG, "tap ghế $id mức=${level(id)} → ${Car3dHero.nextSeat(level(id), ControlRegistry.byId(id)?.displayArgs?.size ?: 3)}")
        t.view.performClick()
        draw(id)
        // Sưởi ↔ mát cùng ghế loại trừ nhau trên xe (owner 14/10) ⇒ icon kia TẮT ngay, giữ trong ân hạn của ô (nhịp đọc chưa kịp
        // thấy xe tự tắt thì không bật lại); sau ân hạn theo số đọc thật. Không gửi lệnh tắt thêm — xe tự làm.
        Car3dHero.exclusive(id, level(id))?.takeIf { level(it) > 0 }?.let { o ->
            ControlTileState.shared.setSel(o, 0); ControlTileState.shared.touch(o); draw(o)
        }
        // Không phát lại ảnh số CŨ sau ân hạn (soát R28 P1: ô SELECT chung sẽ bị kéo về mức cũ) — nhịp đổ số kế tiếp tự đồng bộ.
    }

    /** Có nút ghế đang hiện ở toạ độ chủ [x],[y] (chủ không chặn cử chỉ đó để xoay xe). */
    fun owns(x: Float, y: Float): Boolean = seatViews.values.any { v ->
        v.visibility == VISIBLE && x >= v.translationX && x < v.translationX + v.width && y >= v.translationY && y < v.translationY + v.height
    }

    /** Đặt icon ghế + nhãn lốp theo camera VỪA vẽ (khung [w]×[h] px của TextureView, trùng gốc toạ độ lớp này). */
    fun project(cam: Car3dCamera, w: Int, h: Int) {
        this.cam = cam; pw = w; ph = h
        // Cỡ THẬT của đĩa (lưới widget có thể phóng cỡ con qua `FitScale` — [ĐO máy ảo 13/10] 72 → 104 px ⇒ dùng `touch` thì hai đĩa đè nhau).
        val disc = seatViews.values.firstOrNull()?.width?.takeIf { it > 0 } ?: touch; val gap = disc / GAP_DIV   // Pass 3: registry thiếu ghế ⇒ không ném
        val at = HashMap<Car3dHero.Seat, ScreenPoint>()
        for (s in Car3dHero.Seat.values()) {
            val p = seats[s]?.takeIf { Car3dHero.seatVisible(cam, it, s.side) }?.let { cam.project(it, w, h) }
            if (p != null && p.x in 0f..w.toFloat() && p.y in 0f..h.toFloat()) at[s] = p
        }
        // Hai ghế cùng hiện (nhìn trước/trên) ⇒ hai cụm không đè nhau ([Car3dHero.spread]).
        val d = at[Car3dHero.Seat.DRIVER]; val q = at[Car3dHero.Seat.PASSENGER]
        if (d != null && q != null && kotlin.math.abs(d.y - q.y) < disc) Car3dHero.spread(d.x, q.x, 2f * disc + 3f * gap).let { (a, b) ->
            at[Car3dHero.Seat.DRIVER] = d.copy(x = a); at[Car3dHero.Seat.PASSENGER] = q.copy(x = b)
        }
        for ((id, v) in seatViews) {
            val s = seatOf[id] ?: continue
            val p = at[s] ?: run { v.visibility = INVISIBLE; null } ?: continue
            // Cặp sưởi | mát canh giữa điểm ghế, mỗi đĩa = một đích chạm 48 dp, cách nhau một khe nhỏ.
            val dx = if (id == s.heat) -(disc + gap) / 2f else (disc + gap) / 2f
            v.translationX = (p.x + dx - disc / 2f).coerceIn(0f, (w - disc).toFloat())
            v.translationY = (p.y - disc / 2f).coerceIn(0f, (h - disc).toFloat())
            v.visibility = VISIBLE
        }
        val show = Car3dPaintChoice.tyres
        for ((corner, box) in tyreViews) {
            val p = wheels[corner]?.takeIf { show && box.tag == true && Car3dHero.tyreVisible(cam, it, corner) }?.let { cam.project(it, w, h) }
            if (p == null || p.x < 0 || p.x > w) { box.visibility = INVISIBLE; continue }
            under[corner] = floatArrayOf(p.x, p.y, w.toFloat(), h.toFloat())
            place(box, under.getValue(corner))
            box.visibility = VISIBLE
        }
    }

    /** Điểm chiếu gần nhất của từng bánh (x, y, khung w, h) — chữ đổi bề rộng giữa hai khung vẽ thì canh lại ngay ([place]). */
    private val under = HashMap<TyreCorner, FloatArray>()

    /** Nhãn canh giữa dưới điểm chạm đất của bánh, kẹp trong khung. */
    private fun place(box: View, u: FloatArray) {
        box.translationX = (u[0] - box.width / 2f).coerceIn(0f, (u[2] - box.width).coerceAtLeast(0f))
        box.translationY = (u[1] + dpi(context, Sp.XS)).coerceAtMost((u[3] - box.height).coerceAtLeast(0f))
    }

    private companion object {
        const val TAG = "Car3d"
        /** Khe giữa hai đĩa ghế = 1/8 đĩa. */
        const val GAP_DIV = 8
        const val ON_DISC_ALPHA = 0x66
        /** Icon chiếm ~1/2 đĩa (lề = 1/4 đĩa mỗi bên) — [ĐO máy ảo] icon 24 dp cố định trong đĩa phóng 104 px trông lọt thỏm. */
        const val ICON_PAD_DIV = 5

        /** Cỡ đĩa icon ghế theo % đích chạm chuẩn [Sp.TOUCH] — owner 14/10 *"bé lại tầm 70%"*. */
        const val SEAT_PCT = 70
    }
}
