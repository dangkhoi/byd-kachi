# kachi-301 · stage 7 · TURN → xi-nhan bảng Đèn theo xe THẬT (socket 19322) — off-car, chưa commit

Owner 13/10: *"làm luôn"* (mở khoá mục 2 của `kachi-301-stage-7-POLISH-done.md`).

## 1. Quyết định nguồn — MỘT nguồn, không socket thứ hai
- `:core` MỚI `TurnSignalLive.kt` — hộp `StateFlow<Lamps?>` (`shared` cho tiến trình chính). `null` = socket KHÔNG nối ⇒ giữ số
  đọc getter. `Lamps(l,r)` = socket đang nối ⇒ **giá trị quyết định**. `overlay(s, t)` đè đúng 2 trường
  `lights.leftTurn/rightTurn`; `null` hoặc trùng ⇒ trả CÙNG đối tượng (không thêm lượt vẽ).
- Người ghi DUY NHẤT ở main: `HalSignalClient` (client socket sẵn có của camera xi-nhan) — nối được ⇒ `publish(false,false)`
  (helper gửi ảnh chụp ngay sau) · mỗi lần ĐỔI ⇒ `publish` · đứt dây (luồng hiện hành) ⇒ `clear()` · `stop()` ⇒ `clear()`.
  Kèm: `thread` thành `@Volatile` và được gán TRƯỚC `start()` (luồng cũ sau stop→start nhanh không xoá đè luồng mới).
- Người thu DUY NHẤT: `collectHome` (`KachiHomeWiring.kt`) — `.combine(TurnSignalLive.shared.state, TurnSignalLive::overlay)`
  ngay trên dòng trạng thái xe sẵn có, bên trong `repeatOnLifecycle(STARTED)`. Bảng Đèn (`GroupBoard` → `CarIconBoardView`),
  ô telemetry `light_left/right_turn` và điểm đèn của ô 3D (`Car3dBinding` đọc cùng `CarStatus` của HOME) cùng nhận —
  **không sửa tệp 3D nào**.
- Nháy: giữ nguyên nhịp tại chỗ ~1,5 Hz của POLISH (`LampTone.litAt`), chạy theo trạng thái BẬT; OFF ⇒ tông NEUTRAL ⇒
  `blinkKind` xoá mốc ngay lượt vẽ kế.

## 2. Quyết định vòng đời — KHÔNG khởi helper cho bảng
- [ĐO đọc mã] helper chỉ chạy khi công tắc **camera xi-nhan** BẬT: `KachiHomeRender:73` → `cameraSignal.tick()` →
  `if (Prefs.cameraSignalEnabled) ensureSignal() else release()` (`CameraSignalController.kt:75-76`). `ensureSignal` =
  `HalHelperLauncher.ensure` (dò cổng; lệch bản ⇒ **đẩy jar base64 qua shell uid-2000** + `nohup app_process` sống dai trên xe)
  rồi `signal.start`.
- ⇒ Khởi helper có tác dụng phụ (shell uid 2000, tiến trình ngoài sống quá vòng đời app, dùng chung worker `ShellTransport`
  với đặt-cửa-sổ/cast). Theo luật task: **không khởi helper vì bảng**. Bảng thấy xi-nhan thật CHỈ khi camera xi-nhan đang BẬT;
  tắt ⇒ lùi getter (trên trim đã đo luôn TẮT). Ghi ở KDoc `TurnSignalLive`, `HalSignalClient`, `CarIconBoardView`, `LampTone`.
- Phương án đã cân và BỎ: (a) đếm-tham-chiếu `start/stop` client khi bảng hiện mà KHÔNG `ensure` — chỉ có ích khi helper
  tình cờ còn sống, mà khi không có helper client lùi 1→8→60 s (luồng + ≥ 1 lần nối/60 s) suốt lúc bảng hiện ⇒ tốn mà gần như
  không được gì; (b) `ensure` vì bảng — tác dụng phụ shell như trên.

## 3. Chi phí hiệu năng [ĐO máy ảo emulator-5556, APK đã cài = APK build này, md5 9e0d9bba…]
- Camera xi-nhan TẮT (mặc định): luồng tiến trình **40 → 40** trước/sau bắn xi-nhan giả; không luồng `KachiHalSignal`/
  `kachi-camera-hal`. Thêm: 0 luồng · 0 nhịp định kỳ · 0 lệnh shell · 0 listener hệ thống · 1 `StateFlow` (một ô nhớ).
- Camera xi-nhan BẬT (xe): +1 lần phát `StateFlow` mỗi lần đổi bên (≈ 2 lần mỗi lần gạt cần) ⇒ 1 lượt `setCarStatus`/vẽ.
  Không đổi gì của đường camera.

## 4. Đường số giả (vehicleTest)
- `DummyCarReceiver`: `--ez turn_l/turn_r` nay **phát vào `TurnSignalLive.shared`** (đúng dây thật), `fill` thôi đặt
  `leftTurn/rightTurn`; `--ez on false` ⇒ `clear()`. [ĐO] broadcast `turn_l true` ⇒ log `DummyCar … turn_l=true`, không sập
  (pid giữ nguyên 5496, HOME `mResumedActivity` = KachiHome).
- [ĐO máy ảo, 3 ảnh chụp đọc qua sub-agent, scratchpad `turn-{on,on2,off}.png`] bảng "Đèn" có trên màn: `turn_l true` ⇒ chấm
  HỔ PHÁCH ở gương trái (~549,351 trên ảnh 1600×900) ở ảnh 1, VẮNG ở ảnh 2 chụp sau ~350 ms (pha tối của nhịp nháy), VẮNG ở ảnh
  `turn_l` tắt. Phần còn lại của ba ảnh giống nhau.
- ⚠ vehicleTest-only: bắn số giả trong lúc socket thật đang chạy sẽ đè/xoá trạng thái socket tới lần đổi kế.

## Test [ĐO XML, gradle-locked.sh]
- `:core` (`*TurnSignal* *LampTone* *CarIconBoard* *GroupBoard*`): **49 / 0**. MỚI `TurnSignalLiveTest` (4): không socket ⇒
  cùng đối tượng · socket đè getter-0 · chuỗi ON/OFF/phải/khẩn ⇒ tông bảng Đèn từng bước, `clear` ⇒ lùi · trùng ⇒ không copy.
- `:app` (`*Camera* *TurnSignal* *HalSignal* *LightCarBoard* *LampIndicator* *CarIconBoard* *LayeringRules* *LauncherI18n*
  *DummyCarTap*`): **174 / 1 đỏ**. Đỏ duy nhất = `LauncherI18nContractTest` vì `Car3dTaps.kt: "chấm (px khung ${w}x$h)"` — tệp
  của agent 3D, không phải lượt này. MỚI `HalSignalClientTurnLiveTest` (2, socket THẬT: nối ⇒ (F,F) · ON/OFF/phải · helper đứt ⇒
  null · stop ⇒ null) · `TurnSignalLiveContractTest` (4: một người ghi/một client 19322 · `ensure` chỉ từ controller sau cổng
  pref · chỉ thu trong `repeatOnLifecycle(STARTED)` · số giả qua hộp). Sửa có trích owner: `LampIndicatorContractTest` (số giả
  qua `TurnSignalLive.shared.publish`).
- Thử ĐỎ [ĐO]: `overlay` luôn trả `s` ⇒ 2 bài core đỏ · bỏ `live.clear()` trong `finally` ⇒ `noi duoc - ON OFF - helper dut -
  stop` đỏ · bỏ `.combine(TurnSignalLive…)` ⇒ `hop chi duoc thu khi HOME STARTED` đỏ; khôi phục ⇒ xanh.
- `:app:assembleVehicleTest` xanh; dex có `TurnSignalLive`. Call site §8: `TurnSignalLive.shared.state` 1 (wiring) ·
  `live.publish(` 2 + `live.clear()` 2 (client) · `TurnSignalLive.shared.publish(`/`clear()` 1+1 (dummy).

## Còn mở
- 🚗 [CHƯA BIẾT trên xe] bảng Đèn sáng/tắt theo cần xi-nhan khi camera xi-nhan BẬT — cơ chế socket đã [ĐO xe 09-26] cho camera,
  dây tới bảng mới [ĐO off-car]. Lệnh chốt: bật camera xi-nhan, gạt trái, nhìn bảng Đèn + `logcat -s KachiHalSignal`.
- 🚗 [CHƯA BIẾT] độ trễ tắt trên bảng = độ trễ OFF của helper (~4,2 s [ĐO 09-26]) — bảng sẽ nháy thêm ~4 s sau khi trả cần.
- Quyết định owner: có muốn bảng thấy xi-nhan khi camera xi-nhan TẮT không (cần khởi helper uid-shell — xem §2).
- Nợ có từ trước (ngoài phạm vi): `HalSignalClient.stop()` rồi `start()` thật nhanh có thể để luồng cũ sống thêm một vòng (luồng
  cũ đọc lại `running=true`). Cổng `currentThread() === thread` của lượt này chỉ chặn nó xoá hộp.
- Spec `kachi-301` §Tasks/§Design + backlog chưa ghi TURN (một chủ spec — orchestrator).
