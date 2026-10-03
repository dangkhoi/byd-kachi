package com.byd.clusternav.launcher

/**
 * ═══ L6 · (c) — nút nào nằm ở đầu ô (thuần, `:core`) ═══════════════════════════════════════════════════════════════
 *
 * Owner 03/10: *"Chỗ nút switch app/widget có thể thêm 2 nút, 1 là đẩy app ra chạy nền, 2 là tắt app luôn, để UI trong
 * suốt thấy nền background cho đẹp … Nút cũng tự hide sau 3s"* + *"Ô widget cũng cho tắt đc chứ hả"*. Ô app: [BACKGROUND]
 * [SWAP] [CLOSE] · ô widget: [SWAP] [CLOSE] · ô trống: [SWAP]. Ẩn/hiện theo đúng luật nghỉ của ⇄ ([SlotHeadRest]).
 *
 * ## Nút KHÔNG tồn tại khi việc của nó không làm được (không có nút chết)
 *  - Ô app mà không có màn ảo qua kênh shell ([SlotHeadRest.Projector.VD]) hoặc kênh không dùng được NGAY ⇒ chỉ ⇄:
 *    *tắt* là `am stack remove` trên màn ảo của ô, *chạy nền* là chuỗi BEHIND-HOME — cả hai cần kênh và cần id màn ảo.
 *    Đường ActivityView (ROM ký nền tảng) không đưa id màn ảo ra cho Kachi ⇒ chưa làm ([CHƯA BIẾT] trên ROM đó).
 *  - *Chạy nền* chỉ khi [SlotRevertPlan.backgroundable] (ô có app LƯU khác để đứng trước — KDoc [SlotRevertPlan]).
 *  - Widget: *tắt* không cần kênh (chỉ lớp tạm) ⇒ luôn có, kể cả widget bên thứ ba đã chết.
 *
 * Thứ tự trả về = thứ tự trái → phải trên màn (⇄ ở giữa, như hôm nay).
 */
object SlotHeadActions {

    enum class Button { BACKGROUND, SWAP, CLOSE }

    /** Nút đang làm được của một ô, trái → phải. */
    fun of(kind: SlotHeadRest.Kind, projector: SlotHeadRest.Projector, channel: Boolean, backgroundable: Boolean): List<Button> =
        when (kind) {
            SlotHeadRest.Kind.EMPTY -> listOf(Button.SWAP)
            SlotHeadRest.Kind.WIDGET, SlotHeadRest.Kind.APPWIDGET_LIVE, SlotHeadRest.Kind.APPWIDGET_DEAD ->
                listOf(Button.SWAP, Button.CLOSE)
            SlotHeadRest.Kind.APP -> when {
                projector != SlotHeadRest.Projector.VD || !channel -> listOf(Button.SWAP)
                backgroundable -> listOf(Button.BACKGROUND, Button.SWAP, Button.CLOSE)
                else -> listOf(Button.SWAP, Button.CLOSE)
            }
        }

    /**
     * Nút cạnh ⇄ CÓ THỂ có ở loại ô này (bất kể kênh / lớp tạm lúc này) — tầng vẽ dựng đúng chừng ấy view khi dựng khung;
     * nút ngoài tập này không bao giờ được dựng. ⇄ không thuộc tập (bộ dựng riêng, `SlotSwapButton`).
     */
    fun possible(kind: SlotHeadRest.Kind, projector: SlotHeadRest.Projector): Set<Button> =
        of(kind, projector, channel = true, backgroundable = true).toSet() - Button.SWAP
}
