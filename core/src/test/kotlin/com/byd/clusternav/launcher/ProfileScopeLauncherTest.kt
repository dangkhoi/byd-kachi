package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ PROFILE-IMPORT-TYPES (lớp nhập/xuất) — bảng kiểu hậu tố launcher phải ĐỦ, và tệp độc không đi qua ═══════════════
 *
 * Senior review PROFILE-IO-0930 lượt 2 đặt bài này; lượt hoàn tất `PROFILE-IMPORT-TYPES` (2026-10-01) thêm các ca hồi quy
 * nhiều kiểu (Int/Long/Float/tập ở hậu tố chuỗi, chuỗi ở hậu tố cờ) và bản CHIA SẺ. Lớp ĐỌC ở `:app` có bài riêng
 * (`PrefsTypedReadTest`, `LauncherProfileTypesCoverageTest`). Bài này khoá:
 *  • bảng phủ ĐÚNG [ProfileScope.LAUNCHER_SUFFIXES] — hậu tố mới quên khai kiểu ⇒ đỏ (nhánh "chưa khai ⇒ bỏ" ở [check]
 *    là fail-closed: thiếu dòng khai thì lượt nhập lặng lẽ mất hậu tố đó);
 *  • ca bệnh gốc: tệp sửa tay `b|slot_0|true` — trước lớp này giá trị Boolean vào `kachi_workspace` và `getString` của
 *    `load()` ném `ClassCastException` ở mọi lần mở;
 *  • `null` (= xoá khoá ở chỗ gọi) đi qua nguyên — lọc kiểu không được biến "vắng" thành "giữ rác cũ".
 */
class ProfileScopeLauncherTest {

    private val header = ProfileTransfer.encodeHeader("Thử", ProfileTransfer.Kind.FULL)

    @Test
    fun `bang kieu phu dung moi hau to launcher`() {
        assertEquals(ProfileScope.LAUNCHER_SUFFIXES.toSet(), ProfileScopeLauncher.DECLARED_TYPES.keys)
        // Chỉ hai kiểu mà `WorkspacePrefs` thật sự ghi theo hồ sơ.
        assertEquals(setOf(PrefType.STRING, PrefType.BOOLEAN), ProfileScopeLauncher.DECLARED_TYPES.values.toSet())
    }

    @Test
    fun `check bo sai kieu, giu dung kieu va null`() {
        val r = ProfileScopeLauncher.check(
            linkedMapOf("slot_0" to true, "preset" to "THREE", "dock_visible" to "yes", "saved_places" to null, "la" to "x"),
        )
        assertEquals(linkedMapOf<String, Any?>("preset" to "THREE", "saved_places" to null), r.values)
        assertEquals(listOf("slot_0", "dock_visible", "la"), r.dropped)
    }

    @Test
    fun `tep doc b slot_0 true khong vao ho so moi`() {
        val data = header + "\n" + PrefSnapshot.encode(linkedMapOf("slot_0" to true, "slot_1" to "app:x", "preset" to "TWO"))
        val plan = ProfileTransfer.planImport(data, null, listOf("A"))!!
        assertNull(plan.writes["slot_0"], "Boolean ở hậu tố chuỗi phải thành null (= remove), không chép nguyên kiểu")
        assertTrue("slot_0" in plan.writes, "hậu tố vẫn có mặt trong kế hoạch để chỗ gọi xoá rác cũ")
        assertEquals("app:x", plan.writes["slot_1"])
        assertEquals("TWO", plan.writes["preset"])
        assertEquals(listOf("slot_0"), plan.dropped)
    }

    @Test
    fun `xuat khong mang gia tri sai kieu dang nam tren dia`() {
        val stored = mapOf("A__slot_0" to true, "A__preset" to "TWO", "A__dock_visible" to false)
        val source = object : ProfileTransfer.Source {
            override fun stored(): Map<String, *> = stored
            override fun keyOf(profile: String, suffix: String) = "${profile}__$suffix"
            override fun snapshotLive(profile: String) = Unit
        }
        val out = ProfileTransfer.export(source, "A", "A", ProfileTransfer.Kind.FULL)
        val body = PrefSnapshot.decode(out.split("\n", limit = 2)[1])
        assertFalse("slot_0" in body, "giá trị sai kiểu không được đi sang máy nhận chạy bản chưa có lớp kiểm")
        assertEquals("TWO", body["preset"])
        assertEquals(false, body["dock_visible"])
    }

    // ── PROFILE-IMPORT-TYPES · ca hồi quy nhiều kiểu (2026-10-01) ─────────────────────────────────────────────────

    /** Giá trị chắc chắn KHÁC kiểu khai — sinh từ bảng, nên hậu tố mới tự vào bài mà không ai phải nhớ thêm dòng. */
    private fun wrongFor(type: PrefType): Any = if (type == PrefType.STRING) 7L else 1

    @Test
    fun `moi hau to khai san deu bi chan khi sai kieu, dung kieu thi qua nguyen`() {
        val wrong = ProfileScopeLauncher.DECLARED_TYPES.mapValues { (_, t) -> wrongFor(t) }
        val r = ProfileScopeLauncher.check(wrong)
        assertEquals(emptyMap<String, Any?>(), r.values, "không một hậu tố nào được giữ giá trị sai kiểu")
        assertEquals(ProfileScopeLauncher.DECLARED_TYPES.keys, r.dropped.toSet())
        val right = ProfileScopeLauncher.DECLARED_TYPES.mapValues { (_, t) -> if (t == PrefType.STRING) "x" else true }
        val ok = ProfileScopeLauncher.check(right)
        assertEquals(right, ok.values)
        assertEquals(emptyList<String>(), ok.dropped)
    }

    @Test
    fun `tep doc nhieu kieu sai - slot_0 Int, preset Boolean, top_strip Long - khong vao ho so moi`() {
        val body = linkedMapOf<String, Any?>(
            "slot_0" to 5, "preset" to true, "top_strip" to 7L, "grid_layout" to setOf("a", "b"),
            "header_order" to emptySet<String>(), "dock_visible" to 1.0f, "launcher_autostart" to "yes",
            "top_strip_labels" to "true",
            // đúng kiểu — phải đi qua nguyên vẹn
            "slot_1" to "app:x", "theme_mode" to "DAY", "dock_enabled" to "nav,ac", "top_strip_migrated_ux5b" to true,
        )
        val plan = ProfileTransfer.planImport(header + "\n" + PrefSnapshot.encode(body), null, listOf("A"))!!
        val bad = setOf(
            "slot_0", "preset", "top_strip", "grid_layout", "header_order", "dock_visible", "launcher_autostart", "top_strip_labels",
        )
        assertEquals(bad, plan.dropped.toSet(), "mọi hậu tố sai kiểu phải được BÁO (log), không lặng lẽ")
        bad.forEach {
            assertTrue(it in plan.writes, "$it vẫn trong kế hoạch để chỗ gọi xoá rác cũ")
            assertNull(plan.writes[it], "$it sai kiểu ⇒ null (= remove), không chép nguyên kiểu")
        }
        assertEquals("app:x", plan.writes["slot_1"])
        assertEquals("DAY", plan.writes["theme_mode"])
        assertEquals("nav,ac", plan.writes["dock_enabled"])
        assertEquals(true, plan.writes["top_strip_migrated_ux5b"])
        // Mọi giá trị còn lại trong kế hoạch đều đúng kiểu khai — đây là bất biến mà lượt `load()` dựa vào.
        plan.writes.forEach { (k, v) ->
            if (v != null) assertEquals(ProfileScopeLauncher.DECLARED_TYPES[k], PrefType.of(v), "kiểu của $k trong kế hoạch")
        }
    }

    @Test
    fun `ban CHIA SE nhan tu nguoi khac cung bi kiem kieu, ke ca anh chup`() {
        val shot = ProfileScope.SNAPSHOT_SUFFIXES.first()
        val share = ProfileTransfer.encodeHeader("XauKieu", ProfileTransfer.Kind.SHARE)
        val body = linkedMapOf<String, Any?>("slot_0" to 5, "preset" to true, "slot_1" to "app:y", shot to true)
        val plan = ProfileTransfer.planImport(share + "\n" + PrefSnapshot.encode(body), null, listOf("A"))!!
        assertEquals(ProfileTransfer.Kind.SHARE, plan.kind)
        assertEquals(setOf("slot_0", "preset", shot), plan.dropped.toSet(), "ảnh chụp sai kiểu cũng phải được báo")
        assertNull(plan.writes["slot_0"])
        assertNull(plan.writes["preset"])
        assertEquals("app:y", plan.writes["slot_1"])
        plan.writes.forEach { (k, v) ->
            if (v != null) assertEquals(ProfileScopeLauncher.DECLARED_TYPES[k], PrefType.of(v), "kiểu của $k trong kế hoạch")
        }
    }
}
