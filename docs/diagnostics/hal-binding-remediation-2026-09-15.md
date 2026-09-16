# RE + vá binding HAL cho 187 chức năng — off-car (2026-09-15)

> ⚠ **2026-09-16 — owner gỡ TOÀN BỘ ADAS/an toàn khỏi launcher** (backlog (N) ADAS-PURGE). Mọi mục ADAS/an toàn và mọi con số dưới đây chỉ là **LỊCH SỬ ĐO**, giữ nguyên làm bằng chứng. Trạng thái hôm nay: **167 chức năng** (54 nút · 106 thông tin · 4 gói · 3 hành động), **không còn** nút/datum ADAS/an toàn nào — xem `docs/diagnostics/adas-purge-2026-09-16.md`.

- **Bối cảnh:** chuyến on-car 2026-09-15 chấm 64 OK / 121 KHÔNG / 2 chưa (187). Owner: *"mất quá nhiều thời gian trên xe vô nghĩa"* → RE toàn bộ **off-car**, lên xe chỉ xác nhận.
- **Spec:** `docs/specs/kachi-hal187-cast-remediation.html`. **Nguồn:** `../jadx-*` (stub có enum/feature-id THẬT), `BYDAutoFeatureIds.java`, `../jadx-openbyd/.../CarControlImpl.java` (usage thật), `../dashcast-src/CHANGELOG.md`, doc RE nền.
- **Verdict:** BINDING-OK · WRONG-ROUTE · WRONG-SCALE · WRONG-ENUM · UNMAPPED · NEEDS-ONCAR · UNAVAILABLE-TRIM.
- **Công cụ chốt NEEDS-ONCAR:** T-BRIDGE `hal get <method> [--es args n]` / `hal set <method> --es args a,b --ez auto_confirm true` (bản 1.61+).

---

## ★ ROOT-CAUSE XUYÊN SUỐT (sửa 1 chỗ, mở khoá cả loạt) — ưu tiên tuyệt đối

### §A — Đường ĐỌC feature-id đang VỠ HOÀN TOÀN [ĐO từ source, đã tự verify]
- `BydHal.hasSyncGet`/`tryGet` (`BydHal.kt:193-202`) tìm method **`get(int[])` 1-arg** → method này **KHÔNG TỒN TẠI**.
- API đọc feature THẬT = **`get(int[], Class)` 2-arg** (`AbsBYDAutoDevice.java:84`). App chạy được OpenBYD gọi `dev.get(new int[]{id}, Integer.TYPE).intValue` (`CarControlImpl.java:239-240`).
- ⟹ `hasSyncGet` trả **false cho MỌI device** → `featureGet` (`BydHalGateway.kt:45-49`) trả null → **mọi telemetry route Feature = "—"**. Đây là gốc của phần lớn 121 lỗi đọc.
- **Fix:** sửa `tryGet` gọi `get(intArrayOf(id), Integer.TYPE)`, đọc `.intValue`; bỏ/sửa `hasSyncGet` (dò đúng chữ ký 2-arg). Feature GHI vẫn OK (dùng `set(int[], EventValue)` khớp). **1 fix → hàng chục mục đọc sống lại** (còn lại là scale/enum).

### §B — int[]→toString ra rác (parse) [ĐO]
- Getter trả `int[]` (PM2.5 value/level, wheel_speed…): `callGetter` gọi `.toString()` → `"[I@hash"` → `coerceInt` null → "—". `AbsBYDAutoDevice` có sẵn `arrayToStr(int[])` (:14) / `getIntArray`. **Fix:** khi kết quả là mảng, lấy `[0]` (hoặc parse theo mục).

### §C — Route feature-id telemetry đi SAI device theo domain
- `featureDeviceFor(id, domain)` map domain→device mặc định (`HalBindingTable.kt:219`) → nhiều mục feature-read trỏ nhầm (slope→Setting thay Sensor; anion→AC thay PM2P5; ambient→Light thay Setting). Cần cho phép telemetry khai `halDevice` override (giống control đã có).

> Ba §A/§B/§C là hạ tầng: sửa xong thì phần lớn WRONG-ROUTE bên dưới chỉ còn là đổi bindingKey/thêm readArg.

---

## Theo domain (mỗi mục có file:line — chốt off-car; NEEDS-ONCAR = phải đo 1 lệnh)

### Đèn (Lights)
- **Off-car win:** `light_low_beam`/`light_high_beam` → `getLightStatus` readArg **2/3** (siblings fog/turn/side OK cùng getter). `drl` (ghi): đang ghi hằng `_STATE` (no-op) → `setDayTimeLightState`, OPEN=1/CLOSE=2.
- **Tách/gate:** `headl` + `headlight_mode` **trùng feature-id `1276153912`** (INSTRUMENT_HEADLIGHT_CONTROL_SET, device **INSTRUMENT(1007)** không phải LIGHT) — họ lỗi "hai nút một lệnh".
- **NEEDS-ONCAR:** toàn bộ **ambient** (8 mục) — device nghi **SETTING(1023)** atmosphere-lamp; scale nghi **0–100** (không phải 0–10), màu **31** (không phải 5); `headlight_feedback`.

### Động lực (Drivetrain)
- **Off-car win (có source):** `gear`→`getCurrentGear()` (P=3/R=1/N=0/D=2/255) thay `getGearboxState` (chỉ ON/OFF); `energy_mode`→`getEnergyMode()` (STOP0/EV1/FORCE_EV2/HEV3/FUEL4/KEEP5); `powertrain_mode` ghi→`setEnergyMode` (EV→1, HEV→3); `op_mode`/`drive_mode`→`getOperationMode/setOperationMode` (ECO1/SPORT2/NORMAL3/SNOW4… — map lại index UI); `steering_deg` thêm readArg selector **1**; `slope_deg` đổi device→**BYDAutoSensorDevice** (id 573571116 đúng, sai device).
- **UNMAPPED→đề xuất:** `avh`→`BYDAutoADASDevice.setAVHState` / feature 304087110.
- **NEEDS-ONCAR (id bịa, không có trong nguồn):** `motor_front/rear_rpm`, `motor_front_torque` (feature-id mô-tơ kéo bị zero-hoá trong decompile — cần id thật trên xe); `drift_mode` (nghiêng **UNAVAILABLE-TRIM** — Sealion 6 DM-i không drift).

### Khí hậu (Climate/PM2.5)
- **Off-car win (tên method sai):** `ac_wind`→`getAcWindLevel` (0–7); `ac_cycle`→`getAcCycleMode` (trong=1/ngoài=0); `temp_unit`→`BYDAutoAcDevice.getTemperatureUnit` (°C=1/°F=0, hiện route Setting luôn null); `anion_state`→`getPM2p5AnionState` (device PM2P5).
- **Parse (§B):** `pm25_value`/`pm25_level` int[]→lấy `[0]` (đây là "thông số bụi chưa hiển thị").
- **Feature-read (§A):** `cabin_temp` (device AC đúng, cơ chế đọc vỡ).
- **BINDING-OK:** `pm25_online` (ON=1/**OFF=2**/NULL=0 — doc cũ ghi OFF=0 SAI), `inside_temp`(method), `ext_temp`, `ac_on`, `ac_auto`[SUY].
- **NEEDS-ONCAR:** `coolant_temp` (HAL không lộ giá trị °C numeric — chỉ có mức/đèn cảnh báo; probe feature-id), scale `inside_temp`/`cabin_temp`, enum `ac_auto`.

### Lốp + Thân xe (Tyres + Body)
- **Off-car win (method/setter sai tên):** 4 áp lốp → `getTyrePressureValue(area)` (area 1/2/3/4) + thêm readArg (hiện `getTyrePressureLeftFront` không tồn tại); `sunroof` ghi→**`setMoonRoofState`** (xác nhận OpenBYD:1505), mở=1/đóng=2; cửa đọc `door_*`→`getDoorState(area)` (CLOSED0/OPEN1/255); `emergency_alarm`→`getAlarmState()` (OFF0/ON1).
- **BINDING-OK:** `tailgate_status`(getHatchDoorStatus), `trunk`(setHetchDoorStatus mở1/đóng2), `mirror_fold`(id 960495624=REAR_VIEW_MIRROR_STATE), `sunroof_state/pos`, `power_level`(OFF0/ACC1/ON2), `vehicle_type`. `wiper`(ghi) id 321912848=WIPER_FRONT_WIPER_LEVEL đúng (caveat device-target).
- **WRONG-ROUTE→NEEDS-ONCAR:** `lock`/`door` — `setDoorLockState` KHÔNG tồn tại + class sai case (`BYDAutoDoorlockDevice`→`DoorLockDevice`, L hoa → ClassNotFound); DoorLock device chỉ có READ. Setter khoá-ngay có thể không expose qua HAL app → chốt trên xe.
- **NEEDS-ONCAR (feature-id vô danh, không có method):** 4 nhiệt lốp per-corner (HAL chỉ có `getTyreTemperatureState` enum 0-arg, không có value per-corner), `tailgate_position`, `wiper_state`(đọc), `child_lock`, `mirror_auto`, `mirror_fold_btn`.
- **Nghiêng UNAVAILABLE:** `hood` (HAL không expose mở nắp capô — `BODYWORK_CMD_DOOR_HOOD=5` chỉ là area đọc).

### Năng lượng (Energy)
- **Off-car win (method sai tên / feature→named cùng device):** `is_charging`→`getChargerWorkState()==2` (READY1/START2/FINISH3/TERMINATE4); `charge_power`→`BYDAutoInstrumentDevice.getChargePower()` (đang gọi sai device); `charging_pct`→`getChargePercent()`; `charging_eta_hour/min`→`getChargeRestTime()[0]/[1]`; `charging_capacity_kwh`→`getChargingCapacity()`; `charger_work_state`→`getChargerWorkState()`; `charging_state`→`getChargerWorkState()` (getChargeState KHÔNG tồn tại); `fuel_range_km`→`getFuelDrivingRangeValue()`; `fuel_pct`→`getFuelPercentageValue()`; `ev_mileage_km`→`getEVMileageValue()`.
- **Control (UNMAPPED→named, enum RÕ):** `start_charging`→`setChargingMode(1)` (IMMEDIATELY); `target_soc_set`→`setChargeStopCapacityState` **enum rời** (50→6…100→1, KHÔNG phải % thô); `wireless_charge`→`setWirelessChargingSwitchState` (ON=1/OFF=2, đang route sai device Statistic); `charge_cap`→nghi `setChargeStopSwitchState` (OFF=1/ON=2) — NEEDS-ONCAR phân biệt với giới-hạn-dòng-AC.
- **Feature-id gán NHẦM nghĩa (WRONG-ROUTE, xoá/đổi):** `cell_v_high/low` (1147142192/160) thực ra = atom **tầm xăng** (`EVENT_STAT_FUEL_RANGE`, `VehicleBridge.java:89-90`); `batt_range_bodywork` (300941336) = **BODYWORK_STEERING_WHEEL_SPEED** (`FeatureIds:685`) — không phải áp cell / tầm pin.
- **BINDING-OK:** `soc`, `ev_range_km` (thêm guard sentinel INVALID=1000/DEFAULT=1023), `odometer`, `consumption_50km`.
- **NEEDS-ONCAR (feature-id bị zero-hoá trong decompile / không có named):** `motor_power`, `batt_temp`, `cell_temp_high/low/avg`, `soh_oem`, `trip_km/hours/kwh` (chọn giữa 2 ứng viên named + chốt scale phút-vs-giờ, Wh-vs-kWh).
- **Ngoài domain (báo Safety):** `volt_12v`→`BYDAutoPowerDevice.getBatteryVoltage` KHÔNG tồn tại (chỉ có getBatteryLowVoltageState/getBatteryRemainPowerEV) → WRONG-ROUTE.

### An toàn/ADAS + GPS + Giải trí
- **Off-car win (đổi sang named-method đúng device):** `seatbelt_driver/passenger`→`BYDAutoSafetyBeltDevice.getSafetyBeltStatus(1/2)` (UNLOCK0/LOCK1/INVALID2); `oms_driver/passenger`→`getPassengerStatus(1/2)` (NOBODY0/SOMEBODY1) — hiện route nhầm ADAS(1038), thật ở SafetyBelt(**1042**); `volt_12v`→`BYDAutoOtaDevice.getBatteryVoltage` (Power không có method này); `cam`→`BYDAutoADASDevice.setAVMSwitchState` (OFF1/ON2) bỏ pseudo-id `3001` bịa; `camera_view`→`BYDAutoPanoramaDevice.setDisplayMode`.
- **BINDING-OK:** `radar_zones`(getAllRadarProbeStates — số "vô nghĩa" vì chỉ báo khi lùi/đỗ), `esp_state`(id 305135676=ADAS_ESP_STATE đúng, [X] do enum hiển thị), `mcu_status`, `volt_12v_level`, `vin`, `engine_coolant_level`, `vol`, `brightness_gear`, `hud_*`. `cast`(nút dock no-op **by-design** — cast thật đi đường SimpleCast riêng, không phải lỗi).
- **`engine_code`/`oil_level`:** route đúng, [X] nghi **engine PHEV ngủ** (Sealion 6 DM-i) → chốt: nổ máy rồi `hal get`.
- **GPS (`gps_lat/lon/elevation/heading`): BLOCKED-BY-DESIGN.** KHÔNG qua HAL — `BYDAutoLocationDevice` chỉ có setter. Đường duy nhất = Android `LocationManager` + quyền `ACCESS_FINE_LOCATION`, NHƯNG `DeadReckonRetirementTest` (app) **pin manifest không xin bất kỳ quyền location nào** — retirement an toàn có chủ đích sau sự cố Dead-Reckon suýt ghim GPS toàn xe (CLAUDE.md §3). ⇒ 4 mục giữ route None + nhãn; **mở lại = quyết định của owner** (đảo một retirement an toàn), không tự làm. (Đã thử wire rồi revert 2026-09-15.)
- **NEEDS-ONCAR (id "Overdrive" không resolve trong dump; per-side không có getter):** `child_presence`, `speed_limit_warning`(nghi getSLAState), `key_bluetooth`, `cluster_music`(nghi INSTRUMENT_MUSIC_SOURCE 970981412), `screen_rotation`(enum), `radar_volume`(chỉ write-only, không có đường đọc), và per-corner BSD/LCA/RCTA/DOW (8 mục — ADAS chỉ có aggregate getBSDState, per-side qua listener event).

---

## §C (mở rộng) — Telemetry feature-read đi SAI device vì thiếu `halDevice`
`TelemetrySpec` **không có trường `halDevice`** (chỉ `ControlDef` có) → mọi feature-read route theo Domain mặc định (`featureDeviceFor(id, domain)`), sai cho: dây an toàn/occupancy (thật SafetyBelt 1042, không ADAS 1038), anion (PM2P5 không AC), slope (Sensor không Setting), ambient (Setting không Light). **Fix hạ tầng:** thêm `halDevice` cho `TelemetrySpec` (override device) — HOẶC ưu tiên chuyển các mục này sang **named-method** (đã có getter đúng, degrade-safe, không phụ thuộc feature-id Overdrive chưa verify).

---

## Kế hoạch vá (theo đòn bẩy)
1. **§A** (feature-read 2-arg) — 1 fix `BydHal.tryGet/hasSyncGet`, mở khoá mọi feature-read. **Làm đầu.**
2. **§B** (int[]→[0]) + **§C** (telemetry `halDevice` override).
3. **Batch method-name/route/enum** có source (Đèn low/high/drl, Động lực gear/energy/op_mode/steering/slope, Khí hậu ac_wind/cycle/unit/anion, Lốp áp + sunroof/door/alarm) — mỗi domain 1 test writeArgs/readArg.
4. **Tách "hai nút một lệnh"** (headl/headlight_mode; nếu lock/door tương tự).
5. **NEEDS-ONCAR** gom thành 1 script `hal` sweep — chạy 1 lượt trên xe, không mò UI.
6. **UNAVAILABLE-TRIM** (drift, hood, nhiệt-lốp-per-corner nếu xác nhận) → ẩn/nhãn.
