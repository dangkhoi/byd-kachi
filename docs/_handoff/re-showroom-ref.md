# RE — SHOWROOM / DEMO mode: có bơm full nav (mũi tên + cự ly + TÊN ĐƯỜNG) lên HUD BYD không?

> **Nhiệm vụ:** tìm xem có **chế độ showroom/demo** nào phát *full nav* (mũi tên + cự ly + **tên đường**)
> lên **HUD/cụm BYD** — để làm **bản mẫu** cho app bắt chước. RE **đọc-hiểu**, KHÔNG sửa/không commit.
> **Ngày:** 2026-08-19 · **Trim:** BYD Seal (DiLink, `fission_single_os=0`) · **Owner:** dangkhoi
>
> **Nguồn đọc first-hand phiên này (đường tuyệt đối):**
> - `…/clusternav-re/sysimg/jadx-DiCarServer/sources/com/byd/feature/setting/Setting.java` + `SettingMapper.java`
> - `…/jadx-DiCarServer/…/instrument/Instrument.java` + `InstrumentMapper.java` (họ HUD-nav-map + guide-info)
> - `…/jadx-ClusterDebug/sources/com/byd/clusterdebug/{MainActivity,SecondActivity,ClusterDebugService,BroadcastReceiverCAN}.java`
> - `…/jadx-BydDevelopmentTools/sources/com/byd/byddevelopmenttools/**` (RollbenchMode/RepairMode/OBD…)
> - `…/tmap_c1/out/sources/android/hardware/bydauto/instrument/BYDAutoInstrumentDevice.java` (SDK stub nav) + `BYDAutoFeatureIds.java` + `setting/BYDAutoSettingDevice.java`
> - `…/tmap_c1/out/sources/**` (map app SKT Tmap — grep caller nav SDK)
> - Đối chiếu: `docs/_handoff/re-hud-track4-hal-can.md` (§Q1–Q4, kết luận coding-gate `0x38B00030`)

---

## 0. TL;DR — trả lời thẳng (có bằng chứng)

| # | Câu hỏi | Trả lời ngắn |
|---|---|---|
| **Q1** | Showroom/demo = setting id nào, bật thế nào? | **`SET_EXHIBITION_MODE = 0x31E00008`** (giá trị `INVALID=0 / INDOOR=1 / OUTDOOR=2 / NON=3`). NHƯNG nó điều khiển **cho phép nổ máy + nhắc chuyển số trong showroom**, **KHÔNG dính gì tới nav**. `Setting.java:2639`, `BYDAutoSettingDevice.java:114-117`. |
| **Q2** | Showroom ON → service nào feed nav demo ra HUD? register tên đường? kèm CHECK/CONFIG/FORMAT? | **KHÔNG có service nào.** Grep toàn image (DiCarServer/ClusterDebug/DevTools/TMap) cho `demo/演示/replay/自动播放/retail` = **0 service replay nav**. Register tên đường vẫn là `0x43FA1008` (guide-info-simple family), **không** có "demo mode" nào tự ghi nó. Có một **read-only gate** liên quan: `INSTRUMENT_GET_ROAD_NAME_CHECK_STATE = 0x420A1010` (VALID=1/INVALID=2). |
| **Q3** | Showroom có set cờ enable (32B1102E/34C00026/38B00030) trước khi feed? = enable-path? | **KHÔNG.** 4 register HUD-nav-map (`0x32B1102E` SET, `0x34C00026` FORMAT, `0x38B00030` CONFIG, `0x30100030` CONFIG_STATUS) **chỉ xuất hiện trong định nghĩa `Instrument.java`** — **KHÔNG app nào trong image GHI chúng** (grep hex toàn image = chỉ khớp file định nghĩa). Showroom mode không đụng tới. `0x38B00030` = **cổng coding MCU** (kết luận track4). |
| **Q4** | Showroom làm GÌ KHÁC app (app ghi 43F01010+43FA1008+43F02018/1E)? Thiếu bước enable/format/check nào? | **KHÔNG khác gì ở tầng app/HAL.** SDK OEM (`BYDAutoInstrumentDevice`) chỉ phơi ra đúng bộ `sendSimpleGuidanceInfo / sendNextPathName / sendRestRouteInfo / sendAutoNaviStatus` — **y hệt** cái app đang ghi. **Không có method enable/format/config nào khác** trong SDK. Khác biệt "hiện vs trống trên kính HUD" **KHÔNG phải do app thiếu 1 lệnh HAL** — mà do **coding MCU `0x38B00030`** + widget nav trong scene HUD. |
| **Q5** | Đề xuất chuỗi register+giá trị showroom để tên đường lên (app/probe tái lập) | **Không tồn tại "chuỗi showroom" riêng.** Chuỗi producer chuẩn = **chính cái app đã làm** (xem §5). Để lên **kính HUD** cần thêm `0x38B00030=1` (coding MCU, **không app-writable**). Đề xuất **probe**: (a) đọc `0x420A1010` để biết tên đường có bị MCU từ chối không; (b) thử ClusterDebug **op39 "简易导航"** (`sendInfo(1000,39)`) xem có sáng HUD/strip không. |

**Kết luận một dòng:** **KHÔNG có "showroom/demo mode" nào phát full nav lên HUD để bắt chước.** `EXHIBITION_MODE (0x31E00008)` là chế độ *xe* (nổ máy/số), không phải nav. Cổng thật để nav lên **kính HUD** vẫn là **coding MCU `0x38B00030`** — đúng như `re-hud-track4-hal-can.md` đã kết luận. App **đã** tái lập đúng chuỗi guide-info của producer; chỗ hụt là **coding**, không phải thiếu register.

---

## Q1 — Showroom/demo mode = setting id nào, bật thế nào?

### 1.1 Đúng có "exhibition/showroom mode" — nhưng là chế độ *xe*, không phải nav

`jadx-DiCarServer/…/setting/Setting.java`:
```
2639:  public static final String SET_EXHIBITION_MODE                 = "0x31E00008";
2640:  public static final String SET_EXHIBITION_START_ENGINE_PERMIT_SET = "0x2EA00010";
2641:  public static final String SET_EXHIBITION_START_ENGINE_PROMPT    = "0x46C0003C";
 685:  public static final String SETTING_EXHIBITION_MODE_GEAR_SHIFT_PROMPT = "0x32600038";
```
`SettingMapper.java:121,251,542,855` map các id trên về tên (mapper thuần).

Giá trị của mode (SDK `tmap_c1/…/setting/BYDAutoSettingDevice.java`):
```
114:  public static final int EXHIBITION_MODE_INDOOR   = 1;
115:  public static final int EXHIBITION_MODE_INVALID  = 0;
116:  public static final int EXHIBITION_MODE_NON      = 3;
117:  public static final int EXHIBITION_MODE_OUTDOOR  = 2;
```
`BYDAutoFeatureIds.java:2531` → `SET_EXHIBITION_MODE = 836763656` (= `0x31E00008`).
Listener callback: `AbsBYDAutoSettingListener.java:191 onExhibitionModeChanged(int)`, `:195 onExhibitionStartEnginePromptChanged(int)`.

### 1.2 Nó điều khiển gì? → **nổ máy + nhắc chuyển số trong showroom**, KHÔNG nav
Các id đi kèm (`START_ENGINE_PERMIT`, `START_ENGINE_PROMPT`, `GEAR_SHIFT_PROMPT`) cho thấy exhibition-mode là chế độ trưng bày **xe**: cho phép nổ máy đứng yên + nhắc số. Grep toàn `jadx-DiCarServer/sources/com/byd/feature/` cho `EXHIBITION_MODE|0x31E00008` = **KHÔNG có consumer nào feed nav / guide-info / HUD** — chỉ có định nghĩa + mapper. ⇒ **Bật exhibition mode KHÔNG bật nav-demo.**

➡️ **Q1 trả lời:** id = `0x31E00008` (INDOOR=1/OUTDOOR=2/NON=3/INVALID=0), bật qua SettingDevice set. **Nhưng đây KHÔNG phải đường nav-HUD** — nó không liên quan tới mũi tên/cự ly/tên đường.

---

## Q2 — Showroom ON → SERVICE nào feed nav demo? register tên đường? kèm CHECK/CONFIG/FORMAT?

### 2.1 Không có service replay nav nào trong image
- Grep `retail|autoPlay|自动播放|循环|演示|导航演示|navDemo|replay` toàn `jadx-DiCarServer/sources` → **chỉ RxJava `ReplayProcessor/ReplaySubject/ObservableReplay…`** (nhiễu) + `SET_CAR_LOCK_REPLAY_VIDEO_SET = 0x4DE01020` (`Setting.java:2540` — quay video sentry, **không phải nav**). **0 service nav-demo.**
- `jadx-BydDevelopmentTools` (RollbenchMode/RepairMode/OBD/ScanCode/Mapping): grep `nav|hud|instrument|guide|38B|43F` trong `com/byd/**` → **chỉ khớp `R.java` (layout noise)**. ⇒ **DevTools KHÔNG feed nav/HUD.**
- Map app **SKT Tmap** (`com.skt.tmap`): grep `sendSimpleGuidanceInfo|sendNextPathName|sendAutoNaviStatus|BYDAutoInstrumentDevice` trong **toàn** `tmap_c1/out/sources` → **chỉ khớp file stub SDK** `BYDAutoInstrumentDevice.java`, **KHÔNG có caller nào trong code TMap đã decompile**. ⇒ trong image này TMap **không** đẩy nav qua BYD instrument SDK (nó dùng đường khác / hoặc không phải producer cụm ở trim này).

### 2.2 Register tên đường vẫn là `0x43FA1008` — và có một CHECK-STATE read-only
`Instrument.java`:
```
510:  INSTRUMENT_GET_ROAD_NAME_CHECK_STATE     = "0x420A1010"   // READ-only, VALID=1 / INVALID=2
      (…) TARGET_NEXT_PATHNAME_INFO_SET       = "0x43FA1008"   // tên đường (track4 Phụ lục A)
529:  INSTRUMENT_GUIDE_INFO_SIMPLE_SET         = "0x43F01010"   // mũi tên
      INSTRUMENT_FRONT_CROSSING_DISTANCE_SET  = "0x43F01018"   // cự ly (track4)
```
SDK stub xác nhận CHECK-STATE là **getter**: `BYDAutoInstrumentDevice.java:1499 getRoadNameCheckState()`, hằng `:385 INVALID=2 / :386 VALID=1`; listener `AbsBYDAutoInstrumentListener.java:615 onRoadNameCheckStateChanged(int)`; mapper `InstrumentMapper.java:380`. **Đây là CHECK đọc-only** — MCU/cụm báo *tên đường có được chấp nhận không*, app **không ép** nó valid được, chỉ **đọc để chẩn đoán**.

➡️ **Q2 trả lời:** **Không service nào** feed nav-demo khi showroom ON. Register tên đường không đổi (`0x43FA1008`). Có kèm khái niệm **CHECK-STATE** (`0x420A1010`, read-only) nhưng **không** có CONFIG/FORMAT nào được "showroom" ghi trước — vì showroom không phải đường nav.

---

## Q3 — Showroom có set enable (32B1102E/34C00026/38B00030) trước khi feed? = enable-path?

### 3.1 Họ HUD-nav-map: định nghĩa ĐẦY ĐỦ nhưng KHÔNG app nào GHI
`Instrument.java`:
```
534:  INSTRUMENT_HUD_MAP_FORMAT_34C_SET         = "0x34C00026"   // format map lên HUD (write)
536:  INSTRUMENT_HUD_NAVIGATION_MAP_CONFIG      = "0x38B00030"   // CONFIG/PROVISION gate
537:  INSTRUMENT_HUD_NAVIGATION_MAP_CONFIG_STATUS = "0x30100030" // status read
538:  INSTRUMENT_HUD_NAVIGATION_MAP_SET         = "0x32B1102E"   // trigger mirror nav-map → kính HUD (write)
183:  INSTRUMENT_ARHUD_NAVIGATION_MAP_RESOLUTION_STATUS = "0x34C0000B"
      INSTRUMENT_HUD_NAVIGATION_MAP_STATUS      = "0x38B0002E"   // status read (track4)
```
**Grep hex `0x32B1102E|0x34C00026|0x38B00030|0x30100030` TOÀN `…/sysimg/`** → **chỉ khớp `Instrument.java` (định nghĩa)**. Không ClusterDebug, không DevTools, không TMap, không DiCarServer-logic **GHI** chúng. `InstrumentMapper.java:729,750,758,768,769` chỉ là map id→tên.

⇒ **Không có "enable-path" nào được app/showroom dùng.** `0x38B00030` là **cổng coding MCU** (variant-coding instrument-ECU, set bằng UDS — track4 §Q1/§Q3), không phải cờ app lật. Showroom mode **không** đụng 4 register này.

### 3.2 Quan hệ 2 kênh (làm rõ nhầm lẫn)
- **Kênh A — guide-info-simple `0x43F/0x43E/0x43FA`**: bơm *phần tử* nav rời (mũi tên/cự ly/tên đường/ETA) → **data-item → widget CỤM**. App + SDK dùng kênh này. Tới **cụm-centre** (xe provisioned); **rc=0 nhưng TRỐNG trên kính HUD** ở trim này.
- **Kênh B — HUD-nav-map `0x32B1102E`+`0x34C00026`+`0x38B00030`**: **mirror BẢN ĐỒ nav từ cụm lên KÍNH HUD**. Bị **coding-gate `0x38B00030`**. Khi provisioned, `0x34C00026` (format) + `0x32B1102E` (set) mới hiệu lực.

➡️ **Q3 trả lời:** **Không.** Showroom không set enable nào; và 4 register enable-path đó **không app-reachable** (chỉ MCU coding). Đây **không** phải cửa app mở được.

---

## Q4 — Showroom làm GÌ KHÁC app? App thiếu bước enable/format/check nào?

### 4.1 SDK OEM chỉ phơi đúng bộ guide-info — không hơn
`BYDAutoInstrumentDevice.java` (stub SDK map app compile-against) — **toàn bộ** method nav:
```
sendAutoNaviStatus(int)              // trạng thái nav (NAVI_OPEN_SET_DEST=2, CLOSE=4…)
sendSimpleGuidanceInfo(int,int)      // mũi tên + cự ly  → 0x43F01010 (+0x43F01018)
sendSafeGuidanceInfo(int,int,int)    // 0x43F04010
sendCameraGuidanceInfo(int,int,int)  // 0x43F03010
sendNextPathName(String)             // TÊN ĐƯỜNG → 0x43FA1008
sendRestRouteInfo(int,int,long)      // ETA/quãng còn lại
sendAddressInfo(int,String)          // home/company
sendDestinationSetStatus(int)
getRoadNameCheckState()              // READ-only 0x420A1010 (VALID=1/INVALID=2)  ← check duy nhất
```
**KHÔNG có** method nào ghi `0x32B1102E` / `0x34C00026` / `0x38B00030`. ⇒ **Producer OEM cũng chỉ dùng đúng bộ guide-info y như app.** App **không thiếu** một lệnh HAL nào mà producer có.

### 4.2 Vậy khác biệt nằm ở đâu?
Khác biệt "tên đường hiện (cụm-centre / xe provisioned) vs trống (kính HUD trim Seal VN)" **KHÔNG** do app thiếu register — mà do:
1. **Coding MCU `0x38B00030` = 0** (chưa provision) → widget nav **vắng** trong scene kính HUD (kênh B tắt). Không app/showroom lật được.
2. (Chẩn đoán được) **`0x420A1010` ROAD_NAME_CHECK_STATE**: nếu MCU trả `INVALID=2`, tên đường bị bỏ dù app ghi `0x43FA1008`. App **chưa đọc** cờ này → nên **thêm bước ĐỌC để chẩn đoán** (không phải "enable", chỉ để biết vì sao trống).

➡️ **Q4 trả lời:** Ở tầng app/HAL, **showroom không làm gì khác app cả** (không có bề mặt showroom cho nav). App **không thiếu** enable/format nào app-reachable; chỉ **thiếu 1 bước ĐỌC chẩn đoán** (`0x420A1010`) và bị chặn bởi **coding `0x38B00030`** (ngoài tầm app).

---

## Q5 — Đề xuất chuỗi register+giá trị (để app/probe tái lập)

> **Sự thật:** không có "chuỗi showroom" bí mật. Chuỗi *producer chuẩn* = **đúng cái app đang làm**.
> Dưới đây là chuỗi reference (cite từ SDK + track4), + phần **chỉ mở được bằng coding** tách riêng.

### 5.1 Chuỗi guide-info (app-reachable — ĐÃ làm, tới cụm-centre; kính HUD cần thêm §5.2)
```
1) 0x43E0003A  SEND_NAVI_STATUS_SET      = 2         // nav active (latch)   [track4 Phụ lục A/B]
   (tuỳ chọn) 0x4C10E015 NAVI_SCREEN_STATUS_SET = 3  // chế độ nav trên cụm  [Setting/track4]
2) [PROBE đọc] 0x420A1010 GET_ROAD_NAME_CHECK_STATE  // kỳ vọng = 1 (VALID); =2 ⇒ MCU sẽ bỏ tên đường
3) 0x43F01010  GUIDE_INFO_SIMPLE_SET     = turnIcon  // mũi tên
4) 0x43F01018  FRONT_CROSSING_DISTANCE_SET = meters  // cự ly
5) 0x43FA1008  TARGET_NEXT_PATHNAME_INFO_SET = road  (UTF-16LE)   // TÊN ĐƯỜNG
6) 0x43F02010/18/1E/24/28  NAVI_TRIP…    ; 0x43F09010/18/20/28 ETA
```
→ Đây **chính xác** là `BydHal.writeNavFrame()` của app (track4 Phụ lục B). Kết quả on-car: **rc=0**, hiện **cụm-centre** (xe provisioned), **TRỐNG kính HUD** trim này.

### 5.2 Phần lên KÍNH HUD (KHÔNG app-reachable — chỉ coding MCU)
```
(coding) 0x38B00030 HUD_NAVIGATION_MAP_CONFIG = 1   // UDS variant-coding, MCU — KHÔNG app-writable
   → khi =1: 0x34C00026 HUD_MAP_FORMAT_34C_SET (format) + 0x32B1102E HUD_NAVIGATION_MAP_SET (mirror)
             mới hiệu lực; đọc trạng thái qua 0x30100030 / 0x38B0002E.
```
**Không app/showroom nào trong image ghi 3 register này** (§3.1). Đây là cổng coding — đúng kết luận track4.

### 5.3 Probe rẻ, non-root, on-car-testable (đề xuất, KHÔNG code phiên này)
1. **Đọc `0x420A1010`** trong loop nav: nếu trả `2 (INVALID)` ⇒ giải thích tên đường trống → thử đổi encoding/độ dài chuỗi `0x43FA1008` cho tới khi CHECK=1. *(Đây là dữ liệu mới, app chưa từng đọc.)*
2. **ClusterDebug op39 "简易导航"**: `AutoContainerManager.sendInfo(1000, 39, "")` (định nghĩa `SecondActivity.java infoListInit/_Di5`, dispatch giống `MainActivity`). Đây là **trigger scene nav-demo phía cụm Qt** (opcode-only, KHÔNG tham số → không nhét được tên đường của mình). README ghi op39 **no-op cho centre-view trim này**, nhưng **chưa xác nhận** với dải strip trên / kính HUD → đáng thử 1 lần để biết widget nav có tồn tại trong scene không. **Nếu op39 làm sáng nav trên HUD** ⇒ widget có, vấn đề chỉ là *đường feed*; **nếu không** ⇒ củng cố kết luận coding-gate.
3. **CAN-inject** (ClusterDebug `broadcastToCAN`): broadcast `com.byd.cluster.spi` extra `wholeFrame`/`normal` = hex bytes → `BYDAutoTestDevice.set({0xAA00020F}, frame)` (`BroadcastReceiverCAN.java`). Chỉ hữu ích khi có **arbitration-id + bit-layout** nav (MCU-side, chưa có) — trùng "cửa 2/3" track4.

---

## Phụ lục — bản đồ chứng cứ (file:line)

| Hạng mục | Bằng chứng |
|---|---|
| Exhibition/showroom mode id | `DiCarServer/…/setting/Setting.java:2639` `SET_EXHIBITION_MODE="0x31E00008"`; `:2640,:2641,:685` (engine/gear); `SettingMapper.java:121,251,542,855` |
| Exhibition mode values | `tmap_c1/…/setting/BYDAutoSettingDevice.java:114-117` (INDOOR=1/INVALID=0/NON=3/OUTDOOR=2); `BYDAutoFeatureIds.java:2531` (=836763656); `AbsBYDAutoSettingListener.java:191,195` |
| Không có nav-demo/replay service | grep `retail\|演示\|replay\|自动播放` `DiCarServer/sources` = chỉ RxJava + `Setting.java:2540 SET_CAR_LOCK_REPLAY_VIDEO_SET=0x4DE01020` (video, không nav) |
| DevTools không feed nav | grep `nav\|hud\|instrument\|43F\|38B` `jadx-BydDevelopmentTools/…/com/byd/**` = chỉ `R.java` layout |
| TMap không call nav SDK | grep `sendSimpleGuidanceInfo\|sendNextPathName\|BYDAutoInstrumentDevice` toàn `tmap_c1/out/sources` = chỉ file stub `BYDAutoInstrumentDevice.java` |
| Họ HUD-nav-map (định nghĩa) | `Instrument.java:534 (0x34C00026), :536 (0x38B00030), :537 (0x30100030), :538 (0x32B1102E), :183 (0x34C0000B)` |
| Không app nào GHI HUD-nav-map | grep hex `0x32B1102E\|0x34C00026\|0x38B00030\|0x30100030` toàn `sysimg/` = chỉ `Instrument.java` |
| Guide-info + road-name + check | `Instrument.java:529 (0x43F01010), :510 (0x420A1010 GET_ROAD_NAME_CHECK_STATE)`; `0x43FA1008` (track4 Phụ lục A) |
| SDK nav methods (không có enable) | `BYDAutoInstrumentDevice.java` methods `sendSimpleGuidanceInfo/sendNextPathName/sendRestRouteInfo/sendAutoNaviStatus/getRoadNameCheckState(:1499)`; hằng `:385 INVALID=2 / :386 VALID=1` |
| ClusterDebug op39 简易导航 | `SecondActivity.java infoListInit()/infoListInit_Di5()` `"39:简易导航"`; dispatch `MainActivity.java` `sendInfo(1000, opcode, "")` |
| ClusterDebug CAN-inject | `ClusterDebugService.java` (nghe `com.byd.cluster.spi`) → `BroadcastReceiverCAN.java` → `BYDAutoTestDevice.set({0xAA00020F=-1442840049}, frame)` |
| ClusterDebug opcodes (không có nav-content demo) | `MainActivity.infoListInit()` / `SecondActivity.infoListInit*()` — video-stream/OSD/ADAS-scene/HUD-menu/indicator; op212 "点亮仪表屏 không CAN" |

---

## Kết luận (đọc-hiểu; KHÔNG sửa/không commit phiên này)

1. **Không tồn tại "showroom/demo mode" phát full nav lên HUD** để làm bản mẫu. `EXHIBITION_MODE 0x31E00008` là chế độ *xe* (nổ máy/nhắc số), **không dính nav**.
2. **Không service replay-nav** trong DiCarServer/ClusterDebug/DevTools/TMap. Artefact "nav-demo" duy nhất là **ClusterDebug op39 "简易导航"** — opcode-only, phía cụm Qt, **không nhét được tên đường của mình**, README đã ghi no-op centre-view.
3. **4 register enable HUD-nav-map (`0x32B1102E/0x34C00026/0x38B00030/0x30100030`) không app nào ghi** trong image — `0x38B00030` là **coding MCU**. Showroom không đụng chúng ⇒ **không phải enable-path app dùng được**.
4. **App không thiếu lệnh HAL nào** so với producer OEM: SDK chỉ phơi đúng bộ guide-info app đang ghi. Chỗ hụt "kính HUD trống" = **coding `0x38B00030`** (ngoài tầm app) — **trùng khớp `re-hud-track4-hal-can.md`**.
5. **Việc đáng làm tiếp** (probe, chưa code): (a) **đọc `0x420A1010`** để biết tên đường có bị MCU từ chối không (dữ liệu mới); (b) thử **op39** xem widget nav có trong scene HUD không; (c) coding `0x38B00030=1` qua UDS (`BYDAutoOtaDevice`, gated seed/key) là đường thật duy nhất lên kính — như track4.

**Trạng thái RE:** đọc-hiểu hoàn tất; **không** sửa mã nguồn, **không** commit/push. Handoff này là output.
