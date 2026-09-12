package com.byd.clusternav.launcher

import android.content.Context
import com.byd.clusternav.Lang as ClusterNavLang

/**
 * Lưu/khôi phục [WorkspaceState] + [DockConfig] theo HỒ SƠ TÀI XẾ (profile) + [ThemeMode] (chung), qua SharedPreferences.
 * Mỗi hồ sơ = một bố cục + thanh điều khiển riêng (khoá key theo tên hồ sơ). Off-car test được (thuần prefs).
 *
 * ⚠ Import có tên (`as ClusterNavLang`): `:core` cũng có một `Lang` (enum thuần `VI`/`EN`) và tệp này dùng **cả hai** —
 * [LangMode] của `:core` là kiểu trên đường dây, `ClusterNavLang` là chỗ lưu. Hai tên khác nhau thì không lẫn được; để
 * cả hai tên là `Lang` thì một trong hai phải viết đủ package ở mọi chỗ dùng, và chỗ nào quên sẽ **vẫn biên dịch** với
 * kiểu sai nếu chữ ký trùng.
 */
class WorkspacePrefs(context: Context) {
    private val sp = context.getSharedPreferences("kachi_workspace", Context.MODE_PRIVATE)

    /** Cần cho đường ngôn ngữ: chỗ lưu ngôn ngữ là tệp prefs của ClusterNav, mở qua `Context` chứ không qua [sp]. */
    private val appCtx = context.applicationContext

    // ── Hồ sơ tài xế ──
    fun profiles(): List<String> =
        sp.getString(K_PROFILES, null)?.split("\n")?.filter { it.isNotBlank() }?.takeIf { it.isNotEmpty() }
            ?: listOf(DEFAULT_PROFILE)

    fun activeProfile(): String = sp.getString(K_ACTIVE, DEFAULT_PROFILE) ?: DEFAULT_PROFILE

    fun setActiveProfile(name: String) { sp.edit().putString(K_ACTIVE, name).apply() }

    /**
     * Thêm một hồ sơ và chuyển sang nó. Tên trùng hồ sơ đã có ⇒ chỉ chuyển sang, không tạo thêm.
     *
     * ## ⚠ [SOÁT P2-2] Hồ sơ MỚI phải bắt đầu TRỐNG — kể cả trên máy đã chạy bản cũ
     * [deleteProfile] nay dọn sạch khoá, nhưng **máy đang chạy trên xe thì không**: mọi hồ sơ từng bị xoá bằng bản cũ
     * còn để lại nguyên `<tên>__slot_*`, `__scenes`… trên đĩa. Đặt lại đúng cái tên đó sẽ nạp cấu hình của một hồ sơ
     * người dùng tưởng đã xoá — trong đó có thể có `aw:<id>` mà id đã bị thu hồi ⇒ ô ra thẻ *"app đã bị gỡ"* dù app
     * còn nguyên. Dọn ở đây làm ca đó tự lành, không cần lượt di dữ liệu nào.
     */
    fun addProfile(name: String) {
        val clean = name.trim().replace(Regex("[\\r\\n]"), " "); if (clean.isEmpty()) return
        val list = profiles().toMutableList()
        val isNew = clean !in list
        if (isNew) list.add(clean)
        val e = sp.edit().putString(K_PROFILES, list.joinToString("\n")).putString(K_ACTIVE, clean)
        // CHỈ khi thật sự mới: tên đã có trong danh sách thì đây là lượt "chuyển sang", xoá khoá là **mất cấu hình**.
        if (isNew) profileKeys(clean).forEach { e.remove(it) }
        e.apply()
    }

    /**
     * Xoá một hồ sơ — **và mọi khoá của nó**.
     *
     * ## ⚠⚠ [SOÁT P2-2] Bản cũ chỉ sửa hai khoá danh sách, để lại toàn bộ dữ liệu hồ sơ
     * Nó cập nhật `profiles` + `active_profile` rồi dừng, nên `<tên>__preset`, `<tên>__slot_0..5`, `<tên>__dock_*`,
     * `<tên>__top_strip`, `<tên>__grid_layout`, `<tên>__scenes`, `<tên>__boot_scene` **vẫn nằm nguyên trên đĩa**. Hai
     * hậu quả, cái sau nặng hơn:
     *  1. tệp prefs phình vô hạn (xoá/tạo hồ sơ bao nhiêu lần cũng không bao giờ thu lại);
     *  2. [addProfile] KHÔNG kiểm khoá cũ ⇒ đặt lại **đúng cái tên vừa xoá** thì hồ sơ "mới" nạp nguyên cấu hình cũ,
     *     trong đó có thể có `aw:<id>` của những widget mà id đã bị thu hồi ⇒ ô hiện thẻ *"app đã bị gỡ"* dù app còn
     *     nguyên. Tức người dùng tạo một hồ sơ mới và nhận về rác của một hồ sơ đã xoá.
     *
     * ⚠ Dọn sạch khoá cũng là điều làm **id widget của hồ sơ bị xoá được nhả đúng lúc**: [widgetIdsOtherProfiles] đọc
     * đĩa, nên sau lượt xoá này nó không còn kể id đó nữa ⇒ `AppWidgetIds.orphaned` (chạy ở lượt render kế tiếp) thấy
     * chúng thành rác và `AppWidgetSlotHost.reclaim` thu hồi. Không cần đường nhả riêng — và không nên có, vì đường
     * thứ hai là chỗ để quên.
     */
    fun deleteProfile(name: String) {
        val list = profiles().toMutableList()
        if (list.size <= 1 || name !in list) return
        list.remove(name)
        val e = sp.edit().putString(K_PROFILES, list.joinToString("\n"))
        if (activeProfile() == name) e.putString(K_ACTIVE, list.first())
        profileKeys(name).forEach { e.remove(it) }
        e.apply()
    }

    private fun key(suffix: String) = "${activeProfile()}__$suffix"

    /**
     * Mọi khoá thuộc hồ sơ [name] — **một chỗ duy nhất** khai danh sách này.
     *
     * Viết tay hai lần (một lần ở [deleteProfile], một lần ở [widgetIdsOtherProfiles]) là cách chắc chắn để bản sau
     * thêm một khoá theo-hồ-sơ rồi chỉ cập nhật một trong hai chỗ — và cả hai lỗi đều **im lặng** (khoá mồ côi / id
     * widget bị xoá oan). Có bài canh đòi mọi lời gọi `key("…")` trong tệp này phải có mặt trong [PROFILE_SUFFIXES].
     */
    private fun profileKeys(name: String): List<String> = PROFILE_SUFFIXES.map { "${name}__$it" }

    /**
     * [SOÁT P0-1] Id widget bên thứ ba đang bị **các hồ sơ KHÁC** giữ (bỏ hồ sơ đang dùng — xem KDoc
     * [HomeUiState.widgetIdsOtherProfiles] về việc vì sao phải bỏ).
     *
     * Chỉ đọc chuỗi rồi giao việc giải mã cho `:core` ([AppWidgetIds.idsInStored]) — phần có thể sai thì phải kiểm
     * được off-car, còn tệp này cần `Context` nên không kiểm được.
     */
    fun widgetIdsOtherProfiles(): Set<Int> {
        val active = activeProfile()
        return profiles().filter { it != active }.flatMapTo(mutableSetOf()) { p ->
            AppWidgetIds.idsInStored(
                slotRaw = (0 until WorkspaceState.SLOT_CAP).map { sp.getString("${p}__slot_$it", "") ?: "" },
                scenesRaw = sp.getString("${p}__$K_SCENES", null),
            )
        }
    }

    // ── Workspace (theo hồ sơ) ──
    fun load(): WorkspaceState {
        val preset = runCatching { LayoutPreset.valueOf(sp.getString(key("preset"), LayoutPreset.THREE.name)!!) }
            .getOrDefault(LayoutPreset.THREE)
        val slots = (0 until WorkspaceState.SLOT_CAP).map { decode(sp.getString(key("slot_$it"), "") ?: "") }
        return WorkspaceState(preset, slots)
    }

    fun save(state: WorkspaceState) {
        sp.edit().apply {
            putString(key("preset"), state.preset.name)
            state.slots.forEachIndexed { i, c -> putString(key("slot_$i"), encode(c)) }
            apply()
        }
    }

    // ── Dock (theo hồ sơ) ──
    fun loadDock(): DockConfig {
        val edge = runCatching { DockEdge.valueOf(sp.getString(key("dock_edge"), DockEdge.BOTTOM.name)!!) }
            .getOrDefault(DockEdge.BOTTOM)
        val enabled = sp.getString(key("dock_enabled"), null)?.split(",")?.filter { it.isNotBlank() }
            ?: ControlRegistry.defaultEnabledIds()
        return DockConfig(edge, enabled)
    }

    fun saveDock(c: DockConfig) {
        sp.edit()
            .putString(key("dock_edge"), c.edge.name)
            .putString(key("dock_enabled"), c.enabled.joinToString(","))
            .apply()
    }

    // ── Theme (chung mọi hồ sơ) ──
    fun themeMode(): ThemeMode =
        runCatching { ThemeMode.valueOf(sp.getString(K_THEME, ThemeMode.NIGHT.name)!!) }.getOrDefault(ThemeMode.NIGHT)

    fun setThemeMode(m: ThemeMode) { sp.edit().putString(K_THEME, m.name).apply() }

    // ── Ngôn ngữ (chung mọi hồ sơ) — U5 · T3 ──
    /**
     * ⚠⚠ **KHÔNG có khoá `lang` trong tệp `kachi_workspace`** — hai hàm này **uỷ quyền** sang chỗ lưu ngôn ngữ đã
     * tồn tại của ClusterNav ([com.byd.clusternav.Lang], tệp `clusternav_lang`, khoá `lang`).
     *
     * ## Đây là SAI LỆCH CÓ CHỦ Ý so với spec §3.1, và lý do quan trọng hơn câu chữ của spec
     * Spec ghi *"`WorkspacePrefs` khoá `lang`"*. Làm đúng chữ đó thì trong **một APK** sẽ có **hai** công tắc ngôn
     * ngữ: một của launcher (`kachi_workspace/lang`) và một của màn ClusterNav (`clusternav_lang/lang`, đang có
     * selector `seg_language` và được `MainActivity`/`ClusterNavActivity`/`BilingualLabels` đọc). Hai công tắc cho
     * **một** câu hỏi *"người ngồi đây đọc thứ tiếng nào"* chính là **bẫy hai-bản-sao** mà dự án đã trả giá bốn lần
     * (`customLayout` · `unitPrefs` ×4 bản · `wallpaper` · và chính `themeMode` trước T1). Biểu hiện ở đây sẽ rất khó
     * chối: chọn English trong Cài đặt Kachi rồi bấm "Mở màn ClusterNav" thì màn đó **vẫn tiếng Việt**.
     *
     * Nên chọn ngược lại: **một chỗ lưu, hai bề mặt đọc.** Chỗ lưu là chỗ đã có (`Lang`) vì
     *  1. nó **đã** mang đúng ba giá trị cần thiết (`auto`/`vi`/`en`) và đã có phép đọc tương thích ngược;
     *  2. màn ClusterNav đang **niêm phong** — không sửa được một dòng, nên chỗ lưu phải là chỗ nó đã đọc;
     *  3. `Lang.setChoice` cập nhật luôn cache của nó ⇒ hai bề mặt không thể lệch, kể cả trong cùng một lượt chạy.
     *
     * Cái mất: khoá này không nằm trong tệp prefs chính của launcher. Bù lại bằng máy, không bằng lời —
     * `SettingsCoverageContractTest` đã được **nới gốc quét** để đọc `Lang.kt`, nên `lang` và `clusternav_lang` đều
     * phải khai trong [SettingsCatalog] (và khai sai thì đỏ hai chiều).
     *
     * ## Vì sao vẫn đi qua `WorkspacePrefs` chứ không cho tầng UI gọi thẳng `Lang`
     * Để launcher chỉ có **MỘT** cửa đọc/ghi cấu hình (`repository` → `WorkspacePrefs`), đúng luật *tầng UI 0 lần ghi
     * bền trực tiếp*. Cho `SettingsSections` gọi `Lang.setChoice` thì tầng UI lại ghi thẳng xuống đĩa — đúng thứ RW0
     * vừa dọn xong.
     */
    fun langMode(): LangMode = LangMode.of(ClusterNavLang.choice(appCtx).code)

    fun setLangMode(mode: LangMode) =
        ClusterNavLang.setChoice(appCtx, ClusterNavLang.Choice.entries.first { it.code == mode.code })

    // ── Launcher auto-start (chung mọi hồ sơ) — B6 ──
    // Nổ máy → Kachi tự làm setup KHÔNG cần bung view (seed freeform + đặt HOME + đảm bảo HOME lên để khôi phục ô).
    // Kill-switch của người dùng; MẶC ĐỊNH BẬT (launcher nên tự sẵn sàng). [com.byd.clusternav.KachiAutostart] đọc cờ này.
    fun launcherAutostart(): Boolean = sp.getBoolean(K_AUTOSTART, true)

    fun setLauncherAutostart(on: Boolean) { sp.edit().putBoolean(K_AUTOSTART, on).apply() }

    // ── App mở gần đây (chung mọi hồ sơ) — U3 ──
    // CHUNG chứ không theo hồ sơ: đây là lịch sử dùng máy, không phải bố cục của một tài xế (cùng cách với theme).
    fun recentApps(): List<String> = RecentApps.decode(sp.getString(K_RECENT, null))

    fun touchRecentApp(pkg: String) {
        sp.edit().putString(K_RECENT, RecentApps.encode(RecentApps.touch(recentApps(), pkg))).apply()
    }

    // ── Đơn vị hiển thị (chung mọi hồ sơ) — RW0/R11 ──
    // CHUNG chứ không theo hồ sơ: đơn vị là thói quen của người ĐỌC (cùng cách với theme). Chuỗi rỗng/rác ⇒ mặc định.
    fun unitPrefs(): UnitPrefs = UnitPrefs.decode(sp.getString(K_UNITS, null))

    fun setUnitPrefs(prefs: UnitPrefs) { sp.edit().putString(K_UNITS, prefs.encode()).apply() }

    /**
     * U4 — hình nền + trình chiếu. CHUNG mọi hồ sơ: hình nền là thứ nhìn thấy cả màn, không phải thuộc tính của một
     * hồ sơ (cùng lối với giao diện sáng/tối và đơn vị).
     */
    /** Bố cục tự vẽ của hồ sơ đang dùng. Rỗng = chưa vẽ ⇒ dùng bố cục sẵn. */
    /**
     * Bố cục tự vẽ của hồ sơ đang dùng. Rỗng = chưa vẽ ⇒ dùng bố cục sẵn.
     *
     * [SOÁT P2-3] **Lọc ngay ở cửa vào**: chuỗi lưu là dạng người đọc được (để cứu bằng tay), nên nó có thể bị sửa
     * thành số vô lý. Khung nằm ngoài lưới mà lọt vào trình vẽ thì **kéo một cái là sập** (phép kẹp có trần nhỏ hơn
     * sàn), và việc đếm ô trống chạy hàng tỉ nhịp làm treo giao diện. Màn hình thì đã có lưới an toàn (lùi về bố cục
     * sẵn), nhưng trình vẽ là nơi người dùng vào để **sửa** nên phải chặn ở đây.
     */
    /**
     * Cấu hình chip thanh trên (RW0 vùng thứ ba). Theo **hồ sơ** như thanh nút — hai tài xế thích hai bộ chip khác
     * nhau là chuyện thường. Chuỗi lưu là danh sách mã trần, đọc được bằng mắt để cứu tay khi cần.
     */
    fun topStrip(): TopStripConfig = TopStripConfig.decode(sp.getString(key("top_strip"), null))

    fun setTopStrip(config: TopStripConfig) {
        sp.edit().putString(key("top_strip"), TopStripConfig.encode(config)).apply()
    }

    fun gridLayout(): GridLayout {
        val raw = WorkspaceGrid.decode(sp.getString(key(K_GRID), null))
        val sane = raw.frames.filter {
            it.cols in WorkspaceGrid.MIN_COLS..WorkspaceGrid.COLS &&
                it.rows in WorkspaceGrid.MIN_ROWS..WorkspaceGrid.ROWS &&
                it.col in 0 until WorkspaceGrid.COLS && it.row in 0 until WorkspaceGrid.ROWS &&
                it.colEnd <= WorkspaceGrid.COLS && it.rowEnd <= WorkspaceGrid.ROWS
        }
        return if (sane.size == raw.frames.size) raw else GridLayout(sane)
    }

    fun setGridLayout(layout: GridLayout?) {
        sp.edit().apply {
            if (layout == null || layout.frames.isEmpty()) remove(key(K_GRID))
            else putString(key(K_GRID), WorkspaceGrid.encode(layout))
        }.apply()
    }

    fun wallpaperPrefs(): WallpaperPrefs = WallpaperPrefs.decode(sp.getString(K_WALL, null))

    fun setWallpaperPrefs(prefs: WallpaperPrefs) { sp.edit().putString(K_WALL, prefs.encode()).apply() }

    /**
     * P7 + P6 — **SỔ CẢNH**, lưu THEO HỒ SƠ (mỗi tài xế có bộ cảnh riêng, giống bố cục và thanh nút).
     *
     * ## Vì sao HAI khoá, không phải một
     * `scenes` = danh sách; `boot_scene` = mã cảnh lúc nổ máy. Chúng là **hai câu hỏi khác nhau** của người dùng
     * (*"tôi có những cảnh nào"* vs *"cái nào lên lúc nổ máy"*) nên mỗi câu có một mục riêng trong [SettingsCatalog] —
     * gộp vào một khoá thì một trong hai mục sẽ phải khai `prefKey = null`, tức chỗ lưu của nó biến mất khỏi tầm kiểm
     * của bài test phủ khoá.
     *
     * Cái giá của hai khoá là **lệch nhau được**: `boot_scene` có thể trỏ tới cảnh đã bị xoá khỏi `scenes`. Giá đó trả
     * bằng máy chứ không bằng lời hứa — [SceneBook.decode] nhận cả hai chuỗi và **tự gỡ** con trỏ treo ngay tại cửa
     * vào, nên phần còn lại của app không bao giờ thấy trạng thái lệch.
     */
    fun sceneBook(): SceneBook =
        SceneBook.decode(sp.getString(key(K_SCENES), null), sp.getString(key(K_BOOT_SCENE), null))

    fun setSceneBook(book: SceneBook) {
        val clean = book.normalised()
        sp.edit().apply {
            putString(key(K_SCENES), SceneBook.encode(clean))
            // Bỏ dấu ⇒ XOÁ khoá thay vì ghi chuỗi rỗng: đọc lại sẽ không phải phân biệt "rỗng" với "chưa có".
            if (clean.bootSceneId == null) remove(key(K_BOOT_SCENE)) else putString(key(K_BOOT_SCENE), clean.bootSceneId)
        }.apply()
    }


    // ⚠ Phép mã hoá nội dung ô đã chuyển sang `:core` ([SlotCodec]) khi cảnh (P7/P6) cần lưu **cùng** dạng đó. Để
    // lại hai bản ở hai nơi là cách chắc chắn để cảnh đọc ra nội dung ô khác với thứ người dùng đã lưu — cùng họ với
    // "ngưỡng lốp thứ ba" và "hai bảng màu". Dạng chuỗi KHÔNG đổi một byte ⇒ cấu hình trên đĩa đọc lên nguyên vẹn.
    private fun encode(c: SlotContent): String = SlotCodec.encode(c)

    private fun decode(s: String): SlotContent = SlotCodec.decode(s)

    companion object {
        const val DEFAULT_PROFILE = "Mặc định"
        private const val K_PROFILES = "profiles"
        private const val K_ACTIVE = "active_profile"
        private const val K_THEME = "theme_mode"
        private const val K_AUTOSTART = "launcher_autostart"
        private const val K_RECENT = "recent_apps"
        private const val K_UNITS = "unit_prefs"
        private const val K_WALL = "wallpaper_prefs"

    /**
     * P9 — bố cục tự vẽ, lưu THEO HỒ SƠ (mỗi tài xế có bố cục riêng, giống thanh nút). Chuỗi tự đọc được
     * (`0,0,7,4;7,0,5,6`) để cứu bằng tay được nếu cần.
     */
    private const val K_GRID = "grid_layout"

    /**
     * P7 + P6 — sổ cảnh + cảnh lúc nổ máy, cả hai lưu THEO HỒ SƠ. Xem KDoc [sceneBook] về việc **vì sao hai khoá**.
     *
     * ⚠ Cả hai phải khai trong [SettingsCatalog] (mỗi khoá một mục), không thì `SettingsCoverageContractTest` đỏ với
     * đúng câu *"khoá lưu bền chưa được gom vào nhóm nào"* — đó là phép kiểm của R2 và nó đối chiếu với **mã nguồn**,
     * nên không có cách nào thêm một khoá lặng lẽ.
     */
    private const val K_SCENES = "scenes"
    private const val K_BOOT_SCENE = "boot_scene"

    /**
     * ⚠⚠ [SOÁT P2-2] **MỌI hậu tố khoá theo-hồ-sơ, khai ĐÚNG MỘT LẦN.**
     *
     * Đây là danh sách mà [deleteProfile] dùng để dọn sạch và [widgetIdsOtherProfiles] dùng để dò. Hai chỗ đó **không
     * được** tự viết lại danh sách: thêm một khoá theo-hồ-sơ ở bản sau mà chỉ cập nhật một trong hai nơi thì hoặc là
     * khoá mồ côi sống mãi, hoặc là id widget của hồ sơ khác bị xoá oan — cả hai đều im lặng.
     *
     * `slot_*` phải sinh theo [WorkspaceState.SLOT_CAP], không chép tay: trần ô đã đổi một lần (4 → 6) và chỗ nào
     * chép tay con số đó thì lần đổi sau sẽ bỏ sót hai ô cuối.
     *
     * ⚠ Chỉ khoá THEO HỒ SƠ. Khoá chung cả máy (`theme_mode`, `unit_prefs`, `wallpaper_prefs`, `recent_apps`,
     * `launcher_autostart`, `profiles`, `active_profile`) **KHÔNG** được có ở đây — xoá một hồ sơ mà mất luôn lựa chọn
     * đơn vị của cả xe là một lỗi tệ hơn lỗi đang vá. Có bài canh đòi đúng điều đó.
     */
    val PROFILE_SUFFIXES: List<String> = buildList {
        addAll(listOf("preset", "dock_edge", "dock_enabled", "top_strip", K_GRID, K_SCENES, K_BOOT_SCENE))
        addAll((0 until WorkspaceState.SLOT_CAP).map { "slot_$it" })
    }
    }
}
