package com.byd.clusternav.launcher

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.view.View
import android.widget.LinearLayout
import com.byd.clusternav.R
import com.byd.clusternav.launcher.automation.DayPreset
import com.byd.clusternav.launcher.automation.ScheduledNavRule
import com.byd.clusternav.launcher.automation.ScheduledNavRules
import com.byd.clusternav.launcher.voice.VoiceAppTargets
import com.byd.clusternav.launcher.KachiSpace as Sp

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
 * ═══ HỘP THÊM/SỬA một luật dẫn-đường-theo-lịch — KACHI-301 R12, khuôn Biểu mẫu trên [KachiDialog] ═══════════════
 *
 * Spec `docs/specs/kachi-automation.html` R2.2 — sáu thứ trong một hộp: **bật/tắt · giờ bắt đầu–kết thúc · thứ
 * T2–CN · chỉ-khi-có-GPS · điểm đến (từ sổ địa chỉ) · app dẫn đường**. Hình theo mẫu §4.4 *"Lịch dẫn đường"* của
 * `kachi-301-cast-steps-dialog-ia.html`: hai ô giờ lớn + [TimeWheel] · 7 chip thứ tròn + chip *Ngày thường / Cuối
 * tuần / Mỗi ngày* · thẻ điểm đến · chip app (kèm icon app nếu máy có cài) · hai công tắc.
 *
 * ## MỘT hộp, không hộp con, không bàn phím — bài học tiêu điểm 2026-09-12 giữ nguyên
 * `TimePickerDialog` là một hộp con cho MỖI mốc giờ ⇒ chuỗi *hộp → hộp → hộp*, và hộp con chồng lên hộp cha là đúng ca
 * [ĐO] 2026-09-12 bắt được trên xe (*"không gõ được tên cảnh khi có app đang chiếu trong ô"* — tiêu điểm không chuyển).
 * Bản đầu né bằng ô gõ `"HH:mm"`; bản này thay ô gõ bằng [TimeWheel] nằm NGAY TRONG hộp ⇒ vẫn một hộp, lại không
 * mở bàn phím che màn. [SettingsNavAutomationFormat.parseHhmm] ở lại cho đường giọng nói / nhập tệp.
 *
 * ## Kiểm: một nguồn luật
 * Nút Lưu khoá khi [ScheduledNavRules.of] trả `null` cho bản nháp — đúng hàm mà [SettingsNavAutomationSection.edit]
 * gọi lúc lưu (khung qua đêm · thứ rỗng · không app). Không chép luật nào sang đây; câu lỗi dưới ô giờ chỉ NÓI.
 *
 * ## Dòng tóm tắt sống
 * Dòng phụ đầu hộp = *"T2–T6 · 07:00–09:00 → Công ty bằng VietMap"*, cập nhật sau MỌI thay đổi — người lái đọc
 * một dòng là biết luật sẽ làm gì, không phải dò lại sáu trường.
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

    /** Mã giả chỉ để hỏi [ScheduledNavRules.of] "bản nháp này lưu được không" — không bao giờ được lưu. */
    private const val CHECK_ID = "draft"

    /**
     * Mở hộp. [onOk] nhận [Draft] **thô**: mọi phép kiểm là việc của `:core` — xem KDoc [SettingsNavAutomationSection.edit].
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
        var handle: KachiDialog.Handle? = null
        var refresh: () -> Unit = {}

        // Mặc định BẬT GPS cho luật mới: usecase gốc là xe đỗ trong hầm (R2.4). Mặc định tắt thì lần dùng đầu tiên
        // sẽ dẫn trong hầm và người dùng kết luận tính năng hỏng.
        val enabled = KachiFormControls.toggle(context, context.getString(R.string.kachi_nav_auto_enabled),
            initial?.enabled ?: true) { refresh() }
        val gps = KachiFormControls.toggle(context, context.getString(R.string.kachi_nav_auto_gps),
            initial?.requireGps ?: true) { refresh() }
        val wheel = TimeWheel(context, initial?.startMin ?: DEFAULT_START, initial?.endMin ?: DEFAULT_END) { refresh() }

        // Thứ: khoá là THỨ ISO (không phải chỉ số) ⇒ không có phép đổi chỉ số nào để lệch.
        val days = (initial?.days ?: ScheduledNavRules.WEEKDAYS).toMutableSet()
        val dayChips = ScheduledNavRules.ALL_DAYS.sorted().associateWith { dow ->
            KachiFormControls.roundChip(context, context.getString(SettingsNavAutomationFormat.dayLabel(dow))) {
                if (!days.remove(dow)) days += dow
                refresh()
            }
        }
        val presetChips = DayPreset.entries.associateWith { p ->
            KachiFormControls.chip(context, context.getString(presetLabel(p))) {
                days.clear()
                days += p.days
                refresh()
            }
        }

        // Điểm đến: thẻ chọn-một. KHÔNG `Spinner` — [ĐO] dự án đã bỏ `Spinner` khỏi Settings từ IA v2 (nó mở một cửa
        // sổ thả xuống thứ hai — đúng thứ "hộp chồng hộp" ở trên).
        val placeIds = places.map { ScheduledNavRules.placeIdOf(it) }
        var placeAt = placeIds.indexOf(initial?.placeId).coerceAtLeast(0)
        val placeCards = places.mapIndexed { i, p ->
            KachiFormControls.selectCard(context, p.name, p.query) { placeAt = i; refresh() }
        }

        // App: NHIỀU lựa chọn (owner 2026-09-24 — tới giờ mở cả hai). Khoá lấy từ `:core`, NHÃN tra tài nguyên
        // launcher ([SettingsNavAutomationFormat.navAppLabel]). Luật mới ⇒ tick sẵn app đầu tiên (đỡ luật trống app).
        val appKeys = VoiceAppTargets.NAV.map { it.key }
        val apps = (initial?.navApps ?: listOfNotNull(appKeys.firstOrNull())).toMutableSet()
        val appChips = appKeys.associateWith { key ->
            KachiFormControls.chip(context, SettingsNavAutomationFormat.navAppLabel(context, key), appIcon(context, key)) {
                if (!apps.remove(key)) apps += key
                refresh()
            }
        }

        val error = KachiDialogs.caption(context, context.getString(R.string.kform_sched_invalid)).apply {
            setTextColor(KachiTheme.c(KachiTheme.RED))
            visibility = View.GONE
        }

        fun draft() = Draft(
            enabled = enabled.on,
            startMin = wheel.start,
            endMin = wheel.end,
            days = days.toSet(),
            requireGps = gps.on,
            placeId = placeIds.getOrElse(placeAt) { "" },
            // Thứ tự theo bảng `:core`, không theo thứ tự chạm ⇒ chuỗi lưu ổn định.
            navApp = appKeys.filter { it in apps }.joinToString("+"),
        )

        fun summary(): String {
            val d = draft()
            val dayText = if (d.days.isEmpty()) context.getString(R.string.kform_sched_no_days)
            else SettingsNavAutomationFormat.days(context, d.days)
            val appText = appKeys.filter { it in apps }
                .joinToString(" + ") { SettingsNavAutomationFormat.navAppLabel(context, it) }
                .ifEmpty { context.getString(R.string.kform_sched_no_app) }
            val line = context.getString(
                R.string.kform_sched_summary,
                dayText,
                SettingsNavAutomationFormat.hhmm(d.startMin),
                SettingsNavAutomationFormat.hhmm(d.endMin),
                places.getOrNull(placeAt)?.name.orEmpty(),
                appText,
            )
            return if (d.enabled) line else context.getString(R.string.kachi_nav_auto_row_off, line)
        }

        fun paint() {
            dayChips.forEach { (dow, chip) -> chip.on = dow in days }
            val preset = DayPreset.matching(days)
            presetChips.forEach { (p, chip) -> chip.on = p == preset }
            placeCards.forEachIndexed { i, card -> card.on = i == placeAt }
            appChips.forEach { (key, chip) -> chip.on = key in apps }
        }

        refresh = {
            paint()
            handle?.setSubtitle(summary())
            handle?.revalidate()
        }
        paint()

        handle = KachiDialogs.form(
            context,
            title = title,
            subtitle = summary(),
            icon = R.drawable.ic_clock_g,
            body = listOf(
                enabled.view,
                KachiFormControls.heading(context, context.getString(R.string.kachi_nav_auto_window)),
                wheel.view,
                error,
                KachiFormControls.heading(context, context.getString(R.string.kachi_nav_auto_days)),
                KachiFormControls.flow(context, dayChips.values.toList()),
                KachiFormControls.flow(context, presetChips.values.toList()),
                KachiFormControls.heading(context, context.getString(R.string.kachi_nav_auto_place)),
                stack(context, placeCards.map { it.view }),
                KachiFormControls.heading(context, context.getString(R.string.kachi_nav_auto_app)),
                KachiFormControls.flow(context, appChips.values.toList()),
                gps.view,
            ),
            primaryLabel = context.getString(R.string.kachi_save),
            validate = {
                val d = draft()
                val ok = ScheduledNavRules.of(CHECK_ID, d.enabled, d.startMin, d.endMin, d.days, d.requireGps,
                    d.placeId, d.navApp) != null
                error.visibility = if (ok) View.GONE else View.VISIBLE
                ok
            },
            dangerLabel = onDelete?.let { context.getString(R.string.kachi_delete) },
            onDanger = onDelete,
        ) { onOk(draft()) }
    }

    private fun presetLabel(p: DayPreset): Int = when (p) {
        DayPreset.WEEKDAYS -> R.string.kform_days_weekdays
        DayPreset.WEEKEND -> R.string.kform_days_weekend
        DayPreset.EVERYDAY -> R.string.kform_days_every
    }

    /**
     * Icon của app dẫn đường nếu máy có cài một gói của nó (tra [VoiceAppTargets.NAV] — nguồn gói duy nhất); không cài
     * ⇒ `null`, chip chỉ có chữ. Ba lần tra `PackageManager` lúc MỞ hộp, không gì chạy nền.
     */
    private fun appIcon(context: Context, key: String): Drawable? {
        val pkgs = VoiceAppTargets.NAV.firstOrNull { it.key == key }?.packages ?: return null
        for (pkg in pkgs) {
            try {
                return context.packageManager.getApplicationIcon(pkg)
            } catch (_: PackageManager.NameNotFoundException) {
                // gói này không cài — thử gói kế
            }
        }
        return null
    }

    private fun stack(context: Context, views: List<View>): View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        views.forEachIndexed { i, v -> addView(v, KachiDialog.stackLp(context, top = if (i == 0) 0 else Sp.XS)) }
    }
}
