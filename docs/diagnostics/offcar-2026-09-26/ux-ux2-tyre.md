# UX2 · R2 — widget lốp: chữ lệch trong thẻ + "hình xe chỉ thấy nóc"

Spec: `docs/specs/kachi-274-ux-voice-camera.html` §3 R2 · lane UX2-TYRE · off-car 2026-09-26 · bản gốc lỗi: 2.70 trên xe.

---

## 1. Nguyên nhân gốc — BA lỗi ĐỘC LẬP, cùng nằm trong `TyreBoardView.onDraw`, cộng một lỗi ở kho ảnh

Giả thuyết trong spec (*"nghi giải mã theo mật độ ở closeout 2.66 ⇒ centerCrop chỉ thấy nóc"*) **BỊ BÁC BỎ** [ĐO]:
`CarImageLayer.draw` lấy `src = (0,0,b.width,b.height)` (trọn bitmap) và `dst = fitRect(...)` (letterbox giữ tỉ lệ,
canh giữa) — không có `ImageView`/`scaleType`/crop nào trên đường này. Ảnh **không** bị cắt; nó bị **che** và bị
**xoá alpha ở mép**.

### (A) Khối 2 dòng lệch LÊN — hằng ma thuật không mang chiều cao khối [ĐO]

`TyreBoardView` (2.73) đặt baseline dòng số bằng `centerY + big*0.10 − sub*0.60`. Hai hằng đó không chứa một số hạng
nào của chiều cao khối 2 dòng, nên tâm khối cao hơn tâm thẻ đúng `0.255·big − 0.18·sub` ≈ **0,11 × cellH**.

Dựng lại bằng chính phép toán `onDraw` cho ô THẬT (`WorkspaceLayout` preset `THREE`, ô 0 = 1155×560 trên màn 1920×720),
phông giả định cap-height 0,711 / descent 0,25 em:

| | khoảng trống TRÊN chữ | khoảng trống DƯỚI chữ |
|---|---|---|
| 2.73 | **5,5 px** | **35,9 px** |
| 2.74 | 20,7 px | 20,7 px |

Ảnh owner chụp 2026-09-25 ghi *"chữ dán sát mép trên, chừa ~66 px trống dưới, lệch lên ~32 px"*. Ảnh đó là ảnh **chụp
màn hình bằng máy ảnh**, nên có một hệ số tỉ lệ chưa biết; với đúng MỘT hệ số ≈ 1,8 thì mô hình cho 35,9 × 1,8 = **65 px**
(owner: ~66 px) và lệch 15,2 × 1,8 = 27 px (owner: ~32 px). ⇒ cơ chế **[ĐO]**, độ lớn **[SUY]** (một ẩn số tỉ lệ).

### (B) Con số lệch TRÁI — canh giữa CẢ CỤM thay vì canh con số [ĐO]

`drawPair` canh giữa cụm `số + khe + đơn vị` quanh `cell.centerX()`, còn dòng phụ canh giữa **đúng** `cell.centerX()`.
Hệ quả: hai dòng cùng trục *của cụm* nhưng **con số** — thứ mắt đọc — lệch trái đúng `(khe + rộng đơn vị)/2`
(≈ 24 px với "bar" ở ô thật). Đây là chỗ mô tả của khảo sát ban đầu (*"hai dòng khác trục"*) **không chính xác**: cùng
trục, chỉ con số lệch.

### (C) Thẻ ĐỤC vẽ đè lên thân xe — mép thẻ lấy theo neo bánh, mà neo nằm TRONG thân xe [ĐO]

- `cell` chạy tới `wheelX ∓ gap`, với `wheelX = content.left + 0.17 × Wc` (`CarLayout.wheel`, x = 0.17/0.83).
- `cellFill` = `KachiTheme.CARD2` = `SurfaceRamp.at(2)` **alpha 255 (ĐỤC)**, và `CellsView` được `addView` **sau**
  `carView` ⇒ lớp thẻ tô lên trên ảnh xe.
- Đo lại cho mọi ô thật: thẻ ăn **26,6 px mỗi bên = 10,4 % bề rộng KHUNG ẢNH** (con số này giống nhau ở mọi preset vì
  letterbox luôn bị siết theo chiều cao). Trừ lề trong suốt sẵn có của ảnh (3,98 % mỗi bên) thì phần **THÂN XE** bị che
  là **≈ 6,4 %** mỗi bên — tức mất gương + mép cửa, **không phải** "chỉ còn nóc + kính lái" như mô tả ban đầu
  (mô tả đó phóng đại ~3×; ảnh mô phỏng của chính khảo sát cũng chỉ cắt tới mép gương).

### (D) Feather xoá alpha vào thân xe [ĐO]

`CarImageStore.feather` xoá alpha bằng 4 gradient `DST_OUT` trên dải `0.08 × cạnh NGẮN`, áp mù cho cả 4 mép.
Số đo thật của `app/src/main/assets/car/default-car.png`: **678×1397**, lề **trong suốt** (alpha < 8) = **27 px**
trái/phải (3,98 % bề rộng) và 48 px trên/dưới. Dải = `0.08 × 678` = **54,2 px** ⇒ dải ăn **27 px vào thân xe mỗi bên**;
vì là gradient, mặt nạ tại mép thân còn ~50 % ⇒ **gương chiếu hậu mờ nửa alpha** (mất dần tới 0 ở 27 px vào trong).

Lưu ý thứ tự: trước 2.74 dải này nằm **dưới thẻ đục** (thẻ lấn 10,4 % > dải 8 %), nên nó **không** phải thứ owner
nhìn thấy. Sau khi vá (C), mép ảnh mới lộ ra — nên (D) phải vá **cùng lượt**, nếu không lỗi mới hiện ra lần đầu ở 2.74.

---

## 2. Bản vá

| # | Việc | Ở đâu |
|---|---|---|
| V1 | Mép TRONG của thẻ kẹp ra ngoài **khung ảnh thật** (`min/max` với neo bánh để nếu ai đổi neo ra ngoài thân xe thì thẻ tự lùi theo) | `CellTextLayout.cardSpanX` (`:core`) ← `TyreBoardView.onDraw` |
| V2 | Baseline khối 2 dòng tính từ **số đo phông**: ink của **chữ số mẫu** (`getTextBounds("0")` — dùng cap-height thật thay vì `ascent` vốn chừa chỗ cho dấu mà chữ số không dùng; đo chữ mẫu chứ không đo chuỗi đang vẽ để **bốn thẻ cùng một baseline**, kể cả thẻ đang là "—") + khoảng baseline + `descent` của PHÔNG dòng phụ (không lấy ink chuỗi: "TT · 27°C" và "TT · lệch · 27°C" sẽ nhảy baseline) | `CellTextLayout.twoLineTopBaseline` ← `drawCell` |
| V3 | **Con số** vào trục ô, đơn vị treo bên phải; hết chỗ bên phải thì cả cụm nhích trái (không bao giờ cắt đơn vị); dòng phụ cùng trục. Thẻ hẹp đi 9–10 % sau V1 ⇒ **co chữ cho vừa** (sàn `MIN_VALUE_SP`/`MIN_SUB_SP`), vẫn dài thì cắt đuôi `…` thay vì tràn lên xe | `CellTextLayout.lineStartX` + `fitScale` ← `drawCell` |
| V4 | Feather quyết định theo **TỪNG mép** bằng **TỈ LỆ MỰC của chính mép đó** (`CarImageStore.edgeInkRatio` — **không** bằng lề trong suốt, xem ghi chú dưới bảng): với mỗi mép trong 4 mép, đo **phần** các hàng/cột của mép ấy có mực chạm tới mép (dung sai `CarLayout.HARD_EDGE_PX`). Tỉ lệ **≥ 0,95** (`CarLayout.SOLID_EDGE_INK`) ⇒ coi là **ảnh chụp** (mép đặc mực) ⇒ feather mép đó dải 8 % như cũ; **dưới** ngưỡng ⇒ **ảnh cắt nền** ⇒ **0**, để mép cứng (feather ở đó chính là xoá alpha THÂN XE — đúng lỗi "mất gương"). `hasAlpha() == false` (JPEG/RGB_565) ⇒ trả `1` cho cả 4 mép ⇒ feather đủ, **y hành vi 2.73**; đọc pixel hỏng cũng trả `1` (fail-safe về 2.73), không ném. Cả 4 mép = 0 ⇒ **trả chính `src`**, khỏi tạo bitmap thứ hai (và **không** `recycle` — kho giữ đúng bitmap đó) | `CarLayout.featherBand` + `CarLayout.SOLID_EDGE_INK` (`:core`) + `CarImageStore.edgeInkRatio`/`feather` |
| V5 | Ảnh nạp xong ⇒ lớp thẻ **kẹp lại** theo khung mới (`onDescendantInvalidated`): trước đó khung là placeholder tỉ lệ 0,46 ≠ 0,485 của ảnh thật, nếu không vẽ lại thì thẻ giữ hình học cũ và lấn vào xe tới nhịp số kế | `TyreBoardView.onDescendantInvalidated` |
| V6 | Câu kết luận (`TyreBoard.verdict` + dấu *"nhiệt chưa kiểm"*) đang là **đường chết** — dựng đủ, truyền vào ô vẽ, `grep drawText` = 0 chỗ vẽ, từ `84f91e6` (owner bỏ dòng kết luận) — nay hạ cánh xuống **nhãn trợ năng** của ô (`contentDescription`) | `TyreBoardView.set` + `WidgetViews.tyreBoard` |
| V7 | Viết tắt bánh dùng `displayShortLabel` (theo ngôn ngữ) thay `shortLabel` — cùng luật đã áp cho ô nhóm (bản EN đang hiện *"TT"*) | `drawCell` |
| — | `FOOTER_TOP` (tên chết của dòng kết luận đã gỡ) đổi tên `CAR_BOTTOM`, **giữ nguyên số 0.98** (CLAUDE.md §6: không đảo đường đã chạy ngoài hiện trường) | `TyreBoardView` |

**Vì sao đo TỈ LỆ MỰC chứ không đo LỀ trong suốt** [ĐO — KDoc `CarImageStore.edgeInkRatio`]: hai ca cần hai xử lý
NGƯỢC nhau lại có **lề giống nhau (= 0)**, nên lề không phân biệt được. (a) ảnh chụp chữ nhật **đặc**: cả mép đặc mực
(tỉ lệ ≈ 1) ⇒ **ĐÁNG** feather (mép cứng tan vào nền thẻ); (b) ảnh **cắt nền cắt sát** (gương/mũi xe đúng biên ảnh):
chỉ vài dòng chạm mép (tỉ lệ nhỏ) ⇒ feather ở đó là **xoá alpha thân xe** — đúng lỗi "mất gương". Ảnh mặc định ở §1 (D)
là ca thứ ba (lề 27/48 px, **không** mực nào chạm mép ⇒ tỉ lệ = 0): cả hai cách đo đều cho "đừng feather", nên nó
**không** phải ca phân biệt được hai cách — ca phân biệt là (b). Ngưỡng nghiêng về **KHÔNG xoá mực** (`0,95` chứ không
`1,0`) để chừa viền khử răng cưa / góc bo nhẹ của ảnh chụp.

> **Tên lớp**: chỗ nào trong tài liệu này ghi `CellTextLayout`, bản gốc của làn này gọi là `TyreCellLayout`; làn **UX7**
> (2026-09-27) đổi tên + tổng quát hoá cho bốn ô vẽ khác cùng dùng (`ux-ux7-composite-widgets.md` §2 G1), kèm đổi tên
> tham số `wheelX`→`anchorX`, `carLeft/carRight`→`imageLeft/imageRight`. Tên trong tài liệu này đã cập nhật theo code
> đang ship.

Cơ chế framework, đã fetch source theo CLAUDE.md §3 (không dựa trí nhớ):

| tệp:dòng (`android-10.0.0_r47`) | mã | hệ quả |
|---|---|---|
| `core/java/android/view/View.java:17620` | `p.invalidateChild(this, damage)` | con `invalidate()` báo lên cha |
| `core/java/android/view/ViewGroup.java:5909-5915` | `if (attachInfo.mHardwareAccelerated) { onDescendantInvalidated(child, child); return; }` | đường V5 dựa vào; **không** tăng tốc phần cứng ⇒ hook không chạy ⇒ rơi về hành vi 2.73 (kẹp lại ở nhịp số kế), không sập |

**KHÔNG làm** (có lý do, không phải quên):

- **Không** đổi neo `CarLayout.wheel` (dùng chung với bảng CỬA + xe mini).
- **Không** dịch khung ảnh để "khối 4 thẻ canh giữa ô". Dải 4 thẻ có tâm ở `0.53 × Hc` (neo bánh 0.26/0.80 vốn không
  đối xứng) còn ảnh có tâm `0.50` ⇒ xe "nhô lên" so với khối thẻ đúng `0.03 × Hc` (≈ 16 px ở ô thật) — đúng ý (C)
  trong note của owner. Nhưng **dịch cả khối là quyết định thẩm mỹ owner-visible** và chỉ áp cho MỘT trong ba bảng
  dùng chung ảnh xe ⇒ để owner chốt (xem §5).
- **Không** đổi `decodePlan` sang `fit` (xem §5) — bài ghim hiện có đòi ngữ nghĩa `cover`.
- **Không** chạm đường nạp/LRU/thử-lại (`ensure`/`shared`/`LoadRetry`) vừa hardening ở closeout 2026-09-25.

---

## 3. Test

`core/src/test/kotlin/com/byd/clusternav/launcher/CellTextLayoutTest.kt` — **12 ca của làn này, thuần, off-car** (UX7
thêm ca vào cùng tệp ⇒ nay **20 ca**):

- thẻ **không bao giờ** đè thân xe: lưới = mọi ô của **cả 5 preset `WorkspaceLayout`** (ba chiều cao workspace) ×
  tỉ lệ ô 0,5–3,0 (bao **cả hai** nhánh letterbox) × 3 tỉ lệ ảnh; kèm một assert *phản chứng* đo lại mức đè của công
  thức cũ (> 5 % bề rộng khung ảnh) để bài không kiểm một lỗi tưởng tượng;
- ô THẬT vẫn còn chỗ vẽ (thẻ không bị kẹp quá tay);
- khối 2 dòng cân tâm với **mọi** phông (cap 0,60–0,75 × descent 0,15–0,30) và mọi cỡ ô — không giả định một phông;
- công thức **cũ** luôn lệch LÊN ≥ 0,10 · cellH (khoá lại lỗi (A): trả hằng `0.10`/`0.60` về là đỏ);
- con số ở trục ô; hết chỗ thì nhích trái mà **không** bị cắt; cụm rộng hơn thẻ thì bám lề trong;
- dải feather: số đo THẬT của ảnh mặc định (678×1397, **không** mực nào chạm mép ⇒ tỉ lệ mực = 0 ở cả 4 mép, lề 27/48 px,
  dải cũ 54,2 px) ⇒ 0; ảnh chụp đặc (tỉ lệ ≈ 1) ⇒ vẫn 8 %;
  1 px viền khử răng cưa vẫn là mép cứng; quyết định theo **từng** mép.

`:app` (quét source — JVM thuần không dựng được View/Canvas):

- `CarImageLayerContractTest` + 2 ca: `feather` phải ĐO **tỉ lệ mực từng mép** (`edgeInkRatio(src)`) rồi mới quyết dải,
  nhánh "không mép cứng" phải `return src` **không** `recycle`, `edgeInkRatio` phải có đường nhanh `!hasAlpha()` +
  `runCatching`; hook `onDescendantInvalidated`
  chỉ phản ứng với lớp nền và không gọi ngược lớp nền.
- `Goi2FeatureWiringContractTest` + 1 ca mới, và **siết** ca R8 cũ: ngoài chỗ *dựng* chuỗi, nay ghim cả **bề mặt**
  (`contentDescription = summary`) — chính lỗ đã cho đường chết sống 3 tháng với test xanh.

**Kiểm chất lượng bài test (không chỉ xanh)**: đột biến 3 chỗ (`cardSpanX` về `wheelX ∓ gap`, `twoLineTopBaseline` về
công thức hằng cũ, `featherBand` luôn trả dải đầy) ⇒ **6/12 ca đỏ** đúng chỗ. Đã trả lại nguyên trạng.

Kết quả (đếm từ XML `build/test-results`):

| lệnh | kết quả |
|---|---|
| `:core:test` — `CellTextLayoutTest` · `CarLayoutTest` · `TyreBoardTest` · `WorkspaceLayoutTest` · `LangCoverageTest` | **67/67 xanh** |
| `:app:testDebugUnitTest` — 12 bài: `CarImageLayerContractTest` · `Goi2FeatureWiringContractTest` · `GroupTileWiringContractTest` · `CarImageStoreTest` · `CarImageLoadRetryTest` · `CarDataDemandRendererContractTest` · `LauncherI18nContractTest` · `LauncherLocaleContractTest` · `TypeScaleContractTest` · `SpacingScaleContractTest` · `SurfaceMaterialContractTest` · `ThemePaletteContractTest` | **120/120 xanh** |

Ghi chú cho điều phối: giữa lượt kiểm, `:app` có lúc **không biên dịch được** vì một tệp của LÀN KHÁC
(`ControlTileFactory.kt`, `Return type mismatch: expected 'Boolean', actual 'Boolean?'`) — không phải tệp của làn này.
Đã đợi và chạy lại; hai tệp `:app` của làn này cũng được biên dịch riêng bằng `kotlinc` (android.jar + `core.jar` +
`R.jar`) để chắc là 0 lỗi thuộc làn này.

---

## 4. Pha ảnh (máy ảo / xe) phải chốt những gì

Không dùng máy ảo trong làn này (một agent khác đang chạy voice E2E). Pha ảnh của điều phối cần **năm** ảnh:

1. **Ô lốp ở preset `THREE` (ô 0, 1155×560)** — ba điều trên một ảnh: (a) gương + mép cửa hai bên **không** bị thẻ
   cắt; (b) trong mỗi thẻ, khoảng trống trên = dưới bằng mắt; (c) con số và dòng phụ cùng một trục dọc.
2. **Ô RỘNG nhất (`ONE`, 1920×560) và ô hẹp/thấp (`QUAD`, ~955×255)** — cỡ chữ đã co vẫn đọc được, dòng phụ không
   tràn khỏi thẻ, không thẻ nào mất chữ.
3. **Ô nhóm `g_tyres`** (đường thứ hai tới cùng ô vẽ) — cùng ba điều ở mục 1.
4. **Widget CỬA + xe mini** — hai bảng này dùng chung ảnh đã feather: sau V4 mép ảnh **nét hơn** (không còn xoá 8 %).
   Cần mắt owner xác nhận nét-hơn là **đẹp hơn**, không phải "dán cứng".
5. **Thả một ảnh JPG chữ-nhật-đặc vào thư mục `car/`** rồi chụp lại bảng lốp: mép ảnh **vẫn phải tan** vào thẻ
   (chứng minh V4 không bẻ ca mà feather sinh ra để phục vụ).

Trên XE, một câu hỏi còn mở: owner đang dùng **ảnh nào** (mặc định trong APK hay ảnh tự thả vào thư mục app)? Chốt bằng
một lệnh liệt kê thư mục `car/` của app (ClusterDiag/`run-as`, xem CLAUDE.md §11) — không bắt owner gõ adb.
Cả hai trường hợp đều dính (C); riêng (D) chỉ nặng với ảnh đã cắt nền.

---

## 5. Còn nợ — cần quyết định/ngoài quyền sửa của làn này

1. **`CarImageStore.decodePlan` dùng `cover` trong khi KDoc hứa `fit`** [ĐO]: nó gọi `WallpaperStore.scaledWidth`
   (`maxOf`, đúng cho hình nền vẽ phủ-kín-rồi-cắt) còn ảnh xe vẽ **letterbox** ⇒ cấp thừa pixel: khung 210×554 nhận
   bitmap 269×554 = **1,64× pixel** (RAM, không sai hình). Lượt này **chỉ sửa KDoc cho nói đúng sự thật**, vì
   `CarImageStoreTest` (ngoài quyền sửa của làn) đang **ghim ngữ nghĩa cover** ở 2 chỗ. Sửa trọn gói = 1 dòng mã +
   2 assert.
2. **`docs/PROJECT-BACKLOG.md` đang đánh ✅ DONE cho đúng lỗi này** — mục `UI-TYRE-LAYOUT`. `git show 6872281^` cho
   thấy công thức `numBase` **y hệt** trước và sau 2.56; 2.56 chỉ đổi `TOP_INSET 0.02→0.04`, `FOOTER_TOP 0.88→0.98`
   và dời nút ⇄ (tức làm (B), không làm (A)/(C)/(D)). Mục đó có ghi kèm `🚗 owner nhìn lại trên xe` nên **không phải
   đóng im lặng**, nhưng chữ "DONE" vẫn sai và đã đủ để một lượt sau tin backlog rồi bỏ qua.
3. **Khối 4 thẻ lệch tâm so với ảnh xe `0.03 × Hc`** (≈ 16 px ở ô thật) vì neo bánh 0.26/0.80 không đối xứng — cần
   owner chốt: giữ (ảnh canh giữa ô) hay dịch khung ảnh xuống để khối thẻ canh giữa (đổi vị trí xe, chỉ ở bảng lốp).
4. `WidgetViews.kt` = **479 dòng** (trần 500 theo CLAUDE.md §4.1): lượt sau thêm việc vào đó phải tách theo vai trước.
