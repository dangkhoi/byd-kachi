package com.byd.clusternav.launcher

/** Vị trí thanh điều khiển trên viền màn (owner: đặt được 4 viền). */
enum class DockEdge {
    BOTTOM, LEFT, RIGHT, TOP;

    /**
     * Nhãn cho người đọc (S1). Trước S1 viền chỉ đổi được bằng pill **"Thanh"** ở thanh trên xoay vòng 4 viền, nên
     * không nơi nào cần chữ; màn Cài đặt bày cả 4 viền để **chọn thẳng** (không phải bấm ba lần để tới viền mình
     * muốn) nên phải có chữ. Pill đó nay đã **bỏ hẳn** ⇒ đây là đường duy nhất, và nó cần nhãn.
     * Ở `:core` cùng lý do với [LayoutPreset.label].
     *
     * U5 · T2: nhãn sinh ra bằng `when` (không phải một dòng dữ liệu) ⇒ dịch tại chỗ bằng [Strings.t] — xem KDoc
     * [Strings] về hai cơ chế. Trả tiếng Việt khi [Strings.current] = [Lang.VI], nên test cũ giữ nguyên.
     */
    val label: String
        get() = when (this) {
            BOTTOM -> Strings.t("Dưới", "Bottom")
            LEFT -> Strings.t("Trái", "Left")
            RIGHT -> Strings.t("Phải", "Right")
            TOP -> Strings.t("Trên", "Top")
        }
}

/**
 * Kiểu tile điều khiển.
 *  • [TOGGLE] bật/tắt · [STEP] −/+ (min..max) — 2 kiểu GỐC (test cũ khoá).
 *  • [COVER] mở/đóng/(dừng) cho kính/nóc/rèm/cốp (có thể kèm %) · [SELECT] chọn 1 trong nhiều ([ControlDef.args]) ·
 *    [BUTTON] bấm-1-phát (momentary: lọc-ngay, nhớ-ghế, sạc-ngay) — 3 kiểu MỚI (W1a).
 */
enum class ControlKind { TOGGLE, STEP, COVER, SELECT, BUTTON }

// ⚠ `data class ControlDef` (hợp đồng MỘT dòng nút) đã tách sang `ControlDef.kt` ngày 2026-09-16 vì trần 500 dòng
// (CLAUDE.md §4.1) — cùng package, không đổi chữ ký. Tệp này giữ đúng vai **bảng dữ liệu** + cấu hình thanh.

/**
 * Cấu hình thanh (bền qua prefs): viền + danh sách id đang hiện (thứ tự = thứ tự hiển thị).
 *
 * **RW0 (2026-09-10)**: danh sách này nay nhận **cả thông tin ĐỌC lẫn HÀNH ĐỘNG** — xem [setEnabled].
 * Định dạng lưu KHÔNG đổi (vẫn là danh sách mã trần) ⇒ cấu hình người dùng đã lưu đọc lên nguyên vẹn (spec R4).
 */
data class DockConfig(
    val edge: DockEdge = DockEdge.BOTTOM,
    val enabled: List<String> = ControlRegistry.defaultEnabledIds(),
    /**
     * S1b (owner 2026-09-14: *"thêm chức năng cho ẩn hiện thanh luôn nhé, bên cạnh việc đặt ở trên dưới trái phải"*).
     * `false` ⇒ thanh nút KHÔNG hiện, vùng ô lấp trọn màn (xem [DockAreaLayout]). Là **lựa chọn tách khỏi [edge]**
     * (không phải viền thứ 5): ẩn rồi hiện lại giữ nguyên viền đã chọn thay vì quên mất về BOTTOM. Theo hồ sơ
     * ([ProfileScope] `dock_visible`) như [edge].
     */
    val visible: Boolean = true,
) {
    fun withEdge(e: DockEdge): DockConfig = copy(edge = e)

    /** Ẩn/hiện thanh nút — giữ nguyên [edge] và [enabled] để hiện lại đúng chỗ cũ. */
    fun withVisible(v: Boolean): DockConfig = copy(visible = v)

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
        // NEEDS-ONCAR: khoá cửa. [ĐO] stub `BYDAutoDoorLockDevice` (L hoa — cũ viết `Doorlock` ⇒ ClassNotFound) CHỈ có
        // `getDoorLockStatus(int)` (BYDAutoDoorLockDevice.java:41), KHÔNG có `setDoorLockState`. Giữ named-method với
        // tên lớp ĐÚNG để `HalWriteProbe` ghi lại đúng chuỗi ngoại lệ trên xe (bằng chứng cho grab-list) thay vì
        // ClassNotFound vô nghĩa; setter khoá-ngay có thể không expose qua HAL app → chốt trên xe.
        ControlDef("lock", "Khóa / mở khóa", "ic-car-top-lock", ControlKind.TOGGLE, enabledByDefault = true, onByDefault = true,
            domain = Domain.BODY, tier = EvidenceTier.NEEDS_CAR, bindingKey = "BYDAutoDoorLockDevice.setDoorLockState",
            labelEn = "Lock / unlock"),
        // ⚠ [ĐO] 2026-09-11: nút này TỪNG mang nhãn "Kính 50%" nhưng ghi ĐÚNG CÙNG lệnh với "win_lf"
        // (`setBodyWindowCtrlState(1, state)` — kính CỬA LÁI, chỉ đóng/mở, KHÔNG có nửa). Nhãn cũ hứa thứ xe không
        // làm. Chưa có đường GHI phần trăm nào (chỉ có đường ĐỌC `getWindowOpenPercent`) ⇒ đừng đặt lại nhãn hứa %.
        ControlDef("window", "Kính cửa lái", "ic-car-top-window-lf", ControlKind.TOGGLE, enabledByDefault = true,
            domain = Domain.BODY, tier = EvidenceTier.PROVEN, bindingKey = "BYDAutoBodyworkDevice.setBodyWindowCtrlState",
            labelEn = "Driver window"),
        ControlDef("trunk", "Cốp sau", "ic-car-top-trunk", ControlKind.TOGGLE, enabledByDefault = true,
            // [ĐO xe 2026-09-17] `setHetchDoorStatus` KHÔNG tồn tại trên ROM này; đường THẬT là
            // `BYDAutoSettingDevice.voiceCtlBackDoor(cmd)` — cmd 1 = MỞ · 3 = ĐÓNG (đo 2 lần mỗi lệnh, cốp mở/đóng
            // thật; cmd 2 không thấy tác dụng khi cốp đứng yên — [ĐOÁN] dừng-giữa-hành-trình, chưa thử lúc chạy).
            // Đọc trạng thái từ `getBackDoorOpenedHeight` cũng ở Setting device (Bodywork trả rỗng).
            domain = Domain.BODY, tier = EvidenceTier.PROVEN, bindingKey = "BYDAutoSettingDevice.voiceCtlBackDoor", readKey = "tailgate_status",
            labelEn = "Tailgate"),
        ControlDef("readl", "Đèn đọc", "ic-readlight", ControlKind.TOGGLE, enabledByDefault = true,
            domain = Domain.LIGHTS, tier = EvidenceTier.OVERDRIVE, bindingKey = "1330643002",
            // [ĐO] RE 2026-09-14 §4: feature 0x4f50003a (SET_INSIDE_LIGHT_STATE_SET) thuộc SETTING(1023), KHÔNG phải
            // LIGHT(1004) mà Domain.LIGHTS route tới ⇒ route đèn cabin sang SETTING (cần sweep xe xác nhận).
            halDevice = "BYDAutoSettingDevice",   // [CHƯA BIẾT] khoá ĐỌC — cố ý để rỗng, xem ⚠ ở KDoc [ControlDef.readKey]
            labelEn = "Reading light"),
        ControlDef("pm25", "Lọc bụi", "ic-filter", ControlKind.TOGGLE, enabledByDefault = true, onByDefault = true,
            domain = Domain.CLIMATE, tier = EvidenceTier.PROVEN, bindingKey = "BYDAutoAcDevice.setAutoCleanAirState",
            labelEn = "Air purifier"),
        ControlDef("seatc", "Ghế mát", "ic-seat", ControlKind.TOGGLE, enabledByDefault = true, onByDefault = true,
            domain = Domain.CLIMATE, tier = EvidenceTier.PROVEN, bindingKey = "BYDAutoSettingDevice.setSeatVentilatingState",
            readKey = "seat_vent_state",   // T2 [ĐO xe 2026-09-16] getter ở device Setting; thang mức ở ControlLevels
            labelEn = "Seat ventilation"),
        ControlDef("temp", "Nhiệt độ", "ic-temp", ControlKind.STEP, enabledByDefault = true, value = 22, min = 17, max = 33, step = 1,
            // [ĐO] RE 2026-09-14 §1/§5a: `setTemprature` KHÔNG tồn tại trong HAL ⇒ reflection trượt, không lệnh nào
            // tới xe. Setter thật là `setAcTemperature(type, value, tempSource, unit)` — args ở HalBindingTable.writeArgs.
            domain = Domain.CLIMATE, tier = EvidenceTier.OVERDRIVE, bindingKey = "BYDAutoAcDevice.setAcTemperature", readKey = "inside_temp", readArg = 1,   // area 1 = AC_TEMP_MAIN (ghế lái)
            labelEn = "Temperature"),
        ControlDef("fan", "Gió", "ic-fan", ControlKind.STEP, enabledByDefault = true, value = 4, min = 0, max = 7, step = 1,
            domain = Domain.CLIMATE, tier = EvidenceTier.OVERDRIVE, bindingKey = "501219340", readKey = "ac_wind",
            labelEn = "Fan"),
        // Có sẵn trong kho, mặc định TẮT (bật qua Tuỳ biến):
        ControlDef("defrost", "Sấy kính", "ic-defrost", ControlKind.TOGGLE,
            domain = Domain.CLIMATE, tier = EvidenceTier.OVERDRIVE, bindingKey = "501219362",
            readKey = "defrost_front_state",   // T2: đọc `getAcDefrostState(1)` — GHI vẫn là feature-id, khác đường
            labelEn = "Defrost"),
        // [ĐO] `setAVMSwitchState(int)` BYDAutoADASDevice.java:348 — AVM_FUNCTION_OFF=1 / ON=2 (:34-35); args ở
        // HalBindingTable.writeArgs. Cũ pseudo-id `3001` không có trong BYDAutoFeatureIds (bịa) và trùng với camera_view.
        ControlDef("cam", "Camera 360", "ic-cam", ControlKind.TOGGLE,
            domain = Domain.INFOTAINMENT, tier = EvidenceTier.OVERDRIVE, bindingKey = "BYDAutoADASDevice.setAVMSwitchState",
            labelEn = "360 camera"),
        // ⚠ [SOÁT P0 · vòng 2] TRƯỚC ĐÂY là TOGGLE nhãn "Mở cửa" — nghĩa là **tắt nó thì KHOÁ xe**, mà nhãn không
        // nói điều đó. Sau khi vá P0 (tắt = gửi 2 = khoá thật) thì đây lại đúng họ lỗi vừa dọn: "nhãn hứa việc A,
        // trạng thái kia làm việc B". Nút BẤM một chiều thì không có mặt-tắt để nói dối: bấm = mở khoá, hết.
        // NEEDS-ONCAR: cùng setter chưa tồn tại như `lock` (xem chú thích ở đó) — chỉ sửa case tên lớp.
        ControlDef("door", "Mở khóa cửa", "ic-car-top-door-all", ControlKind.BUTTON,
            domain = Domain.BODY, tier = EvidenceTier.NEEDS_CAR, bindingKey = "BYDAutoDoorLockDevice.setDoorLockState",
            labelEn = "Unlock doors"),
        // NEEDS-ONCAR: hood — nghiêng UNAVAILABLE (BODYWORK_CMD_DOOR_HOOD=5 chỉ là area ĐỌC, không có lệnh mở).
        ControlDef("hood", "Ca-pô", "ic-car-top-hood", ControlKind.TOGGLE,
            domain = Domain.BODY, tier = EvidenceTier.NEEDS_CAR, bindingKey = "BODYWORK_CMD_HOOD",
            labelEn = "Bonnet"),
        // [ĐO] `setMoonRoofState(int)` BYDAutoBodyworkDevice.java:587; OpenBYD gọi thật với state chung enum kính
        // (CarControlImpl.java:1503-1505, windowId=5 → mở=1/đóng=2). Cũ `setSunroofState` KHÔNG tồn tại.
        ControlDef("sunroof", "Cửa sổ trời", "ic-car-top-sunroof", ControlKind.TOGGLE,
            domain = Domain.BODY, tier = EvidenceTier.OVERDRIVE, bindingKey = "BYDAutoBodyworkDevice.setMoonRoofState", readKey = "sunroof_state",
            labelEn = "Sunroof"),
        // NEEDS-ONCAR: headl. [ĐO] feature 1276153912 = INSTRUMENT_HEADLIGHT_CONTROL_SET thuộc INSTRUMENT(1007), và
        // `headlight_mode` (SELECT, phủ cả 4 trạng thái) đã giữ id đó. Enum on/off của id này chưa có nguồn nên KHÔNG
        // thể tách byte an toàn ⇒ gỡ id khỏi nút này (khoá UPPER_SNAKE → BindingRoute.None) để hai nút không còn gửi
        // cùng một byte cho hai nghĩa; chốt enum trên xe rồi mới nối lại.
        ControlDef("headl", "Đèn pha", "ic-car-front-highbeam", ControlKind.TOGGLE,
            domain = Domain.LIGHTS, tier = EvidenceTier.NEEDS_CAR, bindingKey = "INSTRUMENT_HEADLIGHT_ON_OFF",
            labelEn = "Headlights"),
        ControlDef("seath", "Ghế sưởi", "ic-seat", ControlKind.TOGGLE,
            domain = Domain.CLIMATE, tier = EvidenceTier.PROVEN, bindingKey = "BYDAutoSettingDevice.setSeatHeatingState",
            readKey = "seat_heat_state",   // T2 — cùng device Setting, cùng thang mức (ĐO ra 1 = tắt)
            labelEn = "Seat heating"),
        ControlDef("recirc", "Lấy gió trong", "ic-recirc", ControlKind.TOGGLE,
            domain = Domain.CLIMATE, tier = EvidenceTier.OVERDRIVE, bindingKey = "501219355", readKey = "ac_cycle",   // [ĐO] INLOOP=1 trong / OUTLOOP=0 — BYDAutoAcDevice.java:33-34
            labelEn = "Recirculation"),
        // [ĐO] `setDayTimeLightState(int)` BYDAutoLightDevice.java:209 — DAYTIME_LIGHT_OPEN=1 / CLOSE=2 (:10/:8); args ở
        // HalBindingTable.writeArgs. Cũ ghi feature 985661476 = hằng `_STATE` (đọc) ⇒ no-op.
        ControlDef("drl", "Đèn ban ngày", "ic-car-front-drl", ControlKind.TOGGLE,
            domain = Domain.LIGHTS, tier = EvidenceTier.OVERDRIVE, bindingKey = "BYDAutoLightDevice.setDayTimeLightState", readKey = "light_drl",
            // `shortEn` vì nhãn Anh dài 20 ký tự — trong ô hàng nút của nhóm *Đèn* nó bị cắt thành `"Daytime
            // lights (D…"`. Bản Việt (12 ký tự, ba từ ngắn) tự ngắt dòng vừa nên không cần bản ngắn riêng.
            labelEn = "Daytime lights (DRL)", shortEn = "Daytime (DRL)"),
        ControlDef("vol", "Âm lượng", "ic-volume", ControlKind.STEP, value = 12, min = 0, max = 30, step = 1,
            domain = Domain.INFOTAINMENT, tier = EvidenceTier.PROVEN, bindingKey = "AudioManager.setStreamVolume",
            readKey = "media_vol",   // T2: đọc bằng `getStreamVolume` của CHÍNH Android — không mượn HAL xe
            labelEn = "Volume"),
        // BINDING-OK: 321912848 = WIPER_FRONT_WIPER_LEVEL đúng id (caveat device-target — NEEDS-ONCAR).
        ControlDef("wiper", "Gạt mưa", "ic-wiper", ControlKind.TOGGLE,
            domain = Domain.BODY, tier = EvidenceTier.OVERDRIVE, bindingKey = "321912848",
            labelEn = "Wipers"),
        ControlDef("cast", "Chiếu cụm", "ic-cast", ControlKind.TOGGLE,
            domain = Domain.INFOTAINMENT, tier = EvidenceTier.DASHCAST, bindingKey = "AutoContainer.sendInfo",
            labelEn = "Cast to cluster"),

        // ── MỞ RỘNG catalog §B (mặc định TẮT) ─────────────────────────────────────────────────────
        // Khí hậu
        // ⚠ V3 · R12 [ĐO nguồn fw-dl3]: số 1324355606 KHÔNG có trong `BYDAutoFeatureIds` của xe này ⇒ đó là lý do
        // lượt 09-16 trả sentinel "absent on trim" dù owner xác nhận **xe CÓ** điều hoà auto (E6). Hai ứng viên
        // gần nhất — `Ac.AC_CTRL_MODE_SET` (đổi CHẾ ĐỘ điều hoà) và `Ac.AC_AUTOMATIC_BUTTON_TURNED_OFF` (một cờ
        // ĐỌC) — **chưa cái nào chốt được**, và đoán sai ở đây là bắn một lệnh khí hậu lạ khi xe đang chạy.
        // NEEDS-ONCAR (1 lệnh): `featmap` rồi tra tên mang nghĩa AUTO trong set của device AC (1000).
        // ⚠ ĐỌC được nhưng vẫn CHƯA ghi được: `readKey` nối vào `getAcControlMode` (T2), còn `bindingKey` giữ nguyên
        // feature `1324355606` — id đó KHÔNG có trong `BYDAutoFeatureIds` của xe owner, và lệnh *"cấm bắn lệnh khí hậu
        // theo phỏng đoán"* còn nguyên. `readInverted` vì AC_CTRLMODE_AUTO = 0 (xem KDoc trường đó).
        // [ĐO xe 2026-09-17] điểm đo thứ hai xác nhận đường ĐỌC: `getAcControlMode` = 0 khi owner bấm AUTO trên màn,
        // 1 khi tay ⇒ `readInverted` đúng. Đường GHI vẫn chờ `featmap` trên xe (không tự nghĩ giá trị — bài học W2-P0).
        ControlDef("ac_auto", "Điều hòa AUTO", "ic-ac", ControlKind.TOGGLE,
            domain = Domain.CLIMATE, tier = EvidenceTier.OVERDRIVE, bindingKey = "1324355606",
            readKey = "ac_mode_auto", readInverted = true,
            labelEn = "A/C AUTO"),
        // U7 lượt 2 · sấy kính SAU nay có khung REAR riêng (kính hậu + biển số + sóng nhiệt); trước dùng
        // chung `ic-defrost` với sấy trước ⇒ hai ô cạnh nhau y hệt (nợ đã ghi ở kiểm kê §4).
        ControlDef("defrost_rear", "Sấy kính sau", "ic-car-rear-defrost", ControlKind.TOGGLE,
            domain = Domain.CLIMATE, tier = EvidenceTier.OVERDRIVE, bindingKey = "501219357",
            readKey = "defrost_rear_state",   // T2: `getAcDefrostState(2)`
            labelEn = "Rear defrost"),
        ControlDef("anion", "Ion âm", "ic-leaf", ControlKind.TOGGLE,
            domain = Domain.CLIMATE, tier = EvidenceTier.OVERDRIVE, bindingKey = "1337982994", readKey = "anion_state",
            // [ĐO] RE 2026-09-14 §4: PM25_ANION_STATE_SET thuộc PM2P5(1008), KHÔNG phải AC(1000) mà Domain.CLIMATE
            // route tới. `setAutoCleanAirState` (PM2P5_SET) đã chạy trên xe ⇒ cổng chữ ký PM2P5 tin cậy cao.
            halDevice = "BYDAutoPM2p5Device",
            labelEn = "Negative ions"),
        ControlDef("steer_heat", "Sưởi vô-lăng", "ic-seat", ControlKind.TOGGLE,
            domain = Domain.CLIMATE, tier = EvidenceTier.OVERDRIVE, bindingKey = "BYDAutoSettingDevice.setSteeringWheelHeatingState",
            labelEn = "Steering wheel heating"),
        ControlDef("pm25_clean_now", "Lọc ngay", "ic-filter", ControlKind.BUTTON,
            domain = Domain.CLIMATE, tier = EvidenceTier.PROVEN, bindingKey = "BYDAutoAcDevice.setQuickCleanAirState",
            labelEn = "Clean now"),
        // Thân xe — kính từng cửa (COVER)
        //
        // ⚠ [ĐO] `short`/`shortEn` ở bốn ô này KHÔNG phải trang trí: hàng nút của ô nhóm *Kính* chia 6 ô trên khung
        // 4/12 màn ⇒ mỗi ô 82px, và nhãn đầy bị cắt thành `"Kính trước-tr…"` / `"Window front-ri…"` ⇒ hai kính TRƯỚC
        // đọc ra y hệt nhau. Viết tắt theo ĐÚNG quy ước đã có ở bảng lốp (`tyre_p_fl` → `"Lốp TT"` / `"Tyre FL"`), để
        // người dùng chỉ phải học một bộ viết tắt cho cả xe.
        ControlDef("win_lf", "Kính trước-trái", "ic-car-top-window-lf", ControlKind.COVER,
            domain = Domain.BODY, tier = EvidenceTier.PROVEN, bindingKey = "BYDAutoBodyworkDevice.setBodyWindowCtrlState", readKey = "window_lf",
            args = listOf("Đóng", "Mở", "Nửa"),   // T7: mức 2 = WINDOW_OPEN_HALF=4 (writeArgs)
            labelEn = "Window front-left", argsEn = listOf("Close", "Open", "Half"),
            short = "Kính TT", shortEn = "Window FL"),
        ControlDef("win_rf", "Kính trước-phải", "ic-car-top-window-rf", ControlKind.COVER,
            domain = Domain.BODY, tier = EvidenceTier.PROVEN, bindingKey = "BYDAutoBodyworkDevice.setBodyWindowCtrlState", readKey = "window_rf",
            args = listOf("Đóng", "Mở", "Nửa"),
            labelEn = "Window front-right", argsEn = listOf("Close", "Open", "Half"),
            short = "Kính TP", shortEn = "Window FR"),
        ControlDef("win_lr", "Kính sau-trái", "ic-car-top-window-lr", ControlKind.COVER,
            domain = Domain.BODY, tier = EvidenceTier.PROVEN, bindingKey = "BYDAutoBodyworkDevice.setBodyWindowCtrlState", readKey = "window_lr",
            args = listOf("Đóng", "Mở", "Nửa"),
            labelEn = "Window rear-left", argsEn = listOf("Close", "Open", "Half"),
            short = "Kính ST", shortEn = "Window RL"),
        ControlDef("win_rr", "Kính sau-phải", "ic-car-top-window-rr", ControlKind.COVER,
            domain = Domain.BODY, tier = EvidenceTier.PROVEN, bindingKey = "BYDAutoBodyworkDevice.setBodyWindowCtrlState", readKey = "window_rr",
            args = listOf("Đóng", "Mở", "Nửa"),
            labelEn = "Window rear-right", argsEn = listOf("Close", "Open", "Half"),
            short = "Kính SP", shortEn = "Window RR"),
        ControlDef("windows_all", "Tất cả kính", "ic-car-top-window-all", ControlKind.COVER,
            domain = Domain.BODY, tier = EvidenceTier.OVERDRIVE, bindingKey = "BYDAutoBodyworkDevice.setAllWindowState",
            // T7 (owner 2026-09-18 "kính 50%"): mức 2 = Nửa → HAL `setAllWindowState(4,4,4,4)` (WINDOW_OPEN_HALF=4,
            // enum đã proven per-window; ca 4-kính-nửa AWAITING_CAR). «mở một nửa kính» dùng mức này.
            args = listOf("Đóng", "Mở", "Nửa"),
            labelEn = "All windows", argsEn = listOf("Close", "Open", "Half")),
        ControlDef("sunshade", "Rèm che nắng", "ic-car-top-sunshade", ControlKind.COVER,
            domain = Domain.BODY, tier = EvidenceTier.OVERDRIVE, bindingKey = "1330642984", args = listOf("Đóng", "Mở", "Nửa"),
            labelEn = "Sunshade", argsEn = listOf("Close", "Open", "Half")),   // T7: mức 2 = 50% (đường percent)
        // NEEDS-ONCAR: child_lock — feature-id vô danh, không có named-method.
        // ⚠ (V) FEATURE-FILTER 2026-09-17: `mirror_auto` · `mirror_fold_btn` · `rain_close` đã xoá (owner chấm NO).
        // ═══ V3 · R12 — [ĐO nguồn fw-dl3 2026-09-16] id 1276141584 CÓ tên: `DOOR_LOCK_COMMAND_AREA_CHILDLOCK_LEFT_SET`
        // (lớp lồng `Door`, `BYDAutoFeatureIds.java:12954-12958`; giá trị thứ hai = 401664 khi Toyota không CanFD).
        // Nó thuộc device **DOOR_LOCK (1041)**, KHÔNG phải BODYWORK — mà `Domain.BODY` lại đoán ra BODYWORK, nên
        // lượt bắn 09-16 rơi vào `checkDeviceFeatures` và trả sentinel "absent on trim". Bind theo TÊN ⇒ device
        // đích do `BYDAutoDeviceFeaturesMap` quyết, đúng gốc bệnh.
        // ⚠ Tên nói rõ **LEFT**: đây là khoá trẻ em cửa TRÁI. Phải có cả hai bên thì đó là hai nút, không phải
        // sửa dòng này — NEEDS-ONCAR (owner nhìn thấy cửa nào khoá khi bấm).
        ControlDef("child_lock", "Khóa trẻ em", "ic-lock", ControlKind.TOGGLE,
            domain = Domain.BODY, tier = EvidenceTier.OVERDRIVE,
            bindingKey = "BYDAutoFeatureIds.Door.DOOR_LOCK_COMMAND_AREA_CHILDLOCK_LEFT_SET",
            labelEn = "Child lock"),
        ControlDef("seat_memory", "Nhớ ghế lái", "ic-car-top-seat-fl", ControlKind.BUTTON,
            domain = Domain.BODY, tier = EvidenceTier.OVERDRIVE, bindingKey = "1276186678",
            // [ĐO] RE 2026-09-14 §4: SET_LF_MEMORY_LOCATION_SET thuộc SETTING(1023), KHÔNG phải BODYWORK(1001).
            halDevice = "BYDAutoSettingDevice",
            labelEn = "Driver seat memory"),
        // Đèn
        // NEEDS-ONCAR: 4 nút ambient_* — device nghi SETTING(1023) atmosphere-lamp; scale nghi 0–100 (không 0–10), màu 31.
        // ⚠ V3 · R12 [ĐO nguồn fw-dl3]: 1276153924 không có. Ứng viên **gần** nhất là
        // `Setting.SET_ATMOSPHERE_LAMP_PANEL_STATE_SET` = **1276153872** (lệch 52) — gần tới mức nghi cùng họ,
        // nhưng "gần" không phải "đúng", và đây là một lượt GHI vào đèn nội thất. NEEDS-ONCAR (1 lệnh, có người
        // nhìn): `hal --es op set --es dev BYDAutoSettingDevice --es m set --es args …` sau khi có `featmap`.
        ControlDef("ambient_power", "Đèn viền cabin", "ic-car-top-ambient", ControlKind.TOGGLE,
            domain = Domain.LIGHTS, tier = EvidenceTier.OVERDRIVE, bindingKey = "1276153924",
            // ⚠ Nhãn Anh phải TRÙNG với datum `ambient_enabled` **đúng như bản Việt trùng nhau** ("Đèn viền cabin"):
            // cặp này vốn là một-cái-xem / một-cái-bấm nên chúng ĐƯỢC trùng và đã có gợi ý loại (`· xem`/`· bấm`).
            // Nhóm `g_ambient` thì mang nhãn KHÁC ("Ambient light"), y như bản Việt ("Đèn viền") — nếu dịch cả ba
            // thành "Ambient lighting" thì tiếng Anh sinh ra một cặp trùng MỚI mà tiếng Việt không có, và cái trùng
            // mới đó **không được gợi ý loại** (phép phát hiện trùng chạy trên nhãn Việt).
            labelEn = "Cabin ambient light"),
        ControlDef("ambient_color", "Màu đèn viền", "ic-car-top-ambient-color-front", ControlKind.SELECT,
            domain = Domain.LIGHTS, tier = EvidenceTier.OVERDRIVE, bindingKey = "1276194864",
            args = listOf("Tím", "Xanh dương", "Xanh lá", "Vàng", "Trắng"),
            labelEn = "Ambient colour", argsEn = listOf("Purple", "Blue", "Green", "Yellow", "White")),
        ControlDef("ambient_brightness", "Độ sáng viền", "ic-car-top-ambient-bright-front", ControlKind.STEP, value = 3, min = 0, max = 10, step = 1,
            domain = Domain.LIGHTS, tier = EvidenceTier.OVERDRIVE, bindingKey = "1276194858",
            labelEn = "Ambient brightness"),
        ControlDef("ambient_music", "Đèn viền theo nhạc", "ic-car-top-ambient-music", ControlKind.TOGGLE,
            domain = Domain.LIGHTS, tier = EvidenceTier.OVERDRIVE, bindingKey = "489701407",
            labelEn = "Ambient follows music"),
        // [ĐO] 1276153912 = INSTRUMENT_HEADLIGHT_CONTROL_SET (BYDAutoFeatureIds.java) thuộc INSTRUMENT(1007) — Domain.LIGHTS
        // route thô tới LIGHT(1004) là SAI ⇒ `halDevice` ghi đè. NEEDS-ONCAR: enum index↔giá trị (hiện gửi index thô).
        ControlDef("headlight_mode", "Chế độ đèn pha", "ic-car-front-headlight-mode", ControlKind.SELECT,
            domain = Domain.LIGHTS, tier = EvidenceTier.OVERDRIVE, bindingKey = "1276153912",
            halDevice = "BYDAutoInstrumentDevice",
            args = listOf("Tắt", "Auto", "Đỗ", "Cốt"),
            labelEn = "Headlight mode", argsEn = listOf("Off", "Auto", "Parking", "Low beam")),
        // Drive / năng lượng / sạc
        // ⚠ (V) FEATURE-FILTER 2026-09-17: nút `drive_mode` (chọn chế độ lái) đã xoá — owner chấm NO. Datum
        // `op_mode` (ĐỌC xe đang ở chế độ nào) vẫn còn ở TelemetryRegistry; đừng nhầm hai cái.
        // ⚠ Nhãn Anh TRÙNG nhãn Việt: "EV / HEV" là ký hiệu ngành (và `args` cũng vậy) ⇒ có tên trong danh sách cho
        // phép của `LangCoverageTest`. Dịch thành "Electric / Hybrid" sẽ lệch với chữ trên táp-lô xe.
        // [ĐO] `setEnergyMode(int)` BYDAutoEnergyDevice.java:173 — ENERGY_MODE_EV=1 / HEV=3 (:18/:21); map ở writeArgs.
        // Cũ `setEnergyWorkMode` KHÔNG tồn tại.
        ControlDef("powertrain_mode", "EV / HEV", "ic-bolt", ControlKind.SELECT,
            domain = Domain.DRIVETRAIN, tier = EvidenceTier.OVERDRIVE, bindingKey = "BYDAutoEnergyDevice.setEnergyMode",
            args = listOf("EV", "HEV"),
            labelEn = "EV / HEV", argsEn = listOf("EV", "HEV")),
        ControlDef("regen_level", "Mức tái tạo", "ic-bolt", ControlKind.SELECT,
            domain = Domain.DRIVETRAIN, tier = EvidenceTier.OVERDRIVE, bindingKey = "BYDAutoSettingDevice.setEnergyFeedback",
            args = listOf("Tiêu chuẩn", "Cao"),
            labelEn = "Regen level", argsEn = listOf("Standard", "High")),
        // ⚠ (V) FEATURE-FILTER 2026-09-17: ba nút SẠC (`target_soc_set` · `charge_cap` · `start_charging`) đã xoá
        // — owner chấm NO cho cả cụm sạc (xem nhật ký cùng tên ở TelemetryRegistry). `wireless_charge` KHÔNG nằm
        // trong danh sách NO nên ở lại.
        // [ĐO] `setWirelessChargingSwitchState(int)` BYDAutoChargingDevice.java:458 — CHARGE_WIRELESS_CHARGING_ON=1 / OFF=2
        // (:61/:60); args ở writeArgs. Cũ feature 1312817218 route Domain.ENERGY → Statistic (sai device).
        ControlDef("wireless_charge", "Sạc không dây", "ic-charger", ControlKind.TOGGLE,
            domain = Domain.ENERGY, tier = EvidenceTier.OVERDRIVE, bindingKey = "BYDAutoChargingDevice.setWirelessChargingSwitchState",
            labelEn = "Wireless charging"),
        // ⚠ 2026-09-16 — KHÔNG có nút ADAS/an toàn nào ở đây, và sẽ không có. Owner gỡ toàn bộ (cảnh báo quá tốc ·
        // ESP · biển báo · giữ làn · va chạm trước · cắt ngang sau · mở cửa · phát hiện trẻ em · iTAC · AVH) vì một
        // lệnh sai vào hệ an toàn chủ động là rủi ro trên đường thật. Người lái chỉnh mấy thứ đó trong **setting
        // gốc của xe**; launcher không can thiệp. Thêm lại = quyết định của owner, không phải của phiên code.
        // Giải trí / cụm / HUD
        // NEEDS-ONCAR: screen_rotation (enum) / cluster_music (nghi INSTRUMENT_MUSIC_SOURCE 970981412).
        ControlDef("screen_rotation", "Xoay màn hình", "ic-cast", ControlKind.SELECT,
            domain = Domain.INFOTAINMENT, tier = EvidenceTier.OVERDRIVE, bindingKey = "1330643005",
            args = listOf("Ngang", "Dọc"),
            labelEn = "Screen rotation", argsEn = listOf("Landscape", "Portrait")),
        // [ĐO] `setDisplayMode(int)` BYDAutoPanoramaDevice.java:265 (cũ pseudo-id 3001 bịa, trùng `cam`). NEEDS-ONCAR:
        // enum DISPLAY_MODE_* (PANORAMA0/FULL_SCREEN1/WIDGET3/RF_REVERSE4/REVERSE5/3D_PANORAMA6, :46-51) KHÔNG khớp 5
        // nhãn góc — tạm gửi index thô; chốt map nhãn↔enum trên xe trước khi hứa "Trước/Sau/Trái/Phải".
        ControlDef("camera_view", "Góc camera", "ic-cam", ControlKind.SELECT,
            domain = Domain.INFOTAINMENT, tier = EvidenceTier.NEEDS_CAR, bindingKey = "BYDAutoPanoramaDevice.setDisplayMode",
            args = listOf("Trước", "Sau", "Trái", "Phải", "Rộng"),
            labelEn = "Camera view", argsEn = listOf("Front", "Rear", "Left", "Right", "Wide")),
        ControlDef("cluster_music", "Nhạc trên cụm", "ic-music", ControlKind.TOGGLE,
            domain = Domain.INFOTAINMENT, tier = EvidenceTier.OVERDRIVE, bindingKey = "1138753546",
            labelEn = "Music on cluster"),
        ControlDef("brightness_gear", "Độ sáng màn", "ic-light", ControlKind.STEP, value = 5, min = 0, max = 10, step = 1,
            domain = Domain.INFOTAINMENT, tier = EvidenceTier.OVERDRIVE, bindingKey = "1276174360",
            labelEn = "Screen brightness"),
        // HUD = ký hiệu ngành (head-up display) ⇒ giữ nguyên.
        ControlDef("hud_switch", "HUD kính lái", "ic-cast", ControlKind.TOGGLE,
            domain = Domain.INFOTAINMENT, tier = EvidenceTier.DASHCAST, bindingKey = "1276174371",
            labelEn = "Windscreen HUD"),
        ControlDef("hud_brightness", "Độ sáng HUD", "ic-light", ControlKind.STEP, value = 5, min = 0, max = 10, step = 1,
            domain = Domain.INFOTAINMENT, tier = EvidenceTier.DASHCAST, bindingKey = "1276174360",
            labelEn = "HUD brightness"),
    )

    fun byId(id: String): ControlDef? = RegistryIndex.CONTROLS[id]   // [SOÁT P3] tra băm — xem KDoc RegistryIndex
    fun defaultEnabledIds(): List<String> = ALL.filter { it.enabledByDefault }.map { it.id }
    fun defaultDock(): DockConfig = DockConfig()

    /** Nút theo domain (để dựng panel Tuỳ biến §D2). */
    fun byDomain(domain: Domain): List<ControlDef> = ALL.filter { it.domain == domain }
}
