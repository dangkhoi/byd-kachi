package com.byd.clusternav.launcher

/**
 * ═══ 3.1.6 · HAL-EVENTS — getter ↔ feature-id: datum nào nhận được bằng SỰ KIỆN thay vì poll (thuần dữ liệu) ═══════════════
 *
 * Owner 15/10: *"toàn bộ các kéo interval thì xem có event push không, nếu có thì mới tối ưu nhiều được"*. Chẩn đoán + bảng đầy
 * đủ: `docs/diagnostics/hal-events-body-seat-2026-10-15.md`.
 *
 * ## Vì sao một bảng getter → hằng là đủ (và đúng)
 * [ĐO nguồn fw 2606] mỗi getter dưới đây có thân đúng một dạng: `enforce(…_GET_PERM)` rồi `return super.get(mDeviceType, HẰNG)`
 * (hoặc `getDouble`), không đổi thang, không lọc — trừ ngoại lệ ghi ở [Feat.valid]. Còn `AbsBYDAutoDevice.onPostEvent` phát
 * `IBYDAutoEvent(type, HẰNG, value)` cho listener xin đúng HẰNG đó (`AbsBYDAutoDevice.java:221-236`). Cùng tín hiệu, cùng số ⇒ giá
 * trị sự kiện thay được lượt gọi getter, đi qua CÙNG phép parse/lọc của `HalBindingTable` (không đường parse thứ hai).
 *
 * Getter có phép biến đổi (vd `getTotalMileageValue` nhân 0,1 trên CAN-FD; `getCurrentGear` ghép ba tín hiệu) KHÔNG có ở đây ⇒
 * vẫn poll, lý do ở [POLL_ONLY]. Hằng khai bằng TÊN (giá trị khác nhau theo `isCanFD`/`isToyota` — lý do ở
 * [BindingRoute.FeatureName]); app tra số lúc chạy qua [HalGateway.featureIdByName].
 *
 * ## Ngoài bảng getter
 * Datum khai thẳng feature-id ([BindingRoute.Feature]/[BindingRoute.FeatureName]) đọc qua `get(int[]{id})` — chính là giá trị thô
 * ⇒ tự map, không cần dòng nào ở đây. Datum NHANH ([CarDataDemand.FAST_IDS]: tốc độ/số/công suất) cố ý giữ poll 1 s: tín hiệu
 * đổi liên tục khi chạy, sự kiện có thể dày hơn 1 Hz ⇒ đắt hơn poll [CHƯA ĐO tần suất — doc §5].
 */
object HalEventMap {

    enum class Kind { INT, DOUBLE }

    /**
     * Một getter = một hoặc nhiều hằng. [consts] > 1 ⇔ getter trả `int[]` ghép từ các hằng theo thứ tự (`getPM2p5Value` =
     * `{VALUE_IN, VALUE_OUT}`) ⇒ chuỗi thô dựng lại đúng dạng `"[a, b]"` mà gateway trả. [valid] = dải getter tự giữ; ngoài dải
     * getter trả `RESULT_INVALID_VALUE` ⇒ ta cũng thế.
     */
    data class Feat(val consts: List<String>, val kind: Kind = Kind.INT, val valid: IntRange? = null) {
        constructor(c: String, kind: Kind = Kind.INT, valid: IntRange? = null) : this(listOf(c), kind, valid)
    }

    /** `"BYDAuto<X>Device.<getter>#<arg>"` (arg rỗng = getter 0-arg) → hằng. Nguồn: fw 2606 jadx, `file:line` theo nhóm. */
    val GETTERS: Map<String, Feat> = buildMap {
        // statistic/BYDAutoStatisticDevice.java:274 · :281 · :338 · :345 (STATISTIC_GET_PERM)
        put("BYDAutoStatisticDevice.getElecPercentageValue#", Feat("STATISTIC_ELEC_PERCENTAGE", Kind.DOUBLE))
        put("BYDAutoStatisticDevice.getElecDrivingRangeValue#", Feat("STATISTIC_ELEC_DRIVING_RANGE"))
        put("BYDAutoStatisticDevice.getFuelDrivingRangeValue#", Feat("STATISTIC_FUEL_DRIVING_RANGE"))
        put("BYDAutoStatisticDevice.getFuelPercentageValue#", Feat("STATISTIC_FUEL_PERCENTAGE"))
        // instrument/BYDAutoInstrumentDevice.java:1348 · :1355 · :1724 · :2569 (INSTRUMENT_GET_PERM)
        put("BYDAutoInstrumentDevice.getCurrentJourneyDriveMileage#", Feat("INSTRUMENT_2IN1_CURRENT_JOURNEY_DRIVE_MILEAGE", Kind.DOUBLE))
        put("BYDAutoInstrumentDevice.getCurrentJourneyDriveTime#", Feat("INSTRUMENT_2IN1_CURRENT_JOURNEY_DRIVE_TIME", Kind.DOUBLE))
        put("BYDAutoInstrumentDevice.getLast50KmPowerConsume#", Feat("INSTRUMENT_DD_LAST_50KM_POWER_CONSUME", Kind.DOUBLE))
        put("BYDAutoInstrumentDevice.getOutCarTemperature#", Feat("INSTRUMENT_DD_OUT_TEMP"))
        // pm2p5/BYDAutoPM2p5Device.java:82 · :103 · :110 · :117 (PM2P5_GET_PERM) — level/value là int[2] {IN, OUT}
        put("BYDAutoPM2p5Device.getPM2p5Level#", Feat(listOf("PM2P5_LEVEL_IN", "PM2P5_LEVEL_OUT")))
        put("BYDAutoPM2p5Device.getPM2p5Value#", Feat(listOf("PM2P5_VALUE_IN", "PM2P5_VALUE_OUT")))
        put("BYDAutoPM2p5Device.getPM2p5OnlineState#", Feat("PM2P5_ONLINE_STATE"))
        put("BYDAutoPM2p5Device.getPM2p5AnionState#", Feat("PM25_ANION_STATE"))
        // ac/BYDAutoAcDevice.java:278 · :285 · :299 · :385 · :434 · :441 · :768 · :775 (AC_GET_PERM)
        put("BYDAutoAcDevice.getTemprature#1", Feat("AC_TEMP_MAIN"))
        put("BYDAutoAcDevice.getTemprature#2", Feat("AC_TEMP_DEPUTY"))
        put("BYDAutoAcDevice.getAcStartState#", Feat("AC_POWER_STATE"))
        put("BYDAutoAcDevice.getAcWindLevel#", Feat("AC_WIND_LEVEL"))
        put("BYDAutoAcDevice.getAcCycleMode#", Feat("AC_CYCLE_MODE"))
        put("BYDAutoAcDevice.getTemperatureUnit#", Feat("AC_TEMPERATURE_UNIT"))
        put("BYDAutoAcDevice.getAcDefrostState#1", Feat("AC_DEFROST_FRONT_STATE"))
        put("BYDAutoAcDevice.getAcDefrostState#2", Feat("AC_DEFROST_REAR_STATE"))
        put("BYDAutoAcDevice.getAcControlMode#", Feat("AC_CTRL_MODE"))
        put("BYDAutoAcDevice.getAcWindLevelManualSign#", Feat("AC_WINDLEVEL_MANUAL_SIGN"))
        // setting/BYDAutoSettingDevice.java:1702 · :1738 (SETTING_GET_PERM) — seatID 1 lái · 2 phụ
        put("BYDAutoSettingDevice.getSeatVentilatingState#1", Feat("SET_DRIVER_SEAT_VENTILATING_STATE"))
        put("BYDAutoSettingDevice.getSeatVentilatingState#2", Feat("SET_PASSENGER_SEAT_VENTILATING_STATE"))
        put("BYDAutoSettingDevice.getSeatHeatingState#1", Feat("SET_DRIVER_SEAT_HEATING_STATE"))
        put("BYDAutoSettingDevice.getSeatHeatingState#2", Feat("SET_PASSENGER_SEAT_HEATING_STATE"))
        // tyre/BYDAutoTyreDevice.java:112 · :163 · :183 · :227 (TYRE_GET_PERM). Áp suất: getter tự trả INVALID ngoài 0..4094 (:199-203).
        val wheels = listOf("LEFT_FRONT", "RIGHT_FRONT", "LEFT_REAR", "RIGHT_REAR")   // area 1..4
        wheels.forEachIndexed { i, w ->
            put("BYDAutoTyreDevice.getTyrePressureValue#${i + 1}", Feat("TYRE_PRESSURE_VALUE_$w", valid = 0..4094))
            put("BYDAutoTyreDevice.getTyrePressureState#${i + 1}", Feat("TYRE_PRESSURE_STATE_$w"))
            put("BYDAutoTyreDevice.getTyreAirLeakState#${i + 1}", Feat("TYRE_AIR_LEAK_STATE_$w"))
        }
        put("BYDAutoTyreDevice.getTyreSystemState#", Feat("TYRE_SYSTEM_STATE"))
        // bodywork/BYDAutoBodyworkDevice.java:452 · :695-725 · :776 · :854 · :880-908 (BODYWORK_GET_PERM)
        listOf(
            "BODYWORK_WINDOW_LEFT_FRONT_PERCENT", "BODYWORK_WINDOW_RIGHT_FRONT_PERCENT", "BODYWORK_WINDOW_LEFT_REAR_PERCENT",
            "BODYWORK_WINDOW_RIGHT_REAR_PERCENT", "BODYWORK_MOON_ROOF_OPEN_PERCENT", "BODYWORK_SUNSHADE_PANEL_PERCENT",
        ).forEachIndexed { i, c -> put("BYDAutoBodyworkDevice.getWindowOpenPercent#${i + 1}", Feat(c)) }
        listOf(
            "BODYWORK_LEFT_HAND_FRONT_DOOR", "BODYWORK_RIGHT_HAND_FRONT_DOOR", "BODYWORK_LEFT_HAND_REAR_DOOR",
            "BODYWORK_RIGHT_HAND_REAR_DOOR", "BODYWORK_HOOD", "BODYWORK_LUGGAGE_DOOR",
        ).forEachIndexed { i, c -> put("BYDAutoBodyworkDevice.getDoorState#${i + 1}", Feat(c)) }
        put("BYDAutoBodyworkDevice.getSunroofState#", Feat("BODYWORK_SUNROOF_STATE"))
        put("BYDAutoBodyworkDevice.getPowerLevel#", Feat("BODYWORK_POWER_LEVEL"))
        put("BYDAutoBodyworkDevice.getAlarmState#", Feat("BODYWORK_ALARM_STATE"))
        // engine/BYDAutoEngineDevice.java:221 (ENGINE_GET_PERM) · ota/BYDAutoOtaDevice.java:106 (OTA_GET_PERM)
        put("BYDAutoEngineDevice.getOilLevel#", Feat("ENGINE_OIL_LEVEL"))
        put("BYDAutoOtaDevice.getBatteryVoltage#", Feat("OTA_BATTERY_VOLTAGE", Kind.DOUBLE))
    }

    /** Getter CỐ Ý giữ poll — lý do (bài kiểm ép mọi getter đang dùng phải ở [GETTERS] hoặc ở đây). */
    val POLL_ONLY: Map<String, String> = mapOf(
        "BYDAutoStatisticDevice.getTotalMileageValue" to "nhân 0,1 trên CAN-FD (statistic:511-515) — giá trị sự kiện là số thô",
        "BYDAutoGearboxDevice.getCurrentGear" to "ghép hộp số + EPB + phanh tay (gearbox:126+) — không phải một tín hiệu",
        "BYDAutoSpeedDevice.getCurrentSpeed" to "datum NHANH — giữ poll 1 s (sự kiện có thể dày hơn) [CHƯA ĐO tần suất]",
        "BYDAutoLightDevice.getLightStatus" to "đèn đã có đường sự kiện riêng (LampLive, AbsBYDAutoLightListener)",
        "BYDAutoBodyworkDevice.getType" to "hằng 1001, không đọc xe",
        "BYDAutoBodyworkDevice.getAutoVIN" to "đọc buffer, không đổi trong chuyến",
        "AudioManager.getStreamVolume" to "Android, không qua HAL BYD",
    )

    /**
     * Hằng có bằng chứng XE NÀY đẩy sự kiện ([ĐO logcat xe 14–15/10: `postEvent device_type … event_type =…`]) ⇒ tin ngay khi
     * đăng ký OK. Hằng khác chỉ được dùng thay poll SAU sự kiện đầu tiên của chính nó (fail-safe: id im lặng không bao giờ làm số
     * cũ đi — xem [HalEventCache.hit]).
     */
    val PUSH_PROVEN: Set<String> = setOf(
        "BODYWORK_LEFT_HAND_FRONT_DOOR", "BODYWORK_RIGHT_HAND_FRONT_DOOR", "BODYWORK_LEFT_HAND_REAR_DOOR",
        "BODYWORK_RIGHT_HAND_REAR_DOOR", "BODYWORK_HOOD", "BODYWORK_LUGGAGE_DOOR",   // trunk-state-read §2.2 + AVM 15/10
        "SET_DRIVER_SEAT_VENTILATING_STATE",                                        // seat-vent §12.2
    )

    /** Hằng của dấu vân tay "ECU vào lại mạng" (seat-vent §12.3): `0x0950000F` + `0x09500022` (lớp lồng `Setting`). */
    val SEAT_SIGNATURE: List<String> = listOf("Setting.SETTING_PAD_EXIT_PIN_CODE_PROCESS", "Setting.SETTING_UNLOCK_DRIVING_PASSWORD_POPUP_TYPE")

    /** Device của dấu vân tay (`BYDAutoDeviceFeaturesMap` khai hai id trong bộ của 1023 Setting). */
    const val SEAT_SIGNATURE_DEVICE = "BYDAutoSettingDevice"

    /**
     * Một datum đã phân giải: [key] = khoá lượt đọc mà gateway nhìn thấy ([readKeyNamed]/[readKeyFeature]) — hai datum cùng getter
     * cùng khoá. [ids] = feature-id thật của xe (theo thứ tự [Feat.consts]), [device] = FQN để helper đăng ký.
     */
    data class Binding(
        val datumId: String,
        val key: String,
        val device: String,
        val ids: List<Int>,
        val kind: Kind,
        val valid: IntRange?,
        val trusted: Boolean,
    )

    fun readKeyNamed(fqn: String, method: String, arg: Int?): String = "$fqn#$method#${arg ?: ""}"
    fun readKeyFeature(id: Int): String = "feature#$id"

    /**
     * [datumId] → [Binding], hoặc `null` (poll như cũ): datum nhanh · getter ngoài [GETTERS] · tên hằng không có trên xe này.
     * Tra tên/device qua [gateway] (prod: reflection của chính xe; off-car `null` ⇒ không map gì ⇒ poll).
     */
    fun bindingOf(datumId: String, gateway: HalGateway): Binding? {
        if (datumId in CarDataDemand.FAST_IDS) return null
        val path = readPathOf(datumId) ?: return null
        val spec = TelemetryRegistry.byId(path.id) ?: return null
        return when (val r = HalBindingTable.routeOf(path.key)) {
            is BindingRoute.NamedMethod -> {
                val simple = r.fqn.substringAfterLast('.')
                val feat = GETTERS["$simple.${r.method}#${path.arg ?: ""}"] ?: return null
                val ids = feat.consts.map { gateway.featureIdByName(it) ?: return null }
                Binding(datumId, readKeyNamed(r.fqn, r.method, path.arg), r.fqn, ids, feat.kind, feat.valid, feat.consts.all { it in PUSH_PROVEN })
            }
            is BindingRoute.Feature -> feature(datumId, r.id, spec, gateway)
            is BindingRoute.FeatureName -> gateway.featureIdByName(r.constName)?.let { feature(datumId, it, spec, gateway) }
            else -> null
        }
    }

    private fun feature(datumId: String, id: Int, spec: TelemetrySpec, gateway: HalGateway): Binding =
        Binding(datumId, readKeyFeature(id), gateway.deviceForFeature(id) ?: HalBindingTable.featureDeviceFor(spec), listOf(id), Kind.INT, null, false)
}
