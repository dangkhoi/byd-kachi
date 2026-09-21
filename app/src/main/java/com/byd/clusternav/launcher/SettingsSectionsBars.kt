package com.byd.clusternav.launcher

import android.content.Context
import android.widget.LinearLayout
import android.widget.TextView
import com.byd.clusternav.R

/**
 * Nhóm **"Thanh trạng thái & thanh nút"** (IA v2 §4.1 nhóm 2 · R-UI **a**) — chip trên đỉnh màn + viền đặt thanh
 * nút xe + đường chọn nút cho thanh đó.
 *
 * ## Vì sao TÁCH khỏi "Màn hình chính"
 * [ĐO ảnh 2026-09-12] nhóm "Màn hình chính" dài **10.5 màn cuộn**, và 85% chiều dài đó là lưới 123 ô chọn nút
 * thanh xe. Chip thanh trạng thái + thanh nút là **khung cố định quanh** màn chính, không phải nội dung của nó —
 * tách ra thì cả hai nhóm cùng về ≤ 2 màn cuộn (R4) và người muốn đổi bố cục không phải cuộn qua một cái lưới.
 *
 * ## ⚠⚠ LƯỚI 123 Ô ĐÃ RỜI KHỎI ĐÂY — R-UI (m), đóng OQ3
 * Trang này **không** còn dựng lưới ô. Chọn nút cho thanh nay mở **chính bộ chọn của ngăn kéo** ở chế độ
 * `AppDrawer.Mode.PICK_DOCK` ([DrawerController.openDockPicker]). Một bộ chọn, hai lối vào ⇒ mất luôn bốn lệch
 * mà soát ảnh đo được giữa hai lưới (4 vs 5 cột · thụt 6px · cỡ chữ ngoài thang · huy hiệu phủ 19/20 ô), và
 * `CapabilityGridSection` — lưới thứ hai — không còn lý do tồn tại nên đã bị xoá (CLAUDE.md §8).
 *
 * ## Ghi cấu hình: gấp bằng `:core`, đẩy qua ViewModel
 * Bộ chọn trả về một **TẬP**; `dock.enabled` là **DANH SÁCH CÓ THỨ TỰ**. Phép gấp là [DockSelection.apply]
 * (`:core`, có test) — nó làm cả chiều TẮT (bỏ tích thì nút phải rời thanh) và giữ nguyên thứ tự phần cũ. Đừng
 * thay bằng một vòng `setEnabled(id, true)` ở đây: xem KDoc [DockSelection] để biết hai bẫy nó đóng.
 */
class SettingsBarsSection(
    private val context: Context,
    private val rows: SettingsRows,
    private val deps: SettingsDeps,
) {

    /** Bộ chọn chip — MỘT thực thể cho một lượt dựng trang (nó giữ bảng tra `mã → view` để tô lại ô). */
    private val stripPicker = TopStripPicker(context, rows, deps.state().topStrip) { id, on -> deps.onTopStrip(id, on) }

    /**
     * UX-OVERHAUL · WP4 — sắp chỗ các vật trên **thanh trên**.
     *
     * `current`/`onMove` đọc-ghi qua [SettingsDeps] mỗi lượt gọi (không chụp sẵn [HeaderLayout]): trang Cài đặt
     * được nhớ lại ([SettingsPanel.pages]) nên một ảnh chụp lúc dựng có thể đã cũ vài phút, và hồ sơ có thể đã đổi.
     */
    private val headerOrder = SettingsBarOrderRows<HeaderItem>(
        context, rows,
        current = { deps.state().header.order },
        label = { it.displayLabel },
        onMove = { item, delta -> deps.onHeaderLayout(deps.state().header.move(item, delta)) },
    )

    /**
     * UX-OVERHAUL · WP4 — sắp chỗ các nút trên **thanh nút xe**.
     *
     * Thứ tự này KHÔNG có khoá lưu riêng: nó LÀ thứ tự của [DockConfig.enabled] ([DockConfig.moveEnabled]), đi qua
     * đúng [SettingsDeps.onDockConfig] mà bộ chọn nút và công tắc ẩn/hiện đang dùng — không mở đường ghi thứ hai.
     *
     * Nhãn tra qua [CapabilityCatalog.pick]: thanh nút nhận **cả** mục đọc, nút, gói lệnh và hai việc của launcher
     * (RW0 · S4 · R12), nên một bảng tra riêng ở đây sẽ thiếu đúng những loại mới thêm. Mã lạ (rác prefs) ⇒ hiện
     * chính mã, không bỏ hàng: bỏ hàng thì người dùng có một nút trên thanh mà không sắp được nó.
     */
    private val dockOrder = SettingsBarOrderRows<String>(
        context, rows,
        current = { deps.state().dock.enabled },
        label = { id -> CapabilityCatalog.pick(id)?.displayLabel ?: id },
        onMove = { id, delta -> deps.onDockConfig(deps.state().dock.moveEnabled(id, delta)) },
    )

    /** Nút mở bộ chọn nút thanh xe — nhãn mang số nút đang bật, nên phải sửa CHỮ tại chỗ sau khi Áp dụng. */
    private var pickButton: TextView? = null

    fun build(body: LinearLayout) {
        // Chip + thanh nút cũng lưu THEO HỒ SƠ — cùng câu với nhóm Màn hình chính (owner 2026-09-14). S4 · R8 nới
        // câu đó thành "mọi thiết lập ở Cài đặt trừ ba nhóm theo-xe": dùng CHUNG một chuỗi cho cả hai nhóm nên
        // không có hai câu nói hai kiểu về cùng một luật.
        body.addView(rows.note(context.getString(R.string.kachi_home_profile_note, ProfileNames.display(deps.state().activeProfile))))
        stripPicker.section(body)
        chipLabels(body)
        // WP4 — sắp chỗ ĐỨNG SAU chọn chip: sắp chỗ cho một vật chưa có mặt là câu hỏi vô nghĩa (thứ tự khai của
        // danh mục cũng vậy — xem [SettingsCatalogEntries]).
        headerOrder.section(body, R.string.kachi_header_order_title, R.string.kachi_header_order_hint)
        voicePill(body)
        dock(body)
    }

    /**
     * V3 · R14 — **hiện nhãn trên chip hay không** (owner 2026-09-16: *"chỉ hiện icon và chỉ số thôi, text nhiều
     * chật chỗ, cho cái toggle hiện text label"*).
     *
     * Đứng ngay SAU bộ chọn chip vì nó nói về chính những chip vừa chọn. Đi qua [SettingsDeps.onTopStripConfig]
     * (đặt cả cấu hình) chứ không qua một cổng boolean riêng — xem KDoc ở đó.
     */
    private fun chipLabels(body: LinearLayout) {
        body.addView(rows.checkRow(
            on = deps.state().topStrip.showLabels,
            title = context.getString(R.string.kachi_top_strip_labels_title),
            sub = context.getString(R.string.kachi_top_strip_labels_sub),
        ) { on -> deps.onTopStripConfig(deps.state().topStrip.copy(showLabels = on)) })
    }

    /**
     * V1 pha NGHE (R12 b) — công tắc **nút mic** trên thanh trạng thái.
     *
     * ## Vì sao nó nằm ở nhóm này chứ không ở nhóm giọng nói
     * Câu hỏi mà công tắc này trả lời là *"thanh trên có bao nhiêu nút"*, không phải *"Kachi nghe thế nào"* —
     * cùng loại với chọn chip và chọn viền thanh nút ngay cạnh. Đặt nó cạnh hàng tải mô hình sẽ trộn một lựa
     * chọn **bố cục** vào một hàng **cài đặt kỹ thuật**.
     *
     * ## Vì sao hàng vẫn hiện khi chưa tải mô hình
     * Thanh trên tự giấu nút mic nếu chưa có mô hình (`KachiHomeActivity`), nên công tắc này là *"khi có thì
     * hiện hay không"*. Giấu luôn cả công tắc sẽ làm người vừa tải mô hình xong không hiểu vì sao nút không ra,
     * và không có chỗ nào để tìm. Câu mô tả nói thẳng điều kiện đó.
     */
    private fun voicePill(body: LinearLayout) {
        body.addView(rows.checkRow(
            on = deps.bridge.voiceMicPill(),
            title = context.getString(R.string.kachi_voice_pill_title),
            sub = context.getString(R.string.kachi_voice_pill_sub),
        ) { on -> deps.bridge.setVoiceMicPill(on) })
    }

    /**
     * Viền đặt thanh + đường chọn nút.
     *
     * Viền dùng [SettingsDeps.onDockEdge] (đặt THẲNG một viền, không xoay vòng): ở đây cả 4 viền đang hiện ra,
     * nên bấm "Phải" phải ra "Phải" — xoay vòng sẽ bắt bấm ba lần và ô đang sáng nói sai.
     */
    private fun dock(body: LinearLayout) {
        body.addView(rows.sectionLabel(context.getString(R.string.kachi_sec_dock)))
        // S1b — ẩn/hiện thanh. Đặt onDockConfig (không thêm callback mới): giữ nguyên viền + nút đã chọn, chỉ đổi cờ
        // hiện. Đường ghi bền đi qua HomeViewModel.setDockConfig → saveDock như đổi nút.
        body.addView(rows.checkRow(
            on = deps.state().dock.visible,
            title = context.getString(R.string.kachi_dock_visible_title),
            sub = context.getString(R.string.kachi_dock_visible_sub),
        ) { on -> deps.onDockConfig(deps.state().dock.withVisible(on)) })
        body.addView(rows.chipRow(
            context.getString(R.string.kachi_dock_edge),
            DockEdge.values().map { it.name to it.label },
            deps.state().dock.edge.name,
        ) { code -> DockEdge.values().firstOrNull { it.name == code }?.let { deps.onDockEdge(it) } })
        val button = rows.button(pickLabel()) { openPicker() } as TextView
        pickButton = button
        body.addView(button)
        body.addView(rows.note(context.getString(R.string.kachi_dock_note)))
        // WP4 — sắp chỗ các nút, NGAY dưới nút mở bộ chọn: nó sắp đúng danh sách mà bộ chọn vừa chốt.
        dockOrder.section(body, R.string.kachi_dock_order_title, R.string.kachi_dock_order_hint)
        // Cảnh báo "nhóm đổi hành vi lái" là note RIÊNG, không nối vào câu trên: [ĐO] soát ảnh v2 câu gộp dài 2 dòng
        // (R-UI: mô tả ≤ 1 dòng), và khi gộp thì câu hướng dẫn nuốt mất phần cảnh báo — thứ duy nhất ở đây có hệ quả
        // lên XE. Giữ nguyên nội dung cảnh báo (R10), chỉ tách chỗ đứng.
        body.addView(rows.note(context.getString(R.string.kachi_dock_warn)))
    }

    /**
     * Mở bộ chọn với tập ĐANG bật, **đọc lại ngay lúc mở** — không dùng ảnh chụp lúc dựng trang: trang Cài đặt
     * được nhớ lại ([SettingsPanel.pages]) nên ảnh chụp đó có thể đã cũ vài phút.
     */
    private fun openPicker() = deps.openDockPicker(deps.state().dock.enabled.toSet()) { picked ->
        deps.onDockConfig(DockSelection.apply(deps.state().dock, picked))
        pickButton?.text = pickLabel()
        // WP4 — danh sách vật vừa đổi (thêm/bớt mã) ⇒ danh sách sắp chỗ phải theo. Xem KDoc
        // [SettingsBarOrderRows.refresh]: trang Cài đặt được NHỚ lại nên không có lượt dựng lại nào tự chạy.
        dockOrder.refresh()
    }

    private fun pickLabel(): String =
        context.getString(R.string.kachi_dock_pick_n, deps.state().dock.enabled.size)
}
