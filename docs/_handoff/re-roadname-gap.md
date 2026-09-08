# RE — Tại sao TÊN ĐƯỜNG (0x43FA1008) không lên HUD BYD qua app (mũi tên + cự ly thì lên)

> **Loại:** RE doc-hiểu (KHÔNG sửa code/commit) · **Ngày:** 2026-08-19
> **Bối cảnh:** HUD "Taobao" = HUD BYD xịn (VN cắt ra, anh em gắn lại); cụm/HUD showroom OEM hiện được tên đường (vd `五一大道南`). Owner đẩy nav bằng app → **mũi tên + cự ly lên, TÊN ĐƯỜNG không lên**.
> **Nguồn RE:** DiCarServer `Instrument.java`/`InstrumentMapper.java`, SDK stub `BYDAutoInstrumentDevice.java` (tmap_c1 + openbyd-2.3), OpenBYD `CarControlImpl.java`/`HudController.java`, app `<repo>/app/src/main/java/com/byd/clusternav/modules/hal/BydHal.kt`, log xe anh em `<repo>/docs/diagnostics/hud-provisioning-compare-2026-08-19.md`.

---

## TL;DR (xếp hạng nguyên nhân, mỗi cái có test on-car)

Phát hiện then chốt: **`0x43FA1008` (tên đường) là một feature KIỂU BUFFER có REGISTER ĐỌC-NGƯỢC ĐI KÈM `0x420A1010 GET_ROAD_NAME_CHECK_STATE` (SDK: `getRoadNameCheckState()` → `VALID=1` / `INVALID=2`)**. Mũi tên (`0x43F01010`) và cự ly (`0x43F01018`/`0x43F01030`) là feature KIỂU INT, **KHÔNG có register kiểm-tra/handshake nào** → cụm vẽ vô điều kiện. **App ghi tên đường nhưng KHÔNG BAO GIỜ đọc `0x420A1010`** (grep toàn core/app = 0 lần đọc) → app mù hoàn toàn về việc cụm có coi chuỗi là VALID hay không.

Xếp hạng nghi phạm (làm test theo thứ tự — không phá hoại, làm được off-driving):

1. **[Chẩn đoán TRƯỚC] Đọc `getRoadNameCheckState()` (0x420A1010) ngay sau khi ghi pathname.** Kết quả chẻ đôi bài toán: `2=INVALID` ⇒ cụm TỪ CHỐI chuỗi (format/font/handshake) · `1=VALID` mà vẫn không hiện ⇒ gate render nằm chỗ khác (coding/widget).
2. **Thiếu tiền-điều-kiện/thứ tự:** OEM có thể phải set trạng thái đích (`sendDestinationSetStatus(2=SET_DONE)` / `0x43E00038`) hoặc `SEND_NAVI_STATUS=2` (`0x43E0003A`) **trước** khi pathname được nhận. App có ghi status=2 nhưng KHÔNG ghi destination-set-status.
3. **Khung buffer / ký tự:** OEM AmapService ghi UTF-16LE **cuộn từng frame, có dấu cách dẫn đầu** (`[32,0,75,0,…]` = `' '`,`'K'`…). App ghi chuỗi thô, không khung cuộn, không NUL, có thể chứa **dấu tiếng Việt** mà font cụm/HUD không phủ (showroom hiện được `五一大道南` = font CJK, chưa chứng minh phủ dấu VN).
4. **[App-side, cần verify]** Đường DOMESTIC pathname ở `BydHal.kt` **KHÔNG có fallback raw-id** (khác đường OVERSEA). Nếu ROM phản chiếu `INSTRUMENT_TARGET_NEXT_PATHNAME_INFO_SET` ra 0/absent thì ghi trượt register. (On-car cũ báo rc=0 → nhiều khả năng đã resolve đúng, nhưng phải log id để chắc.)
5. **Gate coding widget tên-đường của cụm zin** (cùng họ nghi vấn `0x38B00030` cho HUD-zin). Ít khả năng là nguyên nhân RIÊNG cho tên đường vì mũi tên+cự ly (cùng nav layer) vẫn lên — nhưng sub-widget tên đường có thể bị gate riêng.

> ⚠ Ranh giới trung thực: register/paring/encoding là **bằng chứng cứng từ mã đã dịch ngược**. Việc *render có bị gate bởi check-state hay không* nằm trong **firmware MCU cụm** (không có trong nguồn Android này) → phải xác nhận bằng test #1 trên xe. KHÔNG kết luận "không thể".

---

## 1. Bằng chứng cốt lõi

### 1.1 `0x420A1010` là REGISTER KIỂM-TRA tên đường (đọc-ngược), ghép cặp với `0x43FA1008`

Cấu trúc "ghi info dạng buffer ↔ đọc-ngược kết quả" thấy rõ ở 4 cặp (DiCarServer `Instrument.java`):

| Ghi (SET, app→cụm, buffer) | Đọc-ngược (RESULT/CHECK, cụm→app) | SDK method |
|---|---|---|
| `0x43FA1008` TARGET_NEXT_PATHNAME_INFO_SET (**tên đường**) | **`0x420A1010` GET_ROAD_NAME_CHECK_STATE** | `sendNextPathName()` ↔ **`getRoadNameCheckState()`** |
| `0x43FB1008` MUSIC_INFO_SET | `0x420B1010` MUSIC_INFO_RESULT | `sendMusicInfo()` ↔ `getMusicInfoResult()` |
| `0x43FC1008` CALL_INFO_SET | `0x420C1010` CALL_INFO_RESULT | `sendCallInfo()` ↔ `getCallInfoResult()` |
| `0x43FD1008` RADIO_INFO_SET | `0x420D1010` RADIO_INFO_RESULT | `sendRadioInfo()` ↔ `getRadioInfoResult()` |

Ngữ nghĩa giá trị (SDK stub, cả tmap_c1 lẫn openbyd-2.3):

```
INSTRUMENT_ROAD_NAME_CHECK_VALID   = 1
INSTRUMENT_ROAD_NAME_CHECK_INVALID = 2
int getRoadNameCheckState()   // đọc 0x420A1010
```

- `getRoadNameCheckState()` → `sysimg/tmap_c1/.../BYDAutoInstrumentDevice.java:1499`; `INSTRUMENT_ROAD_NAME_CHECK_INVALID/VALID` → `:385–386`.
- `INSTRUMENT_GET_ROAD_NAME_CHECK_STATE = "0x420A1010"` → DiCarServer `Instrument.java`.
- **Trên ROM owner (tmap_c1) id này = `1107955728` (0x420A1010) — KHÁC 0 ⇒ register ĐƯỢC provision/biết tới** (`sysimg/tmap_c1/.../BYDAutoFeatureIds.java`: `INSTRUMENT_GET_ROAD_NAME_CHECK_STATE = 1107955728`). Cùng bảng: `INSTRUMENT_GET_NAVI_DESTINATION = 1086328891`, `*_INFO_RESULT` đều khác 0 (họ read-back đều tồn tại).

⇒ `0x420A1010` **không** phải feature vô danh — nó là **cổng kiểm-tra tên đường** cụm dùng để báo chuỗi vừa nhận có hợp lệ để render hay không.

### 1.2 Mũi tên + cự ly là INT, KHÔNG có cổng kiểm-tra

`CarControlImpl.java` (OpenBYD) hardcode chính các id ClusterNav dùng:

```
INSTRUMENT_GUIDE_INFO_SIMPLE_SET             = 1139806224  (0x43F01010)  // mũi tên (int)
INSTRUMENT_FRONT_CROSSING_DISTANCE_SET       = 1139806232  (0x43F01018)  // cự ly (int)
INSTRUMENT_GUIDE_INFO_AND_ROAD_AHEAD_DISTANCE_SET = 1139806256 (0x43F01030) // dualIcon (int)
INSTRUMENT_TARGET_NEXT_PATHNAME_INFO_SET     = 1140461576  (0x43FA1008)  // tên đường (BYTES)
INSTRUMENT_SEND_NAVI_STATUS_SET              = 1138753594  (0x43E0003A)  // status (int)
```

`sendSimpleGuidanceInfo(icon,dist)` chỉ `set()` 3 feature INT rồi trả `RESULT_CODE:0`; `sendNextPathName(str)` ghi **`bytes`** vào `0x43FA1008`. **Không hàm INT nào đọc-ngược check-state; chỉ pathname có `getRoadNameCheckState()`.** Đây chính là **sự bất đối xứng** owner thấy.

### 1.3 Encoding KHỚP; nhưng KHUNG có thể khác

- `xh.b = Charset.forName("UTF-16LE")` (openbyd `defpackage/xh.java`), dùng trong `sendNextPathName`: `str.getBytes(xh.b)`.
- App: `road.toByteArray(Charsets.UTF_16LE)` (`BydHal.kt:~290`). ⇒ **encoding UTF-16LE khớp 1-1** với AmapService.
- Log xe anh em (`hud-provisioning-compare-2026-08-19.md §2`): `0x43FA1008 = text UTF-16LE (cuộn từng frame)`; mẫu byte `[32,0,75,0,…]` = `0x0020`(space) + `0x004B`('K') → **có dấu cách dẫn đầu + là 1 khung cuộn (marquee)**, không phải chuỗi tĩnh thô.
- Giới hạn độ dài info buffer: `INFOR_LENGTH_MAX = 255` (SDK) — HAL nhận tới 255 byte; còn **ô hiển thị ~16 byte (~8 ký tự BMP)** là giới hạn RENDER (`<repo>/core/.../NavFormat.kt:9`, OQ2 trong `<repo>/docs/specs/freeze-proof-cluster-switch.html`).

### 1.4 App KHÔNG đọc check-state, KHÔNG có fallback raw-id cho pathname domestic

- Grep toàn `core/`+`app/` cho `getRoadNameCheckState|ROAD_NAME_CHECK|420A1010|checkRoadName` → **0 lần đọc**. App là "ghi rồi quên".
- `BydHal.kt`: đường DOMESTIC `featureId("INSTRUMENT_TARGET_NEXT_PATHNAME_INFO_SET")?.let { … }` **KHÔNG có `?: 0x43FA1008` fallback**, trong khi đường OVERSEA có: `(featureId("…OVERASEA_SET") ?: PATHNAME_OVERSEA_ID=0x1F7A1008)` (`BydHal.kt:196, 301`). `featureId()` chỉ reflect **top-level** `BYDAutoFeatureIds` (không đụng inner class `Instrument`).
- Ghi chú boundary: trong bảng tmap_c1, mọi `_SET` (write) = `0` còn read/RESULT khác 0. On-car cũ báo mũi tên+cự ly+pathname đều rc=0 ⇒ ROM thật resolve đúng id (nếu không mũi tên cũng hỏng). Nên **id-resolution nhiều khả năng KHÔNG phải bug** — nhưng vẫn nên log id thật để loại trừ.

---

## 2. Trả lời 5 câu hỏi

**(1) 0x420A1010 ROAD_NAME_CHECK_STATE là gì? Có phải cổng phải pass trước khi render?**
Là **register ĐỌC-NGƯỢC (status) ghép cặp với pathname `0x43FA1008`**, SDK expose `getRoadNameCheckState()` trả `VALID=1`/`INVALID=2` (tmap_c1 `BYDAutoInstrumentDevice.java:385–386,1499`). **Ai ghi:** firmware/service cụm ghi giá trị check sau khi nhận pathname. **Ai đọc:** consumer nav (OEM AmapService) — để biết chuỗi có được chấp nhận render không. **Có phải hard-gate render?** Bằng chứng Android-side chỉ chứng minh nó là *validation handshake được provision*; *việc render có bị chặn bởi INVALID hay không* nằm trong firmware MCU cụm (ngoài tầm nguồn này) → **phải đọc trên xe để xác nhận** (test #1). Nó là handshake DUY NHẤT app đang bỏ qua và đúng loại "chỉ tác động tên đường, không tác động mũi tên/cự ly".

**(2) Tên đường có cần ghi KÈM/ĐÚNG THỨ TỰ với GUIDE_INFO_SIMPLE/CHECK_STATE?**
Không có ràng buộc thứ tự cứng nào trong mã Android đã dịch (OpenBYD `HudController.updateNavigation` ghi guidance rồi `sendNextPathName` **không** đọc check-state; không gate). NHƯNG luồng OEM đầy đủ còn có bước **trạng thái đích**: `INSTRUMENT_SEND_DESTINATION_STATUS_SET=0x43E00038` + `sendDestinationSetStatus(DESTINATION_STATE_SET_DONE=2)` và `INSTRUMENT_GET_NAVI_DESTINATION=0x40C0103B`. App **không** ghi destination-set-status. Giả thuyết cần test: cụm chỉ "mở khoá" ô tên đường sau khi `SEND_NAVI_STATUS=2` **và** `DESTINATION_SET=2`. Về CHECK_STATE: đúng bản chất là "phải VALID" nhưng đó là kết quả cụm TỰ set để phản hồi, không phải cờ app set trước.

**(3) Định dạng buffer 0x43FA1008 chính xác? App ghi có khớp?**
UTF-16LE (khớp `xh.b` và app). HAL nhận tối đa `INFOR_LENGTH_MAX=255` byte; ô hiển thị ~16 byte. **Điểm KHÁC:** log OEM là **khung cuộn có dấu cách dẫn đầu** `[32,0,75,0,…]`, phát **lặp từng frame**; app ghi **chuỗi thô một lần**, không dấu cách dẫn, không NUL terminator, và có thể chứa **dấu tiếng Việt**. Encoding khớp, nhưng **khung/nội dung có thể không khớp** (marquee framing, terminator, hoặc glyph coverage). Chưa đo được buffer/terminator thật của HUD trên xe (OQ2 còn mở).

**(4) Mũi tên + cự ly đi qua register nào, vì sao chúng lên mà 0x43FA1008 không?**
Mũi tên `0x43F01010` (GUIDE_INFO_SIMPLE), cự ly `0x43F01018` (FRONT_CROSSING_DISTANCE) và `0x43F01030` (dualIcon) — **đều là INT, không có register kiểm-tra/handshake, không phụ thuộc font/encoding**. Cụm vẽ mọi int trong biên. Tên đường `0x43FA1008` là **BYTES + có cổng kiểm-tra `0x420A1010` + phụ thuộc encoding/khung/độ-dài/glyph**. Vậy nên cùng "rc=0" nhưng chỉ tên đường có thể bị chặn ở tầng validate/format/font. (Bổ sung: HUD anh em là **Taobao aftermarket tự vẽ off-bus** nên hiện được; cụm/HUD **zin** owner đi qua firmware render → chịu cổng kiểm-tra/coding.)

**(5) Đề xuất cụ thể** — xem §3.

---

## 3. Đề xuất cụ thể (làm theo thứ tự; #1 là chẩn đoán quyết định)

### Test #1 — ĐỌC check-state (chẻ đôi bài toán) — ưu tiên tuyệt đối
Sau khi `writeNavFrame` ghi pathname, đọc `0x420A1010`.
- **navopen (thử tay trên xe, không đổi code):**
  ```
  # ghi status + pathname trước (hoặc để app đang chạy ghi), rồi đọc check-state:
  navopen getraw instr 420A1010     # kỳ vọng 1=VALID hoặc 2=INVALID
  ```
  (đường getraw instrument đã dùng: `<repo>/docs/archive/diagnostics/oncar-sdk-findings-2026-08-13.md` — `NAV getraw instr 43FA1008`.)
- **hoặc app (đọc-only, đã có sẵn hạ tầng):** `BydHal.callGetter(instr, "getRoadNameCheckState")` hoặc `BydHal.tryGet(instr, 0x420A1010)` rồi `readValue(...)`.
- **Diễn giải:** `2/INVALID` ⇒ cụm từ chối chuỗi → sang #3 (khung/font) hoặc #2 (handshake). `1/VALID` mà vẫn trống ⇒ gate render/coding (#5), pathname đã "được nhận" nhưng widget tắt.

### Test #2 — Thêm bước trạng thái đích + đúng thứ tự
Chuỗi thử (navopen setraw hoặc bản build thử): `SEND_NAVI_STATUS(0x43E0003A)=2` → `SEND_DESTINATION_STATUS(0x43E00038)=2` → guidance/dist → **pathname 0x43FA1008** → đọc lại `0x420A1010`. Nếu `getNaviDestinationCommand`/destination-set là tiền-điều-kiện, check-state sẽ chuyển VALID và tên đường hiện.

### Test #3 — Khung buffer / ký tự
Thử lần lượt (mỗi lần đọc lại check-state):
- (a) **Prefix một dấu cách** giống OEM: ghi `" " + road` (khớp `[32,0,…]`).
- (b) **Thêm NUL terminator** UTF-16LE (`road + "\u0000"`).
- (c) **Chuỗi thuần ASCII** ("Test") vs **chuỗi có dấu VN** ("Trần Trọng Kim") vs **chuỗi CJK** ("五一大道南"). Nếu ASCII/CJK hiện mà dấu VN không → **font coverage / check fail vì glyph** → dùng `NavFormat.fitRoadName`/bỏ dấu cho cụm.
- (d) Giữ độ dài ≤ ngân sách ô (~7–8 ký tự) để loại khả năng tràn (OQ2).

### Test #4 — (App) log + fallback raw-id cho pathname domestic
Trong `writeNavFrame`, **log id thật** mà `featureId("INSTRUMENT_TARGET_NEXT_PATHNAME_INFO_SET")` trả về. Nếu `0`/absent → thêm fallback raw-id **giống đường oversea**:
```kotlin
// gợi ý (KHÔNG commit ở phiên RE này):
(featureId("INSTRUMENT_TARGET_NEXT_PATHNAME_INFO_SET") ?: 0x43FA1008).let { id ->
    rc.append(" PATHNAME=").append(cachedSetBytes(instr, id, road.toByteArray(Charsets.UTF_16LE)))
}
```
(0x43FA1008 = 1140461576, xác nhận từ `CarControlImpl`.)

### Test #5 — Nếu #1 = VALID mà vẫn trống → coding/widget
Xác định coding bật ô nav-text của cụm zin (cùng loại điều tra `0x38B00030`/`factory-hud-nav-RE-avenues-2026-08-19.md`) — việc **coding XE** (dealer/OBD-UDS), không phải app. Điều kiện mở khoá để owner quyết.

---

## 4. Nguồn (file:line)

- DiCarServer `sysimg/jadx-DiCarServer/.../instrument/Instrument.java`:
  `INSTRUMENT_GET_ROAD_NAME_CHECK_STATE="0x420A1010"`, `INSTRUMENT_TARGET_NEXT_PATHNAME_INFO_SET="0x43FA1008"`, `…_OVERASEA_SET="0x1F7A1008"`, `INSTRUMENT_GUIDE_INFO_SIMPLE_SET="0x43F01010"`, `INSTRUMENT_GUIDE_INFO_AND_ROAD_AHEAD_DISTANCE_SET="0x43F01030"`, `INSTRUMENT_FRONT_CROSSING_DISTANCE_SET="0x43F01018"`, `INSTRUMENT_SEND_NAVI_STATUS_SET="0x43E0003A"`, `INSTRUMENT_SEND_DESTINATION_STATUS_SET="0x43E00038"`, `INSTRUMENT_GET_NAVI_DESTINATION="0x40C0103B"`; `InstrumentMapper.java` map "0x420A1010"→INSTRUMENT_GET_ROAD_NAME_CHECK_STATE và `transformFeatureId` reflect `BYDAutoFeatureIds.Instrument` rồi `BYDAutoFeatureIds`.
- SDK `sysimg/tmap_c1/out/sources/android/hardware/bydauto/instrument/BYDAutoInstrumentDevice.java:385` (`INSTRUMENT_ROAD_NAME_CHECK_INVALID=2`), `:386` (`…VALID=1`), `:1499` (`getRoadNameCheckState()`), `:1698` (`sendNextPathName(String)`); `INFOR_LENGTH_MAX=255`, `DESTINATION_STATE_SET_DONE=2`.
- SDK ids `sysimg/tmap_c1/out/sources/android/hardware/bydauto/BYDAutoFeatureIds.java`: `INSTRUMENT_GET_ROAD_NAME_CHECK_STATE=1107955728` (0x420A1010, ≠0), `INSTRUMENT_GET_NAVI_DESTINATION=1086328891`, `*_INFO_RESULT`≠0; các `_SET`=0 (bảng chỉ chứa read ids).
- OpenBYD `openbyd-2.3/.../proxy/CarControlImpl.java`: hằng id (1139806224/…/1140461576/1138753594), `sendNextPathName` = `set(0x43FA1008, bytes=str.getBytes(xh.b))`, `sendSimpleGuidanceInfo` = 3×INT; `openbyd-2.3/.../services/HudController.java` `updateNavigation()` gọi `sendNextPathName(str)` không gate check-state; `openbyd-2.3/.../defpackage/xh.java` `b=UTF-16LE`.
- App `<repo>/app/src/main/java/com/byd/clusternav/modules/hal/BydHal.kt`: `:284` status=2, `:287` GUIDE_INFO_SIMPLE, `:289` FRONT_CROSSING_DISTANCE, `:~290` PATHNAME (UTF-16LE, **không fallback raw-id**), `:196/:301` đường OVERSEA có fallback `0x1F7A1008`. Không nơi nào đọc `getRoadNameCheckState`/`0x420A1010`.
- Log xe anh em `<repo>/docs/diagnostics/hud-provisioning-compare-2026-08-19.md §2`: `0x43FA1008 = UTF-16LE cuộn từng frame`, mẫu `[32,0,75,0,…]`; app ghi đúng feature (`BydHal.kt:284/290/312/313`). HUD anh em = Taobao aftermarket (render độc lập off-bus).
- `<repo>/core/src/main/kotlin/com/byd/clusternav/navigation/NavFormat.kt:9,16` (buffer cụm ~16 byte UTF-16LE; ngân sách HUD); OQ2 `<repo>/docs/specs/freeze-proof-cluster-switch.html:282` (buffer HUD chưa đo).

## 5. Còn mở (trace-đến-tận-cùng)
- Chưa có bằng chứng firmware MCU cụm rằng INVALID **chặn** render (nằm ngoài nguồn Android) → **test #1 on-car** là mấu chốt, chưa chạy.
- Chưa decompile OEM `AmapService` để xem nó có ĐỌC `getRoadNameCheckState` và/hoặc set `DestinationStatus` trước pathname không (chỉ có log feature-id, chưa có call-site). Nếu lấy được AmapService → xác nhận thứ tự chuẩn.
- Chưa đo buffer thật + terminator + phủ-glyph (VN dấu) của HUD/cụm zin trên xe owner.
