# Kachi MAXIMUM Capability Catalog — BYD DiLink telemetry + controls

> ⚠ **2026-09-17 — (V) FEATURE-FILTER**: owner gỡ thêm **19 mã chấm NO** (12 thông tin + 7 nút — cụm SẠC, chế độ lái, gập gương, chế độ drift, trạng thái nguồn MCU, chìa Bluetooth, tự-đóng-kính-khi-mưa) và **ẩn 8 ô lốp lẻ** khỏi bộ chọn. **Trạng thái hôm nay: 154 chức năng** = **47 nút · 100 thông tin · 4 gói · 3 hành động**; `status-by-id.json` **145** mục. Mọi con số dưới đây (kể cả banner ADAS-PURGE ngay dưới) là **LỊCH SỬ ĐO** — xem `docs/diagnostics/feature-filter-2026-09-16.md`.

> ⚠ **2026-09-16 — owner gỡ TOÀN BỘ ADAS/an toàn khỏi launcher** (backlog (N) ADAS-PURGE). Mọi mục ADAS/an toàn và mọi con số dưới đây chỉ là **LỊCH SỬ ĐO**, giữ nguyên làm bằng chứng. Trạng thái lúc đó (2026-09-16): **167 chức năng** (54 nút · 106 thông tin · 4 gói · 3 hành động), **không còn** nút/datum ADAS/an toàn nào — xem `docs/diagnostics/adas-purge-2026-09-16.md`.

> **Status**: Current · **Date**: 2026-09-10 · **Type**: Diagnostics (RE capability harvest, cited to source symbol) ·
> **Purpose**: The single, exhaustive menu of *everything a BYD DiLink launcher can read and actuate*, harvested from
> the two most complete open-source projects (Overdrive-release, byd-dashcast) plus ClusterNav's own proven-on-car
> paths. Drives the **maximum Kachi launcher plan** (all telemetry widgets + all action buttons). This is RESEARCH —
> no code. Owner directive: *Dudu + Overdrive already figured out what is doable on-car; LEARN from them, do not
> re-reverse-engineer.*

> **(VI)** Đây là bản kê ĐẦY ĐỦ mọi thứ launcher đọc được (widget) + điều khiển được (nút), gom từ Overdrive +
> byd-dashcast + ClusterNav. Dùng để dựng kế hoạch launcher tối đa. Chỉ nghiên cứu, KHÔNG code.

## Evidence tiers (read the tier before trusting a row)
- **PROVEN-ClusterNav** — actually executed on the owner's car (Seal, DiLink3) inside ClusterNav 2.0. Highest trust.
- **OVERDRIVE** — API/id read from Overdrive-release source (MIT). Overdrive runs it on DiLink 3+5, but NOT verified on Kachi's trim.
- **DASHCAST** — API/opcode read from byd-dashcast source (MIT). Cluster-cast + HUD-CAN technique.
- **NEEDS-CAR** — a method name, numeric id, or per-trim quirk that can only be confirmed on the physical car.

## Sources harvested (read 2026-09-10)
- **Overdrive-release** — MIT © 2026 Yash Srivastava · `github.com/yash-srivastava/Overdrive-release`. Files: `app/.../byd/BydFeatureIds.java` (≈187 numeric ids), `app/.../mqtt/VehicleControlCatalog.java` (≈59 control entities + 16 tier-3), `app/.../mqtt/TelemetryFieldCatalog.java` (≈131 telemetry fields), `app/.../byd/light/LightConstants.java` (31-colour ambient palette), `app/.../byd/{BydConstants,BydCarSettings,BydDataCollector}.java`, `app/.../byd/bodywork/BodyworkConstants.java`, and the `stubs-bydauto/android/hardware/bydauto/*/BYDAuto*Device.java` HAL stubs (16 devices).
- **byd-dashcast** — MIT © 2026 Cedric Carre · `github.com/Kiroha/byd-dashcast`. Files: `system/CanBusController.java` + `system/CanNavigationBatches.kt` + `proxy/daemon/CanWriteVerbs.java` (CAN HUD/nav ids), `byd/fbs/naviInfo/NaviInfo.java` (FlatBuffers nav struct), `cluster/display/ClusterManager.kt` (AutoContainer opcodes).
- **ClusterNav 2.0** (this repo) — `app/.../modules/hal/BydHal.kt`, `core/.../comfort/*`, `core/.../body/*`, prior RE docs in `docs/diagnostics/`.

## How the harvest maps to the two access mechanisms Kachi already owns
1. **Reflection in-process (HAL reads + most writes)** — `BydHal.device(FQN,ctx)` → `getInstance(Context)` → either named method (`setSeatVentilatingState`) or `set(int[]{featureId}, BYDAutoEventValue)`. This is ClusterNav's proven path and the target for every telemetry read + comfort/body/light write below.
2. **CAN via uid-2000 daemon (HUD / cluster-nav registers)** — the `INSTRUMENT_*`/`SETTING_NAVI_SCREEN_STATUS` writes need the daemon path on dashcast; ClusterNav writes the same family in-process via `NavigationHudOwner`. Section C lists these separately.

Counts: **≈112 readable telemetry data points · ≈75 controllable actuators · ≈213 numeric feature-ids** catalogued below.

---

# A. TELEMETRY (read) — every datum available for a widget

> Source of the field list: `TelemetryFieldCatalog.java` (Overdrive's HA discovery table — the canonical "what can be
> published" set) cross-referenced with the HAL getter method (device stub) or numeric feature-id that produces it.
> Units/ranges are from the catalog `add(...)` metadata. `t/f state` = enum. Tier is the *read* tier.

## A1. Energy / charge / battery
| Datum | HAL read (device.method) or feature-id | Unit / range | Tier | Source |
|---|---|---|---|---|
| State of charge (SOC) | `BYDAutoStatisticDevice.getElecPercentageValue()` · fid `STAT_ELEC_PERCENTAGE` 1246777400 | % 0–100 | PROVEN-ClusterNav (device family) / OVERDRIVE | `stub statistic`; `TelemetryFieldCatalog:add("soc")`; `BydFeatureIds.STAT_ELEC_PERCENTAGE` |
| EV range | `BYDAutoStatisticDevice.getElecDrivingRangeValue()`/`getEVMileageValue()` · fid `STAT_ELEC_DRIVING_RANGE` 1246765118 | km | OVERDRIVE | `stub statistic`; `add("ev_range_km")` |
| Fuel range (PHEV) | fid `STAT_FUEL_DRIVING_RANGE` 1246773304 | km | OVERDRIVE | `add("fuel_range_km")` |
| Fuel level (PHEV) | fid `STAT_FUEL_PERCENTAGE` 1246785600 | % | OVERDRIVE | `add("fuel_pct")` |
| Odometer / total mileage | `BYDAutoStatisticDevice.getTotalMileageValue()` (float) · fid `STAT_TOTAL_MILEAGE` 1246765072 | km total | OVERDRIVE | `stub statistic`; `add("odometer")` |
| EV mileage (lifetime elec-driven) | fid `STAT_EV_DRIVING_MILEAGE` 1146093608 / `STAT_MILEAGE_EV` 1246773284 | km | OVERDRIVE | `BydFeatureIds`; `add("ev_mileage_km")` |
| Trip distance / time / energy | fid `INSTRUMENT_2IN1_CURRENT_JOURNEY_DRIVE_MILEAGE` 1246801948 · `..._DRIVE_TIME` 1246801938 · `STAT_THIS_TRIP_ELEC_CONSUMPTION` 1246801976 | km / h / kWh | OVERDRIVE | `BydFeatureIds`; `add("trip_km"/"trip_hours"/"trip_kwh")` |
| Consumption (last 50 km) | `BYDAutoInstrumentDevice.getLast50KmPowerConsume()` | kWh/100km | OVERDRIVE | `stub instrument`; `add("consumption_50km")` |
| Total lifetime elec / fuel consumption | fid `STAT_*` family | kWh / L | OVERDRIVE | `add("total_elec_con"/"total_fuel_con")` |
| Motor power (drive) | `ENGINE_POWER` 339738656 | kW | OVERDRIVE | `BydFeatureIds`; `add("power","Motor Power")` |
| Charging: is-charging / DC-fast / V2L | `BYDAutoPowerDevice.isCharging()`; `CHARGING_CHARGER_WORK_STATE` 666894346 | bool / enum | OVERDRIVE | `stub power`; `add("is_charging"/"is_dcfc"/"charging_v2l")` |
| Charge power | `BYDAutoChargingDevice.getChargePower()` · fid `INSTRUMENT_CHARGING_CHARGE_POWER_DD` 842006552 | kW | OVERDRIVE | `stub charging`; `add("charge_power")` |
| Charge % / ETA h / ETA min | fid `INSTRUMENT_CHARGING_CHARGE_PERCENT_DD` 842006544 · `..._REST_HOUR_DD` 842006568 · `..._REST_MINUTE_DD` 842006576; `BYDAutoChargingDevice.getRemainChargingTime()` | % / h / min | OVERDRIVE | `BydFeatureIds`; `stub charging`; `add("charging_pct"/"charging_eta_*")` |
| Charged energy this session | `CHARGING_CHARGE_CAPACITY` 666894360 (double, 0–131.07 kWh) | kWh | OVERDRIVE | `BydFeatureIds`; `add("charging_capacity_kwh")` |
| Charger / charging state, gun, type, mode | `BYDAutoChargingDevice.getChargeState()`; `CHARGING_BATTERY_DEVICE_STATE` 876609560 | enum | OVERDRIVE | `stub charging`; `add("charging_state"/"charger_state"/"charging_gun"/"charging_type"/"charging_mode")` |
| Battery temp (pack) | `BYDAutoChargingDevice.getBatteryTemp()` · stat `STAT_AVERAGE_BATTERY_TEMP` 1148190776 (intVal−40=°C) | °C | OVERDRIVE | `stub charging`; `add("batt_temp")` |
| Cell temp max/min/avg/delta | `STAT_HIGHEST_BATTERY_TEMP` 1148190752 · `STAT_LOWEST_BATTERY_TEMP` 1148190736 · `STAT_AVERAGE_BATTERY_TEMP` 1148190776 (each intVal−40) | °C | OVERDRIVE | `BydFeatureIds`; `add("cell_t_*")` |
| Cell voltage max/min/delta, HV pack V | `STAT_HIGHEST_BATTERY_VOLTAGE` 1147142192 · `STAT_LOWEST_BATTERY_VOLTAGE` 1147142160 (intVal/1000=V) | V | OVERDRIVE | `BydFeatureIds`; `add("cell_v_*"/"hv_pack_v")` |
| Battery health SOH (OEM) | `STAT_BATTERY_HEALTHY_INDEX` 1145045032 | % | OVERDRIVE | `BydFeatureIds`; `add("soh_oem")` (est `soh` derived) |
| Usable capacity / capacity Ah | derived from SOC + energy counters | kWh / Ah | OVERDRIVE | `add("capacity"/"capacity_ah")` |
| Target SOC (charge target) | setting `SET_DR_SOC_TARGET` (read-back); `SOC_TARGET_MIN=15 / MAX=70` | % | OVERDRIVE | `BydDataCollector.SOC_TARGET_MIN/MAX`; `add("target_soc")` |
| Bodywork battery range / metric | `BODYWORK_BATTERY_RANGE` 300941336 (0–1016 km) · `BODYWORK_BATTERY_METRIC` 300941320 | km | OVERDRIVE | `BydFeatureIds` |

## A2. Drivetrain / speed / motion
| Datum | HAL read | Unit / range | Tier | Source |
|---|---|---|---|---|
| Vehicle speed | `BYDAutoSpeedDevice.getCurrentSpeed()` | km/h | **PROVEN-ClusterNav** (hero km/h `SpeedProvider`) | `stub speed`; `add("speed")` |
| Accelerator / brake pedal depth | `BYDAutoSpeedDevice.getAccelerateDeepness()` / `getBrakeDeepness()` | % 0–100 | OVERDRIVE | `stub speed`; `add("accel_pct"/"brake_pct")` |
| Front / rear motor RPM | `ENGINE_FRONT_MOTOR_SPEED` 1141899272 (negated) · `ENGINE_REAR_MOTOR_SPEED` 621805576 | rpm | OVERDRIVE | `BydFeatureIds`; `add("motor_front_rpm"/"motor_rear_rpm")` |
| Front motor torque | `ENGINE_FRONT_MOTOR_TORQUE` 1141899288 (double, negated) | Nm | OVERDRIVE | `BydFeatureIds`; `add("motor_front_torque")` |
| ICE engine RPM (PHEV) | `BYDAutoEngineDevice.getEngineSpeed()` · `ENGINE_SPEED` 339738642 | rpm | OVERDRIVE | `stub engine`; `add("engine_rpm")` |
| Steering wheel angle | `BYDAutoBodyworkDevice.getSteeringWheelValue(1)` (±780°) | ° | **PROVEN-ClusterNav** (dead-reckon RE) | `stub bodywork BODYWORK_STEERING_WHEEL_ANGEL_MIN/MAX`; `add("steering_deg")` |
| Wheel speed (per wheel) | `BYDAutoSpecialDevice.getWheelSpeed(area)` | km/h | PROVEN-ClusterNav | `BydHal.SPECIAL` (dead-reckon RE) |
| Road slope | `SENSOR_AUTO_SLOPE` 573571116 (`BYDAutoSensorDevice`) | ° | OVERDRIVE | `BydFeatureIds`; `add("slope_deg")` |
| Gear position | `BYDAutoGearboxDevice.getGearboxState()`/`getGearboxCode()` | enum P/R/N/D | OVERDRIVE | `stub gearbox`; `add("gear")` |
| Drive/operation mode | `SETTING_TARGET_DRIVING_MODE` 1272971280; energy `getOperationMode` | enum normal/eco/sport/snow | OVERDRIVE | `BydFeatureIds`; `add("op_mode")` |
| Energy mode (EV/HEV) | energy device (`ENERGY_MODE_EV=1/HEV=3/FUEL=4/KEEP=5/STOP=0`) | enum | OVERDRIVE | `BydDataCollector.ENERGY_MODE_*`; `add("energy_mode")` |
| Drift mode | `ENGINE_DRIFT_MODE_SWITCH_STATUS` 681574694 | bool | OVERDRIVE | `BydFeatureIds`; `add("drift_mode")` |

## A3. Climate / air quality
| Datum | HAL read | Unit / range | Tier | Source |
|---|---|---|---|---|
| **PM2.5 level (cabin)** | `BYDAutoPM2p5Device.getPM2p5Level()[0]` | 1=Excellent…5=Heavy…6=Serious | **PROVEN-ClusterNav** (on-car 2026-09-08) | `Pm25FilterApplier.readLevel`; `byd-pm25-airclean-RE` |
| **PM2.5 value (raw)** | `BYDAutoPM2p5Device.getPM2p5Value()[0]` | µg/m³ 0–3000 | **PROVEN-ClusterNav** | `Pm25Filter.kt` |
| PM2.5 inside / outside | catalog fields | µg/m³ | OVERDRIVE | `add("pm25_inside"/"pm25_outside")` |
| PM2.5 sensor online | `BYDAutoPM2p5Device.getPM2p5OnlineState()` | 0/1 | PROVEN-ClusterNav | `byd-pm25-airclean-RE §1` |
| Cabin / inside temp | `BYDAutoAcDevice.getTemprature(i)` · `AC_TEMP_INSIDE` 1031798832 | °C | OVERDRIVE | `stub ac`; `add("cabin_temp"/"inside_temp")` |
| Outside temp | `BYDAutoInstrumentDevice.getOutCarTemperature()` | °C | OVERDRIVE | `stub instrument`; `add("ext_temp")` |
| Coolant temp (PHEV) | catalog field | °C | OVERDRIVE | `add("coolant_temp")` |
| A/C on-state / cycle / wind mode / fan level | read-back of AC set fids (`AC_CYCLE_MODE_SET` 501219355, `AC_WIND_LEVEL_SET` 501219340) | enum / 0–7 | OVERDRIVE + Dudu (getAcStartState/WindLevel/CycleMode proven) | `add("ac_on"/"ac_cycle"/"ac_wind"/"ac_fan")`; `dudu-launcher-hal-RE` |
| Temp unit (C/F) | car-setting `unit_temperature` | 0=C/1=F | OVERDRIVE | `BydCarSettings.registry`; `add("temp_unit")` |
| Anion (negative-ion) state | `PM25_ANION_STATE` 1033895958 | on/off | OVERDRIVE | `BydFeatureIds` |

## A4. Tyres (TPMS)
| Datum | HAL read | Unit | Tier | Source |
|---|---|---|---|---|
| Tyre pressure FL/FR/RL/RR | `BYDAutoTyreDevice.getTyrePressure{Left,Right}{Front,Rear}()` (float) | kPa | **PROVEN-ClusterNav** (`BydHal.TYRE` callGetter) | `stub tyre`; `add("tyre_p_*")` |
| Tyre temp FL/FR/RL/RR | fid `INSTRUMENT_{LF,RF,LB,RB}_TYRE_TEMPERATURE` 1246797848/860/872/884 | °C (UNAVAILABLE until TPMS transmits) | OVERDRIVE / NEEDS-CAR | `BydFeatureIds.INSTRUMENT_TYRE_TEMP_IDS`; `add("tyre_t_*")` |
| TPMS system / temp state | catalog enum | enum | OVERDRIVE | `add("tyre_system_state"/"tyre_temp_state")` |

## A5. Body / doors / windows / mirror
| Datum | HAL read | Unit / enum | Tier | Source |
|---|---|---|---|---|
| Window state (open/closed) per window | `BYDAutoBodyworkDevice.getWindowState(w)` w=1..4 | 0=closed/1=open | **PROVEN-ClusterNav** ([ĐO] getWindowOpenPercent=98) | `bodywork-window-trunk-RE`; `stub BODYWORK_STATE_*` |
| Window open percent per window | `BYDAutoBodyworkDevice.getWindowOpenPercent(w)` | 0–100% | **PROVEN-ClusterNav** | `bodywork-window-trunk-RE §CỬA SỔ` |
| Window control-permitted | `BYDAutoBodyworkDevice.getWindowPermitState()` | bool | OVERDRIVE/ClusterNav | `bodywork-window-trunk-RE` |
| Door status LF/RF/LR/RR | `BODY_LF_DOOR_STATUS` 692060176 …`RR` 692060179; `getDoorState(w)` | open/closed | OVERDRIVE | `BydFeatureIds.BODY_*_DOOR_STATUS`; `BodyworkManager` |
| Tailgate / back-door status + position | `TAILGATE_BACK_DOOR_STATUS` 692060181 · `TAILGATE_BACKDOOR_POSITION` 1074790456 · `BYDAutoBodyworkDevice.getHatchDoorStatus()` | enum / % | OVERDRIVE / PROVEN-ClusterNav (close=2) | `BydFeatureIds`; `bodywork-window-trunk-RE §CỐP` |
| Sunroof state / position | `BYDAutoBodyworkDevice.getSunroofPosition/State` (SUNROOF_POSITION_* enum) | enum / % | OVERDRIVE | `stub bodywork SUNROOF_*`; `add("sunroof_state"/"sunroof_pos")` |
| Sunshade percent | `BODY_SUNSHADE_PANEL_PERCENT` 1101004816 | % | OVERDRIVE | `BydFeatureIds`; `add("sunshade_pct")` |
| Mirror fold state | `MIRROR_REARVIEW_STATE` 960495624 | folded/unfolded | OVERDRIVE | `BydFeatureIds` |
| Wiper state / rain-close config | `WIPER_REAR_AREA_STATE` 1196425226 · `WIPER_FRONT_LEVEL` 321912848 · `getRainCloseWindow()` | enum | OVERDRIVE | `BydFeatureIds`; `add("wiper_state")` |
| Power level (ign) | `BYDAutoBodyworkDevice.getPowerLevel()` | 0=off/1=acc/2=on | OVERDRIVE | `stub BODYWORK_POWER_LEVEL_*`; `add("power_level")` |
| Vehicle model / type / VIN | `BYDAutoBodyworkDevice.getType()` (AUTO_TYPE_* table, e.g. EK=138/EL=96) · `getAutoVIN()` | model-code / string | OVERDRIVE / PROVEN-ClusterNav (isHan string-match) | `stub bodywork AUTO_TYPE_*`; `add("vin")` |
| Emergency alarm | `BODYWORK_EMERGENCY_ALARM` 692060190 | enum | OVERDRIVE | `BydFeatureIds` |

## A6. Lights
| Datum | HAL read | Enum | Tier | Source |
|---|---|---|---|---|
| Low / high beam | `BYDAutoLightDevice.getLightStatus(2/3)` · fids `LIGHT_LOW_BEAM` 950009866 / `LIGHT_HIGH_BEAM` 950009868 | 0/1 | OVERDRIVE | `stub light LIGHT_LOW_BEAM=2/HIGH_BEAM=3`; `add("light_low_beam"/"light_high_beam")` |
| Front / rear fog | `getLightStatus(6/7)` | 0/1 | OVERDRIVE | `stub light LIGHT_FRONT_FOG=6/REAR_FOG=7`; `add("light_front_fog"/"light_rear_fog")` |
| Left / right turn signal | `getLightStatus(4/5)`; `getTurnLightFlashState()` | enum | OVERDRIVE | `stub light`; `add("light_left_turn"/"light_right_turn")` |
| Side / foot lamp | `getLightStatus(1/8)` | 0/1 | OVERDRIVE | `stub light LIGHT_SIDE=1/LIGHT_FOOT=8` |
| Hazards | `getLightStatus(8)` ⚠ position 8 = FOOT; readback unverified | 0/1 | NEEDS-CAR | `VehicleControlCatalog` hazard note; `add("light_hazard")` |
| DRL auto state | `LIGHT_DAY_RUNNING_LIGHT_AUTO_STATE` 985661476; `getLightAutoStatus()` | 0/1 | OVERDRIVE | `BydFeatureIds`; `add("light_drl")` |
| Headlight selector feedback | `INSTRUMENT_HEADLIGHT_CONTROL_FEEDBACK` 1011875880 | 1=off/2=auto/3=parking/4=low | OVERDRIVE | `BydFeatureIds` |
| Ambient main-switch state | `LIGHT_ATMOSPHERE_MAIN_SWITCH_STATUS` 1060110406 | 0/1 | OVERDRIVE | `BydFeatureIds`; `add("ambient_enabled")` |
| Ambient colour (front/rear) | `LIGHT_AMBIENT_FRONT_COLOR` 1121976336 / `..._REAR_COLOR` 1121976343 | 1-based index into 31-colour palette | OVERDRIVE | `BydFeatureIds`; `LightConstants.AMBIENT_COLOURS`; `add("ambient_colour")` |
| Ambient brightness (front/rear) | `LIGHT_AMBIENT_FRONT_BRIGHTNESS` 1121976328 / `..._REAR_BRIGHTNESS` 1121976332 | 0–5 level per zone | OVERDRIVE | `BydFeatureIds` |

## A7. Safety / ADAS / occupancy
| Datum | HAL read | Enum | Tier | Source |
|---|---|---|---|---|
| Seatbelt driver / passenger | `BYDAutoSafetyBeltDevice.getPassengerStatus(seat)`; `BYDAutoInstrumentDevice.getSafetyBeltStatus(i)`; fids `INSTRUMENT_DD_MAIN/DEPUTY_SAFETYBELT_STATE` 692060184/638582811 | 0=unbuckled/1=buckled | OVERDRIVE | `stub safetybelt/instrument`; `BydFeatureIds`; `add("seatbelt")` |
| Occupancy driver / passenger (OMS camera) | `ADAS_OMS_DRIVER_DETECTION` 834666600 / `ADAS_OMS_PASSENGER_DETECTION` 834666605 | enum | OVERDRIVE | `BydFeatureIds`; `add("passenger_detection")` |
| Child-presence detection | `SETTING_CPD_SWITCH_STATUS` 376438818 | 1=on/2=off/3=delay | OVERDRIVE | `BydFeatureIds`; `add("child_presence_detection")` |
| Speed-limit warning | `ADAS_SLW_FUNC_SWITCH_STATE` 535834664 | 0/1 | OVERDRIVE | `BydFeatureIds`; `add("speed_limit_warning")` |
| Blind-spot alarm FL / FR (level) | `ADAS_FL_BLIND_SPOT_ALARM` 1098907692 / `ADAS_FR_BLIND_SPOT_ALARM` 1098907694 | ≥1 = alerting | OVERDRIVE | `BydFeatureIds` (level encoding) |
| Lane-change / rear-cross / door-open warnings (L/R) | `ADAS_LCA_WARNING_LEFT/RIGHT` 1098907664/666 · `ADAS_RCTA_WARNING_LEFT/RIGHT` 1098907668/669 · `ADAS_DOW_WARN_LEFT/RIGHT` 1098907680/682 | monotonic COUNTER (alert = increase) | OVERDRIVE | `BydFeatureIds` (counter encoding — note!) |
| Parking radar (8 zones) | `BYDAutoRadarDevice.getAllRadarProbeStates()` / `getRadarProbeState(area)` | 0=safe…4=red, 8 zones | OVERDRIVE | `stub radar`; `RadarConstants.AREA_*`; `add("radar_distances")` |
| Radar switch states (reverse/front/side) + volume | `getReverse/Front/SideRadarSwitchState()`, `getRadarVolume()` | enum / level | OVERDRIVE | `stub radar` |
| ESP state | `ADAS_ESP_STATE` 305135676 | 0/1 | OVERDRIVE | `BydFeatureIds` |
| MCU / power status | `BYDAutoPowerDevice.getMcuStatus()` | 0=sleep/1=active/2=acc-off/3=deep-sleep | OVERDRIVE | `stub power MCU_STATUS_*`; `add("mcu_status")` |
| 12V battery voltage / level | `BYDAutoPowerDevice.getBatteryVoltage()` (mV); bodywork `getBatteryVoltageLevel()` | V / 0=low/1=normal/2=invalid | OVERDRIVE | `stub power/bodywork`; `add("volt_12v"/"volt_12v_level")` |

## A8. Identity / key / engine
| Datum | HAL read | Type | Tier | Source |
|---|---|---|---|---|
| VIN | `BYDAutoBodyworkDevice.getAutoVIN()` | string | OVERDRIVE | `stub bodywork`; `add("vin")` |
| Key battery / missing / low-power / start-state / smart-key warn | `Body.SMART_ENTRY_BLUETOOTH_STATUS` 602931221 + key enums | enum | OVERDRIVE | `BydFeatureIds`; `add("key_*"/"smart_key_warn")` |
| Engine code / coolant level / oil level (PHEV) | `BYDAutoEngineDevice.getEngineCode()/getEngineCoolantLevel()/getOilLevel()` | string / enum / % | OVERDRIVE | `stub engine`; `add("engine_code"/"engine_coolant_level"/"oil_level")` |
| GPS lat / lon / elevation / heading (from nav) | telemetry catalog (GPS source) | °/m | OVERDRIVE | `add("lat"/"lon"/"elevation"/"heading")` |

---

# B. CONTROLS (write / actions) — every actuator

> Source: `VehicleControlCatalog.java` register() entries (Overdrive's controllable-entity registry = its complete
> actuator surface) + `BydCarSettings.java` tier-3 registry + ClusterNav proven paths. **Safety class**:
> `safe-anytime` = comfort/info, no motion hazard · `motion-gated` = should be gated by gear=P / speed=0 (open door,
> window, tailgate, sunroof, some ADAS). Arg semantics from the catalog command-fn + device constants. Tier is the
> *write* tier (writes are harder to prove than reads).

## B1. Climate
| Control | device.method(args) OR feature-id + args | Semantics / range | Safety | Tier | Source |
|---|---|---|---|---|---|
| A/C on / off (auto) | Climate composite → AC device; `AC_AUTO_MODE_SET` 1324355606 | off / auto | safe-anytime | OVERDRIVE | `VehicleControlCatalog climate`; `BydFeatureIds` |
| A/C setpoint temp | AC set-temp (HA entity 17–33 °C, step 1) | 17–33 °C | safe-anytime | OVERDRIVE / NEEDS-CAR (set-method) | `VehicleControlCatalog climate min=17 max=33` |
| A/C fan level | `AC_WIND_LEVEL_SET` 501219340 via `set(1000,id,level)` | 1–7 | safe-anytime | OVERDRIVE | `VehicleControlCatalog fan_mode`; `BydFeatureIds` (named setter is no-op on DL3) |
| A/C recirc / cycle mode | `AC_CYCLE_MODE_SET` 501219355 | on/off | safe-anytime | OVERDRIVE | `BydFeatureIds` |
| Defrost front / rear | `AC_DEFROST_FRONT_SET` 501219362 / `AC_DEFROST_REAR_SET` 501219357 | on/off | safe-anytime | OVERDRIVE | `BydFeatureIds` |
| **PM2.5 auto-filter** | `BYDAutoAcDevice.setAutoCleanAirState(1/0)` | continuous auto-clean | safe-anytime | **PROVEN-ClusterNav** (rc=0 on-car) | `Pm25FilterApplier`; `byd-pm25-airclean-RE` |
| **PM2.5 clean-now** | `BYDAutoAcDevice.setQuickCleanAirState(1)` | one-shot purge | safe-anytime | **PROVEN-ClusterNav** (v1.38) | `Pm25FilterApplier.cleanNow` |
| PM2.5 popup suppress | `BYDAutoAcDevice.enablePurificationFunctionPrompt(0/1)` | best-effort (trim rejects) | safe-anytime | PROVEN-ClusterNav (rejected on owner trim) | `Pm25FilterApplier`; `seat-vietmaploop-oncar §C` |
| Anion generator | `PM25_ANION_STATE_SET` 1337982994 | on/off | safe-anytime | OVERDRIVE | `BydFeatureIds` |
| Remote climate start / schedule | cloud `ClimateOnCommand`/`ClimateScheduleCommand` (15–31 °C, 10–30 min) | timed pre-condition | safe-anytime | OVERDRIVE (cloud, no SDK leg) | `VehicleControlCatalog remote_climate_*` |

## B2. Seats / steering wheel comfort
| Control | device.method(args) | Semantics | Safety | Tier | Source |
|---|---|---|---|---|---|
| **Seat ventilation (cool)** | `BYDAutoSettingDevice.setSeatVentilatingState(seatID, state)` | seatID 1..4; state 1=off/2=low/3=high | safe-anytime | **PROVEN-ClusterNav** | `SeatComfortApplier`; fid `SET_DRIVER_SEAT_VENTILATING_STATE` 1335885832 / passenger 1335885840 |
| **Seat heating** | `BYDAutoSettingDevice.setSeatHeatingState(seatID, state)` | seatID 1..4; state 1/2/3; mutually exclusive w/ vent | safe-anytime | **PROVEN-ClusterNav** | `SeatComfortApplier`; fid `SET_DRIVER_SEAT_HEATING_STATE` 1335885835 / passenger 1335885843 |
| Steering-wheel heating | `setSteeringWheelHeatingState(state)` | on/off (raw 2=on/1=off) | safe-anytime | OVERDRIVE / Dudu | `VehicleControlCatalog steering_heat`; `dudu-launcher-hal-RE` |
| Seat memory recall (driver) | seat-memory `SETTING_LF_MEMORY_LOCATION_SET` 1276186678 | PRESS (recall) | motion-gated | OVERDRIVE | `VehicleControlCatalog seat_memory_driver`; `BydFeatureIds` |

## B3. Windows / sunroof / sunshade / tailgate / doors
| Control | device.method(args) OR feature-id | Semantics | Safety | Tier | Source |
|---|---|---|---|---|---|
| **Window open/close (1 window)** | `BYDAutoBodyworkDevice.setBodyWindowCtrlState(window, state)` | window 1..4 (LF/RF/LR/RR); 0=close/1=open | motion-gated | **PROVEN-ClusterNav** (rc=0, →98% open) | `bodywork-window-trunk-RE`; `stub BODYWORK_CMD_WINDOW_*` |
| Window all (4) | `BYDAutoBodyworkDevice.setAllWindowState(lf,rf,lr,rr)` | each 0/1 (close-all unreliable — anti-pinch; use cloud CloseAllWindows) | motion-gated | OVERDRIVE / ClusterNav path | `VehicleControlCatalog windows_all`; `BodyworkControl` |
| Window open % (exact) | Dudu fid `BODYWORK_WINDOW_{LF,RF,LR,RR}_PERCENT`; SET % | 0–100 (Dudu path — the "open 50%" answer) | motion-gated | Dudu / NEEDS-CAR (id numeric) | `dudu-launcher-hal-RE §CỬA SỔ %` |
| Window vent (crack) | cloud `VentAllWindowsCommand` / `WINDOW_OPEN_HALF=4` | ~10% ventilate | motion-gated | OVERDRIVE | `VehicleControlCatalog windows_vent`; `stub WINDOW_OPEN_HALF` |
| **Tailgate open/close/stop** | `BYDAutoBodyworkDevice.setHetchDoorStatus(status)` (CLOSE=2, OPEN≈1) · `BODY_BACK_DOOR_TRIGGER` 489689126 | close/open/stop | motion-gated | **PROVEN-ClusterNav** (close=2) / OVERDRIVE | `bodywork-window-trunk-RE §CỐP`; `VehicleControlCatalog tailgate` |
| Sunroof open/close/stop | Sunroof cmd (1=open/2=close/3=stop); `BODYWORK_CMD_MOON_ROOF=5`; Dudu `BODYWORK_MOON_ROOF_OPEN_PERCENT` | open/close/stop/% | motion-gated | OVERDRIVE / Dudu / NEEDS-CAR (method) | `VehicleControlCatalog sunroof`; `stub SUNROOF_*`; `dudu-launcher-hal-RE` |
| Sunshade open/close/stop | `BODY_SUNSHADE_PANEL_PERCENT_SET` 1330642984; `BODYWORK_CMD_SUNSHADE_PANEL=6` | open/close/stop/% | motion-gated | OVERDRIVE | `VehicleControlCatalog sunshade`; `BydFeatureIds` |
| Door lock / unlock | doorlock device / SETTING (locked=2/unlocked=1) | LOCK/UNLOCK | motion-gated (unlock) | OVERDRIVE / NEEDS-CAR (method) | `VehicleControlCatalog lock platform` |
| Child lock (rear L/R) | `DOORLOCK_CHILDLOCK_LEFT_SET` 1276141584 / `RIGHT` 1276141586; `SETTING_{L,R}_REAR_DOOR_CHILD_LOCK_STATUS` 957350032/034 | on/off | safe-anytime | OVERDRIVE | `VehicleControlCatalog child_lock`; `BydFeatureIds` |
| Fuel-tank cap / hood release | `BODYWORK_CMD_DOOR_FUEL_TANK_CAP=7` / `..._HOOD=5` (Dudu `BODYWORK_HOOD`) | trigger | motion-gated | OVERDRIVE / Dudu | `stub bodywork BODYWORK_CMD_DOOR_*`; `dudu-launcher-hal-RE` |
| Mirror fold / unfold | `SETTING_OUTSIDE_REARVIEW_MIRROR_FOLD_SET` 1276157992 (1=fold/2=unfold) | fold/unfold | safe-anytime | OVERDRIVE | `VehicleControlCatalog mirror_fold`; `BydConstants.MIRROR_FOLD/UNFOLD_COMMAND` |
| Mirror auto fold-on-lock | `MIRROR_HAVE_AUTO_FOLD` 1081081882 / car-setting `auto_mirror_for_lock` | on/off | safe-anytime | OVERDRIVE | `VehicleControlCatalog mirror_auto_follow_up`; `BydCarSettings` |
| Rain auto-close windows | `BYDAutoBodyworkDevice.setRainCloseWindow(1/2)`; car-setting `rain_close_window` | on=1/off=2 | safe-anytime | OVERDRIVE | `bodywork-window-trunk-RE`; `BydCarSettings` |
| Wiper front level | `WIPER_FRONT_LEVEL` 321912848 (Dudu `WIPER_FRONT_WIPER_LEVEL`) | level | motion-context | OVERDRIVE / Dudu | `BydFeatureIds`; `dudu-launcher-hal-RE` |
| Wiper maintenance/overhaul mode | Dudu `SET_{FRONT,REAR}_WINDSCREEN_WIPER_OVERHAUL_STATE` | raise-blade service mode | motion-gated | Dudu / NEEDS-CAR (id numeric) | `dudu-launcher-hal-RE §Gạt mưa` |
| Voice-control tailgate | `SETTING_VOICE_CTRL_BACK_DOOR_SET` 1125122080 | on/off | safe-anytime | OVERDRIVE | `BydFeatureIds` |

## B4. Lights / ambient
| Control | feature-id + args | Semantics | Safety | Tier | Source |
|---|---|---|---|---|---|
| Daytime running lights | `LightsCommand` → `LIGHT_DAY_RUNNING_LIGHT_AUTO_STATE` 985661476 | on/off | safe-anytime | OVERDRIVE | `VehicleControlCatalog drl`; `BydFeatureIds` |
| Headlight mode selector | `INSTRUMENT_HEADLIGHT_CONTROL_SET` 1276153912 | 1=off/2=auto/3=parking/4=low (off Park-gated) | safe-anytime (off gated) | OVERDRIVE | `VehicleControlCatalog headlight_mode`; `BydFeatureIds` |
| Hazard lights | `HazardCommand` (double-flash cmd — UNCONFIRMED) | on/off | motion-context | NEEDS-CAR | `VehicleControlCatalog hazard` (id inferred) |
| Ambient main switch | `LIGHT_ATMOSPHERE_MAIN_SWITCH_SET` 1276153924 (+ bodywork execute `BODY_ATMOSPHERE_LIGHT_SWITCH` 489701406 + `atmosphere_lamp` setting — 3-tier) | on/off | safe-anytime | OVERDRIVE | `VehicleControlCatalog ambient_power`; `BydFeatureIds` |
| Ambient colour | `LIGHT_ATMOSPHERE_CUSTOM_COLOR_SET` 1276194864 / front `LIGHT_AMBIENT_FRONT_COLOR` 1121976336 | 1-based idx into 31-colour palette | safe-anytime | OVERDRIVE | `VehicleControlCatalog ambient_colour`; `LightConstants.AMBIENT_COLOURS` |
| Ambient brightness | `LIGHT_ATMOSPHERE_CUSTOM_BRIGHTNESS_SET` 1276194858 / `..._VEHICLE_BRIGHTNESS_SET` 1896874032; car-setting `lighting_ambient_brightness` 0–10 | 0–100% / 0–5 level | safe-anytime | OVERDRIVE | `VehicleControlCatalog ambient_brightness`; `BydFeatureIds`; `BydCarSettings` |
| Ambient adjust-area / music-sync | `LIGHT_ATMOSPHERE_ADJUST_AREA_SET` 1896873992 · `BODY_ATMOSPHERE_LIGHT_MUSIC` 489701407 | zone / music-react | safe-anytime | OVERDRIVE | `BydFeatureIds` |
| Interior dome light (by door) | `BODY_INSIDE_LIGHT_STATE_SET` 1330643002 (Dudu `SET_INSIDE_LIGHT_DOOR_STATE`) | on/off/door | safe-anytime | OVERDRIVE / Dudu | `BydFeatureIds`; `dudu-launcher-hal-RE` |
| Low/high beam direct | `LIGHT_LOW_BEAM` 950009866 / `LIGHT_HIGH_BEAM` 950009868 (Dudu light ids) | on/off | motion-context | OVERDRIVE / Dudu | `BydFeatureIds`; `dudu-launcher-hal-RE §Đèn` |

## B5. Drive / energy / charging levers
| Control | args | Semantics | Safety | Tier | Source |
|---|---|---|---|---|---|
| Drive mode | `OperationModeCommand` → `SETTING_TARGET_DRIVING_MODE` 1272971280 | normal/eco/sport/snow | safe-anytime | OVERDRIVE | `VehicleControlCatalog drive_mode` |
| Powertrain mode | `EnergyModeCommand` (EV=1/HEV=3) | ev/hev | safe-anytime | OVERDRIVE | `VehicleControlCatalog powertrain_mode`; `BydDataCollector.ENERGY_MODE_*` |
| Battery hold (SOC-hold) | `SocHoldToggleCommand`/`SocHoldPresetCommand` | off/at_current/at_target/at_floor | safe-anytime | OVERDRIVE | `VehicleControlCatalog battery_hold` (Dudu `SET_DR_SOC_TARGET`) |
| Target SOC | `SocTargetPercentCommand` | 15–70% | safe-anytime | OVERDRIVE | `VehicleControlCatalog target_soc`; `SOC_TARGET_MIN/MAX` |
| Regen (energy recuperation) | `EnergyFeedbackCommand` (0=standard/1=high) | standard/high | safe-anytime | OVERDRIVE (getEnergyFeedback proven readback) | `VehicleControlCatalog regen_level` (Dudu `SET_DR_ENERGY_FB`) |
| Steering assist feel | `SteerAssistCommand` (1=comfort/2=sport) | comfort/sport | safe-anytime | OVERDRIVE | `VehicleControlCatalog steering_mode` |
| Brake pedal feel | `BrakeFeelCommand` (0=comfort/1=sport) | comfort/sport | safe-anytime | OVERDRIVE | `VehicleControlCatalog brake_feel` |
| iTAC (torque control) | `SETTING_ITAC_STATE_SET` 1324376094 | on/off | safe-anytime | OVERDRIVE | `VehicleControlCatalog itac`; `BydFeatureIds` |
| Charge limit enable / % | `ChargeCapToggleCommand` / `ChargeCapPercentCommand` → `SETTING_AC_CHARGING_CURRENT_LIMIT_STATUS_SET` 1324376132 | on/off; 50–100 step 5 | safe-anytime | OVERDRIVE | `VehicleControlCatalog charge_cap_*`; `BydFeatureIds` |
| Smart charging + schedule | cloud `SmartChargingToggleCommand`/`ChargeScheduleCommand` | on/off + time schedule | safe-anytime | OVERDRIVE (cloud) | `VehicleControlCatalog smart_charging/smart_charge_schedule` |
| Start charging now | cloud `StartChargingNowCommand` | PRESS | safe-anytime | OVERDRIVE (cloud) | `VehicleControlCatalog start_charging_now` |
| Wireless phone charger (global/L/R) | `CHARGING_WIRELESS_SWITCH_SET` 1312817218 · `..._LEFT_SWITCH_SET` 1276182576 · `..._RIGHT_SWITCH_SET` 1276182578 | on/off | safe-anytime | OVERDRIVE | `VehicleControlCatalog wireless_charging*`; `BydFeatureIds` |
| AVH auto-hold | Dudu `ADAS_AVH_STATE`; car-setting `avh_assist` | on/off | safe-anytime | Dudu / OVERDRIVE | `dudu-launcher-hal-RE`; `BydCarSettings` |
| Road-surface mode | `ENERGY_ROAD_SURFACE_MODE` 603979827 | 1=common/2=snow | safe-anytime | OVERDRIVE | `BydFeatureIds` |

## B6. ADAS switches (full matrix — all optimistic-echo unless noted)
> All route to the ADAS device via `BydDataCollector`. Feature-ids from OEM SDK, **UNVERIFIED per trim** — verify each before relying. AEB is enable-only. Safety controls (AEB/lane-keep/ESP) are labelled at the action layer.

| Control | SET feature-id | Semantics | Tier | Source |
|---|---|---|---|---|
| Speed-limit warning (SLW) | `ADAS_SLW_FUNC_SWITCH_STATE_SET` 850452531 | on/off | OVERDRIVE | `VehicleControlCatalog adas_slw` |
| Stability control (ESP) | `ADAS_ESP_STATE_SET` 944766984 | on/off | OVERDRIVE/NEEDS-CAR | `adas_esp/esp_control` |
| Blind-spot detection (BSD) | (BSD switch; distinct from alarm) | on/off | OVERDRIVE | `adas_bsd` |
| Traffic sign recognition (TSR/ISLA) | `ADAS_ISLA_SWITCH_SET` 944767044 | on/off | OVERDRIVE | `adas_tsr` |
| Intelligent speed-limit control (ISLC) | `ADAS_ISLC_SWITCH_SET` 1324560408 | on/off | OVERDRIVE | `adas_islc` |
| Lane assist (LDW/LDP) | `BYDAutoADASDevice.setLKSMode` (0..3); car-setting `lane_keeping` | off/LDW/LDP/both | OVERDRIVE | `adas`; `lane_assist` |
| Emergency lane keeping (ELKA) | `ADAS_ELKA_SWITCH_SET` 944767046 | on/off | OVERDRIVE | `adas_elka` |
| Forward collision warning (FCW) | `ADAS_FCW_LEVEL_SET` 1324560420 | 0–3 level | OVERDRIVE | `adas_fcw` |
| Auto emergency braking (AEB) | enable-only | on | OVERDRIVE | `adas_aeb`; car-setting `aeb` |
| Rear cross-traffic alert / brake (RCTA/RCTB) | `ADAS_RCTA_STATE_SET` 944766990 · `ADAS_ECTB_STATE_SET` 944767006 | on/off | OVERDRIVE | `adas_rcta/adas_rctb` |
| Front cross-traffic alert / brake (FCTA/FCTB) | `ADAS_FCTA_SWITCH_SET` 1324560400 · `ADAS_FCTB_SWITCH_SET` 1324560402 | on/off | OVERDRIVE | `adas_fcta/adas_fctb` |
| Door-open warning (DOW) | `ADAS_DOW_STATE_SET` 944766994 | on/off | OVERDRIVE | `adas_dow` |
| Rear collision warning (RCW) | `ADAS_RCW_STATE_SET` 944766992 | on/off | OVERDRIVE | `adas_rcw` |
| Traffic-light attention (TLA) | `ADAS_TLA_SWITCH_SET` 1324560410 | on/off | OVERDRIVE | `adas_tla` |
| Child-presence detection | `SETTING_CPD_SWITCH_STATUS_SET` 1324617778 | 1=on/2=off | OVERDRIVE | `adas_cpd` |
| Parking radar volume / switches | `BYDAutoRadarDevice.setRadarVolume/setReverse/Front/SideRadarSwitch` | level / on-off | OVERDRIVE | `stub radar` |

## B7. Infotainment / instrument / misc
| Control | feature-id + args | Semantics | Safety | Tier | Source |
|---|---|---|---|---|---|
| Screen rotation (portrait/landscape) | `SETTING_PAD_ROTATION_SET` 1330643005 | horizontal=1/vertical=2 | safe-anytime | OVERDRIVE | `VehicleControlCatalog infotainment_rotation` |
| Native camera view | camera codes 3001..3008 | front/rear/L/R/wide/L+R | safe-anytime | OVERDRIVE | `VehicleControlCatalog native_camera_view`; `BydDataCollector.NATIVE_CAMERA_VIEW_*` |
| Cluster music widget (state/source/progress) | `INSTRUMENT_MUSIC_STATE_SET` 1138753546 · `..._SOURCE_SET` 871366704 · `..._PLAYBACK_PROGRESS_SET` 1138753552 | play state / source / progress | safe-anytime | OVERDRIVE | `BydFeatureIds` |
| Brightness gear | `SETTING_BRIGHTNESS_GEAR_SET` 1276174360 | level | safe-anytime | OVERDRIVE | `BydFeatureIds` |
| Auto-lock delay | car-setting `auto_lock_time` (0/10/30/60/120) | seconds | safe-anytime | OVERDRIVE | `BydCarSettings` |
| Close windows on lock | car-setting `shut_window_after_locking` | on/off | safe-anytime | OVERDRIVE | `BydCarSettings` |
| Temperature unit | car-setting `unit_temperature` (0=C/1=F) | C/F | safe-anytime | OVERDRIVE | `BydCarSettings` |

---

# C. Numeric feature-id table (complete)

> The complete `BydFeatureIds.java` set (Overdrive, ≈187 constants) + `CanWriteVerbs.java` (dashcast CAN HUD/nav,
> ≈26). Decimal is authoritative (verified against a live DL3 `android.hardware.bydauto.BYDAutoFeatureIds` scrape by
> both projects); hex is derived here (`0x%08X`). Overdrive resolves the real framework field first via
> `resolveOrFallback("Nested.FIELD", numeric)` and uses the numeric only as fallback — so on Kachi's trim, prefer
> resolving the named field, and treat these decimals as the fallback contract. Written via
> `AbsBYDAutoDevice.set(int[]{id}, BYDAutoEventValue)` (raw) or the named method where one exists (§B).

## C1. SETTING device (BYDAutoSettingDevice, device type 1023)
| Constant | Decimal | Hex |
|---|---|---|
| SET_DRIVER_SEAT_VENTILATING_STATE | 1335885832 | 0x4FA00008 |
| SET_DRIVER_SEAT_HEATING_STATE | 1335885835 | 0x4FA0000B |
| SET_PASSENGER_SEAT_VENTILATING_STATE | 1335885840 | 0x4FA00010 |
| SET_PASSENGER_SEAT_HEATING_STATE | 1335885843 | 0x4FA00013 |
| SETTING_LEFT_REAR_DOOR_CHILD_LOCK_STATUS | 957350032 | 0x39100090 |
| SETTING_RIGHT_REAR_DOOR_CHILD_LOCK_STATUS | 957350034 | 0x39100092 |
| SETTING_ITAC_CONFIG | 656408614 | 0x27200026 |
| SETTING_ITAC_STATE | 656408615 | 0x27200027 |
| SETTING_ITAC_STATE_SET | 1324376094 | 0x4EF0601E |
| SETTING_ITAC_INTELLIGENT_TORQUE_CONTROL_FLAG | 656408620 | 0x2720002C |
| SETTING_IAL_COLOR_CONFIG | 1072693258 | 0x3FF0000A |
| SETTING_LF_MEMORY_LOCATION_SET | 1276186678 | 0x4C111036 |
| SETTING_LF_MEMORY_LOCATION_WAKE_SET | 1276186686 | 0x4C11103E |
| SETTING_RF_MEMORY_LOCATION_SET | 1276186680 | 0x4C111038 |
| SETTING_RF_MEMORY_LOCATION_WAKE_SET | 1276186688 | 0x4C111040 |
| SETTING_PAD_ROTATION_SET | 1330643005 | 0x4F50003D |
| SETTING_BRIGHTNESS_GEAR_SET | 1276174360 | 0x4C10E018 |
| SETTING_VOICE_CTRL_BACK_DOOR_SET | 1125122080 | 0x43100020 |
| SETTING_DRAG_MODE_CURRENT_STATE | 875560989 | 0x3430001D |
| SETTING_TARGET_DRIVING_MODE | 1272971280 | 0x4BE00010 |
| SETTING_TARGET_DRIVING_MODE_ALT | 255852712 | 0x0F4000A8 |
| SETTING_CPD_SWITCH_STATUS | 376438818 | 0x16700022 |
| SETTING_CPD_SWITCH_STATUS_SET | 1324617778 | 0x4EF41032 |
| SETTING_AC_CHARGING_CURRENT_LIMIT_CONFIG_STATUS | 407898179 | 0x18500843 |
| SETTING_AC_CHARGING_CURRENT_LIMIT_STATUS | 407898181 | 0x18500845 |
| SETTING_AC_CHARGING_CURRENT_LIMIT_STATUS_SET | 1324376132 | 0x4EF06044 |
| SETTING_LANE_CURVATURE | 883949604 | 0x34B00024 |
| SETTING_RAIN_WIPER_SPEED | 1196425250 | 0x47500022 |
| SETTING_OUTSIDE_REARVIEW_MIRROR_FOLD_SET | 1276157992 | 0x4C10A028 |
| SETTING_HUD_SWITCH_SET | 1276174371 | 0x4C10E023 |
| SETTING_HUD_CONFIG | 951058453 | 0x38B00015 |
| SETTING_HUD_SWITCH_STATUS_FEEDBACK | 951058460 | 0x38B0001C |

## C2. BODY / BODYWORK / TAILGATE / DOORLOCK / MIRROR / WIPER
| Constant | Decimal | Hex |
|---|---|---|
| BODYWORK_BATTERY_METRIC | 300941320 | 0x11F00008 |
| BODYWORK_BATTERY_RANGE | 300941336 | 0x11F00018 |
| BODYWORK_EMERGENCY_ALARM | 692060190 | 0x2940001E |
| BODY_ATMOSPHERE_LIGHT_SWITCH | 489701406 | 0x1D30401E |
| BODY_ATMOSPHERE_LIGHT_MUSIC | 489701407 | 0x1D30401F |
| BODY_SMART_ENTRY_BLUETOOTH_STATUS | 602931221 | 0x23F00015 |
| BODY_BACK_DOOR_TRIGGER | 489689126 | 0x1D301026 |
| BODY_BACK_DOOR_STATUS | 365953067 | 0x15D0002B |
| BODY_BACK_DOOR_OPENING_STATUS | 365953060 | 0x15D00024 |
| BODY_BACK_DOOR_OPEN_CONFIRM | 654311456 | 0x27000020 |
| BODY_BACK_DOOR_MAINTENANCE | 654311438 | 0x2700000E |
| BODY_BACK_DOOR_POSITION | 365953048 | 0x15D00018 |
| BODY_INSIDE_LIGHT_STATE_SET | 1330643002 | 0x4F50003A |
| BODY_SUNSHADE_PANEL_PERCENT | 1101004816 | 0x41A00010 |
| BODY_SUNSHADE_PANEL_PERCENT_SET | 1330642984 | 0x4F500028 |
| BODY_LF_DOOR_STATUS | 692060176 | 0x29400010 |
| BODY_RF_DOOR_STATUS | 692060177 | 0x29400011 |
| BODY_LR_DOOR_STATUS | 692060178 | 0x29400012 |
| BODY_RR_DOOR_STATUS | 692060179 | 0x29400013 |
| TAILGATE_BACK_DOOR_STATUS | 692060181 | 0x29400015 |
| TAILGATE_BACKDOOR_POSITION | 1074790456 | 0x40100038 |
| TAILGATE_DOWN_BACK_DOOR_POSITION | 365953044 | 0x15D00014 |
| DOORLOCK_CHILDLOCK_LEFT_SET | 1276141584 | 0x4C106010 |
| DOORLOCK_CHILDLOCK_RIGHT_SET | 1276141586 | 0x4C106012 |
| MIRROR_HAVE_AUTO_FOLD | 1081081882 | 0x4070001A |
| MIRROR_REARVIEW_SET | 1324556304 | 0x4EF32010 |
| MIRROR_REARVIEW_STATE | 960495624 | 0x39400008 |
| WIPER_REAR_AREA_STATE | 1196425226 | 0x4750000A |
| WIPER_FRONT_LEVEL | 321912848 | 0x13300010 |

## C3. LIGHT (exterior + ambient)
| Constant | Decimal | Hex |
|---|---|---|
| LIGHT_LOW_BEAM | 950009866 | 0x38A0000A |
| LIGHT_HIGH_BEAM | 950009868 | 0x38A0000C |
| LIGHT_PIXEL_HEADLIGHT_ALS_STATE | 984612932 | 0x3AB00044 |
| LIGHT_DAY_RUNNING_LIGHT_AUTO_STATE | 985661476 | 0x3AC00024 |
| LIGHT_AMBIENT_FRONT_BRIGHTNESS | 1121976328 | 0x42E00008 |
| LIGHT_AMBIENT_FRONT_BRIGHTNESS_ALT | 521142688 | 0x1F1001A0 |
| LIGHT_AMBIENT_REAR_BRIGHTNESS | 1121976332 | 0x42E0000C |
| LIGHT_AMBIENT_REAR_BRIGHTNESS_ALT | 521142696 | 0x1F1001A8 |
| LIGHT_AMBIENT_FRONT_COLOR | 1121976336 | 0x42E00010 |
| LIGHT_AMBIENT_REAR_COLOR | 1121976343 | 0x42E00017 |
| LIGHT_ATMOSPHERE_MAIN_SWITCH_STATUS | 1060110406 | 0x3F300046 |
| LIGHT_ATMOSPHERE_MAIN_SWITCH_SET | 1276153924 | 0x4C109044 |
| LIGHT_ATMOSPHERE_ADJUST_AREA_SET | 1896873992 | 0x71100008 |
| LIGHT_ATMOSPHERE_VEHICLE_BRIGHTNESS_SET | 1896874032 | 0x71100030 |
| LIGHT_ATMOSPHERE_CUSTOM_BRIGHTNESS | 657457175 | 0x27300017 |
| LIGHT_ATMOSPHERE_CUSTOM_BRIGHTNESS_SET | 1276194858 | 0x4C11302A |
| LIGHT_ATMOSPHERE_CUSTOM_COLOR | 657457168 | 0x27300010 |
| LIGHT_ATMOSPHERE_CUSTOM_COLOR_SET | 1276194864 | 0x4C113030 |

## C4. INSTRUMENT (cluster widgets, tyre-temp, charging, music, nav-set)
| Constant | Decimal | Hex |
|---|---|---|
| INSTRUMENT_HEADLIGHT_CONTROL_FEEDBACK | 1011875880 | 0x3C500028 |
| INSTRUMENT_HEADLIGHT_CONTROL_SET | 1276153912 | 0x4C109038 |
| INSTRUMENT_DD_MAIN_SAFETYBELT_STATE | 692060184 | 0x29400018 |
| INSTRUMENT_DD_DEPUTY_SAFETYBELT_STATE | 638582811 | 0x2610001B |
| INSTRUMENT_MUSIC_STATE_SET | 1138753546 | 0x43E0000A |
| INSTRUMENT_MUSIC_SOURCE_SET | 871366704 | 0x33F00030 |
| INSTRUMENT_MUSIC_PLAYBACK_PROGRESS_SET | 1138753552 | 0x43E00010 |
| INSTRUMENT_NAVIGATION_ACTIVATED_SET | 1086373917 | 0x40C0C01D |
| INSTRUMENT_NAVI_ESTIMATED_TIME_SET | 1277239312 | 0x4C212010 |
| INSTRUMENT_NAVI_ESTIMATED_MILEAGE_SET | 1277239328 | 0x4C212020 |
| INSTRUMENT_NAVI_TYPE_SET | 1276157976 | 0x4C10A018 |
| INSTRUMENT_2IN1_CURRENT_JOURNEY_DRIVE_MILEAGE | 1246801948 | 0x4A50B01C |
| INSTRUMENT_2IN1_CURRENT_JOURNEY_DRIVE_TIME | 1246801938 | 0x4A50B012 |
| INSTRUMENT_LF_TYRE_TEMPERATURE | 1246797848 | 0x4A50A018 |
| INSTRUMENT_RF_TYRE_TEMPERATURE | 1246797860 | 0x4A50A024 |
| INSTRUMENT_LB_TYRE_TEMPERATURE | 1246797872 | 0x4A50A030 |
| INSTRUMENT_RB_TYRE_TEMPERATURE | 1246797884 | 0x4A50A03C |
| INSTRUMENT_CHARGING_CHARGE_PERCENT_DD | 842006544 | 0x32300010 |
| INSTRUMENT_CHARGING_CHARGE_POWER_DD | 842006552 | 0x32300018 |
| INSTRUMENT_CHARGING_CHARGE_REST_HOUR_DD | 842006568 | 0x32300028 |
| INSTRUMENT_CHARGING_CHARGE_REST_MINUTE_DD | 842006576 | 0x32300030 |

## C5. STATISTIC (battery health + mileage/range) + CHARGING device + ENGINE + AC + PM25 + SENSOR
| Constant | Decimal | Hex |
|---|---|---|
| STAT_TOTAL_MILEAGE | 1246765072 | 0x4A502010 |
| STAT_ELEC_PERCENTAGE | 1246777400 | 0x4A505038 |
| STAT_FUEL_PERCENTAGE | 1246785600 | 0x4A507040 |
| STAT_ELEC_DRIVING_RANGE | 1246765118 | 0x4A50203E |
| STAT_FUEL_DRIVING_RANGE | 1246773304 | 0x4A504038 |
| STAT_EV_DRIVING_MILEAGE | 1146093608 | 0x44500028 |
| STAT_MILEAGE_EV | 1246773284 | 0x4A504024 |
| STAT_MILEAGE_HEV | 1246773264 | 0x4A504010 |
| STAT_MILEAGE_AFTER_ZEROING | 1245753360 | 0x4A40B010 |
| STAT_THIS_TRIP_ELEC_CONSUMPTION | 1246801976 | 0x4A50B038 |
| STAT_BATTERY_HEALTHY_INDEX | 1145045032 | 0x44400028 |
| STAT_HIGHEST_BATTERY_TEMP | 1148190752 | 0x44700020 |
| STAT_AVERAGE_BATTERY_TEMP | 1148190776 | 0x44700038 |
| STAT_LOWEST_BATTERY_TEMP | 1148190736 | 0x44700010 |
| STAT_HIGHEST_BATTERY_VOLTAGE | 1147142192 | 0x44600030 |
| STAT_LOWEST_BATTERY_VOLTAGE | 1147142160 | 0x44600010 |
| CHARGING_CHARGE_CAPACITY | 666894360 | 0x27C00018 |
| CHARGING_CHARGER_WORK_STATE | 666894346 | 0x27C0000A |
| CHARGING_BATTERY_DEVICE_STATE | 876609560 | 0x34400018 |
| CHARGING_WIRELESS_STATE | 1105199113 | 0x41E00009 |
| CHARGING_WIRELESS_LEFT_SWITCH_SET | 1276182576 | 0x4C110030 |
| CHARGING_WIRELESS_RIGHT_SWITCH_SET | 1276182578 | 0x4C110032 |
| CHARGING_WIRELESS_SWITCH_SET | 1312817218 | 0x4E400042 |
| CHARGING_WIRELESS_LEFT_STATE | 890241048 | 0x35100018 |
| CHARGING_WIRELESS_RIGHT_STATE | 890241052 | 0x3510001C |
| ENGINE_POWER | 339738656 | 0x14400020 |
| ENGINE_SPEED | 339738642 | 0x14400012 |
| ENGINE_FRONT_MOTOR_SPEED | 1141899272 | 0x44100008 |
| ENGINE_REAR_MOTOR_SPEED | 621805576 | 0x25100008 |
| ENGINE_FRONT_MOTOR_TORQUE | 1141899288 | 0x44100018 |
| ENGINE_DRIFT_MODE_SWITCH_STATUS | 681574694 | 0x28A00126 |
| ENGINE_DRIFT_MODE_SWITCH_CONFIG | 681574763 | 0x28A0016B |
| AC_TEMP_INSIDE | 1031798832 | 0x3D800030 |
| AC_AUTO_MODE_SET | 1324355606 | 0x4EF01016 |
| AC_DEFROST_FRONT_SET | 501219362 | 0x1DE00022 |
| AC_DEFROST_REAR_SET | 501219357 | 0x1DE0001D |
| AC_CYCLE_MODE_SET | 501219355 | 0x1DE0001B |
| AC_WIND_LEVEL_SET | 501219340 | 0x1DE0000C |
| AC_DEFROST_FRONT_STATUS | 1128267832 | 0x43400038 |
| AC_DEFROST_REAR_STATUS | 1128267825 | 0x43400031 |
| PM25_ANION_STATE | 1033895958 | 0x3DA00016 |
| PM25_ANION_STATE_SET | 1337982994 | 0x4FC00012 |
| SAFETYBELT_REMINDER_MASK | 89129027 | 0x05500043 |
| SENSOR_AUTO_SLOPE | 573571116 | 0x2230002C |
| ENERGY_ROAD_SURFACE_MODE | 603979827 | 0x24000033 |

## C6. ADAS (switches + warning signals)
| Constant | Decimal | Hex | Note |
|---|---|---|---|
| ADAS_ESP_STATE | 305135676 | 0x1230003C | read |
| ADAS_ESP_STATE_SET | 944766984 | 0x38500008 | write |
| ADAS_SLW_FUNC_SWITCH_STATE | 535834664 | 0x1FF03028 | read |
| ADAS_SLW_FUNC_SWITCH_STATE_SET | 850452531 | 0x32B0E033 | write |
| ADAS_ISLA_SWITCH_SET | 944767044 | 0x38500044 | TSR write |
| ADAS_ISLA_SWITCH_STATUS | 760217615 | 0x2D50000F | read |
| ADAS_ISLC_SWITCH_SET | 1324560408 | 0x4EF33018 | write |
| ADAS_ISLC_SWITCH_STATUS | 760217611 | 0x2D50000B | read |
| ADAS_ELKA_SWITCH_SET | 944767046 | 0x38500046 | write |
| ADAS_ELKA_SWITCH_STATE | 854589482 | 0x32F0002A | read |
| ADAS_FCW_LEVEL_SET | 1324560420 | 0x4EF33024 | write |
| ADAS_FCW_LEVEL_STATUS | 327155720 | 0x13800008 | read |
| ADAS_RCTA_STATE_SET | 944766990 | 0x3850000E | write |
| ADAS_RTCA_SWITCH_STATE | 1098907674 | 0x4180001A | read |
| ADAS_RTCB_SWITCH_STATE | 1098907688 | 0x41800028 | read |
| ADAS_ECTB_STATE_SET | 944767006 | 0x3850001E | RCTB write |
| ADAS_FCTA_SWITCH_STATUS | 748683276 | 0x2CA0000C | read |
| ADAS_FCTA_SWITCH_SET | 1324560400 | 0x4EF33010 | write |
| ADAS_FCTB_SWITCH_STATUS | 748683278 | 0x2CA0000E | read |
| ADAS_FCTB_SWITCH_SET | 1324560402 | 0x4EF33012 | write |
| ADAS_TLA_SWITCH_SET | 1324560410 | 0x4EF3301A | write |
| ADAS_TLA_SWITCH_STATUS | 760217640 | 0x2D500028 | read |
| ADAS_DOW_STATE_SET | 944766994 | 0x38500012 | write |
| ADAS_DOW_SWITCH_STATE | 1098907678 | 0x4180001E | read |
| ADAS_RCW_STATE_SET | 944766992 | 0x38500010 | write |
| ADAS_RCW_SWITCH_STATE | 1098907676 | 0x4180001C | read |
| ADAS_SLR_STATUS_SET | 944767040 | 0x38500040 | write |
| ADAS_OMS_DRIVER_DETECTION | 834666600 | 0x31C00068 | read |
| ADAS_OMS_PASSENGER_DETECTION | 834666605 | 0x31C0006D | read |
| ADAS_FL_BLIND_SPOT_ALARM | 1098907692 | 0x4180002C | alarm (level) |
| ADAS_FR_BLIND_SPOT_ALARM | 1098907694 | 0x4180002E | alarm (level) |
| ADAS_LCA_WARNING_LEFT | 1098907664 | 0x41800010 | alarm (counter) |
| ADAS_LCA_WARNING_RIGHT | 1098907666 | 0x41800012 | alarm (counter) |
| ADAS_RCTA_WARNING_LEFT | 1098907668 | 0x41800014 | alarm (counter) |
| ADAS_RCTA_WARNING_RIGHT | 1098907669 | 0x41800015 | alarm (counter) |
| ADAS_DOW_WARN_LEFT | 1098907680 | 0x41800020 | alarm (counter) |
| ADAS_DOW_WARN_RIGHT | 1098907682 | 0x41800022 | alarm (counter) |
| ADAS_HAS_BSD | 1098907648 | 0x41800000 | capability flag |

## C7. CAN HUD / cluster-nav registers (dashcast `CanWriteVerbs.java`; written via daemon uid-2000 or in-process)
> Instrument-device family `0x43E/0x43F` = nav guidance registers; setting family `0x4C10E` = HUD switch/mode/brightness/angle + nav-screen-status. Values: `NAVI_STATUS_ACTIVE=2 / STOPPED=4`; `HUD_SWITCH_ON=1 / OFF=2`.
| Constant | Decimal | Hex |
|---|---|---|
| INSTRUMENT_SEND_NAVI_STATUS | 1138753594 | 0x43E0003A |
| INSTRUMENT_GUIDE_SIMPLE (turn icon) | 1139806224 | 0x43F01010 |
| INSTRUMENT_FRONT_CROSSING_DIST | 1139806232 | 0x43F01018 |
| INSTRUMENT_NEXT_PATHNAME (UTF-16LE) | 1140461576 | 0x43FA1008 |
| INSTRUMENT_NAVI_MILEAGE | 1139810344 | 0x43F02028 |
| INSTRUMENT_NAVI_HOUR | 1139810320 | 0x43F02010 |
| INSTRUMENT_NAVI_MINUTE | 1139810328 | 0x43F02018 |
| INSTRUMENT_NAVI_REMAINING_SEC | 1139810334 | 0x43F0201E |
| INSTRUMENT_EXPECTED_ARRIVE_DAY | 1139838992 | 0x43F09010 |
| INSTRUMENT_EXPECTED_ARRIVE_HOUR | 1139839000 | 0x43F09018 |
| INSTRUMENT_EXPECTED_ARRIVE_MINUTE | 1139839008 | 0x43F09020 |
| INSTRUMENT_EXPECTED_ARRIVE_SECOND (latch) | 1139839016 | 0x43F09028 |
| INSTRUMENT_NAVI_LEAD_MSG (secondary icon) | 1139834896 | 0x43F08010 |
| INSTRUMENT_DISTANCE_TARGET_AHEAD | 1139834904 | 0x43F08018 |
| SETTING_NAVI_SCREEN_STATUS (=3 to activate lane) | 1276174357 | 0x4C10E015 |
| SET_HUD_SWITCH (1=on/2=off) | 1276174371 | 0x4C10E023 |
| SET_HUD_MODE | 1276174373 | 0x4C10E025 |
| SET_HUD_OPTION_DISPLAY | 1276174384 | 0x4C10E030 |
| SET_HUD_BRIGHTNESS | 1276174360 | 0x4C10E018 |
| SET_HUD_HEIGHT | 1276174352 | 0x4C10E010 |
| SET_HUD_ANGLE (double, °) | 1276174380 | 0x4C10E02C |
| SETTING_HUD_REQUEST_COMMAND | 850436164 | 0x32B0A044 |
| SET_HUD_MODE_FEEDBACK (read) | 951058445 | 0x38B0000D |
| SET_HUD_SWITCH_STATUS_FEEDBACK (read) | 951058460 | 0x38B0001C |

## C8. Non-feature-id constants worth keeping
- **Sentinels**: `NOT_PROVISIONED / INVALID_VALUE_2 = -2147482648`; `INVALID_VALUE = -2147482645`; `BMS_UNAVAILABLE = -10011`; `SDK_NOT_AVAILABLE = -2.147482624E9` (double). A read/write returning these = "feature not on this trim".
- **Bodywork command enums** (`stub BYDAutoBodyworkDevice`): window/door cmd LF=1/RF=2/LR=3/RR=4/HOOD=5/LUGGAGE=6/FUEL_CAP=7; state CLOSED=0/OPEN=1; `WINDOW_OPEN_PERCENT_MIN/MAX`=0/100; SUNROOF_OPEN=3/CLOSE=4/STOP=1/TILTUP=2; `BODYWORK_COMMAND_SUCCESS=0`.
- **Light TYPE enum** (`stub BYDAutoLightDevice`): SIDE=1/LOW_BEAM=2/HIGH_BEAM=3/L_TURN=4/R_TURN=5/F_FOG=6/R_FOG=7/FOOT=8; state OFF=0/ON=1.
- **Ambient palette** (`LightConstants.AMBIENT_COLOURS`, 31 entries, 1-based): #CF39FF, #8C1FFA, #0024E9, #008BF7, #00AAFF, #07B7F1, #00E1F2, #00FFFF, #45FEFE, #A1FFF5, #A1FFF9, #A7FBCE, #6FEF9E, #17E85D, #ADFB65, #E6F972, #FFFE99, #FFE88B, #FFFF85, #FEFF50, #FFF855, #F0D117, #F9A846, #F9884A, #FF764E, #FF0049, #F63778, #F9728B, #FFFFFF, #E9FEFF, #AFF8FF.
- **Range consts** (`BydDataCollector`): `SOC_TARGET_MIN=15 / MAX=70`; energy modes STOP=0/EV=1/HEV=3/FUEL=4/KEEP=5; headlight OFF=1/AUTO=2/PARKING=3/LOW=4; pad rotation H=1/V=2; camera views 3001..3008.

---

# D. WIDGET + ACTION UX ideas — a menu for a MAXIMUM launcher

> How Dudu + Overdrive surface these capabilities, translated into launcher tiles/gauges/widgets Kachi could show.
> Overdrive renders everything as Home-Assistant entities (sensor / binary_sensor / switch / select / number / cover /
> climate / button / lock / text) — that entity-type is the natural widget hint (column "HA type" in §A/§B). Dudu
> renders a native control grid (`BydCarControlView` / `BydCarControlButtonView`) of quick-action buttons.

## D1. Telemetry widgets (HOME dashboard)
- **Energy ring** — SOC % (big), EV range km, "≈X km · full in Y′" caption. (ClusterNav prototype already has this.) Source: `getElecPercentageValue` + `getElecDrivingRangeValue`.
- **Charging card** (appears while `is_charging`) — charge power kW, charge %, ETA min, session kWh, DC-fast badge, gun/mode. Source: charging device + `INSTRUMENT_CHARGING_*_DD`.
- **PM2.5 / air ring** — level 1–6 + µg/m³ + "auto-filter on" caption + a "Clean now" button. (ClusterNav prototype has this.) Source: `getPM2p5Level/Value`.
- **Tyre board** — 4-corner pressure kPa (+ temp °C when TPMS transmits), colour on out-of-range. Source: `getTyrePressure*`.
- **Climate strip** — cabin temp, outside temp, A/C on + fan level + setpoint. Source: AC device.
- **Speed / power gauge** — km/h dial (proven), motor kW, regen. Source: `getCurrentSpeed`.
- **Battery-health card** (diagnostic) — SOH %, cell V min/max/delta, cell temp min/max/avg. Source: `STAT_*` battery-health fids.
- **Trip meter** — trip km / time / kWh, consumption kWh/100km. Source: `INSTRUMENT_2IN1_*` + `getLast50KmPowerConsume`.
- **Body status row** — doors/windows/tailgate/sunroof open indicators, mirror fold, 12V level, gear, power state. Source: door/window fids + gearbox + power.
- **Safety strip** — seatbelt icons, blind-spot L/R alert, parking-radar 8-zone ring, MCU status. Source: safetybelt + ADAS warnings + radar.
- **Odometer / identity footer** — odometer, VIN, model. Source: `getTotalMileageValue` + `getAutoVIN` + `getType`.

## D2. Quick-action buttons / tiles (Dudu-style control dock)
- **Climate tiles** — A/C on/off, fan −/+, temp −/+, recirc, defrost front/rear, PM2.5 auto + clean-now, anion.
- **Comfort tiles** — driver/passenger seat cool (off/low/high), seat heat (off/low/high), steering-wheel heat, seat-memory recall.
- **Body tiles** — window LF/RF/LR/RR open·close (+ % slider "open 50%"), all-windows, vent, tailgate open/close, sunroof, sunshade, mirror fold, child-lock, fuel-cap, wiper-maintenance mode, rain-auto-close.
- **Light tiles** — DRL, headlight mode (off/auto/parking/low), ambient on/off + 31-colour picker + brightness slider, dome light, fog.
- **Drive/energy tiles** — drive mode (normal/eco/sport/snow), powertrain EV/HEV, battery-hold preset, target-SOC slider, regen (standard/high), steering (comfort/sport), brake feel, AVH, road-surface (common/snow).
- **Charging tiles** — charge-limit on + % slider, smart-charging + schedule, start-charging-now, wireless-charger (global/L/R).
- **ADAS panel** (config, gated) — 15+ toggles: SLW, ESP, BSD, TSR, ISLC, lane-assist, ELKA, FCW level, AEB (enable), RCTA/RCTB, FCTA/FCTB, DOW, RCW, TLA, CPD.
- **Infotainment tiles** — screen rotation, native-camera-view switch, brightness gear, cluster music widget (play/source/progress), temp-unit.
- **Cluster / HUD tiles** (dashcast technique) — cast an app to cluster, windshield-HUD on/off + mode + brightness + angle + height, cluster nav-lane activate.

## D3. Widget-type → HA-entity mapping (Overdrive convention, reusable as Kachi widget kinds)
`switch` → on/off tile · `select` → segmented/cycle tile (has live readback → real toggle; else cycles cache) · `number` → slider (min/max/step) · `cover` → open/close/stop (+ position) · `climate` → thermostat card · `button` → momentary press · `lock` → lock/unlock · `text` → JSON param (schedules). Toggle strategies from `VehicleControlCatalog.ControlEntity.toAction`: (a) switch flips live state; (b) select cycles options; (c) set-only switch blind-flips last-commanded.

## D4. Cluster / HUD projection (from dashcast + ClusterNav — for the "cast to cluster" feature)
- **AutoContainer AIDL** (`ClusterManager.kt`): service `AutoContainer` (`android.os.IAutoContainer`); txn #1 `sendJson(int,String)`, #2 `sendInfo(int type, int infoInt, String)`, #3 `sendInfo2(int type, byte[])`, #4 `registerCallback`. type=1000. Opcodes (BYD Seal EU confirmed): `16`=enable fullscreen projection, `18`=close projection, `30`=switch resolution mode (slow path only), `35`=create VirtualDisplay, `0`=refresh Qt video, `1`=disconnect Qt (do NOT use to launch). DL5 = only `sendInfo(16)`.
- **Nav-to-cluster struct** (`NaviInfo.java`): FlatBuffers `byd.fbs.naviInfo.NaviInfo` sent via `sendInfo2(4, bytes)`; 18 fields — naviState, nextRouteName, curToSegmentDist, forwardState, nextTurnIcon, routeRemainTime, routeRemainDist, stringEtaArrivalTime, exitNameInfo, exitDirectionInfo, …, roungAboutNum. This is the OEM's own cluster-nav renderer contract.
- **HUD turn-icon table** (`CanBusController.java`, 49 icons): 1=turn-left, 2=turn-right, 3/5=slight, 7/8=sharp, 9/10=u-turn, 11=straight, 15–24=roundabout entry, 25–34=roundabout CCW exit-count, 35–44=CW, 45–49=stop/parking/toll/destination/tunnel.
- **Freeform on display 0** (ClusterNav proven): `am start --windowingMode 5` + `am task resize`; needs `enable_freeform_support=1`+`force_resizable_activities=1` seeded then a physical power-cycle.

---

# E. Gaps still needing the car (unknowns to close on-car)

**E1. Method-names still unknown (dump `/system/framework/*bydauto*.jar` + `jadx`/`javap` on Kachi's trim):**
- AC set methods: setpoint temp, fan (named `setAcWindLevel` is a NoSuchMethod on DL3 per Dudu — use `AC_WIND_LEVEL_SET` fid), recirc, defrost. Overdrive uses fids; confirm which the trim accepts.
- Sunroof / sunshade set method names (Overdrive uses `SunroofCommand`/`SunshadeCommand` wrapping unknown method) — Dudu uses `BODYWORK_MOON_ROOF_OPEN_PERCENT` / `BODYWORK_SUNSHADE_PANEL_PERCENT`.
- Door lock/unlock method (doorlock device vs SETTING).
- Window set-% method — Dudu names `BODYWORK_{LF,RF,LR,RR}_WINDOW_PERCENT` but the numeric ids are not in Overdrive; ClusterNav `setBodyWindowCtrlState` is 0/1 only. **The "open 50%" answer needs the Dudu percent-fid numeric value from the framework jar.**
- Wiper-overhaul (maintenance) mode fid numeric (Dudu names `SET_{FRONT,REAR}_WINDSCREEN_WIPER_OVERHAUL_STATE`).

**E2. Numeric ids unconfirmed on this trim (probe `set/get` and read rc):**
- Every ADAS `*_SET` id (Overdrive marks all UNVERIFIED per trim) — verify one at a time; `-2147482648` = NOT_PROVISIONED → wrong path.
- Hazard double-flash command (Overdrive says inferred/unconfirmed; readback position-8 is FOOT lamp, not hazard).
- ESP `ADAS_ESP_STATE_SET` (resolveOrFallback guess).
- Seat rear ids 3/4 (Han 4-seat) — proven for front (Seal 2-seat) only.
- HUD-zin gate: `0x38B00030` still the unbeaten suspect (ClusterNav ADR 0002) — separate from these HUD switch ids.

**E3. Per-trim quirks:**
- `enablePurificationFunctionPrompt(0)` rejected on owner trim (rc=-2147482645) — popup may persist.
- Tyre TEMP fids read UNAVAILABLE until TPMS actually transmits (drive to wake).
- `sendInfo` opcodes + resolution codes (29/30/31) differ by cluster size + DL3 vs DL5.
- Write path: named-method (proven for seat/window/PM2.5) vs raw feature-id (Overdrive) — which the trim accepts per feature. Some HAL writes need the uid-2000 daemon (dashcast) or in-process app uid (ClusterNav); the daemon's *setting* writes silently no-op per Overdrive's HUD note, so HUD/setting writes must come from a real app process.

**E4. Read-path uid caveat:** Overdrive notes some CAN `get(int[])` reads throw for the app uid and only work from the uid-2000 daemon. Confirm which telemetry reads Kachi's in-process reflection can do vs which need the shell/daemon path.

---

# F. License / attribution (MIT clean-room)

- **Overdrive-release** — MIT © 2026 Yash Srivastava. `THIRD_PARTY_NOTICES.md`: MIT covers Overdrive's own code only; bundled BYD stubs (`app/libs/classes.jar`) + closed blind-spot components have their own licences. Overdrive credits byd-dashcast for the projection technique.
- **byd-dashcast** — MIT © 2026 Cedric Carre.
- **BYD HAL** (`android.hardware.bydauto.*`) — BYD/OEM API; we describe the API surface only, never redistribute the OEM jar.

**Practice boundary (mandatory):** Kachi **documents the API + technique** here and **reimplements clean** on ClusterNav's own infrastructure (`BydHal` reflection + shell/daemon). **Do NOT copy Overdrive/dashcast source verbatim.** MIT permits adaptation with attribution; this project's method is clean-room from the documented API surface. If any specific snippet is ever adapted, keep its MIT header + author credit in that file and list it in `CREDITS.md`. The numeric feature-ids and CAN opcodes themselves are facts about the BYD HAL (not copyrightable expression); the *code that uses them* is what must be re-authored.

---

## Follow-up (doc discipline R1/R3 — not done in this research turn)
Add this file to `docs/README.md` INDEX (Diagnostics, Current, 2026-09-10) and a `PROJECT-BACKLOG.md` entry for the "maximum launcher" plan it feeds; re-index the knowledge base after. The `docs/diagnostics/launcher-hal-re-overdrive-2026-09-08.md` doc remains the narrative companion; this file is the exhaustive lookup table.
