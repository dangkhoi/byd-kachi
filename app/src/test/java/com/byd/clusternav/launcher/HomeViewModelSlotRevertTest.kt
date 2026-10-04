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

    /**
     * LỖI XE 2.87 (owner 04/10) — khoá qua [HomeViewModel]: hồ sơ LƯU ChatGPT ở ô 1, YouTube đặt TẠM vào ô 1, YouTube rời ô
     * (*tắt* / *chạy nền* / chết) ⇒ ô TRONG SUỐT. Bản 2.87 trả `ShowSaved` ⇒ ô dựng lại với ChatGPT ⇒ host mở / K8 kéo ChatGPT
     * (đang sau màn nhà) vào khung *"thay vì trong suốt"*. Lớp LƯU không đổi, không persist; lớp tạm mất ⇒ ChatGPT về ô 1.
     */
    @Test
    fun `loi xe 2_87 - YouTube dat tam tren o LUU ChatGPT roi o thi trong suot, khong keo ChatGPT vao`() {
        val gpt = "com.openai.chatgpt"
        val yt = "com.google.android.youtube"
        val profile = WorkspaceState.of(LayoutPreset.QUAD, SlotContent.App(gpt), SlotContent.Widget("w_tyres"))
        listOf(SlotRevertPlan.Event.APP_CLOSED, SlotRevertPlan.Event.APP_BACKGROUND, SlotRevertPlan.Event.APP_DIED).forEach { ev ->
            val r = CountingRepo(HomeUiState(workspace = profile))
            val vm = HomeViewModel(r)
            assertTrue(vm.placeTemporary(0, yt))
            val next = vm.slotRevert(0, ev, yt)
            assertEquals(SlotRevertPlan.Next.Clear, next, "$ev")
            vm.applySlotRevert(0, next)
            val shown = vm.uiState.value.effectiveWorkspace.slots
            assertEquals(listOf(SlotContent.Empty, SlotContent.Widget("w_tyres")), shown.take(2), "$ev: khung trong suốt, ô khác giữ")
            assertFalse(SlotContent.App(gpt) in shown, "$ev: ChatGPT không được vào khung — $shown")
            assertEquals(profile, vm.uiState.value.workspace, "$ev: lớp LƯU giữ nguyên")
            assertEquals(0, r.persistCount, "$ev: không bao giờ persist()")
            assertEquals(SlotContent.App(gpt), vm.uiState.value.copy(overlay = SlotOverlay.EMPTY).effectiveWorkspace.slots[0],
                "$ev: khởi động lại ⇒ ChatGPT về ô 1 như hồ sơ")
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
