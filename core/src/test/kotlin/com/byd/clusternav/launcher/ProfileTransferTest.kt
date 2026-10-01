package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ PROFILE-IO-0930 · IO-R3/R4/R5 — định dạng, lọc chia sẻ, chọn ảnh chụp khi xuất, kế hoạch nhập ═══════════════
 *
 * Spec `docs/specs/kachi-profiles-are-everything.html` §12. [Car] là bản mô phỏng MỘT chiếc xe: `stored` = tệp
 * `kachi_workspace`, `live` = các tệp prefs ClusterNav mà dịch vụ đọc. Chụp/áp/đổi hồ sơ/nhập đi đúng thứ tự và đúng
 * phép thuần mà `:app` gọi (`snapshotClusterNav` · `applyClusterNav` · `switchProfile` · `importProfile` +
 * `cleanImportedSnapshot`) — nên bài kiểm được NGỮ NGHĨA "khoá vắng = không chạm · `null` = xoá" qua nhiều lượt đổi hồ
 * sơ, không chỉ hình dạng chuỗi.
 */
class ProfileTransferTest {

    private val cn = ProfileScopeCluster.CLUSTERNAV_FILE
    private val cnSuffix = ProfileScope.snapshotSuffix(cn)
    private val castSuffix = ProfileScope.snapshotSuffix(ProfileScopeCluster.SIMPLE_CAST_FILE)
    private val marker = ProfileScopeCluster.CAST_GEOMETRY.marker

    private class Car {
        val stored = LinkedHashMap<String, Any?>()
        val live: Map<String, MutableMap<String, Any?>> =
            ProfileScope.CLUSTERNAV_KEYS.keys.associateWith { LinkedHashMap<String, Any?>() }
        val profiles = mutableListOf("A", "B")
        var active = "A"
        val snapshotCalls = mutableListOf<String>()

        fun key(p: String, s: String) = "${p}__$s"

        /** = `WorkspacePrefs.snapshotClusterNav`. */
        fun snapshot(profile: String) {
            snapshotCalls += profile
            ProfileScope.CLUSTERNAV_KEYS.forEach { (file, keys) ->
                val all = live.getValue(file)
                val shot = ClusterSnapshotPlan.snapshot(
                    keys.associateWith { all[it] }, all, ProfileScopeCluster.familiesOf(file), ProfileScopeCluster.DEFERRED,
                )
                stored[key(profile, ProfileScope.snapshotSuffix(file))] = PrefSnapshot.encode(shot.values)
            }
        }

        /** = `WorkspacePrefs.applyClusterNav`. */
        fun apply(profile: String) {
            ProfileScope.CLUSTERNAV_KEYS.forEach { (file, keys) ->
                val raw = stored[key(profile, ProfileScope.snapshotSuffix(file))] as? String ?: return@forEach
                val families = ProfileScopeCluster.familiesOf(file)
                val values = PrefSnapshot.decode(raw).filterKeys { ClusterSnapshotPlan.inScope(it, keys, families) }
                if (values.isEmpty()) return@forEach
                val target = live.getValue(file)
                val plan = ClusterSnapshotPlan.apply(
                    target, values, keys, families, ProfileScopeCluster.DECLARED_TYPES, ProfileScopeCluster.DEFERRED,
                )
                plan.writes.forEach { (k, v) -> if (v == null) target.remove(k) else target[k] = v }
            }
        }

        /** = `PrefsWorkspaceRepository.switchProfile`: chụp hồ sơ đang dùng TRƯỚC, rồi áp hồ sơ đích. */
        fun switchTo(profile: String) {
            snapshot(active)
            active = profile
            apply(profile)
        }

        val source = object : ProfileTransfer.Source {
            override fun stored(): Map<String, *> = LinkedHashMap(stored)
            override fun keyOf(profile: String, suffix: String) = key(profile, suffix)
            override fun snapshotLive(profile: String) = snapshot(profile)
        }

        fun export(profile: String, kind: ProfileTransfer.Kind) = ProfileTransfer.export(source, profile, active, kind)

        /** = `WorkspacePrefs.importProfile` + `cleanImportedSnapshot`. */
        fun import(data: String): String? {
            val plan = ProfileTransfer.planImport(data, null, profiles) ?: return null
            plan.writes.forEach { (suffix, v) ->
                val file = ProfileScope.CLUSTERNAV_KEYS.keys.firstOrNull { ProfileScope.snapshotSuffix(it) == suffix }
                val clean = if (file == null || v !is String) {
                    if (file != null) null else v
                } else {
                    PrefSnapshot.encode(
                        ClusterSnapshotPlan.sanitize(
                            PrefSnapshot.decode(v), ProfileScope.CLUSTERNAV_KEYS.getValue(file),
                            ProfileScopeCluster.familiesOf(file), ProfileScopeCluster.DECLARED_TYPES,
                        ).values,
                    )
                }
                if (clean == null) stored.remove(key(plan.target, suffix)) else stored[key(plan.target, suffix)] = clean
            }
            profiles += plan.target
            return plan.target
        }
    }

    /** A đang dùng, có sổ địa chỉ + lịch; B không dùng, có ảnh chụp riêng (lịch khác, cụm tắt). */
    private fun car(): Car = Car().apply {
        live.getValue(cn).putAll(
            mapOf(
                "nav_automation_rules" to "RULES-A", "nav_automation_fired" to "FIRED-A", "enabled" to true,
                "badge_size_dp" to 48, "voicekey_bindings" to "[{\"k\":1,\"t\":\"x\"}]",
            ),
        )
        live.getValue(ProfileScopeCluster.SIMPLE_CAST_FILE)["config_density_vn.vietmap.live"] = "320"
        stored[key("A", "saved_places")] = "PLACES-A"
        stored[key("A", "preset")] = "p2"
        stored[key("B", "saved_places")] = "PLACES-B"
        stored[key("B", "preset")] = "p3"
        // Ảnh của B dựng bằng chính phép chụp, trên một tệp sống của B.
        val keepA = live.getValue(cn).toMap()
        live.getValue(cn).clear()
        live.getValue(cn).putAll(mapOf("nav_automation_rules" to "RULES-B", "enabled" to false))
        snapshot("B")
        live.getValue(cn).clear()
        live.getValue(cn).putAll(keepA)
        snapshotCalls.clear()
    }

    private fun body(file: String): Map<String, Any?> = PrefSnapshot.decode(file.split("\n", limit = 2)[1])

    private fun head(file: String): ProfileTransfer.Header? = ProfileTransfer.parseHeader(file.split("\n", limit = 2)[0])

    private fun shotIn(file: String, suffix: String = cnSuffix): Map<String, Any?> =
        PrefSnapshot.decode(body(file)[suffix] as String)

    // ── Header ──────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `header moi co kieu, tep v1 cu ba truong doc nhu day du`() {
        assertEquals("kachi-profile\tv1\tA B\tshare", ProfileTransfer.encodeHeader("A\tB", ProfileTransfer.Kind.SHARE))
        assertEquals(
            ProfileTransfer.Header("Mặc định", ProfileTransfer.Kind.FULL),
            ProfileTransfer.parseHeader("kachi-profile\tv1\tMặc định"),
        )
        assertEquals(ProfileTransfer.Kind.SHARE, ProfileTransfer.parseHeader("kachi-profile\tv1\tX\tshare\r")?.kind)
        assertEquals(ProfileTransfer.Kind.FULL, ProfileTransfer.parseHeader("\uFEFFkachi-profile\tv1\tX\tfull")?.kind)
    }

    @Test
    fun `header la bi tu choi, khong doan`() {
        listOf(
            "kachi-profile\tv1\tX\tsecret", "kachi-profile\tv2\tX\tshare", "kachi-profile\tv1\t  ", "kachi-profile\tv1",
            "other\tv1\tX", "",
        ).forEach { assertNull(ProfileTransfer.parseHeader(it), it) }
    }

    /**
     * Senior review lượt 1 [P2] — tên hồ sơ là TIỀN TỐ mọi khoá của nó: header tệp độc dài ~1 MB (dưới trần đọc) thành
     * hàng chục MB trong `kachi_workspace.xml`. Tên đi qua tệp bị cắt ở [ProfileTransfer.MAX_NAME_CHARS] (không để nửa
     * cặp surrogate), ký tự điều khiển (C0/DEL/C1, không chỉ tab/CR/LF) thành khoảng trắng.
     */
    @Test
    fun `ten tu tep bi cat tran va bo ky tu dieu khien`() {
        val max = ProfileTransfer.MAX_NAME_CHARS
        assertEquals("A B C D", ProfileTransfer.parseHeader("kachi-profile\tv1\tA\u0000B\u007FC\u0085D")?.name)
        assertEquals("x".repeat(max), ProfileTransfer.parseHeader("kachi-profile\tv1\t" + "x".repeat(200_000))?.name)
        val emoji = "x".repeat(max - 1) + "🚗"
        assertEquals("x".repeat(max - 1), ProfileTransfer.parseHeader("kachi-profile\tv1\t$emoji\tshare")?.name, "không để nửa cặp")
        val huge = "kachi-profile\tv1\t" + "y".repeat(500_000) + "\n" + PrefSnapshot.encode(mapOf("preset" to "p1"))
        val plan = ProfileTransfer.planImport(huge, null, listOf("y".repeat(max)))!!
        assertEquals("y".repeat(max) + " 2", plan.target)
        assertEquals("p1", plan.writes["preset"])
        assertEquals("", ProfileTransfer.clip("abc", 0))
        assertEquals("abc", ProfileTransfer.clip("abc", 3))
    }

    // ── IO-R5 · xuất hồ sơ KHÔNG đang dùng ─────────────────────────────────────────────────────

    /** Lỗi ẩn #4: xuất B khi đang ở A chụp tệp sống (của A) vào ảnh B ⇒ mang cấu hình A và ghi đè ảnh B vĩnh viễn. */
    @Test
    fun `xuat ho so khong dang dung khong chup tep song va giu nguyen anh da luu`() {
        val c = car()
        val before = LinkedHashMap(c.stored)
        val out = c.export("B", ProfileTransfer.Kind.FULL)
        assertTrue(c.snapshotCalls.isEmpty(), "không được chụp tệp sống cho hồ sơ không đang dùng")
        assertEquals(before, c.stored, "ảnh đã lưu của B phải giữ nguyên từng byte")
        val shot = shotIn(out)
        assertEquals("RULES-B", shot["nav_automation_rules"], "phải mang lịch của CHÍNH B")
        assertEquals(false, shot["enabled"])
        assertEquals("PLACES-B", body(out)["saved_places"])
        assertEquals("B", head(out)?.name)
    }

    @Test
    fun `xuat ho so dang dung chup tep song truoc cho tuoi`() {
        val c = car()
        c.live.getValue(cn)["nav_automation_rules"] = "RULES-A-2"
        val out = c.export("A", ProfileTransfer.Kind.FULL)
        assertEquals(listOf("A"), c.snapshotCalls)
        assertEquals("RULES-A-2", shotIn(out)["nav_automation_rules"])
        assertEquals("PLACES-A", body(out)["saved_places"])
    }

    // ── IO-R3 · bản chia sẻ ────────────────────────────────────────────────────────────────────

    @Test
    fun `ban chia se bo so dia chi va lich, giu phan con lai`() {
        val out = car().export("A", ProfileTransfer.Kind.SHARE)
        assertEquals(ProfileTransfer.Kind.SHARE, head(out)?.kind)
        val b = body(out)
        assertFalse(b.containsKey("saved_places"), "sổ địa chỉ không được vào tệp chia sẻ")
        assertEquals("p2", b["preset"])
        val shot = shotIn(out)
        listOf("nav_automation_rules", "nav_automation_fired").forEach {
            assertTrue(shot.containsKey(it) && shot[it] == null, "`$it` phải là null TƯỜNG MINH (không phải vắng)")
        }
        assertEquals(true, shot["enabled"])
        assertEquals(48, shot["badge_size_dp"])
        assertFalse(out.contains("RULES-A") || out.contains("FIRED-A") || out.contains("PLACES-A"), "rò dữ liệu riêng tư")
        val cast = shotIn(out, castSuffix)
        assertEquals("320", cast["config_density_vn.vietmap.live"])
        assertEquals(true, cast[marker], "mốc họ phải đi cùng")
    }

    @Test
    fun `ban chia se tu ho so chua co anh van mang null cho lich`() {
        val out = ProfileTransfer.forShare(mapOf("preset" to "p1", "saved_places" to "X"))
        assertEquals(setOf("preset", cnSuffix), out.keys)
        val shot = PrefSnapshot.decode(out[cnSuffix] as String)
        assertEquals(mapOf("nav_automation_fired" to null, "nav_automation_rules" to null), shot)
    }

    /** Lượt 2 [P3] — mốc họ khớp NGUYÊN chuỗi: một khoá lạ mang tiền tố `@family:` không đi theo vào bản chia sẻ. */
    @Test
    fun `ban chia se chi giu moc ho that`() {
        val cast = PrefSnapshot.encode(
            mapOf(marker to true, "@family:la" to "RIENG-TU", "config_density_vn.vietmap.live" to "320"),
        )
        val shot = PrefSnapshot.decode(ProfileTransfer.forShare(mapOf(castSuffix to cast))[castSuffix] as String)
        assertEquals(mapOf(marker to true, "config_density_vn.vietmap.live" to "320"), shot)
    }

    // ── IO-R4 · nhập bản chia sẻ ───────────────────────────────────────────────────────────────

    @Test
    fun `nhap ban chia se khong xoa gi cua ho so khac va doi qua lai khong mat lich`() {
        val c = car()
        c.snapshot("A")
        c.snapshotCalls.clear()
        val share = c.export("A", ProfileTransfer.Kind.SHARE)
        val before = LinkedHashMap(c.stored)
        val s = c.import(share)
        assertEquals("A 2", s)
        before.forEach { (k, v) -> assertEquals(v, c.stored[k], "nhập đã chạm khoá `$k` của hồ sơ khác") }
        assertFalse(c.stored.containsKey(c.key("A 2", "saved_places")), "hồ sơ nhập không có sổ địa chỉ")

        c.switchTo("A 2")
        assertFalse(c.live.getValue(cn).containsKey("nav_automation_rules"), "hồ sơ chia sẻ không thừa hưởng lịch của A")
        assertFalse(c.live.getValue(cn).containsKey("nav_automation_fired"))
        assertEquals(true, c.live.getValue(cn)["enabled"], "cấu hình cụm chia sẻ được vẫn đi theo")
        assertEquals("RULES-A", PrefSnapshot.decode(c.stored[c.key("A", cnSuffix)] as String)["nav_automation_rules"])

        c.switchTo("A")
        assertEquals("RULES-A", c.live.getValue(cn)["nav_automation_rules"], "về A phải có lại lịch của A")
        assertEquals("FIRED-A", c.live.getValue(cn)["nav_automation_fired"])
        c.switchTo("B")
        assertEquals("RULES-B", c.live.getValue(cn)["nav_automation_rules"])
        c.switchTo("A 2")
        assertFalse(c.live.getValue(cn).containsKey("nav_automation_rules"))
        c.switchTo("B")
        assertEquals("RULES-B", c.live.getValue(cn)["nav_automation_rules"], "lịch của B còn nguyên sau khi qua hồ sơ nhập")
        assertEquals("PLACES-A", c.stored[c.key("A", "saved_places")])
        assertEquals("PLACES-B", c.stored[c.key("B", "saved_places")])
    }

    /** Tệp ghi `share` nhưng bị sửa tay nhét địa chỉ/lịch vào ⇒ lượt nhập lọc lại theo cùng bảng. */
    @Test
    fun `tep chia se sua tay khong lach duoc bo loc`() {
        val shot = PrefSnapshot.encode(mapOf("nav_automation_rules" to "SNEAKY", "enabled" to true))
        val data = ProfileTransfer.encodeHeader("X", ProfileTransfer.Kind.SHARE) + "\n" +
            PrefSnapshot.encode(mapOf("saved_places" to "SNEAKY", "preset" to "p1", cnSuffix to shot))
        val plan = ProfileTransfer.planImport(data, null, emptyList())!!
        assertNull(plan.writes["saved_places"])
        val cleaned = PrefSnapshot.decode(plan.writes[cnSuffix] as String)
        assertTrue(cleaned.containsKey("nav_automation_rules") && cleaned["nav_automation_rules"] == null)
        assertEquals(true, cleaned["enabled"])
        assertEquals("p1", plan.writes["preset"])
    }

    // ── Kế hoạch nhập ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `tep v1 cu nhap nhu ban day du, giu so dia chi`() {
        val data = "kachi-profile\tv1\tCũ\n" + PrefSnapshot.encode(mapOf("saved_places" to "P", "preset" to "p1"))
        val plan = ProfileTransfer.planImport(data, null, listOf("Cũ"))!!
        assertEquals("Cũ 2", plan.target)
        assertEquals(ProfileTransfer.Kind.FULL, plan.kind)
        assertEquals("P", plan.writes["saved_places"])
    }

    @Test
    fun `ke hoach nhap phu du moi hau to, bo hau to la, ten duy nhat`() {
        val data = ProfileTransfer.encodeHeader("X", ProfileTransfer.Kind.FULL) + "\n" +
            PrefSnapshot.encode(mapOf("preset" to "p1", "active_profile" to "pwn", "../evil" to "x"))
        val plan = ProfileTransfer.planImport(data, null, listOf("X", "X 2"))!!
        assertEquals("X 3", plan.target)
        assertEquals(ProfileScope.LAUNCHER_SUFFIXES.toSet(), plan.writes.keys, "mọi hậu tố có mặt (vắng ⇒ null ⇒ remove)")
        assertEquals("p1", plan.writes["preset"])
        assertNull(plan.writes["grid_layout"])
        assertEquals("Tên riêng", ProfileTransfer.planImport(data, " Tên riêng ", listOf("X"))!!.target)
    }

    @Test
    fun `tep hong bi tu choi`() {
        listOf("", "kachi-profile\tv1\tX", "junk\nb|preset|true", "kachi-profile\tv1\tX\tsecret\ns|preset|p").forEach {
            assertNull(ProfileTransfer.planImport(it, null, emptyList()), it)
        }
    }

    @Test
    fun `tep luu lai kieu Windows CRLF va BOM van nhap du`() {
        val data = "\uFEFF" + (ProfileTransfer.encodeHeader("W", ProfileTransfer.Kind.FULL) + "\n" +
            PrefSnapshot.encode(mapOf("dock_visible" to true, "preset" to "p1"))).replace("\n", "\r\n")
        val plan = ProfileTransfer.planImport(data, null, emptyList())!!
        assertEquals(true, plan.writes["dock_visible"])
        assertEquals("p1", plan.writes["preset"])
    }

    @Test
    fun `xuat roi nhap ban day du ra dung gia tri tung hau to`() {
        val c = car()
        val out = c.export("A", ProfileTransfer.Kind.FULL)
        val n = c.import(out)!!
        ProfileScope.LAUNCHER_SUFFIXES.forEach { s ->
            val a = c.stored[c.key("A", s)]
            val b = c.stored[c.key(n, s)]
            if (s in ProfileScope.SNAPSHOT_SUFFIXES && a is String) {
                assertEquals(PrefSnapshot.decode(a), PrefSnapshot.decode(b as String), s)
            } else {
                assertEquals(a, b, s)
            }
        }
    }
}
