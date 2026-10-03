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
    private var shown: List<String>? = null

    /**
     * Đặt nguồn mới; trả `true` nếu nó THẬT SỰ khác nguồn đang hiện.
     *
     * [SOÁT] So theo SỐ LƯỢNG là sai: xoá 1 ảnh rồi thêm 1 ảnh khác ⇒ số lượng y nguyên ⇒ coi như "không đổi" ⇒ widget
     * giữ danh sách CŨ, ảnh vừa xoá vẫn hiện và ảnh mới không bao giờ tới. So theo NỘI DUNG.
     */
    fun set(paths: List<String>, intervalSec: Int): Boolean {
        val changed = paths != shown || intervalSec != this.intervalSec
        provider = { paths }
        this.intervalSec = intervalSec
        shown = paths
        return changed
    }
}
