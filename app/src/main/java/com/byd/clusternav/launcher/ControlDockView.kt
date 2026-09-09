package com.byd.clusternav.launcher

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.byd.clusternav.launcher.KachiTheme.c
import com.byd.clusternav.launcher.KachiTheme.dpi

/**
 * Thanh điều khiển — thẻ kính bo góc trên nền wall; mỗi tile = icon + nhãn (tile bật = gradient accent),
 * stepper −/+ cho STEP. Đặt 4 viền theo [DockConfig]. Hành động qua [CarControlPort] ([NoCar] off-car).
 */
class ControlDockView(context: Context) : LinearLayout(context) {

    var control: CarControlPort = NoCar
    private var config = ControlRegistry.defaultDock()
    private val on = HashMap<String, Boolean>()
    private val values = HashMap<String, Int>()

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
        val tile = LinearLayout(context).apply {
            orientation = VERTICAL; gravity = Gravity.CENTER
            setPadding(dpi(context, 8), dpi(context, 8), dpi(context, 8), dpi(context, 8))
        }
        val lp = LayoutParams(dpi(context, if (config.isVertical()) 100 else 84), dpi(context, if (config.isVertical()) 70 else 86))
        lp.setMargins(dpi(context, 4), dpi(context, 4), dpi(context, 4), dpi(context, 4))
        tile.layoutParams = lp

        val iconResId = iconRes(def.icon)
        val icon = ImageView(context).apply {
            if (iconResId != 0) setImageResource(iconResId)
            layoutParams = LayoutParams(dpi(context, 22), dpi(context, 22)).also { it.bottomMargin = dpi(context, 4) }
        }
        val label = TextView(context).apply {
            text = def.label; setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f); gravity = Gravity.CENTER; maxLines = 1
        }
        tile.addView(icon)

        if (def.kind == ControlKind.TOGGLE) {
            tile.addView(label)
            val active = on[def.id] == true
            applyBg(tile, active); tint(icon, label, active)
            tile.setOnClickListener {
                val nv = !(on[def.id] ?: false); on[def.id] = nv
                applyBg(tile, nv); tint(icon, label, nv); control.toggle(def.id, nv)
            }
        } else {
            // STEP: KHÔNG chữ nhãn (prototype step tile chỉ icon + "− giá trị° +"); thêm ° cho nhiệt độ.
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
        return tile
    }

    private fun iconRes(icon: String): Int = KachiTheme.iconRes(icon)

    private fun tint(icon: ImageView, label: TextView, active: Boolean) {
        icon.setColorFilter(c(if (active) "#ffffff" else "#aeb8c8"))
        label.setTextColor(c(if (active) "#e7ecff" else "#c3cee0"))
    }

    private fun stepBtn(s: String) = TextView(context).apply {
        text = s; setTextColor(Color.WHITE); setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f); gravity = Gravity.CENTER
        val sz = dpi(context, 22); minWidth = sz; minHeight = sz
        background = GradientDrawable().apply { cornerRadius = dpi(context, 6).toFloat(); setColor(c("#2a2f3a")) }
    }

    private fun applyBg(v: View, active: Boolean) {
        v.background = if (active) KachiTheme.gradientSoft(context, 14f) else KachiTheme.card(context, 14f, "#242a34")
    }
}
