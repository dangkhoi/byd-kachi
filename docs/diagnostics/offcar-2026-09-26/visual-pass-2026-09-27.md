# Lượt soát THỊ GIÁC 2.74 trên máy ảo — 2026-09-27

**Máy**: AVD `clusternav10`, `emulator-5554` · `wm size` **1920x1080** · `wm density` **240** ⇒ **1,5 px/dp** [ĐO].
**Bản đang chạy**: `dumpsys package com.byd.launcher` ⇒ `versionName=2.73 versionCode=174` (cây đã gộp 2.74 cài dạng
`vehicleTest`, **chưa bump số** — CLAUDE.md §9: đọc từ máy, không đoán) [ĐO].
**Cách đo**: `uiautomator dump` lấy **hộp view px chính xác**, `exec-out screencap -p` + PIL/numpy lấy **hộp mực**.
Không đo bằng mắt trên ảnh. Ảnh lưu trong scratchpad của phiên (tên tệp ở §8).

**Hồ sơ lúc bắt đầu**: `Mặc định` (12 chip, bố cục 2 hàng: Lốp + YouTube). **Đã trả nguyên trạng** — xem §9.

---

## 1. Tổng kết

| # | Kiểm | Kết quả |
|---|---|---|
| 1 | UX1 — chip hồ sơ trên thanh trên | **PASS** (51×51 · đĩa 33×33 · lệch tâm 0,0 px cả hai trục) |
| 2 | UX6 — hai khe của chip | **PASS** (icon↔chữ **12 px = 8dp** · chip↔chip **18 px = 12dp**, đo trên 5 chip có nhãn THẬT và 12 chip `—`) |
| 3 | UX5b — 5 chip mặc định + 2 hình ghế | **PASS** phần hiển thị; **🚗 CHƯA ĐO** phần `hal set` (chế độ kiểm thử đang TẮT) |
| 4 | UX2 — bảng lốp | **PASS** phần thẻ/ảnh xe; **một phần [CHƯA BIẾT]**: tâm khối chữ chỉ kiểm được gián tiếp vì dòng số đang là `—` |
| 5 | UX3 — danh sách câu lệnh nói | **PASS** về hình; **LỆCH SỐ so với tài liệu làn** (45 ≠ 43 · tổng 156 ≠ 153) |
| 6 | UX4 — ô gió `AUTO` | **NOT RUN** — bố cục của owner không có ô điều khiển nào trên màn; chạy được thì phải sửa bố cục owner |
| 7 | Chung — chồng chữ / `…` / hồi quy | **PASS**, kèm **1 ghi chú** (bong bóng chiếu nằm đè dải thanh trên) |

---

## 2. Kiểm 1 — UX1 · chip hồ sơ (`ux-ux1-avatar.md` §4)

Nguồn số: `uiautomator dump` (hộp view) trên hai hồ sơ khác nhau ⇒ hai chữ cái khác nhau.

| Đại lượng | Đo (px) | Quy ra dp | Mong đợi | Verdict |
|---|---|---|---|---|
| Hộp nền pill chip hồ sơ | `[1821,30]–[1872,81]` = **51 × 51** | 34 × 34 | 51 × 51 | ✅ PASS |
| Hộp đĩa (TextView chữ cái) | `[1830,39]–[1863,72]` = **33 × 33** | 22 × 22 | 33 × 33 | ✅ PASS |
| \|tâm đĩa − tâm pill\| ngang | 1846,5 − 1846,5 = **0,0** | 0 | ≤ 1 px | ✅ PASS |
| \|tâm đĩa − tâm pill\| dọc | 55,5 − 55,5 = **0,0** | 0 | ≤ 1 px | ✅ PASS |
| Khe trái / phải / trên / dưới | **9 / 9 / 9 / 9** | 6 / 6 / 6 / 6 | đối xứng | ✅ PASS |
| Chữ trong đĩa | khe trái = khe phải trên ảnh phóng `03-profile-chip-zoom.png` | — | cân | ✅ PASS |

**Phép thử phủ định (bắt buộc theo §4.1 của tài liệu làn)** — bản CŨ đo được **57 × 51** với khe **6 / 18 px**.
Bản đang chạy đo được **51 × 51** với khe **9 / 9 px** ⇒ **hình dạng ĐÃ KHÁC bản cũ** [ĐO]; chẩn đoán §1 của
`ux-ux1-avatar.md` không bị bác.

**Nhịp 4 nút của thanh trên** [ĐO]:

| Nút | Hộp (px) | Rộng × cao | Khe tới nút trước |
|---|---|---|---|
| Nói với xe | `[1605,30]–[1665,81]` | 60 × 51 | — |
| Ứng dụng | `[1677,30]–[1737,81]` | 60 × 51 | **12 px = 8dp** |
| Cài đặt | `[1749,30]–[1809,81]` | 60 × 51 | **12 px = 8dp** |
| Hồ sơ | `[1821,30]–[1872,81]` | **51** × 51 | **12 px = 8dp** |

Ba khe **bằng nhau tuyệt đối** ⇒ đúng một nhịp ✅ (đây là thứ tài liệu làn đòi: chip hồ sơ nay dùng `Sp.S` = 8dp
như ba pill, không còn 9dp riêng). Ghi chú thị giác, **không phải FAIL**: chip hồ sơ là **hình tròn 34dp** còn ba
pill kia là **viên thuốc 40 × 34dp** (`01-header-buttons-zoom.png`) — bề ngang lệch 9 px; đọc ra vẫn một hàng vì
khe bằng nhau và chiều cao bằng nhau, nhưng nếu owner muốn 4 vật **cùng bề ngang** thì đó là một quyết định khác.

**Trợ năng** [ĐO]: `content-desc` nằm trên **chính nút** (`LinearLayout [1821,30]–[1872,81]`) với chuỗi
*"Hồ sơ đang dùng: Mặc định. Chạm để đổi hồ sơ."*; TextView chữ cái **không** mang nhãn. Đúng bản vá #4 của làn
(trước đó nhãn nằm trên view `GONE` ⇒ không đọc được). Đổi sang hồ sơ `Test` ⇒ *"Hồ sơ đang dùng: Test…"* và chữ
`T`, hộp vẫn **51 × 51 / 33 × 33** ⇒ không phụ thuộc chữ cái ✅.

---

## 3. Kiểm 2 — UX6 · hai khe của chip (`ux-ux6-header-chip-spacing.md`)

### 3.1 Trên hồ sơ có NHÃN THẬT (5 chip mặc định) — `23-default5-chips.png`

`icon box` = `[trái chip, trái chip + 24)`; `text box` = `trái chip + 36`. Cột mực đo bằng numpy.

| Chip | Hộp (px) | Rộng | Mực icon | Mực chữ bắt đầu | **khe icon→chữ** | **khe chữ→icon kế** |
|---|---|---|---|---|---|---|
| `PM2.5 · —` | `906–1030` | 124 | 910–926 | **943** | **12 px = 8dp** | 1030→1048 = **18 px = 12dp** |
| `—°C ngoài` (không icon) | `1048–1138` | 90 | — | 1049 | n/a | 1138→1156 = **18 px = 12dp** |
| `—% · — km` | `1156–1287` | 131 | 1163–1172 | **1193** | **12 px = 8dp** | 1287→1305 = **18 px = 12dp** |
| `Ghế lái · —` | `1305–1434` | 129 | 1310–1324 | **1342** | **12 px = 8dp** | 1434→1452 = **18 px = 12dp** |
| `Ghế phụ · —` | `1452–1593` | 141 | 1456–1470 | **1489** | **12 px = 8dp** | hết hàng |

⇒ **4 chip icon+nhãn** (≥ 3 như yêu cầu) đều cho khe trong **12 px = 8dp** (`CHIP_ICON_GAP`) và khe ngoài
**18 px = 12dp** (`CHIP_GAP`). Tỉ lệ ngoài/trong = **1,5×** — đúng ràng buộc gần-xa `CHIP_GAP ≥ 1,5 × CHIP_ICON_GAP` ✅.

### 3.2 Trên hồ sơ đang dùng (12 chip, mọi giá trị `—`) — `02-header-chips-zoom.png`

Hộp chip: `843–895 · 913–949 · 967–1019 · 1037–1089 · 1107–1131 · 1149–1201 · 1219–1271 · 1289–1341 ·
1359–1411 · 1429–1453 · 1471–1523 · 1541–1593`.

- **11/11 khe giữa hai hộp = 18 px = 12dp** (không một cặp nào lệch) ✅
- Chip **icon + chữ** rộng **52** = `24 icon + 12 khe + 16 chữ "—"`; chip **chỉ icon** rộng **24** (không thừa lề
  phải); chip **không icon** (`—°C`) rộng **36**. Khớp từng px với bảng số học §5 của tài liệu làn ✅
- Mực: icon `847–863` rồi chữ `880–893` ⇒ hộp icon kết thúc ở 867, hộp chữ bắt đầu ở **879** = 12 px sau ✅

### 3.3 Không có `…`

Hàng chip = `[259,41]–[1593,69]` = **1334 px = 889dp**. 12 chip dùng **751 px**, 5 chip mặc định dùng **688 px**
⇒ dư ≥ 583 px. Mọi nhãn hiện đủ chữ; không chip nào cắt đuôi ✅.

---

## 4. Kiểm 3 — UX5b · 5 chip mặc định + hai hình ghế (`ux-ux4-ux5-climate-seat.md` §8.5)

Hồ sơ `Test` trên máy này đang mang **đúng danh sách mặc định**
(`TopStrip.kt:208` `DEFAULT_IDS = listOf(PM25, TEMP, ENERGY, SEAT, SEAT_R)`), nên không cần tạo hồ sơ mới.

| Kiểm | Đo | Verdict |
|---|---|---|
| Số chip | **đúng 5**, đúng thứ tự `PM2.5 · Nhiệt · Năng lượng · Ghế lái · Ghế phụ` | ✅ PASS |
| Chữ khi chưa đọc được | `Ghế lái · —` và `Ghế phụ · —` (a11y: *"Mức ghế sưởi: — · Mức ghế mát: —"* / *"…phụ…"*) | ✅ PASS |
| Hai hình ghế KHÁC nhau | chênh lệch pixel L1 giữa hai hộp icon 24×29 = **28808** (trung bình **13,8/kênh·px**, `0` nghĩa là giống hệt) | ✅ PASS |
| Phân biệt được ở 16dp | `22-seat-chips-zoom.png`: `ic-seat-left` và `ic-seat` là **ảnh gương** của nhau (lưng ghế đổi bên) | ✅ PASS, kèm ghi chú |
| Không bị `…` ở 1920 | hàng 5 chip chiếm 688/1334 px | ✅ PASS |
| `hal set seat_heat_state=3` / `seat_vent_state_r=2` | **KHÔNG CHẠY** | 🚗 xem dưới |

**Ghi chú (không phải FAIL)**: hai hình ghế chỉ khác nhau **bằng phép lật gương**. Ở 16dp trên xe đang chạy, đó
là khác biệt yếu nhất có thể có; chữ `Ghế lái`/`Ghế phụ` mới là thứ tách hai chip. Nếu owner muốn phân biệt được
bằng liếc mắt thì cần một dấu thứ hai (vd chấm bên), không phải chỉ lật hình — để owner chốt.

**Vì sao không chạy `hal set`** [ĐO]: `am broadcast … --es cmd state` trả `{"ok":false,"error":"test_mode_off"}`.
Bật chế độ kiểm thử **không có đường broadcast** (có chủ ý — KDoc `TestBridgeStore`); đường duy nhất là
`scripts/emulator/voice-e2e.sh::enable_test_mode`, tức `am force-stop com.byd.launcher` rồi ghi
`shared_prefs/kachi_test_bridge.xml`. Việc đó **giết launcher trước mặt owner** nên lượt này không làm.
⇒ *"Ghế lái · 2"* + hình sưởi / *"Ghế phụ · 1"* + hình mát vẫn **🚗 CHƯA ĐO**.

---

## 5. Kiểm 4 — UX2 · bảng lốp (`ux-ux2-tyre.md` §4) — `05-tyre-board.png`, `04-tyre-car-strip.png`

Ô lốp = `[42,143]–[1878,553]` (1836 × 410 px). Bốn thẻ tìm bằng lọc **đúng màu nền thẻ** `#222941` (34,41,65).

| Thẻ | Hộp (px) | Rộng × cao | Tâm thẻ |
|---|---|---|---|
| TT (trên-trái) | `[79,211]–[853,307]` | 775 × 97 | (466,0 · 259,0) |
| TP (trên-phải) | `[1066,211]–[1840,307]` | 775 × 97 | (1453,0 · 259,0) |
| ST (dưới-trái) | `[79,419]–[853,516]` | 775 × 98 | (466,0 · 467,5) |
| SP (dưới-phải) | `[1066,419]–[1840,516]` | 775 × 98 | (1453,0 · 467,5) |

### 5.1 Con số trên trục thẻ — ✅ PASS

| Thẻ | Mực dòng số (`—`) | Tâm ngang | Lệch trục thẻ | Mực dòng phụ | Tâm ngang | Lệch trục |
|---|---|---|---|---|---|---|
| TT | `448–487` | 467,5 | **+1,5 px (1,0dp)** | `456–475` | 465,5 | −0,5 px |
| TP | `1435–1474` | 1454,5 | **+1,5 px** | `1443–1463` | 1453,0 | 0,0 px |
| ST | `448–487` | 467,5 | **+1,5 px** | `456–475` | 465,5 | −0,5 px |
| SP | `1435–1474` | 1454,5 | **+1,5 px** | `1443–1463` | 1453,0 | 0,0 px |

Lệch ≤ **1,5 px (1,0dp)** trên cả 4 thẻ, và hai dòng **cùng trục** (lệch giữa chúng ≤ 2 px) ⇒ vá V3 đúng ✅.
(+1,5 px là chênh giữa **hộp mực** của glyph `—` và **bề rộng bước** mà `lineStartX` canh — không phải lệch bố cục.)

### 5.2 Khối chữ cân giữa thẻ — ⚠ chỉ kiểm được GIÁN TIẾP [CHƯA BIẾT]

Máy ảo không có HAL lốp nên dòng số đang là `—`. Em-dash **không có mực ở đỉnh cap** nên "tâm hộp mực" **không
bằng** tâm khối chữ mà `CellTextLayout.twoLineTopBaseline` dựng (nó lấy `getTextBounds("0")`, tức cap-height của
**chữ số mẫu**). Vì vậy:

| Đo được | Số | Nghĩa |
|---|---|---|
| Tâm hộp mực (`—` + dòng phụ) − tâm thẻ | **+7,5 px** trên **cả 4** thẻ | KHÔNG phải "khối lệch 7,5 px" — xem giải thích |
| Chênh lệch giữa 4 thẻ | **0,0 px** (TT=TP, ST=SP; hàng trên/dưới lệch đúng 0,5 px do tâm thẻ lẻ) | ✅ **bốn thẻ CÙNG một baseline**, đúng mục tiêu V2 |
| Dựng lại công thức bằng số (Roboto cap 0,711em, descent 0,25em; `cellH` = `min(410·0,24 ; …)` = 98,4 ⇒ `baseBig` 57,07 px, `baseSub` 17,22 px) | baseline dòng phụ dự đoán **tâm + 29,8 px**, đo được **tâm + ~32,0 px** | dư **≈ 2,2 px (1,5dp)** so với mô hình |

Chiều cao mực `TT` đo được **13 px** khớp dự đoán `0,711 × 17,22 = 12,2 px` ⇒ **cỡ chữ dòng phụ đúng như công
thức** [ĐO]. Phần dư 2,2 px nằm trong sai số của hằng phông giả định (`isFakeBoldText = true` nới hộp mực của
`getTextBounds("0")`) — **không đủ để kết luận FAIL, cũng không đủ để kết luận PASS ở mức ±2 px**.

⇒ **Muốn chốt thì phải có CHỮ SỐ thật trong thẻ**: một lượt trên xe (có HAL lốp), hoặc off-car thêm một đường
bơm `TyreReading` giả qua cầu kiểm thử (chưa có lệnh nào; `WRITABLE_PREFS_KEYS` không có khoá lốp).

### 5.3 Thẻ **không** đè lên ảnh xe — ✅ PASS

| Đại lượng | Đo (px) | Dựng lại bằng công thức |
|---|---|---|
| Khung ảnh (letterbox) | — | `866,5 … 1053,5` (ngang) · `159,4 … 544,8` (dọc) |
| Mép TRONG thẻ trái | **853** | `min(wheelX, imageLeft) − gap` = `866,5 − 12,3` = **854,2** ✅ |
| Mép TRONG thẻ phải | **1066** | `max(wheelX, imageRight) + gap` = `1053,5 + 12,3` = **1065,8** ✅ |
| Hộp mực ảnh xe | `x 874–1044` · `y 173–530` (**171 × 358**) | nằm TRỌN trong khung, không pixel nào bị thẻ cắt ✅ |
| Hàng rộng nhất của xe (**gương**) | `y 286–294`, `x 875–1044` | cách mép thẻ **22 px** mỗi bên ⇒ **gương hiện đủ** ✅ |

Lề trong suốt đo được của ảnh: **7,5 px trái / 9,5 px phải / 13,6 px trên** — khớp lề thật của
`default-car.png` (27/678 = 3,98 % ngang, 48/1397 = 3,44 % dọc) nhân tỉ lệ 0,2758 ⇒ **feather KHÔNG ăn vào
thân xe** (vá V4 đang làm đúng việc: mép đã có lề trong suốt thì dải = 0) ✅. Ảnh xe **nguyên vẹn**.

### 5.4 Bảng CỬA + xe mini — **NOT RUN**

Hai bảng đó không có trong bố cục của owner; xem được thì phải đổi bố cục/ô rồi trả lại. Để lượt sau (xem §7).

---

## 6. Kiểm 5 — UX3 · *Cài đặt › Giọng nói › Câu lệnh nói được* (`ux-ux3-voice-list.md` §4)

`13-voice-section-header.png` · `14-voice-climate-expanded.png` · `15-voice-climate-rows.png` ·
`16-voice-body-expanded.png`

| Kiểm | Đo | Verdict |
|---|---|---|
| Tiêu đề *"Câu lệnh nói được"* nằm **SAU** khối *Nhạc* | khối *Nhạc* ở `y 311`, tiêu đề ở `y 458` | ✅ PASS |
| Mọi nhóm **GẬP** lúc mở trang | 12/12 nhóm mang dấu `▸` | ✅ PASS |
| Mỗi tiêu đề có **số câu** | có, cột phải `x 1740–1793` | ✅ PASS |
| Mở *Khí hậu & không khí* | dấu đổi `▸ → ▾`, thân mở ra | ✅ PASS |
| Mở *Thân xe · cửa · kính* | mở được, 47 câu | ✅ PASS |
| Gập lại đúng nhóm đó, nhóm khác không đổi | ✅ đã thử cả hai | ✅ PASS |
| Dòng = **câu (đậm)** trên **việc nó làm (mờ)** | vd `bật lọc bụi` / `Bật Lọc bụi`; `bật 50% kính lái` / `Bật 50% kính lái` | ✅ PASS |
| Không cắt chữ ở bề rộng 1920 | mọi dòng rộng **1341 px** (`x 471–1812`), bước dòng 77 px, không `…` | ✅ PASS |

**Số câu đo trên máy** (12 nhóm) [ĐO]:

| Nhóm | Số câu | Nhóm | Số câu |
|---|---|---|---|
| Năng lượng & sạc | 13 | Giải trí · cụm · HUD | 3 |
| Động lực & tốc độ | 2 | Nhạc | 4 |
| **Khí hậu & không khí** | **45** | Dẫn đường | 2 |
| Lốp | 8 | Ứng dụng · ô · bố cục | 12 |
| **Thân xe · cửa · kính** | **47** | Hồ sơ tài xế | 3 |
| Đèn | 15 | | |
| | | **Tổng** | **156** |

⚠ **LỆCH so với tài liệu làn**: `ux-ux3-voice-list.md` ghi *"43 câu"* cho Khí hậu và *"153 câu VI"* tổng; máy ảo
cho **45** và **156**. *Thân xe* khớp (**47**). [SUY] chênh +2 Khí hậu / +3 tổng đến từ các làn gộp sau khi tài
liệu UX3 được viết (UX4/UX5 thêm nút gió-AUTO gộp + 2 datum ghế phụ ⇒ thêm câu trong registry).
**Việc cần làm**: sửa con số trong `ux-ux3-voice-list.md` §4 và dòng UX3 ở `docs/README.md:284` (đang ghi *43*).

---

## 7. Kiểm 6 — UX4 · ô gió `AUTO` — **NOT RUN**

Bố cục owner đang dùng có **2 hàng**: hàng 1 = bảng **Lốp**, hàng 2 = ô ứng dụng **YouTube**. **Không có ô điều
khiển nào** (không thanh nút xe, không ô gió) trên màn ⇒ không có nút `−`/`+` để bấm.

Chạy được thì phải **sửa bố cục hoặc thanh nút của hồ sơ owner** rồi trả lại — vượt phạm vi *"trả nguyên trạng"*
của lượt này. Lượt sau làm rẻ nhất như sau (đã khảo sẵn):

1. *Cài đặt › Hồ sơ tài xế* → chuyển sang hồ sơ **`Test`** (hồ sơ nháp, `3 ô · 0 ô có nội dung`).
2. Bày ô **Gió** trên thanh nút của **hồ sơ Test** (không đụng `Mặc định`).
3. `uiautomator dump` lấy hộp nút `−`; chụp **trước**; bấm `−` xuống mức 1 rồi `−` lần nữa; chụp **sau**.
4. So hộp `−`/`+` của ô **nhiệt độ** giữa hai ảnh (lệch 1 px = sàn chung bị đụng); kiểm chữ `AUTO` nằm trọn ô giá trị.
5. `+` một lần, rồi chuyển về hồ sơ `Mặc định`.

---

## 8. Kiểm 7 — chung + ảnh

**Không có chồng chữ trên thanh trên** [ĐO]: 11/11 khe giữa hộp chip = +18 px (dương), khe chip cuối → nút mic =
`1605 − 1593` = **12 px**, ba khe nút = 12 px. Không hộp nào giao nhau.

**Không có `…` ở chỗ tài liệu làn nói là không có** ✅ (xem §3.3).

**Ghi chú thị giác — bong bóng chiếu đè dải thanh trên** [ĐO]:
`dumpsys window windows` ⇒ `Window{… u0 com.byd.launcher}` `ty=APPLICATION_OVERLAY` `appop=SYSTEM_ALERT_WINDOW`
`mAttrs={(318,0) wrapxwrap gr=TOP START …}` = **bong bóng "hoa Kachi" để chiếu** (`BubbleRenderer`). Hộp mực đo
được **`x 336–371` · `y 18–51`** (36 × 34 px) — tức nó nằm **đè lên dải thanh trên** (thanh trên là `y 24–87`),
trong khoảng trống bên trái hàng chip. Hôm nay **không va vào gì**: hàng chip canh phải, chip trái nhất bắt đầu ở
`x 843` (12 chip) / `x 906` (5 chip) ⇒ còn **472 px** đệm. Nhưng nếu số chip tăng tới mức hàng chip chạm `x 371`
thì chữ sẽ chui xuống dưới bong bóng. Đây là **thiết kế đang có**, không phải hồi quy của 2.74 — ghi lại để owner
biết ranh giới. Ảnh: `hdr-emoji-zoom.png`.

**Không thấy hồi quy thị giác nào khác** trên các bề mặt đã xem (thanh trên, bảng lốp, trang Cài đặt Giọng nói).

### Danh sách ảnh (đều trong scratchpad của phiên)

| Tên | Nội dung |
|---|---|
| `00-initial.png` | Màn chính lúc bắt đầu (hồ sơ `Mặc định`) — mốc so sánh |
| `01-header-buttons-zoom.png` | 4 nút thanh trên, phóng 15× |
| `02-header-chips-zoom.png` | 12 chip (mọi giá trị `—`), phóng 2× |
| `03-profile-chip-zoom.png` | Chip hồ sơ `M`, phóng 8× |
| `hdr-emoji-zoom.png` | Bong bóng chiếu (hoa Kachi) ở `x 336–371` |
| `04-tyre-car-strip.png` | Dải giữa hai cột thẻ — ảnh xe + mép thẻ |
| `05-tyre-board.png` | Toàn bộ widget Lốp |
| `13-voice-section-header.png` | Tiêu đề *Câu lệnh nói được* sau khối *Nhạc*, 12 nhóm gập |
| `14-voice-climate-expanded.png` | *Khí hậu & không khí* vừa mở |
| `15-voice-climate-rows.png` | Các dòng câu lệnh (câu đậm / việc mờ) |
| `16-voice-body-expanded.png` | *Thân xe · cửa · kính* đã mở |
| `21-profile-test-header.png` | Thanh trên của hồ sơ có **5 chip mặc định** |
| `22-seat-chips-zoom.png` | Hai chip ghế, phóng 4× (`ic-seat-left` vs `ic-seat`) |
| `23-default5-chips.png` | 5 chip mặc định, phóng 2× |
| `31-restored-home.png` | Màn chính sau khi trả nguyên trạng |

---

## 9. Trạng thái máy ảo sau lượt kiểm

| Việc | Trạng thái |
|---|---|
| Hồ sơ | **`Mặc định`** (đã đổi tạm sang `Test` để kiểm §4, **đã đổi về**) |
| Bố cục / nội dung ô | **không đụng** (không thêm/bớt ô, không đổi preset, không đổi chip) |
| Nhóm câu lệnh trong Cài đặt | **đã gập lại cả hai nhóm đã mở** |
| Cài đặt | **đã đóng** (nút *Xong*) |
| Tiền cảnh | `mResumedActivity: com.byd.launcher/…KachiHomeActivity` ✅ |
| Chế độ kiểm thử | **vẫn TẮT** như lúc bắt đầu (không bật, không ghi prefs) |
| Cài đặt/gỡ app | **không** cài gì, **không** commit gì |

**Chứng minh bằng pixel**: `31-restored-home.png` so với `00-initial.png` khác **516 px**, toàn bộ nằm trong
`x 63–109 · y 47–65` = **đúng vùng chữ số đồng hồ** (01:05 → 01:14). Mọi vùng khác **giống từng pixel** [ĐO].

**Một sự kiện ngoài ý muốn**: giữa lượt, hệ thống bật hộp thoại *"Update your app"* (không phải của Kachi) đè lên
màn; đã bấm **Close** để đóng, không bấm UPDATE. Không để lại thay đổi nào.

---

## 10. Việc cần làm (kèm chủ)

| # | Việc | Tệp | Chủ đề xuất |
|---|---|---|---|
| 1 | Sửa số câu *Khí hậu*: **43 → 45**, tổng **153 → 156** | `docs/diagnostics/offcar-2026-09-26/ux-ux3-voice-list.md` §4 và `docs/README.md:284` | làn UX3 |
| 2 | Chốt tâm khối chữ thẻ lốp bằng **chữ số thật** (xe, hoặc thêm đường bơm `TyreReading` vào cầu kiểm thử) | `TyreBoardView` / `TestBridge*` | làn UX2 |
| 3 | Chạy UX4 (`AUTO` ô gió) theo 5 bước ở §7, trên **hồ sơ `Test`** | — | QA lượt sau |
| 4 | 🚗 `hal set seat_heat_state=3` / `seat_vent_state_r=2` để chốt *"Ghế lái · 2"* + hình sưởi | — | buổi xe |
| 5 | Xem bảng **Cửa** + **xe mini** (dùng chung ảnh xe đã đổi feather) | `DoorBoardView` | QA lượt sau |
| 6 | Quyết định: 4 nút thanh trên có cần **cùng bề ngang** không (nay 40dp × 3 + 34dp) | `KachiTopStrip` | owner |
| 7 | Quyết định: hai hình ghế chỉ khác nhau bằng **lật gương** — có cần dấu thứ hai không | `ic-seat*` | owner |

---

## Lượt 2 — 2026-09-27

**Máy**: AVD `clusternav10`, `emulator-5554` · 1920×1080 @240dpi = **1,5 px/dp** · bản trên máy đọc từ
`dumpsys package` ⇒ `versionName=2.73 versionCode=174` (cây gộp 2.74 cài dạng `vehicleTest`, chưa bump — CLAUDE.md §9) [ĐO].

**Cách dựng bố cục — KHÔNG mò UI** (CLAUDE.md §15: đọc/ghi state bền trước, UI là phương án cuối): bố cục và ô
được dựng bằng cách **ghi thẳng `shared_prefs/kachi_workspace.xml`** (force-stop → ghi → mở lại, đúng kỹ thuật
`scripts/emulator/voice-e2e.sh::put_app_file` nhánh root của máy ảo), **chỉ đụng khoá `Test__*` + `active_profile`**.
Tệp gốc đã sao lưu nguyên văn TRƯỚC và trả lại nguyên văn SAU (xem §9b). Cầu kiểm thử chỉ bật cho **kiểm 5**
(camera) theo đúng đường `enable_test_mode`, và đã tắt ngay sau đó.
Cả lượt chỉ có **10 cú chạm UI**: 5 lần nút −/+ của ô gió · 3 lần mở *Cài đặt › Màn hình chính › Vẽ bố cục riêng…* · 1 lần *Đóng* · 1 phím Back.

Đo: `uiautomator dump` cho **hộp view px**, `exec-out screencap` + PIL cho **hộp mực**. Không kết luận bằng mắt.

### 1b. Tổng kết lượt 2

| # | Kiểm | Kết quả |
|---|---|---|
| 1 | UX4 — ô gió xuống `AUTO`, ô nhiệt không xê dịch | **PASS về bố cục** · **1 FAIL phụ**: cỡ chữ số KHÔNG trở lại sau khi rời AUTO (xem §10b-1) |
| 2 | UX7 — hai vòng `RING`, ô Tốc độ, thẻ nhóm `CARD`, ô đọc thanh nút | **PASS** (khối chữ ôm tâm vòng ≤ 0,6 px · ba cụm số+đơn vị lệch trục ≤ 0,5 px) |
| 3 | Bảng **Cửa & khoang** + **Trạng thái xe** | **PASS** — xe cao **261 px** khớp từng px mô hình `CAR_BOTTOM = 0.98`; mô hình cũ `0.86` bị bác |
| 4 | Ô **Trình chiếu ảnh** (thư mục rỗng) + số khung **Vẽ bố cục** | **PASS** (lệch ngang 0,0 px · lệch dọc ≤ 1,5 px / ≤ 0,5 px) |
| 5 | Camera GL tổng hợp (tuỳ chọn) | **PASS** — vòng chuẩn tròn (w/h = 0,9960), vạch chuẩn cong ≤ 2 px trên 500 px |

### 2b. Kiểm 1 — UX4 · ô gió `AUTO` (nối tiếp §7 lượt 1)

Bố cục: hồ sơ **`Test`**, preset `THREE`, thanh nút **BOTTOM** bật với đúng ba ô `fan,temp,fuel_pct`
(hồ sơ `Mặc định` **không bị đụng**). Ảnh: `41-cfgA-home.png` … `45-fan-3.png`.

Chuỗi thao tác: `−` ×3 (4→1) · `−` ×1 (1→**AUTO**) · `+` ×1 (rời AUTO) · `+` ×1.

| View | mức 4 (41) | mức 1 (42) | **AUTO** (43) | sau `+` (44) | sau `+` (45) |
|---|---|---|---|---|---|
| Gió `−` | `[795,984]–[818,1024]` | y hệt | y hệt | y hệt | y hệt |
| Gió **ô giá trị** | `[818,989]–[865,1018]` 47×29 | y hệt | y hệt | `[818,994]–[865,1013]` **47×19** | 47×19 |
| Gió `+` | `[865,984]–[889,1024]` | y hệt | y hệt | y hệt | y hệt |
| **Nhiệt `−`** | `[913,984]–[936,1024]` | **y hệt** | **y hệt** | **y hệt** | **y hệt** |
| **Nhiệt `+`** | `[983,984]–[1007,1024]` | **y hệt** | **y hệt** | **y hệt** | **y hệt** |

⇒ **Hai nút −/+ của ô NHIỆT lệch đúng 0 px qua cả 5 trạng thái** — chữ `AUTO` **không** nới sàn chung, đúng
điều R2.4/UX4 hứa ✅ (đây là mục đích chính của kiểm này).

**Chữ `AUTO` nằm trọn ô giá trị** [ĐO] `43-fan-auto.png`:

| Đại lượng | Số |
|---|---|
| Hộp ô giá trị | `[818,989]–[865,1018]` = 47 × 29 |
| Hộp **mực** chữ `AUTO` | `[828,999]–[854,1008]` = **27 × 10** |
| Lề trái / phải / trên / dưới | **10 / 10 / 10 / 9** px |
| Tâm mực − tâm ô | ngang **0,0** px · dọc **+0,5** px |
| Cao mực `AUTO` so với `1` cùng ô | 10 px vs **15 px** ⇒ tỉ lệ co **0,67** = đúng sàn `AUTO_FIT_MIN_SP 9` / `valueSp` |

⇒ Chữ **có co** (`setAutoSizeTextTypeUniformWithConfiguration`), **không bị cắt**, cân trong ô ✅.
Bấm `+` ⇒ rời AUTO, ô hiện `2` rồi `3` ✅ (máy ảo không có HAL nên đây là giá trị lạc quan của
`ControlTileState`; nhãn/bố cục mới là thứ kiểm ở đây, đúng yêu cầu).

### 3b. Kiểm 2 — UX7 · vòng đo · cụm số + đơn vị

#### 3b.1 Hai vòng `RING` — `41-cfgA-home.png` / `45-fan-3.png`

Hình học vòng suy từ chính ảnh (hàng rộng nhất của nét vòng), rồi dựng lại `RingView.onDraw` bằng số:

| | **Năng lượng** (ô `THREE` 0) | **Không khí** (ô `THREE` 1) |
|---|---|---|
| Đường kính NGOÀI đo được | **621 px** | **260 px** |
| ⇒ `m` = d/(0,76+0,062) | 756,1 | 316,3 |
| ⇒ `d` · `sw` | 574,6 · 46,9 | 240,4 · 19,6 |
| ⇒ cỡ dòng số · dòng phụ (`BIG/SMALL_RATIO`) | 149,4 · 68,95 | 62,5 · 28,85 |
| ⇒ `lineGap` = cỡ phụ × `SUB_LINE_GAP 1,35` | 93,1 | 38,9 |
| **Tâm vòng đo được** `cy` | **501,5** (hàng rộng nhất y 467..536) | **281,0** (y 264..298) |
| Baseline dòng phụ đo được (đáy chữ `n` / vị trí mực `—`) | **≈ 596,0** | ⇒ base ≈ **282,0** |
| ⇒ baseline dòng số suy ra | **502,9** | **282,0** |
| Baseline dòng số theo công thức 2.74 (cap `0` ≈ 0,748 em có fake-bold) | **502,4** | **281,4** |
| **Δ tâm khối chữ ↔ tâm vòng** | **+0,5 px** (+0,09 % · d) | **+0,6 px** (+0,25 % · d) |
| Cùng phép đo, nếu code còn là **2.73** (`cy + 0,36·cỡ` / `cy + 0,9·cỡ + cỡphụ`) | baseline phụ phải ở **705,5** — đo được **596,0** ⇒ lệch **109,5 px** | — |
| ⇒ tâm khối của 2.73 | **+81,8 px = +14,2 % · d** (tài liệu làn dự đoán +14,6 %) | — |

⇒ **Bản vá R1 đang chạy thật**, và khối chữ nay **ôm tâm vòng** với |Δ| ≤ 0,6 px ✅ (ngưỡng đặt ra: ≤ 2 px).
*Ghi chú phương pháp*: dòng số đang là `—` (máy ảo không có HAL) nên **không** đo "tâm hộp mực" — em-dash không
có mực ở đỉnh cap; số trên là baseline **dựng lại** từ mực đo được + số đo phông, đúng cách §5.2 của lượt 1 đã nêu.
Mức bằng chứng: hộp mực và tâm vòng = **[ĐO]**, quy đổi ra baseline = **[SUY]** (±1 px theo cap-height của phông).

**`µg/m³` không tràn vòng** [ĐO]: mực `[1494,301]–[1569,326]` (rộng **76 px**, nửa rộng 38); nửa dây cung TRONG
của vòng tại đúng hàng đó (`r_trong` = 110,4; dy = 45) = **100,8 px** ⇒ dư **62 px** mỗi bên ✅.

**Vòng ở ô `ONE` KHÔNG chạy**: `ONE` chỉ đổi `m` (≈ 956 ⇒ d ≈ 726) trong khi mọi đại lượng của `RingView` là
**tỉ lệ của `d`**; hai cỡ đã đo (d = 574,6 và d = 240,4) đã kẹp cả dải ⇒ chạy thêm `ONE` không thêm thông tin.

#### 3b.2 Ba cụm `số + đơn vị` (`AxisRow`) — lệch trục

| Chỗ dựng | Ảnh | Hộp SỐ | Tâm số | Trục ô (nhãn/chú thích của chính ô) | **Δ** | Lệch của 2.73 (nửa bề rộng đơn vị) |
|---|---|---|---|---|---|---|
| **Tốc độ** `w_speed` (ô `QUAD`) | `46-cfgB-home.png` | `[464,272]–[514,360]` | **489,0** | chú thích *Tốc độ hiện tại* `[48,360]–[929,399]` ⇒ **488,5** | **+0,5 px** | −28,0 px (` km/h` rộng 56 px) |
| **Thẻ nhóm `CARD`** *Năng lượng* | `41-cfgA-home.png` | `[1512,586]–[1551,654]` | **1531,5** | nhãn *Pin (SOC)* `[1184,654]–[1878,677]` ⇒ **1531,0** | **+0,5 px** | −10,0 px (` %` rộng 20 px) |
| **Ô đọc thanh nút** *Mức xăng* | `41-cfgA-home.png` | `[1070,999]–[1087,1030]` | **1078,5** | nhãn `[1031,978]–[1125,999]` ⇒ **1078,0** | **+0,5 px** | −5,5 px (`%` rộng 11 px) |

Đơn vị treo **bên phải** đúng thiết kế `AxisRow` (`[514,300]–[570,331]` · `[1551,599]–[1571,640]` ·
`[1093,1004]–[1103,1024]`), không tham gia phép canh ⇒ **con số nằm trên trục ô** ở cả ba ✅.

### 4b. Kiểm 3 — bảng **Cửa & khoang** + **Trạng thái xe** — `46-cfgB-home.png`

Bố cục: `QUAD` = `w_speed` · `g_doors` · `w_car` · `w_photos`.

| Đại lượng | Đo (px) | Dựng lại bằng công thức |
|---|---|---|
| `DoorBoardView` | `[984,143]–[1878,437]` = **894 × 294** | — |
| Khung ảnh xe `0,03 … 0,98 × H` | — | y **151,8 … 431,1** (cao 279,3) |
| Lề trong suốt của `default-car.png` | — | 3,44 % dọc · 3,98 % ngang |
| **Hộp mực xe** | `[1370,161]–[1492,421]` = **123 × 261** | dự đoán **124,8 × 260,1**, biên **161,4 … 421,5** ⇒ **khớp ≤ 1 px** ✅ |
| Cùng phép đo với `CAR_BOTTOM` **cũ 0,86** | — | phải ra **109 × 227**, biên 152 … 379 ⇒ **BỊ BÁC** |
| ⇒ xe to hơn bản 2.73 | — | **× 1,15** (261 / 227) |
| Tâm mực xe ↔ tâm ô vẽ | ngang **+1,5** · dọc **0,0** | cân ✅ |
| **Gương** (hàng rộng nhất) | y **243..252**, x **1370..1492** (123 px) vs thân ~111 px | gương nhô ~6 px mỗi bên, cách mép ô **386 px** ⇒ **hiện đủ**, feather không ăn ✅ |
| Hàng nút dưới bảng | `[984,437]–[1878,553]`; ba nhãn *Cốp sau · Cửa sổ trời · Rèm che nắng* ở y 485..529; chấm trang y ≈ 541; đáy ô 571 | **không bị cắt** ✅ |
| `CarMiniView` (*Trạng thái xe*) | `[48,608]–[929,993]`; tâm mực ngang **488,5** = tâm ô **488,5**; hàng gương y 745..750 (x 410..566) | **ĐỐI CHỨNG** — UX7 §1 xếp ô này *"đúng sẵn, không đụng"*; không cắt, không lệch ✅ |

### 5b. Kiểm 4 — ô **Trình chiếu ảnh** (thư mục rỗng) + số khung **Vẽ bố cục**

`PhotoWidgetView` = `[966,584]–[1896,1056]` (930 × 472), thư mục ảnh **rỗng** ⇒ hiện hai dòng nhắc.
Hai dòng **không có trong cây view** (`uiautomator`) ⇒ đúng là chữ vẽ bằng Canvas [ĐO].

| Dòng | Hộp mực | Tâm ngang | Tâm ô | Δ |
|---|---|---|---|---|
| *Chưa có ảnh* | `[1335,787]–[1526,815]` | **1431,0** | **1431,0** | **0,0 px** |
| *Bỏ ảnh vào thư mục ảnh của Kachi* | `[1223,830]–[1638,856]` | **1431,0** | **1431,0** | **0,0 px** |
| Cả khối (787 → 856) | tâm dọc **821,5** | — | tâm ô dọc **820,0** | **+1,5 px** |

**Số thứ tự khung** trong màn *Vẽ bố cục* — `49-layout-editor.png` (4 khung, mở từ *Cài đặt › Màn hình chính ›
Vẽ bố cục riêng…*):

| Khung | Hộp khung | Tâm khung | Hộp mực số | Δ ngang | **Δ dọc** |
|---|---|---|---|---|---|
| 1 | `[302,155]–[957,521]` | (630,0 · 338,5) | `[607,297]–[638,380]` | −7,0 | **+0,5** |
| 2 | `[962,155]–[1617,521]` | (1290,0 · 338,5) | `[1263,296]–[1318,380]` | +1,0 | **0,0** |
| 3 | `[302,527]–[957,893]` | (630,0 · 710,5) | `[603,668]–[654,753]` | −1,0 | **+0,5** |
| 4 | `[962,527]–[1617,893]` | (1290,0 · 710,5) | `[1260,668]–[1319,751]` | +0,0 | **−0,5** |

⇒ Trục **DỌC** (thứ bản vá E1 sửa) lệch **≤ 0,5 px** ở cả bốn khung ✅. Δ ngang −7,0 px của khung 1 là **hình
dạng glyph `1`** (mực nằm lệch trong bề rộng bước của chữ số `1`), không phải bố cục — ba khung còn lại ≤ 1,0 px.

### 6b. Kiểm 5 — camera GL tổng hợp — `50-camera-gl-left.png`

Chế độ kiểm thử BẬT đúng đường `enable_test_mode`; `prefs_set camera_render=GL` → `camera_synth on` →
`camera --es name left`. Lớp phủ *Left camera* lên đúng ô 0.

| Đại lượng | Đo | Ngưỡng | Verdict |
|---|---|---|---|
| Vòng tròn chuẩn (cyan) | `[202,208]–[451,458]` = **250 × 251** ⇒ w/h = **0,9960** | tròn ⇒ không méo | ✅ |
| Vạch ngang chuẩn (cam), 51 cột đo suốt bề ngang khung | y từ **375,0** đến **377,0** ⇒ **cong ≤ 2 px** trên ~500 px | thẳng | ✅ |

⇒ **Lưới thẳng** (đường nắn hình đang ở trạng thái đồng nhất). Vẻ "gợn" của các vạch lưới trong ảnh là **nét vẽ
của chính khung tổng hợp**, không phải méo hình — hai số trên chứng minh điều đó.
Đã trả: `camera_synth off` · `camera none` · `camera_render TV` (TV = **mặc định**, xem KDoc `PrefsCameraDewarp`).

### 7b. Danh sách ảnh (trong scratchpad của phiên, thư mục `visual-274b/`)

| Tên | Nội dung |
|---|---|
| `40-initial.png` | Màn chính lúc bắt đầu (hồ sơ `Mặc định`) — mốc so sánh |
| `41-cfgA-home.png` | Hồ sơ `Test` · `THREE` = vòng Năng lượng + vòng Không khí + thẻ nhóm Năng lượng; thanh nút `fan,temp,fuel_pct` |
| `42-fan-1.png` | Ô gió ở mức **1** (sau 3 lần `−`) |
| `43-fan-auto.png` | Ô gió hiện **AUTO** (lần `−` thứ 4) |
| `44-fan-plus.png` | Sau `+` — rời AUTO (hiện `2`) |
| `45-fan-3.png` | Sau `+` lần nữa (hiện `3`) — ảnh dùng để đo hai vòng |
| `46-cfgB-home.png` | `QUAD` = Tốc độ · Cửa & khoang · Trạng thái xe · Trình chiếu ảnh |
| `49-layout-editor.png` | Màn *Vẽ bố cục* với 4 khung đánh số |
| `50-camera-gl-left.png` | Lớp phủ camera GL tổng hợp (tín hiệu trái) |
| `51-restored-home.png` | Màn chính sau khi trả nguyên trạng |
| `52-header-before-after.png` | Dải chip trên: trước (trên) / sau (dưới) — xem §9b |

### 8b. Trạng thái máy ảo sau lượt kiểm

| Việc | Trạng thái |
|---|---|
| Hồ sơ | **`Mặc định`** (a11y đọc: *"Hồ sơ đang dùng: Mặc định"*) ✅ |
| Bố cục / nội dung ô | **`TWO_ROW` · Lốp + YouTube** — đúng như lúc bắt đầu ✅ |
| Tệp `kachi_workspace.xml` | ghi lại **nguyên văn bản sao lưu**; so lại trên máy: **47/48 khoá giống hệt** (khoá thứ 48 xem §9b) |
| Chế độ kiểm thử | **TẮT** — `am broadcast … --es cmd state` trả `{"ok":false,"error":"test_mode_off"}` ✅ |
| `kachi_test_bridge.xml` | **đã xoá** khỏi `shared_prefs/` ✅ |
| Khoá camera | `camera_render` = **TV** (mặc định); `clusternav_prefs.xml` vẫn **2175 byte** như trước lượt ⇒ không thêm/bớt khoá nào ✅ |
| `adb root` | **đã `unroot`** (`id` ⇒ `uid=2000(shell)`) ✅ |
| Tiền cảnh | `mResumedActivity: com.byd.launcher/…launcher.KachiHome` ✅ |
| Cài / gỡ / commit | **không** cài gì, **không** commit gì ✅ |

### 9b. Một thay đổi KHÔNG do lượt kiểm gây ra, nhưng do lượt kiểm **kích hoạt sớm**

So ảnh `51-restored-home.png` với `40-initial.png`: khác **3 846 px (0,19 %)**, toàn bộ nằm trong dải `y 45..65`
= **đồng hồ + hàng chip**. Thân màn (bảng Lốp, ô YouTube) **giống từng pixel** [ĐO].

Nguyên nhân [ĐO]: lần khởi động nguội (force-stop + mở lại) làm `TopStripConfig.decode` chạy **phép di trú UX5b**
(`TopStrip.kt:353 migrate()`) trên danh sách chip đã lưu của hồ sơ `Mặc định`, rồi **ghi lại bản đã gộp**:

```
CŨ  (14) chip_pm25, chip_outside_temp, ac_cycle, motor_power, defrost_front_state, seat_heat_state,
         door_rr, chip_seat, trip_hours, seat_vent_state, ac_wind, ac_on, pm25_online, ac_wind_auto
MỚI (11) chip_pm25, chip_outside_temp, ac_cycle, motor_power, defrost_front_state, chip_seat,
         door_rr, trip_hours, ac_wind, ac_on, ac_wind_auto
```

- `seat_heat_state` + `seat_vent_state` → gộp thành **một** `chip_seat` tại đúng chỗ cái đầu tiên: **đúng thiết kế
  UX5b** (KDoc `migrate`), luỹ đẳng.
- `pm25_online` bị `isChippable` loại (datum kiểu `BADGE`).

⇒ Đây là hành vi **của chính bản 2.74**, sẽ nổ ở **lần khởi động launcher kế tiếp bất kỳ** (reboot, nâng cấp,
force-stop) chứ không phải do phép ghi prefs của lượt kiểm — và **không đảo lại được**: ghi chuỗi 14 mã trở lại
thì lần khởi động sau lại gộp y như vậy. Ghi ở đây để owner biết vì sao dải chip của `Mặc định` từ 14 xuống 11.
Ảnh: `52-header-before-after.png`.

**Ghi chú thị giác (không phải lỗi 2.74)**: mỗi lần khởi động nguội trên máy ảo, hệ thống hiện toast
*"Kênh điều khiển cửa sổ: app không vào được ô — đây là tính năng lõi của launcher"* ở `x 543..1377 · y 967..1046`
— tức **đè đúng chỗ thanh nút xe**. Máy ảo không có kênh shell nên toast là đúng; chỉ cần biết để không đọc nhầm
ảnh chụp (các phép đo thanh nút ở §2b đều lấy từ `uiautomator`, không lấy từ vùng bị toast che).

### 10b. Việc cần làm (kèm chủ)

| # | Mức | Việc | Tệp | Chủ đề xuất |
|---|---|---|---|---|
| 1 | **[P2]** | **Ô gió: cỡ chữ số KHÔNG trở lại sau khi rời `AUTO`.** [ĐO] cùng ô giá trị `[818,…]–[865,…]`: `1` trước AUTO có mực cao **15 px** (view 47×29); sau khi hiện `AUTO` rồi bấm `+`, `2` và `3` chỉ còn mực cao **10 px** (view 47×**19**) — tức chữ **kẹt ở sàn `AUTO_FIT_MIN_SP = 9`**, nhỏ hơn ô nhiệt bên cạnh (`22°` mực cao 17 px) và không tự lớn lại. [SUY] nguyên nhân: `setAutoSizeTextTypeUniformWithConfiguration` áp lên `TextView` có **chiều cao `WRAP_CONTENT`** (`LayoutParams(0, WRAP, VALUE_WEIGHT)`) — ca mà tài liệu Android nói autosize "có thể không đo lại đúng". Gợi ý: cho ô giá trị chiều cao cố định (hoặc `MATCH_PARENT`) trong hàng ba cột, hoặc đặt lại `textSize` tường minh khi chuỗi mới không cần co. ⇒ **ĐÃ VÁ 2026-09-27 — xem §11b** (gợi ý thứ hai KHÔNG dùng được: `setTextSize` là no-op khi autosize bật; còn nợ một lượt đo lại hành vi) | `app/…/launcher/ControlTileFactory.kt` §`tileStep` (khai autosize ở dòng ~144, hàng ba cột ở ~178-184) | làn **UX4** |
| 2 | [P3] | Ghi lại trong tài liệu UX7 rằng phép kiểm vòng ở máy ảo chỉ chốt được qua **dựng lại baseline** (dòng số là `—`); muốn số thuần-[ĐO] thì cần **chữ số thật** ⇒ một lượt trên xe, hoặc một đường bơm datum giả qua cầu kiểm thử (cùng món nợ #2 của lượt 1) | `docs/diagnostics/offcar-2026-09-26/ux-ux7-composite-widgets.md` §5 | làn UX7 |
| 3 | [P3] | Owner biết: dải chip của hồ sơ `Mặc định` đã tự rút từ 14 xuống 11 mã theo phép di trú UX5b (§9b). Nếu owner muốn giữ `pm25_online` trên thanh thì phải mở lại `isChippable` cho datum kiểu `BADGE` — là một quyết định thiết kế, không phải lỗi | `TopStrip.kt` | **owner** |
| 4 | — | Mọi mục 🚗 của lượt 1 (ghế sưởi/mát thật, chữ số thật trong thẻ lốp) **vẫn còn nguyên** — máy ảo không có HAL | — | buổi xe |

---

### 11b. [2026-09-27, sau lượt 2] ĐÃ VÁ — ô gió: cỡ chữ số nở lại được sau khi rời `AUTO` (khoản #1 của §10b)

**Nguyên nhân [ĐO]** — không phải "autosize đo không đúng" như [SUY] ban đầu, mà là một **vòng phụ thuộc chiều cao**:
`autoSizeText` chọn cỡ lớn nhất còn vừa **khoảng trống ĐO ĐƯỢC** (rộng × cao), mà ô giá trị nằm trong hàng stepper với
`LayoutParams(0, WRAP_CONTENT, VALUE_WEIGHT)` ⇒ **chiều cao đo được đi theo cỡ chữ đang vẽ**: lượt vẽ ở sàn 9sp cho ô
cao **19 px**, và 19 px thì không còn chứa nổi cỡ gốc ⇒ **khoá cứng ở sàn**, vĩnh viễn. Đúng cảnh báo của tài liệu
Android (*"do not set layout_width or layout_height to wrap_content"* khi bật autosize), và chỉ lộ ra ở nút có
`ControlDef.autoId` vì chỉ nút ấy mới đổi độ dài chữ (`AUTO` ⇄ `3`).

Không chữa được bằng cách đặt lại cỡ chữ mỗi lượt đổi text: [ĐO] AOSP `android-10.0.0_r47` `widget/TextView.java` —
`setTextSize(unit, size)` mở đầu bằng `if (!isAutoSizeEnabled())` ⇒ **no-op** khi autosize đang bật. Phải chặn ở
**khoảng trống**, không ở cỡ chữ.

**Bản vá** — **ghim chiều cao** ô giá trị bằng đúng chỗ cho **MỘT dòng ở cỡ TO NHẤT**, đo bằng chính `paint` của view
(nó đang mang cỡ gốc lúc hàm chạy) ⇒ con số tự đúng ở cả ba vùng `TileSize` mà không gõ một hằng dp nào. Chỉ nút có
`ControlDef.autoId` đi qua đường này ⇒ ô **nhiệt độ / âm lượng không đổi một pixel nào**.

| Việc | Tệp |
|---|---|
| Lớp mới `StepValueFit` (tách khỏi `ControlTileFactory` — tệp kia đã 499/500 dòng, CLAUDE.md §4.1) | `app/src/main/java/com/byd/clusternav/launcher/StepValueFit.kt` |
| Bài ghim hợp đồng | `ControlStateUxContractTest.o gia tri co chu roi phai no lai duoc - chieu cao ghim` |

**CÒN NỢ — kiểm lại HÀNH VI trên máy ảo (🚗/QA, CHƯA làm)**: bài trên là **hợp đồng** (quét source + hình học), nó
**không** thay được phép đo ảnh. Phải chạy lại đúng chuỗi §2b (`−` ×3 · `−` · `+` · `+`) và chốt hai số:

1. Sau `AUTO → tay`, **mực chữ số phải đo LẠI BẰNG trước khi vào AUTO** (15 px ở cấu hình §2b, hộp view trở lại
   **47×29**) — không còn 10 px / 47×19.
2. Hai nút **−/+ của ô NHIỆT** vẫn lệch đúng **0 px** qua cả 5 trạng thái (điều §2b đã đạt — bản vá không được làm hỏng).

Tới khi có hai số đó, trạng thái đúng của khoản này là **"đã vá, chưa xác nhận bằng ảnh"**, không phải "xong".

---

## Lượt 3 — 2026-09-27 (2.74 (175))

**Máy**: AVD `clusternav10`, `emulator-5554` · 1920×1080 @240dpi = **1,5 px/dp**.
**Bản trên máy** đọc từ `dumpsys package com.byd.launcher` ⇒ `versionName=2.74 versionCode=175` [ĐO] — lượt này
số hiệu **đã bump** (lượt 1/2 chạy cây 2.74 mang số cũ 2.73/174), nên mọi so sánh dưới đây là *bản sau lượt soát
Opus* (có `StepValueFit`, mốc di trú UX5b, lùi GL) **so với** số đo lượt 2.

**Mục đích**: đóng hai món nợ mở — khoản §11b (*"đã vá, chưa xác nhận bằng ảnh"* của ô gió `AUTO`) và hai ảnh đối
chứng #1/#2 của `ux-ux7-composite-widgets.md` §5 — cộng một phép thử **mốc di trú chip** (vá [P1] của lượt soát).

**Cách dựng bố cục**: y hệt lượt 2 (CLAUDE.md §15 — ghi/đọc state bền trước, UI là phương án cuối): ghi thẳng
`shared_prefs/kachi_workspace.xml` (`am force-stop` → đẩy tệp → `am start`), **chỉ thêm khoá `Test__*` +
`active_profile`**. Tệp gốc sao lưu nguyên văn trước (md5 `114b8618…`, 6228 byte) và trả lại nguyên văn sau (§7c).
**Chế độ kiểm thử KHÔNG bật lần nào** trong cả lượt.
**Ngân sách chạm UI**: đúng **6 cú tap** (3× `−`, 1× `−`, 2× `+`) — không mở màn nào, không cuộn. Tổng thời gian có
lệnh chạm máy ≈ **2 phút**; máy ở trạng thái đã sửa từ 02:58:55 đến 03:05:55.

Đo: `uiautomator dump` cho **hộp view px**, `exec-out screencap` + PIL cho **hộp mực**. Không kết luận bằng mắt.

### 1c. Tổng kết lượt 3

| # | Kiểm | Kết quả |
|---|---|---|
| 1 | UX4 — ô gió `AUTO` đi-về (bản vá [P2] ở §11b) | **PASS 2/3** — cỡ chữ **nở lại đúng** (15 px → 15 px) · hộp ô **47×31 bất biến** qua cả 5 trạng thái (không còn 47×19) · nút −/+ ô nhiệt lệch **0 px** · nhưng **FAIL MỚI**: chữ `AUTO` nay **xuống 2 dòng** `AUT`/`O`, lề dưới **0 px** (§2c) |
| 2 | UX7 ảnh #1 — **Bảng tổng hợp** ở `THREE` ô 0 (đối chứng) | **PASS** — chữ ôm tâm thẻ con: ngang **−0,5 px**, dọc **−0,5 px** |
| 3 | UX7 ảnh #2 — **Lốp** ở `ONE` (đối chứng) | **PASS** — thẻ dừng cách hộp mực xe **37 / 40 px**, gương nhô 19 px mỗi bên vẫn **hiện đủ** (khe 43 px tại đúng hàng gương) |
| 4 | 5 chip mặc định trên hồ sơ `Test` | **PASS** — đúng 5 chip, hộp trùng **từng px** với bảng §3.1 của lượt 1 |
| 5 | Mốc di trú UX5b (bản vá [P1]) | **PASS** + **đối chứng âm chạy được**: có mốc ⇒ gỡ chip ghế là **mất hẳn**; bỏ mốc ⇒ hai chip ghế **mọc lại** đúng như trước bản vá |

### 2c. Kiểm 1 — UX4 · ô gió `AUTO` đi-về (đóng món nợ §11b)

Bố cục: hồ sơ `Test`, preset `THREE` ô 0 = *Bảng tổng hợp*, thanh nút **BOTTOM** = `fan,temp,fuel_pct` — **cùng hình
học với lượt 2** (mọi hộp ngoài của hai ô stepper trùng từng px với bảng §2b). Chuỗi thao tác y lượt 2:
`−` ×3 (4→1) · `−` (1→**AUTO**) · `+` (rời AUTO) · `+`.

#### 2c.1 Hộp view qua 5 trạng thái — [ĐO] `uiautomator`

| View | mức 4 (`60`) | mức 1 (`61`) | **AUTO** (`62`) | sau `+` (`63`) | sau `+` (`64`) |
|---|---|---|---|---|---|
| Gió `−` | `[795,984]–[818,1024]` | y hệt | y hệt | y hệt | y hệt |
| Gió **ô giá trị** | `[818,988]–[865,1019]` **47×31** | **y hệt** | **y hệt** | **y hệt** | **y hệt** |
| Gió `+` | `[865,984]–[889,1024]` | y hệt | y hệt | y hệt | y hệt |
| **Nhiệt `−`** | `[913,984]–[936,1024]` | **y hệt** | **y hệt** | **y hệt** | **y hệt** |
| **Nhiệt ô giá trị** | `[936,988]–[983,1019]` **47×31** | **y hệt** | **y hệt** | **y hệt** | **y hệt** |
| **Nhiệt `+`** | `[983,984]–[1007,1024]` | **y hệt** | **y hệt** | **y hệt** | **y hệt** |

⇒ **Điều kiện 2 của §11b ĐẠT**: hai nút −/+ của ô nhiệt lệch **đúng 0 px** qua cả 5 trạng thái; bản vá không đụng
một pixel nào của ô không có `autoId` (đúng như `StepValueFit.apply` hứa — `stepValueChars(temp)` = 3 = sàn chung
`STEP_VALUE_CHARS` ⇒ thoát ngay).

⇒ **Ô giá trị KHÔNG còn sập xuống `47×19`** — con số đã giết lượt 2 nay biến mất hoàn toàn.

*Ghi chú số 29 → 31*: lượt 2 đo ô gió `47×29`, nay `47×31`. **Không phải hồi quy**: 31 px = `fm.bottom − fm.top`
của đúng cỡ chữ gốc, và ô **nhiệt** (không đi qua phép ghim, chiều cao `WRAP_CONTENT` tự nhiên) cũng đo được
`47×31`. Tức lượt 2 ô gió đang **thấp hơn ô nhiệt 2 px** vì autosize đã co sẵn; nay hai ô giá trị **cùng một hộp**.

#### 2c.2 Cỡ chữ đi-về — [ĐO] hộp mực trong ô `[818,988]–[865,1019]`

| Trạng thái | Chữ | Hộp mực | **Cao mực** | Ảnh |
|---|---|---|---|---|
| Trước AUTO (mức 4) | `4` | `[835,997]–[847,1012]` | **15 px** | `60-cfgA-home.png` |
| Mức 1 | `1` | `[836,997]–[844,1012]` | **15 px** | `61-fan-1.png` |
| **AUTO** | `AUTO` | `[828,993]–[855,1019]` | 26 px (**2 dòng** — §2c.3) | `62-fan-auto.png` |
| **Sau `+`** (rời AUTO) | `2` | `[835,997]–[847,1012]` | **15 px** | `63-fan-manual.png` |
| Sau `+` lần nữa | `3` | `[835,997]–[846,1013]` | **16 px** (vòm chữ `3` trồi 1 px) | `64-fan-manual2.png` |
| Đối chứng cùng ảnh: ô **nhiệt** `22°` | — | `[942,995]–[976,1012]` | 17 px (gồm vòng `°` cao hơn 2 px) | mọi ảnh |

⇒ **Điều kiện 1 của §11b ĐẠT**: sau vòng `AUTO → tay`, mực chữ số **trở lại đúng 15 px** = số trước khi vào AUTO
(lệch **0 px**; mức 3 lệch **+1 px** do hình glyph, trong ngưỡng ±1 px). Con số đáy `10 px / 47×19` của lượt 2
**không tái hiện**. Đường baseline của cả ba chữ số đều ở y 1011–1012, **cùng baseline với ô nhiệt** ⇒ chữ số gió
nay **cùng cỡ chữ với ô nhiệt**, không còn nhỏ hơn.

#### 2c.3 ⚠ FAIL MỚI — chữ `AUTO` xuống 2 dòng (`62-fan-auto.png`, `z62-fan-auto.png`)

| Đại lượng | Lượt 2 (trước vá) | **Lượt 3 (sau vá)** |
|---|---|---|
| Hộp ô giá trị | `[818,989]–[865,1018]` 47×29 | `[818,988]–[865,1019]` 47×31 |
| Hộp **mực** `AUTO` | `[828,999]–[854,1008]` **27×10**, **1 dòng** | `[828,993]–[855,1019]` **27×26**, **2 dòng** |
| Số dòng mực đếm được | 1 khối y 999..1008 | **2 khối**: `AUT` y **993..1002** · `O` y **1009..1018** |
| Lề trái / phải / trên / **dưới** | 10 / 10 / 10 / **9** px | 10 / 10 / 5 / **0 px** |
| Tâm mực − tâm ô (dọc) | +0,5 px | **+2,5 px** (khối chữ tụt xuống) |

Chữ **không bị cắt** (cả hai dòng vẽ đủ nét, dòng dưới cao đúng 10 px như dòng trên) nhưng **đọc ra là `AUT` /
`O`**, và mép dưới chữ **chạm đúng hàng pixel cuối** của ô. Đây là hồi quy **mới**, do chính bản vá §11b sinh ra.

**Nguyên nhân — [ĐO], AOSP `android-10.0.0_r47` `core/java/android/widget/TextView.java`**: `maxLines` và
`height` **dùng CHUNG một cặp trường**, nên cái sau xoá cái trước.

| file:line | Mã | Hệ quả |
|---|---|---|
| `TextView.java:5336-5342` | `setMaxLines(n)` ⇒ `mMaximum = n; mMaxMode = LINES;` | `ControlTileFactory.kt:135` đặt `maxLines = 1` |
| `TextView.java:5438-5444` | `setHeight(px)` ⇒ `mMaximum = mMinimum = px; mMaxMode = mMinMode = PIXELS;` | `StepValueFit.kt:50` `v.height = fm.bottom - fm.top` **ghi đè** `mMaximum`/`mMaxMode` ⇒ `maxLines = 1` **mất** |
| `TextView.java:5357-5359` | `getMaxLines()` ⇒ `mMaxMode == LINES ? mMaximum : -1` | sau phép ghim trả **−1** |
| `TextView.java:9505` | `final int maxLines = getMaxLines();` | = −1 |
| `TextView.java:9524` | `.setMaxLines(mMaxMode == LINES ? mMaximum : Integer.MAX_VALUE)` | layout thử được dựng **không giới hạn dòng** |
| `TextView.java:9530` | `if (maxLines != -1 && layout.getLineCount() > maxLines) return false;` | **bị bỏ qua** (maxLines = −1) |
| `TextView.java:9535` | `if (layout.getHeight() > availableSpace.bottom) return false;` | 2 dòng ở cỡ vừa lọt 31 px ⇒ **được nhận** |

⇒ autosize chọn **cỡ to hơn kèm xuống dòng** thay vì cỡ nhỏ hơn giữ một dòng, vì điều kiện "một dòng" đã bị chính
phép ghim chiều cao xoá mất. Bài canh `ControlStateUxContractTest.o gia tri co chu roi phai no lai duoc - chieu cao
ghim` **không bắt được** vì nó quét **mã nguồn** (có `v.height =`, có autosize, đúng thứ tự) chứ không đo hành vi —
đúng cảnh báo tự nó ghi ở §11b (*"bài trên là hợp đồng, nó không thay được phép đo ảnh"*).

**Hướng sửa (cho làn UX4, chưa làm ở lượt này)**: ghim chiều cao **ngoài** `TextView` — truyền số px đã tính vào
`LayoutParams` ở chỗ `addView` (`ControlTileFactory.kt:180`, `LinearLayout.LayoutParams(0, <px>, VALUE_WEIGHT)`)
thay cho `v.height = …`. Khoảng trống đo được vẫn bị ghim y như vậy, nhưng `mMaxMode` giữ nguyên `LINES` ⇒
`maxLines = 1` còn hiệu lực ⇒ autosize buộc phải **co chữ** để vừa một dòng. Mọi hàm `setHeight`/`setMaxHeight`/
`setMinHeight` của `TextView` đều ghi vào cùng cặp trường (`:5378`, `:5299`) nên **không** đường nào trong số đó
dùng được.

### 3c. Kiểm 2 — UX7 ảnh #1 · **Bảng tổng hợp** ở `THREE` ô 0 (`60-cfgA-home.png`, `z60-board-cell2.png`)

Ô `[24,100]–[1153,904]`, 4 thẻ con; mỗi thẻ con có icon 30×30 + dòng giá trị + (nếu có) dòng nhãn — đều là
`TextView` trải **hết bề ngang thẻ** (canh giữa bằng `gravity`), nên phải đo **hộp mực**, không đo hộp view.

| Thẻ con | Hộp thẻ | Tâm thẻ (x · y) | Hộp mực dòng đo | Tâm mực | **Δ** |
|---|---|---|---|---|---|
| #2 *Áp suất lốp (bar)* — **dòng nhãn** | `[42,508]–[582,886]` | **312,0** · 697,0 | `[253,722]–[370,742]` | **311,5** | **−0,5 px** |
| #2 — **dòng giá trị** `—` | — | 312,0 | `[304,702]–[322,706]` | **313,0** | +1,0 px |
| #2 — **khối icon+giá trị+nhãn theo trục dọc** | — | · **697,0** | y **650 … 743** | **696,5** | **−0,5 px** |
| #1 *PM2.5* — dòng nhãn | `[594,118]–[1135,496]` | 864,5 · 307,0 | `[840,336]–[885,348]` | 862,5 | −2,0 px ⇒ xem ghi chú |
| #1 — khối dọc | — | · 307,0 | y 260 … 353 | 306,5 | −0,5 px |
| #0 / #3 (không có dòng nhãn) | `[42,118]–[582,496]` / `[594,508]–[1135,886]` | · 307,0 / 697,0 | icon+giá trị y 272…342 / 662…732 | 307,0 / 697,0 | **0,0 px** |

⇒ **Đối chứng ĐẠT**: làn UX7 tuyên bố *"4 ô con không đổi"* — khối chữ của cả bốn thẻ con ôm tâm thẻ với
|Δ| ≤ 0,5 px ở **cả hai trục**; không thẻ nào bị chồng chữ, cắt đuôi hay tràn mép.

**Ghi chú [P3] phát sinh**: Δ ngang −2,0 px của thẻ #1 **không phải lỗi bố cục** — `uiautomator` đọc chuỗi nhãn là
`'PM2.5 '` (**có một dấu cách ở cuối**), và dấu cách ấy nằm trong bề rộng bước của dòng nên mực lệch trái đúng
nửa dấu cách. Thẻ #2 cùng đường vẽ, nhãn không có dấu cách thừa, cho **−0,5 px**. Việc cần làm: cắt dấu cách ở
chỗ dựng nhãn đơn vị.

### 4c. Kiểm 3 — UX7 ảnh #2 · bảng **Lốp** ở `ONE` (`65-cfgB-one-tyres.png`, `z65-tyre-full.png`, `z65-mirrors.png`)

`TyreBoardView` = `[42,143]–[1878,886]` = **1836 × 743** — vẽ **hoàn toàn bằng Canvas** (`uiautomator` không thấy
`TextView` con nào bên trong) ⇒ mọi số dưới đây là **hộp mực đo trên ảnh**.

| Đại lượng | Đo (px) | Nhận xét |
|---|---|---|
| Thẻ hàng trên (`TT` · `TP`) | y **265 … 442** (cao 178) | hai thẻ cùng hàng, cùng chiều cao |
| Thẻ hàng dưới (`ST` · `SP`) | y **643 … 819** | — |
| Thẻ **trái** | x **79 … 767** (rộng 689) | mép trong = **768** |
| Thẻ **phải** | x **1152 … 1840** (rộng 689) | mép trong = **1152** |
| Đối xứng hai cột thẻ quanh tâm ô (x = 960) | 960 − 768 = **192** · 1152 − 960 = **192** | lệch **0,0 px** ✅ |
| **Hộp mực xe** | `[805,198]–[1112,846]` = **307 × 648** | tâm ngang **958,5** (lệch tâm ô **−1,5 px**) |
| **Khe mép trong thẻ ↔ mực xe** | trái **805 − 768 = 37 px** · phải **1152 − 1112 = 40 px** | **không chồng lấn** ✅ (lệch 37/40 đúng bằng lệch −1,5 px của hộp mực xe, không phải lệch của thẻ) |
| Hàng xe **rộng nhất** = hàng gương | y **404**, x **810 … 1109** = **300 px** | nằm **trong** dải thẻ hàng trên (y 265…442) |
| Thân xe (không gương) | y 500: x 825…1093 (**269**) · y 600: x 829…1090 (**262**) · y 700: x 826…1093 (**268**) | ⇒ gương nhô **≈ 19 px mỗi bên** |
| Khe **tại đúng hàng gương** | 810 − 767 = **43 px** trái · 1152 − 1109 = **43 px** phải | **gương hiện đủ**, không bị thẻ đè, không bị feather ăn ✅ |

⇒ **Đối chứng ĐẠT**: ở cỡ `ONE` (ô lớn nhất), bốn thẻ **kẹp đúng ra ngoài khung nội dung của ảnh xe** và **gương
vẫn thấy được** — không hồi quy so với trạng thái mẫu sau UX2 mà §5 của tài liệu UX7 mô tả.

*Món nợ #2 của §10b vẫn nguyên*: dòng số trong thẻ là `—` (máy ảo không có HAL) nên lượt này **không** chốt được
phép canh "số + dòng phụ cùng trục" bằng số thuần-[ĐO] — cần chữ số thật (một lượt trên xe, hoặc đường bơm datum
giả qua cầu kiểm thử).

### 5c. Kiểm 4 — 5 chip mặc định trên hồ sơ `Test` (`60-cfgA-home.png`)

Hồ sơ `Test` trên đĩa **không có khoá `top_strip`** ⇒ ăn `TopStripConfig.DEFAULT_IDS`.

| # | Chip | Hộp (px) | Rộng | Nhãn trợ năng |
|---|---|---|---|---|
| 1 | `PM2.5 · —` | `906–1030` | 124 | *Bụi mịn trong xe: —* |
| 2 | `—°C ngoài` | `1048–1138` | 90 | *Nhiệt độ ngoài xe —°C* |
| 3 | `—% · — km` | `1156–1287` | 131 | *Pin chưa đọc được phần trăm, đi thêm — km* |
| 4 | `Ghế lái · —` | `1305–1434` | 129 | *Mức ghế sưởi: — · Mức ghế mát: —* |
| 5 | `Ghế phụ · —` | `1452–1593` | 141 | *Mức ghế sưởi phụ: — · Mức ghế mát phụ: —* |

⇒ **đúng 5 chip, đúng thứ tự**, và **cả năm hộp trùng TỪNG PX** với bảng §3.1 của lượt 1 (11 khe = 18 px = 12dp,
không chip nào cắt đuôi) ⇒ bản 2.74 (175) không làm xê dịch dải chip mặc định ✅.

### 6c. Kiểm 5 — mốc di trú UX5b (bản vá **[P1]** của lượt soát)

Phép thử: ghi thẳng danh sách chip lên đĩa, **khởi động nguội**, rồi đọc lại **cả màn hình lẫn đĩa**.

| Cấu hình | `Test__top_strip` ghi lên đĩa | Mốc `top_strip_migrated_ux5b` | **Chip đếm được sau khởi động nguội** | `top_strip` trên đĩa SAU |
|---|---|---|---|---|
| **C** — bỏ `chip_seat_r` | `chip_pm25,chip_outside_temp,chip_energy,chip_seat` | **có** | **4** (PM2.5 · °C · % km · Ghế lái) | **y nguyên** |
| **D** — bỏ CẢ HAI chip ghế (= đúng mặc định **CŨ**) | `chip_pm25,chip_outside_temp,chip_energy` | **có** | **3** | **y nguyên** |
| **E** — y hệt D nhưng **xoá mốc** (đối chứng âm) | `chip_pm25,chip_outside_temp,chip_energy` | **KHÔNG** | **5** (mọc lại `Ghế lái` + `Ghế phụ`) | **bị ghi đè** thành `…,chip_seat,chip_seat_r` |

⇒ **PASS**: gỡ chip ghế trên hồ sơ có mốc là **mất hẳn** qua khởi động nguội — đúng thứ bản vá [P1] hứa.
⇒ **Đối chứng âm E chứng minh phép thử phân biệt được**: không có mốc thì đúng ca ấy hai chip ghế **mọc lại và
đĩa bị ghi đè** — tức hành vi trước bản vá, và cũng là vòng lặp mà KDoc `migrate` luật 2 mô tả.

*Vì sao phải dùng ca D/E chứ không chỉ ca C*: `migrate` luật 2 chỉ nổ khi danh sách **bằng tuyệt đối** mặc định cũ
3 mã. Ca C (còn `chip_seat`) rơi vào luật 3 (*"không đụng"*) nên **không phân biệt được** có mốc hay không — nó xác
nhận phần *"gỡ là mất hẳn"* mà owner hỏi, còn D/E mới là cặp chốt được chính bản vá.

### 7c. Trạng thái máy ảo sau lượt kiểm

| Việc | Trạng thái |
|---|---|
| `kachi_workspace.xml` | md5 **`114b8618c8eff450fcaf0010c28ffc0c`**, **6228 byte**, chủ `u0_a163` — **giống hệt bản sao lưu**, và **vẫn giống hệt SAU một lần khởi động nguội** ✅ |
| Hồ sơ | **`Mặc định`** (a11y: *"Hồ sơ đang dùng: Mặc định"*) ✅ |
| Bố cục | **`TWO_ROW` · Lốp `[24,100]–[1896,571]` + YouTube `[24,584]–[1896,882]`** — đúng như lúc bắt đầu ✅ |
| Dải chip `Mặc định` | **11 chip** — y như trước lượt (phép di trú §9b đã chạy xong từ lượt 2, mốc đã đóng) ✅ |
| Chế độ kiểm thử | **TẮT** — `am broadcast -a com.byd.launcher.TEST -p com.byd.launcher --es cmd state` ⇒ `{"ok":false,"cmd":"state","ms":1,"error":"test_mode_off"}` ✅ (không bật lần nào trong cả lượt) |
| `kachi_test_bridge.xml` | **không tồn tại** trong `shared_prefs/` ✅ |
| `clusternav_prefs.xml` | vẫn **2175 byte** như hai lượt trước ⇒ không thêm/bớt khoá nào ✅ |
| `adb root` | **đã `unroot`** (`id` ⇒ `uid=2000(shell)`) ✅ |
| Tiền cảnh | `mResumedActivity: com.byd.launcher/…launcher.KachiHome` ✅ |
| Cài / gỡ / commit | **không** cài gì, **không** commit gì ✅ |

**Ghi chú thao tác (lặp lại của lượt 2, nay có số)**: toast *"Kênh điều khiển cửa sổ: app không vào được ô…"* vẫn
hiện ở **mỗi lần khởi động nguội** và **đè đúng thanh nút xe**. Ảnh chụp ở t+6 s bị toast che (chữ toast lọt vào
hộp mực của ô giá trị); ảnh dùng để đo đã chụp lại ở **t+11 s** khi toast đã tắt. Lượt sau: chờ **≥ 10 s** sau
`am start` rồi mới chụp thanh nút.

### 8c. Danh sách ảnh (thư mục `visual-274c/` trong scratchpad của phiên)

| Tên | Nội dung |
|---|---|
| `60-cfgA-home.png` | Hồ sơ `Test` · `THREE` ô 0 = **Bảng tổng hợp** · thanh nút `fan,temp,fuel_pct`; ô gió mức **4** — ảnh dùng cho kiểm 1 (trước AUTO), kiểm 2 và kiểm 4 |
| `61-fan-1.png` | Ô gió mức **1** (sau 3 lần `−`) |
| `62-fan-auto.png` | Ô gió hiện **AUTO** — ảnh của FAIL §2c.3 |
| `63-fan-manual.png` | Sau `+` — rời AUTO (hiện `2`), cỡ chữ đã nở lại |
| `64-fan-manual2.png` | Sau `+` lần nữa (hiện `3`) |
| `65-cfgB-one-tyres.png` | Hồ sơ `Test` · `ONE` = **Lốp** — ảnh đối chứng UX7 #2 |
| `68-restored-home.png` | Màn chính sau khi trả nguyên trạng (hồ sơ `Mặc định`, `TWO_ROW`) |
| `z60-dock.png` | Phóng ×4 thanh nút của `60` (hai ô stepper) |
| `z60-board-cell2.png` | Thẻ con #2 của Bảng tổng hợp, cắt đúng hộp thẻ `[42,508]–[582,886]` |
| `z62-fan-auto.png` | Phóng ×8 ô gió lúc `AUTO` — thấy rõ hai dòng `AUT` / `O` |
| `z63-fan-manual.png` | Phóng ×8 ô gió ngay sau khi rời AUTO |
| `z65-tyre-full.png` | Toàn bộ vùng bảng Lốp ở `ONE` |
| `z65-mirrors.png` | Phóng ×2 vùng gương + mép trong hai thẻ trên |

### 9c. Việc cần làm sau lượt 3 (kèm chủ)

| # | Mức | Việc | Tệp | Chủ đề xuất |
|---|---|---|---|---|
| 1 | **[P2]** | **Chữ `AUTO` xuống 2 dòng** sau bản vá §11b — `v.height` (`TextView.setHeight`) xoá `maxLines = 1` vì hai thứ dùng chung `mMaximum`/`mMaxMode` (bảng file:line ở §2c.3). Sửa: ghim px qua **`LayoutParams` ở chỗ `addView`**, bỏ `v.height`. Kèm **bài đo hành vi**, không chỉ bài quét mã — bài hợp đồng hiện tại vẫn xanh trong khi màn hình đã sai | `StepValueFit.kt:50` · `ControlTileFactory.kt:135,180` · `ControlStateUxContractTest` | làn **UX4** |
| 2 | [P3] | Nhãn đơn vị của thẻ con Bảng tổng hợp có **dấu cách thừa** (`'PM2.5 '`) ⇒ mực lệch trái 2,0 px | chỗ dựng nhãn của `w_board` | làn UX7 |
| 3 | [P3] | Chờ **≥ 10 s** sau `am start` rồi mới chụp thanh nút (toast kênh cửa sổ che đúng vùng đo) — ghi vào quy trình chụp | quy trình QA | QA |
| 4 | — | Khoản §10b-1 (*"cỡ chữ không nở lại"*) ⇒ **ĐÓNG** — đã xác nhận bằng ảnh ở §2c.2 (15 px → 15 px, hộp 47×31 bất biến) | — | — |
| 5 | — | Món nợ 🚗 của lượt 1/2 (ghế sưởi-mát thật, chữ số thật trong thẻ lốp/vòng đo) **vẫn còn nguyên** — máy ảo không có HAL | — | buổi xe |

---

## Lượt 4 — 2026-09-27 (fix AUTO 2 dòng)

**Máy**: AVD `clusternav10`, `emulator-5554` · 1920×1080 @240dpi = **1,5 px/dp**.
**Bản trên máy**: `dumpsys package com.byd.launcher` ⇒ `versionName=2.74 versionCode=175` [ĐO] — cây làm việc sau bản
vá `[P2]` của §9c việc 1, cài lại bằng `adb install -r` (cùng chữ ký, **không mất data**).
**Mục đích**: đóng việc 1 của §9c — chữ `AUTO` xuống 2 dòng — và xác nhận bằng ảnh rằng bốn số của §2c vẫn nguyên.

**Cách dựng bố cục**: y hệt lượt 3 (CLAUDE.md §15 — ghi/đọc state bền trước, UI là phương án cuối): sao lưu nguyên
văn `shared_prefs/kachi_workspace.xml` (md5 `114b8618…`, 6228 byte) → `am force-stop` → ghi tệp qua **`run-as`**
(không `adb root` lần nào) → `am start` → chờ **13 s** (khoản [P3] việc 3 của §9c: toast kênh cửa sổ che đúng thanh
nút trong ~10 s đầu) → đo. **Chế độ kiểm thử KHÔNG bật lần nào.** Chỉ thêm khoá `Test__*` + `active_profile`, hồ sơ
`Test` · preset `THREE` ô 0 = *Bảng tổng hợp* · thanh nút `fan,temp,fuel_pct` — **cùng hình học với lượt 2/3**.

Đo: `uiautomator dump` cho **hộp view px**, `exec-out screencap` + PIL cho **hộp mực** (và **hồ sơ mực theo từng
hàng pixel** để ĐẾM SỐ DÒNG, thứ lượt 3 phải đếm bằng mắt).

### 1d. Tổng kết lượt 4

| # | Kiểm | Kết quả |
|---|---|---|
| 1 | Chữ `AUTO` về **MỘT dòng** | **PASS** — 1 khối mực liền y 998…1008, không còn hai khối `AUT`/`O` |
| 2 | Chữ `AUTO` đọc ra **đủ bốn chữ** | **PASS sau bản vá thứ hai** — trước đó (chỉ ghim `LayoutParams`) màn hình đọc ra **`AUT`**; xem §2d.3 |
| 3 | Hộp ô giá trị **47×31 bất biến** qua cả 5 trạng thái | **PASS** — từng px, cả 5 dump |
| 4 | Cỡ chữ SỐ sau `AUTO → tay` | **PASS** — mực **15 px**, y hệt trước khi vào AUTO và y hệt lượt 3 |
| 5 | Hai nút −/+ và ô giá trị của **ô NHIỆT** | **PASS — lệch 0 px** qua cả 5 trạng thái, và hộp mực `22°` **34×17 không đổi một pixel** |

### 2d. Kiểm 1–5 — ô gió `AUTO` đi-về

Chuỗi thao tác y lượt 3: `−` ×3 (4→1) · `−` (1→**AUTO**) · `+` (rời AUTO) · `+`. **6 cú tap**, không mở màn nào,
không cuộn; trước mỗi cú tap đọc lại `uiautomator` để lấy hộp nút (không tái dùng toạ độ cũ — CLAUDE.md §15).

#### 2d.1 Hộp view qua 5 trạng thái — [ĐO] `uiautomator`

| View | mức 4 (`d80`) | mức 1 (`d81`) | **AUTO** (`d82`) | sau `+` (`d83`) | sau `+` (`d84`) |
|---|---|---|---|---|---|
| Gió `−` | `[795,984]–[818,1024]` | y hệt | y hệt | y hệt | y hệt |
| Gió **ô giá trị** | `[818,988]–[865,1019]` **47×31** | **y hệt** | **y hệt** | **y hệt** | **y hệt** |
| Gió `+` | `[865,984]–[889,1024]` | y hệt | y hệt | y hệt | y hệt |
| **Nhiệt `−`** | `[913,984]–[936,1024]` | **y hệt** | **y hệt** | **y hệt** | **y hệt** |
| **Nhiệt ô giá trị** | `[936,988]–[983,1019]` **47×31** | **y hệt** | **y hệt** | **y hệt** | **y hệt** |
| **Nhiệt `+`** | `[983,984]–[1007,1024]` | **y hệt** | **y hệt** | **y hệt** | **y hệt** |

⇒ **trùng TỪNG PX với bảng §2c.1 của lượt 3** ⇒ bản vá lượt 4 **không xê dịch một pixel nào** của hình học thanh nút,
kể cả ô nhiệt (yêu cầu R2.4).

#### 2d.2 Hộp mực qua 5 trạng thái — [ĐO] PIL trong ô `[818,988]–[865,1019]`

| Trạng thái | Chữ | Hộp mực | **Cao mực** | Số **dòng** đếm được | Ảnh |
|---|---|---|---|---|---|
| Trước AUTO (mức 4) | `4` | `[835,997]–[847,1012]` | **15 px** | 1 | `80-fan-4.png` |
| Mức 1 | `1` | `[836,997]–[844,1012]` | **15 px** | 1 | `81-fan-1.png` |
| **AUTO** | `AUTO` | `[822,998]–[861,1009]` **39×11** | **11 px** | **1** (khối liền y 998…1008) | `82-fan-auto.png` |
| **Sau `+`** (rời AUTO) | `2` | `[835,997]–[847,1012]` | **15 px** | 1 | `83-fan-manual.png` |
| Sau `+` lần nữa | `3` | `[835,997]–[846,1013]` | **16 px** (vòm `3` trồi 1 px) | 1 | `84-fan-manual2.png` |
| Đối chứng: ô **nhiệt** `22°` | — | `[942,995]–[976,1012]` **34×17** | 17 px | 1 | **giống hệt ở cả 5 ảnh** |

**Lề của khối `AUTO` trong ô**: trái **4** · phải **4** · trên **10** · dưới **10** px. Tâm mực − tâm ô (dọc) =
**+0,5 px** (lượt 3 hỏng ở **+2,5 px**). Khe từ mực `AUTO` tới **mực** của nút `−` là **11 px**, tới mực `+` là
**10 px** ⇒ không dính nút.

⇒ cả bốn số của §2c.2 **giữ nguyên** (15 → 15 px, hộp 47×31), và số `AUTO` từ *2 dòng 27×26* về *1 dòng 39×11*.

#### 2d.3 ⚠ Lỗi ĐÃ CÓ TỪ TRƯỚC mà hai lượt QA trước không thấy: `AUTO` bị cắt còn `AUT`

Bản vá theo đúng lời giao (bỏ `v.height`, ghim px qua `LayoutParams` ở chỗ `addView`) **đã chữa đúng chứng 2 dòng** —
nhưng ảnh đo được là **`AUT`**, mất hẳn chữ `O`: mực `[828,999]–[855,1009]` **27×10**, 1 dòng (`72-fan-auto.png`).
Con số ấy **trùng từng px** với hộp mực mà lượt 2 ghi cho trạng thái AUTO (§1b: `[828,999]–[854,1008]` 27×10) ⇒ mở
lại ảnh gốc của lượt 2 (`visual-274b/43-fan-auto.png`, cây **trước mọi bản vá** 2.74) thì **cũng đúng `AUT`**
(`zL2-43-fan-auto-row.png`). Tức đây là lỗi **có từ UX4**, không phải do phép ghim chiều cao; hai lượt trước đo hộp
mực mà **không đếm chữ** nên nó lọt.

**Cơ chế — [ĐO] số đo trên máy + AOSP `android-10.0.0_r47`**:

| Bước | Số | Nguồn |
|---|---|---|
| Bề ngang ô giá trị | **47 px** (do `weight`, không do lề) | `uiautomator`, §2d.1 |
| Lề trong hai bên | `Sp.XS` = 4dp = **6 px mỗi bên** | `ControlTileFactory.tileStep` `setPadding(XS,0,XS,0)` |
| ⇒ chỗ cho chữ | 47 − 12 = **35 px** | `TextView.java:9442-9444` (`availableWidth = measuredWidth − totalPadding`) |
| `"AUTO"` ở **sàn** 9sp | ~**37 px** (suy từ `"AUT"` = 27 px đo được) | phép đo ảnh |
| ⇒ **không cỡ nào** vừa một dòng | `findLargestTextSizeWhichFits` rơi về phần tử 0 | `TextView.java:9483-9498` |
| ⇒ `StaticLayout` ngắt 2 dòng, view chỉ cao 1 dòng | dòng 2 **ngoài vùng vẽ** ⇒ mất chữ, **không** có `…` | không đặt `ellipsize` |

**Vá**: nhường **một nửa** lề trong hai bên cho chữ (`paddingLeft / 2` = 3 px) ⇒ 41 px cho chữ ⇒ `AUTO` đủ bốn chữ,
mực 39×11. Con số "một nửa" đọc từ chính lề mà chỗ gọi đã đặt, **không phải hằng mới** (đổi `Sp.XS` thì nó tự theo).
Đã thử **nhường trọn** lề (0 px): chữ nở tiếp tới mực **47×13** nhưng chạm đúng hai mép ô, sát nút ⇒ **không lấy**
(ảnh `p72-fan-auto.png` giữ lại làm đối chứng). Ô không đi qua phép co giữ **nguyên lề đầy đủ** ⇒ ô nhiệt 0 px đổi
(§2d.1 xác nhận).

Đây đúng bệnh mà KDoc `TileSize.narrow` đã ghi cho nút COVER (*"`Đóng`/`Close` bị cắt cứng thành `Đ`/`C`"*) và cùng
một cách chữa: **cho chữ trọn chỗ của ô**.

#### 2d.4 Vì sao bài canh cũ không đỏ, và nay canh thêm gì

Bài `ControlStateUxContractTest.o gia tri co chu roi phai no lai duoc - chieu cao ghim` quét **mã nguồn**, mà bản vá
lượt 3 có đủ `v.height =` + autosize + đúng thứ tự ⇒ xanh trong khi màn hình sai. Lượt này bài ấy được siết:

| Dây | Chặn |
|---|---|
| **CẤM** `.height =` · `setHeight(` · `minHeight` · `maxHeight` trong `StepValueFit`/`tileStep` | cả ba cửa ghi vào `mMaximum`/`mMaxMode` và **xoá** `maxLines = 1` (`TextView.java:5438` / `:5376` / `:5297`) |
| **BUỘC** `val valueH = StepValueFit.apply(vtext, …)` + `addView(vtext, LinearLayout.LayoutParams(0, valueH, VALUE_WEIGHT))` | ghim px **ngoài** view ⇒ `getChildMeasureSpec` đo con EXACTLY (`LinearLayout.java:1380-1383`) mà `mMaxMode` giữ `LINES` |
| **BUỘC** `val sidePad = v.paddingLeft / 2` + `setPadding(sidePad, …)`, và phải đứng **trước** phép đo `paint` | thiếu ⇒ `AUTO` quay lại `AUT` (§2d.3) |
| **BUỘC** nút không vượt sàn trả `WRAP_CONTENT` | ô nhiệt/âm lượng 0 pixel đổi |

Ba mutation đã thử, **cả ba đều làm bài đỏ**: (a) thêm lại `v.height = h`; (b) đổi `valueH` về `WRAP` ở `addView`;
(c) bỏ dòng `setPadding`. Bài vẫn là **hợp đồng** — nó không thay được phép đo ảnh, nhưng nay nó bắt đúng ba kiểu
hỏng đã xảy ra thật.

### 3d. Trạng thái máy ảo sau lượt kiểm

| Việc | Trạng thái |
|---|---|
| `kachi_workspace.xml` | md5 **`114b8618c8eff450fcaf0010c28ffc0c`**, **6228 byte** — **giống hệt bản sao lưu**, và **vẫn giống hệt SAU một lần khởi động nguội** ✅ |
| Hồ sơ | **`Mặc định`** (a11y: *"Hồ sơ đang dùng: Mặc định"*) ✅ |
| Bố cục | **Lốp `[24,100]–[1896,571]` + YouTube** — đúng như lúc bắt đầu ✅ |
| Chế độ kiểm thử | **TẮT** — `--es cmd state` ⇒ `{"ok":false,…,"error":"test_mode_off"}` ✅ (không bật lần nào) |
| `kachi_test_bridge.xml` | **không tồn tại** trong `shared_prefs/` ✅ |
| `clusternav_prefs.xml` | vẫn **2175 byte** ⇒ không thêm/bớt khoá nào ✅ |
| `adb root` | **không dùng lần nào** (`id` ⇒ `uid=2000(shell)`; ghi tệp qua `run-as`) ✅ |
| Tiền cảnh | `mResumedActivity: com.byd.launcher/…launcher.KachiHome` ✅ |
| APK trên máy | **bản đã vá** (`vehicleTest` 2.74 (175), cài `-r` cùng chữ ký) — cố ý giữ lại ✅ |
| Commit | **không** commit gì ✅ |

### 4d. Danh sách ảnh (thư mục `visual-274d/` trong scratchpad của phiên)

| Tên | Nội dung |
|---|---|
| `80-fan-4.png` … `84-fan-manual2.png` | **bản CUỐI** — 5 trạng thái `4 · 1 · AUTO · 2 · 3` (ảnh dùng cho §2d.1/§2d.2) |
| `85-fan-auto-final.png` | chụp lại `AUTO` trên **đúng APK cuối** sau khi build lại — số trùng khít `82-fan-auto.png` |
| `z82-fan-auto.png` · `z82-fan-row.png` | phóng ×8 ô `AUTO` và cả hàng `− AUTO +` của bản cuối |
| `z83-fan-manual.png` · `z80-dock.png` | phóng ×8 ô ngay sau khi rời AUTO · phóng ×4 thanh nút |
| `70-cfgA-home.png` … `74-fan-manual2.png` | bản **giữa** (chỉ ghim `LayoutParams`, chưa nhường lề) — bằng chứng của `AUT` |
| `72-fan-auto.png` · `z72-fan-row.png` | ảnh `AUT` 1 dòng của bản giữa |
| `p72-fan-auto.png` · `zp72-fan-row.png` | đối chứng **nhường TRỌN lề** — `AUTO` mực 47×13 chạm hai mép ô ⇒ không lấy |
| `zq72-fan-row.png` | hàng `− AUTO +` của bản cuối (nhường nửa lề) |
| `zL2-43-fan-auto-row.png` | cắt từ ảnh **lượt 2** `visual-274b/43-fan-auto.png` — chứng minh `AUT` có từ trước 2.74 |
| `z-auto-4way.png` | bốn ô cạnh nhau: lượt 2 `AUT` · lượt 3 `AUT`/`O` · lượt 4 giữa `AUT` · **lượt 4 cuối `AUTO`** |
| `88-restored-home.png` | màn chính sau khi trả nguyên trạng |

### 5d. Việc cần làm sau lượt 4

| # | Mức | Việc | Tệp | Chủ đề xuất |
|---|---|---|---|---|
| 1 | — | §9c việc 1 (`AUTO` 2 dòng) ⇒ **ĐÓNG** — 1 dòng, đủ bốn chữ, 5 trạng thái hộp bất biến (§2d.1/§2d.2) | — | — |
| 2 | **[P2]** | **`apk/Kachi-2.74-release.apk` đang staged là bản DỰNG TRƯỚC bản vá này** ⇒ phải dựng lại trước khi commit/đăng, nếu không kênh OTA nhận đúng bản `AUT`/2 dòng | `apk/` | người phát hành |
| 3 | [P3] | `AUTO` ở sàn 9sp cần ~37 px mà ô chỉ có 47 px ⇒ dải an toàn còn rất mỏng: **thêm một chữ nữa** (vd nhãn dài hơn, hoặc `TileSize` hẹp hơn) là cắt tiếp. Cách chắc chắn: đo bề rộng chữ ở tầng vẽ rồi mới chọn lề/cỡ, thay vì trông vào autosize | `StepValueFit` | làn UX4 |
| 4 | [P3] | Mọi lượt QA sau: với chữ (không phải số) phải **đếm ký tự trên ảnh**, không chỉ đo hộp mực — đúng chỗ lượt 2/3 lọt lỗi `AUT` | quy trình QA | QA |
| 5 | — | Việc 2/3/5 của §9c (dấu cách `'PM2.5 '`, chờ ≥10 s, món nợ 🚗) **vẫn nguyên** | — | — |
