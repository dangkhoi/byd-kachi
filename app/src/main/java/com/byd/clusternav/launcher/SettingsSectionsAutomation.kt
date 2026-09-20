package com.byd.clusternav.launcher

import android.content.Context
import android.widget.LinearLayout
import com.byd.clusternav.R
import com.byd.clusternav.launcher.automation.NavAutomationBook
import com.byd.clusternav.launcher.automation.ScheduledNavRule
import com.byd.clusternav.launcher.automation.ScheduledNavRules

/**
 * ═══ AUTOMATION #2 · **TỰ DẪN ĐƯỜNG THEO LỊCH** — danh sách luật + màn thêm/sửa ═══════════════════════════════
 *
 * Spec `docs/specs/kachi-automation.html` R2.1 · R2.2. Mục con **thứ hai** của nhóm *Dẫn đường & cụm đồng hồ*,
 * đứng ngay sau [SettingsPlacesSection] — và thứ tự đó là một quyết định: một luật **không dựng được** khi sổ địa
 * chỉ còn trống (nó chọn điểm đến TỪ sổ), nên hai khối phải đọc theo đúng thứ tự phụ thuộc đó.
 *
 * ## Tách tệp, cùng lý do [SettingsPlacesSection] đã tách
 * `SettingsSectionsNav.kt` đã 425 dòng (trần 500), và khối này không liên quan tới nó: một bên là cấu hình **cụm
 * đồng hồ** đọc `bridge`, một bên là **dữ liệu lịch** của chiếc xe. Trộn vào là đẩy tệp kia qua trần ở lần sửa sau.
 *
 * ## Danh sách dựng lại TẠI CHỖ
 * Trang Cài đặt được **nhớ lại** ([SettingsPanel.pages]) nên nó không tự làm mới. Giữ [ruleList] và chỉ nạp lại
 * mình nó ([rebuild]) — cùng cách [SettingsPlacesSection]/[SettingsKeysSection] làm, và cùng lý do: dựng lại cả
 * trang là vứt chỗ đang cuộn.
 *
 * ## ⚠ Mọi phép sửa sổ là hàm THUẦN ở `:core`
 * [NavAutomationBook.upsert] / `remove` / `newId` / [ScheduledNavRules.of] — tầng vẽ này **không giữ một luật
 * nào**. Nó chỉ: đọc sổ qua cầu, gọi hàm thuần, ghi cả sổ đã chốt qua **một** cổng ([ClusterNavBridge.setNavRules]).
 * Đó là lý do luật *"khung qua đêm bị từ chối"* và *"id không được trùng"* kiểm được off-car.
 */
class SettingsNavAutomationSection(
    private val context: Context,
    private val rows: SettingsRows,
    private val deps: SettingsDeps,
) {

    private val bridge get() = deps.bridge

    /** Khung chứa các hàng luật — giữ tham chiếu để [rebuild] nạp lại đúng nó (xem KDoc lớp). */
    private val ruleList = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

    fun build(body: LinearLayout) {
        body.addView(rows.sectionLabel(context.getString(R.string.kachi_sec_nav_auto)))
        body.addView(rows.note(context.getString(R.string.kachi_nav_auto_note)))
        body.addView(ruleList)
        rebuild()
        body.addView(rows.button(context.getString(R.string.kachi_nav_auto_add)) { edit(null) })
    }

    /**
     * Nạp lại danh sách từ **nguồn sự thật** (cầu → prefs → `NavAutomationBook.decode`).
     *
     * ⚠ Đọc lại qua cầu mỗi lượt chứ không giữ một bản chụp: một bản chụp cũ ở đây là cách chắc chắn để xoá một
     * luật rồi thấy nó quay lại ở lượt sửa tiếp theo (đúng bài học [SettingsPlacesSection.rebuild]).
     */
    private fun rebuild() {
        ruleList.removeAllViews()
        val rules = bridge.navRules()
        if (rules.isEmpty()) {
            ruleList.addView(rows.note(context.getString(R.string.kachi_nav_auto_empty)))
            return
        }
        rules.forEach { rule ->
            ruleList.addView(
                rows.listRow(
                    title = title(rule),
                    sub = subtitle(rule),
                    // Nhãn **Sửa** (không phải Xoá): sửa giờ/thứ là việc hay làm, xoá là việc một lần — và xoá
                    // nằm trong chính hộp sửa, sau khi người dùng đã thấy mình đang đứng ở luật nào.
                    actionLabel = context.getString(R.string.kachi_edit),
                ) { edit(rule) },
            )
        }
        if (rules.size >= NavAutomationBook.MAX) {
            ruleList.addView(rows.note(context.getString(R.string.kachi_nav_auto_full, NavAutomationBook.MAX)))
        }
    }

    /** Tiêu đề hàng: *"07:30–09:00 → công ty"*, hoặc kèm *"(đang tắt)"* khi luật bị tắt. */
    private fun title(rule: ScheduledNavRule): String {
        val base = context.getString(
            R.string.kachi_nav_auto_row,
            SettingsNavAutomationFormat.hhmm(rule.startMin),
            SettingsNavAutomationFormat.hhmm(rule.endMin),
            rule.placeId,
        )
        return if (rule.enabled) base else context.getString(R.string.kachi_nav_auto_row_off, base)
    }

    /**
     * Dòng phụ: thứ trong tuần · app dẫn đường · có đòi GPS hay không.
     *
     * Nói ra **cả ba** vì cả ba đều quyết định luật có nổ hay không, và hai trong số đó là nguyên nhân hay gặp
     * nhất của câu *"sao nó không dẫn"*: sai thứ (đặt T2–T6 rồi thử vào Chủ Nhật) và đang chờ GPS (trong hầm).
     */
    private fun subtitle(rule: ScheduledNavRule): String {
        val days = SettingsNavAutomationFormat.days(context, rule.days)
        val app = SettingsNavAutomationFormat.navAppLabel(context, rule.navApp)
        val gps = context.getString(
            if (rule.requireGps) R.string.kachi_nav_auto_gps_on else R.string.kachi_nav_auto_gps_off,
        )
        return "$days · $app · $gps"
    }

    /**
     * Thêm ([rule] = `null`) hoặc sửa một luật.
     *
     * Hai cổng chặn TRƯỚC khi mở hộp, mỗi cổng là một cú bấm **không có tác dụng** nếu bỏ qua — và luật của dự án
     * là *"cú bấm không có tác dụng thì phải NÓI lý do"* (`TopStripPicker.toggle`):
     *  1. **sổ địa chỉ trống** ⇒ không có điểm đến nào để chọn. Nói ra + chỉ tới đúng chỗ thêm địa chỉ.
     *  2. **sổ luật đã đầy** ([NavAutomationBook.MAX]) ⇒ `upsert` sẽ trả về sổ **không đổi**, tức hộp mở ra, người
     *     dùng điền xong, bấm Lưu, và không có gì xảy ra.
     */
    private fun edit(rule: ScheduledNavRule?) {
        val places = deps.state().savedPlaces
        if (places.isEmpty()) {
            SettingsDialogs.notice(
                context,
                context.getString(R.string.kachi_nav_auto_add),
                context.getString(R.string.kachi_nav_auto_need_place),
            )
            return
        }
        if (rule == null && bridge.navRules().size >= NavAutomationBook.MAX) {
            SettingsDialogs.notice(
                context,
                context.getString(R.string.kachi_nav_auto_add),
                context.getString(R.string.kachi_nav_auto_full, NavAutomationBook.MAX),
            )
            return
        }
        SettingsNavAutomationDialog.ask(
            context = context,
            title = context.getString(
                if (rule == null) R.string.kachi_nav_auto_add else R.string.kachi_nav_auto_edit,
            ),
            initial = rule,
            places = places,
            onDelete = rule?.let { r -> { apply(NavAutomationBook.remove(bridge.navRules(), r.id)) } },
        ) { draft ->
            // `of()` trả `null` cho mọi dữ liệu KHÔNG DÙNG ĐƯỢC (không thứ nào được tick · khung qua đêm · giờ
            // ngoài dải). Nói ra thay vì lặng lẽ bỏ qua cú bấm Lưu — một luật lưu xuống rồi nằm im là đúng thứ
            // người dùng tưởng đã đặt xong (cùng lẽ `SavedPlaces.of`).
            val made = ScheduledNavRules.of(
                id = rule?.id ?: NavAutomationBook.newId(bridge.navRules()),
                enabled = draft.enabled,
                startMin = draft.startMin,
                endMin = draft.endMin,
                days = draft.days,
                requireGps = draft.requireGps,
                placeId = draft.placeId,
                navApp = draft.navApp,
            )
            if (made == null) {
                SettingsDialogs.notice(
                    context,
                    context.getString(R.string.kachi_nav_auto_add),
                    context.getString(R.string.kachi_nav_auto_bad),
                )
                return@ask
            }
            apply(NavAutomationBook.upsert(bridge.navRules(), made))
        }
    }

    private fun apply(rules: List<ScheduledNavRule>) {
        bridge.setNavRules(rules)
        rebuild()
    }
}
