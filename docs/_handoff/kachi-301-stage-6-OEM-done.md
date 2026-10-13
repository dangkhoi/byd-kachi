# kachi-301 · stage 6 · OEM (R23: ảnh xe chính hãng BYD cho bảng hình xe) — DONE off-car (máy ảo), chưa commit

Owner 13/10 (trong phiên, qua orchestrator): *"cho phép dùng ảnh firmware BYD trong bản kiểm thử"* — CHỈ `vehicleTest`.
Bằng chứng + bảng ánh xạ: `docs/diagnostics/oem-car-art-2026-10-13.md` (chỉ văn bản, không ảnh BYD trong repo).

## Ánh xạ [ĐO jadx CarSettings + aapt2 + unzip trên fw-2606]
Seal = `car.type` 138 = 0x8a (`CAR_AUTO_TYPE_EK`). Ảnh = `common_top_view_img_6125f_ivi_0x8a` (công thức BYD
`common_top_view_img_ + "6125f_ivi" + _0x%x(vehicleId) [+ _right]`), 840×576, chỉ hdpi (không `-night`), bỏ nóc, đầu xe quay TRÁI,
sườn trái ở mép dưới (vô-lăng ở nửa dưới). Loại: `setting_roof_*` (không có nhánh 138 ⇒ Han `hcb`), `dilink_audio_car_bg_common`
(chỉ nội thất), `common_default_…_0x8a` (¾ — không đặt được kính/bánh).

## Tệp
Mới `:core`: `CarArt.kt` (145 — `CarArt` cắt+xoay+soi gương, `CarArtCatalog.DEFAULT/SEAL_TOP/byKey/oemTopViewName`) ·
test `CarArtTest.kt` (4).
Sửa `:core`: `CarArtPanes.kt` (55 — uỷ quyền cho `CarArt`, tham số `art` mặc định = ảnh mặc định; số đo cũ chuyển vào `DEFAULT`).
Mới `:app` main: `OemCarArt.kt` (122 — `createPackageContext(PKG,0).resources.getIdentifier` → `openRawResource`; giải một lần/tiến
trình; cỡ ≠ số đo ⇒ từ chối; `markBroken` ⇒ lùi mặc định; móc `bundled` cho vehicleTest).
Sửa `:app` main: `CarImageStore.kt` (480 — `builtinSignature`/`artOf`/`isBuiltin`/`OEM_PREFIX`, `loadBuiltinScaled`) ·
`CarImageLayer.kt` (`defaultArt` = ảnh dựng sẵn; `val art`) · `CarLevelsLayer.kt` (hình học theo `art`, đổi art khi BYD hỏng) ·
`CarImageView.kt` (`val art`) · `TyreBoardView.kt` (`art.wheel`) · `DoorBoardView.kt`/`CarMiniView.kt` (`car.art.part`) ·
`AndroidManifest.xml` (`<queries>` gói BYD) · `TileSize.kt` (`stepIconDp`) · `ControlTileFactory.kt` (496, ô STEP dùng `stepIconDp`).
Mới `:app` vehicleTest: `OemCarArtTestProvider.kt` (provider không xuất gán `OemCarArt.bundled`) + manifest + 
`assets/oem-car/common_top_view_img_6125f_ivi_0x8a.png` (**`.gitignore`** — không lên repo; thiếu ⇒ lùi ảnh mặc định).
Test `:app`: mới `OemCarArtSourceSetContractTest` (3: không tên/không trùng SHA-256 ảnh BYD trong main/debug/release · đường release chỉ
`createPackageContext`+`getIdentifier`, khoá lỗi lượt đo cỡ · chỉ vehicleTest gán móc + `<queries>`); sửa `GroupTileWiringContractTest`
(`art.part(s.part)`), `BarScaleBudgetContractTest` c (mô hình ô chỉ-icon, trích owner 13/10 "giảm text").
Docs: diagnostics mới · INDEX `docs/README.md` · BACKLOG `OEM-CAR-ART`, `OEM-CAR-ART-MORE`, `DOCK-STEP-ICON`.

## Đỏ `BarScaleBudgetContractTest` c — gốc + sửa
Gốc [ĐO số học bài c]: R22·R đổi `TileSize.DOCK` icon 20 → 24 dp. Ô STEP (icon + lề XS + hàng −/+ 27 dp + đệm 2×4) = 63 dp > ô thanh
DỌC 60 dp (+1 px) ⇒ tràn THẬT. Nửa "nhãn 2 dòng" của bài đã bị owner thay (nhãn GONE khi có icon). Sửa ở mã: `stepIconDp = ICON_S`
cho DOCK (ô STEP 59 dp); bài c khoá cả hai kiểu thật (chỉ-icon / không-icon). Thử ĐỎ: đặt lại `stepIconDp = ICON_M` ⇒ bài đỏ.

## Test [ĐO XML, gradle-locked.sh]
- Bộ đầy đủ: **app testDebugUnitTest 2 338/0 (1 skip) · core 5 194/0**.
- vehicleTest: `BarScaleBudget` 6/0 · `BarScaleWiring` 8/0 · `OemCarArtSourceSet` 3/0 · `CarImage*` 31/0 · `GroupTileWiring` 17/0 ·
  `DummyCarTap` 2/0. core `SourcePathHygiene` 3/0 · `CarArt*` 7/0.
- Thử ĐỎ: `CarArtTest` (tắt `rotateCw` Seal) 3/4 đỏ · `BarScaleBudget` c đỏ · `OemCarArtSourceSet` đỏ khi chép ảnh đổi tên vào
  `src/main/assets/car/` và khi khôi phục lỗi `?: return null` của lượt đo cỡ. Đều khôi phục ⇒ xanh.
- Call site §8: `OemCarArt.source(` 1 · `.decode(` 1 · `.markBroken(` 1 · `builtinSignature(` 4 · `artOf(` 2 · `stepIconDp` 3 ·
  `art.part(` 2 · `art.wheel(` 2 · `oemTopViewName(` 1 · `OemCarArt.bundled =` 1 (vehicleTest).

## Máy ảo emulator-5556 [ĐO]
APK `:app:assembleVehicleTest` 3.1 (205) — CHƯA bump (§9 orchestrator). `/data` 661 → 608 MB (không cần khởi động lại). Lần cài 1 lộ lỗi
thật (lượt đo cỡ `decodeStream` luôn null ⇒ "hỏng") — đã sửa + khoá bằng bài contract. Lần 2: log `OemCarArt: car art =
common_top_view_img_6125f_ivi_0x8a`, ô Kính hiện Seal BYD đầu xe lên, kính 100 % (sau-trái) / 35 % (trước-phải) đúng sườn.
Màn để lại: HOME, bố cục owner y nguyên (YouTube · Chuyến đi · Kính 2×3), Tiếng Việt, Tối, số giả BẬT (broadcast lại sau cài).
Ảnh trước/sau (chứa ảnh BYD) CHỈ ở thư mục tạm của phiên — không trong repo.
[CHƯA ĐO] bảng Lốp/Cửa/xe mini trên máy ảo (không có trên bố cục owner; cùng đường `CarImageLayer`, test wiring xanh).

## Còn mở / rủi ro
- 🚗 [CHƯA ĐO] gói CarSettingsPlugins trên xe owner có resource này (fw khác 2606 ⇒ có thể vắng ⇒ tự lùi, log `default`).
- [SUY] `vehicleId` CarSettings = `persist.sys.car.type` (trùng 138 ở Seal). Tay lái nghịch luôn bản LHD. Không có bản đêm.
- Ảnh bỏ nóc ⇒ "kính" là dải mép cửa ≈ 5 % bề ngang khung — vẫn mảnh ở ô 2×3 (cùng nhận xét `CAR-ART-RENDER` của stage R).
- Neo bánh Seal [SUY ±20 px] theo kích thước xe; vùng chạm bảng kính (`GroupActionMatrix.zones`) vẫn theo `CarLayout` chung (lệch
  tâm cửa Seal ≤ 0,03 chiều dài — chấp nhận, không đổi).
- Spec `kachi-301`: chưa ghi §Tasks/§Design cho R23 (một chủ spec — orchestrator ghi).
