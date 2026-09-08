# B3 screen-capture nav — kiểm thử end-to-end trên emulator (2026-08-20)

> Diagnostics · nguồn: `docs/PROJECT-BACKLOG.md` B3/B3.5–B3.12 · spec `docs/specs/waze-vietmap-screen-capture.html`.
> **Mục đích:** ghi lại phiên test NGHIÊM TÚC (Waze/VietMap/GMaps dẫn THẬT trên emulator) + các bug tìm ra +
> ĐIỂM RESUME, để lần sau quay lại KHÔNG phải dò lại từ đầu.

## TL;DR
- **Đã chứng minh end-to-end:** B3 chụp màn → crop mũi tên Waze → classify → publish **đúng `amap=2` (rẽ TRÁI)**
  trên emulator với **Waze dẫn thật**. (Trước khi sửa: classify nhầm ROUNDABOUT.)
- **Ống dẫn OK; tầng nhận-diện mới xong cho Waze-TRÁI.** Còn: mở rộng template đủ maneuver/app, VietMap
  (bị B3.10 chặn), camera VietMap, và 2 bug tín-hiệu-giả (B3.10/B3.11) + giới hạn nền tảng (B3.12).

## Môi trường test (QUAN TRỌNG để lặp lại)
- Emulator `emulator-5554`, AVD `clusternav`. Có sẵn: WazeMod (`com.chisadin.wazemod`), GMaps
  (`com.google.android.apps.maps`), VietMap (`vn.vietmap.live`), app `com.byd.clusternav2`.
- **Emulator từng CRASH giữa phiên** (biểu hiện như "không vào wifi": ping 50% loss rồi rớt hẳn, `pgrep
  qemu-system` rỗng). Khởi động lại: `emulator -avd clusternav -dns-server 8.8.8.8,8.8.4.4 -no-snapshot-load`.
- **Capture cần shell ROOT:** `screencap` ghi vào app-cache (`/data/user/0/<pkg>/cache/...`) → *Permission
  denied* với shell thường; ghi `/data/local/tmp` thì OK. On-car dadb-shell là **root** nên chạy được →
  trên emulator phải `adb root` (adbd uid=0) cho khớp môi trường xe.
- **Resolution:** đặt `wm size 960x720` cho khớp calibration màn xe (mặc định emulator 1920×1080 làm rect trật).
- **Bật pipeline:** prefs `clusternav_prefs.xml` (`enabled=true`, `nav_verbose_log=true`); cấp notif-listener +
  a11y; **toggle notif-listener (disallow→allow)** để `onListenerConnected` re-fire (init `NavLog.verbose` +
  `ScreenCaptureNavSource.start`). Sau reinstall/cold-boot phải toggle lại (verbose là in-memory).
- **Bật dẫn thật:** Waze `am start -a android.intent.action.VIEW -d 'waze://?ll=<lat>,<lng>&navigate=yes'
  com.chisadin.wazemod` + `adb emu geo fix <lng> <lat>`. VietMap phải mở route THẬT (đừng `monkey` suông —
  màn tĩnh không có mũi tên/camera).
- **Công cụ dựng template:** log verbose `arrow-sig pkg=… WxH sig=<225-bit>` (thêm ở `handleArrow`) in chữ ký
  MỖI frame → copy chuỗi 225-bit vào `WazeArrowRegistry.BUILTIN` với nhãn maneuver (từ vựng `ManeuverRegistry`).

## Chuỗi nguyên nhân đã bóc (mỗi tầng có bằng chứng)
1. Emulator crash → cold-boot lại (KHÔNG phải lỗi B3).
2. `screencap` fail (Permission denied app-cache) → `adb root` (khớp xe root).
3. Crop rect seed OpenBYD `(26,218,208,298)` nhắm y=218 nhưng banner Waze ở top-left y≈35 → **B3.9**: đổi
   sang **tight arrow-only `(38,38,120,110)`** @960×720 (crop cả banner làm mũi tên nét-mảnh biến mất khi hạ
   mẫu 15×15 → chữ ký ~toàn 0). Entry theo geometry, giữ seed OpenBYD làm null-default.
4. Classify nhầm ROUNDABOUT → **B3.6**: `ManeuverRegistry` toàn icon GMaps/Mapbox; glyph Waze khác. Thêm
   `WazeArrowRegistry` (khớp song song) + template Waze-TRÁI thu qua `signatureBits` → **`amap=2` đúng**.
5. Gate đóng sau app-restart lúc Waze đã foreground → **B3.7**: `ForegroundWindowFilter` (đường window-UNKNOWN)
   chỉ publish khi `WINDOW_STATE_CHANGED`/same-pkg → không bootstrap. Fix: dùng `rootInActiveWindow.packageName`
   = foreground THẬT (overlay không phải active window) → bootstrap đúng + vẫn loại overlay WazeMod.

## Trạng thái từng issue (chi tiết ở backlog)
- **B3.5** ✅ `CaptureBoundsHeuristic.pick` loại node vụn (min 32×24) + score size/zone/aspect. +6 test.
- **B3.6** 🔧 cơ chế `signatureBits`+`WazeArrowRegistry` + Waze-TRÁI PROVEN. CÒN: phải/thẳng/quay-đầu/vòng-xuyến + VietMap arrow.
- **B3.7** 🔧 overlay-filter + rootInActiveWindow bootstrap. CÒN B3.10.
- **B3.8** 🔧 `targetsForPackage` VietMap→[ARROW,CAMERA] + `routePlans` + tick per-target (code+unit). Emulator CHƯA verify (B3.10 chặn).
- **B3.9** ✅ tight arrow rect (38,38,120,110)@960×720.
- **B3.10** 🔲 [BUG P2] `SourceArbiter.activeSource` dính WazeMod (poll logcat còn tươi sau force-stop) → override foreground → VietMap route sai pkg.
- **B3.11** 🔲 [BUG **P1**] crop TRỐNG (sig toàn-0, vd màn home/giữa 2 maneuver) vẫn khớp template thưa (Hamming 0→template ≤18) → **phát `amap=2` GIẢ**. Guard `contrast<30` chưa đủ → cần chặn signature quá THƯA (số bit set < ngưỡng).
- **B3.12** 🔲 [LIMIT nền tảng] B3 KHÔNG đọc được app khi BACKGROUND — `screencap`/mirror chỉ chụp foreground; app background không render. Verified (HOME→sig toàn-0). Nav-background chỉ qua NOTIFICATION.

## Liên hệ 3 bug tín-hiệu-giả-khi-background
Gate vẫn mở khi app rời foreground (B3.10 arbiter dính nguồn cũ) → B3 chụp màn HOME → crop trống → B3.11 khớp
nhầm → publish rẽ-trái GIẢ. **Sửa B3.10 (gate đóng khi app rời foreground) + B3.11 (chặn sig thưa) = dứt.**

## RESUME POINT (làm tiếp khi quay lại)
1. **B3.11 (P1)** — guard signature quá thưa trong `ManeuverSignature` (đếm bit set < ngưỡng → null). Test off-car (frame all-zero → classify null). Loại tín-hiệu-giả.
2. **B3.10** — arbiter/gate nhường foreground khi data-source stale/app rời foreground (đóng gate → không chụp home).
3. **VietMap retest** (sau B3.10): verify `routePlans` ra CẢ ARROW+CAMERA; thu template mũi tên VietMap (banner trái, có thể tái dùng/khác template Waze); camera VietMap = icon neo-bản-đồ DI CHUYỂN → fixed-rect vô vọng, cần a11y-bounds đúng node hoặc dò động (OQ4, chưa có template).
4. **Mở rộng template Waze**: chạy route có rẽ-phải/đi-thẳng/quay-đầu/vòng-xuyến → thu `arrow-sig` → thêm `WazeArrowRegistry.BUILTIN`.
5. **GMaps** retest (GMaps vốn đọc qua notification; B3-capture GMaps có thể khớp registry GMaps đúng).
6. **Senior review** (sub-agent) + hoàn tất **document** (spec §Implementation-Log, re-index knowledge).

## Đã commit
- `87fd1ad` screencap fallback (off-car capture) · `3037fd4` rect 960×720 seed + đăng ký B3.5–B3.9 ·
  `ad4a454` B3.5/B3.7/B3.9 fix + B3.6 Waze-TRÁI end-to-end + B3.8 code + test xanh.
- Log `arrow-sig` (verbose) GIỮ làm công cụ dựng template. Rect + template + fixes đều trong `ad4a454`.
