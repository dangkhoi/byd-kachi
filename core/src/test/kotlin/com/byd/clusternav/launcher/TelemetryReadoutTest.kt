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
        // ⚠ 2026-09-25: mốc "Đóng" đổi từ `tailgate_status` (đã gỡ — cốp không có cảm biến) sang `door_rr`; vẫn là
        // nhánh `openShut(false)`, tức đúng vế thứ hai mà ca này sinh ra để canh.
        assertEquals("Đóng", TelemetryReadout.of("door_rr", CarStatus(body = CarStatus.Body(doorRrOpen = false)))!!.display)
    }

    @Test fun `double format lam tron`() {
        assertEquals("241", TelemetryReadout.of("tyre_p_fl", CarStatus(tyres = CarStatus.Tyres(pFlKpa = 240.6)))!!.display)  // kPa round
        assertEquals("12.4", TelemetryReadout.of("volt_12v", CarStatus(energy = CarStatus.Energy(volt12v = 12.42)))!!.display)
    }

    // ⚠ Bài `radar_zones ghep list` đã gỡ 2026-09-16 cùng datum `radar_zones` (owner gỡ toàn bộ ADAS/an toàn).

    @Test fun `string field passthrough`() {
        assertEquals("P", TelemetryReadout.of("gear", CarStatus(drivetrain = CarStatus.Drivetrain(gear = "P")))!!.display)
    }

    /**
     * ⚠ 2026-09-25 — ca này ĐỔI CHỦ ĐỀ vì chủ đề cũ đã hết đối tượng, không phải vì luật đổi.
     *
     * WP8 gỡ bốn datum GPS (`gps_lat/lon/elevation/heading`) và để lại `target_soc` làm mốc duy nhất cho tính chất
     * *"binding [BindingRoute.None] ⇒ luôn `—`, mà `of()` vẫn KHÁC null vì id có trong registry"*. Lượt 2026-09-25 gỡ
     * luôn `target_soc` (`SET_DR_SOC_TARGET` không phân giải trên ROM xe owner) ⇒ **không còn datum nào** khai một
     * khoá chết. Nên bài giữ hai vế đo được thật:
     *  1. off-car (mọi field `null`) thì datum vẫn ra `of() != null` + `available = false` + `"—"` — KHÔNG null,
     *     KHÔNG số bịa. Đo trên `tyre_t_fl` (NEEDS_CAR, feature-id chưa ai đọc được trên xe);
     *  2. **và bộ đăng ký nay KHÔNG còn khoá chết nào** — ai thêm lại một datum mang khoá không phân giải được thì
     *     vế này ĐỎ và người thêm phải nói ra lý do tại chỗ (thay vì lặng lẽ bày một ô vĩnh viễn `"—"`).
     */
    @Test fun `off-car moi datum tra dash chu khong null, va khong con khoa chet nao`() {
        listOf("tyre_t_fl", "soh_oem", "volt_12v").forEach { id ->
            val v = TelemetryReadout.of(id, CarStatus())
            assertTrue(v != null, "of($id) phải khác null (id trong registry)")
            assertFalse(v!!.available, "$id off-car phải là —")
            assertEquals("—", v.display)
        }
        val dead = TelemetryRegistry.ALL
            .filter { HalBindingTable.routeOf(it.bindingKey) == BindingRoute.None }
            .map { "${it.id}='${it.bindingKey}'" }
        assertEquals(
            emptyList<String>(), dead,
            "datum khai khoá KHÔNG phân giải được = ô bày ra mà vĩnh viễn '—'. Thêm lại thì ghi lý do tại chỗ: $dead",
        )
    }

    @Test fun `datum moi noi (trip_km, soh_oem) hien gia tri khi CarStatus co field`() {
        // Stage 5: trip_km / sức khoẻ pin từng "—" cả trên xe (thiếu field). Nay có field → hiện giá trị.
        // ⚠ WP8: `cell_temp_high` đã purge ⇒ ca thứ hai từng đo bằng `batt_temp`.
        // ⚠ 2026-09-25: `batt_temp` cũng gỡ (`getBatteryTemp` rỗng) ⇒ ca thứ hai đo bằng `soh_oem` — cùng nhóm pin,
        // cùng đường đọc int, và nó là mã pin DUY NHẤT còn đọc được.
        val s = CarStatus(energy = CarStatus.Energy(tripKm = 12.4, sohPct = 31))
        assertEquals("12.4", TelemetryReadout.of("trip_km", s)!!.display)
        assertTrue(TelemetryReadout.of("trip_km", s)!!.available)
        assertEquals("31", TelemetryReadout.of("soh_oem", s)!!.display)
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
            // UX5b (2026-09-27) — ghế PHỤ, cùng thang, cùng lý do. Hai dòng này phải có mặt vì `wiredStatus` nay
            // nạp cả hai datum `_r` (CarDataAdapter đã nối), nên chúng cũng in ra chữ "Tắt"/"Mức n".
            "seat_vent_state_r" to "thang 3 mức (ControlLevels seatc_r) — cùng lý do seat_vent_state",
            "seat_heat_state_r" to "thang 3 mức (ControlLevels seath_r) — cùng lý do seat_heat_state",
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

    // ── UX4 · chip gió nói "AUTO n" ───────────────────────────────────────────────────────────────

    /**
     * [ĐO xe 2026-09-16] `getAcControlMode() = 0` (AUTO) **cùng lượt** với `getAcWindLevel() = 1`
     * (`oncar-trace-2026-09-16b/hal-reads.txt:1,6`) ⇒ `"AUTO 1"` là số THẬT. Phép quy đổi sống ở [ClimateAuto];
     * bài này khoá rằng chip/ô đọc được nó **qua đúng datum `ac_wind`**, không phải qua một đường thứ hai.
     */
    @Test fun `datum gio noi AUTO n khi co auto doc duoc, va chi khi do`() {
        fun wind(level: Int?, autoRaw: Int?) = TelemetryReadout.of(
            "ac_wind", CarStatus(climate = CarStatus.Climate(fanLevel = level, acWindAutoRaw = autoRaw)),
        )!!.valueText
        assertEquals("AUTO 1", wind(1, 0), "AC_WINDLEVEL_MANUAL_SIGN_OFF = 0 ⇒ đang AUTO")
        assertEquals("AUTO", wind(null, 0), "AUTO mà mức chưa về")
        assertEquals("3", wind(3, 1), "chỉnh tay ⇒ chỉ con số")
        assertEquals("2", wind(2, null), "CHƯA ĐỌC ĐƯỢC cờ auto ⇒ tuyệt đối không nói AUTO (im lặng, không nói sai)")
        assertNull(wind(null, null))
    }

    // ── UX5 · mức là một CON SỐ, không phải một chuỗi để bóc ──────────────────────────────────────

    /**
     * ⚠ Bài CHỐNG-RỮA của UX5: chữ (`format`) và số ([TelemetryView.level]) nằm ở **hai chỗ** trong
     * `TelemetryReadout` — có chủ ý, vì dạng `"id" -> s.<cụm>.<field>` là hợp đồng đọc-ngược bằng máy. Nguy cơ
     * hai-bản-sao vì thế phải được **đo**: mã nào có chữ *"Mức n"/"Tắt"* mà không có số (hoặc ngược lại) là đỏ.
     */
    @Test fun `chu muc va so muc khong bao gio lech`() {
        val levelIds = TelemetryRegistry.ALL.map { it.id }.filter { CapabilityDots.maxLevel(it) >= 1 }
        assertEquals(
            // UX5b (2026-09-27): hai → **bốn** datum thang mức (thêm ghế PHỤ). Chúng vào được danh sách này vì hai
            // nút `seatc_r`/`seath_r` nay khai `readKey` — bảng tra đảo của `CapabilityDots` chính là `readKey`.
            listOf("seat_vent_state", "seat_heat_state", "seat_vent_state_r", "seat_heat_state_r").sorted(),
            levelIds.sorted(),
            "hôm nay đúng bốn datum chạy theo thang mức; thêm cái thứ năm thì phải khai cả ở `levelTable`",
        )
        listOf("1", "0").forEach { seed ->
            val status = wiredStatus(seed)
            TelemetryRegistry.ALL.forEach { spec ->
                val v = TelemetryReadout.of(spec.id, status)!!
                // Datum BẬT/TẮT cũng in chữ "Tắt" (qua `onOff`) — nó KHÔNG phải thang mức, nên loại ra bằng
                // chính cờ ấy thay vì bằng một danh sách mã (danh sách sẽ rữa, cờ thì không).
                val saysLevel = v.onOff == null &&
                    (v.valueText == "Tắt" || v.valueText?.startsWith("Mức ") == true)
                assertEquals(
                    saysLevel, v.level != null,
                    "mồi=$seed · ${spec.id}: chữ='${v.valueText}' mà mức=${v.level} — hai chỗ đã lệch nhau",
                )
            }
        }
    }

    // ── UX8 · datum HAI CHẾ ĐỘ — mã trạng thái ([TelemetryView.state]) ────────────────────────────────────────

    /**
     * Hai bảng phải nói **cùng một sự thật**: bảng ĐỌC (`TelemetryReadout.stateTable` — mã trạng thái nằm ở field
     * nào) và bảng HÌNH ([CapabilityIcons.stateIconTable] — mỗi mã một hình).
     *
     * Cùng cách đo với bài mức ngay trên: mồi CẢ HAI chiều qua [wiredStatus] rồi đòi **chữ** và **mã** đổi CÙNG
     * NHỊP. Bảng đọc trỏ nhầm field thì mã đứng yên trong khi chữ đổi ⇒ đỏ ngay, không phải hy vọng.
     *
     * Vì sao cần: nếu một datum khai hình mà bảng đọc quên khai, chip **không nổ** — nó lặng lẽ đứng mãi ở hình
     * trung tính và không ai thấy gì (đúng bệnh "hàm mới chưa từng được gọi" của CLAUDE.md §8).
     */
    @Test fun `hinh trang thai va chu trang thai khong bao gio lech`() {
        val declared = CapabilityIcons.stateIconTable().keys.sorted()
        assertEquals(
            listOf("ac_cycle", "pm25_online"), declared,
            "hôm nay đúng hai datum hai chế độ; thêm cái thứ ba thì phải khai cả ở `stateTable`",
        )
        val seen = mutableMapOf<String, MutableSet<String?>>()
        listOf("1" to 1, "0" to 0).forEach { (seed, want) ->
            val status = wiredStatus(seed)
            TelemetryRegistry.ALL.forEach { spec ->
                val v = TelemetryReadout.of(spec.id, status)!!
                if (spec.id in declared) {
                    assertEquals(want, v.state, "mồi=$seed · ${spec.id}: mã trạng thái sai (bảng đọc trỏ nhầm field?)")
                    seen.getOrPut(spec.id) { mutableSetOf() } += v.valueText
                } else {
                    assertNull(v.state, "${spec.id} không khai hình theo trạng thái mà vẫn sinh mã ${v.state}")
                }
            }
        }
        seen.forEach { (id, texts) ->
            assertEquals(
                2, texts.size,
                "$id: mã trạng thái đổi 1↔0 mà CHỮ đứng yên ($texts) — hai bảng đang đọc hai field khác nhau",
            )
        }
    }

    /** Chưa đọc được ⇒ `null`, **không** phải `0`: `0` là lời khẳng định *"xe đang ở chế độ thứ nhất"*. */
    @Test fun `chua doc duoc thi ma trang thai la null, khong phai 0`() {
        assertNull(TelemetryReadout.of("ac_cycle", CarStatus())!!.state)
        assertEquals(TelemetryView.PLACEHOLDER, TelemetryReadout.of("ac_cycle", CarStatus())!!.display)
        assertEquals(0, TelemetryReadout.of("ac_cycle", CarStatus(climate = CarStatus.Climate(recircOn = false)))!!.state)
        assertEquals(1, TelemetryReadout.of("ac_cycle", CarStatus(climate = CarStatus.Climate(recircOn = true)))!!.state)
    }

    @Test fun `muc doc ra dung thang do tren xe, ma ngoai thang thi im lang`() {
        fun heat(raw: Int?) = TelemetryReadout.of("seat_heat_state", CarStatus(climate = CarStatus.Climate(seatHeatRaw = raw)))!!
        // [ĐO xe 2026-09-17] thang ghế: OFF = 1 · mức 1 = 2 · mức 2 = 3 (ControlLevels.RAW_BY_LEVEL).
        assertEquals(0, heat(1).level, "raw 1 = TẮT ⇒ mức 0 (KHÔNG phải null: 'đang tắt' là một lời khẳng định)")
        assertEquals("Tắt", heat(1).valueText)
        assertEquals(2, heat(3).level); assertEquals("Mức 2", heat(3).valueText)
        assertNull(heat(99).level, "mã ngoài thang ⇒ 'chưa biết', không làm tròn thành mức 1")
        assertNull(heat(99).valueText)
        assertNull(heat(null).level, "chưa đọc được ⇒ null, và null ≠ 0")
    }

    /**
     * UX5b — hai datum ghế **PHỤ** đi qua ĐÚNG cùng thang, và đọc đúng field của mình.
     *
     * Ca quan trọng nhất là ca CHÉO: mồi ghế lái, hỏi ghế phụ ⇒ phải ra *"chưa biết"*. Một dòng `levelTable` chép
     * sai field (dán từ dòng ghế lái) sẽ làm chip ghế phụ hiện **số của ghế lái** — nói sai, im lặng.
     */
    @Test fun `ghe PHU doc dung field cua minh, cung thang muc`() {
        fun vent(raw: Int?) = TelemetryReadout.of(
            "seat_vent_state_r", CarStatus(climate = CarStatus.Climate(seatVentRRaw = raw)),
        )!!
        fun heat(raw: Int?) = TelemetryReadout.of(
            "seat_heat_state_r", CarStatus(climate = CarStatus.Climate(seatHeatRRaw = raw)),
        )!!
        // [ĐO xe 2026-09-17] thang ghế mát (cả hai ghế, `ControlLevels`): OFF = 1 · mức 1 = 2 · mức 2 = 3.
        assertEquals(0, vent(1).level); assertEquals("Tắt", vent(1).valueText)
        assertEquals(1, vent(2).level); assertEquals("Mức 1", vent(2).valueText)
        assertEquals(2, vent(3).level); assertEquals("Mức 2", vent(3).valueText)
        assertNull(vent(99).level, "mã ngoài thang ⇒ 'chưa biết', không làm tròn")
        assertNull(vent(null).level)
        assertEquals(0, heat(1).level); assertEquals(2, heat(3).level)

        // ⚠ CHÉO: mồi ghế LÁI, hỏi ghế PHỤ (và ngược lại) ⇒ tuyệt đối không được mượn số của nhau.
        val driverOnly = CarStatus(climate = CarStatus.Climate(seatHeatRaw = 3, seatVentRaw = 3))
        assertNull(TelemetryReadout.of("seat_heat_state_r", driverOnly)!!.level)
        assertNull(TelemetryReadout.of("seat_vent_state_r", driverOnly)!!.level)
        val passengerOnly = CarStatus(climate = CarStatus.Climate(seatHeatRRaw = 3, seatVentRRaw = 3))
        assertNull(TelemetryReadout.of("seat_heat_state", passengerOnly)!!.level)
        assertNull(TelemetryReadout.of("seat_vent_state", passengerOnly)!!.level)

        // Nhãn nói rõ BÊN NÀO — đó là thứ owner thấy thiếu (2026-09-27).
        assertEquals("Mức ghế sưởi phụ", heat(1).label)
        assertEquals("Ghế sưởi phụ", TelemetryRegistry.byId("seat_heat_state_r")!!.displayShortLabel)
    }

    /**
     * ⚠ Thang mức ở [ControlLevels] có thể RỘNG HƠN số lựa chọn mà nút bày ra ([CapabilityDots.maxLevel]).
     * Hôm nay đúng `seath`/`seath_r` lệch, và đó là một [SUY] đã ghi (chờ đo trên xe), không phải một lỗi ngầm.
     * Bài này giữ nó **nhìn thấy được**: đo xong thang ghế sưởi thì hai bên phải khớp lại, và bài đỏ nhắc điều đó.
     */
    @Test fun `thang muc rong hon so lua chon thi phai la mot lech DA BIET`() {
        val known = mapOf(
            "seath" to "[SUY] khai 4 mã khung (1,2,3,4) nhưng chỉ bày 3 lựa chọn — chờ điểm đo trên xe",
            "seath_r" to "[SUY] cùng thang với seath (chỉ khác seatID) — chờ cùng một phép đo",
        )
        // ⚠ UX5b: `seatc_r`/`seath_r` nay có `readKey` nên `CapabilityDots.maxLevel` của chúng đi qua chính
        // `ControlRegistry` (nút SELECT 3 lựa chọn) như trước — lệch của `seath_r` vì thế KHÔNG đổi, vẫn đúng một
        // lệch [SUY] đã khai. Nếu bảng trên bỗng dài ra thì có ai vừa thêm mức mà chưa đo.
        val mismatch = ControlLevels.RAW_BY_LEVEL.keys.filter {
            ControlLevels.levelCount(it) - 1 != CapabilityDots.maxLevel(it)
        }
        assertEquals(known.keys.sorted(), mismatch.sorted(), "lệch MỚI ⇒ hoặc sửa bảng, hoặc khai kèm lý do")
        ControlLevels.RAW_BY_LEVEL.keys.forEach {
            assertTrue(
                ControlLevels.levelCount(it) - 1 >= CapabilityDots.maxLevel(it),
                "$it: nút bày nhiều mức hơn thang đọc được ⇒ có mức bấm tới mà không đọc lại được",
            )
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
