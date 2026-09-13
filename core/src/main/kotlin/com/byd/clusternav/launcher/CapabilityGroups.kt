package com.byd.clusternav.launcher

/**
 * ═══ G1 · 12 NHÓM KHẢ NĂNG ═══════════════════════════════════════════════════════════════════════════════════
 *
 * Thuần Kotlin (`:core`, cấm `android.*`) ⇒ kiểm được off-car. Spec `docs/specs/kachi-capability-groups.html` §4.1.
 *
 * ## Bệnh nó chữa — nói bằng số
 * [ĐO] 2026-09-11: launcher có **196 mục rời** (123 datum đọc · 64 nút · 9 widget) và **mỗi datum là một ô riêng**.
 * Muốn xem lốp phải đặt **bốn** ô (`tyre_p_fl`, `tyre_p_fr`, `tyre_p_rl`, `tyre_p_rr`); muốn xem kính phải đặt bốn ô
 * nữa. Owner nói đúng: *"không ai xem áp suất lốp 1 lốp cả, phải xem cả 4 cùng lúc"*. Một con số áp suất đứng một
 * mình gần như **không trả lời được câu hỏi nào** — "2.4 bar" chỉ có nghĩa khi đặt cạnh ba bánh kia (lệch hay không).
 *
 * ## Nguyên tắc chọn nhóm — MỘT NHÓM = MỘT CÂU HỎI
 * Không gom theo bảng dữ liệu, không gom theo thiết bị HAL, không gom theo domain. Gom theo **câu người lái hỏi**:
 * *"Lốp tôi ổn không?"* · *"Kính đóng hết chưa?"* · *"Còn đi được bao xa?"*. Vì thế [BATTERY] (sức khoẻ pin) tách
 * khỏi [ENERGY] (còn đi được bao xa) dù cả hai đều là `Domain.ENERGY`: đó là hai câu hỏi khác nhau, hỏi ở hai lúc
 * khác nhau. Ngược lại [DOORS] gộp cửa + cốp + nóc + rèm + gương vì chúng là **một** câu hỏi: *"xe tôi kín chưa?"*.
 *
 * ## Nhóm KHÔNG thay thế mục rời (§4.2)
 * 123 mục rời còn **nguyên** — có người chỉ muốn một con số tốc độ to giữa màn. [ĐO] 12 nhóm phủ **88/123** datum;
 * 35 datum còn lại (động lực, danh tính, GPS…) chưa thuộc nhóm nào và vẫn đặt được như trước. Nhóm chỉ là thứ người
 * dùng **gặp trước**, không phải thứ thay thế. Đây cũng là điều kiện để không phá cấu hình ai đã lưu: mã cũ vẫn đặt
 * được vì không mã nào bị xoá hay đổi tên.
 *
 * ## Vì sao chỉ 3 nhóm có nút (§4.3 + OQ1)
 * [WINDOWS] · [DOORS] · [LIGHTS] mang nút vì chúng là thứ người ta **làm**, và trạng thái của chúng vô nghĩa nếu
 * không sửa được (thấy kính mở 40% mà không đóng được thì để làm gì). Chín nhóm còn lại là thứ người ta **xem**:
 * lốp, radar, pin, chuyến đi không có gì để bấm. Bất biến này bị chốt trong [init] — nút chỉ được ở nhóm có bộ vẽ
 * mang hàng nút ([SHAPES_WITH_ACTIONS] = STRIP + BOARD từ U9 pha 2), vì bộ vẽ CARD **không có hàng nút**, nên nút
 * khai vào đó sẽ **vẽ ra rồi không ai chạm tới được** — đúng họ lỗi *"vẽ được ≠ đặt được"* mà RW0 vừa dọn.
 *
 * ## Vì sao nhóm là [CapabilityKind.READ] trong [CapabilityCatalog]
 * Xem KDoc tại [CapabilityCatalog.kindOf]. Tóm lại: bản chất một nhóm là **cái để xem** (kể cả [WINDOWS], nội dung
 * chính vẫn là 4 phần trăm mở; nút chỉ là một hàng thêm bên dưới), và `isWrite` ở `:app` là công tắc *"dựng ô loại
 * nào"* — trả `WRITE` thì ô nhóm biến thành một cái nút đơn và **mất hết** thành viên, tức mất đúng thứ nhóm sinh ra
 * để làm.
 *
 * ## Vì sao nhóm KHÔNG được lên thanh trạng thái
 * Chip cao ~24dp. Không vẽ được bảng 4 bánh trong đó, và nhóm có nút thì càng không (đích chạm 24dp bắn lệnh xe là
 * hậu quả không hoàn lại được — lý do đã ghi ở [TopStripConfig]). Chặn ở [TopStripConfig.isChippable] chứ KHÔNG
 * bằng cách đổi [CapabilityKind] của nhóm: hợp đồng RW0 (`READ` = xem, `WRITE` = bấm) giữ nguyên.
 */
object CapabilityGroups {

    /**
     * Tiền tố mã nhóm.
     *
     * Nhóm nằm CÙNG không gian mã phẳng với datum/nút/widget/gói lệnh (xem [CapabilityCatalog]) — nhờ vậy ô và thanh
     * nút lưu mã trần như cũ, **không phải chuyển đổi cấu hình người dùng đã lưu**. Tiền tố `g_` là để va chạm mã trở
     * thành chuyện **không thể xảy ra do cấu tạo**, chứ không phải chuyện phải nhớ kiểm: [ĐO] không mã nào trong 200
     * mã hiện có bắt đầu bằng `g_`. Có test khoá cả hai chiều (tiền tố + [CapabilityCatalog.collisions]).
     */
    const val ID_PREFIX = "g_"

    // ── Thứ tự khai = thứ tự hiện ra trong màn chọn ───────────────────────────────────────────────
    // Đi từ thứ hỏi thường xuyên nhất (lốp, kính, cửa) tới thứ hỏi thỉnh thoảng (pin, chuyến đi).

    /**
     * *"Lốp tôi ổn không?"* — 4 áp + 4 nhiệt.
     *
     * `TyreBoard` (:core) + `TyreBoardView` (:app) đã dựng từ W4, nhưng lúc đó nó là **widget dựng tay** `w_tire`,
     * tức một ngoại lệ được viết riêng. Nay bảng lốp là **một trường hợp của luật chung** — không còn là ngoại lệ.
     */
    val TYRES = CapabilityGroup(
        id = "g_tyres", label = "Lốp", labelEn = "Tyres",
        icon = "ic-group-tyres", domain = Domain.TYRES, shape = WidgetShape.BOARD,
        reads = listOf(
            "tyre_p_fl", "tyre_p_fr", "tyre_p_rl", "tyre_p_rr",
            "tyre_t_fl", "tyre_t_fr", "tyre_t_rl", "tyre_t_rr",
        ),
        sub = "áp suất + nhiệt độ từng bánh",
        subEn = "pressure and temperature of each wheel",
    )

    /**
     * *"Kính đóng hết chưa?"* — 4 phần trăm mở + 4 nút kính + 2 gói lệnh.
     *
     * Cố ý KHÔNG dùng nút `windows_all`: nó ở mức CHƯA KIỂM trên xe, còn `mac_win_open_all`/`mac_win_close_all` gộp
     * 4 nút riêng **đều đã chạy thật** ⇒ khả năng ăn cao hơn (đúng lý do W2 dựng hai gói đó). `rain_close` cũng
     * không vào đây: nó là **lựa chọn đặt-một-lần** ("tự đóng khi mưa"), không phải việc làm ngay lúc này.
     */
    val WINDOWS = CapabilityGroup(
        id = "g_windows", label = "Kính", labelEn = "Windows",
        icon = "ic-group-windows", domain = Domain.BODY, shape = WidgetShape.STRIP,
        reads = listOf("window_lf", "window_rf", "window_lr", "window_rr"),
        writes = listOf("win_lf", "win_rf", "win_lr", "win_rr", "mac_win_open_all", "mac_win_close_all"),
        sub = "phần trăm mở + mở/đóng từng kính",
        subEn = "how far open, plus open/close each window",
    )

    /**
     * *"Xe tôi kín chưa?"* — 4 cửa + cốp + nóc + rèm + gương, kèm nút cho từng thứ.
     *
     * ⚠ Bảng §4.1 của spec ghi phần nút bằng một CÂU (*"nút khoá/cốp"*) chứ không bằng danh sách mã. Đã giải thành
     * mã theo luật: **thứ gì nhóm này CHO XEM mà có nút thì đưa luôn nút vào**. Bỏ nửa vời (chỉ khoá + cốp) sẽ ra
     * một ô mà hàng cửa bấm được còn hàng nóc/rèm/gương chỉ ngồi đó — đúng loại bất nhất mà dự án đang dọn.
     *
     * `lock` và `door` cùng có mặt vì chúng KHÁC nhau sau bản vá P0 2026-09-11: `lock` là công tắc khoá/mở-khoá,
     * `door` là nút BẤM một chiều "mở khoá cửa". Trước bản vá chúng gửi cùng một byte cho hai nghĩa đối nghịch.
     *
     * ## U9 pha 2 — STRIP ⇒ [WidgetShape.BOARD] (owner 2026-09-13: *"vẽ hình xe cho những chức năng tổng hợp"*)
     * Câu nhóm này hỏi — *"xe tôi kín chưa?"* — là một câu về **không gian**: người lái cần biết cửa NÀO mở, không
     * phải *"có 1 cảnh báo"*. Dải STRIP trả lời sai loại câu hỏi: [ĐO] 10 ô con cùng một icon cửa (xem
     * [GroupBoardModel.iconsDistinguish]) nên bộ vẽ đã phải **bỏ icon**, còn lại mười ô chữ giống nhau xếp hai hàng.
     * Bảng BOARD (`DoorBoardView`) đặt mỗi bộ phận đúng chỗ của nó trên hình xe — cùng lối [TYRES] và [ADAS] đã đi.
     *
     * Nhóm này vì thế là nhóm BOARD **đầu tiên có nút** — xem [SHAPES_WITH_ACTIONS] về chỗ cho hàng nút.
     */
    val DOORS = CapabilityGroup(
        id = "g_doors", label = "Cửa & khoang", labelEn = "Doors & openings",
        icon = "ic-group-doors", domain = Domain.BODY, shape = WidgetShape.BOARD,
        reads = listOf(
            "door_lf", "door_rf", "door_lr", "door_rr",
            "tailgate_status", "tailgate_position",
            "sunroof_state", "sunroof_pos", "sunshade_pct", "mirror_fold",
        ),
        writes = listOf("lock", "door", "trunk", "sunroof", "sunshade", "mirror_fold_btn"),
        sub = "cửa, cốp, nóc, rèm, gương",
        subEn = "doors, tailgate, sunroof, sunshade, mirrors",
    )

    /**
     * *"Đèn tôi đang bật cái gì?"* — 8 đèn ngoài + chế độ pha, kèm nút.
     *
     * `readl` (đèn đọc) không có datum ĐỌC tương ứng nhưng vẫn vào phần nút: đây là nhóm "Đèn", và một cái đèn
     * trong xe mà không có mặt ở nhóm đèn thì người dùng phải đi tìm ở chỗ khác. Đèn viền tách riêng ([AMBIENT]) vì
     * nó trả lời câu hỏi khác (trang trí, đặt một lần) chứ không phải *"đèn tôi đang bật cái gì"*.
     *
     * ## ⚠⚠ [SOÁT P1-1] Vì sao KHÔNG có `headl` ở đây, dù nó vẫn còn trong [ControlRegistry]
     * `headl` ("Đèn pha", TOGGLE) và `headlight_mode` ("Chế độ đèn pha", SELECT) khai **CÙNG** `bindingKey`
     * `1276153912` **và** [HalBindingTable.writeArgs] sinh **y hệt** tham số cho cả hai — đó là một **nợ xe** đã biết
     * (`ControlWriteArgsTest.COLLISION_PENDING_CAR`), và nó được miễn trừ ở đó với lý do *"là hai mục RỜI, người dùng
     * phải cố ý đặt riêng"*. **G1 làm lý do đó hết đúng**: nhóm này đặt cả hai vào **một ô, cạnh nhau, cùng icon** ⇒
     * hai nút trông như hai việc khác nhau mà gửi cùng một byte, và người dùng không có cách nào biết.
     *
     * Giữ `headlight_mode` vì nó **nói được cả bốn trạng thái** (Tắt · Auto · Đỗ · Cốt) ⇒ phủ luôn việc bật/tắt mà
     * `headl` làm. `headl` **vẫn còn** là mục rời (không xoá khả năng của ai đang dùng nó) — chỉ không nằm cạnh
     * `headlight_mode` trong cùng một ô nữa. Chốt bằng [init] để ca này không mọc lại ở nhóm khác.
     */
    val LIGHTS = CapabilityGroup(
        id = "g_lights", label = "Đèn", labelEn = "Lights",
        icon = "ic-group-lights", domain = Domain.LIGHTS, shape = WidgetShape.STRIP,
        reads = listOf(
            "light_low_beam", "light_high_beam", "light_front_fog", "light_rear_fog",
            "light_left_turn", "light_right_turn", "light_side", "light_drl", "headlight_feedback",
        ),
        writes = listOf("headlight_mode", "drl", "readl"),
        // ⚠ [SOÁT P3-1] Dòng phụ TỪNG nói "đèn ngoài + chế độ pha" trong khi `readl` là **đèn đọc TRONG xe**. KDoc ở
        // trên giải thích vì sao đưa `readl` vào nhưng dòng phụ thì không được sửa theo ⇒ ô nói sai nội dung của
        // chính nó, ở CẢ hai thứ tiếng. Sửa cả hai cùng lúc; luật "không chép tay số" vẫn giữ (không có chữ số nào).
        sub = "đèn ngoài, chế độ pha, đèn đọc trong xe",
        subEn = "exterior lights, headlight mode, interior reading light",
    )

    /**
     * *"Đèn viền đang màu gì?"* — bật/tắt + màu + độ sáng, trước và sau.
     *
     * ⚠ Spec §4.1 xếp nhóm này là `CARD` **không kèm nút**, nên ở đây KHÔNG có `writes` — dù `ambient_power` /
     * `ambient_color` / `ambient_brightness` / `ambient_music` đều tồn tại. Giữ đúng bảng đã duyệt: bộ vẽ CARD
     * không có hàng nút (§4.3), thêm nút vào đây là thêm thứ **vẽ ra mà không ai chạm được**. Muốn bấm thì đặt bốn
     * nút đó như mục rời — đường đó vẫn còn nguyên. Ghi ra để phiên sau biết là **cố ý**, không phải bỏ sót.
     */
    val AMBIENT = CapabilityGroup(
        id = "g_ambient", label = "Đèn viền", labelEn = "Ambient light",
        icon = "ic-group-ambient", domain = Domain.LIGHTS,
        shape = WidgetShape.CARD,
        reads = listOf(
            "ambient_enabled",
            "ambient_front_color", "ambient_rear_color",
            "ambient_front_brightness", "ambient_rear_brightness",
        ),
        sub = "bật/tắt, màu và độ sáng trước–sau",
        subEn = "on/off, colour and brightness front–rear",
    )

    /**
     * *"Có gì bên cạnh tôi không?"* — điểm mù, chuyển làn, cắt ngang sau, cảnh báo mở cửa, quá tốc, ESP.
     *
     * ## ⚠ `BOARD` chứ không `STRIP` — [ĐO] kiểm toán UX 2026-09-12 (mục 4c)
     * Spec §4.1 xếp nhóm này là `STRIP`, và trên dải đó **8/10 thành viên mang CÙNG một icon sóng radar** (`bsd_*`,
     * `lca_*`, `rcta_*`, `dow_*` đều tra ra `ic-radar`) ⇒ tám ô con trông y hệt nhau, nhãn thì bị cắt (*"Điểm mù
     * trư…"* / *"Chuyển làn tr…"*) nên người xem **không phân biệt được ô nào là bên nào**. Icon ở đó không mang
     * thông tin, nó chỉ chiếm chỗ.
     *
     * Câu người lái thật sự hỏi là *"bên NÀO có vật?"* — tức một câu hỏi **không gian**, đúng ca của `BOARD`
     * (*"lưới theo hình học thật của xe"*, §4.3). Đổi kiểu vẽ, KHÔNG đổi thành viên: mọi mã ở dưới giữ nguyên, nên
     * cấu hình ai đã lưu vẫn chạy. Phía trái/phải do `:core` quyết định ([GroupBoard.sideOf]).
     */
    val ADAS = CapabilityGroup(
        id = "g_adas", label = "An toàn · ADAS", labelEn = "Safety · ADAS",
        icon = "ic-group-adas", domain = Domain.SAFETY,
        shape = WidgetShape.BOARD,
        reads = listOf(
            "bsd_fl_alarm", "bsd_fr_alarm", "lca_left", "lca_right",
            "rcta_left", "rcta_right", "dow_left", "dow_right",
            "speed_limit_warning", "esp_state",
        ),
        sub = "điểm mù, chuyển làn, cắt ngang sau, mở cửa",
        subEn = "blind spot, lane change, rear cross-traffic, door open",
    )

    /** *"Ai đang ngồi trong xe, cài dây chưa?"* — dây an toàn, nhận diện người, phát hiện trẻ em. */
    val OCCUPANTS = CapabilityGroup(
        id = "g_occupants", label = "Người ngồi", labelEn = "Occupants",
        icon = "ic-group-occupants", domain = Domain.SAFETY,
        shape = WidgetShape.STRIP,
        reads = listOf("seatbelt_driver", "seatbelt_passenger", "oms_driver", "oms_passenger", "child_presence"),
        sub = "dây an toàn + người ngồi",
        subEn = "seatbelts and who is on board",
    )

    /**
     * *"Đằng sau còn bao nhiêu chỗ?"* — 8 vùng radar + âm lượng.
     *
     * Đúng ca mà [WidgetShape.BOARD] đã ghi sẵn trong KDoc từ đầu (*"4 lốp, 8 zone radar"*) nhưng chưa ai dựng.
     */
    val PARKING = CapabilityGroup(
        id = "g_parking", label = "Cảm biến đỗ", labelEn = "Parking sensors",
        icon = "ic-group-parking", domain = Domain.SAFETY,
        shape = WidgetShape.BOARD,
        reads = listOf("radar_zones", "radar_volume"),
        sub = "vùng cảm biến quanh xe + âm lượng",
        subEn = "sensor zones around the car, plus volume",
    )

    /** *"Trong xe có dễ thở không?"* — nhiệt trong/ngoài/cài đặt, điều hoà, bụi mịn, ion âm. */
    val CLIMATE = CapabilityGroup(
        id = "g_climate", label = "Khí hậu & không khí", labelEn = "Climate & air",
        icon = "ic-group-climate", domain = Domain.CLIMATE,
        shape = WidgetShape.CARD,
        reads = listOf(
            "cabin_temp", "inside_temp", "ext_temp",
            "ac_on", "ac_wind", "ac_cycle",
            "pm25_level", "pm25_value", "pm25_online", "anion_state",
        ),
        sub = "nhiệt trong/ngoài, điều hoà, bụi mịn",
        subEn = "inside/outside temperature, air conditioning, fine dust",
    )

    /** *"Còn đi được bao xa, sạc còn lâu không?"* — pin, tầm chạy, xăng, công suất sạc và thời gian còn lại. */
    val ENERGY = CapabilityGroup(
        id = "g_energy", label = "Năng lượng & sạc", labelEn = "Energy & charging",
        icon = "ic-group-energy", domain = Domain.ENERGY,
        shape = WidgetShape.CARD,
        reads = listOf(
            "soc", "ev_range_km", "fuel_range_km", "fuel_pct",
            "is_charging", "charge_power", "charging_pct",
            "charging_eta_hour", "charging_eta_min", "motor_power",
        ),
        sub = "pin, tầm chạy, công suất sạc",
        subEn = "battery, range, charge power",
    )

    /**
     * *"Pin tôi có đang già đi không?"* — nhiệt/áp từng cell, SOH, ắc-quy 12V.
     *
     * Tách khỏi [ENERGY] vì là câu hỏi khác: [ENERGY] hỏi *hôm nay đi được bao xa*, nhóm này hỏi *pin còn tốt bao
     * lâu nữa*. Gộp làm một sẽ ra một thẻ 19 con số mà không trả lời rõ câu nào.
     */
    val BATTERY = CapabilityGroup(
        id = "g_battery", label = "Sức khoẻ pin", labelEn = "Battery health",
        icon = "ic-group-battery", domain = Domain.ENERGY,
        shape = WidgetShape.CARD,
        reads = listOf(
            "batt_temp", "cell_temp_high", "cell_temp_low", "cell_temp_avg",
            "cell_v_high", "cell_v_low", "soh_oem",
            "volt_12v", "volt_12v_level",
        ),
        sub = "nhiệt và điện áp cell, SOH, ắc-quy",
        // ⚠ [ĐO] bản dịch đầu của tôi viết *"…, SOH, 12V battery"* và `init` **đỏ ngay 86 bài**: luật "dòng phụ không
        // được chép tay số" bắt đúng chữ `12`. Ở đây con số là điện áp chứ không phải số thành viên, nhưng bản tiếng
        // Việt cũng chỉ viết "ắc-quy" (không có số) ⇒ giữ hai bản nói CÙNG một thứ, và luật giữ nguyên độ chặt.
        subEn = "cell temperature and voltage, SOH, auxiliary battery",
    )

    /** *"Chuyến này tôi đi bao nhiêu, tốn bao nhiêu?"* — quãng đường, thời gian, điện tiêu thụ, odo. */
    val TRIP = CapabilityGroup(
        id = "g_trip", label = "Chuyến đi", labelEn = "Trip",
        icon = "ic-group-trip", domain = Domain.ENERGY,
        shape = WidgetShape.CARD,
        reads = listOf("trip_km", "trip_hours", "trip_kwh", "consumption_50km", "odometer", "ev_mileage_km"),
        sub = "quãng đường, thời gian, điện tiêu thụ, odo",
        subEn = "distance, time, energy used, odometer",
    )

    /** 12 nhóm, thứ tự khai = thứ tự hiện ra. */
    val ALL: List<CapabilityGroup> = listOf(
        TYRES, WINDOWS, DOORS, LIGHTS, AMBIENT, ADAS, OCCUPANTS, PARKING, CLIMATE, ENERGY, BATTERY, TRIP,
    )

    /**
     * Bộ vẽ có thật cho nhóm (§4.3) — ba cái, không hơn.
     *
     * Nhóm khai hình khác (vd [WidgetShape.RING]) sẽ **không có bộ vẽ nào nhận**, và cái thiếu đó im lặng: ô hiện
     * ra trống chứ không sập. Vì thế chốt ngay ở [init] thay vì để phát hiện trên xe.
     */
    val SHAPES: Set<WidgetShape> = setOf(WidgetShape.BOARD, WidgetShape.STRIP, WidgetShape.CARD)

    /**
     * Bộ vẽ **có hàng nút** ở đáy ô (§4.3 + U9 pha 2).
     *
     * ## ⚠ Vì sao [WidgetShape.BOARD] vào được danh sách này từ U9 pha 2 (trước đó chỉ [WidgetShape.STRIP])
     * Hàng nút do `GroupTileView.bind` dựng và nó **chưa bao giờ** phụ thuộc vào hình — điều kiện luôn là
     * `model.hasActions`. Thứ từng thiếu là **chỗ**: bộ vẽ BOARD là một `Canvas` xin `MATCH_PARENT`, nên nó nuốt trọn
     * phần cao còn lại và hàng nút bị đẩy ra ngoài rồi cắt (đúng bệnh [ĐO] 2026-09-12 của nhóm *Kính*). Pha 2 chữa
     * bằng cách cho thân ô BOARD-có-nút nhận `weight`: `LinearLayout` đo hàng nút (chi phí CỐ ĐỊNH) trước rồi mới
     * chia phần dư cho bảng — bảng co, nút không bao giờ mất.
     *
     * [WidgetShape.CARD] vẫn đứng ngoài: chưa nhóm nào cần, và thêm một hình vào đây mà không có ảnh chụp chứng minh
     * hàng nút còn nguyên thì đúng là kiểu "nới luật cho xanh" mà bất biến này sinh ra để chặn.
     */
    val SHAPES_WITH_ACTIONS: Set<WidgetShape> = setOf(WidgetShape.STRIP, WidgetShape.BOARD)

    fun byId(id: String): CapabilityGroup? = ALL.firstOrNull { it.id == id }

    /** Nhóm theo [Domain] (thứ tự enum), domain không có nhóm thì không xuất hiện. */
    fun byDomain(): Map<Domain, List<CapabilityGroup>> =
        Domain.values().mapNotNull { d ->
            val items = ALL.filter { it.domain == d }
            if (items.isEmpty()) null else d to items
        }.toMap()

    /**
     * Tra NGƯỢC: mã rời [memberId] đang nằm trong nhóm nào.
     *
     * Trả về **danh sách** chứ không phải một nhóm, vì một mục thuộc hai nhóm là chuyện hợp lệ và sẽ tới: `volt_12v`
     * vừa là *sức khoẻ pin* vừa là *nguồn điện*. Hiện [ĐO] mỗi mã thuộc tối đa một nhóm, nhưng ép chữ ký thành
     * `CapabilityGroup?` sẽ khiến ngày đó phải sửa mọi chỗ gọi — và cái phải sửa đó sẽ được "sửa" bằng cách bỏ bớt
     * một nhóm đi.
     */
    fun groupsContaining(memberId: String): List<CapabilityGroup> = ALL.filter { memberId in it.members }

    /** Mã datum đã được ít nhất một nhóm phủ — dùng để đo tiến bộ, và để báo cáo thật thay vì đoán. */
    fun coveredReadIds(): Set<String> = ALL.flatMapTo(mutableSetOf()) { it.reads }

    /**
     * Datum KHÔNG thuộc nhóm nào — **không phải lỗi** (§4.2: mục rời còn nguyên), nhưng phải đếm được để đừng ai
     * tưởng 12 nhóm đã phủ hết 123 mục.
     */
    fun ungroupedReadIds(): List<String> =
        TelemetryRegistry.ALL.map { it.id }.filterNot { it in coveredReadIds() }

    /**
     * ⚠⚠ [SOÁT P1-1] Cặp nút trong **CÙNG một nhóm** được phép gửi y hệt nhau lên bus — mỗi mục PHẢI có lý do.
     *
     * Hiện **rỗng**, và đó là câu trả lời đúng: cặp duy nhất từng vi phạm (`headl` + `headlight_mode`) đã được xử bằng
     * cách **bỏ một mục khỏi nhóm**, không bằng cách miễn trừ. Danh sách vẫn tồn tại vì sẽ có ca thật cần nó (hai kiểu
     * ô cho **cùng một việc**, như `window`/`win_lf` ở [ControlRegistry] — nếu ngày nào cả hai vào cùng một nhóm).
     *
     * ⚠ KHÁC hẳn `ControlWriteArgsTest.COLLISION_PENDING_CAR`: danh sách đó miễn trừ cho **mục rời**, với lý do
     * *"người dùng phải cố ý đặt riêng"*. Trong một nhóm thì lý do đó không còn — hai nút nằm cạnh nhau, cùng icon,
     * người dùng không chọn gì cả. Vì thế miễn trừ ở đây phải được xét lại từ đầu, không kế thừa.
     */
    val SAME_WIRE_ALLOWED: Map<Set<String>, String> = emptyMap()

    /**
     * Cặp nút cùng nhóm gửi **y hệt** nhau lên bus (cùng lệnh xe + cùng tham số ở mọi trạng thái thăm dò).
     *
     * ## Vì sao phép so là (lệnh + THAM SỐ), không phải chỉ "cùng lệnh"
     * Chỉ so `bindingKey` sẽ bắt oan hai ca **đúng**: bốn nút kính `win_lf/rf/lr/rr` dùng chung
     * `setBodyWindowCtrlState` nhưng khác **chỉ số cửa**, và `lock`/`door` dùng chung `setDoorLockState` nhưng khác
     * **giá trị** (2 vs 1, sau bản vá P0). Cả hai đều phải được phép ở cùng một nhóm. Thứ KHÔNG được phép là hai mã
     * ra **cùng một byte** — đúng phép so mà `ControlWriteArgsTest` đã dùng cho mục rời.
     *
     * Gói lệnh ([ActionMacros]) bị bỏ qua: nó không có `bindingKey` của riêng nó (nó gộp nhiều nút), nên việc "hai gói
     * trùng nhau" là câu hỏi khác và không thuộc chỗ này.
     *
     * [resolve] tách ra để bài canh chứng minh được phép kiểm **có răng** bằng dữ liệu giả — chạy nó trên registry
     * thật (đã sạch) thì không phân biệt được "luật đúng" với "luật không bao giờ chạy".
     */
    fun sameWireWrites(
        groups: List<CapabilityGroup>,
        allowed: Set<Set<String>> = SAME_WIRE_ALLOWED.keys,
        resolve: (String) -> ControlDef? = { ControlRegistry.byId(it) },
    ): List<String> = buildList {
        groups.forEach { g ->
            val defs = g.writes.mapNotNull(resolve).filter { it.bindingKey.isNotBlank() }
            for (i in defs.indices) for (j in i + 1 until defs.size) {
                val a = defs[i]
                val b = defs[j]
                if (a.bindingKey != b.bindingKey) continue
                val identical = (0..1).all { p ->
                    HalBindingTable.writeArgs(a, p).contentEquals(HalBindingTable.writeArgs(b, p))
                }
                if (identical && setOf(a.id, b.id) !in allowed) {
                    add("${g.id}: ${a.id}(\"${a.label}\") ≡ ${b.id}(\"${b.label}\") trên ${a.bindingKey}")
                }
            }
        }
    }

    /**
     * ⚠⚠ **KHỐI NÀY PHẢI NẰM CUỐI THÂN `object`.** Thân `object` chạy **theo thứ tự khai**: đặt `init` phía trên
     * [ALL] thì lúc `require` đọc [ALL] nó còn `null` và cả gói test nổ `ExceptionInInitializerError` thay vì đỏ ở
     * một bài. Dự án đã trả giá đúng chỗ này: xem KDoc [TopStripConfig.BUILT_IN] ([ĐO] 27 bài đỏ vì `DEFAULT` dựng
     * trước khi `BUILT_IN` có giá trị) và [SettingsCatalog.init]. [ID_PREFIX] là `const` nên miễn nhiễm, các `val`
     * thì không.
     *
     * Chốt ngay lúc nạp lớp thay vì chỉ dựa vào bài test, vì một nhóm trỏ vào mã không tồn tại sẽ ra **ô thiếu
     * thành viên mà không báo gì** — người dùng chỉ thấy bảng lốp có 3 bánh và không hiểu vì sao.
     *
     * ⚠ Chỉ dùng [TelemetryRegistry] / [ControlRegistry] / [ActionMacros] ở đây, **KHÔNG** gọi [CapabilityCatalog]:
     * catalog đã hỏi lại [byId] của lớp này, nên gọi ngược sẽ thành vòng khởi tạo lớp.
     */
    init {
        require(ALL.isNotEmpty()) { "phải có nhóm — danh sách rỗng thì màn chọn không bày được gì" }

        val dupIds = ALL.groupingBy { it.id }.eachCount().filterValues { it > 1 }.keys
        require(dupIds.isEmpty()) { "mã nhóm bị trùng ⇒ hai nhóm cùng một mã, ô sẽ dựng nhầm: $dupIds" }

        val badPrefix = ALL.map { it.id }.filterNot { it.startsWith(ID_PREFIX) }
        require(badPrefix.isEmpty()) {
            "mã nhóm phải bắt đầu bằng '$ID_PREFIX' để không thể trùng mã datum/nút/widget/gói lệnh: $badPrefix"
        }

        val blank = ALL.filter { it.label.isBlank() || it.icon.isBlank() }.map { it.id }
        require(blank.isEmpty()) { "nhóm phải có nhãn và icon (ô không nhãn thì không ai biết nó là gì): $blank" }

        // T4 — dòng phụ của ô chọn. Thiếu nó thì người dùng thấy ô "Lốp" mà vẫn phải ĐOÁN bên trong có gì, và đoán
        // sai thì họ quay lại đặt bốn ô rời như trước ⇒ nhóm coi như không giao được.
        val noSub = ALL.filter { it.sub.isBlank() }.map { it.id }
        require(noSub.isEmpty()) { "nhóm phải nói nó gồm gì (dòng phụ của ô chọn): $noSub" }

        // ⚠ Số ĐẾM phải đến từ [CapabilityGroup.reads]/[CapabilityGroup.writes], không phải từ chữ viết tay: chép
        // tay "4 bánh" thì thêm/bớt một thành viên là dòng phụ nói SAI mà không bài nào đỏ.
        //
        // U5 · T2: quét CẢ [CapabilityGroup.subEn] — bản dịch cũng là chữ viết tay, nên nó có đúng cùng cái bẫy. Bỏ
        // sót nửa tiếng Anh nghĩa là luật chỉ áp cho người đọc tiếng Việt.
        val handTypedNumber = ALL.flatMap { g ->
            listOfNotNull(g.sub, g.subEn).filter { s -> s.any { it.isDigit() } }.map { "${g.id}='$it'" }
        }
        require(handTypedNumber.isEmpty()) {
            "dòng phụ KHÔNG được chép tay số — số thành viên lấy từ chính danh sách (xem contentLine): $handTypedNumber"
        }

        // U5 · T2 — mọi nhóm phải có nhãn + dòng phụ tiếng Anh. Chốt ngay lúc nạp lớp vì thiếu nó thì ô nhóm hiện
        // tiếng Việt giữa màn tiếng Anh: sai kiểu **im lặng**, và chỉ người dùng English mới thấy.
        val noEnglish = ALL.filter { it.labelEn.isNullOrBlank() || it.subEn.isNullOrBlank() }.map { it.id }
        require(noEnglish.isEmpty()) { "nhóm phải có nhãn + dòng phụ tiếng Anh (labelEn/subEn): $noEnglish" }

        val empty = ALL.filter { it.members.isEmpty() }.map { it.id }
        require(empty.isEmpty()) { "nhóm rỗng thì ô hiện ra một khung trắng: $empty" }

        val badShape = ALL.filterNot { it.shape in SHAPES }.map { "${it.id}=${it.shape}" }
        require(badShape.isEmpty()) {
            "nhóm chỉ vẽ được bằng ${SHAPES.joinToString("/")} — hình khác thì không bộ vẽ nào nhận: $badShape"
        }

        val badReads = ALL.flatMap { g -> g.reads.filter { TelemetryRegistry.byId(it) == null }.map { "${g.id}:$it" } }
        require(badReads.isEmpty()) {
            "thành viên XEM phải là datum có thật trong TelemetryRegistry (mã bịa/đã đổi tên): $badReads"
        }

        val badWrites = ALL.flatMap { g ->
            g.writes.filter { ControlRegistry.byId(it) == null && ActionMacros.byId(it) == null }
                .map { "${g.id}:$it" }
        }
        require(badWrites.isEmpty()) {
            "thành viên BẤM phải là nút có thật trong ControlRegistry hoặc gói lệnh trong ActionMacros: $badWrites"
        }

        // Nút phải có HÀNG để đứng. Khai nút vào một bộ vẽ không có hàng nút = nút vẽ ra rồi không ai chạm được —
        // đúng họ lỗi "vẽ được ≠ đặt được" của RW0, và nó im lặng.
        val writesOnWrongShape = ALL.filter { it.hasWrites && it.shape !in SHAPES_WITH_ACTIONS }.map { it.id }
        require(writesOnWrongShape.isEmpty()) {
            "bộ vẽ ${SHAPES_WITH_ACTIONS.joinToString("/")} mới có hàng nút; nhóm này khai nút nhưng bộ vẽ của nó " +
                "không có chỗ đặt: $writesOnWrongShape"
        }

        // Một mã không được vừa là XEM vừa là BẤM trong CÙNG một nhóm: hai ô con giống nhau, một cái hiện số một cái
        // bắn lệnh — không phân biệt được bằng mắt.
        val bothWays = ALL.filter { g -> g.reads.any { it in g.writes } }.map { it.id }
        require(bothWays.isEmpty()) { "một mã vừa XEM vừa BẤM trong cùng nhóm: $bothWays" }

        // ⚠⚠ [SOÁT P1-1] Hai nút cùng nhóm KHÔNG được gửi y hệt nhau lên bus.
        //
        // Ca thật đã lọt: `headl` ("Đèn pha") và `headlight_mode` ("Chế độ đèn pha") cùng `bindingKey` 1276153912 và
        // cùng tham số, nằm CẠNH NHAU trong nhóm Đèn với CÙNG icon. Là mục rời thì việc đó được miễn trừ với lý do
        // "người dùng phải cố ý đặt riêng" (`ControlWriteArgsTest.COLLISION_PENDING_CAR`); trong một nhóm thì lý do đó
        // không còn — người dùng không chọn gì, ô tự bày cả hai ra.
        //
        // Chốt ở đây (lúc nạp lớp) vì bài canh cũ của nhóm KHÔNG hề nhắc `bindingKey`: [ĐO] grep `bindingKey` trong
        // `CapabilityGroupsTest`/`GroupBoardTest` = 0 dòng, và 9 `require` có trước không phép nào hỏi câu này. Đây là
        // chặn NGUYÊN NHÂN — nhóm nào mai sau gom hai mã trùng byte thì đỏ tại chỗ khai, không đợi ai đọc ảnh.
        val sameWire = sameWireWrites(ALL)
        require(sameWire.isEmpty()) {
            "hai nút CÙNG NHÓM gửi y hệt nhau lên bus (cùng lệnh + cùng tham số) ⇒ hai ô con cạnh nhau, cùng icon, " +
                "khác nhãn mà cùng một byte: $sameWire. Bỏ một mục khỏi nhóm (nó vẫn còn là mục rời), tách tham số ở " +
                "HalBindingTable.writeArgs, hoặc — nếu thật là CÙNG một việc — khai vào SAME_WIRE_ALLOWED kèm lý do."
        }
    }
}
