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
 * Ngăn kéo app — **hai chế độ** (cùng một view, không nhân bản UI):
 *  - [Mode.ASSIGN_SLOT] (như cũ): "Đặt widget / mở app vào ô này". **Widget**: chọn NHIỀU (1..8) → tô sáng, bấm
 *    **Đặt** để áp vào ô. **Ứng dụng**: chạm 1 app → đặt vào ô (1 app / ô).
 *  - [Mode.OPEN_APP] (gói 1 · U3): "Mở ứng dụng" — KHÔNG có mục widget, chạm app là **mở toàn màn**, không gắn vào
 *    ô nào. Có thêm hàng **Gần đây** (nguồn: [RecentApps], không cần quyền nào).
 * Bám prototype kachi-workspace.html.
 */
class AppDrawer(
    context: Context,
    private val widgets: List<WidgetDef>,
    initialWidgets: List<String>,
    private val onPickApp: (String) -> Unit,
    private val onPickWidgets: (List<String>) -> Unit,
    private val onClose: () -> Unit,
    private val mode: Mode = Mode.ASSIGN_SLOT,
    private val recentApps: List<String> = emptyList(),
) : FrameLayout(context) {

    /** Ngăn kéo dùng để GÁN VÀO Ô (như cũ) hay để MỞ APP toàn màn (U3). */
    enum class Mode { ASSIGN_SLOT, OPEN_APP }

    private val selected = ArrayList<String>().apply { addAll(initialWidgets.take(MAX)) }
    private val widgetTiles = HashMap<String, LinearLayout>()
    private var placeBtn: TextView? = null

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
        val assign = mode == Mode.ASSIGN_SLOT

        panel.addView(TextView(context).apply {
            text = if (assign) "Đặt widget hoặc mở app vào ô này" else "Mở ứng dụng"
            setTextColor(c(KachiTheme.INK)); typeface = Typeface.DEFAULT_BOLD
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
        })
        panel.addView(TextView(context).apply {
            text = if (assign) "Widget: chọn nhiều rồi bấm Đặt · App: chạm để mở ngay · chạm nền để đóng"
            else "Chạm một app để mở TOÀN MÀN · bấm HOME để về Kachi · chạm nền để đóng"
            setTextColor(c(KachiTheme.MUT)); setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(0, dpi(context, 2), 0, dpi(context, 12))
        })

        val body = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

        if (assign) {
            // ── Widget (chọn nhiều) ──
            val head = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            head.addView(sectionLabel("Widget"), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            val btn = TextView(context).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f); typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
                setPadding(dpi(context, 16), dpi(context, 7), dpi(context, 16), dpi(context, 7))
                background = KachiTheme.gradient(context, 999f); setTextColor(Color.WHITE)
                setOnClickListener { onPickWidgets(selected.toList()) }
            }
            placeBtn = btn
            head.addView(btn)
            body.addView(head)
            addWidgetGrid(body, cols = 4)
            refreshPlaceBtn()

            // ── Dữ liệu + HÀNH ĐỘNG của xe, gom theo nhóm — thêm vào ô ──
            // [SOÁT RW0 2026-09-11] Chỗ này TRƯỚC ĐÂY chỉ bày `WidgetCatalog.telemetryByDomain()` = **duy nhất mục
            // ĐỌC**. Hệ quả: `WidgetViews` VẼ được ô hành động và `ActionMacros` có 4 gói lệnh, nhưng người dùng
            // **không có nút nào** để đặt chúng vào ô giữa màn — số đo "3 gói lệnh ở ô giữa màn" của phiên trước đạt
            // được bằng cách **gieo cấu hình bằng tay**. Tức cả gói W2 đang không giao được tới người dùng.
            // Nay dùng CÙNG nguồn với bảng Tuỳ biến (`CapabilityCatalog.byDomain()`), nên hai màn chọn không thể
            // lệch nhau về việc "cái gì đặt được ở đâu".
            CapabilityCatalog.byDomain().forEach { (domain, picks) ->
                body.addView(sectionLabel(domain.label).also { it.setPadding(0, dpi(context, 12), 0, dpi(context, 4)) })
                addPickGrid(body, picks, cols = 4)
            }

            // ── App (chạm đặt vào ô) ──
            body.addView(sectionLabel("Ứng dụng").also { it.setPadding(0, dpi(context, 14), 0, dpi(context, 4)) })
            addGrid(body, loadApps(), cols = 6)
        } else {
            // ── Chế độ MỞ THƯỜNG: gần đây trước, rồi tất cả ──
            val all = loadApps()
            val recent = recentItems(all)
            if (recent.isNotEmpty()) {
                body.addView(sectionLabel("Gần đây"))
                addGrid(body, recent, cols = 6)
                body.addView(sectionLabel("Tất cả ứng dụng").also { it.setPadding(0, dpi(context, 14), 0, dpi(context, 4)) })
            }
            addGrid(body, all, cols = 6)
        }

        panel.addView(ScrollView(context).apply { addView(body) },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        addView(panel, plp)
    }

    private fun refreshPlaceBtn() {
        placeBtn?.text = if (selected.isEmpty()) "Bỏ widget" else "Đặt ${selected.size} widget"
    }

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
    private fun addPickGrid(parent: LinearLayout, picks: List<CapabilityPick>, cols: Int) {
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

    private fun pickTile(pick: CapabilityPick): View {
        val inner = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setPadding(dpi(context, 8), dpi(context, 12), dpi(context, 8), dpi(context, 12))
            addView(ImageView(context).apply {
                val r = KachiTheme.iconRes(pick.icon); if (r != 0) { setImageResource(r); setColorFilter(Color.WHITE) }
                layoutParams = LinearLayout.LayoutParams(dpi(context, 40), dpi(context, 40))
            })
            addView(TextView(context).apply {
                // Hai loại nằm cùng một lưới ⇒ PHẢI dùng displayLabel: [ĐO] 18 nhãn trùng nhau giữa ô XEM và ô BẤM
                // (vd hai ô đều ghi "Kính trước-trái"). Nhãn gốc không đổi, gợi ý chỉ thêm ở chỗ trùng.
                text = pick.displayLabel; setTextColor(c(KachiTheme.INK)); setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
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
    private class GridItem(val pkg: String, val label: String, val iconDrawable: Drawable?, val onTap: () -> Unit)

    /** Các app gần đây (theo thứ tự [recentApps]) lọc xuống những app THẬT còn cài — app đã gỡ tự rụng khỏi hàng. */
    private fun recentItems(all: List<GridItem>): List<GridItem> {
        if (recentApps.isEmpty()) return emptyList()
        val byPkg = all.associateBy { it.pkg }
        // Cùng một app xuất hiện ở CẢ "Gần đây" LẪN "Tất cả" ⇒ phải NHÂN BẢN icon. Một Drawable chỉ giữ ĐÚNG MỘT
        // callback (`setImageDrawable` gán view làm callback) và một bộ bounds/state; dùng chung cho 2 ImageView thì
        // view gắn sau chiếm callback ⇒ view trước có thể không vẽ lại / lệch trạng thái.
        return recentApps.mapNotNull { pkg ->
            byPkg[pkg]?.let { GridItem(it.pkg, it.label, copyDrawable(it.iconDrawable), it.onTap) }
        }
    }

    /** Bản sao độc lập của [d] (chia sẻ constant-state nên rẻ). Không sao chép được → dùng lại bản gốc. */
    private fun copyDrawable(d: Drawable?): Drawable? =
        d?.let { runCatching { it.constantState?.newDrawable(resources) }.getOrNull() ?: it }

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
            .map { (pkg, label, icon) -> GridItem(pkg, label, icon) { onPickApp(pkg) } }
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
