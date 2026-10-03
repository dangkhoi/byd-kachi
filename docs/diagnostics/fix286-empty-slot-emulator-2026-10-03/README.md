# FIX286 R-ES — ô trống trong suốt: bằng chứng máy ảo

> **Trạng thái**: Current · **Cập nhật**: 2026-10-03 · **Loại**: Diagnostics · **Owner**: dangkhoi ·
> **Mục đích**: số đo máy ảo cho luồng R-ES của 2.86 (khung trống trong suốt, ⇄ tô theo nền, bố cục toàn ô trống giữ trống) — nghiệm thu ES1–ES6 và câu hỏi OQ8 (phương án B) cho owner.
> **Spec**: `docs/specs/kachi-286-field-fixes.html` §3.4 · §9 luồng R-ES · **Backlog**: `FIX286-EMPTY-SLOT`

Mức bằng chứng theo CLAUDE.md §2: `[ĐO]` = output thật của lần chạy này · `[SUY]` = đọc nguồn, chưa chạy · `[CHƯA BIẾT]`.

Máy ảo `emulator-5554` (Android 10, API 29, 1920×1080, 240 dpi), bản vehicleTest dựng từ cây làm việc 02–03/10 (gồm cả ba luồng
R-SR/R-VK/R-PI, chưa commit, versionName vẫn 2.85 — không bump). Hồ sơ thử `Test` riêng; mọi prefs của máy ảo sao lưu trước và
trả lại sau (so từng tệp: 9/9 trùng byte), thư mục ảnh nền trả lại 3 tệp cũ (mtime đổi ⇒ bộ đệm ảnh mờ tự tính lại).
Ảnh nền thử là ảnh **tổng hợp** (trắng · xám `#808080` · đen · "nhiều chi tiết" = khối ngẫu nhiên 40 px + sọc đen/trắng 2 px
mỗi 12 px), không dữ liệu thật.

## 1. ES5 — bố cục toàn ô trống giữ trống

| Bước | 2.85 (trước lượt này) | 2.86 (lượt này) |
|---|---|---|
| Hồ sơ `Test` chưa từng lưu (0 khoá `Test__slot_*`) | 3 widget mặc định | 3 widget + log `[ws-default] hồ sơ «…»: chưa từng lưu ⇒ bố cục mặc định` (hồ sơ `ES moi` thêm thẳng vào prefs, không khoá) |
| Xoá cả 3 ô (`slot_clear` 1..3) ⇒ đĩa có `Test__slot_0..5` = `""` | trống | trống |
| `am force-stop` + mở lại | **3 widget quay lại** `[w_board, w_energy, w_pm25]` — mốc ĐỎ [ĐO] | **vẫn trống** `['', '', '']` [ĐO] |

Gốc [ĐO git]: `defaultIfEmpty` có từ `b85d33e` (B5a). Không phải hồi quy của 2.84/2.85.

## 2. ES1 — khung trống trong suốt (so điểm ảnh)

`pixcheck.py`: hai điểm ảnh cách nhau ~17 px hai bên mép ô trống (ngoài = khe giữa ô, là `WallView`; trong = lòng ô trống), ba cặp
mép + một cặp khe↔giữa ô. Ảnh đồng màu: so thêm với màu kỳ vọng `màu × (1 − làm tối)`.

| Chủ đề × ảnh | Lệch kênh lớn nhất trong/ngoài | Lệch so màu kỳ vọng |
|---|---|---|
| Tối × không ảnh | 0 | — |
| Sáng × không ảnh | 1 (vầng sáng gradient của `WallView`) | — |
| Tối/Sáng × trắng/xám/đen × 0/45/90 % (18 ca) | **0** ở mọi ca | ≤ 1 |

⇒ [ĐO] lòng ô trống = điểm ảnh `WallView` (2.85: nền `emptyFill` đục — ảnh `a1`).

## 3. ES2/ES3 — tương phản ⇄ ô trống (phương án A)

**Cách đo (v2 — dùng để nghiệm thu)**: ảnh THREE (ô 1 · 2 · 3 trống; ⇄ ô 1, 2 nằm trong dải che đỉnh màn y≈115–145, ô 3 ngoài
dải) + ảnh tham chiếu cùng cấu hình ở bố cục TWO_COL — ở đó toạ độ của ba nút là lòng ô trống trong suốt, không có nút ⇒ đúng
điểm ảnh `WallView` **dưới** icon. Màu icon = màu `SwapTintBinding` ghi log (`[slot-empty] ⇄ @x,y màu …`); đối chiếu trên ảnh
THREE: lệch 0 ở mọi ca (màu log = màu vẽ). Đo 30×30 điểm dưới icon: tương phản so trung bình (μ), tệ nhất (min), % điểm ≥ 3:1.
Dữ liệu: `v2-ref-grid-SHIP.jsonl` (26 cấu hình × 3 nút = 78 ca).

| Ảnh | Làm tối | Tối: μ / min / % điểm ≥ 3 | Sáng: μ / min / % điểm ≥ 3 | Kết |
|---|---|---|---|---|
| không ảnh | — | 14.55 / 14.55 / 100 | 15.17 / 15.16 / 100 | ✅ |
| trắng | 0 % | 15.38 / 13.63 / 100 | 18.17 / 18.17 / 100 | ✅ |
| trắng | 45 % | 4.83 / 4.41 / 100 | 5.47 / 5.47 / 100 | ✅ |
| trắng | 90 % | 15.18 / 15.16 / 100 | 10.17 / 7.61 / 100 | ✅ |
| xám | 0 % | 3.87 / **3.59** / 100 | 4.60 / 4.60 / 100 | ✅ |
| xám | 45 % | 8.10 / 8.10 / 100 | 5.58 / 4.45 / 100 | ✅ |
| xám | 90 % | 16.81 / 16.72 / 100 | 11.93 / 8.90 / 100 | ✅ |
| đen | 0 · 45 · 90 % | 18.07 / 17.88 / 100 | 13.75 / 10.54 / 100 | ✅ |
| nhiều chi tiết | 90 % | 15.97 / 15.31 / 100 | 11.14 / 7.73 / 100 | ✅ |
| nhiều chi tiết | 45 % (mặc định) | 4.59 / 3.01 / 100 | 3.36 / **2.20** / **32** (ô 1, hàng trên) | ❌ sáng |
| nhiều chi tiết | 0 % | **2.60** / 1.06 / **8.9** (ô 2) | 3.59 / 1.01 / **17.6** (ô 2) | ❌ |

(Mỗi ô bảng là giá trị THẤP NHẤT trong ba nút của cấu hình đó; đủ 78 ca ở tệp JSONL.)

- [ĐO] Ảnh đồng màu + không ảnh, cả hai chủ đề, mọi mức làm tối: **mọi điểm ảnh dưới icon ≥ 3.59:1** — A đạt.
- [ĐO] Ảnh tổng hợp nhiều chi tiết: không đạt ở làm tối **0 %** (cả hai chủ đề) và **45 % chủ đề sáng, hàng trên** (dải che sáng
  của thanh trên chồng lên sọc tối). Không một màu đơn nào đạt 3:1 trên cả điểm ảnh sáng lẫn tối của cùng một vùng
  30 px — đó là giới hạn của "không nền", không phải lỗi chọn màu.
- [ĐO] Đã thử thay lưới 16×9 bằng ảnh mờ ¼ độ phân giải (v3, `v3-ref-blurred-BO.jsonl`): KHÔNG hơn — một ô tốt lên, một ô tệ đi,
  mọi ca khác y hệt ⇒ **bỏ**, mã giữ lưới. [SUY] ảnh ¼ dựng bằng `drawBitmap` lọc song tuyến nên chi tiết < ~4 px răng cưa ở cả
  hai nguồn.
- v1 (`v1-ring.jsonl`) đo nền bằng vòng quanh icon trong khung chạm — đúng với ảnh đồng màu (khớp v2), **sai** với ảnh chi tiết
  (lấy nhầm nét sọc làm "icon"), nên chỉ giữ làm vết.

## 4. OQ8 — phương án B (đĩa kính 36 dp) để owner quyết

Theo ES3, có ca không đạt ⇒ B **cần owner duyệt bằng ảnh** (B đi ngược ba lần owner chọn "không nền": 12/09, 14/09, 25/09).
B **chưa có trong mã** — ảnh `b2`, `b4` chụp từ một bản dựng TẠM (đĩa `KachiGlass.apply(…, 18, NEUTRAL)` 36 dp sau icon, icon
`INK`), dựng–chụp–gỡ trong cùng lượt; mã nguồn đã trả về A.

| Ca | A (đang ship) | B (bản tạm) |
|---|---|---|
| Sáng × chi tiết 45 % | `b1`: ô 1 μ 3.36, 32 % điểm ≥ 3:1 | `b2`: ≥ 13.48:1 mọi điểm đĩa đo được |
| Tối × chi tiết 0 % | `b3`: ô 2 μ 2.60 | `b4`: ≥ 10.54:1 |
| Tối × xám 45 % · Sáng × không ảnh | ✅ (min 8.10 · 15.16) | ≥ 12.08 · ≥ 17.12 |

Đề xuất của luồng: giữ A (owner đã chọn không nền ba lần; mọi ảnh đồng màu và ảnh chi tiết ở 90 % đều đạt). Nếu owner thấy ⇄
khó nhìn trên ảnh thật của mình (OC-ES) thì bật B — đổi một chỗ ở `SlotSwapButton.centered`.

> **→ 2026-10-03 (sau lượt này): OQ8 đã chốt phương án B** — phiên điều phối, owner có thể đổi. B nay CÓ trong mã (đĩa là `View` riêng sau icon, icon giữ `MUT` — khác bản tạm ở trên: bản tạm tô icon `INK`); số đo B đầy đủ (114 ca, gồm sọc 3 px + hoa văn) và bản khôi phục A ở `../fix286-oq8-disc-emulator-2026-10-03/README.md`. Các số A ở §3 giữ làm vết.

## 5. Các điểm khác

| Điểm | Kết quả |
|---|---|
| ES1 điểm thả | [ĐO] `input draganddrop 588 500 1531 700`: ô 1 App (Cài đặt) ⇒ ô 3 trống; state + đĩa đổi `['', '', 'app:com.android.settings']` (ảnh `c1`). Khớp AOSP r47 `ViewGroup.java:1692,1813-1826` (đích thả theo khung + `canAcceptDrag`). |
| ES1 không chạm cả ô | [ĐO] chạm lòng ô trống (1531,300) ⇒ không mở gì (uiautomator: 0 chữ của bảng chọn). |
| ⇄ ô trống | [ĐO] chạm (1531,130) ⇒ bảng chọn mở "Đặt widget hoặc mở app vào ô này" (ảnh `c2`). |
| ES4 mô tả trợ năng | [ĐO] uiautomator: `Chọn ứng dụng cho ô 1 [552,100][624,160]` · `… ô 2 [1495,100][1567,160]` · `… ô 3 [1495,508][1567,568]` (khung chạm 48×40 dp). |
| ES6 nhật ký | [ĐO] trong `files/kachi-logs/usage-*.log`: `[slot-empty] 2 ô trống (ô 1,2) trong suốt · chủ đề=tối` · `[slot-empty] ⇄ @552,100 màu #eaf0f8 · nền L 0.046–0.061 · ảnh=on` · `[ws-default] hồ sơ «ES moi»: chưa từng lưu ⇒ bố cục mặc định`. Dòng ⇄ ghi `ảnh=off` rồi `ảnh=on` khi ảnh nạp xong (ảnh nạp trên luồng nền sau lượt dựng đầu). |
| Lỗi tìm ra NHỜ máy ảo, đã sửa | (a) dòng `[slot-empty]` đầu tiên mỗi lần tiến trình bật là "3 ô trống" GIẢ — `init { rebuild() }` chạy với state rỗng ⇒ bỏ log khỏi `rebuild()`, ghi ở mọi lượt render (khử trùng); (b) trường `ảnh=` ở dòng đếm ô báo `off` dù màn sắp hiện ảnh ⇒ chuyển sang dòng của từng nút, ghi lúc nút thật sự đo. Cả hai có bài khoá. |
| Kiểm lại trên APK CUỐI | [ĐO 03/10 00:35] sau ba sửa cuối (dời `hasStoredSlots` · bỏ log ở `setCustomLayout` · ghim `EXEMPT_LINES`): hồ sơ `Test` chưa lưu ⇒ 3 widget + `[ws-default]`; xoá 3 ô ⇒ force-stop ⇒ vẫn trống; dòng `[slot-empty]` đúng số ô ở mọi lượt; uiautomator thấy cả 3 mô tả. Prefs trả lại 9/9 trùng byte. |

## 6. Chưa biết / cần xe

- [CHƯA BIẾT] ⇄ trên **ảnh thật** của owner, ngày và đêm — OC-ES (ảnh chụp màn).
- [CHƯA BIẾT] DL5 (API 31) có bật kính thật hay không — nếu có, `WallView` tự làm mờ, ô trong suốt thấy ảnh mờ (vô hại).

## 7. Quan sát NGOÀI phạm vi (ghi backlog, không sửa)

- [ĐO hiện tượng · CHƯA BIẾT gốc] Đổi hồ sơ từ `Mặc định` (hình nền BẬT) sang `Test` (không có khoá `Test__wallpaper_prefs`)
  ⇒ ảnh nền của hồ sơ cũ **vẫn hiện** (ảnh `a1` có vân cung của `wall-1.png`; log ⇄ `ảnh=on`) cho tới lần mở lại tiến trình, khi đó
  hết ảnh (ảnh `a2`, log `ảnh=off`). Thấy trên cả bản trước lượt này lẫn bản 2.86 ⇒ không do R-ES. Backlog `WALLPAPER-PROFILE-SWITCH-STALE`.
