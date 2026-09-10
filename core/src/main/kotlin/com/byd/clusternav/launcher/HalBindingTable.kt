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
 *  • **command-wrapper / id chưa chắc** (`"StartChargingNowCommand"`, `"ADAS_AVH_STATE"`, `"SET_DR_SOC_TARGET"`,
 *    `"BODYWORK_CMD_HOOD"`, `"NaviInfo.lat"`) → [BindingRoute.None] ⇒ unavailable (null) tới khi đóng grab-list §9.
 *
 * ── Degrade-safe (R9) ────────────────────────────────────────────────────────────────────────────────
 * Off-car / thiếu method / HAL ném → gateway trả null/false → đây trả `null` (đọc) / `null` rc (ghi) ⇒ UI "—".
 * rc == [SENTINEL_NOT_PROVISIONED] / [SENTINEL_INVALID] (feature không có trên trim) cũng ⇒ unavailable.
 * KHÔNG gate an toàn (owner bỏ 2026-09-10): KHÔNG đọc tốc độ/số/permit để chặn ghi.
 */
class HalBindingTable(private val gateway: HalGateway) {

    // ── ĐỌC ──────────────────────────────────────────────────────────────────────────────────────────

    /** Giá trị THÔ (chuỗi) của [id] hoặc null (unavailable/off-car/NEEDS_CAR/sentinel). */
    fun readRaw(id: String): String? {
        val spec = specOf(id) ?: return null
        val raw = when (val r = routeOf(spec.first)) {
            is BindingRoute.NamedMethod -> gateway.getter(r.fqn, r.method, readArg(id))
            is BindingRoute.Feature -> gateway.featureGet(featureDeviceFqn(spec.second), r.id)
            is BindingRoute.Setting -> gateway.settingGet(r.key)
            is BindingRoute.Local -> gateway.localGet(r.target, r.method, readArg(id))
            BindingRoute.None -> null
        } ?: return null
        return if (rawIsSentinel(raw)) null else raw
    }

    /** [id] → Int (parse "int=.. float=.." của EventValue hoặc số thuần); sentinel/absent → null. */
    fun readInt(id: String): Int? = coerceInt(readRaw(id))

    /** [id] → Double (float= của EventValue hoặc số thuần). */
    fun readDouble(id: String): Double? = coerceDouble(readRaw(id))

    /** [id] → Boolean (1/0, true/false, "on"/"off"). */
    fun readBool(id: String): Boolean? = coerceBool(readRaw(id))

    /** [id] → chuỗi hiển thị (bỏ khoảng trắng thừa). */
    fun readString(id: String): String? = readRaw(id)?.trim()?.takeIf { it.isNotEmpty() && it != "null" }

    /** [id] → danh sách Int (vd 8 vùng radar "[0, 1, 2,…]"); không parse được → null. */
    fun readIntList(id: String): List<Int>? {
        val raw = readRaw(id) ?: return null
        val nums = Regex("-?\\d+").findAll(raw.substringAfter('[', raw).substringBeforeLast(']'))
            .mapNotNull { it.value.toIntOrNull() }.toList()
        return nums.takeIf { it.isNotEmpty() }
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
            is BindingRoute.Feature -> gateway.featureSet(featureDeviceFqn(def.domain), r.id, args.firstOrNull() ?: primary)
            is BindingRoute.Setting -> gateway.settingSet(r.key, args.firstOrNull() ?: primary)
            is BindingRoute.Local -> if (gateway.localSet(r.target, r.method, args)) 0L else null
            BindingRoute.None -> null
        }
    }

    // ── Tra registry (id toàn cục duy nhất: telemetry trước, control sau) ──────────────────────────────
    private fun specOf(id: String): Pair<String, Domain>? =
        TelemetryRegistry.byId(id)?.let { it.bindingKey to it.domain }
            ?: ControlRegistry.byId(id)?.let { it.bindingKey to it.domain }

    /**
     * Arg int cho GETTER named-method theo hậu tố id (per-index): kính `getWindowOpenPercent(w)` w=1..4;
     * đèn `getLightStatus(type)` (SIDE=1/L_TURN=4/R_TURN=5/F_FOG=6/R_FOG=7); nhiệt cabin `getTemprature(0)`.
     * Còn lại → null (getter 0-arg).
     */
    private fun readArg(id: String): Int? = when (id) {
        "window_lf" -> 1; "window_rf" -> 2; "window_lr" -> 3; "window_rr" -> 4
        "light_side" -> 1; "light_left_turn" -> 4; "light_right_turn" -> 5
        "light_front_fog" -> 6; "light_rear_fog" -> 7
        "inside_temp" -> 0
        else -> null
    }

    /**
     * Tham số cuối cho GHI named-method (proven, nhiều arg). Còn lại 1 arg = [primary].
     *  • ghế mát/sưởi `setSeatVentilatingState/HeatingState(seatId,state)` → [1(lái), state 2/1] (bật→mức1/tắt);
     *  • sưởi vô-lăng `setSteeringWheelHeatingState(state)` → [2/1];
     *  • kính từng cửa `setBodyWindowCtrlState(window,state)` → [index, 0/1]; tất cả kính → 4× state;
     *  • kính-nhị-phân "window" → cửa lái [1, state]; cốp `setHetchDoorStatus` → [open?1:close?2];
     *  • lọc-ngay/nhớ-ghế/gập-gương (BUTTON) → [1].
     */
    private fun writeArgs(def: ControlDef, primary: Int): IntArray = when (def.id) {
        "seatc", "seath" -> intArrayOf(1, if (primary > 0) 2 else 1)
        "steer_heat" -> intArrayOf(if (primary > 0) 2 else 1)
        "win_lf" -> intArrayOf(1, primary); "win_rf" -> intArrayOf(2, primary)
        "win_lr" -> intArrayOf(3, primary); "win_rr" -> intArrayOf(4, primary)
        "windows_all" -> intArrayOf(primary, primary, primary, primary)
        "window" -> intArrayOf(1, primary)
        "trunk" -> intArrayOf(if (primary > 0) 1 else 2)
        "pm25_clean_now", "seat_memory" -> intArrayOf(1)
        else -> intArrayOf(primary)
    }

    /** FQN thiết bị cho đường feature-id, chọn theo [Domain] (best-effort — id↔device chính xác = grab-list §9). */
    private fun featureDeviceFqn(domain: Domain): String = deviceFqn(
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
        }
    )

    companion object {
        /** rc HAL khi feature KHÔNG provision trên trim (= `Int.MIN_VALUE + 1000`). Đo lặp trên xe owner. */
        const val SENTINEL_NOT_PROVISIONED = -2147482648L

        /** rc HAL "giá trị không hợp lệ" (khác NOT_PROVISIONED đúng 3). */
        const val SENTINEL_INVALID = -2147482645L

        /** rc là sentinel không-provisioned/không-hợp-lệ ⇒ coi như unavailable. */
        fun isSentinelRc(rc: Long?): Boolean = rc == SENTINEL_NOT_PROVISIONED || rc == SENTINEL_INVALID

        /**
         * Phân loại `bindingKey` → [BindingRoute] (thuần, test được):
         *  • rỗng → [BindingRoute.None];
         *  • toàn số (có thể âm) → [BindingRoute.Feature];
         *  • có `.`: tiền tố `BYDAuto…` → [NamedMethod] (FQN suy ra); `AudioManager`/`AutoContainer` → [Local];
         *    còn lại (vd `NaviInfo`) → [None];
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
                    prefix.startsWith("BYDAuto") -> BindingRoute.NamedMethod(deviceFqn(prefix), method)
                    prefix == "AudioManager" || prefix == "AutoContainer" -> BindingRoute.Local(prefix, method)
                    else -> BindingRoute.None
                }
            }
            if (bindingKey.matches(Regex("[a-z][a-z0-9_]*"))) return BindingRoute.Setting(bindingKey)
            return BindingRoute.None
        }

        /** FQN thiết bị BYDAuto từ tên đơn giản: `BYDAutoPM2p5Device` → `android.hardware.bydauto.pm2p5.BYDAutoPM2p5Device`. */
        fun deviceFqn(simpleClass: String): String {
            val seg = simpleClass.removePrefix("BYDAuto").removeSuffix("Device").lowercase()
            return "android.hardware.bydauto.$seg.$simpleClass"
        }

        /** Chuỗi thô có phải sentinel không (feature-id đọc trả int=sentinel). */
        fun rawIsSentinel(raw: String): Boolean = coerceInt(raw)?.toLong().let { isSentinelRc(it) }

        /** Parse Int từ chuỗi thô: ưu tiên `int=<n>` (EventValue), rồi số thuần / phần nguyên của số thực. */
        fun coerceInt(raw: String?): Int? {
            val s = raw?.trim() ?: return null
            Regex("int=(-?\\d+)").find(s)?.let { return it.groupValues[1].toIntOrNull() }
            s.toIntOrNull()?.let { return it }
            return s.toDoubleOrNull()?.let { if (it.isFinite()) Math.round(it).toInt() else null }
        }

        /** Parse Double từ chuỗi thô: ưu tiên `float=<x>` (EventValue), rồi số thuần / `int=`. */
        fun coerceDouble(raw: String?): Double? {
            val s = raw?.trim() ?: return null
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

/** Đường nối HAL đã phân loại cho một `bindingKey`. */
sealed class BindingRoute {
    /** named-method proven trên device BYDAuto ([fqn] = FQN đầy đủ). */
    data class NamedMethod(val fqn: String, val method: String) : BindingRoute()

    /** feature-id số (Overdrive raw) — ghi `set(int[]{id})` / đọc `get(int[]{id})`. */
    data class Feature(val id: Int) : BindingRoute()

    /** car-setting key. */
    data class Setting(val key: String) : BindingRoute()

    /** local Android (AudioManager / AutoContainer) — KHÔNG qua HAL BYDAuto. */
    data class Local(val target: String, val method: String) : BindingRoute()

    /** chưa map được (command-wrapper / id chưa chắc / GPS) → unavailable (grab-list §9). */
    object None : BindingRoute()
}

/**
 * Trừu tượng thao tác HAL/local — để [HalBindingTable] test được off-car (gateway giả). Impl thật = [BydHalGateway]
 * (bọc [com.byd.clusternav.modules.hal.BydHal]). MỌI method degrade-safe: off-car/thiếu/ném → null/false.
 */
interface HalGateway {
    /** Đọc getter named-method (0/1 int arg) → chuỗi giá trị thô, null nếu không đọc được. */
    fun getter(deviceFqn: String, method: String, arg: Int?): String?

    /** Ghi named-method N int → rc (Long), null nếu off-car/thiếu method/ném. */
    fun namedInt(deviceFqn: String, method: String, args: IntArray): Long?

    /** Đọc feature-id (`get(int[]{id})`) → chuỗi giá trị (kiểu "int=.. float=.. buf=.."), null nếu không đọc. */
    fun featureGet(deviceFqn: String, id: Int): String?

    /** Ghi feature-id (`set(int[]{id}, EventValue.intValue)`) → rc (Long), null nếu off-car/ném. */
    fun featureSet(deviceFqn: String, id: Int, value: Int): Long?

    /** Đọc car-setting → chuỗi, null nếu chưa hỗ trợ. */
    fun settingGet(key: String): String?

    /** Ghi car-setting → rc/status (Long), null nếu chưa hỗ trợ. */
    fun settingSet(key: String, value: Int): Long?

    /** Đọc local (AudioManager…) → chuỗi, null nếu không có. */
    fun localGet(target: String, method: String, arg: Int?): String?

    /** Ghi local (AudioManager…) → true nếu thành công. AutoContainer (cast) KHÔNG wire ở đây → false. */
    fun localSet(target: String, method: String, args: IntArray): Boolean
}
