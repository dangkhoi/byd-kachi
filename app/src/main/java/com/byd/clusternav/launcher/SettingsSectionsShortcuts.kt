package com.byd.clusternav.launcher

import android.content.Context
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.byd.clusternav.R

/**
 * Hai cổng mà trang Cài đặt lối tắt cần — chủ là `KachiHomeShortcuts` (màn chính). Một giao diện thay cho hai lambda để
 * đường nối qua `homePanels` → [HomePanels] → [SettingsDeps] chỉ thêm MỘT tham số (`KachiHomeWiring.kt` sát trần 500).
 */
interface ShortcutSettingsPort {
    /** Mở ngăn kéo chọn app (`DrawerController.openShortcutPicker`); [onApply] nhận gói theo thứ tự chạm. */
    fun openPicker(selected: List<String>, onApply: (List<String>) -> Unit)

    /** Ghi CẢ danh sách đã chốt — intent ViewModel (`HomeViewModel.setAppShortcuts`), KHÔNG ghi bền trực tiếp. */
    fun save(items: List<AppShortcut>)
}

/**
 * ═══ F1 · U2 — Cài đặt › Thanh trạng thái & thanh nút › **Lối tắt ứng dụng** ═══════════════════════════════════════
 *
 * Spec `docs/specs/kachi-launcher-shortcuts-autostart.html` R1.4. Đặt ở nhóm THANH (không ở "Màn hình chính") vì chỗ
 * người dùng GẶP lối tắt đầu tiên là chính thanh nút (khối `launcher_shortcuts` chọn ngay trên trang này bằng nút
 * *"Chọn nút trên thanh…"*), và vì nhóm Màn hình chính đã dài (bố cục + hình nền + chủ đề). Widget `w_apps` dùng CÙNG
 * danh sách nên một chỗ cấu hình đủ cho cả hai bề mặt.
 *
 * Trang có: nút mở ngăn kéo chọn app (đa chọn, trần 8) · mỗi app MỘT hàng chip kiểu mở *Ô 1…Ô N* (N = số ô của bố cục
 * ĐANG dùng; ô đã chọn mà nằm ngoài bố cục thì vẫn hiện, mờ, kèm câu "bố cục hiện có k ô") · *Toàn màn* · *Chạy ngầm* ·
 * danh sách sắp thứ tự ([SettingsBarOrderRows], cùng bộ của hai thanh) · cảnh báo khi có app *Chạy ngầm* mà bố cục không
 * có ô app (§4.2.4). Mọi lượt ghi đi qua [ShortcutSettingsPort.save] → ViewModel (`SettingsScreenWiringContractTest`);
 * phép sửa là hàm thuần [ShortcutSelection] ở `:core`.
 */
class SettingsShortcutsSection(
    private val context: Context,
    private val rows: SettingsRows,
    private val deps: SettingsDeps,
) {
    private val port get() = deps.shortcuts
    private val items: List<AppShortcut> get() = deps.state().shortcuts

    private var title: TextView? = null
    private var pickButton: TextView? = null
    private val modes = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

    private val order = SettingsBarOrderRows<String>(
        context, rows,
        current = { items.map { it.pkg } },
        label = { pkg -> appLabel(pkg) },
        onMove = { pkg, delta -> save(ShortcutSelection.move(items, pkg, delta)) },
    )

    fun section(body: LinearLayout) {
        title = rows.sectionLabel(titleText()).also { body.addView(it) }
        body.addView(rows.note(context.getString(R.string.kachi_sc_note)))
        pickButton = (rows.button(pickText()) { openPicker() } as TextView).also { body.addView(it) }
        body.addView(modes, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        order.section(body, R.string.kachi_sc_order_title, R.string.kachi_sc_order_hint)
        paintModes()
    }

    /** Ngăn kéo chọn app — tập đang chọn ĐỌC LẠI lúc mở (trang Cài đặt được nhớ lại, ảnh chụp lúc dựng có thể đã cũ). */
    private fun openPicker() = port.openPicker(items.map { it.pkg }) { picked -> save(ShortcutSelection.apply(items, picked)) }

    private fun save(next: List<AppShortcut>) {
        if (next == items) return
        port.save(next)
        refresh()
    }

    /** Danh sách vừa đổi ⇒ tiêu đề · nút · hàng chip · hàng sắp chỗ phải theo (trang được NHỚ lại, không tự dựng lại). */
    private fun refresh() {
        title?.text = titleText()
        pickButton?.text = pickText()
        paintModes()
        order.refresh()
    }

    private fun paintModes() {
        modes.removeAllViews()
        val st = deps.state()
        val count = EffectiveLayout.slotCount(st.preset, st.customLayout)
        var outside = false
        items.forEach { sc ->
            val chips = ShortcutSelection.slotChips(count, sc.mode)
            if (chips.any { !it.second }) outside = true
            val options = chips.map { (n, _) -> AppShortcutCodec.modeCode(ShortcutMode.Slot(n)) to context.getString(R.string.kachi_sc_mode_slot, n) } +
                listOf(
                    AppShortcutCodec.modeCode(ShortcutMode.Full) to context.getString(R.string.kachi_sc_mode_full),
                    AppShortcutCodec.modeCode(ShortcutMode.Background) to context.getString(R.string.kachi_sc_mode_bg),
                )
            val row = rows.chipRow(appLabel(sc.pkg), options, AppShortcutCodec.modeCode(sc.mode)) { picked ->
                save(ShortcutSelection.setMode(items, sc.pkg, AppShortcutCodec.modeOf(picked)))
            }
            // Chip ô NGOÀI bố cục: vẫn bày (lựa chọn hiện tại không được giấu), nhưng MỜ — chạm lúc này sẽ mở toàn màn.
            chips.forEachIndexed { i, (_, inLayout) -> if (!inLayout) (row as? ViewGroup)?.getChildAt(1 + i)?.alpha = OUTSIDE_ALPHA }
            modes.addView(row)
        }
        if (outside) modes.addView(rows.note(context.getString(R.string.kachi_sc_slot_outside, count)))
        // L8: dòng nhắc "Chạy ngầm cần ít nhất một ô app" GỠ — bố cục không có ô app sống thì lối tắt chạy qua màn ảo ẩn
        // (`ShortcutAction.StartBehindHidden`), không còn điều kiện nào để nhắc trước.
    }

    private fun titleText() = context.getString(R.string.kachi_sc_section, items.size)
    private fun pickText() = context.getString(R.string.kachi_sc_pick_n, items.size, AppShortcutCodec.MAX)

    /** Nhãn app; đã gỡ ⇒ tên gói + "chưa cài" (vẫn hiện để người dùng thấy và bỏ được nó). */
    private fun appLabel(pkg: String): String =
        InstalledApps.labelOf(context, pkg) ?: context.getString(R.string.kachi_sc_not_installed, pkg)

    private companion object {
        const val OUTSIDE_ALPHA = 0.4f
    }
}
