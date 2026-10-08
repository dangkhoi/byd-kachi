package com.byd.clusternav.launcher

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.HorizontalScrollView
import android.widget.OverScroller
import android.widget.ScrollView
import com.byd.clusternav.launcher.KachiTheme.dpi
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import com.byd.clusternav.launcher.KachiBars as Bars
import com.byd.clusternav.launcher.KachiSpace as Sp
import com.byd.clusternav.launcher.ShortcutGridFit.Scroll
import com.byd.clusternav.launcher.ShortcutScrollKeep.Wanted

/**
 * ═══ Khung đặt icon của lưới lối tắt `w_apps` theo [ShortcutGridFit] — R-SI1 (2.87) → 2.92 (khe cố định + cuộn) ═══════
 *
 * Spec `docs/specs/kachi-292-shortcut-widget.html` (thay §4.4 của `kachi-287-look-and-keys.html`). Hình học ở `:core`
 * (test thuần), lớp này chỉ ĐẶT và CUỘN:
 *  - mỗi con (icon app của [ShortcutIconsView]) đo ĐÚNG `icon + 2 × nửa khe` mỗi trục, lề trong = nửa khe ⇒ hình vẽ đúng
 *    `iconPx` ở đúng chỗ phép khớp chỉ ra, vùng CHẠM phủ tới giữa khe (≥ 48 dp: sàn icon 40 dp + khe 8 dp);
 *  - khớp lại CHỈ khi (rộng, cao, số icon) đổi — đo trong [onMeasure] (không `onSizeChanged`) để icon đúng cỡ ngay lượt đo
 *    đầu; cỡ icon đổi ⇒ báo [onIconPx] SAU lượt bố trí (`post`: đổi drawable giữa lượt đo/đặt là `requestLayout` lồng);
 *  - 2.92 · R3 — nhiều app tới mức icon chạm sàn ⇒ phép khớp trả nội dung DÀI hơn khung theo trục dài: con đặt theo toạ độ
 *    NỘI DUNG, khung cuộn bằng `scrollTo` của chính nó (ViewGroup tự dời vẽ + chạm của con theo `mScrollX/Y` — không cây
 *    view mới). Kéo bị chặn ở [onInterceptTouchEvent] khi dọc trục vượt `scaledTouchSlop` ⇒ icon đang nhấn nhận `CANCEL`
 *    (không mở nhầm app); thả ⇒ trôi bằng [OverScroller]. `DOWN` rơi vào lề (không icon nào nhận) KHÔNG bị nuốt ⇒ chạm /
 *    nhấn giữ lề vẫn tới ô (chọn nội dung · kéo đổi ô của `WorkspaceView`) như trước. Mép mờ báo "còn nữa" (không thanh
 *    cuộn — màn xe, lẽ `DockAreaLayout.scrollWrap`); trợ năng: cuộn một trang.
 *
 * Mỗi lượt khớp ĐỔI bố cục ghi một dòng `adb logcat -s WidgetFit` (`shortcuts n=… frame=… -> c×r icon=…`) — khung thật trên
 * xe đọc được không cần đoán (CLAUDE.md §15; ca ảnh owner 06/10 không kèm số khung).
 *
 * 2.93 · R1/R2 (spec `kachi-293-slot.html`, `ShortcutScrollKeep`): vị trí cuộn NGƯỜI LÁI CHỌN ([wanted] — chỉ kéo / trôi /
 * trợ năng ghi) tách khỏi vị trí ĐANG ÁP (`scrollX/Y` = [wanted] kẹp theo khung hiện tại, [settleScroll]). Lượt đo ở một khung
 * lạ (QA 2.92: 1558×123 giữa hai lượt 1362×148 ⇒ 264 → 43) chỉ áp, không ghi ⇒ lượt kế ở khung thật trả lại đúng chỗ. Lượt
 * dựng lại của [ShortcutIconsView] chuyển [keep] của khung cũ sang khung mới qua `start`. Dòng nhật ký mang `view=` (mã khung)
 * + `pos=áp/chọn` để chốt lượt đo lạ là cùng khung hay một màn khác ([CHƯA BIẾT] — CLAUDE.md §11).
 */
internal class ShortcutGridLayout(
    context: Context,
    /** 2.93 · R1 — vị trí người lái đã chọn ở khung CŨ (lượt dựng lại của [ShortcutIconsView]); khung mới: [Wanted.ORIGIN]. */
    start: Wanted = Wanted.ORIGIN,
    /** 2.93 wave 2A · SHORTCUT-SCROLL-REBUILD — báo mỗi lựa chọn MỚI của người lái (để nhớ qua lượt dựng view mới của ô). */
    private val onUserScroll: (Wanted) -> Unit = {},
    private val onIconPx: (Int) -> Unit,
) : ViewGroup(context) {

    private var fit: ShortcutGridFit.Fit? = null
    private var reportedIconPx = -1

    /** Bố cục của dòng nhật ký gần nhất — chỉ ghi khi bố cục ĐỔI thật (không theo lượt đo). */
    private var shown: ShortcutGridFit.Fit? = null

    /** 2.93 · R1/R2 — vị trí người lái ĐÃ CHỌN (KDoc lớp); chỉ [scrollAlongTo] ghi. */
    private var wanted: Wanted = start

    /** Vị trí người lái đã chọn — lượt dựng lại của [ShortcutIconsView] đem sang khung mới (R1). */
    val keep: Wanted get() = wanted

    private val touchSlop: Int
    private val minFling: Int
    private val maxFling: Int
    private val scroller = OverScroller(context)

    /** 2.98 · R5 — khung (trục + quãng) lúc phóng cú trôi đang chạy; chỉ [fling] ghi, [computeScroll] đọc. */
    private var flingFrame: ShortcutScrollKeep.Fling = ShortcutScrollKeep.Fling.NONE
    private var velocity: VelocityTracker? = null
    private var dragging = false
    private var lastAlong = 0f
    private var pointer = MotionEvent.INVALID_POINTER_ID

    init {
        val vc = ViewConfiguration.get(context)
        touchSlop = vc.scaledTouchSlop
        minFling = vc.scaledMinimumFlingVelocity
        maxFling = vc.scaledMaximumFlingVelocity
        setFadingEdgeLength(dpi(context, Sp.M))
        // Review 292 Pass 1 [P2]: ViewGroup mặc định WILL_NOT_DRAW ⇒ khung không nền mang PFLAG_SKIP_DRAW ⇒ hệ thống chỉ gọi
        // `dispatchDraw`, KHÔNG BAO GIỜ vào `View.draw(Canvas)` — nơi DUY NHẤT vẽ mép mờ ⇒ cờ mép mờ ở [settleScroll] vô tác
        // dụng [ĐO decompile `framework.jar` máy ảo A10: `ViewGroup.initViewGroup` → `setFlags(128, 128)`;
        // `View.updateDisplayListIfDirty` nhánh `(mPrivateFlags & 128) == 128` ⇒ chỉ `dispatchDraw`]. Cùng lẽ
        // `ScrollView.initScrollView` → `setWillNotDraw(false)` [ĐO cùng bản decompile]. Không cuộn ⇒ mép mờ tắt ⇒ `View.draw`
        // đi nhánh nhanh (nền rỗng, `onDraw` rỗng) — không đổi hình.
        setWillNotDraw(false)
        isVerticalScrollBarEnabled = false
        isHorizontalScrollBarEnabled = false
        overScrollMode = OVER_SCROLL_NEVER
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Lưới LẤP khung được cho (khe chia phần dư). Chỉ khi cha không bó (UNSPECIFIED — không có ở ô widget) mới tự
        // chọn cỡ: một hàng icon cỡ sàn.
        val gap = dpi(context, Bars.SHORTCUT_GRID_GAP)
        val minIcon = dpi(context, Bars.SHORTCUT_GRID_MIN_ICON)
        val natural = minIcon + gap
        val outerW = side(widthMeasureSpec, childCount * natural + gap + paddingLeft + paddingRight)
        val outerH = side(heightMeasureSpec, natural + gap + paddingTop + paddingBottom)
        setMeasuredDimension(outerW, outerH)
        val f = refit(
            (outerW - paddingLeft - paddingRight).coerceAtLeast(0),
            (outerH - paddingTop - paddingBottom).coerceAtLeast(0),
            gap, minIcon,
        )
        val padX = (f.gapXPx / 2).toInt()
        val padY = (f.gapYPx / 2).toInt()
        val ws = MeasureSpec.makeMeasureSpec(f.iconPx + 2 * padX, MeasureSpec.EXACTLY)
        val hs = MeasureSpec.makeMeasureSpec(f.iconPx + 2 * padY, MeasureSpec.EXACTLY)
        for (i in 0 until childCount) getChildAt(i).measure(ws, hs)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val f = fit ?: return
        for (i in 0 until childCount) {
            val c = getChildAt(i)
            val x = paddingLeft + f.left(i) - c.paddingLeft
            val y = paddingTop + f.top(i) - c.paddingTop
            c.layout(x, y, x + c.measuredWidth, y + c.measuredHeight)
        }
    }

    private fun side(spec: Int, natural: Int): Int =
        if (MeasureSpec.getMode(spec) == MeasureSpec.UNSPECIFIED) natural else MeasureSpec.getSize(spec)

    /** Phép khớp cho khung [w] × [h] với số con hiện tại — giữ nguyên kết quả cũ khi cả ba không đổi. */
    private fun refit(w: Int, h: Int, gap: Int, minIcon: Int): ShortcutGridFit.Fit {
        fit?.let { if (it.widthPx == w && it.heightPx == h && it.count == childCount) return it }
        val f = ShortcutGridFit.fit(childCount, w, h, gap, minIcon, dpi(context, Bars.SHORTCUT_GRID_MAX_ICON))
        fit = f
        val padX = (f.gapXPx / 2).toInt()
        val padY = (f.gapYPx / 2).toInt()
        for (i in 0 until childCount) getChildAt(i).setPadding(padX, padY, padX, padY)
        settleScroll(f)
        if (f.iconPx != reportedIconPx) {
            reportedIconPx = f.iconPx
            post { onIconPx(f.iconPx) }
        }
        report(f)
        return f
    }

    /** Trục cuộn đổi / quãng cuộn ngắn lại ⇒ mép mờ đúng trục + vị trí cuộn kẹp vào quãng mới (hết cuộn ⇒ về gốc). */
    private fun settleScroll(f: ShortcutGridFit.Fit) {
        isVerticalFadingEdgeEnabled = f.scroll == Scroll.VERTICAL
        isHorizontalFadingEdgeEnabled = f.scroll == Scroll.HORIZONTAL
        // Review 292 Pass 1 [P3]: khung không bấm/không focus ở chế độ AUTO KHÔNG "quan trọng" với trợ năng ⇒ dịch vụ không xin
        // cả nút không quan trọng sẽ không thấy nút này, mất thao tác cuộn (R3) [ĐO decompile `framework.jar` máy ảo A10:
        // `View.isImportantForAccessibility` — AUTO chỉ khi clickable/longClickable/focusable/có listener/provider/live region].
        // `uiautomator dump` (QA máy ảo) không `--compressed` lấy cả nút không quan trọng nên không lộ được chỗ này [SUY]. Không
        // cuộn ⇒ AUTO như 2.91.
        importantForAccessibility =
            if (f.scroll == Scroll.NONE) IMPORTANT_FOR_ACCESSIBILITY_AUTO else IMPORTANT_FOR_ACCESSIBILITY_YES
        if (f.scroll == Scroll.NONE && !scroller.isFinished) scroller.abortAnimation()
        // 2.93 · R2 — áp [wanted] kẹp theo `f.maxScrollPx` (`ShortcutScrollKeep.applied`), KHÔNG kẹp chính vị trí đang áp: lượt
        // đo ở khung lạ chỉ dời hình, lựa chọn của người lái còn nguyên cho lượt sau (QA 2.92: 264 → 43 vĩnh viễn).
        val at = ShortcutScrollKeep.applied(wanted, f)
        val x = if (f.scroll == Scroll.HORIZONTAL) at else 0
        val y = if (f.scroll == Scroll.VERTICAL) at else 0
        if (x != scrollX || y != scrollY) scrollTo(x, y)
    }

    private fun report(f: ShortcutGridFit.Fit) {
        if (f == shown) return
        shown = f
        Log.i(
            TAG,
            String.format(
                Locale.US, "shortcuts n=%d frame=%dx%d -> %dx%d icon=%d gap=%.1fx%.1f scroll=%s content=%dx%d pos=%d/%d view=%x",
                f.count, f.widthPx, f.heightPx, f.cols, f.rows, f.iconPx, f.gapXPx, f.gapYPx, f.scroll,
                f.contentWidthPx, f.contentHeightPx, position(), wanted.px, System.identityHashCode(this),
            ),
        )
    }

    // ── 2.92 · R3 — cuộn theo trục dài khi nhiều app ──────────────────────────────────────────────────────────────────

    private val axis: Scroll get() = fit?.scroll ?: Scroll.NONE

    private fun along(ev: MotionEvent, index: Int): Float = if (axis == Scroll.HORIZONTAL) ev.getX(index) else ev.getY(index)

    private fun position(): Int = if (axis == Scroll.HORIZONTAL) scrollX else scrollY

    /**
     * Cuộn do NGƯỜI LÁI (kéo · trôi · trợ năng) — chỗ DUY NHẤT ghi [wanted] (2.93 · R2). [fling] khác `null` = một bước của cú trôi
     * (2.98 · R5 `SHORTCUT-FLING-CLAMP`): ÁP vị trí kẹp theo khung đang hiện như cũ, nhưng GHI vị trí kẹp theo khung LÚC PHÓNG
     * (`ShortcutScrollKeep.flung`) — lượt khớp ở khung lạ giữa cú trôi không còn ghi đè lựa chọn bằng vị trí đã kẹp.
     */
    private fun scrollAlongTo(p: Int, fling: ShortcutScrollKeep.Fling? = null) {
        val c = p.coerceIn(0, fit?.maxScrollPx ?: 0)
        if (axis == Scroll.HORIZONTAL) scrollTo(c, 0) else scrollTo(0, c)
        wanted = if (fling == null) ShortcutScrollKeep.userScrolled(axis, c) else ShortcutScrollKeep.flung(fling, p)
        onUserScroll(wanted)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (axis == Scroll.NONE) return false
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pointer = ev.getPointerId(0)
                lastAlong = along(ev, 0)
                velocity?.clear()   // DOWN vào lề trước đó không trả UP về khung ⇒ bỏ chuyển động cũ
                // Chạm khi đang trôi ⇒ dừng trôi và NUỐT cú chạm (không mở app ngoài ý) — cùng luật `ScrollView`.
                dragging = !scroller.isFinished
                if (dragging) {
                    scroller.abortAnimation()
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
                track(ev)
            }
            MotionEvent.ACTION_MOVE -> {
                val i = ev.findPointerIndex(pointer)
                if (i >= 0 && !dragging && abs(along(ev, i) - lastAlong) > touchSlop) {
                    dragging = true
                    lastAlong = along(ev, i)
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
                track(ev)
            }
            MotionEvent.ACTION_POINTER_UP -> secondaryUp(ev)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> endDrag()
        }
        return dragging
    }

    // Khung KHÔNG bao giờ là một cú bấm: chạm mở app thuộc ICON con, chạm lề rơi về ô cha (DOWN không bị nuốt); khung chỉ
    // nhận chuỗi chạm sau khi chính nó chặn để KÉO cuộn ⇒ không có `performClick` nào để gọi.
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (axis == Scroll.NONE) return super.onTouchEvent(ev)
        when (ev.actionMasked) {
            // Chỉ tới đây khi chính khung chặn DOWN (dừng trôi) — DOWN vào lề trả `false` ⇒ ô cha nhận chạm như trước.
            MotionEvent.ACTION_DOWN -> return dragging.also { if (it) track(ev) }
            MotionEvent.ACTION_MOVE -> {
                track(ev)
                val i = ev.findPointerIndex(pointer)
                if (i < 0) return true
                val a = along(ev, i)
                if (!dragging && abs(a - lastAlong) > touchSlop) {
                    dragging = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                    lastAlong = a
                }
                if (dragging) {
                    // Review 292 Pass 1 [P2]: dời theo phần NGUYÊN, phần lẻ dồn sang lượt sau. Bản đầu làm tròn từng lượt rồi đặt
                    // `lastAlong = a` ⇒ vứt phần lẻ mỗi khung [SUY đọc mã]: toạ độ chạm lẻ + kéo chậm < 0,5 px/khung ⇒ nội dung
                    // ĐỨNG YÊN dù ngón đi; 0,5–1 px/khung ⇒ dời 1 px (vượt ngón tới gấp đôi). Màn xe có cho toạ độ lẻ không
                    // [CHƯA BIẾT]. `ScrollView` tránh bằng toạ độ nguyên (`int y = (int) ev.getY(…)`, `mLastMotionY` kiểu int —
                    // [ĐO decompile `framework.jar` máy ảo A10]) — cùng bất biến: tổng dời = tổng ngón đi, lệch < 1 px.
                    val d = (lastAlong - a).toInt()
                    if (d != 0) {
                        scrollAlongTo(position() + d)
                        lastAlong -= d
                    }
                }
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                val i = ev.actionIndex
                pointer = ev.getPointerId(i)
                lastAlong = along(ev, i)
            }
            MotionEvent.ACTION_POINTER_UP -> secondaryUp(ev)
            MotionEvent.ACTION_UP -> {
                if (dragging) fling()
                endDrag()
            }
            MotionEvent.ACTION_CANCEL -> endDrag()
        }
        return true
    }

    private fun track(ev: MotionEvent) {
        (velocity ?: VelocityTracker.obtain().also { velocity = it }).addMovement(ev)
    }

    /** Ngón đang dẫn nhấc lên giữa chừng ⇒ ngón còn lại dẫn tiếp (không nhảy vị trí). */
    private fun secondaryUp(ev: MotionEvent) {
        val i = ev.actionIndex
        if (ev.getPointerId(i) != pointer || ev.pointerCount < 2) return
        val next = if (i == 0) 1 else 0
        pointer = ev.getPointerId(next)
        lastAlong = along(ev, next)
        velocity?.clear()
    }

    private fun fling() {
        val vt = velocity ?: return
        vt.computeCurrentVelocity(1000, maxFling.toFloat())
        val v = -(if (axis == Scroll.HORIZONTAL) vt.getXVelocity(pointer) else vt.getYVelocity(pointer)).roundToInt()
        val max = fit?.maxScrollPx ?: return
        if (abs(v) < minFling || max <= 0) return
        if (axis == Scroll.HORIZONTAL) scroller.fling(scrollX, 0, v, 0, 0, max, 0, 0)
        else scroller.fling(0, scrollY, 0, v, 0, 0, 0, max)
        flingFrame = ShortcutScrollKeep.Fling(axis, max)   // 2.98 · R5 — khung của cú trôi này (biên của bộ trôi)
        postInvalidateOnAnimation()
    }

    private fun endDrag() {
        dragging = false
        pointer = MotionEvent.INVALID_POINTER_ID
        velocity?.recycle()
        velocity = null
    }

    override fun onDetachedFromWindow() {
        endDrag()
        scroller.abortAnimation()
        super.onDetachedFromWindow()
    }

    override fun computeScroll() {
        if (axis == Scroll.NONE || !scroller.computeScrollOffset()) return
        // 2.98 · R5 — trục cuộn đổi giữa cú trôi ⇒ vị trí bộ trôi vô nghĩa ở trục mới: dừng trôi, lựa chọn của trục cũ còn nguyên.
        if (axis != flingFrame.axis) { scroller.abortAnimation(); return }
        scrollAlongTo(if (axis == Scroll.HORIZONTAL) scroller.currX else scroller.currY, flingFrame)
        postInvalidateOnAnimation()
    }

    // Quãng cuộn thật ⇒ `canScroll*` của cha, mép mờ đúng phía (`View.getTopFadingEdgeStrength` đọc ba hàm này).
    override fun computeVerticalScrollRange(): Int =
        if (axis == Scroll.VERTICAL) fit?.contentHeightPx ?: height else super.computeVerticalScrollRange()

    override fun computeHorizontalScrollRange(): Int =
        if (axis == Scroll.HORIZONTAL) fit?.contentWidthPx ?: width else super.computeHorizontalScrollRange()

    override fun getAccessibilityClassName(): CharSequence = when (axis) {
        Scroll.HORIZONTAL -> HorizontalScrollView::class.java.name
        Scroll.VERTICAL -> ScrollView::class.java.name
        Scroll.NONE -> super.getAccessibilityClassName()
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        val max = fit?.maxScrollPx ?: 0
        if (axis == Scroll.NONE || max <= 0) return
        info.isScrollable = true
        val horizontal = axis == Scroll.HORIZONTAL
        if (position() > 0) {
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD)
            if (horizontal) info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_LEFT)
            else info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP)
        }
        if (position() < max) {
            info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD)
            if (horizontal) info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_RIGHT)
            else info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN)
        }
    }

    override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean {
        if (super.performAccessibilityAction(action, arguments)) return true
        if (axis == Scroll.NONE) return false
        val page = if (axis == Scroll.HORIZONTAL) width else height
        val before = position()
        when (action) {
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD,
            android.R.id.accessibilityActionScrollDown, android.R.id.accessibilityActionScrollRight -> scrollAlongTo(before + page)
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD,
            android.R.id.accessibilityActionScrollUp, android.R.id.accessibilityActionScrollLeft -> scrollAlongTo(before - page)
            else -> return false
        }
        return position() != before
    }

    private companion object {
        /** Cùng thẻ với `FitGridLayout` — một lệnh `logcat -s WidgetFit` đọc mọi lượt khớp widget. */
        const val TAG = "WidgetFit"
    }
}
