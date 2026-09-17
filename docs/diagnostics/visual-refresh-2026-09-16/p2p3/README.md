# VISUAL-REFRESH P2 + P3 — bằng chứng đo (2026-09-17 · Kachi **1.70 · 71** · máy ảo `emulator-5554` API 29, 1920×1080)

Spec: [`../../../specs/kachi-visual-refresh.html`](../../../specs/kachi-visual-refresh.html) — T4 (đường ống) · T5 · T6 · T7 · T8 · T8b ·
T9 · T10 · T11 · T12 · R1 · R2 · R8 AC8.3. Owner: *"xây lại toàn bộ icon … thật đẹp"* · *"nó visual thật sự không? … 3D gì
không?"* · *"riêng cái lốp … gôm lại thành 1 widget có hình xe đẹp"* · *"cho gradient hay làm sao cho đẹp được thì làm"*.

Thư mục anh em: [`../p2/`](../p2/) (contact sheet 85 glyph × 3 cỡ × 3 nền + `contrast-families.md`, sinh bởi
`scripts/design/icon-audit.py --sheets`) · [`../p3/`](../p3/) (xem trước 4 mặt xe × tối/sáng, 5 màu sơn, 43 icon xe — Pillow,
`scripts/design/render-car.py`; **không phải** render Android) · [`../contrast-table-paint.md`](../contrast-table-paint.md)
(sinh bởi `CarPaintContrastContractTest`).

## 0 · Đường ống — MỘT nguồn, HAI đích (§4.6) [ĐO]

| Nguồn (người vẽ sửa ở đây) | Script (chỉ nó được ghi đích) | Đích |
|---|---|---|
| `design/glyph/*.svg` (85) + `design/icon-grammar.json` | `scripts/design/gen-icons.py` | `res/drawable/ic_*.xml` 85 × 24dp + 21 `large` × {`_l` 32dp, `_xl` 48dp} = **127** |
| `design/car/{top,front,side}.svg` (đúng 3, AC1.1) + `paint.json` | `scripts/design/gen-car.py` | `res/drawable/ic_car_*.xml` **43** + `car_face_{top,front,rear,side}.xml` **4** · `CarFramesGenerated.kt` · `design/car/manifest.json` |

`--check` của cả hai sinh lại vào thư mục tạm rồi **so byte** với đích; `IconStyleContractTest.tep sinh khop byte voi nguon`
gọi hai lệnh đó mỗi lượt test (bỏ qua có nhắn nếu máy không có `python3`). [ĐO] sửa 1 ký tự `ic_ac.xml` ⇒ exit 1.
Bốn tệp `ic_*` chưa từng compile được qua AGP trước lượt này: `android:fillColor` phẳng **đi kèm** `<aapt:attr>` ⇒
`ResourceCompilerRunnable` "Cannot find attribute fillColor" (aapt2 thuần thì nuốt) — script nay bỏ thuộc tính phẳng khi có gradient.

## 1 · Ảnh máy ảo — nhìn cái gì

Cùng AVD · hồ sơ *Mặc định* · xe không nối (mọi datum `—`) · **hình nền TẮT** trừ ảnh cuối. Ô 0 = nhóm *Cửa & khoang*
(`DoorBoardView`), ô 1 = widget *Áp suất lốp* (`TyreBoardView` — **một widget hình xe**, bốn ô số + nhiệt từng bánh, đúng
yêu cầu owner), ô 2 = widget *Trạng thái xe* (`CarMiniView` — chiếc xe thứ ba đã đổi sang cùng nguồn), ô 3 = nhóm *Khí hậu*.

| Ảnh | Nhìn cái gì |
|---|---|
| `home-4o-toi.png` · `home-4o-sang.png` | **Mức tả thực (1)** §4.8 trên cả ba bảng Canvas: thân xe chuyển sắc DỌC màu sơn (ngọc trai), kính có phản chiếu lam nhạt→sẫm, quầng đèn trước (trắng-lam) và đèn hậu (đỏ đèn) bằng gradient toả, bánh xe mảng mờ. **0** blur/shadow/elevation (bài canh). Bảng cửa: 8 bộ phận chưa đọc ⇒ nét DIM, không bịa đóng. |
| `home-2cot-*.png` · `home-1o-*.png` | Cùng chiếc xe ở ô 2 cột và ô 1 (bảng lốp phóng lớn: bốn ô TT/TP/ST/SP bám đúng bánh; kính, đèn, quầng theo tỉ lệ). |
| `home-4o-toi-paint-red.png` | Màu sơn **Đỏ**: thân đỏ, kính/đèn/bánh KHÔNG đổi (sơn là trang trí, §4.8 (b)); [ĐO] `contrast-table-paint.md`: đáy 1.94:1 trên nền tối ⇒ viền `partLine` **tự bật**. Sơn đỏ + ALERT ⇒ hổ phách + viền (bảng `CarPartStyle`, không có dữ liệu ALERT off-car để chụp). |
| `home-4o-sang-paint-black.png` | Sơn **Đen bóng** trên bảng SÁNG: đậm hơn nền, viền tự bật (đáy 1.06 trên nền tối / đỉnh qua trên nền sáng). |
| `sand-4o-toi.png` | Hình nền cát của P1b + hình xe mới + icon mới: thẻ vẫn là cửa sổ kính, xe không tan vào ảnh. |
| `picker-climate-toi.png` · `picker-climate-sang.png` | **AC2.6**: bộ chọn nút — ô *Khí hậu* đang chọn = đủ màu (lam-xanh, chuyển sắc) trên nền ACTIVE; tám ô chưa chọn = hạ bão hoà 35 % + mờ 72 % (`ColorMatrixColorFilter`, KHÔNG `setTint` đơn sắc). Biến thể **48dp** (`_xl`, có đĩa nền) ở ô 44dp. Bảng SÁNG: mọi icon tint INK (họ màu `main`/`light` < 3:1 trên nền sáng — `p2/contrast-families.md`). |
| `settings-color-paint-toi.png` · `settings-color-paint-sang.png` | Cài đặt › Hiển thị & đơn vị › Màu sắc: hàng **Màu sơn xe** (5 ô, ✓ Trắng ngọc trai) dưới hàng màu nhấn + tông thẻ; lưu theo hồ sơ (`color_choice` mảnh thứ 3). |

**Sáu icon vẽ lại** so với lượt B1 (yếu ở 24px, parent chỉ ra): `engine` (khối bậc thang + trục), `mode` (núm xoay + cung nấc),
`group_windows` (xe nhìn ngang + hai ô kính), `pedal` (ba gờ chống trượt), `torque` (đai ốc + mũi tên cong), `hood` (nắp bật ở mũi xe).
Chín glyph được `scripts/design/fit-glyph.py` co/dịch vào ô quang học 2..22 / cạnh ≥ 16 (cùng luật `IconGeometryContractTest`,
script sinh nay kiểm y hệt: dung sai 0.05, cạnh ≥ 16 — bản B1 để 0.35/14).

## 2 · Số đo [ĐO]

| Đại lượng | Số |
|---|---|
| `ic_*.xml` trong `res/drawable` | **182** (140 → 140 vẽ lại + 42 biến thể) + 4 `car_face_*` |
| trần path | glyph 24dp ≤ 6 · biến thể ≤ 10 · khung mặt xe TOP 27 · FRONT 19 · SIDE 18 (trần 28) · 43 icon xe ≤ 6 — bài canh đếm |
| Δ APK vehicleTest (P1b → P2/P3) — **phần tài nguyên drawable nén** | 95 769 B → 190 410 B = **+94.6 KB** (207 → 409 mục) |
| Δ APK vehicleTest — tổng byte nén trong zip | 40 635 255 → 40 816 070 = **+180.8 KB** (trần AC5.5 +350 KB ✅). ⚠ Kích thước TỆP thì 43 659 880 → 40 917 966 (−2.7 MB): bản P1b được dựng ở lượt khác (căn chỉnh/ký), không so được — dùng hai số trên. |
| Test | `:core` **2169**/0 (2161 + 8 `CarPartStyleTest`) · `:app` **1125**/0 (1111 + 14: `IconSetInventoryTest` 2 · `CarPaintContrastContractTest` 3 · `CarArtSourceContractTest` 4 · `CarFramesSourceContractTest` +… · `IconStyleContractTest` viết lại) — đếm từ JUnit XML |

## 3 · gfxinfo — kịch bản `perf-pass4` (cuộn Cài đặt, ≥ 600 khung), 3 lượt, KHÔNG hình nền

| Lượt | Khung | Janky | 50th | 90th | 95th | 99th |
|---|---|---|---|---|---|---|
| 1 | 675 | 26.07 % | 8 ms | **36 ms** | 61 ms | 150 ms |
| 2 | 657 | 20.70 % | 8 ms | **32 ms** | 61 ms | 150 ms |
| 3 | 648 | 18.52 % | 7 ms | **19 ms** | 26 ms | 53 ms |

Cùng kết luận §3 của `../README.md`: biên độ giữa ba lượt (17 ms) lớn hơn nhiều ngưỡng ±1.5 ms ⇒ máy ảo **không kết luận được**;
kịch bản này không đi qua bảng Canvas hình xe (màn Cài đặt). **Cơ chế** (để biết chờ đợi gì trên xe): mỗi bảng dựng
`CarArtPainter` **một lần**, shader (5 `LinearGradient`/`RadialGradient`) dựng **chỉ khi khung đổi cỡ** (`layout`), `draw`/`drawPart`
0 cấp phát (bài canh quét thân hàm cấm `Matrix(`/`Path(`/`RectF(`/`Paint(`/`*Gradient(`). Số thật: T14 🚗. Tệp thô: `gfxinfo-p2p3.txt`.

## 4 · Cách chạy lại

Scratch của phiên (`p2p3/shot.sh` = đặt prefs `run-as` → `am force-stop` ×2 → `monkey HOME` → chờ 12 s → `screencap`;
`ui.sh` = đường UI cố định Cài đặt → *Hiển thị & đơn vị* → cuộn 3 lần; bộ chọn: (1655,66) → (256,296) → cuộn 3 → (634,895) →
cuộn → (739,433) bật *Khí hậu*, BACK ×2 không Áp dụng). Sinh lại tài sản: `python3 scripts/design/gen-icons.py && python3
scripts/design/gen-car.py`; kiểm: `--check`; contact sheet: `python3 scripts/design/icon-audit.py --sheets`; xem trước xe:
`python3 scripts/design/render-car.py`.
