# kachi-301 · stage 7 · LAMPLIVE — đèn THẬT cho bảng Đèn + xe 3D không cần camera xi-nhan (off-car, chưa commit)

Owner 13/10: *"nối luôn đi, đâu cần camera xi nhan bật mới sáng, tự nối lấy tín hiệu được mà"* — đảo quyết định §2 của
`kachi-301-stage-7-TURN-done.md` (trước: bảng KHÔNG được khởi helper). Owner chấp nhận khởi helper uid-shell khi cần.

## 1. Thiết kế — MỘT chủ vòng đời, đếm nhu cầu theo nguồn
- `:app` MỚI `launcher/camera/HalSignalHub.kt` — chủ DUY NHẤT của helper + client 19322 (một mỗi tiến trình, `AppContainer.halSignal`).
  Giữ ĐÚNG MỘT `HalSignalClient`; là chỗ DUY NHẤT gọi `HalHelperLauncher.ensure` (nối ở factory `AppContainer.build`). Mọi việc chặn
  chạy trên MỘT luồng daemon `kachi-hal-hub` (thay luồng `kachi-camera-hal` cũ của controller; rỗi 10 s ⇒ tự thu). `want()` không chặn.
- `:core` MỚI `HalSignalDemand.kt`:
  - `HalSignalDemand` — tập nguồn `{CAMERA, LAMP_WIDGET}`; rỗng→khác rỗng ⇒ `acquire` MỘT lần (hai nguồn chồng = một lần); khác
    rỗng→rỗng ⇒ hẹn `release` sau **60 s** ân hạn; quay lại trong hạn ⇒ huỷ hẹn, không khởi lại; lượt thôi mới ⇒ hạn tính lại;
    hẹn cũ lỡ chạy sau khi huỷ bị chặn bằng số thế hệ. Gọi lặp cùng nguồn = no-op (đếm theo TẬP).
  - `LampSignalDemand` — `wants(demand)` = tập nhu cầu H1 của màn chính (`CarDataDemand.of`) có chứa datum đèn có sự kiện
    (`light_left/right_turn`, `light_low/high_beam`, `light_front/rear_fog`, `light_side`, `light_drl`). `null` ⇒ KHÔNG (không tác
    dụng phụ khi màn chưa công bố/đã dừng). `Watch` gộp "màn bày đèn" × "màn xe sáng", chỉ gọi hub khi kết quả ĐỔI.
- Nguồn `CAMERA`: `CameraSignalController.ensureSignal/release` (vẫn sau cổng `Prefs.cameraSignalEnabled`) chỉ còn `hub.want(CAMERA, …)`;
  `cam_sort` ghi qua `hub.background`. Camera gán `hub.onTurn`; setter phát lại NGAY trạng thái đang biết nếu có bên đang bật (client đã
  chạy vì bảng thì không có ảnh chụp mới cho camera).
- Nguồn `LAMP_WIDGET`: `AppContainer` — `CarDataDemand.Holder { lampWatch.demand(it) }` (Holder thêm móc `onScreen`, gọi ở `set` mỗi lượt
  state + `clear` lúc HOME `onStop`) và `awake = { screenAwake().also { lampWatch.screen(it) } }` — CÙNG lượt hỏi màn của vòng poll
  (không thêm binder/receiver; vòng poll chỉ chạy khi HOME STARTED). `KachiHomeWiring` không thêm dòng nào (chỉ đổi tên hộp).
- Helper chết (ROM giết `app_process`): client MỚI có móc `onLinkLost` (link ĐÃ nối rồi đứt, đang chạy) ⇒ hub `ensure` lại (≤ 1 lần/30 s).
  Trước lượt này helper chết giữa chuyến thì không ai khởi lại. Tiến trình APP chết ⇒ nhu cầu RAM mất theo; helper `nohup` sống tiếp
  như trước; tiến trình mới `ensure` thấy cổng mở ⇒ dùng lại.
- Hàng đợi shell: `:core` `MutationPriority` MỚI mức `BACKGROUND(3)` (rút SAU mọi `NORMAL`; `isHighPriority` = `drainRank < NORMAL`).
  `HalHelperLauncher` chạy MỌI lệnh mức này (`ShellTransport.bg`) ⇒ khởi helper (nay có thể do bảng Đèn) không bao giờ chen trước lệnh
  đặt-cửa-sổ/cast đã xếp. Lệnh đang chạy không bị cắt; mỗi lệnh helper đều ngắn (`wc`/`echo`/`base64`/`pkill`/`nohup … &`).
- Không đổi: helper không bị giết khi nhả (y như camera trước đây — nó chỉ NGHE, không đổi trạng thái hệ thống; đường gỡ có sẵn
  `pkill -9 -f '[K]achiHalMain'` trong `HalHelperLauncher.killHelper`).

## 2. Đèn nào theo xe THỜI GIAN THỰC
- `:core` `TurnSignalLive` → **`LampLive`** (đổi tên, mọi tham chiếu sửa). `Lamps(left, right, lowBeam?, highBeam?, frontFog?, rearFog?,
  side?, drl?)`; `null` ở một trường = helper chưa thấy sự kiện ⇒ `overlay` GIỮ getter cho trường đó; xi-nhan luôn đè (như TURN).
- Helper (`hal-helper/KachiHalMain.java`, jar 5366 → **5792 B** — cỡ khác ⇒ `ensure` tự đẩy lại jar lần đầu): `onLightOn/Off(type)` với
  type 1/2/3/6/7 trước đây BỊ VỨT, nay chuyển tiếp NGUYÊN dạng dòng cũ (client cũ bỏ type lạ ⇒ tương thích hai chiều); MỚI
  `onDayRunningLightStateChanged(v)` ⇒ `{"topic":"light.dayRunning","type":v}` (1=BẬT · 2=TẮT, `DAY_RUNNING_LIGHT_ON/OFF`). Ảnh chụp
  cho client mới kèm mọi đèn ĐÃ thấy. Không thêm listener/device/poll. Stub thêm `onDayRunningLightStateChanged` (lớp thật không có ⇒
  chỉ là phương thức thừa, không ngã).
- Client: `applyLine` (thuần) — topic lạ / type ngoài 1..7 / DRL 0,3 ⇒ BỎ, không đoán. `onTurn` (camera) CHỈ gọi khi xi-nhan đổi.
- Mức bằng chứng:
  - xi-nhan trái/phải: cơ chế socket [ĐO xe 09-26]; nay chạy KHÔNG cần camera xi-nhan [ĐO off-car, test socket thật].
  - cốt/pha/sương mù trước-sau/hông qua `onLightOn/Off(type)`: thang type [ĐO RE] `BYDAutoLightDevice.LIGHT_*` (jadx-tmap :44-62) + app
    Kim `VehicleBridge.createLightListener` lưu `cachedLights[area]` cùng khoá, `roles` side=1·low=2·high=3·fog 6/7. Xe có phát các
    type này không: **[CHƯA BIẾT]**.
  - DRL qua `onDayRunningLightStateChanged`: [ĐO RE] có trong stub SDK; xe có phát không: **[CHƯA BIẾT]**.
  - `headlight_feedback` (chế độ đèn): không có sự kiện ⇒ vẫn getter.
  - Đèn chưa thấy sự kiện ⇒ getter 10 s như cũ (cốt/pha/sương mù/DRL đọc được [ĐO xe 09-16 CapTest ✅]).

## 3. Chi phí hiệu năng
- [ĐO máy ảo emulator-5556, helper jar MỚI chạy tay dưới uid shell — cùng uid với dadb trên xe, cổng 19323 riêng, đã dọn]:
  helper `app_process` **PSS 38,9 MB** (Rss 93,6 MB, Private_Dirty 21,3 MB, Shared_Clean 62,7 MB — phần lớn là boot image dùng chung),
  14 luồng, CPU **0 tick trong 60 s rỗi** (khởi động 90 ms). Ảnh chụp đúng 2 dòng xi-nhan khi chưa thấy đèn nào. Máy ảo không có
  `BYDAutoLightDevice` ⇒ đăng ký listener thất bại như dự kiến, socket vẫn phục vụ.
- [ĐO đọc mã + test JVM] phía app:
  - Không nguồn nào (camera TẮT, màn không bày đèn / màn tắt / HOME dừng): 0 luồng mới (hub rỗi tự thu), 0 lệnh shell, 0 nhịp.
    Mỗi lượt state: một `any` trên tập nhu cầu (≤ ~40 mã) trong khoá; mỗi lượt cổng màn của vòng poll: một lời gọi trong khoá, 0 binder thêm.
  - Có nguồn: +1 luồng `KachiHalSignal` (chặn ở `readLine`, 0 lần thức khi xe im) — y như camera BẬT trước đây. Luồng `kachi-hal-hub`
    chỉ sống khi có việc/hẹn nhả (≤ 60 s + 10 s) — trước đây `kachi-camera-hal` sống MÃI kể từ lần bật camera đầu ⇒ BỚT.
  - Lệnh shell: lần `ensure` đầu của tiến trình, helper sống + jar khớp: **1** (`wc -c`); jar lệch (lần đầu sau bản này): **8** một lần
    (`wc`, `pkill`, `rm`, 2×`echo`, `base64 -d`, `wc`, `nohup app_process`); các lần giữ sau trong cùng tiến trình: **0** (dò cổng 400 ms).
    Helper chết giữa chuyến: ≤ 2 lệnh (`wc` + `nohup`) mỗi ≥ 30 s. Tất cả mức `BACKGROUND`.
  - Mỗi lần một đèn ĐỔI trên xe: 1 dòng socket + 1 lần phát `StateFlow` + 1 lượt `setCarStatus`.
  - Không có helper (máy ảo / kênh shell chưa duyệt) mà bảng Đèn đang hiện: `ensure` hỏng nhanh (lệnh shell bị cổng chặn), client lùi
    1→8→60 s (≈ 1 lần nối/60 s sau ~39 s) suốt lúc bảng hiện — cái giá owner đã chấp nhận ("tự nối"), dừng 60 s sau khi bảng khuất.
- ⚠ Chưa đo được trên máy ảo phần app (cài APK mới thất bại `INSUFFICIENT_STORAGE` — /data 94 %, không xoá app của agent khác).

## Test [ĐO XML, gradle-locked.sh]
- `:core` (`*HalSignal* *LampLive* *LampTone* *PrioritySerial* *WindowMutation* *CarDataDemand* *GroupBoard* *CarIconBoard*
  *CarStatusRepository*`): **112 / 0**. MỚI `HalSignalDemandTest` (8: chồng nguồn · ân hạn · quay lại + hạn tính lại · gọi lặp · hẹn trễ ·
  xin lại · `Watch` · `wants` null/headlight + Holder móc) · `LampLiveTest` +2 (đèn khác null giữ getter · thang `withType`) ·
  `PrioritySerialExecutorTest` +1 (`BACKGROUND` rút sau `NORMAL`).
- `:app` (`*Camera* *HalSignal* *HalHelper* *TurnSignal* *Lamp* *LightCarBoard* *ShellTransport* *LayeringRules* *LauncherI18n*
  *SourcePathHygiene* *WindowCommandDispatcher* *PerfR6c* *AppContainer* *DummyCar*`): **202 / 1 đỏ**. Đỏ duy nhất =
  `LayeringRulesTest.so file thuan…` vì `Car3dPaintChoice.kt` (tệp của agent 3D, untracked — không phải lượt này). MỚI `HalSignalHubTest`
  (6, socket THẬT: chồng nguồn = 1 ensure + 1 kết nối, nhả sau ân hạn · quay lại không nối lại · helper chết ⇒ ensure lại + nối lại ·
  tiến trình mới nối lại · camera gán người nghe nhận ngay · không ai cần = 0) · `HalSignalClientLampTest` (2). Sửa có trích owner:
  `LampLiveContractTest` (đổi tên từ `TurnSignalLiveContractTest`; bài 2 nay khoá "một chủ: `ensure` chỉ nối vào hub, start/stop client
  chỉ ở hub, camera sau cổng pref, bảng qua Holder→Watch→hub") · `PerfR6cScreenGateWiringContractTest` (dòng `awake` mới).
- Thử ĐỎ [ĐO]: bỏ huỷ hẹn + `gen++` ⇒ `quay lai trong an han` ĐỎ · `BACKGROUND(1)` ⇒ `BACKGROUND drains…` ĐỎ · bỏ móc `heal` ⇒
  `helper chet` ĐỎ · bỏ phát lại trong setter `onTurn` ⇒ `camera gan nguoi nghe` ĐỎ · `if (changed)`→luôn ⇒ `onTurn chi khi xi-nhan doi`
  ĐỎ; khôi phục ⇒ xanh.
- `:app:assembleVehicleTest` xanh; dex có `HalSignalHub`, asset `kachi_hal.jar` 5792 B; dexdump jar: không có lớp stub, có
  `onDayRunningLightStateChanged`. Call site §8: `hub.want(` 2 · `halSignal?.want(` 1 · `hub.background` 1 · `lampWatch.demand/screen`
  1+1 · `applyLine(` 2 · `onLinkLost = lost` 1 · `MutationPriority.BACKGROUND` 3 · `HalSignalHub(ensure` 1.
- Trần 500 dòng: `CameraSignalController` 500 → 496 · `KachiHomeWiring` 499 (không đổi) · `HalSignalHub` 132.

## Còn mở
- 🚗 [CHƯA BIẾT] bảng Đèn/xe 3D theo cần xi-nhan khi camera xi-nhan TẮT. Lệnh chốt: tắt camera xi-nhan, để bảng Đèn trên HOME, gạt trái;
  `logcat -s KachiHalHub KachiHalSignal KachiHalLaunch` phải có `giữ helper + socket (nguồn=[LAMP_WIDGET])` rồi `xi-nhan trái=true`.
- 🚗 [CHƯA BIẾT] xe có phát `onLightOn/Off(2/3/6/7/1)` và `onDayRunningLightStateChanged` không. Lệnh chốt: bật/tắt đèn cốt, pha ⇒
  `logcat -s KachiHalSignal` dòng `đèn Lamps(…lowBeam=true…)`; `/data/local/tmp/kachi_hal.log` của helper có `phát light.onLightOn type=2`.
- 🚗 [CHƯA BIẾT] PSS helper trên xe (máy ảo 38,9 MB). Lệnh: `grep Pss /proc/$(pgrep -f '[K]achiHalMain')/smaps_rollup` qua ClusterDiag.
- 🚗 Lần đầu sau bản này helper CŨ (5366 B) đang chạy sẽ bị `pkill` + thay jar (đúng đường lệch-bản sẵn có) — 8 lệnh shell một lần.
- Nợ (ngoài phạm vi, chưa ledger — orchestrator): `scripts/build-hal-helper.sh` bước verify `unzip -l | grep -q` ngã với `pipefail`
  (SIGPIPE) dù jar đúng — đã kiểm tay bằng `unzip -l` + `dexdump`. `HalSignalClient.stop()→start()` nhanh vẫn có thể để luồng cũ sống
  thêm một vòng (nợ từ TURN).
- Spec `kachi-301` §Design/§Tasks, `docs/specs/camera-turn-signal-hal-socket.html`, backlog + INDEX chưa ghi LAMPLIVE (một chủ spec —
  orchestrator).
