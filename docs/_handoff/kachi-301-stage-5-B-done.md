# kachi-301 · stage 5 · B (R22·B: hình xe vật-là-nút · bảng icon Đèn · luật chữ giữ chỗ) — DONE một phần (off-car, chưa commit)

Owner 13/10 (giữa lượt, qua orchestrator): *"như này cũng chỉ là show thông tin, cả mớ số 100% 50% nhìn rối mà"* ⇒ lưới nút chữ
lặp bị bác ở MỌI cỡ. Bằng chứng: `docs/diagnostics/widget-visual-301-2026-10-13.md` §7 + ảnh `widget-visual-301/18…24-*-board.jpg`.

## Tệp
Mới `:core`: `GroupIconBoard.kt` (49 — `IconTile`, `of`, `grid`) · test `GroupActionMatrixCompactTest.kt` (5) · `GroupIconBoardTest.kt` (3).
Mới `:app`: `ActionMatrixCompact.kt` (312) · `CarLevelsLayer.kt` (86, tách từ bản cũ) · `IconBoardView.kt` (161) · `GroupBodies.kt` (25 —
`GroupBody` + chọn thân hình).
XOÁ: `ActionMatrixBoard.kt` (hình xe có thẻ + nút chữ — owner bác) + `GroupActionMatrix.boardFits` + bài test của nó.
Sửa `:core`: `GroupActionMatrix.kt` (+`CompactForm`/`CompactMetrics`/`compactForm`/`compactCarArea`/`Zone`/`zones(minPx)`) ·
`ControlDef.kt` (`verbLevel`) · `ControlRegistry.kt` (động từ kính `Mở hẳn`/`Hé`/`Đóng` + EN `Full`/`Vent`/`Close` + mức 100/50/0) ·
`GroupBoardModel.kt` (`GroupActionCell.level`, `GroupBoardModel.layout`) · `GroupBoard.kt` · `CapabilityGroupModel.kt`
(`enum GroupLayout {AUTO, ICONS}` + `CapabilityGroup.layout`) · `CapabilityGroups.kt` (LIGHTS `layout = ICONS`) ·
`resources/i18n/{zh,th,ms}.tsv` (+2 dòng) · test `GroupActionMatrixTest.kt` (động từ chữ; bỏ bài boardFits).
Sửa `:app`: `ActionMatrixView.kt` (hình xe → danh sách CHỈ dưới ngưỡng; `GroupBody`) · `GroupTileViews.kt` (499, không tăng:
`matrix = GroupBodies.of(...)`) · `CarImageView.kt` (`onImage` — báo lớp đè khi ảnh nạp xong) · `KachiWidgetType.kt` (`pending`,
`light`) · `GroupTileParts.kt`/`ReadTile.kt`/`WidgetTelemetry.kt` (chữ giữ chỗ = MUT, bỏ alpha) · `ActionMatrixList.kt` (KDoc).
Test `:app`: `ActionMatrixWiringContractTest` (+3 bài: thứ tự lùi, chạm qua ô nút thật, chữ giữ chỗ không alpha, bảng icon) ·
`GroupTileWiringContractTest` (notBuilders: Compact/CarLevelsLayer/IconBoardView/GroupBodies; gỡ Board).
`ControlTileFactory.kt` KHÔNG đổi trong lượt này (497).

## Quyết định tự chủ (ghi §7)
- 50 %: **công tắc `Mở hẳn | Hé`** (không nhấn-giữ — vô hình, khó trên xe đang chạy). Lựa chọn là trạng thái giao diện (RAM view).
- Số `%` chỉ hiện ở kính đang mở hoặc `—` khi chưa đọc (không giấu "chưa biết").
- Đèn 2×3: chữ không lọt ⇒ chỉ icon (tên ở contentDescription); ≥ 2×6 có chữ 1–2 dòng.
- Ô đèn sáng: chữ MỰC (mực nhấn trên nền nhấn chỉ 3,44:1 ở bảng sáng).

## Test [ĐO XML, gradle-locked.sh]
Tập trung (`*GroupTile* *GroupCell* *ActionMatrix* *GroupBoard* *Board* *Widget* *Fit* *Control* *IconStyle* *Spacing* *TypeScale*
*LayeringRules* *LauncherI18n* *SourcePathHygiene* *SurfaceContrast* *I18n* *Window* *Light* *Placeholder*): **core 520/0 · app 302/0**.
Full suite CHƯA chạy. Red-proof: đảo `x < 0.5f` + bỏ kẹp `minPx` trong `GroupActionMatrix.zones` ⇒ `GroupActionMatrixCompactTest`
đỏ 2/5; khôi phục ⇒ 5/5 xanh.
Call site §8 (≥1 ngoài định nghĩa): `compactForm(` `compactCarArea(` `zones(` `GroupIconBoard.of(` `.grid(` `GroupBodies.of(`
`ActionMatrixCompact(` `IconBoardView(` `CarLevelsLayer(` `KachiWidgetType.pending(` ×5 `.light(` `onImage` `verbLevel`.

## Máy ảo emulator-5556 [ĐO]
- APK `:app:assembleVehicleTest` 3.1 (205) — **CHƯA bump** (§9 orchestrator). 6 lần cài; **`/data` còn ~137 MB** (rò ~55 MB/lần như
  V22/M) ⇒ lượt sau cần khởi động lại máy ảo trước khi cài. Ngưỡng storage tạm đã `settings delete` (`null`).
- Kiểm kê co giãn 25 widget × 4 cỡ: **106/106 bad=0**.
- Vùng chạm 4 kính: 2×3 133×82/79 px · 2×6 133×136/132 · 6×2 113×95/93 · 4×6 291×295/285 (đích chạm 72 px).
- Logcat: chạm Kính SP ⇒ `win_rr args=[4,1]`; `Hé` + Kính lái ⇒ `win_half_lf args=[1,4]` (cùng đường ControlTileWrite).
- Tương phản chữ min: Kính 6,73/4,73 · Đèn 6,49/4,54 · Khí hậu+Cửa 6,63/4,77 (tối/sáng). `—`: 5,19 sáng (trước 3,15) · 7,55 tối.
  Đồ hoạ (lõi nét): công tắc+chân kính 5,44/4,77 · ô đèn 5,44/3,79. [CHƯA ĐO] thanh kính trên ảnh xe.

## Màn hình để lại
Prefs owner khôi phục y nguyên (`kachi_workspace.xml`, `clusternav_lang.xml` diff rỗng; `clusternav_prefs` chỉ khác
`a11y_proc_start_elapsed`): HOME, bố cục owner YouTube 10×6 · Lốp 2×3 · **Kính 2×3 = hình xe vật-là-nút** (`Mở hẳn` đang chọn),
Tiếng Việt, Tối, ảnh nền Seal như trước, số giả BẬT (RAM). Trạng thái lệnh `win_rr`/`win_half_lf` từ phép thử đã mất khi
khởi động lại Kachi (RAM).

## Chưa làm / đề nghị BACKLOG
- `WIDGET-DOORS-DIAGRAM-CONTROL`: Cửa & khoang — hàng nút `Cốp sau · Cửa sổ trời · Rèm` vẫn là ô nút có nền; cần ánh xạ nút ↔ bộ
  phận trên hình (CarPart) rồi chạm trên hình. Chưa làm.
- `WIDGET-SEATS-BOARD`: không có nhóm ghế trong `CapabilityGroups` (chỉ datum rời `seat_heat_state`/`seat_vent_state`) ⇒ cần owner
  duyệt nhóm mới.
- `WIDGET-CLIMATE-CONTROLS`: nhóm Khí hậu không có nút nào; −/+ nhiệt, quạt, chế độ = thêm khả năng ghi vào widget ⇒ §14 + owner.
- `WIDGET-LIGHTS-2X3-LABELS`: ở 2×3 bảng đèn chỉ còn icon (chữ không lọt) — owner xem có chấp nhận không.
- INDEX: diagnostics §7 + 7 ảnh `*-board.jpg`. [CHƯA ĐO] trên xe: chạm vùng kính / chân / ô đèn.
