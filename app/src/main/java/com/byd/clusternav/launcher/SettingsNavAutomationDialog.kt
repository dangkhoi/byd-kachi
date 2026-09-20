package com.byd.clusternav.launcher

import android.app.AlertDialog
import android.content.Context
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.byd.clusternav.R
import com.byd.clusternav.launcher.automation.ScheduledNavRule
import com.byd.clusternav.launcher.automation.ScheduledNavRules
import com.byd.clusternav.launcher.voice.VoiceAppTargets

/** Giờ-phút → `"07:30"`, và tên thứ — dùng chung bởi hàng danh sách và hộp sửa (một chỗ khai). */
internal object SettingsNavAutomationFormat {

    /** Phút-trong-ngày → `"HH:mm"`. Kẹp vào dải hợp lệ để một giá trị rác trên đĩa không in ra `"25:73"`. */
    fun hhmm(minOfDay: Int): String {
        val m = minOfDay.coerceIn(0, ScheduledNavRules.MAX_MIN)
        return "%02d:%02d".format(m / MIN_PER_HOUR, m % MIN_PER_HOUR)
    }

    /**
     * `"07:30"` → 450; hỏng ⇒ `null` (chỗ gọi nói ra, không đoán). Nhận cả `"7:30"`, `"7h30"` và `"7 30"`.
     *
     * ⚠ Không viết dải `0..23`/`0..59` tại chỗ: `GridSeamGuardTest` quét đúng hình dạng đó (nó canh *"không ai
     * viết cứng khoảng ô"*), và ở đây trần giờ vốn **suy ra được** từ [ScheduledNavRules.MAX_MIN] — một nguồn, chứ
     * không phải hai con số chép tay có thể lệch nhau.
     */
    fun parseHhmm(raw: String): Int? {
        val parts = raw.trim().split(':', 'h', ' ', '.').filter { it.isNotBlank() }
        if (parts.size != 2) return null
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        if (h < 0 || m < 0 || m >= MIN_PER_HOUR) return null
        val total = h * MIN_PER_HOUR + m
        return total.takeIf { it <= ScheduledNavRules.MAX_MIN }
    }

    /** Phút trong một giờ — nguồn của cả phép in ([hhmm]) lẫn phép đọc ([parseHhmm]). */
    private const val MIN_PER_HOUR = 60

    /** Tập thứ → `"T2 T3 T4 T5 T6"`; cả tuần ⇒ một chữ gọn. Thứ tự luôn theo ISO (sắp trước khi in). */
    fun days(context: Context, days: Set<Int>): String {
        if (days.containsAll(ScheduledNavRules.ALL_DAYS)) return context.getString(R.string.kachi_days_all)
        if (days == ScheduledNavRules.WEEKDAYS) return context.getString(R.string.kachi_days_weekdays)
        return days.sorted().joinToString(" ") { context.getString(dayLabel(it)) }
    }

    /** Nhãn một thứ. Bảng `when` (không mảng chỉ số) để lệch-một-ô không thể xảy ra im lặng. */
    fun dayLabel(dow: Int): Int = when (dow) {
        ScheduledNavRules.MON -> R.string.kachi_day_mon
        ScheduledNavRules.TUE -> R.string.kachi_day_tue
        ScheduledNavRules.WED -> R.string.kachi_day_wed
        ScheduledNavRules.THU -> R.string.kachi_day_thu
        ScheduledNavRules.FRI -> R.string.kachi_day_fri
        ScheduledNavRules.SAT -> R.string.kachi_day_sat
        else -> R.string.kachi_day_sun
    }

    /**
     * Nhãn thương hiệu app dẫn đường (danh từ riêng, VI = EN) — tra qua **tài nguyên của launcher**.
     *
     * ## Vì sao KHÔNG đọc `VoiceAppTarget.label` của `:core`
     * `LauncherI18nContractTest.tang ve khong doc nhan GOC cua core` cấm đúng việc đó: theo giao kèo
     * [com.byd.clusternav.launcher.Localized] nhãn gốc của `:core` **luôn tiếng Việt**, nên một chỗ vẽ đọc nó sẽ
     * không đổi khi người dùng chọn English — đúng lỗi *"rail tiếng Việt / nội dung tiếng Anh"* mà máy ảo đã chụp
     * được. Với tên app thì hai bản trùng nhau, nhưng luật là luật ở **hình dạng** chứ không ở từng ca.
     *
     * Rút ra đây (thay vì để mỗi bề mặt một bản `when`) vì nay có **hai** chỗ cần nó: chip *"App dẫn đường mặc
     * định"* của [SettingsNavSection] và hai bề mặt của lịch tự dẫn. Bản thứ hai chép tay là chỗ để hai bên hiện
     * hai cái tên khác nhau cho cùng một app.
     */
    fun navAppLabel(context: Context, key: String): String = context.getString(
        when (key) {
            "vietmap" -> R.string.kachi_nav_app_vietmap
            "waze" -> R.string.kachi_nav_app_waze
            else -> R.string.kachi_nav_app_gmaps
        },
    )
}

/**
 * ═══ HỘP THÊM/SỬA một luật dẫn-đường-theo-lịch ═══════════════════════════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-automation.html` R2.2 — sáu thứ trong một hộp: **bật/tắt · giờ bắt đầu–kết thúc · thứ
 * T2–CN · chỉ-khi-có-GPS · điểm đến (từ sổ địa chỉ) · app dẫn đường**.
 *
 * ## Vì sao tệp RIÊNG, không thêm hàm thứ năm vào [SettingsDialogs]
 * KDoc [SettingsDialogs] cấm **bản dựng `AlertDialog` thứ hai** cho cùng một việc, và ba việc nó gom là *chọn một
 * mục · hỏi lại · hỏi một cái tên*. Hộp này không phải việc nào trong ba: nó là một **biểu mẫu sáu trường** có
 * lưới 7 ô tick. Nhồi nó vào tệp kia đẩy tệp đó từ 209 lên ~360 dòng và trộn hai loại trách nhiệm; giữ riêng thì
 * ba quyết định dùng chung (nút huỷ · nhãn nút lưu · nút xoá ở giữa) vẫn **chép đúng khuôn** của tệp kia.
 *
 * ## Giờ là Ô GÕ `"HH:mm"`, không phải `TimePickerDialog`
 * Một luật cần **hai** mốc giờ, mà `TimePickerDialog` là một hộp thoại riêng cho mỗi mốc ⇒ chuỗi *hộp → hộp →
 * hộp*, và hai hộp con chồng lên hộp cha là đúng ca mà [ĐO] 2026-09-12 đã bắt được trên xe (*"không gõ được tên
 * cảnh khi có app đang chiếu trong ô"* — tiêu điểm không chuyển). Một ô chữ giữ mọi thứ trong **một** hộp, và
 * phép đọc nằm ở [SettingsNavAutomationFormat.parseHhmm] (thuần, nhận cả `7:30`/`7h30`).
 */
internal object SettingsNavAutomationDialog {

    /** Dữ liệu người dùng vừa điền — **chưa** kiểm; [ScheduledNavRules.of] mới là nơi chốt. */
    data class Draft(
        val enabled: Boolean,
        val startMin: Int,
        val endMin: Int,
        val days: Set<Int>,
        val requireGps: Boolean,
        val placeId: String,
        val navApp: String,
    )

    /** Khung giờ mặc định của luật MỚI: 7:00–9:00 — đúng usecase gốc của spec (*"sáng đến cty"*). */
    private const val DEFAULT_START = 7 * 60
    private const val DEFAULT_END = 9 * 60

    /**
     * Mở hộp. [onOk] nhận [Draft] **thô**: mọi phép kiểm (khung qua đêm · thứ rỗng · giờ ngoài dải) là việc của
     * `:core`, nên hộp này không giữ một luật nào — xem KDoc [SettingsNavAutomationSection.edit].
     *
     * @param initial luật đang sửa, `null` = thêm mới.
     * @param places sổ địa chỉ của hồ sơ đang dùng — **đã được chỗ gọi bảo đảm là không rỗng**.
     * @param onDelete đường XOÁ (`null` khi thêm mới ⇒ không có nút xoá). Xoá nằm TRONG hộp sửa, cùng lý do
     *   [SettingsDialogs.askPlace]: một nút xoá không hoàn lại được ngay cạnh nút sửa trên hàng danh sách là ca
     *   bấm nhầm kinh điển khi xe xóc.
     */
    fun ask(
        context: Context,
        title: String,
        initial: ScheduledNavRule?,
        places: List<SavedPlace>,
        onDelete: (() -> Unit)? = null,
        onOk: (Draft) -> Unit,
    ) {
        val pad = KachiTheme.dpi(context, KachiSpace.M)
        val enabled = CheckBox(context).apply {
            text = context.getString(R.string.kachi_nav_auto_enabled)
            isChecked = initial?.enabled ?: true
        }
        val start = timeField(context, initial?.startMin ?: DEFAULT_START)
        val end = timeField(context, initial?.endMin ?: DEFAULT_END)
        val dayBoxes = dayBoxes(context, initial?.days ?: ScheduledNavRules.WEEKDAYS)
        val gps = CheckBox(context).apply {
            text = context.getString(R.string.kachi_nav_auto_gps)
            // Mặc định BẬT cho luật mới: usecase gốc là xe đỗ trong hầm (R2.4). Mặc định tắt thì lần dùng đầu
            // tiên sẽ dẫn trong hầm và người dùng kết luận tính năng hỏng.
            isChecked = initial?.requireGps ?: true
        }
        // Điểm đến + app: hai dãy nút một-dòng-một-lựa-chọn. KHÔNG `Spinner` — [ĐO] dự án đã bỏ `Spinner` khỏi
        // Settings từ IA v2 (chip/nút có đích chạm ≥ TOUCH, `Spinner` thì mở một cửa sổ thả xuống thứ hai).
        val placeIds = places.map { ScheduledNavRules.placeIdOf(it) }
        val placePick = Picker(context, places.map { it.name }, placeIds.indexOf(initial?.placeId).coerceAtLeast(0))
        // Khoá app lấy từ `:core` (nguồn sự thật), NHÃN tra tài nguyên launcher — xem KDoc
        // [SettingsNavAutomationFormat.navAppLabel] về vì sao không đọc `VoiceAppTarget.label`.
        val appKeys = VoiceAppTargets.NAV.map { it.key }
        val appPick = Picker(
            context,
            appKeys.map { SettingsNavAutomationFormat.navAppLabel(context, it) },
            appKeys.indexOf(initial?.navApp).coerceAtLeast(0),
        )

        val body = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, 0)
            addView(enabled)
            addView(label(context, R.string.kachi_nav_auto_window))
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(start, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                addView(TextView(context).apply {
                    text = " – "
                    setTextColor(KachiTheme.c(KachiTheme.MUT))
                })
                addView(end, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            })
            addView(label(context, R.string.kachi_nav_auto_days))
            addView(dayRow(context, dayBoxes))
            addView(gps)
            addView(label(context, R.string.kachi_nav_auto_place))
            addView(placePick.view)
            addView(label(context, R.string.kachi_nav_auto_app))
            addView(appPick.view)
        }
        AlertDialog.Builder(context)
            .setTitle(title)
            // Cuộn: bảy ô tick + hai dãy nút vượt chiều cao hộp trên màn xe 720px ⇒ không cuộn thì nút Lưu của
            // `AlertDialog` bị đẩy ra ngoài và người dùng không lưu được (cùng bệnh "hàng nút bị cắt đáy" mà
            // `GroupTileView.onMeasure` đã gặp ở U5).
            .setView(ScrollView(context).apply { addView(body) })
            .setPositiveButton(context.getString(R.string.kachi_save)) { _, _ ->
                onOk(
                    Draft(
                        enabled = enabled.isChecked,
                        // Giờ gõ sai ⇒ đưa **−1** xuống `:core`, để chính `of()` từ chối và chỗ gọi nói ra. Lùi
                        // về một giá trị mặc định ở đây sẽ lưu một khung giờ người dùng KHÔNG gõ, im lặng.
                        startMin = SettingsNavAutomationFormat.parseHhmm(start.text.toString()) ?: -1,
                        endMin = SettingsNavAutomationFormat.parseHhmm(end.text.toString()) ?: -1,
                        days = dayBoxes.filterValues { it.isChecked }.keys,
                        requireGps = gps.isChecked,
                        placeId = placeIds.getOrElse(placePick.chosen) { "" },
                        navApp = appKeys.getOrElse(appPick.chosen) { "" },
                    ),
                )
            }
            .setNegativeButton(android.R.string.cancel, null)
            .also { b ->
                onDelete?.let { del -> b.setNeutralButton(context.getString(R.string.kachi_delete)) { _, _ -> del() } }
            }
            .show()
    }

    private fun label(context: Context, res: Int) = TextView(context).apply {
        text = context.getString(res)
        setTextColor(KachiTheme.c(KachiTheme.MUT))
        KachiType.apply(this, KachiType.CAPTION)
    }

    private fun timeField(context: Context, minOfDay: Int) = EditText(context).apply {
        setText(SettingsNavAutomationFormat.hhmm(minOfDay))
        // `TYPE_CLASS_DATETIME | VARIATION_TIME` cho bàn phím số có dấu `:` — gõ giờ trong xe bằng bàn phím chữ
        // đầy đủ là ba lần chuyển bảng cho bốn chữ số.
        inputType = InputType.TYPE_CLASS_DATETIME or InputType.TYPE_DATETIME_VARIATION_TIME
    }

    /** Bảy ô tick, khoá là **thứ ISO** (không phải chỉ số) ⇒ không có phép đổi chỉ số nào để lệch. */
    private fun dayBoxes(context: Context, checked: Set<Int>): Map<Int, CheckBox> =
        ScheduledNavRules.ALL_DAYS.sorted().associateWith { dow ->
            CheckBox(context).apply {
                text = context.getString(SettingsNavAutomationFormat.dayLabel(dow))
                isChecked = dow in checked
            }
        }

    /** Bảy ô trên MỘT hàng ngang, chia đều — vừa màn xe và đọc ra ngay là "một tuần". */
    private fun dayRow(context: Context, boxes: Map<Int, CheckBox>): View =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            boxes.values.forEach { box ->
                addView(box, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            }
        }

    /**
     * Dãy nút chọn-một trong hộp thoại: mỗi lựa chọn một hàng, hàng đang chọn tô nền nhấn.
     *
     * Dùng [SettingsRows.chipRow] thì phải dựng một [SettingsRows] mới bên trong hộp (nó nhận `Context` và mang
     * lề **stack của trang Cài đặt** — sai ngữ cảnh trong một hộp thoại). Đây là bản gọn cho đúng bối cảnh hộp,
     * và nó **không** dựng một hình dạng nút thứ hai: nền lấy đúng `KachiTheme.gradient`/`card` mà chip đang dùng.
     */
    private class Picker(context: Context, labels: List<String>, initial: Int) {
        var chosen: Int = initial.coerceIn(0, (labels.size - 1).coerceAtLeast(0))
            private set

        private val items = ArrayList<TextView>(labels.size)

        val view: View = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            labels.forEachIndexed { index, text ->
                val tv = TextView(context).apply {
                    this.text = text
                    gravity = Gravity.CENTER_VERTICAL
                    KachiType.apply(this, KachiType.BODY)
                    val p = KachiTheme.dpi(context, KachiSpace.S)
                    setPadding(KachiTheme.dpi(context, KachiSpace.M), p, KachiTheme.dpi(context, KachiSpace.M), p)
                    minHeight = KachiTheme.dpi(context, KachiSpace.TOUCH)
                    setOnClickListener {
                        chosen = index
                        paint(context)
                    }
                }
                items += tv
                addView(
                    tv,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    ).also { it.bottomMargin = KachiTheme.dpi(context, KachiSpace.XS) },
                )
            }
            paint(context)
        }

        private fun paint(context: Context) = items.forEachIndexed { index, tv ->
            val on = index == chosen
            tv.setTextColor(KachiTheme.c(if (on) KachiTheme.ON_ACCENT else KachiTheme.INK))
            tv.background = if (on) {
                KachiTheme.gradient(context, KachiSpace.RADIUS_L)
            } else {
                KachiTheme.card(context, KachiSpace.RADIUS_L, KachiTheme.CHIP_OFF)
            }
        }
    }
}
