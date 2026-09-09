package com.byd.clusternav.launcher

/**
 * Tầng-dữ-liệu (data-layer seam) cho trạng thái HOME của launcher — ranh giới giữa [HomeViewModel] và nơi lưu bền.
 *
 * Interface THUẦN (:core) nên test được off-car bằng một bản giả in-memory; bản thật `PrefsWorkspaceRepository` (:app)
 * bọc `WorkspacePrefs`/SharedPreferences. Cùng khuôn mẫu Port/Adapter như [AppLauncher]/[CarDataPort] trong :core.
 *
 * Mọi hàm trả/nhận [HomeUiState] (immutable). Ghi bền theo HỒ SƠ đang chọn (khoá prefs scope theo tên hồ sơ).
 */
interface WorkspaceRepository {
    /** Nạp trạng thái đầy đủ của hồ sơ đang chọn (workspace + dock + danh sách hồ sơ + theme). */
    fun load(): HomeUiState

    /** Ghi bền phần lưu-được của [state] (workspace + dock + theme) vào hồ sơ đang chọn. */
    fun persist(state: HomeUiState)

    /** Đổi hồ sơ đang chọn sang [name] rồi trả trạng thái đã nạp lại theo hồ sơ đó. */
    fun switchProfile(name: String): HomeUiState

    /** Thêm hồ sơ [name], đặt làm hồ sơ đang chọn, rồi trả trạng thái đã nạp lại. */
    fun addProfile(name: String): HomeUiState

    /** Xoá hồ sơ [name] (không xoá nếu chỉ còn 1) rồi trả trạng thái đã nạp lại. */
    fun deleteProfile(name: String): HomeUiState
}
