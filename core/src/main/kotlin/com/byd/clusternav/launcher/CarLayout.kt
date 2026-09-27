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

    /**
     * OQ7 (2.76 · R11) — độ DỊCH DỌC của **khối 4 thẻ lốp**, tính theo phần của chiều cao ảnh xe, sao cho tâm khối
     * thẻ trùng **tâm ảnh** (0,50).
     *
     * [ĐO số học] neo bánh [wheel] y = 0,26 / 0,80 không đối xứng quanh 0,50 (tâm dải = 0,53) ⇒ khối thẻ đặt theo
     * neo thì thấp hơn tâm ảnh `0,03 × Hc` (≈ 16 px ở ô lốp thật 1836×410, Hc ≈ 386 — owner 2026-09-25 (C): *"xe
     * nhô lên so với khối thẻ"*). Dịch KHỐI THẺ chứ không dịch NEO: neo là bánh xe thật (chấm cảnh báo vẫn nằm trên
     * bánh, và bảng cửa / xe mini dùng chung ảnh) — thẻ chỉ cần *nằm cạnh* bánh. Suy từ neo, không gõ `-0.03`:
     * đổi neo bánh là số này tự theo.
     */
    val tyreCardShift: Float
        get() = 0.5f - (wheel(TyreCorner.FRONT_LEFT).y + wheel(TyreCorner.REAR_LEFT).y) / 2f

    /**
     * Tâm dọc (px) của thẻ lốp cho bánh [c] trong ảnh chiếm `[imageTop, imageTop + imageHeight)` — đã cộng
     * [tyreCardShift]. Chấm cảnh báo KHÔNG dùng hàm này (nó ở đúng neo bánh).
     */
    fun tyreCardCenterY(c: TyreCorner, imageTop: Float, imageHeight: Float): Float =
        imageTop + (wheel(c).y + tyreCardShift) * imageHeight

    /** Mực nằm trong khoảng này (px) tính từ biên ảnh thì coi là **chạm mép** — 1 px viền khử răng cưa vẫn chạm. */
    const val HARD_EDGE_PX = 1f

    /**
     * Tỉ lệ MỰC trên một mép từ mức này trở lên ⇒ mép đó là **mép CỨNG của ảnh chữ nhật đặc** (ảnh chụp) — thứ duy
     * nhất mà feather sinh ra để làm mềm. Thấp hơn ⇒ ảnh **cắt nền** (chỉ gương/mũi xe chạm biên): làm mềm ở đó là
     * xoá alpha của chính THÂN XE.
     *
     * 0,95 chứ không phải 1,0 để chừa viền khử răng cưa / góc bo nhẹ của ảnh chụp; ngưỡng cố ý nghiêng về **KHÔNG
     * xoá mực** vì lỗi "mất gương" nặng hơn nhiều lỗi "mép ảnh hơi cứng".
     */
    const val SOLID_EDGE_INK = 0.95f

    /**
     * Dải feather (px) cho MỘT mép của ảnh xe — quyết định bằng SỐ ĐO của chính ảnh, không bằng loại tệp.
     *
     * Feather chỉ có một việc: làm **mép CỨNG của ảnh chữ nhật đặc** tan vào nền thẻ (xem KDoc `CarImageStore`).
     * [edgeInkRatio] = phần của mép đó có mực (`1` = cả mép đặc mực như ảnh chụp; `0` = không gì chạm mép).
     *
     * [ĐO 2026-09-26] vì sao phải đo thay vì áp mù `0.08 × cạnh NGẮN` cho cả 4 mép: ảnh xe top-down cao-hẹp (asset
     * mặc định 678×1397, không có mực nào chạm 4 mép — lề trong suốt 29 px trái/phải = 4,3 % bề rộng) nhận dải 54 px
     * ⇒ dải ăn 25 px vào THÂN xe mỗi bên, gradient `DST_OUT` xoá tới ~50 % alpha ngay tại mép thân ⇒ **gương chiếu
     * hậu mờ nửa**; ảnh cắt nền sát mép (gương đúng biên ảnh) còn bị xoá nặng hơn — mà lề trong suốt của nó = 0 nên
     * đo theo LỀ sẽ kết luận sai là "mép cứng". Đo theo TỈ LỆ MỰC phân biệt được hai ca đó.
     *
     * Trả `0` = KHÔNG feather mép đó (và nếu cả 4 mép đều 0 thì chỗ gọi khỏi tạo bitmap thứ hai).
     */
    fun featherBand(fullBand: Float, edgeInkRatio: Float): Float =
        if (edgeInkRatio >= SOLID_EDGE_INK) maxOf(fullBand, 0f) else 0f
}
