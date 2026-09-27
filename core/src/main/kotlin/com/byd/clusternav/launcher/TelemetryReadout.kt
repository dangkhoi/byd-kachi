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
    /**
     * ═══ UX5 · MỨC của một datum chạy theo **thang mức** — `0` = tắt · `1..n` = mức · `null` = chưa đọc ═══════════
     *
     * Sinh ra vì bề mặt hẹp nhất của launcher (chip thanh trên) chỉ vẽ được **một glyph + một chuỗi**, nên trước
     * UX5 chip ghế in đúng chữ `"Ghế sưởi · Tắt"` — lại là chữ trạng thái mà owner đã gạch bỏ cho ô bật/tắt
     * (2026-09-21: *"bỏ chữ Bật/Tắt đi"*), chỉ khác là lần này nó lọt qua vì ghế **không** phải datum bật/tắt.
     *
     * Con số ở đây làm ba việc mà [valueText] không làm được: (a) chip biết **tắt** để mờ icon thay vì in chữ,
     * (b) chip in `"2"` thay vì `"Mức 2"` mà không phải cắt chuỗi, (c) `ReadTile` vẽ hàng chấm **không cần regex
     * bóc chữ số trong chuỗi đã dịch** — cùng cái bẫy so-chuỗi mà ⚠ của [onOff] đã cấm.
     *
     * ## ⚠ `null` ≠ `0`
     * `null` = *"id này không chạy theo thang mức"* **hoặc** *"chưa đọc được"*; `0` là một lời khẳng định *"xe đang
     * TẮT"*. Hai ca ấy phải khác nhau ở tầng vẽ (mờ vs trung tính), đúng luật *"không biết ≠ đang tắt"* của
     * [ChipTone]. Gộp `null` với hai nghĩa đầu là đủ, vì mọi bề mặt xử chúng y như nhau.
     *
     * ⚠⚠ Mã thô NGOÀI thang ⇒ `null` (không làm tròn) — cùng quyết định đã ghi ở [ControlLevels.levelOf].
     */
    val level: Int? = null,
    /**
     * ═══ UX8 · MÃ TRẠNG THÁI của một datum **HAI CHẾ ĐỘ** — `0`/`1` · `null` = chưa đọc / không phải ═══════════
     *
     * Sinh ra cho họ datum thứ ba mà lượt icon-consistency (09-21/22) không phủ: chế độ *lấy gió trong* ↔ *ngoài*,
     * cảm biến *còn sống* ↔ *chết*. Chúng **không** phải công tắc ([onOff] cố ý không nhận — xem ⚠ ở [boolOf]) và
     * cũng không chạy theo [level], nên trước UX8 chip đành in CHỮ trạng thái (`"Chế độ lấy gió · Trong"`) — đúng
     * thứ owner đã gạch bỏ cho ô bật/tắt từ 2026-09-21, chỉ khác là nó lọt qua vì không luật nào với tới.
     *
     * Con số ở đây là **mã của khung đã chuẩn hoá về 0/1**, để [CapabilityIcons.forState] tra ra HÌNH của đúng chế
     * độ ấy. [valueText] **không đổi** — ô lớn vẫn in chữ; đây là trường CỘNG THÊM, đúng lối [onOff]/[level].
     *
     * ## ⚠ `null` gộp HAI ca, có chủ ý
     * *"id này không phải datum hai chế độ"* **hoặc** *"là, nhưng chưa đọc được"*. Mọi bề mặt xử hai ca ấy y như
     * nhau (hình trung tính, không tô). Cần biết ca nào thì hỏi [CapabilityIcons.hasStateIcons] — một câu hỏi về
     * **bảng khai**, không phải về dữ liệu xe.
     */
    val state: Int? = null,
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
        return TelemetryView(
            spec.id, spec.displayLabel, spec.unit, spec.widgetKind, spec.tier, value, bool?.on,
            level = levelOf(id, status),
            state = stateOf(id, status),
        )
    }

    /**
     * UX5 — MỘT bảng nói cả hai điều mà mức cần: *"datum này là trạng thái của NÚT nào"* và *"mã thô nằm ở field
     * nào của [CarStatus]"*. Id khác ⇒ `null` = không chạy theo thang mức.
     *
     * Vì sao một bảng chứ không hai: thang mức là tính chất của cái người ta **bấm** ([ControlLevels] tra bằng mã
     * NÚT), còn con số thì nằm trong cụm trạng thái xe — tách thành hai bảng là đúng bẫy hai-bản-sao mà KDoc [boolOf]
     * đã cảnh báo (chúng lệch ở đúng lần ai đó thêm mã thứ ba mà chỉ sửa một bên, và lỗi ấy im lặng).
     */
    private class Level(val controlId: String, val raw: Int?)

    private fun levelTable(id: String, s: CarStatus): Level? = when (id) {
        "seat_vent_state" -> Level("seatc", s.climate.seatVentRaw)
        "seat_heat_state" -> Level("seath", s.climate.seatHeatRaw)
        // UX5b (owner 2026-09-27) — ghế PHỤ: cùng khuôn, chỉ trỏ sang nút `*_r` (thang mức của nó ở [ControlLevels]).
        "seat_vent_state_r" -> Level("seatc_r", s.climate.seatVentRRaw)
        "seat_heat_state_r" -> Level("seath_r", s.climate.seatHeatRRaw)
        else -> null
    }

    /** Mức người dùng của [id] (xem [TelemetryView.level]); `null` = không phải thang mức / chưa đọc / mã ngoài thang. */
    private fun levelOf(id: String, s: CarStatus): Int? =
        levelTable(id, s)?.let { l -> l.raw?.let { ControlLevels.levelOf(l.controlId, it) } }

    /**
     * ═══ UX8 — datum **HAI CHẾ ĐỘ**: mã của khung → `0`/`1` (xem [TelemetryView.state]) ════════════════════════
     *
     * Giữ ĐÚNG dạng `"id" -> s.<cụm>.<field>` như [format]/[boolOf]: đó là **hợp đồng đọc-ngược bằng máy** mà
     * `CarDataDemandRendererContractTest` dựa vào để suy bảng datum↔field. Vì bảng này đọc **cùng field** với
     * dòng tương ứng trong [format] nên nó không thêm nhu cầu đọc nào — chỉ thêm một cách **nhìn** con số ấy.
     *
     * ⚠ Nguy cơ hai-bản-sao (bảng này trỏ một field, [format] trỏ field khác cho cùng mã) được **ĐO** chứ không
     * hy vọng: `TelemetryReadoutTest.hinh trang thai va chu trang thai khong bao gio lech` đỏ ngay nếu chữ đổi mà
     * mã trạng thái đứng yên (hoặc ngược lại).
     *
     * ⚠⚠ Ai khai ở đây thì **phải** khai hình ở [CapabilityIcons] — bằng không con số này không ai đọc và chip
     * lặng lẽ giữ nguyên chữ cũ (CLAUDE.md §8: hàm mới phải có chỗ gọi). Cùng bài canh khoá cả hai chiều.
     */
    private fun stateTable(id: String, s: CarStatus): Boolean? = when (id) {
        // [ĐO] `BYDAutoAcDevice.java:33-34` — INLOOP = 1 (lấy gió TRONG) · OUTLOOP = 0 (lấy gió NGOÀI).
        "ac_cycle" -> s.climate.recircOn
        // `getPM2p5OnlineState` — 1 = cảm biến còn trả lời · 0 = chết. Không phải công tắc ⇒ không vào [boolOf].
        "pm25_online" -> s.climate.pm25Online
        // ═══ 2.76 (R8) — CỬA ×4 + CỬA SỔ TRỜI: mở/đóng là HAI CHẾ ĐỘ, không phải công tắc (KDoc [boolOf] đã cấm
        // gom vào đó). Mã 1 = MỞ (trạng thái ĐÁNG BÁO — GroupBoard xếp ALERT) · 0 = ĐÓNG. [ĐO source]
        // `BYDAutoBodyworkDevice.java:203-205`: `CLOSED = 0` · `OPEN = 1` · **`UNDEFINED = 255`** — hằng thứ ba ấy
        // không tới được đây vì `HalReadTables.INVALID_VALUES` đã bỏ nó ở tầng đọc ([P1] soát 27/09: cờ Boolean của
        // `CarStatus.Body` xoá mất sentinel, nên phải lọc TRƯỚC khi quy về Boolean — xem KDoc bảng ấy).
        // Cửa sổ trời: [ĐO 4 lượt quét xe owner — carlog-0916/sweep-1.64.json + perf-oncar-2026-09-26/kachi-logs]
        // `getSunroofState = 0` ⇒ trên xe ấy datum này ĐỌC ĐƯỢC và ra mã 0 (ĐÓNG), **không** phải `null`. Con số
        // 65535 của [ĐO 09-25] là của một getter KHÁC (`getSunroofPosition`) — datum `sunroof_pos` đã gỡ vì thế.
        // *"Xe owner không có nóc mở"* chỉ là **[ĐOÁN]** (nóc kính liền cũng cho pos = sentinel mà state = 0); hình
        // MỞ vẫn [CHƯA BIẾT] trên xe này — 🚗 xem `chips-icons-close.md` §5.
        "door_lf" -> s.body.doorLfOpen
        "door_rf" -> s.body.doorRfOpen
        "door_lr" -> s.body.doorLrOpen
        "door_rr" -> s.body.doorRrOpen
        "sunroof_state" -> s.body.sunroofOpen
        // ═══ 2.76 (R9) — hai chỉ báo TỰ ĐỘNG / CHỈNH TAY, mỗi cái đọc MỘT hằng OEM riêng (cố ý KHÔNG gộp qua một
        // hàm chung — xem cảnh báo ở [format] `ac_wind_auto`). Mã 0 = AUTO · 1 = TAY; mã khác (sentinel 65535,
        // −2147482648) ⇒ `null` = *"chưa biết"*, KHÔNG làm tròn thành "tay" (luật "không biết ≠ đang tắt").
        //   • `ac_mode_auto`: `AC_CTRLMODE_AUTO = 0` · `AC_CTRLMODE_MANUAL = 1` — `ac/BYDAutoAcDevice.java:20-21`.
        //   • `ac_wind_auto`: `AC_WINDLEVEL_MANUAL_SIGN_OFF = 0` · `_ON = 1` — cùng tệp `:107-108`.
        // [ĐO xe 09-16] getAcControlMode = 0 khi màn AC đang AUTO · [ĐO xe 09-27 G4] getAcWindLevelManualSign = 0
        // khi đang AUTO — hai getter, hai feature id (AC_CTRL_MODE 1077936146 · AC_WINDLEVEL_MANUAL_SIGN 1077936140)
        // ⇒ hai sự thật HAL, KHÔNG gộp datum (chi tiết: docs/diagnostics/offcar-2026-09-27/chips-icons-close.md §3).
        "ac_mode_auto" -> s.climate.acModeRaw?.let { when (it) { 0 -> false; 1 -> true; else -> null } }
        "ac_wind_auto" -> s.climate.acWindAutoRaw?.let { when (it) { 0 -> false; 1 -> true; else -> null } }
        else -> null
    }

    /** Mã trạng thái của [id] (xem [TelemetryView.state]); `null` = không phải datum hai chế độ / chưa đọc. */
    private fun stateOf(id: String, s: CarStatus): Int? = stateTable(id, s)?.let { if (it) 1 else 0 }

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
        "soh_oem" -> s.energy.sohPct?.toString()
        "fuel_pct" -> s.energy.fuelPct?.toString()
        "trip_km" -> s.energy.tripKm?.let { dec1(it) }
        "trip_hours" -> s.energy.tripHours?.let { hoursToHm(it) }
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
        // UX4 — đang AUTO thì chip/ô nói `"AUTO 1"`: mức là số THẬT ([ĐO xe 2026-09-16] xe báo `getAcWindLevel` ngay
        // khi `getAcControlMode` = AUTO). Phép quy đổi nằm ở [ClimateAuto] — một bản cho cả ô nút lẫn chip.
        // ⚠ `s.climate.fanLevel.let { … }` (KHÔNG `?.`): giữ nguyên dạng `"id" -> s.<cụm>.<field>` mà bài đọc-ngược
        // `CarDataDemandRendererContractTest` dựa vào, và `fanText` **cần** được gọi cả khi mức chưa đọc được —
        // đang AUTO mà mức còn null thì chip vẫn phải nói `"AUTO"`.
        "ac_wind" -> s.climate.fanLevel.let { ClimateAuto.fanText(it, s.climate.acWindAutoRaw) }
        "ac_cycle" -> s.climate.recircOn?.let { if (it) Strings.t("Trong", "Recirc") else Strings.t("Ngoài", "Fresh") }
        "inside_temp" -> s.climate.setTempC?.toString()
        "temp_unit" -> s.climate.tempUnit
        // H1 · T2 — ghế đọc ra MÃ mức của khung, phải đổi qua [ControlLevels] mới thành chữ người ta hiểu. Mã NGOÀI
        // thang ⇒ null ⇒ ô hiện "—": thà nói *"chưa đọc được"* còn hơn làm tròn thành "Mức 1" (thang mới đứng trên
        // MỘT điểm đo — TODO điểm thứ hai ghi ở [ControlLevels]).
        // ⚠ UX5 — HAI dòng này và [levelTable] nói cùng một việc bằng hai chỗ, **có chủ ý**: dạng
        // `"id" -> s.<cụm>.<field>` là một **hợp đồng đọc-ngược bằng máy** (`CarDataDemandRendererContractTest`
        // suy ra bảng datum↔field từ chính chỗ này để canh nhu cầu đọc của mọi ô). Đổi dạng là làm bài canh ấy mù.
        // Nguy cơ hai-bản-sao được **đo** thay vì hy vọng: `TelemetryReadoutTest.chu muc va so muc khong bao gio lech`
        // đỏ ngay nếu một bên có mã mà bên kia không.
        "seat_vent_state" -> s.climate.seatVentRaw?.let { levelText("seatc", it) }
        "seat_heat_state" -> s.climate.seatHeatRaw?.let { levelText("seath", it) }
        // UX5b — ghế PHỤ. Giữ ĐÚNG dạng `"id" -> s.<cụm>.<field>` như mọi dòng khác: đó là hợp đồng đọc-ngược bằng
        // máy mà `CarDataDemandRendererContractTest` dựa vào (xem ⚠ ngay trên).
        "seat_vent_state_r" -> s.climate.seatVentRRaw?.let { levelText("seatc_r", it) }
        "seat_heat_state_r" -> s.climate.seatHeatRRaw?.let { levelText("seath_r", it) }
        // 0 = AUTO (`AC_CTRLMODE_AUTO`) — đảo Ở ĐÂY, và chỉ ở đây, cho bề mặt ĐỌC; nút `ac_auto` có đường riêng
        // ([ControlDef.readInverted]) nên không chỗ nào đảo hai lần.
        // 2.76: mã ngoài {0, 1} (sentinel) ⇒ *"mã N"* ([TelemetryEnums.unknown]) thay vì bịa "Chỉnh tay" — cùng lúc
        // [stateTable] trả `null` cho mã ấy, nên chữ và hình không bao giờ nói hai điều.
        "ac_mode_auto" -> s.climate.acModeRaw?.let {
            when (it) { 0 -> ClimateAuto.AUTO; 1 -> Strings.t("Chỉnh tay", "Manual"); else -> TelemetryEnums.unknown(it) }
        }
        // 1.85 — cùng quy ước và cùng lý do với dòng trên: `AC_WINDLEVEL_MANUAL_SIGN_OFF = 0` ⇒ gió đang AUTO.
        // Đảo Ở ĐÂY cho bề mặt ĐỌC; nút `ac_auto` đảo bằng [ControlDef.readInverted] nên không ai đảo hai lần.
        // UX4 — phép đảo `0 = AUTO` của CHỈ BÁO GIÓ nay chỉ còn một bản, ở [ClimateAuto.autoOnFromRaw]. (Dòng
        // `ac_mode_auto` ngay trên giữ phép so riêng: nó đọc `AC_CTRLMODE_AUTO`, một hằng KHÁC chỉ tình cờ cũng = 0 —
        // gộp hai hằng khác họ vào một hàm là mời một lượt sửa sau làm sai cả hai, xem CLAUDE.md §2.)
        "ac_wind_auto" -> s.climate.acWindAutoRaw?.let {
            when {
                ClimateAuto.autoOnFromRaw(it) == true -> ClimateAuto.AUTO
                it == 1 -> Strings.t("Chỉnh tay", "Manual")
                else -> TelemetryEnums.unknown(it)   // 2.76: sentinel ⇒ "mã N", khớp [stateTable] trả null
            }
        }

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
        "sunshade_pct" -> s.body.sunshadePct?.toString()
        // 2.76 (R8) — MÃ → CHỮ qua bảng OEM ([TelemetryEnums.POWER_LEVEL]); trước đó chip in "Nguồn xe · 2" [P3 UX8].
        "power_level" -> s.body.powerLevel?.let { TelemetryEnums.text("power_level", it) }
        "vehicle_type" -> s.body.vehicleType
        "sunroof_state" -> s.body.sunroofOpen?.let { openShut(it) }

        // ── A6. Đèn ─────────────────────────────────────────────────────────────────────
        // 8 datum đèn bật/tắt (cốt/pha/sương trước-sau/xi-nhan/đèn hông/DRL) nằm ở [boolOf] — chỉ `headlight_feedback`
        // là CHẾ ĐỘ (một con số, không phải công tắc) nên nó ở lại đây.
        // 2.76 (R8) — bảng từ CarSettings OEM ([TelemetryEnums.HEADLIGHT_MODE]); trước đó chip in "Chế độ đèn pha · 2".
        "headlight_feedback" -> s.lights.headlightMode?.let { TelemetryEnums.text("headlight_feedback", it) }

        // ── A7. Điện phụ 12V / nguồn máy (nhóm "An toàn · ADAS" đã gỡ hẳn 2026-09-16) ───
        "volt_12v" -> s.energy.volt12v?.let { dec1(it) }

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

    /**
     * V4 (owner 2026-09-25): giờ thập phân → "h:mm" (1.6h → "1:36") — dễ đọc hơn "1.6h". Phút cắt xuống, kẹp 0..59.
     *
     * TRIP-TIME-6MIN (2026-09-27): `(h * 60).toInt()` cắt SAI ở biên phút vì sai số nhị phân — `4.1 * 60 =
     * 245.99999999999997` ⇒ "4:05" thay "4:06" (17/1000 mốc thô 0,1 h), và với giá trị đã làm mượt `mốc + k/60`
     * ([TripTimeSmoother]) là **620/6000** ca sai (vd `1.9 + 1/60` ⇒ "1:54" thay "1:55"). Cộng 1e-6 phút (60 µs) rồi
     * floor: dưới mọi độ phân giải thật, trên mọi sai số nhị phân (quét 0..99,9 h × 0..5 phút: 0 ca sai).
     */
    private fun hoursToHm(h: Double): String {
        val totalMin = Math.floor(h * 60.0 + 1e-6).toInt().coerceAtLeast(0)
        return "${totalMin / 60}:${String.format(Locale.US, "%02d", totalMin % 60)}"
    }
    private fun dec2(d: Double) = String.format(Locale.US, "%.2f", d)
    private fun dec5(d: Double) = String.format(Locale.US, "%.5f", d)
}
