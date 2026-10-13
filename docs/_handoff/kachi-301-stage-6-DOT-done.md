# kachi-301 · stage 6 · DOT (R22/R23: chấm tròn thay "gạch xanh" trên ảnh xe) — DONE off-car (máy ảo), chưa commit

Owner 13/10: *"thay vì vẽ hẳn 1 cái gạch màu xanh, mình vẽ chấm tròn thôi, như bọn kinex, kimlauncher thấy nó đẹp hơn"*.
Bằng chứng + tương phản: `docs/diagnostics/widget-visual-301-2026-10-13.md` §9 (chỉ văn bản).

## Thiết kế
Một chấm mỗi vật tại tâm mảnh kính ĐO trên chính ảnh (`CarDots.center` ← `CarArtPanes.paneCenter` ← `CarArt`; bảng cửa: `art.part`).
UNKNOWN = vòng đứt nét trắng 55 % · CLOSED = vòng 1,25 dp trắng 80 % · PARTIAL = vòng nhấn + quạt theo tỉ lệ thụt trong vòng 0,75 dp (từ
12 giờ; chọn quạt vì 35/50 % = 126°/180° rõ ở 8–12 dp; kẹp 30°..330°) · OPEN = chấm đặc nhấn + quầng 35 % (CHỈ mở hẳn, ngoài đĩa tối). Đĩa
tối 55 % dưới mọi chấm (giữ ≥ 3:1 trên sơn sáng). Cỡ 0,14 × cạnh nhỏ khung xe, kẹp 10–12 dp (sàn 10 dp sau khi bỏ số: ở 8 dp quạt chỉ vài px). `lit` ⇒ vòng mang màu nhấn. Vùng chạm không đổi.
**Owner 13/10 (giữa lượt, qua orchestrator): *"đẹp, bỏ số luôn em, vì cái chấm nó có thể hiện tương đối % rồi"*** ⇒ GỠ nhãn `%` cạnh xe ở
mọi cỡ (cùng `CompactMetrics.labelPx` ở `:core`, `CarLevelsLayer.anchorY`, `CarDots.labelled`); số chính xác ở `contentDescription` vùng chạm
(`Kính phụ · 35 % · Mở hẳn` [ĐO uiautomator]).

## Tệp
Mới `:core`: `CarDots.kt` (68) · test `CarDotsTest.kt` (3). Sửa `:core`: `CarArtPanes.kt` (gỡ `fillTo`) · test `CarArtPanesTest` (bài ranh
kính hạ → bài tâm chấm trên dải kính), `CarArtTest` (dòng `fillTo` Seal → tâm chấm trong mảnh Seal) — trích owner 13/10.
Mới `:app`: `StateDotPainter.kt` (103 — `Inks.of` thuần số, `diameterPx`) · test `CarDotWiringContractTest.kt` (4: chung bộ tô · lớp kính hạ
không quay lại · cỡ 8–12 dp · ≥ 3:1 Tối/Sáng ở L 0,0395/0,4695/0,598).
Sửa `:app`: `CarLevelsLayer.kt` (170 → 87: gỡ khoang tối/clip/gradient/nét/đường bao) · `DoorBoardView.kt` (chấm quầng+lõi → bộ tô chung,
mọi bộ phận có chấm) · `ActionMatrixCompact.kt` (gỡ nhãn `%` + cột nhãn; `describeZones`) · `:core` `GroupActionMatrix.kt` (gỡ
`CompactMetrics.labelPx`) + test `GroupActionMatrixCompactTest` (bỏ `labelPx`, trích owner 13/10) · test `ZeroBorderContractTest` (INK_VIEWS: `CarLevelsLayer`
→ `StateDotPainter`, lý do trích owner 13/10).
KHÔNG đụng tệp cast-geometry.

## Test [ĐO XML, gradle-locked.sh]
Tập trung core (`*CarArt* *CarDots* *CarLayout* *SourcePathHygiene* *GroupActionMatrix*`) **34/0** · app (`*CarArt* *CarImage* *CarLevels*
*CarDot* *Door* *Tyre* *ActionMatrix* *GroupTile* *Widget* *ZeroBorder* *ThemePalette* *LauncherI18n* *Composite* *SpacingScale* *TypeScale*
*FitGrid* *SurfaceContrast* *KachiChrome*`) **291/0**. Lượt đầu đỏ thật `SpacingScale` "core không giữ số dp" (cỡ dp để ở `CarDots`) ⇒ dời
`diameterPx` sang `:app`. Thử ĐỎ: `BACKING_ALPHA` 0x8C → 0x40 ⇒ `CarDotWiringContractTest` đỏ (1,67:1); khôi phục ⇒ xanh. Full suite CHƯA chạy.
Thử ĐỎ 2: `MIN_SWEEP` 30 → 0 ⇒ `CarDotsTest` đỏ 1/3; khôi phục ⇒ xanh. Sau lượt bỏ số: core 34/0 · app 291/0 (cùng bộ tập trung).
Call site §8: `StateDotPainter(` 2 · `StateDotPainter.diameterPx(` 2 · `CarDots.ofCell(` 1 · `CarDots.ofPart(` 1 · `CarDots.center(` 1 ·
`describeZones(` 2 · `fillTo`/`labelled`/`anchorY` 0.

## Máy ảo emulator-5556 [ĐO]
`:app:assembleVehicleTest` 3.1 — CHƯA bump (orchestrator). `/data` trống 572 MB trước cài (không khởi động lại). Cài `-r` Success; log
`OemCarArt: car art = common_top_view_img_6125f_ivi_0x8a`; số giả bật lại bằng broadcast `DUMMY_CAR`. Ô Kính 2×3 (bản cài 2, sau lượt bỏ số): 4 chấm ở 4 kính —
trước-trái vòng, trước-phải quạt 35 %, sau-trái chấm đặc + quầng, sau-phải vòng; không còn nhãn `%`, không vạch/đường bao trên kính.
Ảnh (có ảnh BYD) CHỈ ở thư mục tạm của phiên — không trong repo.
Màn để lại: HOME, bố cục owner (YouTube · Chuyến đi · Kính 2×3), Tiếng Việt, Tối, số giả BẬT.

## Còn mở
- [CHƯA ĐO trên máy ảo] bảng Sáng (chỉ tính), vòng đứt nét "chưa đọc" (số giả đọc đủ), bảng Cửa (không có trên bố cục owner).
- Owner cần nhìn: quầng của chấm mở làm chấm mở trông to hơn vòng đóng (cố ý nhấn) — chỉnh `HALO_DP` nếu owner thấy nặng.
- Spec `kachi-301` §Tasks/§Design chưa ghi DOT (một chủ spec — orchestrator ghi).
