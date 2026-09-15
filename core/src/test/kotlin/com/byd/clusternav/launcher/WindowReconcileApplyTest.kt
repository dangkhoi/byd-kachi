package com.byd.clusternav.launcher

import com.byd.clusternav.system.AppLocationRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * T6 (quality-review 2026-09-15) — test HÀNH VI cho đường APPLY của reconcile: [LauncherBootPlan.reconcile] →
 * áp vào [AppLocationRegistry] (đúng thứ `LauncherWindows.reconcileLocations` làm ở render). Đây là chỗ Agent
 * test-integrity chỉ ra "không có test": model state test kín, nhưng đường biến state thành vị-trí-cửa-sổ thì không.
 * Test này mô phỏng đúng `reconcileLocations`: đọc placed từ registry, reconcile theo state, place mount + remove evict.
 */
class WindowReconcileApplyTest {

    private val maps = "com.google.android.apps.maps"
    private val youtube = "com.google.android.youtube"

    /** Đúng thân `LauncherWindows.reconcileLocations` nhưng thuần (không Android/closeApp): áp vào registry. */
    private fun applyReconcile(reg: AppLocationRegistry, slots: List<SlotContent>) {
        val placed = reg.onDisplay(0).map { it.pkg }.toSet()
        val r = LauncherBootPlan.reconcile(slots, placed) { pkg -> !reg.isCastable(pkg) }
        r.mount.forEach { reg.place(it.pkg, 0, it.slot) }
        r.evict.forEach { reg.remove(it) }
    }

    @Test fun `chuyen app sang o khac - registry chi con MOT vi tri, khong nhan doi`() {
        val reg = AppLocationRegistry()
        // Ban đầu: maps ở ô 0 (đã hiện).
        reg.place(maps, 0, 0)
        // State chuyển maps sang ô 1 (model withSlot dedup ⇒ ô 0 trống, ô 1 = maps).
        applyReconcile(reg, listOf(SlotContent.Empty, SlotContent.App(maps), SlotContent.Empty, SlotContent.Empty))
        assertEquals(1, reg.locationOf(maps)?.slot, "maps phải ở ô 1")
        assertEquals(listOf(maps), reg.onDisplay(0).map { it.pkg }, "đúng MỘT vị trí của maps trên màn launcher")
    }

    @Test fun `app bi go khoi moi o - registry evict`() {
        val reg = AppLocationRegistry()
        reg.place(maps, 0, 0)
        reg.place(youtube, 0, 1)
        // State: chỉ còn youtube ở ô 0 (maps bị gỡ hết).
        applyReconcile(reg, listOf(SlotContent.App(youtube), SlotContent.Empty, SlotContent.Empty, SlotContent.Empty))
        assertNull(reg.locationOf(maps), "maps không còn ô nào ⇒ evict khỏi registry")
        assertEquals(0, reg.locationOf(youtube)?.slot, "youtube chuyển về ô 0")
        assertEquals(listOf(youtube), reg.onDisplay(0).map { it.pkg })
    }

    @Test fun `reconcile idempotent - state khop registry thi khong doi gi`() {
        val reg = AppLocationRegistry()
        reg.place(maps, 0, 0)
        reg.place(youtube, 0, 2)
        val slots = listOf(SlotContent.App(maps), SlotContent.Empty, SlotContent.App(youtube), SlotContent.Empty)
        applyReconcile(reg, slots)
        assertEquals(0, reg.locationOf(maps)?.slot)
        assertEquals(2, reg.locationOf(youtube)?.slot)
        assertEquals(listOf(maps, youtube), reg.onDisplay(0).map { it.pkg })
    }
}
