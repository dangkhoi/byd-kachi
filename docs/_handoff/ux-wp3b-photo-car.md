# WP3-v5 — Bỏ vector car → ẢNH bitmap (feather) + cốp/rèm bỏ chữ · XONG off-car, CHƯA commit

> **Trạng thái**: code + test xong · 5 module **5078 test / 0 đỏ / 0 lỗi / 0 bỏ qua** · đã chụp máy ảo API 29 · **Cập nhật**: 2026-09-20
> **Spec**: `docs/specs/kachi-ux-overhaul.html` §WP3-v5 (Reviewer Log) + changelog v5
> **Ảnh owner duyệt**: `docs/diagnostics/ux-overhaul-2026-09-20/wp3b-home-dark.png` (API 29, dark)
> **KHÔNG commit** (theo lời giao). CHỈ WP3-v5 (bỏ vector car). WP1/WP2/WP3-cũ vẫn trong cây làm việc chưa commit.

---

## 1. Owner giao gì → làm gì

| # | Owner | Đã làm |
|---|---|---|
| A | Bỏ vector car (chê xấu/tốn token) → **ẢNH bitmap** top-down, đọc từ thư mục máy `files/car/` (mặc định `images/seal.png` 408×624), **user thay được** (cơ chế như hình nền), **feather mờ cạnh** cho tiệp nền (KHÔNG blur runtime — xe API 29). Gỡ vector + multi-model. Overlay trạng thái (cửa/cốp/lốp = chấm) vẽ trên ảnh. | `CarImageStore` + `CarImageLayer` (:app) đọc `getExternalFilesDir/car/`, nạp giảm cỡ (dùng lại `WallpaperStore.loadScaled`), **feather = 4 gradient DST_OUT** tính 1 lần. `TyreBoardView`/`DoorBoardView`/`CarMiniView` viết lại: ảnh + chấm màu tại **neo `:core CarLayout`** + placeholder silhouette mờ khi trống. GỠ vector car (11 tệp). |
| B | Cốp/rèm bỏ chữ Đóng/Mở/Nửa → active/mờ hoặc **vạch như ghế**, KHÔNG chữ. | `ControlTileFactory.tileCover` = MỘT tile text-free: ≤2 mức = toggle active/mờ; ≥3 mức = `ControlLevelBar` (vạch). Gỡ `coverButtons`/`coverCycle`/`miniBtn`. |
| — | `images/` vào gitignore (ảnh bản quyền không vào repo public). | `.gitignore` += `images/` (mục riêng có lý do). |

---

## 2. GỠ (11 tệp) + GIỮ

**Xoá** — :core `CarModel` · `CarPartStyle` (CarFace/CarPartRole/CarInk/PartLook/CarPaint) (+2 test) · :app `CarArtPainter` (KachiCarPaint/CarPaintLook) · `CarArtSource`/`VectorCarArt` · `CarFrames` · `CarFramesGenerated` (+3 test: CarArtSource/CarFramesSource/CarPaintContrast).

**GIỮ**: `ic_car_top_*.xml` (icon nút cửa/lốp, dùng ở tile — grep xác nhận). `gen-car.py` trim phần car-body (không sinh `CarFramesGenerated.kt` + `car_face_*.xml` nữa), **giữ phần icon** — `--check` OK 44 tệp. `car_face_*.xml` (4 mặt 48dp) đã xoá cùng.

**Sửa**: `ColorChoice` (bỏ `paint`/`model`, còn accent+tone; decode tha chuỗi cũ `ACCENT;TONE;paint;model`) · `KachiTheme` (bỏ `carPaint`/`carModel` + change-detection) · `KachiPaletteSeeds` (bỏ `CAR_PAINTS`) · `SettingsSections.color()` (bỏ picker sơn+model, thêm hàng "Hình xe") · `WidgetViews` (không đổi call-site — `CarMiniView.set` giữ chữ ký).

---

## 3. ⚠⚠ QUYẾT ĐỊNH SCOPE — cần owner xác nhận

**Gỡ LUÔN paint-picker (màu sơn xe) — owner KHÔNG nêu tường minh.** Owner liệt kê gỡ "CarArtPainter · CarFrames · gen-car car · CarModel/model-picker". Paint-picker (5 màu sơn) KHÔNG trong danh sách. Nhưng: (1) màu sơn **chỉ tô thân vector** — với ẢNH nó thành **nút chết** (steering cấm); (2) `KachiCarPaint` sống trong `CarArtPainter.kt` (bị xoá) ⇒ giữ paint = phải di chuyển nó + giữ 3 test tô-một-nút-chết. ⇒ gỡ trọn (CarPaint, CAR_PAINTS, 3 hàng picker). **Dễ khôi phục từ git nếu owner muốn giữ** — nói một câu.

**Ảnh xe KHÔNG có khoá pref "chọn tệp" — dùng "ảnh mới nhất trong thư mục thắng".** Owner ghi "Lưu pref (DEVICE-scoped)". [ĐO] `SettingsCoverageContractTest` quét MỌI `getSharedPreferences`/`put*` và đòi khoá phải xếp loại ở `ProfileScope`+`SettingsCatalog` (coupling nặng). Thư mục riêng của app **đã là device-wide** (không đi theo hồ sơ), nên `imagePath()` = tệp mới nhất trong `files/car/` = đúng "theo XE" mà **không cần một khoá lưu bền nào**. "Chọn/thay" = thả tệp mới vào thư mục (như hình nền). Chọn tường minh giữa NHIỀU ảnh là follow-up nhỏ nếu owner cần — backlog.

---

## 4. Kiến trúc ảnh (mẫu HÌNH NỀN)

- `CarImageStore` (:app, 133 dòng) — `folder`/`folderHint`/`imageNames`/`imagePath` (mới nhất trước) · `signature` (path|mtime, cho cache) · `loadFeathered(ctx,w,h)` (giảm cỡ qua `WallpaperStore.loadScaled` + feather) · `feather(src)` (4 `LinearGradient` DST_OUT, mép đục→trong suốt, tính 1 lần). Mặt nạ dùng `0xFF000000.toInt()` (alpha-only, KHÔNG `Color.BLACK` — ThemePaletteContractTest cấm; DST_OUT chỉ đọc alpha, RGB vô nghĩa).
- `CarImageLayer` (:app, 139 dòng) — component 3 bảng dùng chung: `ensure(w,h)` nạp nền (executor daemon) + cache theo (signature·bucket) + thẻ thế hệ bỏ lượt cũ · `draw`/`contentRect` (letterbox giữ tỉ lệ) · `drawPlaceholder` (silhouette mờ DIM, 0 viền) · `release`.
- `:core CarLayout` (50 dòng, thuần) — neo chuẩn hoá `CarPart`/`TyreCorner` → (x,y)∈[0,1] (front trên). Test `CarLayoutTest`: trong [0,1] · trái/phải đối xứng · đầu trên đuôi dưới · không trùng chỗ. Neo **xấp xỉ** (chỉ báo trạng thái, không phải sơ đồ) ⇒ hợp mọi ảnh top-down.

---

## 5. [ĐO] Bằng chứng

| Phép đo | Kết quả |
|---|---|
| `./gradlew test --rerun-tasks --continue` (JAVA_HOME openjdk@17) | **5 module · 5078 test · 0 đỏ · 0 lỗi · 0 bỏ qua** (đếm JUnit XML: app 2456 · core 2440 · car-int 61 · offcar 99 · vehicle-contracts 22) |
| `python3 scripts/design/gen-car.py --check` | **OK — 44 tệp khớp byte** (icon + manifest + paint; car-body đã gỡ khỏi script) |
| `:core` thuần | `CarLayout`/`ColorChoice`: **0** import `android.*` |
| Trần 500 dòng | CarImageStore 133 · CarImageLayer 139 · TyreBoardView 181 · DoorBoardView 117 · CarMiniView 66 · ControlTileFactory 471 · SettingsSections 439 |
| 0 viền/blur/shadow | grep `BlurMaskFilter/setShadowLayer/RenderEffect/elevation/Style.STROKE/setStroke` trong 5 tệp view mới = **NONE** |
| Dangling refs deleted symbols | **0** trong `:core`/`:app` main source |

**Test đã cập nhật cho khớp**: `ColorChoiceTest` (bỏ paint/model, giữ ca chuỗi-cũ-tha-thứ) · `ColorChoiceContractTest` (bỏ ca `doi mau son`) · `ControlStateUxContractTest` (`WP3v5 COVER la MOT tile text-free`) · `GroupTileTightSpaceContractTest` (bỏ ca coverCycle) · `GroupTileWiringContractTest` (door nối qua `CarLayout.part(` thay `CarPart.`) · `ZeroBorderContractTest.INK_VIEWS` (gỡ CarArtPainter + DoorBoardView — hết dùng STROKE) · `LayeringRulesTest` (gỡ entry CarFramesGenerated).

**Ảnh** (`docs/diagnostics/ux-overhaul-2026-09-20/`, `emulator-5554` API **29**, 1920×1080 = cấu hình xe, dark):
- `wp3b-home-dark.png` — **Cửa & khoang** (ảnh xe feather + "8 parts · not read yet" + hàng nút cover text-free) · **Lốp** (ảnh xe + 4 ô giá trị TT/TP/ST/SP) · **thanh nút**: Tailgate (cover 2-mức → toggle, KHÔNG chữ) · **Sunshade** (cover 3-mức → **VẠCH**, KHÔNG chữ) · Seat ventilation (vạch) — Task B rõ.

⚠ **`wp3b-home-dark.png` CHỨA ảnh render của `seal.png` (bản quyền BYD)** — nếu owner commit WP3-v5 lên repo public thì gitignore/thay ảnh screenshot này trước. Phiên này KHÔNG commit nên chưa rủi ro.

⚠ **Lượt soát KHÔNG độc lập** — phiên không có công cụ sinh sub-agent; ảnh đọc qua bản thu nhỏ ≤1000px (luật `image-reading-subagent.md`). Owner nhìn ảnh gốc là bước xác nhận cuối. Máy ảo đã **trả về cấu hình gốc** (slot w_board/w_energy, dock cũ) + xoá tệp tạm.

---

## 6. Chưa làm / owner quyết
- **Xác nhận gỡ paint-picker** (§3) — giữ hay bỏ.
- **Chọn tường minh nhiều ảnh xe** (§3) — hiện dùng ảnh mới nhất; thêm chip chọn = follow-up (cần khoá pref device qua ProfileScope/SettingsCatalog).
- Chưa chụp màn Cài đặt › Màu sắc (hàng "Hình xe") — **verify bằng code+test** (`ColorChoiceContractTest.chon mau noi day` còn xanh: swatchRow accent + onColorChoice + CardTone).
- Chưa đo trên xe thật: feather trên cụm · ảnh owner tự thay · neo chấm có rơi đúng vùng với ảnh khác `seal.png` không (neo là xấp xỉ — owner tinh chỉnh `CarLayout` bằng mắt nếu lệch).
- `gen-car.py` còn hàm chết `gen_kotlin`/`gen_face_vd`/`FACE_VD`/`KT_PATH` (không gọi nữa) — dọn ở lượt sau (không ảnh hưởng `--check`).
- Chưa commit / chưa bump version / chưa OTA (theo lời giao).
