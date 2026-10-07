package com.byd.clusternav.launcher

/**
 * 2.96 WALL-RESCAN — quét lại thư mục ảnh nền theo nhịp, phần QUYẾT ĐỊNH thuần (`:core`).
 *
 * [ĐO máy ảo 07/10] bật *Dùng ảnh làm hình nền* TRƯỚC rồi mới chép ảnh vào ⇒ nền không đổi cho tới khi màn chính được đưa ra
 * lại (`onResume` → `WallpaperController.reload`) — mà màn chính có app trong ô thì đứng yên phía trước cả chuyến. Trên xe đúng
 * đường người dùng làm: cắm USB / chép ảnh khi Kachi đang mở. Nay nhịp 10 s có sẵn của thanh trên (`WallpaperController.step`)
 * quét lại: thư mục còn TRỐNG ⇒ mỗi nhịp; đã có ảnh ⇒ mỗi [TICKS_WITH_PHOTOS] nhịp (thêm/bớt ảnh cũng theo kịp, không dồn I/O).
 */
object WallpaperRescan {

    /** Đã có ảnh ⇒ quét lại sau chừng này nhịp (6 × 10 s = 60 s). */
    const val TICKS_WITH_PHOTOS = 6

    /** Kết quả so danh sách mới với danh sách đang chiếu. */
    enum class Outcome {
        /** Không đổi gì ⇒ không làm gì. */
        SAME,
        /** Trước trống, nay có ảnh ⇒ chiếu NGAY từ ảnh đầu. */
        FIRST,
        /** Có ảnh trước và sau nhưng danh sách khác ⇒ thay danh sách, trình chiếu đi tiếp (chỉ số ngoài phạm vi tự về 0). */
        CHANGED,
        /** Trước có ảnh, nay trống ⇒ về nền mặc định. */
        EMPTIED,
    }

    /** Nhịp này có quét không — [ticksSinceScan] đã gồm nhịp hiện tại. */
    fun due(ticksSinceScan: Int, haveImages: Boolean): Boolean = !haveImages || ticksSinceScan >= TICKS_WITH_PHOTOS

    fun outcome(old: List<String>, new: List<String>): Outcome = when {
        old == new -> Outcome.SAME
        old.isEmpty() -> Outcome.FIRST
        new.isEmpty() -> Outcome.EMPTIED
        else -> Outcome.CHANGED
    }
}
