# W1 Stage 1 (W1a) — DONE — Handoff

> Spec: `docs/specs/kachi-w1-real-data.html` · Catalog: `docs/diagnostics/kachi-capability-catalog-2026-09-10.md`
> Stage 1 = capability layer ở **:core THUẦN**, package `com.byd.clusternav.launcher`. **DONE off-car** 2026-09-10.
> Đọc file này trước khi làm Stage 2 (HAL binding + adapter + StateFlow).

## 1. Files tạo / sửa

| File | Trạng thái | Nội dung |
|---|---|---|
| `core/.../launcher/CapabilityModel.kt` | **tạo** (76 LOC) | `Domain` · `WidgetShape` · `EvidenceTier` (+ `wired`/`needsBadge`) |
| `core/.../launcher/TelemetryRegistry.kt` | **tạo** (214 LOC) | `TelemetrySpec` + `object TelemetryRegistry` (121 datum / 8 domain) |
| `core/.../launcher/CarStatus.kt` | **tạo** (130 LOC) | `data class CarStatus` (bất biến, 8 nested-class, mọi field nullable) |
| `core/.../launcher/CarCapabilities.kt` | **tạo** (47 LOC) | `Capability` + `object CarCapabilities` (VIEW derived từ 2 registry) |
| `core/.../launcher/ControlRegistry.kt` | **sửa** (225 LOC) | Mở rộng `ControlKind` (+COVER/SELECT/BUTTON) + `ControlDef` (+domain/tier/bindingKey/args) + 45 nút §B (20 nút gốc GIỮ NGUYÊN) |
| `core/src/test/.../launcher/CapabilityModelTest.kt` | **tạo** | 5 test enum |
| `core/src/test/.../launcher/TelemetryRegistryTest.kt` | **tạo** | 6 test (phủ 8 domain, size, bindingKey, unique, byId/byDomain, PROVEN) |
| `core/src/test/.../launcher/ControlRegistryExtendedTest.kt` | **tạo** | 8 test (5 kind, 20 nút gốc bất biến, defaultEnabledIds, SELECT args, domain) |
| `core/src/test/.../launcher/CarStatusTest.kt` | **tạo** | 3 test (nullable default, copy-based, radar list) |
| `core/src/test/.../launcher/CarCapabilitiesTest.kt` | **tạo** | 6 test (derived count, PROVEN/NEEDS_CAR/badge, id lạ, control) |

**KHÔNG đụng:** `WidgetRegistry.kt`/`WidgetRegistryTest.kt` (WidgetKind{LOCAL,CAR,BOARD} giữ nguyên) · `ControlRegistryTest.kt` cũ (5 test còn xanh) · seal T11 · MainActivity · appId · cast.

## 2. Shape các model (tên field · kiểu · nullable) — CONTRACT cho Stage 2/3

### `enum EvidenceTier { PROVEN, OVERDRIVE, DASHCAST, NEEDS_CAR }`
- `val wired: Boolean` = `!= NEEDS_CAR` (mọi tier khác đều có đường HAL để thử).
- `val needsBadge: Boolean` = `OVERDRIVE || DASHCAST` (badge "chưa kiểm trên xe").

### `enum Domain(val label)` — 9 giá trị
`ENERGY, DRIVETRAIN, CLIMATE, TYRES, BODY, LIGHTS, SAFETY, IDENTITY, INFOTAINMENT`
- `Domain.TELEMETRY: List<Domain>` = 8 domain đầu (KHÔNG gồm INFOTAINMENT). Chỉ để **gom nhóm panel** — KHÔNG gate.

### `enum WidgetShape { RING, GAUGE, DIAL, CARD, BOARD, STRIP, MEDIA, VALUE, BADGE }`
> ⚠ Tên `WidgetShape` (KHÔNG phải `WidgetKind` — WidgetKind đã bị `WidgetRegistry` chiếm). Field trong TelemetrySpec vẫn tên `widgetKind` (kiểu `WidgetShape`).

### `data class TelemetrySpec`
| field | kiểu | nullable | ghi chú |
|---|---|---|---|
| `id` | `String` | không | khoá snake_case, DUY NHẤT toàn cục (không trùng control id) |
| `label` | `String` | không | nhãn VI |
| `unit` | `String` | không | `""` nếu enum/bool/chuỗi |
| `domain` | `Domain` | không | 1 trong 8 TELEMETRY |
| `widgetKind` | `WidgetShape` | không | gợi ý render |
| `tier` | `EvidenceTier` | không | mức bằng chứng ĐỌC |
| `bindingKey` | `String` | không (không rỗng) | **Stage 2 map → HAL** (xem §3) |

`TelemetryRegistry`: `ALL: List<TelemetrySpec>` (121) · `byId(id)` · `byDomain(domain)` · `domains(): Set<Domain>`.

### `data class ControlDef` (mở rộng — tương thích ngược)
| field | kiểu | default | ghi chú |
|---|---|---|---|
| `id` | `String` | — | DUY NHẤT toàn cục |
| `label` | `String` | — | |
| `icon` | `String` | — | khoá icon `ic-*` |
| `kind` | `ControlKind` | — | TOGGLE/STEP/**COVER/SELECT/BUTTON** |
| `enabledByDefault` | `Boolean` | `false` | (gốc) |
| `onByDefault` | `Boolean` | `false` | (gốc, cho TOGGLE) |
| `value` | `Int` | `0` | (gốc, cho STEP) |
| `min` | `Int` | `0` | (gốc) |
| `max` | `Int` | `0` | (gốc) |
| `step` | `Int` | `1` | (gốc) |
| `domain` | `Domain` | `CLIMATE` | **MỚI** — gom nhóm panel |
| `tier` | `EvidenceTier` | `OVERDRIVE` | **MỚI** — mức bằng chứng GHI |
| `bindingKey` | `String` | `""` | **MỚI** — Stage 2 map → HAL |
| `args` | `List<String>` | `emptyList()` | **MỚI** — SELECT = danh sách nhãn option |
- `fun clamp(v): Int` giữ nguyên (chỉ áp cho STEP).
- `ControlRegistry`: `ALL` (65) · `byId(id)` · `defaultEnabledIds()` (8 nút bất biến) · `defaultDock()` · `byDomain(domain)`.

### `data class CarStatus` — snapshot bất biến, MỌI field nullable (null = chưa đọc/không có → UI "—")
8 nested data class (đều nullable, đều có default `null`):
- `energy: Energy` — soc, evRangeKm, fuelRangeKm, odometerKm, motorPowerKw, isCharging:Boolean?, chargePowerKw:Double?, chargingPct, chargingEtaMin, chargedKwh:Double?, battTempC, sohPct, targetSoc
- `drivetrain: Drivetrain` — speedKmh, accelPct, brakePct, motorFrontRpm, steeringDeg, slopeDeg, gear:String?, opMode:String?, energyMode:String?
- `climate: Climate` — pm25Level, pm25ValueUgm3, pm25Online:Boolean?, cabinTempC, outsideTempC, acOn:Boolean?, fanLevel, recircOn:Boolean?, anionOn:Boolean?
- `tyres: Tyres` — pFlKpa/pFrKpa/pRlKpa/pRrKpa:Double?, tFlC/tFrC/tRlC/tRrC:Int?
- `body: Body` — windowLfPct/RfPct/LrPct/RrPct:Int?, doorLfOpen/RfOpen/LrOpen/RrOpen:Boolean?, tailgateOpen:Boolean?, sunroofPct, sunshadePct, mirrorFolded:Boolean?, powerLevel, vehicleType:String?
- `lights: Lights` — lowBeam/highBeam/frontFog/drl:Boolean?, headlightMode:Int?, ambientOn:Boolean?, ambientColorIndex, ambientBrightness
- `safety: Safety` — seatbeltDriver/Passenger:Boolean?, childPresence:Boolean?, speedLimitWarning:Boolean?, bsdLeftLevel/RightLevel:Int?, radarZones:List<Int>?, espOn:Boolean?, mcuStatus:Int?, volt12v:Double?
- `identity: Identity` — vin:String?, keyState:String?, engineCode:String?, oilLevelPct:Int?, gpsLat/gpsLon:Double?

> Stage 2 `CarStatusRepository` build `CarStatus` bằng copy-based per nhịp; field không đọc được để `null`.

### `data class Capability(id, wired:Boolean, tier:EvidenceTier)` + `object CarCapabilities`
- `TELEMETRY: Map<String,Capability>` · `CONTROL: Map<String,Capability>` · `ALL = TELEMETRY + CONTROL` (id disjoint → size = tổng).
- `tierOf(id)`, `isWired(id)`, `needsBadge(id)`, `of(id)` — id lạ → null/false (an toàn).
- **Derived** hoàn toàn từ tier trong 2 registry (R1) — Stage 3 UI badge/mờ đọc từ đây.

## 3. `bindingKey` convention (Stage 2 map → HAL) — QUAN TRỌNG
- **named-method proven** → `"SimpleDeviceClass.methodName"`, vd `"BYDAutoStatisticDevice.getElecPercentageValue"`, `"BYDAutoSpeedDevice.getCurrentSpeed"`, `"BYDAutoBodyworkDevice.setBodyWindowCtrlState"`. Stage 2 resolve SimpleClass → FQN (`android.hardware.bydauto.*`), gọi qua `BydHal.device()`/`callNamedInt`.
- **feature-id số (Overdrive raw)** → chuỗi decimal, vd `"501219340"` (AC_WIND_LEVEL_SET), `"1246777400"` (STAT_ELEC_PERCENTAGE). Stage 2 dùng `set(int[]{id}, EventValue)` / `get(int[]{id})` kiểu `resolveOrFallback("Nested.FIELD", numeric)`.
- **car-setting key** → khoá setting, vd `"unit_temperature"`.
- **command wrapper / chưa có id số** → tên gợi ý (vd `"StartChargingNowCommand"`, `"ADAS_AVH_STATE"`, `"BODYWORK_CMD_HOOD"`) — tier thường `NEEDS_CAR`, Stage 2 để `unavailable` tới khi grab-list §9 đóng.
- **local Android (không HAL)** → vd `"AudioManager.setStreamVolume"` (vol), `"AutoContainer.sendInfo"` (cast/HUD dashcast). Đây là đường :app-local, KHÔNG qua BydHal.
- **Sentinel unavailable** (Stage 2 xử lý): rc `-2147482648` (NOT_PROVISIONED) / `-2147482645` (INVALID) ⇒ coi như không có trên trim → UI "—".

## 4. Số test
- `./gradlew :core:test` = **978 / 978 XANH** (0 fail, 0 error). Gói `launcher.*` = **100 test** (gồm 5 file test mới: Capability/Telemetry/ControlRegistryExtended/CarStatus/CarCapabilities + test cũ ControlRegistry/WidgetRegistry còn xanh).
- `LayeringRulesTest` (:app) + `CoreIsolationTest` (:core) = **XANH** (:core thuần, 0 android import).
- `:app:compileDebugKotlin` = **sạch** (thêm ControlKind/field KHÔNG vỡ `ControlDockView`).

## 5. Verify đã chạy (lệnh thật)
```
./gradlew :core:test                                             → BUILD SUCCESSFUL, 978/978
grep -rn "SafetyGate\|safetyClass" core/src/main                 → EMPTY (GOOD)
grep -rnE "^import (android|androidx|dadb)\." core/.../launcher/  → EMPTY (GOOD)
./gradlew :app:testDebugUnitTest --tests "*.LayeringRulesTest"   → BUILD SUCCESSFUL
```
JDK: `/opt/homebrew/opt/openjdk@17` (đặt `JAVA_HOME` trước khi chạy gradle).

## 6. Deviation so với plan (đã ghi spec §9)
1. **`WidgetShape`** thay tên `WidgetKind` (né redeclaration — WidgetKind{LOCAL,CAR,BOARD} cũ bị test khoá). Field TelemetrySpec vẫn tên `widgetKind: WidgetShape`.
2. **Mở rộng `ControlDef`** (không tạo `ControlSpec` song song) — ít vỡ nhất, giữ 20 nút + 5 test cũ.
3. **`Domain` có 9 giá trị** (8 telemetry + INFOTAINMENT cho control panel). Test "phủ 8 domain" xét `Domain.TELEMETRY`.
4. **Đổi 2 id control trùng telemetry**: `target_soc→target_soc_set`, `mirror_fold→mirror_fold_btn` (id toàn cục duy nhất cho `CarCapabilities.ALL`).

## 7. Cho Stage 2 (gợi ý)
- `HalBindingTable` (:app) khoá theo `id`, đọc `bindingKey` từ registry để chọn đường named-method vs feature-id.
- `CarDataAdapter` build `CarStatus` (map telemetry id → field CarStatus tương ứng — id ↔ field name gần khớp, xem §2).
- `CarControlAdapter` route theo `ControlDef.kind` (TOGGLE/STEP/COVER/SELECT/BUTTON) + `bindingKey` + `args`. **KHÔNG gate**.
- Window/tyre/light per-index: bindingKey là method chung; index suy từ id suffix (`window_lf`→1, `tyre_p_rr`→RR…).
