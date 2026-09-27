package com.byd.clusternav.launcher

import android.view.View

/**
 * ═══ Lĩnh vực của một ô + bắt đầu kéo ô — hai hàm nhỏ tách khỏi [WorkspaceView] ═══
 *
 * Tách THUẦN khỏi `WorkspaceView.kt` (507 dòng → trần 500, L6-debt 2026-09-27): thân giữ nguyên byte, chỉ đổi `private fun`
 * thành hàm mở rộng `internal` cùng package (cùng khuôn `WorkspaceViewCards.kt`).
 */

/**
 * Lĩnh vực của nội dung trong ô — để khay mang sắc của chính thứ nó chứa.
 *
 * Tra qua [CapabilityCatalog.pick] (chỗ tra DUY NHẤT, đã phủ cả nhóm lẫn datum lẫn nút — xem
 * `LauncherCatalog.kt`), KHÔNG đoán theo tiền tố mã. Ô App / ô widget bên thứ ba / ô trống ⇒ `null` = khay
 * trung tính: launcher không biết app của người khác thuộc lĩnh vực nào, và mượn đại một sắc là nói sai.
 */
internal fun WorkspaceView.slotDomain(content: SlotContent): Domain? = (content as? SlotContent.Widget)
    ?.ids?.firstOrNull()
    ?.let { CapabilityCatalog.pick(it)?.domain }

internal fun WorkspaceView.startSlotDrag(index: Int, v: View) {
    v.startDragAndDrop(null, View.DragShadowBuilder(v), index, 0)
}
