# kachi-301 · stage 5 · R (R22·R: bớt "miếng xanh" + bớt chữ — owner 13/10) — DONE (off-car, chưa commit)

Bằng chứng: `docs/diagnostics/widget-visual-301-2026-10-13.md` §8 + 8 ảnh `widget-visual-301/25…32-*-refine.jpg` (≤ 1280 px, 368 KB).
Hướng xe tay (nét mảnh / vector khối) đã thử rồi DỪNG theo orchestrator (owner: "như đồ chơi con nít") — KHÔNG ship; ảnh xe hiện có giữ nguyên.

## Tệp
Mới `:core`: `CarArtPanes.kt` (70 — mặt nạ 4 kính hông đo trên ảnh mặc định + `fillTo`/`paneCenter`/`ASPECT`, KDoc = móc cho render sau) ·
test `CarArtPanesTest.kt` (3).
Sửa `:app`: `CarLevelsLayer.kt` (viết lại: ảnh mặc định + khoang tối cắt mặt nạ + nét nhấn) · `ActionMatrixCompact.kt` (bỏ CarImageView;
công tắc + chân chỉ icon `WindowGlyph`; xe ăn khe trên/dưới; viên công tắc tông thay viền; mặt nạ sóng không `Color.WHITE`) ·
`CarImageLayer.kt` (`defaultArt`) · `CarImageStore.kt` (`DEFAULT_SIGNATURE`, `loadFeathered(defaultOnly)`) · `CarImageView.kt` (gỡ móc
`onImage` không còn ai gọi) · `TyreBoardView.kt` (bỏ thẻ/chấm, số trần canh mép, bỏ đơn vị + viết tắt bánh, NONE→MUT) ·
`DoorBoardView.kt` (chấm quầng+lõi) · `IconBoardView.kt` (chỉ icon, icon theo ô, nền 8 % thay viền tóc) · `TileSize.kt` (`iconOnly`;
DOCK/GROUP chỉ icon, icon 24 dp) · `ControlTileFactory.kt` (ẩn nhãn + contentDescription khi `iconOnly`; `tonalOn`; icon BẬT ACCENT_INK) ·
`KachiThemeDrawables.kt` (`gradientSoft` → `tonalOn`) · `KachiPalette.kt` (tileOnFrom/To phẳng 16 %/14 %) · `KachiTopStrip.kt` (nút Cài
đặt tông) · `KachiTheme.kt`/`ControlLevelBar.kt` (chú thích) · `:core` `GroupActionMatrix.kt` (`bars > 0` → `bars != 0`: dương tính giả
của `TyreNoThresholdContractTest`).
Test sửa (lý do trong mã, trích owner 13/10): `ThemePaletteContractTest` (icon BẬT = ACCENT_INK + bài mới ≥ 3:1 trên nền tông) ·
`KachiChromeContractTest` (tonalOn; thẻ lốp đã gỡ) · `Goi2FeatureWiringContractTest` (canh mép, không thẻ/chấm/viết tắt) ·
`ZeroBorderContractTest` (INK_VIEWS: CarLevelsLayer, ActionMatrixCompact).
Đỏ CÓ TỪ TRƯỚC (bản B) đã xanh lại trong lượt: ZeroBorder (setStroke ActionMatrixCompact/IconBoardView), Color.WHITE (2 tệp),
TyreNoThreshold (`bars > 0`), CarImageLayerContract (`onImage`).

## Test [ĐO XML, gradle-locked.sh, --rerun-tasks]
Tập trung (`*GroupTile* *ActionMatrix* *GroupBoard* *Board* *Tyre* *CarImage* *CarArtPanes* *CarLayout* *Widget* *Fit* *Control* *Dock*
*Theme* *Palette* *IconStyle* *ZeroBorder* *SurfaceContrast* *Spacing* *TypeScale* *LayeringRules* *LauncherI18n* *SourcePathHygiene*
*KachiChrome* *ColorChoice* *Composite* *Goi2* *ActionMacro* *CellText* *IconBoard*` + app `*TileSize* *Kachi*`): **core 657/0 · app 529/0**.
Full suite CHƯA chạy. Red-proof: đảo `CarArtPanes.fillTo` (tô từ đáy) ⇒ `CarArtPanesTest` đỏ 1/3; khôi phục ⇒ 3/3.
Call site §8: `tonalOn(` 2 · `CarArtPanes.` 8 · `DEFAULT_SIGNATURE` 4 · `defaultArt = true` 2 · `size.iconOnly` 1 · `WindowGlyph(` 3.

## Máy ảo emulator-5556 [ĐO]
APK `:app:assembleVehicleTest` 3.1 (205) — CHƯA bump. Khởi động lại máy ảo 2 lần (rò ~55 MB/lần cài; lần 2 do INSUFFICIENT_STORAGE); `/data`
còn ~677 MB. Fit 106/106 bad=0. Tương phản: xem diagnostics §8.

## Màn hình để lại
Prefs owner khôi phục y nguyên bản sao lúc đầu (`kachi_workspace.xml`, `clusternav_lang.xml` — diff rỗng): HOME, **bố cục như lúc nhận:
YouTube · Chuyến đi · Kính 2×3** (đề bài ghi "Lốp" nhưng máy lúc đầu là Chuyến đi — để nguyên như owner để), Tiếng Việt, Tối, ảnh nền như
cũ, số giả BẬT (RAM).

## Còn mở / đề nghị BACKLOG
- `CAR-ART-RENDER` (owner, track riêng): render 3D dựng sẵn (tối/sáng, bóng nền) ⇒ thả vào `CarImageStore.DEFAULT_ASSET` + đo lại
  `CarArtPanes` (móc đã sẵn). Kính hông trên ảnh top-view hiện tại hẹp (~4 % bề ngang) ⇒ ô 2×3 khó thấy; render góc 3/4 sẽ rõ hơn.
- `PROFILE-AVATAR-TONAL`: đĩa chữ cái hồ sơ (Q) vẫn gradient tím.
- `ASSET-DEFAULT-CAR` không đổi. `WIDGET-CAR-MINI-TEXT`: ô xe mini còn dòng "4 cửa đóng" (không thuộc lượt).
- Đơn vị áp suất nay chỉ ngầm hiểu (owner cho phép) — nếu người dùng chọn psi/kPa cần owner xem có muốn một chữ đơn vị nhỏ.
- [CHƯA ĐO] trên xe: chạm vùng kính/công tắc icon/chân icon (cùng đường ControlTileFactory như B).
