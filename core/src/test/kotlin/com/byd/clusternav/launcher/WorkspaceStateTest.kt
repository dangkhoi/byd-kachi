package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertNotNull
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
     * ═══ UX-OVERHAUL · WP8 2026-09-20 — mọi mã BỎ khỏi bảng owner phải rụng khỏi cấu hình ĐÃ LƯU ═════════════
     *
     * Cùng khuôn với ca (V) ngay dưới: bài đó khoá cơ chế, bài này khoá **đúng danh sách của lượt xoá này**. Đây
     * là các mục người dùng có thể đã đặt vào ô/thanh nút ở bản trước (viền cabin, GPS, cell pin, mô-tơ, tay lái,
     * gạt mưa, vị-trí-cốp, gập gương, HUD, tái tạo phanh, mã máy/nước làm mát…). Duyệt từng mã ⇒ không sót.
     *
     * ⚠ `cast` TỪNG được miễn trừ ở đây (chỉ ẩn khỏi bộ chọn, `pick` vẫn tra ra) — **1.90 đổi**: owner xoá hẳn nút,
     * nên ca *"…chi bi an…"* ở cuối bài nay đòi `pick` trả `null`. Xem lý do đầy đủ tại chính assert ấy.
     */
    @Test fun `sanitized bo het cac ma purge cua luot UX-OVERHAUL WP8`() {
        val gone = listOf(
            // viền cabin (nhóm g_ambient gỡ hẳn)
            "ambient_enabled", "ambient_front_brightness", "ambient_front_color",
            "ambient_rear_brightness", "ambient_rear_color",
            "ambient_power", "ambient_brightness", "ambient_color", "ambient_music",
            // GPS×4 (nav dùng LocationManager, không cần datum HAL)
            "gps_lat", "gps_lon", "gps_heading", "gps_elevation",
            // cell pin / mô-tơ / tay lái / dốc / rpm / bánh — số kỹ thuật không ai xem lúc lái
            "cell_temp_avg", "cell_temp_high", "cell_temp_low", "cell_v_high", "cell_v_low",
            "motor_front_rpm", "motor_rear_rpm", "motor_front_torque",
            "steering_deg", "slope_deg", "wheel_speed",
            // thân xe / cabin bỏ
            "wiper_state", "tailgate_position", "mirror_fold",
            // mã máy / nước làm mát / HUD / tái tạo phanh
            "engine_code", "engine_rpm", "engine_coolant_level", "coolant_temp",
            "hud_switch", "hud_brightness", "regen_level", "wiper",
        )
        gone.forEach { id ->
            assertNull(CapabilityCatalog.pick(id), "$id vẫn tra ra được ⇒ chưa xoá khỏi bộ đăng ký nào đó (WP8)")
            val s = WorkspaceState().withSlot(0, SlotContent.Widget(listOf(id, "soc")))
            assertEquals(listOf(id), s.unknownWidgetIds(), "$id phải bị NÓI RA là mã lạ")
            assertEquals(
                SlotContent.Widget(listOf("soc")), s.sanitized().slots[0],
                "$id phải rụng khỏi ô đã lưu, `soc` ở lại",
            )
        }
        assertNull(
            CapabilityCatalog.pick("cast"),
            "⚠ 1.90 (owner 2026-09-21): `cast` nay XOÁ HẲN, không còn chỉ-ẩn. Lý do cũ ghi *\"nút nổi/giọng nói vẫn " +
                "cần nó\"* đã bị số đo bác: [ĐO grep] cả gói `modules/clustercast` (nút nổi · coordinator · bóng " +
                "VietMap) có 0 tham chiếu tới ControlRegistry/CapabilityCatalog/pick/kindOf, và đường GHI " +
                "`AutoContainer.sendInfo` chưa bao giờ được nối (BydHalGateway.localSet trả false).",
        )
    }

    /**
     * ═══ 1.90 · 2026-09-21 — 11 MÃ owner gỡ cho **XE THUẦN ĐIỆN** phải rụng khỏi cấu hình ĐÃ LƯU ═══════════════
     *
     * Cùng khuôn hai bài trên: bài kia khoá **cơ chế**, bài này khoá **đúng danh sách của lượt xoá này** — duyệt
     * từng mã nên không có chỗ nào để sót. Xe của owner đang chạy bản trước với hồ sơ đã lưu; mã nào còn trên màn
     * mà không rụng sẽ thành ô ghi HOA mã + `"—"` vĩnh viễn (lỗi [P0] mà Pass 1 của ADAS-PURGE đã vá).
     *
     * ⚠ `cast` KHÔNG nằm trong danh sách này — nó đã có ca riêng ở bài trên (nơi nó từng được miễn trừ), nên để
     * nguyên chỗ ấy thì lịch sử đọc được: một mã đi từ *"chỉ ẩn"* sang *"xoá hẳn"*.
     */
    @Test fun `sanitized bo het 11 ma cua luot 1_90 xe thuan dien`() {
        val gone = listOf(
            // 8 nút (không kể `cast` — xem ca riêng ở bài trên)
            "anion", "headlight_mode", "powertrain_mode", "screen_rotation",
            "camera_view", "cluster_music", "brightness_gear", "vol",
            // 2 datum
            "op_mode", "energy_mode",
        )
        assertEquals(10, gone.size, "9 nút + 2 datum = 11 mã, trừ `cast` đã có ca riêng ⇒ 10 mã ở đây")
        gone.forEach { id ->
            assertNull(CapabilityCatalog.pick(id), "$id vẫn tra ra được ⇒ chưa xoá khỏi bộ đăng ký nào đó")
            val s = WorkspaceState().withSlot(0, SlotContent.Widget(listOf(id, "soc")))
            assertEquals(listOf(id), s.unknownWidgetIds(), "$id phải bị NÓI RA là mã lạ")
            assertEquals(
                SlotContent.Widget(listOf("soc")), s.sanitized().slots[0],
                "$id phải rụng khỏi ô đã lưu, `soc` ở lại",
            )
        }
        // GIỮ LẠI có chủ ý (owner: dành cho PHEV Sealion 6) — nếu một trong ba mã này rụng thì đó là lỗi.
        listOf("fuel_range_km", "fuel_pct", "oil_level").forEach { id ->
            assertNotNull(CapabilityCatalog.pick(id), "$id là datum xăng/dầu owner CHỐT GIỮ cho PHEV — không được xoá")
        }
        // Và `media_vol` (ĐỌC âm lượng) phải ở lại dù nút `vol` (ĐỔI âm lượng) đã đi — hai câu hỏi khác nhau.
        assertNotNull(CapabilityCatalog.pick("media_vol"), "`media_vol` là ô ĐỌC, khác nút `vol` đã xoá")
        assertNotNull(CapabilityCatalog.pick("anion_state"), "`anion_state` là ô ĐỌC, khác nút `anion` đã xoá")
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

    /**
     * ═══ 1.85 — `hood` bị XOÁ HẲN, nên ô/thanh nút đã lưu của owner phải tự rụng ══════════════════════════════
     *
     * Cùng khuôn bài (V) ngay trên, và cần riêng một ca vì `hood` đi một đường KHÁC: từ 1.66 nó nằm trong
     * `CapabilityCatalog.HIDDEN_FROM_PICKER` (ẩn khỏi bộ chọn nhưng `pick` VẪN tra ra, để ô ai đã đặt còn chạy).
     * 1.85 xoá mã ([ĐO xe 2026-09-20 §4] xe không có ca-pô điện) ⇒ nay `pick` phải trả `null` **và** mục ở bảng ẩn
     * phải biến mất cùng — giữ lại mục ẩn cho một mã đã chết là ghim `HIDDEN_FROM_PICKER.size` (con số mà
     * `CapabilityGroupsTest` đang trừ trong phép đếm tổng) vào một thứ hư.
     *
     * ⚠ Xe owner đang chạy 1.84; nếu `hood` còn trên màn thì sau khi nâng cấp nó phải RỤNG, không được thành ô ghi
     * hoa mã + `"—"` vĩnh viễn (lỗi [P0] mà Pass 1 của ADAS-PURGE đã vá).
     */
    @Test fun `sanitized bo ma hood da xoa o 1_85`() {
        assertNull(CapabilityCatalog.pick("hood"), "hood vẫn tra ra được ⇒ chưa xoá khỏi bộ đăng ký nào đó")
        assertNull(
            CapabilityCatalog.HIDDEN_FROM_PICKER["hood"],
            "mã đã xoá thì KHÔNG được còn mục ở bảng ẩn — 'ẩn' và 'không tồn tại' là hai trạng thái khác nhau",
        )
        val s = WorkspaceState().withSlot(0, SlotContent.Widget(listOf("hood", "soc")))
        assertEquals(listOf("hood"), s.unknownWidgetIds(), "hood phải bị NÓI RA là mã lạ")
        assertEquals(
            SlotContent.Widget(listOf("soc")), s.sanitized().slots[0],
            "hood phải rụng khỏi ô đã lưu, `soc` ở lại",
        )
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
