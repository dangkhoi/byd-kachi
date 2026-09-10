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

/**
 * Dữ liệu xe MẪU (emulator/demo) — GIỮ cho tương thích; **KHÔNG còn nằm trên đường wire** (OQ1: off-car "—", KHÔNG
 * demo). Đường thật = [CarStatus] LIVE bơm qua [HomeUiState.carStatus]; off-car mọi field null ⇒ widget "—".
 */
object DemoCarData : CarDataPort {
    override fun batteryPercent() = 82
    override fun rangeKm() = 418
    override fun tirePressuresBar() = listOf(2.4, 2.4, 2.3, 2.1)
    override fun pm25Level() = 2
    override fun speedKmh() = 56
    override fun outsideTempC() = 26
}

/** Gói dữ liệu render cho widget: trạng thái xe [car] (nguồn sự thật state) + nhạc [media] (đọc live) + transport [onMedia]. */
class WidgetData(
    val car: CarStatus = CarStatus(),
    val media: MediaSnapshot? = null,
    val onMedia: (String) -> Unit = {},
)

/**
 * Dựng View cho 1 widget. Hai họ:
 *  • **curated** (`w_*` trong [WidgetRegistry]) — thẻ dựng tay bám prototype, đọc từ [CarStatus].
 *  • **generic telemetry** (mọi `id` khác trong [TelemetryRegistry]) — render THEO [WidgetShape] qua [TelemetryReadout]:
 *    RING/DIAL/GAUGE/VALUE/CARD/BOARD/STRIP/BADGE. Field null/off-car ⇒ "—" + mờ; tier OVERDRIVE/DASHCAST ⇒ badge nhỏ.
 */
object WidgetViews {

    private const val BADGE = "chưa kiểm trên xe"

    fun build(ctx: Context, id: String, data: WidgetData): View = when (id) {
        "w_clock" -> clock(ctx, data.car)
        "w_energy" -> energyRing(ctx, data.car)
        "w_pm25" -> pm25Ring(ctx, data.car)
        "w_speed" -> speed(ctx, data.car)
        "w_tire" -> tire(ctx, tyreBars(data.car))
        "w_media" -> media(ctx, data)
        "w_car" -> carState(ctx, data.car)
        "w_board" -> board(ctx, data)
        else -> telemetry(ctx, id, data.car)           // generic telemetry theo shape
    }

    /**
     * Nội dung ô widget: **1** widget → to, giữa ô; **2..8** → lưới card đều nhau (1·2·3=1 hàng; 4=2+2; 5=2+3;
     * 6=3+3; 7=3+4; 8=4+4).
     */
    fun buildGrid(ctx: Context, ids: List<String>, data: WidgetData): View {
        val list = ids.take(8)
        if (list.isEmpty()) return label(ctx, "WIDGET", "—", "")
        if (list.size == 1) return build(ctx, list[0], data)
        val n = list.size
        val topN = if (n <= 3) n else n / 2
        val rows = if (n <= 3) listOf(list) else listOf(list.subList(0, topN), list.subList(topN, n))
        val g = dpi(ctx, 5)
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL; setPadding(g, g, g, g)
            rows.forEach { rowIds ->
                val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
                rowIds.forEach { id ->
                    row.addView(mini(ctx, id, data), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                        .also { it.setMargins(g, g, g, g) })
                }
                addView(row, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            }
        }
    }

    // ── Compact (lưới nhiều widget) ────────────────────────────────────────────────────────────────────
    private fun mini(ctx: Context, id: String, data: WidgetData): View {
        val car = data.car
        return when (id) {
            "w_energy" -> miniCard(ctx, "ic-bolt", car.energy.soc?.let { "$it%" } ?: "—", car.energy.evRangeKm?.let { "$it km" } ?: "", KachiTheme.GREEN, false)
            "w_pm25"   -> miniCard(ctx, "ic-leaf", pm25Ug(car)?.toString() ?: "—", "µg · " + (car.climate.pm25Level?.let { pm(it) } ?: "—"), KachiTheme.CYAN, false)
            "w_speed"  -> miniCard(ctx, "ic-speed", car.drivetrain.speedKmh?.toString() ?: "—", "km/h", KachiTheme.RED, false)
            "w_tire"   -> tyreBars(car)?.let { miniCard(ctx, "ic-tire", "${it.min()}–${it.max()}", "bar", KachiTheme.INK, false) } ?: miniCard(ctx, "ic-tire", "—", "bar", KachiTheme.INK, false)
            "w_clock"  -> miniCard(ctx, "ic-sun", SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()), SimpleDateFormat("dd/MM", Locale.getDefault()).format(Date()), KachiTheme.INK, false)
            "w_media"  -> miniCard(ctx, "ic-music", data.media?.title ?: "—", data.media?.artist ?: "", KachiTheme.AMBER, false)
            "w_car"    -> miniCard(ctx, "ic-lock", "Xe", "", KachiTheme.GREEN, false)
            "w_board"  -> miniCard(ctx, "ic-grid", "Tổng hợp", "", KachiTheme.ACCENT, false)
            else       -> telemetryMini(ctx, id, car)
        }
    }

    /** Mini cho 1 telemetry id (icon domain + số + đơn vị/nhãn), "—"+mờ khi null, badge khi cần. */
    private fun telemetryMini(ctx: Context, id: String, car: CarStatus): View {
        val v = TelemetryReadout.of(id, car) ?: return miniCard(ctx, "", id, "", KachiTheme.MUT, false)
        val icon = WidgetCatalog.pick(id)?.icon ?: ""
        val sub = if (v.unit.isNotEmpty()) v.unit else v.label
        return miniCard(ctx, icon, v.display, sub, KachiTheme.INK, v.needsBadge, dim = !v.available)
    }

    private fun miniCard(ctx: Context, icon: String, big: String, sub: String, color: String, badge: Boolean, dim: Boolean = false): View =
        LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            background = KachiTheme.card(ctx, 12f, "#1c212b")
            val p = dpi(ctx, 6); setPadding(p, p, p, p)
            if (dim) alpha = 0.5f
            val r = KachiTheme.iconRes(icon)
            if (r != 0) addView(ImageView(ctx).apply { setImageResource(r); setColorFilter(c(color)) },
                LinearLayout.LayoutParams(dpi(ctx, 20), dpi(ctx, 20)).also { it.bottomMargin = dpi(ctx, 3) })
            addView(tv(ctx, big, 17f, color, true).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
            if (sub.isNotEmpty()) addView(tv(ctx, sub, 10.5f, KachiTheme.MUT).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
            if (badge) addView(badgeView(ctx))
        }

    // ── Generic telemetry theo shape ────────────────────────────────────────────────────────────────────
    private fun telemetry(ctx: Context, id: String, car: CarStatus): View {
        val v = TelemetryReadout.of(id, car) ?: return label(ctx, id.uppercase(), "—", "")
        val body = when (v.shape) {
            WidgetShape.RING -> telemetryRing(ctx, v)
            WidgetShape.BADGE -> badgeShape(ctx, v)
            WidgetShape.STRIP -> stripShape(ctx, v)
            else -> valueShape(ctx, v)   // DIAL/GAUGE/VALUE/CARD/BOARD/MEDIA → số + đơn vị + nhãn
        }
        if (!v.available) body.alpha = 0.5f
        if (v.needsBadge) return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            addView(body, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(badgeView(ctx))
        }
        return body
    }

    private fun telemetryRing(ctx: Context, v: TelemetryView): View = col(ctx).apply {
        val pctv = v.valueText?.toFloatOrNull()?.let { if (v.unit == "%") it else it.coerceIn(0f, 100f) } ?: 0f
        addView(RingView(ctx).apply { set(pctv, KachiTheme.CYAN, v.display, v.unit) },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        addView(tv(ctx, v.label, 12.5f, KachiTheme.MUT).apply { setPadding(0, dpi(ctx, 4), 0, 0) })
    }

    private fun valueShape(ctx: Context, v: TelemetryView): View = col(ctx).apply {
        addView(eyebrow(ctx, v.label))
        val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        row.addView(tv(ctx, v.display, 34f, KachiTheme.INK, true))
        if (v.unit.isNotEmpty()) row.addView(tv(ctx, " ${v.unit}", 14f, KachiTheme.MUT).apply { setPadding(0, dpi(ctx, 10), 0, 0) })
        addView(row.apply { setPadding(0, dpi(ctx, 3), 0, 0) })
    }

    private fun badgeShape(ctx: Context, v: TelemetryView): View = col(ctx).apply {
        addView(eyebrow(ctx, v.label))
        val badge = TextView(ctx).apply {
            text = v.display; setTextColor(c(if (v.available) KachiTheme.INK else KachiTheme.MUT)); typeface = Typeface.DEFAULT_BOLD
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f); gravity = Gravity.CENTER
            setPadding(dpi(ctx, 16), dpi(ctx, 6), dpi(ctx, 16), dpi(ctx, 6))
            background = KachiTheme.pill(ctx, "#1c212b")
        }
        addView(badge, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).also { it.topMargin = dpi(ctx, 6) })
    }

    private fun stripShape(ctx: Context, v: TelemetryView): View = col(ctx).apply {
        addView(eyebrow(ctx, v.label))
        addView(tv(ctx, v.display, 22f, if (v.available) KachiTheme.INK else KachiTheme.MUT, true).apply { setPadding(0, dpi(ctx, 4), 0, 0) })
    }

    private fun badgeView(ctx: Context): View = TextView(ctx).apply {
        text = BADGE; setTextColor(c(KachiTheme.AMBER)); setTextSize(TypedValue.COMPLEX_UNIT_SP, 9.5f); gravity = Gravity.CENTER
        setPadding(dpi(ctx, 6), dpi(ctx, 1), dpi(ctx, 6), dpi(ctx, 1)); maxLines = 1
        background = GradientDrawable().apply { cornerRadius = dpi(ctx, 6).toFloat(); setColor(c("#33fbbf24")) }
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).also { it.topMargin = dpi(ctx, 4) }
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────────────────────────────
    private fun pm(level: Int) = when { level <= 2 -> "Tốt"; level <= 4 -> "TB"; else -> "Kém" }

    /** PM2.5 µg/m³: giá trị thật nếu có, nếu chỉ có mức thì suy diễn xấp xỉ (mức × 9); null → null. */
    private fun pm25Ug(car: CarStatus): Int? = car.climate.pm25ValueUgm3 ?: car.climate.pm25Level?.let { it * 9 }

    /** Áp suất 4 lốp theo BAR (kPa ÷ 100) cho widget lốp; null nếu chưa đọc được lốp nào. */
    private fun tyreBars(car: CarStatus): List<Double>? {
        val t = car.tyres
        val vals = listOf(t.pFlKpa, t.pFrKpa, t.pRlKpa, t.pRrKpa)
        if (vals.all { it == null }) return null
        return vals.map { ((it ?: 0.0) / 100.0 * 10).toInt() / 10.0 }   // 1 chữ số thập phân
    }

    private fun col(ctx: Context): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
        val p = dpi(ctx, 14); setPadding(p, p, p, p)
    }
    private fun tv(ctx: Context, s: String, sp: Float, color: String, bold: Boolean = false) = TextView(ctx).apply {
        text = s; setTextColor(c(color)); setTextSize(TypedValue.COMPLEX_UNIT_SP, sp); gravity = Gravity.CENTER
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }
    private fun eyebrow(ctx: Context, s: String) = tv(ctx, s, 11f, KachiTheme.MUT2).apply { letterSpacing = 0.08f }

    private fun label(ctx: Context, title: String, big: String, sub: String) = col(ctx).apply {
        addView(eyebrow(ctx, title))
        addView(tv(ctx, big, 30f, KachiTheme.INK, true).apply { setPadding(0, dpi(ctx, 3), 0, dpi(ctx, 2)) })
        if (sub.isNotEmpty()) addView(tv(ctx, sub, 13f, KachiTheme.MUT))
    }

    private fun ring(ctx: Context, percent: Float, color: String, big: String, small: String, sub: String): View =
        col(ctx).apply {
            addView(RingView(ctx).apply { set(percent, color, big, small) },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            if (sub.isNotEmpty()) addView(tv(ctx, sub, 12.5f, KachiTheme.MUT).apply { setPadding(0, dpi(ctx, 4), 0, 0) })
        }

    // ── Curated widgets (đọc từ CarStatus) ───────────────────────────────────────────────────────────────
    private fun energyRing(ctx: Context, car: CarStatus): View {
        val soc = car.energy.soc
        return ring(ctx, (soc ?: 0).toFloat(), KachiTheme.GREEN, soc?.let { "$it%" } ?: "—", "pin",
            car.energy.evRangeKm?.let { "≈ $it km" } ?: "")
    }

    private fun pm25Ring(ctx: Context, car: CarStatus): View {
        val ug = pm25Ug(car); val lvl = car.climate.pm25Level
        return ring(ctx, (ug ?: 0) * 1.2f, KachiTheme.CYAN, ug?.toString() ?: "—", "µg/m³",
            "PM2.5 · " + (lvl?.let { pm(it) } ?: "—"))
    }

    private fun clock(ctx: Context, car: CarStatus) = col(ctx).apply {
        addView(tv(ctx, SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()), 50f, KachiTheme.INK, true))
        addView(tv(ctx, SimpleDateFormat("EEEE, dd/MM", Locale.forLanguageTag("vi")).format(Date()), 14f, KachiTheme.MUT))
        addView(tv(ctx, (car.climate.outsideTempC?.let { "$it°C" } ?: "—") + " · ngoài xe", 13f, KachiTheme.MUT).apply {
            setPadding(0, dpi(ctx, 6), 0, 0)
            val r = KachiTheme.iconRes("ic-sun")
            if (r != 0) {
                val d = resources.getDrawable(r, ctx.theme).apply { setBounds(0, 0, dpi(ctx, 18), dpi(ctx, 18)); setTint(c(KachiTheme.AMBER)) }
                setCompoundDrawablesRelative(d, null, null, null); compoundDrawablePadding = dpi(ctx, 6)
            }
        })
    }

    private fun speed(ctx: Context, car: CarStatus) = col(ctx).apply {
        val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        row.addView(tv(ctx, car.drivetrain.speedKmh?.toString() ?: "—", 44f, KachiTheme.INK, true))
        row.addView(tv(ctx, " km/h", 15f, KachiTheme.MUT))
        addView(row)
        val limit = if (car.safety.speedLimitWarning == true) "Vượt tốc độ" else "Tốc độ hiện tại"
        addView(tv(ctx, limit, 13f, if (car.safety.speedLimitWarning == true) KachiTheme.RED else KachiTheme.MUT).apply { setPadding(0, dpi(ctx, 6), 0, 0) })
    }

    private fun tire(ctx: Context, t: List<Double>?) = col(ctx).apply {
        if (t == null || t.size < 4) { addView(tv(ctx, "—", 28f, KachiTheme.INK, true)); addView(tv(ctx, "áp suất lốp (bar)", 12f, KachiTheme.MUT)); return@apply }
        fun cell(v: Double, tag: String, low: Boolean) = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.START
            val p = dpi(ctx, 5); setPadding(dpi(ctx, 6), p, dpi(ctx, 20), p)
            addView(tv(ctx, tag, 11.5f, KachiTheme.MUT2).apply { gravity = Gravity.START })
            addView(tv(ctx, "$v", 19f, if (low) KachiTheme.AMBER else KachiTheme.INK, true).apply { gravity = Gravity.START })
        }
        fun row(a: View, b: View) = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; addView(a); addView(b) }
        addView(row(cell(t[0], "Trước trái", t[0] < 2.2), cell(t[1], "Trước phải", t[1] < 2.2)))
        addView(row(cell(t[2], "Sau trái", t[2] < 2.2), cell(t[3], "Sau phải", t[3] < 2.2)))
        addView(tv(ctx, "bar", 12f, KachiTheme.MUT).apply { setPadding(0, dpi(ctx, 6), 0, 0) })
    }

    private fun media(ctx: Context, data: WidgetData) = col(ctx).apply {
        val m = data.media
        val art = ImageView(ctx).apply {
            background = KachiTheme.gradient(ctx, 15f, "#f59e0b", "#ef4444")
            if (m?.albumArt != null) setImageBitmap(m.albumArt)
        }
        addView(art, LinearLayout.LayoutParams(dpi(ctx, 78), dpi(ctx, 78)).also { it.bottomMargin = dpi(ctx, 9) })
        addView(tv(ctx, m?.title ?: "—", 15f, KachiTheme.INK, true).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
        addView(tv(ctx, m?.artist ?: "", 12.5f, KachiTheme.MUT).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
        val frac = m?.let { if (it.durationMs > 0) (it.positionMs.toFloat() / it.durationMs).coerceIn(0f, 1f) else 0f } ?: 0f
        val prog = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            background = GradientDrawable().apply { cornerRadius = dpi(ctx, 3).toFloat(); setColor(c("#29FFFFFF")) }
            addView(View(ctx).apply { background = GradientDrawable().apply { cornerRadius = dpi(ctx, 3).toFloat(); setColor(c(KachiTheme.ACCENT)) } },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, frac.coerceAtLeast(0.001f)))
            addView(View(ctx), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, (1f - frac).coerceAtLeast(0.001f)))
        }
        addView(prog, LinearLayout.LayoutParams(dpi(ctx, 150), dpi(ctx, 5)).also { it.topMargin = dpi(ctx, 10) })
        addView(LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; setPadding(0, dpi(ctx, 10), 0, 0)
            fun mbtn(icon: String, action: String) = ImageView(ctx).apply {
                val r = KachiTheme.iconRes(icon); if (r != 0) { setImageResource(r); setColorFilter(Color.WHITE) }
                setOnClickListener { data.onMedia(action) }
            }
            val playing = m?.playing == true
            addView(mbtn("ic-prev", "prev"), LinearLayout.LayoutParams(dpi(ctx, 22), dpi(ctx, 22)).also { it.marginEnd = dpi(ctx, 20) })
            addView(mbtn("ic-play", if (playing) "pause" else "play"), LinearLayout.LayoutParams(dpi(ctx, 22), dpi(ctx, 22)).also { it.marginEnd = dpi(ctx, 20) })
            addView(mbtn("ic-next", "next"), LinearLayout.LayoutParams(dpi(ctx, 22), dpi(ctx, 22)))
        })
    }

    private fun carState(ctx: Context, car: CarStatus) = col(ctx).apply {
        addView(CarMiniView(ctx), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        val doors = listOf(car.body.doorLfOpen, car.body.doorRfOpen, car.body.doorLrOpen, car.body.doorRrOpen)
        val doorLine = when {
            doors.all { it == null } -> "Trạng thái cửa —"
            doors.any { it == true } -> "Có cửa đang mở"
            else -> "4 cửa đóng"
        }
        addView(tv(ctx, doorLine, 13f, if (doors.any { it == true }) KachiTheme.AMBER else KachiTheme.GREEN).apply { setPadding(0, dpi(ctx, 6), 0, 0) })
        val tail = when (car.body.tailgateOpen) { true -> "Cốp sau đang mở"; false -> "Cốp sau đóng"; null -> "Cốp sau —" }
        addView(tv(ctx, tail, 13f, if (car.body.tailgateOpen == true) KachiTheme.AMBER else KachiTheme.MUT))
    }

    private fun board(ctx: Context, data: WidgetData): View {
        val car = data.car
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
        val bat = car.energy.soc; val km = car.energy.evRangeKm; val lvl = car.climate.pm25Level; val ug = pm25Ug(car)
        val tp = tyreBars(car)
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL; val p = dpi(ctx, 6); setPadding(p, p, p, p)
            addView(rowOf(
                cell("ic-bolt", bat?.let { "$it%" } ?: "—", km?.let { "$it km" } ?: "", KachiTheme.GREEN),
                cell("ic-leaf", ug?.let { "${it}µg" } ?: "—", "PM2.5 " + (lvl?.let { pm(it) } ?: ""), KachiTheme.CYAN),
            ), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(rowOf(
                cell("ic-tire", tp?.let { "${it.min()}–${it.max()}" } ?: "—", "Áp suất lốp", KachiTheme.INK),
                cell("ic-music", data.media?.title ?: "—", data.media?.artist ?: "", KachiTheme.INK),
            ), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        }
    }
}
