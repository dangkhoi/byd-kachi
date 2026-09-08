# ON-CAR HANDOFF — verify 1.13 (notif self-grant · default-OFF · voice-key) · 2026-08-13 (pm)

> **Supersedes** `oncar-handoff-2026-08-13.md` (1.12 HAL nav-screen). Các mục 1.12 **CHƯA verify** được mang xuống §2C.
> Xe: BYD Seal DiLink 3.0, Android 10 (API 29), **không root**. Chủ: Đăng Khôi (dangkhoi).
> **1 CHỖ DUY NHẤT** cho lần lên xe tới. Tất cả **parked-only** (số P, phanh tay). Dọn = **power-cycle nút nguồn vật lý** (không tính `adb reboot`).
> **1.13 đã build off-car XANH nhưng CHƯA lên `main`/OTA** — trên `main` vẫn là 1.12 (commit `3884d55`). Muốn test 1.13 phải **cài tay APK** (§1) hoặc push 1.13 lên main trước (chưa uỷ quyền merge).

---

## 0. TRẠNG THÁI (sau phiên off-car 2026-08-13 pm)

| Hạng mục | Trạng thái |
|---|---|
| **1.13** (versionCode 113) | **Build off-car XANH**: `:core:test` + `:app:testDebugUnitTest` + `assembleRelease` PASS; `aapt2` vc113, no test surface; senior review + security scan CLEAN. **CHƯA lên main/OTA.** |
| **T1 — Tự cấp quyền notification** | Nút/công tắc khi thiếu quyền → `NavConnect.selfGrant` bắn `cmd notification allow_listener` qua **dadb uid-shell** (bỏ màn Settings chết → hết toast "IVI không hỗ trợ"). Màn Settings chỉ còn fallback. **CHƯA verify on-car.** |
| **T1 — Nav+HUD mặc định TẮT** | `Prefs.enabled` default **false** → mở app KHÔNG đụng adb; bật công tắc mới grant+connect. **CHƯA verify on-car.** |
| **T3 — Nút vật lý → Trợ lý** | Cấu hình nút + cử chỉ (nhấn/nhấn-giữ) + trợ lý; `onKeyEvent` chỉ nuốt đúng tổ hợp (không thay chức năng gốc); "Học phím" để gán; tự bật Accessibility qua dadb. Default OFF. **CHƯA verify on-car.** |
| **1.12 — value↔menu cụm / app-uid HAL render / mũi tên** | Vẫn **CHƯA verify** (xem §2C — mang từ handoff 1.12). |

---

## 1. CHUẨN BỊ (nối máy + cài 1.13)
```bash
export VEH=<vehicle-ip>:5555        # HỎI lại IP (hotspot đổi), ĐỪNG đoán
adb connect $VEH && adb devices     # thấy DiLink3.0, KHÔNG nhầm emulator
# Cài 1.13 build off-car (cùng key ký, -r giữ data):
adb -s $VEH install -r app/build/outputs/apk/release/app-release.apk
adb -s $VEH shell dumpsys package com.byd.clusternav | grep versionName   # kỳ vọng 1.13
```
- **Cụm = `fission_screencap -d 0`** (đúng khi thấy đồng hồ). IVI = `-d 1`.
- Log hữu ích: `-s NavConnect NavListener NavAccess VoiceKeyLauncher NavigationHudOwner`.
- **Cài đè lần đầu**: bản mới → công tắc Nav+HUD nên ở **TẮT** (default false). Nếu data cũ còn `enabled=true` thì nó vẫn nhớ — để test default-OFF sạch: gỡ hẳn rồi cài lại, hoặc `adb -s $VEH shell pm clear com.byd.clusternav` (mất hết pref).

---

## 2. VIỆC PHẢI LÀM ON-CAR (1.13) — theo thứ tự

### 2A — T1: Tự cấp quyền notification + default-OFF (chính)
1. **Default-OFF**: sau khi cài sạch, mở app → công tắc **Navigation + HUD = TẮT**. Xác nhận mở app KHÔNG chạy adb:
   ```bash
   adb -s $VEH shell "logcat -c"; # mở app; đợi 5s
   adb -s $VEH shell "logcat -d -s NavConnect" | tail   # KỲ VỌNG: rỗng (không selfGrant/ensureConnected lúc mở)
   ```
2. **Gạt Nav+HUD → BẬT.** Lần đầu xe hiện **"Allow USB debugging?"** → bấm **Allow**. Kỳ vọng:
   - Toast *"Đang cấp quyền đọc thông báo…"* → *"Đã cấp quyền — đã kết nối nguồn dẫn đường."*
   - **KHÔNG** còn toast hệ thống *"Hệ thống IVI không hỗ trợ hoạt động này."*
   - Nav card rời trạng thái "Cần cấp quyền".
   ```bash
   adb -s $VEH shell "logcat -d -s NavConnect NavListener" | tail
   # kỳ vọng: "selfGrant xong sau ..ms: bound=true" + "listener connected -> authoritative coordinator ready"
   adb -s $VEH shell "settings get secure enabled_notification_listeners"   # chứa com.byd.clusternav/...NavNotificationListener
   ```
3. **Nút "Cấp quyền / kết nối lại"** (khi đang thiếu quyền, nếu muốn test riêng): bấm → cũng selfGrant như trên.
4. **Persist qua reboot**: **power-cycle nút nguồn** → mở lại app → bật Nav+HUD → **KHÔNG** cần Allow lại, listener bind ngay (quyền đã lưu ở secure settings).
5. **Fallback**: nếu selfGrant lỗi (không bấm Allow) → hiện dialog "Chưa cấp được quyền" với **Thử lại** / **Mở cài đặt**. Xác nhận dialog hiện đúng.

### 2B — T3: Nút vật lý → Trợ lý giọng nói
1. Gạt **"Nút vật lý → Trợ lý giọng nói" = BẬT** → app tự bật Accessibility qua dadb (có thể popup Allow). Toast *"Đã bật…"*.
   ```bash
   adb -s $VEH shell "settings get secure enabled_accessibility_services"   # chứa .../NavAccessibilityService
   adb -s $VEH shell "settings get secure accessibility_enabled"            # = 1
   ```
2. **Gán nút**: chọn **"Học phím…"** trong ô *Nút* → **bấm nút vật lý** muốn dùng (vd nút Voice trên vô-lăng). Kỳ vọng toast *"Đã gán nút: <TÊN> (<code>)"*.
   ```bash
   adb -s $VEH shell "logcat -d -s NavAccess" | tail   # "learned voice keycode=<n> (<KEYCODE_...>)"
   ```
3. Chọn **Cử chỉ** (Nhấn / Nhấn giữ) + **Mở trợ lý** (Google/Gemini · BYD 小迪 · Nhận dạng giọng nói).
4. **Bấm nút** đúng cử chỉ → trợ lý mở.
   ```bash
   adb -s $VEH shell "logcat -d -s NavAccess VoiceKeyLauncher" | tail
   # "voice-key fire → target=.. key=.." + "launched assistant target=.. via .."
   ```
5. **KHÔNG mất chức năng gốc**: nếu chọn **Nhấn giữ**, **nhấn ngắn** nút đó phải vẫn chạy chức năng gốc (vd nút chuyển bài vẫn chuyển bài khi nhấn ngắn). Nếu chọn **Nhấn** thì nút đó bị app chiếm cho trợ lý (đúng ý — user tự chọn nút).
6. **Nếu không ăn** ("lên xe test không OK thì mới mò"):
   - Thử keycode ứng viên khác trong danh sách, hoặc đổi cử chỉ.
   - Dò code thật của nút: `adb -s $VEH shell getevent -lt` → bấm nút → xem dòng `EV_KEY  KEY_xxx` / giá trị; hoặc `adb -s $VEH shell "logcat -d -s NavAccess"` khi đang "Học phím".
   - Nếu `onKeyEvent` KHÔNG nhận phím nào (hệ thống nuốt trước accessibility) → ghi lại để tính đường khác (media-button receiver / CAN) ở phiên off-car sau.

### 2C — (mang từ 1.12) Cụm "Giữa + ETA" qua HAL — CHƯA verify
Chỉ chạy nếu còn thời gian; đây là nợ từ handoff 1.12 (1.13 KHÔNG đụng đường này):
- **Cast = TẮT**, mở GMaps dẫn đường → mở app → spinner **"Chế độ hiển thị trên cụm"**: chọn lần lượt **Đơn giản → Toàn màn hình → Màn hình nhỏ → OFF**, mỗi lần chụp cụm `fission_screencap -d 0` + xem `logcat -s NavigationHudOwner` (rc mỗi feature; nếu thiếu token `NAVI_SCREEN=` ⇒ SETTING device getInstance fail → app-uid ghi HAL bị chặn).
- **Chốt** value nào = "Đơn giản (Giữa+ETA)" → báo lại để set default.
- Mũi tên rẽ đúng hướng không (AMAP vs CAN).
- Chi tiết đầy đủ: `oncar-handoff-2026-08-13.md` §3.

---

## 3. DỌN DẸP (bắt buộc trước khi rời/lái)
- **Power-cycle nút nguồn vật lý** — dọn HAL/opcode + trạng thái compositor.
- Nếu bật voice-key mà không muốn giữ → tắt công tắc (service Accessibility có thể tắt tay ở Cài đặt > Hỗ trợ nếu muốn).
- Nếu đã đổi chế độ cụm lạ → chọn lại chế độ mong muốn.

---

## 4. THAM CHIẾU
- **Spec 1.13:** `docs/specs/notif-grant-docs-voicekey-1.13.html` (requirements → design → tasks → verification → reviewer log Pass 1).
- **Code T1:** `NavConnect.kt` (`selfGrant`/`grantAccessibility`) · `MainActivity.kt` (nút + công tắc + `promptNotificationAccessFallback`) · `Prefs.kt` (`enabled` default false).
- **Code T3:** `core/.../voicekey/VoiceKeyMatcher.kt` · `modules/voicekey/AssistantLauncher.kt` · `modules/navaccess/NavAccessibilityService.kt` (`onKeyEvent`) · `res/xml/nav_accessibility_config.xml` · `res/layout*/activity_main.xml`.
- **Hướng dẫn user:** `docs/HUONG-DAN.md` (1.13, VI+EN).
- **APK off-car:** `app/build/outputs/apk/release/app-release.apk` (vc113). Chưa copy vào `apk/`, chưa lên main.
- **Nợ 1.12:** `oncar-handoff-2026-08-13.md`.
