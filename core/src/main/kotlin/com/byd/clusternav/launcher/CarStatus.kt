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
    val identity: Identity = Identity(),
    /** A9 — giải trí (Android, KHÔNG qua HAL BYDAuto). Xem [Infotainment]. */
    val infotainment: Infotainment = Infotainment(),
    /**
     * ═══ Giá trị THẬT của XE cho các Ô ĐIỀU KHIỂN đang hiện (2026-09-17 · owner báo "không realtime") ══════
     *
     * `control id → giá trị hiện tại đọc từ xe`, ngữ nghĩa **y hệt** [HalBindingTable.readState]:
     *  • STEP (`temp`/`fan`/`vol`) = con số (24 · 3 · 12);
     *  • TOGGLE (`recirc`/`trunk`/`seatc`/`sunroof`/`drl`…) = **0/1** (tắt/bật);
     *  • SELECT = chỉ số lựa chọn.
     *
     * ## Vì sao là map RỜI, không nhét vào các cụm trên
     * Ô control cần con số **theo mã nút** (`temp`), trong khi các cụm ([Climate.setTempC]…) khoá theo **mã datum**
     * (`inside_temp`). Một map theo mã nút để `ControlTileFactory` tra thẳng `controls[def.id]` — không phải dựng
     * một bảng NGƯỢC datum→nút ở tầng vẽ (đúng bẫy "hai bảng lệch nhau" của repo). [CarDataAdapter] điền nó bằng
     * chính [HalBindingTable.readState] nên phép biến đổi (thang mức ghế · đảo AUTO) chỉ sống **một chỗ**.
     *
     * `emptyMap()` = chưa đọc control nào (off-car / màn không bày ô điều khiển nào có đường đọc) ⇒ ô lùi về mức
     * trong RAM ([ControlTileState]) như hành vi 1.69 — KHÔNG bịa số.
     */
    val controls: Map<String, Int> = emptyMap(),
) {
    /** A1 — năng lượng / pin (mọi ô SẠC đã gỡ ở lượt (V) 2026-09-17 — owner chấm NO). */
    data class Energy(
        val soc: Int? = null,
        val evRangeKm: Int? = null,
        val fuelRangeKm: Int? = null,
        val odometerKm: Int? = null,
        val motorPowerKw: Int? = null,
        val battTempC: Int? = null,
        val sohPct: Int? = null,
        val targetSoc: Int? = null,
        val fuelPct: Int? = null,
        val evMileageKm: Int? = null,
        val tripKm: Double? = null,
        val tripHours: Double? = null,
        val tripKwh: Double? = null,
        val consumption50: Double? = null,
        // ── Điện phụ 12V + nguồn máy — chuyển từ `Safety` sang đây 2026-09-16 khi owner gỡ toàn bộ ADAS/an toàn.
        // Ắc-quy 12V không phải hệ an toàn lái; nó là câu hỏi về NĂNG LƯỢNG. (`mcuStatus` xoá ở lượt (V).)
        val volt12v: Double? = null,
        val volt12vLevel: Int? = null,
    )

    /** A2 — động lực / tốc độ. */
    data class Drivetrain(
        val speedKmh: Int? = null,
        val gear: String? = null,
        // ⚠ 1.90 · `opMode`/`energyMode` xoá cùng hai datum `op_mode`/`energy_mode` (owner 2026-09-21 — xe thuần
        // điện; xem nhật ký ở `TelemetryRegistry`). Trường nullable không ai đọc là một cột "—" vĩnh viễn.
    )

    /** A3 — khí hậu / không khí. */
    data class Climate(
        val pm25Level: Int? = null,
        val pm25ValueUgm3: Int? = null,
        /**
         * 1.85 — bụi mịn **NGOÀI** xe (µg/m³): ô thứ HAI của `getPM2p5Value()`, cùng một lời gọi HAL với
         * [pm25ValueUgm3] (javadoc BYD: *"first = in auto, second = out of auto"*). Field riêng vì đó là hai con số
         * người dùng đặt cạnh nhau được, không phải hai cách hiển thị của một con số.
         */
        val pm25OutsideUgm3: Int? = null,
        val pm25Online: Boolean? = null,
        val cabinTempC: Int? = null,
        val outsideTempC: Int? = null,
        val acOn: Boolean? = null,
        val fanLevel: Int? = null,
        val recircOn: Boolean? = null,
        val anionOn: Boolean? = null,
        val setTempC: Int? = null,
        val tempUnit: String? = null,
        /**
         * H1 · T2 — mã mức THÔ của khung cho ghế mát/sưởi ([ĐO xe 2026-09-16] 3 ⇐ màn xe *"mức 2"*, 1 = tắt).
         *
         * Giữ **thô**, không đổi sẵn sang mức người dùng: phép đổi nằm ở [ControlLevels] (một bảng dữ liệu có TODO
         * điểm đo thứ hai). Đổi ở đây thì con số đo được biến mất khỏi `CarStatus`, và lượt đo sau không còn gì để
         * đối chiếu — đúng cái bẫy *"dữ liệu cũ thành [ĐO]"* mà CLAUDE.md §2 cấm.
         */
        val seatVentRaw: Int? = null,
        val seatHeatRaw: Int? = null,
        val defrostFrontOn: Boolean? = null,
        val defrostRearOn: Boolean? = null,
        /**
         * Mã chế độ điều hoà THÔ: `getAcControlMode()` — `AC_CTRLMODE_AUTO = 0` / `_MANUAL = 1`
         * (`ac/BYDAutoAcDevice.java:29-30`; [ĐO xe 2026-09-16] đọc ra 0 trong khi màn xe đang AUTO).
         *
         * Giữ **thô** thay vì một `Boolean` *"đang AUTO"* vì cùng lý do với [seatVentRaw], cộng một lý do nữa: phép
         * đảo phải xảy ra **đúng một lần**. Nút `ac_auto` đã khai [ControlDef.readInverted] (đường đọc của NÚT đảo
         * ở `HalBindingTable.readState`); nếu cụm này cũng cất sẵn dạng đã đảo thì bất cứ ai đọc chéo hai bề mặt
         * cũng có nguy cơ đảo lần thứ hai và ô nói ngược.
         */
        val acModeRaw: Int? = null,
        /**
         * 1.85 — dấu **chỉnh tay của mức gió** THÔ: `getAcWindLevelManualSign()` — javadoc BYD:
         * *"Auto ctrl: `AC_WINDLEVEL_MANUAL_SIGN_OFF`(0) · Manual ctrl: `_ON`(1)"* ⇒ **0 = gió đang AUTO**.
         *
         * Đây là chỉ báo của nút `ac_auto` sau khi nút ấy đổi sang *gió* auto ([ĐO xe 2026-09-20 §3/§4); [acModeRaw]
         * ở trên là câu hỏi KHÁC (chế độ điều hoà) và vẫn còn nguyên. Giữ **thô** đúng một lẽ với [acModeRaw]: phép
         * đảo chỉ được xảy ra một lần, ở đường đọc của nút.
         */
        val acWindAutoRaw: Int? = null,
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
        val powerLevel: Int? = null,
        val vehicleType: String? = null,
        val sunroofOpen: Boolean? = null,
        val emergencyAlarm: Boolean? = null,
    )

    /** A6 — đèn. */
    data class Lights(
        val lowBeam: Boolean? = null,
        val highBeam: Boolean? = null,
        val frontFog: Boolean? = null,
        val drl: Boolean? = null,
        val headlightMode: Int? = null,
        val rearFog: Boolean? = null,
        val leftTurn: Boolean? = null,
        val rightTurn: Boolean? = null,
        val sideLight: Boolean? = null,
    )

    // ⚠ A7 (`Safety`) đã **xoá hẳn** 2026-09-16 cùng toàn bộ ADAS/an toàn chủ động (owner). Đừng dựng lại nhóm này:
    // dây an toàn · nhận diện người ngồi · trẻ em · quá tốc · điểm mù · chuyển làn · cắt ngang sau · cảnh báo mở cửa ·
    // cảm biến đỗ · ESP đều KHÔNG còn trong launcher. Hai mục điện 12V đã dời sang [Energy].

    /**
     * A9 — giải trí. Cụm ĐẦU TIÊN không đến từ HAL BYDAuto: âm lượng là của Android
     * (`AudioManager.getStreamVolume(STREAM_MUSIC)` — [BindingRoute.Local]). Đứng riêng chứ không ghép vào [Climate]
     * hay [Identity] vì nguồn dữ liệu khác hẳn: nó còn đọc được khi HAL xe im lặng hoàn toàn (máy ảo, off-car).
     */
    data class Infotainment(
        val mediaVolume: Int? = null,
    )

    /** A8 — danh tính / khoá / máy. */
    data class Identity(
        val vin: String? = null,
        val oilLevelPct: Int? = null,
    )
}
