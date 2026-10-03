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
     *  • nhiệt **cài đặt** `getTemprature(AC_TEMPERATURE_MAIN=1)` — ⚠ 1.85 đổi từ `0`: javadoc BYD chỉ nhận
     *    1(MAIN)/2(DEPUTY)/3(REAR)/4(OUT); `0` = `AC_TEMPERATURE_MAIN_DEPUTY` là *type* của đường GHI ⇒ đọc trả
     *    sentinel (xem KDoc `inside_temp` ở [TelemetryRegistry]);
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
        "inside_temp" -> 1
        // [ĐO xe 2026-09-25] cabin_temp = getTemprature(1) = 24 (area 0 = sentinel -2147482645; area 1/2 = 24).
        "cabin_temp" -> 1
        // H1 · T2 [ĐO xe 2026-09-16]: ghế `get…State(seatID)` — 1 = ghế LÁI (2 = phụ, cùng giá trị lúc đo);
        // sấy kính `getAcDefrostState(area)` — 1 = kính trước · 2 = kính sau (`hal-reads.txt:2-3,22-25`).
        "seat_vent_state" -> 1; "seat_heat_state" -> 1
        // UX5b (owner 2026-09-27) — ghế PHỤ = **cùng getter, seatID 2**. Đây là chỗ DUY NHẤT con số ấy được khai,
        // đúng cơ chế mà cặp sấy kính trước/sau (`area` 1/2) ngay dưới đã dùng từ T2 ⇒ KHÔNG thêm một trường
        // `readArg` vào `TelemetrySpec` (sẽ là **đường thứ hai** cho cùng một việc, CLAUDE.md §4.1), và cũng không
        // thêm nhánh rẽ theo mã ở tầng gọi. Nút ghế phụ (`seatc_r`/`seath_r`) mượn lại đúng số này qua
        // `HalRoutes.readPathOf` (`def.readArg ?: HalBindingTable.readArg(datum.id)`) nên một con số, một chỗ.
        "seat_vent_state_r" -> 2; "seat_heat_state_r" -> 2
        "defrost_front_state" -> 1; "defrost_rear_state" -> 2
        "tyre_p_fl" -> 1; "tyre_p_fr" -> 2; "tyre_p_rl" -> 3; "tyre_p_rr" -> 4
        "door_lf" -> 1; "door_rf" -> 2; "door_lr" -> 3; "door_rr" -> 4
        else -> null
    }

    /**
     * ═══ 1.85 · Datum lấy **MỘT PHẦN TỬ** của getter trả mảng — khôi phục có chủ đích ═════════════════════════
     *
     * Bảng này từng tồn tại rồi bị xoá ở lượt (V) 2026-09-17 vì hai chủ duy nhất của nó (ETA sạc) bị owner chấm NO
     * — và chú thích lúc đó đã ghi sẵn *"cần lại: thêm bảng + nhánh ở `HalBindingTable.readInt`"*. Nay cần lại thật:
     *
     * `BYDAutoPM2p5Device.getPM2p5Value()` trả `int[]` **hai ô** và **javadoc chính thức BYD** khai thứ tự
     * *"first = value **in** auto, second = value **out** of auto"* ⇒ `pm25_value` lấy **[0]** (đường mặc định
     * `firstOfArray`, đã chạy thật trên xe) và `pm25_outside` lấy **[1]**. Cùng một lời gọi HAL, hai datum.
     *
     * ⚠ Chỉ khai datum nào thật sự cần ô ≥ 1: ô [0] đã có đường mặc định, thêm nó vào đây là hai cơ chế cho một
     * việc. Xem [HalBindingTable.coerceIntAt] về việc vì sao giá trị KHÔNG-mảng phải trả `null` thay vì lùi về số
     * thuần — với datum *"ngoài xe"* thì lùi nghĩa là **hiện số trong cabin dưới nhãn ngoài xe**.
     */
    val ARRAY_INDEX: Map<String, Int> = mapOf("pm25_outside" to 1)

    /**
     * FIX286 · SR3 — `windowId` của **cửa sổ trời** trong `getWindowOpenPercent(int)`: [ĐO nguồn OEM]
     * `SunRoofModel.java:113` đọc độ mở nóc bằng `getWindowOpenPercent(5)` (bốn kính là 1..4 — [readArg]). Nút
     * `sunroof` ghi đè tham số đọc bằng số này (`ControlDef.readArg`) và bảy getter chẩn đoán của nó dùng lại đúng số
     * này — một con số, một chỗ. Getter trả gì trên xe có nóc: [CHƯA BIẾT] (OC-SR).
     */
    const val SUNROOF_WINDOW_ID = 5

    // ⚠ (V) FEATURE-FILTER 2026-09-17 — MỘT bảng tra đã xoá cùng chủ của nó, KHÔNG để lại bảng rỗng:
    //  • `BOOL_WHEN_EQUALS` (bool = MỘT giá trị enum) chỉ từng có `is_charging`→2 của `getChargerWorkState()`.
    // Hai id ấy đều bị owner chấm NO. Một bảng rỗng cộng một nhánh `if` không bao giờ vào là dead code (ADAS-PURGE
    // đã gỡ `CarDataAdapter.Gate.ints()` đúng vì lẽ này). Cần lại: thêm bảng + nhánh ở `HalBindingTable.readBool`.
    // ⚠ Bảng `ARRAY_INDEX` thì đã **quay lại** ở 1.85 (bụi mịn ngoài xe) — xem KDoc của nó ngay trên.

    /** `BODYWORK_STATE_UNDEFINED = 255` [ĐO source `BYDAutoBodyworkDevice.java:205`] — xem KDoc [INVALID_VALUES].
     *  Khai TRƯỚC [INVALID_VALUES]: thân `object` khởi tạo theo thứ tự khai, dùng trước là đọc một `null`. */
    private val BODYWORK_UNDEFINED: Set<Int> = setOf(255)

    /**
     * Giá trị "không hợp lệ" riêng từng getter ⇒ unavailable: tầm điện `getElecDrivingRangeValue` trả
     * STATISTIC_ELEC_DRIVING_RANGE_INVALID=1000 / DEFAULT=1023 (BYDAutoStatisticDevice.java:56-57).
     *
     * ## [P1 · SOÁT Opus 2026-09-27] Thân xe: `255` = **CHƯA XÁC ĐỊNH**, không phải "đang mở"
     * [ĐO source] `jadx-tmap/.../bodywork/BYDAutoBodyworkDevice.java:203-205`: `BODYWORK_STATE_CLOSED = 0` ·
     * `BODYWORK_STATE_OPEN = 1` · **`BODYWORK_STATE_UNDEFINED = 255`**. Năm datum dưới đây đọc bằng `readBool`, mà
     * `coerceBool` là `coerceInt(s) > 0` ⇒ `255` thành **`true` = ĐANG MỞ**: cờ Boolean của `CarStatus.Body` xoá mất
     * sentinel **trước khi** `:core` nhìn thấy, nên tầng trên không có cách nào trả *"chưa biết"* (đường AC làm đúng
     * được vì nó giữ mã thô — `TelemetryReadout.stateTable` `ac_mode_auto`). Hậu quả trên xe: một hình cửa MỞ sáng +
     * nhóm *Cửa* đỏ (`GroupBoard.alertIf`) cho một cánh cửa đang đóng, người lái đang chạy phải dừng lại xem.
     *
     * Đóng bằng **dữ liệu**, không bằng một cửa đọc thứ hai: `readRaw` bỏ giá trị ⇒ `readBool` trả `null` ⇒ ô hiện
     * "—" và [HalAbsentCache] cho datum nguội (không hỏi lại 420 lần/phút — K1b).
     *
     * ⚠ Mức bằng chứng: hằng `255` là **[ĐO source]**; việc getter NÀY trả `255` trên xe owner thì **[CHƯA BIẾT]** —
     * cả 4 lượt quét (`carlog-0916/sweep-1.64.json` + ba tệp `sweep-…` trong `perf-oncar-2026-09-26/kachi-logs`) đều
     * đọc `getDoorState = 0` và `getSunroofState = 0`. Vì vậy đây là **lưới an toàn**, không phải một phép vá cho một
     * triệu chứng đã thấy: không một lượt đo nào đổi hành vi vì dòng này.
     */
    val INVALID_VALUES: Map<String, Set<Int>> = mapOf(
        "ev_range_km" to setOf(1000, 1023),
        "door_lf" to BODYWORK_UNDEFINED,
        "door_rf" to BODYWORK_UNDEFINED,
        "door_lr" to BODYWORK_UNDEFINED,
        "door_rr" to BODYWORK_UNDEFINED,
        "sunroof_state" to BODYWORK_UNDEFINED,
    )
}
