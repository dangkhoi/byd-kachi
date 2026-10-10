# RE fw 2606 — giới hạn tốc độ THEO BẢN ĐỒ: IVI → ADAS (`0x4B4`) → cụm / HUD · mọi ca, kể cả biển đỏ

> **Trạng thái**: Evidence (RE chỉ đọc) + công cụ test-only [ĐÃ THÊM 2026-10-10] · **Cập nhật**: 2026-10-10 (pass #2 thêm §8–§10: HUD · diff 2602↔2606 · nav app hiện tại · cổng coding · khoanh mâu thuẫn disasm; pass #3 thêm op cầu kiểm thử `setarr`/`setisa` — §6.2, backlog `ISA-MAP-SENDARR`) · **Chủ**: dangkhoi ·
> **Xe đích**: Seal, DiLink3, fw 2606 (= OTA V2.0.4), `sys.car.protocol=CANFD`
> **Liên quan**: backlog `ISA-NATIVE-SIGN` · `docs/diagnostics/oncar-seal-2026-10-10-r18-sla-can.md` (biển xám) ·
> `oncar-seal-2026-10-09-hud-isa-power.md` (đọc ADAS) · `oncar-runbook-2.96.md` §B (lượt ghi 08/10) · `fw-2606-blocker-recheck.md` ·
> `../firmware/fw-2606/RE-2606-findings.md` §1.3.
> **Nguyên liệu** (để ở scratchpad, KHÔNG chép mã BYD/Telenav vào repo; chỉ trích tên + `file:line`):
> `framework.jar` fw 2606 (jadx → `BYDAutoFeatureIds`, `BYDAutoStatisticDevice`, `BYDAutoSettingDevice`, `BYDAutoADASDevice`,
> `AbsBYDAutoDevice`, `BYDAutoDeviceFeaturesMap`) · `framework.jar` fw 2602 (so sánh) · `DiCarServer` 2606 (jadx, lớp `com.byd.car.property.*`) ·
> `TelenavISA.apk` 1.45.1.0103 (`com.telenav.app.isa`, lấy từ ảnh `/system/app` **2602**) · `AutoPermission.apk` 2606 (manifest) ·
> `lib64_libBydDataSource.so` / `lib64_libBydCluster.so` 2606 (llvm-objdump NDK 30) · `meta/getprop.txt` 2606.
> **Mức bằng chứng**: [ĐO] = đọc nguồn/disasm/manifest/log thật · [SUY] = suy luận khớp nguồn, chưa đo trực tiếp ·
> [ĐOÁN] = mới hợp lý · [CHƯA BIẾT] = cần đo. Mọi giá trị "đo xe" dưới đây là trích lại tài liệu đã có, không đo mới.

## 0. Tóm tắt (đọc phần này là đủ ra quyết định)

| # | Kết luận | Mức |
|---|---|---|
| 1 | Đường "giới hạn tốc độ theo bản đồ" CHÍNH THỨC của BYD là **IVI → ECU ADAS qua khung CAN-FD `0x4B4`**. App OEM dùng nó là **TelenavISA** (EU NCAP), gọi `BYDAutoStatisticDevice.sendIsaDataToAdas(int[12])` — **12 ô gửi một lần** | [ĐO TelenavISA `QiankunVehicleManager.java:187-213`, `QiankunISADataConverter.java:554-623`; framework `BYDAutoStatisticDevice.java:610-617`] |
| 2 | Ô giới hạn tốc độ là **mã 5 bit**, KHÔNG phải km/h: `mã = km/h ÷ 5` (≤130), 5/7 km/h → 1, >130 → 27, không giới hạn → 30, **không hợp lệ → 31**, không rõ → 0 | [ĐO `QiankunISADataConverter.java:475-492`] |
| 3 | ⇒ **Các lượt ghi trước đều SAI KHUÔN**: 08/10 ghi `50` thô vào `STATISTICS_ISA_CURRENT_ROAD_SPEED_LIMIT_SET` và `setIsaMap*` (50 km/h đúng phải là **10**); ghi từng ô rời, không kèm quốc gia/đơn vị/trạng thái bản đồ trong cùng lượt. `HalSpeedSignPort` của Kachi cũng ghi km/h thô, một ô. Owner nghi "test trước chưa chuẩn" là **đúng** — lượt đó **không** chứng minh được đường này đóng | [ĐO `isa50*.sh` scratchpad, `scripts/vehicle/isa-speedlimit-2606.sh` W1–W3, `HalSpeedSignPort.kt:45-70`] |
| 4 | Nhưng cổng mạnh nhất KHÔNG nằm ở khuôn: ECU ADAS Seal báo `ADAS_TSR_SPEED_LIMIT_MAP_CONFIG` (`0x4320002A`) = **1**; DiCarServer chỉ coi **2** là "TSR có dùng bản đồ". Ô này nằm trong khung cấu hình trang bị ADAS `0x432` (cạnh `ADAS_HAS2_ACC/AEB/SLA…`), **chỉ đọc** (không có setter) | [ĐO xe 09/10: giá trị 1] · [ĐO nguồn `TsrSpeedLimitMapProperty`, `TsrSpeedLimitMapSwitchProperty` (writeKey null)] · ý nghĩa "ADAS bỏ qua 0x4B4" [SUY mạnh] |
| 5 | **Biển đỏ** (cụm) = dữ liệu ADAS khung `0x2B4` (`updateTrafficSymbol`); **biển xám** = ADAS khung `0x237` bit 46 (`updateSpeedLimitValue`). Tiến trình cụm **không đọc khung `0x4B4`** ở đâu cả ⇒ IVI không có đường thẳng nào tới hai ô này; mọi đường hợp lệ đều phải **qua ECU ADAS hợp nhất** | [ĐO disasm: 0 lệnh `movk #0x4b40, lsl #16` trong `libBydDataSource.so` / `libBydCluster.so`] |
| 6 | **HUD**: không có setter IVI nào cho biển tốc độ HUD; framework không có id `_SET` nào trên khung `0x2B4`/`0x237`. Biển đỏ trên HUD do ECU HUD tự nhận từ bus (nghi `0x2B4`) | [ĐO nguồn: 0 id `_SET`] · nguồn HUD [SUY] |
| 7 | Quyền: `BYDAUTO_STATISTIC_SET` / `SETTING_SET` / `ADAS_SET` đều `signature` (0x2). Trên xe, Kachi (uid thường) đã gọi `setIsaMap*` + `STATISTICS_ISA_*` **rc=0, không SecurityException**; rc=0 chỉ nói HAL nhận, **không** chứng minh khung ra bus | [ĐO manifest `AutoPermission.apk`] · [ĐO xe 08/10] · ra bus [CHƯA BIẾT] |
| 8 | Hai đính chính cho tài liệu cũ: (a) `ADAS_SLA_STATE`=1 nghĩa là **công tắc TSR BẬT**, không phải "fusion mode"; (b) `0x2370002E` và `0x2B400020` **không** có trong bảng feature của framework ⇒ `hal getid` **không bao giờ** đọc được (đúng sentinel 08/10) — cách chốt "đọc định kỳ 0x2370002E" trong tài liệu 10/10 §2.2 không chạy | [ĐO `TrafficSignRecognitionSwitchProperty`, `AbsBYDAutoDevice.java:309-320`, `BYDAutoDeviceFeaturesMap`] |

**Verdict**: đường bản đồ → ADAS là đúng cửa chính thức, và lượt thử 08/10 **chưa hợp lệ** (sai mã + gửi rời). Nhưng
cấu hình ADAS `0x4320002A=1` nói ECU ADAS của Seal **không** được cấu hình trộn giới hạn bản đồ ⇒ kết quả khả dĩ nhất của
một lượt thử đúng khuôn vẫn là "không hiện" [SUY mạnh]. Một lượt thử đúng khuôn (§6) là cách DUY NHẤT để chốt [ĐO]; op mới
`setarr`/`setisa` trong cầu kiểm thử (gọi `sendIsaDataToAdas(int[12])`) **ĐÃ THÊM 2026-10-10** (test-only, §6.2 · backlog
`ISA-MAP-SENDARR`) — buổi xe sau chạy được, chưa chạy trên xe.

---

## 1. Khung `0x4B4` — toàn bộ ô IVI ghi được (fw 2606)

### 1.1 Cách đọc feature-id

- `isCanFD = sys.car.protocol == "CANFD" || persist.sys.protocol.record == "CANFD"` (`BYDAutoFeatureIds.java:4337`) [ĐO];
  Seal 2606 có cả hai = `CANFD` (`meta/getprop.txt`) [ĐO] ⇒ dùng nhánh id `0x4B4xxxxx` (nhánh không-FD là số băm khác).
- `id >> 20` = CAN id; phần thấp = **vị trí bit bắt đầu** trong khung [SUY mạnh]: khoảng cách giữa các id khớp đúng
  miền giá trị TelenavISA gửi — ô giới hạn cách 5 bit ↔ mã tối đa 31; khoảng cách ramp 13 bit ↔ "không hợp lệ" 8191;
  quốc gia 8 bit ↔ 255; trạng thái bản đồ ≤4 bit ↔ 0..6. Độ rộng dưới đây = **cận trên** (khoảng cách tới id kế), không phải DBC.
- Ô xa nhất ở bit 301 ⇒ khung ≥ 39 byte ⇒ khung CAN-FD (48/64 byte) [SUY].

### 1.2 Bảng ô

Nhóm A — `BYDAutoStatisticDevice` (device **1014**, quyền `BYDAUTO_STATISTIC_SET`), CÓ từ 2602 [ĐO so `fw_old`: 17 id, `sendIsaDataToAdas` có sẵn]:

| Thứ tự trong `int[12]` | Tên | Id | Rộng ≤ | Mã hoá (TelenavISA) | Mặc định Telenav |
|---|---|---|---|---|---|
| 0 | `STATISTICS_ISA_CURRENT_ROAD_SPEED_LIMIT_SET` | `0x4B40001C` | 5 | 0 không rõ · `km/h÷5` (≤130) · 5/7→1 · >130→27 · 30 không giới hạn · 31 không hợp lệ | 31 |
| 1 | `…_TIME_CONDITIONAL_SPEED_LIMIT_SET` | `0x4B400021` | 2 | `TimeConditionSign` 0 invalid · 1 true · 2 false | 2 |
| 2 | `…_CURRENT_ROAD_TIME_CONDITIONAL_SPEED_LIMIT_SET` | `0x4B400023` | 2 | 1 khi có giới hạn theo giờ | 2 |
| 3 | `…_WHETHER_FRONT_FORK_RAMP_SET` | `0x4B400025` | 2 | `NextRamp` 0..3 | 0 |
| 4 | `…_FRONT_RAMP_DISTANCE_SET` | `0x4B400027` | 13 | mét tới ramp; 8191 không hợp lệ | 8191 |
| 5 | `…_CURRENT_ROAD_SPEED_LIMIT_TYPE_SET` | `0x4B400034` | 13 | `Speedlimit_Type` 0 invalid · 1 implicit · 2 explicit · 3 road features · 4 local hazards | 0 |
| 6 | `…_RAINY_CONDITION_SPEED_LIMIT_SET` | `0x4B400041` | 5 | `km/h÷5`; 31 invalid; 0 none | 0 |
| 7 | `…_SNOW_CONDITION_SPEED_LIMIT_SET` | `0x4B400046` | 5 | như trên | 0 |
| 8 | `…_ROAD_TYPE_SET` | `0x4B40004B` | 3 | `Roadtype` 0 invalid · 1 cao tốc · 2 trục chính · 3 dân cư · 4 unknown · 5 expressway · 6 vòng xuyến | 0 |
| 9 | `…_COUNTRY_CODE_SET` | `0x4B40004E` | 8 | bảng riêng Telenav (DE 82, FR 74, UK 224…, chỉ châu Âu + IL/TR); 255 invalid. **Không có Việt Nam** | theo máy |
| 10 | `…_ROAD_SPEED_LIMIT_UNIT_SET` | `0x4B400056` | 2 | 1 km/h · 2 mph | theo cụm |
| 11 | `…_MAP_STATUS_SET` | `0x4B400058` | 4 | `MapStatus` 0 invalid · **1 active** · 2 init chưa xong · 3 thiếu dữ liệu · 4 off-road · 5 lỗi khác · 6 reset mạng đường | theo máy |

Nguồn mã hoá: `QiankunISADataConverter.java` (enum ở đầu tệp; `convertSpeedLimit` :475-492; `convertISAData` :554-623, mảng mặc định
`{31,2,2,0,8191,0,0,0,0,country,unit,mapStatus}` :558) [ĐO]. Thứ tự mảng = thứ tự `mapFeature` trong
`BYDAutoStatisticDevice.sendIsaDataToAdas` (`BYDAutoStatisticDevice.java:610-617`) [ĐO]; ghi bằng `set(1014, int[] ids, int[] data)` ⇒
`BYDAutoDeviceManager.setIntArray` — **một** lời gọi binder cho cả 12 ô [ĐO `AbsBYDAutoDevice.java:185-192`]. Map status 2/3/5 ⇒
Telenav vẫn gửi mảng mặc định; 6 ⇒ không gửi [ĐO :561-564]. Nhịp gửi = mỗi lần cây đường (horizon) cập nhật, qua một luồng đơn
`SendIsaDataToAdasPool` [ĐO `QiankunVehicleManager.java:43-48,539-559`]; tần số cụ thể [CHƯA BIẾT].

Cũng ở device 1014 (setter riêng, mỗi cái một ô): `setISATrafficJAMStatus` `0x4B40005C` · `setISAConstructionZonesStatus` `0x4B40005E` ·
`setISAAccidentAheadStatus` `0x4B400060` · `setISAWrongWayDriverStatus` `0x4B400062` (0/1/2) · `setISATrafficSignType` `0x4B400064`
(0..6: 1 vòng xuyến · 2 giao lộ · 3 đèn đỏ · 4 stop · 5 beacon · 6 unknown) (`BYDAutoStatisticDevice.java:448-496`) [ĐO]. Telenav gọi
chúng từ `QiankunENCAPManager` (EU NCAP) [ĐO :785-844].

Nhóm B — **MỚI ở 2606** `BYDAutoSettingDevice` (device **1023**, `getInstance` đòi `BYDAUTO_SETTING_COMMON`, setter đòi
`BYDAUTO_SETTING_SET`, nhận `int`, **không kiểm dải**) (`BYDAutoSettingDevice.java:360-361,676,713,3475-3520`) [ĐO]:

| Setter | Id | Rộng ≤ | Mã hoá |
|---|---|---|---|
| `setIsaMapSpeedLimit` | `0x4B4000AA` | 5 | [SUY mạnh] cùng mã 5 bit như ô 0 (`km/h÷5`) — cùng độ rộng, cùng khung, cùng họ tên; chưa có app OEM nào gọi để chốt |
| `setIsaMapRainSpeedLimit` | `0x4B4000AF` | 5 | [SUY] như trên |
| `setIsaMapSnowSpeedLimit` | `0x4B4000B4` | 5 | [SUY] như trên |
| `setIsaMapTimeSpeedLimit` | `0x4B4000B9` | 5 | [SUY] như trên |
| `setIsaMapSpeedLimitType` | `0x4B4000BE` | 3 | [ĐOÁN] cùng enum `Speedlimit_Type` 0..4, hoặc loại thay đổi; [CHƯA BIẾT] |
| `setIsaMapSpeedLimitCarToPointDistance` | `0x4B4000C1` | 11 | khoảng cách tới điểm đổi giới hạn; đơn vị [ĐOÁN] mét, giá trị "không hợp lệ" [ĐOÁN] 2047 |
| `setIsaMapSpeedLimitCarToEndPointDistance` | `0x4B4000CC` | 11 | như trên (điểm cuối giới hạn có điều kiện) |
| `setIsaMapSpeedLimitCarToStartPointDistance` | `0x4B4000D7` | ≤17 | như trên (điểm đầu) |

Tên tiếng Anh "VALUE_OF_CHANGE_IN_THE_SPEED_LIMIT" + khoảng cách ⇒ đây là **giới hạn SẮP TỚI**, không phải giới hạn đang hiệu lực [SUY].
Kết hợp với đèn xám "dự báo giới hạn tốc độ" (khung ADAS `0x237`) cũng mới ở 2606 ⇒ thiết kế khả dĩ: IVI báo "sắp đổi sang X sau D m" →
ADAS → `0x2370002E` → cụm vẽ biển xám [SUY; chưa có bằng chứng ADAS Seal xử lý nhánh này].

Nhóm C — **MỚI ở 2606** `BYDAutoADASDevice` (device **1038**, quyền `BYDAUTO_ADAS_SET`), **không có setter riêng** ⇒ chỉ ghi được
qua `set(int[] ids, BYDAutoEventValue)` generic (`AbsBYDAutoDevice.java:259-272`) [ĐO]. Các id có trong bảng feature của ADAS
(`BYDAutoDeviceFeaturesMap.java:15`, map 1038 ở :294) [ĐO]:

`ADAS_ANGLE_BETWEEN_TARGET_ROAD_AND_CURRENT_ROAD_SET` `0x4B4000E8`(≤6) · `…CURRENT_ROAD_HAS_RIGHT_OF_WAY_INFO_SET` `0x…EE`(2) ·
`…RELATIONSHIP_BETWEEN_VEHICLE_AND_CURVE_AHEAD_SET` `0x…F0`(2) · `…DISTANCE_BETWEEN_VEHICLE_AND_CURVE_AHEAD_SET` `0x…F2`(6) ·
`…HAS_ROUNDABOUT_AHEAD_SET` `0x…F8`(2) / `…DISTANCE…ROUNDABOUT…` `0x…FA`(6) · `…HAS_INTERSECTION_AHEAD_SET` `0x4B400100`(2) /
`…DISTANCE…NEXT_INTERSECTION…` `0x…102`(6) · `…HAS_TRAFFIC_LIGHT_AHEAD_SET` `0x…108`(2) / `…TRAFFIC_LIGHT…` `0x…10A`(6) ·
`…HAS_STOP_SIGN_AHEAD_SET` `0x…110`(2) / `…STOP_SIGN…` `0x…112`(6) · `…HAS_YIELD_SIGN_AHEAD_SET` `0x…118`(2) / `…YIELD…` `0x…11A`(6) ·
**`ADAS_MAP_MATCH_STATUS_SET` `0x4B400120` (≤7 bit)** · `ADAS_RADIUS_CURVATURE_ROUNDABOUT_SET` `0x…127`(6) · `ADAS_RADIUS_CURVATURE_CURVE_SET` `0x…12D`.

`ADAS_MAP_MATCH_STATUS_SET`: chỉ có hằng + mapper (`Adas.java:811` = `"0x4B400120"`, `AdasMapper.java:480`) [ĐO]; **enum giá trị không có
ở đâu trong kho đã đọc** [CHƯA BIẾT]. Đây là các ô dữ liệu bản đồ cho tính năng giảm tốc theo cua/giao lộ (đi cùng
`RoadFeatureSpeedReductionConfig2Property`, cũng đọc `0x4320002A`==2) [SUY].

### 1.3 Ai gọi các setter này trong firmware

| Nơi | Kết quả | Mức |
|---|---|---|
| TelenavISA 1.45.1 (ảnh 2602) | Gọi `sendIsaDataToAdas` + 5 `setISA*` của device 1014. Không gọi nhóm B/C (2602 chưa có id) | [ĐO] |
| TelenavISA trên 2606 | Thư mục `/system/app/TelenavISA` CÓ trên 2606 (`meta/extra.txt`), nhưng gói **không** nằm trong `pm list packages` user 0; APK 2606 **chưa kéo về** ⇒ có gọi `setIsaMap*` hay không | [ĐO danh sách] · [CHƯA BIẾT] |
| DiCarServer / AppServer / CarSetting / Launcher3 / SpotInspection 2606 | Chỉ có hằng (`Setting.java:1060-1067`, `Statistics.java:56-72`, `Adas.java:811`) + mapper tên; **0 lời gọi** | [ĐO trong kho đã decompile] |
| Kachi | `HalSpeedSignPort` ghi `0x4B40001C` = **km/h thô**, một ô, `setInt` (khi bật đầu ra HUD) | [ĐO `HalSpeedSignPort.kt:45-70`] · có đang chạy trên xe hay không [CHƯA BIẾT] |

## 2. Quyền + ai được ghi

| Kiểm | Kết quả | Mức |
|---|---|---|
| `BYDAUTO_STATISTIC_SET` · `BYDAUTO_SETTING_SET` · `BYDAUTO_ADAS_SET` | `protectionLevel=0x2` (signature) trong `AutoPermission.apk` 2606 | [ĐO manifest] |
| Uid thường (Kachi, chữ ký riêng) | `setIsaMap*` 8 setter + `STATISTICS_ISA_*` qua `setev`: rc=0, không `SecurityException` (08/10); cùng hiện tượng với các miền khác (`byd-hal-permission-RE-2026-09-14.md` §1) | [ĐO xe] · vì sao cổng chữ ký không chặn [CHƯA BIẾT] |
| Uid shell (navopen `systemContext()`) | Phía Java đi qua (10/10, device 1061); `Shell.apk` không xin quyền BYDAUTO nào | [ĐO xe + manifest] · cơ chế [CHƯA BIẾT] |
| Cổng 1 `checkDeviceFeatures` | Id phải thuộc đúng device: nhóm A ở map 1014 (:210/:272), nhóm B ở 1023 (:190/:280), nhóm C ở 1038 (:15/:294) — gọi sai device ⇒ sentinel `-2147482648`, không ném | [ĐO] |
| Cổng 3 native `BYDAutoService::checkSetPermission` | Có symbol; hành vi theo uid | [CHƯA BIẾT] |
| Khung có thật ra bus CAN không | rc=0 chỉ = HAL/autoservice nhận lệnh; MCU có phát `0x4B4` trên cấu hình Seal hay không | [CHƯA BIẾT] |

## 3. Phía tiêu thụ — cụm và HUD

### 3.1 Biển ĐỎ (TSR, ô chính của cụm) — `BusinessUi1::updateTrafficSymbol` @`0x131f40` (`libBydDataSource.so` 2606) [ĐO disasm]

- Đọc `0x2B40000D` (trạng thái TSR), `0x2B400020` (**mã giới hạn**), `0x2B400016` (nhấp nháy/vượt tốc), `0x2B400010`, `0x2B40003D`,
  `0x2B400000` (online), `0x12D0002A` (mức nguồn), `0x1ED00010`.
- Cổng: nguồn == 3 (ON) **và** cờ `+0x2a3` == 1 (= `ADAS_HAS2_SLA` `0x4320000E`, ghi ở `updateSlaFunctionEquipment`) **và** `+0x10cf` == 0;
  sai ⇒ ẩn hết (item 495/496/583 = 0).
- Giá trị: online == 1 và trạng thái 1..3 ⇒ mã `v`: `v==1` ⇒ loại 8 (hết giới hạn); `v ∈ 2..27` ⇒ **km/h = 5v − 5**; `v==28` ⇒ loại 4
  (không có số); `v ∈ 29..31` ⇒ `10v − 150`; ghi item 497 (số) + 583 (loại ảnh) + 496 (1 tĩnh / 5 khi `0x2B400016` ∈ {1,2}).
- ⇒ Mã ra cụm (`5v−5`, 50 km/h = **11**) **khác** mã IVI gửi vào ADAS (`km/h÷5`, 50 km/h = **10**) [ĐO hai phía]. Hai miền mã khác nhau
  ⇒ ADAS là bên chuyển đổi, IVI không viết thẳng vào ô đỏ [SUY mạnh].
- Khung `0x2B4` chỉ có 2 id trong framework (`SETTING_SPEED_LIMIT_CHANGE_SWITCH` `0x2B400033`, `SETTING_FATIGUE_MONITORING_STATUS`), **không
  id `_SET` nào**; `0x2B400020` **không** có trong bảng feature ⇒ IVI không ghi được và cũng **không đọc được** qua HAL [ĐO].

### 3.2 Biển XÁM (dự báo) — `updateSpeedLimitValue` @`0x13254c` [ĐO, đã ghi trong tài liệu 09/10–10/10]

- Đọc `0x2370002E` + online `0x23700000`, cùng cổng nguồn/`ADAS_HAS2_SLA`; `v ∈ 2..30` ⇒ hiện, cùng công thức `5v−5`.
- Khung `0x237` là khung **ADAS** (cùng chứa `ADAS_LDA/ELKA/FCW/AEB_SECONDARY_POP_UP_WINDOW`, `ADAS_CORNERING_SPEED_REDUCTION…`) [ĐO tên id];
  `0x2370002E` **không** có trong framework ⇒ `hal getid` trả sentinel — khớp 08/10 [ĐO nguồn giải thích ĐO xe].
- Bơm TestDevice 10/10 chỉ nuôi datasource cục bộ của cụm ⇒ HUD không thấy (đã ghi 10/10 §2.1) [SUY mạnh].

### 3.3 Cụm có đọc `0x4B4` không

Không: 0 lệnh `movk w?, #0x4b40, lsl #16` trong cả `libBydDataSource.so` (146 461 dòng disasm) lẫn `libBydCluster.so` (131 978 dòng);
không có hằng `0x4B40xxxx` dạng dữ liệu nào trong 4 thư viện `_libs/` [ĐO]. ⇒ `setIsaMap*` / `sendIsaDataToAdas` **chỉ có tác dụng nếu ECU ADAS
xử lý và phát lại** qua `0x2B4`/`0x237` [SUY mạnh].

### 3.4 HUD

- Framework: không setter nào cho biển tốc độ HUD; `sendCameraGuidanceInfo(cameraType 0..19, distance 0..4094, state 1/2)` (nhóm
  INSTRUMENT, cho camera phạt nguội trên cụm/HUD) **không có trường km/h** (`BYDAutoInstrumentDevice.java:1648-1658`) [ĐO].
- Owner: biển đỏ hiện trên **cả cụm và HUD** khi xe tự nhận biển; biển xám bơm 10/10 **chỉ cụm** [ĐO owner].
- ⇒ HUD lấy biển từ bus, nghi khung ADAS `0x2B4` [SUY]; đường IVI → HUD cho biển tốc độ: **không có** trong framework 2606 [ĐO nguồn].

## 4. Cổng cấu hình ADAS — điểm mấu chốt

| Id | Tên | Đọc xe | Ý nghĩa theo DiCarServer 2606 | Mức |
|---|---|---|---|---|
| `0x4320002A` | `ADAS_TSR_SPEED_LIMIT_MAP_CONFIG` | **1** (09/10) | `TsrSpeedLimitMapProperty`: chỉ `2` = TRUE; 0/1/3 = FALSE. `TsrSpeedLimitMapSwitchProperty` cùng id, `writeKey=null` ⇒ **không ghi được**. `RoadFeatureSpeedReductionConfig2Property` cũng = (`==2`) | [ĐO xe] · [ĐO nguồn] |
| `0x4320000E` | `ADAS_HAS2_SLA` | (HAL không đọc được — không thuộc bảng feature nào) | cổng `+0x2a3` của cả biển đỏ lẫn xám; biển đỏ đang hiện ⇒ = 1 | [SUY từ ĐO owner] |
| `0x31600025` | `ADAS_SLA_STATE` | 1 (09/10) | `TrafficSignRecognitionSwitchProperty`: 0 tắt · **1 bật**; `TrafficSignRecognitionFaultProperty`: 2 = lỗi. Các hằng `ADAS_SLA_STATE_FUSION_MODE=1…` trong `BYDAutoADASDevice.java:14-18` là `protected`, **không** dùng để giải mã id này | [ĐO] — đính chính tài liệu 09/10 §1 |

Khung `0x432` gồm `ADAS_HAS2_ACC/AEB/FCW/HMA/LDW/LKS/SLA/TJA`, `ADAS_HAS_LDSW`, `ADAS_SECOND_POPUP_CONFIG` ⇒ là khung **khai báo trang bị**
(coding) của ADAS, không phải trạng thái chạy [SUY mạnh từ tên]. ⇒ Muốn ADAS Seal dùng bản đồ phải đổi coding ECU ADAS (đại lý / công cụ
chẩn đoán) — ngoài tầm IVI và ngoài phạm vi owner cho phép [SUY].

## 5. "Lần test trước có chuẩn không?" — từng lượt

| Lượt | Đã gửi | Lệch so với khuôn chính thức | Kết luận được phép |
|---|---|---|---|
| 15/08 (fw 2602) | `STATISTICS_ISA_CURRENT_ROAD_SPEED_LIMIT_SET` + 33 id, km/h thô | Mã sai (km/h thay vì `÷5`); gửi rời; không map status/country/unit cùng lượt | Không chứng minh được gì về đường bản đồ [SUY] |
| 08/10 W1/W2 (`isa50*.sh`) | ô 0 = **50**; rồi map status = 1 + ô 0 = 50 (2 lời gọi `setev` rời) | 50 không vừa 5 bit (đúng là **10**); không có country/unit; 2 lời gọi rời thay vì 1 `setIntArray` | **Không hợp lệ** [ĐO script] |
| 08/10 W3 + luồng 90 s (`isa50c.sh`) | 8 `setIsaMap*` mỗi 0,5 s: tốc độ/mưa/tuyết/giờ = **50**, loại 1/2/3, khoảng cách 300→0, đầu/cuối | Mã tốc độ sai (đúng là 10 nếu cùng mã); kèm ô 0 = 50 sai mã; loại/khoảng cách chưa biết đơn vị | **Không hợp lệ** cho câu hỏi "IVI có đẩy được không" [ĐO script] |
| 10/10 bơm `0x2370002E` | TestDevice, chỉ cụm | Không phải đường IVI → ADAS; đúng khuôn của ô xám | Chứng minh ô xám vẽ được; nói gì về `0x4B4` thì không [ĐO] |

Bit tràn: nếu MCU cắt theo độ rộng, `50` (0b110010) vào ô 5 bit thành 18 (= 90 km/h theo mã `÷5`) hoặc bị từ chối [ĐOÁN — cách MCU
xử lý giá trị tràn chưa biết]. Dù thế nào, các lượt trên không phải phép đo hợp lệ.

## 6. Kế hoạch chốt (owner quyết; chưa chạy gì)

### 6.1 Chỉ đọc (an toàn, làm trước)

| # | Lệnh | Chốt được |
|---|---|---|
| R1 | `adb -s <vehicle-ip>:5555 shell pm path com.telenav.app.isa` · `dumpsys package com.telenav.app.isa \| grep -E "versionName\|enabled\|installed"` | TelenavISA 2606 có cài/bật cho user 0 không |
| R2 | `adb pull /system/app/TelenavISA/TelenavISA.apk` (đọc) → decompile ngoài xe, tìm `setIsaMap`/`0x4B4000` | Mã hoá **chính thức** của 8 ô nhóm B (thay [SUY]/[ĐOÁN] ở §1.2) |
| R3 | `getprop sys.byd.countrycode` · `getprop sys.byd.boot_business` | Điều kiện Telenav tự chạy (quốc gia, kích hoạt) |
| R4 | `hal getid BYDAutoADASDevice ADAS_TSR_SPEED_LIMIT_MAP_CONFIG` (lặp lại khi xe ON). `ADAS_HAS2_SLA` **không** có trong bảng feature của device nào ⇒ không đọc được qua HAL [ĐO `BYDAutoDeviceFeaturesMap`] | Xác nhận lại cổng §4 trên 2606 |
| R5 | navopen `readcfg` (đọc `/collect2/dataCollect/datacollectioncfg` / `canConfigureJson` qua DiCarServer) | Bố cục bit THẬT + mạng CAN của `0x4B4`, `0x2B4`, `0x237` (thay cận trên §1.2) |

### 6.2 Ghi đúng khuôn (chỉ khi owner duyệt — đỗ P, phanh tay, ACC/ISLC TẮT)

Lưu ý an toàn: `0x4B4` là đầu vào của ECU ADAS; nếu ADAS có xử lý, giới hạn bản đồ có thể ảnh hưởng nhắc/giới hạn tốc độ thông minh
(`ADAS_INTELLIGENT_SPEED_LIMIT_CONTROL*`) [SUY] ⇒ chỉ thử khi đỗ, và luôn có lượt trả.

- **Công cụ [ĐÃ THÊM 2026-10-10 · backlog `ISA-MAP-SENDARR`]**: cầu kiểm thử `hal` (receiver `KachiTestBridge` có trong mọi bản kể cả release — nhưng CHỈ chạy sau công tắc «Chế độ kiểm thử
  qua adb» + cổng `auto_confirm`; 2.99 KHÔNG thêm receiver/op-exported/quyền nào vào manifest) có thêm hai op GHI MẢNG — `setarr` (generic: gọi method nhận `int[]` nào cũng được) và `setisa` (tiện ích cho đúng
  `BYDAutoStatisticDevice.sendIsaDataToAdas(int[12])`, có KIỂM 12 ô). Trước đó `hal` chỉ có `set`/`setev` (int rời / một id) nên
  **không** gọi được `sendIsaDataToAdas(int[])`; các lượt 08/10 vì thế gửi rời + km/h thô ⇒ sai khuôn (§5). Phân tích/mã hoá
  thuần ở `:core` `IsaMapArray` (off-car test); reflection ở `BydHal.callNamedIntArray`. Lượt GHI đi qua cổng `auto_confirm`.
- **Lệnh chính xác (buổi xe — đỗ P, 0 km/h, owner có mặt; qua cầu đã BẬT «Chế độ kiểm thử qua adb»):**
  - Cổng coding (đọc, an toàn — làm trước và sau mỗi lần ghi):
    `… --es cmd hal --es op getid --es dev BYDAutoADASDevice --es m ADAS_TSR_SPEED_LIMIT_MAP_CONFIG` (0x4320002A; `2`=dùng bản đồ).
  - Gửi tải thử T1 đúng khuôn (50 km/h mã `10`, bản đồ active):
    `… --es cmd hal --es op setisa --es data "10,2,2,0,8191,2,31,31,2,255,1,1" --ez auto_confirm true`.
  - Dạng generic tương đương (nếu cần gọi method/đời xe khác):
    `… --es cmd hal --es op setarr --es dev BYDAutoStatisticDevice --es m sendIsaDataToAdas --es data "10,2,2,0,8191,2,31,31,2,255,1,1" --ez auto_confirm true`.
  - Quan sát: biển đỏ / biển xám trên cụm + HUD — **bằng mắt** (IVI không đọc được `0x2B400020`/`0x2370002E`, §3).
  - **Trả (invalid/clear)**: `… --es cmd hal --es op setisa --es data "31,2,2,0,8191,0,0,0,0,255,1,0" --ez auto_confirm true` (map status INVALID=0);
    còn nghi ⇒ tắt máy. Script bọc sẵn cả chuỗi (an toàn gear=P + speed=0 + gõ `ISAMAP` + gửi + đọc gate + trả): `scripts/vehicle/isa-map-sendarr-2606.sh`.
- Mã hoá ô tốc độ = **km/h ÷ 5** (50→`10`; 30=no-limit; 31=invalid). `data` của `setisa` là mảng ĐÃ mã hoá; op chỉ KIỂM vừa độ rộng bit từng ô.
- Đọc kết quả: không hiện gì + gate vẫn `1` ⇒ khớp §4 (ADAS không cấu hình bản đồ) ⇒ đóng hẳn đường IVI; hiện ⇒ đảo kết luận §4, mở spec.

### 6.3 Không làm

- Không bơm khung camera/ADAS (`0x2B4`, `0x237`) lên bus — giả mạo khung an toàn (ràng buộc owner).
- TestDevice `0xAA00020F` chỉ là công cụ test cục bộ của cụm, không đưa vào app (đã chốt 10/10).

## 7. Việc rút ra (ghi cho owner, chưa làm)

- `HalSpeedSignPort` (Kachi) đang ghi km/h thô vào một ô ADAS-input khi bật đầu ra HUD — sai khuôn theo §1.2, và đúng là đường đầu vào
  ADAS ⇒ nên tắt hẳn (hoặc chỉ cho chạy sau khi §6 chứng minh) [ĐO nguồn; quyết định thuộc owner].
- Tài liệu 09/10 §1 ("fusion mode") và 10/10 §2.2 ("đọc định kỳ 0x2370002E qua hal getid") cần đính chính theo §4 / §3.2.

---

## 8. Bổ sung 2026-10-10 (pass RE #2) — lấp 4 khoảng trống (a)–(d)

> Phần này do một lượt RE thứ hai ghi thêm, chỉ đọc. Bổ sung cho §1–§7, KHÔNG ghi đè. Mọi kết luận owner-facing ở §0 vẫn đứng vững
> (xem §9 về vài chỗ hai lượt đọc disasm chưa khớp — không chỗ nào làm đổi verdict).

### 8.a HUD — khung vật lý nào + có đường IVI đẩy biển bản đồ lên HUD không?
- DiCarServer 3.4.0 `CarHudServiceImpl` (`com.byd.car.feature.vision`) **chỉ công tắc/cấu hình**: `setHudAngle/BrightnessLevel/HeightLevel/Mode/Theme/SwitchEnabled`, `setDrivingFusionEnabled`, `setDynamicNavigationEnabled`, `setNavigationFusionEnabled`, `setNavigationMapEnabled`, `setSafeDrivingEnabled`, `setSteeringWheelControlAdjustmentEnabled`. **0 method nhận km/h / biển / trafficSign / nội dung** [ĐO `CarHudServiceImpl.java:65-155`]. ⇒ không có API đẩy nội dung biển tốc độ lên HUD.
- `setNavigationMapEnabled` → ghi `0x32B1102E` = bản đồ dẫn đường **AR-HUD**; xe owner là **W-HUD**, id này đọc `NOT_PROVISIONED` [ĐO xe 09/10] ⇒ không phải cổng biển tốc độ của W-HUD (khớp RE-2606 §3.2).
- framework 2606: không id `_SET` nào trên `0x2B4`/`0x237`; `INSTRUMENT_HUD_*` chỉ là hằng bảng SDK CarSetting, **không có field framework** ⇒ không phân giải trên DiLink3 [ĐO RE-2606 §3.1]. SystemUI: không có tham chiếu `speedLimit`/`HudService`/forwarder [ĐO grep `sui/`].
- ⇒ **KHÔNG có đường IVI (DiCarServer / framework / SystemUI) đẩy biển tốc độ theo bản đồ lên HUD trên 2606** [ĐO nguồn]. HUD hiện biển ĐỎ là do **ECU HUD tự đọc đầu ra TSR của ECU ADAS trên bus vật lý** [SUY mạnh — owner thấy đỏ trên CẢ cụm+HUD cùng lúc]. Khung vật lý chính xác ECU HUD đọc = **[CHƯA BIẾT]** (cần sniff bus; framework không lộ; nghi cùng họ `0x2B4`).
- Hệ quả: đường DUY NHẤT để biển-bản-đồ lên HUD là làm **ECU ADAS phát giới hạn hợp-nhất-bản-đồ ra đầu ra TSR của nó** ⇒ lại quay về cổng `0x4320002A=2` (§4, §8.d). Bơm thẳng khung lên bus vật lý = **giả mạo ID của ECU ADAS** (xung đột arbitration, DTC, an toàn) — không làm.
- Chốt rẻ nhất (chỉ đọc): khi chạy qua biển THẬT, owner nhìn HUD có hiện biển không trong khi `0x4320002A` vẫn =1 — nếu CÓ ⇒ HUD đỏ độc lập bản đồ (thuần camera), khớp §8.a. Đọc id/network của khung HUD cần `navopen readcfg` (R5).

### 8.b 2602 → 2606: đường biển-theo-bản-đồ là MỚI hoàn toàn (đo trực tiếp QML + framework)
| Kiểm | 2602 | 2606 | Mức |
|---|---|---|---|
| `WarinLightLayout.qml` theme1 chứa `speedLimitStatus/Value/speed_limit_forecast` | **0** | **5** (+ ảnh `speed_limit_forecast.png`) | [ĐO giải rcc `old/lib64/cluster_theme1.rcc` + grep] |
| Biển ĐỎ TSR (`trafic_border`/`limitTrafficSignRecognition`/`trafficSignType`) | **9** (đã có) | có | [ĐO] |
| `SETTING_ISA_MAP_*` + `setIsaMap*` trong `framework.jar` | **0** | 8 id + 8 method | [ĐO grep `fw_old` vs `fw_new`] |
| `ADAS_MAP_MATCH_STATUS_SET` / `ADAS_*_AHEAD_SET` / `ADAS_TSR_SPEED_LIMIT_MAP_CONFIG` | **đã có** | có | [ĐO grep: có ở old] |
⇒ **Lớp "dự báo giới hạn theo bản đồ" (widget xám QML + 8 `setIsaMap*` trên `0x4B4` + tiêu thụ khung `0x237`) là MỚI ở 2606.** Biển ĐỎ camera (`0x2B4`) và các id ADAS map-ahead/TSR-config **đã có từ 2602** — chỉ là cụm 2602 chưa có widget xám để hiển thị. Nhưng ECU ADAS Seal vẫn `0x4320002A=1` ⇒ lớp mới **chưa được bật trên xe owner**.

### 8.c Nav app hiện cài được có tự bật biển gốc trên cụm không? → KHÔNG (không có bản tham chiếu đang chạy)
- **AMap/AutoNavi** (`jadx-amap*`): chỉ dùng **`BYDAutoInstrumentDevice`** kênh dẫn đường (`INSTRUMENT_GUIDE_INFO_SIMPLE_SET 0x43F…`, next-pathname, crossing-distance, trip/arrival) — **không** `setIsaMap`, không ADAS, không id biển tốc độ, không `0x4B4` [ĐO `AmapService.java`].
- **TMap** (`jadx-tmap`): có class SDK BYD (stub) nhưng app chỉ đọc tốc độ xe (`BYDAutoSpeedDevice`); biển tốc độ là overlay SDI **tự vẽ**; 0 caller cho ADAS/ISA/SLA [ĐO].
- **VietMap mod cluster v2/v3** (`apks/VietMap-3.4.x-mod-cluster-v2.apk`): **0 token** bydauto/ADAS/ISA trong dex; tự vẽ badge + broadcast `com.byd.clusternav.VM_BUBBLE_POS/VIS` sang app ClusterNav [ĐO dex].
- ⇒ Trên BYD, nav app hiện nay lên cụm **chỉ qua kênh INSTRUMENT (mũi tên/tên đường)** hoặc **tự vẽ overlay**; **không** app nào bật biển tốc độ gốc. Driver chính thức của `0x4B4`/`sendIsaDataToAdas` là **TelenavISA** (EU) — **không** có trong danh sách gói user-0 của 2606 Seal (lọc vùng) [ĐO RE-2606 §1 + survey]. ⇒ **không có bản tham chiếu đang chạy để sao chép; mọi hướng là greenfield.**

### 8.d Bật `0x4320002A=2` mà KHÔNG cần công cụ chẩn đoán? → KHÔNG (coding-only). Fusion KHÔNG bỏ qua cổng.
Hội tụ 4 chân, đều [ĐO nguồn]:
1. `BYDAutoADASDevice` 2606: **không** method set cho `ADAS_TSR_SPEED_LIMIT_MAP_CONFIG` (chỉ có `setSLAState` nhận 0/1, `setAVHState`…).
2. DiCarServer `TsrSpeedLimitMapSwitchProperty` = `StableBiMapProperty(ctx, CONFIG, **writeKey=null**)` ⇒ `setValue` không có đích ghi; `SafetyAssistServiceImpl` chỉ phơi **`isTsrSpeedLimitMapEnabled()`** + `isISAMapVersionCodeEnabled()` (**đọc**), **KHÔNG** `setTsrSpeedLimitMapEnabled` (chỉ có `setIntelligentSpeedLimitRemindEnabled` cho id khác) [ĐO `SafetyAssistServiceImpl.java:155-156,235`; `TsrSpeedLimitMapSwitchProperty.java:5-6`].
3. CarSetting / AppServer `carsettings`: **0** toggle UI TsrSpeedLimitMap/ISA-map [ĐO grep].
4. Khung `0x432` = khung khai báo trang bị ADAS (coding), không phải state (§4).
⇒ `0x4320002A=2` chỉ đặt được bằng **coding ECU ADAS** (UDS WriteDataByIdentifier qua tool: VDS2100/Autel; dịch vụ on-device `diagmgrsrv` khoá sau `android.uid.system`+SecurityAccess — RE-2606 §3.4). Ngoài tầm app/shell, cần quyết định owner + tiệm.
- **Fusion có bỏ qua cổng không? KHÔNG.** `ADAS_SLA_STATE=1` chỉ là "công tắc TSR BẬT" (đính chính §4). `TsrSpeedLimitMapProperty` coi `0x4320002A` ∈ {0,1,3} = FALSE bất kể SLA ⇒ **không chế độ nào nhận CẢ camera lẫn bản đồ khi config≠2** [ĐO `TsrSpeedLimitMapProperty.java:10-17`].
- Chốt rẻ nhất (chỉ đọc): R4 (§6.1) đọc lại `0x4320002A` khi ON. Nếu owner muốn chốt coding-lock (đỗ P + duyệt): `hal setev BYDAutoADASDevice 0x4320002A 2` rồi đọc lại — về 1/sentinel ⇒ xác nhận coding-only; =2 và GIỮ ⇒ đảo kết luận (mở spec). Là ghi config (không phải lệnh động) nên rủi ro thấp, nhưng vẫn là "ghi ADAS" ⇒ cần owner.

## 9. Hai lượt disasm chưa khớp — khoanh vùng, KHÔNG chỗ nào đổi verdict

Pass RE #2 (đọc `ds.asm`/`bc.asm` độc lập) lệch với §3 ở vài địa chỉ/item; ghi lại để lượt sau chốt, **không nhận cái nào là chân lý** khi chưa khớp với đo xe:

| Điểm | §3 (pass #1) | Pass #2 | Trọng tài |
|---|---|---|---|
| Hàm biển đỏ | `updateTrafficSymbol`@`0x131f40`, item 495/497/583 | `BusinessUi1::updateTrafficSymbol`@`0x130628`, ghi item **574=type / 488=số / 487=hiện / 486=raw**; có routine demo @`0x139b04` ép đỏ "60" | Cả hai đồng ý: biển đỏ = khung **0x2B4**, số = `(mã−1)*5` (≈`5v−5`), cổng vào `0x12D0002A==3`+cờ nội bộ; **chỉ lệch số hiệu hàm/item** ⇒ không đổi kết luận "đỏ = camera, không trộn bản đồ". Chốt bằng đọc lại 1 lần nếu cần địa chỉ chính xác |
| Biển xám (0x237) | `updateSpeedLimitValue`@`0x13254c` đọc `0x2370002E`→km/h, item 587/588 | **Không thấy** symbol đó (0x13254c = `getRadarDisplay`); cho rằng widget xám là **dead code** (thuộc tính `speedLimitStatus/Value` không có trong DataSourceManager) | ⚠ **[MÂU THUẪN với ĐO xe 10/10]**: bơm `0x2370002E`=11 qua TestDevice **đã hiện biển XÁM "50"** trên cụm [ĐO owner]. Đo xe > suy luận nguồn (CLAUDE.md §15) ⇒ **biển xám KHÔNG dead**, có bên đọc `0x2370002E` và map 11→50. Số hiệu hàm/đường item do pass #2 chưa truy được bảng dịch item→property (chính nó ghi [CHƯA BIẾT]). **Verdict không đổi**: biển xám là cục bộ cụm, không ra HUD, và không chứng minh được đường bản đồ (vẫn kẹt cổng §4) |

**Cách chốt mâu thuẫn (chỉ đọc, rẻ):** đọc lại `ds.asm` quanh người ĐỌC `0x2370002E` (grep `movk .*0x2370` rồi lần `GetData_UINT8`), đối chiếu item ghi ra với `bc.asm handleDataItemChanged`. Không cần xe. Dù kết quả thế nào, **đo xe 10/10 đã chốt biển xám vẽ được**; tranh luận chỉ là về symbol, không về khả năng.

## 10. Verdict cuối cho owner (gộp §0 + §8 + §9)

- **Biển XÁM (dự báo) trên cụm**: vẽ được bằng bơm cục bộ (TestDevice) — đã chứng minh; nhưng đó là **sim cục bộ của cụm**, KHÔNG ra bus, KHÔNG lên HUD, KHÔNG phải API sản phẩm. Đường bản-đồ-thật (`0x4B4`→ADAS→`0x237`) **kẹt cổng** `0x4320002A=1` (coding).
- **Biển ĐỎ (TSR) trên cụm**: thuần **camera** (khung `0x2B4`), không có đường trộn bản đồ; chỉ "ép" được bằng bơm khung camera giả (owner cấm) — và vẫn chỉ cục bộ cụm, không HUD. Lượt test đỏ trước (nếu nhắm `0x237`) **sai khung** ⇒ owner nghi "chưa chuẩn" là đúng, nhưng chuẩn lại vẫn **không mở** được đường bản-đồ-→-đỏ.
- **HUD**: **không** có bất kỳ đường IVI nào (DiCarServer/framework/SystemUI) đẩy biển tốc độ (bản đồ hay không) lên HUD trên 2606. HUD lấy biển từ ECU ADAS qua bus vật lý. Muốn biển-bản-đồ lên HUD ⇒ phải coding ADAS `0x4320002A=2` (tool dealer), rồi mới có cửa — và vẫn cần ECU ADAS Seal thực sự forward map→TSR (chưa chắc, [CHƯA BIẾT]).
- **Có "cửa" không?** Có đúng MỘT cửa nhất quán cho cả 3 ca: **coding ECU ADAS `0x4320002A`=2** (ngoài app). Trước khi bỏ tiền coding, làm R1–R5 (chỉ đọc) + (nếu owner duyệt, đỗ P) một lượt GHI đúng khuôn `sendIsaDataToAdas(int[12])` mã `km/h÷5` để loại trừ khả năng "đúng khuôn thì lên" — op `setarr`/`setisa` trong cầu kiểm thử **ĐÃ THÊM** (test-only, §6.2).

## 11. Đường PHẦN MỀM mở cổng map-ISA (phản biện owner 10/10)

> **Bối cảnh phản biện (owner, 10/10, hợp lệ)**: fw 2606 (= OTA V2.0.4) tới xe **thuần qua OTA phần mềm**; tính năng giới hạn
> tốc-độ-theo-bản-đồ là **MỚI ở 2606** ⇒ kết luận "phải có tool chẩn đoán/coding" ở §4/§8.d/§10 là **vội**, coi như **giả thuyết
> CHƯA chứng minh**. Mục này đi tìm MỌI đường phần mềm làm xe nhận giới hạn theo bản đồ (đặt cổng `ADAS_TSR_SPEED_LIMIT_MAP_CONFIG`
> `0x4320002A` = 2, hoặc tương đương), và chỉ giữ "coding tool" làm phương án CUỐI nếu mọi đường phần mềm bị bác bằng nguồn.
> Lượt này **chỉ đọc nguồn** (framework/DiCarServer/CarSetting/AppServer/Launcher3/SpotInspection/TrafficMonitor/AmapService/TMap/
> TelenavISA + disasm `libbydauto*`), **chưa đo xe**. Theo CLAUDE.md §14: mọi kết luận "được/không" về cổng ECU **phải đo trên xe mới
> lên [ĐO]**; ở đây tối đa là [SUY]/[ĐOÁN].

### 11.0 Trước hết — tách rõ "OTA mang CODE" khác "phần mềm lật cổng" (sửa tiền đề)

- **Cái gì MỚI ở 2606 [ĐO nguồn]**: (a) 16 `setIsaMap*` trong `BYDAutoSettingDevice` (2602 = **0**); (b) 8 `SETTING_ISA_MAP_*` + 17
  `ADAS_*_AHEAD/MAP_MATCH_SET` trong `BYDAutoFeatureIds`; (c) widget "dự báo giới hạn" QML (`speedLimitStatus/Value`, 2602 = 0).
  ⇒ OTA 2.0.4 **đã ship đường GỬI (IVI→ADAS, khung `0x4B4`) + đường HIỂN THỊ (cụm)**.
- **Cái gì KHÔNG mới [ĐO nguồn]**: cổng `ADAS_TSR_SPEED_LIMIT_MAP_CONFIG` (`0x4320002A`) + các id ADAS map-ahead **đã có trong
  framework 2602** (`grep` fw_old: có). ⇒ Cổng là biến **phía ECU ADAS**, không phải thứ OTA phần mềm vừa thêm.
- **Hệ quả**: owner đúng rằng phần GỬI/HIỂN THỊ là phần mềm mới tới qua OTA. Nhưng điều đó **không tự chứng minh** rằng giá trị
  cổng (ECU đọc 1) do một value phần mềm quyết định. Hai việc khác nhau: *ship code* ≠ *lật byte coding của biến thể xe*. Dưới đây
  xếp hạng từng đường phần mềm có thể lật cổng.

### 11.1 Bảng xếp hạng đường PHẦN MỀM (cao = còn mở + rẻ nhất để chốt)

| # | Đường phần mềm | Mức | Vì sao |
|---|---|---|---|
| **S1** | **Ghi HAL generic `set(1038, 0x4320002A, 2)`** qua `BYDAutoADASDevice` (cầu Kachi `setisa`/`setev`, hoặc navopen) | **[SUY] CÒN MỞ — chưa ai bác được bằng nguồn** | Tầng Java **sẽ dispatch**: `checkDeviceFeatures` chỉ kiểm id có thuộc device 1038 không — **gate id CÓ trong `AdasMap`** (`BYDAutoDeviceFeaturesMap.java:15`), **không có cờ hướng/ghi** ⇒ qua luôn xuống `BYDAutoDeviceManager.setInt`→native. "Không có setter Java tên riêng" **KHÁC** "ECU từ chối ghi". ECU nhận hay không = **[CHƯA BIẾT]**, chỉ **ghi+đọc-lại trên xe** mới chốt |
| **S2** | **Cổng do ECU suy từ region/country** (một value phần mềm đặt lúc EOL/boot) | [ĐOÁN] — chưa thấy đường | `sys.byd.countrycode=84` (VN), `ro.build.region=ROW` [ĐO getprop]. **Không có** mã nào ghi `0x4320002A` hay suy nó từ countrycode (`grep` toàn bộ decompiled: 0). Nếu cổng là byte coding **gán theo quốc gia lúc sản xuất**, thì "phần mềm" đặt nó = coding, không phải prop runtime. Chốt: so `0x4320002A` giữa Seal EU (kỳ vọng 2) và VN |
| **S3** | **TelenavISA side-load** tự lật cổng / tự đẩy `0x4B4` | [ĐO nguồn] — **không lật cổng**, nhưng là bên gửi đúng | Telenav **không hề đọc/ghi** `0x4320002A` (`grep` telenav: 0) — nó gửi `sendIsaDataToAdas(0x4B4)` **vô điều kiện**, không quan tâm cổng. ⇒ Side-load Telenav **đẩy được dữ liệu map tới ECU ADAS** nhưng **không mở cổng**; ECU vẫn không trộn khi config≠2. Chỉ hữu ích **sau khi** cổng mở |
| **S4** | **Switch DiCarServer** `TsrSpeedLimitMapSwitchModel.setEnabled(true)` | [ĐO nguồn] — **bác** | Model có thật và `setEnabled→property.setValue(TRUE)→toUnderlying=2`, nhưng (a) `ISafetyAssistService` **KHÔNG phơi** `setTsrSpeedLimitMapEnabled` (chỉ `isTsrSpeedLimitMapEnabled()` đọc + `is…`; setter duy nhất là `setIntelligentSpeedLimitRemindEnabled`/`setSpeedLimitChangeSoundRemindEnabled`) ⇒ **không có cửa IPC** gọi tới; (b) `TsrSpeedLimitMapSwitchProperty` dựng với **writeKey=null** ⇒ `VehicleProperty.set()` gọi `setProperties([CarPropertyValue(null, 2)])` — **ghi vào key rỗng**, datasource không định tuyến được [ĐO `VehicleProperty.java` set(); `TsrSpeedLimitMapSwitchProperty.java:5`; `SafetyAssistServiceImpl.java:105,155`] |
| **S5** | **Công tắc CarSetting** (trang "TSR") | [ĐO nguồn] — **bác** | `TSRCellular` **chỉ hiện khi `isKey2Visible(config)` = (config==2)** (`BydAutoADASDeviceUtil.isKey2Visible:26`); Seal này config=1 ⇒ **trang ẩn**. Và kể cả khi hiện, `setState2Hal` **không ghi ADAS** — nó gọi `TrafficMonitor.setRestrictByUser("com.telenav.app.isa", …)` = **bật/tắt quyền data cho app Telenav** (`IAppTrafficInterface`), **không** chạm cổng [ĐO `TSRCellular.java`] |
| **S6** | **ISA map data / ISAMapVersionCode** (cài gói bản đồ ISA) | [ĐO nguồn] — **bác** | `isISAMapVersionCodeEnabled()` đọc **CÙNG** id `0x4320002A` (qua `TsrSpeedLimitMapSwitchProperty`) ⇒ "có dữ liệu map" **không** đổi cổng; cổng vẫn là coding ECU [ĐO `ISAMapVersionCodeSwitchModel.java`] |
| **S7** | **Menu factory / engineering / secret-code** đặt ISA/TSR-map | [ĐO nguồn] — **không có** đường app thường | Không có `EngineerMode`/`*#*#`/secret-code nào bật TSR-map trong CarSetting/AppServer. `SpotInspection` (EOL) có engine UDS **nhưng khoá sau `diagmgrsrv` + SecurityAccess** (bí mật `keyK`/DID chưa có — RE-2606 §3.4) ⇒ đây **chính là coding** nhưng dạng on-device, vẫn cần khoá |
| **S8** | **Coding ECU ADAS bằng tool** (VDS2100/Autel/XTOOL, UDS WriteDataByIdentifier) | [SUY] — phương án CUỐI, chỉ khi S1/S2 bị bác trên xe | Khung `0x432` = khung khai báo trang bị ADAS (coding); không có `_SET` id nào cho **bất kỳ** trường `0x432xxxx` trong framework (`grep 0x4320….*SET` = 0) ⇒ không có đường ghi "sản phẩm". Nhưng **chỉ được kết luận coding-only SAU KHI S1 bị bác bằng ghi+đọc-lại trên xe** |

### 11.2 Vì sao S1 là mấu chốt (và vì sao disasm không chốt được)

- `AbsBYDAutoDevice.checkDeviceFeatures` chỉ kiểm **thành viên tập id của device**, **không** có bảng hướng (TX/RX) hay cờ ghi ở
  tầng Java [ĐO `AbsBYDAutoDevice.java:390-404`]. Gate id ∈ `AdasMap` ⇒ lệnh ghi generic **không bị chặn ở Java**.
- Native `libbydautoservice.so`/`libbydauto.so`: **0** tham chiếu literal `0x4320` [ĐO disasm] — vì autoservice **nạp bố cục khung +
  hướng tín hiệu từ cấu hình CAN runtime** (`canConfigureJson`/`datacollectioncfg`), **không** hardcode trong `.so`. ⇒ **Không thể**
  biết tĩnh (từ `.so`) là `0x432` có cho IVI ghi (TX) hay chỉ nhận (RX). Writability nằm ở **file cấu hình CAN trên xe**.
- ⇒ Hai cách chốt S1, đều rẻ:
  - **(đọc) R5′**: navopen `readcfg` đọc `canConfigureJson`/`datacollectioncfg`, tìm dòng khung `0x432` / signal `TSR_SPEED_LIMIT_MAP`
    → xem **hướng** (nếu RX-only từ phía IVI ⇒ phần mềm **không** ghi ra bus được ⇒ loại S1 **không cần ghi thử**).
  - **(ghi, cần owner + đỗ P)**: `getid 0x4320002A` (kỳ vọng 1) → `setev BYDAutoADASDevice 0x4320002A 2` → **đọc lại ngay** và sau khi
    tắt-mở máy. **Giữ 2** ⇒ **phần mềm MỞ được cổng → đảo kết luận §4/§8.d/§10, mở spec** (rồi mới tới S3: side-load/đẩy `0x4B4`
    đúng khuôn `km/h÷5`). **Về 1 / sentinel** ⇒ ECU từ chối ghi runtime ⇒ **lúc đó** mới xác nhận coding-only (S8).

### 11.3 Chốt owner (§11)

- **Không giữ "coding tool" làm kết luận nữa** — hạ xuống **phương án cuối (S8)**, chỉ đúng **nếu** S1 bị bác bằng ghi+đọc-lại trên xe.
- **Đường phần mềm CÒN MỞ thực sự = đúng MỘT (S1)**: ghi HAL generic `0x4320002A=2`. Mọi đường "công tắc/menu/region/Telenav/map-data"
  (S2–S7) đều đã **bác bằng nguồn** là **không lật được cổng** (trừ S2 cần một phép so EU-vs-VN để loại hẳn giả thuyết region-coded).
- **Rẻ nhất, làm trước (chỉ đọc)**: R4 (đọc lại `0x4320002A` khi ON) + **R5′** (đọc cấu hình CAN xem `0x432` có hướng ghi). Hai cái này
  **không ghi gì**, đủ để quyết có đáng làm bước ghi thử S1 hay không.
- **Bước ghi thử S1** (đỗ P, phanh tay, ACC/ISLC TẮT, có lượt trả về 1): là **ghi config 1 giá trị**, rủi ro thấp hơn bơm khung động,
  nhưng vẫn là "ghi ADAS" ⇒ **cần owner duyệt**. Đây là phép đo DUY NHẤT phân định dứt khoát "phần mềm mở được" vs "coding-only".
- Nhắc lại ranh giới bằng chứng: toàn §11 là **[SUY]/[ĐOÁN] từ nguồn**; "phần mềm mở được cổng hay không" chỉ thành **[ĐO]** sau
  ghi+đọc-lại trên xe (CLAUDE.md §14).
