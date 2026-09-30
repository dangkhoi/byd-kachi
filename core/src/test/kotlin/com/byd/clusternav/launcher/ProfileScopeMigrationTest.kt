package com.byd.clusternav.launcher

import com.byd.clusternav.modules.clustercast.simplified.CastEnableDeferral
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ V-CLUSTER · VC-R3 — rót cấu hình cụm đang sống xuống MỌI hồ sơ, chỉ điền chỗ trống ════════════════════════
 *
 * Spec §11.4.5 / §11.6 V-7. Hỏng ở đây là **mất cấu hình của người lái ngay lượt nâng cấp**: ghi đè thì xoá lựa chọn
 * vừa đặt; bỏ khoá vắng thì giá trị của hồ sơ vừa rời tràn sang; rót họ khi đã có mốc thì DPI của A đè DPI của B.
 */
class ProfileScopeMigrationTest {

    private val sc = ProfileScopeCluster.SIMPLE_CAST_FILE
    private val families = ProfileScopeCluster.familiesOf(sc)
    private val marker = ProfileScopeCluster.CAST_GEOMETRY.marker
    private val newKeys = ProfileScopeCluster.MOVED_TO_PROFILE.getValue(sc)

    private val live = mapOf<String, Any?>(
        "cast_enabled" to true,
        "cast_bubble_visible" to false,
        "config_size_vn.vietmap.live" to "1920x720",
        "config_density_vn.vietmap.live" to "320",
        "config_bounds_vn.vietmap.live__L30" to "0,0,576,720",
        "split_ratio_left_pct" to 30,
    )

    /** V-7: ba hồ sơ ảnh cũ ⇒ mỗi ảnh có đủ khoá mới + mốc họ, giá trị = đang sống. */
    @Test
    fun `moi ho so nhan du khoa moi va ho, gia tri dang song`() {
        val shots = mapOf("A" to mapOf<String, Any?>("split_ratio_left_pct" to 50), "B" to emptyMap(), "C" to emptyMap())
        val out = ProfileScopeMigration.rotDown(shots, live, newKeys, families, ProfileScopeCluster.DEFERRED)
        assertEquals(setOf("A", "B", "C"), out.keys)
        out.values.forEach { shot ->
            assertEquals(true, shot["cast_enabled"]); assertEquals(false, shot["cast_bubble_visible"])
            assertEquals("320", shot["config_density_vn.vietmap.live"])
            assertEquals("0,0,576,720", shot["config_bounds_vn.vietmap.live__L30"])
            assertEquals(true, shot[marker])
        }
        assertEquals(50, out.getValue("A")["split_ratio_left_pct"], "khoá có sẵn trong ảnh không bị đụng")
    }

    @Test
    fun `chi dien cho trong — ho so da co khoa giu khoa cua minh`() {
        val shots = mapOf(
            "A" to mapOf<String, Any?>("cast_enabled" to false, "cast_bubble_visible" to null),
            "B" to mapOf<String, Any?>(marker to true, "config_density_vn.vietmap.live" to "160"),
        )
        val out = ProfileScopeMigration.rotDown(shots, live, newKeys, families, ProfileScopeCluster.DEFERRED)
        assertEquals(false, out.getValue("A")["cast_enabled"])
        assertTrue(out.getValue("A").containsKey("cast_bubble_visible") && out.getValue("A")["cast_bubble_visible"] == null,
            "null TƯỜNG MINH là lựa chọn của hồ sơ (= xoá khoá lúc áp), không phải chỗ trống")
        assertEquals("160", out.getValue("B")["config_density_vn.vietmap.live"], "họ đã có mốc ⇒ của hồ sơ đó")
        assertFalse(out.getValue("B").containsKey("config_size_vn.vietmap.live"), "không rót nửa họ vào ảnh đã có mốc")
    }

    @Test
    fun `khoa dang VANG van duoc rot duoi dang null`() {
        val out = ProfileScopeMigration.rotDown(mapOf("A" to emptyMap()), mapOf("cast_enabled" to true), newKeys, families)
        val a = out.getValue("A")
        assertTrue(a.containsKey("cast_bubble_visible") && a["cast_bubble_visible"] == null,
            "bỏ khoá vắng ⇒ lượt áp giữ giá trị của hồ sơ vừa rời (bài học lịch dẫn đường 09-28)")
    }

    @Test
    fun `chay hai lan thi lan hai khong doi gi`() {
        val first = ProfileScopeMigration.rotDown(mapOf("A" to emptyMap(), "B" to emptyMap()), live, newKeys, families)
        val second = ProfileScopeMigration.rotDown(first, live + ("cast_enabled" to false), newKeys, families)
        assertTrue(second.isEmpty(), "lượt hai phải là no-op, kể cả khi giá trị sống đã đổi: $second")
    }

    @Test
    fun `nua ho khong moc bi thay bang ho song tron khoi`() {
        val shots = mapOf("A" to mapOf<String, Any?>("config_density_x.y" to "240"))
        val a = ProfileScopeMigration.rotDown(shots, live, newKeys, families).getValue("A")
        assertFalse(a.containsKey("config_density_x.y"), "nửa họ không mốc không phải lựa chọn của hồ sơ")
        assertEquals("1920x720", a["config_size_vn.vietmap.live"])
    }

    @Test
    fun `gia tri rot la lua chon hieu luc, ke ca khi dang co ban cho`() {
        val withPending = live + (CastEnableDeferral.PENDING_KEY to false)
        val a = ProfileScopeMigration.rotDown(mapOf("A" to emptyMap()), withPending, newKeys, families, ProfileScopeCluster.DEFERRED)
        assertEquals(false, a.getValue("A")["cast_enabled"])
        assertFalse(a.getValue("A").containsKey(CastEnableDeferral.PENDING_KEY))
    }

    @Test
    fun `gia tri ho hong khong duoc rot`() {
        val a = ProfileScopeMigration.rotDown(
            mapOf("A" to emptyMap()), live + ("config_density_a.b" to "240;reboot"), newKeys, families,
        ).getValue("A")
        assertFalse(a.containsKey("config_density_a.b"))
    }

    /** Tệp không có họ (camera, nút nổi) — chỉ khoá cố định. */
    @Test
    fun `tep khong co ho chi rot khoa co dinh`() {
        val cam = ProfileScopeCluster.MOVED_TO_PROFILE.getValue(ProfileScopeCluster.CLUSTERNAV_FILE)
        val a = ProfileScopeMigration.rotDown(
            mapOf("A" to emptyMap()), mapOf("camera_pos_left" to "TR", "camera_rot_left" to "90"), cam,
            ProfileScopeCluster.familiesOf(ProfileScopeCluster.CLUSTERNAV_FILE),
        ).getValue("A")
        assertEquals("TR", a["camera_pos_left"])
        assertFalse(a.containsKey("camera_rot_left"), "khoá camera theo XE không được rót vào hồ sơ")
        assertEquals(cam.toSet(), a.keys)
    }
}
