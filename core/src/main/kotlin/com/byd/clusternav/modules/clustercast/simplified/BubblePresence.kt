package com.byd.clusternav.modules.clustercast.simplified

/**
 * ═══ UX-OVERHAUL · WP6 · R6.1 — cửa sổ **nút nổi chiếu cụm** có được dựng hay không ═══════════════════════════
 *
 * Thuần Kotlin (`:core`, cấm `android.*`) ⇒ kiểm off-car. Spec `docs/specs/kachi-ux-overhaul.html` §WP6.
 *
 * ## Vì sao là BA nhánh, không phải một `Boolean`
 * Hai câu hỏi rất dễ bị gộp thành một — *"người dùng có MUỐN thấy nút nổi không"* và *"máy có CHO vẽ lên trên app
 * khác không"* — nhưng ba tổ hợp của chúng đòi ba hành vi khác hẳn nhau, và **mỗi nhánh làm sai là một lỗi thật**:
 *
 *  • [HIDDEN] (người dùng tắt) ⇒ KHÔNG dựng cửa sổ · **KHÔNG xin quyền** · **KHÔNG đứng dịch vụ xuống**.
 *    – xin quyền ở đây = bung màn hệ thống *"cho phép hiển thị trên ứng dụng khác"* cho một thứ owner vừa tắt;
 *    – `stopSelf()` ở đây = **giết luôn bộ tự-chiếu khi nổ máy**, vì dịch vụ nổi là *driver duy nhất* của nó
 *      (R1 · `FloatingBubbleService.dispatchBootAutoStart`) và còn là nơi chạy nhịp giữ-cụm (`repinEscapedCastApps`)
 *      + nhịp áp lại vị trí bong bóng VietMap. Tắt một cái NÚT không được tắt ba tính năng không liên quan —
 *      đó chính là lằn ranh mà owner đặt ra: *"ẩn thì không dựng bubble, nhưng cast vẫn bật được qua cách khác"*.
 *  • [NEEDS_OVERLAY_PERMISSION] (muốn thấy, chưa có quyền) ⇒ xin quyền (một lần mỗi lượt chạy dịch vụ).
 *    Bỏ bước này = nút nổi **không bao giờ hiện mà không nói gì** — đúng loại hỏng im lặng mà dự án cấm.
 *  • [SHOW] ⇒ dựng cửa sổ.
 *
 * ## Điều kiện TIÊN QUYẾT, cố ý nằm NGOÀI hàm này
 * Công tắc CHÍNH *"Bật Cluster Cast"* (`SimpleCastPrefs.castEnabled`) **không** là tham số của [decide]: câu trả
 * lời của nó không phải *"cửa sổ nào"* mà là *"dịch vụ có chạy không"* (`stopSelf` ở cả hai lối vào vòng đời,
 * `FloatingBubbleFirstLaunchContractTest` canh). Nhét nó vào đây sẽ có một nhánh thứ tư mang nghĩa *"đứng xuống"*
 * lẫn giữa ba nhánh đang nói về cửa sổ — và chỗ gọi sẽ phải phân biệt lại bằng tay đúng thứ vừa gộp.
 */
enum class BubblePresence {
    /** Dựng (hoặc giữ) cửa sổ nút nổi. */
    SHOW,

    /** Owner đã tắt nút nổi: gỡ cửa sổ nếu đang có, nhưng dịch vụ **vẫn chạy** (tự-chiếu · giữ-cụm · VietMap). */
    HIDDEN,

    /** Owner muốn thấy nút nổi nhưng chưa cấp quyền vẽ-trên-app-khác ⇒ phải xin, không được im lặng. */
    NEEDS_OVERLAY_PERMISSION,
    ;

    companion object {
        /**
         * [visible] = công tắc *"Hiện nút nổi chiếu cụm"* (mặc định BẬT) · [overlayGranted] =
         * `Settings.canDrawOverlays`.
         *
         * Thứ tự xét là **ý muốn trước, quyền sau**: đảo lại thì máy chưa cấp quyền sẽ ra
         * [NEEDS_OVERLAY_PERMISSION] kể cả khi owner đã tắt nút — tức đi xin quyền cho một thứ vừa bị tắt.
         */
        fun decide(visible: Boolean, overlayGranted: Boolean): BubblePresence = when {
            !visible -> HIDDEN
            overlayGranted -> SHOW
            else -> NEEDS_OVERLAY_PERMISSION
        }
    }
}
