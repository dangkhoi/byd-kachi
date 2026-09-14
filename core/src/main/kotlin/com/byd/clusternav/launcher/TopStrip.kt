package com.byd.clusternav.launcher

/**
 * Một chip trên thanh trạng thái sau khi đã quyết định xong nội dung — **thuần dữ liệu**, không biết View.
 *
 * @property text chữ hiện ra (đã qua lớp đơn vị).
 * @property icon tên icon `ic-*`, hoặc `null` nếu chip chỉ có chữ.
 * @property tone SẮC THÁI, không phải mã màu — bảng màu nằm ở `:app` (`KachiTheme`). Nếu `:core` giữ mã hex thì dự án
 *   có **hai bảng màu** và chúng sẽ lệch nhau: [ĐO] bản nháp đầu của tệp này viết `#37d67a` trong khi `KachiTheme.GREEN`
 *   là `#34d399` — đúng cái bẫy hai-bản-sao mà dự án đang dọn.
 * @property desc câu đọc cho trình đọc màn hình — chip rất ngắn nên chữ hiện ra thường không đủ nghĩa.
 */
data class ChipView(val text: String, val icon: String?, val tone: ChipTone, val desc: String)

/** Sắc thái chip. `:app` dịch sang mã màu — xem [ChipView.tone]. */
enum class ChipTone { NEUTRAL, ENERGY }

/**
 * MỘT KHỐI của màn chọn chip — xem [TopStripConfig.picks].
 *
 * Cố ý **không mang tiêu đề dạng chuỗi**: tiêu đề của khối lĩnh vực là [Domain.displayLabel] (đã có ở `:core`),
 * còn tiêu đề khối *"đang bật"* phải kèm con số `N/${TopStripConfig.CAP}` nên nó là một chuỗi **tài nguyên** của
 * `:app` (VI/EN). Dựng sẵn một chuỗi ở đây là ép `:core` giữ bản sao thứ hai của cùng một câu.
 *
 * @property on khối *"đang bật"* (đúng một khối, luôn đứng đầu).
 * @property domain lĩnh vực của khối; `null` ⇒ khối *"đang bật"* ([on]) hoặc khối mục chưa xếp lĩnh vực.
 * @property picks các ô của khối — mỗi mã chỉ xuất hiện ở **một** khối (xem cảnh báo `tiles[id]` ở [TopStripConfig.picks]).
 * @property open mặc định mở hay gấp. Tầng vẽ giữ trạng thái của phiên, giá trị ban đầu quyết ở `:core`.
 */
data class ChipSection(
    val on: Boolean,
    val domain: Domain?,
    val picks: List<CapabilityPick>,
    val open: Boolean,
)

/**
 * CẤU HÌNH THANH TRÊN — danh sách khả năng hiện thành chip, thứ tự = thứ tự hiển thị.
 *
 * Đây là **vùng thứ ba** của RW0 ("đặt được ở thanh trên / ô giữa màn / thanh tác vụ"). Trước 2026-09-11 thanh trên là
 * **3 chip viết cứng** trong bộ vẽ, nên nó là vùng duy nhất người dùng không sửa được.
 *
 * ⚠⚠ **CHỈ NHẬN MỤC ĐỌC — quyết định có chủ ý, không phải bỏ sót.** Hai lý do:
 *  1. **An toàn**: chip cao ~24dp, dưới xa mức tối thiểu 48dp cho một đích chạm. Một cú chạm lệch trên thanh trên mà
 *     bắn lệnh xe (vd "Mở khoá cửa") là hậu quả không hoàn lại được. Nút có hai vùng khác rộng rãi hơn để đặt.
 *  2. **Ngữ nghĩa**: thanh trên là dòng trạng thái nhìn-là-biết, không phải bảng điều khiển.
 * Nếu owner muốn chip bấm được thì đó là một quyết định riêng, cần đích chạm to hơn — KHÔNG lặng lẽ nới ở đây.
 */
data class TopStripConfig(val ids: List<String> = DEFAULT_IDS) {

    init {
        // ⚠ Chốt ở CHÍNH LỚP, không chỉ ở [setEnabled]. Quét bảo mật trước commit nêu đúng: nếu cổng chỉ nằm ở
        // `setEnabled` thì lớp đang dựa vào "mọi chỗ gọi trong tương lai đều nhớ đi qua nó" — tức một luật con người
        // phải nhớ, đúng loại giả định dự án đã trả giá (bẫy hai-bản-sao). Ở đây hậu quả nếu quên là một mã HÀNH ĐỘNG
        // lọt lên thanh trên, tức một đích chạm 24dp bắn lệnh xe không hoàn lại được.
        require(ids.all { isChippable(it) }) {
            "thanh trên chỉ nhận mục ĐỌC — mã không hợp: " + ids.filterNot { isChippable(it) }
        }
        require(ids.size <= CAP) { "thanh trên chứa tối đa $CAP chip, nhận ${ids.size}" }
    }

    /**
     * Bật/tắt một khả năng. Từ chối mã lạ (không nhét rác vào cấu hình bền) **và** từ chối mã HÀNH ĐỘNG (xem KDoc
     * lớp) — cùng tinh thần với [DockConfig.setEnabled], chỉ khác ở chỗ vùng này hẹp hơn có lý do.
     */
    fun setEnabled(id: String, on: Boolean): TopStripConfig {
        if (!on) return copy(ids = ids - id)
        if (id in ids) return this
        if (ids.size >= CAP) return this                       // đầy thì bỏ qua, không đẩy mục khác ra
        if (!isChippable(id)) return this
        return copy(ids = ids + id)
    }

    fun has(id: String): Boolean = id in ids

    companion object {
        /**
         * TRẦN CHIP — **8** từ S4 · R11 (owner 2026-09-14: *"hiện chỉ cho chọn 3 trong khi có thể chọn nhiều hơn"*).
         *
         * ## Vì sao 4 → 8, và vì sao con số này không còn là "chỗ trống chia cho bề rộng một chip"
         * Trần cũ (4) được đặt khi thanh trên còn mang **5 nút bố cục**; R7 gỡ hàng nút đó ⇒ thanh trên trống thêm
         * một quãng rộng. Nhưng điều đổi hẳn cách chọn con số là bản vá đi kèm ở tầng vẽ
         * (`KachiTopStrip.fitChips`): chip nay **tự co + cắt "…"** theo bề rộng còn lại, nên vượt trần không còn
         * nghĩa là *tràn đè lên nút bên phải* — nó chỉ nghĩa là *mỗi chip đọc được ít chữ hơn*.
         *
         * [SUY] theo số ở 1920px (chưa [ĐO] ảnh — việc của S4 · T4): thanh trên trừ lề + đồng hồ/ngày + ba vật bên
         * phải (chip hồ sơ · Ứng dụng · Cài đặt) còn ≈ 1240dp cho hàng chip ⇒ 8 chip ≈ **155dp/chip**, đủ cho
         * `"Lốp TT · 2.4 bar"` (≈ 160dp, cắt đuôi một chút) và thừa cho `"82% · 418 km"`. 12 chip thì còn ≈ 103dp
         * ⇒ phần lớn chip chỉ còn nhãn cụt, tức thanh trên **có chữ mà không đọc được** — tệ hơn là không bày.
         * Vì thế trần vẫn tồn tại, chỉ đổi số; nó là trần **đọc được**, không còn là trần *vừa khung*.
         */
        const val CAP = 8

        /** Ba chip TỔNG HỢP dựng sẵn — xem [TopStripChips]. */
        const val PM25 = "chip_pm25"
        const val TEMP = "chip_outside_temp"
        const val ENERGY = "chip_energy"

        /**
         * ⚠ **THỨ TỰ KHAI QUAN TRỌNG**: [BUILT_IN] phải nằm TRƯỚC [DEFAULT], vì `init` của lớp gọi [isChippable] mà
         * hàm đó đọc [BUILT_IN] — [ĐO] khai sau thì việc dựng [DEFAULT] lúc nạp lớp đọc `BUILT_IN` còn null và cả
         * gói test nổ `ExceptionInInitializerError` (27 bài đỏ). Ba hằng `PM25`/`TEMP`/`ENERGY` là `const` nên an toàn.
         */
        val BUILT_IN: Set<String> = setOf(PM25, TEMP, ENERGY)

        /** Mặc định = **đúng 3 chip đang có**, để ai không sửa gì thì không thấy gì khác (có test khoá). */
        val DEFAULT_IDS: List<String> = BUILT_IN.toList()
        val DEFAULT = TopStripConfig(DEFAULT_IDS)

        /**
         * Đặt được lên thanh trên: 3 chip dựng sẵn, hoặc một datum ĐỌC. Gói lệnh/nút thì KHÔNG (xem KDoc lớp).
         *
         * ⚠⚠ **NHÓM khả năng (G1) cũng KHÔNG**, dù [CapabilityCatalog.kindOf] trả [CapabilityKind.READ] cho nó. Hai
         * lý do, và cả hai là lý do của CHÍNH chỗ này chứ không phải của loại khả năng:
         *  1. **Không vẽ được**: chip cao ~24dp một dòng chữ; nhóm là bảng 4 bánh / dải 9 đèn / thẻ 10 con số. Nhồi
         *     vào chip thì ra một ô hiện được đúng cái nhãn — mất hết thứ khiến nhóm có ích.
         *  2. **An toàn**: 3/12 nhóm mang nút (kính · cửa & khoang · đèn). Cho nhóm lên đây là mở lại đúng cái cửa mà
         *     KDoc lớp này đóng: một đích chạm 24dp bắn lệnh xe không hoàn lại được.
         * Chặn ở ĐÂY thay vì bắt nhóm khai [CapabilityKind.WRITE] cho "khỏi lọt": làm thế sẽ khiến ô giữa màn dựng
         * nhóm thành một cái nút đơn và mất hết thành viên (xem KDoc [CapabilityCatalog.kindOf]). Hạn chế là của
         * thanh trên, nên nó phải nằm trong thanh trên.
         */
        // ⚠ S4 · R12 — [CapabilityKind.LAUNCHER] cũng bị từ chối ở đây, và **do cấu tạo**: điều kiện là
        // "== READ", không phải "!= WRITE". Viết theo chiều phủ định thì mỗi loại khả năng mới lại lọt lên thanh
        // trên cho tới khi có ai nhớ ra phải chặn — chiều khẳng định thì loại mới mặc định KHÔNG lọt.
        // Bài canh: `TopStripTest.hanh dong cua launcher KHONG len duoc thanh tren`.
        fun isChippable(id: String): Boolean =
            id in BUILT_IN ||
                (CapabilityCatalog.kindOf(id) == CapabilityKind.READ && CapabilityGroups.byId(id) == null)

        /** Mọi thứ đặt được lên thanh trên, cho màn chọn bày ra. */
        fun choices(): List<CapabilityPick> = buildList {
            // U5 · T2: ba chip TỔNG HỢP không có dòng registry nào để treo `labelEn` vào ⇒ dựng [CapabilityPick] với
            // nhãn Việt + `labelEn` ngay tại chỗ, đúng cùng cơ chế như mọi mục khác (xem KDoc [Strings]).
            // ⚠⚠ **KHÔNG đặt lại tên này thành "Bụi mịn PM2.5"** — đó là tên của datum `pm25_value` từ U6, và hai
            // dòng ấy đứng CẠNH NHAU trong CÙNG khối "Khí hậu & không khí" của màn chọn ([picks] xếp theo [Domain],
            // ô chỉ có nhãn — không vẽ dòng phụ). [ĐO] soát U6: đổi `pm25_value` từ "PM2.5" sang "Bụi mịn PM2.5" đã
            // làm màn chọn có **hai ô chữ y hệt** (cả VI lẫn EN) trỏ vào hai việc khác nhau — chip này đổi mức 1–6
            // thành CHỮ ("PM2.5 · Tốt"), datum kia là TRỊ SỐ µg/m³. Tên ở đây phải nói đúng thứ nó hiện: một lời
            // nhận xét về không khí trong xe. Bài canh: `TopStripTest.hai o tren cung mot man chon…`.
            add(CapabilityPick(PM25, "Không khí trong xe", "ic-leaf", EvidenceTier.PROVEN, CapabilityKind.READ, Domain.CLIMATE,
                labelEn = "Cabin air quality"))
            add(CapabilityPick(TEMP, "Nhiệt độ ngoài", "ic-fan", EvidenceTier.PROVEN, CapabilityKind.READ, Domain.CLIMATE,
                labelEn = "Outside temperature"))
            add(CapabilityPick(ENERGY, "Pin và tầm chạy", "ic-bolt", EvidenceTier.PROVEN, CapabilityKind.READ, Domain.ENERGY,
                labelEn = "Battery and range"))
            // Lọc bằng CHÍNH [isChippable] thay vì viết lại điều kiện `kind == READ`: bản cũ lặp lại luật, nên khi
            // luật ở [isChippable] chặt thêm (G1 loại NHÓM) thì màn chọn vẫn bày ra thứ mà [setEnabled] sẽ từ chối —
            // người dùng bấm mà không có gì xảy ra. Một luật, một chỗ.
            addAll(CapabilityCatalog.all().filter { !it.curated && isChippable(it.id) })
        }

        /**
         * MÀN CHỌN CHIP theo **KHỐI**: khối *"đang bật"* trước, rồi mỗi [Domain] một khối (S4 · R11 c).
         *
         * ## Vì sao bày theo khối chứ không phải một danh sách phẳng
         * Tới U6 màn chọn chỉ bày **3 chip dựng sẵn + chip đang bật** (≤ 7 ô); muốn đặt một datum bất kỳ thì phải
         * biết có một nút *"Thêm chip khác…"* mở một hộp thoại **phẳng 120+ dòng**. Owner 2026-09-14: *"hiện chỉ
         * cho chọn 3 trong khi có thể chọn nhiều hơn"* — tức thứ hụt không phải cái trần, mà là **thứ nhìn thấy
         * được**. Một danh sách phẳng 120 dòng thì bày ra cũng như không; xếp theo lĩnh vực thì mỗi khối là một
         * câu hỏi người dùng thật sự có (*"xe còn bao nhiêu pin"*, *"lốp thế nào"*), và R4 (mỗi nhóm ≤ 2 màn cuộn)
         * còn giữ được nhờ tầng vẽ gấp/mở từng khối.
         *
         * ## Ba tính chất mà chỗ gọi được dựa vào (có test khoá từng cái)
         *  1. **Mỗi mã đúng MỘT ô.** Cả hai màn chọn giữ bảng tra `tiles[id] → view` để tô ô đang bật; một mã hai ô
         *     thì `tiles[id]` bị ghi đè và ô trước nói sai cấu hình (đúng lỗi RW0, và là lý do
         *     [CapabilityPicker.singlesOf] tồn tại). Vì thế mục đang bật bị **trừ khỏi** khối lĩnh vực của nó.
         *  2. **Chip mang mã đã ẩn vẫn có ô để GỠ.** U6 thêm [CapabilityCatalog.HIDDEN_FROM_PICKER] (lọc ở `all()`)
         *     còn [decode] **giữ** mã ẩn vì nó lọc bằng [isChippable] chứ không bằng danh sách — đúng thiết kế
         *     (khoá lưu bền của người dùng không được mất). Nếu khối *"đang bật"* cũng dựng từ [choices] thì chip
         *     ấy hiện trên thanh mà **không còn ô nào để bấm gỡ**: một trạng thái không có đường ra. Nên khối này
         *     tra thẳng [CapabilityCatalog.pick] cho mã mà [choices] bỏ sót — và chỉ ở đây, [choices] vẫn phải im
         *     lặng về mã đã ẩn (bày lại chính là đường mời đặt thêm).
         *  3. **[ChipSection.open] là một LUẬT, không phải trạng thái.** Mặc định mở đúng những lĩnh vực đang có
         *     chip trên thanh — nơi người dùng nhiều khả năng muốn thêm cái kế bên. Tầng vẽ giữ trạng thái gấp/mở
         *     của phiên, nhưng giá trị **ban đầu** quyết ở đây để kiểm được off-car.
         */
        fun picks(cfg: TopStripConfig): List<ChipSection> {
            val all = choices()
            val byId = all.associateBy { it.id }
            // Theo ĐÚNG thứ tự chip trên thanh, không theo thứ tự bộ đăng ký: khối này là ảnh của thanh trên.
            val on = cfg.ids.mapNotNull { byId[it] ?: CapabilityCatalog.pick(it) }
            val onIds = on.mapTo(mutableSetOf()) { it.id }
            // [ĐO] ảnh máy ảo 2026-09-14 (T4): mặc định mở lĩnh vực đang có chip ⇒ với 3 chip sẵn là Năng lượng (28 ô)
            // + Khí hậu (11 ô) mở cùng lúc, trang dài quá trần R4 (≤2 màn cuộn). Nay MỌI lĩnh vực gấp sẵn; khối
            // "đang bật" luôn mở nên câu "thanh trên đang có gì" vẫn trả lời ngay; muốn thêm thì mở đúng lĩnh vực.
            return buildList {
                add(ChipSection(on = true, domain = null, picks = on, open = true))
                Domain.values().forEach { d ->
                    val rest = all.filter { it.domain == d && it.id !in onIds }
                    if (rest.isNotEmpty()) add(ChipSection(false, d, rest, open = false))
                }
                // Mục ĐỌC chưa xếp lĩnh vực: hôm nay là danh sách RỖNG (mọi datum đều khai [Domain], widget dựng tay
                // thì `curated` nên [choices] đã loại). Vẫn dựng khối cuối thay vì bỏ im lặng — thêm một mục đọc
                // không-lĩnh-vực về sau thì nó phải hiện ra ở đâu đó, chứ không biến mất khỏi màn chọn.
                val loose = all.filter { it.domain == null && it.id !in onIds }
                if (loose.isNotEmpty()) add(ChipSection(false, null, loose, open = true))
            }
        }

        /** `"a,b,c"` → cấu hình. Chuỗi rỗng/lỗi ⇒ mặc định (không để thanh trên trắng vì một dòng prefs hỏng). */
        fun decode(s: String?): TopStripConfig {
            val ids = s?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: return DEFAULT
            if (ids.isEmpty()) return DEFAULT
            // Lọc mã không còn đặt được (bản sau xoá một datum / người dùng sửa tay) — im lặng bỏ MỤC, không bỏ cả dòng.
            val kept = ids.filter { isChippable(it) }.distinct().take(CAP)
            return if (kept.isEmpty()) DEFAULT else TopStripConfig(kept)
        }

        fun encode(c: TopStripConfig): String = c.ids.joinToString(",")
    }
}

/**
 * DỰNG CHIP cho thanh trên — **thuần**, kiểm được off-car (đây là chỗ từng có lỗi "chip bỏ qua lựa chọn đơn vị").
 *
 * Ba mã dựng sẵn là chip **TỔNG HỢP**, không phải một datum: `PM2.5 · Tốt` (đổi mức 1–6 thành chữ), `24°C ngoài`,
 * `82% · 418 km` (**hai** datum trong một chip). Vì thế chúng không biểu diễn được bằng bảng datum thường ⇒ giữ nguyên
 * dạng dựng sẵn, và [ĐO] có test khoá chuỗi ra **đúng như bản viết cứng cũ** — nới chỗ này KHÔNG được đổi thứ owner
 * đang thấy.
 */
object TopStripChips {

    fun render(cfg: TopStripConfig, status: CarStatus, units: UnitPrefs = UnitPrefs.DEFAULT): List<ChipView> =
        cfg.ids.mapNotNull { chip(it, status, units) }

    private fun chip(id: String, status: CarStatus, units: UnitPrefs): ChipView? = when (id) {
        TopStripConfig.PM25 -> {
            val pm = status.climate.pm25Level?.let {
                if (it <= 2) Strings.t("Tốt", "Good") else if (it <= 4) Strings.t("TB", "Fair") else Strings.t("Kém", "Poor")
            } ?: TelemetryView.PLACEHOLDER
            ChipView("PM2.5 · $pm", "ic-leaf", ChipTone.NEUTRAL, Strings.t("Bụi mịn trong xe: $pm", "Fine dust in the car: $pm"))
        }
        TopStripConfig.TEMP -> {
            val u = units.unitFor(Quantity.TEMPERATURE)
            val t = status.climate.outsideTempC?.let { conv(it.toDouble(), Quantity.TEMPERATURE, units) }
                ?: TelemetryView.PLACEHOLDER
            ChipView(
                "$t$u " + Strings.t("ngoài", "outside"), null, ChipTone.NEUTRAL,
                Strings.t("Nhiệt độ ngoài xe $t$u", "Outside temperature $t$u"),
            )
        }
        TopStripConfig.ENERGY -> {
            val u = units.unitFor(Quantity.DISTANCE)
            val r = status.energy.evRangeKm?.let { conv(it.toDouble(), Quantity.DISTANCE, units) }
                ?: TelemetryView.PLACEHOLDER
            val soc = status.energy.soc
            ChipView(
                "${soc ?: TelemetryView.PLACEHOLDER}% · $r $u", "ic-bolt", ChipTone.ENERGY,
                Strings.t(
                    "Pin ${soc ?: "chưa đọc được"} phần trăm, đi thêm $r $u",
                    "Battery ${soc ?: "not read yet"} per cent, $r $u to go",
                ),
            )
        }
        else -> datumChip(id, status, units)
    }

    /** Chip cho một datum thường: `"<nhãn ngắn> · <giá trị><đơn vị>"`, đi qua ĐÚNG lớp đơn vị như mọi bề mặt khác. */
    private fun datumChip(id: String, status: CarStatus, units: UnitPrefs): ChipView? {
        val spec = TelemetryRegistry.byId(id) ?: return null
        val view = TelemetryReadout.of(id, status)?.let { UnitFormat.apply(it, units) } ?: return null
        val value = view.displayWithUnit()
        return ChipView(
            // U5 · T2: nhãn ngắn THEO NGÔN NGỮ. Chip là bề mặt hẹp nhất của launcher nên nó cần đúng bản ngắn, không
            // phải nhãn đầy — lý do `shortEn` tồn tại.
            text = "${spec.displayShortLabel} · $value",
            icon = CapabilityIcons.forTelemetry(spec.id, spec.domain),
            tone = ChipTone.NEUTRAL,
            desc = "${spec.displayLabel}: $value",
        )
    }

    /** Đổi một số về đơn vị người dùng chọn, dùng CHUNG bộ chuyển của [Units] (không tự nhân chia tại chỗ). */
    private fun conv(v: Double, q: Quantity, units: UnitPrefs): String {
        val base = Units.BASE[q] ?: return v.toInt().toString()
        val raw = TelemetryView("chip", "", base, WidgetShape.VALUE, EvidenceTier.PROVEN,
            if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString())
        return UnitFormat.apply(raw, units).display
    }
}
