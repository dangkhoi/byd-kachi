package com.byd.clusternav.launcher

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.ViewConfiguration
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
 *     màn ảo ⇒ không dựng gì); nút có thể mà LÚC NÀY không làm được (không kênh / bộ chiếu đã nhả) ⇒
 *     `INVISIBLE` (không vẽ, không bấm, không vào cây trợ năng). Đổi `VISIBLE`↔`INVISIBLE` không đo lại bố cục.
 *  3. **Cùng nhịp nghỉ với ⇄**: [SlotHeadAutoHide] gọi [settle]/[show]/[hide]/[cancel] ở đúng bốn chỗ nó làm với ⇄ ⇒ một
 *     hẹn giờ ([SlotHeadRest.HIDE_AFTER_MS]) cho cả đầu ô. Ẩn = `INVISIBLE` (như ⇄ — nút vô hình không bấm được).
 *  4. **`animate().cancel()` trước MỌI animate** (r47 `ViewPropertyAnimator.java:418-433`): hiện lại giữa lúc mờ không
 *     để lại một `INVISIBLE` muộn — cùng luật `SlotHeadAutoHide`, bài canh riêng soi tệp này.
 *  5. **Icon trên ĐĨA KÍNH** (L8 · D-L6-3): icon [Sp.ICON_S] tô [KachiTheme.MUT] đặt trên đĩa [Sp.SWAP_DISC] ([KachiGlass]
 *     NEUTRAL, `fade = false` — CÙNG đĩa của ⇄ ô trống, hợp đồng `MUT ≥ 4.5:1` trên mọi độ chói ảnh). [ĐO máy ảo 03/10,
 *     `p4/e2e-L8`] icon trần (bản L6, như ⇄ ô có nội dung) trên nội dung APP: bảng sáng trên bản đồ tối VietMap 1.73:1, bảng
 *     tối trên Cài đặt nền trắng 2.35:1 (100 % điểm nền dưới 3:1) — app không theo chủ đề của Kachi nên không màu đơn nào
 *     đủ. Tâm đĩa = tâm icon = ngang tâm icon ⇄. Mô tả trợ năng theo loại ô, đủ 5 tiếng (tài nguyên).
 *
 * ## *Tắt* = HAI chạm (soát 2.87 · P2, quyết định điều phối — luật ở `:core` [SlotCloseConfirm])
 * Chạm đầu ⇒ nút *tắt* đổi sang trạng thái xác nhận [SlotCloseConfirm.WINDOW_MS] (2 s): đĩa ĐỎ ([KachiTheme.RED]) + icon tô
 * [KachiTheme.BG] (`SlotLifecycleWiringContractTest` *đĩa xác nhận*: ≥ 4.5:1 cả hai bảng màu) + mô tả trợ năng *"Chạm lần nữa
 * để tắt"* (5 tiếng),
 * và báo [onArmed] để đầu ô hiện tiếp suốt lượt chờ. Chạm lần hai trong 2 s ⇒ tắt thật; hết 2 s / hàng ẩn / khung dựng lại /
 * nút thành không làm được ⇒ về như cũ. *Chạy nền* (đảo được) vẫn một chạm.
 */
internal class SlotActionsCluster private constructor(
    private val slot: ViewGroup,
    private val index: Int,
    private val kind: SlotHeadRest.Kind,
    private val projector: SlotHeadRest.Projector,
    private val port: SlotActionsPort,
    private val row: View,
    private val buttons: Map<Button, View>,
    /** Nút *tắt* vừa vào trạng thái chờ xác nhận ⇒ `SlotHeadAutoHide` giữ đầu ô hiện. */
    private val onArmed: (SlotActionsCluster) -> Unit,
) {

    /** Mốc (`SystemClock.uptimeMillis`) lượt chạm đầu của *tắt*; `null` = không chờ xác nhận. */
    private var armedAt: Long? = null
    private val disarmTask = Runnable { disarm() }

    /** Hỏi lại cổng nút nào làm được LÚC NÀY; chỉ đổi view khi khác (không vẽ lại thừa). */
    fun refresh() {
        val host = (0 until slot.childCount).firstNotNullOfOrNull { slot.getChildAt(it) as? VdAppHost }
        val now = port.buttons(index, kind, projector, hostLive = host != null && !host.isReleased)
        buttons.forEach { (b, v) ->
            val want = if (b in now) View.VISIBLE else View.INVISIBLE
            if (v.visibility != want) v.visibility = want
        }
        if (Button.CLOSE !in now) disarm()
    }

    /** Một chạm lên nút [b]: *tắt* đi qua [SlotCloseConfirm] (hai bước), nút khác làm ngay. */
    private fun tap(b: Button) {
        if (b != Button.CLOSE) return port.onAction(index, b)
        val gap = ViewConfiguration.getDoubleTapTimeout().toLong()
        when (SlotCloseConfirm.onTap(armedAt, SystemClock.uptimeMillis(), gap)) {
            SlotCloseConfirm.Tap.ARM -> arm()
            SlotCloseConfirm.Tap.WAIT -> Unit
            SlotCloseConfirm.Tap.FIRE -> { disarm(); port.onAction(index, Button.CLOSE) }
        }
    }

    private fun arm() {
        val cell = buttons[Button.CLOSE] as? ViewGroup ?: return
        armedAt = SystemClock.uptimeMillis()
        paint(cell, confirm = true)
        cell.contentDescription = cell.context.getString(R.string.kachi_slot_close_confirm)
        slot.removeCallbacks(disarmTask)
        slot.postDelayed(disarmTask, SlotCloseConfirm.WINDOW_MS)
        onArmed(this)
    }

    /** Về trạng thái thường (idempotent): màu, mô tả, hẹn giờ. */
    private fun disarm() {
        slot.removeCallbacks(disarmTask)
        if (armedAt == null) return
        armedAt = null
        val cell = buttons[Button.CLOSE] as? ViewGroup ?: return
        paint(cell, confirm = false)
        cell.contentDescription = cell.context.getString(describe(Button.CLOSE, kind), index + 1)
    }

    /** Trạng thái nghỉ theo ⇄: ẩn hẳn (alpha 0 + `INVISIBLE`) hoặc hiện hẳn. */
    fun settle(hidden: Boolean) {
        disarm()
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
        disarm()
        row.animate().cancel()
        row.animate().alpha(0f).setDuration(SlotHeadRest.FADE_OUT_MS).withEndAction { row.visibility = View.INVISIBLE }
    }

    /** Gỡ lượt mờ đang chạy + lượt chờ xác nhận (rời cửa sổ / dựng lại khung). */
    fun cancel() {
        disarm()
        row.animate().cancel()
    }

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
            onArmed: (SlotActionsCluster) -> Unit,
        ): SlotActionsCluster? {
            val possible = SlotHeadActions.possible(kind, projector)
            if (possible.isEmpty()) return null
            val ctx = slot.context
            val touch = KachiTheme.dpi(ctx, Sp.TOUCH)
            val made = LinkedHashMap<Button, View>()
            val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
            fun cell(b: Button?): View = if (b == null || b !in possible) Space(ctx) else button(slot, index, kind, b).also { made[b] = it }
            listOf(Button.BACKGROUND, null, Button.CLOSE).forEach { row.addView(cell(it), LinearLayout.LayoutParams(touch, touch)) }
            slot.addView(row, slot.childCount - 1, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, touch, Gravity.TOP or Gravity.CENTER_HORIZONTAL))
            val cluster = SlotActionsCluster(slot, index, kind, projector, port, row, made, onArmed)
            made.forEach { (b, v) -> v.setOnClickListener { cluster.tap(b) } }   // *tắt* qua hai bước ([tap]), nút khác làm ngay
            return cluster
        }

        /** Một nút: khung chạm [Sp.TOUCH]² (bấm được, có mô tả) + đĩa kính + icon [Sp.ICON_S] không bấm được, tâm ngang tâm ⇄. */
        private fun button(slot: ViewGroup, index: Int, kind: SlotHeadRest.Kind, b: Button): View {
            val ctx = slot.context
            val icon = ImageView(ctx).apply {
                val r = KachiTheme.iconRes(if (b == Button.BACKGROUND) "ic-to-back" else "ic-close")
                if (r != 0) setImageResource(r)
                scaleType = ImageView.ScaleType.FIT_CENTER
                background = null
                isClickable = false; isFocusable = false
            }
            val iconPx = KachiTheme.dpi(ctx, Sp.ICON_S)
            // Tâm icon ngang tâm icon ⇄ (⇄ canh giữa khung cao SLOT_HEAD_CLEAR): lề trên = (SLOT_HEAD_CLEAR − ICON_S) / 2.
            val top = (KachiTheme.dpi(ctx, Sp.SLOT_HEAD_CLEAR) - iconPx) / 2
            // L8 · D-L6-3 — đĩa kính sau icon (luật 5): view RIÊNG, không bấm được; bán kính = nửa cạnh ⇒ tròn; NÚT ⇒ không mờ R-OP.
            val discPx = KachiTheme.dpi(ctx, Sp.SWAP_DISC)
            val disc = View(ctx).apply { isClickable = false; isFocusable = false }
            return FrameLayout(ctx).apply {
                isClickable = true
                contentDescription = ctx.getString(describe(b, kind), index + 1)
                addView(disc, FrameLayout.LayoutParams(discPx, discPx, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = top - (discPx - iconPx) / 2 })
                addView(icon, FrameLayout.LayoutParams(iconPx, iconPx, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = top })
                paint(this, confirm = false)
            }
        }

        /**
         * Màu của nút ([cell] = khung chạm: con 0 đĩa, con 1 icon). Thường = đĩa kính NEUTRAL không mờ + icon [KachiTheme.MUT]
         * (luật 5). Chờ xác nhận *tắt* = đĩa ĐỎ đặc ([KachiTheme.RED]) + icon [KachiTheme.BG] — [KachiGlass.plain] gỡ thẻ kính để
         * lượt `KachiGlass.refresh` (ảnh nền đổi) không đắp kính đè lên màu cảnh báo.
         */
        private fun paint(cell: ViewGroup, confirm: Boolean) {
            val disc = cell.getChildAt(0) ?: return
            val icon = cell.getChildAt(1) as? ImageView
            if (confirm) {
                KachiGlass.plain(disc, GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.parseColor(KachiTheme.RED)) })
                icon?.setColorFilter(Color.parseColor(KachiTheme.BG))
            } else {
                KachiGlass.apply(disc, Sp.SWAP_DISC / 2, SurfaceTone.NEUTRAL, fade = false)
                icon?.setColorFilter(Color.parseColor(KachiTheme.MUT))
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
