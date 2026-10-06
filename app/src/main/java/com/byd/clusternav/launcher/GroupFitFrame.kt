package com.byd.clusternav.launcher

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import android.view.ViewGroup

/**
 * ═══ 2.93 `GROUPBOARD-1ROW` — ô NHÓM đơn (Khí hậu · Lốp · Kính…) ở khung THẤP: tự chuyển sang dạng tóm tắt ═════════════════
 *
 * Bệnh [ĐO máy ảo QA4 04/10, thấy tình cờ]: bảng nhóm ĐẦY ĐỦ ([GroupTileView]: đầu ô + lưới số + hàng nút) trong khung một
 * hàng chỉ hiện tiêu đề hoặc chữ rất nhỏ — cả khối cần ≈ 168 px (lề 2 × 18 + đầu ô 24 + hàng nút 12 + ~96) [SUY đọc mã]
 * trong khi một hàng lưới cho 97–148 px; lưới số co tới sàn rồi hàng nút TRÀN ra ngoài và bị cắt (không ném, không log).
 *
 * Luật (đo, không theo mã nhóm — CLAUDE.md §7): đo bảng đầy đủ ĐÚNG ở cỡ khung; các con xếp dọc của nó TRÀN khung
 * ([FitRules.spills], cùng phép kiểm tràn của lưới widget) ⇒ hiện dạng TÓM TẮT — chính ô nén của nhóm ([GroupTiles.mini]:
 * icon + tên nhóm + sắc thái cảnh báo, dạng owner đã duyệt 09-23) bọc [FitGridLayout] để nó GIÃN theo khung như mọi ô
 * đơn. Không tràn ⇒ bảng đầy đủ như 2.92 (CLAUDE.md §6). Cả hai dựng MỘT lần, chỉ đổi hiện/ẩn — hàng nút của bảng đầy đủ
 * không bao giờ bị tháo/gắn (SOÁT P1-1/C5). Nhịp xe đổ tại chỗ vào CẢ HAI (chỗ gọi đăng ký một hàm đổ cho khung này).
 */
@SuppressLint("ViewConstructor")   // chỉ dựng bằng mã (GroupTiles.build)
internal class GroupFitFrame(context: Context, private val full: GroupTileView, private val compact: View) : ViewGroup(context) {

    /** Đang hiện dạng tóm tắt (cho bài canh/QA) — chỉ lượt đo đổi. */
    var compactShown = false
        private set

    init {
        addView(full, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(compact, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        compact.visibility = GONE
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(w, h)
        val ws = MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY)
        val hs = MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY)
        full.measure(ws, hs)
        val spill = MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.UNSPECIFIED && h > 0 && spills(full, h)
        if (spill != compactShown) {
            compactShown = spill
            full.visibility = if (spill) GONE else VISIBLE
            compact.visibility = if (spill) VISIBLE else GONE
        }
        if (spill) compact.measure(ws, hs)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val shown = if (compactShown) compact else full
        shown.layout(0, 0, shown.measuredWidth, shown.measuredHeight)
    }

    /**
     * Bảng đầy đủ [g] (đã đo ở cao [h]) KHÔNG đọc được không: các con xếp dọc TRÀN khung ([FitRules.spills], con `GONE` không
     * tính), HOẶC một ô vẽ Canvas bên trong thấp hơn cao tối thiểu nó tự khai ([MinUsefulHeight] — ô vẽ khai `MATCH_PARENT`
     * luôn "vừa" theo phép tràn vì nó nhận đúng phần còn lại, dù còn 60 px).
     */
    private fun spills(g: ViewGroup, h: Int): Boolean {
        val kids = (0 until g.childCount).map { g.getChildAt(it) }.filter { it.visibility != GONE }
        val heights = kids.map { v -> v.measuredHeight + ((v.layoutParams as? MarginLayoutParams)?.let { it.topMargin + it.bottomMargin } ?: 0) }
        return FitRules.spills(h, g.paddingTop + g.paddingBottom, heights, stacked = true) || cramped(g)
    }

    private fun cramped(v: View): Boolean = when {
        v.visibility == GONE -> false
        v is MinUsefulHeight -> v.measuredHeight < v.minUsefulHeightPx
        v is ViewGroup -> (0 until v.childCount).any { cramped(v.getChildAt(it)) }
        else -> false
    }
}

/**
 * 2.93 `GROUPBOARD-1ROW` — ô vẽ Canvas tự khai chiều cao TỐI THIỂU để chữ của nó còn ở trên sàn đọc được (tính từ sàn cỡ
 * chữ của chính nó, không phải một số dp đoán). [GroupFitFrame] đổi sang dạng tóm tắt khi ô thấp hơn.
 */
interface MinUsefulHeight {
    val minUsefulHeightPx: Int
}
