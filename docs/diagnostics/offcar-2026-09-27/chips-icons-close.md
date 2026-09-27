# L4 · Chip + icon — đóng bảng hoãn UX8 và hai câu hỏi owner (2.76 R8 · R9)

> **Trạng thái**: Current · **Cập nhật**: 2026-09-27 · **Mục đích**: đóng **toàn bộ** nợ off-car của chip thanh trên
> còn lại sau 2.75 — mã thô `power_level`/`headlight_feedback` [P3 UX8], cửa ×4 + cửa sổ trời chưa có icon trạng
> thái, câu hỏi gộp `ac_mode_auto`/`ac_wind_auto` (owner 26/09), ghế lái/phụ chỉ khác nhau bởi lật gương. Bảng
> `DEFERRED` của `TopStripStateIconTest` **rỗng**. Spec: `docs/specs/kachi-276-closing.html` R8/R9/OQ3.

## 1. Bối cảnh · số đo

| # | Nợ (2.75) | Bằng chứng | Kết 2.76 |
|---|---|---|---|
| 1 | chip in `"Nguồn xe · 2"` / `"Chế độ đèn pha · 2"` | [ĐO đọc mã 2.75] `TelemetryReadout.format` chỉ `toString()`; owner nhìn thanh trên xe 27/09 | ✅ chữ từ bảng OEM, mã lạ ⇒ *"mã N"* |
| 2 | cửa ×4 + cửa sổ trời in `"· Mở"/"· Đóng"` | 5 mục `DEFERRED` 2.75 (bộ sinh hình xe khác bộ glyph) | ✅ cặp hình mở/đóng sinh từ `gen-car.py`, chữ rời chip |
| 3 | `ac_mode_auto`/`ac_wind_auto` in `"· AUTO"/"· Chỉnh tay"`; owner hỏi gộp (26/09) | 2 mục `DEFERRED` 2.75; OQ3 spec 2.76 | ✅ chip icon 2 trạng thái; `ac_wind_auto` ẩn khỏi bộ chọn chip; **không gộp** (§3.3) |
| 4 | ghế lái/phụ chỉ khác nhau bởi lật gương | owner hỏi 27/09 | ✅ ghế lái thêm **chấm vô-lăng** trước ghế (3 glyph); `ic_seat_left` vào đường ống |

[ĐO] `gen-icons.py --check` **119/119** khớp byte · `gen-car.py --check` **41 tệp** khớp byte (39 icon + manifest +
paint). Icon không đụng tới: byte-identical (chỉ 3 tệp ghế lái đổi + 6 tệp mới).

## 2. Cơ chế

### 2.1 Họ thứ TƯ — nhiều chế độ (≥ 3) ⇒ CHỮ từ bảng OEM (`TelemetryEnums`, :core, thuần)
- `TelemetryEnums.POWER_LEVEL` — [ĐO source] `jadx-tmap/.../bodywork/BYDAutoBodyworkDevice.java:197-202`
  `BODYWORK_POWER_LEVEL_OFF=0 · ACC=1 · ON=2 · OK=3 · FAKE_OK=4 · INVALID=255` (getter `:484 getPowerLevel`).
  Chữ: Tắt · ACC · Bật · Sẵn sàng · Sẵn sàng (giả) · Không xác định (EN: Off · ACC · On · Ready · Ready (fake) ·
  Invalid). [SUY] nghĩa `OK`/`FAKE_OK` suy từ tên hằng (READY / READY chưa đủ điều kiện) — ghi rõ trong KDoc.
- `TelemetryEnums.HEADLIGHT_MODE` — feature `INSTRUMENT_HEADLIGHT_CONTROL_FEEDBACK` 1011875880 (`0x3C500028`,
  `oncar-trace-2026-09-16b/featmap-20260916.json`; `carsettings-apk/.../feature/instrument/Instrument.java:517`).
  Stub `*Device.java` **không** có hằng `HEADLIGHT_*`/`LIGHT_MODE_*` cho tín hiệu này (đã grep cả cây
  `android/hardware/bydauto/`) ⇒ nguồn là **app CarSettings OEM** `carsettings-apk/jadx-carsettings/.../vehiclesettings/outsidelight/view/LightControl.java`:
  `:81 getRadioTextIds() = [close, auto, small_light, low_beam]` · `:106-111 getStateFromHal() = feedback − 1`
  · `:117 setState2Hal(i) → SET = i + 1` ⇒ **1 = Tắt · 2 = Auto · 3 = Đèn hông · 4 = Cốt**; `0` = chưa phản hồi
  ⇒ *"mã 0"* (không bịa "Tắt"). Khớp bảng RE cũ `kachi-capability-catalog-2026-09-10.md:130`.
- `TelemetryReadout.format`: `"power_level" -> s.body.powerLevel?.let { TelemetryEnums.text(…) }` (giữ dạng
  `"id" -> s.<cụm>.<field>` — hợp đồng đọc-ngược của `CarDataDemandRendererContractTest`).
- Mã lạ ở mọi bảng ⇒ `TelemetryEnums.unknown(code)` = *"mã N"* / *"code N"* — con số vẫn hiện (chẩn đoán được),
  tiền tố nói rõ chưa có nghĩa. Dùng cả cho sentinel của hai chỉ báo AUTO (§2.3).

### 2.2 Cửa ×4 + cửa sổ trời — họ 3 (hai chế độ), cặp hình cùng bộ sinh `gen-car.py`
- `TelemetryReadout.stateTable`: `door_*` → `s.body.door*Open` (1 = MỞ; [ĐO source] `BYDAutoBodyworkDevice.java:**203-205**`
  `BODYWORK_STATE_CLOSED=0 · OPEN=1 · **UNDEFINED=255**`), `sunroof_state` → `s.body.sunroofOpen`.
- **[P1 · soát Opus 27/09]** Hằng thứ ba ấy từng bị **bỏ khỏi trích dẫn** (`:203-204`) và đó là một lỗ thật: `readBool`
  đi qua `coerceBool = coerceInt(s) > 0` ⇒ `255` thành `true` = **ĐANG MỞ**, và cờ Boolean của `CarStatus.Body` xoá mất
  sentinel trước khi `:core` thấy ⇒ hình cửa MỞ sáng + nhóm *Cửa* ĐỎ cho một cánh cửa đang đóng. Đã đóng bằng **dữ
  liệu**: `HalReadTables.INVALID_VALUES` lọc `255` cho 4 cửa + `sunroof_state` (bài `BindingRemediationTest`).
  Mức bằng chứng: hằng là [ĐO source]; việc getter này TRẢ 255 trên xe owner là **[CHƯA BIẾT]** (4 lượt quét đều = 0).
- `CapabilityIcons.STATE`: MỞ = chính hình khái niệm cũ `ic-car-top-door-*` (vạt cửa xoè — vốn đã vẽ cửa đang mở,
  R1 một hình/khái niệm giữ nguyên); ĐÓNG = **mới** `ic-car-top-door-*-shut` (vạch cửa nằm sát thân, đúng góc);
  chưa đọc ⇒ hình ĐÓNG + trung tính (mở là trạng thái ĐÁNG BÁO — không vẽ sẵn). Cửa sổ trời: ĐÓNG = `ic-car-top-sunroof`
  (kín ô, sẵn có), MỞ = **mới** `ic-car-top-sunroof-open` (ô nóc nét + khe hở tô, **không** mũi tên — mũi tên là
  "vị trí" của `sunroof_pos` đã gỡ 09-25).
- Nguồn: `design/car/top.svg` thêm 4 phần tử **glyph** `door_*_shut` (`data-stroke="1"`, chỉ icon 24dp; khung
  Canvas không đổi — `frame_pieces()` bỏ glyph, `top: 27/28` path khung như cũ); `gen-car.py` ICONS +5 dòng;
  `design/car/manifest.json` sinh lại (46 mảnh).

### 2.3 Tự động / chỉnh tay — họ 3, cặp `ic-mode-auto` / `ic-mode`
- `stateTable`: `ac_mode_auto` ← `acModeRaw` (`AC_CTRLMODE_AUTO=0 · MANUAL=1`, `ac/BYDAutoAcDevice.java:20-21`);
  `ac_wind_auto` ← `acWindAutoRaw` (`AC_WINDLEVEL_MANUAL_SIGN_OFF=0 · ON=1`, `:107-108`). Mã 0 → trạng thái 0 (AUTO),
  1 → 1 (tay), **khác ⇒ `null`** (sentinel 65535 / −2147482648 không được làm tròn thành "tay"); `format` cùng lúc
  trả *"mã N"* ⇒ chữ và hình không bao giờ nói hai điều (`TelemetryReadout276Test`).
- Glyph mới `design/glyph/mode_auto.svg`: cùng núm xoay của `mode.svg` (cung nấc + vạch mờ + núm tô), KIM thay bằng
  chữ **A**. Tay / chưa đọc = `ic-mode` (núm có kim) — tay không phải trạng thái đáng báo, phân biệt bằng sắc thái.
- Chip `ac_mode_auto`: chỉ nhãn ngắn + hình; **không** chữ đuôi AUTO/Chỉnh tay. Chip **Gió** (`ac_wind`) vẫn
  `"auto n"` (UX8, owner giữ) — không đụng.

### 2.4 Ghế lái — dấu thứ hai
- `design/glyph/seat_heat_left.svg` · `seat_vent_left.svg`: +1 path chấm vô-lăng (r 1,25) trước ghế ở cao độ đệm.
- `design/glyph/seat_left.svg` (**mới**): bản lật của `seat.svg` + chấm ⇒ `ic_seat_left.xml` nay **sinh** qua
  `gen-icons.py` (trước là tệp vá tay `<group scaleX=-1>`, rời danh sách `legacy` của `IconStyleContractTest`).
- `IconSetInventoryTest`: ghế lái = ghế phụ **+1 path** (3 cặp), `ic_seat_left` mang header SINH, không `<group>`.

### 2.5 Ẩn `ac_wind_auto` khỏi bộ chọn chip — `TopStripConfig.CHIP_HIDDEN`
- Cơ chế y hệt `CapabilityCatalog.HIDDEN_FROM_PICKER` nhưng **chỉ** ở `choices()` của thanh trên: `isChippable`
  không lọc ⇒ `decode` GIỮ mã ai đã đặt; khối *"đang bật"* của `picks` vẫn bày để gỡ; không vào `DEFAULT_IDS`.
  Datum giữ nguyên trong registry (ô lớn · nhóm · giọng nói · test-bridge · `readKey` của nút `ac_auto`).

## 3. Quyết định

1. **Họ 4 ra chữ, không ra hình** [SUY có lý do]: 4–6 glyph cho một khái niệm là hoa văn nền (cùng lẽ trần
   `MAX_PER_DOMAIN`); chữ phải dịch + mã lạ *"mã N"*. Máy soát `TopStripStateIconTest` coi `TelemetryEnums.size ≥ 3`
   là họ hợp lệ; bảng **2 mục** bị từ chối ở đó (phải khai hình).
2. **Hình MỞ của cửa dùng lại hình khái niệm** (R1) — chỉ vẽ thêm hình ĐÓNG. Không thêm biến thể "chưa đọc" (dùng
   ĐÓNG + sắc thái, đúng tiền lệ `pm25_online`).
3. **OQ3 — KHÔNG gộp `ac_mode_auto` và `ac_wind_auto`**: hai getter (`getAcControlMode` `:214` vs
   `getAcWindLevelManualSign` `:302`), hai feature id trên xe (`AC_CTRL_MODE` 1077936146 · `AC_WINDLEVEL_MANUAL_SIGN`
   1077936140, featmap 09-16), hai hằng khác họ. [ĐO xe 09-16] `getAcControlMode=0` khi AUTO; [ĐO xe 09-27 G4]
   `getAcWindLevelManualSign=0` khi AUTO — cả hai đo **cùng chiều** nhưng **chưa từng đo được chúng LỆCH nhau**
   ⇒ [CHƯA BIẾT] chúng có bao giờ khác nhau không; gộp datum là khẳng định điều chưa đo. Chốt: tách datum, **ẩn**
   `ac_wind_auto` khỏi bộ chọn chip (bề mặt thứ hai cho câu chip Gió đã trả lời), dùng chung cặp hình vì cùng câu
   hỏi. Mở khoá gộp: một phép đo trên xe cho hai getter lệch nhau (hoặc khớp qua ≥ 20 lượt đổi).
4. **`mode_auto` cùng họ màu DRIVETRAIN với `mode`** để cặp đọc là một (chỉ ảnh hưởng mặt lớn không tint).
5. **Chấm vô-lăng** là dấu tối thiểu, không vẽ vô-lăng thật (ở 20dp chip mọi vành đều thành chấm).

## 4. Test ([ĐO] từ XML `build/test-results`)

- `:core` targeted (`*TopStrip*` `*TelemetryReadout*` `*Icon*` `*CapabilityGroups*` `*CarDataDemand*` `*GroupBoard*`
  `*LangCoverage*` `*ClimateAuto*`): **242 · 0 fail**. Trong đó `TopStripStateIconTest` 12 · `TopStripStateIcon276Test`
  7 (mới) · `TelemetryReadoutTest` 22 · `TelemetryReadout276Test` 2 (mới) · `TopStripTest` 35 · `CapabilityIcons*` 19.
- `:app` targeted (`*Icon*` `*I18n*` `*TopStrip*` `*CarDataDemandRenderer*` `*CarImage*`): **99 · 1 fail** — fail
  duy nhất `LauncherI18nContractTest` do `CameraOverlayView.kt` (làn camera, chuỗi Việt viết cứng), **không thuộc
  L4**. `IconSetInventoryTest` 6/0 (+2 mới) · `IconStyleContractTest` 13/0 (gồm sinh-lại-so-byte) ·
  `IconGeometryContractTest` 4/0 · `TopStripWiringContractTest` 16/0.
- Bài canh mới khoá gì: máy soát nhận diện họ 4 (`TelemetryEnums.text(` trong `format`) + đòi `power_level`/
  `headlight_feedback` lộ ra; DEFERRED rỗng có bài riêng; mọi hằng OEM ánh xạ, mã lạ *"mã N"*, VI+EN; sentinel AUTO ⇒
  chữ *"mã N"* + trạng thái `null`; cửa/cửa sổ trời cả 3 ca (mở/đóng/chưa đọc) VI+EN; `CHIP_HIDDEN` 6 bất biến; hai
  trạng thái = hai tệp khác nội dung; ghế lái +1 path.
- Tách theo VAI để ≤ 500 dòng: `TopStripStateIcon276Test.kt` · `TelemetryReadout276Test.kt` ·
  `TelemetryReadoutFixtures.kt` (`wiredStatus`/`fakeId` top-level, pure move).

## 5. 🚗 Kiểm trên xe (mỗi dòng một phép đo, runbook 2.76)

- 🚗 Đặt chip **Nguồn xe** + **Chế độ đèn pha** lên thanh trên: xe ON ⇒ `"Nguồn xe · Bật"` hoặc `"· Sẵn sàng"` (ghi
  lại mã thật nếu ra *"mã N"*); xoay cần đèn Auto/Tắt/Cốt ⇒ chip đổi chữ đúng thứ tự CarSettings.
- 🚗 Đặt chip **Cửa trước-trái**: mở cửa ⇒ icon vạt xoè (sáng); đóng ⇒ vạch sát thân (sáng); không còn chữ Mở/Đóng.
- 🚗 Chip **Chế độ ĐH**: bấm AUTO trên màn AC ⇒ núm chữ A; chỉnh gió tay ⇒ núm có kim; **cùng lúc** ghi
  `hal get getAcControlMode` và `getAcWindLevelManualSign` (test-bridge, KHÔNG `--es args`) ở 2 trạng thái ⇒ chốt OQ3.
- 🚗 Chip **Ghế lái** ↔ **Ghế phụ** cạnh nhau: ghế lái có chấm trước ghế, nhìn từ ghế lái phân biệt được ở 20dp.
- 🚗 Chip **Cửa sổ trời** — **[P2 · soát Opus 27/09] đo được, không phải N/A**: [ĐO 4 lượt quét]
  `BYDAutoBodyworkDevice.getSunroofState = 0` trên xe owner (`carlog-0916/sweep-1.64.json` + 3 tệp `sweep-…` trong
  `perf-oncar-2026-09-26/kachi-logs`) ⇒ chip phải ra **hình đóng + SÁNG** (đã đọc được), không phải nhánh *chưa đọc*
  trung tính. Phép đo: đặt chip lên thanh trên + `hal get getSunroofState` cùng lượt ⇒ cả hai phải nói `0`/đóng.
  Con số 65535 của [ĐO 09-25] là của getter KHÁC (`getSunroofPosition`, datum `sunroof_pos` đã gỡ); *"xe owner không
  có nóc mở"* chỉ là **[ĐOÁN]**. Hình **MỞ** vẫn [CHƯA BIẾT] trên xe này (không có cách kích trạng thái ấy).

## 6. Nợ còn lại

- `scripts/design/gen-car.py` **882 dòng** (trước lượt này 873; L4 +9) — vượt trần 500 từ trước. Tách pure-move
  (parse SVG · bảng ICONS · sinh XML · main) **phải** giữ `--check` byte-identical; không làm trong lượt closing vì
  script dùng chung nhiều làn. Ghi backlog.
- Nghĩa `OK`/`FAKE_OK` của `power_level` là [SUY] từ tên hằng — chốt bằng 🚗 dòng 1 (ghi mã thật khi READY).
- OQ3 chỉ chốt được **tách/ẩn**; câu "cùng một sự thật HAL hay không" chờ 🚗 dòng 3.
