package com.byd.clusternav.launcher

/**
 * ═══ WP2 · R2 — TRẠNG THÁI HIỂN THỊ của một ô điều khiển (THUẦN) ══════════════════════════════════════════════
 *
 * Spec `docs/specs/kachi-ux-overhaul.html` §WP2. Owner 2026-09-20: *"on/off thì icon active/un-active + đổi màu,
 * bỏ chữ 'Bật/Tắt' đi — nhà quê"* · *"ghế mát 2 mức thì 2 vạch nhỏ"* · *"cốp mở thì màu active"* · *"stepper phải
 * cân đối giống nhau, nhiệt với gió như nhau"*.
 *
 * ## Vì sao luật nằm ở `:core` chứ ở trong bộ dựng ô
 * Cùng lập luận [ChipTone] (RW0) và [GroupTone] (G1): `:core` nói **ý nghĩa** (*"ô này đang có tác dụng"*, *"sáng
 * 1 trong 2 vạch"*, *"con số hiện ra là gì"*), `:app` mới biết **màu và hình**. Trước WP2 câu *"ô này coi là đang
 * bật khi nào"* nằm rải trong **năm** hàm dựng view của `ControlTileFactory` và khác nhau theo từng
 * [ControlKind] — không bài canh nào tới được, nên nó lệch mà không ai thấy: [ĐO đọc mã] `tileStep`/`tileCover`/
 * `tileSelect`/`tileButton` đều gọi `tint(icon, label, true)` (tức **luôn** tô mực *"đang bật"*) trong khi nền ô
 * thì `applyBg(tile, false)` (tức *"đang tắt"*) — hai nửa của cùng một ô nói hai điều khác nhau.
 *
 * Nay cả năm kiểu đi qua đúng một cửa [ControlVisuals.of], và cửa đó kiểm được off-car bằng test thuần.
 *
 * ## Ba luật *"đang có tác dụng"* — mỗi luật một lý do, không phải một bảng cho đẹp
 *  • **TOGGLE · BUTTON · COVER** — `> 0` là bật/mở. Với COVER, bốn kính đọc **phần trăm** mở và cốp đọc cờ mở/đóng,
 *    nên *"mở hé 10 %"* cũng là mở: một cái cửa hé vẫn là cửa chưa đóng, và đó chính là điều người lái cần thấy.
 *  • **STEP** — chỉ coi là *"đang có tác dụng"* khi thang có **điểm 0 thật** ([hasOffPoint]). `fan` 0..7 và `vol`
 *    0..30 khai mức 0 ở đáy thang; `temp` 17..33 thì **không có mức tắt nào** ⇒ ô nhiệt độ giữ nền trung tính mãi,
 *    thay vì sáng màu nhấn suốt chuyến chỉ vì *"nhiệt độ luôn khác min"*.
 *    ⚠ **SỬA 2026-09-26 (UX4)**: câu cũ ở đây ghi *"`fan` 0..7 có mức 0 = im"* — **sai**, và [ĐO xe 2026-09-20] đã
 *    bác: `AC_WIND_LEVEL_SET = 0` **bị xe bỏ qua**, nên mức 0 của gió không phải *"im"* mà là *"không tồn tại"*.
 *    Nấc ấy nay là AUTO (nút khai [ControlDef.autoId] — luật ở [ClimateAuto]), và ô có `autoId` đang AUTO thì
 *    **vẫn là đang có tác dụng** (quạt vẫn thổi, chỉ là xe tự chọn mức).
 *  • **SELECT** — chia HAI ca, xem [isLevelScale].
 *
 * ## ⚠ SELECT: *thang mức* khác *tập lựa chọn*, và chỉ ca đầu được thay chữ bằng vạch
 * [ĐO registry] tám nút SELECT chia hai họ: `seatc`/`seath` là **thang mức** (Tắt · Mức 1 · Mức 2 — đúng thứ owner
 * muốn vẽ thành vạch), còn `ambient_color` (Tím/Xanh/…) · `headlight_mode` (Tắt/Auto/Đỗ/Cốt) · `powertrain_mode`
 * (EV/HEV) · `regen_level` · `screen_rotation` · `camera_view` là **tập lựa chọn không xếp hạng**. Vẽ *"Xanh lá"*
 * thành *"3 trên 5 vạch"* là nói sai: ở đó chữ là thông tin **duy nhất** của ô, và bỏ nó đi thì người dùng không
 * còn cách nào biết đang chọn cái gì.
 *
 * Phép phân biệt đọc **dữ liệu đã có**: [ControlLevels.RAW_BY_LEVEL] — bảng khai *"nút này chạy theo thang mức"*
 * cho cả đường ĐỌC (mã khung → mức) lẫn đường GHI (mức → mã khung). Nút nào thêm vào thang ấy (việc **bắt buộc**
 * nếu muốn ghi đúng) thì tự có vạch; không cần một cờ thứ hai để nhớ bật.
 */
data class ControlVisual(
    /** Ô đang có tác dụng ⇒ `:app` tô nền + icon + nhãn bằng màu nhấn (và icon KHÔNG bị hạ độ mờ). */
    val active: Boolean = false,
    /** Tổng số vạch mức phải vẽ. **0 = không vẽ vạch** (nút không chạy theo thang mức). */
    val ticks: Int = 0,
    /** Số vạch đang sáng (`0..ticks`). `0` với thang mức = *"Tắt"* — mờ cả hai vạch, KHÔNG có chữ "Tắt". */
    val lit: Int = 0,
    /**
     * Chữ lựa chọn hiện dưới nhãn — **rỗng** khi trạng thái đã nói bằng vạch/màu (R2.1/R2.2). Chỉ tập-lựa-chọn
     * mới có chữ (xem ⚠ ở KDoc lớp).
     */
    val option: String = "",
    /** Chữ trong ô giá trị của [ControlKind.STEP] (vd `"22°"`, `"4"`); rỗng với kiểu khác. */
    val valueText: String = "",
)

/** Phép quyết định trạng thái hiển thị — KDoc đầy đủ ở [ControlVisual]. */
object ControlVisuals {

    /**
     * Nút SELECT này là một **thang mức** (⇒ vẽ vạch) hay một **tập lựa chọn** (⇒ giữ chữ)?
     *
     * Đòi cả ba điều kiện: là SELECT · có mặt trong [ControlLevels.RAW_BY_LEVEL] · có ≥ 2 lựa chọn (một nút chỉ
     * một lựa chọn thì vạch nói được gì).
     */
    fun isLevelScale(def: ControlDef): Boolean =
        def.kind == ControlKind.SELECT && ControlLevels.levelCount(def.id) > 0 && def.args.size >= 2

    /**
     * Số vạch của một thang mức = **số lựa chọn − 1** (mức *"Tắt"* không có vạch riêng; nó là *"mờ cả hai"*).
     *
     * ⚠ Đếm theo [ControlDef.args] chứ KHÔNG theo [ControlLevels.levelCount]: hai con số **lệch nhau có chủ ý** —
     * `seath` khai bốn mã khung (`1,2,3,4`, [SUY] chờ đo trên xe) nhưng chỉ bày **ba** lựa chọn (Tắt/Mức 1/Mức 2),
     * mà thứ người dùng bấm vòng qua là `args` ([ControlTileLogic.nextSelectIndex] đọc `args.size`). Vẽ 3 vạch cho
     * một ô chỉ lên được tới vạch 2 là hứa một mức không bấm tới được.
     */
    fun tickCount(def: ControlDef): Int = if (isLevelScale(def)) def.args.size - 1 else 0

    /**
     * Thang của [ControlKind.STEP] này có **điểm tắt thật** không (min = 0)?
     *
     * Dùng để quyết *"ô stepper có sáng màu nhấn hay không"* — xem ⚠ ở KDoc [ControlVisual]. Là một phép đọc
     * **dữ liệu của dòng registry**, không phải `if (id == "temp")`: thêm một nút gió/âm-lượng mới là nó tự đúng.
     */
    fun hasOffPoint(def: ControlDef): Boolean = def.kind == ControlKind.STEP && def.min == 0

    /**
     * Đơn vị NGẮN cho ô giá trị của một stepper — suy từ **đơn vị của chính datum mà nút đọc**
     * ([ControlDef.readKey] → [TelemetrySpec.unit]), không phải `if (def.id == "temp") "°"` như trước WP2.
     *
     * ## Vì sao rút về `"°"` chứ không để `"°C"`
     * Ô stepper của thanh nút rộng 84dp và phải chứa `[−] [giá trị] [+]`; `"22°C"` là 4 ký tự, đủ để bóp hai nút
     * xuống dưới ngưỡng bấm được ([KachiSpace.TOUCH_TIGHT] ghi lại phép đo đó). Và `"°"` **đúng hơn** ở đây: con số
     * này là **điểm đặt của chính xe**, hiện nguyên như xe đang hiểu — launcher không đổi °C↔°F cho nó, nên dán
     * `"C"` vào là khẳng định một thang mà lượt ghi không bảo đảm.
     */
    fun stepUnit(def: ControlDef): String {
        if (def.kind != ControlKind.STEP) return ""
        val unit = TelemetryRegistry.byId(def.readKey)?.unit.orEmpty()
        return if (unit.startsWith(DEGREE)) DEGREE else unit
    }

    /** Chữ trong ô giá trị của một stepper: số đã kẹp về `min..max` + [stepUnit]. */
    fun stepText(def: ControlDef, value: Int): String = "${def.clamp(value)}${stepUnit(def)}"

    /**
     * ═══ UX4 — chữ ô giá trị khi nút có mặt **TỰ ĐỘNG** ([ControlDef.autoId]) ═══════════════════════════════════
     *
     * [autoOn] `true` ⇒ chỉ `"AUTO"`, **không** `"AUTO 1"`. Đây là một phép ĐO bề rộng, không phải thẩm mỹ: ô giá
     * trị của thanh nút dùng được ≈34dp (`ControlTileFactory` KDoc `VALUE_WEIGHT`) trừ đệm hai bên còn ≈26dp, mà
     * `"AUTO 1"` ở 15sp bold cần hơn gấp đôi ⇒ cắt cứng. Con số thì đã có trên **chip thanh trên** (`"Gió · AUTO 1"`,
     * xem [TelemetryReadout] · [ClimateAuto.fanText]) — bề mặt rộng nói đủ, bề mặt hẹp nói đúng.
     *
     * `null`/`false` ⇒ y hệt [stepText] hai tham số ⇒ mọi nút không khai `autoId` **không đổi một ký tự**.
     */
    fun stepText(def: ControlDef, value: Int, autoOn: Boolean?): String =
        if (def.autoId.isNotBlank() && autoOn == true) ClimateAuto.AUTO else stepText(def, value)

    /**
     * Số ký tự mà ô giá trị của **CHÍNH nút này** có thể phải chứa — gồm cả chữ AUTO nếu nút có [ControlDef.autoId].
     *
     * ## ⚠ Vì sao đây là một hàm THEO NÚT, và [STEP_VALUE_CHARS] **KHÔNG** được nâng theo
     * Sàn chung là thứ làm hai nút −/+ đứng đúng một chỗ ở **mọi** ô (R2.4). Nâng nó lên 4 vì một chữ AUTO là bắt
     * `"0000"` làm sàn cho **cả** nhiệt độ và âm lượng ⇒ [ĐO số học] sàn vượt 26dp dùng được ⇒ `minWidth` thắng tỉ
     * lệ ⇒ hai nút bị bóp và trôi ở TẤT CẢ các ô, tức lật đúng thứ R2.4 vừa chữa, cho một chữ mà **một** nút cần.
     *
     * Nên tầng vẽ dùng [STEP_VALUE_CHARS] làm `minWidth` (không đổi) và **co chữ** khi [stepValueChars] của nút lớn
     * hơn sàn — quyết định đó ghi ở `ControlTileFactory.tileStep`. Hàm này là con số để tầng vẽ biết *khi nào cần co*.
     */
    fun stepValueChars(def: ControlDef): Int {
        if (def.kind != ControlKind.STEP) return 0
        val digits = maxOf(stepText(def, def.min).length, stepText(def, def.max).length)
        return if (def.autoId.isBlank()) digits else maxOf(digits, ClimateAuto.AUTO.length)
    }

    /**
     * ═══ R2.4 — SỐ KÝ TỰ mà ô giá trị của **mọi** stepper phải chứa được ══════════════════════════════════════
     *
     * Owner: *"ô giá trị rộng CỐ ĐỊNH + nút −/+ thẳng hàng bất kể '22°' (2 chữ + đơn vị) hay '4' (1 chữ); nhiệt và
     * gió phải cân đối GIỐNG NHAU"*. Đây là con số để `:app` đặt **SÀN bề rộng** cho ô giá trị — cùng một sàn cho
     * mọi stepper ⇒ hai nút −/+ đứng đúng một chỗ ở mọi ô, thay vì trôi theo độ dài con số của từng nút.
     *
     * **Suy từ registry, không gõ tay**: lấy chuỗi dài nhất trong `{min, max}` × mọi nút STEP. [ĐO] hôm nay =
     * **3** (`"33°"` của `temp`; `vol` chỉ 2 với `"30"`, `fan` 1 với `"7"`). Thêm một nút có dải rộng hơn thì con
     * số tự lớn theo và mọi ô stepper rộng ra cùng lúc — đúng nghĩa *"consistent"*.
     *
     * ⚠ **UX4 — con số này đếm SỐ, không đếm chữ AUTO.** Chữ AUTO là của đúng một nút; đưa nó vào sàn CHUNG là bóp
     * hai nút −/+ ở mọi ô stepper (lập luận đầy đủ + phép đo ở [stepValueChars]). Đừng "dọn cho phủ hết".
     *
     * `by lazy` chứ không `const`: nó đọc [ControlRegistry.ALL], nên tính lúc nạp lớp sẽ dựng một bẫy thứ-tự-khởi-tạo
     * (dự án đã trả giá đúng kiểu đó một lần — xem KDoc `CapabilityCatalog`/`DockConfig.DEFAULT` ở RW0).
     */
    val STEP_VALUE_CHARS: Int by lazy {
        ControlRegistry.ALL.filter { it.kind == ControlKind.STEP }
            .maxOfOrNull { d -> maxOf(stepText(d, d.min).length, stepText(d, d.max).length) }
            ?: 1
    }

    /**
     * Trạng thái hiển thị của [def] tại giá trị [value].
     *
     * [value] là **một con số cho cả năm kiểu**, đúng như [CarStatus.controls] và [ControlTileState] đang giữ:
     * TOGGLE/BUTTON 0-1 · COVER cờ mở hoặc phần trăm mở · STEP mức hiện tại · SELECT **chỉ số lựa chọn**.
     * `null` = *"chưa đọc được"* ⇒ trạng thái mặc định (tắt / mức 0 / giá trị mặc định của registry) — KHÔNG đoán
     * một trạng thái bật, vì một ô sáng màu nhấn là lời khẳng định *"xe đang làm việc này"*.
     */
    fun of(def: ControlDef, value: Int?): ControlVisual = of(def, value, null)

    /**
     * Như [of] nhưng biết thêm *"nút này đang ở chế độ TỰ ĐỘNG không"* ([ControlDef.autoId]; `null` = chưa biết).
     *
     * Chỉ STEP dùng tới: đang AUTO ⇒ ô **sáng màu nhấn** (quạt vẫn thổi) và chữ là `"AUTO"`. `null` ⇒ y hệt hành vi
     * trước UX4 — *"chưa đọc được"* không bao giờ được vẽ thành một lời khẳng định (xem ⚠ ở KDoc [ClimateAuto]).
     */
    fun of(def: ControlDef, value: Int?, autoOn: Boolean?): ControlVisual = when (def.kind) {
        ControlKind.TOGGLE, ControlKind.BUTTON, ControlKind.COVER ->
            ControlVisual(active = (value ?: 0) > 0)
        ControlKind.STEP -> {
            val v = value ?: def.value
            val auto = def.autoId.isNotBlank() && autoOn == true
            ControlVisual(active = auto || (hasOffPoint(def) && v > def.min), valueText = stepText(def, v, autoOn))
        }
        ControlKind.SELECT ->
            if (isLevelScale(def)) {
                val level = (value ?: 0).coerceIn(0, tickCount(def))
                ControlVisual(active = level > 0, ticks = tickCount(def), lit = level)
            } else {
                ControlVisual(option = ControlTileLogic.selectLabel(def, value ?: 0))
            }
    }

    /** Ký hiệu độ — tách ra để [stepUnit] không so chuỗi trần hai lần. */
    private const val DEGREE = "°"
}
