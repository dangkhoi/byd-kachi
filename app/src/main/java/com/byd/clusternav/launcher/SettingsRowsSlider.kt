package com.byd.clusternav.launcher

import android.content.res.ColorStateList
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import com.byd.clusternav.launcher.KachiTheme.c
import com.byd.clusternav.launcher.KachiTheme.dpi
import com.byd.clusternav.launcher.KachiSpace as Sp

/**
 * ═══ 2.88 · R-OP — HÀNG THANH KÉO của màn Cài đặt ═══════════════════════════════════════════════════════════════
 *
 * Phần mở rộng của [SettingsRows] (tách tệp vì trần 500 dòng, cùng lối [swatchRow]): **một nơi dựng component** vẫn là
 * `SettingsRows` — hàm này dùng lại `stackLp`/`rowLabel` của nó, không dựng lề riêng. Sinh ra cho "Độ trong suốt nền"
 * (owner 04/10: *"có thay kéo từ 0-100%"*); generic để hàng kéo sau này không dựng bản thứ hai.
 *
 * Bố cục `nhãn trái · thanh kéo (giãn) · giá trị đậm phải` — cùng nhịp với [stepperRow] (giá trị là thứ người dùng
 * nhìn khi chỉnh). Thanh kéo cao [KachiSpace.TOUCH]: `SeekBar` nhận chạm trên trọn view, nên đích chạm là cả dải 48 dp,
 * không chỉ núm. Số vị trí do chỗ gọi đặt ([positions] — độ trong suốt dùng 20 nấc 5 %): nấc thưa để ngón tay đặt trúng
 * trên xe đang chạy.
 *
 * ## Khi nào gọi [onCommit] — lúc THẢ tay, không theo từng nấc kéo
 * Trong lúc kéo chỉ chữ giá trị (+ mô tả trợ năng) đổi. Áp thật ([onCommit]) chạy khi thả, vì đường áp của độ đục là
 * `deps.onColorChoice` ⇒ ghi prefs theo hồ sơ + `applyThemeInPlace` dựng lại thanh nút (`ControlDockView.restyle` →
 * `rebuild`) — áp theo nhịp kéo là dựng lại view + ghi đĩa nhiều lần mỗi giây. [ĐO AOSP r47 `AbsSeekBar.java`]:
 *  • `onTouchEvent` 870-922: chạm-không-kéo trong vùng cuộn (Cài đặt là `ScrollView`) vẫn gọi đủ
 *    `onStartTrackingTouch` → `trackTouchEvent` → `onStopTrackingTouch` (895-906); kéo bị huỷ (`ACTION_CANCEL`) khi đang
 *    kéo cũng gọi `onStopTrackingTouch` (913-919) ⇒ mọi lượt chạm đổi số đều tới [onCommit].
 *  • phím (`onKeyDown` 1008-1033) và hành động trợ năng (`performAccessibilityActionInternal` 1059-1100) đổi số bằng
 *    `setProgressInternal(…, fromUser = true, …)` KHÔNG qua start/stop ⇒ nhánh `fromUser && !tracking` áp ngay.
 * [onCommit] chỉ chạy khi vị trí KHÁC lần áp trước (chạm lại đúng chỗ cũ không ghi gì). Luật nằm ở lớp thuần
 * [CommitOnRelease] (`:core`, `CommitOnReleaseTest`) — hàm này chỉ chuyển ba sự kiện của `SeekBar` vào nó.
 *
 * ## Trợ năng
 * `contentDescription` của thanh = [describe] của chữ giá trị đang hiện (chỗ gọi đưa câu đã dịch, vd "Độ trong suốt
 * nền: 45%"), đổi theo từng nấc — trình đọc màn hình đọc đúng phần trăm đang hiện, không chỉ tỉ lệ vị trí/tổng
 * (`setStateDescription` chỉ có từ API 30; xe API 29).
 */
internal fun SettingsRows.sliderRow(
    label: String,
    positions: Int,
    current: Int,
    valueText: (Int) -> String,
    describe: (String) -> String,
    /**
     * 2.89 · B3 — mỗi nấc NGƯỜI DÙNG đổi (kéo · phím · trợ năng), TRƯỚC luật áp-khi-thả: chỉ để vẽ lại thứ RẺ ngay trong
     * Cài đặt (dải ô mẫu của cỡ thanh nút). Không được ghi gì — ghi đi [onCommit]. Mặc định không làm gì ⇒ hàng cũ y nguyên.
     */
    onPreview: (Int) -> Unit = {},
    onCommit: (Int) -> Unit,
): View {
    val value = TextView(context).apply {
        setTextColor(c(KachiTheme.INK)); KachiType.apply(this, KachiType.BODY, bold = true)
        gravity = Gravity.CENTER_VERTICAL or Gravity.END
        // Bề rộng tối thiểu cố định: "5%" → "100%" không làm thanh kéo co giãn dưới ngón tay khi số đổi độ dài.
        minWidth = dpi(context, Sp.CHIP_MIN_W)
    }
    val bar = SeekBar(context).apply {
        max = positions
        progress = current.coerceIn(0, positions)
        keyProgressIncrement = 1
        // Màu theo bảng của Kachi (không phải màu nhấn của theme hệ thống): đoạn đã kéo + núm = ACCENT, rãnh = MUT (2.88 —
        // lịch sử dưới; 2.93 thay bằng drawable riêng, rãnh MUT2 ≥ 3:1).
        // Soát Pass 10 [P2]: rãnh KHÔNG dùng FIELD_SUNKEN — hàng nằm thẳng trên thẻ PANEL, mà FIELD_SUNKEN/PANEL chỉ ≈ 1.11:1
        // (tối) / 1.34:1 (sáng) ngay cả khi đục; rãnh Material còn vẽ ở `?attr/disabledAlpha` (0.30, `seekbar_track_
        // material.xml` [ĐO tài nguyên android-34; r47 [SUY] giống]) ⇒ ≈ 1.03–1.09:1, ở vị trí 0 chỉ còn một chấm núm.
        // MUT (vai Material dùng cho rãnh là `colorControlNormal` — mực phụ) ở 30 % ≈ 1.80:1 tối · 1.60:1 sáng [SUY tính
        // tay từ bảng] — trên ngưỡng nhìn-ra-được ~1.15× của `SettingsRows.checkRow`.
        // 2.93 SLIDER-RAIL-CONTRAST — [ĐO máy ảo QA 04/10, ảnh `t3-slider-row-*`] cách tô rãnh trên (MUT ở disabledAlpha của
        // theme ROM) chỉ ra 1,6:1 (sáng) / 1,81:1 (tối), dưới 3:1 của điều khiển. Nay rãnh + đoạn đã kéo là drawable RIÊNG
        // ([sliderRail]) — không còn phụ thuộc alpha của theme ROM; độ đục rãnh = mức nhạt nhất vẫn ≥ 3:1 trên PANEL.
        progressDrawable = sliderRail(context)
        val rail = dpi(context, Sp.XS)
        minHeight = rail; maxHeight = rail
        thumbTintList = ColorStateList.valueOf(c(KachiTheme.ACCENT))
    }
    fun show(pos: Int) {
        val text = valueText(pos)
        value.text = text
        bar.contentDescription = describe(text)
    }
    show(bar.progress)
    // Luật "áp khi thả tay" là lớp thuần `:core` [CommitOnRelease] (có test hành vi); ở đây chỉ chuyển sự kiện vào.
    val gate = CommitOnRelease(bar.progress)
    bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
            show(progress)
            if (fromUser) onPreview(progress)
            gate.onChange(progress, fromUser)?.let(onCommit)   // phím / trợ năng: không có lượt chạm để chờ thả
        }

        override fun onStartTrackingTouch(seekBar: SeekBar) {
            gate.onStart()
        }

        override fun onStopTrackingTouch(seekBar: SeekBar) {
            gate.onStop(seekBar.progress)?.let(onCommit)
        }
    })
    return LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        layoutParams = stackLp()
        addView(rowLabel(label))
        addView(bar, LinearLayout.LayoutParams(0, dpi(context, Sp.TOUCH), 1f))
        addView(value, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }
}

/**
 * 2.93 `SLIDER-RAIL-CONTRAST` — `progressDrawable` riêng của thanh kéo Cài đặt: RÃNH (lớp `background`) = MUT2 ở độ đục nhạt
 * NHẤT mà vẫn ≥ [RAIL_FLOOR] trên thẻ PANEL ([IconFade.offAlpha] — cùng phép dò "mờ nhất còn đạt sàn", `:core`), ĐOẠN ĐÃ KÉO
 * (lớp `progress`, `ClipDrawable` theo mức) = ACCENT đục. Cả hai bo tròn, cao bằng `min/maxHeight` của thanh (4 dp). Không
 * còn đi qua `progressBackgroundTintList` (theme ROM vẽ nó ở `disabledAlpha` 0,30 ⇒ 1,6:1 sáng / 1,81:1 tối [ĐO QA 04/10]).
 */
internal fun sliderRail(context: android.content.Context): android.graphics.drawable.Drawable {
    val r = dpi(context, Sp.RADIUS_PILL).toFloat()
    val alpha = IconFade.offAlpha(c(KachiTheme.MUT2), intArrayOf(c(KachiTheme.PANEL)), 0.0, floor = RAIL_FLOOR)
    val rail = android.graphics.drawable.GradientDrawable().apply {
        cornerRadius = r; setColor(ColorMath.scaleAlpha(ColorMath.withAlpha(c(KachiTheme.MUT2), 255), alpha))
    }
    val done = android.graphics.drawable.GradientDrawable().apply { cornerRadius = r; setColor(c(KachiTheme.ACCENT)) }
    val clip = android.graphics.drawable.ClipDrawable(done, Gravity.START, android.graphics.drawable.ClipDrawable.HORIZONTAL)
    return android.graphics.drawable.LayerDrawable(arrayOf(rail, clip)).apply {
        setId(0, android.R.id.background); setId(1, android.R.id.progress)
    }
}

/** Sàn tương phản của rãnh thanh kéo — WCAG 1.4.11 (thành phần điều khiển) 3:1. */
internal const val RAIL_FLOOR = 3.0
