package com.byd.clusternav.launcher

/**
 * DỰNG CHIP cho thanh trên — **thuần**, kiểm được off-car (đây là chỗ từng có lỗi "chip bỏ qua lựa chọn đơn vị").
 *
 * ⚠ **Tách khỏi `TopStrip.kt` ngày 2026-09-27 (UX5b)** — tách theo **VAI**, không theo số dòng: tệp kia là *cấu hình
 * bền* của thanh trên (danh sách mã · trần · mặc định · mã hoá/giải mã · phép **di trú**), còn đây là *phép dựng chữ
 * và hình* cho một mã. Hai vai đọc vào hai lúc khác nhau: thêm một chip thì mở tệp kia; hỏi *"chip này hiện ra chữ
 * gì"* thì mở đây. Lượt tách không đổi một dòng hành vi nào (cùng package, cùng tên, cùng chữ ký) — nó xảy ra vì
 * UX5b thêm chip ghế thứ hai và tệp gộp sẽ vượt trần 500 dòng (CLAUDE.md §4.1).
 *
 * Năm mã dựng sẵn là chip **TỔNG HỢP**, không phải một datum: `PM2.5 · Tốt` (đổi mức 1–6 thành chữ), `24°C ngoài`,
 * `82% · 418 km` (**hai** datum trong một chip), và hai chip **ghế** (mỗi cái gộp sưởi + mát của MỘT ghế — xem
 * [seatChip]). Vì thế chúng không biểu diễn được bằng bảng datum thường ⇒ giữ nguyên dạng dựng sẵn, và [ĐO] có test
 * khoá chuỗi ra **đúng như bản viết cứng cũ** — nới chỗ này KHÔNG được đổi thứ owner đang thấy.
 */
object TopStripChips {

    fun render(cfg: TopStripConfig, status: CarStatus, units: UnitPrefs = UnitPrefs.DEFAULT): List<ChipView> =
        cfg.ids.mapNotNull { chip(it, status, units, cfg.showLabels) }

    /**
     * ⚠ [ChipView.desc] (câu cho trình đọc màn hình) **luôn đầy đủ**, kể cả khi [labels] tắt.
     *
     * Tắt nhãn là một quyết định về **chỗ trên thanh**, không phải về nội dung: một chip chỉ còn `24°C` thì mắt
     * người vẫn đọc được nhờ icon và vị trí, còn trình đọc màn hình thì phát ra đúng hai chữ *"24 độ C"* và người
     * không nhìn được màn hình mất hẳn thông tin *"của cái gì"*.
     */
    private fun chip(id: String, status: CarStatus, units: UnitPrefs, labels: Boolean): ChipView? = when (id) {
        TopStripConfig.PM25 -> {
            val pm = status.climate.pm25Level?.let {
                if (it <= 2) Strings.t("Tốt", "Good") else if (it <= 4) Strings.t("TB", "Fair") else Strings.t("Kém", "Poor")
            } ?: TelemetryView.PLACEHOLDER
            ChipView(
                if (labels) "PM2.5 · $pm" else pm, "ic-leaf", ChipTone.NEUTRAL,
                Strings.f("Bụi mịn trong xe: {0}", "Fine dust in the car: {0}", pm),
            )
        }
        TopStripConfig.TEMP -> {
            val u = units.unitFor(Quantity.TEMPERATURE)
            val t = status.climate.outsideTempC?.let { conv(it.toDouble(), Quantity.TEMPERATURE, units) }
                ?: TelemetryView.PLACEHOLDER
            ChipView(
                if (labels) "$t$u " + Strings.t("ngoài", "outside") else "$t$u", null, ChipTone.NEUTRAL,
                Strings.f("Nhiệt độ ngoài xe {0}{1}", "Outside temperature {0}{1}", t, u),
            )
        }
        TopStripConfig.ENERGY -> {
            val u = units.unitFor(Quantity.DISTANCE)
            val r = status.energy.evRangeKm?.let { conv(it.toDouble(), Quantity.DISTANCE, units) }
                ?: TelemetryView.PLACEHOLDER
            val soc = status.energy.soc
            ChipView(
                "${soc ?: TelemetryView.PLACEHOLDER}% · $r $u", "ic-bolt", ChipTone.ENERGY,
                Strings.f(
                    "Pin {0} phần trăm, đi thêm {1} {2}",
                    "Battery {0} per cent, {1} {2} to go",
                    soc ?: Strings.t("chưa đọc được", "not read yet"), r, u,
                ),
            )
        }
        // ⚠ UX5b — HAI dòng, MỘT bộ dựng. Khác nhau đúng ba dữ liệu: cặp mã (tra ở [TopStripConfig.SEAT_PAIRS]),
        // nhãn ghế, và hình ghế-trống của đúng cạnh đó. Chép bộ dựng ra hai bản là cách chắc chắn để hai chip lệch
        // nhau sau lượt sửa thứ ba (CLAUDE.md §7).
        TopStripConfig.SEAT -> seatChip(id, Strings.t("Ghế lái", "Driver seat"), "ic-seat-left", status, labels)
        TopStripConfig.SEAT_R -> seatChip(id, Strings.t("Ghế phụ", "Passenger seat"), "ic-seat", status, labels)
        // 2.88 · bốn số áp suất, mỗi số một màu theo lời phán của chính xe — xem [tyreChip].
        TopStripConfig.TYRES -> tyreChip(status.tyres, units, labels)
        else -> datumChip(id, status, units, labels)
    }

    /**
     * ═══ 2.88 · CHIP ÁP SUẤT LỐP — `"2.4 2.4  2.6 2.6"`, mỗi số một màu (spec `kachi-288-tyre-car-state.html` R3/R4) ═══
     *
     * Thứ tự TT TP · ST SP: MỘT dấu cách trong cùng trục, HAI dấu cách giữa trục trước và trục sau (đọc như một hình
     * xe nhìn từ trên). Số đi qua CHUNG bộ đổi đơn vị ([conv] → [UnitPrefs] của hồ sơ: bar 1 chữ số · kPa 0 · psi 1),
     * KHÔNG in chữ đơn vị (chip là bề mặt hẹp nhất; đơn vị nằm ở câu trình đọc màn hình).
     *
     * Màu = [TyreBoard.readings] — nguồn phán DUY NHẤT (chip · bảng · nhóm · ô nhỏ cùng đọc): xanh = [ChipTone.ENERGY]
     * (cùng mực chip năng lượng), vàng = [ChipTone.WARN], đỏ = [ChipTone.ALERT], xám = [ChipTone.NEUTRAL]. Bánh không có
     * số in `"—"` mà VẪN tô màu xe phán (xe đo gián tiếp vẫn có thể có màu cụm). Cả bốn không số và cả bốn xám ⇒ MỘT
     * dấu `"—"` (cùng kiểu mọi chip khác). Icon lấy màu nặng nhất; cả bốn xanh ⇒ xanh; còn lại trung tính.
     *
     * Chỉ đọc áp suất + mức nặng của [TyreReading] — KHÔNG đọc nhiệt (`tempC`), nên [CarDataDemand.CHIPS] chỉ khai bốn
     * mã áp suất (bài `CarDataDemandRendererContractTest` canh đúng điều này).
     */
    private fun tyreChip(tyres: CarStatus.Tyres, units: UnitPrefs, labels: Boolean): ChipView {
        val readings = TyreBoard.readings(tyres)
        val label = Strings.t("Lốp", "Tyres")
        val prefix = if (labels) "$label · " else ""
        val unit = units.unitFor(Quantity.PRESSURE)
        val nums = readings.map { rd -> rd.pressureKpa?.let { conv(it, Quantity.PRESSURE, units) } }
        val icon = CapabilityGroups.TYRES.icon
        if (nums.all { it == null } && readings.all { it.severity == TyreSeverity.NONE }) {
            return ChipView(
                prefix + TelemetryView.PLACEHOLDER, icon, ChipTone.NEUTRAL,
                Strings.f("Áp suất lốp: {0}", "Tyre pressure: {0}", Strings.t("chưa đọc được", "not read yet")),
            )
        }
        val sb = StringBuilder(prefix)
        val runs = ArrayList<ChipRun>(4)
        readings.forEachIndexed { i, rd ->
            if (i > 0) sb.append(if (i == AXLE_SPLIT) AXLE_GAP else WHEEL_GAP)
            val start = sb.length
            sb.append(nums[i] ?: TelemetryView.PLACEHOLDER)
            runs += ChipRun(start, sb.length, toneOf(rd.severity))
        }
        val worst = TyreBoard.worst(readings)
        val tone = when {
            worst.alert -> toneOf(worst)
            readings.all { it.severity == TyreSeverity.OK } -> ChipTone.ENERGY
            else -> ChipTone.NEUTRAL
        }
        val parts = readings.mapIndexed { i, rd ->
            val value = nums[i]?.let { "$it $unit" } ?: Strings.t("chưa đọc được áp suất", "pressure not read yet")
            val corner = rd.corner.displayLabel
            when (rd.severity) {
                TyreSeverity.OK -> Strings.f("{0} {1}, bình thường", "{0} {1}, normal", corner, value)
                TyreSeverity.NONE -> Strings.f("{0} {1}, chưa rõ", "{0} {1}, unknown", corner, value)
                TyreSeverity.WARN -> Strings.f("{0} {1}, chú ý: {2}", "{0} {1}, caution: {2}", corner, value, rd.reason)
                TyreSeverity.ALERT -> Strings.f("{0} {1}, cảnh báo: {2}", "{0} {1}, alert: {2}", corner, value, rd.reason)
            }
        }
        return ChipView(
            sb.toString(), icon, tone,
            Strings.f("Áp suất lốp: {0}", "Tyre pressure: {0}", parts.joinToString("; ")),
            runs,
        )
    }

    /** Mức nặng của một bánh → sắc thái chip. Một bảng, vét cạn (không `else`). */
    private fun toneOf(sev: TyreSeverity): ChipTone = when (sev) {
        TyreSeverity.OK -> ChipTone.ENERGY
        TyreSeverity.WARN -> ChipTone.WARN
        TyreSeverity.ALERT -> ChipTone.ALERT
        TyreSeverity.NONE -> ChipTone.NEUTRAL
    }

    /** Khe giữa hai bánh CÙNG trục (một dấu cách) và giữa trục trước ↔ trục sau (hai dấu cách) — xem [tyreChip]. */
    private const val WHEEL_GAP = " "
    private const val AXLE_GAP = "  "

    /** Chỉ số bánh đầu tiên của trục sau trong thứ tự [TyreCorner] (TT · TP · **ST** · SP). */
    private const val AXLE_SPLIT = 2

    /**
     * MỘT chip cho cả ghế sưởi lẫn ghế mát của **một** ghế: hình nói **chế độ**, chữ nói **ghế nào + mức mấy**.
     *
     * Mức đọc qua [TelemetryReadout] (nguồn thang mức DUY NHẤT — mã thô ngoài thang ⇒ `null` ⇒ `"—"`, không làm
     * tròn), hình đọc qua [CapabilityDots.iconOverride] (nguồn icon DUY NHẤT, nên chip · ô widget · bộ chọn không
     * bao giờ vẽ ba hình cho một khái niệm).
     *
     * ## ⚠ UX5b — nhãn nói **GHẾ NÀO**, không nói *"sưởi"/"mát"*
     * Bản UX5 in nhãn ngắn của **chế độ** (`"Ghế sưởi · 2"`). Owner 2026-09-27, trước máy ảo: *"ghế sao không có ghế
     * lái hay ghế phụ? 2 ghế nó khác nhau mà"*. Với hai chip ghế đứng cạnh nhau thì chữ *"Ghế sưởi"* ở cả hai là
     * **thông tin sai chỗ**: chế độ đã nằm trong glyph ghép (làn nhiệt / bông tuyết), còn thứ không hình nào nói
     * được là **bên nào**. [ChipView.desc] vẫn đọc đủ cả hai chế độ nên người dùng trình đọc màn hình không mất gì.
     *
     * ⚠ **Cả hai > 0 KHÔNG nên xảy ra** — [SUY] RE `docs/specs/seat-comfort-auto.html` §2 ghi hai chế độ loại trừ
     * nhau, nhưng đó là RE của app khác và đường HAL của launcher **chưa đo** ca ấy. Quy tắc cố định, có test:
     * **ưu tiên SƯỞI**. Cố ý KHÔNG `require`/`error`: một cái chip không được làm sập màn chính vì xe trả một cặp
     * số lạ.
     *
     * @param chipId mã chip GỘP ([TopStripConfig.SEAT] / [TopStripConfig.SEAT_R]) — cặp datum tra từ nó.
     * @param label nhãn ghế theo ngôn ngữ đang chọn (*"Ghế lái"* / *"Ghế phụ"*).
     * @param emptyIcon hình lúc **cả hai tắt / chưa biết**: một cái ghế TRỐNG của đúng cạnh ấy (không dám nói đang
     *   sưởi hay đang mát).
     */
    private fun seatChip(
        chipId: String,
        label: String,
        emptyIcon: String,
        status: CarStatus,
        labels: Boolean,
    ): ChipView {
        val (heatId, ventId) = TopStripConfig.SEAT_PAIRS.getValue(chipId)
        val heat = TelemetryReadout.of(heatId, status)
        val vent = TelemetryReadout.of(ventId, status)
        val onId = when {
            (heat?.level ?: 0) > 0 -> heatId
            (vent?.level ?: 0) > 0 -> ventId
            else -> null
        }
        val lvl = (if (onId == ventId) vent else heat)?.level?.takeIf { onId != null }
        // ⚠ Mờ đi = lời khẳng định *"cả hai đang tắt"* ⇒ chỉ được nói khi **cả hai** đã đọc được. Một mã còn
        // nguội mà mã kia báo 0 thì đúng câu phải nói là *"chưa biết"* — luật *"không biết ≠ đang tắt"*. (Có
        // mã nào đang CHẠY thì đã rơi vào nhánh ACTIVE ở trên rồi, nên luật này không giấu mất trạng thái thật.)
        val unread = heat?.level == null || vent?.level == null
        // 2.76 L7 (owner 27/09): MỨC nằm trong HÌNH — một/hai làn nhiệt, một/hai bông tuyết ([CapabilityIcons.forLevel]).
        // Có hình cho mức ⇒ chữ chỉ còn nhãn ghế; mức ngoài bảng ⇒ con số quay lại (không vẽ bừa một mức chưa đo).
        val concept = onId?.let { CapabilityDots.iconOverride(it) }
        val levelIcon = CapabilityIcons.forLevel(concept, lvl)
        val shown = if (lvl != null) (if (levelIcon != null) "" else "$lvl") else if (unread) TelemetryView.PLACEHOLDER else ""
        return ChipView(
            text = when {
                shown.isEmpty() -> if (labels) label else ""
                labels -> "$label · $shown"
                else -> shown
            },
            icon = levelIcon ?: concept ?: emptyIcon,
            tone = when {
                lvl != null -> ChipTone.ACTIVE
                unread -> ChipTone.NEUTRAL   // "không biết" ≠ "đang tắt" (luật chung của thanh trên)
                else -> ChipTone.INACTIVE
            },
            // Câu cho trình đọc màn hình nói ĐỦ cả hai chế độ: người không thấy màu/hình mất hết thông tin nếu
            // chỉ đọc con số. Dùng lại chính chữ của [TelemetryReadout] ⇒ không có bản dịch thứ hai.
            desc = listOfNotNull(heat, vent).joinToString(" · ") { "${it.label}: ${it.display}" },
        )
    }

    /**
     * Chip cho một datum thường: `"<nhãn ngắn> · <giá trị><đơn vị>"`, đi qua ĐÚNG lớp đơn vị như mọi bề mặt khác.
     *
     * ## Datum BẬT/TẮT thì KHÔNG có phần chữ giá trị
     * [ĐO xe 2026-09-21] chip `defrost_front_state` hiện `"Sấy kính · Tắt"`. Owner: trạng thái phải là **icon
     * mờ/sáng**, không phải chữ. Nên khi [TelemetryView.onOff] có giá trị, chip còn **nhãn ngắn + icon** và trạng
     * thái nằm trong [ChipTone.ACTIVE]/[ChipTone.INACTIVE] ⇒ `:app` tô màu icon + chữ theo đó.
     *
     * Ba điều cố ý giữ nguyên:
     *  1. **[ChipView.desc] vẫn đầy đủ** (`"Sấy kính: Tắt"`). Bỏ chữ là quyết định về **chỗ trên thanh**, không phải
     *     về nội dung — người dùng trình đọc màn hình không thấy được màu icon, nên với họ chữ là đường DUY NHẤT.
     *     Cùng lập luận đã ghi ở [chip] cho ca tắt nhãn.
     *  2. **Chưa đọc được ⇒ về đường thường** ([TelemetryView.onOff] null) ⇒ `"Sấy kính · —"` + [ChipTone.NEUTRAL].
     *     Icon mờ ở đây sẽ là lời khẳng định *"đang tắt"* mà không ai đo được.
     *  3. **Datum SỐ không đụng tới** (nhiệt/gió/pin/lốp): chúng không có [TelemetryView.onOff] nên đi nhánh cũ,
     *     vẫn [ChipTone.NEUTRAL] + hiện giá trị. Một con số không có trạng thái bật/tắt để mà tô.
     *
     * ## UX8 — datum HAI CHẾ ĐỘ thì đổi HÌNH, không đổi sáng/mờ
     * Họ thứ ba (lấy gió trong/ngoài, cảm biến sống/chết): **không chế độ nào là "tắt"** nên sáng/mờ không nói
     * được cái nào ⇒ mỗi chế độ MỘT hình ([CapabilityIcons.forState]), chữ giá trị bỏ hẳn. Ba họ, ba luật, một
     * bảng `when` — thứ tự trong `when` chính là thứ tự ưu tiên, và bài canh khoá không datum nào rơi vào hai họ.
     */
    private fun datumChip(id: String, status: CarStatus, units: UnitPrefs, labels: Boolean): ChipView? {
        val spec = TelemetryRegistry.byId(id) ?: return null
        val view = TelemetryReadout.of(id, status)?.let { UnitFormat.apply(it, units) } ?: return null
        val value = view.displayWithUnit()
        val on = view.onOff
        // B10 (owner 2026-09-23): datum BẬT/TẮT (sấy kính…) mà CHƯA đọc được (on==null) — trên xe owner nhiều datum
        // này không bao giờ có tín hiệu ⇒ "· —" là dấu gạch VÔ NGHĨA + chiếm chỗ đẩy icon xa nhau. Coi như một
        // datum bật/tắt: chỉ icon (mờ = INACTIVE), KHÔNG hiện giá trị "—". Chỉ áp cho datum bật/tắt (isOnOff),
        // datum SỐ chưa đọc vẫn hiện "· —" (số thật sẽ về).
        val isBool = TelemetryReadout.isOnOff(id)
        // ═══ UX5 — datum chạy theo THANG MỨC nói trạng thái bằng MÀU + SỐ, không bằng chữ ═══════════════════════
        //
        // [ĐO đọc mã 2026-09-26] chip `seat_heat_state` in đúng `"Ghế sưởi · Tắt"` — lại là chữ trạng thái mà owner
        // đã gạch bỏ cho ô bật/tắt (2026-09-21 *"bỏ chữ Bật/Tắt đi"*), chỉ khác là nó lọt qua vì ghế KHÔNG phải
        // datum bật/tắt nên nhánh [TelemetryReadout.isOnOff] không với tới.
        //
        // Điều kiện là một **tính chất dữ liệu** (`maxLevel >= 1` — nút của datum này chạy theo thang mức), KHÔNG
        // phải một nhánh theo mã ghế (CLAUDE.md §7): thêm một nút thang mức là nó tự đúng.
        val hasLevel = CapabilityDots.maxLevel(id) >= 1
        // ═══ UX8 — datum HAI CHẾ ĐỘ nói trạng thái bằng HÌNH ═══════════════════════════════════════════════════
        //
        // [ĐO xe 2026-09-27, owner nhìn thanh trên] chip `ac_cycle` in `"Chế độ lấy gió · Trong"` — lại là chữ
        // trạng thái mà owner đã gạch bỏ từ 09-21, lọt qua vì *lấy gió trong/ngoài* không phải bật/tắt (không có
        // [TelemetryView.onOff]) mà cũng không chạy theo thang mức. Nay hình đổi theo chế độ, chữ bỏ hẳn.
        //
        // Điều kiện là **bảng khai** ([CapabilityIcons.hasStateIcons]), không phải một nhánh theo mã (CLAUDE.md
        // §7): khai thêm một datum ở bảng ấy là chip này tự đúng, không phải sửa một dòng nào ở đây.
        val stateIcon = CapabilityIcons.forState(id, view.state)
        // 2.76 L7 — họ THANG MỨC nói MỨC bằng số dấu trong hình ([CapabilityIcons.forLevel], khoá theo hình khái
        // niệm — chính hình mà nút của datum này mang). Có hình cho mức ⇒ con số rời chip; mức ngoài bảng ⇒ số ở lại.
        val concept = CapabilityDots.iconOverride(spec.id) ?: CapabilityIcons.forTelemetry(spec.id, spec.domain)
        val levelIcon = if (hasLevel) CapabilityIcons.forLevel(concept, view.level) else null
        // B9 (owner 2026-09-22): chip là bề mặt hẹp nhất — với datum mức rút "Mức 2"/"Level 2" → "2" cho đỡ chật
        // (hình ghế nói rõ là ghế rồi). Nay lấy thẳng [TelemetryView.level] chứ không bóc chuỗi đã dịch.
        // UX8 (owner 2026-09-27): chữ `AUTO` trên chip viết **thường** — phép hạ chữ sống đúng một bản ở
        // [ClimateAuto.narrowAuto] (cạnh chính hằng `AUTO`), và áp cho MỌI chip mang dấu ấy, không theo mã.
        val chipValue = ClimateAuto.narrowAuto(chipValue(view, value, hasLevel, levelIcon))
        return ChipView(
            // U5 · T2: nhãn ngắn THEO NGÔN NGỮ. Chip là bề mặt hẹp nhất của launcher nên nó cần đúng bản ngắn, không
            // phải nhãn đầy — lý do `shortEn` tồn tại.
            //
            // Bật/tắt: chỉ nhãn (trạng thái đã ở màu icon). Tắt nhãn NỮA ⇒ chuỗi rỗng = chip chỉ-icon, và đó đúng là
            // thứ người dùng xin khi gạt cả hai công tắc — icon vẫn nói được trạng thái nhờ màu.
            text = when {
                stateIcon != null -> if (labels) spec.displayShortLabel else ""      // UX8: hình nói chế độ, chữ chỉ nói VIỆC GÌ
                on != null || isBool -> if (labels) spec.displayShortLabel else ""   // bật/tắt: chỉ nhãn (chưa đọc = icon mờ, không "· —")
                chipValue.isEmpty() -> if (labels) spec.displayShortLabel else ""    // UX5 · mức 0: cùng cách với bật/tắt
                labels -> "${spec.displayShortLabel} · $chipValue"
                else -> chipValue
            },
            icon = stateIcon ?: levelIcon ?: concept,
            tone = when {
                // UX8 — hai chế độ: HÌNH đã nói chế độ nào, nên sắc thái chỉ còn nói *"đã đọc được hay chưa"*.
                // Đọc được ⇒ sáng (một chế độ đang chạy thật, cả hai đều là trạng thái sống, không cái nào là
                // "tắt" để mà mờ); chưa đọc ⇒ trung tính — luật chung *"không biết ≠ đang tắt"* của thanh trên.
                stateIcon != null -> if (view.state != null) ChipTone.ACTIVE else ChipTone.NEUTRAL
                on == true -> ChipTone.ACTIVE
                on == false -> ChipTone.INACTIVE
                // UX5 — datum MỨC: mức ≥ 1 sáng, mức 0 mờ, **chưa đọc thì KHÔNG mờ** (xem dưới). Luật này trùng khít
                // luật của ô nút (`ControlVisuals.of` ca thang mức) nên chip và ô không bao giờ nói hai điều.
                hasLevel && view.level != null -> if (view.level > 0) ChipTone.ACTIVE else ChipTone.INACTIVE
                // Chưa đọc: giữ NEUTRAL — "không biết" ≠ "đang tắt" (lập luận cũ). Chỉ BỎ dấu "· —" (B10 owner
                // 2026-09-23): dấu vô nghĩa + chiếm chỗ đẩy icon xa nhau. Icon trung tính, không mờ hẳn.
                else -> ChipTone.NEUTRAL
            },
            desc = "${spec.displayLabel}: $value",
        )
    }

    /**
     * Chữ GIÁ TRỊ của chip datum (trước phép hạ `AUTO`) — hàm riêng để khoá được ở MỌI [Lang] (`TopStripChipLevelLangTest`).
     *
     * Nhánh `!hasLevel` (datum không có thang mức): bản trước bóc `Strings.t("Mức ", "Level ")` khỏi chuỗi ĐÃ DỊCH —
     * chỉ đúng khi bản dịch của mẫu `"Mức {0}"` cũng là TIỀN TỐ; zh `"{0}挡"` là HẬU TỐ ⇒ chip in `"2挡"` thay `"2"`.
     * Nay lấy thẳng [TelemetryView.level] (cùng lối B9 ở nhánh thang mức): không phép mổ chuỗi nào phụ thuộc trật tự
     * từ của một tiếng. Mức 0 / không phải datum mức ⇒ giữ nguyên chữ giá trị (VI/EN y byte như bản cũ — test khoá).
     */
    internal fun chipValue(view: TelemetryView, value: String, hasLevel: Boolean, levelIcon: String?): String = when {
        !hasLevel -> view.level?.takeIf { it > 0 }?.toString() ?: value.trim()
        view.level == null -> TelemetryView.PLACEHOLDER   // chưa đọc / mã ngoài thang ⇒ "—" + NEUTRAL
        levelIcon != null -> ""                           // L7: mức đã nằm trong hình, không in số
        view.level > 0 -> view.level.toString()
        else -> ""                                        // mức 0 = TẮT: icon mờ nói hết, không in chữ "Tắt"
    }

    /** Đổi một số về đơn vị người dùng chọn, dùng CHUNG bộ chuyển của [Units] (không tự nhân chia tại chỗ). */
    private fun conv(v: Double, q: Quantity, units: UnitPrefs): String {
        val base = Units.BASE[q] ?: return v.toInt().toString()
        val raw = TelemetryView("chip", "", base, WidgetShape.VALUE, EvidenceTier.PROVEN,
            if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString())
        return UnitFormat.apply(raw, units).display
    }
}
