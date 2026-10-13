# kachi-301 · stage 5 · M (R22 bảng vật × việc + hình xe — owner 13/10 "rối mà" / "visual thiết kế cho đẹp") — DONE (off-car, chưa commit)

Bằng chứng: `docs/diagnostics/widget-visual-301-2026-10-13.md` §6 + ảnh `widget-visual-301/12…17-*-matrix.jpg` (≤ 1280 px).

## Tệp
Mới: `core/.../GroupActionMatrix.kt` (189 — dựng bảng + `plan` + `boardFits` + `placeOf`) · `core/src/test/.../GroupActionMatrixTest.kt`
(12 bài, dựng từ `CapabilityGroups.WINDOWS/LIGHTS/DOORS/TYRES` thật) · `app/.../ActionMatrixView.kt` (124 — chọn dạng + `MatrixKit`)
· `ActionMatrixList.kt` (126 — danh sách gọn) · `ActionMatrixBoard.kt` (202 — hình xe) · `app/src/test/.../ActionMatrixWiringContractTest.kt` (5).
Sửa: `ControlDef.kt` (`verb`/`verbEn`/`displayVerb`) · `ControlRegistry.kt` (10 nút kính `verb = "100%"|"50%"`) · `GroupBoardModel.kt`
(`GroupActionCell.verb/readKey`) · `GroupBoard.kt` (điền 2 trường) · `GroupTileViews.kt` (499: dựng bảng, bỏ hàng nút phẳng khi có bảng,
`tintOf` ACTIVE → INK, gọn 2 chú thích) · `GroupCellGrid.kt` (`tileNeedPx`/`tileSignature`/`tileTexts` dùng chung; hàng ngắn canh giữa
ở cả hai lưới) · `GroupTileParts.kt` (chú thích) · `ControlTileFactory.kt` (497: `actionTile(def, label: String? = null)`).
Test sửa theo hợp đồng mới: `GroupTileTightSpaceContractTest` (điều kiện hàng nút), `WidgetFitJ1ContractTest` (chữ ký actionTile),
`GroupTileWiringContractTest` (notBuilders + lý do cho 2 tệp ActionMatrix — chúng có bộ dựng nút riêng nên KHÔNG vào `tileFiles`;
quét riêng ở `ActionMatrixWiringContractTest`), `core/.../I18nPairs.kt` + `i18n/nonliteral-allow.tsv` (`displayVerb`).

## Nhóm đổi
- **Kính**: thay hoàn toàn (hình xe ở ô đủ lớn, danh sách gọn ở ô nhỏ). Đây là nhóm DUY NHẤT dữ liệu hôm nay khớp mẫu vật × việc.
- **Mọi nhóm**: luật màu số (ACTIVE không còn chữ nhấn — vd Đèn `Bật` nay mực thường) + hàng ngắn canh giữa (Đèn `Chế độ đèn pha`).
- **CHƯA LÀM** (yêu cầu giữa lượt "as time allows"): bảng icon sáng/tắt cho Đèn, ghế (glyph + chấm mức), Khí hậu (nhiệt lớn giữa +
  icon gió/chế độ), cửa/cốp trên cùng hình. Cửa & khoang đã là bảng hình xe từ U9 (không đổi). Đề nghị BACKLOG `WIDGET-VISUAL-BOARDS`.

## Test [ĐO XML, gradle-locked.sh]
Tập trung (core + app: `*GroupTile* *GroupCell* *Widget* *Fit* *Control* *Window* *Light* *Spacing* *TypeScale* *LayeringRules*
*LauncherI18n* *SourcePathHygiene* *ActionMatrix* *Lang* *I18n* *GroupBoard*): **core 528/0 · app 300/0**. Full suite CHƯA chạy.
Red-proof: `placeOf` trả `null` ⇒ `GroupActionMatrixTest.bon kinh dat dung bon cua…` đỏ; khôi phục ⇒ xanh.
Call site §8 (≥1 ngoài định nghĩa): `ActionMatrixView(` `ActionMatrixBoard(` `ActionMatrixList(` `GroupActionMatrix.of/plan/boardFits(`
`placeOf(` `tileNeedPx(` `tileSignature(` `displayVerb` `mergedCellIds`.

## Máy ảo emulator-5556 [ĐO]
- APK `:app:assembleVehicleTest` 3.1 (205) — **CHƯA bump** (§9 orchestrator). `/data` còn ~510 MB (rò ~55 MB mỗi lần cài, như V22).
  Ngưỡng `sys_storage_threshold_*` dùng tạm khi cài đã `settings delete`.
- Kiểm kê co giãn (harness `widget-fit-301/harness/`, số giả, vi): **106/106 bad=0** (g_windows: LARGE 20 nút chữ · NARROW 21 · SMALL 14 · WIDE 21).
- Tương phản (`widget-visual-301/harness/contrast.py`): xem diagnostics §6 (chữ thật ≥ 4,95; thấp nhất là `—` chưa đọc có từ trước).

## Màn hình để lại cho owner
Prefs owner **khôi phục y nguyên bản sao trước khi đụng** (`kachi_workspace.xml`, `clusternav_lang.xml` — diff rỗng): HOME, bố cục owner
(YouTube 10×6 · Lốp 2×3 · **Kính 2×3**), Tiếng Việt, Tối, ảnh nền như V22, **số giả BẬT** (RAM). Ô Kính 2×3 của owner hiện **danh sách gọn**
(3 hàng + `Đóng cả cụm`; `Kính SP` + `4 kính` bị bỏ vì thiếu cao cho đích chạm 48 dp). Muốn thấy hình xe: kéo ô Kính lên ≥ 4×6.

## Còn mở / đề nghị
- OQ owner: ở 2×3, ưu tiên bỏ hàng nào (hiện: bỏ từ cuối theo thứ tự khai — mất `Kính SP` + `4 kính`)? Hoặc hạ đích chạm <48 dp?
- INDEX: diagnostics §6 + 6 ảnh `*-matrix.jpg`. BACKLOG: `WIDGET-VISUAL-BOARDS` (Đèn/ghế/Khí hậu dạng hình), `WIDGET-MATRIX-2X3-PRIORITY`.
- [CHƯA ĐO] trên xe: chạm nút `100%`/`50%` trong bảng (cùng `ControlTileFactory` ⇒ cùng đường ghi HAL đã PROVEN của 8 nút kính lẻ).
