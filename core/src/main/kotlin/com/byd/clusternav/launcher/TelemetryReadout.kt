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
    /**
     * Datum **BẬT/TẮT** đang ở trạng thái nào — `true` = đang bật, `false` = đang tắt.
     *
     * Sinh ra để bề mặt hẹp (chip thanh trên) vẽ **trạng thái bằng ICON sáng/mờ** thay vì bằng chữ *"Bật"/"Tắt"*
     * (owner 2026-09-21: *"trạng thái bật/tắt phải thể hiện bằng icon active/inactive, KHÔNG bằng chữ"*). [valueText]
     * **KHÔNG đổi** — ô lớn vẫn hiện chữ; đây là một trường CỘNG THÊM, không phải một cách trình bày khác.
     *
     * ## ⚠ `null` gộp HAI ca, có chủ ý
     * `null` = *"id này không phải datum bật/tắt"* **hoặc** *"là bật/tắt nhưng chưa đọc được"* (off-car / không có
     * trên trim). Gộp vì mọi bề mặt xử hai ca ấy **y như nhau**: không có trạng thái để tô thì hiện giá trị (hoặc
     * `"—"`) ở sắc thái trung tính. Tách thành tri-state chỉ để rồi hai nhánh viết cùng một đoạn mã là mời một cái
     * `when` thứ hai đi lệch về sau.
     *
     * ⚠ **Đây là đường DUY NHẤT** để tầng vẽ biết một datum là bật/tắt. Tuyệt đối KHÔNG so [valueText] với
     * `"Bật"`/`"Tắt"`: chuỗi đó đổi theo ngôn ngữ (xem cảnh báo ở [TelemetryReadout.onOff]), nên so chuỗi sẽ vỡ
     * **im lặng** với đúng một nửa người dùng — cùng cái bẫy mà [GroupBoard] đã bị cấm rơi vào.
     */
    val onOff: Boolean? = null,
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
        // Datum bật/tắt đi qua [boolOf] — MỘT chỗ biết "id nào là bật/tắt", và chính chỗ đó cũng dựng chữ. Tách
        // thành hai bảng (một để biết, một để format) là đúng bẫy hai-bản-sao: chúng lệch nhau ở đúng lần ai đó
        // thêm datum thứ 14 mà chỉ sửa một bên, và lỗi ấy im lặng.
        val bool = boolOf(id, status)
        val value = if (bool != null) bool.on?.let { onOff(it) } else format(id, status)
        // U5 · T2: nhãn theo ngôn ngữ ngay tại đây — `TelemetryView` là thứ tầng vẽ đọc, nên nếu để nhãn gốc thì
        // `:app` phải tự dịch lại (bản-sao-thứ-hai của phép chọn ngôn ngữ).
        return TelemetryView(spec.id, spec.displayLabel, spec.unit, spec.widgetKind, spec.tier, value, bool?.on)
    }

    /**
     * Một datum BẬT/TẮT đã đọc. `Bool(null)` = là datum bật/tắt nhưng **chưa đọc được**; bản thân [boolOf] trả
     * `null` khi id **không phải** datum bật/tắt. Hai mức null khác nghĩa nhau, nên phải là hai tầng.
     */
    private class Bool(val on: Boolean?)

    /**
     * ĐÚNG MỘT bản kê các datum **bật/tắt** (giá trị hiện ra là kết quả của [onOff]) — vừa trả lời *"id này có phải
     * bật/tắt không"*, vừa đưa ra cờ thật để [of] dựng chữ. Id khác ⇒ `null` ⇒ [of] đi đường [format].
     *
     * ## Vì sao đọc thẳng field [CarStatus] chứ không phân loại theo registry
     * *"Là bật/tắt"* là tính chất của **kiểu dữ liệu xe trả về** (`Boolean?`), không phải của dòng registry:
     * [TelemetrySpec.unit] rỗng cho cả bool, enum (`gear`, `op_mode`) và chuỗi (`vin`), còn
     * [TelemetrySpec.widgetKind] nói về *hình vẽ*. Cùng lối [GroupBoard.toneOf] đã đi.
     *
     * ⚠ CỐ Ý **không** gom [yesNo] (`pm25_online`: Có/Không) và [openShut] (cửa/cốp: Mở/Đóng) vào đây. Chúng cũng là
     * `Boolean?` nhưng nghĩa khác: *"có kết nối"* và *"đang mở"* không đọc ra được từ một icon sáng/mờ, và *"cửa
     * đang mở"* là chuyện đáng báo (xem `GroupBoard` xếp nó vào ALERT) chứ không phải một công tắc. Mở phạm vi ở đây
     * là âm thầm biến một cảnh báo thành một icon mờ.
     */
    private fun boolOf(id: String, s: CarStatus): Bool? = when (id) {
        // ── Khí hậu ─────────────────────────────────────────────────────────────────────
        "ac_on" -> Bool(s.climate.acOn)
        "anion_state" -> Bool(s.climate.anionOn)
        "defrost_front_state" -> Bool(s.climate.defrostFrontOn)
        "defrost_rear_state" -> Bool(s.climate.defrostRearOn)

        // ── Thân xe ─────────────────────────────────────────────────────────────────────
        "emergency_alarm" -> Bool(s.body.emergencyAlarm)

        // ── Đèn ─────────────────────────────────────────────────────────────────────────
        "light_low_beam" -> Bool(s.lights.lowBeam)
        "light_high_beam" -> Bool(s.lights.highBeam)
        "light_front_fog" -> Bool(s.lights.frontFog)
        "light_rear_fog" -> Bool(s.lights.rearFog)
        "light_left_turn" -> Bool(s.lights.leftTurn)
        "light_right_turn" -> Bool(s.lights.rightTurn)
        "light_side" -> Bool(s.lights.sideLight)
        "light_drl" -> Bool(s.lights.drl)

        else -> null
    }

    /**
     * Id này có phải datum BẬT/TẮT không (thuộc bản kê [boolOf]) — dù giá trị đã đọc được hay chưa.
     * Dùng cho chip header (B10 owner 2026-09-23): datum bật/tắt CHƯA đọc được thì hiện **icon mờ**, KHÔNG hiện
     * "· —" (dấu gạch vô nghĩa + chiếm chỗ đẩy icon xa nhau). `CarStatus()` rỗng ⇒ boolOf vẫn dựng `Bool(null)`
     * cho id thuộc bản kê ⇒ non-null = là bật/tắt.
     */
    fun isOnOff(id: String): Boolean = boolOf(id, CarStatus()) != null

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

        // ── A2. Động lực ────────────────────────────────────────────────────────────────
        "speed" -> s.drivetrain.speedKmh?.toString()
        "gear" -> s.drivetrain.gear
        // ⚠ 1.90 · `op_mode`/`energy_mode` xoá (owner 2026-09-21 — xe thuần điện; xem `TelemetryRegistry`).

        // ── A3. Khí hậu ─────────────────────────────────────────────────────────────────
        "pm25_level" -> s.climate.pm25Level?.toString()
        "pm25_value" -> s.climate.pm25ValueUgm3?.toString()
        "pm25_outside" -> s.climate.pm25OutsideUgm3?.toString()
        "pm25_online" -> s.climate.pm25Online?.let { yesNo(it) }
        "cabin_temp" -> s.climate.cabinTempC?.toString()
        "ext_temp" -> s.climate.outsideTempC?.toString()
        "ac_wind" -> s.climate.fanLevel?.toString()
        "ac_cycle" -> s.climate.recircOn?.let { if (it) Strings.t("Trong", "Recirc") else Strings.t("Ngoài", "Fresh") }
        "inside_temp" -> s.climate.setTempC?.toString()
        "temp_unit" -> s.climate.tempUnit
        // H1 · T2 — ghế đọc ra MÃ mức của khung, phải đổi qua [ControlLevels] mới thành chữ người ta hiểu. Mã NGOÀI
        // thang ⇒ null ⇒ ô hiện "—": thà nói *"chưa đọc được"* còn hơn làm tròn thành "Mức 1" (thang mới đứng trên
        // MỘT điểm đo — TODO điểm thứ hai ghi ở [ControlLevels]).
        "seat_vent_state" -> s.climate.seatVentRaw?.let { levelText("seatc", it) }
        "seat_heat_state" -> s.climate.seatHeatRaw?.let { levelText("seath", it) }
        // 0 = AUTO (`AC_CTRLMODE_AUTO`) — đảo Ở ĐÂY, và chỉ ở đây, cho bề mặt ĐỌC; nút `ac_auto` có đường riêng
        // ([ControlDef.readInverted]) nên không chỗ nào đảo hai lần.
        "ac_mode_auto" -> s.climate.acModeRaw?.let { if (it == 0) "AUTO" else Strings.t("Chỉnh tay", "Manual") }
        // 1.85 — cùng quy ước và cùng lý do với dòng trên: `AC_WINDLEVEL_MANUAL_SIGN_OFF = 0` ⇒ gió đang AUTO.
        // Đảo Ở ĐÂY cho bề mặt ĐỌC; nút `ac_auto` đảo bằng [ControlDef.readInverted] nên không ai đảo hai lần.
        "ac_wind_auto" -> s.climate.acWindAutoRaw?.let { if (it == 0) "AUTO" else Strings.t("Chỉnh tay", "Manual") }

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
        "power_level" -> s.body.powerLevel?.toString()
        "vehicle_type" -> s.body.vehicleType
        "sunroof_state" -> s.body.sunroofOpen?.let { openShut(it) }

        // ── A6. Đèn ─────────────────────────────────────────────────────────────────────
        // 8 datum đèn bật/tắt (cốt/pha/sương trước-sau/xi-nhan/đèn hông/DRL) nằm ở [boolOf] — chỉ `headlight_feedback`
        // là CHẾ ĐỘ (một con số, không phải công tắc) nên nó ở lại đây.
        "headlight_feedback" -> s.lights.headlightMode?.toString()

        // ── A7. Điện phụ 12V / nguồn máy (nhóm "An toàn · ADAS" đã gỡ hẳn 2026-09-16) ───
        "volt_12v" -> s.energy.volt12v?.let { dec1(it) }
        "volt_12v_level" -> s.energy.volt12vLevel?.toString()

        // ── A8. Danh tính ───────────────────────────────────────────────────────────────
        "vin" -> s.identity.vin
        "oil_level" -> s.identity.oilLevelPct?.toString()

        // MỌI id trong registry đều có case — ở ĐÂY hoặc ở [boolOf] (test `moi telemetry id deu of duoc`).
        // Non-resolvable (GPS/NaviInfo, SET_DR_SOC_TARGET) đọc null (route None) ⇒ "—". else = phòng vệ id
        // ngoài-registry (of() đã chặn) **và** là đường đi của 13 datum bật/tắt (of() đã lấy chữ từ [boolOf] trước
        // khi gọi hàm này, nên chúng không bao giờ tới được đây).
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
     * người dùng. Cần biết một datum đang bật/tắt thì đọc [TelemetryView.onOff] — đường DUY NHẤT, và nó là `Boolean`
     * nên không có ngôn ngữ nào để mà lệch. [onOff] chỉ còn được gọi từ [of] (đường bật/tắt) — đừng gọi nó ở chỗ
     * khác, vì chỗ gọi mới sẽ là một datum bật/tắt mà [boolOf] không biết ⇒ chip mất icon trạng thái, im lặng.
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
