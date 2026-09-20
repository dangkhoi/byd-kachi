package com.byd.clusternav.launcher

/**
 * ADAPTER ĐỌC XE (W1b) — nối [CarDataPort] (6 method cũ, GIỮ cho widget hiện có) + [CarStatusReader] (build
 * [CarStatus] đầy đủ theo 2 nhịp poll của [CarStatusRepository]). Mọi giá trị đọc qua [HalBindingTable] theo `id`
 * của [TelemetryRegistry]; off-car → null ⇒ UI "—" (R3/OQ1: KHÔNG demo).
 *
 * Map `id` → field [CarStatus] theo §2 handoff (id ↔ tên field gần khớp). Per-index (kính/lốp/đèn) suy từ hậu tố
 * id trong [HalBindingTable]. KHÔNG gate an toàn.
 *
 * ## H1 (PERF 2026-09-16) — [demand]: đọc thứ ĐANG HIỆN, giữ nguyên phần còn lại
 * [demand] trả tập `id` mà màn hình thật sự bày ra ([CarDataDemand.of]); `null` = *"không tính được ⇒ đọc hết"*
 * (mặc định, và là hành vi y hệt mọi bản trước 1.67). Datum ngoài tập ấy **KHÔNG được đọc** và **GIỮ giá trị cũ**
 * của [CarStatus] — cố ý không đặt `null`, vì `null` ở đây nghĩa là *"không đọc được"* và sẽ biến ô đang hiện
 * thành "—" ngay khi nó rời khỏi tập nhu cầu trong một nhịp giao thời.
 *
 * ⚠ Cổng CHỈ áp cho hai hàm nhịp ([readFast]/[readSlow]). Sáu method [CarDataPort] và mọi đường đọc tường minh
 * (cầu `sweep`/`read`, màn kiểm-tra-từng-nút, câu hỏi bằng giọng) đi thẳng [HalBindingTable] nên không bị lọc —
 * xem khối ⚠ trong KDoc [CarDataDemand].
 */
class CarDataAdapter(
    private val table: HalBindingTable,
    private val demand: () -> Set<String>? = { null },
    private val absent: HalAbsentCache = HalAbsentCache(),
    private val clock: () -> Long = System::currentTimeMillis,
    /**
     * Tập mã NÚT đang hiện có đường đọc ([CarDataDemand.controlsOf]) — đọc giá trị THẬT của chúng vào
     * [CarStatus.controls] mỗi nhịp CHẬM. Mặc định rỗng ⇒ mọi test/reader cũ không đổi hành vi.
     */
    private val controlDemand: () -> Set<String> = { emptySet() },
) : CarDataPort, CarStatusReader {

    /**
     * Một lượt đọc có **hai** cổng. Chụp tập nhu cầu + mốc giờ MỘT lần cho cả nhịp (gọi [demand] 123 lần mỗi nhịp
     * là đúng kiểu chi phí mà cổng này sinh ra để cắt), rồi mỗi field hỏi hai câu:
     *  1. *màn có đang bày không* ([CarDataDemand]) — nếu không thì GIỮ giá trị cũ;
     *  2. *xe này có không* ([HalAbsentCache]) — datum đã `null` liên tiếp thì giãn nhịp hỏi lại.
     *
     * Thứ tự QUAN TRỌNG: hỏi nhu cầu TRƯỚC. Một datum không hiện thì không được tính là "miss" — nếu không, cái
     * không đọc lại tự nguội, rồi lúc người dùng kéo nó lên màn thì nó câm cho tới lượt thử lại.
     */
    private class Gate(
        private val table: HalBindingTable,
        private val want: Set<String>?,
        private val absent: HalAbsentCache,
        private val now: Long,
    ) {
        private fun wanted(id: String): Boolean {
            val w = want ?: return true
            if (id in w) return true
            KachiPerf.add(KachiPerf.Counter.HAL_SKIP_OFFSCREEN)
            return false
        }

        /**
         * ⚠ Hai lối bỏ qua trả về HAI thứ khác nhau, có chủ ý:
         *  • *không hiện* ⇒ trả [prev] — ta KHÔNG biết gì mới, mà cũng chưa kết luận gì; xoá đi thì lúc ô quay lại
         *    màn hình nó nháy "—" một nhịp dù dữ liệu cũ vẫn còn đúng.
         *  • *đang nguội* ⇒ trả `null` (= "—") — ta ĐÃ kết luận datum này đọc không ra. Trả [prev] ở đây là đóng
         *    băng con số cuối cùng đọc được, tức ô hiện một giá trị CŨ mà trông như đang sống (đúng bệnh
         *    "SurfaceView giữ khung hình cuối" mà `SlotLiveProbe` sinh ra để chữa, lần này bằng số).
         */
        private inline fun <T> read(id: String, prev: T?, body: () -> T?): T? {
            if (!wanted(id)) return prev
            if (!absent.shouldRead(id, now)) { KachiPerf.add(KachiPerf.Counter.HAL_SKIP_ABSENT); return null }
            val v = body()
            absent.record(id, v != null, now)
            return v
        }

        fun int(id: String, prev: Int?): Int? = read(id, prev) { table.readInt(id) }
        fun dbl(id: String, prev: Double?): Double? = read(id, prev) { table.readDouble(id) }
        fun bool(id: String, prev: Boolean?): Boolean? = read(id, prev) { table.readBool(id) }
        fun str(id: String, prev: String?): String? = read(id, prev) { table.readString(id) }
    }

    private fun gate() = Gate(table, demand(), absent, clock())

    /** Nhịp NHANH chỉ đáng chạy khi màn đang bày ít nhất một datum nhanh — xem [CarDataDemand.needsFast]. */
    override fun fastNeeded(): Boolean = CarDataDemand.needsFast(demand())

    /**
     * [SOÁT P2-1 · 2026-09-16] Quên mọi kết luận *"xe này không có datum ấy"* ([HalAbsentCache.clear]).
     *
     * Vì sao phải có chỗ gọi, không để `clear()` nằm không: giãn nhịp chạm trần **10 phút**, nên một datum vắng
     * lâu rồi mới có (ETA sạc lúc vừa cắm sạc, ghế/ECU lúc vừa nổ máy) có thể câm tới 10 phút — trong khi người
     * dùng vừa mở màn chính lên đúng để xem nó.
     *
     * Gọi CÙNG chỗ với [CarDataDemand.Holder.clear] (màn rời tiền cảnh): lượt poll đầu sau khi màn quay lại vốn
     * đã là một lượt **đọc hết** (nhu cầu `null`), nên gỡ kết luận cũ ở đúng đó **không tốn thêm một lượt đọc
     * nào** — nó chỉ làm lượt đọc-hết ấy thật sự đọc hết.
     */
    fun forgetAbsent() = absent.clear()

    // ── 6 method CŨ (tương thích WorkspaceView/WidgetViews) ────────────────────────────────────────────
    override fun batteryPercent(): Int? = table.readInt("soc")
    override fun rangeKm(): Int? = table.readInt("ev_range_km")

    /** Áp suất 4 lốp theo BAR cho widget cũ (HAL trả kPa → ÷100). [trước-trái, trước-phải, sau-trái, sau-phải]. */
    override fun tirePressuresBar(): List<Double>? {
        val fl = table.readDouble("tyre_p_fl"); val fr = table.readDouble("tyre_p_fr")
        val rl = table.readDouble("tyre_p_rl"); val rr = table.readDouble("tyre_p_rr")
        if (fl == null && fr == null && rl == null && rr == null) return null
        fun bar(kpa: Double?) = ((kpa ?: 0.0) / 100.0)
        return listOf(bar(fl), bar(fr), bar(rl), bar(rr))
    }

    override fun pm25Level(): Int? = table.readInt("pm25_level")
    override fun speedKmh(): Int? = table.readInt("speed")
    override fun outsideTempC(): Int? = table.readInt("ext_temp")

    // ── NHỊP NHANH (~1s): tốc độ / động lực / công suất ─────────────────────────────────────────────────
    override fun readFast(prev: CarStatus): CarStatus {
        val g = gate()
        val d = prev.drivetrain
        return prev.copy(
            drivetrain = CarStatus.Drivetrain(
                speedKmh = g.int("speed", d.speedKmh),
                accelPct = g.int("accel_pct", d.accelPct),
                brakePct = g.int("brake_pct", d.brakePct),
                motorFrontRpm = g.int("motor_front_rpm", d.motorFrontRpm),
                steeringDeg = g.int("steering_deg", d.steeringDeg),
                slopeDeg = g.int("slope_deg", d.slopeDeg),
                gear = g.str("gear", d.gear),
                opMode = g.str("op_mode", d.opMode),
                energyMode = g.str("energy_mode", d.energyMode),
                motorRearRpm = g.int("motor_rear_rpm", d.motorRearRpm),
                motorFrontTorqueNm = g.int("motor_front_torque", d.motorFrontTorqueNm),
                engineRpm = g.int("engine_rpm", d.engineRpm),
                wheelSpeedKmh = g.int("wheel_speed", d.wheelSpeedKmh),
            ),
            energy = prev.energy.copy(motorPowerKw = g.int("motor_power", prev.energy.motorPowerKw)),
        )
    }

    // ── NHỊP CHẬM (~10s): pin/tầm/sạc · khí hậu · lốp · thân xe · đèn · danh tính ────────────────────────
    override fun readSlow(prev: CarStatus): CarStatus {
        val g = gate()
        val e = prev.energy; val c = prev.climate; val t = prev.tyres
        val b = prev.body; val l = prev.lights; val i = prev.identity
        return prev.copy(
            energy = e.copy(   // GIỮ motorPowerKw của nhịp nhanh
                soc = g.int("soc", e.soc),
                evRangeKm = g.int("ev_range_km", e.evRangeKm),
                fuelRangeKm = g.int("fuel_range_km", e.fuelRangeKm),
                odometerKm = g.int("odometer", e.odometerKm),
                battTempC = g.int("batt_temp", e.battTempC),
                sohPct = g.int("soh_oem", e.sohPct),
                targetSoc = g.int("target_soc", e.targetSoc),
                fuelPct = g.int("fuel_pct", e.fuelPct),
                evMileageKm = g.int("ev_mileage_km", e.evMileageKm),
                tripKm = g.dbl("trip_km", e.tripKm),
                tripHours = g.dbl("trip_hours", e.tripHours),
                tripKwh = g.dbl("trip_kwh", e.tripKwh),
                consumption50 = g.dbl("consumption_50km", e.consumption50),
                cellTempHighC = g.int("cell_temp_high", e.cellTempHighC),
                cellTempLowC = g.int("cell_temp_low", e.cellTempLowC),
                cellTempAvgC = g.int("cell_temp_avg", e.cellTempAvgC),
                cellVHigh = g.dbl("cell_v_high", e.cellVHigh),
                cellVLow = g.dbl("cell_v_low", e.cellVLow),
                // Điện 12V + nguồn MCU — trước 2026-09-16 nằm ở cụm `Safety`, chuyển sang đây cùng lượt gỡ ADAS.
                volt12v = g.dbl("volt_12v", e.volt12v),
                volt12vLevel = g.int("volt_12v_level", e.volt12vLevel),
            ),
            climate = CarStatus.Climate(
                pm25Level = g.int("pm25_level", c.pm25Level),
                pm25ValueUgm3 = g.int("pm25_value", c.pm25ValueUgm3),
                // 1.85 — ô [1] của CÙNG getter (`HalReadTables.ARRAY_INDEX`). Hai lượt `g.int` = hai lời gọi HAL
                // cho một mảng; chấp nhận vì cổng `wanted(id)` chỉ đọc khi datum ĐANG HIỆN, và gộp lại sẽ phải
                // dựng một đường "đọc một lần trả nhiều datum" mà hôm nay chỉ có đúng một chủ.
                pm25OutsideUgm3 = g.int("pm25_outside", c.pm25OutsideUgm3),
                pm25Online = g.bool("pm25_online", c.pm25Online),
                cabinTempC = g.int("cabin_temp", c.cabinTempC),
                outsideTempC = g.int("ext_temp", c.outsideTempC),
                acOn = g.bool("ac_on", c.acOn),
                fanLevel = g.int("ac_wind", c.fanLevel),
                recircOn = g.bool("ac_cycle", c.recircOn),
                anionOn = g.bool("anion_state", c.anionOn),
                setTempC = g.int("inside_temp", c.setTempC),
                coolantTempC = g.int("coolant_temp", c.coolantTempC),
                tempUnit = g.str("temp_unit", c.tempUnit),
                // H1 · T2 — năm getter [ĐO xe 2026-09-16]. Ba mục giữ mã THÔ (`seat*Raw`, `acModeRaw`): phép đổi
                // (thang mức · đảo AUTO) làm ở chỗ HIỂN THỊ/chỗ đọc của nút, đúng MỘT lần — xem KDoc ở `CarStatus`.
                // ⚠ Cả năm dòng phải truyền giá trị CŨ làm `prev`: [Gate.read] phân biệt *"ô không hiện"* (trả prev)
                // với *"đọc không ra"* (trả null = ⚠). Truyền `null` rồi tự `?: cũ` ở ngoài là **xoá mất** phân biệt
                // ấy — datum nguội sẽ đóng băng con số cuối thay vì hiện "—".
                seatVentRaw = g.int("seat_vent_state", c.seatVentRaw),
                seatHeatRaw = g.int("seat_heat_state", c.seatHeatRaw),
                defrostFrontOn = g.bool("defrost_front_state", c.defrostFrontOn),
                defrostRearOn = g.bool("defrost_rear_state", c.defrostRearOn),
                acModeRaw = g.int("ac_mode_auto", c.acModeRaw),
                acWindAutoRaw = g.int("ac_wind_auto", c.acWindAutoRaw),   // 1.85 — chỉ báo gió auto (0 = AUTO)
            ),
            tyres = CarStatus.Tyres(
                pFlKpa = g.dbl("tyre_p_fl", t.pFlKpa),
                pFrKpa = g.dbl("tyre_p_fr", t.pFrKpa),
                pRlKpa = g.dbl("tyre_p_rl", t.pRlKpa),
                pRrKpa = g.dbl("tyre_p_rr", t.pRrKpa),
                tFlC = g.int("tyre_t_fl", t.tFlC),
                tFrC = g.int("tyre_t_fr", t.tFrC),
                tRlC = g.int("tyre_t_rl", t.tRlC),
                tRrC = g.int("tyre_t_rr", t.tRrC),
            ),
            body = CarStatus.Body(
                windowLfPct = g.int("window_lf", b.windowLfPct),
                windowRfPct = g.int("window_rf", b.windowRfPct),
                windowLrPct = g.int("window_lr", b.windowLrPct),
                windowRrPct = g.int("window_rr", b.windowRrPct),
                doorLfOpen = g.bool("door_lf", b.doorLfOpen),
                doorRfOpen = g.bool("door_rf", b.doorRfOpen),
                doorLrOpen = g.bool("door_lr", b.doorLrOpen),
                doorRrOpen = g.bool("door_rr", b.doorRrOpen),
                tailgateOpen = g.bool("tailgate_status", b.tailgateOpen),
                sunroofPct = g.int("sunroof_pos", b.sunroofPct),
                sunshadePct = g.int("sunshade_pct", b.sunshadePct),
                mirrorFolded = g.bool("mirror_fold", b.mirrorFolded),
                powerLevel = g.int("power_level", b.powerLevel),
                vehicleType = g.str("vehicle_type", b.vehicleType),
                tailgatePct = g.int("tailgate_position", b.tailgatePct),
                sunroofOpen = g.bool("sunroof_state", b.sunroofOpen),
                wiperOn = g.bool("wiper_state", b.wiperOn),
                emergencyAlarm = g.bool("emergency_alarm", b.emergencyAlarm),
            ),
            lights = CarStatus.Lights(
                lowBeam = g.bool("light_low_beam", l.lowBeam),
                highBeam = g.bool("light_high_beam", l.highBeam),
                frontFog = g.bool("light_front_fog", l.frontFog),
                drl = g.bool("light_drl", l.drl),
                headlightMode = g.int("headlight_feedback", l.headlightMode),
                ambientOn = g.bool("ambient_enabled", l.ambientOn),
                ambientColorIndex = g.int("ambient_front_color", l.ambientColorIndex),
                ambientBrightness = g.int("ambient_front_brightness", l.ambientBrightness),
                rearFog = g.bool("light_rear_fog", l.rearFog),
                leftTurn = g.bool("light_left_turn", l.leftTurn),
                rightTurn = g.bool("light_right_turn", l.rightTurn),
                sideLight = g.bool("light_side", l.sideLight),
                ambientRearColorIndex = g.int("ambient_rear_color", l.ambientRearColorIndex),
                ambientRearBrightness = g.int("ambient_rear_brightness", l.ambientRearBrightness),
            ),
            identity = CarStatus.Identity(
                vin = g.str("vin", i.vin),
                engineCode = g.str("engine_code", i.engineCode),
                oilLevelPct = g.int("oil_level", i.oilLevelPct),
                gpsLat = g.dbl("gps_lat", i.gpsLat),
                gpsLon = g.dbl("gps_lon", i.gpsLon),
                engineCoolantLevel = g.int("engine_coolant_level", i.engineCoolantLevel),
                gpsElevation = g.dbl("gps_elevation", i.gpsElevation),
                gpsHeading = g.dbl("gps_heading", i.gpsHeading),
            ),
            // A9 — âm lượng Android (`AudioManager`), nhịp CHẬM: nó chỉ đổi khi người ta bấm, và đọc nó không tốn
            // một lời gọi HAL nào nên không cần nhịp nhanh.
            infotainment = CarStatus.Infotainment(
                mediaVolume = g.int("media_vol", prev.infotainment.mediaVolume),
            ),
            // ═══ Giá trị THẬT cho các Ô ĐIỀU KHIỂN đang hiện (2026-09-17 · owner "không realtime") ══════════
            // Đọc mỗi nút qua CHÍNH [HalBindingTable.readState] (readKey → readInt → transform) nên phép biến đổi
            // (thang mức ghế · đảo AUTO) sống một chỗ. Chỉ các nút đang hiện có đường đọc ([controlDemand]) — nhịp
            // CHẬM, ≤ số ô điều khiển trên màn, trong ngân sách K1. `null` (đọc không ra) ⇒ loại khỏi map ⇒ ô lùi
            // về mức RAM (không bịa). Đây là đường đọc TƯỜNG MINH theo nhu cầu, không đi qua [Gate] (Gate lọc field
            // của cụm; nút đã được [controlDemand] chọn sẵn nên không cần lọc lần hai).
            controls = readControls(prev.controls),
        )
    }

    /**
     * Giá trị THẬT của các nút đang hiện, giữ giá trị CŨ cho nút không còn trong [controlDemand] (một nhịp giao
     * thời không nên xoá về "—"). Nút đọc ra `null` (off-car / getter chưa provision) ⇒ **loại khỏi map** để ô lùi
     * về mức RAM thay vì hiện số bịa. Đọc qua [HalBindingTable.readState] — cùng đường mà nút ± dùng ở [ControlTileFactory].
     */
    private fun readControls(prev: Map<String, Int>): Map<String, Int> {
        val want = controlDemand()
        if (want.isEmpty()) return prev
        val out = HashMap<String, Int>(prev)   // giữ nút cũ (ngoài nhu cầu lượt này) — tránh nháy "—" khi giao thời
        for (id in want) {
            val v = runCatching { table.readState(id) }.getOrNull()
            if (v != null) out[id] = v   // đọc không ra ⇒ GIỮ giá trị cũ nếu có, không ghi đè bằng bịa
        }
        return out
    }
}
