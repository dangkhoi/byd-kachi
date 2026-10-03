package com.byd.clusternav.launcher

import com.byd.clusternav.modules.clustercast.simplified.CastBounds
import com.byd.clusternav.modules.clustercast.simplified.CastEnableDeferral
import com.byd.clusternav.modules.clustercast.simplified.CastGeometryGuard
import com.byd.clusternav.modules.clustercast.simplified.DisplayConfig
import com.byd.clusternav.modules.clustercast.simplified.SimpleCastState
import com.byd.clusternav.modules.clustercast.simplified.SlotState
import com.byd.clusternav.modules.clustercast.simplified.AppType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ FIX286 · PI1/PI2/PI3/PI5 — nhập hồ sơ GIỮ cấu hình chiếu cụm của xe nhận (spec `kachi-286-field-fixes.html` §3.3) ═
 *
 * Báo cáo hiện trường 2.84 `FIELD-285-0310`: nhập hồ sơ ⇒ *"phải bật lại + chỉnh khung tay"*. Gốc [ĐO mã]: tệp có mốc
 * họ mà không có bản ghi của app P ⇒ lượt đổi sang hồ sơ đó XOÁ khung của P trên xe nhận (`ClusterSnapshotPlan.apply`,
 * refute C4); tệp vắng `cast_enabled` ⇒ `null` ⇒ coi như TẮT. Owner 03/10 *"2 theo đề xuất"*: app tệp không có ⇒ GIỮ
 * khung của xe nhận — merge **một lần lúc nhập** ([ClusterSnapshotPlan.mergeImport]), lượt đổi hồ sơ không đổi byte.
 *
 * Bài chạy trên **bảng thật** (`ProfileScope` + `ProfileScopeCluster`) như `ClusterSnapshotPlanTest`; [pour] đổ `Edit`
 * vào map y như tầng `:app` đổ vào `SharedPreferences.Editor`.
 */
class ClusterImportMergeTest {

    private val file = ProfileScopeCluster.SIMPLE_CAST_FILE
    private val fixed = ProfileScope.CLUSTERNAV_KEYS.getValue(file)
    private val families = ProfileScopeCluster.familiesOf(file)
    private val marker = ProfileScopeCluster.CAST_GEOMETRY.marker
    private val p = "vn.vietmap.live"
    private val q = "com.google.android.apps.maps"

    private fun geometry(pkg: String, dpi: String, variant: String = "") = mapOf(
        "config_size_$pkg$variant" to "1920x720",
        "config_overscan_$pkg$variant" to "0,0,0,0",
        "config_density_$pkg$variant" to dpi,
        "config_bounds_$pkg$variant" to "0,0,1920,720",
    )

    /** Xe nhận của V-PI: khung P, Q, Q nửa trái 30 %, Cast đang BẬT. */
    private val car: Map<String, Any?> =
        geometry(p, "320") + geometry(q, "240") + geometry(q, "160", "__L30") + ("cast_enabled" to true)

    private fun merge(shot: Map<String, Any?>, live: Map<String, Any?> = car) =
        ClusterSnapshotPlan.mergeImport(shot, live, fixed, families, ProfileScopeCluster.DEFERRED)

    /** Chụp y như `snapshotClusterNav` (khoá cố định `associateWith`, giữ khoá vắng = null). */
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

    private fun geom(m: Map<String, Any?>) = m.filterKeys { it.startsWith("config_") }

    // ── PI1 · khung ─────────────────────────────────────────────────────────────────────────────────

    /** (a) Tệp có bản ghi của P ⇒ TỆP thắng — cả bản ghi, không trộn trường của xe vào (spec K4: bốn trường đi cùng). */
    @Test
    fun `a - app co trong tep thi lay gia tri cua tep, ca ban ghi`() {
        val fileP = mapOf("config_size_$p" to "1280x720", "config_density_$p" to "160")
        val m = merge(fileP + (marker to true))
        assertEquals("1280x720", m.values["config_size_$p"])
        assertEquals("160", m.values["config_density_$p"])
        assertFalse(m.values.containsKey("config_overscan_$p"), "trường xe có mà tệp không có KHÔNG được chép vào bản ghi của tệp")
        assertFalse(m.values.containsKey("config_bounds_$p"))
        assertEquals(setOf(p), m.fromFile)
        assertEquals(setOf(p), m.replacing, "xe đang có khung P khác ⇒ đổi sang hồ sơ này sẽ THAY")
    }

    /** (b) App chỉ xe nhận có ⇒ giữ khung của xe (gốc lỗi #2: trước đây lượt đổi hồ sơ XOÁ nó). */
    @Test
    fun `b - app chi xe co thi giu khung cua xe`() {
        val m = merge(geometry(p, "160") + (marker to true))
        geometry(q, "240").forEach { (k, v) -> assertEquals(v, m.values[k], "khung Q của xe nhận phải được giữ: $k") }
        assertTrue(q in m.fromCar)
        assertEquals(true, m.values[marker], "ảnh kết quả LUÔN mang mốc")
    }

    /** (c) Cùng gói, khác biến thể: tệp có Q toàn cụm nhưng không có Q nửa trái 30 % ⇒ giữ biến thể của xe. */
    @Test
    fun `c - cung goi khac bien the __L30 thi giu bien the cua xe`() {
        val m = merge(geometry(q, "320") + (marker to true))
        assertEquals("320", m.values["config_density_$q"], "toàn cụm của Q: tệp thắng")
        assertEquals("160", m.values["config_density_${q}__L30"], "Q nửa trái 30 %: tệp không có ⇒ của xe")
        assertEquals(setOf("${q}__L30", p), m.fromCar)
        assertEquals(setOf(q), m.fromFile)
    }

    /** (d) Tệp ≤ 2.83 (không mốc, không bản ghi) ⇒ toàn họ của xe + mốc. */
    @Test
    fun `d - tep khong moc thi toan ho cua xe va co moc`() {
        val m = merge(mapOf("split_ratio_left_pct" to 30))
        assertEquals(geom(car), geom(m.values))
        assertEquals(true, m.values[marker])
        assertFalse(m.fileHadFamily)
        assertTrue(m.fromFile.isEmpty())
        assertEquals(30, m.values["split_ratio_left_pct"], "khoá cố định của tệp giữ nguyên")
    }

    /** (e) Giá trị của xe hỏng (ghi trước 2.84 / sửa tay) ⇒ KHÔNG chép, báo [ClusterSnapshotPlan.ImportMerge.dropped]. */
    @Test
    fun `e - gia tri xe hong thi bo va bao dropped`() {
        val live = car + ("config_density_a.b" to "240;reboot") + ("config_bounds_a.b" to "9,9,1,1")
        val m = merge(mapOf(marker to true), live)
        assertFalse(m.values.containsKey("config_density_a.b"))
        assertFalse(m.values.containsKey("config_bounds_a.b"))
        assertEquals(setOf("config_density_a.b", "config_bounds_a.b"), m.dropped.toSet())
        assertFalse("a.b" in m.fromCar, "bản ghi không chép được giá trị nào thì không tính là 'giữ của xe'")
    }

    /**
     * (f) Merge CHỈ ở lượt nhập: sau đó đổi hồ sơ A ⇄ B vẫn HAI CHIỀU (refute C4 nguyên vẹn — `ClusterSnapshotPlanTest`
     * `ho so co ho — khoa thua bi xoa` không đổi). Kịch bản: xe đang ở A (car) · nhập B (tệp chỉ có P) · đổi A→B · chỉnh Q
     * trong B · đổi B→A ⇒ A về ĐÚNG khung của A; rồi đổi sang C (có mốc, chỉ có P) ⇒ khung Q bị xoá như cũ.
     */
    @Test
    fun `f - doi ho so sau khi nhap van hai chieu nhu cu`() {
        val a = shoot(car)
        val b = PrefSnapshot.decode(PrefSnapshot.encode(merge(geometry(p, "160") + (marker to true)).values))
        var live = pour(car, applyTo(car, b))
        assertEquals("160", live["config_density_$p"]); assertEquals("240", live["config_density_$q"])
        live = live + ("config_density_$q" to "200")                 // người lái chỉnh Q khi đang ở B
        val bAfter = shoot(live)
        live = pour(live, applyTo(live, a))
        assertEquals(geom(car), geom(live), "về A ⇒ đúng khung của A, không mang chỉnh sửa của B")
        assertEquals("200", bAfter["config_density_$q"], "ảnh B giữ chỉnh sửa của B")
        val c = geometry(p, "320") + (marker to true)
        live = pour(live, applyTo(live, c))
        assertEquals(geometry(p, "320"), geom(live), "hồ sơ có mốc thiếu Q ⇒ Q bị XOÁ (refute C4, đổi hồ sơ thường)")
    }

    @Test
    fun `merge khong sua dau vao`() {
        val shot = geometry(p, "160") + (marker to true)
        val shotCopy = LinkedHashMap(shot)
        val carCopy = LinkedHashMap(car)
        merge(shot)
        assertEquals(shotCopy, shot); assertEquals(carCopy, car)
    }

    // ── PI2 · cast_enabled ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `PI2 - cast_enabled vang hoac null thi lay gia tri DANG CHAY cua xe, khong lay ban cho`() {
        val live = car + (CastEnableDeferral.PENDING_KEY to false)          // hồ sơ đang dùng muốn TẮT, đang chạy BẬT
        listOf(mapOf<String, Any?>(), mapOf("cast_enabled" to null)).forEach { shot ->
            val m = merge(shot, live)
            assertEquals(true, m.values["cast_enabled"], "giá trị đang chạy, không phải bản chờ: $shot")
            assertEquals(setOf("cast_enabled"), m.deferredFromCar)
            assertFalse(m.values.containsKey(CastEnableDeferral.PENDING_KEY), "khoá chờ theo XE — không vào ảnh")
        }
    }

    @Test
    fun `PI2 - tep co gia tri thi tep thang`() {
        listOf(true, false).forEach { v ->
            val m = merge(mapOf("cast_enabled" to v))
            assertEquals(v, m.values["cast_enabled"])
            assertTrue(m.deferredFromCar.isEmpty())
        }
    }

    @Test
    fun `PI2 - xe chua tung dat cast_enabled thi anh mang null = mac dinh`() {
        val m = merge(emptyMap(), geometry(p, "320"))
        assertTrue(m.values.containsKey("cast_enabled") && m.values["cast_enabled"] == null)
    }

    /** Ca lỗi hiện trường: tệp vắng ⇒ trước đây đổi sang hồ sơ nhập ghi bản chờ TẮT. Nay: không bản chờ nào. */
    @Test
    fun `PI2 - doi sang ho so nhap vang cast_enabled khong de ra ban cho`() {
        val m = merge(mapOf("cast_enabled" to null))
        val edit = applyTo(car, m.values)
        assertFalse(edit.writes.containsKey(CastEnableDeferral.PENDING_KEY))
        assertEquals(CastEnableDeferral.OnApply.ClearPending, edit.deferred["cast_enabled"])
        // Đối chứng: KHÔNG merge thì đúng là lỗi 2.84 — bản chờ TẮT.
        assertEquals(false, applyTo(car, mapOf("cast_enabled" to null)).writes[CastEnableDeferral.PENDING_KEY])
    }

    // ── PI3/PI5 · tóm tắt + log ─────────────────────────────────────────────────────────────────────

    @Test
    fun `tom tat dem theo APP va noi dung nguon cua cast`() {
        val shot = geometry(p, "160") + geometry(p, "240", "__R70") + (marker to true)
        val s = ClusterImportSummary.of(merge(shot))
        assertTrue(s.hasClusterPart)
        assertEquals(1, s.fileApps, "P toàn cụm + P nửa phải = MỘT app")
        assertEquals(2, s.fileRecords)
        assertEquals(1, s.replacingApps, "chỉ P toàn cụm có trên xe với giá trị khác")
        assertEquals(1, s.replacingRecords)
        assertEquals(1, s.keptApps, "Q (toàn cụm + nửa trái) = MỘT app")
        assertEquals(2, s.keptRecords)
        assertFalse(s.castFromFile); assertTrue(s.castOn)
        val line = s.logLine("X", ProfileTransfer.Kind.SHARE)
        listOf("import «X»", "kind=SHARE", "có phần cụm", "cast_enabled=xe(giữ):BẬT", "khung tệp 1 app/2 bản ghi (thay 1 app/1 bản ghi)",
            "giữ của xe 1 app/2 bản ghi").forEach { assertTrue(line.contains(it), "log thiếu `$it`: $line") }
    }

    @Test
    fun `tom tat tep cu noi khong co phan cum`() {
        val s = ClusterImportSummary.of(merge(emptyMap()))
        assertFalse(s.hasClusterPart)
        assertEquals(0, s.fileApps); assertEquals(2, s.keptApps)
        assertTrue(s.logLine("Y", ProfileTransfer.Kind.FULL).contains("KHÔNG có phần cụm"))
    }

    /**
     * [ĐO máy ảo 02/10, V-PI tệp (c)]: tệp có Maps TOÀN CỤM, xe còn Maps NỬA TRÁI ⇒ "giữ khung cho 1 APP mà tệp không có"
     * là sai (tệp CÓ Maps). Hộp thoại đếm theo KHUNG — bài này khoá hai số đếm khác nhau đúng ở ca đó.
     */
    @Test
    fun `khung giu cua xe dem theo KHUNG khi tep co cung app khac bien the`() {
        val s = ClusterImportSummary.of(merge(geometry(p, "240") + geometry(q, "320") + (marker to true)))
        assertEquals(1, s.keptRecords, "Maps nửa trái 30 % — một KHUNG của xe tệp không có")
        assertEquals(2, s.fileRecords); assertEquals(2, s.replacingRecords)
    }

    @Test
    fun `cung khung thi khong tinh la thay`() {
        val s = ClusterImportSummary.of(merge(geometry(p, "320") + (marker to true) + ("cast_enabled" to false)))
        assertEquals(0, s.replacingApps); assertEquals(0, s.replacingRecords)
        assertTrue(s.castFromFile); assertFalse(s.castOn)
    }

    @Test
    fun `describe noi cast va so khung ghi xoa cua luot doi ho so`() {
        val edit = applyTo(car, geometry(p, "160") + (marker to true) + ("cast_enabled" to false))
        val line = ClusterSnapshotPlan.describe(edit, families, ProfileScopeCluster.DEFERRED)!!
        assertTrue(line.startsWith("cast=SetPending(on=false) (ghi khoá chờ 1)"), line)
        assertTrue(line.contains("khung ghi 1") && line.contains("xoá 8"), "1 trường đổi (density P), 8 khoá Q bị xoá: $line")
        val same = applyTo(car, shoot(car))
        assertEquals("cast=ClearPending (ghi khoá chờ 0) · khung ghi 0 · xoá 0 · bỏ 0",
            ClusterSnapshotPlan.describe(same, families, ProfileScopeCluster.DEFERRED))
        val other = ClusterSnapshotPlan.apply(
            emptyMap(), mapOf("enabled" to true), listOf("enabled"), emptyList(), ProfileScopeCluster.DECLARED_TYPES,
            ProfileScopeCluster.DEFERRED,
        )
        assertNull(ClusterSnapshotPlan.describe(other, emptyList(), ProfileScopeCluster.DEFERRED), "tệp không họ/không hoãn ⇒ im")
    }

    @Test
    fun `ten app cua ban ghi bo hau to nua cum`() {
        assertEquals(p, CastGeometryGuard.appOfRecord("${p}__L30"))
        assertEquals(p, CastGeometryGuard.appOfRecord("${p}__R90"))
        assertEquals(p, CastGeometryGuard.appOfRecord(p))
        assertEquals("${p}__L35", CastGeometryGuard.appOfRecord("${p}__L35"), "tỉ lệ ngoài SPLIT_PERCENTS không phải biến thể")
    }

    // ── PI3 · "Dùng ngay" chỉ áp chiếu cụm khi cụm không đang chiếu app ───────────────────────────────

    @Test
    fun `Dung ngay ap ban cho chi khi cum Off hoac Idle`() {
        val cfg = DisplayConfig("1920x720", "0,0,0,0", "240", CastBounds(0, 0, 1920, 720))
        assertEquals(true, CastEnableDeferral.applyOnUse(true, SimpleCastState.Off))
        assertEquals(false, CastEnableDeferral.applyOnUse(false, SimpleCastState.Idle))
        listOf(
            SimpleCastState.Opening, SimpleCastState.Stopping, SimpleCastState.Closing, SimpleCastState.Error("x"),
            SimpleCastState.CastingFull(p, AppType.NORMAL, cfg),
            SimpleCastState.CastingSplit(SlotState(p, cfg), null),
        ).forEach { s -> assertNull(CastEnableDeferral.applyOnUse(true, s), "đang chiếu/chuyển trạng thái ⇒ không áp: $s") }
        assertNull(CastEnableDeferral.applyOnUse(null, SimpleCastState.Off), "không có gì chờ ⇒ không làm gì")
    }

    // ── PI5 · mốc bền lượt chốt lúc khởi động ────────────────────────────────────────────────────────

    @Test
    fun `moc chot ma hoa va doc lai, doc hong thi null`() {
        val m = CastEnableDeferral.CommitMark(true, 1_759_400_000_000, 187, "2.86")
        assertEquals(m, CastEnableDeferral.CommitMark.decode(m.encode()))
        assertEquals("2.86x", CastEnableDeferral.CommitMark(false, 1, 1, "2.86|x").let { CastEnableDeferral.CommitMark.decode(it.encode())!!.versionName })
        listOf(null, 5, "", "1|2|3", "2|1|1|v", "1|-5|1|v", "1|x|1|v", "1|1|-1|v", "1|1|1|v|extra").forEach {
            assertNull(CastEnableDeferral.CommitMark.decode(it), "đọc hỏng phải ra null, không ném: $it")
        }
    }

    @Test
    fun `moc chot la khoa theo XE va khong vao anh chup`() {
        assertEquals(ProfileScope.Scope.DEVICE, ProfileScope.scopeOf(CastEnableDeferral.COMMIT_MARK_KEY))
        assertFalse(shoot(car + (CastEnableDeferral.COMMIT_MARK_KEY to "1|1|1|v")).containsKey(CastEnableDeferral.COMMIT_MARK_KEY))
    }
}
