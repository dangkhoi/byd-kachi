# kachi-301 · stage 7 · POLISH → icon nút ≠ đèn báo · xi-nhan nháy · "Hé tất cả kính" — off-car, chưa commit

Owner 13/10: *"ok, làm thôi, phân biệt ra dạng icon cho hiểu, với cả cái đèn xinhan có nhấp nháy theo xe không? phần kính thêm 1
action là 50% tất cả kính nhé"*.

## 1. Hai HÌNH: nút vs đèn báo (bảng Đèn + mọi bảng gọi `StateDotPainter.drawIcon`)
- `StateDotPainter.drawIcon(…, scale, tappable)` — CHỮ KÝ GIỮ NGUYÊN (bảng 3D `Car3dDotsLayer` gọi). Bên trong rẽ:
  - **nút** (`drawButton`): icon trong ĐĨA TÔNG đặc (nền đen 80 % `Inks.shadow` + mặt mực sáng 14 % `Inks.tonal`). BẬT = vòng
    nhấn + icon nhấn + quầng hai lớp · TẮT = đĩa, KHÔNG vòng · CHƯA BIẾT = vòng đứt nét.
  - **chỉ-xem** = `drawLamp` (MỚI, công khai): hình đèn TRẦN, không đĩa/không vòng; viền bóng đen 80 % (chính hình đèn tô tối vẽ
    9 lần lệch 1 dp — tint là bộ lọc lúc vẽ nên VectorDrawable không dựng lại bitmap). BẬT = màu loại đèn + quầng nhỏ cùng màu
    25 % · TẮT = mực sáng 60 % · CHƯA BIẾT = mực "chưa đọc".
- `:core` MỚI `LampTone.kt` — màu đèn báo theo MÃ ĐỌC (dữ liệu, quy ước ISO 2575/UN R121): pha = xanh dương · cốt/sương mù
  trước/hông/ban ngày = xanh lá · sương mù sau = hổ phách · xi-nhan = hổ phách (màu BÓNG thật, theo yêu cầu — ISO đèn báo là xanh
  lá; ghi trong KDoc). Mã lạ ⇒ `null` ⇒ màu nhấn.
- `CarIconBoardView`: có nút ⇒ `drawIcon(tappable = true)` · chỉ-xem ⇒ `drawLamp(…, LampTone.of(cell.id)?.argb)`. Trợ năng
  không đổi ("· chạm: bật/tắt" vs "· chỉ xem").
- Tương phản [ĐO bài canh `LampIndicatorContractTest`, Tối + Sáng × ảnh L 0,0395 / 0,4695 / 0,598]: mọi nét ≥ 3:1. Lần chạy đầu
  ĐỎ thật: icon nhấn trên mặt tông = **2,55:1** ở ảnh sáng P95 khi nền nút 55 % ⇒ nền nút nâng lên 80 % (`Inks.shadow`) ⇒ xanh.

## 2. Xi-nhan — nguồn số đọc [ĐO đọc mã] và nhịp nháy
- `light_left_turn`/`light_right_turn` = `BYDAutoLightDevice.getLightStatus(4/5)` (`HalReadTables.kt:33`), đọc ở nhịp **CHẬM
  10 s** (`CarStatusRepository.kt:54-55` `slowMs`; `CarDataAdapter.kt:319` trong `readSlow`). Đường tới bảng:
  `KachiHomeWiring.kt:438` → `WorkspaceView.renderInternal` → `GroupTileView.refresh` → `GroupBoard.of`.
- [ĐO xe 2026-09-25, spec `camera-turn-signal-hal-socket.html`] getter này trên trim đã đo **LUÔN trả 0**. Nguồn xi-nhan THẬT là
  socket HAL helper `127.0.0.1:19322` (`HalSignalClient` → `CameraSignalController.tick`) — báo TRẠNG THÁI (ON lúc bật, OFF ~4,2 s
  sau khi tắt), không phải pha bóng — và **CHỈ camera dùng**, không chảy vào `CarStatus.lights`.
- ⇒ Trả lời owner: **hiện tại KHÔNG nháy theo xe**. Số đọc 10 s không thể theo pha ~1,5 Hz, và trên xe đã đo nó luôn 0.
- Đã làm: khi số đọc nói BẬT, điểm xi-nhan nháy TẠI CHỖ (`LampTone.litAt`: sáng 333 ms / tắt 333 ms ≈ 1,5 Hz, bắt đầu SÁNG);
  **không đồng bộ pha bóng thật** (KDoc `LampTone` + `CarIconBoardView.blinkKind`). [SUY] trên xe hôm nay sẽ không thấy nháy vì
  getter trả 0.
- Hiệu năng: không vòng hoạt hoạ. Chỉ khi có đèn đang nháy + bảng `isShown` + cửa sổ VISIBLE ⇒ hẹn ĐÚNG MỘT
  `postInvalidateDelayed` tới mép pha kế (≤ 3 lần vẽ/giây, không chồng hẹn — `blinkAt`). Tắt/chưa biết ⇒ xoá mốc ⇒ hết hẹn ngay ở
  lần vẽ kế; tách cửa sổ ⇒ xoá; màn tắt ⇒ cửa sổ ẩn ⇒ không hẹn; hiện lại ⇒ `onWindowVisibilityChanged` vẽ lại.
- **Mở khoá (cần owner duyệt — chưa làm):** cho bảng Đèn nghe cùng socket 19322 (vd `HalSignalClient` đẩy trạng thái vào
  `CarStatusTap`/một luồng riêng cho `lights.leftTurn/rightTurn`) ⇒ điểm bật/tắt theo xe thật (vẫn nháy tại chỗ, pha không đồng bộ).
  Chi phí: helper chỉ chạy khi tính năng camera xi-nhan bật — cần quyết có bật helper chỉ vì widget không.
- `DummyCarReceiver` (vehicleTest): `--ez turn_l true` / `--ez turn_r true`.

## 3. Kính — "Hé tất cả kính"
- Dùng `ControlDef` SẴN CÓ `win_half_all` ("50% 4 kính", `setAllWindowState(4,4,4,4)`, tier OVERDRIVE — chưa kiểm trên xe, như
  `windows_all`). Không đường HAL mới.
- `:core` `ActionMatrix.halfAll` = việc của CẢ XE có mức dương thấp nhất, thấp hơn `openAll` (đọc `level`, không tên nút).
- `ActionMatrixCompact`: chân 3 nút icon cách đều, 48 dp, không chữ: mở (⇊) · **hé (˅, kính hạ nửa)** · đóng (⇈). Hé = `kit.set(id, 1)`
  (đặt BẬT tường minh, cùng hàm ghi ô; qua `gated`).
- Icon `design/glyph/win_half_all_g.svg` → `gen-icons.py` → `ic_win_half_all_g.xml` (`--check`: 132 tệp khớp byte) +
  `icon-grammar.json`. Chuỗi `kwin_half_all` ×5 (vi "Hé tất cả kính" · en · zh · th · ms).

## Test [ĐO XML, gradle-locked.sh]
- `:core` tập trung (`*LampTone* *GroupActionMatrix* *WindowCycle* *CarIconBoard* *LangCoverage* *SourcePathHygiene* *KeyCtl*
  *CapabilityIcons*`): **106 / 0**.
- `:app` tập trung (`*CarIconBoard* *LightCarBoard* *ActionMatrix* *GroupActionMatrix* *WindowCycle* *WindowControlIcon* *IconStyle*
  *ZeroBorder* *ThemePalette* *LauncherI18n* *LayeringRules* *DummyCarTap* *LampIndicator* *CarDot* *LangCoverage* *SourcePathHygiene*
  *GroupTile* *KeyCtl*`): **144 / 1 đỏ / 1 bỏ qua**. Đỏ duy nhất = `GroupTileWiringContractTest` (`Car3dWidgetView.kt`/`Car3dTaps.kt`
  chưa khai) — tệp của agent 3D, đã đỏ từ TAPFIX.
- Mới: `LampToneTest` (4: màu theo loại, chỉ xi-nhan nháy, nhịp 1,5 Hz + mép pha, `halfAll` = `win_half_all` mức 50 + args
  `[4,4,4,4]`) · `LampIndicatorContractTest` (4: hai hình, tương phản ≥ 3:1, nhịp chỉ khi bật+hiện, số giả). Sửa có trích owner:
  `ActionMatrixWiringContractTest` (2 → 3 nút chân, thứ tự mở·hé·đóng) · `WindowControlIconContractTest` (+ `win_half_all_g`) ·
  `LightCarBoardContractTest` (`tappable = …` → `drawIcon`/`drawLamp`).
- Thử ĐỎ: `SHADOW_ALPHA` 0xCC → 0x40 ⇒ `LampIndicatorContractTest` đỏ · `litAt` đảo pha (bắt đầu tắt) ⇒ `LampToneTest` đỏ; khôi
  phục ⇒ xanh. (Bài tương phản còn đỏ thật ở lượt đầu — xem §1.)
- Call site §8: `drawLamp(` 1 (+ `drawIcon` nội bộ) · `LampTone.of(`/`blinks(`/`litAt(`/`nextEdgeIn(` ≥ 1 · `scheduleBlink(` 1 ·
  `blinkKind(` 1 · `mx.halfAll` 1 · `R.drawable.ic_win_half_all_g` 1 · `R.string.kwin_half_all` 1.
- `:app:assembleVehicleTest` xanh (APK 83 MB).

## Máy ảo emulator-5556 [ĐO] — CHƯA soát được bằng mắt
- Cài: `install -r` thường ⇒ `INSUFFICIENT_STORAGE` (406–496 MB trống); cài lại với ngưỡng TẠM 100 MB rồi `delete` (đã trả,
  `get` = null) ⇒ Success. KHÔNG xoá dữ liệu nào (sherpa trong `files/sherpa` vẫn VẮNG từ lượt TAPFIX — chưa trả, sao lưu ở
  scratchpad `sherpa-bak/inner.tar`).
- ⚠ Bố cục QA2 lúc này: `slot_0 = widget:w_car3d` (agent 3D đổi, KHÔNG phải lượt này), slot 1 = g_trip, slot 2 = g_windows. Lượt
  này KHÔNG sửa bố cục.
- ⚠ HOME **sập** ngay khi ô 3D nạp: `JNI DETECTED ERROR … use of invalid jobject` trong `com.google.android.filament.Box.nSet` ←
  `Car3dRenderer.anchor` ← `Car3dWidgetView.onLoaded` (logcat 19:43:22, pid 6755). Mã của agent 3D — báo orchestrator. Màn để
  lại: hộp "Select a Home app" (đã đóng hộp "Kachi keeps stopping"); số giả BẬT (speed 0, P, turn_l).
- ⇒ Owner cần nhìn trên máy ảo khi ô 3D hết sập (hoặc khi ô 0 trả về `widget:g_lights`): `am broadcast -a
  com.byd.clusternav.vehicletest.DUMMY_CAR -p com.byd.launcher --ez on true --ez turn_l true` ⇒ điểm xi-nhan trái nháy hổ phách.

## Còn mở
- 🚗 Xi-nhan theo xe thật cần nguồn socket (mục 2 "Mở khoá") — quyết định owner.
- `win_half_all` OVERDRIVE (chưa kiểm trên xe) — như nút "mở tất cả".
- Soát bằng mắt máy ảo (bị chặn bởi sập 3D) · spec `kachi-301` §Tasks/§Design + backlog chưa ghi POLISH (một chủ spec — orchestrator).
