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

    /**
     * ═══ UX-OVERHAUL · WP4 — DỜI CHỖ MỘT NÚT TRONG THANH ════════════════════════════════════════════════════
     *
     * [enabled] đã là **danh sách có thứ tự** (thứ tự = thứ tự nút hiện trên thanh) từ RW0, nhưng tới 1.85 **không
     * có bề mặt nào sắp lại được nó**: [DockSelection.apply] cố ý giữ nguyên chỗ của phần cũ và nối phần mới vào
     * cuối (xem KDoc ở đó — xáo lại theo thứ tự catalog là một lỗi đã tránh). Nên nút vào thanh muộn thì **mãi mãi**
     * ở cuối. WP4 · R4.1 vá đúng chỗ ấy.
     *
     * Uỷ quyền [BarOrder.move] — CÙNG phép với thứ tự thanh trên ([HeaderLayout.move]), không viết bản thứ hai.
     * Không dời được (đụng biên / mã không có trong thanh) ⇒ trả về **chính** vật này, để chỗ gọi biết mà không
     * nhân đôi luật.
     */
    fun moveEnabled(id: String, delta: Int): DockConfig {
        val next = BarOrder.move(enabled, id, delta)
        return if (next === enabled) this else copy(enabled = next)
    }

    /** Dời được nút [id] theo [delta] hay không — CÙNG luật với [moveEnabled]. */
    fun canMove(id: String, delta: Int): Boolean = BarOrder.canMove(enabled, id, delta)
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
        // ⚠ [ĐO] 2026-09-11: nút này TỪNG mang nhãn "Kính 50%" nhưng ghi ĐÚNG CÙNG lệnh với "win_lf"
        // (`setBodyWindowCtrlState(1, state)` — kính CỬA LÁI, chỉ đóng/mở, KHÔNG có nửa). Nhãn cũ hứa thứ xe không
        // làm. Chưa có đường GHI phần trăm nào (chỉ có đường ĐỌC `getWindowOpenPercent`) ⇒ đừng đặt lại nhãn hứa %.
        ControlDef("trunk", "Cốp sau", "ic-car-top-trunk", ControlKind.COVER, enabledByDefault = true,
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
        ControlDef("seatc", "Mát ghế lái", "ic-seat-left", ControlKind.SELECT, enabledByDefault = true,
            args = listOf("Tắt", "Mức 1", "Mức 2"), argsEn = listOf("Off", "Level 1", "Level 2"),
            domain = Domain.CLIMATE, tier = EvidenceTier.PROVEN, bindingKey = "BYDAutoSettingDevice.setSeatVentilatingState",
            readKey = "seat_vent_state",   // T2 [ĐO xe 2026-09-16] getter ở device Setting; thang mức ở ControlLevels
            // 3 mức (Tắt/Mức1/Mức2) — [ĐO xe 2026-09-17 ControlLevels] getSeatVentilatingState OFF=1·mức1=2·mức2=3.
            // writeArgs đổi index (0/1/2) → state khung (1/2/3) qua ControlLevels.rawForLevel. Voice: "ghế mát mức 1/2".
            labelEn = "Driver seat ventilation"),
        ControlDef("temp", "Nhiệt độ", "ic-temp", ControlKind.STEP, enabledByDefault = true, value = 22, min = 17, max = 33, step = 1,
            // [ĐO] RE 2026-09-14 §1/§5a: `setTemprature` KHÔNG tồn tại trong HAL ⇒ reflection trượt, không lệnh nào
            // tới xe. Setter thật là `setAcTemperature(type, value, tempSource, unit)` — args ở HalBindingTable.writeArgs.
            domain = Domain.CLIMATE, tier = EvidenceTier.OVERDRIVE, bindingKey = "BYDAutoAcDevice.setAcTemperature", readKey = "inside_temp", readArg = 1,   // area 1 = AC_TEMP_MAIN (ghế lái)
            labelEn = "Temperature"),
        ControlDef("fan", "Gió", "ic-fan", ControlKind.STEP, enabledByDefault = true, value = 4, min = 0, max = 7, step = 1,
            domain = Domain.CLIMATE, tier = EvidenceTier.OVERDRIVE, bindingKey = "501219340", readKey = "ac_wind",
            labelEn = "Fan"),
        // Có sẵn trong kho, mặc định TẮT (bật qua Tuỳ biến):
        ControlDef("defrost", "Sấy kính trước", "ic-defrost", ControlKind.TOGGLE,
            domain = Domain.CLIMATE, tier = EvidenceTier.OVERDRIVE, bindingKey = "501219362",
            readKey = "defrost_front_state",   // T2: đọc `getAcDefrostState(1)` — GHI vẫn là feature-id, khác đường
            labelEn = "Front defrost"),
        // [ĐO] `setAVMSwitchState(int)` BYDAutoADASDevice.java:348 — AVM_FUNCTION_OFF=1 / ON=2 (:34-35); args ở
        // HalBindingTable.writeArgs. Cũ pseudo-id `3001` không có trong BYDAutoFeatureIds (bịa) và trùng với camera_view.
        ControlDef("cam", "Camera 360", "ic-cam", ControlKind.TOGGLE,
            domain = Domain.INFOTAINMENT, tier = EvidenceTier.OVERDRIVE, bindingKey = "BYDAutoADASDevice.setAVMSwitchState",
            labelEn = "360 camera"),
        // ⚠ [SOÁT P0 · vòng 2] TRƯỚC ĐÂY là TOGGLE nhãn "Mở cửa" — nghĩa là **tắt nó thì KHOÁ xe**, mà nhãn không
        // nói điều đó. Sau khi vá P0 (tắt = gửi 2 = khoá thật) thì đây lại đúng họ lỗi vừa dọn: "nhãn hứa việc A,
        // trạng thái kia làm việc B". Nút BẤM một chiều thì không có mặt-tắt để nói dối: bấm = mở khoá, hết.
        // NEEDS-ONCAR: cùng setter chưa tồn tại như `lock` (xem chú thích ở đó) — chỉ sửa case tên lớp.
        // ⚠⚠ 1.85 · **`hood` (Ca-pô) ĐÃ XOÁ HẲN** — [ĐO xe 2026-09-20 §4] owner xác nhận bằng mắt: xe **KHÔNG có
        // ca-pô điện**, chỉ cốp sau điện. Từ 1.66 nó đã bị ẩn khỏi bộ chọn (`HIDDEN_FROM_PICKER`) với lý do ấy; lượt
        // này owner chốt bỏ hẳn nên giữ một dòng registry không ai bấm được nữa chỉ là dead code.
        // ⇒ Đây là mã đầu tiên rời **khối 20 nút GỐC**, nên khối đó nay còn **19** và `ControlRegistryExtendedTest`
        // đã ghim lại danh sách mới (thứ tự các nút còn lại KHÔNG đổi).
        // Ô/thanh nút của ai đã đặt `hood` tự rụng khi nạp — `WorkspaceState.sanitized()` (đường đã dựng ở ADAS-PURGE
        // Pass 1 rồi dùng lại cho 19 mã của (V) FEATURE-FILTER); `WorkspaceStateTest` có ca duyệt riêng cho `hood`.
        // [ĐO nguồn] RE cũ cũng đã nghi đúng: `BODYWORK_CMD_HOOD` chỉ là **area ĐỌC**, không có lệnh mở.
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
        ControlDef("seath", "Sưởi ghế lái", "ic-seat-left", ControlKind.SELECT,
            args = listOf("Tắt", "Mức 1", "Mức 2"), argsEn = listOf("Off", "Level 1", "Level 2"),
            domain = Domain.CLIMATE, tier = EvidenceTier.PROVEN, bindingKey = "BYDAutoSettingDevice.setSeatHeatingState",
            readKey = "seat_heat_state",   // T2 — cùng device Setting; thang mức ControlLevels (seath [SUY] 1/2/3/4, đo lại)
            labelEn = "Driver seat heating"),
        ControlDef("recirc", "Lấy gió trong", "ic-recirc", ControlKind.TOGGLE,
            domain = Domain.CLIMATE, tier = EvidenceTier.OVERDRIVE, bindingKey = "501219355", readKey = "ac_cycle",   // [ĐO] INLOOP=1 trong / OUTLOOP=0 — BYDAutoAcDevice.java:33-34
            labelEn = "Recirculation"),
        // [ĐO] `setDayTimeLightState(int)` BYDAutoLightDevice.java:209 — DAYTIME_LIGHT_OPEN=1 / CLOSE=2 (:10/:8); args ở
        // HalBindingTable.writeArgs. Cũ ghi feature 985661476 = hằng `_STATE` (đọc) ⇒ no-op.
        // ⚠ readKey BỎ (owner 2026-09-24 [ĐO xe follow]): lệnh GHI CHẠY THẬT (đèn ban ngày đổi) nhưng đọc lại
        // `light_drl` KHÔNG phản ánh đúng state ⇒ readback lệch ⇒ báo DỐI "xe không nhận tín hiệu" cho một nút
        // work. Bỏ verify → nút báo best-effort "đã bật/tắt", không false-report (dự án cấm nút nói dối).
        ControlDef("drl", "Đèn ban ngày", "ic-car-front-drl", ControlKind.TOGGLE,
            domain = Domain.LIGHTS, tier = EvidenceTier.OVERDRIVE, bindingKey = "BYDAutoLightDevice.setDayTimeLightState", readKey = "",
            // `shortEn` vì nhãn Anh dài 20 ký tự — trong ô hàng nút của nhóm *Đèn* nó bị cắt thành `"Daytime
            // lights (D…"`. Bản Việt (12 ký tự, ba từ ngắn) tự ngắt dòng vừa nên không cần bản ngắn riêng.
            labelEn = "Daytime lights (DRL)", shortEn = "Daytime (DRL)"),
        // ⚠⚠ 1.90 · **`vol` (Âm lượng) và `cast` (Chiếu cụm) ĐÃ XOÁ HẲN** — owner chốt 2026-09-21 sau lượt sweep
        // trên xe (`docs/diagnostics/oncar-sweep-verify-2026-09-21.md` §mã không dùng). Hai lý do KHÁC nhau:
        //  • `vol`: âm lượng đã có núm cứng trên vô-lăng + thanh của chính Android; một ô STEP trong launcher là bề
        //    mặt thứ ba cho cùng một việc. Datum ĐỌC `media_vol` (*"Âm lượng giải trí"*) **Ở LẠI** — nó trả lời câu
        //    *"đang ở mức mấy"*, khác hẳn việc ĐỔI mức. Đừng gộp hai cái đó khi đọc nhật ký này.
        //  • `cast`: [ĐO grep 2026-09-21] đường GHI của nó (`AutoContainer.sendInfo`) **chưa bao giờ được nối** —
        //    `BydHalGateway.localSet` trả `false` cho `AutoContainer` kèm chú thích *"cast do SimpleCastRuntime sở
        //    hữu, KHÔNG wire ở đây"*. Tức nút này là nút CHẾT từ đầu. Việc chiếu cụm THẬT nằm trọn ở
        //    `SimpleCastRuntime`/`ClusterNavBridgeCast`/pref `cast_enabled` + nút nổi + nhóm Cài đặt › Chiếu màn
        //    lên cụm, và [ĐO] cả gói `modules/clustercast` có **0** tham chiếu tới `ControlRegistry`/
        //    `CapabilityCatalog`/`pick`/`kindOf` ⇒ xoá dòng này KHÔNG đụng tính năng chiếu cụm.
        //    ⇒ Mục `HIDDEN_FROM_PICKER["cast"]` của WP8 cũng gỡ theo (giữ một mục ẩn cho mã đã chết là ghim
        //    `HIDDEN_FROM_PICKER.size` vào một thứ hư — cùng lẽ đã ghi cho `hood` ở 1.85).
        // Cả hai rời **khối nút GỐC** (sau `hood` 1.85 và `seat_memory` 1.89) ⇒ khối đó nay còn **17**.
        // Ô/thanh nút của ai đã đặt chúng tự rụng khi nạp qua `WorkspaceState.sanitized()`.

        // ── MỞ RỘNG catalog §B (mặc định TẮT) ─────────────────────────────────────────────────────
        // Khí hậu
        // ═══ 1.85 · GIÓ TỰ ĐỘNG — [ĐO xe 2026-09-20 §3] route ĐÃ RE XONG, nút đổi cả nghĩa lẫn nhãn ═════════════
        //
        // Lịch sử ngắn: 1.69 nối được đường ĐỌC nhưng đường GHI đứng yên ở feature `1324355606` — một id **không có
        // trong `BYDAutoFeatureIds` của xe owner** (nên CAPTEST chấm X) trong khi owner xác nhận xe CÓ điều hoà auto.
        // Phiên on-car 2026-09-20 tìm ra đường thật: **`Ac.AC_CTRL_MODE_SET`** (số trên xe owner: 501219352), device
        // **`BYDAutoAcDevice`** — ⚠ chữ `c` THƯỜNG, `BYDAutoACDevice` là ClassNotFound. rc=0, thử cả hai chiều.
        // KHÔNG phải `AC_WIND_MODE_SET` (hướng gió) cũng KHÔNG phải `AC_WIND_LEVEL_SET=0` (bị xe bỏ qua).
        //
        // Bind theo **TÊN HẰNG** (R11) chứ không dán số 501219352: [ĐO nguồn fw-dl3] `BYDAutoFeatureIds` gán giá trị
        // trong `static {}` theo `isCanFD`/`isToyota`, nên cùng một tín hiệu mang số khác nhau tuỳ cấu hình xe. Tên
        // là thứ ổn định; số chỉ đúng cho một cấu hình. (Đo được 501219352 trên xe owner ⇒ nếu sweep sau này thấy
        // tên tra ra đúng số ấy thì hai bằng chứng khớp nhau.)
        //
        // ⚠⚠ **NHÃN ĐỔI: "Điều hòa AUTO" → "Gió tự động"** (owner review). [ĐO xe §4] xe **không có nhiệt-auto** —
        // thứ id này bật/tắt là **gió** auto. Giữ nhãn cũ là hứa một việc rộng hơn thứ nút làm, đúng họ lỗi đã cắn
        // dự án ba lần (*"Kính 50%"* · `lock`/`door` một byte · `hood` không tồn tại). **Mã `ac_auto` GIỮ NGUYÊN** —
        // nó là khoá lưu bền của ô/thanh nút người dùng đã đặt (xem KDoc `CapabilityCatalog.HIDDEN_FROM_PICKER`).
        // Cách nói *"điều hoà/máy lạnh"* vẫn trỏ nút này (`VoiceSynonyms`) vì đây là nút AC duy nhất bật/tắt được.
        //
        // `readKey` → `ac_wind_auto` (`getAcWindLevelManualSign`, chỉ báo gió auto) thay cho `ac_mode_auto`
        // (`getAcControlMode` = *chế độ điều hoà*, câu hỏi khác — datum ấy vẫn còn nguyên). Cả hai đều **0 = AUTO**
        // nên `readInverted` giữ nguyên. Giá trị GHI cũng ĐẢO — xem `HalBindingTable.writeArgs` ca `ac_auto`.
        ControlDef("ac_auto", "Gió tự động", "ic-fan", ControlKind.TOGGLE,
            domain = Domain.CLIMATE, tier = EvidenceTier.PROVEN,
            bindingKey = "BYDAutoFeatureIds.Ac.AC_CTRL_MODE_SET", halDevice = "BYDAutoAcDevice",
            readKey = "ac_wind_auto", readInverted = true,
            labelEn = "Auto fan", short = "Gió auto", shortEn = "Auto fan"),
        // U7 lượt 2 · sấy kính SAU nay có khung REAR riêng (kính hậu + biển số + sóng nhiệt); trước dùng
        // chung `ic-defrost` với sấy trước ⇒ hai ô cạnh nhau y hệt (nợ đã ghi ở kiểm kê §4).
        ControlDef("defrost_rear", "Sấy kính sau", "ic-car-rear-defrost", ControlKind.TOGGLE,
            domain = Domain.CLIMATE, tier = EvidenceTier.OVERDRIVE, bindingKey = "501219357",
            readKey = "defrost_rear_state",   // T2: `getAcDefrostState(2)`
            labelEn = "Rear defrost"),
        // ⚠ 1.90 · **`anion` (Ion âm) ĐÃ XOÁ** — owner chốt 2026-09-21: nút chưa verify được trên xe (sweep
        // 09-21 xếp nó vào nhóm "mã không dùng"). Datum ĐỌC `anion_state` **Ở LẠI** — nhóm `g_climate` đọc nó.
        ControlDef("pm25_clean_now", "Lọc ngay", "ic-filter", ControlKind.BUTTON,
            domain = Domain.CLIMATE, tier = EvidenceTier.PROVEN, bindingKey = "BYDAutoAcDevice.setQuickCleanAirState",
            labelEn = "Clean now"),
        // Thân xe — kính từng cửa (COVER)
        //
        // ⚠ [ĐO] `short`/`shortEn` ở bốn ô này KHÔNG phải trang trí: hàng nút của ô nhóm *Kính* chia 6 ô trên khung
        // 4/12 màn ⇒ mỗi ô 82px, và nhãn đầy bị cắt thành `"Kính trước-tr…"` / `"Window front-ri…"` ⇒ hai kính TRƯỚC
        // đọc ra y hệt nhau. Viết tắt theo ĐÚNG quy ước đã có ở bảng lốp (`tyre_p_fl` → `"Lốp TT"` / `"Tyre FL"`), để
        // người dùng chỉ phải học một bộ viết tắt cho cả xe.
        // ═══ 1.94 · KÍNH — NÚT TƯỜNG MINH (owner 2026-09-22) ══════════════════════════════════════════════════
        // Owner chốt: bỏ nút COVER 3-mức cycle (Đóng/Mở/Nửa) khó hiểu. Thay bằng nút TOGGLE rõ ràng:
        //  • 5 nút MỞ/ĐÓNG (mở hết ↔ đóng): 4 kính · lái · phụ · sau-trái · sau-phải
        //  • 5 nút 50% (mở 50% ↔ đóng): 4 kính · lái · phụ · sau-trái · sau-phải  — chạm lần 1 mở 50%, lần 2 đóng
        //  • 1 nút ĐÓNG TẤT CẢ (BUTTON một chiều, backup)
        // Mỗi nút = MỘT việc, không cycle. writeArgs: TOGGLE primary 1=Mở(hết/50% tuỳ nút)/0=Đóng.
        // ── 5 nút MỞ/ĐÓNG (mở hết) ──
        ControlDef("win_lf", "Kính lái", "ic-car-top-window-lf", ControlKind.TOGGLE, enabledByDefault = true,
            domain = Domain.BODY, tier = EvidenceTier.PROVEN, bindingKey = "BYDAutoBodyworkDevice.setBodyWindowCtrlState", readKey = "window_lf",
            labelEn = "Driver window", short = "Kính lái", shortEn = "Driver win"),
        ControlDef("win_rf", "Kính phụ", "ic-car-top-window-rf", ControlKind.TOGGLE,
            domain = Domain.BODY, tier = EvidenceTier.PROVEN, bindingKey = "BYDAutoBodyworkDevice.setBodyWindowCtrlState", readKey = "window_rf",
            labelEn = "Passenger window", short = "Kính phụ", shortEn = "Pass. win"),
        ControlDef("win_lr", "Kính sau trái", "ic-car-top-window-lr", ControlKind.TOGGLE,
            domain = Domain.BODY, tier = EvidenceTier.PROVEN, bindingKey = "BYDAutoBodyworkDevice.setBodyWindowCtrlState", readKey = "window_lr",
            labelEn = "Rear-left window", short = "Kính ST", shortEn = "Win RL"),
        ControlDef("win_rr", "Kính sau phải", "ic-car-top-window-rr", ControlKind.TOGGLE,
            domain = Domain.BODY, tier = EvidenceTier.PROVEN, bindingKey = "BYDAutoBodyworkDevice.setBodyWindowCtrlState", readKey = "window_rr",
            labelEn = "Rear-right window", short = "Kính SP", shortEn = "Win RR"),
        ControlDef("windows_all", "4 kính", "ic-car-top-window-all", ControlKind.TOGGLE,
            domain = Domain.BODY, tier = EvidenceTier.OVERDRIVE, bindingKey = "BYDAutoBodyworkDevice.setAllWindowState",
            labelEn = "All windows", short = "4 kính", shortEn = "All win"),
        // ── 5 nút 50% (chạm 1: mở 50% · chạm 2: đóng) ──
        ControlDef("win_half_lf", "50% kính lái", "ic-car-top-window-lf", ControlKind.TOGGLE,
            domain = Domain.BODY, tier = EvidenceTier.PROVEN, bindingKey = "BYDAutoBodyworkDevice.setBodyWindowCtrlState",
            labelEn = "50% driver window", short = "50% lái", shortEn = "50% drv"),
        ControlDef("win_half_rf", "50% kính phụ", "ic-car-top-window-rf", ControlKind.TOGGLE,
            domain = Domain.BODY, tier = EvidenceTier.PROVEN, bindingKey = "BYDAutoBodyworkDevice.setBodyWindowCtrlState",
            labelEn = "50% passenger window", short = "50% phụ", shortEn = "50% pass"),
        ControlDef("win_half_lr", "50% kính sau trái", "ic-car-top-window-lr", ControlKind.TOGGLE,
            domain = Domain.BODY, tier = EvidenceTier.PROVEN, bindingKey = "BYDAutoBodyworkDevice.setBodyWindowCtrlState",
            labelEn = "50% rear-left window", short = "50% ST", shortEn = "50% RL"),
        ControlDef("win_half_rr", "50% kính sau phải", "ic-car-top-window-rr", ControlKind.TOGGLE,
            domain = Domain.BODY, tier = EvidenceTier.PROVEN, bindingKey = "BYDAutoBodyworkDevice.setBodyWindowCtrlState",
            labelEn = "50% rear-right window", short = "50% SP", shortEn = "50% RR"),
        ControlDef("win_half_all", "50% 4 kính", "ic-car-top-window-all", ControlKind.TOGGLE,
            domain = Domain.BODY, tier = EvidenceTier.OVERDRIVE, bindingKey = "BYDAutoBodyworkDevice.setAllWindowState",
            labelEn = "50% all windows", short = "50% 4 kính", shortEn = "50% all"),
        // ── nút ĐÓNG TẤT CẢ (backup, một chiều) ──
        ControlDef("windows_close_all", "Đóng tất cả kính", "ic-car-top-window-all", ControlKind.BUTTON,
            domain = Domain.BODY, tier = EvidenceTier.OVERDRIVE, bindingKey = "BYDAutoBodyworkDevice.setAllWindowState",
            labelEn = "Close all windows", short = "Đóng cả cụm", shortEn = "Close all win"),
        ControlDef("sunshade", "Rèm che nắng", "ic-car-top-sunshade", ControlKind.COVER,
            domain = Domain.BODY, tier = EvidenceTier.OVERDRIVE, bindingKey = "1330642984", args = listOf("Đóng", "Mở", "Nửa"),
            labelEn = "Sunshade", argsEn = listOf("Close", "Open", "Half")),   // T7: mức 2 = 50% (đường percent)
        // ═══ 1.85 · KHOÁ TRẺ EM — [ĐO xe 2026-09-20 §3] RE xong CẢ HAI BÊN, nên nay là HAI nút ════════════════════
        //
        // Route: `Door.DOOR_LOCK_COMMAND_AREA_CHILDLOCK_LEFT_SET` (số trên xe owner 1276141584) và `…_RIGHT_SET`
        // (1276141586), device **`BYDAutoDoorLockDevice`**. rc=0, state đổi, owner xác nhận bằng cửa thật.
        // ⚠ Giá trị NGƯỢC trực giác: ghi **2 → BẬT** · ghi **1 → TẮT** — ở `HalBindingTable.writeArgs`.
        //
        // ⚠⚠ Vì sao HAI nút chứ không một nút "khoá cả hai": một `ControlDef` = **một** feature-id (`write` bắn đúng
        // một lệnh), nên "cả hai bên" phải là một **gói lệnh** (`ActionMacros`, cơ chế gộp-nhiều-lệnh đã có và đã
        // chạy — `mac_win_close_all` gộp 4 kính). Gói ấy CHƯA làm ở lượt này: thêm gói là thêm một khả năng mới vào
        // bộ chọn, và owner chưa duyệt ⇒ ghi vào handoff xin duyệt thay vì tự thêm.
        // ⚠ Nhãn nói rõ BÊN NÀO. Trước 1.85 nhãn là *"Khóa trẻ em"* trong khi nút chỉ bind cửa TRÁI — một nút hứa
        // cả xe mà khoá nửa xe là chỗ tệ nhất để hứa quá, vì người lái tin là con mình không mở được cửa nào.
        // Mã `child_lock` GIỮ NGUYÊN (khoá lưu bền của ô người dùng đã đặt); cách nói mơ hồ *"khoá trẻ em"* trỏ
        // về nút TRÁI, đúng tiền lệ owner đã duyệt ở 1.80 cho *"mở kính"* → kính LÁI (`VoiceSynonyms`).
        ControlDef("child_lock", "Khóa trẻ em trái", "ic-lock", ControlKind.TOGGLE,
            domain = Domain.BODY, tier = EvidenceTier.PROVEN,
            bindingKey = "BYDAutoFeatureIds.Door.DOOR_LOCK_COMMAND_AREA_CHILDLOCK_LEFT_SET",
            halDevice = "BYDAutoDoorLockDevice",
            labelEn = "Child lock left", short = "Khóa trẻ T", shortEn = "Child lock L"),
        ControlDef("child_lock_r", "Khóa trẻ em phải", "ic-lock", ControlKind.TOGGLE,
            domain = Domain.BODY, tier = EvidenceTier.PROVEN,
            bindingKey = "BYDAutoFeatureIds.Door.DOOR_LOCK_COMMAND_AREA_CHILDLOCK_RIGHT_SET",
            halDevice = "BYDAutoDoorLockDevice",
            labelEn = "Child lock right", short = "Khóa trẻ P", shortEn = "Child lock R"),
        // Đèn
        // ⚠ 1.90 · **`headlight_mode` (Chế độ đèn pha) ĐÃ XOÁ** — owner chốt 2026-09-21 (sweep 09-21: không dùng).
        // Nút `headl` (bật/tắt đèn pha, NEEDS_CAR) ở trên VẪN CÒN. Hệ quả: cặp feature-id trùng `headl` ↔
        // `headlight_mode` mà `CapabilityGroups.init` từng phải canh nay **tự hết** — chỉ còn một mã dùng id đó.
        // ⚠ 1.90 · **`powertrain_mode` (EV / HEV) ĐÃ XOÁ** — owner chốt 2026-09-21: **xe thuần điện**, không có
        // chế độ HEV để chọn. Nhánh `writeArgs` riêng của nó (EV→1 / HEV→3) gỡ theo.
        // Drive / năng lượng / sạc
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
        // ⚠⚠ 1.90 · **BỐN nút giải trí/cụm ĐÃ XOÁ HẲN** — owner chốt 2026-09-21 sau lượt sweep trên xe
        // (`docs/diagnostics/oncar-sweep-verify-2026-09-21.md`): `screen_rotation` (Xoay màn hình) ·
        // `camera_view` (Góc camera) · `cluster_music` (Nhạc trên cụm) · `brightness_gear` (Độ sáng màn).
        // Cả bốn chưa bao giờ verify được trên xe (enum không khớp nhãn / id nghi sai), và ba trong bốn có bề mặt
        // gốc của xe tốt hơn. `cam` (Camera 360, TOGGLE) **Ở LẠI** — nó là công tắc bật/tắt, khác việc chọn góc.
        // ⇒ Cặp feature-id trùng `brightness_gear` ↔ `hud_brightness` mà `ControlWriteArgsTest` phải khai miễn-trừ
        // nay **tự hết** (`hud_brightness` đã purge ở WP8, `brightness_gear` xoá hôm nay).
        // B10 (owner 2026-09-22): ghế mát/sưởi PHỤ — cùng setter ghế lái, chỉ khác seatID 2 (writeArgs). Control đã
        // test xe OK, chỉ wire UI. Đặt CUỐI danh sách để KHÔNG phá thứ tự khối nút gốc (ControlRegistryExtendedTest).
        // Không readKey (đường đọc ghế phụ chưa có datum — write-only; không bịa getter).
        ControlDef("seatc_r", "Mát ghế phụ", "ic-seat", ControlKind.SELECT,
            args = listOf("Tắt", "Mức 1", "Mức 2"), argsEn = listOf("Off", "Level 1", "Level 2"),
            domain = Domain.CLIMATE, tier = EvidenceTier.PROVEN, bindingKey = "BYDAutoSettingDevice.setSeatVentilatingState",
            labelEn = "Passenger seat ventilation"),
        ControlDef("seath_r", "Sưởi ghế phụ", "ic-seat", ControlKind.SELECT,
            args = listOf("Tắt", "Mức 1", "Mức 2"), argsEn = listOf("Off", "Level 1", "Level 2"),
            domain = Domain.CLIMATE, tier = EvidenceTier.PROVEN, bindingKey = "BYDAutoSettingDevice.setSeatHeatingState",
            labelEn = "Passenger seat heating"),
    )

    fun byId(id: String): ControlDef? = RegistryIndex.CONTROLS[id]   // [SOÁT P3] tra băm — xem KDoc RegistryIndex
    fun defaultEnabledIds(): List<String> = ALL.filter { it.enabledByDefault }.map { it.id }
    fun defaultDock(): DockConfig = DockConfig()

    /** Nút theo domain (để dựng panel Tuỳ biến §D2). */
    fun byDomain(domain: Domain): List<ControlDef> = ALL.filter { it.domain == domain }
}
