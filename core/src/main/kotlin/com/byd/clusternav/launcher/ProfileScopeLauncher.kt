package com.byd.clusternav.launcher

/**
 * ═══ PROFILE-IMPORT-TYPES — KIỂU khai sẵn của MỌI hậu tố theo hồ sơ phía LAUNCHER ═════════════════════════════════════
 *
 * Spec `docs/specs/kachi-profiles-are-everything.html` §12 (IO-R7 · §12.4.4). Cùng khuôn [ProfileScopeCluster.DECLARED_TYPES]
 * (khoá BÊN TRONG ảnh chụp ClusterNav), nhưng cho chính các hậu tố của tệp `kachi_workspace`: bố cục · cá nhân · `slot_*` ·
 * bốn hậu tố ảnh chụp `__cn__*` (bản thân chuỗi ảnh là `String`; khoá bên trong nó do bảng kia lo).
 *
 * ## Bệnh nó chữa [ĐO source + code]
 * `SharedPreferencesImpl.getString` ép kiểu thẳng — `String v = (String)mMap.get(key);` (android-10.0.0_r47
 * `core/java/android/app/SharedPreferencesImpl.java:288`, `getBoolean` :331; android-12.0.0_r34 :302/:345) ⇒ một giá trị
 * sai kiểu NÉM `ClassCastException`. Lượt nhập hồ sơ tới 2.84 chép NGUYÊN kiểu đã giải mã từ tệp (`b|slot_0|true` là một
 * dòng hợp lệ của [PrefSnapshot]) vào hồ sơ mới; `load()` của launcher đọc `slot_*` của MỌI hồ sơ khác
 * (`widgetIdsOtherProfiles`) ⇒ ném ngay lượt nạp sau khi nhập, và ở MỌI lần mở sau đó: màn chính sập cho tới khi xoá dữ
 * liệu app.
 *
 * ## Ba lớp, không lớp nào ném
 *  1. **Nhập** — [check] chạy trong [ProfileTransfer.planImport]: sai kiểu ⇒ khoá thành `null` (= `remove`), tên nó vào
 *     `dropped` để `WorkspacePrefs.importProfile` ghi log.
 *  2. **Xuất** — [check] chạy trong [ProfileTransfer.export]: tệp xuất không mang độc từ đĩa sang máy chạy bản ≤ 2.84.
 *  3. **Đọc** (`:app` `PrefsTypedRead.kt`) — giá trị sai kiểu ĐÃ nằm trên đĩa từ một lượt nhập ≤ 2.84 được đọc như VẮNG
 *     (`stringOrNull`/`booleanOrNull`), nên `load()` không sập kể cả trên máy đã nhập tệp độc trước bản này.
 *
 * Bài canh: `ProfileScopeLauncherTest` (`:core`) đòi bảng phủ ĐÚNG [ProfileScope.LAUNCHER_SUFFIXES] (hậu tố mới quên
 * khai kiểu ⇒ đỏ) và chạy thật các ca tệp độc (`slot_0` Int/Boolean, `preset` Boolean, `top_strip` Long…);
 * `LauncherProfileTypesCoverageTest` (`:app`) quét MỌI lượt `put*`/đọc khoá theo hồ sơ trong `WorkspacePrefs*.kt` và đòi
 * kiểu của chúng == bảng này, mọi hậu tố có cả lượt ghi lẫn lượt đọc, và không còn lượt `sp.get*` trần nào trên khoá
 * mang tiền tố hồ sơ.
 *
 * ⚠ Đọc [ProfileScope] lúc khởi tạo là được; chiều ngược KHÔNG được ([ProfileScope] không đọc đối tượng này — vòng khởi
 * tạo cho giá trị `null` giữa chừng, bài học `TopStripConfig.BUILT_IN`).
 */
object ProfileScopeLauncher {

    /** Hậu tố → kiểu mà `WorkspacePrefs` ghi và đọc. Kiểu chỉ có hai: chuỗi mã hoá hoặc cờ. */
    val DECLARED_TYPES: Map<String, PrefType> = buildMap {
        // Bố cục — `WorkspacePrefs.save/saveDock/setTopStrip/setHeaderLayout/setGridLayout` + `writeRecord` của lượt chuyển cảnh.
        listOf("preset", "dock_edge", "dock_enabled", "top_strip", "grid_layout", "header_order", "app_shortcuts", "dock_scale")
            .forEach { put(it, PrefType.STRING) }
        listOf("dock_visible", "top_strip_labels", "top_strip_migrated_ux5b", "swap_button_autohide").forEach { put(it, PrefType.BOOLEAN) }
        // Cá nhân — chuỗi mã hoá của `:core` (`UnitPrefs`, `WallpaperPrefs`, `SavedPlaces`, `ColorChoice`, `LangMode`…).
        listOf("theme_mode", "unit_prefs", "wallpaper_prefs", "lang", "saved_places", "color_choice", "ignition_apps", "ignition_music")
            .forEach { put(it, PrefType.STRING) }
        put("launcher_autostart", PrefType.BOOLEAN)
        // Sinh từ CÙNG nguồn với [ProfileScope.LAUNCHER_SUFFIXES]: trần ô và số tệp ClusterNav còn đổi được, chép tay con
        // số đó ở đây là lần đổi sau bỏ sót im lặng.
        (0 until WorkspaceState.SLOT_CAP).forEach { put("${SettingsCatalog.SLOT_KEY_PREFIX}$it", PrefType.STRING) }
        ProfileScope.SNAPSHOT_SUFFIXES.forEach { put(it, PrefType.STRING) }
    }

    /** Kết quả [check]: giá trị giữ lại (đúng kiểu hoặc `null`) + hậu tố bị bỏ, theo thứ tự gặp. */
    data class Checked(val values: Map<String, Any?>, val dropped: List<String>)

    /**
     * Bỏ mọi giá trị sai [DECLARED_TYPES]. `null` (= "xoá khoá" ở chỗ gọi) đi qua nguyên. Hậu tố CHƯA khai kiểu ⇒ bỏ
     * (fail-closed: không đoán kiểu cho thứ bảng không biết — bài canh đòi bảng phủ đủ nên nhánh này không nên xảy ra).
     */
    fun check(values: Map<String, Any?>): Checked {
        val kept = LinkedHashMap<String, Any?>()
        val dropped = ArrayList<String>()
        values.forEach { (suffix, v) ->
            val declared = DECLARED_TYPES[suffix]
            if (v == null || (declared != null && PrefType.of(v) == declared)) kept[suffix] = v else dropped += suffix
        }
        return Checked(kept, dropped)
    }
}
