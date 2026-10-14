package com.byd.clusternav.launcher

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import com.byd.clusternav.launcher.KachiTheme.c
import com.byd.clusternav.launcher.KachiTheme.dpi

/**
 * Phần PHỤ của khung [KachiDialog] — tách tệp vì trần 500 dòng (CLAUDE.md §4.1), không phải một khung thứ hai: chỉ
 * [KachiDialog] gọi các hàm cửa sổ/ô chữ ở đây; [dialogControlFill] là luật màu THUẦN (test off-device được).
 */
internal object KachiDialogSupport {

    /** Gắn [onChange] vào MỌI [EditText] trong cây [roots] — watcher sống trong ô của hộp, chết cùng hộp. */
    fun watchEdits(roots: List<View>, onChange: () -> Unit) {
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) = onChange()
        }
        forEachEdit(roots) { it.addTextChangedListener(watcher) }
    }

    private fun forEachEdit(roots: List<View>, run: (EditText) -> Unit) {
        fun walk(v: View) {
            if (v is EditText) run(v)
            if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i))
        }
        roots.forEach(::walk)
    }

    fun anyEdit(roots: List<View>): Boolean {
        var found = false
        forEachEdit(roots) { found = true }
        return found
    }

    /**
     * QA-301 D2 [P1] — MỌI ô chữ trong hộp lấy mực/gợi ý/con trỏ theo bảng Kachi ĐANG DÙNG. Ô dựng bằng context Activity
     * (theme `DeviceDefault` tối) mà không tô ⇒ ở bảng Sáng chữ trắng trên thẻ trắng (ô tìm trang Dạy tên). Tô ở KHUNG
     * nên chỗ gọi quên tô cũng không vô hình được nữa; [KachiDialogs.field] là đường dựng ô chuẩn (nền lõm + 48 dp).
     */
    fun themeEdits(roots: List<View>) = forEachEdit(roots) { e ->
        e.setTextColor(c(KachiTheme.INK))
        e.setHintTextColor(c(KachiTheme.MUT))
        e.highlightColor = c(KachiTheme.ACCENT_SOFT)
        e.textCursorDrawable = GradientDrawable().apply {
            setColor(c(KachiTheme.ACCENT))
            setSize(dpi(e.context, KachiSpace.STROKE), 0)   // con trỏ = nét nhấn 2 dp
        }
    }

    /**
     * QA-301 (P3) — MỌI cửa sổ hộp ẩn thanh hệ thống khi Activity chủ là màn toàn màn (cờ cửa sổ `FLAG_FULLSCREEN` — màn
     * chính Kachi đặt ở `onCreate`, cùng `goImmersiveWindow`). Cửa sổ đang giữ tiêu điểm quyết định thanh trạng thái ⇒ ảnh
     * QA: trang Dạy tên lộ thanh 0–36 px; [ĐO Re-QA] hộp Xác nhận mở trên trang cũng lộ (đồng hồ + pin).
     *
     * Đặt bộ cờ ẨN cố định, KHÔNG chép `systemUiVisibility` của Activity: [ĐO máy ảo 13/10] lúc hộp thứ hai mở, cờ của
     * Activity đã bị hệ thống hạ còn `0x1000` (chỉ STICKY) ⇒ chép là lộ thanh. Không đặt ba cờ `LAYOUT_*` (và không đặt
     * cờ cửa sổ `FLAG_FULLSCREEN`): [ĐO] có `LAYOUT_*` thì cửa sổ KHÔNG co khi bàn phím lên (`adjustResize` mất tác dụng).
     */
    @Suppress("DEPRECATION")
    fun followHostBars(win: Window, host: Window) {
        if (host.attributes.flags and WindowManager.LayoutParams.FLAG_FULLSCREEN == 0) return
        win.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
    }

    /**
     * QA-301 (P3) — tiêu đề hộp là TÊN việc, không phải nhãn nút mở nó: bỏ dấu "…" cuối (*"Thêm địa chỉ…"* ⇒
     * *"Thêm địa chỉ"*). Dấu "…" trên nhãn nút nghĩa là "còn một bước nữa" — trong hộp thì bước đó đang hiện.
     */
    fun cleanTitle(text: CharSequence): CharSequence {
        val s = text.toString().trimEnd()
        val cut = s.removeSuffix(ELLIPSIS).removeSuffix(DOTS).trimEnd()
        return if (cut.length == text.length) text else cut
    }

    /** Nền điều khiển phụ trên thẻ hộp theo bảng ĐANG DÙNG — luật ở [dialogControlFill]. */
    fun controlFill(): String = dialogControlFill(KachiTheme.palette)

    private const val ELLIPSIS = "…"
    private const val DOTS = "..."

    /**
     * Cột có TRẦN chiều cao = [KachiDialog.MAX_H_FRACTION] màn. Kẹp đo ở `AT_MOST` để `LinearLayout` co phần thân (trọng số 1)
     * khi nội dung tràn — đầu + chân luôn nằm trong màn, thân cuộn. Bàn phím mở (`adjustResize`) ⇒ khung còn nhỏ hơn,
     * phép kẹp này lấy cái nhỏ hơn.
     */
    class MaxHeightColumn(ctx: Context) : LinearLayout(ctx) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val cap = (resources.displayMetrics.heightPixels * KachiDialog.MAX_H_FRACTION).toInt()
            val size = View.MeasureSpec.getSize(heightMeasureSpec)
            val capped = if (View.MeasureSpec.getMode(heightMeasureSpec) == View.MeasureSpec.UNSPECIFIED) cap
            else minOf(size, cap)
            super.onMeasure(widthMeasureSpec, View.MeasureSpec.makeMeasureSpec(capped, View.MeasureSpec.AT_MOST))
        }
    }
}

/**
 * QA-301 (P3) — nền của ĐIỀU KHIỂN PHỤ nằm trên thẻ hộp (nút Huỷ · thẻ chọn chưa chọn · ô giờ chưa chọn · ▲▼ · chip tắt).
 *
 * [ĐO] bảng Sáng: thẻ hộp = `surfFrom` `#ffffff`, mà `cardFill` (nền thẻ mặc định — đúng cho thẻ đặt trên NỀN MÀN) cũng
 * `#ffffff` ⇒ điều khiển trùng màu thẻ, mất hình (ảnh QA `schedule-add-light.png`). Bảng Tối ngược lại: `chipOff` = bậc 2
 * = đúng `surfFrom` (1.00:1) còn `cardFill` trắng 8 % thì tách 1.26×. Nên KHÔNG chọn theo Sáng/Tối mà ĐO: vai đầu tiên
 * trong ba ứng viên `cardFill` → `chipOff` → `fieldSunken` tách khỏi CẢ HAI đầu chuyển sắc của thẻ hộp ≥ [CONTROL_MIN_STEP] (bảng/tông đổi thì luật tự
 * chọn lại). Không vai nào đạt ⇒ vai lõm `fieldSunken` (luôn tối hơn mặt chứa nó một bậc).
 */
internal fun dialogControlFill(p: KachiPalette): String {
    val top = ColorMath.parse(p.surfFrom)
    val bottom = ColorMath.parse(p.surfTo)
    return listOf(p.cardFill, p.chipOff, p.fieldSunken).firstOrNull { role ->
        val fill = ColorMath.parse(role)
        listOf(top, bottom).all { card -> ColorMath.ratio(ColorMath.over(fill, card), card) >= CONTROL_MIN_STEP }
    } ?: p.fieldSunken
}

/** Bước sáng tối thiểu điều khiển ↔ thẻ hộp để "thấy hình nút" — [ĐO] bảng Sáng `chipOff` 1.16–1.23×, Tối `cardFill` 1.23–1.26×. */
internal const val CONTROL_MIN_STEP = 1.12

/**
 * 3.1.x LIGHT-CONTRAST — mặt ô chọn TẮT trong hộp ([KachiFormControls.toggle]): ô rỗng chỉ có MẶT để nói "đây là ô chọn". [TÍNH]
 * `fieldSunken` cũ chỉ 1,26–1,34:1 trên thẻ hộp Sáng (Tối 1,03–1,22) ⇒ ĐO như [dialogControlFill]: vai đầu tiên trong
 * `fieldSunken` → `lineStrong` tách CẢ HAI đầu chuyển sắc ≥ [BOX_OFF_MIN] (WCAG 1.4.11); không vai nào ⇒ `lineStrong`.
 */
internal fun dialogBoxOff(p: KachiPalette): String {
    val faces = listOf(ColorMath.parse(p.surfFrom), ColorMath.parse(p.surfTo))
    return listOf(p.fieldSunken, p.lineStrong).firstOrNull { role ->
        faces.all { f -> ColorMath.ratio(ColorMath.over(ColorMath.parse(role), f), f) >= BOX_OFF_MIN }
    } ?: p.lineStrong
}

internal const val BOX_OFF_MIN = 3.0
