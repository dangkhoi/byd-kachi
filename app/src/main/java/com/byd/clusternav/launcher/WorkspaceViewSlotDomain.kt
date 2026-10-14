package com.byd.clusternav.launcher

import android.view.View
import android.view.ViewGroup

/**
 * ═══ Lĩnh vực của một ô + bắt đầu kéo ô — hai hàm nhỏ tách khỏi [WorkspaceView] ═══
 *
 * Tách THUẦN khỏi `WorkspaceView.kt` (507 dòng → trần 500, L6-debt 2026-09-27): thân giữ nguyên byte, chỉ đổi `private fun`
 * thành hàm mở rộng `internal` cùng package (cùng khuôn `WorkspaceViewCards.kt`).
 */

// 3.01 R22 — `slotDomain` (sắc lĩnh vực của KHAY) đã XOÁ: khay nay trung tính (brief Kim §2.3); sắc lĩnh vực của thẻ
// tra ở chính bộ dựng widget qua `KachiWidgetType.accentOf` (chip icon đầu thẻ, thanh mức).

internal fun WorkspaceView.startSlotDrag(index: Int, v: View) {
    v.startDragAndDrop(null, View.DragShadowBuilder(v), index, 0)
}

/**
 * 2.93 `WIDGET-CAPACITY-HINT` — cỡ px THẬT của ô [i] (view ô đã đo; `null` = chưa đo / không có ô) cho câu sức chứa của bộ
 * chọn widget. `slotViews` mở `internal` cho đúng phép đọc này (không ghi).
 */
internal fun WorkspaceView.slotFrame(i: Int): Pair<Int, Int>? =
    slotViews.getOrNull(i)?.let { v -> (v.width to v.height).takeIf { it.first > 0 && it.second > 0 } }

/** Bộ chiếu màn ảo của ô [i] (`null` = ô không có) — cho đường đặt tạm ở `WorkspaceViewSwap.kt` (chuyển nguyên văn từ `WorkspaceView.kt`, R30). */
internal fun WorkspaceView.hostAt(i: Int): VdAppHost? = (slotViews.getOrNull(i) as? ViewGroup)?.let { g ->
    (0 until g.childCount).firstNotNullOfOrNull { g.getChildAt(it) as? VdAppHost }
}
