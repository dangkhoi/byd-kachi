# Hiệu năng trên xe — chiều tối 15/10, Kachi 3.1.8 (213)

> Đo **chỉ-đọc** qua cầu adb (top · /proc · dumpsys · logcat), xe đang chạy, nhạc YouTube đang phát, "Hey Kachi" BẬT.
> Cửa sổ đo: 17:58:28 → 18:06:48 (logcat liên tục) + 13 mẫu `top` 5 s (≈18:00:10 → 18:01:10).
> Phiên bản [ĐO]: `dumpsys package com.byd.launcher` ⇒ `versionName=3.1.8 versionCode=213`.
> Không cài, không ghi pref, không chạm, không `am/wm/input/settings`. `simpleperf` có trên ROM nhưng `security.perf_harden=1`
> ⇒ không lấy mẫu được app phát hành nếu không `setprop` (lệnh ghi — không làm) ⇒ phần "ai ăn luồng chính" còn [CHƯA BIẾT].

Mức bằng chứng: **[ĐO]** đo/đọc log hôm nay · **[SUY]** suy từ mã + số đo · **[ĐOÁN]** chưa kiểm · **[CHƯA BIẾT]**.

---

## Tóm tắt

| # | Triệu chứng | Kết luận | Mức | Hồi quy 3.1.7/3.1.8? |
|---|---|---|---|---|
| A | `:wake` 80–263 % CPU | Bộ nghe "Hey Kachi" dùng **ASR offline** (giải mã cửa sổ 1,6 s, 4 luồng) — khi nhạc phát, cổng RMS 0,010 luôn mở ⇒ giải mã **gần như liên tục**; "trần cứng" tầng 4 đếm **khung**, không đếm **thời gian suy diễn** ⇒ không chặn được CPU | [ĐO] nhịp + CPU · [SUY] cơ chế từ mã | **Không** — có sẵn từ 2026-09-22/24; 14/10 và sáng 15/10 cùng dạng |
| B | 3D vẽ ~1 khung/s | Nhịp **xi-nhan** (`blinkTick`) — đúng thiết kế; khi không xi-nhan 3D vẽ **0 khung** (4 phút liền) | [ĐO] | Không |
| B' | HAL đọc 517/phút dù đã có sự kiện | Ô điều khiển AC bị đọc **mỗi 1 s** vì `readControls` không hỏi `covered` ở nhịp thường | [ĐO] log SDK · [SUY mạnh] từ mã | Không (có từ 3.1.6) |
| D | Tương phản R29 chọn lại ~700 mặt/phút | Mỗi lần tô một phần tử **chưa đo được** (`GONE`/0×0) ⇒ một lượt chọn họ cả mặt gốc; >95 % ra họ cũ | [ĐO] số đếm · [SUY mạnh] kẻ kích từ mã | Không — 3.1.7/3.1.8 không chạm `AdaptiveInk`/`Car3dHeroLayer`/`Car3dFootRow`/`CarDataAdapter` [ĐO `git show --stat`] |
| C | Sự kiện ĐẦU theo device | Instrument · PM2p5 · Tyre · Ac · Statistic: **có**. Bodywork · Setting: đăng ký OK, **chưa** có sự kiện. Light: [CHƯA BIẾT] | [ĐO] | — |

---

## A. `:wake` CPU

### A1. Hình dạng tải: dồn cục, không đều [ĐO]

13 mẫu `top -d 5` (CPU cộng theo tiến trình, % một lõi; máy 8 lõi — `nproc`=8):

| mẫu | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 | 10 | 11 | 12 | 13 |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| `:wake` | 197 | 87 | 9 | 178 | 241 | 8 | 0 | 1 | 0 | 17 | 160 | 263 | 186 |
| main | 13 | 74 | 19 | 15 | 19 | 24 | 14 | 27 | 20 | 23 | 16 | 17 | 22 |
| `:tts` | 84 | 0 | 0 | 26 | 53 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |

- Luồng nóng [ĐO `/proc/<pid>/task/*/stat`, cộng dồn ~6 phút tuổi tiến trình]: `kachi-wake` (luồng nghe, gọi `decode`) 85 s ·
  3 luồng onnx 16305–16307 ≈ 179 s · 6 luồng onnx 16397–16405 ≈ 99 s. Tổng tiến trình 380 s CPU / ~360 s tuổi ⇒ **TB ≈ 105 % một lõi**
  (gồm cả nạp mô hình và một phiên lệnh).
- Khoảng 0 % (mẫu 6–9) trùng đúng quãng log **không có dòng `sherpa ra`** nào (18:00:29 → 18:00:44) [ĐO]. Nghĩa là CPU = số lượt giải mã.

### A2. Nhịp giải mã [ĐO logcat]

- `KachiVoiceRec: sherpa ra` từ luồng `kachi-wake` (log ở **mọi** lượt giải mã, kể cả chuỗi rỗng — `VoiceRecognizer.kt:163`):
  102 lượt trong ~8 phút; khoảng cách giữa hai lượt liền (< 3 s): **p50 1 294 ms · p90 2 447 ms · max 2 934 ms · min 737 ms** (75 cặp).
  Sáng 15/10 (`oncar-bugs-2026-10-15.md`): p50 1 212 · p90 1 926 · max 2 667 ⇒ **cùng dạng**.
- 97/102 lượt ra chữ khác rỗng ⇒ cửa sổ có âm thanh to (nhạc/lời) — cổng RMS không cắt gì [ĐO đếm; nội dung không ghi vì là lời nói].
- Nhạc [ĐO `dumpsys media_session`]: YouTube `state=3` (PLAYING); `dumpsys audio`: một AudioTrack `USAGE_MEDIA … state:started`.
- Chỉ 1 lần khớp câu gọi thật trong cửa sổ đo (17:59:36) [ĐO].

### A3. Cơ chế [SUY từ mã, khớp số đo]

1. `VoiceWakeListener.kt:272-290`: mỗi khung đọc 100 ms (1 600 mẫu) hỏi `VoiceWakeController.onFrame`; `RUN_KWS` ⇒ `kws.feed`.
2. `VoiceWakeAsr.kt:35-50`: `feed` gom cửa sổ cuộn 1,6 s, cứ **500 ms** mẫu (`DECODE_EVERY_MS = 500`, `:80`) thì **giải mã cả cửa sổ** bằng
   mô hình ASR chung, trừ khi RMS cửa sổ < `RMS_GATE = 0.010` (`:44`, `:84`). Nhạc trong cabin luôn > 0,010 ⇒ giải mã mỗi lượt có thể.
3. Mô hình chung mở với `numThreads = threadsForDecode()` = `nproc/2` kẹp 2..4 ⇒ **4 luồng** trên xe này (`VoiceRecognizer.kt:475-476`;
   wake dùng `openFree` cùng recognizer, `:217-219`).
4. "Trần cứng" tầng 4 `VoiceWakeController.chargeKws` (`VoiceWakeController.kt:84-89`, mặc định 40 khung / 10 s — `:120-121`) **đếm khung**.
   KDoc `:76-78` hứa "≤ 40 %" — đúng cho KWS **dòng** cũ (`VoiceWakeKws`, 1 luồng, mỗi khung rẻ), **sai** cho ASR: 5 khung = 1 lượt giải mã
   0,7–2,9 s × 4 luồng ⇒ trần cho phép tới ~8 lượt/10 s, thực tế bị chặn bởi chính thời gian giải mã (~1 lượt/1,3 s) ⇒ **chạy kín**.
5. Lá chắn tầng 1 `VoiceLoadGuard.forSelfCpu`: dừng khi tiến trình > `0.5 × nproc` = **4 lõi** (`VoiceLoadGuard.kt:83`) ⇒ 2,6 lõi không bao giờ chạm.

⇒ Gốc: **ngân sách suy diễn tính bằng khung chứ không bằng thời gian/CPU**, áp lên một engine mà mỗi lần gọi đắt gấp hàng chục lần
engine nó được thiết kế cho. Không phải lỗi của nhạc — nhạc chỉ làm lộ ra.

### A4. Có phải hồi quy 3.1.7/3.1.8? — **Không** [ĐO]

- `git show --stat 5e24bea1` (3.1.7) và `45afd095` (3.1.8): không chạm `VoiceWakeListener/VoiceWakeService/VoiceWakeAsr/VoiceWakeController`.
  `holdMicForSession` + lần hỏi cờ nhường trước `feed` là của **3.1.6** (`434a1466`) và chỉ làm bộ nghe **đỗ** trong lượt nhường (giảm tải).
- `snap.txt` cùng định dạng `top -n1` (một mẫu/phút): chuyến 14/10 — 49 mẫu, max 380 %, 5 mẫu > 50 %; sáng 15/10 — 37 mẫu, max 228 %,
  8 mẫu > 50 %; tối nay — 4 mẫu: 105 · 0 · 333 · 0. Cùng dạng dồn cục. (Bản đang chạy ở hai chuyến trước suy từ thứ tự commit:
  ≤ 3.1.4 và ≤ 3.1.5 — [SUY].)
- Gốc có từ 2026-09-22 (đổi KWS → ASR no-train) và nặng gấp đôi từ 2026-09-24 (`DECODE_EVERY_MS` 1000 → 500, ghi chú ở `:80`).

### A5. Hướng sửa (chưa làm)

Tầng đúng: **ngân sách suy diễn** (`VoiceWakeController`, `:core`, kiểm off-car được) — không vá riêng cho "khi có nhạc".

1. **Ngân sách theo thời gian, không theo khung** — `VoiceWakeController.kt:84-89`: `chargeKws` nhận chi phí thật (ms tường hoặc
   CPU-time của luồng sau `feed`, đo ở `VoiceWakeListener.kt:282`) và giữ tỉ lệ `Σ chi phí / cửa sổ ≤ trần` (vd 15 % một lõi-tường
   ⇒ với lượt 1,3 s: tối đa ~1 lượt / 9 s khi ồn liên tục). Test core: bơm chi phí 1 300 ms/lượt + "âm luôn to" ⇒ khẳng định trần.
   Sửa luôn KDoc `:76-78` (lời hứa ≤ 40 % hiện sai cho engine ASR).
2. **Cổng tiếng người trước ASR** — `VoiceWakeAsr.kt:44`: thay/thêm cổng RMS bằng VAD silero sẵn có (`VoiceVad`, 1 luồng) trên cửa sổ;
   nhạc có lời vẫn lọt VAD ⇒ chỉ là lớp phụ, không thay (1).
3. **Bớt luồng cho giải mã nền** — `VoiceRecognizer.kt:475-476`: wake dùng chung recognizer 4 luồng; nghe nền không cần độ trễ của phiên
   lệnh. Đánh đổi: recognizer riêng 1–2 luồng = thêm ~74 MB RAM ⇒ cần owner chọn [CHƯA BIẾT chi phí RAM thật trên xe].
4. (phụ) `VoiceLoadGuard.kt:83` `SELF_SUSPEND_FRACTION = 0.5` trên 8 lõi = 4 lõi — quá rộng để là lá chắn; xem lại sau khi có (1).

Đo lại sau sửa: cùng lệnh `top -d 5 -n 13` + đếm `sherpa ra`/phút khi nhạc phát; mục tiêu đề xuất `:wake` p90 < 30 %.

---

## B. 3D vẽ "mỗi giây"

### B1. Kẻ kích: xi-nhan [ĐO]

| Mốc | Sự kiện |
|---|---|
| 17:58:53.203 | `KachiHalSignal: xi-nhan trái=false phải=true` |
| 17:58:53.645 → 17:59:19.688 | 29 đợt `Car3d: đợt vẽ 1 khung` (tổng 6 → 34), ~1,1 khung/s |
| 17:59:18.460 | `KachiHalSignal: xi-nhan … phải=false` |
| 17:59:19.081 | `Car3d: blink [light_right_turn] -> []` — đợt cuối 17:59:19.688 |
| 18:02:40 → 18:06:48 | **0** đợt vẽ (sau lượt nạp lại lúc HOME hiện lại 18:02:36–40: 3 khung) |

- Đường gọi: `applyVariants` hẹn `blinkTick` đúng mép pha kế (`Car3dWidgetView.kt:296`) → `blinkTick` (`:69`) → `requestFrames(1)`.
  KDoc `applyVariants` ghi rõ đây là thiết kế (nháy ~1,5 Hz như bảng Đèn 2D, mỗi mép một khung, TẮT ⇒ bỏ hẹn).
- Ứng viên khác bị loại [ĐO]: không có dòng tap/paint/glass/swing nào trong quãng; HAL/fill vẫn chạy suốt 18:02–18:06 mà 0 khung ⇒
  `fill → applyVariants/retargetGlass/glow` không đòi vẽ khi số không đổi; nhịp nhanh 1 s, R29 ink, LampLive không tự sinh khung.

### B2. Giá [ĐO]

- Luồng chính / khung (29 khung): **TB 5,9 ms · trung vị ~3,3 ms**, hai khung lẻ 18,5 và 38,9 ms. ⇒ ~0,7 % một lõi trong lúc nháy.
  3D **không** phải phần chính của 25–75 % ở tiến trình main.
- Mỗi đợt 1 khung kết thúc bằng `taps.dump(...)` (một dòng log ~700 ký tự) + `Car3dInk.settle` → `AdaptiveInk.rescore`
  (`Car3dWidgetView.kt:472`) — vốn để chạy khi "xoay DỪNG". Đếm `KachiPerf` cho thấy nó **không** phải nguồn chính của bão tương phản:
  phút có nháy (17:59:23) `mặt=203`, phút không nháy (18:03:37) `mặt=467` [ĐO].

### B3. Hướng sửa (nhỏ, tuỳ chọn)

- `Car3dWidgetView.kt:472`: chỉ `dump` + `settle` khi camera thật sự đổi trong đợt (so yaw/pitch/zoom với lần settle trước) — nháy chỉ đổi
  vật liệu đèn. Lợi nhỏ (bớt ~1 dòng log/s + một lượt chấm lại/s lúc xi-nhan).

### B4. Tiến trình main — phần còn lại [ĐO số · nguồn CHƯA BIẾT]

`/proc` cộng dồn (≈534 s tuổi): tổng 144 s CPU ⇒ **TB 27 %**; luồng chính 62,8 s (11,7 %) · `HeapTaskDaemon` 14,1 s · `RenderThread` 9,4 s ·
`ReferenceQueueDaemon` 8,5 s · `hud-hal-deliver` 4,9 s · `KachiLogCapture` 3,0 s. GC: 100 lượt / 8,3 phút, giải phóng 549 MB
(~66 MB/phút churn). Không có profiler (perf_harden) ⇒ chưa chia được luồng chính cho tương phản R29 / dẫn đường / HAL tốc độ.
Một quan sát kèm [ĐO]: `BYDAutoSpeedDevice.getCurrentSpeed` gọi **trên luồng chính** ~2,8 lần/s khi đang dẫn đường
(`ClusterBroadcaster` nhịp tim 400 ms, `ClusterBroadcaster.kt:24` + `SpeedProvider.mps()` `:155`) — một binder đồng bộ mỗi nhịp trên UI thread.

---

## B'. HAL đọc 517/phút khi đã có sự kiện

`KachiPerf` [ĐO]: 17:59:23 `HAL đọc=515/phút · thay-bằng-sự-kiện=131 · sự-kiện=94` · 18:00:23 `398 · 211 · 60` ·
18:02:37 (cửa sổ 133 s, có quãng HOME khuất) `134 · 115 · 29` · 18:03:37 `517 · 114 · 81`.

Đếm log SDK BYD của tiến trình main, 18:02:40 → 18:05:00 (140 s, sau khi đăng ký lại đủ) [ĐO]:

| Getter | lượt/phút | Nhịp suy ra |
|---|---|---|
| `BYDAutoAcDevice` getTemprature + getAcWindLevel + getAcWindLevelManualSign + getAcCycleMode | ≈ 63 mỗi cái ⇒ **≈ 250** | 1 s |
| `BYDAutoBodyworkDevice.getDoorState` ×5 cửa | ≈ 96 | 3 s (nhịp thân xe) |
| `BYDAutoLightDevice.getLightStatus` ×7 | ≈ 42 | 10 s |
| `BYDAutoSpeedDevice.getCurrentSpeed` | ≈ 180 (≈170 trên luồng chính) | dẫn đường 400 ms — đường `SpeedProvider`, ngoài bảng HAL |

**AC mỗi giây — gốc** [SUY mạnh, đọc mã + ĐO log]: `CarDataAdapter.kt:382`

```kotlin
val want = controlDemand().let { w -> if (ev == null) w else w.filterTo(HashSet()) { readPathOf(it)?.id in ev } }
```

Ở nhịp thường (`eventIds == null`) `readControls` đọc **mọi** ô điều khiển đang hiện, không hỏi `covered`. `fastNeeded` (`:79`) có
loại ô đã được sự kiện phục vụ, nhưng nhịp 1 s vẫn chạy vì màn bày tốc độ (`needsFast`) ⇒ một khi nhịp chạy, `readControls` đọc lại cả
nhiệt/gió/lấy gió/dấu chỉnh tay. Sự kiện AC đã chứng minh tới helper (§C) ⇒ phần lớn ~250/phút là thừa.

Hướng sửa: `CarDataAdapter.kt:382` — nhánh `ev == null` lọc bỏ id có `readPathOf(it)?.id?.let(covered) == true` (đúng luật `fastNeeded` `:79`);
giữ nguyên đối soát 60 s (`HalEventCache.hit` tự trả `null` khi quá hạn ⇒ ô đó lại được đọc). Test core: `covered` = true cho datum
AC ⇒ nhịp nhanh 0 lượt đọc AC; = false ⇒ đọc như cũ. Ước lợi: −~250 lượt/phút ≈ −~6 % một lõi (theo ~23 ms/lượt đo 09-16) [SUY].

**Vì sao nhịp 1 s không bao giờ nghỉ** [ĐO mã]: `speed`, `gear`, `motor_power` là `CarDataDemand.FAST_IDS` (`CarDataDemand.kt:369-372`)
và **cố ý không có sự kiện** — `HalEventMap.kt:98-99` ghi `getCurrentGear` = tín hiệu ghép, `getCurrentSpeed` = "giữ poll 1 s
[CHƯA ĐO tần suất]". Màn có tốc độ ⇒ `needsFast` luôn đúng ⇒ nhịp 1 s chạy mãi ⇒ `readControls` kéo theo cả hàng ô điều khiển.
Về `ac_wind_auto`: [ĐO] có sự kiện ĐẦU `0x4040000c → ac_wind_auto` lúc 17:59:46 (ngay sau lệnh chỉnh gió), nên nó **có** đường sự kiện;
trước một lần đổi đầu tiên thì `covers()` trả false (luật "chưa chứng minh" của `HalEventCache.hit`) — nhưng kể cả khi đã chứng minh,
nhánh `ev == null` ở `:382` vẫn đọc nó. Tức là gốc là `readControls`, không phải riêng `ac_wind_auto`.

Hướng sửa chung (một tầng, `CarDataAdapter`):
1. `readControls` ở nhịp thường bỏ id đã `covered` (như trên) — áp cho mọi ô, không liệt kê tên.
2. Tốc độ có **hai** người đọc độc lập: nhịp nhanh 1 s của `CarDataAdapter` và `SpeedProvider` 2,8 lần/s trên luồng chính (B4) ⇒ một
   nguồn: `SpeedProvider` đọc giá trị vừa cất của nhịp nhanh (hoặc ngược lại, một bộ đọc tốc độ chung có tuổi tối đa), không gọi binder
   thứ hai. Đổi tốc độ sang sự kiện thì phải đo shell thô tần suất sự kiện trên xe trước (CLAUDE.md §14) — chưa làm được hôm nay.

Cửa 3 s: đúng luật fail-safe — `HalEventCache.hit` chỉ tin id sau sự kiện đầu của **chính nó** (hoặc id `PUSH_PROVEN` khi device đã đẩy
≥ 1 sự kiện); Bodywork chưa đẩy sự kiện nào ⇒ poll tiếp. Không phải lỗi.

Đăng ký [ĐO]: 18:01:15 `4 id / 1 device (+0 −52) · lý do: hết hoãn thu hẹp` — 9,6 s sau `Car3d dừng` (18:01:06, HOME khuất) ⇒ hoãn
thu hẹp 10 s của 3.1.8 chạy đúng; 18:02:36 nở lại `39 id / 6 device` rồi `56 id / 8 device` (`nhu cầu màn`). Mọi dòng `từ-chối=—`.

---

## D. Bão chọn họ tương phản R29 (~700 mặt/phút)

### D1. Số đo [ĐO `KachiPerf`]

| Mốc | mặt | khác-họ | đổi-họ | dưới-sàn |
|---|---|---|---|---|
| 17:59:23 | 203 | 161 | 7 | 148 |
| 18:00:23 | 321 | 263 | 18 | 241 |
| 18:03:37 | 467 | 405 | 22 | 375 |
| 18:06–18:07 (điều phối viên đọc) | 619–706 | 557–644 | 22 | 527–614 |

`mặt` = số lần `ContrastInk.note` chạy = **một lượt `AdaptiveInk.decide` (chọn họ) trên một mặt gốc** (`ContrastInk.kt:141-146`).
`đổi-họ` chỉ 7–22/phút ⇒ >95 % lượt chọn lại ra **đúng họ cũ** ⇒ công vô ích. Log `dưới sàn:` theo mặt gốc (17:58–18:10, kể cả `[+N lặp]`):
`Car3dWidgetView` họ#1 49 dòng · `LinearLayout` họ#1 32 · `Car3dWidgetView` họ#3 8 ⇒ khung 3D là mặt gốc bị chọn lại nhiều nhất.
Bão **không** đi theo 3D vẽ: phút không nháy (18:03) cao hơn phút có nháy (17:59) [ĐO] ⇒ không phải `Car3dInk.settle` sau mỗi khung.

### D2. Kẻ kích [SUY mạnh, đọc mã — chưa có vết luồng để chốt ĐO]

`AdaptiveInk.inkUnsafe` (`AdaptiveInk.kt:224-228`) — mỗi lời gọi `AdaptiveInk.text/icon` (chỗ gọi tô màu trong `fill` của ô, chạy **mỗi nhịp**):

```kotlin
if (stale || g == null || root.second.family < 0 || slot(v, key, role(v, key)) !in root.second.seen) {
    refresh(root.first, root.second, force = stale, cascade = false, rescore = true)
```

- `g = mark.ground` chỉ được đặt khi view **đã gắn và có cỡ > 0** (`groundOf`, `:337`; `measure`, `:285`). View `GONE` / `LayoutParams(0, 0)`
  **không bao giờ** có `ground` ⇒ **mỗi** lần tô nó là một `refresh(..., rescore = true)` = đi cả cây mặt gốc + `decide` (+1 `mặt`).
- Có đúng loại view ấy trong khung 3D, được tô **mỗi nhịp**: `Car3dHeroLayer.fill` (`Car3dHeroLayer.kt:86-108`) gọi `AdaptiveInk.text` cho
  4 lốp × 2 nhãn mỗi lần đổ số, nhãn nhiệt bị `GONE` khi không có số (`:102`); ô ẩn dựng `GONE` cỡ `0×0` (`:57`); `Car3dFootRow` ô ẩn `GONE`
  (`Car3dFootRow.kt:65`). Ô thường (mặt gốc `LinearLayout`) cùng đường.
- Nhịp đổ số: 1 s (nhịp nhanh, chạy mãi vì tốc độ — B') + nhịp sự kiện + 3 s thân xe ⇒ vài view không-đo-được × ~1–2 lần/s ⇒ đúng bậc
  hàng trăm `mặt`/phút. Mỗi lượt cũng gọi lại `backdropOf` (ghép nền) cho phần tử dời/đổi ⇒ cộng vào luồng chính 11,7 % (B4) [SUY].
- Phụ: `Car3dHeroLayer.fill` gọi `reproject()` → `Car3dInk.settle` → `rescore` mỗi khi tập "lốp có số" đổi (`:108`); sự kiện áp suất
  đến lẻ tẻ (§C) có thể bật/tắt nhãn [ĐOÁN — chưa thấy dấu bật/tắt trong log].

Cách chốt [ĐO] (off-car được): máy ảo, một `TextView` `GONE` có dấu mực trong một mặt, gọi `AdaptiveInk.text` 1 Hz ⇒ đếm
`ContrastInk.resolved`; dự đoán +60/phút. Trên xe: bản debuggable + `simpleperf` hoặc một bộ đếm "lượt chọn vì phần tử chưa đo".

### D3. Hướng sửa chung (một chỗ, `AdaptiveInk`)

1. `AdaptiveInk.kt:226`: phần tử **chưa đo được** (`!isAttachedToWindow || width <= 0 || height <= 0` hoặc `!isShown`) ⇒ chỉ gắn dấu,
   **không** `refresh`; lượt giải đến từ `onLayoutChange` khi nó thật sự có cỡ (đường sẵn có, `AdaptiveInkParts.kt:29`). Áp cho mọi ô, không
   theo tên view.
2. Gộp: nhiều lời `text()` trong cùng một `fill` ⇒ **một** `refresh` mỗi mặt gốc mỗi khung (đánh dấu bẩn + `post` một lần), thay vì một
   lượt mỗi lời gọi.
3. Bộ đếm mới trong `KachiPerf`: `chọn-vì-chưa-đo` / `chọn-vì-phần-tử-mới` ⇒ chuyến sau thấy thẳng kẻ kích, không cần suy.
Test: mặt có 1 phần tử `GONE` tô lại 60 lần ⇒ `resolved` tăng 0 (đỏ khi bỏ sửa 1); 8 lời `text()` cùng một nhịp ⇒ `resolved` +≤1 (sửa 2).

---

## C. Sự kiện ĐẦU theo device [ĐO]

| Device | Đăng ký (18:02:36) | Sự kiện ĐẦU (id → datum, giờ) |
|---|---|---|
| Instrument | 13 id, 0 từ chối | `0x4a50a024` tyre_t_fr 17:58:38 · `0x4a503040` ext_temp 17:58:44 · `0x4a50b012` trip_hours 17:59:09 · `0x4a50b01c` trip_km 18:02:38 · `0x4a50a030` tyre_t_rl 18:02:55 |
| PM2p5 | 4 id | `0x4f600033` pm25_level 17:58:45 · `0x4f600010` 18:02:37 · `0x4f60001c` 18:02:38 |
| Tyre | 13 id | `0x99000128` tyre_p_fr 17:59:24 · `0x99000130` tyre_p_rr 18:02:52 · `0x99000124` tyre_p_fl 18:03:25 · `0x9900012c` tyre_p_rl 18:03:27 |
| Ac | 6 id | `0x40400028` 17:59:46 · `0x4040000c` ac_wind_auto 17:59:46 · `0x4040001c` ac_wind 17:59:46 — cả ba ngay sau lệnh giọng chỉnh nhiệt/gió |
| Statistic | 2 id | `0x4a50203e` ev_range_km 18:02:37 |
| Bodywork | 11 id | **chưa có** trong ~8 phút (cửa không đổi — [SUY] không có gì để đẩy) |
| Setting | 6 id | **chưa có** dòng ĐẦU |
| Light | không thấy dòng đăng ký ở lượt 18:02:36 | [CHƯA BIẾT] — xi-nhan đến qua `KachiHalSignal`, đường khác |

Ghi chú: dòng đăng ký lúc khởi động (~17:55) đã trôi khỏi bộ đệm logcat (xe ghi log rất dày) — chỉ thấy các lượt từ 18:01.

---

## E. Sửa B' — `HAL-POLL-COVERED` (3.1.9, chưa lên xe)

### E1. Gốc, sửa lại so với B' [SUY mạnh, đọc mã + khớp `KachiPerf`]

B' quy gốc cho `readControls` không hỏi `covered`. Đọc kỹ thì **chưa đủ**: khi `covered` đúng, lượt `readState` đi qua
`EventCachedGateway` và được bộ nhớ sự kiện trả lời ⇒ **0 lượt HAL** dù `readControls` không lọc. AC vẫn bị đọc thật vì `covers()` **sai**:

1. `HalEventCache.setPlan` thu `seen.retainAll(keep)`. 18:01:15 kế hoạch thu về `4 id / 1 device` (HOME khuất) ⇒ bằng chứng sự kiện
   AC 17:59:46 bị xoá; 18:02:36 nở lại thì AC chưa có sự kiện mới ⇒ poll 1 s. Khớp `KachiPerf`: 17:59:23 515 → 18:00:23 **398**
   (sau sự kiện AC đầu, `thay-bằng-sự-kiện` 131 → 211) → 18:03:37 **517** (sau lượt thu hẹp).
2. Id ngoài `PUSH_PROVEN` chỉ được tin sau sự kiện của **chính nó**. Id chưa đổi giá trị lần nào (vd chế độ lấy gió) không bao giờ có
   sự kiện ⇒ poll mãi.
3. Nhịp 1 s không nghỉ vì **ô điều khiển AC** (nhiệt/gió/lấy gió, `HOSTED w_car3d`), không phải vì tốc độ: tốc độ không ở nhu cầu
   màn 3D (`CarDataDemand.CURATED` chỉ `w_speed` bày nó), và ~170/180 lượt `getCurrentSpeed` là luồng chính = nhịp tim dẫn đường.
   Câu "màn có tốc độ ⇒ `needsFast` luôn đúng" ở B' là [SUY] sai cho phiên này.

### E2. Sửa (một tầng mỗi việc, không tên gói)

| # | Tầng | Thay đổi |
|---|---|---|
| 1 | `HalEventCache` | `seen` sống theo **lần nối** (chỉ `linkDown` xoá), không theo kế hoạch. |
| 2 | `HalEventCache.hit` (F6-TRUST) | Không id nào được tin khi device chưa đẩy ≥ 1 sự kiện tới helper (giữ nguyên). Device đã chứng minh ⇒ id có sự kiện riêng / `PUSH_PROVEN` hạn **60 s**; id còn lại hạn **30 s** (`inferredMs`). |
| 3 | `CarDataAdapter` | `readControls` nhịp thường bỏ nút `controlCovered` — **một** luật với `fastNeeded` (nút đọc khác tham số với datum ⇒ không coi là phủ). Lợi CPU, không đổi số HAL. |
| 4 | `SpeedShare` (`:core`) + `SpeedProvider.mps` | Nhịp tim dẫn đường dùng lại lượt đọc **thật** gần nhất ≤ 1 s (của chính nó hoặc nhịp nhanh). `mpsOrNull` và nhịp nhanh luôn gọi binder. |
| 5 | `HalEventMap.GETTERS` | Đèn tĩnh `getLightStatus` #1/#2/#3/#6/#7 (`light/BYDAutoLightDevice.java:216-250`, `get(hằng)` không biến đổi). Xi-nhan #4/#5 giữ poll (nháy ~2,5 Hz ⇒ một lượt phát `CarStatus` mỗi nháy). Light chưa đẩy tới helper ⇒ vẫn poll. |
| 6 | `HalPollReport` + `CarDataAdapter` | Mỗi nhịp chậm tính dòng `poll còn lại: nhanh=… · không-sự-kiện=… · chưa-chứng-minh=… · helper-vắng=… · sự-kiện=N`, log `KachiHalEvent` **khi đổi** (không xét hạn ⇒ không nhảy mỗi phút). |

Tốc độ/số **không** vào kế hoạch sự kiện [ĐO nguồn]: `getCurrentSpeed` = `getDouble(SPEED_AUTO_SPEED)` (`speed/BYDAutoSpeedDevice.java:96-99`)
và device **có** phát id ấy (`postEvent :161-165`), nhưng cố ý không log riêng id ấy ⇒ [SUY mạnh] luồng dày; `getCurrentGear` ghép ba tín
hiệu (`gearbox/BYDAutoGearboxDevice.java:126-148`). Lý do giữ poll: `EventCachedGateway` phục vụ **mọi** người đọc một khoá đã vào kế hoạch,
kể cả cổng cốp `CtlSafetyPolicy.readMotion`; và chưa đo tần suất sự kiện trên xe (CLAUDE.md §14). Cổng cốp [ĐO test]: `readFresh` →
`readFast` → binder mỗi lần bấm; mẫu chung chỉ được **ghi** trên đường ấy. Giới hạn có sẵn, không đổi: màn đang bày tốc độ ⇒ cổng dùng ảnh
chụp của nhịp nhanh (≤ 1 s, `refreshForRead`), nhịp ấy vẫn chạy mỗi 1 s vì `needsFast`.

Nhịp cửa 3 s: giữ nguyên luật có sẵn (`bodyNeeded` nghỉ khi mọi cửa `covered`) — nay Bodywork chỉ cần **một** sự kiện bất kỳ.

Rủi ro mới [SUY]: id im lặng trên bus nhưng giá trị đổi (chưa thấy ca nào) ⇒ ô lệch tối đa **30 s** (trước: 1 s nhịp nhanh / 10 s nhịp
chậm). Chốt trên xe ở E4.

### E3. Số dự kiến [ĐO off-car `HalLoadBudgetTest` 319 — gateway trả lời mọi getter, mỗi device đúng MỘT sự kiện rồi im]

| Màn | Poll (= sự kiện chưa chứng minh) | Sự kiện đã chứng minh |
|---|---|---|
| QA2 (xe 3D + lốp + kính, thanh nút ẩn) | 550 | **87** |
| Mặc định (3 ô) | 676 | **93** |

Phần còn lại QA2 theo device (lượt/phút): Light 25 (xi-nhan 12 + đối soát) · Instrument 17 · Bodywork 16 · Ac 7 · Tyre 7 · PM2p5 6 ·
Setting 6 · Statistic 3. Gần như toàn bộ là **đối soát** (một lượt mỗi khoá mỗi 30/60 s). Tốc độ ngoài bảng: nhịp tim dẫn đường
≈ 150 → ≤ 60 lượt/phút khi đang dẫn đường [SUY từ mã, chưa đo xe].

Test: `HalPollCoveredTest` (8 ca) + `HalLoadBudgetTest` 319. Thử ĐỎ [ĐO]: bỏ lọc `controlCovered` ở `readControls` ⇒ `nhip thuong bo nut…`
đỏ · thu lại `seen` theo kế hoạch ⇒ `bang chung su kien song qua thu hep…` đỏ · `hit` về luật cũ ⇒ `device da chung minh…` + 319 đỏ ·
nhịp nhanh đọc tốc độ từ mẫu chung ⇒ `cong coc doc toc do tuoi…` đỏ.

### E4. Chốt trên xe (🚗)

`KachiPerf HAL đọc` trên màn 3D < 150/phút sau khi các device có `sự kiện ĐẦU` · dòng `poll còn lại` · Light có `sự kiện ĐẦU` không ·
đổi chế độ lấy gió trên màn AC gốc rồi xem ô Kachi đổi trong bao lâu (có sự kiện ⇒ ~0,2 s; im lặng ⇒ ≤ 30 s).

---

## F. Sửa D — `R29-RESOLVE-STORM` (chưa lên xe, chưa bump bản)

### F1. Chốt kẻ kích [ĐO emulator-5556, clusternav10, vehicleTest 3.1.8 + bộ đếm kẻ kích]

Bộ đếm mới (ship theo app, `ContrastLedger.Trigger`, in cuối dòng `KachiPerf`): `kích tô-mới · tô-bảng-cũ · tô-chưa-họ · tô-gộp ·
sự-kiện · chấm-lại · ép` + `hoãn-chưa-đo`. Dựng lại ca xe: QA2, 3D ô 0, Sáng + `wall-10` (sáng nhất), xe giả tốc độ đổi mỗi giây
(`--ei speed 60..66` — nhịp đổ số chỉ chạy khi `CarStatus` đổi, `WorkspaceView.renderInternal`), nhiệt lốp chỉ bánh trước phải
(`--es tyre_t fr`, tuỳ chọn mới của `DummyCarReceiver` — xe thật: nhiệt lốp đến lẻ tẻ, §C). Tốc độ đứng yên ⇒ 0 lượt/phút (không bão).

| Bản 3.1.8 (trước) | mặt/phút | tô-chưa-đo | sự-kiện | đổi-họ |
|---|---|---|---|---|
| Sáng, tốc độ đổi | ~240 | ~180 | ~58 | 0 |
| Tối, tốc độ đổi | ~228 | ~171 | ~57 | 0 |

Hai kẻ kích, cả hai trên mặt gốc `Car3dWidgetView` (vết `DBG decide` tạm, đã gỡ):
1. **`tô-chưa-đo` (≈ 75 %)** — đúng D2: `Car3dHeroLayer.fill` tô nhãn nhiệt lốp `GONE` mỗi nhịp; `mark.ground == null` ⇒ lượt chọn đầy đủ.
2. **`sự-kiện` ~1/giây** — lỗi THẬT khác, chưa có trong D2: `measure`/`groundOf` khi ÉP bỏ qua `moved()` (`!force && !moved(..)`) nên
   ghi vùng từ `loc` CŨ của view khác. Sau mỗi lượt chọn, `refresh` đo ép mọi nút đĩa (`measure(v, it, true)`) ⇒ cả 7 nút hàng chân 3D
   nhận cùng vùng `[917,983][989,1055]` ⇒ lượt layout kế (mỗi lần đổ số) thấy "dời" ⇒ giữ ⇒ lặng 200 ms ⇒ đo lại (nền khác) ⇒ chọn họ ⇒
   lại ghi vùng sai — vòng lặp vô tận theo nhịp đổ số.

### F2. Sửa (một chỗ, `AdaptiveInk`, chung mọi phần tử)
- **Cổng thuần `InkSolveGate`** (`:core`): lời tô có nền + mặt tươi + đã có họ + đã trong lượt chọn ⇒ chỉ tra/tô; thiếu mà phần tử / mặt
  chứa / mặt gốc **chưa đo được** (chưa gắn · `GONE` · 0×0) ⇒ chỉ giữ dấu (`hoãn-chưa-đo`), lượt giải đến từ `OnLayoutChangeListener`
  của mặt khi nó có cỡ (AOSP r47 `View.java:15200` bỏ `GONE` ⇒ `requestLayout`, `:24465` cờ lan lên cha, `:24563` `PFLAG_LAYOUT_REQUIRED`,
  `:21926` listener chạy); thiếu mà đo được ⇒ MỘT lượt qua `InkCoalescer` (lời đầu mỗi mặt gốc mỗi lượt vòng lặp chạy ngay — giữ màu trả về
  cho chip thanh trên; lời sau dồn vào một hẹn).
- **Lời tô lặp = no-op**: cùng nền (cùng đối tượng) + vai + họ ⇒ dùng lại `Resolution`; view đang hiện đúng mực ⇒ không `setTextColor`/
  `setColorFilter` (0 invalidate, 0 cấp phát).
- **Vùng đo đọc chỗ thật**: `measure`/`groundOf` gọi `getLocationInWindow` ngay trước khi ghi vùng (gỡ kẻ kích 2).
- **`dưới sàn:`** chỉ log khi (mặt, họ, đạt-sàn) đổi, vẫn qua `LogLineThrottle`.
- Chỗ gọi theo nhịp (`Car3dHeroLayer`, `Car3dFootRow`, `GroupTileViews`, `KachiWidgetType`, ô nút ẩn) không phải sửa: đều đi qua cổng.

### F3. Sau sửa [ĐO cùng kịch bản, cửa sổ 180 s/120 s]

| | mặt/phút | hoãn-chưa-đo/phút | luồng chính | tiến trình | logcat pid | log app (`KachiPerf`) | dòng `KachiContrast` |
|---|---|---|---|---|---|---|---|
| Trước, Sáng, tốc độ đổi | ~240 | — | 1,7 % | 3,3 % | 12,8 KB/phút | 7,3–8,4 KB/phút | 21 / 3 phút |
| Sau, Sáng, tốc độ đổi | **0** | ~180 | 1,1 % | 2,4 % | 10,7 KB/phút | 5,4–7,0 KB/phút | **0** |
| Sau, Tối, tốc độ đổi | **0** | ~170 | — | — | — | 5,6–7,8 KB/phút | 0 |
| Sau, đứng yên | 0 | 0 | 0,2 % | 1,9 % | 1,7 KB/phút | — | 0 |

- Lượt chọn chỉ còn ở khởi động (~53 tổng, đều có nguồn: ép/sự-kiện/chấm-lại) — đúng mục tiêu "chỉ khi có thay đổi thật".
- Màu nhãn nhiệt lốp vừa hiện lại (GONE → VISIBLE) [ĐO pixel ảnh chụp]: trùng từng giá trị min/max với bản trước ⇒ không hồi quy hiển thị.
- GC: máy ảo không in dòng GC cho tiến trình (0 cả trước lẫn sau) ⇒ **[CHƯA ĐO]** churn; CPU máy ảo rất nhỏ so với xe nên 25–75 % trên xe
  phải đo lại bằng chuyến kế (🚗: dòng `KachiPerf` — `mặt` phải ≈ `đổi-họ`, `hoãn-chưa-đo` cho thấy cổng đang chặn bao nhiêu).
- Bài: `InkSolveGateTest` (6 — 60 lời tô GONE ⇒ 0 lượt · cùng vai/nền ⇒ 0 · một layout ⇒ 1 · 8 phần tử mới ⇒ 1 · bộ gộp · kẻ kích),
  `AdaptiveInkSourceContractTest` (+ nối cổng/gộp/đếm + `getLocationInWindow` trước khi ghi vùng).

## G. Sửa A — `VOICE-WAKE-CPU-BUDGET` + 3 việc voice kèm theo (chưa lên xe, chưa bump bản)

### G1. Cơ chế đã sửa (generic, tầng thi hành)

| # | Tầng | Thay đổi | Tệp |
|---|---|---|---|
| 1 | Ngân sách | Trần tầng 4 đếm **ms CPU đo được** (`Process.getElapsedCpuTime()` quanh mỗi `feed`/`observe`, gồm luồng onnx), xô token `DUTY = 0.25` một lõi, đầy `DUTY × 16 s = 4 000 ms`; nợ ⇒ `Frame.OBSERVE` (engine gom khung, không suy diễn). Chặn trên: `Σ CPU ≤ 4 000 + 0,25·T + 1 lượt` trên MỌI khoảng `T`. Thay trần cũ 40 khung / 10 s. | `:core` `VoiceWakeCpuBudget.kt` · `VoiceWakeController.kt` · `:app` `VoiceWakeListener.kt` |
| 2 | Cổng trước ASR | Silero VAD **riêng** (`Vad.compute` 512 mẫu, 1 luồng — không dùng bộ gom đoạn vì `AcceptWaveform` chép cả đoạn đang mở mỗi lượt, `csrc/voice-activity-detector.cc` v1.13.8). Giải mã chỉ khi có tiếng người trong 1 200 ms gần nhất. VAD hỏng ⇒ cổng MỞ (về hành vi cũ, ngân sách vẫn chặn). Asset là Silero **v4** (`h`/`c`, `silero-vad-model.cc:208`). | `:core` `VoiceWakeSpeechGate.kt` · `:app` `VoiceWakeVad.kt` · `VoiceWakeAsr.kt` |
| 3 | Đo trên xe | Một dòng `WakeListen: ngan_sach cpu_ms=… du=… giai_ma=… bo_khong_tieng_nguoi=…` mỗi 60 s. | `VoiceWakeListener.logBudget` |

Đã chọn VAD thay "N khung tiếng liên tiếp" (nhạc là liên tục ⇒ không cắt gì) và thay "năng lượng dải giọng" (nhạc có lời cùng dải). Hold 700 ms → **1 200 ms** sau đo G2 (700 ms mất 4/57 ca "ka chi"/"ca chi" mà bản cũ bắt ở lượt giải mã THỨ HAI sau câu).

### G2. Harness máy ảo [ĐO emulator-5554, clusternav10, bản vehicleTest 3.1.8 cây làm việc]

Lệnh mới `kws --es name asr|asr-base [--ei v <ms>]`: đẩy WAV khung 100 ms qua **đúng** `VoiceWakeController` → `feed/observe` → `onInferenceCost`, đồng hồ = thời lượng tiếng. `asr-base` = trước bản vá (cổng RMS + trần 40 khung/10 s dựng lại tại chỗ). `v` = giá giả mỗi lượt giải mã — máy ảo nhanh hơn xe nhiều lần, nên muốn thấy ngân sách cắt ở giá xe thì bơm giá xe 1 350 ms [ĐO xe §A].
Bộ WAV: TTS `say -v Linh` 19 câu gọi + 15 câu thường × 3 tốc độ; nhạc **tổng hợp** (hợp âm + bass + trống, không bản quyền) không lời / có lời (lời TTS); giọng ≈ +3,5 dB trên nhạc; 3 tệp 45 s.

| Nhóm | n | trước (`asr-base`) | sau, giá máy ảo | sau, giá xe 1 350 ms |
|---|---|---|---|---|
| câu gọi, cabin yên | 57 | 35 (61,4 %) | 35 (61,4 %) | 35 (61,4 %) |
| câu thường (nổ nhầm) | 45 | 0 | 0 | 0 |
| câu gọi + nhạc không lời | 57 | 48 (84,2 %) · 4,44 lượt/tệp | 48 (84,2 %) · 2,33 lượt/tệp | 48 (84,2 %) |
| câu gọi + nhạc có lời | 57 | 33 (57,9 %) | 33 (57,9 %) | **23 (40,4 %)** |
| 45 s nhạc không lời — lượt giải mã | 1 | 40 | **0** (90 lượt bị cổng bỏ) | 0 |
| 45 s nhạc có lời — lượt giải mã | 1 | 40 (trần khung cũ) | 83 | **12** |

Đường cong ngân sách cho nhạc có lời (giả lập bằng `v = 1350 × 0,25 / DUTY`, xô 16 s): DUTY 0,25 → 40,4 % · DUTY 0,5 → 57,9 % (không mất) · DUTY 1,0 → 57,9 %.
⚠ Tệp câu gọi ngắn (3,6 s) bắt đầu với xô ĐẦY; dưới nhạc có lời kéo dài thì xô đã cạn lúc người lái gọi ⇒ tỉ lệ thật của ca này [SUY] **thấp hơn** 40 % ở DUTY 0,25.

### G3. CPU `:wake` khi nhạc phát — ước tính xe [SUY từ số lượt × giá xe 1 350 ms/lượt, §A]

| Ca | Trước [ĐO §A] | Sau |
|---|---|---|
| nhạc không lời | ~105 % TB, đỉnh 263 % | **≈ 0 % giải mã** (0 lượt/45 s) + VAD: máy ảo 93–178 ms CPU / 45 s ≈ 0,2–0,4 %; xe [CHƯA BIẾT], ước 1–2 % |
| nhạc có lời | ~105 % TB, đỉnh 263 % | 12 lượt/45 s × 1 350 = 16,2 s / 45 s ≈ **36 %** gồm xô đầu; dài hạn **≤ 25 %** (chặn trên chứng minh ở test) |
| cabin yên | thấp (cổng RMS) | không đổi |

Đo lại trên xe: cùng `top -d 5 -n 13` + đếm `sherpa ra`/phút khi nhạc phát + dòng `ngan_sach` mỗi phút.

### G4. Ba việc voice kèm theo — owner thử Hey Kachi 18:37 [ĐO log xe `logcat-175828`]

| ID | Triệu chứng [ĐO] | Gốc | Sửa |
|---|---|---|---|
| `VOICE-WAKE-HANDOFF-GAP` | `WAKE khớp` 32.650 → micro phiên mở 35.513: **2,9 s không ai nghe** ⇒ chỉ còn "trên youtube" ⇒ NO_VERB | bộ nghe nhả micro khi khớp; phiên mở micro MỚI sau khi dựng xong | bộ nghe **giữ** micro, báo `onWake` ngay, ghi tiếp vào đệm (≤ 6 s); phiên xin micro ⇒ nhận **nguyên `AudioRecord` đang ghi** + đệm (`VoiceWakeHandoff`, `VoiceCaptureDevice.adopt`), không mở lại, không bíp. Không ai lấy trong 3 s ⇒ tự đóng. Nguồn ưu tiên khác MIC ⇒ đóng và mở như cũ. |
| `VOICE-SESSION-READY-CACHE` | `sẵn sàng nghe sau 2204 ms`, 2,2 s nằm trước dòng `Aurora Store trùng` (bảng gọi app) | [SUY] `loadLabel` ~40 app dựng lại MỖI phiên | `VoiceLabelMemo` theo chữ ký rẻ (gói·activity·APK·mã nhãn·locale); cảnh báo trùng chỉ in khi dựng lại; dòng mốc giờ `bảng app:` + `dựng nghe:`. [ĐO máy ảo] phiên đầu 335 ms (nhãn 119 ms) → phiên sau **9 ms**. Xe: [CHƯA BIẾT] tới buổi đo — dòng mốc giờ mới sẽ chốt phần 2,2 s nằm ở bước nào. |
| `VOICE-MEDIA-QUERY-POSSESSIVE` | "mở nhạc của đen trên youtube" ⇒ `Media(QUERY "của đen")` | thân câu đưa nguyên văn | `VoiceTailClause.queryText`: bỏ "của/bởi [ca sĩ]" ở ĐẦU thân khi còn tên phía sau; dùng cho cả hai chỗ dựng `Media(QUERY)`. |

Phát hiện khi làm (owner 16/10: sửa luôn ở 3.1.9 — xem G6): `VOICE-WAKE-TAIL-UNDECODED` — cabin yên chỉ 61,4 % câu gọi nổ ở harness (cả trước lẫn sau): khung im bị tầng 3 bỏ ⇒ đuôi câu gọi không bao giờ đủ 500 ms để được giải mã lần nữa (1,44 lượt/tệp). [SUY] khớp phàn nàn *"nói 2–3 lần mới lên"*.

Lựa chọn owner (không làm): recognizer riêng 1–2 luồng cho wake ≈ +74 MB RAM [SUY, cỡ mô hình]; giảm thời gian tường mỗi lượt nhưng không giảm CPU tổng — ngân sách đã chặn CPU, nên chỉ đáng làm nếu độ trễ nổ dưới nhạc là vấn đề.

### G6. `VOICE-WAKE-TAIL-UNDECODED` — lượt giải mã cuối theo sườn tiếng→im (owner 16/10, DUTY giữ 0,25)

Cơ chế (`VoiceWakeController.tail`, chung mọi engine): sau khung có tiếng cuối, [`DEFAULT_TAIL_MS` = 800 ms] im lặng
vẫn tới engine dạng `OBSERVE` (gom, không suy diễn), rồi đúng MỘT `Frame.FINISH` ⇒ `WakeEngine.finish()` giải mã cửa
sổ một lần (ASR: bỏ nếu < 200 ms tiếng mới từ lượt trước). Vẫn qua ngân sách (nợ ⇒ không FINISH). Bài canh:
`VoiceWakeControllerTest` (đuôi → một FINISH · đoạn mới → FINISH mới · không tiếng/nợ ⇒ không) — red-proof 1 đỏ.

[ĐO emulator-5554, cùng bộ WAV G2]

| Nhóm | n | trước (G2, chưa đuôi) | đuôi 600 ms | **đuôi 800 ms (ship)** | 800 ms, giá xe |
|---|---|---|---|---|---|
| câu gọi, cabin yên | 57 | 35 (61,4 %) · 1,44 lượt | 46 (80,7 %) · 1,82 | **49 (86,0 %)** · 1,82 | 49 (86,0 %) |
| câu thường — nổ nhầm | 45 | 0 · 1,47 lượt | 0 · 2,47 | **0** · 2,47 | 0 |
| câu gọi + nhạc không lời | 57 | 48 (84,2 %) · 2,33 | 48 (84,2 %) | 48 (84,2 %) · 2,33 | 48 (84,2 %) |
| câu gọi + nhạc có lời | 57 | 33 (57,9 %) | 33 (57,9 %) | 33 (57,9 %) · 5,16 | 23 (40,4 %) |
| 45 s nhạc không lời — lượt | 1 | 0 | 0 | 0 | 0 |
| 45 s nhạc có lời — lượt | 1 | 83 | 83 | 78 | 11 |

Chi phí: +1 lượt giải mã mỗi đoạn tiếng (câu thường 1,47 → 2,47; câu gọi +0,38 vì nhiều câu đã nổ trước sườn) —
dưới nhạc liên tục sườn hiếm khi xảy ra nên số lượt không tăng. Tổng bộ test: **10 705 / 0** (GREEN).

### G5. Kiểm

`:core:test :app:testDebugUnitTest :app:testVehicleTestUnitTest` — **10 703 test / 0 lỗi** (`scripts/count-tests.py` GREEN). Bài mới: `VoiceWakeCpuBudgetTest` (5) · `VoiceWakeSpeechGateTest` (4) · `VoiceWakeControllerTest` tầng 4 viết lại (3) · `VoiceLabelMemoTest` (2) · `VoiceMediaQueryPossessiveTest` (2) · `VoiceWakeHandoffWiringContractTest` (3). Red-proof: tắt ngân sách + cổng ⇒ 8/18 đỏ; tắt memo + `queryText` ⇒ 2/4 đỏ.
🚗 chưa kiểm được off-car: đường giao micro chỉ có trên micro thật (máy ảo không có câu gọi qua micro) — kiểm bằng log `KachiWakeHandoff: giữ micro …` + `KachiVoiceMic: micro nhận từ bộ nghe …` ở buổi xe sau.

## Việc tiếp theo đề xuất (cho backlog — chưa thêm)

1. `WAKE-ASR-CPU-BUDGET` — ngân sách suy diễn theo thời gian (A5.1) + sửa KDoc; cân nhắc A5.2/A5.3. **Ưu tiên cao** (2,6 lõi khi có nhạc). → **đã làm** dưới tên `VOICE-WAKE-CPU-BUDGET` (§G; A5.3 để owner chọn).
2. `HAL-EVENTS-CONTROLS-FAST` — `readControls` tôn trọng `covered` ở nhịp thường (B'), −~250 lượt HAL/phút. → **đã làm** dưới tên `HAL-POLL-COVERED` (§E; gốc thật là bằng chứng sự kiện, không riêng `readControls`).
3. `INK-RESOLVE-UNMEASURED` — D3: không chọn lại họ cho phần tử chưa đo được + gộp một lượt/mặt/khung + bộ đếm kẻ kích.
3b. `SPEED-READ-MAIN-THREAD` — đưa `SpeedProvider.mps()` của nhịp tim dẫn đường ra khỏi luồng chính hoặc đọc từ cache tốc độ (B4). → phần "đọc từ mẫu chung ≤ 1 s" **đã làm** (§E2 #4); lượt binder còn lại (≤ 1/s) vẫn trên luồng chính.
4. (nhỏ) `CAR3D-BLINK-NO-SETTLE` — B3.
5. Đo lại luồng chính khi có quyền lấy mẫu (bản debuggable cùng chữ ký, `simpleperf --app`) để chia 11,7 % luồng chính.
