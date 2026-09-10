package com.byd.clusternav.launcher

/**
 * Trạng thái UI TOÀN màn HOME của launcher (Kachi) — MỘT nguồn sự thật duy nhất do [HomeViewModel] (:app) giữ trong
 * `StateFlow<HomeUiState>`. Immutable + copy-based: mọi thay đổi là một [HomeUiState] mới → chảy MỘT chiều xuống view.
 *
 * Đặt ở :core vì thuần Kotlin (chỉ gộp các model :core: [WorkspaceState]/[DockConfig]/[ThemeMode]) — không đụng Android.
 * Điều này cũng giữ guard LayeringRules `pureFilesStillInApp = 0` xanh (file thuần KHÔNG được nằm trong :app).
 *
 * @property workspace bố cục + nội dung 4 ô (preset + slots) — xem [WorkspaceState].
 * @property dock cấu hình thanh điều khiển (viền + danh sách control bật) — xem [DockConfig].
 * @property activeProfile tên hồ sơ tài xế đang chọn.
 * @property profiles danh sách hồ sơ tài xế hiện có.
 * @property themeMode chế độ giao diện sáng/tối (chung mọi hồ sơ).
 * @property embedded cờ RUNTIME: app có đang nhúng vào ô qua VirtualDisplay/ActivityView không (dadb loopback hoặc ROM
 *   platform-signed). KHÔNG bền — do host quyết định lúc chạy. Off-car/emulator không dadb → false.
 * @property carStatus ảnh chụp trạng thái xe LIVE (W1c) — do [CarStatusRepository] phát qua `StateFlow<CarStatus>`,
 *   Activity thu (`repeatOnLifecycle`) rồi bơm vào đây (một chiều) để widget render THEO STATE (KHÔNG đọc port trực
 *   tiếp trong view). KHÔNG bền (runtime; off-car mọi field null ⇒ widget "—"). Mặc định [CarStatus] rỗng.
 */
data class HomeUiState(
    val workspace: WorkspaceState = WorkspaceState(),
    val dock: DockConfig = DockConfig(),
    val activeProfile: String = DEFAULT_PROFILE,
    val profiles: List<String> = listOf(DEFAULT_PROFILE),
    val themeMode: ThemeMode = ThemeMode.NIGHT,
    val embedded: Boolean = false,
    val carStatus: CarStatus = CarStatus(),
) {
    /** Preset bố cục hiện tại (tiện đọc, uỷ quyền [WorkspaceState.preset]). */
    val preset: LayoutPreset get() = workspace.preset

    /** Nội dung 4 ô hiện tại (tiện đọc, uỷ quyền [WorkspaceState.slots]). */
    val slots: List<SlotContent> get() = workspace.slots

    companion object {
        /**
         * Tên hồ sơ mặc định. Trùng chuỗi với `WorkspacePrefs.DEFAULT_PROFILE` (:app) — giữ literal ở đây để :core
         * không phụ thuộc ngược lên :app; giá trị thật lúc chạy luôn đến từ [WorkspaceRepository.load].
         */
        const val DEFAULT_PROFILE = "Mặc định"
    }
}
