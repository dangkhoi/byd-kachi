# fw-2606 — rà lại MỌI bế tắc do firmware: lên 2606 có cửa mới không?

> **Trạng thái**: Current · **Cập nhật**: 2026-10-08 · **Loại**: Diagnostics (nghiên cứu off-car, CHỈ ĐỌC — không chạm xe, không máy ảo, không chạm mã sản phẩm) · **Owner**: dangkhoi
> **Câu hỏi owner 08/10**: *"xét lại, những gì đã gọi là bế tắc do firmware, thì lên 2606 có cửa không?"*
> **Bản MỚI**: `13.1.33.2606100.1` build `eng.build.20260610.082733` (SX361, MCU `18.3.5.2606100.2`, car.type 138, Android 10/SDK 29) — `../firmware/fw-2606/`
> **Bản CŨ (mốc so)**: `13.1.33.2602030.1` (SX326, build 2026-02-04) — `../firmware/fw-2602-diff/new_out/system.img` = cùng fingerprint xe owner trước 07/10.
> **Mức bằng chứng** (CLAUDE.md §2, §16): `[ĐO nguồn]` đọc firmware thật (decompile/disasm/qml) · `[ĐO xe]` đo trên xe · `[SUY]` · `[ĐOÁN]` · `[CHƯA BIẾT]`. Không chép nguyên văn mã; chỉ diễn đạt + tên hằng/id/địa chỉ.
> **Kế thừa**: `fw-2606/RE-2606-findings.md` (RE gốc 07/10) + bộ decompile trong scratchpad phiên này (`jx/fw_{old,new}`, `jx/svc_{old,new}` — framework.jar + services.jar hai bản, chuẩn hoá bỏ số/hex/R.* rồi so).

---

## 0. Kết luận ba dòng

1. **Đa số bế tắc do firmware GIỮ NGUYÊN trên 2606** [ĐO nguồn]: toàn bộ lớp Window/Activity của `services.jar` (ActivityRecord, ActivityStackSupervisor, ActivityDisplay, TaskRecord, DisplayContent, ActivityStarter, DisplayWindowSettings) **giống từng byte** sau khi chuẩn hoá ⇒ BEHIND-HOME NPE, uiMode-no-relaunch, Waze-vào-ô, am-stack/overscan/resize, PIP đều y như 2602. Các thiết bị HAL khoá/cốp/gạt mưa/360 **không có setter mới**.
2. **MỘT cửa mới thật sự, cùng hướng owner đang theo**: 2606 thêm cả một họ **ISA bản đồ** (`SETTING_ISA_MAP_*` + `setIsaMap*` của `BYDAutoSettingDevice`) và **đèn cụm "dự báo giới hạn tốc độ"** mới ⇒ đây là cửa khả dĩ cho *"đẩy giới hạn tốc độ của app nav lên biển gốc/cụm"*. Đã có runbook on-car CHỈ-ĐỌC (`oncar-runbook-2.96.md` §B). Cần đo trên xe mới chốt.
3. **Vài thay đổi nhỏ "gần Kachi" nhưng KHÔNG mở cho Kachi**: cổng chạy-khi-nguội (`relatestart`) thêm `com.android.launcher3` vào danh sách miễn, thêm `com.byd.muslim` vào whitelist tự khởi động nội bộ — **không có `com.byd.launcher`** ⇒ cơ chế chặn với Kachi y nguyên. DiCarServer 3.4.0 có thêm phía server HUD nhưng **không** mở nav cho W-HUD.

**Đếm verdict** (19 mục, xem bảng §3): UNCHANGED 12 · CHANGED-STILL-BLOCKED 3 · NEW DOOR 2 (1 chính + 1 phụ) · CAN'T TELL 2.

---

## 1. Cách so (tái lập 10 phút cho OTA sau)

- `debugfs` (`/opt/homebrew/opt/e2fsprogs/sbin/debugfs`) `dump`/`rdump` từ `fw-2602-diff/new_out/system.img` cho bản CŨ; bản MỚI đã kéo sẵn ở `fw-2606/_system_framework`, `_system_priv-app`, `_libs`.
- `jadx` (`../tools/jadx/bin/jadx`, JAVA_HOME `/opt/homebrew/opt/openjdk@17`) ra `framework.jar` + `services.jar` hai bản.
- So lớp Java: chuẩn hoá bỏ số ≥5 chữ số / hex / tên `R.*` / chú thích JADX (vì `framework-res` đổi id tài nguyên gây hàng trăm "đổi" giả) rồi `cmp`.
- `.so`: `nm -D -C` + so chuỗi + so symbol.
- Xác nhận nền tảng: `ro.build.version.sdk=29`, không có `com.android.settings` (202 gói), `ro.boot.selinux=permissive` ở getprop bản kéo (xe thật enforcing — getprop này của bản eng), `ro.debuggable=0`, `ro.secure=1`.

### 1.1 Lớp WM/AM nào GIỐNG HỆT (nền cho mọi verdict UNCHANGED)

`services.jar` chuẩn hoá chỉ **13 lớp đổi**: ConnectivityService, ElectricAirConditioningService, AppOpsDataCachedService, ShortcutRequestPinProcessor, PowerManagerService, InputMethodManagerService, NetworkStatsService, **ActivityManagerService**, BroadcastQueue, **AccModeManagerService**, AdbService, StreamFocusControl, GnssLogManager. **Không** lớp nào trong `server/wm/*` đổi ⇒ mọi bế tắc nằm ở tầng Activity/Window/Display là UNCHANGED theo nghĩa chặt (giống byte).

---

## 2. Thay đổi 2602 → 2606 liên quan trực tiếp tới các bế tắc

| Thành phần | Đổi gì (2606) | Liên quan bế tắc | Mức |
|---|---|---|---|
| `BYDAutoFeatureIds` | +~80 id: họ **ADAS_* map-ahead** (giao lộ/vòng xuyến/đèn/biển dừng-nhường/độ cong/map-match), **8 `SETTING_ISA_MAP_*`** (giới hạn tốc độ theo bản đồ: thường/mưa/tuyết/giờ + loại + 3 khoảng cách), **`INSTRUMENT_INPUT_KEYCODE_STATUS`** (mới, device INSTRUMENT), trip B travel-time, dây an toàn hàng 2 HQ, đèn phanh, sạc, panorama calibration. **Không** id `*HUD*`/naviType/instrument-theme mới | §3 mục C (ISA = cửa) + mục R (keycode) | [ĐO nguồn] |
| `BYDAutoSettingDevice` | +`setIsaMapSpeedLimit/RainSpeedLimit/SnowSpeedLimit/TimeSpeedLimit/SpeedLimitType` (`(I)I`, quyền `BYDAUTO_SETTING_SET`, device 1023 = Setting mà Kachi với tới được) | Cửa "giới hạn tốc độ lên cụm" | [ĐO nguồn] |
| `BYDAutoDeviceFeaturesMap` | `SETTING_ISA_MAP_*` → device **SETTING (1023)**; `INSTRUMENT_INPUT_KEYCODE_STATUS` → **INSTRUMENT (1007)**; `TRIP_A_TRAVEL_TIME_MINUTE` → **STATISTIC** | ISA/keycode phân giải đúng device ⇒ gọi được từ app | [ĐO nguồn] |
| `BYDAutoDoorLockDevice`, `BYDAutoBodyworkDevice`, `BYDAutoWiperDevice`, `BYDAutoPanoramaDevice` | Setter **không đổi** (panorama chỉ thêm 3 id calibration, **không** thêm setter hiển thị) | khoá/cốp/gạt mưa/360 vẫn như cũ | [ĐO nguồn] |
| `ActivityManagerService.isCallerAppGranted` | Thêm `com.android.launcher3` (cũ chỉ `com.byd.media.autoplay`) — cổng cho "service vào app nguội" | `com.byd.launcher` KHÔNG có ⇒ Kachi vẫn bị chặn | [ĐO nguồn] |
| `AppOpsDataCachedService` | Whitelist tự-khởi-động nội bộ thêm `com.byd.muslim` (cũ: `tunein.player`, `com.byd.tunein`). Danh sách thật lấy từ `StrategyManager("permission", AutoStartWhite/Black)` + file; cờ mỗi-uid ghi qua provider `appops` (quyền `ACCESS_APPOPSDATA` **protectionLevel signature**, do `android.uid.shell` giữ) | Gốc cổng relatestart; Kachi không trong whitelist | [ĐO nguồn] |
| `AccModeManagerService` | Chỉ **thêm** toast "chưa về P" (`POWER_MCU_NOTIFY_SOC_B_POPUP_REMINDER`); logic quickboot-kill-mọi-app **không đổi** | "BYD giết Kachi lúc tắt máy" y nguyên | [ĐO nguồn] |
| `DiCarServer` 2.1.0→**3.4.0** | Thêm phía server `ICarHudService`/`ICarCmsService`/`ICarOmsService` (`com.byd.car.feature.vision`); `HudConfigModel` W-HUD mặc định **không** có NAVIGATION_MAP | Nav-HUD vẫn blocked (§3 mục B) | [ĐO nguồn] |
| `CarSetting` 212→264 | Bảng hằng DiCar SDK thêm nhiều id HUD/nav (`INSTRUMENT_NAVI_GUIDANCE_INFO_SET`, `INSTRUMENT_HUD_NAVIGATION_ARROW_STYLE_SET`, `INSTRUMENT_THEME_MODE_SET`…) — **chỉ là hằng**, không có field framework tương ứng ⇒ DiLink3 không phân giải | Không cửa | [ĐO nguồn] |
| QML theme1/theme2 (`.rcc`) | Theme2 (Seal/chữ nhật) **giống từng ký tự**; chỉ thêm đèn cảnh báo ADAS mới + đèn "dự báo giới hạn tốc độ" | ADAS-thu-nhỏ + km/h vẫn blocked | [ĐO nguồn] |
| `libBydDataSource.so` | Bảng opcode AutoContainer 212 mục **giống từng mục**; chỉ đánh số lại data-item (466→475…) + thêm `updateSpeedLimitValue` | Chiếu/theme cụm không đổi | [ĐO disasm] |

---

## 3. Bảng rà từng bế tắc

Ký hiệu verdict: **UNCHANGED** (cùng mã — không cửa) · **CHANGED-STILL-BLOCKED** (có đổi quanh đó nhưng vẫn chặn) · **NEW DOOR** (có đòn bẩy mới) · **CAN'T TELL** (thiếu phân vùng/tệp để kết luận).

| # | Mục | Bế tắc cũ (cơ chế + nguồn) | 2606 | Cửa mới? | Bước kế |
|---|---|---|---|---|---|
| A | **Thu nhỏ khung ADAS ở Chữ nhật (theme2 FULL)** | Nền ADAS trắng hiện khi `adasInterfaceDisplay !== 0`; chỉ lib ghi từ CAN trang cụm, máy ON luôn 1/2, không bao giờ 0; không opcode/HAL/broadcast nào đặt 0 (`cluster-rect-adas-shrink-2026-10-06.md`, `kachi-cluster-theme-facts-1005`) | **UNCHANGED** [ĐO nguồn]: QML theme2 giống từng ký tự; 3 chỗ ghi `adasInterfaceDisplay` (nay item 475) y hệt; bảng opcode giống | **Không** từ Android | Chỉ mở khi (a) firmware đổi theme2 sang `=== 1` (kiểm mỗi OTA, §1) hoặc (b) root vá rcc. Dùng Bo tròn (theme1) + phím menu như hiện tại |
| B | **Nav lên W-HUD kính lái zin** | HUD zin = gate firmware/ECU, không phải app (ADR 0002); `0x38B00030` chưa provision bị coi là cổng | **CHANGED-STILL-BLOCKED** [ĐO nguồn]: DiCarServer 3.4.0 có `CarHudServiceImpl`; `HudConfigModel` cho W-HUD mặc định = W_HUD\|chỉnh cao\|sáng\|góc\|theme\|DRIVING_FUSION\|DYNAMIC_NAVIGATION — **không** NAVIGATION_MAP/FUSION. `0x38B00030` là tính năng **AR-HUD**, không thuộc W-HUD ⇒ "chưa provision" là bình thường, **không phải** cổng của HUD owner | **Không** API/opcode mới; cổng thật là **coding biến thể xe/HUD ECU** cho W-HUD | Off-car: xong. On-car CHỈ ĐỌC: xác nhận `0x38B00015=1` (W-HUD) + `0x38B00028=1` qua cầu kiểm thử (RE §4 G5). Mở khoá: coding tool chính hãng (đổi đích thợ: "bật dẫn đường W-HUD, so cấu hình SL6 40d=162") — quyết định owner |
| C | **Biển/giới hạn tốc độ gốc lên cụm** (thay overlay Kachi tự vẽ) | `STATISTICS_ISA_CURRENT_ROAD_SPEED_LIMIT_SET` + 33 id ghi `rc=0` mà cụm không đổi; Kachi chỉ ghi được giá trị 0 (runbook 2.96 §B) | **NEW DOOR** [ĐO nguồn]: 2606 thêm **8 `SETTING_ISA_MAP_*`** + **`setIsaMap*`** (`BYDAutoSettingDevice`, phân giải về device SETTING 1023, quyền `BYDAUTO_SETTING_SET` mà Kachi với tới) **và** đèn cụm mới "dự báo giới hạn tốc độ" (`speed_limit_forecast.png`, hiện khi khung CAN `0x237` online, giá trị `0x2370002E`) | **CÓ** — đòn bẩy: ghi `setIsaMapSpeedLimit(...)` / id `SETTING_ISA_MAP_SPEED_LIMIT_VALUE…SET`. Vì sao có thể chạy: id resolvable + named method có thật + có đèn cụm tiêu thụ. Rủi ro: ghi tới ECU ADAS | On-car CHỈ ĐỌC trước (runbook 2.96 §B1/B2: `isa-speedlimit-2606.sh PHASE=read|watch`, đọc tên hằng + xem `0x237` có online khi xe tự có dự báo). Ghi = PHASE=write chỉ khi đỗ/P/owner đồng ý (state-change ⇒ quyết định owner). Nếu W1/W3 làm cụm/HUD hiện số ⇒ spec 2.97 `ISA-NATIVE-SIGN` |
| D | **theme2 FULL mất km/h (QML OEM)** | `naviSpeedId` không có x/y ⇒ dồn (0,0); chữ "km/h" lạc nằm trên nền đục z≥0 không che được; chỉ ẩn khi speed===-1 (app không đặt được) — cả 2506/2511/2602 (`kachi-cluster-theme-facts-1005`) | **UNCHANGED** [ĐO nguồn]: theme2 QML giống từng byte | **Không** | Giữ kết luận: chữ nhật trọn ngang + km/h Kachi tự vẽ (chữ "m/h" lạc chấp nhận), hoặc root vá rcc |
| E | **Cổng cold-start chặn service/broadcast/provider vào app nguội (`relatestart`)** | Mọi app cài/cập nhật bị gắn cờ; khi nguội, start/bind service · broadcast manifest · provider call từ uid khác bị bỏ im (`ssc_skip … ignored`); **Activity start thì qua** (`kachi-byd-firmware-facts-1003`) | **CHANGED-STILL-BLOCKED** [ĐO nguồn]: cơ chế y nguyên (`isCallerAppGranted`+`relatestart`+`isTargetAppEnabledStartedBy3rd`). 2606 chỉ thêm `com.android.launcher3` vào `isCallerAppGranted` và `com.byd.muslim` vào whitelist nội bộ — **không có `com.byd.launcher`** | **Không** cho Kachi. Lộ thêm: whitelist tự-khởi-động là data (`StrategyManager "permission"` + file `/system/etc` + provider `appops`, quyền ghi là signature do `uid.shell`) | Giữ thiết kế: đánh thức app bên thứ ba chỉ bằng **Activity start** (đã làm 2.87+). Việc gỡ cờ cold-start cho một uid qua provider `appops` là lệnh ĐỔI STATE hệ thống phạm vi uid khác ⇒ KHÔNG đề xuất (CLAUDE.md §4/§5); nếu muốn đo đọc trạng thái cờ: on-car đọc provider (owner quyết) |
| F | **BYD giết Kachi mỗi lần ACC_OFF (không PACKAGE_RESTARTED)** | `AccModeManagerService` quickboot xoá mọi task + `killApplicationEx("quickboot")` mọi gói ngoài whitelist ngắn (`kachi-car-facts-0929`, `kachi-byd-firmware-facts-1003`) | **UNCHANGED** [ĐO nguồn]: AccModeManager chỉ **thêm** toast "chưa về P"; logic giết không đổi | **Không** | Đã bù bằng HomeGuard + rebind (2.96). Không có đường firmware để Kachi khỏi bị giết |
| G | **BEHIND-HOME: `ActivityDisplay.hasSplitScreenPrimaryStack()` NPE** | anchor display 0 + `avoidMoveToFront` ⇒ `ActivityStackSupervisor.handleNonResizableTaskIfNeeded` deref display null (nhánh `mDoResume=false`) [ĐO xe 05/10] (`kachi-car-facts-1005-behind`) | **UNCHANGED** [ĐO nguồn]: ActivityDisplay + ActivityStackSupervisor + ActivityStarter giống từng byte | **Không** | Đã né bằng "ô 7" (StagingDisplay, giữ app trong màn ảo của chính nó). Giữ |
| H | **Đổi Sáng/Tối không relaunch Activity (whitelist)** | `ActivityRecord.shouldRelaunchLocked` OR cờ UI_MODE vào tự-xử-lý cho mọi gói ngoài `isBydUiModeWhiteListApp` ⇒ app không khai `configChanges` không được gọi lại (`kachi-byd-no-relaunch-uimode`) | **UNCHANGED** mã [ĐO nguồn]: ActivityRecord giống byte; whitelist đọc từ `/system/etc/bydUiModeChangeWhitelist.properties` | **ĐÃ GIẢI phía app** (2.96 `configChanges=uiMode`) ⇒ không cần firmware. Nội dung file whitelist: **CAN'T TELL** (file `/system/etc` chưa kéo) | Đã xong ở 2.96. Nếu muốn biết file có liệt Kachi không: `adb pull /system/etc/bydUiModeChangeWhitelist.properties` (chỉ-đọc) — không bắt buộc |
| I | **Khoá cửa / cốp không có setter riêng** | `BYDAutoDoorLockDevice` không setter nào (`setDoorLockState` NoSuchMethod); `BYDAutoBodyworkDevice` không `setHetchDoorStatus`; chỉ generic `set(int[],EventValue)` (`kachi-car-hal-facts-0916`, `oncar-master/1-hal` B5) | **UNCHANGED** [ĐO nguồn]: setter hai device giống hệt; `setHetchDoorStatus`/`BackDoor` vẫn 0 kết quả trong BodyworkDevice | **Không** | Giữ đường generic `set()` + `voiceCtlBackDoor` cho cốp (đã đo 1=mở 3=đóng). Cửa chính: `DOOR_LOCK_COMMAND_AREA_*` generic trả NOT_PROVISIONED trên trim — giữ NEEDS_CAR |
| J | **Gạt mưa không poll được "đang gạt"** | `getWindscreenWiperRelayState`=0 suốt lúc gạt; `getWindscreenWiperResetState(1)`=-10011 ổn định; chỉ còn đường listener (`kachi-car-facts-0917`, `oncar-master/1-hal`) | **UNCHANGED** [ĐO nguồn]: `BYDAutoWiperDevice` setter/getter giống hệt | **Không** | Giữ tín hiệu mưa qua `SETTING_FRONT_RAIN_WIPER_SPEED` (1 khô/≥2 mưa) như automation 1.85 |
| K | **Khoá trẻ em chỉ bên TRÁI** | Thực chất không phải firmware chặn: `CHILDLOCK_LEFT_SET`+`RIGHT_SET` đều có; là luật app "1 ControlDef = 1 feature-id" + cụm mơ hồ → nút trái (`oncar-1.84-session`) | **UNCHANGED (không phải chặn firmware)** [ĐO nguồn]: cả hai id còn nguyên, cùng device DOOR_LOCK | **Không liên quan firmware** | Nếu owner muốn "cả hai bên": gói lệnh 2 nút ở app — việc off-car, không chờ firmware |
| L | **5 datum "không có hằng" (drift, 2 màu viền IAL, 2 BSD)** | Zero-hoá trong **stub jadx-tmap** (khác build); drift = UNAVAILABLE-TRIM (Seal/SL6 DM-i không drift); IAL/BSD route sai device (`hal-binding-remediation`, `oncar-master/1-hal`) | **UNCHANGED (không phải firmware mới chặn)** [ĐO nguồn]: id có thật trong `BYDAutoFeatureIds` fw thật cả hai bản; drift là trim | **Không** (owner đã FEATURE-FILTER gỡ sạc/drift/gập gương/MCU khỏi bộ chọn 2026-09-17) | Đã đóng theo quyết định owner. Không mở lại |
| M | **AVM/camera 360 đóng** | `pano_sdk.txt` cấm (uid shell); không `panorama_online`; `BYDAutoPanoramaDevice` không `setDisplayMode` (`project-context`, `oncar-master/1-hal`) | **UNCHANGED** [ĐO nguồn]: PanoramaDevice **không** thêm setter hiển thị; 2606 chỉ thêm 3 id *calibration* (hiệu chỉnh ảnh), không mở luồng xem | **Không** | Giữ camera-theo-xi-nhan qua HAL helper hiện có. 360 OEM vẫn đóng |
| N | **`am stack resize` chết / overscan / wm size** | `TaskRecord.resolveOverrideConfiguration` → `computeFullscreenBounds` `setEmpty()`; overscan ghi `display_settings.xml` theo uniqueId (CLAUDE.md §3/§5) | **UNCHANGED** [ĐO nguồn]: TaskRecord + DisplayContent + DisplayWindowSettings giống byte | **Không** | Giữ luật hiện hành (đo `overscanVerified`, đường mới xuống cuối) |
| O | **`set-task-windowing-mode` / `task resize` inert (DL5)** | Ghi chú DashCast cho **DL5** (ROM khác); trên DL3 `am stack move-task`/`list` vẫn chạy (`kachi-byd-firmware-facts-1003`) | **UNCHANGED / không áp dụng** [ĐO nguồn]: xe owner là DiLink3 (car.type 138); WM giống byte ⇒ hành vi DL3 như cũ | **Không** (không phải bế tắc của xe này) | Không việc |
| P | **PIP** | Phụ thuộc PinnedStackController / WM | **UNCHANGED** [ĐO nguồn]: `server/wm/*` (gồm PinnedStackController) **không** đổi lớp nào | **Không** | Không việc; nếu từng có kế PIP thì hành vi y 2602 |
| Q | **`byd_float_app_list` thiếu trong ROM** | Settings key không tồn tại trên 2602 (`kachi-ivi-unsupport-dialog-facts-1005`) | **CAN'T TELL**: giá trị nằm trong Settings DB / `content://carsettings`, **chưa kéo** phân vùng dữ liệu | — | On-car CHỈ ĐỌC: `settings get` / query `content://carsettings/...` cho khoá float list (không bắt buộc — đã có đường khác) |
| R | **Phân biệt nguồn phím (núm bệ giữa vs vô-lăng)** | Không phải firmware chặn: cả hai ra code 291/292; đã giải bằng `AUDIO_VOLUME_CTRL_MODE` (1=núm, 2=vô-lăng) (`kachi-car-facts-1004-key-source`, `feat/key-source-split`) | **NEW DOOR (phụ)** [ĐO nguồn]: 2606 thêm `INSTRUMENT_INPUT_KEYCODE_STATUS` (device INSTRUMENT 1007) — id trạng thái mã phím mới | **Có thể** — đường đọc keycode/nguồn thứ hai; nhưng hiện `AUDIO_VOLUME_CTRL_MODE` đã đủ | On-car CHỈ ĐỌC (nếu cần đối chứng): `getid INSTRUMENT_INPUT_KEYCODE_STATUS` lúc bấm từng nút, so với `AUDIO_VOLUME_CTRL_MODE`. Không chặn tầng 2 key-source-split |
| S | **Hộp "Hệ thống IVI không hỗ trợ"** | `CarSetting.UnsupportActivity` nhận mọi `android.settings.*` vì ROM không có `com.android.settings` (`kachi-ivi-unsupport-dialog-facts-1005`) | **UNCHANGED** [ĐO nguồn]: packages 2606 vẫn **không** có `com.android.settings`; CarSetting 212→264 (cùng cơ chế theo intent) | **Không** | Đã né (VietMap miễn pin qua `deviceidle whitelist`). Giữ |

---

## 4. Khoảng trống dữ liệu (phân vùng/tệp còn thiếu — vì sao vài mục là CAN'T TELL)

| # | Thiếu | Hệ quả | Lệnh (chỉ-đọc) |
|---|---|---|---|
| G1 | `/product` kéo thất bại (0 file, chỉ có versionCode) | Không soi được app `/product` (vd BydMuslimService, DiLinkAccount) | `adb pull /product <fw-2606>/_product` |
| G2 | `/system/app` mới chỉ 9/~80 thư mục | Không chắc app vùng (DAB/Zenrin…) bị gỡ hay lọc | `adb pull /system/app <fw-2606>/_system_app` |
| G3 | `/vendor` (HAL) + MCU `18.3.5.2606100.2` | HAL vendor + MCU ngoài tầm — mọi "cửa" cuối cùng do MCU/ECU quyết thì không đọc được từ Android | MCU không đọc được từ Android; `/vendor` cần dump riêng |
| G4 | `/system/etc/bydUiModeChangeWhitelist.properties` | Mục H: không biết file có liệt Kachi (đã moot vì fix app) | `adb pull /system/etc/bydUiModeChangeWhitelist.properties` |
| G5 | Settings DB / `content://carsettings` | Mục Q (`byd_float_app_list`) + cờ cold-start per-uid (mục E) | `settings list ...` / `content query --uri content://carsettings/...` (chỉ đọc) |
| G6 | Xác nhận W-HUD trên 2606 (mục B dựa readback 08-16) | Hạ mức từ [ĐO xe cũ] về "nghi là" cho 2606 | Cầu kiểm thử: `getid 0x38B00015` (kỳ vọng 1) + `0x38B00028` (kỳ vọng 1) — RE §4 G5 |
| G7 | Ai phát khung CAN `0x237` (đèn dự báo giới hạn tốc độ) | Mục C: chốt ISA có phải nguồn | runbook 2.96 §B2 `PHASE=watch` khi xe đi qua 2 biển tốc độ |

---

## 5. Việc kế đề xuất (không tự quyết việc đổi state xe)

1. **Mục C (ISA — cửa chính)**: chạy `oncar-runbook-2.96.md §B` khi có buổi xe — B1/B2 chỉ-đọc trước; B3 ghi chỉ khi đỗ/P/owner đồng ý (CLAUDE.md §14 tầng 1). Nếu cụm/HUD hiện số ⇒ mở spec 2.97 `ISA-NATIVE-SIGN`.
2. **Mục B (W-HUD)**: off-car đã cạn; chỉ còn coding ECU (quyết định owner, ADR 0002).
3. **Mục A/D (ADAS + km/h chữ nhật)**: giữ nguyên; thêm bước kiểm QML theme2 mỗi OTA (10 phút) phòng BYD đổi `!== 0`→`=== 1`.
4. **Các mục UNCHANGED còn lại**: không có việc firmware; giữ workaround hiện hành.
5. Cập nhật INDEX (`docs/README.md`) + `docs/PROJECT-BACKLOG.md` (mục `ISA-NATIVE-SIGN`, `CLUSTER-RECT-ADAS-SHRINK`, `CLUSTER-RECT-SEAL`) trỏ tới tài liệu này (CLAUDE.md §16 R2.1) — **chỉ khi owner duyệt tài liệu** (phiên này chỉ tạo đúng 1 file theo yêu cầu).

---

*Nguồn RE gốc: `../firmware/fw-2606/RE-2606-findings.md`. Mọi khẳng định firmware ở đây là `[ĐO nguồn]` trên hai bản decompile 2602 vs 2606; mọi khẳng định trạng thái xe là `[ĐO xe]` CŨ (≤07/10) ⇒ với 2606 chỉ ở mức "nghi là" cho tới khi đo lại (CLAUDE.md §14).*
