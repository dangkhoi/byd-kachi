# Widget màn nhà — lượt thẩm mỹ R22 (3.01, 2026-10-13)

> Spec: `docs/specs/kachi-301-cast-steps-dialog-ia.html` R22 (nhóm E). Owner 13/10: *"canh lệch thì OK rồi, mà có thấy nó không
> thẩm mỹ không? visual nó lên được không, nhìn thô lắm em"*; giữa lượt owner chỉ Kim Launcher làm đích (*"đẹp, elegant quá
> trời, mình làm nó quê mùa lắm"*) ⇒ hướng theo `docs/diagnostics/kim-design-brief-2026-10-13.md` (chỉ học phong cách — không
> chép ảnh/logo/tài sản của Kim). Máy ảo `kachi_play` (emulator-5556, 1920×1080, 240 dpi), Kachi 3.1 (205) `vehicleTest`,
> tiếng Việt, số giả BẬT. Ảnh ≤ 1280 px ở `widget-visual-301/` (trên = TRƯỚC, dưới = SAU).

## 1. Vì sao "thô" — [ĐO ảnh 13/10, trước khi sửa]

| # | Bệnh | Bằng chứng |
|---|---|---|
| 1 | **Hộp trong hộp**: mỗi giá trị chỉ-đọc nằm trong phiến tối riêng (Chuyến đi 3 phiến, Khí hậu 12, Đèn 4) — trông như nút bấm mà không bấm được | `01`/`02` trên |
| 2 | **Đậm khắp nơi**: số chính 34sp + số phụ 19sp + cả đơn vị (`18.2 kWh`) đều `DEFAULT_BOLD` ⇒ không chỗ cho mắt dừng | `GroupTileViews.kt:495`, `WidgetViews.kt:324`, `ReadTile.kt:123`, `RingView` giả đậm |
| 3 | **Thẻ nhuộm màu lĩnh vực**: khay Khí hậu xanh két, Đèn nâu, Chuyến đi xanh lục ⇒ đục, lệch tông nhau | `01` trên, `03` trên (ô telemetry tím/xanh két) |
| 4 | **Đầu thẻ yếu**: icon xám 16dp + tiêu đề 12sp `MUT2` giãn chữ — chìm hơn cả nội dung | mọi nhóm |
| 5 | Đại lượng có trần (pin %, SOH %) chỉ là chữ, không có thanh | Năng lượng, Sức khoẻ pin |
| 6 | **Không có không khí**: nền xanh phẳng, thẻ đục đứng như biểu mẫu (không có ảnh nền mặc định — chỉ khi người dùng tự chép ảnh) | `08` |
| 7 | Lưới ô "nhãn trên / số dưới" lơ lửng giữa thẻ (2 cột DỌC thắng 1 cột NGANG cùng bậc) | Chuyến đi/Năng lượng 2×3 |

## 2. Thiết kế — sửa ở TẦNG CHUNG

| Lớp | Tệp | Quyết định |
|---|---|---|
| Vai chữ widget (mới) | `KachiWidgetType.kt` | `hero` = Roboto **light** + `tnum` (40sp — brief 44–52 cho thẻ 324 dp của Kim; ô 2×3 Kachi ~200 dp, 46 [ĐO] đẩy mục Odo ra) · `value` = **medium** + `tnum` 16sp · `caption` 13sp regular `MUT` · `title` 14.5sp medium `INK2` · `withUnit`: đơn vị trong chuỗi số phụ 0,72× + `MUT` (brief 0,55× = 8sp — dưới sàn đọc-lúc-lái) · `accentOf(domain)` = sắc lĩnh vực của bảng bỏ alpha (cửa tra duy nhất `domainTint`) · `chip/chipIcon` chip 28 dp tô 15 % |
| Token | `KachiSpace.HEAD_CHIP` (28) · `HEAD_CHIP_PAD` (6) | chip đầu thẻ |
| Thanh mức (mới) | `KachiMeter.kt` | thanh 4 dp (`BAR_THIN`) bo tròn, chọn theo DỮ LIỆU (đơn vị `%`), số chưa đọc ⇒ chỉ rãnh; rãnh = `INK` 16 % (vai `TRACK` [ĐO] trùng mặt thẻ ở bảng sáng 228,232,241 vs 227,231,240 ⇒ tàng hình); 0 cấp phát trong `onDraw` |
| Đầu thẻ nhóm | `GroupTileViews.header` | chip icon màu lĩnh vực + tiêu đề medium mực đầy |
| Số chính nhóm | `GroupTileViews.leadRow` | light 40sp, đơn vị 16sp `MUT` riêng, nhãn caption, + `KachiMeter` khi `%` |
| Hàng giá trị | `CellBinder` (`GroupTileParts.kt`) | NEUTRAL/ACTIVE **không phiến nền** (WARN/ALERT giữ nền màu — màu nền LÀ thông tin); "Bật" = mực nhấn (`tintOf(ACTIVE)` → `ACCENT_INK`); nền/ span chỉ dựng khi tone/chuỗi ĐỔI |
| Dạng hàng | `GroupCellGrid.setForm` + `core GroupCellFit.tiers` | NGANG = nhãn ĐẦU · số CUỐI (bảng hai cột, mép số chung); mọi dạng NGANG (1 rồi 2 dòng nhãn) thử ở mọi số cột TRƯỚC dạng DỌC (đảo luật W "bậc 1 chọn DỌC" — owner 13/10, brief §2.2) |
| Thẻ/khay | `WorkspaceView.makeSlot` · `WidgetTelemetry.MiniCard` | một mặt thẻ trung tính (bỏ `slotDomain`); sắc lĩnh vực chỉ ở chip/icon |
| Widget telemetry / vòng | `WidgetViews.tv` · `WidgetTelemetry` · `RingView` · `ReadTile` | `bold` ⇒ ≥ `KachiType.DISPLAY`: light, còn lại medium; vòng nét 6,2 % → 4,2 % cạnh, số light, bỏ giả đậm |
| Nền mặc định (mới) | `WallDefault.kt` + `KachiPaletteSeeds.BACKDROP_DARK/LIGHT` + `WallpaperController.showDefault/onThemeChanged` | "phòng trưng bày" trừu tượng dựng bằng mã (chuyển sắc dọc graphite→xanh đêm / sứ→xám xanh + đèn hắt trần + vầng sáng sàn), bitmap ¼ màn đưa qua ĐÚNG đường ảnh nền (WallView + `WallArtStore` ⇒ kính + `GlassVeil` + dải che áp như ảnh thật); chỉ khi người dùng không có ảnh; đổi Sáng/Tối ⇒ dựng lại |

Không làm (giữ nguyên vì là ràng buộc thật, có bài canh): **0 viền** (WP1, owner 20/09) · **0 bóng/blur/elevation** (GPU TRINKET,
`SurfaceMaterialContractTest`) — chiều sâu do chuyển sắc + kính giả. **Độ đục thẻ trên ảnh**: đã thử 0,8 → 0,64 (brief §4) ⇒
`SurfaceContrastContractTest` đỏ (`ink` trên ảnh trắng tinh chỉ 3,95:1 nếu không có lớp che) và [ĐO] ảnh gần như không đổi vì
`GlassVeil` bù lại ⇒ **hoàn nguyên 0,8**.

## 3. Tương phản — [ĐO] `widget-visual-301/harness/contrast.py`

Mỗi nút chữ của `uiautomator`: nền = trung vị vành 3 px NGOÀI hộp (thứ chữ thật nằm lên — thẻ, lớp che, ảnh), mực = phân vị 80 %
của các điểm khác nền (≥ 1,3:1) TRONG hộp (lõi nét). Sàn chữ 4,5:1.

| Cảnh | Tối (min) | Sáng (min) |
|---|---|---|
| 6 nhóm 2×3 (nền mặc định) | 6,77 | 5,01 |
| telemetry đơn 2×3 | 7,20 | 5,60 |
| 6 nhóm 6×2 | 6,77 | 5,01 |
| 3 nhóm 4×6 | 4,71 (`—` chưa đọc, mờ 50 % có chủ ý) | — |
| 106 ô kiểm kê (4 cỡ, tối) | 4,71 (cùng `—`) | — |
| trên ảnh Seal-06 (sáng nhất, mean 134) | 5,98 | 4,59 |
| trên ảnh Seal-08 (tối nhất, mean 60) | 6,57 | 4,63 |
| trên ảnh Seal-03 (mean 123) | 6,14 | 4,63 |

Đồ hoạ (sàn 3:1): thanh mức đầy/rãnh **5,64** (tối) · **3,96** (sáng); đầy/mặt thẻ 8,98 · 5,49; rãnh/mặt thẻ 1,59 · 1,39 (rãnh
chỉ là nền, không mang thông tin). Icon chip/nền chip 6,66 (tối) · 4,53 (sáng).
Ảnh Seal = bộ ảnh BYD owner đưa để THỬ (14 JPEG ở thư mục ảnh nền của máy ảo) — **không** vào repo/APK.

## 4. Co giãn R19–R21 — giữ nguyên

Harness `widget-fit-301/harness/` (`batch.sh` + `analyze.py`, nay nhận thư mục làm việc qua `WFIT_DIR`), số giả, tiếng Việt,
25 widget × 4 cỡ: **106/106 mục `bad=0`** trên bản cuối (`widget-visual-301/harness/inventory-r22.txt`). Lỗi phát hiện bằng ảnh
mà `analyze.py` không bắt được (chiều rộng/ký tự vẫn ≥ 4,5 px): `Bật`/`Tắt` nhóm Đèn bị cắt còn **19 px** (= bề rộng `—`) — gốc:
bỏ lượt đặt nền mỗi nhịp thì TextView `ellipsize` không tự xin đo khi chuỗi đổi ⇒ `CellBinder` gọi `value.requestLayout()` CHỈ khi
chuỗi đổi; khoá bằng `WidgetVisualContractTest` (red-proof: bỏ dòng đó ⇒ đỏ).

## 5. Chi phí (owner 08/10 — mỗi bản sửa chứng minh không nặng thêm)

- **Bớt**: ô con mỗi nhịp 1 Hz trước gọi `surfaceOf` ⇒ dựng 1–2 `Drawable` mới (+ gắn/gỡ listener kính khi có ảnh) **cho mọi ô
  con** (~10/nhóm) dù không đổi gì; nay chỉ khi tone đổi, và hàng NEUTRAL/ACTIVE không có nền ⇒ ít lớp vẽ hơn mỗi khung.
- **Thêm**: nền mặc định = 2 bitmap 480×270 (~1 MB RAM), dựng 1 lần ở thread nền mỗi lần đổi Sáng/Tối; WallView vẽ 1 bitmap
  thay 2 lớp RadialGradient toàn màn. Hệ quả lớn hơn: **đường kính giả (`WallWindowDrawable`) nay bật cho MỌI người** (trước chỉ
  khi có ảnh của người dùng) ⇒ mỗi thẻ thêm 1 lượt `BitmapShader` + listener layout. [CHƯA ĐO] khung/CPU bằng
  `perf-snapshot.sh` — đề nghị đo trước khi phát hành; đường lùi rẻ: bỏ `WallArtStore.swap` cho nền mặc định (giữ ảnh, bỏ kính).
- Thanh mức: 2 `drawRoundRect`, `invalidate` chỉ khi phân số đổi. Span đơn vị: chỉ khi chuỗi đổi. 0 luồng, 0 nhịp mới.

## 6. Còn mở

1. Brief §2.6 **ẩn hàng không có dữ liệu** (thay lưới `—`) — chưa làm: ảnh hưởng kế hoạch co giãn theo nhịp (mục ẩn/hiện mỗi
   khi xe trả số) và chỉ kiểm được trên xe thật (số giả hiện đủ). Cần owner chốt.
2. Brief §2.7 **gradient chàm/tím của ô BẬT ở thanh nút** (`gradientSoft`/`tileOn*`) — ngoài phạm vi widget, chưa đổi.
3. Bảng lốp/cửa (Canvas) vẫn là ô số trong hộp — hình học xe, để lượt sau.
4. Perf khung với kính bật mặc định (mục 5).

## 6. R22 tiếp — bảng VẬT × VIỆC + hình xe cho nhóm Kính (owner 13/10 "rối mà" → "visual thiết kế cho đẹp")

[ĐO] ảnh owner: nhóm *Kính* ô 2×3 = hàng đọc nhãn xuống dòng + **11 nút phẳng** 3 cột, hàng cuối hụt 1 ô; `0 %` trắng cạnh
`35 %` xanh. Thật ra 11 nút = **2 việc (`100%` · `50%`) × 5 vật** + 1 nút lẻ.

- `:core` `GroupActionMatrix` dựng bảng từ DỮ LIỆU: vật = icon nút (quy ước có sẵn: `win_lf`/`win_half_lf` chung
  `ic-car-top-window-lf`), việc = `ControlDef.verb` (trường mới, khai ở 10 nút kính), số = ô XEM trùng `readKey` (gộp hàng
  đọc vào hàng vật), vị trí trên thân xe = hậu tố `-lf/-rf/-lr/-rr`. Không `if` theo nhóm; quét cả bộ hôm nay chỉ `g_windows`
  khớp (Đèn/Cửa/Lốp/Khí hậu không khai động từ ⇒ đường cũ, không đổi pixel nào ngoài luật màu + canh giữa hàng ngắn).
- Dạng theo chỗ đo: **hình xe** (`ActionMatrixBoard`: ảnh xe top-view chung với bảng lốp/cửa, thanh mức kính ở đúng cửa đầy theo
  % mở màu nhấn, thẻ số + nút `100%`/`50%` cạnh đúng cửa, thanh đáy có hình `4 kính 100%` · `4 kính 50%` · `Đóng cả cụm`) →
  **danh sách gọn** (`ActionMatrixList`: `nhãn · số · [100%] [50%]`, nút thẳng cột; ô rộng-mà-thấp chia khối cạnh nhau; thiếu cao
  thì bỏ hàng từ cuối, chân giữ) → ô tóm tắt (đường cũ). Nhãn nút một dòng, không xuống dòng.
- Luật màu số (mọi ô nhóm): **mực trung tính**; "đang mở/bật" nói bằng nút sáng nền nhấn + mức đầy; chỉ cảnh báo đổi màu chữ.
- Lưới ô con + lưới nút: hàng ngắn **canh giữa** (hết ô trống lẻ cuối lưới).

Ảnh: `widget-visual-301/12-windows-before-matrix.jpg` (trước) · `13|14-windows-list-{dark,light}-after-matrix.jpg` (2×3 · 2×6 ·
6×2) · `15|16-windows-board-{dark,light}-after-matrix.jpg` (4×6 hình xe, cạnh Đèn/Cửa) · `17-owner-layout-dark-after-matrix.jpg`.
Kiểm kê co giãn (số giả, vi, 25 widget × 4 cỡ): **106/106 bad=0**. Tương phản chữ min: 4×6 tối **6,65** / sáng **5,01**; cảnh
2×3/2×6/6×2 + Khí hậu: tối 4,72 / sáng 3,15 — cả hai là `—` CHƯA ĐỌC (mờ 50 % có chủ ý, `CellBinder.DIM`, có từ trước, ô Khí hậu);
mọi chữ khác ≥ 4,95. [CHƯA ĐO] tương phản đồ hoạ thanh mức trên ảnh xe.
Giới hạn [ĐO]: ô 2×3 của owner chỉ đủ cao cho 3 hàng vật + chân (đích chạm 48 dp) ⇒ `Kính SP` và hàng `4 kính` bị bỏ ở cỡ đó; 2×6,
6×2, 4×6 hiện đủ.

## 7. R22·B — hình xe mà CHÍNH VẬT là nút · bảng icon Đèn · luật chữ giữ chỗ (owner 13/10 "cả mớ số 100% 50% nhìn rối")

[ĐO] bệnh (ảnh owner + máy ảo): ô Kính 2×3 (vùng bảng 267×312 px @1,5×) lùi về danh sách gọn và **bỏ `Kính SP` + hàng
`4 kính`** (thiếu cao cho hàng 48 dp); owner bác luôn lưới nút chữ `100% | 50%` ở MỌI cỡ (kể cả thẻ cạnh cửa của hình xe 4×6).

**Kính — `ActionMatrixCompact` (thay hình xe có thẻ + nút; bản cũ `ActionMatrixBoard` đã GỠ):**
- Ảnh xe top-view chung (CarImageView) + thanh kính đúng vị trí, đầy theo % mở (`CarLevelsLayer`, tách ra dùng chung);
  số `%` nét LIGHT chỉ ở kính ĐANG MỞ (hoặc `—` khi chưa đọc — không giấu "chưa biết" thành "đóng").
- Đích chạm = CHÍNH kính: mỗi kính một phần tư khung (`:core` `GroupActionMatrix.zones`, kẹp ≥ đích chạm), nở quanh hình vẽ.
  [ĐO] ô 2×3: bốn vùng 133×82 · 133×79 · 134×82 · 134×79 px (≥ 72 px = 48 dp); 2×6, 6×2, 4×6 đều đủ bốn kính.
- Chạm làm gì: **một công tắc mảnh `Mở hẳn | Hé`** trên đầu bảng (đoạn chọn = viền tóc + chữ nhấn) — chọn công tắc, KHÔNG
  nhấn-giữ: nhấn-giữ vô hình và khó trên xe đang chạy. Động từ nay là CHỮ (`ControlDef.verb` `Mở hẳn`/`Hé`/`Đóng`, EN
  `Full`/`Vent`/`Close`, zh/th/ms trong `i18n/*.tsv`); mức là dữ liệu riêng `ControlDef.verbLevel` (100/50/0).
- Chân: 3 nút lặng (không nền đặc, chỉ sóng chạm) — hình xe nhỏ vẽ bằng mã với 4 kính đầy đúng mức + một chữ:
  `Mở hẳn · Hé · Đóng`. Kính có lệnh đang bật ở chế độ hiện tại ⇒ viền nhấn quanh thanh.
- Đường ghi: vùng chạm/nút chân `performClick()` ô nút THẬT của `ControlTileFactory` (gắn `GONE`) — không đường ghi thứ hai.
  [ĐO logcat máy ảo] chạm Kính SP ⇒ `ctl id=win_rr args=[4,1]`; đổi `Hé` rồi chạm Kính lái ⇒ `id=win_half_lf args=[1,4]`
  (off-car: HAL trả false, giữ lạc quan — như mọi nút). [CHƯA ĐO] trên xe.
- Dạng theo chỗ (`:core` `compactForm`): STACK (công tắc · xe · chân, cụm canh giữa) → ROW (ô rộng-thấp 6×2) → danh sách gọn
  CHỈ dưới ngưỡng hình xe → ô tóm tắt. Ảnh xe nạp xong ⇒ `CarImageView.onImage` đặt lại thanh + nhãn ([ĐO] 2×6: trước đó thanh
  lệch vào giữa thân xe, nhãn đè xe).

**Đèn — `IconBoardView` (khai `CapabilityGroup.layout = GroupLayout.ICONS`, không `if` theo nhóm):** mỗi đèn một ô icon; ô đọc
+ nút CÙNG hình gộp làm một (`headl`↔`light_high_beam`, `drl`↔`light_drl`; `readl` ô riêng). Đang bật = nền nhấn mềm 15 % +
icon nhấn, chữ mực; ô bấm được = viền tóc 20 % + sóng chạm; chữ 1 dòng → 2 dòng → chỉ icon theo chỗ (2×3: chỉ icon). Lưới do
`GroupIconBoard.grid` chọn (ô vuông nhất, cạnh ≥ đích chạm, trừ điểm chỗ trống).

**Luật chữ giữ chỗ (`—`):** `KachiWidgetType.pending` = vai `MUT` (đã khoá ≥ 4,5 ở SurfaceContrastContractTest), không còn
`alpha 0,5` ở CellBinder · ReadTile · MatrixKit · WidgetTelemetry (MiniCard, valueShape; bỏ alpha cả ô).

Số đo [ĐO máy ảo, số giả, vi]:
- Kiểm kê co giãn 25 widget × 4 cỡ: **106/106 bad=0**.
- Tương phản chữ min (harness `contrast.py`): Kính tối 6,73 / sáng 4,73 (sau gom cụm, đo lại) · Đèn tối 6,49 / sáng 4,54 · Khí hậu+Cửa tối 6,63 /
  sáng 4,77. `—` Khí hậu: **sáng 5,19 (trước 3,15) · tối 7,55 (trước 4,72)**.
- Đồ hoạ (lõi nét 98 %, nút có mô tả; trừ 4 vùng chạm kính — chúng trong suốt trên ẢNH xe): công tắc + chân Kính tối
  5,44 / sáng 4,77 · ô Đèn tối 5,44 / sáng 3,79 (≥ 3). Trước khi sửa chữ ô đèn sáng
  dùng mực nhấn: 3,44 ⇒ đổi sang mực. [CHƯA ĐO] tương phản thanh kính trên ảnh xe (nền là ảnh).

Ảnh: `widget-visual-301/18|19-windows-sizes-{dark,light}-board.jpg` (2×6 · 6×2 · 4×6 · 2×3) · `20|21-lights-sizes-{dark,light}-board.jpg`
· `22-windows-2x3-tap-ve-board.jpg` (sau khi chọn `Hé` + chạm Kính lái: viền nhấn) · `23-owner-layout-dark-board.jpg` ·
`24-owner-windows-2x3-dark-board.jpg`.

Chưa làm: **Cửa & khoang** (hình xe đã có, hàng nút `Cốp sau · Cửa sổ trời · Rèm` vẫn là ô nút có nền — cần ánh xạ nút ↔ bộ
phận trên hình), **Ghế** (không có nhóm ghế nào trong `CapabilityGroups` — chỉ hai datum rời), **Khí hậu −/+** (nhóm KHÔNG có nút
nào; thêm nút nhiệt/quạt là thêm khả năng mới ⇒ §14 + owner).

## 8. R22·R — bớt "miếng xanh", bớt chữ (owner 13/10: *"mấy cái miếng xanh xanh đó xấu và thô lắm em"* · *"giảm text đi em, làm các icon, hình ảnh … chữ nó làm xấu lắm"*)

Diễn biến trong lượt (để lượt sau không lặp): thử xe NÉT MẢNH vẽ bằng mã → owner *"hơi thô … không chỉ khung thô"* → orchestrator
DỪNG mọi hướng vẽ xe tay (owner: *"như đồ chơi con nít"* so với Kim/Kinex dùng render 3D) ⇒ hình xe là **track riêng với owner**
(render dựng sẵn). Lượt này **giữ ảnh xe hiện có** (`assets/car/default-car.png`, 678×1397); mã xe nét đã XOÁ, không ship.

**Kính** (`CarLevelsLayer` + `:core` `CarArtPanes`):
- Bảng kính luôn vẽ ảnh MẶC ĐỊNH (`CarImageLayer(defaultArt = true)`), khung theo tỉ lệ ảnh ⇒ xe TO hơn (ô 4×6: ~300×620 px), khung
  không đổi khi ảnh nạp xong. Ảnh xe riêng của người dùng vẫn thắng ở bảng lốp/cửa/xe mini (không đổi).
- Bốn kính hông = MẶT NẠ đo một lần trên ảnh [ĐO lưới phóng 3×: dải tối giữa nếp gờ cửa x≈108–110 và mép trong thanh nóc x≈131–137;
  trụ B y≈685–728; ảnh đối xứng quanh x=338,5]. Kính mở = phần "kính hạ" lộ KHOANG TỐI (đen 88→70 %, cắt đúng mặt nạ) + nét nhấn
  mảnh 1,25 dp có quầng 4 dp ở mép kính hạ và quanh mảnh đang mở. Đóng = kính nguyên của ảnh. Hết thanh xanh đặc.
- **Móc cho render tương lai**: thay asset + `ART_W/ART_H` + hai đa giác trái trong `CarArtPanes` (KDoc ghi cách làm); không mã vẽ nào đổi.
- Công tắc `Mở hẳn | Hé` và ba nút chân → **chỉ icon** (`WindowGlyph`: một ô kính hạ đúng mức; chân = hai ô kính = cả xe), tên ở
  `contentDescription`. Số `%` chỉ ở kính đang mở (giữ).

**Lốp** (`TyreBoardView`): bỏ 4 thẻ nền + chấm cảnh báo; CHỈ con số nét light canh mép về phía xe (hai cột thẳng hàng), dòng phụ nhỏ
= nhiệt (+ lý do khi xe báo). Bỏ "bar" lặp và "TT/TP/ST/SP" (vị trí nói bánh nào). Chưa phán ⇒ MUT (MUT2 chỉ 4,48 trên kính sáng [ĐO]).

**Thanh nút / ô BẬT** (tầng chung): vai `tileOnFrom/To` = nhấn PHẲNG 16 % (tối) / 14 % (sáng), bộ dựng `KachiTheme.tonalOn` = bề mặt ô +
lớp nhấn; icon BẬT = `ACCENT_INK`, chữ = `INK_ON_ACCENT` (trung tính). Hết gradient chàm→tím. Ô thanh nút (`TileSize.DOCK`) và hàng nút
ô nhóm (`TileSize.GROUP`) **chỉ icon** (`iconOnly`, icon 24 dp), tên ở `contentDescription`; số (22°, quạt 4, chấm mức) vẫn hiện.
Nút Cài đặt trên thanh trên: đĩa tông nhạt `ACCENT_SOFT` + icon nhấn (hết đĩa gradient). Đĩa chữ cái hồ sơ (Q) CHƯA đổi.

**Đèn** (`IconBoardView`): chỉ icon ở mọi cỡ, icon lớn theo ô (42 % cạnh, 24–44 dp); ô bấm được = nền mực 8 % thay viền tóc (ZeroBorder).
**Cửa**: chấm "đang mở" = quầng 30 % + lõi nhỏ thay đĩa đặc.

Số đo [ĐO máy ảo emulator-5556, số giả, vi]:
- Kiểm kê co giãn 25 widget × 4 cỡ: **106/106 bad=0** (`harness/inventory-r22-refine.txt`).
- Chữ (`contrast.py`, nút chữ): min tối 8,99 · sáng 4,80. Số lốp vẽ Canvas (lõi nét 80 %): tối 6,73–7,44 · sáng 5,05–5,31.
- Đồ hoạ: nét nhấn quanh kính mở trên ẢNH: vs sơn xe 3,45 (tối) / 3,49 (sáng) · vs khoang lộ 4,13 / 4,16 (bảng sáng pha 55 % trắng vào
  mực nhấn vì mực nhấn sáng-chủ-đề chỉ 1,72 trên ảnh). Icon BẬT trên nền tông ≥ 3:1 khoá ở `ThemePaletteContractTest` (cả hai bảng).

Ảnh: `25-owner-windows-2x3-before-after-refine.jpg` · `26-dock-before-after-refine.jpg` · `27-header-settings-before-after-refine.jpg` ·
`28|29-windows-tyres-lights-4x6-{dark,light}-refine.jpg` · `30|31-small-groups-2x3-{dark,light}-refine.jpg` · `32-owner-layout-dark-refine.jpg`.

## 9. DOT — chấm tròn thay "gạch xanh" trên ảnh xe (owner 13/10: *"thay vì vẽ hẳn 1 cái gạch màu xanh, mình vẽ chấm tròn thôi, như bọn kinex, kimlauncher thấy nó đẹp hơn"*)

Thiết kế (một đường cho ảnh mặc định lẫn ảnh BYD, bảng kính lẫn bảng cửa): trạng thái ở `:core` `CarDots` (UNKNOWN · CLOSED · PARTIAL ·
OPEN), tô ở `:app` `StateDotPainter`. Tâm chấm = tâm hộp bao mảnh kính đo trên chính ảnh (`CarArtPanes.paneCenter` ← `CarArt`) — đã nằm
trên dải kính (mặc định x ≈ 123/678; Seal y ≈ 395/576 trong dải 386–404) ⇒ không cần nắn. Bảng cửa: neo bộ phận của ảnh (`art.part`).
- Đóng = vòng rỗng 1,25 dp trắng 80 % · chưa đọc = vòng ĐỨT nét trắng 55 % (không nhãn) · mở một phần = vòng nhấn + QUẠT nhấn theo tỉ lệ
  từ 12 giờ, thụt vào trong vòng một khe 0,75 dp (chọn quạt thay nửa-tô: ở 8–12 dp, 35 %/50 % = 126°/180° phân biệt rõ; mực nước nửa-tô
  chỉ chênh ~1,5 dp), quạt kẹp 30°..330° (3 % vẫn thấy miếng, 97 % vẫn hở khe) · mở hẳn = chấm đặc nhấn + quầng nhấn 35 % tới r + 4 dp
  (CHỈ mở hẳn có quầng ⇒ 100 % không lẫn quạt lớn). Mọi chấm đứng trên đĩa tối 55 % (r + 1,5 dp) — quầng nằm NGOÀI đĩa đó.
- Cỡ: đường kính = 0,14 × cạnh nhỏ khung xe, kẹp 10–12 dp (sàn 10 dp sau khi bỏ số: ở 8 dp quạt chỉ vài px). Vùng chạm không đổi (`GroupActionMatrix.zones`, ≥ 48 dp).
- Owner 13/10 sau khi xem bản chấm: *"đẹp, bỏ số luôn em, vì cái chấm nó có thể hiện tương đối % rồi"* ⇒ GỠ nhãn `%` cạnh xe ở mọi cỡ
  (cùng cột nhãn trong số đo `:core` `CompactMetrics.labelPx`); số chính xác chỉ còn trong `contentDescription` vùng chạm
  (`Kính lái · 35 % · Mở hẳn`).
- Gỡ hẳn: khoang tối cắt mặt nạ, nét mép kính hạ, đường bao mảnh (`CarLevelsLayer`), `CarArtPanes.fillTo`, chấm quầng+lõi riêng của bảng cửa.

Tương phản [ĐO độ chói sơn quanh tâm chấm trên ảnh gốc, đĩa bán kính ≈ chấm, script PIL tạm của phiên]: trung vị 0,022–0,0395 (xấu nhất Seal
sau-phải), p95 xấu nhất 0,4695 (ảnh mặc định trước-phải — nóc bạc/phản chiếu), đỉnh 0,598. Tỉ số [TÍNH theo `ColorMath`, cùng công thức
bài canh `CarDotWiringContractTest`] — nền = đĩa tối 55 % trên sơn:

| Bảng | Sơn L | Chấm mở (nhấn) | Vòng đóng | Vòng chưa đọc |
|---|---|---|---|---|
| Tối (nhấn #8ab2ff) | 0,0395 / 0,4695 / 0,598 | 8,28 / 3,68 / 3,20 | 11,51 / 5,70 / 5,05 | 6,02 / 3,60 / 3,31 |
| Sáng (nhấn 45 % #2b52d1 + 55 % trắng) | 0,0395 / 0,4695 / 0,598 | 8,35 / 3,71 / 3,22 | 11,51 / 5,70 / 5,05 | 6,02 / 3,60 / 3,31 |

Bản trước (quầng đè dưới chấm) chỉ 2,07–2,25 ở p95/đỉnh ⇒ đã dời quầng ra ngoài đĩa tối. Thử đỏ: hạ đĩa tối còn 25 % ⇒ bài canh đỏ (1,67).
Máy ảo emulator-5556, Tối, ảnh Seal BYD [ĐO pixel ảnh chụp]: điểm sáng nhất chấm / điểm tối nhất quanh chấm 9,9–16,7:1; trung vị sơn quanh
chấm 0,009–0,012. Bảng Sáng chưa chụp trên máy ảo (ảnh xe không đổi theo chủ đề, chỉ màu nhấn đổi — đã tính ở bảng trên).
Chưa có dữ liệu "chưa đọc" trên máy ảo (số giả đọc đủ) ⇒ vòng đứt nét mới khoá bằng test, chưa nhìn trên màn.

> Ảnh chụp `widget-visual-301/*.jpg` chỉ giữ ở máy dựng (gitignore): nhiều ảnh có ảnh nền/hình xe BYD dùng để THỬ, không được lên repo public.

## 10. 3.1.5 — bộ ảnh nền MẶC ĐỊNH đóng gói (owner 14/10), CHỈ ở máy dựng

Owner 14/10: bộ ảnh của owner (1 ảnh chụp riêng + 10 hình nền BYD Seal) là nền mặc định của Kachi. Ảnh BYD có bản quyền mà repo
công khai ⇒ ảnh CHỈ được đóng vào APK dựng tại máy owner (release + vehicleTest, vì nằm ở `app/src/main/assets/wall-default/`),
thư mục bị `.gitignore` chặn (`app/src/*/assets/wall-default/`); `DefaultWallpapersContractTest` canh `git ls-files` rỗng + `check-ignore`.

- **Thứ tự nguồn** (`WallSourcePolicy`, `:core`): ảnh người dùng (bật *Dùng ảnh làm hình nền* + thư mục có ảnh) > bộ ảnh đóng gói >
  nền "phòng trưng bày" vẽ bằng mã (`WallDefault`, mục 2). Lựa chọn của người dùng không bao giờ bị đè; tắt ⇒ về bộ ảnh đóng gói.
- **Cùng đường ảnh người dùng**: `WallpaperController.step` giải mã asset theo cỡ màn (`WallpaperStore.decodeScaled`, chung luật
  inSampleSize) → `WallArtBuilder` (kính + `GlassVeil` + lớp làm tối theo mức người dùng chọn) → `WallView` + `WallArtStore`; đổi ảnh
  theo đúng nhịp trình chiếu có sẵn (chu kỳ của người dùng, mặc định 60 s). Không thêm luồng/nhịp. Bộ đệm kính của asset ở
  `cacheDir/wall-default-art/` (bộ dọn thư mục người dùng coi khoá asset là mồ côi).
- **Lùi an toàn**: không có thư mục (clone sạch) / `AssetManager.list` lỗi ⇒ danh sách rỗng, log `không có ảnh nền đóng gói` ⇒ nền vẽ
  bằng mã. Ảnh đóng gói giải mã hỏng khi chưa hiện ảnh đóng gói nào ⇒ nền vẽ bằng mã. Không danh sách tệp cứng trong mã.
- **Tệp**: `wall-01.jpg` (ảnh owner) … `wall-11.jpg` (Seal theo số thứ tự), 1920 px ngang giữ tỉ lệ (1920×977–1080), JPEG q85, đã
  xoá toàn bộ EXIF/metadata (ảnh owner không có GPS). Tổng **≈1,7 MB** (104–302 KB/ảnh).
- **Dựng lại** (khi đổi ảnh): từ thư mục ảnh của owner ngoài repo, script PIL ngoài repo — với mỗi ảnh: áp xoay EXIF → resize 1920 ngang
  (LANCZOS) → lưu JPEG q85 không kèm EXIF/ICC → đặt tên `wall-NN.jpg` (ảnh owner trước, rồi ảnh Seal theo số trong tên), ghi vào
  `app/src/main/assets/wall-default/`, rồi dựng APK như thường. Kiểm `git check-ignore -v app/src/main/assets/wall-default/wall-01.jpg`.
- Chưa đo trên máy ảo/xe: tương phản chữ/thẻ trên từng ảnh ở bảng Sáng và Tối (dim mặc định 45 %), RAM thực (ước: 1 bitmap 1920×1080
  ARGB ≈ 8 MB, như ảnh người dùng).

## 11. Chủ đề SÁNG — nút/icon 3.1 "mờ", sai màu (owner 14/10, log xe `chủ đề=sáng`)

Đo trên máy ảo emulator-5556 (vehicleTest 3.1.4 (209), hồ sơ QA2: ảnh nền + làm tối 45 % + độ đục thẻ 70 %, số giả: kính 0/35/100/0,
cốt + DRL + sương mù trước bật, xi-nhan trái). Số = WCAG từ ảnh chụp (PIL, script tạm của phiên); ảnh chỉ ở máy dựng.

| Bề mặt | Gốc [mức] | Sửa (tầng chung) | Trước → sau (Sáng) |
|---|---|---|---|
| Nút tròn chỉ-icon: chân Kính 2D, nút xe 3D, hàng chân 3D (−/+, 3 nút kính, lấy gió) | [ĐO] ba bản chép tay cùng công thức bảng TỐI (đĩa `INK` 8 % + glyph `ICON`). Ở Sáng nền dưới nút là ảnh/thẻ đã làm tối ≈ (181–188) ⇒ đĩa xám trên nền xám | `KachiButtonInks` (một chỗ, chọn theo độ sáng `bg` của bảng): Sáng = đĩa kính tối (`wallScrim` 60 %) + glyph `ON_ACCENT`; BẬT = đĩa nhấn đậm (`GRAD_FROM` ½ `INK_ON_ACCENT`). Tối giữ nguyên từng byte | đĩa/nền **1,16 → 4,5–4,7** · glyph/đĩa nút kính **2,2–3,3 → 8,5–8,9** · −/+ **1,7–2,5 → 5,0–7,5** |
| Đĩa nút đèn (`StateDotPainter.drawButton`, dùng chung 2D/3D) | [ĐO] glyph đèn hai tông (thân 0,16 · phụ 0,38 · chỉ phần ĐÈN 1,0) ở ~21 px: chỉ vài điểm đạt, phần lớn nét là xám | Sáng: alpha nét × 2 (bộ lọc ma trận, giữ theo màu) + nền nút 90 %; nhấn trên ảnh 0,45 → 0,40 | đèn pha 3D (p90 nét) **3,3 → 8,5** · DRL bật **2,1 → 4,6** (đỉnh 4,5 → 7,3) |
| Đèn báo chỉ-xem (`drawLamp`, bảng Đèn 2D) | [ĐO] mực trắng 60 % + viền đen 1 dp trên thẻ sáng: lõi nét chỉ 1,4–2,6:1 | Sáng: viền SÁNG + mực TỐI (`INK`; chưa đọc `MUT`), màu loại đèn → vai bản sáng (`green`/`amber`/`accentInk`); không thêm đĩa (luật POLISH) | đèn tối/thẻ: lõi nét **1,4–2,6 → 6,9–10,2** (đỉnh) |
| Ô chọn TẮT trong KachiDialog | [TÍNH] `fieldSunken` trên mặt hộp 1,26–1,34 (Tối 1,03–1,22) | `dialogBoxOff`: vai đầu tiên `fieldSunken` → `lineStrong` đạt 3:1 trên cả hai đầu mặt hộp (Tối cũng đổi) | **1,3 → 4,5–4,8** (Tối 1,0–1,2 → 3,05–3,18) |
| Nút nguy hiểm KachiDialog | [TÍNH] `RED` trên `redSoft` 20 % ở đáy mặt hộp 4,39 | `redSoft` Sáng 20 % → 16 % | **4,39 → 4,68** |

Bảng Tối [ĐO]: bảng Đèn/Kính/Khí hậu 2D 0 điểm ảnh khác (> 8) giữa trước/sau; đĩa/glyph hàng chân 3D cùng giá trị (glyph 174 · đĩa
33–48), chỉ ảnh nền phía sau khác (bộ ảnh nền đóng gói §10 vào cùng bản dựng). Đĩa nút Tối/nền vẫn 1,2 — thiết kế "tông mềm" giữ nguyên.

Khoá bằng `Widget301LightContrastTest` (nút sáng ở nền L 0,30–1,0: glyph ≥ 4,5, đĩa ≥ 3, BẬT lam rõ ≠ TẮT; ba chỗ vẽ nút đi qua
`KachiButtonInks`; nút/đèn báo Sáng: nét chính ≥ 4,5, lớp phụ 0,38 ≥ 3 ở ba mức ảnh + nền sáng; hộp thoại) + `WindowControlIconContractTest`
(4,5 + Tối giữ byte) + `LampIndicatorContractTest` (đổi sang `lampEdge`/`lampTone`).

**Còn mở [ĐO máy ảo, SUY cho xe]**: với ảnh nền + làm tối 45 % + thẻ 70 %, mặt thẻ bảng Sáng chỉ còn ≈ (186,189,197) ⇒ nhãn `MUT` của ô
nhóm (`GroupCellGrid`) đo 3,65–3,73:1 — không riêng 3.1, gốc là lớp làm tối ảnh KHÔNG theo chủ đề (`KachiPalette.wallScrim` KDoc: đổi
chiều/nhãn "Làm tối ảnh" là quyết định owner). Nhãn nhiệt lốp (`38°C`, `MUT`) dưới bánh xe 3D cùng gốc (không sửa ở lượt này). Cần đọc trên xe `wallpaper_prefs`
+ `color_choice` của hồ sơ đang dùng để biết xe có ở cấu hình này không.

## 12. R29 pha 2 — mực thích ứng nối vào `:app` (14/10, chưa commit, cùng bản 3.1.5)

Spec 301 §R29 · §4.7 · §5.1 T29-2…T29-10. Nối: thẻ kính (`KachiGlass.paint` / `WallWindowDrawable.relocate`) · chữ/icon ô nhóm +
năm hàm tô màu của `KachiWidgetType` · thanh trên (giờ, ngày, chip + icon chip) · thanh nút + ô nút (nhãn, số STEP, chữ lựa chọn).
Mực = `ContrastInk.resolve` trên nền GHÉP đo được (ảnh sắc dưới vùng mặt → làm tối → dải che → lớp mọi mặt), ứng viên từ bảng màu SỐNG;
thiếu ⇒ lớp đỡ dưới chính phần tử (`InkHalo`), không nâng độ đục thẻ (OQ7).

**Chi phí** [ĐO-JVM máy dựng, ba ảnh đóng gói 1920×1080, bài thăm dò tạm đã gỡ]: lưới ảnh sắc 64×36 ≈ 22 ms/ảnh (gồm đọc hàng qua
`ImageIO`; riêng bộ dựng 6,8 ms — pha 1) · lưới ảnh mờ 32×18 ≈ 0,6 ms · mã hoá đệm: lưới sắc **43–46 KB** + lưới mờ **11 KB** ⇒
≈ 57 KB/khoá (`.kachi-art/*.txt`; trần 128 khoá `WallArtCachePolicy` ⇒ ≤ 7,3 MB tệp chữ tệ nhất). Chỉ nấu một lần mỗi ảnh/mức làm tối
(lượt sau đọc đệm); mỗi lượt nấu ghi một dòng `WallArt: R29 đo nền: lưới sắc … ms · lưới mờ … ms · đệm … KB` (đọc trên máy ảo/xe).
Giải: theo sự kiện (vùng mặt đổi · ảnh · chủ đề · độ đục), kết quả lưu trên mặt theo (vai, loại) — nhịp xe 1 Hz chỉ tra; `onDraw` 0
lời gọi. 0 luồng · 0 nhịp; listener mới duy nhất: `OnLayoutChangeListener` trên mặt THƯỜNG (thanh trên, thanh nút, ô nút).
Bộ nhớ: hai lưới ≈ 2 × (2 304 × 2 Int + Float) ≈ 55 KB/ảnh đang hiện; histogram dựng tạm 196 KB trên luồng nạp ảnh.

**Máy ảo (T29-10)** [ĐO emulator-5556, vehicleTest 3.1.5 (210), hồ sơ QA2 — thanh trên + widget 3D ở ô 0; bằng chứng ảnh chỉ ở máy
dựng]: hai APK dựng từ CÙNG một bản sao cây mã, khác đúng một hằng (mực thích ứng TẮT = "trước", BẬT = "sau"). Ma trận Sáng/Tối ×
wall-01 · wall-03 (tối nhất, độ sáng TB 64/255) · wall-10 (sáng nhất, 152) × làm tối 0/45/90 × độ đục thẻ o20/o70/o100 = 54 cảnh mỗi bản.
Đo WCAG trên ảnh chụp theo khung `uiautomator dump` (nút của gói Kachi): mực = lõi nét (Otsu, 20 % điểm đậm nhất phía mực), nền = p10/p90
phía gần mực của các điểm cách nét ≥ 2 px (bỏ viền khử răng cưa — bản đo đầu không bỏ nên báo oan 3,75 cho giờ, thật ≈ 5,8).

| Vùng · vai | Tối: tệ nhất trước → sau (số nút dưới sàn) | Sáng: tệ nhất trước → sau (số nút dưới sàn) |
|---|---|---|
| Thanh trên · chữ (giờ, ngày, chip) | **1,54 → 4,94** (15/216 → 0/216) | **1,07 → 4,86** (62/216 → 0/216) |
| Vùng giữa · icon (ImageView) | 8,35 → 8,35 (0/54) | **1,00 → 6,48** (8/45 → 0/54) |
| Thanh trên · icon (nút đĩa ứng dụng/mic — CHƯA nối) | 2,48 → 2,48 (1/81) | 1,11 → 1,11 (9/81) |
| Hàng chân widget 3D · chữ (T29-6 hoãn) | 5,41 → 5,41 (0/27) | 1,16 → 1,16 (22/110) |
| Hàng chân widget 3D · icon (T29-6 hoãn) | 2,46 → 2,46 (3/108) | 1,02 → 1,02 (9/127) |

Ca tệ nhất trước đây: `—% · — km` trên wall-03 Sáng làm tối 45 % o20 (1,07) và ngày `Thứ Tư, 14/10` trên wall-10 Tối làm tối 0 % o20 (1,54).
Còn dưới sàn sau R29 = đúng các chỗ CHƯA nối (hàng chân 3D, nút đĩa thanh trên), không đổi một số giữa trước/sau ⇒ đúng phạm vi.
Ô nhóm (T29-5) không có trên màn QA2 ⇒ chỉ có bằng chứng bài off-car, chưa có ảnh chụp [CHƯA ĐO máy ảo].

Chi phí trên máy ảo (dòng `WallArt: R29 đo nền`, 10 lượt nấu): lưới sắc 64×36 **9,8–26,5 ms** · lưới mờ 32×18 **0,7–5,3 ms** · đệm
**51–58 KB**/khoá; mỗi ảnh/mức làm tối một lần, luồng nạp ảnh sẵn có. `KachiContrast` (dưới sàn / không đặt được lớp đỡ): 0 dòng trong
cả lượt. PSS cuối lượt: 331,7 MB (trước) · 340,5 MB (sau) — MỘT mẫu mỗi bản sau 54 lần khởi động lại, chênh +8,8 MB nằm trong độ dao động
giữa hai lần chạy ⇒ [CHƯA KẾT LUẬN]; phần R29 giữ trên heap theo tính toán ≈ 55 KB/ảnh (hai lưới) + một `InkHalo` mỗi nhãn được đỡ.

**Chưa nối (backlog `R29-ADAPTIVE-CONTRAST`)**: T29-6 lớp phủ widget 3D (`Car3d*`, `StateDotPainter`, `Car3dFootRow`,
`KachiButtonInks`) · nút ⇄ `SlotSwapButton` · icon ô nút (`KachiIcons.tint`/`fadeOff`) · hai nút đĩa `pill` của thanh trên · đoạn màu
trong chuỗi (đơn vị `withUnit`, chip lốp) · nền WARN/ALERT 35 % của ô con · nút −/+ nền `DIM` (log `không đặt được lớp đỡ`).


### 12b. R29 pha 2b — các chỗ còn lại đã nối (14/10, chưa commit, cùng bản 3.1.5)

Spec 301 §R29 · §4.7 · §5.1 T29-6 · Autonomous Decisions (R29, pha 2b). Nối: lớp phủ widget 3D (`Car3dInk`: khung 3D là một mặt — ảnh
nền xe ∪ màu sơn ∪ bóng đất; nhãn lốp/hàng chân = mặt theo vùng; đĩa ghế = mặt mang màu đĩa; chấm/nút/đèn canvas đo từng điểm) ·
`KachiButtonInks` (mặt dò chọn công thức đĩa/glyph theo nền đo; nhánh `lightSurface` chỉ còn là mặc định chưa đo) · hai/ba nút pill
thanh trên · ⇄ (`AdaptiveInk.ride`) · icon ô nút (`KachiIcons.tint` → `AdaptiveInk.icon`; `fadeOff` trên mực + nền đo) · đoạn màu
(`InkSpan` thay `ForegroundColorSpan`: đơn vị `withUnit`, chip lốp) · ô con WARN/ALERT 35 % · nút −/+ `DIM` (lớp đỡ khi phần tử đã có
nền: `HaloLayer` cùng hình).

**Máy ảo (T29-10)** [ĐO emulator-5556, vehicleTest 3.1.5 (210), hồ sơ QA2 — widget 3D ô 0, **ảnh nền xe 3D = chính ảnh nền màn**
(`bg=<ảnh>`) + sơn steelgrey; bằng chứng ảnh chỉ ở máy dựng]: hai APK dựng từ CÙNG một bản chụp cây mã, khác đúng các tệp của lượt này.
Cùng ma trận §12 (Sáng/Tối × wall-01/03/10 × làm tối 0/45/90 × o20/o70/o100 = 54 cảnh mỗi bản), cùng phép đo. Lượt chạy đầu HỎNG
(đọc lại tệp hồ sơ từ máy mỗi cảnh, trúng lúc app đang ghi ⇒ mất hồ sơ QA2, các cảnh sau là màn mặc định) — chạy lại từ mẫu QA2 cố
định; chỉ số dưới đây là lượt chạy lại.

| Vùng · vai | Tối: tệ nhất trước → sau (số nút dưới sàn) | Sáng: tệ nhất trước → sau (số nút dưới sàn) |
|---|---|---|
| Thanh trên · icon (pill ứng dụng · mic · cài đặt) | **2,48 → 3,53** (1/81 → 0/81) | **1,11 → 3,49** (9/81 → 0/81) |
| Hàng chân 3D · icon (gió · lấy gió · ba nút kính) | **1,84 → 3,67** (45/117 → 0/108) | **1,39 → 3,67** (27/108 → 0/108) |
| Hàng chân 3D · số (nhiệt `22°`, gió) | **3,39 → 5,07** (9/27 → 0/36) | **1,02 → 5,07** (54/54 → 0/36) |
| Hàng chân 3D · dấu −/+ trên đĩa (đo trong đĩa nội tiếp¹) | **1,86 → 12,2** | 12,2 → 12,2 (đĩa kính sẵn) |
| Thanh trên · chữ (đã nối pha 2 — kiểm không lùi) | 4,94 → 4,94 | 4,86 → 4,86 |
| Vùng giữa · icon ghế 3D (không lùi) | 12,75 → 12,75 | 10,35 → 10,35 |

¹ `r29meas` lấy cả bốn góc vuông NGOÀI đĩa tròn (ảnh nền) làm "nền" ⇒ với dấu −/+ nó báo 3,2–3,3 cho cả hai bản (đo nét với ảnh, không
với đĩa); số trên đo nét (0,3 % điểm sáng nhất) với trung vị điểm trong đĩa. Bản trước Tối trên ảnh sáng: đĩa mực 8 % + glyph `ICON` =
1,86 (owner: "mờ"); bản sau mặt dò đổi sang đĩa kính + glyph trắng.

**Chưa đo được trên máy ảo [CHƯA ĐO]**: nhãn số lốp (máy ảo không có số lốp ⇒ nhãn ẩn đúng luật) · chấm/nút/đèn vẽ canvas (không có
nút trợ năng mang khung nét — chỉ bài off-car `cham den nut tren xe 3D`, 136 080 phép kiểm, kiểm độc lập theo đúng thứ tự vẽ) · ⇄, icon ô
nút, đoạn màu, ô WARN/ALERT, nút DIM (không có trên màn QA2) — khoá bằng bài nguồn + bài tính chất của `AdaptiveContrastContractTest`.

**Chi phí** [ĐO máy ảo, dòng log `Car3d … R29 lưới` / `KachiContrast R29 chấm`]:
- Giải chấm mỗi lần dừng xoay — bản đầu (cả 15 điểm, mọi lần): trung vị **3,5 ms**, p90 8,8 ms, đỉnh **15,5 ms** trên LUỒNG CHÍNH (264
  lượt). Sửa: chỉ điểm ĐANG HIỆN có nền ĐỔI (`AdaptiveInk.sameBackdrop`). Sau sửa [ĐO 6 cảnh × 3 lần vuốt, APK after2]: lượt có giải
  (2–5/15 điểm) trung vị **0,57 ms**, đỉnh **2,9 ms**; lượt không giải gì (chỉ dựng nền dưới điểm) đỉnh 1,4 ms.
- Lưới nền 3D (64×36): số log đọc 179–282 ms nhưng **SAI DỤNG CỤ ĐO** — mốc cuối lấy trong `main.post` nên cộng cả thời gian chờ luồng
  chính lúc HOME đang dựng (lấy mẫu 2×2 không đổi được số đó: 243–282 ms ⇒ không phải chi phí lưới). Một cảnh có luồng chính rảnh đọc
  37 ms (cận trên, gồm chờ). Đã sửa dòng log (mốc trên luồng nền); lấy mẫu 2×2 vẫn giữ. Chi phí lưới THẬT [CHƯA ĐO] — đọc dòng
  `R29 lưới` ở lượt máy ảo kế. Luồng nền sẵn có (`car3d-bg`), một lần mỗi ảnh nền xe.
- 0 luồng mới · 0 nhịp mới; listener mới = `OnLayoutChangeListener` của các mặt mới (khung 3D, hàng chân, 4 nhãn
lốp, đĩa ghế, mỗi nút đĩa, pill, nút DIM, ô con cảnh báo). `KachiContrast` dưới sàn / lỗi: 0 dòng. PSS cuối lượt 353,5 → 351,5 MB — một
mẫu mỗi bản ⇒ [CHƯA KẾT LUẬN].

**P0 tìm trong lượt (máy ảo, agent kính)**: `GradientDrawable.getCornerRadii()` ném NPE (`float[].clone()` trên mảng null) khi nền chỉ có
`cornerRadius` ⇒ lỗi lớp đỡ lọt lên `Car3dHeroLayer.fill` ⇒ widget 3D lùi 2D. Sửa: đọc qua `runCatching` + mọi cửa công khai của
`AdaptiveInk` là ranh giới fail-soft (log qua `LogLineThrottle`); khoá bằng bài `muc thich ung khong bao gio nem vao cho goi`.

### 12c. R29 làm lại (15/10) — bỏ MỌI lớp đỡ, đổi MÀU MỰC theo MẶT (chưa commit, nhắm 3.1.6)

Owner 15/10 bác bản §12–12b: *"cái làm tương phản chữ bằng cách vá 1 mớ text background nhìn rất dị hợm, xấu không tả được, phải làm
đổi màu chữ tự động, đồng đều, chứ không vá thế này được em, rất tởm"*. Spec 301 §R29 Pass 12 · Autonomous Decisions (R29, pha 3) · OQ7'.

**Thiết kế**: `ContrastInk.choose` (`:core`) chọn MỘT họ mực cho mỗi MẶT gốc (thẻ · thanh trên · thanh nút · lớp phủ 3D · ô nút) theo
thứ tự: chủ đề → chủ đề nâng chữ phụ (`MUT`/`ICON` → `INK2`) → bảng đối cực → đối cực nâng; điểm = MIN(tỉ số tệ nhất/sàn) trên mọi
phần tử, nền tệ nhất đo dưới CHÍNH vùng từng phần tử; có trễ 10 %; không họ nào đạt ⇒ họ để NHIỀU phần tử đạt nhất, cả mặt vẫn một họ,
`Log.w "dưới sàn: mặt …"` (kèm phần tử tệ nhất + hộp nền của từng họ). Bộ vẽ tự tô (`AdaptiveInk.Probe`: nút đĩa, chấm 3D, mặt pill /
nút DIM qua `AdaptiveInk.face`) góp điểm và nhận CÙNG họ; mọi nút đĩa của một mặt dùng CHUNG một kiểu đĩa (`Probe.group`). Gỡ:
`InkHalo` · `HaloLayer` · `placeHalo` · `InkCandidate` · `overlayHalo/haloFor` · `Inks.halos` + tham số `halo` của ba hình vẽ ·
`Spot.halos` · `KachiButtonInks.of(p, nền)` (đĩa đậm thêm theo từng nút). Không nâng độ đục thẻ / mức làm tối (lựa chọn người dùng).

**Ba lần sửa trong lượt — mỗi lần do số đo máy ảo, không đoán** [ĐO logcat `KachiContrast R29 họ` + dòng dưới sàn]:
1. Nền của MẶT (cả thanh 1920 px) làm nền đo cho mọi chữ ⇒ điểm kẹt 0,22 ở mọi họ (hộp nền phủ trọn dải độ chói) ⇒ đo dưới vùng TỪNG
   phần tử (`AdaptiveInk.groundOf`), mặt chọn trên hợp các số đo đó.
2. Pill thanh trên mang màu của CHỦ ĐỀ ⇒ khi thanh cần mực tối, icon tối nằm trên pill tối ⇒ mặt pill/DIM theo HỌ (`AdaptiveInk.face`).
3. Tối: đĩa "tông mềm" trên ảnh trượt ⇒ 9 nút kéo cả lớp phủ 3D sang mực tối, số hàng chân còn 1,49 ⇒ hai kiểu đĩa đã thiết kế (tông
   mềm / kính tối, cùng glyph sáng) chọn CHUNG cho cả nhóm nút của mặt.
(Bẫy công cụ: worktree đo không có `app/src/vehicleTest/assets` (bị ignore) ⇒ bản dựng đầu lùi widget 3D về 2D — chép tài sản rồi đo lại.)

**Máy ảo** [ĐO emulator-5556, vehicleTest, hồ sơ QA2 — widget 3D ô 0, sơn steelgrey, ảnh nền xe = ảnh màn]: trước = 3.1.5 (210) HEAD
`fb166537`; sau = cây làm việc này. Sáng/Tối × wall-03 (tối) · wall-07 (rối nhất, cạnh 16,1) · wall-10 (sáng) × làm tối 0/45 × o20/o70
= 24 cảnh mỗi bản; cùng phép đo §12 (`r29meas` + cờ cực mực). Bỏ `−`/`+` (đo với góc ngoài đĩa — chú thích ¹ §12b) và thẻ phải
(media "Chưa có kênh điều khiển" — ngoài phạm vi T29, cùng số hai bản).

| Mặt · cảnh | Trước: chữ / icon tệ nhất (dưới sàn) | Sau: chữ / icon tệ nhất (dưới sàn) |
|---|---|---|
| Thanh trên · Sáng o70 | 5,96 / 3,92 (0/66) | 4,14 / 3,32 (2/66) |
| Thanh trên · Sáng o20 | 5,30 / 3,54 (0/66) | **1,55 / 1,11 (32/66)** |
| Thanh trên · Tối o70 | 4,97 / 4,47 (0/66) | 2,54 / 2,86 (10/66) |
| Thanh trên · Tối o20 | 3,07 / 3,13 (1/66) | **1,54 / 1,74 (20/66)** |
| Lớp phủ 3D · Sáng | 5,03 / 4,46 (0/44) | 3,92 / 2,68 (4/42) |
| Lớp phủ 3D · Tối | 5,48 / 4,46 (0/42) | 3,92 / 2,68 (6/42) |

Số "trước" đo nét với ĐỆM dưới nó (chính lớp vá owner bác) ⇒ cao mà xấu. Đồng đều [ĐO cờ cực mực]: sau, 0/12 cảnh mỗi chủ đề có chữ
trộn cực trên cùng một mặt (huy hiệu hồ sơ `Q` có mặt đặc riêng, không tính).

**Kết luận [ĐO + SUY]**: tương phản đồng đều theo mặt đạt sàn khi nền dưới mặt đủ "một phía" (ảnh tối, o70, Tối wall-03: thanh trên
họ#0 điểm 1,44–2,14). Thanh trên ở o20 trên ảnh sáng/rối [ĐO log]: nửa trái nền L ≈ 0,30–0,38 (mực sáng 1,5–2,6), nửa phải L ≈ 0,10
(mực tối 2,8) ⇒ KHÔNG mực đồng đều nào đạt cả thanh — đó là giới hạn vật lý của luật "đồng đều" khi thanh 20 % trong, không phải lỗi đo.
Lối ra duy nhất đúng luật owner là lớp nhuộm ĐỀU cả thanh/thẻ khi đo thấy dưới sàn = **OQ7'** (chạm "tùy user chọn" ⇒ CHƯA làm); hiện
nhận họ tốt nhất + log dưới sàn (fail-loud, `ContrastLedger.belowFloor`).

**Chi phí**: chọn họ chỉ theo sự kiện (nền mặt đổi · phần tử mới · xoay 3D dừng); đo nền dưới phần tử chỉ khi vùng của nó đổi; 0 luồng ·
0 nhịp · không listener mới. Bài off-car: `ContrastInkTest` (quét 3 318 mặt — đạt kiểm độc lập hoặc dưới sàn thật, không bỏ sót họ đạt;
trễ: 40 nhịp dao động ±3 % ⇒ ≤ 1 lần đổi họ) · `AdaptiveContrastContractTest` · `AdaptiveInkSourceContractTest` (chữ `halo` = 0 trong
toàn bộ mã chính).

> **OQ7' CHỐT (owner 15/10):** *"Giữ đúng độ đục anh chọn"* — không phủ mờ thêm khi dưới sàn; chỉ đổi họ mực đồng đều + ghi log dưới sàn. Người dùng tự tăng độ đục thẻ nếu khó đọc.
