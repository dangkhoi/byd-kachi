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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.byd.clusternav.launcher.KachiTheme.c
import com.byd.clusternav.launcher.KachiTheme.dpi

/** Dữ liệu xe MẪU (emulator/demo). Trên xe thật thay bằng adapter BydHal. Off-car thật = [NoCar] (→ "—"). */
object DemoCarData : CarDataPort {
    override fun batteryPercent() = 82
    override fun rangeKm() = 418
    override fun tirePressuresBar() = listOf(2.4, 2.4, 2.3, 2.1)
    override fun pm25Level() = 2
    override fun speedKmh() = 56
    override fun outsideTempC() = 26
}

/** Dựng View cho 1 widget — bám sát prototype kachi-workspace.html (vòng đo, thẻ kính, typography). */
object WidgetViews {
    fun build(ctx: Context, id: String, data: CarDataPort): View = when (id) {
        "w_clock" -> clock(ctx, data)
        "w_energy" -> ring(ctx, pct(data.batteryPercent(), 100), KachiTheme.GREEN,
            data.batteryPercent()?.let { "$it%" } ?: "—", "pin",
            data.rangeKm()?.let { "≈ $it km · đầy sau 40′" } ?: "")
        "w_pm25" -> {
            val lvl = data.pm25Level(); val ug = (lvl ?: 0) * 9
            ring(ctx, ug * 1.2f, KachiTheme.CYAN, if (lvl != null) "$ug" else "—", "µg/m³",
                "PM2.5 · ${lvl?.let { pm(it) } ?: "—"} · tự lọc đang bật")
        }
        "w_speed" -> speed(ctx, data)
        "w_tire" -> tire(ctx, data.tirePressuresBar())
        "w_media" -> media(ctx)
        "w_car" -> carState(ctx)
        "w_board" -> board(ctx, data)
        else -> label(ctx, id.uppercase(), "—", "")
    }

    private fun pct(v: Int?, max: Int) = ((v ?: 0).toFloat() / max * 100f)
    private fun pm(level: Int) = when { level <= 2 -> "Tốt"; level <= 4 -> "TB"; else -> "Kém" }

    /**
     * Nội dung ô widget theo yêu cầu owner: **1** widget → to, giữa ô; **2..8** → lưới card đều nhau, cách đều:
     * 1·2·3 = 1 hàng; 4 = 2+2; 5 = 2+3; 6 = 3+3; 7 = 3+4; 8 = 4+4.
     */
    fun buildGrid(ctx: Context, ids: List<String>, data: CarDataPort): View {
        val list = ids.take(8)
        if (list.isEmpty()) return label(ctx, "WIDGET", "—", "")
        if (list.size == 1) return build(ctx, list[0], data)          // 1 → widget đầy đủ, to
        val n = list.size
        val topN = if (n <= 3) n else n / 2                            // hàng trên
        val rows = if (n <= 3) listOf(list) else listOf(list.subList(0, topN), list.subList(topN, n))
        val g = dpi(ctx, 5)
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL; setPadding(g, g, g, g)
            rows.forEach { rowIds ->
                val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
                rowIds.forEach { id ->
                    row.addView(mini(ctx, id, data), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                        .also { it.setMargins(g, g, g, g) })                 // size bằng nhau (weight 1) + cách đều (margin g)
                }
                addView(row, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            }
        }
    }

    /** Widget compact (icon + số chính + phụ đề) cho ô lưới nhiều widget. */
    private fun mini(ctx: Context, id: String, data: CarDataPort): View {
        val lvl = data.pm25Level(); val ug = (lvl ?: 0) * 9; val tp = data.tirePressuresBar()
        return when (id) {
            "w_energy" -> miniCard(ctx, "ic-bolt", data.batteryPercent()?.let { "$it%" } ?: "—", data.rangeKm()?.let { "$it km" } ?: "", KachiTheme.GREEN)
            "w_pm25"   -> miniCard(ctx, "ic-leaf", if (lvl != null) "$ug" else "—", "µg · " + (lvl?.let { pm(it) } ?: "—"), KachiTheme.CYAN)
            "w_speed"  -> miniCard(ctx, "ic-speed", data.speedKmh()?.toString() ?: "—", "km/h", KachiTheme.RED)
            "w_tire"   -> miniCard(ctx, "ic-tire", tp?.takeIf { it.isNotEmpty() }?.let { "${it.min()}–${it.max()}" } ?: "—", "bar", KachiTheme.INK)
            "w_clock"  -> miniCard(ctx, "ic-sun", SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()), SimpleDateFormat("dd/MM", Locale.getDefault()).format(Date()), KachiTheme.INK)
            "w_media"  -> miniCard(ctx, "ic-music", "Đang phát", "Sơn Tùng", KachiTheme.AMBER)
            "w_car"    -> miniCard(ctx, "ic-lock", "Đã khoá", "4 cửa", KachiTheme.GREEN)
            "w_board"  -> miniCard(ctx, "ic-grid", "Tổng hợp", "", KachiTheme.ACCENT)
            else       -> miniCard(ctx, "", id, "", KachiTheme.MUT)
        }
    }

    private fun miniCard(ctx: Context, icon: String, big: String, sub: String, color: String): View =
        LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            background = KachiTheme.card(ctx, 12f, "#1c212b")
            val p = dpi(ctx, 6); setPadding(p, p, p, p)
            val r = KachiTheme.iconRes(icon)
            if (r != 0) addView(ImageView(ctx).apply { setImageResource(r); setColorFilter(c(color)) },
                LinearLayout.LayoutParams(dpi(ctx, 20), dpi(ctx, 20)).also { it.bottomMargin = dpi(ctx, 3) })
            addView(tv(ctx, big, 17f, color, true).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
            if (sub.isNotEmpty()) addView(tv(ctx, sub, 10.5f, KachiTheme.MUT).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
        }

    private fun col(ctx: Context): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
        val p = dpi(ctx, 14); setPadding(p, p, p, p)
    }
    private fun tv(ctx: Context, s: String, sp: Float, color: String, bold: Boolean = false) = TextView(ctx).apply {
        text = s; setTextColor(c(color)); setTextSize(TypedValue.COMPLEX_UNIT_SP, sp); gravity = Gravity.CENTER
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }
    private fun eyebrow(ctx: Context, s: String) = tv(ctx, s, 11f, KachiTheme.MUT2).apply {
        letterSpacing = 0.08f
    }

    private fun label(ctx: Context, title: String, big: String, sub: String) = col(ctx).apply {
        addView(eyebrow(ctx, title))
        addView(tv(ctx, big, 30f, KachiTheme.INK, true).apply { setPadding(0, dpi(ctx, 3), 0, dpi(ctx, 2)) })
        if (sub.isNotEmpty()) addView(tv(ctx, sub, 13f, KachiTheme.MUT))
    }

    private fun ring(ctx: Context, percent: Float, color: String, big: String, small: String, sub: String): View =
        col(ctx).apply {
            val rv = RingView(ctx).apply { set(percent, color, big, small) }
            addView(rv, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            if (sub.isNotEmpty()) addView(tv(ctx, sub, 12.5f, KachiTheme.MUT).apply { setPadding(0, dpi(ctx, 4), 0, 0) })
        }

    private fun clock(ctx: Context, data: CarDataPort) = col(ctx).apply {
        addView(tv(ctx, SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()), 50f, KachiTheme.INK, true))
        addView(tv(ctx, SimpleDateFormat("EEEE, dd/MM", Locale.forLanguageTag("vi")).format(Date()), 14f, KachiTheme.MUT))
        addView(tv(ctx, (data.outsideTempC()?.let { "$it°C" } ?: "—") + " · Có mây", 13f, KachiTheme.MUT).apply {
            setPadding(0, dpi(ctx, 6), 0, 0)
            val r = KachiTheme.iconRes("ic-sun")
            if (r != 0) {
                val d = resources.getDrawable(r, ctx.theme).apply { setBounds(0, 0, dpi(ctx, 18), dpi(ctx, 18)); setTint(c(KachiTheme.AMBER)) }
                setCompoundDrawablesRelative(d, null, null, null); compoundDrawablePadding = dpi(ctx, 6)
            }
        })
    }

    private fun speed(ctx: Context, data: CarDataPort) = col(ctx).apply {
        val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        row.addView(tv(ctx, data.speedKmh()?.toString() ?: "—", 44f, KachiTheme.INK, true))
        row.addView(tv(ctx, " km/h", 15f, KachiTheme.MUT))
        addView(row)
        val sign = tv(ctx, "60", 16f, "#111111", true).apply {
            gravity = Gravity.CENTER
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.WHITE); setStroke(dpi(ctx, 3), c(KachiTheme.RED)) }
            val s = dpi(ctx, 40); minWidth = s; minHeight = s; width = s; height = s
        }
        val srow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; setPadding(0, dpi(ctx, 6), 0, 0) }
        srow.addView(sign); srow.addView(tv(ctx, "  Giới hạn · còn dư 4", 13f, KachiTheme.MUT))
        addView(srow)
    }

    private fun tire(ctx: Context, t: List<Double>?) = col(ctx).apply {
        if (t == null || t.size < 4) { addView(tv(ctx, "—", 28f, KachiTheme.INK, true)); return@apply }
        fun cell(v: Double, tag: String, low: Boolean) = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.START
            val p = dpi(ctx, 5); setPadding(dpi(ctx, 6), p, dpi(ctx, 20), p)
            addView(tv(ctx, tag, 11.5f, KachiTheme.MUT2).apply { gravity = Gravity.START })
            addView(tv(ctx, "$v", 19f, if (low) KachiTheme.AMBER else KachiTheme.INK, true).apply { gravity = Gravity.START })
        }
        fun row(a: View, b: View) = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; addView(a); addView(b) }
        addView(row(cell(t[0], "Trước trái", false), cell(t[1], "Trước phải", false)))
        addView(row(cell(t[2], "Sau trái", false), cell(t[3], "Sau phải", t[3] < 2.2)))
        addView(tv(ctx, "bar · bánh sau phải non hơi", 12f, KachiTheme.MUT).apply { setPadding(0, dpi(ctx, 6), 0, 0) })
    }

    private fun media(ctx: Context) = col(ctx).apply {
        // Bám prototype w_media: ảnh bìa vuông bo góc → tên bài → nghệ sĩ → progress → nút ⏮ ⏯ ⏭ (dọc, canh giữa).
        addView(View(ctx).apply { background = KachiTheme.gradient(ctx, 15f, "#f59e0b", "#ef4444") },
            LinearLayout.LayoutParams(dpi(ctx, 78), dpi(ctx, 78)).also { it.bottomMargin = dpi(ctx, 9) })
        addView(tv(ctx, "Chạy ngay đi", 15f, KachiTheme.INK, true))
        addView(tv(ctx, "Sơn Tùng M-TP", 12.5f, KachiTheme.MUT))
        val prog = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            background = GradientDrawable().apply { cornerRadius = dpi(ctx, 3).toFloat(); setColor(c("#29FFFFFF")) }
            addView(View(ctx).apply { background = GradientDrawable().apply { cornerRadius = dpi(ctx, 3).toFloat(); setColor(c(KachiTheme.ACCENT)) } },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 0.42f))
            addView(View(ctx), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 0.58f))
        }
        addView(prog, LinearLayout.LayoutParams(dpi(ctx, 150), dpi(ctx, 5)).also { it.topMargin = dpi(ctx, 10) })
        addView(LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; setPadding(0, dpi(ctx, 10), 0, 0)
            fun mbtn(icon: String) = ImageView(ctx).apply {
                val r = KachiTheme.iconRes(icon); if (r != 0) { setImageResource(r); setColorFilter(Color.WHITE) }
            }
            addView(mbtn("ic-prev"), LinearLayout.LayoutParams(dpi(ctx, 22), dpi(ctx, 22)).also { it.marginEnd = dpi(ctx, 20) })
            addView(mbtn("ic-play"), LinearLayout.LayoutParams(dpi(ctx, 22), dpi(ctx, 22)).also { it.marginEnd = dpi(ctx, 20) })
            addView(mbtn("ic-next"), LinearLayout.LayoutParams(dpi(ctx, 22), dpi(ctx, 22)))
        })
    }

    private fun carState(ctx: Context) = col(ctx).apply {
        addView(CarMiniView(ctx), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        addView(tv(ctx, "Đã khoá · 4 cửa đóng", 13f, KachiTheme.GREEN).apply { setPadding(0, dpi(ctx, 6), 0, 0) })
        addView(tv(ctx, "Cốp sau đang mở", 13f, KachiTheme.AMBER))
    }

    private fun board(ctx: Context, data: CarDataPort): View {
        fun cell(icon: String, big: String, sub: String, color: String) = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            background = KachiTheme.card(ctx, 12f, "#1c212b")
            val r = KachiTheme.iconRes(icon)
            if (r != 0) addView(ImageView(ctx).apply { setImageResource(r); setColorFilter(c(color)) },
                LinearLayout.LayoutParams(dpi(ctx, 22), dpi(ctx, 22)).also { it.bottomMargin = dpi(ctx, 3) })
            addView(tv(ctx, big, 17f, color, true))
            if (sub.isNotEmpty()) addView(tv(ctx, sub, 11f, KachiTheme.MUT))
        }
        fun rowOf(a: View, b: View) = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(a, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).also { it.setMargins(dpi(ctx,5),dpi(ctx,5),dpi(ctx,5),dpi(ctx,5)) })
            addView(b, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).also { it.setMargins(dpi(ctx,5),dpi(ctx,5),dpi(ctx,5),dpi(ctx,5)) })
        }
        val bat = data.batteryPercent(); val km = data.rangeKm(); val lvl = data.pm25Level(); val ug = (lvl ?: 0) * 9
        val tp = data.tirePressuresBar()
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL; val p = dpi(ctx, 6); setPadding(p, p, p, p)
            addView(rowOf(
                cell("ic-bolt", bat?.let { "$it%" } ?: "—", km?.let { "$it km" } ?: "", KachiTheme.GREEN),
                cell("ic-leaf", if (lvl != null) "${ug}µg" else "—", "PM2.5 " + (lvl?.let { pm(it) } ?: ""), KachiTheme.CYAN),
            ), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(rowOf(
                cell("ic-tire", tp?.takeIf { it.isNotEmpty() }?.let { "${it.min()}–${it.max()}" } ?: "—", "Áp suất lốp", KachiTheme.INK),
                cell("ic-music", "Đang phát", "Sơn Tùng", KachiTheme.INK),
            ), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        }
    }
}
