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
 *    *tắt* là `am stack remove` trên màn ảo của ô, *chạy nền* là chuỗi lớp che + BEHIND-HOME trên màn ảo của ô — cả hai
 *    cần kênh và cần id màn ảo. Đường ActivityView (ROM ký nền tảng) không đưa id màn ảo ra cho Kachi ⇒ chưa làm
 *    ([CHƯA BIẾT] trên ROM đó).
 *  - L8 (owner 03/10: nút chạy nền phải làm được ở MỌI ô app): *chạy nền* KHÔNG còn đòi ô có app LƯU khác (D-L6-1 mở khoá —
 *    lớp che của Kachi đứng trước app trong màn ảo ô, `BehindHomeSequence.evictCovered`). App hệ thống vẫn bị từ chối chạy
 *    nền (R0.6, an toàn — tiền lệ CarPlay `move-task` làm sập surfaceflinger): nút vẫn có, chạm nói lý do, 0 lệnh (cùng cách
 *    chip *Chạy nền* của Cài đặt — L4 · D5).
 *  - Widget: *tắt* không cần kênh (chỉ lớp tạm) ⇒ luôn có, kể cả widget bên thứ ba đã chết.
 *  - Soát 2.87 · P3 — BEHIND-HOME bị TẮT trong tiến trình ([behind] = `false`: một PHÉP ĐO `ANCHOR_IN_FRONT` đặt
 *    `BehindHomeRunner.disabledReason` tới lần khởi động sau) ⇒ mọi lượt *chạy nền* trả `DISABLED`, 0 lệnh ⇒ nút *chạy nền*
 *    KHÔNG có (*tắt* vẫn có — nó không đi qua BEHIND-HOME).
 *
 * Thứ tự trả về = thứ tự trái → phải trên màn (⇄ ở giữa, như hôm nay).
 */
object SlotHeadActions {

    enum class Button { BACKGROUND, SWAP, CLOSE }

    /** Nút đang làm được của một ô, trái → phải. [behind] = BEHIND-HOME còn dùng được trong tiến trình này. */
    fun of(kind: SlotHeadRest.Kind, projector: SlotHeadRest.Projector, channel: Boolean, behind: Boolean): List<Button> =
        when (kind) {
            SlotHeadRest.Kind.EMPTY -> listOf(Button.SWAP)
            SlotHeadRest.Kind.WIDGET, SlotHeadRest.Kind.APPWIDGET_LIVE, SlotHeadRest.Kind.APPWIDGET_DEAD ->
                listOf(Button.SWAP, Button.CLOSE)
            SlotHeadRest.Kind.APP -> when {
                projector != SlotHeadRest.Projector.VD || !channel -> listOf(Button.SWAP)
                !behind -> listOf(Button.SWAP, Button.CLOSE)
                else -> listOf(Button.BACKGROUND, Button.SWAP, Button.CLOSE)
            }
        }

    /**
     * Nút cạnh ⇄ CÓ THỂ có ở loại ô này (bất kể kênh lúc này) — tầng vẽ dựng đúng chừng ấy view khi dựng khung; nút ngoài
     * tập này không bao giờ được dựng. ⇄ không thuộc tập (bộ dựng riêng, `SlotSwapButton`).
     */
    fun possible(kind: SlotHeadRest.Kind, projector: SlotHeadRest.Projector): Set<Button> =
        of(kind, projector, channel = true, behind = true).toSet() - Button.SWAP
}
