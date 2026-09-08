package com.byd.clusternav.comfort

/**
 * GHẾ — LÀM MÁT / SƯỞI TỰ ĐỘNG · pure model (không Android, unit-test off-car được).
 *
 * ── NGUỒN (RE app OEM `com.byd.airconditioning`, module `airseating`, đã đối chiếu on-car 2026-09-06) ──
 * Ghế điều khiển qua **`BYDAutoSettingDevice`** (= [com.byd.clusternav.modules.hal.BydHal] `SETTING`) bằng
 * method-TÊN, **KHÔNG** qua AC device và **KHÔNG** raw feature-id:
 *  • Làm mát: `setSeatVentilatingState(int seatID, int state)`
 *  • Sưởi:   `setSeatHeatingState(int seatID, int state)`
 * (Nguồn: `AirSeatingVentilateAndHeatModel` gọi `mBYDAutoSettingDevice.setSeat{Ventilating,Heating}State`,
 * `AirSeatingVentilateAndHeatFragment` gọi seatID **1..4** cho lái/phụ/sau-trái/sau-phải.)
 *
 * ⚠ LỊCH SỬ: bản trước ghi thẳng raw feature-id (họ `0x431010xx`) qua `BYDAutoAcDevice.set(int[], ev)` — on-car
 * (2026-09-06) trả `NOT_PROVISIONED` (rc=-2147482648) NGAY CẢ với ghế trước (id đã RE-proven), nên đường AC +
 * raw-id là SAI trên xe này. Feature-id ghế thật chỉ dùng để ĐĂNG KÝ listener, KHÔNG để GHI — đã bỏ khỏi model.
 *
 * ── Ánh xạ (lựa chọn người dùng → HAL) ───────────────────────────────────────────────────────────
 *  • seatID **1-based**: index-app 0 → HAL 1 (lái), 1 → 2 (phụ), 2 → 3 (sau-trái), 3 → 4 (sau-phải).
 *  • state (RE `AirSeatingVentilateAndHeatModel`): **1=Tắt · 2=Mức1(LOW) · 3=Mức2(HIGH)** — user-level 0/1/2
 *    ↦ state 1/2/3 (xem [stateForLevel]).
 *  • Làm mát và sưởi LOẠI TRỪ NHAU (set method này → MCU reset method kia) ⇒ mỗi ghế chỉ gọi MỘT method
 *    theo mode đang chọn.
 */
object SeatComfort {

    /** Chế độ TOÀN CỤC — chọn MỘT (loại trừ nhau, xe reset cái kia khi set cái này). */
    enum class SeatMode { COOL, HEAT }

    /** Cấp độ người dùng TẮT (0). Mức hoạt động là 1 ("Mức 1") và 2 ("Mức 2"). */
    const val LEVEL_OFF = 0

    // ── Giá trị state của HAL (setSeat{Ventilating,Heating}State) — RE AirSeatingVentilateAndHeatModel ──
    const val STATE_OFF = 1   // VENTILATE_HEAT_STATE_OFF
    const val STATE_L1 = 2    // VENTILATE_HEAT_STATE_LOW  ("Mức 1")
    const val STATE_L2 = 3    // VENTILATE_HEAT_STATE_HIGH ("Mức 2")

    /** user-level (0/1/2) → HAL state (1/2/3). Clamp ngoài dải để không ném (degrade-safe cho pref hỏng). */
    private val STATE: IntArray = intArrayOf(STATE_OFF, STATE_L1, STATE_L2)
    fun stateForLevel(level: Int): Int = STATE[level.coerceIn(0, STATE.size - 1)]

    // ── Tên method trên BYDAutoSettingDevice (applier gọi qua BydHal.callNamedInt) ───────────────
    const val METHOD_VENTILATING = "setSeatVentilatingState"   // (seatID 1..4, state 1..3) — làm mát
    const val METHOD_HEATING = "setSeatHeatingState"           // (seatID 1..4, state 1..3) — sưởi

    /** HAL method cho [mode]: COOL → [METHOD_VENTILATING], HEAT → [METHOD_HEATING]. */
    fun methodFor(mode: SeatMode): String = when (mode) {
        SeatMode.COOL -> METHOD_VENTILATING
        SeatMode.HEAT -> METHOD_HEATING
    }

    /**
     * Một ghế: [index] (0=FL/lái, 1=FR/phụ, 2=RL, 3=RR) + [labelKey] khoá nhãn (MainActivity dịch song ngữ qua
     * `Lang.t`). Không còn feature-id (ghi qua method-tên trên SETTING device).
     */
    data class Seat(val index: Int, val labelKey: String)

    /** Bảng ghế theo thứ tự index-app 0..3. */
    val SEATS: List<Seat> = listOf(
        Seat(0, "driver"),
        Seat(1, "passenger"),
        Seat(2, "rear_left"),
        Seat(3, "rear_right"),
    )

    /**
     * index-app 0-based → HAL seatID **1-based** (1=lái, 2=phụ, 3=sau-trái, 4=sau-phải). UI giữ 0-based; chỉ
     * ánh xạ +1 tại đường GHI HAL (khớp `AirSeatingVentilateAndHeatFragment` gọi 1..4).
     */
    fun seatId(seatIndex: Int): Int = seatIndex + 1

    /**
     * Danh sách chỉ-số ghế theo mẫu xe. Seal (2 ghế trước) → `[0, 1]`; Han (4 ghế) → `[0, 1, 2, 3]`.
     * Đây là NGUỒN SỰ THẬT cho "hiện 2 hay 4 ghế" ở UI và "áp cho ghế nào" ở applier. Han rear (3,4) hỗ trợ.
     */
    fun seatsForModel(isHan: Boolean): List<Int> = if (isHan) listOf(0, 1, 2, 3) else listOf(0, 1)

    /** Số ghế theo mẫu xe (tiện cho UI/detect): Seal=2, Han=4. */
    fun seatCountForModel(isHan: Boolean): Int = seatsForModel(isHan).size

    /**
     * Xe có phải Han không (thuần chuỗi, không phân biệt hoa/thường): tên mẫu chứa "han". Null/rỗng → false
     * (mặc định Seal — 2 ghế) để off-car / mẫu-không-rõ luôn an toàn về phía ÍT ghế hơn.
     */
    fun isHanModel(model: String?): Boolean = model != null && model.contains("han", ignoreCase = true)
}
