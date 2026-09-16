package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
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

    // ── Mã khả năng đã BIẾN MẤT khỏi bộ đăng ký (owner gỡ hẳn) ───────────────────────────────────
    //
    // ⚠ Bài này khoá bài học của lượt **ADAS-PURGE 2026-09-16**: owner gỡ 10 nút + 17 datum + 3 nhóm, nhưng cấu
    // hình ĐÃ LƯU trên xe vẫn trỏ tới chúng. [ĐO đọc source] đường cũ không sập mà **tệ hơn**: `WidgetViews.build`
    // rơi xuống `telemetry(...)` → `TelemetryReadout.of` trả null → ô hiện `"ADAS_FCW"` + `"—"` **mãi mãi**. Thanh
    // nút (`ControlDockView` nhánh `null -> Unit`) và chip thanh trên (`TopStripChips.render` `mapNotNull`) đã bỏ
    // mã lạ từ trước — ô giữa màn là bề mặt cuối cùng còn giữ rác.
    @Test fun `sanitized bo ma widget khong con trong bo dang ky`() {
        val dirty = WorkspaceState(slots = List(WorkspaceState.SLOT_CAP) { i ->
            when (i) {
                0 -> SlotContent.Widget(listOf("adas_fcw"))              // nút ADAS đã xoá ⇒ cả ô về trống
                1 -> SlotContent.Widget(listOf("w_energy", "radar_zones"))  // một mã sống + một mã chết
                2 -> SlotContent.Widget(listOf("g_adas"))                // nhóm đã xoá
                3 -> SlotContent.Widget(listOf("soc"))                   // mã sống ⇒ giữ nguyên
                else -> SlotContent.Empty
            }
        })
        assertEquals(
            listOf("adas_fcw", "radar_zones", "g_adas"), dirty.unknownWidgetIds(),
            "phải NÓI RA được mã nào sắp bị bỏ — mất một ô đã lưu mà im lặng là kênh im lặng",
        )
        val clean = dirty.sanitized()
        assertSame(SlotContent.Empty, clean.slots[0], "ô chỉ có mã đã xoá ⇒ về trống, không phải ô 'ADAS_FCW —'")
        assertEquals(SlotContent.Widget(listOf("w_energy")), clean.slots[1], "giữ mã còn sống, bỏ mã đã xoá")
        assertSame(SlotContent.Empty, clean.slots[2], "mã NHÓM đã xoá cũng phải rụng")
        assertEquals(SlotContent.Widget(listOf("soc")), clean.slots[3], "mã sống không bị đụng tới")
        assertEquals(emptyList<String>(), clean.unknownWidgetIds(), "chạy lại phải sạch (idempotent)")
    }

    /**
     * ═══ (V) FEATURE-FILTER 2026-09-17 — CẢ 19 MÃ owner chấm NO phải rụng khỏi cấu hình ĐÃ LƯU ═══════════════
     *
     * Bài trên khoá **cơ chế**; bài này khoá **đúng danh sách của lượt xoá này**. Hai bài khác nhau ở chỗ: cơ chế
     * có thể đúng mà vẫn sót một mã (vd một mã bị xoá khỏi registry này nhưng còn trong registry kia ⇒ `pick`
     * vẫn tra ra ⇒ ô hỏng vẫn sống). Duyệt từng mã nên không có chỗ nào để sót.
     *
     * ⚠ Xe của owner đang chạy 1.68 với hồ sơ đã lưu; nếu một trong 19 mã này còn trên màn thì sau khi nâng cấp
     * nó sẽ thành ô ghi hoa mã + `"—"` vĩnh viễn — đúng lỗi [P0] mà Pass 1 của ADAS-PURGE đã vá.
     */
    @Test fun `sanitized bo het 19 ma cua luot FEATURE-FILTER`() {
        val gone = listOf(
            "is_charging", "charge_power", "charging_pct", "charging_eta_hour", "charging_eta_min",
            "charging_capacity_kwh", "charging_state", "charger_work_state", "batt_range_bodywork",
            "target_soc_set", "charge_cap", "start_charging", "drift_mode", "drive_mode", "rain_close",
            "mirror_auto", "mirror_fold_btn", "mcu_status", "key_bluetooth",
        )
        assertEquals(19, gone.size, "danh sách của lượt (V) phải đúng 19 mã — xem docs/diagnostics/feature-filter-2026-09-16.md §1")
        gone.forEach { id ->
            assertNull(CapabilityCatalog.pick(id), "$id vẫn tra ra được ⇒ chưa xoá khỏi bộ đăng ký nào đó")
            val s = WorkspaceState().withSlot(0, SlotContent.Widget(listOf(id, "soc")))
            assertEquals(listOf(id), s.unknownWidgetIds(), "$id phải bị NÓI RA là mã lạ")
            assertEquals(
                SlotContent.Widget(listOf("soc")), s.sanitized().slots[0],
                "$id phải rụng khỏi ô đã lưu, `soc` ở lại",
            )
        }
    }

    // ⚠ Ngược lại: 8 ô lốp LẺ chỉ bị **ẩn khỏi bộ chọn** ở lượt (V), KHÔNG xoá ⇒ ô ai đã đặt phải sống tiếp.
    @Test fun `tam o lop le chi bi an, khong bi don khoi cau hinh da luu`() {
        listOf(
            "tyre_p_fl", "tyre_p_fr", "tyre_p_rl", "tyre_p_rr",
            "tyre_t_fl", "tyre_t_fr", "tyre_t_rl", "tyre_t_rr",
        ).forEach { id ->
            assertTrue(id in CapabilityCatalog.HIDDEN_FROM_PICKER, "$id phải nằm trong danh sách ẩn có lý do")
            val s = WorkspaceState().withSlot(0, SlotContent.Widget(id))
            assertSame(s, s.sanitized(), "$id còn trong registry ⇒ không phải rác")
        }
    }

    // Mã CÒN trong registry nhưng bị ẩn khỏi bộ chọn (`CapabilityCatalog.HIDDEN_FROM_PICKER`) KHÔNG được coi là
    // rác — đó chính là luật *"ẩn khỏi bộ chọn ≠ xoá mã"*: ô của ai đã đặt phải tiếp tục chạy.
    @Test fun `sanitized khong dung toi ma chi bi an khoi bo chon`() {
        val hidden = CapabilityCatalog.HIDDEN_FROM_PICKER.keys.first()
        val s = WorkspaceState().withSlot(0, SlotContent.Widget(hidden))
        assertSame(s, s.sanitized(), "mã ẩn vẫn tra ra được ⇒ không phải rác")
        assertEquals(emptyList<String>(), s.unknownWidgetIds())
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
