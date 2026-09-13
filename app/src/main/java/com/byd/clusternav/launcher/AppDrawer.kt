package com.byd.clusternav.launcher

import android.content.Context
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
import com.byd.clusternav.R
import com.byd.clusternav.launcher.KachiTheme.c
import com.byd.clusternav.launcher.KachiTheme.dpi
import com.byd.clusternav.launcher.KachiSpace as Sp

/**
 * Ngăn kéo app — **ba chế độ** (cùng một view, không nhân bản UI):
 *  - [Mode.ASSIGN_SLOT] (như cũ): "Đặt widget / mở app vào ô này". **Widget**: chọn NHIỀU (1..8) → tô sáng, bấm
 *    **Đặt** để áp vào ô. **Ứng dụng**: chạm 1 app → đặt vào ô (1 app / ô).
 *  - [Mode.OPEN_APP] (gói 1 · U3): "Mở ứng dụng" — KHÔNG có mục widget, chạm app là **mở toàn màn**, không gắn vào
 *    ô nào. Có thêm hàng **Gần đây** (nguồn: [RecentApps], không cần quyền nào).
 *  - [Mode.PICK_DOCK] (T6 · spec `kachi-settings-ia-v2.html` R-UI **(m)**): "Chọn nút cho thanh nút xe" — đa chọn
 *    trên ĐÚNG tập ô mà màn Cài đặt đang bày cho thanh nút, chọn sẵn theo `dock.enabled`, bấm **Áp dụng (N)**.
 *
 * ## Vì sao chế độ thứ ba nằm Ở ĐÂY chứ không là một lưới thứ hai trong Cài đặt
 * [ĐO] soát ảnh 2026-09-12: màn Cài đặt có lưới 123 ô của riêng nó ⇒ nhóm "Màn hình chính" dài **10.5 màn cuộn**, và
 * hai lưới cùng bày một tập ô đã lệch nhau ba lần (cột 4-vs-5, thụt 6px, cỡ chữ ngoài thang). R-UI (m) chốt **một bộ
 * chọn, hai lối vào** ⇒ mọi phép vá nhịp lưới (R5) chỉ phải làm một lần. Bám prototype kachi-workspace.html.
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
    /**
     * Mục "Widget của app khác" (T4). Mặc định rỗng ⇒ chế độ mở-app và mọi chỗ gọi cũ **không đổi hành vi**;
     * ở chế độ gán ô thì mục vẫn hiện tiêu đề kèm câu "máy chưa có widget nào" thay vì mất tăm.
     */
    private val appWidgetPicks: List<AppWidgetPick> = emptyList(),
    /** [Mode.PICK_DOCK] — tập khả năng người dùng vừa chốt cho **thanh nút xe**. Mặc định rỗng ⇒ chỗ gọi cũ y nguyên. */
    private val onApply: (Set<String>) -> Unit = {},
) : FrameLayout(context) {

    /** Ngăn kéo dùng để GÁN VÀO Ô (như cũ), MỞ APP toàn màn (U3), hay CHỌN NÚT cho thanh nút xe (T6). */
    enum class Mode { ASSIGN_SLOT, OPEN_APP, PICK_DOCK }

    /**
     * Trần số mục **của bảng này** — không phải một hằng toàn cục.
     *
     * Ô giữa màn chứa tối đa [MAX] = 8 mục (giới hạn hình học của ô). **Thanh nút xe KHÔNG có trần**: `DockConfig`
     * lưu `List<String>` dài tuỳ ý và `ControlRegistry.defaultEnabledIds()` đã 8 mục — mở bảng chọn với trần 8 cho
     * một cấu hình đang có 10 nút sẽ **cắt mất 2 nút mà không nói gì**, đúng họ lỗi "chặn im lặng" mà
     * [toggleSelection] sinh ra để chống.
     */
    private val cap: Int = if (mode == Mode.PICK_DOCK) NO_CAP else MAX

    private val selected = ArrayList<String>().apply { addAll(initialWidgets.take(cap)) }
    private val widgetTiles = HashMap<String, LinearLayout>()
    private var placeBtn: TextView? = null

    /** Phần danh sách ứng dụng (tách tệp vì trần 500 dòng) — xem [AppDrawerApps]. */
    private val apps = AppDrawerApps(context, onPickApp)

    /** Câu nhắc trần ô ở thanh đáy — rỗng khi chưa đầy (đủ thì im lặng). */
    private var capHint: TextView? = null

    init {
        setBackgroundColor(c(KachiTheme.SCRIM_PANEL))
        isClickable = true
        setOnClickListener { onClose() }

        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = KachiTheme.card(context, Sp.RADIUS_XXL, KachiTheme.PANEL)
            setPadding(dpi(context, Sp.XXL), dpi(context, Sp.XL), dpi(context, Sp.XXL), dpi(context, Sp.XL))
            isClickable = true
        }
        val plp = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).also {
            it.setMargins(dpi(context, Sp.XXL), dpi(context, Sp.XXL), dpi(context, Sp.XXL), dpi(context, Sp.XXL)); it.gravity = Gravity.CENTER
        }
        val assign = mode == Mode.ASSIGN_SLOT; val dock = mode == Mode.PICK_DOCK

        panel.addView(TextView(context).apply {
            text = context.getString(
                when (mode) {
                    Mode.ASSIGN_SLOT -> R.string.kachi_drawer_title_assign
                    Mode.OPEN_APP -> R.string.kachi_drawer_title_open
                    Mode.PICK_DOCK -> R.string.kachi_drawer_title_dock
                },
            )
            setTextColor(c(KachiTheme.INK))
            KachiType.apply(this, KachiType.TITLE, bold = true)
        })
        panel.addView(TextView(context).apply {
            text = context.getString(
                when (mode) {
                    Mode.ASSIGN_SLOT -> R.string.kachi_drawer_hint_assign
                    Mode.OPEN_APP -> R.string.kachi_drawer_hint_open
                    Mode.PICK_DOCK -> R.string.kachi_drawer_hint_dock
                },
            )
            setTextColor(c(KachiTheme.MUT)); KachiType.apply(this, KachiType.CAPTION)
            setPadding(0, dpi(context, Sp.XS), 0, dpi(context, Sp.M))
        })

        val body = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

        if (dock) {
            // ĐÚNG tập ô mà màn Cài đặt bày cho thanh nút, cùng thứ tự — không chép danh sách, cả hai đường đi qua
            // `CapabilityPicker`/`CapabilityCatalog` ở `:core`. KHÔNG có widget dựng tay / widget app khác / danh sách
            // app: `DockConfig.setEnabled` chỉ nhận mã trong `CapabilityCatalog` ⇒ bày chúng ở đây là bày nút chết.
            groupSection(body); singlesSection(body)
        } else if (assign) {
            groupSection(body)

            // ── Widget dựng tay (chọn nhiều) ──
            body.addView(sectionLabel(context.getString(R.string.kachi_drawer_section_widgets)).also { it.setPadding(0, dpi(context, Sp.M), 0, dpi(context, Sp.XS)) })
            addWidgetGrid(body, cols = COLS_TILE)

            singlesSection(body)

            // ── Widget của APP KHÁC (T4) — đặt SAU nhóm/thẻ dựng tay và các mục lẻ, TRƯỚC danh sách app ──
            //
            // Vì sao ở đây chứ không cạnh "Thẻ dựng tay": nó **ít dùng hơn** (thẻ Kachi đọc dữ liệu xe, cái người ta
            // mở launcher để xem), và nó là thứ **có thể không chạy được trên xe** — ràng buộc widget cần bind-grant
            // qua kênh shell. Đặt nó lên trước sẽ đẩy thứ chắc chắn chạy xuống dưới.
            //
            // Rỗng thì **vẫn hiện tiêu đề** kèm câu nói rõ "máy chưa có app nào cung cấp widget": im lặng bỏ cả mục
            // sẽ thành "tính năng biến mất không lý do" — đúng họ lỗi trần-ô-chặn-im-lặng mà G1 đã phải đi vá.
            body.addView(sectionLabel(context.getString(R.string.kachi_drawer_section_appwidgets)).also { it.setPadding(0, dpi(context, Sp.L), 0, dpi(context, Sp.XS)) })
            if (appWidgetPicks.isEmpty()) {
                body.addView(note(context.getString(R.string.kachi_drawer_note_appwidgets_none)))
            } else {
                body.addView(note(context.getString(R.string.kachi_drawer_note_appwidgets)))
                apps.grid(body, appWidgetPicks.map { p -> AppDrawerApps.Item(APPWIDGET_PKG, p.title, p.icon, p.onTap) }, cols = COLS_TILE)
            }

            // ── App (chạm đặt vào ô) ──
            body.addView(sectionLabel(context.getString(R.string.kachi_drawer_section_apps)).also { it.setPadding(0, dpi(context, Sp.L), 0, dpi(context, Sp.XS)) })
            apps.grid(body, apps.load(), cols = COLS_APP)
        } else {
            // ── Chế độ MỞ THƯỜNG: gần đây trước, rồi tất cả ──
            val all = apps.load()
            val recent = apps.recent(all, recentApps)
            if (recent.isNotEmpty()) {
                body.addView(sectionLabel(context.getString(R.string.kachi_drawer_section_recent)))
                apps.grid(body, recent, cols = COLS_APP)
                body.addView(sectionLabel(context.getString(R.string.kachi_drawer_section_all_apps)).also { it.setPadding(0, dpi(context, Sp.L), 0, dpi(context, Sp.XS)) })
            }
            apps.grid(body, all, cols = COLS_APP)
        }

        panel.addView(
            ScrollView(context).apply {
                addView(body)
                // ⚠ [KIỂM TOÁN UX mục 5c] Mép cuộn trước đây CẮT NGANG chữ: [ĐO] 3 nhãn chỉ còn ~40% nét ở đường
                // biên, đọc thành chữ lỗi chứ không đọc thành "còn nữa, cuộn đi". Mép mờ nói đúng điều đó, và là
                // cách nền tảng có sẵn (không phải một lớp phủ tự vẽ phải tự nhớ đổi màu theo nền).
                isVerticalFadingEdgeEnabled = true
                setFadingEdgeLength(dpi(context, Sp.M))
                // Đệm trên/dưới + KHÔNG cắt theo đệm ⇒ hàng đầu và hàng cuối không dính vào biên vùng cuộn.
                clipToPadding = false
                // ⚠⚠ [R-UI (e)] Đệm ĐÁY phải LỚN HƠN dải mờ, không thì hàng cuối KHÔNG BAO GIỜ đọc được: [ĐO] đệm
                // `Sp.S` < dải mờ `Sp.XL` ⇒ cuộn hết cỡ mà nhãn hàng cuối vẫn bị cắt ngang chữ. `Sp.TOUCH + Sp.S` =
                // cao nút áp + một nhịp — [ĐO] soát ảnh v2: bản `+ Sp.M` kèm dải mờ `Sp.XL` để lại **99px trống** ở
                // đáy mà vẫn làm mờ nhãn hàng cuối, nên dải mờ hạ về `Sp.M` (quan hệ "đệm > dải mờ" giữ: 56 > 12).
                setPadding(0, dpi(context, Sp.XS), 0, dpi(context, Sp.TOUCH) + dpi(context, Sp.S))
            },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f),
        )
        // ⚠⚠ [KIỂM TOÁN UX mục 5a] Nút áp cấu hình GHIM Ở ĐÁY BẢNG, **ngoài** vùng cuộn.
        //
        // [ĐO] trước đây nó nằm trong thân cuộn (cạnh tiêu đề mục đầu), nên cuộn xuống là **mất nút**: điểm sáng ở
        // vùng nút đi 7242 → 83 → 0. Người dùng chọn xong ở cuối danh sách thì không còn đường áp — phải cuộn ngược
        // lên mới thấy, mà không có gì nói cho họ biết điều đó. Nút quyết định phải luôn ở trong tầm mắt.
        if (assign || dock) {
            panel.addView(placeBar(), LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            refreshPlaceBtn()
        }
        addView(panel, plp)
    }

    /**
     * Mục **NHÓM** — thứ người dùng gặp TRƯỚC (G1 · T4 · §4.2). Dùng chung cho ngăn kéo gán-ô và bộ chọn thanh nút.
     *
     * ⚠ [SOÁT UI 2026-09-12] Nhóm dùng **4 cột như mọi phần ô-khả-năng bên dưới**. Trước đây Nhóm để 3 cột "cho dòng
     * phụ rộng" — nhưng nó nằm NGAY TRÊN các phần 4 cột trong CÙNG một vùng cuộn, nên cuộn xuống thì tâm cột nhảy
     * 3→4 = "lệch loạn" (owner báo). Một vùng cuộn phải có MỘT lưới cột.
     */
    private fun groupSection(body: LinearLayout) {
        body.addView(sectionLabel(CapabilityPicker.GROUPS_TITLE))
        body.addView(note(CapabilityPicker.GROUPS_NOTE))
        val groups = CapabilityPicker.groupPicks()
        PickerBadge.unverifiedNote(context, groups)?.let { body.addView(note(it)) }
        addPickGrid(body, groups, cols = COLS_TILE)
    }

    /**
     * Mục **TỪNG MỤC RIÊNG** — dữ liệu + HÀNH ĐỘNG của xe, gom theo lĩnh vực.
     *
     * [SOÁT RW0 2026-09-11] Chỗ này TRƯỚC ĐÂY chỉ bày `WidgetCatalog.telemetryByDomain()` = **duy nhất mục ĐỌC**.
     * Hệ quả: `WidgetViews` VẼ được ô hành động và `ActionMacros` có 4 gói lệnh, nhưng người dùng **không có nút
     * nào** để đặt chúng vào ô giữa màn. Nay dùng CÙNG nguồn với màn Cài đặt ([CapabilityCatalog.byDomain]), nên hai
     * màn chọn không thể lệch nhau về việc "cái gì đặt được ở đâu".
     */
    private fun singlesSection(body: LinearLayout) {
        body.addView(sectionLabel(CapabilityPicker.SINGLES_TITLE).also { it.setPadding(0, dpi(context, Sp.L), 0, dpi(context, Sp.XS)) })
        CapabilityCatalog.byDomain().forEach { (domain, picks) ->
            // `singlesOf` BẮT BUỘC: nhóm đã bày ở mục đầu, để nó nằm trong lĩnh vực nữa là **hai ô cùng một mã**
            // ⇒ `widgetTiles[id]` bị ghi đè ⇒ chỉ ô sau được tô sáng (đúng lỗi RW0 đã ghi).
            body.addView(sectionLabel(domain.displayLabel).also { it.setPadding(0, dpi(context, Sp.M), 0, dpi(context, Sp.XS)) })
            CapabilityPicker.groupHint(picks).takeIf { it.isNotEmpty() }?.let { body.addView(note(it)) }
            val singles = CapabilityPicker.singlesOf(picks)
            // U7 · R6: nói MỘT câu cho cả lĩnh vực thay vì 19 chấm hổ phách rải khắp lưới (xem [PickerBadge]).
            PickerBadge.unverifiedNote(context, singles)?.let { body.addView(note(it)) }
            addPickGrid(body, singles, cols = COLS_TILE)
        }
    }

    /** Thanh đáy ghim: câu nhắc trần ô (bên trái) + nút áp (bên phải). */
    private fun placeBar(): View = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        setPadding(0, dpi(context, Sp.M), 0, 0)
        val hint = TextView(context).apply {
            setTextColor(c(KachiTheme.MUT)); KachiType.apply(this, KachiType.CAPTION)
        }
        capHint = hint
        addView(hint, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val btn = TextView(context).apply {
            // ⚠ [R7] Đích chạm 48dp khai TẠI ĐÂY: [ĐO] soát ảnh v2 nút QUYẾT ĐỊNH của bảng cao **34.7dp**, thấp
            // hơn mọi nút phụ của Settings (48dp) — đúng bệnh "bốn chiều cao cho ba vai" mà `SettingsRows` đã gom.
            KachiType.apply(this, KachiType.BODY, bold = true); gravity = Gravity.CENTER; minHeight = dpi(context, Sp.TOUCH)
            setPadding(dpi(context, Sp.L), dpi(context, Sp.S), dpi(context, Sp.L), dpi(context, Sp.S))
            background = KachiTheme.gradient(context, Sp.RADIUS_PILL); setTextColor(c(KachiTheme.ON_ACCENT))
            // Một nút, hai đích đến theo chế độ — KHÔNG hai nút: thanh đáy chỉ có chỗ cho một quyết định, và hai nút
            // trong đó thì lúc nào cũng có đúng một cái là nút chết.
            setOnClickListener {
                if (mode == Mode.PICK_DOCK) onApply(selected.toSet()) else onPickWidgets(selected.toList())
            }
        }
        placeBtn = btn; addView(btn)
    }

    /**
     * Câu nói khi đã đủ trần — nêu **cả trần lẫn đường đi tiếp**.
     *
     * Một chỗ duy nhất vì nó xuất hiện ở HAI nơi (câu nhắc ở thanh đáy + toast khi bấm): hai bản chữ sẽ lệch nhau
     * đúng lúc ai đó sửa một chỗ, và lúc đó hai bề mặt nói hai điều về cùng một luật.
     *
     * ⚠ U5·T3 — đây từng là `const val CAP_NOTE` nội suy `$MAX`. Nay là HÀM vì chuỗi nằm trong tài nguyên (cần
     * `Context`), và trần vẫn lấy từ [MAX] chứ không gõ lại — `PickerCapNoticeContractTest` đọc CHÍNH tệp tài nguyên
     * để chốt hai tính chất cũ (nêu số trần · nói cách đi tiếp), nên phép kiểm không yếu đi khi chữ dời chỗ.
     */
    private fun capNote(): String = context.getString(R.string.kachi_drawer_cap_note, MAX)

    private fun refreshPlaceBtn() {
        placeBtn?.text = when {
            // Chọn nút cho thanh xe: nút luôn là "Áp dụng (N)" kể cả N = 0 — bỏ HẾT nút khỏi thanh là một lựa chọn
            // hợp lệ (thanh ẩn đi), không phải một trạng thái phải đổi tên nút.
            mode == Mode.PICK_DOCK -> context.getString(R.string.kachi_drawer_apply_n, selected.size)
            selected.isEmpty() -> context.getString(R.string.kachi_drawer_place_none)
            else -> context.resources.getQuantityString(R.plurals.kachi_drawer_place_n, selected.size, selected.size)
        }
        // Câu nhắc chỉ hiện KHI ĐẦY (đủ thì im lặng — cùng luật với vòng kiểm quyền). Nói cả trần LẪN cách đi tiếp,
        // vì "đã đủ 8" một mình không cho người dùng biết phải làm gì.
        // Và **trả dòng này về dáng THÔNG TIN** (mực mờ, không nền): dáng CẢNH BÁO chỉ thuộc về cú bấm vừa bị từ chối
        // (xem [notice]). Không trả về thì cái nền hổ phách còn nằm đó sau khi người dùng đã bỏ một mục ra — tức nó
        // nói một điều không còn đúng.
        capHint?.let { v ->
            v.text = if (selected.size >= cap) capNote() else ""
            v.setTextColor(c(KachiTheme.MUT))
            v.background = null
            v.setPadding(0, 0, 0, 0)
        }
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
        } else if (selected.size >= cap) {
            notice(capNote())
            return
        } else {
            selected.add(id)
        }
        refreshTiles(); refreshPlaceBtn()
    }

    /**
     * ═══ [KIỂM TOÁN 2026-09-12 mục 1] KÊNH NÓI CỦA NGĂN KÉO — KHÔNG THỂ LÀ TOAST ══════════════════════════════
     *
     * ## [ĐO] bằng số, không suy luận — 2026-09-12, máy ảo
     * Ngăn kéo mở dưới dạng **cửa sổ phủ** (`TYPE_APPLICATION_OVERLAY`, xem [DrawerController]). `dumpsys window`
     * lúc toast đang lên:
     *  • toast: `ty=TOAST`, `mBaseLayer=81000`, khung `[655,969][1264,1044]`;
     *  • ngăn kéo: `ty=APPLICATION_OVERLAY`, `mBaseLayer=**121000**`, khung `[0,0][1920,1080]`.
     *
     * 121000 > 81000 ⇒ ngăn kéo nằm **TRÊN** toast, và nó phủ **cả màn**. So hai ảnh chụp (trước / sau cú bấm bị từ
     * chối): trong dải CHỮ của toast (`y 969..1037`) có **0 pixel** đổi; chỉ dải `y 1037..1044` — 7px lọt ra dưới đáy
     * bảng — đổi (4018 px, (8,11,16) → (47,49,54)), tức thấy được **mép hộp** toast mà không thấy một nét chữ nào.
     *
     * Vì vậy toast ở bề mặt này là một **kênh im lặng**: mã có gọi, người dùng không nhận được gì. Cùng họ lỗi
     * `DockConfig.setEnabled` (bỏ qua im lặng) và trần-8-mục (bấm không một lời nào) — dự án đã vá ba lần.
     *
     * ## Vì sao dòng chữ nằm trong THANH ĐÁY
     * Nó **ghim ngoài vùng cuộn** (cùng hàng với nút áp cấu hình), nên luôn thấy được dù người dùng đang cuộn ở đâu —
     * khác toast, nó không thể bị cửa sổ nào che vì nó là con của chính bảng. Đổi **màu + nền** (không chỉ đổi chữ)
     * để một cú bấm bị từ chối tạo ra thay đổi **nhìn ra được**: khi đã đủ trần thì dòng này vốn đã hiện sẵn câu nhắc,
     * nên nếu chỉ đặt lại cùng một chữ thì trên màn **không có gì đổi**.
     *
     * Toast ở màn Cài đặt thì vẫn dùng được (bảng đó là con của cửa sổ Activity, `mBaseLayer` ~21000 < 81000) — nên
     * đây là luật của **bề mặt phủ**, không phải "bỏ toast trong toàn dự án".
     */
    private fun notice(msg: String) {
        val v = capHint ?: return
        v.text = msg
        v.setTextColor(c(KachiTheme.AMBER))
        v.background = KachiTheme.card(context, Sp.RADIUS_PILL, KachiTheme.AMBER_SOFT, KachiTheme.AMBER)
        val px = dpi(context, Sp.S)
        v.setPadding(px, dpi(context, Sp.XS), px, dpi(context, Sp.XS))
    }

    /**
     * Nói một câu ra thanh đáy **từ ngoài** (T4: kết quả ràng buộc widget bên thứ ba).
     *
     * Có mặt vì việc ràng buộc widget là **không đồng bộ** (phải mở kênh shell để xin bind-grant) nên câu trả lời tới
     * khi bảng này vẫn đang mở — và đây là kênh nói DUY NHẤT dùng được ở bề mặt phủ (xem KDoc [notice]: toast nằm
     * DƯỚI lớp `APPLICATION_OVERLAY`, [ĐO] `dumpsys window` 81000 < 121000).
     *
     * ⚠ Khai SAU [notice], không phải trước: đặt trước thì KDoc *"vì sao không dùng Toast"* của [notice] (một luật của
     * dự án, có bài canh riêng) bị **tách khỏi hàm nó nói về** — hai khối KDoc liền nhau thì Kotlin chỉ nhận khối
     * cuối, nên [notice] mất tài liệu và chính lời dẫn "xem KDoc [notice]" ở trên trỏ vào chỗ trống.
     */
    fun say(msg: String) = notice(msg)

    // ── Widget grid (toggle) — khe/đồng cao lấy từ [CapabilityTileGrid] như mọi lưới khác (R5) ──
    private fun addWidgetGrid(parent: LinearLayout, cols: Int) =
        CapabilityTileGrid.rows(context, parent, widgets.size, cols) { i -> widgetTile(widgets[i]) }

    private fun widgetTile(def: WidgetDef): View {
        val tile = LinearLayout(context).apply {
            // Căn NGANG-giữa nhưng DỌC-TRÊN (không CENTER cả hai): ô cao MATCH_PARENT theo hàng, căn giữa dọc làm
            // icon của ô nhãn ngắn tụt xuống lệch với ô nhãn hai dòng cùng hàng.
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL or Gravity.TOP
            setPadding(dpi(context, Sp.S), dpi(context, Sp.M), dpi(context, Sp.S), dpi(context, Sp.M))
            addView(ImageView(context).apply {
                val r = KachiTheme.iconRes(def.icon); if (r != 0) { setImageResource(r); setColorFilter(c(KachiTheme.INK)) }
                layoutParams = LinearLayout.LayoutParams(dpi(context, Sp.ICON_XL), dpi(context, Sp.ICON_XL))
            })
            addView(TextView(context).apply {
                text = def.displayLabel; setTextColor(c(KachiTheme.INK)); KachiType.apply(this, KachiType.BODY)
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
    private fun addPickGrid(parent: LinearLayout, picks: List<CapabilityPick>, cols: Int) =
        CapabilityTileGrid.rows(context, parent, picks.size, cols) { i -> pickTile(picks[i]) }

    private fun pickTile(pick: CapabilityPick): View {
        val inner = LinearLayout(context).apply {
            // ⚠ [R5] Căn NGANG-giữa nhưng DỌC-TRÊN — cùng luật với lưới trong Cài đặt: ô cao `MATCH_PARENT` theo
            // hàng, nếu căn giữa dọc thì ô có dòng phụ đẩy icon/nhãn xuống ~10px lệch với ô cùng hàng.
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL or Gravity.TOP
            setPadding(dpi(context, Sp.S), dpi(context, Sp.M), dpi(context, Sp.S), dpi(context, Sp.M))
            addView(PickerBadge.icon(context, KachiTheme.iconRes(pick.icon), pick.needsBadge, Sp.ICON_XL))
            addView(TextView(context).apply {
                // Nhãn = TÊN của khả năng, không mang gợi ý loại (U6): loại xuống dòng phụ bên dưới. [ĐO] ảnh
                // 2026-09-12 owner đọc được "Charge target · view" trên lưới — thuật ngữ nội bộ lọt vào tên.
                text = pick.displayLabel; setTextColor(c(KachiTheme.INK)); setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)   // [type scale] ô lưới mật độ cao, ngoài 5 bậc
                gravity = Gravity.CENTER; maxLines = 2; ellipsize = TextUtils.TruncateAt.END
                setPadding(dpi(context, Sp.XS), dpi(context, Sp.S), dpi(context, Sp.XS), 0)
            })
            // Dòng phụ nói ô này GỒM GÌ (nhóm) và/hoặc thuộc LOẠI gì khi tên bị trùng (U6 — `displaySub`). Mục rời
            // tên không trùng vẫn để rỗng: [ĐO] 88/123 datum, thêm một dòng cho tất cả là làm chật đúng chỗ đang chật.
            // ⚠ T5 (thang khoảng cách/cỡ chữ): 10sp là số TÔI TỰ CHỌN — nhãn ở trên là 11.5sp, dòng phụ phải nhỏ hơn
            // để đọc ra thứ bậc. Mọi dpi() ở đây là số ĐÃ dùng sẵn trong chính ô này, không thêm số mới.
            if (pick.displaySub.isNotEmpty()) addView(TextView(context).apply {
                text = pick.displaySub; setTextColor(c(KachiTheme.MUT)); setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)   // [type scale] ô lưới mật độ cao, ngoài 5 bậc
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
     * ⚠ U7 · R6 — hàm `iconWithBadge` CŨ đã dời sang [PickerBadge.icon].
     *
     * Không phải dọn cho gọn: [TopStripPicker] bày **đúng những ô ấy** mà lại **không** vẽ chấm nào, tức cùng một
     * mã thì hai màn nói hai điều khác nhau về độ tin cậy của nó. Gom về một nơi là cách duy nhất để hai màn không
     * lệch tiếp — cùng lẽ với [CapabilityPicker.COLS].
     */

    private fun applyTileState(id: String) {
        val tile = widgetTiles[id] ?: return
        val on = id in selected
        tile.background = if (on) GradientDrawable().apply {
            cornerRadius = dpi(context, Sp.RADIUS_L).toFloat()
            setColor(c(KachiTheme.ACCENT_SOFT)); setStroke(dpi(context, Sp.HAIRLINE), c(KachiTheme.ACCENT))   // tô nền accent mờ + viền accent
        } else {
            // ⚠ [R-UI (g)] Ô CHƯA CHỌN cũng có NỀN MỜ, trước đây là `null`.
            //
            // [ĐO] soát ảnh pha 2: không nền thì hai ô cạnh nhau "nền liền mạch" — mắt không tách được ranh giới ô,
            // và cái chấm "chưa kiểm trên xe" ở góc icon không có mặt phẳng nào để thuộc về. Cùng nền `FIELD` mà lưới
            // trong Cài đặt đã dùng từ pha 1 ⇒ hai bề mặt đọc như một.
            KachiTheme.card(context, Sp.RADIUS_L, KachiTheme.FIELD)
        }
        // [KIỂM TOÁN UX mục 5b] Đầy trần ⇒ LÀM MỜ những ô không còn chọn được, để trạng thái "không bấm được nữa"
        // nhìn ra được TRƯỚC khi bấm; toast chỉ là lớp thứ hai cho người đã bấm.
        tile.alpha = if (on || selected.size < cap) 1f else DIMMED
    }

    private fun sectionLabel(text: String) = TextView(context).apply {
        // [SOÁT UI 2026-09-12] Header nhóm TRƯỚC ĐÂY màu MUT2 (mờ) + 12sp ⇒ mờ và nhỏ HƠN chữ nội dung (INK ~14.5sp)
        // nên không ra "đầu mục", các phần dồn thành một dải. Header phải NỔI hơn body: màu INK sáng + đậm + thưa chữ.
        // ⚠ [type scale] Bản vá đó nâng lên 13sp — vẫn **dưới** [KachiType.BODY] (13.5) nên tỉ số cỡ với nội dung là
        // 0.96: mắt không đọc ra thứ bậc, chỉ còn màu+nét gánh. Đây là TIÊU ĐỀ NHÓM ⇒ đúng bậc của nó là
        // [KachiType.SECTION] (16, tỉ số 1.19) — cùng bậc `SettingsRows.sectionHeader` đang dùng cho cùng vai.
        this.text = text; setTextColor(c(KachiTheme.INK)); KachiType.apply(this, KachiType.SECTION, bold = true)
        letterSpacing = 0.06f; setPadding(0, 0, 0, dpi(context, Sp.S))
    }

    /** Câu phụ dưới tiêu đề mục — cùng khuôn với câu mô tả ở đầu bảng, không phải cỡ chữ mới. */
    private fun note(text: String) = TextView(context).apply {
        this.text = text; setTextColor(c(KachiTheme.MUT)); KachiType.apply(this, KachiType.CAPTION)
        setPadding(0, 0, 0, dpi(context, Sp.S))
    }

    private companion object {
        /** Trần mục của MỘT Ô GIỮA MÀN (giới hạn hình học của ô) — xem [cap] về vì sao thanh nút xe không dùng nó. */
        const val MAX = 8

        /**
         * "KHÔNG có trần" — dùng cho [Mode.PICK_DOCK].
         *
         * Là một con số (không phải `null`) để mọi phép so `selected.size >= cap` giữ **một** hình dạng duy nhất:
         * thêm một nhánh `cap == null` là thêm một chỗ nữa có thể quên, đúng họ lỗi "hai bản sao của một luật".
         */
        const val NO_CAP = Int.MAX_VALUE

        // [SOÁT UI 2026-09-12] MỘT vùng cuộn = MỘT lưới cột, và con số đó do `:core` giữ (màn Cài đặt bày CHÍNH những ô này — xem KDoc [CapabilityPicker.COLS]). Danh sách app khác loại nên có số riêng.
        const val COLS_TILE = CapabilityPicker.COLS
        const val COLS_APP = 6

        /**
         * Gói giả cho mục widget bên thứ ba.
         *
         * [AppDrawerApps.Item.pkg] chỉ dùng để tra hàng **"Gần đây"** (`recent` khớp theo gói). Widget bên thứ ba
         * không phải app để mở nên không bao giờ vào hàng đó; đưa một giá trị KHÔNG trùng gói thật vào đây để nó
         * không thể tình cờ khớp — dùng tên gói thật sẽ làm mục widget hiện lại ở hàng "Gần đây" như một app.
         */
        const val APPWIDGET_PKG = "\u0000appwidget"

        /** Độ mờ của ô KHÔNG còn chọn được (đã đủ trần). */
        const val DIMMED = 0.4f
    }
}
