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
        // 2.88 — `getTyrePressureState(area)` / `getTyreAirLeakState(area)`: CÙNG bộ `area` với áp suất (stub :27-30).
        "tyre_ps_fl" -> 1; "tyre_ps_fr" -> 2; "tyre_ps_rl" -> 3; "tyre_ps_rr" -> 4
        "tyre_lk_fl" -> 1; "tyre_lk_fr" -> 2; "tyre_lk_rl" -> 3; "tyre_lk_rr" -> 4
        "door_lf" -> 1; "door_rf" -> 2; "door_lr" -> 3; "door_rr" -> 4
        // 3.1.6 · R32 — cốp: CÙNG `getDoorState(area)`, area 6 = `BODYWORK_CMD_DOOR_LUGGAGE_DOOR` (xem [TRUNK_DOOR_AREA]).
        "tailgate_status" -> TRUNK_DOOR_AREA
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

    /**
     * 3.1.6 · R32 — `area` của **cốp** trong `getDoorState(int)`: [ĐO nguồn fw 2606] `BYDAutoBodyworkDevice.java:207`
     * `BODYWORK_CMD_DOOR_LUGGAGE_DOOR = 6`, `:715-716` `case 6 → get(BODYWORK_LUGGAGE_DOOR)` (feature 692060186 = 0x2940001A trên
     * CAN-FD). [ĐO logcat xe 14/10] dịch vụ nhắc nhở BYD gọi `getDoorState(6)` ⇒ 1 sau lệnh mở của Kachi, 0 sau lệnh đóng.
     */
    const val TRUNK_DOOR_AREA = 6

    // ⚠ (V) FEATURE-FILTER 2026-09-17 — MỘT bảng tra đã xoá cùng chủ của nó, KHÔNG để lại bảng rỗng:
    //  • `BOOL_WHEN_EQUALS` (bool = MỘT giá trị enum) chỉ từng có `is_charging`→2 của `getChargerWorkState()`.
    // Hai id ấy đều bị owner chấm NO. Một bảng rỗng cộng một nhánh `if` không bao giờ vào là dead code (ADAS-PURGE
    // đã gỡ `CarDataAdapter.Gate.ints()` đúng vì lẽ này). Cần lại: thêm bảng + nhánh ở `HalBindingTable.readBool`.
    // ⚠ Bảng `ARRAY_INDEX` thì đã **quay lại** ở 1.85 (bụi mịn ngoài xe) — xem KDoc của nó ngay trên.

    /**
     * ═══ 3.1.6 · Datum bool đọc từ một ENUM 1=BẬT/2=TẮT — `coerceBool` (`> 0`) đọc NHẦM `2` thành BẬT ═══════════════════
     *
     * Owner 15/10 (xe): *"tắt đèn ban ngày trên 3D ⇒ đèn tắt, rồi trên 3D tự sáng lại, đèn xe thì đã tắt"*. Gốc [ĐO]:
     *  • feature `985661476` = `LIGHT_DAY_RUNNING_LIGHT_AUTO_STATE` (`BYDAutoFeatureIds.java:1683`) trả **1 hoặc 2** — ba lượt quét
     *    trên xe (`sweep-2026092x`) đọc `int=2` và `int=1`; logcat xe 15/10 08:21:52 sau `setDayTimeLightState [2]` phát
     *    `event_type =3ac00024, value = 2`;
     *  • thang ấy là `DAYTIME_LIGHT_INVALID=0 · OPEN=1 · CLOSE=2 · UNDEFINED=3` (`BYDAutoLightDevice.java:8-11`);
     *  • [HalBindingTable.coerceBool] là `coerceInt > 0` ⇒ **2 (TẮT) thành `true`** ⇒ hết 3 s giữ lạc quan ([LampHold]) thì số
     *    đọc "BẬT" thắng và đèn 3D sáng lại. Cũng là lý do 2026-09-24 phải bỏ `readKey` của nút `drl` (*"đọc lại không phản ánh
     *    đúng"*) — số đọc đúng, phép GIẢI sai.
     *
     * Bảng = dữ liệu: datum → (mã BẬT, mã TẮT). Mã khác hai số ấy (INVALID/UNDEFINED) ⇒ `null` = "—", KHÔNG đoán.
     * Thêm một datum enum-bool khác = thêm một dòng, có `file:line` của thang.
     */
    val BOOL_ENUM: Map<String, Pair<Int, Int>> = mapOf(
        "light_drl" to (1 to 2),
        // 3.1.6 · R32 — cốp `BODYWORK_STATE_OPEN 1 / CLOSED 0` (`BYDAutoBodyworkDevice.java:239-241` fw 2606): mã khác (255
        // UNDEFINED · số lạ) ⇒ `null`, không phải "đang mở" như `coerceBool` (`> 0`) sẽ đọc.
        "tailgate_status" to (1 to 0),
    )

    /** Giải mã [code] của datum enum-bool [id] theo [BOOL_ENUM]; `null` khi [id] không trong bảng HOẶC mã ngoài cặp (BẬT, TẮT). */
    fun enumBool(id: String, code: Int?): Boolean? {
        val (on, off) = BOOL_ENUM[id] ?: return null
        return when (code) { on -> true; off -> false; else -> null }
    }

    /** `BODYWORK_STATE_UNDEFINED = 255` [ĐO source `BYDAutoBodyworkDevice.java:205`] — xem KDoc [INVALID_VALUES].
     *  Khai TRƯỚC [INVALID_VALUES]: thân `object` khởi tạo theo thứ tự khai, dùng trước là đọc một `null`. */
    private val BODYWORK_UNDEFINED: Set<Int> = setOf(255)

    /** 2.88 — `COLOR_INVALID = 0` của màu lốp cụm đồng hồ ([TyreJudge.COLOUR_INVALID]): cụm chưa có màu ⇒ `null`
     *  ⇒ chip lùi sang mã TPMS; KHÔNG nguội (getter đã trả lời — [INVALID_IS_PENDING], soát Pass 2). Khai TRƯỚC [INVALID_VALUES]. */
    private val TYRE_COLOUR_INVALID: Set<Int> = setOf(TyreJudge.COLOUR_INVALID)

    /** 2.88 · bốn MÃ OEM trong dải số áp suất lốp (`TyreJudge.PRESSURE_BURST..PRESSURE_DEFAULT` — [ĐO source] L3
     *  `TyreCardView.java:34-37`): lọc ở đây ⇒ chip · bảng · ô nhóm · ô nhỏ · giọng nói cùng một luật. Khai TRƯỚC [INVALID_VALUES]. */
    private val TYRE_PRESSURE_CODES: Set<Int> = setOf(
        TyreJudge.PRESSURE_BURST, TyreJudge.PRESSURE_ABNORMAL, TyreJudge.PRESSURE_NONE, TyreJudge.PRESSURE_DEFAULT,
    )

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
        // 3.1.6 · R32 — nút `trunk` đọc qua datum này bằng `readState` (Int, không qua [BOOL_ENUM]) ⇒ lọc 255 ở đây nữa.
        "tailgate_status" to BODYWORK_UNDEFINED,
    ) + TyreIds.COLOUR.associateWith { TYRE_COLOUR_INVALID } + TyreIds.PRESSURE.associateWith { TYRE_PRESSURE_CODES }

    /**
     * ═══ 2.88 · soát Pass 2 — mã [INVALID_VALUES] nghĩa là *"CHƯA có số"*, không phải *"xe không có"* ═══════════════
     *
     * Datum ở đây: getter trả một mã không-có-số thì vẫn HIỆN "—" như mọi datum khác ([HalBindingTable.readRaw]), nhưng
     * nhịp poll ([CarDataAdapter]) **không** cho [HalAbsentCache] nguội vì nó — getter đã TRẢ LỜI. Màu lốp cụm 0
     * (`COLOR_INVALID`) và áp suất 4094/4095 = *chưa có số* (lúc vừa nổ máy, cảm biến TPMS chưa phát); 4092/4093 = mã
     * BÁO của chính xe (nổ lốp / bất thường) — cũng là lời đáp của getter nên cũng không nguội [ĐO L3
     * `TyreCardView.java:34-37,141-156`: BURST 4092 · ABNORMAL 4093 · NONE 4094; giá trị trên xe owner CHƯA đo, spec
     * §6.1]. Màu (đỏ/vàng) của hai mã báo vẫn đến từ màu cụm (M1); chữ lý do "nổ lốp"/"bất thường" (2.93
     * `TYRE-BURST-REASON`) đọc mã từ CHÍNH lượt đọc này (`CarDataAdapter` cổng `code`, `CarStatus.Tyres.pcFl`…). Cho nguội
     * thì giãn 60 s → … → 10 phút: lốp đã có số mà chip/bảng vẫn "—" / XÁM thêm vài phút, và trong lúc màu cụm nguội
     * phép phán lùi sang mã TPMS (M2–M6) dù cụm đã có lời. Getter VẮNG thật (sentinel / không tra được tên) vẫn nguội.
     *
     * Không mở cho cửa/tầm điện: chúng giữ quyết định nguội của soát 09-27 (255 = chưa xác định). Ngân sách K1 đo lại
     * ở `TyreChipTest` (R7 K1 — ca màu cụm 0: 144 lượt/phút).
     */
    val INVALID_IS_PENDING: Set<String> = (TyreIds.COLOUR + TyreIds.PRESSURE).toSet()
}
