# 4 — MẢNG SYSTEM · bài test QUYẾT ĐỊNH cho phần hệ thống chưa verify trên xe

> **Trạng thái**: Current · **Ngày**: 2026-09-19 · **Bản đích**: **1.79 (80)** (xe đang 1.76 ⇒ OTA trước)
> **Mục đích**: biến 8 mục hệ thống đang `🚗 chờ xe` thành [ĐO] **trong MỘT buổi**. Mỗi mục có **≥2 đường đo**
> (A → B → C) để đường A hỏng thì thử tiếp NGAY, không phải hẹn buổi thứ hai.
> **Phạm vi**: K5 · K8 · P7/S5 · F4 · W5 · T-BRIDGE · P8 · U8a. (Voice/HAL/cast ở các doc `1-`…`3-`, `5-`.)
>
> Mức bằng chứng (CLAUDE.md §2): **[ĐO source]** = đọc được trong mã/log của repo, kèm `file:line` ·
> **[SUY]** = suy từ mã, chưa chạy trên máy nào · **[CHƯA BIẾT]** = không có dữ liệu, chính là thứ buổi này đo.
> **Không mục nào dưới đây được viết `DONE` mà không có output dán kèm.**

---

## 0. TRƯỚC KHI CHẠM MỤC NÀO (15 phút, làm đủ — nếu không thì 8 mục sau đều vô nghĩa)

### 0.1 Bản trên xe phải là 1.79 (80)

```bash
$A shell "dumpsys package com.byd.launcher | grep -E 'versionName|versionCode'"
```

- `versionName=1.79` + `versionCode=80` ⇒ đi tiếp.
- Thấp hơn ⇒ **OTA trước** (`scripts/vehicle/kachi/40-ota.sh`). Lý do không bỏ qua được: 1.79 đổi **ba thứ đo
  được ở đây** — Piper ra tiến trình `:tts` (đổi cách đọc RSS/CPU, §1), overlay ẩn thanh hệ thống (§8), và
  `NavConnect.GRANT_TIMEOUT_MS` 9s→20s của 1.78 (điều kiện để phím sống, §5).

### 0.2 Kênh đo — thang 4 đường, thử theo thứ tự

[ĐO 2026-09-16/18, `docs/diagnostics/adb-car-tunnel-macos.md` §1] `nc` thông tới `<ip-xe>:5555` mà `adb`/`python3`
báo `No route to host` ⇒ **macOS Privacy › Local Network** chưa cấp cho tiến trình cha, **không phải lỗi xe**.

| Đường | Lệnh | Khi nào dùng |
|---|---|---|
| **A** — adb thẳng | `adb connect <ip-xe>:5555` | sau khi bật Local Network cho terminal (sửa gốc, một lần) |
| **B** — cầu `nc` về loopback | `mkfifo /tmp/kachi-adb/adbfifo`; `(nc -l 127.0.0.1 15555 < f \| nc <ip-xe> 5555 > f) &`; `adb connect 127.0.0.1:15555` | A hỏng. **Đã dùng thật 2 lượt xe** (doc trên §3) |
| **C** — client adb thô pure-python | `python3 /tmp/adb_raw.py <ip-xe> 5555 '<lệnh shell>'` | A+B hỏng (CNXN+AUTH ký `~/.android/adbkey`). [ĐO 18-09] đường DUY NHẤT vào được hôm đó |
| **D** — app tự chụp, không adb | `DiagActivity` · bridge `voice_dump` · `kachi-logs/` kéo qua USB/thẻ | đang cắm CarPlay/AA (đầu xe **tắt Wi-Fi**) |

Mọi lệnh dưới đây viết `$A` = `adb -s <target>` của đường đang dùng.

⚠ **Nếu adb wireless chết hẳn** — chạy **Test A của BUG2** trước khi kết luận (`docs/diagnostics/oncar-adb-wireless-broken-after-kachi-2026-09-18.md` §3):
`am force-stop com.byd.launcher` → thử `adb connect` lại. Sống lại ⇒ Kachi đang **chiếm adbd** (H1/H2/H3);
vẫn chết ⇒ có thứ **bền** bị đổi (H4). **Đây là phép thử quyết định đang nợ, chạy luôn vì nó tốn 1 phút** và nó
gate cả 8 mục.

### 0.3 Ba tiền đề bật sẵn — một lần, đầu buổi

```bash
# (1) Cầu kiểm thử: Kachi › Cài đặt › Hệ thống & quyền › Nâng cao › "Chế độ kiểm thử qua adb"  (TAY, không có
#     đường bật bằng broadcast — có chủ ý). Tự tắt sau 60 phút ⇒ buổi dài phải bật lại.
$A shell "am broadcast -n com.byd.launcher/com.byd.clusternav.launcher.testbridge.KachiTestBridge -a com.byd.launcher.TEST --es cmd state"
#   → có JSON            = cầu SỐNG + cổng MỞ
#   → "error":"test_mode_off" = cầu sống, công tắc CHƯA bật
#   → không kết quả nào  = bản trên xe chưa có cầu (hoặc §6 fail — xem đó)

# (2) Dịch vụ trợ năng phải BOUND, không chỉ ENABLED — điều kiện của §5 và của phím-thoại
$A shell "settings get secure enabled_accessibility_services"
$A shell "settings get secure accessibility_enabled"
$A shell "dumpsys accessibility | grep -A6 -iE 'Bound services|Binding services'"
```

[ĐO xe 2026-09-18, `oncar-voice-number-and-voicekey-bind-2026-09-18.md` §4] `NavAccessibilityService` **ENABLED
nhưng KHÔNG Bound** (chỉ StatusBar Bound) ⇒ `onKeyEvent` không bắt ⇒ **phím chết, và §5 đo ra kết quả SAI ÂM**.
Chữa live đã proven: toggle danh sách trực tiếp (bỏ Kachi → thêm lại) ⇒ Bound NGAY, `capabilities=9` gồm
`FILTER_KEY_EVENTS`. Đường trong app: *Cài đặt › Phím vô-lăng › **Kiểm tra / Sửa ngay*** (1.78 nới timeout
9s→20s, `app/.../NavConnect.kt:49`) — **[SUY] chưa ai đo sau khi nới**, chính §5 đo hộ.

```bash
# (3) Mốc lần nổ máy — in ra TRƯỚC, dùng lại ở §3 và §6
$A shell "cat /proc/sys/kernel/random/boot_id; uptime"
```
[ĐO source `app/.../testbridge/TestBridgeStore.kt:56`] app dùng **đúng tệp này** làm danh tính lần nổ máy ⇒ chép
giá trị ra giấy là có ngay **chứng cứ độc lập** rằng lượt reboot ở §3 là reboot THẬT.

### 0.4 Hai thứ chụp trước khi chạm

```bash
$A shell "cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME"
$A shell "am broadcast -n com.byd.launcher/com.byd.clusternav.launcher.testbridge.KachiTestBridge -a com.byd.launcher.TEST --es cmd prefs --es file kachi_workspace" \
  | tr ',' '\n' | grep -iE "keep_home_on_boot|home_chosen|launcher_autostart"
```
Không có hai dòng này thì cuối buổi **không ai chứng minh được HOME đã đổi** (§3).

---

## 1. K5 — tải hệ thống sau bản 1.67 (HAL/phút · CPU · RSS · log)

**Mục tiêu**: 4 con số + 1 lượt kiểm mắt. Tiêu chí đạt (nguyên văn `perf-profile-2026-09-16.md` §6):
**HAL đọc < 150/phút** · **`no permission` < 20/phút** · **CPU idle < 2 % (8 lõi)** · **log < 20 KB/phút** ·
**KHÔNG ô nào mới thành `—`**.

**Mốc để so** [ĐO xe 1.66, cùng doc §0]: HAL **510/phút** · no-permission **229/phút** · log **79 KB/phút** ·
RSS **537 MB** · CPU idle **6,6 %**.

### Tiền đề phải biết trước khi đọc số (cả ba đều [ĐO source], cả ba đều làm sai phép đo nếu không biết)

1. **Dòng `KachiPerf` chỉ in khi màn chính ĐANG RESUMED.** `app/.../KachiHomeActivity.kt:181-190` (tick 10 s in
   `Log.i("KachiPerf", …)`), `:438` `handler.post(tick)` trong `onResume`, `:457` `removeCallbacks(tick)` trong
   `onPause`. ⇒ mở một app **toàn màn** che Kachi thì **không có dòng nào**, và đó KHÔNG phải "tải = 0".
2. **Vòng poll HAL scope theo `STARTED`, không theo `RESUMED`** — `app/.../KachiHomeWiring.kt:383,395-400`
   (`repeatOnLifecycle(STARTED)` → `carStatusRepository.start()/stop()`), nhịp `1 s` / `10 s`
   (`core/.../CarStatusRepository.kt:51-52`). ⇒ có **một khe**: màn PAUSED-mà-còn-STARTED (hộp thoại chồng lên)
   thì **poll vẫn chạy mà dòng perf đã tắt**. [SUY] đây là ca tải cao nhất và là ca không đo được bằng dòng perf
   ⇒ phải đo bằng `top` (đường B).
3. **Từ 1.79 có HAI tiến trình** — `app/src/main/AndroidManifest.xml:205` `android:process=":tts"`;
   `KachiApplication.kt:23,44-45` `isTtsProcess()` early-return để `:tts` **không** nạp model NGHE 74 MB.
   ⇒ `top | grep byd.launcher` nay khớp **2 dòng**; `dumpsys meminfo com.byd.launcher` **không** gộp `:tts`.
   Đọc gộp một số là số bịa.

### Đường A — bộ đếm ship sẵn (rẻ nhất, nên chạy trước)

```bash
# Màn chính Kachi, bố cục MẶC ĐỊNH, KHÔNG chạm gì, để yên 5 phút. Dòng đầu ra ở ~60-70 s
# (dueLine trả null lần gọi đầu để đóng mốc — core/.../KachiPerf.kt).
$A shell "logcat -d -v time -s KachiPerf" | tail -10
```
Dạng dòng (`KachiPerf.dueLine`): `cửa sổ 60s · HAL đọc=N/phút · bỏ-không-hiện=N · bỏ-xe-không-có=N · shell=N/phút · log=N KB/phút`

### Đường B — `top` + `meminfo`, không phụ thuộc dòng perf

```bash
$A shell "top -b -n 2 -d 5 | grep -E 'byd.launcher|load average'"     # 2 dòng launcher: :app và :tts
$A shell "dumpsys meminfo com.byd.launcher     | grep -E 'TOTAL PSS|Native Heap'"
$A shell "dumpsys meminfo com.byd.launcher:tts | grep -E 'TOTAL PSS|Native Heap'"
```
⚠ `load average` phải chép kèm: [ĐO 18-09] xe từng ở **load 10–17** vì GMaps-in-slot 61 % + BYD `cdr` 39 % +
`surfaceflinger` 34 % + VietMap 18 %, trong khi **Kachi chỉ 2 %**. Một số CPU của Kachi không kèm load toàn hệ
là số không đọc được.

### Đường C — log app tự ghi (đối chiếu chéo với `LOG_BYTES`)

```bash
$A shell "ls -l /sdcard/Android/data/com.byd.launcher/files/kachi-logs/"   # đo 2 lần cách 5 phút → Δ byte
$A pull /sdcard/Android/data/com.byd.launcher/files/kachi-logs/ ./kachi-logs/
grep -c "D/BYDAuto"                kachi-logs/usage-*.log
grep -c "no permission to use the" kachi-logs/usage-*.log
```
Đường dẫn + tên tệp: `app/.../KachiLog.kt:14-15,29,38,66` (`usage-<ts>.log`, xả theo `FLUSH_EVERY_MS=2000`, `:118`).

### Đường D — kiểm mắt (KHÔNG bỏ được: cổng H1 là cổng *hiệu năng* nhưng nó cắt đường *dữ liệu*)

Lần lượt đặt lên màn: ô **Tốc độ** (bật lại vòng nhanh) · ô **Áp suất lốp** · nhóm **Kính** · một **chip** bất kỳ
ở thanh trên. Mỗi cái phải ra số **≤ 10 s** sau khi lên màn.

### PASS / FAIL

| Chỉ số | PASS | FAIL ⇒ làm gì ngay trong buổi |
|---|---|---|
| HAL đọc/phút | < 150 | chép cả 3 cột `bỏ-*`; `bỏ-không-hiện` ≈ 0 ⇒ cổng H1 **không ăn** trên xe ⇒ chụp `state` xem màn đang bày gì |
| `no permission`/phút | < 20 | ⇒ rejection-cache không dính; chép 5 dòng `W/AbsBYDAutoDevice` đầu (tên device + feature) |
| CPU idle | < 2 % (8 lõi), **kèm load average** | ≥ 2 % ⇒ chạy `top -H -p <pid>` lấy thread nóng (chỉ đo được lúc này) |
| RSS | ghi cả `:app` và `:tts` | `:tts` > ~100 MB ⇒ nó đang nạp model NGHE ⇒ `isTtsProcess()` hỏng ⇒ **[P0]**, chép `logcat -s KachiVoiceRec` |
| log KB/phút | < 20 | ⇒ chép tên 3 tag ồn nhất: `logcat -d \| awk '{print $5}' \| sort \| uniq -c \| sort -rn \| head` |
| Kiểm mắt | 4/4 ra số ≤ 10 s | có ô thành `—` ⇒ **[P0] cổng H1 cắt mất dữ liệu**, chụp ảnh + ghi mã datum |

**Ghi vào**: `docs/diagnostics/perf-profile-2026-09-16.md` — thêm mục **§0b. [ĐO] xe bản 1.79** (bảng cùng cột với
§0 để so được trực tiếp) + đánh dấu backlog **K5** ✅/❌ kèm 4 con số.

---

## 2. K8 — ba nhánh PHỤC HỒI mà máy ảo không dựng được

**Vì sao chỉ đo được ở đây** [ĐO source]: máy ảo không có HAL BYDAuto ⇒ *mọi* datum `null` ⇒ cả ba nhánh dưới
**không tồn tại** off-car; off-car chỉ khoá được bằng test thuần (đã làm).

### 2a. Hỏi bằng giọng một datum KHÔNG có trên màn ⇒ phải ra số THẬT

**Gốc** [ĐO source, `perf-profile-2026-09-16.md` §9.1]: `VoiceDispatcher.runRead` đọc `TelemetryReadout.of(id,
state().carStatus)` = **ảnh chụp của vòng poll** — đúng thứ cổng H1 vừa lọc. Bản vá: **ghim** datum vào nhu cầu
rồi đọc ngay một lượt (`Holder.withExtra` + `CarStatusRepository.refreshNow`), trần cứng **một** datum.

- **Chuẩn bị**: đặt màn về mặc định (pin · bụi · nhiệt độ ngoài) — **không** có ô Tốc độ.
- **Đường A (máy, đúng đường một cú chạm đi)**:
  ```bash
  # xe ĐANG LĂN BÁNH, tốc độ X1 → hỏi → đổi tốc độ sang X2 (chênh ≥ 15 km/h) → hỏi lại
  $A shell "am broadcast -n com.byd.launcher/com.byd.clusternav.launcher.testbridge.KachiTestBridge -a com.byd.launcher.TEST --es cmd say --es text 'tốc độ bao nhiêu'"
  $A shell "logcat -d -v time -s KachiVoice KachiTest" | tail -20
  ```
- **Đường B (miệng người)**: bấm mic → nói *"tốc độ bao nhiêu"*. Dùng khi cầu im. Cùng tiêu chí.
- **Đường C (tách nhiễu ASR khỏi nhiễu dữ liệu)**: nếu A/B ra số nhưng **nghi ASR nghe sai datum**, hỏi lại bằng
  ô: đặt ô Tốc độ lên màn, đọc số trên ô, gỡ ô ra, hỏi lại bằng giọng. Hai số phải cùng nguồn.
- **PASS**: hai lần hỏi ở hai tốc độ khác nhau ra **hai số khác nhau**, **khớp đồng hồ xe** (±2 km/h).
  **FAIL**: một con số **đứng im** giữa hai lần hỏi ⇒ [P1] đọc ảnh chụp cũ ⇒ chép cả hai lượt + tốc độ thật.
- ⚠ An toàn: có người thứ hai đọc/ghi; **người lái không gõ adb**.

### 2b. HAL lên MUỘN lúc nổ máy — tay cầm device phải bắt được

**Gốc** [ĐO source `app/.../BydHalGateway.kt:186,193`]: `MISS_TTL_MS = 30 s` (nhớ "không resolve được" 30 s rồi
thử lại) + `HIT_TTL_MS = 300 s` (lần TRÚNG cũng hết hạn, vì tiền đề *"getInstance là singleton"* chỉ ở mức
[SUY] — thân hàm trong stub SDK là `throw new RuntimeException("Stub!")`).

- **Đường A**: tắt máy hẳn → nổ lại → mở Kachi **trong ~5 s đầu** (Kachi là HOME nên nó tự lên) → bấm đồng hồ.
  **Mọi ô phải ra số ≤ 1 phút.**
- **Đường B (nếu A ra `—` mãi)**: phân biệt *HAL chưa lên* với *cache miss dính* — đợi đủ **35 s** rồi xem ô có
  tự ra số không (đó là `MISS_TTL_MS` hết hạn). Vẫn `—` ⇒ không phải cache, là HAL/quyền.
- **Đường C (đo bằng máy, không bằng mắt)**:
  ```bash
  $A shell "logcat -d -v time -s KachiPerf" | tail -5     # cột bỏ-xe-không-có phải TỤT về ~0 sau khi HAL lên
  $A shell "am broadcast -n com.byd.launcher/com.byd.clusternav.launcher.testbridge.KachiTestBridge -a com.byd.launcher.TEST --es cmd sweep --es op info"
  # → JSON trên thẻ: đếm datum có giá trị ở giây thứ 10 vs giây thứ 60 sau khi nổ máy
  ```
- **PASS**: ≤ 60 s mọi ô đang hiện có số. **FAIL**: còn `—` sau 60 s ⇒ chép `sweep` của cả hai mốc + `logcat -s Preflight`.

### 2c. Datum vắng rồi mới có ⇒ ⚠ BÀI TEST CŨ KHÔNG CHẠY ĐƯỢC NỮA — dùng bản thay

**[ĐO source] Đính chính**: `perf-profile-2026-09-16.md` §9.3 mục 7 ghi *"cắm sạc khi app đang chạy, ô **ETA sạc**
phải ra số ≤ 60 s"*. **Ô đó không còn tồn tại**: `(V) FEATURE-FILTER 2026-09-17` xoá TOÀN BỘ 8 mục sạc
(`core/.../TelemetryRegistry.kt:160-161` ghi đúng danh sách đã xoá: `is_charging` · `charge_power` ·
`charging_pct` · `charging_eta_hour` · `charging_eta_min` · …). ⇒ bài test như viết là **không thể chạy**.

Cơ chế cần đo vẫn còn nguyên và vẫn đáng đo: `HalAbsentCache` (`core/.../HalAbsentCache.kt:87,90,93` —
**3 lần trượt** rồi giãn nhịp **60 s → 600 s**) + `clear()` (`:83`) gọi qua `AppContainer.forgetCarDemand()` ở
`onStop`. Ba đường thay, chọn được cái nào thì chạy cái đó:

- **Đường A — thuần cơ chế, KHÔNG cần sạc, đo bằng bộ đếm (nên dùng)**: đặt lên màn một datum mà **xe này không
  có** — `coolant_temp` (`BYDAutoEngineDevice.getEngineCoolantTemp`, `TelemetryRegistry.kt:230`; xe thuần điện
  ⇒ [SUY] trả `null` mãi). Xem `KachiPerf`:
  1. sau ~3 nhịp, cột **`bỏ-xe-không-có` > 0** ⇒ `HalAbsentCache` đã đóng ⇒ **cơ chế chạy**;
  2. bấm Home ra khỏi Kachi rồi vào lại (kích `onStop` → `forgetCarDemand`) ⇒ cột đó **về 0 một nhịp** rồi leo
     lại ⇒ **`clear()` có chỗ gọi thật** (đúng thứ [P2-1] của review sinh ra để chặn).
  **PASS** = thấy đủ hai chuyển động. **FAIL** = `bỏ-xe-không-có` **không bao giờ về 0** ⇒ [P2] datum vắng lâu rồi
  mới có sẽ câm tới 10 phút.
- **Đường B — datum thật vắng-rồi-có, không cần trạm sạc**: `target_soc` (`TelemetryRegistry.kt:175`, còn sống,
  `NEEDS_CAR`) — đổi *mục tiêu sạc* trên màn BYD rồi xem ô Kachi có ra số ≤ 60 s.
- **Đường C — giữ đúng tinh thần bản gốc**, chỉ khi có sạc tại chỗ: cắm sạc **khi app đang chạy** → ô `target_soc`
  (không còn ô ETA) phải ra số ≤ 60 s.

**Ghi vào**: `perf-profile-2026-09-16.md` §9.3 — **sửa mục 7 tại chỗ** (nó đang trỏ tới ô đã xoá) + backlog **K8**
(a)(b)(c) kèm output.

---

## 3. P7 + S5 — HOME sống qua reboot **BẰNG NÚT NGUỒN VẬT LÝ**

**Mục tiêu**: (a) nút *Đặt Kachi làm màn hình chính* chạy đúng đường; (b) **[CHƯA BIẾT]** ROM có giữ HOME qua
reboot không — câu trả lời quyết định công tắc *giữ khi nổ máy* có cần bật mặc định.

⚠ **`adb reboot` KHÔNG TÍNH** (VEHICLE-TEST-V2.md:224 ghi rõ `adb reboot invalid`). Chứng minh reboot là thật:
`boot_id` ở §0.3 phải **đổi giá trị**.

### ⚠ Đính chính bắt buộc đọc trước: component đích ĐÃ ĐỔI, playbook 1.53 §2.19 đang SAI

| | Playbook 1.53 §2.19 (2026-09-14) | Code hôm nay [ĐO source] |
|---|---|---|
| Đích `set-home-activity` | `com.byd.launcher/…launcher.KachiHomeActivity` | **`com.byd.launcher/com.byd.clusternav.launcher.KachiHome`** (alias) — `app/.../DefaultHome.kt` `HOME_ALIAS_CLASS`/`component()` |
| Trạng thái xuất xưởng | (không nói) | alias **`enabled=false`** — `AndroidManifest.xml:114-124` |
| Lý do | — | `AndroidManifest.xml:99-103`: **[ĐO on-car 2026-09-15]** BYD packageinstaller chặn GUI-install app **có HOME đang bật**; owner [ĐO] DuDu cài như app thường rồi mới chọn làm launcher ⇒ HOME chỉ bật lúc chạy |
| Bật bằng gì | — | `DefaultHome.enableHomeEntry` (PackageManager, **không** shell) — bước ĐẦU của "Đặt làm màn hình chính" |
| `am start` nhắm ai | — | **`KachiHomeActivity`** (`launchComponent`), KHÔNG nhắm alias: `am start -n` vào component đang tắt trả `Activity class … does not exist` |

⇒ **Không chạy lệnh shell thô theo playbook cũ trước khi bấm nút trong app** — alias còn tắt thì `set-home-activity`
nhắm một component disabled, và kết quả đọc được sẽ nói sai về cả tính năng.

### Đường A — qua UI (đường người dùng thật, chạy trước)

1. Kachi › **Cài đặt › Hệ thống & quyền › Màn hình chính**. Dòng hổ phách *"Chưa — hệ thống đang dùng &lt;gói&gt;"*
   ⇒ bấm **Đặt Kachi làm màn hình chính**.
2. Kỳ vọng: *"Đang đặt…"* → *"Đã đặt — bấm Home để về Kachi"*, dòng chuyển xanh, nút biến mất.
3. Xác nhận bằng máy (2 lệnh, chép nguyên văn):
   ```bash
   $A shell "cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME"
   $A shell "dumpsys package com.byd.launcher | grep -A3 -i 'KachiHome'"   # alias enabled chưa
   ```
4. Bấm **nút Home vật lý** → Kachi phải lên.

- **Nếu báo "Cần kênh shell"** ⇒ đó là **§4 (F4)**, làm §4 trước rồi quay lại. Hai mục này nối nhau, đừng đổi thứ tự.

### Đường B — shell thô, **đúng component mới** (khi đường A báo lỗi và cần biết lỗi ở app hay ở ROM)

```bash
COMP=com.byd.launcher/com.byd.clusternav.launcher.KachiHome
$A shell "pm enable $COMP"                      # alias đang enabled=false — KHÔNG bỏ bước này
$A shell "cmd package set-home-activity $COMP"  # kỳ vọng: Success
$A shell "cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME"
```
`Success` mà `resolve-activity` vẫn trỏ launcher khác ⇒ **[P0] ROM giành lại HOME** ⇒ chép nguyên output + đợi 30 s
đọc lại (có launcher giành lại sau vài giây).

### Đường C — nếu cả A và B không đặt được HOME

Thử `KachiHomeActivity` (component của playbook cũ) để biết **ROM chỉ nhận activity chứ không nhận alias**:
```bash
$A shell "cmd package set-home-activity com.byd.launcher/com.byd.clusternav.launcher.KachiHomeActivity"
```
⚠ `KachiHomeActivity` **không khai `CATEGORY_HOME`** (`AndroidManifest.xml:104-108` chỉ MAIN+DEFAULT+LAUNCHER) ⇒
[SUY] lệnh này sẽ **không** làm nó thành HOME. Kết quả của phép thử này là **bằng chứng để sửa doc**, không phải
đường chữa — chạy vì nó tốn 5 giây và nó dứt điểm câu "alias hay activity".

### P7 — vòng reboot vật lý (chạy HAI lượt, công tắc TẮT rồi BẬT)

```bash
# TRƯỚC: chép boot_id + resolve HOME + 3 khoá prefs (§0.3, §0.4)
```
| Lượt | `keep_home_on_boot` | Làm | Kỳ vọng / kết luận |
|---|---|---|---|
| **1** | **TẮT** (mặc định) | tắt máy bằng **nút nguồn** → nổ lại → bấm Home | Kachi lên ⇒ **ROM GIỮ HOME** ⇒ mặc định TẮT là đúng, P7 xanh. Không lên ⇒ đi lượt 2 |
| **2** | **BẬT** (*Giữ Kachi làm màn hình chính khi nổ máy*) | reboot vật lý lần nữa → bấm Home | Kachi lên ⇒ công tắc **cứu được**, đề xuất owner bật. Vẫn không lên ⇒ **[P1]** `KachiAutostart` không chạy hoặc bị chặn — xem dưới |

Sau mỗi lượt, 4 lệnh (chép hết):
```bash
$A shell "cat /proc/sys/kernel/random/boot_id"                        # PHẢI khác giá trị ở §0.3
$A shell "cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME"
$A shell "logcat -d -v time -s KachiAutostart" | tail -30
$A shell "dumpsys package com.byd.launcher | grep -A3 -i 'KachiHome'"
```

**Đọc log `KachiAutostart` theo đúng 5 dòng mà mã in ra** (`app/.../KachiAutostart.kt`, thứ tự này là hợp đồng):
1. `launcher auto-start disabled by pref — skip` ⇒ **`launcher_autostart` đang TẮT** ⇒ mọi thứ sau không chạy
   (đây đúng là lỗ mà mục P7 của backlog đã ghi: một công tắc **không liên quan** giết cả đường khởi động).
2. `skip (a run is in-flight or within cooldown 30000ms)` ⇒ burst boot/OTA, **không phải lỗi**.
3. `freeform seed ensured (wrote=…)` ⇒ `false` là bình thường nếu marker `FF_USER_REMOVED` đã đặt.
4. `home entry (alias) enabled=… — reasserting HOME (keepOnBoot=… chosen=…)` **hoặc**
   `keep-home-on-boot OFF + home not chosen — not reasserting default HOME on boot`
   ⇒ dòng này nói thẳng vì sao HOME không được đặt lại. Điều kiện trong mã là `keepHomeOnBoot() || homeChosen()`.
5. `requested HOME up (…KachiHomeActivity)` ⇒ launcher được đưa lên (khác với "là HOME").

⚠ Phân biệt hai kết quả rất dễ lẫn: **Kachi LÊN** (dòng 5 làm được) ≠ **Kachi LÀ HOME** (dòng 4 + `resolve-activity`).
Bấm nút Home vật lý là phép thử duy nhất tách được hai cái.

**Đường lùi / hoàn tác** (ghi trước khi bắt đầu, owner có thể muốn trả lại ngay):
```bash
$A shell "cmd package set-home-activity <gói-launcher-cũ>/<activity>"   # lấy từ output §0.4
```
Hoặc trong app: **Cài đặt › Màn hình chính › "Bỏ chọn"** (`ClusterNavBridge.clearDefaultHome` xoá cả
`homeChosen` + `keepHomeOnBoot` → tắt alias → `set-home-activity` launcher khác — 1.76 thêm chính vì BUG1 kẹt Kachi).

**Ghi vào**: `docs/diagnostics/oncar-master/4-system.md` §Kết quả (§9) + **sửa playbook 1.53 §2.19** (component sai)
+ backlog **P7** / **S5**.

---

## 4. F4 — hộp *"Cho phép gỡ lỗi USB?"* ở lần mở đầu

**Bệnh đã đo** [ĐO xe DiLink3.0 2026-09-14, `carlog-kachi-20260914-2044/session-findings.md`]: `20:49:12` Kachi mở →
`onCreate` nối dadb bằng khoá mới ⇒ hệ bung `UsbDebuggingActivity`; `20:49:16.986`
`WindowManager: removeWindow … UsbDebuggingActivity` **đúng lúc** `KachiHomeActivity` resume toàn màn ⇒ hộp thoại
chết trước khi người lái thấy. Ổ cắm `ESTABLISHED`, `Recv-Q` 24→48 (dadb treo, vì `Dadb.create` **không** hạn đọc).
Hàng quyền lại nói *"Hạn chế của môi trường"* — sai người, sai việc.

**Bản vá phải verify (3 nửa, [ĐO source])**:
1. hoãn lần dò đầu tới `decorView.post` + `onWindowFocusChanged(true)` + yên **`SETTLE_MS=1500`**
   (`car-integration/.../FirstOpenApprovalPolicy.kt:41`) — `app/.../ShellChannelGate.kt` `arm()`;
2. thử lại **không giới hạn số lần** mỗi **`RETRY_EVERY_MS=20000`** (`:47`) chừng nào màn còn hiện, dừng ở `onStop`
   (`onHidden()`); mất tiêu điểm ⇒ **bỏ lượt** (không dựng hộp thoại chồng hộp thoại) — `FirstOpenApproval.attemptAllowed`;
3. nói ĐÚNG lý do: **chỉ** khi phân loại được `LocalShellFailure.AWAITING_APPROVAL`
   (`LocalShellRetryPolicy.kt:76` — `chain.any { it is SocketTimeoutException }`, lộ ra được **chỉ nhờ** hạn đọc
   `PROBE_READ_TIMEOUT_MS=6000`, `:54`) thì mới đổi hàng quyền sang *việc của người dùng* + hiện **dải nhắc**.

### Dựng lại điều kiện "lần mở đầu" — 3 đường, xếp theo mức phá

| | Cách | Phá gì | Ghi chú |
|---|---|---|---|
| **A** | **Máy mới / xe của anh em chưa từng cài Kachi** | không phá gì | **đường sạch nhất** — nếu có xe thứ hai thì luôn chọn cái này |
| **B** | Xoá khoá adb của Kachi rồi khởi động lại app: `run-as com.byd.launcher rm -f files/adb.key files/adb.pub` | chỉ khoá của Kachi (`AdbKeys.ensure` sinh lại) | **chỉ bản `vehicleTest`** (`run-as` cần debuggable). Bản release ⇒ không dùng được |
| **C** | Xoá `adb_keys` của xe: `rm /data/misc/adb/adb_keys` | ⚠ **thu hồi uỷ quyền của CẢ laptop** ⇒ mất luôn kênh đo | cần root/`su` — [CHƯA BIẾT] có trên ROM này không. **Đường cuối**, và phải làm **CUỐI BUỔI** |

⚠ Thứ tự bắt buộc: **F4 làm sau khi đã lấy hết số liệu cần adb** nếu chọn đường C. Đường A/B thì làm sớm được, và
nên làm **trước §3** vì §3 cần kênh shell để đặt HOME.

### Bước đo

```bash
# 1) trước khi mở Kachi
$A shell "ss -tn | grep 5555 || cat /proc/net/tcp | grep 15B3"     # 0x15B3 = 5555
# 2) mở Kachi (bấm Home / am start), rồi QUAN SÁT MÀN XE bằng MẮT trong 30 s
$A shell "logcat -d -v time | grep -iE 'UsbDebugging|ShellChannelGate|Preflight|removeWindow'" | tail -40
$A exec-out screencap -p > f4-01-dialog.png
```

**PASS — cả 4 phải đúng**:
1. hộp *"Cho phép gỡ lỗi USB?"* **hiện và SỐNG** qua lượt resume của Kachi (không bị `removeWindow` trong ~5 s);
2. **dải nhắc** hổ phách ở **đáy** màn Kachi có chữ + nút **Thử lại** (`R.string.kachi_shell_approval_msg`), và
   **không chặn cú chạm nào** ở ngoài khung chữ (chạm thử một ô bất kỳ — nó phải ăn);
3. tích **"Luôn cho phép từ máy tính này"** + OK ⇒ dải nhắc **tự biến mất trong ~1,5 s** (đường
   `onFocus(true)` dời lượt đã hẹn lên sớm thay vì ngồi hết 20 s — `ShellChannelGate.onFocus`);
4. quyền **tự cấp xong**: `logcat -s Preflight` có dòng `tự cấp …` và `sau khi tự cấp: …` với danh sách thiếu ngắn lại.

**FAIL và việc phải làm ngay**:
- hộp thoại **vẫn bị đè chết** ⇒ chép mốc `removeWindow` + mốc `ShellChannelGate` (chứng minh 1500 ms chưa đủ) ⇒
  đó là con số phải nới, **mang về số thật** chứ không đoán.
- dải nhắc **không hiện** nhưng hàng quyền nói *"Hạn chế của môi trường"* ⇒ lý do **chưa phân loại được**
  (`IO_ERROR`/`PORT_CLOSED`/`UNKNOWN` thay vì `AWAITING_APPROVAL`) ⇒ chép `logcat` + `Recv-Q`; đây là ca
  `ShellApprovalProbe.probe` trả `Ok(false)` (nối được mà `echo kachi_ok` không vọng lại).
- dải nhắc hiện **mãi không tắt** sau khi đã bấm Cho phép ⇒ bấm **Thử lại** trên dải; vẫn không tắt ⇒ [P1]
  `channelUp` không bao giờ được đặt ⇒ chép `logcat -s ShellChannelGate Preflight`.

**Đường lùi (nếu F4 làm mất kênh shell giữa buổi)**: mở lại bằng tay trên màn xe (hộp thoại RSA của adb), hoặc
đường **D** ở §0.2 (app tự chụp) để không mất phần còn lại của buổi.

**Ghi vào**: backlog **F4** + `docs/specs/kachi-permission-preflight.html` §9 (nhật ký triển khai) + ảnh `f4-*.png`.

---

## 5. W5 — mã phím khi **app camera đang tiền cảnh**

**Ba câu phải trả lời bằng số** (spec `kachi-camera-context-keys.html` §6): **Q1** mã phím thật của 4 phím ·
**Q2** phím có tới `onKeyEvent` khi app cam tiền cảnh không · **Q3** lệnh đổi góc có ăn khi cam đang mở không.

**Script đã có**: `scripts/vehicle/kachi/50-keys.sh <ip>:5555` (mục A→E + sinh `50-keys-table.md`). Phần dưới là
**bổ sung** cho nó, không thay: một tiền đề bắt buộc + thứ tự đường đo + một đường máy cho Q3.

### ⚠ Tiền đề — không có nó thì kết quả Q2 là SAI ÂM

`onKeyEvent` chỉ được gọi khi service **BOUND** và có cờ `FILTER_KEY_EVENTS`. [ĐO xe 18-09] service từng
**ENABLED mà KHÔNG Bound** ⇒ không phím nào tới. ⇒ **chạy §0.3 (2) ngay trước mục này**, và khi thấy không Bound
thì chữa bằng *Kiểm tra / Sửa ngay* (hoặc toggle danh sách) **rồi mới đo**. `capabilities=9` phải có trong
`dumpsys accessibility`.

### Q1 + Q2 — bốn đường, chạy theo thứ tự này

- **Đường A (QUYẾT ĐỊNH — rẻ nhất, không nuốt phím, không cần arm gì)**
  [ĐO source `app/.../modules/navaccess/NavAccessibilityService.kt:78-80`]: service log **MỌI** phím DOWN
  *trước* cả cổng học-phím và cổng `voiceKeyEnabled`:
  ```bash
  $A shell "logcat -c"
  # → mở app camera 360, rồi bấm: tăng âm · giảm âm · trái vô-lăng · phải vô-lăng (mỗi phím 1 nhấp + 1 lần GIỮ 1s)
  $A shell "logcat -d -v time -s NavAccess" | grep "onKeyEvent DOWN keycode="
  ```
  **Có dòng** ⇒ phím tới app (Q2 = CÓ) **và** dòng đó cho luôn keycode (Q1). **Không dòng nào** ⇒ app cam/hệ
  **nuốt trước** (Q2 = KHÔNG) — kết quả hợp lệ, xem "nếu Q2 = KHÔNG" dưới.
  ⚠ Dùng `logcat -c` + đọc ngay: đây là cách duy nhất chắc chắn dòng vừa in là của lần bấm này.
- **Đường B (`getevent`, lấy scancode — bổ sung, không thay A)**
  ```bash
  $A shell "getevent -lp" | grep -iE "add device|name:|KEY_VOLUME|KEY_UP|KEY_DOWN|KEY_LEFT|KEY_RIGHT"
  $A shell "getevent -lt"        # bấm trong ~25 s (50-keys.sh mục [B] đã tự bấm giờ)
  ```
  **[CHƯA BIẾT]** `getevent` có bị SELinux chặn trên ROM này không. Chặn ⇒ **bỏ Q1 đường này**, đường A đủ để gán
  phím (nó cho keycode Android — đúng thứ `VoiceKeyMatcher` dùng). Định hướng có sẵn [ĐO 2026-08-13/14]: nút mic
  vô-lăng = **scancode 582** trên `event7` (`simulate-keys`).
- **Đường C ("Học phím mới" — dùng khi A và B đều im)**
  Cơ chế [ĐO source]: `Prefs.voiceKeyLearn` bật ⇒ `onKeyEvent` **nuốt** phím (`return true`, `:88`), tự tắt học
  sau **ĐÚNG MỘT** phím (`:84`), rồi `VoiceKeyLearnBus.publish`. Bus **có đệm `pending`**
  (`VoiceKeyLearnBus.kt`) ⇒ bắt được cả khi màn Kachi không hiện, hộp đặt tên bung khi quay lại.
  ⇒ **một vòng arm = một phím** ⇒ 4 phím = 4 lượt (Cài đặt › Phím vô-lăng › Học phím mới → sang app cam → bấm →
  về Kachi). Chậm, nhưng nó **chứng minh được đường gán thật**, không chỉ đường log.
  ⚠ Nó nuốt phím ⇒ nếu owner đang cần phím đó cho xe thì tắt học trước khi rời mục.
- **Đường D (phím GIỮ có mã riêng không — R4 của spec)**
  Mỗi phím bấm thêm **1 lần giữ ~1 s**, so keycode với lần nhấp ngắn. Khác nhau ⇒ làm được "giữ = thân xe trong
  suốt" **không cần mốc thời gian** (bài học 1.19: cấm tự đặt ngưỡng giữ). Giống nhau ⇒ **R4 không thoả**, ghi
  "chưa làm được + điều kiện mở khoá".

### Q3 — lệnh đổi góc có ăn khi cam đang mở

`camera_view` [ĐO source `core/.../ControlRegistry.kt:339-342`]: `SELECT`, 5 nhãn *Trước/Sau/Trái/Phải/Rộng*,
`bindingKey = BYDAutoPanoramaDevice.setDisplayMode`, tier **NEEDS_CAR**. ⚠ Chú thích ngay trên nó nói thẳng: enum
`DISPLAY_MODE_*` (PANORAMA0/FULL_SCREEN1/WIDGET3/RF_REVERSE4/REVERSE5/3D_PANORAMA6) **KHÔNG khớp 5 nhãn** ⇒ đang
gửi **index thô** ⇒ *phép đo này là để chốt map nhãn↔enum*, không phải để xác nhận nhãn đúng.

- **Đường A (máy — nên dùng; `camera_view` KHÔNG nằm trong `CtlSafetyPolicy.CONFIRM_REQUIRED`
  (`core/.../CtlSafetyPolicy.kt:35-38`) ⇒ **không cần `auto_confirm`**)**:
  ```bash
  # cam đang mở; bắn lần lượt 0..5 rồi QUAN SÁT MÀN CAM mỗi lượt (có người thứ hai nhìn + đọc to)
  for v in 0 1 2 3 4 5; do
    $A shell "am broadcast -n com.byd.launcher/com.byd.clusternav.launcher.testbridge.KachiTestBridge -a com.byd.launcher.TEST --es cmd ctl --es id camera_view --ei v $v"
    sleep 2
  done
  ```
  Lời đáp `ctl` trả `{id,label,kind,v,route,device,accepted,hal_line,reply}` ⇒ **có luôn `accepted` + `hal_line`**,
  tức phân biệt được *"HAL nhận mà hình không đổi"* với *"HAL từ chối"* — điều mà bấm tay không cho biết.
  Lập bảng `v → góc thấy trên màn` = **chính cái map đang thiếu**.
- **Đường B (tay)**: bấm ô *Góc camera › Trái* trong Kachi (50-keys.sh mục [E]). Dùng khi cầu im.
- **Đường C (HAL thô — khi A trả `accepted:false`)**: gọi thẳng method để tách lỗi *registry* khỏi lỗi *HAL*:
  ```bash
  $A shell "am broadcast -n com.byd.launcher/com.byd.clusternav.launcher.testbridge.KachiTestBridge -a com.byd.launcher.TEST --es cmd hal \
     --es dev BYDAutoPanoramaDevice --es m setDisplayMode --es args 4 --es op set --ez auto_confirm true"
  ```

### Thu thêm 3 thứ trong cùng lượt (rẻ, và nếu thiếu thì phải quay lại xe)

```bash
$A shell "dumpsys window | grep -E 'mCurrentFocus|mFocusedApp'"   # package app cam THẬT → danh sách ngữ cảnh R2
$A exec-out screencap -p > w5-camera.png
```
- **OQ2 của spec**: khi **CarPlay/AA đang chiếu**, app cam còn nhận phím không. ⚠ Lúc cắm CP/AA thì đầu xe **tắt
  Wi-Fi** ⇒ mất adb ⇒ đo bằng **mắt** (phím có đổi góc không) và ghi tay; đừng cố adb.
- **OQ1**: *"thân xe trong suốt"* là arg nào — nằm trong bảng `v → góc` ở đường A (nếu một trong 6 giá trị cho ra
  hình đó thì đã trả lời xong; không có thì ghi **[CHƯA BIẾT]**, cần feature khác).

### PASS / FAIL

- **PASS** = `50-keys-table.md` điền KÍN (4 phím × 6 cột) + package app cam + bảng `v → góc`.
- **FAIL mềm (Q2 = KHÔNG)** = **vẫn là kết quả hợp lệ và đủ để quyết**: theo CLAUDE.md §14 tầng 1 thì
  **KHÔNG được viết code ngữ cảnh** (T1–T3 của spec dừng), ghi *"chưa làm được + điều kiện mở khoá"*
  (`trace-den-tan-cung.md`). Đừng biến nó thành "để sau xem lại".
- **FAIL cứng** = không đo được vì service không Bound / cam không mở được ⇒ đó là lỗi **tiền đề**, chạy lại §0.3.

**Ghi vào**: `docs/diagnostics/oncar-w5-keys-<ngày>.md` (đúng tên spec R1 yêu cầu) + `50-keys-table.md` +
spec `kachi-camera-context-keys.html` §6/§7 + backlog **W5**.

---

## 6. T-BRIDGE — xác nhận cầu kiểm thử trên ROM BYD (ba câu còn nợ)

Backlog nợ đúng ba câu: **(a)** shell của ROM BYD gửi được vào receiver `exported` không · **(b)** reboot bằng
**nút nguồn vật lý** rồi bắn lại phải ra `test_mode_off` · **(c)** một lệnh xe THẬT trả `✓`.

**Tiền đề [ĐO source]**: receiver `app/src/main/AndroidManifest.xml:252-258` — `.launcher.testbridge.KachiTestBridge`,
`exported="true"`, action `${applicationId}.TEST`. `exported` là **bắt buộc**: uid shell 2000 không gửi được vào
receiver `exported=false` (cùng họ ca `am start` vào `.ClusterNavActivity` ghi từ 2026-07-20), và `onReceive`
**không có `getCallingUid`** ⇒ cổng thật là **công tắc 60 phút**, không phải quyền gửi.

### (a) Shell ROM BYD gửi vào được không — 3 đường

```bash
# A — đường chuẩn (có -p, đúng như playbook)
$A shell "am broadcast -n com.byd.launcher/com.byd.clusternav.launcher.testbridge.KachiTestBridge -a com.byd.launcher.TEST --es cmd state"
# B — nếu A bị chối: thử nhắm THẲNG component (một số ROM lọc theo -p)
$A shell "am broadcast -n com.byd.launcher/.launcher.testbridge.KachiTestBridge \
   -a com.byd.launcher.TEST --es cmd state"
# C — nếu cả hai im: đọc dấu vết ở NƠI KHÁC (receiver vẫn có thể đã chạy mà setResultData không về tới terminal)
$A shell "logcat -d -s KachiTest" | tail -20
$A shell "ls -t /sdcard/Android/data/com.byd.launcher/files/test/ | head"
```
⚠ **Ba nơi trả lời, đừng chỉ đọc một** (`KachiTestBridge` KDoc): `setResultData` (terminal) · tệp JSON
`files/test/<mốc>-<lệnh>.json` · một dòng `logcat -s KachiTest`. Terminal im mà file/logcat có ⇒ **cầu CHẠY**,
chỉ `setResultData` không về được — kết luận khác hẳn.

⚠ **Bẫy dấu nháy đã trả giá** (playbook 1.53): `adb shell` cắt chuỗi theo khoảng trắng ⇒ `--es text "bật đèn đọc"`
chỉ tới `"bật"` mà lời đáp **vẫn `ok:true`**. Phải nháy **hai lớp**: `"'bật đèn đọc'"` (helper `k_shq` trong
`scripts/vehicle/kachi/_common.sh:303` làm đúng).

- **PASS** = một trong A/B trả JSON (hoặc C thấy dấu vết). **FAIL** = cả ba im ⇒ chép nguyên câu lỗi
  (`SecurityException`? `Broadcast completed: result=0`?) ⇒ **đó là kết luận lớn**: mọi script tự động của buổi
  mất hiệu lực, chuyển sang đường tay của từng mục (mỗi mục ở doc này đều có).

### (b) `test_mode_off` sau reboot vật lý — gộp vào cùng lượt reboot của §3

Cơ chế [ĐO source `core/.../testbridge/TestBridgeWindow.kt`]: giá trị lưu = `"<bootId>:<upUntil>"`;
`remainingMs` trả 0 khi `boot != bootId` ⇒ **khác lần nổ máy là cửa đóng**. `WINDOW_MS = 60 phút`.
`bootId` đọc từ `/proc/sys/kernel/random/boot_id` (`TestBridgeStore.kt:56`).
⚠ Bản đầu (2026-09-14) dùng `giờ tường − uptime` với dung sai 5 s và **đã hỏng thật trên xe** (đầu xe chỉnh giờ
GPS >5 s sau khi bật ⇒ cầu tự tắt) — nên phép thử này là **hồi quy cho đúng lỗi đó**.

```bash
# TRƯỚC reboot: bật công tắc, xác nhận cửa mở, chép boot_id
$A shell "am broadcast -n com.byd.launcher/com.byd.clusternav.launcher.testbridge.KachiTestBridge -a com.byd.launcher.TEST --es cmd state" | head -3
$A shell "cat /proc/sys/kernel/random/boot_id"
# → tắt máy bằng NÚT NGUỒN, nổ lại (cùng lượt với §3)
$A shell "cat /proc/sys/kernel/random/boot_id"        # PHẢI khác
$A shell "am broadcast -n com.byd.launcher/com.byd.clusternav.launcher.testbridge.KachiTestBridge -a com.byd.launcher.TEST --es cmd state"
```
- **PASS** = `"error":"test_mode_off"`, **và** `boot_id` đã đổi (chứng minh reboot thật).
- **FAIL** = còn chạy ⇒ **[P1] cửa sống qua lần nổ máy** = đúng thứ `TestBridgeWindow` sinh ra để chặn ⇒ chép giá
  trị thô: `... --es cmd prefs --es file kachi_test_bridge` (khoá `test_bridge_until`) + hai `boot_id`.
- Phụ (rẻ, làm luôn): **chờ 60 phút** trong buổi rồi bắn lại ⇒ cũng phải `test_mode_off` (trần thời gian).

### (c) Một lệnh xe THẬT trả `✓`

```bash
# đọc — vô hại
$A shell "am broadcast -n com.byd.launcher/com.byd.clusternav.launcher.testbridge.KachiTestBridge -a com.byd.launcher.TEST --es cmd sweep --es op info"
# ghi — chọn control KHÔNG thuộc CONFIRM_REQUIRED (CtlSafetyPolicy.kt:35-38) ⇒ đèn đọc
$A shell "am broadcast -n com.byd.launcher/com.byd.clusternav.launcher.testbridge.KachiTestBridge -a com.byd.launcher.TEST --es cmd ctl --es id readl"
# cổng CONFIRM phải TỪ CHỐI khi không có cờ:
$A shell "am broadcast -n com.byd.launcher/com.byd.clusternav.launcher.testbridge.KachiTestBridge -a com.byd.launcher.TEST --es cmd ctl --es id door"
#   → kỳ vọng needs_confirm + nguyên văn câu hỏi, và XE KHÔNG ĐỘNG GÌ
```
- **PASS** = `readl` trả `accepted:true` + `hal_line` thật + **đèn đọc bật thật** (mắt); `door` trả `needs_confirm`
  và **xe không mở khoá**.
- **FAIL nghiêm trọng** = `door` **chạy thật** mà không có `auto_confirm` ⇒ **[P0]**, dừng dùng cầu ngay, chép log.

**Ghi vào**: backlog **T-BRIDGE** (a)(b)(c) + `docs/specs/kachi-test-bridge.html` §Nhật ký triển khai.
⚠ **TẮT công tắc trước khi rời xe** — một cửa thi hành lệnh mở vĩnh viễn trên xe là một lỗ.

---

## 7. P8 — vòng kiểm quyền lúc mở launcher

**Số đúng là 7 điều kiện, không phải 6** [ĐO source `core/.../LauncherRequirements.kt:293-295`]:
`ALL = [SHELL_CHANNEL, FREEFORM, DEFAULT_HOME, OVERLAY, NOTIFICATION_LISTENER, ACCESSIBILITY, MICROPHONE]`
(`SHELL_CHANNEL_AWAITING_APPROVAL` là bản `copy()` của F4, **không** nằm trong `ALL`). Backlog/playbook còn ghi
"5–6 điều kiện" ⇒ xem §10.

| id | `fixBy` | `coreFeature` | Lệnh app tự cấp [ĐO source `PermissionPreflight.grantCommand`] |
|---|---|---|---|
| `shell` | ENVIRONMENT (→ USER khi `AWAITING_APPROVAL`) | ✅ | — (là F4) |
| `freeform` | **SELF_AT_BOOT** | ✅ | — (cố ý KHÔNG có lệnh: trạng thái BỀN, chỉ `FreeformSeedPolicy` được ghi) |
| `default_home` | USER | | — (là §3) |
| `overlay` | SELF | | `appops set com.byd.launcher SYSTEM_ALERT_WINDOW allow` |
| `notif_listener` | SELF | | `cmd notification allow_listener com.byd.launcher/<NavNotificationListener>` |
| `accessibility` | SELF | | **đọc-sửa-ghi** (append, KHÔNG ghi đè) + `settings put secure accessibility_enabled 1` |
| `microphone` | SELF | | `pm grant com.byd.launcher android.permission.RECORD_AUDIO` |

### Đo — 3 đường, đối chiếu chéo (đây là cả mục đích của P8: app nói phải KHỚP hệ thống)

- **Đường A — ngoài shell (sự thật của hệ thống)**
  ```bash
  $A shell "settings get secure enabled_notification_listeners"
  $A shell "settings get secure enabled_accessibility_services"
  $A shell "settings get secure accessibility_enabled"
  $A shell "appops get com.byd.launcher SYSTEM_ALERT_WINDOW"
  $A shell "settings get global enable_freeform_support"
  $A shell "dumpsys package com.byd.launcher | grep -i RECORD_AUDIO"
  $A shell "cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME"
  ```
- **Đường B — trong app**: *Cài đặt › Hệ thống & quyền* — đọc 7 hàng bằng mắt + chụp ảnh.
- **Đường C — nhật ký vòng kiểm** (một dòng, cho cả trước và sau khi tự cấp):
  ```bash
  $A shell "logcat -d -v time -s Preflight" | tail -20
  ```

### PASS / FAIL

- **PASS (1)** — **khớp từng hàng** giữa A và B. Lệch một hàng ⇒ chép **cả hai vế** (đó là bug, và chỉ so mới thấy).
- **PASS (2)** — **đủ thì IM LẶNG**: mở/đóng Kachi **3 lần**, toast *"Kênh điều khiển cửa sổ…"* nổ **≤ 1 lần mỗi
  phiên tiến trình** (`noticeShown: AtomicBoolean` — `PermissionPreflight.kt`). Đây là U8(b), verify cùng lượt.
- **PASS (3)** — **KHÔNG chặn launcher**: dù thiếu quyền, màn chính vẫn dùng được (chạm được ô, mở được Cài đặt).
- **PASS (4)** — hàng nào `UNKNOWN` thì app phải nói *"chưa đọc được"*, **không** nói "thiếu"
  (`RequirementState.UNKNOWN` không tính là thiếu).
- **Nhánh tự-lành sau khi tiến trình chết** — [ĐO 2026-09-11] hệ thống **TỰ THU HỒI** trợ năng khi tiến trình chết
  (danh sách về `null`, cờ về 0). Đo lại trên xe:
  ```bash
  $A shell "am force-stop com.byd.launcher"
  $A shell "settings get secure enabled_accessibility_services; settings get secure accessibility_enabled"
  # mở lại Kachi → đọc lại 2 lệnh trên ⇒ phải CÓ LẠI (vòng kiểm tự lành)
  ```
  ⚠ Giới hạn của lần đo cũ: chỉ đo bằng **tắt hẳn app**, **chưa** đo bằng **reboot máy** ⇒ đo nốt trong lượt
  reboot của §3 (đọc 2 lệnh trên **ngay sau khi nổ máy**, trước khi mở Kachi).
- **Ca CỐ Ý KHÔNG dựng**: ép thiếu quyền bằng cách ghi `enabled_accessibility_services` — **KHÔNG làm**
  (dự án từng có [P1] xoá trợ năng của app khác). Chỉ đọc; nếu muốn ca thiếu thì dùng đúng `force-stop` ở trên.

**Ghi vào**: `10-permissions.txt` (nếu chạy `10-baseline.sh`) + ảnh màn *Hệ thống & quyền* + backlog **P8** / **U8(b)**.

---

## 8. U8a — ROM DiLink có hiện lại thanh trạng thái / taskbar không

**Câu hỏi backlog**: khi cửa sổ **freeform của một ô** có focus, thanh trạng thái Android có hiện lại ở mép trên?

**[ĐO source — một nửa câu trả lời ĐÃ CÓ, từ 1.79]**: `app/.../launcher/voice/VoiceOverlay.kt:164-180` ghi
**[ĐO xe 2026-09-18]** — owner: *"overlay kéo taskbar hệ thống lên"*. Gốc: cờ ẩn thanh hệ thống được Android đọc từ
**cửa sổ ĐANG CÓ TIÊU ĐIỂM**; màn chính giữ cờ (`goImmersiveWindow`), overlay không ⇒ **giây nó nhận tiêu điểm là
giây status/nav/taskbar hiện lại**, và nó **ở lại** cả khi tấm chữ đã tắt.

⇒ ROM này **CÓ** thanh hệ thống và **CÓ** kéo lên khi một cửa sổ không-immersive lấy tiêu điểm ([ĐO] gián tiếp,
qua báo cáo owner + bản vá). Phần **[CHƯA BIẾT]** còn lại là hẹp hơn: **cửa sổ freeform của một ô** có gây ra
điều đó không (nó là cửa sổ của **app khác**, Kachi không đặt cờ hộ được).

### Đo — 3 đường

```bash
# A — đo bằng khung cửa sổ, không bằng mắt (chắc nhất)
$A shell "dumpsys window | grep -iE 'mCurrentFocus|statusbar|StatusBar|navigationbar' " > u8a-before.txt
#   → chạm vào Ô có app (freeform) để nó lấy tiêu điểm
$A shell "dumpsys window | grep -iE 'mCurrentFocus|statusbar|StatusBar|navigationbar' " > u8a-after.txt
diff u8a-before.txt u8a-after.txt
# B — ảnh nguyên màn (đếm pixel dải trên), theo CLAUDE.md §15: chụp NGAY TRƯỚC mỗi thao tác
$A exec-out screencap -p > u8a-01-home.png        # trước khi chạm
$A exec-out screencap -p > u8a-02-slot-focus.png  # sau khi chạm vào ô
# C — xác nhận bản vá 1.79 của overlay KHÔNG kéo thanh lên nữa (hồi quy của chính vá đó)
#   → bấm mic (overlay voice hiện) → chụp; overlay tắt → chụp lại
$A exec-out screencap -p > u8a-03-voice-overlay.png
```

### PASS / FAIL

- **Kết quả cần**: một trong hai câu, **có ảnh kèm**:
  - *"ô freeform lấy focus ⇒ thanh hệ thống HIỆN"* ⇒ launcher phải xử lý (mất chiều cao thật của vùng làm việc);
    mang về **chiều cao dải (px)** để tính, đừng chỉ nói "có".
  - *"KHÔNG hiện"* ⇒ U8a đóng, ghi [ĐO] + ảnh.
- **PASS của đường C (hồi quy 1.79)**: overlay voice bật/tắt xong, thanh hệ thống **không bị ghim** lại.
  FAIL ⇒ [P1] bản vá `VoiceOverlay` chưa ăn trên xe ⇒ chép ảnh + `logcat -s KachiVoice`.
- ⚠ Đo trên **màn giữa 1920×720**. **CẤM `input tap <x> <y>`** để lấy focus (toạ độ máy ảo 1080×2340 khác hẳn —
  CLAUDE.md §15 đã trả giá); dùng **tay người** chạm vào ô.

**Ghi vào**: backlog **U8** (a) + ảnh `u8a-*.png`.

---

## 9. GHI KẾT QUẢ Ở ĐÂU (điền ngay tại xe — trí nhớ không phải bằng chứng)

### 9.1 Bảng chốt — copy vào đây và điền
| # | Mục | Đường đã dùng (A/B/C/D) | Kết quả | PASS/FAIL | Bằng chứng (tệp/ảnh/log) |
|---|---|---|---|---|---|
| 1 | K5 HAL đọc/phút (< 150) | | | | `logcat -s KachiPerf` |
| 2 | K5 `no permission`/phút (< 20) | | | | `usage-*.log` |
| 3 | K5 CPU idle % + **load average** | | | | `top -b -n 2` |
| 4 | K5 RSS `:app` / `:tts` | | / | | `dumpsys meminfo` ×2 |
| 5 | K5 log KB/phút (< 20) | | | | Δ byte `kachi-logs/` |
| 6 | K5 kiểm mắt 4 bề mặt | | /4 | | ảnh |
| 7 | K8a giọng hỏi datum ngoài màn (2 tốc độ) | | → | | `logcat -s KachiVoice` |
| 8 | K8b HAL lên muộn (≤ 60 s) | | | | `KachiPerf` + `sweep` |
| 9 | K8c `bỏ-xe-không-có` lên rồi về 0 | | | | `KachiPerf` |
| 10 | S5 đặt HOME qua UI | | | | `resolve-activity` trước/sau |
| 11 | S5 alias `KachiHome` enabled | | | | `dumpsys package` |
| 12 | **P7 lượt 1** — keep=TẮT, reboot vật lý | | | | `boot_id` ×2 + `resolve` |
| 13 | **P7 lượt 2** — keep=BẬT, reboot vật lý | | | | `logcat -s KachiAutostart` |
| 14 | F4 hộp USB-debug sống qua resume | | | | ảnh + `logcat` |
| 15 | F4 dải nhắc hiện / tự tắt ≤1,5 s | | | | ảnh ×2 |
| 16 | F4 quyền tự cấp sau khi Cho phép | | | | `logcat -s Preflight` |
| 17 | W5 Q1 keycode 4 phím | | | | `50-keys-table.md` |
| 18 | W5 Q2 tới `onKeyEvent` khi cam mở | | | | `logcat -s NavAccess` |
| 19 | W5 phím GIỮ có mã riêng | | | | cùng trên |
| 20 | W5 Q3 bảng `v → góc` cam | | | | lời đáp `ctl` |
| 21 | W5 package app cam | | | | `mCurrentFocus` |
| 22 | T-BRIDGE (a) shell BYD gửi vào được | | | | JSON / `KachiTest` |
| 23 | T-BRIDGE (b) reboot ⇒ `test_mode_off` | | | | 2 `boot_id` |
| 24 | T-BRIDGE (c) `readl` ✓ + `door` chặn | | | | lời đáp `ctl` |
| 25 | P8 7 hàng app ↔ hệ thống khớp | | /7 | | `10-permissions.txt` + ảnh |
| 26 | P8 toast ≤ 1 lần / 3 lượt mở | | | | quan sát + `logcat` |
| 27 | P8 trợ năng tự lành sau reboot | | | | `settings get` ×2 |
| 28 | U8a ô freeform ⇒ thanh hệ thống? | | | | `u8a-*.png` + diff |
| 29 | U8a overlay 1.79 không ghim thanh | | | | ảnh |
| 30 | **BUG2** force-stop có chữa adb? | | | | §0.2 |

### 9.2 Doc phải cập nhật sau buổi (atomic với backlog — R2.1)
| Kết quả của | Ghi vào |
|---|---|
| K5 | `perf-profile-2026-09-16.md` **§0b mới** (bảng cùng cột §0) · backlog **K5** |
| K8 | `perf-profile-2026-09-16.md` §9.3 (**sửa mục 7** — xem §10.2) · backlog **K8** |
| P7/S5 | doc này §9.1 · **sửa `oncar-playbook-kachi-1.53.md` §2.19** (§10.1) · backlog **P7**, **S5** |
| F4 | spec `kachi-permission-preflight.html` §9 · backlog **F4** |
| W5 | **`docs/diagnostics/oncar-w5-keys-<ngày>.md`** (spec R1 đòi đúng tên này) · spec §6/§7 · backlog **W5** |
| T-BRIDGE | spec `kachi-test-bridge.html` §Nhật ký · backlog **T-BRIDGE** |
| P8/U8a | backlog **P8**, **U8** · ảnh |
| BUG2 | `oncar-adb-wireless-broken-after-kachi-2026-09-18.md` §3 (điền kết quả Test A) |
| Tất cả | `.kiro/steering/project-context.md` §5 + `docs/PROJECT-BACKLOG.md` |

---

## 10. ĐÍNH CHÍNH — 6 chỗ tài liệu/bài test cũ đã LỆCH code (tìm được khi soạn doc này)

Mỗi mục là một bài test **sẽ chạy sai** nếu làm theo doc cũ.

**10.1 Component HOME sai trong playbook 1.53 §2.19.** Doc ghi `set-home-activity …/KachiHomeActivity`; code từ
2026-09-15 dùng **alias `…/KachiHome`**, `enabled=false` sẵn (`DefaultHome.kt` · `AndroidManifest.xml:114-124`).
`KachiHomeActivity` **không khai `CATEGORY_HOME`** (`:104-108`) ⇒ [SUY] lệnh cũ không làm nó thành HOME. Tác động:
§3 sẽ kết luận "ROM không nhận" trong khi thật ra là gọi sai component. **⇒ sửa playbook.**

**10.2 K8c trỏ vào một ô đã bị xoá.** `perf-profile-2026-09-16.md` §9.3 mục 7 dùng ô **"ETA sạc"**; toàn bộ 8 datum
sạc đã xoá ở `(V) FEATURE-FILTER 2026-09-17` (`TelemetryRegistry.kt:160-161`). Bài test **không chạy được**.
**⇒ dùng §2c đường A/B/C; sửa mục 7 tại chỗ.**

**10.3 `top | grep byd.launcher` nay khớp HAI tiến trình.** 1.79 đưa Piper ra `:tts`
(`AndroidManifest.xml:205`), `dumpsys meminfo com.byd.launcher` **không** gộp nó. Đọc một số = số bịa.
**⇒ §1 đường B đo cả hai.** Thêm giá trị: `:tts` RSS lớn ⇒ `isTtsProcess()` hỏng ⇒ bắt được đúng nguồn OOM cũ.

**10.4 Dòng `KachiPerf` chỉ in khi màn chính RESUMED** (`KachiHomeActivity.kt:181-190,438,457`), còn **poll HAL
scope theo `STARTED`** (`KachiHomeWiring.kt:383,395-400`). ⇒ có khe *poll chạy mà perf im*; và "app toàn màn ⇒ 0
dòng" **không** nghĩa là tải 0. **⇒ §1 phải có đường `top` độc lập.** [SUY] — chưa ai đo khe này trên xe.

**10.5 P8 có 7 điều kiện, không phải 5–6.** `LauncherRequirements.ALL` (`:293-295`) gồm cả `MICROPHONE`.
Playbook §2.1 ghi "5 điều kiện", backlog ghi "6". **⇒ §7 đo 7 hàng; sửa hai chỗ chữ.**

**10.6 U8a không còn là [CHƯA BIẾT] hoàn toàn.** `VoiceOverlay.kt:164-180` mang **[ĐO xe 2026-09-18]**: ROM **có**
thanh hệ thống và **có** kéo lên khi cửa sổ không-immersive lấy tiêu điểm. **⇒ câu hỏi thu hẹp lại** thành "cửa sổ
freeform của ô có gây ra không" + thêm một phép **hồi quy cho bản vá 1.79** (§8 đường C).

---

## 11. THỨ TỰ CHẠY ĐỀ NGHỊ (một buổi, rủi ro tăng dần — và không mục nào chặn mục sau)

| Thứ tự | Mục | Vì sao ở đây | Rủi ro |
|---|---|---|---|
| 1 | §0.1–0.4 + **BUG2 Test A** | không có kênh đo thì 8 mục đều vô nghĩa; Test A tốn 1 phút | đọc |
| 2 | §7 **P8** | chỉ đọc, và nó cho biết thiếu quyền gì ⇒ mọi mục sau đọc đúng ngữ cảnh | đọc |
| 3 | §1 **K5** | cần **5 phút ĐỨNG YÊN** ⇒ làm sớm, làm trong lúc nghỉ; đừng chen thao tác | đọc |
| 4 | §6 **T-BRIDGE (a)(c)** | mọi mục sau dùng cầu ⇒ chốt nó sớm | ghi nhẹ (`readl`) |
| 5 | §8 **U8a** | quan sát + ảnh | đọc |
| 6 | §5 **W5** | cần mở app cam; đo xong đóng cam | đọc + mở cam |
| 7 | §4 **F4** | nếu dùng đường C (xoá `adb_keys`) thì **mất kênh** ⇒ để sau 1–6; đường A/B thì làm được ở đây | ghi (khoá adb) |
| 8 | §3 **S5** đặt HOME | cần kênh shell (⇒ sau F4) | ghi hệ thống |
| 9 | §2a **K8a** | cần **xe LĂN BÁNH** ⇒ gộp vào lúc chạy thử | đọc, khi lái |
| 10 | §2c **K8c** | bộ đếm, làm lúc đứng yên | đọc |
| **11** | **§3 P7 lượt 1 + §6(b) + §2b K8b** | **MỘT lượt tắt máy trả 3 câu**: HOME sống không · cầu `test_mode_off` · HAL lên muộn. Chụp `boot_id` trước/sau | reboot vật lý |
| 12 | §3 P7 lượt 2 (nếu lượt 1 fail) | reboot thứ hai với công tắc BẬT | reboot vật lý |
| 13 | Dọn: tắt cầu kiểm thử · trả HOME nếu owner muốn · đóng cam | | — |

⚠ **Gộp ở bước 11 là chỗ tiết kiệm lớn nhất của buổi** — ba mục đang nợ đều cần đúng một lượt tắt-nổ máy. Chuẩn bị
sẵn 6 lệnh của bước đó trên một dòng để bắn ngay khi máy vừa nổ (K8b có **cửa sổ 5 giây đầu**).

---

## 12. NGUỒN

**Mã (tất cả `[ĐO source]`, đọc 2026-09-19 tại HEAD)**
`core/.../launcher/KachiPerf.kt` · `CarStatusRepository.kt:51-52,92-98` · `HalAbsentCache.kt:83,87,90,93` ·
`LauncherRequirements.kt:175-295` · `HomeActivityCmd.kt` · `ControlRegistry.kt:133,339-342` ·
`CtlSafetyPolicy.kt:35-41` · `TelemetryRegistry.kt:160-161,175,230` ·
`testbridge/TestBridgeWindow.kt` · `testbridge/TestBridgeCommand.kt:120-245`
`app/.../launcher/KachiHomeActivity.kt:181-190,438,457` · `KachiHomeWiring.kt:383,395-400` ·
`BydHalGateway.kt:78,102,186,193` · `KachiLog.kt:14-15,29,38,66,118` · `PermissionPreflight.kt` ·
`DefaultHome.kt` · `ShellChannelGate.kt` · `NavConnect.kt:49` ·
`modules/navaccess/NavAccessibilityService.kt:70-89` · `modules/voicekey/VoiceKeyLearnBus.kt` ·
`launcher/voice/VoiceOverlay.kt:164-180` · `testbridge/KachiTestBridge.kt` · `testbridge/TestBridgeStore.kt:26-56`
`app/.../KachiAutostart.kt` · `KachiApplication.kt:23,44-45` · `app/src/main/AndroidManifest.xml:99-124,205,252-258`
`car-integration/.../carexec/FirstOpenApprovalPolicy.kt:41,47,54,63-85` · `LocalShellRetryPolicy.kt:38-76`

**Doc RE / diagnostics đã có (xây trên, không RE lại)**
`perf-profile-2026-09-16.md` §0/§0.1/§6/§7/§9.1-9.3 · `oncar-playbook-kachi-1.53.md` §0.5/§1.1/§1.2/§2.1/§2.4/§2.9/§2.19/§3 ·
`oncar-playbook-kachi-1.55.md` · `oncar-voice-number-and-voicekey-bind-2026-09-18.md` §2/§4 ·
`oncar-adb-wireless-broken-after-kachi-2026-09-18.md` §1-§6 · `adb-car-tunnel-macos.md` ·
`oncar-piper-crash-binding-2026-09-18.md` · `oncar-runbook-hey-kachi.md` · `dudu-launcher-hal-RE-2026-09-06.md` ·
`carlog-kachi-20260914-2044/session-findings.md` · `VEHICLE-TEST-V2.md:224` · `oncar-session-2026-09-14-findings.md`

**Spec** `kachi-camera-context-keys.html` §6-§8 · `kachi-permission-preflight.html` · `kachi-test-bridge.html` ·
`kachi-settings-ia-v2.html` §9 · `kachi-perf-2026-09.html` §10

**Script** `scripts/vehicle/kachi/50-keys.sh` · `71-hal-sweep.sh` · `10-baseline.sh` · `40-ota.sh` ·
`_common.sh:303` (`k_shq` — bẫy dấu nháy) · `_common.sh:336-353` (`k_test_alive`/`k_test_gate`)

**Chưa có, nên làm**: `scripts/vehicle/kachi/adb_raw.py` — client adb thô hiện còn ở `/tmp/adb_raw.py`, là đường
DUY NHẤT vào được xe hôm 18-09. Mất nó là mất kênh đo (đã ghi nợ ở `oncar-runbook-hey-kachi.md`).
