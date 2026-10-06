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

    // ── 2.92 · PROFILE-NEW-KEYS — khoá vào phạm vi hồ sơ ở bản SAU lượt rót của nó ────────────────────────────────

    private val cn = ProfileScopeCluster.CLUSTERNAV_FILE
    private val cnKeys = ProfileScope.CLUSTERNAV_KEYS.getValue(cn)
    private val newCam = listOf("camera_projection", "camera_zoom")

    /**
     * Khoá ca QA máy ảo 2.92 [ĐO]: hồ sơ «Mặc định» lưu bằng 2.9x (Nắn hình 0, chưa có khoá kiểu/thu phóng); ở hồ sơ
     * A chọn *Thẳng rộng* 120 % rồi đổi sang ⇒ «Mặc định» cũng *Thẳng rộng* 120 % (phải là *Gương cầu* 100 %).
     */
    @Test
    fun `ca QA 2_92 — kieu hinh va thu phong khong ro sang ho so luu bang ban cu`() {
        val old = mapOf<String, Any?>("camera_dewarp_amount" to 0, "camera_shape" to "RECT")
        val types = ProfileScopeCluster.DECLARED_TYPES
        val liveAfterChoice = mapOf<String, Any?>("camera_projection" to "WIDE", "camera_zoom" to 120, "camera_dewarp_amount" to 100)

        // Trước bản vá: lượt áp không chạm khoá vắng khỏi ảnh ⇒ giá trị của hồ sơ vừa rời ở lại (đúng bệnh đo được).
        val leak = ClusterSnapshotPlan.apply(liveAfterChoice, old, cnKeys, emptyList(), types, emptyMap())
        assertFalse("camera_projection" in leak.writes || "camera_zoom" in leak.writes, "mô tả bệnh: $leak")

        // Bản vá: lúc nâng cấp (khoá mới VẮNG ở tệp sống) rót `null` vào ảnh cũ ⇒ lượt áp XOÁ ⇒ về kiểu suy từ Nắn hình 0.
        val filled = ProfileScopeMigration.fillNewKeys(mapOf("Mặc định" to old), emptyMap(), newCam).getValue("Mặc định")
        assertTrue(filled.containsKey("camera_projection") && filled["camera_projection"] == null)
        assertTrue(filled.containsKey("camera_zoom") && filled["camera_zoom"] == null)
        assertEquals(0, filled["camera_dewarp_amount"], "khoá có sẵn trong ảnh không bị đụng")
        val edit = ClusterSnapshotPlan.apply(liveAfterChoice, filled, cnKeys, emptyList(), types, emptyMap())
        assertTrue(edit.writes.containsKey("camera_projection") && edit.writes["camera_projection"] == null)
        assertTrue(edit.writes.containsKey("camera_zoom") && edit.writes["camera_zoom"] == null)
        assertEquals(0, edit.writes["camera_dewarp_amount"])
        assertEquals("FISHEYE", com.byd.clusternav.launcher.camera.CameraViewMode.resolve(null, 0), "vắng + Nắn hình 0 ⇒ Gương cầu")
    }

    @Test
    fun `ho so CHUA co anh khong bi de anh, khong cham ho tien to`() {
        val shots = mapOf("A" to emptyMap(), "B" to mapOf<String, Any?>("cast_bubble_visible" to true))
        val out = ProfileScopeMigration.fillNewKeys(shots, live, listOf("cast_style"), ProfileScopeCluster.DEFERRED)
        assertEquals(setOf("B"), out.keys, "chưa có ảnh = bản sao của hiện tại (S4 · R5) — không đẻ ảnh cho nó")
        assertFalse(out.getValue("B").containsKey(marker), "họ có mốc riêng — lượt này không chạm")
        assertFalse(out.getValue("B").keys.any { it.startsWith("config_") }, "không rót họ")
    }

    @Test
    fun `khoa moi rot gia tri DANG SONG, chi dien cho trong, chay hai lan khong doi`() {
        val shots = mapOf(
            "A" to mapOf<String, Any?>("enabled" to true),
            "B" to mapOf<String, Any?>("vm_bubble_hidden" to false),
        )
        val live = mapOf<String, Any?>("vm_bubble_hidden" to true)
        val out = ProfileScopeMigration.fillNewKeys(shots, live, listOf("vm_bubble_hidden"))
        assertEquals(true, out.getValue("A")["vm_bubble_hidden"], "khoá đã có giá trị sống ⇒ ai cũng bắt đầu từ cái đang dùng")
        assertFalse("B" in out, "B đã có khoá (kể cả khác giá trị sống) ⇒ của B, không chạm")
        assertTrue(ProfileScopeMigration.fillNewKeys(shots + out, live + ("vm_bubble_hidden" to false), listOf("vm_bubble_hidden")).isEmpty())
    }

    @Test
    fun `so da rot — chi khoa chua co trong so, so day thi khong con gi`() {
        val scope = ProfileScope.CLUSTERNAV_KEYS
        val all = ProfileScopeMigration.ledgerOf(scope)
        assertTrue(ProfileScopeMigration.pendingKeys(scope, all).isEmpty())
        assertEquals(scope, ProfileScopeMigration.pendingKeys(scope, emptySet()), "sổ rỗng (lần đầu) ⇒ duyệt mọi khoá")
        val older = all - newCam.map { ProfileScopeMigration.ledgerEntry(cn, it) }.toSet()
        assertEquals(mapOf(cn to newCam), ProfileScopeMigration.pendingKeys(scope, older))
        assertTrue(all.none { it.count { c -> c == '/' } != 1 }, "mỗi mục đúng một dấu tách tệp/khoá")
    }

    /** Bốn khoá vào phạm vi hồ sơ SAU lượt rót V-CLUSTER (2.84) — phải nằm trong bảng mà sổ đã-rót duyệt. */
    @Test
    fun `bon khoa them sau 2_84 nam trong pham vi so da rot`() {
        val scope = ProfileScope.CLUSTERNAV_KEYS
        assertTrue(scope.getValue(cn).containsAll(newCam + "vm_bubble_hidden"), "${scope[cn]}")
        assertTrue("cast_style" in scope.getValue(ProfileScopeCluster.SIMPLE_CAST_FILE))
        assertEquals(ProfileScope.Scope.DEVICE, ProfileScope.scopeOf(ProfileScopeMigration.FILLED_LEDGER_KEY), "sổ theo XE")
    }

    // ── 2.93 · PROFILE-NEW-FILE-FILL — "đã chụp" trên MỌI tệp · họ tiền tố đi qua CÙNG sổ ─────────────────────────
    //
    // Senior review 2.92 Pass 1 (F1) khoá TẠM bảng tệp + họ bằng một bài canh ("thêm tệp/họ ⇒ đỏ, đọc KDoc trước khi sửa")
    // vì phép rót xét "chưa có ảnh" THEO TỆP và sổ không có mục cho họ. Nay phép rót đã generic ⇒ bài canh tạm được THAY
    // bằng các bài chạy thật dưới: tệp MỚI / họ MỚI được rót đúng một lần, hồ sơ chưa chụp ở đâu vẫn không bị đẻ ảnh.

    private val newFile = "kachi_new_prefs"

    @Test
    fun `tep prefs MOI - ho so da chup o tep khac duoc rot, ho so chua chup o dau khong`() {
        val shots = mapOf(
            cn to mapOf("A" to mapOf<String, Any?>("enabled" to true), "B" to emptyMap()),
            newFile to mapOf("A" to emptyMap(), "B" to emptyMap()),
        )
        val captured = ProfileScopeMigration.captured(shots)
        assertEquals(setOf("A"), captured, "đã chụp = ảnh khác rỗng ở BẤT KỲ tệp nào")
        val live = mapOf<String, Any?>("new_key" to 7)
        // Bệnh 2.92 (mô tả): xét "chưa có ảnh" theo tệp ⇒ không ai được rót, mà sổ vẫn ghi xong.
        assertTrue(ProfileScopeMigration.fillNewKeys(shots.getValue(newFile), live, listOf("new_key", "gone_key")).isEmpty())
        val out = ProfileScopeMigration.fillNewKeys(shots.getValue(newFile), live, listOf("new_key", "gone_key"), captured = captured)
        assertEquals(setOf("A"), out.keys, "B chưa chụp ở tệp nào = bản sao của hiện tại (S4 · R5) — không đẻ ảnh")
        assertEquals(7, out.getValue("A")["new_key"])
        assertTrue(out.getValue("A").containsKey("gone_key") && out.getValue("A")["gone_key"] == null, "vắng ở tệp sống ⇒ null tường minh")
    }

    /** Họ MỚI giả lập (tiền tố `tf_`) ở tệp chiếu cụm — đúng hình [ProfileScopeCluster.CAST_GEOMETRY]. */
    private val newFamily = SnapshotFamily(
        id = "test_family", file = sc, prefixes = listOf("tf_"), owns = { it.startsWith("tf_") }, valueOk = { _, v -> v != "hong" },
    )

    @Test
    fun `ho MOI di qua so - ho so da chup thieu moc nhan ho song va moc, ho so co moc giu`() {
        val scope = ProfileScopeMigration.ledgerScope(ProfileScope.CLUSTERNAV_KEYS, ProfileScopeCluster.FAMILIES + newFamily)
        val pending = ProfileScopeMigration.pendingKeys(scope, ProfileScopeMigration.ledgerOf(ProfileScope.CLUSTERNAV_KEYS))
        assertEquals(setOf(marker, newFamily.marker), pending.getValue(sc).toSet(), "sổ 2.92 chưa có mục họ ⇒ hai mốc chờ")
        val shots = mapOf(
            "A" to mapOf<String, Any?>(marker to true, "cast_enabled" to true),
            "B" to mapOf<String, Any?>(marker to true, newFamily.marker to true, "tf_x" to "của-B"),
        )
        val live = mapOf<String, Any?>("tf_x" to "sống", "tf_y" to "hong", "cast_enabled" to false)
        val out = ProfileScopeMigration.fillNewKeys(
            shots, live, pending.getValue(sc), ProfileScopeCluster.DEFERRED, setOf("A", "B"), ProfileScopeCluster.FAMILIES + newFamily,
        )
        assertEquals(setOf("A"), out.keys, "B đã có mốc CẢ hai họ ⇒ của B; A có mốc cast_geometry ⇒ họ cũ không bị rót lại")
        // Hồ sơ đã chụp mà THIẾU mốc một họ (ảnh nhập ở 2.84/2.85 từ tệp ≤ 2.83) ⇒ nhận họ SỐNG + mốc, đúng hợp đồng V-CLUSTER.
        val noMark = ProfileScopeMigration.fillNewKeys(
            mapOf("C" to mapOf<String, Any?>("cast_enabled" to true)), this.live, listOf(marker), captured = setOf("C"), families = families,
        ).getValue("C")
        assertEquals(true, noMark[marker]); assertEquals("320", noMark["config_density_vn.vietmap.live"])
        val a = out.getValue("A")
        assertEquals(true, a[newFamily.marker]); assertEquals("sống", a["tf_x"])
        assertFalse(a.containsKey("tf_y"), "giá trị họ hỏng không được rót")
        assertEquals(true, a["cast_enabled"], "khoá cố định có sẵn không bị đụng")
        assertFalse(a.keys.any { it.startsWith("config_") }, "họ cast_geometry đã có mốc ⇒ không rót")
    }

    @Test
    fun `nang cap tu so 2_92 - chi moc ho cu cho, anh da co moc khong doi byte`() {
        val scope = ProfileScopeMigration.ledgerScope(ProfileScope.CLUSTERNAV_KEYS, ProfileScopeCluster.FAMILIES)
        assertEquals(ProfileScope.CLUSTERNAV_KEYS.keys, scope.keys, "bảng sổ = đúng bảng tệp ảnh chụp")
        val pending = ProfileScopeMigration.pendingKeys(scope, ProfileScopeMigration.ledgerOf(ProfileScope.CLUSTERNAV_KEYS))
        assertEquals(mapOf(sc to listOf(marker)), pending, "xe đã chạy 2.92: chỉ mục họ cast_geometry là mới")
        val shots = mapOf("A" to mapOf<String, Any?>(marker to true, "config_density_a.b" to "240"))
        assertTrue(
            ProfileScopeMigration.fillNewKeys(shots, live, pending.getValue(sc), captured = setOf("A"), families = families).isEmpty(),
            "mọi ảnh ≥ 2.84 có mốc ⇒ lượt rót đầu của 2.93 không ghi gì",
        )
        assertTrue(ProfileScopeMigration.pendingKeys(scope, ProfileScopeMigration.ledgerOf(scope)).isEmpty(), "sổ đủ ⇒ hết việc")
        assertTrue(ProfileScopeMigration.ledgerOf(scope).none { it.count { c -> c == '/' } != 1 }, "mốc `@family:` không chứa '/'")
    }

    @Test
    fun `anh VANG cua ho so da chup duoc dung du khoa cua tep - ca nhap tep cu`() {
        val catalog = ProfileScopeCluster.CAST_CATALOG_FILE
        val keys = ProfileScope.CLUSTERNAV_KEYS.getValue(catalog)
        val live = mapOf<String, Any?>("bubbleX" to 120, "bubbleY" to 40)
        val chua = ProfileScopeMigration.fillNewKeys(mapOf(catalog to emptyMap()), live, keys)
        assertTrue(chua.isEmpty(), "tệp nhập không có ảnh ClusterNav nào ⇒ chưa chụp ⇒ giữ 'bản sao của hiện tại'")
        val da = ProfileScopeMigration.fillNewKeys(mapOf(catalog to emptyMap()), live, keys, captured = setOf(catalog))
        assertEquals(mapOf<String, Any?>("bubbleX" to 120, "bubbleY" to 40), da.getValue(catalog), "vị trí nút nổi của xe nhận")
    }

    /** Họ nằm ở tệp KHÔNG có hậu tố ảnh chụp thì không bao giờ được chụp/áp/rót — phải đỏ ngay khi ai khai như vậy. */
    @Test
    fun `moi ho deu nam o tep co anh chup`() {
        val files = ProfileScope.CLUSTERNAV_KEYS.keys
        assertTrue(ProfileScopeCluster.FAMILIES.all { it.file in files }, "${ProfileScopeCluster.FAMILIES} ⊄ $files")
        val scope = ProfileScopeMigration.ledgerScope(ProfileScope.CLUSTERNAV_KEYS, ProfileScopeCluster.FAMILIES)
        ProfileScopeCluster.FAMILIES.forEach { assertTrue(it.marker in scope.getValue(it.file), "$it thiếu mục sổ") }
    }
}
