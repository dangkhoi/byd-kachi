package com.byd.clusternav.launcher

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.byd.clusternav.R
import com.byd.clusternav.launcher.KachiTheme.c
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Thanh trạng thái trên cùng của HOME: đồng hồ + ngày + chọn bố cục segmented + chip xe (PM2.5/nhiệt/pin) +
 * pill "Thanh"/"Cài đặt" + avatar hồ sơ. Tách khỏi [KachiHomeActivity] (B5b) để activity còn là composition-root.
 *
 * THUẦN VIEW — KHÔNG giữ state launcher: mọi tương tác đẩy lên qua callback (một chiều), activity nối vào intent VM.
 * [selectPreset]/[setProfileInitial]/[refreshChips]/[updateClock] do [KachiHomeActivity.render] / vòng tick gọi để
 * phản chiếu state. Byte-giữ so với `buildTopStrip()`/`buildSegmented()`/`pill()`/`chip()`/`profileAvatar()` cũ.
 */
class KachiTopStrip(
    private val activity: Activity,
    private val onSelectPreset: (LayoutPreset) -> Unit,
    private val onCycleDock: () -> Unit,
    private val onOpenSettings: () -> Unit,
    private val onProfileTap: () -> Unit,
    private val onProfileLongPress: () -> Unit,
) {
    private val presetCells = HashMap<LayoutPreset, ImageView>()
    private lateinit var clock: TextView
    private lateinit var dateText: TextView
    private lateinit var chipRow: LinearLayout
    private lateinit var profileAvatarView: TextView

    /** View thanh trạng thái (dựng lười một lần). */
    val view: View by lazy { build() }

    private fun build(): View {
        val strip = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = KachiTheme.card(context, 14f, "#990a0d13", "#26ffffff")   // thanh mờ bo góc + viền rõ (prototype)
            setPadding(dp(14), dp(4), dp(14), dp(4))
        }
        clock = TextView(activity).apply {
            setTextColor(Color.WHITE); setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f); typeface = Typeface.DEFAULT_BOLD; letterSpacing = 0.02f
        }
        dateText = TextView(activity).apply { setTextColor(c(KachiTheme.MUT)); setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f); setPadding(dp(10), 0, 0, 0) }
        strip.addView(clock); strip.addView(dateText)
        strip.addView(buildSegmented(), LinearLayout.LayoutParams(WRAP, WRAP).also { it.marginStart = dp(16) })
        strip.addView(View(activity), LinearLayout.LayoutParams(0, 1, 1f))
        chipRow = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        strip.addView(chipRow)
        strip.addView(pill("Thanh", false) { onCycleDock() }, pillLp())
        strip.addView(pill("Cài đặt", true) { onOpenSettings() }, pillLp())
        strip.addView(profileAvatar(), LinearLayout.LayoutParams(WRAP, WRAP).also { it.marginStart = dp(10) })
        refreshChips()
        return strip
    }

    private fun pillLp() = LinearLayout.LayoutParams(WRAP, WRAP).also { it.marginStart = dp(8) }

    private fun pill(text: String, primary: Boolean, onClick: () -> Unit) = TextView(activity).apply {
        this.text = text; setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f); typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
        setPadding(dp(14), dp(6), dp(14), dp(6))
        if (primary) { background = KachiTheme.gradient(context, 999f); setTextColor(Color.WHITE) }
        else { background = KachiTheme.pill(context); setTextColor(c(KachiTheme.INK)) }
        setOnClickListener { onClick() }
    }

    private fun buildSegmented(): View {
        val bar = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            background = KachiTheme.pill(context); val p = dp(3); setPadding(p, p, p, p)
        }
        presetCells.clear()
        listOf(
            R.drawable.ic_layout_1 to LayoutPreset.ONE,
            R.drawable.ic_layout_2c to LayoutPreset.TWO_COL,
            R.drawable.ic_layout_2r to LayoutPreset.TWO_ROW,
            R.drawable.ic_layout_3 to LayoutPreset.THREE,
            R.drawable.ic_layout_4 to LayoutPreset.QUAD,
        ).forEach { (icon, p) ->
            val cell = ImageView(activity).apply {
                setImageResource(icon)
                setPadding(dp(10), dp(7), dp(10), dp(7))
                setOnClickListener { onSelectPreset(p) }
            }
            presetCells[p] = cell
            bar.addView(cell, LinearLayout.LayoutParams(WRAP, WRAP))
        }
        return bar
    }

    /** Tô sáng ô preset đang chọn (do render gọi khi preset đổi). */
    fun selectPreset(sel: LayoutPreset) {
        presetCells.forEach { (p, cell) ->
            if (p == sel) { cell.background = KachiTheme.gradient(activity, 999f); cell.setColorFilter(Color.WHITE) }
            else { cell.background = null; cell.setColorFilter(c(KachiTheme.MUT)) }
        }
    }

    private fun chipLp() = LinearLayout.LayoutParams(WRAP, WRAP).also { it.marginStart = dp(8) }

    private fun chip(text: String, iconName: String?, color: String): TextView = TextView(activity).apply {
        this.text = text; setTextColor(c(color)); setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f); gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(4), 0, dp(4), 0)   // KHÔNG viền pill — chip prototype chỉ icon + chữ
        if (iconName != null) {
            val r = KachiTheme.iconRes(iconName)
            if (r != 0) {
                val d = activity.resources.getDrawable(r, activity.theme).apply { setBounds(0, 0, dp(16), dp(16)); setTint(c(color)) }
                setCompoundDrawablesRelative(d, null, null, null); compoundDrawablePadding = dp(6)
            }
        }
    }

    /** Cập nhật chip xe (PM2.5 / nhiệt ngoài / pin·km) từ [DemoCarData]. */
    fun refreshChips() {
        chipRow.removeAllViews()
        val d: CarDataPort = DemoCarData   // interface type (Int?): chip vẫn hiện "—" nếu nguồn xe thật trả null
        val pm = d.pm25Level()?.let { if (it <= 2) "Tốt" else if (it <= 4) "TB" else "Kém" } ?: "—"
        chipRow.addView(chip("PM2.5 · $pm", "ic-leaf", "#c3cee0"), chipLp())
        chipRow.addView(chip("${d.outsideTempC() ?: "—"}°C ngoài", null, "#c3cee0"), chipLp())
        chipRow.addView(chip("${d.batteryPercent() ?: "—"}% · ${d.rangeKm() ?: "—"} km", "ic-bolt", KachiTheme.GREEN), chipLp())
    }

    // ── Hồ sơ tài xế: pill hiện tên, chạm = đổi hồ sơ, giữ = tạo mới ──
    private fun profileAvatar(): TextView {
        profileAvatarView = TextView(activity).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f); typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            val s = dp(30); width = s; height = s; background = KachiTheme.gradient(activity, 999f)
            setOnClickListener { onProfileTap() }
            setOnLongClickListener { onProfileLongPress(); true }
        }
        return profileAvatarView
    }

    /** Chữ cái đầu của hồ sơ [profileName] lên avatar (do render / init gọi). */
    fun setProfileInitial(profileName: String) {
        profileAvatarView.text = profileName.take(1).uppercase()
    }

    /** Cập nhật đồng hồ + ngày (do vòng tick / onResume gọi). */
    fun updateClock() {
        clock.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        dateText.text = SimpleDateFormat("EEEE, dd/MM", Locale.forLanguageTag("vi")).format(Date())
    }

    private fun dp(v: Int): Int = (v * activity.resources.displayMetrics.density).toInt()

    private companion object {
        const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    }
}
