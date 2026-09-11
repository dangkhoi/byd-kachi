package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class WorkspaceStateTest {

    @Test fun `mac dinh 3 widget, con lai o trong`() {
        val s = WorkspaceState()
        assertEquals(LayoutPreset.THREE, s.preset)
        // Chốt trần ô để ai đổi phải NGHĨ: mỗi ô chứa app cần một màn ảo riêng, và ô quá nhỏ thì app vô dụng.
        // Xem KDoc WorkspaceState.SLOT_CAP. Nới 4 → 6 ở P9 bước 3.
        assertEquals(6, WorkspaceState.SLOT_CAP)
        assertEquals(WorkspaceState.SLOT_CAP, s.slots.size)
        s.slots.forEach { assertSame(SlotContent.Empty, it) }
    }

    @Test fun `withSlot gan app va widget`() {
        val s = WorkspaceState()
            .withSlot(0, SlotContent.App("com.google.android.apps.maps"))
            .withSlot(2, SlotContent.Widget("w_energy"))
        assertEquals(SlotContent.App("com.google.android.apps.maps"), s.slots[0])
        assertEquals(SlotContent.Widget("w_energy"), s.slots[2])
        assertSame(SlotContent.Empty, s.slots[1])
    }

    @Test fun `withSlot index ngoai pham vi giu nguyen`() {
        val s = WorkspaceState()
        assertEquals(s, s.withSlot(9, SlotContent.App("x")))
        assertEquals(s, s.withSlot(-1, SlotContent.App("x")))
    }

    @Test fun `visibleSlots theo preset`() {
        val s = WorkspaceState(preset = LayoutPreset.THREE)
        assertEquals(3, s.visibleSlots().size)
        assertEquals(1, s.withPreset(LayoutPreset.ONE).visibleSlots().size)
        assertEquals(4, s.withPreset(LayoutPreset.QUAD).visibleSlots().size)
    }

    @Test fun `doi preset giu gan o an`() {
        val s = WorkspaceState(preset = LayoutPreset.QUAD)
            .withSlot(3, SlotContent.App("com.netflix"))
            .withPreset(LayoutPreset.THREE)   // ô 3 ẩn nhưng vẫn nhớ
        assertEquals(SlotContent.App("com.netflix"), s.slots[3])
        assertEquals(3, s.visibleSlots().size)
        assertEquals(SlotContent.App("com.netflix"), s.withPreset(LayoutPreset.QUAD).slots[3])
    }

    @Test fun `clearSlot ve trong`() {
        val s = WorkspaceState().withSlot(1, SlotContent.App("x")).clearSlot(1)
        assertSame(SlotContent.Empty, s.slots[1])
    }

    @Test fun `swap doi cho 2 o`() {
        val s = WorkspaceState()
            .withSlot(0, SlotContent.App("a"))
            .withSlot(3, SlotContent.Widget("w_energy"))
            .swap(0, 3)
        assertEquals(SlotContent.Widget("w_energy"), s.slots[0])
        assertEquals(SlotContent.App("a"), s.slots[3])
    }

    @Test fun `swap index xau hoac trung giu nguyen`() {
        val s = WorkspaceState().withSlot(0, SlotContent.App("a"))
        assertEquals(s, s.swap(0, 9))
        assertEquals(s, s.swap(-1, 0))
        assertEquals(s, s.swap(2, 2))
    }
}
