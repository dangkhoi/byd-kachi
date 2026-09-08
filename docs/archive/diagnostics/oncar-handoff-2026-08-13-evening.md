# On-car handoff + kịch bản test — buổi tới (soạn 2026-08-13 tối)

> Chạy tuần tự trên laptop (adb tới xe). **Đặt `VEH=<vehicle-ip>`** (đừng ghi IP thật vào file tracked).
> Mục tiêu buổi tới: (1) chốt Gemini-làm-trợ-lý + cách gọi overlay, (2) recon "nói ngay", (3) test nút 1.17,
> (4) validate nav 1.15/1.16. Cuối mỗi phần có **BÁO LẠI** = dữ liệu gửi về để wire bước sau.

```bash
export PATH="$HOME/Library/Android/sdk/platform-tools:$PATH"
VEH=<vehicle-ip>
adb connect "$VEH"; adb devices
GSA="com.google.android.googlequicksearchbox/com.google.android.voiceinteraction.GsaVoiceInteractionService"
REC="com.google.android.googlequicksearchbox/com.google.android.voicesearch.serviceapi.GoogleRecognitionService"
NAV(){ adb -s "$VEH" shell "CLASSPATH=/data/local/tmp/navopen.jar app_process /system/bin com.byd.navopen.NavOpen $*"; }
```

## Bối cảnh / trạng thái đã có
- Đã ship: **1.15** (HUD keep-alive + interp log), **1.16** (quantizeDisplay floor→round), **1.17** (voice-key "Google/Gemini" mở thẳng app Gemini). Xe nên đã OTA lên **1.17** (kiểm ở §0).
- Tối nay đã: **bật accessibility service** của ClusterNav (cần cho screenRead + voice-key); **set trợ lý = GsaVoiceInteractionService** (⚠️ ROM **reset sau reboot** → coi như đã mất, set lại ở §1).
- Nút mic: **giữ = keycode 328** (đã học; ngắn = mã khác `BTN_THUMB2`) — `onKeyEvent` nhận được.
- Overlay Gemini "đẹp" = **phiên assist NATIVE của hệ thống** — chỉ hệ thống gọi (assist gesture/phím/hotword); **app KHÔNG tự bật overlay** (chỉ mở được *app* Gemini ra foreground).
- Component Gemini = app "Google" `googlequicksearchbox/GsaVoiceInteractionService` (app "Gemini"/bard KHÔNG có VoiceInteractionService → không set làm trợ lý được; backend Gemini "robin" nằm trong app Google).

---

## §0 — Kết nối + version
```bash
adb -s "$VEH" shell "dumpsys package com.byd.clusternav | grep -E 'versionName|versionCode'" | head -2
```
- [ ] ClusterNav = **1.17 (117)**? Nếu còn 1.16/1.15 → chờ OTA hoặc `adb install -r apk/ClusterNav-1.17-release.apk`.

## §1 — Đặt Gemini làm trợ lý + verify nó LÀ Gemini
```bash
# trạng thái hiện tại (có thể trống nếu đã reboot)
adb -s "$VEH" shell "settings get secure voice_interaction_service; settings get secure assistant"
# set
adb -s "$VEH" shell "settings put secure voice_interaction_service '$GSA'"
adb -s "$VEH" shell "settings put secure assistant '$GSA'"
adb -s "$VEH" shell "settings put secure voice_recognition_service '$REC'"
adb -s "$VEH" shell "settings get secure voice_interaction_service"   # verify = $GSA
```
→ **REBOOT máy đầu xe bằng nút nguồn vật lý** (assist framework bind lúc boot — bước then chốt).
- [ ] Sau reboot: `settings get secure voice_interaction_service` còn = `$GSA` không? (nếu trống lại ⇒ ROM wipe ⇒ cần toggle re-apply-on-boot của ClusterNav — mình build sau).

**BÁO LẠI §1:** setting sau reboot còn giữ hay bị xoá.

## §2 — Tìm CÁCH GỌI ra overlay Gemini (sau §1 + reboot)
Thử lần lượt, sau mỗi lần nhìn màn xem **overlay "Hỏi Gemini"** có hiện không:
```bash
adb -s "$VEH" shell "input keyevent 219"    # KEYCODE_ASSIST
adb -s "$VEH" shell "input keyevent 231"    # VOICE_ASSIST
```
- [ ] `input keyevent 219` → overlay Gemini? (tối nay TRƯỚC reboot nó về launcher — thử LẠI sau reboot)
- [ ] **Long-press nút Home** trên màn → overlay Gemini?
- [ ] **Vuốt chéo từ góc dưới màn** (assist gesture Android 10) → overlay?
- [ ] **Nhấn-GIỮ nút mic vô-lăng** → overlay Gemini hay vẫn ra Bluetooth/小迪?

**BÁO LẠI §2:** cách nào (nếu có) làm hiện overlay Gemini. Đây là "đường đẹp" như hội làm. Nếu overlay hiện → xác nhận nhãn là **"Hỏi Gemini"** (Gemini) chứ không phải Assistant cũ.

## §3 — "Nói ngay" recon (chỉ chạy nếu muốn mic tự bật)
```bash
# (a) hands-free có mở Gemini ĐANG-NGHE không (hay Voice Search cũ)?
adb -s "$VEH" shell "am start -a android.speech.action.VOICE_SEARCH_HANDS_FREE -p com.google.android.googlequicksearchbox"
# (b) mở Gemini/overlay rồi DUMP node nút mic:
adb -s "$VEH" shell uiautomator dump /sdcard/gemini_ui.xml && adb -s "$VEH" pull /sdcard/gemini_ui.xml /tmp/
grep -oE '(content-desc|resource-id)="[^"]*"' /tmp/gemini_ui.xml | grep -iE "mic|voice|speak|nói|talk|listen|record"
```
**BÁO LẠI §3:** (a) hands-free mở ra cái gì (Gemini-nghe / Voice Search / không gì); (b) `content-desc`/`resource-id` của nút mic. → mình wire auto-tap hoặc đảo launcher.

## §4 — Nút ClusterNav → Gemini (1.17, độc lập với §1–§3)
Trong app ClusterNav → mục **"nút vật lý → trợ lý"**:
1. Bật tính năng (nó tự bật accessibility qua dadb).
2. Nút = **"Học phím…"** → **nhấn-GIỮ mic ~2s** → học **328** (Toast "Đã gán nút: 328").
3. Cử chỉ = **"Nhấn" (PRESS)** ⚠️ KHÔNG phải "Nhấn giữ".
4. Target = **"Google / Gemini"**.
- [ ] Giữ mic → **mở app Gemini** (foreground robin)? Nhấn-thả → trợ lý xe 小迪 còn nguyên?

*(Lưu ý: đây mở APP Gemini, KHÁC overlay native ở §2. Nếu §2 ra overlay được thì ưu tiên §2.)*

**BÁO LẠI §4:** giữ mic có mở Gemini không; ngắn có còn ra 小迪 không; có mở nhầm Bluetooth nữa không.

## §5 — Validate nav (1.15 + 1.16) — làm khi lái
- **1.15 HUD keep-alive:** chạy **đoạn thẳng dài không rẽ** → HUD/giữa cụm **hết chớp/mất ~1s** chưa?
- **1.16 quantize round + screenRead:** lái có GMaps dẫn 1 lúc, rồi:
```bash
adb -s "$VEH" pull /sdcard/Android/data/com.byd.clusternav/files/   # lấy nav_log_*.csv mới nhất
python3 scripts/analyze-nav-distance-log.py nav_log_*.csv
```
- [ ] Cột **`screenRead_m`** đã có số (accessibility đang chạy)? `display − screen` bias đã **nhỏ lại** (round có ăn)? đọc `projected − screen` để chỉnh FACTOR.

**BÁO LẠI §5:** HUD hết chớp chưa; gửi `nav_log` mới (có screenRead) để mình chốt FACTOR.

---

## Sau buổi test — mình sẽ build tiếp (tùy kết quả)
- Nếu §1 setting bị wipe sau reboot → build **toggle "Đặt Gemini làm trợ lý" + re-apply on BOOT** trong ClusterNav (dùng `SimpleCastRuntime.executeShell` qua dadb — hạ tầng đã có). Thay hẳn app kangrio.
- Nếu §2 có cách gọi overlay → doc hoá; nếu chỉ nút-map được → nghiên cứu remap keylayout nút-giữ = KEYCODE_ASSIST.
- Theo §3 → wire "nói ngay" (auto-tap mic hoặc hands-free).
- Theo §5 → chỉnh FACTOR nội suy cho khớp Google.

## Phụ lục
**Revert trợ lý (nếu cần):**
```bash
adb -s "$VEH" shell "settings delete secure voice_interaction_service; settings delete secure assistant"
adb -s "$VEH" shell "settings put secure voice_recognition_service 'com.arlosoft.macrodroid/.voiceservice.RecognitionServiceTrampoline'"
```
**Revert accessibility (nếu cần):** gỡ `com.byd.clusternav/.modules.navaccess.NavAccessibilityService` khỏi `enabled_accessibility_services`.

**nav feature-ids đã xác nhận ghi rc=0 (navopen `setraw instr <hex> <val>`):**
SEND_NAVI_STATUS `43e0003a` (2=on,4=off) · GUIDE_INFO_SIMPLE `43f01010` · FRONT_CROSSING `43f01018` ·
NEXT_PATHNAME `43fa1008` · NAVI_MILEAGE `43f02028` · HOUR `43f02010` · MINUTE `43f02018` ·
REMAINING_SEC `43f0201e` · SET_NAVI_SCREEN (setting) `4c10e015` (=3 bật).

**Chi tiết nền:** `docs/diagnostics/gemini-assistant-voicekey-oncar-2026-08-13.md` · `docs/diagnostics/oncar-sdk-findings-2026-08-13.md` · specs `docs/specs/hud-keepalive-interp-log-1.15.html`.
