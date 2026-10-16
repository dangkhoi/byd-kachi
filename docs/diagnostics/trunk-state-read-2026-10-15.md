# Đọc trạng thái cốp thật (Seal, car.type 138, fw 2606, CAN-FD) — 2026-10-15

> **Trạng thái**: Current · 2026-10-15 · backlog `TRUNK-STATE-READ` (nối `KEYCTL-TRUNK-READ`, OQ14 của `specs/kachi-286-field-fixes.html`).
> **Yêu cầu owner 15/10**: *"xem có đọc được cốp không nhé, lần trước test thì fail, vì sẽ có trường hợp đóng cốp bằng
> nút vật lý thật của xe, thì lúc đó mình không đóng được trạng thái cốp thật, gây conflict"*.
> **Phạm vi phiên này**: nghiên cứu + kế hoạch đo trên xe (CLAUDE.md §14 tầng 1). **Cập nhật cùng ngày: đã nối dây ở 3.1.6 (R32) theo quyết định owner — xem §5.** **16/10: nắp 3D đóng sập sau khi mở — gốc + sửa ở §8.**

## 0. Kết luận ngắn

| # | Khẳng định | Mức |
|---|---|---|
| K1 | Lần thử cũ (25/09) hỏng vì **sai tên getter**, không phải vì cốp không có cảm biến: `getHatchDoorStatus` **không tồn tại** trong `framework.jar` của xe; cầu `hal` trả `unavailable: null (off-car / method absent / no permission)`. Kết luận "cốp xe này không có cảm biến" của DATA-DEAD-7 là **quy kết sai** từ một lỗi cơ chế (CLAUDE.md §2). | [ĐO] log xe 25/09 + [ĐO nguồn] fw 2606 |
| K2 | Đường đọc đúng là `BYDAutoBodyworkDevice.getDoorState(6)` (area 6 = `BODYWORK_CMD_DOOR_LUGGAGE_DOOR`) ⇒ feature `BODYWORK_LUGGAGE_DOOR` = **692060186 (0x2940001A)** trên CAN-FD. Thang `BODYWORK_STATE_CLOSED 0 · OPEN 1 · UNDEFINED 255`. | [ĐO nguồn] fw 2606 |
| K3 | Trên **chính xe owner, 14/10**, sau lệnh mở của Kachi, xe phát sự kiện `0x2940001A = 1` tới mọi tiến trình đăng ký, và dịch vụ nhắc nhở của BYD gọi `getDoorState(6)` ⇒ **1**; sau lệnh đóng, sự kiện `= 0` và `getDoorState(6)` ⇒ **0**. | [ĐO] logcat xe 14/10 |
| K4 | Chính SystemUI của BYD (ô Cốp ở trung tâm điều khiển, `TrunkItem`) dùng **đúng feature này**: đọc lúc dựng, nghe sự kiện, và một nhịp đọc lại 2 s sau khi mô-tơ thanh chống dừng. Đây là đường BYD tự dùng. | [ĐO nguồn] + [ĐO] log khớp từng chuỗi |
| K5 | Cốp đóng bằng **nút vật lý** cho ra cùng số 0: **chưa đo**. Nhiều khả năng có, vì giá trị là trạng thái khoá cốp từ thân xe, không phải dấu của lệnh IVI. | [SUY] — chốt bằng §3 bước 4 |
| K6 | Tiến trình **Kachi** gọi `getDoorState(6)`: chưa đo. Kachi đang đọc `getDoorState(1..4)` mỗi ~10 s với cùng quyền `BODYWORK_GET_PERM` ⇒ nhiều khả năng đọc được. | [SUY mạnh] — chốt bằng §3 bước 1 |

## 1. Vì sao lần thử cũ hỏng

- 25/09 (log `perf-oncar-2026-09-26/kachi-logs/usage-1790325388671.log:4785-4787, 5035-5037`): `cmd=hal dev=BYDAutoBodyworkDevice m=getHatchDoorStatus` (không arg, rồi arg 0) ⇒ `"value":"","reply":"unavailable: null (off-car / method absent / no permission)"`.
- `getHatchDoorStatus()` chỉ có trong **stub OpenBYD** (`jadx-openbyd/.../BYDAutoBodyworkDevice.java:881`, `throw new RuntimeException("Stub!")`), không có trong `framework.jar` của fw 2606 (grep cả cây jadx: 0 kết quả). `oncar-master/1-hal.md:537` đã ghi đúng điều này, nhưng 25/09 vẫn gỡ datum với lý do "cốp không có cảm biến".
- `getDoorState(6)` được đề xuất trong OQ14/`KEYCTL-TRUNK-READ`, nhưng **chưa bao giờ được gọi** từ Kachi.
- `getBackDoorOpenedHeight` (ứng viên ở comment `ControlRegistry` dòng `trunk`) là **cài đặt độ cao mở** (`SET_BACK_DOOR_OPEN_HEIGHT`, min 15 / max 100; đo 17/09 = 80), không phải trạng thái ⇒ loại.

## 2. Ứng viên đường đọc (xếp hạng)

| Hạng | Đường | Feature id (CAN-FD) | Thang | Kiểu | Mức bằng chứng |
|---|---|---|---|---|---|
| **1** | `BYDAutoBodyworkDevice.getDoorState(6)` | `BODYWORK_LUGGAGE_DOOR` 692060186 = 0x2940001A | 0 đóng · 1 mở · 255 chưa xác định (lạ ⇒ `null`) | **poll** (Kachi chưa có listener); xe cũng phát sự kiện | [ĐO nguồn] + [ĐO log xe 14/10, tiến trình BYD] · tiến trình Kachi [SUY mạnh] |
| 2 | Generic `get(692060186)` (op `getid`) | như trên | như trên | poll | cùng nguồn — dùng làm kiểm chéo trên xe |
| 3 | Sự kiện `postEvent(1001, 0x2940001A, v)` qua `registerListener(…, int[]{692060186})` | như trên | như trên | **event** | [ĐO log] sự kiện tới ≥ 9 tiến trình (SystemUI, 3D AVM `com.byd.avc`, nhắc nhở giọng nói…). Kachi **chưa có** đường listener (không có `registerListener` trong `app/` hay `:core`) ⇒ hạng thấp vì cần cơ chế mới |
| phụ | Mô-tơ thanh chống trái/phải `BODYWORK_LEFT/RIGHT_STRUT_MOTOR_RUNNING_STATUS` | 1074790418 = 0x40100012 · 1074790454 = 0x40100036 | 0 dừng · 1 / 2 đang chạy | event/poll | [ĐO log] trái: 1 sau lệnh mở, 2 sau lệnh đóng, 0 khi dừng. Nghĩa 1 = đang mở · 2 = đang đóng là **[SUY]**. Chỉ để biết "cốp đang chạy", không thay hạng 1 |
| loại | `getHatchDoorStatus` | — | — | — | không tồn tại trên ROM (K1) |
| loại | `getBackDoorOpenedHeight` | `SET_BACK_DOOR_OPEN_HEIGHT` 1074790408 | 15..100 | — | là cài đặt, không phải trạng thái |
| tham khảo | `BODYWORK_TAILGATE_TYPE_SIGN` 1274019868 · `SET_CAR_BACK_DOOR_ELECTRIC_MODE_DET` 1074790427 | — | — | — | SystemUI dùng để biết cốp điện/cốp tay (`getTrunkOnlineState`); đo một lần để ghi loại cốp của xe |

### 2.1 Nguồn (file:line)

- fw 2606 `framework.jar` (jadx 15/10): `BYDAutoBodyworkDevice.java:207` `BODYWORK_CMD_DOOR_LUGGAGE_DOOR = 6` · `:239-241` `BODYWORK_STATE_CLOSED 0 / OPEN 1 / UNDEFINED 255` · `:695-725` `getDoorState(int)`, `case 6 → super.get(mDeviceType, BYDAutoFeatureIds.BODYWORK_LUGGAGE_DOOR)` (`:715-716`), quyền `BODYWORK_GET_PERM` (cùng quyền với cửa 1..4). `BYDAutoFeatureIds.java:15935-15940` `isCanFD ? 692060186 : …`. Thanh chống `:5367` / `:5648`. Đối chiếu với bản jadx fw 14/10 trong cùng phiên: thân `getDoorState` giống hệt (chỉ khác tên biến).
- SystemUI xe (dex kéo về, jadx) `com.byd.bydqsmodeslib.item.vehicle.TrunkItem`: `:55-56` id đăng ký (`BODYWORK_LUGGAGE_DOOR` + hai thanh chống) · `:60-73` listener `onDataEventChanged` → `updateTrunkState(v)` (1 ⇒ mở, khác ⇒ đóng) · `:99-100` `get(int[]{id})` · `:113-135` `startTrunkReadBackTiming(2000)`: bỏ qua sự kiện (`mIgnore`) rồi đọc lại sau 2 s · `:139-146` khi cả hai thanh chống = 0 ⇒ hẹn đọc lại · `:149-158` đọc lúc dựng · `:365` lệnh mở/đóng = `SET_VOICE_CTRL_BACK_DOOR_SET` 1 / 3 (`setTrunkStateToMCU(z ? 1 : 3)`) (**cùng lệnh Kachi đang gửi**).
- Log SystemUI trên xe khớp nguyên văn các chuỗi trên (`Con--TrunkItem: eventType :692060186 …`, `startTrunkReadBackTiming delay=2000`, `doAcDefrostReadBack state is 1`) ⇒ đây đúng là mã đang chạy.

### 2.2 Dòng thời gian đo được trên xe owner, 14/10 (Kachi 3.1.x, xe đứng, `tap-gate speed=0`)

| Giờ | Sự kiện |
|---|---|
| 08:46:56.436 | Kachi `ctl #10 id=trunk m=named:voiceCtlBackDoor args=[1] rc=0` |
| 08:46:56.719 | `postEvent 1001, 0x2940001a, value = 1` tới ≥ 9 tiến trình (+283 ms) · nhắc nhở BYD `onDoorStateChanged area=6 state=1` → `getDoorState(6)` ⇒ **1** |
| 08:46:56.99 | thanh chống trái `0x40100012 = 1` |
| 08:47:01.84 | thanh chống trái `= 0` (dừng) |
| 08:47:02.558 | Kachi `ctl #11 id=trunk … args=[3] rc=0` |
| 08:47:03.190 | thanh chống trái `= 2` |
| 08:47:03.855 | SystemUI đọc lại `BODYWORK_LUGGAGE_DOOR` ⇒ **1** (đang đóng, chưa khoá ⇒ vẫn "mở") |
| 08:47:07.734 | thanh chống `= 0` |
| 08:47:08.402 | `postEvent 0x2940001a, value = 0` · `getDoorState(6)` ⇒ **0** (+5,8 s sau lệnh đóng) |

Hệ quả cho thiết kế: số đọc bật **1 ngay khi chốt nhả** (~0,3 s sau lệnh mở) nhưng chỉ về **0 lúc chốt khoá** (~6 s sau lệnh đóng). Thời gian giữ lạc quan sau lệnh của Kachi phải dài hơn khoảng đó (§4.2).

Các chuyến 15/10 (`all.txt`, `logcat-arrive-dump.txt`): không có sự kiện `0x2940001a` nào (cốp không được mở); chỉ có lượt đăng ký listener của 3D AVM: `[29400008, 2940000a, 2940000c, 2940000e, 2940001c, 2940001a, 4fb00016]` (bốn cửa · nắp ca-pô · cốp · khoá). 3D AVM cũng gọi `getDoorState(1..6)` lúc dựng ⇒ 0 (cốp đóng).

## 3. Kế hoạch đo trên xe (CHỈ ĐỌC — tầng 1, §14)

Điều kiện: xe đỗ, số P; adb vào xe (cầu `nc` → loopback, xem memory `kachi-adb-car-tunnel`); Kachi bản `vehicleTest`; **bật Chế độ kiểm thử trong Kachi** (không có đường bật bằng broadcast — có chủ ý). Mọi lệnh dưới là `get`/`getid` ⇒ không qua cổng `auto_confirm`, không đổi một bit nào của xe. Cốp được mở/đóng **bằng tay người** (chìa, nút), không bằng lệnh.

```sh
A="adb -s <xe>"
BR='am broadcast -n com.byd.launcher/com.byd.clusternav.launcher.testbridge.KachiTestBridge -a com.byd.launcher.TEST --es cmd hal'
rd() {   # một vòng đọc: cốp theo 2 đường + thanh chống + một cửa làm mốc
  $A shell "$BR --es op get   --es dev BYDAutoBodyworkDevice --es m getDoorState --es args 6"
  $A shell "$BR --es op getid --es dev BYDAutoBodyworkDevice --es m 692060186"
  $A shell "$BR --es op getid --es dev BYDAutoBodyworkDevice --es m 1074790418"
  $A shell "$BR --es op getid --es dev BYDAutoBodyworkDevice --es m 1074790454"
  $A shell "$BR --es op get   --es dev BYDAutoBodyworkDevice --es m getDoorState --es args 1"
}
# Cửa sổ log riêng, chạy suốt buổi:
$A logcat -c
$A logcat -v time | grep -E "KachiTest|getDoorState|event_type =2940001a|event_type =40100012|event_type =40100036|Con--TrunkItem|onDoorStateChanged" \
  | tee trunk-read-$(date +%m%d-%H%M).log
```

| Bước | Thao tác của người | Lệnh | Kỳ vọng nếu đường đúng |
|---|---|---|---|
| 0 | — | `$A shell "$BR --es op getid --es dev BYDAutoSettingDevice --es m 1074790427"` · `… --es dev BYDAutoBodyworkDevice --es m 1274019868` | ghi loại cốp (SystemUI: 1 ⇒ cốp điện có học hành trình); chỉ để ghi lại |
| 1 | cốp **đóng** | `rd` ×3 (cách nhau ~2 s) | `getDoorState(6)` = **0** cả 3 lần (tiến trình Kachi đọc được, K6); `getid 692060186` cùng số; thanh chống 0 |
| 2 | mở cốp bằng **chìa** (hoặc nút mở trên cốp) | `rd` ngay khi cốp bắt đầu nhấc, rồi `rd` khi cốp đã dừng | log có `event_type =2940001a, value = 1`; `getDoorState(6)` = **1** cả lúc đang nhấc và lúc đã dừng; thanh chống ≠ 0 lúc chạy |
| 3 | — (cốp đang mở) | `rd` ×2 | ổn định **1** |
| 4 | đóng bằng **nút vật lý trên cốp** (ca xung đột owner nói) | `rd` lúc đang hạ, rồi `rd` ~2 s sau khi khoá | lúc hạ: vẫn **1**; sau khoá: log `value = 0`, `getDoorState(6)` = **0** |
| 5 | lặp 2–4 một lần nữa (nếu có nút đóng trong khoang/chìa thì dùng nút đó) | như trên | lặp lại được |

**Đạt** (mở khoá tầng 2): bước 1–4 đúng, lặp lại ở bước 5, không có 255/sentinel. **Không đạt**: ghi nguyên lời đáp (`reply`) + log vào `docs/diagnostics/`, cốp giữ Mở/Đóng riêng như hôm nay. Có 255 ⇒ coi như không đọc được ở lúc đó (`null`), ghi lại thời điểm.

Thu về: tệp `trunk-read-*.log` + các tệp `files/test/*-hal.json` của cầu.

## 4. Nối dây đề xuất sau khi §3 đạt (chưa làm — mô tả)

Thứ tự đúng §14: tầng 2 (đường đọc) → tầng 3 (luật `:core`) → tầng 4 (UI). Không hardcode tên gói hay mẫu xe; khác biệt chỉ đến từ số đọc.

### 4.1 Đường đọc = dữ liệu (một dòng)

Theo đúng mẫu *"cùng getter, khác vùng"* mà `sunroof` đang dùng (`readKey = "window_lf", readArg = 5`):

```kotlin
ControlDef("trunk", …, bindingKey = "BYDAutoSettingDevice.voiceCtlBackDoor",
    readKey = "door_rr", readArg = 6,   // getDoorState(6) = BODYWORK_LUGGAGE_DOOR; 0 đóng · 1 mở · 255 ⇒ null
    …)
```

- `readPathOf("trunk")` (`HalRoutes.kt:41-46`) sẽ cho `ReadPath(door_rr, BYDAutoBodyworkDevice.getDoorState, BODY, 6)`; không thêm getter, không thêm đường gateway thứ hai.
- 255 (`BODYWORK_STATE_UNDEFINED`) phải ra `null`: `HalReadTables.INVALID_VALUES` đã có `door_rr → {255}` (soát 27/09) [ĐO mã]. Lọc đó có áp cho lượt đọc của nút `trunk` (đi qua datum `door_rr` nhưng `readArg` 6) hay không phụ thuộc đường gọi — phải có một bài kiểm off-car khoá điều này trước khi bật.
- Datum riêng cho bảng Cửa và widget `w_car` (`tailgate_state`, khôi phục bộ phận `CarPart.TAILGATE` trong `GroupBoard.DOOR_PARTS`) là **quyết định của owner** — owner đã gỡ datum cốp 25/09. Đề xuất: chỉ nối `readKey` cho nút trước; hỏi owner có muốn hiện lại dòng "Cốp" ở bảng Cửa không.
- Bài khoá đi kèm: `KeyCtlTargetTest.cot chi co mo va dong rieng…` sẽ đỏ đúng ngày khai `readKey` (đã thiết kế sẵn như thế) ⇒ đổi bài đó cùng lúc, kèm trích dẫn log đo §3.

### 4.2 Ô cốp + nắp 3D theo trạng thái thật

- Hôm nay: `Car3dTaps.dot` rẽ vào nhánh *"vật không cảm biến"* (`cell == null`) ⇒ trạng thái = `ControlTileState.shared.isOn("trunk")` = lệnh cuối Kachi gửi (cờ RAM — đúng cái CLAUDE.md §5 cấm dùng để quyết). Khi nút có `readKey`, `r.cell` có số ⇒ nhánh đọc thật tự chạy, không cần mã riêng cho cốp.
- Giữ lạc quan sau lệnh của chính Kachi: dùng lại `LampHold` (hiện cốp bị loại khỏi `LampHold`, `Car3dTaps.kt:173,233`), nhưng với **thời gian giữ theo dòng dữ liệu** thay cho 3 s cố định: số đo §2.2 cho thấy đóng mất ~6 s mới về 0 ⇒ đề xuất trường `confirmMs` trên `ControlDef` (cốp ~10 s), mặc định = `LampHold.HOLD_MS`. Số đọc **trùng đích ⇒ thả ngay**; hết hạn ⇒ **số đọc thắng** (người lái đóng bằng nút vật lý ⇒ màn hình về "đóng" ở lượt đọc kế).
- Đây cũng là cách SystemUI của BYD làm (`mIgnore` + đọc lại 2 s sau khi thanh chống dừng, `TrunkItem.java:113-146`).
- Độ trễ khi người lái đóng bằng nút: bằng nhịp poll của datum cửa (Kachi đang đọc cửa ~10 s/lần khi có nhu cầu). Nếu owner muốn nhanh hơn: (a) đọc dày (1 s) trong khoảng `confirmMs` sau lệnh và khi ô cốp đang hiện; (b) về sau, một đường listener `registerListener(…, int[]{692060186})` qua `Proxy` (thiết kế có sẵn ở `specs/kachi-live-state-ux.html`) — cơ chế mới, cần qua đủ tầng §14 riêng.
- Chi phí (memory "mỗi bản sửa chứng minh không nặng thêm"): thêm **một** lời gọi getter 1–3 ms vào cùng vòng đọc cửa; không thêm luồng, không thêm shell.

### 4.3 Đảo cốp một nút (OQ14)

- `KeyCtlTargets.actionsFor` đã có luật *"Đảo/Kế tiếp chỉ khi có `readKey`"* (D-KC3) ⇒ khai `readKey` là phím "Mở/đóng Cốp (đảo)" tự xuất hiện, không sửa mã R-KC.
- Lúc bấm: đọc tươi (`CarDataDemand.Holder.withSoloIfIdle`) ⇒ 0 ⇒ gửi 1 (mở) · 1 ⇒ gửi 3 (đóng) · `null`/255 ⇒ `Unreadable`, không bắn. Cổng tốc độ cốp (`CtlSafetyPolicy.REQUIRES_STATIONARY`) giữ nguyên.
- Ca biên: đang hạ (vẫn đọc 1) mà bấm đảo ⇒ gửi đóng lần nữa — vô hại (cùng hướng). Đang nhấc (đọc 1) mà bấm ⇒ gửi đóng giữa hành trình; SystemUI cũng cho làm vậy. cmd 2 (nghi = dừng) vẫn [ĐOÁN], không dùng.
- `VoiceReadback` của cốp chuyển từ *"đã gửi lệnh"* sang *"đã xác nhận"* khi số đọc khớp đích trong `confirmMs`.

## 5. Đã nối (3.1.6 · R32 — chờ xe)

**Quyết định owner 15/10**: *"có nhé, với cả làm luôn rồi lên xe test, không cần phải mò lại chi mất công, tự tin làm thôi"*.
⇒ Owner **miễn bước shell thô của CLAUDE.md §14 tầng 1** (§3 ở trên không chạy riêng). Căn cứ: chính tiến trình BYD (nhắc nhở, SystemUI
`TrunkItem`) đọc `getDoorState(6)` trên xe owner [ĐO logcat 14/10, §2.2] + nguồn fw 2606 [ĐO nguồn, §2.1]. Phần còn [SUY] (K5 nút vật
lý, K6 tiến trình Kachi) chốt bằng bản build kế tiếp trên xe (§5.3). Spec: `specs/kachi-301-cast-steps-dialog-ia.html` R32.

### 5.1 Đã làm (generic, dữ liệu trước)

- **Datum** `tailgate_status` trở lại (`TelemetryRegistry`, cụm A5): getter `BYDAutoBodyworkDevice.getDoorState`, area **6** ở
  `HalReadTables.readArg` (`TRUNK_DOOR_AREA`). Giải mã: `HalReadTables.BOOL_ENUM` 1 ⇒ mở · 0 ⇒ đóng · khác ⇒ `null`; `INVALID_VALUES`
  255 ⇒ `null` cho cả đường `readState` của nút. Chọn datum riêng thay `readKey = "door_rr", readArg = 6`: bảng Cửa/`w_car` cần một ô
  đọc, và mượn `door_rr` làm mọi chỗ tra ô theo `readKey` (3D, bảng vật × việc) đọc nhầm cửa sau-phải. Cùng getter, cùng gateway.
- **Nút** `trunk`: `readKey = "tailgate_status"`, `confirmMs = 10 s` (trường mới `ControlDef.confirmMs`).
- **Nhịp đọc**: cốp chỉ đọc ở nhịp THÂN XE 3 s (`CarDataDemand.BODY_IDS`); ô nút `trunk` lấy luôn số ấy (`CarDataDemand.mirrorsBody`,
  `CarDataAdapter.mirrorBody`) — không đọc ở nhịp nhanh 1 s. Chi phí [ĐO off-car `HalLoadBudgetTest`]: **+20 lượt/phút** khi màn bày cốp
  (xe 3D · bảng Cửa · `w_car` · ô cốp); màn không bày: 0. Không luồng mới, không poll mới.
- **Giữ lạc quan**: `LampHold` hạn theo dòng (`holdMsOf` = `confirmMs`): ô cốp, chấm/nắp 3D cùng một bộ; số đọc trùng đích ⇒ thả sớm;
  hết hạn ⇒ số đọc thắng (ca nút vật lý); cổng tốc độ chặn ⇒ `TileSafetyGate.refuse` bỏ đích giữ.
- **Ô cốp** (`ControlTileFactory.tileCover`): hiện đích giữ ⇒ không thì số đọc; trí nhớ (`ControlLastSent`) theo số đọc ⇒ cú chạm kế đảo
  đúng chiều sau khi đóng bằng nút vật lý. Chưa đọc được ⇒ giữ hình cuối (lối cũ `TileResync`).
- **3D**: điểm `trunk` sang nhóm Cửa (`read = "tailgate_status"`), biến thể nắp đọc `tailgate_status` (cơ chế `cmd:` lệnh-cuối GỠ — chủ duy
  nhất là cốp). Chưa đọc ⇒ vòng đứt (chưa biết), nắp ở bản nghỉ — cùng luật cửa.
- **Phím Đảo (OQ14 · D-KC3)**: đọc tươi 0 ⇒ mở · >0 ⇒ đóng. ~~3.1.6: `null` ⇒ `Outcome.Unreadable`, không bắn~~ — **gỡ ở 3.1.7**
  (hồi quy, §6): `null` ⇒ lệnh cuối Kachi đã gửi như 2.87–3.1.5. Cổng tốc độ không đổi (tầng thi hành `VoiceControlDispatch`/`TileSafetyGate`).
- **Giọng nói**: *"mở cốp"/"đóng cốp"* không đổi đường; `VoiceReadback` đọc lại mỗi 1 s trong `confirmMs` (≤ 10 lượt, luồng nền sẵn có)
  ⇒ khớp ⇒ *"đã xác nhận"*; hết cửa sổ ⇒ ✓ + hedge như cũ (`MOVES_SLOWLY`).
- **Bảng Cửa** (`GroupBoard.DOOR_PARTS` + `CarPart.TAILGATE`), nhóm `g_doors` (*"cửa, cốp, nóc, rèm"*), widget `w_car` (dòng *"Cốp sau
  đang mở / đóng / —"* + chấm đỏ trên hình xe), icon cặp trạng thái `ic_car_top_trunk_open` (sinh bằng `gen-car.py`).
- **Máy ảo**: `DummyCarReceiver` nhại cốp: `--es trunk open|closed|unknown`; lệnh Kachi mở ⇒ đọc 1 sau 0,3 s, đóng ⇒ đọc 0 sau 5,8 s (như §2.2);
  `--ez trunk_only true --es trunk closed` = đóng bằng nút vật lý (chỉ đổi cốp).

### 5.2 Hồi quy khoá bằng test

`KeyCtlPlanTest.R32 dao cop chi theo so doc that…` · `CarDataAdapterTest.R32 nut cop chi doc o nhip than xe…` · `LampHoldTest` (hạn theo
dòng, thả sớm, số đọc thắng sau hạn, `drop`) · `TrunkStateReadTest` (giải mã 0/1/255 qua `HalBindingTable`) · `KeyCtlTargetTest` (cốp
có readKey) · `Car3dBindingTest` (điểm cốp đọc `tailgate_status`) · `HalLoadBudgetTest` (+20/phút ghim).

### 5.3 Kiểm trên xe (bản 3.1.6, đỗ, số P)

```sh
adb -s <xe> logcat -v time | grep -E "KachiHal|getDoorState|KeyCtl|Car3d tap|KachiCtl|0x2940001a|2940001a|Con--TrunkItem"
```

1. Cốp đóng, màn có xe 3D/bảng Cửa: log HAL của Kachi có `getDoorState(6)` mỗi ~3 s ⇒ `0` (chốt K6). Ô cốp tối, nắp đóng.
2. Mở bằng ô cốp: `ctl … id=trunk … args=[1] rc=0` ⇒ trong ≤ 3 s `getDoorState(6)` ⇒ `1`; ô sáng, nắp 3D mở.
3. **Đóng bằng nút vật lý trên cốp** (ca owner): sau khi chốt khoá (~6 s) lượt đọc kế ⇒ `0`; ô + nắp về đóng trong ≤ 3 s (chốt K5).
4. Bấm ô cốp lần nữa ⇒ gửi MỞ (`args=[1]`), không phải ĐÓNG.
5. Phím gán "Đảo cốp": log `KeyCtl ctl:trunk:flip ⇒ Run(… value=1 …, basis=CAR)` khi đóng, `value=0` khi mở. Xe chạy: MỞ bị chặn
   (`KachiCtl … MỞ bị chặn` / câu giọng nói dừng xe) như 3.1.5.
6. Giọng nói *"đóng cốp"*: câu trả lời *"đã xác nhận"* sau ~6 s (không còn hedge).
7. Có `255` ở bước nào ⇒ ô giữ hình cuối, 3D vòng đứt, phím Đảo vẫn chạy theo lệnh cuối (`basis=MEMORY`, 3.1.7 §6) — ghi lại thời điểm.

## 6. Phím Đảo cốp hồi quy ở 3.1.6 (`KEYCTL-TRUNK-TOGGLE-REGRESS`, sửa 3.1.7)

Owner 15/10: *"Lúc trước cái nút đóng mở cốp là gán lên vô lăng một nút, bấm cái nó mở, bấm cái nó tắt. Sao bây giờ nó lại chia ra
thành hai cái … mở xong là mình đâu có đóng lại được"*.

| # | Khẳng định | Mức |
|---|---|---|
| H1 | Phím một-nút có từ **2.87** (R-FL1/R-FL2): mã `ctl:trunk:flip`, quyết bằng **lệnh cuối Kachi đã gửi** (`ControlLastSent`) vì cốp chưa đọc được. Owner gán nó vào `key=306`; log xe 06/10 (2.9x) `ctl:trunk:flip ⇒ Run(value=1 … basis=MEMORY)` rồi `value=0` 16 s sau; 10/10 lặp lại. | [ĐO] log xe + git `f297411a` |
| H2 | Mã đích **không đổi** từ 2.87 tới 3.1.6: `decode("ctl:trunk:flip")` vẫn ra FLIP, không bị đổi thành Mở. Không có mã cũ nào cần migrate (2.86 không có Đảo cốp). | [ĐO] mã + `KeyCtlTargetTest` |
| H3 | Cái đổi là **hành vi**: commit `434a1466` (3.1.6 · R32) thêm `KeyCtlPlan.readOnly` + `Outcome.Unreadable` — cốp có `readKey` mà lượt đọc `getDoorState(6)` không ra số ⇒ **không bắn**, chỉ toast. Đó là luật 2.86 mà 2.87 đã gỡ có chủ ý. | [ĐO] git diff |
| H4 | Trên xe owner, tiến trình Kachi có đọc ra số area 6 không (K6): chưa đo — log 14/10–15/10 không có lần bấm phím cốp nào. Nếu không đọc được thì 3.1.6 từ chối MỌI lần bấm Đảo. | [CHƯA BIẾT] |
| H5 | Hộp chọn xếp Đảo **cuối** (Mở · Đóng · Mở/đóng (đảo)) từ 2.87 ⇒ trông như cốp bị tách làm hai việc. | [ĐO] mã + ảnh owner |

**Sửa (3.1.7, generic — không `if (cốp)`):**
- `KeyCtlPlan`: gỡ `readOnly`/`Unreadable`/`unreadableReply`; mọi nút TOGGLE/COVER/SELECT về một luật: số đọc hợp lệ ⇒ `basis=CAR`;
  không ra số ⇒ lệnh cuối `basis=MEMORY`; trí nhớ ra MỞ mà cổng tốc độ chặn lúc này ⇒ ĐÓNG `basis=MEMORY_SAFE`. Số đọc từ xe = 0 lúc
  xe chạy ⇒ vẫn MỞ và cổng thi hành chặn (đúng sự thật). Nhật ký `KeyCtl … ⇒ Run(… basis=…)` cho biết nguồn trạng thái.
- `KeyCtlTargets.actionsFor`: Đảo **đứng đầu** cho TOGGLE và COVER (cốp: *Mở/đóng Cốp sau (đảo)* · *Mở* · *Đóng*). Chỉ đổi thứ tự hộp
  chọn; mã bền giữ nguyên.
- Gỡ 3 dòng dịch của câu từ chối (zh/th/ms).

**Bài khoá (đỏ với mã 3.1.6, đã chạy):** `KeyCtlPlanTest.dao cop theo so doc that, doc khong ra thi lui tri nho…` (0 ⇒ mở · 1 ⇒ đóng ·
null ⇒ trí nhớ · xe chạy + trí nhớ ⇒ đóng) · `KeyCtlPlanTest.dao hai lan ve nhu cu…` (mọi nút, kể cả cốp thật) ·
`KeyCtlTargetTest.cot co mot phim Dao dung dau…` (Đảo đầu, `ctl:trunk:flip` ⇒ FLIP) · `KeyCtlSafetyTest.dao cop theo so doc that…`
(chạy hết đường thi hành: hai lần bấm khi đọc không ra ⇒ mở rồi đóng; cổng tốc độ y như 3.1.5).

**Kiểm trên xe (3.1.7, đỗ, số P):** gán một phím vào *Mở/đóng Cốp sau (đảo)* (hoặc giữ phím 306 đã gán), bấm 2 lần, logcat lọc `KeyCtl`:
`value=1` rồi `value=0`; ghi `basis=CAR` (⇒ K6 đạt) hay `basis=MEMORY` (⇒ tiến trình Kachi chưa đọc được area 6 — ghi lời đáp `hal`).

## 7. Còn mở

- K5 + K6 chờ bản 3.1.6 trên xe (§5.3 — owner miễn §3 riêng). Chưa đo khi xe khoá cửa / ACC OFF (sau tắt máy Kachi bị BYD giết, nên không áp dụng).
- Lọc 255 cho lượt đọc của nút `trunk` (mượn datum `door_rr`): cần bài kiểm off-car (§4.1).
- ~~Có hiện lại dòng "Cốp" ở bảng Cửa / widget `w_car` không~~ — owner 15/10: có (§5).
- Đường listener: ngoài phạm vi, chỉ làm nếu poll không đủ nhanh.

## 8. Nắp 3D đóng sập sau khi mở trong khi cốp thật đang MỞ (`TRUNK-3D-HOLD-CONTRADICT`, sửa trên 3.1.9)

Owner 16/10 ~10:40 (xe Seal, bản 3.1.9 (214)): *"vừa mở đống cửa, thấy xe 3D hiện nhanh, cốp mở nháy mở xong lại đóng mất, trong
khi thực tế thì đã mở"*. Dữ liệu: logcat xe chụp 10:41:24 (bộ đệm `main` bắt đầu từ 10:40:46.6), tiến trình chính Kachi + tiến trình
helper HAL của Kachi (cùng tập feature-id đăng ký với `KachiHalEvent … đăng ký BYDAutoBodyworkDevice` lúc 10:21:04 và 10:37:05).

### 8.1 Dòng thời gian [ĐO logcat xe 16/10]

Nắp 3D đọc từ dòng `Car3d … dots …` (ghi khi một đợt vẽ dừng): chấm `trunk` đi theo nắp đang vẽ. [ĐO máy ảo emulator-5556, 3.1.9,
mô hình byd-avm-eke, yaw ≈ 170°] nắp đóng ⇒ chấm `y ≈ 499`, nắp mở ⇒ `y ≈ 436`, ảnh chụp hai trạng thái khác nhau rõ; một lần vẽ lại
vì cửa mở KHÔNG dời chấm cốp ⇒ chấm là thước đo trung thực của nắp. Trên xe (yaw 172): đóng `trunk@641,477`, mở `trunk@644,429`.

| Giờ | Nguồn | Sự kiện |
|---|---|---|
| ~10:40:45.0 | `ctl #6` (mất khỏi bộ đệm; `ctl-after #6 after=1` lúc 48.04) | lệnh cốp thứ nhất trong khoảng trống 10:37–10:40:46 |
| 10:40:46.94 | `Car3d dots` | nắp ĐÓNG — trong khi số đọc cốp là 1 (`ctl-after #6 after=1`, `ctl #7 before=1`) |
| 10:40:52.715 | `ctl #7 args=[3] before=1` | phím Đảo ⇒ ĐÓNG; giá đỡ cốp chạy 53.35 (`40100012=2`), chốt khoá 58.60 (`2940001a=0`) |
| 10:41:11.73–11.77 | luồng phím | lượt đọc tươi cho cổng tốc độ (`getCurrentSpeed`, cửa 2/3/4 thật; cốp lấy từ bộ nhớ sự kiện = 0) |
| 10:41:11.772 | `ctl #8 args=[1] before=0` | phím Đảo ⇒ MỞ; `ControlWriteEcho` giữ đích MỞ 10 s |
| ~11.78 → 12.79 | `Car3d đợt vẽ 9 khung` | nắp xoay MỞ (900 ms), dừng ở `trunk@644,429` |
| 10:41:12.049 | helper `postEvent 2940001a = 1` | bus báo cốp MỞ (SystemUI `TrunkItem` + `ReminderService area=6 state=1` cùng ms) |
| 10:41:12.650 | helper `postEvent 4f600010` | bụi mịn trong xe — sự kiện KHÁC cốp (~1 lần/s suốt buổi) |
| ~12.85 → 13.81 | `Car3d đợt vẽ 10 khung` | nắp xoay về ĐÓNG (`trunk@641,477`) — cốp thật đang nâng (`40100012=1` từ 12.33) |
| 10:41:14.776 | `ctl-after #8 after=1` | Kachi đọc cốp MỞ (đường đọc của NÚT, không phải của 3D) |
| 10:41:16.818 | `ctl #9 args=[3] before=1` | owner bấm lần nữa (thấy 3D đóng) ⇒ Đảo theo số xe = ĐÓNG; chốt 22.68 (`2940001a=0`) |

Mỗi sự kiện bụi mịn của helper đi tiếp tới Kachi (`HalEventCache.onEvent` → `CarStatusRepository.onEvents` → +150 ms một lượt
`readEvents`) ⇒ lượt đổ số ~12.80 là lượt đặt nắp về ĐÓNG. Ca 10:40:46.94 cùng hình: nắp đóng trong khi số cốp là 1.

### 8.2 Gốc [ĐO phát lại adapter + bộ nhớ sự kiện THẬT · ĐO mã]

`CarDataAdapter.readSlow` dựng `CarStatus.Body(...)` MỚI và chép tay bốn cửa từ ảnh cũ, nhưng không chép `tailgateOpen` (field thêm ở
3.1.6 · R32, chủ là `readBody`). Hệ quả:

- MỌI lượt chậm (10 s) và MỌI lượt `readEvents` của datum KHÁC cốp (`readEvents` = `readBody(readSlow(readFast(prev)))`; `readBody`
  chỉ đọc lại cốp khi cốp nằm trong lô) xoá `body.tailgateOpen` về `null`;
- nhịp thân xe 3 s NGHỈ vì cốp có sự kiện (`bodyNeeded` — `covered`) ⇒ không ai đọc lại; cốp chỉ hiện đúng trong ~0,6 s giữa sự kiện
  cốp và sự kiện bụi mịn kế;
- nắp 3D + bảng Cửa + widget `w_car` đọc `body.tailgateOpen` ⇒ `null` = "chưa đọc" = nắp ĐÓNG; ô cốp đọc `controls["trunk"]`
  (`mirrorBody` GIỮ số cũ khi `null`) ⇒ vẫn MỞ. Hai bề mặt nói hai điều.

Phát lại (bài `TrunkLidStaleTest`, gateway giả có getter cốp = 0 trước lệnh): đọc tươi `tail=false` → sự kiện `2940001a=1`
`tail=true trunk=1` → sự kiện bụi mịn `tail=null trunk=1`.

**Vì sao không thấy trên máy ảo**: xe giả (`DummyCarReceiver`) đè `tailgateOpen` qua `CarStatusTap` ở MỖI lượt — không đi qua
`CarDataAdapter`. [ĐO máy ảo 16/10, bản 3.1.9 trước sửa] chạm cốp 3D ⇒ `ctl #1 args=[1]`, xe giả nhại MỞ sau 300 ms, nắp mở và đứng yên
(một đợt vẽ duy nhất) ⇒ máy ảo KHÔNG tái hiện được lỗi này; bằng chứng tái hiện là bài phát lại ở `:core`.

### 8.3 Các nghi vấn đã loại

| Nghi vấn | Kết luận | Mức |
|---|---|---|
| Đích giữ (LampHold) trái với sự thật đè số đọc 10 s | Với số đọc HAI mức, đích chỉ sống khi số đọc = ngược đích (khớp ⇒ thả ngay ở lần hỏi đầu); mọi sự kiện mới (= đổi mức) đều thả nó. Ở #8 đích MỞ được thả lúc sự kiện 12.05; nắp đóng ở 12.85 là do số đọc `null`, không do đích | [ĐO mã + bài] |
| Bộ nhớ sự kiện / đối soát 60 s trả 0 cũ | Không có lượt getter cốp thật nào của Kachi trong cửa sổ; bộ nhớ giữ "1" từ 12.049 | [ĐO logcat] |
| Hoạt cảnh nắp đảo chiều khi đích giữ và số đọc xen nhau | Hai đợt vẽ là hai lần xoay trọn (9–10 khung ≈ 900 ms trên xe); lần hai do lượt đổ số `null` | [ĐO logcat + máy ảo] |
| Cửa mở cùng lúc đổ lại số cốp cũ | Không có sự kiện cửa nào trong bộ đệm 10:40:46–10:41:24 | [ĐO logcat] |
| Ca 10:40:46.94 (lệnh #6) | Cùng gốc (bụi mịn ~1 Hz xoá cốp). Lệnh #6 là MỞ hay ĐÓNG thì [CHƯA BIẾT] — dòng `ctl #6` đã trôi khỏi bộ đệm | [SUY] |

### 8.4 Sửa (generic — không `if (cốp)`)

`readSlow`: `body = b.copy(...)` chỉ ghi field CỦA nhịp chậm (kính · rèm · nguồn · loại xe · cửa sổ trời · báo động). Field của nhịp
khác (bốn cửa + cốp) giữ nguyên THEO CẤU TRÚC — field thêm sau này không bị rơi vì một danh sách chép tay. `LampHold` / `Car3dTaps` /
`ControlWriteEcho` KHÔNG đổi: luật *"đích giữ chỉ che lúc chưa có xác nhận"* đã đúng (§8.3) — khoá lại bằng bài.

Chi phí: 0 lượt HAL, 0 luồng, 0 nhịp mới (chỉ đổi phép dựng đối tượng).

### 8.5 Bài khoá — `core/src/test/kotlin/com/byd/clusternav/launcher/TrunkLidStaleTest.kt`

- `luot cham khong xoa field than xe cua nhip khac` — `Body` đủ mọi field, cổng nhu cầu rỗng ⇒ `readSlow` trả NGUYÊN thân xe.
- `xe 16-10 su kien bui min sau khi cop mo khong dong nap 3D, nap va o cung noi MO` — phát lại §8.1 qua adapter + `HalEventCache` +
  `EventCachedGateway` thật; nắp (phép của `Car3dTaps.active`) và ô (phép của `ControlTileFactory.tileCover`) cùng MỞ sau sự kiện bụi
  mịn và sau một lượt chậm.
- `dich giu chi che luc chua xac nhan, su kien moi luon thang` — giữ ĐÓNG rồi bus báo 1 ⇒ MỞ · giữ MỞ, bus 1 ⇒ MỞ, đã thả · giữ ĐÓNG,
  xe không làm theo, không sự kiện ⇒ trong hạn ĐÓNG, hết 10 s số đọc (MỞ) thắng.

Thử ĐỎ [ĐO 16/10]: trả `readSlow` về `CarStatus.Body(...)` dựng mới ⇒ hai bài đầu ĐỎ (`expected <true> but was <null>`); bỏ vế
thả-khi-khớp của `LampHold.held` ⇒ bài ba ĐỎ; bỏ vế hết-hạn ⇒ bài ba ĐỎ.

### 8.6 Kiểm trên xe (bản kế, đỗ, số P)

Bấm phím Đảo cốp một lần (MỞ), chờ ≥ 15 s không bấm gì: nắp 3D phải mở và ĐỨNG mở (logcat `Car3d … dots` sau đợt vẽ đầu giữ
chấm cốp ở vị trí MỞ — yaw 172: `y ≈ 429`, không có đợt vẽ thứ hai đưa về `y ≈ 477`); bảng Cửa hiện "Cốp · Mở"; bấm lần nữa ⇒ ĐÓNG, nắp đóng ngay
(đích giữ), cốp chốt ~6 s sau.
