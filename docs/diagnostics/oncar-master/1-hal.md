# MẢNG HAL — ma trận sweep MỘT-BUỔI-ĐỦ (RE xong off-car, chỉ còn bấm)

> **Trạng thái**: Superseded — thay bởi `docs/diagnostics/oncar-sweep-verify-2026-09-21.md` · **Cập nhật**: 2026-09-19 · **Mục đích**: gom MỌI control/datum chưa verify trên xe thành **một bảng sweep** — mỗi dòng có device · method/feature-id · **DẢI giá trị ứng viên** · đọc-lại kỳ vọng · lệnh gõ sẵn · outcome→kết luận. Có **Option A/B/C** cho mọi mục rủi ro để A fail thì thử B/C **ngay trong buổi**, không cần buổi thứ hai.
> **Phạm vi**: HAL (L-RE · L-RE2 · H1-T2b · W2 · W3 · W5 · 5 datum NEEDS_CAR · thang mức ghế). Cast/voice ở `2-slot-cast.md` / `3-voice.md`.
> **Nhãn bằng chứng**: `[ĐO source]` = đọc được ở nguồn/dữ liệu đã chụp (có `file:line`) · `[SUY]` = suy từ nguồn, chưa chạy · `[CHƯA BIẾT]` = không có dữ liệu.

---

## 0. Ba phát hiện đổi CÁCH sweep (đọc trước khi lên xe)

### 0.1 `featmap` đã có sẵn từ lượt 09-16 — 35/38 feature-id vốn **không kiểm được off-car** nay đã tra xong

[ĐO source] `BYDAutoFeatureIds` trong stub SDK (`jadx-tmap/sources/android/hardware/bydauto/BYDAutoFeatureIds.java`, 6743 dòng / 6334 hằng, **0 khối `static {}`**) mang giá trị **inline**, và điền `0` cho mọi id mà bản SDK đó không có. Bản R8 của OpenBYD thì ngược lại: `public static int` **không final, không giá trị** (`jadx-openbyd24/sources/android/hardware/bydauto/BYDAutoFeatureIds.java:5-21`) ⇒ số thật do framework gán **lúc chạy**.

Đối chiếu 38 `bindingKey` dạng số của repo với stub: **chỉ 3 tra ra tên** (`321912848` = `WIPER_FRONT_WIPER_LEVEL` · `960495624` = `REAR_VIEW_MIRROR_STATE` · `985661476` = `LIGHT_DAY_RUNNING_LIGHT_AUTO_STATE`), **35 còn lại vắng**.

**Nhưng không cần lên xe mới biết**: lượt 09-16 đã chụp `featmap` của **chính xe owner** — `docs/diagnostics/oncar-trace-2026-09-16b/featmap-20260916.json` (stamp `20260916-185942`, **7193** tên→id, **47** device kèm tập id). Tra trên tệp đó đã giải xong toàn bộ 35 id + tìm được id THẬT cho mọi mục đang treo (§5, §6). ⇒ **Ma trận dưới đây là `[ĐO source]` trên dữ liệu xe thật, không phải phỏng đoán.**

> ⚠ `featmap` cũ ≠ xe hôm nay. Bước đầu buổi vẫn chạy lại `featmap` (10 giây) rồi `diff` với tệp 09-16; khác thì tin tệp mới.

### 0.2 Lượt sweep gạt mưa 09-16 fail vì **sai device**, không vì id vắng

[ĐO source] `hal-reads.txt:27-29` — đọc `1336934438` / `321912864` trả `unavailable: null` và kết luận treo. Gốc: `TestBridgeHal.DEFAULT_DEVICE = "BYDAutoBodyworkDevice"` (`app/.../testbridge/TestBridgeHal.kt:41`), lệnh **không truyền `--es dev`** ⇒ hỏ�i Bodywork (1001) về một id thuộc **Wiper (1046)** ⇒ `checkDeviceFeatures` từ chối.

**LUẬT SWEEP**: mọi lượt `getid`/`setev` **PHẢI** truyền `--es dev` đúng device theo `featmap`. Cột `device` của ma trận đã điền sẵn. Lời đáp có trường `device_by_map` — nếu nó khác `--es dev` mình gõ thì **gõ lại theo nó** trước khi kết luận "không có".

### 0.3 Ba cặp feature-id TRÙNG (L-RE2) — đã tra xong, 1 cặp là **bug thật đang chạy**

| Cặp | Tình trạng | [ĐO source] trên `featmap-20260916.json` |
|---|---|---|
| `cam` / `camera_view` (cũ `3001`) | **đã hết trùng** | cả hai đã đổi sang named-method ở 1.70. Còn lỗi khác: `camera_view` trỏ `setDisplayMode` mà **method đó không tồn tại** (§6.4) |
| `headl` / `headlight_mode` (`1276153912`) | **đã hết trùng** | `1276153912` = `Instrument.INSTRUMENT_HEADLIGHT_CONTROL_SET`, dev **1007** — chú thích repo ĐÚNG. `headl` đã rút id, còn `INSTRUMENT_HEADLIGHT_ON_OFF` → route `None`. Không có id on/off nào cho đèn pha trên xe này (§6.5) |
| `brightness_gear` / `hud_brightness` (`1276174360`) | ⚠ **VẪN TRÙNG — bug đang chạy** | `1276174360` = `Setting.SET_BRIGHTNESS_GEAR_SET`, dev 1023 = **độ sáng màn chính**. Kéo thanh "Độ sáng HUD" đang đổi **độ sáng màn hình**. Cả họ HUD trên xe chỉ có 5 id, **không có id brightness nào**: `SET_HUD_SWITCH_SET` 1276174371 · `SET_HUD_MODE_SET` **1276174373** · `SET_HUD_CONFIG` 951058453 · `SET_HUD_MODE_FEEDBACK` 951058445 · `SET_HUD_SWITCH_STATUS_FEEDBACK` 951058460 ⇒ `hud_brightness` phải thành **MODE** hoặc **gỡ**, không thể là brightness (§6.3) |

---

## 1. Giao thức sweep — thứ tự, an toàn, cách ghi

**Điều kiện**: xe **ĐỖ**, tay số **P**, phanh tay, người ngồi trong xe nhìn được kính/cốp/nóc. Bật *Cài đặt › Hệ thống › Nâng cao › Chế độ kiểm thử qua adb* (tự tắt sau 60 phút — bật lại khi hết).

**Bốn lệnh dùng suốt buổi** (action = `com.byd.launcher.TEST`):

```bash
A=com.byd.launcher.TEST
B() { adb shell am broadcast -a $A "$@"; }        # in JSON thẳng ra terminal

# 1) bảng feature-id THẬT của xe (chạy ĐẦU TIÊN)
B --es cmd featmap

# 2) quét chỉ-đọc toàn bộ telemetry + route toàn bộ control (không bắn gì)
B --es cmd sweep --es op all

# 3) ĐỌC getter named-method            (không cần confirm)
B --es cmd hal --es op get   --es dev <Device> --es m <getter> --es args <csv>
# 3b) ĐỌC theo feature-id                (không cần confirm)
B --es cmd hal --es op getid --es dev <Device> --es m <id|TÊN_HẰNG>

# 4) GHI named-method                    (cần auto_confirm)
B --es cmd hal --es op set   --es dev <Device> --es m <setter> --es args <csv> --ez auto_confirm true
# 4b) GHI theo feature-id
B --es cmd hal --es op setev --es dev <Device> --es m <id|TÊN_HẰNG> --es args <v> --ez auto_confirm true

# 5) bắn qua ĐÚNG đường một cú chạm ô nút (kiểm cả writeArgs của repo)
B --es cmd ctl --es id <controlId> --ei v <n> --ez auto_confirm true
```

**Đọc lời đáp** — phân biệt ba kết cục, đừng gộp:

| Trường | Nghĩa | Kết luận |
|---|---|---|
| `"reply":"ok: read"` + `value` số | đọc được | ghi số vào cột đọc-lại |
| `"sentinel":true` (`-2147482648` / `-2147482645`) | feature **không provision** trên trim | mục này xe KHÔNG có ⇒ gỡ/ẩn, đừng vá tiếp |
| `"reply":"unavailable: null…"` | method vắng **HOẶC sai device** **HOẶC thiếu quyền** | **chưa kết luận được** — gõ lại với `--es dev` = `device_by_map` |
| `rc=0` ở lượt ghi | HAL **nhận** lệnh | ⚠ rc=0 **không** = xe làm. **Phải NHÌN xe** rồi đọc lại datum |

⚠ **`rc=0` là cái bẫy lớn nhất của buổi này.** Bài học 1.38/1.33: `setAutoCleanAirState` trả `rc=0` suốt mà xe **không lọc**. Mỗi dòng ghi đều có cột *đọc-lại kỳ vọng* — không đọc lại thì dòng đó **chưa PASS**.

**Ghi kết quả**: một dòng TSV / mục, dán thẳng vào §7:
`<id> | <option> | <lệnh> | rc | value đọc lại | owner NHÌN thấy gì | PASS/FAIL`

**Thứ tự chạy** (rủi ro tăng dần, việc chặn-nhiều-thứ trước):

1. `featmap` + `sweep` — 2 lệnh, mở khoá mọi phân loại sau đó.
2. **Khối ĐỌC** (§3) — 0 rủi ro, trả lời 27 nút chưa có đường đọc + 5 datum NEEDS_CAR.
3. **Khối GHI nhẹ** (§4.A khí hậu/đèn/ghế) — đảo lại được bằng một cú bấm.
4. **Khối GHI thân xe** (§4.B kính/nóc/rèm/cốp/khoá) — cần người nhìn, xe đỗ.
5. **Khối còn treo** (§4.C ambient/camera/HUD/gạt mưa) — nhiều option, chạy cuối.

---

## 2. Bản đồ device (số ↔ tên) — dùng cho cột `device` của mọi bảng

[ĐO source] `featmap-20260916.json` → 47 device có trên xe owner:
`1000` Ac · `1001` Bodywork · `1002` Charging(?) · `1004` Light · `1005`–`1006` · `1007` Instrument · `1008` Pm2p5 · `1009` Charging · `1010`–`1015` · `1014` Statistic · `1016` Tyre · `1017` **Location** · `1019`–`1030` · `1023` **Setting** · `1031` **Panorama** · `1032` Ota · `1033`–`1034` · `1036`–`1043` · `1041` **DoorLock** · `1045` · `1046` **Wiper** · `1047` RearViewMirror · `1048`–`1049` · `1061` BigData.

FQN suy ra bằng `HalBindingTable.deviceFqn` (`core/.../HalBindingTable.kt:392`): `BYDAutoSettingDevice` → `android.hardware.bydauto.setting.BYDAutoSettingDevice`. Ở lệnh `hal` chỉ cần tên đơn giản.

⚠ **Bốn control đang route SAI device** [ĐO source] (feature-id thuộc device X, `Domain` lại đoán ra Y):

| id | feature-id | device THẬT | `Domain` đoán ra | Hệ quả |
|---|---|---|---|---|
| `wiper` | `321912848` `WIPER_FRONT_WIPER_LEVEL` | **1046** Wiper | BODY → Bodywork 1001 | bị `checkDeviceFeatures` chặn |
| `cluster_music` | `1138753546` `INSTRUMENT_MUSIC_STATE_SET` | **1007** Instrument | INFOTAINMENT → Setting 1023 | chặn |
| `mirror_fold` (datum) | `960495624` `REAR_VIEW_MIRROR_STATE` | **1047** | BODY → Bodywork 1001 | chặn |
| `ambient_front_color` + 3 datum IAL | `1121976336/43/28/32` `SET_IAL_*` | **1023** Setting | LIGHTS → Light 1004 | chặn |

⇒ Bốn dòng này chỉ cần `halDevice` = device thật là chạy, **không phải đổi id**. Sweep chỉ để xác nhận (§4.C).

---

## 3. KHỐI ĐỌC — grab-list (0 rủi ro, chạy trước mọi lượt ghi)

Mục đích: đóng **H1-T2b** (27 nút chưa có đường ĐỌC) + **5 datum NEEDS_CAR**.

[ĐO source] đếm bằng máy trên `ControlRegistry.kt`: **47** nút, **17** có `readKey`, **30** chưa. Trong 30 đó có 3 nút BẤM-một-phát không có trạng thái để đọc (`door` · `pm25_clean_now` · `seat_memory`) và `cast` không phải control xe ⇒ **26 nút** thật sự cần đường đọc (backlog ghi 27 — lệch 1 vì `cast`).

### 3.1 Nút — getter đã tìm ra ở nguồn (chạy hết một lượt, 26 lệnh)

Tất cả đều `--es op get`, không cần confirm. Cột *kỳ vọng* = giá trị hợp lệ theo enum; ngoài dải ⇒ ghi `[CHƯA BIẾT]`.

| # | nút | device | getter (`--es m`) | args | kỳ vọng | nguồn `file:line` |
|---|---|---|---|---|---|---|
| R1 | `lock` | `BYDAutoDoorLockDevice` | `getDoorLockStatus` | `1`…`5` | UNLOCK=1 · LOCK=2 · INVALID=0 | `doorlock/BYDAutoDoorLockDevice.java:41`, area `:8-14`, state `:20-22` |
| R2 | `lock` (Option B) | `BYDAutoSettingDevice` | `getDoorLock` | — | [CHƯA BIẾT] enum | `setting/BYDAutoSettingDevice.java:616` |
| R3 | `window` | `BYDAutoBodyworkDevice` | `getWindowOpenPercent` | `1` | 0..100 | `bodywork/…:538`, `WINDOW_OPEN_PERCENT_MIN/MAX` `:379-380` |
| R4 | `readl` | `BYDAutoSettingDevice` | `getInsideLightDoorState` | — | OPEN=1 · CLOSE=2 · INVALID=0 | `setting/…:730`, enum `:215-217` |
| R5 | `pm25` | `BYDAutoPM2p5Device` | `getPM2p5Level` | — | `int[]` → phần tử [0] | `pm2p5/…:84` |
| R6 | `pm25` (giá trị µg/m³) | `BYDAutoPM2p5Device` | `getPM2p5Value` | — | `int[]` → [0] | `pm2p5/…:92` |
| R7 | `cam` | `BYDAutoPanoramaDevice` | `getPanoWorkState` | — | WORK_OFF=0 · ON=1 | `panorama/…:214`, enum `:128-129` |
| R8 | `headl` | `BYDAutoLightDevice` | `getLightStatus` | `2` (LOW) rồi `3` (HIGH) | OFF=0 · ON=1 | `light/…:158`, enum `:49,:56,:58-59` |
| R9 | `headl` (Option B) | `BYDAutoLightDevice` | `getGroupHeadlightState` | `0`(L) `1`(R) | [CHƯA BIẾT] | `light/…:142`, group `:46-47` |
| R10 | `wiper` | `BYDAutoWiperDevice` | `getWindscreenWiperSensitivity` | — | 1..4 · INVALID=0 — **[ĐO xe 09-16] = 4 ✓** | `wiper/…:76`, enum `:19-23`; `hal-reads.txt:13` |
| R11 | `wiper` (đang gạt?) | `BYDAutoWiperDevice` | `getWindscreenWiperRelayState` | — | ACTUATION=1 · DISCONNECT=2 — **[ĐO xe] = 0 (INVALID) cả lúc đang gạt ⇒ getter này KHÔNG phản ánh việc gạt** | `wiper/…:68`, enum `:16-18`; `wiper-poll.txt` 22 mẫu |
| R12 | `steer_heat` | `BYDAutoSettingDevice` | `getSteeringWheelHeatingState` | — | OFF=1 · ON=2 · INVALID=0 | `setting/…:1031`, enum `:407-409` |
| R13 | `windows_all` | `BYDAutoBodyworkDevice` | `getWindowOpenPercent` | `1,2,3,4` (4 lượt) | 0..100 mỗi kính | `bodywork/…:538` |
| R14 | `sunshade` | `BYDAutoBodyworkDevice` | `getSunroofWindowblindPosition` | — | 0..100 · STOP=254 | `bodywork/…:525`, `SUNSHADE_STOP` `:365` |
| R15 | `child_lock` | `BYDAutoDoorLockDevice` | `getDoorLockStatus` | `6`(L) `7`(R) | UNLOCK=1 · LOCK=2 | `doorlock/…:41`, area `:9-10` |
| R16 | `ambient_power` | `BYDAutoSettingDevice` | `getAirLightPanelState` | — | OPEN=1 · CLOSE=2 · FIRST_POWERON=3 | `setting/…:519`, enum `:8-11` |
| R17 | `ambient_color` | `BYDAutoSettingDevice` | `getIALColor` | — (và bản 1-arg `area`) | ICE_BLUE=1 · COLD_WHITE=2 · WARM_WHITE=3 · RED_ORANGE=4 · TRUE_RED=5 · RED_PURPLE=6 · DARK_BLUE=7 · OTHER=31 | `setting/…:710` + `:1537`, enum `:204-210` |
| R18 | `ambient_brightness` | `BYDAutoSettingDevice` | `getIALBrightness` | — (và `area`) | **0..5** | `setting/…:706` + `:1533`, `IAL_BRIGHTNESS_MIN/MAX` `:194-195` |
| R19 | `ambient_*` area | `BYDAutoSettingDevice` | `getIALArea` | — | FRONT=1 · BACK=2 · ALL=3 | `setting/…:702`, enum `:191-193` |
| R20 | `headlight_mode` | `BYDAutoSettingDevice` | `getLeftHeadlampLevel` / `getRightHeadlampLevel` | — | 1..11 · INVALID=0 | `setting/…:790`, `:954`, enum `:188-190` |
| R21 | `powertrain_mode` | `BYDAutoEnergyDevice` | `getEnergyMode` | — | STOP=0 · EV=1 · FORCE_EV=2 · HEV=3 · FUEL=4 · KEEP=5 | `energy/…:112`, enum `:18-23` |
| R22 | `regen_level` | `BYDAutoSettingDevice` | `getEnergyFeedback` | — | [CHƯA BIẾT] (nghi 1=std · 2=high) | `setting/…:656` |
| R23 | `wireless_charge` | `BYDAutoChargingDevice` | `getWirelessChargingSwitchState` | — | ON=1 · OFF=2 | `charging/…:385` |
| R24 | `screen_rotation` | — | **KHÔNG có getter** trên Setting | — | dùng `getid` `SET_PAD_ROTATION` | — |
| R25 | `camera_view` | `BYDAutoPanoramaDevice` | `getPanoOutputState` | — | OUTPUT_OFF=1 · FRONT=2 · REAR=3 · LEFT=4 · RIGHT=5 · COMPOSE=6 · F_WIDE=12 · R_WIDE=13 | `panorama/…:198`, enum `:92-105` |
| R26 | `camera_view` (đang hiện gì) | `BYDAutoPanoramaDevice` | `getDisplayMode` | — | PANORAMA=0 · FULL=1 · WIDGET=3 · RF_REV=4 · REV=5 · 3D=6 | `panorama/…:168`, enum `:46-51` |
| R27 | `hud_switch` | — | `getid` `SET_HUD_SWITCH_STATUS_FEEDBACK` | — | [CHƯA BIẾT] | `featmap`: 951058460 dev 1023 |
| R28 | `brightness_gear` | — | `getid` `SET_BRIGHTNESS_GEAR_SET` | — | [CHƯA BIẾT] thang | `featmap`: 1276174360 dev 1023 |
| R29 | `trunk` (đã có readKey, nhưng **id sai** — §6.6) | `BYDAutoSettingDevice` | `getBackDoorOpenedHeight` | — | 15..100 — **[ĐO xe 09-16] = 80 ✓** | `setting/…:567`, enum `:17-18`; `hal-reads.txt:17` |
| R30 | `cluster_music` | — | `getid` `INSTRUMENT_MUSIC_STATE_SET` **dev 1007** | — | [CHƯA BIẾT] | `featmap`: 1138753546 dev 1007 |

Ví dụ hai lệnh:

```bash
# R1 — khoá cửa 4 bánh + cốp (5 lượt)
for a in 1 2 3 4 5; do B --es cmd hal --es op get --es dev BYDAutoDoorLockDevice --es m getDoorLockStatus --es args $a; done
# R18 — độ sáng đèn viền (dải THẬT 0..5, repo đang khai 0..10)
B --es cmd hal --es op get --es dev BYDAutoSettingDevice --es m getIALBrightness
```

### 3.2 Năm datum `NEEDS_CAR` — **cả 5 đã tìm ra đường đọc** [ĐO source]

| datum | repo hiện tại | đường đọc TÌM RA | lệnh | ghi chú |
|---|---|---|---|---|
| `gps_lat` | `"NaviInfo.lat"` → route `None` | `getid` `LOCATION_LATITUDE_VALUE` = **311087**, dev **1017**; kèm `LOCATION_LATITUDE_TYPE` 847404 (NORTH=2/SOUTH=1) | `B --es cmd hal --es op getid --es dev BYDAutoLocationDevice --es m 311087` | ⚠ §3.3 |
| `gps_lon` | `"NaviInfo.lon"` | `LOCATION_LONGITUDE_VALUE` = **965600**, dev 1017; type 805436 (EAST=1/WEST=2) | như trên, `--es m 965600` | ⚠ §3.3 |
| `gps_elevation` | `"NaviInfo.elevation"` | `LOCATION_ALTITUDE` = **689157**, dev 1017 | `--es m 689157` | INVALID=8001.0, dải −8000..8000 (`location/…:8-10`) |
| `gps_heading` | `"NaviInfo.heading"` | `LOCATION_ORIENTATION` = **639440**, dev 1017 | `--es m 639440` | INVALID=360.0, dải 0..359 (`location/…:28-30`) |
| `target_soc` | `"SET_DR_SOC_TARGET"` → UPPER_SNAKE ⇒ route `None` | (a) named `BYDAutoSettingDevice.getSOCTarget()` `setting/…:966` · (b) `getid` `SET_DR_SOC_TARGET` = **873463848** dev 1023 | `--es op get --es dev BYDAutoSettingDevice --es m getSOCTarget` | dải 15..70 (`setting/…:339-340`) |

**`target_soc` — sửa được off-car ngay**: `bindingKey = "SET_DR_SOC_TARGET"` là UPPER_SNAKE nên `routeOf` trả `BindingRoute.None` (`HalBindingTable.kt:373-379`). Đổi thành `"BYDAutoFeatureIds.SET_DR_SOC_TARGET"` là vào đúng `BindingRoute.FeatureName`, hoặc đơn giản hơn: `"BYDAutoSettingDevice.getSOCTarget"`.

**Bốn datum GPS còn có đường thứ hai**: `BYDAutoLocationDevice.getLocationLongitudeLatitudeValue()` trả `double[]` (`location/…:65`) — một lệnh ra cả lat+lon.

### 3.3 ⚠ GPS là **quyết định của owner**, không phải việc của phiên code

Dự án đã **retire quyền location** (`DeadReckonRetirementTest` ghim manifest không xin `ACCESS_FINE_LOCATION`), và `LOCAL_TARGETS` cố ý không có `LocationManager` (`HalBindingTable.kt:246-250`).

Đường trên đây **KHÁC về cơ chế**: nó là getter HAL BYDAuto (quyền `BYDAUTO_*`), **không** dùng `LocationManager`, nên không bật lại quyền Android nào. Nhưng **về ý nghĩa thì vẫn là đọc vị trí xe** ⇒ đọc để đo là một chuyện, đưa vào ô hiển thị là chuyện khác. Sweep cứ đo (chỉ-đọc, vô hại); **nối vào UI chờ owner chốt**.

⛔ **TUYỆT ĐỐI KHÔNG bắn** `LOCATION_*_SET` / `setLocationInfo(...)` (`location/…:88`) — đó là đường **GHI vị trí VÀO xe** (họ mock-location đã bị gỡ khỏi dự án). Không có dòng nào trong ma trận này ghi vào device 1017.

---

## 4. KHỐI GHI — ma trận sweep

Quy ước cột: **Opt** = A/B/C (A fail thì chạy B ngay) · **giá trị ứng viên** = sweep **từng** giá trị, không đoán · **đọc-lại** = lệnh xác nhận (không có ⇒ dòng chưa PASS) · **outcome→kết luận** = đọc số ra thì kết luận gì.

### 4.A Khí hậu · đèn · ghế — đảo lại bằng một cú bấm, chạy trước

#### A1 · `ac_auto` (W3 họ hàng — điều hoà AUTO). Repo: feature `1324355606` = **KHÔNG CÓ trên xe** [ĐO source: `featmap` → `<NO NAME>`, `dev=[]`]

| Opt | device | method / id | giá trị ứng viên | đọc-lại | outcome→kết luận |
|---|---|---|---|---|---|
| **A** | `BYDAutoAcDevice` | `setAcControlMode` (2 arg) | thử **`0,0`** rồi **`0,1`**; nếu `bad_args`/rc null thì đảo thứ tự **`0,0`**/**`1,0`** | `--es op get --es m getAcControlMode` → AUTO=**0** · MANUAL=**1** | đọc lại đổi đúng ⇒ chốt arg-order + giá trị. `ac_auto` bỏ feature-id, dùng named-method |
| **B** | `BYDAutoAcDevice` | `setev` `AC_CTRL_MODE_SET` = **501219352** | `0` (AUTO) · `1` (MANUAL) | như trên | B xanh ⇒ đổi `bindingKey` sang `501219352`, giữ `readInverted` |
| **C** | `BYDAutoSettingDevice` | `setACAutoAir` | `1` (ECONOMY) · `2` (COMFORT) | `getACAutoAir` | ⚠ **KHÁC việc**: đây là *kiểu gió AUTO*, không phải bật/tắt AUTO. Chỉ ghi nhận, **không** map vào `ac_auto` |

Nguồn: `ac/BYDAutoAcDevice.java:442` (`setAcControlMode`), enum `:20-21`; `featmap` `AC_CTRL_MODE_SET`=501219352 dev 1000, `AC_CTRL_MODE`=1077936146 (đọc), `AC_HAS_AC_AUTO_MODE`=−846192616 (cờ có/không); `setting/…:1105` + enum `:317-318`; `CarSettings` `com/byd/ccs/impl/server/ac/Ac00600900120000.java:41` (`setACAutoAir(key+1)` ⇒ 1-based).
[ĐO xe 09-16] `getAcControlMode()` = **0** khi owner bấm AUTO, **1** khi tay (`hal-reads.txt:1`) ⇒ đường ĐỌC đã chốt, chỉ thiếu đường GHI.

#### A2 · `fan` / `temp` / `recirc` / `defrost` / `defrost_rear` — id ĐÚNG, thêm option named-method

[ĐO source] cả 5 feature-id tra ra đúng tên, dev **1000**: `501219340` `AC_WIND_LEVEL_SET` · `501219355` `AC_CYCLE_MODE_SET` · `501219362` `AC_DEFROST_FRONT_STATE_SET` · `501219357` `AC_DEFROST_REAR_STATE_SET`. `temp` đi named `setAcTemperature`.

| id | Opt A (đang chạy) | Opt B (named-method) | giá trị ứng viên | đọc-lại |
|---|---|---|---|---|
| `fan` | `setev` 501219340 = `0..7` | `setAcWindLevel` (2 arg) `<level>,0` rồi `<level>,1` (`AC_WINDLEVEL_MANUAL_SIGN_OFF=0/ON=1`) | 0,1,4,7 | `getAcWindLevel` — [ĐO xe] = 1 ✓ |
| `recirc` | `setev` 501219355 = `1` trong / `0` ngoài | `setAcCycleMode` (2 arg) | INLOOP=**1** · OUTLOOP=**0** | `getAcCycleMode` — [ĐO xe] = 1 ✓ |
| `defrost` | `setev` 501219362 = `0/1` | `setAcDefrostState` (**3 arg**) `1,<0|1>,0` (area FRONT=1) | ON=**1** · OFF=**0** | `getAcDefrostState 1` — [ĐO xe] = 0 ✓ |
| `defrost_rear` | `setev` 501219357 | `setAcDefrostState` `2,<0|1>,0` | ON=1 · OFF=0 | `getAcDefrostState 2` — [ĐO xe] = 0 ✓ |
| `temp` | `setAcTemperature 0,<°C>,0,1` | — | 17 · 22 · 33 (dải `AC_TEMP_IN_CELSIUS_MIN/MAX` 17..33) | `getTemprature 1` (area MAIN) |

Nguồn: `ac/BYDAutoAcDevice.java` — `setAcWindLevel:482` · `setAcCycleMode:446` · `setAcDefrostState:450` · `setAcTemperature:466`; enum `AC_WINDLEVEL_*:99-108` · `AC_CYCLEMODE_*:24-25` · `AC_DEFROST_*:28-31` · `AC_TEMP*:68-86`.
**Vì sao vẫn sweep khi cả 5 đã ✅ trên xe**: `defrost` báo ✅ ở lượt 09-15 nhưng đọc lại = 0; cần một lượt ghi→đọc-lại khép kín để biết ô có nói đúng trạng thái không.

#### A3 · `seatc` / `seath` — **điểm đo THỨ HAI cho thang mức** (ghế sưởi còn `[SUY]`)

[ĐO source] `setting/BYDAutoSettingDevice.java:302-309`: `SEAT_VENTILATING_OFF=1 · LOW=2 · HIGH=3` và `SEAT_HEATING_OFF=1 · LOW=2 · HIGH=3` (+ `SEAT_HEATING_LEVEL1_OFF=1 / LEVEL1_ON=2` `:303-304` cho biến thể `…State1`).

⚠ **Khung chỉ có 3 bậc cho CẢ HAI.** Repo hiện khai `"seath" to listOf(1, 2, 3, 4)` (`ControlLevels.kt`) = **4 bậc**, mâu thuẫn `SEAT_HEATING_HIGH=3`. `seatc` đã đúng `listOf(1,2,3)`.

| Opt | device | method | sweep | đọc-lại | outcome→kết luận |
|---|---|---|---|---|---|
| **A** | `BYDAutoSettingDevice` | `setSeatVentilatingState` | `1,1` → `1,2` → `1,3` → `1,4` | `getSeatVentilatingState 1` sau MỖI lượt + **owner đọc mức trên màn BYD** | raw 4 trả sentinel/không đổi ⇒ trim 2 mức ⇒ thang `[1,2,3]` CHỐT |
| **A′** | idem | `setSeatHeatingState` | `1,1` → `1,2` → `1,3` → `1,4` | `getSeatHeatingState 1` + màn BYD | **đây là điểm đo thứ hai còn thiếu**. Ra 3 bậc ⇒ sửa `ControlLevels` `seath` → `listOf(1,2,3)` |
| **B** | idem | `setSeatHeatingState1` (biến thể thứ hai) | `1,1` → `1,2` | `getSeatHeatingState1 1` | A′ fail mà B chạy ⇒ trim dùng biến thể `…State1` (2 trạng thái) |
| **C** | idem | `setev` per-seat | `SET_DRIVER_SEAT_VENTILATING_STATE_SET`=**1125122064** · heating=**1125122068**; ghế phụ vent=**1125122072** | đọc `SET_DRIVER_SEAT_VENTILATING_STATE`=1335885832 · heating=1335885835 | dùng khi named-method bị chặn |

Nguồn getter/setter: `setting/…:978` `getSeatHeatingState` · `:982` `getSeatHeatingState1` · `:986` `getSeatVentilatingState` · `:1445/:1449/:1453` ba setter. `featmap`: 4 cờ `SET_HAS_*_SEAT_VENTILATING` (−811597816 lái · −811597808 phụ · −933232632 sau-trái · −933232624 sau-phải) — **đọc cờ trước** để biết xe có ghế nào.
[ĐO xe 09-16] `getSeatVentilatingState(1)`=**3** khi màn hiện *"mức 2"*, `(2)`=3; `getSeatHeatingState(1)`=**1** khi TẮT (`hal-reads.txt:22-25`).

#### A4 · `readl` (đèn đọc) — on/off tay ❌ trên xe, chế-độ-theo-cửa ✅

| Opt | device | method / id | sweep | đọc-lại | outcome→kết luận |
|---|---|---|---|---|---|
| **A** | `BYDAutoSettingDevice` | `setev` `1330643002` (`SET_INSIDE_LIGHT_STATE_SET`, dev 1023 ✓) | `1` (OFF) · `2` (ON) | `getid` `1330643002` | id + enum đã đúng ⇒ nếu vẫn ❌ thì là **quyền/gate**, không phải giá trị |
| **B** | idem | `setInsideLightDoorState` | `1` (DOOR_OPEN) · `2` (DOOR_CLOSE) | `getInsideLightDoorState` | đây chính là nhánh owner báo **✅** ⇒ chốt: nút `readl` nên là **SELECT 3 trạng thái** (tắt/bật/theo-cửa), không phải TOGGLE |
| **C** | idem | `setILDuration` | [CHƯA BIẾT] thang | — | chỉ thăm dò |

Nguồn: enum `INSIGHT_LIGHT_OFF=1 / ON=2` `setting/…:218-219`; `INSIDE_LIGHT_DOOR_OPEN=1 / CLOSE=2` `:215-217`; setter `:1277`, `setILDuration:1269`, getter `:730`.

#### A5 · `drl` · `steer_heat` · `powertrain_mode` · `regen_level` · `wireless_charge` — enum đã chốt ở nguồn, chỉ xác nhận

| id | device | method | sweep | đọc-lại | nguồn |
|---|---|---|---|---|---|
| `drl` | `BYDAutoLightDevice` | `setDayTimeLightState` | OPEN=**1** · CLOSE=**2** | `getDayTimeLightState` | `light/…:209`, `:119` |
| `steer_heat` | `BYDAutoSettingDevice` | `setSteeringWheelHeatingState` | OFF=**1** · ON=**2** | `getSteeringWheelHeatingState` | `setting/…:1477`, `:1031`, enum `:407-409` |
| `powertrain_mode` | `BYDAutoEnergyDevice` | `setEnergyMode` | EV=**1** · HEV=**3**; (ứng viên thêm FORCE_EV=2 · FUEL=4 · KEEP=5) | `getEnergyMode` | `energy/…:173`, `:112`, enum `:18-23` |
| `regen_level` | `BYDAutoSettingDevice` | `setEnergyFeedback` | `1` · `2` (nghi std/high) · thử `0` | `getEnergyFeedback` | `setting/…:1225`, `:656` — **enum `[CHƯA BIẾT]`** |
| `wireless_charge` | `BYDAutoChargingDevice` | `setWirelessChargingSwitchState` | ON=**1** · OFF=**2** | `getWirelessChargingSwitchState` | `charging/…:458`, `:385` |
| `anion` | `BYDAutoPM2p5Device` | `setev` `1337982994` (`PM25_ANION_STATE_SET` dev 1008 ✓) | `1` · `2` · `0` | `getPM2p5AnionState` | `pm2p5/…:72`; `featmap` ✓ |
| `pm25` | `BYDAutoAcDevice` | `setAutoCleanAirState` | `1` · `0` | `getid` `AC_AUTO_CLEAN_AIR`=**1301291046** dev 1000 | ⚠ 1.38 đã biết: rc=0 mà **không lọc** ⇒ đọc lại BẮT BUỘC |
| `pm25_clean_now` | `BYDAutoAcDevice` | `setQuickCleanAirState` | `1` | nghe tiếng quạt + `getPM2p5Level` | [ĐO xe 09-15] ✅ |

### 4.B Thân xe — kính · nóc · rèm · cốp · khoá (cần người NHÌN, xe ĐỖ)

#### B1 · **W2 — kính ½ (và ½ cho TẤT CẢ kính)**: ba đường độc lập, cả ba đo được trong một buổi

Câu hỏi W2: *"có method set % / state trung gian không, hay phải dừng-giữa-hành-trình?"* → **[ĐO source] có CẢ BA.**

| Opt | cơ chế | device | method / id | giá trị ứng viên | đọc-lại |
|---|---|---|---|---|---|
| **A** | **enum nửa** | `BYDAutoBodyworkDevice` | `setBodyWindowCtrlState` (w, state) | `1,4` = `WINDOW_OPEN_HALF` (đủ 4 kính: `1,4` `2,4` `3,4` `4,4`) | `getWindowOpenPercent <w>` → **≈50** |
| **B** | **percent THẬT** (mới tìm ra) | `BYDAutoBodyworkDevice` | `setev` `BODYWORK_LF_WINDOW_TARGET_POSITION_SET` | `50`; rồi `25` · `75` để chứng minh là thang liên tục | `getWindowOpenPercent 1` → ≈ đúng số đã gửi |
| **C** | **dừng giữa hành trình** | `BYDAutoBodyworkDevice` | `setBodyWindowCtrlState` | `1,1` (mở) → đợi ~1 s → `1,3` = `WINDOW_STOP` | `getWindowOpenPercent 1` → số bất kỳ giữa 0 và 100 |
| **D** | 4 kính một lệnh | idem | `setAllWindowState` | `4,4,4,4` (nửa) · `1,1,1,1` (mở) · `2,2,2,2` (đóng) | đọc 4 lượt `getWindowOpenPercent` |

**Feature-id per-window** [ĐO source `featmap`, dev **1001**]:

| kính | TARGET_POSITION_SET (ghi %) | CTRL_SET (ghi enum) | PERCENT (đọc) |
|---|---|---|---|
| trước-trái LF | **1276219408** | 1125122104 | 947912728 |
| trước-phải RF | **1276219424** | 1125122107 | 1267728400 |
| sau-trái LR | **1276219416** | 1125122112 | 947912736 |
| sau-phải RR | **1276219432** | 1125122115 | 1267728408 |

```bash
# B1-A: nửa bằng enum, rồi đọc lại phần trăm
B --es cmd hal --es op set --es dev BYDAutoBodyworkDevice --es m setBodyWindowCtrlState --es args 1,4 --ez auto_confirm true
B --es cmd hal --es op get --es dev BYDAutoBodyworkDevice --es m getWindowOpenPercent --es args 1
# B1-B: percent thật 50 → 25 → 75
for p in 50 25 75; do
  B --es cmd hal --es op setev --es dev BYDAutoBodyworkDevice --es m 1276219408 --es args $p --ez auto_confirm true
  B --es cmd hal --es op get  --es dev BYDAutoBodyworkDevice --es m getWindowOpenPercent --es args 1
done
```

**outcome→kết luận**:
- A xanh (≈50) ⇒ `writeArgs` hiện tại (mức 2 → 4) **đúng**, đóng W2 mà không sửa gì; `windows_all` mức Nửa = `4,4,4,4` cũng chốt.
- A câm mà B xanh ⇒ nửa phải đi đường **percent**; đổi `win_*` mức 2 sang `setev TARGET_POSITION_SET=50` (và mở/đóng vẫn giữ enum 1/2).
- A+B câm, C xanh ⇒ nửa chỉ làm được bằng **mở rồi STOP**; đây là thay đổi **kiến trúc** (nút phải giữ timer) ⇒ ghi backlog, **không** tự làm.
- Cả ba câm ⇒ đọc `getWindowPermitState` (`bodywork/…:542`): xe **chặn** điều khiển kính từ app ⇒ dừng, gỡ nhãn "Nửa".

Nguồn enum: `bodywork/BYDAutoBodyworkDevice.java:372-381` — `WINDOW_ENABLE=0 · DISABLE=1 · CLOSE=2 · OPEN_FULL=1 · STOP=3 · OPEN_HALF=4 · BREATH=5 · PERCENT_MIN=0 · PERCENT_MAX=100`; setter `:575` `setAllWindowState`, `:579` `setBodyWindowCtrlState`; getter `:538` `getWindowOpenPercent`, `:542` `getWindowPermitState`, `:546` `getWindowState`.
⚠ Chú ý `WINDOW_ENABLE=0` **=** `WINDOW_INVALID=0` — đó là lý do lượt 09-15 "mở được, đóng không": mức Đóng gửi `0`.

#### B2 · ⚠ `sunroof` — **BUG chứng minh được KHÔNG CẦN XE**: đang gửi "mở 1%" thay vì "đóng"

Repo: `"sunroof" -> intArrayOf(if (primary > 0) 1 else 2)` (`HalBindingTable.kt`), chú thích viện dẫn *"cùng enum kính mở=1/đóng=2 (OpenBYD CarControlImpl.java:1503-1505)"*.

**Cả hai vế của chú thích đó đều sai** [ĐO source]:

1. `CarControlImpl.setWindow(windowId, state)` là **hàm chuyển tiếp thuần** — `jadx-openbyd/sources/com/sr/openbyd/proxy/CarControlImpl.java:1488-1520`: `else if (i == 5) { … setMoonRoofState(i2) }`, `i2` là tham số từ RPC. Nó **không chứng minh** giá trị nào cả; trích dẫn này là `[SUY]` bị ghi thành `[ĐO]`.
2. Nóc **không dùng** enum kính. `bodywork/BYDAutoBodyworkDevice.java:338-344`: `MOONROOF_BREATH=253 · CLOSED=0 · COMFORTABLE=252 · INVALID=255 · MIN=21 · OPEN=100 · STOP=254` ⇒ **thang PHẦN TRĂM**.
3. Xác nhận bằng chính UI của BYD — `carsettings-apk/jadx-carsettings/sources/com/byd/vehiclesettings/sunroof/fragment/SunRoofFragment.java`: `:775` `setSunRoofState(0)` · `:781` `setSunRoofState(mSunRoofPercentageState)` ← **biến phần trăm** · `:787` `(100)` · `:794` `(252)` · `:801` `(253)` · `:882` `(254)` · `:1109` `(255)`; đổ xuống `model/SunRoofModel.java:127` `setMoonRoofState(i)`.
4. Chốt thêm: `mSunRoofPercentageState` khai `private static int` `:51`, nạp từ `getSunRoofState()` `:355`, và UI so nó với **`21` · `75` · `90` · `100`** (`:384-385`) rồi đẩy qua `transformPercentage()` vào một **SeekBar**. Ngưỡng `>= 21` khớp đúng `MOONROOF_MIN=21` (`bodywork/…:342`) ⇒ thang phần trăm 0..100 **và** sàn 21 đều là hành vi thật, không phải suy diễn.

⇒ Hôm nay bấm "Cửa sổ trời" **mở** gửi `1` = **hé 1%**; bấm **đóng** gửi `2` = **hé 2%** (không phải đóng). Đúng họ lỗi rèm che nắng đã đo trên xe 09-15 (*"bấm mở CHÚT XÍU"*).

| Opt | method | giá trị ứng viên | đọc-lại | kết luận |
|---|---|---|---|---|
| **A** | `setMoonRoofState` | `0` (đóng) · `50` (nửa) · `100` (mở) · `254` (stop) · `252` (comfort) · `253` (breath) | `getSunroofPosition` + `getSunroofState` + **owner NHÌN nóc** | xanh ⇒ `writeArgs` `sunroof` → 0/100 (+ nửa = 50) |
| **B** | `setMoonRoofAndSunshadeStop` (0 arg) | — | như trên | đường dừng đồng thời nóc + rèm (`bodywork/…:583`) |
| — | ⚠ `MOONROOF_MIN=21` | thử `10` (< MIN) | `getSunroofPosition` | dưới 21 có bị bỏ qua không ⇒ biết sàn thật |

⚠ **`sunroof_pos` cũng sai đơn vị**: `TelemetryRegistry.kt:297` khai unit `"%"` cho `getSunroofPosition`, nhưng getter đó trả **enum** (`SUNROOF_POSITION_FULL_OPEN=1 · FULL_CLOSE=2 · HALF_OPEN=3 · STOP=4 · UPDIP=5 · COMFORTABLE=6 · INVALID=0`, `bodywork/…:356-361`) ⇒ nóc hé nửa sẽ hiện **"3 %"**. Sweep đọc cả `getSunroofPosition` và `getSunroofWindowblindPosition` để biết cái nào là % thật.

#### B3 · `sunshade` (rèm) — repo đã đi percent; xác nhận + thêm STOP

[ĐO source] `1330642984` = `BODYWORK_SUNSHADE_PANEL_PERCENT_SET` dev 1001 ✓; đọc `1101004816` = `BODYWORK_SUNSHADE_PANEL_PERCENT` ✓. CarSettings dùng `setSunshadeState(0/100/254)` — `SunRoofFragment.java:867` · `:875` · `:886`.

| Opt | method / id | sweep | đọc-lại |
|---|---|---|---|
| **A** (đang chạy) | `setev` `1330642984` | `0` · `50` · `100` | `getid` `1101004816` |
| **B** | named `setSunshadeState` (`bodywork/…:595`) | `0` · `50` · `100` · **`254`** (STOP — chữa đúng *"bấm giữ chưa được"* 09-15) | `getSunroofWindowblindPosition` (`bodywork/…:525`) |

#### B4 · `trunk` (cốp) — mở/đóng đã ✅; thêm **mở-theo-chiều-cao** (mới tìm ra)

[ĐO xe 09-17] `voiceCtlBackDoor`: **1** = mở · **3** = đóng; `2` không thấy tác dụng khi cốp đứng yên.
[ĐO source] `bodywork/BYDAutoBodyworkDevice.java` **không có** `setHetchDoorStatus` trên DL3 (chỉ có ở `jadx-openbyd/.../BYDAutoBodyworkDevice.java:1163`) — khớp `NoSuchMethod` đã ghi.

| Opt | device | method / id | sweep | đọc-lại |
|---|---|---|---|---|
| **A** (đang chạy) | `BYDAutoSettingDevice` | `voiceCtlBackDoor` (`setting/…:1529`) | `1` mở · `3` đóng · **`2` và `4` lúc cốp ĐANG chạy** (kiểm giả thuyết STOP) | `getBackDoorOpenedHeight` |
| **B** mới | `BYDAutoSettingDevice` | `setBackDoorOpenedHeight` (`setting/…:1157`) | `15` (MIN) · `50` · `80` · `100` (MAX) | `getBackDoorOpenedHeight` — [ĐO xe] đọc **80** ✓ |
| **C** | idem | `setev` `SET_BACK_DOOR_OPEN_HEIGHT_SET` = **1069547584** dev 1023 | như B | `getid` `SET_BACK_DOOR_OPEN_HEIGHT` = **1074790408** |
| **D** | idem | `setev` `SET_VOICE_CTRL_BACK_DOOR_SET` = **1125122080** | `1` · `3` | như A |

Dải `BACK_DOOR_OPENED_HEIGHT_MIN=15 · MAX=100` (`setting/…:17-18`). ⇒ **cốp mở nửa làm được** (chưa có nút nào dùng).
⚠ `getBackDoorElectricMode()` = **65535** = `DEVICE_THE_FEATURE_LINK_ERROR` (`bodywork/…` hằng cùng họ) [ĐO xe 09-16 `hal-reads.txt:15`] ⇒ chế-độ-cốp-điện **không đọc được**, đừng nối.

#### B5 · `lock` / `door` / `child_lock` — `BYDAutoDoorLockDevice` **không có setter nào**

[ĐO source] `doorlock/BYDAutoDoorLockDevice.java` chỉ có `getDoorLockStatus(int)` `:41`. ⇒ `bindingKey = "BYDAutoDoorLockDevice.setDoorLockState"` của `lock`/`door` **chắc chắn `NoSuchMethod`**.

| Opt | device | method / id | sweep | đọc-lại |
|---|---|---|---|---|
| **A** mới | `BYDAutoSettingDevice` | `setDoorLock` (`setting/…:1201`) | `1` (UNLOCK) · `2` (LOCK) — theo `DOOR_LOCK_STATE_*` `:20-22` | `getDoorLockStatus 1..4` (DoorLock dev) + `getDoorLock` (Setting) |
| **B** | `BYDAutoSettingDevice` | `setev` `SET_CAR_DOOR_LOCK_SET` = **515647** dev 1023 | `1` · `2` | như A; đọc `SET_CAR_DOOR_LOCK` = **190493** |
| **C** | `BYDAutoDoorLockDevice` | `setev` per-door: LF **960495668** · LR 960495672 · RF 960495670 · RR 960495674 · BACK 960495676 (dev **1041**) | `1` (unlock) · `2` (lock) | `getDoorLockStatus <area>` |
| **D** `child_lock` | `BYDAutoDoorLockDevice` | `setev` `DOOR_LOCK_COMMAND_AREA_CHILDLOCK_LEFT_SET` = **1276141584** · RIGHT_SET = **1276141586** (dev 1041) | `1` · `2` | `getDoorLockStatus 6` / `7` |

⚠ **An toàn**: `door` là nút MỞ KHOÁ một chiều. Sweep khoá/mở khoá **chỉ khi xe đỗ, người ngồi trong, cửa đóng**. Mở khoá rồi **khoá lại ngay** cuối mỗi lượt.
⚠ `hood` (ca-pô): `BODYWORK_CMD_DOOR_HOOD` chỉ là **area ĐỌC**, không có lệnh mở ⇒ **không sweep**; đọc `getDoorState` nếu có area ca-pô, còn lại giữ `NEEDS_CAR`.

### 4.C Khối còn treo — ambient · camera (W5) · HUD · gạt mưa (W2 phần bảo trì)

#### C1 · **W2 — bảo trì gạt mưa**: đã tìm ra ĐÚNG đường, có cả call-site của BYD

[ĐO source] Đây là tính năng **"đại tu / overhaul"** — nằm ở **Setting**, không ở Wiper device (`BYDAutoWiperDevice` chỉ có **3 getter, 0 setter**: `wiper/BYDAutoWiperDevice.java:68,:72,:76`).

- Setter: `BYDAutoSettingDevice.setWindscreenWiperOverhaulState(int area, int state)` — `setting/BYDAutoSettingDevice.java:1513`
- Getter: `getWindscreenWiperOverhaulState(int area)` — `setting/…:1060`
- area: `FRONT_WINDSCREEN_WIPER=1` (`:183`) · `REAR_WINDSCREEN_WIPER=2` (`:281`)
- state: `WINDSCREEN_WIPER_OVERHAUL_OPEN=1` (vào vị trí bảo trì) · `CLOSE=2` (thoát) · `INVALID=0` · `UNALLOWED_OVERHAUL=3` (`:479-482`)
- **Call-site THẬT của BYD** (chốt thứ tự tham số): `carsettings-apk/jadx-carsettings/sources/com/byd/vehiclehealth/repair/frontwiperrepair/FrontWiperRepairModel.java:61` → `setWindscreenWiperOverhaulState(1, z ? 1 : 2)`; bản sau: `…/rearwiperrepair/RearWiperRepairModel.java:53` → `(2, z ? 1 : 2)`.

| Opt | device | method / id | sweep | đọc-lại | kết luận |
|---|---|---|---|---|---|
| **0** | `BYDAutoSettingDevice` | `getid` `SET_HAS_FRONT_WINDSCREEN_WIPER_OVERHAUL` = **−951058404** | — | — | **chạy TRƯỚC**: xe có tính năng này không |
| **A** | `BYDAutoSettingDevice` | `setWindscreenWiperOverhaulState` | `1,1` (vào) → NHÌN cần gạt dựng lên → `1,2` (thoát) | `getWindscreenWiperOverhaulState 1` | xanh ⇒ nút mới *"Bảo trì gạt mưa"*, đóng W2 |
| **B** | idem | `setev` `SET_FRONT_WINDSCREEN_WIPER_OVERHAUL_STATE_SET` = **1330642972** (sau = **1330642974**) | `1` · `2` | `getid` `SET_FRONT_WINDSCREEN_WIPER_OVERHAUL_STATE` = **1196425244** | dùng nếu named bị chặn |
| **C** | idem | `setAutoRainWiperState` (`setting/…:1145`) | ON=**1** · OFF=**2** (`:14-16`) | `getAutoRainWiperState` — [ĐO xe] = 1 ✓ | đây là *gạt tự động theo mưa*, **khác** bảo trì |

⚠ **`wiper` (nút "Gạt mưa" hiện có) route SAI device**: `321912848` thuộc **1046 Wiper**, `Domain.BODY` đoán ra Bodywork 1001. Sweep: `--es dev BYDAutoWiperDevice --es m 321912848` (`setev`, giá trị 0..4 — thang `WIPER_FRONT_WIPER_LEVEL` `[CHƯA BIẾT]`).
⚠ **`getWindscreenWiperResetState(1)` trả `-10011`** ổn định 22/22 mẫu ([ĐO xe] `wiper-poll.txt`) — **không** phải sentinel của dự án (`-2147482648`/`-2147482645`), **không** có trong stub. `[CHƯA BIẾT]` ⇒ sweep đọc lại kèm `--es dev BYDAutoWiperDevice` để loại giả thuyết sai-device.

#### C2 · Đèn viền cabin (4 nút) — **cả 4 feature-id đang dùng đều KHÔNG CÓ trên xe**

[ĐO source `featmap`] `1276153924` · `1276194864` · `1276194858` · `489701407` → **`<NO NAME>`, `dev=[]`** (cả 4). ⇒ 4 nút này chưa bao giờ chạm được xe.

Id THẬT + named-method (tất cả dev **1023 Setting**):

| nút | named-method (Opt A) | feature-id THẬT (Opt B) | giá trị ứng viên | đọc-lại |
|---|---|---|---|---|
| `ambient_power` | `setAirLightPanelState` (`setting/…:1125`) | `SET_ATMOSPHERE_LAMP_PANEL_STATE_SET` = **1276153872** | OPEN=**1** · CLOSE=**2** · FIRST_POWERON=3 (`:8-11`) | `getAirLightPanelState`; hoặc `getid` `SET_ATMOSPHERE_LAMP_PANEL_STATE` = **993001480** |
| `ambient_brightness` | `setIALBrightness(area, value, src)` (`setting/…:1549`) | `SET_ATMOSPHERE_LAMP_BRIGHTNESS_SET` = **653629** | area `1`/`2`/`3`; value **0..5**; src `0`=UI → `3,0,0` · `3,3,0` · `3,5,0` | `getIALBrightness` / `getIALBrightness(area)` (`:706`/`:1533`) |
| `ambient_color` | `setIALColor(area, color, src)` (`setting/…:1553`) | RGB **riêng 3 id**: R=**787480584** · G=**787480592** · B=**561293** | color **1..7** (+31); `3,1,0` … `3,7,0` | `getIALColor` / `(area)` (`:710`/`:1537`) |
| `ambient_music` | — **không tìm ra setter** | chỉ có `SET_ATMOSPHERE_LAMP_LINKAGE_SETTING_FEEDBACK` = **1121976383** (đọc) | — | `[CHƯA BIẾT]` — đọc cờ feedback, đừng đoán đường ghi |

**Call-site THẬT của BYD** (chốt thứ tự + off-by-one):
`carsettings-apk/.../com/byd/vehiclesettings/outsidelight/view/AmbientLightColor.java:109` → `setIALColor(ambientAreaValue, i + 1, 0)` — **màu là 1-based**, `i` là index UI, `0` = `IAL_CTRL_SOURCE_UI` (`setting/…:202`).
`.../AmbientBrightness.java:100` → `setIALBrightness(ambientAreaValue, i, 0)` — **độ sáng KHÔNG +1**.

⚠ Hai sai lệch của repo, chứng minh được off-car:
1. `ambient_brightness` khai `min=0 max=10`; dải thật **0..5** (`IAL_BRIGHTNESS_MIN/MAX` `setting/…:194-195`) ⇒ nửa trên thanh trượt vô nghĩa.
2. `ambient_color` khai 5 nhãn `["Tím","Xanh dương","Xanh lá","Vàng","Trắng"]` gửi index **0..4**. Bảng thật 7 màu **1..7**: ice-blue · cold-white · warm-white · red-orange · true-red · red-purple · dark-blue (`:204-210`) — **không có xanh lá, không có vàng**, và `0` = `IAL_COLOR_INVALID`. ⇒ nhãn hiện đang hứa màu xe không có, và giá trị đầu là INVALID.
3. Bốn **datum** IAL thì id ĐÚNG (`SET_IAL_FRONT_COLOR`=1121976336 · `BACK_COLOR`=1121976343 · `FRONT_BRIGHTNESS`=1121976328 · `BACK_BRIGHTNESS`=1121976332, dev 1023) nhưng `Domain.LIGHTS` route sang Light 1004 ⇒ chỉ thiếu `halDevice`.

#### C3 · **W5 — camera 360 / góc camera**

[ĐO source] `BYDAutoPanoramaDevice` **không có `setDisplayMode`** — chỉ `getDisplayMode()` (`panorama/…:168`) + hai setter `setAPAAvmMode` (`:253`) · `setAPATransparentSwitch` (`:257`). ⇒ `camera_view` → `BYDAutoPanoramaDevice.setDisplayMode` là **route chết**.

| nút | Opt | device | method / id | giá trị ứng viên | đọc-lại |
|---|---|---|---|---|---|
| `cam` | **A** (đang chạy) | `BYDAutoADASDevice` | `setAVMSwitchState` | OFF=**1** · ON=**2** (`adas/…:34-35`, setter `:348`) | `getPanoWorkState` (`panorama/…:214`) |
| `cam` | **B** | `BYDAutoPanoramaDevice` | `setev` `PANORAMA_WORK_MODE` = **1329598484** *(stub; xác nhận bằng `featmap`)* | WORK_OFF=**0** · ON=**1** (`panorama/…:128-129`) | `getid` `PANORAMA_WORK_STATE` = 1329598488 |
| `camera_view` | **A** mới | `BYDAutoPanoramaDevice` | `setev` `PANORAMA_OUTPUT_STATE_SET` = **1306529808** (dev **1031**) | OFF=**1** · FRONT=**2** · REAR=**3** · LEFT=**4** · RIGHT=**5** · COMPOSE=**6** · FRONT_WIDE=**12** · REAR_WIDE=**13** (`panorama/…:92-105`) | `getPanoOutputState` (`:198`); hoặc `getid` `PANORAMA_OUTPUT_STATE` = **1329598480** |
| `camera_view` | **B** | `BYDAutoPanoramaDevice` | `setAPAAvmMode` | `[CHƯA BIẾT]` enum; thử `1`..`6` | `getid` `PANORAMA_APA_AVM_MODE` = 1306529862 |
| `camera_view` | **C** | idem | `setev` `PANORAMA_OPERATION_SET` = **180979** | `[CHƯA BIẾT]` | `getPanoWorkState` |

⇒ **Opt A giải xong TODO của repo** (*"map nhãn↔enum chưa chốt"*): 5 nhãn `Trước/Sau/Trái/Phải/Rộng` khớp đúng `FRONT=2 · REAR=3 · LEFT=4 · RIGHT=5 · FRONT_WIDE=12` — hết phải gửi index thô.
⚠ Camera bật là **màn hình đổi nội dung** khi xe đỗ; chạy cuối buổi, chụp màn mỗi bước.

#### C4 · HUD — `hud_brightness` phải đổi nghĩa hoặc gỡ (xem §0.3)

| nút | Opt | method / id (dev 1023) | giá trị ứng viên | đọc-lại |
|---|---|---|---|---|
| `hud_switch` | A (đang chạy) | `setev` `SET_HUD_SWITCH_SET` = **1276174371** ✓ tên đúng | `0` · `1` · `2` | `getid` `SET_HUD_SWITCH_STATUS_FEEDBACK` = **951058460** |
| `hud_brightness` | **A** | `setev` `SET_HUD_MODE_SET` = **1276174373** | `0`..`3` (thang `[CHƯA BIẾT]`) | `getid` `SET_HUD_MODE_FEEDBACK` = **951058445** |
| — | B | `getid` `SET_HUD_CONFIG` = **951058453** | — (chỉ đọc) | biết xe có HUD zin hay không |
| `brightness_gear` | A (đang chạy) | `setev` `SET_BRIGHTNESS_GEAR_SET` = **1276174360** ✓ | `0` · `5` · `10` | `getid` cùng id; **NHÌN màn có đổi sáng** |
| — | ⚠ | `BYDAutoInstrumentDevice.setBacklightBrightness` (`instrument/…:1728`) | `0` · `5` · `10` | độ sáng **CỤM**, khác màn chính — ứng viên cho một nút MỚI |

**outcome→kết luận**: `SET_HUD_MODE_SET` xanh ⇒ đổi `hud_brightness` thành SELECT *"Chế độ HUD"*. Câm ⇒ **gỡ** nút (hiện nó đang lặng lẽ đổi độ sáng màn chính = bug đang chạy).

#### C5 · `screen_rotation` · `cluster_music` · `seat_memory` — id đúng, giá trị/route sai

| nút | [ĐO source] | sai ở đâu | sweep |
|---|---|---|---|
| `screen_rotation` | `1330643005` = `SET_PAD_ROTATION_SET` dev 1023 ✓ | repo gửi **index 0/1**; enum thật `ROTATION_HORIZONTAL=1 · VERTICAL=2` (`setting/…:300-301`) ⇒ `0` là giá trị không có | `setev` `1` rồi `2`; hoặc named `setPadRotation` (`:1401`). Đọc lại: `getid` `SET_PAD_ROTATION` (tra `featmap`) — **không có getter named** |
| `cluster_music` | `1138753546` = `INSTRUMENT_MUSIC_STATE_SET` dev **1007** | route sang Setting 1023 | `setev --es dev BYDAutoInstrumentDevice --es m 1138753546 --es args 0/1/2` |
| `seat_memory` | `1276186678` = `Setting.SET_LF_MEMORY_LOCATION_SET` dev 1023 ✓ + `halDevice` ✓ | giá trị `[CHƯA BIẾT]` | `setev` `1` · `2` · `3` (vị trí nhớ) — ⚠ ghế **di chuyển**, không ai ngồi ghế lái lúc bắn |

#### C6 · Ứng viên MỚI tìm được (chưa có nút) — chỉ đọc-thăm-dò, đừng làm nút trong buổi này

[ĐO source] `setting/BYDAutoSettingDevice.java` — `setMassageLevel(int,int)` `:1349` / `setMassageMode(int,int)` `:1353`, đọc `:834`/`:838`; enum `MASSAGE_LEVEL_INTENSITY1..3=1..3` · `MASSAGE_MODE_CLOSE=1 · WAVE=2 · PULSE=3 · STRETCH=4 · WAIST=5 · SHOULDER=6` (`:247-257`); `featmap` có id per-seat (`SET_FRONT_LEFT_SEAT_MASSAGE_LEVEL`=1335885876 · `_MODE`=1335885872 · `_CONFIG`=1335885879, dev 1023).
⇒ **Ghế massage** là một khả năng xe có mà launcher chưa hề có. Buổi này chỉ `getid` `SET_FRONT_LEFT_SEAT_MASSAGE_CONFIG` để biết xe owner có hay không; làm nút = quyết định owner.

---

## 5. Tám lỗi **chứng minh được NGAY off-car** — vá trước khi lên xe thì buổi sweep sạch hơn

Không cái nào cần xe để biết là sai; chúng sai vì **mâu thuẫn với nguồn**. Vá trước ⇒ sweep đo được thứ mình muốn đo, không đo lại lỗi cũ.

| # | id | Hiện tại | Sai vì | Đúng là | Mức |
|---|---|---|---|---|---|
| 1 | `sunroof` | `1` mở / `2` đóng | thang PERCENT (`MOONROOF_OPEN=100 · CLOSED=0 · STOP=254`, `bodywork/…:338-344`) + CarSettings gửi `0/percent/100/252/253/254` (`SunRoofFragment.java:775-1109`) | `0` đóng · `100` mở · `50` nửa · `254` stop | **P1** — hiện "đóng" = hé 2% |
| 2 | `hud_brightness` | `1276174360` | = `SET_BRIGHTNESS_GEAR_SET` — **trùng `brightness_gear`**, là độ sáng **màn chính**; họ HUD không có id brightness | `SET_HUD_MODE_SET`=1276174373 (đổi thành *Chế độ HUD*) hoặc gỡ | **P1** — đang đổi sai thứ |
| 3 | `ambient_brightness` | `min=0 max=10` | `IAL_BRIGHTNESS_MAX=5` (`setting/…:194-195`) | `0..5` | **P2** |
| 4 | `ambient_color` | 5 nhãn, index `0..4` | bảng thật 7 màu **1..7**, `0`=INVALID; không có xanh-lá/vàng (`setting/…:204-210`) | 7 nhãn 1..7, +1 như CarSettings (`AmbientLightColor.java:109`) | **P2** — nhãn hứa màu xe không có |
| 5 | `screen_rotation` | index `0/1` | `ROTATION_HORIZONTAL=1 · VERTICAL=2` (`setting/…:300-301`); `0` không tồn tại | `1` / `2` | **P2** |
| 6 | `tailgate_position` (datum) | `1074790456` | **không có trên xe** (`featmap` → `dev=[]`); id thật `SET_BACK_DOOR_OPEN_HEIGHT` = **1074790408** (lệch 48) | `1074790408` hoặc named `getBackDoorOpenedHeight` ([ĐO xe] = 80 ✓) | **P2** |
| 7 | `seath` thang mức | `listOf(1,2,3,4)` | `SEAT_HEATING_HIGH=3` (`setting/…:302-306`) — khung chỉ 3 bậc | `listOf(1,2,3)` — **chốt bằng A3′** | **P2** |
| 8 | `sunroof_pos` (datum) | unit `"%"` | `getSunroofPosition` trả **enum** 0..6 (`bodywork/…:356-361`) | bỏ `%`, map enum → chữ; `%` thật lấy từ `getSunroofWindowblindPosition` | **P2** — hé nửa hiện "3 %" |

Thêm 4 dòng chỉ thiếu `halDevice` (không đổi id): `wiper`→Wiper · `cluster_music`→Instrument · `mirror_fold`→1047 · 4 datum IAL→Setting (§2).

⚠ **Không sửa `readl` / `lock` / `door` / `camera_view` / `ac_auto` / 4 ambient trước khi sweep** — chúng cần một phép đo để chọn giữa các option, sửa trước là đoán.

---

## 6. Mẫu ghi kết quả (dán vào đây trên xe)

```
# HAL sweep — ngày ____ · bản Kachi ____ · featmap stamp ____ (diff vs 20260916: ____)
# id | opt | lệnh | rc | value đọc lại | owner NHÌN thấy | PASS/FAIL | kết luận
R1  lock-read   | -- | getDoorLockStatus 1..5     | -- | ____ | --              | ____ |
A1  ac_auto     | A  | setAcControlMode 0,0       | __ | ____ | AUTO sáng?      | ____ |
A3' seath       | A′ | setSeatHeatingState 1,2    | __ | ____ | màn BYD mức?    | ____ |
B1  win ½       | A  | setBodyWindowCtrlState 1,4 | __ | ____ | kính xuống nửa? | ____ |
B1  win ½       | B  | setev 1276219408 = 50      | __ | ____ | kính xuống nửa? | ____ |
B2  sunroof     | A  | setMoonRoofState 0/50/100  | __ | ____ | nóc chạy?       | ____ |
B5  lock        | A  | setDoorLock 2 rồi 1        | __ | ____ | tiếng khoá?     | ____ |
C1  wiper-bảo-trì| A | setWindscreenWiperOverhaulState 1,1 | __ | ____ | cần dựng? | ____ |
C2  ambient     | A  | setAirLightPanelState 1/2  | __ | ____ | đèn viền?       | ____ |
C3  camera_view | A  | setev 1306529808 = 2/3/4/5 | __ | ____ | góc đổi?        | ____ |
C4  hud_mode    | A  | setev 1276174373           | __ | ____ | HUD đổi?        | ____ |
```

**Ưu tiên nếu hết thời gian** (cắt từ dưới lên): §3 khối đọc → B1 kính ½ → A3′ thang ghế sưởi → C1 bảo trì gạt mưa → B2 nóc → A1 ac_auto → B5 khoá → C2 ambient → C3 camera → C4 HUD → C6 massage.

---

## 7. Câu hỏi còn mở `[CHƯA BIẾT]` — ghi ra để không giả vờ đã biết

1. **`featmap` 09-16 còn đúng với xe hôm nay?** Hằng feature-id gán lúc chạy theo `isCanFD`/`isToyota`; OTA có thể đổi. ⇒ chạy lại `featmap` + `diff`.
2. **`-10011`** của `getWindscreenWiperResetState` là gì — không phải sentinel dự án, không có trong stub.
3. **Thứ tự tham số** của `setAcControlMode` / `setAcWindLevel` / `setAcCycleMode` (2 arg) — không tìm được call-site nào trong 6 cây nguồn. Sweep phải thử cả hai chiều.
4. **Thang `WIPER_FRONT_WIPER_LEVEL`** (321912848) — mấy mức, giá trị nào.
5. **Enum `setEnergyFeedback`** (`regen_level`) và **`SET_HUD_MODE_SET`** — chưa có bảng giá trị.
6. **`ambient_music`** — không tìm ra id/method GHI nào; chỉ có một cờ FEEDBACK.
7. **`headl` (đèn pha on/off)** — xe **không có** id nào cho bật/tắt đèn pha: chỉ `INSTRUMENT_HEADLIGHT_CONTROL_SET` (mode, dev 1007) và `SET_CMD_HEADLAMP_HEIGHT_LEVEL_SET`=1125122084 (chiều cao 1..11). ⇒ khả năng cao **không làm được**, giữ `NEEDS_CAR`; nút nên đổi nghĩa thành *mode* hoặc *height*.
8. **4 datum nhiệt lốp** — id ĐÚNG (`INSTRUMENT_2IN1_*_TYRE_TEMPERATURE` 1246797848/60/72/84, dev **1007**) và `Domain.TYRES`→Instrument(1007) **cũng đúng** ⇒ chưa rõ vì sao vẫn `NEEDS_CAR`. Một lượt `getid` là chốt được.
9. **GPS**: đọc được (§3.2) nhưng **nối vào UI hay không là quyết định owner** (§3.3).
10. **`rc=0` ≠ xe làm** — mọi dòng PASS trong buổi này chỉ tính khi có **đọc-lại** hoặc **owner NHÌN**.

---

## 8. Nguồn (đã đọc, có `file:line`)

**Dữ liệu XE THẬT (mạnh nhất)**
- `docs/diagnostics/oncar-trace-2026-09-16b/featmap-20260916.json` — 7193 tên→id, 47 device, stamp `20260916-185942`. Nguồn của mọi id trong doc này.
- `docs/diagnostics/oncar-trace-2026-09-16b/hal-reads.txt` — 30 lượt đọc getter thật (dòng 1,13,15,17,22-25 được trích).
- `docs/diagnostics/oncar-trace-2026-09-16b/wiper-poll.txt` — 22 mẫu relay/reset.
- `docs/diagnostics/carlog-0916/{sweep-1.64.json, device-features-map.tsv, hal-feature-not-in-device.txt, ctl-round.txt}`.

**Stub HAL DL3** — `<byd-workspace>/jadx-tmap/sources/android/hardware/bydauto/`
- `bodywork/BYDAutoBodyworkDevice.java` — WINDOW `:372-381` · MOONROOF `:338-344` · SUNROOF_POSITION `:356-361` · SUNSHADE_STOP `:365` · VOICE_CMD `:367-371` · getter `:437,:450,:476,:488,:509-546` · setter `:575-595`
- `setting/BYDAutoSettingDevice.java` — AIR_LIGHT `:8-11` · AUTO_RAIN_WIPER `:14-16` · BACK_DOOR_HEIGHT `:17-18` · HEADLAMP `:188-190` · IAL `:191-210` · INSIDE_LIGHT/INSIGHT `:215-219` · MASSAGE `:247-257` · ROTATION `:300-301` · SEAT `:302-309` · AC_AUTO_AIR `:317-321` · SOC_TARGET `:339-340` · STEER_HEAT `:407-409` · WIPER_OVERHAUL `:479-482` · getter `:499-1060` · setter `:1105-1553`
- `ac/BYDAutoAcDevice.java` — CTRLMODE `:20-21` · CYCLEMODE `:24-25` · DEFROST `:26-31` · TEMP `:68-86` · WINDLEVEL `:99-108` · WINDMODE `:109-117` · setter `:438-514`
- `wiper/BYDAutoWiperDevice.java` — `:9-26` enum, `:68,:72,:76` ba getter (0 setter)
- `doorlock/BYDAutoDoorLockDevice.java` — AREA `:8-14` · STATE `:20-22` · `:41` getter duy nhất
- `light/BYDAutoLightDevice.java` — `:46-62` enum · `:107-183` getter · `:205,:209` setter
- `panorama/BYDAutoPanoramaDevice.java` — DISPLAY_MODE `:46-51` · PANORAMA_* `:61-130` · `:151-226` getter · `:253,:257` setter
- `adas/BYDAutoADASDevice.java` — AVM `:33-35` · `:348` `setAVMSwitchState`
- `energy/BYDAutoEnergyDevice.java` — `:18-23` enum · `:112,:130` getter · `:173,:177` setter
- `charging/BYDAutoChargingDevice.java` — `:385,:458`
- `pm2p5/BYDAutoPM2p5Device.java` — `:72,:84,:92,:131`
- `tyre/BYDAutoTyreDevice.java` — `:99,:107,:111,:115,:127`
- `location/BYDAutoLocationDevice.java` — `:8-33` enum · `:65` `getLocationLongitudeLatitudeValue` · `:88` ⛔`setLocationInfo`
- `instrument/BYDAutoInstrumentDevice.java` — `:1728,:1732,:1752`
- `BYDAutoFeatureIds.java` — 6743 dòng, 6334 hằng, **0 `static {}`**; WIPER `:3145-3149` · OVERHAUL `:2548-2549,:2789-2790` · nested class `:3172-6715`

**CarSettings (app THẬT của BYD — chốt thứ tự tham số)** — `carsettings-apk/jadx-carsettings/sources/`
- `com/byd/vehiclehealth/repair/frontwiperrepair/FrontWiperRepairModel.java:61` · `…/rearwiperrepair/RearWiperRepairModel.java:53`
- `com/byd/vehiclesettings/outsidelight/view/AmbientLightColor.java:109` · `…/AmbientBrightness.java:100`
- `com/byd/vehiclesettings/sunroof/fragment/SunRoofFragment.java:775,781,787,794,801,835-906,1109` · `…/model/SunRoofModel.java:127` · `…/presenter/SunRoofPresenter.java:56`
- `com/byd/vehiclesettings/doorwindowlock/view/BackDoorHeight.java:86`
- `com/byd/ccs/impl/server/ac/Ac00600900120000.java:41`

**OpenBYD**
- `jadx-openbyd/sources/com/sr/openbyd/proxy/CarControlImpl.java:1488-1520` — `setWindow` là **pass-through** (bác chú thích sunroof của repo)
- `jadx-openbyd/sources/android/hardware/bydauto/bodywork/BYDAutoBodyworkDevice.java:881,:1163` — `getHatchDoorStatus`/`setHetchDoorStatus` **chỉ có ở bản này**, KHÔNG có trên DL3
- `jadx-openbyd24/sources/android/hardware/bydauto/BYDAutoFeatureIds.java:5-21` — R8, `public static int` không giá trị

**Repo**
- `core/.../launcher/{ControlRegistry,ControlDef,ControlLevels,HalBindingTable,HalReadTables,TelemetryRegistry}.kt`
- `app/.../launcher/testbridge/{TestBridgeHal,TestBridgeSweep,TestBridgeFeatMap}.kt` · `core/.../testbridge/TestBridgeCommand.kt:84-165`
- `app/.../launcher/{BydFeatureIds,BydHalGateway}.kt` · `app/.../modules/hal/BydHal.kt:319-333`

**Doc đã có (xây trên, không RE lại)**
`launcher-hal-re-overdrive-2026-09-08.md` · `hal-binding-remediation-2026-09-15.md` §A/§B/§C · `kachi-capability-catalog-2026-09-10.md` · `bodywork-window-trunk-RE-2026-09-06.md` · `byd-pm25-airclean-RE-2026-09-04.md` · `dudu-launcher-hal-RE-2026-09-06.md` · `byd-hal-permission-RE-2026-09-14.md` §4 · `oncar-hal-probe.md` · `feature-filter-2026-09-16.md` · `oncar-captest-results-2026-09-15.md` · `oncar-trace-2026-09-16.md` §3/§4
