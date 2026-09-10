package com.byd.clusternav.launcher

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.byd.clusternav.launcher.KachiTheme.c
import com.byd.clusternav.launcher.KachiTheme.dpi

/**
 * Thanh điều khiển — thẻ kính bo góc trên nền wall; mỗi tile render THEO [ControlKind]:
 *  • **TOGGLE** icon + nhãn, chạm bật/tắt (bật = gradient accent).
 *  • **STEP** icon + "− giá trị +".
 *  • **COVER** icon + nhãn + 2 nút Đóng/Mở (kính·nóc·rèm·cốp).
 *  • **SELECT** icon + nhãn lựa chọn hiện tại, chạm = xoay vòng ([ControlDef.args]).
 *  • **BUTTON** icon + nhãn, chạm = bắn 1 phát (momentary) + nháy sáng.
 * Hành động qua [CarControlPort] ([NoCar] off-car → no-op). Tier OVERDRIVE/DASHCAST → chấm amber "chưa kiểm".
 * **KHÔNG gate**: mọi tile bấm được bất kể tốc độ/số (owner bỏ gate 2026-09-10).
 */
class ControlDockView(context: Context) : LinearLayout(context) {

    var control: CarControlPort = NoCar
    private var config = ControlRegistry.defaultDock()
    private val on = HashMap<String, Boolean>()
    private val values = HashMap<String, Int>()
    private val selIndex = HashMap<String, Int>()   // SELECT: chỉ số lựa chọn hiện tại

    init {
        gravity = Gravity.CENTER
        ControlRegistry.ALL.forEach { on[it.id] = it.onByDefault; values[it.id] = it.value }
        background = GradientDrawable().apply {
            cornerRadius = dpi(context, 22).toFloat(); setColor(c("#d915191f")); setStroke(dpi(context, 1), c("#33ffffff"))
        }
        val p = dpi(context, 8); setPadding(p, p, p, p)
        rebuild()
    }

    fun setConfig(cfg: DockConfig) { config = cfg; rebuild() }

    private fun rebuild() {
        orientation = if (config.isVertical()) VERTICAL else HORIZONTAL
        removeAllViews()
        config.enabled.forEach { id -> ControlRegistry.byId(id)?.let { addView(makeTile(it)) } }
    }

    private fun makeTile(def: ControlDef): View {
        val content = LinearLayout(context).apply {
            orientation = VERTICAL; gravity = Gravity.CENTER
            setPadding(dpi(context, 8), dpi(context, 8), dpi(context, 8), dpi(context, 8))
        }
        val icon = ImageView(context).apply {
            val r = iconRes(def); if (r != 0) setImageResource(r)
            layoutParams = LayoutParams(dpi(context, 22), dpi(context, 22)).also { it.bottomMargin = dpi(context, 4) }
        }
        val label = TextView(context).apply {
            text = def.label; setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f); gravity = Gravity.CENTER; maxLines = 1
        }
        content.addView(icon)

        when (def.kind) {
            ControlKind.TOGGLE -> tileToggle(def, content, icon, label)
            ControlKind.STEP -> tileStep(def, content, icon, label)
            ControlKind.COVER -> tileCover(def, content, icon, label)
            ControlKind.SELECT -> tileSelect(def, content, icon, label)
            ControlKind.BUTTON -> tileButton(def, content, icon, label)
        }
        val tile = if (ControlTileLogic.needsBadge(def)) withBadge(content) else content
        val lp = LayoutParams(dpi(context, if (config.isVertical()) 100 else 84), dpi(context, if (config.isVertical()) 70 else 86))
        lp.setMargins(dpi(context, 4), dpi(context, 4), dpi(context, 4), dpi(context, 4))
        tile.layoutParams = lp
        return tile
    }

    // ── TOGGLE (bật/tắt) ────────────────────────────────────────────────────────────────────────────────
    private fun tileToggle(def: ControlDef, tile: LinearLayout, icon: ImageView, label: TextView) {
        tile.addView(label)
        val active = on[def.id] == true
        applyBg(tile, active); tint(icon, label, active)
        tile.setOnClickListener {
            val nv = !(on[def.id] ?: false); on[def.id] = nv
            applyBg(tile, nv); tint(icon, label, nv); control.toggle(def.id, nv)
        }
    }

    // ── STEP (−/+) ──────────────────────────────────────────────────────────────────────────────────────
    private fun tileStep(def: ControlDef, tile: LinearLayout, icon: ImageView, label: TextView) {
        val unit = if (def.id == "temp") "°" else ""
        applyBg(tile, false); tint(icon, label, true)
        val vtext = TextView(context).apply {
            text = "${values[def.id]}$unit"; setTextColor(Color.WHITE); setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            typeface = Typeface.DEFAULT_BOLD; setPadding(dpi(context, 8), 0, dpi(context, 8), 0)
        }
        val minus = stepBtn("−"); val plus = stepBtn("+")
        minus.setOnClickListener { val nv = def.clamp((values[def.id] ?: def.value) - def.step); values[def.id] = nv; vtext.text = "$nv$unit"; control.step(def.id, nv) }
        plus.setOnClickListener { val nv = def.clamp((values[def.id] ?: def.value) + def.step); values[def.id] = nv; vtext.text = "$nv$unit"; control.step(def.id, nv) }
        tile.addView(LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER; addView(minus); addView(vtext); addView(plus) })
    }

    // ── COVER (mở/đóng) ───────────────────────────────────────────────────────────────────────────────────
    private fun tileCover(def: ControlDef, tile: LinearLayout, icon: ImageView, label: TextView) {
        applyBg(tile, false); tint(icon, label, true)
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f); tile.addView(label)
        val closeLbl = def.args.getOrElse(0) { "Đóng" }; val openLbl = def.args.getOrElse(1) { "Mở" }
        val close = miniBtn(closeLbl) { control.cover(def.id, false) }
        val open = miniBtn(openLbl) { control.cover(def.id, true) }
        tile.addView(LinearLayout(context).apply {
            orientation = HORIZONTAL; gravity = Gravity.CENTER; setPadding(0, dpi(context, 3), 0, 0)
            addView(close, LayoutParams(WRAP, WRAP).also { it.marginEnd = dpi(context, 4) }); addView(open)
        })
    }

    // ── SELECT (xoay lựa chọn) ────────────────────────────────────────────────────────────────────────────
    private fun tileSelect(def: ControlDef, tile: LinearLayout, icon: ImageView, label: TextView) {
        applyBg(tile, false); tint(icon, label, true)
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f); tile.addView(label)
        val idx = selIndex[def.id] ?: 0
        val optView = TextView(context).apply {
            text = ControlTileLogic.selectLabel(def, idx); setTextColor(c(KachiTheme.ACCENT))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f); typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            maxLines = 1; setPadding(0, dpi(context, 2), 0, 0)
        }
        tile.addView(optView)
        tile.setOnClickListener {
            val next = ControlTileLogic.nextSelectIndex(selIndex[def.id] ?: 0, def.args.size)
            selIndex[def.id] = next; optView.text = ControlTileLogic.selectLabel(def, next); control.select(def.id, next)
        }
    }

    // ── BUTTON (bắn 1 phát) ───────────────────────────────────────────────────────────────────────────────
    private fun tileButton(def: ControlDef, tile: LinearLayout, icon: ImageView, label: TextView) {
        tile.addView(label); applyBg(tile, false); tint(icon, label, true)
        tile.setOnClickListener {
            applyBg(tile, true); tint(icon, label, true)
            control.press(def.id)
            postDelayed({ applyBg(tile, false); tint(icon, label, true) }, 220)   // nháy sáng momentary
        }
    }

    /** iconRes theo def.icon; nếu chưa map (ic-adas/ic-drive/ic-mirror…) → icon đại diện domain. */
    private fun iconRes(def: ControlDef): Int {
        val r = KachiTheme.iconRes(def.icon)
        return if (r != 0) r else KachiTheme.iconRes(WidgetCatalog.iconFor(def.domain))
    }

    private fun tint(icon: ImageView, label: TextView, active: Boolean) {
        icon.setColorFilter(c(if (active) "#ffffff" else "#aeb8c8"))
        label.setTextColor(c(if (active) "#e7ecff" else "#c3cee0"))
    }

    private fun stepBtn(s: String) = TextView(context).apply {
        text = s; setTextColor(Color.WHITE); setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f); gravity = Gravity.CENTER
        val sz = dpi(context, 22); minWidth = sz; minHeight = sz
        background = GradientDrawable().apply { cornerRadius = dpi(context, 6).toFloat(); setColor(c("#2a2f3a")) }
    }

    private fun miniBtn(s: String, onClick: () -> Unit) = TextView(context).apply {
        text = s; setTextColor(Color.WHITE); setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f); gravity = Gravity.CENTER
        setPadding(dpi(context, 8), dpi(context, 3), dpi(context, 8), dpi(context, 3)); maxLines = 1
        background = GradientDrawable().apply { cornerRadius = dpi(context, 8).toFloat(); setColor(c("#2a2f3a")) }
        setOnClickListener { onClick() }
    }

    private fun applyBg(v: View, active: Boolean) {
        v.background = if (active) KachiTheme.gradientSoft(context, 14f) else KachiTheme.card(context, 14f, "#242a34")
    }

    /** Bọc tile + chấm amber góc trên-phải cho tier "chưa kiểm trên xe" (OVERDRIVE/DASHCAST). */
    private fun withBadge(content: LinearLayout): View = FrameLayout(context).apply {
        addView(content, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        addView(View(context).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(c(KachiTheme.AMBER)) }
        }, FrameLayout.LayoutParams(dpi(context, 6), dpi(context, 6), Gravity.TOP or Gravity.END).also {
            it.topMargin = dpi(context, 6); it.marginEnd = dpi(context, 6)
        })
    }

    private companion object {
        const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    }
}
