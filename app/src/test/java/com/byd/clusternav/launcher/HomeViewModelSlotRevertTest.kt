package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Soát 2.87 · P1 — luật hoàn ô qua [HomeViewModel] (`slotRevert` → `applySlotRevert`) khi app ĐẶT TẠM cũng là nội dung LƯU
 * của một ô KHÁC. Tách khỏi `HomeViewModelTest` (trần 500 dòng); bảng thuần ở `:core` `SlotRevertPlanTest`.
 *
 * Ca hỏng (trước bản vá): LƯU [App(P), lốp, W]; lối tắt / giọng nói đặt P tạm vào ô 3 (ô 1 hiện TRỐNG vì một-app-một-ô); P
 * rời ô 3 ⇒ lớp tạm chỉ bỏ mục ô 3 ⇒ lớp LƯU lộ lại ⇒ P hiện ở ô 1 ⇒ host ô 1 `am force-stop` + `am start` đúng app vừa
 * *tắt*, K8 kéo app vừa *chạy nền* về, mở lại app vừa chết.
 */
class HomeViewModelSlotRevertTest {

    /** Kho giả tối thiểu: chỉ đếm `persist()` — luật hoàn ô KHÔNG BAO GIỜ được ghi hồ sơ (owner 01/10). */
    private class CountingRepo(private val initial: HomeUiState) : WorkspaceRepository {
        var persistCount = 0; private set
        override fun load(): HomeUiState = initial
        override fun persist(state: HomeUiState) { persistCount++ }
        override fun switchProfile(name: String): HomeUiState = initial
        override fun addProfile(name: String): HomeUiState = initial
        override fun deleteProfile(name: String): HomeUiState = initial
    }

    private val p = "com.p"
    private val saved = WorkspaceState.of(LayoutPreset.QUAD, SlotContent.App(p), SlotContent.Widget("w_tyres"), SlotContent.Widget("w_clock"))

    @Test
    fun `app dat tam roi o thi khong ve o LUU khac, khong ghi ben`() {
        listOf(SlotRevertPlan.Event.APP_DIED, SlotRevertPlan.Event.APP_CLOSED, SlotRevertPlan.Event.APP_BACKGROUND).forEach { ev ->
            val r = CountingRepo(HomeUiState(workspace = saved))
            val vm = HomeViewModel(r)
            assertTrue(vm.placeTemporary(2, p))
            assertEquals(SlotContent.Empty, vm.uiState.value.effectiveWorkspace.slots[0], "đặt tạm: một app một ô")
            val next = vm.slotRevert(2, ev, p)
            assertEquals(SlotRevertPlan.Next.ShowSaved, next, "$ev")
            vm.applySlotRevert(2, next)
            val shown = vm.uiState.value.effectiveWorkspace.slots
            assertFalse(SlotContent.App(p) in shown, "$ev: P không được mở lại ở ô LƯU khác — $shown")
            assertEquals(SlotContent.Widget("w_clock"), shown[2], "$ev: ô 3 về widget LƯU")
            assertEquals(SlotContent.Empty, shown[0], "$ev: ô LƯU của P trong suốt tạm")
            assertEquals(saved, vm.uiState.value.workspace, "$ev: lớp LƯU giữ nguyên")
            assertEquals(0, r.persistCount, "$ev: không bao giờ persist()")
        }
    }

    @Test
    fun `khoi dong lai (lop tam mat) thi app ve o LUU cua no`() {
        val vm = HomeViewModel(CountingRepo(HomeUiState(workspace = saved)))
        vm.placeTemporary(2, p)
        vm.applySlotRevert(2, vm.slotRevert(2, SlotRevertPlan.Event.APP_CLOSED, p))
        assertEquals(SlotContent.App(p), vm.uiState.value.copy(overlay = SlotOverlay.EMPTY).effectiveWorkspace.slots[0])
    }
}
