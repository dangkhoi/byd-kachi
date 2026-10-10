package com.byd.clusternav.launcher.testbridge

/**
 * ═══ T-BRIDGE · MÃ HOÁ/KIỂM MẢNG ISA-BẢN-ĐỒ (`sendIsaDataToAdas(int[12])`) — THUẦN, KIỂM OFF-DEVICE ══════════════
 *
 * Nguồn: `docs/diagnostics/re-map-speedlimit-2606-2026-10-10.md` §1.2 (bảng 12 ô) + §6.2 (tải thử + mảng trả).
 * Mức bằng chứng của BỐ CỤC: [ĐO TelenavISA `QiankunISADataConverter.java:475-492,554-623`; framework
 * `BYDAutoStatisticDevice.java:610-617`]. Đây là đường "giới hạn tốc độ THEO BẢN ĐỒ" chính thức của BYD: IVI gửi
 * **12 ô một lần** cho ECU ADAS qua `BYDAutoStatisticDevice.sendIsaDataToAdas(int[12])`.
 *
 * ## Vì sao tách ra `:core` (thuần)
 * Phần dễ sai nhất của op `setisa`/`setarr` là **phân tích + kiểm dải**, không phải lời gọi reflection: một ô
 * tràn bit (gửi `50` thô vào ô 5-bit thay vì mã `10`) là đúng lỗi mà các lượt test 08/10 mắc phải (§5 của RE doc),
 * và nó không lộ ra off-car nếu logic nằm trong `BroadcastReceiver`. Ở `:core` thì `IsaMapArrayTest` đi hết bảng.
 *
 * ## ⚠ Mã hoá ô tốc độ KHÔNG nằm ở đây — người gọi tự mã hoá
 * Ô 0/6/7 là **mã 5-bit = km/h ÷ 5** (50 km/h → `10`; 5/7 → 1; >130 → 27; 30 = không giới hạn; 31 = không hợp lệ).
 * Op `setisa` nhận mảng **đã mã hoá** (vd `10`, không phải `50`) rồi chỉ KIỂM từng ô có vừa độ rộng bit không —
 * cố ý không "giúp" bằng cách chia 5, vì ô 0 và ô 11 (map status) có miền nghĩa khác nhau, "giúp" một ô là mở
 * đường đoán cả 12. Việc chia 5 do script/người đo làm tường minh (xem `scripts/vehicle/isa-map-sendarr-2606.sh`).
 */
object IsaMapArray {

    /** Số ô cố định của `sendIsaDataToAdas` (fw 2606). */
    const val FIELD_COUNT = 12

    /**
     * Độ rộng bit (CẬN TRÊN) của từng ô, theo THỨ TỰ mảng `int[12]` — RE doc §1.2. Giá trị hợp lệ mỗi ô là
     * `0..(2^bits − 1)`. Đây là cận trên suy từ khoảng cách feature-id (RE doc đánh dấu [SUY mạnh]); nếu `navopen
     * readcfg` (R5) cho bố cục bit THẬT khác đi thì sửa Ở ĐÂY, một chỗ.
     */
    val FIELD_BITS = intArrayOf(5, 2, 2, 2, 13, 13, 5, 5, 3, 8, 2, 4)

    /** Tên ASCII từng ô (cho lời đáp/lỗi đọc được) — khớp cột "Tên" RE doc §1.2. */
    val FIELD_NAMES = listOf(
        "current_road_speed_limit",                   // 0 · mã km/h÷5
        "time_conditional_speed_limit",               // 1
        "current_road_time_conditional_speed_limit",  // 2
        "whether_front_fork_ramp",                    // 3
        "front_ramp_distance",                        // 4 · mét; 8191 = không hợp lệ
        "current_road_speed_limit_type",              // 5
        "rainy_condition_speed_limit",                // 6 · mã km/h÷5
        "snow_condition_speed_limit",                 // 7 · mã km/h÷5
        "road_type",                                  // 8
        "country_code",                               // 9 · bảng Telenav; 255 = không hợp lệ
        "road_speed_limit_unit",                      // 10 · 1 km/h · 2 mph
        "map_status",                                 // 11 · 1 active · 0 invalid
    )

    /**
     * Tải thử T1 (RE doc §6.2): 50 km/h, loại rõ ràng, bản đồ ACTIVE, đơn vị km/h, quốc gia "không hợp lệ".
     * `[10,2,2,0,8191,2,31,31,2,255,1,1]`. Là **hằng tham chiếu**, KHÔNG tự chạy (không có auto-run — chỉ là mẫu
     * để script/test đối chiếu).
     */
    val SAMPLE_T1 = intArrayOf(10, 2, 2, 0, 8191, 2, 31, 31, 2, 255, 1, 1)

    /**
     * Mảng TRẢ / XOÁ (RE doc §6.2): map status INVALID (`0`) + mọi giới hạn về mặc định Telenav
     * `[31,2,2,0,8191,0,0,0,0,255,1,0]`. Op đọc-lại gate sau khi gửi mảng này phải thấy biển biến mất.
     */
    val RESTORE = intArrayOf(31, 2, 2, 0, 8191, 0, 0, 0, 0, 255, 1, 0)

    /** Giá trị hợp lệ tối đa của ô [index] (`2^bits − 1`). */
    fun maxValue(index: Int): Int = (1 shl FIELD_BITS[index]) - 1

    /** Kết quả phân tích: mảng dùng được, hoặc **mã lỗi ASCII** (cùng luật `TestBridgeParse.Err`). */
    sealed interface Parsed {
        data class Ok(val values: IntArray) : Parsed
        data class Err(val code: String) : Parsed
    }

    // ── Mã lỗi (ASCII, không dịch) ───────────────────────────────────────────────────────────────

    /** `--es data` rỗng (op `setarr` cần ÍT NHẤT một phần tử). */
    const val ERR_EMPTY = "data_empty"

    /** Một phần tử không phải số nguyên — nối token sai để script biết gõ nhầm chỗ nào. */
    const val ERR_NAN = "data_nan:"

    /** `setisa`: số ô khác 12 — nối số đếm được. */
    const val ERR_LEN = "isa_len:"

    /** `setisa`: một ô vượt dải bit — nối `idx(name)=val>max`. */
    const val ERR_RANGE = "isa_range:"

    /**
     * Phân tích CSV → `IntArray` GENERIC (op `setarr`): bất kỳ độ dài ≥ 1, cho phép âm (một số method mảng nhận
     * giá trị có dấu). Chỉ từ chối rỗng / token không-số. KHÔNG kiểm dải — kiểm dải là việc của [parseIsa].
     */
    fun parseArray(csv: String): Parsed {
        val parts = csv.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.isEmpty()) return Parsed.Err(ERR_EMPTY)
        val out = IntArray(parts.size)
        for (i in parts.indices) {
            out[i] = parts[i].toIntOrNull() ?: return Parsed.Err(ERR_NAN + parts[i])
        }
        return Parsed.Ok(out)
    }

    /**
     * Phân tích + KIỂM mảng ISA 12 ô (op `setisa`): đúng [FIELD_COUNT] phần tử, mỗi ô `0..maxValue(ô)`.
     * Từ chối: sai độ dài ([ERR_LEN]), token không-số ([ERR_NAN]), ô ngoài dải ([ERR_RANGE]).
     */
    fun parseIsa(csv: String): Parsed {
        val parts = csv.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.size != FIELD_COUNT) return Parsed.Err(ERR_LEN + parts.size)
        val out = IntArray(FIELD_COUNT)
        for (i in 0 until FIELD_COUNT) {
            val v = parts[i].toIntOrNull() ?: return Parsed.Err(ERR_NAN + parts[i])
            val max = maxValue(i)
            if (v < 0 || v > max) return Parsed.Err("$ERR_RANGE$i(${FIELD_NAMES[i]})=$v>max$max")
            out[i] = v
        }
        return Parsed.Ok(out)
    }
}
