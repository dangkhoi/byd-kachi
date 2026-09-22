package com.byd.clusternav.launcher

import com.byd.clusternav.comfort.Pm25Filter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
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

    // Phần *"dây an toàn chưa thắt"* của bài này đã gỡ 2026-09-16 cùng nhóm `g_occupants` (owner gỡ ADAS/an toàn).
    @Test
    fun `cua mo la CANH BAO`() {
        val car = CarStatus(body = CarStatus.Body(doorLfOpen = true, doorRfOpen = false))
        val doors = GroupBoard.of(CapabilityGroups.DOORS, car).cells
        assertEquals(GroupTone.ALERT, doors.first { it.id == "door_lf" }.tone)
        assertEquals(GroupTone.NEUTRAL, doors.first { it.id == "door_rf" }.tone)
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

    // ⚠ Hai bài `ESP dang tat la LUU Y` và `cam bien ho tro lai dang keu thi canh bao` đã gỡ 2026-09-16: nhóm
    // `g_adas` và mọi datum của nó (ESP · điểm mù · chuyển làn · cắt ngang sau · cảnh báo mở cửa) không còn tồn tại.

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
        // Cố ý: tự nghĩ ngưỡng cho nhiệt pin / sức khoẻ pin rồi tô đỏ là **bịa cảnh báo**. Bài này khoá quyết định
        // đó lại, để lần sau ai thêm ngưỡng thì phải sửa bài test (tức phải nhìn thấy quyết định).
        // ⚠ WP8 — `cell_v_high` đã purge (#5-18), nên ca "điện áp cell" đo bằng `soh_oem` (số sức khoẻ còn lại
        // trong nhóm pin). Tính chất canh không đổi: KHÔNG tự nghĩ ngưỡng rồi tô màu.
        val car = CarStatus(
            energy = CarStatus.Energy(battTempC = 61, sohPct = 71, soc = 3, volt12v = 10.9),
        )
        val batt = GroupBoard.of(CapabilityGroups.BATTERY, car).cells
        assertEquals(GroupTone.NEUTRAL, batt.first { it.id == "batt_temp" }.tone)
        assertEquals(GroupTone.NEUTRAL, batt.first { it.id == "soh_oem" }.tone)
        assertEquals(GroupTone.NEUTRAL, batt.first { it.id == "volt_12v" }.tone)
        assertEquals(
            GroupTone.NEUTRAL,
            GroupBoard.of(CapabilityGroups.ENERGY, car).cells.first { it.id == "soc" }.tone,
            "pin 3% vẫn không tô: dự án chưa chốt ngưỡng, và cụm đồng hồ zin đã có đèn báo riêng",
        )
    }

    // ⚠ Hai bài về **8 vùng cảm biến đỗ** đã gỡ 2026-09-16 cùng nhóm `g_parking`, datum `radar_zones`/
    // `radar_volume` và mọi phép radar ở `GroupBoard` (owner gỡ toàn bộ ADAS/an toàn khỏi launcher).

    // ── Dấu "chưa kiểm" + tóm tắt ────────────────────────────────────────────────────────────────

    @Test
    fun `2026-09-21 badge da bo - nhom va cell khong con dau`() {
        val tyres = GroupBoard.of(CapabilityGroups.TYRES, CarStatus())
        assertFalse(tyres.needsBadge, "owner bỏ hẳn chấm ⇒ ô nhóm không mang dấu")
        assertFalse(tyres.cells.any { it.needsBadge }, "không cell nào còn mang dấu")
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
        // Sắc thái LƯU Ý: lốp lệch (bánh thấp nhất) — ca WARN duy nhất còn lại sau lượt gỡ ADAS/an toàn.
        val warn = CarStatus(tyres = CarStatus.Tyres(pFlKpa = 240.0, pFrKpa = 240.0, pRlKpa = 240.0, pRrKpa = 200.0))
        assertEquals("1 lưu ý", GroupBoard.of(CapabilityGroups.TYRES, warn).summary())
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
        // ⚠ [KIỂM TOÁN 2026-09-12 mục 4] Mốc này SIẾT lại, không nới: trước đây nó đòi nhãn ĐẦY (`label`), nay đòi
        // nhãn NGẮN theo ngôn ngữ (`displayShortLabel`). Lý do là [ĐO] ảnh máy ảo: hàng nút của ô nhóm chia 6 ô trên
        // khung 4/12 màn ⇒ 82px/ô, nhãn đầy bị cắt `"Window front-ri…"` / `"Kính trước-p…"` ⇒ hai kính TRƯỚC đọc ra y
        // hệt nhau. Cùng luật đã áp cho ô con XEM ở U5·T2 (`displayShortLabel`), nay áp cho ô BẤM.
        assertEquals(ControlRegistry.byId(btn.id)!!.displayShortLabel, btn.label)
        assertEquals("Kính lái", btn.label, "nhãn ngắn phải theo ĐÚNG quy ước bảng lốp (`Lốp TT`), không viết tắt kiểu khác")
        assertEquals(ControlRegistry.byId(btn.id)!!.icon, btn.icon)
        // ⚠ 1.94 (owner 2026-09-22): sau khi kính thành nút TƯỜNG MINH, KHÔNG nhóm nào còn chứa gói lệnh (macro).
        // Board chỉ render control/telemetry. Kiểm mọi action của board đều là control THẬT (không macro mồ côi).
        assertTrue(m.actions.all { ControlRegistry.byId(it.id) != null },
            "nút board lấy nhãn/icon từ ControlRegistry")
    }

    /**
     * ⚠⚠ **[KIỂM TOÁN 2026-09-12 mục 4] Nhãn nút của nhóm phải VỪA ô hẹp — ở CẢ hai thứ tiếng.**
     *
     * [ĐO] ảnh máy ảo: hàng nút của ô nhóm chia bề ngang cho tối đa 6 ô ⇒ **82px/ô** ở khung 4/12 màn (≈70px dùng
     * được cho chữ), nhãn hai dòng ở 10.5sp ⇒ khoảng **9 ký tự mỗi dòng**. Nhãn đầy vượt mức đó bị cắt:
     * `"Window front-ri…"` / `"Kính trước-p…"` ⇒ hai kính TRƯỚC đọc ra y hệt nhau.
     *
     * Hai điều kiện, và **điều kiện thứ hai mới là điều kiện thật**:
     *  1. tổng ≤ [SHORT_CAP_TILE] ký tự;
     *  2. **từ dài nhất** ≤ [WORD_CAP_TILE] — `TextView` chỉ ngắt dòng ở dấu CÁCH, nên `"Kính trước-trái"` (15 ký tự,
     *     từ dài nhất `"trước-trái"` = 10) cần **ba** dòng và bị cắt dù mỗi từ đều ngắn. Chỉ đếm tổng thì luật này
     *     xanh với những nhãn vẫn cắt.
     *
     * Kiểm trên **chuỗi thật sẽ hiện** (`GroupBoard.of(...).actions`), không kiểm registry — nhờ vậy nếu ai đổi
     * `action()` về nhãn đầy thì bài đỏ, chứ không chỉ khi ai xoá `short`.
     */
    @Test
    fun `nhan nut cua nhom vua o hep o ca hai thu tieng`() {
        val tooLong = ArrayList<String>()
        listOf(Lang.VI, Lang.EN).forEach { lang ->
            Strings.current = lang
            CapabilityGroups.ALL.filter { it.hasWrites }.forEach { g ->
                GroupBoard.of(g, CarStatus()).actions.forEach { a ->
                    val word = a.label.split(' ').maxOfOrNull { it.length } ?: 0
                    if (a.label.length > SHORT_CAP_TILE || word > WORD_CAP_TILE) {
                        tooLong += "$lang ${a.id}='${a.label}' (${a.label.length} ký tự, từ dài nhất $word)"
                    }
                }
            }
        }
        Strings.current = Lang.VI
        assertTrue(
            tooLong.isEmpty(),
            "nhãn nút vượt chỗ của ô hẹp ⇒ sẽ bị cắt trên màn. Khai `short`/`shortEn` cho nút đó (khuôn " +
                "`TelemetrySpec.short`), đừng viết tắt ở tầng vẽ: $tooLong",
        )
    }

    // ⚠ Bốn bài về **bảng sơ đồ hai bên xe** (`GroupBoard.sidePlan`) đã gỡ 2026-09-16: cả phép đó, kiểu
    // `SideBoardPlan`, `GroupSide` lẫn ô vẽ `SideBoardView` đều xoá cùng nhóm `g_adas` (owner gỡ ADAS/an toàn).

    /**
     * ⚠⚠ **[THỬ PHÁ tìm ra] Luật icon của HÀNG NÚT trước đó KHÔNG có ai canh.**
     *
     * [ĐO] đổi thân [GroupBoardModel.actionIconsDistinguish] thành `get() = true` — tức hàng nút **luôn** vẽ icon, kể
     * cả bốn nút kính cùng một hình — rồi chạy cả `:core:test` lẫn `:app:testDebugUnitTest`: **0 bài đỏ**. Mà chính
     * con số 30px lấy lại từ việc bỏ icon là phần làm hàng nút hết bị cắt, nên đó là một luật **đang giữ một lỗi
     * nhìn-thấy-được không mọc lại** mà không có gì bảo vệ.
     *
     * Bài này chốt luật bằng **dữ liệu thật của 3 nhóm có nút**, không bằng một ca dựng tay: nhóm *Kính* có 4/6 nút
     * cùng `ic-window` ⇒ icon vô nghĩa ⇒ bỏ; hai nhóm kia lặp tối đa 2 ⇒ giữ. Ngày ai thêm/bớt nút là phải xem lại
     * con số ở đây, đúng kiểu bài `phep chia hang khong doi hinh dang cua 12 nhom dang co`.
     *
     * ⚠ [ĐO] **giả định của tôi về nhóm *Đèn* bị dữ liệu bác**: tôi viết `g_lights to true` (tưởng nó chỉ lặp 2 lần)
     * và bài đỏ ngay — thật ra `headl` · `headlight_mode` · `drl` **đều** dùng `ic-light` ⇒ lặp 3 ⇒ nhóm Đèn cũng bỏ
     * icon. Giữ số đo, sửa giả định — và đây chính là lý do bài này đọc registry thật thay vì chép một danh sách.
     *
     * ## ⚠⚠ [SOÁT P1-1] Nhóm *Đèn* đã ĐỔI CHIỀU `false → true`, và đó là hệ quả ĐÚNG
     * Bản vá P1-1 bỏ `headl` khỏi nhóm (nó trùng byte với `headlight_mode` — xem `CapabilityGroups.LIGHTS`). [ĐO]
     * hàng nút nay là `headlight_mode:ic-light` · `drl:ic-light` · `readl:ic-readlight` ⇒ lặp tối đa **2**, dưới trần 3
     * ⇒ icon phân biệt được trở lại nên được vẽ trở lại. Tức bỏ cái nút trùng **cũng trả lại icon** cho hàng đó: cái
     * làm icon vô nghĩa chính là mục thứ ba cùng hình. Đây là ví dụ đúng của việc bài canh này đọc dữ liệu thật —
     * nó phát hiện thay đổi hình dạng do một bản vá ở chỗ khác gây ra, và bắt người sửa xác nhận bằng số đo.
     */
    @Test
    fun `luat icon cua hang nut theo dung du lieu that cua 3 nhom co nut`() {
        val verdict = CapabilityGroups.ALL.filter { it.hasWrites }.associate { g ->
            g.id to GroupBoard.of(g, CarStatus()).actionIconsDistinguish
        }
        assertEquals(
            mapOf("g_windows" to false, "g_doors" to true, "g_lights" to true),
            verdict,
            "[ĐO] sau U7 cả ba nhóm có nút đều phân biệt được bằng icon. Nhóm *Kính* ĐỔI CHIỀU `false → true`: " +
                "bốn nút kính trước đây cùng mang `ic-window` (4/6 ô một hình ⇒ icon vô nghĩa ⇒ bỏ để lấy lại " +
                "30px bề cao), nay mỗi nút mang hình xe với ĐÚNG ô kính của nó tô đặc ⇒ lặp tối đa 1. Đây đúng " +
                "chiều mà luật này mong đợi: khi icon nói được điều gì thì hàng nút vẽ icon trở lại",
        )
        // Và luật phải dùng CHUNG trần với ô con XEM — hai trần khác nhau cho cùng một câu hỏi là bẫy hai-bản-sao.
        val m = GroupBoard.of(CapabilityGroups.WINDOWS, CarStatus())
        assertEquals(
            m.actions.groupingBy { it.icon }.eachCount().none { it.value >= 3 }, m.actionIconsDistinguish,
            "trần lặp icon của hàng nút phải là CÙNG con số với ô con XEM (3)",
        )
    }

    private companion object {
        /** Trần ký tự của nhãn nút trong ô HẸP — [ĐO] 82px ÷ 2 dòng ở 10.5sp ≈ 9 ký tự/dòng. */
        const val SHORT_CAP_TILE = 14

        /** Trần độ dài MỘT TỪ: `TextView` chỉ ngắt ở dấu cách, nên từ dài hơn một dòng thì cắt bất kể tổng bao nhiêu. */
        const val WORD_CAP_TILE = 10
    }
}
