# kachi-301 · stage 7 — R25 widget xe 3D — HOÀN THIỆN (off-car + máy ảo; 🚗 chưa đo xe; CHƯA commit)

Owner 13/10: *"làm hết đi rồi thử lại"*. Trước: `kachi-301-stage-7-3D-done.md` · `kachi-301-stage-7-LAMPLIVE-done.md`.
Chẩn đoán + bảng 36 lưới + số đo: `docs/diagnostics/widget-3d-301-2026-10-13.md` §7–§9. Backlog mới: `CAR3D-LID-EDGE`, `CAR3D-PINCH-VERIFY`.

## Checklist (bằng chứng: ảnh cắt trong scratchpad — không vào repo — mô tả qua sub-agent; logcat `Car3d`/`KachiCtl`)

| # | Việc | Trạng thái | Bằng chứng |
|---|---|---|---|
| 1 | Đèn đuôi không cháy trắng; đuôi luôn đỏ (tắt đỏ sẫm / sáng đỏ vừa, giữ hoa văn LED), pha trắng, xi-nhan hổ phách nháy | ✅ | `Car3dLamps` (vai theo vị trí) + `emissiveTexture` + `PBRNeutral`; ảnh đuôi: dải đỏ, thấy chấm LED; `Car3dPartsTest`. Đèn trắng phía trước vẫn có vệt sáng mạnh ở dải DRL (đúng là đèn trắng sáng) |
| 2 | Sơn áp MỌI bộ phận sơn kể cả cửa mở | ✅ | gốc = bộ phận gộp `doorXX_open` (đen trùng mặt) — `Car3dDupes`; ảnh: cửa mở cùng màu thân ở 3 khung hoạt cảnh |
| 3 | Một vật liệu kính chung, không viền trắng, không cháy | ✅ | kính chung khoá `glass`; bỏ lớp gộp ⇒ kính cửa mở giống kính cố định (sub-agent: "không viền sáng/trắng"; kính cửa mở hơi xanh hơn chút do góc nhìn) |
| 4 | Cốp mở/đóng theo lệnh cuối, hoạt cảnh ~0,9 s, bỏ chấm cốp | ✅ (mép răng cưa ⇒ `CAR3D-LID-EDGE`) | `Car3dLid` cắt nắp Seal (3 705 tam giác) + bản lề; `tap trunk → trunk BẬT` → `ctl #1 id=trunk voiceCtlBackDoor [1]`; 3 ảnh đóng/giữa/mở; dump `trunk@…:part` (không chấm) |
| 5 | Cửa mở/đóng êm ~0,9 s, không giật, không chấm cửa | ✅ | khung giữa hoạt cảnh: cửa ở góc trung gian, vỏ/kính/ốp/gương đi cùng; `đợt vẽ 19 khung`; dump `door-*:part` |
| 6 | Kính trượt ~560 ms; chạm kính 50→100→0; không chấm kính | ✅ | `tap win-lf mức=0 → win_half_lf [1,4]` · `mức=50 → win_lf [1,1]` · `mức=100 → win_lf TẮT [1,2]` |
| 7 | Chân widget: 3 nút icon mở hết / hé hết / đóng hết | ✅ | `windows_all [1,1,1,1]` · `win_half_all [4,4,4,4]` · `windows_close_all [2,2,2,2]`; nút ở giữa đáy, khung 3D chừa chân (không đè bánh) |
| 8 | Đồ tuỳ chọn chỉ hiện khi xe báo; số giả không bịa rèm | ✅ | `DummyCarReceiver` rèm chỉ khi `--ez sunshade true` (log `sunshade=false`); uiautomator: không còn "Rèm". Lưới Seal không có bộ phận rèm/cửa sổ trời ⇒ 3D không có gì để ẩn/hiện |
| 9 | Màu = đúng các dòng CSV đời xe, mọi màu áp được, theo hồ sơ, ô màu từ CSV, tên trợ năng | ✅ | điểm ảnh cửa: white (123,129,141) · grey (47,65,91) · black (2,2,2) · red (89,0,0) · smartblue (0,19,107). Vì sao xám "không áp" trước: dùng `/100` như tuyến tính (đã chữa trước lượt này, ĐO lại). `grey` Seal = (46,52,62) xám ánh xanh theo dữ liệu AVM |
| 10 | Bánh đúng hốc | ✅ | pivot `wheel.mesh` kind 0 (10 f32/đỉnh); 36/36 tệp 4/4 bánh trong hốc (`Car3dSanity`); ảnh ¾ trước, ¾ sau, cửa mở: bánh trong hốc |
| 11 | Xoay yaw + pitch, chọn bộ phận đúng mọi góc, cử chỉ thuộc khung 3D | ✅ | `Car3dPickTest` lưới yaw×pitch; `requestDisallowInterceptTouchEvent`; vuốt chéo trên máy ảo xoay 2 trục |
| 12 | Đèn theo LampLive/CarStatus, xi-nhan nháy, chạm đèn bật/tắt (headl, drl, readl), đèn chỉ-đọc ⇒ không lệnh + lắc | ✅ | `blink [light_left_turn]` 1 khung/mép pha; `tap headl → headl BẬT` (`ctl #4`), `tap drl → setDayTimeLightState [1]`; `tap turn-left → chỉ xem` (không `ctl`, lắc khung); xi-nhan trước hổ phách nay không bị phần DRL trắng đè (`Car3dPartsTest`). `readl` không có hình đèn ⇒ giữ chấm |
| 13 | Ảnh nền theo hồ sơ từ thư mục hình nền, chế độ căn kéo/chụm, lưu; bóng đất; giải mã một lần; rảnh 0 khung; test không đóng gói ảnh | ✅ (chụm chưa thử tay ⇒ `CAR3D-PINCH-VERIFY`) | Cài đặt › Hiển thị › "Nền xe 3D" + "Căn ảnh nền" → kéo trên widget → Xong ⇒ prefs `bg=seal-08.jpg@1.000,0.190,0.192`, không xoay xe; log `nền seal-08.jpg 1566x880 · 252 ms` một lần; `bóng đất …`; `Car3dBackdropContractTest`, `Car3dBackdropTest` |
| 14 | Kiểm hợp lý lúc nạp ⇒ 2D; 36 lưới AVM qua bộ đọc `:core` (biến môi trường, không byte BYD) + bảng | ✅ | `Car3dSanityTest` với `KACHI_AVM_ROOT`: 35 OK, `sc2e/bev` trượt (không cửa) ⇒ 2D; bảng §8 diagnostics |
| 15 | Hiệu năng | ✅ | rảnh 30 s: 0 khung, CPU 0,0 %; xoay 32 khung/đợt, luồng chính 227–261 µs/khung; nạp đệm 98–159 ms + gltfio 82–157 ms; GLB đệm 34,5 → 29,9 MB; PSS 217–228 MB |

## Files (lượt này)
- `:core` MỚI: `Car3dDupes.kt`, `Car3dLid.kt`, `Car3dLamps.kt`, `Car3dBackdrop.kt` (+ `Car3dBackdropChoice`); SỬA: `Car3dGlb.kt` (`lid`, `extras.grp`,
  `emissiveTexture`), `Car3dBinding.kt` (vật liệu đèn theo vai, `whiteLamps`, đèn chỉ-đọc `turn-left/right`, `tail`, `Car3dBox.group`, ẩn DRL khi xi-nhan
  Seal, cốp `shownBy = trunk*`), `ColorChoice.kt` (`carBackdrop`, trường `bg=`).
- `:app` MỚI: `Car3dBackdropView.kt`; SỬA: `Car3dModelSource.kt` (bỏ gộp → kiểm → nắp → vai đèn; `CACHE_VERSION` v7), `Car3dRenderer.kt` (khớp nhóm,
  `PBRNeutral`), `Car3dWidgetView.kt` (nút chân, khung chừa chân, nắp không bản mở giữ quay, nền + bóng + chế độ căn, log µs/khung), `SettingsCarPaintRow.kt`
  (hàng nền), `SettingsSections.kt`, `ThemeHost.kt`, `KachiHomeRender.kt` (đường ghi `Car3dBackdropChoice.save`), `strings_kachi_car3d301.xml` ×5.
- vehicleTest: `DummyCarReceiver.kt` (`--ez sunshade`).
- Test: MỚI `Car3dPartsTest`, `Car3dBackdropTest`, `Car3dBackdropContractTest`; SỬA `BydAvmMeshTest` (vai đèn), `Car3dSanityTest` (cột cốp/bánh/gộp),
  `KachiChromeContractTest` (khớp bản sửa "ghi từ state sống" của lượt trước ở `SettingsSections.color`).
- Red-proof: bỏ luật "≥ 2 vật liệu" của `Car3dDupes` hoặc vai đỏ của `Car3dLamps` ⇒ `Car3dPartsTest` 2/6 đỏ; trả lại ⇒ xanh.

## Test
- Bộ đầy đủ `:app:testDebugUnitTest :app:testVehicleTestUnitTest :core:test :car-integration:test :vehicle-contracts:test :offcar-planner:test
  --rerun-tasks`: **10 312 test · 0 hỏng · 3 bỏ qua** (core 5 311 · app debug 2 394 · app vehicleTest 2 406 · car-integration 80 · vehicle-contracts 22 ·
  offcar-planner 99; đếm từ XML).
- Lượt đầu bộ đầy đủ đỏ 1 ca `LauncherLocaleContractTest` (×2 biến thể): `AppCtxResScan` coi mọi `build(app, …)` là "đọc chuỗi" vì hàm
  `SettingsCarPaintRow.build(context: Context…)` (lượt trước) có `getString` ⇒ trùng tên với `VoiceVad.build(app…)`… Đổi tên ⇒ `paintRow`.

## Máy ảo emulator-5556 (để nguyên cho owner)
HOME · hồ sơ QA2 · ô lớn `widget:w_car3d`, ô 2 `widget:g_windows` (Kính) · Tiếng Việt · Tối · số giả BẬT (speed 0, P, cửa lf,rf,lr,rr mở) · màu `grey` ·
KHÔNG ảnh nền (owner tự chọn ở Cài đặt › Hiển thị & đơn vị › Nền xe 3D; ảnh mẫu `seal-08.jpg` có sẵn trong thư mục hình nền). `/data` còn ~483 MB.
APK vehicleTest 3.1 (205) — CHƯA bump (§9 orchestrator quyết).

## Rủi ro / còn mở
- 🚗 chưa chạy trên GPU/RAM thật (`CAR3D-ONCAR-VERIFY`); vai đèn theo vị trí là [SUY] cho đời chưa giải mã (đỏ đuôi/hổ phách xi-nhan an toàn hơn trắng).
- Nắp cốp cắt theo tam giác: mép dưới răng cưa, khoang cốp đen (`CAR3D-LID-EDGE`); đời có `trunk_*` (20 tệp) dùng bản lề tổng hợp — chưa nhìn bằng mắt.
- Chụm phóng ảnh nền chưa thử tay (`CAR3D-PINCH-VERIFY`).
