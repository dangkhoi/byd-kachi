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
        /** Trần chip. Thanh trên còn phải chứa đồng hồ + ngày + chọn bố cục + hồ sơ; quá 4 chip là tràn. */
        const val CAP = 4

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

        /** Đặt được lên thanh trên: 3 chip dựng sẵn, hoặc một datum ĐỌC. Gói lệnh/nút thì KHÔNG (xem KDoc lớp). */
        fun isChippable(id: String): Boolean =
            id in BUILT_IN || CapabilityCatalog.kindOf(id) == CapabilityKind.READ

        /** Mọi thứ đặt được lên thanh trên, cho màn chọn bày ra. */
        fun choices(): List<CapabilityPick> = buildList {
            add(CapabilityPick(PM25, "Bụi mịn PM2.5", "ic-leaf", EvidenceTier.PROVEN, CapabilityKind.READ, Domain.CLIMATE))
            add(CapabilityPick(TEMP, "Nhiệt độ ngoài", "ic-fan", EvidenceTier.PROVEN, CapabilityKind.READ, Domain.CLIMATE))
            add(CapabilityPick(ENERGY, "Pin và tầm chạy", "ic-bolt", EvidenceTier.PROVEN, CapabilityKind.READ, Domain.ENERGY))
            addAll(CapabilityCatalog.all().filter { it.kind == CapabilityKind.READ && !it.curated })
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
            val pm = status.climate.pm25Level?.let { if (it <= 2) "Tốt" else if (it <= 4) "TB" else "Kém" } ?: "—"
            ChipView("PM2.5 · $pm", "ic-leaf", ChipTone.NEUTRAL, "Bụi mịn trong xe: $pm")
        }
        TopStripConfig.TEMP -> {
            val u = units.unitFor(Quantity.TEMPERATURE)
            val t = status.climate.outsideTempC?.let { conv(it.toDouble(), Quantity.TEMPERATURE, units) } ?: "—"
            ChipView("$t$u ngoài", null, ChipTone.NEUTRAL, "Nhiệt độ ngoài xe $t$u")
        }
        TopStripConfig.ENERGY -> {
            val u = units.unitFor(Quantity.DISTANCE)
            val r = status.energy.evRangeKm?.let { conv(it.toDouble(), Quantity.DISTANCE, units) } ?: "—"
            ChipView("${status.energy.soc ?: "—"}% · $r $u", "ic-bolt", ChipTone.ENERGY,
                "Pin ${status.energy.soc ?: "chưa đọc được"} phần trăm, đi thêm $r $u")
        }
        else -> datumChip(id, status, units)
    }

    /** Chip cho một datum thường: `"<nhãn ngắn> · <giá trị><đơn vị>"`, đi qua ĐÚNG lớp đơn vị như mọi bề mặt khác. */
    private fun datumChip(id: String, status: CarStatus, units: UnitPrefs): ChipView? {
        val spec = TelemetryRegistry.byId(id) ?: return null
        val view = TelemetryReadout.of(id, status)?.let { UnitFormat.apply(it, units) } ?: return null
        val value = view.displayWithUnit()
        return ChipView(
            text = "${spec.shortLabel} · $value",
            icon = CapabilityIcons.forTelemetry(spec.id, spec.domain),
            tone = ChipTone.NEUTRAL,
            desc = "${spec.label}: $value",
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
