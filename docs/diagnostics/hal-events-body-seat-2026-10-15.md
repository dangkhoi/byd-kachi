# Sự kiện HAL thay poll: cửa, cốp, ghế và mọi datum đang poll — 2026-10-15

> **Trạng thái**: Current · 2026-10-15 · backlog `HAL-EVENTS-GENERIC` (gộp `DOOR-EVENT-LISTENER`, `DRL-EVENT-CALLBACK`), nối `SEAT-AUTO-OFF-REAPPLY`.
> **Yêu cầu owner 15/10**: *"nó có event ko, hay chỉ pull status interval, cần phải RE việc lấy event và đưa vào app hơn là cứ pull,
> càng pull càng hao"* · rồi *"hàng trăm chip, datum nữa nhé … toàn bộ các kéo interval thì xem có event push không"*.
> **Phạm vi**: RE off-car (fw 2606 jadx) + cơ chế chung đã code + test off-car. Owner cho làm thẳng rồi lên xe kiểm
> (*"làm luôn rồi lên xe test"*), nên chuyến xe kế tiếp là phép đo §14 tầng 1. Mọi khẳng định "trên xe" bên dưới vẫn là [CHƯA ĐO]
> cho tới khi có log §8.

## 0. Kết luận ngắn

| # | Khẳng định | Mức |
|---|---|---|
| E1 | Mọi device BYD đẩy sự kiện qua MỘT cơ chế chung: `AbsBYDAutoDevice.onPostEvent` gọi `onDataChanged(IBYDAutoEvent)` (rồi `onDataEventChanged(id, value)`) cho các listener đã xin đúng feature-id ấy. Không cần listener riêng cho từng loại device. | [ĐO nguồn] |
| E2 | `AbsBYDAutoDevice.registerListener(IBYDAutoListener, int[])` là phương thức công khai của lớp cha và **không kiểm quyền Java**. Các overload theo device (`registerListener(AbsBYDAutoBodyworkListener, int[])`…) chỉ thêm `enforceCallingOrSelfPermission(<X>_GET)`. Helper đã bọc Context (`PermissiveContext`) nên cả hai đều qua. | [ĐO nguồn] |
| E3 | Tầng native (`BYDAutoManager.nativeEnableDevice(type, ids)`) có cho tiến trình uid shell nhận sự kiện của Bodywork/Ac/Setting… không: nhiều khả năng có, vì listener đèn của chính helper đi đúng đường này và **đã chạy trên xe** (log chuyến 14–15/10). | [SUY mạnh] — chốt bằng §8 |
| E4 | 73/80 datum đang poll có getter **thuần** (`return super.get(type, HẰNG)`) hoặc là feature-id thô ⇒ giá trị sự kiện thay được lượt đọc. Còn lại giữ poll: tốc độ/số/công suất (datum nhanh), odo (getter nhân 0,1), VIN/loại xe, âm lượng Android. | [ĐO nguồn] cho bảng · [SUY] cho từng id có thật sự được đẩy |
| E5 | Có bằng chứng xe NÀY đẩy: bốn cửa + cốp (`0x2940001A`, AVM đăng ký đúng bộ id, SystemUI nhận sự kiện), mát ghế lái (`0x4FA00008`), DRL (`0x3AC00024` tới chính helper). | [ĐO log] (trunk-state-read §2.2 · seat-vent §12.2 · oncar-bugs-2026-10-15 §2) |
| E6 | Dấu vân tay ghế `0x0950000F`/`0x09500022` thuộc bảng feature của device 1023 Setting ⇒ helper nghe được bằng cùng listener chung. | [ĐO nguồn] · nhận thật trên xe [CHƯA ĐO] |

## 1. RE: cơ chế listener (fw 2606, `framework.jar` jadx)

- `android/hardware/IBYDAutoListener.java`: đúng ba phương thức `onDataChanged(IBYDAutoEvent)`, `onDataEventChanged(int, BYDAutoEventValue)`, `onError(int, String)`.
- `bydauto/AbsBYDAutoDevice.java`
  - `:43-56` `checkDeviceFeatures(type, ids)`: một id không thuộc `BYDAutoDeviceFeaturesMap.getFeatureIdsFromDevice(type)` ⇒ log *"You have no permission to use the feature"* và **bỏ cả lượt đăng ký**.
  - `:221-236` `onPostEvent`: với mỗi listener trong `getIdListeners(eventType)` gọi `onDataChanged(event)` rồi `postEventValue` ⇒ `onDataEventChanged(eventType, value)`.
  - `:276-292` `registerListener(IBYDAutoListener, int[])`: thêm vào bản đồ id; nếu tiến trình chưa có listener "mọi id" thì gọi `BYDAutoDeviceManager.enableDevice(this, ids)` ⇒ `BYDAutoManager.java:239` `nativeEnableDevice(type, ids)`.
  - `:390-405` `unregisterListener`: gỡ; hết listener ⇒ `disableDevice`.
- `IBYDAutoListenerIdsMap.java:52` `addToMap(listener, ids)` · `:68` `getIdListeners(id)` (listener theo id + listener mọi id).
- `bydauto/BYDAutoDeviceManager.java:27` `mMainHandler = new Handler(context.getMainLooper())`: sự kiện được post về **main looper** của tiến trình ⇒ helper làm mọi việc đăng ký trên main looper.
- `BYDAutoManager.java:215` chặn gọi (`mForbidCall`) theo **tên gói** trong danh sách đen chiến lược; system context của helper mang gói `android`.
- Lớp listener riêng có sẵn, **không dùng**:
  - `bodywork/AbsBYDAutoBodyworkListener.java:46` `onDataChanged` là `final`, `:109-125` rẽ `onDoorStateChanged(area 1..6, state)`, `:332`.
  - `setting/AbsBYDAutoSettingListener.java:105` (`final`), `:490` `onSeatVentilatingStateChanged(1, v)`.
  - Lý do bỏ: `onDataChanged` là `final` (stub sai chữ ký ⇒ `VerifyError`), và mỗi device một lớp là đúng kiểu "một listener mỗi datum" owner không muốn. Giao diện `IBYDAutoListener` có ba phương thức, cài đủ cả ba là hết rủi ro "lớp thật khai abstract".
- Quyền theo device (đều là kiểm phía Java, trên Context truyền vào): `BYDAUTO_BODYWORK_GET` (`BYDAutoBodyworkDevice.java:225`, registerListener `:1309-1323`), `BYDAUTO_SETTING_GET` (`BYDAutoSettingDevice.java:373`, `:2190-2205`), `BYDAUTO_AC_GET` (`ac:45`), `BYDAUTO_PM2P5_GET` (`pm2p5:23`), `BYDAUTO_TYRE_GET` (`tyre:41`), `BYDAUTO_STATISTIC_GET` (`statistic:73`), `BYDAUTO_INSTRUMENT_GET` (`instrument:387`), `BYDAUTO_ENGINE_GET` (`engine:31`), `BYDAUTO_OTA_GET` (`ota:37`), `BYDAUTO_SPEED_GET` (`speed:31`), `BYDAUTO_GEARBOX_GET` (`gearbox:70`), `BYDAUTO_LIGHT_GET` (`light:51`). Từ uid app các quyền này ném `SecurityException` (đã đo với LIGHT 25/09); helper bọc `PermissiveContext` và gọi thẳng phương thức lớp cha nên không chạm chúng.
- Device phải lấy bằng `getInstance(Context)`; một số device trả `null` khi Context `null` (`BYDAutoBodyworkDevice.java:444-450`) ⇒ helper dựng system context MỘT lần (`ActivityThread.systemMain()` tạo ActivityThread mới mỗi lần gọi) và dùng chung.

## 2. Giao thức helper ↔ app (thêm, tương thích hai chiều)

| Chiều | Dòng | Ghi chú |
|---|---|---|
| app → helper | `sub <fqn>=<id>,<id>;<fqn>=<id>…` | Mỗi dòng THAY tập cũ của client đó. `sub` trống = thôi xin. Helper cũ không đọc gì từ client ⇒ dòng nằm yên trong bộ đệm |
| helper → app | `{"topic":"hal.hello","type":2}` | Ngay khi nối. Không có dòng này ⇒ app ở chế độ POLL |
| helper → app | `{"topic":"hal.reg","type":<deviceType>,"dev":"<fqn>","ok":"<hex…>","rej":"<hex…>","err":"…"}` | Mỗi lần tập đăng ký của một device đổi, và gửi lại cho client mới nối |
| helper → app | `{"topic":"hal.ev","type":<featureId>,"dev":"<fqn>","v":<int>,"d":<double>}` | Chỉ khi giá trị ĐỔI; client mới nối nhận giá trị cuối của các id còn đăng ký |

- Client cũ (bản app trước) bỏ qua topic lạ (`applyLine` trả `null`); dòng đèn `light.*` không đổi.
- Helper lấy HỢP các tập xin của mọi client cho từng device, lọc theo `getFeatureIdsFromDevice`, rồi `unregisterListener` + `registerListener(ids)` trong MỘT lượt (native thêm hay thay tập id: [CHƯA BIẾT], một lượt đủ tập thì đúng cả hai). Client đứt ⇒ rút phần của nó ⇒ hết người xin ⇒ huỷ listener; giá trị cuối của id đã huỷ bị quên.
- Mã: `hal-helper/KachiHalMain.java` khối "3.1.6 HAL-EVENTS"; stub biên dịch `hal-helper/stubs/android/hardware/{IBYDAutoListener,IBYDAutoEvent}.java`, `…/bydauto/BYDAutoEventValue.java`; dựng `scripts/build-hal-helper.sh` ⇒ `app/src/main/assets/kachi_hal.jar` (cỡ khác ⇒ `HalHelperLauncher` tự giết bản cũ và đẩy bản mới).

## 3. Phía app

- **Bảng dữ liệu** `core/…/HalEventMap.kt`: `"BYDAuto<X>Device.<getter>#<arg>"` → tên hằng (+ kiểu int/double, dải hợp lệ). Hằng tra số lúc chạy (`featureIdByName`, đúng `isCanFD` của xe). Datum khai thẳng feature-id tự map. `POLL_ONLY` ghi lý do từng getter giữ poll; bài kiểm ép mọi getter đang dùng phải ở một trong hai bảng.
- **Bộ nhớ** `core/…/HalEventCache.kt` + **gateway bọc** `EventCachedGateway` (tầng gateway, nên vòng poll, ô điều khiển, giọng nói, canh ghế cùng hưởng; `HalBindingTable` không thêm dòng nào). Poll còn ba vai:
  1. *đối soát*: giá trị dùng được 60 s kể từ lần làm mới cuối (sự kiện hoặc lượt đọc thật); quá hạn ⇒ một lượt đọc thật rồi cất lại;
  2. *id chưa chứng minh*: chỉ thay poll sau sự kiện đầu tiên của chính nó trong lần nối; id có bằng chứng xe đẩy (`PUSH_PROVEN`: bốn cửa, nắp ca-pô, cốp, mát ghế lái) tin ngay khi đăng ký OK;
  3. *helper vắng / cũ / đứt dây / id bị từ chối*: `hit` luôn `null` ⇒ y hệt trước.
- **Đẩy lên trạng thái**: `CarEventFeed.onMessage` ⇒ `CarStatusRepository.onEvents(ids)` gộp 150 ms ⇒ `CarDataAdapter.readEvents(prev, ids)` chạy lại ba thân nhịp với cổng chỉ gồm `ids` (giá trị từ bộ nhớ ⇒ 0 lượt HAL) + các nút mượn datum đó. Lượt sự kiện không chạm mốc TPMS.
- **Nhịp thân xe 3 s** (`CarStatusRepository.bodyMs`) chỉ còn là đường lùi: `bodyNeeded` chạy khi còn ≥ 1 datum cửa/cốp đang hiện mà sự kiện chưa phủ. Nhịp 1 s của nút cũng nghỉ khi mọi nút đang hiện được phủ.
- **Nguồn giữ helper** (`HalSignalDemand.Source`): `CAR_EVENTS` (màn bày ≥ 1 datum/nút map được VÀ màn sáng) và `SEAT_WATCH` (`SeatAutoOffWatch` đang canh). Đếm chung với `CAMERA`, `LAMP_WIDGET`; ân hạn 60 s như cũ.
- **Ghế**: `SeatAutoOffWatch.start` xin sự kiện ghế + hai id `0x095`; `SeatAutoOffGuard.signatureOf`:
  - luồng không sống hoặc id dấu chưa đăng ký OK ⇒ `UNOBSERVABLE` (cửa sổ +6…+15′ như bản đầu);
  - thấy dấu trong 40 s ⇒ `SEEN`;
  - không thấy dấu nhưng chính lần tắt ghế đã tới qua luồng sự kiện ≥ 8 s trước ⇒ `NOT_SEEN` (thôi canh: không phải mẫu xe tự tắt);
  - còn lại ⇒ `UNOBSERVABLE`. Không bao giờ nói "không thấy" khi chưa chứng minh được là nhìn thấy được.

## 4. Bảng từng datum

"Sự kiện?" = getter có thay được bằng sự kiện không (theo nguồn) và mức bằng chứng xe đẩy. "Nhịp lùi" = nhịp còn lại khi sự kiện chạy (helper vắng ⇒ mọi dòng về nhịp cũ: nhanh 1 s · thân xe 3 s · chậm 10 s).

| datum | device | feature (tên hằng / id) | sự kiện? | quyền getter | nhịp lùi |
|---|---|---|---|---|---|
| `soc` | Statistic | STATISTIC_ELEC_PERCENTAGE | CÓ theo nguồn [SUY]; dùng sau sự kiện đầu | STATISTIC_GET | đối soát 60 s |
| `ev_range_km` | Statistic | STATISTIC_ELEC_DRIVING_RANGE | CÓ theo nguồn [SUY]; dùng sau sự kiện đầu | STATISTIC_GET | đối soát 60 s |
| `fuel_range_km` | Statistic | STATISTIC_FUEL_DRIVING_RANGE | CÓ theo nguồn [SUY]; dùng sau sự kiện đầu | STATISTIC_GET | đối soát 60 s |
| `fuel_pct` | Statistic | STATISTIC_FUEL_PERCENTAGE | CÓ theo nguồn [SUY]; dùng sau sự kiện đầu | STATISTIC_GET | đối soát 60 s |
| `odometer` | Statistic | — | poll — getter nhân 0,1 trên CAN-FD | STATISTIC_GET | như cũ |
| `trip_km` | Instrument | INSTRUMENT_2IN1_CURRENT_JOURNEY_DRIVE_MILEAGE | CÓ theo nguồn [SUY]; dùng sau sự kiện đầu | INSTRUMENT_GET | đối soát 60 s |
| `trip_hours` | Instrument | INSTRUMENT_2IN1_CURRENT_JOURNEY_DRIVE_TIME | CÓ theo nguồn [SUY]; dùng sau sự kiện đầu | INSTRUMENT_GET | đối soát 60 s |
| `consumption_50km` | Instrument | INSTRUMENT_DD_LAST_50KM_POWER_CONSUME | CÓ theo nguồn [SUY]; dùng sau sự kiện đầu | INSTRUMENT_GET | đối soát 60 s |
| `motor_power` | theo BYDAutoDeviceFeaturesMap | Engine.ENGINE_POWER | poll (datum nhanh) | — (get(int[]) không kiểm quyền Java) | 1 s |
| `soh_oem` | theo BYDAutoDeviceFeaturesMap | 1145045032 (0x44400028) | CÓ theo nguồn [SUY]; dùng sau sự kiện đầu | — (get(int[]) không kiểm quyền Java) | đối soát 60 s |
| `speed` | Speed | — | poll (datum nhanh) | SPEED_GET | 1 s |
| `gear` | Gearbox | — | poll (datum nhanh) | GEARBOX_GET | 1 s |
| `pm25_level` | PM2p5 | PM2P5_LEVEL_IN + PM2P5_LEVEL_OUT | CÓ theo nguồn [SUY]; dùng sau sự kiện đầu | PM2P5_GET | đối soát 60 s |
| `pm25_value` | PM2p5 | PM2P5_VALUE_IN + PM2P5_VALUE_OUT | CÓ theo nguồn [SUY]; dùng sau sự kiện đầu | PM2P5_GET | đối soát 60 s |
| `pm25_online` | PM2p5 | PM2P5_ONLINE_STATE | CÓ theo nguồn [SUY]; dùng sau sự kiện đầu | PM2P5_GET | đối soát 60 s |
| `pm25_outside` | PM2p5 | PM2P5_VALUE_IN + PM2P5_VALUE_OUT | CÓ theo nguồn [SUY]; dùng sau sự kiện đầu | PM2P5_GET | đối soát 60 s |
| `cabin_temp` | Ac | AC_TEMP_MAIN | CÓ theo nguồn [SUY]; dùng sau sự kiện đầu | AC_GET | đối soát 60 s |
| `inside_temp` | Ac | AC_TEMP_MAIN | CÓ theo nguồn [SUY]; dùng sau sự kiện đầu | AC_GET | đối soát 60 s |
| `ext_temp` | Instrument | INSTRUMENT_DD_OUT_TEMP | CÓ theo nguồn [SUY]; dùng sau sự kiện đầu | INSTRUMENT_GET | đối soát 60 s |
| `ac_on` | Ac | AC_POWER_STATE | CÓ theo nguồn [SUY]; dùng sau sự kiện đầu | AC_GET | đối soát 60 s |
| `ac_wind` | Ac | AC_WIND_LEVEL | CÓ theo nguồn [SUY]; dùng sau sự kiện đầu | AC_GET | đối soát 60 s |
| `ac_cycle` | Ac | AC_CYCLE_MODE | CÓ theo nguồn [SUY]; dùng sau sự kiện đầu | AC_GET | đối soát 60 s |
| `temp_unit` | Ac | AC_TEMPERATURE_UNIT | CÓ theo nguồn [SUY]; dùng sau sự kiện đầu | AC_GET | đối soát 60 s |
| `anion_state` | PM2p5 | PM25_ANION_STATE | CÓ theo nguồn [SUY]; dùng sau sự kiện đầu | PM2P5_GET | đối soát 60 s |
| `seat_vent_state` | Setting | SET_DRIVER_SEAT_VENTILATING_STATE | CÓ — [ĐO log xe] + nguồn | SETTING_GET | đối soát 60 s |
| `seat_heat_state` | Setting | SET_DRIVER_SEAT_HEATING_STATE | CÓ theo nguồn [SUY]; dùng sau sự kiện đầu | SETTING_GET | đối soát 60 s |
| `seat_vent_state_r` | Setting | SET_PASSENGER_SEAT_VENTILATING_STATE | CÓ theo nguồn [SUY]; dùng sau sự kiện đầu | SETTING_GET | đối soát 60 s |
| `seat_heat_state_r` | Setting | SET_PASSENGER_SEAT_HEATING_STATE | CÓ theo nguồn [SUY]; dùng sau sự kiện đầu | SETTING_GET | đối soát 60 s |
| `defrost_front_state` | Ac | AC_DEFROST_FRONT_STATE | CÓ theo nguồn [SUY]; dùng sau sự kiện đầu | AC_GET | đối soát 60 s |
| `defrost_rear_state` | Ac | AC_DEFROST_REAR_STATE | CÓ theo nguồn [SUY]; dùng sau sự kiện đầu | AC_GET | đối soát 60 s |
| `ac_mode_auto` | Ac | AC_CTRL_MODE | CÓ theo nguồn [SUY]; dùng sau sự kiện đầu | AC_GET | đối soát 60 s |
| `ac_wind_auto` | Ac | AC_WINDLEVEL_MANUAL_SIGN | CÓ theo nguồn [SUY]; dùng sau sự kiện đầu | AC_GET | đối soát 60 s |
| `tyre_p_fl` | Tyre | TYRE_PRESSURE_VALUE_LEFT_FRONT | CÓ theo nguồn [SUY]; dùng sau sự kiện đầu | TYRE_GET | đối soát 60 s |
| `tyre_p_fr` | Tyre | TYRE_PRESSURE_VALUE_RIGHT_FRONT | CÓ theo nguồn [SUY]; dùng sau sự kiện đầu | TYRE_GET | đối soát 60 s |
| `tyre_p_rl` | Tyre | TYRE_PRESSURE_VALUE_LEFT_REAR | CÓ theo nguồn [SUY]; dùng sau sự kiện đầu | TYRE_GET | đối soát 60 s |
| `tyre_p_rr` | Tyre | TYRE_PRESSURE_VALUE_RIGHT_REAR | CÓ theo nguồn [SUY]; dùng sau sự kiện đầu | TYRE_GET | đối soát 60 s |
| `tyre_t_fl` | theo BYDAutoDeviceFeaturesMap | INSTRUMENT_2IN1_LF_TYRE_TEMPERATURE | CÓ theo nguồn [SUY]; dùng sau sự kiện đầu | — (get(int[]) không kiểm quyền Java) | đối soát 60 s |
| `tyre_t_fr` | theo BYDAutoDeviceFeaturesMap | INSTRUMENT_2IN1_RF_TYRE_TEMPERATURE | CÓ theo nguồn [SUY]; dùng sau sự kiện đầu | — (get(int[]) không kiểm quyền Java) | đối soát 60 s |
| `tyre_t_rl` | theo BYDAutoDeviceFeaturesMap | INSTRUMENT_2IN1_LB_TYRE_TEMPERATURE | CÓ theo nguồn [SUY]; dùng sau sự kiện đầu | — (get(int[]) không kiểm quyền Java) | đối soát 60 s |
| `tyre_t_rr` | theo BYDAutoDeviceFeaturesMap | INSTRUMENT_2IN1_RB_TYRE_TEMPERATURE | CÓ theo nguồn [SUY]; dùng sau sự kiện đầu | — (get(int[]) không kiểm quyền Java) | đối soát 60 s |
| `tyre_c_fl` | theo BYDAutoDeviceFeaturesMap | INSTRUMENT_2IN1_LF_TYRE_COLOR | CÓ theo nguồn [SUY]; dùng sau sự kiện đầu | — (get(int[]) không kiểm quyền Java) | đối soát 60 s |
| `tyre_c_fr` | theo BYDAutoDeviceFeaturesMap | INSTRUMENT_2IN1_RF_TYRE_COLOR | CÓ theo nguồn [SUY]; dùng sau sự kiện đầu | — (get(int[]) không kiểm quyền Java) | đối soát 60 s |
| `tyre_c_rl` | theo BYDAutoDeviceFeaturesMap | INSTRUMENT_2IN1_LB_TYRE_COLOR | CÓ theo nguồn [SUY]; dùng sau sự kiện đầu | — (get(int[]) không kiểm quyền Java) | đối soát 60 s |
| `tyre_c_rr` | theo BYDAutoDeviceFeaturesMap | INSTRUMENT_2IN1_RB_TYRE_COLOR | CÓ theo nguồn [SUY]; dùng sau sự kiện đầu | — (get(int[]) không kiểm quyền Java) | đối soát 60 s |
| `tyre_ps_fl` | Tyre | TYRE_PRESSURE_STATE_LEFT_FRONT | CÓ theo nguồn [SUY]; dùng sau sự kiện đầu | TYRE_GET | đối soát 60 s |
| `tyre_ps_fr` | Tyre | TYRE_PRESSURE_STATE_RIGHT_FRONT | CÓ theo nguồn [SUY]; dùng sau sự kiện đầu | TYRE_GET | đối soát 60 s |
| `tyre_ps_rl` | Tyre | TYRE_PRESSURE_STATE_LEFT_REAR | CÓ theo nguồn [SUY]; dùng sau sự kiện đầu | TYRE_GET | đối soát 60 s |
| `tyre_ps_rr` | Tyre | TYRE_PRESSURE_STATE_RIGHT_REAR | CÓ theo nguồn [SUY]; dùng sau sự kiện đầu | TYRE_GET | đối soát 60 s |
| `tyre_lk_fl` | Tyre | TYRE_AIR_LEAK_STATE_LEFT_FRONT | CÓ theo nguồn [SUY]; dùng sau sự kiện đầu | TYRE_GET | đối soát 60 s |
| `tyre_lk_fr` | Tyre | TYRE_AIR_LEAK_STATE_RIGHT_FRONT | CÓ theo nguồn [SUY]; dùng sau sự kiện đầu | TYRE_GET | đối soát 60 s |
| `tyre_lk_rl` | Tyre | TYRE_AIR_LEAK_STATE_LEFT_REAR | CÓ theo nguồn [SUY]; dùng sau sự kiện đầu | TYRE_GET | đối soát 60 s |
| `tyre_lk_rr` | Tyre | TYRE_AIR_LEAK_STATE_RIGHT_REAR | CÓ theo nguồn [SUY]; dùng sau sự kiện đầu | TYRE_GET | đối soát 60 s |
| `tyre_sys` | Tyre | TYRE_SYSTEM_STATE | CÓ theo nguồn [SUY]; dùng sau sự kiện đầu | TYRE_GET | đối soát 60 s |
| `window_lf` | Bodywork | BODYWORK_WINDOW_LEFT_FRONT_PERCENT | CÓ theo nguồn [SUY]; dùng sau sự kiện đầu | BODYWORK_GET | đối soát 60 s |
| `window_rf` | Bodywork | BODYWORK_WINDOW_RIGHT_FRONT_PERCENT | CÓ theo nguồn [SUY]; dùng sau sự kiện đầu | BODYWORK_GET | đối soát 60 s |
| `window_lr` | Bodywork | BODYWORK_WINDOW_LEFT_REAR_PERCENT | CÓ theo nguồn [SUY]; dùng sau sự kiện đầu | BODYWORK_GET | đối soát 60 s |
| `window_rr` | Bodywork | BODYWORK_WINDOW_RIGHT_REAR_PERCENT | CÓ theo nguồn [SUY]; dùng sau sự kiện đầu | BODYWORK_GET | đối soát 60 s |
| `door_lf` | Bodywork | BODYWORK_LEFT_HAND_FRONT_DOOR | CÓ — [ĐO log xe] + nguồn | BODYWORK_GET | đối soát 60 s |
| `door_rf` | Bodywork | BODYWORK_RIGHT_HAND_FRONT_DOOR | CÓ — [ĐO log xe] + nguồn | BODYWORK_GET | đối soát 60 s |
| `door_lr` | Bodywork | BODYWORK_LEFT_HAND_REAR_DOOR | CÓ — [ĐO log xe] + nguồn | BODYWORK_GET | đối soát 60 s |
| `door_rr` | Bodywork | BODYWORK_RIGHT_HAND_REAR_DOOR | CÓ — [ĐO log xe] + nguồn | BODYWORK_GET | đối soát 60 s |
| `tailgate_status` | Bodywork | BODYWORK_LUGGAGE_DOOR | CÓ — [ĐO log xe] + nguồn | BODYWORK_GET | đối soát 60 s |
| `sunroof_state` | Bodywork | BODYWORK_SUNROOF_STATE | CÓ theo nguồn [SUY]; dùng sau sự kiện đầu | BODYWORK_GET | đối soát 60 s |
| `sunshade_pct` | theo BYDAutoDeviceFeaturesMap | 1101004816 (0x41a00010) | CÓ theo nguồn [SUY]; dùng sau sự kiện đầu | — (get(int[]) không kiểm quyền Java) | đối soát 60 s |
| `power_level` | Bodywork | BODYWORK_POWER_LEVEL | CÓ theo nguồn [SUY]; dùng sau sự kiện đầu | BODYWORK_GET | đối soát 60 s |
| `vehicle_type` | Bodywork | — | poll — hằng/buffer | BODYWORK_GET | như cũ |
| `emergency_alarm` | Bodywork | BODYWORK_ALARM_STATE | CÓ theo nguồn [SUY]; dùng sau sự kiện đầu | BODYWORK_GET | đối soát 60 s |
| `light_low_beam` | Light | — | CÓ — đường LampLive riêng | LIGHT_GET | như cũ (getter vẫn đọc đèn) |
| `light_high_beam` | Light | — | CÓ — đường LampLive riêng | LIGHT_GET | như cũ (getter vẫn đọc đèn) |
| `light_front_fog` | Light | — | CÓ — đường LampLive riêng | LIGHT_GET | như cũ (getter vẫn đọc đèn) |
| `light_rear_fog` | Light | — | CÓ — đường LampLive riêng | LIGHT_GET | như cũ (getter vẫn đọc đèn) |
| `light_left_turn` | Light | — | CÓ — đường LampLive riêng | LIGHT_GET | như cũ (getter vẫn đọc đèn) |
| `light_right_turn` | Light | — | CÓ — đường LampLive riêng | LIGHT_GET | như cũ (getter vẫn đọc đèn) |
| `light_side` | Light | — | CÓ — đường LampLive riêng | LIGHT_GET | như cũ (getter vẫn đọc đèn) |
| `light_drl` | theo BYDAutoDeviceFeaturesMap (Light 1004) | 985661476 (0x3ac00024) | CÓ theo nguồn [SUY] (đã thấy `postEvent 3ac00024` tới helper 15/10 [ĐO], nay nghe bằng listener chung) + LampLive | — | đối soát 60 s |
| `headlight_feedback` | theo BYDAutoDeviceFeaturesMap | 1011875880 (0x3c500028) | CÓ theo nguồn [SUY]; dùng sau sự kiện đầu | — (get(int[]) không kiểm quyền Java) | đối soát 60 s |
| `volt_12v` | Ota | OTA_BATTERY_VOLTAGE | CÓ theo nguồn [SUY]; dùng sau sự kiện đầu | OTA_GET | đối soát 60 s |
| `vin` | Bodywork | — | poll — hằng/buffer | BODYWORK_GET | như cũ |
| `oil_level` | Engine | ENGINE_OIL_LEVEL | CÓ theo nguồn [SUY]; dùng sau sự kiện đầu | ENGINE_GET | đối soát 60 s |
| `media_vol` | Android | — | không (không qua HAL) | — | như cũ |


Ghi chú: feature-id thô (`soh_oem`, `sunshade_pct`, `headlight_feedback`, nhiệt/màu lốp…) đọc bằng `get(int[]{id})` (`AbsBYDAutoDevice.java:103-160`), không có lớp getter nên không có kiểm quyền Java; device do `BYDAutoDeviceFeaturesMap` của xe quyết.

## 5. Còn mở / rủi ro

- Tần suất sự kiện của tốc độ (`SPEED_AUTO_SPEED`), công suất, nhiệt độ: [CHƯA ĐO]. Datum nhanh giữ poll 1 s cho tới khi có số.
- Native nhận sự kiện cho uid shell ngoài device Light: [SUY mạnh], chốt bằng dòng `đăng ký … ok=` và `sự kiện ĐẦU` (§8).
- Id tin ngay (`PUSH_PROVEN`) mà sự kiện không tới tiến trình helper ⇒ số cũ tối đa 60 s (hạn đối soát). Đổi được bằng cách bỏ id khỏi `PUSH_PROVEN` (dữ liệu, một dòng).
- Native `enableDevice(type, ids)` thêm hay thay tập: [CHƯA BIẾT]; helper luôn đăng ký đủ HỢP trong một lượt nên đúng cả hai. Bodywork tự đăng ký hai id cảm biến vô-lăng lúc dựng (`BYDAutoBodyworkDevice.java:441`): nếu là "thay" thì hai id đó trong tiến trình helper mất — vô hại (helper không dùng).

## 6. Hiệu năng (owner: mỗi bản sửa chứng minh không nặng thêm)

- Thêm: helper mỗi device một listener + một luồng đọc mỗi client (luồng chặn `readLine`, 0 CPU khi rỗi). App: 0 luồng mới (dùng luồng đọc socket sẵn có + `carScope`). Mỗi sự kiện: một dòng socket loopback + một lượt `readEvents` (gộp 150 ms, 0 HAL).
- Bỏ (khi sự kiện chạy):
  - nhịp thân xe 3 s: 5 lượt `getDoorState` / 3 s = **100/phút** ⇒ ~5/phút (đối soát);
  - nhịp nhanh của nút: mỗi nút đang hiện 60/phút ⇒ ~1/phút (dock 10 nút: ~600 ⇒ ~10/phút);
  - nhịp chậm: mỗi datum map được 6/phút ⇒ ~1/phút.
- Số thật đo bằng dòng `KachiPerf` mỗi phút: thêm `thay-bằng-sự-kiện=` (lượt đọc trả bằng bộ nhớ) và `sự-kiện=` (sự kiện vào). So `HAL đọc=` trước/sau trên cùng màn.

## 7. Bài kiểm

- `core/src/test/…/HalEventsTest.kt`: bảng (mọi getter đang poll có quyết định), map cửa/cốp, giao thức, ba vai còn lại của poll, mảng/dải, gateway bọc 0 HAL, nhịp thân xe chỉ là đường lùi, `readEvents` 0 HAL, repo gộp sự kiện, kế hoạch + nguồn helper, canh ghế + dấu vân tay, nguồn mới đếm chung.
- `app/src/test/…/HalEventsContractTest.kt`: helper đăng ký listener chung, cài đủ ba phương thức, lọc theo bảng device, đọc `sub`, jar đóng gói đúng bản; app nối dây (gateway bọc, `covered`, sink, `subscribe`, canh ghế).
- `app/src/test/…/camera/HalSignalClientEventsTest.kt`: socket thật với helper giả: `sub` khi nối, dòng `hal.*` sang sink, dòng đèn đi đường cũ, đứt dây ⇒ `onHalDown`.

- Thử ĐỎ [ĐO 15/10, `:core:test --tests HalEventsTest`, sửa rồi hoàn tác]: bỏ điều kiện "id chưa chứng minh" + cho `signatureOf` nói `NOT_SEEN` khi chưa thấy lần tắt ⇒ 2/18 ĐỎ (`id chua chung minh…`, `dau van tay`); bỏ cổng `hal.hello` + bỏ vế `covered` của `bodyNeeded` ⇒ 2/18 ĐỎ (`helper vang…`, `nhip than xe chi la duong lui`). Bản thật: 18/18 xanh.
- Nợ: `hal-helper/KachiHalMain.java` 784 dòng (trần 500; tệp Java ngoài Gradle — tách theo khối đèn / sự kiện / socket là lượt dọn sau) · `CarDataAdapter.kt` 509 dòng (cộng phần cốp R32 cùng ngày).

## 8. Kiểm trên xe (chuyến kế tiếp — chỉ đọc log, không thao tác thêm)

Nhật ký Kachi (`usage-*.log`), tag `KachiHalEvent`:

| Dòng | Nghĩa |
|---|---|
| `chế độ=SỰ KIỆN · helper giao thức v2 …` | helper mới đã lên và chào. Không có ⇒ `chế độ=POLL` (helper cũ/không lên) ⇒ mọi thứ như trước |
| `đăng ký BYDAutoBodyworkDevice (type 1001): ok=29400008,… từ-chối=—` | một dòng mỗi device; `từ-chối` ≠ — hoặc `lỗi=` ⇒ id đó vẫn poll |
| `sự kiện ĐẦU BYDAutoBodyworkDevice 0x2940001a v=1 … → tailgate_status` | id đó chứng minh xe đẩy tới helper |
| `KachiPerf … thay-bằng-sự-kiện=…/phút · sự-kiện=…/phút` | số lượt poll đã thay |

Tệp `/data/local/tmp/kachi_hal.log` của helper (ClusterDiag): `đăng ký … type=… ok=[…] rej=[…] err=…`, `sự kiện đầu … id=0x… v=…`.

Thao tác tự nhiên của chuyến là đủ: mở/đóng cửa, mở cốp, bật điều hoà/quạt, chờ +10′ (ghế). Bảng §4 được chứng minh theo từng dòng `sự kiện ĐẦU`.

## 9. Đo xe 15/10 16:13 (Seal, Kachi 3.1.7 vừa cài) — đăng ký 8/8 device + lỗi thu hẹp tập xin (`HAL-EVENTS-SUB-NARROW`)

Nguồn: `kachi_hal.log` của helper, nhật ký Kachi `usage-*.log` (tiến trình chính pid 22824, `:wake` pid 22966), logcat hệ thống cùng chuyến.

### 9.1 Đã chứng minh [ĐO]

| Mốc | Dòng | Kết luận |
|---|---|---|
| 16:13:54.276 | `KachiHalSignal: đã nối 127.0.0.1:19322` | client tiến trình chính nối helper |
| 16:13:54.298 | `KachiHalEvent: chế độ=SỰ KIỆN · helper giao thức v2` | helper mới chào, app vào chế độ sự kiện |
| 16:13:54.497–.649 | 8 dòng `đăng ký …` , tất cả `từ-chối=—`, `err=` rỗng | **đăng ký listener chung chạy cho 8/8 device** (uid shell, `registerListener(IBYDAutoListener, int[])`): PM2p5 4 id · Setting 6 · Statistic 2 · Instrument 13 · Tyre 13 · Bodywork 11 · Ac 6 · Light 1 = 56 id |
| 16:13:54.676 | `sự kiện ĐẦU BYDAutoPM2p5Device 0x4f600010 v=7 → pm25_outside` | sự kiện native tới tiến trình helper (uid shell) cho device ngoài Light — §5 dòng 2 chuyển từ [SUY mạnh] sang [ĐO] cho PM2p5 |
| 16:13:55.13–.23 | helper `sub nhận: 1 device` rồi 8 dòng `đăng ký`: chỉ Setting còn `ok=950000f,9500022,4fa00008,4fa0000b`, 7 device `ok=—` | tập xin bị thu về 4 id canh ghế (`SEAT_WATCH`) |

- Chỉ MỘT client: helper `phát light.* → 1 client`; mọi dòng `KachiHalEvent`/`KachiHalSignal` là pid 22824; `:wake` 0 dòng `KachiHal` ⇒ **giả thuyết C (client thứ hai từ `:wake`) bị loại**.
- 4 id còn lại đúng bằng phần `SEAT_WATCH` (2 dấu `0x095` + 2 id ghế); dòng `sub` một client đã HỢP màn ∪ ghế trong `CarEventFeed.replan` ⇒ **giả thuyết B (SEAT_WATCH thay hợp) bị loại** — cái mất là phần MÀN.
- Phần màn mất vì HOME `onStop`: 16:13:52.897 `KachiAutoNav` luật r3 giao "Nhà" cho Google Maps → 16:13:53.605 `am start --display 0 --windowingMode 1 … MapsActivity` → 16:13:53.907 `Activity pause timeout for … KachiHome` → 16:13:55.031 Maps lên đỉnh → **16:13:55.13 dòng `sub` mới** → 16:13:55.543 `Car3d: dừng`. `onStop` ⇒ `forgetCarDemand` ⇒ `CarEventFeed.screen(null)` ⇒ dòng `sub` chỉ còn ghế. Đây là **giả thuyết A, nửa đầu** — và là ĐÚNG luật thiết kế (màn khuất ⇒ không xin cho màn).
- Sau 16:13:55 tới hết log (16:17) KHÔNG có lần focus/top nào về `KachiHome` (16:14:09 `ClusterBlackActivity` display 1, 16:14:13 VietMap đỉnh display 0, 16:15:44 AVC), và tiến trình chính không gọi getter Bodywork/Ac nào (vòng poll của HOME dừng theo `repeatOnLifecycle(STARTED)`). ⇒ Nửa sau giả thuyết A ("HOME quay lại mà không xin lại") **chưa có dữ liệu**: HOME không quay lại trong cửa sổ log. Đọc nguồn [SUY]: HOME `onStart` ⇒ `collectHome` phát lại ảnh state ⇒ `publish` ⇒ `screen(ids)` ⇒ dòng `sub` đủ — đường xin lại có.
- `KachiPerf` (`thay-bằng-sự-kiện=` / `sự-kiện=`): **0 dòng** trong các tệp chuyến này [ĐO grep] — phiên quá ngắn / chưa tới nhịp in.

### 9.2 Lỗi và sửa (3.1.8)

1. **Thu hẹp tức thì** — mỗi lần HOME khuất/dựng lại (cài đặt, AutoNav mở app, màn chớp, ảnh state trung gian) là một vòng huỷ rồi đăng ký lại cả bus, và bộ nhớ sự kiện của datum màn bị xoá (`setPlan` giữ lại đúng kế hoạch) ⇒ HOME về là poll lạnh tới sự kiện/đối soát kế. Sửa `CarEventFeed`: tập gửi = HỢP mọi nguồn; THÊM id ⇒ gửi ngay; BỚT id ⇒ giữ cũ ∪ mới, hẹn `NARROW_HOLD_MS` = 10 s trên luồng hub (`HalSignalHub.after`, không luồng mới); trong hạn nhu cầu quay lại ⇒ huỷ hẹn, 0 dòng `sub`; hết hạn ⇒ thu về đúng nhu cầu (id ghế đang canh không bao giờ bị bỏ). Nguồn helper `CAR_EVENTS` cũng chỉ nhả lúc thu thật.
2. **`registered` không co** — `hal.reg` mang TOÀN BỘ tập của device, nhưng `HalEventCache.onReg` chỉ cộng/trừ ⇒ id helper đã huỷ (`ok=—`) vẫn được coi là đăng ký tới khi đứt dây [SUY từ nguồn]. Sửa: thay tập theo device (`onReg(ok, rej, device)`).
3. **Log mỗi lần đổi** — `KachiHalEvent: đăng ký sự kiện: <n> id / <m> device (+a −b) · lý do: <màn rời/chưa công bố | nhu cầu màn … | màn xe tắt/sáng | canh ghế … | thôi canh ghế | hết hoãn thu hẹp>` (+ `· hoãn thu hẹp 10 s` khi đang giữ).

Helper không đổi (đã HỢP đúng theo client: `SUBS.put(os, want)` / `SUBS.remove(os)` / `reconcile` cộng mọi client) ⇒ `kachi_hal.jar` giữ nguyên.

Chi phí: 0 luồng mới, 0 lượt HAL thêm. Thêm: tối đa 10 s sự kiện của datum màn đã rời đi tiếp qua socket loopback. Bớt: mỗi lần HOME khuất < 10 s không còn 2 vòng huỷ/đăng ký (8 device × 2 lời gọi binder) và không còn poll lạnh lúc về.

Bài kiểm: `core/src/test/…/HalEventsSubNarrowTest.kt` (6 bài: hợp màn ∪ ghế · khuất ngắn không huỷ, về không gửi lại · hết hạn thu hẹp, ghế giữ, lý do được log, về sau khi thu xin lại ngay · thêm id gửi ngay khi đang hoãn · không bộ hẹn ⇒ thu ngay · `hal.reg` thay tập theo device). Thử ĐỎ [ĐO 15/10]: bỏ nhánh hoãn ⇒ 3/6 ĐỎ; nhánh hoãn gửi `target` ⇒ 3/6 ĐỎ; bỏ khối `device` của `onReg` ⇒ 1/6 ĐỎ. `app/src/test/…/HalEventsContractTest.kt`: helper hợp mọi client + app nối bộ hẹn vào luồng hub.

### 9.3 Soi trên xe lần sau (grep `KachiHalEvent` trong `usage-*.log`)

- `đăng ký sự kiện: … lý do: màn rời/chưa công bố · hoãn thu hẹp 10 s` ngay sau lúc mở app khác từ HOME; quay lại HOME trong 10 s ⇒ KHÔNG có dòng `đăng ký sự kiện` mới và helper KHÔNG có `sub nhận` mới.
- Ở ngoài HOME > 10 s ⇒ `… lý do: hết hoãn thu hẹp` (−N id); về HOME ⇒ `… lý do: nhu cầu màn … (+N …)` ngay lượt state đầu, kèm 8 dòng `đăng ký … ok=` đủ lại.
- `sự kiện ĐẦU` cho Bodywork/Ac/Tyre (chưa thấy chuyến này vì HOME khuất 0,5 s sau đăng ký) — mở/đóng cửa khi HOME đang hiện.
- `KachiPerf … thay-bằng-sự-kiện=… · sự-kiện=…` sau ≥ 1 phút ở HOME.
