package com.byd.clusternav.modules.clustercast.simplified

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 2.98 · R6-E — khoá hình học chiếu của app đã gỡ không bao giờ bị dọn (perf-inventory-2026-10-08 §4 E). Khoá:
 * nhận đúng gói (cả hậu tố nửa/Chữ nhật), fail-safe khi PackageManager không trả lời, ân hạn 30 ngày (cài lại mod khác
 * chữ ký), đồng hồ lùi, sổ không phình.
 */
class CastPrefsPruneTest {

    private val day = 24L * 60 * 60 * 1000

    private fun geometry(pkg: String, suffix: String = "") =
        listOf("size", "overscan", "density", "bounds").map { "config_${it}_$pkg$suffix" }

    @Test
    fun `nhan dung goi cua moi ho khoa, khoa la thi khong dung`() {
        assertEquals("vn.vietmap.live", CastPrefsPrune.packageOf("config_size_vn.vietmap.live"))
        assertEquals("vn.vietmap.live", CastPrefsPrune.packageOf("config_bounds_vn.vietmap.live__L30"))
        assertEquals("vn.vietmap.live", CastPrefsPrune.packageOf("config_bounds_vn.vietmap.live__R70${CastProfile.RECT_SUFFIX}"))
        assertEquals("com.google.android.youtube", CastPrefsPrune.packageOf("config_density_com.google.android.youtube${CastProfile.RECT_SUFFIX}"))
        assertEquals("app.revanced.android.youtube", CastPrefsPrune.packageOf("scale-dpi:app.revanced.android.youtube"))
        assertEquals("a.b", CastPrefsPrune.packageOf("scale-b:a.b"))
        assertEquals("a.b", CastPrefsPrune.packageOf("dpi:a.b"))
        listOf(
            "cast_enabled", "last_display_id", "config_size_", "config_size_nodot", "bubbleX", "scale-x:a.b",
            "autostart_package", "config_size_a.b;rm", "dpi:a.b c",
        ).forEach { assertNull(CastPrefsPrune.packageOf(it), it) }
    }

    @Test
    fun `goi vang lan dau chi ghi so, qua an han 30 ngay moi go`() {
        val keys = geometry("gone.app") + geometry("kept.app") + listOf("scale-dpi:gone.app", "cast_enabled")
        val installed = { p: String -> p == "kept.app" }
        val first = CastPrefsPrune.plan(keys, installed, emptyMap(), nowMs = 100 * day)
        assertEquals(emptyList<String>(), first.removeKeys, "lần đầu thấy vắng: KHÔNG gỡ")
        assertEquals(mapOf("gone.app" to 100 * day), first.missingSince)

        val early = CastPrefsPrune.plan(keys, installed, first.missingSince, nowMs = 129 * day)
        assertEquals(emptyList<String>(), early.removeKeys)

        val due = CastPrefsPrune.plan(keys, installed, first.missingSince, nowMs = 130 * day)
        assertEquals(geometry("gone.app") + "scale-dpi:gone.app", due.removeKeys, "chỉ khoá của gói vắng, không chạm khoá lạ")
        assertEquals(emptyMap<String, Long>(), due.missingSince)
    }

    @Test
    fun `goi quay lai thi xoa khoi so, khong go`() {
        val keys = geometry("mod.app")
        val p = CastPrefsPrune.plan(keys, { true }, mapOf("mod.app" to 0L), nowMs = 365 * day)
        assertEquals(emptyList<String>(), p.removeKeys)
        assertEquals(emptyMap<String, Long>(), p.missingSince)
    }

    /** Binder hỏng / ROM ném ⇒ `null` ⇒ cả lượt không gỡ gì — không bao giờ "xoá cấu hình của mọi app". */
    @Test
    fun `mot goi khong tra loi duoc thi ca luot khong go gi`() {
        val keys = geometry("gone.app") + geometry("unknown.app")
        val ledger = mapOf("gone.app" to 0L)
        val p = CastPrefsPrune.plan(keys, { if (it == "unknown.app") null else false }, ledger, nowMs = 365 * day)
        assertTrue(p.aborted)
        assertEquals(emptyList<String>(), p.removeKeys)
        assertEquals(ledger, p.missingSince, "sổ giữ nguyên")
    }

    @Test
    fun `dong ho lui thi dat lai moc, khong go som`() {
        val keys = geometry("gone.app")
        val p = CastPrefsPrune.plan(keys, { false }, mapOf("gone.app" to 500 * day), nowMs = 10 * day)
        assertEquals(emptyList<String>(), p.removeKeys)
        assertEquals(mapOf("gone.app" to 10 * day), p.missingSince)
    }

    @Test
    fun `so chi giu goi dang co khoa, khong phinh`() {
        val p = CastPrefsPrune.plan(geometry("a.b"), { false }, mapOf("old.gone" to 1L, "a.b" to 5L), nowMs = 6L)
        assertEquals(mapOf("a.b" to 5L), p.missingSince)
    }

    @Test
    fun `so ma hoa giai ma khu hong`() {
        val ledger = mapOf("a.b" to 1L, "vn.vietmap.live" to 1_790_000_000_000L)
        assertEquals(ledger, CastPrefsPrune.decode(CastPrefsPrune.encode(ledger)))
        assertEquals(mapOf("a.b" to 1L), CastPrefsPrune.decode("a.b=1;=2;x=3;c.d=zz;e.f=-1;;"))
        assertEquals(emptyMap<String, Long>(), CastPrefsPrune.decode(null))
    }
}
