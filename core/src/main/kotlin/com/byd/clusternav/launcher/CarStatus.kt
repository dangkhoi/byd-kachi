package com.byd.clusternav.launcher

/**
 * SNAPSHOT bất biến trạng thái xe cho UI (registry-driven). MỌI field **nullable** — `null` = chưa đọc / không có
 * trên trim / off-car ⇒ UI hiện **"—"** (spec R3, OQ1: KHÔNG demo). Gom theo 8 domain của telemetry catalog §A;
 * Stage 2 (`CarStatusRepository` ở :app) build snapshot này từ [TelemetryRegistry] + [HalBindingTable] rồi phát qua
 * `StateFlow<CarStatus>`.
 *
 * Thuần Kotlin (:core) — mọi thay đổi là một [CarStatus] mới (copy-based, hợp UDF một chiều).
 */
data class CarStatus(
    val energy: Energy = Energy(),
    val drivetrain: Drivetrain = Drivetrain(),
    val climate: Climate = Climate(),
    val tyres: Tyres = Tyres(),
    val body: Body = Body(),
    val lights: Lights = Lights(),
    val safety: Safety = Safety(),
    val identity: Identity = Identity(),
) {
    /** A1 — năng lượng / sạc / pin. */
    data class Energy(
        val soc: Int? = null,
        val evRangeKm: Int? = null,
        val fuelRangeKm: Int? = null,
        val odometerKm: Int? = null,
        val motorPowerKw: Int? = null,
        val isCharging: Boolean? = null,
        val chargePowerKw: Double? = null,
        val chargingPct: Int? = null,
        val chargingEtaMin: Int? = null,
        val chargedKwh: Double? = null,
        val battTempC: Int? = null,
        val sohPct: Int? = null,
        val targetSoc: Int? = null,
        val fuelPct: Int? = null,
        val evMileageKm: Int? = null,
        val tripKm: Double? = null,
        val tripHours: Double? = null,
        val tripKwh: Double? = null,
        val consumption50: Double? = null,
        val chargingEtaHour: Int? = null,
        val chargingState: Int? = null,
        val chargerWorkState: Int? = null,
        val battRangeBodyworkKm: Int? = null,
        val cellTempHighC: Int? = null,
        val cellTempLowC: Int? = null,
        val cellTempAvgC: Int? = null,
        val cellVHigh: Double? = null,
        val cellVLow: Double? = null,
    )

    /** A2 — động lực / tốc độ. */
    data class Drivetrain(
        val speedKmh: Int? = null,
        val accelPct: Int? = null,
        val brakePct: Int? = null,
        val motorFrontRpm: Int? = null,
        val steeringDeg: Int? = null,
        val slopeDeg: Int? = null,
        val gear: String? = null,
        val opMode: String? = null,
        val energyMode: String? = null,
        val motorRearRpm: Int? = null,
        val motorFrontTorqueNm: Int? = null,
        val engineRpm: Int? = null,
        val wheelSpeedKmh: Int? = null,
        val driftMode: Boolean? = null,
    )

    /** A3 — khí hậu / không khí. */
    data class Climate(
        val pm25Level: Int? = null,
        val pm25ValueUgm3: Int? = null,
        val pm25Online: Boolean? = null,
        val cabinTempC: Int? = null,
        val outsideTempC: Int? = null,
        val acOn: Boolean? = null,
        val fanLevel: Int? = null,
        val recircOn: Boolean? = null,
        val anionOn: Boolean? = null,
        val setTempC: Int? = null,
        val coolantTempC: Int? = null,
        val tempUnit: String? = null,
    )

    /** A4 — lốp (áp suất kPa + nhiệt °C). */
    data class Tyres(
        val pFlKpa: Double? = null,
        val pFrKpa: Double? = null,
        val pRlKpa: Double? = null,
        val pRrKpa: Double? = null,
        val tFlC: Int? = null,
        val tFrC: Int? = null,
        val tRlC: Int? = null,
        val tRrC: Int? = null,
    )

    /** A5 — thân xe / cửa / kính. Kính = % mở (0..100); cửa/cốp = mở?(Boolean). */
    data class Body(
        val windowLfPct: Int? = null,
        val windowRfPct: Int? = null,
        val windowLrPct: Int? = null,
        val windowRrPct: Int? = null,
        val doorLfOpen: Boolean? = null,
        val doorRfOpen: Boolean? = null,
        val doorLrOpen: Boolean? = null,
        val doorRrOpen: Boolean? = null,
        val tailgateOpen: Boolean? = null,
        val sunroofPct: Int? = null,
        val sunshadePct: Int? = null,
        val mirrorFolded: Boolean? = null,
        val powerLevel: Int? = null,
        val vehicleType: String? = null,
        val tailgatePct: Int? = null,
        val sunroofOpen: Boolean? = null,
        val wiperOn: Boolean? = null,
        val emergencyAlarm: Boolean? = null,
    )

    /** A6 — đèn. */
    data class Lights(
        val lowBeam: Boolean? = null,
        val highBeam: Boolean? = null,
        val frontFog: Boolean? = null,
        val drl: Boolean? = null,
        val headlightMode: Int? = null,
        val ambientOn: Boolean? = null,
        val ambientColorIndex: Int? = null,
        val ambientBrightness: Int? = null,
        val rearFog: Boolean? = null,
        val leftTurn: Boolean? = null,
        val rightTurn: Boolean? = null,
        val sideLight: Boolean? = null,
        val ambientRearColorIndex: Int? = null,
        val ambientRearBrightness: Int? = null,
    )

    /** A7 — an toàn / ADAS / occupancy. */
    data class Safety(
        val seatbeltDriver: Boolean? = null,
        val seatbeltPassenger: Boolean? = null,
        val childPresence: Boolean? = null,
        val speedLimitWarning: Boolean? = null,
        val bsdLeftLevel: Int? = null,
        val bsdRightLevel: Int? = null,
        /** 8 vùng cảm biến đỗ (0=an toàn…4=đỏ); null = chưa đọc. */
        val radarZones: List<Int>? = null,
        val espOn: Boolean? = null,
        val mcuStatus: Int? = null,
        val volt12v: Double? = null,
        val omsDriver: Boolean? = null,
        val omsPassenger: Boolean? = null,
        val lcaLeft: Int? = null,
        val lcaRight: Int? = null,
        val rctaLeft: Int? = null,
        val rctaRight: Int? = null,
        val dowLeft: Int? = null,
        val dowRight: Int? = null,
        val radarVolume: Int? = null,
        val volt12vLevel: Int? = null,
    )

    /** A8 — danh tính / khoá / máy. */
    data class Identity(
        val vin: String? = null,
        val keyState: String? = null,
        val engineCode: String? = null,
        val oilLevelPct: Int? = null,
        val gpsLat: Double? = null,
        val gpsLon: Double? = null,
        val engineCoolantLevel: Int? = null,
        val gpsElevation: Double? = null,
        val gpsHeading: Double? = null,
    )
}
