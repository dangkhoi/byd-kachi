package com.byd.clusternav.launcher

import com.byd.clusternav.modules.clustercast.simplified.CastEnableDeferral
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ V-CLUSTER · VC-R2/R5/R8 — chụp–áp theo HỌ TIỀN TỐ có mốc, khoá HOÃN, kiểm KIỂU ═════════════════════════════
 *
 * Spec §11.4.2 / §11.4.7 / §11.6 V-1 · V-4 · V-5 · V-6 · V-8. Bài chạy trên **bảng thật** (`ProfileScope` +
 * `ProfileScopeCluster`), không trên bảng dựng riêng cho test — đổi bảng mà làm vỡ hợp đồng thì đỏ ở đây.
 *
 * [pour] đổ `Edit` vào một map y như tầng `:app` đổ vào `SharedPreferences.Editor` (`null` ⇒ `remove`).
 */
class ClusterSnapshotPlanTest {

    private val file = ProfileScopeCluster.SIMPLE_CAST_FILE
    private val fixed = ProfileScope.CLUSTERNAV_KEYS.getValue(file)
    private val families = ProfileScopeCluster.familiesOf(file)
    private val marker = ProfileScopeCluster.CAST_GEOMETRY.marker
    private val vm = "vn.vietmap.live"

    private fun geometry(dpi: String, pkg: String = vm) = mapOf(
        "config_size_$pkg" to "1920x720",
        "config_overscan_$pkg" to "0,0,0,0",
        "config_density_$pkg" to dpi,
        "config_bounds_$pkg" to "0,0,1920,720",
    )

    /** Chụp y như `snapshotClusterNav`: khoá cố định `associateWith` (giữ khoá vắng = null) + họ + khoá hoãn. */
    private fun shoot(live: Map<String, Any?>): Map<String, Any?> = PrefSnapshot.decode(
        PrefSnapshot.encode(
            ClusterSnapshotPlan.snapshot(fixed.associateWith { live[it] }, live, families, ProfileScopeCluster.DEFERRED).values,
        ),
    )

    private fun applyTo(live: Map<String, Any?>, shot: Map<String, Any?>): ClusterSnapshotPlan.Edit =
        ClusterSnapshotPlan.apply(
            live, shot.filterKeys { ClusterSnapshotPlan.inScope(it, fixed, families) }, fixed, families,
            ProfileScopeCluster.DECLARED_TYPES, ProfileScopeCluster.DEFERRED,
        )

    private fun pour(live: Map<String, Any?>, edit: ClusterSnapshotPlan.Edit): Map<String, Any?> =
        LinkedHashMap(live).apply { edit.writes.forEach { (k, v) -> if (v == null) remove(k) else put(k, v) } }

    // ── 1 · Chụp ───────────────────────────────────────────────────────────────────────────────

    @Test
    fun `anh chup giu khoa vang duoi dang null, mang ho va moc`() {
        val shot = shoot(geometry("320") + ("cast_enabled" to true))
        assertTrue(shot.containsKey("autostart_package") && shot["autostart_package"] == null, "khoá vắng ⇒ null tường minh")
        assertEquals("320", shot["config_density_$vm"])
        assertEquals(true, shot[marker], "ảnh mới luôn mang mốc có mặt của họ")
        assertFalse(shot.containsKey(CastEnableDeferral.PENDING_KEY), "khoá chờ theo XE — không bao giờ vào ảnh")
    }

    @Test
    fun `anh chup bo khoa ho hong, khong mang rac ra tep xuat`() {
        val live = geometry("320") + ("config_density_x.y" to "240;reboot") + ("config_density_$(id)" to "240")
        val r = ClusterSnapshotPlan.snapshot(fixed.associateWith { live[it] }, live, families, ProfileScopeCluster.DEFERRED)
        assertFalse(r.values.containsKey("config_density_x.y"))
        assertFalse(r.values.keys.any { '$' in it })
        assertEquals(listOf("config_density_x.y"), r.dropped, "giá trị hỏng phải được BÁO để ghi log")
    }

    @Test
    fun `anh chup cua khoa hoan la lua chon cua ho so = ban cho`() {
        val shot = shoot(mapOf("cast_enabled" to true, CastEnableDeferral.PENDING_KEY to false))
        assertEquals(false, shot["cast_enabled"])
    }

    // ── 2 · Áp: họ hai chiều (V-4) ────────────────────────────────────────────────────────────

    /** V-4 chiều 1: ảnh CŨ (chưa từng có họ) ⇒ không chạm tệp sống. Bỏ phép kiểm mốc ⇒ bài này đỏ. */
    @Test
    fun `ho so cu chua co ho — giu nguyen tep song`() {
        val live = geometry("320") + geometry("240", "com.google.android.apps.maps")
        val oldShot = mapOf<String, Any?>("split_ratio_left_pct" to 30)   // ảnh của bản trước V-CLUSTER
        val after = pour(live, applyTo(live, oldShot))
        geometry("320").forEach { (k, v) -> assertEquals(v, after[k], "khoá $k bị chạm dù ảnh không có mốc") }
        assertEquals(8, after.keys.count { it.startsWith("config_") })
    }

    /** V-4 chiều 2 (refute C4): ảnh CÓ mốc mà thiếu khoá K ⇒ K bị xoá — cấu hình của A không tràn sang B. */
    @Test
    fun `ho so co ho — khoa thua bi xoa, tep song bang dung ho cua anh`() {
        val live = geometry("320") + geometry("240", "com.google.android.apps.maps")
        val bShot = geometry("160") + (marker to true)
        val after = pour(live, applyTo(live, bShot))
        assertEquals(geometry("160"), after.filterKeys { it.startsWith("config_") })
    }

    @Test
    fun `moc sai kieu khong duoc coi la co moc`() {
        val live = geometry("320")
        val after = pour(live, applyTo(live, mapOf(marker to "true")))
        assertEquals(live, after, "mốc phải là Boolean true — chuỗi 'true' do sửa tay không mở quyền xoá cả họ")
    }

    // ── 3 · Đổi hồ sơ A→B→A (V-1 · V-6) ──────────────────────────────────────────────────────

    @Test
    fun `hai ho so khac DPI cung mot app — A sang B sang A tra dung A`() {
        var live: Map<String, Any?> = geometry("320")
        val a = shoot(live)
        live = pour(live, applyTo(live, geometry("240") + (marker to true)))
        assertEquals("240", live["config_density_$vm"], "V-1: tệp sống = DPI của B sau khi đổi")
        val b = shoot(live)
        live = pour(live, applyTo(live, a))
        assertEquals("320", live["config_density_$vm"], "V-6: quay về A ⇒ đúng 320")
        assertEquals("240", b["config_density_$vm"], "ảnh B giữ 240")
        assertEquals(true, a[marker]); assertEquals(true, b[marker])
    }

    // ── 4 · Khoá hoãn (V-8): lượt đổi hồ sơ KHÔNG ghi khoá sống ──────────────────────────────────

    @Test
    fun `doi sang ho so TAT khi dang BAT — chi ghi ban cho, khoa song giu nguyen`() {
        val live = mapOf<String, Any?>("cast_enabled" to true)
        val edit = applyTo(live, mapOf("cast_enabled" to false))
        assertFalse(edit.writes.containsKey("cast_enabled"), "ghi khoá sống lúc đổi hồ sơ = cụm hai chủ (lý do OQ2)")
        assertEquals(mapOf<String, Any?>(CastEnableDeferral.PENDING_KEY to false), edit.writes)
        val mid = pour(live, edit)
        val back = pour(mid, applyTo(mid, mapOf("cast_enabled" to true)))
        assertFalse(back.containsKey(CastEnableDeferral.PENDING_KEY), "đổi về hồ sơ khớp hiệu lực ⇒ bản chờ bị xoá")
        assertEquals(true, back["cast_enabled"])
    }

    @Test
    fun `ho so chua dat cast_enabled = mac dinh TAT`() {
        val edit = applyTo(mapOf("cast_enabled" to true), mapOf("cast_enabled" to null))
        assertEquals(mapOf<String, Any?>(CastEnableDeferral.PENDING_KEY to false), edit.writes)
    }

    // ── 5 · Kiểm KIỂU (vá [P1] có sẵn) + khoá cố định ─────────────────────────────────────────

    @Test
    fun `sai kieu bi bo — ca khi tep song dang vang khoa`() {
        val edit = applyTo(emptyMap(), mapOf("cast_bubble_visible" to "true", "split_ratio_left_pct" to "30"))
        assertTrue(edit.writes.isEmpty(), "chuỗi vào khoá Boolean/Int là ClassCastException trong dịch vụ: ${edit.writes}")
        assertEquals(2, edit.dropped.size)
    }

    /**
     * Khoá KHÔNG khai kiểu ⇒ so với kiểu của giá trị sống. (Senior review Pass 1: `enabled`/`badge_size_dp` nay đã khai
     * kiểu — xem bài ngay dưới — nên bài này đổi sang hai khoá vẫn chưa khai, giữ nguyên ý và độ chặt.)
     *
     * Senior review Pass 2: MỌI khoá của ảnh chụp thật nay đều khai kiểu (bài ngay dưới đòi điều đó), nên nhánh lùi "so
     * với kiểu sống" chỉ còn chạy được trên một khoá cố định DỰNG RIÊNG cho bài — phép tính thuần nhận `fixedKeys` làm
     * tham số. Ý và độ chặt giữ nguyên: sai kiểu so với tệp sống ⇒ bỏ; tệp sống vắng + không khai ⇒ ghi.
     */
    @Test
    fun `khoa khong khai kieu thi so voi kieu song`() {
        val fixedSynthetic = listOf("khoa_bool_chua_khai", "khoa_int_chua_khai")
        fixedSynthetic.forEach { assertTrue(it !in ProfileScopeCluster.DECLARED_TYPES, "$it phải CHƯA khai kiểu") }
        val edit = ClusterSnapshotPlan.apply(
            mapOf("khoa_bool_chua_khai" to true), mapOf("khoa_bool_chua_khai" to "yes", "khoa_int_chua_khai" to 2),
            fixedSynthetic, emptyList(), ProfileScopeCluster.DECLARED_TYPES, emptyMap(),
        )
        assertEquals(mapOf<String, Any?>("khoa_int_chua_khai" to 2), edit.writes)
        assertEquals(1, edit.dropped.size)
    }

    /**
     * Senior review Pass 2 — spec §11.8 [P1] (*"tệp nhập đặt sai kiểu MỘT khoá ClusterNav ⇒ ClassCastException trong dịch
     * vụ đang chạy"*) chỉ đóng hẳn khi KHÔNG còn khoá nào của ảnh chụp phải trông vào "kiểu sống" — tệp sống vắng khoá là
     * ca thường (chưa ai gán phím / chỉnh ghế). Thêm một khoá theo hồ sơ mà quên khai kiểu ⇒ đỏ tại đây.
     * (Kiểu ĐÚNG hay không do `ClusterProfileScopeCoverageTest` ở `:app` đối chiếu với lượt `put*` thật trong mã.)
     */
    @Test
    fun `moi khoa cua anh chup deu khai kieu — khong con khoa nao trong vao kieu song`() {
        assertEquals(emptySet<String>(), ProfileScope.CLUSTERNAV_PROFILE_KEYS - ProfileScopeCluster.DECLARED_TYPES.keys)
        val poisoned = mapOf<String, Any?>(
            "voicekey_bindings" to true, "seat_level_2" to "3", "pm25_filter_enabled" to "yes", "theme_choice" to 1,
        )
        val clusterFixed = ProfileScope.CLUSTERNAV_KEYS.getValue(ProfileScopeCluster.CLUSTERNAV_FILE) + "theme_choice"
        val edit = ClusterSnapshotPlan.apply(
            emptyMap(), poisoned, clusterFixed, emptyList(), ProfileScopeCluster.DECLARED_TYPES, emptyMap(),
        )
        assertTrue(edit.writes.isEmpty(), "tệp sống VẮNG khoá mà vẫn ghi sai kiểu ⇒ dịch vụ nổ: ${edit.writes}")
        val layer1 = ClusterSnapshotPlan.sanitize(poisoned, clusterFixed, emptyList(), ProfileScopeCluster.DECLARED_TYPES)
        assertTrue(layer1.values.isEmpty(), "lớp 1 (tệp nhập) phải bỏ trước khi chạm đĩa: ${layer1.values}")
    }

    /**
     * Senior review Pass 1 — nhóm "lên cụm" (dẫn đường · biển báo · bong bóng VietMap) đọc bằng `getBoolean`/`getInt`
     * trong DỊCH VỤ đang chạy. Tệp sống thường VẮNG chúng (chưa ai kéo bong bóng) ⇒ trước khi khai kiểu, một chuỗi từ tệp
     * nhập lọt qua lượt áp và nổ `ClassCastException` trên đường. Khai kiểu ⇒ bỏ ở cả lớp 1 (nhập) và lớp 2 (áp).
     */
    @Test
    fun `khoa len cum sai kieu bi bo ca khi tep song vang — lop 1 va lop 2`() {
        val clusterFixed = ProfileScope.CLUSTERNAV_KEYS.getValue(ProfileScopeCluster.CLUSTERNAV_FILE)
        val poisoned = mapOf<String, Any?>(
            "enabled" to "yes", "vm_bubble_x" to "abc", "badge_center_x" to "1", "nav_cluster_screen_mode" to true,
        )
        val edit = ClusterSnapshotPlan.apply(
            emptyMap(), poisoned, clusterFixed, emptyList(), ProfileScopeCluster.DECLARED_TYPES, emptyMap(),
        )
        assertTrue(edit.writes.isEmpty(), "sai kiểu vào tệp sống ⇒ ClassCastException trong dịch vụ: ${edit.writes}")
        assertEquals(poisoned.size, edit.dropped.size)
        val layer1 = ClusterSnapshotPlan.sanitize(poisoned, clusterFixed, emptyList(), ProfileScopeCluster.DECLARED_TYPES)
        assertTrue(layer1.values.isEmpty(), "lớp 1 (tệp nhập) phải bỏ trước khi chạm đĩa: ${layer1.values}")
        // Giá trị ĐÚNG kiểu đi qua y như cũ — khai kiểu không được làm mất cấu hình thật.
        val good = mapOf<String, Any?>("enabled" to true, "vm_bubble_x" to 1162, "badge_size_dp" to 120, "marquee" to false)
        assertEquals(good, ClusterSnapshotPlan.apply(
            emptyMap(), good, clusterFixed, emptyList(), ProfileScopeCluster.DECLARED_TYPES, emptyMap(),
        ).writes)
    }

    @Test
    fun `null tuong minh o khoa co dinh van la XOA`() {
        val edit = applyTo(mapOf("autostart_package" to "a.b"), mapOf("autostart_package" to null))
        assertTrue(edit.writes.containsKey("autostart_package") && edit.writes["autostart_package"] == null)
    }

    // ── 6 · Làm sạch tệp nhập (V-5) ───────────────────────────────────────────────────────────

    @Test
    fun `tep nhap doc — moi dang doc bi bo o lop 1`() {
        val poisoned = mapOf<String, Any?>(
            "config_density_$vm" to "240;reboot",
            "config_bounds_$vm" to "a,b,c,d",
            "config_size_$vm" to "1920x720 && rm -rf /",
            "config_overscan_$vm" to "'0,0,0,0'",
            "config_density_$(id)" to "240",
            "config_density_a.b\"" to "240",
            "config_bounds_a.b" to "-5,0,1920,720",
            "config_bounds_c.d" to "0,0,99999999,720",
            "cast_enabled" to "true",
            "cast_bubble_visible" to 1,
            marker to "yes",
            "khoa_la" to "x",
            "config_density_ok.app" to "320",
        )
        val r = ClusterSnapshotPlan.sanitize(poisoned, fixed, families, ProfileScopeCluster.DECLARED_TYPES)
        assertEquals(mapOf<String, Any?>("config_density_ok.app" to "320"), r.values)
        assertEquals(poisoned.size - 1, r.dropped.size)
    }

    @Test
    fun `tep nhap sach thi giu nguyen`() {
        val clean = shoot(geometry("320") + ("cast_enabled" to true) + ("split_ratio_left_pct" to 30))
        val r = ClusterSnapshotPlan.sanitize(clean, fixed, families, ProfileScopeCluster.DECLARED_TYPES)
        assertEquals(clean, r.values)
        assertTrue(r.dropped.isEmpty())
    }

    @Test
    fun `sua tay anh tren dia de lot lop 1 thi lop 2 van bo`() {
        val live = geometry("320")
        val edit = applyTo(live, mapOf(marker to true, "config_density_$vm" to "240;reboot"))
        assertNull(pour(live, edit)["config_density_$vm"], "giá trị độc không được ghi; khoá cũ bị xoá vì ảnh có mốc")
        assertTrue(edit.writes.values.none { it is String && (';' in it || '$' in it) })
        assertEquals(listOf("config_density_$vm"), edit.dropped)
    }

    @Test
    fun `khoa ngoai pham vi khong vao tep song`() {
        assertFalse(ClusterSnapshotPlan.inScope("khoa_la", fixed, families))
        assertFalse(ClusterSnapshotPlan.inScope(CastEnableDeferral.PENDING_KEY, fixed, families))
        assertTrue(ClusterSnapshotPlan.inScope(marker, fixed, families))
        assertTrue(ClusterSnapshotPlan.inScope("config_bounds_$vm", fixed, families))
    }
}
