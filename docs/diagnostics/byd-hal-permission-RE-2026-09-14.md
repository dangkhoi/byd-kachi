# BYD HAL — mổ cơ chế quyền/route để phân loại nút hỏng (fix off-car vs cổng chữ ký)

> **Trạng thái**: Current · **Cập nhật**: 2026-09-14 · **Mục đích**: mổ cơ chế quyền/route của HAL BYD để phân loại nút hỏng: fix off-car hay cổng chữ ký.

- **Ngày:** 2026-09-14 · **Owner:** dangkhoi · **Xe:** DiLink3 (DL3), Kachi uid 10135, chữ ký 177b2fc5 (KHÔNG platform)
- **Nguyên liệu:** `framework.jar` (29 MB, 3 dex), `libbydauto.so`, `libbydautoservice.so`, `hal-read-snapshot.txt`, `byd-services.txt` — pull từ xe, để scratchpad, **KHÔNG chép mã BYD vào repo**. Chỉ trích tên lớp/method/hằng làm bằng chứng.
- **Công cụ:** jadx 1.5.6 (framework.jar → Java), `strings`/`nm` (so). Toàn bộ trích dẫn `file:line` dưới đây trỏ vào bản decompile trong scratchpad (không commit).
- **Mức bằng chứng** (theo `.kiro/steering/conversation-protocol.md` + CLAUDE.md §2): **[ĐO]** = đọc source AOSP/BYD hoặc log thật; **[SUY]** = suy luận khớp source nhưng giá trị dùng chung/biến thể chưa chốt; **[CHƯA BIẾT]** = cần đo thêm trên xe.

---

## 1. TL;DR — kết luận cho câu hỏi gốc

**Nút "đèn đọc" và "AC nhiệt độ" đang hỏng KHÔNG phải vì cổng quyền chữ ký. Cả hai là lỗi ROUTE/METHOD, fix được off-car.** [ĐO]

- **Đèn đọc**: Kachi gửi feature-id `1330643002` (=`0x4f50003a`, `SET_INSIDE_LIGHT_STATE_SET`) tới **thiết bị LIGHT (1004)**, nhưng feature này thuộc **thiết bị SETTING (1023)**. `checkDeviceFeatures(1004, [0x4f50003a])` thất bại → đúng dòng log *"You have no permission to use the feature: 0x4f50003a with this device: 1004"*. Đây là **feature-whitelist theo thiết bị**, **KHÔNG phải** `SecurityException` của Android permission. Route sang 1023 là sửa được. [ĐO]
- **AC nhiệt độ**: Kachi bind `bindingKey = "BYDAutoAcDevice.setTemprature"` — **method này KHÔNG tồn tại** trong HAL. Setter thật là `setAcTemperature(int type, int value, int tempSource, int unit)` (4 tham số). Reflection tìm không ra → không lệnh nào tới xe ⇒ "xe không nhận lệnh". Bind lại đúng method + đúng công thức 4-arg là sửa được. [ĐO]

**Cổng quyền chữ ký (`enforceCallingOrSelfPermission(BYDAUTO_*_SET)`) hiện KHÔNG chặn Kachi ở các miền đã đo** (AC/BODYWORK/PM2P5/SETTING): các named-method đang chạy tốt (`setBodyWindowCtrlState`, `setSeatVentilatingState`, `setAutoCleanAirState`) và feature-id AC gió `501219340` đều **có gọi enforce SET-perm mà vẫn chạy** ⇒ Kachi **qua được** cổng chữ ký cho 4 thiết bị này. Điều này **mâu thuẫn** với tiền đề "Kachi giữ 0 BYDAUTO_*_SET" — xem §6, cần chốt bằng logcat. [ĐO]/[CHƯA BIẾT]

---

## 2. Cơ chế `checkDeviceFeatures` + hai cổng, và "device: 1004" là gì

Đường ghi feature-id (`set(int[] eventTypes, BYDAutoEventValue)`) trong `AbsBYDAutoDevice.java` đi qua **HAI cổng nối tiếp**, theo đúng thứ tự: [ĐO]

```
public int set(int[] eventTypes, BYDAutoEventValue eventValue) {
    ...
    if (!checkDeviceFeatures(getDevicetype(), eventTypes)) {   // ← CỔNG 1: whitelist feature-theo-thiết-bị
        Log.w(TAG, "checkDeviceFeatures failed!");
        return -2147482648;                                     //   (KHÔNG ném; trả sentinel)
    }
    if (getSetPermission() == null) { ... return -2147482648; }
    this.mContext.enforceCallingOrSelfPermission(getSetPermission(), null);  // ← CỔNG 2: Android signature-perm
    ...  // chỉ tới đây mới thật sự setInt/setIntArray xuống binder → native autoservice
}
```

**Cổng 1 — `checkDeviceFeatures(int deviceType, int[] featureIds)`** (`AbsBYDAutoDevice.java`, cuối lớp): [ĐO]

```
Set<Integer> ids = BYDAutoDeviceFeaturesMap.getFeatureIdsFromDevice(deviceType);
for (int featureId : featureIds)
    if (!ids.contains(featureId)) {
        Log.w(TAG, String.format(
          "You have no permission to use the feature: 0x%s with this device: %s!",
          Integer.toHexString(featureId), deviceType));   // ← ĐÂY là dòng log ta thấy
        return false;
    }
```

- Nó **không** hỏi Android permission, **không** hỏi chữ ký, **không** hỏi uid. Nó chỉ kiểm **feature-id có nằm trong tập feature mà `deviceType` khai báo hay không** (`BYDAutoDeviceFeaturesMap`). Chữ "permission" trong câu log là **gây hiểu nhầm** — thực chất là "thiết bị này không có feature đó".
- **"device: 1004" = `getDevicetype()` của object thiết bị mà lời gọi đi qua.** `1004 = BYDAUTO_DEVICE_LIGHT` (`BYDAutoConstants.java:27`). Vậy log nói: *"feature 0x4f50003a không thuộc thiết bị Light (1004)"*. [ĐO]
- Thất bại cổng 1 **trả sentinel `-2147482648`, KHÔNG ném exception**. Đây là dấu hiệu phân biệt: **route sai** ⇒ log `checkDeviceFeatures`/"no permission … device X" + sentinel; **thiếu chữ ký** ⇒ `enforceCallingOrSelfPermission` **ném `SecurityException`** với log kiểu *"Permission Denial: … requires android.permission.BYDAUTO_LIGHT_SET"*. Hai vết log KHÁC nhau — nhìn logcat là biết ngay bệnh nào. [ĐO]

**Cổng 2 — `enforceCallingOrSelfPermission(getSetPermission())`.** Đây mới là cổng chữ ký thật. Vì Kachi gọi HAL **in-process qua reflection**, object thiết bị chạy trong **process Kachi**, nên `enforceCallingOrSelfPermission` kiểm **chính uid Kachi (self)**. Mỗi thiết bị có perm riêng (`BYDAutoLightDevice.getSetPermission()` = `android.permission.BYDAUTO_LIGHT_SET`, v.v.). [ĐO]

> Native (`libbydautoservice.so`) có thêm `BYDAutoService::checkSetPermission(int deviceType,…)` / `checkGetPermission` (đọc từ symbol `_ZN7android14BYDAutoService18checkSetPermissionEiPij`). Đây là cổng thứ 3 ở process autoservice (system uid), kiểm theo Binder caller. Nhưng vì các named-method hiện chạy tốt đều đi **cùng đường** `setInt → binder → native`, native cũng đang **cho Kachi qua** ở các thiết bị đó. `strings` không lộ tên permission nào được so ở native ⇒ [CHƯA BIẾT] chi tiết, nhưng không phải điểm chặn quan sát được. [ĐO]/[CHƯA BIẾT]

---

## 3. Bảng thiết bị (device type) liên quan

`BYDAutoConstants.java` — trích các thiết bị dùng trong bảng nối Kachi: [ĐO]

| Device | type (dec) | Kachi Domain map tới đây? |
|---|---|---|
| `BYDAUTO_DEVICE_AC` | **1000** | CLIMATE |
| `BYDAUTO_DEVICE_BODYWORK` | **1001** | BODY, IDENTITY |
| `BYDAUTO_DEVICE_LIGHT` | **1004** | LIGHTS |
| `BYDAUTO_DEVICE_INSTRUMENT` | **1007** | TYRES |
| `BYDAUTO_DEVICE_PM2P5` | **1008** | — |
| `BYDAUTO_DEVICE_CHARGING` | **1009** | — |
| `BYDAUTO_DEVICE_STATISTIC` | **1014** | ENERGY |
| `BYDAUTO_DEVICE_SETTING` | **1023** | DRIVETRAIN, INFOTAINMENT |
| `BYDAUTO_DEVICE_ADAS` | **1038** | SAFETY |
| `BYDAUTO_DEVICE_DOOR_LOCK` | **1041** | — |
| `BYDAUTO_DEVICE_WIPER` | **1046** | — |

Kachi chọn thiết bị cho đường **feature-id** bằng `HalBindingTable.featureDeviceFqn(domain)` — **map thô theo Domain của UI** (`core/.../launcher/HalBindingTable.kt`). Đây chính là gốc của mọi lỗi route: **feature-id của BYD nằm rải rác trên các thiết bị KHÔNG khớp với Domain UI** (đèn đọc/ghi-nhớ-ghế/CPD nằm ở SETTING; đèn pha ở INSTRUMENT; ion ở PM2P5; khoá-trẻ-em ở DOOR_LOCK; sạc không dây ở CHARGING; gạt mưa ở WIPER…). [ĐO]

---

## 4. Bảng feature → id → thiết bị THẬT vs route hiện tại của Kachi

Nguồn "thiết bị thật" = trường `FEATURES_BYDAUTO_DEVICE_*` trong `BYDAutoDeviceFeaturesMap.java` (authoritative; tên inner-class jadx đặt như `PhoneMap` KHÔNG đáng tin, phải đọc trường `FEATURES_BYDAUTO_DEVICE_…`). Đối chiếu Kachi từ `ControlRegistry.kt`. [ĐO trừ nơi ghi [SUY]/[CHƯA BIẾT]]

| id (Kachi) | feature-id | Hằng BYD | Thiết bị THẬT | Route Kachi (Domain→dev) | Khớp? |
|---|---|---|---|---|---|
| `readl` đèn đọc | 1330643002 `0x4f50003a` | `SET_INSIDE_LIGHT_STATE_SET` | **SETTING 1023** | LIGHTS→**1004** | ❌ **SAI** |
| `anion` ion âm | 1337982994 | `PM25_ANION_STATE_SET` | **PM2P5 1008** | CLIMATE→**1000** | ❌ **SAI** |
| `wiper` gạt mưa | 321912848 | `WIPER_FRONT_LEVEL` (họ WIPER) | **WIPER 1046** [SUY] | BODY→**1001** | ❌ **SAI** [SUY] |
| `child_lock` khoá trẻ em | 1276141584 | `DOOR_LOCK_COMMAND_AREA_CHILDLOCK_LEFT_SET` | **DOOR_LOCK 1041** | BODY→**1001** | ❌ **SAI** |
| `headl`/`headlight_mode` | 1276153912 | `INSTRUMENT_HEADLIGHT_CONTROL_SET` | **INSTRUMENT 1007** | LIGHTS→**1004** | ❌ **SAI** |
| `seat_memory` nhớ ghế | 1276186678 | `SET_LF_MEMORY_LOCATION_SET` | **SETTING 1023** | BODY→**1001** | ❌ **SAI** |
| `charge_cap` giới hạn dòng sạc | 1324376132 | `SETTING_AC_CHARGING_CURRENT_LIMIT_STATUS_SET` | **SETTING 1023** | ENERGY→**1014** | ❌ **SAI** |
| `wireless_charge` sạc không dây | 1312817218 | `CHARGING_CHARGE_WIRELESS_CHARGING_SWITCH_SET` | **CHARGING 1009** | ENERGY→**1014** | ❌ **SAI** |
| `adas_cpd` cảnh báo trẻ | 1324617778 | `SETTING_CPD_SWITCH_STATUS_SET` (giá trị dùng chung nhiều hằng) | **SETTING 1023** [SUY] | SAFETY→**1038** | ❌ **SAI** [SUY] |
| `fan` gió | 501219340 `0x1de0000c` | `AC_WIND_LEVEL_SET` | AC 1000 | CLIMATE→1000 | ✅ đúng ([ĐO] chạy) |
| `recirc` tuần hoàn | 501219355 | `AC_CYCLE_MODE_SET` | AC 1000 | CLIMATE→1000 | ✅ đúng |
| `defrost` sấy trước | 501219362 | `AC_DEFROST_FRONT_STATE_SET` | AC 1000 | CLIMATE→1000 | ✅ đúng |
| `defrost_rear` sấy sau | 501219357 | `AC_DEFROST_REAR_STATE_SET` | AC 1000 | CLIMATE→1000 | ✅ đúng |
| `drl` đèn ban ngày | 985661476 | `LIGHT_DAY_RUNNING_LIGHT_AUTO_STATE` | LIGHT 1004 | LIGHTS→1004 | ✅ route đúng ⚠ (ghi hằng `_STATE` không phải `_SET` — có thể no-op, [SUY]) |
| `sunshade` rèm nóc | 1330642984 | `BODYWORK_SUNSHADE_PANEL_PERCENT` | BODYWORK 1001 | BODY→1001 | ✅ đúng |
| `drive_mode` chế độ lái | 1272971280 | `SETTING_TARGET_DRIVING_MODE` | SETTING 1023 | DRIVETRAIN→1023 | ✅ đúng |
| `itac` | 1324376094 | `SET_ITAC_STATE_SET` | SETTING 1023 | DRIVETRAIN→1023 | ✅ đúng |
| `screen_rotation` xoay màn | 1330643005 | `SET_PAD_ROTATION_SET` | SETTING 1023 | INFOTAINMENT→1023 | ✅ đúng |
| `adas_esp` | 944766984 | `ADAS_ESP_STATE_SET` | ADAS 1038 | SAFETY→1038 | ✅ đúng |
| `adas_tsr` | 944767044 | `ADAS_ISLA_SWITCH_SET` | ADAS 1038 | SAFETY→1038 | ✅ đúng |
| `adas_fcw` | 1324560420 | `ADAS_FCW_LEVEL_SET` | ADAS 1038 | SAFETY→1038 | ✅ đúng |
| `adas_rcta` | 944766990 | `ADAS_RCTA_STATE_SET` | ADAS 1038 | SAFETY→1038 | ✅ đúng |
| `adas_dow` | 944766994 | `ADAS_DOW_STATE_SET` | ADAS 1038 | SAFETY→1038 | ✅ đúng |
| `adas_slw` | 850452531 | `ADAS_SLW_FUNC_SWITCH_STATE_SET` | ADAS 1038 | SAFETY→1038 | ✅ đúng |
| `ambient_power/color/brightness/music` | 1276153924 / 1276194864 / 1276194858 / 489701407 | (chưa map được hằng trong FeaturesMap) | **[CHƯA BIẾT]** | LIGHTS→1004 | ⚠ [CHƯA BIẾT] |
| `mirror_fold_btn` gập gương | 1276157992 | (chưa map; `mirror_auto` cùng họ resolve về SETTING) | **[CHƯA BIẾT], nghi SETTING** | BODY→1001 | ⚠ [SUY] SAI |
| `ac_auto` A/C auto | 1324355606 | `AC_AUTO_MODE_SET` (catalog) — chưa xác thực FeaturesMap | AC 1000 [SUY] | CLIMATE→1000 | ✅ [SUY] đúng |
| `cam`/`camera_view` | 3001 | (số nhỏ — không phải feature-id BYD hợp lệ) | — | INFOTAINMENT | ⚠ pseudo-id, [CHƯA BIẾT] |

> **Lưu ý biến thể (portability):** hằng trong `BYDAutoFeatureIds` **được tính lúc chạy theo cờ biến thể** `isCanFD`/`isToyota`, ví dụ `SET_INSIDE_LIGHT_STATE_SET = (!isCanFD && isToyota) ? 590348346 : 1330643002` (`BYDAutoFeatureIds.java:8829`). Giá trị Kachi hardcode (`1330643002`) đúng cho **nhánh mặc định = DL3 hiện tại** (khớp `0x4f50003a` xe báo), nhưng **sai cho biến thể Toyota/CanFD**. Muốn bền qua đời xe: đọc hằng qua reflection `BYDAutoFeatureIds.<NAME>` theo trim thay vì chôn số. [ĐO]

---

## 5. Phân loại từng nút hỏng

### 5a. FIX ĐƯỢC OFF-CAR (route sai / method sai — không phải cổng chữ ký)

Các nút này chỉ cần đổi **thiết bị đích** (đường feature-id) hoặc **tên method** (đường named). Sau khi route đúng, chúng vẫn phải qua cổng 2 (SET-perm của thiết bị đích) — với các thiết bị **đã chứng minh Kachi qua được** (AC/BODYWORK/PM2P5/SETTING) thì tin cậy cao; với thiết bị **chưa đo** (LIGHT/INSTRUMENT/DOOR_LOCK/CHARGING/WIPER/ADAS) thì phải sweep xác nhận (§6).

| Nút | Sửa gì | Tin cậy cổng chữ ký sau khi route |
|---|---|---|
| **`readl` đèn đọc** | feature `1330643002` route sang **SETTING (1023)** | **CAO** — `setSeatVentilatingState` (SETTING_SET) đã chạy ⇒ Kachi qua SETTING_SET [ĐO] |
| **`temp` AC nhiệt độ** | bỏ `setTemprature` (không có); bind `setAcTemperature(type,value,tempSource,unit)` trên AC (1000) | **CAO** — AC_SET đã qua (gió + named AC) [ĐO] |
| **`anion` ion** | feature `1337982994` route sang **PM2P5 (1008)** | **CAO** — `setAutoCleanAirState` (PM2P5_SET) đã chạy [ĐO] |
| **`seat_memory`** | feature `1276186678` route sang **SETTING (1023)** | CAO (SETTING_SET đã qua) |
| **`charge_cap`** | feature `1324376132` route sang **SETTING (1023)** | CAO |
| **`adas_cpd`** | feature `1324617778` là CPD của SETTING → route **SETTING (1023)** [SUY] | CAO nếu đúng hằng CPD |
| **`headl`/`headlight_mode`** | feature `1276153912` route sang **INSTRUMENT (1007)** | TRUNG BÌNH — INSTRUMENT_SET chưa đo [CHƯA BIẾT] |
| **`child_lock`** | feature `1276141584` route sang **DOOR_LOCK (1041)** | TRUNG BÌNH — DOOR_LOCK_SET chưa đo |
| **`wireless_charge`** | feature `1312817218` route sang **CHARGING (1009)** | TRUNG BÌNH — CHARGING_SET chưa đo |
| **`wiper`** | feature `321912848` route sang **WIPER (1046)** [SUY] | TRUNG BÌNH — WIPER_SET chưa đo |

### 5b. CỔNG QUYỀN CHỮ KÝ (chỉ platform ghi được)

**Chưa quan sát được nút nào rơi vào nhóm này.** Vết log của cả 2 ca đo được (`readl`, `temp`) đều **KHÔNG phải** `SecurityException`/`Permission Denial` — mà là `checkDeviceFeatures`/method-not-found. Cổng chữ ký thật (`enforceCallingOrSelfPermission`) **đang cho Kachi qua** trên 4 thiết bị đo được. Vậy tại thời điểm này **KHÔNG có bằng chứng cổng chữ ký chặn Kachi** ⇒ nhóm này **rỗng cho tới khi sweep §6 tìm ra một `SecurityException` thật**. [ĐO]

### 5c. Chưa map / pseudo-id (cần grab-list on-car)

`ambient_power/color/brightness/music`, `mirror_fold_btn`, `cam`/`camera_view` (id `3001`) — chưa chốt được thiết bị/hằng từ FeaturesMap. [CHƯA BIẾT] → đưa vào sweep.

---

## 6. Cổng chữ ký: mâu thuẫn với tiền đề, và cách chốt

**Tiền đề đưa vào task:** *"BYDAUTO_*_SET là signature-perm, Kachi giữ 0, com.byd.carsettings giữ 48."*

**Bằng chứng ngược từ source + hành vi thật** [ĐO]:
- `BYDAutoBodyworkDevice.setBodyWindowCtrlState` **có** `enforceCallingOrSelfPermission(BODYWORK_SET_PERM)` (`:895–898`) — mà **kính vẫn mở được** trên xe.
- `BYDAutoSettingDevice.setSeatVentilatingState` **có** `enforceCallingOrSelfPermission(SETTING_SET_PERM)` (`:1945–1947`) — **ghế mát vẫn chạy**.
- `BYDAutoAcDevice.setAcTemperature`/các setter AC **có** `enforceCallingOrSelfPermission(AC_SET_PERM)` (`:622…`) — feature-id AC gió `501219340` (đường `set(int[],EventValue)` → cũng phải qua enforce AC_SET) **vẫn chạy** ([ĐO] `1de0000c` gió CHẠY).

⇒ Nếu Kachi thật sự giữ **0** `BYDAUTO_*_SET`, các lệnh trên đã phải ném `SecurityException` và **không** chạy. Vì chúng chạy, **Kachi đang qua được `enforceCallingOrSelfPermission` cho AC/BODYWORK/PM2P5/SETTING**. Có ba khả năng, **chưa phân định** [CHƯA BIẾT]:
1. Con số "0 vs 48" đọc nhầm chỉ số (có thể là app-op/gid, không phải grant permission).
2. Các perm này **không** thật sự ở mức `signature` trên ROM DL3 (định nghĩa protectionLevel nằm trong `framework-res.apk`/`etc/permissions/*.xml` — **KHÔNG có trong `framework.jar`**, nên RE này chưa thấy) → có thể là `normal`/`privileged-allowlist` mà Kachi vẫn thoả.
3. Kachi được cấp qua allowlist priv-app nào đó.

**Cách chốt (rẻ, làm trên xe theo §15 CLAUDE.md — đọc dữ liệu, không mò UI):**
- `adb shell dumpsys package com.byd.kachi | grep -A40 "requested permissions\|install permissions\|runtime permissions"` → xem Kachi được **grant** những `BYDAUTO_*_SET` nào (granted=true/false).
- `adb shell pm list permissions -g -d | grep -i bydauto` → xem **protectionLevel** thật của nhóm perm này.
- Sau khi vá route `readl`→1023 (bản build mới), bấm thử và đọc `logcat`:
  - Nếu thấy lệnh xuống binder + đèn bật ⇒ xác nhận **fix off-car đủ**.
  - Nếu thấy `SecurityException … requires android.permission.BYDAUTO_SETTING_SET` ⇒ khi đó mới là cổng chữ ký (nhưng trái với `setSeatVentilatingState` đang chạy — rất khó xảy ra).

**Đường vòng nếu (giả định) một thiết bị nào đó thật sự chặn chữ ký** (chưa cần tới, ghi để tham chiếu):
- `byd_auth_service` = `android.app.IBYDAuthService` (`byd-services.txt:25`) có `requestAuth`/`requestAuthNew` (challenge-response) — là cơ chế **xác thực phiên**, không phải API ghi HAL cho app thường; chưa có bằng chứng nó cấp quyền ghi vượt cổng. [SUY]
- `AutoContainer` (`IAutoContainer`, dịch vụ 24) — cast/2nd-display, **không** ghi HAL thân xe; ngoài phạm vi (và ràng buộc: KHÔNG đụng logic cluster-cast).
- Route qua **shell uid (dadb `localhost:5555`)**: shell **cũng không platform** ⇒ nếu cổng là signature thật thì shell cũng bị chặn ⇒ **không ăn**. Chỉ nên dùng shell để *đo/chẩn đoán*, không phải để vượt quyền. [SUY]
- Kết luận: **chưa cần đường vòng** — chưa có cổng chữ ký nào chặn thật.

---

## 7. Đề xuất bản vá `HalBindingTable` (CHỜ OWNER DUYỆT — chưa code)

Gốc rễ: `featureDeviceFqn(domain)` chọn thiết bị theo **Domain UI**, sai bản chất. **Không** sửa bằng cách đổi Domain (Domain còn dùng để gom nhóm UI). Hướng generic đề xuất (theo CLAUDE.md §7 — đo/dữ liệu, không hardcode tên gói):

1. **Thêm thiết bị đích tường minh cho control đường feature-id.** Cho mỗi `ControlDef` đường feature-id một trường `featureDevice: Int?` (device-type BYD), fill từ bảng §4 (đã verify từ `BYDAutoDeviceFeaturesMap`). `HalBindingTable.write`/`readRaw` ưu tiên `featureDevice` nếu có, fallback `featureDeviceFqn(domain)`.
   - Hoặc **data-driven hơn**: dựng bảng `featureId → deviceType` một lần từ `BYDAutoDeviceFeaturesMap.getFeatureIdsFromDevice(d)` qua reflection lúc khởi động (đúng theo trim, tự chịu biến thể) rồi tra ngược. Tránh chôn số theo từng control.
2. **AC nhiệt độ**: thay `bindingKey` `"BYDAutoAcDevice.setTemprature"` → named-method 4-arg `setAcTemperature`. `writeArgs("temp")` = `[type, value, tempSource, unit]`. Công thức từ source `setAcTemperature` (`BYDAutoAcDevice.java:619`): `type` 0=cả trước/1=chính/2=phụ/3=sau; `unit` 1=°C (0=°F); `value` °C nguyên 17..33 (nửa độ 34..66); `tempSource` ∈ {0,1} (nguồn lệnh — đo trên xe để chốt 0 hay 1). Ví dụ set 22°C cả xe: `setAcTemperature(0, 22, 0, 1)`. Cần `HalGateway.namedInt` hỗ trợ 4 int (đã có cho ghế/kính nhiều-arg).
3. **Giá trị (state) cho các toggle feature-id** (đèn đọc/ion…): xác nhận enum bật/tắt trên xe (đa số BYD là 1=ON/2=OFF, không phải 0). `writeArgs` hiện gửi `primary` (1/0) — mặt "tắt" gửi `0` nhiều khả năng bị xe bỏ qua (giống bài học `setRainCloseWindow` OFF=2). Chốt bằng sweep.

**Chắc chắn fix được ngay lượt sau (id + device rõ ràng từ RE):** `readl`→SETTING(1023), `anion`→PM2P5(1008), `seat_memory`→SETTING(1023), `charge_cap`→SETTING(1023), `temp`→named `setAcTemperature`. Các nút còn lại (headl/child_lock/wireless_charge/wiper/adas_cpd/ambient*/mirror_fold) — route theo §4 nhưng **phải sweep** vì (a) một số giá trị dùng chung nhiều hằng ([SUY]) và (b) cổng SET-perm của thiết bị đích chưa đo.

---

## 8. Grab-list on-car (sweep xác nhận — theo CLAUDE.md §14/§15)

Chạy trên xe (dadb `localhost:5555`, hoặc qua `ClusterDiag`), đọc dữ liệu/log thay vì mò UI:
1. `dumpsys package com.byd.kachi` + `pm list permissions -g -d | grep bydauto` → chốt grant + protectionLevel (giải quyết §6).
2. Với mỗi nút §5a: sau khi build bản route-đúng, bấm và đọc `logcat -s AbsBYDAutoDevice BYDAuto*Device` → phân biệt: (a) lệnh xuống (fix xong), (b) `checkDeviceFeatures failed` (còn sai device/id), (c) `SecurityException` (mới là cổng chữ ký).
3. Chốt `tempSource` của `setAcTemperature` (0 vs 1) + enum ON/OFF của đèn đọc/ion.
4. Xác định thiết bị/hằng cho `ambient_*`, `mirror_fold_btn`, `cam(3001)` — hiện [CHƯA BIẾT].

---

## 9. Nguồn (trích, không commit mã BYD)

- `AbsBYDAutoDevice.java` — `set(int[],BYDAutoEventValue)`, `checkDeviceFeatures` (câu log "no permission … device"), hai cổng.
- `BYDAutoConstants.java` — device types (1000/1001/1004/1007/1008/1009/1014/1023/1038/1041/1046).
- `BYDAutoFeatureIds.java:8829` — `SET_INSIDE_LIGHT_STATE_SET = … : 1330643002` (biến thể theo `isCanFD/isToyota`).
- `BYDAutoDeviceFeaturesMap.java` — `FEATURES_BYDAUTO_DEVICE_*` (nguồn thiết-bị-thật cho từng feature).
- `BYDAutoLightDevice.java:96,125` (mDeviceType=1004, LIGHT_SET_PERM); `BYDAutoBodyworkDevice.java:895` (setBodyWindowCtrlState enforce BODYWORK_SET); `BYDAutoSettingDevice.java:1945` (setSeatVentilatingState enforce SETTING_SET); `BYDAutoAcDevice.java:619` (setAcTemperature enforce AC_SET, 4-arg).
- `libbydautoservice.so` — symbol `BYDAutoService::checkSetPermission/checkGetPermission` (cổng native, chưa lộ tên perm).
- Kachi: `core/src/main/kotlin/com/byd/clusternav/launcher/ControlRegistry.kt` (readl:161-163, temp:171-172, …), `HalBindingTable.kt` (`featureDeviceFqn`, `write`).
