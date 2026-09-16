package com.byd.clusternav.launcher

import android.content.Context

/**
 * Bản thật của [WorkspaceRepository] cho :app — bọc [WorkspacePrefs] (SharedPreferences, khoá theo hồ sơ tài xế).
 * Chạm [Context] nên KHÔNG phải "file thuần" (LayeringRules không tính vào `pureFilesStillInApp`).
 *
 * Áp quy tắc bố cục mặc định (3 widget) khi hồ sơ trống — chuyển logic `initialState()` cũ từ Activity vào tầng dữ liệu,
 * để [HomeViewModel] chỉ cần `load()`.
 *
 * ⚠ **Một ngoại lệ có chủ ý cho luật "không ghi bền lúc load"**: lượt `load()` ĐẦU TIÊN của tiến trình áp **hồ sơ lúc
 * nổ máy** (S4 · R6, thay cho "cảnh lúc nổ máy" của P7) — mà áp một hồ sơ **là** một lượt `switchProfile` thật, có
 * chụp–áp. Lý do đầy đủ ở KDoc [load]. Mọi lượt `load()` sau (đổi/thêm/xoá hồ sơ) **chỉ đọc**, như trước.
 */
class PrefsWorkspaceRepository(context: Context) : WorkspaceRepository {

    private val app = context.applicationContext
    private val prefs = WorkspacePrefs(app)

    /**
     * Cầu sang các **applier** của ClusterNav — dùng cho ĐÚNG một việc: [reapplyAll] sau lượt đổi hồ sơ (R5).
     *
     * ## Vì sao dựng một cầu ở tầng dữ liệu, chứ không nhận cầu của màn hình
     * [ClusterNavBridge] là **facade không trạng thái** trên `Prefs`/`SimpleCastRuntime` (đều process-singleton), và
     * mỗi Activity vốn đã tự dựng một cái (`Activity.clusterNavBridge()`), nên "một instance duy nhất" chưa bao giờ là
     * bất biến của nó — *một đường đi* mới là. Nhận cầu từ ngoài vào thì lượt đổi hồ sơ **chỉ áp được khi có màn hình
     * đang mở**, mà đường khởi động nguội ([KachiAutostart]) đổi hồ sơ lúc chưa có Activity nào.
     *
     * `toast` rỗng là **cố ý**: đây là lượt áp lại **im lặng** của một hành động người dùng đã thấy kết quả (họ vừa
     * chọn hồ sơ). Bắn 6–8 toast "đã bật dẫn đường"/"đang cấp quyền" cho một cú chạm là làm người lái phải đọc.
     */
    private val bridge: ClusterNavBridge by lazy {
        ClusterNavBridge(app, toast = {}, ui = { r -> android.os.Handler(android.os.Looper.getMainLooper()).post(r) })
    }

    /**
     * ⚠⚠ S4 · R2 — lượt chuyển **cảnh → hồ sơ** chạy ở `init`, tức **trước** lượt [load] đầu tiên của tiến trình.
     *
     * Thứ tự này không phải cho gọn: lượt dọn rác widget bên thứ ba (`AppWidgetIds.orphaned` →
     * `AppWidgetSlotHost.reclaim`) chạy ở nhịp render đầu tiên, và trước khi chuyển đổi xong thì id widget của một
     * cảnh **chỉ còn nằm trong chuỗi `<hồ sơ>__scenes`**. Để lượt dọn đi trước là để chính lượt nâng cấp **xoá vĩnh
     * viễn** widget của người dùng — xem KDoc [WorkspacePrefs.migrateScenesOnce] và `AppWidgetIds.idsInLegacyScenes`.
     */
    init {
        prefs.migrateScenesOnce()
    }

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
            // `.sanitized()`: chữa state cũ đã lưu cùng app ở hai ô (trước bản vá MỘT-APP-MỘT-Ô 2026-09-15) **và**
            // bỏ mã khả năng đã biến mất khỏi mọi bộ đăng ký (vd lượt ADAS-PURGE 2026-09-16) — nạp thẳng qua
            // constructor không đi qua withSlot nên phải ép bất biến ở đây, nếu không ô trùng / ô rác vẫn hiện.
            workspace = defaultIfEmpty(prefs.load().let { raw ->
                // Nói ra thứ vừa bỏ: ô của người dùng biến mất mà không có một dòng nào là kênh im lặng.
                raw.unknownWidgetIds().takeIf { it.isNotEmpty() }?.let {
                    android.util.Log.i(
                        "KachiWorkspace",
                        "[dọn ô] bỏ ${it.size} mã widget không còn trong bộ đăng ký: ${it.joinToString(" ")}",
                    )
                }
                raw.sanitized()
            }),
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
            // Sổ địa chỉ (spec `kachi-voice-addresses.html` R1): nạp CÙNG lượt vì hai chỗ đọc nó — bảng Cài đặt
            // (vẽ danh sách) và đường lệnh giọng nói (tra sổ lúc thi hành) — đều đọc `HomeUiState`. Nạp ở đây thì
            // ca **đổi hồ sơ** tự đúng: `switchProfile` gọi lại `load()` nên sổ của hồ sơ mới về cùng lúc với mọi
            // thứ khác, không phải nhớ nạp lại bằng tay ở tầng UI (bài học [SOÁT P1-1]).
            savedPlaces = prefs.savedPlaces(),
            topStrip = prefs.topStrip(),
            // S1·T4: nạp cùng lượt với mọi thứ khác ⇒ mở lại màn Cài đặt là thấy đúng cờ đang lưu (bài học P1-1: nạp
            // bằng tay ở tầng UI thì sẽ có lần quên).
            autostart = prefs.launcherAutostart(),
            // U5·T3: nạp cùng lượt ⇒ bộ chọn ngôn ngữ mở ra là thấy đúng lựa chọn đang lưu. `LangHost` giải nghĩa ra
            // `Strings.current` từ giá trị này (nó cần locale của máy nên không giải được ở `:core`).
            langMode = prefs.langMode(),
            // S4 · R6: hồ sơ lúc nổ máy nạp cùng lượt ⇒ nhóm Hồ sơ ở Cài đặt vẽ đúng chip đang chọn mà không phải
            // mở một đường đọc bền thứ hai ở tầng UI. Theo XE nên nó KHÔNG đổi khi đổi hồ sơ — nạp lại vẫn đúng.
            bootProfile = prefs.bootProfile(),
            // ⚠⚠ [SOÁT P0-1] BẮT BUỘC nạp ở đây, và bắt buộc ở CHÍNH lượt này. Id widget là của HOST (mọi hồ sơ)
            // trong khi mọi trường trên là của riêng hồ sơ đang dùng ⇒ thiếu dòng này thì `AppWidgetIds.used` trả lời
            // hẹp hơn sự thật và lượt thu hồi id đi **xoá vĩnh viễn** widget của hồ sơ khác ([ĐO] emulator: đổi hồ sơ
            // ⇒ id 654 mất khỏi host, quay lại ra thẻ "app đã bị gỡ" dù app còn cài). `switchProfile`/`addProfile`/
            // `deleteProfile` đều gọi lại `load()` nên ảnh chụp này luôn khớp hồ sơ đang dùng.
            widgetIdsOtherProfiles = prefs.widgetIdsOtherProfiles(),
        )
        // Lượt `load()` thứ hai trở đi là ĐỔI/THÊM/XOÁ HỒ SƠ, không phải khởi động ⇒ **không** áp lại hồ sơ nổ máy.
        // Thiếu cờ này thì bấm sang hồ sơ B sẽ bị hồ sơ nổ máy kéo ngược về ngay lập tức — một nút không bấm được.
        if (coldStartDone) return base
        coldStartDone = true
        // Đã đang ở đúng hồ sơ đó ⇒ không làm gì: `switchProfile` sang chính nó vẫn kéo theo một lượt chụp–áp +
        // `reapplyAll` (R5), tức mỗi lần mở launcher lại đập lại toàn bộ cấu hình ClusterNav mà không được gì.
        val boot = base.bootProfile ?: return base
        if (boot == base.activeProfile) return base
        return switchProfile(boot)
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

    /**
     * ═══ S4 · R5 — ĐỔI HỒ SƠ = **CHỤP A → ĐẶT CON TRỎ → ÁP B → GỌI LẠI APPLIER**, đúng thứ tự đó ═══════════════
     *
     * Bốn bước, và **thứ tự là một phần của hợp đồng**:
     *  1. **chụp A** — giá trị ClusterNav đang nằm trên đĩa là của hồ sơ A. Chụp SAU khi đổi con trỏ thì ảnh của A bị
     *     ghi vào ô của B; chụp thiếu thì mọi thứ A vừa chỉnh biến mất ngay lần quay lại đầu tiên.
     *  2. **đặt con trỏ** — mọi khoá theo hồ sơ đọc qua `key()` nên bước này phải đứng trước bước 3 và 4.
     *  3. **áp B** — ghi ảnh của B vào đúng tệp prefs dịch vụ đang đọc; B chưa có ảnh ⇒ **giữ nguyên** (hồ sơ mới =
     *     bản sao của hiện tại, R5).
     *  4. **gọi lại applier** — [ĐO] **không một dịch vụ nào** trong dự án đăng ký
     *     `registerOnSharedPreferenceChangeListener` (grep toàn `app/src/main`, 0 kết quả), nên ghi prefs xong là
     *     xong *trên đĩa* mà **không có gì đang chạy biết**. [ClusterNavBridge.reapplyAll] gọi đúng các applier đã
     *     có — không dựng cơ chế thứ hai (CLAUDE.md §6: không đảo thứ tự/cơ chế đang chạy tốt trên xe).
     *
     * Ngôn ngữ phát lại ở [WorkspacePrefs.broadcastLang]: bản theo hồ sơ là nguồn, `clusternav_lang` là bản phát cho
     * `attachBaseContext` của màn ClusterNav — thiếu bước này thì hồ sơ dùng English mà màn kia vẫn tiếng Việt.
     */
    override fun switchProfile(name: String): HomeUiState {
        prefs.snapshotClusterNav(prefs.activeProfile())
        prefs.setActiveProfile(name)
        prefs.applyClusterNav(name)
        prefs.broadcastLang()
        bridge.reapplyAll()
        return load()
    }

    /** S4 · R8 — hồ sơ mới là **bản sao** của hồ sơ đang dùng (chép mọi hậu tố + ảnh chụp ClusterNav). */
    override fun duplicateProfile(name: String): HomeUiState {
        prefs.duplicateActiveProfile(name)
        return load()
    }

    override fun bootProfile(): String? = prefs.bootProfile()

    override fun setBootProfile(name: String?) = prefs.setBootProfile(name)

    override fun addProfile(name: String): HomeUiState {
        prefs.addProfile(name)
        return load()
    }

    override fun deleteProfile(name: String): HomeUiState {
        prefs.deleteProfile(name)
        return load()
    }

    /**
     * V3 · R13 — đổi tên hồ sơ. Phép DỜI khoá + ba con trỏ nằm ở `WorkspacePrefs.renameProfile`, phép kiểm thuần
     * ở `ProfileRename` (`:core`) — ở đây chỉ uỷ quyền rồi nạp lại, đúng khuôn [addProfile]/[deleteProfile].
     *
     * Bị từ chối (tên rỗng/trùng) ⇒ `load()` trả nguyên trạng và màn Cài đặt tự thấy tên không đổi; câu giải
     * thích do tầng vẽ nói, vì chỉ nó mới biết đang hỏi tên ở hộp thoại nào.
     */
    override fun renameProfile(old: String, new: String): HomeUiState {
        prefs.renameProfile(old, new)
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

    override fun savedPlaces(): List<SavedPlace> = prefs.savedPlaces()

    override fun setSavedPlaces(places: List<SavedPlace>) = prefs.setSavedPlaces(places)

    override fun wallpaperPrefs(): WallpaperPrefs = prefs.wallpaperPrefs()

    override fun setWallpaperPrefs(wall: WallpaperPrefs) = prefs.setWallpaperPrefs(wall)

    override fun autostart(): Boolean = prefs.launcherAutostart()

    override fun setAutostart(on: Boolean) = prefs.setLauncherAutostart(on)

    override fun langMode(): LangMode = prefs.langMode()

    override fun setLangMode(mode: LangMode) = prefs.setLangMode(mode)

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
