package com.byd.clusternav.launcher

import android.app.Activity
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
 * Thanh trạng thái trên cùng của HOME: đồng hồ + ngày + chip xe (cấu hình được) + hai pill **chỉ-icon**
 * "Ứng dụng"/"Cài đặt" (S4 · R12 — xem [pill]) + **chip hồ sơ**. Tách khỏi [KachiHomeActivity] (B5b) để activity
 * còn là composition-root.
 *
 * THUẦN VIEW — KHÔNG giữ state launcher: mọi tương tác đẩy lên qua callback (một chiều), activity nối vào intent VM.
 * [setProfile]/[refreshChips]/[updateClock] do [KachiHomeActivity.render] / vòng tick gọi để phản chiếu state.
 *
 * ## S1 — MỘT cửa vào cấu hình
 * Trước S1 thanh này có **ba** bề mặt cấu hình: pill "Tuỳ biến" (bảng khả năng + đơn vị + hình nền + quyền), pill
 * "Cài đặt" (nhảy thẳng sang màn ClusterNav cũ) và pill **"Thanh"** (xoay vòng 4 viền của thanh nút xe, ghi bền
 * `dock_edge`). Ba chỗ cho cùng một loại việc, và không chỗ nào bày đủ. Nay chỉ còn **"Cài đặt"** → [SettingsPanel];
 * màn ClusterNav là **một nhóm bên trong** đó.
 *
 * ## ⚠⚠ S4 · R7 — 5 NÚT BỐ CỤC ĐÃ RỜI KHỎI ĐÂY
 * §4.5 của IA v2 cố ý giữ chúng lại (*"đổi bố cục là việc hằng ngày"*). S4 đảo quyết định đó, và lý do là một quyết
 * định **sản phẩm** của owner chứ không phải một lỗi kỹ thuật: hồ sơ tài xế nay giữ **tất cả** lựa chọn (bố cục · ô ·
 * thanh nút · chip · chủ đề · đơn vị · ngôn ngữ · cấu hình ClusterNav), nên *"đổi cả bộ"* là việc hằng ngày còn
 * *"đổi riêng bố cục"* thì lùi về mức chỉnh-một-lần. Owner 2026-09-14: *"bỏ luôn các nút đổi bố cục trên header"*.
 * Đường chọn bố cục sẵn còn **đúng một** chỗ: Cài đặt → Màn hình chính ([SettingsHomeSection.layout]) — cùng intent
 * `onPreset` như trước, nên không mất chức năng nào.
 *
 * Thứ **cố ý ở lại**: chip hồ sơ — đang ở hồ sơ nào là thông tin phải thấy liên tục, và từ S4 nó còn là đường đổi
 * **cả bộ cấu hình**. Chạm nó KHÔNG xoay vòng nữa (xem [ProfileChip.picker]): nó mở bộ chọn có tên từng hồ sơ, và
 * cả lối *"Quản lý hồ sơ…"* sang Cài đặt → Hồ sơ tài xế. Việc *tạo/xoá* hồ sơ vẫn chỉ ở Cài đặt.
 *
 * Giới hạn của thanh này là **khoá lưu bền nào được phép chạm**, không phải "bao nhiêu pill": từ S4 chỉ còn
 * `active_profile` (chip hồ sơ) — xem [SettingsCatalog.TOP_STRIP_ALLOWED_KEYS], nơi khai lý do, và
 * `TopStripSurfaceContractTest` canh cả hai đầu.
 */
class KachiTopStrip(
    private val activity: Activity,
    private val onOpenSettings: () -> Unit,
    private val onProfileTap: () -> Unit,
    private val onOpenAppList: () -> Unit = {},   // U3: lối vào "Mở ứng dụng" (mở app toàn màn, không gắn ô)
) {
    private lateinit var clock: TextView
    private lateinit var dateText: TextView
    private lateinit var chipRow: LinearLayout
    private lateinit var profileInitialView: TextView
    private lateinit var profileNameView: TextView

    /** View thanh trạng thái (dựng lười một lần). */
    val view: View by lazy { build() }

    private fun build(): View {
        val strip = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = KachiTheme.card(context, Sp.RADIUS_L, KachiTheme.BAR_TOP, KachiTheme.LINE_STRONG)   // thanh mờ bo góc + viền rõ (prototype)
            setPadding(dp(Sp.L), dp(Sp.XS), dp(Sp.L), dp(Sp.XS))
        }
        clock = TextView(activity).apply {
            setTextColor(c(KachiTheme.INK)); KachiType.apply(this, KachiType.SECTION, bold = true); letterSpacing = 0.02f
        }
        dateText = TextView(activity).apply { setTextColor(c(KachiTheme.MUT)); KachiType.apply(this, KachiType.CAPTION); setPadding(dp(Sp.M), 0, 0, 0) }
        strip.addView(clock); strip.addView(dateText)
        // S4 · R7 — hàng 5 nút bố cục đã BỎ ở đây (nó từng đứng giữa ngày và khoảng đệm). Không thay bằng gì.
        //
        // ⚠⚠ S4 · R11 (b) — KHOẢNG ĐỆM CO GIÃN ĐÃ NHẬP VÀO CHÍNH HÀNG CHIP, và đó là phần cốt lõi của bản vá.
        // Trước đây thanh có một `View` đệm riêng mang `weight = 1`, còn hàng chip thì `WRAP_CONTENT`. Sắp như thế
        // thì LinearLayout đo hàng chip TRƯỚC ba vật bên phải (Ứng dụng · Cài đặt · chip hồ sơ) ⇒ 8 chip ăn hết
        // bề rộng và ba vật kia bị **ép về 0 / đẩy khỏi mép** — đúng cái tràn mà R11 (b) cấm. Nay hàng chip LÀ
        // phần co giãn (`0dp + weight 1`): mọi vật khác được đo ở bề rộng tự nhiên trước, phần **còn lại** rơi vào
        // đây, nên hàng chip không bao giờ lấn sang chúng dù có bao nhiêu chip. Chip căn END ⇒ nhìn y như cũ khi
        // còn dư chỗ. Phần chia đều chỗ ấy cho từng chip nằm ở [fitChips].
        chipRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.END or Gravity.CENTER_VERTICAL
            // Bề rộng còn lại chỉ biết được SAU một lượt bố cục (và nó đổi khi tên hồ sơ dài ra / ngôn ngữ đổi /
            // màn đổi kích thước) ⇒ nghe theo bố cục thật thay vì đoán trước. So bề rộng cũ↔mới để không chạy lại
            // ở mỗi lượt bố cục do chính [fitChips] gây ra.
            addOnLayoutChangeListener { _, l, _, r, _, ol, _, or, _ -> if (r - l != or - ol) fitChips() }
        }
        strip.addView(chipRow, LinearLayout.LayoutParams(0, WRAP, 1f))
        strip.addView(pill("ic-apps", R.string.kachi_pill_apps, false) { onOpenAppList() }, pillLp())   // U3: mở app toàn màn
        strip.addView(pill("ic-settings", R.string.kachi_pill_settings, true) { onOpenSettings() }, pillLp())
        strip.addView(profileChip(), LinearLayout.LayoutParams(WRAP, WRAP).also { it.marginStart = dp(Sp.SLOT_GAP) })
        refreshChips(CarStatus())
        return strip
    }

    private fun pillLp() = LinearLayout.LayoutParams(WRAP, WRAP).also { it.marginStart = dp(Sp.S) }

    /**
     * Pill bấm được của thanh trên ("Ứng dụng" · "Cài đặt") — **CHỈ ICON** từ S4 · R12.
     *
     * ## Vì sao bỏ chữ (owner 2026-09-14: *"đổi chữ Ứng Dụng, Cài Đặt thành icon luôn cho gọn"*)
     * [ĐO] hai pill chữ chiếm ≈ 200dp bề ngang của thanh trên. Cùng lượt R11 vừa nâng trần chip 4 → 8 và giao
     * **toàn bộ chỗ còn lại** cho hàng chip ([fitChips] chia đều), nên 200dp đó là ≈ 25dp/chip — đủ để một chip
     * nữa đọc được thay vì hiện `…`. Hai việc này thuộc CÙNG một thanh: chữ ở đây là chỗ của chip.
     *
     * ## Chữ không mất, nó chuyển vai
     * `contentDescription` lấy **đúng khoá tài nguyên cũ** (`kachi_pill_apps` / `kachi_pill_settings`, VI+EN) ⇒
     * TalkBack và phép kiểm giao diện vẫn đọc ra "Ứng dụng"/"Settings"; chỉ phần VẼ là hình. Dùng lại khoá cũ
     * thay vì thêm khoá `*_desc` mới: hai chuỗi cho cùng một cái tên sẽ lệch nhau ở đúng lần ai đó sửa một chỗ.
     *
     * ## Đích chạm KHÔNG được co theo icon
     * **T5** đã nới pill từ ~30dp lên [Sp.TOUCH]; bỏ chữ đi thì bề NGANG cũng tụt xuống cỡ icon (20dp) nếu chỉ
     * dựa vào lề trong ⇒ đặt cả `minimumWidth` lẫn `minimumHeight` = [Sp.TOUCH]. `CENTER_INSIDE` để hình giữ đúng
     * cỡ vẽ (không bị phóng to lấp đầy vùng chạm — vùng chạm to hơn hình là CỐ Ý).
     */
    private fun pill(icon: String, descRes: Int, primary: Boolean, onClick: () -> Unit) = ImageView(activity).apply {
        KachiTheme.iconRes(icon).let { if (it != 0) setImageResource(it) }
        contentDescription = activity.getString(descRes)
        scaleType = ImageView.ScaleType.CENTER_INSIDE
        minimumWidth = dp(Sp.TOUCH); minimumHeight = dp(Sp.TOUCH)
        setPadding(dp(Sp.M), dp(Sp.S), dp(Sp.M), dp(Sp.S))
        if (primary) { background = KachiTheme.gradient(context, Sp.RADIUS_PILL); setColorFilter(c(KachiTheme.ON_ACCENT)) }
        else { background = KachiTheme.pill(context); setColorFilter(c(KachiTheme.INK)) }
        setOnClickListener { onClick() }
    }

    private fun chipLp() = LinearLayout.LayoutParams(WRAP, WRAP).also { it.marginStart = dp(Sp.S) }

    private fun chip(text: String, iconName: String?, color: String): TextView = TextView(activity).apply {
        this.text = text; KachiType.apply(this, KachiType.BODY); gravity = Gravity.CENTER_VERTICAL
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
     *
     * **S4 · R11 (b)**: trần chip lên 8 ⇒ hàng chip có thể dài hơn chỗ còn lại. Bề rộng từng chip do [fitChips]
     * chia; ở đây chỉ gọi lại nó khi **số chip** đổi (bề rộng đổi thì `addOnLayoutChangeListener` tự gọi).
     */
    fun refreshChips(status: CarStatus, units: UnitPrefs = UnitPrefs.DEFAULT, config: TopStripConfig = chipConfig) {
        chipConfig = config
        val chips = TopStripChips.render(config, status, units)
        if (chipViews.size != chips.size) {                 // danh sách đổi (hoặc lượt đầu) ⇒ dựng lại
            chipRow.removeAllViews(); chipViews.clear()
            chips.forEach { chipRow.addView(chip("", null, "").also { v -> chipViews.add(v) }, chipLp()) }
            // Chip mới dựng chưa có trần bề rộng nào, và số chip vừa đổi ⇒ phần chia phải tính lại. Quên `-1` thì
            // chốt "cap == chipCap" ở [fitChips] có thể bỏ qua đúng lượt cần đặt (cùng số nhưng view khác).
            chipCap = -1
            fitChips()
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

    /**
     * S4 · R11 (b) — **CHIA CHỖ CÒN LẠI CHO TỪNG CHIP** để thanh trên không bao giờ tràn.
     *
     * ## Bề rộng "còn lại" là bao nhiêu, và vì sao không tự cộng trừ ra nó
     * Đúng công thức của R11: `strip − (đồng hồ + ngày) − (chip hồ sơ + Ứng dụng + Cài đặt) − lề`. Nhưng tự cộng
     * các số hạng đó ở đây nghĩa là đọc `width` của những view **có thể đã bị ép** (LinearLayout đo theo thứ tự,
     * hàng chip đứng trước ba vật bên phải) — tức đo lại chính triệu chứng. Nên phép trừ được **giao cho bố cục**:
     * hàng chip khai `0dp + weight 1` nên `chipRow.width` CHÍNH LÀ phần còn lại sau khi mọi vật khác đã lấy đủ bề
     * rộng tự nhiên. Một nguồn số, không có bản sao để lệch.
     *
     * ## Chia đều, không chia theo độ dài chữ
     * [SUY] 1920px: phần còn lại ≈ 1240dp ⇒ 8 chip được ≈ 155dp/chip — `"82% · 418 km"` thừa chỗ, `"Lốp TT · 2.4 bar"`
     * cắt đuôi một chút. Chia theo độ dài chữ thì chip **đổi bề rộng mỗi khi số đổi** (mỗi giây trên xe) ⇒ cả hàng
     * nhảy qua nhảy lại; chia đều thì mép chip đứng yên, chỉ phần đuôi chữ co lại. Trên thanh trạng thái của một
     * chiếc xe đang chạy, ổn định quan trọng hơn tiết kiệm vài pixel.
     *
     * ## KHÔNG cấp phát gì trong vòng tick
     * Đây là số nguyên + một phép gán thuộc tính; không tra drawable, không dựng paint, không sinh đối tượng (xem
     * KDoc lớp và bản vá [SOÁT P2-9]). Và chốt `cap == chipCap` giữ cho `maxWidth` — thứ gọi `requestLayout()` —
     * chỉ chạy khi con số thật sự đổi, nếu không thì mỗi lượt bố cục lại đẻ ra một lượt bố cục nữa.
     */
    private fun fitChips() {
        val n = chipViews.size
        if (n == 0) return
        val room = chipRow.width
        if (room <= 0) return                               // chưa qua lượt bố cục nào ⇒ listener sẽ gọi lại
        // Mỗi chip mang lề trái [Sp.S] (xem [chipLp]) nên phần chữ được hưởng là suất chia trừ đi lề đó. Sàn
        // [Sp.ICON_M]: hẹp hơn nữa thì chip chỉ còn "…" — giữ một mức mà icon + dấu cắt vẫn đọc ra là một chip.
        val cap = (room / n - dp(Sp.S)).coerceAtLeast(dp(Sp.ICON_M))
        if (cap == chipCap) return
        chipCap = cap
        chipViews.forEach { it.maxWidth = cap }
    }

    private var chipConfig: TopStripConfig = TopStripConfig.DEFAULT
    private val chipViews = ArrayList<TextView>()

    /** Trần bề rộng đang áp cho mỗi chip (px). `-1` = chưa tính / vừa dựng lại hàng chip. */
    private var chipCap = -1

    // ── Hồ sơ tài xế: chip CHỮ CÁI + TÊN, chạm = mở bộ chọn hồ sơ ──
    /**
     * Chip hồ sơ — **chữ cái đầu + TÊN hồ sơ** (S4 · R7), thay cho avatar chỉ-một-chữ-cái của S1.
     *
     * ## Vì sao phải có cả cái TÊN, không chỉ chữ cái
     * Từ S4 hồ sơ giữ **tất cả** lựa chọn, nên "đang ở hồ sơ nào" là câu hỏi có hệ quả lên cả màn hình (bố cục · ô ·
     * chủ đề · đơn vị · cấu hình ClusterNav). Một chữ cái trả lời được câu đó chỉ khi người dùng đã thuộc lòng chữ
     * đầu của từng hồ sơ — mà hai hồ sơ *"Đi làm"* / *"Đường trường"* thì cùng chữ `Đ`. [ĐO] không cần xe để thấy:
     * `ProfileNames.initial` = `display(name).take(1)`, nên trùng chữ đầu là ca thường, không phải ca hiếm.
     *
     * Cử chỉ: **chạm = MỞ BỘ CHỌN**, không xoay vòng (`ProfileBar.cycle` đã xoá). Xoay vòng trên một bộ giữ toàn bộ
     * cấu hình là cú chạm nguy hiểm nhất của launcher: bấm nhầm một cái thì cả màn hình đổi và người lái không biết
     * mình vừa đi tới hồ sơ nào trong danh sách. Bộ chọn bày tên, đánh dấu hồ sơ đang dùng, và có cả lối *"Quản lý
     * hồ sơ…"* — xem [ProfileChip].
     *
     * Đích chạm: cả chip cao ≥ [Sp.TOUCH] (`minimumHeight`, không phải bằng lề trong — lề trong đẩy chữ ra xa viền).
     * Tên hồ sơ kẹp ở [Sp.LABEL_COL] + một dòng + `…`: tên người dùng tự đặt có thể dài tuỳ ý, mà thanh trên thì
     * không được đẩy pill "Cài đặt" ra khỏi mép.
     */
    private fun profileChip(): View = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        background = KachiTheme.pill(context)
        minimumHeight = dp(Sp.TOUCH)
        setPadding(dp(Sp.XS), 0, dp(Sp.M), 0)
        profileInitialView = TextView(activity).apply {
            setTextColor(c(KachiTheme.ON_ACCENT))
            KachiType.apply(this, KachiType.BODY, bold = true); gravity = Gravity.CENTER
            val s = dp(Sp.ICON_L); width = s; height = s; background = KachiTheme.gradient(activity, Sp.RADIUS_PILL)
        }
        profileNameView = TextView(activity).apply {
            setTextColor(c(KachiTheme.INK)); KachiType.apply(this, KachiType.BODY, bold = true)
            maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END; maxWidth = dp(Sp.LABEL_COL)
            setPadding(dp(Sp.S), 0, 0, 0)
        }
        addView(profileInitialView); addView(profileNameView)
        setOnClickListener { onProfileTap() }
    }

    /**
     * Chữ cái đầu **và tên** của hồ sơ [profileName] lên chip (do render / init gọi).
     *
     * [SOÁT P3-4] Cả hai lấy theo **NHÃN** (đã dịch), không theo khoá lưu: máy tiếng Anh hiện `D` / *Default*, không
     * phải `M` / *Mặc định*. Tên GỐC vẫn là thứ duy nhất đi vào prefs — xem KDoc [ProfileNames].
     */
    fun setProfile(profileName: String) {
        profileInitialView.text = ProfileNames.initial(profileName)
        profileNameView.text = ProfileNames.display(profileName)
        profileNameView.contentDescription = activity.getString(R.string.kachi_profile_chip_desc, ProfileNames.display(profileName))
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
