package com.byd.clusternav.launcher

import android.content.Context
import android.graphics.drawable.Drawable
import android.graphics.Rect
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.byd.clusternav.R
import com.byd.clusternav.launcher.KachiTheme.c
import com.byd.clusternav.launcher.KachiTheme.dpi
import com.byd.clusternav.launcher.KachiSpace as Sp

/**
 * Một mục của khuôn **Chọn** ([KachiDialogs.pick]).
 *
 * @param current mục ĐANG DÙNG — thẻ tô [SurfaceTone.ACTIVE] + dấu "✓ Đang dùng" (R9: mục đang dùng nổi bật).
 * @param icon vector drawable (tô [KachiTheme.INK]); @param drawable ảnh có màu riêng (icon app — không tô);
 * @param initial chữ cái đầu trong ô tròn gradient (ảnh đại diện hồ sơ, R10). Cả ba trống ⇒ không ô đầu.
 */
internal data class PickItem(
    val label: CharSequence,
    val subtitle: CharSequence? = null,
    val current: Boolean = false,
    val icon: Int = 0,
    val drawable: Drawable? = null,
    val initial: String? = null,
)

/**
 * ═══ KACHI-301 R9 — BỐN KHUÔN hộp thoại, dựng trên [KachiDialog] ════════════════════════════════════════════
 *
 * Mỗi hộp thoại sống thuộc ĐÚNG một khuôn (bảng spec §4.3): **Chọn** ([pick]) · **Biểu mẫu** ([form] + [field]) ·
 * **Xác nhận** ([confirm]) · **Thông báo** ([notice]). Khuôn giữ ba quyết định chung một chỗ — nhãn nút huỷ, nút
 * nào bên nào, nút chính khoá khi nào — để hộp thứ hai trở đi không lệch (bẫy hai-bản-sao, KDoc [SettingsDialogs]).
 * Mọi hàm trả [KachiDialog.Handle] hoặc `null` khi hộp không mở được (Activity đã đóng).
 */
internal object KachiDialogs {

    /** Từ bao nhiêu mục thì lưới 2 cột (R10 — hồ sơ ≥ 4). Chỗ gọi đặt `columns = 2` tường minh mới áp. */
    const val GRID_MIN_ITEMS = 4

    /**
     * Khuôn **CHỌN**: danh sách thẻ; chạm một thẻ ⇒ hộp đóng TRƯỚC rồi mới gọi [onPick] (chỉ số) — để hộp mà
     * [onPick] mở tiếp (chuỗi gán phím) nằm trên một màn sạch.
     *
     * @param columns 2 ⇒ lưới hai cột khi có ≥ [GRID_MIN_ITEMS] mục; ít hơn vẫn một cột (hai thẻ lẻ trông như lỗi).
     * @param linkLabel/onLink lối tắt ở chân trái (vd *"Quản lý hồ sơ…"*). @param emptyText danh sách rỗng ⇒ NÓI
     *   lý do bằng [notice] thay vì một hộp trắng.
     */
    fun pick(
        context: Context,
        title: CharSequence,
        items: List<PickItem>,
        subtitle: CharSequence? = null,
        icon: Int = 0,
        columns: Int = 1,
        emptyText: CharSequence? = null,
        linkLabel: CharSequence? = null,
        onLink: (() -> Unit)? = null,
        onPick: (Int) -> Unit,
    ): KachiDialog.Handle? {
        if (items.isEmpty() && emptyText != null) return notice(context, title, emptyText, icon = icon)
        val d = KachiDialog(context).title(title).subtitle(subtitle).icon(icon)
            .cancel(context.getString(R.string.kdlg_close))
        var handle: KachiDialog.Handle? = null
        cards(context, items, columns) { i ->
            handle?.dismiss()
            onPick(i)
        }.forEach { d.body(it) }
        if (linkLabel != null && onLink != null) d.link(linkLabel) { onLink() }
        handle = d.show()
        return handle
    }

    /**
     * Thân của khuôn Chọn: thẻ theo [items], một cột hoặc lưới [GridPlan] (khi `columns >= 2` và ≥ [GRID_MIN_ITEMS]).
     * Có thẻ mang ô đầu (icon/ảnh/chữ cái) thì thẻ KHÔNG ô đầu vẫn chừa chỗ ô ⇒ chữ mọi thẻ thẳng một lề (QA-301 P3: bước 2
     * gán phím lệch x=564 vs 630).
     */
    private fun cards(context: Context, items: List<PickItem>, columns: Int, onPick: (Int) -> Unit): List<View> {
        val reserve = items.any { it.drawable != null || it.icon != 0 || !it.initial.isNullOrEmpty() }
        val views = items.mapIndexed { i, item -> pickCard(context, item, reserve) { onPick(i) } }
        val perRow = if (columns >= 2 && items.size >= GRID_MIN_ITEMS) 2 else 1
        if (perRow == 1) return views
        return GridPlan.rows(views.size, perRow).map { row -> gridRow(context, row.map { cell -> cell.index?.let(views::get) to cell }) }
    }

    /**
     * ═══ QA-301 D3 — hộp CHỌN NHIỀU BƯỚC (spec §4.3 #8: *"một hộp có thanh bước 1-2-3 + nút ←"*) ═══════════════════
     *
     * MỘT cửa sổ, thân đổi tại chỗ mỗi bước ([KachiDialog.Handle.setBody]) — không còn ba hộp chồng nhau (Back ở bước 3
     * đóng cả chuỗi, tiêu đề lặp "Bước n" hai lần). Thanh bước ghim trên thân ([StepBar], chỉ số, không chữ "Bước n" —
     * câu đó là `contentDescription` cho đọc màn hình). *"← Quay lại"* (chân trái) và phím Back ở bước > 1 lùi một bước;
     * Back ở bước 1 / *Huỷ* đóng hộp. Huỷ ở bước nào cũng KHÔNG ghi gì: việc thật chỉ chạy trong [Step.onPick] của bước cuối.
     *
     * @param first bước 1. [Step.onPick] trả bước KẾ, hoặc `null` = xong (hộp đóng).
     * @param describe câu đọc cho thanh bước (vd *"Bước 2/3"*).
     */
    fun steps(context: Context, total: Int, first: Step, describe: (Int, Int) -> CharSequence): KachiDialog.Handle? {
        val trail = arrayListOf(first)
        val bar = StepBar(context, total)
        var handle: KachiDialog.Handle? = null
        lateinit var render: () -> Unit
        fun body(step: Step): List<View> =
            if (step.items.isEmpty()) listOf(KachiDialog.messageView(context, step.emptyText))
            else cards(context, step.items, columns = 1) { i ->
                val next = step.onPick(i)
                if (next == null) handle?.dismiss() else { trail += next; render() }
            }
        fun back(): Boolean {
            if (trail.size <= 1) return false
            trail.removeAt(trail.lastIndex)
            render()
            return true
        }
        render = {
            val step = trail.last()
            bar.show(trail.size, describe(trail.size, total))
            handle?.setTitle(step.title)
            handle?.setBody(body(step))
            handle?.setLinkVisible(trail.size > 1)
        }
        val d = KachiDialog(context).title(first.title).pinned(bar.view)
            .link(context.getString(R.string.kdlg_back), autoDismiss = false) { back() }
            .onBack { back() }
        body(first).forEach { d.body(it) }
        bar.show(1, describe(1, total))
        handle = d.show()
        handle?.setLinkVisible(false)
        return handle
    }

    /** Một bước của [steps]: tiêu đề · thẻ · câu khi rỗng · [onPick] trả bước kế (`null` = xong). */
    class Step(
        val title: CharSequence,
        val items: List<PickItem>,
        val emptyText: CharSequence,
        val onPick: (Int) -> Step?,
    )

    /** Thanh bước 1-2-3: ô đã qua = bề mặt BẬT, ô đang ở = gradient, ô chưa tới = [KachiDialogSupport.controlFill]. */
    private class StepBar(context: Context, total: Int) {
        private val dots = List(total) { i ->
            TextView(context).apply {
                text = (i + 1).toString()
                gravity = Gravity.CENTER
                KachiType.apply(this, KachiType.CAPTION, bold = true)
            }
        }
        val view: View = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            dots.forEachIndexed { i, dot ->
                addView(dot, LinearLayout.LayoutParams(dpi(context, Sp.ICON_M), dpi(context, Sp.ICON_M)).apply {
                    if (i > 0) marginStart = dpi(context, Sp.S)
                })
            }
        }

        fun show(step: Int, said: CharSequence) {
            view.contentDescription = said
            dots.forEachIndexed { i, dot ->
                val n = i + 1
                dot.setTextColor(c(if (n == step) KachiTheme.ON_ACCENT else if (n < step) KachiTheme.INK else KachiTheme.MUT))
                dot.background = when {
                    n == step -> KachiTheme.gradient(dot.context, Sp.RADIUS_PILL)
                    n < step -> KachiTheme.surface(dot.context, Sp.RADIUS_PILL, SurfaceTone.ACTIVE)
                    else -> KachiTheme.pill(dot.context, KachiDialogSupport.controlFill())
                }
            }
        }
    }

    /**
     * Khuôn **BIỂU MẪU**: chỗ gọi đưa [body] (thường là các [field]) + [validate]. Nút chính KHOÁ tới khi [validate]
     * trả `true` (chạy lúc mở và sau mỗi lần chữ đổi — R9 *"nút chính khoá tới khi hợp lệ"*); [validate] tự đặt lỗi
     * TẠI CHỖ qua [Field.error]. [dangerLabel]/[onDanger] = nút đỏ bên trái (Xoá mục đang sửa).
     */
    fun form(
        context: Context,
        title: CharSequence,
        body: List<View>,
        primaryLabel: CharSequence,
        subtitle: CharSequence? = null,
        icon: Int = 0,
        validate: (() -> Boolean)? = null,
        focus: EditText? = null,
        showKeyboard: Boolean = false,
        dangerLabel: CharSequence? = null,
        onDanger: (() -> Unit)? = null,
        onOk: () -> Unit,
    ): KachiDialog.Handle? {
        val d = KachiDialog(context).title(title).subtitle(subtitle).icon(icon)
        body.forEach { d.body(it) }
        validate?.let { d.validate(it) }
        focus?.let { d.focus(it, showKeyboard) }
        if (dangerLabel != null && onDanger != null) d.danger(dangerLabel) { onDanger() }
        return d.form().primary(primaryLabel) { onOk() }.show()
    }

    /**
     * Khuôn **XÁC NHẬN**. [destructive] = việc KHÔNG hoàn lại được ⇒ ô icon + nút chính đỏ, và [message] phải nói
     * rõ MẤT GÌ (R9). Nhãn nút chính nói đúng việc ([confirmLabel]), không bao giờ là "OK".
     */
    fun confirm(
        context: Context,
        title: CharSequence,
        message: CharSequence,
        confirmLabel: CharSequence,
        destructive: Boolean = false,
        cancelLabel: CharSequence? = null,
        icon: Int = 0,
        onConfirm: () -> Unit,
    ): KachiDialog.Handle? {
        val d = KachiDialog(context).title(title).icon(icon).cancel(cancelLabel)
        if (destructive) d.tone(KachiDialog.Tone.DESTRUCTIVE).also { if (icon == 0) it.glyph(GLYPH_WARN) }
        if (message.isNotBlank()) d.message(message)
        return d.primary(confirmLabel) { onConfirm() }.show()
    }

    /**
     * Khuôn **THÔNG BÁO**: nói một điều. Không [actionLabel] ⇒ đúng một nút "OK". Có ⇒ nút chính = hành động,
     * nút phụ = "Đóng" (vd *"Đã nhập hồ sơ — Dùng ngay"*).
     */
    fun notice(
        context: Context,
        title: CharSequence,
        message: CharSequence,
        icon: Int = 0,
        actionLabel: CharSequence? = null,
        onAction: (() -> Unit)? = null,
    ): KachiDialog.Handle? {
        val d = KachiDialog(context).title(title).icon(icon)
        if (message.isNotBlank()) d.message(message)
        if (actionLabel != null && onAction != null) {
            d.cancel(context.getString(R.string.kdlg_close)).primary(actionLabel) { onAction() }
        } else {
            d.noCancel().primary(context.getString(R.string.kdlg_ok)) {}
        }
        return d.show()
    }

    /**
     * Ô nhập của khuôn Biểu mẫu: **nhãn TRÊN ô** ([KachiType.CAPTION] đậm, [KachiTheme.MUT]) · ô lõm
     * [SurfaceTone.SUNKEN] · dòng lỗi đỏ ẩn sẵn. [Field.error] khác `null` ⇒ ô tô [KachiTheme.RED_SOFT] + câu lỗi
     * hiện NGAY dưới ô (R9 *"lỗi tại chỗ"*) — không viền (WP1 · R1.1), phân biệt chỉ bằng fill.
     */
    class Field internal constructor(val view: View, val edit: EditText, private val err: TextView) {
        val text: String get() = edit.text.toString()
        var error: CharSequence? = null
            set(value) {
                if (field == value) return
                field = value
                err.text = value
                err.visibility = if (value.isNullOrEmpty()) View.GONE else View.VISIBLE
                edit.background = if (value.isNullOrEmpty()) KachiTheme.surface(edit.context, Sp.RADIUS_M, SurfaceTone.SUNKEN)
                else KachiTheme.card(edit.context, Sp.RADIUS_M, KachiTheme.RED_SOFT)
                // QA-301 (P3): bàn phím đang mở thì câu lỗi nằm ngay dưới ô bị cắt (thân chỉ cuộn tới Ô có tiêu điểm) ⇒
                // xin thân cuộn cho thấy CẢ khối ô + lỗi.
                if (!value.isNullOrEmpty() && edit.hasFocus()) view.post {
                    view.requestRectangleOnScreen(Rect(0, 0, view.width, view.height), false)
                }
            }

        /**
         * Người dùng VỪA chọn gõ (chip *Khác*, nút *Gõ tên…*) ⇒ tiêu điểm + bàn phím ngay — QA-301: chỉ `requestFocus`
         * thì ô sáng con trỏ mà bàn phím không lên (`mInputShown=false`), phải chạm lần nữa.
         */
        fun requestInput() {
            edit.requestFocus()
            edit.post {
                edit.context.getSystemService(InputMethodManager::class.java)?.showSoftInput(edit, InputMethodManager.SHOW_IMPLICIT)
            }
        }
    }

    fun field(
        context: Context,
        label: CharSequence?,
        value: String = "",
        hint: CharSequence? = null,
        inputType: Int = InputType.TYPE_CLASS_TEXT,
        selectAll: Boolean = false,
    ): Field {
        val edit = EditText(context).apply {
            setText(value)
            this.hint = hint
            this.inputType = inputType
            setTextColor(c(KachiTheme.INK))
            setHintTextColor(c(KachiTheme.MUT))
            KachiType.apply(this, KachiType.BODY)
            minHeight = dpi(context, Sp.TOUCH)
            setPadding(dpi(context, Sp.M), dpi(context, Sp.S), dpi(context, Sp.M), dpi(context, Sp.S))
            background = KachiTheme.surface(context, Sp.RADIUS_M, SurfaceTone.SUNKEN)
            if (selectAll) setSelection(0, value.length)
        }
        val err = TextView(context).apply {
            setTextColor(c(KachiTheme.RED))
            KachiType.apply(this, KachiType.CAPTION)
            visibility = View.GONE
        }
        val col = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            label?.let { addView(caption(context, it, bold = true)) }
            addView(edit, KachiDialog.stackLp(context, top = if (label == null) 0 else Sp.XS))
            addView(err, KachiDialog.stackLp(context, top = Sp.XS))
        }
        return Field(col, edit, err)
    }

    /** Chú thích dưới/trên ô ([KachiType.CAPTION], [KachiTheme.MUT]) — dùng chung cho biểu mẫu. */
    fun caption(context: Context, text: CharSequence, bold: Boolean = false) = TextView(context).apply {
        this.text = text
        setTextColor(c(KachiTheme.MUT))
        KachiType.apply(this, KachiType.CAPTION, bold = bold)
    }

    // ── thẻ của khuôn Chọn ───────────────────────────────────────────────────────────────────────────────

    private fun pickCard(context: Context, item: PickItem, reserveLead: Boolean, onTap: () -> Unit): View = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = dpi(context, Sp.TOUCH)
        setPadding(dpi(context, Sp.L), dpi(context, Sp.M), dpi(context, Sp.L), dpi(context, Sp.M))
        background = if (item.current) KachiTheme.surface(context, Sp.RADIUS_L, SurfaceTone.ACTIVE)
        else KachiTheme.card(context, Sp.RADIUS_L, KachiDialogSupport.controlFill())
        isClickable = true
        contentDescription = item.label
        setOnClickListener { onTap() }
        (leading(context, item) ?: if (reserveLead) View(context) else null)?.let { lead ->
            addView(lead, LinearLayout.LayoutParams(dpi(context, Sp.ICON_L), dpi(context, Sp.ICON_L)).apply {
                marginEnd = dpi(context, Sp.M)
            })
        }
        // Dấu "✓ Đang dùng" nằm CÙNG HÀNG nhãn (không phải cột phải của cả thẻ): dòng phụ được trọn bề ngang ⇒ không xuống
        // dòng, thẻ đang dùng cao bằng thẻ khác trong lưới (QA-301 P3: 73 dp vs 59 dp).
        val labelRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(TextView(context).apply {
                text = item.label
                maxLines = 2
                setTextColor(c(KachiTheme.INK))
                KachiType.apply(this, KachiType.BODY, bold = item.current)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            if (item.current) addView(TextView(context).apply {
                text = context.getString(R.string.kdlg_current)
                maxLines = 1
                setTextColor(c(KachiTheme.ACCENT_INK))
                KachiType.apply(this, KachiType.CAPTION, bold = true)
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginStart = dpi(context, Sp.S)
            })
        }
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(labelRow)
            item.subtitle?.takeIf { it.isNotBlank() }?.let { addView(caption(context, it)) }
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
    }

    private fun leading(context: Context, item: PickItem): View? = when {
        item.drawable != null -> ImageView(context).apply {
            setImageDrawable(item.drawable)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        item.icon != 0 -> ImageView(context).apply {
            setImageResource(item.icon)
            setColorFilter(c(KachiTheme.INK))
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        !item.initial.isNullOrEmpty() -> TextView(context).apply {
            text = item.initial
            gravity = Gravity.CENTER
            setTextColor(c(KachiTheme.ON_ACCENT))
            KachiType.apply(this, KachiType.SECTION, bold = true)
            background = KachiTheme.gradient(context, Sp.RADIUS_PILL)
        }
        else -> null
    }

    /** Một hàng lưới: mỗi ô theo [GridPlan.Cell] (thẻ thật, hoặc ô đệm khi `index == null`). */
    private fun gridRow(context: Context, cells: List<Pair<View?, GridPlan.Cell>>): View = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        cells.forEachIndexed { i, (card, cell) ->
            addView(card ?: View(context), LinearLayout.LayoutParams(0, cell.height, 1f).apply {
                if (i > 0) marginStart = dpi(context, Sp.S)
            })
        }
    }

    /**
     * ═══ QA-301 D1 [P1] — KẾ HOẠCH LƯỚI THUẦN (test off-device được) ═══════════════════════════════════════════════
     *
     * Lỗi: 5 hồ sơ ⇒ hộp chỉ hiện 4 thẻ — hàng cuối (1 thẻ + 1 ô đệm) cao 0. Bản cũ đặt thẻ `MATCH_PARENT` nhưng ô đệm cao
     * `0`; hàng nằm trong `ScrollView` (đo chiều cao UNSPECIFIED). [SUY — đọc mã `LinearLayout.measureHorizontal`, QA đã
     * chụp hiện tượng] khi KHÔNG phải mọi con đều `MATCH_PARENT` thì hàng lấy cao "phương án" — con `MATCH_PARENT` chỉ góp
     * lề (0), ô đệm góp 0 ⇒ hàng 0. Luật khoá ở đây: MỌI ô của MỌI hàng (thẻ lẫn ô đệm) cùng một [Cell.height] =
     * `MATCH_PARENT` ⇒ hàng lẻ đo như hàng chẵn (cao = thẻ cao nhất). Re-QA máy ảo chụp 5/7 hồ sơ để chốt.
     */
    object GridPlan {
        /** Một ô: [index] = thẻ thứ mấy, `null` = ô đệm của hàng lẻ; [height] = chiều cao `LayoutParams` của ô. */
        data class Cell(val index: Int?, val height: Int)

        /** Chia [count] thẻ thành hàng [perRow] ô; hàng cuối thiếu thì bù ô đệm CÙNG chiều cao. */
        fun rows(count: Int, perRow: Int): List<List<Cell>> = (0 until count).chunked(perRow).map { row ->
            row.map { Cell(it, CELL_HEIGHT) } + List(perRow - row.size) { Cell(null, CELL_HEIGHT) }
        }

        /** Mọi ô (cả ô đệm) cao `MATCH_PARENT` — xem KDoc [GridPlan]. */
        const val CELL_HEIGHT = ViewGroup.LayoutParams.MATCH_PARENT
    }

    /** Ký tự ô icon của hộp Xác nhận phá huỷ khi chỗ gọi không đưa icon — ký hiệu, không phải chữ cần dịch. */
    private const val GLYPH_WARN = "!"
}
