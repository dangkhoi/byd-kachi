package com.byd.clusternav.launcher

import android.content.Context
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.byd.clusternav.launcher.KachiTheme.c
import com.byd.clusternav.launcher.KachiSpace as Sp

// ⚠ [KIỂM TOÁN UX 2026-09-12] Tách khỏi `GroupTileViews.kt` vì tệp đó đã sát trần 500 dòng. Đường cắt: đây là hai
// **bộ phận** của ô nhóm (một khung đo, một bộ đổ dữ liệu), còn `GroupTileView` là bộ vẽ và `GroupTiles` là cửa vào
// — ba vai khác nhau, không phải cắt bừa cho vừa số dòng.
//
// ⚠⚠ `GroupTileWiringContractTest.tileFiles` phải liệt kê CẢ tệp này: bài canh nối nội dung các tệp rồi
// `assertFalse(...)` (không tra dữ liệu · không ngưỡng · không chép mã thành viên · một mã hex). Bỏ tệp này ra khỏi
// danh sách thì tách tệp trở thành cách lách bài canh — bài `tang ve nhom van la dung cac tep da khai` canh đúng
// giả định đó nên nó sẽ đỏ nếu ai quên.

/**
 * KHUNG LƯỚI Ô CON của ô nhóm — cao **đúng số hàng × [KachiSpace.READ_ROW]**, và **co lại** khi ô không đủ chỗ.
 *
 * ## Vì sao phải là một lớp riêng chứ không đặt `layoutParams` chiều cao cố định
 * Hai đòi hỏi mâu thuẫn nhau nếu chỉ dùng `LayoutParams`:
 *  1. **Không phình** — [ĐO] hàng đọc từng cao 474px với 0.31% mực (xem KDoc [KachiSpace.READ_ROW]). Chiều cao phải
 *     có TRẦN.
 *  2. **Không tràn** — cùng một nhóm cũng nằm được trong ô nhỏ (ô 1/6 màn). Chiều cao cố định ở đó sẽ đẩy hàng nút
 *     ra ngoài khung và **cắt mất** nó, tức chữa một lỗi nhìn-thấy-được bằng một lỗi nhìn-thấy-được khác.
 *
 * Nên: `WRAP_CONTENT` (để cha đưa xuống phần chỗ còn dùng được qua `AT_MOST`) + kẹp `min(trần, chỗ còn)`. Hàng bên
 * trong vẫn dùng `weight` nên chúng tự chia đều phần đã kẹp.
 *
 * ⚠ `UNSPECIFIED` (cha là `ScrollView` / đo thử) ⇒ lấy TRẦN, không lấy 0: trả 0 ở nhánh đó là cách một lưới biến
 * mất mà không ai thấy vì sao.
 */
internal class ReadGrid(context: Context, private val capPx: Int) : LinearLayout(context) {

    init { orientation = VERTICAL }

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        val mode = MeasureSpec.getMode(heightSpec)
        val avail = MeasureSpec.getSize(heightSpec)
        val target = if (mode == MeasureSpec.UNSPECIFIED) capPx else minOf(capPx, avail)
        super.onMeasure(widthSpec, MeasureSpec.makeMeasureSpec(target, MeasureSpec.EXACTLY))
    }
}

/**
 * BỘ ĐỔ DỮ LIỆU cho một ô con: đổi **chữ + màu + nền + độ mờ** mà KHÔNG dựng view mới.
 *
 * Đây là điều kiện để ô nhóm sống được với nhịp trạng thái xe (1 lần/giây trên xe): dựng lại view là tháo/gắn cả
 * hàng nút bên trong nhóm giữa cú chạm ⇒ **mất cú bấm** (bệnh [SOÁT P1-1]).
 *
 * @param numberOnly ô số-chính hiện số KHÔNG kèm đơn vị (đơn vị nằm ở TextView riêng cạnh nó, và đơn vị không đổi
 *   theo nhịp trạng thái xe — nó chỉ đổi khi người dùng chọn đơn vị khác, mà lúc đó ô được dựng lại).
 */
internal class CellBinder(
    private val root: View,
    private val value: TextView,
    private val icon: ImageView?,
    private val numberOnly: Boolean = false,
) {
    fun bind(cell: GroupCell) {
        value.text = if (numberOnly) cell.number else cell.value
        value.setTextColor(c(GroupTileView.tintOf(cell.tone)))
        icon?.setColorFilter(c(if (cell.tone == GroupTone.NEUTRAL) KachiTheme.MUT2 else GroupTileView.tintOf(cell.tone)))
        // Ô số-chính không có nền riêng (nó nằm trên nền của cả ô nhóm) ⇒ chỉ ô con của lưới mới tô nền.
        if (!numberOnly) {
            root.background = KachiTheme.card(
                root.context, Sp.RADIUS_M, GroupTileView.fillOf(cell.tone), GroupTileView.strokeOf(cell.tone),
            )
        }
        // ⚠⚠ [KIỂM TOÁN UX mục 2] LÀM MỜ **GIÁ TRỊ**, KHÔNG LÀM MỜ CẢ Ô.
        //
        // Bản trước đặt `root.alpha = 0.5f`, tức làm mờ luôn **cái nhãn**. [ĐO] ảnh máy ảo: nhãn ô con ra màu
        // (81,87,99) trên nền (25,29,37) = **2.33:1** — dưới xa mức đọc được 4.5:1, và ở cỡ nét chỉ 10px. Hậu quả
        // đúng ngược ý định: off-car (ca THƯỜNG, không phải ca lỗi) người dùng **không đọc được ô đó là cái gì**,
        // trong khi thứ đáng làm mờ chỉ là con số chưa có.
        //
        // Nhãn phải luôn đọc được: nó là câu trả lời cho *"ô này là cái gì"*, và câu đó không phụ thuộc việc xe đã
        // trả số hay chưa.
        root.alpha = 1f
        value.alpha = if (cell.available) 1f else DIM
    }

    private companion object {
        /** Độ mờ của GIÁ TRỊ chưa đọc được. Cùng giá trị bản cũ dùng cho cả ô ⇒ dấu gạch trông y như trước. */
        const val DIM = 0.5f
    }
}
