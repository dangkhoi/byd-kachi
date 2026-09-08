# RE launcher tham chiếu "Dudu桌面PRO" (`com.dudu.autoui` 1.015007, bản BYD) — 2026-09-06

> **Trạng thái**: RE NOTED. Owner: "mò method-by-method không OK — cài launcher thật (Dudu) xem người ta làm gì". Đã cài lên xe + decompile `/tmp/dudu`. Đây là **ground-truth thao tác + feature-id cho launcher ClusterNav sau này**.

## Cơ chế điều khiển HAL của Dudu (khác ClusterNav)
- Extends **`android.hardware.bydauto.AbsBYDAutoDevice`** trực tiếp (không qua từng `BYDAuto*Device` con), fire bằng:
  - `set(int featureId, int i2, int i3)` · `set(int featureId, int i2, byte[])`
  - `get(int featureId, int i2)` · `getIntArray` · `getBuffer` · `getDouble` · `getDoubleArray`
- featureId = hằng `android.hardware.bydauto.BYDAutoFeatureIds.*` (40+ hằng).
- **Tầng shellManage** (`com.dudu.autoui.manage.shellManage.k` + UI `BydShellManageView`): khi cờ bật + shell sẵn sàng → route get/set qua **helper đặc quyền** thay vì `super.set/get`. Đây là cách Dudu ghi được HAL mà app thường bị chặn (giống ClusterNav bị SELinux chặn shell app_process — Dudu giải bằng shell helper riêng). **Cần điều tra tầng này** nếu ClusterNav-launcher gặp feature bị chặn ghi trực tiếp.
- Base wrapper: `manage/console/impl/byd/api/AbsBYDAutoDeviceEx.java`; lớp control obfuscated (`api/{a..z,a0..}.java`); UI ở `ui/activity/launcher/byd/BydCarControlView`, `BydCarControlButtonView`, `BydCarBaseControlView`.

## Bản đồ THAO TÁC launcher (feature-id Dudu dùng) — nhóm theo domain
### Cửa sổ — ĐIỀU KHIỂN THEO % (giải đáp "mở 50%")
- `BODYWORK_WINDOW_LEFT_FRONT_PERCENT` · `..._RIGHT_FRONT_PERCENT` · `..._LEFT_REAR_PERCENT` · `..._RIGHT_REAR_PERCENT`
- ⇒ mở 50% = **set feature % = 50** (KHÔNG phải nhị phân như `setBodyWindowCtrlState`). Đây là đường đúng cho "mở hé/50%".
### Cửa sổ trời / rèm
- `BODYWORK_MOON_ROOF_OPEN_PERCENT` (nóc) · `BODYWORK_SUNSHADE_PANEL_PERCENT` (rèm) — theo %. (Xe owner đọc sunroof=65535 ⇒ có thể không có nóc; tuỳ trim.)
### Cửa / cốp / nắp ca-pô
- `BODYWORK_LEFT_HAND_FRONT_DOOR` · `..._RIGHT_HAND_FRONT_DOOR` · `..._LEFT_HAND_REAR_DOOR` · `..._RIGHT_HAND_REAR_DOOR`
- `BODYWORK_LUGGAGE_DOOR` (**cốp**) · `BODYWORK_HOOD` (ca-pô)
### Gạt mưa (gồm CHẾ ĐỘ BẢO TRÌ owner hỏi)
- `WIPER_FRONT_WIPER_LEVEL` (mức gạt trước)
- `SET_FRONT_WINDSCREEN_WIPER_OVERHAUL_STATE` · `SET_REAR_WINDSCREEN_WIPER_OVERHAUL_STATE` — **"OVERHAUL" = chế độ bảo trì/sửa (dựng cần gạt)** ✅ đúng thứ owner cần.
### Ghế + vô-lăng (khớp đường SETTING device ClusterNav đã fix)
- `SET_{DRIVER,PASSENGER,REAR_LEFT,REAR_RIGHT}_SEAT_VENTILATING_STATE` + `..._HEATING_STATE` (= feature-id sau các method `setSeat{Ventilating,Heating}State`).
- Sưởi vô-lăng: `setSteeringWheelHeatingState` (thấy ở app AC OEM).
### Đèn (find-my-car / điều khiển đèn)
- `LIGHT_LOW_BEAM_LIGHT` · `LIGHT_HIGH_BEAM_LIGHT` · `LIGHT_FRONT_FOG_LIGHT` · `LIGHT_REAR_FOG_LIGHT` · `LIGHT_{LEFT,RIGHT,}_TURN_SIGNAL_LIGHT` · `LIGHT_SIDE_LIGHT` · `LIGHT_FOOT_LIGHT` · `LIGHT_DAY_RUNNING_LIGHT_AUTO_STATE` · `SET_INSIDE_LIGHT_DOOR_STATE` (đèn trần theo cửa)
### Máy lạnh
- `AC_CTRL_SOURCE_SET` (14× — nguồn điều khiển AC). + getter AC đã [ĐO] trên xe: `getAcStartState/WindLevel/WindMode/CycleMode/…` (setter KHÔNG phải `setAcWindLevel(int)` — cần RE tiếp: có thể qua feature-id `AC_*` hoặc set(featureId,i2,i3)).
### Lái / năng lượng / an toàn
- `GEARBOX_MANUAL_MODE_LEVEL` · `GEARBOX_AUTO_MODE_TYPE` · `SET_DR_SOC_TARGET` (mục tiêu sạc %) · `SET_DR_ENERGY_FB` (mức tái tạo/regen) · `ADAS_AVH_STATE` (auto hold) · `SENSOR_AUTO_SLOPE`
### Đọc bảng đồng hồ (read-only, cho widget)
- `STATISTIC_*` (tổng km, tiêu thụ xăng/điện, range, pin cao/thấp/nhiệt) · `TYRE_PRESSURE_VALUE_{LF,RF,LR,RR}` + `INSTRUMENT_2IN1_*_TYRE_{PRESSURE,TEMPERATURE}` · `PM2P5_ONLINE_STATE` · `OTA_BATTERY_*` · `SAFETY_BELT_*` (dây an toàn từng ghế)

## Việc RE tiếp (để dựng launcher)
1. **Giá trị SỐ của feature-id**: `BYDAutoFeatureIds` là class framework — lấy value thật (pull `/system/framework/*bydauto*.jar` từ xe, hoặc từ OpenBYD apk bản đủ). Dudu chỉ import tên.
2. **Ngữ nghĩa arg `set(featureId, i2, i3)`**: đọc lớp control Dudu (`api/*.java` obfuscated) cho vài control mẫu (window %, wiper overhaul, light) → biết i2/i3 là gì (giá trị %, on/off…).
3. **Tầng shellManage**: cách Dudu ghi HAL bị chặn — có cần cho ClusterNav không (nhiều feature ClusterNav set trực tiếp OK: kính `setBodyWindowCtrlState` rc=0 [ĐO]).
4. So khớp: ClusterNav nên chuyển sang **`AbsBYDAutoDevice.set(featureId,…)`** (đầy đủ như Dudu) hay giữ named-method (đang OK cho ghế/kính/PM2.5)? Hybrid: named cho cái đã proven, feature-id cho phần mới (đèn/gạt-mưa/%-kính/sạc).

## [ĐO] đã xác nhận trên xe hôm nay (probe named-mode)
- Kính: `setBodyWindowCtrlState(1,1) rc=0` → mở 98%, `(1,0)` đóng. `getWindowOpenPercent` đọc được (98). Nhị phân qua method này; nhưng Dudu cho thấy có feature-id `%_PERCENT` để đặt % chính xác.
- AC getter chạy hết (AC on, gió=2, recirc=1). `setAcWindLevel(int)`/`setAcWindMode(int)` KHÔNG tồn tại (NoSuchMethod) ⇒ AC set qua feature-id, không qua named setter đơn giản.
- PM2.5 `setAutoCleanAirState(1) rc=0` (probe preset).
