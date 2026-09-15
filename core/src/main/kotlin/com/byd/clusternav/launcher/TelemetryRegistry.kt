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
    override val label: String,
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
    /**
     * Nhãn tiếng Anh (U5 · T2) — **cùng khuôn tham số mặc định** với [short], vì cùng lý do: nó là DỮ LIỆU của dòng
     * này, không phải chữ tra từ tài nguyên Android (`:core` thuần, xem KDoc [Strings]).
     *
     * `null` ⇒ [displayLabel] lùi về [label]; `LangCoverageTest` đếm tuyệt đối 123 dòng nên bỏ trống là **đỏ off-car**.
     */
    override val labelEn: String? = null,
    /** Nhãn NGẮN tiếng Anh. `null` ⇒ [shortLabelIn] lùi về [labelEn] rồi tới [label] — xem [shortLabel]. */
    val shortEn: String? = null,
    /**
     * Ghi ĐÈ thiết bị BYDAuto cho đường **feature-id** ĐỌC — cùng khuôn [ControlDef.halDevice]. `null` ⇒ chọn theo
     * [domain] ([HalBindingTable.featureDeviceFqn]).
     *
     * [ĐO] `docs/diagnostics/hal-binding-remediation-2026-09-15.md` §C: trước đây chỉ `ControlDef` có trường này nên
     * MỌI feature-read đi theo Domain mặc định — sai cho dây an toàn (SafetyBelt 1042, không ADAS 1038), ion (PM2P5,
     * không AC), độ dốc (Sensor, không Setting), đèn viền (Setting, không Light). Các mục có getter tên thật đã được
     * chuyển sang named-method (degrade-safe hơn); trường này dành cho mục CHỈ có feature-id.
     */
    val halDevice: String? = null,
) : Localized {
    /**
     * Nhãn để hiện ở bề mặt hẹp — luôn có giá trị, tự lùi về [label] nếu chưa khai [short].
     *
     * ⚠ Giữ nguyên nghĩa **tiếng Việt** (test cũ khoá `"Lốp TT"`); bản theo ngôn ngữ là [displayShortLabel].
     */
    val shortLabel: String get() = short ?: label

    /** Nhãn ngắn theo [Strings.current] — dùng ở chip thanh trên và ô con của nhóm. */
    val displayShortLabel: String get() = shortLabelIn(Strings.current)

    /**
     * Nhãn ngắn theo một ngôn ngữ CỤ THỂ (phép đọc thuần, cho test).
     *
     * Bậc lùi có thứ tự: [shortEn] → [labelEn] → [short] → [label]. Lùi sang **nhãn đầy tiếng Anh** trước khi lùi về
     * tiếng Việt là có chủ ý: một chip đọc `"Battery health (SOH)"` hơi dài vẫn tốt hơn một chip đột ngột nói tiếng
     * Việt giữa màn tiếng Anh.
     */
    fun shortLabelIn(lang: Lang): String =
        if (lang == Lang.EN) (shortEn?.takeIf { it.isNotBlank() } ?: labelEn?.takeIf { it.isNotBlank() } ?: shortLabel)
        else shortLabel
}

/**
 * REGISTRY TELEMETRY — bản kê MỌI datum đọc được, gom theo 8 domain của catalog §A
 * (`docs/diagnostics/kachi-capability-catalog-2026-09-10.md`). Nguồn cột `bindingKey` = HAL getter / feature-id số
 * trong catalog. Tier = mức bằng chứng ĐỌC (read) của datum đó.
 */
object TelemetryRegistry {

    /**
     * Một dòng registry. **Nhãn Việt và nhãn Anh đứng cạnh nhau** (tham số 2 và 3) chứ không nhét nhãn Anh xuống
     * cuối: đọc một dòng là thấy cả hai thứ tiếng, nên bản dịch lệch nghĩa thì thấy ngay lúc đọc code — cùng lý do
     * `Lang.kt` của ClusterNav để bản dịch tại chỗ gọi thay vì trong tệp tài nguyên riêng.
     *
     * ([TelemetrySpec] thì khai `labelEn` ở CUỐI, sau [TelemetrySpec.short], để mọi chỗ dựng bên ngoài registry —
     * nếu có — không phải sửa. Hai thứ tự khác nhau vì hai mục đích khác nhau: hàm này là chỗ ĐỌC của con người,
     * lớp kia là hợp đồng với mã bên ngoài.)
     */
    private fun t(
        id: String,
        label: String,
        labelEn: String,
        unit: String,
        domain: Domain,
        shape: WidgetShape,
        tier: EvidenceTier,
        key: String,
        /** Nhãn NGẮN cho bề mặt hẹp (chip thanh trên). Bỏ trống ⇒ tự lùi về [label] — xem [TelemetrySpec.shortLabel]. */
        short: String? = null,
        /** Nhãn NGẮN tiếng Anh. Bỏ trống ⇒ lùi về [labelEn] — xem [TelemetrySpec.shortLabelIn]. */
        shortEn: String? = null,
        /** Ghi đè thiết bị cho feature-read — xem [TelemetrySpec.halDevice]. */
        halDevice: String? = null,
    ) = TelemetrySpec(id, label, unit, domain, shape, tier, key, short, labelEn, shortEn, halDevice)

    val ALL: List<TelemetrySpec> = listOf(
        // ── A1. Năng lượng / sạc / pin ───────────────────────────────────────────────────────────
        // [ĐO] Bản vá binding 2026-09-15 (`docs/diagnostics/hal-binding-remediation-2026-09-15.md` §Năng lượng): các
        // mục dưới đổi từ feature-id/tên method SAI sang getter THẬT trong stub `../jadx-tmap/.../bydauto/` (file:line
        // ghi cạnh từng dòng). `// NEEDS-ONCAR:` = chưa có nguồn off-car, giữ nguyên, chốt bằng T-BRIDGE `hal get`.
        t("soc", "Pin (SOC)", "Battery (SOC)", "%", ENERGY, RING, PROVEN, "BYDAutoStatisticDevice.getElecPercentageValue"),
        // Sentinel INVALID=1000 / DEFAULT=1023 (BYDAutoStatisticDevice.java:56-57) → HalBindingTable.INVALID_VALUES chặn.
        t("ev_range_km", "Tầm hoạt động EV", "EV range", "km", ENERGY, VALUE, OVERDRIVE, "BYDAutoStatisticDevice.getElecDrivingRangeValue", short = "Tầm điện"),
        // BYDAutoStatisticDevice.java:196 (cũ: feature 1246773304 = atom tầm xăng, đọc vỡ theo §A).
        t("fuel_range_km", "Tầm hoạt động xăng", "Fuel range", "km", ENERGY, VALUE, OVERDRIVE, "BYDAutoStatisticDevice.getFuelDrivingRangeValue", short = "Tầm xăng"),
        // BYDAutoStatisticDevice.java:200.
        t("fuel_pct", "Mức xăng", "Fuel level", "%", ENERGY, VALUE, OVERDRIVE, "BYDAutoStatisticDevice.getFuelPercentageValue"),
        t("odometer", "Odo tổng", "Odometer", "km", ENERGY, VALUE, OVERDRIVE, "BYDAutoStatisticDevice.getTotalMileageValue"),
        // BYDAutoStatisticDevice.java:167.
        t("ev_mileage_km", "Km chạy điện", "EV distance driven", "km", ENERGY, VALUE, OVERDRIVE, "BYDAutoStatisticDevice.getEVMileageValue", shortEn = "EV distance"),
        // NEEDS-ONCAR: trip_km/hours/kwh — 2 ứng viên named, chưa chốt scale phút-vs-giờ, Wh-vs-kWh.
        t("trip_km", "Quãng đường chuyến", "Trip distance", "km", ENERGY, VALUE, OVERDRIVE, "1246801948", short = "Quãng chuyến", shortEn = "Trip dist."),
        t("trip_hours", "Thời gian chuyến", "Trip time", "h", ENERGY, VALUE, OVERDRIVE, "1246801938"),
        t("trip_kwh", "Điện tiêu thụ chuyến", "Trip energy used", "kWh", ENERGY, VALUE, OVERDRIVE, "1246801976", short = "Điện chuyến", shortEn = "Trip energy"),
        t("consumption_50km", "Tiêu thụ 50km", "Consumption last 50 km", "kWh/100km", ENERGY, VALUE, OVERDRIVE, "BYDAutoInstrumentDevice.getLast50KmPowerConsume", shortEn = "Use 50 km"),
        // NEEDS-ONCAR: motor_power — feature-id mô-tơ bị zero-hoá trong decompile, cần id thật.
        t("motor_power", "Công suất mô-tơ", "Motor power", "kW", ENERGY, GAUGE, OVERDRIVE, "339738656"),
        // BYDAutoChargingDevice.java:252 — READY1/START2/FINISH3/TERMINATE4; "đang sạc" = ==2 (HalBindingTable.BOOL_WHEN_EQUALS).
        // Cũ `BYDAutoPowerDevice.isCharging` KHÔNG tồn tại.
        t("is_charging", "Đang sạc", "Charging", "", ENERGY, BADGE, OVERDRIVE, "BYDAutoChargingDevice.getChargerWorkState"),
        // BYDAutoInstrumentDevice.java:1096 (double) — cũ gọi sai device Charging.
        t("charge_power", "Công suất sạc", "Charge power", "kW", ENERGY, CARD, OVERDRIVE, "BYDAutoInstrumentDevice.getChargePower"),
        // BYDAutoInstrumentDevice.java:1092.
        t("charging_pct", "Sạc %", "Charge %", "%", ENERGY, CARD, OVERDRIVE, "BYDAutoInstrumentDevice.getChargePercent"),
        // BYDAutoInstrumentDevice.java:1100 `int[] getChargeRestTime()` → [0]=giờ, [1]=phút (HalBindingTable.ARRAY_INDEX).
        t("charging_eta_hour", "Còn (giờ)", "Remaining (h)", "h", ENERGY, CARD, OVERDRIVE, "BYDAutoInstrumentDevice.getChargeRestTime"),
        t("charging_eta_min", "Còn (phút)", "Remaining (min)", "min", ENERGY, CARD, OVERDRIVE, "BYDAutoInstrumentDevice.getChargeRestTime"),
        // BYDAutoChargingDevice.java:260 (double).
        t("charging_capacity_kwh", "Đã sạc phiên", "Charged this session", "kWh", ENERGY, CARD, OVERDRIVE, "BYDAutoChargingDevice.getChargingCapacity", shortEn = "Session kWh"),
        // BYDAutoChargingDevice.java:252 — `getChargeState` KHÔNG tồn tại; cả hai badge đọc cùng getter.
        t("charging_state", "Trạng thái sạc", "Charge state", "", ENERGY, BADGE, OVERDRIVE, "BYDAutoChargingDevice.getChargerWorkState"),
        t("charger_work_state", "Trạng thái bộ sạc", "Charger state", "", ENERGY, BADGE, OVERDRIVE, "BYDAutoChargingDevice.getChargerWorkState"),
        // NEEDS-ONCAR: batt_temp / cell_temp_* / soh_oem — feature-id zero-hoá trong decompile, không có named.
        t("batt_temp", "Nhiệt độ pin", "Battery temp", "°C", ENERGY, VALUE, OVERDRIVE, "BYDAutoChargingDevice.getBatteryTemp"),
        t("cell_temp_high", "Nhiệt cell cao", "Cell temp high", "°C", ENERGY, VALUE, OVERDRIVE, "1148190752"),
        t("cell_temp_low", "Nhiệt cell thấp", "Cell temp low", "°C", ENERGY, VALUE, OVERDRIVE, "1148190736"),
        t("cell_temp_avg", "Nhiệt cell TB", "Cell temp avg", "°C", ENERGY, VALUE, OVERDRIVE, "1148190776"),
        // [ĐO] feature 1147142192/1147142160 thực ra = atom TẦM XĂNG (`EVENT_STAT_FUEL_RANGE`, openbyd VehicleBridge.java:89-90),
        // KHÔNG phải áp cell ⇒ gỡ id để không đọc ra số sai; khoá UPPER_SNAKE → BindingRoute.None ("—") tới khi có id thật.
        t("cell_v_high", "Áp cell cao", "Cell voltage high", "V", ENERGY, VALUE, NEEDS_CAR, "UNMAPPED_CELL_VOLTAGE_HIGH", shortEn = "Cell V high"),
        t("cell_v_low", "Áp cell thấp", "Cell voltage low", "V", ENERGY, VALUE, NEEDS_CAR, "UNMAPPED_CELL_VOLTAGE_LOW", shortEn = "Cell V low"),
        t("soh_oem", "Sức khoẻ pin (SOH)", "Battery health (SOH)", "%", ENERGY, CARD, OVERDRIVE, "1145045032", short = "SOH pin", shortEn = "SOH"),
        t("target_soc", "Mục tiêu sạc", "Charge target", "%", ENERGY, VALUE, NEEDS_CAR, "SET_DR_SOC_TARGET"),
        // [ĐO] feature 300941336 = BODYWORK_STEERING_WHEEL_SPEED (BYDAutoFeatureIds.java:685), KHÔNG phải tầm pin ⇒ gỡ.
        t("batt_range_bodywork", "Tầm pin (thân xe)", "Battery range (body)", "km", ENERGY, VALUE, NEEDS_CAR, "UNMAPPED_BATT_RANGE_BODYWORK", shortEn = "Batt range"),

        // ── A2. Động lực / tốc độ / chuyển động ──────────────────────────────────────────────────
        t("speed", "Tốc độ", "Speed", "km/h", DRIVETRAIN, DIAL, PROVEN, "BYDAutoSpeedDevice.getCurrentSpeed"),
        t("accel_pct", "Chân ga", "Accelerator pedal", "%", DRIVETRAIN, GAUGE, OVERDRIVE, "BYDAutoSpeedDevice.getAccelerateDeepness", shortEn = "Accelerator"),
        t("brake_pct", "Chân phanh", "Brake pedal", "%", DRIVETRAIN, GAUGE, OVERDRIVE, "BYDAutoSpeedDevice.getBrakeDeepness"),
        // NEEDS-ONCAR: motor_*_rpm / motor_front_torque — feature-id mô-tơ kéo zero-hoá trong decompile, cần id thật.
        t("motor_front_rpm", "Vòng tua mô-tơ trước", "Front motor rpm", "rpm", DRIVETRAIN, VALUE, OVERDRIVE, "1141899272", short = "Tua trước", shortEn = "Front rpm"),
        t("motor_rear_rpm", "Vòng tua mô-tơ sau", "Rear motor rpm", "rpm", DRIVETRAIN, VALUE, OVERDRIVE, "621805576", short = "Tua sau", shortEn = "Rear rpm"),
        t("motor_front_torque", "Mô-men mô-tơ trước", "Front motor torque", "Nm", DRIVETRAIN, VALUE, OVERDRIVE, "1141899288", short = "Mô-men trước", shortEn = "Front torque"),
        t("engine_rpm", "Vòng tua máy xăng", "Engine rpm", "rpm", DRIVETRAIN, VALUE, OVERDRIVE, "BYDAutoEngineDevice.getEngineSpeed"),
        // `double getSteeringWheelValue(int)` BYDAutoBodyworkDevice.java:505 — cần selector BODYWORK_CMD_STEERING_WHEEL_ANGEL=1
        // (:178); HalBindingTable.readArg cấp. Trước thiếu arg ⇒ reflection không khớp chữ ký 1-arg ⇒ "—".
        t("steering_deg", "Góc vô-lăng", "Steering angle", "°", DRIVETRAIN, DIAL, PROVEN, "BYDAutoBodyworkDevice.getSteeringWheelValue"),
        // [ĐO] `byte[] getWheelSpeed()` **0-arg** (BYDAutoSpecialDevice.java:59) — catalog cũ ghi 1-arg là SAI; readArg null.
        // Mảng 4 bánh → gateway trả "[a, b, c, d]" → coerceInt lấy phần tử ĐẦU (bánh 1).
        t("wheel_speed", "Tốc độ bánh", "Wheel speed", "km/h", DRIVETRAIN, VALUE, PROVEN, "BYDAutoSpecialDevice.getWheelSpeed"),
        // BYDAutoSensorDevice.java:75 (feature 573571116 = SENSOR_AUTO_SLOPE đúng id nhưng Domain.DRIVETRAIN route
        // sang Setting ⇒ sai device; named-method không phụ thuộc device-map).
        t("slope_deg", "Độ dốc", "Gradient", "°", DRIVETRAIN, VALUE, OVERDRIVE, "BYDAutoSensorDevice.getSlope"),
        // BYDAutoGearboxDevice.java:109 — GEAR_P=3/R=1/N=0/D=2/INVALID=255 (:79-83). Cũ `getGearboxState` chỉ ON/OFF.
        t("gear", "Số", "Gear", "", DRIVETRAIN, BADGE, OVERDRIVE, "BYDAutoGearboxDevice.getCurrentGear"),
        // BYDAutoEnergyDevice.java:130 — ECONOMY1/SPORT2/NORMAL3/SNOW4/MUDDY5/SAND6 (:24-33).
        t("op_mode", "Chế độ lái", "Drive mode", "", DRIVETRAIN, BADGE, OVERDRIVE, "BYDAutoEnergyDevice.getOperationMode"),
        // BYDAutoEnergyDevice.java:112 — STOP0/EV1/FORCE_EV2/HEV3/FUEL4/KEEP5 (:18-23). Cũ `getEnergyWorkMode` không tồn tại.
        t("energy_mode", "Chế độ năng lượng", "Energy mode", "", DRIVETRAIN, BADGE, OVERDRIVE, "BYDAutoEnergyDevice.getEnergyMode"),
        // NEEDS-ONCAR: drift_mode — nghiêng UNAVAILABLE-TRIM (Sealion 6 DM-i không drift).
        t("drift_mode", "Chế độ drift", "Drift mode", "", DRIVETRAIN, BADGE, OVERDRIVE, "681574694"),

        // ── A3. Khí hậu / không khí ──────────────────────────────────────────────────────────────
        // ⚠⚠ [U6 · ĐO ảnh 2026-09-12] BA Ô GẦN TRÙNG TÊN — "PM2.5 level" · "PM2.5" · "PM2.5 sensor" nằm cạnh nhau
        // trong lưới, khác nhau đúng một chữ ở CUỐI (chỗ bị cắt trước nhất trong ô 2 dòng). Ba mã này đo ba thứ
        // KHÁC HẲN nhau, nên tên phải nói ra điều đó ở ĐẦU chuỗi:
        //   • getPM2p5Level      → thang mức 1..6 = "bụi đang ở mức nào"              ⇒ MỨC BỤI MỊN
        //   • getPM2p5Value      → trị số µg/m³                                       ⇒ BỤI MỊN PM2.5 (giữ ký hiệu)
        //   • getPM2p5OnlineState→ cảm biến còn sống không (nói về THIẾT BỊ)          ⇒ CẢM BIẾN BỤI MỊN
        // Ký hiệu "PM2.5" không mất: nó ở đúng chỗ nó là ký hiệu (trị số) và ở nhãn ngắn của chip.
        // ⚠ KHÔNG đặt tên là "Chất lượng không khí"/"Air quality": widget dựng tay `w_pm25` đã mang đúng tên đó
        // (`LangCoverageTest.ban dich khong sinh ra nhan trung MOI` bắt được ngay lần chạy đầu — cái ô THẺ và cái
        // ô SỐ THÔ là hai thứ người dùng đặt được cạnh nhau).
        t("pm25_level", "Mức bụi mịn", "Dust level", "", CLIMATE, RING, PROVEN, "BYDAutoPM2p5Device.getPM2p5Level"),
        // ⚠ Nhãn ngắn Anh TRÙNG nhãn ngắn Việt — cố ý: PM2.5 là ký hiệu ngành, dịch thành câu dài sẽ sai chuẩn
        // (spec §6 OQ2). Có tên trong danh sách cho phép của `LangCoverageTest`.
        t("pm25_value", "Bụi mịn PM2.5", "Fine dust PM2.5", "µg/m³", CLIMATE, RING, PROVEN, "BYDAutoPM2p5Device.getPM2p5Value", short = "PM2.5", shortEn = "PM2.5"),
        t("pm25_online", "Cảm biến bụi mịn", "Fine dust sensor", "", CLIMATE, BADGE, PROVEN, "BYDAutoPM2p5Device.getPM2p5OnlineState", short = "Cảm biến", shortEn = "Sensor"),
        // Feature-read, device AC đúng theo domain; sống lại khi §A (get 2-arg) được vá ở :app. NEEDS-ONCAR: scale.
        t("cabin_temp", "Nhiệt trong cabin", "Cabin temp", "°C", CLIMATE, VALUE, OVERDRIVE, "1031798832"),
        t("inside_temp", "Nhiệt cài đặt", "Set temp", "°C", CLIMATE, VALUE, OVERDRIVE, "BYDAutoAcDevice.getTemprature", short = "Trong xe", shortEn = "In car"),
        t("ext_temp", "Nhiệt ngoài xe", "Outside temp", "°C", CLIMATE, VALUE, OVERDRIVE, "BYDAutoInstrumentDevice.getOutCarTemperature"),
        // NEEDS-ONCAR: coolant_temp — HAL không lộ °C numeric (chỉ mức/đèn cảnh báo); probe feature-id.
        t("coolant_temp", "Nhiệt nước làm mát", "Coolant temp", "°C", CLIMATE, VALUE, NEEDS_CAR, "BYDAutoEngineDevice.getEngineCoolantTemp"),
        t("ac_on", "Điều hoà", "Air conditioning", "", CLIMATE, BADGE, OVERDRIVE, "BYDAutoAcDevice.getAcStartState", shortEn = "A/C"),
        // BYDAutoAcDevice.java:298 (0–7). Cũ `getWindLevel` không tồn tại.
        t("ac_wind", "Mức quạt gió", "Fan level", "", CLIMATE, VALUE, OVERDRIVE, "BYDAutoAcDevice.getAcWindLevel"),
        // BYDAutoAcDevice.java:218 — INLOOP=1 (trong) / OUTLOOP=0 (ngoài) (:24-25). Cũ `getCycleMode` không tồn tại.
        t("ac_cycle", "Chế độ lấy gió", "Recirculation mode", "", CLIMATE, BADGE, OVERDRIVE, "BYDAutoAcDevice.getAcCycleMode", shortEn = "Air intake"),
        // BYDAutoAcDevice.java:400 — °C=1 / °F=0 (:76-77). Cũ route car-setting `unit_temperature` luôn null (settingGet chưa wire).
        t("temp_unit", "Đơn vị nhiệt", "Temperature unit", "", CLIMATE, BADGE, OVERDRIVE, "BYDAutoAcDevice.getTemperatureUnit", shortEn = "Temp unit"),
        // BYDAutoPM2p5Device.java:72 — cũ feature 1033895958 route theo Domain.CLIMATE → AC (sai device, thật PM2P5).
        t("anion_state", "Ion âm", "Negative ions", "", CLIMATE, BADGE, OVERDRIVE, "BYDAutoPM2p5Device.getPM2p5AnionState"),

        // ── A4. Lốp (TPMS) ──────────────────────────────────────────────────────────────────────
        // [ĐO] `int getTyrePressureValue(int area)` BYDAutoTyreDevice.java:115, area LEFT_FRONT=1/RIGHT_FRONT=2/LEFT_REAR=3/
        // RIGHT_REAR=4 (:27-30) — HalBindingTable.readArg cấp. Cũ `getTyrePressureLeftFront…` KHÔNG tồn tại.
        t("tyre_p_fl", "Áp lốp trước-trái", "Tyre pressure front-left", "kPa", TYRES, BOARD, PROVEN, "BYDAutoTyreDevice.getTyrePressureValue", short = "Lốp TT", shortEn = "Tyre FL"),
        t("tyre_p_fr", "Áp lốp trước-phải", "Tyre pressure front-right", "kPa", TYRES, BOARD, PROVEN, "BYDAutoTyreDevice.getTyrePressureValue", short = "Lốp TP", shortEn = "Tyre FR"),
        t("tyre_p_rl", "Áp lốp sau-trái", "Tyre pressure rear-left", "kPa", TYRES, BOARD, PROVEN, "BYDAutoTyreDevice.getTyrePressureValue", short = "Lốp ST", shortEn = "Tyre RL"),
        t("tyre_p_rr", "Áp lốp sau-phải", "Tyre pressure rear-right", "kPa", TYRES, BOARD, PROVEN, "BYDAutoTyreDevice.getTyrePressureValue", short = "Lốp SP", shortEn = "Tyre RR"),
        // NEEDS-ONCAR: tyre_t_* — HAL chỉ có `getTyreTemperatureState` enum 0-arg, không có value per-corner.
        t("tyre_t_fl", "Nhiệt lốp trước-trái", "Tyre temp front-left", "°C", TYRES, BOARD, NEEDS_CAR, "1246797848", short = "Nhiệt TT", shortEn = "Temp FL"),
        t("tyre_t_fr", "Nhiệt lốp trước-phải", "Tyre temp front-right", "°C", TYRES, BOARD, NEEDS_CAR, "1246797860", short = "Nhiệt TP", shortEn = "Temp FR"),
        t("tyre_t_rl", "Nhiệt lốp sau-trái", "Tyre temp rear-left", "°C", TYRES, BOARD, NEEDS_CAR, "1246797872", short = "Nhiệt ST", shortEn = "Temp RL"),
        t("tyre_t_rr", "Nhiệt lốp sau-phải", "Tyre temp rear-right", "°C", TYRES, BOARD, NEEDS_CAR, "1246797884", short = "Nhiệt SP", shortEn = "Temp RR"),

        // ── A5. Thân xe / cửa / kính / gương ─────────────────────────────────────────────────────
        // ⚠ Bốn dòng này có `short` (VI) từ 2026-09-12: trước đó chỉ có `shortEn`, nên ô con nhóm *Kính* hiện
        // `"Window FL"` ở bản Anh mà `"Kính trước-trái"` (nhãn ĐẦY) ở bản Việt — [ĐO] ảnh máy ảo: bản Việt bị cắt
        // `"Kính trước-p…"` ngay khi ô hẹp lại. Viết tắt theo ĐÚNG quy ước bảng lốp (`"Lốp TT"`), và **khớp** với
        // `short` của bốn nút kính (`win_*`) để hai hàng trong cùng một ô nhóm gọi một cái kính bằng một tên.
        t("window_lf", "Kính trước-trái", "Window front-left", "%", BODY, STRIP, PROVEN, "BYDAutoBodyworkDevice.getWindowOpenPercent", short = "Kính TT", shortEn = "Window FL"),
        t("window_rf", "Kính trước-phải", "Window front-right", "%", BODY, STRIP, PROVEN, "BYDAutoBodyworkDevice.getWindowOpenPercent", short = "Kính TP", shortEn = "Window FR"),
        t("window_lr", "Kính sau-trái", "Window rear-left", "%", BODY, STRIP, PROVEN, "BYDAutoBodyworkDevice.getWindowOpenPercent", short = "Kính ST", shortEn = "Window RL"),
        t("window_rr", "Kính sau-phải", "Window rear-right", "%", BODY, STRIP, PROVEN, "BYDAutoBodyworkDevice.getWindowOpenPercent", short = "Kính SP", shortEn = "Window RR"),
        // [ĐO] `int getDoorState(int area)` BYDAutoBodyworkDevice.java:450 — CLOSED0/OPEN1/255; area LEFT_FRONT=1/
        // RIGHT_FRONT=2/LEFT_REAR=3/RIGHT_REAR=4 (:172-176) — HalBindingTable.readArg cấp (cũ feature-id 692060176…9 đọc vỡ §A).
        t("door_lf", "Cửa trước-trái", "Door front-left", "", BODY, STRIP, OVERDRIVE, "BYDAutoBodyworkDevice.getDoorState", shortEn = "Door FL"),
        t("door_rf", "Cửa trước-phải", "Door front-right", "", BODY, STRIP, OVERDRIVE, "BYDAutoBodyworkDevice.getDoorState", shortEn = "Door FR"),
        t("door_lr", "Cửa sau-trái", "Door rear-left", "", BODY, STRIP, OVERDRIVE, "BYDAutoBodyworkDevice.getDoorState", shortEn = "Door RL"),
        t("door_rr", "Cửa sau-phải", "Door rear-right", "", BODY, STRIP, OVERDRIVE, "BYDAutoBodyworkDevice.getDoorState", shortEn = "Door RR"),
        t("tailgate_status", "Cốp sau", "Tailgate", "", BODY, STRIP, PROVEN, "BYDAutoBodyworkDevice.getHatchDoorStatus"),
        // NEEDS-ONCAR: tailgate_position / wiper_state — feature-id vô danh, không có method.
        t("tailgate_position", "Vị trí cốp", "Tailgate position", "%", BODY, VALUE, OVERDRIVE, "1074790456", shortEn = "Tailgate pos"),
        t("sunroof_state", "Cửa sổ trời", "Sunroof", "", BODY, BADGE, OVERDRIVE, "BYDAutoBodyworkDevice.getSunroofState"),
        t("sunroof_pos", "Vị trí cửa sổ trời", "Sunroof position", "%", BODY, VALUE, OVERDRIVE, "BYDAutoBodyworkDevice.getSunroofPosition", shortEn = "Sunroof pos"),
        t("sunshade_pct", "Rèm che nắng", "Sunshade", "%", BODY, VALUE, OVERDRIVE, "1101004816"),
        t("mirror_fold", "Gương chiếu hậu", "Door mirrors", "", BODY, BADGE, OVERDRIVE, "960495624"),
        t("wiper_state", "Gạt mưa", "Wipers", "", BODY, BADGE, OVERDRIVE, "1196425226"),
        t("power_level", "Nguồn xe", "Vehicle power", "", BODY, BADGE, OVERDRIVE, "BYDAutoBodyworkDevice.getPowerLevel"),
        t("vehicle_type", "Mẫu xe", "Vehicle model", "", BODY, VALUE, PROVEN, "BYDAutoBodyworkDevice.getType"),
        // BYDAutoBodyworkDevice.java:396 — ALARM_STATE_OFF=0/ON=1 (:156-157).
        t("emergency_alarm", "Cảnh báo khẩn", "Emergency alarm", "", BODY, BADGE, OVERDRIVE, "BYDAutoBodyworkDevice.getAlarmState"),

        // ── A6. Đèn ─────────────────────────────────────────────────────────────────────────────
        // [ĐO] cùng getter `getLightStatus(type)` với 5 đèn anh em bên dưới; LIGHT_LOW_BEAM=2 (BYDAutoLightDevice.java:56),
        // LIGHT_HIGH_BEAM=3 (:49) — HalBindingTable.readArg cấp. Cũ feature 950009866/8 đọc vỡ (§A).
        t("light_low_beam", "Đèn cốt", "Low beam", "", LIGHTS, STRIP, OVERDRIVE, "BYDAutoLightDevice.getLightStatus"),
        t("light_high_beam", "Đèn pha", "High beam", "", LIGHTS, STRIP, OVERDRIVE, "BYDAutoLightDevice.getLightStatus"),
        t("light_front_fog", "Đèn sương mù trước", "Front fog lights", "", LIGHTS, STRIP, OVERDRIVE, "BYDAutoLightDevice.getLightStatus", shortEn = "Fog front"),
        t("light_rear_fog", "Đèn sương mù sau", "Rear fog lights", "", LIGHTS, STRIP, OVERDRIVE, "BYDAutoLightDevice.getLightStatus", shortEn = "Fog rear"),
        t("light_left_turn", "Xi-nhan trái", "Left indicator", "", LIGHTS, STRIP, OVERDRIVE, "BYDAutoLightDevice.getLightStatus"),
        t("light_right_turn", "Xi-nhan phải", "Right indicator", "", LIGHTS, STRIP, OVERDRIVE, "BYDAutoLightDevice.getLightStatus"),
        t("light_side", "Đèn hông", "Side lights", "", LIGHTS, STRIP, OVERDRIVE, "BYDAutoLightDevice.getLightStatus"),
        // DRL = ký hiệu ngành (daytime running lights), giữ nguyên viết tắt — spec §6 OQ2.
        t("light_drl", "Đèn ban ngày", "Daytime lights (DRL)", "", LIGHTS, BADGE, OVERDRIVE, "985661476", shortEn = "DRL"),
        // NEEDS-ONCAR: headlight_feedback + 8 mục ambient — device nghi SETTING(1023), scale nghi 0–100, màu 31.
        t("headlight_feedback", "Chế độ đèn pha", "Headlight mode", "", LIGHTS, BADGE, OVERDRIVE, "1011875880"),
        t("ambient_enabled", "Đèn viền cabin", "Cabin ambient light", "", LIGHTS, BADGE, OVERDRIVE, "1060110406", shortEn = "Ambient"),
        t("ambient_front_color", "Màu viền trước", "Ambient colour front", "", LIGHTS, VALUE, OVERDRIVE, "1121976336", shortEn = "Colour front"),
        t("ambient_rear_color", "Màu viền sau", "Ambient colour rear", "", LIGHTS, VALUE, OVERDRIVE, "1121976343", shortEn = "Colour rear"),
        t("ambient_front_brightness", "Độ sáng viền trước", "Ambient brightness front", "", LIGHTS, VALUE, OVERDRIVE, "1121976328", shortEn = "Bright front"),
        t("ambient_rear_brightness", "Độ sáng viền sau", "Ambient brightness rear", "", LIGHTS, VALUE, OVERDRIVE, "1121976332", shortEn = "Bright rear"),

        // ── A7. An toàn / ADAS / occupancy ──────────────────────────────────────────────────────
        // [ĐO] `int getSafetyBeltStatus(int area)` BYDAutoSafetyBeltDevice.java:85 — UNLOCK0/LOCK1/INVALID2 (:38-40);
        // area MAIN=1/DEPUTY=2 (:16/:15) — HalBindingTable.readArg cấp. Cũ feature-id route Domain.SAFETY → ADAS(1038),
        // thật thuộc SafetyBelt(1042).
        t("seatbelt_driver", "Dây an toàn lái", "Seatbelt driver", "", SAFETY, STRIP, OVERDRIVE, "BYDAutoSafetyBeltDevice.getSafetyBeltStatus", shortEn = "Belt driver"),
        t("seatbelt_passenger", "Dây an toàn phụ", "Seatbelt passenger", "", SAFETY, STRIP, OVERDRIVE, "BYDAutoSafetyBeltDevice.getSafetyBeltStatus", shortEn = "Belt pass."),
        // NEEDS-ONCAR: oms_driver — enum PASSENGER của `getPassengerStatus` KHÔNG có ghế lái (DEPUTY=1, hàng 2 = 2/3/4,
        // BYDAutoSafetyBeltDevice.java:27-30) ⇒ chưa có arg đúng cho tài xế; giữ feature-id, chốt bằng `hal get`.
        t("oms_driver", "Nhận diện tài xế", "Driver detected", "", SAFETY, BADGE, OVERDRIVE, "834666600"),
        // [ĐO] `int getPassengerStatus(int)` BYDAutoSafetyBeltDevice.java:73 — NOBODY0/SOMEBODY1 (:32-33), ghế phụ = DEPUTY=1 (:27).
        t("oms_passenger", "Nhận diện ghế phụ", "Passenger detected", "", SAFETY, BADGE, OVERDRIVE, "BYDAutoSafetyBeltDevice.getPassengerStatus", shortEn = "Passenger"),
        // NEEDS-ONCAR: child_presence / speed_limit_warning / bsd_* / lca_* / rcta_* / dow_* — id "Overdrive" không resolve
        // trong dump; per-side không có getter (ADAS chỉ có aggregate, per-side qua listener event).
        t("child_presence", "Phát hiện trẻ em", "Child presence", "", SAFETY, BADGE, OVERDRIVE, "376438818"),
        t("speed_limit_warning", "Cảnh báo quá tốc", "Speed limit warning", "", SAFETY, BADGE, OVERDRIVE, "535834664", shortEn = "Over speed"),
        t("bsd_fl_alarm", "Điểm mù trước-trái", "Blind spot front-left", "", SAFETY, STRIP, OVERDRIVE, "1098907692", shortEn = "Blind spot L"),
        t("bsd_fr_alarm", "Điểm mù trước-phải", "Blind spot front-right", "", SAFETY, STRIP, OVERDRIVE, "1098907694", shortEn = "Blind spot R"),
        t("lca_left", "Chuyển làn trái", "Lane change left", "", SAFETY, STRIP, OVERDRIVE, "1098907664", shortEn = "Lane chg L"),
        t("lca_right", "Chuyển làn phải", "Lane change right", "", SAFETY, STRIP, OVERDRIVE, "1098907666", shortEn = "Lane chg R"),
        t("rcta_left", "Cắt ngang sau trái", "Rear cross-traffic left", "", SAFETY, STRIP, OVERDRIVE, "1098907668", shortEn = "Cross rear L"),
        t("rcta_right", "Cắt ngang sau phải", "Rear cross-traffic right", "", SAFETY, STRIP, OVERDRIVE, "1098907669", shortEn = "Cross rear R"),
        t("dow_left", "Mở cửa cảnh báo trái", "Door open warning left", "", SAFETY, STRIP, OVERDRIVE, "1098907680", short = "Cảnh báo cửa trái", shortEn = "Door warn L"),
        t("dow_right", "Mở cửa cảnh báo phải", "Door open warning right", "", SAFETY, STRIP, OVERDRIVE, "1098907682", short = "Cảnh báo cửa phải", shortEn = "Door warn R"),
        t("radar_zones", "Cảm biến đỗ (8 vùng)", "Parking sensors (8 zones)", "", SAFETY, BOARD, OVERDRIVE, "BYDAutoRadarDevice.getAllRadarProbeStates", short = "Cảm biến đỗ", shortEn = "Park sensors"),
        // NEEDS-ONCAR: radar_volume — chỉ có đường ghi, không có getter đọc.
        t("radar_volume", "Âm lượng cảm biến", "Sensor volume", "", SAFETY, VALUE, OVERDRIVE, "BYDAutoRadarDevice.getRadarVolume", short = "Âm lượng", shortEn = "Volume"),
        // ESP · MCU = ký hiệu ngành, giữ nguyên viết tắt (spec §6 OQ2). Nhãn ngắn "ESP" trùng cả hai thứ tiếng ⇒ có
        // tên trong danh sách cho phép của `LangCoverageTest`.
        t("esp_state", "Cân bằng điện tử (ESP)", "Stability control (ESP)", "", SAFETY, BADGE, OVERDRIVE, "305135676", short = "ESP", shortEn = "ESP"),
        t("mcu_status", "Trạng thái nguồn (MCU)", "Power state (MCU)", "", SAFETY, BADGE, OVERDRIVE, "BYDAutoPowerDevice.getMcuStatus", short = "Nguồn MCU", shortEn = "MCU power"),
        // [ĐO] `double getBatteryVoltage()` BYDAutoOtaDevice.java:87 — Power KHÔNG có method này (chỉ getBatteryLowVoltageState).
        t("volt_12v", "Ắc-quy 12V", "12V battery", "V", SAFETY, VALUE, OVERDRIVE, "BYDAutoOtaDevice.getBatteryVoltage"),
        t("volt_12v_level", "Mức ắc-quy 12V", "12V battery level", "", SAFETY, BADGE, OVERDRIVE, "BYDAutoBodyworkDevice.getBatteryVoltageLevel", shortEn = "12V level"),

        // ── A8. Danh tính / khoá / máy ──────────────────────────────────────────────────────────
        // VIN = ký hiệu ngành, giữ nguyên (spec §6 OQ2).
        t("vin", "Số VIN", "VIN", "", IDENTITY, VALUE, OVERDRIVE, "BYDAutoBodyworkDevice.getAutoVIN"),
        // NEEDS-ONCAR: key_bluetooth — id không resolve trong dump.
        t("key_bluetooth", "Chìa Bluetooth", "Bluetooth key", "", IDENTITY, BADGE, OVERDRIVE, "602931221"),
        // engine_code/oil_level: route đúng, [X] on-car nghi engine PHEV ngủ → chốt: nổ máy rồi `hal get`.
        t("engine_code", "Mã máy", "Engine code", "", IDENTITY, VALUE, OVERDRIVE, "BYDAutoEngineDevice.getEngineCode"),
        t("engine_coolant_level", "Mức nước làm mát", "Coolant level", "", IDENTITY, VALUE, OVERDRIVE, "BYDAutoEngineDevice.getEngineCoolantLevel"),
        t("oil_level", "Mức dầu", "Oil level", "%", IDENTITY, VALUE, OVERDRIVE, "BYDAutoEngineDevice.getOilLevel"),
        // [ĐO] GPS KHÔNG qua HAL — `BYDAutoLocationDevice` chỉ có setter (app đẩy toạ độ xuống xe); đường đúng là Android
        // `LocationManager`. BLOCKED-BY-DESIGN: quyền location đã retire (DeadReckonRetirementTest ghim manifest không xin
        // quyền location nào — an toàn sau sự cố ghim GPS toàn xe) — mở lại = quyết định owner. Giữ `NaviInfo.*` → None.
        t("gps_lat", "Vĩ độ", "Latitude", "°", IDENTITY, VALUE, NEEDS_CAR, "NaviInfo.lat"),
        t("gps_lon", "Kinh độ", "Longitude", "°", IDENTITY, VALUE, NEEDS_CAR, "NaviInfo.lon"),
        t("gps_elevation", "Cao độ", "Elevation", "m", IDENTITY, VALUE, NEEDS_CAR, "NaviInfo.elevation"),
        t("gps_heading", "Hướng", "Heading", "°", IDENTITY, VALUE, NEEDS_CAR, "NaviInfo.heading"),
    )

    fun byId(id: String): TelemetrySpec? = ALL.firstOrNull { it.id == id }

    /** Datum theo domain (để dựng panel/HOME nhóm §D). */
    fun byDomain(domain: Domain): List<TelemetrySpec> = ALL.filter { it.domain == domain }

    /** Tập domain đang có datum (dùng test phủ 8 domain). */
    fun domains(): Set<Domain> = ALL.map { it.domain }.toSet()
}
