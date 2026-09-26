# Verify hàng loạt datum + control trên xe — 2026-09-21

> **Trạng thái**: Current · **Cập nhật**: 2026-09-22 · **Mục đích**: verify hàng loạt datum + control trên xe qua test-bridge `sweep info`/`sweep ctl` (2026-09-21).

> [ĐO] xe <car-ip> (1.89), test-bridge `sweep info` + `sweep ctl`. File gốc: `sweep-20260921-181714.json`.

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

## KẾT QUẢ verify GHI on-car 2026-09-21 (owner bấm/nhìn từng nút)

### ✅ CHẠY TỐT (14)
Đèn đọc · Đèn ban ngày · Lọc bụi (=chế độ tự-lọc) · Lọc ngay · Nhiệt độ AC · Quạt gió · Gió tự động (AUTO) · Sấy kính trước · Sấy kính sau (+gương) · Lấy gió trong/ngoài · Ghế mát · Ghế sưởi · Kính lái · Mở/đóng hết kính · Sạc không dây.

### ⚠ MỘT PHẦN / KHÔNG WORK
| nút | kết quả | lý do |
|---|---|---|
| `anion` (ion âm) | bật OK, **tắt KHÔNG được** | xe không cho tắt / giá trị tắt sai |
| `cam` + `camera_view` (camera 360 + góc) | **KHÔNG WORK** | `setAVMSwitchState`/`setDisplayMode` rc=0 nhưng không điều khiển được AVM view thật (owner tự bật bằng nút vật lý mới hiện). Backlog: RE đường điều khiển AVM. |
| `lock` (khoá cửa) | **KHÔNG WORK** | không khoá thật — NOT_PROVISIONED (khớp records: khoá cửa chính BẤT KHẢ) |
| `door` (mở khoá cửa) | **KHÔNG WORK** | như trên |
| `headlight_mode` (đèn pha) | **KHÔNG WORK** → owner BỎ | — |

### 🗑 BỎ (owner chốt — xoá nút + action)
- `seat_memory` (đã xoá 1.89) · `steer_heat` (xe không có) · `headlight_mode` · `powertrain_mode` (EV/HEV — xe thuần điện) · chế độ lái · chế độ phanh · `vol` · `cast` (nút) · `camera_view`/display · `cluster_music` · `brightness_gear` · `screen_rotation` · `energy_mode`.
- ⚠ GIỮ function chiếu-lên-cụm + bubble VietMap (chạy độc lập, KHÔNG đụng) — chỉ bỏ NÚT control.
- EV: bỏ xăng/dầu datum (`fuel_*`, `oil_level`).

### 🐛 Bug UI phát hiện (off-car)
- read-back báo "✗ xe không nhận lệnh" khi TẮT dù tắt thật (DRL) — read-back sai chiều tắt.
- nhãn "Sấy kính" → nên "Sấy kính trước".
- parser: "điều hòa X độ" + "lấy gió ngoài" chưa khớp.

### CHƯA test (owner nói đã dùng ngon lâu nay, khỏi test)
Kính 3 cửa còn lại · khoá trẻ em ×2 · cốp · cửa sổ trời · rèm trần.

## BUG sau RESTART 2026-09-21 (owner nổ máy, chưa đụng xe)

### Bug X — bóng VietMap KHÔNG tự lên
[ĐO log boot] VietMap MainActivity CÓ lên (`DPfinishLw Fullscreen window vn.vietmap.live/MainActivity` @18:56:41) rồi `destroySurface` @18:56:45 (bị hạ nền sau ~4s). VietMap process CÒN chạy (pid 4507) NHƯNG **`VMBluetoothService` (service dựng bóng) KHÔNG trong active services** (chỉ có Firebase). `VmOverlayPos` VẪN gửi `VM_BUBBLE_POS x=1339 y=100` đều nhưng không ai dựng bóng. ⇒ **Gốc: autostart hạ VietMap nền TRƯỚC khi VMBluetoothService kịp dựng bóng** (fix `isInMapActivity` 1.87 chưa đủ — MainActivity lên ≠ bóng đã dựng). Cần: chờ VMBluetoothService chạy (hoặc bóng thật xuất hiện) mới hạ nền, KHÔNG chỉ chờ MainActivity resumed.

### Bug Y — YouTube (app trong ô) ĐEN sau boot
[ĐO] VD `kachi-slot-0` được tạo (cycle ON/OFF/ON) NHƯNG **YouTube process KHÔNG chạy** (`pidof` rỗng) + không activity YouTube resumed ở đâu. ⇒ **Ô đen vì app trong ô KHÔNG được launch lại vào VD sau cold boot**. Đây là lỗ khôi phục app-in-slot lúc khởi động nguội: VD dựng nhưng nội dung (app) chưa được mở + đưa vào VD. Cần: boot-restore mở lại app đã lưu trong ô + host vào VD (đường `AppOpener`/`LauncherWindows.placeApp` phải chạy trên boot-path).

**Cả 2 = bug BOOT-PATH off-car** (cần nổ máy thật để tái hiện; adb `am start` không kích boot). Ưu tiên cao cho lượt off-car kế.

---

## ✅ ĐÃ XOÁ — 1.90 (off-car, 2026-09-21)

Owner chốt gỡ **9 nút + 2 datum** cho bản release production (xe **thuần điện**). Mechanical removal theo đúng
tiền lệ `seat_memory` (1.89).

**9 nút** (`ControlRegistry`): `anion` · `headlight_mode` · `powertrain_mode` · `screen_rotation` ·
`camera_view` · `cluster_music` · `brightness_gear` · `vol` · `cast`.
**2 datum** (`TelemetryRegistry`): `op_mode` · `energy_mode` (+ hai trường `CarStatus.Drivetrain.opMode`/
`energyMode` và hai mục `CarDataDemand.FAST_IDS`).

Số mới, đếm bằng máy: **29 nút** (trước 38) · **71 datum** (trước 73) · nhãn EN **244** (trước 255) ·
nút có lựa chọn **8** (trước 12) · đường đọc **15/29** (trước 17/38) · cụm ngữ pháp KEPT **284** /
DROPPED **171** / ENTRIES **1804**.

### ⚠ GIỮ — chức năng chiếu cụm KHÔNG bị đụng (đã xác minh, không suy luận)
[ĐO grep 2026-09-21] cả gói `modules/clustercast` (nút nổi `FloatingBubbleService` · `SimpleCastRuntime` ·
`SimpleCastCoordinator` · bóng VietMap `VmOverlayPosition` · `ClusterNavBridgeCast`) có **0** tham chiếu tới
`ControlRegistry` / `CapabilityCatalog` / `pick` / `kindOf`; và [ĐO] đường GHI của nút `cast`
(`AutoContainer.sendInfo`) **chưa bao giờ được nối** — `BydHalGateway.localSet` trả `false` cho `AutoContainer`
kèm chú thích *"cast do SimpleCastRuntime sở hữu, KHÔNG wire ở đây"*. Tức nút ấy là **nút chết từ đầu**.
⇒ Lý do cũ ghi trong `CapabilityCatalog.HIDDEN_FROM_PICKER["cast"]` (*"xoá dòng registry là gỡ luôn cả tính
năng"*) là **SAI** và đã bị số đo bác; mục ẩn đó gỡ theo. Mọi tệp cast **byte-identical** với HEAD.

### ⚠ Hai sai lệch so với mục "🗑 BỎ" ở trên — có chủ ý, owner chốt
1. **`steer_heat` KHÔNG xoá** — không nằm trong danh sách 9 nút owner giao lượt này.
2. **Datum xăng/dầu KHÔNG xoá** (`fuel_range_km` · `fuel_pct` · `oil_level`) — owner chốt **GIỮ cho PHEV
   Sealion 6**, ngược với dòng *"EV: bỏ xăng/dầu datum"* ở trên. Dòng ấy là ghi chép phiên xe; quyết định
   sau của owner thắng. `WorkspaceStateTest` có assert canh cả ba mã này **phải còn**.

### Hệ quả cần biết
- `Domain.DRIVETRAIN` nay **không còn nút nào** (`powertrain_mode` là nút duy nhất) — chỉ còn datum ĐỌC.
- Nợ *"hai nút một byte"* của dự án **đóng hết**: `COLLISION_PENDING_CAR` nay rỗng (cặp cuối
  `brightness_gear`/`hud_brightness` hết trùng vì cả hai mã đã đi).
- `headl` **quay lại** nhóm Đèn: va chạm id với `headlight_mode` tự hết, và nhóm *"Đèn"* phải có nút đèn pha.
- Datum ĐỌC **giữ nguyên**: `media_vol` (âm lượng) · `anion_state` (ion âm) · `headlight_feedback` (chế độ pha).
- 🗣 **Voice mất hai câu**: *"chiếu cụm"/"dừng chiếu"* và *"xem chế độ lái"* nay ra *"không hiểu"*. Chiếu cụm
  vẫn bật/tắt bằng **nút nổi** + **Cài đặt › Chiếu màn lên cụm**. Muốn trả lời lịch sự thay vì "không hiểu"
  thì thêm dòng `["che","do","lai"]` vào `VoiceFeatureGone.ALL` — **chưa làm**, vì đó là đổi HÀNH VI giọng nói
  và cần owner quyết (xem chú thích tại `VoiceLogCases0918Test`).

[ĐO] `./gradlew test --rerun-tasks --continue` 5 module: **5186 test / 0 fail / 0 error / 0 skip** ·
`BUILD SUCCESSFUL`. Chưa commit, chưa build APK.
