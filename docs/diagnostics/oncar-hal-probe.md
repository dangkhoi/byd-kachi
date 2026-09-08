# On-car HAL probe — bắn lệnh HAL nhanh qua adb (vehicleTest-only)

> **Trạng thái**: Current · **Cập nhật**: 2026-09-06 · **Mục đích**: Cách dùng `HalProbeReceiver` để bắn lệnh HAL BYDAuto tuỳ ý (method-tên / raw feature-id / preset) trên xe qua adb, phục vụ RE + kiểm tra nhanh không cần rebuild.

## ⚠ Chỉ có trong build `vehicleTest`

`HalProbeReceiver` + `HalProbePresets` nằm ở source set `app/src/vehicleTest/` và chỉ đăng ký trong
`app/src/vehicleTest/AndroidManifest.xml`. Vì vậy **release/debug APK KHÔNG có receiver này** (đây là bề mặt
ghi-thiết-bị, luật dự án giữ tách khỏi release — giống bộ dò T10). Build bản dò:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:assembleVehicleTest
```

Cài rồi bắn lệnh (receiver `exported=true` trong vehicleTest nên `am broadcast` từ uid shell tới được).

## 1. Bắn lệnh — 3 chế độ

Action: `com.byd.clusternav.vehicletest.HAL_PROBE`.

### a) PRESET (nhanh nhất) — `--es cmd <tên>`

```bash
adb shell am broadcast -a com.byd.clusternav.vehicletest.HAL_PROBE --es cmd window-open-lf
```

Preset seed (xem `HalProbePresets.REGISTRY`):

| cmd                | device    | tác động                                     |
|--------------------|-----------|----------------------------------------------|
| `seat-cool-driver` | SETTING   | `setSeatVentilatingState(1, 3)`              |
| `seat-off-driver`  | SETTING   | `setSeatVentilatingState(1, 1)`              |
| `window-open-lf`   | BODYWORK  | `setBodyWindowCtrlState(1, 1)` (mở trái-trước)|
| `window-close-lf`  | BODYWORK  | `setBodyWindowCtrlState(1, 0)` (đóng trái-trước)|
| `trunk-open`       | BODYWORK  | `setHetchDoorStatus(1)`                      |
| `trunk-close`      | BODYWORK  | `setHetchDoorStatus(2)`                      |
| `pm25-on`          | AC        | `setAutoCleanAirState(1)`                    |
| `nav-screen-on`    | SETTING   | raw `0x4C10E015 = 3` (SET_NAVI_SCREEN_STATUS)|

Gõ sai `cmd` → log liệt kê mọi tên có sẵn.

### b) NAMED method — `--es device <KEY> --es method <tên> [--ei a0 .. a3 ..]`

Gọi 1 method int×N tuỳ ý trên device. Số tham số = số `a0..a3` LIÊN TIẾP có mặt.

```bash
# Mở cửa sổ trái-trước: setBodyWindowCtrlState(1, 1)
adb shell am broadcast -a com.byd.clusternav.vehicletest.HAL_PROBE \
  --es device BODYWORK --es method setBodyWindowCtrlState --ei a0 1 --ei a1 1

# Mở cốp: setHetchDoorStatus(1)
adb shell am broadcast -a com.byd.clusternav.vehicletest.HAL_PROBE \
  --es device BODYWORK --es method setHetchDoorStatus --ei a0 1
```

### c) RAW feature-id — `--es device <KEY> --es fid 0x<id> --ei val <v>`

Ghi thẳng feature-id qua `BydHal.setInt` (kiểu T10 cũ). `fid` nhận `0x…` hoặc số thập phân.

```bash
# SET_NAVI_SCREEN_STATUS_SET = 3
adb shell am broadcast -a com.byd.clusternav.vehicletest.HAL_PROBE \
  --es device SETTING --es fid 0x4C10E015 --ei val 3
```

**Device KEY hợp lệ**: `SETTING | AC | BODYWORK | INSTRUMENT | PM2P5 | SPEED | STATISTIC`
(ánh xạ sang FQN trong `HalProbePresets.fqnFor` / `BydHal`).

## 2. Đọc kết quả

Mọi kết quả ra logcat tag `HalProbe`:

```bash
adb logcat -s HalProbe
```

Ví dụ dòng log:
```
HalProbe: preset=window-open-lf rc=0
HalProbe: named device=BODYWORK method=setBodyWindowCtrlState args=[1, 1] rc=0
HalProbe: fid device=SETTING fid=0x4c10e015 val=3 rc=0
```

- `rc=<n>` = giá trị HAL trả về (0 thường là OK; `-2147482648` = not-provisioned/no-permission — xem
  `BydHal.NOT_PROVISIONED_RC`).
- `rc=null` = method trả void (gọi được, không có giá trị trả).
- Chuỗi lỗi kiểu `NoSuchMethodException: …` = ROM không có method đó / sai chữ ký.
- `device null (off-car / no HAL)` = không lấy được device (chạy ngoài xe).

Probe chạy trên thread nền, mọi thứ bọc `runCatching` → không bao giờ crash app.

## 3. Thêm 1 preset (đúng 1 dòng)

Mở `app/src/vehicleTest/java/com/byd/clusternav/HalProbePresets.kt`, thêm 1 entry vào `REGISTRY`:

```kotlin
// gọi method-TÊN int×N:
"window-open-rf" to named(BydHal.BODYWORK, "setBodyWindowCtrlState", 2, 1),
// hoặc ghi RAW feature-id:
"my-raw" to fid(BydHal.SETTING, 0x4C10E015, 1),
```

Không cần sửa gì khác — `HalProbeReceiver` tự tra `REGISTRY` theo `--es cmd <tên>`.

## Ghi chú an toàn

- Mở cửa sổ / cốp khi xe đang chạy = rủi ro. Probe KHÔNG tự gate theo tốc độ/số P — dùng trên xe đứng yên.
- `getWindowPermitState()` (đọc quyền điều khiển) có thể kiểm trước bằng NAMED mode nếu ROM cho phép.
- Đây là công cụ RE/kiểm tra của owner, KHÔNG có trong bản release phát cho anh em.
