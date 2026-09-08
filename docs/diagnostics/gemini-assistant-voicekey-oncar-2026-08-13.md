# On-car procedure — Gemini as assistant + mic-button → Gemini (prep 2026-08-13)

> **Trạng thái**: Current · **Cập nhật**: 2026-08-13 · **Mục đích**: Thủ tục on-car bật Gemini làm trợ lý + map nút mic → Gemini (2 hướng độc lập).

> Off-car prep so the next on-car session is just execution. Vehicle IP redacted → set `VEH=<vehicle-ip>`.
> Two independent paths; Path A is the "enable Gemini as the car's assistant" the community got working.

```bash
VEH=<vehicle-ip>
adb connect "$VEH"
GSA="com.google.android.googlequicksearchbox/com.google.android.voiceinteraction.GsaVoiceInteractionService"
REC="com.google.android.googlequicksearchbox/com.google.android.voicesearch.serviceapi.GoogleRecognitionService"
```

## What tonight's probing established
- The Gemini overlay in the community photo = the **Google app VoiceInteractionService session** (`GsaVoiceInteractionService`, "robin" car voice surface). It has a text box "Hỏi Gemini" + a mic.
- On this car **no assistant was set** (`settings get secure voice_interaction_service` = empty) → that's why ACTION_ASSIST hit a chooser, "OK Google" had nothing to trigger, and Gemini said "voice not supported". **Setting Google/Gemini as the assistant is the enable step** (== Settings → Apps → Default apps → Digital assistant app → Google/Gemini, which BYD hides — so do it via adb).
- Mic/voice button surfaces on input device `simulate-keys` (event7) as **gamepad pulse codes**: short-tap = `BTN_THUMB2`; **long-press = `BTN_BASE2`/`BTN_TL2` → Android keycode `328`** (learned via ClusterNav's learn-key → so `onKeyEvent` DOES receive it). Short and long are **different codes** → map long→Gemini, leave short = car assistant.
- Launching `com.google.android.apps.bard` **directly** opens Gemini's robin car voice surface (works). The generic `ACTION_ASSIST` intent opened a chooser → Bluetooth (the bug the owner hit). **1.16→1.17 fix:** ClusterNav's "Google / Gemini" voice-key target now launches Gemini directly.
- "Voice not supported on this device" in Gemini is almost certainly the **always-on "Hey Google" hotword** (needs device certification the head unit lacks), NOT the tap-mic in the overlay — tap-mic likely works.

---

## Path A — enable Gemini as the system assistant (device config, no app needed)
```bash
# was empty; set Google/Gemini as assistant + recognizer
adb -s "$VEH" shell "settings put secure voice_interaction_service '$GSA'"
adb -s "$VEH" shell "settings put secure assistant '$GSA'"
adb -s "$VEH" shell "settings put secure voice_recognition_service '$REC'"
# verify
adb -s "$VEH" shell "settings get secure voice_interaction_service; settings get secure assistant"
```
Then **reboot the head unit** (physical power button) so the assist framework re-binds.

**Test after reboot:**
1. Open Gemini once, sign in / accept terms if prompted.
2. Invoke the assistant: long-press Home, or the assist gesture, or the mapped mic button (Path B). → does the **Gemini overlay** ("Hỏi Gemini") appear?
3. Tap the **mic** in the overlay and speak → does Gemini answer? (This is the realistic "voice" path.)
4. Try saying **"OK Google"** (hotword) — may not work on this unit; don't rely on it.

**State now:** set tonight but not yet verified (adb went offline right after). Re-run the verify line on next connect.

**Revert (if wanted):**
```bash
adb -s "$VEH" shell "settings delete secure voice_interaction_service; settings delete secure assistant"
adb -s "$VEH" shell "settings put secure voice_recognition_service 'com.arlosoft.macrodroid/.voiceservice.RecognitionServiceTrampoline'"  # original
```

---

## Path B — mic button (long-press) → Gemini, via ClusterNav (needs 1.17)
1.17 makes the "Google / Gemini" target launch Gemini directly (fixes the chooser→Bluetooth). Configure in the app:
1. ClusterNav → **"nút vật lý → trợ lý"** → bật (it auto-enables the accessibility service over dadb).
2. Button = **"Học phím…"** → **NHẤN-GIỮ mic ~2s** → it learns keycode **328** (the long-press code). *(Toast "Đã gán nút: 328".)*
3. Gesture = **"Nhấn" (PRESS)** — ⚠️ NOT "Nhấn giữ". The firmware already emits a distinct code (328) for the hold, delivered as an instant pulse, so "Nhấn giữ" (which measures hold-duration) would **never** match.
4. Target = **"Google / Gemini"**.

**Result:** long-press mic → 328 → ClusterNav launches Gemini (robin voice surface). Short-press mic → `BTN_THUMB2` (different code, not matched) → car assistant 小迪 unchanged. If Path A assistant is set, the overlay is Gemini; tap mic to talk.

**If the long-press ALSO opens something native (e.g. Bluetooth) alongside Gemini:** the firmware's long-press may have its own action. Then either (a) accept both, or (b) learn `BTN_TL2`'s keycode instead (the release code) and test which is cleaner.

---

## Reality check
- **Achievable:** Gemini opens (button or overlay); type or tap-mic to talk.
- **Likely NOT achievable on this head unit:** always-listening **"OK Google" hotword** (Google device-certification limitation — same root as Gemini's "voice not supported" banner). So the button/overlay is the trigger, not hotword.
- If Path A's assistant + reboot makes the native voice button or a gesture open Gemini directly, Path B (the ClusterNav button) becomes optional.

---

## Path C — "nói ngay" (mở Gemini + tự nghe) — prep

**Đã thêm vào launcher (ship kèm bản tới):** target "Google/Gemini" giờ thử `ACTION_VOICE_SEARCH_HANDS_FREE`
(mở + nghe ngay, hands-free) TRƯỚC, rồi mới fallback mở app Gemini. On-car: bấm nút đã map → Gemini có
**mở ở trạng thái đang-nghe** (nói được luôn) không? Nếu ra Gemini thường (chưa nghe) hoặc "Voice Search" cũ
→ ghi lại để đảo thứ tự candidate.

**Fallback bạn mô tả — accessibility TỰ BẤM nút mic (cần định danh node, dump trên xe):**
Mở Gemini / màn voice của nó lên, rồi dump cây view để tìm node mic:
```bash
adb -s "$VEH" shell uiautomator dump /sdcard/gemini_ui.xml
adb -s "$VEH" pull /sdcard/gemini_ui.xml /tmp/
grep -oE '(content-desc|resource-id)="[^"]*"' /tmp/gemini_ui.xml | grep -iE "mic|voice|speak|nói|talk|listen|record"
```
→ gửi mình `content-desc`/`resource-id` khớp. Rồi mình wire accessibility service: sau khi mở Gemini, tìm
đúng node đó và `performAction(ACTION_CLICK)` một lần → mic bật. (KHÔNG build mù được — định danh phải lấy
từ bản Gemini thật trên xe.)

**"Không cần foreground": không khả thi sạch** — không thể bấm mic của app không hiển thị. Chỉ overlay trợ lý
(đang chập chờn trên ROM này) hoặc tự chạy SpeechRecognizer nền (tự dựng pipeline, Gemini chưa chắc nhận).
Thực tế tốt nhất = "mở + tự nghe" (Path C) hoặc "mở + auto-tap mic" (fallback trên).

---

## Session results — 2026-08-13 evening (on-car, 1.17, parked at home)

Ran live over adb (IP redacted → `<vehicle-ip>`), car parked. 1.17 confirmed on car (`versionCode=117 versionName=1.17`).

### Reboot persistence (answers the §1 open question)
- **Reboot WIPES the assistant**: after the owner's reboot, `voice_interaction_service` = empty, `assistant` = empty, `voice_recognition_service` reverted to `com.arlosoft.macrodroid/.voiceservice.RecognitionServiceTrampoline`. → the ROM clears it on every boot.
- **Accessibility booster SURVIVES**: `enabled_accessibility_services` still contains `com.byd.clusternav/…NavAccessibilityService`, `accessibility_enabled=1`. Voice-key onKeyEvent + screenRead ready without re-enable.
- **Notif listener SURVIVES**: `NavNotificationListener` still enabled.

### The real missing piece: RoleManager ASSISTANT role
- `dumpsys role` showed `android.app.role.ASSISTANT` with **NO holders** (empty) even though `assistant`/`voice_interaction_service` were set. On Android 10 the assist session path needs the **role**, not just the secure settings.
- Granted it: `cmd role add-role-holder --user 0 android.app.role.ASSISTANT com.google.android.googlequicksearchbox` → `holders=com.google.android.googlequicksearchbox`. (Role persists across reboot, unlike the secure settings.)
- Set live (no reboot needed): `voice_interaction_service` + `assistant` = `…/GsaVoiceInteractionService`, `voice_recognition_service` = `…/GoogleRecognitionService`. Readback confirms it sticks. `dumpsys voiceinteraction` → `mComponent=…GsaVoiceInteractionService`, `Session service=…NgaVoiceInteractionSessionService` (Nga = Gemini), `Supports assist=true`.

### §4 — ClusterNav mic → Gemini: WORKS
- Exited CarPlay (required), configured in-app (learn key **328**, gesture **Nhấn/PRESS**, target **Google/Gemini**). Long-press mic → **opens Gemini app** (`com.google.android.apps.bard/.shellapp.BardEntryPointActivity`). Short-press → BYD 小迪 preserved (ClusterNav consumes only the long-press). Confirmed by owner.
- Limitation: opens the app but **does NOT auto-listen** (mic not active). And in **CarPlay the mic = Siri** (Apple owns it) — ClusterNav's Gemini button only works in native Android mode.

### Overlay ("Hỏi Gemini" assist session) — NOT reachable via ADB on this unit
Evidence (all tried, all blocked):
- Injected `input keyevent 219`/`231` → nothing (policy ignores injected assist keys).
- Real key via `sendevent /dev/input/event7` (simulate-keys) scancode **582** (`Generic.kl: key 582 VOICE_ASSIST`) → **SELinux Permission denied** (device is `crw-rw---- root input`, shell is in group `input(1004)` but MAC blocks the `shell` context).
- **No root** (`su` not found) → cannot edit `/system/usr/keylayout`, cannot create uhid.
- Android **10** (SDK 29) has **no** `cmd voiceinteraction show-session` (added in 11+); `cmd voiceinteraction` → "No shell command implementation".
- `am start -a android.intent.action.ASSIST` → opens `…GoogleAppImplicitActionAssistGatewayInternal` (an **activity**), not the floating session; mic not activated.
- `VOICE_SEARCH_HANDS_FREE` cold → bounces to launcher, mic not activated. (Showed `…voice.robin.main.MainActivity` only when Gemini already foreground.)
- Input devices: `event7 = simulate-keys` (steering voice button surface), `event6 = gpio-keys`.

**Conclusion:** Gemini-as-assistant is fully set (settings + role). The floating overlay is blocked purely by the **trigger** on this Android-10 head unit; via pure ADB/dadb (uid shell, no root, SELinux-confined) there is **no path** to summon it. The community likely uses **root/Magisk** (remap a hardware button → `KEYCODE_ASSIST`, or a system-perm module), a native button that emits ASSIST, or different firmware — not merely "set assistant".

### Options (pending owner choice)
- **A (root):** root/Magisk → remap steering button → `KEYCODE_ASSIST` → real overlay like the group.
- **B (no root):** ClusterNav opens the **robin** voice surface + auto-taps the mic node (needs one UI dump + build) → "talk to Gemini" without the floating panel.
- **C:** defer; proceed to §5 nav validation.

### Reference — exact commands used (redacted)
```bash
GSA="com.google.android.googlequicksearchbox/com.google.android.voiceinteraction.GsaVoiceInteractionService"
REC="com.google.android.googlequicksearchbox/com.google.android.voicesearch.serviceapi.GoogleRecognitionService"
settings put secure voice_interaction_service "$GSA"; settings put secure assistant "$GSA"; settings put secure voice_recognition_service "$REC"
cmd role add-role-holder --user 0 android.app.role.ASSISTANT com.google.android.googlequicksearchbox
# revert: cmd role remove-role-holder --user 0 android.app.role.ASSISTANT com.google.android.googlequicksearchbox
#         settings delete secure voice_interaction_service; settings delete secure assistant
#         settings put secure voice_recognition_service "com.arlosoft.macrodroid/.voiceservice.RecognitionServiceTrampoline"
```

---

## FINAL VERDICT — 2026-08-13: Gemini feature FAILED (abandoned)

Owner decision after the on-car session: **drop the Gemini/voice-key feature.**

- **Overlay ("Hỏi Gemini" assist session): impossible via ADB on this head unit.** Gemini-as-assistant is fully set (settings + RoleManager ASSISTANT role granted), framework confirms Nga/Gemini bound — but no trigger can summon the floating session: injected keyevent 219/231 ignored; real `sendevent` to `simulate-keys` (event7, scancode 582 VOICE_ASSIST) blocked by **SELinux**; **no root** (no keylayout remap / uhid); Android 10 has **no** `cmd voiceinteraction show-session`; `ACTION_ASSIST` opens the Google app activity, not the session. A root/Magisk path (remap a hardware button → KEYCODE_ASSIST) is the only realistic route and is out of scope.
- **Button → open Gemini app (§4) worked** but only opens the app (no auto-listen), only in native Android mode (in CarPlay the mic = Siri), and hands-free intent does not activate the mic here. Not worth keeping.

**Action:** the physical-button → voice-assistant **UI is being removed from the app** (this cycle). Assistant/role changes made during the session are reversible (see revert commands above) and get wiped on reboot anyway.
