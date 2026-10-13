package com.byd.clusternav.launcher

import android.content.Context
import android.view.View

/**
 * 3.01 R22·B — THÂN Ô NHÓM dạng hình (thay lưới đọc + hàng nút chữ). Một hợp đồng cho mọi dạng để [GroupTileView] không có
 * nhánh theo dạng: [refresh] đổ số tại chỗ; [consumed] = ô XEM mà thân này đã tự bày (không vẽ lần hai ở lưới đọc).
 */
internal interface GroupBody {
    val consumed: Set<String>
    fun refresh(m: GroupBoardModel)
}

/**
 * Chọn thân hình cho một model — quyết bằng DỮ LIỆU `:core`, không bằng mã nhóm (CLAUDE.md §7):
 *  • nút có dạng vật × việc trên thân xe ⇒ hình xe mà vật là nút ([ActionMatrixView]);
 *  • nhóm khai [GroupLayout.ICONS] ⇒ icon trên HÌNH XE khi ảnh có điểm cho chúng và đủ chỗ (R24, [CarIconBoardView]), không thì
 *    bảng icon sáng/tắt ([IconBoardView] — dạng lùi bên trong nó);
 *  • còn lại `null` ⇒ đường cũ của [GroupTileView].
 */
internal object GroupBodies {
    fun of(ctx: Context, m: GroupBoardModel, data: WidgetData): View? =
        GroupActionMatrix.of(m)?.let { ActionMatrixView(ctx, it, data) }
            ?: GroupIconBoard.of(m)?.let { CarIconBoardView(ctx, it, data) }
}
