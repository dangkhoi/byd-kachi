package com.byd.clusternav.launcher

import com.byd.clusternav.launcher.Domain.BODY
import com.byd.clusternav.launcher.Domain.CLIMATE
import com.byd.clusternav.launcher.Domain.DRIVETRAIN
import com.byd.clusternav.launcher.Domain.ENERGY
import com.byd.clusternav.launcher.Domain.IDENTITY
import com.byd.clusternav.launcher.Domain.LIGHTS
import com.byd.clusternav.launcher.Domain.SAFETY
import com.byd.clusternav.launcher.Domain.TYRES
import com.byd.clusternav.launcher.EvidenceTier.NEEDS_CAR
import com.byd.clusternav.launcher.EvidenceTier.OVERDRIVE
import com.byd.clusternav.launcher.EvidenceTier.PROVEN
import com.byd.clusternav.launcher.WidgetShape.BADGE
import com.byd.clusternav.launcher.WidgetShape.BOARD
import com.byd.clusternav.launcher.WidgetShape.CARD
import com.byd.clusternav.launcher.WidgetShape.DIAL
import com.byd.clusternav.launcher.WidgetShape.GAUGE
import com.byd.clusternav.launcher.WidgetShape.RING
import com.byd.clusternav.launcher.WidgetShape.STRIP
import com.byd.clusternav.launcher.WidgetShape.VALUE

/**
 * Một DATUM đọc được từ xe (registry-driven). Thêm 1 thông tin = thêm 1 [TelemetrySpec] vào [TelemetryRegistry.ALL].
 *
 * @property id khoá ổn định (snake_case) — dùng ở [CarCapabilities] + wire UI.
 * @property label nhãn hiển thị (VI).
 * @property unit đơn vị ("" nếu là enum/bool/chuỗi).
 * @property domain nhóm panel (KHÔNG gate — xem [Domain]).
 * @property widgetKind gợi ý hình render ([WidgetShape]). (Tên field giữ theo spec §4.1; kiểu là [WidgetShape] vì
 *   [WidgetKind] đã bị `WidgetRegistry` chiếm — xem KDoc [WidgetShape].)
 * @property tier mức bằng chứng ([EvidenceTier]) — UI badge/mờ theo đây.
 * @property bindingKey KHOÁ để Stage 2 map tới HAL. Quy ước:
 *   • named-method proven → `"SimpleDeviceClass.methodName"` (vd `"BYDAutoStatisticDevice.getElecPercentageValue"`);
 *   • feature-id Overdrive → chuỗi decimal (vd `"1246777400"`) — Stage 2 dùng `set/get(int[]{id})`;
 *   • car-setting → khoá setting (vd `"unit_temperature"`).
 *   KHÔNG rỗng (guard bởi test). Đây là FACT (tên method / id số) — không phải mã Overdrive/dashcast (MIT clean-room).
 */
data class TelemetrySpec(
    val id: String,
    val label: String,
    val unit: String,
    val domain: Domain,
    val widgetKind: WidgetShape,
    val tier: EvidenceTier,
    val bindingKey: String,
    /**
     * Nhãn NGẮN cho bề mặt hẹp (chip thanh trên, ô nhỏ). `null` ⇒ dùng [label].
     *
     * Là **tham số mặc định** để không phải sửa 123 dòng khai báo: chỉ điền cho datum nào thật sự cần. Trước đây khái
     * niệm này chỉ tồn tại ở `TyreCorner.shortLabel` (bảng lốp), tức mỗi bề mặt hẹp lại tự nghĩ cách viết tắt riêng.
     */
    val short: String? = null,
) {
    /** Nhãn để hiện ở bề mặt hẹp — luôn có giá trị, tự lùi về [label] nếu chưa khai [short]. */
    val shortLabel: String get() = short ?: label
}

/**
 * REGISTRY TELEMETRY — bản kê MỌI datum đọc được, gom theo 8 domain của catalog §A
 * (`docs/diagnostics/kachi-capability-catalog-2026-09-10.md`). Nguồn cột `bindingKey` = HAL getter / feature-id số
 * trong catalog. Tier = mức bằng chứng ĐỌC (read) của datum đó.
 */
object TelemetryRegistry {

    private fun t(
        id: String,
        label: String,
        unit: String,
        domain: Domain,
        shape: WidgetShape,
        tier: EvidenceTier,
        key: String,
        /** Nhãn NGẮN cho bề mặt hẹp (chip thanh trên). Bỏ trống ⇒ tự lùi về [label] — xem [TelemetrySpec.shortLabel]. */
        short: String? = null,
    ) = TelemetrySpec(id, label, unit, domain, shape, tier, key, short)

    val ALL: List<TelemetrySpec> = listOf(
        // ── A1. Năng lượng / sạc / pin ───────────────────────────────────────────────────────────
        t("soc", "Pin (SOC)", "%", ENERGY, RING, PROVEN, "BYDAutoStatisticDevice.getElecPercentageValue"),
        t("ev_range_km", "Tầm hoạt động EV", "km", ENERGY, VALUE, OVERDRIVE, "BYDAutoStatisticDevice.getElecDrivingRangeValue", short = "Tầm điện"),
        t("fuel_range_km", "Tầm hoạt động xăng", "km", ENERGY, VALUE, OVERDRIVE, "1246773304", short = "Tầm xăng"),
        t("fuel_pct", "Mức xăng", "%", ENERGY, VALUE, OVERDRIVE, "1246785600"),
        t("odometer", "Odo tổng", "km", ENERGY, VALUE, OVERDRIVE, "BYDAutoStatisticDevice.getTotalMileageValue"),
        t("ev_mileage_km", "Km chạy điện", "km", ENERGY, VALUE, OVERDRIVE, "1146093608"),
        t("trip_km", "Quãng đường chuyến", "km", ENERGY, VALUE, OVERDRIVE, "1246801948", short = "Quãng chuyến"),
        t("trip_hours", "Thời gian chuyến", "h", ENERGY, VALUE, OVERDRIVE, "1246801938"),
        t("trip_kwh", "Điện tiêu thụ chuyến", "kWh", ENERGY, VALUE, OVERDRIVE, "1246801976", short = "Điện chuyến"),
        t("consumption_50km", "Tiêu thụ 50km", "kWh/100km", ENERGY, VALUE, OVERDRIVE, "BYDAutoInstrumentDevice.getLast50KmPowerConsume"),
        t("motor_power", "Công suất mô-tơ", "kW", ENERGY, GAUGE, OVERDRIVE, "339738656"),
        t("is_charging", "Đang sạc", "", ENERGY, BADGE, OVERDRIVE, "BYDAutoPowerDevice.isCharging"),
        t("charge_power", "Công suất sạc", "kW", ENERGY, CARD, OVERDRIVE, "BYDAutoChargingDevice.getChargePower"),
        t("charging_pct", "Sạc %", "%", ENERGY, CARD, OVERDRIVE, "842006544"),
        t("charging_eta_hour", "Còn (giờ)", "h", ENERGY, CARD, OVERDRIVE, "842006568"),
        t("charging_eta_min", "Còn (phút)", "min", ENERGY, CARD, OVERDRIVE, "842006576"),
        t("charging_capacity_kwh", "Đã sạc phiên", "kWh", ENERGY, CARD, OVERDRIVE, "666894360"),
        t("charging_state", "Trạng thái sạc", "", ENERGY, BADGE, OVERDRIVE, "BYDAutoChargingDevice.getChargeState"),
        t("charger_work_state", "Trạng thái bộ sạc", "", ENERGY, BADGE, OVERDRIVE, "666894346"),
        t("batt_temp", "Nhiệt độ pin", "°C", ENERGY, VALUE, OVERDRIVE, "BYDAutoChargingDevice.getBatteryTemp"),
        t("cell_temp_high", "Nhiệt cell cao", "°C", ENERGY, VALUE, OVERDRIVE, "1148190752"),
        t("cell_temp_low", "Nhiệt cell thấp", "°C", ENERGY, VALUE, OVERDRIVE, "1148190736"),
        t("cell_temp_avg", "Nhiệt cell TB", "°C", ENERGY, VALUE, OVERDRIVE, "1148190776"),
        t("cell_v_high", "Áp cell cao", "V", ENERGY, VALUE, OVERDRIVE, "1147142192"),
        t("cell_v_low", "Áp cell thấp", "V", ENERGY, VALUE, OVERDRIVE, "1147142160"),
        t("soh_oem", "Sức khoẻ pin (SOH)", "%", ENERGY, CARD, OVERDRIVE, "1145045032", short = "SOH pin"),
        t("target_soc", "Mục tiêu sạc", "%", ENERGY, VALUE, NEEDS_CAR, "SET_DR_SOC_TARGET"),
        t("batt_range_bodywork", "Tầm pin (thân xe)", "km", ENERGY, VALUE, OVERDRIVE, "300941336"),

        // ── A2. Động lực / tốc độ / chuyển động ──────────────────────────────────────────────────
        t("speed", "Tốc độ", "km/h", DRIVETRAIN, DIAL, PROVEN, "BYDAutoSpeedDevice.getCurrentSpeed"),
        t("accel_pct", "Chân ga", "%", DRIVETRAIN, GAUGE, OVERDRIVE, "BYDAutoSpeedDevice.getAccelerateDeepness"),
        t("brake_pct", "Chân phanh", "%", DRIVETRAIN, GAUGE, OVERDRIVE, "BYDAutoSpeedDevice.getBrakeDeepness"),
        t("motor_front_rpm", "Vòng tua mô-tơ trước", "rpm", DRIVETRAIN, VALUE, OVERDRIVE, "1141899272", short = "Tua trước"),
        t("motor_rear_rpm", "Vòng tua mô-tơ sau", "rpm", DRIVETRAIN, VALUE, OVERDRIVE, "621805576", short = "Tua sau"),
        t("motor_front_torque", "Mô-men mô-tơ trước", "Nm", DRIVETRAIN, VALUE, OVERDRIVE, "1141899288", short = "Mô-men trước"),
        t("engine_rpm", "Vòng tua máy xăng", "rpm", DRIVETRAIN, VALUE, OVERDRIVE, "BYDAutoEngineDevice.getEngineSpeed"),
        t("steering_deg", "Góc vô-lăng", "°", DRIVETRAIN, DIAL, PROVEN, "BYDAutoBodyworkDevice.getSteeringWheelValue"),
        t("wheel_speed", "Tốc độ bánh", "km/h", DRIVETRAIN, VALUE, PROVEN, "BYDAutoSpecialDevice.getWheelSpeed"),
        t("slope_deg", "Độ dốc", "°", DRIVETRAIN, VALUE, OVERDRIVE, "573571116"),
        t("gear", "Số", "", DRIVETRAIN, BADGE, OVERDRIVE, "BYDAutoGearboxDevice.getGearboxState"),
        t("op_mode", "Chế độ lái", "", DRIVETRAIN, BADGE, OVERDRIVE, "1272971280"),
        t("energy_mode", "Chế độ năng lượng", "", DRIVETRAIN, BADGE, OVERDRIVE, "BYDAutoEnergyDevice.getEnergyWorkMode"),
        t("drift_mode", "Chế độ drift", "", DRIVETRAIN, BADGE, OVERDRIVE, "681574694"),

        // ── A3. Khí hậu / không khí ──────────────────────────────────────────────────────────────
        t("pm25_level", "Mức bụi PM2.5", "", CLIMATE, RING, PROVEN, "BYDAutoPM2p5Device.getPM2p5Level"),
        t("pm25_value", "PM2.5", "µg/m³", CLIMATE, RING, PROVEN, "BYDAutoPM2p5Device.getPM2p5Value"),
        t("pm25_online", "Cảm biến PM2.5", "", CLIMATE, BADGE, PROVEN, "BYDAutoPM2p5Device.getPM2p5OnlineState"),
        t("cabin_temp", "Nhiệt trong cabin", "°C", CLIMATE, VALUE, OVERDRIVE, "1031798832"),
        t("inside_temp", "Nhiệt cài đặt", "°C", CLIMATE, VALUE, OVERDRIVE, "BYDAutoAcDevice.getTemprature", short = "Trong xe"),
        t("ext_temp", "Nhiệt ngoài xe", "°C", CLIMATE, VALUE, OVERDRIVE, "BYDAutoInstrumentDevice.getOutCarTemperature"),
        t("coolant_temp", "Nhiệt nước làm mát", "°C", CLIMATE, VALUE, NEEDS_CAR, "BYDAutoEngineDevice.getEngineCoolantTemp"),
        t("ac_on", "Điều hoà", "", CLIMATE, BADGE, OVERDRIVE, "BYDAutoAcDevice.getAcStartState"),
        t("ac_wind", "Mức quạt gió", "", CLIMATE, VALUE, OVERDRIVE, "BYDAutoAcDevice.getWindLevel"),
        t("ac_cycle", "Chế độ lấy gió", "", CLIMATE, BADGE, OVERDRIVE, "BYDAutoAcDevice.getCycleMode"),
        t("temp_unit", "Đơn vị nhiệt", "", CLIMATE, BADGE, OVERDRIVE, "unit_temperature"),
        t("anion_state", "Ion âm", "", CLIMATE, BADGE, OVERDRIVE, "1033895958"),

        // ── A4. Lốp (TPMS) ──────────────────────────────────────────────────────────────────────
        t("tyre_p_fl", "Áp lốp trước-trái", "kPa", TYRES, BOARD, PROVEN, "BYDAutoTyreDevice.getTyrePressureLeftFront", short = "Lốp TT"),
        t("tyre_p_fr", "Áp lốp trước-phải", "kPa", TYRES, BOARD, PROVEN, "BYDAutoTyreDevice.getTyrePressureRightFront", short = "Lốp TP"),
        t("tyre_p_rl", "Áp lốp sau-trái", "kPa", TYRES, BOARD, PROVEN, "BYDAutoTyreDevice.getTyrePressureLeftRear", short = "Lốp ST"),
        t("tyre_p_rr", "Áp lốp sau-phải", "kPa", TYRES, BOARD, PROVEN, "BYDAutoTyreDevice.getTyrePressureRightRear", short = "Lốp SP"),
        t("tyre_t_fl", "Nhiệt lốp trước-trái", "°C", TYRES, BOARD, NEEDS_CAR, "1246797848", short = "Nhiệt TT"),
        t("tyre_t_fr", "Nhiệt lốp trước-phải", "°C", TYRES, BOARD, NEEDS_CAR, "1246797860", short = "Nhiệt TP"),
        t("tyre_t_rl", "Nhiệt lốp sau-trái", "°C", TYRES, BOARD, NEEDS_CAR, "1246797872", short = "Nhiệt ST"),
        t("tyre_t_rr", "Nhiệt lốp sau-phải", "°C", TYRES, BOARD, NEEDS_CAR, "1246797884", short = "Nhiệt SP"),

        // ── A5. Thân xe / cửa / kính / gương ─────────────────────────────────────────────────────
        t("window_lf", "Kính trước-trái", "%", BODY, STRIP, PROVEN, "BYDAutoBodyworkDevice.getWindowOpenPercent"),
        t("window_rf", "Kính trước-phải", "%", BODY, STRIP, PROVEN, "BYDAutoBodyworkDevice.getWindowOpenPercent"),
        t("window_lr", "Kính sau-trái", "%", BODY, STRIP, PROVEN, "BYDAutoBodyworkDevice.getWindowOpenPercent"),
        t("window_rr", "Kính sau-phải", "%", BODY, STRIP, PROVEN, "BYDAutoBodyworkDevice.getWindowOpenPercent"),
        t("door_lf", "Cửa trước-trái", "", BODY, STRIP, OVERDRIVE, "692060176"),
        t("door_rf", "Cửa trước-phải", "", BODY, STRIP, OVERDRIVE, "692060177"),
        t("door_lr", "Cửa sau-trái", "", BODY, STRIP, OVERDRIVE, "692060178"),
        t("door_rr", "Cửa sau-phải", "", BODY, STRIP, OVERDRIVE, "692060179"),
        t("tailgate_status", "Cốp sau", "", BODY, STRIP, PROVEN, "BYDAutoBodyworkDevice.getHatchDoorStatus"),
        t("tailgate_position", "Vị trí cốp", "%", BODY, VALUE, OVERDRIVE, "1074790456"),
        t("sunroof_state", "Cửa sổ trời", "", BODY, BADGE, OVERDRIVE, "BYDAutoBodyworkDevice.getSunroofState"),
        t("sunroof_pos", "Vị trí cửa sổ trời", "%", BODY, VALUE, OVERDRIVE, "BYDAutoBodyworkDevice.getSunroofPosition"),
        t("sunshade_pct", "Rèm che nắng", "%", BODY, VALUE, OVERDRIVE, "1101004816"),
        t("mirror_fold", "Gương chiếu hậu", "", BODY, BADGE, OVERDRIVE, "960495624"),
        t("wiper_state", "Gạt mưa", "", BODY, BADGE, OVERDRIVE, "1196425226"),
        t("power_level", "Nguồn xe", "", BODY, BADGE, OVERDRIVE, "BYDAutoBodyworkDevice.getPowerLevel"),
        t("vehicle_type", "Mẫu xe", "", BODY, VALUE, PROVEN, "BYDAutoBodyworkDevice.getType"),
        t("emergency_alarm", "Cảnh báo khẩn", "", BODY, BADGE, OVERDRIVE, "692060190"),

        // ── A6. Đèn ─────────────────────────────────────────────────────────────────────────────
        t("light_low_beam", "Đèn cốt", "", LIGHTS, STRIP, OVERDRIVE, "950009866"),
        t("light_high_beam", "Đèn pha", "", LIGHTS, STRIP, OVERDRIVE, "950009868"),
        t("light_front_fog", "Đèn sương mù trước", "", LIGHTS, STRIP, OVERDRIVE, "BYDAutoLightDevice.getLightStatus"),
        t("light_rear_fog", "Đèn sương mù sau", "", LIGHTS, STRIP, OVERDRIVE, "BYDAutoLightDevice.getLightStatus"),
        t("light_left_turn", "Xi-nhan trái", "", LIGHTS, STRIP, OVERDRIVE, "BYDAutoLightDevice.getLightStatus"),
        t("light_right_turn", "Xi-nhan phải", "", LIGHTS, STRIP, OVERDRIVE, "BYDAutoLightDevice.getLightStatus"),
        t("light_side", "Đèn hông", "", LIGHTS, STRIP, OVERDRIVE, "BYDAutoLightDevice.getLightStatus"),
        t("light_drl", "Đèn ban ngày", "", LIGHTS, BADGE, OVERDRIVE, "985661476"),
        t("headlight_feedback", "Chế độ đèn pha", "", LIGHTS, BADGE, OVERDRIVE, "1011875880"),
        t("ambient_enabled", "Đèn viền cabin", "", LIGHTS, BADGE, OVERDRIVE, "1060110406"),
        t("ambient_front_color", "Màu viền trước", "", LIGHTS, VALUE, OVERDRIVE, "1121976336"),
        t("ambient_rear_color", "Màu viền sau", "", LIGHTS, VALUE, OVERDRIVE, "1121976343"),
        t("ambient_front_brightness", "Độ sáng viền trước", "", LIGHTS, VALUE, OVERDRIVE, "1121976328"),
        t("ambient_rear_brightness", "Độ sáng viền sau", "", LIGHTS, VALUE, OVERDRIVE, "1121976332"),

        // ── A7. An toàn / ADAS / occupancy ──────────────────────────────────────────────────────
        t("seatbelt_driver", "Dây an toàn lái", "", SAFETY, STRIP, OVERDRIVE, "692060184"),
        t("seatbelt_passenger", "Dây an toàn phụ", "", SAFETY, STRIP, OVERDRIVE, "638582811"),
        t("oms_driver", "Nhận diện tài xế", "", SAFETY, BADGE, OVERDRIVE, "834666600"),
        t("oms_passenger", "Nhận diện ghế phụ", "", SAFETY, BADGE, OVERDRIVE, "834666605"),
        t("child_presence", "Phát hiện trẻ em", "", SAFETY, BADGE, OVERDRIVE, "376438818"),
        t("speed_limit_warning", "Cảnh báo quá tốc", "", SAFETY, BADGE, OVERDRIVE, "535834664"),
        t("bsd_fl_alarm", "Điểm mù trước-trái", "", SAFETY, STRIP, OVERDRIVE, "1098907692"),
        t("bsd_fr_alarm", "Điểm mù trước-phải", "", SAFETY, STRIP, OVERDRIVE, "1098907694"),
        t("lca_left", "Chuyển làn trái", "", SAFETY, STRIP, OVERDRIVE, "1098907664"),
        t("lca_right", "Chuyển làn phải", "", SAFETY, STRIP, OVERDRIVE, "1098907666"),
        t("rcta_left", "Cắt ngang sau trái", "", SAFETY, STRIP, OVERDRIVE, "1098907668"),
        t("rcta_right", "Cắt ngang sau phải", "", SAFETY, STRIP, OVERDRIVE, "1098907669"),
        t("dow_left", "Mở cửa cảnh báo trái", "", SAFETY, STRIP, OVERDRIVE, "1098907680", short = "Cảnh báo cửa trái"),
        t("dow_right", "Mở cửa cảnh báo phải", "", SAFETY, STRIP, OVERDRIVE, "1098907682", short = "Cảnh báo cửa phải"),
        t("radar_zones", "Cảm biến đỗ (8 vùng)", "", SAFETY, BOARD, OVERDRIVE, "BYDAutoRadarDevice.getAllRadarProbeStates", short = "Cảm biến đỗ"),
        t("radar_volume", "Âm lượng cảm biến", "", SAFETY, VALUE, OVERDRIVE, "BYDAutoRadarDevice.getRadarVolume"),
        t("esp_state", "Cân bằng điện tử (ESP)", "", SAFETY, BADGE, OVERDRIVE, "305135676", short = "ESP"),
        t("mcu_status", "Trạng thái nguồn (MCU)", "", SAFETY, BADGE, OVERDRIVE, "BYDAutoPowerDevice.getMcuStatus", short = "Nguồn MCU"),
        t("volt_12v", "Ắc-quy 12V", "V", SAFETY, VALUE, OVERDRIVE, "BYDAutoPowerDevice.getBatteryVoltage"),
        t("volt_12v_level", "Mức ắc-quy 12V", "", SAFETY, BADGE, OVERDRIVE, "BYDAutoBodyworkDevice.getBatteryVoltageLevel"),

        // ── A8. Danh tính / khoá / máy ──────────────────────────────────────────────────────────
        t("vin", "Số VIN", "", IDENTITY, VALUE, OVERDRIVE, "BYDAutoBodyworkDevice.getAutoVIN"),
        t("key_bluetooth", "Chìa Bluetooth", "", IDENTITY, BADGE, OVERDRIVE, "602931221"),
        t("engine_code", "Mã máy", "", IDENTITY, VALUE, OVERDRIVE, "BYDAutoEngineDevice.getEngineCode"),
        t("engine_coolant_level", "Mức nước làm mát", "", IDENTITY, VALUE, OVERDRIVE, "BYDAutoEngineDevice.getEngineCoolantLevel"),
        t("oil_level", "Mức dầu", "%", IDENTITY, VALUE, OVERDRIVE, "BYDAutoEngineDevice.getOilLevel"),
        t("gps_lat", "Vĩ độ", "°", IDENTITY, VALUE, NEEDS_CAR, "NaviInfo.lat"),
        t("gps_lon", "Kinh độ", "°", IDENTITY, VALUE, NEEDS_CAR, "NaviInfo.lon"),
        t("gps_elevation", "Cao độ", "m", IDENTITY, VALUE, NEEDS_CAR, "NaviInfo.elevation"),
        t("gps_heading", "Hướng", "°", IDENTITY, VALUE, NEEDS_CAR, "NaviInfo.heading"),
    )

    fun byId(id: String): TelemetrySpec? = ALL.firstOrNull { it.id == id }

    /** Datum theo domain (để dựng panel/HOME nhóm §D). */
    fun byDomain(domain: Domain): List<TelemetrySpec> = ALL.filter { it.domain == domain }

    /** Tập domain đang có datum (dùng test phủ 8 domain). */
    fun domains(): Set<Domain> = ALL.map { it.domain }.toSet()
}
