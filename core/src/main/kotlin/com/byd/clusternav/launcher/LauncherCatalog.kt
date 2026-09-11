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
            val picks = TelemetryRegistry.byDomain(d).map { WidgetPick(it.id, it.label, CapabilityIcons.forTelemetry(it.id, d), it.tier) }
            if (picks.isEmpty()) null else d to picks
        }

    /** Tra 1 pick theo id (curated trước, telemetry sau) — cho UI dựng nhãn/badge khi id đã nằm trong ô. */
    fun pick(id: String): WidgetPick? =
        CURATED.firstOrNull { it.id == id }
            ?: TelemetryRegistry.byId(id)?.let { WidgetPick(it.id, it.label, CapabilityIcons.forTelemetry(it.id, it.domain), it.tier) }

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

/**
 * ═══ RW0 · Ô KHẢ NĂNG HỢP NHẤT ═══════════════════════════════════════════════════════════════════════════════
 *
 * Loại một "khả năng xe": **ĐỌC** (chỉ xem) hay **HÀNH ĐỘNG** (bấm được). Owner chốt 2026-09-10 (backlog nhóm 3):
 * cả hai phải đặt được ở 3 vùng — thanh trên · ô giữa màn · thanh nút.
 */
enum class CapabilityKind {
    /** Thông tin đọc từ xe — hiển thị, KHÔNG bấm. Nguồn: [TelemetryRegistry] hoặc [WidgetRegistry] (widget dựng tay). */
    READ,
    /** Hành động ghi vào xe — bấm được. Nguồn: [ControlRegistry]. */
    WRITE,
}

/**
 * Một khả năng CHỌN ĐƯỢC để đặt vào 3 vùng — gộp cả đọc lẫn hành động về MỘT hình dạng, để UI chỉ cần một danh
 * sách và một bộ dựng ô.
 *
 * @property domain nhóm hiển thị; `null` với 8 widget dựng tay (chúng không thuộc nhóm nào).
 * @property curated `true` = widget dựng tay (có bố cục riêng, đẹp hơn ô chung — cố ý KHÔNG gộp vào registry).
 */
data class CapabilityPick(
    val id: String,
    val label: String,
    val icon: String,
    val tier: EvidenceTier,
    val kind: CapabilityKind,
    val domain: Domain?,
    val curated: Boolean = false,
) {
    val needsBadge: Boolean get() = tier.needsBadge

    /**
     * Nhãn để HIỂN THỊ. Bằng [label] trong hầu hết trường hợp; chỉ thêm gợi ý loại khi nhãn đó **bị trùng** giữa
     * mục ĐỌC và HÀNH ĐỘNG — xem [CapabilityCatalog.collidingLabels].
     *
     * [ĐO] 2026-09-11: từ gói 2, bảng chọn bày CẢ hai loại trong cùng một lưới ⇒ **18 nhãn trùng nhau** lộ ra
     * (vd hai ô đều ghi "Kính trước-trái": một cái để XEM độ mở %, một cái để BẤM đóng/mở). Trước gói 2 hai loại
     * nằm ở hai màn khác nhau nên trùng không sao. Chỉ thêm gợi ý ở chỗ trùng — thêm cho cả 187 mục là nhiễu.
     */
    val displayLabel: String
        get() = if (label in CapabilityCatalog.collidingLabels()) "$label · $kindHint" else label

    /**
     * Gợi ý loại, chỉ dùng khi nhãn bị trùng. Thứ tự xét quan trọng: **widget dựng tay trước**, vì nó cũng là ĐỌC
     * nên nếu xét theo loại trước thì nó và mục đọc thô sẽ ra cùng một gợi ý ⇒ vẫn không phân biệt được.
     * [ĐO] ca thật: widget "Tốc độ" (thẻ dựng tay) và mục đọc "Tốc độ" (số thô) — cùng là ĐỌC.
     */
    private val kindHint: String
        get() = when {
            curated -> "thẻ"
            kind == CapabilityKind.READ -> "xem"
            else -> "bấm"
        }
}

/**
 * TRA CỨU KHẢ NĂNG — lớp mỏng nằm TRÊN ba bộ đăng ký, KHÔNG trộn dữ liệu của chúng.
 *
 * ## Vì sao một không gian mã PHẲNG là an toàn (và vì sao phải khoá lại)
 * [ĐO] 2026-09-10: 123 mã telemetry + 64 mã control + 8 mã widget dựng tay = **195 mã, giao nhau RỖNG ở cả 3 cặp**.
 * Nhờ vậy tra cứu chỉ cần `id` ⇒ **KHÔNG phải chuyển đổi cấu hình người dùng đã lưu** (ô + thanh nút đang lưu mã
 * trần). Nhưng "hôm nay không trùng" KHÔNG phải bảo đảm: thêm một nút trùng tên một datum sẽ gây **nối chéo âm
 * thầm** (ô hiện số trong khi người dùng tưởng bấm được, hoặc ngược lại). Vì thế [collisions] tồn tại và bị test
 * khoá — thêm mã trùng ⇒ test ĐỎ ngay.
 *
 * Thứ tự tra: widget dựng tay → telemetry → control (xác định, không phụ thuộc việc không-trùng ở trên).
 */
object CapabilityCatalog {

    /** [CapabilityKind] của [id], hoặc `null` nếu mã không thuộc bộ đăng ký nào (mã cũ đã xoá / rác trong prefs). */
    fun kindOf(id: String): CapabilityKind? = when {
        WidgetRegistry.byId(id) != null -> CapabilityKind.READ
        TelemetryRegistry.byId(id) != null -> CapabilityKind.READ
        ControlRegistry.byId(id) != null -> CapabilityKind.WRITE
        // W2: GÓI LỆNH cũng là HÀNH ĐỘNG (nó tác động vào xe), nên tự động đặt được ở cả 3 vùng như mọi nút khác
        // — không cần code đặt-chỗ mới. Đây là giá trị cụ thể của nền RW0.
        ActionMacros.byId(id) != null -> CapabilityKind.WRITE
        else -> null
    }

    /** `true` nếu [id] là hành động bấm được — dùng ở chỗ quyết định dựng ô loại nào. */
    fun isWrite(id: String): Boolean = kindOf(id) == CapabilityKind.WRITE

    /** Một khả năng theo [id], hoặc `null` nếu mã lạ (⇒ chỗ gọi suy giảm an toàn, KHÔNG sập). */
    fun pick(id: String): CapabilityPick? {
        WidgetRegistry.byId(id)?.let {
            return CapabilityPick(it.id, it.label, it.icon, EvidenceTier.PROVEN, CapabilityKind.READ, null, curated = true)
        }
        TelemetryRegistry.byId(id)?.let {
            return CapabilityPick(it.id, it.label, CapabilityIcons.forTelemetry(it.id, it.domain), it.tier, CapabilityKind.READ, it.domain)
        }
        ControlRegistry.byId(id)?.let {
            return CapabilityPick(it.id, it.label, it.icon, it.tier, CapabilityKind.WRITE, it.domain)
        }
        ActionMacros.byId(id)?.let {
            // Mức bằng chứng của gói = THẤP NHẤT trong các bước ⇒ dấu "chưa kiểm" chảy ra UI đúng, không hứa quá.
            return CapabilityPick(it.id, it.label, it.icon, it.tier(), CapabilityKind.WRITE, it.domain)
        }
        return null
    }

    /** MỌI khả năng: widget dựng tay → telemetry → control (thứ tự khai trong từng bộ được giữ). */
    fun all(): List<CapabilityPick> = buildList {
        WidgetRegistry.ALL.forEach {
            add(CapabilityPick(it.id, it.label, it.icon, EvidenceTier.PROVEN, CapabilityKind.READ, null, curated = true))
        }
        TelemetryRegistry.ALL.forEach {
            add(CapabilityPick(it.id, it.label, CapabilityIcons.forTelemetry(it.id, it.domain), it.tier, CapabilityKind.READ, it.domain))
        }
        ControlRegistry.ALL.forEach {
            add(CapabilityPick(it.id, it.label, it.icon, it.tier, CapabilityKind.WRITE, it.domain))
        }
        ActionMacros.ALL.forEach {
            add(CapabilityPick(it.id, it.label, it.icon, it.tier(), CapabilityKind.WRITE, it.domain))
        }
    }

    /**
     * Gom theo nhóm cho màn chọn: mỗi nhóm có phần ĐỌC rồi phần HÀNH ĐỘNG (đúng tinh thần "một ô khả năng" — người
     * dùng thấy cùng chỗ cả thứ xem được lẫn thứ bấm được). Nhóm rỗng bị bỏ. Widget dựng tay KHÔNG vào đây (không
     * thuộc nhóm nào) — UI hiện chúng riêng ở đầu, xem [WidgetCatalog.CURATED].
     */
    fun byDomain(): List<Pair<Domain, List<CapabilityPick>>> {
        // `all()` dựng lại CẢ 195 mục mỗi lần gọi ⇒ gọi trong vòng lặp domain là 8 lượt dựng (1560 đối tượng) cho
        // một lần mở màn chọn. Dựng MỘT lần rồi lọc: cùng kết quả, cùng thứ tự.
        val everything = all()
        return Domain.values().mapNotNull { d ->
            val items = everything.filter { it.domain == d }
            if (items.isEmpty()) null else d to items
        }
    }

    /**
     * Nhãn xuất hiện NHIỀU HƠN MỘT LẦN trên toàn bộ khả năng (không chỉ giữa đọc↔hành động: [ĐO] còn có ca cùng
     * loại, vd widget "Tốc độ" dựng tay và mục đọc "Tốc độ" thô — cả hai đều ĐỌC).
     *
     * Tính một lần rồi giữ, vì [CapabilityPick.displayLabel] gọi nó cho TỪNG ô khi dựng lưới 187 ô — tính lại mỗi
     * lần sẽ quét toàn bộ bộ đăng ký 187 lần cho một lần mở bảng.
     */
    fun collidingLabels(): Set<String> = collidingLabelsCache

    private val collidingLabelsCache: Set<String> by lazy {
        (WidgetRegistry.ALL.map { it.label } + TelemetryRegistry.ALL.map { it.label } +
            ControlRegistry.ALL.map { it.label } + ActionMacros.ALL.map { it.label })
            .groupBy { it }.filterValues { it.size > 1 }.keys
    }

    /**
     * Mã xuất hiện ở NHIỀU HƠN MỘT bộ đăng ký. **Phải luôn rỗng** — bị test khoá (spec R5).
     * Trùng mã = nối chéo âm thầm giữa "xem" và "bấm", loại lỗi rất khó lần ra từ hiện tượng.
     */
    fun collisions(): List<String> {
        val w = WidgetRegistry.ALL.map { it.id }
        val t = TelemetryRegistry.ALL.map { it.id }
        val c = ControlRegistry.ALL.map { it.id }
        val m = ActionMacros.ALL.map { it.id }
        return (w + t + c + m).groupBy { it }.filterValues { it.size > 1 }.keys.sorted()
    }
}
