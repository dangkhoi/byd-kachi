package com.byd.clusternav.launcher

import android.content.Context
import com.byd.clusternav.launcher.voice.VoiceGrammarSnapshotStore
import com.byd.clusternav.Lang as ClusterNavLang

/**
 * Lưu/khôi phục **mọi lựa chọn của người dùng theo HỒ SƠ TÀI XẾ** qua SharedPreferences (tệp `kachi_workspace`).
 *
 * ## S4 · R3 — "hồ sơ giữ TẤT CẢ", không còn khoá chung cả máy nào là cấu hình
 * Trước S4 một hồ sơ chỉ giữ bố cục · ô · thanh nút · chip, còn chủ đề · đơn vị · hình nền · ngôn ngữ · tự-mở nằm
 * **chung cả máy**, và toàn bộ cấu hình ClusterNav thì không ai chép. Owner 2026-09-14: *"profile cover bố cục, các
 * cấu hình tất cả mọi thứ"*. Nay:
 *  • hậu tố theo hồ sơ = [ProfileScope.LAUNCHER_SUFFIXES] (khai một chỗ ở `:core`, có bài canh);
 *  • cấu hình ClusterNav đi theo hồ sơ bằng **ảnh chụp** (`WorkspacePrefsProfile.kt`: [snapshotClusterNav] /
 *    [applyClusterNav]);
 *  • khoá còn lại chung cả xe (`profiles` · `active_profile` · `boot_profile` · `recent_apps` · dấu chuyển đổi) đều
 *    có lý do ghi tại chỗ ở [ProfileScope.DEVICE_KEYS].
 *
 * Off-car test được phần thuần (khoá/danh sách ở `:core`); phần cần `Context` thì có bài canh quét **mã nguồn**
 * (`ProfileKeysWiringContractTest`).
 *
 * ⚠ Import có tên (`as ClusterNavLang`): `:core` cũng có một `Lang` (enum thuần `VI`/`EN`) và tệp này dùng **cả hai** —
 * [LangMode] của `:core` là kiểu trên đường dây, `ClusterNavLang` là chỗ lưu. Hai tên khác nhau thì không lẫn được; để
 * cả hai tên là `Lang` thì một trong hai phải viết đủ package ở mọi chỗ dùng, và chỗ nào quên sẽ **vẫn biên dịch** với
 * kiểu sai nếu chữ ký trùng.
 */
class WorkspacePrefs(context: Context) {
    /**
     * ⚠ `internal`, không `private`: [snapshotClusterNav]/[applyClusterNav]/[migrateScenesOnce] là **hàm mở rộng của
     * chính lớp này** nằm ở `WorkspacePrefsProfile.kt` (trần 500 dòng — cùng cách `ClusterNavBridge` đã tách phần
     * Cast/Phím). Chúng phải ghi vào **đúng một** `SharedPreferences` với mọi hàm ở đây; mở tệp lần thứ hai ở lớp
     * khác là dựng **cửa thứ hai** vào cùng chỗ lưu, đúng thứ [SOÁT P1-1] đã dọn.
     */
    internal val sp = context.getSharedPreferences("kachi_workspace", Context.MODE_PRIVATE)

    /**
     * Cần cho đường ngôn ngữ (chỗ lưu là tệp prefs của ClusterNav) **và** cho ảnh chụp cấu hình ClusterNav
     * ([applyClusterNav] mở từng tệp trong [ProfileScope.CLUSTERNAV_KEYS]) — cả hai đi qua `Context`, không qua [sp].
     */
    internal val appCtx = context.applicationContext

    // ── Hồ sơ tài xế ──
    fun profiles(): List<String> =
        sp.getString(K_PROFILES, null)?.split("\n")?.filter { it.isNotBlank() }?.takeIf { it.isNotEmpty() }
            ?: listOf(DEFAULT_PROFILE)

    fun activeProfile(): String = sp.getString(K_ACTIVE, DEFAULT_PROFILE) ?: DEFAULT_PROFILE

    // Mọi đường ghi hồ sơ/sổ địa chỉ kết bằng `VoiceGrammarSnapshotStore.write(this)` — `:wake` đọc TỆP đó (§8.2 A).
    fun setActiveProfile(name: String) { sp.edit().putString(K_ACTIVE, name).apply(); VoiceGrammarSnapshotStore.write(this) }

    /**
     * S4 · R6 — **hồ sơ lúc nổ máy**; `null` = *"hồ sơ dùng gần nhất"*.
     *
     * Theo **XE** ([ProfileScope.DEVICE_KEYS]), không mang tiền tố hồ sơ: nó CHỌN hồ sơ nên phải đọc được **trước
     * khi** biết hồ sơ nào (đúng ca `active_profile`).
     *
     * ⚠ **Gỡ con trỏ treo ngay tại cửa ra**: tên trỏ tới một hồ sơ đã xoá ⇒ trả `null`, đúng luật *"con trỏ treo thì
     * tự bỏ"* mà sổ cảnh của P7 đã phải học. Không làm ở đây thì màn Cài đặt hiện một hồ sơ không tồn tại, còn
     * đường khởi động nguội lại lên bằng hồ sơ mặc định — hai bề mặt nói hai chuyện và không ai hiểu vì sao.
     */
    fun bootProfile(): String? = sp.getString(K_BOOT_PROFILE, null)?.takeIf { it in profiles() }

    /** Ghi bền hồ sơ lúc nổ máy; `null`/tên lạ ⇒ **xoá khoá** (đọc lại không phải phân biệt "rỗng" với "chưa có"). */
    fun setBootProfile(name: String?) {
        sp.edit().apply {
            if (name == null || name !in profiles()) remove(K_BOOT_PROFILE) else putString(K_BOOT_PROFILE, name)
        }.apply()
    }

    /**
     * S5 — **giữ Kachi làm màn hình chính khi nổ máy**. Theo **XE** ([ProfileScope.DEVICE_KEYS]), không tiền tố hồ
     * sơ: màn hình chính là thuộc tính của cả xe, không của một tài xế.
     *
     * ⚠ Mặc định **TẮT**: đặt HOME của cả xe (`cmd package set-home-activity`) là đổi state hệ thống, không được tự
     * làm sau lưng người dùng (CLAUDE.md §4). Đường CHÍNH để thành HOME là **nút trong Cài đặt**
     * ([ClusterNavBridge.setDefaultHome]); công tắc này chỉ cho đường khởi động nguội đặt lại **một lần** nếu ROM
     * reset HOME sau reboot ([SUY] — chưa đo, chờ P7). [com.byd.clusternav.KachiAutostart] đọc cờ này.
     */
    fun keepHomeOnBoot(): Boolean = sp.getBoolean(K_KEEP_HOME_ON_BOOT, false)

    /**
     * Người dùng ĐÃ bấm "Đặt làm màn hình chính" thành công (2026-09-15, HOME-alias). Khác [keepHomeOnBoot] (opt-in
     * "giữ home mỗi boot", mặc định TẮT): marker này là **lựa chọn đã bày tỏ** — lối vào HOME là alias tắt sẵn, sau
     * nâng cấp alias mới lại tắt ⇒ Home rơi về launcher3 nếu không re-apply. KachiAutostart đọc marker để bật alias +
     * `set-home-activity` lại (khôi phục lựa chọn của người dùng, không phải đổi state mới — CLAUDE.md §4/§5).
     */
    fun homeChosen(): Boolean = sp.getBoolean(K_HOME_CHOSEN, false)

    fun setHomeChosen(on: Boolean) { sp.edit().putBoolean(K_HOME_CHOSEN, on).apply() }

    fun setKeepHomeOnBoot(on: Boolean) { sp.edit().putBoolean(K_KEEP_HOME_ON_BOOT, on).apply() }

    /**
     * Thêm một hồ sơ và chuyển sang nó. Tên trùng hồ sơ đã có ⇒ chỉ chuyển sang, không tạo thêm.
     *
     * ## ⚠ [SOÁT P2-2] Hồ sơ MỚI phải bắt đầu TRỐNG — kể cả trên máy đã chạy bản cũ
     * [deleteProfile] nay dọn sạch khoá, nhưng **máy đang chạy trên xe thì không**: mọi hồ sơ từng bị xoá bằng bản cũ
     * còn để lại nguyên `<tên>__slot_*`, `<tên>__theme_mode`… trên đĩa. Đặt lại đúng cái tên đó sẽ nạp cấu hình của một hồ sơ
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
        VoiceGrammarSnapshotStore.write(this)
    }

    /**
     * Xoá một hồ sơ — **và mọi khoá của nó**.
     *
     * ## ⚠⚠ [SOÁT P2-2] Bản cũ chỉ sửa hai khoá danh sách, để lại toàn bộ dữ liệu hồ sơ
     * Nó cập nhật `profiles` + `active_profile` rồi dừng, nên `<tên>__preset`, `<tên>__slot_0..5`, `<tên>__dock_*`,
     * `<tên>__top_strip`, `<tên>__grid_layout`, `<tên>__cn__*` **vẫn nằm nguyên trên đĩa**. Hai
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
        VoiceGrammarSnapshotStore.write(this)
    }

    internal fun key(suffix: String) = keyOf(activeProfile(), suffix)

    /**
     * Khoá của **một hồ sơ bất kỳ** — ĐÚNG MỘT chỗ trong dự án ghép tiền tố tên hồ sơ vào hậu tố.
     *
     * Ghép tay ở chỗ thứ hai là cách chắc chắn để một bên dùng `__` còn bên kia dùng `_` (và lỗi đó **im lặng**: khoá
     * mới đơn giản là rỗng ⇒ cấu hình "về mặc định" mà không ai biết vì sao).
     */
    internal fun keyOf(profile: String, suffix: String) = "${profile}__$suffix"

    /**
     * Mọi khoá thuộc hồ sơ [name] — **một chỗ duy nhất** khai danh sách này.
     *
     * Viết tay hai lần (một lần ở [deleteProfile], một lần ở [widgetIdsOtherProfiles]) là cách chắc chắn để bản sau
     * thêm một khoá theo-hồ-sơ rồi chỉ cập nhật một trong hai chỗ — và cả hai lỗi đều **im lặng** (khoá mồ côi / id
     * widget bị xoá oan). Có bài canh đòi mọi lời gọi `key("…")` trong tệp này phải có mặt trong [PROFILE_SUFFIXES].
     */
    internal fun profileKeys(name: String): List<String> = PROFILE_SUFFIXES.map { keyOf(name, it) }

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
                slotRaw = (0 until WorkspaceState.SLOT_CAP).map { sp.getString(keyOf(p, "slot_$it"), "") ?: "" },
                // ⚠⚠ S4 · R2 — vẫn đọc chuỗi CẢNH đời cũ, và đó không phải mã thừa. Trong cửa sổ *"bản mới đã cài
                // nhưng `migrateScenesOnce` chưa chạy"* thì id widget của một cảnh **chỉ còn nằm ở đây**; bỏ vế này
                // là để lượt dọn rác đầu tiên của bản mới thu hồi chúng **vĩnh viễn** — tức chính lượt nâng cấp làm
                // mất dữ liệu. Sau khi chuyển xong, khoá này đã bị xoá nên phép đọc trả `null` và vế tự tắt.
                scenesRaw = sp.getString(keyOf(p, LEGACY_SCENES), null),
            )
        }
    }

    /**
     * Bố cục **ĐANG HIỆU LỰC** + số ô có nội dung của hồ sơ [name] (KHÔNG phải hồ sơ đang dùng) — cho thẻ hồ sơ ở
     * Cài đặt nói ra hồ sơ đó giữ gì. Đọc theo tiền tố tên như [widgetIdsOtherProfiles]. `null` = **tự vẽ**.
     *
     * ## ⚠ Vì sao hỏi [EffectiveLayout], KHÔNG phải "có khung là tự vẽ"
     * Bố cục tự vẽ **lưu rồi mà không dùng được** (đè nhau · nhiều khung hơn trần ô — ca có thật khi hạ cấp bản) bị
     * màn hình **LÙI về bố cục sẵn**. Trả "Tự vẽ" ở đây thì thẻ hồ sơ và màn hình nói hai chuyện khác nhau — đúng lỗi
     * mà KDoc [EffectiveLayout.highlightedPreset] đã phải dọn một lần cho dải chip bố cục.
     *
     * Số ô cũng đếm theo bố cục đang hiệu lực ([EffectiveLayout.slotCount]), không theo trần ô: nội dung còn sót ở ô
     * thứ 5 của một bố cục 2 ô **không hiện ra ở đâu cả**, kể nó vào là báo một con số người dùng không thấy.
     */
    fun profileLayout(name: String): Pair<LayoutPreset?, Int> {
        val custom = grid(name)
        val preset = runCatching { LayoutPreset.valueOf(sp.getString(keyOf(name, "preset"), LayoutPreset.THREE.name)!!) }
            .getOrDefault(LayoutPreset.THREE)
        val shown = EffectiveLayout.slotCount(preset, custom).coerceAtMost(WorkspaceState.SLOT_CAP)
        val filled = (0 until shown).count { decode(sp.getString(keyOf(name, "slot_$it"), "") ?: "") != SlotContent.Empty }
        return EffectiveLayout.highlightedPreset(preset, custom) to filled
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
            // 1.95+ (bug 2026-09-22 "đặt 10 hiện 6"): lọc mã đã XOÁ khỏi bộ đăng ký qua [DockSelection.sanitize]
            // (một nguồn, test ở :core) — cấu hình cũ lưu lock/door/window/mac_door_light (gỡ 1.94/1.95) thì
            // `ControlDockView.rebuild` bỏ qua IM LẶNG nên "10 đang bật" mà chỉ 6 nút hiện. Lọc ở cửa NẠP.
            ?.let { DockSelection.sanitize(it) }
            ?: ControlRegistry.defaultEnabledIds()
        val visible = sp.getBoolean(key("dock_visible"), true)   // S1b — vắng = hiện (giữ hành vi cũ)
        return DockConfig(edge, enabled, visible)
    }

    fun saveDock(c: DockConfig) {
        sp.edit()
            .putString(key("dock_edge"), c.edge.name)
            .putString(key("dock_enabled"), c.enabled.joinToString(","))
            .putBoolean(key("dock_visible"), c.visible)
            .apply()
    }

    // ── Theme (S4 · R3a — nay THEO HỒ SƠ; xem [profileString] về đường lùi khoá chung cũ) ──
    fun themeMode(): ThemeMode =
        runCatching { ThemeMode.valueOf(profileString(K_THEME) ?: ThemeMode.NIGHT.name) }
            .getOrDefault(ThemeMode.NIGHT)

    fun setThemeMode(m: ThemeMode) { sp.edit().putString(key(K_THEME), m.name).apply() }

    // ── VISUAL-REFRESH P1b · R8 — màu nhấn/tông thẻ theo hồ sơ. Khoá MỚI ⇒ đọc thẳng `key()` (không có bản chung cũ
    //    để lùi về, cùng lẽ [savedPlaces]); thiếu/rác ⇒ mặc định = bảng màu 1.69, không hỏi (AC8.4).
    fun colorChoice(): ColorChoice = ColorChoice.decode(sp.getString(key(K_COLOR), null))

    fun setColorChoice(c: ColorChoice) { sp.edit().putString(key(K_COLOR), c.encode()).apply() }

    // ── Launcher auto-start (S4 · R3a — nay THEO HỒ SƠ; xem [profileBoolean] về đường lùi khoá chung cũ) — B6 ──
    // Nổ máy → Kachi tự làm setup KHÔNG cần bung view (seed freeform + đặt HOME + đảm bảo HOME lên để khôi phục ô).
    // Kill-switch của người dùng; MẶC ĐỊNH BẬT (launcher nên tự sẵn sàng). [com.byd.clusternav.KachiAutostart] đọc cờ này.
    fun launcherAutostart(): Boolean = profileBoolean(K_AUTOSTART, true)

    fun setLauncherAutostart(on: Boolean) { sp.edit().putBoolean(key(K_AUTOSTART), on).apply() }

    // ── App mở gần đây (chung mọi hồ sơ) — U3 ──
    // CHUNG chứ không theo hồ sơ: đây là lịch sử dùng máy, không phải bố cục của một tài xế (cùng cách với theme).
    fun recentApps(): List<String> = RecentApps.decode(sp.getString(K_RECENT, null))

    fun touchRecentApp(pkg: String) {
        sp.edit().putString(K_RECENT, RecentApps.encode(RecentApps.touch(recentApps(), pkg))).apply()
    }

    // ── Đơn vị hiển thị (S4 · R3a — nay THEO HỒ SƠ) — RW0/R11 ──
    // Owner 2026-09-14 "profile cover ... tất cả mọi thứ": đơn vị là lựa chọn của MỘT người lái, nên nó đi theo hồ
    // sơ như chủ đề/hình nền. Đường lùi khoá chung cũ ở [profileString]. Chuỗi rỗng/rác ⇒ mặc định.
    fun unitPrefs(): UnitPrefs = UnitPrefs.decode(profileString(K_UNITS))

    fun setUnitPrefs(prefs: UnitPrefs) { sp.edit().putString(key(K_UNITS), prefs.encode()).apply() }

    /**
     * U4 — hình nền + trình chiếu. S4 · R3a: **theo HỒ SƠ** (cùng lối với chủ đề, đơn vị, ngôn ngữ) — đường lùi
     * khoá chung cũ ở [profileString], nên lựa chọn đang có trên xe không mất khi cập nhật.
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
    fun topStrip(): TopStripConfig =
        TopStripConfig.decode(sp.getString(key("top_strip"), null), sp.getBoolean(key("top_strip_labels"), true))

    /** Ghi cấu hình chip — **hai khoá, một lượt ghi**: không đường nào ghi được một nửa cấu hình. */
    fun setTopStrip(config: TopStripConfig) {
        sp.edit()
            .putString(key("top_strip"), TopStripConfig.encode(config))
            .putBoolean(key("top_strip_labels"), config.showLabels)
            .apply()
    }

    /**
     * UX-OVERHAUL · WP4 — **thứ tự các vật trên thanh trên**, theo hồ sơ (khoá `header_order`).
     *
     * Chuỗi lưu là danh sách **tên hằng** ([HeaderItem.name]) đọc được bằng mắt, cùng lệ `top_strip`/`grid_layout`
     * (cứu tay qua `run-as … cat`). Mọi phép chữa dữ liệu hỏng/thiếu nằm ở [HeaderLayout.decode] — ở đây chỉ đọc
     * chuỗi, để việc *"bản sau thêm một vật thì vật đó xuất hiện ở cuối"* kiểm được off-car.
     */
    fun headerLayout(): HeaderLayout = HeaderLayout.decode(sp.getString(key("header_order"), null))

    fun setHeaderLayout(layout: HeaderLayout) {
        sp.edit().putString(key("header_order"), HeaderLayout.encode(layout)).apply()
    }

    fun gridLayout(): GridLayout = grid(activeProfile())

    /**
     * Bố cục tự vẽ của **một hồ sơ bất kỳ**, đã lọc ở cửa vào — MỘT phép đọc cho cả [gridLayout] (hồ sơ đang dùng) và
     * [profileLayout] (hồ sơ khác). Hai bản lọc riêng là cách chắc chắn để thẻ hồ sơ và màn hình lệch nhau khi ai đó
     * sửa một trong hai (cùng họ với bẫy hai-bản-sao đã ghi ở KDoc [PROFILE_SUFFIXES]).
     */
    private fun grid(name: String): GridLayout {
        val raw = WorkspaceGrid.decode(sp.getString(keyOf(name, K_GRID), null))
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

    fun wallpaperPrefs(): WallpaperPrefs = WallpaperPrefs.decode(profileString(K_WALL))

    fun setWallpaperPrefs(prefs: WallpaperPrefs) { sp.edit().putString(key(K_WALL), prefs.encode()).apply() }

    /**
     * **Sổ địa chỉ** của hồ sơ đang dùng (spec `docs/specs/kachi-voice-addresses.html` R1).
     *
     * ⚠ Đọc thẳng `key(K_PLACES)`, **không** đi qua [profileString]: đường lùi-khoá-chung-cũ ở đó tồn tại cho bốn
     * khoá **đã nằm trên đĩa** trước S4 (chủ đề · đơn vị · hình nền · tự-mở). Sổ địa chỉ là khoá MỚI hoàn toàn —
     * không có bản chung nào để lùi về, và nếu cứ gọi thì phép `sp.contains(suffix)` lại đi hỏi một khoá không bao
     * giờ tồn tại ở mọi lượt đọc.
     *
     * Chuỗi rỗng/rác ⇒ sổ rỗng (phép giải mã ở `:core` bỏ dòng hỏng, không ném — xem [SavedPlaces.decode]).
     */
    fun savedPlaces(): List<SavedPlace> = SavedPlaces.decode(sp.getString(key(K_PLACES), null))

    fun setSavedPlaces(places: List<SavedPlace>) {
        sp.edit().putString(key(K_PLACES), SavedPlaces.encode(places)).apply()
        VoiceGrammarSnapshotStore.write(this)
    }

    // ── S4 · R3(a) — LÙI về khoá chung cũ, đúng MỘT lần, rồi ghi sang hồ sơ ──────────────────────

    /**
     * Giá trị chuỗi theo hồ sơ; **chưa có ⇒ lùi về khoá chung cũ** (cùng tên, không tiền tố) rồi ghi sang hồ sơ.
     *
     * ## Vì sao phải lùi, chứ không chỉ đọc mặc định
     * Bốn khoá này (`theme_mode` · `unit_prefs` · `wallpaper_prefs` · `launcher_autostart`) **đã nằm trên đĩa của xe
     * đang chạy** dưới dạng khoá chung cả máy. Bản mới đọc theo hồ sơ mà không lùi thì lượt mở app đầu tiên sau khi
     * cập nhật sẽ nói *"chưa chọn gì"* — tức người dùng mất sạch lựa chọn giao diện/đơn vị/hình nền của mình, im
     * lặng, và không có đường lấy lại (giá trị cũ vẫn ở đó nhưng không ai đọc nữa).
     *
     * ## Vì sao GHI sang hồ sơ ngay, và vì sao KHÔNG xoá khoá cũ
     * Ghi ngay ⇒ mọi lượt đọc sau chỉ còn một đường, và [snapshotClusterNav]/[duplicateProfile] thấy đủ dữ liệu. Giữ
     * khoá cũ ⇒ **mọi** hồ sơ đã có đều thừa hưởng đúng lựa chọn đó ở lần đọc đầu của nó (nếu xoá ngay sau hồ sơ đầu
     * tiên thì hồ sơ thứ hai lại về mặc định — đúng cái lỗi đang chữa, chỉ chậm hơn một nhịp).
     *
     * Kiểu sai trên đĩa (người dùng sửa tay, hoặc bản cũ ghi kiểu khác) ⇒ coi như chưa có: `getString` trên một khoá
     * ghi bằng `putBoolean` **ném** `ClassCastException`, và launcher không được sập vì một byte hỏng.
     */
    private fun profileString(suffix: String): String? {
        val k = key(suffix)
        if (sp.contains(k)) return sp.getString(k, null)
        val legacy = runCatching { sp.getString(suffix, null) }.getOrNull() ?: return null
        sp.edit().putString(k, legacy).apply()
        return legacy
    }

    /** [profileString] cho giá trị `Boolean` (`launcher_autostart`). Cùng luật, cùng lý do. */
    private fun profileBoolean(suffix: String, def: Boolean): Boolean {
        val k = key(suffix)
        if (sp.contains(k)) return sp.getBoolean(k, def)
        if (!sp.contains(suffix)) return def
        val legacy = runCatching { sp.getBoolean(suffix, def) }.getOrDefault(def)
        sp.edit().putBoolean(k, legacy).apply()
        return legacy
    }

    // ⚠ Phép mã hoá nội dung ô đã chuyển sang `:core` ([SlotCodec]) khi cảnh (P7/P6) cần lưu **cùng** dạng đó. Để
    // lại hai bản ở hai nơi là cách chắc chắn để cảnh đọc ra nội dung ô khác với thứ người dùng đã lưu — cùng họ với
    // "ngưỡng lốp thứ ba" và "hai bảng màu". Dạng chuỗi KHÔNG đổi một byte ⇒ cấu hình trên đĩa đọc lên nguyên vẹn.
    private fun encode(c: SlotContent): String = SlotCodec.encode(c)

    private fun decode(s: String): SlotContent = SlotCodec.decode(s)

    companion object {
        const val DEFAULT_PROFILE = "Mặc định"

        // ── Khoá theo XE (R4) — KHÔNG mang tiền tố hồ sơ ────────────────────────────────────────
        internal const val K_PROFILES = "profiles"
        internal const val K_ACTIVE = "active_profile"

        /** S4 · R6 — hồ sơ lúc nổ máy (`null`/vắng = hồ sơ dùng gần nhất). Lý do "theo xe" ở [ProfileScope.DEVICE_KEYS]. */
        internal const val K_BOOT_PROFILE = "boot_profile"

        /** S5 — "giữ Kachi làm màn hình chính khi nổ máy". Theo XE (không tiền tố) — lý do ở [ProfileScope.DEVICE_KEYS]. */
        internal const val K_KEEP_HOME_ON_BOOT = "keep_home_on_boot"
        internal const val K_HOME_CHOSEN = "home_chosen"

        /**
         * S4 · R2 — dấu *"đã chuyển cảnh sang hồ sơ"*, đặt MỘT lần cho cả máy ([migrateScenesOnce]).
         *
         * Theo **xe** chứ không theo hồ sơ: để nó theo hồ sơ thì mỗi hồ sơ mới lại chạy lại một lượt chuyển đổi trên
         * dữ liệu đã chuyển rồi ⇒ nhân bản cảnh cũ thành `"Đi làm 2"`, `"Đi làm 3"`… mỗi lần thêm hồ sơ.
         */
        internal const val K_MIGRATED_SCENES = "migrated_scenes_v1"

        private const val K_RECENT = "recent_apps"

        // ── Hậu tố theo HỒ SƠ (R3) — luôn đi qua [key]/[keyOf] ──────────────────────────────────
        private const val K_THEME = "theme_mode"
        /** P1b · R8 — `ColorChoice.encode()`; nằm trong [ProfileScope.LAUNCHER_PERSONAL_SUFFIXES]. */
        private const val K_COLOR = "color_choice"
        private const val K_AUTOSTART = "launcher_autostart"
        private const val K_UNITS = "unit_prefs"
        private const val K_WALL = "wallpaper_prefs"
        /** `internal` vì ba hàm ngôn ngữ nay ở `WorkspacePrefsLang.kt` (trần 500 dòng) — vẫn MỘT khoá, một chỗ khai. */
        internal const val K_LANG = "lang"

        /**
         * Sổ địa chỉ — **một chuỗi cho cả sổ** (mỗi mục một dòng, xem [SavedPlaces]), không phải họ khoá
         * `place_0..n`: một họ khoá dựng động kéo theo một tiền tố nữa trong [ProfileScope.PROFILE_KEY_PREFIXES]
         * và một vòng xoá thủ công lúc xoá hồ sơ — cùng lý do `top_strip`/`grid_layout` là một chuỗi.
         */
        private const val K_PLACES = "saved_places"

        /**
         * P9 — bố cục tự vẽ, lưu THEO HỒ SƠ (mỗi tài xế một bố cục, giống thanh nút). Chuỗi tự đọc được
         * (`0,0,7,4;7,0,5,6`) để cứu bằng tay được nếu cần.
         */
        private const val K_GRID = "grid_layout"

        // ── Khoá ĐỜI CŨ, chỉ còn ĐỌC (S4 · R1 đã bỏ khái niệm "cảnh") ───────────────────────────

        /**
         * Hậu tố `<hồ sơ>__scenes` / `<hồ sơ>__boot_scene` của P7/P6.
         *
         * ⚠ Còn ở đây vì **đĩa của xe đang chạy còn chúng**, không phải vì app còn dùng: [widgetIdsOtherProfiles] đọc
         * `scenes` làm lưới an toàn cho id widget, và [migrateScenesOnce] đọc cả hai đúng một lần rồi xoá. Chúng KHÔNG
         * nằm trong [PROFILE_SUFFIXES] (chúng là [ProfileScope.Scope.TRANSIENT]) nên không đi theo hồ sơ nào, không
         * được chép khi nhân bản, và không sống quá lượt chuyển đổi.
         */
        internal const val LEGACY_SCENES = "scenes"
        internal const val LEGACY_BOOT_SCENE = "boot_scene"

        /**
         * ⚠⚠ **MỌI hậu tố khoá theo-hồ-sơ** — đọc THẲNG từ [ProfileScope.LAUNCHER_SUFFIXES] (`:core`), không viết lại.
         *
         * ## Vì sao danh sách chuyển hẳn sang `:core` ở S4
         * Trước S4 nó là một `buildList` ngay tại đây, và văn xuôi *"chỉ khoá theo hồ sơ"* là thứ duy nhất canh nó.
         * S4 · R3 làm danh sách dài gấp đôi (thêm chủ đề · đơn vị · hình nền · ngôn ngữ · tự-mở + một hậu tố **ảnh
         * chụp** cho mỗi tệp prefs ClusterNav) và thêm một bảng đối xứng *"khoá theo XE"* — hai danh sách phải khớp
         * nhau tuyệt đối, mà phép kiểm đó chỉ chạy được ở `:core` (off-car). [ProfileScope] giữ cả hai, sinh `slot_*`
         * theo [WorkspaceState.SLOT_CAP] và hậu tố ảnh chụp theo [ProfileScope.CLUSTERNAV_KEYS], và `ProfileScopeTest`
         * đòi **mọi** khoá lưu bền đã khai trong mã phải rơi vào đúng một bảng.
         *
         * Viết lại danh sách ở đây (kể cả "chép cho gọn") là mở lại đúng cái cửa đó: [deleteProfile] sẽ để khoá mồ
         * côi, [widgetIdsOtherProfiles] sẽ dò thiếu — cả hai **im lặng**.
         */
        val PROFILE_SUFFIXES: List<String> = ProfileScope.LAUNCHER_SUFFIXES
    }
}
