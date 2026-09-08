# ON-CAR HANDOFF — Nút vật lý → mở app (voice-key 1.19): giữ mic vẫn ra Bluetooth · 2026-08-14

> Xe: BYD Seal DiLink 3.0 · Android 10 (API 29) · KHÔNG root · **parked-only**. Dọn = **power-cycle nút nguồn vật lý**.
> **HỎI LẠI IP** mỗi phiên: `export VEH=<vehicle-ip>:5555` (thường `<vehicle-ip>`). ĐỪNG đoán.
> Nguyên tắc: **KHÔNG assume** — mỗi claim trace về log/readback. Bản trên xe/OTA: **1.19 (versionCode 119)** đã push `main`.
> Spec: `docs/specs/voicekey-rework-1.19.html`. Bug 1.18: `docs/diagnostics/gemini-assistant-voicekey-oncar-2026-08-13.md`.

---

## ✅ KẾT QUẢ (2026-08-14 ~16:30, XE OWNER, IP <vehicle-ip>) — RESOLVED

**Trên xe owner: giữ nút mic → mở đúng Kiki, chạy ngon.** Không ra Bluetooth/Gemini. Không cần test thêm case này.

Phân định A/B/C → **nhánh (A)**: chỉ cần bật tính năng + bind accessibility trên xe owner; **app BẮT được phím mic** (nhánh **(C) bị loại**). Triệu chứng Bluetooth/Gemini ở 1.18 → là **xe team** (đời/cấu hình khác), đúng như cảnh báo đầu doc.

Bằng chứng (không cần owner bấm gì):
- `versionName=1.19`, `versionCode=119` — `[on-car readback: dumpsys package com.byd.clusternav]`
- Service bound: `com.byd.clusternav/…NavAccessibilityService` có trong `enabled_accessibility_services` — `[on-car readback: settings get secure enabled_accessibility_services]`
- Service đang chạy với `capabilities=9` = `CAN_RETRIEVE_WINDOW_CONTENT(1)` + **`CAN_REQUEST_FILTER_KEY_EVENTS(8)`** ⇒ được phép nuốt/nhận key-event — `[on-car readback: dumpsys accessibility]`
- Giữ mic → Kiki: **`[owner on-car observation]`** (không đo máy được vì release build không đọc được prefs; quan sát trực quan là ground-truth cho outcome này).

**Hành động:** KHÔNG cần code. Feature 1.19 hoạt động đúng trên xe owner. Doc dưới đây giữ làm tham chiếu cho lần gặp lại triệu chứng trên xe/đời khác.

---

## 0. TL;DR
- **Triệu chứng:** giữ nút mic → **1.18 mở Gemini**, **1.19 mở màn Bluetooth**. Cả hai = **hệ thống tự xử lý** phím assist/mic-hold (ACTION_ASSIST trên unit này rơi vào Bluetooth/chooser) — **app KHÔNG bắt/nuốt được phím**.
- **1.19 đã đổi bản chất:** matcher bỏ cử chỉ/mốc-500ms → nếu keycode tới service thì **nuốt** (onKeyEvent trả true) → hệ thống KHÔNG thấy phím → **không thể ra Bluetooth**. Bluetooth vẫn ra ⇒ **phím KHÔNG tới `onKeyEvent`**.
- **Đã loại trừ off-car:** cấu hình accessibility ĐÚNG (`flagRequestFilterKeyEvents` + `canRequestFilterKeyEvents=true` + BIND perm + meta-data; `packageNames` không lọc key-event). Không có bug cấu hình.
- **Mục tiêu phiên:** phân định 3 khả năng còn lại → **(A)** chưa cấu hình/chưa cấp quyền · **(B)** mã thực ≠ 328 · **(C)** phím mic-hold bị PhoneWindowManager **nuốt trước accessibility** (app bó tay nút này) → rồi chọn hướng.

> ⚠️ Lần test vừa rồi là **xe của team** — có thể khác đời/chưa cấu hình. Mã 328 đo trên **xe owner** (13/8). Phiên này test **xe owner**.

---

## 1. CHUẨN BỊ
```bash
export VEH=<vehicle-ip>:5555
ADB="$HOME/Library/Android/sdk/platform-tools/adb"
"$ADB" connect "$VEH"; "$ADB" -s "$VEH" shell dumpsys package com.byd.clusternav | grep -E 'versionName|versionCode'   # kỳ vọng 1.19/119
```
- Mở **ClusterNav** trên màn (bắt buộc để "Học phím mới" hiện được ô đặt tên — luồng service→Activity).

---

## 2. THÍ NGHIỆM QUYẾT ĐỊNH (phân định A/B/C trong 1 lượt)

### S1 — Tiền đề: tính năng bật + service bound?
- Trong app: gạt **"Nút vật lý → Trợ lý"** = ON (mặc định TẮT). Gạt ON tự cấp Accessibility qua dadb.
- Verify service bound:
```bash
"$ADB" -s "$VEH" shell settings get secure enabled_accessibility_services | tr ',' '\n' | grep -i clusternav || echo "CHƯA bound → đây là lý do (khả năng A)"
"$ADB" -s "$VEH" shell dumpsys accessibility | grep -i "clusternav" | head
```
- Nếu **chưa bound** → onKeyEvent không chạy → **(A)**. Bật xong làm tiếp S2.

### S2 — "Học phím mới": MIC vs NÚT KHÁC (phân định B vs C)  ← mấu chốt
Mở logcat trước:
```bash
"$ADB" -s "$VEH" shell logcat -c; "$ADB" -s "$VEH" shell logcat -s NavAccess VoiceKeyLauncher &
```
1. Bấm **"Học phím mới…"** → **giữ nút mic** (như lúc dùng).
   - **Hiện ô đặt tên + mã** (logcat `learned voice keycode=<n>`) → app BẮT được mic. Mã có phải **328**? Nếu ≠ → **(B)** (đổi keycode). Nếu =328 → app bắt được nhưng lúc dùng vẫn Bluetooth ⇒ nghi race, xem S3.
   - **KHÔNG hiện gì** (Bluetooth/Gemini bật lên, logcat im) → mic-hold **không tới accessibility**.
2. Bấm **"Học phím mới…"** lần nữa → bấm **1 nút vô-lăng KHÁC** (vd Vol+, Next, nút tuỳ chỉnh).
   - **Nút khác HIỆN mã** nhưng **mic KHÔNG** → service bound + nhận phím OK, **riêng phím mic bị nuốt trước** → **(C) xác nhận** (app không giành được nút mic).
   - **Nút khác cũng KHÔNG hiện** → service không nhận phím nào (xem lại S1 / cờ filter).

### S3 — (nếu mic học được mã 328) Test đích thật
- Đặt tên nút → chọn **đích = Kiki (Zalo)** trong list app → giữ mic:
  - Ra **Kiki** (không Gemini/Bluetooth) → **FIX! báo lại để chốt.**
  - Vẫn Bluetooth/Gemini dù logcat có `voice-key fire → target=…KIKI` → hệ thống chạy assist **song song/không nuốt được** → coi như (C).

---

## 3. KẾT LUẬN → HƯỚNG (chọn theo S2)
- **(A) chưa cấu hình** → chỉ cần bật + học phím + chọn app. Xong, không cần code.
- **(B) mã ≠ 328** → "Học phím mới" đã nhận mã đúng → chọn app → xong (không cần code; keycode lưu tự động).
- **(C) phím mic bị nuốt trước accessibility** → **hướng app-bắt-phím CHẾT cho nút mic**. Pivot (KHÔNG patch tiếp app bắt phím):
  1. **Set trợ lý mặc định hệ thống = Kiki/app muốn** (Cài đặt Android → Ứng dụng trợ lý). Vì mic-hold kích `ACTION_ASSIST` hệ thống → set default đúng thì hệ thống tự mở nó thay Bluetooth. Test ngay trên xe:
     ```bash
     "$ADB" -s "$VEH" shell settings get secure assistant
     "$ADB" -s "$VEH" shell settings get secure voice_interaction_service
     # (xem có Kiki/Google trong danh sách assist không; đổi bằng UI Cài đặt, rồi giữ mic test)
     ```
  2. **Dùng nút KHÁC** (nút vô-lăng học được ở S2) map sang app — bỏ nút mic.
  3. RE key-binding (xem §4) để hiểu/redirect mic-hold.

---

## 4. PULL cho RE off-car (nếu vào nhánh C — làm ở xe, mình RE sau)
Lấy về để phân tích mic-hold bind vào đâu ở tầng framework/policy:
```bash
"$ADB" -s "$VEH" shell 'ls /system/usr/keylayout/ /vendor/usr/keylayout/ 2>/dev/null'
"$ADB" -s "$VEH" pull /system/usr/keylayout ./keylayout-sys 2>/dev/null
"$ADB" -s "$VEH" pull /vendor/usr/keylayout ./keylayout-vendor 2>/dev/null
"$ADB" -s "$VEH" shell dumpsys input > ./dumpsys-input.txt      # key mappings + input devices
"$ADB" -s "$VEH" shell "getevent -lp" > ./getevent-devices.txt  # devices + hỗ trợ key nào
# giữ mic + xem getevent bắt sự kiện gì (device nào, mã gì):
"$ADB" -s "$VEH" shell getevent -lt   # (giữ mic vài lần rồi Ctrl-C) → mã scancode/keycode thật
```
→ Gửi mấy file này về, mình xác định mic-hold là scancode→keycode nào + ai nuốt.

---

## 5. BÁO LẠI (để chốt) — ĐÃ CHỐT 2026-08-14
- [x] S1: service ClusterNav **bound** (readback `enabled_accessibility_services`), `capabilities=9` gồm FILTER_KEY_EVENTS(8); công tắc hiệu lực ON (feature chạy).
- [ ] S2: **không thực hiện** — owner dùng trực tiếp, ra Kiki luôn nên khỏi "Học phím mới"; phân định B/C thành moot (đã là nhánh A, app bắt được mic).
- [x] S3: giữ mic → **ra Kiki** (không Bluetooth/Gemini). `[owner on-car observation]`
- [ ] (C) set default assistant: **không cần** — chạy đúng mà không phải đổi trợ lý mặc định.

---

## 6. DỌN DẸP
- Power-cycle nút nguồn vật lý trước khi rời.

## 7. THAM CHIẾU
- Rework 1.19: `docs/specs/voicekey-rework-1.19.html` · commit `80fc467`.
- Code: `core/…/voicekey/VoiceKeyMatcher.kt` (fire-on-keycode+consume) · `app/…/modules/navaccess/NavAccessibilityService.kt` (onKeyEvent + learn→VoiceKeyLearnBus) · `app/…/modules/voicekey/AssistantLauncher.kt` (launch by package) · `MainActivity.kt` (Học phím mới + list app) · `res/xml/nav_accessibility_config.xml` (đã verify đúng cờ).
- Bug 1.18 Gemini/Bluetooth (ACTION_ASSIST chooser): `docs/diagnostics/gemini-assistant-voicekey-oncar-2026-08-13.md`.

---

## 8. FOLLOW-UP 2026-08-14 PM — reboot làm mic→Bluetooth lại · ROOT + FIX (đã VERIFY)

Sau **power-cycle (reboot)** buổi tối: mic-hold → **Bluetooth** lại (Kiki chết), DÙ `enabled_accessibility_services` vẫn liệt kê ClusterNav.

**Root (readback trên xe):** NavAccessibilityService **ENABLED nhưng KHÔNG BOUND**.
- `dumpsys accessibility`: ClusterNav ở **"Enabled services"** nhưng KHÔNG ở **"Bound services"** (chỉ StatusBar bound); `accessibility_enabled`=1; logcat `NavAccess` rỗng (service không chạy `onServiceConnected`).
- ⇒ `onKeyEvent` không chạy → phím mic rơi về ACTION_ASSIST hệ thống = Bluetooth.
- Nguyên nhân: app self-grant chỉ **GHI setting** (→ enabled) chứ **KHÔNG ép hệ thống BIND**. Sau reboot, hệ thống để "enabled" mà không bind.

**Fix LIVE (đã verify — owner xác nhận 18:58):** toggle service ra/vào ép rebind:
```bash
ACC="com.byd.clusternav/com.byd.clusternav.modules.navaccess.NavAccessibilityService"
OTHERS="com.byd.vrassistant.xf/com.iflytek.autofly.access.service.AccessibilityServices:com.android.systemui/com.android.systemui.custom.StatusBarAccessibilityService"
settings put secure enabled_accessibility_services "$OTHERS"          # remove clusternav
settings put secure enabled_accessibility_services "$OTHERS:$ACC"     # re-add
settings put secure accessibility_enabled 1
```
→ ClusterNav vào **"Bound services"**, `capabilities=9` (window-content + **FILTER_KEY_EVENTS**) → **mic→Kiki chạy lại**.

**Fix CODE (cần làm — chưa implement):** lúc boot / bật Nav+HUD, app phải verify service **BOUND thật** (đọc `dumpsys accessibility` "Bound services", không chỉ đọc `enabled_accessibility_services`); nếu enabled-but-not-bound → **toggle remove/re-add** ép bind. Ảnh hưởng CẢ voice-key (onKeyEvent) LẪN screen-read booster (ground-truth tuning) → đây là bug thật sau mỗi reboot.
