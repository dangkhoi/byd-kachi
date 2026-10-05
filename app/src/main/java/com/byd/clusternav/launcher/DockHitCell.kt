package com.byd.clusternav.launcher

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Rect
import android.graphics.Region
import android.view.Gravity
import android.view.MotionEvent
import android.view.TouchDelegate
import android.view.View
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout

/**
 * ═══ 2.89 · B3 DOCK-SCALE — KHUNG CHẠM của một ô thanh nút ở cỡ ≠ 100 % (`kachi-289-field-fixes` §B3) ═══════════════════
 *
 * Owner: hình đúng số kéo (*"kéo bao nhiêu hiển thị bấy nhiêu"*), nhưng đích chạm không được co theo — ô 50 % chỉ còn
 * 35,5 × 36,5 dp. Cách làm giống hệt `StepTouchTarget` (đã chạy ngoài hiện trường): nới VÙNG NHẬN CHẠM, không nới hình.
 *
 *  • Khung = `max(hình + 2 × lề, sàn 48 dp thật)` dọc trục thanh ([BarScale.cellAlongPx], chỗ gọi đặt `layoutParams`) ×
 *    TRỌN bề dày thanh ngang trục (MATCH_PARENT). Hình ô nằm GIỮA khung, đúng cỡ %.
 *  • Chạm trúng hình ⇒ đi thẳng vào ô như mọi view (ViewGroup giao cho con chứa điểm chạm).
 *  • Chạm vào phần khung NỚI ra ⇒ không con nào chứa điểm ⇒ `ViewGroup` coi khung như view thường (r47
 *    `ViewGroup.java:2740`) ⇒ `View.onTouchEvent` hỏi `TouchDelegate` TRƯỚC mọi thứ (r47 `View.java:14773-14777`) ⇒
 *    [ClampDelegate] quy toạ độ về MÉP hình gần nhất ([BarScale.clampInto]) rồi giao cho ô. Ô STEP (hình nằm giữa khung):
 *    nửa trái khung ⇒ mép trái ⇒ vùng − của `StepTouchTarget`, nửa phải ⇒ +.
 *  • Ngón tay trượt RA NGOÀI khung quá touch-slop ⇒ toạ độ KHÔNG kẹp ⇒ ô thấy điểm ngoài mình ⇒ tự bỏ trạng thái nhấn và
 *    không bấm khi nhấc tay (r47 `View.java:14913, 14932` `pointInView(x, y, touchSlop)`) — kéo qua thanh không bắn lệnh.
 *  • Trợ năng: TalkBack không dùng `onTouchEvent`; khai vùng nới qua [ClampDelegate.getTouchDelegateInfo] (API 29 = minSdk,
 *    cùng lối `StepTouchTarget.SplitDelegate`).
 */
@SuppressLint("ViewConstructor")
internal class DockHitCell(context: Context, val tile: View, tileW: Int, tileH: Int) : FrameLayout(context) {

    init {
        addView(tile, LayoutParams(tileW, tileH, Gravity.CENTER))
        touchDelegate = ClampDelegate(this, tile, ViewConfiguration.get(context).scaledTouchSlop)
    }

    /** Giao mọi cú chạm trong khung cho [tile], toạ độ kẹp vào hình. Dính đích từ `ACTION_DOWN` tới hết cử chỉ. */
    private class ClampDelegate(private val host: View, private val tile: View, private val slop: Int) :
        TouchDelegate(Rect(), tile) {
        private var active = false

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (event.actionMasked == MotionEvent.ACTION_DOWN) active = true else if (!active) return false
            val x = event.x
            val y = event.y
            val near = x >= -slop && y >= -slop && x < host.width + slop && y < host.height + slop
            val lx = if (near) BarScale.clampInto(x - tile.left, tile.width) else x - tile.left
            val ly = if (near) BarScale.clampInto(y - tile.top, tile.height) else y - tile.top
            val copy = MotionEvent.obtain(event).apply { setLocation(lx, ly) }
            val handled = try { tile.dispatchTouchEvent(copy) } finally { copy.recycle() }
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> active = handled   // ô không bấm được (ô ĐỌC) ⇒ không giữ cử chỉ
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> active = false
            }
            return handled
        }

        /** Vùng nới = trọn khung, đích = ô. Dựng theo cỡ HIỆN TẠI (khung đổi cỡ theo bố cục). */
        override fun getTouchDelegateInfo(): AccessibilityNodeInfo.TouchDelegateInfo =
            AccessibilityNodeInfo.TouchDelegateInfo(mapOf(Region(0, 0, host.width, host.height) to tile))
    }
}
