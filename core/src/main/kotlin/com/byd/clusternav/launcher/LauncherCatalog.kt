package com.byd.clusternav.launcher

/**
 * CATALOG cho màn **Tuỳ biến** (registry-driven, THUẦN → test JVM) — nguồn cho picker widget + nút. Không kê tay:
 * mọi mục sinh ra từ [WidgetRegistry] / [TelemetryRegistry] / [ControlRegistry]. Thêm 1 dòng registry ⇒ tự có 1 mục
 * chọn được (R1/R7). UI (:app) chỉ render danh sách này thành ô chọn.
 */

/** Một mục widget CHỌN ĐƯỢC (curated `w_*` hoặc telemetry `id`). [needsBadge] ⇒ hiện "chưa kiểm trên xe". */
data class WidgetPick(
    val id: String,
    override val label: String,
    val icon: String,
    val tier: EvidenceTier,
    /** Nhãn tiếng Anh (U5 · T2) — chảy từ [WidgetDef.labelEn] / [TelemetrySpec.labelEn], không gõ lại ở đây. */
    override val labelEn: String? = null,
) : Localized {
    val needsBadge: Boolean get() = tier.needsBadge

}

/**
 * CATALOG WIDGET: curated (8 widget prototype, luôn PROVEN) + telemetry (121 datum) gom theo [Domain]. Picker hiện
 * curated trước (mặc định HOME §4.2), rồi các nhóm telemetry cho ai muốn dày thêm.
 */
object WidgetCatalog {

    /** Widget curated (prototype) — luôn coi PROVEN (dựng tay, có off-car "—" sẵn). */
    val CURATED: List<WidgetPick> =
        WidgetRegistry.ALL.map { WidgetPick(it.id, it.label, it.icon, EvidenceTier.PROVEN, it.labelEn) }

    /** Telemetry gom theo domain (thứ tự enum), mỗi datum → [WidgetPick]; icon suy từ domain. Nhóm rỗng bị bỏ. */
    fun telemetryByDomain(): List<Pair<Domain, List<WidgetPick>>> =
        Domain.values().mapNotNull { d ->
            val picks = TelemetryRegistry.byDomain(d)
                .map { WidgetPick(it.id, it.label, CapabilityIcons.forTelemetry(it.id, d), it.tier, it.labelEn) }
            if (picks.isEmpty()) null else d to picks
        }

    /** Tra 1 pick theo id (curated trước, telemetry sau) — cho UI dựng nhãn/badge khi id đã nằm trong ô. */
    fun pick(id: String): WidgetPick? =
        CURATED.firstOrNull { it.id == id }
            ?: TelemetryRegistry.byId(id)?.let {
                WidgetPick(it.id, it.label, CapabilityIcons.forTelemetry(it.id, it.domain), it.tier, it.labelEn)
            }

    /** Icon đại diện cho domain (dùng icon đã có trong bộ vector Kachi). */
    fun iconFor(domain: Domain): String = when (domain) {
        Domain.ENERGY -> "ic-bolt"
        Domain.DRIVETRAIN -> "ic-speed"
        Domain.CLIMATE -> "ic-fan"
        Domain.TYRES -> "ic-tire"
        Domain.BODY -> "ic-window"
        Domain.LIGHTS -> "ic-light"
        // [KIỂM TOÁN UX mục 4a] Lĩnh vực này từng lùi về `ic-grid` (⊞) — cùng hình với widget "Bảng tổng hợp"
        // VÀ với kính cửa, tức một glyph mang ba nghĩa. Nay ⊞ chỉ còn nghĩa "bảng".
        Domain.IDENTITY -> "ic-car"
        Domain.INFOTAINMENT -> "ic-cast"
    }
}

/**
 * NHÓM NÚT điều khiển theo [Domain] cho màn Tuỳ biến — chế độ lái (DRIVETRAIN) / HUD (INFOTAINMENT) nằm trong
 * panel RIÊNG của domain đó (KHÔNG ẩn — OQ3). Không gate: mọi nút bật được vào dock.
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

    /**
     * Nhãn hiển thị của SELECT theo [index]; ngoài phạm vi → nhãn nút.
     *
     * U5 · T2: đọc [ControlDef.displayArgs] và [ControlDef.displayLabel] — cả lựa chọn lẫn đường lùi đều theo ngôn
     * ngữ, nên nút tiếng Anh không hiện ra một lựa chọn tiếng Việt.
     */
    fun selectLabel(def: ControlDef, index: Int): String =
        def.displayArgs.getOrElse(index) { def.displayLabel }
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

    /**
     * ═══ S4 · R12 — HÀNH ĐỘNG CỦA CHÍNH LAUNCHER ═════════════════════════════════════════════════════════
     *
     * Bấm được, nhưng **KHÔNG gửi gì xuống xe**: mở ngăn kéo ứng dụng, mở màn Cài đặt. Nguồn: [LauncherActions].
     *
     * ## Vì sao phải là giá trị thứ BA, dù KDoc [CapabilityCatalog.kindOf] từng viết *"KHÔNG thêm giá trị thứ ba"*
     * Câu cấm đó viết cho ca **NHÓM khả năng** (G1) và lý do của nó là: nhóm vốn là thứ để XEM, nên gán cho nó một
     * loại mới sẽ làm `else -> "bấm"` ở [CapabilityPick.kindHint] và nhánh hai-chiều của `ControlDockView` âm thầm
     * hiểu sai. Ca này **ngược lại**: đây thật sự là thứ để BẤM, chỉ khác đích đến (launcher thay vì xe). Gán tạm
     * nó vào [WRITE] mới là cái bẫy — `ControlDockView` sẽ đi tìm [ControlDef]/[ActionMacro] cho mã đó, không thấy,
     * rồi **không vẽ gì cả mà cũng không báo lỗi** (đúng nhánh đã phải vá cho gói lệnh ở W2).
     *
     * Hệ quả phải chặn ở CHỖ CỦA NÓ, y như G1: thanh trên chỉ nhận [READ] nên loại này bị
     * [TopStripConfig.isChippable] từ chối do cấu tạo — không cần bóp méo loại ở đây.
     */
    LAUNCHER,
}

/**
 * Một khả năng CHỌN ĐƯỢC để đặt vào 3 vùng — gộp cả đọc lẫn hành động về MỘT hình dạng, để UI chỉ cần một danh
 * sách và một bộ dựng ô.
 *
 * @property domain nhóm hiển thị; `null` với 8 widget dựng tay (chúng không thuộc nhóm nào).
 * @property curated `true` = widget dựng tay (có bố cục riêng, đẹp hơn ô chung — cố ý KHÔNG gộp vào registry).
 * @property group `true` = **NHÓM khả năng** ([CapabilityGroups], G1) — nhiều mục rời trong một ô.
 *
 * ⚠ [group] là cờ RIÊNG, KHÔNG dùng lại [curated] dù cả hai đều nghĩa "có bố cục riêng": [curated] nghĩa *"widget
 * dựng tay, không thuộc nhóm nào"* và [TopStripConfig.choices] đang dựa vào đó để loại widget. Nhồi nhóm vào cùng
 * một cờ sẽ làm một cờ mang hai nghĩa — đúng bẫy hai-bản-sao mà dự án đang dọn. Cờ này có mặc định nên mọi chỗ dựng
 * [CapabilityPick] cũ không phải sửa.
 *
 * @property sub dòng phụ nói ô này **gồm gì** (T4) — rỗng với mọi mục rời, vì nhãn của một datum đã tự nói hết
 *   ("Áp lốp trước-trái" không cần giải thích thêm). Chỉ NHÓM có, và chuỗi đó do
 *   [CapabilityGroup.contentLine] ghép (số đếm lấy từ dữ liệu, không chép tay).
 */
data class CapabilityPick(
    val id: String,
    override val label: String,
    val icon: String,
    val tier: EvidenceTier,
    val kind: CapabilityKind,
    val domain: Domain?,
    val curated: Boolean = false,
    val group: Boolean = false,
    val sub: String = "",
    /**
     * Nhãn tiếng Anh (U5 · T2) — **chảy từ bộ đăng ký gốc**, không gõ lại ở đây (một nhãn, một chỗ).
     *
     * ⚠ Riêng ba chip TỔNG HỢP của thanh trên ([TopStripConfig.choices]) thì gõ tại chỗ, vì chúng KHÔNG có dòng
     * registry nào — chúng là chip ghép từ hai datum.
     */
    override val labelEn: String? = null,
) : Localized {
    val needsBadge: Boolean get() = tier.needsBadge

    /**
     * Nhãn để HIỂN THỊ. Bằng [displayLabel] của [Localized] trong hầu hết trường hợp; chỉ thêm gợi ý loại khi nhãn đó
     * **bị trùng** giữa mục ĐỌC và HÀNH ĐỘNG — xem [CapabilityCatalog.collidingLabels].
     *
     * [ĐO] 2026-09-11: từ gói 2, bảng chọn bày CẢ hai loại trong cùng một lưới ⇒ **18 nhãn trùng nhau** lộ ra
     * (vd hai ô đều ghi "Kính trước-trái": một cái để XEM độ mở %, một cái để BẤM đóng/mở). Trước gói 2 hai loại
     * nằm ở hai màn khác nhau nên trùng không sao. Chỉ thêm gợi ý ở chỗ trùng — thêm cho mọi mục là nhiễu.
     *
     * ⚠ Phép **phát hiện trùng vẫn chạy trên nhãn tiếng Việt** ([CapabilityCatalog.collidingLabels] đọc `label`):
     * tiếng Việt là nhãn GỐC, và tập trùng của nó là tập đã được kiểm/khoá bằng test. Nếu đổi sang so nhãn hiện tại
     * thì tập trùng sẽ **đổi theo ngôn ngữ** ⇒ cùng một màn hình lại có/không gợi ý loại tuỳ ngôn ngữ, và bài test
     * *"0 nhãn trùng còn lại"* của gói 2 sẽ nói về một tập khác mỗi lần. Bản dịch nào làm sinh ra trùng MỚI thì
     * `LangCoverageTest` báo ra để người dịch sửa chữ, chứ không tự vá bằng gợi ý loại.
     */
    override val displayLabel: String
        get() = Strings.pick(label, labelEn)

    /**
     * ═══ U6 · GỢI Ý LOẠI RA KHỎI NHÃN CHÍNH ═══════════════════════════════════════════════════════════════════
     *
     * Gợi ý loại của ô này — `""` khi nhãn KHÔNG trùng (phần lớn ô), `"xem"`/`"bấm"`/`"nhóm"`/`"thẻ"` khi trùng.
     *
     * ## Vì sao tách khỏi [displayLabel]
     * [ĐO] soát ảnh 2026-09-12/13: owner đọc được trên lưới các nhãn *"Charge target · view"*, *"Speed · view"*,
     * *"Drive mode · press"*, *"Negative ions · view"* — và gọi đúng tên vấn đề: đó là **thuật ngữ nội bộ lọt vào
     * tên thứ người dùng đang chọn**. Tên một khả năng là *"Mục tiêu sạc"*; *"xem"* là **loại** của ô, không phải
     * một phần của tên. Nhét loại vào nhãn còn ăn mất bề ngang của chính cái tên (nhãn ô chỉ 2 dòng, chữ bị cắt
     * đúng chỗ cần đọc) và làm nhãn tiếng Anh đọc như lỗi dịch.
     *
     * Nhãn giờ là nhãn; loại xuống **dòng phụ** ([displaySub]) với cỡ chữ nhỏ hơn — cùng chỗ nhóm đang nói *"gồm
     * gì"*. Phép **phân biệt** hai ô trùng tên không mất đi, nó chỉ chuyển từ một dòng sang hai dòng, nên bài canh
     * *"0 nhãn hiển thị còn trùng"* nay so **CẶP (nhãn, dòng phụ)** thay vì so mỗi nhãn.
     *
     * ⚠ Phép **phát hiện trùng vẫn chạy trên nhãn tiếng Việt** ([CapabilityCatalog.collidingLabels] đọc `label`):
     * tiếng Việt là nhãn GỐC, và tập trùng của nó là tập đã được kiểm/khoá bằng test. Nếu đổi sang so nhãn hiện tại
     * thì tập trùng sẽ **đổi theo ngôn ngữ** ⇒ cùng một màn hình lại có/không gợi ý loại tuỳ ngôn ngữ. Bản dịch nào
     * làm sinh ra trùng MỚI thì `LangCoverageTest` báo ra để người dịch sửa chữ, chứ không tự vá bằng gợi ý loại.
     */
    val typeHint: String get() = if (label in CapabilityCatalog.collidingLabels()) kindHint else ""

    /**
     * DÒNG PHỤ để hiển thị: gợi ý loại (khi trùng) · nội dung nhóm (khi là nhóm). Rỗng với hầu hết ô rời.
     *
     * Ghép bằng `·` đúng như dấu cũ từng dùng trong nhãn, nên với ô nhóm trùng tên thì người đọc thấy
     * *"nhóm · 4 mục"* — một dòng nói cả loại lẫn nội dung, không phải hai dòng chồng nhau.
     *
     * ⚠ [sub] (trường dữ liệu) vẫn CHỈ nhóm mới có: bài canh *"chỉ NHÓM có dòng phụ"* đọc trường đó, và nó vẫn
     * đúng — thứ thêm ở đây là phần TRÌNH BÀY, tính lúc vẽ, không ghi vào bộ đăng ký.
     */
    val displaySub: String get() = listOf(typeHint, sub).filter { it.isNotEmpty() }.joinToString(" · ")

    /**
     * Gợi ý loại, chỉ dùng khi nhãn bị trùng. Thứ tự xét quan trọng: **nhóm trước, rồi widget dựng tay**, vì cả hai
     * đều là ĐỌC — xét theo [kind] trước thì chúng và mục đọc thô sẽ ra cùng một gợi ý ⇒ vẫn không phân biệt được.
     * [ĐO] ca thật: widget "Tốc độ" (thẻ dựng tay) và mục đọc "Tốc độ" (số thô) — cùng là ĐỌC. Nhóm cần gợi ý riêng
     * vì nó CÓ THỂ trùng nhãn với một mục rời (vd nhóm "Lốp" và một datum áp suất); không có nhánh này thì
     * hai bên cùng ra "· xem" và phép kiểm nhãn-trùng bế tắc thay vì tự giải.
     */
    private val kindHint: String
        get() = when {
            group -> Strings.t("nhóm", "group")
            curated -> Strings.t("thẻ", "card")
            kind == CapabilityKind.READ -> Strings.t("xem", "view")
            // S4 · R12 — [CapabilityKind.LAUNCHER] cố ý rơi vào nhánh này cùng [CapabilityKind.WRITE]: gợi ý loại
            // chỉ hiện khi NHÃN BỊ TRÙNG, và câu nó phải trả lời là *"ô này xem hay bấm"* — "Ứng dụng" là thứ để
            // BẤM. Thêm một gợi ý thứ năm ("launcher") sẽ nói về ĐÍCH ĐẾN, không phải về cách dùng ô.
            else -> Strings.t("bấm", "press")
        }
}

/**
 * TRA CỨU KHẢ NĂNG — lớp mỏng nằm TRÊN ba bộ đăng ký, KHÔNG trộn dữ liệu của chúng.
 *
 * ## Vì sao một không gian mã PHẲNG là an toàn (và vì sao phải khoá lại)
 * [ĐO] 2026-09-10: mã telemetry + mã control + mã widget dựng tay **giao nhau RỖNG ở cả 3 cặp**.
 * Nhờ vậy tra cứu chỉ cần `id` ⇒ **KHÔNG phải chuyển đổi cấu hình người dùng đã lưu** (ô + thanh nút đang lưu mã
 * trần). Nhưng "hôm nay không trùng" KHÔNG phải bảo đảm: thêm một nút trùng tên một datum sẽ gây **nối chéo âm
 * thầm** (ô hiện số trong khi người dùng tưởng bấm được, hoặc ngược lại). Vì thế [collisions] tồn tại và bị test
 * khoá — thêm mã trùng ⇒ test ĐỎ ngay.
 *
 * Thứ tự tra: widget dựng tay → telemetry → control (xác định, không phụ thuộc việc không-trùng ở trên).
 */
object CapabilityCatalog {

    /** Tám ô lốp LẺ — ẩn khỏi bộ chọn, xem [HIDDEN_FROM_PICKER]. */
    private val TYRE_SINGLES = listOf(
        "tyre_p_fl", "tyre_p_fr", "tyre_p_rl", "tyre_p_rr",
        "tyre_t_fl", "tyre_t_fr", "tyre_t_rl", "tyre_t_rr",
    )

    private const val TYRE_SINGLE_WHY =
        "owner 2026-09-16 (bảng feature.xlsx): \"riêng cái lốp (áp suất, nhiệt độ) thì chỉ gôm lại thành 1 " +
            "widget có hình xe đẹp, hiện đủ thông tin rõ ràng\". Tám ô LẺ bày cạnh nhau là tám con số không " +
            "nói được bánh nào ở đâu; nhóm `g_tyres` (hình xe) trả lời đúng câu đó. Ẩn ô lẻ khỏi bộ chọn, " +
            "GIỮ datum: nhóm + widget đọc chúng, và ô của ai đã đặt từ bản trước vẫn chạy."

    /**
     * ═══ U6 · MÃ CÓ THẬT NHƯNG **KHÔNG BÀY** Ở MÀN CHỌN ═══════════════════════════════════════════════════════
     *
     * Mã → lý do cố ý không bày. Cùng khuôn [SettingsCatalog.NOT_SETTINGS]: có danh sách thì phân biệt được
     * *"cố ý ẩn"* với *"quên nối"*; không có danh sách thì mọi mục vắng mặt đều trông như lỗi.
     *
     * ## ⚠⚠ ẨN KHỎI BỘ CHỌN ≠ XOÁ MÃ
     * Mã vẫn tra ra được qua [pick]/[kindOf] — **bắt buộc**, vì mã là KHOÁ LƯU BỀN của người dùng: ai đã đặt ô này
     * từ bản trước thì ô đó phải tiếp tục vẽ ra bình thường. Xoá dòng registry (hoặc đổi mã) sẽ làm ô của họ thành
     * mã lạ ⇒ biến mất không báo. Vì thế chỗ lọc duy nhất là [all] — cửa mà **mọi** màn chọn đi qua (ngăn kéo ·
     * mục Nhóm · lựa chọn thanh trạng thái), chứ không phải xoá ở registry.
     */
    val HIDDEN_FROM_PICKER: Map<String, String> = mapOf(
        "temp_unit" to
            "[ĐO ảnh 2026-09-12] ô \"Đơn vị nhiệt\" trong lưới dữ liệu xe nói ĐÚNG cái mà hàng \"đơn vị nhiệt độ\" " +
                "ở Hiển thị & đơn vị đã nói — và hàng kia còn ĐỔI được, ô này chỉ xem. Hai chỗ cho một thứ thì " +
                "người dùng bấm nhầm chỗ không đổi được. Giữ mã để ô ai đã đặt vẫn chạy.",
        // ⚠⚠ 1.85 · mục `hood` ĐÃ RỜI bảng này vì mã bị **xoá hẳn** khỏi `ControlRegistry` ([ĐO xe 2026-09-20 §4]
        // owner xác nhận xe không có ca-pô điện; 1.66 chỉ ẩn, 1.85 bỏ). Giữ một mục ở đây cho một mã không còn tồn
        // tại là ghim `HIDDEN_FROM_PICKER.size` vào một thứ hư — mà con số ấy đang bị `CapabilityGroupsTest` trừ
        // trong phép đếm tổng, nên nó phải nói đúng. Ô của ai đã đặt `hood` rụng qua `WorkspaceState.sanitized()`,
        // cùng đường mà 19 mã của (V) FEATURE-FILTER đã đi.
        // ── (V) FEATURE-FILTER 2026-09-17 · TÁM Ô LỐP LẺ ────────────────────────────────────────────────
        // Ca thứ BA của bảng này: ẩn vì **có bề mặt tốt hơn cho cùng dữ liệu**, không phải vì trùng
        // (`temp_unit`) hay vì thiếu phần cứng (`hood`, nay đã xoá). Datum GIỮ NGUYÊN trong `TelemetryRegistry` —
        // nhóm `g_tyres` và widget `w_tire` đọc đúng tám mã này; xoá chúng là gỡ luôn cái widget owner muốn.
        // ⚠⚠ 1.90 · mục `cast` ĐÃ RỜI bảng này vì mã bị **xoá hẳn** khỏi `ControlRegistry` (owner 2026-09-21).
        // WP8 chỉ ẩn nó với lý do *"việc chiếu cụm đã có bề mặt riêng và tốt hơn"*; 1.90 bỏ hẳn sau khi [ĐO grep]
        // xác nhận đường GHI của nó (`AutoContainer.sendInfo`) **chưa bao giờ được nối** — `BydHalGateway.localSet`
        // trả `false` cho `AutoContainer` vì cast do `SimpleCastRuntime` sở hữu. Tức nút đó chết từ đầu.
        // ⚠ Lý do cũ ghi *"xoá dòng registry là gỡ luôn cả tính năng"* là **SAI** và đã bị số đo bác: [ĐO grep] cả
        // gói `modules/clustercast` (nút nổi · coordinator · bóng VietMap) có **0** tham chiếu tới `ControlRegistry`/
        // `CapabilityCatalog`/`pick`/`kindOf`. Nút nổi + nhóm Cài đặt › Chiếu màn lên cụm chạy độc lập.
        // Giữ một mục ẩn cho mã không còn tồn tại là ghim `HIDDEN_FROM_PICKER.size` vào một thứ hư — cùng lẽ đã
        // ghi cho `hood` ở 1.85. Ô của ai đã đặt `cast` rụng qua `WorkspaceState.sanitized()`.
    ) + TYRE_SINGLES.associateWith { TYRE_SINGLE_WHY }

    /** [CapabilityKind] của [id], hoặc `null` nếu mã không thuộc bộ đăng ký nào (mã cũ đã xoá / rác trong prefs). */
    fun kindOf(id: String): CapabilityKind? = when {
        // ── G1: NHÓM khả năng ────────────────────────────────────────────────────────────────────
        // Đặt TRƯỚC vì mã nhóm có tiền tố riêng (`g_`) nên không thể lẫn với bộ nào khác, và vì màn chọn phải bày
        // Nhóm ĐẦU TIÊN (§4.2) — thứ tự ở đây chảy thẳng ra thứ tự của [all] và [byDomain].
        //
        // ⚠⚠ VÌ SAO NHÓM LÀ **READ** dù 3/12 nhóm có nút bên trong:
        //  1. [isWrite] là công tắc *"dựng ô loại nào"* ở `:app` (`WidgetViews`: `isWrite(id)` ⇒ ô MỘT-NÚT). Trả
        //     WRITE thì ô "Kính" biến thành một cái nút và 4 phần trăm mở **biến mất** — mất đúng thứ nhóm sinh ra
        //     để làm. Nội dung chính của mọi nhóm là thứ để XEM; nút chỉ là một hàng thêm bên dưới (§4.3).
        //  2. Nhóm phải làm mới theo nhịp trạng thái xe, mà [WorkspaceRenderPlanner] nhận diện nội dung đọc bằng
        //     `!isWrite(...)`. Trả WRITE thì ô nhóm đứng im và số không bao giờ đổi — đúng lỗi U4 đã vá cho
        //     `w_photos`, chỉ ngược chiều.
        //  3. KHÔNG thêm giá trị thứ ba vào [CapabilityKind]: [CapabilityPick] dùng `else -> "bấm"` và
        //     `ControlDockView` rẽ nhánh hai chiều ⇒ giá trị mới sẽ **âm thầm** gán nghĩa "bấm" cho nhóm và đổi hợp
        //     đồng RW0. Nút bên trong nhóm vẫn tra ra WRITE khi hỏi từng mã, nên không mất gì.
        // Hệ quả duy nhất phải chặn riêng: READ ⇒ thanh trạng thái nhận. Đã chặn ở [TopStripConfig.isChippable]
        // (chip 24dp không vẽ được bảng 4 bánh) — chặn ở ĐÓ, không bằng cách bóp méo loại khả năng ở đây.
        CapabilityGroups.byId(id) != null -> CapabilityKind.READ
        WidgetRegistry.byId(id) != null -> CapabilityKind.READ
        TelemetryRegistry.byId(id) != null -> CapabilityKind.READ
        ControlRegistry.byId(id) != null -> CapabilityKind.WRITE
        // W2: GÓI LỆNH cũng là HÀNH ĐỘNG (nó tác động vào xe), nên tự động đặt được ở cả 3 vùng như mọi nút khác
        // — không cần code đặt-chỗ mới. Đây là giá trị cụ thể của nền RW0.
        ActionMacros.byId(id) != null -> CapabilityKind.WRITE
        // S4 · R12: hành động của CHÍNH launcher (mở ngăn kéo / mở Cài đặt). Nhờ trả về một loại RIÊNG, thanh nút
        // biết dựng ô bấm mà KHÔNG đi tìm một [ControlDef] không hề tồn tại, và không có lệnh nào chạm vào xe.
        LauncherActions.byId(id) != null -> CapabilityKind.LAUNCHER
        else -> null
    }

    /** `true` nếu [id] là hành động bấm được — dùng ở chỗ quyết định dựng ô loại nào. */
    fun isWrite(id: String): Boolean = kindOf(id) == CapabilityKind.WRITE

    /** Một khả năng theo [id], hoặc `null` nếu mã lạ (⇒ chỗ gọi suy giảm an toàn, KHÔNG sập). */
    fun pick(id: String): CapabilityPick? {
        CapabilityGroups.byId(id)?.let { return groupPick(it) }
        WidgetRegistry.byId(id)?.let {
            return CapabilityPick(it.id, it.label, it.icon, EvidenceTier.PROVEN, CapabilityKind.READ, null,
                curated = true, labelEn = it.labelEn)
        }
        TelemetryRegistry.byId(id)?.let {
            return CapabilityPick(it.id, it.label, CapabilityIcons.forTelemetry(it.id, it.domain), it.tier,
                CapabilityKind.READ, it.domain, labelEn = it.labelEn)
        }
        ControlRegistry.byId(id)?.let {
            return CapabilityPick(it.id, it.label, it.icon, it.tier, CapabilityKind.WRITE, it.domain,
                labelEn = it.labelEn)
        }
        ActionMacros.byId(id)?.let {
            // Mức bằng chứng của gói = THẤP NHẤT trong các bước ⇒ dấu "chưa kiểm" chảy ra UI đúng, không hứa quá.
            return CapabilityPick(it.id, it.label, it.icon, it.tier(), CapabilityKind.WRITE, it.domain,
                labelEn = it.labelEn)
        }
        LauncherActions.byId(id)?.let { return launcherPick(it) }
        return null
    }

    /**
     * Hành động launcher → một khả năng chọn được. **Một chỗ duy nhất** ([pick] và [allIncludingHidden] cùng đi qua
     * đây) — cùng lý do với [groupPick]: hai chỗ dựng cùng một [CapabilityPick] thì thêm một thuộc tính là sửa hai
     * nơi, và nơi thứ hai luôn là nơi bị quên.
     *
     * [EvidenceTier.PROVEN] + `domain = null` là hai quyết định, không phải hai chỗ trống — lý do ở KDoc
     * [LauncherActions].
     */
    private fun launcherPick(a: LauncherActionDef): CapabilityPick = CapabilityPick(
        a.id, a.label, a.icon, EvidenceTier.PROVEN, CapabilityKind.LAUNCHER, null, labelEn = a.labelEn,
    )

    /**
     * NHÓM → một khả năng chọn được. **Một chỗ duy nhất**: [pick] và [all] đều đi qua đây.
     *
     * Trước T4 hai hàm đó dựng [CapabilityPick] riêng với danh sách tham số y hệt nhau, nên thêm một thuộc tính
     * (dòng phụ [CapabilityGroup.contentLine]) là phải sửa đúng hai chỗ — và chỗ thứ hai rất dễ quên vì hai hàm cách
     * nhau 40 dòng. Đúng bẫy hai-bản-sao; gộp lại luôn.
     */
    private fun groupPick(g: CapabilityGroup): CapabilityPick = CapabilityPick(
        g.id, g.label, g.icon, groupTier(g), CapabilityKind.READ, g.domain,
        group = true, sub = g.contentLine, labelEn = g.labelEn,
    )

    /**
     * Mức bằng chứng của một NHÓM = **THẤP NHẤT** trong các thành viên — cùng luật với [ActionMacro.tier], và cùng
     * lý do: nhóm có một thành viên chưa kiểm thì cả ô mang dấu chưa-kiểm. Lấy mức cao nhất sẽ hứa quá đúng chỗ dễ
     * tin nhất — [ĐO] nhóm Lốp có 4 áp suất PROVEN nhưng 4 nhiệt độ [EvidenceTier.NEEDS_CAR], nên nếu lấy mức cao
     * thì cả bảng trông như đã chạy thật trong khi một nửa số ô của nó chắc chắn ra "—".
     *
     * Thứ hạng lấy từ **thứ tự khai** của [EvidenceTier] (tin cậy giảm dần) nên phép so luôn phủ đủ mọi giá trị enum
     * — thêm tier mới không âm thầm rơi ra ngoài. Nhóm không tra được thành viên nào ⇒ [EvidenceTier.NEEDS_CAR]
     * (không có gì chứng minh nó chạy); ca đó bị [CapabilityGroups.init] chặn nên chỉ là lưới an toàn.
     */
    private fun groupTier(g: CapabilityGroup): EvidenceTier {
        val tiers = g.members.mapNotNull {
            TelemetryRegistry.byId(it)?.tier ?: ControlRegistry.byId(it)?.tier ?: ActionMacros.byId(it)?.tier()
        }
        return tiers.maxByOrNull { it.ordinal } ?: EvidenceTier.NEEDS_CAR
    }

    /**
     * MỌI khả năng: **nhóm** → widget dựng tay → telemetry → control → gói lệnh (thứ tự khai trong từng bộ được giữ).
     *
     * Nhóm đứng ĐẦU là yêu cầu §4.2 (*"màn chọn xếp Nhóm lên trước"*) — đặt nó ở đây thay vì để mỗi màn chọn tự sắp
     * nghĩa là cả ngăn kéo lẫn màn Cài đặt tự đúng, và không thể có hai màn sắp khác nhau.
     *
     * U6: [HIDDEN_FROM_PICKER] bị lọc ở ĐÂY — một cửa cho mọi màn chọn. Lọc ở từng màn là bản sao thứ hai của cùng
     * một quyết định, và bản sao đó sẽ lệch (dự án đã trả giá đúng kiểu này với `unitPrefs` ×4, `customLayout` ×2).
     */
    fun all(): List<CapabilityPick> = allIncludingHidden().filterNot { it.id in HIDDEN_FROM_PICKER }

    /**
     * Như [all] nhưng KHÔNG lọc [HIDDEN_FROM_PICKER] — chỉ dùng cho phép kiểm/kê toàn bộ (test đếm mã, quét nhãn).
     * Màn chọn KHÔNG được gọi hàm này; ô đã đặt sẵn thì đi qua [pick] chứ không qua danh sách.
     */
    fun allIncludingHidden(): List<CapabilityPick> = buildList {
        CapabilityGroups.ALL.forEach {
            add(groupPick(it))
        }
        WidgetRegistry.ALL.forEach {
            add(CapabilityPick(it.id, it.label, it.icon, EvidenceTier.PROVEN, CapabilityKind.READ, null,
                curated = true, labelEn = it.labelEn))
        }
        TelemetryRegistry.ALL.forEach {
            add(CapabilityPick(it.id, it.label, CapabilityIcons.forTelemetry(it.id, it.domain), it.tier,
                CapabilityKind.READ, it.domain, labelEn = it.labelEn))
        }
        ControlRegistry.ALL.forEach {
            add(CapabilityPick(it.id, it.label, it.icon, it.tier, CapabilityKind.WRITE, it.domain,
                labelEn = it.labelEn))
        }
        ActionMacros.ALL.forEach {
            add(CapabilityPick(it.id, it.label, it.icon, it.tier(), CapabilityKind.WRITE, it.domain,
                labelEn = it.labelEn))
        }
        // S4 · R12 — ĐỨNG CUỐI, cố ý: đây là việc của launcher, không phải khả năng của xe. Bộ chọn bày chúng ở
        // khối RIÊNG ([LauncherActions.SECTION_TITLE]) chứ không qua [byDomain] (chúng `domain = null`).
        LauncherActions.ALL.forEach { add(launcherPick(it)) }
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
     * Tính một lần rồi giữ, vì [CapabilityPick.displayLabel] gọi nó cho TỪNG ô khi dựng lưới — tính lại mỗi lần sẽ
     * quét toàn bộ bộ đăng ký một lượt cho mỗi ô của một lần mở bảng.
     */
    fun collidingLabels(): Set<String> = collidingLabelsCache

    private val collidingLabelsCache: Set<String> by lazy {
        // Phải phủ ĐÚNG những gì [all] trả về, kể cả nhóm (G1): thiếu một nguồn ở đây thì nguồn đó trùng nhãn với
        // nguồn khác mà không bên nào được gợi ý loại ⇒ hai ô hiện chữ y hệt nhau.
        (CapabilityGroups.ALL.map { it.label } + WidgetRegistry.ALL.map { it.label } +
            TelemetryRegistry.ALL.map { it.label } +
            ControlRegistry.ALL.map { it.label } + ActionMacros.ALL.map { it.label } +
            LauncherActions.ALL.map { it.label })
            .groupBy { it }.filterValues { it.size > 1 }.keys
    }

    /**
     * Mã xuất hiện ở NHIỀU HƠN MỘT bộ đăng ký. **Phải luôn rỗng** — bị test khoá (spec R5).
     * Trùng mã = nối chéo âm thầm giữa "xem" và "bấm", loại lỗi rất khó lần ra từ hiện tượng.
     *
     * G1 thêm nguồn thứ NĂM (nhóm). Mã nhóm có tiền tố `g_` nên hôm nay chắc chắn không trùng, nhưng đưa nó vào phép
     * kiểm mới làm điều đó **thành bảo đảm** thay vì một nhận xét đúng-lúc-này.
     */
    fun collisions(): List<String> {
        val g = CapabilityGroups.ALL.map { it.id }
        val w = WidgetRegistry.ALL.map { it.id }
        val t = TelemetryRegistry.ALL.map { it.id }
        val c = ControlRegistry.ALL.map { it.id }
        val m = ActionMacros.ALL.map { it.id }
        // S4 · R12 thêm nguồn thứ SÁU (hành động launcher). Tiền tố `launcher_` hôm nay chắc chắn không trùng, nhưng
        // đưa vào phép kiểm mới biến điều đó thành BẢO ĐẢM — y như lý do G1 đã được thêm vào đây.
        val l = LauncherActions.ALL.map { it.id }
        return (g + w + t + c + m + l).groupBy { it }.filterValues { it.size > 1 }.keys.sorted()
    }
}
