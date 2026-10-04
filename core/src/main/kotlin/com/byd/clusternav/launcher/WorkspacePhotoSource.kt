package com.byd.clusternav.launcher

/**
 * U4(b) — nguồn ảnh cho widget trình chiếu của `WorkspaceView` (`:app`).
 *
 * Tách khỏi `WorkspaceView.kt` (489 dòng, trần 500 — CLAUDE.md §4.1) ở 2.87 · R-AH để có chỗ cho nút ⇄ tự ẩn. Nằm ở `:core`
 * vì nó THUẦN (luật tầng Q1, `LayeringRulesTest.so file thuan…`). Không đổi hành vi: cùng ba trường, cùng phép so;
 * `WorkspaceView.setPhotoSource` vẫn là chỗ quyết định dựng lại (chỉ ô widget).
 *
 * [provider] là HÀM (không phải danh sách) để chỗ gọi quyết định khi nào đọc thư mục: đọc thư mục là I/O, không nên
 * chạy mỗi lần dựng ô.
 */
class WorkspacePhotoSource {
    var provider: () -> List<String> = { emptyList() }
        private set
    var intervalSec: Int = Slideshow.DEFAULT_INTERVAL_SEC
        private set

    /**
     * Nguồn mà widget ĐANG được dựng với — ban đầu đúng [provider] mặc định (rỗng). QA2 04/10 (P3, [ĐO] nhật ký
     * `vi-dock-widgetfit.log` của QA2: mỗi khung ghi `refit#1` HAI lần cách 0,5–1 s): bản cũ khởi `null` ⇒ lượt quét
     * ảnh ĐẦU TIÊN sau `onResume` (`WallpaperController.reload` → `setPhotoSource`) luôn là "đổi", kể cả khi không có
     * ảnh nào ⇒ `rebuildWidgetSlots` dựng lại MỌI ô widget ngay sau lượt dựng đầu — mỗi lần mở máy chạy phép khớp hai lần.
     */
    private var shown: List<String> = emptyList()

    /**
     * Đặt nguồn mới; trả `true` nếu nó THẬT SỰ khác nguồn widget đang dùng.
     *
     * [SOÁT] So theo SỐ LƯỢNG là sai: xoá 1 ảnh rồi thêm 1 ảnh khác ⇒ số lượng y nguyên ⇒ coi như "không đổi" ⇒ widget
     * giữ danh sách CŨ, ảnh vừa xoá vẫn hiện và ảnh mới không bao giờ tới. So theo NỘI DUNG. Nhịp đổi khi KHÔNG có ảnh
     * nào không đổi gì widget thấy (nhịp chỉ có nghĩa khi có ảnh; lần có ảnh sau sẽ là "đổi" và mang nhịp mới).
     */
    fun set(paths: List<String>, intervalSec: Int): Boolean {
        val changed = paths != shown || (paths.isNotEmpty() && intervalSec != this.intervalSec)
        provider = { paths }
        this.intervalSec = intervalSec
        shown = paths
        return changed
    }

    /**
     * Ô widget có ĐỌC nguồn ảnh không — chỉ ô đó phải dựng lại khi nguồn đổi (QA2 P3: bản cũ dựng lại MỌI ô widget, kể
     * cả lưới nút kính không dính gì tới ảnh ⇒ mọi lưới khớp lại lần hai). Người đọc duy nhất: [PHOTO_WIDGET] (ô to + ô
     * nén của `WidgetViews`).
     */
    fun consumes(ids: List<String>): Boolean = PHOTO_WIDGET in ids

    companion object {
        /** Mã widget trình chiếu — bộ dựng duy nhất đọc `WidgetData.photos`. */
        const val PHOTO_WIDGET = "w_photos"
    }
}
