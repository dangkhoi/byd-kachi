package com.byd.clusternav.launcher

import java.util.Locale

/**
 * VIEW MODEL bất biến cho MỘT widget telemetry — kết quả THUẦN của việc đọc [TelemetryRegistry] + [CarStatus]
 * (KHÔNG Android → test JVM). UI (`WidgetViews` ở :app) render THEO đây: shape → hình, [display] → chữ (null = "—"),
 * [needsBadge] → gắn nhãn "chưa kiểm trên xe".
 *
 * @property valueText giá trị đã format, hoặc `null` = chưa đọc / không có trên trim / off-car ⇒ UI **"—" + mờ**
 *   (spec R3, OQ1: KHÔNG bịa số).
 */
data class TelemetryView(
    val id: String,
    val label: String,
    val unit: String,
    val shape: WidgetShape,
    val tier: EvidenceTier,
    val valueText: String?,
) {
    /** Có đọc được giá trị không (off-car/null ⇒ false ⇒ view mờ). */
    val available: Boolean get() = valueText != null

    /** Có cần badge "chưa kiểm trên xe" ([EvidenceTier.OVERDRIVE]/[EvidenceTier.DASHCAST]). */
    val needsBadge: Boolean get() = tier.needsBadge

    /** Chữ giá trị hiển thị ("—" nếu chưa đọc). */
    val display: String get() = valueText ?: PLACEHOLDER

    /** Chữ giá trị + đơn vị (vd "82 %"); "—" nếu chưa đọc; bỏ đơn vị nếu rỗng. */
    fun displayWithUnit(): String = when {
        valueText == null -> PLACEHOLDER
        unit.isEmpty() -> valueText
        else -> "$valueText $unit"
    }

    companion object {
        const val PLACEHOLDER = "—"
    }
}

/**
 * ĐỌC-RA telemetry (registry-driven, THUẦN): [of] tra [TelemetryRegistry] lấy label/unit/shape/tier, đọc giá trị từ
 * [CarStatus] theo `id` rồi format. `id` không có trong registry → null. MỌI id trong registry đều có case format
 * (W1 Stage 5 đóng khe ~46 datum): binding resolve được (feature-id số / `Class.method`) → giá trị chảy khi ở trên
 * xe, off-car → null ⇒ "—"; binding KHÔNG resolve được (GPS `NaviInfo.*`, `SET_DR_SOC_TARGET`) → luôn "—" (route
 * [BindingRoute.None]) tới khi đóng grab-list §9.
 *
 * Đây là ĐIỂM DUY NHẤT map `id` → field [CarStatus] cho UI — khớp map ĐỌC ở [CarDataAdapter] (cùng danh sách id).
 */
object TelemetryReadout {

    /** [TelemetryView] cho [id] từ [status], hoặc null nếu [id] không có trong [TelemetryRegistry]. */
    fun of(id: String, status: CarStatus): TelemetryView? {
        val spec = TelemetryRegistry.byId(id) ?: return null
        // U5 · T2: nhãn theo ngôn ngữ ngay tại đây — `TelemetryView` là thứ tầng vẽ đọc, nên nếu để nhãn gốc thì
        // `:app` phải tự dịch lại (bản-sao-thứ-hai của phép chọn ngôn ngữ).
        return TelemetryView(spec.id, spec.displayLabel, spec.unit, spec.widgetKind, spec.tier, format(id, status))
    }

    /** Giá trị hiển thị đã format cho telemetry [id] từ [s]; null = chưa đọc/không có ⇒ "—". */
    private fun format(id: String, s: CarStatus): String? = when (id) {
        // ── A1. Năng lượng ──────────────────────────────────────────────────────────────
        "soc" -> s.energy.soc?.toString()
        "ev_range_km" -> s.energy.evRangeKm?.toString()
        "fuel_range_km" -> s.energy.fuelRangeKm?.toString()
        "odometer" -> s.energy.odometerKm?.toString()
        "motor_power" -> s.energy.motorPowerKw?.toString()
        "batt_temp" -> s.energy.battTempC?.toString()
        "soh_oem" -> s.energy.sohPct?.toString()
        "target_soc" -> s.energy.targetSoc?.toString()
        "fuel_pct" -> s.energy.fuelPct?.toString()
        "ev_mileage_km" -> s.energy.evMileageKm?.toString()
        "trip_km" -> s.energy.tripKm?.let { dec1(it) }
        "trip_hours" -> s.energy.tripHours?.let { dec1(it) }
        "trip_kwh" -> s.energy.tripKwh?.let { dec1(it) }
        "consumption_50km" -> s.energy.consumption50?.let { dec1(it) }
        "cell_temp_high" -> s.energy.cellTempHighC?.toString()
        "cell_temp_low" -> s.energy.cellTempLowC?.toString()
        "cell_temp_avg" -> s.energy.cellTempAvgC?.toString()
        "cell_v_high" -> s.energy.cellVHigh?.let { dec2(it) }
        "cell_v_low" -> s.energy.cellVLow?.let { dec2(it) }

        // ── A2. Động lực ────────────────────────────────────────────────────────────────
        "speed" -> s.drivetrain.speedKmh?.toString()
        "accel_pct" -> s.drivetrain.accelPct?.toString()
        "brake_pct" -> s.drivetrain.brakePct?.toString()
        "motor_front_rpm" -> s.drivetrain.motorFrontRpm?.toString()
        "steering_deg" -> s.drivetrain.steeringDeg?.toString()
        "slope_deg" -> s.drivetrain.slopeDeg?.toString()
        "gear" -> s.drivetrain.gear
        "op_mode" -> s.drivetrain.opMode
        "energy_mode" -> s.drivetrain.energyMode
        "motor_rear_rpm" -> s.drivetrain.motorRearRpm?.toString()
        "motor_front_torque" -> s.drivetrain.motorFrontTorqueNm?.toString()
        "engine_rpm" -> s.drivetrain.engineRpm?.toString()
        "wheel_speed" -> s.drivetrain.wheelSpeedKmh?.toString()

        // ── A3. Khí hậu ─────────────────────────────────────────────────────────────────
        "pm25_level" -> s.climate.pm25Level?.toString()
        "pm25_value" -> s.climate.pm25ValueUgm3?.toString()
        "pm25_online" -> s.climate.pm25Online?.let { yesNo(it) }
        "cabin_temp" -> s.climate.cabinTempC?.toString()
        "ext_temp" -> s.climate.outsideTempC?.toString()
        "ac_on" -> s.climate.acOn?.let { onOff(it) }
        "ac_wind" -> s.climate.fanLevel?.toString()
        "ac_cycle" -> s.climate.recircOn?.let { if (it) Strings.t("Trong", "Recirc") else Strings.t("Ngoài", "Fresh") }
        "anion_state" -> s.climate.anionOn?.let { onOff(it) }
        "inside_temp" -> s.climate.setTempC?.toString()
        "coolant_temp" -> s.climate.coolantTempC?.toString()
        "temp_unit" -> s.climate.tempUnit
        // H1 · T2 — ghế đọc ra MÃ mức của khung, phải đổi qua [ControlLevels] mới thành chữ người ta hiểu. Mã NGOÀI
        // thang ⇒ null ⇒ ô hiện "—": thà nói *"chưa đọc được"* còn hơn làm tròn thành "Mức 1" (thang mới đứng trên
        // MỘT điểm đo — TODO điểm thứ hai ghi ở [ControlLevels]).
        "seat_vent_state" -> s.climate.seatVentRaw?.let { levelText("seatc", it) }
        "seat_heat_state" -> s.climate.seatHeatRaw?.let { levelText("seath", it) }
        "defrost_front_state" -> s.climate.defrostFrontOn?.let { onOff(it) }
        "defrost_rear_state" -> s.climate.defrostRearOn?.let { onOff(it) }
        // 0 = AUTO (`AC_CTRLMODE_AUTO`) — đảo Ở ĐÂY, và chỉ ở đây, cho bề mặt ĐỌC; nút `ac_auto` có đường riêng
        // ([ControlDef.readInverted]) nên không chỗ nào đảo hai lần.
        "ac_mode_auto" -> s.climate.acModeRaw?.let { if (it == 0) "AUTO" else Strings.t("Chỉnh tay", "Manual") }

        // ── A9. Giải trí ────────────────────────────────────────────────────────────────
        "media_vol" -> s.infotainment.mediaVolume?.toString()

        // ── A4. Lốp (kPa thô → hiển thị nguyên kPa) ───────────────────────────────────────
        "tyre_p_fl" -> s.tyres.pFlKpa?.let { dec0(it) }
        "tyre_p_fr" -> s.tyres.pFrKpa?.let { dec0(it) }
        "tyre_p_rl" -> s.tyres.pRlKpa?.let { dec0(it) }
        "tyre_p_rr" -> s.tyres.pRrKpa?.let { dec0(it) }
        "tyre_t_fl" -> s.tyres.tFlC?.toString()
        "tyre_t_fr" -> s.tyres.tFrC?.toString()
        "tyre_t_rl" -> s.tyres.tRlC?.toString()
        "tyre_t_rr" -> s.tyres.tRrC?.toString()

        // ── A5. Thân xe ─────────────────────────────────────────────────────────────────
        "window_lf" -> s.body.windowLfPct?.toString()
        "window_rf" -> s.body.windowRfPct?.toString()
        "window_lr" -> s.body.windowLrPct?.toString()
        "window_rr" -> s.body.windowRrPct?.toString()
        "door_lf" -> s.body.doorLfOpen?.let { openShut(it) }
        "door_rf" -> s.body.doorRfOpen?.let { openShut(it) }
        "door_lr" -> s.body.doorLrOpen?.let { openShut(it) }
        "door_rr" -> s.body.doorRrOpen?.let { openShut(it) }
        "tailgate_status" -> s.body.tailgateOpen?.let { openShut(it) }
        "sunroof_pos" -> s.body.sunroofPct?.toString()
        "sunshade_pct" -> s.body.sunshadePct?.toString()
        "mirror_fold" -> s.body.mirrorFolded?.let { if (it) Strings.t("Gập", "Folded") else Strings.t("Mở", "Out") }
        "power_level" -> s.body.powerLevel?.toString()
        "vehicle_type" -> s.body.vehicleType
        "tailgate_position" -> s.body.tailgatePct?.toString()
        "sunroof_state" -> s.body.sunroofOpen?.let { openShut(it) }
        "wiper_state" -> s.body.wiperOn?.let { onOff(it) }
        "emergency_alarm" -> s.body.emergencyAlarm?.let { onOff(it) }

        // ── A6. Đèn ─────────────────────────────────────────────────────────────────────
        "light_low_beam" -> s.lights.lowBeam?.let { onOff(it) }
        "light_high_beam" -> s.lights.highBeam?.let { onOff(it) }
        "light_front_fog" -> s.lights.frontFog?.let { onOff(it) }
        "light_drl" -> s.lights.drl?.let { onOff(it) }
        "headlight_feedback" -> s.lights.headlightMode?.toString()
        "ambient_enabled" -> s.lights.ambientOn?.let { onOff(it) }
        "ambient_front_color" -> s.lights.ambientColorIndex?.toString()
        "ambient_front_brightness" -> s.lights.ambientBrightness?.toString()
        "light_rear_fog" -> s.lights.rearFog?.let { onOff(it) }
        "light_left_turn" -> s.lights.leftTurn?.let { onOff(it) }
        "light_right_turn" -> s.lights.rightTurn?.let { onOff(it) }
        "light_side" -> s.lights.sideLight?.let { onOff(it) }
        "ambient_rear_color" -> s.lights.ambientRearColorIndex?.toString()
        "ambient_rear_brightness" -> s.lights.ambientRearBrightness?.toString()

        // ── A7. Điện phụ 12V / nguồn máy (nhóm "An toàn · ADAS" đã gỡ hẳn 2026-09-16) ───
        "volt_12v" -> s.energy.volt12v?.let { dec1(it) }
        "volt_12v_level" -> s.energy.volt12vLevel?.toString()

        // ── A8. Danh tính ───────────────────────────────────────────────────────────────
        "vin" -> s.identity.vin
        "engine_code" -> s.identity.engineCode
        "oil_level" -> s.identity.oilLevelPct?.toString()
        "gps_lat" -> s.identity.gpsLat?.let { dec5(it) }
        "gps_lon" -> s.identity.gpsLon?.let { dec5(it) }
        "engine_coolant_level" -> s.identity.engineCoolantLevel?.toString()
        "gps_elevation" -> s.identity.gpsElevation?.let { dec1(it) }
        "gps_heading" -> s.identity.gpsHeading?.let { dec1(it) }

        // MỌI id trong registry đều có case ở trên (test `every id maps`). Non-resolvable (GPS/NaviInfo,
        // SET_DR_SOC_TARGET) đọc null (route None) ⇒ "—". else = phòng vệ id ngoài-registry (of() đã chặn).
        else -> null
    }?.takeIf { it.isNotBlank() }

    /**
     * ⚠ **CHỮ GIÁ TRỊ CŨNG PHẢI DỊCH** (U5 · T2): `"Bật"`/`"Mở"`/`"Có"` là thứ hiện **trong ô**, không phải nhãn.
     * Bỏ qua chúng thì màn tiếng Anh có ô ghi *"Low beam — Bật"*: nhãn đã dịch, giá trị thì không.
     *
     * Ba cặp dưới đây gọi qua [Strings.t] chứ không qua bảng dữ liệu, vì chúng là **phép quy đổi bool → chữ** dùng
     * chung cho hàng chục datum, không phải nhãn của một dòng registry nào.
     *
     * ⚠ [GroupBoard] **KHÔNG** được so chuỗi này để quyết định sắc thái (KDoc ở đó đã cấm) — nay lý do càng mạnh:
     * chuỗi đổi theo ngôn ngữ, nên so chuỗi sẽ **vỡ khi người dùng chọn English**, tức lỗi chỉ xảy ra với một nửa
     * người dùng.
     */
    private fun yesNo(b: Boolean) = if (b) Strings.t("Có", "Yes") else Strings.t("Không", "No")
    private fun onOff(b: Boolean) = if (b) Strings.t("Bật", "On") else Strings.t("Tắt", "Off")
    private fun openShut(b: Boolean) = if (b) Strings.t("Mở", "Open") else Strings.t("Đóng", "Closed")
    /**
     * Mã mức thô của khung → chữ *"Tắt" / "Mức n"*, hoặc `null` khi mã nằm NGOÀI thang của nút ấy (⇒ ô hiện "—").
     *
     * Tra bằng mã **NÚT** (`seatc`/`seath`) chứ không bằng mã datum: thang là tính chất của cái người ta bấm, và
     * [ControlLevels] đã khai đúng một chỗ cho cả đường đọc lẫn đường ghi.
     */
    private fun levelText(controlId: String, raw: Int): String? =
        ControlLevels.levelOf(controlId, raw)?.let {
            if (it == 0) Strings.t("Tắt", "Off") else Strings.t("Mức $it", "Level $it")
        }

    private fun dec0(d: Double) = Math.round(d).toString()
    private fun dec1(d: Double) = String.format(Locale.US, "%.1f", d)
    private fun dec2(d: Double) = String.format(Locale.US, "%.2f", d)
    private fun dec5(d: Double) = String.format(Locale.US, "%.5f", d)
}
