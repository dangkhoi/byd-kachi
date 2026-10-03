package com.byd.clusternav.launcher

/**
 * ═══ 2.87 · R-AH (spec `docs/specs/kachi-287-look-and-keys.html` §3 R-AH1..3 · §4.2) — nút ⇄ NGHỈ thế nào ═══════════════
 *
 * Owner 03/10: *"cái nút switch chỉnh app / widget vào khung, cho ẩn đi đc ko, khi nào nhấn vô khung đó thì mới hiện ra,
 * kiểu auto hide đi sau mấy giây ấy, cho nó đẹp"* và *"Khung trống cũng cần ẩn luôn, thế mới lòi hình nền lên mới đẹp,
 * nhấn đại vào màn nó lại lòi ra ấy mà"*. Bảng dưới trả lời đúng một câu: **khi không ai chạm, ⇄ của ô này hiện hay ẩn?**
 * Phần đo chạm / hẹn giờ / mờ dần ở `:app` (`SlotHeadAutoHide`) — ở đây chỉ có luật, thuần, test đủ ô.
 *
 * ## Vì sao có ô ép LUÔN HIỆN dù công tắc bật — mỗi dòng là một chỗ KHÔNG nhìn thấy được cú chạm
 * Ẩn ⇄ chỉ an toàn khi chạm vào khung làm nó hiện lại được. Nơi chạm không tới cửa sổ Kachi thì ẩn = mất nút:
 *  - [Projector.ACTIVITY_VIEW] (ROM ký nền tảng, `SlotAppHost`): `ActivityView` đăng ký vùng loại-trừ-chạm nên cú chạm
 *    trong ô đi THẲNG vào màn nhúng, Kachi không thấy [ĐO AOSP r47 `ActivityView.java:390-405`]; và vùng ấy chỉ trừ ra
 *    các con đang nhận được chạm (`ViewGroup.java:7165`) ⇒ ⇄ ẩn còn bị nuốt luôn.
 *  - [Projector.NONE] (chưa có kênh, ROM không cho ActivityView): ô App có bản ⇄ THỨ HAI nổi trên cửa sổ app
 *    (`OverlayHeads`, chỉ cho ô App). Ẩn một bản mà bản kia còn là đúng bệnh *"lúc 1 icon lúc 2 icon"* (2026-09-13).
 *  - `touchExploration` (TalkBack): view `INVISIBLE` rơi khỏi cây trợ năng — người dùng trình đọc màn hình mất nút.
 *
 * [Projector] chỉ có nghĩa với [Kind.APP]: ô widget / ô trống là view thường của Kachi trên MỌI đường, chạm luôn tới
 * `WorkspaceView` (chỉ nội dung ô App mới có thể là màn nhúng).
 *
 * ## Vì sao ô TRỐNG và widget CHẾT cũng ẩn (khác khuyến nghị của bản kiểm kê)
 * Ô trống: owner chốt (spec §7 OQ1). Khung trống không nhận chạm (FIX286 · ES1) nên `WorkspaceView` thấy cú DOWN không
 * ai nhận ⇒ hiện ⇄ ngay — không cần dựng lại "vùng trong suốt bấm được". Widget chết: thẻ của nó tự nói *"Chạm để chọn
 * lại"* và chạm cả ô mở đúng bảng chọn (`WorkspaceView.makeSlot` nhánh `AppWidget` · `deadWidgetCard`) ⇒ ⇄ thừa ở đó.
 *
 * Đơn vị: mili-giây. `:core` không có dp/màu (luật tầng).
 */
object SlotHeadRest {

    /** Trạng thái nghỉ của ⇄: [ALWAYS] = luôn hiện (hành vi ≤ 2.86) · [AUTO_HIDE] = ẩn, chạm khung thì hiện vài giây. */
    enum class Rest { ALWAYS, AUTO_HIDE }

    /** Loại ô, đúng bốn nhánh của `WorkspaceView.makeSlot` (widget bên thứ ba tách sống/chết). */
    enum class Kind { EMPTY, WIDGET, APPWIDGET_LIVE, APPWIDGET_DEAD, APP }

    /** Đường hiện app trong ô App: màn ảo qua dadb · ActivityView (ROM ký nền tảng) · không bộ chiếu (thẻ + ⇄ nổi). */
    enum class Projector { VD, ACTIVITY_VIEW, NONE }

    /**
     * Ẩn lại sau lần hiện cuối (≈ lần nhấc tay cuối) — owner *"auto hide đi sau mấy giây"*; L6 (owner 03/10, kèm hai nút
     * chạy nền / tắt cạnh ⇄): *"Nút cũng tự hide sau 3s"* ⇒ 4 s → 3 s, áp cho MỌI nút đầu ô (một hẹn giờ chung).
     */
    const val HIDE_AFTER_MS = 3_000L

    /** Hiện ra nhanh: người vừa chạm đang chờ thấy nút. */
    const val FADE_IN_MS = 150L

    /** Mờ đi chậm hơn một chút để mắt kịp thấy nút đi đâu. */
    const val FADE_OUT_MS = 250L

    /** Bảng luật (đủ ô ở `SlotHeadRestTest`). Công tắc tắt hoặc TalkBack bật ⇒ luôn hiện, như ≤ 2.86. */
    fun rest(kind: Kind, projector: Projector, autoHideEnabled: Boolean, touchExploration: Boolean): Rest {
        if (!autoHideEnabled || touchExploration) return Rest.ALWAYS
        return when (kind) {
            Kind.APP -> if (projector == Projector.VD) Rest.AUTO_HIDE else Rest.ALWAYS
            Kind.EMPTY, Kind.WIDGET, Kind.APPWIDGET_LIVE, Kind.APPWIDGET_DEAD -> Rest.AUTO_HIDE
        }
    }

    /** Nội dung ô → [Kind]. [liveHost] chỉ có nghĩa với [SlotContent.AppWidget]: nhà cung cấp còn dựng được view không. */
    fun kindOf(content: SlotContent, liveHost: Boolean): Kind = when (content) {
        SlotContent.Empty -> Kind.EMPTY
        is SlotContent.Widget -> Kind.WIDGET
        is SlotContent.AppWidget -> if (liveHost) Kind.APPWIDGET_LIVE else Kind.APPWIDGET_DEAD
        is SlotContent.App -> Kind.APP
    }
}
