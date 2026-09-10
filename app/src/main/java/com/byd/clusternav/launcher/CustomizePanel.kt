package com.byd.clusternav.launcher

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.byd.clusternav.launcher.KachiTheme.c
import com.byd.clusternav.launcher.KachiTheme.dpi

/**
 * Màn **Tuỳ biến** thanh điều khiển — panel kính, liệt kê MỌI nút [ControlRegistry] gom theo [Domain]
 * ([ControlPanels]); mỗi domain là 1 mục có tiêu đề. ADAS (An toàn) / chế độ lái (Động lực) / HUD (Giải trí) nằm
 * trong panel RIÊNG của domain đó (KHÔNG ẩn — OQ3). Chạm 1 nút = bật/tắt nó vào thanh dock; đổi lưu bền qua
 * [onToggle] → `HomeViewModel.toggleDock` → `WorkspacePrefs` (một chiều). Tier OVERDRIVE/DASHCAST → chấm amber.
 *
 * Bám phong cách [AppDrawer] (scrim + panel + lưới kính). Không gate: mọi nút bật được.
 */
class CustomizePanel(
    context: Context,
    enabledIds: List<String>,
    private val onToggle: (String, Boolean) -> Unit,
    private val onClose: () -> Unit,
) : FrameLayout(context) {

    private val enabled = HashSet(enabledIds)
    private val tiles = HashMap<String, LinearLayout>()

    init {
        setBackgroundColor(c("#cc05070c"))
        isClickable = true
        setOnClickListener { onClose() }

        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = KachiTheme.card(context, 24f, "#12141c")
            setPadding(dpi(context, 24), dpi(context, 20), dpi(context, 24), dpi(context, 20))
            isClickable = true
        }
        val plp = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).also {
            it.setMargins(dpi(context, 36), dpi(context, 22), dpi(context, 36), dpi(context, 22)); it.gravity = Gravity.CENTER
        }

        val head = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        head.addView(TextView(context).apply {
            text = "Tuỳ biến thanh điều khiển"; setTextColor(c(KachiTheme.INK)); typeface = Typeface.DEFAULT_BOLD
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        head.addView(TextView(context).apply {
            text = "Xong"; setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f); typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE)
            gravity = Gravity.CENTER; setPadding(dpi(context, 18), dpi(context, 7), dpi(context, 18), dpi(context, 7))
            background = KachiTheme.gradient(context, 999f); setOnClickListener { onClose() }
        })
        panel.addView(head)
        panel.addView(TextView(context).apply {
            text = "Chạm 1 nút để thêm/bớt khỏi thanh. Nhóm An toàn/Động lực/Giải trí đổi hành vi lái — tự dùng tự chịu."
            setTextColor(c(KachiTheme.MUT)); setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(0, dpi(context, 2), 0, dpi(context, 12))
        })

        val body = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        ControlPanels.byDomain().forEach { (domain, controls) ->
            body.addView(sectionLabel(domain.label))
            addGrid(body, controls, cols = 5)
        }
        panel.addView(ScrollView(context).apply { addView(body) },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        addView(panel, plp)
    }

    private fun sectionLabel(text: String) = TextView(context).apply {
        this.text = text; setTextColor(c(KachiTheme.MUT2)); setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
        letterSpacing = 0.05f; typeface = Typeface.DEFAULT_BOLD; setPadding(0, dpi(context, 12), 0, dpi(context, 6))
    }

    private fun addGrid(parent: LinearLayout, items: List<ControlDef>, cols: Int) {
        var row: LinearLayout? = null
        items.forEachIndexed { i, def ->
            if (i % cols == 0) {
                row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
                parent.addView(row, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            }
            row!!.addView(tile(def), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        val rem = items.size % cols
        if (rem != 0) repeat(cols - rem) { row!!.addView(View(context), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)) }
    }

    private fun tile(def: ControlDef): View {
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setPadding(dpi(context, 8), dpi(context, 12), dpi(context, 8), dpi(context, 12))
            addView(ImageView(context).apply {
                val r = iconRes(def); if (r != 0) { setImageResource(r); setColorFilter(Color.WHITE) }
                layoutParams = LinearLayout.LayoutParams(dpi(context, 34), dpi(context, 34))
            })
            addView(TextView(context).apply {
                text = def.label; setTextColor(c(KachiTheme.INK)); setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
                gravity = Gravity.CENTER; maxLines = 2; ellipsize = TextUtils.TruncateAt.END
                setPadding(dpi(context, 2), dpi(context, 6), dpi(context, 2), 0)
            })
            setOnClickListener {
                val now = def.id !in enabled
                if (now) enabled.add(def.id) else enabled.remove(def.id)
                applyState(def.id); onToggle(def.id, now)
            }
        }
        tiles[def.id] = content
        applyState(def.id)
        // badge tier "chưa kiểm" (chấm amber góc trên-phải)
        return if (ControlTileLogic.needsBadge(def)) FrameLayout(context).apply {
            addView(content, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            addView(View(context).apply {
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(c(KachiTheme.AMBER)) }
            }, FrameLayout.LayoutParams(dpi(context, 7), dpi(context, 7), Gravity.TOP or Gravity.END).also {
                it.topMargin = dpi(context, 8); it.marginEnd = dpi(context, 8)
            })
        } else content
    }

    private fun applyState(id: String) {
        val tile = tiles[id] ?: return
        tile.background = if (id in enabled) GradientDrawable().apply {
            cornerRadius = dpi(context, 14).toFloat(); setColor(c("#264c7dff")); setStroke(dpi(context, 1), c(KachiTheme.ACCENT))
        } else KachiTheme.card(context, 14f, "#161b24")
    }

    /** iconRes theo def.icon; nếu chưa map → icon đại diện domain (khỏi tile trống icon). */
    private fun iconRes(def: ControlDef): Int {
        val r = KachiTheme.iconRes(def.icon)
        return if (r != 0) r else KachiTheme.iconRes(WidgetCatalog.iconFor(def.domain))
    }
}
