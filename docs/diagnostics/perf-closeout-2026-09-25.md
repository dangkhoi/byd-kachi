# Đóng dự án — Profiling & tối ưu toàn bộ tiến trình nền (2.65 → 2.66) · 2026-09-25

> **Trạng thái**: Current · **Cập nhật**: 2026-09-25 · **Mục đích**: số đo TRƯỚC/SAU cùng một cách đo (`scripts/emulator/perf-snapshot.sh`) cho mọi tiến trình Kachi, phát hiện + cách vá, phần chỉ đo được trên xe (🚗). Spec: `docs/specs/kachi-closeout-hardening.html`. Kiểm kê tiến trình nền: `perf-inventory-2026-09-25.md`.

## 0. Cách đo (tái lập)

```bash
# Máy ảo clusternav10 (API 29, 2 lõi, không HAL/AutoContainer), bản vehicleTest (chữ ký release, debuggable)
~/Library/Android/sdk/emulator/emulator -avd clusternav10 -no-window -no-audio -gpu swiftshader_indirect &
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:assembleVehicleTest
adb install -r -t app/build/outputs/apk/vehicleTest/app-vehicleTest.apk
adb shell am start -n com.byd.launcher/com.byd.clusternav.launcher.KachiHomeActivity
sleep 60; scripts/emulator/perf-snapshot.sh <label> 300     # CPU từ /proc/<pid>/stat, PSS dumpsys, RSS /proc/status, luồng, gfx, wakelock, log
```

Baseline test trước khi chạm code [ĐO 2026-09-25 22:5x, `--rerun-tasks --continue`, đếm XML]: app 1266 · core 2508 · car-integration 61 · vehicle-contracts 22 · offcar-planner 99 = **3956 test, 0 fail, 0 error**.

## 1. TRƯỚC (2.65, HEAD 93dc1b4)

[ĐO 2026-09-25 23:07, `perf-snapshot.sh before-idle-allon 300`, màn chính đứng yên, prefs máy ảo: voice wake BẬT · bong bóng BẬT · automation có 1 rule · PM2.5 lọc BẬT · dock 10 nút · slot: YouTube Music, widget năng lượng, widget PM2.5]

| Tiến trình | CPU % (1 lõi, 300 s) | PSS | RSS | Luồng | Native heap |
|---|---|---|---|---|---|
| `com.byd.launcher` | **1,99** | 178,5 MB | 253,6 MB | 51–54 | 146 MB (alloc 149,5 / free 10,5) |
| `com.byd.launcher:wake` | 0,16 | 266,3 MB | 333,2 MB | 18 | 245 MB (alloc 124 / **free 126,5 — không trả OS**) |

- `KachiPerf` (3 cửa sổ 60 s): **HAL đọc = 420–429/phút** · bỏ-không-hiện 426 · bỏ-xe-không-có 108–120 · shell 4,0/phút · log 0 KB/phút.
- gfxinfo 300 s: 67 khung, 49 janky (73 %) — mẫu nhỏ, chỉ nói "gần như không vẽ lại lúc đứng yên".
- Wakelock của app: 0 · alarm đăng ký: 5 · job: 0. Logcat 300 s: 350 dòng tổng, 202 dòng của app.
- Luồng tiến trình chính (comm): 15 `Binder:*` · 3 `KachiVoicePreload` · 2 `KachiHalSignal` · 2 `kachi-camera-hal` · 2 `Okio Watchdog` · 2 `DefaultDispatcher` · `pm25-filter-poll` · `hud-keepalive` · `kachi-automation` · `kachi-slot-probe` · `kachi-window-shell` · `kachi-inputd-life` · `widget-hash` · `diagstoragecap` · `pool-1/2-thread-1` · `AsyncTask #1` · `OkHttp Connection` + luồng ART/hwui.
- `top -H` 20 s: chỉ `Jit thread pool` 0,3 % · `RenderThread` 0,2 % · `HeapTaskDaemon` 0,1 % — không luồng nào quay vòng.
- Log lặp lúc đứng yên (tag/phút): `VmOverlayPos` gửi `VM_BUBBLE_POS` cùng toạ độ mỗi 16 s (3,75/phút) · `Pm25Filter poll mức=0` mỗi 45 s · `NavRebind REBIND_WATCHDOG` + `NavConnect grantAccessibility` mỗi ~32 s (2 watchdog chồng: AlarmManager 60 s + Handler 30 s) · `KachiAutoGps fix tuổi` mỗi 60 s · 1 "Explicit concurrent copying GC" trong 5 phút.
- `:wake`: `avc: denied { read } name="loadavg"` **1 dòng/giây** (58/phút) — `VoiceWakeListener.readLoad1` đọc `/proc/loadavg` mỗi 1 000 ms, SELinux `untrusted_app` API 29 chặn ⇒ luôn 0,0 ⇒ `VoiceLoadGuard` mù + 1 ngoại lệ/giây + audit spam. [SUY] xe cũng API 29 untrusted_app ⇒ cùng kết quả (chốt: `logcat | grep loadavg` trên xe).

## 2. Phát hiện

| # | Phát hiện | Mức | Bằng chứng | Hướng vá |
|---|---|---|---|---|
| F1 | **HAL 420 lượt/phút lúc đứng yên**: `CarDataAdapter.readControls` đọc thẳng `table.readState` cho MỌI nút dock ở nhịp NHANH 1 Hz, **không qua `HalAbsentCache`** ⇒ nút off-car/chưa provision bị hỏi mãi. Trên xe ≈23 ms/lượt ⇒ ~16 % một lõi. | [ĐO] KachiPerf + [SUY] `CarDataAdapter.kt:80,232` | K1b: đi qua cache vắng, bấm nút ⇒ forget, `fastNeeded` ngủ khi mọi nút nguội (giữ yêu cầu owner #5 ≤1 s cho nút có giá trị) |
| F2 | `:wake` đọc `/proc/loadavg` 1 Hz bị SELinux chặn: guard tải mù + 58 dòng audit/phút + 1 exception/giây | [ĐO] logcat | dò 1 lần; bị chặn ⇒ đổi sang tín hiệu tự đo (`/proc/self/stat` delta CPU của chính tiến trình, đọc được) — generic |
| F3 | 3 luồng `KachiVoicePreload` sống lúc đứng yên, `pthread_cond_wait` trong native (sherpa) — `preload()` gọi nhiều lần, mỗi lần một Thread | [ĐO] tdumps | cờ idempotent (AtomicBoolean) + 1 luồng |
| F4 | 2 `HalSignalClient` (+2 executor `kachi-camera-hal`): `CameraSignalController` tạo ở cả `KachiHomeActivity:115` lẫn `AutomationService:198` ⇒ 2 vòng reconnect socket 19322 (backoff 1→8 s) mãi khi không có helper (máy ảo), 2 socket + sự kiện đôi trên xe | [ĐO] tdumps + [SUY] source | 1 controller/tiến trình (singleton qua `AppContainer`) |
| F5 | 2 watchdog a11y chồng nhau (`RebindReceiver` alarm WAKEUP 60 s + `VoiceKeyKeepAliveService` handler 30 s), mỗi lượt 1–2 lệnh shell dumpsys (shell 4/phút) | [ĐO] logcat/KachiPerf | giữ 1 đường chính in-process; alarm chỉ khi FGS không sống |
| F6 | `VmOverlayPosition.applyOnOpen` gửi broadcast cùng toạ độ tới VietMap mỗi 15 s kể cả khi VietMap không cài | [ĐO] logcat | gate: gói VietMap có cài (cache PackageManager) |
| F7 | `pm25-filter-poll` 45 s đọc HAL không có (off-car/xe không có datum) | [ĐO] logcat | qua cache vắng / giãn nhịp khi INVALID liên tiếp |
| F8 | Native heap 146 MB ở tiến trình chính (Dalvik chỉ 3 MB); `:wake` giữ 126 MB free không trả OS | [ĐO] meminfo | xem `ram-audit` (agent) |
| F9 | lintRelease **38 Error** (MissingClass 5 layout chết, NewApi 2, MissingPermission 3, WrongConstant 2, MissingTranslation 24, …) + DrawAllocation 10 + StaticFieldLeak 1 | [ĐO] lint XML | agent lint |

## 3. SAU (vá xong, cùng cách đo, 2026-09-25 23:55, bản vehicleTest build từ working tree)

[ĐO `perf-snapshot.sh after-idle-allon 300`, cùng prefs, cùng màn chính, sau `force-stop` + mở lại 90 s]

| Tiến trình | CPU % (1 lõi, 300 s) | PSS | RSS | Luồng | Native heap |
|---|---|---|---|---|---|
| `com.byd.launcher` | 1,99 → **1,47** | 178,5 → **71,2 MB** | 253,6 → 161,9 MB | 51 → **34** | 146 → **25 MB** |
| `com.byd.launcher:wake` | 0,16 → 0,17 | 266 → 270 MB | 333 → 353 MB | 18 → 17 | 245 → (không đổi — mô hình ASR 74 MB + 126 MB free chưa trả OS, xem `ram-audit` §4.2) |

- `KachiPerf`: **HAL đọc 420 → 0/phút** · shell 4,0 → **0/phút** · bỏ-xe-không-có 120 → 540 (nút dock nay đi qua cache vắng, đếm SKIP thay vì đọc) · bỏ-không-hiện 426 (không đổi, K1 nhịp chậm).
- Logcat 300 s: của app 202 → **34 dòng**. `avc: denied loadavg`: 58/phút → **0**.
- Luồng chính: hết `KachiHalSignal`×2, `kachi-camera-hal` chỉ khi camera bật; `KachiVoicePreload`×3 hết (chính không nạp mô hình khi wake ON — `VoicePreloadPolicy.shouldPreloadInMain`).
- Alarm đăng ký `com.byd.launcher`: 5 → 0 ngay sau khởi động lại (alarm rebind chỉ đặt khi keep-alive phím-thoại lên — xem ghi chú §3.1).
- gfxinfo 300 s: 47 khung (đứng yên) — mẫu nhỏ, không so sánh.

### 3.1 Ghi chú
- PSS chính giảm 107 MB phần lớn là **không còn bản mô hình ASR thứ hai** trong tiến trình chính khi "Hey Kachi" BẬT (ram-audit §1–2). Khi "Hey Kachi" TẮT, chính vẫn nạp sẵn như V3 R4 (15 s lần bấm mic đầu) — không đổi.
- `VmOverlayPos` vẫn gửi mỗi 16 s trong lần đo này: [ĐO] `pm list packages` ⇒ `vn.vietmap.live` CÓ cài trên máy ảo ⇒ cổng `InstalledPackageGate` cho qua là đúng (F6 chỉ chặn khi không cài); cast ON ⇒ `ResendGate` 15 s giữ nguyên hành vi hiện trường.
- ⚠ Lần đo SAU #1 thiếu 3 service so với TRƯỚC (`VoiceKeyKeepAlive`, `Automation`, a11y) vì `adb root` trước đó làm rớt `tcpip 5555` ⇒ kênh shell loopback không lên ⇒ keep-alive không được khởi. Đo lại SAU #2 sau khi dựng lại kênh (§3.3).

### 3.3 SAU #2 — đủ dịch vụ như TRƯỚC (2026-09-26 00:04, sau `adb tcpip 5555` + `reverse`, mở lại 120 s)

Dịch vụ sống [ĐO `dumpsys activity services`]: VoiceKeyKeepAlive · NavNotificationListener · VoiceWake · NavAccessibility · FloatingBubble (AutomationService không lên vì rule của hồ sơ khác — không đổi so với cơ chế `anyEnabled`). Alarm đăng ký: 4 (rebind alarm còn, nay no-op khi FGS sống — log `watchdog alarm no-op` mỗi 60 s).

| Tiến trình | CPU % (1 lõi, 300 s) | PSS | Luồng | Native heap |
|---|---|---|---|---|
| `com.byd.launcher` | **3,57** (xem ghi chú) | **60,5 MB** | 42 | 21,4 MB |
| `com.byd.launcher:wake` | 0,17 | 270 MB | 18 | 246 MB |

- `KachiPerf`: HAL đọc **0/phút** · shell 4,0/phút (bằng TRƯỚC — nguồn không phải a11y: log chỉ có alarm no-op; [ĐOÁN] `repinEscapedCastApps`/dò kênh shell của bong bóng cast ON) · log 0,7 KB/phút.
- `avc: denied loadavg` = **0** (TRƯỚC 58/phút). Luồng chính hết `KachiVoicePreload`×3, `kachi-camera-hal`×2; còn 1 `KachiHalSignal` (camera BẬT ⇒ đúng 1 — trước 2).
- ⚠ CPU 3,57 % > TRƯỚC 1,99 %: `top -H` 15 s ⇒ **`widget-hash` 2,4 %** (luồng hash ảnh cảnh báo VietMap, `VietMapWidgetExtraction`), main 0,5 %, RenderThread 0,3 %. [ĐO] VietMap (`vn.vietmap.live`, pid 4534, chạy từ trước cả lần đo TRƯỚC) có 3 widget bound (708/709/710) và đang đẩy RemoteViews ⇒ mỗi update `onHostViewUpdated` chụp pixel + hash + **một `Thread{}` mới chờ future** (`VietMapWidgetBridge.kt:262-290`, hành vi có sẵn, không phải thay đổi phiên này). Vì sao TRƯỚC không thấy luồng này tốn CPU: [CHƯA BIẾT] (nghi VietMap chỉ đẩy update sau khi host `startListening` lại). Đã chuyển cho senior review (Pass 1) vá generic: hash chỉ khi pixel đổi + không thread-per-update; đo lại ở lượt build cuối.

### 3.4 Hồi quy giọng nói off-car (harness `scripts/emulator/voice-e2e.sh`, máy ảo API 29)

- T1 (chữ → ý định → thi hành, 105 ca): bản vá **80/105 PASS**, HEAD 93dc1b4 sạch (worktree, cùng máy ảo, cùng thứ tự) **80/105** — **giống nhau từng ca** [ĐO `diff` báo cáo 2 lượt, 2026-09-26 00:1x–00:2x]. Lượt chạy đầu của bản vá ra 78/105: 2 ca lệch (t43 "bố cục chỉ có 1 ô", t52 "app không lên màn") là trạng thái máy ảo phụ thuộc thứ tự ca — lượt chạy lại trên cùng bản vá trở về 80/105 = HEAD.
- 25 ca FAIL có sẵn ở HEAD: tính năng đã bỏ (khoá xe/đèn viền/chiếu cụm), nhãn đổi ("Ghế sưởi" → "Sưởi ghế lái", "Kính trước-trái" → "Kính lái"), 2 ca CONFIRM. ⇒ bộ ca `voice-cases.tsv` lệch sản phẩm — mục backlog `VOICE-CASES-DRIFT` (không phải hồi quy của vòng này).
- **Sau CLOSE-2 (2026-09-26, bộ ca soát lại theo 2.67 — 106 ca, chỉ sửa TSV):** [ĐO máy ảo, 3 lượt liên tiếp] **96/102 → 100/106 → 101/106**; 5 FAIL còn lại là cố ý (BUG-CANDIDATE `VoiceFeatureGone.ALL` thiếu dòng cho khoá xe · mở khoá cửa · rời xe · âm lượng · độ sáng màn); 1 ca FLAKY t52 (đọc `mResumedActivity` 3 s cố định). Chi tiết ở backlog CLOSE-2.
- T2 (WAV → sherpa → ý định): xem §3.5.

### 3.5 T2 (WAV → sherpa-onnx → ý định) trên bản vá

[ĐO 2026-09-26, 24 WAV TTS macOS 16 kHz, `voice-e2e.sh --only wav`]: **23/27 nghe đúng nguyên văn, 0 ca FAIL** (4 ca nghe lệch chữ nhưng ý định đúng). Đường `VoiceRecognizer` (nay có `useLock` RW + preload idempotent) giải mã bình thường; không có baseline T2 của HEAD trong phiên này (T1 đã chứng minh giống HEAD từng ca).

### 3.6 SAU #3 — bản CUỐI 2.66 (167), sau senior review Pass 1 (2026-09-26 00:41)

[ĐO `perf-snapshot.sh after3-final-2.66 300`, vehicleTest build từ đúng working tree sẽ commit; dịch vụ sống: NavNotificationListener · VoiceWake · FloatingBubble (kênh shell loopback không lên ở lượt này ⇒ keep-alive/a11y không khởi — cấu hình đủ 4 FGS đã đo ở §3.3)]

| Tiến trình | CPU % (1 lõi, 300 s) | PSS | Luồng | Native heap |
|---|---|---|---|---|
| `com.byd.launcher` | 1,99 → **1,22** | 178,5 → **74,6 MB** | 51 → **35** | 146 → **24 MB** |
| `com.byd.launcher:wake` | 0,16 → 0,14 | 266 → 272 MB | 18 → 19 | 245 → 246 MB (không đổi — cần NDK `mallopt`, backlog) |

- `KachiPerf`: HAL đọc **0/phút** (420) · shell **0** · log 0,7 KB/phút. `top -H` 15 s: main 0,6 % · DefaultExecutor 0,1 % · RenderThread 0,1 % — **`widget-hash` không còn nổi** (Pass 1: hash chỉ khi pixel đổi, không thread-per-update). `avc loadavg` 0. Logcat của app 300 s: 202 → **34 dòng**.
- Full 5 module `--rerun-tasks` [ĐO]: **4079 test / 0 fail** (baseline 3956 ⇒ +123 test hồi quy mới). `lintRelease` 38 → **0 error** (554 warning, không đổi loại). APK release 43 342 022 B.

**Tóm tắt TRƯỚC → SAU (máy ảo, màn chính đứng yên, 300 s; số cuối §3.7)**: CPU launcher −59 % (1,99 → 0,82) · PSS launcher −71 % (178,5 → 51,4 MB, phần lớn = không còn bản mô hình ASR thứ hai khi "Hey Kachi" bật) · luồng −16 · HAL 420 → 0/phút · dòng log app −83 % · SELinux audit `:wake` 58/phút → 0. Trên xe kỳ vọng thêm phần K1b (7 lượt HAL × 23 ms/s ≈ 16 % một lõi) — 🚗 C1.

### 3.7 SAU #4 — bản CUỐI 2.66 (167) sau review Pass 2, ĐỦ dịch vụ như TRƯỚC (2026-09-26 01:22)

[ĐO `perf-snapshot.sh after4-final-2.66-pass2 300`; dịch vụ: VoiceKeyKeepAlive · NavNotificationListener · VoiceWake · NavAccessibility · FloatingBubble (đủ như §1); alarm 4 (rebind alarm no-op khi FGS sống)]

| Tiến trình | CPU % (1 lõi, 300 s) | PSS | RSS | Luồng | Native heap |
|---|---|---|---|---|---|
| `com.byd.launcher` | 1,99 → **0,82** | 178,5 → **51,4 MB** | 253,6 → 112,4 MB | 51 → **35** | 146 → **23,9 MB** |
| `com.byd.launcher:wake` | 0,16 → 0,13 | 266 → 258 MB | 333 → 309 MB | 18 | không đổi (CLOSE-4) |

- `top -H` 15 s: chỉ main 0,6 % — `widget-hash` không còn. `avc loadavg` = 0. Logcat app 35 dòng/300 s.
- Full 5 module `--rerun-tasks` [ĐO 2026-09-26 01:1x]: **4087 / 0 fail** (app 1332 · core 2570 · car-integration 64 · vehicle-contracts 22 · offcar-planner 99). `lintRelease` 0 error. APK release 43 342 022 B = `apk/Kachi-2.66-release.apk`.
- **Đây là bảng số CUỐI của vòng** (thay §3.6 — §3.6 thiếu 2 FGS nên CPU/PSS không so được với §1).

### 3.8 CLOSE-3 trên máy ảo (2.68, wake BẬT) — nút mic/EXTRA_START_VOICE đi `:wake`

[ĐO 2026-09-26 11:20, `am start … --ez start_voice true` + bridge `listen`]: `KachiVoiceEntry: lối vào → :wake đã ack (hạn 1500 ms) — không dựng recognizer ở tiến trình chính` sau **52 ms**; `WakeSvc: LISTEN_NOW — mở phiên nghe headless`; `KachiVoiceTiming(:wake): sẵn sàng nghe sau 921 ms` (lượt 2: 382 ms). Native heap tiến trình chính **25,6 → 22,4 MB** (trước 2.68: +74 MB + ~15 s lần đầu). E2E T1 sau CLOSE-2: **105/106** (t52 flaky đã ghi). 🚗 kiểm nút mic dock thật + hạn ack 1,5/4 s trên xe (`logcat -s KachiVoiceEntry`).

### 3.9 Đợt 2.68 (CLOSE-2/3/3b/5/7/8/10 + RES-CLEAN) — số gộp [ĐO 2026-09-26]

- Full 5 module `--rerun-tasks`: **4143 / 0 fail** (2.67: 4105). `lintRelease` 0 error; UnusedResources 138 → 97 (77 còn lại bị 2 tệp niêm phong T11 giữ). APK release 43 467 609 B.
- Voice E2E T1 sau CLOSE-2: **106/106 PASS** (bộ ca 105 → 106, 25 ca lệch sản phẩm đã sửa kỳ vọng, 5 dòng `VoiceFeatureGone` mới, t52 không lệch lượt này).
- CLOSE-3b ảnh chụp ngữ pháp: `files/voice/grammar-snapshot.tsv` có ngay sau mở màn chính (2 hồ sơ + 1 địa chỉ), **tự cập nhật `active` khi harness đổi hồ sơ** (mốc 1790399213125 → 1790399341450) ⇒ `:wake` đọc được hồ sơ/sổ địa chỉ mới nhất mà không qua SharedPreferences cache. 🚗 kiểm câu "đổi sang hồ sơ X" bằng nút mic khi wake ON.

## 4. 🚗 NEEDS-ONCAR (một buổi, theo thứ tự; playbook cũ §6 `perf-profile-2026-09-16.md` vẫn áp)

| # | Kiểm gì | Lệnh / thao tác | Đạt khi |
|---|---|---|---|
| C1 | HAL/phút màn mặc định (K1b) | `adb logcat -d -s KachiPerf \| tail -5` sau 5 phút đứng yên | `HAL đọc` < 150/phút; nút dock có giá trị vẫn đổi ≤ 1 s khi chỉnh trên màn AC gốc (#5 owner 09-21) |
| C2 | a11y watchdog 0 shell khi bound (B1) | `adb logcat -d -s NavConnect NavRebind`; `dumpsys accessibility \| grep -A3 "Bound services"` | có dòng "đã BOUND (AccessibilityManager) → bỏ dadb"; ROM giữ `getEnabledAccessibilityServiceList == mBoundServices` (nếu lệch ⇒ fallback shell vẫn chạy, không mất phím) |
| C3 | Phím vô-lăng vẫn ăn sau 30 phút lái | bấm phím 3 lần ở 3 mốc | mỗi lần Kachi nghe |
| C4 | Camera xi-nhan (B2: hẹn giờ HOLD thay 250 ms; 1 controller) | bật/tắt công tắc 3 lần; xi-nhan nháy; tắt hẳn | camera lên khi nháy, tắt sau ~1,2 s; `ps -T -p $(pidof com.byd.launcher) \| grep -c KachiHalSignal` ≤ 1 (= 0 khi tắt) |
| C5 | Ô điều khiển ghi off-main (P1-main) | bấm nhanh 5 lần nút gió/nhiệt; bấm 1 nút HAL từ chối | không giật khung; nút lật về giá trị cũ + `adb logcat -s ControlWrite` có W |
| C6 | `:wake` (loadavg → CPU tự đo; stand-down) | `adb logcat -d \| grep -c "avc: denied.*loadavg"`; wake OFF + phím-thoại ngoài Kachi, nói xong chờ 10 s: `dumpsys activity services \| grep VoiceWakeService` | 0 dòng avc; service rỗng / PSS `:wake` < 30 MB |
| C7 | Một mô hình khi wake ON | `adb logcat -d -s KachiVoiceEngine \| grep "BỎ QUA"`; `dumpsys meminfo com.byd.launcher \| grep "Native Heap"` | có "Hey Kachi đang bật"; native heap chính giảm ~85–110 MB so với 1.8x/2.6x |
| C8 | Crash handler | (không chủ động gây crash trên xe) — chỉ kiểm tệp `kachi-logs/crash-*.log` có xuất hiện khi có sự cố thật | có stack + tiến trình + phiên bản |
| C9 | Hình xe không nháy (car-image) | nhìn widget lốp 1 phút đứng yên | không nhấp |
| C10 | Ngưỡng tự-CPU guard `:wake` (OQ3: 0,5/0,25 × lõi) | `adb logcat -d -s WakeListen \| grep -c "hệ nóng"` trong 30 phút lái có nav+nhạc | không tắt-mở liên tục; hotword vẫn nhận |

Khi K5 (vòng 09-16) và C1–C10 xanh ⇒ cập nhật `PROJECT-BACKLOG.md` CLOSE-1 → DONE-oncar.
