package com.byd.clusternav.launcher

import com.byd.clusternav.launcher.camera.CameraSettingsIa
import com.byd.clusternav.modules.clustercast.simplified.CastEnableDeferral
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ V-CLUSTER · VC-R1 — bảng phân loại cụm · chiếu · camera: ĐỦ, KHÔNG TRÙNG, CÓ LÝ DO ══════════════════════════
 *
 * Owner 2026-09-30: *"Phần cụm lưu hết thành profile nhé"*. Spec §11.4.1 / §11.5 C1. Bài quét SOURCE của `:app` (khoá
 * literal trong `SharedPrefsSimpleCastPrefs`/`CastAppCatalog`) nằm ở `:app` — `ClusterProfileScopeCoverageTest`.
 */
class ProfileScopeClusterTest {

    private val cameraAll = CameraSettingsIa.ALL_KEYS

    @Test
    fun `34 khoa camera = 8 theo ho so + 26 theo xe, khong khoa nao UNKNOWN`() {
        // 2.92 · CAMERA-FULL-VIEW: +`camera_projection` +`camera_zoom` (HỒ SƠ — cách trình bày, owner 30/09 "phần cụm
        // lưu hết thành profile") · +`camera_wide_kappa/_focal/_pan_x` (XE — quang học, cùng họ tám núm nắn).
        // 2.93 · CAMERA-PER-CAM-CONFIG: +22 khoá bộ chỉnh *Từng camera* — 18 theo HỒ SƠ (góc sau/trước · vị trí · cỡ · hình ·
        // kiểu ×4) + 4 theo XE (xoay/lật camera sau/trước — sự thật lắp đặt). 34 → 56 · 8 → 26 · 26 → 30.
        assertEquals(56, cameraAll.size, "CameraSettingsIa đổi số khoá ⇒ xếp loại lại ở ProfileScopeCluster")
        assertEquals(26, ProfileScopeCluster.CAMERA_PROFILE_KEYS.size)
        assertEquals(30, ProfileScopeCluster.CAMERA_DEVICE_KEYS.size)
        com.byd.clusternav.launcher.camera.CameraCamConfig.PROFILE_KEYS
            .forEach { assertTrue(it in ProfileScopeCluster.CAMERA_PROFILE_KEYS, "$it là sở thích trình bày ⇒ theo hồ sơ") }
        com.byd.clusternav.launcher.camera.CameraCamConfig.DEVICE_KEYS
            .forEach { assertTrue(it in ProfileScopeCluster.CAMERA_DEVICE_KEYS, "$it là sự thật lắp đặt ⇒ theo xe") }
        assertEquals(
            cameraAll.toSet(), ProfileScopeCluster.CAMERA_PROFILE_KEYS.keys + ProfileScopeCluster.CAMERA_DEVICE_KEYS.keys,
            "hợp hai bảng phải ĐÚNG bằng tập khoá camera — thêm khoá mà quên xếp loại là đỏ ở đây",
        )
        assertTrue((ProfileScopeCluster.CAMERA_PROFILE_KEYS.keys intersect ProfileScopeCluster.CAMERA_DEVICE_KEYS.keys).isEmpty())
        assertEquals(emptySet<String>(), ProfileScope.unclassified(cameraAll))
        ProfileScopeCluster.CAMERA_PROFILE_KEYS.keys.forEach {
            assertEquals(ProfileScope.Scope.PROFILE, ProfileScope.scopeOf(it), it)
            assertTrue(it in ProfileScope.CLUSTERNAV_PROFILE_KEYS, "$it phải vào ảnh chụp")
        }
        ProfileScopeCluster.CAMERA_DEVICE_KEYS.keys.forEach {
            assertEquals(ProfileScope.Scope.DEVICE, ProfileScope.scopeOf(it), it)
            assertFalse(it in ProfileScope.CLUSTERNAV_PROFILE_KEYS, "$it theo XE không được vào ảnh chụp")
        }
    }

    @Test
    fun `khoa cua chieu cum va CastAppCatalog deu da xep loai`() {
        val simpleCast = listOf(
            "cast_enabled", "cast_bubble_visible", "split_ratio_left_pct", "autostart_enabled", "autostart_package",
            "autostart_split_enabled", "autostart_left_package", "autostart_right_package",
            "config_size_a.b", "config_overscan_a.b__L30", "config_density_a.b", "config_bounds_a.b__R70",
        )
        simpleCast.forEach { assertEquals(ProfileScope.Scope.PROFILE, ProfileScope.scopeOf(it), it) }
        listOf("last_display_id", "doze_whitelist_applied", CastEnableDeferral.PENDING_KEY, "bubbleEnabled", "favorites",
            "protected", "rectStyle", "legacyDefaultCandidate", "migrationVersion", "scale-dpi:a.b", "scale-l:a.b",
            "dpi:a.b", "profileOverride", "autoCast", "castable", "keepSession", "bubble_auto", "badge_corner",
            "badge_dx", "badge_dy", "vm_float_whitelist_applied", "camera_rotation", ProfileScopeCluster.MIGRATED_KEY,
        ).forEach { assertEquals(ProfileScope.Scope.DEVICE, ProfileScope.scopeOf(it), it) }
        listOf("bubbleX", "bubbleY").forEach {
            assertEquals(ProfileScope.Scope.PROFILE, ProfileScope.scopeOf(it), it)
            assertEquals(listOf("bubbleX", "bubbleY"), ProfileScope.CLUSTERNAV_KEYS[ProfileScopeCluster.CAST_CATALOG_FILE])
        }
    }

    @Test
    fun `khong khoa nao vua PROFILE vua DEVICE`() {
        val device = ProfileScope.DEVICE_KEYS.keys
        assertTrue((device intersect ProfileScope.CLUSTERNAV_PROFILE_KEYS).isEmpty(), "${device intersect ProfileScope.CLUSTERNAV_PROFILE_KEYS}")
        assertTrue((device intersect ProfileScopeCluster.PROFILE_EXTRA_KEYS.keys).isEmpty())
        assertTrue(ProfileScopeCluster.FAMILY_PREFIXES.keys.none { p -> ProfileScope.DEVICE_KEY_PREFIXES.keys.any { p.startsWith(it) || it.startsWith(p) } })
    }

    @Test
    fun `moi dong co ly do`() {
        listOf(
            ProfileScopeCluster.CAMERA_PROFILE_KEYS, ProfileScopeCluster.CAMERA_DEVICE_KEYS, ProfileScopeCluster.DEVICE_KEYS,
            ProfileScopeCluster.DEVICE_KEY_PREFIXES, ProfileScopeCluster.EXTRA_FILES, ProfileScopeCluster.FAMILY_PREFIXES,
        ).forEach { t -> assertTrue(t.values.all { it.isNotBlank() }, "thiếu lý do: $t") }
    }

    /** Mốc họ dùng tiền tố `@` — không khoá prefs thật nào được bắt đầu bằng nó, không thì mốc nhầm thành khoá. */
    @Test
    fun `khong khoa that nao bat dau bang tien to moc`() {
        val real = SettingsCatalog.CLUSTERNAV_KEYS.keys + SettingsCatalog.NOT_SETTINGS.keys + ProfileScope.DEVICE_KEYS.keys +
            ProfileScope.LAUNCHER_SUFFIXES + ProfileScopeCluster.PROFILE_EXTRA_KEYS.keys + ProfileScope.TRANSIENT_KEYS.keys
        assertTrue(real.none { it.startsWith("@") }, real.filter { it.startsWith("@") }.toString())
        assertEquals("@family:cast_geometry", ProfileScopeCluster.CAST_GEOMETRY.marker)
        ProfileScopeCluster.FAMILIES.forEach { assertFalse(it.owns(it.marker), "mốc của họ không được là khoá của họ") }
    }

    @Test
    fun `ho, khoa them va khoa di tru deu tro vao tep anh chup co that`() {
        ProfileScopeCluster.FAMILIES.forEach {
            assertTrue(it.file in ProfileScope.CLUSTERNAV_KEYS, "họ ${it.id} ở tệp không có ảnh chụp ⇒ không bao giờ được chụp")
        }
        val files = SettingsCatalog.CLUSTERNAV_PREFS_FILES.keys + ProfileScopeCluster.EXTRA_FILES.keys
        ProfileScopeCluster.PROFILE_EXTRA_KEYS.values.forEach { assertTrue(it in files, "tệp chưa khai lý do: $it") }
        ProfileScopeCluster.MOVED_TO_PROFILE.forEach { (file, keys) ->
            keys.forEach { k ->
                assertTrue(k in ProfileScope.CLUSTERNAV_KEYS.getValue(file), "$k phải là khoá ảnh chụp của $file")
                assertEquals(ProfileScope.Scope.PROFILE, ProfileScope.scopeOf(k), k)
            }
        }
    }

    @Test
    fun `kieu khai san chi cho khoa theo ho so, khoa hoan o dung tep`() {
        ProfileScopeCluster.DECLARED_TYPES.keys.forEach {
            assertTrue(it in ProfileScope.CLUSTERNAV_PROFILE_KEYS, "khai kiểu cho khoá không đi qua ảnh chụp: $it")
        }
        ProfileScopeCluster.DEFERRED.forEach { (liveKey, pendingKey) ->
            assertTrue(liveKey in ProfileScope.CLUSTERNAV_KEYS.getValue(ProfileScopeCluster.SIMPLE_CAST_FILE))
            assertEquals(ProfileScope.Scope.DEVICE, ProfileScope.scopeOf(pendingKey))
            assertFalse(pendingKey in ProfileScope.CLUSTERNAV_PROFILE_KEYS, "khoá chờ vào ảnh ⇒ đổi hồ sơ đẻ bản chờ giả")
        }
    }
}
