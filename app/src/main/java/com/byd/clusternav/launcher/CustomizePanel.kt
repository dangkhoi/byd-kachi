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
    /** W3 — trạng thái hiện tại của ô tick "nổ máy thì tự lấy gió trong". */
    recircOnStart: Boolean = false,
    /** W3 — người dùng bật/tắt ô tick. Mặc định no-op để chỗ gọi cũ và test cũ không phải sửa. */
    private val onRecircOnStart: (Boolean) -> Unit = {},
    /** R11 — lựa chọn đơn vị hiện tại. */
    unitPrefs: UnitPrefs = UnitPrefs.DEFAULT,
    /** R11 — người dùng đổi đơn vị của một loại đại lượng. */
    private val onUnitPrefs: (UnitPrefs) -> Unit = {},
) : FrameLayout(context) {

    private val enabled = HashSet(enabledIds)
    private val tiles = HashMap<String, LinearLayout>()
    private var units = unitPrefs

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

        // Đơn vị + tiện nghi đặt LÊN ĐẦU: chúng là cài đặt chung, còn danh sách khả năng thì rất dài (195 mục) nên
        // nếu đặt sau thì phải cuộn hết mới thấy.
        body.addView(sectionLabel("Đơn vị hiển thị"))
        UnitFormat.quantitiesInUse().forEach { q ->
            body.addView(unitRow(q, units.unitFor(q)) { code ->
                units = units.with(q, code)
                onUnitPrefs(units)
            })
        }

        body.addView(sectionLabel("Tiện nghi tự động"))
        body.addView(recircRow(recircOnStart) { onRecircOnStart(it) })

        // RW0/R2: bày CẢ hai loại. Trước đây chỗ này chỉ liệt kê nút HÀNH ĐỘNG (ControlPanels), nên dù cổng
        // DockConfig.setEnabled đã nới thì người dùng vẫn KHÔNG có đường nào thêm một ô ĐỌC vào thanh.
        CapabilityCatalog.byDomain().forEach { (domain, picks) ->
            body.addView(sectionLabel(domain.label))
            addGrid(body, picks, cols = 5)
        }
        panel.addView(ScrollView(context).apply { addView(body) },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        addView(panel, plp)
    }

    private fun sectionLabel(text: String) = TextView(context).apply {
        this.text = text; setTextColor(c(KachiTheme.MUT2)); setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
        letterSpacing = 0.05f; typeface = Typeface.DEFAULT_BOLD; setPadding(0, dpi(context, 12), 0, dpi(context, 6))
    }

    // ── W3 · ô tick "nổ máy thì tự lấy gió trong" ─────────────────────────────────────────────────────
    /**
     * Đặt Ở ĐÂY (bề mặt dựng bằng CODE) chứ không nhét vào màn Cài đặt cũ: layout XML của màn đó bị **niêm phong**
     * (ràng buộc C3) — thêm một dòng vào đó là phải xin owner đóng dấu lại. Panel này dựng hoàn toàn bằng code nên
     * không đụng seal.
     */
    private fun recircRow(on: Boolean, onChange: (Boolean) -> Unit): View {
        val box = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f); typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(dpi(context, 26), dpi(context, 26))
        }
        var state = on
        fun paint() {
            box.text = if (state) "✓" else ""
            box.setTextColor(Color.WHITE)
            box.background = if (state) GradientDrawable().apply {
                cornerRadius = dpi(context, 7).toFloat(); setColor(c(KachiTheme.ACCENT))
            } else GradientDrawable().apply {
                cornerRadius = dpi(context, 7).toFloat(); setColor(c("#00000000")); setStroke(dpi(context, 2), c(KachiTheme.MUT2))
            }
        }
        paint()
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = KachiTheme.card(context, 14f, "#161b24")
            val p = dpi(context, 12); setPadding(p, p, p, p)
            addView(box)
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dpi(context, 12), 0, 0, 0)
                addView(TextView(context).apply {
                    text = "Nổ máy thì tự lấy gió trong"
                    setTextColor(c(KachiTheme.INK)); setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f)
                })
                addView(TextView(context).apply {
                    // R10 — KHÔNG hứa quá: lệnh này ở mức "đọc từ mã nguồn khác, chưa xác nhận trên xe owner".
                    text = "Xe quên chế độ này mỗi lần khởi động. ⚠ Lệnh chưa kiểm trên xe — có thể xe không nhận."
                    setTextColor(c(KachiTheme.MUT)); setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                })
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            setOnClickListener { state = !state; paint(); onChange(state) }
        }
    }

    // ── R11–R13 · chọn ĐƠN VỊ theo LOẠI đại lượng ─────────────────────────────────────────────────────
    /**
     * Một hàng cho mỗi loại đại lượng ĐANG DÙNG ([UnitFormat.quantitiesInUse] — không bày loại không có mục nào),
     * mỗi hàng là dãy lựa chọn bấm chọn. Chọn theo LOẠI (7 hàng) chứ không theo từng mục (61 mục) — R11.
     */
    private fun unitRow(q: Quantity, current: String, onPick: (String) -> Unit): View {
        val chips = HashMap<String, TextView>()
        var chosen = current
        fun paint() = chips.forEach { (code, tv) ->
            val on = code == chosen
            tv.setTextColor(if (on) Color.WHITE else c(KachiTheme.MUT))
            tv.background = if (on) KachiTheme.gradient(context, 999f)
            else KachiTheme.card(context, 999f, "#1a1f29")
        }
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dpi(context, 5), 0, dpi(context, 5))
            addView(TextView(context).apply {
                text = q.label; setTextColor(c(KachiTheme.INK)); setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            Units.options(q).forEach { opt ->
                val tv = TextView(context).apply {
                    text = opt.code; setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f); typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                    setPadding(dpi(context, 14), dpi(context, 6), dpi(context, 14), dpi(context, 6))
                    setOnClickListener { chosen = opt.code; paint(); onPick(opt.code) }
                }
                chips[opt.code] = tv
                addView(tv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    .also { it.marginStart = dpi(context, 6) })
            }
            paint()
        }
    }

    private fun addGrid(parent: LinearLayout, items: List<CapabilityPick>, cols: Int) {
        var row: LinearLayout? = null
        items.forEachIndexed { i, pick ->
            if (i % cols == 0) {
                row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
                parent.addView(row, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            }
            row!!.addView(tile(pick), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        val rem = items.size % cols
        if (rem != 0) repeat(cols - rem) { row!!.addView(View(context), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)) }
    }

    private fun tile(pick: CapabilityPick): View {
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setPadding(dpi(context, 8), dpi(context, 12), dpi(context, 8), dpi(context, 12))
            addView(ImageView(context).apply {
                val r = iconRes(pick); if (r != 0) { setImageResource(r); setColorFilter(Color.WHITE) }
                layoutParams = LinearLayout.LayoutParams(dpi(context, 34), dpi(context, 34))
            })
            addView(TextView(context).apply {
                text = pick.label; setTextColor(c(KachiTheme.INK)); setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
                gravity = Gravity.CENTER; maxLines = 2; ellipsize = TextUtils.TruncateAt.END
                setPadding(dpi(context, 2), dpi(context, 6), dpi(context, 2), 0)
            })
            setOnClickListener {
                val now = pick.id !in enabled
                if (now) enabled.add(pick.id) else enabled.remove(pick.id)
                applyState(pick.id); onToggle(pick.id, now)
            }
        }
        tiles[pick.id] = content
        applyState(pick.id)
        // badge tier "chưa kiểm" (chấm amber góc trên-phải)
        return if (pick.needsBadge) FrameLayout(context).apply {
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

    /** iconRes theo icon của khả năng; chưa map → icon đại diện nhóm (khỏi ô trống icon). */
    private fun iconRes(pick: CapabilityPick): Int {
        val r = KachiTheme.iconRes(pick.icon)
        if (r != 0) return r
        val d = pick.domain ?: return 0
        return KachiTheme.iconRes(WidgetCatalog.iconFor(d))
    }
}
