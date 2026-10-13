package com.byd.clusternav.launcher

import android.annotation.SuppressLint
import android.content.Context
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.byd.clusternav.launcher.KachiTheme.c
import com.byd.clusternav.launcher.KachiTheme.dpi
import com.byd.clusternav.launcher.KachiSpace as Sp

/**
 * ═══ 3.01 R22 — Ô NHÓM có bảng VẬT × VIỆC ([ActionMatrix]): chọn dạng theo CHỖ ĐO ĐƯỢC ═══════════════════════════
 *
 * Owner 13/10 (ảnh nhóm *Kính*): *"rối mà"* → *"visual thiết kế, icon, bố cục, gom nhóm"* → (R22·B, ảnh danh sách gọn)
 * *"như này cũng chỉ là show thông tin, cả mớ số 100% 50% nhìn rối"*. ⇒ dạng CHÍNH ở MỌI cỡ là **hình xe mà chính vật là
 * nút** ([ActionMatrixCompact]); bản hình xe có thẻ + nút chữ cạnh từng cửa đã gỡ. **Danh sách gọn** ([ActionMatrixList])
 * chỉ còn là lưới an toàn khi ô dưới ngưỡng hình xe; dưới nữa khung ngoài lùi về ô tóm tắt (đường cũ).
 *
 * Cả hai dạng dựng MỘT lần ở [bind] (nút không bao giờ tháo/gắn khi làm mới — SOÁT P1-1); đổi dạng chỉ đổi `visibility`
 * trong lượt đo. Số đổi tại chỗ qua [refresh].
 */
@SuppressLint("ViewConstructor")
internal class ActionMatrixView(context: Context, mx: ActionMatrix, data: WidgetData) : ViewGroup(context), GroupBody {

    override val consumed: Set<String> = mx.mergedCellIds

    private val kit = MatrixKit(context, data)
    private val compact: ActionMatrixCompact? = if (mx.boardable) ActionMatrixCompact(context, mx, kit) else null
    private val list = ActionMatrixList(context, mx, kit)

    /** Dạng đang hiện — để đo/hỏi trong test và báo cáo kiểm kê. */
    var showing: View = list
        private set

    init {
        compact?.let { addView(it) }
        addView(list)
    }

    /** Đổ số mới tại chỗ (không dựng view). */
    override fun refresh(m: GroupBoardModel) {
        kit.bind(m.cells)
        compact?.refresh(m)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val mode = MeasureSpec.getMode(heightMeasureSpec)
        val h = if (mode == MeasureSpec.UNSPECIFIED) null else MeasureSpec.getSize(heightMeasureSpec)
        val k = compact
        // Thứ tự lùi theo chỗ ĐO (KDoc lớp): hình xe (mọi cỡ đủ ngưỡng) → danh sách gọn (chỉ dưới ngưỡng).
        val shown: View = when {
            k != null && h != null && k.fits(w, h) -> k
            else -> list
        }
        showing = shown
        listOfNotNull(k, list).forEach { it.visibility = if (it === shown) View.VISIBLE else View.GONE }
        shown.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), heightMeasureSpec)
        setMeasuredDimension(w, shown.measuredHeight)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val v = showing
        v.layout(0, 0, v.measuredWidth, v.measuredHeight)
    }
}

/**
 * Bộ phận dùng chung của hai dạng: dựng nút qua [ControlTileFactory] (CÙNG bộ dựng với thanh nút ⇒ cùng chốt chống-bấm-
 * kép, cùng bảng trạng thái — nút bật thì sáng nền nhấn) và giữ các ô SỐ theo mã ô XEM để [bind] đổ tại chỗ.
 *
 * ## Luật màu số (R22 — một luật cho mọi số của ô nhóm, xem [GroupTileView.tintOf])
 * Số luôn **mực trung tính**. *"Đang mở / đang bật"* nói bằng thứ có nghĩa với nó: nút tương ứng sáng nền nhấn và (hình
 * xe) mức đầy nhấn; màu chữ chỉ đổi khi là CẢNH BÁO (hổ phách / đỏ). Ảnh owner: `0 %` trắng cạnh `35 %` xanh — hai màu
 * chữ cho cùng một loại số, không có luật nào người xem đoán ra được.
 */
internal class MatrixKit(private val ctx: Context, data: WidgetData) {
    private val plain = ControlTileFactory(ctx, control = { data.control }, size = TileSize.GROUP, icons = false)
    private val iconic = ControlTileFactory(ctx, control = { data.control }, size = TileSize.GROUP, icons = true)
    private val values = HashMap<String, MutableList<TextView>>()
    /** R22·C — đặt tường minh của ô TOGGLE đã dựng ([ActionTile.set]) theo mã nút: chạm chấm kính đi qua đây. */
    private val setters = HashMap<String, (Int) -> Unit>()

    /**
     * Một nút. [label] = chữ thay nhãn nút (động từ `100%` trong hàng của một vật; `4 kính 100%` ở thanh cả-xe), `null` =
     * nhãn ngắn của nút; [icon] = có hình. Nhãn **một dòng** — bề rộng nút đã được đo đủ cho chữ ([tileNeedPx]).
     */
    fun tile(a: GroupActionCell, label: String?, icon: Boolean): View {
        val f = if (icon) iconic else plain
        val v = ActionMacros.byId(a.id)?.let { f.macroTile(it) }
            ?: ControlRegistry.byId(a.id)?.let { def -> f.actionTile(def, label).also { t -> t.set?.let { setters[a.id] = it } }.view }
            ?: View(ctx)
        val texts = ArrayList<TextView>(); tileTexts(v, texts)
        texts.forEach { it.minLines = 1; it.maxLines = 1; it.ellipsize = TextUtils.TruncateAt.END }
        return v
    }

    /** Nhãn vật: một dòng, lùi màu, "…" nếu bị kẹp. */
    fun caption(text: String, size: Float = KachiWidgetType.LABEL_SP): TextView =
        GroupTileView.text(ctx, text, size, KachiTheme.MUT).apply {
            KachiWidgetType.caption(this); maxLines = 1; ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
        }

    /** Ô số của [cell] (rỗng nếu vật không có ô XEM) — đăng ký để [bind] đổ tại chỗ. */
    fun value(cell: GroupCell?, size: Float = KachiWidgetType.VALUE_SP): TextView =
        GroupTileView.text(ctx, "", size, KachiTheme.INK, bold = true).apply {
            maxLines = 1; gravity = Gravity.END or Gravity.CENTER_VERTICAL
            cell?.let { values.getOrPut(it.id) { ArrayList() } += this; show(this, it) }
        }

    /** Đặt ô [id] về [v] (TOGGLE: `> 0` bật · `0` tắt) qua CÙNG đường ghi của ô; `false` = ô không đặt tường minh được. */
    fun set(id: String, v: Int): Boolean = setters[id]?.let { it(v); true } ?: false

    fun bind(cells: List<GroupCell>) = cells.forEach { cell -> values[cell.id]?.forEach { show(it, cell) } }

    private fun show(tv: TextView, cell: GroupCell) {
        val text: CharSequence = if (cell.available) KachiWidgetType.withUnit(cell.number, cell.unit) else cell.number
        if (tv.text.toString() != text.toString()) { tv.text = text; tv.requestLayout() }
        // Mực trung tính; chỉ cảnh báo mới đổi màu chữ (xem KDoc lớp). Chưa đọc được ⇒ mờ, KHÔNG bịa số.
        KachiWidgetType.pending(tv, cell.available, GroupTileView.tintOf(cell.tone))   // R22·B: chưa đọc ⇒ MUT
    }

    fun px(dp: Int): Int = dpi(ctx, dp)

    /** Đích chạm + khe hàng: nút cao [Sp.TOUCH], hàng cao thêm [Sp.XS] để hai hàng nút không dính nhau. */
    val rowPx: Int get() = px(Sp.TOUCH + Sp.XS)

}
