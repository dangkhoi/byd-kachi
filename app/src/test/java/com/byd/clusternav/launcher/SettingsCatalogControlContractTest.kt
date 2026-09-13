package com.byd.clusternav.launcher

import com.byd.clusternav.testsupport.SourceRoots
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * ═══ R2 · §4.3 — **MỌI MỤC CỦA DANH MỤC ĐỀU CÓ MỘT ĐIỀU KHIỂN THẬT** ═════════════════════════════════════════
 *
 * Tách khỏi [SettingsScreenWiringContractTest] (backlog D2c: tệp đó 547 dòng, quá trần 500 của CLAUDE.md §4.1).
 * Ranh giới cắt chọn ở đây vì bảng 55 mục là **một loại bài khác** với phần còn lại: phần kia canh *hình dạng dây
 * nối* của vỏ màn (rail, back, một-cửa-vào, không-ghi-bền), còn bài này là **bảng đối chiếu dữ liệu** giữa danh mục
 * `:core` và các tệp section của `:app` — nó dài vì có 55 hàng, và nó sẽ còn dài thêm mỗi lần IA nhận mục mới.
 * Trộn hai loại trong một tệp nghĩa là mỗi lần thêm một mục cài đặt lại đẩy tệp kia gần trần hơn.
 *
 * Toàn bộ assert giữ NGUYÊN văn từ bản gộp — đây là lượt tách tệp, không phải lượt sửa luật.
 */
class SettingsCatalogControlContractTest {

    private fun code(relative: String): String = SourceRoots.codeOf(relative)

    private val sections by lazy { code("src/main/java/com/byd/clusternav/launcher/SettingsSections.kt") }
    private val home by lazy { code("src/main/java/com/byd/clusternav/launcher/SettingsSectionsHome.kt") }
    private val scenes by lazy { code("src/main/java/com/byd/clusternav/launcher/SettingsSceneSection.kt") }
    private val bars by lazy { code("src/main/java/com/byd/clusternav/launcher/SettingsSectionsBars.kt") }
    private val nav by lazy { code("src/main/java/com/byd/clusternav/launcher/SettingsSectionsNav.kt") }
    private val cast by lazy { code("src/main/java/com/byd/clusternav/launcher/SettingsSectionsCast.kt") }
    private val keys by lazy { code("src/main/java/com/byd/clusternav/launcher/SettingsSectionsKeys.kt") }
    private val car by lazy { code("src/main/java/com/byd/clusternav/launcher/SettingsSectionsCar.kt") }

    /**
     * ⚠⚠ **BÀI CANH CHÍNH CỦA IA v2 (R2 · §4.3)** — *"không cấu hình nào nằm ngoài"*.
     *
     * `SettingsCoverageContractTest` trả lời chiều thứ nhất: *mọi khoá lưu bền đều thuộc một nhóm của danh mục*.
     * Nó **không thể** trả lời chiều thứ hai, và chiều thứ hai mới là thứ người dùng thấy: *mỗi mục của danh mục có
     * thật một điều khiển trên màn hay không*. Danh mục khai 55 mục; một mục khai rồi mà không ai dựng control thì
     * rail vẫn nói "nhóm này có N mục" còn trang thì thiếu — và không có gì đỏ.
     *
     * ## Cách khoá: BẢNG mã mục → dấu vết trong tệp section
     * Mỗi dòng là một cặp `(mã mục, chuỗi nhận diện)` — chuỗi đó là **lời gọi thật** dựng/ghi cho mục ấy
     * (`bridge.setBadgeCenter(`, `deps.onDockEdge(`…), không phải một nhãn. Chọn lời gọi chứ không chọn nhãn vì
     * nhãn đổi theo câu chữ còn lời gọi thì đổi theo **hành vi** — và hành vi mới là thứ cần canh.
     *
     * Hai chiều, cả hai đều phải đỏ:
     *  1. mục có trong danh mục mà bảng này thiếu ⇒ **đỏ** (ai đó thêm mục vào `:core` mà quên dựng control);
     *  2. mã trong bảng mà danh mục không còn ⇒ **đỏ** (bảng rữa, canh một thứ đã bỏ).
     */
    @Test
    fun `moi muc cua danh muc deu co it nhat mot control`() {
        val controls: Map<String, Pair<String, String>> = mapOf(
            // ── 1 · Màn hình chính ──
            "home_scenes" to ("SettingsSceneSection" to "book.scenes.forEach"),
            "home_scene_boot" to ("SettingsSceneSection" to "deps.scenes.setBoot("),
            "home_scene_save" to ("SettingsSceneSection" to "deps.scenes.save()"),
            "home_preset" to ("SettingsSectionsHome" to "deps.onPreset("),
            "home_grid" to ("SettingsSectionsHome" to "EffectiveLayout.highlightedPreset("),
            "home_grid_editor" to ("SettingsSectionsHome" to "deps.onOpenLayoutEditor()"),
            "home_wallpaper" to ("SettingsSectionsHome" to "deps.onWallpaper("),
            // ── 2 · Thanh trạng thái & thanh nút ──
            "bars_top_strip" to ("SettingsSectionsBars" to "stripPicker.section("),
            "bars_dock_edge" to ("SettingsSectionsBars" to "deps.onDockEdge("),
            "bars_dock_items" to ("SettingsSectionsBars" to "deps.openDockPicker("),
            // ── 3 · Hiển thị & đơn vị ──
            "display_units" to ("SettingsSections" to "rows.unitRow("),
            "display_theme" to ("SettingsSections" to "deps.onThemeMode("),
            "display_lang" to ("SettingsSections" to "deps.onLangMode("),
            // ── 4 · Hồ sơ tài xế ──
            "profiles_list" to ("SettingsSections" to "deps.onSwitchProfile("),
            "profiles_active" to ("SettingsSections" to "R.string.kachi_profile_active"),
            // ── 5 · Dẫn đường & cụm đồng hồ ──
            "nav_enabled" to ("SettingsSectionsNav" to "bridge.setNavEnabled("),
            "nav_cluster_mode" to ("SettingsSectionsNav" to "bridge.setClusterMode("),
            "nav_marquee" to ("SettingsSectionsNav" to "bridge.setMarquee("),
            // ⚠ Bốn dòng dưới KHÔNG có "(" ở cuối: chúng là lời gọi dạng **trailing lambda** (`bridge.reconnect { … }`).
            "nav_reconnect" to ("SettingsSectionsNav" to "bridge.reconnect"),
            "badge_enabled" to ("SettingsSectionsNav" to "bridge.setBadgeEnabled("),
            "badge_upcoming" to ("SettingsSectionsNav" to "bridge.setUpcomingBadge("),
            "badge_alert_chip" to ("SettingsSectionsNav" to "bridge.setAlertChip("),
            "badge_size" to ("SettingsSectionsNav" to "bridge.setBadgeSizeDp("),
            "badge_center" to ("SettingsSectionsNav" to "bridge.setBadgeCenter("),
            "vm_bubble_enabled" to ("SettingsSectionsNav" to "bridge.setVmBubbleEnabled("),
            "vm_bubble_pos" to ("SettingsSectionsNav" to "bridge.setVmBubblePos("),
            // ── 6 · Chiếu màn lên cụm ──
            "cast_enabled" to ("SettingsSectionsCast" to "bridge.setCastEnabled("),
            "cast_split" to ("SettingsSectionsCast" to "bridge.setSplitPct("),
            "cast_autostart" to ("SettingsSectionsCast" to "bridge.setAutostartFull("),
            "cast_autostart_pkg" to ("SettingsSectionsCast" to "bridge.setAutostartPkg("),
            "cast_autostart_split" to ("SettingsSectionsCast" to "bridge.setAutostartSplit("),
            "cast_autostart_left" to ("SettingsSectionsCast" to "bridge.setAutostartLeftPkg("),
            "cast_autostart_right" to ("SettingsSectionsCast" to "bridge.setAutostartRightPkg("),
            "cast_actions" to ("SettingsSectionsCast" to "bridge.castFull("),
            "cast_rescue" to ("SettingsSectionsCast" to "bridge.deepRescue("),
            // ── 7 · Phím vô-lăng ──
            "keys_enabled" to ("SettingsSectionsKeys" to "bridge.setVoiceKeyEnabled("),
            "keys_bindings" to ("SettingsSectionsKeys" to "bridge.bindings()"),
            "keys_custom_buttons" to ("SettingsSectionsKeys" to "bridge.customButtons()"),
            "keys_learn" to ("SettingsSectionsKeys" to "bridge.startLearn"),
            "keys_check" to ("SettingsSectionsKeys" to "bridge.checkFix"),
            // ── 8 · Tiện nghi xe ──
            "car_recirc_on_start" to ("SettingsSectionsCar" to "bridge.setRecircOnStart("),
            "car_seat_enabled" to ("SettingsSectionsCar" to "bridge.setSeatEnabled("),
            "car_seat_mode" to ("SettingsSectionsCar" to "bridge.setSeatMode("),
            "car_seat_levels" to ("SettingsSectionsCar" to "bridge.setSeatLevel("),
            "car_pm25" to ("SettingsSectionsCar" to "bridge.setPm25Enabled("),
            "car_pm25_clean" to ("SettingsSectionsCar" to "bridge.pm25CleanNow()"),
            // ── 9 · Hệ thống & quyền ──
            "system_permissions" to ("SettingsSections" to "rows.permissionRow("),
            "system_autostart" to ("SettingsSections" to "deps.onAutostart("),
            "system_headless_autostart" to ("SettingsSections" to "deps.bridge.setHeadlessAutostart("),
            "system_update" to ("SettingsSections" to "deps.bridge.checkUpdate"),
            "system_nav_stop" to ("SettingsSections" to "deps.bridge.navStop()"),
            "system_vietmap_data" to ("SettingsSections" to "deps.bridge.openVietMapData()"),
            "system_diagnostics" to ("SettingsSections" to "deps.bridge.openDiagnostics()"),
            // ── 10 · Giới thiệu ──
            "about_version" to ("SettingsSections" to "R.string.kachi_about_version"),
            "about_disclaimer" to ("SettingsSections" to "R.string.kachi_about_disclaimer"),
        )
        val sources = mapOf(
            "SettingsSections" to sections,
            "SettingsSectionsHome" to home,
            "SettingsSceneSection" to scenes,
            "SettingsSectionsBars" to bars,
            "SettingsSectionsNav" to nav,
            "SettingsSectionsCast" to cast,
            "SettingsSectionsKeys" to keys,
            "SettingsSectionsCar" to car,
        )

        val catalogIds = SettingsCatalog.ENTRIES.map { it.id }.toSet()
        assertEquals(
            emptyList<String>(), (catalogIds - controls.keys).sorted(),
            "mục khai trong danh mục mà KHÔNG có control nào trên màn ⇒ rail nói có, trang thì thiếu",
        )
        assertEquals(
            emptyList<String>(), (controls.keys - catalogIds).sorted(),
            "bảng canh nhắc một mã KHÔNG còn trong danh mục ⇒ nó đang canh một thứ đã bỏ (bài canh rữa)",
        )
        val missing = controls.filterNot { (_, where) ->
            sources.getValue(where.first).contains(where.second)
        }.map { "${it.key} → ${it.value.first}: '${it.value.second}'" }
        assertEquals(
            emptyList<String>(), missing.sorted(),
            "mục của danh mục không tìm thấy control tương ứng trong tệp section của nhóm nó: $missing",
        )
        // Chốt chống bảng rỗng: 10 nhóm phải có mặt đủ, không nhóm nào lọt qua vì bảng chỉ khai vài mục.
        assertEquals(
            10, SettingsGroup.values().size,
            "IA v2 §4.1 chốt 10 nhóm — đổi số nhóm là đổi cả bản đồ cài đặt, phải sửa cả bảng trên",
        )
    }
}
