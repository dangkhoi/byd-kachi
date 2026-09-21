package com.byd.clusternav.launcher

import com.byd.clusternav.comfort.Pm25Filter

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
 * [Pm25Filter.isDirty]. Không có ngưỡng nào tự nghĩ ra ở đây.
 *
 * ⚠ **CỐ Ý KHÔNG gán sắc thái cho các số "sức khoẻ"** (nhiệt pin, điện áp cell, ắc-quy 12V, nhiệt lốp, µg/m³, SOC…):
 * tôi KHÔNG biết ngưỡng đúng cho đời xe owner, và tự nghĩ một con số rồi tô đỏ là **bịa cảnh báo** — nguy hơn là
 * không cảnh báo, vì người lái sẽ học cách bỏ qua màu đỏ. Chỉ những datum **bản thân nó đã là tín hiệu báo động**
 * (cửa mở, cốp mở) mới có sắc thái. Đây là chỗ để owner thêm ngưỡng sau khi đo số thật trên xe.
 *
 * ⚠ 2026-09-16 — mọi sắc thái ADAS/an toàn (dây an toàn, điểm mù, chuyển làn, cắt ngang sau, cảnh báo mở cửa, quá
 * tốc, ESP, 8 vùng cảm biến đỗ) đã **gỡ hẳn** cùng các datum của chúng: launcher không còn nói gì về hệ an toàn.
 */
object GroupBoard {

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

    /**
     * Bộ phận thân xe ↔ mã datum — bảng nối DUY NHẤT giữa [CarPart] (hình học) và bộ đăng ký (dữ liệu).
     *
     * Đặt ở đây, cùng chỗ với [TYRE_PRESSURE_BY_CORNER], vì cùng một vai: nhóm nói bằng **mã datum** còn bảng vẽ nói
     * bằng **bộ phận**, và phải có đúng MỘT chỗ nối hai cách gọi đó. Chép nó sang `:app` là dựng bản sao thứ hai —
     * có test cấm (`GroupTileWiringContractTest`: tầng vẽ không được chứa `"door_"`).
     *
     * ⚠ Hậu tố thân xe `lf · rf · lr · rr` (KHÔNG phải `fl · fr · rl · rr` của TPMS) — xem KDoc [CarPart].
     */
    private val DOOR_PARTS: List<DoorPartSpec> = listOf(
        DoorPartSpec(CarPart.DOOR_LF, "door_lf"),
        DoorPartSpec(CarPart.DOOR_RF, "door_rf"),
        DoorPartSpec(CarPart.DOOR_LR, "door_lr"),
        DoorPartSpec(CarPart.DOOR_RR, "door_rr"),
        DoorPartSpec(CarPart.TAILGATE, "tailgate_status"),
        DoorPartSpec(CarPart.SUNROOF, "sunroof_state", "sunroof_pos"),
        // Rèm chỉ có MỘT datum, và nó đã là phần trăm ⇒ vừa là trạng thái vừa là số.
        DoorPartSpec(CarPart.SUNSHADE, "sunshade_pct", "sunshade_pct"),
        // ⚠ UX-OVERHAUL · WP8 — bộ phận GƯƠNG đã rời bảng cùng datum `mirror_fold` (#30 trong danh sách BỎ). Một
        // `DoorPartSpec(CarPart.MIRROR)` không có datum nào vẫn vẽ được một chấm trên hình xe, nhưng chấm đó không
        // bao giờ nói được gì (luôn "chưa đọc") — đúng thứ ô-nút-chết mà steering cấm.
    )

    /**
     * @property stateId datum nói bộ phận đang ĐÓNG hay MỞ.
     * @property posId datum nói MỞ BAO NHIÊU (`null` nếu bộ phận không có số). Được phép trùng [stateId] khi datum
     *   duy nhất của bộ phận vốn đã là phần trăm (rèm).
     */
    private data class DoorPartSpec(val part: CarPart, val stateId: String, val posId: String? = null)

    /** Model cho nhóm [id], hoặc `null` nếu mã không phải nhóm (⇒ chỗ gọi suy giảm an toàn, KHÔNG sập). */
    fun of(id: String, status: CarStatus, units: UnitPrefs = UnitPrefs.DEFAULT): GroupBoardModel? =
        CapabilityGroups.byId(id)?.let { of(it, status, units) }

    /**
     * Model cho [g]. Luôn trả về **đúng** `g.reads.size` ô con và `g.writes.size` nút, theo đúng thứ tự khai — kể cả
     * off-car (ô con chưa đọc được hiện `"—"`), để bố cục không nhảy khi số về.
     */
    fun of(g: CapabilityGroup, status: CarStatus, units: UnitPrefs = UnitPrefs.DEFAULT): GroupBoardModel {
        val tyres = tyreStatuses(g, status)
        return GroupBoardModel(
            id = g.id,
            // U5 · T2: nhãn theo ngôn ngữ đang dùng ở CHÍNH chỗ dựng model — bộ vẽ ở `:app` chỉ đọc model, nên nếu
            // để nhãn gốc ở đây thì `:app` phải tự dịch lại và đó là bản-sao-thứ-hai của phép chọn ngôn ngữ.
            label = g.displayLabel,
            icon = g.icon,
            shape = g.shape,
            cells = g.reads.map { cell(it, status, units, tyres) },
            actions = g.writes.mapNotNull { action(it) },
        )
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

    /**
     * Kế hoạch cho bảng **Cửa & khoang** (U9 pha 2) — bộ phận nào đang mở, tô sắc thái nào, kết luận là câu gì.
     *
     * ## Vì sao ở `:core` chứ không ở ô vẽ
     * Cùng lý do [TyreBoard.verdict]: *"cửa nào đang mở"* và *"nói câu gì về chúng"* là quyết định về
     * DỮ LIỆU, kiểm được off-car; còn *"vẽ vạt cửa ở góc nào"* mới là việc của Canvas. Ô vẽ ở `:app` thậm chí **không
     * được phép** nhắc tới mã `door_lf` — `GroupTileWiringContractTest` cấm đúng điều đó, nên chỗ nối phải là
     * [CarPart].
     *
     * ## Hai datum cho MỘT bộ phận là chuyện thường, và phải gộp
     * Cốp có `tailgate_status` (mở/đóng) **và** `tailgate_position` (%); nóc có `sunroof_state` **và** `sunroof_pos`.
     * Vẽ chúng thành hai vùng tô riêng thì cùng một nắp cốp hiện hai lần với hai màu. Gộp: sắc thái lấy cái **nặng
     * nhất** ([worst]), số hiển thị lấy cái **có số**.
     *
     * ⚠ Nhóm không có datum nào của một bộ phận ⇒ bộ phận đó **không vào danh sách** (không vẽ), chứ không vào với
     * trạng thái "chưa đọc": hai câu đó khác nhau — *"xe này không có cửa sổ trời"* và *"chưa đọc được cửa sổ trời"*.
     */
    fun doorPlan(m: GroupBoardModel): DoorBoardPlan {
        val byId = m.cells.associateBy { it.id }
        val parts = DOOR_PARTS.mapNotNull { spec ->
            val st = byId[spec.stateId] ?: return@mapNotNull null
            val pos = spec.posId?.let { byId[it] }
            val tone = worst(st.tone, pos?.tone)
            CarPartState(
                part = spec.part,
                label = st.label,
                // Ưu tiên bản CÓ SỐ: "30 %" nói được nhiều hơn "Mở", và khi chưa đọc được phần trăm thì vẫn còn
                // trạng thái đóng/mở để nói — không rơi về "—" chỉ vì một trong hai datum im lặng.
                value = if (pos != null && pos.available) pos.value else st.value,
                note = pos?.takeIf { it.available }?.value.orEmpty(),
                tone = tone,
                // "Đang khác trạng thái nghỉ" = có sắc thái. Gương GẬP cũng rơi vào đây dù gập không phải là "mở" —
                // xem KDoc [CarPartState.open] về việc quyết định này phải ở cùng chỗ với luật sắc thái.
                open = tone != GroupTone.NEUTRAL,
                available = st.available || pos?.available == true,
            )
        }
        return DoorBoardPlan(parts, doorVerdict(parts))
    }

    /**
     * Dòng KẾT LUẬN của bảng cửa — trả lời thẳng câu *"xe tôi kín chưa?"*.
     *
     * Ba điều nó phải phân biệt, và cả ba đều là chỗ dự án đã trả giá ở bảng khác:
     *  • **đã đọc, đóng hết** ≠ **chưa đọc được gì** — off-car mọi field là `null`, nói *"tất cả đã đóng"* ở đó là
     *    hứa một điều chưa kiểm;
     *  • **đóng hết nhưng còn bộ phận chưa đọc** — vẫn phải kể ra con số, không được im lặng làm tròn thành "đóng hết";
     *  • bốn cửa thì **đếm**, các khoang còn lại thì **kể tên kèm giá trị** (xem [CarPart.isDoor]).
     */
    private fun doorVerdict(parts: List<CarPartState>): String {
        if (parts.isEmpty()) return ""
        if (parts.none { it.available }) {
            return Strings.t("${parts.size} bộ phận · chưa đọc được", "${parts.size} parts · not read yet")
        }
        val unread = parts.count { !it.available }
        val doors = parts.count { it.part.isDoor && it.open }
        val others = parts.filter { !it.part.isDoor && it.open }
        if (doors == 0 && others.isEmpty()) {
            return if (unread == 0) Strings.t("Tất cả đã đóng", "All closed")
            else Strings.t("Đã đóng · $unread chưa đọc được", "Closed · $unread not read yet")
        }
        return buildList {
            if (doors > 0) {
                add(Strings.t("$doors cửa mở", "$doors ${if (doors == 1) "door" else "doors"} open"))
            }
            others.forEach { add("${it.label} · ${it.value}") }
            if (unread > 0) add(Strings.t("$unread chưa đọc được", "$unread not read yet"))
        }.joinToString("   ")
    }

    /**
     * Sắc thái NẶNG hơn trong hai cái.
     *
     * Viết thẳng bốn nhánh chứ **không** so `ordinal`: thứ tự khai của [GroupTone] không hề hứa là thang nặng-nhẹ
     * (khác [EvidenceTier], nơi giao kèo đó được ghi ra và có test khoá), nên dựa vào nó là dựng một phụ thuộc ngầm
     * mà ngày ai chèn thêm một sắc thái vào giữa sẽ vỡ **im lặng** — và vỡ theo chiều tệ nhất: mất cảnh báo.
     */
    private fun worst(a: GroupTone, b: GroupTone?): GroupTone = when {
        b == null -> a
        a == GroupTone.ALERT || b == GroupTone.ALERT -> GroupTone.ALERT
        a == GroupTone.WARN || b == GroupTone.WARN -> GroupTone.WARN
        a == GroupTone.ACTIVE || b == GroupTone.ACTIVE -> GroupTone.ACTIVE
        else -> GroupTone.NEUTRAL
    }

    // ── Dựng ô con ──────────────────────────────────────────────────────────────────────────────────────

    private fun cell(
        id: String,
        s: CarStatus,
        units: UnitPrefs,
        tyres: Map<String, TyreStatus>,
    ): GroupCell {
        val spec = TelemetryRegistry.byId(id)
        // Mã không có trong registry là chuyện [CapabilityGroups.init] đã chặn; giữ lưới an toàn để ô hiện "—" thay
        // vì cả launcher sập nếu ngày nào đó một datum bị đổi tên.
        val view = TelemetryReadout.of(id, s)?.let { UnitFormat.apply(it, units) }
        return GroupCell(
            id = id,
            label = spec?.displayShortLabel ?: id,
            number = view?.display ?: TelemetryView.PLACEHOLDER,
            unit = view?.unit ?: "",
            tone = toneOf(id, s, tyres),
            icon = spec?.let { CapabilityIcons.forTelemetry(it.id, it.domain) } ?: "",
            available = view?.available == true,
            needsBadge = spec?.tier?.needsBadge == true,
        )
    }

    /**
     * Nút của nhóm — nhãn dùng **bản NGẮN**, cùng lý do với ô con XEM (xem [GroupCell.label]).
     *
     * [ĐO] ảnh máy ảo 2026-09-12: hàng nút chia 6 ô trên khung 4/12 màn ⇒ 82px/ô, nhãn đầy bị cắt
     * (`"Window front-ri…"` · `"Kính trước-p…"`). Nhãn ngắn khai ở [ControlDef.short]/[ActionMacro] chứ không viết tắt
     * tại đây: quy tắc viết tắt tự nghĩ ở tầng trình bày sẽ ra nhãn vô nghĩa ở đâu đó trong bộ nút mà không ai kiểm.
     */
    private fun action(id: String): GroupActionCell? {
        ControlRegistry.byId(id)?.let {
            return GroupActionCell(it.id, it.displayShortLabel, it.icon, it.tier.needsBadge)
        }
        ActionMacros.byId(id)?.let {
            return GroupActionCell(it.id, it.displayLabel, it.icon, it.needsBadge())
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
    private fun toneOf(id: String, s: CarStatus, tyres: Map<String, TyreStatus>): GroupTone {
        tyres[id]?.let { return tyreTone(it) }
        return when (id) {
            // ── ĐANG BẬT / ĐANG MỞ (sáng lên) ───────────────────────────────────────────────
            "window_lf" -> openPct(s.body.windowLfPct)
            "window_rf" -> openPct(s.body.windowRfPct)
            "window_lr" -> openPct(s.body.windowLrPct)
            "window_rr" -> openPct(s.body.windowRrPct)
            "sunroof_pos" -> openPct(s.body.sunroofPct)
            "sunshade_pct" -> openPct(s.body.sunshadePct)
            // Nóc/rèm mở là LỰA CHỌN của người lái, không phải chuyện đáng lo ⇒ ACTIVE. Cửa và cốp thì khác (xem
            // nhánh cảnh báo dưới): xe tự kêu khi chúng mở lúc đang đi.
            "sunroof_state" -> active(s.body.sunroofOpen)
            "light_low_beam" -> active(s.lights.lowBeam)
            "light_high_beam" -> active(s.lights.highBeam)
            "light_front_fog" -> active(s.lights.frontFog)
            "light_rear_fog" -> active(s.lights.rearFog)
            "light_left_turn" -> active(s.lights.leftTurn)
            "light_right_turn" -> active(s.lights.rightTurn)
            "light_side" -> active(s.lights.sideLight)
            "light_drl" -> active(s.lights.drl)
            "ac_on" -> active(s.climate.acOn)
            "ac_cycle" -> active(s.climate.recircOn)
            "anion_state" -> active(s.climate.anionOn)

            // ── CẢNH BÁO ────────────────────────────────────────────────────────────────────
            "door_lf" -> alertIf(s.body.doorLfOpen)
            "door_rf" -> alertIf(s.body.doorRfOpen)
            "door_lr" -> alertIf(s.body.doorLrOpen)
            "door_rr" -> alertIf(s.body.doorRrOpen)
            "tailgate_status" -> alertIf(s.body.tailgateOpen)
            // Ngưỡng bụi dùng LẠI [Pm25Filter] (đã có, đang chạy trên xe) — không đặt ngưỡng thứ hai.
            "pm25_level" -> s.climate.pm25Level?.let {
                when {
                    Pm25Filter.isDirty(it) -> GroupTone.ALERT
                    it >= Pm25Filter.LOW_GRADE -> GroupTone.WARN
                    else -> GroupTone.NEUTRAL
                }
            } ?: GroupTone.NEUTRAL

            else -> GroupTone.NEUTRAL
        }
    }

    private fun openPct(pct: Int?): GroupTone =
        if (pct != null && pct > 0) GroupTone.ACTIVE else GroupTone.NEUTRAL

    private fun active(on: Boolean?): GroupTone = if (on == true) GroupTone.ACTIVE else GroupTone.NEUTRAL

    private fun alertIf(open: Boolean?): GroupTone = if (open == true) GroupTone.ALERT else GroupTone.NEUTRAL
}
