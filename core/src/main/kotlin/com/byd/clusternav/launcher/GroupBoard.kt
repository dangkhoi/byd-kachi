package com.byd.clusternav.launcher

import com.byd.clusternav.comfort.Pm25Filter

/**
 * SẮC THÁI một ô con của ô nhóm — **không phải mã màu**.
 *
 * Bảng màu nằm ở `:app` ([KachiTheme]), đúng quy ước [ChipTone] của RW0. [ĐO] bài học đã trả giá: bản nháp trước
 * viết `#37d67a` ở `:core` trong khi `KachiTheme.GREEN` là `#34d399` ⇒ dự án có **hai bảng màu** và chúng lệch nhau
 * ngay từ dòng đầu.
 *
 * Bốn sắc thái, không ba: spec §4.3 đòi *"ô con sáng lên khi đang bật/đang cảnh báo"* — đó là **hai** việc khác nhau.
 * Đèn cốt đang bật không phải chuyện đáng lo, nhưng phải nhìn ra ngay; cửa đang mở thì là chuyện đáng lo. Gộp chúng
 * vào một sắc thái sẽ khiến người xem không phân biệt được "đang chạy" với "đang sai".
 */
enum class GroupTone {
    /** Bình thường / đang tắt / chưa đọc được. */
    NEUTRAL,

    /** Đang bật, đang mở, đang hoạt động — làm nổi bật, KHÔNG phải cảnh báo. */
    ACTIVE,

    /** Đáng để ý nhưng không nguy (lốp lệch, ESP đang tắt, bụi mức trung bình). */
    WARN,

    /** Đang cảnh báo (cửa mở, dây chưa thắt, radar sát vật, lốp non/căng). */
    ALERT,
}

/**
 * PHÍA của một ô con **trên xe** — để bộ vẽ `BOARD` đặt nó đúng chỗ trong không gian.
 *
 * ## Vì sao ở `:core` chứ không suy ra ở tầng vẽ
 * Biết `bsd_fl_alarm` là bên TRÁI là kiến thức về **mã datum**, cùng họ với việc biết `window_lf` là kính nào. Tầng
 * vẽ tự đoán từ mã nghĩa là nó phải chép quy ước đặt tên — đúng bản-sao-thứ-hai mà [CapabilityGroups] tồn tại để
 * loại bỏ, và có test cấm tầng vẽ nhắc tới mã thành viên.
 *
 * [NONE] **không phải** "chưa biết" mà là *"mục này không thuộc bên nào"* (ESP, cảnh báo quá tốc — chúng nói về cả
 * xe). Bộ vẽ đưa chúng xuống dòng chân bảng thay vì gán bừa vào một bên.
 */
enum class GroupSide { LEFT, RIGHT, NONE }

/**
 * Một ô con **XEM** trong ô nhóm — đã quyết định xong nội dung, không biết View.
 *
 * @property label nhãn NGẮN ([TelemetrySpec.shortLabel]) — ô con của nhóm hẹp hơn ô rời rất nhiều (một dải 10 cửa
 *   trên khung 640dp còn ~64dp mỗi ô), nhãn đầy sẽ bị cắt thành những chuỗi giống hệt nhau. Đây chính là lỗi [ĐO]
 *   2026-09-10 (*"Áp lốp trước-t…"* × 2) mà `short` của RW0 sinh ra để chữa.
 * @property number số/chữ **không kèm đơn vị** — cho bộ vẽ CARD dựng số chính cỡ lớn. `"—"` nếu chưa đọc được.
 * @property unit đơn vị đã theo lựa chọn người dùng ([UnitFormat]); rỗng nếu datum không có đơn vị.
 * @property available đọc được số hay chưa. Off-car là ca **thường**, không phải ca lỗi ⇒ bộ vẽ làm mờ, KHÔNG bịa số.
 * @property needsBadge mức bằng chứng chưa PROVEN ⇒ ô nhóm mang dấu "chưa kiểm trên xe".
 * @property side phía trên xe (xem [GroupSide]) — chỉ bộ vẽ `BOARD` dùng.
 */
data class GroupCell(
    val id: String,
    val label: String,
    val number: String,
    val unit: String,
    val tone: GroupTone,
    val icon: String,
    val available: Boolean,
    val needsBadge: Boolean,
    val side: GroupSide = GroupSide.NONE,
) {
    /**
     * Số **kèm đơn vị** — dạng dùng cho dải STRIP và cho các số phụ của thẻ CARD.
     *
     * Là thuộc tính TÍNH RA, không phải field thứ ba: giữ cả `"2.4"` và `"2.4 bar"` trong hai field là hai bản sao
     * của một dữ liệu, và chúng sẽ lệch nhau đúng lúc ai đó sửa một chỗ.
     */
    val value: String get() = if (!available || unit.isEmpty()) number else "$number $unit"
}

/**
 * Một ô con **BẤM** — nút ([ControlRegistry]) hoặc gói lệnh ([ActionMacros]), nằm ở **hàng dưới cùng ô** (§4.3).
 *
 * Chỉ mang thứ để dựng ô; **không** mang giá trị đọc, vì nút không có giá trị đọc (xem KDoc [ControlTileState]:
 * phần lớn nút không có đường đọc lại từ xe).
 */
data class GroupActionCell(
    val id: String,
    val label: String,
    val icon: String,
    val needsBadge: Boolean,
)

/**
 * MODEL TRÌNH BÀY của một ô nhóm — kết quả THUẦN của [CapabilityGroup] + [CarStatus] + [UnitPrefs].
 *
 * Bộ vẽ ở `:app` chỉ đọc cái này; nó KHÔNG được tự tra [TelemetryReadout] hay tự đổi đơn vị (có test canh), vì mỗi
 * bề mặt tự quyết đơn vị chính là bệnh mà lớp [UnitFormat] sinh ra để dọn.
 */
data class GroupBoardModel(
    val id: String,
    val label: String,
    val icon: String,
    val shape: WidgetShape,
    val cells: List<GroupCell>,
    val actions: List<GroupActionCell>,
) {
    /** Số chính của bộ vẽ CARD = ô con ĐẦU TIÊN (thứ tự khai trong nhóm là thứ tự trình bày). */
    val lead: GroupCell? get() = cells.firstOrNull()

    /** Các số phụ xếp hàng dưới số chính (CARD). */
    val rest: List<GroupCell> get() = if (cells.isEmpty()) emptyList() else cells.drop(1)

    /** Nhóm có hàng nút ở dưới không — chỉ 3/12 nhóm có (kính · cửa & khoang · đèn). */
    val hasActions: Boolean get() = actions.isNotEmpty()

    /**
     * Ô con **bên trái / bên phải xe** (theo [GroupCell.side]) — cho bộ vẽ `BOARD` xếp theo không gian.
     *
     * ## Vì sao là hai danh sách chứ không phải danh sách CẶP
     * Ghép cặp đòi hai bên **luôn** cùng số lượng. Đúng với nhóm ADAS hôm nay (4 trái / 4 phải), nhưng một nhóm chỉ
     * có cảnh báo bên trái là chuyện hợp lệ, và lúc đó phép ghép cặp sẽ hoặc ném hoặc âm thầm đẩy một mục sang bên
     * kia — tức **nói sai vị trí**, đúng thứ bảng theo-không-gian sinh ra để tránh. Hai cột độc lập thì bên nào có
     * bao nhiêu vẽ bấy nhiêu.
     */
    val leftCells: List<GroupCell> get() = cells.filter { it.side == GroupSide.LEFT }

    /** Đối xứng với [leftCells]. */
    val rightCells: List<GroupCell> get() = cells.filter { it.side == GroupSide.RIGHT }

    /** Ô con **không thuộc bên nào** (ESP, quá tốc…) ⇒ bộ vẽ BOARD đưa xuống dòng chân bảng. */
    val centreCells: List<GroupCell> get() = cells.filter { it.side == GroupSide.NONE }

    /**
     * Icon của các ô con có **phân biệt được** không.
     *
     * ## ⚠⚠ [KIỂM TOÁN UX mục 4c] Icon không phân biệt được thì phải BỎ, không phải để cho đủ
     * [ĐO] nhóm *An toàn · ADAS*: **8/10 mục cùng một icon sóng radar** ⇒ tám ô con trông y hệt nhau và icon **không
     * mang thông tin nào**, nó chỉ chiếm chỗ của thứ có mang (con số và cái nhãn, mà nhãn thì đang bị cắt).
     *
     * Đo bằng *"có hình nào lặp ≥ [ICON_REPEAT_CAP] lần"* chứ không bằng *"mọi hình đều khác nhau"*: hai ô cùng hình
     * (trái/phải của một cặp) vẫn phân biệt được nhờ nhãn và vị trí; ba ô trở lên thì mắt thôi phân loại được.
     *
     * [ĐO] hiện trạng — bốn nhóm KHÁC cũng không phân biệt được (`g_windows` 4× kính · `g_doors` 4× cửa · `g_lights`
     * nhiều × đèn · `g_occupants` 3× ghế). Ba nhóm đầu **có nút** nên bộ vẽ vốn đã bỏ icon (chỗ đó cần bề cao cho
     * hàng nút); `g_occupants` thì trước bản vá này vẫn hiện ba icon ghế giống nhau.
     */
    val iconsDistinguish: Boolean
        get() = cells.groupingBy { it.icon }.eachCount().none { it.value >= ICON_REPEAT_CAP }

    private companion object {
        /**
         * Số lần một hình được lặp trước khi coi là "không phân biệt được".
         *
         * 3 chứ không 2: một CẶP trái/phải cùng hình là chuyện bình thường và vẫn đọc được nhờ nhãn; từ ba ô thì
         * không còn là cặp nữa mà là một dãy đồng nhất.
         */
        const val ICON_REPEAT_CAP = 3
    }

    /**
     * Ô nhóm có mang dấu "chưa kiểm trên xe" không.
     *
     * Tính từ CHÍNH các thành viên đã có trong model, **không** dựng lại phép xếp hạng
     * [EvidenceTier] lần thứ hai (`CapabilityCatalog` đã có một bản — hai bản sẽ lệch nhau).
     */
    val needsBadge: Boolean get() = cells.any { it.needsBadge } || actions.any { it.needsBadge }

    /**
     * Một dòng TÓM TẮT cho ô nén (khi người dùng nhét nhiều widget vào cùng một ô, mỗi ô con chỉ còn ~1/4 khung nên
     * không vẽ nổi cả dải/bảng).
     *
     * Ưu tiên nói **cái sai** trước: một nhóm 10 cửa mà tóm tắt bằng số của cửa đầu tiên thì vô nghĩa; *"1 cảnh
     * báo"* trả lời đúng câu người lái hỏi. Không có gì sai thì mới hiện số chính.
     */
    fun summary(): String {
        val alerts = cells.count { it.tone == GroupTone.ALERT }
        if (alerts > 0) return "$alerts cảnh báo"
        val warns = cells.count { it.tone == GroupTone.WARN }
        if (warns > 0) return "$warns lưu ý"
        return lead?.takeIf { it.available }?.value ?: TelemetryView.PLACEHOLDER
    }
}

/**
 * ═══ G1 · T3 — PHẦN QUYẾT ĐỊNH CỦA Ô NHÓM ════════════════════════════════════════════════════════════════════
 *
 * Thuần Kotlin (`:core`, cấm `android.*`) ⇒ kiểm được off-car. Spec `docs/specs/kachi-capability-groups.html` §4.3.
 * Cùng lối chia việc với [TyreBoard] (quyết định ở `:core`) ↔ `TyreBoardView` (vẽ ở `:app`) — tiền lệ đúng của W4.
 *
 * ## Thành viên lấy từ [CapabilityGroups], KHÔNG chép tay
 * Danh sách thành viên chỉ tồn tại ở MỘT chỗ. Chép sang đây (hoặc sang `:app`) sẽ tạo bản sao thứ hai phải giữ đồng
 * bộ bằng trí nhớ — có test canh cả hai đầu.
 *
 * ## Ngưỡng: chỉ dùng lại, gần như không đặt mới
 * Phán xét non/căng/lệch của lốp lấy **nguyên** từ [TyreBoard] (nếu viết lại ở đây thì thành ngưỡng **THỨ TƯ** của
 * dự án — lỗi đã xảy ra thật: widget lốp cũ có `t[i] < 2.2` viết tại chỗ, lệch với `:core`). Mức bụi lấy từ
 * [Pm25Filter.isDirty]. Chỉ có ĐÚNG MỘT ngưỡng mới ([RADAR_ALERT_LEVEL]) vì cảm biến đỗ chưa từng có ngưỡng nào.
 *
 * ⚠ **CỐ Ý KHÔNG gán sắc thái cho các số "sức khoẻ"** (nhiệt pin, điện áp cell, ắc-quy 12V, nhiệt lốp, µg/m³, SOC…):
 * tôi KHÔNG biết ngưỡng đúng cho đời xe owner, và tự nghĩ một con số rồi tô đỏ là **bịa cảnh báo** — nguy hơn là
 * không cảnh báo, vì người lái sẽ học cách bỏ qua màu đỏ. Chỉ những datum **bản thân nó đã là tín hiệu báo động**
 * (cửa mở, dây chưa thắt, radar, BSD/LCA/RCTA/DOW, quá tốc, ESP tắt) mới có sắc thái. Đây là chỗ để owner thêm ngưỡng
 * sau khi đo số thật trên xe.
 */
object GroupBoard {

    /**
     * Số vùng cảm biến đỗ. Bằng 8 vì [CarStatus.Safety.radarZones] khai *"8 vùng cảm biến đỗ (0=an toàn…4=đỏ)"* và
     * [WidgetShape.BOARD] cũng ghi *"8 zone radar"* từ đầu.
     */
    const val RADAR_ZONE_COUNT = 8

    /**
     * Mức vùng radar từ đây trở lên = **cảnh báo** (0..4 theo KDoc [CarStatus.Safety.radarZones], 4 = đỏ).
     *
     * ⚠ Con số này là **quyết định của agent**, chưa đo trên xe — cùng tình trạng với [TyreBoard.LOW_BAR]. Đặt ở MỘT
     * chỗ để đổi một dòng sau khi owner nghe thử tiếng bíp ở từng mức. Chọn 3 (chứ không 1) có chủ ý: cảm biến đỗ kêu
     * gần như liên tục lúc đỗ xe, tô đỏ từ mức 1 sẽ làm cả bảng đỏ suốt và mất hết ý nghĩa.
     */
    const val RADAR_ALERT_LEVEL = 3

    /**
     * Datum áp suất lốp ↔ góc bánh. Cần vì [TyreBoard.readings] trả về theo [TyreCorner] còn nhóm nói bằng **mã
     * datum** — phải có một chỗ nối hai cách gọi đó. Đặt ở đây (không ở [TyreBoard]) vì đây là chỗ DUY NHẤT cần nối.
     */
    private val TYRE_PRESSURE_BY_CORNER: Map<TyreCorner, String> = mapOf(
        TyreCorner.FRONT_LEFT to "tyre_p_fl",
        TyreCorner.FRONT_RIGHT to "tyre_p_fr",
        TyreCorner.REAR_LEFT to "tyre_p_rl",
        TyreCorner.REAR_RIGHT to "tyre_p_rr",
    )

    /** Model cho nhóm [id], hoặc `null` nếu mã không phải nhóm (⇒ chỗ gọi suy giảm an toàn, KHÔNG sập). */
    fun of(id: String, status: CarStatus, units: UnitPrefs = UnitPrefs.DEFAULT): GroupBoardModel? =
        CapabilityGroups.byId(id)?.let { of(it, status, units) }

    /**
     * Model cho [g]. Luôn trả về **đúng** `g.reads.size` ô con và `g.writes.size` nút, theo đúng thứ tự khai — kể cả
     * off-car (ô con chưa đọc được hiện `"—"`), để bố cục không nhảy khi số về.
     */
    fun of(g: CapabilityGroup, status: CarStatus, units: UnitPrefs = UnitPrefs.DEFAULT): GroupBoardModel {
        val tyres = tyreStatuses(g, status)
        val radar = radarLevels(status)
        return GroupBoardModel(
            id = g.id,
            label = g.label,
            icon = g.icon,
            shape = g.shape,
            cells = g.reads.map { cell(it, status, units, tyres, radar) },
            actions = g.writes.mapNotNull { action(it) },
        )
    }

    /**
     * 8 mức vùng radar cho bộ vẽ BOARD — luôn ĐÚNG [RADAR_ZONE_COUNT] phần tử; vùng chưa đọc = `null`.
     *
     * Đọc THẲNG từ [CarStatus] chứ **không** tách lại chuỗi hiển thị của `radar_zones` (chuỗi đó là `"0 1 2 …"` ghép
     * bằng dấu cách trong [TelemetryReadout]). Tách ngược một chuỗi trình bày để lấy lại số là dựng **bản sao thứ
     * hai** của cùng dữ liệu, và nó sẽ vỡ im lặng ngay khi ai đổi dấu phân cách. Giá trị hiển thị của ô con
     * `radar_zones` thì vẫn đi qua [TelemetryReadout] như mọi ô con khác — hai việc khác nhau, không phải hai đường
     * cho cùng một việc.
     */
    fun radarLevels(status: CarStatus): List<Int?> {
        val z = status.safety.radarZones
        return List(RADAR_ZONE_COUNT) { z?.getOrNull(it) }
    }

    /**
     * Nhóm ở dạng **MỘT-SỐ** — cho thanh nút xe (ô 84×86dp), nơi không vẽ nổi cả dải/bảng.
     *
     * ## ⚠⚠ Vì sao hàm này PHẢI tồn tại (nếu không thì màn Cài đặt bày ra một ô CHẾT)
     * T4 cho nhóm xuất hiện ở màn Cài đặt, và lưới ở đó bật/tắt **thanh nút xe**. Thanh nút dựng ô ĐỌC bằng
     * `TelemetryReadout.of(id)` — mà mã nhóm không có trong [TelemetryRegistry] ⇒ trả `null` ⇒ ô hiện `"—"` **mãi
     * mãi**. Tức bày ra một lựa chọn mà chọn xong thì được một ô trống: đúng họ lỗi *"vẽ được ≠ đặt được"* của RW0,
     * chỉ ngược chiều (lần này người dùng đặt được nhưng thứ đặt ra thì vô dụng).
     *
     * Dùng LẠI [GroupBoardModel.summary] (đã có, đã test) chứ không nghĩ ra phép tóm tắt thứ hai: *"2 cảnh báo"* trả
     * lời đúng câu người lái hỏi khi họ chỉ có một ô nhỏ để nhìn. Đơn vị cũng đã đi qua [UnitFormat] bên trong [of]
     * ⇒ không có lớp đơn vị thứ hai.
     *
     * @return `null` nếu [id] không phải nhóm ⇒ chỗ gọi rơi về đường telemetry như cũ.
     */
    fun summaryView(id: String, status: CarStatus, units: UnitPrefs = UnitPrefs.DEFAULT): TelemetryView? {
        val m = of(id, status, units) ?: return null
        val s = m.summary()
        return TelemetryView(
            id = m.id,
            label = m.label,
            unit = "",              // tóm tắt tự mang đơn vị bên trong ("2.4 bar") hoặc là một câu ("2 cảnh báo")
            shape = m.shape,
            // Chỉ [TelemetryView.needsBadge] được đọc từ trường này ở chỗ gọi; suy từ [GroupBoardModel.needsBadge]
            // thay vì xếp hạng lại [EvidenceTier] lần thứ ba (CapabilityCatalog đã có một bản).
            tier = if (m.needsBadge) EvidenceTier.OVERDRIVE else EvidenceTier.PROVEN,
            // Chưa đọc được gì ⇒ `null` để ô tự mờ đi. Nhồi chuỗi "—" vào đây thì ô sáng như đã có số.
            valueText = s.takeIf { it != TelemetryView.PLACEHOLDER },
        )
    }

    /** Sắc thái tổng của bảng radar (dùng cho cả ô con `radar_zones` lẫn màu vùng). */
    fun radarTone(level: Int?): GroupTone = when {
        level == null -> GroupTone.NEUTRAL
        level >= RADAR_ALERT_LEVEL -> GroupTone.ALERT
        level > 0 -> GroupTone.WARN
        else -> GroupTone.NEUTRAL
    }

    // ── Dựng ô con ──────────────────────────────────────────────────────────────────────────────────────

    private fun cell(
        id: String,
        s: CarStatus,
        units: UnitPrefs,
        tyres: Map<String, TyreStatus>,
        radar: List<Int?>,
    ): GroupCell {
        val spec = TelemetryRegistry.byId(id)
        // Mã không có trong registry là chuyện [CapabilityGroups.init] đã chặn; giữ lưới an toàn để ô hiện "—" thay
        // vì cả launcher sập nếu ngày nào đó một datum bị đổi tên.
        val view = TelemetryReadout.of(id, s)?.let { UnitFormat.apply(it, units) }
        return GroupCell(
            id = id,
            label = spec?.shortLabel ?: id,
            number = view?.display ?: TelemetryView.PLACEHOLDER,
            unit = view?.unit ?: "",
            tone = toneOf(id, s, tyres, radar),
            icon = spec?.let { CapabilityIcons.forTelemetry(it.id, it.domain) } ?: "",
            available = view?.available == true,
            needsBadge = spec?.tier?.needsBadge == true,
            side = sideOf(id),
        )
    }

    /**
     * Phía trên xe của một mã datum, suy từ **quy ước đặt tên** của bộ đăng ký.
     *
     * Quy ước có thật và nhất quán trong cả 123 datum: `_lf`/`_fl`/`_lr` + hậu tố `_left` = bên trái, `_rf`/`_fr`/
     * `_rr` + `_right` = bên phải. Mã không mang dấu hiệu nào ⇒ [GroupSide.NONE] (ESP, cảnh báo quá tốc — chúng nói
     * về cả xe, gán bừa vào một bên là **nói sai** chứ chỉ là xếp xấu).
     *
     * ⚠ Xét TRÁI trước PHẢI không quan trọng ở đây (không mã nào mang cả hai), nhưng xét `contains` thay vì
     * `endsWith` thì **bắt buộc**: `bsd_fl_alarm` kết bằng `_alarm`, nên `endsWith("_fl")` sẽ trả NONE cho đúng
     * bốn mục điểm mù — tức bảng mất hẳn hai hàng mà không báo gì.
     */
    fun sideOf(id: String): GroupSide = when {
        id.endsWith("_left") || id.contains("_fl") || id.contains("_lf") || id.contains("_lr") -> GroupSide.LEFT
        id.endsWith("_right") || id.contains("_fr") || id.contains("_rf") || id.contains("_rr") -> GroupSide.RIGHT
        else -> GroupSide.NONE
    }

    private fun action(id: String): GroupActionCell? {
        ControlRegistry.byId(id)?.let {
            return GroupActionCell(it.id, it.label, it.icon, it.tier.needsBadge)
        }
        ActionMacros.byId(id)?.let {
            return GroupActionCell(it.id, it.label, it.icon, it.needsBadge())
        }
        return null
    }

    /**
     * Trạng thái 4 bánh theo **mã datum**, rỗng nếu nhóm không chứa áp suất lốp.
     *
     * Gọi [TyreBoard.readings] đúng MỘT lần cho cả nhóm: độ chênh giữa các bánh chỉ tính được khi biết cả bốn, nên
     * hỏi từng bánh riêng lẻ sẽ ra kết quả khác (và sai).
     */
    private fun tyreStatuses(g: CapabilityGroup, s: CarStatus): Map<String, TyreStatus> {
        if (g.reads.none { it in TYRE_PRESSURE_BY_CORNER.values }) return emptyMap()
        return TyreBoard.readings(s.tyres)
            .mapNotNull { rd -> TYRE_PRESSURE_BY_CORNER[rd.corner]?.let { it to rd.status } }
            .toMap()
    }

    /** Non/căng = cảnh báo; lệch = để ý (đúng thứ tự ưu tiên của [TyreStatus]). */
    private fun tyreTone(st: TyreStatus): GroupTone = when (st) {
        TyreStatus.LOW, TyreStatus.HIGH -> GroupTone.ALERT
        TyreStatus.UNEVEN -> GroupTone.WARN
        TyreStatus.OK, TyreStatus.UNKNOWN -> GroupTone.NEUTRAL
    }

    // ── Sắc thái ────────────────────────────────────────────────────────────────────────────────────────

    /**
     * Sắc thái một ô con. Đọc **field có kiểu** của [CarStatus], KHÔNG so chuỗi hiển thị:
     * [TelemetryReadout] trả `"Mở"`/`"Bật"`/`"Có"` bằng tiếng Việt, nên so chuỗi sẽ vỡ im lặng ngay lần đầu ai sửa
     * một nhãn — và vỡ theo cách tệ nhất (mất cảnh báo, không phải báo sai).
     *
     * Mã không có luật ⇒ [GroupTone.NEUTRAL]: xem KDoc lớp về việc **không bịa ngưỡng**.
     */
    private fun toneOf(id: String, s: CarStatus, tyres: Map<String, TyreStatus>, radar: List<Int?>): GroupTone {
        tyres[id]?.let { return tyreTone(it) }
        return when (id) {
            // ── ĐANG BẬT / ĐANG MỞ (sáng lên) ───────────────────────────────────────────────
            "window_lf" -> openPct(s.body.windowLfPct)
            "window_rf" -> openPct(s.body.windowRfPct)
            "window_lr" -> openPct(s.body.windowLrPct)
            "window_rr" -> openPct(s.body.windowRrPct)
            "tailgate_position" -> openPct(s.body.tailgatePct)
            "sunroof_pos" -> openPct(s.body.sunroofPct)
            "sunshade_pct" -> openPct(s.body.sunshadePct)
            // Nóc/rèm mở là LỰA CHỌN của người lái, không phải chuyện đáng lo ⇒ ACTIVE. Cửa và cốp thì khác (xem
            // nhánh cảnh báo dưới): xe tự kêu khi chúng mở lúc đang đi.
            "sunroof_state" -> active(s.body.sunroofOpen)
            "mirror_fold" -> active(s.body.mirrorFolded)
            "light_low_beam" -> active(s.lights.lowBeam)
            "light_high_beam" -> active(s.lights.highBeam)
            "light_front_fog" -> active(s.lights.frontFog)
            "light_rear_fog" -> active(s.lights.rearFog)
            "light_left_turn" -> active(s.lights.leftTurn)
            "light_right_turn" -> active(s.lights.rightTurn)
            "light_side" -> active(s.lights.sideLight)
            "light_drl" -> active(s.lights.drl)
            "ambient_enabled" -> active(s.lights.ambientOn)
            "ac_on" -> active(s.climate.acOn)
            "ac_cycle" -> active(s.climate.recircOn)
            "anion_state" -> active(s.climate.anionOn)
            "is_charging" -> active(s.energy.isCharging)
            // Có người ngồi = thông tin, không phải cảnh báo (cảnh báo là *chưa thắt dây*, ở nhánh dưới).
            "oms_driver" -> active(s.safety.omsDriver)
            "oms_passenger" -> active(s.safety.omsPassenger)

            // ── CẢNH BÁO ────────────────────────────────────────────────────────────────────
            "door_lf" -> alertIf(s.body.doorLfOpen)
            "door_rf" -> alertIf(s.body.doorRfOpen)
            "door_lr" -> alertIf(s.body.doorLrOpen)
            "door_rr" -> alertIf(s.body.doorRrOpen)
            "tailgate_status" -> alertIf(s.body.tailgateOpen)
            "child_presence" -> alertIf(s.safety.childPresence)
            "speed_limit_warning" -> alertIf(s.safety.speedLimitWarning)
            "seatbelt_driver" -> alertIfNot(s.safety.seatbeltDriver)
            "seatbelt_passenger" -> alertIfNot(s.safety.seatbeltPassenger)
            "bsd_fl_alarm" -> alarmLevel(s.safety.bsdLeftLevel)
            "bsd_fr_alarm" -> alarmLevel(s.safety.bsdRightLevel)
            "lca_left" -> alarmLevel(s.safety.lcaLeft)
            "lca_right" -> alarmLevel(s.safety.lcaRight)
            "rcta_left" -> alarmLevel(s.safety.rctaLeft)
            "rcta_right" -> alarmLevel(s.safety.rctaRight)
            "dow_left" -> alarmLevel(s.safety.dowLeft)
            "dow_right" -> alarmLevel(s.safety.dowRight)
            // ESP TẮT là đèn báo trên táp-lô thật ⇒ để ý. Bật = bình thường, không cần tô gì.
            "esp_state" -> if (s.safety.espOn == false) GroupTone.WARN else GroupTone.NEUTRAL
            // Ngưỡng bụi dùng LẠI [Pm25Filter] (đã có, đang chạy trên xe) — không đặt ngưỡng thứ hai.
            "pm25_level" -> s.climate.pm25Level?.let {
                when {
                    Pm25Filter.isDirty(it) -> GroupTone.ALERT
                    it >= Pm25Filter.LOW_GRADE -> GroupTone.WARN
                    else -> GroupTone.NEUTRAL
                }
            } ?: GroupTone.NEUTRAL
            "radar_zones" -> radarTone(radar.filterNotNull().maxOrNull())

            else -> GroupTone.NEUTRAL
        }
    }

    private fun openPct(pct: Int?): GroupTone =
        if (pct != null && pct > 0) GroupTone.ACTIVE else GroupTone.NEUTRAL

    private fun active(on: Boolean?): GroupTone = if (on == true) GroupTone.ACTIVE else GroupTone.NEUTRAL

    private fun alertIf(open: Boolean?): GroupTone = if (open == true) GroupTone.ALERT else GroupTone.NEUTRAL

    /** Cảnh báo khi giá trị là **false** (dây an toàn chưa thắt). `null` = chưa đọc ⇒ không cảnh báo oan. */
    private fun alertIfNot(ok: Boolean?): GroupTone = if (ok == false) GroupTone.ALERT else GroupTone.NEUTRAL

    /** Mức báo động của cảm biến hỗ trợ lái (0 = im lặng). */
    private fun alarmLevel(level: Int?): GroupTone =
        if (level != null && level > 0) GroupTone.ALERT else GroupTone.NEUTRAL
}
