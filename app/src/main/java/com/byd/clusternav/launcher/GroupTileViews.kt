package com.byd.clusternav.launcher

import android.content.Context
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
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
     * Các vùng **nhường chỗ được** (xem [onMeasure]), theo THỨ TỰ nhường: khoảng thở của số chính thẻ CARD ([ReadGrid] trần
     * [Sp.LEAD_ROW], sàn = chỗ số thật cần) TRƯỚC, rồi lưới ô con ([GroupCellGrid] — bớt hàng/dạng, cuối cùng bỏ mục cuối).
     * 3.01 R20: [ĐO] *Chuyến đi* ở khung 6×2 — số chính giữ trọn 162 px trong khi hàng số phụ còn 25 px (bị cắt): khoảng
     * trắng phải nhường trước chữ.
     *
     * Rỗng với nhóm BOARD (bảng tự vẽ, không có lưới) ⇒ ở đó không có gì nhường. Từ U9 pha 2 nhóm BOARD **có thể
     * có nút** (*Cửa & khoang*), nên chỗ cho hàng nút được lo bằng cách khác: thân ô nhận `weight` để `LinearLayout`
     * đo hàng nút — chi phí CỐ ĐỊNH — trước rồi mới chia phần dư cho bảng (xem [bind]).
     */
    private val trimAreas = ArrayList<TrimArea>(2)

    /** Khoảng thở dưới thân (`null` ở bảng BOARD-có-nút: thân ăn `weight`) — [onLayout] chia đôi nó trên/dưới thân. */
    private var breath: View? = null

    /** Mực nhấn của lĩnh vực nhóm (chip icon đầu ô + thanh mức) — đặt ở [bind]. */
    private var accent = 0

    /** R22 — thân HÌNH ([GroupBodies]: hình xe vật-là-nút / bảng icon) thay lưới đọc + hàng nút chữ khi dữ liệu có dạng đó. */
    private var matrix: View? = null

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
        accent = KachiWidgetType.accentOf(CapabilityCatalog.pick(id)?.domain)   // R22: sắc lĩnh vực CHỈ ở chip đầu + thanh mức
        removeAllViews(); binders.clear(); trimAreas.clear()
        bodyHolder.removeAllViews()
        matrix = GroupBodies.of(context, model, data)

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
        // thì bảng chỉ còn một nửa chỗ mà chẳng để làm gì). [onLayout] dời thân xuống NỬA khoảng này (owner 13/10).
        breath = if (boardWithActions) null else View(context).also { addView(it, LayoutParams(MATCH, 0, 1f)) }
        // Hàng nút dựng MỘT LẦN. [refresh] không chạm ⇒ cú bấm không bị cắt giữa chuỗi MotionEvent.
        if (model.hasActions && matrix == null) addView(actionsRow(context, model, data), LayoutParams(MATCH, WRAP))
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
     * Nút phải bấm được ⇒ chi phí CỐ ĐỊNH. Khoảng thở của số chính và dòng dữ liệu thì co được ([TrimArea]). Nên: đo một
     * lượt bình thường, thấy tổng vượt hộp thì bảo vùng nhường ĐẦU TIÊN còn nhường được bớt đúng phần vượt và đo lại.
     * Vòng lặp bị chặn bởi [TrimArea.trimBy] (`false` khi tới sàn) và [MAX_PASSES].
     *
     * ⚠ **PHẢI `resetTrim()` ở đầu mỗi lượt**: không thì lần đo sau (ô rộng ra, đổi bố cục) vẫn dùng phần đã bớt của
     * lần trước ⇒ lưới **co dần một chiều** và không bao giờ trở lại — đúng họ lỗi "trạng thái rò qua các lượt vẽ".
     */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        trimAreas.forEach { it.resetTrim() }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        if (trimAreas.isEmpty() || MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED) return
        val box = MeasureSpec.getSize(heightMeasureSpec) - paddingTop - paddingBottom
        repeat(MAX_PASSES) {
            var need = 0
            for (i in 0 until childCount) need += getChildAt(i).measuredHeight
            // Phần tràn bị cha `WRAP` kẹp mất (KDoc [TrimArea.overflowPx]) cũng là phần phải nhường.
            val excess = maxOf(need - box, trimAreas.sumOf { it.overflowPx })
            if (excess <= 0 || trimAreas.none { it.trimBy(excess) }) return
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }

    /**
     * 3.01 R20 — owner 13/10 *"nhiều cái khi hiện số lên nó nằm chơi vơi lắm"*: [ĐO] thẻ CARD ở ô cao dồn lên đầu ô (Chuyến
     * đi 2×6: 318 px trống dưới; Pin 4×6: 553 px). Thân dời xuống NỬA khoảng thở ⇒ cân giữa đầu ô và hàng nút/đáy ô.
     * Chỉ dời lúc đặt, KHÔNG đổi phép đo: thêm `Space`/`View` `weight` TRƯỚC thân thì `LinearLayout` đo thân với
     * `heightUsed = 0` (đã có `weight` đứng trước) ⇒ [ĐO] bảng lốp nhận trọn khung, tràn, rơi về ô tóm tắt.
     */
    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        val half = (breath?.height ?: 0) / 2
        if (half > 0) bodyHolder.offsetTopAndBottom(half)
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
        (matrix as? GroupBody)?.refresh(model)
        trimAreas.forEach { (it as? GroupCellGrid)?.recheck() }   // 3.01 R20: cột bề rộng tĩnh ⇒ tự xin đo khi định dạng số đổi
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
        // R22 (owner 13/10 "thô"; brief Kim §2.5): icon trong chip 28 dp tô sắc lĩnh vực + tiêu đề medium mực đầy.
        if (r != 0) addView(
            ImageView(context).apply { setImageResource(r); KachiWidgetType.chipIcon(this, accent) },
            LayoutParams(dpi(context, Sp.HEAD_CHIP), dpi(context, Sp.HEAD_CHIP))
                .also { it.marginEnd = dpi(context, Sp.S) },
        )
        addView(
            // ⚠ [T1] Vai màu ở đây GIỮ NGUYÊN `MUT2` — và việc giữ nguyên là một kết luận có đo, không phải bỏ qua.
            // Lượt đo đầu của tôi báo nhãn này chỉ **4.12:1** trên bảng sáng và tôi đã đổi sang `INK2`. Nhưng phép đo
            // đó SAI: nó lấy trung vị mực trên **hộp a11y rộng 1158px** trong khi chữ "Lốp" chỉ chiếm **31px**, nên
            // phân vị rơi vào viền khử răng cưa chứ không vào lòng nét. Cắt về đúng vùng có mực thì `MUT2` đo được
            // **5.67:1** — ĐẠT. Đã hoàn nguyên: không đổi thẩm mỹ một màn owner đã duyệt dựa trên một số đo đã bị bác.
            text(context, m.label, KachiWidgetType.TITLE_SP, KachiTheme.INK2).apply {
                KachiWidgetType.title(this); gravity = Gravity.START or Gravity.CENTER_VERTICAL
                maxLines = 1; ellipsize = TextUtils.TruncateAt.END
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
        val mx = matrix
        val body = if (mx != null) LinearLayout(context).apply {
            orientation = VERTICAL   // ô XEM đã gộp vào hàng của vật thì không vẽ lần hai; còn lại giữ lưới đọc ở trên
            val rest = m.cells.filter { it.id !in (mx as GroupBody).consumed }
            if (rest.isNotEmpty()) addView(gridRows(rest, MAX_PER_ROW, withIcon = false), LayoutParams(MATCH, WRAP))
            addView(mx, LayoutParams(MATCH, WRAP))
        } else when (m.shape) {
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
        // 3.01 R20: trần [Sp.LEAD_ROW] qua [ReadGrid] (nhường khoảng trắng trước khi lưới số phụ phải bỏ mục).
        m.lead?.let { lead ->
            addView(ReadGrid(context, dpi(context, Sp.LEAD_ROW)).apply {
                trimAreas += this
                addView(leadRow(lead), LayoutParams(MATCH, MATCH))
            }, LayoutParams(MATCH, WRAP))
        }
        // `withIcon = false` — xem KDoc [stripCell]: thẻ CARD không đủ bề cao cho icon + nhãn + số.
        addView(gridRows(m.rest, CARD_PER_ROW, withIcon = false), LayoutParams(MATCH, WRAP))
    }

    /**
     * Lưới ô con — 3.01 R20: [GroupCellGrid] chọn số cột (≤ [perRow]), dạng ngang/dọc, số dòng nhãn và số mục hiện theo
     * BỀ RỘNG đo được ([GroupCellFit] ở `:core`). Trần [perRow] giữ nguyên ⇒ ô rộng y như cũ (R21); hàng chia đều như
     * [rowsOf] (hàng ngắn canh giữa — R22, cùng bề rộng ô với hàng đủ). Cao: trần = số hàng × [Sp.READ_ROW] (kiểm toán mục 3).
     */
    private fun gridRows(cells: List<GroupCell>, perRow: Int, withIcon: Boolean): View =
        GroupCellGrid(context, dpi(context, Sp.READ_ROW), perRow, withIcon, xs(), dpi(context, Sp.S)).apply {
            trimAreas += this
            cells.forEach { add(stripCell(it, withIcon)) }
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
    private fun stripCell(cell: GroupCell, withIcon: Boolean): GroupCellGrid.Cell {
        val icon = KachiTheme.iconRes(cell.icon).takeIf { withIcon && it != 0 }?.let { res ->
            ImageView(context).apply { setImageResource(res) }
        }
        // ⚠ [KIỂM TOÁN UX mục 3] GIÁ TRỊ là thứ TO NHẤT trong ô con (13sp → 17sp): người mở ô nhóm ra để xem SỐ,
        // còn nhãn chỉ để biết số đó là của cái gì. Trước đây số 13sp / nhãn 9.5sp — cùng bậc, nên mắt không có chỗ
        // để dừng. Đơn vị đã nằm ngay trong [GroupCell.value] ("2.4 bar") nên nó đi cùng số, không xuống caption.
        val value = text(context, cell.value, KachiWidgetType.VALUE_SP, tintOf(cell.tone), bold = true).apply {
            maxLines = 1; ellipsize = TextUtils.TruncateAt.END
        }
        // ⚠⚠ [KIỂM TOÁN UX mục 2] Nhãn dùng [KachiTheme.MUT] (147,160,180) — KHÔNG dùng `MUT2` (139,149,167) nữa —
        // và cỡ 9.5sp → 12sp (nét ~10px → ~13px). [ĐO] nhãn cũ ra **2.33:1** trên nền ô con, dưới xa mức đọc được
        // 4.5:1; phần lớn độ tối đến từ việc CẢ Ô bị làm mờ 50% (đã sửa ở [CellBinder]), phần còn lại từ chính mã
        // màu. MUT trên nền ô con cho **5.9:1** ⇒ đọc được cả khi off-car.
        val labelView = text(context, cell.label, KachiWidgetType.LABEL_SP, KachiTheme.MUT).apply {
            KachiWidgetType.caption(this)
            // ⚠⚠ HAI DÒNG, không phải một — đây là **lỗi tôi tự gây ra khi nới cỡ nhãn** và tìm ra bằng ảnh chụp.
            // [ĐO] nhãn 13.5sp trong ô con rộng ~130px (nhóm Kính ở khung nhỏ) bị cắt thành `"Kính trư…"` cho CẢ
            // trước-trái LẪN trước-phải, và `"Kính sau…"` cho cả hai bánh sau ⇒ bốn ô thành hai cặp **giống hệt
            // nhau**. Đó đúng lỗi 18-nhãn-trùng mà `displayLabel`/`shortLabel` của RW0 sinh ra để chữa: nới cỡ chữ
            // mà không nới chỗ chứa là đổi một lỗi đọc-được thành một lỗi phân-biệt-được.
            // Hai dòng thì `"Kính trước-trái"` ngắt ở dấu cách thành `Kính` / `trước-trái` — đọc đủ, và dòng dữ liệu
            // cao 108px vẫn chứa được (2 dòng nhãn 40px + số 28px + lề 8px = 76px).
            maxLines = 2; ellipsize = TextUtils.TruncateAt.END
        }
        // 3.01 R20: hướng xếp + LayoutParams nhãn/số do [GroupCellGrid] đặt theo kế hoạch đo được (ngang nhãn·số khi đủ
        // chỗ, dọc nhãn trên số dưới khi hẹp). Ở đây chỉ dựng đủ con, đúng thứ tự icon · nhãn · số.
        val root = LinearLayout(context).apply {
            gravity = Gravity.CENTER
            val p = xs(); setPadding(p, p, p, p)
            if (icon != null) addView(
                icon,
                LayoutParams(dpi(context, Sp.ICON_XS), dpi(context, Sp.ICON_XS)).also { it.bottomMargin = dpi(context, Sp.XS) },
            )
            addView(labelView, LayoutParams(0, WRAP, 1f))
            addView(value, LayoutParams(WRAP, WRAP))
        }
        CellBinder(root, value, icon).also { binders[cell.id] = it; it.bind(cell) }
        return GroupCellGrid.Cell(root, icon, labelView, value)
    }

    /**
     * Số chính: số to nét MẢNH + đơn vị nhỏ lùi màu + nhãn ngắn dưới (R22 · brief Kim §2.4) + thanh mức mảnh khi đại lượng
     * CÓ TRẦN ([KachiMeter.bounded] — đơn vị `%`: pin, SOH…). Luật chọn thanh theo DỮ LIỆU, không theo mã nhóm.
     */
    private fun leadRow(cell: GroupCell): View {
        val big = text(context, cell.number, KachiWidgetType.HERO_SP, tintOf(cell.tone)).apply {
            maxLines = 1; KachiWidgetType.hero(this, tintOf(cell.tone))
        }
        val meter = if (KachiMeter.bounded(cell.unit)) KachiMeter(context).apply { setColors(accent) } else null
        val root = LinearLayout(context).apply {
            orientation = VERTICAL; gravity = Gravity.CENTER
            // [AxisRow]: cụm số chính + đơn vị cân giữa trục ô, cùng tâm nhãn dưới nó (owner 13/10 — KDoc AxisRow).
            addView(AxisRow(context).apply {
                addView(big)
                if (cell.unit.isNotEmpty()) addView(
                    text(context, " ${cell.unit}", KachiWidgetType.UNIT_SP, KachiTheme.MUT).apply {
                        KachiWidgetType.unit(this); setPadding(0, dpi(context, Sp.M), 0, 0)
                    },
                )
            })
            addView(text(context, cell.label, KachiType.CAPTION, KachiTheme.MUT).apply { KachiWidgetType.caption(this) })
            meter?.let { addView(it, LayoutParams(MATCH, WRAP).also { lp -> lp.topMargin = dpi(context, Sp.S) }) }
        }
        // Số chính CŨNG phải đổi tại chỗ ⇒ đăng ký bộ nối như mọi ô con khác. Thiếu bước này thì mọi số phụ chạy
        // trong khi số to đứng im — sai theo cách rất dễ tưởng là "xe chưa trả dữ liệu".
        CellBinder(root, big, null, numberOnly = true, meter = meter).also { binders[cell.id] = it; it.bind(cell) }
        return root
    }

    /** [Sp.XS] quy ra pixel — khe/lề nhỏ nhất của lưới ô con, gọi nhiều lần nên tách cho gọn. */
    private fun xs(): Int = dpi(context, Sp.XS)

    companion object {
        private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

        /** Ô con tối đa mỗi hàng của dải STRIP — 5 vì nhóm dài nhất có 10 thành viên (⇒ 2 hàng đều). */
        private const val MAX_PER_ROW = 5

        /** Số lượt đo tối đa của [onMeasure] (1 lượt thường + mỗi vùng nhường ≤ 2 lượt). */
        private const val MAX_PASSES = 4

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
            // 3.01: cùng phép chia của [GridFit.rowCounts] `BALANCED` (một luật cho lưới ô con, hàng nút và lưới widget).
            var at = 0
            return GridFit.rowCounts(items.size, max, GridFit.RowSplit.BALANCED).map { n -> ArrayList(items.subList(at, at + n)).also { at += n } }
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
            // R22 (owner 13/10: `0 %` trắng cạnh `35 %` xanh — "rối"): MỘT luật cho mọi số — mực trung tính; "đang bật/mở"
            // nói bằng nút sáng nền nhấn + mức đầy (thứ có nghĩa với nó), chỉ CẢNH BÁO mới đổi màu chữ.
            GroupTone.NEUTRAL, GroupTone.ACTIVE -> KachiTheme.INK
            GroupTone.WARN -> KachiTheme.AMBER
            GroupTone.ALERT -> KachiTheme.RED
        }

        /**
         * Nền ô con theo trạng thái — suy từ CHÍNH màu [KachiTheme] + kênh trong suốt (không khai mã màu mới).
         *
         * ## ⚠⚠ WP1 · R1.1 — MÀU NGỮ NGHĨA chuyển hẳn sang NỀN (viền `strokeOf` đã xoá)
         * Owner gỡ hết viền, riêng màu ngữ nghĩa thì *"chuyển sang nền/chữ màu"*. WARN/ALERT trước nói ra bằng **hai**
         * thứ: nền 15% (`26`) + viền cùng màu 55% (`8C`). Giữ 15% mà bỏ viền thì tín hiệu tụt còn [ĐO] 1.38×/1.25× —
         * sát ngưỡng nhìn-ra-được, tức một cảnh báo trên màn lái xe **mờ đi** vì một lượt dọn thẩm mỹ. Nền nay 35%
         * (`59`): [ĐO] 2.25×/1.99× (tối) · 1.74×/1.83× (sáng) — đậm hơn cả cặp nền+viền cũ, 0 đường kẻ.
         */
        fun fillOf(t: GroupTone): String = when (t) {
            GroupTone.NEUTRAL -> CELL_BG
            GroupTone.ACTIVE -> alpha(KachiTheme.ACCENT, "26")
            GroupTone.WARN -> alpha(KachiTheme.AMBER, SEMANTIC_ALPHA)
            GroupTone.ALERT -> alpha(KachiTheme.RED, SEMANTIC_ALPHA)
        }

        /**
         * Độ đục nền màu-ngữ-nghĩa (`0x59` = 35 %) — **gánh một mình** việc mà nền 15 % + viền 55 % từng chia nhau
         * (xem [fillOf]). Hạ là làm mờ cảnh báo; nâng quá thì mực tụt dưới 4.5:1 (`ThemePaletteContractTest` là rào).
         */
        private const val SEMANTIC_ALPHA = "59"

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
                GroupTone.WARN, GroupTone.ALERT ->   // R29: nền 35 % = MẶT ⇒ chữ trong ô con giải trên chính nền cảnh báo
                    KachiGlass.plain(view, KachiTheme.card(view.context, radius, fillOf(t))).also { AdaptiveInk.fill(view, fades = false, variant = t) { intArrayOf(KachiTheme.c(fillOf(t))) } }
            }
        }

        // ⚠ WP1 · R1.1 — `strokeOf(GroupTone)` đã XOÁ (0 chỗ gọi). Nó là bảng tra VIỀN, tức chính thứ owner bỏ; và
        // giữ một bảng tra không ai gọi là mời nó mọc lại. Màu ngữ nghĩa nay nằm trong [fillOf] (xem KDoc ở đó).

        /** Nền ô con bình thường — cùng giá trị ô nén đang dùng, để nhóm không trông lạ giữa các widget khác. */
        private val CELL_BG: String get() = KachiTheme.CELL

        /** `#RRGGBB` + kênh trong suốt `AA` → `#AARRGGBB` (dạng [android.graphics.Color.parseColor] nhận). */
        private fun alpha(hex: String, aa: String): String = "#$aa${hex.removePrefix("#")}"

        /** TextView một dòng căn giữa — cùng khuôn với [WidgetViews] để nhóm không lệch phông với ô thường. */
        fun text(ctx: Context, s: String, sp: Float, color: String, bold: Boolean = false): TextView =
            TextView(ctx).apply {
                text = s; AdaptiveInk.text(this, color); setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)   // R29 T29-5
                gravity = Gravity.CENTER
                if (bold) KachiWidgetType.value(this)   // R22: số = medium + tnum, KHÔNG còn DEFAULT_BOLD (brief Kim §2.4)
            }
    }
}
