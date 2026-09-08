# RE tập trung — Đường CODING/UDS để bật nav trên HUD ZIN (`0x38B00030`)

> **Loại:** Diagnostics / RE handoff · **Ngày:** 2026-08-19 · **Scope:** RE đọc-hiểu decompile. **KHÔNG sửa code, KHÔNG commit/push.** Coding XE là quyết định của owner.
> **Mục tiêu:** Tìm MỌI cơ chế set được `0x38B00030` (INSTRUMENT_HUD_NAVIGATION_MAP_CONFIG = not-provisioned = `-2147482648`) — on-device (adb/privileged) HAY chỉ OBD-UDS ngoài — + DID/ECU + tool/thủ tục.
> **Nối tiếp:** `<repo>/docs/diagnostics/factory-hud-nav-RE-avenues-2026-08-19.md` (đường 4/5/10 = "cần tool coding"), `<repo>/docs/decisions/0002-hud-nav-coding-locked.md` (gate firmware). Handoff này **mở khoá được đường 10** ("không app nào coding") thành **"CÓ kênh UDS on-device nhưng gated system-UID"**, và **định danh chính xác cơ chế provisioning** (vehicleCode self-study + equipment config).

**Nguồn RE (cache gốc `RE_ROOT = ~/Library/Caches/clusternav-re`):**
`sysimg/jadx-DiCarServer` (SDK DiCar + feature-id catalog), `sysimg/jadx-BydDevelopmentTools` (app factory system-UID), `sysimg/libBydDataSource.so` (native cluster/DataSource — UDS responder + self-study + config store), `sysimg/libbyddiagnosticservice.so` (cloud DTC uploader), `sysimg/diagnostic_config.json` (DTC map).

---

## TL;DR — Verdict

1. **`0x38B00030` do INSTRUMENT-CLUSTER ECU giữ** (routed qua `BYDAutoInstrumentDevice`), cùng cả họ `INSTRUMENT_HUD_NAVIGATION_MAP_*`. Không phải head-unit/SOC. **Confirmed từ decompile.**
2. **CÓ một kênh UDS-thô on-device thật** trong firmware: **BYDAutoOtaDevice `0xAA000140` (multi-frame TX) + BYDAutoSettingDevice SECRET-OBD `0xAA000241/0x99000241`**. App factory `BydDevelopmentTools` dùng kênh này để chạy **UDS thật: `10 03` (session) → `27 01/02` (SecurityAccess seed/key) → `2E`/`31` (WriteDID / RoutineControl)** trên bus. **NHƯNG kênh này gated `sharedUserId="android.uid.system"` (platform-signed)** — ClusterNav (user-signed) **KHÔNG** với tới được nếu không có root/platform-key. **Confirmed.**
3. **Cơ chế provisioning nav-HUD = "vehicleCode self-study" của cụm.** `libBydDataSource.so` có `BusinessSelfStudy::vehicleCodeSelfStudyUpdate()` + log `"selfstduy has completed, vehicleCode is 0x%02x"`, đổ vào `BydConfigInfo`/`ConfigureManager` (config XML có `SetConfig_UINT8...`). HUD-nav-map là một **equipment/config flag** cùng lớp với ADAS equip flags (`updateSlaFunctionEquipment`…). `0x38B00030` chỉ **phản chiếu** flag đó. **Confirmed cơ chế; chưa confirmed bit/giá trị cụ thể.**
4. **`40d` (138=`0x8A` / 162=`0xA2`) khớp đúng dạng `vehicleCode` (1 byte, `0x%02x`)** — nên giả thuyết "vehicleCode khác nhau" giờ có cơ sở CƠ CHẾ (không còn "tình cờ vu vơ"). Nhưng **KHÔNG có bảng trong image** ánh xạ `0xA2 → HUD-nav ON`. Vẫn là hypothesis cần dealer-DB/on-car.
5. **Đường thực tế cho owner:** tool coding BYD (OBD-II → UDS) đặt equipment/vehicleCode nav-HUD (dealer knows the DID), cụm self-study lại → `0x38B00030=1`. **DID/ECU/khoá cụ thể nằm trong dealer ODX, KHÔNG có trong image head-unit.**

---

## (1) `0x38B00030` + họ hàng do ECU NÀO giữ?

**INSTRUMENT-CLUSTER ECU** (bảng đồng hồ), giữ qua `BYDAutoInstrumentDevice`. Bằng chứng:

- `sysimg/jadx-DiCarServer/.../feature/instrument/Instrument.java`:
  - `:534 INSTRUMENT_HUD_MAP_FORMAT_34C_SET = "0x34C00026"`
  - `:536 INSTRUMENT_HUD_NAVIGATION_MAP_CONFIG = "0x38B00030"`
  - `:537 INSTRUMENT_HUD_NAVIGATION_MAP_CONFIG_STATUS = "0x30100030"`
  - `:538 INSTRUMENT_HUD_NAVIGATION_MAP_SET = "0x32B1102E"`
  - `:539 INSTRUMENT_HUD_NAVIGATION_MAP_STATUS = "0x38B0002E"`
  → cả họ đều prefix `INSTRUMENT_HUD_*` (config / config-status / set / status / format), tức thuộc **feature-domain "instrument"**.
- `.../feature/instrument/InstrumentMapper.java:1-8` → `getDevice()` trả `BYDAutoInstrumentDevice.getInstance(context)`; mọi id `INSTRUMENT_*` route qua device này ⇒ **đích vật lý = MCU cụm đồng hồ**.
- `sysimg/libBydDataSource.so` (native của cụm) chứa render nav của cụm: symbols `BusinessUi1::updateNaviType/updateNaviDisplay/sendNaviMessage`, strings `NAVI_TYPE_FULL_SCREEN/SMALL_SCREEN/EASY/INVALID`, `getConfig ... m_u8NaviType=%d` ⇒ **cụm là nơi vẽ nav + giữ config nav.** Đây là ECU sở hữu các cờ trên.

**Sentinel not-provisioned:** readback owner `0x38B00030 = -2147482648 = 0x800003E8` (= `Integer.MIN_VALUE + 1000`, KHÔNG phải `MIN_VALUE` thuần) ⇒ là **giá trị thật do cụm trả** (not-provisioned indicator), không phải lỗi parse của mapper. (verify số học ở Phụ lục.)

---

## (2) Có cơ chế ON-DEVICE ghi coding/variant không?

**CÓ một kênh UDS-thô on-device — nhưng gated system-UID.** Đây là điểm mở khoá "đường 10" (trước kết luận "không app nào coding").

### 2.1 Kênh: SECRET-OBD / OTA multi-frame (raw UDS-over-CAN)

App factory `BydDevelopmentTools` inject **UDS thô** qua BYD framework devices:

- `sysimg/jadx-BydDevelopmentTools/sources/a/a/a/l0/a.java` (`DiagnoseManager`):
  - Constructor: `BYDAutoOtaDevice.getInstance()` + `BYDAutoSettingDevice.getInstance()`; `otaDevice.registerListener(e, {-1728052928})` (=`0x99000140` OTA_MULTI_FRAME_CANDATA); `settingDevice.registerListener(f, {SETTING_SECRETOBD_DYNAMIC_DATA_CALLBACK})` (=`0x99000241`).
  - `f(byte[] frame, int rxId)` → **`otaDevice.set({-1442840256}, eventValue)`** — `-1442840256 = 0xAA000140` = **`OTA_MULTI_FRAME_SET`** ⇒ **đây là đường TX UDS thô**.
  - `g(int rxId,int)` → `settingDevice.set({SETTING_SECRETOBD_SENT_MONITOR_TABLE_SET}, ...)` = `0xAA000241` — khai báo CAN-ID phản hồi cho MCU giám sát.
  - `c(frames, csum, reqId, rxId)` — đóng khung ISO-TP–like: header `{0,3,-24}` (0x03E8=1000?), reqId/rxId 2-byte, độ dài, checksum.
- Định nghĩa id: `.../feature/setting/Setting.java`:
  - `:1832 SETTING_SECRETOBD_DYNAMIC_DATA_CALLBACK = "0x99000241"`
  - `:1833 SETTING_SECRETOBD_SENT_MONITOR_TABLE_SET = "0xAA000241"`
  - `.../feature/ota/Ota.java`: `OTA_MULTI_FRAME_SET = "0xAA000140"`, `OTA_MULTI_FRAME_CANDATA = "0x99000140"`, `OTA_MULTI_FRAME_ACK = "0x99000141"`, `OTA_REMOTE_DIAGNOSTIC_FUNCTION_REQUEST = "0xAA000390"`.

### 2.2 UDS thật chạy trên kênh này (factory tool)

`a/a/a/l0/b.java` (`ObdDataManager`) build **byte UDS chuẩn**:
- `l(times,...)`: `times==1 → "10 03"` (**DiagnosticSessionControl 0x10, subfn 03 = extendedSession**); `times==2 → "27 01"` (**SecurityAccess requestSeed**); `times==3 → bArr[0]=39(0x27), bArr[1]=02` (**SecurityAccess sendKey**) + key.
- `w(int v)`: `"2E 00 0B 00"` (**WriteDataByIdentifier 0x2E, DID 0x000B**, byte = v) — dùng cho ignition-cycle/loop test.
- `x(int v)`: `"31 01 00 06 00"` (**RoutineControl start, routine 0x0006**); `f()`: `"31 02 00 06/07"` (**RoutineControl stop**). `g()`: `"31 01 00 07 …"` (routine 0x0007 + 366 byte network-segment map).
- Target CAN: reqId `0x747`/rxId `0x74F` ("right domain"), reqId `0x720`/rxId `0x728` ("left domain") — **địa chỉ chẩn đoán GATEWAY/domain**, 6 domain: 智能进入网/车身网/能量网/底盘网/ADAS网/车身网2.

⇒ **Firmware CÓ đường on-device chạy full UDS (10/27/2E/31).** Nhưng factory tool chỉ dùng nó cho **CAN-ID/network-segment mapping + ignition-cycle test trên GATEWAY**, KHÔNG target cụm để code HUD.

### 2.3 Privilege — vì sao ClusterNav KHÔNG dùng được

- Manifest `BydDevelopmentTools.apk`: **`sharedUserId="android.uid.system"`** (parse AXML string pool, có cả `sharedUserId` + `android.uid.system`), + BYD intent actions, permission riêng `com.byd.byddevelopmenttools.permission.ROLLBENCH_MODE`. ⇒ **app chạy UID system, ký platform-key.**
- `.../com/byd/car/utils/PermissionUtils.java:9-27`: DiCarServer gọi `context.checkPermission(readPermission/writePermission/dangerousPermission, caller.pid, caller.uid)` cho **từng property** ⇒ mỗi id (SECRETOBD, OTA-multiframe, INSTRUMENT_HUD_*) có permission BYD gắn kèm; caller phải giữ mới set được.
- **Hệ quả:** kênh UDS-thô on-device là **system/signature-gated**. dadb uid-2000 shell hay app 3rd-party (ClusterNav user-signed) **không** mở được. Muốn dùng on-device ⇒ cần **(a) root** (chạy như system / inject), **(b) platform signing key** để co-sign app với `android.uid.system`, hoặc **(c) sửa/thay `BydDevelopmentTools`**. **Confirmed gate; chưa test bypass on-car.**

### 2.4 BydDevelopmentTools KHÔNG có màn coding HUD

Grep app package `com/byd/byddevelopmenttools`: chỉ dùng `BYDAutoVersionDevice.get(...)` (đọc version — VersionMsgActivity/DevelopmentSettingsActivity/LogControl), + `ObdDataActivity`/`MappingActivity` = **CAN-ID/网段 mapping** (映射) với "safe auth". **0 khớp** `INSTRUMENT_HUD_*` / `0x38B000` / coding-variant / WriteDID-tới-cụm. ⇒ Không có UI coding nav-HUD trong app này.

---

## (3) Đường OBD-UDS ngoài — DID / session / SecurityAccess

### 3.1 Session + Security cần gì (bằng chứng cứng)

Từ `ObdDataManager` (mục 2.2), quy trình UDS chuẩn để "làm việc đặc quyền" trên bus BYD:
1. **`10 03`** DiagnosticSessionControl → **extendedDiagnosticSession** (không thấy `10 02` programmingSession trong flow mapping; coding cụm có thể cần 0x10 03 hoặc 02 — chưa xác định cho ECU cụm).
2. **`27 01` (requestSeed) → `27 02` (sendKey)** SecurityAccess.
3. Sau unlock: **`2E xx xx <data>`** WriteDataByIdentifier hoặc **`31 01/02 <routine>`** RoutineControl.

### 3.2 Thuật toán seed→key CÓ LỘ trong app (nhưng cho GATEWAY, không phải cụm)

`ObdDataManager.k(long seed, int domainType, int len)` (getSTKey) + `l()` (getSafeAuthData):
```
t   = (seed >> 2) ^ (((seed >> 1) ^ seed) << 3)
domainType==1 (right/0x747): key = t ^ (len==2 ? 0x1E : 0x6500001E)   // 0x6500001E = 1694498846
domainType==2 (left /0x720): hằng 0x1D / 0x6500001D (1694498845)
```
Seed đọc từ reply `27 01` (hàm `p(...,2)`: gom `stCcSeed`), key gửi ở `27 02`.

**Caveat quan trọng (honest):** thuật toán này unlock **"secret-OBD safe access" của GATEWAY** (0x720/0x747) để cho phép **CAN-ID/network mapping** (rollbench). **KHÔNG có bằng chứng nó là security-level của INSTRUMENT-CLUSTER cho coding.** ECU khác nhau thường có seed/key khác. ⇒ **không thể giả định** dùng key này code được cụm.

### 3.3 DID nào map tới HUD-nav-config?

- **KHÔNG có trong image head-unit.** WriteDID factory tool chỉ chạm `DID 0x000B` (ignition-cycle, trên gateway), routine `0x0006/0x0007` (mapping). Không có `2E`/`31` nào target cờ nav-HUD của cụm.
- Cụm là **UDS responder**: `libBydDataSource.so` có `BusinessUdsManager::HandleReadDIDRequest`, `HandleReadDIDResponse`, `HandleIOControlRequest/Response`, `DispatchUdsReq`; strings `"handle read DID request"`, `"request read did[0x%X]"`. ⇒ cụm **trả lời** ReadDID (0x22) + IOControl (0x2F) từ tester ngoài; **không thấy symbol `HandleWriteDID`** ⇒ coding-write có thể đi qua **IOControl/RoutineControl + self-study** hoặc một session/ECU khác (chưa xác định).
- **DID + security-level cụ thể cho nav-HUD nằm trong dealer ODX/coding-DB của BYD**, không có trong firmware này.

---

## (4) Dấu vết "HUD nav = codeable feature"

**CÓ — cơ chế provisioning rất rõ, giá trị cụ thể thì chưa.**

- `libBydDataSource.so`:
  - **`BusinessSelfStudy`**: `GetInstance`, `selfStudyIsCompleted`, **`vehicleCodeSelfStudyUpdate`**, `canDataUpdate`; string log **`"BusinessSelfStudy::Init selfstduy has completed, vehicleCode is 0x%02x"`** ⇒ cụm **self-study một `vehicleCode` 1-byte** từ CAN.
  - **`BydConfigInfo`** (config store bền): `loadConfigXML/saveConfigXML/resetConfigXML`, `GetConfig_UINT8/INT8/BOOL/INT16/INT32/STRING`, `SetConfig_UINT8...`, `config_data_array_item_type_`, `insertItemToXML` ⇒ config được ghi vào XML nội bộ cụm, phân loại theo item-id.
  - **`ConfigureManager`** + `BusinessBase::m_pConfigureManager` — mọi business unit đọc config qua đây.
  - **Equipment flags cùng lớp:** `updateAcc/Aeb/Alc/Hma/Icc/Lka/Sla/Tla/Bsis/Elka/Mois/RearRadar/FrontRadar FunctionEquipment`; strings `"ADAS:Sla function equip self study result is %d"` ⇒ **mỗi feature được provision bằng một "equipment" flag từ self-study/config.** HUD-nav-map là cùng cơ chế (một equipment/config flag).
- **Chuỗi nhân-quả (traced):** vehicle config/vehicleCode (trên bus) → `BusinessSelfStudy` self-study → ghi `BydConfigInfo`/`ConfigureManager` → quyết định `m_u8NaviType` + equipment nav-HUD → `0x38B00030` phản chiếu ra head-unit. Owner đọc sentinel not-provisioned ⇒ config cụm chưa bật equipment nav-HUD.

**`40d` 138 vs 162:** `138=0x8A`, `162=0xA2` — **đúng khuôn `vehicleCode` 1-byte (`0x%02x`)**. Giờ có cơ sở CƠ CHẾ để nghi vehicleCode là biến quyết định. **NHƯNG (honest):** không có bảng nào trong image nói `0xA2 → HUD-nav ON` / `0x8A → OFF`; có thể vehicleCode chỉ là 1 trong nhiều input, và equipment-flag nav-HUD là một item config riêng. ⇒ **vẫn hypothesis, cần dealer-DB hoặc on-car A/B.**

---

## (5) ĐỀ XUẤT thực tế cho owner + đường on-device khả thi

### 5.1 Đường chắc chắn nhất — tool coding BYD ngoài (OBD-II)

**Cần gì:**
- Tool chẩn đoán BYD chính hãng (BYD dealer diagnostic, vd họ **"BYD DiLink/Explorer" service tool** hoặc thiết bị OBD hãng) — thứ **có ODX/coding-DB** biết đúng ECU + DID + security-level + routine cho equipment nav-HUD.
- Cáp OBD-II tới cổng chẩn đoán xe.

**Thủ tục (khái quát, dealer thực thi):**
1. Kết nối, đọc **variant coding / equipment matrix** hiện tại của cụm (và/hoặc gateway VIN-config).
2. Bật equipment flag **"HUD navigation map"** (hoặc đặt vehicleCode/biến-thể tương ứng xe có HUD-nav-zin) — qua `10` session → `27` security (**key dealer**, không phải key gateway ở §3.2) → `2E`/`31` coding.
3. Trigger cụm **self-study lại** (RoutineControl / khởi động lại cụm) → `vehicleCodeSelfStudyUpdate` chạy → `0x38B00030` chuyển `1`.
4. Test: bật Nav+HUD của ClusterNav (app đã ghi guide-info đúng — xem ADR 0002) → xem HUD zin có render nav không.

**Rủi ro:** coding sai → DTC/misconfig cụm; cần bản coding gốc để rollback. Security key là của dealer — nếu không có, đường này **chỉ dealer làm được**.

### 5.2 Đường ON-DEVICE để THỬ (kèm rủi ro — chưa test)

Firmware **có** kênh UDS-thô (mục 2). Về lý thuyết một tác nhân **system-UID/root** có thể phát UDS tới cụm mà không cần tool ngoài. Điều kiện mở khoá + rủi ro:

| Điều kiện cần | Trạng thái | Rủi ro |
|---|---|---|
| Chạy như `android.uid.system` (giữ permission BYD cho OTA/SECRETOBD/instrument) | **Chưa có** (ClusterNav user-signed). Cần root, HOẶC platform-key co-sign, HOẶC drive `BydDevelopmentTools` | Cao — cần thay đổi hệ thống |
| Biết **CAN reqId/rxId của INSTRUMENT-CLUSTER** (không phải 0x720/0x747 của gateway) | **Chưa biết** — cần sniff/dealer-DB | TB |
| Biết **security seed/key của cụm** | **Chưa biết** (key §3.2 là của gateway) | Cao — sai key có thể khoá/counter |
| Biết **DID/routine coding nav-HUD của cụm** | **Chưa biết** — nằm ở dealer ODX | Cao — ghi sai DID hỏng coding |

**Bước thử rẻ, an toàn (đọc-only, để thu thông tin — nếu có system-UID/root):**
- Dùng kênh SECRET-OBD/OTA-multiframe phát **`10 03` + `22 <DID>`** (ReadDID) tới **cụm** để đọc coding/equipment hiện tại (không ghi). Cần biết reqId/rxId cụm trước (sniff lúc dealer đọc, hoặc thử dải địa chỉ chẩn đoán cụm).
- Đọc `0x30100030` (CONFIG_STATUS) + `0x38B0002E` (STATUS) qua `BYDAutoInstrumentDevice.get()` để đối chiếu trạng thái provisioning (đọc-only, app-reachable, an toàn — đã có trong avenues đường 2/3).

**KHÔNG khuyến nghị** thử `2E`/`31` mù tới cụm khi chưa biết DID/security — rủi ro hỏng coding/DTC cao, không rollback được nếu thiếu bản gốc.

---

## Confirmed vs Cần xác nhận (honest — trace-den-tan-cung)

| Mục | Từ decompile (CONFIRMED) | Cần tool/on-car (UNCONFIRMED) |
|---|---|---|
| ECU giữ `0x38B00030` | ✅ Instrument-cluster (BYDAutoInstrumentDevice; native nav render + config store trong cụm) | — |
| Kênh UDS on-device tồn tại | ✅ OTA `0xAA000140` + SECRETOBD `0xAA000241`, chạy `10/27/2E/31` thật | Có bypass được system-UID gate trên xe owner không |
| Privilege | ✅ `android.uid.system`, platform-signed; per-property `checkPermission` | Root/platform-key trên xe owner khả thi tới đâu |
| Seed→key algo | ✅ Lộ trong `ObdDataManager.k()` — **cho GATEWAY 0x720/0x747** | Key/security-level của **cụm** (khác gateway) — chưa biết |
| Cơ chế provisioning | ✅ vehicleCode self-study + equipment/config flag (`BusinessSelfStudy`,`BydConfigInfo`,`ConfigureManager`) | Bit/DID equipment nav-HUD cụ thể; giá trị vehicleCode bật nav |
| `40d` 138/162 | ✅ Khớp khuôn vehicleCode 1-byte (`0x%02x`) | Không có bảng `0xA2→ON`; là hypothesis |
| DID coding nav-HUD | ❌ **Không có trong image** | Nằm trong dealer ODX/coding-DB của BYD |

**KHÔNG kết luận "không thể".** Điều kiện mở khoá đã nêu rõ: (a) dealer tool có ODX → chắc chắn set được; (b) on-device cần system-UID/root + reqId/rxId cụm + security key cụm + DID coding — tất cả **có thể thu được** bằng sniff phiên dealer / root + fuzz đọc-only, chưa làm.

---

## Phụ lục — Evidence & số học

**Feature ids (`jadx-DiCarServer/.../feature/...`):**
- `instrument/Instrument.java:534,536,537,538,539` — họ `INSTRUMENT_HUD_NAVIGATION_MAP_*` + `34C00026`.
- `instrument/InstrumentMapper.java` — `getDevice → BYDAutoInstrumentDevice`.
- `ota/Ota.java` — `OTA_MULTI_FRAME_SET 0xAA000140`, `_CANDATA 0x99000140`, `_ACK 0x99000141`, `OTA_REMOTE_DIAGNOSTIC_FUNCTION_REQUEST 0xAA000390`, `OTA_CMD_ECU_VER_SOFTCODE 0x99000053`, `OTA_HANDSHAKE_MESSAGE_SET 0xAA000254`, `OTA_RANDOM_NUMBER 0x19D00808`, `OTA_ENCRYPTED_NUMBER_SET 0x4EF40818`, `OTA_MAC_VALUE_1/2_SET`, `OTA_REPLACEMENT_AUTHENTICATION_*`.
- `setting/Setting.java:1832,1833` — `SETTING_SECRETOBD_DYNAMIC_DATA_CALLBACK 0x99000241`, `SETTING_SECRETOBD_SENT_MONITOR_TABLE_SET 0xAA000241`.
- `dtc/Dtc.java` — `DTC_MCU_INITIATE_DIAGNOSTIC_REQUEST 0x99000385`, `DTC_REMOTE_DIAG_SET 0x49A0042F`.
- `security/Security.java` — chỉ MAC/palm-vein/scratch-removal + `SECURITY_SAFECHIP_TYPE_SET 0xAA000297` (không phải SecurityAccess UDS).

**Factory tool (`jadx-BydDevelopmentTools/sources/`):**
- `a/a/a/l0/a.java` (DiagnoseManager) — TX qua `otaDevice.set(0xAA000140)`; RX listen `0x99000140` + `0x99000241`; monitor-table `settingDevice.set(0xAA000241)`; framing `c()`.
- `a/a/a/l0/b.java` (ObdDataManager) — `l()` `10 03`/`27 01`/`27 02`; `k()` seed→key; `w()` `2E 00 0B`; `x()`/`f()`/`g()` `31 01/02 00 06/07`; CAN 0x747/0x74F, 0x720/0x728.
- `com/byd/byddevelopmenttools/activity/{ObdData,Mapping}Activity.java` — UI 映射 (ID/网段 mapping) + safe-auth; không coding cụm.
- `com/byd/car/utils/PermissionUtils.java` — per-property `checkPermission(pid,uid)`.
- Manifest: `sharedUserId="android.uid.system"`.

**Native cluster (`sysimg/libBydDataSource.so`, symbols/strings):**
- `BusinessUdsManager::{DispatchUdsReq, HandleReadDIDRequest, HandleReadDIDResponse, HandleIOControlRequest/Response, callbackSelfStudyComplete}`.
- `BusinessSelfStudy::{selfStudyIsCompleted, vehicleCodeSelfStudyUpdate, canDataUpdate}`; log `"selfstduy has completed, vehicleCode is 0x%02x"`, `"Self Study Complete!"`, `"request read did[0x%X]"`, `"handle read DID request"`.
- `BydConfigInfo::{loadConfigXML, saveConfigXML, GetConfig_*, SetConfig_UINT8/…, config_data_array_item_type_}`; `ConfigureManager::{GetConfig_*, SetConfig_*, ThreadFunction, ResetToDefault}`.
- Nav: `BusinessUi1::{updateNaviType, updateNaviDisplay, sendNaviMessage}`; `NAVI_TYPE_FULL_SCREEN/SMALL_SCREEN/EASY/INVALID`, `getConfig … m_u8NaviType=%d`; `updateAcc/Aeb/Sla/…FunctionEquipment`, `"ADAS:Sla function equip self study result is %d"`, `delClusterConfigFile`.

**`libbyddiagnosticservice.so`** = cloud DTC uploader (`BYDDiagnosticService`, `HttpCommon::updateEcuConfig/uploadCloudDiagnosticData`, MQTT) — **không** phải UDS coding writer local. `diagnostic_config.json` = bảng DTC (DIAG-MCU/DIAG-DRIVER), không có DID/coding map.

**Số học (verify):**
- `0xAA000140` → signed `-1442840256` (TX frame). `0x99000140` → `-1728052928` (RX candata). `0x99000241` → `-1728052671`. `0xAA000241` → `-1442839999`.
- Sentinel `-2147482648 = 0x800003E8 = Integer.MIN_VALUE + 1000` (giá trị thật, không phải parse-fail `MIN_VALUE`).
- seed/key: `1694498846 = 0x6500001E`, `1694498845 = 0x6500001D`.
- CAN: `1863=0x747`, `1871=0x74F`, `1824=0x720`, `1832=0x728`. `40d`: `138=0x8A`, `162=0xA2`.

> RE-only. Không sửa code, không commit/push. Coding XE là quyết định của owner; đường on-device (2/5.2) cần system-UID/root + thông tin cụm chưa có — nêu kèm điều kiện mở khoá, không kết luận "không thể".
