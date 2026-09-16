package com.byd.clusternav.launcher

// ⚠ [KIỂM TOÁN 2026-09-12] Tách khỏi `GroupBoard.kt` vì tệp đó vượt trần 500 dòng sau khi thêm
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

    /** Đáng để ý nhưng không nguy (lốp lệch, bụi mức trung bình). */
    WARN,

    /** Đang cảnh báo (cửa mở, cốp mở, lốp non/căng). */
    ALERT,
    // ⚠ 2026-09-16 — thuộc tính `isLoud` (*"sắc thái này có đáng nhìn ngay không"*) đã XOÁ cùng
    // `GroupBoard.sidePlan`: chỗ gọi DUY NHẤT của nó là phép "ô hẹp thì ưu tiên ô đang cảnh báo" của bảng sơ đồ
    // hai bên, mà bảng đó rụng cùng toàn bộ ADAS/an toàn (owner). Cần lại thì dựng lại cùng chỗ gọi — đừng để
    // một thuộc tính không ai đọc nằm đây (luật "nút chết" của dự án).
}

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

    /** Nhóm có hàng nút ở dưới không — chỉ 3/9 nhóm có (kính · cửa & khoang · đèn). */
    val hasActions: Boolean get() = actions.isNotEmpty()

    /**
     * Icon của các ô con có **phân biệt được** không.
     *
     * ## ⚠⚠ [KIỂM TOÁN UX mục 4c] Icon không phân biệt được thì phải BỎ, không phải để cho đủ
     * Một nhóm mà phần lớn ô con mang CÙNG một hình thì icon **không mang thông tin nào**, nó chỉ chiếm chỗ của thứ
     * có mang (con số và cái nhãn, mà nhãn thì đang bị cắt).
     *
     * Đo bằng *"có hình nào lặp ≥ [ICON_REPEAT_CAP] lần"* chứ không bằng *"mọi hình đều khác nhau"*: hai ô cùng hình
     * (trái/phải của một cặp) vẫn phân biệt được nhờ nhãn và vị trí; ba ô trở lên thì mắt thôi phân loại được.
     *
     * [ĐO] hiện trạng — ba nhóm không phân biệt được (`g_windows` 4× kính · `g_doors` 4× cửa · `g_lights` nhiều ×
     * đèn). Cả ba **có nút** nên bộ vẽ vốn đã bỏ icon (chỗ đó cần bề cao cho hàng nút).
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
 * BỘ PHẬN MỞ ĐƯỢC của thân xe — khoá **hình học** của bảng *Cửa & khoang* (U9 pha 2).
 *
 * ## Vì sao enum ở `:core` chứ không để tầng vẽ tự suy từ mã datum
 * Biết `door_lf` là **vạt cửa trước-trái** là kiến thức về mã datum, không phải về pixel.
 * `GroupTileWiringContractTest` cấm tầng vẽ nhắc tới mã thành viên (`"door_"`) chính vì mỗi lần
 * chép một mã sang `:app` là dựng thêm một bản sao phải giữ đồng bộ bằng trí nhớ. Enum này là chỗ nối: `:core` nói
 * *"bộ phận nào"*, `:app` tra ra path của **đúng** bộ phận đó trong bộ icon v2.
 *
 * ⚠ Hậu tố theo quy ước **THÂN XE** (`lf · rf · lr · rr`), KHÔNG phải quy ước TPMS (`fl · fr · rl · rr`) mà
 * [TyreCorner] dùng. Kiểm kê U7 §2 đã ghi: bộ đăng ký có tới bốn quy ước hậu tố cho cùng một góc xe, và đổi chỗ
 * giữa chúng là lỗi **im lặng** — bảng vẫn vẽ đủ bốn vạt, chỉ là vạt sau-trái mang trạng thái của cửa trước-trái.
 * Tên hằng ở đây bám đúng chữ trong mã datum để chỗ nối đọc ra được bằng mắt.
 */
enum class CarPart {
    DOOR_LF, DOOR_RF, DOOR_LR, DOOR_RR, TAILGATE, SUNROOF, SUNSHADE, MIRROR;

    /**
     * Có phải một trong **bốn cửa** không.
     *
     * Dòng kết luận đếm cửa riêng (*"2 cửa mở"*) và kể tên các khoang còn lại (*"Cốp · Mở"*): bốn cửa là **một loại**
     * nên đếm là đủ, còn cốp/nóc/rèm/gương mỗi thứ một nghĩa nên đếm gộp sẽ ra một câu vô nghĩa (*"3 thứ đang mở"*).
     */
    val isDoor: Boolean
        get() = this == DOOR_LF || this == DOOR_RF || this == DOOR_LR || this == DOOR_RR
}

/**
 * Trạng thái ĐÃ QUYẾT ĐỊNH của một bộ phận trên bảng *Cửa & khoang* — `:app` chỉ vẽ, không phán xét.
 *
 * @property label nhãn ngắn của datum chính ([GroupCell.label]) — dùng cho dòng kết luận.
 * @property value giá trị đọc được, ưu tiên bản **có số** (cốp/nóc có cả trạng thái lẫn phần trăm) — dùng cho dòng
 *   kết luận, nên nó mang cả đơn vị (xem [GroupCell.value]).
 * @property note nhãn NGẮN vẽ ngay cạnh bộ phận, **chỉ khi bộ phận có số** (rèm %, nóc %, cốp %); rỗng với bốn cửa
 *   và gương vì chúng chỉ có đóng/mở — vẽ chữ *"Mở"* lên vạt cửa đã tô màu là nói hai lần một điều.
 * @property tone sắc thái NẶNG NHẤT trong các datum của bộ phận (cốp có hai: trạng thái ALERT + phần trăm ACTIVE).
 * @property open bộ phận có **đang khác trạng thái nghỉ** không ⇒ `:app` vẽ VÙNG TÔ. Quyết ở đây chứ không để tầng
 *   vẽ suy từ [tone]: *"tô khi nào"* là một câu hỏi về dữ liệu (gương **gập** cũng là "đang khác", dù gập không phải
 *   là "mở"), và câu trả lời phải nằm cùng chỗ với luật sắc thái để hai thứ không lệch nhau.
 * @property available đã đọc được chưa. Off-car là ca **thường** ⇒ `:app` làm mờ nét, KHÔNG bịa trạng thái đóng.
 */
data class CarPartState(
    val part: CarPart,
    val label: String,
    val value: String,
    val note: String,
    val tone: GroupTone,
    val open: Boolean,
    val available: Boolean,
)

/**
 * KẾ HOẠCH TRÌNH BÀY của bảng *Cửa & khoang* — quyết định ở `:core`, vẽ ở `:app`.
 *
 * **Không phụ thuộc bề cao ô**: bảng này vẽ theo hình học thật của xe (vạt cửa ở đúng góc xe), nên không có phép
 * "bớt hàng cho vừa" — bộ phận nào cũng phải ở đúng chỗ của nó, hoặc cả bảng thu nhỏ lại.
 *
 * @property parts đúng thứ tự khai của [CarPart] và chỉ gồm bộ phận **có datum trong nhóm** (nhóm khác gọi nhầm thì
 *   được danh sách rỗng chứ không phải một bảng vẽ bừa).
 * @property footer dòng KẾT LUẬN (*"Tất cả đã đóng"* / *"2 cửa mở"* / *"chưa đọc được"*) — câu này phân biệt
 *   *"đã đọc, đóng hết"* với *"chưa đọc được"*.
 */
data class DoorBoardPlan(
    val parts: List<CarPartState>,
    val footer: String,
)
