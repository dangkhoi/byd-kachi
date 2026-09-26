# UX7 · mọi widget TỔNG HỢP theo cùng một luật với widget lốp

Spec: `docs/specs/kachi-274-ux-voice-camera.html` §3 · làn UX7 · off-car 2026-09-27 · nối tiếp
`docs/diagnostics/offcar-2026-09-26/ux-ux2-tyre.md` (làn UX2 — chỉ sửa bảng lốp).

> Owner 2026-09-27: *"ngoài widget lốp đã sửa, có sửa cùng các widget tổng hợp khác không? cho nó giống nhau, hết
> lỗi chứ?"*

UX2 sửa **ba lớp lỗi** nhưng chỉ trong `TyreBoardView`:
**(1)** khối 2 dòng canh giữa bằng **hằng ma thuật** thay vì số đo phông · **(2)** cụm `số + đơn vị` canh giữa
**cả cụm** nên con số lệch trục so với nhãn của chính ô · **(3)** thẻ ĐỤC chạy tới neo bánh nên đè lên thân xe.
Làn này đi soát **từng** ô vẽ/ô dựng còn lại của launcher bằng đúng ba câu hỏi đó, sửa chỗ nào dính, và **nói rõ
chỗ nào không dính** (kèm lý do) để lần sau khỏi soát lại.

Mức bằng chứng theo CLAUDE.md §2: công thức/hằng/thứ tự vẽ = **[ĐO]** (đọc source, tính lại được off-car);
số px quy ra từ mô hình advance của phông = **[SUY]** (chưa đo trên xe thật, phông do ROM quyết).

---

## 1. Kiểm kê — 14 bề mặt vẽ chữ của launcher

Phép quét: `grep onDraw( · drawText( · drawRoundRect( · Paint(` trong `app/src/main/java/com/byd/clusternav/launcher/`
cộng với `WidgetRegistry.ALL` (9 widget dựng tay) và ba bộ vẽ ô nhóm (`GroupTileViews`).

| # | Bề mặt (widget/ô) | Đặt chữ bằng | (1) khối lệch | (2) số lệch trục | (3) thẻ đè ảnh | Bằng chứng (bản 2.73) | Kết luận |
|---|---|---|---|---|---|---|---|
| 1 | **Áp suất lốp** `TyreBoardView` | Canvas | đã sửa | đã sửa | đã sửa | UX2 | **mẫu** — không đụng |
| 2 | **Năng lượng** + **Không khí** + mọi datum `RING` → `RingView` | Canvas | **CÓ** | không (2 dòng cùng `Align.CENTER` trên `cx`) | — (không có ảnh) | `RingView.kt:51-52` `cy + bigP.textSize*0.36f` · `cy + bigP.textSize*0.9f + smallP.textSize` | **SỬA** (1) + thêm co-chữ |
| 3 | **Trình chiếu ảnh** `PhotoWidgetView` (lời nhắc "chưa có ảnh") | Canvas | **CÓ** (về hình thức; độ lớn ~1 px) | không | — | `PhotoWidgetView.kt:157,159` `h/2 − textSize*0.4f` · `h/2 + textSize*1.4f` | **SỬA** (1) + co-chữ |
| 4 | **Trình vẽ bố cục** `GridEditorView` (số thứ tự khung) | Canvas | **CÓ** (1 dòng) | — | — | `GridEditorView.kt:184` `box.centerY() + label.textSize*0.35f` | **SỬA** (1) — *không phải widget, nhưng cùng lỗi* |
| 5 | **Cửa & khoang** `DoorBoardView` | Canvas (chỉ **chấm**) | — | — | **không**: 0 thẻ đục, chỉ `drawCircle` tại neo | `DoorBoardView.kt:61-90` | **DỌN**: đường chết `footerP`/`clip()`/`FOOTER_*` + dải trống đáy 14 % |
| 6 | **Trạng thái xe** `CarMiniView` | Canvas (chỉ **chấm**) | — | — | **không**: 0 chữ, 0 thẻ | `CarMiniView.kt:40-57` | **ĐÚNG SẴN** — không đụng |
| 7 | **Bảng tổng hợp** `w_board` → 4 × `WidgetTelemetry.BoardCell` | TextView (`LinearLayout` dọc, `gravity=CENTER`) | không (khung tự canh theo line-box) | **CÓ** — [ĐO 2026-09-27, sửa lại kết luận bản đầu] đơn vị **KHÔNG** nằm ở caption: nó được **nối vào chuỗi GIÁ TRỊ lớn** rồi cả dòng đó canh giữa ⇒ **con số** lệch trục ô đúng **nửa advance của đơn vị** — [SUY] ≈ **7 px** với `%`, ≈ **12 px** với `µg` (17sp · 1,5). Hai ô còn lại của bảng (`tyres`, `telemetryMini`) **sạch thật**: đơn vị ở caption | — | `WidgetTelemetry.kt:170-192`; chỗ **dựng chuỗi**: `WidgetViews.kt:465` (`"$it%"`) · `WidgetViews.kt:466` (`"${it}µg"`) | **không sửa ở lượt này** — cùng độ lớn với ca `ReadTile` (~11 px) mà làn này ĐÃ sửa, nhưng dời đơn vị xuống caption là **đổi CHỮ** trên màn owner vừa duyệt ⇒ **hoãn**, số đo ghi lại tại đây |
| 8 | Ô **nén** (lưới nhiều widget) `MiniCard` | TextView | không | **CÓ ở một trong ba nhánh** — [ĐO 2026-09-27] `w_energy` nối `%` vào chuỗi giá trị lớn ⇒ số lệch trục [SUY] ≈ **7 px**; `w_pm25` và `w_speed` **sạch** (đơn vị ở caption: `"µg · …"` / `"km/h"`) | — | `WidgetTelemetry.kt:62-116`; chỗ **dựng chuỗi**: `WidgetViews.kt:206` (`"$it%"`) | **không sửa ở lượt này** — cùng lý do #7 (đổi chữ trên màn đã duyệt), số đo ghi lại tại đây |
| 9 | Ô đọc **chung** (`VALUE/CARD/BOARD/DIAL/GAUGE`) `valueShape` | TextView + hàng ngang | không | **CÓ** | — | `WidgetTelemetry.kt:274-289` `LinearLayout(HORIZONTAL) + gravity=CENTER` bọc `số + " đơn vị"`, trong khi eyebrow canh giữa ô | **SỬA** (2) |
| 10 | **Tốc độ** `w_speed` | TextView + hàng ngang | không | **CÓ** (nặng nhất) | — | `WidgetViews.kt` `fun speed(` — hàng `số 44sp + " km/h" 15sp`, chú thích dưới canh giữa ô | **SỬA** (2) |
| 11 | Số chính thẻ **nhóm** (`CARD`) `GroupTileViews.leadRow` | TextView + hàng ngang | không | **CÓ** | `GroupTileViews.kt` `fun leadRow(` — `big 34sp + " ${unit}" 14sp`, nhãn ngắn dưới canh giữa | | **SỬA** (2) |
| 12 | Ô **đọc** của thanh nút / lưới nhóm `readTileOf` | TextView + hàng ngang | không | **CÓ** | — | `ReadTile.kt:141-146` `HORIZONTAL + gravity=CENTER`, nhãn 2 dòng phía trên canh giữa ô | **SỬA** (2) |
| 13 | Ô con dải `GroupTileViews.stripCell` | TextView | không | **không**: bản `CARD` cố ý xếp `nhãn ⟷ số` hai đầu (không phải cụm canh giữa); bản `STRIP` mỗi dòng một view canh giữa | — | `GroupTileViews.kt` `fun stripCell(` | **ĐÚNG SẴN** |
| 14 | **Đang phát** `MediaWidgetView` · **Đồng hồ** `clock` | TextView | không | không (0 cụm `số + đơn vị`; hàng nút là 3 nút bằng nhau ⇒ canh giữa cả hàng là ĐÚNG) | — | `MediaWidgetView.kt:65-80` · `WidgetViews.kt fun clock(` | **ĐÚNG SẴN** |

Ba bề mặt khác có `Paint`/`onDraw` nhưng **0 chữ**: `DatumIconView` · `VoiceWaveView` · `WallView`/`WallGlass`
(hình nền) — không thuộc phạm vi ba lỗi.

---

## 2. Bản vá

| # | Việc | Ở đâu |
|---|---|---|
| G1 | `TyreCellLayout` (`:core`) → **`CellTextLayout`** — di chuyển thuần + đổi tên tham số `wheelX`→`anchorX`, `carLeft/carRight`→`imageLeft/imageRight`. Lý do: bốn ô vẽ khác nhau cùng gọi, mà một ô ảnh hỏi *"hình học ô LỐP"* thì lần sau sẽ có người chép một bản thứ hai | `core/.../CellTextLayout.kt` |
| G2 | Thêm `CellTextLayout.centeredBaseline(centerY, topInk, bottomInk=0)` = `twoLineTopBaseline` với `lineGap = 0` — cho ô **một dòng**, để không ai phải truyền `0f` rồi tự nghĩ ra công thức riêng | `core/.../CellTextLayout.kt` |
| G3 | Nhịp 2 dòng `SUB_GAP_RATIO = 1.35f` rời khỏi `TyreBoardView` → **`CellTextLayout.SUB_LINE_GAP`** (số **không đổi**, CLAUDE.md §6). Ba ô vẽ 2 dòng nay cùng một nhịp chữ | `core` + `TyreBoardView` |
| R1 | `RingView`: baseline khối 2 dòng từ số đo phông (`getTextBounds("0")` + `SUB_LINE_GAP` + `descent`) | `RingView.onDraw` |
| R2 | `RingView`: **co chữ cho vừa đường kính trong** của vòng (`fitScale`), sàn cỡ chữ = `KachiSpace.BOARD_VALUE_MIN`/`BOARD_LABEL_MIN` — sàn chỉ chặn phần CO, không kéo chữ TO lên ở vòng bé | `RingView.fit` |
| R3 | `RingView`: 4 tỉ lệ ma thuật rải trong `onDraw` thành hằng có tên (`STROKE_RATIO`/`DIAMETER_RATIO`/`BIG_RATIO`/`SMALL_RATIO`) — **số giữ nguyên** | `RingView` |
| P1 | `PhotoWidgetView`: khối nhắc 2 dòng canh giữa bằng `ascent`/`descent` của phông + `SUB_LINE_GAP`; co chữ cho vừa bề ngang ô | `PhotoWidgetView.drawHint` |
| E1 | `GridEditorView`: số thứ tự khung canh bằng `centeredBaseline(box.centerY(), −ink.top)` thay hằng `0.35f` | `GridEditorView.onDraw` |
| D1 | `DoorBoardView`: xoá **đường chết** `footerP` · `clip()` · `FOOTER_BASELINE` · `FOOTER_RATIO` · `labelFloorPx` (dựng + tính mỗi lượt vẽ, `drawText` = 0 chỗ từ `84f91e6`) | `DoorBoardView` |
| D2 | `DoorBoardView`: `FOOTER_TOP 0.86` → `CAR_BOTTOM 0.98` — **cùng số với bảng lốp**; 14 % chiều cao ô đang bỏ trống để chừa chỗ cho đúng dòng chữ đã gỡ | `DoorBoardView` |
| A1 | **`AxisRow`** (`:app`) — `ViewGroup` mỏng: con 0 (số) đặt bằng `CellTextLayout.lineStartX` ⇒ nằm trên trục ô; con 1 (đơn vị) treo bên phải; đo ĐƠN VỊ trước rồi cho số phần còn lại (thà `ellipsize` số còn hơn mất đơn vị — luật của `TyreBoardView`) | `app/.../AxisRow.kt` |
| A2 | Bốn chỗ dựng `số + đơn vị` đổi sang `AxisRow`: `valueShape` · `speed` · `leadRow` · `readTileOf`. **Khe giữ nguyên** (khoảng trắng sẵn trong chuỗi đơn vị, hoặc `Sp.XS` như `marginStart` cũ) ⇒ nhịp ngang không đổi một px, chỉ TRỤC đổi | 4 tệp |

**KHÔNG làm** (có lý do, không phải quên):

- **Không** đổi `CarLayout.wheel`/`part` (dùng chung ba bảng) và **không** dịch khung ảnh xe — OQ7 của UX2 (khối 4
  thẻ tâm `0.53·Hc` vs ảnh `0.50`) **vẫn mở**: nó là quyết định thẩm mỹ của owner, không phải hệ quả của luật nào ở
  đây (xem §6).
- **Không** đụng `CarMiniView`, `BoardCell`, `MiniCard`, `stripCell`, `MediaWidgetView`, `clock` — đã đúng, lý do ở
  bảng §1. Sửa "cho đều tay" là đổi pixel một màn owner đã duyệt mà không có lỗi nào để chữa.
- **Không** đổi cỡ chữ/bán kính/lề của bảng nào: ngôn ngữ thị giác giữ nguyên thang cũ — thẻ lốp `radius = m*0.035`,
  lề chữ `m*0.02`; ô `TextView` `Sp.RADIUS_M` (12dp) + lề `Sp.S/M`; sàn chữ bảng `Sp.BOARD_VALUE_MIN` (16dp) /
  `Sp.BOARD_LABEL_MIN` (13dp).

---

## 3. Số trước/sau (dựng lại bằng chính phép toán `onDraw`, off-car)

Ô THẬT lấy từ `WorkspaceLayout.slots(...)` cho màn **1920×720**, vùng workspace **1920×560**, khe 9 px:
`ONE 1920×560` · `TWO_COL 955×560` · `TWO_ROW 1920×275` · `THREE ô0 1161×560, ô1/2 750×275` · `QUAD 955×275`.
Mô hình phông: cap-height 0,711 em · ascent 0,927 em · descent 0,244 em (Roboto) — **[ĐO]** cho công thức,
**[SUY]** cho px.

### 3.1 `RingView` — *Năng lượng* · *Không khí* · mọi datum `RING`

Khung vòng = ô trừ lề `col()` (16dp = 24 px mỗi bên) và dòng chú thích (~28 px) — **[SUY]** hai số đó; kết luận
KHÔNG phụ thuộc chúng (mức lệch là tỉ lệ của `d`).

| ô widget | khung vòng | `d` | **trước**: tâm khối lệch so với tâm vòng | **sau** | khe baseline trước→sau |
|---|---|---|---|---|---|
| ONE · THREE ô0 | 1872×484 · 1113×484 | 367,8 px | **+53,7 px** (+14,6 % · d) — chữ tụt xuống dưới tâm | **0,0 px** | 95,8 → 59,6 px |
| TWO_ROW · THREE ô1/2 · QUAD | ~1872×199 … 702×200 | 151–152 px | **+22,1 px** (+14,6 % · d) | **0,0 px** | 39,4 → 24,5 px |

Hệ quả nhìn thấy: con số nhích **lên** ~35 px (ô to) và dòng đơn vị nhích lên ~72 px, tức cụm chữ **ôm lấy tâm
vòng** thay vì treo lệch xuống nửa dưới.

### 3.2 `PhotoWidgetView` — lời nhắc "chưa có ảnh"

| ô widget | `m` | cỡ 2 dòng | **trước**: tâm khối lệch | **sau** | khe baseline trước→sau |
|---|---|---|---|---|---|
| ONE · TWO_COL · THREE ô0 | 560 | 42,0 / 32,5 px | −1,2 px | 0,0 px | 62,3 → 43,8 px |
| TWO_ROW · QUAD · THREE ô1/2 | 275 | 20,6 / 16,0 px | −0,6 px | 0,0 px | 30,6 → 21,5 px |

**Nói thẳng**: ở đúng cặp tỉ lệ đang dùng (0,075 / 0,058) hai hằng cũ **gần như triệt tiêu nhau** ⇒ lỗi (1) ở đây
chỉ ~1 px, KHÔNG phải thứ owner nhìn thấy. Lý do vẫn sửa: hai hằng tính theo **hai cỡ chữ khác nhau**, nên chúng
cân bằng **do may mắn**; đổi một trong hai tỉ lệ là lệch ngay (bài `:core` chứng minh: 0,075/0,030 ⇒ lệch > 2 % · m).
Co-chữ là **phòng ngừa**: câu nhắc dài nhất (EN, 36 ký tự) rộng ≈ 1,09 × `m`, nên nó chỉ tràn khi ô **cao hơn rộng**
17 % — không có trong 5 preset hiện tại, nhưng `drawText` không tự kẹp nên một ô dọc sau này sẽ tràn im lặng.

### 3.3 `GridEditorView` — số thứ tự khung

| cỡ chữ | cap 0,711 (Roboto) | cap 0,62 | cap 0,75 |
|---|---|---|---|
| 40 px | cũ +14,0 · mới +14,2 (lệch 0,2 px) | cũ +14,0 · mới +12,4 (**1,6 px**) | cũ +14,0 · mới +15,0 (**1,0 px**) |
| 128 px | cũ +44,8 · mới +45,5 (0,7 px) | cũ +44,8 · mới +39,7 (**5,1 px**) | cũ +44,8 · mới +48,0 (**3,2 px**) |

Hằng `0.35f` là **cap-height của Roboto chia đôi** (0,3555) viết cứng — đúng với phông của máy ảo, sai với phông
ROM khác. Đây là lỗi "đúng nhờ may mắn" y như §3.2.

### 3.4 `AxisRow` — con số lệch trục (lỗi (2)), density 1,5

| chỗ dựng | đơn vị | cỡ | bề rộng đơn vị | khe | **trước**: số lệch trái | **sau** |
|---|---|---|---|---|---|---|
| `w_speed` *Tốc độ* | `" km/h"` | 15sp | 58,3 px | 0 | **29,1 px** | 0,0 px |
| số chính thẻ nhóm (`CARD`) | `" bar"` | 14sp | 35,7 px | 0 | **17,9 px** | 0,0 px |
| ô đọc chung (`VALUE`…) | `" %"` | 14sp | 22,9 px | 0 | **11,4 px** | 0,0 px |
| ô đọc thanh nút · `BIG` | `"%"` | 13sp | 16,2 px | 6 px | **11,1 px** | 0,0 px |
| ô đọc thanh nút · `DOCK`/`GROUP` | `"%"` | 9,5sp | 11,8 px | 6 px | **8,9 px** | 0,0 px |

Lệch = `(khe + rộng đơn vị)/2` — **[ĐO]** (công thức), **[SUY]** (px, theo mô hình advance). Thấy được vì nhãn /
chú thích / eyebrow của **chính ô đó** canh đúng trục ô: hai dòng lệch nhau chừng ấy px là đủ để đọc ra "ô bị lệch".

### 3.5 `DoorBoardView` — dải trống đáy

| | khung ảnh (theo chiều cao ô) | phần bỏ trống | hình xe ở ô nhóm *Cửa & khoang* |
|---|---|---|---|
| trước | `0.03 … 0.86` | **14 %** đáy (di sản dòng kết luận đã gỡ ở `84f91e6`) | nhỏ hơn bảng lốp dù dùng CÙNG ảnh |
| sau | `0.03 … 0.98` | 2 % | cùng cỡ với bảng lốp (cùng `CAR_BOTTOM = 0.98`) |

---

## 4. Test

### `:core` — `CellTextLayoutTest` (13 → **20 ca**, thuần, off-car)

Bảy ca mới:

- `khoi chu can giua O THAT cua moi bang` — lưới **1 728 ca**: mọi ô của 5 preset × ba loại hộp (thẻ lốp · vòng đo ·
  ô ảnh) × 4 cỡ chữ chính × 4 tỉ lệ dòng phụ (kể cả **một dòng**) × 3 cap × 3 descent ⇒ `|tâm khối − tâm ô| ≤ 0,5 px`;
- `mot dong can giua bang so do muc` — `centeredBaseline` **là** `twoLineTopBaseline(lineGap = 0)` (không có phép
  canh thứ hai) + chứng minh hằng `0.35` lệch rõ với cap ≠ 0,711;
- `RingView - cong thuc CU day khoi chu xuong duoi tam vong 0,146 x d` — dựng lại chính hai hằng của 2.73;
- `PhotoWidgetView - hai hang cu ... vo khi doi ti le` — chứng minh "cân do may mắn";
- `AxisRow - con so ve dung truc o` — bốn chỗ gọi thật, lệch cũ = `(khe + đơn vị)/2` và > 8 px ở cả bốn;
- `AxisRow - o hep thi SO bi cat, don vi khong bao gio bi day ra ngoai` — dựng lại **hai bước** đo của `onMeasure`;
- `nhip 2 dong la MOT so dung chung`.

### `:app` — `CompositeWidgetLayoutContractTest` (**8 ca mới**, quét source)

`:app` không có Robolectric (android.jar stub ném) nên không dựng được `Canvas` thật ⇒ tầng này canh **dây nối**:

- **`khong con baseline bang hang so - moi drawText cua launcher`** — quét **mọi** `drawText` của thư mục launcher,
  lấy đúng tham số **y** (tách theo ngoặc, không theo dòng) và **đi thêm một tầng biến** (`val base = …`): hằng số
  thực ở đó ⇒ đỏ. Tự-kiểm: phải đọc được ≥ 8 lời gọi;
- năm ô vẽ Canvas dùng chung `CellTextLayout` + `SUB_LINE_GAP`; bảng cửa **không** có `drawText`/`footerP`/`FOOTER_`;
- mực dòng số đo trên **chữ mẫu** `INK_REF` (không trên chuỗi đang vẽ);
- chữ co cho vừa ô (`fitScale`) + sàn cỡ chữ lấy từ `KachiSpace`;
- bốn chỗ `số + đơn vị` đều là `AxisRow` và **không còn** hàng ngang tự canh giữa;
- `AxisRow` chỉ là vỏ quanh `lineStartX`, và đo **đơn vị trước** số;
- *(theo tính chất, áp cho cả bảng viết sau)* bảng nào vẽ ảnh xe **và** vẽ khối đục lên đó thì phải kẹp theo
  `cardSpanX`; hai bảng dùng ảnh xe phải có **cùng** `CAR_BOTTOM`.

### Kiểm chất lượng bài test (đột biến, không chỉ xanh)

| đột biến | kết quả |
|---|---|
| `RingView` trả về **đúng** công thức 2.73 (`base = cy + textSize*0.36f`, gap cũ) | **3/8 ca `:app` đỏ** |
| `CellTextLayout.twoLineTopBaseline` trả về công thức hằng cũ (`centerY + topInk*0.10 − lineGap*0.60`) | **6/20 ca `:core` đỏ** |

Lỗ đã vá nhờ đột biến: bản đầu của bài quét `drawText` **không** bắt được đột biến `RingView` vì hằng chỉ dời lên
một dòng `val` — nay bài đi thêm một tầng biến (ghi trong KDoc của nó để không ai gỡ ra).

### Kết quả (đếm từ XML `build/test-results`)

| lệnh | trước làn | sau làn |
|---|---|---|
| `:core:test --tests '*Cell*' '*CarLayout*' '*Tyre*' '*Widget*'` (15 lớp) | 105/105 xanh | **112/112 xanh** |
| `:app:testDebugUnitTest --tests '*Widget*' '*Tyre*' '*Door*' '*CarImage*' '*Group*' '*Goi2*' '*I18n*'` (17 lớp) | 153, **2 đỏ** | 161, **2 đỏ** |

Hai ca đỏ của `:app` là **của làn khác và đã đỏ từ trước làn này**: `LauncherI18nContractTest` bắt chuỗi trần trong
`CameraGlRenderer.kt` · `CameraGlSurface.kt` · `TestBridgeSynth.kt` (làn camera/voice). 0 tệp của làn UX7 dính.

---

## 5. Pha ẢNH — điều phối chụp gì sau khi dựng lại

Ảnh máy ảo chụp được trong làn này (chỉ đọc, không cài/không chạm): màn hình đang hiện **bảng LỐP preset 1 ô** của
bản đã cài — xác nhận trạng thái mẫu sau UX2 (thẻ dừng trước thân xe, gương còn nguyên; số và dòng phụ cùng trục).
Mọi số ở §3 là **mô phỏng off-car**, chưa có ảnh của bản mới ⇒ cần pha ảnh:

| # | Hồ sơ / bố cục | Ô | Phải nhìn ra |
|---|---|---|---|
| 1 | `THREE`, ô 0 (1161×560) | **Bảng tổng hợp** (`w_board`) | 4 ô con không đổi (đối chứng: làn này KHÔNG đụng) |
| 2 | `ONE` (1920×560) | **Lốp** | y như trước (đối chứng cho phần `SUB_LINE_GAP` dời sang `:core`) |
| 3 | `THREE` ô1 + `ONE` | **Năng lượng** | số + "Pin"/khoảng chạy **ôm tâm vòng**, không tụt xuống nửa dưới; chữ không chạm cung |
| 4 | `THREE` ô2 + `QUAD` | **Không khí** | như #3, và `µg/m³` không tràn vòng ở ô nhỏ |
| 5 | `QUAD` | **Tốc độ** | con số **thẳng trục** với dòng "Tốc độ hiện tại" dưới nó (trước lệch trái 29 px) |
| 6 | bất kỳ | ô nhóm **Năng lượng & sạc** (thẻ `CARD`) | số chính thẳng trục với nhãn ngắn dưới |
| 7 | thanh nút dưới + ô nhóm có mục đọc | ô **đọc** (vd *Mức xăng*) | giá trị thẳng trục với nhãn 2 dòng phía trên |
| 8 | nhóm **Cửa & khoang** | bảng cửa | hình xe **to hơn** (hết dải trống đáy), chấm cửa vẫn đúng vị trí, hàng nút không bị cắt |
| 9 | ô có **Trạng thái xe** | xe mini | không đổi (đối chứng) |
| 10 | **Trình chiếu ảnh** với thư mục ảnh RỖNG | ô ảnh | hai dòng nhắc cân giữa ô, khe hẹp hơn trước, không tràn mép |
| 11 | màn **Sửa bố cục** (trình vẽ khung) | số thứ tự khung | số nằm giữa khung (thay đổi ≤ 1 px với phông máy ảo — chỉ cần không lệch) |

Ảnh #8 và #3/#4 là **thay đổi owner-visible lớn nhất** của làn này ⇒ cần mắt owner duyệt.

---

## 6. Còn nợ — cần owner hoặc làn khác

1. **OQ7 (UX2) vẫn MỞ** — khối 4 thẻ của bảng lốp có tâm `0.53 × Hc` (neo bánh 0,26/0,80 không đối xứng) còn ảnh xe
   có tâm `0.50` ⇒ xe "nhô lên" ~16 px so với khối thẻ. Luật của làn này **không** giải được nó: đây là câu hỏi
   *"đặt ẢNH ở đâu"*, không phải *"đặt CHỮ ở đâu trong ô"*, và nó chỉ áp cho MỘT trong ba bảng dùng chung ảnh xe.
   Vẫn chờ owner chốt (giữ ảnh canh giữa ô, hay dịch ảnh xuống để khối thẻ canh giữa).
2. **Đổi tên `TyreCellLayout` → `CellTextLayout`** kéo theo hai chỗ trong tài liệu còn ghi tên cũ:
   `docs/README.md` (dòng chỉ mục 2.74) và `docs/specs/kachi-274-ux-voice-camera.html` §3 R2. Làn này **không sửa**
   hai tệp đó để tránh ghi đè lẫn nhau (CLAUDE.md §2: một chủ sở hữu cho một spec tại một thời điểm) ⇒ **điều phối
   sửa** trong lượt tổng hợp. `ux-ux2-tyre.md` giữ nguyên tên cũ vì nó là **biên bản** của làn UX2.
3. **`CarImageStore.decodePlan` dùng `cover` trong khi KDoc hứa `fit`** — nợ cũ của UX2 (§5.1 tài liệu đó), chưa
   sửa vì `CarImageStoreTest` đang ghim ngữ nghĩa `cover` ở 2 chỗ. Không thuộc phạm vi làn này.
4. **Chưa đo trên XE** — mọi số ở §3 là off-car. Phông của ROM DiLink có thể có cap-height/descent khác Roboto;
   điều đó **không** làm sai bản vá (chính vì vậy mà baseline nay đọc số đo phông lúc chạy), nhưng nó đổi các con
   số px ở §3. Cần ảnh trên xe của ô *Năng lượng* + ô *Tốc độ* để đóng lại.
