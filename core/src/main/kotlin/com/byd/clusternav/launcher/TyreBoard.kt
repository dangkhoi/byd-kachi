package com.byd.clusternav.launcher

/**
 * BẢNG ÁP SUẤT LỐP 4 BÁNH (W4) — phần QUYẾT ĐỊNH, thuần Kotlin (`:core`, cấm `android.*`) ⇒ test off-car.
 * Phần VẼ nằm ở `:app` (ô vẽ tay, cùng lối với vòng đo và sơ đồ ghế).
 *
 * Spec: `docs/specs/kachi-unified-capability-tile.html` §4.4 (R6–R8).
 *
 * ## Dữ liệu đã có (KHÔNG phải đào thêm)
 *  • **4 áp suất** — [EvidenceTier.PROVEN] (đã chạy trên xe owner trong ClusterNav, đọc qua `BYDAutoTyreDevice`).
 *  • **4 nhiệt độ** — [EvidenceTier.NEEDS_CAR] (feature-id số, CHƯA xác nhận trên xe) ⇒ off-car luôn "—".
 * Xe trả **kPa**; hiển thị đi qua lựa chọn đơn vị của người dùng ([UnitPrefs], mặc định `bar`).
 *
 * ## Cái này thay thế gì
 * Widget lốp hiện tại chỉ hiện **khoảng** `min–max` (vd "2.1–2.4") ⇒ biết CÓ bánh non nhưng **không biết bánh nào**.
 * Bảng này hiện **từng bánh một số riêng** theo đúng vị trí trên xe.
 */

/** Trạng thái một bánh. Thứ tự ưu tiên khi nhiều điều kiện cùng đúng: [LOW] ▸ [HIGH] ▸ [UNEVEN] ▸ [OK]. */
enum class TyreStatus {
    /** Chưa đọc được số ⇒ hiện "—", KHÔNG bịa (R7). */ UNKNOWN,
    /** Trong khoảng bình thường. */ OK,
    /** Non hơi — dưới ngưỡng. */ LOW,
    /** Quá căng — trên ngưỡng. */ HIGH,
    /** Số nằm trong khoảng nhưng LỆCH so với các bánh khác (thấp nhất khi độ chênh vượt ngưỡng). */ UNEVEN;

    /** Có cần làm nổi bật cảnh báo không. */
    val alert: Boolean get() = this == LOW || this == HIGH || this == UNEVEN

    /**
     * Chữ NGẮN nói **SAI CÁI GÌ** — đóng nợ gói 2: bảng lốp trước đây cho biết bánh nào có vấn đề (bằng màu) nhưng
     * người xem phải tự so số mới biết là non hay căng. Trả `null` khi không có gì để nói (bình thường / chưa đọc)
     * ⇒ bộ vẽ không hiện chữ nào, không chiếm chỗ.
     *
     * Ngắn có chủ ý: nó nằm trong ô nhỏ cạnh con số, dài là bị cắt.
     */
    val reason: String?
        get() = when (this) {
            LOW -> "non"
            HIGH -> "căng"
            UNEVEN -> "lệch"
            OK, UNKNOWN -> null
        }
}

/** Vị trí bánh — thứ tự cố định để bộ vẽ đặt đúng góc. */
enum class TyreCorner(val label: String, val shortLabel: String) {
    FRONT_LEFT("Trước trái", "TT"),
    FRONT_RIGHT("Trước phải", "TP"),
    REAR_LEFT("Sau trái", "ST"),
    REAR_RIGHT("Sau phải", "SP"),
}

/**
 * Một bánh sau khi quyết định.
 * @property pressureKpa số THÔ từ xe (kPa) — `null` = chưa đọc. Bộ vẽ tự đổi đơn vị qua [UnitFormat].
 * @property tempC nhiệt độ (°C) — `null` = chưa đọc (mức bằng chứng chưa kiểm trên xe).
 */
data class TyreReading(
    val corner: TyreCorner,
    val pressureKpa: Double?,
    val tempC: Int?,
    val status: TyreStatus,
)

/**
 * Quyết định trạng thái 4 bánh từ [CarStatus.Tyres].
 *
 * ## Ngưỡng — QUYẾT ĐỊNH CỦA AGENT, owner sửa được (spec OQ1)
 * Tôi **không biết** số nhà sản xuất khuyến nghị cho đúng đời xe owner nên **không ghi là "chuẩn"**. Ba hằng số dưới
 * đặt ở MỘT chỗ để đổi một dòng sau khi đo số thật trên xe. Chọn dải rộng có chủ ý: thà ít cảnh báo sai hơn là kêu
 * oan mỗi lần trời lạnh.
 */
object TyreBoard {

    /** Dưới mức này = non hơi (bar). Đổi 1 dòng sau khi đo trên xe. */
    const val LOW_BAR = 2.0

    /** Trên mức này = quá căng (bar). */
    const val HIGH_BAR = 3.2

    /** Chênh giữa bánh cao nhất và thấp nhất từ mức này = lệch (bar). */
    const val SPREAD_BAR = 0.3

    /** kPa → bar (xe trả kPa; ngưỡng ở trên viết theo bar cho dễ đọc). */
    private const val KPA_PER_BAR = 100.0

    /**
     * 4 bánh theo thứ tự [TyreCorner]. Luôn trả về ĐÚNG 4 phần tử — bánh chưa đọc được là [TyreStatus.UNKNOWN]
     * (bộ vẽ hiện "—"), KHÔNG bỏ bớt phần tử để bố cục không bị nhảy.
     */
    fun readings(t: CarStatus.Tyres): List<TyreReading> {
        val raw = listOf(
            TyreCorner.FRONT_LEFT to (t.pFlKpa to t.tFlC),
            TyreCorner.FRONT_RIGHT to (t.pFrKpa to t.tFrC),
            TyreCorner.REAR_LEFT to (t.pRlKpa to t.tRlC),
            TyreCorner.REAR_RIGHT to (t.pRrKpa to t.tRrC),
        )
        val bars = raw.mapNotNull { it.second.first }.map { it / KPA_PER_BAR }
        // Độ chênh chỉ có nghĩa khi biết ÍT NHẤT 2 bánh — 1 bánh thì không có gì để so.
        val spread = if (bars.size >= 2) (bars.max() - bars.min()) else 0.0
        val minBar = bars.minOrNull()
        return raw.map { (corner, v) ->
            val kpa = v.first
            TyreReading(
                corner = corner,
                pressureKpa = kpa,
                tempC = v.second,
                status = statusOf(kpa?.div(KPA_PER_BAR), spread, minBar),
            )
        }
    }

    /**
     * Trạng thái một bánh. [spread] = độ chênh toàn bộ (bar), [minBar] = bánh thấp nhất đang biết.
     *
     * Quy tắc lệch: chỉ đánh dấu **bánh THẤP NHẤT**, không đánh dấu bánh cao nhất — vì bánh gây lo là bánh non, còn
     * gắn cảnh báo lên bánh đang đủ hơi thì gây hiểu sai.
     */
    fun statusOf(bar: Double?, spread: Double, minBar: Double?): TyreStatus = when {
        bar == null -> TyreStatus.UNKNOWN
        bar < LOW_BAR -> TyreStatus.LOW
        bar > HIGH_BAR -> TyreStatus.HIGH
        spread >= SPREAD_BAR && minBar != null && bar <= minBar -> TyreStatus.UNEVEN
        else -> TyreStatus.OK
    }

    /** Có bánh nào cần cảnh báo không — cho ô thu nhỏ / chip tóm tắt. */
    fun anyAlert(t: CarStatus.Tyres): Boolean = readings(t).any { it.status.alert }

    /** Mức bằng chứng của phần NHIỆT ĐỘ (chưa kiểm trên xe) ⇒ bộ vẽ gắn dấu "chưa kiểm". */
    val tempTier: EvidenceTier = EvidenceTier.NEEDS_CAR
}
