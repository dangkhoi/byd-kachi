# Verify hàng loạt datum + control trên xe — 2026-09-21

> [ĐO] xe 172.20.10.8 (1.89), test-bridge `sweep info` + `sweep ctl`. File gốc: `sweep-20260921-181714.json`.

## ĐỌC (telemetry) — 73 datum: 59 OK · 9 rác/sentinel · 5 rỗng

### ✅ 59 datum ĐỌC ĐÚNG (mẫu)
`soc=91` · `speed=0` · `ev_range_km=527` · `pm25_value=[7,21]` · `pm25_outside=[7,21]` · `inside_temp=24` · `soh_oem=98` · `op_mode=1` · `energy_mode=3` · `odometer=5941` · `consumption_50km=14.9` …

### ⚠ 9 datum RÁC/sentinel (đọc được nhưng giá trị vô nghĩa)
| datum | giá trị | lý do |
|---|---|---|
| `fuel_range_km` | 2046 | **xe EV — không có bình xăng** ⇒ nên ẨN |
| `fuel_pct` | 255 | sentinel (EV không xăng) ⇒ nên ẨN |
| `oil_level` | 255 | sentinel (EV không dầu) ⇒ nên ẨN |
| `ev_mileage_km` | 1048575 | sentinel tràn — key/route sai |
| `trip_km/hours/kwh` | −10013 | sentinel lỗi — cả 3 cùng lỗi ⇒ nghi chung 1 getter sai |
| `sunroof_pos` | 65535 | sentinel — không có cảm biến vị trí / key sai |
| `volt_12v_level` | 65535 | sentinel — key sai |

### ✗ 5 datum RỖNG (không đọc được)
| datum | tier | key | ghi chú |
|---|---|---|---|
| `tailgate_status` | **PROVEN** | `getHatchDoorStatus` | ⚠ đánh dấu PROVEN nhưng rỗng — sai nhãn/binding |
| `batt_temp` | OVERDRIVE | `getBatteryTemp` | experimental |
| `gear` | OVERDRIVE | `getCurrentGear` | experimental |
| `cabin_temp` | OVERDRIVE | feature 1031798832 | experimental |
| `target_soc` | NEEDS_CAR | `SET_DR_SOC_TARGET` route=None | không có đường đọc (đã biết) |

## GHI (control) — 39 nút: 38 có đường ghi · 1 chết
- ✗ `headl` route=none (trùng `headlight_mode` — đã biết, backlog).
- 38 nút có route (named/feature/local). ⚠ "có route" ≠ "ghi chạy thật" — cần thử GHI từng nút (intrusive, chờ owner OK vì đang on-car).

## Việc rút ra (backlog)
1. **EV-HIDE**: ẩn `fuel_range_km`/`fuel_pct`/`oil_level` (xe EV, luôn rác) — off-car.
2. **TRIP-FIX**: `trip_km/hours/kwh` = −10013 → RE getter đúng trên xe.
3. **SENTINEL**: `ev_mileage_km`/`sunroof_pos`/`volt_12v_level` = sentinel → RE key.
4. **TAILGATE**: `tailgate_status` PROVEN nhưng rỗng → kiểm lại binding/tier.
5. **CTL-WRITE-VERIFY**: thử ghi 38 nút từng cái trên xe (chờ owner).
