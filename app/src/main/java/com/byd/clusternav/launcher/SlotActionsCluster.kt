package com.byd.clusternav.launcher

import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Space
import com.byd.clusternav.R
import com.byd.clusternav.launcher.SlotHeadActions.Button
import com.byd.clusternav.launcher.KachiSpace as Sp

/**
 * ═══ L6 · (c) — cổng màn chính cấp cho đầu ô: nút nào làm được + chạm nút + app rời ô ═══════════════════════════════
 *
 * Gắn vào [SlotHeadAutoHide.actions] (`KachiHomeActivity`). Thân ở `KachiHomeSlotActions`; quyết định ở `:core`
 * ([SlotHeadActions] · [SlotRevertPlan]). Mọi hàm chạy trên luồng chính.
 */
internal interface SlotActionsPort {
    /**
     * Nút đang làm được của ô [index] (lớp tạm / kênh có thể đổi mà khung không dựng lại ⇒ hỏi mỗi lần hiện). [hostLive] =
     * khung này có bộ chiếu màn ảo chưa nhả — đọc từ CHÍNH khung (lúc dựng, `WorkspaceView.hostAt` còn trỏ khung cũ).
     */
    fun buttons(index: Int, kind: SlotHeadRest.Kind, projector: SlotHeadRest.Projector, hostLive: Boolean): List<Button>

    /** Người dùng chạm [button] của ô [index]. */
    fun onAction(index: Int, button: Button)

    /** App [pkg] của ô [index] đã rời màn ảo (nhịp đo `SlotLiveProbe`) — (a)/(b), `SlotRevertPlan.Event.APP_DIED`. */
    fun onAppGone(index: Int, pkg: String)
}

/**
 * ═══ L6 · (c) — CỤM nút *chạy nền* / *tắt* cạnh ⇄ của MỘT khung ô ═══════════════════════════════════════════════════
 *
 * Owner 03/10: *"Chỗ nút switch app/widget có thể thêm 2 nút, 1 là đẩy app ra chạy nền, 2 là tắt app luôn … Nút cũng tự
 * hide sau 3s"* · *"Ô widget cũng cho tắt đc chứ hả"*. Hàng ngang `[chạy nền] [chỗ của ⇄] [tắt]` canh giữa mép trên khung —
 * ⇄ (bộ dựng riêng, bị ghim byte: `SlotSwapButton`) nằm đúng ô giữa, nổi TRÊN hàng này (con cuối của khung).
 *
 * ## Năm điều phải đúng
 *  1. **Anh em của ⇄, không phải con**: khung ⇄ cao [Sp.SLOT_HEAD_CLEAR] (dưới 48 dp, có lý do ở `WorkspaceView.headLp`)
 *     ⇒ nút đặt trong đó không thể có đích chạm 48 dp (chạm ngoài biên cha không tới con). Hàng này cao [Sp.TOUCH], mỗi
 *     nút [Sp.TOUCH]×[Sp.TOUCH]. Hàng và khung ⇄ KHÔNG bấm được ⇒ chạm vào chỗ trống của chúng rơi xuống app/widget.
 *  2. **Nút không làm được thì không có**: chỉ dựng nút trong [SlotHeadActions.possible] của loại ô (ô trống / ô app không
 *     màn ảo ⇒ không dựng gì); nút có thể mà LÚC NÀY không làm được (không kênh, không app LƯU khác để chạy nền) ⇒
 *     `INVISIBLE` (không vẽ, không bấm, không vào cây trợ năng). Đổi `VISIBLE`↔`INVISIBLE` không đo lại bố cục.
 *  3. **Cùng nhịp nghỉ với ⇄**: [SlotHeadAutoHide] gọi [settle]/[show]/[hide]/[cancel] ở đúng bốn chỗ nó làm với ⇄ ⇒ một
 *     hẹn giờ ([SlotHeadRest.HIDE_AFTER_MS]) cho cả đầu ô. Ẩn = `INVISIBLE` (như ⇄ — nút vô hình không bấm được).
 *  4. **`animate().cancel()` trước MỌI animate** (r47 `ViewPropertyAnimator.java:418-433`): hiện lại giữa lúc mờ không
 *     để lại một `INVISIBLE` muộn — cùng luật `SlotHeadAutoHide`, bài canh riêng soi tệp này.
 *  5. **Hình như ⇄**: chỉ icon [Sp.ICON_S] tô [KachiTheme.MUT], không nền, không viền (owner 2026-09-14 *"kín đáo, nhỏ gọn"*)
 *     — tâm icon ngang tâm icon ⇄. Mô tả trợ năng theo loại ô, đủ 5 tiếng (tài nguyên).
 */
internal class SlotActionsCluster private constructor(
    private val slot: ViewGroup,
    private val index: Int,
    private val kind: SlotHeadRest.Kind,
    private val projector: SlotHeadRest.Projector,
    private val port: SlotActionsPort,
    private val row: View,
    private val buttons: Map<Button, View>,
) {

    /** Hỏi lại cổng nút nào làm được LÚC NÀY; chỉ đổi view khi khác (không vẽ lại thừa). */
    fun refresh() {
        val host = (0 until slot.childCount).firstNotNullOfOrNull { slot.getChildAt(it) as? VdAppHost }
        val now = port.buttons(index, kind, projector, hostLive = host != null && !host.isReleased)
        buttons.forEach { (b, v) ->
            val want = if (b in now) View.VISIBLE else View.INVISIBLE
            if (v.visibility != want) v.visibility = want
        }
    }

    /** Trạng thái nghỉ theo ⇄: ẩn hẳn (alpha 0 + `INVISIBLE`) hoặc hiện hẳn. */
    fun settle(hidden: Boolean) {
        refresh()
        row.animate().cancel()
        row.alpha = if (hidden) 0f else 1f
        row.visibility = if (hidden) View.INVISIBLE else View.VISIBLE
    }

    /** Hiện cùng ⇄ (mờ vào). */
    fun show() {
        refresh()
        row.animate().cancel()
        row.visibility = View.VISIBLE
        row.animate().alpha(1f).setDuration(SlotHeadRest.FADE_IN_MS)
    }

    /** Ẩn cùng ⇄ (mờ ra rồi `INVISIBLE`). */
    fun hide() {
        row.animate().cancel()
        row.animate().alpha(0f).setDuration(SlotHeadRest.FADE_OUT_MS).withEndAction { row.visibility = View.INVISIBLE }
    }

    /** Gỡ lượt mờ đang chạy (rời cửa sổ / dựng lại khung). */
    fun cancel() = row.animate().cancel()

    /**
     * Toạ độ ([x],[y] — trong KHUNG ô) có rơi vào một nút đang làm được không — để [SlotHeadAutoHide] không hiện đầu ô khi
     * cú chạm rơi vào CHỖ một nút đang ẩn (luật 4 của ⇄: ô tìm kiếm của Google Maps nằm giữa-trên).
     */
    fun hits(x: Float, y: Float): Boolean = buttons.values.any { v ->
        val l = row.left + v.left
        val t = row.top + v.top
        v.visibility == View.VISIBLE && x >= l && x < l + v.width && y >= t && y < t + v.height
    }

    companion object {
        /**
         * Dựng cụm cho khung [slot] (ô [index]) rồi chèn NGAY DƯỚI ⇄ (⇄ vẫn là con cuối — `SlotHeadAutoHide.register`
         * đọc nó như thế). Loại ô không có nút nào ngoài ⇄ ⇒ `null`, không dựng view nào.
         */
        fun attach(
            slot: ViewGroup,
            index: Int,
            kind: SlotHeadRest.Kind,
            projector: SlotHeadRest.Projector,
            port: SlotActionsPort,
        ): SlotActionsCluster? {
            val possible = SlotHeadActions.possible(kind, projector)
            if (possible.isEmpty()) return null
            val ctx = slot.context
            val touch = KachiTheme.dpi(ctx, Sp.TOUCH)
            val made = LinkedHashMap<Button, View>()
            val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
            fun cell(b: Button?): View =
                if (b == null || b !in possible) Space(ctx) else button(slot, index, kind, b) { port.onAction(index, b) }.also { made[b] = it }
            listOf(Button.BACKGROUND, null, Button.CLOSE).forEach { row.addView(cell(it), LinearLayout.LayoutParams(touch, touch)) }
            slot.addView(row, slot.childCount - 1, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, touch, Gravity.TOP or Gravity.CENTER_HORIZONTAL))
            return SlotActionsCluster(slot, index, kind, projector, port, row, made)
        }

        /** Một nút: khung chạm [Sp.TOUCH]² (bấm được, có mô tả) + icon [Sp.ICON_S] không bấm được, tâm ngang tâm ⇄. */
        private fun button(slot: ViewGroup, index: Int, kind: SlotHeadRest.Kind, b: Button, onTap: () -> Unit): View {
            val ctx = slot.context
            val icon = ImageView(ctx).apply {
                val r = KachiTheme.iconRes(if (b == Button.BACKGROUND) "ic-to-back" else "ic-close")
                if (r != 0) { setImageResource(r); setColorFilter(Color.parseColor(KachiTheme.MUT)) }
                scaleType = ImageView.ScaleType.FIT_CENTER
                background = null
                isClickable = false; isFocusable = false
            }
            val iconPx = KachiTheme.dpi(ctx, Sp.ICON_S)
            // Tâm icon ngang tâm icon ⇄ (⇄ canh giữa khung cao SLOT_HEAD_CLEAR): lề trên = (SLOT_HEAD_CLEAR − ICON_S) / 2.
            val top = (KachiTheme.dpi(ctx, Sp.SLOT_HEAD_CLEAR) - iconPx) / 2
            return FrameLayout(ctx).apply {
                isClickable = true
                contentDescription = ctx.getString(describe(b, kind), index + 1)
                setOnClickListener { onTap() }
                addView(icon, FrameLayout.LayoutParams(iconPx, iconPx, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = top })
            }
        }

        /** Mô tả trợ năng (người đọc thấy số ô 1-based như mọi chỗ khác). */
        private fun describe(b: Button, kind: SlotHeadRest.Kind): Int = when {
            b == Button.BACKGROUND -> R.string.kachi_slot_to_back
            kind == SlotHeadRest.Kind.APP -> R.string.kachi_slot_close_app
            else -> R.string.kachi_slot_close_widget
        }
    }
}
