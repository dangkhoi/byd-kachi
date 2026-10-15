package com.byd.clusternav.launcher

import android.annotation.SuppressLint
import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.byd.clusternav.R
import com.byd.clusternav.launcher.KachiTheme.c
import com.byd.clusternav.launcher.KachiTheme.dpi
import com.byd.clusternav.launcher.KachiSpace as Sp

/**
 * ═══ 3.01 R28 — hàng chân xe 3D: [nhiệt − số +] · [mở hết · hé hết · đóng hết kính] · [gió − số +] · [lấy gió trong/ngoài] ════
 *
 * Owner 13/10: *"thêm nhiệt độ bên trái 3 icon cửa, gió bên phải 3 icon cửa, thêm icon gió (hiện trong/ngoài) bên phải, nhấn chọn lấy
 * gió trong ngoài"*. Cả hàng canh giữa widget ([CenteredRow] — cụm canh theo tâm khung), nét mảnh cùng dáng ba nút kính.
 *
 * GHI không đường thứ hai: nhiệt/gió = ô STEP THẬT ẩn (`temp`/`fan` — `ControlTileFactory`, đọc mức thật · [ClimateAuto] · làn nền);
 * nút −/+ gọi [ActionTile.nudge], số hiện SOI chữ giá trị của chính ô ([ActionTile.value] — `stepText`, kể cả "AUTO"); lấy gió =
 * ô TOGGLE `recirc` ẩn (đặt tường minh [ActionTile.set]), hình theo [CapabilityIcons.forState] `ac_cycle` (cùng chip thanh trên).
 * Ô ẩn nhận số đọc ở [fill] (mã nút vào [CarDataDemand.HOSTED] ⇒ vòng poll đọc `readState` khi widget có trên màn).
 *
 * Không đủ ngang ([Car3dHero.fits], bề rộng ĐO) ⇒ ẩn HAI bộ chỉnh trước, giữ ba nút kính + lấy gió — không bao giờ đè nhau.
 *
 * 3.1.5 R29 T29-6 — [ĐO máy ảo 14/10] Sáng: chữ 1,16 · icon 1,02 (mực một hằng trên ảnh nền xe). Nay hàng là một mặt không lớp
 * riêng ([inkRegion]) ⇒ số nhiệt/gió + icon gió giải trên nền dưới CHÍNH hàng (ảnh nền xe — không cộng màu sơn: xe không vẽ
 * xuống chân); mỗi đĩa tự chọn mực theo nền dưới nó ([KachiButtonInks.adapt]).
 */
@SuppressLint("ViewConstructor")
internal class Car3dFootRow(context: Context, data: WidgetData, windows: List<View>) : ViewGroup(context) {

    private val factory = ControlTileFactory(context, control = { data.control }, size = TileSize.GROUP)
    private val hidden = ArrayList<ActionTile>()
    private val touch = dpi(context, Sp.TOUCH)
    private val gap = dpi(context, Sp.S)
    private val groupGap = dpi(context, Sp.L)
    private val value = dpi(context, Sp.TOUCH) - dpi(context, Sp.XS)   // ô số vừa "22°"/"AUTO" chữ LIGHT
    private var recircIcon: ImageView? = null
    private val steppers = ArrayList<View>()
    private val groups = ArrayList<View>()

    init {
        inkRegion("car3d-foot")   // R29 T29-6
        stepper("temp", null)?.let { steppers += it; groups += it }
        groups += LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            windows.forEachIndexed { i, b -> addView(b, LinearLayout.LayoutParams(touch, touch).also { lp -> lp.marginStart = CenteredRow.leading(i, gap) }) }
        }
        stepper("fan", null)?.let { steppers += it; groups += it }   // owner 15/10: bỏ icon quạt (lệch), nhìn số là hiểu
        recirc()?.let { groups += it }
        groups.forEach { addView(it) }
    }

    /** Ô thật ẩn (GONE, cỡ 0) — đường ghi + đọc lại của chính nút. */
    private fun tileOf(id: String): ActionTile? = ControlRegistry.byId(id)?.let { factory.actionTile(it) }?.also { t ->
        t.view.visibility = GONE; addView(t.view); hidden += t
    }

    private fun stepper(id: String, glyph: String?): View? {
        val def = ControlRegistry.byId(id) ?: return null
        val t = tileOf(id) ?: return null
        val nudge = t.nudge ?: return null
        val shown = TextView(context).apply {
            if (id == FAN) { fanShown = this; fanTile = t }
            setTextSize(TypedValue.COMPLEX_UNIT_SP, KachiWidgetType.VALUE_SP + 2f); typeface = KachiWidgetType.heroFace
            AdaptiveInk.text(this, KachiTheme.INK); gravity = Gravity.CENTER; maxLines = 1; minWidth = value   // R29 T29-6
            text = label(id, t.value?.text?.toString())
            contentDescription = def.displayLabel
        }
        // Soi chữ giá trị của ô thật (đổi lạc quan khi chạm · về số cũ khi ghi hỏng · số đọc mỗi nhịp) — một nguồn chữ.
        t.value?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, n: Int) = Unit
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, n: Int) = Unit
            override fun afterTextChanged(s: Editable?) { val v = label(id, s?.toString()); if (shown.text.toString() != v) shown.text = v }
        })
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            addView(sign("−", context.getString(R.string.kcar3d_step_down, def.displayLabel)) { Log.i(TAG, "tap $id −"); nudge(-def.step) }, LinearLayout.LayoutParams(touch, touch))
            glyph?.let { g ->
                addView(ImageView(context).apply {
                    setImageResource(KachiIcons.res(g, Sp.ICON_S)); KachiIcons.tint(this, Sp.ICON_S, false, KachiTheme.ICON)
                }, LinearLayout.LayoutParams(dpi(context, Sp.ICON_S), dpi(context, Sp.ICON_S)))
            }
            addView(shown, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, touch))
            addView(sign("+", context.getString(R.string.kcar3d_step_up, def.displayLabel)) { Log.i(TAG, "tap $id +"); nudge(def.step) }, LinearLayout.LayoutParams(touch, touch))
        }
    }

    private fun recirc(): View? {
        val def = ControlRegistry.byId(RECIRC) ?: return null
        val t = tileOf(RECIRC) ?: return null
        val img = ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER }
        recircIcon = img
        // Bọc như các nhóm khác: hàng đo con theo CAO HÀNG (EXACTLY) ⇒ đĩa đặt thẳng sẽ thành bầu dục, icon lệch khỏi vòng.
        val box = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        box.addView(disc(img, glyph = false).apply {
            contentDescription = def.displayLabel
            setOnClickListener {
                val on = !ControlTileState.shared.isOn(RECIRC)
                Log.i(TAG, "tap nút → $RECIRC ${if (on) "TRONG" else "NGOÀI"}")
                t.set?.invoke(if (on) 1 else 0) ?: t.view.performClick()
                drawRecirc()
            }
        }, LinearLayout.LayoutParams(touch, touch))
        drawRecirc()
        return box
    }

    /** Hình lấy gió theo lệnh/số đọc của ô (`recirc` 1 = gió trong) — cùng bảng hình chip `ac_cycle`. */
    private fun drawRecirc() {
        val img = recircIcon ?: return
        val inner = ControlTileState.shared.isOn(RECIRC)
        val name = CapabilityIcons.forState(CYCLE, if (inner) 1 else 0) ?: return
        val r = KachiIcons.res(name, Sp.ICON_M)
        if (r != 0 && img.tag != r) { img.setImageResource(r); img.tag = r }
        val ink = KachiButtonInks.at(img.parent as? View ?: img)   // R29: mực của CHÍNH đĩa này (đo theo nền dưới nó)
        KachiIcons.tint(img, Sp.ICON_M, inner, if (inner) ink.glyphOn else ink.glyph)
        (img.parent as? View)?.isActivated = inner
    }

    /** Đổ số xe vào các ô ẩn (đọc lại thật) rồi vẽ lại hình lấy gió. */
    fun fill(d: WidgetData) {
        hidden.forEach { it.refresh(d.car) }; drawRecirc()
        fanLevel = d.car.climate.fanLevel; fanAutoRaw = d.car.climate.acWindAutoRaw
        fanShown?.let { v -> fanTile?.value?.text?.toString()?.let { v.text = label(FAN, it) } }
    }

    /** Owner 15/10: gió AUTO ⇒ "AUTO n" như chip thanh trên ([ClimateAuto.fanText]); ô ẩn chỉ in "AUTO" (bề rộng ô thanh nút). */
    private fun label(id: String, tile: String?): String? =
        if (id == FAN && tile == ClimateAuto.AUTO) ClimateAuto.fanText(fanLevel, fanAutoRaw) ?: tile else tile
    private var fanShown: TextView? = null
    private var fanTile: ActionTile? = null
    private var fanLevel: Int? = null
    private var fanAutoRaw: Int? = null

    /**
     * Nút tròn cùng dáng chân bảng Kính 2D / ba nút kính — mực đĩa/glyph theo bảng ([KachiButtonInks]), sóng nhấn. R29: đĩa/glyph
     * theo nền ĐO dưới nút ([KachiButtonInks.adapt]); [glyph] = `false` ⇒ con tự tô (icon lấy gió — vẽ lại qua [drawRecirc]).
     */
    private fun disc(child: View, glyph: Boolean = true): FrameLayout = FrameLayout(context).apply {
        background = KachiButtonInks.background()
        addView(child, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.CENTER))
        isClickable = true; isFocusable = true; minimumWidth = touch; minimumHeight = touch
        if (glyph) KachiButtonInks.adapt(this, child) else KachiButtonInks.adapt(this, null) { drawRecirc() }
    }

    /** −/+ nét mảnh (chữ LIGHT), đĩa như nút kính. */
    private fun sign(s: String, say: String, onTap: () -> Unit): View = disc(TextView(context).apply {
        text = s; gravity = Gravity.CENTER; typeface = KachiWidgetType.heroFace
        setTextSize(TypedValue.COMPLEX_UNIT_SP, KachiWidgetType.VALUE_SP + 4f); setTextColor(KachiButtonInks.glyphTint())
        isDuplicateParentStateEnabled = true
    }).apply { contentDescription = say; setOnClickListener { onTap() } }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val avail = MeasureSpec.getSize(widthMeasureSpec)
        // Cao = cao ô chủ cấp (lưới widget có thể phóng cỡ nút qua `FitScale` — [ĐO máy ảo 13/10] nút 72 → 104 px), không ép [touch].
        val h = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED) touch else MeasureSpec.getSize(heightMeasureSpec)
        // Đo MỌI cụm (cả cụm đang GONE) rồi mới quyết; đổi `visibility` CHỈ khi khác (soát R28 P2 — không xin bố cục lại trong lượt đo).
        groups.forEach { it.measure(MeasureSpec.makeMeasureSpec(avail, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY)) }
        val fit = Car3dHero.fits(avail, groups.map { it.measuredWidth }, groupGap)
        val want = if (fit) VISIBLE else GONE
        steppers.forEach { if (it.visibility != want) it.visibility = want }
        val w = groups.filter { it.visibility != GONE }.map { it.measuredWidth }
        setMeasuredDimension(resolveSize(w.sum() + (w.size - 1).coerceAtLeast(0) * groupGap, widthMeasureSpec), h)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val visible = groups.filter { it.visibility != GONE }
        var x = CenteredRow.start(r - l, 1, visible.sumOf { it.measuredWidth } + (visible.size - 1).coerceAtLeast(0) * groupGap, 0)
        for (g in visible) { g.layout(x, 0, x + g.measuredWidth, g.measuredHeight); x += g.measuredWidth + groupGap }
    }

    private companion object {
        const val TAG = "Car3d"
        const val RECIRC = "recirc"
        const val FAN = "fan"
        const val CYCLE = "ac_cycle"
    }
}
