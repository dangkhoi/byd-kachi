package com.byd.clusternav.launcher

// ⚠ [KIỂM TOÁN 2026-09-12] Tách khỏi `GroupBoard.kt` vì tệp đó vượt trần 500 dòng sau khi thêm [SideBoardPlan] và
// [GroupBoardModel.actionIconsDistinguish]. Đường cắt: đây là **các KIỂU** (dữ liệu đã quyết định xong), còn
// `GroupBoard` là **phần quyết định** (đọc CarStatus + đơn vị + ngưỡng) — hai vai khác nhau, không phải cắt bừa.
//
// ⚠⚠ `GroupTileWiringContractTest` nối CẢ HAI tệp khi kiểm `:core` (không giữ mã màu hex · không biết KachiTheme ·
// thuần JVM), nên tách chỗ này KHÔNG làm bài canh nào thôi phủ — có bài đòi đúng điều đó.


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
    ALERT;

    /**
     * Sắc thái này có **đáng để người lái nhìn ngay** không (⇒ được ưu tiên chỗ khi ô hẹp — xem [SideBoardPlan]).
     *
     * [ACTIVE] KHÔNG tính: *"đèn cốt đang bật"* là thông tin, không phải chuyện phải xử lý; nếu tính nó thì một dải
     * đèn đang bật bình thường sẽ đẩy cảnh báo thật ra khỏi chỗ.
     */
    val isLoud: Boolean get() = this == WARN || this == ALERT
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

    /**
     * Icon của các **NÚT** có phân biệt được không — cùng luật [iconsDistinguish], áp cho hàng nút.
     *
     * ## ⚠⚠ [KIỂM TOÁN 2026-09-12 mục 3] Đây là chỗ lấy lại 30px cuối cùng, và nó KHÔNG phải mẹo tiết kiệm
     * [ĐO] nhóm *Kính* có 6 nút, trong đó **4 nút cùng icon cửa kính** (`win_lf/rf/lr/rr` đều `ic-window`) ⇒ bốn ô
     * trông y hệt nhau, đúng tình huống mà luật G1 (*"nhóm nào ≥3 mục dùng chung icon ⇒ bỏ icon, thay bằng vị trí"*)
     * sinh ra để xử. Ô con XEM đã bỏ icon vì lý do đó từ G1; hàng nút thì tới nay vẫn giữ.
     *
     * Bỏ icon ở đó vừa **đúng luật đã có**, vừa trả lại `ICON_XS + XS` (30px @1.5×) — đúng phần còn thiếu để hàng nút
     * hết bị cắt ([ĐO] trước khi bỏ: thiếu **1px**, nên ô kính mất ~1px vành dưới ở CẢ hai thứ tiếng). Nói cách khác:
     * hai lỗi có **một** nguyên nhân chung là ô đang chứa thứ không mang thông tin.
     *
     * Hai nhóm còn lại KHÔNG bị ảnh hưởng: *Cửa & khoang* (icon lặp tối đa 2) và *Đèn* (lặp tối đa 2) vẫn giữ icon.
     */
    val actionIconsDistinguish: Boolean
        get() = actions.groupingBy { it.icon }.eachCount().none { it.value >= ICON_REPEAT_CAP }

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
        if (alerts > 0) return Strings.t("$alerts cảnh báo", "$alerts ${if (alerts == 1) "alert" else "alerts"}")
        val warns = cells.count { it.tone == GroupTone.WARN }
        if (warns > 0) return Strings.t("$warns lưu ý", "$warns to note")
        return lead?.takeIf { it.available }?.value ?: TelemetryView.PLACEHOLDER
    }
}

/**
 * KẾ HOẠCH TRÌNH BÀY của bảng sơ đồ hai bên xe — **quyết định ở `:core`, vẽ ở `:app`**.
 *
 * ## [ĐO] bệnh nó chữa — ảnh máy ảo 2026-09-12
 * Nhóm *An toàn · ADAS* có 8 ô con hai bên. Bảng cũ chia bề cao cho **4 hàng mỗi bên** bất kể ô cao bao nhiêu, nên ở
 * khung 4/12 màn mỗi hàng chỉ còn ~45px cho HAI dòng chữ (giá trị trên, nhãn dưới) ⇒ nhãn ra **nét cao 13px**, dưới
 * chuẩn G1 (15–16px). Tức bảng tự bóp chữ để nhồi cho đủ hàng — đúng thứ kiểm toán gọi là *"nhét 10 nhãn bằng chữ
 * nhỏ"*, mà người đang lái thì không đọc được.
 *
 * ## Luật: cỡ chữ có SÀN, số hàng thì CO
 * `:app` biết mấy hàng còn vẽ được ở cỡ chữ đọc được (nó có pixel), rồi hỏi hàm này *"vậy hiện cái gì"*. Trả lời:
 *  • đủ chỗ cho mọi hàng ⇒ vẽ đủ, y như trước (ca thường ở ô to);
 *  • không đủ ⇒ chỉ những ô **ĐANG cảnh báo** (chúng trả lời đúng câu người lái hỏi: *"có gì bên cạnh tôi không, bên
 *    nào?"*), phần còn lại **đếm** ở dòng chân;
 *  • không đủ chỗ mà cũng **không có cảnh báo nào** ⇒ một dòng [summary] nói thẳng trạng thái. Ở đây phải phân biệt
 *    *"đã đọc, không có gì"* với *"chưa đọc được"* — gộp hai câu đó lại là **nói sai** với người lái (off-car và ca
 *    mất cảm biến đều rơi vào nhánh sau).
 *
 * @property left / @property right ô con sẽ vẽ ở mỗi bên (đã theo thứ tự khai của nhóm).
 * @property hidden số ô con hai bên KHÔNG được vẽ — `:app` ghép vào dòng chân, không im lặng bỏ.
 * @property summary câu duy nhất thay cho cả hai cột khi không vẽ được hàng nào; `null` khi có hàng để vẽ.
 */
data class SideBoardPlan(
    val left: List<GroupCell>,
    val right: List<GroupCell>,
    val hidden: Int,
    val summary: String?,
)
