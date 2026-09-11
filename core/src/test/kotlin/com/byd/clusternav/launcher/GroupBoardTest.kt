package com.byd.clusternav.launcher

import com.byd.clusternav.comfort.Pm25Filter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * G1 · T3 — phần **quyết định** của ô nhóm ([GroupBoard]), kiểm off-car.
 *
 * Đây là chỗ chứng minh những thứ mà ảnh chụp máy ảo KHÔNG chứng minh được: off-car mọi ô con ra `"—"` (không bịa
 * số), số đi qua lớp đơn vị, nhãn dùng nhãn NGẮN, sắc thái cảnh báo lấy từ ngưỡng ĐANG CÓ chứ không phải ngưỡng mới.
 */
class GroupBoardTest {

    // ── Cấu trúc: model phải phản ánh ĐÚNG nhóm, không thêm không bớt ────────────────────────────

    @Test
    fun `moi nhom ra dung so o con va so nut nhu khai trong CapabilityGroups`() {
        CapabilityGroups.ALL.forEach { g ->
            val m = GroupBoard.of(g, CarStatus())
            assertEquals(g.reads.size, m.cells.size, "${g.id}: số ô con phải bằng số thành viên XEM")
            assertEquals(g.writes.size, m.actions.size, "${g.id}: số nút phải bằng số thành viên BẤM")
            assertEquals(g.reads, m.cells.map { it.id }, "${g.id}: thứ tự ô con = thứ tự khai (thứ tự trình bày)")
            assertEquals(g.writes, m.actions.map { it.id }, "${g.id}: thứ tự nút = thứ tự khai")
        }
    }

    @Test
    fun `nhom 8 thanh vien ra dung 8 o con`() {
        // Nhóm Lốp = 4 áp + 4 nhiệt. Đây là ca owner nêu đích danh ("phải xem cả 4 cùng lúc").
        val m = GroupBoard.of(CapabilityGroups.TYRES, CarStatus())
        assertEquals(8, m.cells.size)
        assertEquals(8, CapabilityGroups.TYRES.reads.size, "nếu nhóm đổi thành viên thì bài này phải được xem lại")
    }

    @Test
    fun `nhom co nut thi model noi ro dau la nut, dau la muc xem`() {
        val win = GroupBoard.of(CapabilityGroups.WINDOWS, CarStatus())
        assertTrue(win.hasActions, "nhóm Kính phải có hàng nút")
        assertEquals(CapabilityGroups.WINDOWS.writes, win.actions.map { it.id })
        // Hai danh sách RỜI nhau: một ô con hiện số, một ô con bắn lệnh — không được lẫn.
        assertTrue(
            win.cells.map { it.id }.none { it in win.actions.map { a -> a.id } },
            "ô con XEM và nút BẤM không được trùng mã",
        )
        val tyres = GroupBoard.of(CapabilityGroups.TYRES, CarStatus())
        assertFalse(tyres.hasActions, "nhóm Lốp không có gì để bấm ⇒ không có hàng nút")
        assertTrue(tyres.actions.isEmpty())
    }

    @Test
    fun `ma khong phai nhom thi tra null chu khong dung o rong`() {
        assertNull(GroupBoard.of("soc", CarStatus()), "mã datum không phải nhóm")
        assertNull(GroupBoard.of("win_lf", CarStatus()), "mã nút không phải nhóm")
        assertNull(GroupBoard.of("g_khong_ton_tai", CarStatus()))
        assertEquals("g_tyres", GroupBoard.of("g_tyres", CarStatus())?.id)
    }

    // ── Off-car: KHÔNG bịa số ────────────────────────────────────────────────────────────────────

    @Test
    fun `off-car moi o con cua moi nhom ra dau gach ngang va bao chua doc duoc`() {
        CapabilityGroups.ALL.forEach { g ->
            GroupBoard.of(g, CarStatus()).cells.forEach { c ->
                assertEquals(TelemetryView.PLACEHOLDER, c.number, "${g.id}/${c.id}: off-car phải là '—'")
                assertEquals(TelemetryView.PLACEHOLDER, c.value, "${g.id}/${c.id}: chuỗi kèm đơn vị cũng là '—'")
                assertFalse(c.available, "${g.id}/${c.id}: off-car là chưa đọc được ⇒ bộ vẽ làm mờ")
                assertEquals(GroupTone.NEUTRAL, c.tone, "${g.id}/${c.id}: chưa đọc được thì KHÔNG cảnh báo oan")
            }
        }
    }

    // ── Lớp đơn vị ───────────────────────────────────────────────────────────────────────────────

    @Test
    fun `so di qua lop don vi - ap suat theo lua chon nguoi dung`() {
        val car = CarStatus(tyres = CarStatus.Tyres(pFlKpa = 241.0))
        val bar = GroupBoard.of(CapabilityGroups.TYRES, car).cells.first { it.id == "tyre_p_fl" }
        assertEquals("2.4", bar.number, "mặc định là bar (giữ đúng thứ owner đang thấy)")
        assertEquals("bar", bar.unit)
        assertEquals("2.4 bar", bar.value)

        val psi = GroupBoard.of(
            CapabilityGroups.TYRES, car, UnitPrefs().with(Quantity.PRESSURE, "psi"),
        ).cells.first { it.id == "tyre_p_fl" }
        assertEquals("35.0", psi.number, "241 kPa = 35.0 psi")
        assertEquals("psi", psi.unit)

        val kpa = GroupBoard.of(
            CapabilityGroups.TYRES, car, UnitPrefs().with(Quantity.PRESSURE, "kPa"),
        ).cells.first { it.id == "tyre_p_fl" }
        assertEquals("241", kpa.number, "chọn đúng đơn vị gốc ⇒ số thô của xe, KHÔNG định dạng lại")
    }

    @Test
    fun `so di qua lop don vi - nhiet do theo lua chon nguoi dung`() {
        val car = CarStatus(climate = CarStatus.Climate(cabinTempC = 25))
        val c = GroupBoard.of(CapabilityGroups.CLIMATE, car).cells.first { it.id == "cabin_temp" }
        assertEquals("25 °C", c.value)
        val f = GroupBoard.of(
            CapabilityGroups.CLIMATE, car, UnitPrefs().with(Quantity.TEMPERATURE, "°F"),
        ).cells.first { it.id == "cabin_temp" }
        assertEquals("77", f.number, "25 °C = 77 °F")
        assertEquals("°F", f.unit)
    }

    @Test
    fun `datum khong co don vi thi khong bi ghep don vi gia`() {
        val car = CarStatus(body = CarStatus.Body(doorLfOpen = true))
        val c = GroupBoard.of(CapabilityGroups.DOORS, car).cells.first { it.id == "door_lf" }
        assertEquals("", c.unit, "cửa không có đơn vị")
        assertEquals("Mở", c.value, "chuỗi trạng thái không được kèm đuôi đơn vị")
    }

    // ── Nhãn ngắn ────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `o con dung NHAN NGAN cua bo dang ky`() {
        CapabilityGroups.ALL.forEach { g ->
            GroupBoard.of(g, CarStatus()).cells.forEach { c ->
                val spec = TelemetryRegistry.byId(c.id)!!
                assertEquals(spec.shortLabel, c.label, "${c.id}: ô con của nhóm rất hẹp ⇒ phải dùng nhãn ngắn")
            }
        }
        // Ca cụ thể để bài này không chỉ là phép so tautology: nhãn đầy dài hơn nhãn ngắn thật.
        val fl = GroupBoard.of(CapabilityGroups.TYRES, CarStatus()).cells.first { it.id == "tyre_p_fl" }
        assertEquals("Lốp TT", fl.label)
        assertEquals("Áp lốp trước-trái", TelemetryRegistry.byId("tyre_p_fl")!!.label)
    }

    // ── Sắc thái: dùng LẠI ngưỡng đang có, không đặt ngưỡng mới ──────────────────────────────────

    @Test
    fun `lop non hoac cang ra canh bao, lay tu TyreBoard chu khong phai nguong thu hai`() {
        val low = CarStatus(tyres = CarStatus.Tyres(pFlKpa = 190.0, pFrKpa = 240.0, pRlKpa = 240.0, pRrKpa = 240.0))
        val cells = GroupBoard.of(CapabilityGroups.TYRES, low).cells
        assertEquals(GroupTone.ALERT, cells.first { it.id == "tyre_p_fl" }.tone, "1.9 bar < ngưỡng non")
        // Phán xét phải TRÙNG với TyreBoard — nếu ai đặt ngưỡng riêng trong GroupBoard thì hai bên lệch và bài này đỏ.
        val fromTyreBoard = TyreBoard.readings(low.tyres).first { it.corner == TyreCorner.FRONT_LEFT }
        assertEquals(TyreStatus.LOW, fromTyreBoard.status)

        val high = CarStatus(tyres = CarStatus.Tyres(pFlKpa = 330.0, pFrKpa = 330.0, pRlKpa = 330.0, pRrKpa = 330.0))
        assertEquals(
            GroupTone.ALERT,
            GroupBoard.of(CapabilityGroups.TYRES, high).cells.first { it.id == "tyre_p_fl" }.tone,
            "3.3 bar > ngưỡng căng",
        )
    }

    @Test
    fun `lop lech thi la LUU Y chu khong phai canh bao, va chi banh thap nhat`() {
        // 2.4/2.4/2.4/2.0 → chênh 0.4 ≥ ngưỡng lệch; chỉ bánh THẤP NHẤT bị đánh dấu (luật của TyreBoard).
        val car = CarStatus(tyres = CarStatus.Tyres(pFlKpa = 240.0, pFrKpa = 240.0, pRlKpa = 240.0, pRrKpa = 200.0))
        val cells = GroupBoard.of(CapabilityGroups.TYRES, car).cells
        assertEquals(GroupTone.WARN, cells.first { it.id == "tyre_p_rr" }.tone)
        assertEquals(GroupTone.NEUTRAL, cells.first { it.id == "tyre_p_fl" }.tone, "bánh đủ hơi không được tô cảnh báo")
    }

    @Test
    fun `cua mo va day an toan chua that la CANH BAO`() {
        val car = CarStatus(
            body = CarStatus.Body(doorLfOpen = true, doorRfOpen = false),
            safety = CarStatus.Safety(seatbeltDriver = false, seatbeltPassenger = true),
        )
        val doors = GroupBoard.of(CapabilityGroups.DOORS, car).cells
        assertEquals(GroupTone.ALERT, doors.first { it.id == "door_lf" }.tone)
        assertEquals(GroupTone.NEUTRAL, doors.first { it.id == "door_rf" }.tone)
        val occ = GroupBoard.of(CapabilityGroups.OCCUPANTS, car).cells
        assertEquals(GroupTone.ALERT, occ.first { it.id == "seatbelt_driver" }.tone, "chưa thắt dây là cảnh báo")
        assertEquals(GroupTone.NEUTRAL, occ.first { it.id == "seatbelt_passenger" }.tone)
    }

    @Test
    fun `dang bat thi SANG LEN, khong phai canh bao`() {
        val car = CarStatus(
            lights = CarStatus.Lights(lowBeam = true, highBeam = false),
            body = CarStatus.Body(windowLfPct = 40, windowRfPct = 0),
        )
        val lights = GroupBoard.of(CapabilityGroups.LIGHTS, car).cells
        assertEquals(GroupTone.ACTIVE, lights.first { it.id == "light_low_beam" }.tone)
        assertEquals(GroupTone.NEUTRAL, lights.first { it.id == "light_high_beam" }.tone)
        val win = GroupBoard.of(CapabilityGroups.WINDOWS, car).cells
        assertEquals(GroupTone.ACTIVE, win.first { it.id == "window_lf" }.tone, "kính đang mở 40% ⇒ sáng lên")
        assertEquals(GroupTone.NEUTRAL, win.first { it.id == "window_rf" }.tone)
    }

    @Test
    fun `ESP dang tat la LUU Y, dang bat thi khong to gi`() {
        val off = CarStatus(safety = CarStatus.Safety(espOn = false))
        assertEquals(
            GroupTone.WARN,
            GroupBoard.of(CapabilityGroups.ADAS, off).cells.first { it.id == "esp_state" }.tone,
        )
        val on = CarStatus(safety = CarStatus.Safety(espOn = true))
        assertEquals(
            GroupTone.NEUTRAL,
            GroupBoard.of(CapabilityGroups.ADAS, on).cells.first { it.id == "esp_state" }.tone,
        )
    }

    @Test
    fun `cam bien ho tro lai dang keu thi canh bao`() {
        val car = CarStatus(safety = CarStatus.Safety(bsdLeftLevel = 2, bsdRightLevel = 0, dowLeft = 1))
        val cells = GroupBoard.of(CapabilityGroups.ADAS, car).cells
        assertEquals(GroupTone.ALERT, cells.first { it.id == "bsd_fl_alarm" }.tone)
        assertEquals(GroupTone.NEUTRAL, cells.first { it.id == "bsd_fr_alarm" }.tone)
        assertEquals(GroupTone.ALERT, cells.first { it.id == "dow_left" }.tone)
    }

    @Test
    fun `muc bui dung LAI nguong cua Pm25Filter`() {
        fun tone(level: Int) = GroupBoard
            .of(CapabilityGroups.CLIMATE, CarStatus(climate = CarStatus.Climate(pm25Level = level)))
            .cells.first { it.id == "pm25_level" }.tone
        assertEquals(GroupTone.ALERT, tone(Pm25Filter.HEAVY), "ngưỡng 'bẩn' đã có sẵn ở :core, không đặt mới")
        assertEquals(GroupTone.ALERT, tone(Pm25Filter.SERIOUS))
        assertEquals(GroupTone.WARN, tone(Pm25Filter.LOW_GRADE))
        assertEquals(GroupTone.NEUTRAL, tone(Pm25Filter.GOOD))
        assertTrue(Pm25Filter.isDirty(Pm25Filter.HEAVY), "bài trên dựa vào hàm này — khoá luôn giả định")
    }

    @Test
    fun `so suc khoe KHONG bi to mau vi chua co nguong do tren xe`() {
        // Cố ý: tự nghĩ ngưỡng cho nhiệt pin / điện áp cell rồi tô đỏ là **bịa cảnh báo**. Bài này khoá quyết định
        // đó lại, để lần sau ai thêm ngưỡng thì phải sửa bài test (tức phải nhìn thấy quyết định).
        val car = CarStatus(
            energy = CarStatus.Energy(battTempC = 61, cellVHigh = 4.35, soc = 3),
            safety = CarStatus.Safety(volt12v = 10.9),
        )
        val batt = GroupBoard.of(CapabilityGroups.BATTERY, car).cells
        assertEquals(GroupTone.NEUTRAL, batt.first { it.id == "batt_temp" }.tone)
        assertEquals(GroupTone.NEUTRAL, batt.first { it.id == "cell_v_high" }.tone)
        assertEquals(GroupTone.NEUTRAL, batt.first { it.id == "volt_12v" }.tone)
        assertEquals(
            GroupTone.NEUTRAL,
            GroupBoard.of(CapabilityGroups.ENERGY, car).cells.first { it.id == "soc" }.tone,
            "pin 3% vẫn không tô: dự án chưa chốt ngưỡng, và cụm đồng hồ zin đã có đèn báo riêng",
        )
    }

    // ── Cảm biến đỗ: 8 vùng ──────────────────────────────────────────────────────────────────────

    @Test
    fun `muc 8 vung doc THANG tu trang thai xe va luon du 8 phan tu`() {
        assertEquals(8, GroupBoard.RADAR_ZONE_COUNT)
        val full = CarStatus(safety = CarStatus.Safety(radarZones = listOf(0, 1, 2, 3, 4, 0, 0, 0)))
        assertEquals(listOf(0, 1, 2, 3, 4, 0, 0, 0), GroupBoard.radarLevels(full))
        // Thiếu vùng ⇒ null (chưa đọc), KHÔNG phải 0 (an toàn) — hai nghĩa khác nhau.
        val partial = CarStatus(safety = CarStatus.Safety(radarZones = listOf(0, 4, 1)))
        assertEquals(listOf(0, 4, 1, null, null, null, null, null), GroupBoard.radarLevels(partial))
        assertEquals(List<Int?>(8) { null }, GroupBoard.radarLevels(CarStatus()), "off-car: 8 vùng chưa đọc")
    }

    @Test
    fun `sac thai vung radar theo mot nguong duy nhat`() {
        assertEquals(GroupTone.NEUTRAL, GroupBoard.radarTone(null), "chưa đọc ≠ an toàn, nhưng cũng không cảnh báo")
        assertEquals(GroupTone.NEUTRAL, GroupBoard.radarTone(0))
        assertEquals(GroupTone.WARN, GroupBoard.radarTone(GroupBoard.RADAR_ALERT_LEVEL - 1))
        assertEquals(GroupTone.ALERT, GroupBoard.radarTone(GroupBoard.RADAR_ALERT_LEVEL))
        assertEquals(GroupTone.ALERT, GroupBoard.radarTone(4))
        // Ô con `radar_zones` mang sắc thái của vùng NẶNG NHẤT (một dòng chữ không thể nói tám điều).
        val car = CarStatus(safety = CarStatus.Safety(radarZones = listOf(0, 0, 4, 0, 0, 0, 0, 0)))
        assertEquals(
            GroupTone.ALERT,
            GroupBoard.of(CapabilityGroups.PARKING, car).cells.first().tone,
        )
    }

    // ── Dấu "chưa kiểm" + tóm tắt ────────────────────────────────────────────────────────────────

    @Test
    fun `nhom co thanh vien chua kiem tren xe thi mang dau chua kiem`() {
        val tyres = GroupBoard.of(CapabilityGroups.TYRES, CarStatus())
        assertTrue(tyres.needsBadge, "4 nhiệt lốp ở mức NEEDS_CAR ⇒ cả ô phải mang dấu")
        assertTrue(tyres.cells.any { it.id.startsWith("tyre_t_") && it.needsBadge })
        // Nhóm chỉ gồm thành viên đã chạy thật thì KHÔNG mang dấu — nếu không có ca này thì `needsBadge` có thể luôn
        // đúng mà bài trên vẫn xanh.
        val proven = CapabilityGroup(
            id = "g_test", label = "T", icon = "ic-bolt", domain = Domain.ENERGY,
            shape = WidgetShape.CARD, reads = listOf("soc"),
        )
        assertFalse(GroupBoard.of(proven, CarStatus()).needsBadge)
    }

    @Test
    fun `tom tat noi CAI SAI truoc, khong noi so cua thanh vien dau tien`() {
        val alert = CarStatus(body = CarStatus.Body(doorLfOpen = true, doorRfOpen = true))
        assertEquals("2 cảnh báo", GroupBoard.of(CapabilityGroups.DOORS, alert).summary())
        val warn = CarStatus(safety = CarStatus.Safety(espOn = false))
        assertEquals("1 lưu ý", GroupBoard.of(CapabilityGroups.ADAS, warn).summary())
        // Không có gì sai ⇒ hiện số chính.
        val ok = CarStatus(energy = CarStatus.Energy(soc = 82))
        assertEquals("82 %", GroupBoard.of(CapabilityGroups.ENERGY, ok).summary())
        // Off-car ⇒ không có số nào ⇒ dấu gạch, KHÔNG bịa.
        assertEquals(TelemetryView.PLACEHOLDER, GroupBoard.of(CapabilityGroups.ENERGY, CarStatus()).summary())
    }

    @Test
    fun `so chinh cua the CARD la thanh vien dau, phan con lai la so phu`() {
        val m = GroupBoard.of(CapabilityGroups.ENERGY, CarStatus(energy = CarStatus.Energy(soc = 82)))
        assertEquals("soc", m.lead?.id, "mở nhóm Năng lượng ra phải thấy % pin trước")
        assertEquals(m.cells.size - 1, m.rest.size)
        assertEquals(m.cells.drop(1).map { it.id }, m.rest.map { it.id })
        // Nhóm rỗng ô con (chỉ có nút) không được ném — `lead` là null, `rest` rỗng.
        val onlyWrites = CapabilityGroup(
            id = "g_test2", label = "T", icon = "ic-bolt", domain = Domain.BODY,
            shape = WidgetShape.STRIP, reads = emptyList(), writes = listOf("win_lf"),
        )
        val empty = GroupBoard.of(onlyWrites, CarStatus())
        assertNull(empty.lead)
        assertTrue(empty.rest.isEmpty())
        assertEquals(TelemetryView.PLACEHOLDER, empty.summary())
    }

    @Test
    fun `nut cua nhom lay nhan va icon tu bo dang ky, ke ca goi lenh`() {
        val m = GroupBoard.of(CapabilityGroups.WINDOWS, CarStatus())
        val btn = m.actions.first { ControlRegistry.byId(it.id) != null }
        assertEquals(ControlRegistry.byId(btn.id)!!.label, btn.label)
        assertEquals(ControlRegistry.byId(btn.id)!!.icon, btn.icon)
        val macro = m.actions.first { ActionMacros.byId(it.id) != null }
        assertEquals(ActionMacros.byId(macro.id)!!.label, macro.label)
        assertEquals(
            ActionMacros.byId(macro.id)!!.needsBadge(), macro.needsBadge,
            "gói lệnh mang mức bằng chứng THẤP NHẤT trong các bước ⇒ dấu chưa-kiểm phải chảy ra đúng",
        )
    }
}
