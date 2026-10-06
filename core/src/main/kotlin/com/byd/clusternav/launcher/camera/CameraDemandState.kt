package com.byd.clusternav.launcher.camera

/**
 * ═══ 2.93 · TRẠNG THÁI camera theo yêu cầu của `CameraSignalController` (`:app`; RAM, CHỈ main thread) ══════════════
 *
 * Tách khỏi controller vì trần 500 dòng (CLAUDE.md §4.1) và vì đây là một VAI riêng: *"người lái đang muốn xem camera
 * nào"* — còn controller lo *"cửa sổ + AVMCamera đang treo cái gì"*. THUẦN (không Android) ⇒ ở `:core`, có bài
 * `CameraDemandStateTest`; luật chọn ở [CameraDemand]. Không khoá: chỉ luồng main của controller chạm vào.
 *
 * ## Sống trong RAM, không là pref — chết theo tiến trình là hướng AN TOÀN
 * Cùng lẽ cờ `camera_synth`: một cờ "đang mở camera" lưu bền sẽ sống qua lần nổ máy (BYD giết Kachi mỗi lần tắt máy) và
 * bật một overlay lên màn mà không ai vừa bấm gì. Cửa sổ overlay cũng chết theo tiến trình ⇒ hai thứ không lệch nhau
 * (CLAUDE.md §5: không có state ngoài hệ thống nào để trả lại).
 */
class CameraDemandState {

    /** Camera theo yêu cầu đang BẬT (`null` = không). */
    var current: CameraWhich? = null
        private set

    /** Lệnh theo yêu cầu MỚI HƠN lần xi-nhan bật/đổi bên gần nhất — controller hạ cờ khi xi-nhan bật ([CameraDemand.shown]). */
    var newer: Boolean = false

    private val listeners = LinkedHashSet<(CameraWhich?) -> Unit>()

    /**
     * Áp lệnh [op] khi overlay đang hiện [visible] (camera của phiên đang treo — xi-nhan hay theo yêu cầu; mặc định =
     * camera theo yêu cầu, tức không xi-nhan che); trả camera TRƯỚC lệnh (cho dòng log).
     *
     * [newer] (*"lệnh theo yêu cầu là sự kiện mới nhất"*) chỉ dựng khi lệnh THẬT SỰ nói về camera muốn xem: lệnh MỞ
     * ([CameraDemand.Op.Toggle] · [CameraDemand.Op.Open] — kể cả lượt đưa camera đang bị che lên) hoặc lệnh TẮT đã đổi
     * trạng thái. Lệnh TẮT TRƯỢT (*"tắt cam sau"* khi camera sau không mở) không đổi gì nên không dựng cờ — dựng thì camera
     * theo yêu cầu KHÁC đang bị xi-nhan che nhảy lên ĐÈ camera xi-nhan, tức một câu TẮT làm mất camera điểm mù giữa lúc
     * rẽ (soát senior 2.93 [P1], bài `CameraDemandStateTest.lenh tat truot …`).
     */
    fun apply(op: CameraDemand.Op, visible: CameraWhich? = current): CameraWhich? {
        val before = current
        current = CameraDemand.next(before, op, visible)
        if (op is CameraDemand.Op.Toggle || op is CameraDemand.Op.Open || current != before) newer = true
        return before
    }

    /** Camera nên hiện khi camera xi-nhan đang giữ là [blinker] (`null` = không xi-nhan). */
    fun shown(blinker: CameraWhich?): CameraWhich? = CameraDemand.shown(blinker, current, newer)

    /**
     * 2.93 wave 2C · CAM-D6-SAME-CAMERA-EDGE — đã HẸN dựng lại khung lúc xi-nhan nhả (hồ sơ đổi trong khi xi-nhan giữ ĐÚNG
     * camera theo yêu cầu đang hiện — [CameraDemand.profileReapply]). RAM, chỉ main — cùng lẽ [current].
     */
    var reapplyAtRelease: Boolean = false
        private set

    /** Lượt đổi hồ sơ (xi-nhan giữ [blinker], khung đang hiện [showing]): `true` ⇒ dựng lại NGAY; HẸN ⇒ dựng cờ, trả `false`. */
    fun onProfileSwitched(blinker: CameraWhich?, showing: CameraWhich?): Boolean =
        when (CameraDemand.profileReapply(blinker, current, showing)) {
            CameraDemand.ProfileReapply.NOW -> true
            CameraDemand.ProfileReapply.AT_RELEASE -> { reapplyAtRelease = true; false }
            CameraDemand.ProfileReapply.NONE -> false
        }

    /** Xi-nhan vừa nhả: còn hẹn dựng lại không — đọc + XOÁ cờ (đúng một lần). */
    fun takeReapplyAtRelease(): Boolean = reapplyAtRelease.also { reapplyAtRelease = false }

    /** Một phiên MỚI vừa dựng (đọc cấu hình tươi của hồ sơ hiện hành) ⇒ lời hẹn hết nghĩa — không dựng lại lần hai vô cớ. */
    fun sessionOpened() {
        reapplyAtRelease = false
    }

    /** Đăng ký nghe đổi; trả hàm GỠ (chỗ gọi gỡ khi view rời cửa sổ). */
    fun listen(l: (CameraWhich?) -> Unit): () -> Unit {
        listeners.add(l)
        return { listeners.remove(l) }
    }

    /** Báo mọi người nghe (bản chụp danh sách: người nghe được phép tự gỡ trong lúc nhận). Một người nghe ném không chặn ai. */
    fun notifyChanged() = listeners.toList().forEach { runCatching { it(current) } }
}
