package com.byd.clusternav.modules.clustercast.simplified

/**
 * ═══ 3.1 · R1/R4 — "Ý ĐỊNH" của bốn thanh −/+ khung chiếu (thuần, test off-device) ═══════════════════════════════════
 *
 * Spec `docs/specs/kachi-301-cast-steps-dialog-ia.html` §2.1, §4.1.
 *
 * ## Bệnh nó chữa [SUY — đọc mã, chưa tái lập trên máy ảo]
 * Bản cũ (`SettingsCastGeometryBlock.nudge`) đọc bản GHIM của phiên, cộng ±20, gửi, rồi đọc lại bản ghim NGAY dòng kế. Nhưng
 * lệnh áp chạy BẤT ĐỒNG BỘ trên executor của coordinator và bản ghim chỉ đổi sau khi `am task resize` xong
 * (`CastSessionPin.resizeFullBody`) ⇒ lần bấm 1 hiện số cũ, lần 2 nhảy lên 40, bấm nhanh hai lần trước khi lệnh xong thì cả hai
 * cùng đọc 0 ⇒ mất một bước.
 *
 * ## Luật: ý định ở MÀN, sự thật ở PHIÊN
 *  • Mỗi ô ([K] = ô khung của màn) giữ một khung ý định. Mỗi cú bấm cộng dồn TRÊN Ý ĐỊNH (không trên bản ghim đang trễ) rồi kẹp
 *    ĐỒNG BỘ bằng đúng phép kẹp của cầu (`ClusterNavBridge.geometryClamp` → [CastGeometryGuard.clampBounds]) ⇒ số trên màn đổi
 *    ngay, đúng ±bước, chạm biên thì đứng ở biên.
 *  • Khi hàng gộp nói ô đã rảnh (áp xong / hỏng / bị bỏ — [CastResizeQueue.busy]) màn [forget] ý định ⇒ số về SỰ THẬT của phiên.
 *    Không có trạng thái màn nói A, cụm là B quá một nhịp áp (R2).
 */
enum class CastEdge {
    LEFT, TOP, RIGHT, BOTTOM;

    fun read(b: CastBounds): Int = when (this) {
        LEFT -> b.left
        TOP -> b.top
        RIGHT -> b.right
        BOTTOM -> b.bottom
    }

    fun write(b: CastBounds, value: Int): CastBounds = when (this) {
        LEFT -> b.copy(left = value)
        TOP -> b.copy(top = value)
        RIGHT -> b.copy(right = value)
        BOTTOM -> b.copy(bottom = value)
    }
}

class CastGeometryIntent<K> {
    private val intents = HashMap<K, CastBounds>()

    /** Khung màn đang hiện cho ô [key]: ý định nếu còn, không thì sự thật [truth]. */
    fun shown(key: K, truth: CastBounds): CastBounds = intents[key] ?: truth

    /**
     * Một cú bấm: mép [edge] của khung đang hiện ± [delta], kẹp bằng [clamp]. Trả khung MỚI cần gửi; `null` khi kẹp xong vẫn
     * y nguyên (đứng ở biên — không gửi lệnh shell thừa).
     */
    fun nudge(key: K, truth: CastBounds, edge: CastEdge, delta: Int, clamp: (CastBounds) -> CastBounds): CastBounds? {
        val base = shown(key, truth)
        val next = clamp(edge.write(base, edge.read(base) + delta))
        if (next == base) return null
        intents[key] = next
        return next
    }

    /** Đặt ý định trực tiếp (nút "Đặt lại": khung gốc của ô). */
    fun set(key: K, bounds: CastBounds) {
        intents[key] = bounds
    }

    /** Bỏ ý định của ô [key] — số trên màn về sự thật (sau khi lệnh xong/hỏng/bị bỏ). */
    fun forget(key: K) {
        intents.remove(key)
    }

    /** Ô [key] còn ý định chưa được sự thật bắt kịp không. */
    fun pending(key: K): Boolean = key in intents
}

/**
 * 3.1 · R4 — bước của một cú −/+ (px trên cụm): 5 (canh mép mịn) · 20 (mặc định cũ) · 50 (đi xa nhanh). Lưu THEO HỒ SƠ ở khoá
 * [KEY] (tệp `simple_cast_prefs`, khai ở `ProfileScopeCluster.CAST_PROFILE_EXTRA_KEYS`).
 */
object CastGeometrySteps {
    const val KEY = "cast_frame_step"
    val OPTIONS: List<Int> = listOf(5, 20, 50)
    const val DEFAULT = 20

    /** Giá trị đọc từ prefs (có thể đến từ tệp nhập) ⇒ một trong [OPTIONS]; lạ/vắng ⇒ [DEFAULT]. */
    fun normalize(raw: Int?): Int = raw?.takeIf { it in OPTIONS } ?: DEFAULT
}

/**
 * 3.1 · R2 — khoá NHẸ của khung đang GHIM trong phiên (mỗi ô một khung). Bộ nghe trang Cài đặt so khoá này RIÊNG với `statusKey`
 * (`statusKey` cố ý bỏ qua bản ghim để không dựng lại trang theo từng lần áp): khoá này đổi ⇒ chỉ đặt lại số của bốn thanh −/+.
 * Ngoài lúc chiếu ⇒ chuỗi rỗng.
 */
fun SimpleCastState.geometryKey(): String = when (this) {
    is SimpleCastState.CastingFull -> (pinned ?: displayConfig).let { "full:${it.bounds}|${it.overscan}" }   // 3.1 · R6: + độ dịch
    is SimpleCastState.CastingSplit -> "split:${left?.pinned?.bounds}|${right?.pinned?.bounds}"
    else -> ""
}
