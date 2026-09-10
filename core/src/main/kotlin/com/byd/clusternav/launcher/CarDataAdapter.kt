package com.byd.clusternav.launcher

/**
 * ADAPTER ĐỌC XE (W1b) — nối [CarDataPort] (6 method cũ, GIỮ cho widget hiện có) + [CarStatusReader] (build
 * [CarStatus] đầy đủ theo 2 nhịp poll của [CarStatusRepository]). Mọi giá trị đọc qua [HalBindingTable] theo `id`
 * của [TelemetryRegistry]; off-car → null ⇒ UI "—" (R3/OQ1: KHÔNG demo).
 *
 * Map `id` → field [CarStatus] theo §2 handoff (id ↔ tên field gần khớp). Per-index (kính/lốp/đèn) suy từ hậu tố
 * id trong [HalBindingTable]. KHÔNG gate an toàn.
 */
class CarDataAdapter(private val table: HalBindingTable) : CarDataPort, CarStatusReader {

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

    // ── NHỊP NHANH (~1s): tốc độ / động lực / công suất / cảnh báo ADAS ─────────────────────────────────
    override fun readFast(prev: CarStatus): CarStatus = prev.copy(
        drivetrain = CarStatus.Drivetrain(
            speedKmh = table.readInt("speed"),
            accelPct = table.readInt("accel_pct"),
            brakePct = table.readInt("brake_pct"),
            motorFrontRpm = table.readInt("motor_front_rpm"),
            steeringDeg = table.readInt("steering_deg"),
            slopeDeg = table.readInt("slope_deg"),
            gear = table.readString("gear"),
            opMode = table.readString("op_mode"),
            energyMode = table.readString("energy_mode"),
            motorRearRpm = table.readInt("motor_rear_rpm"),
            motorFrontTorqueNm = table.readInt("motor_front_torque"),
            engineRpm = table.readInt("engine_rpm"),
            wheelSpeedKmh = table.readInt("wheel_speed"),
            driftMode = table.readBool("drift_mode"),
        ),
        energy = prev.energy.copy(motorPowerKw = table.readInt("motor_power")),
        safety = prev.safety.copy(
            speedLimitWarning = table.readBool("speed_limit_warning"),
            bsdLeftLevel = table.readInt("bsd_fl_alarm"),
            bsdRightLevel = table.readInt("bsd_fr_alarm"),
        ),
    )

    // ── NHỊP CHẬM (~10s): pin/tầm/sạc · khí hậu · lốp · thân xe · đèn · an toàn(bền) · danh tính ─────────
    override fun readSlow(prev: CarStatus): CarStatus = prev.copy(
        energy = prev.energy.copy(   // GIỮ motorPowerKw của nhịp nhanh
            soc = table.readInt("soc"),
            evRangeKm = table.readInt("ev_range_km"),
            fuelRangeKm = table.readInt("fuel_range_km"),
            odometerKm = table.readInt("odometer"),
            isCharging = table.readBool("is_charging"),
            chargePowerKw = table.readDouble("charge_power"),
            chargingPct = table.readInt("charging_pct"),
            chargingEtaMin = table.readInt("charging_eta_min"),
            chargedKwh = table.readDouble("charging_capacity_kwh"),
            battTempC = table.readInt("batt_temp"),
            sohPct = table.readInt("soh_oem"),
            targetSoc = table.readInt("target_soc"),
            fuelPct = table.readInt("fuel_pct"),
            evMileageKm = table.readInt("ev_mileage_km"),
            tripKm = table.readDouble("trip_km"),
            tripHours = table.readDouble("trip_hours"),
            tripKwh = table.readDouble("trip_kwh"),
            consumption50 = table.readDouble("consumption_50km"),
            chargingEtaHour = table.readInt("charging_eta_hour"),
            chargingState = table.readInt("charging_state"),
            chargerWorkState = table.readInt("charger_work_state"),
            battRangeBodyworkKm = table.readInt("batt_range_bodywork"),
            cellTempHighC = table.readInt("cell_temp_high"),
            cellTempLowC = table.readInt("cell_temp_low"),
            cellTempAvgC = table.readInt("cell_temp_avg"),
            cellVHigh = table.readDouble("cell_v_high"),
            cellVLow = table.readDouble("cell_v_low"),
        ),
        climate = CarStatus.Climate(
            pm25Level = table.readInt("pm25_level"),
            pm25ValueUgm3 = table.readInt("pm25_value"),
            pm25Online = table.readBool("pm25_online"),
            cabinTempC = table.readInt("cabin_temp"),
            outsideTempC = table.readInt("ext_temp"),
            acOn = table.readBool("ac_on"),
            fanLevel = table.readInt("ac_wind"),
            recircOn = table.readBool("ac_cycle"),
            anionOn = table.readBool("anion_state"),
            setTempC = table.readInt("inside_temp"),
            coolantTempC = table.readInt("coolant_temp"),
            tempUnit = table.readString("temp_unit"),
        ),
        tyres = CarStatus.Tyres(
            pFlKpa = table.readDouble("tyre_p_fl"),
            pFrKpa = table.readDouble("tyre_p_fr"),
            pRlKpa = table.readDouble("tyre_p_rl"),
            pRrKpa = table.readDouble("tyre_p_rr"),
            tFlC = table.readInt("tyre_t_fl"),
            tFrC = table.readInt("tyre_t_fr"),
            tRlC = table.readInt("tyre_t_rl"),
            tRrC = table.readInt("tyre_t_rr"),
        ),
        body = CarStatus.Body(
            windowLfPct = table.readInt("window_lf"),
            windowRfPct = table.readInt("window_rf"),
            windowLrPct = table.readInt("window_lr"),
            windowRrPct = table.readInt("window_rr"),
            doorLfOpen = table.readBool("door_lf"),
            doorRfOpen = table.readBool("door_rf"),
            doorLrOpen = table.readBool("door_lr"),
            doorRrOpen = table.readBool("door_rr"),
            tailgateOpen = table.readBool("tailgate_status"),
            sunroofPct = table.readInt("sunroof_pos"),
            sunshadePct = table.readInt("sunshade_pct"),
            mirrorFolded = table.readBool("mirror_fold"),
            powerLevel = table.readInt("power_level"),
            vehicleType = table.readString("vehicle_type"),
            tailgatePct = table.readInt("tailgate_position"),
            sunroofOpen = table.readBool("sunroof_state"),
            wiperOn = table.readBool("wiper_state"),
            emergencyAlarm = table.readBool("emergency_alarm"),
        ),
        lights = CarStatus.Lights(
            lowBeam = table.readBool("light_low_beam"),
            highBeam = table.readBool("light_high_beam"),
            frontFog = table.readBool("light_front_fog"),
            drl = table.readBool("light_drl"),
            headlightMode = table.readInt("headlight_feedback"),
            ambientOn = table.readBool("ambient_enabled"),
            ambientColorIndex = table.readInt("ambient_front_color"),
            ambientBrightness = table.readInt("ambient_front_brightness"),
            rearFog = table.readBool("light_rear_fog"),
            leftTurn = table.readBool("light_left_turn"),
            rightTurn = table.readBool("light_right_turn"),
            sideLight = table.readBool("light_side"),
            ambientRearColorIndex = table.readInt("ambient_rear_color"),
            ambientRearBrightness = table.readInt("ambient_rear_brightness"),
        ),
        safety = prev.safety.copy(   // GIỮ cảnh báo ADAS (bsd/speedLimitWarning) của nhịp nhanh
            seatbeltDriver = table.readBool("seatbelt_driver"),
            seatbeltPassenger = table.readBool("seatbelt_passenger"),
            childPresence = table.readBool("child_presence"),
            radarZones = table.readIntList("radar_zones"),
            espOn = table.readBool("esp_state"),
            mcuStatus = table.readInt("mcu_status"),
            volt12v = table.readDouble("volt_12v"),
            omsDriver = table.readBool("oms_driver"),
            omsPassenger = table.readBool("oms_passenger"),
            lcaLeft = table.readInt("lca_left"),
            lcaRight = table.readInt("lca_right"),
            rctaLeft = table.readInt("rcta_left"),
            rctaRight = table.readInt("rcta_right"),
            dowLeft = table.readInt("dow_left"),
            dowRight = table.readInt("dow_right"),
            radarVolume = table.readInt("radar_volume"),
            volt12vLevel = table.readInt("volt_12v_level"),
        ),
        identity = CarStatus.Identity(
            vin = table.readString("vin"),
            keyState = table.readString("key_bluetooth"),
            engineCode = table.readString("engine_code"),
            oilLevelPct = table.readInt("oil_level"),
            gpsLat = table.readDouble("gps_lat"),
            gpsLon = table.readDouble("gps_lon"),
            engineCoolantLevel = table.readInt("engine_coolant_level"),
            gpsElevation = table.readDouble("gps_elevation"),
            gpsHeading = table.readDouble("gps_heading"),
        ),
    )
}
