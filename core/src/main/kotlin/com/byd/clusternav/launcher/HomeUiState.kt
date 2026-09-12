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
    /**
     * U5 · T3 — NGÔN NGỮ launcher. Mặc định [LangMode.AUTO] ("Theo xe", §6 OQ1): xe của owner đặt tiếng Việt nên
     * không ai thấy gì khác, còn người cài trên máy tiếng khác thì nhận đúng tiếng Anh mà không phải đi tìm nút.
     *
     * ## Vì sao nó ở TRONG state chứ không để bộ chọn tự đọc prefs
     * Cùng lý do đã trả giá bốn lần (`customLayout` · `unitPrefs` · `wallpaper` · và chính `themeMode`): thứ gì được
     * **render** thì phải nằm trong nguồn sự thật, không thì bề mặt cấu hình và màn hình lệch nhau và không ai biết.
     * Ở đây `prev.langMode != next.langMode` chính là điều kiện dựng lại màn (`LangHost.changed`) — không có nó trong
     * state thì không có gì để so.
     *
     * ⚠ Đây là **lựa chọn ba cách**, chưa giải nghĩa. Ngôn ngữ THẬT (`Strings.current`) do `LangHost` ở `:app` giải ra
     * và là chỗ ghi DUY NHẤT — [LangMode.resolve] cần locale của máy, tức cần Android, tức không thuộc `:core`.
     */
    val langMode: LangMode = LangMode.AUTO,
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
     * SỔ CẢNH của hồ sơ đang dùng (P7 + P6) — danh sách cảnh + cảnh lúc nổ máy. Xem [SceneBook].
     *
     * Ở trong state vì **màn Cài đặt render nó** (danh sách cảnh, dấu "nổ máy", trạng thái đủ trần). Đúng luật đã trả
     * giá bốn lần: thứ gì được vẽ thì phải nằm trong nguồn sự thật, không thì bề mặt cấu hình và màn hình lệch nhau
     * mà không ai biết.
     *
     * ⚠ Nó **không** tham gia vào phép quyết định dựng lại ô: [WorkspaceRenderPlanner] nhận [WorkspaceState], không
     * nhận [HomeUiState]. Nhờ vậy thêm trường này KHÔNG đụng phép chứng minh tương-đương hơn 1000 tổ hợp đang khoá
     * hành vi của bộ đó — và đó cũng là lý do *áp* một cảnh không thể tự làm app trong ô mở lại (R4): việc áp chỉ
     * ghi `workspace`/`dock`/`customLayout`, rồi bộ quyết định so **nội dung ô** như mọi lần.
     */
    val scenes: SceneBook = SceneBook.EMPTY,
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
    /**
     * ⚠⚠ [SOÁT P0-1] Id widget bên thứ ba đang bị **các hồ sơ tài xế KHÁC** giữ (đọc từ đĩa lúc [WorkspaceRepository.load]).
     *
     * ## Vì sao một trường "dữ liệu của người khác" lại nằm trong state của hồ sơ này
     * Id widget do nền tảng cấp **cho một HOST**, không cho một hồ sơ; nhưng mọi trường khác ở đây là dữ liệu của
     * riêng hồ sơ đang dùng (`WorkspacePrefs` khoá theo `"<hồ sơ>__<hậu tố>"`). Sự lệch đó chính là lỗi: phép "id nào
     * hết dùng" đọc state, nên nó **không thấy** widget của hồ sơ kia và đi xoá chúng. [ĐO] `emulator-5554`: đặt
     * widget ở hồ sơ *Mặc định* (id 654) rồi đổi sang hồ sơ *Vợ* ⇒ 654 mất khỏi host **vĩnh viễn**, quay lại thì ô
     * hiện *"app đã bị gỡ"* trong khi app vẫn còn cài.
     *
     * Chọn cách này (một trường trong state) thay vì thêm tham số cho `AppWidgetIds.orphaned/unused` vì nó chốt bằng
     * **KIỂU**: hai chỗ gọi đã nhận `HomeUiState`, nên không có cách nào hỏi "còn ai dùng" mà bỏ sót vế này. Thêm
     * tham số thì mỗi chỗ gọi mới lại là một chỗ có thể quên — đúng hình dạng đã để lọt lỗi này hai lần.
     *
     * ⚠ **KHÔNG gồm hồ sơ đang dùng.** Hồ sơ đang dùng đã nằm ở [workspace] + [scenes] (bản trong bộ nhớ, luôn mới
     * hơn đĩa). Gộp cả nó vào đây thì ảnh chụp lúc `load()` sẽ **bảo vệ vĩnh viễn** một id mà người dùng vừa bỏ khỏi ô
     * ⇒ id rác sống mãi. Ảnh chụp là đủ vì dữ liệu hồ sơ khác chỉ đổi khi hồ sơ đó **được chọn**, mà lúc đó `load()`
     * chạy lại.
     */
    val widgetIdsOtherProfiles: Set<Int> = emptySet(),
) {
    /** Preset bố cục hiện tại (tiện đọc, uỷ quyền [WorkspaceState.preset]). */
    val preset: LayoutPreset get() = workspace.preset

    /** Nội dung 4 ô hiện tại (tiện đọc, uỷ quyền [WorkspaceState.slots]). */
    val slots: List<SlotContent> get() = workspace.slots

    companion object {
        /**
         * Tên hồ sơ mặc định. Trùng chuỗi với `WorkspacePrefs.DEFAULT_PROFILE` (:app) — giữ literal ở đây để :core
         * không phụ thuộc ngược lên :app; giá trị thật lúc chạy luôn đến từ [WorkspaceRepository.load].
         *
         * ## ⚠⚠ U5 · T2 — CHUỖI NÀY **KHÔNG ĐƯỢC DỊCH**, dù nó có dấu tiếng Việt và hiện ra trên avatar
         * Nó là **KHOÁ LƯU**, không phải nhãn: `WorkspacePrefs` ghi mọi cấu hình theo hồ sơ dưới dạng
         * `"<tên hồ sơ>__<hậu tố>"` (bố cục, thanh nút, chip thanh trên…). Dịch nó thành `"Default"` sẽ làm mọi khoá
         * cũ (`Mặc định__preset`, `Mặc định__slot_0`…) **thành mồ côi** ⇒ người dùng mở launcher lên thấy bố cục về
         * mặc định và tưởng mất hết cấu hình, mà không có gì báo lỗi.
         *
         * Muốn avatar hiện chữ tiếng Anh thì phải là một lớp **trình bày** riêng (map tên-lưu → tên-hiện) ở `:app`,
         * KHÔNG phải đổi giá trị này. Ghi ra đây vì T3 sẽ đi dịch phần `:app` và đây là chỗ dễ dịch nhầm nhất:
         * nó *trông* y như một nhãn.
         */
        const val DEFAULT_PROFILE = "Mặc định"
    }
}
