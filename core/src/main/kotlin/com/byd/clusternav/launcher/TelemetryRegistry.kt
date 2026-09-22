package com.byd.clusternav.launcher

import com.byd.clusternav.launcher.Domain.BODY
import com.byd.clusternav.launcher.Domain.CLIMATE
import com.byd.clusternav.launcher.Domain.DRIVETRAIN
import com.byd.clusternav.launcher.Domain.ENERGY
import com.byd.clusternav.launcher.Domain.IDENTITY
import com.byd.clusternav.launcher.Domain.INFOTAINMENT
import com.byd.clusternav.launcher.Domain.LIGHTS
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
     * Là **tham số mặc định** để không phải sửa cả trăm dòng khai báo: chỉ điền cho datum nào thật sự cần. Trước đây khái
     * niệm này chỉ tồn tại ở `TyreCorner.shortLabel` (bảng lốp), tức mỗi bề mặt hẹp lại tự nghĩ cách viết tắt riêng.
     */
    val short: String? = null,
    /**
     * Nhãn tiếng Anh (U5 · T2) — **cùng khuôn tham số mặc định** với [short], vì cùng lý do: nó là DỮ LIỆU của dòng
     * này, không phải chữ tra từ tài nguyên Android (`:core` thuần, xem KDoc [Strings]).
     *
     * `null` ⇒ [displayLabel] lùi về [label]; `LangCoverageTest` đếm tuyệt đối mọi dòng nên bỏ trống là **đỏ off-car**.
     */
    override val labelEn: String? = null,
    /** Nhãn NGẮN tiếng Anh. `null` ⇒ [shortLabelIn] lùi về [labelEn] rồi tới [label] — xem [shortLabel]. */
    val shortEn: String? = null,
    /**
     * Ghi ĐÈ thiết bị BYDAuto cho đường **feature-id** ĐỌC — cùng khuôn [ControlDef.halDevice]. `null` ⇒ chọn theo
     * [domain] ([HalBindingTable.featureDeviceFqn]).
     *
     * [ĐO] `docs/diagnostics/hal-binding-remediation-2026-09-15.md` §C: trước đây chỉ `ControlDef` có trường này nên
     * MỌI feature-read đi theo Domain mặc định — sai cho ion (PM2P5, không AC), độ dốc (Sensor, không Setting), đèn
     * viền (Setting, không Light). Các mục có getter tên thật đã được chuyển sang named-method (degrade-safe hơn);
     * trường này dành cho mục CHỈ có feature-id.
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
 * REGISTRY TELEMETRY — bản kê MỌI datum đọc được, gom theo 7 domain của catalog §A
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
        t("ev_mileage_km", "Km chạy điện", "EV distance driven", "km", ENERGY, VALUE, NEEDS_CAR, "BYDAutoStatisticDevice.getEVMileageValue", shortEn = "EV distance"),
        // ═══ V3 · R11 — bind theo TÊN HẰNG, giá trị tra lúc chạy (xem [BindingRoute.FeatureName]) ═══
        // [ĐO nguồn fw-dl3 2026-09-16] `BYDAutoFeatureIds.java`: `INSTRUMENT_2IN1_CURRENT_JOURNEY_DRIVE_MILEAGE`
        // = 1246801948 **chỉ khi** `isCanFD`; không CanFD thì 1230024732 (Toyota) hoặc 602471. Số cũ chép từ
        // jadx-tmap là con số của MỘT cấu hình ⇒ trên xe khác cấu hình nó không tồn tại và HAL từ chối.
        // Tên hằng thuộc lớp lồng `Instrument` ⇒ device đích do `BYDAutoDeviceFeaturesMap` quyết, không đoán.
        // NEEDS-ONCAR (còn lại): scale phút-vs-giờ của DRIVE_TIME.
        t("trip_km", "Quãng đường chuyến", "Trip distance", "km", ENERGY, VALUE, NEEDS_CAR,
            "BYDAutoFeatureIds.Instrument.INSTRUMENT_2IN1_CURRENT_JOURNEY_DRIVE_MILEAGE",
            short = "Quãng chuyến", shortEn = "Trip dist."),
        t("trip_hours", "Thời gian chuyến", "Trip time", "h", ENERGY, VALUE, NEEDS_CAR,
            "BYDAutoFeatureIds.Instrument.INSTRUMENT_2IN1_CURRENT_JOURNEY_DRIVE_TIME"),
        t("trip_kwh", "Điện tiêu thụ chuyến", "Trip energy used", "kWh", ENERGY, VALUE, NEEDS_CAR, "1246801976", short = "Điện chuyến", shortEn = "Trip energy"),
        t("consumption_50km", "Tiêu thụ 50km", "Consumption last 50 km", "kWh/100km", ENERGY, VALUE, OVERDRIVE, "BYDAutoInstrumentDevice.getLast50KmPowerConsume", shortEn = "Use 50 km"),
        // V3 · R11 — [ĐO nguồn fw-dl3] `ENGINE_POWER` = 339738656 (CanFD) / 353370144 (Toyota) / 1033203762.
        t("motor_power", "Công suất mô-tơ", "Motor power", "kW", ENERGY, GAUGE, OVERDRIVE,
            "BYDAutoFeatureIds.Engine.ENGINE_POWER"),
        // ⚠ (V) FEATURE-FILTER 2026-09-17 — owner chấm NO cho TOÀN BỘ mục SẠC: `is_charging` · `charge_power` ·
        // `charging_pct` · `charging_eta_hour` · `charging_eta_min` · `charging_capacity_kwh` · `charging_state` ·
        // `charger_work_state` · `batt_range_bodywork` (đọc) và `target_soc_set` · `charge_cap` · `start_charging`
        // (ghi, ở ControlRegistry). Xe sạc ở trụ/nhà, người lái không theo dõi qua launcher. Thêm lại =
        // quyết định của owner. `target_soc` (đọc, NEEDS_CAR) và `wireless_charge` (nút) KHÔNG nằm trong danh sách NO.
        // NEEDS-ONCAR: batt_temp / soh_oem — feature-id zero-hoá trong decompile, không có named.
        t("batt_temp", "Nhiệt độ pin", "Battery temp", "°C", ENERGY, VALUE, NEEDS_CAR, "BYDAutoChargingDevice.getBatteryTemp"),
        t("soh_oem", "Sức khỏe pin (SOH)", "Battery health (SOH)", "%", ENERGY, CARD, OVERDRIVE, "1145045032", short = "SOH pin", shortEn = "SOH"),
        t("target_soc", "Mục tiêu sạc", "Charge target", "%", ENERGY, VALUE, NEEDS_CAR, "SET_DR_SOC_TARGET"),

        // ── A2. Động lực / tốc độ / chuyển động ──────────────────────────────────────────────────
        t("speed", "Tốc độ", "Speed", "km/h", DRIVETRAIN, DIAL, PROVEN, "BYDAutoSpeedDevice.getCurrentSpeed"),
        // BYDAutoGearboxDevice.java:109 — GEAR_P=3/R=1/N=0/D=2/INVALID=255 (:79-83). Cũ `getGearboxState` chỉ ON/OFF.
        t("gear", "Số", "Gear", "", DRIVETRAIN, BADGE, NEEDS_CAR, "BYDAutoGearboxDevice.getCurrentGear"),
        // ⚠⚠ 1.90 · **`op_mode` (Chế độ lái) và `energy_mode` (Chế độ năng lượng) ĐÃ XOÁ HẲN** — owner chốt
        // 2026-09-21. `energy_mode` vì **xe thuần điện** (thang STOP/EV/FORCE_EV/HEV/FUEL/KEEP không có nghĩa khi
        // không có động cơ xăng — [ĐO sweep 09-21] đọc ra `3`=HEV trên một chiếc EV, tức con số vô nghĩa đang được
        // bày ra như thật); `op_mode` vì nút đổi chế độ lái (`drive_mode`) đã gỡ từ (V) 2026-09-17, nên chỉ còn một
        // ô CHỈ-ĐỌC cho thứ người lái đã thấy ngay trên táp-lô.
        // ⇒ Hai trường `CarStatus.Drivetrain.opMode`/`energyMode` + hai mục `FAST_IDS` gỡ theo (chúng không còn ai
        // đọc). Nút `powertrain_mode` (EV/HEV) cũng xoá cùng lượt ở `ControlRegistry`.
        // ⚠ (V) FEATURE-FILTER 2026-09-17 — `drift_mode` (đọc) và `drive_mode` (nút, ControlRegistry) đã xoá:
        // owner chấm NO.

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
        // ═══ 1.85 · BỤI MỊN NGOÀI XE — KHÔNG phải getter riêng, mà là PHẦN TỬ THỨ HAI của getter đã dùng ════════
        //
        // Lượt 1.84 đi tìm một getter tên kiểu `getOutCarPM2p5` (analogy `getOutCarTemperature`) và không thấy, nên
        // datum này mang khoá giả `UNMAPPED_OUTSIDE_PM25` ⇒ hiện "—". [ĐO nguồn 2026-09-20] câu hỏi đặt sai chỗ:
        //  • `BYDAutoPM2p5Device.getPM2p5Value()` trả **`int[]`**, không phải `int` (stub `jadx-tmap/.../pm2p5/
        //    BYDAutoPM2p5Device.java:92` · `jadx-kim` cùng lớp) — thiết bị này là **dual-channel**
        //    (`FEATURE_DUAL_CHANNEL_DETECT`, `WARNING_INFO_EXCESS_IN=1`/`_OUT=2`);
        //  • **javadoc CHÍNH THỨC của BYD** (`sdk_v1.0.5/DOC_v1.0.5/doc/.../pm2p5/BYDAutoPM2p5Device.html`) nói
        //    thẳng thứ tự: *"The first param is value **in** auto and the second param is value **out** of auto"*
        //    (0..3000 µg/m³) ⇒ **[1] = NGOÀI xe**;
        //  • callback cùng thiết bị khai đúng thứ tự ấy — `onPM2p5ValueChanged(int value_in, int value_out)`
        //    (`jadx-kim/.../AbsBYDAutoPM2p5Listener.java:16`, bản này còn giữ TÊN tham số), khớp [ĐO car log
        //    2026-09-20] `Pm2p5Controller: onPM2p5ValueChanged in:8 out:22`.
        // ⇒ cùng getter với `pm25_value`, khác **chỉ số phần tử**: `pm25_value` lấy [0] (đường `firstOfArray` đã
        // chạy thật, tier PROVEN), datum này lấy [1] qua `HalReadTables.ARRAY_INDEX`.
        //
        // ⚠ TIER GIỮ `NEEDS_CAR` — có chủ ý. Thứ tự phần tử là [ĐO nguồn] (vendor doc) + [ĐO log] chứ **chưa** có
        // một lượt đọc `getPM2p5Value()` nào trên xe owner in ra cả hai ô để đối chiếu. Đường đã nối nên nó có thể
        // hiện số thật ngay; dấu "chưa kiểm" ở lại tới khi sweep đọc được ([ĐO] out ≈ 22 khi in ≈ 8 ⇒ lên PROVEN).
        t("pm25_outside", "Bụi mịn ngoài xe", "Outside fine dust", "µg/m³", CLIMATE, VALUE, NEEDS_CAR, "BYDAutoPM2p5Device.getPM2p5Value", short = "Bụi ngoài", shortEn = "Outside dust"),
        // ⚠ 1.85 · [ĐO xe 2026-09-20] ô này đọc **X** (hiện "—") trong khi xe CÓ số nhiệt ([ĐO car log]
        // `AmapService: temp=22`). **KHÔNG sửa route ở lượt này, và đây là lý do** (task: *"không chắc thì ghi TODO,
        // không bịa"*): [ĐO nguồn] cả `jadx-tmap`/`jadx-kim`/`jadx-dashcast`/`jadx-openbyd` lẫn **javadoc chính thức
        // BYD** (`sdk_v1.0.5`) đều KHÔNG có getter nào cho nhiệt độ ĐO ĐƯỢC trong cabin — thiết bị AC chỉ phơi
        // `getTemprature(area)` (= **setpoint**, dải 17..33 = `AC_TEMP_IN_CELSIUS_MIN/MAX`) và Instrument phơi
        // `getOutCarTemperature` (ngoài xe). Con số `temp=22` của AmapService vì thế **có thể chính là setpoint**,
        // không phải một datum thứ ba. ⇒ TODO on-car (`0-PENDING` nhóm D): sweep feature-id này trên device AC
        // (1031798832 = đường hiện tại) rồi đối chiếu với số trên màn AC khi ĐỔI setpoint — nếu hai số dính nhau thì
        // ô này TRÙNG `inside_temp` và nên bỏ, chứ không phải nối thêm một getter.
        t("cabin_temp", "Nhiệt trong cabin", "Cabin temp", "°C", CLIMATE, VALUE, NEEDS_CAR, "1031798832"),
        // ⚠ 1.85 · GỐC của *"nhiệt cài đặt đọc X"* [ĐO xe 2026-09-20] — nằm ở **tham số**, không ở tên getter.
        // `HalReadTables.readArg` khai `inside_temp → 0`, mà **javadoc chính thức BYD** cho `getTemprature(int area)`
        // liệt kê đúng bốn vùng đọc được: `AC_TEMPERATURE_MAIN`(1) · `_DEPUTY`(2) · `_REAR`(3) · `_OUT`(4) — **0
        // KHÔNG có trong danh sách** (0 = `AC_TEMPERATURE_MAIN_DEPUTY`, một *type* của đường GHI `setAcTemperature`,
        // xem `HalBindingTable.writeArgs` ca `temp`) ⇒ đọc area 0 trả `AC_COMMAND_INVALID_VALUE` = sentinel ⇒ "—".
        // Nút `temp` không bị bệnh này vì nó đã ghi đè `readArg = 1` từ 1.69 — nay datum dùng CHUNG con số đó.
        t("inside_temp", "Nhiệt cài đặt", "Set temp", "°C", CLIMATE, VALUE, OVERDRIVE, "BYDAutoAcDevice.getTemprature", short = "Trong xe", shortEn = "In car"),
        t("ext_temp", "Nhiệt ngoài xe", "Outside temp", "°C", CLIMATE, VALUE, OVERDRIVE, "BYDAutoInstrumentDevice.getOutCarTemperature"),
        t("ac_on", "Điều hòa", "Air conditioning", "", CLIMATE, BADGE, OVERDRIVE, "BYDAutoAcDevice.getAcStartState", shortEn = "A/C"),
        // BYDAutoAcDevice.java:298 (0–7). Cũ `getWindLevel` không tồn tại.
        t("ac_wind", "Mức quạt gió", "Fan level", "", CLIMATE, VALUE, OVERDRIVE, "BYDAutoAcDevice.getAcWindLevel"),
        // BYDAutoAcDevice.java:218 — INLOOP=1 (trong) / OUTLOOP=0 (ngoài) (:24-25). Cũ `getCycleMode` không tồn tại.
        t("ac_cycle", "Chế độ lấy gió", "Recirculation mode", "", CLIMATE, BADGE, OVERDRIVE, "BYDAutoAcDevice.getAcCycleMode", shortEn = "Air intake"),
        // BYDAutoAcDevice.java:400 — °C=1 / °F=0 (:76-77). Cũ route car-setting `unit_temperature` luôn null (settingGet chưa wire).
        t("temp_unit", "Đơn vị nhiệt", "Temperature unit", "", CLIMATE, BADGE, OVERDRIVE, "BYDAutoAcDevice.getTemperatureUnit", shortEn = "Temp unit"),
        // BYDAutoPM2p5Device.java:72 — cũ feature 1033895958 route theo Domain.CLIMATE → AC (sai device, thật PM2P5).
        t("anion_state", "Ion âm", "Negative ions", "", CLIMATE, BADGE, OVERDRIVE, "BYDAutoPM2p5Device.getPM2p5AnionState"),
        // ═══ H1 · T2 — NĂM getter ĐÃ ĐO TRÊN XE, nay có datum để nút mượn đường đọc ═══════════════════════
        // [ĐO xe 2026-09-16 · `docs/diagnostics/oncar-trace-2026-09-16b/README.md` §1] — mỗi dòng dưới đây là MỘT
        // lời gọi thật, có giá trị trả về thật, chứ không phải tên method chép từ stub:
        //  • ghế mát/sưởi sống trên **`BYDAutoSettingDevice`** — gọi đúng hai tên ấy trên device **AC** trả `null`
        //    ("unavailable: null", `hal-reads.txt:10-11`) còn trên Setting trả 3 và 1 (`:22-25`). Khoá named-method
        //    mang sẵn tên device nên không ai phải đoán theo domain — đúng chỗ họ lỗi §C của
        //    `hal-binding-remediation-2026-09-15.md` (sai device ⇒ datum im lặng) không tái diễn.
        //  • thang mức ghế là **mã khung**, không phải 0/1 — đổi sang mức người dùng ở [ControlLevels] (một bảng dữ
        //    liệu, TODO điểm đo thứ hai ghi ngay tại đó).
        t("seat_vent_state", "Mức ghế mát", "Seat ventilation level", "", CLIMATE, VALUE, PROVEN,
            "BYDAutoSettingDevice.getSeatVentilatingState", short = "Ghế mát", shortEn = "Seat vent"),
        t("seat_heat_state", "Mức ghế sưởi", "Seat heating level", "", CLIMATE, VALUE, PROVEN,
            "BYDAutoSettingDevice.getSeatHeatingState", short = "Ghế sưởi", shortEn = "Seat heat"),
        // `getAcDefrostState(area)` — area 1 = kính trước · 2 = kính sau ([ĐO] cả hai trả 0 lúc đo, `hal-reads.txt:2-3`);
        // tham số khai MỘT chỗ ở `HalBindingTable.readArg`.
        t("defrost_front_state", "Trạng thái sấy trước", "Front defrost state", "", CLIMATE, BADGE, PROVEN,
            "BYDAutoAcDevice.getAcDefrostState", short = "Sấy trước", shortEn = "Front defrost"),
        t("defrost_rear_state", "Trạng thái sấy sau", "Rear defrost state", "", CLIMATE, BADGE, PROVEN,
            "BYDAutoAcDevice.getAcDefrostState", short = "Sấy sau", shortEn = "Rear defrost"),
        // ⚠ Số của getter này ĐẢO so với ô bật/tắt: `AC_CTRLMODE_AUTO = 0` / `_MANUAL = 1` (`ac/BYDAutoAcDevice.java:29-30`;
        // [ĐO] đọc ra 0 trong khi owner xác nhận màn xe đang AUTO) ⇒ nút `ac_auto` khai [ControlDef.readInverted].
        // Datum thì giữ NGUYÊN số thô của khung — đảo là việc của nút, không phải của phép đọc.
        t("ac_mode_auto", "Chế độ điều hòa", "A/C control mode", "", CLIMATE, BADGE, PROVEN,
            "BYDAutoAcDevice.getAcControlMode", short = "Chế độ ĐH", shortEn = "A/C mode"),
        // ═══ 1.85 · CHỈ BÁO **GIÓ TỰ ĐỘNG** — đường ĐỌC của nút `ac_auto` sau khi nút ấy đổi nghĩa ═════════════
        //
        // [ĐO xe 2026-09-20 §3] nút `ac_auto` nay ghi `AC_CTRL_MODE_SET` và thứ owner thấy đổi trên màn AC là
        // **gió auto**, nên chỉ báo của nó là `AC_WINDLEVEL_MANUAL_SIGN`, KHÔNG phải `getAcControlMode` (đó là
        // *chế độ điều hoà*, một câu hỏi khác — datum ngay trên vẫn giữ nguyên, không ai mất gì).
        //
        // [ĐO nguồn] getter có tên thật (degrade-safe hơn feature-id): `getAcWindLevelManualSign()`
        // (`jadx-tmap/.../ac/BYDAutoAcDevice.java:302`), và **javadoc chính thức BYD** ghi rõ hai giá trị:
        // *"Auto ctrl: `AC_WINDLEVEL_MANUAL_SIGN_OFF`(0) · Manual ctrl: `AC_WINDLEVEL_MANUAL_SIGN_ON`(1)"*.
        // ⇒ **0 = đang AUTO**, tức số ĐẢO so với ô bật/tắt — y hệt `ac_mode_auto`. Datum giữ **số thô của khung**
        // (phép đảo là việc của nút, qua [ControlDef.readInverted]) để không có hai chỗ cùng đảo.
        t("ac_wind_auto", "Chế độ gió", "Fan mode", "", CLIMATE, BADGE, OVERDRIVE,
            "BYDAutoAcDevice.getAcWindLevelManualSign", short = "Gió auto", shortEn = "Fan auto"),

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
        t("window_lf", "Kính lái", "Driver window", "%", BODY, STRIP, PROVEN, "BYDAutoBodyworkDevice.getWindowOpenPercent", short = "Kính lái", shortEn = "Driver win"),
        t("window_rf", "Kính phụ", "Passenger window", "%", BODY, STRIP, PROVEN, "BYDAutoBodyworkDevice.getWindowOpenPercent", short = "Kính phụ", shortEn = "Pass. win"),
        t("window_lr", "Kính sau trái", "Rear-left window", "%", BODY, STRIP, PROVEN, "BYDAutoBodyworkDevice.getWindowOpenPercent", short = "Kính ST", shortEn = "Win RL"),
        t("window_rr", "Kính sau phải", "Rear-right window", "%", BODY, STRIP, PROVEN, "BYDAutoBodyworkDevice.getWindowOpenPercent", short = "Kính SP", shortEn = "Win RR"),
        // [ĐO] `int getDoorState(int area)` BYDAutoBodyworkDevice.java:450 — CLOSED0/OPEN1/255; area LEFT_FRONT=1/
        // RIGHT_FRONT=2/LEFT_REAR=3/RIGHT_REAR=4 (:172-176) — HalBindingTable.readArg cấp (cũ feature-id 692060176…9 đọc vỡ §A).
        t("door_lf", "Cửa trước-trái", "Door front-left", "", BODY, STRIP, OVERDRIVE, "BYDAutoBodyworkDevice.getDoorState", shortEn = "Door FL"),
        t("door_rf", "Cửa trước-phải", "Door front-right", "", BODY, STRIP, OVERDRIVE, "BYDAutoBodyworkDevice.getDoorState", shortEn = "Door FR"),
        t("door_lr", "Cửa sau-trái", "Door rear-left", "", BODY, STRIP, OVERDRIVE, "BYDAutoBodyworkDevice.getDoorState", shortEn = "Door RL"),
        t("door_rr", "Cửa sau-phải", "Door rear-right", "", BODY, STRIP, OVERDRIVE, "BYDAutoBodyworkDevice.getDoorState", shortEn = "Door RR"),
        t("tailgate_status", "Cốp sau", "Tailgate", "", BODY, STRIP, NEEDS_CAR, "BYDAutoBodyworkDevice.getHatchDoorStatus"),
        t("sunroof_state", "Cửa sổ trời", "Sunroof", "", BODY, BADGE, OVERDRIVE, "BYDAutoBodyworkDevice.getSunroofState"),
        t("sunroof_pos", "Vị trí cửa sổ trời", "Sunroof position", "%", BODY, VALUE, NEEDS_CAR, "BYDAutoBodyworkDevice.getSunroofPosition", shortEn = "Sunroof pos"),
        t("sunshade_pct", "Rèm che nắng", "Sunshade", "%", BODY, VALUE, OVERDRIVE, "1101004816"),
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
        // NEEDS-ONCAR: headlight_feedback — device nghi SETTING(1023).
        t("headlight_feedback", "Chế độ đèn pha", "Headlight mode", "", LIGHTS, BADGE, OVERDRIVE, "1011875880"),

        // ── A7. Điện phụ / nguồn (trước 2026-09-16 là "An toàn · ADAS") ─────────────────────────
        // ⚠ Owner 2026-09-16 gỡ TOÀN BỘ ADAS/an toàn chủ động khỏi launcher — 17 datum (dây an toàn · nhận diện
        // người ngồi · phát hiện trẻ em · cảnh báo quá tốc · điểm mù · chuyển làn · cắt ngang sau · cảnh báo mở cửa ·
        // 8 vùng cảm biến đỗ · âm lượng cảm biến · ESP) đã xoá cùng `Domain.SAFETY`. Ba mục dưới đây KHÔNG thuộc hệ
        // an toàn lái: chúng nói về **điện 12V và nguồn máy**, nên ở lại dưới [ENERGY].
        // ⚠ (V) FEATURE-FILTER 2026-09-17: `mcu_status` (mục thứ ba của cụm này) đã xoá — owner chấm NO. Hai vai
        // `volt_12v*` GIỮ.
        // [ĐO] `double getBatteryVoltage()` BYDAutoOtaDevice.java:87 — Power KHÔNG có method này (chỉ getBatteryLowVoltageState).
        t("volt_12v", "Ắc-quy 12V", "12V battery", "V", ENERGY, VALUE, OVERDRIVE, "BYDAutoOtaDevice.getBatteryVoltage"),
        t("volt_12v_level", "Mức ắc-quy 12V", "12V battery level", "", ENERGY, BADGE, NEEDS_CAR, "BYDAutoBodyworkDevice.getBatteryVoltageLevel"),

        // ── A8. Danh tính / khoá / máy ──────────────────────────────────────────────────────────
        // VIN = ký hiệu ngành, giữ nguyên (spec §6 OQ2).
        t("vin", "Số VIN", "VIN", "", IDENTITY, VALUE, OVERDRIVE, "BYDAutoBodyworkDevice.getAutoVIN"),
        // ⚠ (V) FEATURE-FILTER 2026-09-17: `key_bluetooth` đã xoá — owner chấm NO (và id không resolve trong dump).
        // oil_level: route đúng, [X] on-car nghi engine PHEV ngủ → chốt: nổ máy rồi `hal get`.
        t("oil_level", "Mức dầu", "Oil level", "%", IDENTITY, VALUE, OVERDRIVE, "BYDAutoEngineDevice.getOilLevel"),

        // ── A9. Giải trí — KHÔNG qua HAL BYDAuto ────────────────────────────────────────────────
        // ⚠ Datum đầu tiên của [Domain.INFOTAINMENT], và cũng là datum đầu tiên đi đường [BindingRoute.Local]:
        // âm lượng là của **Android** (`AudioManager.getStreamVolume(STREAM_MUSIC)` — `BydHalGateway.localGet:126`,
        // đường ĐỌC đã có sẵn từ trước, chỉ thiếu một dòng registry để nút `vol` mượn). Không mượn HAL xe cho việc
        // này: nút `vol` GHI cũng bằng `AudioManager.setStreamVolume`, nên đọc bằng đường khác là tự chuốc lệch.
        t("media_vol", "Âm lượng giải trí", "Media volume", "", INFOTAINMENT, VALUE, PROVEN,
            "AudioManager.getStreamVolume", short = "Âm lượng", shortEn = "Volume"),
    )

    fun byId(id: String): TelemetrySpec? = RegistryIndex.TELEMETRY[id]   // [SOÁT P3] tra băm — xem KDoc RegistryIndex

    /** Datum theo domain (để dựng panel/HOME nhóm §D). */
    fun byDomain(domain: Domain): List<TelemetrySpec> = ALL.filter { it.domain == domain }

    /** Tập domain đang có datum (dùng test phủ 8 domain). */
    fun domains(): Set<Domain> = ALL.map { it.domain }.toSet()
}
