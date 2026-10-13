# kachi-301 · stage 8 — POLISH2 trước bản 3.1.1 (máy ảo; 🚗 chưa đo xe; CHƯA commit, CHƯA bump version)

Owner 13/10 (emulator-5556) + orchestrator chuyển thêm 5 lượt phản hồi trong lúc làm. Trước: `kachi-301-stage-7-3D-final-done.md`.
Chẩn đoán + bảng lỗi/gốc/chữa: `docs/diagnostics/widget-3d-301-2026-10-13.md` §10. Ảnh cắt chỉ ở scratchpad (không vào repo), đọc qua
sub-agent (G12).

## Checklist

| # | Việc | Trạng thái | Bằng chứng |
|---|---|---|---|
| 1a | Xe giả NHẠI lệnh ghi (đèn pha/ban ngày, từng kính + cả cụm mở/hé/đóng) — chỉ vehicleTest | ✅ | `CtlWriteTap` (`:core`) gọi ở `ControlWriteFlow.write` sau lượt gửi (không cho lượt nhả, không cho lệnh bị cổng có-mặt từ chối); `DummyCarReceiver.echo` (kính theo `verbLevel`, hậu tố vị trí). [ĐO logcat] `nhại windows_all [1] ⇒ {100×4}` · `nhại win_half_all [1] ⇒ {50×4}` · `nhại drl [0] ⇒ lights=[low]`. Cốp: xe không có trường đọc cốp ⇒ không nhại (3D vẫn theo lệnh cuối). Test `CtlWriteTapTest` 3 · `DummyCarTapContractTest` +1 (chỉ vehicleTest gán) |
| 1b | Đèn lạc quan khi chạm (bảng Đèn 2D + 3D) tới khi số đọc xác nhận / ~3 s | ✅ | `LampHold` (`:core`, một bộ chung); 2D `CarIconBoardView.kind/tap` (thay ân hạn 2,5 s theo thời gian); 3D `Car3dTaps.dot/active` (biến thể đèn theo ô đọc CỦA NÚT). Gốc phụ [ĐO]: điểm 3D `drl` không có ô đọc ⇒ chạm đầu gửi BẬT cho đèn đang sáng — nay `headl`/`drl` đọc `light_high_beam`/`light_drl`; [ĐO] `tap drl → drl TẮT`. Số giả: đèn vị trí mặc định TẮT (đèn 7/8 Seal sáng theo DRL HOẶC vị trí). Test `LampHoldTest` 2, `LightCarBoardContractTest` cập nhật |
| 2 | Ba icon kính chân widget vẽ mảnh | ✅ | grammar `"weight": "fine"` ⇒ nét chính 1.25 (nét phụ 1.2), kính tô gọn, mũi nhỏ; sinh lại bằng `gen-icons.py` (`--check` 132 tệp khớp byte); `IconStyleContractTest` chỉ nhận 1.25 cho icon khai fine; tương phản tông `chrome` 16,96:1 (`icon-audit.py`) |
| 3 | Cốp: nắp THẬT theo khe tấm, cả nắp quay, bản lề mép trước, đích chạm theo tư thế, lòng cốp | ✅ máy ảo · 🚗 | `Car3dPanels` + `Car3dLid.split` (Seal 5 278 tam giác: mặt trên + mặt sau + đoạn giữa dải đèn + logo + viền; bản lề (−1,83; 1,14)); `Car3dRenderer.box(posed)` + `Car3dTaps.retarget` mỗi bước hoạt cảnh; `Car3dPick` ưu tiên đích có việc ([ĐO] trước: `tap tail → chỉ xem` khi chạm giữa nắp). Lòng cốp: khoang `lid_cavity` + ẩn tam giác thân có trọng tâm trong lòng khoang (đo bao kín); owner chốt *"một màu, đen luôn sâu vào"* ⇒ đen sâu một màu. QA sub-agent: nắp nâng một khối ở góc sau ¾ / sau / hông; đóng: không lòi gì. 36 lưới AVM (`Car3dSanityTest` + `KACHI_AVM_ROOT`): 35 OK như trước, 20 tệp `trunk_*` vẫn nắp theo tên + nay có khoang |
| 4 | Một widget 3D một lúc + cổng `minSlotDp` lúc dựng | ✅ | `Car3dSeat` (claim trước `Car3dRenderer.create`, release ở `stop`/`fallBack`); cỡ ô THẬT ở `onSizeChanged` (`Car3dDevice.slotFits`). Test `Car3dSeatTest` 3 · `Car3dLifecycleContractTest` +1 |
| C1 | Hàng nút chân canh giữa (3D + Kính 2D) | ✅ | `CenteredRow`; [ĐO uiautomator] 3D: cụm 622–982 tâm 802 = widget 19–1585 tâm 802; Kính 2D (ô 2): cụm 1629–1869 tâm 1749 = ô 1616–1883 tâm 1749,5. Cỡ thứ hai (bố cục cuối): 3D 19–1155 / cụm 407–767 (tâm 587 = 587); Kính 1186–1883 / cụm 1414–1654 (1534 vs 1534,5); 3D toàn ngang 19–1901 / cụm 780–1140 (960 = 960). Bài đơn vị 5 bề rộng × 1–4 nút |
| C2 | Viền khung kính/trụ B/mép nóc bạc ⇒ đen; chấm đèn đọc trên nóc ⇒ bỏ, hắt sáng nội thất | ✅ | `metal` đen mờ; `readl` chạm vùng kính cabin (`shownBy = body_glass`, không chấm), bật ⇒ `emissiveFactor` nội thất (khai sẵn 0 trong GLB). [ĐO] `tap readl → readl BẬT`; QA: không còn chấm/icon nào trên xe |
| C3 | Kính cửa mở lòi khỏi cửa | ✅ (không tái lập) | QA 4 cửa mở × kính 100 %/50 % × 4 góc: không kính/khung nào lòi dưới dây đai hay ra ngoài vỏ cửa (đã có `M·T(dy)` — kính trượt trong khung cửa rồi mới quay) |
| C4 | Mâm: nửa dưới tối, nan đen | ✅ phần | [ĐO] ảnh mâm áp đúng (UV lật: nửa trên 61 · nửa dưới 63); `wheel`/`metal_black` điện môi + đèn dội mặt đất. Nan bạc/trắng [QA]. Mép vành dưới vẫn tối hơn mép trên (che nắng — ánh sáng, không phải ảnh) |
| C5 | Nhìn từ trên xe bị cắt ở mép dưới | ✅ | `Car3dMath.fitDistance` (MAX với khoảng cách cũ); `Car3dMathTest` lưới yaw×pitch; QA: không còn đường cắt |

## Files (lượt này)
- `:core` MỚI: `Car3dPanels.kt`, `Car3dSeat.kt`, `CenteredRow.kt`, `CtlWriteTap.kt`, `LampHold.kt`. SỬA: `Car3dLid.kt` (viết lại tách nắp + khoang),
  `Car3dBinding.kt` (`glow`, ô đọc đèn, vật liệu metal/wheel/cavity/interior), `Car3dPick.kt` (ưu tiên đích có việc), `Car3dMath.kt`
  (`fitDistance`), `ControlWriteFlow.kt` (một dòng gọi tap).
- `:app` MỚI: `Car3dDevice.kt` (tách khỏi widget — trần 500 dòng). SỬA: `Car3dWidgetView.kt` (499 dòng), `Car3dTaps.kt`, `Car3dRenderer.kt`
  (hộp theo tư thế + cache hộp `extras`, `glow`, đèn dội), `Car3dModelSource.kt` (`CACHE_VERSION` v15), `CarIconBoardView.kt`, `ActionMatrixCompact.kt`.
- vehicleTest: `DummyCarReceiver.kt` (nhại lệnh, đèn vị trí mặc định tắt). Icon: `design/glyph/win_{open,half,close}_all_g.svg`,
  `design/icon-grammar.json`, `scripts/design/gen-icons.py`, 3 drawable sinh lại.
- Test MỚI: `Car3dSeatTest`, `CtlWriteTapTest`, `LampHoldTest`; SỬA `Car3dPartsTest`, `Car3dPickTest`, `Car3dMathTest`, `Car3dLifecycleContractTest`,
  `DummyCarTapContractTest`, `IconStyleContractTest`, `LightCarBoardContractTest`.
- Docs: diagnostics §10, `docs/README.md` (dòng chỉ mục), `PROJECT-BACKLOG.md` (`CAR3D-LID-EDGE` → DONE).

## Test
- Bộ đầy đủ `:app:testDebugUnitTest :app:testVehicleTestUnitTest :core:test :car-integration:test :vehicle-contracts:test :offcar-planner:test
  --rerun-tasks` (sau thay đổi cuối): **10 346 test · 0 hỏng · 3 bỏ qua** (core 5 325 · app debug 2 404 · app vehicleTest 2 416 · car-integration 80 ·
  vehicle-contracts 22 · offcar-planner 99; đếm từ XML).
- Lượt đầu bộ đầy đủ đỏ 2 ca (đã chữa): `LightCarBoardContractTest` (khớp chuỗi cũ của `tap` — cập nhật + thêm khẳng định `LampHold`),
  `SpacingScaleContractTest` (hàm px→dp một dòng `slotFits` bị coi là hàm đổi dp lạ — chuyển sang `Car3dDevice.slotFits` thân khối).
- `Car3dSanityTest` + `KACHI_AVM_ROOT` (36 lưới): 35 OK (Seal/eke_right tách 5 278 tam giác; 23 `trunk_*`; 8 cặp; 2 không cốp), `sc2e/bev` FAIL như cũ ⇒ 2D.
- Red-proof: [SUY — CHƯA chạy hoàn tác] các khẳng định mới viết để đỏ với bản cũ: quấn khoang (thứ tự cũ cho pháp tuyến −Y ở sàn — tính tay),
  12 chỉ số sơn nắp (bản cắt ngang cũ chỉ lấy mặt trên), ca cốp/đèn đuôi của `Car3dPickTest` (luật "nhỏ nhất thắng" cũ ra `tail`). /data máy ảo còn ~644 MB.

## Máy ảo emulator-5556 (để nguyên cho owner)
Khởi động lại (dữ liệu /data còn ~570 MB) · HOME · hồ sơ QA2 · bố cục THREE: ô 0 `w_car3d` (lớn), ô 1 `g_tyres`, ô 2 `g_windows` (Kính) · Tiếng Việt ·
Tối · số giả BẬT (speed 0, P, cửa đóng hết, đèn cốt + DRL, vị trí tắt) · cốp đóng · đèn đọc tắt. Giữa lượt bố cục từng bị đổi sang ONE (không phải
do lượt này) — đã trả THREE. APK vehicleTest 3.1 (205) — CHƯA bump.

## QA còn ghi nhận (sub-agent, chưa chữa)
- Lòng cốp nhìn từ sau-¾-phải: còn mảng ~(11,10,11)/(20,20,19) cạnh đen (2,1,2) — gần mục tiêu "đen sâu một màu", chưa tuyệt đối đều.
- Một đốm trắng sáng sau kính hậu ở góc sau-¾-phải khi cốp mở (nghi phản xạ nắng trên kính / đèn phanh cao `body_lamp_16` lộ ra) — [CHƯA BIẾT].
- Mép vành dưới tối hơn mép trên (~65 vs ~172) dù đã thêm đèn dội; nan mâm bạc đúng.

## Rủi ro / còn mở
- 🚗 chưa chạy trên GPU/RAM xe (`CAR3D-ONCAR-VERIFY`). Khoang + ẩn kết cấu trong chỉ nhìn bằng mắt trên Seal; 20 đời `trunk_*` chỉ kiểm bằng số.
- Máy ảo dùng chung: trong lượt có thao tác của người khác trên cùng emulator (chạm cốp/xoay ngoài kịch bản) — các ảnh QA chụp lại sau khi xác
  nhận trạng thái qua logcat.
- Mép vành dưới tối hơn mép trên (ánh sáng một nắng); khử răng cưa tắt ở hạng LOW ⇒ sọc trắng răng cưa ở hốc gió trước (có từ trước, không đổi).
