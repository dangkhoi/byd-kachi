package com.byd.clusternav.launcher

/**
 * ═══ HÌNH HỌC CHỮ TRONG MỘT Ô — dùng chung cho MỌI widget tổng hợp (thuần `:core`) ════════════════════════════
 *
 * Spec `docs/specs/kachi-274-ux-voice-camera.html` §3 R2 (bảng lốp, 2.74 · UX2) và §3 R7 (mọi bảng khác, UX7).
 *
 * ## Vì sao một tệp THUẦN cho việc "đặt chữ vào ô"
 * Bốn ô vẽ Canvas của launcher ([TyreBoardView] · `RingView` · `PhotoWidgetView` · `GridEditorView` ở `:app`) đều
 * phải trả lời đúng ba câu: *baseline ở đâu để khối chữ cân giữa ô*, *con số có nằm trên trục ô không*, *chữ có
 * vừa ô không*. Trước UX7, mỗi ô tự trả lời bằng **hằng ma thuật** (`cy + textSize*0.36f`, `centerY + big*0.10f −
 * sub*0.60f`, `h/2 − textSize*0.4f`, `centerY + textSize*0.35f`) — bốn công thức khác nhau cho cùng một câu hỏi,
 * và chỗ duy nhất kiểm được là **mắt người trên xe đang chạy**, tức không kiểm được. Nay hình học nằm ở đây: thuần
 * px, không `android.*`, không trạng thái ⇒ kiểm bằng số, off-car, với mọi phông và mọi cỡ ô.
 *
 * Tên cũ `TyreCellLayout` (2.74 · UX2, chỉ bảng lốp) — đổi thành [CellTextLayout] ở UX7 khi bốn ô vẽ dùng chung.
 * Không có bản sao nào ở `:app`: đó là điều `CompositeWidgetLayoutContractTest` canh.
 *
 * ## Ba lỗi mà tệp này khoá lại (owner 2026-09-25 báo trên bảng lốp; UX7 đo thấy đúng ba lỗi đó ở các bảng khác)
 *  1. **Khối 2 dòng lệch** [ĐO]. Công thức cũ của bảng lốp: `numBase = cell.centerY() + big*0.10 − sub*0.60` —
 *     hai hằng KHÔNG mang chiều cao thật của khối 2 dòng, nên tâm khối cao hơn tâm ô đúng `0.255·big − 0.18·sub`
 *     ≈ **0.11 × cellH**; ở `RingView` cùng bệnh nhưng lệch **xuống** `0.146 × d` (đường kính vòng), ở
 *     `PhotoWidgetView` lệch xuống `0.267 × cỡ chữ`. [twoLineTopBaseline] lấy số đo THẬT của phông (ink dòng trên +
 *     khoảng baseline + descent dòng dưới) nên không còn hằng nào. Một dòng ⇒ [centeredBaseline].
 *  2. **Con số lệch TRỤC** [ĐO]. Canh giữa CẢ CỤM `số + khe + đơn vị` quanh tâm ô đẩy bản thân CON SỐ — thứ mắt
 *     đọc — lệch trái đúng `(khe + rộng đơn vị)/2`, còn nhãn/dòng phụ thì canh đúng tâm ⇒ hai dòng KHÔNG cùng trục.
 *     [lineStartX] đặt CON SỐ vào trục ô (đơn vị treo bên phải nó), chỉ đẩy cụm sang khi mép thẻ không đủ chỗ.
 *     Ô dựng bằng `TextView` mắc đúng lỗi này qua `LinearLayout(HORIZONTAL) + gravity = CENTER`; ở `:app` chúng
 *     dùng `AxisRow` — một `ViewGroup` mỏng gọi đúng [lineStartX], không có phép canh thứ hai.
 *  3. **Thẻ đục ĐÈ LÊN ảnh** [ĐO]. Cho thẻ chạy tới `neo ∓ gap` trong khi neo ([CarLayout.wheel]/[CarLayout.part])
 *     nằm BÊN TRONG thân xe ⇒ thẻ đục (vẽ SAU lớp ảnh) cắt mất gương + mép cửa. [cardSpanX] kẹp mép TRONG của thẻ
 *     theo **khung ảnh thật** (letterbox đo được), nên đổi ảnh khác tỉ lệ hay đổi cỡ ô đều tự đúng — không hằng,
 *     không rẽ nhánh theo tên ảnh (CLAUDE.md §7).
 *
 * Mọi hàm ở đây nhận px và trả px, không biết `android.*`, không giữ trạng thái.
 */
object CellTextLayout {

    /**
     * Khoảng baseline dòng trên → dòng dưới, tính theo **cỡ chữ của DÒNG DƯỚI**.
     *
     * Đây là nhịp chữ của bảng lốp từ 2.56 (`SUB_GAP_RATIO`) và nó chạy tốt ngoài hiện trường, nên UX7 mang đúng số
     * đó sang các bảng còn lại thay vì phát minh nhịp thứ hai cho từng ô (CLAUDE.md §6: đường đã chạy thì không đảo).
     * Số ở `:core` để bốn ô vẽ **không thể** lệch nhịp nhau sau một lượt sửa.
     */
    const val SUB_LINE_GAP = 1.35f

    /** Dải ngang của một thẻ giá trị (px trong hệ toạ độ View). [end] < [start] là không thể — đã kẹp. */
    data class CellSpan(val start: Float, val end: Float) {
        val width: Float get() = (end - start).coerceAtLeast(0f)

        /** Còn chỗ vẽ được không (thẻ bị kẹp hết bề rộng khi ô quá hẹp so với ảnh). */
        val usable: Boolean get() = width > 0f
    }

    /**
     * Dải ngang của thẻ ở bên [onLeft] của ẢNH: mép NGOÀI theo lề ô ([pad]), mép TRONG kẹp ra ngoài **khung ảnh
     * thật** [imageLeft]..[imageRight] một khoảng [gap].
     *
     * Vì sao kẹp theo khung ảnh mà không theo neo [anchorX]: neo (bánh xe / bộ phận) nằm TRONG thân xe, nên lấy nó
     * làm mép thẻ là mời thẻ đục trùm lên ảnh. Vẫn giữ `minOf/maxOf` với [anchorX] để nếu ai đổi neo ra ngoài thân
     * xe thì thẻ tự lùi theo, không cần sửa hai chỗ.
     */
    fun cardSpanX(
        onLeft: Boolean,
        viewWidth: Float,
        pad: Float,
        imageLeft: Float,
        imageRight: Float,
        anchorX: Float,
        gap: Float,
    ): CellSpan = if (onLeft) {
        val inner = minOf(anchorX, imageLeft) - gap
        CellSpan(pad, maxOf(pad, inner))
    } else {
        val outer = viewWidth - pad
        val inner = maxOf(anchorX, imageRight) + gap
        CellSpan(minOf(outer, inner), outer)
    }

    /**
     * Baseline của DÒNG TRÊN sao cho khối 2 dòng nằm **đúng giữa** ô theo chiều dọc.
     *
     * @param centerY tâm dọc của ô (thẻ lốp · tâm vòng đo · tâm ô ảnh).
     * @param topInk chiều cao phần MỰC của dòng trên tính từ baseline lên (chữ số ⇒ lấy `getTextBounds`, không lấy
     *   `ascent`: ascent của phông Việt chừa chỗ cho dấu mà chữ số không bao giờ dùng ⇒ khối sẽ tụt xuống).
     * @param lineGap khoảng từ baseline dòng trên tới baseline dòng dưới (xem [SUB_LINE_GAP]).
     * @param bottomInk phần mực của dòng dưới tính từ baseline nó xuống (lấy `descent` của PHÔNG, không lấy ink của
     *   chuỗi: "TT · 27°C" không có nét chìm còn "TT · lệch · 27°C" thì có ⇒ lấy ink là baseline nhảy giữa các ô).
     */
    fun twoLineTopBaseline(centerY: Float, topInk: Float, lineGap: Float, bottomInk: Float): Float {
        val top = topInk.coerceAtLeast(0f)
        val block = top + lineGap.coerceAtLeast(0f) + bottomInk.coerceAtLeast(0f)
        return centerY - block / 2f + top
    }

    /**
     * Baseline của MỘT dòng cân giữa [centerY] theo số đo mực của chính nó — chính là [twoLineTopBaseline] với
     * `lineGap = 0`, viết riêng để chỗ gọi khỏi truyền `0f` và để đọc ra ngay là "một dòng".
     *
     * Chữ số ⇒ `bottomInk = 0` (chữ số không có nét chìm) ⇒ baseline = `centerY + capHeight/2`, tức đúng phép canh
     * quang học mà hằng `0.35f × textSize` của `GridEditorView` đang xấp xỉ (cap-height Roboto = 0.711 em ⇒ 0.3555).
     * Xấp xỉ ấy sai 0.005 em với Roboto và sai nhiều hơn với bất kỳ phông khác — mà phông là thứ ROM quyết, không
     * phải app.
     */
    fun centeredBaseline(centerY: Float, topInk: Float, bottomInk: Float = 0f): Float =
        twoLineTopBaseline(centerY, topInk, 0f, bottomInk)

    /**
     * Tỉ lệ co chữ để một cụm rộng [contentWidth] vừa chỗ trống [room]; `1` = không co (đã vừa). Chỗ gọi nhân với
     * cỡ chữ rồi kẹp sàn cỡ tối thiểu của mình — sàn là chuyện của tầng vẽ, không phải của hình học.
     */
    fun fitScale(contentWidth: Float, room: Float): Float =
        if (contentWidth <= 0f || room <= 0f) 1f else minOf(1f, room / contentWidth)

    /**
     * Toạ độ x (mép trái) để vẽ một dòng rộng [lineWidth] sao cho **phần NEO** rộng [anchorWidth] nằm giữa trục
     * [centerX], mà cả dòng vẫn nằm trong \[[left]+[inset], [right]−[inset]\].
     *
     * Dòng số: `anchorWidth` = bề rộng CON SỐ, `lineWidth` = số + khe + đơn vị ⇒ số ở trục, đơn vị treo bên phải;
     * hết chỗ bên phải thì cả cụm nhích sang trái (thà số lệch vài px còn hơn cắt mất đơn vị).
     * Dòng phụ / nhãn: hai tham số bằng nhau ⇒ canh giữa đúng trục ⇒ hai dòng CÙNG một trục.
     */
    fun lineStartX(
        centerX: Float,
        anchorWidth: Float,
        lineWidth: Float,
        left: Float,
        right: Float,
        inset: Float,
    ): Float {
        val lo = left + inset
        val hi = right - inset - lineWidth
        if (hi <= lo) return lo
        return (centerX - anchorWidth / 2f).coerceIn(lo, hi)
    }
}
