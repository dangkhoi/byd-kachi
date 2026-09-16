package com.byd.clusternav.launcher

/**
 * BẢNG NỐI HAL (W1b) — map `id` (telemetry/control) → đường chạm xe THẬT, theo `bindingKey` trong registry
 * ([TelemetryRegistry] / [ControlRegistry], :core). Đây là bộ ĐỊNH TUYẾN + PARSE thuần, mọi lời gọi HAL thật đi
 * qua [HalGateway] (tiêm vào) nên **test được off-car** bằng gateway giả. Adapter thật = [BydHalGateway] (bọc
 * [com.byd.clusternav.modules.hal.BydHal] — TÁI DÙNG reflection sẵn có, KHÔNG mở reflection mới).
 *
 * ── 4 đường (theo dạng bindingKey — xem [routeOf]) ──────────────────────────────────────────────────
 *  (a) **named-method proven** `"BYDAutoXxxDevice.method"` → FQN `android.hardware.bydauto.<seg>.<Class>` +
 *      [HalGateway.getter] (đọc) / [HalGateway.namedInt] (ghi N int). Công thức đã proven ở
 *      [com.byd.clusternav.comfort.SeatComfortApplier] / [com.byd.clusternav.body.BodyworkControl] /
 *      [com.byd.clusternav.comfort.Pm25FilterApplier] / [com.byd.clusternav.SpeedProvider].
 *  (b) **feature-id số decimal** `"501219340"` (Overdrive raw) → `set(int[]{id}, EventValue)` /
 *      `get(int[]{id})` qua [HalGateway.featureSet]/[HalGateway.featureGet]. Thiết bị chọn theo [Domain]
 *      (best-effort — id-chính-xác-thuộc-thiết-bị-nào là grab-list §9 on-car; off-car null nên vô hại).
 *  (c) **car-setting key** `"unit_temperature"` (lowercase snake) → [HalGateway.settingGet]/[settingSet].
 *  (d) **local Android** `"AudioManager.setStreamVolume"` / `"AutoContainer.sendInfo"` → [HalGateway.localGet]/
 *      [localSet] (KHÔNG qua HAL BYDAuto). ⚠ AutoContainer (cast) KHÔNG wire ở đây — cast do
 *      `SimpleCastRuntime` sở hữu (ràng buộc: KHÔNG đụng logic cluster-cast).
 *  • **command-wrapper / id chưa chắc** (`"BODYWORK_CMD_HOOD"`, `"INSTRUMENT_HEADLIGHT_ON_OFF"`, `"UNMAPPED_*"`) và
 *    **GPS** `"NaviInfo.lat"` (BLOCKED-BY-DESIGN — xem [LOCAL_TARGETS]) → [BindingRoute.None] ⇒ unavailable (null).
 *
 * ── Degrade-safe (R9) ────────────────────────────────────────────────────────────────────────────────
 * Off-car / thiếu method / HAL ném → gateway trả null/false → đây trả `null` (đọc) / `null` rc (ghi) ⇒ UI "—".
 * rc == [SENTINEL_NOT_PROVISIONED] / [SENTINEL_INVALID] (feature không có trên trim) cũng ⇒ unavailable.
 * KHÔNG gate an toàn (owner bỏ 2026-09-10): KHÔNG đọc tốc độ/số/permit để chặn ghi.
 */
class HalBindingTable(private val gateway: HalGateway) {

    // ── ĐỌC ──────────────────────────────────────────────────────────────────────────────────────────

    /**
     * Giá trị THÔ (chuỗi) của [id] hoặc null (unavailable/off-car/NEEDS_CAR/sentinel). Giá trị "không hợp lệ" riêng
     * của từng getter ([INVALID_VALUES], vd tầm điện 1000/1023) cũng ⇒ null, để KHÔNG hiện số vô nghĩa lên ô.
     */
    fun readRaw(id: String): String? {
        val spec = specOf(id) ?: return null
        val raw = when (val r = routeOf(spec.first)) {
            is BindingRoute.NamedMethod -> gateway.getter(r.fqn, r.method, readArg(id))
            is BindingRoute.Feature -> gateway.featureGet(deviceForFeature(r.id, id, spec.second), r.id)
            // V3 · R11 — tên hằng: tra giá trị lúc chạy; không tra được ⇒ unavailable (KHÔNG đoán một con số).
            is BindingRoute.FeatureName -> gateway.featureIdByName(r.constName)
                ?.let { fid -> gateway.featureGet(deviceForFeature(fid, id, spec.second), fid) }
            is BindingRoute.Setting -> gateway.settingGet(r.key)
            is BindingRoute.Local -> gateway.localGet(r.target, r.method, readArg(id))
            BindingRoute.None -> null
        } ?: return null
        if (rawIsSentinel(raw)) return null
        val invalid = INVALID_VALUES[id] ?: return raw
        return if (coerceInt(raw)?.let { it in invalid } == true) null else raw
    }

    /**
     * [id] → Int (parse "int=.. float=.." của EventValue hoặc số thuần); sentinel/absent → null. Datum lấy MỘT phần tử
     * của getter trả mảng ([ARRAY_INDEX], vd `getChargeRestTime()[0]`=giờ) đọc đúng chỉ số đó.
     */
    fun readInt(id: String): Int? {
        val idx = ARRAY_INDEX[id] ?: return coerceInt(readRaw(id))
        return readIntList(id)?.getOrNull(idx)
    }

    /** [id] → Double (float= của EventValue hoặc số thuần). */
    fun readDouble(id: String): Double? = coerceDouble(readRaw(id))

    /**
     * [id] → Boolean (1/0, true/false, "on"/"off"). Datum mà "đúng" là MỘT giá trị enum cụ thể ([BOOL_WHEN_EQUALS],
     * vd đang sạc = `getChargerWorkState()==2`) so đúng giá trị đó thay vì `>0` (READY=1/FINISH=3 cũng >0 nhưng KHÔNG sạc).
     */
    fun readBool(id: String): Boolean? {
        val eq = BOOL_WHEN_EQUALS[id] ?: return coerceBool(readRaw(id))
        return coerceInt(readRaw(id))?.let { it == eq }
    }

    /** [id] → chuỗi hiển thị (bỏ khoảng trắng thừa). */
    fun readString(id: String): String? = readRaw(id)?.trim()?.takeIf { it.isNotEmpty() && it != "null" }

    /**
     * [id] → danh sách Int (vd 8 vùng radar "[0, 1, 2,…]" — `BydHal.arrayToStr`); không parse được → null.
     * MỌI token phải là số: `"[I@1a2b3c"` (toString mặc định của mảng) từng lọt qua regex-tìm-chữ-số thành `[1, 2, 3]`
     * ⇒ số rác lên ô. Nay token lạ ⇒ null (unavailable), không đoán.
     */
    fun readIntList(id: String): List<Int>? {
        val raw = readRaw(id) ?: return null
        val body = raw.trim().removePrefix("[").removeSuffix("]").trim()
        if (body.isEmpty()) return null
        return body.split(Regex("[,\\s]+")).map { it.toIntOrNull() ?: return null }
    }

    // ── GHI (control) ─────────────────────────────────────────────────────────────────────────────────

    /**
     * Ghi 1 control [id] với tham số chính [primary] (TOGGLE 0/1 · STEP giá trị · COVER 0/1 · SELECT index ·
     * BUTTON 1). Trả rc (Long) hoặc null nếu unavailable/off-car. KHÔNG gate. Args cuối tính bởi [writeArgs]
     * (named-method nhiều-arg như ghế/kính lấy đúng công thức proven).
     */
    fun write(id: String, primary: Int): Long? {
        val def = ControlRegistry.byId(id) ?: return null
        val args = writeArgs(def, primary)
        return when (val r = routeOf(def.bindingKey)) {
            is BindingRoute.NamedMethod -> gateway.namedInt(r.fqn, r.method, args)
            is BindingRoute.Feature ->
                gateway.featureSet(deviceForFeature(r.id, def), r.id, args.firstOrNull() ?: primary)
            is BindingRoute.FeatureName -> gateway.featureIdByName(r.constName)
                ?.let { fid -> gateway.featureSet(deviceForFeature(fid, def), fid, args.firstOrNull() ?: primary) }
            is BindingRoute.Setting -> gateway.settingSet(r.key, args.firstOrNull() ?: primary)
            is BindingRoute.Local -> if (gateway.localSet(r.target, r.method, args)) 0L else null
            BindingRoute.None -> null
        }
    }

    // ── Tra registry (id toàn cục duy nhất: telemetry trước, control sau) ──────────────────────────────
    private fun specOf(id: String): Pair<String, Domain>? =
        TelemetryRegistry.byId(id)?.let { it.bindingKey to it.domain }
            ?: ControlRegistry.byId(id)?.let { it.bindingKey to it.domain }

    /** FQN thiết bị cho đường feature-id, chọn theo [Domain] (best-effort — id↔device chính xác = grab-list §9). */
    private fun featureDeviceFqn(domain: Domain): String = Companion.featureDeviceFqn(domain)

    /** FQN thiết bị feature-id cho một [def]: ưu tiên ghi đè [ControlDef.halDevice], nếu không thì theo [Domain]. */
    private fun featureDeviceFor(def: ControlDef): String =
        def.halDevice?.let { deviceFqn(it) } ?: featureDeviceFqn(def.domain)

    /**
     * Như trên nhưng tra theo [id] (đường ĐỌC): ưu tiên [TelemetrySpec.halDevice] (§C — thêm 2026-09-15), rồi
     * [ControlDef.halDevice] (id vừa đọc vừa ghi), cuối cùng theo [domain].
     */
    private fun featureDeviceFor(id: String, domain: Domain): String =
        TelemetryRegistry.byId(id)?.let { featureDeviceFor(it) }
            ?: ControlRegistry.byId(id)?.halDevice?.let { deviceFqn(it) }
            ?: featureDeviceFqn(domain)

    /**
     * ═══ V3 · R11(b) — HỎI FRAMEWORK feature-id này thuộc device nào, thay vì đoán theo [Domain] ═════════
     *
     * [ĐO nguồn fw-dl3] `BYDAutoDeviceFeaturesMap.getFeatureIdsFromDevice(int deviceType)` là **bảng thật** của
     * chính chiếc xe: 50+ device (`1000` Ac … `1061` BigData), mỗi cái một `Set<Integer>`. Còn
     * `AbsBYDAutoDevice.checkDeviceFeatures` từ chối đúng khi id **không** nằm trong set của device được gọi —
     * tức câu *"gọi sai device"* trả lời được bằng máy, không cần đoán.
     *
     * Tầng dưới ([HalGateway.deviceForFeature]) trả `null` khi off-car / framework không có bảng ⇒ **lùi về**
     * phép đoán cũ (`halDevice` khai tay, rồi [Domain]). Đó là chủ ý: bảng là một phép **cải thiện**, không phải
     * một cổng — thiếu nó thì mọi thứ chạy y như 1.65, không có gì im lặng tắt đi.
     */
    private fun deviceForFeature(featureId: Int, id: String, domain: Domain): String =
        gateway.deviceForFeature(featureId) ?: featureDeviceFor(id, domain)

    /** Như trên, cho đường GHI (đã có sẵn [ControlDef]). */
    private fun deviceForFeature(featureId: Int, def: ControlDef): String =
        gateway.deviceForFeature(featureId) ?: featureDeviceFor(def)

    companion object {
        /**
         * Arg int cho GETTER named-method theo id (per-index) — THUẦN, khoá bằng `BindingRemediationTest`. Nguồn enum:
         * stub `../jadx-tmap/sources/android/hardware/bydauto/` (file:line):
         *  • kính `getWindowOpenPercent(w)` w=1..4;
         *  • đèn `getLightStatus(type)` BYDAutoLightDevice.java — SIDE=1 · **LOW_BEAM=2 (:56) · HIGH_BEAM=3 (:49)** ·
         *    L_TURN=4 · R_TURN=5 · F_FOG=6 · R_FOG=7;
         *  • nhiệt cabin `getTemprature(0)`;
         *  • áp lốp `getTyrePressureValue(area)` BYDAutoTyreDevice.java:27-30 — LF=1 · RF=2 · LR=3 · RR=4;
         *  • cửa `getDoorState(area)` BYDAutoBodyworkDevice.java:172-176 — LF=1 · RF=2 · LR=3 · RR=4;
         *  • vô-lăng `getSteeringWheelValue(BODYWORK_CMD_STEERING_WHEEL_ANGEL=1)` BYDAutoBodyworkDevice.java:178;
         *  • dây an toàn `getSafetyBeltStatus(area)` BYDAutoSafetyBeltDevice.java:16/15 — MAIN=1 · DEPUTY=2;
         *  • ghế phụ `getPassengerStatus(SAFETY_BELT_PASSENGER_DEPUTY=1)` BYDAutoSafetyBeltDevice.java:27.
         * Còn lại → null (getter 0-arg; `getWheelSpeed()` là 0-arg — BYDAutoSpecialDevice.java:59).
         */
        fun readArg(id: String): Int? = when (id) {
            "window_lf" -> 1; "window_rf" -> 2; "window_lr" -> 3; "window_rr" -> 4
            "light_side" -> 1; "light_low_beam" -> 2; "light_high_beam" -> 3
            "light_left_turn" -> 4; "light_right_turn" -> 5
            "light_front_fog" -> 6; "light_rear_fog" -> 7
            "inside_temp" -> 0
            "tyre_p_fl" -> 1; "tyre_p_fr" -> 2; "tyre_p_rl" -> 3; "tyre_p_rr" -> 4
            "door_lf" -> 1; "door_rf" -> 2; "door_lr" -> 3; "door_rr" -> 4
            "steering_deg" -> 1
            "seatbelt_driver" -> 1; "seatbelt_passenger" -> 2
            "oms_passenger" -> 1
            else -> null
        }

        /**
         * Datum đọc MỘT phần tử của getter trả mảng: `int[] getChargeRestTime()` BYDAutoInstrumentDevice.java:1100 —
         * [0]=giờ · [1]=phút. Gateway (`BydHal.arrayToStr`) trả "[h, m]" ⇒ [readIntList] rồi lấy chỉ số.
         */
        val ARRAY_INDEX: Map<String, Int> = mapOf("charging_eta_hour" to 0, "charging_eta_min" to 1)

        /**
         * Datum bool mà "đúng" = MỘT giá trị enum: đang sạc = `getChargerWorkState()==2` (READY1/START2/FINISH3/
         * TERMINATE4 — `docs/diagnostics/hal-binding-remediation-2026-09-15.md` §Năng lượng).
         */
        val BOOL_WHEN_EQUALS: Map<String, Int> = mapOf("is_charging" to 2)

        /**
         * Giá trị "không hợp lệ" riêng từng getter ⇒ unavailable: tầm điện `getElecDrivingRangeValue` trả
         * STATISTIC_ELEC_DRIVING_RANGE_INVALID=1000 / DEFAULT=1023 (BYDAutoStatisticDevice.java:56-57).
         */
        val INVALID_VALUES: Map<String, Set<Int>> = mapOf("ev_range_km" to setOf(1000, 1023))

        /** FQN thiết bị feature-id cho một [spec] ĐỌC: ưu tiên [TelemetrySpec.halDevice], nếu không thì theo [Domain]. */
        fun featureDeviceFor(spec: TelemetrySpec): String =
            spec.halDevice?.let { deviceFqn(it) } ?: featureDeviceFqn(spec.domain)

        /** rc HAL khi feature KHÔNG provision trên trim (= `Int.MIN_VALUE + 1000`). Đo lặp trên xe owner. */
        const val SENTINEL_NOT_PROVISIONED = -2147482648L

        /** rc HAL "giá trị không hợp lệ" (khác NOT_PROVISIONED đúng 3). */
        const val SENTINEL_INVALID = -2147482645L

        /** rc là sentinel không-provisioned/không-hợp-lệ ⇒ coi như unavailable. */
        fun isSentinelRc(rc: Long?): Boolean = rc == SENTINEL_NOT_PROVISIONED || rc == SENTINEL_INVALID

        /**
         * Tiền tố `bindingKey` đi đường [BindingRoute.Local] (Android, không qua HAL BYDAuto). `LocationManager` (GPS)
         * CỐ Ý không có ở đây — BLOCKED-BY-DESIGN: quyền location đã retire (`DeadReckonRetirementTest` ở :app ghim
         * manifest không xin quyền location), mở lại = quyết định owner.
         */
        val LOCAL_TARGETS: Set<String> = setOf("AudioManager", "AutoContainer")

        /**
         * Tham số cuối cho GHI named-method (proven, nhiều arg). Còn lại 1 arg = [primary].
         *  • ghế mát/sưởi `setSeatVentilatingState/HeatingState(seatId,state)` → [1(lái), state 2/1] (bật→mức1/tắt);
         *  • sưởi vô-lăng `setSteeringWheelHeatingState(state)` → [2/1];
         *  • kính từng cửa `setBodyWindowCtrlState(window,state)` → [index, mở=1/đóng=2] (enum WINDOW_*); tất cả kính → 4× state;
         *  • rèm che nắng feature 0x4F500028 (PERCENT_SET) → [mở=100/đóng=0]; đèn đọc 0x4F50003A → [ON=2/OFF=1];
         *  • kính-nhị-phân "window" → cửa lái [1, state]; cốp `setHetchDoorStatus` → [open?1:close?2];
         *  • **khoá cửa `setDoorLockState(state)` → [khoá?2:mở?1]** (xem ⚠ dưới);
         *  • **mưa-tự-đóng-kính `setRainCloseWindow(state)` → [bật?1:tắt?2]**;
         *  • lọc-ngay/nhớ-ghế/gập-gương (BUTTON) → [1].
         *
         * ## ⚠ [SOÁT P0] Vì sao khoá cửa PHẢI có nhánh riêng
         * [ĐO] 2026-09-11: `lock` ("Khoá xe") và `door` ("Mở cửa") khai **CÙNG** `bindingKey`
         * `BYDAutoDoorlockDevice.setDoorLockState`, và trước bản vá này **cả hai** rơi vào nhánh `else` ⇒ gửi
         * **ĐÚNG CÙNG một byte** cho cùng `primary`. Hai nhãn nghĩa ĐỐI NGHỊCH mà gửi byte y hệt ⇒ ít nhất một
         * cái sai, **chứng minh được không cần xe**. Hệ quả nặng nhất: gói `mac_leave` ("Rời xe") kết bằng
         * `MacroStep("lock", 1)` = đúng byte của `MacroStep("door", 1)` trong `mac_door_light` ⇒ bấm "Rời xe" thì
         * kính đóng, đèn tắt, xe **KHÔNG khoá** — mà kết quả vẫn báo thành công (rc=0) và ô còn sáng như đã khoá.
         *
         * Giá trị lấy từ tài liệu dự án, KHÔNG tự nghĩ ra: `docs/diagnostics/launcher-hal-re-overdrive-2026-09-08.md`
         * §7 (*"state_locked=\"2\", unlocked=\"1\""*) + `docs/diagnostics/kachi-capability-catalog-2026-09-10.md` §196
         * (*"locked=2/unlocked=1"*). Mưa-tự-đóng lấy từ `bodywork-window-trunk-RE-2026-09-06.md` §14
         * (*"setRainCloseWindow(int) (ON=1/OFF=2)"*) — trước bản vá này TẮT gửi `0`, một giá trị không có trong
         * tài liệu nên gần như chắc chắn bị xe bỏ qua.
         *
         * ⚠ Cả hai mã vẫn ở mức **chưa kiểm trên xe** (`OVERDRIVE`/`NEEDS_CAR`): bản vá này sửa chỗ **tự mâu
         * thuẫn nội bộ** (hai nhãn đối nghịch, một byte), KHÔNG hứa rằng xe sẽ nhận lệnh.
         *
         * THUẦN (không đọc gateway) ⇒ kiểm được off-car; [ControlWriteArgsTest] khoá cả họ "hai mã một lệnh".
         */
        fun writeArgs(def: ControlDef, primary: Int): IntArray = when (def.id) {
            "seatc", "seath" -> intArrayOf(1, if (primary > 0) 2 else 1)
            "steer_heat" -> intArrayOf(if (primary > 0) 2 else 1)
            // [ĐO xe 2026-09-15] kính MỞ được, ĐÓNG không. Gốc: state cũ = COVER primary (Đóng=0/Mở=1) — Mở gửi
            // 1 (= WINDOW_OPEN_FULL, chạy), Đóng gửi 0 (= WINDOW_ENABLE/INVALID, KHÔNG phải đóng ⇒ no-op). Enum
            // đúng của BYDAutoBodyworkDevice: WINDOW_OPEN_FULL=1 · WINDOW_CLOSE=2 · WINDOW_STOP=3 (jadx-tmap
            // BYDAutoBodyworkDevice.java:367-381, DL3). ⇒ ánh xạ COVER: Mở(primary>0)→1, Đóng→2.
            // T7 (owner 2026-09-15 "mở 50%"): mức 2 = WINDOW_OPEN_HALF=4 — enum THẬT cùng bảng CLOSE=2/OPEN_FULL=1 đã
            // đo đúng cả 4 kính (jadx-tmap BYDAutoBodyworkDevice.java:378). 0/1 giữ nguyên. NEEDS-ONCAR (1 lệnh):
            // `hal set setBodyWindowCtrlState 1,4` rồi `getWindowOpenPercent(1)` ≈ 50. `windows_all` KHÔNG khai mức 2:
            // `setAllWindowState(a,b,c,d)` nhận CÙNG enum WINDOW_* cho cả 4 ô (OpenBYD gọi `setAllWindowState(s,s,s,s)`
            // — CarControlImpl.java:1513-1515) nên 1/2 là đúng, nhưng OPEN_HALF cho cả 4 kính chưa từng đo ⇒ không hứa.
            "win_lf" -> intArrayOf(1, when (primary) { 2 -> 4; else -> if (primary > 0) 1 else 2 })
            "win_rf" -> intArrayOf(2, when (primary) { 2 -> 4; else -> if (primary > 0) 1 else 2 })
            "win_lr" -> intArrayOf(3, when (primary) { 2 -> 4; else -> if (primary > 0) 1 else 2 })
            "win_rr" -> intArrayOf(4, when (primary) { 2 -> 4; else -> if (primary > 0) 1 else 2 })
            "windows_all" -> (if (primary > 0) 1 else 2).let { intArrayOf(it, it, it, it) }
            // [ĐO] RE 2026-09-14 §1/§5a: `setAcTemperature(type, value, tempSource, unit)` — lái=0, value=°C thô,
            // tempSource=0, unit=1 (Celsius). Vd 22°C → setAcTemperature(0,22,0,1). Thay `setTemprature` (không tồn tại).
            "temp" -> intArrayOf(0, primary, 0, 1)
            "window" -> intArrayOf(1, if (primary > 0) 1 else 2)   // kính lái nhị-phân: cùng enum WINDOW_* (mở=1/đóng=2)
            "trunk" -> intArrayOf(if (primary > 0) 1 else 2)
            "lock" -> intArrayOf(if (primary > 0) 2 else 1)     // khoá = 2 · mở khoá = 1
            "door" -> intArrayOf(1)                             // NÚT BẤM một chiều: mở khoá (1), không có mặt tắt
            "rain_close" -> intArrayOf(if (primary > 0) 1 else 2)
            // [ĐO xe 2026-09-15] rèm "bấm mở CHÚT XÍU". Gốc: feature 1330642984 = 0x4F500028
            // BODYWORK_SUNSHADE_PANEL_PERCENT_SET — nhận PHẦN TRĂM 0..100, không phải 0/1. Gửi 1 = "mở 1%".
            // ⇒ Mở=100%, Đóng=0% (carsettings Body.java:1653 · WINDOW_OPEN_PERCENT_MAX=100).
            // T7: rèm đi đường PERCENT (0..100) nên mức 2 = 50 thẳng, không cần enum.
            "sunshade" -> intArrayOf(when (primary) { 2 -> 50; else -> if (primary > 0) 100 else 0 })
            // [ĐO xe 2026-09-15] đèn đọc on/off tay không ăn (chế-độ-theo-cửa thì ăn — feature KHÁC 0x4F500038).
            // feature 1330643002 = 0x4F50003A SET_INSIDE_LIGHT_STATE_SET, enum INSIGHT_LIGHT_OFF=1 · ON=2
            // (jadx-tmap BYDAutoSettingDevice.java:218-219, DL3). Cũ gửi 0/1 ⇒ không trúng ON=2. ⇒ ON=2, OFF=1.
            "readl" -> intArrayOf(if (primary > 0) 2 else 1)
            "pm25_clean_now", "seat_memory" -> intArrayOf(1)
            // ── Bản vá binding 2026-09-15 (`docs/diagnostics/hal-binding-remediation-2026-09-15.md`) — enum lấy từ stub
            // `../jadx-tmap/sources/android/hardware/bydauto/`, KHÔNG phải 0/1:
            // đèn ban ngày `setDayTimeLightState` — DAYTIME_LIGHT_OPEN=1 / CLOSE=2 (BYDAutoLightDevice.java:10/:8).
            "drl" -> intArrayOf(if (primary > 0) 1 else 2)
            // EV/HEV `setEnergyMode` — index args [EV, HEV] → ENERGY_MODE_EV=1 / HEV=3 (BYDAutoEnergyDevice.java:18/:21).
            "powertrain_mode" -> intArrayOf(if (primary == 0) 1 else 3)
            // chế độ lái `setOperationMode` — index args [Thường, Eco, Thể thao, Tuyết] → NORMAL=3 · ECONOMY=1 · SPORT=2 ·
            // SNOW=4 (BYDAutoEnergyDevice.java:30/:24/:33/:32). Index lạ → NORMAL (không gửi số ngoài enum).
            // ⚠ [SOÁT 2026-09-15 · NEEDS-ONCAR] CÙNG lớp còn một họ hằng **protected** ngược nhau
            // (`ENERGY_OPERATION_MODE_NORMAL=1/ECO=2/SPORT=3`, :26-28). Ta chọn họ **public** `ENERGY_OPERATION_*` vì
            // protected = nội bộ khung, app không gọi tới; nhưng đây là ĐỔI CHẾ ĐỘ LÁI khi xe đang chạy ⇒ chốt bằng
            // 1 lệnh trước khi tin: `hal set --es dev BYDAutoEnergyDevice --es m setOperationMode --es args 2` rồi
            // `hal get --es m getOperationMode` phải trả 2 (Thể thao), không phải 3.
            "drive_mode" -> intArrayOf(when (primary) { 1 -> 1; 2 -> 2; 3 -> 4; else -> 3 })
            // cửa sổ trời `setMoonRoofState` — cùng enum kính mở=1/đóng=2 (OpenBYD CarControlImpl.java:1503-1505).
            "sunroof" -> intArrayOf(if (primary > 0) 1 else 2)
            // sạc ngay `setChargingMode(CHARGE_MODE_IMMEDIATELY=1)` (BYDAutoChargingDevice.java:38) — nút bấm.
            "start_charging" -> intArrayOf(1)
            // mục tiêu sạc `setChargeStopCapacityState` — enum RỜI (BYDAutoChargingDevice.java:42-47), % → mốc gần nhất.
            "target_soc_set" -> intArrayOf(chargeStopCapacityEnum(primary))
            // sạc không dây `setWirelessChargingSwitchState` — CHARGE_WIRELESS_CHARGING_ON=1 / OFF=2 (:61/:60).
            "wireless_charge" -> intArrayOf(if (primary > 0) 1 else 2)
            // camera 360 `setAVMSwitchState` — AVM_FUNCTION_ON=2 / OFF=1 (BYDAutoADASDevice.java:35/:34).
            "cam" -> intArrayOf(if (primary > 0) 2 else 1)
            // NEEDS-ONCAR: `avh` `setAVHState` enum on/off chưa có nguồn ⇒ đi nhánh else (1/0) — chốt trên xe.
            // NEEDS-ONCAR: `camera_view` `setDisplayMode` — gửi index thô, map nhãn↔DISPLAY_MODE_* chưa chốt.
            else -> intArrayOf(primary)
        }

        /** Các mốc % mà HAL nhận cho mục tiêu sạc (BYDAutoChargingDevice.java:42-47), tăng dần. */
        private val CHARGE_STOP_MARKS = listOf(50, 60, 70, 80, 90, 100)

        /**
         * % mục tiêu sạc → enum `CHARGE_STOP_CAPACITY_*` (BYDAutoChargingDevice.java:42-47): 100→1 · 90→2 · 80→3 · 70→4 ·
         * 60→5 · 50→6. Không phải % thô: gửi 80 = giá trị ngoài enum ⇒ xe bỏ qua. % lẻ → mốc GẦN NHẤT, hoà (85) → mốc
         * THẤP hơn (an toàn cho pin); ngoài [50,100] kẹp vào biên. THUẦN — khoá bằng `BindingRemediationTest`.
         */
        fun chargeStopCapacityEnum(percent: Int): Int {
            val p = percent.coerceIn(50, 100)
            val nearest = CHARGE_STOP_MARKS.minBy { kotlin.math.abs(it - p) }   // minBy: hoà → phần tử ĐẦU = mốc thấp
            return 6 - CHARGE_STOP_MARKS.indexOf(nearest)
        }

        /**
         * Phân loại `bindingKey` → [BindingRoute] (thuần, test được):
         *  • rỗng → [BindingRoute.None];
         *  • toàn số (có thể âm) → [BindingRoute.Feature];
         *  • có `.`: tiền tố `BYDAuto…` → [NamedMethod] (FQN suy ra); [LOCAL_TARGETS] → [Local]; còn lại (vd
         *    `NaviInfo` — GPS, BLOCKED-BY-DESIGN) → [None];
         *  • lowercase snake (`unit_temperature`) → [Setting];
         *  • còn lại (UPPER_SNAKE / PascalCase command) → [None] (NEEDS_CAR grab-list).
         */
        fun routeOf(bindingKey: String): BindingRoute {
            if (bindingKey.isBlank()) return BindingRoute.None
            if (bindingKey.matches(Regex("-?\\d+"))) {
                return bindingKey.toIntOrNull()?.let { BindingRoute.Feature(it) } ?: BindingRoute.None
            }
            val dot = bindingKey.indexOf('.')
            if (dot > 0) {
                val prefix = bindingKey.substring(0, dot)
                val method = bindingKey.substring(dot + 1)
                return when {
                    // ⚠ Phải xét TRƯỚC nhánh `BYDAuto…` ngay dưới: `BYDAutoFeatureIds` cũng bắt đầu bằng
                    // "BYDAuto", và [deviceFqn] sẽ biến nó thành `android.hardware.bydauto.featureids.…` — một
                    // lớp không tồn tại ⇒ mọi dòng bind-theo-tên im lặng trở thành "off-car".
                    prefix == FEATURE_IDS_CLASS -> BindingRoute.FeatureName(method)
                    prefix.startsWith("BYDAuto") -> BindingRoute.NamedMethod(deviceFqn(prefix), method)
                    prefix in LOCAL_TARGETS -> BindingRoute.Local(prefix, method)
                    else -> BindingRoute.None
                }
            }
            if (bindingKey.matches(Regex("[a-z][a-z0-9_]*"))) return BindingRoute.Setting(bindingKey)
            return BindingRoute.None
        }

        /**
         * FQN thiết bị cho đường feature-id, chọn theo [Domain] (best-effort — id↔device chính xác = grab-list §9).
         * Tách khỏi instance để [describeWrite] mô tả được đường ghi **thuần** (không cần gateway/xe) cho cầu kiểm thử.
         */
        fun featureDeviceFqn(domain: Domain): String = deviceFqn(
            when (domain) {
                Domain.ENERGY -> "BYDAutoStatisticDevice"
                Domain.DRIVETRAIN -> "BYDAutoSettingDevice"
                Domain.CLIMATE -> "BYDAutoAcDevice"
                Domain.TYRES -> "BYDAutoInstrumentDevice"
                Domain.BODY -> "BYDAutoBodyworkDevice"
                Domain.LIGHTS -> "BYDAutoLightDevice"
                Domain.SAFETY -> "BYDAutoADASDevice"
                Domain.IDENTITY -> "BYDAutoBodyworkDevice"
                Domain.INFOTAINMENT -> "BYDAutoSettingDevice"
            },
        )

        /**
         * Mô tả THUẦN đường GHI của một control cho cầu kiểm thử — KHÔNG chạm gateway/xe, nên tính được off-car và
         * khoá bằng test ở `:core`. Trả (nhãn route, FQN thiết bị) đúng như [write] sẽ định tuyến:
         *  • named-method → `"named:<method>"`, device = FQN suy ra từ prefix;
         *  • feature-id   → `"feature:0x%08x"`, device = [featureDeviceFqn] theo [ControlDef.domain];
         *  • car-setting  → `"setting:<key>"`, device = "";
         *  • local        → `"local:<target>.<method>"`, device = target;
         *  • chưa map      → `"none"`, device = "" (command-wrapper / id chưa chắc — grab-list §9).
         */
        fun describeWrite(def: ControlDef): Pair<String, String> = when (val r = routeOf(def.bindingKey)) {
            is BindingRoute.NamedMethod -> "named:${r.method}" to r.fqn
            is BindingRoute.Feature -> "feature:0x%08x".format(r.id) to
                (def.halDevice?.let { deviceFqn(it) } ?: featureDeviceFqn(def.domain))
            // Mô tả THUẦN ⇒ **không** tra reflection ở đây (hàm này chạy được off-car, đó là cả điểm của nó):
            // nói đúng rằng giá trị sẽ được tra lúc chạy, và device sẽ do bảng của framework quyết.
            is BindingRoute.FeatureName -> "feature_name:${r.constName}" to
                (def.halDevice?.let { deviceFqn(it) } ?: featureDeviceFqn(def.domain))
            is BindingRoute.Setting -> "setting:${r.key}" to ""
            is BindingRoute.Local -> "local:${r.target}.${r.method}" to r.target
            BindingRoute.None -> "none" to ""
        }

        /**
         * Tham số CHÍNH (primary) cho một control theo [ControlDef.kind] khi cầu kiểm thử KHÔNG truyền `--ei v`.
         * TOGGLE/COVER/BUTTON → 1 (bật/mở/bấm — mặt "làm việc" của nút); STEP → giá trị mặc định của nút;
         * SELECT → 0 (lựa chọn đầu). Truyền `v` thì dùng thẳng `v` (đã clamp cho STEP ở [ControlDef.clamp]).
         */
        fun defaultPrimary(def: ControlDef): Int = when (def.kind) {
            ControlKind.STEP -> def.value
            ControlKind.SELECT -> 0
            ControlKind.TOGGLE, ControlKind.COVER, ControlKind.BUTTON -> 1
        }

        /**
         * Tên lớp hằng feature-id của framework BYD. Tiền tố `bindingKey` cho đường [BindingRoute.FeatureName].
         *
         * FQN đầy đủ (`android.hardware.bydauto.BYDAutoFeatureIds`) dựng ở tầng thi hành — nó nằm **thẳng** trong
         * package `bydauto`, không theo công thức `<seg>.<Class>` của [deviceFqn].
         */
        const val FEATURE_IDS_CLASS = "BYDAutoFeatureIds"

        /** FQN của lớp hằng feature-id — một chỗ duy nhất dựng chuỗi này. */
        const val FEATURE_IDS_FQN = "android.hardware.bydauto.$FEATURE_IDS_CLASS"

        /** FQN của bảng feature→device của framework BYD ([HalGateway.deviceForFeature] dùng). */
        const val FEATURES_MAP_FQN = "android.hardware.bydauto.BYDAutoDeviceFeaturesMap"

        /** FQN thiết bị BYDAuto từ tên đơn giản: `BYDAutoPM2p5Device` → `android.hardware.bydauto.pm2p5.BYDAutoPM2p5Device`. */
        fun deviceFqn(simpleClass: String): String {
            val seg = simpleClass.removePrefix("BYDAuto").removeSuffix("Device").lowercase()
            return "android.hardware.bydauto.$seg.$simpleClass"
        }

        /** Chuỗi thô có phải sentinel không (feature-id đọc trả int=sentinel). */
        fun rawIsSentinel(raw: String): Boolean = coerceInt(raw)?.toLong().let { isSentinelRc(it) }

        /**
         * Mảng từ gateway ("[a, b, …]" — `BydHal.arrayToStr`, ≥2 phần tử; 1 phần tử đã là số trần) → phần tử ĐẦU.
         * [ĐO] §B remediation 2026-09-15: getter trả `int[]`/`byte[]` (PM2.5 value/level, wheel_speed…) từng ra
         * `"[I@hash"` ⇒ "—". Không phải mảng → trả nguyên chuỗi. [readIntList] (radar 8 vùng) vẫn đọc cả mảng.
         */
        private fun firstOfArray(s: String): String =
            if (s.startsWith("[")) s.removePrefix("[").substringBefore(',').substringBefore(']').trim() else s

        /**
         * Parse Int từ chuỗi thô: ưu tiên `int=<n>` (EventValue), rồi số thuần, rồi `float=<x>` làm tròn; mảng → [0].
         *
         * Nhánh `float=` [SOÁT 2026-09-15 · P1]: `BydHal.readFeature` **rút** ô sentinel ra khỏi chuỗi (`int=-`) vì
         * `BYDAutoEventValue` khởi tạo cả hai field bằng sentinel — một feature kiểu float hợp lệ vẫn mang
         * `intValue = -999999999`. Không có nhánh này thì mọi datum float-only (đọc bằng [readInt]) thành "—".
         */
        fun coerceInt(raw: String?): Int? {
            val s = raw?.trim()?.let(::firstOfArray) ?: return null
            Regex("int=(-?\\d+)").find(s)?.let { return it.groupValues[1].toIntOrNull() }
            s.toIntOrNull()?.let { return it }
            Regex("float=(-?[0-9.]+)").find(s)?.groupValues?.get(1)?.toDoubleOrNull()
                ?.let { if (it.isFinite()) return Math.round(it).toInt() }
            return s.toDoubleOrNull()?.let { if (it.isFinite()) Math.round(it).toInt() else null }
        }

        /** Parse Double từ chuỗi thô: ưu tiên `float=<x>` (EventValue), rồi số thuần / `int=`; mảng → [0]. */
        fun coerceDouble(raw: String?): Double? {
            val s = raw?.trim()?.let(::firstOfArray) ?: return null
            Regex("float=(-?[0-9.]+)").find(s)?.let { return it.groupValues[1].toDoubleOrNull() }
            s.toDoubleOrNull()?.let { return it }
            return coerceInt(s)?.toDouble()
        }

        /** Parse Boolean: true/false, on/off, 1/0 (>0 = true). */
        fun coerceBool(raw: String?): Boolean? {
            val s = raw?.trim()?.lowercase() ?: return null
            return when (s) {
                "true", "on", "yes" -> true
                "false", "off", "no" -> false
                else -> coerceInt(s)?.let { it > 0 }
            }
        }
    }
}
