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
import android.widget.Toast
import com.byd.clusternav.launcher.KachiTheme.c
import com.byd.clusternav.launcher.KachiTheme.dpi
import com.byd.clusternav.launcher.KachiSpace as Sp

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

    /** Câu nhắc trần ô ở thanh đáy — rỗng khi chưa đầy (đủ thì im lặng). */
    private var capHint: TextView? = null

    init {
        setBackgroundColor(c("#cc05070c"))
        isClickable = true
        setOnClickListener { onClose() }

        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = KachiTheme.card(context, Sp.RADIUS_XXL, "#12141c")
            setPadding(dpi(context, Sp.XXL), dpi(context, Sp.XL), dpi(context, Sp.XXL), dpi(context, Sp.XL))
            isClickable = true
        }
        val plp = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).also {
            it.setMargins(dpi(context, Sp.XXL), dpi(context, Sp.XXL), dpi(context, Sp.XXL), dpi(context, Sp.XXL)); it.gravity = Gravity.CENTER
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
            setPadding(0, dpi(context, Sp.XS), 0, dpi(context, Sp.M))
        })

        val body = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

        if (assign) {
            // ── NHÓM — thứ người dùng gặp TRƯỚC (G1 · T4 · §4.2) ──
            body.addView(sectionLabel(CapabilityPicker.GROUPS_TITLE))
            body.addView(note(CapabilityPicker.GROUPS_NOTE))
            // 3 cột (không 4): ô nhóm có thêm DÒNG PHỤ nói nó gồm gì, cần chỗ cho chữ. Số cột do tôi chọn.
            addPickGrid(body, CapabilityPicker.groupPicks(), cols = 3)

            // ── Widget dựng tay (chọn nhiều) ──
            body.addView(sectionLabel("Thẻ dựng tay").also { it.setPadding(0, dpi(context, Sp.M), 0, dpi(context, Sp.XS)) })
            addWidgetGrid(body, cols = 4)

            // ── Dữ liệu + HÀNH ĐỘNG của xe, gom theo lĩnh vực — thêm vào ô ──
            // [SOÁT RW0 2026-09-11] Chỗ này TRƯỚC ĐÂY chỉ bày `WidgetCatalog.telemetryByDomain()` = **duy nhất mục
            // ĐỌC**. Hệ quả: `WidgetViews` VẼ được ô hành động và `ActionMacros` có 4 gói lệnh, nhưng người dùng
            // **không có nút nào** để đặt chúng vào ô giữa màn — số đo "3 gói lệnh ở ô giữa màn" của phiên trước đạt
            // được bằng cách **gieo cấu hình bằng tay**. Tức cả gói W2 đang không giao được tới người dùng.
            // Nay dùng CÙNG nguồn với màn Cài đặt (`CapabilityCatalog.byDomain()`), nên hai màn chọn không thể
            // lệch nhau về việc "cái gì đặt được ở đâu".
            body.addView(sectionLabel(CapabilityPicker.SINGLES_TITLE).also { it.setPadding(0, dpi(context, Sp.L), 0, dpi(context, Sp.XS)) })
            CapabilityCatalog.byDomain().forEach { (domain, picks) ->
                // `singlesOf` BẮT BUỘC: nhóm đã bày ở mục đầu, để nó nằm trong lĩnh vực nữa là **hai ô cùng một mã**
                // ⇒ `widgetTiles[id]` bị ghi đè ⇒ chỉ ô sau được tô sáng (đúng lỗi RW0 đã ghi).
                body.addView(sectionLabel(domain.label).also { it.setPadding(0, dpi(context, Sp.M), 0, dpi(context, Sp.XS)) })
                CapabilityPicker.groupHint(picks).takeIf { it.isNotEmpty() }?.let { body.addView(note(it)) }
                addPickGrid(body, CapabilityPicker.singlesOf(picks), cols = 4)
            }

            // ── App (chạm đặt vào ô) ──
            body.addView(sectionLabel("Ứng dụng").also { it.setPadding(0, dpi(context, Sp.L), 0, dpi(context, Sp.XS)) })
            addGrid(body, loadApps(), cols = 6)
        } else {
            // ── Chế độ MỞ THƯỜNG: gần đây trước, rồi tất cả ──
            val all = loadApps()
            val recent = recentItems(all)
            if (recent.isNotEmpty()) {
                body.addView(sectionLabel("Gần đây"))
                addGrid(body, recent, cols = 6)
                body.addView(sectionLabel("Tất cả ứng dụng").also { it.setPadding(0, dpi(context, Sp.L), 0, dpi(context, Sp.XS)) })
            }
            addGrid(body, all, cols = 6)
        }

        panel.addView(
            ScrollView(context).apply {
                addView(body)
                // ⚠ [KIỂM TOÁN UX mục 5c] Mép cuộn trước đây CẮT NGANG chữ: [ĐO] 3 nhãn chỉ còn ~40% nét ở đường
                // biên, đọc thành chữ lỗi chứ không đọc thành "còn nữa, cuộn đi". Mép mờ nói đúng điều đó, và là
                // cách nền tảng có sẵn (không phải một lớp phủ tự vẽ phải tự nhớ đổi màu theo nền).
                isVerticalFadingEdgeEnabled = true
                setFadingEdgeLength(dpi(context, Sp.XL))
                // Đệm trên/dưới + KHÔNG cắt theo đệm ⇒ hàng đầu và hàng cuối không dính vào biên vùng cuộn.
                clipToPadding = false
                setPadding(0, dpi(context, Sp.XS), 0, dpi(context, Sp.S))
            },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f),
        )
        // ⚠⚠ [KIỂM TOÁN UX mục 5a] Nút áp cấu hình GHIM Ở ĐÁY BẢNG, **ngoài** vùng cuộn.
        //
        // [ĐO] trước đây nó nằm trong thân cuộn (cạnh tiêu đề mục đầu), nên cuộn xuống là **mất nút**: điểm sáng ở
        // vùng nút đi 7242 → 83 → 0. Người dùng chọn xong ở cuối danh sách thì không còn đường áp — phải cuộn ngược
        // lên mới thấy, mà không có gì nói cho họ biết điều đó. Nút quyết định phải luôn ở trong tầm mắt.
        if (assign) {
            panel.addView(placeBar(), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            refreshPlaceBtn()
        }
        addView(panel, plp)
    }

    /** Thanh đáy ghim: câu nhắc trần ô (bên trái) + nút áp (bên phải). */
    private fun placeBar(): View = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        setPadding(0, dpi(context, Sp.M), 0, 0)
        val hint = TextView(context).apply {
            setTextColor(c(KachiTheme.MUT)); setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
        }
        capHint = hint
        addView(hint, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val btn = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f); typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            setPadding(dpi(context, Sp.L), dpi(context, Sp.S), dpi(context, Sp.L), dpi(context, Sp.S))
            background = KachiTheme.gradient(context, Sp.RADIUS_PILL); setTextColor(Color.WHITE)
            setOnClickListener { onPickWidgets(selected.toList()) }
        }
        placeBtn = btn
        addView(btn)
    }

    private fun refreshPlaceBtn() {
        placeBtn?.text = if (selected.isEmpty()) "Bỏ widget" else "Đặt ${selected.size} widget"
        // Câu nhắc chỉ hiện KHI ĐẦY (đủ thì im lặng — cùng luật với vòng kiểm quyền). Nói cả trần LẪN cách đi tiếp,
        // vì "đã đủ 8" một mình không cho người dùng biết phải làm gì.
        capHint?.text = if (selected.size >= MAX) CAP_NOTE else ""
    }

    /**
     * ĐƯỜNG DUY NHẤT bật/tắt một lựa chọn — cho cả widget dựng tay lẫn mục khả năng.
     *
     * ## ⚠⚠ [KIỂM TOÁN UX mục 5b] Trần 8 mục trước đây CHẶN IM LẶNG
     * Bản cũ viết `else if (selected.size < MAX) selected.add(id)` ở **hai** chỗ (widget và mục khả năng). [ĐO] đang
     * chọn 8 mục rồi bấm thêm Lốp/Kính/Khí hậu: vẫn 8, **không một lời nào** — không toast, không đổi màu, không câu
     * nhắc; bỏ một mục xuống 7 thì lại bấm được. Người dùng không thể biết vì sao cú bấm của họ "mất".
     *
     * Đây đúng họ lỗi mà dự án đã trả giá ở `DockConfig.setEnabled` (*"mã không phải nút ⇒ return this"*, bỏ qua im
     * lặng), nên cách chữa cũng phải giống: **nói ra**, và nói cả đường đi tiếp. Có bài canh
     * `PickerCapNoticeContractTest` cấm nhánh bỏ-qua-im-lặng mọc lại.
     *
     * Gộp về một hàm cũng là để hai chỗ không thể lệch nhau — hai bản sao của cùng một luật là cách chắc chắn để
     * một bản được sửa và bản kia không.
     */
    private fun toggleSelection(id: String) {
        if (id in selected) {
            selected.remove(id)
        } else if (selected.size >= MAX) {
            Toast.makeText(context, CAP_NOTE, Toast.LENGTH_SHORT).show()
            return
        } else {
            selected.add(id)
        }
        refreshTiles(); refreshPlaceBtn()
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
            setPadding(dpi(context, Sp.S), dpi(context, Sp.M), dpi(context, Sp.S), dpi(context, Sp.M))
            addView(ImageView(context).apply {
                val r = KachiTheme.iconRes(def.icon); if (r != 0) { setImageResource(r); setColorFilter(Color.WHITE) }
                layoutParams = LinearLayout.LayoutParams(dpi(context, Sp.ICON_XL), dpi(context, Sp.ICON_XL))
            })
            addView(TextView(context).apply {
                text = def.label; setTextColor(c(KachiTheme.INK)); setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                gravity = Gravity.CENTER; maxLines = 1; ellipsize = TextUtils.TruncateAt.END
                setPadding(dpi(context, Sp.XS), dpi(context, Sp.S), dpi(context, Sp.XS), 0)
            })
            setOnClickListener { toggleSelection(def.id) }
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
            setPadding(dpi(context, Sp.S), dpi(context, Sp.M), dpi(context, Sp.S), dpi(context, Sp.M))
            addView(iconWithBadge(KachiTheme.iconRes(pick.icon), pick.needsBadge))
            addView(TextView(context).apply {
                // Hai loại nằm cùng một lưới ⇒ PHẢI dùng displayLabel: [ĐO] 18 nhãn trùng nhau giữa ô XEM và ô BẤM
                // (vd hai ô đều ghi "Kính trước-trái"). Nhãn gốc không đổi, gợi ý chỉ thêm ở chỗ trùng.
                text = pick.displayLabel; setTextColor(c(KachiTheme.INK)); setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
                gravity = Gravity.CENTER; maxLines = 2; ellipsize = TextUtils.TruncateAt.END
                setPadding(dpi(context, Sp.XS), dpi(context, Sp.S), dpi(context, Sp.XS), 0)
            })
            // Dòng phụ nói ô này GỒM GÌ — chỉ NHÓM có (mục rời để rỗng, nhãn của chúng đã tự nói hết). Không có dòng
            // này thì người dùng thấy ô "Lốp" mà vẫn phải đoán bên trong có gì.
            // ⚠ T5 (thang khoảng cách/cỡ chữ): 10sp là số TÔI TỰ CHỌN — nhãn ở trên là 11.5sp, dòng phụ phải nhỏ hơn
            // để đọc ra thứ bậc. Mọi dpi() ở đây là số ĐÃ dùng sẵn trong chính ô này, không thêm số mới.
            if (pick.sub.isNotEmpty()) addView(TextView(context).apply {
                text = pick.sub; setTextColor(c(KachiTheme.MUT)); setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                gravity = Gravity.CENTER; maxLines = 2; ellipsize = TextUtils.TruncateAt.END
                setPadding(dpi(context, Sp.XS), dpi(context, Sp.XS), dpi(context, Sp.XS), 0)
            })
            setOnClickListener { toggleSelection(pick.id) }
        }
        widgetTiles[pick.id] = inner
        applyTileState(pick.id)
        return inner
    }

    /**
     * Icon của một ô chọn, kèm chấm "chưa kiểm trên xe" **DÁN VÀO GÓC ICON**.
     *
     * ## ⚠ [KIỂM TOÁN UX mục 6] Chấm trước đây thả nổi ở góc THẺ
     * Ô nhóm rộng ~583px mà icon chỉ 66px và nằm giữa ô, nên chấm ở góc trên-phải thẻ cách icon **~268px** — và ô
     * chưa chọn thì **không có nền thẻ** để cái góc đó thuộc về, nên chấm trông như một hạt bụi trên màn. Dán vào
     * góc icon thì khoảng cách còn 0 và mắt ghép ngay được "dấu này nói về mục này".
     */
    private fun iconWithBadge(res: Int, needsBadge: Boolean): View {
        val size = dpi(context, Sp.ICON_XL)
        val img = ImageView(context).apply { if (res != 0) { setImageResource(res); setColorFilter(Color.WHITE) } }
        if (!needsBadge) return img.apply { layoutParams = LinearLayout.LayoutParams(size, size) }
        return FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(size, size)
            addView(img, FrameLayout.LayoutParams(size, size))
            addView(View(context).apply {
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(c(KachiTheme.AMBER)) }
            }, FrameLayout.LayoutParams(dpi(context, Sp.DOT), dpi(context, Sp.DOT), Gravity.TOP or Gravity.END))
        }
    }

    private fun applyTileState(id: String) {
        val tile = widgetTiles[id] ?: return
        val on = id in selected
        tile.background = if (on) GradientDrawable().apply {
            cornerRadius = dpi(context, Sp.RADIUS_L).toFloat()
            setColor(c("#264c7dff")); setStroke(dpi(context, Sp.HAIRLINE), c(KachiTheme.ACCENT))   // tô nền accent mờ + viền accent
        } else null
        // [KIỂM TOÁN UX mục 5b] Đầy trần ⇒ LÀM MỜ những ô không còn chọn được, để trạng thái "không bấm được nữa"
        // nhìn ra được TRƯỚC khi bấm; toast chỉ là lớp thứ hai cho người đã bấm.
        tile.alpha = if (on || selected.size < MAX) 1f else DIMMED
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
        letterSpacing = 0.06f; setPadding(0, 0, 0, dpi(context, Sp.S))
    }

    /** Câu phụ dưới tiêu đề mục — cùng khuôn với câu mô tả ở đầu bảng, không phải cỡ chữ mới. */
    private fun note(text: String) = TextView(context).apply {
        this.text = text; setTextColor(c(KachiTheme.MUT)); setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
        setPadding(0, 0, 0, dpi(context, Sp.S))
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
            setPadding(dpi(context, Sp.S), dpi(context, Sp.M), dpi(context, Sp.S), dpi(context, Sp.M))
            setOnClickListener { item.onTap() }
            addView(ImageView(context).apply {
                if (item.iconDrawable != null) setImageDrawable(item.iconDrawable)
                layoutParams = LinearLayout.LayoutParams(dpi(context, Sp.ICON_XL), dpi(context, Sp.ICON_XL))
            })
            addView(TextView(context).apply {
                text = item.label; setTextColor(c(KachiTheme.INK)); setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                gravity = Gravity.CENTER; maxLines = 1; ellipsize = TextUtils.TruncateAt.END
                setPadding(dpi(context, Sp.XS), dpi(context, Sp.S), dpi(context, Sp.XS), 0)
            })
        }

    private companion object {
        const val MAX = 8

        /**
         * Câu nói khi đã đủ trần — nêu **cả trần lẫn đường đi tiếp**.
         *
         * Một chỗ duy nhất vì nó xuất hiện ở HAI nơi (câu nhắc ở thanh đáy + toast khi bấm): hai bản chữ sẽ lệch
         * nhau đúng lúc ai đó sửa một chỗ, và lúc đó hai bề mặt nói hai điều về cùng một luật.
         */
        const val CAP_NOTE = "Ô chứa tối đa $MAX mục — bỏ một mục để thêm"

        /** Độ mờ của ô KHÔNG còn chọn được (đã đủ trần). */
        const val DIMMED = 0.4f
    }
}
