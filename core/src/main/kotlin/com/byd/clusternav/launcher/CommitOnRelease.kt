package com.byd.clusternav.launcher

/**
 * ═══ 2.88 · R-OP — "ÁP KHI THẢ TAY" của thanh kéo Cài đặt, phần THUẦN (`kachi-287-look-and-keys` §4.1.2 mục 1) ═══
 *
 * `SettingsRows.sliderRow` (`:app`) chỉ chuyển ba sự kiện của `SeekBar` vào đây; mọi quyết định "có áp không, áp số nào"
 * nằm ở lớp này để test được ngoài thiết bị (soát Pass 10 [P3]: trước đây chỉ có bài grep — xoá dòng `tracking = true`
 * là mỗi nấc kéo đều áp, tức ghi prefs theo hồ sơ + `applyThemeInPlace` dựng lại thanh nút tới 20 lần một lượt kéo,
 * mà mọi bài vẫn xanh).
 *
 * Luật [ĐO AOSP r47 `AbsSeekBar.java`, trích ở KDoc `sliderRow`]:
 *  • chạm/kéo bằng tay: `onStartTrackingTouch` → các nấc → `onStopTrackingTouch` (cả khi chạm-không-kéo trong vùng cuộn,
 *    cả khi kéo bị huỷ) ⇒ trong lúc kéo KHÔNG áp, thả tay áp đúng một lần;
 *  • phím / hành động trợ năng: đổi số với `fromUser = true` KHÔNG qua start/stop ⇒ áp ngay;
 *  • đổi số do mã (`fromUser = false`) không bao giờ áp;
 *  • chỉ áp khi vị trí KHÁC lần áp trước (kéo rồi trả về chỗ cũ, hay chạm lại đúng chỗ cũ ⇒ không ghi gì).
 *
 * Mỗi hàm trả vị trí CẦN ÁP, hoặc `null` = không làm gì. Không cấp phát.
 */
class CommitOnRelease(initial: Int) {

    /** Đang trong một lượt chạm (giữa start và stop). */
    var tracking: Boolean = false
        private set

    /** Vị trí đã áp gần nhất (ban đầu = vị trí đang lưu lúc dựng hàng). */
    var committed: Int = initial
        private set

    /** `onStartTrackingTouch`: bắt đầu lượt chạm — chưa áp gì. */
    fun onStart() {
        tracking = true
    }

    /** `onProgressChanged`: chỉ áp khi người dùng đổi số mà KHÔNG trong lượt chạm (phím / trợ năng). */
    fun onChange(pos: Int, fromUser: Boolean): Int? = if (fromUser && !tracking) commit(pos) else null

    /** `onStopTrackingTouch` (thả tay hoặc huỷ): hết lượt chạm, áp vị trí đang đứng. */
    fun onStop(pos: Int): Int? {
        tracking = false
        return commit(pos)
    }

    private fun commit(pos: Int): Int? {
        if (pos == committed) return null
        committed = pos
        return pos
    }
}
