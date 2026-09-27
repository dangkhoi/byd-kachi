# UX8 — chip thanh trên: trạng thái nói bằng HÌNH (họ thứ BA), và chữ `AUTO` viết thường

**Ngày**: 2026-09-27 · **Nhánh**: `feat/voice-hotword-phrases` (HEAD `c160f60` = 2.74 (175) đã ship) ·
**Bản sẽ mang đi**: 2.75 · **Đo ở đâu**: off-car (máy quét nguồn + 2 867 bài `:core` / 1 463 bài `:app`, xanh);
xe/máy ảo: **chưa** — xem §7.

> Owner (2026-09-27, đang nhìn header trên **xe thật**): *"chip header → chế độ lấy gió đổi icon trong /
> ngoài, bỏ chữ trong / ngoài đi chứ; check xem còn chip nào vẫn còn missing như thế này, mình có làm cái
> này 1 lần rồi mà? gió auto chạy ngon, nhưng cần đổi chữ AUTO thành viết thường, không cần viết hoa —
> trên header chip thôi."*

**Kết luận ngắn** — owner đúng cả ba vế, và vế *"mình có làm cái này 1 lần rồi mà"* chỉ ra đúng chỗ hổng:
lượt cũ (`docs/specs/kachi-datum-icon-consistency.html`, 09-21/22) phủ **hai** họ datum, còn `ac_cycle` rơi
vào **họ thứ ba** mà lượt ấy không biết là có. Nay có luật cho họ đó, **bằng bảng dữ liệu**, cộng một bài
canh **soát bằng máy** trả lời mãi mãi câu *"còn chip nào vẫn còn missing"*.

---

## 1. Lượt cũ phủ cái gì, và vì sao `ac_cycle` lọt

| Họ | Luật đã có | Chip hiện ra | Khai ở đâu |
|---|---|---|---|
| **1. BẬT/TẮT** (13 datum) | spec icon-consistency **R3** — MỘT hình, sáng/mờ theo `TelemetryView.onOff` | nhãn ngắn + icon `ACTIVE`/`INACTIVE` | `TelemetryReadout.boolOf` |
| **2. THANG MỨC** (4 datum ghế) | **R4** — một hình + SỐ mức (chấm dưới icon ở ô lớn) | `Ghế lái · 2` | `TelemetryReadout.levelTable` |
| **3. HAI CHẾ ĐỘ** | ❌ **không có luật** | `Chế độ lấy gió · Trong` | — |

Vì sao họ 3 không dùng được luật của họ 1: **không chế độ nào là "tắt"**. *Lấy gió trong* và *lấy gió
ngoài* đều là trạng thái đang chạy, nên `sáng/mờ` không nói được cái nào đang chạy — chip đành quay về in
CHỮ, đúng thứ owner đã gạch bỏ từ 09-21 (*"bỏ chữ Bật/Tắt đi"*).

[ĐO đọc mã] `TelemetryReadout.format`:
`"ac_cycle" -> s.climate.recircOn?.let { if (it) Strings.t("Trong", "Recirc") else Strings.t("Ngoài", "Fresh") }`
— và chip lấy chuỗi ấy nguyên văn. [ĐO ảnh máy ảo 2.73] bảng chip ở `ux-ux6-header-chip-spacing.md` §1 đã
ghi đúng dòng *"Chế độ lấy gió | 52 px | icon + chữ"*.

---

## 2. SOÁT — "còn chip nào vẫn còn missing như thế này?"

Soát **bằng máy**, không bằng mắt: quét thân `TelemetryReadout.format` rồi lọc những nhánh gọi một hàm
đổi cờ/mã thành **CHỮ** (`yesNo` · `openShut` · `levelText` · `Strings.t` tại chỗ). Ba hàm ấy là **toàn bộ**
đường sinh chữ trạng thái trong tệp — datum SỐ không đi qua chúng. [ĐO] 53 nhánh, **13** nhánh in chữ
trạng thái. Cộng 13 datum BẬT/TẮT (ở bảng `boolOf`, không nằm trong `format`).

### 2.1 Bảng soát đầy đủ

| # | mã datum | chữ trên chip **hôm nay** | đề nghị hình theo trạng thái | kết |
|---|---|---|---|---|
| 1 | `ac_cycle` | `Chế độ lấy gió · Trong` / `· Ngoài` | `0` = `ic-air-fresh` (gió từ ngoài vào) · `1` = `ic-recirc` (vòng trong khoang) · chưa đọc = `ic-air-intake` | ✅ **LÀM** (lượt này) |
| 2 | `pm25_online` | `Cảm biến · Có` / `· Không` | `0` = `ic-sensor-off` (gạch chéo) · `1` = `ic-sensor` · chưa đọc = `ic-sensor` + trung tính | ✅ **LÀM** (lượt này) |
| 3 | `door_lf` | `Cửa trước-trái · Mở` / `· Đóng` | cửa mở ↔ cửa khép, trên khung xe nhìn từ trên | ⏸ **HOÃN** — §2.2 (a) |
| 4 | `door_rf` | `Cửa trước-phải · Mở` | — | ⏸ **HOÃN** — §2.2 (a) |
| 5 | `door_lr` | `Cửa sau-trái · Mở` | — | ⏸ **HOÃN** — §2.2 (a) |
| 6 | `door_rr` | `Cửa sau-phải · Mở` | — | ⏸ **HOÃN** — §2.2 (a) |
| 7 | `sunroof_state` | `Cửa sổ trời · Đóng` | nóc mở ↔ nóc khép | ⏸ **HOÃN** — §2.2 (b) |
| 8 | `ac_mode_auto` | `Chế độ điều hòa · AUTO` / `· Chỉnh tay` | cặp hình *tự động ↔ chỉnh tay* | ⏸ **HOÃN** — §2.2 (c) |
| 9 | `ac_wind_auto` | `Chế độ gió · AUTO` / `· Chỉnh tay` | cặp hình *tự động ↔ chỉnh tay* | ⏸ **HOÃN** — §2.2 (c) |
| 10–13 | `seat_{vent,heat}_state[_r]` | `Ghế lái · 2` (KHÔNG còn chữ *"Tắt"/"Mức"*) | — | ✅ **ĐÃ CÓ** họ 2 (UX5/UX5b) — nay **đo lại cả bốn**, không giả định |
| 14–26 | 13 datum BẬT/TẮT (`ac_on` · `anion_state` · 2 sấy · `emergency_alarm` · 8 đèn) | chỉ nhãn ngắn, trạng thái ở sắc thái | — | ✅ **ĐÃ CÓ** họ 1 (09-21) — nay **đo lại cả mười ba**, không giả định |

**Không phải chữ trạng thái** (soát cho đủ, không đổi gì): `gear` (P/R/N/D — chữ cái LÀ giá trị, quy ước
toàn cầu) · `temp_unit` (`C`/`F` — chính là đơn vị) · `vehicle_type` · `vin` (chuỗi tự do) · `ac_wind`
(SỐ + dấu AUTO — xem §4) · `pm25_level` (số 1–6).

⚠ **Phát hiện phụ, KHÔNG thuộc lượt này**: `power_level` và `headlight_feedback` in **mã thô** của khung
(`Nguồn xe · 2`, `Chế độ đèn pha · 2`) — không phải "chữ trạng thái" nên máy soát không bắt, nhưng con số
ấy cũng không nói gì với người đọc. Cần bảng enum → chữ trước khi bàn tới hình. Ghi vào backlog, không
sửa ở đây (ngoài phạm vi owner xin).

### 2.2 Lý do HOÃN (mỗi dòng kèm điều kiện mở khoá)

**(a) Bốn cửa** — ba lý do cộng lại, không phải một:
1. **Nghĩa khác**: *cửa đang mở* là chuyện **đáng BÁO** (`GroupBoard` xếp nó vào `ALERT`), không phải một
   chế độ để liếc. KDoc `TelemetryReadout.boolOf` đã cấm sẵn việc *"âm thầm biến một cảnh báo thành một
   icon mờ"* — bỏ chữ *"Mở"* mà chưa có ai nhìn bằng mắt là làm đúng điều đó.
2. **Khác bộ sinh hình**: hình cửa thuộc `scripts/design/gen-car.py` (nguồn `design/car/top.svg`), không
   phải `gen-icons.py` — cần 4 biến thể *cửa khép* mới, dựng trong đúng khung xe nhìn từ trên.
3. **Làm thì làm cả họ**: bốn cửa đứng cạnh nhau; làm một cái là có ngay hai quy ước hình trong một hàng.

**Mở khoá**: owner duyệt cặp hình *cửa mở ↔ cửa khép* (một lượt vẽ + một lượt nhìn trên xe).

**(b) `sunroof_state`** — cùng họ MỞ/ĐÓNG và cùng bộ sinh hình với bốn cửa; ngoài ra [ĐO 2026-09-25]
`getSunroofPosition` = 65535 trên xe owner (xe **không có** cửa sổ trời) ⇒ không đo được trạng thái thật.
**Mở khoá**: một xe có cửa sổ trời, hoặc owner chấp nhận một cặp hình chưa đo.

**(c) `ac_mode_auto` · `ac_wind_auto`** — chữ giá trị là `AUTO`, một dấu **bốn ký tự** mà chính màn AC gốc
của xe dùng, và owner **vừa xin GIỮ** nó (chỉ viết thường). Bộ icon chưa có cặp hình *tự động ↔ chỉnh tay*
nào đọc ra nghĩa mà không cần học. Hơn nữa chip gió (`ac_wind`) đã trả lời **đúng câu hỏi ấy** bằng
`auto n`, nên một cặp hình mới sẽ là **bề mặt thứ ba** cho một sự thật.
**Mở khoá**: owner chốt một cặp hình thay được chữ `AUTO`.

---

## 3. Cơ chế — bằng DỮ LIỆU, không nhánh theo mã (CLAUDE.md §7)

Ba mảnh, mỗi mảnh một vai:

| Mảnh | Ở đâu | Nói gì |
|---|---|---|
| `TelemetryView.state: Int?` | `:core TelemetryReadout` | mã trạng thái đã chuẩn hoá `0`/`1`; `null` = chưa đọc **hoặc** không thuộc họ 3 |
| `TelemetryReadout.stateTable` | `:core TelemetryReadout` | mã ấy nằm ở **field nào** của `CarStatus` — giữ đúng dạng `"id" -> s.<cụm>.<field>` (hợp đồng đọc-ngược của `CarDataDemandRendererContractTest`) |
| `CapabilityIcons.STATE` | `:core CapabilityIcons` | mỗi mã một **hình**, cộng một hình cho lúc **chưa đọc** |

Bộ dựng chip đọc đúng một câu: `CapabilityIcons.forState(id, view.state)`. Trả `null` ⇒ datum đi đường cũ
(không một `if (id == "ac_cycle")` nào). **Thêm datum thứ ba = thêm hai dòng khai**, không sửa bộ dựng.

Luật hiện ra trên chip:

| Ca | Hình | Chữ | Sắc thái |
|---|---|---|---|
| đọc được chế độ | hình của **đúng** chế độ | chỉ **nhãn ngắn** | `ACTIVE` (cả hai chế độ đều là trạng thái SỐNG) |
| chưa đọc được | hình **trung tính** | chỉ nhãn ngắn, **không** `· —` (B10) | `NEUTRAL` — *"không biết ≠ đang tắt"* |
| tắt nhãn | hình | chuỗi rỗng | như trên |

`ChipView.desc` (câu cho trình đọc màn hình) **vẫn đủ chữ**: `"Chế độ lấy gió: Trong"`. Bỏ chữ là quyết
định về **chỗ trên thanh**, không phải về nội dung — người không nhìn được màn hình không thấy hình.

### 3.1 Vì sao "chưa đọc" của `ac_cycle` cần hình THỨ BA mà `pm25_online` thì không

`ac_cycle`: **cả hai** chế độ đều bình thường, không cái nào "lành" hơn ⇒ vẽ sẵn một chiều gió lúc chưa
biết là một lời khẳng định không ai đo được ⇒ hình riêng (khoang xe, **không có** đầu mũi tên).

`pm25_online`: trạng thái **đáng báo** chỉ có một (*cảm biến chết*) ⇒ nó là cái mang hình riêng (gạch
chéo); *còn sống* và *chưa đọc* dùng chung hình cảm biến, phân biệt bằng **sắc thái** — đúng cách 13 datum
bật/tắt đang làm từ 09-21. Không phải nhượng bộ: đó là cùng một luật, áp cho cùng một hình dạng dữ liệu.

---

## 4. Chữ `AUTO` viết thường — CHỈ trên chip

`ClimateAuto` nay giữ **hai** chính tả **cạnh nhau**: `AUTO` (bề mặt rộng: ô nút, đúng chữ màn AC gốc của
xe) và `AUTO_NARROW = "auto"` + `narrowAuto(text)` cho bề mặt hẹp. Bộ dựng chip gọi `narrowAuto` cho **mọi**
chip datum, nên chip nào mang dấu AUTO cũng tự xuống chữ thường — không nhánh theo mã.

| Bề mặt | Trước | Sau |
|---|---|---|
| chip thanh trên `ac_wind` | `Gió · AUTO 1` | **`Gió · auto 1`** |
| ô NÚT gió (`StepValueFit`/`ControlVisuals.stepText`) | `AUTO` | `AUTO` — **không đổi** (có bài canh) |
| `desc` cho trình đọc màn hình | `Mức quạt gió: AUTO 1` | không đổi (đó là chữ của bề mặt rộng) |

Đặt hai chính tả cạnh nhau là **có chủ ý**: để bản thường trong bộ dựng chip thì lượt sửa sau đổi một bên
là hai bề mặt nói hai chữ mà không ai biết.

---

## 5. Hình mới (3 glyph, sinh bằng máy)

Nguồn `design/glyph/*.svg` + khai trong `design/icon-grammar.json`, sinh bằng
`python3 scripts/design/gen-icons.py`. **Không** vá tay tệp trong `res/drawable` (`--check` so byte).

| tên | tệp nguồn | vẽ gì | path @24dp |
|---|---|---|---|
| `ic-air-fresh` | `air_fresh.svg` | khoang xe (lớp `sub`, y hệt `recirc.svg`) + mũi tên đi **từ ngoài vào** | 2 / trần 6 |
| `ic-air-intake` | `air_intake.svg` | khoang xe (lớp `main`) + hai vạch gió **không đầu mũi tên** | 2 / trần 6 |
| `ic-sensor-off` | `sensor_off.svg` | đúng mô-đun + hai cung của `sensor.svg` + **gạch chéo** một nét chính | 4 / trần 6 |

`ic-recirc` (đã có từ trước, là hình của **nút** *"Lấy gió trong"*) được **dùng lại nguyên vẹn** cho chế độ
*trong* ⇒ chip · ô nút · bộ chọn vẫn **một hình cho một khái niệm** (spec icon-consistency **R1**). Ba hình
gió dùng **cùng một khoang xe** nên đọc ra là một cặp ba.

[👁 ĐO off-car] dựng lại ba hình bằng `flatten()` của chính `gen-icons.py` + Pillow (một tông trắng, đúng
như chip tint): `recirc` vòng-trong-khoang · `air_fresh` mũi tên xuyên mái vào khoang · `air_intake` khoang
trống + hai vạch · `sensor` vs `sensor_off` khác nhau rõ ở nét gạch chéo. Ba luật hình của
`gen-icons.py` (ô quang học 2..22 · cạnh lớn ≥ 16 · ≤ 6 path) **ĐẠT** — script từ chối sinh nếu không.

---

## 6. Bài canh (test)

| Gói | Trước | Sau | Ghi chú |
|---|---|---|---|
| `:core` | 2 855 | **2 867** | **+12** của lượt này (`TopStripStateIconTest` 8 bài mới · `TelemetryReadoutTest` +2 · 2 bài soát lại họ cũ). ⚠ Chạy lại lúc cuối ra **2 878** vì agent khác thêm 11 bài **song song** trong cùng cây — hai con số 2 855/2 867 là hai lượt chạy **của chính lượt này**, đo liền nhau |
| `:app` | 1 463 | **1 463** | +1 bài (`IconSetInventoryTest`), tổng không đổi vì gói chạy lại cả bộ; **1 ĐỎ không liên quan** — `CameraSpanShapeWiringContractTest` thuộc lượt camera đang làm dở của agent khác |

Bài mới, theo thứ tự giá trị:

1. **`TopStripStateIconTest.KHONG chip nao con in chu trang thai ma chua duoc khai (soat bang may)`** — bài
   *quan trọng nhất*: nó **là** câu trả lời cho owner, và nó tự trả lời lại mỗi lần ai thêm datum. Quét
   nguồn `TelemetryReadout.format`, đòi mỗi datum in chữ trạng thái phải thuộc một trong ba họ **hoặc**
   được khai hoãn kèm **lý do ≥ 40 ký tự** (bảng `DEFERRED`, chính là §2.2).
2. `chip che do lay gio doi HINH theo che do…` — hai chế độ ra hai hình khác nhau, chữ *Trong/Ngoài/Recirc/
   Fresh* biến mất, `desc` vẫn đủ.
3. `chua doc duoc che do lay gio thi hinh TRUNG TINH…` — hình trung tính **khác cả hai** hình chế độ.
4. `chip hai che do noi tieng Anh…` — đường EN là đường riêng (nhãn qua `shortEn`).
5. `chip gio viet auto thuong, o NUT van giu AUTO viet hoa` — đo **cả hai** vế của câu owner.
6. `ca 13 chip bat-tat van khong in chu Bat-Tat` + `ca 4 chip thang muc…` — **đo lại** hai họ cũ ở cả hai
   trạng thái thật, không giả định chúng còn đúng.
7. `TelemetryReadoutTest.hinh trang thai va chu trang thai khong bao gio lech` — mồi cả hai chiều qua
   `wiredStatus`: **chữ** và **mã trạng thái** phải đổi cùng nhịp ⇒ bảng đọc trỏ nhầm field là đỏ ngay.
8. `IconSetInventoryTest.moi hinh theo trang thai… tra ra tep that` — tên hình gõ sai ⇒ `iconRes` trả 0 ⇒
   chip **mất icon** đúng lúc chế độ ấy đang chạy, **không lỗi gì**. Bài trên không với tới vì nó chỉ hỏi
   `forTelemetry` (hình *khái niệm*).
9. `IconStyleContractTest` mục sinh-lại-so-byte: `gen-icons.py --check` **117/117 tệp khớp byte**.

**Thử-đỏ (mutation)** — gỡ đúng một dòng khai `"ac_cycle" to StateIcons(...)`:
**6 bài ĐỎ** (`hinh trang thai va chu trang thai khong bao gio lech` · bài soát bằng máy · 4 bài chip
`ac_cycle`). Khôi phục ⇒ xanh lại. Bảng khai KHÔNG phải trang trí.

---

## 7. Chưa đo / còn lại

- **🚗 / 👁 CHƯA NHÌN BẰNG MẮT trên máy ảo hay xe.** Hai lý do: (1) máy ảo **không có HAL** nên `recircOn`
  và `pm25Online` luôn `null` ⇒ chỉ dựng được ca *chưa đọc* (hình trung tính), **không** dựng được hai
  hình chế độ; (2) lúc làm lượt này máy ảo `emulator-5554` đang có phiên harness của agent khác
  (`KachiTest` ghi `prefs_set` lúc 11:28) ⇒ **không cài đè** (CLAUDE.md: không giẫm lên phiên đang chạy).
  ⇒ Bằng chứng hình ở lượt này là **dựng lại glyph off-car** (§5) + bài canh, không phải ảnh màn hình.
- **🚗 Việc cần làm trên xe** (1 phút, buổi xe kế): đặt chip *Chế độ lấy gió* lên thanh trên, bấm nút *Lấy
  gió trong* trên màn AC gốc → chip phải đổi **hình** (vòng-trong-khoang ↔ mũi-tên-từ-ngoài) và **không có
  chữ** nào sau nhãn. Đồng thời liếc chip *Gió*: phải là `auto 1` chữ thường.
- **[CHƯA BIẾT]** Hai hình `ic-recirc` / `ic-air-fresh` có phân biệt được ở **24dp trên màn xe** (1920×720,
  density khác máy ảo) không. Cả hai dùng chung khoang xe nên khác biệt nằm ở **đường gió bên trong** —
  đo được bằng mắt trong 2 giây, không đo được bằng số off-car.
- **[CHƯA LÀM]** 7 datum ở bảng hoãn (§2.2) và hai datum in **mã thô** (`power_level`,
  `headlight_feedback`) — đã ghi backlog, mỗi dòng kèm điều kiện mở khoá.

---

## 8. Tệp đã đụng

| Tệp | Dòng | Việc |
|---|---|---|
| `core/…/CapabilityIcons.kt` | 206 → **265** | bảng `STATE` + `forState` + `hasStateIcons` + `stateIconTable` |
| `core/…/TelemetryReadout.kt` | 324 → **367** | `TelemetryView.state` + `stateTable`/`stateOf` |
| `core/…/TopStripChips.kt` | 209 → **230** | nhánh hình-theo-trạng-thái + gọi `narrowAuto` |
| `core/…/ClimateAuto.kt` | 141 → **164** | `AUTO_NARROW` + `narrowAuto` |
| `app/…/KachiTheme.kt` | +7 | 3 dòng `iconRes` cho hình mới |
| `design/glyph/{air_fresh,air_intake,sensor_off}.svg` | mới | nguồn hình |
| `design/icon-grammar.json` | +12 | khai 3 hình (lĩnh vực `CLIMATE`) |
| `app/src/main/res/drawable/ic_{air_fresh,air_intake,sensor_off}.xml` | sinh | `gen-icons.py` |
| `core/src/test/…/TopStripStateIconTest.kt` | mới **310** | 8 bài |
| `core/src/test/…/TelemetryReadoutTest.kt` | 450 → **497** | 2 bài |
| `app/src/test/…/IconSetInventoryTest.kt` | +17 | 1 bài |

Mọi tệp mã đều **dưới trần 500 dòng** (CLAUDE.md §4.1). Không đụng `strings_kachi.xml` (không có chuỗi
mới: chữ biến mất, và `auto` là hằng **không dịch**) ⇒ không giẫm lên lượt sửa chuỗi của agent khác.
