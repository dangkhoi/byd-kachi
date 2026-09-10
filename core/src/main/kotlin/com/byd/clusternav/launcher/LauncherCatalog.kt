package com.byd.clusternav.launcher

/**
 * CATALOG cho màn **Tuỳ biến** (registry-driven, THUẦN → test JVM) — nguồn cho picker widget + nút. Không kê tay:
 * mọi mục sinh ra từ [WidgetRegistry] / [TelemetryRegistry] / [ControlRegistry]. Thêm 1 dòng registry ⇒ tự có 1 mục
 * chọn được (R1/R7). UI (:app) chỉ render danh sách này thành ô chọn.
 */

/** Một mục widget CHỌN ĐƯỢC (curated `w_*` hoặc telemetry `id`). [needsBadge] ⇒ hiện "chưa kiểm trên xe". */
data class WidgetPick(val id: String, val label: String, val icon: String, val tier: EvidenceTier) {
    val needsBadge: Boolean get() = tier.needsBadge
}

/**
 * CATALOG WIDGET: curated (8 widget prototype, luôn PROVEN) + telemetry (121 datum) gom theo [Domain]. Picker hiện
 * curated trước (mặc định HOME §4.2), rồi các nhóm telemetry cho ai muốn dày thêm.
 */
object WidgetCatalog {

    /** Widget curated (prototype) — luôn coi PROVEN (dựng tay, có off-car "—" sẵn). */
    val CURATED: List<WidgetPick> = WidgetRegistry.ALL.map { WidgetPick(it.id, it.label, it.icon, EvidenceTier.PROVEN) }

    /** Telemetry gom theo domain (thứ tự enum), mỗi datum → [WidgetPick]; icon suy từ domain. Nhóm rỗng bị bỏ. */
    fun telemetryByDomain(): List<Pair<Domain, List<WidgetPick>>> =
        Domain.values().mapNotNull { d ->
            val picks = TelemetryRegistry.byDomain(d).map { WidgetPick(it.id, it.label, iconFor(d), it.tier) }
            if (picks.isEmpty()) null else d to picks
        }

    /** Tra 1 pick theo id (curated trước, telemetry sau) — cho UI dựng nhãn/badge khi id đã nằm trong ô. */
    fun pick(id: String): WidgetPick? =
        CURATED.firstOrNull { it.id == id }
            ?: TelemetryRegistry.byId(id)?.let { WidgetPick(it.id, it.label, iconFor(it.domain), it.tier) }

    /** Icon đại diện cho domain (dùng icon đã có trong bộ vector Kachi). */
    fun iconFor(domain: Domain): String = when (domain) {
        Domain.ENERGY -> "ic-bolt"
        Domain.DRIVETRAIN -> "ic-speed"
        Domain.CLIMATE -> "ic-fan"
        Domain.TYRES -> "ic-tire"
        Domain.BODY -> "ic-window"
        Domain.LIGHTS -> "ic-light"
        Domain.SAFETY -> "ic-grid"
        Domain.IDENTITY -> "ic-grid"
        Domain.INFOTAINMENT -> "ic-cast"
    }
}

/**
 * NHÓM NÚT điều khiển theo [Domain] cho màn Tuỳ biến — ADAS (SAFETY) / chế độ lái (DRIVETRAIN) / HUD (INFOTAINMENT)
 * nằm trong panel RIÊNG của domain đó (KHÔNG ẩn — OQ3). Không gate: mọi nút bật được vào dock.
 */
object ControlPanels {

    /** Mọi domain có ít nhất 1 nút → (domain, danh sách nút). Thứ tự = thứ tự enum [Domain]. */
    fun byDomain(): List<Pair<Domain, List<ControlDef>>> =
        Domain.values().mapNotNull { d ->
            val items = ControlRegistry.byDomain(d)
            if (items.isEmpty()) null else d to items
        }
}

/** Helper THUẦN cho render 1 tile điều khiển (badge tier + xoay SELECT). */
object ControlTileLogic {

    /** Tile có cần badge "chưa kiểm trên xe" không ([EvidenceTier.OVERDRIVE]/[EvidenceTier.DASHCAST]). */
    fun needsBadge(def: ControlDef): Boolean = def.tier.needsBadge

    /** SELECT: chỉ số kế tiếp (vòng). optionCount ≤ 0 → 0. */
    fun nextSelectIndex(current: Int, optionCount: Int): Int =
        if (optionCount <= 0) 0 else (current + 1).mod(optionCount)

    /** Nhãn hiển thị của SELECT theo [index]; ngoài phạm vi → nhãn nút. */
    fun selectLabel(def: ControlDef, index: Int): String = def.args.getOrElse(index) { def.label }
}
