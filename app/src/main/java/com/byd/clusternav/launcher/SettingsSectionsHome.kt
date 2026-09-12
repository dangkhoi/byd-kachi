package com.byd.clusternav.launcher

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import com.byd.clusternav.R
import com.byd.clusternav.launcher.KachiTheme.dpi
import com.byd.clusternav.launcher.KachiSpace as Sp

/**
 * Nội dung nhóm **"Màn hình chính"** của màn Cài đặt (S1 · T3) — bố cục · hình nền · chip thanh trạng thái · thanh
 * nút xe (viền + lưới 187 ô).
 *
 * Tách khỏi [SettingsSections] vì trần **500 dòng** của dự án: nhóm này một mình gom bốn mảng, còn sáu nhóm kia cộng
 * lại vẫn ngắn hơn. Thứ tự bên trong theo [SettingsCatalog.entriesOf] cho [SettingsGroup.HOME]: đi từ **khung** ra
 * **nội dung** (bố cục → hình nền → chip → thanh nút), vì chọn bố cục trước thì các lựa chọn sau mới có nghĩa.
 *
 * ## ⚠ MỘT THỰC THỂ CHO MỘT LƯỢT DỰNG TRANG
 * Lớp này sở hữu [grid] ([CapabilityGridSection]) và [stripPicker] ([TopStripPicker]) — cả hai giữ **bảng tra
 * `mã → view`** để tô lại ô khi bật/tắt. Vì vậy mỗi lượt dựng trang phải là một thực thể **MỚI** của lớp này
 * ([SettingsSections.build] làm đúng thế). Dùng lại một thực thể cho hai lượt dựng sẽ để lại view cũ trong bảng tra —
 * đúng bẫy *"hai bản sao cùng khoá"* đã sinh ra ba lỗi cùng lúc ở phiên RW0.
 */
class SettingsHomeSection(
    private val context: Context,
    private val rows: SettingsRows,
    private val deps: SettingsDeps,
) {

    /** Bộ chọn chip thanh trạng thái (RW0 vùng thứ ba) — dùng LẠI y nguyên, không dựng bản thứ hai. */
    private val stripPicker = TopStripPicker(context, deps.state().topStrip) { id, on -> deps.onTopStrip(id, on) }

    /**
     * Lưới 187 ô khả năng. **GIỮ** ô ⇒ đưa datum lên thanh trạng thái, và đường đó đi qua **cùng** [stripPicker] mà
     * mục "Chip thanh trạng thái" phía trên dùng — nếu nối vào một bộ chọn thứ hai thì hai chỗ sẽ tô trạng thái khác
     * nhau cho cùng một cấu hình.
     */
    private val grid = CapabilityGridSection(
        context,
        enabledIds = deps.state().dock.enabled,
        onToggle = { id, on -> deps.onToggleDock(id, on) },
        onChipToggle = { id -> stripPicker.toggle(id) },
        chipEnabled = { id -> stripPicker.has(id) },
    )

    fun build(body: LinearLayout) {
        // Thứ tự = thứ tự khai trong [SettingsCatalog.entriesOf(HOME)]: CẢNH (cả bộ) → bố cục → hình nền → chip →
        // thanh nút. Cảnh đứng đầu vì gọi lại một cảnh là việc thường xuyên nhất ở trang này.
        SettingsSceneSection(context, rows, deps).build(body)
        layout(body)
        wallpaper(body)
        stripPicker.section(body)
        dock(body)
    }

    // ── Bố cục ───────────────────────────────────────────────────────────────────────────────────

    /**
     * Bố cục sẵn (5) + đường mở bảng vẽ bố cục riêng.
     *
     * §4.5 — **5 nút bố cục Ở LẠI thanh trên** (đổi bố cục là việc làm hằng ngày, bắt mở Cài đặt là làm launcher tệ
     * hơn). Ở đây bày **đủ** 5 bố cục kèm chữ, và cả hai bề mặt đi qua **cùng một** đường [SettingsDeps.onPreset] →
     * `KachiHomeActivity.selectPreset` → intent `setPreset` ⇒ không có bản sao thứ hai của trạng thái, cũng không có
     * bản sao thứ hai của hành vi "chọn bố cục sẵn thì bỏ bố cục tự vẽ".
     */
    private fun layout(body: LinearLayout) {
        val s = deps.state()
        body.addView(rows.sectionLabel(context.getString(R.string.kachi_sec_layout)))
        val summary = rows.note(layoutSummary(s.customLayout))
        body.addView(summary)
        body.addView(rows.chipRow(
            context.getString(R.string.kachi_row_preset),
            LayoutPreset.values().map { it.name to it.label },
            // ⚠ [SOÁT UI 2026-09-12] Đang dùng bố cục TỰ VẼ thì KHÔNG pill bố-cục-sẵn nào được sáng: trước đây vẫn
            // sáng "1 ô" (giá trị preset mặc định) trong khi dòng trên nói "đang dùng bố cục tự vẽ: N khung" ⇒ hai
            // câu trên cùng màn đá nhau. Truyền mã KHÔNG khớp ("") ⇒ 0 pill sáng; chạm một preset vẫn sáng bình thường.
            if (s.customLayout != null) "" else s.preset.name,
        ) { code ->
            LayoutPreset.values().firstOrNull { it.name == code }?.let { deps.onPreset(it) }
            // ⚠ [SOÁT S1 · P2] Chọn bố cục sẵn = **BỎ bố cục tự vẽ** (`KachiHomeActivity.selectPreset`), nên dòng
            // mô tả phía trên vừa thành SAI: nó còn nói "đang dùng bố cục tự vẽ: N khung". Trang này được **nhớ
            // lại** (xem [SettingsPanel]) nên nó không tự dựng lại ⇒ phải sửa CHỮ tại chỗ. Sửa chữ chứ không dựng
            // lại cả trang: dựng lại là 187 ô + mất chỗ đang cuộn. Đọc lại từ nguồn sự thật vì intent chạy đồng bộ.
            summary.text = layoutSummary(deps.state().customLayout)
        })
        body.addView(rows.button(context.getString(R.string.kachi_layout_open_editor)) { deps.onOpenLayoutEditor() }, wrapLp())
    }

    /**
     * Nói người dùng đang dùng bố cục nào — và **nếu bố cục tự vẽ bị bỏ qua thì nói lý do**.
     *
     * Im lặng ở chỗ này là ca xấu nhất: bố cục tự vẽ nhiều khung hơn trần ô (xảy ra thật khi hạ cấp bản) sẽ bị lùi về
     * bố cục sẵn, và nếu không ai nói thì người dùng thấy "đã lưu bố cục" mà màn hình khác hẳn.
     */
    private fun layoutSummary(custom: GridLayout?): String = custom?.let {
        context.getString(R.string.kachi_layout_custom_in_use, it.frames.size) +
            (EffectiveLayout.ignoredReason(it)?.let { r -> context.getString(R.string.kachi_layout_ignored, r) } ?: "")
    } ?: context.getString(R.string.kachi_layout_preset_in_use)

    // ── Hình nền & trình chiếu ───────────────────────────────────────────────────────────────────

    /**
     * Bốn dòng chuyển **nguyên văn** từ bảng Tuỳ biến: bật/tắt · chu kỳ đổi ảnh · cách phủ · làm tối.
     *
     * Câu phụ phải nói **chỗ bỏ ảnh vào** ([SettingsDeps.wallpaperFolderHint]): người dùng không có cách nào tự đoán
     * đường dẫn, và màn chọn tệp của hệ thống bị khoá trên xe.
     */
    private fun wallpaper(body: LinearLayout) {
        body.addView(rows.sectionLabel(context.getString(R.string.kachi_sec_wallpaper)))
        var wp = deps.state().wallpaper
        // ⚠ [SOÁT UI 2026-09-12] Ba dãy pill dưới (chu kỳ · cách phủ · làm tối) CHỈ có nghĩa khi hình nền đang BẬT.
        // Trước đây chúng luôn hiện đầy đủ + bấm được kể cả khi công tắc TẮT ⇒ 12 pill "chết" (bấm không đổi gì thấy
        // được) — đúng luật cấm nút-chết của dự án. Nay ẩn hẳn khi tắt, hiện lại khi bật (đổi ngay, không mở lại trang).
        val dependents = ArrayList<View>()
        fun gate(on: Boolean) { val v = if (on) View.VISIBLE else View.GONE; dependents.forEach { it.visibility = v } }
        body.addView(rows.checkRow(
            on = wp.enabled,
            title = context.getString(R.string.kachi_wall_title),
            sub = if (deps.wallpaperFolderHint.isEmpty()) context.getString(R.string.kachi_wall_sub_nofolder)
            else context.getString(R.string.kachi_wall_sub_folder, deps.wallpaperFolderHint),
        ) { on -> wp = wp.copy(enabled = on); deps.onWallpaper(wp); gate(on) })
        val period = rows.chipRow(
            context.getString(R.string.kachi_wall_period),
            Slideshow.INTERVAL_CHOICES_SEC.map { it.toString() to Slideshow.intervalLabel(it) },
            wp.intervalSec.toString(),
        ) { code ->
            wp = wp.copy(intervalSec = code.toIntOrNull() ?: Slideshow.DEFAULT_INTERVAL_SEC)
            deps.onWallpaper(wp)
        }
        val fit = rows.chipRow(context.getString(R.string.kachi_wall_fit), ImageFit.values().map { it.name to it.label }, wp.fit.name) { code ->
            wp = wp.copy(fit = ImageFit.values().firstOrNull { it.name == code } ?: ImageFit.FILL)
            deps.onWallpaper(wp)
        }
        val dim = rows.chipRow(
            context.getString(R.string.kachi_wall_dim),
            listOf(0, 25, 45, 65).map { it.toString() to "$it%" },
            wp.dim.toString(),
        ) { code ->
            wp = wp.copy(dimPercent = code.toIntOrNull() ?: WallpaperPrefs.DEFAULT_DIM_PERCENT)
            deps.onWallpaper(wp)
        }
        dependents += period; dependents += fit; dependents += dim
        body.addView(period); body.addView(fit); body.addView(dim)
        gate(wp.enabled)
    }

    // ── Thanh nút xe ─────────────────────────────────────────────────────────────────────────────

    /**
     * Viền đặt thanh nút + lưới 187 ô khả năng.
     *
     * Viền dùng [SettingsDeps.onDockEdge] (đặt THẲNG một viền) — nay là **đường DUY NHẤT**: pill "Thanh" ở thanh trên
     * (xoay vòng BOTTOM → LEFT → RIGHT → TOP) đã bỏ hẳn cùng `HomeViewModel.cycleDockEdge()`. Đặt thẳng là hình dạng
     * đúng cho bề mặt này: ở đây cả 4 viền đang hiện ra, nên bấm "Phải" phải ra "Phải" — xoay vòng sẽ bắt bấm ba lần
     * và ô đang sáng nói sai.
     *
     * Lưới bày **CẢ** mục ĐỌC lẫn HÀNH ĐỘNG ([CapabilityCatalog.byDomain] — R2): nếu chỉ bày nút thì việc nới cổng
     * `DockConfig.setEnabled` ở gói 2 thành vô nghĩa, vì không còn đường nào thêm một ô ĐỌC vào thanh.
     */
    private fun dock(body: LinearLayout) {
        body.addView(rows.sectionLabel(context.getString(R.string.kachi_sec_dock)))
        body.addView(rows.chipRow(
            context.getString(R.string.kachi_dock_edge),
            DockEdge.values().map { it.name to it.label },
            deps.state().dock.edge.name,
        ) { code -> DockEdge.values().firstOrNull { it.name == code }?.let { deps.onDockEdge(it) } })
        body.addView(rows.note(context.getString(R.string.kachi_dock_note)))
        // ── NHÓM TRƯỚC (G1 · T4 · §4.2) ──
        // Cùng thứ tự với ngăn kéo, và cùng nguồn (`CapabilityPicker`) ⇒ hai màn chọn không thể sắp khác nhau. Ô nhóm
        // trên thanh nút hiện **tóm tắt** ("2 cảnh báo") — xem `GroupBoard.summaryView`; không có nó thì ô hiện "—"
        // mãi mãi và mục này thành một lựa chọn chết.
        body.addView(rows.sectionLabel(CapabilityPicker.GROUPS_TITLE))
        body.addView(rows.note(CapabilityPicker.GROUPS_NOTE))
        grid.addGrid(body, CapabilityPicker.groupPicks(), cols = CapabilityPicker.COLS)
        body.addView(rows.sectionLabel(CapabilityPicker.SINGLES_TITLE))
        CapabilityCatalog.byDomain().forEach { (domain, picks) ->
            // `singlesOf` BẮT BUỘC: nhóm đã bày ở mục trên, để nó nằm trong lĩnh vực nữa là **hai ô cùng một mã** ⇒
            // `tiles[id]` bị ghi đè ⇒ chỉ ô sau được tô (đúng ba lỗi cùng lúc mà RW0 đã ghi ở KDoc lớp lưới).
            body.addView(rows.sectionLabel(domain.displayLabel))
            CapabilityPicker.groupHint(picks).takeIf { it.isNotEmpty() }?.let { body.addView(rows.note(it)) }
            grid.addGrid(body, CapabilityPicker.singlesOf(picks), cols = CapabilityPicker.COLS)
        }
    }

    private fun wrapLp() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
    ).also { it.bottomMargin = dpi(context, Sp.XS) }
}
