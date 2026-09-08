# RE TRACK 3 — Đường SET cờ (coding / engineering menu / vehicle-settings / BydDevelopmentTools)

> **Loại:** RE handoff (đọc-hiểu, KHÔNG sửa/commit) · **Ngày:** 2026-08-19 · **Scope:** off-car source RE
> **Mục tiêu:** Nếu HUD-zin nav bị gate bởi *coding*, tìm CÁCH SET nó — qua DID/UDS, menu kỹ thuật, app coding, hoặc settings/prop mà firmware đọc.
> **Nguồn đọc:** `jadx-BydDevelopmentTools` (dev/factory tool), `com.byd.vehiclesettings` (4 DEX: `e23be25a`=classes, `5f4b840b`=classes2, `ea75450d`=classes3, `ff2dd9ce`=classes4), `jadx-DiCarServer/.../instrument/Instrument.java`, codebase `CarExecHudCatalog.kt`, ADR 0002 + `hud-provisioning-compare-2026-08-19.md`.
> **Liên quan (đọc kèm, không lặp lại):** `re-hud-track2-firmware-gate.md` (firmware gate 0x38B00030/0x32B1102E), `re-hud-track5-priorart.md`.

---

## TL;DR — 5 gạch đầu dòng quan trọng nhất

1. **KHÔNG có app/menu kỹ thuật Android nào trong image làm variant coding HUD.** `BydDevelopmentTools` (`com.byd.byddevelopmenttools`) là tool **sửa chữa/rollbench/OBD-readiness/log**, KHÔNG chạm HUD/coding/variant. Grep toàn app-code cho `hud|coding|variant|writeData|0x2E|38B000|4C10E0` = **0 khớp** (chỉ trúng androidx + `R.java`).

2. **KHÔNG có primitive UDS trong bất kỳ app nào.** Grep toàn bộ sysimg + decoded cho `WriteDataByIdentifier|0x2E|SecurityAccess|DiagnosticSession|RoutineControl|ReadDataByIdentifier` = **0 khớp**. Variant coding là thao tác **off-board (máy chẩn đoán ngoài qua OBD/UDS)**, không nằm trong Android.

3. **`40d` variant (138 vs 162) là READ-ONLY từ Android.** Nó map vào `CarInfo.CAR_AUTO_TYPE_*` (138=`EK`/`0x8A`, 162=`SA3EJ`/`SA3H_JK`/`0xA2`), đọc runtime qua `ICarInfoManager` (Binder tới DiCarServer) — interface này **chỉ có getter, KHÔNG có setter**. Giá trị nằm trong **ECU/instrument variant coding** (provision tại nhà máy/đại lý), Android không ghi được.

4. **Cờ HUD ghi được duy nhất "gần nghĩa bật nav-HUD" = `0x32B1102E`** (`INSTRUMENT_HUD_NAVIGATION_MAP_SET`, 2=ON/1=OFF, ghi bởi OEM `Hud00600401300000.setState`). NHƯNG đây là **toggle runtime**, KHÔNG phải coding; và ADR 0002 + provisioning-compare đã chứng minh **gate thật KHÔNG phải cờ 38B đọc được nào** (mọi cờ 38B giống hệt giữa xe 138 và xe 162). `0x38B00015`/`0x38B00030` là họ *feedback/config đọc-only* — **KHÔNG có `*_SET` để ghi CONFIG**; ghi thẳng `0x38B00030` đã bị xe REJECT.

5. **Đường SET thật (nếu muốn mở HUD-nav) = coding tool BYD ngoài xe:** OBD-II → instrument/HUD ECU → UDS `DiagnosticSessionControl(0x10)` + `SecurityAccess(0x27)` + `WriteDataByIdentifier(0x2E)` để re-code `40d` từ 138 → provisioning kiểu 162. **KHÔNG** qua app/adb/dadb/settings/prop. Cờ coding cụ thể **chưa xác định** (việc coding XE, owner quyết).

---

## (1) Có app/menu kỹ thuật (engineering/factory) nào set HUD config / variant coding không?

**Trả lời: KHÔNG (trong toàn bộ image đã decompile).**

### BydDevelopmentTools — nó thực sự làm gì
Package `com.byd.byddevelopmenttools`. Bề mặt:
- **Activities:** `JumpActivity`, `MappingActivity`, `ObdDataActivity`, `RepairModeActivity`, `RollbenchModeActivity`, `ScanCodeActivity`, `LogControlAndTestToolsActivity`.
- **Services:** `RepairModeService`, `RollbenchModeService`. **Broadcast:** `RebootBroadcastReceiver`.

| Thành phần | Chức năng thật (evidence) | Có coding HUD? |
|---|---|---|
| `ObdDataActivity` | OBD/emissions **readiness ID-mapping** (IUMPR): `MAP_TYPE 1=network map / 2=id map`, loop-count, ignition-cycle. String TQ "是否确认停止映射" (xác nhận dừng mapping). Gọi `a.a.a.l0.b.i(ctx).s(...)`. | ❌ |
| `RepairModeService` | Bật/tắt **维修模式 (repair mode)**: `SystemProperties.set("persist.sys.repair_mode.enable", "false")` + broadcast `com.byd.cloudmanager.receiver` (APPID 24, cmd 1, `repair_mode`). | ❌ (prop repair, không liên quan HUD) |
| `RollbenchModeService` | **滚动台模式 (dyno/rollbench)**: bắt tay với IPB qua `a.a.a.k0.b`. | ❌ |
| `LogControlAndTestToolsActivity` | Log/test tool: `tcpdump`, `IAdbManager`, `ICloudRemoteControlService`, `SystemProperties`, đọc version `BYDAutoVersionDevice`. | ❌ |

**Bằng chứng grep:** `grep -riE "(hud|coding|variant|writeData|0x2E|WriteDataByIdentifier|38B000|4C10E0|configItem|40d)"` trên `sources/com/byd/byddevelopmenttools/` → **0 khớp trong app-code** (chỉ `androidx/.../LinkifyCompat.java` + `R.java`).

### vehiclesettings — HUD UI chỉ ĐỌC config, KHÔNG code
`com.byd.vehiclesettings.hud.*` (DEX `ea75450d`):
- `HudFuncVisibleUtils.isHudWMode()/isHudArMode()` → `HalGetter.get(BYDAutoSettingDevice, SET_HUD_CONFIG /*0x38B00015*/)` — **chỉ đọc** để quyết định có hiện màn HUD settings không (1=W, 2=AR, khác=ẩn). *Không bao giờ ghi 0x38B00015.*
- Các model **ghi** chỉ là **user-toggle** (không phải coding): `HudSwitchModel.setHudSwitchState` → `SET_HUD_SWITCH_SET 0x4C10E023` (1/2); `HudOptionDisplayModel.setHudNavigationState` → `SET_DYNAMIC_NAVI_FUNCTION_STATUS_SET 0x4C10E03A` (1/2); `setHudOptionDisplayState` → `SET_SAFE_DRIVING_ASSIST_STATUS_SET 0x4C10E030` (1/2).

**Kết luận (1):** không có menu kỹ thuật Android set HUD-config/variant. Variant coding = máy chẩn đoán BYD ngoài (OBD/UDS), không có trong image.

---

## (2) DID/UDS hay feature-id nào GHI được coding HUD (khác đọc-only 0x38B00015)?

### Bảng feature-id HUD (Setting.java + Instrument.java)

Họ **`0x38B0xxxx` = FEEDBACK/CONFIG đọc-only từ MCU** — KHÔNG có counterpart `*_SET`:

| Hex | Tên | Vai trò |
|---|---|---|
| `0x38B00015` | `SET_HUD_CONFIG` | đọc-only: 0=no HUD / 1=W / 2=AR (gate hiện UI) |
| `0x38B0001C` | `SET_HUD_SWITCH_STATUS_FEEDBACK` | đọc-only trạng thái switch |
| `0x38B00028` | `SET_DYNAMIC_NAVI_FUNCTION_STATUS_FEEDBACK` | đọc-only nav-content |
| `0x38B0001E` | `SET_SAFE_DRIVING_ASSIST_STATUS_FEEDBACK` | đọc-only ADAS |
| `0x38B00030` | `INSTRUMENT_HUD_NAVIGATION_MAP_CONFIG` | **cờ provisioning self-learn** (đọc-only; ghi bị REJECT) |
| `0x38B0002E` | `INSTRUMENT_HUD_NAVIGATION_MAP_STATUS` | đọc-only runtime status (2=ON) |

Họ **ghi được (SET)** liên quan HUD:

| Hex | Tên | Ghi | Ghi chú |
|---|---|---|---|
| `0x4C10E023` | `SET_HUD_SWITCH_SET` | 1/2 | công tắc HUD tổng, KHÔNG phải coding |
| `0x4C10E025` | `SET_HUD_MODE_SET` | — | mode |
| `0x4C10E03C` | `SET_HUD_MODE_CHOICE_SET` | — | mode choice |
| `0x4C10E03A` | `SET_DYNAMIC_NAVI_FUNCTION_STATUS_SET` | 1/2 | cờ "hiện nav-content trên HUD" |
| `0x4C10E030` | `SET_SAFE_DRIVING_ASSIST_STATUS_SET` | 1/2 | cờ ADAS-on-HUD |
| **`0x32B1102E`** | **`INSTRUMENT_HUD_NAVIGATION_MAP_SET`** | **2=ON/1=OFF** | **gần nhất với "bật nav-HUD"**; ghi bởi OEM `Hud00600401300000.setState` (`DiCarSetter.set("0x32B1102E", on?2:1)`) |

**Điểm mấu chốt:**
- **KHÔNG có `SET_HUD_CONFIG_SET`** trong bảng (trên trim này). `SET_FSE_HUD_CONFIG_SET 0x4C50000B` chỉ có ở nền tảng **FSE** khác, không áp cho Seal DiLink3.
- `0x38B00030` (config) chỉ có `*_STATUS 0x30100030` (đọc-only), **không có SET**; ghi thẳng `0x38B00030` đã bị **REJECT** trên xe (ADR 0002). `Hud00600401300000.readSelfLearnState()` = `get("0x38B00030") == 1` (đọc-only, self-learn chỉ mirror MCU state vào cache).
- **UDS trong app: KHÔNG có.** Grep toàn corpus cho `WriteDataByIdentifier|0x2E|SecurityAccess|DiagnosticSession|RoutineControl` = 0 khớp.

**Kết luận (2):** *coding* HUD (thứ quyết định) **không có feature-id ghi được nào trong Android**. Cái ghi được (`0x32B1102E`) là **toggle on/off runtime**, không phải coding, và bằng chứng xe cho thấy nó không phải gate thật. Coding thật = UDS `0x2E` từ máy ngoài. `0x32B1102E` đáng để **thử đọc/ghi on-car một lần** (chi phí thấp) nhưng KHÔNG kỳ vọng nó mở khoá — xem (5).

---

## (3) `40d` variant (138 vs 162) lưu/đọc ở đâu, ghi được không?

**Nguồn giá trị — `CarInfo.java`** (`com.byd.common.carinfo`, DEX `e23be25a`): interface hằng số compile-time của các "car auto type":
- `CAR_AUTO_TYPE_EK = 138` (= `CAR_AUTO_TYPE_0x8A`, 0x8A)
- `CAR_AUTO_TYPE_SA3EJ = 162` (= `CAR_AUTO_TYPE_SA3H_JK`, 0xA2)

**Đọc runtime — `CarModelAndTypeHelper`** → `ICarInfoManager` (DiCar Binder → DiCarServer `ICarInfoService`):
- `getVehicleId():int`, `getCarType(int):String`, `getVehicleType():String`, `getPowerType():int`, `getBrand/getManufactor/getSerialNumber/getVehicleVin/getIoTCardNumber/getDriverSeat`.
- **Toàn bộ là GETTER — KHÔNG có setter.** (Full method list `ICarInfoManager.java`: 10 getter, 0 setter; `ICarInfoService` chỉ có `TRANSACTION_getCarType` v.v., không có transaction ghi.)

**Ghi được không? → KHÔNG (từ Android).** Giá trị `40d`/car-auto-type nằm trong **ECU/instrument variant coding**, surface lên Android **read-only** qua Binder. Không có `setCarType/setVehicleId`. Chỉ đổi được bằng **variant coding tool ngoài (OBD/UDS)**.

**Bằng chứng khác biệt xe (provisioning-compare 2026-08-19):**
- owner (Seal) `40d=138`, anh em (Sealion 6) `40d=162`; `gbClientVersion=6125f` như nhau; anh em `ro.build.region=ROW`.
- Mọi cờ `38B*` đọc được **giống hệt** hai xe (kể cả `38B00030 = -2147482648` cả hai) → **cờ coding tạo ra khác biệt HUD-nav KHÔNG nằm trong 38B đọc được**.

**Kết luận (3):** `40d` là read-only từ Android; muốn đổi 138→162-equivalent phải re-code ECU qua tool BYD. Byte coding cụ thể **chưa xác định** — cần dump variant-coding đầy đủ 2 xe (việc coding XE).

---

## (4) Có settings-secure / persist prop / service call nào bật HUD nav mà firmware đọc?

**Trả lời: KHÔNG có bề mặt Android-side nào firmware dùng để bật HUD-nav.**

- **persist prop:** người ghi `persist.*` duy nhất trong corpus là `RepairModeService` → `SystemProperties.set("persist.sys.repair_mode.enable", …)` — **repair mode, không liên quan HUD**. Không tìm thấy `persist.*` nào gate HUD-nav.
- **Settings.Secure/Global:** không có key nào firmware đọc để bật HUD-nav. Quyết định HUD-nav ở **MCU/ECU (variant coding)**, không phải giá trị Android settings.
- **Service call (đường OEM thật):** `DiCarSetter.set("0x32B1102E")` qua `ICarPropertyManager.setProperties` **bên trong DiCarServer/CCS** (privileged). Còn **đường data nav thật nuôi HUD = `AmapService` → instrument-guide họ `0x43E`/`0x43F`**:
  - `0x43E0003A INSTRUMENT_SEND_NAVI_STATUS = 2`, `0x43FA1008 TARGET_NEXT_PATHNAME` (text), `0x43F02018 TRIP_INFO_MINUTE`, `0x43F0201E TRIP_REMAINING_SECOND` — kèm `GuideInfo.naviState: 1`.
  - **App mình đã ghi đúng** các id này (`BydHal.kt:284/290/312/313`) và chúng **được chấp nhận rc=0 trên CẢ hai xe**.
- **ADR 0002:** "firmware không có đường app ghi MCU coding"; ghi `0x38B00030` bị reject; self-learn chỉ mirror MCU state vào cache đọc.

**Kết luận (4):** không có prop/settings/service call Android nào là "công tắc" mà firmware tra để bật HUD-nav. Gate nằm upstream trong MCU variant provisioning. Đường data (43E/43F) đã thông và app đã ghi đúng.

---

## (5) BydDevelopmentTools/vehiclesettings require permission/uid/signature gì? App/dadb chạm được không?

### Mô hình quyền
- **vehiclesettings + CCS ghi qua HAL `android.hardware.bydauto`** (`BYDAutoSettingDevice`/`BYDAutoInstrumentDevice`) và **DiCar `ICarPropertyManager`** (trong DiCarServer). Đây là thành phần **BYD-platform**. HAL enforce quyền **theo từng feature/device-code** (ADR 0002: device codes app dùng = **1007/1023/1038/1014**).
  - Thực chứng: app third-party (ClusterNav) ghi **domestic** `0x43F01010/018` **thành công rc=0**, nhưng **oversea `0x1F7*`** + dualIcon `0x43F01030` bị **REJECT**: `no permission … with this device: 1007`. ⇒ **không phải grant tổng**; per-feature/per-device.
- **BydDevelopmentTools là app hệ thống/privileged:** dùng `android.debug.IAdbManager`, `android.os.ICloudRemoteControlService`, `android.strategyservice.StrategyManager`, `SystemProperties.set("persist.sys.*")`. Các API này đòi **system-uid / platform-signature / privileged perms** — **app side-load thường (và dadb uid=2000) KHÔNG có**.

### App/dadb chạm được không?
| Đích | App (uid app) | dadb (uid 2000) | Ghi chú |
|---|---|---|---|
| Coding `40d` (ECU) | ❌ | ❌ | chỉ UDS ngoài (OBD tester + SecurityAccess) — **không tiến trình Android nào ghi được**, dù ký platform |
| `0x38B00030` (write) | ❌ | ❌ | đã REJECT on-car; đọc-only |
| `0x32B1102E` (SET on/off) | ❓ | ❓ | **chưa thử on-car**; đi cùng HAL instrument đã nhận `0x43F01010` cho app → *có thể* rc=0, nhưng ADR 0002/compare cho thấy **không đổi kết quả** vì gate là variant coding |
| `persist.sys.repair_mode.enable` | ❌ | ❌ | cần system-uid/selinux (BydDevTools là privileged) |
| instrument-guide `0x43F01010/018` (domestic) | ✅ rc=0 | ✅ rc=0 | app đã ghi đúng — đây là đường nav data, không phải coding |

**Kết luận (5):** đường coding HUD-nav **không app/dadb nào chạm được**. `0x32B1102E` là mục **duy nhất chưa thử** đáng probe on-car (đọc trước, ghi thử 2 rồi rollback), nhưng bằng chứng nói nó không phải unlock. Coding thật = tool ngoài + SecurityAccess.

---

## Verdict Track 3 + điều kiện mở khoá (trace-den-tan-cung)

**Nếu HUD-zin nav bị gate bởi coding, đường SET nó là NGOÀI Android:**
> BYD dealer/diagnostic coding tool → OBD-II → instrument/HUD ECU → UDS `DiagnosticSessionControl(0x10)` → `SecurityAccess(0x27)` → `WriteDataByIdentifier(0x2E)` đặt lại provisioning `40d` (138 → kiểu 162 có HUD-nav). Không qua app / adb / dadb / settings / prop / feature-id nào trong system image.

- **KHÔNG** menu kỹ thuật Android nào (kể cả BydDevelopmentTools) làm variant coding.
- **KHÔNG** primitive UDS trong bất kỳ app decompiled nào.
- `40d` read-only từ Android (`ICarInfoManager` chỉ getter).
- Cờ ghi gần nhất `0x32B1102E` = toggle runtime, không phải coding, và không phải gate thật (cờ 38B giống hệt 2 xe).

**Còn mở (chưa đóng cửa):**
1. **Byte/DID coding cụ thể khác nhau giữa 138 và 162 cho HUD-nav CHƯA xác định** — không nằm trong 38B đọc được. Cần: dump variant-coding đầy đủ 2 xe bằng tool chẩn đoán, hoặc bảng tra ý nghĩa `40d` 138 vs 162, hoặc bắt UDS `0x2E` khi đại lý code xe 162. → việc **coding XE**, owner quyết.
2. **Probe rẻ đáng làm on-car:** `getraw instr 32B1102E` (đọc), rồi thử `setraw instr 32B1102E 2` + đọc lại + rollback về giá trị cũ. Nếu ghi rc=0 mà HUD vẫn không nav → xác nhận thêm rằng gate là variant coding, không phải toggle.

## Nguồn (file:line)
- `sysimg/jadx-BydDevelopmentTools/sources/com/byd/byddevelopmenttools/` — `activity/ObdDataActivity.java`, `service/RepairModeService.java` (`persist.sys.repair_mode.enable`, cloudmanager APPID 24), `service/RollbenchModeService.java`, `LogControlAndTestToolsActivity.java`.
- `decoded/ea75450d/.../com/byd/vehiclesettings/hud/utils/HudFuncVisibleUtils.java` (đọc-only 0x38B00015), `.../model/hudswitch/HudSwitchModel.java` (SET 0x4C10E023), `.../model/optiondisplay/HudOptionDisplayModel.java` (SET 0x4C10E03A / 0x4C10E030).
- `decoded/e23be25a/.../com/byd/ccs/impl/server/hud/Hud00600401300000.java` — `readSelfLearnState()` get 0x38B00030==1; `getState()` get 0x38B0002E==2; `setState()` set **0x32B1102E** 2/1.
- `decoded/e23be25a/.../com/byd/ccs/impl/hal/DiCarSetter.java` — `ICarPropertyManager.setProperties` (privileged, trong DiCarServer).
- `decoded/e23be25a/.../com/byd/common/carinfo/CarInfo.java` — 138=`CAR_AUTO_TYPE_EK`/`0x8A`, 162=`CAR_AUTO_TYPE_SA3EJ`/`SA3H_JK`/`0xA2`.
- `decoded/e23be25a/.../com/byd/common/carinfo/CarModelAndTypeHelper.java` + `com/byd/car/ICarInfoManager.java` (10 getter, 0 setter).
- `decoded/*/…/com/byd/feature/setting/Setting.java` (bảng SET_HUD_* / 0x4C10E*), `.../com/byd/feature/instrument/Instrument.java:536-539` (0x38B00030 / 0x30100030 / **0x32B1102E** / 0x38B0002E).
- Codebase: `core/src/main/kotlin/com/byd/clusternav/carexec/CarExecHudCatalog.kt` (RE 2026-07-29), `app/src/main/java/com/byd/clusternav/NavigationHudOwner.kt:28`, `docs/decisions/0002-hud-nav-coding-locked.md` (amended 2026-08-19), `docs/diagnostics/hud-provisioning-compare-2026-08-19.md`, `docs/_handoff/re-hud-track2-firmware-gate.md`.
- **Grep âm tính (quan trọng):** `WriteDataByIdentifier|0x2E|SecurityAccess|DiagnosticSession|RoutineControl|ReadDataByIdentifier` trên toàn `sysimg` + `decoded/*/jadx-auto/sources` = **0 khớp**; `hud|coding|variant|writeData|38B000|4C10E0` trên `com/byd/byddevelopmenttools` app-code = **0 khớp**.
