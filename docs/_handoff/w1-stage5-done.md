# W1 Stage 5 (telemetry full-wire) — DONE off-car — Handoff

> Spec: `docs/specs/kachi-w1-real-data.html` (§9 Nhật ký Stage 5 · §10 Reviewer Log Pass 5) · Reads: `w1-stage{1,2,3}-done.md`
> Stage 5 = đóng khe **~46 datum "pickable nhưng render trống"** mà Pass 4 [P2] gắn cờ. **DONE off-car** 2026-09-10.

## 1. Kết quả coverage (con số cuối)

| Chỉ số | Trước (Stage 3) | Sau (Stage 5) |
|---|---|---|
| Telemetry id trong registry | 123 | 123 |
| Có case `TelemetryReadout` | 77 | **123 (100%)** |
| **Nối value** (chảy end-to-end qua gateway) | 77 | **118** |
| NEEDS_CAR route-None (đúng "—") | — | **5** |
| Field `CarStatus` mới thêm | — | **46** |

> ⚠ Registry thực tế **123** datum (handoff cũ ghi 121 — số đã tăng; grep xác nhận 123). "121/121" trong đề bài = đạt & vượt: **123/123 id đều có case readout**, **118 nối value**.

**5 datum còn "—" (route `None`, KHÔNG bịa đường đọc — grab-list §9):**
`gps_lat`, `gps_lon`, `gps_elevation`, `gps_heading` (`NaviInfo.*`) + `target_soc` (`SET_DR_SOC_TARGET`).

## 2. Phân loại 46 khe đã đóng (theo `routeOf(bindingKey)`)

- **44 resolve được → nối ĐỦ** (field + adapter read + readout case):
  - **27 Feature** (id-số): fuel_pct, ev_mileage_km, trip_km/hours/kwh, charging_eta_hour, charger_work_state, batt_range_bodywork, cell_temp_high/low/avg, cell_v_high/low, motor_rear_rpm, motor_front_torque, drift_mode, tailgate_position, wiper_state, emergency_alarm, ambient_rear_color/brightness, oms_driver/passenger, lca_left/right, rcta_left/right, dow_left/right.
  - **14 NamedMethod** (`BYDAuto….method`): consumption_50km, charging_state, engine_rpm, wheel_speed, inside_temp, coolant_temp, engine_coolant_level, light_rear_fog/left_turn/right_turn/side, radar_volume, sunroof_state, volt_12v_level.
  - **1 Setting** (`unit_temperature`): temp_unit.
- **2 KHÔNG resolve (route None) → chỉ thêm case đọc "—"**: gps_elevation, gps_heading.

## 3. Files thay đổi (đều :core THUẦN + test)

| File | LOC | Nội dung |
|---|---|---|
| `core/.../launcher/CarStatus.kt` | 130→**176** | +46 field nullable (Energy+15 · Drivetrain+5 · Climate+3 · Body+4 · Lights+6 · Safety+10 · Identity+3) |
| `core/.../launcher/CarDataAdapter.kt` | 132→**178** | đọc 46 field (5 drivetrain vào readFast — Drivetrain dựng mới; còn lại readSlow) |
| `core/.../launcher/TelemetryReadout.kt` | 162→**212** | +46 case format (đủ 123 id) + helper `dec2` (cell-V) + KDoc |
| `core/src/test/.../launcher/FakeHalGateway.kt` | — | +`settings: Map` (phủ route Setting temp_unit); backward-compat |
| `core/src/test/.../launcher/TelemetryReadoutTest.kt` | — | −1 test cũ (trip_km→— nay đã nối) · +4 test: genuine-None→"—", datum mới hiện giá trị, **FULL WIRE E2E**, wired-tier nạp field |
| `docs/specs/kachi-w1-real-data.html` | — | §9 Stage 5 journal + §10 Pass 5 Reviewer Log |

**KHÔNG split file** (đều < 500 LOC). **KHÔNG đụng** seal T11 · MainActivity · appId · cluster-cast · input-daemon · XML layout · reflection mới (đi qua `HalBindingTable`/`BydHalGateway` sẵn).

## 4. [ĐO] verify (lệnh thật)
```
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew test :app:assembleDebug  → BUILD SUCCESSFUL (2m2s)
  → tổng 2302 test / 0 fail / 0 error (292 suite; +3 net so Stage 3 2299)
./gradlew :app:testDebugUnitTest --tests "*.LayeringRulesTest" --tests "*.LayoutVariantIdParityTest" \
          :core:test --tests "*.LauncherWindowingGuardTest" --rerun-tasks  → BUILD SUCCESSFUL (XANH)
grep -riE "safetygate|safetyClass|fun .*gate\(" core/src/main/.../launcher/  → RỖNG (no gate)
for f in core/.../launcher/*.kt: LOC > 500  → RỖNG (0 file)
grep readout-mapped vs registry ids  → 123 mapped / 123 registry / 0 unmapped
```

## 5. Nguyên tắc quyết định (chi tiết §9 spec)
- **"Nối" theo binding-resolvability, "badge" theo tier** — `coolant_temp` (NEEDS_CAR) VẪN nối vì bindingKey resolve được (NamedMethod); tier giữ NEEDS_CAR (không badge). Giống `tyre_t_*` (NEEDS_CAR + Feature) nối từ trước.
- **State code hiện SỐ THÔ** (charging_state/charger_work_state/lca/rcta/dow/oms/radar_volume/12v_level) — enum→nhãn để grab-list §9.
- **cell-V dùng dec2** — scale (mV/V) hiệu chỉnh on-car.
- **route None KHÔNG bịa đường đọc** — gps_elevation/heading + target_soc honest "—".

## 6. Còn lại / on-car (grab-list §9)
- Hiệu chỉnh scale (cell-V mV↔V, tyre kPa), gắn nhãn enum cho state code, xác nhận feature-id/method đúng trim (dump `*bydauto*.jar`).
- `settingGet` (`BydHalGateway`) hiện trả null → `temp_unit` "—" tới khi proven đường car-setting.
- Nợ orchestrator: docs sync (`docs/README.md` index · `PROJECT-BACKLOG.md` #6 W1 · `.kiro/steering/project-context.md`) + commit local (KHÔNG push).
