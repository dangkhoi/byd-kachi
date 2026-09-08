# On-Car Checklist — August 2026 (Speed-Limit Badge + HAL + HUD)

> Duration target: ≤20 min. Xe ĐỖ, số P, phanh tay.
> Script: `./scripts/vehicle/hud-cluster-probe.sh <ip:port>`
> APK: ClusterNav 2.0 v1.1 (com.byd.clusternav2, versionCode 2)

## Pre-flight

- [ ] adb connect `<vehicle-ip>:5555` — `adb get-state` = device
- [ ] `dumpsys package com.byd.clusternav2 | grep versionName` → `1.1`
- [ ] Nav+HUD bật trong app (công tắc master ON)
- [ ] VietMap/Waze đang chạy nền (nguồn tín hiệu tốc độ)

## Group A — Cluster nav render (proven, sanity ≤2 min)

- [ ] A1: Amap broadcast → cụm hiện icon rẽ + 444m + tên đường
- [ ] A2: navopen frame → cụm hiện icon + tên đường

## Group B — Cluster speed-limit via ISA HAL (≤5 min)

- [ ] B1: ISA 0x4B40001C = 50 → cụm hiện biển "50"? `____`
- [ ] B1: ISA = 80 → cụm đổi sang "80"? `____`
- [ ] B1: restore → cụm xoá biển? `____`
- [ ] B2: ISA type+value → biển có vẽ khi kèm type? `____`

## Group C — HUD nav guidance on windshield (≤5 min)

- [ ] C1: HUD master ON + content gate + nav feed → KÍNH LÁI có icon + tên đường? `____`
- [ ] C3: INSTRUMENT HUD nav-map (0x32B1102E=2) → HUD? `____`

## Group D — HUD speed-sign ADAS gate (≤2 min)

- [ ] D1: ADAS smart-speed-limit-control → HUD hiện biển tốc độ? `____`

## Group E — App speed-limit badge on cluster display 1 (≤5 min)

- [ ] E1: `dumpsys window` có SpeedBadge overlay trên display 1? `____`
- [ ] E2: ISA baseline read = `____`
- [ ] E3: Chạy qua biển tốc độ (VietMap/Waze active) → badge đỏ-trắng-số hiện góc trên-phải cụm? `____`
- [ ] E4: ISA read-back sau khi app ghi = `____` (≠ baseline = HalSpeedSignPort hoạt động)

## HUD Windshield Coding

- [ ] `hal_get setting 38B00030` = `____` (cần =1 để W-mode hoạt động; nếu =0 → cần vehicle coding, không phải app)

## Decision Matrix (điền on-car)

| Track | Verdict | Evidence | Note |
|-------|---------|----------|------|
| T3 cluster badge overlay | ☐ PASS ☐ FAIL ☐ BLOCKED | E1+E3 | |
| T1 HAL ISA write (HUD port) | ☐ PASS ☐ FAIL ☐ BLOCKED | E4 ≠ E2 | |
| T2 HUD windshield | ☐ PASS ☐ FAIL ☐ BLOCKED | C1 visual | cần 0x38B00030=1 |
| B cluster ISA sign (OEM render) | ☐ PASS ☐ FAIL ☐ BLOCKED | B1 | |

## Post-session

- [ ] `adb pull /sdcard/Android/data/com.byd.clusternav2/files/logs/` (nếu có)
- [ ] Copy log file `hud-cluster-probe-*.log` → `docs/diagnostics/`
- [ ] Fill decision matrix above → commit
