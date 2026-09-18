# Voice: nhận-SỐ/câu-HỎI (đã ship 1.73) + phím-thoại kẹt bind (finding xe sáng 18-09)

> **Trạng thái**: Current · **Ngày**: 2026-09-18 · **Mục đích**: Ghi finding từ log xe 81 lượt (đêm 17-09) đã fix ở 1.73, và finding ADB trên xe sáng 18-09 về phím-thoại mất tác dụng.

## 1. VOICE nhận-SỐ + câu HỎI — ĐÃ FIX, ĐÃ ĐĂNG OTA 1.73 (74)

Nguồn: 81 lượt log xe thật (`/tmp/kvlog`). Model NGHE đúng; lỗi ở tầng PARSE.

| Log SAI | Nay | Test |
|---|---|---|
| «tăng/giảm nhiệt độ 24 độ» → ±24 (22+24 kẹt trần 33) | ĐẶT=24 (số trong dải 17..33 = setpoint; gió/âm lượng giữ tương đối) | [ĐO] `VoiceIntentParserTest` +4 |
| «chỉ **số** bụi mịn» → gear (nhãn "Số" nuốt câu) | bụi mịn (datum dài nhất thắng + bỏ dẫn "chỉ số") | [ĐO] |
| «nhiệt độ / máy lạnh bao nhiêu độ» → rỗng / media_vol | nhiệt AC (`inside_temp`) | [ĐO] |
| «tắt bụi mịn» → mở Google Maps | tắt máy lọc (pm25) | [ĐO] |

Tách `VoiceControlParse.kt` (VoiceIntentParser về 451 dòng < trần 500). [ĐO] 5 module 0 đỏ (core 2224). APK `Kachi-1.73-release.apk` vc74 sha `953b5510…`, push `origin/main 5ba7819`.

**Còn nợ trong log** — [KIỂM 18-09 trên emulator, đo parse thật của 1.73]: 4/5 câu misfire nguy hiểm **ĐÃ trả Unknown an toàn** trong 1.73 (KHÔNG còn bắn nhầm), đã KHÓA bằng test `log xe · cau feature-da-go KHONG ban nham control`:
- «chuyển chế độ lái» → Unknown (không bật đèn pha) · «xi nhan» → Unknown (không bật đèn ngày) — feature đã gỡ, khớp-mờ nay không bắn nhầm.
- «tất cả cửa khóa hay mở» → Unknown (không mở cửa sổ trời) — câu HỎI, chưa có datum ĐỌC khóa cửa (→ backlog: thêm datum door-lock).
- «chỉ số xăng» → Unknown (không trả tốc độ sai) — fix "chỉ số" của 1.73 làm nó thành ĐỌC-không-datum (BEV không có "xăng").
- **Còn lại (feature gap, chưa fix)**: «mở một nửa kính» → mở HẾT kính (windows_all, có cổng CONFIRM) — thiếu lệnh ghi kính ½ (spec §4.5); reply STEP tương đối nói TRỄ (readback nền); ASR nghe méo («mở mắt đích», «tắc ra sao»).

## 2. PHÍM-THOẠI mất tác dụng — finding xe sáng 18-09 [ĐO qua ADB]

**Cách vào**: `adb` binary trên Mac bị macOS chặn LAN (nc/socket tới `<car-ip>:5555` OK, daemon adb báo "No route"). Vòng qua bằng **client adb thô Python** (ký AUTH bằng `~/.android/adbkey`, xe đã uỷ quyền). Dữ liệu dưới là **[ĐO] thật trên xe**.

**Gốc — KHÔNG phải mất quyền:**
- `enabled_accessibility_services` VẪN chứa `com.byd.launcher/com.byd.clusternav.modules.navaccess.NavAccessibilityService`; master `accessibility_enabled`=1 → **grant đúng**.
- `dumpsys accessibility`: NavAccessibilityService ở **`Binding services`**, KHÔNG ở **`Bound services`**. Chỉ StatusBar (hệ) là Bound.
- **Trợ lý zin BYD (iFlytek `com.byd.vrassistant.xf`) CŨNG kẹt "Binding"** y hệt → wedge tầng hệ thống, không riêng app mình.
- Launcher sống (pid 23546, 53 thread). `load average: 13.48 / 14.52 / 16.36` — **CPU bão hoà** (uptime 1,5 ngày).

⇒ Phím không ăn vì service trợ năng **không bind xong** (kẹt "Binding"), tương quan với **CPU quá tải** — AMS bị đói không hoàn tất bind.

**Vì sao "Kiểm tra / Sửa ngay" vẫn không lành** — [ĐÍNH CHÍNH so với nhận định ban đầu]: đọc code (`ClusterNavBridgeKeys.checkFix` → `NavConnect.grantAccessibility(reset=true)` → `forceRebindIfNeeded` + `AccessibilityRebind`) cho thấy nó **KHÔNG phải no-op** — nó đã làm **force-rebind toggle đúng** (remove→pause→re-add, nhắm ĐÚNG component `com.byd.launcher/...` qua `BuildConfig.APPLICATION_ID`, giữ nguyên service OEM) + xoá single-flight. Nhận định "chỉ ghi đè" ban đầu là SAI. Lý do thật nó không lành:
- **CPU bão hoà**: sau toggle đúng, AMS vẫn không bind xong (iFlytek zin cũng kẹt → đói CPU toàn hệ);
- **`GRANT_TIMEOUT_MS = 9s`** (`NavConnect`): dưới load 14, các lệnh `settings`/`dumpsys` qua dadb chậm → settle 1.2s + pause 0.8s + nhiều lượt đọc chậm có thể vượt 9s → worker bị interrupt GIỮA toggle (`finally` re-add an toàn nhưng bind chưa xong) → trả false → "Sửa ngay" báo lỗi.

⇒ Cơ chế force-rebind ĐÚNG; **nút thắt là CPU (load 14)**. Cả hai đường (bind + "Sửa ngay") đều thua khi hệ thống quá tải.

### 2b. Đo hiệu năng (emulator 1.73 + tính lại số xe) — [ĐO 18-09]

- **Emulator (emulator-5554, cài 1.73, idle)**: `loadavg 0.35` (2 core); trong 6s launcher chỉ tiêu ~9 jiffies (main 3 · RenderThread 3 · hud-keepalive 2 · Jit 1). **Launcher IDLE hoàn toàn sạch — KHÔNG có thread spin.** 33 thread (xe: 53 — chênh do xe có nav/voice/cast chạy thật).
- **Tính lại từ số xe sáng nay**: `/proc/23546/stat` utime=82726 + stime=23877 = 106603 jiffies ≈ **1066s CPU**; process chạy ~16,5h (từ lastUpdate 09-17 16:17) ⇒ **~1,8% CPU trung bình**. Một thread spin sẽ ăn ~1 core (~100%). ⇒ **launcher KHÔNG phải thủ phạm load 14** — load 14 là **system-wide** (process KHÁC: system_server / iFlytek / OEM…).
- **Hệ quả**: bind wedge là do **quá tải toàn hệ**, không do code app mình. Force-rebind của mình đúng nhưng không thắng nổi hệ đói CPU. [CHƯA BIẾT] process nào ngốn CPU trên xe — cần đo `/proc/*/stat` diff toàn hệ lúc load cao (xe offline → chặn).

## 3. Hướng xử lý (đã cập nhật sau khi đọc code)

1. **Ngay (tạm)**: reboot đầu xe → AMS + load reset → bind lại. Tái phát nếu CPU lại bão hoà.
2. **Gốc = CPU (load 14)** — cần đo per-thread trên xe (process/thread nào spin). `top` trả rỗng ⇒ đã dựng sampler `/proc/*/stat` diff 2s (client adb thô Python). **BLOCKED**: xe rớt mạng (100% packet loss) → chạy lại khi xe online.
3. **Force-rebind KHÔNG cần sửa** — `NavConnect.forceRebindIfNeeded` + `AccessibilityRebind` đã toggle đúng component `com.byd.launcher/...`. Chỉ **xét nới `GRANT_TIMEOUT_MS` 9s** (dưới load nặng dadb chậm, timeout cắt toggle giữa chừng) — nhưng chỉ là giảm nhẹ, không phải gốc; **chờ đo CPU rồi mới quyết** (đừng vá triệu chứng).
4. **Phụ (môi trường)**: `adb` daemon Mac bị macOS chặn LAN — dùng client thô Python để vào xe cho phiên sau.

**[CHƯA BIẾT]**: thread nào spin gây load 14 (cần đo) · reboot có giữ bind lâu không · macOS Local Network cấp quyền cho adb thế nào.

## 4. [ĐO LIVE trên xe 2026-09-18 13:18–13:24, adb wireless <car-ip>:5555] — TÌM RA THỦ PHẠM CPU + KHÔI PHỤC PHÍM

Vào xe bằng **client ADB thô pure-python** (`/tmp/adb_raw.py` — CNXN + AUTH ký `~/.android/adbkey` PKCS#8 + shell; không cần adb binary/cryptography). **adb wireless SỐNG** suốt phiên ⇒ **BUG2 KHÔNG tái hiện lúc này** (chập chờn, không chết vĩnh viễn).

**Thủ phạm CPU (8 lõi, `load 10.71/14.45/17.02` → 10.02/11.29/14.78) — `top -b -n 2`:**

| PID | Tiến trình | %CPU | Ghi chú |
|---|---|---|---|
| 5537 | com.google.android.apps.maps | **53–61** | **Chạy trong 1 Ô Kachi** (freeform stack #10, VD `kachi-slot-0` display 5) — nav/render |
| 3929 | com.byd.cdr | 38–39 | BYD driving recorder — KHÔNG phải Kachi |
| 138 | surfaceflinger | 34 | Compositing **3 display**: chính(0) + cụm(2 `fission_bg_xdja`) + ô-VD(5) |
| 10489 | vn.vietmap.live | 16–18 | Kachi autostart (bóng/badge) |
| 410/387 | camera / media.codec | 14–16 | |
| 1700 | com.byd.vrassistant.xf (iFlytek) | 13–14 | trợ lý zin — KHÔNG phải Kachi |
| 14083 | **com.byd.launcher (Kachi)** | **2** | **KHÔNG phải thủ phạm** |

⇒ **Kachi process 2%**, nhưng **TÍNH NĂNG Kachi induce phần lớn tải**: chiếu GMaps vào ô (→ GMaps 61% + surfaceflinger 34% compositing thêm 2 display) + autostart VietMap (18%). Cộng BYD cdr 39% + iFlytek 13% (của BYD) ⇒ 8 lõi bão hoà ⇒ lag. GMaps 61% là con lớn nhất — là nav-in-slot owner tự chọn; không rẻ hơn được.

**Phím gán không ăn — GỐC + KHÔI PHỤC LIVE:** `dumpsys accessibility`: `NavAccessibilityService` **ENABLED nhưng KHÔNG Bound** (Bound chỉ StatusBar; Binding rỗng) ⇒ `onKeyEvent` không bắt ⇒ phím chết. iFlytek cũng không Bound. Service **rớt bind** dưới áp lực CPU/RAM. **Toggle a11y TRỰC TIẾP** (`settings put secure enabled_accessibility_services` bỏ Kachi → thêm lại) ⇒ **BOUND NGAY** (label "ClusterNav — booster đọc", `capabilities=9` = window-content + **FILTER_KEY_EVENTS 8**), giữ bound sau đó ⇒ **phím chạy lại**. Cơ chế rebind ĐÚNG; Kachi tự-rebind thua vì **`GRANT_TIMEOUT_MS=9s`** đi qua **dadb** (dưới load 14 dadb chậm > 9s → cắt giữa toggle), còn toggle settings trực tiếp thì nhanh.

**FIX (1.78):** nới `NavConnect.GRANT_TIMEOUT_MS` **9s → 20s** ⇒ rebind hoàn tất dưới tải. [SUY] "Sửa ngay" / OFF→ON sẽ ăn trên xe kể cả load 14. **Còn nợ**: service vẫn rớt bind lại nếu áp lực kéo dài (gốc là CPU); giảm tải (bớt chạy đồng thời GMaps-in-slot + VietMap) là cách bền hơn — owner quyết vì đó là cách dùng.

**Tồn**: `/tmp/adb_raw.py` là client tạm (chưa lưu repo) — cân nhắc `scripts/vehicle/kachi/adb_raw.py` cho phiên sau.
