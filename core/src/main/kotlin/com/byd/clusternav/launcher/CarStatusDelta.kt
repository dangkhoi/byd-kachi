package com.byd.clusternav.launcher

/**
 * ═══ 3.1.10 · EVENT-PATH-SCOPED — hai ảnh [CarStatus] khác nhau Ở DATUM NÀO (thuần `:core`) ═══════════════════════════════════
 *
 * [ĐO xe 16/10] sự kiện bụi mịn `0x4f600010` ~1 Hz ⇒ mỗi giây một ảnh [CarStatus] mới ⇒ màn chính đổ lại MỌI bề mặt (ô widget · xe 3D ·
 * thanh nút) dù màn 3D không bày bụi mịn. [ĐO máy ảo 16/10, màn QA2 3D, đổi một datum KHÔNG hiện mỗi giây]: 66 khung / 60 sự kiện.
 *
 * Luật fan-out (một chỗ): bề mặt chỉ đổ lại khi [Delta] chạm tập nó bày ([CarDataDemand.Interest]). Bảng [FIELDS] là ánh xạ
 * `id datum → field` — CHÉP của [CarDataAdapter] (mỗi `g.int("id", x.field)` ở đó là một dòng ở đây), và bản chép **được đo** chứ
 * không được tin: `CarStatusDeltaTest` cho adapter đọc MỘT id rồi đòi [of] trả đúng id ấy (thiếu dòng / chỉ nhầm field ⇒ đỏ), và mọi
 * id adapter đọc phải có dòng. Datum lạ (không có dòng) không bao giờ nằm trong [Delta] ⇒ bên vẽ coi như "không biết" — xem
 * [CarDataDemand.interestOf]: tập nhu cầu có mã lạ ⇒ `null` ⇒ luôn đổ (fail-open, hụt hiệu năng chứ không hụt dữ liệu).
 */
object CarStatusDelta {

    /** Datum đổi + mã nút ([CarStatus.controls]) đổi. Rỗng cả hai ⇒ không bề mặt nào phải vẽ lại. */
    data class Delta(val datums: Set<String>, val controls: Set<String>) {
        val isEmpty: Boolean get() = datums.isEmpty() && controls.isEmpty()

        /** Bề mặt bày [interest] có phải đổ lại không. `null` (không biết bày gì) ⇒ có, khi có bất kỳ đổi nào. */
        fun touches(interest: CarDataDemand.Interest?): Boolean {
            if (isEmpty) return false
            val i = interest ?: return true
            return datums.any { it in i.datums } || controls.any { it in i.controls }
        }
    }

    val NONE = Delta(emptySet(), emptySet())

    private class Group(val part: (CarStatus) -> Any, val fields: Map<String, (CarStatus) -> Any?>)

    /** id datum → field. Thứ tự cụm = thứ tự trong [CarStatus]; so CỤM trước (data class `equals`) rồi mới so từng field. */
    private val GROUPS: List<Group> = listOf(
        Group({ it.energy }, mapOf(
            "soc" to { s -> s.energy.soc }, "ev_range_km" to { s -> s.energy.evRangeKm },
            "fuel_range_km" to { s -> s.energy.fuelRangeKm }, "odometer" to { s -> s.energy.odometerKm },
            "motor_power" to { s -> s.energy.motorPowerKw }, "soh_oem" to { s -> s.energy.sohPct },
            "fuel_pct" to { s -> s.energy.fuelPct }, "trip_km" to { s -> s.energy.tripKm },
            "trip_hours" to { s -> s.energy.tripHours }, "consumption_50km" to { s -> s.energy.consumption50 },
            "volt_12v" to { s -> s.energy.volt12v },
        )),
        Group({ it.drivetrain }, mapOf("speed" to { s -> s.drivetrain.speedKmh }, "gear" to { s -> s.drivetrain.gear })),
        Group({ it.climate }, mapOf(
            "pm25_level" to { s -> s.climate.pm25Level }, "pm25_value" to { s -> s.climate.pm25ValueUgm3 },
            "pm25_outside" to { s -> s.climate.pm25OutsideUgm3 }, "pm25_online" to { s -> s.climate.pm25Online },
            "cabin_temp" to { s -> s.climate.cabinTempC }, "ext_temp" to { s -> s.climate.outsideTempC },
            "ac_on" to { s -> s.climate.acOn }, "ac_wind" to { s -> s.climate.fanLevel },
            "ac_cycle" to { s -> s.climate.recircOn }, "anion_state" to { s -> s.climate.anionOn },
            "inside_temp" to { s -> s.climate.setTempC }, "temp_unit" to { s -> s.climate.tempUnit },
            "seat_vent_state" to { s -> s.climate.seatVentRaw }, "seat_heat_state" to { s -> s.climate.seatHeatRaw },
            "seat_vent_state_r" to { s -> s.climate.seatVentRRaw }, "seat_heat_state_r" to { s -> s.climate.seatHeatRRaw },
            "defrost_front_state" to { s -> s.climate.defrostFrontOn }, "defrost_rear_state" to { s -> s.climate.defrostRearOn },
            "ac_mode_auto" to { s -> s.climate.acModeRaw }, "ac_wind_auto" to { s -> s.climate.acWindAutoRaw },
        )),
        Group({ it.tyres }, mapOf(
            // Áp suất mang luôn mã báo 4092/4093 của CÙNG lượt đọc (`pc*` — 2.93 TYRE-BURST-REASON): một datum, hai field.
            "tyre_p_fl" to { s -> s.tyres.pFlKpa to s.tyres.pcFl }, "tyre_p_fr" to { s -> s.tyres.pFrKpa to s.tyres.pcFr },
            "tyre_p_rl" to { s -> s.tyres.pRlKpa to s.tyres.pcRl }, "tyre_p_rr" to { s -> s.tyres.pRrKpa to s.tyres.pcRr },
            "tyre_t_fl" to { s -> s.tyres.tFlC }, "tyre_t_fr" to { s -> s.tyres.tFrC },
            "tyre_t_rl" to { s -> s.tyres.tRlC }, "tyre_t_rr" to { s -> s.tyres.tRrC },
            "tyre_c_fl" to { s -> s.tyres.cFl }, "tyre_c_fr" to { s -> s.tyres.cFr },
            "tyre_c_rl" to { s -> s.tyres.cRl }, "tyre_c_rr" to { s -> s.tyres.cRr },
            "tyre_ps_fl" to { s -> s.tyres.psFl }, "tyre_ps_fr" to { s -> s.tyres.psFr },
            "tyre_ps_rl" to { s -> s.tyres.psRl }, "tyre_ps_rr" to { s -> s.tyres.psRr },
            "tyre_lk_fl" to { s -> s.tyres.lkFl }, "tyre_lk_fr" to { s -> s.tyres.lkFr },
            "tyre_lk_rl" to { s -> s.tyres.lkRl }, "tyre_lk_rr" to { s -> s.tyres.lkRr },
            "tyre_sys" to { s -> s.tyres.sys },
        )),
        Group({ it.body }, mapOf(
            "window_lf" to { s -> s.body.windowLfPct }, "window_rf" to { s -> s.body.windowRfPct },
            "window_lr" to { s -> s.body.windowLrPct }, "window_rr" to { s -> s.body.windowRrPct },
            "door_lf" to { s -> s.body.doorLfOpen }, "door_rf" to { s -> s.body.doorRfOpen },
            "door_lr" to { s -> s.body.doorLrOpen }, "door_rr" to { s -> s.body.doorRrOpen },
            "tailgate_status" to { s -> s.body.tailgateOpen }, "sunshade_pct" to { s -> s.body.sunshadePct },
            "power_level" to { s -> s.body.powerLevel }, "vehicle_type" to { s -> s.body.vehicleType },
            "sunroof_state" to { s -> s.body.sunroofOpen }, "emergency_alarm" to { s -> s.body.emergencyAlarm },
        )),
        Group({ it.lights }, mapOf(
            "light_low_beam" to { s -> s.lights.lowBeam }, "light_high_beam" to { s -> s.lights.highBeam },
            "light_front_fog" to { s -> s.lights.frontFog }, "light_drl" to { s -> s.lights.drl },
            "headlight_feedback" to { s -> s.lights.headlightMode }, "light_rear_fog" to { s -> s.lights.rearFog },
            "light_left_turn" to { s -> s.lights.leftTurn }, "light_right_turn" to { s -> s.lights.rightTurn },
            "light_side" to { s -> s.lights.sideLight },
        )),
        Group({ it.identity }, mapOf("vin" to { s -> s.identity.vin }, "oil_level" to { s -> s.identity.oilLevelPct })),
        Group({ it.infotainment }, mapOf("media_vol" to { s -> s.infotainment.mediaVolume })),
    )

    /**
     * [next] với mọi cụm BẰNG cụm của [prev] thay bằng CHÍNH đối tượng của [prev] — ảnh phát đi chỉ mang đối tượng mới ở cụm thật sự
     * đổi (so sau đó tắt ở `===`, không giữ hai bản bằng nhau). Bằng nhau toàn phần ⇒ [prev].
     */
    fun share(prev: CarStatus, next: CarStatus): CarStatus {
        if (next == prev) return prev
        return next.copy(   // `copy`, không dựng mới: field thêm sau của CarStatus đi theo [next] (bài học `tailgateOpen`)
            energy = if (next.energy == prev.energy) prev.energy else next.energy,
            drivetrain = if (next.drivetrain == prev.drivetrain) prev.drivetrain else next.drivetrain,
            climate = if (next.climate == prev.climate) prev.climate else next.climate,
            tyres = if (next.tyres == prev.tyres) prev.tyres else next.tyres,
            body = if (next.body == prev.body) prev.body else next.body,
            lights = if (next.lights == prev.lights) prev.lights else next.lights,
            identity = if (next.identity == prev.identity) prev.identity else next.identity,
            infotainment = if (next.infotainment == prev.infotainment) prev.infotainment else next.infotainment,
            controls = if (next.controls == prev.controls) prev.controls else next.controls,
        )
    }

    /** Mọi id datum có dòng — cho bài canh (mọi id adapter đọc phải nằm ở đây). */
    val FIELDS: Set<String> = GROUPS.flatMapTo(LinkedHashSet()) { it.fields.keys }

    /**
     * Datum + nút khác nhau giữa [a] và [b]. Cùng đối tượng / bằng nhau ⇒ [NONE] (một phép `equals`). Chi phí: so ≤ 9 cụm, chỉ đi
     * từng field trong cụm khác nhau (sự kiện bụi mịn: 1 cụm × 20 field).
     */
    fun of(a: CarStatus, b: CarStatus): Delta {
        if (a === b || a == b) return NONE
        val datums = HashSet<String>()
        for (g in GROUPS) {
            if (g.part(a) == g.part(b)) continue
            for ((id, get) in g.fields) if (get(a) != get(b)) datums += id
        }
        val controls = if (a.controls == b.controls) emptySet() else
            (a.controls.keys + b.controls.keys).filterTo(HashSet()) { a.controls[it] != b.controls[it] }
        return Delta(datums, controls)
    }
}
