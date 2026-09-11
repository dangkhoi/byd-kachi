package com.byd.clusternav.launcher

/** Vị trí thanh điều khiển trên viền màn (owner: đặt được 4 viền). */
enum class DockEdge { BOTTOM, LEFT, RIGHT, TOP }

/**
 * Kiểu tile điều khiển.
 *  • [TOGGLE] bật/tắt · [STEP] −/+ (min..max) — 2 kiểu GỐC (test cũ khoá).
 *  • [COVER] mở/đóng/(dừng) cho kính/nóc/rèm/cốp (có thể kèm %) · [SELECT] chọn 1 trong nhiều ([ControlDef.args]) ·
 *    [BUTTON] bấm-1-phát (momentary: lọc-ngay, nhớ-ghế, sạc-ngay) — 3 kiểu MỚI (W1a).
 */
enum class ControlKind { TOGGLE, STEP, COVER, SELECT, BUTTON }

/**
 * Catalog 1 nút điều khiển. Hành động THẬT bơm qua [CarControlPort] (BydHal on-car; [NoCar] off-car no-op).
 * Nhãn + ngữ nghĩa map theo catalog §B (`docs/diagnostics/kachi-capability-catalog-2026-09-10.md`).
 *
 * MỞ RỘNG W1a (giữ tương thích ngược — 4 field đầu + `enabledByDefault/onByDefault/value/min/max/step` KHÔNG đổi,
 * field mới đặt SAU + có default nên mọi call-site + test cũ còn nguyên):
 * @property domain nhóm panel ([Domain]) — CHỈ gom nhóm, KHÔNG gate an toàn (owner bỏ gate 2026-09-10).
 * @property tier mức bằng chứng GHI (write) — UI badge/mờ theo đây.
 * @property bindingKey khoá map HAL cho Stage 2 (named-method `"Device.method"` hoặc feature-id decimal `"501219340"`).
 * @property args tham số phụ: với [ControlKind.SELECT] = danh sách nhãn lựa chọn; kiểu khác thường rỗng.
 */
data class ControlDef(
    val id: String,
    val label: String,
    val icon: String,
    val kind: ControlKind,
    val enabledByDefault: Boolean = false,
    val onByDefault: Boolean = false,   // cho TOGGLE
    val value: Int = 0,                 // mặc định cho STEP
    val min: Int = 0,
    val max: Int = 0,
    val step: Int = 1,
    val domain: Domain = Domain.CLIMATE,
    val tier: EvidenceTier = EvidenceTier.OVERDRIVE,
    val bindingKey: String = "",
    val args: List<String> = emptyList(),
) {
    fun clamp(v: Int): Int = if (kind == ControlKind.STEP) v.coerceIn(min, max) else v
}

/**
 * Cấu hình thanh (bền qua prefs): viền + danh sách id đang hiện (thứ tự = thứ tự hiển thị).
 *
 * **RW0 (2026-09-10)**: danh sách này nay nhận **cả thông tin ĐỌC lẫn HÀNH ĐỘNG** — xem [setEnabled].
 * Định dạng lưu KHÔNG đổi (vẫn là danh sách mã trần) ⇒ cấu hình người dùng đã lưu đọc lên nguyên vẹn (spec R4).
 */
data class DockConfig(
    val edge: DockEdge = DockEdge.BOTTOM,
    val enabled: List<String> = ControlRegistry.defaultEnabledIds(),
) {
    fun withEdge(e: DockEdge): DockConfig = copy(edge = e)

    /**
     * Bật/tắt một **khả năng** trong thanh.
     *
     * TRƯỚC RW0 chỗ này chặn: `if (ControlRegistry.byId(id) == null) return this` ⇒ mọi mã KHÔNG phải nút bị **bỏ
     * qua im lặng**, nên thông tin đọc (áp suất lốp, phần trăm pin…) không bao giờ vào được thanh. Đó là **cổng chặn
     * thật** của yêu cầu "đặt được ở cả 3 vùng" (spec Đ3/R2).
     *
     * NAY nhận mọi mã có trong [CapabilityCatalog] (đọc HOẶC hành động). Vẫn từ chối mã lạ — giữ nguyên tính chất
     * "không nhét rác vào cấu hình bền" của bản cũ.
     */
    fun setEnabled(id: String, on: Boolean): DockConfig {
        if (CapabilityCatalog.kindOf(id) == null) return this
        val cur = enabled.toMutableList()
        if (on) { if (id !in cur) cur.add(id) } else cur.remove(id)
        return copy(enabled = cur)
    }
    fun isVertical(): Boolean = edge == DockEdge.LEFT || edge == DockEdge.RIGHT
}

/**
 * REGISTRY CONTROL — mọi actuator điều khiển được, registry-driven. 20 nút GỐC giữ nguyên đầu danh sách (thứ tự +
 * cờ `enabledByDefault` KHÔNG đổi → `defaultEnabledIds()` bất biến, test cũ xanh); phần mở rộng catalog §B nối
 * TIẾP theo, mặc định TẮT (bật qua Tuỳ biến). KHÔNG gate an toàn.
 */
object ControlRegistry {
    val ALL: List<ControlDef> = listOf(
        // ── 20 nút GỐC (giữ nguyên id + thứ tự + cờ default; bổ sung domain/tier/bindingKey) ───────
        ControlDef("lock", "Khoá / mở khoá", "ic-lock", ControlKind.TOGGLE, enabledByDefault = true, onByDefault = true,
            domain = Domain.BODY, tier = EvidenceTier.OVERDRIVE, bindingKey = "BYDAutoDoorlockDevice.setDoorLockState"),
        // ⚠ [ĐO] 2026-09-11: nút này TỪNG mang nhãn "Kính 50%" nhưng ghi ĐÚNG CÙNG lệnh với "win_lf"
        // (`setBodyWindowCtrlState(1, state)` — kính CỬA LÁI, chỉ đóng/mở, KHÔNG có nửa). Nhãn cũ hứa thứ xe không
        // làm. Chưa có đường GHI phần trăm nào (chỉ có đường ĐỌC `getWindowOpenPercent`) ⇒ đừng đặt lại nhãn hứa %.
        ControlDef("window", "Kính cửa lái", "ic-window", ControlKind.TOGGLE, enabledByDefault = true,
            domain = Domain.BODY, tier = EvidenceTier.PROVEN, bindingKey = "BYDAutoBodyworkDevice.setBodyWindowCtrlState"),
        ControlDef("trunk", "Cốp sau", "ic-trunk", ControlKind.TOGGLE, enabledByDefault = true,
            domain = Domain.BODY, tier = EvidenceTier.PROVEN, bindingKey = "BYDAutoBodyworkDevice.setHetchDoorStatus"),
        ControlDef("readl", "Đèn đọc", "ic-readlight", ControlKind.TOGGLE, enabledByDefault = true,
            domain = Domain.LIGHTS, tier = EvidenceTier.OVERDRIVE, bindingKey = "1330643002"),
        ControlDef("pm25", "Lọc bụi", "ic-leaf", ControlKind.TOGGLE, enabledByDefault = true, onByDefault = true,
            domain = Domain.CLIMATE, tier = EvidenceTier.PROVEN, bindingKey = "BYDAutoAcDevice.setAutoCleanAirState"),
        ControlDef("seatc", "Ghế mát", "ic-seat", ControlKind.TOGGLE, enabledByDefault = true, onByDefault = true,
            domain = Domain.CLIMATE, tier = EvidenceTier.PROVEN, bindingKey = "BYDAutoSettingDevice.setSeatVentilatingState"),
        ControlDef("temp", "Nhiệt độ", "ic-temp", ControlKind.STEP, enabledByDefault = true, value = 22, min = 17, max = 33, step = 1,
            domain = Domain.CLIMATE, tier = EvidenceTier.OVERDRIVE, bindingKey = "BYDAutoAcDevice.setTemprature"),
        ControlDef("fan", "Gió", "ic-fan", ControlKind.STEP, enabledByDefault = true, value = 4, min = 0, max = 7, step = 1,
            domain = Domain.CLIMATE, tier = EvidenceTier.OVERDRIVE, bindingKey = "501219340"),
        // Có sẵn trong kho, mặc định TẮT (bật qua Tuỳ biến):
        ControlDef("defrost", "Sấy kính", "ic-defrost", ControlKind.TOGGLE,
            domain = Domain.CLIMATE, tier = EvidenceTier.OVERDRIVE, bindingKey = "501219362"),
        ControlDef("cam", "Camera 360", "ic-cam", ControlKind.TOGGLE,
            domain = Domain.INFOTAINMENT, tier = EvidenceTier.OVERDRIVE, bindingKey = "3001"),
        // ⚠ [SOÁT P0 · vòng 2] TRƯỚC ĐÂY là TOGGLE nhãn "Mở cửa" — nghĩa là **tắt nó thì KHOÁ xe**, mà nhãn không
        // nói điều đó. Sau khi vá P0 (tắt = gửi 2 = khoá thật) thì đây lại đúng họ lỗi vừa dọn: "nhãn hứa việc A,
        // trạng thái kia làm việc B". Nút BẤM một chiều thì không có mặt-tắt để nói dối: bấm = mở khoá, hết.
        ControlDef("door", "Mở khoá cửa", "ic-door", ControlKind.BUTTON,
            domain = Domain.BODY, tier = EvidenceTier.NEEDS_CAR, bindingKey = "BYDAutoDoorlockDevice.setDoorLockState"),
        ControlDef("hood", "Ca-pô", "ic-hood", ControlKind.TOGGLE,
            domain = Domain.BODY, tier = EvidenceTier.NEEDS_CAR, bindingKey = "BODYWORK_CMD_HOOD"),
        ControlDef("sunroof", "Cửa sổ trời", "ic-sunroof", ControlKind.TOGGLE,
            domain = Domain.BODY, tier = EvidenceTier.OVERDRIVE, bindingKey = "BYDAutoBodyworkDevice.setSunroofState"),
        ControlDef("headl", "Đèn pha", "ic-light", ControlKind.TOGGLE,
            domain = Domain.LIGHTS, tier = EvidenceTier.OVERDRIVE, bindingKey = "1276153912"),
        ControlDef("seath", "Ghế sưởi", "ic-seat", ControlKind.TOGGLE,
            domain = Domain.CLIMATE, tier = EvidenceTier.PROVEN, bindingKey = "BYDAutoSettingDevice.setSeatHeatingState"),
        ControlDef("recirc", "Lấy gió trong", "ic-recirc", ControlKind.TOGGLE,
            domain = Domain.CLIMATE, tier = EvidenceTier.OVERDRIVE, bindingKey = "501219355"),
        ControlDef("drl", "Đèn ban ngày", "ic-light", ControlKind.TOGGLE,
            domain = Domain.LIGHTS, tier = EvidenceTier.OVERDRIVE, bindingKey = "985661476"),
        ControlDef("vol", "Âm lượng", "ic-volume", ControlKind.STEP, value = 12, min = 0, max = 30, step = 1,
            domain = Domain.INFOTAINMENT, tier = EvidenceTier.PROVEN, bindingKey = "AudioManager.setStreamVolume"),
        ControlDef("wiper", "Gạt mưa", "ic-wiper", ControlKind.TOGGLE,
            domain = Domain.BODY, tier = EvidenceTier.OVERDRIVE, bindingKey = "321912848"),
        ControlDef("cast", "Chiếu cụm", "ic-cast", ControlKind.TOGGLE,
            domain = Domain.INFOTAINMENT, tier = EvidenceTier.DASHCAST, bindingKey = "AutoContainer.sendInfo"),

        // ── MỞ RỘNG catalog §B (mặc định TẮT) ─────────────────────────────────────────────────────
        // Khí hậu
        ControlDef("ac_auto", "Điều hoà AUTO", "ic-fan", ControlKind.TOGGLE,
            domain = Domain.CLIMATE, tier = EvidenceTier.OVERDRIVE, bindingKey = "1324355606"),
        ControlDef("defrost_rear", "Sấy kính sau", "ic-defrost", ControlKind.TOGGLE,
            domain = Domain.CLIMATE, tier = EvidenceTier.OVERDRIVE, bindingKey = "501219357"),
        ControlDef("anion", "Ion âm", "ic-leaf", ControlKind.TOGGLE,
            domain = Domain.CLIMATE, tier = EvidenceTier.OVERDRIVE, bindingKey = "1337982994"),
        ControlDef("steer_heat", "Sưởi vô-lăng", "ic-seat", ControlKind.TOGGLE,
            domain = Domain.CLIMATE, tier = EvidenceTier.OVERDRIVE, bindingKey = "BYDAutoSettingDevice.setSteeringWheelHeatingState"),
        ControlDef("pm25_clean_now", "Lọc ngay", "ic-leaf", ControlKind.BUTTON,
            domain = Domain.CLIMATE, tier = EvidenceTier.PROVEN, bindingKey = "BYDAutoAcDevice.setQuickCleanAirState"),
        // Thân xe — kính từng cửa (COVER)
        ControlDef("win_lf", "Kính trước-trái", "ic-window", ControlKind.COVER,
            domain = Domain.BODY, tier = EvidenceTier.PROVEN, bindingKey = "BYDAutoBodyworkDevice.setBodyWindowCtrlState",
            args = listOf("Đóng", "Mở")),
        ControlDef("win_rf", "Kính trước-phải", "ic-window", ControlKind.COVER,
            domain = Domain.BODY, tier = EvidenceTier.PROVEN, bindingKey = "BYDAutoBodyworkDevice.setBodyWindowCtrlState",
            args = listOf("Đóng", "Mở")),
        ControlDef("win_lr", "Kính sau-trái", "ic-window", ControlKind.COVER,
            domain = Domain.BODY, tier = EvidenceTier.PROVEN, bindingKey = "BYDAutoBodyworkDevice.setBodyWindowCtrlState",
            args = listOf("Đóng", "Mở")),
        ControlDef("win_rr", "Kính sau-phải", "ic-window", ControlKind.COVER,
            domain = Domain.BODY, tier = EvidenceTier.PROVEN, bindingKey = "BYDAutoBodyworkDevice.setBodyWindowCtrlState",
            args = listOf("Đóng", "Mở")),
        ControlDef("windows_all", "Tất cả kính", "ic-window", ControlKind.COVER,
            domain = Domain.BODY, tier = EvidenceTier.OVERDRIVE, bindingKey = "BYDAutoBodyworkDevice.setAllWindowState",
            args = listOf("Đóng", "Mở")),
        ControlDef("sunshade", "Rèm che nắng", "ic-sunroof", ControlKind.COVER,
            domain = Domain.BODY, tier = EvidenceTier.OVERDRIVE, bindingKey = "1330642984", args = listOf("Đóng", "Mở")),
        ControlDef("child_lock", "Khoá trẻ em", "ic-lock", ControlKind.TOGGLE,
            domain = Domain.BODY, tier = EvidenceTier.OVERDRIVE, bindingKey = "1276141584"),
        ControlDef("rain_close", "Tự đóng kính khi mưa", "ic-wiper", ControlKind.TOGGLE,
            domain = Domain.BODY, tier = EvidenceTier.OVERDRIVE, bindingKey = "BYDAutoBodyworkDevice.setRainCloseWindow"),
        ControlDef("mirror_auto", "Gập gương khi khoá", "ic-mirror", ControlKind.TOGGLE,
            domain = Domain.BODY, tier = EvidenceTier.OVERDRIVE, bindingKey = "1081081882"),
        ControlDef("mirror_fold_btn", "Gập gương", "ic-mirror", ControlKind.BUTTON,
            domain = Domain.BODY, tier = EvidenceTier.OVERDRIVE, bindingKey = "1276157992"),
        ControlDef("seat_memory", "Nhớ ghế lái", "ic-seat", ControlKind.BUTTON,
            domain = Domain.BODY, tier = EvidenceTier.OVERDRIVE, bindingKey = "1276186678"),
        // Đèn
        ControlDef("ambient_power", "Đèn viền cabin", "ic-light", ControlKind.TOGGLE,
            domain = Domain.LIGHTS, tier = EvidenceTier.OVERDRIVE, bindingKey = "1276153924"),
        ControlDef("ambient_color", "Màu đèn viền", "ic-light", ControlKind.SELECT,
            domain = Domain.LIGHTS, tier = EvidenceTier.OVERDRIVE, bindingKey = "1276194864",
            args = listOf("Tím", "Xanh dương", "Xanh lá", "Vàng", "Trắng")),
        ControlDef("ambient_brightness", "Độ sáng viền", "ic-light", ControlKind.STEP, value = 3, min = 0, max = 10, step = 1,
            domain = Domain.LIGHTS, tier = EvidenceTier.OVERDRIVE, bindingKey = "1276194858"),
        ControlDef("ambient_music", "Đèn viền theo nhạc", "ic-light", ControlKind.TOGGLE,
            domain = Domain.LIGHTS, tier = EvidenceTier.OVERDRIVE, bindingKey = "489701407"),
        ControlDef("headlight_mode", "Chế độ đèn pha", "ic-light", ControlKind.SELECT,
            domain = Domain.LIGHTS, tier = EvidenceTier.OVERDRIVE, bindingKey = "1276153912",
            args = listOf("Tắt", "Auto", "Đỗ", "Cốt")),
        // Drive / năng lượng / sạc
        ControlDef("drive_mode", "Chế độ lái", "ic-drive", ControlKind.SELECT,
            domain = Domain.DRIVETRAIN, tier = EvidenceTier.OVERDRIVE, bindingKey = "1272971280",
            args = listOf("Thường", "Eco", "Thể thao", "Tuyết")),
        ControlDef("powertrain_mode", "EV / HEV", "ic-bolt", ControlKind.SELECT,
            domain = Domain.DRIVETRAIN, tier = EvidenceTier.OVERDRIVE, bindingKey = "BYDAutoEnergyDevice.setEnergyWorkMode",
            args = listOf("EV", "HEV")),
        ControlDef("regen_level", "Mức tái tạo", "ic-bolt", ControlKind.SELECT,
            domain = Domain.DRIVETRAIN, tier = EvidenceTier.OVERDRIVE, bindingKey = "BYDAutoSettingDevice.setEnergyFeedback",
            args = listOf("Tiêu chuẩn", "Cao")),
        ControlDef("itac", "iTAC (kiểm soát mô-men)", "ic-drive", ControlKind.TOGGLE,
            domain = Domain.DRIVETRAIN, tier = EvidenceTier.OVERDRIVE, bindingKey = "1324376094"),
        ControlDef("avh", "Giữ phanh tự động (AVH)", "ic-drive", ControlKind.TOGGLE,
            domain = Domain.DRIVETRAIN, tier = EvidenceTier.OVERDRIVE, bindingKey = "ADAS_AVH_STATE"),
        ControlDef("target_soc_set", "Mục tiêu sạc", "ic-bolt", ControlKind.STEP, value = 80, min = 50, max = 100, step = 5,
            domain = Domain.ENERGY, tier = EvidenceTier.OVERDRIVE, bindingKey = "SET_DR_SOC_TARGET"),
        ControlDef("charge_cap", "Giới hạn sạc", "ic-bolt", ControlKind.TOGGLE,
            domain = Domain.ENERGY, tier = EvidenceTier.OVERDRIVE, bindingKey = "1324376132"),
        ControlDef("wireless_charge", "Sạc không dây", "ic-bolt", ControlKind.TOGGLE,
            domain = Domain.ENERGY, tier = EvidenceTier.OVERDRIVE, bindingKey = "1312817218"),
        ControlDef("start_charging", "Sạc ngay", "ic-bolt", ControlKind.BUTTON,
            domain = Domain.ENERGY, tier = EvidenceTier.NEEDS_CAR, bindingKey = "StartChargingNowCommand"),
        // ADAS (panel riêng — KHÔNG gate, owner tự chịu)
        ControlDef("adas_slw", "Cảnh báo quá tốc", "ic-adas", ControlKind.TOGGLE,
            domain = Domain.SAFETY, tier = EvidenceTier.OVERDRIVE, bindingKey = "850452531"),
        ControlDef("adas_esp", "Cân bằng điện tử (ESP)", "ic-adas", ControlKind.TOGGLE,
            domain = Domain.SAFETY, tier = EvidenceTier.NEEDS_CAR, bindingKey = "944766984"),
        ControlDef("adas_tsr", "Nhận diện biển báo", "ic-adas", ControlKind.TOGGLE,
            domain = Domain.SAFETY, tier = EvidenceTier.OVERDRIVE, bindingKey = "944767044"),
        ControlDef("adas_lane", "Hỗ trợ giữ làn", "ic-adas", ControlKind.SELECT,
            domain = Domain.SAFETY, tier = EvidenceTier.OVERDRIVE, bindingKey = "BYDAutoADASDevice.setLKSMode",
            args = listOf("Tắt", "LDW", "LDP", "Cả hai")),
        ControlDef("adas_fcw", "Cảnh báo va chạm trước", "ic-adas", ControlKind.STEP, value = 2, min = 0, max = 3, step = 1,
            domain = Domain.SAFETY, tier = EvidenceTier.OVERDRIVE, bindingKey = "1324560420"),
        ControlDef("adas_rcta", "Cắt ngang phía sau", "ic-adas", ControlKind.TOGGLE,
            domain = Domain.SAFETY, tier = EvidenceTier.OVERDRIVE, bindingKey = "944766990"),
        ControlDef("adas_dow", "Cảnh báo mở cửa", "ic-adas", ControlKind.TOGGLE,
            domain = Domain.SAFETY, tier = EvidenceTier.OVERDRIVE, bindingKey = "944766994"),
        ControlDef("adas_cpd", "Phát hiện trẻ em", "ic-adas", ControlKind.TOGGLE,
            domain = Domain.SAFETY, tier = EvidenceTier.OVERDRIVE, bindingKey = "1324617778"),
        // Giải trí / cụm / HUD
        ControlDef("screen_rotation", "Xoay màn hình", "ic-cast", ControlKind.SELECT,
            domain = Domain.INFOTAINMENT, tier = EvidenceTier.OVERDRIVE, bindingKey = "1330643005",
            args = listOf("Ngang", "Dọc")),
        ControlDef("camera_view", "Góc camera", "ic-cam", ControlKind.SELECT,
            domain = Domain.INFOTAINMENT, tier = EvidenceTier.OVERDRIVE, bindingKey = "3001",
            args = listOf("Trước", "Sau", "Trái", "Phải", "Rộng")),
        ControlDef("cluster_music", "Nhạc trên cụm", "ic-music", ControlKind.TOGGLE,
            domain = Domain.INFOTAINMENT, tier = EvidenceTier.OVERDRIVE, bindingKey = "1138753546"),
        ControlDef("brightness_gear", "Độ sáng màn", "ic-light", ControlKind.STEP, value = 5, min = 0, max = 10, step = 1,
            domain = Domain.INFOTAINMENT, tier = EvidenceTier.OVERDRIVE, bindingKey = "1276174360"),
        ControlDef("hud_switch", "HUD kính lái", "ic-cast", ControlKind.TOGGLE,
            domain = Domain.INFOTAINMENT, tier = EvidenceTier.DASHCAST, bindingKey = "1276174371"),
        ControlDef("hud_brightness", "Độ sáng HUD", "ic-light", ControlKind.STEP, value = 5, min = 0, max = 10, step = 1,
            domain = Domain.INFOTAINMENT, tier = EvidenceTier.DASHCAST, bindingKey = "1276174360"),
    )

    fun byId(id: String): ControlDef? = ALL.firstOrNull { it.id == id }
    fun defaultEnabledIds(): List<String> = ALL.filter { it.enabledByDefault }.map { it.id }
    fun defaultDock(): DockConfig = DockConfig()

    /** Nút theo domain (để dựng panel Tuỳ biến §D2). */
    fun byDomain(domain: Domain): List<ControlDef> = ALL.filter { it.domain == domain }
}
