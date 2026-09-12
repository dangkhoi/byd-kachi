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
import com.byd.clusternav.launcher.KachiSpace as Sp

/**
 * Thanh trạng thái trên cùng của HOME: đồng hồ + ngày + chọn bố cục segmented + chip xe (cấu hình được) +
 * pill "Ứng dụng"/"Cài đặt" + avatar hồ sơ. Tách khỏi [KachiHomeActivity] (B5b) để activity còn là
 * composition-root.
 *
 * THUẦN VIEW — KHÔNG giữ state launcher: mọi tương tác đẩy lên qua callback (một chiều), activity nối vào intent VM.
 * [selectPreset]/[setProfileInitial]/[refreshChips]/[updateClock] do [KachiHomeActivity.render] / vòng tick gọi để
 * phản chiếu state.
 *
 * ## S1 — MỘT cửa vào cấu hình
 * Trước S1 thanh này có **ba** bề mặt cấu hình: pill "Tuỳ biến" (bảng khả năng + đơn vị + hình nền + quyền), pill
 * "Cài đặt" (nhảy thẳng sang màn ClusterNav cũ) và pill **"Thanh"** (xoay vòng 4 viền của thanh nút xe, ghi bền
 * `dock_edge`). Ba chỗ cho cùng một loại việc, và không chỗ nào bày đủ. Nay chỉ còn **"Cài đặt"** → [SettingsPanel];
 * màn ClusterNav là **một nhóm bên trong** đó.
 *
 * ⚠⚠ **Pill "Thanh" đã BỎ HẲN** (owner: *"không để cấu hình nằm lỉ tỉ"*). Chức năng không mất mà **tốt hơn**: Cài đặt
 * → Màn hình chính → "Viền đặt thanh" bày cả 4 viền và đặt THẲNG một viền, thay vì bắt bấm tới ba lần để đi hết vòng
 * BOTTOM → LEFT → RIGHT → TOP mà ô đang sáng thì không nói gì. `HomeViewModel.cycleDockEdge()` bị xoá cùng lúc vì sau
 * khi gỡ pill nó không còn chỗ gọi nào (mã chết).
 *
 * Hai thứ **cố ý ở lại** (§4.5): 5 nút bố cục (đổi bố cục là việc hằng ngày; cả hai bề mặt đi cùng một intent) và
 * avatar hồ sơ (đang ở hồ sơ nào là thông tin phải thấy liên tục). Nhưng avatar chỉ còn **hiển thị + chạm để đổi** —
 * việc *tạo* hồ sơ chuyển vào Cài đặt, vì giữ cả hai đường tạo là hai chỗ phải sửa và sẽ lệch nhau.
 *
 * Giới hạn của thanh này là **khoá lưu bền nào được phép chạm**, không phải "bao nhiêu pill": chỉ `preset` (5 nút bố
 * cục) và `active_profile` (avatar) — xem [SettingsCatalog.TOP_STRIP_ALLOWED_KEYS], nơi khai lý do, và
 * `TopStripSurfaceContractTest` canh cả hai đầu.
 */
class KachiTopStrip(
    private val activity: Activity,
    private val onSelectPreset: (LayoutPreset) -> Unit,
    private val onOpenSettings: () -> Unit,
    private val onProfileTap: () -> Unit,
    private val onOpenAppList: () -> Unit = {},   // U3: lối vào "Mở ứng dụng" (mở app toàn màn, không gắn ô)
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
            background = KachiTheme.card(context, Sp.RADIUS_L, KachiTheme.BAR_TOP, KachiTheme.LINE_STRONG)   // thanh mờ bo góc + viền rõ (prototype)
            setPadding(dp(Sp.L), dp(Sp.XS), dp(Sp.L), dp(Sp.XS))
        }
        clock = TextView(activity).apply {
            setTextColor(c(KachiTheme.INK)); setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f); typeface = Typeface.DEFAULT_BOLD; letterSpacing = 0.02f
        }
        dateText = TextView(activity).apply { setTextColor(c(KachiTheme.MUT)); setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f); setPadding(dp(Sp.M), 0, 0, 0) }
        strip.addView(clock); strip.addView(dateText)
        strip.addView(buildSegmented(), LinearLayout.LayoutParams(WRAP, WRAP).also { it.marginStart = dp(Sp.L) })
        strip.addView(View(activity), LinearLayout.LayoutParams(0, 1, 1f))
        chipRow = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        strip.addView(chipRow)
        strip.addView(pill(activity.getString(R.string.kachi_pill_apps), false) { onOpenAppList() }, pillLp())   // U3: mở app toàn màn
        strip.addView(pill(activity.getString(R.string.kachi_pill_settings), true) { onOpenSettings() }, pillLp())
        strip.addView(profileAvatar(), LinearLayout.LayoutParams(WRAP, WRAP).also { it.marginStart = dp(Sp.SLOT_GAP) })
        refreshChips(CarStatus())
        return strip
    }

    private fun pillLp() = LinearLayout.LayoutParams(WRAP, WRAP).also { it.marginStart = dp(Sp.S) }

    /**
     * Pill bấm được của thanh trên ("Ứng dụng" · "Cài đặt").
     *
     * **T5 — cao ~30dp → [Sp.TOUCH]**: đây là đường vào chính của launcher (mở danh sách app, mở Cài đặt) mà
     * lại là đích chạm dưới mức tối thiểu. Nới bằng `minimumHeight` chứ KHÔNG bằng cách tăng lề trong: lề trong
     * đẩy cả chữ ra xa viền, còn `minimumHeight` chỉ kéo cao vùng chạm và giữ chữ ở giữa.
     */
    private fun pill(text: String, primary: Boolean, onClick: () -> Unit) = TextView(activity).apply {
        this.text = text; setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f); typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
        setPadding(dp(Sp.L), dp(Sp.S), dp(Sp.L), dp(Sp.S))
        minimumHeight = dp(Sp.TOUCH)
        if (primary) { background = KachiTheme.gradient(context, Sp.RADIUS_PILL); setTextColor(c(KachiTheme.ON_ACCENT)) }
        else { background = KachiTheme.pill(context); setTextColor(c(KachiTheme.INK)) }
        setOnClickListener { onClick() }
    }

    private fun buildSegmented(): View {
        val bar = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            // Lề DỌC = 0 (chỉ còn lề ngang): từ T5 mỗi ô tự cao Sp.TOUCH, thêm lề dọc nữa thì thanh trên cao
            // 56dp mà không ai chạm được phần lề đó.
            background = KachiTheme.pill(context); setPadding(dp(Sp.XS), 0, dp(Sp.XS), 0)
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
                // T2: `ic_layout_*` đổi khung vẽ 22×16 → 24×24 (cả bộ icon một khung). Cỡ NỘI TẠI của drawable vì thế
                // đi từ 24×17dp lên 24×24dp, nên lề dọc phải hạ 7 → 4 để ô giữ nguyên chiều cao (32dp so với 31dp
                // trước đây). KHÔNG hạ thì dải chọn bố cục — thứ luôn nằm trên màn — tự nhiên cao thêm 7dp.
                setPadding(dp(Sp.M), dp(Sp.XS), dp(Sp.M), dp(Sp.XS))
                // T5 — ô chọn bố cục cao 32dp → Sp.TOUCH. Glyph giữ 24dp (cỡ nội tại của drawable, ImageView
                // không kéo giãn khi khung lớn hơn hình), nên chỉ vùng chạm to ra.
                minimumHeight = dp(Sp.TOUCH)
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
            if (p == sel) { cell.background = KachiTheme.gradient(activity, Sp.RADIUS_PILL); cell.setColorFilter(c(KachiTheme.ON_ACCENT)) }
            else { cell.background = null; cell.setColorFilter(c(KachiTheme.MUT)) }
        }
    }

    /** Đổi một số theo lựa chọn đơn vị, dùng CHUNG bộ chuyển của :core (không tự nhân chia tại chỗ). */
    private fun conv(v: Double, q: Quantity, units: UnitPrefs): String {
        val base = Units.BASE[q] ?: return v.toInt().toString()
        val raw = TelemetryView("chip", "", base, WidgetShape.VALUE, EvidenceTier.PROVEN,
            if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString())
        return UnitFormat.apply(raw, units).display
    }

    private fun chipLp() = LinearLayout.LayoutParams(WRAP, WRAP).also { it.marginStart = dp(Sp.S) }

    private fun chip(text: String, iconName: String?, color: String): TextView = TextView(activity).apply {
        this.text = text; setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f); gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(Sp.XS), 0, dp(Sp.XS), 0)   // KHÔNG viền pill — chip prototype chỉ icon + chữ
        maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
        applyChipFace(this, iconName, color.ifEmpty { CHIP_INK })
    }

    /** Đặt màu chữ + icon dẫn đầu cho một chip. Tách riêng để đổi được mà không dựng lại view. */
    private fun applyChipFace(v: TextView, iconName: String?, color: String) {
        v.setTextColor(c(color))
        val r = iconName?.let { KachiTheme.iconRes(it) } ?: 0
        if (r != 0) {
            val d = activity.resources.getDrawable(r, activity.theme)
                .apply { setBounds(0, 0, dp(Sp.ICON_XS), dp(Sp.ICON_XS)); setTint(c(color)) }
            v.setCompoundDrawablesRelative(d, null, null, null); v.compoundDrawablePadding = dp(Sp.S)
        } else {
            v.setCompoundDrawablesRelative(null, null, null, null)
        }
    }

    /**
     * Làm mới chip xe theo **cấu hình** [TopStripConfig] (RW0 vùng thứ ba). Trước 2026-09-11 chỗ này là **3 chip viết
     * cứng** nên thanh trên là vùng duy nhất người dùng không sửa được; nay danh sách chip là cấu hình bền, còn việc
     * quyết định *chữ gì* nằm ở `:core` ([TopStripChips]) nên kiểm được off-car.
     *
     * **Dựng một lần, sau đó chỉ đổi CHỮ** — giữ nguyên bản vá [SOÁT P2-9]: bản trước gọi `removeAllViews()` rồi dựng
     * lại 3 `TextView` (kèm tra + tint drawable) **mỗi nhịp trạng thái xe**, tức mỗi giây trên xe cho dữ liệu phần lớn
     * không đổi. Chỉ dựng lại khi **danh sách chip đổi** (người dùng vừa sửa cấu hình).
     *
     * R11: mọi số đi qua lớp đơn vị. [ĐO] máy ảo bản đầu: người dùng chọn °F mà chip vẫn ghi °C, vì bề mặt này dựng
     * chuỗi trực tiếp từ [CarStatus] — đúng bệnh "mỗi bề mặt tự đổi đơn vị theo ý mình".
     */
    fun refreshChips(status: CarStatus, units: UnitPrefs = UnitPrefs.DEFAULT, config: TopStripConfig = chipConfig) {
        chipConfig = config
        val chips = TopStripChips.render(config, status, units)
        if (chipViews.size != chips.size) {                 // danh sách đổi (hoặc lượt đầu) ⇒ dựng lại
            chipRow.removeAllViews(); chipViews.clear()
            chips.forEach { chipRow.addView(chip("", null, "").also { v -> chipViews.add(v) }, chipLp()) }
        }
        chips.forEachIndexed { idx, c ->
            val v = chipViews[idx]
            val color = when (c.tone) {
                ChipTone.ENERGY -> KachiTheme.GREEN
                ChipTone.NEUTRAL -> CHIP_INK
            }
            // Icon/màu chỉ đặt lại khi ĐỔI — tra drawable + tint mỗi giây là việc bản vá P2-9 vừa dọn.
            if (v.tag != c.icon.toString() + color) {
                applyChipFace(v, c.icon, color)
                v.tag = c.icon.toString() + color
            }
            v.text = c.text
            v.contentDescription = c.desc
        }
    }

    private var chipConfig: TopStripConfig = TopStripConfig.DEFAULT
    private val chipViews = ArrayList<TextView>()

    // ── Hồ sơ tài xế: avatar hiện chữ đầu, chạm = đổi hồ sơ ──
    /**
     * S1 — **không còn `setOnLongClickListener`**: tạo hồ sơ nay ở Cài đặt → Hồ sơ tài xế (§4.5). Giữ giữ-để-tạo ở
     * đây thì có hai đường tạo cho cùng một việc, và cử chỉ giữ trên một đích 30dp giữa lúc lái là chỗ dễ bấm nhầm.
     */
    private fun profileAvatar(): TextView {
        profileAvatarView = TextView(activity).apply {
            setTextColor(c(KachiTheme.ON_ACCENT))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f); typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            // T5 — avatar 30dp → Sp.TOUCH. KDoc phía trên đã nói "một đích 30dp giữa lúc lái là chỗ dễ bấm nhầm"
            // rồi kết luận bỏ cử chỉ GIỮ; nhưng cú CHẠM (đổi hồ sơ) vẫn ở lại trên đúng đích 30dp đó. Nới đích
            // mới là chữa nguyên nhân, bỏ cử chỉ chỉ là bớt hậu quả.
            val s = dp(Sp.TOUCH); width = s; height = s; background = KachiTheme.gradient(activity, Sp.RADIUS_PILL)
            setOnClickListener { onProfileTap() }
        }
        return profileAvatarView
    }

    /** Chữ cái đầu của hồ sơ [profileName] lên avatar (do render / init gọi). */
    fun setProfileInitial(profileName: String) {
        profileAvatarView.text = profileName.take(1).uppercase()
    }

    /** Cập nhật đồng hồ + ngày (do vòng tick / onResume gọi). */
    fun updateClock() {
        clock.text = SimpleDateFormat("HH:mm", LangHost.locale()).format(Date())
        dateText.text = SimpleDateFormat("EEEE, dd/MM", LangHost.locale()).format(Date())
    }

    private fun dp(v: Int): Int = (v * activity.resources.displayMetrics.density).toInt()

    private companion object {
        const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

        /** Màu chữ chip trung tính. Bảng màu chỉ ở `:app` — `:core` chỉ nói SẮC THÁI (xem [ChipTone]). */
        val CHIP_INK: String get() = KachiTheme.INK2
    }
}
