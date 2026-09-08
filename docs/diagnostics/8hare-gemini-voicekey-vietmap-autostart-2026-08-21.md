# 8hare RE → voice-key Gemini (keyevent 231) + VietMap autostart headless

> **Trạng thái**: Current · **Ngày**: 2026-08-21 · **Loại**: Diagnostics (RE + finding có bằng chứng + off-car verify)
> **Mục đích 1 dòng**: Cơ chế 8hare mở Gemini dạng assistant (keyevent 231 + recipe), áp vào ClusterNav voice-key; và fix VietMap autostart (pidof + không đè, headless boot).

## 1. RE app tham chiếu `8hare` (`com.byd.user.eighthare` v1.5.0)
Launcher/dashboard + điều khiển-từ-xa cho đầu xe BYD (mqtt + webrtc + accessibility + nhiều quyền BYDAUTO_*). **Cơ chế mở Gemini dạng ASSISTANT (không phải mở app home)** = 2 phần:

**(a) Runtime** — `EightHareAccessibilityService.onKeyEvent` bắt phím voice (FLAG_REQUEST_FILTER_KEY_EVENTS) → chạy **`input keyevent 231`** (KEYCODE_VOICE_ASSIST) qua ADB-loopback → hệ thống route tới trợ lý.

**(b) Setup (một lần)** — `DevToolsActivity` chạy 7 lệnh qua ADB-loopback (thứ tự QUAN TRỌNG):
```
settings put secure assistant  com.google.android.googlequicksearchbox/com.google.android.voiceinteraction.GsaVoiceInteractionService
settings put secure voice_interaction_service ''         # clear
(sleep 300ms)
settings put secure voice_interaction_service  <GsaVoiceInteractionService>
settings put secure voice_recognition_service  com.google.android.googlequicksearchbox/com.google.android.voicesearch.serviceapi.GoogleRecognitionService
settings put global byd_float_app_list  googlequicksearchbox,com.google.android.apps.bard,<self>   # cho overlay hiện trên launcher BYD
appops set com.google.android.googlequicksearchbox SYSTEM_ALERT_WINDOW allow
appops set com.google.android.apps.bard          SYSTEM_ALERT_WINDOW allow
```
Precondition: 8hare **bắt buộc CẢ googlequicksearchbox LẪN bard cài** mới bật (thiếu → báo app nào).

**Shell runner** (`c.b.a.a.n1.q.b()`): thư viện `com.tananaev.adblib` → `Socket("127.0.0.1", 5555)` → auth keypair tự sinh → `shell:<cmd>` ⇒ chạy với **uid shell (2000)** = có `INJECT_EVENTS` + `WRITE_SECURE_SETTINGS`. Giống kỹ thuật dadb loopback của ClusterNav, khác thư viện (tananaev vs mobile-dev-inc/dadb).

## 2. Áp vào ClusterNav — voice-key Gemini (keyevent 231)
Trước đây (1.17-1.19) voice-key → Gemini bằng `startActivity(bard)` hoặc `Intent(ACTION_ASSIST)` → chỉ **mở app home**, không phải surface voice; ACTION_ASSIST còn nhầm trợ lý (bug 1.18). Nay theo 8hare:
- `AssistantLauncher.launch(spec)`: nếu `isGeminiVoiceSpec(spec)` (= sentinel `__VOICEKEY231__` HOẶC chọn thẳng app bard/googlequicksearchbox) → **`launchViaVoiceAssistKey`** = emit `input keyevent 231` qua `LocalDeviceShell` (dadb). **CHẠY NỀN** (Thread) — KHÔNG block `onKeyEvent` (tránh ANR). **Debounce 1500ms** chống self-loop nếu capture keycode==231.
- `setSystemAssistant(ctx)`: replicate ĐẦY ĐỦ 7 lệnh 8hare (order + sleep 300 + byd_float_app_list APPEND + appops). Precondition CẢ googlequicksearchbox + bard cài, thiếu → trả chuỗi lỗi hiển thị Toast. Gọi khi user chọn target Gemini (`MainActivity.onItemSelected`), chạy nền + Toast kết quả.
- File: `app/.../modules/voicekey/AssistantLauncher.kt`, `Prefs.VK_TARGET_GEMINI_KEY`, `MainActivity` targetSpecs + onItemSelected.

**On-car cần**: Gemini (bard) + Google app (googlequicksearchbox) cài + migrate; adb-tcp 5555 mở + key allow. Nếu thiếu → Toast báo (bản cũ im lặng nên không lộ lý do fail).

## 3. Fix VietMap autostart (headless-friendly)
Bug on-car: mở app LUÔN relaunch VietMap + VietMap ĐÈ app. Gốc: guard cũ `isAppForeground` dùng `runningAppProcesses` — **Android 10+ chỉ thấy process của chính mình → luôn false → luôn relaunch**; `startActivity(VietMap)` → đè.

Fix (`VietMapAutostart.kt`, dùng chung 2 case):
- **pidof qua dadb** (uid shell = tin cậy cross-app) → CHỈ start khi CHƯA chạy.
- Sau start: **app-open** (`MainActivity`, `ensureRunning` async) → đưa ClusterNav lại trước; **boot headless** (`BootSetupService`, `runNow` đồng bộ) → về **HOME** (không đè launcher; app mình vốn headless trên boot per tickbox "Tự khởi động nền").
- Gate `badgeEnabled` + VietMap-installed bên trong; degrade-safe.

## 4. Bằng chứng verify OFF-CAR (emulator `clusternav`, đã kiểm bằng lệnh — không đoán)
- Emulator CÓ (ground truth `pm list`): `vn.vietmap.live` (chạy), `com.chisadin.wazemod`, `com.google.android.apps.maps`, `com.google.android.googlequicksearchbox`. **KHÔNG có** `com.google.android.apps.bard`.
- **VietMap app-open** (fresh onCreate): VietMap đang chạy → log "đã chạy → bỏ auto-start" (skip); kill VietMap → "chưa chạy → start + trả foreground (com.byd.clusternav2)" → VietMap pid mới + top=ClusterNav. ✅ cả 2 nhánh.
- **VietMap boot headless** (`am start-foreground-service BootSetupService`): VietMap tắt → "chưa chạy → start + trả foreground (HOME)" → VietMap pid 29430 + top=NexusLauncher HOME (KHÔNG đè), ClusterNav headless; VietMap chạy → "đã chạy → bỏ auto-start", pid không đổi. ✅ cả 2 nhánh.
- **Gemini**: bard KHÔNG có trên emulator → precondition đúng khi sẽ báo "Thiếu app bắt buộc: Gemini" (verify path thiếu-app). Full flow Gemini-voice **CHƯA test được off-car** (thiếu bard) → cần xe hoặc cài bard.
- **Test**: `:app:testDebugUnitTest` = 417 pass 0 fail (sau refactor). **Senior review** (sub-agent): PASS (threading/recipe/loop/boundary) + patch dead const + tôi thêm debounce [P2].

## 5. Chưa xong / cần on-car
- Gemini-voice full flow (keyevent 231 → Gemini assistant surface) — cần bard cài + migrate + adb 5555 trên xe.
- Chưa commit (chờ owner test on-car).

## References
- RE: `~/Library/Caches/clusternav-re/decoded/…/com/byd/user/eighthare/{DevToolsActivity,service/EightHareAccessibilityService}.java` + `c/b/a/a/n1/q.java` (shell runner).
- App: `AssistantLauncher.kt`, `VietMapAutostart.kt`, `BootSetupService.kt`, `MainActivity.kt`, `Prefs.kt`.
