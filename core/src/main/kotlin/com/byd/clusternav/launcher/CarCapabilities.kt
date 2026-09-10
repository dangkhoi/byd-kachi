package com.byd.clusternav.launcher

/**
 * Năng lực của MỘT id (telemetry hoặc control): đã NỐI binding chưa + mức bằng chứng. UI quyết định badge/mờ theo
 * đây (spec R3).
 *
 * @property id khoá (telemetry [TelemetrySpec.id] hoặc control [ControlDef.id]).
 * @property wired có đường HAL để thử không (`tier != NEEDS_CAR`).
 * @property tier mức bằng chứng ([EvidenceTier]).
 */
data class Capability(val id: String, val wired: Boolean, val tier: EvidenceTier) {
    /** Cần badge "chưa kiểm trên xe" ([EvidenceTier.OVERDRIVE]/[EvidenceTier.DASHCAST]). */
    val needsBadge: Boolean get() = tier.needsBadge
}

/**
 * BẢNG NĂNG LỰC — VIEW *derived* từ [TelemetryRegistry] + [ControlRegistry] (single source of truth, R1). KHÔNG kê
 * tay id nào — mọi capability sinh ra từ tier trong registry, nên thêm 1 dòng registry = tự có capability tương ứng.
 *
 * Quy ước `wired` (R3): PROVEN/OVERDRIVE/DASHCAST = đã nối (có đường thử) ⇒ `wired=true`; NEEDS_CAR = chưa xác nhận
 * trên xe ⇒ `wired=false` (UI "—" + mờ tới khi đóng grab-list spec §9). Badge "chưa kiểm" cho OVERDRIVE/DASHCAST.
 */
object CarCapabilities {

    /** Năng lực mọi telemetry id → [Capability]. */
    val TELEMETRY: Map<String, Capability> =
        TelemetryRegistry.ALL.associate { it.id to Capability(it.id, it.tier.wired, it.tier) }

    /** Năng lực mọi control id → [Capability]. */
    val CONTROL: Map<String, Capability> =
        ControlRegistry.ALL.associate { it.id to Capability(it.id, it.tier.wired, it.tier) }

    /** Gộp cả hai (telemetry + control). Id telemetry và control KHÔNG trùng nhau theo thiết kế. */
    val ALL: Map<String, Capability> = TELEMETRY + CONTROL

    /** Tier của [id] (telemetry trước, control sau) hoặc null nếu không có trong registry nào. */
    fun tierOf(id: String): EvidenceTier? = ALL[id]?.tier

    /** [id] đã nối binding chưa (mọi tier trừ NEEDS_CAR). Id lạ → false. */
    fun isWired(id: String): Boolean = ALL[id]?.wired ?: false

    /** [id] có cần badge "chưa kiểm trên xe" không. Id lạ → false. */
    fun needsBadge(id: String): Boolean = ALL[id]?.needsBadge ?: false

    /** [Capability] của [id] hoặc null nếu không có. */
    fun of(id: String): Capability? = ALL[id]
}
