package com.byd.clusternav.launcher

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * W1c: [TelemetryReadout] — biên CarStatus → hiển thị. Field có ⇒ chữ đã format; null/off-car ⇒ "—" (available=false);
 * tier OVERDRIVE/DASHCAST ⇒ needsBadge; PROVEN ⇒ không. Thuần (không Android) → ở :core.
 */
class TelemetryReadoutTest {

    @Test fun `soc co gia tri thi hien so + available + shape RING + PROVEN khong badge`() {
        val v = TelemetryReadout.of("soc", CarStatus(energy = CarStatus.Energy(soc = 82)))!!
        assertEquals("82", v.display)
        assertTrue(v.available)
        assertEquals(WidgetShape.RING, v.shape)
        assertEquals(EvidenceTier.PROVEN, v.tier)
        assertFalse(v.needsBadge)
        assertEquals("82 %", v.displayWithUnit())
    }

    @Test fun `soc null off-car thi hien dash + not available`() {
        val v = TelemetryReadout.of("soc", CarStatus())!!
        assertEquals("—", v.display)
        assertFalse(v.available)
        assertNull(v.valueText)
        assertEquals("—", v.displayWithUnit())
    }

    @Test fun `tier OVERDRIVE la du lieu, badge da bo`() {
        val v = TelemetryReadout.of("motor_power", CarStatus(energy = CarStatus.Energy(motorPowerKw = 40)))!!
        assertEquals(EvidenceTier.OVERDRIVE, v.tier)
        assertFalse(v.needsBadge)   // 2026-09-21 owner bỏ hẳn chấm
        assertEquals("40", v.display)
    }

    @Test fun `bool format co ngu nghia`() {
        assertEquals("Bật", TelemetryReadout.of("ac_on", CarStatus(climate = CarStatus.Climate(acOn = true)))!!.display)
        assertEquals("Tắt", TelemetryReadout.of("ac_on", CarStatus(climate = CarStatus.Climate(acOn = false)))!!.display)
        // ⚠ (V) 2026-09-17: ca `is_charging` ("Có") đã gỡ cùng datum; `door_lf` ngay dưới vẫn khoá nhánh yesNo/onOff.
        assertEquals("Mở", TelemetryReadout.of("door_lf", CarStatus(body = CarStatus.Body(doorLfOpen = true)))!!.display)
        assertEquals("Đóng", TelemetryReadout.of("tailgate_status", CarStatus(body = CarStatus.Body(tailgateOpen = false)))!!.display)
    }

    @Test fun `double format lam tron`() {
        assertEquals("241", TelemetryReadout.of("tyre_p_fl", CarStatus(tyres = CarStatus.Tyres(pFlKpa = 240.6)))!!.display)  // kPa round
        assertEquals("12.4", TelemetryReadout.of("volt_12v", CarStatus(energy = CarStatus.Energy(volt12v = 12.42)))!!.display)
    }

    // ⚠ Bài `radar_zones ghep list` đã gỡ 2026-09-16 cùng datum `radar_zones` (owner gỡ toàn bộ ADAS/an toàn).

    @Test fun `string field passthrough`() {
        assertEquals("P", TelemetryReadout.of("gear", CarStatus(drivetrain = CarStatus.Drivetrain(gear = "P")))!!.display)
    }

    @Test fun `genuine NEEDS_CAR (GPS - binding None) van co case va tra dash`() {
        // ⚠ WP8 — bốn datum GPS (`gps_lat/lon/elevation/heading`, #53-56) đã XOÁ khỏi registry: đường đọc qua HAL
        // không tồn tại (`BYDAutoLocationDevice` chỉ có setter) và automation dẫn-đường-theo-lịch dùng
        // `LocationManager` của Android. Còn lại `target_soc` giữ đúng tính chất: binding None ⇒ luôn "—", mà
        // `of()` vẫn KHÁC null vì id có trong registry.
        listOf("target_soc").forEach { id ->
            val v = TelemetryReadout.of(id, CarStatus())
            assertTrue(v != null, "of($id) phải khác null (id trong registry)")
            assertFalse(v!!.available, "$id (binding None) phải là —")
            assertEquals("—", v.display)
        }
    }

    @Test fun `datum moi noi (trip_km, batt_temp) hien gia tri khi CarStatus co field`() {
        // Stage 5: trip_km / nhiệt pin từng "—" cả trên xe (thiếu field). Nay có field → hiện giá trị.
        // ⚠ WP8: `cell_temp_high` đã purge ⇒ ca thứ hai đo bằng `batt_temp` (cùng nhóm pin, cùng đường đọc int).
        val s = CarStatus(energy = CarStatus.Energy(tripKm = 12.4, battTempC = 31))
        assertEquals("12.4", TelemetryReadout.of("trip_km", s)!!.display)
        assertTrue(TelemetryReadout.of("trip_km", s)!!.available)
        assertEquals("31", TelemetryReadout.of("batt_temp", s)!!.display)
    }

    @Test fun `id la khong co trong registry tra null`() {
        assertNull(TelemetryReadout.of("khong_ton_tai", CarStatus()))
    }

    @Test fun `moi telemetry id deu of duoc (khong crash) va dash khi rong`() {
        // Off-car: mọi datum trả TelemetryView với "—" (available=false), KHÔNG null (id có trong registry).
        val empty = CarStatus()
        TelemetryRegistry.ALL.forEach { spec ->
            val v = TelemetryReadout.of(spec.id, empty)
            assertTrue(v != null, "of(${spec.id}) phải khác null (id trong registry)")
            assertEquals("—", v!!.display, "${spec.id} off-car phải là —")
        }
    }

    @Test fun `FULL WIRE - moi binding resolve duoc thi chay gia tri end-to-end qua adapter`() {
        // Dựng gateway giả trả "1" cho MỌI binding resolve được (feature-id số / named-method / car-setting),
        // chạy adapter thật (fast+slow) rồi đọc lại qua TelemetryReadout. Đây là bằng chứng E2E: registry →
        // HalBindingTable → CarStatus → TelemetryReadout không đứt ở đâu cho datum nối được.
        val getters = mutableMapOf<String, String?>()
        val features = mutableMapOf<Int, String?>()
        val settings = mutableMapOf<String, String?>()
        val names = mutableMapOf<String, Int>()
        val locals = mutableMapOf<String, String?>()
        TelemetryRegistry.ALL.forEach { spec ->
            when (val r = HalBindingTable.routeOf(spec.bindingKey)) {
                is BindingRoute.NamedMethod -> getters[r.method] = "1"
                is BindingRoute.Feature -> features[r.id] = "1"
                is BindingRoute.Setting -> settings[r.key] = "1"
                // H1 · T2 — nay CÓ telemetry Local (`media_vol` → `AudioManager.getStreamVolume`). Trước đây nhánh
                // này rỗng kèm chú thích "không có telemetry Local"; bỏ quên nó thì ô âm lượng trống mà bài vẫn xanh.
                is BindingRoute.Local -> locals[r.method] = "1"
                // V3 · R11 — bind theo TÊN HẰNG: xe giả cấp cho mỗi tên một số **tự đặt** rồi mồi giá trị vào
                // số ấy. Đó chính là điều phải canh: giá trị KHÔNG được viết cứng trong registry nữa, nên
                // đường đọc phải đi qua phép tra tên; ai gỡ phép tra đi thì mấy datum này lại ra "—".
                is BindingRoute.FeatureName -> {
                    val fake = fakeId(r.constName)
                    names[r.constName] = fake
                    features[fake] = "1"
                }
                BindingRoute.None -> {}              // GPS/target_soc → không đường đọc
            }
        }
        // ⚠ 1.85 · Getter trả MẢNG phải được mồi đúng hình dạng mảng, không phải một số đơn.
        // `int[] getPM2p5Value()` trả `[trong cabin, ngoài xe]` (javadoc BYD; gateway thật: `BydHal.arrayToStr`):
        // `pm25_value` lấy ô [0], `pm25_outside` lấy ô [1] (`HalReadTables.ARRAY_INDEX`). Mồi "1" như mọi getter
        // khác thì `pm25_outside` ra "—" và bài này ĐỎ — đúng ý nó: `coerceIntAt` CỐ Ý không lùi về số thuần cho
        // ô ≥ 1 (lùi = hiện số trong cabin dưới nhãn ngoài xe). Trước 1.85 dòng này mồi `getChargeRestTime`, một
        // getter đã không còn chủ nào từ lượt (V) FEATURE-FILTER.
        getters["getPM2p5Value"] = "[1, 2]"
        val adapter = CarDataAdapter(
            HalBindingTable(
                FakeHalGateway(
                    getters = getters, features = features, settings = settings,
                    featureNames = names, locals = locals,
                ),
            ),
        )
        val status = adapter.readSlow(adapter.readFast(CarStatus()))

        // Datum còn "—" sau khi nối đủ = ĐÚNG tập binding None (genuine NEEDS_CAR), không hơn.
        val blanks = TelemetryRegistry.ALL
            .filter { !TelemetryReadout.of(it.id, status)!!.available }.map { it.id }.toSet()
        val expectedNone = TelemetryRegistry.ALL
            .filter { HalBindingTable.routeOf(it.bindingKey) == BindingRoute.None }.map { it.id }.toSet()
        assertEquals(expectedNone, blanks, "CHỈ binding None mới được '—' sau khi nối đủ; còn lại phải có giá trị")

        // Và mọi datum resolve được PHẢI available (không còn 'pickable-but-blank' trên xe).
        TelemetryRegistry.ALL.forEach { spec ->
            if (HalBindingTable.routeOf(spec.bindingKey) != BindingRoute.None) {
                assertTrue(
                    TelemetryReadout.of(spec.id, status)!!.available,
                    "${spec.id} (binding ${spec.bindingKey}) phải chảy giá trị end-to-end",
                )
            }
        }
    }

    @Test fun `wired-tier telemetry KHONG con khe trong (moi tier != NEEDS_CAR co case va nap duoc field)`() {
        // Mọi telemetry tier wired (PROVEN/OVERDRIVE/DASHCAST) đều resolve binding + có field CarStatus.
        val getters = mutableMapOf<String, String?>()
        val features = mutableMapOf<Int, String?>()
        val settings = mutableMapOf<String, String?>()
        val names = mutableMapOf<String, Int>()
        val locals = mutableMapOf<String, String?>()
        TelemetryRegistry.ALL.filter { it.tier.wired }.forEach { spec ->
            when (val r = HalBindingTable.routeOf(spec.bindingKey)) {
                is BindingRoute.NamedMethod -> getters[r.method] = "1"
                is BindingRoute.Feature -> features[r.id] = "1"
                is BindingRoute.Setting -> settings[r.key] = "1"
                is BindingRoute.Local -> locals[r.method] = "1"     // H1 · T2 — xem chú thích ở bài FULL WIRE
                // V3 · R11 — xem chú thích cùng ca ở bài FULL WIRE.
                is BindingRoute.FeatureName -> {
                    val fake = fakeId(r.constName)
                    names[r.constName] = fake
                    features[fake] = "1"
                }
                else -> {}
            }
        }
        getters["getPM2p5Value"] = "[1, 2]"   // mảng [trong, ngoài] — xem bài FULL WIRE.
        val adapter = CarDataAdapter(
            HalBindingTable(
                FakeHalGateway(
                    getters = getters, features = features, settings = settings,
                    featureNames = names, locals = locals,
                ),
            ),
        )
        val status = adapter.readSlow(adapter.readFast(CarStatus()))
        TelemetryRegistry.ALL.filter { it.tier.wired }.forEach { spec ->
            assertTrue(
                TelemetryReadout.of(spec.id, status)!!.available,
                "wired-tier ${spec.id} phải nạp được field từ gateway giả",
            )
        }
    }

    /**
     * Số feature giả cho một tên hằng — **ổn định theo tên**, và cố ý nằm ngoài dải id thật của bảng ở trên.
     *
     * Dùng `hashCode` chứ không phải một bộ đếm: bài test bơm bảng ở một chỗ và đọc ở chỗ khác, nên hai lượt
     * phải ra cùng số. Dải âm (`or Int.MIN_VALUE`) thì không bao giờ đụng id thật nào đang khai trong registry.
     */
    private fun fakeId(constName: String): Int = constName.hashCode() or Int.MIN_VALUE

    // ══ ICON-STATE (2026-09-21) — cờ [TelemetryView.onOff] cho chip thanh trên ═════════════════════════════

    @Test fun `datum bat-tat mang co onOff, datum so va thang muc thi khong`() {
        val cl = { b: Boolean? -> CarStatus(climate = CarStatus.Climate(defrostFrontOn = b)) }
        val on = TelemetryReadout.of("defrost_front_state", cl(true))!!
        assertEquals(true, on.onOff, "đang sấy ⇒ cờ true")
        // ⚠ CHỮ GIÁ TRỊ **KHÔNG ĐỔI**: cờ là trường CỘNG THÊM cho bề mặt hẹp; ô lớn vẫn phải đọc được "Bật".
        // Nếu bài này đỏ thì việc "bỏ chữ" đã bị làm sai tầng (ở :core thay vì ở chip).
        assertEquals("Bật", on.display)

        val off = TelemetryReadout.of("defrost_front_state", cl(false))!!
        assertEquals(false, off.onOff, "đang tắt ⇒ cờ false")
        assertEquals("Tắt", off.display)

        // Chưa đọc được ⇒ null, KHÔNG phải false. "Không biết" khác "đang tắt" — tô icon mờ ở đây là bịa trạng thái.
        val unread = TelemetryReadout.of("defrost_front_state", cl(null))!!
        assertNull(unread.onOff)
        assertEquals("—", unread.display)

        // Datum SỐ không bao giờ mang cờ (chip giữ NEUTRAL + hiện giá trị).
        assertNull(TelemetryReadout.of("soc", CarStatus(energy = CarStatus.Energy(soc = 82)))!!.onOff)
        assertNull(TelemetryReadout.of("cabin_temp", CarStatus(climate = CarStatus.Climate(cabinTempC = 24)))!!.onOff)
        // Thang MỨC 3 bậc cũng không: "Tắt / Mức 1 / Mức 2" không nén được vào một icon sáng-mờ.
        assertNull(TelemetryReadout.of("seat_vent_state", CarStatus(climate = CarStatus.Climate(seatVentRaw = 1)))!!.onOff)
        // Cửa/cốp là Mở/Đóng, không phải công tắc — xem KDoc `boolOf` về vì sao cố ý để ngoài.
        assertNull(TelemetryReadout.of("door_lf", CarStatus(body = CarStatus.Body(doorLfOpen = true)))!!.onOff)
    }

    @Test fun `ca 13 datum bat-tat deu mang co (khong sot mot cai nao)`() {
        val expected = setOf(
            "ac_on", "anion_state", "defrost_front_state", "defrost_rear_state", "emergency_alarm",
            "light_low_beam", "light_high_beam", "light_front_fog", "light_rear_fog",
            "light_left_turn", "light_right_turn", "light_side", "light_drl",
        )
        val actual = TelemetryRegistry.ALL
            .filter { TelemetryReadout.of(it.id, wiredStatus("1"))!!.onOff != null }
            .map { it.id }.toSet()
        assertEquals(expected, actual, "tập datum bật/tắt đổi ⇒ cập nhật cả `boolOf` lẫn bài này có chủ ý")
    }

    /**
     * ⚠⚠ CHỐT CHỐNG-RỮA: **không datum nào được hiện chữ *"Bật"/"Tắt"* mà thiếu cờ** [TelemetryView.onOff].
     *
     * Đây là bài quan trọng nhất của lượt ICON-STATE, vì nó canh đúng cái lỗi sẽ xảy ra: ai thêm một datum bật/tắt
     * thứ 14 vào `format` (thói quen cũ, ~100 dòng ở đó) thay vì vào `boolOf` thì chip lại hiện chữ *"Tắt"* — **im
     * lặng**, compile xanh, mọi bài khác xanh. Chạy trên CẢ HAI mồi `"1"`/`"0"` nên phủ cả hai chiều bật và tắt.
     *
     * Ngoại lệ có lý do (danh sách phải bắt viết lý do, lệ `SettingsCatalog.NOT_SETTINGS`).
     */
    @Test fun `KHONG datum nao hien chu Bat-Tat ma thieu co onOff`() {
        /** Datum hiện chữ *"Tắt"* mà KHÔNG phải công tắc: thang MỨC (raw 0 ⇒ "Tắt", 1 ⇒ "Mức 1", 2 ⇒ "Mức 2"). */
        val levelScale = mapOf(
            "seat_vent_state" to "thang 3 mức (ControlLevels) — một icon sáng/mờ không nói được 'Mức 1' vs 'Mức 2'",
            "seat_heat_state" to "thang 3 mức (ControlLevels) — cùng lý do seat_vent_state",
        )
        val onOffWords = setOf("Bật", "Tắt", "On", "Off")
        listOf("1", "0").forEach { seed ->
            val status = wiredStatus(seed)
            TelemetryRegistry.ALL.forEach { spec ->
                val v = TelemetryReadout.of(spec.id, status)!!
                if (spec.id in levelScale) return@forEach
                assertEquals(
                    v.valueText in onOffWords, v.onOff != null,
                    "mồi=$seed · ${spec.id}: chữ='${v.valueText}' nhưng cờ onOff=${v.onOff} — datum bật/tắt phải " +
                        "khai ở TelemetryReadout.boolOf (không phải ở format), nếu không chip mất icon trạng thái",
                )
            }
        }
    }

    /**
     * [CarStatus] đã nạp **mọi** datum nối được, bằng gateway giả trả [seed] cho mọi binding — cùng cách hai bài
     * FULL WIRE / wired-tier ở trên dựng, gom lại để bài mới không chép lần thứ ba.
     */
    private fun wiredStatus(seed: String): CarStatus {
        val getters = mutableMapOf<String, String?>()
        val features = mutableMapOf<Int, String?>()
        val settings = mutableMapOf<String, String?>()
        val names = mutableMapOf<String, Int>()
        val locals = mutableMapOf<String, String?>()
        TelemetryRegistry.ALL.forEach { spec ->
            when (val r = HalBindingTable.routeOf(spec.bindingKey)) {
                is BindingRoute.NamedMethod -> getters[r.method] = seed
                is BindingRoute.Feature -> features[r.id] = seed
                is BindingRoute.Setting -> settings[r.key] = seed
                is BindingRoute.Local -> locals[r.method] = seed
                is BindingRoute.FeatureName -> fakeId(r.constName).let { names[r.constName] = it; features[it] = seed }
                BindingRoute.None -> {}
            }
        }
        getters["getPM2p5Value"] = "[$seed, $seed]"   // getter trả MẢNG — xem chú thích ở bài FULL WIRE
        val adapter = CarDataAdapter(
            HalBindingTable(
                FakeHalGateway(
                    getters = getters, features = features, settings = settings,
                    featureNames = names, locals = locals,
                ),
            ),
        )
        return adapter.readSlow(adapter.readFast(CarStatus()))
    }
}
