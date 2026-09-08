# Changelog — 1.17 → 1.18

> **Trạng thái:** đã **bump `versionCode 118 / 1.18`** (app/build.gradle.kts) + cập nhật mục changelog trong README. **CHƯA commit, CHƯA publish OTA** (chưa copy vào `apk/` trên `main`).
> APK build lại: `versionCode=118 versionName=1.18`, quét `aapt2` = **không có** surface test (TEST_ADAS/TEST_SPEED_LIMIT/vehicleTest).
> Đây là bản thử iterate trên xe. Cài để test bằng:
> `adb connect <vehicle-ip>` → `adb -s <vehicle-ip>:5555 install -r app/build/outputs/apk/release/app-release.apk`
> Build: JDK17 + `./gradlew :app:assembleRelease`. Đã chạy `:core:test` + `:app:testDebugUnitTest` = **xanh** (373 app test).
> Ngày: 2026-08-14. Chưa test trên xe (owner off-car lúc build).

---

## Tính năng mới

### 1. Nút vật lý → mở **Kiki** (nút mic vô-lăng)
- Thêm **Kiki (`ai.zalo.kiki.car`)** làm đích của tính năng "Nút vật lý → Trợ lý giọng nói".
- **Đặt Kiki làm mặc định** (đích ordinal 0) + **keycode mặc định 328** (nút mic vô-lăng NHẤN-GIỮ trên xe này — đo on-car 2026-08-13; nhấn ngắn phát mã khác nên trợ lý gốc 小迪 giữ nguyên).
- UI: spinner "Nút" có sẵn preset **"Nút mic vô-lăng — NHẤN GIỮ (328)"**; spinner "Mở trợ lý" thêm mục **"Kiki (Zalo)"** (thứ tự: Kiki · BYD 小迪 · Nhận dạng giọng nói · Google/Gemini).
- Cử chỉ mặc định **Nhấn (PRESS)** — bắt buộc, vì 328 là xung tức thời (giữ mới phát), "Nhấn giữ" sẽ không khớp.
- `AssistantLauncher` mở Kiki qua `getLaunchIntentForPackage` (đã xác nhận resolve trên xe → `ai.zalo.kiki.auto.ui.CarMainActivity`), fallback VOICE_COMMAND/ASSIST setPackage(Kiki).
- Service Hỗ trợ (Accessibility) tự bật qua dadb khi bật công tắc (không cần vào Cài đặt).

### 2. Cast watchdog — tự kéo app cast bị rớt về lại cụm *(bản thử)*
- **Vấn đề:** split-cast (GMaps trái + VietMap phải). Bảo Kiki dẫn bằng **GMaps** → GMaps mở màn dẫn trên **display 0 (màn chính)** → task rời khỏi cụm → nửa trái đen. VietMap không sao (Kiki dẫn trong task đang chạy ở cụm).
- **Fix:** `SimpleCastCoordinator.repinEscapedCastApps()` — khi đang cast (split/full), nếu app đáng lẽ ở cụm mà **không còn trên display 1** → tự cast lại vào đúng slot (dùng recipe `AppMover.castToCluster` đã proven). Gọi từ vòng lặp 2s của `FloatingBubbleService`.
- **An toàn:** chỉ tác động slot đã đen sẵn (không xê dịch app đang đúng chỗ); **debounce** (thiếu 2 lần liên tiếp) + **cooldown 10s** + throttle 4s; bỏ qua CarPlay/AndroidAuto và chính ClusterNav. Có log `repin:` để trace.

## Sửa lỗi (đã có sẵn trong working tree — nền tảng "fix B", giữ lại)

### 3. Tự cấp quyền Accessibility khi bật Nav+HUD
- Sau reboot, `enabled_accessibility_services` mất service ClusterNav → 2 chuyến screenRead RỖNG (không tinh chỉnh nội suy được). Nay bật Nav+HUD (và mở app khi Nav+HUD đã bật) sẽ **tự cấp lại qua dadb khi thiếu** (giống selfGrant notification), chỉ gọi dadb khi thật sự thiếu.

### 4. Marquee OFF: rút gọn tên đường
- Khi tắt marquee, tên đường dài giờ **rút gọn bằng `NavFormat.fitRoadName`** (vd "Trần Trọng Kim" → "T.T.Kim") thay vì để firmware cụm cắt cứng.

## Test / contract
- `AmapFrameBuilderContractTest`: thêm test khoá hành vi marquee OFF = `fitRoadName`.
- `NavCastUiWiringContractTest`: thêm test khoá việc Nav+HUD tự cấp Accessibility (gated `if (!accessibilityBoosterGranted())`).
- `NotificationAccessFlowContractTest`: cập nhật assertion "startup không đụng adb khi master switch off" sang regex khớp block guarded mới (fix B đổi one-liner thành block đa dòng).

---

## Files thay đổi (so với 1.17 / HEAD)

**Mã (tính năng mới — Kiki + cast watchdog):**
- `core/…/voicekey/VoiceKeyMatcher.kt` — enum `VoiceKeyTarget` +`KIKI`
- `app/…/modules/voicekey/AssistantLauncher.kt` — `KIKI_PKG` + nhánh mở Kiki
- `app/…/modules/navaccess/NavAccessibilityService.kt` — map ordinal 0 → KIKI
- `app/…/Prefs.kt` — keycode mặc định 231→328, comment đích 0=Kiki
- `app/…/MainActivity.kt` — spinner đích [Kiki,BYD,Recognizer,Gemini] + preset 328 *(và giữ code self-grant của fix B)*
- `core/…/clustercast/simplified/SimpleCastCoordinator.kt` — watchdog `repinEscapedCastApps()`/`doRepinEscapedCastApps()`
- `app/…/modules/clustercast/FloatingBubbleService.kt` — gọi watchdog trong vòng 2s

**Mã (fix B — có sẵn trong working tree):**
- `app/…/ClusterBroadcaster.kt` — marquee OFF dùng `fitRoadName`
- `app/…/MainActivity.kt` — self-grant Accessibility

**Test:**
- `app/…/AmapFrameBuilderContractTest.kt`, `app/…/NavCastUiWiringContractTest.kt`, `app/…/NotificationAccessFlowContractTest.kt`

---

## Cần test trên xe (checklist)

```
adb connect <vehicle-ip>
adb -s <vehicle-ip>:5555 install -r app/build/outputs/apk/release/app-release.apk
adb -s <vehicle-ip>:5555 logcat -c && adb -s <vehicle-ip>:5555 logcat | grep -iE "SimpleCast|repin|AppMover|VoiceKey|voice-key"
```
1. **Nút → Kiki:** mở ClusterNav → bật "Nút vật lý → Trợ lý" → nhấn-giữ nút mic → Kiki mở? *(log `voice-key fire → target=KIKI`)*
2. **Cast fix:** split GMaps trái + VietMap phải → bảo Kiki "dẫn bằng Google Maps" → nửa trái có tự hiện lại GMaps **đang dẫn** không? *(log `repin: …maps escaped cluster → re-cast`)*

## Câu hỏi mở (cần dữ liệu trên xe)
- **Cast fix:** GMaps được cast lại có **giữ màn dẫn đường** không, hay về màn chính của app? Nếu về màn chính → vòng sau đổi cơ chế re-pin sang `am stack move-task` (giữ nguyên task đang dẫn) thay vì relaunch LAUNCHER.
- **Nút → Kiki:** Kiki chỉ mở app (chưa chắc tự nghe) — như Gemini trước đây; xác nhận Kiki có tự vào trạng thái nghe không.

## Việc còn lại
- [x] Bump **1.18** (versionCode 118) + cập nhật mục changelog trong README + quét `aapt2` sạch.
- [ ] Cài + test 2 tính năng trên xe.
- [ ] Nếu đạt: commit (qua security scan) + publish OTA (copy APK vào `apk/` trên `main`).
