package com.byd.clusternav.launcher

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.byd.clusternav.launcher.KachiTheme.c
import com.byd.clusternav.launcher.KachiTheme.dpi
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
 *
 * ## ⚠⚠ [KIỂM TOÁN 2026-09-12 mục 3] Kẹp theo "chỗ còn" là CHƯA ĐỦ — phải NHƯỜNG được
 * `LinearLayout` đo con theo THỨ TỰ, nên "chỗ còn" mà lưới này nhìn thấy **chưa trừ hàng nút** (hàng nút đứng sau).
 * [ĐO] nhóm *Kính*: lưới lấy đủ 108px, hàng nút còn thiếu 15px ⇒ bị cắt đáy. Vì thế lưới có thêm [trim]: chỗ gọi đo
 * xong, thấy tràn thì bảo lưới bớt đi đúng phần tràn rồi đo lại. **Sàn co là chỗ nội dung THẬT cần** — đo được, không
 * đoán bằng một hằng dp (xem KDoc [contentPx] về hai lần đoán sai).
 */
internal class ReadGrid(
    context: Context,
    private val capPx: Int,
) : LinearLayout(context) {

    /** Phần chiều cao đã tự nguyện bớt đi trong lượt đo này (px). Reset mỗi lượt đo của ô nhóm — xem [resetTrim]. */
    private var trim = 0

    /**
     * SÀN = chiều cao mà nội dung THẬT cần (đo bằng `UNSPECIFIED`), cập nhật ở mỗi [onMeasure].
     *
     * ## ⚠⚠ [ĐO] Hai bản vá TRƯỚC của tôi đều dùng một con số dp ĐOÁN, và cả hai đều sai — theo hai chiều ngược nhau
     *  • Sàn 52dp (78px, suy từ ô con xếp DỌC): bản **tiếng Anh** vẫn bị cắt hàng nút 11px, vì nhãn `"Window FL"` xuống
     *    hai dòng làm ô nút cao thêm mà lưới thì đã kẹt ở sàn.
     *  • Sàn 27dp (40px, suy từ ô con xếp NGANG một dòng): hàng nút hết bị cắt, nhưng lưới co xuống 51px trong khi
     *    nhãn hai dòng cần 52px ⇒ **chính ô đọc bị cắt chữ** (`"Window"` / `"FL"` mất nửa dưới). Tức tôi chỉ dời lỗi.
     *
     * Nguyên nhân chung: chiều cao ô con phụ thuộc **nhãn có xuống dòng hay không**, mà điều đó phụ thuộc BỀ NGANG và
     * NGÔN NGỮ — không con số dp nào biết trước được. Nên sàn phải là **phép đo**, không phải hằng: hỏi chính bộ bố cục
     * *"nội dung này cần bao nhiêu"*. Nhờ vậy nó tự đúng cho mọi thứ tiếng, mọi cỡ ô, và không có hằng nào để đoán sai
     * lần thứ ba.
     */
    private var contentPx = 0

    init { orientation = VERTICAL }

    /** Bỏ phần đã bớt — gọi ở ĐẦU mỗi lượt đo, để lưới không co dần vĩnh viễn khi ô sau này rộng ra. */
    fun resetTrim() { trim = 0 }

    /**
     * Bớt thêm [px] (cộng dồn), **tối đa tới chỗ nội dung thật cần**. Trả `true` nếu bớt được thêm ⇒ chỗ gọi đo lại.
     *
     * Trả `false` khi đã tới sàn: lúc đó ô thật sự không đủ chỗ cho cả lưới lẫn hàng nút, và chỗ gọi phải chấp nhận —
     * nhưng nó đã nhường **hết mức không làm hỏng chính nó**, chứ không phải chưa thử.
     */
    fun trimBy(px: Int): Boolean {
        if (px <= 0) return false
        val room = (capPx - trim) - contentPx
        if (room <= 0) return false
        trim += minOf(px, room)
        return true
    }

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        // Lượt 1 — hỏi nội dung cần bao nhiêu. `UNSPECIFIED` làm LinearLayout đo con `weight` theo `WRAP_CONTENT`
        // (xem `LinearLayout.measureVertical`: `if (useExcessSpace) lp.height = WRAP_CONTENT` khi mode != EXACTLY),
        // nên con số này là chiều cao THẬT của chữ đang có, không phải một giả định.
        super.onMeasure(widthSpec, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
        contentPx = measuredHeight
        val mode = MeasureSpec.getMode(heightSpec)
        val avail = MeasureSpec.getSize(heightSpec)
        val cap = capPx - trim
        // Lượt 2 — kẹp: không phình quá trần, không tràn quá chỗ có, và không co xuống dưới chỗ chữ cần.
        val target = (if (mode == MeasureSpec.UNSPECIFIED) cap else minOf(cap, avail)).coerceAtLeast(contentPx)
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
            GroupTileView.surfaceOf(root, Sp.RADIUS_M, cell.tone)
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

/**
 * HÀNG NÚT dưới cùng của ô nhóm — tách khỏi `GroupTileViews.kt` (tệp đó vượt trần 500 dòng sau bản vá kiểm toán).
 *
 * Đường cắt hợp lý: hàng nút là **bộ phận** duy nhất của ô nhóm không đọc trạng thái riêng của ô (không đăng ký bộ
 * nối, không bị [GroupTileView.refresh] chạm tới — nó dựng MỘT LẦN), nên nó tách ra được mà không phải chuyền theo
 * thứ gì. Cùng lý do [ReadGrid] và [CellBinder] ở đây.
 *
 * ⚠ `GroupTileWiringContractTest.tileFiles` **đã** gồm tệp này, nên mọi `assertFalse` (không tra dữ liệu · không
 * ngưỡng · không chép mã thành viên · một chỗ mở cổng ra xe) vẫn phủ nguyên phần vừa dời.
 *
 * @param ACTIONS_PER_ROW xem hằng cùng tên bên dưới.
 */
/**
 * Hàng nút dưới cùng (chỉ nhóm STRIP có — bất biến chốt trong [CapabilityGroups]).
 *
 * Dựng bằng **CÙNG** bộ dựng với thanh nút và ô hành động ([ControlTileFactory]) ⇒ nút trong nhóm không thể lệch
 * hành vi với nút ngoài nhóm: cùng chốt chống-bấm-kép theo mã gói, cùng bảng trạng thái dùng chung.
 *
 * ⚠ Cỡ [TileSize.GROUP] (không phải `DOCK`): ô ở đây **hẹp hơn ô thanh nút** — thanh nút cho mỗi ô 84dp cố định,
 * còn hàng này chia bề ngang ô cho tối đa [ACTIONS_PER_ROW] ô ⇒ [ĐO] 82px ở khung 4/12 màn, tức ~55dp. Xem KDoc
 * [TileSize.GROUP] về ba thứ đổi theo và vì sao.
 */
internal fun actionsRow(context: Context, m: GroupBoardModel, data: WidgetData): View {
    // `icons` hỏi `:core`: hàng nút nào có ≥3 nút cùng một hình thì icon KHÔNG mang thông tin ⇒ bỏ, lấy lại 30px
    // bề cao cho chính hàng đó (xem KDoc [GroupBoardModel.actionIconsDistinguish]). Cùng luật G1 đã áp cho ô con
    // XEM — ở đây chỉ là áp nốt cho ô BẤM.
    val factory = ControlTileFactory(
        context, control = { data.control }, size = TileSize.GROUP, icons = m.actionIconsDistinguish,
    )
    return LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, dpi(context, Sp.S), 0, 0)
        val rows = GroupTileView.rowsOf(m.actions, ACTIONS_PER_ROW)
        val cols = rows.maxOfOrNull { it.size } ?: 0
        rows.forEach { rowActions ->
            val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            rowActions.forEach { a ->
                val tile = ActionMacros.byId(a.id)?.let { factory.macroTile(it) }
                    ?: ControlRegistry.byId(a.id)?.let { factory.actionTile(it).view }
                    ?: View(context)
                row.addView(tile, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).also { it.setMargins(dpi(context, Sp.XS), 0, dpi(context, Sp.XS), 0) })
            }
            // Chèn tới số nút THỰC TẾ của hàng dài nhất — KHÔNG tới [ACTIONS_PER_ROW]. Cùng lỗi đã sửa ở
            // [gridRows]: nhóm 4 nút bị chèn 2 ô trống ⇒ 1/3 bề ngang hàng nút bỏ không.
            //
            // ⚠⚠ CHIỀU CAO Ô CHÈN PHẢI LÀ 0, KHÔNG ĐƯỢC `WRAP` — bẫy này đã ăn mất CẢ dải mục đọc.
            // `View.getDefaultSize` trả về TRỌN `specSize` khi spec là `AT_MOST`, nên một `View` trơ khai
            // `WRAP_CONTENT` lại **giãn hết** chỗ còn lại thay vì cao 0. Hàng nút (`WRAP`) vì thế báo cao
            // **648px**, ăn hết phần của thân ô (`weight = 1`) ⇒ thân ô cao **0** ⇒ 9 ô con của nhóm *Đèn*
            // vẫn được DỰNG (log `bodyChildren=2`) mà không hiện một pixel. [ĐO] sau khi sửa: thân ô
            // **0 → 498px**, hàng nút **648 → 125px**.
            repeat(cols - rowActions.size) { row.addView(View(context), LinearLayout.LayoutParams(0, 0, 1f)) }
            addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
    }
}

/** Nút mỗi hàng — 6 vì nhóm nhiều nút nhất (kính · cửa & khoang) có đúng 6. */
private const val ACTIONS_PER_ROW = 6
