package com.byd.clusternav.launcher

import android.graphics.Color
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.accessibility.AccessibilityManager
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
 * Chạm đầu ⇒ nút *tắt* đổi sang trạng thái xác nhận trong cửa sổ [armedWindow] (gốc [SlotCloseConfirm.WINDOW_MS] = 2 s, dài
 * hơn theo *"Thời gian thực hiện hành động"* của trợ năng — [windowNow]): đĩa ĐỎ ([KachiTheme.RED]) + icon tô [KachiTheme.BG]
 * (`SlotLifecycleWiringContractTest` *đĩa xác nhận*: ≥ 4.5:1 cả hai bảng màu) + mô tả trợ năng *"Chạm lần nữa để tắt"* (5
 * tiếng), và báo [onArmed] để đầu ô hiện tiếp suốt lượt chờ. Chạm lần hai trong cửa sổ ⇒ tắt thật; hết cửa sổ / hàng ẩn /
 * khung dựng lại / nút thành không làm được ⇒ về như cũ. *Chạy nền* (đảo được) vẫn một chạm. Soát vòng 2: chặn nhấp đúp đo
 * DOWN₂ − UP₁ bằng `MotionEvent.eventTime` ([track] — chỉ NHÌN, trả `false` nên click vẫn nổ như cũ). Soát vòng 3: mỗi click
 * lấy ĐÚNG lần nhấn của nó từ hàng đợi theo thứ tự UP ([SlotCloseTouch]) và mọi mốc là `eventTime` — luồng chính trễ (click
 * được POST, DOWN₂ tới trước click₁) không còn biến một cú nhấp đúp thành "không phải ngón" ⇒ tắt.
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

    /** Cửa sổ chờ của lượt đang chờ ([windowNow] đọc lúc vào chờ). */
    private var armedWindow = SlotCloseConfirm.WINDOW_MS

    /**
     * Soát vòng 5 [P3]: lúc (`SystemClock.uptimeMillis`) trạng thái chờ (đĩa đỏ) lên KHUNG VẼ đầu tiên ([markShown]); `null` =
     * chưa vẽ ⇒ lần nhấn nào cũng chưa thể là xác nhận ([SlotCloseConfirm.seen]).
     */
    private var shownAt: Long? = null
    private val disarmTask = Runnable { disarm() }

    /** Các lần nhấn TRỌN của ngón trên nút *tắt* ([track]) chờ click của chúng ([tap]) — xem [SlotCloseTouch]. */
    private val presses = SlotCloseTouch()

    /** Cùng ngưỡng trượt của chính View (`mTouchSlop` — r47 `View.java:5059`) để [track] biết lần nhấn nào KHÔNG ra click. */
    private val slop = ViewConfiguration.get(slot.context).scaledTouchSlop.toFloat()

    /** UP của cú chạm trước trong lượt chờ — mốc so cho DOWN kế (`GestureDetector.isConsideredDoubleTap`). `null` = không có. */
    private var lastUp: Long? = null

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
        // Soát vòng 3 [P3]: click được POST sau UP (r47 `View.java:14820-14825`) ⇒ lúc nó chạy, ngón có thể đã nhấn tiếp. Lấy lần
        // nhấn CỦA click này từ hàng đợi ([SlotCloseTouch]); mọi mốc là `eventTime` của nó. Không có ⇒ click không đến từ ngón
        // (trợ năng `ACTION_CLICK`, bàn phím) ⇒ không có khoảng nhấp đúp để đo, mốc = giờ hiện tại.
        // Soát vòng 4 [P3]: ĐÚNG MỘT lần nhấn ở đầu hàng, không bỏ theo tuổi (bỏ theo tuổi lệch cặp ⇒ FIRE sau lần kẹt 1,0–1,25 s).
        val now = SystemClock.uptimeMillis()
        val press = presses.take()
        val at = press?.up ?: now
        val gap = press?.let { p -> lastUp?.let { p.down - it } }
        val tapGap = ViewConfiguration.getDoubleTapTimeout().toLong()
        // Soát vòng 5 [P3]: lần nhấn mà ngón xuống TRƯỚC khung vẽ đầu tiên của đĩa đỏ không thể là xác nhận (luồng chính kẹt ⇒
        // hai click chạy liền nhau trước mọi khung vẽ) ⇒ WAIT, người lái thấy đỏ rồi chạm lại.
        val seen = SlotCloseConfirm.seen(press?.down, shownAt)
        when (SlotCloseConfirm.onTap(armedAt, at, gap, tapGap, armedWindow, seen)) {
            SlotCloseConfirm.Tap.ARM -> arm(at, press?.up)
            SlotCloseConfirm.Tap.WAIT -> lastUp = press?.up   // nhấp đúp: cú kế so với UP của cú NÀY (chuỗi nhấp nhanh vẫn là nhấp đúp)
            SlotCloseConfirm.Tap.FIRE -> { disarm(); port.onAction(index, Button.CLOSE) }
        }
    }

    /**
     * Chỉ NHÌN cú chạm trên nút *tắt* (lần nhấn cho [tap]) — không nuốt: trả `false`, `View.onTouchEvent` vẫn ra click. Trượt khỏi
     * nút quá ngưỡng ⇒ View không ra click (r47 `View.java:14931-14941`) ⇒ lần nhấn ấy không vào hàng đợi. Soát vòng 4 [P3]: ở UP
     * post mốc lượt [SlotCloseTouch.reached] qua CHÍNH `View.post` của nút — người nghe chạy trước `onTouchEvent` (r47
     * `View.java:13424-13430`) ⇒ mốc vào hàng NGAY TRƯỚC `post(mPerformClick)` của cùng UP, dọn lần nhấn View không ra click.
     */
    private fun track(ev: MotionEvent) {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> presses.down(ev.eventTime)
            MotionEvent.ACTION_MOVE -> buttons[Button.CLOSE]?.let { v -> if (!SlotCloseTouch.inView(ev.x, ev.y, v.width, v.height, slop)) presses.left() }
            MotionEvent.ACTION_UP -> presses.up(ev.eventTime)?.let { p -> buttons[Button.CLOSE]?.post { presses.reached(p) } }
            MotionEvent.ACTION_CANCEL -> presses.cancel()
        }
    }

    /**
     * Cửa sổ chờ lúc này: gốc 2 s, dài hơn nếu người dùng đặt *"Thời gian thực hiện hành động"* (API 29 = minSdk
     * `getRecommendedTimeoutMillis` — [ĐO nguồn r47 `AccessibilityManager.java:895-907`]: CONTROLS ⇒ max(gốc, cài đặt tương tác),
     * ICONS ⇒ max(…, cài đặt không tương tác)). Không có dịch vụ ⇒ gốc.
     */
    private fun windowNow(): Long {
        val am = slot.context.getSystemService(AccessibilityManager::class.java) ?: return SlotCloseConfirm.WINDOW_MS
        val flags = AccessibilityManager.FLAG_CONTENT_CONTROLS or AccessibilityManager.FLAG_CONTENT_ICONS
        return SlotCloseConfirm.window(am.getRecommendedTimeoutMillis(SlotCloseConfirm.WINDOW_MS.toInt(), flags).toLong())
    }

    /** Phần còn lại của lượt chờ xác nhận (`null` = không chờ) — [SlotHeadAutoHide] không ẩn đầu ô trước khi nó hết. */
    fun armedLeftMs(): Long? = armedAt?.let { (it + armedWindow - SystemClock.uptimeMillis()).coerceAtLeast(0L) }

    /** Vào chờ xác nhận từ mốc [at] (UP của lần nhấn, hoặc giờ hiện tại nếu không đến từ ngón); hết giờ theo CÙNG mốc đó. */
    private fun arm(at: Long, up: Long?) {
        val cell = buttons[Button.CLOSE] as? ViewGroup ?: return
        armedAt = at
        armedWindow = windowNow()
        lastUp = up
        paint(cell, confirm = true)
        shownAt = null
        markShown(cell, at)
        cell.contentDescription = cell.context.getString(R.string.kachi_slot_close_confirm)
        slot.removeCallbacks(disarmTask)
        slot.postDelayed(disarmTask, (at + armedWindow - SystemClock.uptimeMillis()).coerceAtLeast(0L))
        onArmed(this)
    }

    /**
     * Soát vòng 5 [P3] — ghi [shownAt] ở KHUNG VẼ đầu tiên sau khi vào chờ. [ĐO nguồn] `postOnAnimation` = `CALLBACK_ANIMATION`
     * của Choreographer (android-10.0.0_r47 `View.java:17902-17912`; 12.0.0_r34 `:19080`), mà một khung chạy input → animation →
     * traversal (vẽ) → commit (r47 `Choreographer.java:718-727`; 12_r34 `:772-782`) ⇒ `paint` vừa đổi nền nên CHÍNH khung đó vẽ
     * đĩa đỏ. Luồng chính còn kẹt ⇒ chưa có khung nào ⇒ chưa đánh dấu. Nút chưa hiện (hàng đang mờ vào) ⇒ đợi khung sau. Lượt
     * chờ khác/đã gỡ ⇒ bỏ. View đã tháo ⇒ việc nằm hàng chờ của view tới khi gắn lại (`getRunQueue`), không rò.
     *
     * Soát vòng 6 [P3] — mốc ghi SAU khi khung ấy XONG (đo + bố cục + vẽ), không ở pha animation: traversal của CHÍNH khung đó có
     * thể là một lượt WidgetFit nguội 0,5–0,7 s (`FitGridLayout` khớp trong `onMeasure`) ⇒ mốc ở pha animation cho phép một cú chạm
     * trong lúc khung còn đang đo (đĩa đỏ CHƯA lên màn) thành xác nhận. [ĐO nguồn r47] `View.post` trong callback animation = message
     * ĐỒNG BỘ, nằm sau rào của traversal (`ViewRootImpl.java:1689-1694` đặt rào lúc `paint` xin vẽ, `:1712-1715` gỡ ở `doTraversal`)
     * ⇒ chỉ chạy khi `doFrame` đã xong. Chạm tới giữa khung được phát ở `nativePollOnce` (`MessageQueue.java:336`, trước khi lấy
     * message; `ViewRootImpl.java:7630` + `:7434-7435` xử lý ngay) nên click của nó post SAU mốc này ⇒ DOWN < mốc ⇒ WAIT. [SUY] đường
     * fd native của `InputEventReceiver` (chưa fetch `android_view_InputEventReceiver.cpp`).
     */
    private fun markShown(cell: View, at: Long) {
        cell.postOnAnimation {
            if (armedAt != at || shownAt != null) return@postOnAnimation
            if (cell.isShown) cell.post { if (armedAt == at && shownAt == null) shownAt = SystemClock.uptimeMillis() } else markShown(cell, at)
        }
    }

    /** Về trạng thái thường (idempotent): màu, mô tả, hẹn giờ. */
    private fun disarm() {
        slot.removeCallbacks(disarmTask)
        lastUp = null
        shownAt = null
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
     * QA 2.87 — chủ đề đổi TẠI CHỖ: khung ô App KHÔNG dựng lại (`WorkspaceView.restyle` giữ app chạy) ⇒ tô lại nút theo bảng
     * màu mới (đĩa + icon [KachiTheme.MUT]; nút đang chờ xác nhận giữ đỏ). Đĩa sau ⇄ ([swapDisc]) mang thẻ kính ⇒
     * `KachiGlass.refresh` của `applyThemeInPlace` đã tô lại.
     */
    fun restyle() = buttons.forEach { (b, v) -> (v as? ViewGroup)?.let { paint(it, confirm = b == Button.CLOSE && armedAt != null) } }

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
            fun cell(b: Button?): View = when {
                b == null && kind == SlotHeadRest.Kind.APP -> swapDisc(ctx)   // QA 2.87 [P3]: ⇄ ô App cũng nằm trên đĩa kính
                b == null || b !in possible -> Space(ctx)
                else -> button(slot, index, kind, b).also { made[b] = it }
            }
            listOf(Button.BACKGROUND, null, Button.CLOSE).forEach { row.addView(cell(it), LinearLayout.LayoutParams(touch, touch)) }
            slot.addView(row, slot.childCount - 1, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, touch, Gravity.TOP or Gravity.CENTER_HORIZONTAL))
            val cluster = SlotActionsCluster(slot, index, kind, projector, port, row, made, onArmed)
            made.forEach { (b, v) -> v.setOnClickListener { cluster.tap(b) } }   // *tắt* qua hai bước ([tap]), nút khác làm ngay
            made[Button.CLOSE]?.let { observe(it, cluster) }
            return cluster
        }

        /**
         * Nút *tắt*: người nghe chạm CHỈ NHÌN ([track]) và trả `false` ⇒ `View.onTouchEvent` vẫn chạy, click (và `performClick`
         * cho trợ năng) đi đúng đường cũ — lý do chặn lint `ClickableViewAccessibility` (nó đòi `performClick` khi người nghe
         * TỰ xử lý chạm; ở đây không).
         */
        @SuppressLint("ClickableViewAccessibility")
        private fun observe(v: View, cluster: SlotActionsCluster) = v.setOnTouchListener { _, ev -> cluster.track(ev); false }

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
            // L8 · D-L6-3 — đĩa kính sau icon (luật 5): view RIÊNG, không bấm được; bán kính = nửa cạnh ⇒ tròn; NÚT ⇒ không mờ R-OP.
            val disc = View(ctx).apply { isClickable = false; isFocusable = false }
            return FrameLayout(ctx).apply {
                isClickable = true
                contentDescription = ctx.getString(describe(b, kind), index + 1)
                addView(disc, discLp(ctx))
                addView(icon, FrameLayout.LayoutParams(iconPx, iconPx, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = iconTop(ctx) })
                paint(this, confirm = false)
            }
        }

        /** Tâm icon ngang tâm icon ⇄ (⇄ canh giữa khung cao SLOT_HEAD_CLEAR): lề trên = (SLOT_HEAD_CLEAR − ICON_S) / 2. */
        private fun iconTop(ctx: Context): Int = (KachiTheme.dpi(ctx, Sp.SLOT_HEAD_CLEAR) - KachiTheme.dpi(ctx, Sp.ICON_S)) / 2

        /** Đĩa [Sp.SWAP_DISC] đồng tâm với icon (nút cụm và ⇄ dùng CHUNG một hình học — tâm đĩa = tâm icon ⇄). */
        private fun discLp(ctx: Context): FrameLayout.LayoutParams {
            val iconPx = KachiTheme.dpi(ctx, Sp.ICON_S)
            val discPx = KachiTheme.dpi(ctx, Sp.SWAP_DISC)
            return FrameLayout.LayoutParams(discPx, discPx, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = iconTop(ctx) - (discPx - iconPx) / 2 }
        }

        /**
         * QA 2.87 [P3] (D-L8-1) — ĐĨA KÍNH sau ⇄ của ô App, ở ô giữa của hàng (đúng dưới ⇄ — ⇄ là con cuối của khung, vẽ trên hàng).
         * [ĐO máy ảo QA `l4/s7-chrome-bg-toast.png`] ⇄ trần trên trang trắng của Chrome gần như vô hình, trong khi hai nút cụm có
         * đĩa thì đọc được — app không theo chủ đề của Kachi nên không màu đơn nào đủ (lý lẽ luật 5). Đĩa ở ĐÂY (không trong
         * `SlotSwapButton` — bộ dựng ⇄ bị ghim byte): chỉ ô App mới có cụm hiện cùng ⇄; nó ẩn/hiện CÙNG hàng (cùng nhịp nghỉ của
         * ⇄), không bấm được, không vào cây trợ năng — cú chạm vẫn tới khung chạm của ⇄ nằm trên.
         */
        private fun swapDisc(ctx: Context): View = FrameLayout(ctx).apply {
            isClickable = false; isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            val disc = View(ctx).apply { isClickable = false; isFocusable = false }
            addView(disc, discLp(ctx))
            KachiGlass.apply(disc, Sp.SWAP_DISC / 2, SurfaceTone.NEUTRAL, fade = false)
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
