# On-car 2026-09-06 — 2 lỗi cần sửa OFF-CAR (v1.32, xe BYD AUTO, ADB qua wifi)

> **Trạng thái**: NOTED — owner dặn "check nhanh, KHÔNG fix, note off-car làm sau". Đây là bằng chứng [ĐO] trên xe + hướng sửa. CHƯA sửa code.

## BUG A — Ghế mát/sưởi tự động KHÔNG chạy (HAL từ chối)

**[ĐO] logcat trên xe** (mở app, ghế trước Lái đặt Mức 2, mode Làm mát):
```
SeatComfort: ghi ghế=0 mode=COOL featureId=0x43101010 value=3 rc=-2147482648
SeatComfort: áp xong: 1 ghế, mode=COOL, seats=[0, 1]
```
- Cấu hình ĐÚNG (enabled + set mức + Seal 2 ghế). Lệnh **tới được HAL** (không `AcDevice null`).
- `rc = -2147482648` = **`NOT_PROVISIONED_RC`** (`BydHal.NOT_PROVISIONED_RC`, = `Int.MIN_VALUE + 1000` = `0x800003E8`). HAL **từ chối** feature này.
- Đây là **ghế TRƯỚC** (id `0x43101010` RE-proven, không phải rear-suy-luận) ⇒ lỗi KHÔNG do rear extrapolation.

**[SUY] gốc:** ghi qua `BYDAutoAcDevice.set(int[]{0x43101010}, ev)` (setInt) → device AC trả NOT_PROVISIONED cho feature-id này. App tham chiếu proven (`com.byd.mecanum.dashboard`) ghi qua **binder với deviceType 1023 TƯỜNG MINH** (`m.Y(binder,1023,fid,val)`), KHÔNG qua device AC. Giả định "BYDAutoAcDevice = device 1023" trong `SeatComfort.kt` KDoc **SAI** (OpenBYD chỉ là stub, không xác nhận được; feature 0x43101010 không có trong OpenBYD).

**Hướng sửa OFF-CAR — ĐÃ XÁC NHẬN [ĐO] bằng decompile app OEM `com.byd.airconditioning` (`/tmp/byd-ac`, module `airseating`):**
Ghế điều khiển qua **`BYDAutoSettingDevice`** (= `BydHal.SETTING`, ClusterNav ĐÃ CÓ + đã ghi được cho nav screen mode) — **KHÔNG phải AC device, KHÔNG raw feature-id**. Method TÊN:
- Mát: `setSeatVentilatingState(int seatID, int state)`
- Sưởi: `setSeatHeatingState(int seatID, int state)`
- (vô-lăng: `setSteeringWheelHeatingState(int state)`)
- **seatID 1-based:** 1=Lái · 2=Phụ · 3=sau-trái · 4=sau-phải (ClusterNav đang **0-based → SAI, phải +1**).
- **state:** **1=Tắt · 2=Thấp(Mức1) · 3=Cao(Mức2)** (xe 2 mức: state 2..3; xe 3 mức: 2..4). ClusterNav gửi 2/3 cho mức active = ĐÚNG; OFF thật là **1** (không phải 0) — apply-on-start skip OFF nên không sao, nhưng nếu muốn TẮT chủ động phải gửi 1.
- Feature-id thật (chỉ để đăng ký listener, KHÔNG để ghi) = hằng `BYDAutoFeatureIds.SET_{DRIVER,PASSENGER,REAR_LEFT,REAR_RIGHT}_SEAT_{VENTILATING,HEATING}_STATE` — nên `0x43101010` trong `SeatComfort.kt` là RE SAI, bỏ.

**Việc off-car:** viết lại `SeatComfortApplier` + `SeatComfort` ghi qua `BydHal.SETTING` gọi `setSeatVentilatingState`/`setSeatHeatingState`(seatID **1..4**, state 2/3); thêm helper `BydHal.callSet2Int(dev, "setSeatVentilatingState", seatID, state)` (reflection method-tên 2 int, trả rc); BỎ hẳn đường `BydHal.AC` + `0x43101010` + `HAL_VALUE`(giữ 2/3 nhưng OFF→1). Sửa `SeatDiagramView`/UI seat index 0-based→map +1 khi gọi HAL (giữ UI 0-based). Cực-tin-cậy vì OEM dùng đúng path này + BYDAutoSettingDevice đã proven ghi được trên xe. **Verify lại trên xe** sau khi implement.

## BUG B — VietMap bị gọi lên liên tục (loop flash)

**[ĐO] logcat + [ĐO] code:**
- `VietMapAutostart: autostart silent-bg → launch activity VietMap + trả nền [bubbleOn=true running=true]` — **launch KỂ CẢ khi VietMap đang chạy** (`running=true`).
- `maybeAutoStartVietMap()` gọi từ **`onCreate`** (MainActivity:318) → `VietMapAutostart.ensureRunning` → `runNow`.
- `ensureRunning` = `Thread { runNow(...) }` — **KHÔNG dedup / KHÔNG cooldown**.
- `runNow` nhánh bóng bật: `bubbleOn || !running` (bóng bật ⇒ luôn true) → `monkey launch VietMap` → `Thread.sleep(1500)` → `monkey launch lại returnToSelfPkg (ClusterNav)`. ⇒ mỗi onCreate = VietMap nhảy lên foreground 1.5s rồi ClusterNav quay lại.
- `VmOverlayPos: gửi VM_BUBBLE_POS` ~2s/lần (refresh loop) — **vô hại** (chỉ broadcast vị trí, KHÔNG launch). Steady-state 18s: **0** lần launch → loop KHÔNG chạy khi app foreground-đứng-yên; kích hoạt là **onCreate/recreate/boot**.

**[SUY] gốc loop:** mỗi lần `onCreate` (mở app · **recreate khi đổi ngôn ngữ/giao diện** · app auto-open lúc boot · quay lại từ recents nếu activity bị recreate) đều chạy màn "launch VietMap → sleep 1.5s → trả ClusterNav". Bóng bật ⇒ bỏ dedup `running` (đổi ở 1.32) ⇒ churn. Nếu onCreate lặp (recreate/relaunch) ⇒ VietMap nhảy lên lặp = "loop" owner thấy.

**Hướng sửa OFF-CAR:**
1. Thêm **dedup/cooldown** vào `VietMapAutostart` (vd chỉ launch 1 lần/phiên-app hoặc mỗi N phút; nhớ mốc thời gian last-launch).
2. **Đừng launch nếu VietMap activity đã mở** (không chỉ `pidof` process — cần check activity/nav thật; hoặc chỉ launch khi bóng chưa init).
3. Cân nhắc KHÔNG gọi `maybeAutoStartVietMap()` khi là **recreate** (đổi theme/ngôn ngữ) — chỉ gọi lần onCreate đầu / boot.
4. Bỏ/giảm màn "sleep 1500 + trả foreground" nếu tìm được cách để bóng init mà không giật foreground.

## FINDING C — PM2.5 tự lọc: gần như ĐÃ CHẠY (khác ghế), 1 lỗi nhỏ popup

**[ĐO] logcat trên xe** (mở app, công tắc PM2.5 bật, khí sạch level=1):
```
Pm25Filter: enablePurificationFunctionPrompt(0) rc=-2147482645
Pm25Filter: setAutoCleanAirState(1) rc=0
Pm25Filter: read level=1 (Excellent)   ← khớp HAL BYDAutoPM2p5Device.getPM2p5Level=1
```
- **`setAutoCleanAirState(1) rc=0` = THÀNH CÔNG** → bật chế độ auto-clean liên-tục của xe. Method+device ĐÚNG (khớp OEM `AirCleanerDualChannelModel.mBydAutoAcDevice.setQuickCleanAirState/setAutoCleanAirState` trên BYDAutoAcDevice). Đọc mức ĐÚNG.
- **Verify KHÔNG cần bụi cao:** cơ chế = bật auto-clean của xe (không gate ngưỡng) ⇒ `setAutoCleanAirState(1)` chạy bất kể mức. Chỉ nhánh `setQuickCleanAirState` (lọc-ngay) mới cần level ≥ HEAVY(5) → test bằng hun khói/nhang gần cảm biến HOẶC tạm hạ ngưỡng off-car; nhưng đây là add-on, nhánh chính đã verify.
- **Lỗi NHỎ off-car:** `enablePurificationFunctionPrompt(0) rc=-2147482645` (= `Int.MIN_VALUE + 1003`, sentinel KHÁC NOT_PROVISIONED) — method tắt-popup xe không nhận. Lọc vẫn chạy; chỉ là popup nhắc lọc có thể vẫn hiện (owner muốn "không popup"). **Hướng off-car:** kiểm tra method này có trên BYDAutoAcDevice không / đúng arg chưa (grep app OEM `com.byd.airconditioning`), hoặc bỏ hẳn nếu autoClean đã đủ im. KHÔNG chặn tính năng lọc.

## FINDING D — Hero nav TRỐNG khi GMaps dẫn (đang CAST GMaps lên cụm)

**[ĐO trên xe]:** listener notification BÁM ổn định (0 reconnect/churn trong 12s sau lần reconnect khởi động). GMaps post noti nav ĐẦY ĐỦ DATA (dumpsys):
```
category=navigation  groupKey=navigation_status_notification_group  contentView=null
android.title   = "0 m"                               ← cự-ly tới rẽ
android.text    = "Trần Trọng Kim"                    ← tên đường
android.subText = "25 phút · 11 km · Dự kiến 12:56 CH" ← ETA
```
Nhưng hero (app) TRỐNG (owner xác nhận) + KHÔNG có log ingest/ghi-HAL nav (verbose off). ĐỒNG THỜI GMaps đang được **CAST lên cụm** (`BydAutoRotation: MapsActivity … reparentToDisplay displayId:1`).

**[SUY] gốc — điều tra off-car:**
- (a) Parser `NavNotificationListener` có thể không rút được từ **noti NHÓM** (`groupKey=navigation_status_notification_group`, nhiều noti con) — match nhầm summary/con khác thay vì con nav (title=cự-ly + text=đường). Đây là nghi số 1.
- (b) HOẶC ingest bị gate khi **Cast bật** (navOnlyMode=false chỉ nên chặn HAL-write-cụm, KHÔNG nên chặn cập nhật hero) — kiểm `NavRepository.ingest`/`updateHeroStrip` có gate theo cast không.
- (c) Nav+HUD master: NavConnect reconnect có chạy ⇒ `Prefs.enabled=true` (đang bật) — nên (c) ít khả năng.

**Verify off-car (device-agnostic, KHÔNG cần xe — theo rule emulator-first):** bơm noti giả format y hệt GMaps (title="300 m", text="đường X", subText ETA, category=navigation, groupKey nhóm) trên emulator → xem parser rút + hero cập nhật. Data thật đã lấy ở trên để dựng fixture.

## Ghi chú
- Cả 2 là **HAL/hành vi trên xe** — sửa off-car xong **PHẢI verify lại trên xe** (qua ADB wifi hoặc bản OTA kế).
- KHÔNG đụng code trong phiên này (owner dặn).
