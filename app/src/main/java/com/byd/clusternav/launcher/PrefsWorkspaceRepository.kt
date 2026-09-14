package com.byd.clusternav.launcher

import android.content.Context

/**
 * Bản thật của [WorkspaceRepository] cho :app — bọc [WorkspacePrefs] (SharedPreferences, khoá theo hồ sơ tài xế).
 * Chạm [Context] nên KHÔNG phải "file thuần" (LayeringRules không tính vào `pureFilesStillInApp`).
 *
 * Áp quy tắc bố cục mặc định (3 widget) khi hồ sơ trống — chuyển logic `initialState()` cũ từ Activity vào tầng dữ liệu,
 * để [HomeViewModel] chỉ cần `load()`.
 *
 * ⚠ **Một ngoại lệ có chủ ý cho luật "không ghi bền lúc load"**: lượt `load()` ĐẦU TIÊN của tiến trình áp **cảnh lúc
 * nổ máy** (P7) và ghi bền kết quả. Lý do đầy đủ ở KDoc [load]; tóm lại là áp cảnh mà không ghi thì màn hình và đĩa
 * nói hai chuyện khác nhau. Mọi lượt `load()` sau (đổi/thêm/xoá hồ sơ) **chỉ đọc**, như trước.
 */
class PrefsWorkspaceRepository(context: Context) : WorkspaceRepository {

    private val app = context.applicationContext
    private val prefs = WorkspacePrefs(app)

    /**
     * Lượt [load] ĐẦU TIÊN của tiến trình này đã đi qua chưa — đây là cách nhận ra *"launcher vừa khởi động nguội"*.
     *
     * ## ⚠⚠ Đây là chỗ định nghĩa NGỮ NGHĨA của "cảnh lúc nổ máy" (P7 · R3) — đọc trước khi sửa
     * `load()` còn được gọi lại **giữa phiên** cho `switchProfile`/`addProfile`/`deleteProfile`. Nếu áp cảnh khởi động
     * ở *mọi* lượt `load()` thì **đổi hồ sơ cũng bị áp cảnh** — người dùng bấm sang hồ sơ B và bố cục vừa nạp của B
     * lập tức bị cảnh khởi động của B ghi đè. Cờ này giới hạn việc áp vào đúng **một lần cho mỗi lần tiến trình sống**.
     *
     * `@Volatile` vì `AppContainer` dựng repository một lần rồi dùng từ nhiều thread (Activity trên main;
     * `KachiAutostart.logBootPlan` gọi `load()` trên thread nền của foreground service).
     */
    @Volatile private var coldStartDone = false

    /**
     * Nạp trạng thái đầy đủ của hồ sơ đang chọn, và ở **lượt đầu của tiến trình** thì áp **cảnh lúc nổ máy** nếu có
     * (P7 · R3).
     *
     * ## Ngữ nghĩa đã chọn: áp lúc launcher KHỞI ĐỘNG NGUỘI (một lần cho mỗi lần tiến trình sống)
     * Nói thẳng cái được và cái mất, vì đây là chỗ có thể làm mất công của người dùng:
     *  - **Được**: một cơ chế duy nhất, luôn chạy, và đo được (`force-stop` rồi mở lại ⇒ ra đúng cảnh — T3 của spec).
     *  - **Mất**: cách bố trí **chưa lưu thành cảnh** sẽ bị thay khi tiến trình khởi động lại.
     *
     * ## Vì sao KHÔNG dùng "chỉ khi đúng là máy vừa nổ"
     * Dự án **phân biệt được** thật (`RebindReceiver` nhận `BOOT_COMPLETED` → `KachiAutostartService` →
     * `KachiAutostart.runBoot`), và tôi đã dựng xong đôi dấu `markBootPending`/`consumeBootPending` rồi **bỏ**. [ĐO]
     * đọc mã đường đó cho ba lỗ không vá được ở tầng này:
     *  1. `runBoot` **thoát ngay dòng đầu** khi người dùng tắt `launcher_autostart` ⇒ "cảnh lúc nổ máy" chết theo một
     *     công tắc **không liên quan**, im lặng. Đúng loại phụ thuộc ẩn làm tính năng trông như hỏng.
     *  2. Nó cũng chạy trên `MY_PACKAGE_REPLACED` (cài bản mới) — **không phải** nổ máy ⇒ ngữ nghĩa "chỉ khi nổ máy"
     *     đã sai ngay trong chính đường đóng dấu.
     *  3. Nó có `AutostartGate` (một lượt đang bay + nguội 30 s) ⇒ một chùm trigger lúc boot có thể **bỏ** lượt đóng
     *     dấu ⇒ cảnh khởi động lên *thất thường*. Chạy 8/10 lần khó dùng hơn luôn chạy.
     *
     * Rủi ro còn lại được trả bằng ba thứ **cụ thể**, không bằng lời hứa: hàng cảnh khởi động trong Cài đặt **nói
     * thẳng** hợp đồng này; có đường **bỏ dấu** (chạm lại) mà không phải xoá cảnh; và cảnh chỉ chạm **vùng làm việc** —
     * hình nền, chip thanh trạng thái, đơn vị, sáng/tối, ngôn ngữ, hồ sơ đều KHÔNG đổi (§6 OQ1).
     *
     * ## Vì sao có GHI BỀN ở đây (lớp này vốn "không tự ghi lúc load")
     * Áp cảnh là một thay đổi trạng thái thật. Chỉ đổi trong bộ nhớ mà không ghi thì màn hình và đĩa nói hai chuyện
     * khác nhau — đúng **bẫy hai-bản-sao** dự án đã trả giá bốn lần, và `KachiAutostart.logBootPlan` (đọc `load()` để
     * biết ô nào có app) sẽ thấy dữ liệu cũ.
     *
     * ⚠ Mọi field vẫn nạp **trong chính hàm này** (không tách ra hàm phụ): ba bài canh của dự án
     * (`TopStripWiringContractTest` · `SettingsScreenWiringContractTest` · `GridSeamGuardTest`) đọc **thân của
     * `override fun load()`** để chốt *"nạp cùng một lượt, không nạp riêng ở tầng UI"*. Tách ra hàm phụ làm ba bài đó
     * quét một thân rỗng — chúng đã đỏ đúng lúc tôi thử tách, và đó là hành vi đúng của chúng.
     */
    override fun load(): HomeUiState {
        val base = HomeUiState(
            workspace = defaultIfEmpty(prefs.load()),
            dock = prefs.loadDock(),
            activeProfile = prefs.activeProfile(),
            profiles = prefs.profiles(),
            themeMode = prefs.themeMode(),
            embedded = false,
            // [SOÁT P1-1 kiến trúc] Ba nhóm này nằm ở KHOÁ RIÊNG (không đi qua `persist`) nhưng vẫn phải có mặt trong
            // state ngay từ lượt nạp. Nạp ở đây thì ca **đổi hồ sơ** tự đúng: `switchProfile` gọi lại `load()` nên bố
            // cục tự vẽ của hồ sơ mới được nạp cùng lúc với mọi thứ khác — trước đây phải nhớ nạp lại bằng tay ở tầng
            // UI (và đã từng quên, làm hồ sơ B hiện bố cục của A rồi bấm Lưu là ghi đè mất bố cục của B).
            customLayout = prefs.gridLayout().takeIf { it.frames.isNotEmpty() },
            unitPrefs = prefs.unitPrefs(),
            wallpaper = prefs.wallpaperPrefs(),
            topStrip = prefs.topStrip(),
            // S1·T4: nạp cùng lượt với mọi thứ khác ⇒ mở lại màn Cài đặt là thấy đúng cờ đang lưu (bài học P1-1: nạp
            // bằng tay ở tầng UI thì sẽ có lần quên).
            autostart = prefs.launcherAutostart(),
            // U5·T3: nạp cùng lượt ⇒ bộ chọn ngôn ngữ mở ra là thấy đúng lựa chọn đang lưu. `LangHost` giải nghĩa ra
            // `Strings.current` từ giá trị này (nó cần locale của máy nên không giải được ở `:core`).
            langMode = prefs.langMode(),
            // P7/P6: sổ cảnh nạp cùng lượt ⇒ ca ĐỔI HỒ SƠ tự đúng (mỗi hồ sơ một bộ cảnh + một cảnh khởi động).
            scenes = prefs.sceneBook(),
            // ⚠⚠ [SOÁT P0-1] BẮT BUỘC nạp ở đây, và bắt buộc ở CHÍNH lượt này. Id widget là của HOST (mọi hồ sơ)
            // trong khi mọi trường trên là của riêng hồ sơ đang dùng ⇒ thiếu dòng này thì `AppWidgetIds.used` trả lời
            // hẹp hơn sự thật và lượt thu hồi id đi **xoá vĩnh viễn** widget của hồ sơ khác ([ĐO] emulator: đổi hồ sơ
            // ⇒ id 654 mất khỏi host, quay lại ra thẻ "app đã bị gỡ" dù app còn cài). `switchProfile`/`addProfile`/
            // `deleteProfile` đều gọi lại `load()` nên ảnh chụp này luôn khớp hồ sơ đang dùng.
            widgetIdsOtherProfiles = prefs.widgetIdsOtherProfiles(),
        )
        // Lượt `load()` thứ hai trở đi là ĐỔI/THÊM/XOÁ HỒ SƠ, không phải khởi động ⇒ **không** áp cảnh. Thiếu cờ này
        // thì bấm sang hồ sơ B sẽ bị cảnh khởi động của B ghi đè ngay lên bố cục vừa nạp của B.
        if (coldStartDone) return base
        coldStartDone = true
        val boot = base.scenes.bootScene() ?: return base
        val applied = base.withScene(boot)
        persist(applied)
        setGridLayout(applied.customLayout)   // bố cục ở khoá RIÊNG, không nằm trong persist()
        return applied
    }

    override fun persist(state: HomeUiState) {
        prefs.setActiveProfile(state.activeProfile)
        prefs.save(state.workspace)
        prefs.saveDock(state.dock)
        prefs.setThemeMode(state.themeMode)
        // IA v2 · R3 "một công tắc cho một khái niệm" (docs/specs/kachi-settings-ia-v2.html): màn ClusterNav cũ (nay là
        // màn nâng cao) đọc chủ đề từ store RIÊNG `clusternav_theme` ở attachBaseContext — trước đây hai công tắc độc
        // lập, người dùng chỉnh ở Kachi mà màn kia không đổi. Gương lựa chọn sang store đó NGAY lúc lưu bền, tại chính
        // tầng lưu bền (không phải UI/ViewModel) để mọi đường ghi themeMode (chip Settings, đổi hồ sơ, cảnh) đều gương.
        // Phép ánh xạ DAY/NIGHT/AUTO → light/dark/system là hàm thuần ở :core (ClusterNavSettingsModel), có test.
        //
        // ⚠ [SOÁT SENIOR 2026-09-13] Chỉ ghi khi THẬT SỰ đổi. `persist()` chạy sau **mọi** thay đổi state — kéo
        // thả một ô, gạt một nút trên thanh, đổi một chip — còn `setChoice` là `edit().apply()`, tức mỗi lượt là
        // một lần ghi lại NGUYÊN tệp `clusternav_theme.xml` trên thread nền, cho một giá trị hầu như không bao
        // giờ đổi. `choice()` đọc từ bản đồ trong RAM của SharedPreferences nên phép so này gần như miễn phí.
        val mirrored = com.byd.clusternav.ThemeMode.Choice.fromCode(
            ClusterNavSettingsModel.themeChoiceCode(state.themeMode),
        )
        if (com.byd.clusternav.ThemeMode.choice(app) != mirrored) {
            com.byd.clusternav.ThemeMode.setChoice(app, mirrored)
        }
    }

    override fun switchProfile(name: String): HomeUiState {
        prefs.setActiveProfile(name)
        return load()
    }

    override fun addProfile(name: String): HomeUiState {
        prefs.addProfile(name)
        return load()
    }

    override fun deleteProfile(name: String): HomeUiState {
        prefs.deleteProfile(name)
        return load()
    }

    override fun recentApps(): List<String> = prefs.recentApps()

    override fun touchRecentApp(pkg: String) = prefs.touchRecentApp(pkg)

    override fun unitPrefs(): UnitPrefs = prefs.unitPrefs()

    // Tên tham số KHÔNG đặt là `prefs`: field `prefs` (WorkspacePrefs) sẽ bị che, phải viết `this.prefs` mới đúng —
    // đọc dễ tưởng gọi đệ quy.
    override fun setUnitPrefs(units: UnitPrefs) = prefs.setUnitPrefs(units)

    override fun gridLayout(): GridLayout = prefs.gridLayout()

    override fun setGridLayout(layout: GridLayout?) = prefs.setGridLayout(layout)

    override fun profileLayout(name: String): Pair<LayoutPreset?, Int> = prefs.profileLayout(name)

    override fun topStrip(): TopStripConfig = prefs.topStrip()

    override fun setTopStrip(config: TopStripConfig) = prefs.setTopStrip(config)

    override fun wallpaperPrefs(): WallpaperPrefs = prefs.wallpaperPrefs()

    override fun setWallpaperPrefs(wall: WallpaperPrefs) = prefs.setWallpaperPrefs(wall)

    override fun autostart(): Boolean = prefs.launcherAutostart()

    override fun setAutostart(on: Boolean) = prefs.setLauncherAutostart(on)

    override fun langMode(): LangMode = prefs.langMode()

    override fun setLangMode(mode: LangMode) = prefs.setLangMode(mode)

    override fun sceneBook(): SceneBook = prefs.sceneBook()

    override fun setSceneBook(book: SceneBook) = prefs.setSceneBook(book)

    /** Hồ sơ trống (mọi ô Empty) → bố cục mặc định 3 widget (khớp `initialState()` cũ của KachiHomeActivity). */
    private fun defaultIfEmpty(ws: WorkspaceState): WorkspaceState =
        if (ws.slots.all { it is SlotContent.Empty }) DEFAULT_WORKSPACE else ws

    companion object {
        // Bố cục mặc định nay ở :core (WorkspaceState.DEFAULT) để KIỂM ĐƯỢC off-car. Trước đây nó là danh sách
        // CỨNG 4 phần tử ở đây, nên khi nới trần ô 4 → 6 nó ném lỗi NGAY LÚC NẠP LỚP ⇒ launcher sập ở lần chạy đầu
        // (lúc chưa có cấu hình để nạp). Không test nào bắt được vì lớp này cần Android.
        private val DEFAULT_WORKSPACE = WorkspaceState.DEFAULT
    }
}
