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
