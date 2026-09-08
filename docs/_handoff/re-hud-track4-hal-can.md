# RE Track 4 — HAL feature-ids + CAN + mọi bề mặt GHI ngoài-OBD (nav-HUD)

> **Nhiệm vụ:** liệt kê MỌI đường app / dadb / CAN có thể chạm để (a) **bật nav-HUD** hoặc
> (b) **bơm nav vào một kênh HUD zin đang render**. RE **đọc-hiểu**, không sửa/không commit.
> **Ngày:** 2026-08-19 · **Trim:** BYD Seal (DiLink, `fission_single_os=0` dual-OS) · **Owner:** dangkhoi.
>
> **Nguồn đọc first-hand phiên này:**
> - `…/clusternav-re/sysimg/jadx-DiCarServer/sources/com/byd/feature/instrument/Instrument.java` (820 dòng) + `InstrumentMapper.java` (873 dòng, 815 `put`)
> - `…/jadx-DiCarServer/…/setting/Setting.java` + `SettingMapper.java` (họ HUD/nav `0x4C10E0xx` + status `0x38B00xx`)
> - `…/jadx-DiCarServer/…/adas/Adas.java` (SLA / speed-limit / HUD-ADAS ids)
> - `…/jadx-DiCarServer/…/bydauto/IAutoMapper.java` (⇒ mapper KHÔNG mã hoá read/write)
> - `…/jadx-CanDataCollect/sources/**` (telemetry uploader — có API sniff CAN)
> - `app/src/main/java/com/byd/clusternav/modules/hal/BydHal.kt` (app ghi gì, rc=0 vs reject)
> - `docs/_handoff/hud-cluster-injection-findings-2026-08-10.md` (§1–§26 — bằng chứng on-car A11)
> - `docs/design/navigation-hud-evidence.html` (chỉ là contract Stage-2 của app; KHÔNG chứa evidence HAL/CAN)

---

## 0. TL;DR (câu trả lời ngắn, có bằng chứng)

| # | Câu hỏi | Trả lời ngắn |
|---|---|---|
| **Q1** | Ngoài `0x38B00030`, feature/device nào liên quan bật nav-HUD? ghi-được vs đọc-only? | **SettingDevice** giữ TOÀN BỘ bề mặt điều khiển HUD/nav ghi-được: họ `_SET` `0x4C10E0xx` + 2 fusion switch obfuscated `0x8e2fcdbf`/`0xd61b6746`. **InstrumentDevice** `0x38B00xx` phần lớn là **status/config đọc-only** (kể cả `0x38B00030`). **ADASDevice** `0x17F000xx` HUD-status = đọc-only. **R/W KHÔNG có trong lớp Java DiCarServer** — suy từ hậu tố `_SET` + rc on-car. |
| **Q2** | HUD zin render speed/limit/ADAS/call qua feature-id nào — nhồi nav vào được không? | HUD zin trim này có widget **km/h + speed-limit + ADAS**, **KHÔNG có widget nav** (owner xác nhận §19). Widget bind **một chiều** `Text{text:DataSource.x}` (§25 Q4), nguồn là data-item nội bộ do CAN đọc-only nuôi. Ghi guidance `0x43F01010`/SDK `sendSimpleGuidanceInfo` → **rc=0 nhưng HUD trống**. **Nhồi nav vào field call/ADAS: bất khả thi thực tế** (field không render trên kính + phá UI gốc). |
| **Q3** | CAN có frame bật HUD-nav-mirror / nav mà HUD zin nghe? ESP32-inject khả thi? | **KHÔNG có frame app gửi được để bật nav-HUD** — nó là **softcode MCU `0x38B00030`** (coding UDS, dealer/security-access). CanDataCollect = **telemetry thuần** (0 ref hud/nav). CAN-inject (on-device `BYDAutoTestDevice 0xAA00020F` hoặc ESP32) **chỉ khả thi để giả tín hiệu speed-limit VALUE** (#3) — cần arbitration-id + bit-layout SLA (MCU-side, chưa có). **Không tạo được widget nav** trên HUD. |
| **Q4** | Bề mặt GHI ngoài HAL-feature nào CHƯA thử? | **CHƯA thử:** (1) Binder `ICollect2FileStoreService.readTextFile` qua `CarServiceProvider` (đọc `/collect2` config = CAN-id SLA); (2) `BYDAutoBigDataDevice` sniff whole-frame; (3) `BYDAutoTestDevice` inject với frame THẬT; (4) `BYDAutoOtaDevice` UDS coding `0x38B00030=1`; (5) sweep 2 fusion switch `0x8e2fcdbf`/`0xd61b6746`. **Đã thử & fail:** HAL set, AMap broadcast, AutoContainer sendInfo/sendInfo2, SDK guidance, system property, ZMQ (khác domain), đọc /collect2 (uid 2000 denied). |

**Trần thật (honest ceiling):** nav turn-by-turn trên kính HUD ⇒ hoặc **provision `0x38B00030=1` bằng UDS coding** (gated seed/key, dealer-tool) hoặc widget nav vốn **vắng trong scene HUD** trim VN này (chỉ xác nhận được on-car). Speed-limit số tuỳ ý ⇒ **CAN-inject tín hiệu SLA** (cần arbitration-id, chỉ lấy được bằng sniff). Cả hai đều **ngoài tầm ADB/app thuần** trên trim này — nhưng còn **5 cửa CHƯA thử** ở Q4 (non-root, on-car-testable) trước khi kết luận "bất khả thi".

---

## Q1 — Device + feature-id liên quan bật nav-HUD (ngoài `0x38B00030`), ghi-được vs đọc-only

### 1.1 Nguồn phân loại R/W (caveat quan trọng, đọc trước)

`com/byd/feature/bydauto/IAutoMapper.java` chỉ có `getDevice(ctx)` + `transformFeatureId(String)→int`. Các `*Mapper.java` (Instrument/Setting/Adas) là **map id→tên thuần** (815 `map.put` trong InstrumentMapper). **⇒ Lớp Java DiCarServer KHÔNG mã hoá "read-only/writable".** Phân loại R/W trong doc này suy từ:
1. **Quy ước tên:** hậu tố `_SET` = lệnh ghi; `_STATUS` / `_FEEDBACK` / dải `0x30100xxx` + phần `0x38B000xx` (feedback) = **status đọc-only**.
2. **rc on-car (A11):** `rc=0` = HAL nhận (vẫn có thể no-op hiển thị); `rc=-2147482648` (`0x800003E8`) = **NOT provisioned / no-permission** (từ chối); id đọc-only trả **sentinel** khi `get`. (Hằng số này = `BydHal.NOT_PROVISIONED_RC`.)

### 1.2 InstrumentDevice — họ `0x38B*` (từ `Instrument.java`, đọc first-hand)

| Feature-id | Tên | Loại (suy luận) |
|---|---|---|
| `0x38B00025` | INSTRUMENT_HUD_DEVELOPER_MODE_FEEDBACK | status R |
| `0x38B0002A` | INSTRUMENT_DYNAMIC_NAVI_FUNCTION | config/status R |
| `0x38B0002B` | INSTRUMENT_HUD_DRIVING_AMBIENT_CONFIG | config R |
| `0x38B0002C` | INSTRUMENT_DRIVING_AMBIENT | R |
| `0x38B0002E` | **INSTRUMENT_HUD_NAVIGATION_MAP_STATUS** | **status R** (trạng thái mirror cụm→HUD) |
| `0x38B00030` | **INSTRUMENT_HUD_NAVIGATION_MAP_CONFIG** | **cổng provisioning — đọc = sentinel trên xe này** (write REJECTED) |
| `0x38B0003D` | INSTRUMENT_OBSTACLE_WARNING_FUNCTION_CONFIG | config R |
| `0x38B00040` | INSTRUMENT_OBSTACLE_WARNING_FUNCTION_SWITCH_FEEDBAEK | status R |
| `0x38B00042` | INSTRUMENT_DISPLAY_CONTENT_SET_FUNCTION_CONFIG | config R |
| `0x38B00044` | INSTRUMENT_DISPLAY_CONTENT_SET_FEEDBAEK | status R |

**Ghi-được liên quan trên Instrument** (KHÔNG thuộc `0x38B`, nhưng là cặp write của map-HUD):
- `0x32B1102E` INSTRUMENT_HUD_NAVIGATION_MAP_SET — **write, nhưng REJECTED** vì `0x38B00030` chưa provisioned (§2, §16).
- `0x34C00026` INSTRUMENT_HUD_MAP_FORMAT_34C_SET — write (format map).
- `0x32B1102C` INSTRUMENT_DRIVING_AMBIENT_SET — cặp write của `0x38B0002C`.

➡️ **Kết luận Instrument/`0x38B`:** gần như toàn bộ là **đọc-only (status/config)**. `0x38B00030` là cờ **capability MCU**, không ghi từ head-unit. Không có "sibling `_SET`" nào bật được nav-map ngoài `0x32B1102E` (đã reject).

### 1.3 SettingDevice — TOÀN BỘ bề mặt điều khiển HUD/nav GHI-ĐƯỢC (từ `Setting.java`)

Đây là phát hiện chính cho Q1: **họ `_SET` nằm ở SettingDevice, cặp status `0x38B00xx` là bản đọc-only.**

| SET (ghi-được) | id | STATUS/FEEDBACK (đọc-only) | id | Ghi chú on-car |
|---|---|---|---|---|
| SET_HUD_SWITCH_SET | `0x4C10E023` | SET_HUD_SWITCH_STATUS_FEEDBACK | `0x38B0001C` | write=0 **KHÔNG tắt HUD** (§19) |
| SET_HUD_MODE_SET | `0x4C10E025` | SET_HUD_MODE_FEEDBACK | `0x38B0000D` | **sweep ĐỔI MÀU HUD blue↔white** (write tới được settings) |
| SET_HUD_MODE_CHOICE_SET | `0x4C10E03C` | SET_HUD_MODE_CHOICE | `0x38B00038` | — |
| SET_NAVI_SCREEN_STATUS_SET | `0x4C10E015` | — | — | app dùng (chế độ nav trên cụm) |
| SET_MAP_SENDING_STATUS_SET | `0x4C10E01D` | — | — | — |
| SET_DYNAMIC_NAVI_FUNCTION_STATUS_SET | `0x4C10E03A` | SET_DYNAMIC_NAVI_FUNCTION_STATUS_FEEDBACK | `0x38B00028` | — |
| SET_DIRECTION_FUNCTION_STATUS_SET | `0x4C10E036` | SET_DIRECTION_FUNCTION_STATUS_FEEDBACK | `0x38B00024` | — |
| SET_SAFE_DRIVING_ASSIST_STATUS_SET | `0x4C10E030` | SET_SAFE_DRIVING_ASSIST_STATUS_FEEDBACK | `0x38B0001E` | **gate ADAS trên HUD**: off→ADAS biến mất |
| **SET_NAVIGATION_FUSION_SWITCH_SET** | **`0x8e2fcdbf`** | SET_NAVIGATION_FUSION_SWITCH | `0x38B00034` | **bật nav-fusion lên HUD/cụm — ứng viên #1** |
| **SET_SAFETY_DRIVING_AID_FUSION_SWITCH_SET** | **`0xd61b6746`** | SET_SAFETY_DRIVING_AID_FUSION_SWITCH | `0x38B00032` | tắt → chống SLA đè (cho #3) |
| SETTING_HUD_IMAGE_TEXT_INFO_FUSION_SWITCH_SET | `0x4C10E044` | — | — | — |
| SETTING_SET_MULTIMEDIA_INFO_FUSION_SWITCH_SET | `0x4C10E03E` | — | — | — |
| SETTING_EASY_NAVI_SIGNAL_MAP_TYPE | `0x4C10E040` | — | — | — |
| SET_HUD_CONFIG | — | (đọc) | `0x38B00015` | đọc=1 (W-mode); **write REJECTED** — thực chất capability |

- **FSE variants** (`0x4C500008/10/12/16`, `0x4C50000B`) = HUD "FSE" (nhiều khả năng màn phụ / rear) — coi như N/A trim này.
- `0x4C500018` SETTING_MULTIMEDIA_INFO_FUSION_SWITCH_SET, `0x4C50001A` SETTING_IMAGE_TEXT_INFO_FUSION_SWITCH_SET — biến thể fusion.
- **HUD vị trí vật lý (ghi-được nhưng KHÔNG phải content):** `0x4C10A03E` (adaptive), `0x4C10C030` (linked-seat height), `0x32B0A044` (request-command), `0x34C0002C` (projection status), `0x34C00030`/`0x4EF34034` (steering-wheel adjust).

➡️ **Kết luận Setting:** đây là bề mặt **ghi-được** đúng nghĩa cho HUD/nav. On-car (§19) đã chứng minh write **tới được HUD ở tầng SETTINGS** (đổi màu/mode) nhưng **KHÔNG bật được CONTENT nav**. Hai fusion switch `0x8e2fcdbf`/`0xd61b6746` là ứng viên **chưa sweep dứt điểm** (xem Q4).

### 1.4 ADASDevice — HUD/SLA (từ `Adas.java`)

| id | Tên | Loại |
|---|---|---|
| `0x2D500020` | ADAS_SLA_OUTPUT_SPEED_LIMIT | **READ-ONLY** (nguồn số speed-limit hiển thị) |
| `0x31600025` | ADAS_SLA_STATE | status R |
| `0x38500022` | ADAS_SLA_STATE_SET | **write** (0/1 ok; mode 3 REJECTED) |
| `0x38500044` | ADAS_ISLA_SWITCH_SET | write |
| `0x43F03028` / `0x43F0302C` | ADAS_SPEED_LIMIT_ASSIST_OFFSET_KPH/MPH_SET | write (**REJECTED on-car**) |
| `0x32B0E018` | ADAS_SMART_SPEED_LIMIT_CONTROL_SET | write |
| `0x17F00008` / `0x17F0000E` | ADAS_HUD_SYSTEM_STATUS / ADAS_HUD_SIGNAL_EFFECTIVE | **READ-ONLY** (trạng thái ADAS→HUD) |
| `0x2F2xxxxx` | ADAS_TSR_HIGH_TARGET_* (telemetry) | READ-ONLY |

➡️ **Kết luận ADAS:** HUD/SLA của ADAS là **đọc-only** ở phần output + status; chỉ có state/switch bật-tắt ghi-được (không phải kênh content).

---

## Q2 — HUD zin render speed/limit/ADAS/call qua feature nào — "nhồi" nav vào được không?

### 2.1 HUD zin render gì, qua đâu
- **Speed / speed-limit / ADAS**: nguồn = **ADAS SLA output `0x2D500020` (đọc-only)**, chảy qua pipeline nội bộ **MCU → data-provider → ZMQ → cluster/HUD** (RE `libBydDataSource.so`, §22–24). Mọi HAL write vào id speed-limit (statistic/setting/adas) đều **rc=0 nhưng hiển thị bỏ qua** (§19, §24). Gate ADAS-trên-HUD = `0x4C10E030` (chỉ bật/tắt, không "set value").
- **Call**: `0x43E0000C` CALL_STATE_SET, `0x43E00018/20/28` CALL_TIME_*, `0x43FC1008` CALL_INFO_SET, `0x43FC2010..0x43FCE010` CALL_INFO_C2..CE (số điện thoại). Đây là **SET ghi-được** cho overlay cuộc gọi — **nhưng render trên cụm, chưa xác nhận trên kính HUD**.
- **Nav guidance** (ghi-được): `0x43F01010` GUIDE_INFO_SIMPLE_SET (mũi tên), `0x43F01018` FRONT_CROSSING_DISTANCE_SET (cự ly), `0x43F01030` GUIDE_INFO_AND_ROAD_AHEAD_DISTANCE_SET, `0x43FA1008` TARGET_NEXT_PATHNAME_INFO_SET (tên đường), `0x43F02010/18/1E/24/28` trip (giờ/phút/giây/ngày/mileage), `0x43F09010/18/20/28` ETA + SDK `sendSimpleGuidanceInfo`. **On-car: TẤT CẢ rc=0 nhưng HUD kính TRỐNG** (§19) — vì widget nav bị coding-gate tắt (`0x38B00030`=sentinel). Chúng tới **cụm-centre**, không tới kính.

### 2.2 Có "nhồi" nav vào field đang render (ADAS / tốc-độ / text cuộc-gọi) được không?
- **QML bind một chiều** (bằng chứng §25 Q4, đã giải nén `cluster_theme*.rcc`): `Text{ text: DataSource.trafficSignValue }`, `switch(DataSource.trafficSignType)`. `DataSource.*` là **C++ context property; QML KHÔNG BAO GIỜ gán ngược**. ⇒ chỉ đổi được **giá trị** widget hiện, KHÔNG repurpose field để hiện text nav tuỳ ý; mà giá trị đó do **data-item CAN-fed đọc-only** nuôi, không phải HAL.
- **Repurpose field call/ADAS cho nav**: (1) chưa xác nhận field call hiện trên **kính HUD** (nó là overlay cụm), (2) kể cả ghi được thì đó là field ngữ nghĩa "điện thoại" — nhồi nav vào sẽ **phá UI cuộc gọi**, không phải nav thật, (3) không có binding để text tuỳ ý thành widget nav.
- **Điểm sáng duy nhất**: opcode-2 (`ac 1000 2`) từng khiến **cụm hiện "60" độc lập với SLA đọc-only** (§20) — nhưng đó là **đường fake-CAN test nội bộ** (giá trị cứng, không tham số hoá), tức **tầng CAN**, không phải HAL field.

➡️ **Kết luận Q2:** **KHÔNG khả thi** nhồi nav vào field HUD đang render trên trim này. HUD **thiếu widget nav** (coding-gate) và widget content bind **một chiều** vào data-item CAN nội bộ. Chỉ khả thi nếu (a) provision `0x38B00030` (UDS coding) để widget nav xuất hiện, hoặc (b) đổi **giá trị** tín hiệu nguồn qua CAN-inject (chỉ áp dụng cho số speed-limit, không phải nav).

---

## Q3 — CAN: frame bật HUD-nav-mirror / nav mà HUD zin nghe? ESP32/CAN-inject khả thi?

### 3.1 CanDataCollect = telemetry thuần, KHÔNG có frame hud/nav
- `grep -niE 'hud|navigation|instrument|0x38B|_nav'` trên toàn bộ `jadx-CanDataCollect/sources` (trừ fastjson) = **0 kết quả**. Resource/assets grep 'nav|hud|speed.?limit|traffic' = **rỗng**. Đây là service **thu CAN → upload HTTPS** (fastjson), không phải bộ phát display.

### 3.2 NHƯNG CanDataCollect lộ hạ tầng CAN (sniff, không hardware) — first-hand
`service/CanDataCollectService.java`:
```
BYDAutoBigDataDevice.getInstance(ctx).registerListener(mBigDataListener, new int[]{-1728053216});  // L375-377
public void onWholeFrameDataChanged(byte[] bArr) { … }                                              // L290  (raw whole CAN frame)
BYDAutoVehicleDataDevice.getInstance(ctx).sendRegisterTable(3, table);                              // lập trình MCU stream id nào
BYDAutoPowerDevice.getInstance(ctx).wakeUpMcu();                                                     // đánh thức MCU trước cấu hình
```
`entity/CanDataHandle.java` — format frame (encode BE):
- **register-table entry** (`collect_id_conf`): `[canid BE:4][subid:1][canChanel:1][mode:1][flag:1]` (L232-247).
- **received frame** (`recv_can`): `[canid BE:4][subid:1][canChanel:1][data…]`.
- remove-table mẫu: `sendRegisterTable(3, {0,0,3,213,0,3,0,3})` (L331).

➡️ Đây là **CAN monitor on-device không cần hardware/root** — cách lấy arbitration-id SLA/nav (phương pháp "wiggle": đổi speed-limit thật rồi diff frame). (Lưu ý: `BYDAutoSettingDevice.get(int[], Class)` cũng là 1 **sync-get 2 tham số** thấy ở L251 — khác `get(int[])` mà `BydHal.tryGet` tìm.)

### 3.3 Có frame nào app gửi để BẬT nav-HUD-mirror không? → KHÔNG
- HUD-nav-mirror là **softcode MCU `0x38B00030`** (variant coding instrument-ECU), set bằng **UDS `WriteDataByIdentifier`** (cần coding DID + rất có thể security-access 27 seed/key). **Không frame CAN nào app phát ra lật được cờ này** (§14, §24 — dò cả firmware không có app factory ghi MCU coding).
- Số speed-limit HUD hiện = **tín hiệu SLA CAN** (arbitration-id + bit-layout nằm MCU-side, trong `/collect2/byd_datasource_config.xml` bị khoá root).

### 3.4 CAN-inject / ESP32 khả thi tới đâu?
- **On-device (không hardware):** `BYDAutoTestDevice.set(new int[]{0xAA00020F} TEST_SIMULATE_DOWN, frameBytes)` / `0xAA000210` (UP). Đây là **CƠ CHẾ ĐÃ CHỨNG MINH** = đúng đường `com.byd.cluster.spi` của ClusterDebug (§13.2), và opcode-2 chứng minh đường decode này **đẩy được số lên cụm ("60")**. **Cần** arbitration-id + bit-layout SLA (chưa có).
- **ESP32 / CAN interface ngoài:** khả thi vật lý trên bus ADAS/instrument **NẾU** có (a) arbitration-id + payload tín hiệu SLA/traffic-sign, (b) truy cập bus vật lý. Nhưng: id **không nằm trong image head-unit** (phải sniff), ghi bus ADAS **rủi ro cao** (DTC/hệ an toàn), và nó bơm **số speed-limit (#3), KHÔNG phải nav (#1)**.
- Community `wheregoes/byd-dolphin-hacking` (Dolphin/DiLink 50, khác trim Seal) công bố **UDS diag-id theo net** (ADAS net; left `0x720/728`, right `0x747/74F`, IPB `0x782/78A`) nhưng **KHÔNG** có arbitration-id SLA/traffic-sign/nav và **KHÔNG** có coding DID.

➡️ **Kết luận Q3:** **Không frame app-gửi nào bật nav-HUD-mirror** (nó là coding MCU). CAN-inject (on-device TEST device hoặc ESP32) **chỉ khả thi để giả tín hiệu speed-limit VALUE** — và chỉ sau khi có arbitration-id SLA (bằng sniff). **Không tạo được widget nav** trên HUD bằng CAN.

---

## Q4 — Mọi bề mặt GHI ngoài HAL-feature: cái nào CHƯA thử?

### 4.1 ĐÃ thử & FAIL (A11 / on-car — không lặp lại)
| Bề mặt | Kết quả |
|---|---|
| HAL `set` (instrument/setting/adas/statistic) | rc=0 (no-op hiển thị) hoặc rc=-2147482648 (reject); không inject content |
| AMap broadcast `AUTONAVI_STANDARD_BROADCAST_SEND` | render nav **cụm** OK; **không có field speed-limit; không lên HUD** |
| AutoContainer `sendInfo(id,sub,str)` sweep 0..578 | control-path, **0 hiển thị** |
| AutoContainer `sendInfo(1000,cmd,"")` (ClusterDebug ch) | opcode-only, không tham số value; opcode-2 = fake-CAN "60" cứng |
| AutoContainer `sendInfo2(ch,bytes)` (ch4 nav / ch8 RCC) | chỉ nav-FlatBuffer (không field speed-limit) + RCC RPC hẹp |
| SDK `sendSimpleGuidanceInfo`/`sendSafeGuidanceInfo` | rc=0, **HUD trống** |
| System property | chỉ `sys.init.navi_protect`/`sys.change_navi_auth`; **không** có prop hud/sla |
| ZMQ data bus `192.168.195.x:8889/6666` | ở **fission OS khác**, Android chỉ có `lo`+`rmnet` → **no route, no root** |
| Đọc `/collect2/*` bằng shell | **Permission denied** (uid 2000) |

### 4.2 CHƯA thử (non-root, on-car-testable — cửa còn mở)
1. **Binder `com.byd.car.collect2.ICollect2FileStoreService`** (DiStore file-store, chạy trong DiCarServer **uid đặc quyền**). Resolve qua ContentProvider **`content://com.byd.car.server.provider.CarServiceProvider`** (extends `BinderProvider`) → `readTextFile(path)` / `readFile(path)`. Cho client **non-root đọc `/collect2/byd_datasource_config.xml`** (⇒ **arbitration-id + bit-layout SLA trực tiếp**) + `/collect2/dataCollect/datacollectioncfg`. **Rủi ro:** có thể chặn theo uid/permission caller. *(navopen-v4 đã thêm verb `readcfg` — §26 doc findings.)*
2. **`BYDAutoBigDataDevice` sniff whole-frame** (Q3.2): `registerListener({-1728053216})` + `sendRegisterTable(3, ids)` → `onWholeFrameDataChanged`. **Chưa thử qua navopen bypass-context**; `getbytes` trên `TEST_CANIN_*` trước đây là **sai API**. → lấy arbitration-id SLA/nav live. *(navopen-v4: verb `canmon`/`canreg`.)*
3. **`BYDAutoTestDevice` inject với frame THẬT**: `set({0xAA00020F}/{0xAA000210}, frameBytes)` — cơ chế proven, nhưng cần frame SLA thật từ (1) hoặc (2).
4. **`BYDAutoOtaDevice` UDS coding**: `set({0xAA000140}, udsFrame)` + listener `{0x99000140}`; đọc softcode `getbytes ota 99000053`. Đường **provision `0x38B00030=1`** (mở #1) — **nhưng cần coding DID + security-access (27 seed/key)** = cổng thật, nhiều khả năng chặn.
5. **Sweep 2 fusion switch** `SET_NAVIGATION_FUSION_SWITCH_SET 0x8e2fcdbf` + `SET_SAFETY_DRIVING_AID_FUSION_SWITCH_SET 0xd61b6746` cùng họ `0x4C10E0` HUD/nav + guidance feeds (combo §16/§18). §19 mới thử HUD mode/switch + guidance (rc=0, không content); **2 fusion switch obfuscated có thể CHƯA sweep dứt điểm** — rẻ, đáng xác nhận.

### 4.3 Sắp xếp ưu tiên (an toàn → rủi ro)
1. **Cửa (5)** — sweep 2 fusion switch + đọc feedback `0x38B00034`/`0x38B00032` (rẻ nhất, off-đè-vào-app-hiện-có).
2. **Cửa (1)** — `readcfg` config `/collect2` (nếu trả text ⇒ có arbitration-id SLA, mở khoá #3 không cần đoán).
3. **Cửa (2)** — `canmon` sniff (nếu (1) bị gate) → wiggle speed-limit thật, diff frame.
4. **Cửa (3)** — inject frame SLA đã có → xem cụm/HUD render số ta chọn (#3).
5. **Cửa (4)** — UDS coding `0x38B00030=1` (làm cuối; gated seed/key; rủi ro DTC/misconfig; có recovery).

---

## Phụ lục A — Bản đồ feature-id nav/HUD theo họ (Instrument.java, đọc first-hand)

**`0x43E*` (SET trạng thái call/music/nav-status — InstrumentDevice):**
`0x43E0000A` MUSIC_STATE_SET · `0x43E0000C` CALL_STATE_SET · `0x43E0000E` RADIO_STATE_SET · `0x43E00010` MUSIC_PLAYBACK_PROGRESS_SET · `0x43E00018/20/28` CALL_TIME_HOUR/MIN/SEC_SET · `0x43E00038` SEND_DESTINATION_STATUS_SET · **`0x43E0003A` SEND_NAVI_STATUS_SET** (latch trạng thái nav — app ghi=2 lúc push, =4 lúc clear).

**`0x43F*` (guide-info / call-info / nav-trip SET — InstrumentDevice):**
`0x43F01010` GUIDE_INFO_SIMPLE_SET (mũi tên) · `0x43F01018` FRONT_CROSSING_DISTANCE_SET (cự ly) · `0x43F01030` GUIDE_INFO_AND_ROAD_AHEAD_DISTANCE_SET (dualIcon) · `0x43F02010/18/1E/24/28` NAVI_TRIP hour/min/sec/day/mileage · `0x43F03010` GUIDE_INFO_CAMERA_SET · `0x43F03018` CAMERA_DISPLAY_STATE_SET · `0x43F0301C` NAVI_CAM_REMAINING_MILEAGE_SET · `0x43F04010` GUIDE_INFO_SAFETY_SET · `0x43F04018` SAFETY_DISPLAY_STATE_SET · `0x43F0401C` NAVI_SAFETY_REMAINING_MILEAGE_SET · `0x43F08010` NAVI_LEAD_MSG_ADVANCED_SET · `0x43F08018` DISTANCE_OF_TARGET_AHEAD_ADVANCED_SET · `0x43F08030` GUIDE_INFO_ADVANCED_ACTION_SET · `0x43F09010/18/20/28` EXPECTED_ARRIVE day/hour/min/sec · `0x43F11010` DISTANCE_FROM_LAST_TRAFFIC_LIGHT_SET · `0x43FA1008` TARGET_NEXT_PATHNAME_INFO_SET (tên đường) · `0x43FB1008` MUSIC_INFO_SET · `0x43FC1008` CALL_INFO_SET · `0x43FC2010..0x43FCE010` CALL_INFO_C2..CE · `0x43FD1008` RADIO_INFO_SET · `0x43FE1008` TARGET_HOME_ADDRESS_INFO_SET · `0x43FF1008` TARGET_COMPANY_ADDRESS_INFO_SET · `0x43FFF030` NAVI_MAP_ARRIVAL_DESTINATION_PASSPOINT_STATUS_SET.
> Biến thể **OVERSEA** (export) song song: `0x1F701010` (mũi tên), `0x1F701018` (cự ly), `0x1F7A1008` (tên đường), `0x1F702010/18/1E/28` (time/mileage), `0x1F705010/18/20/28` (ETA). App (`BydHal.writeNavFrame`) ghi CẢ 2 họ.

**`0x4C10E*` — KHÔNG có trong Instrument.java; toàn bộ nằm ở SettingDevice** (bảng Q1.3). Đây là điểm dễ nhầm: `SET_NAVI_SCREEN_STATUS_SET 0x4C10E015` mà app dùng là **Setting**, không phải Instrument.

**Nav ids khác (Instrument):** `0x40C0C01D` NAVIGATION_ACTIVATED_SET · `0x4C10A018` NAVI_TYPE_SET · `0x4C130041` NAVIGATION_STYLE_SET · `0x4C212010/20/30/32` NAVI_ESTIMATED_TIME/MILEAGE/USAGE/CHARGING_STATION_SET · `0x40C0103B` GET_NAVI_DESTINATION.

## Phụ lục B — App ghi gì (BydHal.kt) — đối chiếu rc

`writeNavFrame()` (in-process, bypass-context) ghi mỗi frame (~4/s):
- `INSTRUMENT_SEND_NAVI_STATUS_SET=2` (latch, chỉ real-push) · `SET_NAVI_SCREEN_STATUS_SET=screenMode` (Setting, chỉ real-push, `NAV_SCREEN_MODE_ON=3`)
- `GUIDE_INFO_SIMPLE_SET=icon` · `GUIDE_INFO_AND_ROAD_AHEAD_DISTANCE_SET=icon` · `FRONT_CROSSING_DISTANCE_SET=segMeters` · `TARGET_NEXT_PATHNAME_INFO_SET=road(UTF-16LE)` · + họ OVERSEA `0x1F7*` · + trip/ETA (`0x43F02*`/`0x43F09*` + oversea `0x1F702*`/`0x1F705*`)
- SDK: `sendSimpleGuidanceInfo(icon,dist)` · `sendNextPathName(road)` · `sendRestRouteInfo(h,m,meters)` (chỉ real-push)
- **Cache reject:** rc == `NOT_PROVISIONED_RC = -2147482648` (`0x800003E8`) ⇒ id vào `rejectedFeatures`, SKIP frame sau (hết spam `no permission device 1007`). SDK ném ⇒ `rejectedSdk`. Xe provision oversea (Sealion 6) không nhận sentinel ⇒ vẫn ghi.
- **Bằng chứng on-car (§19):** các lệnh này **rc=0 nhưng HUD kính KHÔNG hiện nav** trên trim Seal VN (widget nav coding-gated). Chúng tạo được "Giữa + ETA" ở **cụm-centre**, không phải kính.

## Phụ lục C — Chuỗi pipeline hiển thị (vì sao HAL bị bỏ qua)
`CAN bus` → data-provider (`CanDataWrapper::GetData_*`) → data-item id (vd `trafficSignValue`=id **564/0x234**) → `DataItemWrapper::SetDataItem_INT` → (JNI) `DataSourceManager::callbackDataItemUpdateInt` → `handleDataItemChanged(id,QVariant)` → **widget cụm/HUD**. Transport provider↔cluster = **ZMQ PUB/SUB TCP `192.168.195.2:8889` + `.3:6666`** (mạng nội bộ fission — Android không route tới). ⇒ widget **chỉ đọc** cái tới trên bus này (nguồn = SLA CAN đọc-only); HAL write không vào bus ⇒ bị bỏ qua. `0x2D500020` là nguồn đọc-only.

---

## Kết luận & việc tiếp (không sửa/không commit phiên này)
- **Bật nav-HUD (#1):** đường thật = provision **`0x38B00030=1`** qua **UDS coding** (`BYDAutoOtaDevice 0xAA000140`) — cần coding DID + security-access (chưa biết). Hoặc widget nav vốn **vắng scene HUD** trim VN (chỉ on-car xác nhận). Không có HAL/CAN/broadcast app-reachable nào bật nó.
- **Nhồi nav vào HUD field (#2):** bất khả thi thực tế (bind một chiều + không có widget nav).
- **CAN (#3 speed-limit VALUE):** cơ chế inject sẵn (`BYDAutoTestDevice 0xAA00020F`, on-device hoặc ESP32) nhưng **thiếu arbitration-id SLA** — lấy bằng **Binder readcfg (cửa 1)** hoặc **BigData sniff (cửa 2)**.
- **5 cửa Q4** đều **non-root, on-car-testable** và **CHƯA thử** — phải test hết trước khi tuyên bố "bất khả thi" (theo rule trace-đến-tận-cùng).

**Trạng thái RE:** đọc-hiểu hoàn tất; **không** sửa file mã nguồn, **không** commit/push. Handoff này là output.
