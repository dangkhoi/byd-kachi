# kachi-301 · stage 5 · V22 (R22 lượt thẩm mỹ widget — owner 13/10 "nhìn thô" → brief Kim) — DONE (off-car, chưa commit)

Spec: `docs/specs/kachi-301-cast-steps-dialog-ia.html` R22. Bằng chứng: `docs/diagnostics/widget-visual-301-2026-10-13.md` +
`docs/diagnostics/widget-visual-301/` (11 JPG ≤ 1280 px, ~0,8 MB; `harness/contrast.py`, `harness/inventory-r22.txt`).

## Kèm theo (orchestrator giao giữa lượt)
- Đỏ #1 `LauncherI18nContractTest.tang ve khong doc nhan GOC cua core`: `GroupCellGrid.Cell.label` → `labelView`;
  `GroupCellFit.Columns(label, value)` → `(labelPx, valuePx)` (+ `GroupCellFitTest`). Xanh [ĐO XML].
- Đỏ #2 `SourcePathHygieneTest`: `widget-fit-301/harness/{batch,shot,loc}.sh` đọc `WFIT_DIR`, `owner.sh` đọc `WFIT2_DIR`
  (`${VAR:?…}`), không còn đường tuyệt đối tới scratchpad. Xanh [ĐO XML].

## Tệp
Mới: `app/.../KachiWidgetType.kt` (vai chữ + sắc lĩnh vực + chip) · `KachiMeter.kt` (thanh mức) · `WallDefault.kt` (nền mặc định) ·
test `app/.../WidgetVisualContractTest.kt` (5 bài) · `docs/diagnostics/widget-visual-301*`.
Sửa: `GroupTileViews.kt` (498) · `GroupTileParts.kt` (CellBinder) · `GroupCellGrid.kt` · `WidgetViews.kt` · `WidgetTelemetry.kt` ·
`ReadTile.kt` · `RingView.kt` · `WorkspaceView.kt` (500) · `WorkspaceViewSlotDomain.kt` (xoá `slotDomain`) · `WallGlass.kt`
(`plain(view, Drawable?)`) · `WallpaperController.kt` · `KachiHomeRender.kt` · `KachiHomeActivity.kt` (`wallpaper` internal) ·
`KachiSpace.kt` (`HEAD_CHIP`, `HEAD_CHIP_PAD`) · `KachiPaletteSeeds.kt` (`Backdrop`, `BACKDROP_DARK/LIGHT`) ·
`core/.../GroupCellFit.kt` (tiers: NGANG trước DỌC) · `core/.../GroupCellFitTest.kt` (đảo 1 khẳng định, trích owner 13/10 + 1 bài mới).
`KachiPalette.kt`/`KachiTheme.kt`: thử hạ độ đục thẻ-trên-ảnh 0,8→0,64 rồi HOÀN NGUYÊN (byte-y-nguyên HEAD).

## Đổi gì (nhìn thấy)
Số chính light 40sp + đơn vị nhỏ lùi màu · số phụ medium, đơn vị 0,72× mờ · không còn DEFAULT_BOLD/giả đậm · đầu thẻ = chip 28 dp
màu lĩnh vực + tiêu đề medium · giá trị chỉ-đọc là HÀNG `nhãn … số` (hết hộp trong hộp; "Bật" = mực nhấn) · thẻ trung tính (hết
nhuộm xanh két/nâu) · thanh mức mảnh cho `%` (pin, SOH) · vòng đo mảnh hơn · NỀN MẶC ĐỊNH "phòng trưng bày" dựng bằng mã đi qua
đường kính/ảnh nền (Tối + Sáng) khi người dùng không có ảnh.

## Test [ĐO XML, gradle-locked.sh]
Tập trung (core `*Fit* *Grid* *Group* *Widget* *CellText* *SourcePath*` + app `*Widget* *GroupTile* *Fit* *Theme* *Palette*
*ZeroBorder* *Spacing* *TypeScale* *SurfaceMaterial* *SurfaceContrast* *IconStyle* *Composite* *CellText* *LayeringRules*
*KachiChrome* *LauncherI18n* *Wall* *Glass* *ColorChoice* *ChromeOpacity* *Ring* *ReadTile* *DummyCar* *Axis* *Tyre* *Goi2*
*SurfaceTone* *Workspace*): **713 / 0 fail** (core 331 · app 382). Full suite CHƯA chạy (orchestrator).
Red-proof: bỏ `value.requestLayout()` ở `CellBinder` ⇒ `WidgetVisualContractTest` đỏ; khôi phục ⇒ xanh. `hang ngang thang moi dang
doc - R22` khoá thứ tự bậc mới.
Call site §8 đều ≥ 1: `KachiWidgetType.{hero,value,unit,caption,title,withUnit,accentOf,chipIcon,heroFace}` · `KachiMeter(` ·
`KachiMeter.{fractionOf,bounded}` · `WallDefault.build(` · `showDefault()` ×2 · `onThemeChanged()` ×2 · `HEAD_CHIP_PAD`.

## Máy ảo [ĐO]
- APK `:app:assembleVehicleTest` 3.1 (205) — **CHƯA bump** (§9 orchestrator). Cài 13:2x.
- ⚠ `/data` máy ảo rò ~55 MB mỗi lần cài (còn 90 MB, `INSUFFICIENT_STORAGE` cả khi hạ ngưỡng) ⇒ **đã khởi động lại máy ảo**
  (13:0x) ⇒ còn 736 MB. Hai ngưỡng `sys_storage_threshold_max_bytes`/`_percentage` dùng tạm khi cài đều đã `settings delete`
  (giá trị gốc `null`, đã kiểm lại = `null`).
- Kiểm kê co giãn (số giả, vi, 25 widget × 4 cỡ): **106/106 bad=0** trên bản cuối.
- Tương phản chữ min: nền mặc định 6,77 (tối) / 5,01 (sáng); trên ảnh Seal 5,98–6,57 (tối) / 4,59–4,63 (sáng); `—` mờ 4,71.
  Đồ hoạ: thanh đầy/rãnh 5,64 / 3,96; icon chip 6,66 / 4,53.

## Màn hình để lại cho owner
HOME, bố cục owner (QA2: YouTube · Lốp · Chuyến đi), **Tiếng Việt**, **Tối** (`QA2__theme_mode` NIGHT như owner để), **số giả
BẬT** (RAM — mất khi Kachi khởi động lại; bật lại: `am broadcast -a com.byd.clusternav.vehicletest.DUMMY_CAR -p com.byd.launcher
--ez on true`). Ảnh nền: **BẬT, Seal-08** (ảnh tối nhất) — prefs owner + `QA2__wallpaper_prefs=true;1800;FILL;45` (khác bản sao
lưu đúng một dòng này); 13 ảnh Seal còn lại dời vào `…/files/wallpapers/_hold/` để chỉ hiện Seal-08 (trả lại: `mv _hold/*.jpg .`).

## Đề nghị INDEX/BACKLOG (orchestrator gộp)
- INDEX: `diagnostics/widget-visual-301-2026-10-13.md` + `diagnostics/widget-visual-301/` — Evidence 2026-10-13.
- BACKLOG mới: `WIDGET-HIDE-NODATA-ROWS` (brief §2.6, cần owner + xe) · `DOCK-TILE-ON-GRADIENT-RESTRAINT` (brief §2.7) ·
  `PERF-GLASS-DEFAULT-ON` (đo khung `perf-snapshot.sh` khi kính bật mặc định — mục 5 tài liệu) · `BOARD-CANVAS-ROWS` (lốp/cửa).
