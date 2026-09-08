# Phiên ON-CAR 2026-08-17 — Tổng hợp & Kế hoạch OFF-CAR

> Nguyên tắc: `.kiro/steering/trace-den-tan-cung.md` — trace tận cùng, KHÔNG bỏ cuộc sớm, kết luận "không thể" phải kèm bằng chứng.
> Xe: `<IP-xe>` (địa chỉ LAN qua hotspot). main = `f7843c0` (1.0 ổn định, KHÔNG đụng). Mọi việc trên `feat/speed-limit-badge-hal-hud`.

---

# PHẦN A — TỔNG HỢP PHIÊN ON-CAR

## A1. Đã build + ship trong phiên (trạng thái hiện tại)
- Branch `feat/speed-limit-badge-hal-hud` (đã push origin):
  - `a2791a4` — debug telemetry: NavNotifLog, NavAccessLog, SegmentShotCapturer (chụp seg mỗi ngã rẽ), DiagActivity "Thử badge 50", puller `scripts/vehicle/pull-drive-logs.sh/.bat`, `docs/HUONG-DAN-LAY-LOG.md`.
  - `1c4485c` — badge chỉnh vị trí/cỡ (Prefs corner/size/nudge + DiagActivity live) + **debug auto-verbose** (`NavLog.verbose |= BuildConfig.DEBUG`).
- Đã cài trên xe: `com.byd.clusternav2` vc2/1.1 **debug** (5 suite xanh, review APPROVED, scan CLEAN).

## A2. Đã XÁC NHẬN CHẠY trên xe (bằng chứng thật)
- **Overlay badge vẽ trên cụm, đè cast** — khi CAST BẬT. Badge "60" lấy từ **VietMap** → data path VietMap OK.
- **GMaps → cụm "Giữa + ETA"**: "350 m · ← rẽ trái · Đường Vạn Hoa 5 · 4 phút · 6:56到" (fission -d 1).
- **Logging 100%**: nav_notif (notif GMaps thật: "80 m, Rẽ trái", "350 m, Đường Vạn Hoa 5"), nav_log (2309 dòng interp), nav_access, nav_arrow + png, `diag/seg-*-{gmaps,cluster}.png` (16 ngã rẽ, ảnh 2 màn hợp lệ). Debug auto-verbose ăn → **mai lái GMaps/VietMap là chắc chắn có data**.

## A3. Bug/ẩn số phát hiện (bằng chứng)
- **BUG-1** (P1): 2 badge cùng lúc (60 thật + 50 debug) = 2 instance `SpeedBadgeOverlay` riêng (ClusterSpeedBadgePort vs NavigationSpeedSignOwner.debugBadgeOverlay). Chỉ badge debug chỉnh được.
- **Badge ↔ cast** (ẩn số): overlay sống trên surface cast ảo (display 1) → **chỉ hiện trên cụm khi CAST BẬT** (owner xác nhận mắt: cast tắt → cụm OEM bình thường, không badge).
- **Ảnh cụm không tin cậy**: `fission_screencap -d 1` lúc ra cụm / lúc ra VietMap (không ổn định) + không bắt overlay; `screencap -d 1` bắt overlay nhưng nền đen (không có OEM base). → seg-*-cluster ngày mai CHƯA đáng tin.
- **Waze**: WazeMod HUD Link kẹt "Starting HUD Link"; app đọc SAI tag (`WazeHUD` vs doc `WazeHudLink`); `hud_link_log` chỉ tap transport ĐÃ kết nối. (Chi tiết + kế hoạch: Track W.)

## A4. Kiến trúc (ground truth, xác nhận bằng dumpsys + mắt owner)
- **Display 0** = màn giữa 1920×1080 (GMaps/VietMap chạy ở đây). **Display 1** = virtual `fission_bg_xdjaVirtualSurface` 1920×720 (owner com.xdja.containerservice) = surface **cast lên cụm**.
- **Cụm** = OEM base + "Giữa + ETA" (render từ **tín hiệu GMaps**) + overlay badge (chỉ khi cast on).
- Phân vai nguồn: **GMaps = dẫn đường/turn-by-turn**; **VietMap = giới hạn tốc độ (badge)**. (Xác nhận chính xác lại từ log off-car.)

---

# PHẦN B — KẾ HOẠCH OFF-CAR (theo thứ tự ưu tiên)

## ƯU TIÊN 1 — Làm TRƯỚC chuyến mai (để chuyến mai thu data có ích)

### Track B1 — Gộp 1 badge (BUG-1)
- NavigationSpeedSignOwner tạo **1 `SpeedBadgeOverlay` dùng chung**, inject vào ClusterSpeedBadgePort; debug force-show + refreshLayout dùng chính instance đó.
- ClusterSpeedBadgePort: nhận overlay qua ctor thay vì tự `new`.
- Kết quả: 1 badge duy nhất, chỉnh live (60 thật hay 50 force-show cùng 1 cửa sổ).
- Verify: chỉ 1 instance được new; 5 suite xanh; rebuild; cài lại.

### Track B2 — Ảnh cụm đáng tin
- `fission -d 1` không ổn định + không bắt overlay. Đổi cách chụp seg-cluster:
  - Dùng `screencap -d 1` cho lớp overlay; cân nhắc chụp cả composite (OEM) + overlay rồi ghép; hoặc tìm index/lệnh đúng cho cụm hợp thành.
  - Mục tiêu: seg-*-cluster phản ánh ĐÚNG cái tài xế thấy (OEM + Giữa+ETA + badge).
- Đổi tên seg `-gmaps` → `-main` (display 0 là màn giữa, không chắc GMaps).

## ƯU TIÊN 2 — Waze (trace tận cùng; ĐỘC LẬP, không chặn chuyến mai)

### Track W — WazeMod HUD Link (chi tiết)
**Bối cảnh**: TỪNG chạy OK trên emulator (đã cài WazeMod) → lên xe không chạy = CÓ regression tìm được.
**Bằng chứng on-car**: WazeMod (`com.chisadin.wazemod`) chạy; "Waze HUD Link" kẹt "Starting"; tag `WazeHUD` rỗng; owner bật "log payload" vẫn 0.
**Facts từ doc** (wazemod.chisadin.id.vn/tai-lieu/esp32{,/android-hud-link}):
- HudLink → transport **Classic SPP** (`00001101-0000-1000-8000-00805F9B34FB`) hoặc **BLE GATT** (service `8a7e0001-4d6e-4c48-9a9d-484c504c0001`, TX `..0002` write-w/-response, RX `..0003` notify+CCCD `0x2902`). Frame HLP/1 JSON ≤512B, `\n`. Handshake `dev`→`hi`, `ping`/`pong` 5s.
- Prefs `waze_hud_gw`: `hud_link`, `hud_link_log` (log RX/TX **đã kết nối**, tag **`WazeHudLink`**/`WazeHudLink-BLE`), `hud_link_transport` (0/1/2), `hud_link_device_address/name`, `hud_link_status/error`.
- Fields khớp parser: nav/spd/lim/over/trn/trn2/dst/exit/st/st2/eta/rmin/rkm/avg + **alr/alrD/alrV/alrs**. Alert enum 1..9 (police/camera/hazard/…).

**W1 — Tái lập trên EMULATOR** (biết cơ chế "working" thật, không đoán):
- WazeMod trên emulator: **version**? **tag log thật**? Có phát HLP ra logcat **khi CHƯA kết nối device** không? App (đọc WazeHUD) bắt được không? Có "device" ảo kết nối không?

**W2 — Diff xe vs emulator** → root cause (chứng minh/bác bỏ):
- H1: đổi tag (`WazeHUD`↔`WazeHudLink`). H2: bản mới cần connection mới log. H3: BT emulator cho loopback/peer ảo, BT xe thật không.

**W3 — Fix theo root cause:**
1. Sửa tag app WazeHudSource: `WazeHUD` → `WazeHudLink` (+ `WazeHudLink-BLE`) — làm ngay.
2. **Tự làm RECEIVER trong app — app đóng vai HUD device** (doc cho đủ UUID/handshake): BLE GATT server hoặc Classic SPP → WazeMod nối thẳng vào app → parse HLP/1 trực tiếp (nav+tốc độ+cảnh báo), khỏi phụ thuộc logcat.
3. **Test loopback cùng-máy trên EMULATOR trước**, rồi xe.

**W4 — Chỉ khi có bằng chứng** (log lỗi self-connect trên xe, thử ≥2 cách) mới kết luận + fallback (notification Waze nav-only / peer ngoài tối thiểu) + điều kiện mở khoá. KHÔNG bỏ.

## ƯU TIÊN 3 — SAU chuyến mai

### Track L — Phân tích log chuyến có-tốc-độ
- Phân tích nav_notif/nav_log/nav_access/arrow + seg → chỉnh **arrow / road / distance / interpolation**.
- Xác nhận nguồn: GMaps cấp gì, VietMap cấp gì (từ log thật).

---

# PHẦN C — THỨ TỰ THỰC THI & OPEN QUESTIONS

**Thứ tự**: B1 (gộp badge) → B2 (ảnh cụm) → [chuyến mai lấy log] → L (phân tích). Track W (Waze) chạy song song bất kỳ lúc nào (độc lập, cần emulator).
**Workflow**: mỗi track viết spec HTML `docs/specs/<slug>.html` trước khi code (plan→approve→code→test→review→scan).

**Open questions (trả lời bằng bằng chứng):**
1. [W] Emulator: WazeMod phát HLP ra logcat khi CHƯA kết nối device? Tag gì? Version?
2. [W] Android cho 1 app làm BLE GATT server + app khác làm central nối **cùng máy** không? (test emulator trước)
3. [Badge] "Badge chỉ hiện khi cast on" là đúng thiết kế (mục tiêu = cast GMaps + overlay speed) hay cần badge độc lập cast? (owner quyết)
4. [Screenshot] Lệnh/index nào chụp được cụm hợp thành + overlay cùng lúc?
