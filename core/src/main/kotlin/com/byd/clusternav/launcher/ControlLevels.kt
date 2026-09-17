package com.byd.clusternav.launcher

/**
 * ═══ THANG MỨC của những nút KHÔNG chạy 0/1 — bảng DỮ LIỆU, không phải nhánh rẽ theo mã ═══════════════════
 *
 * Ghế mát / ghế sưởi của BYD không trả `0 = tắt · 1 = bật`: getter trả một **mã mức** của khung, và số đó phải đổi
 * sang *"đang ở mức mấy"* trước khi hiện lên ô hay đọc cho người lái nghe.
 *
 * Vì sao là một bảng ở `:core` chứ không phải `if (raw == 3) HIGH` trong bộ dựng ô: cùng lẽ với
 * [ControlDef.readInverted] và [ControlDef.halDevice] (CLAUDE.md §7) — khác biệt giữa các nút, và giữa các **đời xe**,
 * phải lộ ra bằng dữ liệu đo được, không bằng một nhánh mà chỉ người viết nó nhớ. Đời xe sau đo ra thang khác thì sửa
 * một dòng bảng, không ai phải đi tìm cái `if`.
 *
 * ## ⚠ [ĐO xe 2026-09-16 — MỘT phép đo, chưa đủ để gọi là thang đã chốt]
 * Trên xe owner (DL3): màn hình gốc hiện ghế mát **MỨC 2** trong khi
 * `BYDAutoSettingDevice.getSeatVentilatingState(1)` trả về **3**; ghế sưởi đọc **1** khi đang **TẮT**. Suy ra thang
 * `1 = tắt · 2 = mức 1 · 3 = mức 2 · 4 = mức 3` (hằng của khung chạy tới 6 — `BYDAutoSettingDevice.java:326-332`,
 * `:1958-1966`).
 *
 * TODO [CHƯA BIẾT] — thang này đứng trên **đúng một** điểm đo (mức 2 ↔ raw 3) cộng một điểm mốc (tắt ↔ raw 1). Cần
 * **điểm đo thứ hai** trước khi được phép gọi là [ĐO] cho cả thang: trên xe, đặt ghế mát sang một mức KHÁC ở màn BYD
 * gốc rồi đọc lại `getSeatVentilatingState(1)` (spec `kachi-live-state-ux.html` OQ3 · T10). Nếu mức 1 trả về 2 thì
 * thang này đúng; nếu trả về 4 thì thang là 1/4/3/… và bảng dưới phải sửa theo SỐ ĐO, không theo suy luận.
 *
 * ⚠ Tài liệu cũ trong repo còn ghi `OFF = 1 · LOW = 2 · HIGH = 3` cho ghế mát — đó là tên **hằng** của khung, KHÔNG
 * phải thang mức mà màn hình xe đang hiện, và phép đo trên mâu thuẫn với nó (màn hiện "mức 2" mà raw = 3). Chỗ nào
 * trong dự án còn khẳng định 1/2/3 là "tắt/thấp/cao" thì đang nói sai.
 *
 Nay bảng này nằm trên ĐƯỜNG CHẠY THẬT (T2, 2026-09-16): `seatc`/`seath` khai [ControlDef.readKey] trỏ vào
 * `seat_vent_state`/`seat_heat_state`, và [HalBindingTable.readState] gọi [levelOf] để đổi mã khung → mức, rồi quy
 * về 0/1 vì hai nút ấy là [ControlKind.TOGGLE]; [TelemetryReadout] gọi nó để hiện chữ *"Mức 2"*. Lượt trước bảng
 * chỉ có bài kiểm dùng — đúng hình dạng `CastShell.evictVd` mà CLAUDE.md §8 nói tới — nên chỗ này ghi lại mốc đó
 * thay vì xoá dấu vết.
 */
object ControlLevels {

    /**
     * Mã mức thô của khung → **mức người dùng thấy** (0 = tắt · 1 = mức 1 · 2 = mức 2 · …), theo mã nút.
     *
     * Khai bằng danh sách theo THỨ TỰ mức: phần tử thứ `n` là mã thô ứng với mức `n`. Viết thế thì bảng tự nói ra cả
     * số mức lẫn phép đổi, và không thể lệch nhau như hai `Map` rời.
     */
    val RAW_BY_LEVEL: Map<String, List<Int>> = mapOf(
        // ═══ [ĐO xe 2026-09-17 — điểm đo THỨ HAI, thang ghế mát nay đã chốt] ═══════════════════════════════
        // `getSeatVentilatingState(1|2)` cả ghế lái lẫn phụ: **OFF=1 · mức1=2 · mức2=3**, và trim owner **KHÔNG
        // có mức 3** (raw 4 không bao giờ xuất hiện). Bỏ `4` khỏi thang: một mã 4 nếu có sẽ trả `null` (hiện ⚠)
        // đúng hơn là bịa ra "mức 3" mà xe này không có.
        "seatc" to listOf(1, 2, 3),
        // `seath` (ghế sưởi): getter cùng họ ở `BYDAutoSettingDevice` nhưng **CHƯA đo thang trên xe** — giữ
        // [SUY] bốn mức cho tới lượt xe sau (handoff 2026-09-17 §6.7: `seath` chờ đo).
        "seath" to listOf(1, 2, 3, 4),
    )

    /**
     * Mã thô [raw] → mức người dùng, hoặc `null` khi mã không nằm trong thang của nút (trim khác · thang đoán sai ·
     * sentinel đã lọt qua). `null` là **có chủ ý**: hiện ⚠ đúng hơn là làm tròn một mã lạ thành "mức 1".
     */
    fun levelOf(id: String, raw: Int): Int? = RAW_BY_LEVEL[id]?.indexOf(raw)?.takeIf { it >= 0 }

    /**
     * Phép **NGHỊCH ĐẢO** của [levelOf]: mức người dùng → mã thô, hoặc `null` nếu nút không khai thang / mức
     * ngoài thang.
     *
     * ⚠ [SOÁT 1.69 · P3] KDoc cũ ghi *"mã thô để GHI xuống xe"* — **không đúng**: đường ghi thật đi qua
     * `HalBindingTable.writeArgs`, và `grep` cho thấy chỗ gọi duy nhất của hàm này là `ControlLevelsTest`.
     * Hàm ở lại vì nó là một **oracle** tốt (bài kiểm đối chiếu hai chiều khớp nhau), nhưng một KDoc khai một
     * vai trò không có thật là đúng thứ làm lượt grep của CLAUDE.md §8 trả lời sai.
     */
    fun rawOf(id: String, level: Int): Int? = RAW_BY_LEVEL[id]?.getOrNull(level)

    /** Số mức (kể cả "tắt") của một nút; 0 = nút không chạy theo thang mức. */
    fun levelCount(id: String): Int = RAW_BY_LEVEL[id]?.size ?: 0
}
