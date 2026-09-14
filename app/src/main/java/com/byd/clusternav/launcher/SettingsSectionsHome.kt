package com.byd.clusternav.launcher

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import com.byd.clusternav.R

/**
 * Nội dung nhóm **"Màn hình chính"** của màn Cài đặt — bố cục · hình nền.
 *
 * ## ⚠⚠ T4 · nhóm này vừa NGẮN ĐI HAI PHẦN BA — IA v2 · R-UI (a)(m)
 * Bản S1 gom bốn mảng và [ĐO ảnh 2026-09-12] dài **10.5 màn cuộn** (nhóm ngắn nhất: 0.18 — chênh 58×). Chip thanh
 * trạng thái + thanh nút xe đã rời sang [SettingsBarsSection], và lưới 123 ô thì bỏ hẳn khỏi Settings (mở bộ chọn
 * của ngăn kéo thay thế). Sau S4 (mục cảnh rời đi, xem ngay dưới) còn đúng **hai** mảng trả lời cùng một câu
 * hỏi: *"màn chính trông thế nào"*.
 *
 * ## ⚠⚠ S4 · R1 — MỤC "CẢNH" ĐÃ RỜI KHỎI ĐÂY HẲN
 * P7/P6 đặt *cảnh* đứng đầu trang này (cả bộ bố cục + ô + thanh nút, có cả "cảnh lúc nổ máy"). S4 bỏ hẳn khái niệm
 * đó: owner 2026-09-14 *"có cảnh rồi có hồ sơ nữa hơi khó hiểu"*, và backlog P7 đã ghi *"đọc kỹ hai dòng thì chúng
 * là CÙNG một khái niệm"*. Chức năng không mất — nó **lên cấp**: mỗi cảnh cũ thành một hồ sơ cùng tên (R2, chuyển
 * dữ liệu ở `:core`), còn "cảnh lúc nổ máy" thành "hồ sơ lúc nổ máy" ở nhóm Hồ sơ tài xế (R6).
 *
 * Thứ tự theo [SettingsCatalog.entriesOf] cho [SettingsGroup.HOME]: bố cục → hình nền — từ **khung** ra **nội
 * dung**, vì chọn bố cục trước thì các lựa chọn sau mới có nghĩa.
 */
class SettingsHomeSection(
    private val context: Context,
    private val rows: SettingsRows,
    private val deps: SettingsDeps,
) {

    fun build(body: LinearLayout) {
        // Owner 2026-09-14: mọi thứ ở nhóm này lưu THEO HỒ SƠ đang dùng — nói ra ngay đầu trang, vì rail chỉ ghi
        // "Hồ sơ tài xế" ở một nhóm khác và không ai nhìn thấy mối gắn. S4 · R8 nới câu đó ra CẢ màn Cài đặt (trừ
        // ba nhóm theo-xe), vì từ R3 hồ sơ giữ tất cả — câu chỉ nói "nhóm này" sẽ làm người đọc tưởng chủ đề/đơn vị
        // ở nhóm khác là chung cho cả máy.
        body.addView(rows.note(context.getString(R.string.kachi_home_profile_note, ProfileNames.display(deps.state().activeProfile))))
        layout(body)
        wallpaper(body)
    }

    // ── Bố cục ───────────────────────────────────────────────────────────────────────────────────

    /**
     * Năm bố cục sẵn + **chip "Tự vẽ"** + đường mở bảng vẽ.
     *
     * ## ⚠ Vì sao phải có chip "Tự vẽ" (R-UI **o**)
     * [ĐO ảnh 2026-09-12] đang dùng bố cục tự vẽ thì hàng này có **0/5 pill sáng** — một hàng chọn mà không lựa
     * chọn nào được chọn đọc ra như *"chưa đặt gì"*, trong khi sự thật là *"đang dùng một bố cục không nằm trong
     * năm cái này"*. Bản vá trước (truyền mã không khớp `""`) chữa được câu **sai** nhưng đẻ ra câu **trống**.
     * Nay dải chip có đủ sáu lựa chọn nên **luôn có đúng một** chip sáng, và chip "Tự vẽ" bấm được: nó mở bảng vẽ.
     *
     * Chip nào sáng do [EffectiveLayout.highlightedPreset] quyết (`null` ⇒ đang dùng tự vẽ) — cùng nguồn với thứ
     * màn hình THẬT SỰ vẽ, nên bố cục tự vẽ *lưu rồi mà không dùng được* vẫn sáng đúng ô bố cục sẵn đang thay nó.
     *
     * ## ⚠ S4 · R7 — ĐÂY LÀ BỀ MẶT CHỌN BỐ CỤC **DUY NHẤT**
     * §4.5 của IA v2 giữ 5 nút bố cục ở thanh trên làm bề mặt thứ hai (*"đổi bố cục là việc hằng ngày"*). S4 gỡ chúng
     * (owner: *"bỏ luôn các nút đổi bố cục trên header"*) vì việc hằng ngày nay là **đổi hồ sơ** — cả bộ, một cú
     * chạm. Hàng chip dưới đây vì thế là chỗ duy nhất chọn bố cục sẵn; intent [SettingsDeps.onPreset] KHÔNG đổi, nên
     * hành vi "chọn bố cục sẵn thì bỏ bố cục tự vẽ" vẫn nằm ở đúng một chỗ như trước.
     */
    private fun layout(body: LinearLayout) {
        val s = deps.state()
        body.addView(rows.sectionLabel(context.getString(R.string.kachi_sec_layout)))
        val summary = rows.note(layoutSummary(s.customLayout))
        body.addView(summary)
        val options = LayoutPreset.values().map { it.name to it.label } +
            (CUSTOM_CODE to context.getString(R.string.kachi_layout_custom_chip))
        body.addView(rows.chipRow(
            context.getString(R.string.kachi_row_preset),
            options,
            EffectiveLayout.highlightedPreset(s.preset, s.customLayout)?.name ?: CUSTOM_CODE,
        ) { code ->
            if (code == CUSTOM_CODE) {
                deps.onOpenLayoutEditor()
                return@chipRow
            }
            LayoutPreset.values().firstOrNull { it.name == code }?.let { deps.onPreset(it) }
            // ⚠ [SOÁT S1 · P2] Chọn bố cục sẵn = **BỎ bố cục tự vẽ** (`KachiHomeActivity.selectPreset`), nên dòng
            // mô tả phía trên vừa thành SAI. Trang này được **nhớ lại** (xem [SettingsPanel]) nên nó không tự dựng
            // lại ⇒ phải sửa CHỮ tại chỗ, đọc lại từ nguồn sự thật (intent chạy đồng bộ).
            summary.text = layoutSummary(deps.state().customLayout)
        })
        body.addView(rows.button(context.getString(R.string.kachi_layout_open_editor)) { deps.onOpenLayoutEditor() })
    }

    /**
     * Nói người dùng đang dùng bố cục nào — và **nếu bố cục tự vẽ bị bỏ qua thì nói lý do**.
     *
     * Im lặng ở chỗ này là ca xấu nhất: bố cục tự vẽ nhiều khung hơn trần ô (xảy ra thật khi hạ cấp bản) sẽ bị lùi về
     * bố cục sẵn, và nếu không ai nói thì người dùng thấy "đã lưu bố cục" mà màn hình khác hẳn.
     */
    private fun layoutSummary(custom: GridLayout?): String = custom?.let {
        // ⚠ finding #18 — `getQuantityString`, không phải `getString`: bản một-chuỗi in "1 frames" ở tiếng Anh.
        context.resources.getQuantityString(R.plurals.kachi_layout_custom_in_use, it.frames.size, it.frames.size) +
            (EffectiveLayout.ignoredReason(it)?.let { r -> context.getString(R.string.kachi_layout_ignored, r) } ?: "")
    } ?: context.getString(R.string.kachi_layout_preset_in_use)

    // ── Hình nền & trình chiếu ───────────────────────────────────────────────────────────────────

    /**
     * Bốn dòng: bật/tắt · chu kỳ đổi ảnh · cách phủ · làm tối.
     *
     * Câu phụ phải nói **chỗ bỏ ảnh vào** ([SettingsDeps.wallpaperFolderHint]): người dùng không có cách nào tự đoán
     * đường dẫn, và màn chọn tệp của hệ thống bị khoá trên xe.
     */
    private fun wallpaper(body: LinearLayout) {
        body.addView(rows.sectionLabel(context.getString(R.string.kachi_sec_wallpaper)))
        var wp = deps.state().wallpaper
        // ⚠ [SOÁT UI 2026-09-12] Ba dãy pill dưới (chu kỳ · cách phủ · làm tối) CHỈ có nghĩa khi hình nền đang BẬT.
        // Trước đây chúng luôn hiện + bấm được kể cả khi công tắc TẮT ⇒ 12 pill "chết" (bấm không đổi gì thấy được)
        // — đúng luật cấm nút-chết của dự án. Nay ẩn hẳn khi tắt, hiện lại khi bật (đổi ngay, không mở lại trang).
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

    private companion object {
        /**
         * Mã của chip "Tự vẽ".
         *
         * KHÔNG thể là một giá trị của [LayoutPreset] — enum đó là **danh sách bố cục sẵn** và có bài canh đếm
         * đúng năm cái. Mã riêng phải KHÁC mọi `LayoutPreset.name`; dấu `__` hai đầu là khuôn sentinel đã dùng ở
         * `Prefs.VK_TARGET_*`, chọn lại cho nhất quán chứ không phát minh khuôn mới.
         */
        const val CUSTOM_CODE = "__CUSTOM__"
    }
}
