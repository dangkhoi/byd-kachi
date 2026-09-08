# ON-CAR 2026-08-13 — AMAP cluster-nav MENU activated + op39-in-app root cause

> Xe: BYD Seal DiLink 3.0, Android 10, không root. Version cài: **1.11 (vc 111)**.
> Kết nối `<vehicle-ip>:5555`. adb = `~/Library/Android/sdk/platform-tools/adb`.
> Screencaps phiên này: `oncar-op39-1.11-20260813/`. RE firmware: `~/Library/Caches/clusternav-re/`.

## TL;DR (2 kết quả lớn)

1. ✅ **KÍCH HOẠT ĐƯỢC menu "Nav trên cụm" của AMAP** (trước bị xám/disable). Lever = ghi HAL
   **`SET_NAVI_SCREEN_STATUS_SET` (feature id `0x4C10E015`)** trên `BYDAutoSettingDevice`.
   Menu hiện ra: **Đơn giản · Màn hình nhỏ · Toàn màn hình · OFF** (ghi chú: "Chỉ hỗ trợ Bản đồ Amap
   tùy chỉnh để điều hướng"). `Đơn giản` = "Giữa + ETA" (đúng cái muốn); `Màn hình nhỏ` = dải nav nhỏ ở đỉnh
   (đúng cái đang bị kẹt).
2. 🎯 **Root cause vì sao "trace op39 OK mà app không lên Giữa+ETA":** cơ chế THẬT làm nav-giữa là chuỗi
   **ghi HAL** (`SET_NAVI_SCREEN_STATUS_SET=3` + `INSTRUMENT_GUIDE_INFO_SIMPLE_SET` + distance + road) —
   chính là cái `navopen` làm và cái `BydHal.writeNavFrame()` trong app. NHƯNG trong app đường này **bị
   khoá chủ đích** (T7 fail-closed): `NavigationHudOwner` (wrapper của `writeNavFrame`) **không được khởi
   tạo ở production**, và có test (`PhysicalHudOwnershipTest`) **ép cho nó unreachable**. Production chỉ
   chạy: (a) broadcast AUTONAVI → `com.example.amapservice` (chỉ ra dải nhỏ ở đỉnh), (b)
   `ClusterNavLaneWidget` bắn **ch1000 op39** — đã chứng minh **no-op** trên xe này, (c) `HudMirrorController`
   = **no-op** hoàn toàn. → App không bao giờ chạy chuỗi HAL tạo ra "Giữa + ETA".

---

## PHẦN 1 — Menu AMAP trên cụm: cách kích hoạt (GHI NHỚ)

### Feature ids THẬT (từ firmware RE, đã verify)
| Tên | id | Device | Ý nghĩa |
|---|---|---|---|
| `SET_NAVI_SCREEN_STATUS_SET` | **`0x4C10E015`** | `BYDAutoSettingDevice` | Bật/chọn chế độ nav-trên-cụm (mở menu Đơn giản/Nhỏ/Toàn/OFF) |
| `INSTRUMENT_SEND_NAVI_STATUS_SET` | **`0x43E0003A`** | `BYDAutoInstrumentDevice` | Trạng thái đang-dẫn (2 = navigating, 4 = ended) |
| `INSTRUMENT_GUIDE_INFO_SIMPLE_SET` | **`0x43F01010`** | `BYDAutoInstrumentDevice` | Nội dung "simple nav" (icon rẽ) — feed cho chế độ Đơn giản |
| `INSTRUMENT_FRONT_CROSSING_DISTANCE_SET` | `0x43F01018` | instrument | cự ly tới ngã rẽ |
| `INSTRUMENT_TARGET_NEXT_PATHNAME_INFO_SET` | `1140461576` | instrument | tên đường (UTF-16LE bytes) |

### Công cụ đã chứng minh (trên xe, hôm nay)
`navopen.jar` sẵn ở **`/data/local/tmp/navopen.jar`** (uid shell). Chạy KHÔNG tham số → chạy demo "OPEN nav":
```
CLASSPATH=/data/local/tmp/navopen.jar app_process /system/bin com.byd.navopen.NavOpen
```
Output (tất cả rc=0):
```
== OPEN nav (status=2, screen=3) ==
  set INSTRUMENT_SEND_NAVI_STATUS_SET (0x43e0003a) = 2 -> rc=0
  set SET_NAVI_SCREEN_STATUS_SET     (0x4c10e015) = 3 -> rc=0
== FRAME icon=2 dist=250m road='Nguyen Hue' eta=8min mileage=5000m ==
  set INSTRUMENT_GUIDE_INFO_SIMPLE_SET (0x43f01010) = 2 -> rc=0
  set INSTRUMENT_FRONT_CROSSING_DISTANCE_SET (0x43f01018) = 250 -> rc=0
  ...
```
`getraw` 2 id này trả `-10011` (SET-only, không đọc được) — nhưng SET rc=0.

### Cơ chế (từ RE `com.example.amapservice/AmapService.java`)
- `mClusterType = SystemProperties.get("ro.build.system.fission_single_os")` = **"0"** (dual-OS) → cho phép
  gửi nav xuống cụm độc lập. (mClusterType=0 là ĐÚNG, không phải lỗi.)
- `setNaviScreenStatus(SET_NAVI_SCREEN_STATUS_SET, v)` → `mBYDAutoSettingDevice.set(...)`.
- Nav data (GMaps→ClusterNav→broadcast AUTONAVI) tới AmapService (`mIsGAODENaving=true`) → nó ghi
  `INSTRUMENT_GUIDE_INFO_SIMPLE_SET`, `发送独立仪表...` xuống cụm. **AmapService KHÔNG set SET_NAVI_SCREEN_STATUS
  lúc đang dẫn** (chỉ set lúc kill/shutdown) → chế độ hiển thị phụ thuộc GIÁ TRỊ MENU (persisted).

### Việc "hôm đầu dự án mò AMAPService" chính là: ghi `SET_NAVI_SCREEN_STATUS_SET=…` để mở menu.
State reset (factory/OTA/wipe) làm menu xám lại → sáng nay op39 vô tác dụng. Ghi lại HAL id này là mở lại được.

### ⚠️ Chưa biết (bước on-car kế): map GIÁ TRỊ `0x4C10E015` ↔ menu option
navopen dùng `=3`; menu đang chọn **"Toàn màn hình"**. Cần sweep 0/1/2/3 + chụp cụm để biết value nào =
**"Đơn giản" (Giữa+ETA)**. (Đoán: 0=OFF, 1=Đơn giản, 2=Màn hình nhỏ, 3=Toàn màn hình — PHẢI verify.)

---

## PHẦN 2 — Vì sao "trace op39 OK" nhưng "app không lên Giữa + ETA"

### Có HAI cơ chế khác nhau, app đi nhầm cơ chế
| | Cơ chế | Kết quả trên xe | App dùng? |
|---|---|---|---|
| **A. HAL trực tiếp** (navopen / `BydHal.writeNavFrame`) | set `0x4C10E015`=screen + `INSTRUMENT_GUIDE_INFO_SIMPLE` + dist + road | ✅ TẠO nav-giữa | **CÓ code nhưng BỊ KHOÁ** |
| **B. ch1000 opcode 39** (`service call AutoContainer 2 i32 1000 i32 39`) | opcode "简易导航" | ❌ **no-op hôm nay** (op6/7 đổi theme OK ⇒ kênh sống, nhưng op39 không đổi center) | **CÓ — đây là path production** (`ClusterNavLaneWidget`) |
| C. broadcast AUTONAVI | → `com.example.amapservice` → cụm | chỉ ra **dải nhỏ ở đỉnh** (theo mode menu) | CÓ |

"Trace op39 thành công" hôm qua = thực chất chuỗi **HAL (A)** hoạt động (hoặc op39 ch1000 ăn vì lúc đó
menu `SET_NAVI_SCREEN_STATUS` đang bật). "Đưa vào app" = app chạy **(B) ch1000 op39** — cơ chế yếu, no-op
trên xe này → không bao giờ lên Giữa+ETA.

### Bằng chứng đường HAL (A) bị khoá chủ đích trong app
- `BydHal.writeNavFrame()` (app/.../modules/hal/BydHal.kt) = **đúng chuỗi navopen** (set `0x4C10E015`=3 +
  `INSTRUMENT_SEND_NAVI_STATUS`=2 + `INSTRUMENT_GUIDE_INFO_SIMPLE` + dist + `TARGET_NEXT_PATHNAME`), chạy
  in-process qua reflection + `BydPermissionBypassContext` (spoof package `com.byd.dashcast`).
- `NavigationHudOwner.kt` bọc `writeNavFrame`, nhưng **không file production nào khởi tạo nó** (grep xác nhận).
  `initiallyEnabled = false`.
- `HudMirrorController.kt` (cái ClusterBroadcaster THỰC SỰ dùng) = **"T7 fail-closed gate", capability
  UNKNOWN, "deliberately has no HAL gateway"** — chỉ ghi nhận request, KHÔNG ghi HAL. `requestEnabled()` luôn
  trả `NO_OP_UNKNOWN`.
- `PhysicalHudOwnershipTest.kt` có test **"active runtime has no direct HUD writer"** → assert
  `ClusterBroadcaster/HudMirrorController/NavigationSpeedSignOwner` KHÔNG chứa `BydHal`/`writeNavFrame`, và
  **không .kt nào (trừ chính NavigationHudOwner/BydHal) được gọi `BydHal.writeNavFrame`** ("direct HUD write
  must be unreachable"). ⇒ đường HAL bị test KHOÁ.

### Vì sao bị khoá: nhầm "HUD kính lái" với "nav cụm"
Quyết định **T7** fail-closed vì cho rằng khả năng ghi HUD (kính lái) là UNKNOWN/chưa verify → tắt để an
toàn. NHƯNG cùng chuỗi HAL đó (`SET_NAVI_SCREEN_STATUS` + `INSTRUMENT_GUIDE_INFO_SIMPLE`) thực chất vẽ
**nav GIỮA trên CỤM** (đã chứng minh bằng navopen hôm nay) — không phải (chỉ) HUD kính lái (cái cần dealer
coding `0x38B00030`). ⇒ T7 vô tình khoá đúng tính năng người dùng muốn.

---

## PHẦN 3 — Hướng sửa (off-car) + bước on-car kế

### Sửa app (đề xuất — chờ user chốt trước khi code)
1. **Nối feed nav sống → đường HAL:** cho `NavNotificationListener`/nav-frame đẩy vào `NavigationHudOwner.push(icon, segMeters, road)` (gọi `BydHal.writeNavFrame`) mỗi frame — thay vì chỉ bắn ch1000 op39.
2. **Gỡ fail-closed T7:** cập nhật `HudMirrorController` capability (UNKNOWN → cluster-write đã-proven) hoặc
   thay bằng wiring `NavigationHudOwner`. Sửa `PhysicalHudOwnershipTest` (assertion "unreachable" phải đổi).
3. **Bỏ/hạ vai trò ch1000 op39** trong `ClusterNavLaneWidget` (no-op trên xe này) — giữ chuỗi HAL làm lever chính.
4. **Set đúng chế độ:** ghi `SET_NAVI_SCREEN_STATUS_SET`= <value "Đơn giản"> (cần map ở bước on-car). App
   chạy HAL in-process qua bypass-context (đã có) HOẶC qua dadb shell loopback chạy navopen.
5. Tôn trọng two-track: chỉ ghi khi nav-only + Cast OFF (như gate op39 hiện tại).

### Bước on-car kế (probe ngắn, parked)
- **Map `0x4C10E015` values → menu:** `navopen setraw setting 4C10E015 <0|1|2|3>` + `fission_screencap -d 0`
  từng bước → tìm value = **Đơn giản (Giữa+ETA)**.
- Với value đúng: feed liên tục `writeNavFrame` (status=2 + guide) → xác nhận nav-giữa ổn định (không phải
  1-shot hết hạn như demo navopen).
- Xác nhận app (qua bypass-context) ghi được `0x4C10E015` từ app-uid (navopen chạy uid shell; app-uid có thể
  cần dadb loopback → chạy navopen, hoặc bypass-context đủ).

---

## PHẦN 4 — ĐÃ IMPLEMENT (2026-08-13, off-car; verified compile + tests)

Wire đường HAL đã-proven vào feed nav sống, thay cho ch1000 op39 (no-op). 4 file:
- `modules/hal/BydHal.kt`: `writeNavFrame(..., screenMode = NAV_SCREEN_MODE_ON=3)` — tham số hoá value
  `SET_NAVI_SCREEN_STATUS_SET (0x4C10E015)` (trước hardcode 3). `const NAV_SCREEN_MODE_ON=3` + TODO map "Đơn giản" on-car.
- `NavigationHudOwner.kt`: nhận `screenMode`; delivery route **clear-sentinel** (icon0/dist null/road null) →
  `BydHal.clearNavFrame` (bug cũ: stop() ghi lại status=2); doc nói rõ nó vẽ **CỤM center simple-nav**, không phải HUD kính lái.
- `NavRepository.kt`: tạo+start `NavigationHudOwner` trong `createCoordinator`; adapter **CLUSTER_LANE** giờ push
  `(icon, seg, road)` vào owner, **gated `navOnlyMode()` = Cast master OFF** (two-track). `stop()` +
  tắt lane → `owner.stop()` (clear). Thêm import `SimpleCastRuntime` + field `hudOwner`.
- `PhysicalHudOwnershipTest.kt`: GIỮ ownership boundary (chỉ `NavigationHudOwner` gọi `writeNavFrame`) +
  giữ decouple ClusterBroadcaster/HudMirror/speed; ĐỔI intent "unreachable" → "wired via NavRepository two-track"
  (assert NavRepository chứa `NavigationHudOwner` + `navOnlyMode` + `castEnabled`).

**Verify (JDK17 homebrew):** `:app:assembleRelease` ✅ · `:app:testDebugUnitTest` ✅ · `:app:testVehicleTestUnitTest` ✅ ·
`:core/:car-integration/:vehicle-contracts test` ✅. Fail DUY NHẤT = `:offcar-planner ExpansionTransportFenceTest`
— **PRE-EXISTING**: commit 1.11 sửa `app/src/main/res/layout/activity_main.xml` (sha `bc7892fc…`) nhưng không cập nhật
pin T11 (`188efcc5…`); KHÔNG do thay đổi này (git status chỉ có 4 file trên + doc này). Owner cần re-pin/ revert riêng.

### ⚠️ CÒN PHẢI VERIFY ON-CAR (không làm off-car được)
1. **Map value `0x4C10E015` → "Đơn giản"**: `navopen setraw setting 4C10E015 0|1|2|3` + `fission_screencap -d 0`.
   Nếu ≠ 3 → sửa `NAV_SCREEN_MODE_ON`.
2. **App-uid ghi HAL có render không**: navopen proven từ uid shell; ghi in-process từ app-uid (BydHal bypass-context)
   CHƯA verify. Nếu fail → fallback chạy navopen qua dadb loopback (uid shell).
3. **Arrow icon**: `INSTRUMENT_GUIDE_INFO_SIMPLE_SET` nhận mã AMAP hay CAN — xem mũi tên đúng không.

## Bằng chứng / tham chiếu
- Screencaps: `oncar-op39-1.11-20260813/` — `op39_now_cluster.png` (energy center), `op6_day`/`op7_night`
  (theme đổi ⇒ ch1000 sống), `please_open_amap_cluster.png`, menu (ảnh user gửi: Đơn giản/Nhỏ/Toàn/OFF).
- RE: `~/Library/Caches/clusternav-re/diagnostic-amap/auto/sources/com/example/amapservice/AmapService.java`;
  feature ids trong `.../BYDAutoFeatureIds` (giá trị thật: `INSTRUMENT_SEND_NAVI_STATUS_SET=0x43E0003A`,
  `SET_NAVI_SCREEN_STATUS_SET=0x4C10E015`).
- App: `app/src/main/java/com/byd/clusternav/modules/hal/BydHal.kt` (writeNavFrame),
  `NavigationHudOwner.kt` (orphaned), `HudMirrorController.kt` (no-op T7), `ClusterNavLaneWidget.kt`
  (ch1000 op39), `NavNotificationListener.kt` (feed).
- On-car proof: 1.11 vc111; notif-listener granted; GMaps navigating; Cast OFF; loopback shell exit=0;
  ch1000 op39/op34 no-op; op6/op7 theme flip OK; `mode_dashboard` chỉ 0/1 (không phải lever).
