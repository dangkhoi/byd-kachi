package com.byd.clusternav.launcher

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.byd.clusternav.R
import com.byd.clusternav.launcher.KachiTheme.c
import com.byd.clusternav.launcher.KachiTheme.dpi
import com.byd.clusternav.launcher.automation.TimeWheelMath
import com.byd.clusternav.launcher.KachiSpace as Sp

/**
 * ═══ KACHI-301 R12 — BỘ CHỌN KHUNG GIỜ nằm TRONG hộp *Lịch dẫn đường* ═══════════════════════════════════════════
 *
 * Hai ô giờ lớn **Bắt đầu / Kết thúc** (segmented — chạm để chọn ô đang sửa) · dưới đó cột GIỜ và cột PHÚT, mỗi cột
 * ▲ số ▼ (nút [Sp.TOUCH]², nhấn GIỮ thì lặp qua [SettingsRepeatTouch.attach]) · chip mốc nhanh 06:00…18:00.
 *
 * ## Vì sao tự dựng, không `TimePickerDialog` / `TimePicker` / `NumberPicker`
 *  - `TimePickerDialog` là hộp CON ⇒ hộp chồng hộp — [ĐO] 2026-09-12 trên xe: tiêu điểm không chuyển sang hộp con
 *    khi có app đang chiếu trong ô. Bài học đó giữ nguyên: mọi thứ nằm trong MỘT hộp.
 *  - `TimePicker`/`NumberPicker` nhúng: vẽ theo theme hệ thống BYD, không tô được theo bảng Kachi (spec §4.5), và
 *    `NumberPicker` mở bàn phím khi chạm vào số. R12: không ô gõ giờ ⇒ không bàn phím.
 *
 * Mọi phép đổi giá trị (quay vòng, bước 5, bắt lưới) ở [TimeWheelMath] (`:core`, kiểm off-car). Lớp này chỉ vẽ.
 * Hiệu năng: 0 luồng/nhịp nền; `postDelayed` chỉ sống trong lúc ngón tay đang giữ nút (xem [SettingsRepeatTouch]).
 *
 * @param onChange gọi sau MỌI lần đổi giá trị (chỗ gọi cập nhật dòng tóm tắt + kiểm hợp lệ).
 */
internal class TimeWheel(
    private val context: Context,
    initialStart: Int,
    initialEnd: Int,
    private val onChange: () -> Unit,
) {
    var start: Int = TimeWheelMath.clamp(initialStart)
        private set
    var end: Int = TimeWheelMath.clamp(initialEnd)
        private set

    /** Ô đang sửa: `true` = Bắt đầu. */
    private var editingStart = true

    private val startTile = tile(R.string.kform_sched_start) { select(true) }
    private val endTile = tile(R.string.kform_sched_end) { select(false) }
    private val hourText = number()
    private val minuteText = number()
    private val presets = TimeWheelMath.PRESETS.map { m ->
        KachiFormControls.chip(context, SettingsNavAutomationFormat.hhmm(m)) { set(m) }
    }

    val view: View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(startTile.first, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(endTile.first, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dpi(context, Sp.S)
            })
        })
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            addView(column(hourText, R.string.kform_sched_hour_up, R.string.kform_sched_hour_down) { dir ->
                set(TimeWheelMath.stepHour(current(), dir))
            })
            addView(TextView(context).apply {
                text = SEP
                setTextColor(c(KachiTheme.INK))
                KachiType.apply(this, KachiType.DISPLAY, bold = true)
                setPadding(dpi(context, Sp.M), 0, dpi(context, Sp.M), 0)
            })
            addView(column(minuteText, R.string.kform_sched_minute_up, R.string.kform_sched_minute_down) { dir ->
                set(TimeWheelMath.stepMinute(current(), dir))
            })
        }, KachiDialog.stackLp(context, top = Sp.M))
        addView(KachiFormControls.flow(context, presets), KachiDialog.stackLp(context, top = Sp.S))
        paint()
    }

    private fun current() = if (editingStart) start else end

    private fun set(minOfDay: Int) {
        if (editingStart) start = minOfDay else end = minOfDay
        paint()
        onChange()
    }

    private fun select(isStart: Boolean) {
        editingStart = isStart
        paint()
    }

    private fun paint() {
        paintTile(startTile, start, editingStart)
        paintTile(endTile, end, !editingStart)
        val v = current()
        hourText.text = two(TimeWheelMath.hourOf(v))
        minuteText.text = two(TimeWheelMath.minuteOf(v))
        presets.forEachIndexed { i, chip -> chip.on = TimeWheelMath.PRESETS[i] == v }
    }

    private fun paintTile(t: Pair<LinearLayout, TextView>, value: Int, on: Boolean) {
        val (box, num) = t
        num.text = SettingsNavAutomationFormat.hhmm(value)
        // QA-301 (P3): ô chưa chọn tô [KachiDialogSupport.controlFill] — `card()` mặc định trùng màu thẻ hộp ở bảng Sáng.
        box.background = if (on) KachiTheme.surface(context, Sp.RADIUS_L, SurfaceTone.ACTIVE)
        else KachiTheme.card(context, Sp.RADIUS_L, KachiDialogSupport.controlFill())
        box.isSelected = on
    }

    /** Ô giờ lớn: nhãn (chú thích) + giờ ([KachiType.TITLE] đậm). Trả (ô, chữ số) để [paintTile] tô lại. */
    private fun tile(labelRes: Int, onTap: () -> Unit): Pair<LinearLayout, TextView> {
        val num = TextView(context).apply {
            setTextColor(c(KachiTheme.INK))
            KachiType.apply(this, KachiType.TITLE, bold = true)
        }
        val box = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            minimumHeight = dpi(context, Sp.TOUCH)
            setPadding(dpi(context, Sp.M), dpi(context, Sp.S), dpi(context, Sp.M), dpi(context, Sp.S))
            isClickable = true
            contentDescription = context.getString(labelRes)
            setOnClickListener { onTap() }
            addView(KachiDialogs.caption(context, context.getString(labelRes)))
            addView(num)
        }
        return box to num
    }

    private fun number() = TextView(context).apply {
        gravity = Gravity.CENTER
        setTextColor(c(KachiTheme.INK))
        KachiType.apply(this, KachiType.DISPLAY, bold = true)
    }

    /** Một cột ▲ số ▼. Chạm đơn = một nấc; giữ = lặp ([SettingsRepeatTouch]). */
    private fun column(value: TextView, upDesc: Int, downDesc: Int, step: (Int) -> Unit): View =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            addView(arrow(GLYPH_UP, upDesc) { step(+1) })
            addView(value)
            addView(arrow(GLYPH_DOWN, downDesc) { step(-1) })
        }

    private fun arrow(glyph: String, descRes: Int, step: () -> Unit): View = TextView(context).apply {
        text = glyph
        gravity = Gravity.CENTER
        setTextColor(c(KachiTheme.INK))
        KachiType.apply(this, KachiType.SECTION, bold = true)
        background = KachiTheme.pill(context, KachiDialogSupport.controlFill())   // QA-301: thấy hình nút ở bảng Sáng
        contentDescription = context.getString(descRes)
        layoutParams = LinearLayout.LayoutParams(dpi(context, Sp.TOUCH), dpi(context, Sp.TOUCH))
        setOnClickListener { step() }
        SettingsRepeatTouch.attach(this, step)
    }

    private companion object {
        /** Ký hiệu nút cột + dấu ngăn giờ:phút — ký hiệu, không phải chữ cần dịch. */
        const val GLYPH_UP = "▲"
        const val GLYPH_DOWN = "▼"
        const val SEP = ":"

        /** Hai chữ số (giờ/phút) — cùng dạng `%02d` của [SettingsNavAutomationFormat.hhmm]. */
        fun two(n: Int) = n.toString().padStart(2, '0')
    }
}
