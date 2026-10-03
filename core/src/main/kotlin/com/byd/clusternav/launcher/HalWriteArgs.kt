package com.byd.clusternav.launcher

/**
 * ═══ BẢNG THAM SỐ **GHI** — tách khỏi `HalBindingTable.kt` ngày 2026-10-02 (FIX286 · SR-T1) ═══════════════════
 *
 * Tách vì trần 500 dòng (CLAUDE.md §4.1 — [ĐO `wc -l` 02/10] `HalBindingTable.kt` 497 dòng, mà lượt 2.86 phải thêm
 * cổng có-nóc + nhả 255 + nhật ký `ctl` vào đúng tầng ghi), và tách theo **VAI** như hai lần tách trước
 * (`HalRoutes.kt` 1.66 · `HalReadTables.kt` 2026-09-16): tệp kia là bộ **định tuyến có gateway trong tay** (nó thật
 * sự gọi xe), còn đây là **số đo chép từ stub HAL / từ xe** — mỗi nhánh một `file:line` hoặc một lần đo. Chúng đổi
 * khi đo lại một chiếc xe, không đổi khi sửa cách gọi HAL.
 *
 * ⚠ Không đổi chữ ký: `HalBindingTable.writeArgs` (companion) vẫn là đường gọi công khai và uỷ quyền xuống đây, nên
 * mọi chỗ gọi + bài kiểm cũ (`ControlWriteArgsTest`, `ControlReadKeyTest` …) còn nguyên. Hành vi đổi DUY NHẤT của
 * lượt tách là nhánh `sunroof` (1/2 → 100/0, SR1) — viết rõ tại nhánh đó.
 */
object HalWriteArgs {

    /** `MOONROOF_OPEN` — mở hết (stub `BYDAutoBodyworkDevice.java:338-344`; app OEM `SunRoofFragment.java:859`). */
    const val MOONROOF_OPEN = 100

    /** `MOONROOF_CLOSED` — đóng (stub cùng chỗ; app OEM `SunRoofFragment.java:835`). */
    const val MOONROOF_CLOSED = 0

    /**
     * Giá trị **nhả** = hằng `MOONROOF_INVALID` (255) — app OEM gửi nó 200 ms sau mỗi lệnh nóc (`SunRoofFragment.java:1109`,
     * huỷ lượt đang chờ trước mỗi lệnh mới bằng `removeMessages(0)`). Dòng registry `sunroof` khai nó qua
     * [ControlDef.release]; bắt buộc hay không: [SUY] (OEM luôn gửi), chưa đo trên xe.
     */
    const val MOONROOF_RELEASE = 255

    /**
     * Tham số cuối cho GHI named-method (proven, nhiều arg). Còn lại 1 arg = [primary].
     *  • ghế mát/sưởi `setSeatVentilatingState/HeatingState(seatId,state)` → [1(lái), state 2/1] (bật→mức1/tắt);
     *  • sưởi vô-lăng `setSteeringWheelHeatingState(state)` → [2/1];
     *  • kính từng cửa `setBodyWindowCtrlState(window,state)` → [index, mở=1/đóng=2] (enum WINDOW_*); tất cả kính → 4× state;
     *  • rèm che nắng feature 0x4F500028 (PERCENT_SET) → [mở=100/đóng=0]; đèn đọc 0x4F50003A → [ON=2/OFF=1];
     *  • cửa sổ trời `setMoonRoofState(%)` → [mở=100/đóng=0] (2.86 — trước đó 1/2, xem nhánh `sunroof`);
     *  • kính-nhị-phân "window" → cửa lái [1, state]; cốp `setHetchDoorStatus` → [open?1:close?2];
     *  • **khoá cửa `setDoorLockState(state)` → [khoá?2:mở?1]** (xem ⚠ dưới);
     *  • **mưa-tự-đóng-kính `setRainCloseWindow(state)` → [bật?1:tắt?2]**;
     *  • lọc-ngay/gập-gương (BUTTON) → [1].
     *
     * ## ⚠ [SOÁT P0] Vì sao khoá cửa PHẢI có nhánh riêng
     * [ĐO] 2026-09-11: `lock` ("Khoá xe") và `door` ("Mở cửa") khai **CÙNG** `bindingKey`
     * `BYDAutoDoorlockDevice.setDoorLockState`, và trước bản vá này **cả hai** rơi vào nhánh `else` ⇒ gửi
     * **ĐÚNG CÙNG một byte** cho cùng `primary`. Hai nhãn nghĩa ĐỐI NGHỊCH mà gửi byte y hệt ⇒ ít nhất một
     * cái sai, **chứng minh được không cần xe**. Hệ quả nặng nhất: gói `mac_leave` ("Rời xe") kết bằng
     * `MacroStep("lock", 1)` = đúng byte của `MacroStep("door", 1)` trong `mac_door_light` ⇒ bấm "Rời xe" thì
     * kính đóng, đèn tắt, xe **KHÔNG khoá** — mà kết quả vẫn báo thành công (rc=0) và ô còn sáng như đã khoá.
     *
     * Giá trị lấy từ tài liệu dự án, KHÔNG tự nghĩ ra: `docs/diagnostics/launcher-hal-re-overdrive-2026-09-08.md`
     * §7 (*"state_locked=\"2\", unlocked=\"1\""*) + `docs/diagnostics/kachi-capability-catalog-2026-09-10.md` §196
     * (*"locked=2/unlocked=1"*). Mưa-tự-đóng lấy từ `bodywork-window-trunk-RE-2026-09-06.md` §14
     * (*"setRainCloseWindow(int) (ON=1/OFF=2)"*) — trước bản vá này TẮT gửi `0`, một giá trị không có trong
     * tài liệu nên gần như chắc chắn bị xe bỏ qua.
     *
     * ⚠ Cả hai mã vẫn ở mức **chưa kiểm trên xe** (`OVERDRIVE`/`NEEDS_CAR`): bản vá này sửa chỗ **tự mâu
     * thuẫn nội bộ** (hai nhãn đối nghịch, một byte), KHÔNG hứa rằng xe sẽ nhận lệnh.
     *
     * THUẦN (không đọc gateway) ⇒ kiểm được off-car; [ControlWriteArgsTest] khoá cả họ "hai mã một lệnh".
     */
    fun writeArgs(def: ControlDef, primary: Int): IntArray = when (def.id) {
        "seatc", "seath" -> intArrayOf(1, ControlLevels.rawForLevel(def.id, primary) ?: 1)
        // B10: ghế PHỤ = seatID 2, cùng setter + cùng thang mức (ControlLevels khai seatc_r/seath_r).
        "seatc_r", "seath_r" -> intArrayOf(2, ControlLevels.rawForLevel(def.id, primary) ?: 1)
        // [ĐO xe 2026-09-15] kính MỞ được, ĐÓNG không. Gốc: state cũ = COVER primary (Đóng=0/Mở=1) — Mở gửi
        // 1 (= WINDOW_OPEN_FULL, chạy), Đóng gửi 0 (= WINDOW_ENABLE/INVALID, KHÔNG phải đóng ⇒ no-op). Enum
        // đúng của BYDAutoBodyworkDevice: WINDOW_OPEN_FULL=1 · WINDOW_CLOSE=2 · WINDOW_STOP=3 (jadx-tmap
        // BYDAutoBodyworkDevice.java:367-381, DL3). ⇒ ánh xạ COVER: Mở(primary>0)→1, Đóng→2.
        // T7 (owner 2026-09-15 "mở 50%"): mức 2 = WINDOW_OPEN_HALF=4 — enum THẬT cùng bảng CLOSE=2/OPEN_FULL=1 đã
        // đo đúng cả 4 kính (jadx-tmap BYDAutoBodyworkDevice.java:378). 0/1 giữ nguyên. NEEDS-ONCAR (1 lệnh):
        // `hal set setBodyWindowCtrlState 1,4` rồi `getWindowOpenPercent(1)` ≈ 50. `windows_all` mức 2 (Nửa) nay
        // gửi `setAllWindowState(4,4,4,4)` — CÙNG enum WINDOW_OPEN_HALF=4 đã đo per-window; ca 4-kính-nửa CHƯA đo
        // trên xe (AWAITING_CAR) nhưng enum đã proven ⇒ làm được, câu trả lời mang nhãn "chưa kiểm trên xe".
        // ── 1.94 · KÍNH TƯỜNG MINH (owner 2026-09-22) — nút TOGGLE, không còn COVER 3-mức ──
        // enum BYDAutoBodyworkDevice: WINDOW_OPEN_FULL=1 · WINDOW_CLOSE=2 · WINDOW_OPEN_HALF=4 (jadx-tmap :367-381).
        // Full: primary 1 → mở HẾT(1) · 0 → đóng(2).   Half: primary 1 → mở 50%(4) · 0 → đóng(2).
        "win_lf" -> intArrayOf(1, if (primary > 0) 1 else 2)
        "win_rf" -> intArrayOf(2, if (primary > 0) 1 else 2)
        "win_lr" -> intArrayOf(3, if (primary > 0) 1 else 2)
        "win_rr" -> intArrayOf(4, if (primary > 0) 1 else 2)
        "win_half_lf" -> intArrayOf(1, if (primary > 0) 4 else 2)
        "win_half_rf" -> intArrayOf(2, if (primary > 0) 4 else 2)
        "win_half_lr" -> intArrayOf(3, if (primary > 0) 4 else 2)
        "win_half_rr" -> intArrayOf(4, if (primary > 0) 4 else 2)
        // Tất cả kính: full = 4× state (mở=1/đóng=2). 50% = 4× WINDOW_OPEN_HALF=4 (ca 4-kính-nửa AWAITING_CAR,
        // enum đã proven per-window). Nút Đóng-tất-cả (BUTTON) luôn gửi 2 (WINDOW_CLOSE) cho cả 4 — backup an toàn.
        "windows_all" -> (if (primary > 0) 1 else 2).let { intArrayOf(it, it, it, it) }
        "win_half_all" -> (if (primary > 0) 4 else 2).let { intArrayOf(it, it, it, it) }
        "windows_close_all" -> intArrayOf(2, 2, 2, 2)
        // [ĐO] RE 2026-09-14 §1/§5a: `setAcTemperature(type, value, tempSource, unit)` — lái=0, value=°C thô,
        // tempSource=0, unit=1 (Celsius). Vd 22°C → setAcTemperature(0,22,0,1). Thay `setTemprature` (không tồn tại).
        "temp" -> intArrayOf(0, primary, 0, 1)
        // [ĐO xe 2026-09-17] cốp = `voiceCtlBackDoor(cmd)` ở Setting device: MỞ=1 · ĐÓNG=3 (đo 2 lần mỗi
        // lệnh). Trước 1.70 gửi 1/2 cho `setHetchDoorStatus` (method KHÔNG tồn tại) ⇒ no-op. cmd 2 = dừng
        // giữa hành trình ([ĐOÁN], chưa thử lúc cốp chạy) — không dùng cho TOGGLE mở/đóng.
        "trunk" -> intArrayOf(if (primary > 0) 1 else 3)
        // [ĐO xe 2026-09-15] rèm "bấm mở CHÚT XÍU". Gốc: feature 1330642984 = 0x4F500028
        // BODYWORK_SUNSHADE_PANEL_PERCENT_SET — nhận PHẦN TRĂM 0..100, không phải 0/1. Gửi 1 = "mở 1%".
        // ⇒ Mở=100%, Đóng=0% (carsettings Body.java:1653 · WINDOW_OPEN_PERCENT_MAX=100).
        // T7: rèm đi đường PERCENT (0..100) nên mức 2 = 50 thẳng, không cần enum.
        "sunshade" -> intArrayOf(when (primary) { 2 -> 50; else -> if (primary > 0) 100 else 0 })
        // [ĐO xe 2026-09-15] đèn đọc on/off tay không ăn (chế-độ-theo-cửa thì ăn — feature KHÁC 0x4F500038).
        // feature 1330643002 = 0x4F50003A SET_INSIDE_LIGHT_STATE_SET, enum INSIGHT_LIGHT_OFF=1 · ON=2
        // (jadx-tmap BYDAutoSettingDevice.java:218-219, DL3). Cũ gửi 0/1 ⇒ không trúng ON=2. ⇒ ON=2, OFF=1.
        "readl" -> intArrayOf(if (primary > 0) 2 else 1)
        "pm25_clean_now" -> intArrayOf(1)
        // ── Bản vá binding 2026-09-15 (`docs/diagnostics/hal-binding-remediation-2026-09-15.md`) — enum lấy từ stub
        // `../jadx-tmap/sources/android/hardware/bydauto/`, KHÔNG phải 0/1:
        // đèn ban ngày `setDayTimeLightState` — DAYTIME_LIGHT_OPEN=1 / CLOSE=2 (BYDAutoLightDevice.java:10/:8).
        "drl" -> intArrayOf(if (primary > 0) 1 else 2)
        // ⚠ 1.90 · nhánh `powertrain_mode` (EV→1 / HEV→3) gỡ cùng nút — owner 2026-09-21, xe thuần điện.
        // ═══ 2.86 · FIX286-SUNROOF (R-SR SR1) — cửa sổ trời `setMoonRoofState` nhận **PHẦN TRĂM**, không phải 1/2 ═══
        // [ĐO nguồn OEM, đọc lại 02/10] app Cài đặt BYD (`com.byd.vehiclesettings`) gửi Đóng `setSunRoofState(0)`
        // (`SunRoofFragment.java:835`) · Mở `(100)` (`:859`) → `SunRoofModel.java:127` `setMoonRoofState(i)`; stub
        // `jadx-tmap/.../BYDAutoBodyworkDevice.java:338-344` `MOONROOF_CLOSED=0 · OPEN=100 · MIN=21 · STOP=254 ·
        // INVALID=255`. Từ 1.63 (`ad33a52`) tới 2.85 nhánh này gửi **1 / 2** — nhãn cũ "[ĐO] OpenBYD
        // CarControlImpl.java:1503-1505 mở=1/đóng=2" là **[SUY] bị ghi thành [ĐO]**: OpenBYD chỉ chuyển tiếp nguyên
        // giá trị, enum `ko1` OPEN=1/CLOSE=2 là enum KÍNH chung. Cùng họ lỗi rèm *"bấm mở chút xíu"* đã [ĐO xe
        // 2026-09-15] và sửa sang 100/0 ngay trên ([`sunshade`]). Lệnh **nhả 255 sau 200 ms** không nằm ở đây mà là
        // DỮ LIỆU của dòng registry ([ControlDef.release]) — `HalBindingTable.write` thi hành.
        // ⚠ Xe có nhận 100/0 hay không: **[CHƯA BIẾT]** — owner duyệt OTA cho anh em có nóc mở thử (OC-SR1),
        // nhật ký `ctl` (SR6) ghi rc thô + đọc trước/sau để lượt thử ấy sinh ra bằng chứng.
        "sunroof" -> intArrayOf(if (primary > 0) MOONROOF_OPEN else MOONROOF_CLOSED)
        // sạc không dây `setWirelessChargingSwitchState` — CHARGE_WIRELESS_CHARGING_ON=1 / OFF=2 (:61/:60).
        "wireless_charge" -> intArrayOf(if (primary > 0) 1 else 2)
        // camera 360 `setAVMSwitchState` — AVM_FUNCTION_ON=2 / OFF=1 (BYDAutoADASDevice.java:35/:34).
        "cam" -> intArrayOf(if (primary > 0) 2 else 1)
        // ═══ 1.85 · HAI BẪY GIÁ TRỊ **NGƯỢC**, cả hai [ĐO trên xe 2026-09-20 §3] ═════════════════════════════
        //
        // (a) **gió tự động** `AC_CTRL_MODE_SET`: [ĐO] **0 → AUTO** · **1 → chỉnh tay** (rc=0, thử cả hai chiều,
        //     owner xác nhận màn AC đổi theo). Nút là TOGGLE nên `primary` 1 = *"bật gió auto"* ⇒ phải gửi **0**.
        //     Không có nhánh này thì bật/tắt chạy **ngược hoàn toàn** mà rc vẫn 0 — im lặng, đúng loại lỗi chỉ
        //     người ngồi trong xe phát hiện được. (`AC_CTRLMODE_AUTO=0`/`_MANUAL=1` ở `ac/BYDAutoAcDevice.java:20-21`
        //     khớp con số đo được.)
        "ac_auto" -> intArrayOf(if (primary > 0) 0 else 1)
        // (b) **khoá trẻ em** `DOOR_LOCK_COMMAND_AREA_CHILDLOCK_{LEFT,RIGHT}_SET`: [ĐO] ghi **2 → BẬT** (state
        //     đọc về 1) · ghi **1 → TẮT** (state 2) — owner xác nhận bằng cửa thật. Tức giá trị GHI và state
        //     ĐỌC ngược nhau; ở đây chỉ lo vế GHI (bật→2). Cùng hình dạng `OFF=1/ON=2` của `lock`/`steer_heat`,
        //     nhưng viết riêng để con số đo được có chỗ neo kèm bằng chứng thay vì lẫn vào nhánh `else`.
        "child_lock", "child_lock_r" -> intArrayOf(if (primary > 0) 2 else 1)
        // NEEDS-ONCAR: `camera_view` `setDisplayMode` — gửi index thô, map nhãn↔DISPLAY_MODE_* chưa chốt.
        else -> intArrayOf(primary)
    }
}
