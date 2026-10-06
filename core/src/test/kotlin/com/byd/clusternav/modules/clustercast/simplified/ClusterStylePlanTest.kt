package com.byd.clusternav.modules.clustercast.simplified

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * CLUSTER-THEME-SAFE B1a · test C.3 mục 3 — ma trận `desired × native × sổ × cổng` → opcode cần gửi + kiểu cụm tin là đang
 * hiện + có dừng lượt mở không (bảng B.3 nghiên cứu 05/10).
 *
 * Sự thật nền: [ĐO F2] gửi theme khi chưa có màn ảo đã chạy ngoài đường từ 08/02 · [ĐO-gv F3] nổ máy lại ⇒ cụm Seal về
 * theme2 gốc (chữ nhật) · [ĐO-gv F4] `16 → 35` không opcode theme ⇒ chữ nhật · [ĐO 05/10 §4] theme gốc Seal mất số km/h.
 */
class ClusterStylePlanTest {

    private val seal = ProjectionRecipe.SEAL_DL3                                  // kiểu gốc chưa biết ⇒ RECT ẩn
    private val seal1025 = ProjectionRecipe.SEAL_DL3.copy(nativeStyle = CastStyle.RECT)
    private val dl5 = ProjectionRecipe.of("auto_container", listOf(16), listOf(18, 0), emptyMap(), null)

    private val n = ThemeLedger.Now(elapsedMs = 500_000, boot = 4, processStartMs = 400_000)
    private fun ok(op: Int, at: Long = 450_000) = ThemeLedger.Entry(op, ThemeLedger.State.OK, at, 4)
    private fun pending(op: Int) = ThemeLedger.Entry(op, ThemeLedger.State.PENDING, 450_000, 4)
    private val previousProcess = ThemeLedger.Entry(30, ThemeLedger.State.OK, 100_000, 4)

    private fun plan(r: ProjectionRecipe, desired: CastStyle, ledger: ThemeLedger.Entry?, gate: ThemeVerdict?): ClusterStylePlan.Plan {
        val want = ClusterStylePlan.wanted(r, desired, ledger)
        return ClusterStylePlan.decide(r, desired, ledger, want, if (want == null) null else gate, n)
    }

    // ── opcode cần (luật 1–3) ──────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `wanted - Bo tron luon xin 30 (khac kieu goc hoac goc chua biet), du so ghi gi`() {
        for (l in listOf(null, ok(30), ok(31), pending(30), previousProcess)) {
            assertEquals(30, ClusterStylePlan.wanted(seal, CastStyle.CURVED, l), "$l")
            assertEquals(30, ClusterStylePlan.wanted(seal1025, CastStyle.CURVED, l), "$l")
        }
    }

    /** 2.90 · R2 — sổ không bao giờ làm bỏ opcode: Chữ nhật luôn xin 31 (cổng đỡ lượt trùng cùng tiến trình). */
    @Test
    fun `290 - wanted - Chu nhat tren xe goc chu nhat - luon xin 31, bat ke so`() {
        for (l in listOf(null, ok(31), ok(31, at = 10), ok(30), pending(31), pending(30))) {
            assertEquals(31, ClusterStylePlan.wanted(seal1025, CastStyle.RECT, l), "$l")
        }
    }

    @Test
    fun `wanted - RECT an (goc chua biet, DL5) - ve Bo tron hoac khong gi`() {
        assertEquals(CastStyle.CURVED, ClusterStylePlan.effective(seal, CastStyle.RECT))
        assertEquals(30, ClusterStylePlan.wanted(seal, CastStyle.RECT, ok(31)))
        assertNull(ClusterStylePlan.effective(dl5, CastStyle.CURVED))
        assertNull(ClusterStylePlan.wanted(dl5, CastStyle.RECT, null))
    }

    // ── quyết định (luật 4–6) ──────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `cong SEND - gui opcode, kieu tin = kieu da ep`() {
        val p = plan(seal1025, CastStyle.CURVED, null, ThemeVerdict.SEND)
        assertEquals(30, p.themeOp); assertEquals(BelievedStyle.CURVED, p.believed); assertFalse(p.abort)
        val q = plan(seal1025, CastStyle.RECT, ok(30), ThemeVerdict.SEND)
        assertEquals(31, q.themeOp); assertEquals(BelievedStyle.RECT, q.believed)
    }

    @Test
    fun `khong can gui (opcode goc da ep) - kieu tin = kieu goc, cong khong duoc hoi`() {
        val p = plan(seal1025, CastStyle.RECT, ok(31), ThemeVerdict.ABORT)
        assertNull(p.themeOp); assertEquals(BelievedStyle.RECT, p.believed); assertFalse(p.abort)
    }

    @Test
    fun `cong SKIP_KNOWN (man ao co tu truoc) - bo opcode, di tiep, kieu theo so`() {
        val a = plan(seal1025, CastStyle.CURVED, ok(30), ThemeVerdict.SKIP_KNOWN)
        assertNull(a.themeOp); assertFalse(a.abort); assertEquals(BelievedStyle.CURVED, a.believed)
        val b = plan(seal1025, CastStyle.CURVED, null, ThemeVerdict.SKIP_KNOWN)
        assertFalse(b.abort); assertEquals(BelievedStyle.UNKNOWN, b.believed, "SKIP khi màn ảo còn mà sổ trống ⇒ UNKNOWN")
        val c = plan(seal1025, CastStyle.CURVED, previousProcess, ThemeVerdict.SKIP_KNOWN)
        assertEquals(BelievedStyle.UNKNOWN, c.believed)
    }

    @Test
    fun `cong ABORT - di tiep CHI KHI so cung tien trinh chung minh dung kieu, khong thi DUNG`() {
        val proven = plan(seal1025, CastStyle.CURVED, ok(30), ThemeVerdict.ABORT)
        assertFalse(proven.abort); assertNull(proven.themeOp); assertEquals(BelievedStyle.CURVED, proven.believed)
        for (l in listOf(null, pending(30), ok(31), previousProcess)) {
            val p = plan(seal1025, CastStyle.CURVED, l, ThemeVerdict.ABORT)
            assertTrue(p.abort, "$l ⇒ không chứng minh được Bo tròn ⇒ DỪNG (theme gốc Seal mất km/h)")
            assertNull(p.themeOp)
        }
    }

    @Test
    fun `verdict null khi can opcode - loi goi, huong an toan (coi nhu ABORT)`() {
        val p = ClusterStylePlan.decide(seal, CastStyle.CURVED, null, 30, null, n)
        assertTrue(p.abort)
    }

    @Test
    fun `DL5 - khong opcode kieu, khong hoi cong, di tiep, kieu UNKNOWN`() {
        val p = plan(dl5, CastStyle.CURVED, null, null)
        assertNull(p.themeOp); assertFalse(p.abort); assertEquals(BelievedStyle.UNKNOWN, p.believed)
    }
}
