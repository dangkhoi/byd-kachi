package com.byd.clusternav.launcher

/**
 * ═══ WP3-v5 · NEO VỊ TRÍ trên hình xe (thuần `:core`) ════════════════════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-ux-overhaul.html` §WP3-v5. Owner bỏ vector car ⇒ hình xe nay là **ẢNH bitmap**
 * ([CarImageStore]/[CarImageLayer] ở `:app`). Ảnh không mang toạ độ từng bộ phận như path vector cũ, nên chấm
 * trạng thái (cửa/cốp/lốp) cần một **neo chuẩn hoá** — đó là tệp này.
 *
 * ## Vì sao là NEO XẤP XỈ, không phải sơ đồ chính xác
 * Overlay ở đây là **chỉ báo trạng thái** (một chấm màu nói *"cửa này đang mở"*), không phải bản vẽ kỹ thuật.
 * Ảnh xe do người dùng thay được (mỗi ảnh một bố cục), nên toạ độ chính xác là bất khả. Neo top-down chuẩn
 * (đầu ở trên, đuôi ở dưới) đúng cho **mọi** ảnh chụp-từ-trên ở mức "chấm nằm đúng vùng của bộ phận đó". Đây là
 * chỗ owner tinh chỉnh bằng mắt nếu ảnh cụ thể lệch.
 *
 * ## Vì sao ở `:core` (thuần) chứ không nằm trong View
 * Cùng lẽ [GroupBoard]/[TyreBoard]: một bảng dữ liệu **kiểm được off-car** (neo trong [0,1] · trái/phải đối
 * xứng · đầu trên đuôi dưới · không trùng), và **dùng chung** cho ba bảng ([TyreBoardView] · [DoorBoardView] ·
 * [CarMiniView]) thay vì mỗi View chép một bản. KHÔNG có `android.*` ở đây.
 *
 * Hệ quy chiếu: `x` 0 = mép TRÁI, 1 = mép PHẢI; `y` 0 = ĐẦU xe (trên), 1 = ĐUÔI xe (dưới).
 */
data class CarAnchor(val x: Float, val y: Float)

object CarLayout {

    /**
     * Neo của một bộ phận thân xe. Bốn cửa ở hai bên (trước cao hơn sau), cốp ở đuôi, nóc/rèm giữa thân, gương ở
     * đầu bên trái. Đối xứng trái/phải để bảng đọc ra được "cửa nào" bằng vị trí.
     */
    fun part(p: CarPart): CarAnchor = when (p) {
        CarPart.DOOR_LF -> CarAnchor(0.14f, 0.42f)
        CarPart.DOOR_RF -> CarAnchor(0.86f, 0.42f)
        CarPart.DOOR_LR -> CarAnchor(0.14f, 0.60f)
        CarPart.DOOR_RR -> CarAnchor(0.86f, 0.60f)
        CarPart.TAILGATE -> CarAnchor(0.50f, 0.90f)
        CarPart.SUNROOF -> CarAnchor(0.50f, 0.40f)
        CarPart.SUNSHADE -> CarAnchor(0.50f, 0.55f)
    }

    /** Neo của một bánh — bốn góc, hơi thụt vào trong mép (vè xe), trước cao hơn sau. */
    fun wheel(c: TyreCorner): CarAnchor = when (c) {
        TyreCorner.FRONT_LEFT -> CarAnchor(0.17f, 0.26f)
        TyreCorner.FRONT_RIGHT -> CarAnchor(0.83f, 0.26f)
        TyreCorner.REAR_LEFT -> CarAnchor(0.17f, 0.80f)
        TyreCorner.REAR_RIGHT -> CarAnchor(0.83f, 0.80f)
    }
}
