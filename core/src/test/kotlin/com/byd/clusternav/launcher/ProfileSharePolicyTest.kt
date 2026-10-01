package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ PROFILE-IO-0930 · IO-R3 — bảng phân loại khoá cho bản CHIA SẺ phải ĐỦ và ĐÚNG ════════════════════════════════
 *
 * Spec `docs/specs/kachi-profiles-are-everything.html` §12.4.2. Bài chạy trên **bảng thật** ([ProfileScope] +
 * [ProfileScopeCluster]) — thêm một khoá theo hồ sơ ở bản sau mà quên xếp loại ở [ProfileSharePolicy] thì đỏ ở đây,
 * trước khi tệp chia sẻ đầu tiên mang nó ra khỏi xe.
 *
 * Khoá cái gì:
 *  • mọi khoá theo hồ sơ (hậu tố launcher + khoá trong 4 ảnh `__cn__*`) thuộc ĐÚNG MỘT bảng;
 *  • bảng không mang khoá ma (khoá đã rời hồ sơ mà còn nằm lại ⇒ không ai biết bảng còn đúng không);
 *  • khoá có TÊN mang dáng vị trí/lịch đi lại phải nằm ở bảng riêng tư — bắt ca xếp nhầm bảng;
 *  • lọc là danh sách TRẮNG: khoá chưa soát không bao giờ được coi là chia sẻ được.
 */
class ProfileSharePolicyTest {

    /**
     * Mọi khoá TRẦN theo hồ sơ: hậu tố launcher (trừ ảnh chụp, trừ ô sinh `slot_<số>`) + khoá cố định của mọi ảnh
     * ClusterNav. Lượt 2 [P3]: chỉ loại ô SINH theo số — một hậu tố mới tên `slot_…` khác vẫn phải được xếp loại.
     */
    private val generatedSlot = Regex("^" + Regex.escape(SettingsCatalog.SLOT_KEY_PREFIX) + "[0-9]+$")

    private val profileKeys: Set<String> = buildSet {
        ProfileScope.LAUNCHER_SUFFIXES
            .filterNot { it in ProfileScope.SNAPSHOT_SUFFIXES || generatedSlot.matches(it) }
            .forEach { add(it) }
        ProfileScope.CLUSTERNAV_KEYS.values.flatten().forEach { add(it) }
    }

    /** Tiền tố dựng động theo hồ sơ đi vào tệp: `slot_` + họ `cast_geometry` (mọi họ ở [ProfileScopeCluster.FAMILIES]). */
    private val profilePrefixes: Set<String> =
        setOf(SettingsCatalog.SLOT_KEY_PREFIX) + ProfileScopeCluster.FAMILIES.flatMap { it.prefixes }

    /**
     * Gốc từ mang dáng dữ liệu vị trí / lịch đi lại / nhật ký chuyến. So theo TỪNG TỪ của tên khoá (tách `_`/`-`/chữ
     * hoa) để `top_strip` không khớp nhầm `trip`.
     */
    private val locationStems = listOf(
        "place", "addr", "lat", "lng", "lon", "coord", "geo", "gps", "locat", "route", "dest", "sched", "automation",
        "trip", "journey", "history", "home", "work",
    )

    private fun locationShaped(key: String): Boolean =
        key.replace(Regex("([a-z])([A-Z])"), "$1_$2").lowercase().split('_', '-')
            .any { word -> locationStems.any { word.startsWith(it) } }

    @Test
    fun `moi khoa theo ho so thuoc dung mot bang`() {
        val missing = profileKeys.filter { it !in ProfileSharePolicy.PRIVATE && it !in ProfileSharePolicy.SHAREABLE }
        assertTrue(missing.isEmpty(), "khoá theo hồ sơ CHƯA xếp loại chia sẻ (thêm vào PRIVATE hoặc SHAREABLE kèm lý do): $missing")
        val both = ProfileSharePolicy.PRIVATE.keys intersect ProfileSharePolicy.SHAREABLE.keys
        assertTrue(both.isEmpty(), "khoá nằm ở CẢ HAI bảng: $both")
        val prefixMissing = profilePrefixes - ProfileSharePolicy.SHAREABLE_PREFIXES.keys
        assertTrue(prefixMissing.isEmpty(), "họ tiền tố theo hồ sơ chưa soát: $prefixMissing")
    }

    @Test
    fun `bang khong mang khoa ma`() {
        val ghost = (ProfileSharePolicy.PRIVATE.keys + ProfileSharePolicy.SHAREABLE.keys) - profileKeys
        assertTrue(ghost.isEmpty(), "khoá trong bảng chia sẻ không còn theo hồ sơ: $ghost")
        val ghostPrefix = ProfileSharePolicy.SHAREABLE_PREFIXES.keys - profilePrefixes
        assertTrue(ghostPrefix.isEmpty(), "tiền tố ma: $ghostPrefix")
        (ProfileSharePolicy.PRIVATE + ProfileSharePolicy.SHAREABLE + ProfileSharePolicy.SHAREABLE_PREFIXES)
            .forEach { (k, why) -> assertTrue(why.length >= 20, "`$k` thiếu lý do thật") }
    }

    @Test
    fun `khoa ten mang dang vi tri phai o bang rieng tu`() {
        val shaped = profileKeys.filter(::locationShaped).toSet()
        assertEquals(
            setOf("saved_places", "nav_automation_rules", "nav_automation_fired"), shaped,
            "khoá mang dáng vị trí/lịch đổi — soát lại bảng PRIVATE rồi mới sửa danh sách này",
        )
        val leaked = shaped.filterNot { it in ProfileSharePolicy.PRIVATE }
        assertTrue(leaked.isEmpty(), "khoá mang dáng vị trí lọt vào bảng chia sẻ được: $leaked")
    }

    @Test
    fun `ba khoa rieng tu dung nhu owner duyet`() {
        assertEquals(setOf("saved_places", "nav_automation_rules", "nav_automation_fired"), ProfileSharePolicy.PRIVATE.keys)
        ProfileSharePolicy.PRIVATE.keys.forEach { assertFalse(ProfileSharePolicy.shareable(it), it) }
    }

    @Test
    fun `khoa chua soat khong bao gio chia se duoc — danh sach trang`() {
        listOf("home_coords", "last_destination", "trip_log", "brand_new_key", "").forEach {
            assertFalse(ProfileSharePolicy.shareable(it), "`$it` chưa soát mà đi ra ngoài")
        }
        assertTrue(ProfileSharePolicy.shareable("slot_3"))
        assertTrue(ProfileSharePolicy.shareable("config_density_vn.vietmap.live"))
        assertTrue(ProfileSharePolicy.shareable("preset"))
    }

    /**
     * Lượt 2 [P3] — họ dựng động khớp NEO HAI ĐẦU, không theo tiền tố: khoá mới mang tên `slot_…`/`config_size_…` mà
     * chưa ai soát (và không đúng dạng của họ) vẫn bị bỏ khỏi bản chia sẻ, kể cả khi bài canh bị tắt.
     */
    @Test
    fun `ho dung dong khop dung dang, khong theo tien to`() {
        listOf(
            "slot_home_address", "slot_", "slot_3x", "config_size_", "config_density_last_destination",
            "config_bounds_not a package", "config_density_vn.vietmap.live;rm",
        ).forEach { assertFalse(ProfileSharePolicy.shareable(it), "`$it` không thuộc họ nào đã soát") }
        ProfileScopeCluster.CAST_GEOMETRY.prefixes.forEach {
            assertTrue(ProfileSharePolicy.shareable("${it}com.example.app"), "khoá họ hợp lệ của `$it`")
        }
    }
}
