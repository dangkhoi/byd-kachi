# VISUAL-REFRESH P1b — hình nền là CỬA SỔ + chọn màu (2026-09-17, máy ảo `emulator-5554` API 29, 1920×1080)

> **Trạng thái**: Current · **Cập nhật**: 2026-09-17 · **Mục đích**: bằng chứng P1b — hình nền là CỬA SỔ + chọn màu (máy ảo API 29, 1920×1080).

Spec: [`../../../specs/kachi-visual-refresh.html`](../../../specs/kachi-visual-refresh.html) §4.10 · §R8 · T3b · §9 "P1b".
Owner 2026-09-16: *"cái màu đen, xám của mình, khi nhét thêm hình nền vào, nó lại không đẹp nữa"* · *"có cho người ta
chọn màu không nhỉ?"*.

Ba ảnh nền **tổng hợp** (PIL, không phải ảnh thật của owner — không đưa ảnh cá nhân vào repo): `wall-sand.jpg` (sáng,
tông cát + trời, kiểu ảnh owner đang dùng) · `wall-night.jpg` (tối, sao + chân trời tím) · `wall-contrast.jpg` (nửa
đen / nửa trắng + khối đỏ + dải lục — ca tệ nhất cho lớp che). Mọi ảnh chụp dùng hồ sơ *Mặc định*, ô 0 = nhóm *Kính*,
ô 1 = app YouTube (ô app trên máy ảo hiện thẻ tên app), hai ô còn lại trống; hình nền BẬT, "làm tối 45 %" (mặc định),
phủ kín.

## 0 · Không có ảnh nền ⇒ không đổi một byte [ĐO]

`base-4o-toi.png` (P1b, hình nền TẮT, màu mặc định) so với `../after/home-4o-toi.png` (1.69 Pass 5) tại các điểm
**bề mặt** (điểm nội dung khác nhau vì ô 1 trên máy ảo nay là YouTube, không phải nhóm Khí hậu):

| Điểm | Là gì | 1.69 | P1b |
|---|---|---|---|
| (800,320) | khay ô làm việc | (34,40,49) | (34,40,49) |
| (300,300) | thẻ nội dung trong khay | (34,41,50) | (34,41,50) |
| (960,540) | nền màn | (10,13,19) | (10,13,19) |
| (1500,900) · (100,1000) | ô trống | (11,15,22) | (11,15,22) |
| (1800,100) | thanh trên | (29,32,38) | (29,32,38) |

Cùng cửa `KachiGlass.apply` rơi về đúng `KachiTheme.surface(...)` khi `WallArtStore.current == null`
(`WallGlassContractTest.khong co anh nen thi nen the la dung KachiTheme surface nhu cu`).

## 1 · Ma trận ảnh — nhìn cái gì

| Ảnh | Nhìn cái gì |
|---|---|
| `sand-4o-toi.png` · `sand-2cot-toi.png` · `sand-1o-toi.png` | bảng TỐI trên ảnh cát: khay và thẻ là **cửa sổ** — thấy tông cát/trời **mờ** xuyên qua, không còn hình chữ nhật xám dán lên ảnh; chữ vẫn đọc được; dải che ở đỉnh (thanh trên) và đáy |
| `sand-4o-sang.png` · `sand-2cot-sang.png` · `sand-1o-sang.png` | bảng SÁNG trên cùng ảnh: cửa sổ sáng, hairline vẫn tách thẻ; lớp làm tối 45 % của hình nền vẫn là của người dùng đặt |
| `night-4o-toi.png` | ảnh tối: lớp che ở **đáy dải** (35 %), kính trong nhất — thấy sao mờ xuyên qua khay |
| `contrast-4o-toi.png` · `contrast-4o-sang.png` | ca tệ nhất: nửa trái đen / nửa phải trắng + khối đỏ. Khay bên phải (trên nền trắng) **tự đậm hơn** khay bên trái (lớp che chọn theo độ chói đo được của vùng dưới thẻ); chữ ở cả hai bên đọc được; khối đỏ mờ lọt qua khay trên-trái |
| `sand-4o-toi-cherry.png` | màu nhấn **Hồng anh đào**: chip hồ sơ + nút Cài đặt đổi họ màu, bề mặt không đổi |
| `sand-4o-toi-fromart-warm.png` | **theo ảnh nền** + tông ẤM: màu nhấn lấy từ màu trội rực nhất của ảnh cát (trời xanh-xám nhạt), thẻ ngả ấm |
| `sand-4o-sang-fromart-cool.png` | bảng SÁNG, theo ảnh nền + tông LẠNH |
| `nowall-4o-toi-amber.png` | không ảnh nền, màu nhấn **Hổ phách**: chỉ vai nhấn đổi, bề mặt = 1.69 |
| `settings-color-toi.png` | Cài đặt › Hiển thị & đơn vị › **Màu sắc**: hàng 9 ô tròn (✓ trên ô đang chọn, ô *theo ảnh nền* gạch đứt khi chưa có ảnh) + 3 chip tông thẻ |

## 2 · Bảng tương phản — sinh bằng máy

- [`../contrast-table-colors.md`](../contrast-table-colors.md) — `ColorChoiceContractTest`: **56 tổ hợp** (9 màu nhấn
  × 3 tông × 2 bảng; ô *theo ảnh nền* thử 4 ảnh cực đoan trắng/đen/đỏ rực/xám) × 62 cặp chữ/nền, dòng nào cũng ghi
  **mực tệ nhất** sau khi `ContrastGuard` tự chỉnh — mọi dòng ✅ ≥ 4.5.
- Lớp che theo độ chói: `WallGlassContractTest.lop che du cho moi do choi anh, moi tone, hai bang` đo **mọi** độ
  chói 0..1 (bước 0.05) × 3 tone × 2 bảng = 126 ca, mực tệ nhất ≥ 4.5 ở alpha tìm được (35–90 %). Đây là phép đo
  **chặt hơn** "3 ảnh nền" của DoD T3b: ba ảnh chỉ là ba điểm trên trục đó.

## 3 · gfxinfo — kịch bản `perf.sh` (như P1/Pass 4), hình nền BẬT (ảnh cát)

Xem mục 3 ở `../README.md` về vì sao máy ảo không phân giải được ngưỡng ±1.5 ms (biên độ giữa các lượt lớn hơn
hiệu số). Ba lượt liên tiếp, cùng bản, cùng kịch bản:

| Lượt | Khung | 50th | 90th | 95th | 99th |
|---|---|---|---|---|---|
| 1 | 649 | 10 ms | **36 ms** | 61 ms | 150 ms |
| 2 | 665 | 7 ms | **27 ms** | 44 ms | 150 ms |
| 3 | 671 | 8 ms | **26 ms** | 46 ms | 113 ms |

So với Pass 4 cùng kịch bản, KHÔNG ảnh nền (`../gfxinfo-pass4.txt`: 90th **21 / 23 / 20 ms**): 90th nay 26–36 ms.
Biên độ giữa ba lượt (10 ms) vẫn lớn hơn ngưỡng ±1.5 ms của §6.2 ⇒ máy ảo **không kết luận được** — nhưng chiều
là có thật: kịch bản cuộn màn Cài đặt **không có thẻ kính nào** (bảng phủ đục), nên phần chênh lệch đo được ở đây
là của WallView (vẽ bitmap + 2 dải) chứ không phải của kính. Tệp thô: `gfxinfo-p1b.txt`.

**Cơ chế** (để biết chờ đợi gì trên xe): mỗi thẻ kính thêm **một** lớp `WallWindowDrawable` = 3 `drawRoundRect`
(shader bitmap ¼ màn · lớp che · nhuộm 6 %) — không blur, không shadow, không cấp phát trong `draw()`; vị trí và độ
đục lớp che chỉ tính lại khi thẻ đổi chỗ (layout/cuộn). Ảnh mờ nấu **một lần** ở thread nền lúc nạp ảnh
(box blur ×3 trên 480×270), lượt sau đọc từ `wallpapers/.kachi-art/`. Số thật phải đo trên xe (T14 🚗).

## 4 · Cách chạy lại

Kịch bản trong scratch của phiên (`p1b/shot.sh` · `p1b/wall.sh` · `p1b/setprefs.py`): đặt prefs bằng **dữ liệu**
(`run-as` sửa `shared_prefs/kachi_workspace.xml`: `theme_mode` · `preset` · `wallpaper_prefs` · `color_choice`), chỉ
để **một** ảnh trong `Android/data/com.byd.launcher/files/wallpapers/` (ảnh đầu = ảnh duy nhất ⇒ hạt giống *theo ảnh
nền* xác định), `am force-stop` → `monkey HOME` → chờ 12 s → `screencap`. Màn Cài đặt đi bằng UI: chạm (1655,66) →
nhóm *Hiển thị & đơn vị* trên rail → cuộn tới *Màu sắc*.
