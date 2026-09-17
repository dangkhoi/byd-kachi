# NOTES-car — P3 hình xe tổng hợp: nguồn · script · những gì tầng tích hợp phải làm (agent B2 · 2026-09-17)

> ⚠ **Tích hợp 2026-09-17 (agent C):** `design/out/car/` KHÔNG còn — `gen-car.py` ghi thẳng `res/drawable/` + `launcher/CarFramesGenerated.kt` + `design/car/manifest.json`; xem trước ở `docs/diagnostics/visual-refresh-2026-09-16/p3/`. `PAINT_STOP_*` bỏ (không ai gọi). Mục 4 dưới đã làm: `CarFrames` tra `PIECES`, `CarPartStyle` (:core), `CarArtSource`/`VectorCarArt`/`CarArtPainter`, `CarMiniView` đổi sang cùng nguồn, `IconStyleContractTest` viết lại.


> Phạm vi của lượt này: **NGUỒN + SINH VÀO STAGING**. Không gradle, không chạm `res/drawable`, Kotlin, test, spec.
> Mọi khẳng định dưới đây là [ĐO] trên máy build trừ khi ghi khác.

## 1 · Có gì, ở đâu

| Thứ | Đường dẫn | Ghi chú |
|---|---|---|
| 3 SVG nguồn (AC1.1) | `design/car/top.svg` · `front.svg` · `side.svg` | viewBox `0 0 24 24` (hệ CarFrames) · mỗi bộ phận = 1 phần tử có `id` · lớp `<g id="layer-…">` theo §4.3 · `data-role` = vai màu · `data-glyph="1"` = ký hiệu chỉ cho icon · `data-merge="1"` gộp con thành 1 path · `data-ref="body"` = highlight dùng lại thân |
| Màu sơn | `design/car/paint.json` | 5 màu §4.8; script điền `contrast`/`forceOutline` (WCAG, **cả hai đầu** gradient) |
| Script sinh | `scripts/design/gen-car.py` (stdlib) | `python3 scripts/design/gen-car.py` sinh · **`python3 scripts/design/gen-car.py --check`** so byte (exit 1 nếu lệch) |
| Xem trước | `scripts/design/render-car.py` (Pillow) | máy không có rsvg/cairosvg/inkscape/ImageMagick ⇒ tự làm phẳng path + gradient theo hộp bao; ~40 s |
| Staging | `design/out/car/drawable/ic_car_*.xml` (43) · `car_face_{top,front,rear,side}.xml` (4, 48dp có `<aapt:attr><gradient>`) · `CarFramesGenerated.kt` · `manifest.json` · `paint.json` | 47 XML đều parse hợp lệ (ElementTree) |
| Ảnh ngắm | `design/out/car/preview-sheet-faces.png` (4 mặt × tối/sáng 240px) · `preview-sheet-48.png` (48px phóng ×3) · `preview-paints.png` (5 sơn × 2 nền) · `preview-top-parts.png` (từng bộ phận mặt trên nổi màu nhấn) · `preview-icons-{dark,light}.png` (43 icon 72px) · `preview-<mặt>-{240,48}-{dark,light}.png` | 620 KB PNG — đề nghị chuyển vào `docs/diagnostics/visual-refresh-2026-09-17/car/` khi tích hợp, không để trong `design/out` |

`design/out/` **không** bị gitignore. Đề nghị (agent A quyết): commit `design/car/*` + `scripts/design/*` + `design/out/car/{drawable,CarFramesGenerated.kt,manifest.json,paint.json}`; test T10 "sinh-lại-so-byte" thì so `res/drawable/ic_car_*.xml` với `design/out/car/drawable/` (JVM đọc 2 tệp), còn `--check` giữ SVG ↔ out đồng bộ (chạy tay/CI, không cần Python trong test).

## 2 · Bộ phận từng mặt (id) và số path

**TOP — 27/28 path khung** (AC5.1) · 14 glyph
- glow: `glow_front` `glow_rear` (ellipse, gradient toả — quầng đèn, **không blur**)
- body: `body` (NGUYÊN VĂN TOP_BODY của U7)
- glass: `windscreen` `glass_rear` (MẢNG — cung trên vẫn là cung của U7) · `glass_lf` `glass_rf` `glass_lr` `glass_rr` · `sunroof` · `sunshade` (1 path even-odd = thanh cuộn + tấm phủ + 2 nếp — trước là 2 hằng `SHADE_ROLL`/`SHADE_SHEET`)
- lights: `headlamp` (2 nhánh) · `drl` (2 nhánh) · `turn_l` `turn_r` · `tail` (2 nhánh)
- tyres: `wheel_fl` `wheel_fr` `wheel_rl` `wheel_rr` (NGUYÊN VĂN WHEEL_* U7)
- doors: `door_lf` `door_rf` `door_lr` `door_rr` (NGUYÊN VĂN FLAP_* U9) · `bonnet` · `boot` · `mirror` (NGUYÊN VĂN)
- highlight: `highlight` → `data-ref="body"` (không phải path mới)
- glyph (chỉ icon): `lock_shackle` `lock_body` `seat_fl` `therm_stem` `therm_bulb` `sunroof_gap` `arrow_v_stem` `arrow_v_heads` `chev_front` `chev_rear` `dots` `drop` `note_head` `note_stem`

**FRONT (+ REAR cùng bóng thân) — 19/28** · 8 glyph
- shadow: `shadow` · glow: `glow_front` · tyres (DƯỚI thân): `wheel_l` `wheel_r`
- body: `body` (bóng MỚI: vai mềm, nóc bo, đáy bo — thay hình thang góc nhọn U7) · `bonnet` (gờ ca-pô = NGUYÊN VĂN cung kính U7, nét)
- glass: `windscreen` · lights: `headlamp_l` `headlamp_r` (NGUYÊN VĂN thấu kính U7) · `drl` · `fog` (2) · `turn_l` `turn_r` · `sidelight` (2) · doors: `mirror` (2)
- rear (chỉ nhìn từ sau): `glow_rear` `tail_bar` `plate` `fog_rear`
- glyph: `rays_high` `rays_low` `fog_beam` `mode_rays` (cốt+pha gộp 1 path — icon chế-độ-đèn mới lọt 6 path) `defrost_waves` `hinge` `arrow_h_stem` `arrow_h_heads`

**SIDE — 18/28** · 0 glyph (chưa icon nào dùng; SideBoardView đã xoá cùng ADAS)
- `shadow` `glow_front` `glow_rear` · `body` `bonnet` · `windscreen` `glass_front` `glass_rear` `glass_quarter` · `headlamp` `tail` · `wheel_front` `wheel_rear` `rim_front` `rim_rear` (bánh TRÊN thân) · `door_front` `door_rear` `mirror` · `highlight`→body

Icon: 43/43 ≤ 6 path (tối đa 6: `door_all` · `window_all` · `ambient_music` · `sunroof_pos`). Mặt 48dp: top 20 · front 11 · rear 10 · side 18 path.

## 3 · Delta hộp bao so với CarFrames hôm nay (hình học thật, hệ 24×24)

| id | cũ | mới | Δ [l,t,r,b] |
|---|---|---|---|
| body · 4 wheel · 4 door flap · mirror · bonnet(hood) | — | — | **0** (chuỗi giữ nguyên hình) |
| boot (trunk) | 8.9,17.3,15.1,19.5 | 9.2,17.7,14.8,19.6 | +0.3,+0.4,−0.3,+0.1 (né kính hậu nay là mảng) |
| sunroof | 9.6,9.3,14.4,15.3 | 10.3,10.9,13.7,14.6 | +0.7,+1.6,−0.7,−0.7 (thu vào giữa nóc, không đè kính lái/kính cửa) |
| sunshade | 9.3,9.9,14.7,16.0 | 10.6,11.2,13.4,14.4 | nằm trong sunroof mới |
| glass_lf (window) | 8.9,9.4,10.3,12.2 | 8.6,10.6,9.7,12.4 | dịch ra mép cabin, xuống dưới kính lái |

⚠ **Hai loại hộp bao**: `Path.computeBounds` của Android trả hộp **điểm điều khiển** (thân: x 7.2..16.8), script trả hộp **hình học thật** (7.28..16.72). Nếu tầng tích hợp chuyển sang dùng `Piece.bounds` thì nhãn `%`/áp suất dịch ≤ 0.3 đơn vị — nhỏ, nhưng là một thay đổi có thật, ghi ra để không ai tưởng lệch.

⚠ **Định dạng số đổi**: script bỏ số 0 thừa (`9,20.3` thay `9.0,20.3`; `A1.2,1.2` thay `A1.20,1.20`). Hình học y hệt, **chuỗi khác byte** ⇒ `CarFrames.kt` hiện tại KHÔNG còn khớp từng ký tự với icon mới; phải thay bằng chuỗi sinh (mục 4).

## 4 · Việc của tầng tích hợp (agent A) — không tự làm ở lượt này

1. **`CarFramesGenerated.kt`** → đặt vào `app/src/main/java/com/byd/clusternav/launcher/`. Hai bài canh sẽ đỏ nếu để nguyên:
   - `CarFramesSourceContractTest.chi CarFrames duoc giu chuoi path` quét mọi `.kt` ≠ `CarFrames.kt` tìm literal `"M…"` ⇒ hoặc **gộp nội dung sinh vào chính `CarFrames.kt`** (khuyên: `CarFrames` giữ API `topFrame/wheel/part/…`, đọc chuỗi từ `PIECES`), hoặc thêm `CarFramesGenerated.kt` vào danh sách được phép.
   - `moi thanh vien cong khai cua CarFrames deu co cho goi` (regex `\n    (?:fun|val) (\w+)`) ⇒ `PIECES`, `HIGHLIGHT_REF`, `PAINT_STOP_*` phải có chỗ gọi (CLAUDE.md §8).
   - Chuỗi icon gộp = **các chuỗi mảnh nối bằng MỘT dấu cách theo thứ tự** (vd. lớp PHỤ của icon mặt trên = `windscreen + " " + glass_rear`). Bài `tron bo icon lop tung goc` (3 path: thân · kính · bánh) vẫn đúng số 3 nhưng path kính là chuỗi gộp ⇒ đổi phép so sang "mỗi pathData là chuỗi mảnh hoặc chuỗi nối của các mảnh".
2. **`IconStyleContractTest`**: icon mới dùng nét **1.8** (chính) / **1.2** (phụ) và `fillAlpha`/`strokeAlpha` **0.16 / 0.38** (AC2.1 · AC2.2). Bài hiện tại ghim `strokeWidth = "1.6"` duy nhất ⇒ phải cập nhật cùng lượt với bộ glyph của B1 (cùng ngữ pháp). `fillType="evenOdd"` chỉ ở `ic_car_top_sunshade`.
3. **Thân icon = một path vừa tô (0.16) vừa nét (0.38, 1.2)** — trước đây thân chỉ nét 1.6. Đây là lớp NỀN §4.2; nếu owner thấy icon "đục", hạ `ALPHA_CTX` ở `gen-car.py` rồi sinh lại (một chỗ).
4. **`CarPartStyle` (:core, §4.4)** — ánh xạ vai → token; đề nghị: `paint`→`carPaintFrom/To` · `glass`→`partFill` (NEUTRAL) / `ACCENT_WASH` / `AMBER_SOFT` / `RED`α.35 · `flap`→không tô / `ACCENT_SOFT` / `AMBER`α.30 / `RED`α.40+nét · `tyre`→`partFill` / `ACCENT` / `AMBER` / `RED` · `lamp` `drl` `turn` `tail`→nét `MUT2` khi tắt, tô `ACCENT`/màu đèn khi bật · `mirror`→`ACCENT_SOFT` khi gập · `panel`(bonnet/boot/door side)→hairline `partLine` · `glow`/`glowtail`/`shadow`→trang trí, chỉ ở mặt 48dp/Canvas, ẩn khi `available=false` · `highlight`→ẩn / `ACCENT_LINE` / `AMBER` / `RED` · `rim`/`plate`/`shade`→trung tính. **Sơn ĐỎ** (`paint.json` → `alertToneOverride: "amber_outline"`): tone ALERT đổi sang **AMBER + viền tĩnh**, nếu không "xe đỏ" đọc thành "xe đang báo lỗi".
5. **`forceOutline` ([ĐO] `paint.json`, sàn 3:1, đo cả hai đầu)**: nền TỐI — chỉ *Trắng ngọc trai* qua (đáy 6.74); titan 1.61 · đen 1.06 · kachi 1.97 · đỏ 1.94 **phải bật viền**. Nền SÁNG — pearl **đỉnh 1.03** phải bật viền; 4 màu kia qua. ⇒ app bật `partLine` theo cờ này, đúng AC8.5, không cấm chọn.
6. **Mặt 48dp `car_face_*.xml`**: gradient `<aapt:attr>` đã [ĐO] render đúng API 29 ở T4 (icon hoa anh đào) nhưng **4 tệp này chưa qua một lượt build** — agent A build rồi chụp máy ảo. Màu sơn trong tệp là mặc định pearl; đổi sơn lúc chạy = `VectorCarArt` vẽ Canvas từ `PIECES` (Shader theo `Piece.bounds`, cùng công thức script) — **không** `setTint` (đè cả hình).
7. **Chiếc xe thứ ba còn sống**: `CarMiniView.kt` (widget *Trạng thái xe*, `WidgetViews.kt:447`) vẫn `drawRoundRect` hệ 150×250 — spec AC1.4 nêu `SideBoardView` (đã xoá) nhưng bỏ sót cái này. Bốn bảng thật hôm nay: `TyreBoardView` · `DoorBoardView` · `CarMiniView` (+ widget lốp gộp P3 sắp có). `CarMiniView` nên đổi sang `VectorCarArt(top)`.
8. Khi tích hợp xong: `python3 scripts/design/gen-car.py --check` phải OK; chép `design/out/car/drawable/*.xml` → `res/drawable/` (thay 43 tệp `ic_car_*` cũ, thêm 4 `car_face_*`), giữ tên.

## 5 · Quyết định thiết kế đã lấy (để không ai làm lại)

- Giữ NGUYÊN hình thân/bánh/vạt cửa/gương/ca-pô mặt trên (delta 0) — bảng lốp/cửa không phải đo lại; chỉ kính/nóc/rèm/cốp xếp lại để không đè nhau khi bảng vẽ **nhiều bộ phận cùng lúc** (trước đây mỗi icon chỉ vẽ một bộ phận nên không ai thấy sunroof đè kính lái).
- Mặt SAU không phải SVG thứ tư (AC1.1 = đúng 3 tệp): bóng thân đối xứng, lớp `layer-rear` trong `front.svg` giữ đèn hậu/biển số/sương mù sau; `windscreen` đóng vai kính hậu khi nhìn từ sau.
- Xi-nhan · sương mù · đèn hông **không** nằm trong mặt "nghỉ" (`FACE_VD`) — chúng là lớp trạng thái; ngắm mặt xe với hai mũi tên cam sáng trông như đang bật cảnh báo.
- Sương mù + đèn hông bỏ khỏi mặt TRÊN (dưới cản/hông, từ trên không thấy) để lọt trần 28 path; quầng đèn = 1 ellipse toả cho cả cụm (gradient VectorDrawable chỉ có một tâm/path).
- Icon 24dp vẫn **một tông `#FFFFFF`**, tint được (T8b "icon 24dp không đổi hợp đồng tint"); chiều sâu/màu chỉ ở mặt 48dp + Canvas.
- Trình xem trước Pillow: gradient chéo của kính vẽ đúng công thức VD (theo hộp bao mảnh); nét không có miter — đủ để ngắm, không dùng làm bằng chứng render Android.

## 6 · Chưa làm / cần ai

- 🔲 Build + chụp máy ảo 4 mặt 48dp và 43 icon (agent A, gradle).
- 🔲 `CarPartStyleTest` · `CarArtSource`/`VectorCarArt` · widget lốp gộp (T8/T8b) — ngoài phạm vi B2.
- 🔲 Nếu B1 chốt `design/icon-grammar.json` có họ màu theo lĩnh vực, thêm mục `car` (bạc thân · lam kính · trắng-lam đèn · đỏ hậu · cam xi-nhan) cho AC2.5 — các hex đó hiện nằm trong SVG (`data-role` + fill), không trong Kotlin.
- ❓ Owner: mặt trên hai tai gương (giữ nguyên U7) trông to so với xe có khối — nếu chê, chỉ sửa `mirror` trong `top.svg` rồi sinh lại (bảng cửa lấy `partBounds(MIRROR)` nên tự theo).
