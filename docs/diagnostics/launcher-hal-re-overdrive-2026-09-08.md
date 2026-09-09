# Launcher HAL / Action / Multi-window RE — Overdrive + byd-dashcast + ClusterNav

> **Trạng thái**: Current · **Ngày**: 2026-09-08 · **Loại**: Diagnostics (RE finding, có bằng chứng file/method) ·
> **Mục đích**: Đóng các ẩn số HAL / hành-động điều-khiển / multi-window của launcher **Kachi** (BYD DiLink v3,
> Android 10) **off-car**, để phiên sau wire phần xe. Một tài liệu tham chiếu DUY NHẤT để dev cầm mà nối dây.

> **Phạm vi bằng chứng (đọc trước khi tin)** — mỗi dòng ma trận có cột NGUỒN + TRẠNG THÁI:
> - ✅ **proven ClusterNav** = ĐÃ chạy thật trên xe owner (Seal, DiLink3) trong ClusterNav 2.0 — mức tin cao nhất.
> - 🔶 **từ Overdrive/dashcast, chưa xác minh xe** = API/kỹ-thuật đọc được từ mã nguồn mở, CHƯA đo trên xe Kachi.
> - 🚗 **cần xe** = còn thiếu số liệu (feature-id, method-name, quirk theo trim) chỉ lấy được trên xe thật.
> - `[SUY]` = suy luận, chưa có bằng chứng trực tiếp.
>
> Nguồn mã (đọc 2026-09-08):
> - **Overdrive-release** (MIT, © 2026 Yash Srivastava) — `github.com/yash-srivastava/Overdrive-release`. DiLink3+5.
> - **byd-dashcast** (MIT, © 2026 Cedric Carre) — `github.com/Kiroha/byd-dashcast`. DiLink3+5. Overdrive ghi công
>   dashcast cho kỹ thuật chiếu (Readme §Credits).
> - **ClusterNav 2.0** (repo này) — `app/.../modules/hal/BydHal.kt`, `core/.../comfort/*`, `core/.../body/*`,
>   `app/.../comfort/*Applier.kt`, `app/.../body/BodyworkControl.kt`, `core/.../clustercast/simplified/*`,
>   `app/.../clustercast/CastShell.kt`.

---

## 0. Ảnh chụp kiến trúc (3 cách chạm xe — QUAN TRỌNG để chọn đường)

Ba dự án chạm HAL/hệ-thống BYD theo **ba cơ chế khác nhau**; Kachi kế thừa cơ chế của ClusterNav:

1. **ClusterNav — reflection IN-PROCESS + shell dadb loopback.**
   - HAL: `BydHal.device(FQN, ctx)` gọi `Class.forName("android.hardware.bydauto.…Device").getMethod("getInstance", Context).invoke(null, bypassCtx)` rồi hoặc `set(int[], BYDAutoEventValue)` (raw feature-id) hoặc `getMethod("setXxx", …).invoke()` (method-tên). Context bọc `BydPermissionBypassContext` (check\*Permission→GRANTED cho quyền `BYDAUTO*`) + spoof `getPackageName()="com.byd.dashcast"`. Nguồn: `BydHal.kt`.
   - Multi-window: shell qua dadb loopback (`am start`, `am task resize`, `am stack`, `wm size`). Nguồn: `AppMover.kt`, `CastShell.kt`.
2. **Overdrive — reflection HAL + Settings-provider + shell trampoline (uid-2000).**
   - HAL đọc/ghi: reflect device + feature-id (`BydFeatureIds.resolveOrFallback("Setting.SET_DRIVER_SEAT_…", <numeric>)`); một số điều-khiển đi qua **Settings.System key** của car-config provider (`getSystemInt/setSystemInt` — `BydCarSettings.java`) và **shell-exec `BydModeCommand`** (một class chạy trong process riêng bằng `app_process` với classpath của app, cho lệnh cần quyền).
   - Multi-window: reflection `IActivityTaskManager.setTaskWindowingMode` + `resizeTask` (`ClusterFreeformWindow.java`), hoặc chờ VirtualDisplay của "fission" rồi `am start --display <id>` (`ClusterCast.java`).
3. **byd-dashcast — daemon uid-2000 `app_process` + SurfaceControl + `AutoContainer.sendInfo`.**
   - "Beta Proxy Daemon" chạy `app_process64` ở uid=2000 (shell), làm: `sendInfo` tới service Binder `AutoContainer`, transaction `SurfaceControl` (mirror), đổi windowing-mode + quản FREEFORM stack, ghi CAN. App fallback nếu daemon chết. Nguồn: README §Architecture, `cluster/display/ClusterManager.kt`, `proxy/daemon/*`.

> **Hệ quả cho Kachi**: đường ✅ đã-proven của ta = **reflection in-process (HAL) + shell freeform (multi-window)**.
> Overdrive/dashcast cho ta **API surface + kỹ thuật thay thế**, KHÔNG phải đường phải copy. Feature-id numeric
> của Overdrive là **ứng viên fallback** khi tên-method/tên-feature không resolve.

---

## §1. CONTROL MATRIX — điều khiển từ dock Kachi → HAL

Ghi chú chung:
- **seatID 1-based** (1=lái/FL, 2=phụ/FR, 3=sau-trái/RL, 4=sau-phải/RR) — proven ClusterNav.
- ClusterNav ghi qua **`BydHal.callNamedInt(dev, "methodName", args…)`** (reflection, degrade-safe, không ném).
- `NOT_PROVISIONED_RC = -2147482648` (=`0x800003E8`) = HAL từ chối feature-id trên trim đó (proven Seal owner).

| # | Dock control (Kachi) | BYD HAL device.method(args) | Value semantics / range | NGUỒN | TRẠNG THÁI |
|---|---|---|---|---|---|
| 1 | **Cửa sổ — mở/đóng 1 cửa** | `BYDAutoBodyworkDevice.setBodyWindowCtrlState(window, state)` | window 1..4 (LF/RF/LR/RR); state 0=đóng, 1=mở | [proven ClusterNav] `Bodywork.kt`+`BodyworkControl.kt` (RE `bodywork-window-trunk-RE-2026-09-06.md`); [Overdrive stub] bodywork device | 🔶 core RE + reflection path sẵn, **chưa test ghi thật trên xe** (core-only, chưa UI) |
| 2 | **Cửa sổ — cả 4** | `BYDAutoBodyworkDevice.setAllWindowState(lf, rf, lr, rr)` | mỗi arg 0/1 | [proven ClusterNav path] `BodyworkControl.setAllWindows` | 🔶 chưa test ghi xe |
| 3 | **Cửa sổ — % (¼/½/¾/full)** | *(chưa có set-%; đọc `getWindowOpenPercent(w)` 0..100)* | Overdrive cover `position` 0–100 (`set_position_topic`) | [Overdrive code] `VehicleControlCatalog` cover; ClusterNav `Bodywork.kt` ghi "cơ chế % CHƯA rõ" | 🚗 `[SUY]` cần xe: method set-% chưa xác định |
| 4 | **Cốp (tailgate)** | `BYDAutoBodyworkDevice.setHetchDoorStatus(status)` *(typo "Hetch" đúng theo API OEM)* | close=2 (**proven**), open=1 `[SUY]` | [proven ClusterNav] `Bodywork.HATCH_CLOSE=2`; [Overdrive code] tailgate cover OPEN/CLOSE/STOP | ✅ close=2 proven · open `[SUY]` |
| 5 | **Cửa sổ trời (sunroof)** | `BYDAutoBodyworkDevice.set…` *(tên method chưa dump)* | cover OPEN/CLOSE/STOP | [Overdrive code] `VehicleControlCatalog` `cover("sunroof", …, "window")` | 🔶🚗 `[SUY]` method-name cần dump jar |
| 6 | **Rèm che trần (sunshade)** | `BYDAutoBodyworkDevice.set…` *(chưa dump)* | cover OPEN/CLOSE/STOP | [Overdrive code] `cover("sunshade", …, "shade")` | 🔶🚗 `[SUY]` method-name cần dump |
| 7 | **Khoá/mở khoá cửa (lock)** | doorlock device *(BYDAutoDoorLockDevice `[SUY]`)* hoặc SETTING | LOCK/UNLOCK; Overdrive state_locked="2", unlocked="1" | [Overdrive code] `VehicleControlCatalog` lock platform; [Overdrive stub] `doorlock/AbsBYDAutoDoorLockListener` | 🔶🚗 method-name cần dump |
| 8 | **Khoá trẻ em (child lock)** | SETTING feature-id | `SETTING_LEFT_REAR_DOOR_CHILD_LOCK_STATUS`=957350032, `…RIGHT…`=957350034; on/off 1/0 | [Overdrive code] `BydFeatureIds.java` + `VehicleControlCatalog` child lock switch | 🔶 |
| 9 | **Điều hoà — mode off/auto** | AC on/off *(BYDAutoAcDevice `[SUY]` / Settings key)* | off / auto; state topic `ac_on` (>0 = auto) | [Overdrive code] `VehicleControlCatalog` climate | 🔶🚗 `[SUY]` |
| 10 | **Điều hoà — nhiệt độ 17–33 °C** | AC device set-temp *(chưa dump)* / Settings key | min/max/step (catalog dùng `min_temp/max_temp/temp_step`; task nêu 17–33) | [Overdrive code] climate; [Overdrive stub] `BYDAutoAcDevice.getTemprature(i)` (đọc) | 🔶🚗 `[SUY]` set-method + range chốt trên xe |
| 11 | **Điều hoà — quạt 0–7** | AC device set-fan *(chưa dump)* | state topic `ac_fan`; 0..7 | [Overdrive code] climate `fan_modes` | 🔶🚗 `[SUY]` |
| 12 | **Điều hoà — recirc / defrost** | AC device *(chưa dump)* | on/off | `[SUY]` (không thấy entry rõ trong nguồn đã đọc) | 🚗 cần xe |
| 13 | **Ghế — làm mát (vent)** | `BYDAutoSettingDevice.setSeatVentilatingState(seatID, state)` | seatID 1..4; state **1=Tắt, 2=Mức1(low), 3=Mức2(high)** | ✅ [proven ClusterNav] `SeatComfort.kt`+`SeatComfortApplier.kt` (RE OEM `com.byd.airconditioning`); [Overdrive] feature-id `Setting.SET_DRIVER_SEAT_VENTILATING_STATE`=1335885832, passenger=1335885840 | ✅ proven (method-name path) |
| 14 | **Ghế — sưởi (heat)** | `BYDAutoSettingDevice.setSeatHeatingState(seatID, state)` | seatID 1..4; state 1/2/3; **loại trừ nhau với vent** (MCU reset cái kia) | ✅ [proven ClusterNav]; [Overdrive] `Setting.SET_DRIVER_SEAT_HEATING_STATE`=1335885835, passenger=1335885843 | ✅ proven |
| 15 | **Ghế — nhớ vị trí (memory recall)** | SETTING/seat method *(chưa dump)* | button PRESS ("Recall Driver Seat") | [Overdrive code] `VehicleControlCatalog` `seat_memory_driver` button | 🔶🚗 `[SUY]` |
| 16 | **Lọc bụi PM2.5** | `BYDAutoAcDevice.setAutoCleanAirState(1/0)` + `setQuickCleanAirState(1)` + `enablePurificationFunctionPrompt(0/1)` | autoClean=đặt chế độ (proven rc=0); **quickClean=lọc-thật** (trim owner cần cái này); prompt=best-effort (trim từ chối) | ✅ [proven ClusterNav] `Pm25Filter.kt`+`Pm25FilterApplier.kt` (on-car 2026-09-08) | ✅ proven |
| 17 | **Đèn ban ngày (DRL)** | BYDAutoLightDevice / feature-id | on/off 1/0; `Light.LIGHT_DAY_RUNNING_LIGHT_AUTO_STATE`=985661476 | [Overdrive code] `sw("drl", … "light_drl", "1","0")`; [Overdrive stub] `BYDAutoLightDevice` | 🔶 |
| 18 | **Đèn (pha/cốt/gầm/xi-nhan)** | `BYDAutoLightDevice.getLightStatus(type)` (đọc); `INSTRUMENT_HEADLIGHT_CONTROL_SET`=1276153912 (ghi) | LIGHT_SIDE=1/LOW_BEAM=2/HIGH_BEAM=3/L_TURN=4/R_TURN=5/F_FOG=6/R_FOG=7/FOOT=8; state 0/1. Headlight sel: 1=off,2=auto,3=parking,4=low | [Overdrive stub] `light/BYDAutoLightDevice`; [Overdrive code] `BydFeatureIds` | 🔶🚗 ghi cần xe |
| 19 | **Đèn nội thất (ambient)** | feature-id BODY/LIGHT | `Light.AMBIENT_FRONT_COLOR`=1121976336, `…BRIGHTNESS`=1121976328; palette 31 màu (`LightConstants.AMBIENT_COLOURS`) | [Overdrive code] `BydFeatureIds.java`, `light/LightConstants.java` | 🔶 |
| 20 | **Gương gập/mở (mirror fold)** | SETTING device (device_type 1023) command | 1=gập, 2=mở (`MIRROR_FOLD_COMMAND=1/UNFOLD=2`) | [Overdrive code] `BydConstants.java` | 🔶 |
| 21 | **Sạc không dây (wireless charging)** | *(chưa dump — Tier-2 switch)* | on/off 1/0 | [Overdrive code] `VehicleControlCatalog` Tier-2 wireless charger | 🔶🚗 `[SUY]` |
| 22 | **Giới hạn sạc (charge cap)** | SETTING `SETTING_AC_CHARGING_CURRENT_LIMIT_STATUS_SET`; SOC target `[SUY]` | `charge_cap_enabled` 1/0; `charge_cap_percent` **50–100 step 5** (%). *(Lưu ý: đây là giới hạn DÒNG sạc; SOC-target là kênh khác — Overdrive `socTargetPercent` 15–70)* | [Overdrive code] `VehicleControlCatalog` `number(50,100,5)`, `BydFeatureIds` | 🔶🚗 |
| 23 | **ADAS — cảnh báo tốc độ (SLW)** | adasDevice qua `BydDataCollector` (feature-id hoặc reflection) | on/off 1/0; state `speed_limit_warning` | [Overdrive code] `sw("adas_slw", … "speed_limit_warning")` | 🔶🚗 |

> **Phát hiện chốt về GHẾ (đối chiếu 2 nguồn) — dùng cho wiring:**
> ClusterNav **đã chứng minh trên xe** (2026-09-06) rằng ghi **raw feature-id họ `0x431010xx` trên AC device**
> trả `NOT_PROVISIONED` ngay cả ghế trước; đường ĐÚNG là **`BYDAutoSettingDevice.setSeatVentilatingState/
> setSeatHeatingState`** (method-tên, seatID 1..4, state 1/2/3). Overdrive độc lập đặt feature-id ghế trên
> **SETTING device** (`Setting.SET_DRIVER_SEAT_*_STATE` = 1335885832/835/840/843, họ 0x4FAx `[SUY hex]`) — **cùng
> device SETTING**, khác họ AC. ⇒ Kachi wire ghế: **SETTING device, method-tên** (proven); giữ feature-id SETTING
> của Overdrive làm **fallback raw-id** nếu tên-method không resolve trên trim khác. **3 mức** (off/low/high), KHÔNG
> phải 4 — Overdrive `VehicleControlCatalog` giữ `"medium"` chỉ như alias của `high` (HAL/cloud đều 3 mức).

---

## §2. WIDGET-DATA MATRIX — telemetry đọc cho widget HOME

| # | Widget | BYD HAL read | Đơn vị / ghi chú | NGUỒN | TRẠNG THÁI |
|---|---|---|---|---|---|
| 1 | **Pin %** | `BYDAutoStatisticDevice.getElecPercentageValue()` | double 0–100 | [Overdrive stub] statistic; [Overdrive] `BydVehicleData.socPercent` | 🔶 (device family ClusterNav đã có: `BydHal.STATISTIC`) |
| 2 | **Tầm hoạt động (range)** | `BYDAutoStatisticDevice.getElecDrivingRangeValue()` / `getEVMileageValue()` | int km | [Overdrive stub] statistic; `BydVehicleData.elecRangeKm` | 🔶 |
| 3 | **Odometer** | `BYDAutoStatisticDevice.getTotalMileageValue()` | float km | [Overdrive stub] statistic | 🔶 |
| 4 | **Tốc độ** | `BYDAutoSpeedDevice.getCurrentSpeed()` | double km/h; cũng `getAccelerateDeepness()/getBrakeDeepness()` | ✅ [proven ClusterNav] `SpeedProvider` hero km/h; [Overdrive stub] speed | ✅ proven |
| 5 | **Áp suất lốp** | `BYDAutoTyreDevice.getTyrePressureValue(area)` (int kPa) **hoặc** `getTyrePressure{Left,Right}{Front,Rear}()` (float) | [FL,FR,RL,RR] | ✅ [proven ClusterNav] `BydHal.TYRE` `callGetter`; [Overdrive stub] tyre; `BydVehicleData.tyrePressure[]` | ✅/🔶 |
| 6 | **Nhiệt độ lốp** | feature-id `Instrument.LF/RF/LB/RB_TYRE_TEMPERATURE` (1246797848…) | °C; **UNAVAILABLE tới khi TPMS bắn** | [Overdrive code] `BydFeatureIds.java`; `BydVehicleData.tyreTemperature[]` | 🚗 |
| 7 | **Nhiệt độ cabin** | `BYDAutoAcDevice.getTemprature(i)` / feature-id `Ac.AC_TEMP_INSIDE`=1031798832 | °C; climate `current_temperature_topic=cabin_temp` | [Overdrive stub] ac; [Overdrive code] `BydFeatureIds` | 🔶 |
| 8 | **Nhiệt độ pin** | `BYDAutoChargingDevice.getBatteryTemp()` (double) **hoặc** stat `STAT_*_BATTERY_TEMP` (intValue−40=°C: HIGHEST=1148190752, AVG=1148190776, LOWEST=1148190736) | °C | [Overdrive stub] charging; [Overdrive code] `BydFeatureIds`; `BydVehicleData.bodyworkBattTempC` | 🔶 |
| 9 | **Trạng thái sạc** | `BYDAutoChargingDevice.getChargeState()/getChargePower()/getRemainChargingTime()`; `BYDAutoPowerDevice.isCharging()/getBatteryRemainPowerEV()` | state enum; power double kW; time int | [Overdrive stub] charging+power | 🔶 |
| 10 | **Cửa/cửa-sổ (status)** | `BYDAutoBodyworkDevice` listener `onWindowStateChanged(area,state)`/`onDoorStateChanged`; `getWindowState(w)` 0/1, `getWindowOpenPercent(w)` 0..100 | area/state; power `getPowerLevel()` 0=off/1=acc/2=on | [Overdrive code] `BodyworkManager.java`; [proven ClusterNav] `Bodywork` getters | 🔶 |
| 11 | **PM2.5** | `BYDAutoPM2p5Device.getPM2p5Level()[0]` (1..6) + `getPM2p5Value()[0]` (µg/m³ 0–3000) | 1=Excellent…5=Heavy…6=Serious | ✅ [proven ClusterNav] `Pm25FilterApplier.readLevel` | ✅ proven |
| 12 | **Ắc-quy 12V / khoá** | `BYDAutoPowerDevice.getBatteryVoltage()`; bodywork `getBatteryVoltageLevel()` (0=low/1=normal/2=invalid); `getKeyPowerLowInd()` | V / enum | [Overdrive stub] power+bodywork; `BydVehicleData.battery12vLevel` | 🔶 |
| 13 | **Góc lái / bánh** | `BYDAutoBodyworkDevice.getSteeringWheelValue(i)` (±780°); `BYDAutoSpecialDevice.getWheelSpeed(area)` | độ; km/h theo bánh | ✅ [proven ClusterNav] `BydHal.BODYWORK/SPECIAL` (dead-reckon RE) | ✅ proven |
| 14 | **VIN / loại xe (Seal↔Han)** | `BYDAutoBodyworkDevice.getAutoVIN()/getType()`; SystemProperties + Build.MODEL | model-code table `AUTO_TYPE_*` (vd EK=138, EL=96) | [Overdrive stub] bodywork model-codes; [proven ClusterNav] `SeatComfortApplier.isHanModel` | 🔶/✅ (ClusterNav dùng string-match "han") |

---

## §3. MULTI-WINDOW / APP-INTO-SLOT — chiếu / đặt app vào ô

### 3.1 ClusterNav (✅ proven on-car) — shell freeform
- **Đặt app**: `am start -a android.intent.action.MAIN -c android.intent.category.LAUNCHER --display <id> --windowingMode 5 -n '<comp>'` (freeform) rồi **`am task resize <taskId> <l> <t> <r> <b>`** để fit ô. Nguồn: `AppMover.castToCluster` / `CastGeometryController.resizeSlot`.
- **⚠ Bài học chốt**: KHÔNG dựa vào bare `--windowingMode 5` để có bounds đúng — nó rơi vào rect mặc định nhỏ của AMS; recipe proven = launch (fullscreen/land) → `am task resize`. (Overdrive xác nhận độc lập — xem 3.2.)
- **Cờ freeform (BẮT BUỘC)**: `settings put global enable_freeform_support 1` + `settings put global force_resizable_activities 1`. Framework đọc **chỉ lúc BOOT** (`ATMS.retrieveSettings`, không ContentObserver) ⇒ **phải power-cycle xe 1 lần** sau khi seed; trước đó mọi yêu cầu freeform bị hạ cấp im lặng. Nguồn: `CastShell.ensureFreeformSeed`, `CastGeometryController.ensureFreeformFlags`. Có marker prefs bền + đường `unseedFreeform` để gỡ.
- **Fallback khi freeform chưa sống**: `wm size WxH -d <disp>` (đổi logical size, letterbox căn giữa — sống qua reboot, phải `resetDisplayAll`) → `wm overscan` (content-inset, A11+ đã gỡ). Nguồn: `CastShell.forceDisplaySize/overscanVerified`.
- **CP/AA**: `am stack move-task <taskId> <stackId> true` (không NPE); tránh freeform cho CP (bounds nhỏ → surfaceflinger crash).
- **Về màn chính**: `am start --display 0 --windowingMode 1 -f 0x20000000 (SINGLE_TOP) …LAUNCHER…` (verb DUY NHẤT đổi được windowing-mode của task đang chạy trên A10). Nguồn: `AppMover.fullscreenReturnCommand`, `CastShell.returnAppToMain`.

### 3.2 Overdrive — reflection freeform (cùng cổng, khác cơ chế)
- `ClusterFreeformWindow.java`: reflect `IActivityTaskManager` → `setTaskWindowingMode(taskId, FREEFORM=5)` → `resizeTask(taskId, Rect, RESIZE_MODE)`; verify `getTaskBounds` ≈ bounds, else fallback shell `cmd activity task resize`. **`resizeTask` ném nếu task CHƯA freeform** ⇒ phải đổi mode trước.
- **Xác nhận cùng cổng freeform như ClusterNav**: `supportsFreeform = config_freeformWindowManagement || Settings.Global.enable_freeform_support != 0`; "car head unit ships neither by default" ⇒ mọi verb freeform fail tới khi bật cờ. `setStackWindowingMode` **absent trên API 29**.
- **KHÔNG dùng bare `am start --windowingMode 5`** (ghi rõ trong KDoc): rơi vào default freeform rect → launch fullscreen, để RESUME, rồi convert task-sống kèm bounds. (Trùng khớp bài học ClusterNav.)
- `ClusterCast.java`: trên firmware này "display 1 là HEAD UNIT", cụm là **VirtualDisplay của 'fission'** ⇒ chờ VD id materialise rồi `am start --display <id> --windowingMode 1`.

### 3.3 byd-dashcast — daemon uid-2000 + SurfaceControl mirror + AutoContainer.sendInfo
- **Kích hoạt cụm** qua Binder service `AutoContainer` (`android.os.IAutoContainer`, txn #2 `sendInfo(int type, int infoInt, String infoStr)`), type=1000 (CONFIRMED BYD Seal EU):
  - `30` → chuyển chế độ độ-phân-giải Seal EU (29=8.8", 30=12.3", 31=10.25") — **chỉ an toàn trên slow path (không VD)**;
  - `16` → **bật chiếu fullscreen** (lệnh thật để launch app lên display cụm);
  - `35` → tạo VirtualDisplay (slow path, VD hiện ~280ms sau);
  - `18` → **đóng chiếu** (restore, `cmd=0` một mình KHÔNG đủ); `0` → refresh Qt video (đi sau 18).
  - Chuỗi: warm `30→(3s)→16→onDisplayReady`; slow `30→16→35`. DL5 = **chỉ `sendInfo(16)`** trên `auto_container`. Nguồn: `ClusterManager.kt`.
- **Mirror**: TextureView cụm qua `SurfaceControl` + forward touch/key (`cluster/mirror/*`). **Fission** = layout nhiều app trên cụm.
- **Resize task**: `ShellTaskResizer` (`am task resize` DL2/3/4 → fallback `cmd activity task resize` DL5); `ReflectionTaskResizer` (`IActivityTaskManager.resizeTask(taskId, Rect, RESIZE_MODE_FORCED)`).
- Chạy qua **daemon uid=2000 `app_process64`** (ProxyKeeperService giữ sống) cho các thao tác cần quyền shell.

### 3.4 Khuyến nghị P2 — freeform trên DISPLAY 0 (màn chính IVI)

**Chọn: TÁI DÙNG freeform proven của ClusterNav** (`CastShell` / `AppMover` / `CastGeometryController`), KHÔNG mirror/VirtualDisplay cho P2.

| Cách | Ưu | Nhược | Emulator test được? |
|---|---|---|---|
| **Freeform + `am task resize`** (ClusterNav shell, đề xuất) | Per-task bounds thật → xếp GRID/ô nhiều app trên display 0; đã proven on-car; không cần daemon uid-2000; hoàn tác được | Cần seed cờ + **power-cycle 1 lần**; freeform "decoration"; task fullscreen-lock từ chối resize | ✅ Logic parse `am stack list`/`wm size`, dựng lệnh, geometry math test off-car. ❌ Kích hoạt freeform thật cần xe (power-cycle) |
| Reflection freeform (Overdrive) | Không shell fork trên đường drag (rẻ) | Cùng cổng power-cycle; `resizeTask` ném nếu chưa freeform; API mảnh theo ROM | ✅ một phần (logic) · ❌ reflect ATMS cần thiết bị |
| Mirror / VirtualDisplay (dashcast) | Không cần freeform; touch-forward | Nặng (daemon uid-2000 + SurfaceControl); hợp **đẩy sang cụm (display 1)**, KHÔNG hợp "đặt app vào ô trên display 0"; nhiều app = Fission phức tạp | Phần lớn car-only |

**Lý do**: display 0 là màn IVI thật (không phải VD), freeform-window hoạt động ở đó **một khi cờ đã seed + power-cycle**. Muốn lưới ô app resize-được thì **cần per-task bounds = freeform**; `wm size`/overscan là display-global, không đặt 2 app vào 2 ô. Mirror/VD là để **chiếu sang cụm**, không phải để slot app trên màn chính. Kachi đã có sẵn toàn bộ hạ tầng này (đường Cast) ⇒ P2 = reuse, chỉ đổi layout/điều-phối (`WorkspaceLayout` Rect → `am task resize` bounds).

**Emulator-testable vs car-only (P2)**:
- ✅ Off-car/emulator: parse `am stack list`/`dumpsys`, tính Rect từ preset `WorkspaceLayout`, dựng chuỗi lệnh, `wm size`, render vỏ HOME, đặt app **fullscreen** vào 1 ô (không cần freeform).
- 🚗 Car-only: freeform **thật** (sau power-cycle), 2+ app cùng lúc mỗi ô một bounds, HAL writes, `sendInfo` opcodes theo trim/độ-phân-giải.

---

## §4. ON-CAR VERIFICATION CHECKLIST — còn thiếu gì phải lấy trên xe

**A. Dump nguồn sự thật của trim (làm 1 lần / xe):**
1. Kéo framework để có `BYDAutoFeatureIds` + chữ ký method THẬT của trim:
   `adb pull /system/framework/*bydauto*.jar` (và `/system/framework/framework.jar` nếu cần), rồi `jadx`/`javap` để đối chiếu tên feature-id + method (họ `Setting.*`, `Body.*`, `Ac.*`, `Light.*`).
2. Liệt kê device thật + method qua ClusterNav probe (vehicleTest build): `BydHal.methods(dev, "set","get")` cho từng device (SETTING, AC, BODYWORK, LIGHT, CHARGING, POWER, STATISTIC, TYRE, SPEED).

**B. Chốt các method-name còn `[SUY]` (§1):** sunroof/sunshade/lock/wireless-charging/recirc/defrost/seat-memory + set-% cửa sổ + set-temp/set-fan điều hoà. Dò trên BODYWORK/AC/SETTING device (`getMethods()` lọc `set*`).

**C. Chốt feature-id numeric nếu tên không resolve:** seat `Setting.SET_*_SEAT_*_STATE` (1335885832/835/840/843), charge-limit `SETTING_AC_CHARGING_CURRENT_LIMIT_STATUS_SET`, child-lock (957350032/34), light/ambient/headlight ids — probe `writeProbe`/`callNamedInt`, đọc rc: `-2147482648` = NOT_PROVISIONED (đổi đường).

**D. Quirk theo trim:**
- **Seal (2 ghế) vs Han (4 ghế)**: `SeatComfort.seatsForModel(isHan)`; xác nhận seatID 3/4 (Han rear) ghi được.
- Ghi **method-name** (proven) vs **raw feature-id** (Overdrive) — trim nào ăn đường nào.
- PM2.5 `enablePurificationFunctionPrompt` bị từ chối trên trim owner (rc=-2147482645) — best-effort, đừng chặn lọc.
- `sendInfo` opcodes + độ-phân-giải (29/30/31) khác theo kích thước cụm; DL3 vs DL5 khác chuỗi.

**E. Multi-window trên xe (KHÔNG thay được bằng emulator):**
- Seed `enable_freeform_support=1` + `force_resizable_activities=1` → **TẮT MÁY XE hẳn 1 lần** (không phải `adb reboot`) → probe `am task resize` có được chấp nhận không (`isFreeformAlive`).
- 2 app cùng lúc mỗi app một ô (split) — chỉ freeform-sống mới làm được.
- Đặt app lên display 0 ở freeform rồi trả fullscreen (`--windowingMode 1 -f 0x20000000`) không để lại cửa sổ nổi kẹt.

---

## §5. LICENSE / ATTRIBUTION

- **Overdrive-release** — **MIT License, © 2026 Yash Srivastava**. `THIRD_PARTY_NOTICES.md`: MIT chỉ áp cho **mã của Overdrive**; các thành phần bundle (vd `app/libs/classes.jar` = BYD stubs, hệ số blind-spot closed-source) theo giấy phép RIÊNG của chúng. Overdrive `Readme.md` §Credits **ghi công byd-dashcast** cho kỹ thuật chiếu.
- **byd-dashcast** — **MIT License, © 2026 Cedric Carre**.
- **BYD HAL stubs** (`android.hardware.bydauto.*`) = API của BYD/OEM (không phải của hai repo trên); ta chỉ **mô tả API surface**, không tái phân phối jar OEM.

> **Ranh giới thực hành (bắt buộc):** Kachi/ClusterNav **DOCUMENT API + kỹ thuật** rồi **tái hiện SẠCH** bằng hạ
> tầng riêng đã có (`BydHal` reflection + shell cast). **KHÔNG bê nguyên mã** của Overdrive/dashcast. MIT cho phép
> adapt kèm attribution, nhưng cách làm của dự án là clean-room từ API surface — nếu về sau có adapt đoạn mã cụ
> thể nào thì phải **giữ header MIT + ghi công tác giả** trong file đó và liệt kê ở `CREDITS.md`.

---

## Phụ lục — trích dẫn nguồn (file:đã-đọc)

- ClusterNav: `app/src/main/java/com/byd/clusternav/modules/hal/BydHal.kt`; `core/…/comfort/{SeatComfort,Pm25Filter}.kt`; `core/…/body/Bodywork.kt`; `app/…/comfort/{SeatComfortApplier,Pm25FilterApplier}.kt`; `app/…/body/BodyworkControl.kt`; `core/…/clustercast/simplified/{AppMover,CastGeometryController}.kt`; `app/…/clustercast/CastShell.kt`.
- Overdrive: `stubs-bydauto/android/hardware/bydauto/{setting,ac,bodywork,charging,light,tyre,speed,power,statistic}/BYDAuto*Device.java`; `app/…/byd/{BydConstants,BydFeatureIds,BydVehicleData,VehicleActuatorBridge,BydCarSettings}.java`; `app/…/byd/bodywork/{BodyworkConstants,BodyworkManager}.java`; `app/…/byd/light/LightConstants.java`; `app/…/mqtt/VehicleControlCatalog.java`; `app/…/launcher/{ClusterCast,ClusterFreeformWindow}.java`; `LICENSE`, `Readme.md`, `THIRD_PARTY_NOTICES.md`.
- byd-dashcast: `README.md`, `LICENSE`; `app/…/cluster/display/ClusterManager.kt`; `app/…/infrastructure/task/{ShellTaskResizer,ReflectionTaskResizer}.kt`.

> **Việc kế (không làm ở phiên này — chỉ ghi doc):** thêm entry vào `docs/README.md` (INDEX) + `docs/PROJECT-BACKLOG.md`
> cho tài liệu này (R1/R3 documentation-discipline), và re-index knowledge base.
