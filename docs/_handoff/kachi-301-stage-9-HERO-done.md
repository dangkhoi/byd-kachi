# kachi-301 · stage 9 — HERO R28: widget xe 3D là "hero" (máy ảo; 🚗 chưa đo xe; CHƯA commit, CHƯA bump version)

Owner 13/10 vòng 2 (*"làm hết ra apk mới"*). Trước: `kachi-301-stage-8-POLISH2-done.md`. Spec `docs/specs/kachi-301-cast-steps-dialog-ia.html`
R28 + §9. Chẩn đoán + số đo: `docs/diagnostics/widget-3d-301-2026-10-13.md` §11. Ảnh chụp chỉ ở scratchpad (không vào repo), đọc qua sub-agent (G12).
Chỉ UX: mọi ghi đi qua ô nút SẴN CÓ (`ControlTileFactory` → `ControlWriteFlow`), không lệnh HAL mới, không nhịp/luồng mới.

## Checklist

| # | Việc | Trạng thái | Bằng chứng |
|---|---|---|---|
| 1 | Đèn đọc bật ⇒ khoang hắt sáng ấm, theo lệnh cuối + `LampHold` | ✅ máy ảo | Đường theo dõi có sẵn (POLISH2: `taps.glowing()` mỗi nhịp đổ số + sau hạn `LampHold`); gốc "không thấy": `GLOW` 0,22/0,15/0,06 × phơi sáng ≈ 0,1 ⇒ [ĐO] 0 điểm ảnh khoang đổi. Nay ×24: [ĐO] `tap readl → BẬT` ⇒ khoang qua kính (5,17,28) → (45,40,28), ~3 200 điểm ảnh đổi; TẮT trả lại. `readl` không có ô đọc trên xe ([ĐO] `readKey` rỗng) ⇒ "thật" = lệnh cuối (kể cả giọng/phím) |
| 2 | Hai icon sưởi/mát mỗi ghế, ở ghế, xoay vòng Tắt→1→2→Tắt, icon như chip | ✅ máy ảo | `Car3dHeroLayer` + `Car3dHero` (`:core`). Ô SELECT THẬT ẩn (`seath/seatc/seath_r/seatc_r`), mức thật bơm vào ô qua `Car3dHero.seatControls` (datum chip ghế). [ĐO logcat] `tap ghế seatc mức=1 → 2` ⇒ `setSeatVentilatingState args=[1,3]` · `mức=2 → 0` ⇒ `[1,1]` · `seath 0 → 1` ⇒ `setSeatHeatingState [1,2]`; chip thanh trên theo ("Mức ghế sưởi: Mức 1"). Ghế khuất ẩn (¾ trước-trái: chỉ ghế lái; ¾ sau-phải: chỉ ghế phụ; nhìn trên: cả hai, tách không đè — `Car3dHero.spread`). Đĩa 48 dp; BẬT = đĩa tông nhấn. Kéo bắt đầu trên icon ⇒ thành xoay (không vùng chết) |
| 3 | Hàng chân [nhiệt −/số/+] · [3 nút kính] · [gió −/quạt số/+] · [lấy gió trong/ngoài], canh giữa, hẹp ⇒ ẩn bộ chỉnh | ✅ máy ảo | `Car3dFootRow`: ô STEP thật ẩn (`ActionTile.nudge`/`value` — mới, cùng đường `nudge` của nút −/+), `recirc` TOGGLE (`ActionTile.set`), hình `CapabilityIcons.forState(ac_cycle)`. [ĐO] `tap temp + ⇒ setAcTemperature [0,23,0,1]` · `fan − ⇒ [3]` · `recirc ⇒ TRONG [1]`. [ĐO uiautomator] ô 3D 19–1155 ⇒ hàng 170–1004 (tâm 587 = 587). Đo bề rộng THẬT (`Car3dHero.fits`); ô 1136 px vừa đủ cả bốn cụm. Gốc phụ [ĐO]: `FitScale` phóng con của widget (72 → 104 px) ⇒ thêm `Car3dWidgetView` vào `FitGridLayout.selfFitting` |
| 4 | Áp suất + nhiệt dưới từng bánh, theo xoay, bánh khuất ẩn, màu chỉ trên số; công tắc theo hồ sơ (mặc định BẬT) | ✅ máy ảo | Cùng `TyreBoard.readings` + định dạng đơn vị của `WidgetViews`; điểm chạm đất từ hộp hợp `wheel*` (`Car3dHero.wheels`). [ĐO] ¾ trước-trái: TT "2.5 / 38°C", ST "2.4 / 36°C"; ¾ sau-phải: TP, SP. Cài đặt › Hiển thị & đơn vị › "Hiện thông số lốp" — [ĐO E2E] TẮT ⇒ prefs `;tyre=0`, nhãn biến mất; BẬT lại ⇒ hiện (đặt lại theo camera cũ, không vẽ lại Filament — `Car3dHeroLayer.reproject`). TẮT cũng bỏ 8 datum lốp khỏi nhu cầu đọc. Không góc phủ nào |
| 5 | Mặc định ô 1 = xe 3D (cài mới / hồ sơ chưa lưu) | ✅ | `WorkspaceState.DEFAULT` ô 0 = `w_car3d`; bố cục đã lưu không đổi (`WorkspaceDefault`); xe không mô hình ⇒ widget tự lùi bảng 2D (đường cũ). ⚠ Chi phí đọc: xem "Rủi ro" (`CAR3D-K1-BUDGET`) |
| 6 | Màu "Xanh xám" cho Seal | ✅ máy ảo | `Car3dModel.extraPaints` (chỉ nhóm `eke`), `Car3dPaint.withExtras` (sau các dòng CSV; CSV rỗng ⇒ không hàng). Ảnh owner (ngoài repo): trung vị sơn hông khuất sáng (64,83,112), mặt hắt sáng (135,153,180). Chỉnh trên máy ảo: preset sRGB (0,51; 0,595; 0,72) ⇒ [ĐO] cửa trước xe 3D (62,83,112) ≈ ảnh (63,82,111). Ô "Xanh xám" ở hàng Màu xe 3D (uiautomator) |
| 7a | Mép vành dưới tối hơn trên | ✅ phần | Mâm phát sáng nền × ảnh mâm (`emissive` 0,8 — 1,5 làm nan cháy trắng [ĐO] 1 079 điểm ảnh > 235). [ĐO] đỉnh 6 giờ 65 → ~105 (đỉnh 12 giờ 227); hai cung cạnh đáy 205–215; 0 điểm ảnh > 245. Điểm đáy tuyệt đối vẫn tối hơn (ánh sáng một nắng) |
| 7b | Đốm trắng sau kính hậu khi cốp mở | ✅ không tái lập | [ĐO] sau-¾-phải (yaw −120 — trần `yawMin`), cốp mở: không vùng nào > 235 ngoài mâm; sáng nhất vùng đuôi (255,213,213) = phản chiếu hồng trên mép đèn đỏ. Nghi QA cũ thấy phản chiếu đèn — [SUY], không sửa |

## Files
- `:core` MỚI `Car3dHero.kt`; SỬA `Car3dBinding.kt` (`extraPaints`, `SEAL_STEEL`, mâm phát sáng nền), `Car3dPaint.kt` (`withExtras`), `Car3dPaintChoice.kt`
  (`tyres`), `ColorChoice.kt` (`carTyres`, trường `tyre=0`), `CarDataDemand.kt` (`CAR3D_TYRES` theo công tắc, `HOSTED` temp/fan/recirc, datum ghế),
  `WorkspaceState.kt` (mặc định), `Car3dMath.kt` (`groundRect` — chuyển từ widget, trần 500 dòng).
- `:app` MỚI `Car3dHeroLayer.kt`, `Car3dFootRow.kt`; SỬA `Car3dWidgetView.kt` (499 dòng), `ControlTileFactory.kt` + `TileSize.kt` (STEP `nudge`/`value`),
  `FitGridLayout.kt` (`selfFitting`), `WidgetViews.kt` (định dạng lốp `internal`), `SettingsCarPaintRow.kt` + `SettingsSections.kt` (hàng lốp, tên màu),
  `ThemeHost.kt`, `Car3dRenderer.kt` (`GLOW`), `Car3dModelSource.kt` (màu thêm, `CACHE_VERSION` v17), `strings_kachi_car3d301.xml` ×5.
- vehicleTest `DummyCarReceiver.kt` (ghế + nhiệt/gió/lấy gió giả, nhại lệnh ghi).
- Test MỚI `Car3dHeroTest` (13); SỬA `TyreChipTest` (K1 đo trên màn bảng + bài trần màn 3D), `Car3dSealTest`.
- Docs: diagnostics §11, spec R28 + §9, `docs/README.md`, `PROJECT-BACKLOG.md` (`CAR3D-K1-BUDGET`).

## Test
- Red-proof [ĐO]: `nextSeat` không xoay / `tyreVisible` luôn đúng / mặc định `w_board` ⇒ `Car3dHeroTest` 3 ca đỏ; trả lại ⇒ xanh.
- Bộ đầy đủ `:app:testDebugUnitTest :app:testVehicleTestUnitTest :core:test :car-integration:test :vehicle-contracts:test :offcar-planner:test --rerun-tasks`: **10 360 test · 0 hỏng · 3 bỏ qua** (app debug 2 404 · vehicleTest 2 416 · core 5 339 · car-integration 80 · vehicle-contracts 22 · offcar-planner 99; đếm XML).

## Senior review (Pass R28, chỉ đọc)
APPROVE sau vá P1. P1 phát lại ảnh số cũ sau ân hạn (`Car3dHeroLayer.tap`) — gỡ. P2 ×2 (đổi visibility trong lượt đo; vẽ lại mỗi nhịp) — vá. P3: `CAR3D-FALLBACK-DEMAND` (backlog), hằng chết — bỏ, bốn tệp sát trần 500 (`Car3dWidgetView` 499 · `FitGridLayout` 499 · `ControlTileFactory` 497 · `SettingsSections` 496). Bộ đầy đủ chạy lại sau vá: 10 360 · 0 · 3.

## Hiệu năng (emulator-5556)
- Rảnh 30 s: 0 khung (log `đợt vẽ`), CPU 0,0 %. Icon/nhãn chỉ đặt lại khi Filament vẽ một khung hoặc số lốp/công tắc đổi (không xin khung).
- PSS: ô 0 = `w_board` 245 MB · = xe 3D 292–350 MB ⇒ chênh ~+105 MB như R25 (nền cao hơn POLISH2 vì máy ảo vừa khởi động lại có nạp mô hình giọng).
- Đọc HAL (bài `TyreChipTest`, trần trên): màn mặc định MỚI 240 lượt/phút (168 khi TẮT số lốp). Thêm của R28 trên màn có xe 3D: 8 datum lốp
  (+ mã phán màu) khi BẬT, 4 datum ghế (0 nếu chip ghế đang hiện — mặc định có), 4 `readState` (temp/fan/ac_auto/recirc) ở nhịp chậm. Không nhịp/luồng mới.

## Máy ảo (để nguyên cho owner)
HOME · hồ sơ QA2 · THREE: ô 0 `w_car3d` (lớn), ô 2 `g_windows` (Kính), ô 1 `g_tyres` · Tiếng Việt · Tối · số giả BẬT (speed 0, P, cửa đóng) · màu
`steelgrey` (Xanh xám) · số lốp BẬT · đèn đọc tắt · ¾ trước-trái. APK vehicleTest 3.1 (205) — CHƯA bump.

## Rủi ro / còn mở
- `CAR3D-K1-BUDGET` [P1 — owner quyết]: màn mặc định 3D vượt K1 (150/phút) ngay cả không lốp (xe 3D đã 114/phút từ R25).
- 🚗 chưa chạy trên xe: vị trí icon ghế suy từ neo cửa/kính (tay lái nghịch [CHƯA BIẾT] ⇒ coi tay lái thuận); hàng chân ở ô hẹp hơn ~1 100 px tự ẩn bộ chỉnh.
- Điểm đáy tuyệt đối của vành vẫn ~0,46× đỉnh (ánh sáng); thử thêm phát sáng thì nan cháy trắng.
