# RUNBOOK — TEST XE MỘT-LẦN-ĐỦ (Kachi 1.79 · 80)

> **Loại**: Diagnostics (on-car master runbook) · **Trạng thái**: Current · **Ngày**: 2026-09-20 · **Bản đích**: **1.83 (84)** (OTA trước; xe cũ hơn ⇒ `40-ota.sh`)
> **Mục đích**: MỘT buổi đóng hết mục `🚗 chờ xe`. Mỗi bước có **lệnh gõ sẵn** · **tiêu chí PASS/FAIL** · **outcome → option kế thử NGAY cùng buổi** (không hẹn buổi thứ hai) · **ô ghi kết quả**.
> **📋 LIST HẾT MỤC CẦN XE (đọc TRƯỚC)**: `oncar-master/0-PENDING.md` — bảng tổng 8 nhóm (100 info + 34 action + voice + Hey Kachi + cast + system), ưu tiên + trace tới cách-làm. Runbook này (8 phase) là bộ điều phối chi tiết.
> **Gom từ 6 doc** (chi tiết nằm ở đó, runbook này là bộ điều phối — đừng chép lại):
> `oncar-master/1-hal.md` (ma trận HAL) · `2-slot-cast.md` (option A–I app-vào-ô + cast) · `3-voice.md` (voice + `:tts`) · `4-system.md` (K5/K8/P7/S5/F4/W5/T-BRIDGE/P8/U8a) · `oncar-playbook-kachi-1.53.md` (quy trình + bẫy) · `oncar-runbook-hey-kachi.md` (wake word).
> **Nhãn** (CLAUDE.md §2): `[ĐO source]` đọc được ở mã/dump có `file:line` · `[ĐO xe]` số thật của lượt xe trước · `[SUY]` suy từ nguồn, chưa chạy · `[CHƯA BIẾT]` không có dữ liệu.
> **Ô trống = CHƯA ĐO.** Điền bừa một dấu tick tệ hơn để trống.
> **An toàn**: xe **ĐỖ · số P · phanh tay** cho mọi bước GHI. Phase E chạm **cụm trước mặt người lái**. Phase C3 + D-cốp là bước cần xe **lăn bánh** — **người lái KHÔNG gõ adb**, phải có người thứ hai.

---

## 0.0 DELTA 1.79 → 1.83 (thêm vào runbook này, 2026-09-20)

> 8 phase A–I bên dưới VẪN ĐÚNG (voice/`:tts`/Hey Kachi/HAL/cast). Bản đích nay **1.83** — thêm các mục sau, mỗi mục chỉ 1–2 lệnh, cắm vào phase tương ứng:

| # | Bản | Mục | Cắm vào | Lệnh / cách đo | PASS |
|---|---|---|---|---|---|
| Δ1 | 1.82 | **"tìm bài hát X"** tra nhạc (không còn KHÔNG-HIỂU) | Phase C (say) | `T say "'tìm bài hát ngày chưa giông bão'"` → decision | intent = Media(QUERY) |
| Δ2 | 1.82 | **"mở việt máp/mép/mốp"** mở VietMap (biến thể phiên âm) | Phase C (mic thật) | nói "mở việt máp" · `logcat \| grep 'START.*vietmap'` | mở `vn.vietmap.live`, KHÔNG mở drawer |
| Δ3 | 1.82 | **"điều hòa 25 độ"** đặt nhiệt (không toggle AUTO) | Phase D (HAL) | `T say "'mở điều hòa hai mươi lăm độ'"` → xe đặt 25°C | nút temp = 25 (KHÔNG chỉ bật AUTO) |
| Δ3b | 1.83 | GUARD: **"điều hòa chế độ hai"** KHÔNG được đặt nhiệt 17 | Phase D | `T say "'bật điều hòa chế độ hai'"` | intent = ac_auto (KHÔNG phải temp=17) |
| Δ4 | 1.80 | **"mở kính"** hạ 1 cửa LÁI (không cả 4) | Phase D | `T say "'mở kính'"` → nhìn xe | chỉ kính tài xế hạ |
| Δ5 | 1.80 | **cốp/ca-pô chỉ mở khi 0km/h** (gate tốc độ) | Phase D + xe lăn (người thứ 2) | đỗ: "mở cốp" ăn · lăn: "mở cốp" → "chỉ mở khi xe dừng" | gate đúng cả 2 trạng thái |
| Δ6 | 1.80 | **read-back**: TOGGLE/COVER đọc lại xác nhận | Phase D | nghe reply sau "bật đèn đọc" | khớp→"đã bật" bỏ hedge · lệch→"xe không nhận lệnh" |
| Δ7 | 1.81 | **B music có PHÁT thật** (WavProbe không ghi được) | Phase C | `logcat -c` → nói/say "mở bài hát X" → `logcat \| grep -iE 'MediaSession\|PLAY_FROM_SEARCH\|youtube'` + NGHE | nhạc kêu thật |
| Δ8 | 1.83 | **P2 marker**: Hey Kachi tự-tắt hiện đúng OFF + persist | Phase G | bật Hey Kachi → ép auto-disable (nói bừa 6 lần/phút) → mở Cài đặt | công tắc hiện **OFF** (không ON dởm); reboot vẫn OFF tới khi bật lại tay |
| Δ9 | 1.83 | **setting KHÔNG bị :wake xoá** (P2 gốc) | Phase G | đặt biển tốc độ vị trí X → bật/auto-tắt Hey Kachi → kiểm vị trí X | vị trí biển KHÔNG mất |

⚠ **Δ7 (music) + C1 (vietmap) là hai mục CHÍNH cần `logcat` — chạy `logcat -c` NGAY TRƯỚC, `logcat -d \| grep` NGAY SAU.** Đây là thứ log WavProbe của owner **không** trả lời được (không ghi app đích).

---

## 0. CHUẨN BỊ TRƯỚC KHI RA XE

### 0.1 Bản nào cài — quyết định TRƯỚC, vì nó mở/đóng 3 bước

| Bản | Lệnh | Mở được gì | Mất gì |
|---|---|---|---|
| **`release`** (mặc định ship) | `apk/Kachi-1.79-release.apk` đã có sẵn ([ĐO] 38 128 296 B, 2026-09-18) | đúng bản sẽ ship | **không `run-as`** ⇒ **F3 (kill `:tts`) và H1 (F4) mất đường tin cậy nhất**, không đọc được `shared_prefs` |
| **`vehicleTest`** ⭐ **khuyến nghị cho buổi này** | `./gradlew :app:assembleVehicleTest` → `adb install -r` | `run-as com.byd.launcher` ⇒ **F3 kill `:tts` cùng uid** (đường DUY NHẤT [SUY] chắc chắn không bị từ chối) + **H1 xoá `files/adb.key`** dựng lại "lần mở đầu" + đọc prefs | bản debuggable **không được để lại trên xe** ⇒ cài lại `release` cuối buổi |

[ĐO source] `app/build.gradle.kts:116-124` — `vehicleTest` = `initWith(release)` + `isDebuggable = true` + **cùng khoá ký release** ⇒ `install -r` đè lên release **không mất dữ liệu**.

⚠ **Vì sao `run-as` quan trọng cho F3**: `:tts` và `:app` **cùng uid** ([ĐO source] `AndroidManifest.xml:205` `android:process=":tts"` — cùng gói, cùng uid). Shell uid 2000 gửi `kill -9` vào tiến trình của uid khác **[CHƯA BIẾT] có bị từ chối trên ROM này không**; `run-as` thì chắc chắn được vì nó *là* uid đó. Thang 3 đường ở F3.

### 0.2 OTA lên 1.79 — không bỏ qua được

```bash
adb shell "dumpsys package com.byd.launcher | grep -E 'versionName|versionCode'"
```
`versionName=1.79` + `versionCode=80` ⇒ đi tiếp. Thấp hơn ⇒ `scripts/vehicle/kachi/40-ota.sh <ip>:5555` (đọc bản đích từ tên tệp `apk/Kachi-*-release.apk`, không từ hằng).

**Ba thứ chỉ có ở 1.79 mà cả buổi này đo** [ĐO source]: Piper ra tiến trình `:tts` (đổi cách đọc RSS/CPU — §B1; là toàn bộ nội dung F3) · overlay ẩn thanh hệ thống (`VoiceOverlay.kt:164-180` — §B7) · `NavConnect.GRANT_TIMEOUT_MS` 9s→20s của 1.78 (điều kiện để phím sống — §A4, §F).

### 0.3 Kênh đo — thang 4 đường, thử theo thứ tự

[ĐO 09-16/18 `adb-car-tunnel-macos.md` §1] `nc` thông tới `<ip>:5555` mà `adb` báo `No route to host` ⇒ **macOS Privacy › Local Network** chưa cấp cho terminal, **không phải lỗi xe**.

| Đường | Lệnh | Khi nào |
|---|---|---|
| **A** adb thẳng | `adb connect <ip>:5555` | sau khi bật Local Network (sửa gốc, một lần) |
| **B** cầu `nc`→loopback | `mkfifo /tmp/kachi-adb/f; (nc -l 127.0.0.1 15555 < f \| nc <ip> 5555 > f) &` rồi `adb connect 127.0.0.1:15555` | A hỏng. **Đã dùng thật 2 lượt xe** |
| **C** client adb thô | `python3 /tmp/adb_raw.py <ip> 5555 '<shell>'` | A+B hỏng. [ĐO 18-09] **đường DUY NHẤT vào được hôm đó** |
| **D** app tự chụp | `DiagActivity` · bridge `voice_dump` · `kachi-logs/` kéo qua USB/thẻ | đang cắm CarPlay/AA (đầu xe **tắt Wi-Fi** ⇒ mất adb) |

⚠ **`/tmp/adb_raw.py` CHƯA lưu repo** ([ĐO] còn ở `/tmp`, 3 231 B) — **chép vào USB trước khi đi**. Mất nó là mất đường C.

### 0.4 Mang theo

- Repo ở máy (script đọc `:core` để sinh bảng datum — **phải có repo**, không chỉ APK) · `git status` sạch.
- `adb_raw.py` + `keystore.properties` (nếu định build `vehicleTest` tại chỗ).
- **Người thứ hai** (C3 khi xe lăn bánh · D2 nhìn thân xe · F1 nhìn màn cam).
- Điện thoại có **CarPlay/AA** (E9) · điện thoại quay video màn (đối chứng độ trễ voice).
- IP xe: **hỏi owner tại chỗ**, KHÔNG hardcode vào script/doc (repo public).
- `for f in scripts/vehicle/kachi/*.sh; do bash -n "$f" || echo "LỖI: $f"; done` — chạy ở nhà.

### 0.5 Ba tiền đề bật tại xe (một lần, đầu buổi) — §A làm

1. **Cầu kiểm thử**: Kachi › Cài đặt › Hệ thống & quyền › Nâng cao › *Chế độ kiểm thử qua adb* (**TAY** — không có đường bật bằng broadcast, có chủ ý). **Tự tắt sau 60 phút** ⇒ buổi dài phải bật lại.
2. **Trợ năng phải BOUND, không chỉ ENABLED** — [ĐO xe 18-09] `NavAccessibilityService` **ENABLED mà KHÔNG Bound** ⇒ `onKeyEvent` không bắt ⇒ **phím chết và §F đo ra SAI ÂM**.
3. **`boot_id`** chép ra giấy — chứng cứ độc lập rằng reboot ở §H là THẬT.

### 0.6 Quy ước chung cho cả buổi

```bash
CAR=<ip-xe>; A="adb -s $CAR:5555"                 # hoặc: A="python3 /tmp/adb_raw.py $CAR 5555"
T() { $A shell "am broadcast -a com.byd.launcher.TEST -p com.byd.launcher $*"; }
mkdir -p car-logs && exec > >(tee car-logs/session.txt) 2>&1
```

⚠ **Bẫy dấu nháy đã trả giá**: `adb shell` cắt chuỗi theo khoảng trắng ⇒ `--es text "bật đèn đọc"` chỉ tới `"bật"` mà lời đáp **vẫn `ok:true`**. Nháy **hai lớp**: `"'bật đèn đọc'"` (helper `k_shq`, `_common.sh:303`).

⚠ **Cầu trả lời ở BA nơi, đừng chỉ đọc một** ([ĐO source] `KachiTestBridge` KDoc): `setResultData` (terminal) · `files/test/<mốc>-<lệnh>.json` · `logcat -s KachiTest`. Terminal im mà file/logcat có ⇒ **cầu CHẠY** — kết luận khác hẳn.

⚠ **CẤM `input tap <x> <y>`** ở mọi bước. Màn giữa xe **1920×720**, máy ảo 1080×2340 ⇒ toạ độ đúng ở nhà là cú chạm nhầm hàng trên xe (CLAUDE.md §15 đã trả giá hơn chục vòng).

⚠ **`rc=0` ≠ xe làm.** Bài học 1.38/1.33: `setAutoCleanAirState` trả `rc=0` suốt mà xe **không lọc**. Mọi dòng GHI ở §D chỉ PASS khi có **đọc-lại** hoặc **owner NHÌN**.

⚠ **🚫 `am display move-stack` — CẤM CHẠY cả buổi.** [ĐO source] `CarExecClusterProjectionCatalog.kt:52-80` (`risk = MAY_HANG_SYSTEM`, fieldNote "CẤM DÙNG") + trace `archive/diagnostics/carplay-move-stack-npe-crash-2026-08-01.md`: **treo system_server 3/3 lần trên chính DiLink3**, task biến mất, phiên CarPlay rớt phải **cắm lại cáp**. Gốc AOSP a10 `DisplayContent.java:2401-2402`, không có bản vá. Đường thay thế cùng mục đích = **`am stack move-task`** (§E5), đã proven trên đúng xe này.

---

### 0.7 Artefact prep — ĐÃ DỰNG SẴN (2026-09-19)
- **Release 1.79 (vc80)** OTA: `apk/Kachi-1.79-release.apk` sha256 `1a3de67a…` (xe tự tải khi bật Nav+HUD).
- **vehicleTest 1.79 + diagLog** (debuggable → run-as kill `:tts` + verbose): build `JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:assembleVehicleTest -PdiagLog=true` → `app/build/outputs/apk/vehicleTest/app-vehicleTest.apk` sha256 `7b81350a…` (KHÔNG commit — cài tay cho buổi test, nhớ cài lại release cuối buổi).
- **Đường vào xe**: real adb `~/Library/Android/sdk/platform-tools/adb connect <car-ip>:5555` (ưu tiên) · fallback `scripts/vehicle/kachi/adb_raw.py` (shell PROVEN + push/pull/screencap/install).

## 1. SESSION FLOW — 8 phase, rủi ro tăng dần

**Tổng ~5,5 h.** Cột **outcome→option** là chỗ quyết định: đọc-và-đi, không nghĩ tại xe.
Cột **ID** = mã backlog để đóng 🚗.

### Phase A — Kênh + bản + cầu sống (đọc · 20 phút · chặn mọi phase sau)

| # | ID | Việc | Lệnh chính | PASS / FAIL | Outcome → option | Kết quả |
|---|---|---|---|---|---|---|
| A1 | **BUG2** | adb wireless có chết vì Kachi không (phép thử QUYẾT ĐỊNH đang nợ, tốn 1 phút) | `$A shell am force-stop com.byd.launcher` → thử `adb connect $CAR:5555` lại | adb **sống lại** ⇒ Kachi chiếm adbd (H1/H2/H3) · **vẫn chết** ⇒ có thứ **bền** bị đổi (H4) | sống lại ⇒ ghi + giảm footprint sau; chết ⇒ chuyển **đường C** (`adb_raw.py`) rồi đi tiếp, **đừng dừng buổi** | |
| A2 | — | Bản = 1.79 (80)? | `$A shell "dumpsys package com.byd.launcher \| grep -E 'versionName\|versionCode'"` | `1.79`/`80` | thấp hơn ⇒ `40-ota.sh` NGAY; OTA hỏng ⇒ cài tay `apk/Kachi-1.79-release.apk` | |
| A3 | **T-BRIDGE(a)** | Shell ROM BYD gửi được vào receiver `exported`? | `T --es cmd state` → nếu chối: `$A shell "am broadcast -n com.byd.launcher/.launcher.testbridge.KachiTestBridge -a com.byd.launcher.TEST --es cmd state"` → nếu im: `$A shell "logcat -d -s KachiTest"` + `ls files/test/` | JSON về (bất kỳ nơi nào trong ba nơi) | `test_mode_off` ⇒ bật công tắc TAY (§0.5.1) · **cả ba im** ⇒ **kết luận lớn**: mọi script tự động mất hiệu lực ⇒ chuyển **đường tay** của từng bước (mỗi bước đều có) + chép nguyên câu lỗi | |
| A4 | **W5-tiền-đề** | Trợ năng **BOUND** (không chỉ ENABLED) | `$A shell "dumpsys accessibility \| grep -A6 -iE 'Bound services\|Binding services'"` | thấy `NavAccessibilityService` trong **Bound** + `capabilities=9` (có `FILTER_KEY_EVENTS`) | **ENABLED mà không Bound** ⇒ Cài đặt › Phím vô-lăng › **Kiểm tra / Sửa ngay** (1.78 nới 9s→20s — **chính §F đo hộ bản vá này**); vẫn không ⇒ toggle danh sách trực tiếp (bỏ Kachi → thêm lại), [ĐO 18-09] Bound NGAY cả khi load 14 | |
| A5 | — | Baseline + chứng cứ reboot | `$A shell "cat /proc/sys/kernel/random/boot_id; uptime"` · `$A shell "cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME"` · `T --es cmd prefs --es file kachi_workspace` (lọc `keep_home_on_boot\|home_chosen\|launcher_autostart`) | ba dòng chép ra giấy | không có ⇒ cuối buổi **không ai chứng minh được HOME đã đổi** (§H) | |
| A6 | **L-RE** | `featmap` của xe HÔM NAY | `T --es cmd featmap` rồi `diff` với `oncar-trace-2026-09-16b/featmap-20260916.json` | diff rỗng ⇒ dùng ma trận §D nguyên trạng | **khác** ⇒ tin tệp MỚI, tra lại id của dòng liên quan trước khi bắn (hằng feature-id gán **lúc chạy** theo `isCanFD`/`isToyota`; OTA có thể đổi) | |

⚠ A6 mở khoá toàn bộ §D: [ĐO source] stub SDK `BYDAutoFeatureIds` chỉ tra ra **3/38** `bindingKey` số của repo; `featmap` 09-16 (**7193** tên→id, **47** device của chính xe owner) giải xong **35 id còn lại** ⇒ ma trận §D là `[ĐO source]` trên dữ liệu xe thật, không phải phỏng đoán.

### Phase B — Đọc an toàn (0 rủi ro · 60 phút)

⚠ **B1 phải chạy TRƯỚC mọi bước nhồi ô** (B6 đặt `coolant_temp`, E1 nhồi VietMap/YouTube) — nó cần **bố cục MẶC ĐỊNH + 5 phút đứng yên**. Đổi thứ tự là mất mốc so sánh với [ĐO xe 1.66].

| # | ID | Việc | Lệnh chính | PASS / FAIL | Outcome → option | Kết quả |
|---|---|---|---|---|---|---|
| B1 | **K5** | Tải hệ thống sau 1.67: HAL/phút · CPU · RSS · log | màn chính, bố cục mặc định, **KHÔNG chạm 5 phút** → `$A shell "logcat -d -v time -s KachiPerf" \| tail -10` | HAL đọc **< 150/phút** · `no permission` **< 20/phút** · CPU idle **< 2 %** (8 lõi) · log **< 20 KB/phút** (mốc 1.66: 510 · 229 · 6,6 % · 79 KB) | dòng perf **rỗng** ⇒ nhớ: nó **chỉ in khi màn chính RESUMED** ([ĐO source] `KachiHomeActivity.kt:181-190,438,457`) ⇒ **đường B** `top` (bên dưới), đừng kết luận "tải 0" | |
| B1b | **K5** | Đường B — `top` + `meminfo` **HAI tiến trình** | `$A shell "top -b -n 2 -d 5 \| grep -E 'byd.launcher\|load average'"` · `$A shell "dumpsys meminfo com.byd.launcher \| grep 'TOTAL PSS'"` · **và** `…com.byd.launcher:tts \| grep 'TOTAL PSS'` | 2 dòng launcher; **chép kèm `load average`** | `:tts` PSS **> ~100 MB** ⇒ nó đang nạp model NGHE 74 MB ⇒ `isTtsProcess()` hỏng ⇒ **[P0]**, chép `logcat -s KachiVoiceRec`. Đọc gộp một số = **số bịa** ([ĐO source] `meminfo <pkg>` KHÔNG gộp `:tts`) | |
| B2 | **P8** | **7** điều kiện quyền (không phải 5–6) — app nói phải KHỚP hệ thống | 7 lệnh đọc: `settings get secure enabled_notification_listeners` · `… enabled_accessibility_services` · `… accessibility_enabled` · `appops get com.byd.launcher SYSTEM_ALERT_WINDOW` · `settings get global enable_freeform_support` · `dumpsys package com.byd.launcher \| grep -i RECORD_AUDIO` · `cmd package resolve-activity --brief … HOME`; rồi ĐỌC 7 hàng trong app + chụp ảnh | 7/7 **khớp** hai vế; toast *"Kênh điều khiển cửa sổ…"* ≤ **1 lần / 3 lượt mở** (U8b); thiếu quyền **KHÔNG chặn** launcher | lệch một hàng ⇒ chép **cả hai vế** (chỉ so mới thấy, đó là cả mục đích P8). Hàng `UNKNOWN` mà app nói "thiếu" ⇒ bug hiển thị | |
| B3 | **H1-T2b** | Grab-list ĐỌC **26 nút** chưa có đường đọc (R1–R30 của `1-hal.md` §3.1) | `T --es cmd hal --es op get --es dev <Device> --es m <getter> --es args <csv>` (26 lượt, **PHẢI** có `--es dev`) | giá trị trong dải enum đã khai | `unavailable: null` ⇒ **chưa kết luận được**: gõ lại với `--es dev` = trường `device_by_map` trong lời đáp. [ĐO] lượt sweep gạt mưa 09-16 fail vì **sai device** (mặc định `BYDAutoBodyworkDevice`, `TestBridgeHal.kt:41`), KHÔNG vì id vắng · `sentinel:true` ⇒ xe **không có** ⇒ gỡ/ẩn, đừng vá tiếp | |
| B4 | **W1** | 5 datum `NEEDS_CAR` — cả 5 **đã có đường đọc** | 4 GPS: `--es op getid --es dev BYDAutoLocationDevice --es m 311087\|965600\|689157\|639440` · `target_soc`: `--es op get --es dev BYDAutoSettingDevice --es m getSOCTarget` | lat/lon hợp lý · alt ≠ 8001.0 · heading ≠ 360.0 · SOC 15..70 | ⛔ **TUYỆT ĐỐI KHÔNG** bắn `LOCATION_*_SET`/`setLocationInfo` (ghi vị trí VÀO xe — họ mock-location đã bị gỡ khỏi dự án). Đọc để đo là một chuyện; **nối vào UI = quyết định owner** (`1-hal.md` §3.3) · `target_soc` route `None` ⇒ **sửa được off-car ngay**: `bindingKey` UPPER_SNAKE → `"BYDAutoSettingDevice.getSOCTarget"` | |
| B5 | **W1·W4** | Số thật lốp/nhiệt + kiểm mắt 4 bề mặt | `20-datums.sh <ip>:5555` sinh bảng → đặt lên màn: ô **Tốc độ** · ô **Áp suất lốp** · nhóm **Kính** · một **chip** thanh trên | mỗi cái ra số **≤ 10 s** | có ô thành `—` ⇒ **[P0] cổng H1 cắt mất dữ liệu**, chụp ảnh + ghi mã datum. Ngưỡng lốp (non<2.0 · căng>3.2 · lệch≥0.3 bar) là **số tự chọn** ⇒ số thật ở đây mới chốt được (W4 OQ1) | |
| B6 | **K8c** | `HalAbsentCache` đóng rồi `clear()` có chỗ gọi thật | đặt `coolant_temp` lên màn (xe BEV ⇒ [SUY] null mãi) → đọc `KachiPerf` 3 nhịp → bấm Home ra/vào → đọc lại | cột **`bỏ-xe-không-có` > 0** rồi **về 0 một nhịp** sau khi ra/vào | **không bao giờ về 0** ⇒ [P2] datum vắng-rồi-có sẽ câm tới 10 phút. ⚠ **Bài test cũ chạy KHÔNG được**: `perf-profile-2026-09-16.md` §9.3 mục 7 dùng ô **"ETA sạc"** — 8 datum sạc **đã xoá** ((V) FEATURE-FILTER, `TelemetryRegistry.kt:160-161`) ⇒ dùng đường này | |
| B7 | **H1·U8a·F** | 4 probe chỉ-đọc quyết định cả cây phase E/F | `dumpsys package com.byd.launcher \| grep -E 'userId=\|sharedUser'` · `dumpsys package com.waze \| grep -icE 'allowEmbedded'` · `dumpsys package \| grep -A3 'permission android.permission.ACTIVITY_EMBEDDING'` · `pm list packages \| grep -i wazemod` | ghi 4 số | `userId=1000` ⇒ **option B thắng** ⇒ **BỎ E2–E7**, làm ô bằng đường nhúng thẳng, kết thúc mảng ([ĐO] 09-14/15 uid **10135**/**10138**, chữ ký `177b2fc5` ≠ platform ⇒ dự kiến loại) · `ACTIVITY_EMBEDDING` **không** `signature` ⇒ ưu tiên **option H1** (mod manifest) lên trước E4 · có `wazemod` ⇒ E7 rẻ, làm sớm | |

### Phase C — Voice KHÔNG ghi xe (50 phút)

**Vì sao đứng trước F**: mọi bước dưới đây chạy bằng cầu `say` (**bơm CHỮ**, không cần mic, không cần phím) ⇒ tách được lỗi *dispatch* khỏi lỗi *ASR* khỏi lỗi *phím*. Doc `3-voice.md` xếp `:tts` trước vì lo "phím chết thì không nói được" — `say` đã giải xong lo đó. Vài câu bằng **mic thật** vẫn phải nói (C1b) để đo ASR.

| # | ID | Việc | Lệnh chính | PASS / FAIL | Outcome → option | Kết quả |
|---|---|---|---|---|---|---|
| C0 | — | **Biến dùng chung: mạng xe ra internet được không** (chặn Nominatim *và* resolver YouTube cùng lúc — đo MỘT lần) | `$A shell "ping -c 2 -W 3 nominatim.openstreetmap.org"` · `$A shell 'toybox wget -q -O - "https://nominatim.openstreetmap.org/search?format=json&limit=1&q=ben+thanh" 2>&1 \| head -c 200'` | có lat/lng | ⚠ `toybox` Android 10 có TLS **[CHƯA BIẾT]** ⇒ wget rỗng thì **đừng kết luận**, dùng log app (C1) làm nguồn sự thật · **mạng KHÔNG ra** ⇒ C1 bắt buộc đi **option B1/B2/B3** (0 mạng) và C2 chỉ còn **option C** | |
| C1 | **V1·VietMap** | Dẫn đường thật — option **A** (đường 1.79: Nominatim → `vietmaplive://…lat&lng`) | `$A shell logcat -c` → `T --es cmd say --es text "'dẫn đường tới chợ Bến Thành bằng VietMap'"` → `$A shell "logcat -d \| grep -E 'ActivityTaskManager: START\|vietmaplive\|google.navigation' \| tail -5"` | `dat=vietmaplive://companion/navigation?lat=10.77…&lng=106.69… pkg=vn.vietmap.live` **và VietMap dẫn thật** | ⇒ **A ĐẠT**, đổi `coordEvidence` VietMap `AWAITING_CAR`→`MEASURED`, đóng nợ 1.74→1.79 | |
| C1a | | Geocode đi đường NÀO (phân biệt A vs A′ vs treo) | `$A shell "logcat -d \| grep -E 'KachiVoiceGeo\|quá hạn\|máy chủ tra cứu\|Geocoder của máy' \| tail -10"` (tag `KachiVoiceGeo`, Log.i/w ⇒ **không cần verbose**) | không dòng lỗi + có lat/lng | *"Geocoder của máy không trả lời"* ⇒ A′ chết, đã lùi Nominatim (bình thường, xe không GMS) · *"quá hạn 7000ms"* ⇒ **A′ TREO** ⇒ bỏ nhánh on-device / hạ hạn · *"máy chủ tra cứu trả &lt;mã&gt;"* ⇒ mạng ra được mà bị chặn | |
| C1b | | VietMap mở mà **KHÔNG dẫn** ⇒ 4 option CHỮ, mỗi cái 1 lệnh (**bỏ hẳn geocode** nếu ăn) | **B1** `am start -a android.intent.action.SEND -t "text/plain" -p vn.vietmap.live --es android.intent.extra.TEXT "chợ Bến Thành"` · **B2** `… VIEW -p vn.vietmap.live -d "https://www.google.com/maps/search/?api=1&query=<urlencode>"` · **B3** `…/maps/dir/?api=1&destination=<urlencode>` · **B4** `-d 'vietmaplive://companion/navigation?searchText=…&startNavigation=true'` (thử 4 khoá `searchText\|keyword\|query\|q`) | VietMap hiện **ô tìm / điểm đến = đúng chữ** | **B1 ĐẠT = thắng lớn**: đổi VietMap `OpenOnly`→`VoiceLaunch.Action(ACTION_SEND)`, **gỡ `needsCoords`**, hết ngoại lệ mạng · B2/B3 đạt ⇒ thêm `VoiceLaunch.Uri` · **cả 4 chỉ mở app trơn** ⇒ filter có mà không có màn xử lý ⇒ giữ A + giữ câu nói-thật `navNoPlace` · tất cả chết ⇒ giữ **C** (GMaps passthrough `google.navigation:q=`), nói rõ với owner *"cái nào ra cái đó"* phải nhượng bộ ở VietMap | |
| C2 | **V1·nhạc** | YouTube / YT Music **phát thật** | `$A shell logcat -c` → `T --es cmd say --es text "'phát bài Diễm Xưa trên YouTube Music'"` → `$A shell "logcat -d \| grep -E 'YtResolve\|ActivityTaskManager: START' \| tail -8"` | `START dat=https://music.youtube.com/watch?v=…` | `YtResolve HTTP <mã>` / *"quá hạn 7000ms"* ⇒ **mạng xe là nút thắt** (không phải code) ⇒ nới hạn / giải id lúc rảnh / chấp nhận option C | |
| C2a | | Tách resolver khỏi deep-link: bắn id **đã biết** | `$A shell "cmd package resolve-activity -a android.intent.action.VIEW -d 'https://music.youtube.com/watch?v=R0mpl0Az4Q8'"` → `am start -p com.google.android.apps.youtube.music -d <cùng URL>` → `sleep 6; dumpsys media_session \| grep -E 'state=PlaybackState\|package=' \| head -6` | `state=3` (PLAYING) | `state=3` mà C2 báo lỗi resolver ⇒ **deep-link đúng, mạng sai** · `resolve-activity` **không ra activity** ⇒ bản YT Music trên xe không nhận watch-URL ⇒ thử **B** (`www.youtube.com/watch`) rồi **D** `am start -d "vnd.youtube:R0mpl0Az4Q8"` · D ăn ⇒ thêm `vnd.youtube:` làm `watch` của YouTube (**1 dòng dữ liệu**) · cả A/B/D chết ⇒ giữ **C** `MEDIA_PLAY_FROM_SEARCH` + đổi mặc định sang **YT Music** | |
| C2b | | Bản app trên xe (vì sao khác máy ảo 2019) | `$A shell "dumpsys package com.google.android.apps.youtube.music \| grep versionName \| head -1"` (+ `com.google.android.youtube`) | chép số | — | |
| C3 | **K8a** | ⚠ **Bước DUY NHẤT cần xe LĂN BÁNH** — hỏi bằng giọng một datum **KHÔNG có trên màn** phải ra số THẬT | màn về mặc định (**không** có ô Tốc độ) → xe chạy ở tốc độ X1 → `T --es cmd say --es text "'tốc độ bao nhiêu'"` → đổi sang X2 (chênh ≥ 15 km/h) → hỏi lại | **hai số khác nhau**, khớp đồng hồ ±2 km/h | **một số đứng im** giữa hai lần ⇒ **[P1]** đường đọc bằng giọng đang đọc **ảnh chụp cũ** của vòng poll (đúng thứ cổng H1 vừa lọc) ⇒ chép cả hai lượt + tốc độ thật. Nếu không thể lái: hoãn C3 sang lượt chạy về, **đừng bỏ** | |
| C4 | **Hey Kachi** | Load xe trong 4 trạng thái dùng thật (input cho §G, và giải thích Piper chậm + rớt bind) | `for S in idle vietmap gmaps-in-slot gmaps+vietmap; do $A shell 'sh -c "for i in $(seq 1 60); do cut -d\" \" -f1 /proc/loadavg; sleep 1; done"'; done` · `$A shell "grep -c processor /proc/cpuinfo"` | đếm: có chuỗi **≥3 mẫu liên tiếp < 4.0**? có mẫu **> 6.0**? | **0 chuỗi < 4.0** (dự kiến — [ĐO 18-09] load 10.71/14.45/17.02, nhẹ nhất 10) ⇒ **guard sai chuẩn, không phải xe sai** ⇒ làm **option A: chia số lõi** (14/8 = 1.75 < 4.0) TRƯỚC khi tốn công host model · < 4.0 lúc `idle` nhưng > 6.0 khi GMaps-in-slot ⇒ A + nói rõ giới hạn (option F: chỉ chạy khi xe rảnh) | |

⚠ **C4 huỷ Phase 2 của `oncar-runbook-hey-kachi.md`** — không phải bỏ qua, là **âm tính giả**. Chuỗi đóng kín [ĐO source]: `VoiceWakeListener.kt:286` đọc load1 **THÔ** (không chia lõi) → `VoiceWakeController.kt:25` dựng `VoiceLoadGuard()` **mặc định, KHÔNG có núm pref** → `VoiceLoadGuard.kt:59-61` `suspendAbove=6.0` → `VoiceWakeController.kt:48` `return Frame.SUSPENDED` ⇒ nhả mic, **0 inference** ⇒ nói "Hey Kachi" 20 lần ra **0/20 bất kể model tốt đến đâu**, mất cả buổi mà không học được gì.

### Phase D — GHI vào xe (HAL sweep · 70 phút · **xe ĐỖ, có người NHÌN**)

**Giao thức**: mỗi dòng = `setev`/`set` rồi **đọc-lại** (hoặc owner NHÌN). Không đọc lại ⇒ dòng đó **chưa PASS**.
**Lệnh khung** (mọi lượt GHI cần `--ez auto_confirm true`; mọi lượt **PHẢI** có `--es dev`):

```bash
T --es cmd hal --es op set   --es dev <Device> --es m <setter> --es args <csv> --ez auto_confirm true
T --es cmd hal --es op setev --es dev <Device> --es m <id|TÊN_HẰNG> --es args <v> --ez auto_confirm true
T --es cmd ctl --es id <controlId> --ei v <n> --ez auto_confirm true   # đi ĐÚNG đường một cú chạm ô nút
```

| # | ID | Việc | Giá trị ứng viên (sweep TỪNG cái) | Đọc-lại / PASS | Outcome → option | Kết quả |
|---|---|---|---|---|---|---|
| D1 | **W3·L-RE** | `ac_auto` — feature `1324355606` **KHÔNG CÓ trên xe** ([ĐO source] `featmap` → `dev=[]`) | **A** `setAcControlMode` args `0,0` rồi `0,1`; `bad_args` ⇒ đảo `0,0`/`1,0` · **B** `setev AC_CTRL_MODE_SET`=**501219352** giá trị `0`(AUTO)/`1` | `getAcControlMode` → AUTO=**0** · MANUAL=**1** ([ĐO xe 09-16] đúng chiều này) | A xanh ⇒ bỏ feature-id, dùng named-method + chốt arg-order · A câm, B xanh ⇒ đổi `bindingKey`→`501219352`, **giữ `readInverted`** · **C** `setACAutoAir` là *kiểu gió AUTO*, **KHÁC việc** ⇒ chỉ ghi nhận, **không** map vào `ac_auto` | |
| D2 | **L-RE** | `fan` · `recirc` · `defrost` · `defrost_rear` · `temp` (id ĐÚNG, thêm đường named) | `fan` 0/1/4/7 · `recirc` IN=**1**/OUT=**0** · `defrost` `setAcDefrostState 1,<0\|1>,0` · rear `2,<0\|1>,0` · `temp` `setAcTemperature 0,<17\|22\|33>,0,1` | `getAcWindLevel` · `getAcCycleMode` · `getAcDefrostState 1\|2` · `getTemprature 1` | vì sao vẫn sweep khi cả 5 báo ✅ 09-15: `defrost` báo ✅ mà **đọc lại = 0** ⇒ cần một lượt **ghi→đọc-lại khép kín** để biết ô có nói đúng trạng thái không | |
| D3 | **W2** | `seatc`/`seath` — **điểm đo THỨ HAI cho thang mức** (ghế sưởi còn [SUY]) | **A** `setSeatVentilatingState` `1,1`→`1,2`→`1,3`→**`1,4`** · **A′** `setSeatHeatingState` cùng dải · **B** `setSeatHeatingState1` `1,1`/`1,2` | `getSeat*State 1` sau MỖI lượt **+ owner đọc mức trên màn BYD** | raw `4` trả sentinel/không đổi ⇒ trim **3 bậc** ⇒ **sửa `ControlLevels` `seath` `listOf(1,2,3,4)`→`listOf(1,2,3)`** ([ĐO source] `SEAT_HEATING_HIGH=3`, khung chỉ 3 bậc — mâu thuẫn đang chạy) · A′ câm mà B chạy ⇒ trim dùng biến thể `…State1` · cả hai câm ⇒ đọc 4 cờ `SET_HAS_*_SEAT_VENTILATING` xem xe có ghế nào | |
| D4 | **W2** | `readl` (đèn đọc) — on/off tay ❌ trên xe, chế-độ-theo-cửa ✅ | **A** `setev 1330643002` `1`(OFF)/`2`(ON) · **B** `setInsideLightDoorState` `1`(DOOR_OPEN)/`2`(DOOR_CLOSE) | `getid 1330643002` · `getInsideLightDoorState` | A vẫn ❌ với id+enum ĐÚNG ⇒ là **quyền/gate**, không phải giá trị · **B là nhánh owner báo ✅** ⇒ chốt: `readl` nên thành **SELECT 3 trạng thái** (tắt/bật/theo-cửa), không phải TOGGLE | |
| D5 | **L-RE** | 8 nút enum đã chốt ở nguồn — chỉ xác nhận | `drl` 1/2 · `steer_heat` 1/2 · `powertrain_mode` EV=1/HEV=3 (+2/4/5) · `regen_level` 1/2/0 (**enum [CHƯA BIẾT]**) · `wireless_charge` 1/2 · `anion` `setev 1337982994` 1/2/0 · `pm25` `setAutoCleanAirState` 1/0 · `pm25_clean_now` `setQuickCleanAirState 1` | getter tương ứng; `pm25` đọc `getid AC_AUTO_CLEAN_AIR`=1301291046 | ⚠ `pm25`: 1.38 đã biết **rc=0 mà KHÔNG lọc** ⇒ đọc lại BẮT BUỘC + nghe tiếng quạt | |
| D6 | **W2 ⭐** | **Kính ½** — ba đường độc lập, đo hết trong một buổi (câu hỏi W2 gốc: *"có set % không hay phải dừng-giữa-hành-trình"* → [ĐO source] **có CẢ BA**) | **A** enum `setBodyWindowCtrlState 1,4` (`WINDOW_OPEN_HALF`) · **B** percent THẬT `setev 1276219408` = `50` rồi `25`,`75` · **C** mở `1,1` → đợi 1 s → `1,3` (`WINDOW_STOP`) · **D** `setAllWindowState 4,4,4,4` | `getWindowOpenPercent 1` → **≈50** (B: đúng số đã gửi) + **owner NHÌN kính** | A xanh ⇒ `writeArgs` hiện tại **ĐÚNG**, đóng W2 không sửa gì · A câm/B xanh ⇒ đổi `win_*` mức 2 sang `setev TARGET_POSITION_SET=50` · A+B câm/C xanh ⇒ nửa chỉ làm được bằng **mở rồi STOP** = đổi **kiến trúc** (nút phải giữ timer) ⇒ **ghi backlog, KHÔNG tự làm** · cả ba câm ⇒ đọc `getWindowPermitState`: xe **chặn** điều khiển kính từ app ⇒ dừng, gỡ nhãn "Nửa" | |
| D7 | **P1** | ⚠ `sunroof` — **BUG chứng minh được KHÔNG CẦN XE**: đang gửi "mở 1%" thay vì "đóng" | `setMoonRoofState` `0`(đóng) · `50` · `100` · `254`(stop) · `252` · `253`; thêm `10` (< `MOONROOF_MIN=21`) để biết sàn thật | `getSunroofPosition` + `getSunroofState` + **NHÌN nóc** | xanh ⇒ `writeArgs sunroof` → 0/100 (+nửa 50). [ĐO source] thang là **PERCENT** (`bodywork:338-344` `CLOSED=0·MIN=21·OPEN=100·COMFORT=252·BREATH=253·STOP=254`), chốt bằng UI BYD `SunRoofFragment.java:775-1109`; chú thích repo viện dẫn `CarControlImpl.java:1503` là **hàm chuyển tiếp thuần**, không chứng minh gì ⇒ [SUY] bị ghi thành [ĐO]. ⚠ `sunroof_pos` cũng sai đơn vị: khai `"%"` mà getter trả **enum 0..6** ⇒ hé nửa hiện **"3 %"** | |
| D8 | **W2** | `sunshade` (rèm) + `trunk` (cốp) **mở-theo-chiều-cao** (mới tìm ra) | rèm **A** `setev 1330642984` 0/50/100 · **B** `setSunshadeState` + **`254`** (STOP — chữa đúng *"bấm giữ chưa được"* 09-15) · cốp **A** `voiceCtlBackDoor` 1(mở)/3(đóng) + thử `2`,`4` lúc cốp ĐANG chạy · **B** `setBackDoorOpenedHeight` 15/50/80/100 | `getSunroofWindowblindPosition` · `getBackDoorOpenedHeight` ([ĐO xe] đọc **80**) | B cốp xanh ⇒ **cốp mở nửa làm được** (chưa nút nào dùng) · ⚠ `getBackDoorElectricMode()` = **65535** = `LINK_ERROR` [ĐO xe 09-16] ⇒ **đừng nối** chế-độ-cốp-điện · `tailgate_position` id `1074790456` **không có trên xe**, id thật **1074790408** (lệch 48) | |
| D9 | **W2** | `lock`/`door`/`child_lock` — `BYDAutoDoorLockDevice` **KHÔNG có setter nào** ([ĐO source] chỉ `getDoorLockStatus`) ⇒ `bindingKey` hiện tại **chắc chắn `NoSuchMethod`** | **A** `setDoorLock` 1(UNLOCK)/2(LOCK) · **B** `setev SET_CAR_DOOR_LOCK_SET`=515647 · **C** per-door `setev 960495668\|670\|672\|674\|676` · **D** child `setev 1276141584\|1276141586` | `getDoorLockStatus 1..7` + **tiếng khoá** | ⚠ **An toàn**: chỉ khi xe đỗ, **người ngồi trong**, cửa đóng; mở khoá rồi **khoá lại NGAY** cuối mỗi lượt · `hood`: `BODYWORK_CMD_DOOR_HOOD` chỉ là **area ĐỌC**, không có lệnh mở ⇒ **KHÔNG sweep** | |
| D10 | **W2** | **Bảo trì gạt mưa** — đã tìm ra ĐÚNG đường (KHÔNG ở Wiper device: nó có **3 getter, 0 setter**) | **0** đọc cờ trước: `getid SET_HAS_FRONT_WINDSCREEN_WIPER_OVERHAUL` = **−951058404** · **A** `setWindscreenWiperOverhaulState 1,1` (vào) → NHÌN cần dựng → `1,2` (thoát) · **B** `setev 1330642972` 1/2 | `getWindscreenWiperOverhaulState 1` | A xanh ⇒ nút mới *"Bảo trì gạt mưa"*, **đóng W2** · thứ tự tham số đã chốt bằng call-site THẬT của BYD `FrontWiperRepairModel.java:61` → `(1, z?1:2)` · **C** `setAutoRainWiperState` là *gạt tự động theo mưa*, **KHÁC** bảo trì · ⚠ `getWindscreenWiperResetState(1)` trả **−10011** ổn định 22/22 mẫu — **không** phải sentinel dự án ⇒ [CHƯA BIẾT], đọc lại kèm `--es dev BYDAutoWiperDevice` để loại giả thuyết sai-device | |
| D11 | **L-RE** | Đèn viền cabin — **cả 4 feature-id đang dùng KHÔNG CÓ trên xe** ([ĐO source] `<NO NAME>`, `dev=[]`) ⇒ 4 nút này **chưa bao giờ chạm được xe** | `ambient_power` **A** `setAirLightPanelState` 1/2 · **B** `setev 1276153872` · `ambient_brightness` `setIALBrightness 3,0,0`→`3,3,0`→`3,5,0` (**dải thật 0..5**, repo khai 0..10) · `ambient_color` `setIALColor 3,1,0`…`3,7,0` (**7 màu 1..7**, repo khai 5 nhãn index 0..4 + `0`=INVALID) | `getAirLightPanelState` · `getIALBrightness` · `getIALColor` | xanh ⇒ sửa 2 sai lệch đã chứng minh off-car: trần độ sáng **10→5**; 5 nhãn (có *xanh lá*, *vàng* — **xe không có**) → **7 nhãn 1..7**, +1 như CarSettings (`AmbientLightColor.java:109`) · `ambient_music` **không tìm ra setter nào** ⇒ [CHƯA BIẾT], chỉ đọc cờ `1121976383`, **đừng đoán** | |
| D12 | **W5 Q3** | `camera_view` — `setDisplayMode` **không tồn tại** trên DL3 ⇒ route chết. Đường mới giải xong TODO *"map nhãn↔enum"* | **A** `setev PANORAMA_OUTPUT_STATE_SET`=**1306529808** dev **1031**, giá trị OFF=1·FRONT=**2**·REAR=**3**·LEFT=**4**·RIGHT=**5**·COMPOSE=6·F_WIDE=**12** · **B** `setAPAAvmMode` 1..6 · hoặc `T --es cmd ctl --es id camera_view --ei v 0..5` (**KHÔNG cần `auto_confirm`** — `camera_view` không thuộc `CtlSafetyPolicy.CONFIRM_REQUIRED`) | `getPanoOutputState` + **NHÌN màn cam**; lập bảng `v → góc` | 5 nhãn *Trước/Sau/Trái/Phải/Rộng* khớp đúng `2/3/4/5/12` ⇒ hết phải gửi **index thô** · lời đáp `ctl` có `accepted`+`hal_line` ⇒ phân biệt *"HAL nhận mà hình không đổi"* vs *"HAL từ chối"* — thứ bấm tay **không** cho biết · **OQ1** *"thân xe trong suốt"* nằm trong bảng này; không có giá trị nào cho ra ⇒ ghi [CHƯA BIẾT] | |
| D13 | **L-RE2** | **HUD — cặp trùng id là BUG ĐANG CHẠY** | `hud_switch` `setev 1276174371` 0/1/2 · `hud_brightness` **A** `setev SET_HUD_MODE_SET`=**1276174373** 0..3 · đọc `SET_HUD_CONFIG`=951058453 · `brightness_gear` `setev 1276174360` 0/5/10 | `getid 951058460` · `getid 951058445`; `brightness_gear` **NHÌN màn có đổi sáng** | ⚠ `1276174360` = `SET_BRIGHTNESS_GEAR_SET` = **độ sáng MÀN CHÍNH**, và `hud_brightness` đang dùng **cùng id** ⇒ kéo "Độ sáng HUD" đang đổi độ sáng màn hình. Xe chỉ có **5 id HUD, không có id brightness nào** (verify 2 lần) ⇒ `SET_HUD_MODE_SET` xanh ⇒ đổi thành SELECT *"Chế độ HUD"*; câm ⇒ **gỡ nút** | |
| D14 | **L-RE2** | 3 nút còn sai giá trị/route | `screen_rotation` `setev 1330643005` **1**(HORIZONTAL)/**2**(VERTICAL) — repo gửi index **0/1**, `0` không tồn tại · `cluster_music` `setev 1138753546` **`--es dev BYDAutoInstrumentDevice`** (route đang sang Setting 1023) 0/1/2 · `seat_memory` `setev 1276186678` 1/2/3 | getter/`getid` tương ứng | ⚠ `seat_memory`: **ghế DI CHUYỂN** — không ai ngồi ghế lái lúc bắn · 4 dòng chỉ thiếu `halDevice` (**không đổi id**): `wiper`→Wiper(1046) · `cluster_music`→Instrument(1007) · `mirror_fold`→1047 · 4 datum IAL→Setting(1023) | |
| D15 | — | Thăm dò (chỉ ĐỌC, **không làm nút trong buổi này**) | `getid SET_FRONT_LEFT_SEAT_MASSAGE_CONFIG`=1335885879 · 4 datum nhiệt lốp `getid 1246797848\|60\|72\|84 --es dev BYDAutoInstrumentDevice` | có/không | ghế **massage** = khả năng xe có mà launcher chưa hề có ⇒ làm nút = **quyết định owner** · 4 datum nhiệt lốp có id ĐÚNG + Domain ĐÚNG ⇒ chưa rõ vì sao vẫn `NEEDS_CAR`, **một lượt `getid` là chốt được** | |

**Ưu tiên nếu hết thời gian** (cắt từ dưới lên): D6 kính ½ → D3 thang ghế sưởi → D10 bảo trì gạt mưa → D7 nóc → D1 ac_auto → D9 khoá → D11 ambient → D12 camera → D13 HUD → D15.

⚠ **`71-hal-sweep.sh` KHÔNG tự bắn D6–D9** — `DENYLIST` của nó (`lock door trunk hood sunroof sunshade window windows_all win_*`) khớp `CtlSafetyPolicy.CONFIRM_REQUIRED` và có bài canh giữ hai bên khớp. Đó là **đúng thiết kế**: thân xe bấm tay. Script lo §D1–D5 + D11–D14; **§D6–D9 gõ tay theo bảng trên**.

### Phase E — Cast + app vào ô (60 phút · **rủi ro cao nhất: chạm cụm trước mặt người lái**)

**Câu hỏi của phase này KHÔNG phải "ô có chạy không"** — ô đã chạy trên xe ([ĐO source] `session-findings.md`: *"VietMap chạy trong ô, YouTube phát MV trong ô"*; `oncar-trace-2026-09-16.md` §1: *"YouTube trong `kachi-slot-0`, display 7, 1673×935"*). Câu hỏi hẹp hơn: **app có activity trung chuyển (Waze) thì làm sao**.

**Gate chặn Waze — một dòng điều kiện, bốn cửa thoát** ([ĐO source] AOSP `android-10.0.0_r47` `ActivityStackSupervisor.java:349-367,1084-1136`):
`display == DEFAULT_DISPLAY` → TRUE ngay (**cửa #1** = option C) · caller có `INTERNAL_SYSTEM_WINDOW` → TRUE (**cửa #2** = vì sao shell uid 2000 mở được) · `TYPE_VIRTUAL && displayOwnerUid != SYSTEM_UID && != app.uid` → cần `FLAG_ALLOW_EMBEDDED` **VÀ** `ACTIVITY_EMBEDDING` (**cửa #3** chủ = SYSTEM = option B/G · **cửa #4** = option A/H).
Chuỗi thật của Waze: `am start --display <vd>` từ shell ✅ lên màn ảo → **2,4 s sau** Waze **tự** mở `.MainActivity` từ uid riêng ⇒ `Failed to put TaskRecord{…} on display N` ⇒ **cả task rơi về display 0 toàn màn**.

| # | ID | Việc | Lệnh chính | PASS / FAIL | Outcome → option | Kết quả |
|---|---|---|---|---|---|---|
| E1 | **H2** | Ô hiện trạng còn tốt không (KHÔNG phải Waze) + đếm màn ảo | đặt VietMap + YouTube vào 2 ô → `$A shell "am stack list \| grep -iE 'vietmap\|youtube\|displayId'"` · `$A shell "dumpsys display \| grep -c kachi-slot"` | số `kachi-slot-*` **= số ô App**, không dư; app render thật | dư VD ⇒ [P1] rò vòng đời (H2 vá 1.53 chưa verify xe) · nhãn *"App đã đóng"* phải hiện ≤ 10 s sau khi giết app trong ô (`SlotLiveness` 5 s × 2 nhịp) · **ô hỏng ⇒ chữa trước, mọi bước sau vô nghĩa** | |
| E2 | **H1** | Waze vào ô — **tái hiện triệu chứng** (mốc [ĐO] để so) | đặt Waze vào ô → `$A shell "logcat -s ActivityTaskManager \| grep -i 'Failed to put'"` · `am stack list` ở **T+3 s và T+10 s** + 1 ảnh | — | **Ở LẠI trong ô** ⇒ 🎉 gate không nổ trên ROM này ⇒ **BỎ E3–E7**, chỉ ghi lại · bị đẩy ⇒ đi tiếp | |
| E3 | **H1-G1** | ⭐ **Waze lên CỤM** — phép thử rẻ nhất có khả năng **đổi cả thiết kế** | bật Cast (Kachi › Cài đặt › Chiếu màn lên cụm) → chiếu **Waze** → `$A shell "am stack list \| grep -iE 'waze\|displayId'"` · `logcat -d -s ActivityTaskManager \| grep -i 'Failed to put'` | Waze ở `displayId=<cụm>` **và KHÔNG** có dòng `Failed to put` | ⇒ `[SUY mạnh]` **đúng**: VD cụm do **uid 1000 (SYSTEM)** làm chủ (`fission_bg_xdjaVirtualSurface`, owner `com.xdja.containerservice`) ⇒ **cửa thoát #3** ⇒ (a) Waze dùng được ngay qua cụm; (b) mở đường **G2** (E6). Có `Failed to put` ⇒ giả thuyết **SAI** — ghi lại (nó bác một suy luận, **phải ghi**) | |
| E4 | **H1-C** | **Freeform trên display 0** (cửa thoát #1) — nền đã BẬT SẴN trên xe | `settings get global enable_freeform_support` (=1 [ĐO]) · `am start … --display 0 --windowingMode 5 -n com.waze/.FreeMapAppActivity` · `sleep 6; am stack list \| grep -iE 'waze\|mWindowingMode\|displayId'` (**T+6 s** là mốc: trampoline nổ ~2,4 s) · `am task resize <taskId> 100 200 900 800; echo exit=$?` · `sleep 2; am stack list \| grep -A3 -i waze` | bounds **đổi THẬT** + `displayId=0` + `mWindowingMode=freeform` | ⇒ **C ĐẠT**: đường lùi khả thi, nhưng ghi **chi phí kiến trúc** (`embedding()` cờ toàn cục → trạng thái **theo ô**, đụng 5 chỗ — đúng vùng vừa sinh P-bug1/P-bug2/P9-bước-3) · **exit 0 mà bounds KHÔNG đổi** ⇒ DL3 no-op như DL5 ⇒ C rụng, sang E5 · **`IllegalArgumentException: resizeTask not allowed`** ⇒ app UNRESIZEABLE ⇒ **đừng kết luận C rụng**: thử đòn bẩy density (E5-D3) rồi đo lại · `Unknown command` ⇒ verb bị cắt ⇒ C rụng | |
| E4b | **U8a** | Ảnh miễn phí trong cùng bước: ROM có hiện lại thanh trạng thái khi ô freeform có focus? | `$A exec-out screencap -p > u8a-freeform-focus.png` + `dumpsys window \| grep -iE 'mCurrentFocus\|statusbar\|navigationbar'` trước/sau khi **tay người** chạm vào ô | hai câu, **có ảnh kèm** | HIỆN ⇒ mang về **chiều cao dải (px)** để trừ vào vùng làm việc, đừng chỉ nói "có" · KHÔNG ⇒ U8a đóng. ⚠ U8a không còn [CHƯA BIẾT] hoàn toàn: `VoiceOverlay.kt:164-180` mang [ĐO xe 18-09] *ROM **CÓ** thanh hệ thống và **CÓ** kéo lên khi cửa sổ không-immersive lấy tiêu điểm* ⇒ câu hỏi thu hẹp còn "cửa sổ **freeform của ô** có gây ra không" | |
| E5 | **H1-D** | ⭐ **`am stack move-task`** — cơ chế **ĐÃ PROVEN trên đúng xe này**, mà repo **chưa bao giờ dùng cho ô** | **D2 trước (dễ thắng nhất)**: mở Waze **bình thường trên display 0**, đợi **vào map** (MainActivity đã tồn tại ⇒ hết trampoline) → `am stack list` lấy `taskId` Waze + `stackId` của stack trên VD ô → VD ô chưa có stack thì dựng: `am start --display <vdSlot> --windowingMode 1 -n com.android.settings/.Settings` → `am stack move-task <wazeTaskId> <slotStackId> true` → đọc lại **T+2 s và T+10 s** | Waze `displayId=<vdSlot>` **và còn ở đó sau 10 s** | ⇒ **D ĐẠT = đường lùi RẺ NHẤT** (không đổi kiến trúc `embedding()`, chỉ thêm bộ "đo rồi sửa"). [ĐO xe 2026-08-01 cùng ROM] `am stack move-task 15 6 true` đưa **CarPlay** display 0 → display 1 (`bounds=[0,0][1920,720]`, `visible=true`, **0 crash**), rồi trả về an toàn · ⚠ **Điều kiện bắt buộc**: **stack phải TỒN TẠI SẴN** trên display đích (`move-task` chuyển task *vào* stack, không tạo stack) · ⚠ **system_server chết** (màn nháy, launcher restart) ⇒ **DỪNG cả nhánh D**, ghi nguyên văn logcat: nghĩa là ranh giới *display 0 ↔ VD-của-app* khác ranh giới *display 0 ↔ VD-của-ROM* đã proven — **phát hiện lớn nhất phải mang về** · **D3 đòn bẩy density** khi app từ chối resize: `wm density <dpi> -d <display>` chỉ cho **display 0 / cụm** — ⛔ **KHÔNG** bắn `wm density -d <vdSlot>` (Kachi đã đặt density ngay trong `createVirtualDisplay`; lệnh shell ghi `display_settings.xml`, **sống qua reboot**, phải dọn) | |
| E6 | **H1-G2** | ROM tạo được **mấy** display? (chỉ ĐỌC, **không brute-force**) | `dumpsys display \| grep -iE 'Display [0-9]+:\|fission\|xdja\|virtual:\|type VIRTUAL\|FLAG_'` **trước và sau** khi mở projection → `diff` · `service list \| grep -iE 'AutoContainer\|auto_container\|xdja'` · `getprop ro.build.system.fission_single_os` | đếm số display fission/xdja | **≥2** ⇒ mở việc off-car **trần cao nhất**: dùng display của ROM làm **ô** ⇒ phủ **MỌI app**, hết cần gate (DL5 tạo **2**: `shared_fission_bg_XDJAScreenProjection_0/1`) · chỉ **1** ⇒ ROM DL3 một vùng ⇒ G2 đóng, giữ G1 · ⚠ **CHỈ** dùng mã proven `30/16/35` (mở) và `18/0` (đóng); **KHÔNG brute-force mã `sendInfo` lạ** — đây là service điều khiển bề mặt **trước mặt người lái**, và mã DL5 ≠ DL3 | |
| E7 | **H1-H2** | WazeMod (chỉ nếu đã cài) — giải bằng **chọn app**, 0 dòng code | đặt **WazeMod** (không phải `com.waze`) vào ô → `logcat -d -s ActivityTaskManager \| grep -iE 'wazemod\|Failed to put'` | ở lại trong ô | ⇒ **giải quyết xong bằng lựa chọn app**, ghi vào tài liệu người dùng (có tiền lệ: dự án đã ship VietMap mod) · vẫn bị đẩy ⇒ cần smali = việc off-car | |
| E8 | **X2** | Cast: dò display cụm **động** (bản sửa 09-14 chưa verify xe) | `$A shell "dumpsys display \| grep -iE 'Display [0-9]+:\|fission\|xdja\|virtual:'"` · `$A shell "logcat -d -s SimpleCast \| grep -iE 'cluster display\|detect\|profile 35\|-d [0-9]'" \| tail -20` | log in ra id cụm **THẬT** (vd 2) **SAU** profile 35; `am stack list` thấy app + `ClusterBlackActivity` ở đúng id đó | **FAIL-1** `detect … -1` nhiều lần ⇒ VD fission lên chậm hơn ngân sách `AWAIT_ATTEMPTS=12 × 500 ms = 6 s` ⇒ dán log, nâng hằng · **FAIL-2** dò ra id mà id đó là `kachi-slot-*` ⇒ guard `DisplayParse.isOwnedVirtualDisplay` hỏng ⇒ dán nguyên `DETECT_CMD`. [ĐO source] **đã từng xảy ra thật**: sau reboot **display 1 = `kachi-slot-0`** | |
| E8b | **X2** | `am start --display <cụm>` có bị chặn không — **dự đoán ĐÃ ĐỔI** | `$A shell "logcat -d -s SimpleCast \| grep -iE 'R1 did not land\|Permission Denial\|move-task'"` · `$A shell "dumpsys display \| grep -A3 fission \| grep -iE 'FLAG_\|owner'"` | **không** có `R1 did not land` | ⇒ `[SUY]` đúng: cụm do uid 1000 làm chủ + dump ghi `FLAG_OWN_CONTENT_ONLY\|FLAG_PRESENTATION` (**không** `FLAG_PRIVATE`) ⇒ gate không nổ ⇒ R1 lẽ ra **THÀNH CÔNG**; `Permission Denial` ngày 09-14 là do **hardcode `--display 1`** (không phải cụm) · vẫn Permission Denial **với id cụm ĐÚNG** ⇒ `[SUY]` **SAI** ⇒ dán khối cụm của `dumpsys display` — **dữ liệu quý nhất của khối cast** (rất có thể cụm CÓ `FLAG_PRIVATE`, hoặc `getOwnerUid()` ≠ 1000) | |
| E8c | **X2** | Fallback R2 — nhớ: **move-task**, KHÔNG phải move-stack | `$A shell "logcat -d -s SimpleCast \| grep -E 'am stack move-task\|am display move-stack'"` | thấy `am stack move-task <taskId> <stackId> true` | thấy **`am display move-stack`** ⇒ **DỪNG NGAY, tắt cast, báo về**: lệnh bị CẤM (§0.6) và bài canh `AppMoverMoveStackFallbackTest.kt:75-78` lẽ ra đã chặn ⇒ **có bản build sai** | |
| E8d | **X2** | Geometry/DPI + bóng VietMap (gate theo state cast) | sau khi cast **bám VD** (state → `CastingFull`): mục khung/DPI phải hiện nút chỉnh, bộ chỉnh bóng VietMap **mở khoá**; kéo bóng trong lúc đang chiếu, chụp trước/sau · `logcat -d \| grep -E 'VM_BUBBLE_POS\|geometryTargets\|slot-density'` | nút hiện + bóng dời theo | hai thứ này gate theo `coordinator.state`/`prefs.castEnabled()` ⇒ **hiện = bằng chứng phụ rằng cast bám thật** | |
| E8e | **X2** | **SpeedBadge nghi gắn nhầm màn** — nay có bằng chứng mạnh | `$A shell "dumpsys display \| grep -E 'Display 1:' -A6"` · `$A shell "dumpsys window windows \| grep -iE 'SpeedBadge\|mDisplayId'" \| head -20` | badge ở **display cụm** | display 1 tồn tại và **không** phải cụm ⇒ **xác nhận bug** ⇒ mở việc sửa (bỏ hằng, dùng `ClusterDisplayResolver`). [ĐO source] `SpeedBadgeOverlay.kt:44,138-141` thử `dm.getDisplay(1)` **TRƯỚC**; `DisplayParse.kt:184-188` sau reboot **display 1 = `kachi-slot-0`** ⇒ badge rất có thể đang gắn vào **một ô app của Kachi** | |
| E9 | **X1·ARCH-🚗** | Mồ côi cửa sổ — 5 mốc (giữ nguyên playbook 1.53 §2.12, không lặp ở đây) | `scripts/vehicle/kachi/60-cast.sh <ip>:5555` | **`FAIL=0`** ở cả 5 mốc: baseline → chiếu app thường → chiếu CP/AA → **đổi app từ CP/AA** (điểm mồ côi cũ) → stress 60 s → sau khi tắt chiếu | ⚠ lúc cắm CP/AA đầu xe **tắt Wi-Fi** ⇒ mất adb ⇒ mốc đó đo bằng **mắt** + ghi tay (đường D §0.3) | |

**Cây quyết định sau phase E** (xếp theo **trần giá trị ÷ chi phí**, không theo thứ tự chạy):

| Ưu tiên | Điều kiện | Việc sinh ra | Vì sao xếp đây |
|---|---|---|---|
| 0 | **E2 đạt** (Waze ở lại ô) | không làm gì, chỉ ghi | gate không nổ trên ROM này |
| 1 | **E6 đạt** (ROM ≥2 display) | đổi nền ô sang display của ROM | phủ **mọi** app, hết cần gate, rẻ nhất về lâu dài |
| 2 | **E5 đạt** | bộ "đo rồi sửa" + đặt task vào stack trên VD ô | cơ chế **đã proven trên xe**; **không** đổi kiến trúc `embedding()` |
| 3 | **E4 đạt** | trả giá kiến trúc: `embedding()` → trạng thái **theo ô** (5 chỗ) | chạy được nhưng đắt, đụng vùng vừa sinh P-bug1/P-bug2 |
| 4 | **E7 / H1 đạt** | giải bằng **chọn app** (tài liệu người dùng) | 0 dòng code, nhưng chỉ đúng cho Waze |
| 5 | **E3 đạt** (chỉ cụm) | Waze dùng qua **cụm** | không phải "vào ô" nhưng **dùng được thật** |
| 6 | tất cả rụng | **option I** — suy giảm **có nói lý do** (ô hiện thẻ app + nhãn nói thật, không im lặng) | **không trả giá kiến trúc trước khi E4/E5 đạt** |

### Phase F — Phím vô-lăng + `:tts` (40 phút · đường nóng #0 **chưa từng chạy**)

| # | ID | Việc | Lệnh chính | PASS / FAIL | Outcome → option | Kết quả |
|---|---|---|---|---|---|---|
| F1 | **W5 Q1+Q2** | Mã phím thật + phím có tới `onKeyEvent` khi **app cam tiền cảnh** — đường **QUYẾT ĐỊNH**, không nuốt phím, không cần arm gì | mở app camera 360 → `$A shell logcat -c` → bấm **tăng âm · giảm âm · trái vô-lăng · phải vô-lăng** (mỗi phím 1 nhấp + 1 lần **GIỮ 1 s**) → `$A shell "logcat -d -v time -s NavAccess" \| grep "onKeyEvent DOWN keycode="` | **có dòng** ⇒ Q2 = CÓ **và** dòng đó cho luôn keycode (Q1) | [ĐO source] `NavAccessibilityService.kt:78-80` log **MỌI** phím DOWN *trước* cả cổng học-phím lẫn cổng `voiceKeyEnabled` ⇒ đường này **không nuốt phím, không arm** · **không dòng nào** ⇒ Q2 = KHÔNG ⇒ **kết quả HỢP LỆ và đủ để quyết**: theo CLAUDE.md §14 tầng 1 thì **KHÔNG được viết code ngữ cảnh** (T1–T3 của spec dừng), ghi *"chưa làm được + điều kiện mở khoá"* — **đừng biến thành "để sau xem lại"** · `getevent -lp` bị SELinux chặn ⇒ **bỏ đường đó**, A đủ (nó cho keycode Android = đúng thứ `VoiceKeyMatcher` dùng) · A+B im ⇒ đường **C "Học phím mới"**: nó **nuốt** phím + tự tắt sau **ĐÚNG MỘT** phím ⇒ 4 phím = **4 vòng** | |
| F2 | **W5 R4** | Phím **GIỮ** có mã riêng không | so keycode lần giữ vs lần nhấp (cùng lượt F1) | khác nhau | khác ⇒ làm được *"giữ = thân xe trong suốt"* **không cần mốc thời gian** (bài học 1.19: **cấm tự đặt ngưỡng giữ**) · giống nhau ⇒ **R4 không thoả**, ghi "chưa làm được + điều kiện mở khoá" | |
| F2b | **W5** | Thu thêm trong cùng lượt (thiếu thì phải quay lại xe) | `$A shell "dumpsys window \| grep -E 'mCurrentFocus\|mFocusedApp'"` · `$A exec-out screencap -p > w5-camera.png` | package app cam THẬT | ⇒ danh sách ngữ cảnh R2 của spec · **OQ2** (CP/AA đang chiếu thì cam còn nhận phím?) đo bằng **mắt** — lúc đó mất adb, **đừng cố** | |
| F3 | **#0 1.79** | ⭐ **KILL `:tts` GIỮA LÚC ĐỌC** — toàn bộ giá trị của 1.79 nằm ở giả thuyết này | **mốc TRƯỚC**: `$A shell "ps -A \| grep com.byd.launcher"` (ghi pid `:app`; `:tts` **chưa có** — `RemotePiperSpeaker` bind **LƯỜI**) + `dumpsys accessibility \| grep -A6 NavAccessibilityService`. **Đặt bẫy vòng lặp** (giết ngay khi `:tts` xuất hiện ⇒ **luôn trúng giữa lúc synth**), rồi owner nói một câu có reply **DÀI** (vd *"mở tất cả kính"*) | **H1** pid `:app` **KHÔNG đổi** · **H2** a11y vẫn **Bound** · **H3** log *"mở N chỗ đang chờ"* + overlay đóng bình thường (không treo tới `SPEAK_SAFETY`) · **H4** câu SAU đọc được + `:tts` **pid MỚI** | **4 đúng** ⇒ **#0 ĐẠT**, 1.79 sửa đúng gốc, đóng nợ crash-binding, **bỏ** đề xuất (b) chuyển mặc định sang System TTS · pid `:app` **ĐỔI** ⇒ **#0 THẤT BẠI** ⇒ đi ngay đường **(b)**: `AndroidTtsSpeaker` làm mặc định, Piper opt-in (`VoiceSpeakerSelector` đã có sẵn đường) · `:app` sống nhưng a11y **kẹt "Binding"** ⇒ crash không còn là gốc, **rebind vẫn kẹt dưới tải** ⇒ làm **watchdog binding** (force-stop + re-enable — đường đã proven recover 18-09) · `:app` sống mà overlay treo ~10 s ⇒ `onDone` **không nổ** ⇒ có lỗ trong bảng "ai đóng sổ", trace `sendSpeak` vs `onRemoteGone` · `:tts` **không bao giờ xuất hiện** ⇒ Piper không được chọn (thiếu gói giọng) ⇒ kiểm `SherpaTtsSpeaker.voiceFilesPresent` + `voice_tts_voice` | |
| F3-cmd | | **Thang 3 đường giết `:tts`** (đường 1 chắc chắn nhất) | **(1) `run-as`** (bản `vehicleTest` — cùng uid nên **không thể bị từ chối**): `$A shell "run-as com.byd.launcher sh -c 'for i in $(seq 1 300); do p=$(ps -A | grep \"com.byd.launcher:tts\" | grep -v grep | awk \"{print \\$2}\"); if [ -n \"$p\" ]; then echo TRAP_KILL $p; kill -9 $p; break; fi; sleep 0.1; done'"` · **(2) shell thẳng** `kill -9 <pid>` — **[CHƯA BIẾT]** uid 2000 có được phép signal tiến trình uid khác trên ROM này · **(3) chờ crash TỰ NHIÊN** ở F4 | — | (2) trả `Operation not permitted` ⇒ **dùng (1)** ⇒ đó là lý do §0.1 khuyến nghị `vehicleTest` · ⚠ `kill -9` chỉ mô phỏng SIGSEGV ở mức *"tiến trình biến mất"* — đủ cho H1–H4 (nền tảng gọi **cùng** callback), **không** mô phỏng heap corruption lan trước khi chết ⇒ nếu F4 bắt được crash **TỰ NHIÊN** thì **ưu tiên bằng chứng đó** | |
| F4 | **#0** | Phím sống qua **≥10 lượt** (rải suốt buổi — mỗi lượt nói ở §C tính là 1 lượt) | sau mỗi lượt: `$A shell "dumpsys accessibility \| grep -c Bound"` (**không được giảm**) · `$A shell "logcat -d \| grep -cE 'Fatal signal 11'"` | Bound không giảm · `Fatal signal 11` trong `:app` = **0** | có `Fatal signal 11` trong **`:tts`** mà không ai kill ⇒ **xác nhận SIGSEGV thật vẫn còn, nhưng đã bị cô lập** ⇒ đây là **bằng chứng mạnh nhất cho #0** — chép tombstone | |

**Tag logcat của 1.79 cần cho phase này** [ĐO source]: `KachiVoiceTtsLink` (`RemotePiperSpeaker.kt:298`) · `KachiVoiceTtsProc` (`PiperTtsService.kt:131`) · `KachiVoiceGeo` (`VoiceGeocoder.kt:41`) · `YtResolve` (`VoiceYoutubeResolver.kt:19`) · `WakeListen` (`VoiceWakeListener.kt:293`) — ⚠ **cả 5 đều THIẾU** trong `KACHI_LOG_TAGS` của `_common.sh` (§3).

### Phase G — Hey Kachi (10 phút · **chỉ probe CPU, KHÔNG đo hit/20**)

**Cổng vào** — chỉ chạy đo độ chính xác khi **CẢ HAI** đúng: (a) Phase 0 off-car của `oncar-runbook-hey-kachi.md` đã **đạt** (model gigaspeech bắt được "Hey Kachi" giọng Việt: hit ≥ 8/10 im + ≥ 6/10 có nhạc, false-accept ≤ 1/30 phút) **VÀ** (b) `VoiceLoadGuard` đã **chia số lõi** (option A của C4) và ship. **Hôm nay cả hai đều SAI** ⇒ phase G rút về đúng một việc:

| # | ID | Việc | Lệnh chính | PASS / FAIL | Outcome → option | Kết quả |
|---|---|---|---|---|---|---|
| G1 | **Hey Kachi** | Giá CPU của lớp mic+gating (RMS-only — đúng cái đo được hôm nay) | baseline `top` lúc wake **TẮT** → Cài đặt › Hệ thống › *Hey Kachi* = **BẬT** → `$A shell "top -b -n 1 \| grep byd.launcher"` · `$A shell "logcat -d \| grep -iE 'WakeListen\|VoiceWakeKws\|chỉ-RMS\|thiếu model KWS'" \| tail -10` · `$A shell "dumpsys media.audio_flinger \| grep -iE 'input\|record'" \| head -5` | %CPU **~không đổi** + log *"chỉ-RMS"* | ~không đổi ⇒ lớp gating rẻ đúng thiết kế ⇒ **rủi ro #2 của runbook hạ cấp**, còn lại đúng rủi ro #1 (model) · **tăng đáng kể DÙ đang SUSPENDED** ⇒ có lỗ: đang treo mà vẫn đốt CPU ⇒ trace vòng đọc load/mic · mic **bị giữ** ⇒ kiểm `VoiceMicPreempt` (nút mic tài xế phải thắng) | |
| G1b | | **TẮT lại** trước khi rời (mặc định TẮT = 0 ảnh hưởng) | Cài đặt › Hệ thống › *Hey Kachi* = TẮT | công tắc TẮT | — | |

⇒ **Cổng nghiệm thu Phase 3 của `oncar-runbook-hey-kachi.md` GIỮ NGUYÊN**, nhưng chỉ chạy **sau khi** (A hoặc B) đã ship **và** model đã qua Phase 0. Buổi này chỉ lấy **dữ liệu load (C4) + CPU baseline (G1)**.

### Phase H — Reboot VẬT LÝ + lần mở đầu (30 phút · cuối buổi)

⚠ **`adb reboot` KHÔNG TÍNH** (`VEHICLE-TEST-V2.md:224` ghi rõ `adb reboot invalid`). Chứng minh reboot thật: **`boot_id` phải ĐỔI** so với §A5.

| # | ID | Việc | Lệnh chính | PASS / FAIL | Outcome → option | Kết quả |
|---|---|---|---|---|---|---|
| H1 | **F4** | Hộp *"Cho phép gỡ lỗi USB?"* ở **lần mở đầu** — 3 đường dựng lại, xếp theo mức phá | **A** xe/máy chưa từng cài Kachi (**sạch nhất**, chọn nếu có xe thứ hai) · **B** `$A shell "run-as com.byd.launcher rm -f files/adb.key files/adb.pub"` rồi mở lại Kachi (**chỉ bản `vehicleTest`**; ⚠ chỉ xoá khoá **của Kachi**, **KHÔNG** đụng uỷ quyền của laptop ⇒ kênh đo an toàn) · **C** `rm /data/misc/adb/adb_keys` (⚠ **thu hồi uỷ quyền CẢ laptop ⇒ mất kênh đo**, cần root [CHƯA BIẾT]) | **4 điều**: (1) hộp thoại **hiện và SỐNG** qua lượt resume của Kachi (không bị `removeWindow` trong ~5 s); (2) **dải nhắc** hổ phách ở **đáy** màn có chữ + nút **Thử lại**, **không chặn cú chạm nào** ngoài khung chữ (chạm thử một ô — phải ăn); (3) tích *"Luôn cho phép"* + OK ⇒ dải **tự biến mất trong ~1,5 s**; (4) `logcat -s Preflight` có `tự cấp …` + `sau khi tự cấp: …` với danh sách thiếu **ngắn lại** | hộp thoại **vẫn bị đè chết** ⇒ chép mốc `removeWindow` + mốc `ShellChannelGate` (chứng minh `SETTLE_MS=1500` chưa đủ) ⇒ **mang về số THẬT phải nới**, không đoán · dải **không hiện** mà hàng quyền nói *"Hạn chế của môi trường"* ⇒ lý do **chưa phân loại được** (`IO_ERROR`/`PORT_CLOSED` thay vì `AWAITING_APPROVAL`) ⇒ chép `logcat` + `Recv-Q` · dải hiện **mãi không tắt** sau khi đã Cho phép ⇒ bấm **Thử lại**; vẫn không ⇒ [P1] `channelUp` không bao giờ được đặt · ⚠ nếu chọn **đường C** thì làm **SAU CÙNG** (mất adb ⇒ phần còn lại của buổi phải đi đường D §0.3) | |
| H2 | **S5** | Đặt Kachi làm màn hình chính — ⚠ **component ĐÃ ĐỔI, playbook 1.53 §2.19 đang SAI** | **A (đường người dùng, chạy trước)**: Cài đặt › Hệ thống & quyền › **Màn hình chính** → *Đặt Kachi làm màn hình chính* → `cmd package resolve-activity --brief … HOME` + `dumpsys package com.byd.launcher \| grep -A3 -i KachiHome` → bấm **nút Home vật lý**. **B (shell thô, đúng component MỚI)**: `COMP=com.byd.launcher/com.byd.clusternav.launcher.KachiHome`; `pm enable $COMP` (**alias ship `enabled=false`** — KHÔNG bỏ bước này); `cmd package set-home-activity $COMP`; đọc lại resolve | *"Đã đặt — bấm Home để về Kachi"*, dòng chuyển xanh, nút biến mất; Home vật lý → Kachi lên | **C (5 giây, để dứt điểm "alias hay activity")**: `set-home-activity …/KachiHomeActivity` — [SUY] **sẽ KHÔNG** thành HOME vì `KachiHomeActivity` **không khai `CATEGORY_HOME`** (`AndroidManifest.xml:104-108`) ⇒ kết quả là **bằng chứng để sửa doc**, không phải đường chữa · `Success` mà resolve vẫn launcher khác ⇒ **[P0] ROM giành lại HOME** ⇒ chép output + đợi 30 s đọc lại · báo *"Cần kênh shell"* ⇒ đó là H1, làm H1 trước · **Hoàn tác**: Cài đặt › Màn hình chính › **"Bỏ chọn"** (1.76 thêm chính vì BUG1 kẹt Kachi) hoặc `set-home-activity <launcher-cũ>` (lấy từ §A5) | |
| H3 | **P7·T-BRIDGE(b)·K8b·W3·P8** | ⭐ **MỘT lượt tắt-nổ máy trả NĂM câu** — chỗ tiết kiệm lớn nhất của buổi | **TRƯỚC**: chép `boot_id`, bật cầu, `keep_home_on_boot` = **TẮT**, `recirc_on_start` = **BẬT**. → **tắt máy bằng NÚT NGUỒN** → nổ lại → **trong ~5 giây đầu** bắn 6 lệnh đã soạn sẵn **trên một dòng**: `cat /proc/sys/kernel/random/boot_id` · `cmd package resolve-activity --brief … HOME` · `settings get secure enabled_accessibility_services; settings get secure accessibility_enabled` (**trước khi mở Kachi** — P8 tự-lành) · `T --es cmd state` · `logcat -d -v time -s KachiAutostart \| tail -30` · `logcat -d -s KachiPerf \| tail -5` | **P7**: bấm Home → Kachi lên ⇒ **ROM GIỮ HOME** ⇒ mặc định TẮT là đúng · **T-BRIDGE(b)**: `"error":"test_mode_off"` **và** `boot_id` đã đổi · **K8b**: mọi ô đang hiện ra số **≤ 60 s**, cột `bỏ-xe-không-có` **tụt về ~0** · **W3**: lấy gió trong tự bật (`getAcCycleMode`=1) · **P8**: trợ năng về `null`/0 ngay sau nổ máy rồi **CÓ LẠI** sau khi mở Kachi | **P7 fail** ⇒ đọc log `KachiAutostart` theo đúng **5 dòng hợp đồng**: (1) *"launcher auto-start disabled by pref — skip"* ⇒ **`launcher_autostart` đang TẮT** ⇒ một công tắc **không liên quan** giết cả đường khởi động (đúng lỗ P7 của backlog); (2) *"skip (a run is in-flight or within cooldown 30000ms)"* ⇒ burst boot/OTA, **không phải lỗi**; (3) *"freeform seed ensured"*; (4) *"home entry (alias) enabled=… reasserting HOME (keepOnBoot=… chosen=…)"* **hoặc** *"keep-home-on-boot OFF + home not chosen — not reasserting"* ⇒ **dòng này nói thẳng vì sao**; (5) *"requested HOME up"*. ⚠ **Kachi LÊN ≠ Kachi LÀ HOME** — bấm nút Home vật lý là phép thử duy nhất tách được · **T-BRIDGE(b) fail** (cầu còn chạy) ⇒ **[P1] cửa sống qua lần nổ máy** ⇒ chép `T --es cmd prefs --es file kachi_test_bridge` (khoá `test_bridge_until`) + hai `boot_id` (bản đầu 09-14 dùng *giờ tường − uptime* và **đã hỏng thật trên xe** vì đầu xe chỉnh giờ GPS ⇒ đây là **hồi quy cho đúng lỗi đó**) · **K8b fail** (còn `—` sau 60 s) ⇒ đợi đủ **35 s** (hết `MISS_TTL_MS`) rồi xem có tự ra số: vẫn `—` ⇒ **không phải cache**, là HAL/quyền | |
| H4 | **P7 lượt 2** | Chỉ khi H3 fail phần HOME: `keep_home_on_boot` = **BẬT** rồi reboot vật lý lần nữa | như H3 | Kachi lên | công tắc **cứu được** ⇒ đề xuất owner bật · vẫn không ⇒ **[P1]** `KachiAutostart` không chạy hoặc bị chặn | |
| H5 | **T-BRIDGE(b) phụ** | Trần 60 phút (rẻ, làm luôn nếu buổi đủ dài) | chờ 60 phút không bật lại → `T --es cmd state` | `test_mode_off` | — | |

### Phase I — Dọn bắt buộc trước khi rời xe

```bash
$A shell am force-stop com.waze; $A shell am force-stop com.android.settings
$A shell am force-stop com.chisadin.wazemod 2>/dev/null
# TẮT CHIẾU bằng UI (nút nổi / Cài đặt). Cụm kẹt ⇒ tắt máy xe rồi nổ lại
#   + GHI LẠI ĐÚNG CHUỖI THAO TÁC dẫn tới kẹt (dữ liệu quý nhất của buổi)
$A shell "dumpsys display | grep -c kachi-slot"     # phải = số ô App, không dư
$A shell "cat /proc/net/tcp | grep 15B3"            # 0x15B3 = 5555, kiểm ổ cắm không rò
```
1. **TẮT công tắc *Chế độ kiểm thử qua adb*** — một cửa thi hành lệnh mở vĩnh viễn trên xe đang chạy là **một lỗ**, không phải tiện nghi.
2. **TẮT *Hey Kachi*** (G1b).
3. **Cài lại bản `release`** nếu đã dùng `vehicleTest` — bản debuggable **không được để lại trên xe**.
4. **Trả HOME** nếu owner muốn (H2 hoàn tác).
5. Đóng app camera; trả kính/nóc/rèm/cốp/khoá về trạng thái ban đầu; **khoá xe**.
6. **KHÔNG để lại**: task freeform trên display 0 · `am compat` override · `wm density` đã ghi vào `display_settings.xml` · ghi vào `/system`.

---

## 2. TEMPLATE KẾT QUẢ — MỘT TRANG (điền ngay tại xe · trí nhớ không phải bằng chứng)

> Mỗi dòng đóng một 🚗 trong `docs/PROJECT-BACKLOG.md`. **Ô trống = CHƯA ĐO** (đúng, và tốt hơn tick bừa).
> Cột *Số đo* phải là **con số hoặc chuỗi nguyên văn**, không phải "OK".

```
BUỔI TEST XE — ngày ______ · bản Kachi ______ (vc __) · build: release / vehicleTest
kênh đo: A adb thẳng / B cầu nc / C adb_raw.py / D app tự chụp        featmap stamp ______ (diff vs 20260916: ______)
boot_id TRƯỚC ____________________  boot_id SAU reboot ____________________  (phải KHÁC)
load average lúc đo K5: ______ / ______ / ______      số lõi: ______
```

| # | Phase | ID backlog | Mục | PASS/FAIL | Số đo / chuỗi nguyên văn | Bug mới |
|---|---|---|---|---|---|---|
| 1 | A1 | **BUG2** | force-stop có chữa adb wireless | | | |
| 2 | A3 | **T-BRIDGE(a)** | shell ROM BYD → receiver `exported` | | | |
| 3 | A4 | **W5-tiền-đề** | a11y BOUND + `capabilities=9` | | | |
| 4 | A6 | **L-RE** | `featmap` diff vs 09-16 | | | |
| 5 | B1 | **K5** | HAL đọc/phút (< 150) | | | |
| 6 | B1 | **K5** | `no permission`/phút (< 20) | | | |
| 7 | B1 | **K5** | CPU idle % (< 2) **+ load average** | | | |
| 8 | B1b | **K5** | RSS `:app` / `:tts` (⚠ `:tts` > 100 MB = [P0]) | | / | |
| 9 | B1 | **K5** | log KB/phút (< 20) | | | |
| 10 | B2 | **P8** | **7** hàng app ↔ hệ thống khớp | /7 | | |
| 11 | B2 | **U8b** | toast ≤ 1 lần / 3 lượt mở | | | |
| 12 | B3 | **H1-T2b** | grab-list ĐỌC 26 nút | /26 | | |
| 13 | B4 | **W1** | 5 datum NEEDS_CAR (4 GPS + target_soc) | /5 | | |
| 14 | B5 | **W1·W4** | kiểm mắt 4 bề mặt ≤ 10 s | /4 | | |
| 15 | B5 | **W4** | số áp suất/nhiệt lốp THẬT (chốt ngưỡng OQ1) | | | |
| 16 | B6 | **K8c** | `bỏ-xe-không-có` lên rồi **về 0** | | | |
| 17 | B7 | **H1-B** | `userId=` của Kachi (1000 ⇒ bỏ E2–E7) | | | |
| 18 | B7 | **H1-H1** | `protectionLevel` của `ACTIVITY_EMBEDDING` | | | |
| 19 | C0 | — | **mạng xe ra internet** (biến dùng chung) | | | |
| 20 | C1 | **V1·VietMap** | option nào ĐẠT: A / B1 / B2 / B3 / B4 / C | | | |
| 21 | C1a | **V1** | geocode đi đường nào (on-device / Nominatim / treo) | | | |
| 22 | C2 | **V1·nhạc** | option nào ĐẠT: A / B / C / D | | | |
| 23 | C2a | **V1** | `state=PlaybackState` (3 = PLAYING) | | | |
| 24 | C3 | **K8a** | hỏi tốc độ ở 2 tốc độ → **2 số khác nhau** | | → | |
| 25 | C4 | **Hey Kachi** | load1: idle / vietmap / gmaps-slot / cả hai | | / / / | |
| 26 | C4 | **Hey Kachi** | có chuỗi ≥3 mẫu < 4.0? (dự kiến KHÔNG) | | | |
| 27 | D1 | **W3·L-RE** | `ac_auto` option A / B | | | |
| 28 | D2 | **L-RE** | fan · recirc · defrost · rear · temp | /5 | | |
| 29 | D3 | **W2** | thang ghế: raw 4 có nhận? ⇒ `seath` 3 hay 4 bậc | | | |
| 30 | D4 | **W2** | `readl` option A / B (⇒ TOGGLE hay SELECT 3) | | | |
| 31 | D5 | **L-RE** | 8 nút enum xác nhận | /8 | | |
| 32 | D6 | **W2 ⭐** | **kính ½**: option A / B / C / D | | | |
| 33 | D7 | **P1** | `sunroof` percent 0/50/100/254 | | | |
| 34 | D8 | **W2** | rèm STOP=254 · cốp chiều cao 15..100 | | | |
| 35 | D9 | **W2** | `lock`/`door`/`child_lock` option A/B/C/D | | | |
| 36 | D10 | **W2** | **bảo trì gạt mưa** (cờ + A/B) | | | |
| 37 | D11 | **L-RE** | ambient 4 nút với id THẬT | /4 | | |
| 38 | D12 | **W5 Q3** | bảng `v → góc camera` (map nhãn↔enum) | | | |
| 39 | D13 | **L-RE2** | HUD mode (⇒ đổi nghĩa hay gỡ `hud_brightness`) | | | |
| 40 | D14 | **L-RE2** | screen_rotation · cluster_music · seat_memory | /3 | | |
| 41 | E1 | **H2** | số `kachi-slot-*` = số ô App | | | |
| 42 | E2 | **H1** | Waze ở lại ô? (T+3 s / T+10 s) | | | |
| 43 | E3 | **H1-G1** | Waze lên **CỤM** (không `Failed to put`) | | | |
| 44 | E4 | **H1-C** | freeform display 0 (bounds đổi THẬT?) | | | |
| 45 | E4b | **U8a** | ô freeform ⇒ thanh hệ thống hiện? (px) | | | |
| 46 | E5 | **H1-D** | `move-task` vào VD ô (D2 → D1) | | | |
| 47 | E6 | **H1-G2** | ROM tạo được **mấy** display fission | | | |
| 48 | E7 | **H1-H2** | WazeMod ở lại ô | | | |
| 49 | E8 | **X2** | dò display cụm ĐỘNG (id thật) | | | |
| 50 | E8b | **X2** | `am start --display <cụm>` có bị chặn | | | |
| 51 | E8c | **X2** | fallback = `move-task` (KHÔNG move-stack) | | | |
| 52 | E8d | **X2** | khung/DPI + bóng VietMap dời theo | | | |
| 53 | E8e | **X2** | SpeedBadge gắn màn nào | | | |
| 54 | E9 | **X1·ARCH-🚗** | mồ côi cửa sổ 5 mốc (`FAIL=0`) | /5 | | |
| 55 | F1 | **W5 Q1** | keycode 4 phím | | | |
| 56 | F1 | **W5 Q2** | phím tới `onKeyEvent` khi cam mở | | | |
| 57 | F2 | **W5 R4** | phím GIỮ có mã riêng | | | |
| 58 | F2b | **W5** | package app cam THẬT | | | |
| 59 | F3 | **#0 1.79** | **H1** pid `:app` không đổi | | | |
| 60 | F3 | **#0** | **H2** a11y vẫn Bound | | | |
| 61 | F3 | **#0** | **H3** log *"mở N chỗ đang chờ"* + overlay đóng | | | |
| 62 | F3 | **#0** | **H4** câu sau đọc được + `:tts` pid MỚI | | | |
| 63 | F3-cmd | — | đường kill dùng được: run-as / shell / tự nhiên | | | |
| 64 | F4 | **#0** | phím sống qua ≥10 lượt · `Fatal signal 11` = 0 | /10 | | |
| 65 | G1 | **Hey Kachi** | Δ%CPU khi bật wake (RMS-only) | | | |
| 66 | H1 | **F4** | hộp USB-debug sống qua resume | | | |
| 67 | H1 | **F4** | dải nhắc hiện / tự tắt ≤ 1,5 s | | | |
| 68 | H1 | **F4** | quyền tự cấp sau khi Cho phép | | | |
| 69 | H2 | **S5** | đặt HOME qua UI (alias `KachiHome` enabled) | | | |
| 70 | H3 | **P7** | HOME sống qua reboot **VẬT LÝ** (keep=TẮT) | | | |
| 71 | H3 | **T-BRIDGE(b)** | `test_mode_off` sau reboot | | | |
| 72 | H3 | **K8b** | HAL lên muộn: mọi ô ra số ≤ 60 s | | | |
| 73 | H3 | **W3** | lấy gió trong tự bật khi nổ máy | | | |
| 74 | H3 | **P8** | trợ năng tự lành sau reboot | | | |
| 75 | H4 | **P7** | keep=BẬT có cứu được không | | | |

### 2.1 Kết quả nào → sửa doc nào (atomic với backlog — R2.1)

| Kết quả của | Ghi vào |
|---|---|
| K5 | `perf-profile-2026-09-16.md` **§0b MỚI** (bảng cùng cột §0 để so trực tiếp) · backlog **K5** |
| K8 (a/b/c) | `perf-profile-2026-09-16.md` §9.3 — **sửa mục 7 tại chỗ** (đang trỏ ô "ETA sạc" đã xoá) · backlog **K8** |
| HAL §D | `1-hal.md` §6 (mẫu ghi) · `hal-binding-remediation-2026-09-15.md` · `docs/catalog/status-by-id.json` · backlog **L-RE**, **L-RE2**, **W2**, **W3**, **H1-T2b** |
| app-vào-ô §E | `2-slot-cast.md` §3 (đánh dấu option ĐẠT/RỤNG) · `waze-into-slot-research-2026-09-14.md` §4 · backlog **H1**, **D-emu** |
| cast §E8 | `oncar-playbook-kachi-1.53.md` §2.12.X2 (**sửa move-stack → move-task**) · backlog **X2**, **X1**, **ARCH-🚗** |
| voice §C | `3-voice.md` §1.3/§2.3 · `VoiceAppTargets` evidence tier · backlog **V1**, **V-STT** |
| `:tts` §F3 | `oncar-piper-crash-binding-2026-09-18.md` · spec `kachi-voice-rearchitecture-and-remaining.html` · backlog **1.79 #0** |
| W5 §F1/F2/D12 | **`docs/diagnostics/oncar-w5-keys-<ngày>.md`** (spec R1 đòi đúng tên này) · spec `kachi-camera-context-keys.html` §6/§7 · backlog **W5** |
| Hey Kachi §C4/§G1 | `oncar-runbook-hey-kachi.md` (Phase 2 → ghi lý do huỷ + số load) · backlog **Hey Kachi** |
| F4 §H1 | spec `kachi-permission-preflight.html` §9 · backlog **F4** |
| P7/S5 §H2/H3 | **sửa `oncar-playbook-kachi-1.53.md` §2.19** (component sai) · backlog **P7**, **S5** |
| T-BRIDGE §A3/H3 | spec `kachi-test-bridge.html` §Nhật ký · backlog **T-BRIDGE** |
| P8 §B2 | backlog **P8** (sửa "6 điều kiện" → **7**) · **U8(b)** |
| U8a §E4b | backlog **U8** (a) · ảnh `u8a-*.png` |
| BUG2 §A1 | `oncar-adb-wireless-broken-after-kachi-2026-09-18.md` §3 (điền kết quả Test A) |
| **Tất cả** | `.kiro/steering/project-context.md` §5 + `docs/PROJECT-BACKLOG.md` |

---

## 3. ĐỐI CHIẾU 12 SCRIPT `scripts/vehicle/kachi/` — chỗ cần cập nhật 1.53 → 1.79

> ⚠ **Stage này KHÔNG sửa script** — chỉ liệt kê. Mọi dòng dưới đây đã verify bằng `grep`/`sed` trên chính tệp.
> Cột **Mức** = ảnh hưởng tới phép đo: **[CHẶN]** làm bước đo sai/không chạy · **[SAI SỐ]** ra con số lệch · **[CHỮ]** chỉ lạc hậu câu chữ.

| Script | Dòng | Hiện tại | Phải thành | Mức |
|---|---|---|---|---|
| `_common.sh` | `KACHI_LOG_TAGS` (≈173-178) | 26 tag của 1.53 — **thiếu 5 tag mới của 1.79/1.77**: `KachiVoiceTtsLink` · `KachiVoiceTtsProc` · `YtResolve` · `WakeListen` · `KachiPerf` (và `KachiVoiceTiming`) | thêm 6 tag ⇒ nếu không, **§F3 và §C2 và §B1 không có dòng nào trong `logcat-<bước>.txt`** (bộ lọc `*:S` chặn hết) | **[CHẶN]** |
| `_common.sh` | 3 · 131 · 288 | *"bộ script LÊN XE của Kachi **1.53**"* · *"thêm 2026-09-14 cho bộ 1.53"* · *"máy ảo với bản 1.53/54"* | 1.79 (80) | [CHỮ] |
| `00-connect.sh` | 27 | trỏ `oncar-playbook-kachi-**1.47**.md §5` | trỏ **RUNBOOK này** §0.3 (thang 4 đường kênh đo) | [CHỮ] |
| `00-connect.sh` | 45 | *"bản **1.47** ký khoá Kachi riêng (L2)"* | "từ **1.41** ký khoá Kachi riêng" | [CHỮ] |
| `10-baseline.sh` | 38 | `[C] Quyền Kachi — cùng **5 điều kiện** mà PermissionPreflight đọc` | **7 điều kiện** — [ĐO source] `LauncherRequirements.kt:293-295` `ALL = [SHELL_CHANNEL, FREEFORM, DEFAULT_HOME, OVERLAY, NOTIFICATION_LISTENER, ACCESSIBILITY, **MICROPHONE**]` (`SHELL_CHANNEL_AWAITING_APPROVAL` là bản `copy()`, **không** trong `ALL`) ⇒ hiện đang đọc thiếu **2 hàng**, §B2 sẽ báo "khớp" oan | **[SAI SỐ]** |
| `20-datums.sh` | 64-112 | đếm **động** từ registry ⇒ **KHÔNG lạc hậu** ✅ | — (giữ) · chỉ kiểm: bảng sinh ra phải ra **100 thông tin · 47 nút** sau (V) FEATURE-FILTER, không phải 123/64 | [CHỮ] |
| `30-profiles.sh` | 32 | *"Nếu xe từng chạy bản ≤1.46 và có cảnh…"* | vẫn đúng lịch sử; **thêm** kiểm 1.76 BUG1: nút **"Bỏ chọn"** HOME có trả về launcher khác không (`clearDefaultHome` xoá cả `homeChosen`+`keepHomeOnBoot`) | [CHỮ] |
| `40-ota.sh` | 8 · 12 · 101 | đọc bản đích từ **tên tệp** `apk/Kachi-*-release.apk` ⇒ **KHÔNG lạc hậu** ✅ (§9 sinh ra đúng để chặn hằng sót) | — (giữ); chỉ xác nhận `apk/` đang có **`Kachi-1.79-release.apk`** ([ĐO] có, 38 128 296 B) | ✅ |
| `50-keys.sh` | 6-7 · 19-32 | **đã có** đường logcat `NavAccess` ✅ và đã lường `getevent` bị SELinux chặn ✅ | **thiếu tiền đề BẮT BUỘC**: kiểm `dumpsys accessibility` **BOUND** *trước* khi bấm phím — [ĐO xe 18-09] ENABLED-mà-không-Bound ⇒ **mọi kết quả Q2 là SAI ÂM**. Thêm mục `[A0]` gọi §A4 và **dừng** nếu không Bound | **[CHẶN]** |
| `50-keys.sh` | mục [E] | Q3 bấm tay ô *Góc camera* | thêm đường **máy**: `ctl --es id camera_view --ei v 0..5` (**không cần `auto_confirm`** — `camera_view` không thuộc `CtlSafetyPolicy.CONFIRM_REQUIRED`, `:35-38`) ⇒ lời đáp có `accepted`+`hal_line` **phân biệt được "HAL nhận mà hình không đổi" vs "HAL từ chối"** — bấm tay không cho biết | [SAI SỐ] |
| `60-cast.sh` | 24 | *"Ánh xạ câu nhắc của on-car-verify.sh → màn Kachi **1.47**"* | 1.79 | [CHỮ] |
| `60-cast.sh` | — | không kiểm chuỗi fallback | **thêm** grep chặn: thấy `am display move-stack` trong log ⇒ **DỪNG** (lệnh CẤM, §0.6); kỳ vọng đúng là `am stack move-task <taskId> <stackId> true` | **[CHẶN]** |
| `70-voice.sh` | 8 · 59 · 62 · 75 · 76 · 243 | **toàn bộ mục [A2] nói về Vosk**: *"Vosk chạy TẠI MÁY"* · `/data/data/$KACHI_PKG/files/**vosk**` · *"vosk-model-small-vn-0.4 · 32 MB nén · 51 MB trên đĩa"* · *"19529 từ"* · *"Tải mô hình tiếng Việt"* | **sherpa-onnx từ 1.55** (V-STT): model `zipformer-vi-int8-2025-04-20` **74 MB**, thư mục `files/sherpa/` (không phải `files/vosk/`); **thêm** gói ĐỌC Piper `voice/tts/piper-vi_VN-vais1000-medium/` **13 tệp / 63 877 499 B**; **thêm** `:tts` process (§F3) | **[CHẶN]** (mục [A2] đo sai đối tượng) |
| `70-voice.sh` | 243 | `[E] CPU/RAM của một phiên nghe` đọc **một** tiến trình | đọc **HAI**: `com.byd.launcher` **và** `com.byd.launcher:tts` ([ĐO source] `meminfo <pkg>` KHÔNG gộp) ⇒ hiện đang ra **số bịa** | **[SAI SỐ]** |
| `71-hal-sweep.sh` | 15 · 74 | *"Giá trị **123 datum**: xem 20-datums.sh"* | **100 thông tin** (sau (V) FEATURE-FILTER 2026-09-17) | [SAI SỐ] |
| `71-hal-sweep.sh` | 116-139 | `ctl id on` → đọc `accepted`/`hal_line` → TOGGLE thì `ctl id off` | **thiếu pha `getid`/`setev` theo ma trận §D** (từng-giá-trị-ứng-viên) và **thiếu `--es dev`** — [ĐO] lượt sweep gạt mưa 09-16 fail vì `TestBridgeHal.DEFAULT_DEVICE = BYDAutoBodyworkDevice` (`:41`) hỏi Bodywork(1001) về id thuộc Wiper(1046). ⇒ thêm: **mọi lượt truyền `--es dev`**, và **đối chiếu trường `device_by_map` trong lời đáp** trước khi kết luận *"không có"* | **[CHẶN]** |
| `71-hal-sweep.sh` | 38 | `DENYLIST="lock door trunk hood sunroof sunshade window windows_all win_*"` | **giữ nguyên** ✅ (khớp `CtlSafetyPolicy.CONFIRM_REQUIRED`, có bài canh `TestBridgeSafetyContractTest` giữ hai bên khớp) — §D6–D9 **cố ý gõ tay** | ✅ |
| `90-collect.sh` | 53 | *"Ba tệp bằng chứng MỚI của bộ **1.53**"* | 1.79 · **thêm** tệp mới của buổi này: `hal-sweep.csv` · `50-keys-table.md` · `u8a-*.png` · `f4-*.png` | [CHỮ] |
| `90-collect.sh` | 94 | *"Vòng kiểm quyền đủ **5 điều kiện**"* | **7** | [SAI SỐ] |
| `90-collect.sh` | 102 | *"OTA bản đang cài → **1.47** trên xe"* | đọc từ tên tệp `apk/` (như `40-ota.sh` §9), **đừng hardcode** | [SAI SỐ] |
| `90-collect.sh` | 92-105 | checklist **14 dòng** của 1.53 | **75 dòng** của §2 runbook này (hoặc trỏ tới §2 thay vì chép) | [CHỮ] |
| `run-all.sh` | 33 · 36 · 42 | tiêu đề *"KACHI **1.53** — BỘ TEST LÊN XE"* · trỏ `playbook: …1.53.md` · *"TỰ ĐỘNG HOÁ (mới, 1.53)"* | **1.79** + trỏ **RUNBOOK này**; thứ tự chạy `00→10→20→40→30→50→70→60→90` phải cập nhật theo **8 phase** của §1 (nhất là: **71-hal-sweep** chưa có trong `run-all.sh`) | [CHỮ] + **[CHẶN]** (thiếu bước 71) |
| **THIẾU tệp** | — | `adb_raw.py` chỉ ở `/tmp/adb_raw.py` ([ĐO] 3 231 B) | lưu `scripts/vehicle/kachi/adb_raw.py` — **đường DUY NHẤT vào được xe hôm 18-09**; mất nó là **mất kênh đo** (nợ đã nêu ở 2 doc trước, vẫn chưa làm) | **[CHẶN]** |
| **THIẾU tệp** | — | không có script cho §F3 (kill `:tts`) | thêm `72-tts-kill.sh` — bẫy vòng lặp + chụp pid/a11y/logcat trước-sau (đường nóng #0 là mục **quan trọng nhất** của buổi mà chưa có script nào) | [SAI SỐ] |

**Tổng**: **7 mục [CHẶN]** · **7 mục [SAI SỐ]** · **9 mục [CHỮ]** · 3 mục đã ✅ (`20-datums.sh` đếm động · `40-ota.sh` đọc tên tệp · `71` DENYLIST).
⇒ **Nếu chỉ sửa được một thứ trước khi ra xe**: `KACHI_LOG_TAGS` của `_common.sh` (6 tag) — nó làm mù **3 phase** (B1, C2, F3).

---

## 4. KIỂM MÂU THUẪN GIỮA 4 DOC MẢNG (đã soát từng cặp)

| # | Hai chỗ | Mâu thuẫn | Giải quyết trong runbook này |
|---|---|---|---|
| 1 | `2-slot-cast.md` §3-E **CẤM** `am display move-stack` ↔ `oncar-playbook-kachi-1.53.md:432,438` **kỳ vọng** log `am display move-stack <stack> 2` ↔ `.kiro/steering/project-context.md` §5 mục X2 mô tả cùng thứ | **THẬT** — code **đã đổi** sang `am stack move-task` từ quality-review 2026-09-15 (`AppMover.kt:112`), và `move-stack` treo system_server 3/3 trên chính DiLink3 | **Theo `2-slot-cast.md`**: lệnh vào **danh sách CẤM §0.6** (đặt ở đầu runbook để người vận hành thấy — hiện lệnh cấm chỉ nằm trong KDoc code + doc archive) · §E8c biến nó thành **cổng DỪNG** (thấy `move-stack` ⇒ bản build sai) · §2.1 ghi việc sửa playbook + `project-context.md` |
| 2 | `4-system.md` §11 xếp **P8 → K5** ngay sau §0 ↔ `2-slot-cast.md` §4 bước 1 nhồi VietMap+YouTube vào ô ↔ `4-system.md` §2c đặt `coolant_temp` lên màn | **THẬT về thứ tự** — cả hai đổi bố cục/màn, mà K5 cần **bố cục MẶC ĐỊNH + 5 phút đứng yên** để so với mốc [ĐO xe 1.66] | **K5 = B1, trước mọi bước nhồi ô**; cảnh báo in đậm ở đầu phase B. B6 (`coolant_temp`) và E1 (nhồi ô) đứng **sau** B1 |
| 3 | `3-voice.md` §5 xếp **Việc 3 (`:tts` kill) TRƯỚC** Việc 1–2 vì *"phím chết thì không nói được câu nào"* ↔ yêu cầu xếp voice (C) trước phím (F) | **GIẢ** — cầu `say` **bơm CHỮ**, không cần mic **và không cần phím** | Giữ **C trước F**, nêu lý do ở đầu phase C; và chuyển đúng cái lo của `3-voice.md` thành **tiền đề A4** (a11y BOUND) — nó gate cả C lẫn F. Kill test (phá) ở F3 |
| 4 | `3-voice.md` §3.2 kill bằng `kill -9` từ shell ↔ uid 2000 signal tiến trình uid khác | **[CHƯA BIẾT] chưa ai đo** — trên bản production `kill -9` app pid thường trả `Operation not permitted` | **Thang 3 đường F3-cmd**: (1) `run-as` (cùng uid ⇒ không thể bị từ chối) là đường chính ⇒ đó là lý do **§0.1 khuyến nghị `vehicleTest`**; (2) shell thẳng; (3) chờ crash **tự nhiên** ở F4 (**bằng chứng mạnh nhất**, ưu tiên nếu bắt được) |
| 5 | `4-system.md` §11 xếp **F4 trước S5** (*"S5 cần kênh shell"*) ↔ F4 đường C xoá `adb_keys` **mất kênh đo** | **GIẢ một phần** — F4 đường **B** chỉ xoá `files/adb.key` **của Kachi**, KHÔNG đụng uỷ quyền laptop | H1 (F4) đứng **ngay trước** H2 (S5) trong phase H, **dùng đường B**; đường C ghi rõ *"làm SAU CÙNG"* |
| 6 | `1-hal.md` §4.B sweep `lock`/`door`/kính/nóc ↔ `71-hal-sweep.sh:38` DENYLIST **không tự bắn** chúng | **GIẢ** — hai thứ khác nhau: script auto vs gõ tay có người nhìn | §D ghi rõ: script lo **D1–D5 + D11–D14**, **D6–D9 gõ tay**; DENYLIST **giữ nguyên** (có bài canh) |
| 7 | `2-slot-cast.md` §3-C ảnh U8a khi freeform focus ↔ `4-system.md` §8 U8a đo bằng ô freeform | **GIẢ** (trùng việc, không trái nhau) | **Gộp thành E4b** — một lượt, ba ảnh (home / ô-focus / overlay-voice), lấy miễn phí trong bước E4 |
| 8 | `3-voice.md` F5 **huỷ Phase 2** của `oncar-runbook-hey-kachi.md` ↔ runbook đó vẫn ghi *"đo hit/20 ở 3 mức ồn"* | **THẬT** — đo hôm nay ra **0/20 bất kể model**, là **âm tính giả** | **Phase G có cổng vào tường minh** (Phase 0 đạt **AND** guard chia lõi đã ship). Hôm nay ⇒ G rút về **chỉ probe CPU (G1)** + load (C4). §2.1 ghi việc sửa runbook Hey Kachi |
| 9 | `4-system.md` §10.1 component HOME = alias `KachiHome` ↔ `oncar-playbook-kachi-1.53.md:688` dùng `KachiHomeActivity` | **THẬT** — alias ship `enabled=false`; `KachiHomeActivity` **không khai `CATEGORY_HOME`** | **Theo `4-system.md`**: H2 đường B dùng alias + `pm enable` trước; đường C thử component cũ **chỉ để sinh bằng chứng sửa doc** |
| 10 | `4-system.md` §10.5 P8 = **7** điều kiện ↔ playbook `:127,217,716` + `10-baseline.sh:38` + `90-collect.sh:94` ghi **5**, backlog ghi **6** | **THẬT** (đếm được từ `LauncherRequirements.kt:293-295`) | B2 đo **7 hàng**; §3 liệt 3 chỗ script/doc phải sửa; §2.1 ghi sửa backlog |
| 11 | `2-slot-cast.md` §5.2 dự đoán `am start --display <cụm>` **THÀNH CÔNG** ↔ ghi chép 09-14 *"Permission Denial"* | **GIẢ** — 09-14 hardcode `--display 1` (**không phải** cụm) | E8b ghi **cả hai nhánh**: nếu vẫn Denial **với id cụm ĐÚNG** thì `[SUY]` sai ⇒ dán `dumpsys display` khối cụm (**dữ liệu quý nhất** của khối cast) |
| 12 | `1-hal.md` §3.2 đọc GPS qua HAL ↔ dự án đã **retire quyền location** (`DeadReckonRetirementTest`) | **GIẢ về cơ chế, THẬT về ý nghĩa** — đường HAL BYDAuto không dùng `LocationManager`, không bật lại quyền Android nào | B4 **đo (chỉ-đọc, vô hại)**; **nối vào UI = quyết định owner**; ⛔ cấm tuyệt đối `LOCATION_*_SET`/`setLocationInfo` (ghi vị trí VÀO xe) |
| 13 | `2-slot-cast.md` §3-D3 dùng `wm density` ↔ `VdAppHost` đã đặt density trong `createVirtualDisplay` | **THẬT về phạm vi** | E5 ghi ⛔: `wm density` **chỉ** cho display 0 / cụm; **KHÔNG** cho `<vdSlot>` (lệnh shell ghi `display_settings.xml`, **sống qua reboot**, phải dọn) |

**Kết luận** (13 cặp đã soát): **6 mâu thuẫn THẬT** (#1, 2, 8, 9, 10, 13 — đều đã chọn bên đúng và ghi việc sửa doc ở §2.1) · **6 mâu thuẫn GIẢ** (#3, 5, 6, 7, 11, 12 — hiểu nhầm phạm vi, đã hoà giải trong luồng chạy) · **1 [CHƯA BIẾT]** (#4 — giải bằng thang 3 đường kill). **Không còn option nào của 4 doc chống nhau.**

---

## 5. NGUỒN

**Bốn doc mảng của gói này** (chi tiết + `file:line` đầy đủ nằm ở đó):
`oncar-master/1-hal.md` (546 dòng · ma trận sweep + 8 lỗi vá được off-car) · `oncar-master/2-slot-cast.md` (374 dòng · option A–I + cây quyết định 7 mức) · `oncar-master/3-voice.md` (341 dòng · 5 phát hiện đổi kế hoạch) · `oncar-master/4-system.md` (803 dòng · 8 mục + 6 đính chính doc cũ).

**Runbook/playbook đã có** (runbook này **gom**, không thay): `oncar-playbook-kachi-1.53.md` (quy trình · §1.2 bốn thứ tự chụp · §5 **21 bẫy** — vẫn là nguồn tra bẫy) · `oncar-playbook-kachi-1.55.md` (delta) · `oncar-runbook-hey-kachi.md` (Phase 0–3 wake word) · `oncar-verify-1.63-2026-09-15.md` §6f/§6g/§6h.

**Diagnostics on-car đã có**: `oncar-trace-2026-09-16b/{featmap-20260916.json, hal-reads.txt, wiper-poll.txt}` (nguồn của **mọi** feature-id ở §D) · `oncar-trace-2026-09-16.md` · `perf-profile-2026-09-16.md` (mốc K5 + §9.3 K8) · `oncar-piper-crash-binding-2026-09-18.md` · `oncar-voice-{cases-findings,music-vietmap,number-and-voicekey-bind}-2026-09-18.md` · `oncar-adb-wireless-broken-after-kachi-2026-09-18.md` · `adb-car-tunnel-macos.md` · `carlog-kachi-20260914-2044/session-findings.md` §X2 · `waze-into-slot-research-2026-09-14.md` · `archive/diagnostics/carplay-move-task-success-2026-08-01.md` (bằng chứng option D) · `archive/diagnostics/carplay-move-stack-npe-crash-2026-08-01.md` (trace crash 3/3) · `VEHICLE-TEST-V2.md:224`.

**Mã** (đọc 2026-09-19 tại HEAD `ecb0d97`): `app/build.gradle.kts:53-54,110-124` · `app/src/main/AndroidManifest.xml:99-124,205,252-258` · `KachiHomeActivity.kt:181-190,438,457` · `KachiHomeWiring.kt:383,395-400` · `RemotePiperSpeaker.kt:298` · `PiperTtsService.kt:131` · `VoiceGeocoder.kt:41` · `VoiceYoutubeResolver.kt:19` · `VoiceWakeListener.kt:286,293` · `VoiceWakeController.kt:25,48` · `VoiceLoadGuard.kt:59-61` · `VoiceOverlay.kt:164-180` · `LauncherRequirements.kt:293-295` · `CtlSafetyPolicy.kt:35-38` · `TelemetryRegistry.kt:160-161,175,230` · `ControlRegistry.kt:339-342` · `TestBridgeHal.kt:41` · `TestBridgeStore.kt:56` · `NavAccessibilityService.kt:78-80` · `NavConnect.kt:49` · `SpeedBadgeOverlay.kt:44,138-141` · `DisplayParse.kt:184-188` · `AppMover.kt:41,112` · `CarExecClusterProjectionCatalog.kt:52-80` · `AppMoverMoveStackFallbackTest.kt:75-78`.

**Script**: `scripts/vehicle/kachi/{_common.sh,00-connect.sh,10-baseline.sh,20-datums.sh,30-profiles.sh,40-ota.sh,50-keys.sh,60-cast.sh,70-voice.sh,71-hal-sweep.sh,90-collect.sh,run-all.sh}` — đối chiếu ở §3.
