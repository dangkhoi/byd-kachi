package com.byd.clusternav.launcher

import android.content.Context
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.byd.clusternav.R
import com.byd.clusternav.launcher.KachiTheme.c
import com.byd.clusternav.launcher.KachiTheme.dpi
import com.byd.clusternav.launcher.KachiSpace as Sp

/**
 * ═══ UX-OVERHAUL · WP4 — DANH SÁCH SẮP CHỖ, dùng cho CẢ HAI thanh ════════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-ux-overhaul.html` §WP4 · R4.1. Bày thứ tự hiện tại theo **một hàng một vật**, mỗi hàng có
 * số thứ tự + tên + hai nút ◀ ▶ dời chỗ.
 *
 * ## ⚠⚠ SAI LỆCH CÓ CHỦ Ý với lời giao: nút ◀ ▶, **không** kéo-thả
 * Lời giao ghi *"panel Tuỳ biến thanh cho **sắp/kéo** thứ tự-vị trí"*. Chọn nút bấm, và đây là lý do chứ không
 * phải sự tiện:
 *  1. **Kéo trong một vùng cuộn là cử chỉ mơ hồ.** Trang Cài đặt là một `ScrollView` dài (R4: ≤ 2 màn cuộn), nên
 *     một cú kéo dọc phải được phân xử giữa *"cuộn trang"* và *"dời vật"*. Cách duy nhất phân xử được là giữ-rồi-kéo
 *     (long-press) — tức một cử chỉ **không nhìn thấy được**, trong một chiếc xe, cho một việc mà người dùng làm
 *     một lần rồi quên.
 *  2. **Nút bấm là thứ ĐO được off-car.** Kết quả một cú bấm là một [HeaderLayout]/[DockConfig] mới, kiểm bằng test
 *     thuần ở `:core`; một cú kéo thì chỉ kiểm được bằng tay trên máy thật.
 *  3. **Đích chạm.** Hai nút [Sp.TOUCH] mỗi chiều rõ hơn một vùng kéo trên màn cảm ứng rung theo đường.
 * Phép DỜI vẫn nhận [Int] bậc bất kỳ ([BarOrder.move]), nên nối thêm kéo-thả về sau **không** phải viết lại luật —
 * chỉ là một chỗ gọi thứ hai vào cùng phép. Xin owner xác nhận hướng này.
 *
 * ## Vì sao MỘT lớp cho hai thanh
 * Thanh trên sắp [HeaderItem], thanh nút sắp mã khả năng (`String`). Hai loại dữ liệu, một bề mặt: cùng hình hàng,
 * cùng luật làm mờ nút ở hai đầu, cùng câu *"đang ở vị trí n/N"*. Hai bản sao sẽ lệch nhau ở đúng lần ai đó sửa một
 * bên — cùng lập luận đã đưa [BarOrder.move] về `:core` (xem KDoc ở đó).
 *
 * Lớp này **KHÔNG ghi bền và KHÔNG giữ nguồn sự thật**: nó đọc [current] mỗi lượt dựng và báo ra qua [onMove].
 * Cùng ranh giới [TopStripPicker].
 */
class SettingsBarOrderRows<T>(
    private val context: Context,
    private val rows: SettingsRows,
    /** Thứ tự ĐANG hiệu lực — đọc lại mỗi lượt dựng lại (trang Cài đặt được nhớ nên ảnh chụp có thể đã cũ). */
    private val current: () -> List<T>,
    /** Nhãn hiện cho một vật (đã dịch). */
    private val label: (T) -> String,
    /** Người dùng bấm dời: `delta` = −1 (lên/trái) hoặc +1 (xuống/phải). */
    private val onMove: (T, Int) -> Unit,
) {

    /** Khung của danh sách — giữ để dựng lại **chỉ nó** sau mỗi cú bấm, không dựng lại cả trang. */
    private val list = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

    /**
     * Gắn mục vào trang: tiêu đề + câu hướng dẫn + danh sách.
     *
     * @param titleRes tiêu đề mục · @param hintRes câu nói rõ mục này sắp cái gì.
     */
    fun section(parent: LinearLayout, titleRes: Int, hintRes: Int) {
        parent.addView(rows.sectionLabel(context.getString(titleRes)))
        parent.addView(rows.note(context.getString(hintRes)))
        parent.addView(list)
        rebuild()
    }

    /**
     * Dựng lại danh sách vì **danh sách vật đã đổi từ bên ngoài** (bộ chọn nút vừa Áp dụng thêm/bớt một mã).
     *
     * ⚠ Cần thiết vì `KachiHomeActivity.render` KHÔNG dựng lại trang Cài đặt khi `dock` đổi (nó chỉ gọi
     * `dock.setConfig`) — trang được **nhớ lại** ([SettingsPanel.pages]). Thiếu lời gọi này thì bỏ tích một nút ở
     * bộ chọn xong, danh sách sắp chỗ vẫn còn hàng của nút đã bỏ, và bấm ◀/▶ trên hàng đó là dời một mã không còn
     * trong thanh ([BarOrder.move] trả về nguyên vật cũ ⇒ *"bấm mà không có gì xảy ra"*).
     */
    fun refresh() = rebuild()

    /**
     * Dựng lại danh sách từ thứ tự hiện tại.
     *
     * Dựng lại **cả** danh sách thay vì đổi chỗ hai hàng: một cú dời đổi **số thứ tự của mọi hàng ở giữa** và đổi
     * cả trạng thái mờ/rõ của nút ở hai đầu. Tô tại chỗ thì ba thứ đó lệch nhau — đúng lỗi đã đo ở dòng *"chưa
     * kiểm"* của [TopStripPicker] hồi U7.
     */
    private fun rebuild() {
        list.removeAllViews()
        val items = current()
        items.forEachIndexed { i, item -> list.addView(row(item, i, items.size)) }
    }

    private fun row(item: T, index: Int, total: Int): View = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = KachiTheme.surface(context, Sp.RADIUS_M)
        minimumHeight = dpi(context, Sp.TOUCH)
        setPadding(dpi(context, Sp.M), dpi(context, Sp.S), dpi(context, Sp.S), dpi(context, Sp.S))
        layoutParams = LinearLayout.LayoutParams(MATCH, WRAP)
            .also { it.topMargin = dpi(context, Sp.XS) }
        // Số thứ tự: *"đang ở vị trí mấy"* là câu hỏi đầu tiên của mục này, và nó phải trả lời được khi người dùng
        // chỉ nhìn một hàng (danh sách có thể dài hơn màn).
        addView(TextView(context).apply {
            text = context.getString(R.string.kachi_bar_order_index, index + 1, total)
            setTextColor(c(KachiTheme.MUT)); KachiType.apply(this, KachiType.CAPTION)
            minWidth = dpi(context, Sp.ICON_XL)
        })
        addView(TextView(context).apply {
            text = label(item)
            setTextColor(c(KachiTheme.INK)); KachiType.apply(this, KachiType.BODY)
            maxLines = 1; ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(0, WRAP, 1f)
                .also { it.marginStart = dpi(context, Sp.S) }
        })
        addView(moveButton(item, -1, R.string.kachi_bar_order_up, index > 0))
        addView(moveButton(item, +1, R.string.kachi_bar_order_down, index < total - 1))
    }

    /**
     * Nút dời một bậc. [enabled] = còn dời được — **làm mờ + bỏ nhận chạm**, KHÔNG ẩn.
     *
     * Ẩn thì hai hàng đầu/cuối có ít nút hơn các hàng khác ⇒ cột nút không thẳng và mắt phải đọc lại từng hàng.
     * Bỏ qua im lặng thì người dùng bấm mà không có gì xảy ra — đúng họ lỗi mà nút bố cục sẵn ở P9 đã trả giá.
     */
    private fun moveButton(item: T, delta: Int, descRes: Int, enabled: Boolean): View = TextView(context).apply {
        text = if (delta < 0) GLYPH_UP else GLYPH_DOWN
        contentDescription = context.getString(descRes)
        setTextColor(c(KachiTheme.INK)); KachiType.apply(this, KachiType.SECTION, bold = true)
        gravity = Gravity.CENTER
        minWidth = dpi(context, Sp.TOUCH); minimumHeight = dpi(context, Sp.TOUCH)
        background = KachiTheme.pill(context)
        layoutParams = LinearLayout.LayoutParams(WRAP, WRAP).also { it.marginStart = dpi(context, Sp.XS) }
        isEnabled = enabled
        alpha = if (enabled) 1f else DISABLED_ALPHA
        if (enabled) setOnClickListener {
            onMove(item, delta)
            rebuild()      // thứ tự vừa đổi ⇒ số thứ tự + trạng thái mờ của MỌI hàng đổi theo (xem [rebuild])
        }
    }

    private companion object {
        const val MATCH = LinearLayout.LayoutParams.MATCH_PARENT
        const val WRAP = LinearLayout.LayoutParams.WRAP_CONTENT

        /**
         * Mũi tên là **KÝ HIỆU**, không phải chữ ⇒ không đi qua `getString` (`LauncherI18nContractTest` chỉ bắt
         * literal có CHỮ CÁI — cùng ranh giới với dấu ▸/▾ của [TopStripPicker]). Tên đọc được cho trình đọc màn
         * hình thì vẫn từ `R.string`.
         *
         * Dùng **lên/xuống** chứ không trái/phải: danh sách xếp DỌC, nên mũi tên phải chỉ theo chiều người dùng
         * thấy vật di chuyển trên trang này — dù kết quả trên thanh ngang là trái/phải.
         */
        const val GLYPH_UP = "▲"
        const val GLYPH_DOWN = "▼"

        /** Nút hết dời được: mờ đủ để đọc ra là *"không dùng được"* mà vẫn thấy hình. */
        const val DISABLED_ALPHA = 0.35f
    }
}
