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
import android.widget.Toast
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
    /** P8 — báo cáo vòng kiểm quyền. `null` = chưa kiểm ⇒ không hiện mục nào. */
    permissions: PermissionReport? = null,
    /** U4 — lựa chọn hình nền hiện tại. */
    wallpaper: WallpaperPrefs = WallpaperPrefs.DEFAULT,
    /** U4 — người dùng đổi lựa chọn hình nền. */
    private val onWallpaper: (WallpaperPrefs) -> Unit = {},
    /** U4 — chỗ bỏ ảnh vào, để nói cho người dùng biết (họ không có cách nào tự đoán). */
    private val wallpaperFolderHint: String = "",
    /** RW0 — cấu hình chip thanh trên hiện tại. */
    topStrip: TopStripConfig = TopStripConfig.DEFAULT,
    /** RW0 — người dùng bật/tắt một chip. Mặc định no-op ⇒ chỗ gọi cũ và test cũ không phải sửa. */
    private val onTopStrip: (String, Boolean) -> Unit = { _, _ -> },
    /** P9 — mở bảng vẽ bố cục. `null` = không hiện mục đó (chỗ gọi cũ và test cũ không phải sửa). */
    private val onOpenLayoutEditor: (() -> Unit)? = null,
    /** P9 — mô tả bố cục đang dùng, để nói cho người dùng biết họ đang ở đâu. */
    private val layoutSummary: String = "",
) : FrameLayout(context) {

    private val enabled = HashSet(enabledIds)
    private val tiles = HashMap<String, LinearLayout>()
    private var units = unitPrefs
    /** RW0 vùng thứ ba — bộ chọn chip thanh trên, tách riêng (xem [TopStripPicker]). */
    private val stripPicker = TopStripPicker(context, topStrip) { id, on -> onTopStrip(id, on) }

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

        layoutSection(body)

        body.addView(sectionLabel("Hình nền"))
        var wp = wallpaper
        body.addView(checkRow(
            on = wp.enabled,
            title = "Dùng ảnh làm hình nền",
            sub = if (wallpaperFolderHint.isEmpty()) "Bỏ ảnh vào thư mục ảnh của Kachi"
            else "Bỏ ảnh vào: $wallpaperFolderHint",
        ) { on -> wp = wp.copy(enabled = on); onWallpaper(wp) })
        body.addView(chipRow("Đổi ảnh mỗi", Slideshow.INTERVAL_CHOICES_SEC.map { it.toString() to Slideshow.intervalLabel(it) },
            wp.intervalSec.toString()) { code ->
            wp = wp.copy(intervalSec = code.toIntOrNull() ?: Slideshow.DEFAULT_INTERVAL_SEC); onWallpaper(wp)
        })
        body.addView(chipRow("Cách phủ", ImageFit.values().map { it.name to it.label }, wp.fit.name) { code ->
            wp = wp.copy(fit = ImageFit.values().firstOrNull { it.name == code } ?: ImageFit.FILL); onWallpaper(wp)
        })
        body.addView(chipRow("Làm tối ảnh", listOf(0, 25, 45, 65).map { it.toString() to "$it%" },
            wp.dim.toString()) { code ->
            wp = wp.copy(dimPercent = code.toIntOrNull() ?: WallpaperPrefs.DEFAULT_DIM_PERCENT); onWallpaper(wp)
        })

        body.addView(sectionLabel("Tiện nghi tự động"))
        body.addView(recircRow(recircOnStart) { onRecircOnStart(it) })

        // P8: chỉ hiện mục này KHI có thứ thiếu. Đủ thì im lặng — không ai muốn đọc danh sách những thứ đang chạy tốt.
        permissions?.takeIf { !it.allOk }?.let { rep ->
            body.addView(sectionLabel("Quyền còn thiếu"))
            rep.missing.forEach { body.addView(permissionRow(it, rep)) }
        }

        stripPicker.section(body)

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

    /** P9 — mục mở bảng vẽ bố cục. Chỉ hiện khi chỗ gọi cấp đường mở ⇒ chỗ gọi cũ và test cũ không phải sửa. */
    private fun layoutSection(parent: LinearLayout) {
        val open = onOpenLayoutEditor ?: return
        parent.addView(sectionLabel("Bố cục màn hình"))
        parent.addView(TextView(context).apply {
            text = layoutSummary.ifEmpty { "Đang dùng bố cục sẵn chọn ở thanh trên." }
            setTextColor(Color.parseColor(KachiTheme.MUT))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            setPadding(0, 0, 0, px(8))
        })
        parent.addView(TextView(context).apply {
            text = "Vẽ bố cục riêng…"
            setTextColor(Color.parseColor(KachiTheme.INK))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(px(14), px(9), px(14), px(9))
            background = GradientDrawable().apply {
                cornerRadius = px(20).toFloat()
                setColor(Color.parseColor(KachiTheme.CARD2))
                setStroke(px(1), Color.parseColor(KachiTheme.LINE))
            }
            setOnClickListener { open() }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private fun px(v: Int): Int = (v * resources.displayMetrics.density).toInt()

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
    /**
     * W3 — ô tick "nổ máy thì tự lấy gió trong". Dùng [checkRow] dùng chung (trước U4 hàng này dựng tay riêng; nay
     * có hai chỗ cần cùng một kiểu hàng nên rút ra một chỗ — luật không-lặp-code).
     *
     * Đặt ở bề mặt dựng bằng CODE, không nhét vào màn Cài đặt cũ: layout XML của màn đó bị niêm phong.
     */
    private fun recircRow(on: Boolean, onChange: (Boolean) -> Unit): View = checkRow(
        on = on,
        title = "Nổ máy thì tự lấy gió trong",
        // R10 — KHÔNG hứa quá: lệnh này ở mức "đọc từ mã nguồn khác, chưa xác nhận trên xe owner".
        sub = "Xe quên chế độ này mỗi lần khởi động. ⚠ Lệnh chưa kiểm trên xe — có thể xe không nhận.",
        onChange = onChange,
    )

    // ── Hàng dùng chung: ô tick + dãy chip ────────────────────────────────────────────────────────
    /** Ô tick + tiêu đề + dòng phụ. Rút ra dùng chung cho hình nền và tiện nghi (trước đó chỉ có một chỗ dựng tay). */
    private fun checkRow(on: Boolean, title: String, sub: String, onChange: (Boolean) -> Unit): View {
        val box = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f); typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(dpi(context, 26), dpi(context, 26))
        }
        var state = on
        fun paint() {
            box.text = if (state) "✓" else ""
            box.setTextColor(Color.WHITE)
            box.background = GradientDrawable().apply {
                cornerRadius = dpi(context, 7).toFloat()
                if (state) setColor(c(KachiTheme.ACCENT))
                else { setColor(c("#00000000")); setStroke(dpi(context, 2), c(KachiTheme.MUT2)) }
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
                    text = title; setTextColor(c(KachiTheme.INK)); setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f)
                })
                addView(TextView(context).apply {
                    text = sub; setTextColor(c(KachiTheme.MUT)); setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                })
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            setOnClickListener { state = !state; paint(); onChange(state) }
        }
    }

    /** Một hàng: nhãn bên trái + dãy chip chọn bên phải. Dùng chung cho đơn vị và hình nền. */
    private fun chipRow(label: String, options: List<Pair<String, String>>, current: String,
                        onPick: (String) -> Unit): View {
        val chips = HashMap<String, TextView>()
        var chosen = current
        fun paint() = chips.forEach { (code, tv) ->
            val on = code == chosen
            tv.setTextColor(if (on) Color.WHITE else c(KachiTheme.MUT))
            tv.background = if (on) KachiTheme.gradient(context, 999f) else KachiTheme.card(context, 999f, "#1a1f29")
        }
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dpi(context, 5), 0, dpi(context, 5))
            addView(TextView(context).apply {
                text = label; setTextColor(c(KachiTheme.INK)); setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            options.forEach { (code, text) ->
                val tv = TextView(context).apply {
                    this.text = text; setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f); typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                    setPadding(dpi(context, 12), dpi(context, 6), dpi(context, 12), dpi(context, 6))
                    setOnClickListener { chosen = code; paint(); onPick(code) }
                }
                chips[code] = tv
                addView(tv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    .also { it.marginStart = dpi(context, 6) })
            }
            paint()
        }
    }

    // ── P8 · một hàng cho mỗi quyền còn thiếu ─────────────────────────────────────────────────────────
    /**
     * Nói rõ **thiếu cái gì** và **mất gì**, kèm việc cần làm nếu người dùng phải tự làm.
     * KHÔNG chỉ tới màn cài đặt hệ thống — [ĐO] màn đó bị khoá trên xe.
     */
    private fun permissionRow(req: LauncherRequirement, rep: PermissionReport): View {
        val hint = when {
            req in rep.selfFixable -> "Kachi đang tự xin lại — không cần làm gì"
            req.userAction != null -> req.userAction
            req in rep.environment -> "Hạn chế của môi trường, không phải lỗi của app"
            else -> null
        }
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = KachiTheme.card(context, 14f, "#161b24")
            val p = dpi(context, 12); setPadding(p, p, p, p)
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.bottomMargin = dpi(context, 6); layoutParams = lp
            addView(TextView(context).apply {
                text = if (req.coreFeature) "${req.label} — ảnh hưởng tính năng chính" else req.label
                setTextColor(c(if (req.coreFeature) KachiTheme.AMBER else KachiTheme.INK))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f)
            })
            addView(TextView(context).apply {
                text = "Thiếu thì: ${req.losesWhatIfMissing}"
                setTextColor(c(KachiTheme.MUT)); setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            })
            if (hint != null) addView(TextView(context).apply {
                text = hint; setTextColor(c(KachiTheme.MUT2)); setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            })
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
                text = pick.displayLabel; setTextColor(c(KachiTheme.INK)); setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
                gravity = Gravity.CENTER; maxLines = 2; ellipsize = TextUtils.TruncateAt.END
                setPadding(dpi(context, 2), dpi(context, 6), dpi(context, 2), 0)
            })
            setOnClickListener {
                val now = pick.id !in enabled
                if (now) enabled.add(pick.id) else enabled.remove(pick.id)
                applyState(pick.id); onToggle(pick.id, now)
            }
            // GIỮ = đưa lên/bỏ khỏi thanh trạng thái. Đây là đường đặt **datum bất kỳ** lên thanh trên mà không phải
            // dựng thêm 123 ô cho bảng này (xem [TopStripPicker.section]). Chỉ mục ĐỌC — nút thì nói rõ vì sao không được.
            setOnLongClickListener {
                if (TopStripConfig.isChippable(pick.id)) {
                    stripPicker.toggle(pick.id)
                    val onNow = stripPicker.has(pick.id)
                    Toast.makeText(
                        context,
                        if (onNow) "Đã đưa \"${pick.label}\" lên thanh trạng thái" else "Đã bỏ \"${pick.label}\" khỏi thanh trạng thái",
                        Toast.LENGTH_SHORT,
                    ).show()
                } else {
                    Toast.makeText(context, "Nút bấm không đặt được lên thanh trên (chip quá nhỏ để bấm an toàn)", Toast.LENGTH_SHORT).show()
                }
                true
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
