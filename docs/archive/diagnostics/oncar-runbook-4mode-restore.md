# ON-CAR RUNBOOK — Khôi phục "Giữa + ETA" + map 4 mode nav-cụm · phiên tới

> Xe: BYD Seal DiLink 3.0 · Android 10 (API 29) · KHÔNG root · **parked-only** (số P + phanh tay).
> Dọn = **power-cycle nút nguồn vật lý** (KHÔNG tính `adb reboot`). **HỎI LẠI IP** (hotspot đổi) — đừng đoán.
> Nguyên tắc: **KHÔNG assume** — map value↔menu phải readback trên xe; mỗi claim trace về nguồn.
> Spec: `docs/specs/cluster-nav-4mode-restore.html`. Điều tra gốc: `oncar-handoff-2026-08-14.md`, `oncar-2026-08-13-amap-cluster-menu-and-op39-rootcause.md`.
> Phối hợp: máy verify được (rc, readback, version, service list, logcat) → agent tự chạy adb; trực quan (menu xám? cụm hiện gì?) → **hỏi owner 1 câu ngắn** sau mỗi chùm.

---

## ✅ FINDINGS 2026-08-14 PM (cập nhật premise — ĐỌC TRƯỚC KHI CHẠY RUNBOOK)

Off-car git + on-car readback (xe **1.19**, IP session này):

**1. "Magical" recovery = app RESTART, KHÔNG phải version/pipeline GMaps.**
- Code chế độ cụm **y hệt 1.17↔1.19**: `NavigationHudOwner`/`NavRepository`/`BydHal` diff **RỖNG**; MainActivity/Prefs đổi chỉ do voice-key `[git diff 3cdac14..80fc467]`.
- OTA hôm nay = **update `-r` lúc 16:24** (firstInstall 2026-08-11 giữ) → **pref KHÔNG bị xoá** `[dumpsys package firstInstallTime/lastUpdateTime]`.
- Sáng (process 1.17 cũ) = small strip; 16:24 OTA **kill+restart app** → chiều (process mới re-assert lúc start) = centre. Khớp lời owner ở S5 "1.15 lên là active ngay".
- ⇒ **S5 đã có đáp án: fresh install/restart CÓ activate centre — nhưng chỉ lúc START.**

**2. ⛔ Selector 4-mode là DEAD CONTROL (phát hiện quan trọng nhất).**
- Dropdown app hiện **"Màn hình nhỏ" (pref=SMALL=2)** NHƯNG cụm đang hiện **Giữa+ETA (centre)** — mâu thuẫn `[owner readback 2026-08-14]`.
- Owner xác nhận: **chọn bất kỳ option nào trong 4 mode → cụm KHÔNG đổi.**
- ⇒ Đường ghi `SET_NAVI_SCREEN_STATUS_SET 0x4C10E015` (qua `NavigationHudOwner`/`BydHal.setNaviScreenStatus`, kể cả `reapply` 4→2) là **NO-OP** để chuyển mode trên trim này. Centre hiện KHÔNG do selector — nhiều khả năng do **AmapService guidance-broadcast pipeline** hoặc trạng thái OEM sau restart. Củng cố "ĐÃ THỬ FAIL" cũ.

**3. Value↔menu map (R1): pref=2 KHÔNG ra "màn hình nhỏ" trên cụm** → nhãn `NAV_SCREEN_SMALL=2`/`SIMPLE=1`/`FULL=3` trong `Prefs.kt` là **GUESS**, không đáng tin (hoặc write vô hiệu hoàn toàn).

**4. ⛔ Write-probe `0x4C10E040` (EASY_NAVI) — REJECTED, KHÔNG phải lever (2026-08-14 PM, on-car).**
- navopen reaches `instr` device ✓. Read: `setting 4C10E040`, `instr 1F704010`, `instr 1F701010` đều = **-2147482648** (sentinel KHÁC `-10011` "write-only" của 015/01D/03A).
- Write sweep `setraw setting 4C10E040 = 0,1,2,3` → **rc=-2147482648 cả 4** (rejected; tương phản `015` write rc=0). Screenshot cụm base vs 0 vs 3 **giống hệt** (Giữa+ETA, chỉ tick giờ/ETA) → **0 thay đổi**, centre nguyên vẹn `[on-car screencap]`.
- ⇒ EASY_NAVI **not provisioned/writable trên trim này**; KHÔNG phải bộ chọn 4-mode. Kết hợp `015` (bị AmapService ghim=3) → **mọi HAL id với tới ĐÃ LOẠI**. Khớp regression doc: 4-mode + centre = **trạng thái OEM/AmapService nội bộ**, không drive bằng 1 HAL id.
- **Việc còn lại:** (a) test menu OEM **GỐC** "Nav trên cụm" (Cài đặt xe, KHÔNG phải dropdown app) có đổi cụm không → phân biệt OEM-path còn sống vs stuck MCU; (b) RE tìm app/model implement menu 4-option + HAL call nó phát (`HudOptionDisplayModel` chỉ là HUD on/off nhị phân `03A`, KHÔNG phải 4-mode).

**5. ⛔⛔ Menu OEM GỐC (AMAP nav-cụm) — bấm cả 4 lựa chọn: KHÔNG effect (2026-08-14 PM, on-car).**
- Owner bấm lần lượt 4 option trên **menu chỉnh AMAP nav-cụm GỐC** (không phải dropdown app) → cụm **đứng im, 0 thay đổi** `[owner on-car]`.
- ⇒ **KHÔNG phải bug app mình.** Ngay cả đường OEM chính chủ cũng không switch được mode live → xác nhận **stuck MCU / coding-lock trên trim này**. Khớp: nhiều instrument write khác cũng "REJECTED / not provisioned" (`hud-cluster-injection-findings-2026-08-10.md`).

## 🧭 VERDICT (2026-08-14 PM) — LIVE 4-MODE SWITCH = KHÔNG KHẢ THI (software) TRÊN TRIM NÀY
Đã loại **HẾT** đường: (a) dropdown app · (b) HAL `0x4C10E015` (AmapService ghim=3) · (c) `01D/03A` (write-only, không phải mode) · (d) `EASY_NAVI 0x4C10E040` + `instr 0x1F70xxxx` (write **REJECTED** rc=-2147482648) · (e) **menu OEM gốc (no effect)**. Mode do **AmapService/MCU quyết lúc (re)start**, không đổi live bằng bất kỳ menu nào. Sáng small ↔ chiều centre = khác nhau CHỈ qua restart (trigger chưa pin).

### 🔧 HƯỚNG THỰC TẾ (đổi mục tiêu: "switch mode" → "reliably có Giữa+ETA")
- **[UI] Dropdown 4-mode = DEAD CONTROL** → gỡ / thay bằng trạng thái trung thực (vi phạm "no dead button"). Đừng hứa switch cái không switch được.
- **[C-3 reliability] Mục tiêu khả thi = Giữa+ETA hiện ổn định.** Lever DUY NHẤT đã proven = app/service **(re)start** thiết lập render → app nên re-trigger đường establish (status2 + screen3 + GUIDE_INFO_SIMPLE full-frame) lúc **bật Nav+HUD / boot**; power-cycle = recovery.
- **[RE — research, KHÔNG hứa]** off-car: tìm AmapService đọc gì lúc start để quyết layout (small vs centre) = cái "trigger" regression. Nếu là prop/setting persist → preset trước restart; nếu MCU-coding → chấp nhận limitation.

### 🔧 FIX cũ (giữ để tham chiếu — nay OBE bởi VERDICT)
- **[BLOCKER] Tìm lever THẬT trước tiên.** Selector hiện vô dụng → PHẢI tìm cơ chế thật điều khiển 4 mode (Đơn giản/Nhỏ/Toàn/OFF). **KHÔNG** patch thêm vào đường `0x4C10E015` đã chứng minh no-op. Hướng: RE `AmapService`/`Setting` xem OEM đổi menu "Nav trên cụm" bằng gì (broadcast? intent? setting khác?), rồi replicate. (S2 `ICarHudService` **loại** — không có trong `service list` trên xe `[readback]`.)
- **[C-1] Sau khi có lever + map value↔menu thật (R1):** set giá trị proven cho từng mode.
- **[C-3] Sau khi có lever:** re-assert mode **lúc bật Nav+HUD + định kỳ**, không chỉ lúc app start (vì hiện chỉ START mới set được → process chạy dài rơi small không tự cứu, phải reinstall/restart).

---

## 0. TL;DR mục tiêu phiên
1. **Khôi phục ngay** cụm về **Đơn giản (Giữa + ETA)** (làm owner dùng được liền).
2. **Học** đúng **chuỗi mở khoá** (R2) + **value↔menu map cho cả 4 mode** (R1) → để code fix persist.
3. Xác định **app-uid gọi được không** / cần **dadb loopback** (R4/C-3).

Thứ tự thiết kế: thử lever **rẻ + mới + readback được TRƯỚC**, mò raw sweep sau. **Dừng ở lever đầu tiên mở được menu.**

---

## 1. CHUẨN BỊ (30s)
```bash
export VEH=<vehicle-ip>:5555              # HỎI owner IP
ADB="$HOME/Library/Android/sdk/platform-tools/adb"
"$ADB" connect "$VEH"; "$ADB" -s "$VEH" shell dumpsys package com.byd.clusternav | grep -E 'versionName|versionCode'
# helpers
NAV(){ "$ADB" -s "$VEH" shell "CLASSPATH=/data/local/tmp/navopen.jar app_process /system/bin com.byd.navopen.NavOpen $*"; }
SHOT(){ "$ADB" -s "$VEH" shell "fission_screencap -d 0 -p /data/local/tmp/p.png"; "$ADB" -s "$VEH" pull /data/local/tmp/p.png "./$1"; }
```
- Cụm = `fission_screencap -d 0` (thấy đồng hồ = đúng). IVI = `-d 1`.
- navopen sẵn ở `/data/local/tmp/navopen.jar` (uid shell). `getraw` các id nav → `-10011` = SET-only, **bình thường**.
- **Setup cảnh:** power-cycle nút nguồn → boot sạch → **Cast OFF** → mở **GMaps** dẫn 1 tuyến thật (đứng yên OK) → bật **Nav+HUD** trong ClusterNav.

### ⛔ ĐÃ THỬ — FAIL post-reboot, ĐỪNG chạy lại như "fix" (nguồn: `cluster-centre-nav-regression-2026-08-13.md`)
- Ghi **chỉ** `0x4C10E015` (app selector ×4 · `navopen setraw 4C10E015 3` · `navopen` full=3) → **không mở menu, không lên centre**.
- Combo `01D=1 + 03A=1 + 015=3` (navopen) → **vẫn không**.
- navopen full frame (device thật, rc=0) → cụm **không render centre**.
→ Nên phiên này thử **lever MỚI**: ICarHudService enable (S2) · EASY_NAVI 0x4C10E040 (S4) · broadcast-first (S4) · fresh install (S5).

---

## 2. S0 — Trạng thái boot sạch (làm ĐẦU TIÊN, trước khi ghi gì)
**Máy:**
```bash
"$ADB" -s "$VEH" shell dumpsys package com.byd.clusternav | grep -E 'versionName|versionCode'   # xác nhận bản đang chạy
"$ADB" -s "$VEH" shell "ps -A | grep -iE 'amapservice|clusternav'"                                # renderer + app còn sống?
SHOT s0-cluster.png
```
**❓ Hỏi owner (1 câu):** mở **Cài đặt xe → "Nav trên cụm"** — menu 4 mode **chọn được** hay **xám/mất**? Và cụm đang hiện **Giữa+ETA** hay **dải nhỏ ở đỉnh**?
> Ghi lại: đây là **baseline boot sạch** — quyết định C-3 có cần app tự re-assert mỗi boot không (Q1).
> Nếu menu **chọn được sẵn** → nhảy thẳng **S3 (readback map)**. Nếu **xám** → **S2**.

---

## 3. S2 — Lever MỚI: ICarHudService enable (ưu tiên, readback được)
**[RE: com.byd.car.feature.vision.ICarHudService]** — API "enable" cấp cao, ĐỌC được (khác HAL 015 SET-only).
```bash
# BƯỚC 0: service có trong servicemanager không?
"$ADB" -s "$VEH" shell service list | grep -iE "hud|vision|byd|nav"
```
- **Nếu THẤY tên service `<svc>`** (vd chứa "hud"/"vision"):
```bash
"$ADB" -s "$VEH" shell service call <svc> 23        # isNavigationMapEnabled  → ĐỌC trước (0/1?)
"$ADB" -s "$VEH" shell service call <svc> 24 i32 1  # setNavigationMapEnabled(true)  ← ứng viên mở menu
"$ADB" -s "$VEH" shell service call <svc> 20 i32 1  # setDynamicNavigationEnabled(true)
"$ADB" -s "$VEH" shell service call <svc> 22 i32 1  # setNavigationFusionEnabled(true)
"$ADB" -s "$VEH" shell service call <svc> 23        # đọc lại → có đổi 0→1 không?
SHOT s2-after-enable.png
```
> ⚠️ txn code (23/24/20/22) từ RE stub `ICarHudService`. Nếu `service call` trả `Parcel … 0` đều đặn hoặc lỗi → service này KHÔNG ở servicemanager.
- **Nếu KHÔNG thấy** (SPI-bound, không phải binder tên): **không gọi được bằng `service call`** → cần diagnostic build gọi qua BYD Car SDK (`Spi.getService(ICarHudService).setNavigationMapEnabled(true)`). **Báo mình kết quả `service list`** để mình dựng build đó. Trong lúc chờ → tiếp **S4**.

**❓ Hỏi owner:** sau các lệnh trên, mở lại Cài đặt "Nav trên cụm" — **menu hết xám chưa?** Cụm có nhảy về Giữa+ETA không?
> **HẾT XÁM ⇒ tìm ra lever.** Ghi rõ lệnh nào (24? 20? 22?). Nhảy **S3**.

---

## 4. S4 — Mò unlock (chỉ khi S2 chưa mở). Sau MỖI bước, **mở Cài đặt xem menu hết xám chưa**
```bash
# S4a — EASY_NAVI (0x4C10E040) — MỚI, tên khớp "Đơn giản", chưa từng thử:
for v in 0 1 2 3; do NAV setraw setting 4C10E040 $v; sleep 2; SHOT s4a-easynavi-$v.png; done
# S4b — bơm broadcast "đang dẫn" TRƯỚC rồi mới OPEN (thứ tự có thể là điều kiện mở):
"$ADB" -s "$VEH" shell "am broadcast -a AUTONAVI_STANDARD_BROADCAST_SEND --ei KEY_TYPE 10001 --ei TYPE 1 \
  --ei EXTRA_STATE 1 --ez IS_BYD_MAP false --ei NEW_ICON 3 --ei SEG_REMAIN_DIS 444 --es NEXT_ROAD_NAME 'Le Loi' \
  --ei ROUTE_REMAIN_DIS 6000 --ei ROUTE_REMAIN_TIME 300 --es SEG_REMAIN_DIS_AUTO '444 m'"
NAV                     # navopen demo OPEN (status2 + screen3 + 1 frame)
SHOT s4b-bcast-open.png
```
**❓ Hỏi owner:** bước nào làm **menu hết xám** / cụm lên centre?
> Đây là **fix gốc regression (R2)**. Ghi CHÍNH XÁC chuỗi. Nếu S4 cũng fail → **S5**.

---

## 5. S5 — Fresh install có tự mở khoá không (trả lời "app tự unlock?" — Q + R4)
> Chỉ làm nếu S2/S4 chưa mở, HOẶC để test persistence cuối phiên. `pm clear` xoá pref (mất về default FULL=3).
```bash
"$ADB" -s "$VEH" shell pm clear com.byd.clusternav
"$ADB" -s "$VEH" install -r apk/ClusterNav-1.15-release.apk   # bản owner nhớ "1.15 lên là active ngay"
# mở app → bật Nav+HUD → Cast OFF → GMaps đang dẫn → chờ ~10s
"$ADB" -s "$VEH" shell "logcat -d -s NavigationHudOwner" | tail
SHOT s5-after-install.png
```
**❓ Hỏi owner:** cụm **tự lên Giữa+ETA** không?
> **CÓ** ⇒ app tự mở khoá khi pref=default → regression = pref bị đổi value không-mở-khoá; fix C-1 = luôn re-assert value proven lúc boot/bật.
> **KHÔNG** ⇒ app KHÔNG tự mở → cần lever S2/S4 chạy trong app (C-3, có thể qua dadb loopback).

---

## 6. S3 — READBACK map 4 mode (khi menu ĐÃ chọn được — hết đoán)
```bash
VEH=$VEH MODE=readback bash scripts/vehicle/nav-screen-mode-probe.sh
#  → script nhắc tự tay chọn: Đơn giản → Màn hình nhỏ → Toàn màn hình → OFF
#  → readback 0x4C10E015 + 0x4C10E01D + 0x4C10E03A + 0x4C10E040 mỗi lần → nav-mode-probe/value-map.txt
```
> **Đây là R1** — con số THẬT cho cả 4 mode. Nếu getraw vẫn −10011 (SET-only) thì đối chiếu bằng screenshot sweep:
```bash
VEH=$VEH MODE=sweep bash scripts/vehicle/nav-screen-mode-probe.sh   # ghi 015=0..7 + combo + easy_navi=0..3, chụp mỗi bước
```
**❓ Hỏi owner:** đối chiếu screenshot — value nào ra **Đơn giản / Nhỏ / Toàn / OFF**?

---

## 7. S6 — Verify render + persist (sau khi có lever + map)
```bash
# set value "Đơn giản" (từ S3) bằng đường đang chạy được (service call hoặc navopen), rồi:
"$ADB" -s "$VEH" shell "logcat -d -s NavigationHudOwner AbsBYDAutoDevice" | tail
SHOT s6-simple.png
```
**❓ Hỏi owner:** cụm lên **Giữa + ETA** ổn định (không chớp tắt)? Mũi tên rẽ **đúng hướng**?
- **Persist:** power-cycle nút nguồn → bật lại Nav+HUD → cụm tự lên lại chưa? (nếu không → cần C-3 re-assert on-boot.)

---

## 8. BÁO LẠI (checklist gửi về để chốt code)
- [ ] **S0:** boot sạch — menu xám/mất hay chọn được? cụm hiện gì?
- [ ] **S2:** `service list` có `<svc>` hud/vision không? `isNavigationMapEnabled` đọc ra 0/1? `setNavigationMapEnabled(true)` mở menu không?
- [ ] **S4:** `EASY_NAVI 0x4C10E040` value nào có tác dụng? broadcast-first + OPEN mở menu không?
- [ ] **S5:** fresh install 1.15 tự lên Giữa+ETA không?
- [ ] **S3:** `value-map.txt` — số cho **4 mode** (015 [+01D +03A +040 nếu combo]).
- [ ] **S6:** set Đơn giản → centre lên? mũi tên đúng? persist qua power-cycle?
> Có mấy cái này là mình chốt được **Candidate C** (C-1 value map · C-2 combo · C-3 re-assert/loopback) và bump 1.19.

---

## 9. DỌN DẸP (trước khi rời/lái)
- **Power-cycle nút nguồn vật lý** — dọn HAL/opcode + trạng thái compositor.
- Nếu đã sweep value lạ / OFF → chọn lại chế độ mong muốn (menu OEM hoặc selector app) sau power-cycle.

---

## 10. THAM CHIẾU
- Spec: `docs/specs/cluster-nav-4mode-restore.html` (Candidate A/B/B2/C).
- Regression + đã-thử-FAIL: `docs/diagnostics/cluster-centre-nav-regression-2026-08-13.md`.
- Cơ chế menu + AmapService: `docs/diagnostics/oncar-2026-08-13-amap-cluster-menu-and-op39-rootcause.md`.
- Handoff kế hoạch §A0/§A1-3/§B: `docs/diagnostics/oncar-handoff-2026-08-14.md`.
- Probe: `scripts/vehicle/nav-screen-mode-probe.sh` (readback|sweep; đã thêm `0x4C10E040 EASY_NAVI`).
- RE: `~/Library/Caches/clusternav-re/…/com/example/amapservice/AmapService.java` · `…/com/byd/feature/setting/Setting.java` (015/01D/03A/040) · `…/com/byd/car/feature/vision/ICarHudService.java` (enable API, txn 20/22/23/24).
- Feature-ids: `SET_NAVI_SCREEN_STATUS_SET 0x4C10E015` · `SET_MAP_SENDING_STATUS_SET 0x4C10E01D` · `SET_DYNAMIC_NAVI_FUNCTION_STATUS_SET 0x4C10E03A` · `SETTING_EASY_NAVI_SIGNAL_MAP_TYPE 0x4C10E040`.
