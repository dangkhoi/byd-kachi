package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ═══ PROFILE-SWITCH-SLOTS · R-A1 — đổi viền / ẩn-hiện thanh nút KHÔNG được tháo vùng ô ═══════════════════════════
 *
 * Khoá lỗi A [ĐO máy ảo 2026-10-01, spec `kachi-profile-switch-slots.html` §2.2 E2/E3]: đổi giữa hai hồ sơ khác cạnh
 * thanh nút ⇒ `DockAreaLayout.apply` cũ `removeView(workspace)` + `removeAllViews()` ⇒ cây ô nhận
 * `onDetachedFromWindow` ⇒ `VdAppHost.release()` ⇒ ô app đen mãi. Đối chứng cùng ngày: hai hồ sơ CÙNG cạnh thì ô
 * dựng màn ảo bình thường — tức đúng một biến là việc tháo vùng ô.
 *
 * Bộ chứa giả dưới đây thi hành các bước ĐÚNG như `DockAreaLayout.apply` ánh xạ chúng sang `ViewGroup` (khoá ánh xạ đó
 * ở `DockAreaLayoutContractTest` bên `:app`): `Remove(i)` = `removeViewAt(i)` · `AddWorkspace` = `addView` vào cuối ·
 * `AddDock(atStart)` = `addView` ở chỉ số 0 hoặc cuối. Mỗi lần tháo được ĐẾM theo loại con.
 */
class DockAreaPlanTest {

    private val w = DockAreaChild.WORKSPACE
    private val d = DockAreaChild.DOCK

    /** 4 viền × hiện/ẩn = 8 cấu hình ⇒ 64 cặp chuyển. */
    private val configs: List<DockConfig> =
        DockEdge.entries.flatMap { e -> listOf(true, false).map { v -> DockConfig(edge = e, visible = v) } }

    /** Bộ chứa giả: danh sách con + số lần tháo theo loại. */
    private class FakeArea(start: List<DockAreaChild>) {
        val children = start.toMutableList()
        var workspaceRemovals = 0
        var dockRemovals = 0

        fun run(steps: List<DockAreaStep>) = steps.forEach { s ->
            when (s) {
                is DockAreaStep.Remove -> {
                    assertTrue(s.index in children.indices, "chỉ số tháo ${s.index} ngoài [0, ${children.size}) — bước sai thứ tự")
                    if (children.removeAt(s.index) == DockAreaChild.WORKSPACE) workspaceRemovals++ else dockRemovals++
                }
                DockAreaStep.AddWorkspace -> children.add(DockAreaChild.WORKSPACE)
                is DockAreaStep.AddDock -> if (s.atStart) children.add(0, DockAreaChild.DOCK) else children.add(DockAreaChild.DOCK)
            }
        }
    }

    private fun built(cfg: DockConfig): List<DockAreaChild> =
        FakeArea(emptyList()).apply { run(DockAreaPlan.steps(children.toList(), DockAreaPlan.target(cfg))) }.children.toList()

    // ── Bất biến chính ──────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `64 cap chuyen — vung o bi thao 0 lan va thu tu cuoi dung dich`() {
        var pairs = 0
        for (from in configs) for (to in configs) {
            val area = FakeArea(built(from))
            val target = DockAreaPlan.target(to)
            area.run(DockAreaPlan.steps(area.children.toList(), target))
            assertEquals(0, area.workspaceRemovals, "$from → $to: vùng ô bị tháo ⇒ VdAppHost.release ⇒ ô đen (lỗi A)")
            assertEquals(target.order, area.children, "$from → $to: thứ tự con cuối phải đúng bố cục đích")
            pairs++
        }
        assertEquals(64, pairs, "phải phủ đủ 8 × 8 cấu hình")
    }

    @Test
    fun `lan dung dau — mainArea rong thi gan du vung o va thanh nut theo dich`() {
        for (cfg in configs) {
            val steps = DockAreaPlan.steps(emptyList(), DockAreaPlan.target(cfg))
            assertEquals(1, steps.count { it == DockAreaStep.AddWorkspace }, "$cfg: lần đầu phải gắn vùng ô đúng một lần")
            assertEquals(DockAreaPlan.target(cfg).order, built(cfg), "$cfg")
        }
    }

    @Test
    fun `vung o da co thi KHONG gan lai — khong bao gio co buoc AddWorkspace`() {
        for (from in configs) for (to in configs) {
            val steps = DockAreaPlan.steps(built(from), DockAreaPlan.target(to))
            assertTrue(steps.none { it == DockAreaStep.AddWorkspace }, "$from → $to: gắn lại vùng ô = đã tháo nó trước đó")
        }
    }

    @Test
    fun `con la khong phai vung o deu bi thao, vung o thi khong — ke ca khi vung o nam giua`() {
        val area = FakeArea(listOf(d, w, d, d))
        area.run(DockAreaPlan.steps(area.children.toList(), DockAreaPlan.target(DockConfig(edge = DockEdge.LEFT))))
        assertEquals(0, area.workspaceRemovals)
        assertEquals(3, area.dockRemovals, "mọi khung cuộn cũ của thanh nút phải được tháo — không để hai thanh")
        assertEquals(listOf(d, w), area.children)
    }

    // ── Đích = đúng bố cục của bản 2.84 (không đổi giao diện, chỉ đổi cách gắn) ─────────────────────────────

    @Test
    fun `dich theo vien giu nguyen thu tu va chieu cua ban cu`() {
        fun t(e: DockEdge, v: Boolean = true) = DockAreaPlan.target(DockConfig(edge = e, visible = v))
        assertEquals(DockAreaTarget(vertical = true, order = listOf(w, d)), t(DockEdge.BOTTOM))
        assertEquals(DockAreaTarget(vertical = true, order = listOf(d, w)), t(DockEdge.TOP))
        assertEquals(DockAreaTarget(vertical = false, order = listOf(d, w)), t(DockEdge.LEFT))
        assertEquals(DockAreaTarget(vertical = false, order = listOf(w, d)), t(DockEdge.RIGHT))
        for (e in DockEdge.entries) {
            assertEquals(DockAreaTarget(vertical = true, order = listOf(w)), t(e, v = false),
                "thanh ẩn ($e): vùng ô lấp trọn, mainArea dọc — như S1b")
            assertTrue(!t(e, v = false).dockShown)
        }
    }

    @Test
    fun `ca do duoc tren may ao — A canh duoi sang B canh trai roi ve A`() {
        // E2/E3: hồ sơ A thanh nút DƯỚI, hồ sơ B thanh nút TRÁI. A → B → A ×3.
        val a = DockConfig(edge = DockEdge.BOTTOM)
        val b = DockConfig(edge = DockEdge.LEFT)
        val area = FakeArea(built(a))
        repeat(3) {
            area.run(DockAreaPlan.steps(area.children.toList(), DockAreaPlan.target(b)))
            assertEquals(listOf(d, w), area.children)
            area.run(DockAreaPlan.steps(area.children.toList(), DockAreaPlan.target(a)))
            assertEquals(listOf(w, d), area.children)
        }
        assertEquals(0, area.workspaceRemovals, "6 lượt đổi hồ sơ, vùng ô không được tháo lần nào")
        assertEquals(6, area.dockRemovals, "mỗi lượt chỉ tháo đúng một khung cuộn cũ của thanh nút")
    }
}
