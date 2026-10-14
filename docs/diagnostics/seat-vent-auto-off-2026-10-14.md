# Mát ghế do Kachi bật bị xe tắt sau ~10 phút, mát ghế do giọng BYD bật thì không — truy gốc off-car

> **Ngày**: 2026-10-14 (cập nhật cùng ngày: gỡ bản vá 3.1.5 §7, đào sâu native/HAL/firmware §8, phép đo trên xe §9) · **Loại**: chẩn đoán off-car, chỉ đọc · **Người làm**: agent RE cho owner dangkhoi
> **Xe**: Seal, `car.type` 138, `sys.car.protocol=CANFD` (firmware 2606, `fw-2606/meta/getprop.txt`)
> **Nguồn**: logcat chuyến 14/10 (`logcat-075959.txt`, `logcat-boot-dump.txt`) + nhật ký Kachi `usage-*.log` (224 tệp, 17/09 → 14/10) ·
> `libbydautoservice.so`/`libbydauto.so` 2606 · HAL `auto.default.so` + `vendor.img` + `mcu_18_3_5.xcd` (gói 2602) · jadx `framework.jar` 2606 (từng lớp) · `CustomServer.apk` + `AirConditioning.apk` 2606 · `SpeechHMI.apk` + `SystemUIWithLegacyRecents.apk` lấy từ ảnh 2602 (`fw-2602-diff/new_out`, bản 2606 chưa kéo về; bản 2602 là 1032, xe chạy 1033).
> Số dòng `file:line` của firmware là số dòng của bản jadx, ghi kèm để tra lại; không chép nguyên văn mã firmware.
> Mức bằng chứng theo `CLAUDE.md` §2: **[ĐO]** = đọc nguồn / log thật · **[SUY]** · **[ĐOÁN]** · **[CHƯA BIẾT]**.

## 0. Kết luận ngắn (viết lại 2026-10-14 tối, sau khi gỡ bản vá 3.1.5 và đào sâu §8)

1. **Gốc: [CHƯA BIẾT], và nằm dưới IVI.** Lệnh tắt mát ghế lúc 08:05:58 ngày 14/10 không đến từ bất kỳ tiến trình Java nào trên IVI [ĐO log, §2–§3].
   Phần native của IVI cũng đã đọc: `libbydautoservice.so` chỉ chuyển tiếp binder. HAL `auto.default.so` là bộ dịch tín hiệu theo bảng, không có hàm hẹn giờ hay tự tắt ghế [ĐO ký hiệu + chuỗi, §8.1–§8.2].
   Hai tầng còn lại không đọc được off-car: firmware MCU bị mã hoá [ĐO entropy], còn firmware ECU ghế/điều hoà không có trong gói OTA của IVI [ĐO, §8.3].
2. **Kachi và trợ lý giọng BYD ghi CÙNG MỘT thứ** [ĐO nguồn]: `setInt(1023, 0x43100010, 3)` (§1).
   Khả năng "HAL phân biệt theo uid bên gọi" nay **bị loại** [ĐO]: `libbydautoservice` không nhập `IPCThreadState`; `libbydauto` chỉ nhập `getCallingPid`, không có `getCallingUid` (§8.1).
3. **Loại được "N phút sau MỖI lần bật"** [ĐO log 14/10]. Lần bật bằng giọng lúc 08:08:18 (+12 phút sau lên nguồn) giữ mức 3 liên tục tới ít nhất 08:47:01, tức ≥ 38 phút. Riêng khoảng 08:08:18 → 08:38:13 không có lời ghi nào.
4. **Mẫu sạch duy nhất kiểu "xe tắt, không ai ghi"** có thể quy kết chắc là 14/10 (có logcat toàn hệ thống). Trong nhật ký Kachi còn ba lần tụt khác trong lúc chạy: +7,3′ (10/10), +3…6′ (11/10, mốc mờ), +10,0′ (13/10) sau lên nguồn (§8.5).
   Không quy kết được các lần này: nhật ký Kachi không thấy được lệnh của UI BYD. Vì thế **không** chứng minh được một bộ hẹn giờ cố định ~10 phút. Khoảng phân bố 3–10 phút hợp với một điều kiện hơn là một bộ đếm cố định [ĐOÁN].
5. **Đính chính bản trước** [ĐO]: hai mẫu "mát ghế xe tự nhớ vẫn tụt sau 12–13,6 phút, không cần Kachi" là SAI.
   - 20/09 10:44:09.896: chính Kachi ghi `setSeatVentilatingState(1, 1)`.
   - 23/09 08:45:13.628: chính Kachi ghi `setSeatHeatingState(1, 2)`. Sưởi và mát cùng ghế loại trừ nhau, nên mát tụt theo.
6. **Bản vá 3.1.5 (`SEAT-AUTO-OFF-REAPPLY`) đã GỠ** theo lệnh owner (§7). Không có sửa mã nào cho tới khi phép đo §9 chốt được cơ chế.
7. **Theo phiên bản (§10)**: lỗi bắt đầu cùng firmware **2606** (xe lên 07/10 16:52), không cùng một bản Kachi [SUY mạnh]. Trên 2602 có 0/16 lần tụt sớm, trên 2606 có 4/10. Cùng APK 3.0 cho cả lần giữ lẫn lần tụt, và mã ghi ghế không đổi hành vi từ 13/09 [ĐO]. Chưa chứng minh B: cần thí nghiệm A của §9.
8. **So 2602 ↔ 2606 (§11)**: IVI 2606 **không có** logic mới nào ghi mát/sưởi ghế [ĐO]. Hằng "nhớ theo tài khoản" (`BODYWORK_ACCOUNT_MEMORY_AWAKE_STRATEGY`, `…SEAT_VENTILATION_HEAT_MEMORY`) chỉ có trong bảng hằng SDK, không có trong framework, và không ai dùng. Dịch vụ ghế mới của DiCarServer không ghi lần nào cả chuyến [ĐO log].
   Thứ đổi thật dưới IVI là **firmware MCU** `2601300` → `2606100` [ĐO getprop] ⇒ ứng viên mạnh nhất [SUY]. Còn chờ: HAL 2606 (sha1 trên xe) và thí nghiệm F (đăng xuất tài khoản BYD).

## 1. Câu hỏi 1: `setSeatVentilatingState` và `0x43100010` ghi vào đâu, khác nhau gì

### 1.1 Hằng số [ĐO nguồn `BYDAutoFeatureIds` 2606]

| Hằng | Nhánh CANFD (xe owner) | Nghĩa |
|---|---|---|
| `SET_DRIVER_SEAT_VENTILATING_STATE_SET` | `1125122064` = **`0x43100010`** (`BYDAutoFeatureIds` jadx :8633; chỉ khác khi `!CANFD && TOYOTA`) | id **GHI** mát ghế lái |
| `SET_DRIVER_SEAT_VENTILATING_STATE` | `1335885832` = **`0x4FA00008`** (:8627–8632, nhánh `isCanFD`) | id **TRẠNG THÁI** (get + sự kiện) mát ghế lái |
| `isCanFD` | `sys.car.protocol` / `persist.sys.protocol.record` = `CANFD` (:5095) | xe owner = CANFD [ĐO `fw-2606/meta/getprop.txt`] |

⇒ `event_type =4fa00008` trong logcat là **sự kiện trạng thái** của chính tính năng mà id ghi `0x43100010` điều khiển. Đây không phải một tính năng khác [ĐO].

### 1.2 `setSeatVentilatingState` [ĐO nguồn `BYDAutoSettingDevice` 2606]

- `mDeviceType = 1023` (jadx :689). Đây đúng là `device_type: 1023` trong `postEvent`.
- `setSeatVentilatingState(seatID, state)` (:2095–2119):
  1. kiểm quyền `SETTING_SET_PERM`;
  2. log `"setSeatVentilatingState: The state of …"`;
  3. kiểm khoảng giá trị (ghế 1 nhận 1..6, ghế khác 1..4);
  4. ghế 1 → `SET_DRIVER_SEAT_VENTILATING_STATE_SET`;
  5. gọi `super.set(1023, id, state)`.
- `AbsBYDAutoDevice.set(int device, int event, int value)` (protected, :164–171) chuyển thẳng sang `mDeviceManager.setInt(device, event, value)` và **không log** gì.
- `AbsBYDAutoDevice.set(int[] ids, BYDAutoEventValue)` (public, :260–277) làm theo thứ tự: `checkDeviceFeatures` → `enforceCallingOrSelfPermission(getSetPermission())` → log **`"set featureID is <hex> intValue is <v>"`** → gọi lại **chính hàm `set(getDevicetype(), ids[0], intValue)` ở trên**.

⇒ Lệnh của Kachi không có dòng `AbsBYDAutoDevice: set featureID` vì method có tên đi vòng qua hàm public có log. **Không có lý do nào khác** [ĐO].
Hai đường kết thúc ở cùng một lời gọi `setInt(1023, 0x43100010, 3)`.

### 1.3 Ai trong firmware ghi mát ghế, và ghi bằng đường nào [ĐO nguồn]

| Ứng dụng | Đường ghi | Chỗ trong nguồn |
|---|---|---|
| **Kachi** (`seatc` / `seatc_r`, `SeatComfortApplier`) | `BYDAutoSettingDevice.setSeatVentilatingState(1, 3)` (method có tên, qua reflection) | `app/src/main/java/com/byd/clusternav/comfort/SeatComfortApplier.kt:96-119` (bulk) · `:56-75` (chạm) |
| **Trợ lý giọng** `com.byd.vrassistant.xf` (pid 1704) | `settingDevice.set(int[]{SET_DRIVER_SEAT_VENTILATING_STATE_SET}, ev)` (generic) | `SpeechHMI` `CarControlHelper.sendSeatVentilationOrHeatRequest` jadx :1762–1781 · `BusinessConstants` :48 |
| **App điều hoà OEM** `com.byd.airconditioning` | `mBYDAutoSettingDevice.setSeatVentilatingState(seatID, state)`: **cùng method với Kachi** | `AirSeatingVentilateAndHeatModel` jadx :353 (Runnable) · :383–386 |
| **CustomServer** (pid 1864) | `setSeatVentilatingState(1..4, 1)`: **cùng method với Kachi** | `AirConditioningListener.setSeatVentilateHeatClosed` jadx :483–501 |
| **Thanh AC của SystemUI** (pid 1515) | `MAIN_SEAT_VENTILATION_ID_SET = Setting.SET_DRIVER_SEAT_VENTILATING_STATE_SET` | `CustomConstant` jadx :15 |

⇒ Chính BYD cũng dùng **cả hai** hình dạng gọi (method có tên và `set(int[])` generic) cho cùng một id. Không hình dạng nào là "đường từ xa".

### 1.4 Có id "điều khiển từ xa / đặt lịch" cho ghế không [ĐO nguồn, trả lời câu (a) của owner]

- Quét `BYDAutoFeatureIds` tìm `REMOTE|APPOINT|RESERV|PRECOND|TIMING|BOOK` trên các hằng AC/ghế/sưởi/mát. Chỉ có:
  - `AC_HAS_AC_REMOTE_CTRL`, `AC_REMOTE_CTRL_TIME(_SET)` (`BYDAutoFeatureIds` :39, :82–83);
  - `CHARGING_CAR_APPOINTMENT_AC_*` (:793–795, điều hoà đặt lịch khi sạc).
- **Không có id ghế nào mang nghĩa từ xa.**
- `BYDAutoAcDevice` 2606 có `AC_REMOTE_CTRL_TIME_10/15/20/25/30 = 1..5` (jadx :73–77), `getAcRemoteCtrlTime()` / `setAcRemoteCtrlTime(1..5)` (:840–855). App điều hoà OEM có mục cài đặt cho nó (`AcSettingFragment` jadx :484–528).
  Đây là **thời lượng một phiên điều hoà từ xa** (mặc định 10 phút nếu giá trị 1) [ĐO hằng · ý nghĩa SUY theo tên].
  Phiên đó có tắt cả mát ghế hay không thì nằm dưới IVI [CHƯA BIẾT].
- Quét `CustomServer`, `AirConditioning`, `navigationbar` của `SystemUI` tìm hằng `600000/1200000/540000/580000/TimeUnit.MINUTES`. Chỉ có **`START_CLOSEVENTIHEAT_MSG_DELAY = 1200000`** (20 phút, §2), **không có 10 phút** [ĐO].

### 1.5 Phần còn mở ở 1.x

- Dưới `BYDAutoDeviceManager.setInt` là binder → `libbydautoservice.so` / HAL → MCU.
- Hai lời gọi ở tầng Java giống hệt nhau. Câu hỏi còn lại là HAL native có phân biệt theo **uid/gói** của bên gọi không (Kachi là app thường đi qua bypass, giọng nói là app hệ thống). **Đóng 14/10: không** [ĐO bảng nhập, §8.1].
- Nghi là không phân biệt [SUY]. Lý do: lệnh chạm tay của Kachi lúc 08:47:10–12 đổi được 1→2→3 và xe phát sự kiện ngay trong 25 ms (§3).
- Cách chốt (vẫn đáng làm như đối chứng): §9 thí nghiệm A. Nếu mát ghế bật bằng **chính UI BYD** trong 20 s đầu sau khi lên nguồn cũng tự tắt ⇒ không liên quan bên gọi.

## 2. Câu hỏi 2: `sendCloseVentiHeatMsg` là gì, cái gì kích hoạt tự tắt

[ĐO nguồn `CustomServer` `AirConditioningListener` 2606]

- Listener đăng ký trên id trạng thái của 4 ghế mát + 4 ghế sưởi + `836763656` (jadx :94, :249).
- `onSeatVentilatingStateChanged`, `onSeatHeatingStateChanged`, `onExhibitionModeChanged` đều gọi `sendCloseVentiHeatMsg()` (:195–207).
- `sendCloseVentiHeatMsg()` (:467–474) đọc `getExhibitionMode()`, xoá tin hẹn cũ, log `"sendCloseVentiHeatMsg..." + mode`. **Chỉ khi mode = 1 (trưng bày trong nhà) hoặc 2 (ngoài trời)** nó mới hẹn `msg 2` sau **1 200 000 ms = 20 phút** (`START_CLOSEVENTIHEAT_MSG_DELAY` :69–70).
- `msg 2` → `setSeatVentilateHeatClosed()` (:483–501): log `"setSeatVentilateHeatClosed..."` rồi tắt lần lượt 4 ghế sưởi, 4 ghế mát, mỗi lệnh cách nhau 1 s.

[ĐO log] Xe báo `getExhibitionMode mode is 3` ⇒ **không hẹn gì**. Hàm này chạy sau **mọi** sự kiện đổi trạng thái, kể cả sự kiện bật của giọng nói lúc 08:08:18 và ba lần chạm của Kachi lúc 08:47. Cả chuyến **không có** dòng `setSeatVentilateHeatClosed`.
⇒ Đây là chế độ trưng bày ở showroom, **không phải** thủ phạm. Thời gian 20 phút cũng không khớp.

Các điều kiện còn lại, đối chiếu với log:

| Ứng viên | Bằng chứng | Kết luận |
|---|---|---|
| Hẹn giờ trong một app IVI | Không có lời ghi `0x43100010` hay `setSeatVentilatingState` nào từ 07:59:58 tới 08:05:58 [ĐO grep logcat]. Các lời ghi lân cận chỉ là `43e0003a` / `43f0xxxx` của dẫn đường lên cụm (AmapService, Kachi), chạy theo nhịp đều suốt chuyến | Loại [ĐO] (giới hạn: một tiến trình gọi `BYDAutoDeviceManager.setInt` trực tiếp thì không để lại log — [SUY] khó xảy ra) |
| Tắt AC | `getAcStartState state is: 1` lúc 08:05:59.930; mức gió 7, 24 °C không đổi quanh mốc [ĐO] | Loại |
| Chế độ trưng bày | mode = 3 suốt chuyến [ĐO] | Loại |
| Đổi mức nguồn (ACC/ON/OK) | `onPowerLevelChanged: level = 2` lúc 07:56:02.375 (pid 2001, buffer system). `getPowerLevel = 2` lúc 07:59:42, 07:59:55, 07:59:59. Không có `onPowerLevelChanged` nào khác tới 08:49:50 [ĐO]. Hằng: 0=OFF 1=ACC **2=ON** 3=OK 4=FAKE_OK (`BYDAutoBodyworkDevice` jadx :232–237) | **Không có chuyển mức nào lúc 08:05:58** [ĐO]. Lúc 07:56:18 và 08:08:18 cùng mức 2 [ĐO 08:08 · 07:56:18 SUY: không có dòng đổi mức nào từ 07:56:02 tới 07:59:42] |
| Hẹn giờ 10 phút **dưới IVI** (phiên điều hoà từ xa, hoặc cửa sổ "mới lên nguồn") | Tắt lúc **+9:56 sau khi lên nguồn**, **+9:40 sau lệnh Kachi**. Lúc lên nguồn AC **đã chạy** với gió **mức 7** (07:56:02.598), 74 s sau tụt về 2 [ĐO usage log]. Gió tối đa ngay lúc vào xe giống một phiên làm mát trước [ĐOÁN] | **Ứng viên mạnh nhất** [SUY], một mẫu |
| Bộ lệnh lúc lên nguồn của Kachi (PM2.5 `setAutoCleanAirState(1)` 07:56:18.216 + lấy gió trong `0x1de0001b=1` 07:56:18.263, kẹp hai bên lệnh ghế) | Trùng thời điểm [ĐO]. Cơ chế làm lọc không khí kéo theo mát ghế: không thấy trong nguồn IVI [CHƯA BIẾT] | Chưa loại |

Gear và tốc độ: lúc 07:59:44 xe chạy 19 km/h, `CurGear:4` (D) [ĐO]. Lúc 08:08:18 xe chạy 35 km/h [ĐO]. **Lúc 07:56:18 [CHƯA BIẾT]**: logcat main chỉ bắt đầu lúc 07:59:58, và Kachi không ghi gear/tốc độ trong khoảng đó.

## 3. Đo trong log: dòng thời gian chuyến 14/10

| Giờ | Sự kiện | Nguồn |
|---|---|---|
| 07:56:02.375 | `onPowerLevelChanged level = 2` (lên nguồn; Kachi `screen_on`, "new trip") | boot-dump / usage |
| 07:56:02.652 | mát ghế lái = 1 (TẮT) | usage |
| 07:56:18.216 → .263 | Kachi: PM2.5 tự lọc = 1 → `setSeatVentilatingState(1,3)` rc=0 → lấy gió trong = 1 | usage |
| 07:57:16 → 08:04:09 | Kachi đọc mát ghế lái = 3 (đang BẬT) | usage |
| 07:59:24 → 07:59:30 | tiến trình Kachi dựng lại (pid 4966), `SeatComfort` ghi lại 3 lúc đang 3 (không đổi trạng thái) | logcat |
| **08:05:58.106** | xe phát `4fa00008 = 1` (TẮT); không có lời ghi IVI nào trước đó | logcat |
| 08:08:18.794 | giọng nói `set featureID is 43100010 intValue is 3` → 08:08:18.817 xe phát `= 3` | logcat |
| 08:38:13.629 | Kachi (tiến trình mới 22938) ghi lại 3 lúc đang 3 ⇒ xe **không** phát sự kiện (không có thay đổi) | logcat |
| 08:47:10–12 | người dùng chạm `seatc` trên Kachi: 1 → 2 → 3, xe phát sự kiện trong ≤ 25 ms mỗi lần (`before=2` ⇒ lúc đó vẫn đang bật) | logcat |
| 08:49:50 | logcat kết thúc (không quan sát được 10 phút sau lần chạm 08:47) | logcat |

Lịch sử 17/09 → 13/10 trong `usage-*.log` [ĐO số liệu · SUY diễn giải]:

- ~~Có nhiều lần mát ghế đã = 3 sẵn khi lên nguồn vẫn tụt sau 12–13,6 phút (20/09, 23/09)~~ — **ĐÍNH CHÍNH 14/10** [ĐO]: 20/09 là Kachi tự ghi 1 (10:44:09.896); 23/09 là Kachi ghi sưởi 2 (08:45:13.628), mát tụt theo luật loại trừ sưởi/mát. Bảng đầy đủ đã phân loại: §8.5.
- Nhật ký Kachi không phân biệt được người tắt bằng UI BYD với xe tự tắt [CHƯA BIẾT]. Mẫu quy kết chắc duy nhất là 14/10.

### 3.1 Trích logcat (27 dòng)

```
10-14 07:56:02.375  2001  2001 D AbsBYDAutoBodyworkListener: onPowerLevelChanged: level = 2
10-14 07:56:02.652 D/BYDAutoSettingDevice(24999): getSeatVentilatingState: The state of 1 seat ventilating is 1
10-14 07:56:02.671 D/BYDAutoSettingDevice(24999): getSeatVentilatingState: The state of 2 seat ventilating is 1
10-14 07:56:18.216 D/BYDAutoAcDevice(24999): setAutoCleanAirState state is: 1
10-14 07:56:18.232 D/BYDAutoSettingDevice(24999): setSeatVentilatingState: The state of 1 seat ventilating is 3
10-14 07:56:18.245 I/Pm25Filter(24999): setAutoCleanAirState(1) rc=0
10-14 07:56:18.255 I/SeatComfort(24999): ghi ghế: setSeatVentilatingState(seatId=1, state=3) [seatIndex=0 mode=COOL] rc=0
10-14 08:04:09.076 D/BYDAutoSettingDevice( 4966): getSeatVentilatingState: The state of 1 seat ventilating is 3
10-14 08:05:58.106  1515  1515 D BYDAutoSettingDevice: postEvent device_type: 1023, event_type =4fa00008, value = 1
10-14 08:05:58.106  1864  1864 D BYDAutoSettingDevice: postEvent device_type: 1023, event_type =4fa00008, value = 1
10-14 08:05:58.107  1515  1515 D Custom_RecyclerViewAdapter: updateSeatButtonState MAIN_SEAT_VENTILATION_ID value: 1
10-14 08:05:58.111  1864  1864 I AbsBYDAutoSettingListener: onSeatVentilatingStateChanged seatID:1 state:1 thread main
10-14 08:05:58.113  1864  1864 D BYDAutoSettingDevice: getExhibitionMode mode is 3
10-14 08:05:58.113  1864  1864 I Custom.AirConditioningListener: sendCloseVentiHeatMsg...3
10-14 08:08:18.793  1704  2031 D CarControlHelper: handleSeatVentilation: getCurValue , seatValue = 1, index = 0
10-14 08:08:18.793  1704  2031 I CarControlHelper: shouldSetSeatVentilation : shouldSet = true
10-14 08:08:18.793  1704  2031 D CarControlHelper: sendSeatVentilationRequest() call with : list = [AcTaskEntity{targetValue=3, curValue=1, flag=0}]
10-14 08:08:18.794  1704  2031 D AbsBYDAutoDevice: set featureID is 43100010 intValue is 3
10-14 08:08:18.795  1704  2031 D CarControlHelper: sendSeatVentilationOrHeatRequest : result:0
10-14 08:08:18.817  1515  1515 D BYDAutoSettingDevice: postEvent device_type: 1023, event_type =4fa00008, value = 3
10-14 08:38:13.629 22938 23111 D BYDAutoSettingDevice: setSeatVentilatingState: The state of 1 seat ventilating is 3
10-14 08:38:13.634 22938 23111 I SeatComfort: ghi ghế: setSeatVentilatingState(seatId=1, state=3) [seatIndex=0 mode=COOL] rc=0
10-14 08:38:13.635 22938 23111 I SeatComfort: áp xong: 1 ghế, mode=COOL, seats=[0, 1]
10-14 08:47:10.407 22938 27331 D BYDAutoSettingDevice: setSeatVentilatingState: The state of 1 seat ventilating is 1
10-14 08:47:10.409 22938 27331 I KachiCtl: … ctl #12 id=seatc m=named:setSeatVentilatingState args=[1,1] rc=0 before=2 why=cmd …
10-14 08:47:10.435  1515  1515 D BYDAutoSettingDevice: postEvent device_type: 1023, event_type =4fa00008, value = 1
10-14 08:47:10.435  1864  1864 D BYDAutoSettingDevice: postEvent device_type: 1023, event_type =4fa00008, value = 1
```

## 4. Câu hỏi 3 + 4: phía Kachi, và cách sửa

### 4.1 Kachi dùng API nào [ĐO mã repo]

- Nút `seatc`, `seatc_r`: `bindingKey = "BYDAutoSettingDevice.setSeatVentilatingState"`, `readKey = seat_vent_state(_r)` (`core/src/main/kotlin/com/byd/clusternav/launcher/ControlRegistry.kt:148-152`, `:417-420`).
- Nút `seath`, `seath_r`: `BYDAutoSettingDevice.setSeatHeatingState` (`:230-233`, `:422-425`).
- Tự áp lúc lên nguồn: `BootSetupService.kt:91` → `SeatComfortApplier.applyOnStart` (ngủ `START_DELAY_MS = 5 000`) → `apply()` gọi `BydHal.callNamedInt(dev, "setSeatVentilatingState", seatId, state)` (`SeatComfortApplier.kt:96-119`). Ngay sau đó là `Pm25FilterApplier.applyOnStart` và `RecircApplier.applyOnStart` (`BootSetupService.kt:94`, `:100`).
- Đường chạm cũng dùng method đó (`SeatComfortApplier.kt:56-75`). Probe có sẵn: `HalProbePresets.kt:39-40` (`seat-cool-driver`).

### 4.2 Các hướng sửa và mức chứng minh

| # | Hướng | Chứng minh được off-car? | Đánh giá |
|---|---|---|---|
| F0 | Đổi `seatc` sang `set(int[]{0x43100010}, ev)` "giống giọng nói" (đổi bindingKey/ControlRegistry) | **Đã chứng minh là VÔ TÁC DỤNG** [ĐO nguồn §1.2]: cùng `setInt(1023, 0x43100010, v)`, chỉ thêm một dòng log | **Không làm.** Vá sai tầng (G0), chỉ "trông giống" BYD. Phần mở duy nhất (§1.5 uid ở HAL) không thay đổi khi đổi hình dạng gọi, vì bên gọi vẫn là Kachi |
| F1 | **Dời lệnh ghế lúc lên nguồn** tới khi xe "vào chuyến thật". Không mở đường ghi mới: cùng `applyOnStart`, chỉ đổi cổng thời gian, ví dụ đợi tốc độ > 0 hoặc gear D lần đầu, có trần chờ. Đọc tốc độ/gear: Kachi đã đọc sẵn (`BYDAutoSpeedDevice.getCurrentSpeed`, ClusterSpeedBadge) | Chỉ chứng minh được **cơ chế đọc** [ĐO: getter đang chạy trong log]. **Không** chứng minh được "đợi tới D thì xe không tự tắt": gốc ở dưới IVI | Ứng viên chính nếu §9 thí nghiệm A/C xác nhận cửa sổ "mới lên nguồn" hoặc "phiên từ xa". Chi phí perf: một lần đọc, không thêm nhịp hay listener nếu móc vào nhịp đọc tốc độ có sẵn |
| F2 | **Kiểm lại một lần** sau khi áp. Ví dụ ở mốc lên nguồn + 11 phút (ngay sau cửa sổ 10 phút): nếu mức đọc được thấp hơn mức Kachi đã áp **và** Kachi không thấy người dùng chạm trên Kachi ⇒ áp lại **một lần duy nhất** | Cơ chế đọc lại [ĐO: `getSeatVentilatingState` chạy được]. Nhưng **không phân biệt được** "xe tự tắt" với "người dùng tắt bằng thanh AC hay giọng BYD": cả hai chỉ hiện ra là sự kiện `4fa00008 = 1` [SUY từ §1.3: mọi app BYD cùng một id] | Rủi ro làm trái ý người dùng ⇒ **cần owner chốt** (Open Question). Nếu làm: chỉ một lần mỗi chuyến, chỉ trong cửa sổ đã đo, có log `SeatComfort` |
| F3 | F1 + F2 kết hợp | như trên | Chỉ khi F1 một mình không đủ |

> **14/10**: F2 đã làm ở 3.1.5 rồi **gỡ** theo owner (§7). F0 vô tác dụng (§1.2, §8.1). F1 chỉ được cân nhắc sau khi §9 chốt cơ chế.

Theo `CLAUDE.md` §14 thì không hướng nào được ship trước khi §9 xanh. F1/F2 không mở đường ghi mới, nhưng giả định "xe có hẹn giờ 10 phút" là một **hành vi mới chưa đo**.

## 5. Phép đo shell thô (bản đầu — đã gộp và thay bằng §9)

Theo dõi chung (chạy suốt mỗi thí nghiệm, lưu vào `docs/diagnostics/`):

```
adb logcat -v time | grep -E "4fa00008|43100010|setSeatVentilat|onPowerLevelChanged|getPowerLevel|sendCloseVentiHeat|setSeatVentilateHeatClosed|RemoteCtrl|CurGear"
```

Đọc thời lượng phiên điều hoà từ xa (chỉ đọc, không cần confirm):

```
adb shell am broadcast -n com.byd.launcher/com.byd.clusternav.launcher.testbridge.KachiTestBridge -a com.byd.launcher.TEST \
  --es cmd hal --es op get --es dev BYDAutoAcDevice --es m getAcRemoteCtrlTime
```

Ý nghĩa giá trị: 1=10' · 2=15' · 3=20' · 4=25' · 5=30' (`BYDAutoAcDevice` :73–77).

Ghi mát ghế lái bằng đúng đường của Kachi:

```
adb shell am broadcast -n com.byd.launcher/com.byd.clusternav.launcher.testbridge.KachiTestBridge -a com.byd.launcher.TEST \
  --es cmd hal --es op set --es dev BYDAutoSettingDevice --es m setSeatVentilatingState --es args 1,3 --ez auto_confirm true
```

Ghi mát ghế lái bằng đường generic của giọng nói (đối chứng F0, **cùng id**):

```
adb shell am broadcast -n com.byd.launcher/com.byd.clusternav.launcher.testbridge.KachiTestBridge -a com.byd.launcher.TEST \
  --es cmd hal --es op setev --es dev BYDAutoSettingDevice --es m 1125122064 --es args 3 --ez auto_confirm true
```

Các thí nghiệm. Mỗi thí nghiệm một chuyến; tắt `SeatComfort` tự áp trong Kachi trừ khi ghi khác:

| # | Làm | Nếu tắt sau ~10' | Nếu giữ |
|---|---|---|---|
| A | ≤ 20 s sau khi lên nguồn, bật mát ghế bằng **thanh AC của BYD** (không dùng Kachi) | Không phải lỗi Kachi: luật xe cho lệnh bật sớm hoặc trong phiên từ xa ⇒ F1 | Có gì đó riêng của Kachi (§1.5 uid hoặc bộ lệnh PM2.5/lấy gió trong) ⇒ làm D |
| B | ≥ 12 phút sau khi lên nguồn, xe đang chạy: lệnh `op set … 1,3` | Giả thuyết "cửa sổ sau lên nguồn" sai | Thời điểm là yếu tố quyết định ⇒ F1 |
| C | Trong app điều hoà BYD, đổi thời lượng điều khiển từ xa sang 30', lặp lại A hoặc chuyến Kachi tự áp | Mốc tắt **chuyển sang ~30'** ⇒ **chứng minh** phiên điều hoà từ xa là gốc (giả thuyết của owner) | Bộ hẹn không phải phiên từ xa |
| D | Bật `SeatComfort`, **tắt** PM2.5 tự lọc + lấy gió trong lúc lên nguồn | Không phải do bộ lệnh kèm | Bộ lệnh kèm là gốc ⇒ tách lệnh ghế ra khỏi chúng (đổi thứ tự hoặc giãn thời gian) |
| E | Đối chứng F0: chuyến dùng `op setev 1125122064` thay cho `op set` | Xác nhận F0 vô tác dụng | (trái với §1.2 ⇒ HAL phân biệt hình dạng gọi, phải đọc `libbydautoservice`) |

Cũng cần ghi lại: trước mỗi chuyến xe có được bật điều hoà từ xa bằng app điện thoại không (owner trả lời). Đó là điều kiện tiên quyết của giả thuyết C.

## 6. Việc còn mở

- `docs/README.md` (INDEX) đã có dòng cho tệp này. Backlog: `SEAT-AUTO-OFF-REAPPLY` = Withdrawn; việc truy gốc chuyển sang `SEAT-AUTO-OFF-ROOTCAUSE` (🚗).
- `SpeechHMI` / `SystemUI` đọc từ ảnh 2602 (giọng nói 1032, xe chạy 1033). Bảng hằng `0x43100010` trong log 2606 khớp, nên sai khác (nếu có) không ảnh hưởng §1.
- `libbydautoservice.so`: đã đọc bảng nhập/ký hiệu (§8.1). Bảng ánh xạ feature → tín hiệu của HAL `auto.default.so` dựng trong mã (`FeatureMapper`, hằng tính bằng `mov/movk/add`) và **chưa** disasm hết (§8.2).

## 7. GỠ 14/10 — owner: root cause trước, không vá

> **GỠ 2026-10-14.** Owner: *"cái mát ghế sưởi ghế 10p tắt đó cần đi rootcause, không vá trên vá nhé"*.
> Bản vá 3.1.5 "áp lại MỘT lần sau khi xe tự tắt trong cửa sổ +8…+15 phút" (hướng F2 của §4.2) đã gỡ sạch:
> - `SeatAutoOffGuard` (`:core`) và test của nó;
> - `SeatAutoOffWatch` (`:app`) và `SeatAutoOffWiringContractTest`;
> - nối dây trong `SeatComfortApplier` và `WakeOnWriteControl`. Hai tệp này trả về đúng bản HEAD (trước bản vá).
>
> Bản vá chưa từng bump hay OTA. Backlog `SEAT-AUTO-OFF-REAPPLY` = **Withdrawn**, kéo theo `SEAT-AUTO-OFF-POWERLEVEL-READ`.

Vì sao đúng là phải gỡ, không chỉ vì owner nói:

- Bản vá đặt cược vào một **cửa sổ thời gian** suy ra từ một mẫu đo (14/10) và hai mẫu cũ. §0 mục 5 cho thấy hai mẫu cũ đó là do chính Kachi ghi.
- Nó cũng không phân biệt được "xe tắt" với "người dùng tắt bằng UI BYD" (§4.2 F2), nên sẽ có lúc bật lại trái ý người dùng.
- Đó là vá triệu chứng ở tầng ứng dụng cho một luật nằm dưới IVI mà ta chưa biết là luật gì (G0: sai tầng).

Sự thật giữ lại từ phiên làm bản vá:

- **[ĐO owner 14/10]** sưởi và mát **cùng một ghế** loại trừ nhau: bật cái này thì xe tắt cái kia. Khớp KDoc `SeatComfort` (RE app điều hoà OEM).
- **[ĐO mã]** `SeatComfortApplier` không bao giờ ghi cả sưởi lẫn mát cho cùng một ghế trong một lượt (`SeatComfort.methodFor(mode)` chỉ trả một method).

## 8. Đào sâu off-car 14/10 (sau khi gỡ)

### 8.1 Tầng binder native: `libbydautoservice.so` + `libbydauto.so` [ĐO, `fw-2606/_libs`, lib64]

- `libbydautoservice.so` (dịch vụ `autoservice` = `android.gui.BYDAutoServer`, `fw-2606/meta/services.txt:206`) làm ba việc: nạp module HAL bằng `hw_get_module("auto")`, giữ danh sách callback, chuyển `setInt/getInt/...` xuống `mAutoDevice->ops`. Chuỗi log toàn là `BYDAutoService::setInt: device_type is (%d)` / `event_type`, `handleChangedEvent`, `findValueCommand`…
- `checkSetPermission(int device, int event)` chỉ nhận device type và event. Bảng nhập (`nm -D -u`) **không có** `IPCThreadState` ⇒ thư viện này không đọc được uid hay pid bên gọi.
- `libbydauto.so` (phía client) nhập `IPCThreadState::self` + `getCallingPid`, **không** có `getCallingUid`. Chuỗi `BYDAutoManager(%d)` cho thấy pid chỉ dùng để in log [SUY].
- ⇒ Không có đường nào để tầng native đối xử khác với Kachi và với giọng nói BYD. §1.5 đóng.

### 8.2 HAL `auto.default.so` [ĐO, `system.img` 2602 `/system/lib64/hw/auto.default.so`, 1,1 MB]

Đây là module mà `hw_get_module("auto")` nạp. Không có HAL ô tô nào khác trong `/system/lib64/hw` hay `vendor.img`, chỉ có `auto_emulator.default.so` [ĐO `debugfs ls`].

- Nói chuyện với MCU qua `/dev/spidev_ivi` (`[SPI Callback] id:0x%08x`) và đánh thức MCU qua `/sys/qc_mcu/qc_wakeup_mcu`.
- Ghi log ra tệp `/data/logs/autohallogs`. BYD tự nén tệp này vào `/storage/emulated/0/logs/All*.zip`: thấy `ZipHelper` làm việc này lúc 08:05:11–08:06:39 ngày 14/10, đúng quanh mốc tắt [ĐO logcat].
- 204 hàm `FunctionTable::*` đều là bộ đổi giá trị cho từng tín hiệu: `windlevel`, `actempin`, `powerlevel`, `passengerseatvencanfd`, `seatmessageconver`, `c_timer1time`…
  - Hàm duy nhất có chữ "timer" là `c_timer1/2*`, `chargingtimer`, `s_chargingtimer`, đều là **hẹn giờ sạc**.
  - Không có tên hay chuỗi nào kiểu auto-off / timeout / remote / close cho ghế.
  - `seatmessageconver` chép tín hiệu ghế giữa các khung (chuỗi `4FA_47_in … value402_56`, `42F_56_in … value4FA_57`, `value3CA_71`). Nó dịch giá trị, không tự sinh lệnh [SUY từ chữ ký `(id, int in, void* out)` + đoạn đầu disasm `0x33248`].
- Hằng `0x4FA00008` có trong `.rodata` (`0x1eb6c`, `0x1edb8`). Bảng `FeatureMapper` dựng bằng mã (`mov #0x4fa00000` + `add`), nên **chưa** dò hết được ánh xạ id → khung [CHƯA BIẾT phần còn lại].
- Theo khuôn id `0xMMM0SSSS`, `0x4FA00008` khớp khung CAN `0x4FA` và `0x43100010` khớp khung `0x431`. Cách hiểu này hợp với tên tín hiệu `4FA_xx` trong HAL [SUY].
  ⇒ Trạng thái mát ghế đi **lên** từ một ECU trong khung `0x4FA`. Lệnh bật/tắt của IVI đi **xuống** trong khung `0x431`.

### 8.3 Dưới HAL: MCU và ECU [ĐO]

- Gói OTA `2602.zip` (`config` = `qc+mcu+dsp+anc`) gồm: ảnh SoC, `mcu_18_3_5.xcd` (7,15 MB), DSP, ANC. **Không có** firmware ECU ghế, điều hoà hay thân xe.
- `mcu_18_3_5.xcd` có entropy 7,76–8,00 bit/byte ở cả đầu, giữa và cuối. Không có chuỗi đọc được và không có bảng vector ARM ⇒ bị mã hoá hoặc nén. Off-car không đọc được.
- Log MCU có mặt trên logcat (`mcu_log_collection`, pid 151) nhưng chỉ có radar, AVAS, DSP, đèn xi-nhan. Trong ±10 s quanh 08:05:58 không có dòng nào về ghế hay điều hoà.

### 8.4 Ứng dụng BYD: có "ghế thông minh" nào không [ĐO nguồn 2606]

- `BYDAutoFeatureIds` không có id ghế nào mang nghĩa tự động, thông minh hay liên kết. Những cái gần nhất:
  - `SET_AUTO_STEER_WHEEL_HEATING_*`: sưởi **vô-lăng** tự động;
  - `AC_INTELLIGENT_CONTROL_MODE_KEY_SIGNAL`: app điều hoà dùng cho chế độ hướng gió "thổi người" (`AirConditioningCocosPresenter.updateIntelligentModeUi`), không dính ghế;
  - `SET_COMFORT_MODE_CONTROL_SWITCH_SIGNAL_SET`;
  - `YUN_SEAT_VENTILATING_HEATING(_SET)` (`0xAA000144` trong `com.byd.feature.yun.Yun`): **ghế qua đám mây**, tức app điện thoại. Không app IVI nào gọi nó [ĐO grep].
- CustomServer `study/BydCanfdStudyAutonomouslyPolicy` (khớp `persist.sys.rebootreason=StudyAutonomouslyPolicy`) **không** phải học thói quen. Nó học **cấu hình**: so giá trị `…ByMCU` với `…ByDB` rồi ghi `driver_seat_ventilating_online` vào `CarSettings` (jadx :961–964). Không bật/tắt gì.
- App điều hoà (`AirSeatingVentilateAndHeatModel`) chỉ ghi khi người dùng chạm (`SendMcuStateRunnable` :330–372). Giá trị 1 = TẮT, 2 = THẤP, 3 = CAO (:398–405).
- Mục cài đặt điều hoà có các "dò PM2.5" (`powerOnDetect`, `thirtyMinDetect`, `doorOpenCloseDetect`), tự lọc và thời lượng điều khiển từ xa (`AcSettingViewModel`). **Không** có mục nào cho ghế.

### 8.5 Bảng mọi lần tụt mát/sưởi 17/09 → 14/10 [ĐO số liệu từ 224 tệp `usage-*.log` · SUY phân loại]

Cách phân loại:

- **TẮT MÁY**: mức tụt ≤ 90 s trước `A11yJournal note=tat-may` hoặc `getPowerLevel = 0`.
- **KACHI**: có lời ghi của Kachi (`setSeat…State` hoặc `KachiCtl seat*`) trong 60 s trước đó.
- **ĐỔI SƯỞI/MÁT**: Kachi ghi chức năng kia của cùng ghế.
- **?**: còn lại, tức người dùng qua UI BYD **hoặc** xe; nhật ký Kachi không phân biệt được.

Mốc "lên nguồn" lấy theo lúc nhật ký Kachi chạy lại sau một quãng im ≥ 4 phút.

| Ngày | Lúc mát lên 3 | Tụt về 1 (khoảng giữa hai lượt đọc) | Sau lên nguồn | Phân loại | AC lúc tụt |
|---|---|---|---|---|---|
| 20/09 | xe nhớ, Kachi ghi 3 lúc 10:30:38 | 10:44:00–10:44:10 | +13,6′ | **KACHI** (ghi 1 lúc 10:44:09.896) | — |
| 23/09 | Kachi 08:33:16 | 08:45:12–13 | +12′ | **ĐỔI SƯỞI/MÁT** (Kachi ghi sưởi 2 lúc 08:45:13.628) | gió 1 |
| 28/09 | xe nhớ (đọc 3 lúc 09:52:33), Kachi không ghi | 10:05:55–10:06:04 | +13,5′ | **TẮT/MỞ MÁY** — lượt đọc 10:06:04 từ tiến trình Kachi mới, gió 0, nhiệt ngoài 195 (giá trị rác lúc chưa có nguồn) | — |
| 29/09 | Kachi 11:22:15 | 11:33:18–24 | +12,5′ | **TẮT/MỞ MÁY** — 11:33:26 tiến trình mới, gió 0, nhiệt ngoài 195; `SeatComfort` áp lại lúc 11:34:28 | — |
| 10/10 | Kachi 13:54:55 | 14:01:52–14:02:03 | +7,3′ | ? (đang chạy: 2 → 57 km/h) | gió 2, ngoài 32 °C |
| 11/10 | Kachi 16:44:18 | 16:47:08–16:50:11 | +3…+6′ (mốc mờ) | ? | gió 2 |
| 13/10 | Kachi 08:27:18 | 08:36:56–08:37:06 | +10,0′ | ? | AUTO, gió 2, 47 km/h, ngoài 31 °C |
| **14/10** | Kachi 07:56:18 (ghi lại 07:59:30, không đổi) | **08:05:58.106** | **+9,9′** | **xe** — có logcat toàn hệ thống, không ai ghi [ĐO] | AUTO, gió 2, 30 km/h, ngoài 30 °C |
| 14/10 | **giọng** 08:08:18 (+12,3′) | không tụt tới 08:47:01 (người dùng chạm) | — | đối chứng: **giữ ≥ 38′** [ĐO] | AUTO |

Các lần khác cũng đã kiểm: 17/09–27/09, 05/10–08/10. Đó là các lần tắt máy, các lần chạm Kachi, hoặc lần đọc cuối sát lúc gió về 0 / nhiệt ngoài 195, tức sắp tắt máy. Không lần nào thêm vào được bảng "xe tự tắt".

Đọc bảng:

- **Không phải hẹn giờ theo từng lần bật** [ĐO: dòng giọng nói 14/10].
- **Chưa chứng minh được** "luôn ~10 phút sau lên nguồn". Hai mẫu 13/10, 14/10 cùng ở +10,0′, nhưng 10/10 ở +7,3′ và 11/10 sớm hơn. Các lần "?" có thể có lần là người dùng tắt bằng UI BYD [CHƯA BIẾT quy kết].
- Điểm chung của các mẫu: bật trong vài chục giây đầu sau lên nguồn, AC đang chạy (AUTO khi có ghi chế độ), trời ngoài 30–34 °C, xe đang chạy. Lần giữ được thì bật **muộn** (+12 phút).
  Nghi là luật ở ECU/MCU gắn với **giai đoạn đầu sau lên nguồn**, kết thúc theo một điều kiện, ví dụ cabin đạt nhiệt độ đặt ở chế độ AUTO, hoặc hết một phiên kiểu "điều hoà từ xa" (`AC_REMOTE_CTRL_TIME` mặc định 10′, §1.4) [ĐOÁN, chưa có dữ liệu trực tiếp].
- PM2.5 trong xe nhảy vọt (lên 145–267) trong ±30 s quanh các lần tắt 10/10, 13/10, 14/10. Nhưng các cú nhảy này cũng lặp lại vài phút một lần trong cả chuyến (chu kỳ dò của cảm biến) ⇒ coi là trùng hợp [ĐOÁN, không dùng làm căn cứ].

### 8.6 ±30 s quanh 08:05:58 ngày 14/10 [ĐO `logcat-075959.txt`]

- **Không** có `setSeatVentilatingState`, `set featureID … 43100010` hay `setSeatVentilateHeatClosed`. Không có `onPowerLevelChanged`.
- AC giữ nguyên: `getAcStartState = 1`, `getAcControlMode = 0` (AUTO), gió 2, cycle 1, đặt 24 °C. Chế độ chỉ thoáng sang tay (1) lúc 08:02:38 và 08:25:53.
- Sự kiện xe trong cửa sổ:
  - đạp phanh: `BODYWORK_BRAKE_PEDAL_STATE 0x29400022` = 2/1/0 lúc 08:05:55–56 và 08:05:57.3, `SET_VCU_BREAK_PEDAL_STATS_S` = 1 lúc 08:05:57.355 (ở 30 km/h);
  - PM2.5 trong xe tụt 70 → 5 (08:05:28 → 08:05:57), `PM2P5_LEVEL_IN` = 1 lúc 08:05:37;
  - bộ đếm năng lượng chuyến `BODYWORK_BATTERY_POWER 0x44500008` tăng 0,1 mỗi 30 s (= 2,0 lúc 08:05:52).
  - Sau mốc tắt 3 s: `SETTING_PAD_EXIT_PIN_CODE_PROCESS` / `SETTING_UNLOCK_DRIVING_PASSWORD_POPUP_TYPE` = 0 (08:06:01).
  - Không cái nào có cơ chế đã biết nối với ghế [SUY: trùng thời điểm, không phải bằng chứng].

## 9. Phép đo trên xe để chốt gốc (CLAUDE.md §14 bước 1 — shell thô, chưa code)

Trước chuyến (xe đã lên nguồn, chỉ đọc):

```
# 0a. Đọc được log HAL/MCU không? (chưa biết quyền của uid shell)
adb shell ls -l /data/logs/autohallogs/ /data/logs/mcu_log /data/logs/host/spilogs /storage/emulated/0/logs/
# 0b. Thời lượng phiên điều hoà từ xa: 1=10' 2=15' 3=20' 4=25' 5=30'
adb shell am broadcast -n com.byd.launcher/com.byd.clusternav.launcher.testbridge.KachiTestBridge -a com.byd.launcher.TEST \
  --es cmd hal --es op get --es dev BYDAutoAcDevice --es m getAcRemoteCtrlTime
```

Hỏi owner một câu: trước chuyến có bật điều hoà hay ghế bằng **app điện thoại** BYD không, và bật lúc mấy giờ.

Suốt chuyến (bắt đầu ngay khi lên nguồn, lưu vào `docs/diagnostics/`):

```
adb logcat -v threadtime -b main -b system | grep -E \
 "4fa00008|43100010|SeatVentilat|SeatHeating|onPowerLevelChanged|getAcControlMode|getAcWindLevel|getAcStartState|getOutCarTemperature|PM2p5Level|RemoteCtrl|sendCloseVentiHeat|setSeatVentilateHeatClosed|ZipHelper: retZipFileName"
```

Ngay sau khi thấy `postEvent … 4fa00008, value = 1` mà trước đó không có lời ghi nào:

```
adb shell "tail -n 3000 /data/logs/autohallogs/autohallog" > autohal-off.txt      # nếu 0a cho đọc
adb shell "tail -n 3000 /data/logs/mcu_log" > mcu-off.txt
# nếu 0a bị chặn: kéo tệp nén BYD vừa tạo (tên in ở dòng ZipHelper: retZipFileName), thử unzip -l (có thể bị mã hoá)
```

Trong `autohal-off.txt`, tìm `SPI Callback id:0x4fa00008` và các khung ngay trước nó. Mục tiêu là biết MCU báo gì cùng lúc: khung `0x4FA` đi kèm thay đổi nào khác.

Thí nghiệm, mỗi chuyến một cái. `SeatComfort`, PM2.5 tự lọc và lấy gió trong của Kachi tắt hết, trừ khi ghi khác:

| # | Làm | Nếu tắt ở ~7–14′ | Nếu giữ ≥ 20′ |
|---|---|---|---|
| A | ≤ 20 s sau lên nguồn, bật mát ghế bằng **thanh AC BYD** | Luật của xe cho lệnh bật sớm, không liên quan Kachi (đường ghi đã chứng minh giống hệt, §8.1) | Có gì đó riêng của Kachi lúc lên nguồn ⇒ làm D |
| B | Bật bằng thanh AC BYD ở **≥ 12′** sau lên nguồn (lặp lại đối chứng giọng 14/10) | Mẫu giọng 14/10 là ngoại lệ ⇒ xem lại §8.5 | Thời điểm bật là yếu tố quyết định (giai đoạn đầu) |
| C | Đổi thời lượng điều khiển từ xa sang 30′ (app điều hoà BYD › cài đặt), lặp A | Mốc tắt **dời sang ~30′** ⇒ **chứng minh** là phiên điều hoà từ xa | Không phải phiên từ xa |
| D | Lặp A với AC chỉnh **tay** (không AUTO) hoặc AC tắt lúc lên nguồn | Không phụ thuộc chế độ AUTO | Luật gắn với AUTO / hạ nhiệt cabin |
| E | Bật `SeatComfort` của Kachi, **tắt** PM2.5 tự lọc + lấy gió trong | (cùng hiện tượng) | Bộ lệnh kèm lúc lên nguồn là gốc |

### 9.1 Khi đã chốt, Kachi nên làm gì (chỉ sau khi owner duyệt)

- **Nếu A tắt** (xe tự tắt kể cả khi bật bằng UI BYD): đây là luật của xe. Kachi **không** chống lại nó bằng vòng áp lại. Hướng đúng là ghi đúng lúc xe cho giữ, tức F1 của §4.2: dời lệnh ghế ra sau giai đoạn đầu. Mốc dời phải lấy từ kết quả của C hoặc D, không đoán.
  - Nếu C chứng minh là phiên điều hoà từ xa: mốc = `getAcRemoteCtrlTime()` đọc lúc chạy, không hằng số trong mã.
  - Nếu D chứng minh là AUTO hạ nhiệt: mốc = lúc tín hiệu AC tương ứng đổi trạng thái (cần tìm tín hiệu ở bước đo).
- **Nếu A giữ**: có gì đó riêng ở bộ lệnh lúc lên nguồn của Kachi ⇒ làm E rồi tách lệnh ghế khỏi lệnh kèm.
- Nếu đó là một cài đặt BYD người dùng chỉnh được (ví dụ thời lượng điều hoà từ xa), Kachi chỉ cần hiển thị đúng cài đặt đó và trỏ người dùng tới nó. Không ghi đè.

## 10. Lịch sử theo phiên bản / firmware (14/10 tối — trả lời giả thuyết của owner)

> Owner 14/10 tối: *"có vẻ các bản trước đây không có bị, có khi bản nào đó làm sai, hoặc lên 2606 nó làm tắt, 1 trong 2 trường hợp"*.
> (A) một bản Kachi làm hỏng · (B) firmware 2606 gây ra. Chỉ nghiên cứu, không sửa mã.

### 10.1 Nguồn và cách dựng

- **Nhật ký Kachi** kéo từ xe sáng 14/10: 224 `usage-*.log` + 23 `usage-wake-*.log`, 17/09 → 14/10 08:49 [ĐO]. Chuyến tối 14/10 **chưa kéo** (lần sau kéo lại `/sdcard/Android/data/com.byd.launcher/files/kachi-logs/`).
- **Nhật ký không in versionName hay build firmware** [ĐO grep]. Phiên bản Kachi suy từ **mã băm thư mục cài** `/data/app/com.byd.launcher-<băm>/` (đổi mỗi lần cài) đối chiếu mốc commit `versionName` trong `git log app/build.gradle.kts` ⇒ cột Kachi là **"≈"** [SUY, có thể lệch một bản thử cài tay].
- **Mốc firmware**: `fw-2606/meta/getprop.txt` (kéo từ xe 07/10 20:34) có `persist.sys.cloud_reboot_time = 1791366765` = **07/10 16:52:45**, `ro.build.version.incremental = eng.build.20260610.082733` [ĐO]. Bộ đếm khởi động trong nhật ký Kachi nhảy `n177 → n178` lúc 07/10 16:53:55 [ĐO]. Ngày 06/10 xe còn chạy `fw 2602030` (`oncar-2026-10-06-cluster-rect.md`) [ĐO tài liệu]. ⇒ **xe lên 2606 lúc 07/10 ~16:52** [SUY mạnh: lần khởi động lại do OTA đám mây khớp phút với bộ đếm].
- Mỗi lần lên nguồn: mốc = dòng `KachiReady screen_on` (từ 05/10) hoặc lúc nhật ký chạy lại sau ≥ 4′ im (trước 05/10). Trạng thái mát ghế lái = dòng `getSeatVentilatingState: … 1 seat ventilating is N` do Kachi poll. Lời ghi của Kachi = dòng `setSeat…State` trong tiến trình Kachi + `SeatComfort ghi ghế`.
- Giới hạn chung: nhật ký Kachi **không thấy** lệnh của UI BYD ⇒ một lần tụt "?" có thể là người dùng tắt [CHƯA BIẾT quy kết], trừ 14/10 có logcat toàn hệ thống (§3).

### 10.2 Bảng: mỗi lần lên nguồn có `SeatComfort` ghi mát ghế lái = 3

"Giữ ≥ Z′" = mọi lượt đọc từ lúc ghi tới Z phút đều = 3. "Tụt +Y′" = lượt đọc đầu tiên ≠ 3, tính từ lúc lên nguồn. Chỉ ghi các lần có đủ lượt đọc để kết luận.

| Ngày lên nguồn | Kachi (≈) | fw | Ghi ở | Mát trước ghi | Kết quả | Ghi chú |
|---|---|---|---|---|---|---|
| 17/09 16:14 | 1.71 | 2602 | +3,5′ | 3 | giữ 61′ | |
| 20/09 10:08 | 1.83 | 2602 | +33 s | 1 | giữ ≥ 35′ | sau đó Kachi tự ghi |
| 20/09 10:30 | 1.84 | 2602 | +9 s | 3 | giữ 13,5′ | rồi Kachi (giọng) ghi tắt |
| 22/09 08:31 | 1.90 | 2602 | +6,8′ | 1 | giữ 62′ | |
| 23/09 09:50 | 2.15 | 2602 | +6 s | 3 | giữ ≥ 17,5′ | đọc cuối = 1, tiến trình hết ngay sau ⇒ nghi tắt máy |
| 24/09 16:35 | 2.25 | 2602 | +5,9′ | 1 | giữ 50′ | |
| 25/09 10:52 | 2.30 | 2602 | +7 s | 3 | giữ ≥ 89′ | |
| 25/09 14:04 | 2.36 | 2602 | +3,8′ | 1 | giữ 107′ | |
| 25/09 19:59 | 2.56 | 2602 | +6,4′ | 1 | giữ ≥ 40′ | tới hết nhật ký |
| 27/09 09:57 | 2.74 | 2602 | +6 s | 3 | 3 ở +32′ | không đọc +0,5…+32′ |
| 27/09 20:44 | 2.77 | 2602 | +4,2′ | 1 | giữ 67′ | |
| 28/09 15:08 | 2.81 | 2602 | +5,1′ | 3 | giữ 31′ | |
| 29/09 11:20 | 2.82 | 2602 | +1,4′ | 1 | tụt +12,5′ | trùng tắt/mở máy (§8.5) |
| 06/10 07:04 | 2.89 | 2602 | +75 s | 1 | giữ 46′ | 52 km/h |
| 06/10 13:44 | 2.90 | 2602 | +22 s | 1 | giữ 92′ | tới lúc tắt máy · 39 km/h |
| 06/10 14:18 | 2.90 | 2602 | +13 s | 3 | giữ 59′ | tới lúc tắt máy |
| 07/10 07:08 | 2.93 | 2602 | +101 s | 1 | giữ 81′ | tới lúc tắt máy · 63 km/h |
| **07/10 16:52** | — | **lên 2606** | | | | |
| 07/10 17:15 | 2.95 | 2606 | +58 s | 1 | giữ 23,5′ | rồi Kachi (giọng) bật sưởi · 41 km/h |
| 09/10 11:02 | 2.97 | 2606 | +91 s | 1 | giữ ≥ 89′ | không có tốc độ trong log (buổi đo, có thể đỗ) |
| **10/10 13:54** | 2.98 | 2606 | +15 s | — | **tụt +7,4′** | ? · 66 km/h |
| **11/10 16:43** | 2.99 | 2606 | +25 s | 1 | **tụt +6,3′** | ? · bản 3.0 được cài giữa chừng (+2,2′) · không đọc 3′ trước mốc |
| 12/10 08:48 | 3.0 | 2606 | +19 s | 1 | 3 ở +52′ | không đọc +2…+52′ ⇒ chưa quyết |
| 12/10 15:03 | 3.0 | 2606 | +19 s | 1 | **giữ ≥ 29′** | đọc liên tục (khe lớn nhất 20 s) · 52 km/h |
| 12/10 19:00 | 3.0 | 2606 | +18 s | 1 | **giữ 15,4′** | tới lúc tắt máy · 70 km/h |
| **13/10 08:27** | 3.0 | 2606 | +17 s | 1 | **tụt +10,1′** | ? · 47 km/h |
| 13/10 17:11 | 3.0 | 2606 | +18 s | 1 | **giữ 15,8′** | rồi Kachi (giọng) bật sưởi · 45 km/h |
| **14/10 07:56** | 3.0 | 2606 | +16 s | 1 | **tụt +10,0′** | **xe tự tắt** [ĐO logcat, §3] |

Đếm [ĐO số liệu · SUY phân loại]:

- **2602 (17/09 → 07/10 sáng, Kachi ≈1.71 → 2.93)**: 16 lần đủ dữ liệu, **0 lần** tụt không rõ lý do trong 3–15′ sau lên nguồn. Mọi lần tụt đều là Kachi tự ghi, đổi sưởi/mát hoặc tắt máy.
- **2606 (07/10 16:52 →, Kachi ≈2.95 → 3.0)**: 10 lần. **4 lần tụt ở +6…+10′** (10/10, 11/10, 13/10, 14/10). **5 lần giữ quá +15′** trong lúc chạy (07/10 17:15, 12/10 15:03, 12/10 19:00, 13/10 17:11, cộng 09/10 nhiều khả năng đỗ). 1 lần chưa quyết.
- Ba ngày đầu trên 2606 (07/10 tối → 09/10) không có lần tụt nào. Lần tụt "?" đầu tiên là 10/10. Ngày 07/10 tối là buổi đo nhiều lần tắt/mở máy, không đủ dữ liệu.

### 10.3 Một thay đổi thứ hai cùng lúc với 2606: xe thôi nhớ mát ghế qua lần tắt máy [ĐO log · SUY nghĩa]

So lượt đọc cuối trước một quãng im ≥ 20′ với lượt đọc đầu sau đó, chỉ lấy các lần lượt cuối = 3 và Kachi không ghi gì trong quãng im:

- **2602**: 9 lần vẫn = 3 khi lên nguồn lại, ví dụ qua đêm 25/09 20:46 → 26/09 18:03, 26/09 19:00 → 27/09 09:57. 5 lần về 1, 1 lần về 2.
- **2606**: 7/8 lần về 1, gồm cả bốn lần qua đêm 10/10 → 14/10. Lần duy nhất còn 3 là quãng 37′ ngày 12/10, có thể không phải một lần tắt máy thật.
- Kachi không ghi mức TẮT lúc tắt máy: đường bulk bỏ qua mức Tắt (`SeatComfortApplier.kt`, `apply()`), và trong các quãng im không có dòng `setSeatVentilatingState` nào của Kachi [ĐO].

⇒ Từ 2606, cách ECU/MCU (hoặc tầng dưới IVI) quản lý mát ghế quanh chu kỳ nguồn **đã đổi**. Ở 2602 mức mát sống qua lần tắt máy, ở 2606 thì không. Đây là cùng vùng hành vi với lỗi tụt sau lên nguồn [SUY].

### 10.4 Phía Kachi: mã có đổi không [ĐO `git log -p`]

| Tệp | Thay đổi kể từ 13/09 | Ảnh hưởng hành vi ghi |
|---|---|---|
| `comfort/SeatComfortApplier.kt` | `798e4a8c` (13/09, 1.41): gỡ chỗ gọi thứ hai ở màn ClusterNav cũ · `1823c670` (27/09, 2.76): `systemProp` chuyển sang `SysProps.get` (chỉ dò mẫu xe) | **Không**. Cùng `setSeatVentilatingState(1, 3)` qua `BydHal.callNamedInt`, cùng trễ `START_DELAY_MS = 5 000`, một method mỗi ghế (không ghi cả sưởi lẫn mát), không thử lại, không ghi TẮT rồi BẬT |
| `BootSetupService.kt` (thứ tự ghế → PM2.5 → lấy gió trong) | Khối ghế không đổi từ `e0cb368c` (20/09, 1.85: thêm Automation **sau** khối này). `0c6c480e`/`0e62bc79` (23/09) chỉ đụng phần trợ năng ở đầu | **Không** |
| `ClusterNavBridgeReapply.kt` `step("seat")` | gọi `applyNow` **khi đổi hồ sơ**, không chạy lúc lên nguồn | Không liên quan lúc lên nguồn |

Nhật ký cũng cho thấy **cùng một bộ lệnh lúc lên nguồn** ở cả lần giữ lẫn lần tụt, trên cả hai firmware [ĐO]:
- `setSeatVentilatingState(1,3)` + `setAutoCleanAirState(1)` + lấy gió trong `0x1de0001b=1` trong cùng một giây;
- `HomeGuard` (từ 2.96) có mặt ở cả lần giữ (12/10 ×2) lẫn lần tụt (11/10, 13/10).

Mốc ghi so với lúc lên nguồn có đổi:
- Trước 05/10 thường **+3…+7′**, vì tiến trình lên muộn.
- Từ 05/10 luôn **+15…+25 s**.
- Nhưng trên 2602 đã có 7 lần ghi sớm (+6 s…+101 s) mà vẫn giữ 35–92′: 20/09 ×2, 23/09, 25/09, 06/10 ×3, 07/10 sáng.
⇒ Thời điểm ghi **không** phải yếu tố phân biệt [ĐO bảng 10.2].

### 10.5 Kết luận

1. **Mốc bắt đầu khớp với firmware 2606, không khớp với một bản Kachi** [SUY mạnh, nhiều khả năng].
   - Trên 2602: 0/16 lần tụt sớm, qua ~25 bản Kachi khác nhau (1.71 → 2.93).
   - Trên 2606: 4/10 lần tụt sớm.
   - **Cùng một APK 3.0** (mã băm `Gt-zPAYY`, 11/10 → 14/10 07:59) cho cả giữ (12/10 15:03, 12/10 19:00, 13/10 17:11) lẫn tụt (13/10 08:27, 14/10 07:56). Mã ghi ghế không đổi hành vi từ 13/09.
   ⇒ Giả thuyết **A bị loại ở dạng "một bản Kachi làm hỏng"** [ĐO: cùng mã, cùng APK, cả hai kết quả].
2. **B (2606) là giải thích phù hợp nhất nhưng CHƯA chứng minh** [SUY].
   - Lý do tin: mốc 07/10 tách bảng gọn; cùng lúc đó hành vi "nhớ mát ghế qua tắt máy" cũng đổi (§10.3: 9/15 → 1/8); ghi chú phát hành 2606 có hai mục chạm đúng vùng này (`fw-2606-release-notes-v204.md`): **mục 1** "liên kết bộ nhớ giữa xe và tài khoản — cài đặt cá nhân theo tài khoản BYD" và **mục 5** "Trợ lý BYD thêm lệnh giọng nói tắt sưởi, bật/tắt thông gió". Hai mục này có liên quan hay không là [ĐOÁN].
   - Lý do chưa chắc: Kachi 2.95+ lên **cùng chiều** 07/10 (17:16, 24 phút sau 2606), nên ranh giới firmware và ranh giới Kachi trùng nhau. Ba trong bốn lần tụt không có logcat để loại trừ người dùng tắt bằng UI BYD.
3. **Lỗi có điều kiện**, không phải lần nào cũng xảy ra: trên 2606 có 4 lần tụt và 5 lần giữ.
   - Tụt: 10/10 13:54, 11/10 16:43, 13/10 08:27, 14/10 07:56.
   - Giữ: 12/10 15:03 và 19:00, 13/10 17:11, 07/10 17:15.
   - Gió 7 lúc lên nguồn có ở 3/4 lần tụt, nhưng cũng có ở lần giữ 07/10 17:15 và ở hai lần giữ trên 2602 (20/09, 22/09) ⇒ không phải điều kiện đủ [ĐO].
   - Điều kiện thật [CHƯA BIẾT].
4. **Không có commit nghi vấn** phía Kachi. Không bản nào đổi thời điểm, method, thứ tự hay số lần ghi ghế lúc lên nguồn.

### 10.6 Dữ liệu tối thiểu để chốt

1. **Kéo lại nhật ký Kachi** (`/sdcard/Android/data/com.byd.launcher/files/kachi-logs/`) để thêm chuyến tối 14/10 vào bảng 10.2. Rẻ, nhưng vẫn không quy kết được người tắt.
2. **Thí nghiệm A của §9** (quyết định giữa A và B): 2–3 chuyến, tắt `SeatComfort`, bật mát ghế bằng **thanh AC BYD** trong 20 s đầu.
   - Tụt ở +6…+10′ ⇒ B chắc (không cần Kachi).
   - Giữ ≥ 20′ ở cả 3 chuyến ⇒ quay lại tìm phía Kachi.
3. **Logcat main + system từ lúc lên nguồn** cho mỗi lần tụt (ClusterDiag tự chụp, §11). Mục đích: xác nhận "không ai ghi" như 14/10, và xem dòng `ZipHelper`/autohal ở §9.
4. **Off-car, không cần xe**: so `BYDAutoFeatureIds` / `CarSetting` / `AirConditioning` 2602 ↔ 2606 tìm hằng/logic **mới** về nhớ ghế theo tài khoản hoặc chu kỳ nguồn (ví dụ `BODYWORK_ACCOUNT_MEMORY_AWAKE_STRATEGY`, `_POWER_OFF_MEMORY_STATUS`: có trong dex 2606, chưa so với 2602).
   Cũng kéo `auto.default.so` của **2606** (§8.2 mới đọc bản 2602) để so `seatmessageconver`.

## 11. So sánh 2602 ↔ 2606 (off-car, 14/10 tối)

> Câu hỏi: 2606 có logic MỚI nào trên IVI tắt mát/sưởi ghế sau 6–10 phút, hoặc làm xe thôi nhớ mát ghế qua lần tắt máy không?
> Nguồn: `framework.jar` + app 2602 lấy từ `fw-2602-diff/new_out/system.img` (debugfs), bản 2606 từ `fw-2606/_system_*`. jadx 1.5.0, từng lớp hoặc cả APK. Diff đã chuẩn hoá: bỏ số ≥ 5 chữ số, hex, `R.*`, chú thích JADX. Logcat 14/10 (`logcat-075959.txt`, `logcat-boot-dump.txt`). Số dòng là số dòng jadx.

### 11.1 Kết luận ngắn

1. **IVI 2606 không có logic mới nào ghi mát/sưởi ghế** [ĐO nguồn + log]. Hằng tài khoản/nhớ ghế mới chỉ có trong bảng hằng của SDK DiCar. Framework 2606 không có các hằng này, và không app nào dùng chúng ngoài bảng ánh xạ tên (§11.2–§11.3).
2. **Lúc 08:05:58 không tầng IVI nào ghi** [ĐO log], kể cả các đường mới của 2606 (§11.4):
   - DiCarServer 3.4.0 (dịch vụ ghế mới) có log mọi lần `setProperties`, nhưng không có lần nào suốt chuyến;
   - app tài khoản BYD im lặng quanh mốc;
   - đám mây IVI không nhận gói nào quanh mốc.
3. **Thứ đổi thật ở 2606, nằm dưới IVI**: **firmware MCU** `18.3.5.2601300.2` → **`18.3.5.2606100.2`** [ĐO getprop: `docs/diagnostics/carlog-kachi-20260914-2044/00-getprop.txt` · `fw-2606/meta/getprop.txt:216`]. Gói OTA `qc+mcu+dsp+anc` ghi cả MCU (§8.3).
   ⇒ Đây là **ứng viên mạnh nhất** cho cả hai thay đổi hành vi ở §10 (tụt +6…+10′ và thôi nhớ qua tắt máy) [SUY]. MCU bị mã hoá nên off-car không đọc được [ĐO §8.3].
4. HAL `auto.default.so` của 2606 **chưa có bản off-car** (chỉ có bản 2602) [CHƯA BIẾT có đổi không]. Một lệnh trên xe sẽ chốt được (§11.6).
5. **Không có cài đặt BYD nào trên IVI điều khiển việc này** [ĐO nguồn: UI ghế/điều hoà 2606 không có mục mới cho ghế]. Vì vậy Kachi **chưa có gì để tôn trọng** ngoài luật của xe.

### 11.2 `framework.jar` 2602 ↔ 2606 [ĐO]

| Lớp | Kết quả |
|---|---|
| `BYDAutoAcDevice`, `BYDAutoBodyworkDevice` | **Giống từng byte** sau jadx |
| `BYDAutoSettingDevice` | Chỉ thêm 8 `setIsaMap*` (giới hạn tốc độ bản đồ). Không đổi dòng nào về seat / venti / heat / memory |
| `BYDAutoFeatureIds` | 3 796 → 3 878 hằng: **+82, −0**. Trong số thêm: ADAS bản đồ, ISA, sạc, thống kê, đèn phanh, dây an toàn, `SETTING_CPD_SW_SWITCH_MEMORY_STATUS` (nhớ công tắc **phát hiện trẻ em** CPD, jadx :12733/:13734). **172 dòng hằng `SEAT_(VENTILAT|HEAT)` giống hệt nhau** |
| `BODYWORK_ACCOUNT_MEMORY_AWAKE_STRATEGY`, `*_POWER_OFF_MEMORY_STATUS` | **Không có** trong framework của bản nào ⇒ trên DiLink3 không app nào đọc/ghi được chúng qua framework |

### 11.3 Các hằng "nhớ theo tài khoản" ở đâu, ai dùng [ĐO nguồn 2606]

- Chúng nằm trong **bảng hằng chung của SDK DiCar** (`com.byd.feature.body.Body` / `com.byd.feature.setting.Setting`), có ở mọi app build với SDK 2606: CarSetting, AirConditioning, AppServer, DiCarServer, Cota, Launcher3, FileManager… Chuỗi này không có trong bản 2602 của các app đó.
  - `CarSetting` `Body.java`: `BODYWORK_ACCOUNT_MEMORY_AWAKE_STRATEGY = 0x32B31046` (:14); `BODYWORK_DRIVER_SEAT_VENTILATION_HEAT_MEMORY = 0x2AA0403E`, `_2E4 = 0x2E40A03E`, `_311 = 0x31103144` (:415–417); `BODYWORK_STEERING_WHEEL_HEATING_ACCOUNT_MEMORY_FLAG = 0x4290003D` (:2059).
  - `Setting.java`: `SET_PASSENGER_SEAT_ACCOUNT_MEMORY_AWAKE_STRATEGY_SET = 0x16B05134` (:3141), `SETTING_DRIVER_SEAT_REMOTE_VENTILATION_STATUS = 0x29E00010` (:643), `SETTING_DRIVER_SEAT_AUTOMATIC_HEATING_STATE = 0x48D0000E` (:620).
- **Nơi dùng duy nhất là bảng ánh xạ tên** `BodyMapper` / `SettingMapper` (DiCarServer `BodyMapper.java:2034–2042`, `SettingMapper.java:669`; AppServer cùng vị trí). Không có lớp nào đọc, ghi hay nghe các id này [ĐO grep toàn bộ CarSetting, AirConditioning, AppServer, DiCarServer, CustomServer].
- Ánh xạ tên → field framework bằng reflection không ra được vì framework không có field đó (giống tình huống HUD ở `RE-2606-findings.md` §3.1) [SUY].
- Không id nào trong số này xuất hiện trong logcat 14/10: `2aa0403e|32b31046|2e40a03e|31103144|4290003d|29e00010|48d0000e` = 0 dòng [ĐO].
- ⇒ Đây là bảng hằng đa nền tảng. Nó cho thấy BYD **có** chức năng "nhớ mát/sưởi ghế theo tài khoản" ở **ECU** (khung `0x2AA`/`0x2E4`/`0x311`/`0x32B`) trên các nền khác. **Không** chứng minh được xe owner có chức năng này, cũng không có IVI nào kích hoạt nó [SUY].

### 11.4 Từng app 2606 có thể chạm ghế [ĐO nguồn · ĐO log 14/10]

| App | 2602 → 2606 | Ghi ghế? | Log quanh 08:05:58 |
|---|---|---|---|
| `AirConditioning` | Diff chuẩn hoá chỉ đổi `R.java` + `PopupAlertDialog`. `AirSeatingVentilateAndHeatModel` **giống hệt** | Chỉ khi người dùng chạm (§8.4) | Không có |
| `CustomServer` | Chỉ `study/*AutonomouslyPolicy` đổi: bỏ ghi `low_fuel_warning` (`BydCanfdStudyAutonomouslyPolicy` cũ :1671, :1692) | `sendCloseVentiHeatMsg` không đổi (§2) | `sendCloseVentiHeatMsg...3` (mode 3, không hẹn) |
| `CarSetting` 1.4.1.212 → .264 | Ở vùng ghế chỉ `SeatAdjustMainFragment` đổi. **Cũ** (:362–372): chế độ ghế-theo-tài-khoản bật khi ECU báo `SET_SEAT_ACCOUNT_LINKAGE_CONFIG` (0x4B300102) = 1. **Mới** (:364–379): bật khi ghế điện (`SETTING_LF_ELECTRIC_CONFIG(_HAL)` ∈ 1..3) **và** đã cài app tài khoản BYD, bỏ cờ ECU | Không. Đây chỉ là hiện/ẩn 3 nút **vị trí** nhớ ghế (`location1..3`) trong `onResume` | Không có |
| `DiCarServer` 2.1.0 → 3.4.0 | **Mới**: `CarSeatVentHeatServiceImpl`, chỉ ghi khi có client gọi `setSeatVentilatingLevel` / `setSeatHeatingLevel` (:296–341). Không có hẹn giờ, không tự chạy | Chỉ theo yêu cầu. Mỗi lần ghi đi qua `CarPropertyServiceImpl.setProperties`, hàm này `Log.i` (:34–35) | **0 dòng `setProperties` cả chuyến**; chỉ có 7 `getProperty` |
| Client của dịch vụ đó | Chỉ có stub SDK (`ICarSeatVentHeatManager`) trong AppServer 2606 và Launcher3. Không có lời gọi `.setSeatVentilatingLevel(` nào ngoài stub | — | — |
| `BydDiLinkAccount` (`com.byd.diLinkAccount`) | Ảnh 2602: `V50F.251009_5_mp`. Xe chạy **`V50F.260401_5_mp`** [ĐO tag log] — bản 2606 **chưa kéo về**. Bản 2602 (`spi/y7.java`): lúc lên nguồn đẩy cài đặt tài khoản xuống xe (:231–302), toàn bằng `set(int[])` **có log**. Hồ sơ `CarCommonSettings` gồm 31 trường: đèn không khí, âm thanh, màu chủ đề, **không có ghế** | Theo bản 2602 thì không | Không có dòng nào 07:59:58 → 08:43. JSON 08:45:20 trên 2606 cũng chỉ có đèn/âm thanh (30 khoá), **không có ghế** [ĐO] |
| `BydDiLinkAccountService` 13 → 14 | Bản 14 chưa kéo về (2606 để ở `/product/app`). Bản 13 (`MyApp.java:540–580`): lúc `POWER_LEVEL = 2` chỉ đánh thức một loạt app tài khoản, trễ 500–2 400 ms. Không có API ghế | Bản 13 không | Không có dòng nào quanh mốc |
| `SpeechHMI` 1032 → 1033 | Bản 1033 chưa kéo về. Lệnh ghế đi qua `set(int[])` có log (08:08:18, §3) | Chỉ theo lệnh nói | Không có |

Thêm từ log [ĐO]:

- `set featureID` 08:04–08:06 chỉ đến từ:
  - AmapService (3935) và Kachi (4966): id `43e0…`/`43f…` lên cụm;
  - pid 3306: `4e1000xx`;
  - system_server (666): `aa000146` mỗi phút ở giây :30. Payload là ngày giờ, ví dụ `[7,-22,10,14,8,5,30,…]` = 2026-10-14 08:05:30 ⇒ **đồng bộ đồng hồ**, không phải ghế.
- Đám mây IVI (`[BYDCLOUD]`) cả chuyến chỉ **nhận** đúng một gói, lúc 08:01:22 (85 byte). Từ 08:05:44 tới 08:06:09 chỉ có gửi (FuncID 554) ⇒ không có lệnh từ xa nào đi qua IVI vào lúc tắt [ĐO].
  Lệnh từ xa đi qua **T-Box** (ECU riêng, không qua IVI) thì không thấy được trong log này [CHƯA BIẾT].

### 11.5 Dấu vân tay thời gian

- Mốc tắt 08:05:58.106 = **+595,7 s** sau `onPowerLevelChanged level = 2` (07:56:02.375) [ĐO], tức ~4 s trước tròn 10′.
  - Trong ±5 s không có sự kiện tài khoản, đồng bộ đám mây hay đăng nhập nào.
  - `AccountManagerService LOGIN_ACCOUNTS_CHANGED` lúc 08:01:40, 08:01:42 và 08:07:18 là tài khoản Android/GMS, và lệch xa mốc tắt.
  - `AutoLoginMgr` của app tài khoản BYD chỉ chạy khi đổi số (08:43–08:45).
- Các mẫu khác ở +7,4′, +6,3′, +10,1′ (§10.2) ⇒ **không** phải một bộ đếm 600 s cố định [ĐO bảng]. Hợp với một điều kiện (giai đoạn đầu sau lên nguồn) ở MCU/ECU hơn [SUY].

### 11.6 Việc trên xe để chốt (thêm vào §9, chỉ đọc)

```
# a. HAL có đổi ở 2606 không (sha1 bản 2602 = 6eee2b0af6cfa7bf9caa9ae2be71a2fd7963ffd6)
adb shell sha1sum /system/lib64/hw/auto.default.so
#    khác ⇒ adb pull rồi so FunctionTable::*/seatmessageconver với bản 2602 (§8.2)
# b. kéo 3 APK 2606 còn thiếu để soi đường tài khoản
adb shell pm path com.byd.accountservice; adb shell pm path com.byd.diLinkAccount; adb shell pm path com.byd.vrassistant.xf
#    rồi adb pull từng đường dẫn; so với bản 2602 (sha1 accountservice 13 = d2ffaad8…, diLinkAccount 251009 = ecc7e6b1…)
```

Thí nghiệm F (bổ sung cho A–E của §9, mỗi chuyến một cái): **đăng xuất tài khoản BYD trên IVI** rồi lặp lại A.
- Vẫn tụt ⇒ loại hẳn nhánh "nhớ theo tài khoản", còn lại MCU.
- Hết tụt ⇒ nhánh tài khoản có dính. Khi đó soi APK của bước b.

### 11.7 Nếu là luật của xe thì Kachi làm gì

- Off-car **không tìm thấy** cài đặt BYD nào (IVI hay app) bật/tắt "nhớ mát ghế" hay "tự tắt mát ghế" cho dòng xe này [ĐO §11.3–§11.4]. Không có gì để người dùng chỉnh, và Kachi cũng không có gì để đọc rồi tôn trọng.
- Nếu thí nghiệm F cho thấy liên quan tài khoản: mục người dùng chỉnh được nằm ở **app tài khoản BYD / Cài đặt › Ghế** (nút nhớ vị trí 1–3, chỉ hiện trên 2606 khi có app tài khoản). Kachi chỉ nên trỏ người dùng tới đó, **không** ghi đè.
- Mọi hướng sửa phía Kachi vẫn chờ §9 theo luật §14 (hướng F1 ở §4.2).
