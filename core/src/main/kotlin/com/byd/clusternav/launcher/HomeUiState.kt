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
    /** Chip nào hiện trên thanh trạng thái (RW0 vùng thứ ba) — nguồn sự thật DUY NHẤT, KHÔNG có bản sao ở View. */
    val topStrip: TopStripConfig = TopStripConfig.DEFAULT,
    val activeProfile: String = DEFAULT_PROFILE,
    val profiles: List<String> = listOf(DEFAULT_PROFILE),
    val themeMode: ThemeMode = ThemeMode.NIGHT,
    val embedded: Boolean = false,
    val carStatus: CarStatus = CarStatus(),
    /**
     * Bố cục TỰ VẼ đang dùng (P9), `null` = dùng bố cục sẵn của [workspace].
     *
     * ## ⚠ [SOÁT P1-1 kiến trúc] Vì sao PHẢI ở đây
     * Trước đây thứ này sống thành **hai bản sao** (một ở màn chính, một trong `WorkspaceView`) với lý do "để không
     * xáo trộn bộ quyết-định-dựng-lại". Hậu quả có thật: một lần xoá bố cục ở bản của màn chính mà khung vẽ vẫn giữ
     * bản riêng ⇒ [ĐO] cấu hình đã xoá mà màn hình **vẫn hiện 6 khung**. Lần đó vá bằng *quy ước* "mọi thay đổi đi
     * qua một hàm", tức là vẫn hai bản sao, chỉ thêm luật con người phải nhớ.
     *
     * Bộ quyết-định-dựng-lại KHÔNG đọc [HomeUiState] (nó nhận [WorkspaceState]) nên đưa vào đây **không** đụng phép
     * chứng minh tương-đương hơn 1000 tổ hợp — lý do tránh né ban đầu không còn đúng.
     */
    val customLayout: GridLayout? = null,
    /**
     * Lựa chọn ĐƠN VỊ của người dùng (R11–R13). Trước đây có **4 bản sao** (màn chính · khung làm việc · thanh nút ·
     * bảng Tuỳ biến) đồng bộ bằng lời gọi tay ⇒ quên một chỗ là hai bề mặt nói hai đơn vị cho cùng một con số.
     */
    val unitPrefs: UnitPrefs = UnitPrefs.DEFAULT,
    /** Lựa chọn HÌNH NỀN (U4). Cùng lý do: state được render thì phải nằm trong nguồn sự thật. */
    val wallpaper: WallpaperPrefs = WallpaperPrefs.DEFAULT,
    /**
     * **Tự mở khi nổ máy** (S1·T4) — cờ cho `KachiAutostart.runBoot`.
     *
     * ## [ĐO] vì sao nó vào state chứ chỉ là một dòng đọc prefs
     * Trước S1 khoá `launcher_autostart` có getter, có setter, **có người đọc thật** (`KachiAutostart.runBoot` gọi
     * `WorkspacePrefs(app).launcherAutostart()` và bỏ cả lượt khởi động nếu tắt) — nhưng **không có nút nào** để
     * người dùng đổi. Nghĩa là một kill-switch đã nối dây đầy đủ mà chủ xe không tới được: đúng họ lỗi *"vẽ được ≠
     * đặt được"* của RW0.
     *
     * Đưa vào [HomeUiState] thay vì cho ô tick tự đọc/ghi prefs, để tầng UI **0 lần** ghi bền trực tiếp (luật kiến
     * trúc đang có) và để mở lại màn Cài đặt là thấy đúng giá trị đang lưu — cùng khuôn mẫu [topStrip] của RW0.
     *
     * Mặc định **BẬT**, khớp `WorkspacePrefs.launcherAutostart()` (`getBoolean(..., true)`): launcher nên tự sẵn
     * sàng. Hai mặc định lệch nhau sẽ làm ô tick nói sai ngay lần mở đầu, trước cả khi có gì được ghi.
     */
    val autostart: Boolean = true,
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
