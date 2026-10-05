package com.byd.clusternav.launcher

import android.annotation.SuppressLint
import android.content.Context
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.byd.clusternav.R

/**
 * ═══ 2.89 · B3 DOCK-SCALE — Cài đặt › Hiển thị › "Thanh nút xe · Cỡ" (`kachi-289-field-fixes` §B3) ═════════════════════
 *
 * Owner 05/10: *"cái taskbar hơi to, cho chỉnh size luôn trong hiển thị nhé, theo %"* rồi *"50-150% đi"*. Tệp riêng vì
 * `SettingsSections.kt` sát trần 500 dòng — `display()` chỉ thêm MỘT dòng gọi [build].
 *
 * ## Kéo ⇒ chỉ dải mẫu đổi · thả ⇒ áp đúng một lần
 * Đổi bề dày thanh = lượt bố trí lại vùng ô ⇒ `VdAppHost.resize` ⇒ app trong ô nhận MỘT lượt đổi cấu hình (KDoc
 * `VdAppHost.resize`: *"KHÔNG rẻ"*). Áp theo từng nấc kéo là tới 20 lượt/lần kéo. Nên:
 *  • kéo: [PreviewStrip] dựng lại dải ô mẫu (gộp tối đa một lượt mỗi khung hình, `postOnAnimation`); màn chính KHÔNG đổi
 *    — bảng Cài đặt là anh em của `content` trong `rootFrame` nên `requestLayout` của dải mẫu không đo lại vùng ô
 *    [ĐO AOSP r47 `View.java` measure bỏ qua khi không cờ + spec không đổi — trích ở thiết kế §3.6];
 *  • thả (hoặc phím / trợ năng): `deps.onDockConfig(dock.withScale(%))` — intent sẵn có của thanh nút, không cửa ghi thứ
 *    hai ⇒ lưu theo hồ sơ + `render` ⇒ `ControlDockView.setConfig` + `DockAreaLayout.apply` một lượt (luật [CommitOnRelease]).
 *
 * ## Dải mẫu = CHÍNH `ControlDockView` (K4 — một đường dựng, không bản sao phép cỡ ô)
 * Ba ô đầu của thanh hồ sơ đang dùng (bỏ khối lối tắt), cùng viền ⇒ đúng TỪNG PIXEL ô thật sẽ có ở cỡ đó. Trơ tuyệt đối:
 * cổng xe mặc định [NoCar], bảng trạng thái ô RIÊNG (`ControlTileState(ControlLastSent())` — không đụng `ControlTileState.shared`
 * lẫn `ControlLastSent.shared` (phím Đảo đọc) của thanh
 * thật), khung [InertFrame] nuốt mọi cú chạm trước khi tới ô và giấu cây khỏi trợ năng.
 */
internal class SettingsBarScaleSection(
    private val context: Context,
    private val rows: SettingsRows,
    private val deps: SettingsDeps,
) {

    fun build(body: LinearLayout) {
        body.addView(rows.sectionLabel(context.getString(R.string.kachi_sec_bar_scale)))
        val saved = deps.state()
        val preview = PreviewStrip(context, saved.dock, saved.carStatus, saved.unitPrefs)
        body.addView(rows.sliderRow(
            label = context.getString(R.string.kachi_row_bar_scale),
            positions = BarScale.POSITIONS,
            current = BarScale.position(saved.dock.scalePct),
            valueText = { pos -> "${BarScale.ofPosition(pos)}%" },
            describe = { value -> context.getString(R.string.kachi_bar_scale_a11y, value) },
            onPreview = { pos -> preview.show(BarScale.ofPosition(pos)) },
        ) { pos -> deps.onDockConfig(deps.state().dock.withScale(BarScale.ofPosition(pos))) })
        body.addView(preview.view.apply { layoutParams = rows.stackLp() })   // lề STACK như mọi component của trang
        body.addView(rows.note(context.getString(R.string.kachi_bar_scale_note)))
    }

    /** Dải ô mẫu đúng cỡ (xem KDoc lớp). Khung cao CỐ ĐỊNH = cỡ ở [BarScale.MAX] ⇒ trang không nhảy khi kéo. */
    private class PreviewStrip(ctx: Context, saved: DockConfig, car: CarStatus, units: UnitPrefs) {
        private val cfg = saved.copy(enabled = sample(saved.enabled), visible = true)
        // `control` mặc định = NoCar. Review 2.89 Pass 3 · vietmap-dock-r2-4: bảng lệnh-cuối RIÊNG — `ControlTileState()` mặc định ghi
        // vào `ControlLastSent.shared` (bảng phím Đảo đọc) mỗi lượt `rebuild` ⇒ kéo thanh là ghi đè giá trị CŨ của ảnh chụp lúc mở trang.
        private val dock = ControlDockView(ctx, ControlTileState(ControlLastSent()))
        private val frame = InertFrame(ctx)
        private var pending = saved.scalePct
        private val apply = Runnable { place(pending) }

        val view: View get() = frame

        init {
            dock.setCarStatus(car, units)   // ô đọc hiện số thật một lần (chỉ đọc); không theo nhịp
            frame.addView(dock)
            frame.minimumHeight = extentAt(BarScale.MAX)
            place(saved.scalePct)
        }

        /** Mỗi nấc kéo — gộp: chỉ nấc cuối của khung hình được dựng. */
        fun show(pct: Int) {
            pending = pct
            frame.removeCallbacks(apply)
            frame.postOnAnimation(apply)
        }

        private fun place(pct: Int) {
            dock.setConfig(cfg.withScale(pct))
            val t = dock.thicknessPx()
            val wrap = ViewGroup.LayoutParams.WRAP_CONTENT
            dock.layoutParams = if (cfg.isVertical()) FrameLayout.LayoutParams(t, wrap, Gravity.CENTER)
            else FrameLayout.LayoutParams(wrap, t, Gravity.CENTER)
        }

        /** Chiều cao dải ở [pct]: thanh ngang = bề dày; thanh dọc = đo thật (bề rộng = bề dày, cao tự do). */
        private fun extentAt(pct: Int): Int {
            place(pct)
            if (!cfg.isVertical()) return dock.thicknessPx()
            dock.measure(
                View.MeasureSpec.makeMeasureSpec(dock.thicknessPx(), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            )
            return dock.measuredHeight
        }

        private companion object {
            /** Số ô mẫu — đủ thấy cỡ ô + khoảng cách, không kéo dài trang. */
            const val SAMPLE = 3

            /** Ba mã đầu của thanh (bỏ khối lối tắt: nó nghe danh sách app, không cần cho mẫu); rỗng ⇒ mặc định. */
            fun sample(enabled: List<String>): List<String> =
                enabled.filter { it != LauncherActions.SHORTCUTS }.take(SAMPLE)
                    .ifEmpty { ControlRegistry.defaultEnabledIds().take(SAMPLE) }
        }
    }

    /** Khung TRƠ: nuốt mọi cú chạm trước khi tới ô (không bắn gì, để `ScrollView` của trang cuộn), giấu khỏi trợ năng. */
    private class InertFrame(ctx: Context) : FrameLayout(ctx) {
        init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS }

        override fun onInterceptTouchEvent(ev: MotionEvent): Boolean = true

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(event: MotionEvent): Boolean = false
    }
}
