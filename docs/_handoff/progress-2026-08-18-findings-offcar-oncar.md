# Progress 2026-08-18 — Findings implemented (off-car) + on-car verify plan

> Nhánh: `feat/speed-limit-badge-hal-hud` (CHƯA merge main — chờ PASS trên xe).
> Nguồn: phiên phân tích log chuyến sáng (07:02–07:49, build `d4dd6d5`, 1128 file/640MB) + soi kênh dữ liệu VietMap + dựng bộ so-sánh HUD.
> Nguyên tắc: trace tới gốc; chỉ kết luận "không thể" khi có bằng chứng + điều kiện mở khoá (steering `trace-den-tan-cung.md`).

---

## 1. Findings phiên này (bằng chứng)

### 1.1 Nội suy khoảng-cách-tới-rẽ (interpolation)
- So `display_m` (app) vs `screenRead_m` (số GMaps đọc trên màn = ground-truth): **~88% khớp CHÍNH XÁC (median lệch 0 m)**; mean −13 m do outlier lúc chuyển đoạn/reroute (min −1580). → **KHÔNG cần đổi tham số**. Chỉ nên làm mượt lúc transition (tùy chọn, chưa làm — tránh regression).

### 1.2 Mã maneuver (GMaps notif)
- Phân bố: 3(×304 rẽ phải)/9(×207 đi thẳng)/2(×139 rẽ trái/đầu)/15(×16 tới nơi)/…; **vòng xuyến (11) chỉ ×1** → **thiếu coverage vòng xuyến** → cần chuyến của bạn (có vòng xuyến) để map icon.
- Mũi tên: 99.9% `arrow_src=live` (bitmap notif GMaps thật); sub-agent map 9 mũi tên (thẳng/trái/phải/giữ-trái/giữ-phải/nhập làn/vòng-xuyến-trái/tới-đích).

### 1.3 VietMap speed limit + alert
- Speed limit 50/60/70/80: hàng nghìn dòng, **tốt**.
- Alert ~20% dòng nhưng **chỉ 2 loại icon**, cả 2 là **biển cấm** (cấm đỗ `09c649…`, cấm dừng `49265…`) — **KHÔNG có icon camera**.

### 1.4 Nhãn ảnh screenshot (đã sửa từ bd09a4d)
- `fission -d0` = **màn CỤM 720px (có badge)**; `fission -d1` = infotainment 1080px. File chuyến sáng (`d4dd6d5`) bị **dán nhãn ngược** (main.png mới là cụm thật). Bản `bd09a4d`+ đã sửa (cụm = fission-d0).

### 1.5 Kênh dữ liệu VietMap (decompile 3.3.4 + xác nhận emulator/on-car)
| Kênh | Lấy được | Camera? | Ràng buộc |
|---|---|---|---|
| **Widget** | tốc độ, giới hạn, cấm dừng/đỗ, **giới hạn-sắp-tới + cự ly** (VMAlertWidgetProvider) | ❌ | bind (dễ) |
| **Accessibility** | turn distance + tên đường + tốc độ/giới hạn **khi đang dẫn** (nav card hiện) | ❌ | proven 3.3.4 ("100m Hẻm 7/8 Thành Thái"); verify chuyến sau |
| **BLE HUD (giả H50)** | FULL nav + **danh sách CAMERA (cmd 0x0D)** + turn + speed | ✅ | same-device không loopback → **cần phần cứng ngoài** |
| Android Auto Trip svc | full Trip | ✅ | phải giả host AA — bất khả thi |
| Notification | (VietMap không post nav notif) | — | chỉ GMaps có |
- 4 widget provider VietMap: `VMOnlySpeedLimitWidgetProvider`, `VMOnlyStickyAlertWidgetProvider` (cấm dừng/đỗ), **`VMAlertWidgetProvider`** (giới hạn-sắp-tới + cự ly), `VMTPMSWidgetProvider` (áp suất lốp — BỎ).

### 1.6 HUD kính lái (nghiên cứu trước)
- Giả thuyết gốc: cờ coding xe `0x38B00030 INSTRUMENT_HUD_NAVIGATION_MAP_CONFIG`. Xe owner (Seal) = `-2147482648` (NOT provisioned); xe bạn (CÓ HUD chạy) kỳ vọng `=1`. Đọc read-only qua `navopen-v4.jar getraw`. Khác biến thể: `vehicle_40d_code` owner=138 vs bạn=162.

---

## 2. OFF-CAR — ĐÃ IMPLEMENT (DONE)

| # | Việc | Trạng thái | Commit |
|---|---|---|---|
| 1 | Badge placement UI (kéo-thả) + gom 1 overlay (BUG-1) + full VietMap logging + Waze tag fix (`WazeHudLink`) + screenshot index | ✅ | `d4dd6d5` |
| 2 | Badge overlay **lifecycle fix** (idempotent init + DisplayManager.DisplayListener retry/teardown — sửa lỗi badge hỏng vĩnh viễn nếu display-1 chưa sẵn lúc init) + badge toggle (mặc định ON) | ✅ | `bd09a4d` |
| 3 | nav_access **source-tagged multi-app** (maps + revanced + `vn.vietmap.live` + `com.waze` + `com.chisadin.wazemod`; TYPE_ANNOUNCEMENT + WINDOW_CONTENT_CHANGED) | ✅ | `bd09a4d` |
| 4 | VietMap alert `place_holder` fix + screenshot fission-index fix (cụm=fission-d0) | ✅ | `bd09a4d` |
| 5 | **Bind `VMAlertWidgetProvider`** (slot mới `ALERT_FULL`) → đọc `warning_speed_limit_widget_text_view` + `warning_speed_distance_text_view` (+ second_*) → publish **giới hạn-sắp-tới + cự ly**; extend VietMapSignalLog CSV 13→17 cột. **Cũng fix bug warning_speed_* null** (trước đọc nhầm ở widget sticky). Degrade-safe, verbose-gated, off-thread. KHÔNG bind VMTPMS. | ✅ | (commit này) |
| 6 | Nội suy khoảng cách | ✅ giữ nguyên (88% khớp) | — |

**Test**: `:core:test` 525 pass · `:app:testDebugUnitTest` 388 pass (0 fail). Senior review VMAlert = **APPROVED** (0 P0–P2).

### Bộ công cụ cho anh em (teammate deliverables)
- `scripts/vehicle/hud-compare.bat` + `apks/navopen-v4.jar` (có `getraw`) + `docs/HUONG-DAN-THU-DATA-HUD.html` → so cờ HUD trên xe CÓ HUD.
- `scripts/vehicle/pull-drive-logs.bat` + `docs/HUONG-DAN-LAY-LOG-WINDOWS.html` → kéo log chuyến (auto-find adb, ra `D:\clusternav`, chạy từ thư mục bất kỳ).
- Self-test owner: `~/Desktop/HUD-TEST-XE-MINH.command` (verify getraw trên xe owner trước khi nhờ bạn).

---

## 3. ON-CAR — CẦN VERIFY (PENDING)

| # | Verify gì | Cách | Điều kiện PASS |
|---|---|---|---|
| V1 | Badge overlay lifecycle | reboot xe bằng nút nguồn vật lý → bật Nav+HUD → cast GMaps → badge hiện đúng dù display-1 chưa sẵn lúc init | badge không hỏng vĩnh viễn; retry/teardown hoạt động |
| V2 | VietMap accessibility turn/đường/distance | chạy VietMap dẫn thật (build data-collection tag nguồn) → xem `nav_access` có dòng `vn.vietmap.live` (turn/đường/distance) | có dòng vietmap với text dẫn đường |
| V3 | **VMAlertWidgetProvider** giới hạn-sắp-tới + cự ly | chạy qua đoạn có đổi giới hạn tốc độ → xem `vietmap_signal` cột up-limit/up-dist có số | cột up* có giá trị (không null) |
| V4 | Icon vòng xuyến | **chuyến của bạn** (có vòng xuyến) → thu mã maneuver 11 + bitmap | map được icon vòng xuyến |
| V5 | HUD provisioning compare | **xe bạn (CÓ HUD)**: chạy `hud-compare.bat` (USB) → `0x38B00030` | kỳ vọng `=1` (hoặc oversea provisioned) → xác nhận 100% là khác coding biến thể |

---

## 4. DROPPED — kèm bằng chứng + điều kiện mở khoá

| Bỏ gì | Bằng chứng bất khả thi (no-root, same-device) | Điều kiện MỞ KHOÁ |
|---|---|---|
| **VietMap camera-as-camera + full nav** (qua BLE-HUD giả H50) | Android không cho self-connect BLE GATT cùng adapter (research 2026-08-04 + review opus đồng thuận). Camera nằm ở cmd 0x0D chỉ qua BLE/map SurfaceView. | **1 thiết bị ngoài** (ESP32 / điện thoại 2) làm HUD giả → relay về cụm. Owner (14:39) quyết: **quá phức tạp → bỏ**. |
| **WazeMod same-device HLP** | HudLink chỉ phát HLP lên peer BT/BLE đã kết nối (doc: không transport giả). Same-device blocked. | Cùng thiết bị ngoài như trên (ESP32) — giải pháp CHUNG cho cả Waze + VietMap. |
| HUD kính lái hiện nav (xe owner) | Cờ coding `0x38B00030` NOT provisioned trên biến thể owner (40d=138) | Coding qua công cụ BYD OBD/UDS (không sửa được bằng app). Chờ V5 xác nhận. |

**Thay thế cho camera VietMap**: dùng **"giới hạn-sắp-tới + cự ly"** (VMAlertWidgetProvider, V3) — gần "cảnh báo tốc độ ép trước camera" nhất mà không cần phần cứng.

---

## 4b. Nguyên tắc "ĐO HẾT, kết luận sau" (2026-08-18 15:35) + ma trận capture

Owner chốt: **KHÔNG chọn sẵn kênh cho từng app** (giả định từ lần trước). Thu ĐỦ mọi kênh cho cả 4 app,
về đọc log mới kết luận app nào kênh nào có/không data — bằng SỐ LIỆU, không phải giả định.

Rà lại code → phát hiện **kênh notif đang lọc ngầm**: `handle()` drop notif không-phải-nav
(`!isNav && !hasDist return`) TRƯỚC khi ghi → giấu mất "Waze/VietMap có post notif nhưng rỗng nav".
**Đã sửa** (commit `16f7214`): thêm `NavNotifRawLog` ghi **RAW mọi notif của cả 5 gói** (tag pkg +
category + isNav + hasDist + hasLargeIcon + title/text/sub/big) TRƯỚC mọi drop. Verbose-gated, off-thread,
không đụng feed cụm. app 392 test pass, review APPROVED (sửa P2 vị trí + P3 collapse-key).

**Ma trận capture (build 4-kênh, sau khi sửa) — giờ MAXIMAL, không lọc theo giả định:**
| Kênh | GMaps | VietMap | Waze | WazeMod | Ghi chú |
|---|---|---|---|---|---|
| `nav_access` (a11y: text + **giọng/announcement**) | ✅ | ✅ | ✅ | ✅ | cả 5 gói tag pkg; **chỉ thấy app FOREGROUND** |
| `nav_notif_raw` (RAW mọi notif) | ✅ | ✅ | ✅ | ✅ | cả 5 gói, kể cả notif rỗng-nav — chạy NỀN |
| `nav_notif` (parsed nav) | ✅ | (nếu có nav) | (nếu có) | (nếu có) | chỉ notif qua được gate nav |
| `vietmap_signal` (widget) | — | ✅ | — | — | chỉ VietMap publish widget (bản chất) |
| `nav_arrow`/`nav_log` (arrow+interp) | ✅ | — | — | — | dẫn xuất từ notif GMaps |
- Kết luận per-app/per-kênh **để DÀNH cho lúc đọc log sau chuyến** — không đóng khung trước.

## 5. Checklist thu data chuyến sau (build 4-kênh)
- [ ] Cài **APK 4-kênh**: `~/Desktop/ClusterNav2.0-debug-thu-data-4kenh-20260818.apk` (VMAlert + raw-notif + a11y-tag).
- [ ] Bật Nav+HUD → **tắt/bật 1 lần** (a11y rebind config mới có typeAnnouncement).
- [ ] **PROTOCOL foreground**: luân phiên đưa từng app lên foreground ~1–2 phút (GMaps→VietMap→Waze→WazeMod)
      để a11y bắt từng cái (a11y CHỈ thấy app foreground). notif_raw + widget tự chạy NỀN.
- [ ] WazeHud (HLP) sẽ trống — bằng chứng "cần ESP32", không phải lỗi.
- [ ] GMaps dẫn (maneuver/arrow/ETA) + VietMap dẫn song song (speed/limit/alert/**up-limit+dist**).
- [ ] Đi qua **vòng xuyến** (V4) + đoạn **đổi giới hạn tốc độ** (V3).
- [ ] Kéo log: `pull-drive-logs.bat` → `D:\clusternav`.
- [ ] (Xe bạn) `hud-compare.bat` USB → gửi `0x38B00030` (V5).

---

## 6. Việc off-car còn tồn (backlog)
- Làm mượt interpolation lúc transition (tùy chọn, ưu tiên thấp — hiện 88% khớp).
- `.bat` auto-reconnect khi WiFi drop (tiện lợi).
- Viết `docs/diagnostics/hud-provisioning-compare-*.md` sau khi có readback xe bạn (V5).
- Xét vẽ "giới hạn-sắp-tới + cự ly" lên cụm (UX mới, sau khi V3 xác nhận có data).
