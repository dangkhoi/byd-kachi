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

    // Owner 2026-09-15: chọn GMaps ở ô 1 rồi chọn lại GMaps ở ô 2 → GMaps hiện CẢ hai ô. Một app không được ở hai ô.
    @Test fun `mot app mot o - dat app o o moi go khoi o cu`() {
        val gmaps = "com.google.android.apps.maps"
        val s = WorkspaceState()
            .withSlot(0, SlotContent.App(gmaps))   // ô 1 = GMaps
            .withSlot(1, SlotContent.App(gmaps))   // ô 2 cũng chọn GMaps ⇒ phải CHUYỂN, không nhân đôi
        assertSame(SlotContent.Empty, s.slots[0], "ô cũ phải trống — không để lại app trùng")
        assertEquals(SlotContent.App(gmaps), s.slots[1], "ô mới giữ GMaps")
        assertEquals(1, s.slots.count { it is SlotContent.App && it.pkg == gmaps }, "GMaps chỉ được ở đúng 1 ô")
    }

    @Test fun `sanitized chua state cu co app trung o - giu o dau`() {
        val gmaps = "com.google.android.apps.maps"
        // Mô phỏng state NẠP từ prefs cũ: gmaps ở CẢ ô 0 và ô 1 (dựng thẳng qua constructor, không qua withSlot).
        val dirty = WorkspaceState(slots = List(WorkspaceState.SLOT_CAP) { i ->
            if (i == 0 || i == 1) SlotContent.App(gmaps) else SlotContent.Empty
        })
        val clean = dirty.sanitized()
        assertEquals(SlotContent.App(gmaps), clean.slots[0], "giữ ô đầu (đang hiện cửa sổ thật)")
        assertSame(SlotContent.Empty, clean.slots[1], "ô trùng sau về trống")
        assertEquals(1, clean.slots.count { it is SlotContent.App && it.pkg == gmaps })
    }

    @Test fun `sanitized khong doi tham chieu khi khong co trung`() {
        val s = WorkspaceState().withSlot(0, SlotContent.App("a")).withSlot(1, SlotContent.App("b"))
        assertSame(s, s.sanitized(), "không trùng ⇒ trả chính nó")
    }

    @Test fun `mot app mot o - app khac khong bi anh huong`() {
        val gmaps = "com.google.android.apps.maps"
        val s = WorkspaceState()
            .withSlot(0, SlotContent.App("com.youtube"))
            .withSlot(1, SlotContent.App(gmaps))
            .withSlot(2, SlotContent.App(gmaps))   // chuyển GMaps sang ô 3
        assertEquals(SlotContent.App("com.youtube"), s.slots[0], "app KHÁC giữ nguyên")
        assertSame(SlotContent.Empty, s.slots[1])
        assertEquals(SlotContent.App(gmaps), s.slots[2])
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
