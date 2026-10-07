# Kiểm kê việc nền + đo hiệu năng 4 trạng thái — 2026-10-07 (2.96 R18, spec `kachi-296-plan.html` §3 R18 · T11)

> Nguồn: mã trong cây (2.96 WIP, R1–R17) · log xe gitignored `logs/carlog-1007-restart/`, `logs/carlog-1007-ign2/`,
> `logs/201007/kachi-logs/` (SL6) — chỉ đếm tag/lệnh, không chép nội dung người dùng · máy ảo `kachi_play` (emulator-5556),
> bản release 2.95-WIP TRƯỚC và SAU vá. Mức bằng chứng (CLAUDE.md §2): **[ĐO]** · **[SUY]** · **[CHƯA BIẾT]**.

## 1. Kết luận một dòng

Chạy thường lúc đang chiếu tốn **19 lệnh shell/phút**, trong đó **15 là `am stack list` của lượt dò repin** và ~4 là `am stack
list` của nhịp đo ô — cùng một lệnh chỉ-đọc, cùng kênh dadb [ĐO]. Ở **standby** (màn tắt, tiến trình còn sống hàng giờ) lượt dò
repin **vẫn chạy mỗi 4 s** mỗi khi SoC thức [ĐO 8 h log], còn khi rảnh hẳn luồng `hud-keepalive` thức **4 lần/giây vô thời hạn**
dù không có frame nào (79 % số lần thức của tiến trình lúc màn tắt) [ĐO máy ảo]. HAL: 162/phút (Seal), 509/phút (SL6) — theo yêu
cầu realtime 1 Hz của owner, chỉ khi màn chính hiện, đã dừng ở `onStop` [ĐO] ⇒ không đụng.

## 2. Kiểm kê (tiến trình chính trừ khi ghi khác) — 34 việc định kỳ/vòng chờ

Cổng: **R** = chỉ khi màn chính RESUMED · **S** = STARTED · **M** = có cổng màn tắt · **—** = KHÔNG cổng màn (trước R18).

### 2.1 Việc chạy MÃI (vòng đời tiến trình / dịch vụ)

| # | Việc | file:hàm | Nhịp | Cổng | Chi phí mỗi nhịp |
|---|---|---|---|---|---|
| 1 | Nhịp nút nổi | `modules/clustercast/FloatingBubbleService.kt` `refresh` | 2 s | — (sống khi Cast BẬT, kể cả HIDDEN) → **R18: 10 s khi màn tắt** | prefs + vẽ; gọi #2 #3 #4 |
| 2 | **Dò repin** | `core/…/SimpleCastCoordinator.repinEscapedCastApps` → `Ops.doRepinEscapedCastApps` | ≥ 4 s | chỉ khi `Casting*`, `submitIfIdle` · — → **R18: ≥ 30 s khi màn tắt** | **`am stack list` (dadb)**; hụt 2 nhịp ⇒ chuỗi `castToCluster` |
| 3 | Gửi lại vị trí bóng VietMap | `VmOverlayPosition.applyOnOpen` | qua #1, cổng 15 s | Cast BẬT | `sendBroadcast` |
| 4 | Giữ ẩn bóng VietMap | `VmBubbleVisibility.keepHidden` | qua #1, cổng 15 s | công tắc ẩn | `sendBroadcast` |
| 5 | Watchdog a11y phím-thoại | `VoiceKeyKeepAliveService.watchdog` | 5 s rồi 30 s | phím-thoại BẬT · — | binder + **ghi SharedPreferences mỗi nhịp** (→ **R18: chỉ khi đổi**); chưa gắn ⇒ dadb |
| 6 | Alarm watchdog | `RebindReceiver.scheduleWatchdog` | 60 s **WAKEUP** | — (ROM `ssc_skip` hay chặn [ĐO logcat]) | `requestRebind`; a11y chỉ khi #5 chết |
| 7 | NLS heal | `NlsHeal.onWatchdog` | theo #6 | **M** | thường 0; CHECK ⇒ dadb ≤ 5 phút/lần |
| 8 | **Giữ HUD** | `NavigationHudOwner.keepAliveTick` | **250 ms** | — (bật làn cụm là chạy mãi) → **R18: tự huỷ khi hết frame, dựng lại ở lần đẩy thật** | so khoá; có frame ⇒ ghi HAL |
| 9 | Nhịp tim làn cụm | `AmapEmissionArbiter`/`ClusterBroadcaster` | 400 ms | chỉ ≤ 180 s sau frame nguồn | broadcast (+ đọc tốc độ) |
| 10 | Nhịp widget VietMap | `VietMapWidgetBridge.freshnessTick` | 1 s / 10 s | NLS nối · — | RAM; danh mục provider ≤ 60 s |
| 11 | **HomeGuard** (2.96 R8) | `launcher/HomeGuard.tick` | 5 s (5 phút đầu chuyến) rồi 30 s | — (chỉ bỏ lượt đặt lại) → **R18: màn tắt ⇒ không đọc HOME, nhịp 10 s** | binder `isInteractive` + `resolveActivity` + prefs |
| 12 | Lấy mẫu YouTube (2.94) | `trip/YoutubeResumeSampler.tick` | 60 s | — | binder `isMusicActive`; đang phát ⇒ MediaSession + `commit()` |
| 13 | Động cơ automation | `automation/AutomationService.startLoop` | 60 s | có automation BẬT · — | HAL/vị trí |
| 13b | Socket tín hiệu camera | `camera/HalSignalClient.loop` | chặn đọc; nối lại 1→8→60 s | công tắc camera | socket |
| 14 | Chép logcat ra thẻ | `KachiLog.startCapture` | liên tục | — (trần 8 MB/tệp) | một tiến trình `logcat` + ghi tệp |
| 15 | Bộ nghe "Hey Kachi" | `voice/VoiceWakeListener` | 100 ms/khung | **M** (màn tắt ⇒ nhả mic, ngủ 5 s) | mic + KWS · **`:wake`** |
| 16 | Daemon phím | `system/inputd/InputDaemonMain` | chặn `accept` | — | 0 · uid 2000 riêng |

### 2.2 Việc của màn chính (dừng khi màn khuất)

| # | Việc | file:hàm | Nhịp | Cổng | Chi phí |
|---|---|---|---|---|---|
| 17 | Nhịp màn chính (đồng hồ · widget · ảnh nền · KachiPerf) | `KachiHomeActivity.tick` | 10 s | **R** | định dạng giờ, duyệt cây view |
| 18 | Quét lại ảnh nền (2.96 R4) | `WallpaperController.rescanIfDue` | qua #17: trống 10 s / có ảnh 60 s | **R** + bật ảnh nền | liệt kê thư mục (luồng nền) |
| 19 | Đo ô sống | `SlotLiveProbe.sweep` | 5 → 10 → 15 s | **S** + có ô | **`am stack list`** → **R18: dùng lại bản của #2 nếu mới** |
| 20 | HAL nhanh | `core/…/CarStatusRepository` | 1 s | **S** (`repeatOnLifecycle`) | 3 + N ô điều khiển trên màn |
| 21 | HAL chậm | như trên | 10 s | **S** | ≤ 76 đọc, chỉ datum đang hiện |
| 22 | Cổng kênh shell F4 | `ShellChannelGate` | 1,5 s rồi 20 s | **S**, tới khi kênh lên | phiên dadb `echo` |
| 23 | Trình chiếu widget ảnh | `PhotoWidgetView.tick` | ≥ 5 s | cửa sổ HIỆN | giải mã ảnh khi tới hạn |
| 24 | Trạng thái model giọng (Cài đặt) | `SettingsVoiceSection` | 1,5 s | chỉ khi trang mở | đọc tệp |

### 2.3 Vòng chờ có trần (khởi động / sự kiện)

| # | Việc | file:hàm | Nhịp · trần | Chi phí |
|---|---|---|---|---|
| 25 | Chờ màn chính cho nhạc chuyến | `trip/TripStart.awaitReady` | 1 s · ≤ 180 s | **`am stack list`** mỗi giây (chỉ sau màn bật) |
| 26 | Chờ phiên nhạc | `trip/TripMusicRun` | 1 s · ≤ 15 s | binder |
| 27 | Chờ bóng VietMap | `VietMapAutostart.awaitBubble` | 500 ms · ≤ 60 s | **2 `dumpsys` / 500 ms** (đo: READY sau ~6 s khi VietMap nguội) |
| 28 | Dò kênh sớm | `EarlyShellChannel.earlyBody` | 1 s, 2 s · 3 lần | dadb |
| 29 | Chữa a11y theo vòng đời | `A11yLifecycleHeal` | 250 ms / 100 ms · ≤ 20 s | binder, ≤ 2 `dumpsys` |
| 30 | Mở chiếu thử lại | `BubbleAutostart.open` | 3 s × 5 | dadb |
| 31 | Đặt bù nền chiếu (2.96 R15) | `CastPlaceholder.schedulePlaceholderRecover` | 4 s rồi 3 s × 5 | dadb |
| 32 | Chặn PiP | `BubblePipGuard.block` | 1 lần / dịch vụ | appops ×gói đã cài |
| 33 | Đóng ô / đỗ ẩn | `StackReads.settle`, `SlotClose`, `HiddenPark` | ≤ 4 s | `am stack list` mỗi bước |
| 34 | Tự khởi động launcher | `KachiAutostart.runBoot` | 1 lần · gác 30 s | set-home + `am start` → **R18: bỏ `am start` khi màn chính đã resumed** |

Ngoài ra (không định kỳ): `:tts` `SherpaTtsSpeaker.drain` 20 ms khi đang đọc · `VoiceWakeService` stand-down 2 s ≤ 3 phút ·
`CameraSynthFeeder` 200 ms chỉ phiên camera giả · `CastLifecycleReceiver.schedule` = mã chết (không call site, không trong manifest).

## 3. Số đo TRƯỚC

### 3.1 Xe — chạy thường (màn chính hiện, đang chiếu) [ĐO]

| Nguồn | Cửa sổ | HAL đọc/phút | shell/phút | trong đó `am stack list` repin |
|---|---|---|---|---|
| Seal 2.95, `usage-1791368197456` | 17:20–18:40 (80 phút) | 162 | 19,0 | 1 197 lệnh = **14,96/phút** |
| SL6 2.95, `201007/usage-1791344372399` | 10:45–12:20 (95 phút) | 509 | 19,0 | 1 422 lệnh = **14,98/phút** |
| SL6, đêm 06→07/10 (`usage-1791291278969`, màn bật 9,5 h) | 566 dòng KachiPerf | 509 | 19,0 | ~15/phút |

19 − 15 = ~4/phút còn lại khớp `SlotLiveProbe` ở trần 15 s (không ghi log từng lệnh) [SUY].

### 3.2 Xe — standby (tiến trình bật lúc màn tắt, sống hàng giờ) [ĐO]

| Nguồn | Cửa sổ | `am stack list` | Nhận xét |
|---|---|---|---|
| `usage-1791336721400` (`proc interactive=false`) | 08:32→16:52 (8 h 20) | **548** | chùm 4 s/lệnh mỗi khi SoC thức (vd 09:13–09:16); 0 dòng KachiPerf (màn chính không resumed) |
| `usage-1791366834754` (khởi động lại thật, màn tắt) | 16:53→17:15 (22 phút) | 63 | cũng chỉ là lượt dò repin |

HAL ở standby ≈ 0 (chỉ vài lượt dựng thiết bị lúc khởi động) — đã có cổng `onStop` [ĐO].

### 3.3 Xe — khởi động lại đầu xe thật [ĐO]

Pid thấp (~3 900) + `boot=n178/n180`: 16:53 (màn tắt) và 20:11. BOOT_COMPLETED tới **0,7 s / 2,7 s** sau khi tiến trình bật ở hai lần
khởi động thật; còn ở **mỗi lần nổ máy** (tiến trình nguội do BYD giết) nó tới **~24 s** sau màn bật (`startup-timeline-2026-10-07.md`).
Lúc đó `KachiAutostart.runBoot` chạy trọn, gồm `am start -n KachiHomeActivity` (20:48:44.40 → 44.90 ≈ 0,5 s) dù màn chính đã lên từ
+2,7 s và đúng lúc lượt mở chiếu cụm đang xếp hàng trên cùng kênh. Các phần còn lại của đường BOOT đều idempotent có cổng (a11y hỏi
binder trước, freeform đọc-trước R13, model "Hey Kachi" R11, set-home đọc trước, `KachiAutostart` gác 30 s) [ĐO mã].

### 3.4 Máy ảo `kachi_play`, release 2.95-WIP, màn chính + YouTube trong ô, không chiếu cụm

| Trạng thái | CPU % (1 lõi) | PSS KB | RSS KB | Luồng | Lần thức/giây (tổng) | Lớn nhất |
|---|---|---|---|---|---|---|
| Màn bật | 0,13 | 54 861 | 105 848 | 40 | 7,62 | `hud-keepalive` 4,00 · main 0,75 · `kachi-home-guard` 0,60 |
| Màn tắt | 0,05 | 56 495 | 107 172 | 38 | 5,02 | `hud-keepalive` 3,97 (79 %) · `kachi-home-guard` 0,60 |

Lần thức = tổng `voluntary_ctxt_switches` theo từng tid trong 60 s (`scripts/emulator/thread-wakeups.sh 60 emulator-5556`); CPU/PSS/RSS/luồng =
`scripts/emulator/perf-snapshot.sh` 120 s (JSON ở `docs/diagnostics/perf-r18-2026-10-07/`, cả bản markdown). Máy ảo không có HAL xe và không chiếu
cụm ⇒ không đo được #2/#19 ở đây; chúng lấy số từ log xe.

## 4. Đã sửa (R18) — mỗi sửa một bài khoá

| # | Sửa | Tệp | Mức | Tiết kiệm |
|---|---|---|---|---|
| 1 | Màn tắt ⇒ nhịp nút nổi 2 → 10 s, dò repin ≥ 4 → ≥ 30 s (`interactive == false` mới thưa; `null`/bật = y cũ) | `core/…/system/StandbyCadence.kt` · `SimpleCastCoordinator.repinEscapedCastApps(interactive)` · `FloatingBubbleService.refresh` · `app/…/system/ScreenInteractive.kt` | [ĐO] gốc · [SUY] mức tiết kiệm | standby: `am stack list` −87 % (8 h log: 548 → ~73); lần thức nút nổi 0,5 → 0,1/s |
| 2 | Bản đọc `am stack list` dùng chung: repin ghi, đo ô dùng lại nếu chụp SAU nhịp đo trước và ≤ 4 s | `core/…/system/StackListSnapshot.kt` · `SimpleCastCoordinatorOps` · `SlotLiveProbe.sweep` | [ĐO] gốc · [SUY] tiết kiệm (cần xe) | chạy thường đang chiếu + có ô: 19 → ~15 lệnh/phút (−21 %) |
| 3 | Nhịp giữ HUD 250 ms tự huỷ khi hết frame (kiểm + huỷ trong `keepAliveLock`), dựng lại ở lần đẩy thật (Lỗ 3 giữ nguyên) | `core/…/navigation/HudKeepAlivePolicy.hasFrame` · `NavigationHudOwner.keepAliveTick` | [ĐO máy ảo] | −4 lần thức/giây mọi lúc không dẫn đường (xem §5) |
| 4 | HomeGuard màn tắt: không đọc prefs/HOME, nhịp 10 s | `HomeGuardPolicy.nextDelayMs(…, interactive)` · `HomeGuard.tick` | [ĐO máy ảo] | 0,6 → ~0,1 lần thức/giây; −1 binder/nhịp |
| 5 | Boot bỏ `am start` màn chính khi đã có màn RESUMED trong tiến trình (MY_PACKAGE_REPLACED = tiến trình mới ⇒ vẫn chạy) | `core/…/launcher/BootHomeUp.kt` (+ `HomeResumed`) · `KachiHomeActivity.onResume/onPause` · `KachiAutostart.runBoot` | [ĐO] 0,5 s kênh | −1 lệnh shell ~0,5 s mỗi lần nổ máy, đúng lúc mở chiếu |
| 6 | Watchdog phím-thoại chỉ ghi mốc ngủ khi lệch ≥ 1 s | `A11yBindJournal.shouldPersistDeepSleep` · `VoiceKeyKeepAliveService.wokeFromLongSleep` | [ĐO mã] | −~120 lần ghi tệp prefs / giờ |

Không đổi: thứ tự opcode / lệnh mở chiếu / lệnh đặt slot (CLAUDE.md §6) · mọi nhịp khi màn bật (trừ việc trùng).
Test: `:core` `system/StandbyPerfR18Test` (8) · `:app` `perf/StandbyPerfR18WiringContractTest` (6).

## 5. Số đo SAU

### 5.1 Máy ảo `kachi_play` — cùng thủ tục §3.4, bản release có R18 [ĐO]

| Trạng thái | CPU % (1 lõi) | PSS KB | RSS KB | Luồng | Lần thức/giây | Đổi so TRƯỚC |
|---|---|---|---|---|---|---|
| Màn bật | 0,11 | 65 364 | 138 756 | 40 | **3,62** (trước 7,62) | `hud-keepalive` 4,00 → **0** |
| Màn tắt | 0,00 | 59 947 | 120 824 | 38 | **0,68** (trước 5,02, **−86 %**) | `hud-keepalive` 3,97 → **0** · `kachi-home-guard` 0,60 → **0,20** |

PSS/RSS SAU cao hơn là do tiến trình MỚI cài (trước: tiến trình đã chạy lâu, bộ nhớ đã thu gọn) — R18 không thêm cấp phát
lâu dài nào (một chuỗi `am stack list` gần nhất + vài trường) [SUY]; so bộ nhớ phải cùng tuổi tiến trình. Luồng `hud-keepalive`
vẫn tồn tại (bộ lập lịch) nhưng không thức.

### 5.2 Xe — ước lượng từ log (cần đo lại sau OTA) [SUY]

| Trạng thái | Trước [ĐO] | Sau (ước) | Cách chốt trên xe |
|---|---|---|---|
| Chạy thường, đang chiếu + có ô | shell 19/phút | ~15/phút | dòng `KachiPerf … shell=` |
| Standby (8 h, `usage-1791336721400`) | 548 `am stack list` | ≤ ~73 | đếm `SimpleCast shell: am stack list` khi `proc interactive=false` |
| Mỗi lần nổ máy, BOOT_COMPLETED +24 s | `am start` màn chính ~0,5 s trên kênh | 0 | dòng `HOME already resumed in-process — skip am start (R18)` |
| Không dẫn đường | `hud-keepalive` 4 lần thức/giây | 0 | — (không log; đo bằng `voluntary_ctxt_switches` qua `ClusterDiag` nếu cần) |

## 6. Đề xuất, chưa làm (lý do)

- **Alarm watchdog 60 s `ELAPSED_REALTIME_WAKEUP`** (`RebindReceiver`) thức SoC mỗi phút ở standby; nhưng ROM hay `ssc_skip` nó và
  nó là lưới phụ của đường tự chữa đã chạy ngoài xe ⇒ đổi sang không-WAKEUP cần owner duyệt + đo pin/standby trên xe [CHƯA BIẾT].
- **Dò repin khi màn bật** vẫn 15/phút: lùi nhịp khi ổn định (kiểu `SlotLiveness`) sẽ làm chậm phát hiện "Kiki kéo GMaps khỏi cụm"
  4–8 s → 8–16 s — đổi hành vi người lái thấy ⇒ cần owner.
- **`VietMapAutostart.awaitBubble`** 2 `dumpsys`/500 ms lúc khởi động (~6 s ⇒ ~24 lệnh) — thưa 1 s chậm READY 0,5 s; đường đã chạy
  ngoài xe ⇒ đo xe trước (§6).
- **Nút nổi không bao giờ mờ**: `refreshBubbleState()` gọi `wakeBubble()` mỗi 2 s ⇒ hẹn mờ 2,5 s không bao giờ tới [SUY mã] — lỗi
  hành vi, không phải hiệu năng; chuyển T10.
- **HAL 509/phút (SL6)** — theo yêu cầu realtime 1 Hz (owner 09-21), đã dừng ở `onStop` ⇒ giữ.
- **Bản ghi `isInteractive` trùng 7 chỗ** — R18 chỉ thêm `ScreenInteractive` cho mã mới + HomeGuard; gom các chỗ cũ là refactor
  ngoài phạm vi.
