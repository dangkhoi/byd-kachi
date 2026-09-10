package com.byd.clusternav.launcher

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
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
 * Ngăn kéo "Đặt widget / mở app vào ô này" — panel kính, 2 mục:
 *  - **Widget**: chọn NHIỀU (1..8) → tô sáng; bấm **Đặt** để áp vào ô (Kachi xếp lưới theo số lượng: 1 to giữa, 2/3 một hàng, 4..8 hai hàng).
 *  - **Ứng dụng**: chạm 1 app → mở ngay (1 app / ô).
 * Bám prototype kachi-workspace.html.
 */
class AppDrawer(
    context: Context,
    private val widgets: List<WidgetDef>,
    initialWidgets: List<String>,
    private val onPickApp: (String) -> Unit,
    private val onPickWidgets: (List<String>) -> Unit,
    private val onClose: () -> Unit,
) : FrameLayout(context) {

    private val selected = ArrayList<String>().apply { addAll(initialWidgets.take(MAX)) }
    private val widgetTiles = HashMap<String, LinearLayout>()
    private lateinit var placeBtn: TextView

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
            it.setMargins(dpi(context, 36), dpi(context, 26), dpi(context, 36), dpi(context, 26)); it.gravity = Gravity.CENTER
        }

        panel.addView(TextView(context).apply {
            text = "Đặt widget hoặc mở app vào ô này"; setTextColor(c(KachiTheme.INK)); typeface = Typeface.DEFAULT_BOLD
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
        })
        panel.addView(TextView(context).apply {
            text = "Widget: chọn nhiều rồi bấm Đặt · App: chạm để mở ngay · chạm nền để đóng"
            setTextColor(c(KachiTheme.MUT)); setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(0, dpi(context, 2), 0, dpi(context, 12))
        })

        val body = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

        // ── Widget (chọn nhiều) ──
        val head = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        head.addView(sectionLabel("Widget"), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        placeBtn = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f); typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            setPadding(dpi(context, 16), dpi(context, 7), dpi(context, 16), dpi(context, 7))
            background = KachiTheme.gradient(context, 999f); setTextColor(Color.WHITE)
            setOnClickListener { onPickWidgets(selected.toList()) }
        }
        head.addView(placeBtn)
        body.addView(head)
        addWidgetGrid(body, cols = 4)
        refreshPlaceBtn()

        // ── Widget dữ liệu xe (telemetry, registry-driven) gom theo domain — thêm vào ô ──
        WidgetCatalog.telemetryByDomain().forEach { (domain, picks) ->
            body.addView(sectionLabel(domain.label).also { it.setPadding(0, dpi(context, 12), 0, dpi(context, 4)) })
            addPickGrid(body, picks, cols = 4)
        }

        // ── App (chạm mở ngay) ──
        body.addView(sectionLabel("Ứng dụng").also { it.setPadding(0, dpi(context, 14), 0, dpi(context, 4)) })
        addGrid(body, loadApps(), cols = 6)

        panel.addView(ScrollView(context).apply { addView(body) },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        addView(panel, plp)
    }

    private fun refreshPlaceBtn() { placeBtn.text = if (selected.isEmpty()) "Bỏ widget" else "Đặt ${selected.size} widget" }

    // ── Widget grid (toggle) ──
    private fun addWidgetGrid(parent: LinearLayout, cols: Int) {
        var row: LinearLayout? = null
        widgets.forEachIndexed { i, def ->
            if (i % cols == 0) {
                row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
                parent.addView(row, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            }
            row!!.addView(widgetTile(def), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        val rem = widgets.size % cols
        if (rem != 0) repeat(cols - rem) { row!!.addView(View(context), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)) }
    }

    private fun widgetTile(def: WidgetDef): View {
        val tile = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setPadding(dpi(context, 8), dpi(context, 12), dpi(context, 8), dpi(context, 12))
            addView(ImageView(context).apply {
                val r = KachiTheme.iconRes(def.icon); if (r != 0) { setImageResource(r); setColorFilter(Color.WHITE) }
                layoutParams = LinearLayout.LayoutParams(dpi(context, 44), dpi(context, 44))
            })
            addView(TextView(context).apply {
                text = def.label; setTextColor(c(KachiTheme.INK)); setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                gravity = Gravity.CENTER; maxLines = 1; ellipsize = TextUtils.TruncateAt.END
                setPadding(dpi(context, 2), dpi(context, 7), dpi(context, 2), 0)
            })
            setOnClickListener {
                if (def.id in selected) selected.remove(def.id)
                else if (selected.size < MAX) selected.add(def.id)
                refreshTiles(); refreshPlaceBtn()
            }
        }
        widgetTiles[def.id] = tile
        applyTileState(def.id)
        return tile
    }

    private fun refreshTiles() { widgetTiles.keys.forEach { applyTileState(it) } }

    // ── Telemetry pick grid (registry-driven; cùng cơ chế chọn với curated) ──
    private fun addPickGrid(parent: LinearLayout, picks: List<WidgetPick>, cols: Int) {
        var row: LinearLayout? = null
        picks.forEachIndexed { i, pick ->
            if (i % cols == 0) {
                row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
                parent.addView(row, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            }
            row!!.addView(pickTile(pick), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        val rem = picks.size % cols
        if (rem != 0) repeat(cols - rem) { row!!.addView(View(context), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)) }
    }

    private fun pickTile(pick: WidgetPick): View {
        val inner = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setPadding(dpi(context, 8), dpi(context, 12), dpi(context, 8), dpi(context, 12))
            addView(ImageView(context).apply {
                val r = KachiTheme.iconRes(pick.icon); if (r != 0) { setImageResource(r); setColorFilter(Color.WHITE) }
                layoutParams = LinearLayout.LayoutParams(dpi(context, 40), dpi(context, 40))
            })
            addView(TextView(context).apply {
                text = pick.label; setTextColor(c(KachiTheme.INK)); setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
                gravity = Gravity.CENTER; maxLines = 2; ellipsize = TextUtils.TruncateAt.END
                setPadding(dpi(context, 2), dpi(context, 6), dpi(context, 2), 0)
            })
            setOnClickListener {
                if (pick.id in selected) selected.remove(pick.id)
                else if (selected.size < MAX) selected.add(pick.id)
                refreshTiles(); refreshPlaceBtn()
            }
        }
        widgetTiles[pick.id] = inner
        applyTileState(pick.id)
        return if (pick.needsBadge) FrameLayout(context).apply {
            addView(inner, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            addView(View(context).apply {
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(c(KachiTheme.AMBER)) }
            }, FrameLayout.LayoutParams(dpi(context, 7), dpi(context, 7), Gravity.TOP or Gravity.END).also {
                it.topMargin = dpi(context, 6); it.marginEnd = dpi(context, 6)
            })
        } else inner
    }

    private fun applyTileState(id: String) {
        val tile = widgetTiles[id] ?: return
        tile.background = if (id in selected) GradientDrawable().apply {
            cornerRadius = dpi(context, 14).toFloat()
            setColor(c("#264c7dff")); setStroke(dpi(context, 1), c(KachiTheme.ACCENT))   // tô nền accent mờ + viền accent
        } else null
    }

    // ── App grid (single pick) ──
    private class GridItem(val label: String, val iconDrawable: Drawable?, val onTap: () -> Unit)

    private fun sectionLabel(text: String) = TextView(context).apply {
        this.text = text; setTextColor(c(KachiTheme.MUT2)); setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        letterSpacing = 0.06f; setPadding(0, 0, 0, dpi(context, 6))
    }

    private fun loadApps(): List<GridItem> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .mapNotNull { ri ->
                val pkg = ri.activityInfo?.packageName ?: return@mapNotNull null
                Triple(pkg, ri.loadLabel(pm).toString(), ri.loadIcon(pm))
            }
            .distinctBy { it.first }
            .sortedBy { it.second.lowercase() }
            .map { (pkg, label, icon) -> GridItem(label, icon) { onPickApp(pkg) } }
    }

    private fun addGrid(parent: LinearLayout, items: List<GridItem>, cols: Int) {
        var row: LinearLayout? = null
        items.forEachIndexed { i, item ->
            if (i % cols == 0) {
                row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
                parent.addView(row, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            }
            row!!.addView(tileView(item), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        val rem = items.size % cols
        if (rem != 0) repeat(cols - rem) { row!!.addView(View(context), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)) }
    }

    private fun tileView(item: GridItem): View =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setPadding(dpi(context, 8), dpi(context, 12), dpi(context, 8), dpi(context, 12))
            setOnClickListener { item.onTap() }
            addView(ImageView(context).apply {
                if (item.iconDrawable != null) setImageDrawable(item.iconDrawable)
                layoutParams = LinearLayout.LayoutParams(dpi(context, 44), dpi(context, 44))
            })
            addView(TextView(context).apply {
                text = item.label; setTextColor(c(KachiTheme.INK)); setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                gravity = Gravity.CENTER; maxLines = 1; ellipsize = TextUtils.TruncateAt.END
                setPadding(dpi(context, 2), dpi(context, 7), dpi(context, 2), 0)
            })
        }

    private companion object { const val MAX = 8 }
}
