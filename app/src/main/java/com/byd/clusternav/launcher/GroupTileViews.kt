package com.byd.clusternav.launcher

import android.content.Context
import android.graphics.Typeface
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.byd.clusternav.launcher.KachiTheme.c
import com.byd.clusternav.launcher.KachiTheme.dpi
import com.byd.clusternav.launcher.KachiSpace as Sp

/**
 * Ô NHÓM — tự đổi chữ **TẠI CHỖ** theo nhịp trạng thái xe.
 *
 * ## ⚠⚠ Vì sao PHẢI có [refresh] thay vì để chỗ gọi thay view
 * Luật của [WidgetViews.refreshRead]: ô con là **mục ĐỌC** thì được thay view, ô **HÀNH ĐỘNG** thì giữ. Nhóm khai
 * [CapabilityKind.READ] (đúng — nội dung chính của nó là thứ để xem, xem KDoc [CapabilityCatalog.kindOf]), nên nếu
 * không có đường làm mới tại chỗ thì cả ô nhóm bị thay **mỗi giây** trên xe, kéo theo **hàng nút bên trong** của
 * nhóm kính/cửa/đèn. Đó đúng là bệnh [SOÁT P1-1] đã vá cho ô trộn: nút bị tháo/gắn giữa cú chạm ⇒ **mất cú bấm**.
 *
 * Vì thế: chữ và sắc thái đổi qua [CellBinder] (không dựng view mới), còn hàng nút dựng **một lần** ở [bind] và
 * [refresh] **không bao giờ chạm tới**.
 */
class GroupTileView(context: Context) : LinearLayout(context) {

    private var groupId: String? = null
    private var tyreBoardPort: ((Context, WidgetData) -> View)? = null

    /**
     * Bộ nối theo **MÃ ô con**, không theo chỉ số.
     *
     * Chỉ số phụ thuộc vào **thứ tự dựng view**, mà thứ tự đó khác nhau giữa ba bộ vẽ (thẻ CARD dựng số chính trước,
     * dải STRIP dựng tuần tự). Khoá theo mã thì đổi bố cục cũng không thể làm số của ô con này nhảy sang ô con khác —
     * đúng loại lỗi mà `TyreBoardView.set` phải chuẩn hoá độ dài để tránh.
     */
    private val binders = LinkedHashMap<String, CellBinder>()
    private val bodyHolder = FrameLayout(context)

    /** Sổ đăng ký bảng `BOARD` (dựng + đổ dữ liệu tại chỗ) — xem KDoc [GroupBoardBinder]. */
    private val boards = GroupBoardBinder()

    /**
     * Lưới ô con ĐANG dựng — thứ duy nhất trong ô này **nhường chỗ được** (xem [onMeasure]).
     *
     * `null` với nhóm BOARD (bảng tự vẽ, không có lưới) ⇒ ở đó không có gì nhường. Từ U9 pha 2 nhóm BOARD **có thể
     * có nút** (*Cửa & khoang*), nên chỗ cho hàng nút được lo bằng cách khác: thân ô nhận `weight` để `LinearLayout`
     * đo hàng nút — chi phí CỐ ĐỊNH — trước rồi mới chia phần dư cho bảng (xem [bind]). Bảng Canvas co được tới 0 mà
     * không mất gì phải bấm; lưới chữ thì không, nên hai ca cần hai cách.
     */
    private var readGrid: ReadGrid? = null

    init {
        orientation = VERTICAL
        val p = dpi(context, Sp.M)
        setPadding(p, p, p, p)
    }

    /** Dựng ô. Trả `false` nếu [id] không phải nhóm ⇒ chỗ gọi hiện ô suy giảm. */
    fun bind(id: String, data: WidgetData, tyreBoard: (Context, WidgetData) -> View): Boolean {
        val model = GroupBoard.of(id, data.car, data.units) ?: return false
        groupId = id
        tyreBoardPort = tyreBoard
        removeAllViews(); binders.clear(); readGrid = null
        bodyHolder.removeAllViews()

        addView(header(model), LayoutParams(MATCH, WRAP))
        // ⚠⚠ [KIỂM TOÁN UX mục 3] Thân ô **KHÔNG còn `weight = 1`**.
        //
        // [ĐO] với `weight = 1` thân ô ăn hết phần còn lại: nhóm *Kính* ở khung to ra hàng đọc **1162×474px** chứa
        // **0.31% mực** (4 nhãn + 4 dấu gạch) = **64% chiều cao ô** cho gần như không thông tin nào. Nay thân cao
        // đúng nội dung (mỗi dòng dữ liệu [Sp.READ_ROW], kẹp lại khi ô hẹp — xem [ReadGrid]), còn phần dư đi vào
        // **khoảng thở** bên dưới ⇒ hàng nút nằm ở đáy ô, đúng chỗ ngón tay tìm tới.
        //
        // ⚠⚠ U9 pha 2 — NGOẠI LỆ: bảng `BOARD` **có nút** thì thân ô lấy `weight` thay vì `WRAP`.
        // Một ô vẽ Canvas trơ khai `MATCH_PARENT` báo cao **trọn** phần còn lại (`View.getDefaultSize` trả nguyên
        // `specSize` khi spec là `AT_MOST` — đúng cái bẫy đã ăn mất cả dải mục đọc ở [actionsRow]), nên với nhóm
        // *Cửa & khoang* nó sẽ nuốt hết chỗ và đẩy hàng nút ra ngoài lề rồi bị cắt, **không ném, không log**. Cho
        // thân `weight` thì `LinearLayout` đo hàng nút (chi phí CỐ ĐỊNH) trước và chỉ chia **phần dư** cho bảng.
        val boardWithActions = model.shape == WidgetShape.BOARD && model.hasActions
        addView(bodyHolder, if (boardWithActions) LayoutParams(MATCH, 0, 1f) else LayoutParams(MATCH, WRAP))
        buildBody(model, data)
        // Khoảng thở đẩy hàng nút xuống đáy ô — không cần khi thân đã ăn phần dư bằng `weight` (hai `weight` cùng lúc
        // thì bảng chỉ còn một nửa chỗ mà chẳng để làm gì).
        if (!boardWithActions) addView(View(context), LayoutParams(MATCH, 0, 1f))
        // Hàng nút dựng MỘT LẦN. [refresh] không chạm ⇒ cú bấm không bị cắt giữa chuỗi MotionEvent.
        if (model.hasActions) addView(actionsRow(context, model, data), LayoutParams(MATCH, WRAP))
        return true
    }

    /**
     * ═══ [KIỂM TOÁN 2026-09-12 mục 3] ĐO THEO THỨ TỰ ƯU TIÊN, KHÔNG THEO THỨ TỰ XẾP ═══════════════════════════
     *
     * ## Bệnh — hàng nút bị CẮT ĐÁY, và cắt IM LẶNG
     * `LinearLayout` đo con **theo thứ tự xếp**, đưa cho mỗi con "chỗ còn lại tính tới lúc đó". Ô nhóm xếp
     * `đầu ô → lưới đọc → khoảng thở → hàng nút`, nên lưới đọc (đứng trước) nhìn thấy phần còn lại **chưa trừ hàng
     * nút** và lấy đủ trần [KachiSpace.READ_ROW]; hàng nút đứng cuối, chỉ nhận phần thừa, và khi thiếu thì nó **tràn
     * ra ngoài** rồi bị cha cắt theo lề — không ném, không log, không có gì đỏ.
     *
     * [ĐO] ảnh máy ảo, nhóm *Kính* ở khung 4/12 màn (hộp nội dung 267px): đầu ô 26 + lưới **108** + hàng nút cần
     * **148** ⇒ thiếu 15px ⇒ bốn ô kính bị cắt phẳng ở y=464 (mất vành dưới của nút *Đóng/Mở* + trọn lề dưới), trong
     * khi hai ô macro cạnh đó thấp hơn nên nguyên vẹn.
     *
     * ## Chữa — cái nào CO ĐƯỢC thì nhường
     * Nút phải bấm được ⇒ chi phí CỐ ĐỊNH. Dòng dữ liệu thì co được, và [ReadGrid] đã có sẵn giao kèo đó. Nên: đo một
     * lượt bình thường, thấy tổng vượt hộp thì bảo lưới bớt đúng phần vượt và đo lại. Vòng lặp bị chặn bởi
     * [ReadGrid.trimBy] (`false` khi tới sàn) nên tối đa hai lượt.
     *
     * ⚠ **PHẢI `resetTrim()` ở đầu mỗi lượt**: không thì lần đo sau (ô rộng ra, đổi bố cục) vẫn dùng phần đã bớt của
     * lần trước ⇒ lưới **co dần một chiều** và không bao giờ trở lại — đúng họ lỗi "trạng thái rò qua các lượt vẽ".
     */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val grid = readGrid
        grid?.resetTrim()
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        if (grid == null || MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED) return
        val box = MeasureSpec.getSize(heightMeasureSpec) - paddingTop - paddingBottom
        var need = 0
        for (i in 0 until childCount) need += getChildAt(i).measuredHeight
        if (need > box && grid.trimBy(need - box)) super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    /**
     * Đổ lại số/sắc thái **tại chỗ**. Trả `false` ⇒ chỗ gọi lùi về dựng lại cả ô.
     *
     * Ô con nào không có bộ nối (vd bộ phận do bảng Canvas tự vẽ theo hình học xe) thì bỏ qua — không ném, vì
     * "không có gì để đổ" khác với "làm mới thất bại".
     */
    fun refresh(data: WidgetData): Boolean {
        val id = groupId ?: return false
        val model = GroupBoard.of(id, data.car, data.units) ?: return false
        model.cells.forEach { cell -> binders[cell.id]?.bind(cell) }
        // Bảng nào có đường đổ dữ liệu thì giữ NGUYÊN ô vẽ (Paint đã cấp phát sẵn trong nó); chỉ bảng lốp phải thay
        // ô vẽ, và chỉ nhóm lốp — nhóm KHÔNG có nút — mới đi vào nhánh đó (xem KDoc [GroupBoardBinder.refill]).
        if (model.shape == WidgetShape.BOARD && !boards.refill(model)) {
            bodyHolder.removeAllViews()
            buildBody(model, data)
        }
        return true
    }

    // ── Đầu ô ───────────────────────────────────────────────────────────────────────────────────────────
    /** Icon + nhãn nhóm (+ chấm "chưa kiểm"). Nhóm có nhiều số nên phải tự nói nó là nhóm gì. */
    private fun header(m: GroupBoardModel): View = LinearLayout(context).apply {
        orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        val r = KachiTheme.iconRes(m.icon)
        if (r != 0) addView(
            ImageView(context).apply { setImageResource(r); setColorFilter(c(KachiTheme.MUT)) },
            LayoutParams(dpi(context, Sp.ICON_XS), dpi(context, Sp.ICON_XS))
                .also { it.marginEnd = dpi(context, Sp.S) },
        )
        addView(
            // ⚠ [T1] Vai màu ở đây GIỮ NGUYÊN `MUT2` — và việc giữ nguyên là một kết luận có đo, không phải bỏ qua.
            // Lượt đo đầu của tôi báo nhãn này chỉ **4.12:1** trên bảng sáng và tôi đã đổi sang `INK2`. Nhưng phép đo
            // đó SAI: nó lấy trung vị mực trên **hộp a11y rộng 1158px** trong khi chữ "Lốp" chỉ chiếm **31px**, nên
            // phân vị rơi vào viền khử răng cưa chứ không vào lòng nét. Cắt về đúng vùng có mực thì `MUT2` đo được
            // **5.67:1** — ĐẠT. Đã hoàn nguyên: không đổi thẩm mỹ một màn owner đã duyệt dựa trên một số đo đã bị bác.
            text(context, m.label, 12f, KachiTheme.MUT2).apply {
                letterSpacing = 0.06f; gravity = Gravity.START
            },
            LayoutParams(0, WRAP, 1f),
        )
        // ⚠ U10 (soát 2026-09-13) — chấm vẽ bằng [PickerBadge.dot], KHÔNG còn [KachiTheme.AMBER].
        // [ĐO] ảnh máy ảo bố cục 2 cột: sau khi U10 hạ chấm của thanh nút xuống mực mờ, hai ô nhóm "Lốp" /
        // "Cảm biến đỗ" vẫn sáng **hổ phách** ở góc tiêu đề — cùng một sự thật ([GroupBoardModel.needsBadge] =
        // `cells.any{…} || actions.any{…}` của CHÍNH [EvidenceTier.needsBadge], xem `GroupBoardModel.kt:192`),
        // nói bằng hai giọng, trên cùng một màn hình. Đây KHÔNG phải cảnh báo thật: tông cảnh báo thật của ô nhóm
        // đi đường khác ([GroupTone.ALERT]/[GroupTone.WARN] ở `tint`/`toneFill`), nên hổ phách ở đây chỉ tranh mất
        // sắc độ của chúng.
        if (m.needsBadge) addView(
            PickerBadge.dot(context),
            LayoutParams(dpi(context, Sp.DOT), dpi(context, Sp.DOT)),
        )
    }

    // ── Thân ô theo bộ vẽ ───────────────────────────────────────────────────────────────────────────────
    private fun buildBody(m: GroupBoardModel, data: WidgetData) {
        val body = when (m.shape) {
            WidgetShape.BOARD -> boardBody(m, data)
            WidgetShape.CARD -> cardBody(m)
            else -> stripBody(m)
        }
        bodyHolder.addView(body, FrameLayout.LayoutParams(MATCH, MATCH))
    }

    /**
     * **BOARD** — bảng theo hình học thật của xe; nhóm nào chưa có bảng riêng thì lùi về dải STRIP (thà xếp hàng
     * ngang còn hơn ô trống). Phép chọn + đường đổ dữ liệu nằm ở [GroupBoardBinder].
     */
    private fun boardBody(m: GroupBoardModel, data: WidgetData): View =
        boards.build(context, m, data, tyreBoardPort) { stripBody(m) }

    /**
     * **STRIP** — dải ô con: icon + nhãn ngắn + trạng thái, ô con **sáng lên** khi đang bật / đang cảnh báo.
     *
     * ## ⚠ Nhóm CÓ NÚT thì ô con bỏ icon — [ĐO] máy ảo
     * Thân ô nhận phần CÒN LẠI sau hàng nút (`WRAP`), nên nhóm có nút thì ô con thấp hơn nhiều và clip theo thứ tự
     * icon → nhãn → số: *Cửa & khoang* (10 mục + 6 nút) từng **chỉ còn ICON**, mất cả nhãn lẫn số. Nhóm KHÔNG nút
     * lấy trọn chiều cao và [ĐO] hiện đủ ⇒ chúng giữ icon. (Hai nhóm không-nút của phép đo gốc 2026-09-12 — *ADAS*
     * 10 mục và *Người ngồi* 5 mục — đã xoá 2026-09-16 cùng toàn bộ ADAS/an toàn, owner; luật thì không đổi.)
     *
     * Bỏ icon (thay vì thu chữ) vì **con số là thứ không được mất**: ô chỉ còn icon nói ít hơn cả ô trống — nó
     * trông như đã có dữ liệu.
     */
    private fun stripBody(m: GroupBoardModel): View =
        // ⚠ [KIỂM TOÁN UX mục 4c] Điều kiện THỨ HAI: icon phải **phân biệt được**. Ba ô con cùng một hình thì icon
        // không mang thông tin, nó chỉ ăn bề cao của cái nhãn và con số (xem [GroupBoardModel.iconsDistinguish]).
        // Quyết định "có phân biệt được không" nằm ở `:core` — ở đây chỉ dùng.
        gridRows(m.cells, MAX_PER_ROW, withIcon = !m.hasActions && m.iconsDistinguish)

    /**
     * **CARD** — một số chính cỡ lớn + các số phụ xếp hàng, nhãn dùng nhãn ngắn.
     *
     * Số chính = thành viên ĐẦU của nhóm (thứ tự khai = thứ tự trình bày), nên "Năng lượng & sạc" mở ra là thấy `%`
     * pin trước, không phải công suất động cơ.
     */
    private fun cardBody(m: GroupBoardModel): View = LinearLayout(context).apply {
        orientation = VERTICAL
        // Số chính cao [Sp.LEAD_ROW] (= 1.5 dòng dữ liệu) — KHÔNG ăn phần dư của ô. Trước đây nó `WRAP` và phần dư
        // dồn vào lưới số phụ bên dưới, nên thẻ CARD cũng bị đúng bệnh phình của kiểm toán mục 3.
        m.lead?.let { addView(leadRow(it), LayoutParams(MATCH, dpi(context, Sp.LEAD_ROW))) }
        // `withIcon = false` — xem KDoc [stripCell]: thẻ CARD không đủ bề cao cho icon + nhãn + số.
        addView(gridRows(m.rest, CARD_PER_ROW, withIcon = false), LayoutParams(MATCH, WRAP))
    }

    /**
     * Lưới ô con: các hàng **đều nhau**, mỗi hàng ≤ [perRow], hàng thiếu ô thì chèn chỗ trống.
     *
     * Chèn chỗ trống là để ô con hàng trên và hàng dưới rộng **BẰNG NHAU** — không thì hàng 4 ô có ô to hơn hàng
     * 5 ô và trông như hai cỡ ô khác nhau trong cùng một bảng.
     *
     * ⚠⚠ **Chèn tới số ô THỰC TẾ của hàng dài nhất, KHÔNG tới [perRow]** — [ĐO] trên máy ảo: nhóm Kính có 4 ô con
     * với `perRow = 5`, bản đầu chèn `5 − 4 = 1` ô trống ⇒ **1/5 bề ngang bị bỏ trống** trong khi hàng nút bên
     * dưới trải hết ô, nhìn ra ngay là lệch. [rowsOf] vốn đã chia đều (4 ô ⇒ một hàng 4), nên trần `perRow` chỉ
     * là *giới hạn*, không phải *số cột phải phủ*.
     */
    private fun gridRows(cells: List<GroupCell>, perRow: Int, withIcon: Boolean): View {
        val rows = rowsOf(cells, perRow)
        val cols = rows.maxOfOrNull { it.size } ?: 0
        // TRẦN chiều cao = số hàng × chiều cao MỘT dòng dữ liệu. Đây là chỗ chặn phình (kiểm toán mục 3): lưới xin
        // đúng chỗ nó cần, không xin "tất cả chỗ còn lại". [ReadGrid] tự co khi ô hẹp hơn trần, và **sàn co** là chỗ
        // chữ thật cần — nó tự đo, nên không có con số dp nào để đoán sai (xem KDoc [ReadGrid]).
        return ReadGrid(context, dpi(context, Sp.READ_ROW) * rows.size).apply {
            readGrid = this
            rows.forEach { rowCells ->
                val row = LinearLayout(context).apply { orientation = HORIZONTAL }
                rowCells.forEach { cell ->
                    row.addView(stripCell(cell, withIcon), LayoutParams(0, MATCH, 1f).also { it.setMargins(xs(), xs(), xs(), xs()) })
                }
                repeat(cols - rowCells.size) { row.addView(View(context), LayoutParams(0, MATCH, 1f)) }
                addView(row, LayoutParams(MATCH, 0, 1f))
            }
        }
    }

    /**
     * Một ô con của lưới. **KHÔNG gắn chạm** — xem OQ1 ở KDoc [GroupTiles].
     *
     * ## ⚠ [withIcon] = false ⇒ xếp NGANG (nhãn · số)
     * Ô con dọc cần ≈ `ICON_XS + XS + nhãn + số` ≈ **50dp**. Thẻ CARD chia phần còn lại cho 3 hàng nên mỗi hàng chỉ
     * còn ~35dp ⇒ [ĐO] 9 ô con của *"Khí hậu & không khí"* hiện **CHỈ CÓ ICON**, nhãn và số bị cắt sạch — ô trông
     * có nội dung mà không nói gì. Xếp ngang chỉ cần ~20dp nên số luôn còn chỗ.
     *
     * Đúng §4.3: CARD là *"một số chính + các số phụ xếp hàng, dùng nhãn ngắn"* — không nói tới icon cho từng số
     * phụ. STRIP thì giữ icon vì ở đó icon **là** chỉ báo trạng thái, không phải hình trang trí.
     */
    private fun stripCell(cell: GroupCell, withIcon: Boolean): View {
        val icon = KachiTheme.iconRes(cell.icon).takeIf { withIcon && it != 0 }?.let { res ->
            ImageView(context).apply { setImageResource(res) }
        }
        // ⚠ [KIỂM TOÁN UX mục 3] GIÁ TRỊ là thứ TO NHẤT trong ô con (13sp → 17sp): người mở ô nhóm ra để xem SỐ,
        // còn nhãn chỉ để biết số đó là của cái gì. Trước đây số 13sp / nhãn 9.5sp — cùng bậc, nên mắt không có chỗ
        // để dừng. Đơn vị đã nằm ngay trong [GroupCell.value] ("2.4 bar") nên nó đi cùng số, không xuống caption.
        val value = text(context, cell.value, VALUE_SP, tintOf(cell.tone), bold = true).apply {
            maxLines = 1; ellipsize = TextUtils.TruncateAt.END
        }
        // ⚠⚠ [KIỂM TOÁN UX mục 2] Nhãn dùng [KachiTheme.MUT] (147,160,180) — KHÔNG dùng `MUT2` (139,149,167) nữa —
        // và cỡ 9.5sp → 12sp (nét ~10px → ~13px). [ĐO] nhãn cũ ra **2.33:1** trên nền ô con, dưới xa mức đọc được
        // 4.5:1; phần lớn độ tối đến từ việc CẢ Ô bị làm mờ 50% (đã sửa ở [CellBinder]), phần còn lại từ chính mã
        // màu. MUT trên nền ô con cho **5.9:1** ⇒ đọc được cả khi off-car.
        val labelView = text(context, cell.label, LABEL_SP, KachiTheme.MUT).apply {
            // ⚠⚠ HAI DÒNG, không phải một — đây là **lỗi tôi tự gây ra khi nới cỡ nhãn** và tìm ra bằng ảnh chụp.
            // [ĐO] nhãn 13.5sp trong ô con rộng ~130px (nhóm Kính ở khung nhỏ) bị cắt thành `"Kính trư…"` cho CẢ
            // trước-trái LẪN trước-phải, và `"Kính sau…"` cho cả hai bánh sau ⇒ bốn ô thành hai cặp **giống hệt
            // nhau**. Đó đúng lỗi 18-nhãn-trùng mà `displayLabel`/`shortLabel` của RW0 sinh ra để chữa: nới cỡ chữ
            // mà không nới chỗ chứa là đổi một lỗi đọc-được thành một lỗi phân-biệt-được.
            // Hai dòng thì `"Kính trước-trái"` ngắt ở dấu cách thành `Kính` / `trước-trái` — đọc đủ, và dòng dữ liệu
            // cao 108px vẫn chứa được (2 dòng nhãn 40px + số 28px + lề 8px = 76px).
            maxLines = 2; ellipsize = TextUtils.TruncateAt.END
        }
        val root = LinearLayout(context).apply {
            // CARD xếp NGANG (nhãn · số), STRIP xếp DỌC (icon / nhãn / số) — xem KDoc trên.
            orientation = if (withIcon) VERTICAL else HORIZONTAL
            gravity = Gravity.CENTER
            val p = xs(); setPadding(p, p, p, p)
            if (icon != null) addView(
                icon,
                LayoutParams(dpi(context, Sp.ICON_XS), dpi(context, Sp.ICON_XS)).also { it.bottomMargin = dpi(context, Sp.XS) },
            )
            if (withIcon) {
                addView(labelView)
                addView(value)
            } else {
                // Nhãn co được (weight) để số LUÔN còn chỗ: thà cắt "Cảm biến PM2.5" còn hơn mất con số.
                addView(labelView, LayoutParams(0, WRAP, 1f))
                addView(value, LayoutParams(WRAP, WRAP).also { it.marginStart = dpi(context, Sp.XS) })
            }
        }
        CellBinder(root, value, icon).also { binders[cell.id] = it; it.bind(cell) }
        return root
    }

    /** Số chính: số to + đơn vị nhỏ bên cạnh + nhãn ngắn phía dưới. */
    private fun leadRow(cell: GroupCell): View {
        val big = text(context, cell.number, 34f, tintOf(cell.tone), bold = true).apply { maxLines = 1 }
        val root = LinearLayout(context).apply {
            orientation = VERTICAL; gravity = Gravity.CENTER
            addView(LinearLayout(context).apply {
                orientation = HORIZONTAL; gravity = Gravity.CENTER
                addView(big)
                if (cell.unit.isNotEmpty()) addView(
                    text(context, " ${cell.unit}", 14f, KachiTheme.MUT).apply {
                        setPadding(0, dpi(context, Sp.S), 0, 0)
                    },
                )
            })
            addView(text(context, cell.label, 11f, KachiTheme.MUT2).apply { letterSpacing = 0.06f })
        }
        // Số chính CŨNG phải đổi tại chỗ ⇒ đăng ký bộ nối như mọi ô con khác. Thiếu bước này thì mọi số phụ chạy
        // trong khi số to đứng im — sai theo cách rất dễ tưởng là "xe chưa trả dữ liệu".
        CellBinder(root, big, null, numberOnly = true).also { binders[cell.id] = it; it.bind(cell) }
        return root
    }


    /** [Sp.XS] quy ra pixel — khe/lề nhỏ nhất của lưới ô con, gọi nhiều lần nên tách cho gọn. */
    private fun xs(): Int = dpi(context, Sp.XS)

    companion object {
        private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

        // ── T5 XONG: năm hằng khoảng cách/cỡ TẠM ở đây đã bị XOÁ, mọi chỗ đi thẳng vào thang KachiSpace ──
        // PAD 10 → Sp.M(12) · GAP 6 → Sp.S(8) · XS 3 → Sp.XS(4) · ICON_HEAD 16 + ICON_CELL 15 → Sp.ICON_XS(16).
        // ⚠ Xoá hẳn thay vì trỏ sang thang: hằng cũ tên `XS` mang giá trị **3** trong khi `Sp.XS` là **4** — hai
        // thứ cùng tên khác giá trị trong cùng một tệp là cái bẫy đọc-nhầm, và ICON_HEAD/ICON_CELL lệch nhau 1dp
        // (16 vs 15) cho hai icon nằm cạnh nhau, tức là lệch mà không ai cố ý.

        /** Ô con tối đa mỗi hàng của dải STRIP — 5 vì nhóm dài nhất có 10 thành viên (⇒ 2 hàng đều). */
        private const val MAX_PER_ROW = 5

        /**
         * Cỡ chữ GIÁ TRỊ / NHÃN của ô con (sp).
         *
         * Chúng phải cách nhau đủ xa để mắt đọc ra **thứ bậc**: giá trị là nội dung, nhãn là chú thích. Trước lượt
         * kiểm toán là 13 / 9.5 — cùng bậc, nên ô con trông như hai dòng chữ ngang hàng và người xem phải đọc cả hai
         * mới biết đâu là số. Tỉ lệ mới ≈ 1.4× (17 / 12), cùng tỉ lệ mà thẻ CARD dùng giữa số chính và nhãn của nó.
         *
         * ## ⚠ Vì sao 13.5sp mà KHÔNG phải 16sp
         * Kiểm toán ghi *"cỡ ≥ 16dp"*. [ĐO] ở density 1.5: 13.5sp cho **nét cao 16px** (đạt, nếu đọc "16" là chiều
         * cao nét — con số kiểm toán dùng để đo cái sai là *"cao nét 10px"*, cùng thang). Còn hiểu "16" là **cỡ
         * chữ** thì nhãn 16sp = 24px, tức **bằng con số** (19sp = 28px chỉ hơn 4px) ⇒ mất hẳn thứ bậc mà chính
         * kiểm toán đòi ở mục 3 (*"giá trị là thứ to nhất"*). Hai yêu cầu đó xung đột trong một dòng cao 108px, nên
         * tôi chọn giữ thứ bậc và đạt "16" theo thang mà số đo của kiểm toán đang dùng. Ghi ra để owner bác được.
         */
        private const val VALUE_SP = 19f
        private const val LABEL_SP = 13.5f

        /** Số phụ mỗi hàng của thẻ CARD — 3, vì số phụ có nhãn dài hơn ô con của dải. */
        private const val CARD_PER_ROW = 3

        /**
         * Chia [items] thành các hàng **đều nhau**, mỗi hàng ≤ [max].
         *
         * Chia đều (9 ⇒ 5+4) chứ không nhồi-rồi-tràn (10 với max 6 ⇒ 5+5, không phải 6+4): hàng cuối chỉ có 1 ô con
         * trông như lỗi bố cục. Danh sách rỗng ⇒ không hàng nào.
         *
         * ## ⚠⚠ [SOÁT G1] Bản trước KHÔNG giữ đúng lời hứa đó
         * Bản trước tính một bề rộng hàng duy nhất `per = ceil(n / rows)` rồi `chunked(per)`. Với 12 nhóm hiện nay nó
         * ra đúng, nhưng nói chung thì **không**: [ĐO] `rowsOf(7, 3)` ra `[3, 3, 1]` — đúng cái *"hàng cuối trơ trọi"*
         * mà KDoc này cấm; `rowsOf(13, 5)` ra `[5, 5, 3]`. Tức lời hứa chỉ đúng nhờ **may mắn về dữ liệu**, và ngày
         * ai đó thêm một thành viên vào nhóm là nó vỡ, im lặng.
         *
         * Nay chia theo `base` + `extra`: `extra` hàng ĐẦU nhận thêm một ô, nên hàng dài nhất và ngắn nhất **luôn**
         * chênh nhau tối đa 1 ô. `rowsOf(7, 3)` ⇒ `[3, 2, 2]`, `rowsOf(13, 5)` ⇒ `[5, 4, 4]`.
         *
         * **Kết quả cho 12 nhóm hiện nay KHÔNG đổi một hàng nào** — có bài test ghim đúng điều đó
         * (`phep chia hang khong doi hinh dang cua 12 nhom dang co`), vì đây là sửa để khớp giao kèo, không phải đổi
         * thiết kế.
         *
         * Trần vẫn được giữ: `rows = ceil(n/max)` ⇒ `n ≤ rows*max`, nên `base+1 ≤ max` mỗi khi `extra > 0`.
         */
        fun <T> rowsOf(items: List<T>, max: Int): List<List<T>> {
            if (items.isEmpty() || max <= 0) return emptyList()
            val rows = (items.size + max - 1) / max
            val base = items.size / rows
            val extra = items.size % rows
            var at = 0
            return (0 until rows).map { i ->
                val n = base + if (i < extra) 1 else 0
                ArrayList(items.subList(at, at + n)).also { at += n }
            }
        }

        /** Sắc thái NẶNG NHẤT trong ô nhóm — cho ô nén và cho màu tóm tắt. */
        fun worstTone(m: GroupBoardModel): GroupTone = when {
            m.cells.any { it.tone == GroupTone.ALERT } -> GroupTone.ALERT
            m.cells.any { it.tone == GroupTone.WARN } -> GroupTone.WARN
            m.cells.any { it.tone == GroupTone.ACTIVE } -> GroupTone.ACTIVE
            else -> GroupTone.NEUTRAL
        }

        /**
         * Sắc thái → màu chữ/icon. Đây là ĐẦU `:app` của giao kèo [GroupTone]: `:core` nói *"đang bật"* / *"cảnh
         * báo"*, chỗ này mới biết màu — nhờ vậy dự án chỉ có MỘT bảng màu ([KachiTheme]).
         *
         * "Đang bật" dùng CHÍNH màu mực (không phải một màu riêng): thứ làm nó nổi lên là **nền + viền** accent, đúng
         * cách thanh nút thể hiện ô đang bật (`gradientSoft` + chữ trắng). Thêm màu chữ thứ năm chỉ để nói "đang bật"
         * sẽ khiến dải đèn có 4 màu chữ cùng lúc.
         */
        fun tintOf(t: GroupTone): String = when (t) {
            GroupTone.NEUTRAL, GroupTone.ACTIVE -> KachiTheme.INK
            GroupTone.WARN -> KachiTheme.AMBER
            GroupTone.ALERT -> KachiTheme.RED
        }

        /**
         * Nền ô con. Suy ra từ CHÍNH màu của [KachiTheme] bằng cách thêm kênh trong suốt — KHÔNG khai mã màu mới (đó
         * là cách một bảng màu thứ hai bắt đầu). Cùng thủ pháp với `#264c7dff` đang dùng ở lưới chọn khả năng.
         */
        fun fillOf(t: GroupTone): String = when (t) {
            GroupTone.NEUTRAL -> CELL_BG
            GroupTone.ACTIVE -> alpha(KachiTheme.ACCENT, "26")
            GroupTone.WARN -> alpha(KachiTheme.AMBER, "26")
            GroupTone.ALERT -> alpha(KachiTheme.RED, "26")
        }

        /**
         * VISUAL-REFRESH P1 · T3 — nền ô con **theo trạng thái**, một chỗ tra cho cả nhóm lẫn ô con.
         *
         * Vì sao rẽ nhánh theo [GroupTone] chứ không chuyển hết sang [KachiTheme.surface]: ở [GroupTone.WARN] và
         * [GroupTone.ALERT], **màu nền CHÍNH LÀ thông tin** (hổ phách = chưa kiểm · đỏ = cảnh báo). Phủ một chuyển
         * sắc trung tính lên đó là lấy mất tín hiệu để đổi lấy chất liệu — sai đánh đổi trên một màn hình lái xe.
         * Hai tone còn lại không mang màu riêng nên chúng nhận đúng bề mặt mới:
         *  • [GroupTone.NEUTRAL] → [SurfaceTone.NEUTRAL] (+ sắc lĩnh vực nếu chỗ gọi biết)
         *  • [GroupTone.ACTIVE] → [SurfaceTone.ACTIVE] — "đang bật" của nhóm và "đang bật" của ô picker từ nay
         *    trông **giống nhau**, trước đây là hai cách vẽ khác nhau cho cùng một nghĩa.
         */
        fun surfaceOf(view: android.view.View, radius: Int, t: GroupTone, domain: Domain? = null) {
            // P1b: hai tone chất liệu đi qua KachiGlass (kính khi có ảnh nền, KachiTheme.surface khi không) — nhận
            // VIEW chứ không trả Drawable, vì cửa sổ kính phải biết vị trí của thẻ trong cửa sổ.
            when (t) {
                GroupTone.NEUTRAL -> KachiGlass.apply(view, radius, SurfaceTone.NEUTRAL, domain)
                GroupTone.ACTIVE -> KachiGlass.apply(view, radius, SurfaceTone.ACTIVE, domain)
                // Qua `KachiGlass.plain` chứ không gán thẳng `background`: ô con DÙNG LẠI view và đổi sắc thái theo
                // nhịp trạng thái (1 Hz), nên nếu thẻ kính của lượt NEUTRAL/ACTIVE trước còn lại thì lượt
                // `KachiGlass.refresh` khi ảnh nền đổi sẽ đắp kính đè lên nền cảnh báo (màu nền LÀ thông tin).
                GroupTone.WARN, GroupTone.ALERT ->
                    KachiGlass.plain(view, KachiTheme.card(view.context, radius, fillOf(t), strokeOf(t)))
            }
        }

        /** Viền ô con — cùng nguyên tắc [fillOf]. */
        fun strokeOf(t: GroupTone): String = when (t) {
            GroupTone.NEUTRAL -> KachiTheme.LINE
            GroupTone.ACTIVE -> alpha(KachiTheme.ACCENT, "8C")
            GroupTone.WARN -> alpha(KachiTheme.AMBER, "8C")
            GroupTone.ALERT -> alpha(KachiTheme.RED, "8C")
        }

        /** Nền ô con bình thường — cùng giá trị ô nén đang dùng, để nhóm không trông lạ giữa các widget khác. */
        private val CELL_BG: String get() = KachiTheme.CELL

        /** `#RRGGBB` + kênh trong suốt `AA` → `#AARRGGBB` (dạng [android.graphics.Color.parseColor] nhận). */
        private fun alpha(hex: String, aa: String): String = "#$aa${hex.removePrefix("#")}"

        /** TextView một dòng căn giữa — cùng khuôn với [WidgetViews] để nhóm không lệch phông với ô thường. */
        fun text(ctx: Context, s: String, sp: Float, color: String, bold: Boolean = false): TextView =
            TextView(ctx).apply {
                text = s; setTextColor(c(color)); setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
                gravity = Gravity.CENTER
                if (bold) typeface = Typeface.DEFAULT_BOLD
            }
    }
}
