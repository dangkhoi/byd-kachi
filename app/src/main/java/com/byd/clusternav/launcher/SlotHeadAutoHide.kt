package com.byd.clusternav.launcher

import android.appwidget.AppWidgetHostView
import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.accessibility.AccessibilityManager
import com.byd.clusternav.ShellReadiness
import com.byd.clusternav.carexec.ShellReadinessState
import com.byd.clusternav.launcher.SlotHeadRest.Rest

/**
 * ═══ 2.87 · R-AH — nút ⇄ TỰ ẨN: một chủ cho trạng thái nút + hẹn giờ của mọi ô ═══════════════════════════════════════
 *
 * Spec `docs/specs/kachi-287-look-and-keys.html` §4.2. Luật nghỉ (ẩn hay luôn hiện) ở `:core` [SlotHeadRest]; ở đây là
 * phần biết Android: đăng ký nút của từng ô, NHÌN cú chạm do [WorkspaceView.dispatchTouchEvent] chuyển xuống, hiện/ẩn
 * có mờ dần. [WorkspaceView] sở hữu một thực thể; mọi hẹn giờ post trên chính view đó và gỡ ở [release].
 *
 * ## Năm điều phải đúng — mỗi điều là một cách làm hỏng app đang chạy trong ô [ĐO AOSP android-10.0.0_r47]
 *  1. **Ẩn = `INVISIBLE`**: view không `VISIBLE` không được chọn làm đích chạm (`View.java:13462-13464`
 *     `canReceivePointerEvents`; `ViewGroup.java:2683`) ⇒ chạm vào chỗ ⇄ đang ẩn rơi xuống app. Chỉ hạ alpha = một nút
 *     vô hình vẫn bấm được (đúng thứ FIX286 · ES1 cấm). `GONE` thì đổi bố cục ⇒ `requestLayout` cả workspace mỗi cú chạm
 *     (`View.java:15200-15202`), còn `INVISIBLE` chỉ vẽ lại (`View.java:15227-15262`) — lỗi "nháy dựt" (WorkspaceView `structural`).
 *  2. **Không cướp chạm**: chỉ được gọi SAU `super.dispatchTouchEvent` (đích chạm đã chọn xong — `ViewGroup.java:2683-2716`)
 *     và [observe] trả `Unit`, không bao giờ nuốt sự kiện. `MotionEvent` được trả về nguyên toạ độ/hành động sau lượt
 *     chuyển cho con (`ViewGroup.java:3024-3033` · `3058-3062`) nên đọc nó sau `super` là đọc đúng cú chạm.
 *  3. **Nhấp đúp vẫn tới app**: hiện ⇄ sau khi nhấc tay + `ViewConfiguration.getDoubleTapTimeout()` (đọc tại chỗ gọi như
 *     `VdAppHost` đọc ngưỡng cử chỉ); cú DOWN kế tiếp trong khoảng đó huỷ lượt hiện.
 *  4. **Chạm vào CHỖ ⇄ đang ẩn ⇒ không hiện**: ô tìm kiếm của Google Maps nằm giữa-trên, đúng chỗ ⇄; hiện ⇄ ở đó thì cú
 *     chạm thứ hai mở bảng chọn thay vì gõ tìm kiếm. Muốn thấy ⇄ thì chạm chỗ khác trong khung.
 *  5. **`animate().cancel()` trước MỌI animate**: r47 `cancel()` xoá hành động-cuối (`ViewPropertyAnimator.java:418-433`,
 *     `onAnimationCancel` :1083-1091) ⇒ hiện lại giữa lúc đang mờ không để lại một `INVISIBLE` muộn.
 *
 * ## Cú chạm KHÔNG ai nhận (khung trống · khe giữa khung)
 * Khung trống không nhận chạm (FIX286 · ES1), nên DOWN rơi xuống `WorkspaceView` mà không con nào nhận ⇒ nó trả `false`
 * và KHÔNG thấy MOVE/UP sau đó (`ViewGroup.java:2740-2742` → `View.onTouchEvent` của view không bấm được). Vì vậy với DOWN
 * không ai nhận: trong một khung ⇒ hiện ⇄ khung đó NGAY; ngoài mọi khung ⇒ hiện ⇄ MỌI khung (owner: *"nhấn đại vào màn nó
 * lại lòi ra"*). Hẹn ẩn tính từ lúc hiện.
 *
 * ## Nút là con CUỐI của khung
 * Bốn nhánh của `makeSlot` đều gắn ⇄ sau cùng (để nó nổi trên cùng và được thử chạm trước). [register] lấy con cuối và
 * kiểm hình dạng (khung chạm clickable bên trong); khác hình ⇒ không quản lý ⇒ nút giữ nguyên `VISIBLE` (an toàn).
 *
 * ## L6 (owner 03/10) — *chạy nền* / *tắt* cạnh ⇄, cùng nhịp nghỉ
 * Có [actions] (màn chính gắn) ⇒ [register] dựng thêm [SlotActionsCluster] NGAY DƯỚI ⇄ (⇄ vẫn là con cuối) và mọi chỗ
 * đặt / hiện / ẩn / gỡ ⇄ ở đây kéo theo cụm đó — một hẹn giờ [SlotHeadRest.HIDE_AFTER_MS] (3 s) cho cả đầu ô. Chạm vào CHỖ
 * một nút đang ẩn ⇒ không hiện (điều 4, cùng lẽ ⇄). Phần animate của cụm nằm ở tệp của nó (cùng luật cancel-trước).
 *
 * ## Soát 2.87 — ai QUYẾT, ai LÀM
 *  - P2: cú chạm nào hiện / giữ / hẹn hiện đầu ô là bảng thuần `:core` [SlotHeadTouch] (bảng đủ ô `SlotHeadTouchTest`); ở đây
 *    chỉ ĐO (ô nào, có con nhận, DOWN có trúng vùng nút, nút đang hiện không) rồi [exec] các việc nó trả về.
 *  - P3: nút L6 được hỏi lại không chỉ ở DOWN — kênh shell đổi trạng thái ([attach] nghe `ShellReadiness`) và sau mỗi việc
 *    đầu ô ([refreshAll], `KachiHomeSlotActions`) — chế độ luôn hiện / TalkBack không có DOWN nào để nhờ.
 *  - P3: bố cục bớt ô ⇒ [retain] bỏ mục của ô đã mất (không giữ cây view đã tháo, không hỏi nút của ô chết).
 */
internal class SlotHeadAutoHide(private val host: ViewGroup) {

    /** Một ô đã đăng ký: nút + khung chạm của nó + đầu vào của luật (để áp lại khi công tắc/TalkBack đổi). */
    private inner class Entry(
        val head: View,
        val hit: View,
        val kind: SlotHeadRest.Kind,
        val projector: SlotHeadRest.Projector,
        /** L6 — cụm *chạy nền* / *tắt* của khung (`null` = ô không có nút nào ngoài ⇄ / chưa gắn cổng). */
        val cluster: SlotActionsCluster?,
    ) {
        var rest: Rest = Rest.ALWAYS
        val reveal = Runnable { reveal(this) }
        val hide = Runnable { hide(this) }
    }

    private val entries = HashMap<Int, Entry>()

    /** L6 — cổng màn chính cho nút *chạy nền* / *tắt* + báo app rời ô. `null` (dựng lượt đầu trong `init`) ⇒ chỉ ⇄. */
    var actions: SlotActionsPort? = null
    private var enabled = true

    /** Cử chỉ đang diễn ra (DOWN → UP/CANCEL) — [SlotHeadTouch] quyết cái gì cần nhớ. */
    private var gesture = SlotHeadTouch.Gesture.NONE

    private val a11y: AccessibilityManager? =
        host.context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
    private val teListener = AccessibilityManager.TouchExplorationStateChangeListener { reapply() }

    /** P3 — kênh shell lên / xuống (luồng bất kỳ) ⇒ hỏi lại nút L6 trên luồng chính, không chờ một cú DOWN. */
    private val refreshTask = Runnable { refreshAll() }
    private val shellListener: (ShellReadinessState) -> Unit = { _ -> host.post(refreshTask) }

    private fun touchExploration(): Boolean = a11y?.isTouchExplorationEnabled == true

    /**
     * Đăng ký ⇄ của ô [index] — gọi MỖI lần một khung được dựng (cả bốn đường: `rebuild` · `renderInternal` theo ô ·
     * `restyle` · `rebuildWidgetSlots` — đều đi qua `makeSlot`). Huỷ hẹn giờ của đời trước, đặt trạng thái đầu:
     * ẩn ([Rest.AUTO_HIDE]) / hiện ([Rest.ALWAYS]).
     */
    fun register(index: Int, slot: ViewGroup, content: SlotContent) {
        entries.remove(index)?.let(::drop)
        val head = slot.getChildAt(slot.childCount - 1) as? ViewGroup ?: return
        val hit = head.getChildAt(0)?.takeIf { it.isClickable } ?: return
        val live = (0 until slot.childCount).any { slot.getChildAt(it) is AppWidgetHostView }
        val kind = SlotHeadRest.kindOf(content, live)
        val projector = projectorOf(slot)
        val e = Entry(head, hit, kind, projector, actions?.let { SlotActionsCluster.attach(slot, index, kind, projector, it, ::armed) })
        entries[index] = e
        settle(e, touchExploration())
    }

    /** P3 — bố cục còn [count] ô (`WorkspaceView.rebuild`) ⇒ bỏ mục của các ô đã mất (giữ = rò cây view đã tháo). */
    fun retain(count: Int) {
        entries.keys.filter { it >= count }.forEach { i -> entries.remove(i)?.let(::drop) }
    }

    /** P3 — hỏi lại nút L6 của MỌI ô (kênh đổi · BEHIND-HOME vừa tắt · việc đầu ô vừa xong). Luồng chính. */
    fun refreshAll() = entries.values.forEach { it.cluster?.refresh() }

    /** Công tắc theo hồ sơ (R-AH3) — áp lại trạng thái nghỉ tại chỗ, KHÔNG dựng lại ô (app trong ô chạy tiếp). */
    fun setEnabled(on: Boolean) {
        if (on == enabled) return
        enabled = on
        reapply()
    }

    /**
     * NHÌN một sự kiện chạm của workspace, SAU khi nó đã được chuyển cho con. [consumed] = kết quả của `super`.
     * Không đổi [ev], không trả gì ⇒ không thể nuốt chạm.
     */
    fun observe(ev: MotionEvent, slots: List<View>, consumed: Boolean) {
        val at = { j: Int -> valid(j, slots) }
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val i = slotAt(slots, ev.x, ev.y)
                val e = i.takeIf { it >= 0 }?.let(at)
                e?.cluster?.refresh()   // L6: lớp tạm / kênh đổi mà khung không dựng lại ⇒ hỏi lại nút TRƯỚC khi đo vùng nút
                val inHead = e != null && inHit(e, slots[i], ev.x, ev.y)
                val step = SlotHeadTouch.onDown(i, consumed, inHead, heads(at))
                gesture = step.gesture
                exec(step.acts, at)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val g = gesture
                gesture = SlotHeadTouch.Gesture.NONE
                exec(SlotHeadTouch.onUp(g, heads(at)), at)
            }
        }
    }

    /** Đầu ô hợp lệ theo chỉ số, đúng dạng đầu vào của [SlotHeadTouch]. */
    private fun heads(at: (Int) -> Entry?): Map<Int, SlotHeadTouch.Head> = entries.keys.mapNotNull { i ->
        at(i)?.let { i to SlotHeadTouch.Head(it.rest, it.head.visibility == View.VISIBLE) }
    }.toMap()

    /** THI HÀNH việc `:core` đã quyết — không quyết gì ở đây. Nhịp nhấp đúp đọc tại chỗ (không chụp sẵn vào field). */
    private fun exec(acts: List<SlotHeadTouch.Act>, at: (Int) -> Entry?) = acts.forEach { a ->
        val e = at(a.slot) ?: return@forEach
        when (a) {
            is SlotHeadTouch.Act.Reveal -> reveal(e)
            is SlotHeadTouch.Act.RevealAfterDoubleTap -> host.postDelayed(e.reveal, ViewConfiguration.getDoubleTapTimeout().toLong())
            is SlotHeadTouch.Act.Hold -> { host.removeCallbacks(e.reveal); host.removeCallbacks(e.hide) }
        }
    }

    /** L6 · *tắt* hai bước: nút *tắt* của [cluster] vừa vào trạng thái chờ ⇒ đầu ô hiện tiếp suốt lượt chờ (P2). */
    private fun armed(cluster: SlotActionsCluster) {
        val (i, e) = entries.entries.firstOrNull { it.value.cluster === cluster }?.toPair() ?: return
        val at = { j: Int -> e.takeIf { j == i } }
        exec(SlotHeadTouch.onConfirmArmed(i, heads(at)), at)
    }

    /** Workspace gắn (lại) cửa sổ: nghe TalkBack bật/tắt + kênh shell đổi, rồi áp lại (trạng thái có thể đã đổi lúc rời cửa sổ). */
    fun attach() {
        a11y?.addTouchExplorationStateChangeListener(teListener)
        ShellReadiness.addListener(shellListener)
        reapply()
    }

    /**
     * Workspace rời cửa sổ: gỡ MỌI hẹn giờ + animation + người nghe. Giữ [entries] — gắn lại mà không dựng lại ô
     * (`onAttachedToWindow` chỉ dựng lại khi có host đã nhả) thì [attach] áp lại đúng các nút đó, không để nút nào kẹt ẩn.
     */
    fun release() {
        entries.values.forEach(::drop)
        a11y?.removeTouchExplorationStateChangeListener(teListener)
        ShellReadiness.removeListener(shellListener)
        host.removeCallbacks(refreshTask)
        gesture = SlotHeadTouch.Gesture.NONE
    }

    private fun reapply() {
        val te = touchExploration()
        entries.values.forEach { e -> drop(e); settle(e, te) }
    }

    /** Trạng thái đầu theo luật: ẩn hẳn (alpha 0 + `INVISIBLE`) hoặc hiện hẳn. */
    private fun settle(e: Entry, te: Boolean) {
        e.rest = SlotHeadRest.rest(e.kind, e.projector, enabled, te)
        e.head.animate().cancel()
        val hidden = e.rest == Rest.AUTO_HIDE
        e.head.alpha = if (hidden) 0f else 1f
        e.head.visibility = if (hidden) View.INVISIBLE else View.VISIBLE
        e.cluster?.settle(hidden)
    }

    private fun drop(e: Entry) {
        host.removeCallbacks(e.reveal)
        host.removeCallbacks(e.hide)
        e.head.animate().cancel()
        e.cluster?.cancel()
    }

    private fun reveal(e: Entry) {
        host.removeCallbacks(e.reveal)
        host.removeCallbacks(e.hide)
        if (e.rest != Rest.AUTO_HIDE) return
        e.head.animate().cancel()
        e.head.visibility = View.VISIBLE
        e.head.animate().alpha(1f).setDuration(SlotHeadRest.FADE_IN_MS)
        e.cluster?.show()
        host.postDelayed(e.hide, SlotHeadRest.HIDE_AFTER_MS)
    }

    private fun hide(e: Entry) {
        host.removeCallbacks(e.hide)
        if (e.rest != Rest.AUTO_HIDE) return
        val head = e.head
        head.animate().cancel()
        head.animate().alpha(0f).setDuration(SlotHeadRest.FADE_OUT_MS).withEndAction { head.visibility = View.INVISIBLE }
        e.cluster?.hide()
    }

    /** Mục của ô [i] còn đúng là nút của khung đang hiện (khung dựng lại mà chưa đăng ký ⇒ bỏ qua, không đụng view cũ). */
    private fun valid(i: Int, slots: List<View>): Entry? = entries[i]?.takeIf { it.head.parent === slots.getOrNull(i) }

    /** DOWN có rơi vào KHUNG CHẠM của ⇄ — hoặc của một nút L6 đang làm được — không (toạ độ của workspace). */
    private fun inHit(e: Entry, slot: View, x: Float, y: Float): Boolean {
        val l = slot.left + e.head.left + e.hit.left
        val t = slot.top + e.head.top + e.hit.top
        return (x >= l && x < l + e.hit.width && y >= t && y < t + e.hit.height) ||
            e.cluster?.hits(x - slot.left, y - slot.top) == true
    }

    private fun slotAt(slots: List<View>, x: Float, y: Float): Int =
        slots.indexOfFirst { x >= it.left && x < it.right && y >= it.top && y < it.bottom }

    /** Đường chiếu của ô, đọc từ chính cây view vừa dựng (không lặp lại điều kiện của `makeSlot`). */
    private fun projectorOf(slot: ViewGroup): SlotHeadRest.Projector {
        val kids = (0 until slot.childCount).map { slot.getChildAt(it) }
        return when {
            kids.any { it is VdAppHost } -> SlotHeadRest.Projector.VD
            kids.any { it is SlotAppHost } -> SlotHeadRest.Projector.ACTIVITY_VIEW
            else -> SlotHeadRest.Projector.NONE
        }
    }
}
