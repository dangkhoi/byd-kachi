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
import com.byd.clusternav.launcher.KachiBars as Bars
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
    /**
     * V1 pha NGHE (R12 b) — chạm nút mic. CÙNG lambda mà ô *Nói với xe* trên thanh nút dùng
     * (`KachiHomeWiring.controlDock`), không đường thứ hai.
     */
    private val onVoice: () -> Unit = {},
    /**
     * Có vẽ nút mic không — hỏi **mỗi lần dựng**, không nhận một `Boolean` chụp sẵn.
     *
     * Hai điều kiện, và cả hai đổi được sau khi thanh này đã dựng: người dùng bật/tắt ở *Cài đặt › Thanh trạng
     * thái*, và **mô hình đã tải hay chưa**. Vẽ một nút mic trên máy chưa có mô hình là hứa một việc mà bấm vào
     * chỉ nhận được câu *"chưa tải mô hình"* — thà chưa có nút.
     */
    private val voicePillEnabled: () -> Boolean = { false },
    /**
     * UX-OVERHAUL · WP4 — **THỨ TỰ các vật trên thanh**, hỏi mỗi lần dựng (không nhận một [HeaderLayout] chụp sẵn).
     *
     * Cùng lẽ [voicePillEnabled] ngay trên: [view] là `by lazy` nên thanh dựng một lần cho mỗi vòng đời màn, mà
     * thứ tự thì đổi được **sau** đó (người dùng bấm ◀/▶ ở Cài đặt, hoặc đổi hồ sơ). Lượt đổi đi qua [setLayout];
     * lambda này chỉ trả lời câu *"lúc dựng thì đang ở thứ tự nào"*.
     *
     * ⚠ KHÔNG phải một cổng `on*`: thanh này **không ghi** thứ tự đi đâu cả (`TopStripSurfaceContractTest` canh
     * đúng điều đó). Đường ghi là màn Cài đặt → `HomeViewModel.setHeaderLayout` → prefs.
     */
    private val header: () -> HeaderLayout = { HeaderLayout.DEFAULT },
) {
    private lateinit var clock: TextView
    private lateinit var dateText: TextView
    private lateinit var chipRow: LinearLayout
    private lateinit var profileInitialView: TextView
    private lateinit var profileNameView: TextView

    /** Hàng ngang của thanh — giữ tham chiếu vì [place] gắn/tháo con của nó khi thứ tự đổi (WP4). */
    private lateinit var stripRow: LinearLayout

    /**
     * Một view cho MỖI vật của thanh, dựng đúng một lần ở [build].
     *
     * Bảng này (chứ không phải thứ tự `addView`) là chỗ *"vật nào tồn tại"*; [place] quyết *"nó đứng đâu"*. Tách
     * hai câu đó ra là toàn bộ nội dung kỹ thuật của WP4: `getValue` sẽ **nổ** nếu [HeaderItem] có thêm thành viên
     * mà [build] chưa dựng view cho nó — thà nổ ở lượt dựng đầu còn hơn thiếu im lặng một vật trên thanh.
     */
    private val items = HashMap<HeaderItem, View>()

    /** Thứ tự đang ĐẶT trên thanh — để [setLayout] bỏ qua lượt gọi không đổi gì (nó tháo/gắn cả hàng). */
    private var placed: HeaderLayout? = null

    /** View thanh trạng thái (dựng lười một lần). */
    val view: View by lazy { build() }

    private fun build(): View {
        val strip = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            // WP1 · R1.1 — thanh mờ bo góc, **KHÔNG viền**. [ĐO ảnh `after-home-dark.png`] viền cũ là vạch 1px
            // `rgb(100,106,121)` chạy từ x=45 tới x=1874 ở y=107 — một trong hai đường kẻ dễ thấy nhất màn chính
            // (owner: *"KHÔNG còn viền ở BẤT CỨ ĐÂU hết"*). Thanh vẫn tách khỏi nền bằng chính nền `BAR_TOP` của nó.
            background = KachiTheme.card(context, Sp.RADIUS_L, KachiTheme.BAR_TOP)
            setPadding(dp(Sp.L), dp(Sp.XS), dp(Sp.L), dp(Sp.XS))
        }
        stripRow = strip
        clock = TextView(activity).apply {
            setTextColor(c(KachiTheme.INK)); KachiType.apply(this, KachiType.SECTION, bold = true); letterSpacing = 0.02f
        }
        dateText = TextView(activity).apply { setTextColor(c(KachiTheme.MUT)); KachiType.apply(this, KachiType.CAPTION); setPadding(dp(Sp.M), 0, 0, 0) }
        // WP4 — đồng hồ + ngày là MỘT vật ([HeaderItem.CLOCK], xem KDoc ở đó): chúng đọc liền nhau, tách ra chỉ
        // mời người dùng dựng những thứ tự không ai muốn.
        items[HeaderItem.CLOCK] = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            addView(clock); addView(dateText)
        }
        // S4 · R7 — hàng 5 nút bố cục đã BỎ ở đây (nó từng đứng giữa ngày và khoảng đệm). Không thay bằng gì.
        //
        // ⚠⚠ S4 · R11 (b) — KHOẢNG ĐỆM CO GIÃN ĐÃ NHẬP VÀO CHÍNH HÀNG CHIP, và đó là phần cốt lõi của bản vá.
        // Trước đây thanh có một `View` đệm riêng mang `weight = 1`, còn hàng chip thì `WRAP_CONTENT`. Sắp như thế
        // thì LinearLayout đo hàng chip TRƯỚC ba vật bên phải (Ứng dụng · Cài đặt · chip hồ sơ) ⇒ 8 chip ăn hết
        // bề rộng và ba vật kia bị **ép về 0 / đẩy khỏi mép** — đúng cái tràn mà R11 (b) cấm. Nay hàng chip LÀ
        // phần co giãn (`0dp + weight 1`, xem [lpFor]): mọi vật khác được đo ở bề rộng tự nhiên trước, phần **còn
        // lại** rơi vào đây, nên hàng chip không bao giờ lấn sang chúng dù có bao nhiêu chip. Phần chia đều chỗ ấy
        // cho từng chip nằm ở [fitChips].
        chipRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            // Bề rộng còn lại chỉ biết được SAU một lượt bố cục (và nó đổi khi tên hồ sơ dài ra / ngôn ngữ đổi /
            // màn đổi kích thước) ⇒ nghe theo bố cục thật thay vì đoán trước. So bề rộng cũ↔mới để không chạy lại
            // ở mỗi lượt bố cục do chính [fitChips] gây ra.
            addOnLayoutChangeListener { _, l, _, r, _, ol, _, or, _ -> if (r - l != or - ol) fitChips() }
        }
        items[HeaderItem.CHIPS] = chipRow
        // V1 pha NGHE — nút mic. Thứ tự **DỰNG** ở đây là thứ tự mặc định (nói → ứng dụng → cài đặt); thứ tự
        // **ĐẶT** trên thanh do [HeaderLayout] quyết (WP4). Chỉ-icon + đích chạm [Bars.HEADER_BTN] (xem [pill]).
        //
        // [SOÁT Pass 2 · P2] Luôn GẮN, ẩn/hiện bằng `visibility` — xem [refreshVoicePill].
        voicePill = pill("ic-mic", R.string.kachi_pill_voice, false) { onVoice() }
            .also { items[HeaderItem.VOICE] = it }
        refreshVoicePill()
        items[HeaderItem.APPS] = pill("ic-apps", R.string.kachi_pill_apps, false) { onOpenAppList() }   // U3
        items[HeaderItem.SETTINGS] = pill("ic-settings", R.string.kachi_pill_settings, true) { onOpenSettings() }
        items[HeaderItem.PROFILE] = profileChip()
        place(header())
        refreshChips(CarStatus())
        return strip
    }

    /**
     * UX-OVERHAUL · WP4 — **ĐẶT LẠI CHỖ** các vật theo [layout]. Do `KachiHomeActivity.render` gọi khi state đổi.
     *
     * Chỉ **sắp lại** view đã dựng (`removeAllViews` + gắn lại), KHÔNG dựng lại chúng. Dựng lại sẽ mất chữ đang
     * hiện trên chip và tên trên chip hồ sơ, và mỗi lần bấm ◀/▶ lại tra + tint lại từng drawable — đúng việc mà
     * bản vá [SOÁT P2-9] vừa dọn khỏi đường nóng.
     */
    fun setLayout(layout: HeaderLayout) {
        if (!this::stripRow.isInitialized || layout == placed) return
        place(layout)
    }

    private fun place(layout: HeaderLayout) {
        placed = layout
        // Hàng chip là phần co giãn; nó hút chỗ trống, nên chỗ trống nằm TRƯỚC hay SAU chip là do căn lề của chính
        // nó. Luật ở `:core` ([HeaderLayout.chipsAlignEnd]) để kiểm được off-car.
        chipRow.gravity =
            (if (layout.chipsAlignEnd) Gravity.END else Gravity.START) or Gravity.CENTER_VERTICAL
        stripRow.removeAllViews()
        layout.order.forEach { item -> stripRow.addView(items.getValue(item), lpFor(item)) }
        // Chip vừa đổi chỗ ⇒ bề rộng còn lại của hàng chip đổi. `-1` để chốt `cap == chipCap` ở [fitChips] không
        // bỏ qua lượt đặt lại (bề rộng mới có thể tình cờ bằng bề rộng cũ ở một cấu hình khác).
        chipCap = -1
    }

    /**
     * Cách đặt của từng vật — **chỉ phụ thuộc LOẠI vật, không phụ thuộc chỗ nó đứng**.
     *
     * Đó là điều khiến WP4 an toàn: đổi thứ tự không đổi *cách* một vật chiếm chỗ, nên không có tổ hợp thứ tự nào
     * làm thanh tràn (hàng chip vẫn là phần duy nhất co giãn, mọi vật khác vẫn `WRAP_CONTENT`).
     */
    private fun lpFor(item: HeaderItem): LinearLayout.LayoutParams = when (item) {
        HeaderItem.CHIPS -> LinearLayout.LayoutParams(0, WRAP, 1f)
        HeaderItem.CLOCK -> LinearLayout.LayoutParams(WRAP, WRAP)
        // Chip hồ sơ mang khe rộng hơn ba pill: nó là vật duy nhất có CHỮ nên cần tách khỏi hàng icon để đọc ra
        // là một vật khác loại.
        HeaderItem.PROFILE -> LinearLayout.LayoutParams(WRAP, WRAP).also { it.marginStart = dp(Sp.SLOT_GAP) }
        else -> pillLp()
    }

    /** Nút mic — giữ tham chiếu vì nó ẩn/hiện theo hai thứ đổi được lúc đang chạy (xem [refreshVoicePill]). */
    private var voicePill: View? = null

    /**
     * Ẩn/hiện nút mic theo [voicePillEnabled] — gọi lại mỗi khi lớp phủ Cài đặt đóng/mở và mỗi lần màn quay lại.
     *
     * ## [SOÁT Pass 2 · P2] Vì sao không thể quyết một lần lúc dựng
     * [view] là `by lazy` ⇒ thanh trên dựng **một lần cho mỗi vòng đời màn chính**. Nhưng hai điều kiện của nút
     * mic đều đổi được **sau** lúc dựng, và đổi ngay trong màn Cài đặt đang phủ lên chính thanh này: người dùng
     * bấm *Tải mô hình tiếng Việt* (xong sau ~1 phút), hoặc gạt công tắc *Nút mic* ở **Thanh trạng thái**. Quyết
     * một lần lúc dựng thì cả hai thao tác đó **không đổi gì trên màn** cho tới lần dựng lại màn chính — tức
     * đúng hình dạng "bấm nút mà màn hình không đổi gì" mà P9 đã trả giá một lần (bố cục sẵn).
     */
    fun refreshVoicePill() {
        voicePill?.visibility = if (voicePillEnabled()) View.VISIBLE else View.GONE
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
     * dựa vào lề trong ⇒ đặt cả `minimumWidth` lẫn `minimumHeight`.
     *
     * ## ⚠⚠ WP5 · R5.2 — đích chạm nay là [Bars.HEADER_BTN] (34dp), KHÔNG còn [Sp.TOUCH] (48dp)
     * Owner 2026-09-20: *"header … nút App+Voice+profile 70 %"*. Tính chất được canh **không đổi**: khai TƯỜNG MINH
     * cả hai chiều, không để bề ngang co theo hình. Nhưng con số thì xuống dưới mức tối thiểu 48dp — đánh đổi này
     * ghi đầy đủ ở KDoc [Bars.HEADER_BTN] (tóm lại: không nút nào ở đây bắn lệnh xe, bấm nhầm hoàn lại được bằng
     * Back). `FIT_CENTER` + lề trong [Bars.HEADER_BTN_PAD] cho hộp hình 18dp — hình co theo nút, không phải nút co
     * theo hình.
     */
    private fun pill(icon: String, descRes: Int, primary: Boolean, onClick: () -> Unit) = ImageView(activity).apply {
        KachiTheme.iconRes(icon).let { if (it != 0) setImageResource(it) }
        contentDescription = activity.getString(descRes)
        scaleType = ImageView.ScaleType.FIT_CENTER
        minimumWidth = dp(Bars.HEADER_BTN); minimumHeight = dp(Bars.HEADER_BTN)
        dp(Bars.HEADER_BTN_PAD).let { setPadding(it, it, it, it) }
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
            v.setCompoundDrawablesRelative(d, null, null, null)
            // B6 (owner 2026-09-22): chip CHỈ-ICON (không chữ, vd trạng thái sấy) không cần khoảng đệm icon↔chữ —
            // để nguyên thì icon thừa lề phải. Chỉ đệm khi có chữ.
            v.compoundDrawablePadding = if (v.text.isNullOrEmpty()) 0 else dp(Sp.S)
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
                // Trạng thái bật/tắt của datum boolean = MÀU, không phải chữ (owner 2026-09-21). Cả chữ lẫn icon đổi
                // màu vì [applyChipFace] tint icon bằng CHÍNH màu này — đó là thứ làm "icon sáng / icon mờ".
                //
                // Vì sao hai vai này: [KachiTheme.ACCENT_INK] là vai *"màu nhấn dùng làm CHỮ"* — [ĐO] `accent` thuần
                // (`#4c7dff`) làm chữ thì không đạt tương phản, nên bảng màu đã tách riêng vai này và cho nó đi qua
                // `ContrastGuard.fitInk`. [KachiTheme.MUT2] là vai chữ mờ nhất còn đạt sàn tương phản. Dùng lại hai
                // vai có sẵn thay vì thêm vai mới: "mờ" và "nhấn" đã được định nghĩa và đã được bài canh tương phản
                // đo ở CẢ HAI bảng (tối + sáng) — thêm vai mới là thêm hai hex phải tự chứng minh lại.
                ChipTone.ACTIVE -> KachiTheme.ACCENT_INK
                ChipTone.INACTIVE -> KachiTheme.MUT2
                ChipTone.NEUTRAL -> CHIP_INK
            }
            // Icon/màu chỉ đặt lại khi ĐỔI — tra drawable + tint mỗi giây là việc bản vá P2-9 vừa dọn.
            if (v.tag != c.icon.toString() + color) {
                applyChipFace(v, c.icon, color)
                v.tag = c.icon.toString() + color
            }
            // H5 (PERF 2026-09-16) — cùng luật với dòng icon/màu ngay trên: `setText` với CHÍNH chuỗi đang hiện
            // vẫn dựng lại `Layout` của TextView và gọi `requestLayout()`. Trên xe, trạng thái đổi kéo theo cả
            // dải chip vẽ lại dù phần lớn chip (bụi mịn · nhiệt độ ngoài) đứng yên hàng phút. So chuỗi rẻ hơn
            // nhiều lần so với đo-và-sắp lại một hàng 8 chip.
            if (v.text?.toString() != c.text) v.text = c.text
            if (v.contentDescription?.toString() != c.desc) v.contentDescription = c.desc
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
        // B6 (owner 2026-09-22): KHÔNG cap đều `room/n` — cách cũ ép MỌI chip cùng bề rộng nên chip dài
        // ("15.9 kWh/50km") bị cắt bằng chip ngắn ("18°C"). Chip nay **rộng theo nội dung** (WRAP + margin ở
        // [chipLp]); chỉ đặt một TRẦN RỘNG RÃI cho chip cá biệt quá dài (nửa hàng) để một chip khổng lồ không
        // đẩy hết chip khác ra. Chip ngắn giữ ngắn; hàng chip là `0dp+weight1` nên nếu tổng vượt room thì hệ
        // thống tự cắt chip cuối — không phải cắt đều mọi chip.
        val cap = (room / 2).coerceAtLeast(dp(Sp.ICON_M) * 4)
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
        // WP5 · R5.2 — chip hồ sơ là một NÚT ⇒ cùng đích chạm với ba pill ([Bars.HEADER_BTN], 70 % của [Sp.TOUCH]).
        minimumHeight = dp(Bars.HEADER_BTN)
        setPadding(dp(Sp.XS), 0, dp(Sp.M), 0)
        profileInitialView = TextView(activity).apply {
            setTextColor(c(KachiTheme.ON_ACCENT))
            KachiType.apply(this, KachiType.BODY, bold = true); gravity = Gravity.CENTER
            // WP5 — đĩa chữ-cái-đầu 70 % ([Bars.HEADER_AVATAR]): giữ 32dp trong một chip cao 34dp thì đĩa ăn gần
            // trọn bề cao và chip đọc ra như một nút tròn dính hai mép.
            val s = dp(Bars.HEADER_AVATAR); width = s; height = s
            background = KachiTheme.gradient(activity, Sp.RADIUS_PILL)
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
