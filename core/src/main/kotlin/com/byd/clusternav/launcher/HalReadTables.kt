package com.byd.clusternav.launcher

/**
 * ═══ BỐN BẢNG TRA của đường **ĐỌC** — thuần dữ liệu, tách khỏi `HalBindingTable.kt` ngày 2026-09-16 ══════════
 *
 * Tách vì trần 500 dòng (CLAUDE.md §4.1), và tách theo **vai** như lần tách `HalRoutes.kt` trước đó: tệp kia là bộ
 * **định tuyến + parse** *có gateway trong tay*, còn các bảng dưới đây chỉ là **số đo chép từ stub HAL / từ xe** —
 * mỗi dòng một `file:line` hoặc một lần đo. Chúng đổi khi đo lại một chiếc xe, không đổi khi sửa cách gọi HAL.
 *
 * ⚠ Hành vi KHÔNG đổi: `HalBindingTable.readArg` / `.INVALID_VALUES` vẫn là đường gọi công khai (companion uỷ
 * quyền xuống đây) nên mọi call-site và bài kiểm cũ còn nguyên chữ ký. (Hai bảng `ARRAY_INDEX`/`BOOL_WHEN_EQUALS`
 * đã xoá ở lượt (V) 2026-09-17 — xem chú thích tại chỗ.)
 */
object HalReadTables {

    /**
     * Arg int cho GETTER named-method theo id (per-index) — THUẦN, khoá bằng `BindingRemediationTest`. Nguồn enum:
     * stub `../jadx-tmap/sources/android/hardware/bydauto/` (file:line):
     *  • kính `getWindowOpenPercent(w)` w=1..4;
     *  • đèn `getLightStatus(type)` BYDAutoLightDevice.java — SIDE=1 · **LOW_BEAM=2 (:56) · HIGH_BEAM=3 (:49)** ·
     *    L_TURN=4 · R_TURN=5 · F_FOG=6 · R_FOG=7;
     *  • nhiệt cabin `getTemprature(0)`;
     *  • áp lốp `getTyrePressureValue(area)` BYDAutoTyreDevice.java:27-30 — LF=1 · RF=2 · LR=3 · RR=4;
     *  • cửa `getDoorState(area)` BYDAutoBodyworkDevice.java:172-176 — LF=1 · RF=2 · LR=3 · RR=4;
     *  • vô-lăng `getSteeringWheelValue(BODYWORK_CMD_STEERING_WHEEL_ANGEL=1)` BYDAutoBodyworkDevice.java:178.
     * Còn lại → null (getter 0-arg; `getWheelSpeed()` là 0-arg — BYDAutoSpecialDevice.java:59).
     */
    fun readArg(id: String): Int? = when (id) {
        "window_lf" -> 1; "window_rf" -> 2; "window_lr" -> 3; "window_rr" -> 4
        "light_side" -> 1; "light_low_beam" -> 2; "light_high_beam" -> 3
        "light_left_turn" -> 4; "light_right_turn" -> 5
        "light_front_fog" -> 6; "light_rear_fog" -> 7
        "inside_temp" -> 0
        // H1 · T2 [ĐO xe 2026-09-16]: ghế `get…State(seatID)` — 1 = ghế LÁI (2 = phụ, cùng giá trị lúc đo);
        // sấy kính `getAcDefrostState(area)` — 1 = kính trước · 2 = kính sau (`hal-reads.txt:2-3,22-25`).
        "seat_vent_state" -> 1; "seat_heat_state" -> 1
        "defrost_front_state" -> 1; "defrost_rear_state" -> 2
        "tyre_p_fl" -> 1; "tyre_p_fr" -> 2; "tyre_p_rl" -> 3; "tyre_p_rr" -> 4
        "door_lf" -> 1; "door_rf" -> 2; "door_lr" -> 3; "door_rr" -> 4
        "steering_deg" -> 1
        else -> null
    }

    // ⚠ (V) FEATURE-FILTER 2026-09-17 — HAI BẢNG TRA ĐÃ XOÁ CÙNG CHỦ CỦA CHÚNG, KHÔNG để lại bảng rỗng:
    //  • `ARRAY_INDEX` (datum lấy MỘT phần tử của getter trả mảng) chỉ từng có `charging_eta_hour`→0 và
    //    `charging_eta_min`→1 của `int[] getChargeRestTime()`;
    //  • `BOOL_WHEN_EQUALS` (bool = MỘT giá trị enum) chỉ từng có `is_charging`→2 của `getChargerWorkState()`.
    // Cả bốn id đều bị owner chấm NO. Một bảng rỗng cộng một nhánh `if` không bao giờ vào là dead code (ADAS-PURGE
    // đã gỡ `CarDataAdapter.Gate.ints()` đúng vì lẽ này). Cần lại: thêm bảng + nhánh ở `HalBindingTable.readInt` /
    // `.readBool` — cả hai chỗ đều là 2 dòng.

    /**
     * Giá trị "không hợp lệ" riêng từng getter ⇒ unavailable: tầm điện `getElecDrivingRangeValue` trả
     * STATISTIC_ELEC_DRIVING_RANGE_INVALID=1000 / DEFAULT=1023 (BYDAutoStatisticDevice.java:56-57).
     */
    val INVALID_VALUES: Map<String, Set<Int>> = mapOf("ev_range_km" to setOf(1000, 1023))
}
