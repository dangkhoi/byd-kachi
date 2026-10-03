package com.byd.clusternav.launcher

import android.content.Context
import android.widget.LinearLayout
import com.byd.clusternav.R
import com.byd.clusternav.launcher.trip.TripGate
import com.byd.clusternav.launcher.trip.TripStep
import com.byd.clusternav.launcher.trip.TripStepCode
import com.byd.clusternav.launcher.trip.TripStepKind
import java.text.SimpleDateFormat
import java.util.Date

/**
 * ═══ L4 · D1(b) — Cài đặt › Mở app khi nổ máy: KẾT QUẢ chuyến gần nhất, từng bước MỘT câu dễ hiểu ═══════════════════════
 *
 * Owner 03/10 trên xe 2.86: chạy nền / nhạc không làm gì mà Cài đặt vẫn nói *"đã chạy lúc HH:mm"*, lý do chỉ nằm ở màn Chẩn
 * đoán (không có nút ở bản phát hành). Nay Cài đặt dịch từng MÃ bước ([TripStepCode], sổ `kachi_trip_last` trường `s=`)
 * thành một câu ở 5 tiếng ⇒ anh em chụp màn hình gửi về là đủ (CLAUDE.md §11). Một mã một câu — [reasonRes] là `when` đủ
 * nhánh: thêm mã mới mà quên câu là KHÔNG biên dịch được.
 */
internal fun tripResultRows(list: LinearLayout, context: Context, rows: SettingsRows, r: TripGate.Result) {
    // Giờ 24h theo ngôn ngữ NGƯỜI DÙNG chọn (cùng mẫu đồng hồ thanh trên) — `DateFormat.getTimeInstance` không truyền
    // locale thì theo locale MÁY (xe đặt `ms`/`en_US` ra 12h + AM/PM), lệch với đồng hồ ngay trên màn (spec R7).
    // L4: kết quả KHÔNG phải của hôm nay (kênh chưa lên lần nổ máy này ⇒ chuyến chưa chạy, dòng này là của lần trước) ⇒ kèm
    // ngày, để ảnh chụp không đọc nhầm "đã chạy lúc 08:10" của hôm qua thành của hôm nay.
    val day = SimpleDateFormat("yyyyMMdd", LangHost.locale())
    val pattern = if (day.format(Date(r.atWall)) == day.format(Date())) "HH:mm" else "dd/MM HH:mm"
    val at = SimpleDateFormat(pattern, LangHost.locale()).format(Date(r.atWall))
    val text = when (r.code) {
        TripGate.Code.RAN -> context.getString(R.string.kachi_trip_res_ran, at)
        TripGate.Code.NOTHING -> context.getString(R.string.kachi_trip_res_nothing, at)
        TripGate.Code.EXPIRED -> context.getString(R.string.kachi_trip_expired)
        TripGate.Code.GAVE_UP -> context.getString(R.string.kachi_trip_res_gave_up, at)
        TripGate.Code.NOOP -> context.getString(R.string.kachi_trip_res_noop, at)
        TripGate.Code.PARTIAL -> context.getString(R.string.kachi_trip_res_partial, at)
    }
    list.addView(rows.note(context.getString(R.string.kachi_trip_last_ran, text)))
    r.steps.forEach { s -> list.addView(rows.note(context.getString(R.string.kachi_trip_step, stepLabel(context, s), context.getString(reasonRes(s.code))))) }
}

/** Tên hiện cho một bước: tên app thật (đã gỡ ⇒ tên gói); bước nhạc ⇒ *"Nhạc (YouTube)"*. */
private fun stepLabel(context: Context, s: TripStep): String {
    val app = InstalledApps.labelOf(context, s.pkg) ?: s.pkg
    return if (s.kind == TripStepKind.MUSIC) context.getString(R.string.kachi_trip_step_music, app) else app
}

internal fun reasonRes(code: TripStepCode): Int = when (code) {
    TripStepCode.MOVED -> R.string.kachi_trip_why_moved
    TripStepCode.HOME_RESTORED -> R.string.kachi_trip_why_home_restored
    TripStepCode.ALREADY_RUNNING -> R.string.kachi_trip_why_already_running
    TripStepCode.KEPT_UNDER -> R.string.kachi_trip_why_kept_under
    TripStepCode.IN_SLOT -> R.string.kachi_trip_why_in_slot
    TripStepCode.OPENED -> R.string.kachi_trip_why_opened
    TripStepCode.PLAYING -> R.string.kachi_trip_why_playing
    TripStepCode.SELF_PLAYING -> R.string.kachi_trip_why_self_playing
    TripStepCode.SENT -> R.string.kachi_trip_why_sent
    TripStepCode.TIMEOUT -> R.string.kachi_trip_why_timeout
    TripStepCode.NO_STAGE -> R.string.kachi_trip_why_no_stage
    TripStepCode.NO_CHANNEL -> R.string.kachi_trip_why_no_channel
    TripStepCode.DISABLED -> R.string.kachi_trip_why_disabled
    TripStepCode.SYSTEM_APP -> R.string.kachi_trip_why_system_app
    TripStepCode.NOT_INSTALLED -> R.string.kachi_trip_why_not_installed
    TripStepCode.SELF -> R.string.kachi_trip_why_self
    TripStepCode.CAMERA_UNKNOWN -> R.string.kachi_trip_why_camera_unknown
    TripStepCode.CAMERA -> R.string.kachi_trip_why_camera
    TripStepCode.OTHER_FRONT -> R.string.kachi_trip_why_other_front
    TripStepCode.NOT_STAGED -> R.string.kachi_trip_why_not_staged
    TripStepCode.NO_SESSION -> R.string.kachi_trip_why_no_session
    TripStepCode.UNKNOWN_MEDIA -> R.string.kachi_trip_why_unknown_media
    TripStepCode.DEADLINE -> R.string.kachi_trip_why_deadline
}
