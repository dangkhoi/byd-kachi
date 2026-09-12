package com.byd.clusternav.launcher

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.byd.clusternav.R
import com.byd.clusternav.launcher.KachiTheme.c
import com.byd.clusternav.launcher.KachiTheme.dpi
import com.byd.clusternav.launcher.KachiSpace as Sp

/**
 * Mọi thứ màn Cài đặt cần biết, gom một chỗ (S1).
 *
 * Vì sao là một lớp giữ **lambda + hàm đọc state** chứ không phải 20 tham số hàm dựng như bảng cũ: bảng cũ nhận
 * **ảnh chụp** giá trị lúc mở (`unitPrefs`, `wallpaper`, `topStrip`…), nên sau khi state đổi thì bảng đang mở nói
 * sai — chấp nhận được khi bảng chỉ sống một lượt, nhưng màn Cài đặt có **7 nhóm dựng lười và được nhớ lại**
 * ([SettingsPanel.pages]) nên một trang dựng lại phải thấy giá trị MỚI. Đọc qua [state] thì mỗi lượt dựng đều lấy
 * từ nguồn sự thật duy nhất, không có bản sao nào để lệch.
 *
 * ⚠ **Không có hàm nào ở đây ghi bền.** Tầng UI 0 lần ghi bền trực tiếp (luật kiến trúc đang có): mọi thay đổi của
 * phía **launcher** đi qua intent của `HomeViewModel` mà [KachiHomeActivity] nối vào các lambda dưới đây. Mọi thay
 * đổi của phía **ClusterNav** đi qua [bridge] — một lớp, có KDoc từng hàm chỉ tới dòng gốc ở màn cũ (IA v2 · N2).
 * Hai đường, hai luật, không có đường thứ ba: lớp này không tự chạm `Prefs` hay `SharedPreferences` lần nào.
 *
 * @param state nguồn sự thật (đọc mới mỗi lượt dựng trang).
 * @param permissions báo cáo vòng kiểm quyền (P8) — đọc lúc dựng trang, không giữ ảnh chụp cũ.
 * @param wallpaperFolderHint chỗ bỏ ảnh vào; người dùng không có cách nào tự đoán và màn chọn tệp của hệ thống bị
 *   khoá trên xe.
 * @param onAddProfile mở hộp thoại tạo hồ sơ. **Cố ý là lambda dùng LẠI `ProfileBar.addDialog()`** thay vì dựng hộp
 *   thoại thứ hai ở đây: hai bản dựng hộp thoại là hai chỗ phải sửa và sẽ lệch nhau (§4.5 dùng đúng lập luận này
 *   cho 5 nút bố cục ở thanh trên — nhiều bề mặt được, nhưng phải đi **cùng một** đường).
 */
class SettingsDeps(
    val state: () -> HomeUiState,
    val permissions: () -> PermissionReport,
    val wallpaperFolderHint: String,
    /**
     * IA v2 · §4.2 — **cầu DUY NHẤT** sang mọi cấu hình/hành động của ClusterNav (nav · cast · phím · ghế ·
     * PM2.5 · lấy gió trong · khởi động nền · cập nhật · mở màn nâng cao).
     *
     * Một tham số thay cho ~60 lambda: khác với các lambda phía launcher (chúng là **intent** của ViewModel, và
     * danh sách của chúng chính là hợp đồng "UI không ghi bền"), phía ClusterNav đã có một lớp chịu trách nhiệm
     * đó rồi. Bọc lại thành lambda ở đây chỉ là một tầng chép-tên thứ hai — và tầng đó sẽ lệch.
     */
    val bridge: ClusterNavBridge,
    val onPreset: (LayoutPreset) -> Unit,
    val onOpenLayoutEditor: () -> Unit,
    val onWallpaper: (WallpaperPrefs) -> Unit,
    val onTopStrip: (String, Boolean) -> Unit,
    /**
     * T6 · R-UI (m) — mở **bộ chọn của ngăn kéo** ở chế độ chọn nút thanh xe.
     * `(tập đang bật, gọi lại khi Áp dụng)`; xem hợp đồng ở [DrawerController.openDockPicker].
     */
    val openDockPicker: (Set<String>, (Set<String>) -> Unit) -> Unit,
    /**
     * Đặt **cả** cấu hình thanh nút một lượt — thay `onToggleDock(id, on)` cũ.
     *
     * Bộ chọn trả về một TẬP, và gấp tập đó vào [DockConfig] là một quyết định thật ([DockSelection.apply]: chiều
     * TẮT + giữ thứ tự phần cũ). Cổng "bật/tắt từng mã" không diễn tả được chiều tắt hàng loạt, nên giữ nó lại
     * chỉ mời người sau viết vòng lặp một chiều — đúng bẫy mà KDoc [DockSelection] mô tả.
     */
    val onDockConfig: (DockConfig) -> Unit,
    val onDockEdge: (DockEdge) -> Unit,
    val onUnitPrefs: (UnitPrefs) -> Unit,
    val onThemeMode: (ThemeMode) -> Unit,
    val onLangMode: (LangMode) -> Unit,
    val onAutostart: (Boolean) -> Unit,
    val onSwitchProfile: (String) -> Unit,
    val onAddProfile: () -> Unit,
    val onDeleteProfile: (String) -> Unit,
    /**
     * P7 + P6 — bộ việc làm với **cảnh**. Một tham số thay vì năm lambda: xem KDoc [SceneActions] (và
     * [KachiHomeActivity] đang đúng trần 500 dòng).
     */
    val scenes: SceneActions,
)

/**
 * MÀN CÀI ĐẶT của launcher (S1) — vỏ: đầu bảng · **rail nhóm bên trái** · khung nội dung bên phải cuộn riêng.
 *
 * Gộp bốn bề mặt cấu hình rải rác trước đây (bảng "Tuỳ biến" · 5 nút bố cục ở thanh trên · giữ-avatar để tạo hồ sơ ·
 * pill "Cài đặt" nhảy sang màn ClusterNav) về **một** chỗ. Nhóm và thứ tự nhóm KHÔNG do lớp này quyết — đọc từ
 * [SettingsCatalog.GROUPS] ở `:core`, nơi có bài test canh *"mọi khoá lưu bền phải thuộc đúng một nhóm"*. Nếu tầng UI
 * tự liệt kê nhóm lần nữa thì phép kiểm đó mất hiệu lực với chính màn hình mà nó bảo vệ.
 *
 * ## Vì sao RAIL, không phải danh sách → trang con
 * Trên xe, mỗi lần lùi một cấp là một cú chạm thêm và người dùng đang ngồi trong xe. Rail cho thấy **toàn bộ** bản đồ
 * cài đặt ngay lần mở đầu — đó cũng là cách chứng minh cho owner rằng không còn cấu hình nào nằm ngoài (§4.2).
 *
 * ## Dựng LƯỜI + NHỚ LẠI, và vì sao điều đó không phải tối ưu sớm
 * Trang chỉ được dựng khi lần đầu chọn nhóm, rồi **giữ lại** trong [pages]:
 *  1. **R1** đòi *"chuyển nhóm không mất chỗ đang cuộn của nhóm khác"* — giữ chính thực thể `ScrollView` là cách duy
 *     nhất đạt được điều đó mà không phải tự nhớ toạ độ cuộn.
 *  2. Trang nào cũng đọc lại state + (với ba nhóm ClusterNav) đọc `Prefs`/HAL qua [ClusterNavBridge]. Dựng lại mỗi
 *     lần đổi nhóm là trả giá đó lại từ đầu cho một cú chạm rail.
 *  3. Bộ chọn chip ([TopStripPicker]) giữ **bảng tra `mã → view`** để tô lại ô ⇒ mỗi lượt dựng trang phải là một
 *     thực thể MỚI (ràng buộc *"một lưới = một bảng tiles"*). Giữ trang cũ trong bộ nhớ thay vì dựng thêm một bộ
 *     chọn thứ hai chính là điều ràng buộc đó muốn.
 *
 * Đổi lại: state đổi thì trang đã nhớ trở nên cũ ⇒ [invalidateAll] để chỗ gọi bỏ hết và dựng lại theo state mới
 * (đường một chiều: state đổi → `render` → gọi vào đây), thay vì lớp này tự đi thu thay đổi.
 */
class SettingsPanel(
    context: Context,
    private val deps: SettingsDeps,
    private val onClose: () -> Unit,
) : FrameLayout(context) {

    private val rows = SettingsRows(context)
    private val sections = SettingsSections(context, rows, deps)

    private val railCells = LinkedHashMap<SettingsGroup, LinearLayout>()
    private val pages = HashMap<SettingsGroup, View>()
    private val content = FrameLayout(context)
    private var current: SettingsGroup = SettingsCatalog.GROUPS.first()

    init {
        setBackgroundColor(c(KachiTheme.SCRIM_PANEL))
        isClickable = true
        setOnClickListener { onClose() }        // chạm ra ngoài = đóng (giữ đúng thói quen của bảng cũ)

        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = KachiTheme.card(context, Sp.RADIUS_XXL, KachiTheme.PANEL)
            setPadding(dpi(context, Sp.XXL), dpi(context, Sp.XL), dpi(context, Sp.XXL), dpi(context, Sp.XL))
            isClickable = true                  // chặn chạm lọt xuống lớp scrim bên dưới
        }
        panel.addView(head())

        val body = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        body.addView(
            // ⚠ [SOÁT ẢNH 2026-09-12] Thanh cuộn BẬT: rail lên 10 nhóm (IA v2 §4.1) nên nó cuộn được, mà không có
            // chỉ báo thì người dùng không có cách nào biết còn nhóm ở dưới — trên xe, thứ không thấy là thứ không
            // tồn tại. `isVerticalScrollBarEnabled = false` là mặc định cũ khi rail còn 7 nhóm và vừa một màn.
            ScrollView(context).apply { addView(rail()); isVerticalScrollBarEnabled = true },
            LinearLayout.LayoutParams(dpi(context, Sp.RAIL_COL), LinearLayout.LayoutParams.MATCH_PARENT),
        )
        body.addView(
            content,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                .also { it.marginStart = dpi(context, Sp.L) },
        )
        panel.addView(body, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        addView(
            panel,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).also {
                it.setMargins(dpi(context, Sp.XXL), dpi(context, Sp.XL), dpi(context, Sp.XXL), dpi(context, Sp.XL))
                it.gravity = Gravity.CENTER
            },
        )
        show(current)
    }

    /** Nhóm đang xem — chỗ gọi cần biết để nhật ký/đo, và để [invalidateAll] dựng lại đúng trang. */
    fun currentGroup(): SettingsGroup = current

    /**
     * Bảng bị tháo khỏi màn ⇒ trả lại tài nguyên sống NGOÀI cây view ([SettingsSections.dispose]).
     *
     * ## Vì sao móc vào `onDetachedFromWindow` chứ không vào `onClose`
     * `onClose` là **một** đường đóng (nút Xong / chạm ra ngoài). Bảng còn biến mất theo ba đường khác mà nó
     * không đi qua: phím Back của Activity, `HomePanels.closeAll()` lúc huỷ màn, và `openLayoutEditor` (đóng
     * bảng này rồi mở bảng vẽ). `removeView` thì đường nào cũng phải gọi — [ĐO] `HomePanels.closeSettings()` là
     * chỗ duy nhất gỡ view, và cả ba đường trên đều rơi vào nó. Bắt ở nơi HỆ THỐNG bảo "đã tháo" thì không có
     * đường nào lọt, đúng kỷ luật *"kiểm bằng sự thật, không bằng cờ"* (CLAUDE.md §5).
     */
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        sections.dispose()
    }

    /**
     * Bỏ mọi trang đã nhớ rồi dựng lại trang đang xem.
     *
     * Gọi khi **state đã đổi từ bên ngoài trang** — trên thực tế là lúc đổi/thêm/xoá hồ sơ, vì việc đó nạp lại
     * *toàn bộ* (bố cục · thanh nút · chip · hình nền · đơn vị) nên MỌI trang đều cũ, không riêng trang Hồ sơ. Bỏ
     * hết là câu trả lời đúng và rẻ: trang khác chỉ phải dựng lại khi người dùng thật sự bấm sang.
     */
    fun invalidateAll() {
        pages.clear()
        content.removeAllViews()
        show(current)
    }

    /** Chọn nhóm: đổi nội dung khung phải, giữ chỗ đang cuộn của các nhóm khác (trang cũ chỉ bị **tháo**, không xoá). */
    fun show(group: SettingsGroup) {
        current = group
        railCells.forEach { (g, cell) -> paintRail(cell, g == group) }
        content.removeAllViews()
        val page = pages.getOrPut(group) { sections.build(group) }
        content.addView(
            page,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT),
        )
    }

    // ── Đầu bảng ─────────────────────────────────────────────────────────────────────────────────

    private fun head(): View = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        // ⚠ [SOÁT ẢNH 2026-09-12] Đệm PHẢI [KachiSpace.XS] không phải 0: thân trang (`SettingsSections.build`) tự
        // chừa `paddingRight = Sp.XS` cho thanh cuộn, nên đầu bảng để 0 thì mép phải nút "Xong" (x=1835) **thò ra
        // 6px** so với mép phải của mọi thẻ bên dưới (x=1829). Hai khối chồng nhau theo chiều dọc phải cùng một
        // cột — lệch vài px là thứ mắt đọc ra "lổn nhổn" mà không chỉ được tên.
        setPadding(0, 0, dpi(context, Sp.XS), dpi(context, Sp.L))
        addView(
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(context).apply {
                    text = context.getString(R.string.kachi_settings_title); setTextColor(c(KachiTheme.INK))
                    KachiType.apply(this, KachiType.TITLE, bold = true)
                })
                addView(TextView(context).apply {
                    text = context.getString(R.string.kachi_settings_sub)
                    setTextColor(c(KachiTheme.MUT)); KachiType.apply(this, KachiType.CAPTION)
                })
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )
        // Nút CHÍNH của bảng — nền gradient (khác nút phụ viền mảnh của [SettingsRows.button]) nhưng **cùng đích
        // chạm và cùng đệm**: [ĐO] ảnh 2026-09-12 nó cao **52px = 34.7dp**, dưới đích chạm [KachiSpace.TOUCH] 48
        // và thấp hơn nút phụ (72px) ngay trên cùng một màn ⇒ 4 chiều cao cho 3 vai (design system §10 [P2]).
        // `minHeight` + `gravity = CENTER` phải đi CÙNG NHAU: minHeight chỉ nới ô chứ không căn chữ.
        // `ControlHeightContractTest` ghim dòng minHeight này.
        addView(TextView(context).apply {
            text = context.getString(R.string.kachi_done)
            KachiType.apply(this, KachiType.BODY, bold = true)
            setTextColor(c(KachiTheme.ON_ACCENT)); gravity = Gravity.CENTER
            setPadding(dpi(context, Sp.L), dpi(context, Sp.S), dpi(context, Sp.L), dpi(context, Sp.S))
            minHeight = dpi(context, Sp.TOUCH)
            background = KachiTheme.gradient(context, Sp.RADIUS_PILL)
            setOnClickListener { onClose() }
        })
    }

    // ── Rail nhóm ────────────────────────────────────────────────────────────────────────────────

    private fun rail(): View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        SettingsCatalog.GROUPS.forEach { g ->
            val cell = railCell(g)
            railCells[g] = cell
            addView(
                cell,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                ).also { it.bottomMargin = dpi(context, Sp.XS) },
            )
        }
    }

    /**
     * Một ô rail: nhãn + **câu phụ** nói nội dung nhóm.
     *
     * Câu phụ lấy từ [SettingsGroup.sub] ở `:core` chứ không viết tại đây — nó là phần *"nhóm này chứa gì"*, cùng
     * nguồn với phép kiểm phủ khoá. Có nó thì rail tự giải thích được, không cần bấm thử từng nhóm để biết ở đâu có gì.
     */
    private fun railCell(group: SettingsGroup): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        val p = dpi(context, Sp.M)
        setPadding(p, dpi(context, Sp.M), p, dpi(context, Sp.M))
        // ⚠ [SOÁT ẢNH 2026-09-12] Nhãn rail là bậc [KachiType.SECTION], KHÔNG phải BODY: rail là cấp **trên** của
        // mọi tiêu đề mục bên trong trang, mà tiêu đề mục ([SettingsRows.sectionLabel]) đã là SECTION 16 ⇒ để rail
        // ở BODY 13.5 là vẽ cây thư mục **ngược**, cấp cha nhỏ hơn cấp con. Cùng lẽ đã đưa sectionLabel từ 12–13
        // lên 16 (KDoc [KachiType]). Câu phụ giữ CAPTION + 2 dòng — nó là chú thích của nhãn, không phải một cấp.
        addView(TextView(context).apply {
            text = group.displayLabel; setTextColor(c(KachiTheme.INK))
            KachiType.apply(this, KachiType.SECTION, bold = true)
        })
        addView(TextView(context).apply {
            text = group.displaySub; setTextColor(c(KachiTheme.MUT2))
            KachiType.apply(this, KachiType.CAPTION)
            maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(0, dpi(context, Sp.XS), 0, 0)
        })
        setOnClickListener { show(group) }
    }

    /** Nhóm đang chọn = nền nhạt + viền accent; nhóm khác = trong suốt (rail không được ồn hơn nội dung). */
    private fun paintRail(cell: LinearLayout, on: Boolean) {
        cell.background = if (on) GradientDrawable().apply {
            cornerRadius = dpi(context, Sp.RADIUS_M).toFloat()
            setColor(c(KachiTheme.ACCENT_SOFT)); setStroke(dpi(context, Sp.HAIRLINE), c(KachiTheme.ACCENT))
        } else null
    }

    // ⚠ [SOÁT G1] `private companion object { const val RAIL_DP = 230 }` đã XOÁ ở đây: bề rộng rail nay là
    // [KachiSpace.RAIL_COL]. Một hằng cỡ dp sống ngoài thang thì bài canh không thấy (nó là định danh, không phải
    // số trần) — tức "một thang, một chỗ" chỉ đúng trên giấy. Xem KDoc của hằng đó.
}
