# Kiểm toán "màn đứng hình cũ sau khi phím/giọng nói ghi" (STALE-AUDIT) — 2026-10-15

> **Backlog**: `STALE-DISPLAY-AUDIT` · **Bản**: sửa off-car trên nền 3.1.7 đang làm (chưa bump, chưa commit) · **Liên quan**:
> [`fan-step-2026-10-15.md`](fan-step-2026-10-15.md) (ô STEP — ca đầu tiên của họ lỗi này).

## 1. Câu hỏi

Owner 15/10, sau FAN-STEP: *"ngoài cái gió thì còn cái nào bị 'sót' nữa không?"*

## 2. Họ lỗi [ĐO mã]

Đường phím/giọng nói (`KeyCtlDispatch` → `VoiceControlDispatch.run` → `finish(ok)`) ghi bảng dùng chung
(`ControlTileState.shared` / `ControlLastSent.shared`) nhưng **không vẽ gì**. Một bề mặt đứng hình cũ khi một trong hai điều sau xảy ra:

- **(A) so sai mốc** — lượt đổ số so số đọc với *bảng* (người khác vừa ghi) hoặc với một cờ riêng, thay vì với cái **đang vẽ**;
- **(B) không ai gọi vẽ** — bề mặt chỉ vẽ lại khi `CarStatus` ĐỔI. Nút không có số đọc (đèn đọc, rèm…) thì bảng đổi mà `CarStatus`
  không đổi ⇒ chờ tới khi một số liệu khác đổi — [CHƯA BIẾT] bao lâu khi xe đứng yên (giới hạn này đã ghi sẵn ở KDoc `TileResync`).

Thêm một chênh lệch với cú chạm: cú chạm ô cốp / chấm đèn-cốp trên xe 3D / bảng Đèn đặt **đích giữ** `LampHold` (hiện đích tới khi xe
xác nhận); đường phím/giọng nói **không** ⇒ *"đóng cốp"* bằng phím xong, màn vẫn hiện MỞ ~5,8 s (cốp chỉ đọc 0 khi chốt khoá — [ĐO
logcat xe 14/10], ghi ở `DummyCarReceiver.TRUNK_CLOSE_MS`), còn cùng lệnh bằng ngón tay hiện ĐÓNG ngay.

## 3. Bảng kiểm toán

Mức bằng chứng của cột "Kẹt được?": **[ĐO mã]** — đọc nguồn trên nhánh `feat/key-source-split` hôm nay; chưa có phép đo trên xe cho
từng ô (xem §6).

| Bề mặt | Kiểu | Kẹt được? | Sửa | Bài |
|---|---|---|---|---|
| Ô STEP (nhiệt · gió · âm lượng) — thanh nút, ô giữa màn, ô ẩn hàng chân 3D | STEP | (A) có — đã sửa sáng nay (`StepTileSync`) | FAN-STEP | `StepTileSyncTest` · `StepTileSyncWiringContractTest` |
| Ô TOGGLE (lấy gió, AC, sấy, kính `win_*`, đèn…) — `ControlTileFactory.tileToggle` | TOGGLE | (A) không — so với hình (`TileResync.stale`, 2.87). (B) có với nút không số đọc. Phím không có đích giữ ⇒ nút có số đọc hiện số cũ tới khi xe xác nhận | F1 · F2 (refresh hỏi `LampHold.shown` trước số đọc) | `ControlWriteEchoTest` · `StaleDisplayWiringContractTest` |
| Ô SELECT (sưởi/mát ghế, chế độ) — `tileSelect` | SELECT | (A) không (`v != sel ‖ TileResync.stale`). (B) có | F1 | `StaleDisplayWiringContractTest` |
| Ô COVER (cốp, rèm che nắng) — `tileCover` | COVER | **(A) có**: lượt đọc so với cờ riêng `open`, mà cú chạm (`show`) không cập nhật cờ đó ⇒ chạm MỞ, xe không mở, hết hạn giữ không có lượt đổ số nào xen giữa ([SUY] hiếm khi xe chạy, dễ khi số liệu đứng) ⇒ số đọc 0 = cờ 0 ⇒ ô **kẹt MỞ**. Phím: không đích giữ (§2) | F4 (so chỉ số với hình đã vẽ qua `TileResync`, bỏ cờ `open`) · F2 · F1 | `ControlWriteEchoTest` · `StaleDisplayWiringContractTest` · `ControlStateUxContractTest` (đổi luật) |
| Ô BUTTON (`pm25_clean_now`…) | BUTTON | không — không có trạng thái bền | — | — |
| Xe 3D: chấm (`Car3dTaps.fill` / `dot`) | đọc + bảng + giữ | (A) không — tính lại từ đầu mỗi lượt. (B) có với vật không cảm biến (theo bảng). Chấm cốp/đèn: phím không đích giữ | F1 · F2 | `ControlWriteEchoTest` |
| Xe 3D: biến thể đèn (`Car3dTaps.active`) | đọc + giữ | như trên — chỉ thiếu đích giữ khi ghi bằng phím | F2 | `ControlWriteEchoTest` |
| Xe 3D: icon ghế (`Car3dHeroLayer`, `drawn[id]` vs `level(id)`) | SELECT qua ô ẩn | (A) không — `drawn` là mức đã vẽ, ô ẩn đồng bộ bảng với số đọc trước đó (`fill`). (B) có | F1 | `StaleDisplayWiringContractTest` (đường vẽ lại) |
| Xe 3D: hàng chân (`Car3dFootRow`) — số nhiệt/gió | soi chữ ô STEP ẩn | thừa hưởng ô STEP (đã sửa) | FAN-STEP | `StepTileSync*` |
| Xe 3D: hàng chân — lấy gió (`drawRecirc`) | TOGGLE qua ô ẩn | (A) không — tính lại mỗi lượt, so `tag` hình. (B) có | F1 | — |
| Bảng Đèn hình xe 2D (`CarIconBoardView.refresh`) | đọc + bảng + giữ | **(A) có**: chỉ xin vẽ khi một **ô đọc** đổi. Điểm KHÔNG ô đọc (đèn đọc…) sáng theo bảng lệnh cuối ⇒ bật bằng phím/giọng nói, không ô đọc nào đổi ⇒ điểm **không bao giờ** vẽ lại tới khi một đèn khác đổi | F3 (so trạng thái điểm `kind` với cái đã vẽ) · F1 | `StaleDisplayWiringContractTest` |
| Bảng Đèn dạng lưới (`IconBoardView`) | đọc + bảng | (A) không — `shown` là khoá hình đã vẽ. (B) có | F1 | — |
| Bảng Kính gọn (`ActionMatrixCompact`) | đọc + bảng | (A) không — `refresh` luôn `invalidate`, `shown` tính lại. (B) có | F1 | — |
| `StateDotPainter` · `GroupCellGrid` | chỉ vẽ / chỉ đọc | không — không đọc bảng | — | — |
| Widget `w_car` · khí hậu · chip thanh trên | chỉ đọc datum | không — không đọc `ControlTileState` (grep), hiện số xe thật | — | — |
| Thanh nút (dock) | ô `ControlTileFactory` | như các dòng ô ở trên | như trên | như trên |
| Gói lệnh (ô gói `macroTile` · gói bằng giọng nói) | ghi bảng TOGGLE | (B) có — `setOn` không vẽ | F1 (`announce` sau khi ghi) | `StaleDisplayWiringContractTest` |

## 4. Sửa — một luật, một đường

Cú chạm ô làm ba việc: **ghi bảng · đặt đích giữ (nút đọc được) · vẽ**. Đường phím/giọng nói nay làm đủ ba, qua MỘT chỗ:

- **`ControlWriteEcho.applied`** (`:core`, mới) — gọi từ `ControlTileState.echo` ← `VoiceControlDispatch.finish(ok)` (chỉ khi `ok`, như
  cũ): ghi bảng theo kiểu nút (y luật cũ), `LampHold.put` cho TOGGLE/COVER có `readKey`, rồi `ControlLastSent.announce()`.
- **F1** `ControlLastSent.announce()` tăng CÙNG bộ đếm `relayed` mà cầu `:wake` đã dùng ⇒ màn chính (`collectHome`, luồng chính, chỉ khi
  STARTED) gọi `resyncTiles`. `resyncTiles` nay đổ lại **cả ô widget** (xe 3D, bảng Đèn/Kính, nhóm) bằng chính đường của nhịp xe
  (`WidgetViews.refreshRead`), không chỉ ô hành động. Gói lệnh (`macroTile`, gói giọng nói) gọi `announce()` sau khi ghi bảng.
- **F2** ô TOGGLE hỏi `LampHold.shown` trước số đọc (ô COVER, chấm 3D, bảng Đèn đã hỏi sẵn) ⇒ đích của phím hiện ngay, xe xác nhận ⇒ thả;
  xe không làm ⇒ hết hạn, số đọc thắng.
- **F3** `CarIconBoardView.refresh` so danh sách trạng thái điểm với lượt đã vẽ.
- **F4** `tileCover` so chỉ số với hình đã vẽ (`TileResync.stale`) thay cờ `open`.

**Vì sao không "mọi lượt ghi bảng đều báo"** (phương án owner gợi ý): lượt đổ số của ô CŨNG ghi bảng (đồng bộ với số đọc). Hai bề mặt
cùng một nút nhận hai số khác nhau — [SUY, chưa đo] vd icon ghế 3D đọc mức từ datum (`Car3dHero.seatControls`), ô ghế trên thanh nút đọc `readState`
— thì ghi đè nhau ⇒ mỗi lượt ghi lại báo ⇒ vòng lặp vẽ trên luồng chính. Báo chỉ ở đường KHÔNG vẽ (`record` của ô vẫn im lặng — bài
`ControlLastSentTest` + `ControlWriteEchoTest` khoá) thì không thể thành vòng.

**Đồng hồ đích giữ**: `ControlWriteEcho` dùng `System.nanoTime()/1e6` (chạy được trong bài JVM), người vẽ hỏi bằng
`SystemClock.uptimeMillis()`. Cùng gốc [ĐO nguồn android-10.0.0_r47]: `system/core/libutils/SystemClock.cpp:35-40` (`uptimeMillis` =
`systemTime(SYSTEM_TIME_MONOTONIC)`), `libcore/ojluni/src/main/native/System.c:218-221` (`System_nanoTime` = `clock_gettime(CLOCK_MONOTONIC)`),
javadoc `frameworks/base/core/java/android/os/SystemClock.java:95-101`.

**Chi phí** (luật owner 08/10): 0 lượt đọc HAL, 0 luồng, 0 nhịp mới. Một lượt `resyncTiles` cho MỖI lệnh phím/giọng nói/gói ĂN (đổ lại
tại chỗ, không dựng view — trừ ô đọc chưa đăng ký hàm đổ, lùi y như nhịp xe). `CarIconBoardView.refresh` thêm một danh sách `kind` cỡ số
điểm (≤ ~10) mỗi nhịp đổ số.

## 5. Kiểm

- `ControlWriteEchoTest` (core, 7 bài): phím đóng cốp khi xe còn đọc mở ⇒ hiện ĐÓNG ngay, xe đọc 0 ⇒ thả giữ; xe từ chối ⇒ hết hạn số đọc
  thắng; nút không đọc được ⇒ không giữ, bảng + báo; STEP ⇒ bảng mức của `:app`, báo; `record` không báo. **Đỏ khi gỡ**: bỏ `hold.put` +
  `announce` trong `applied` ⇒ 3/7 đỏ (đã chạy thử, rồi trả lại).
- `StaleDisplayWiringContractTest` (app, 5 bài): dây `finish → echo`, gói lệnh `announce`, `resyncTiles` đổ ô widget, TOGGLE hỏi đích giữ,
  COVER so hình, bảng Đèn so điểm.
- Đổi luật bài cũ cho đúng thiết kế mới: `KeyCtlWiringContractTest` (luật theo kiểu nút dời sang `ControlWriteEcho`),
  `ControlStateUxContractTest` (COVER: "chỉ vẽ khi đổi" nay là so với hình; `ControlVisuals.of` còn MỘT chỗ gọi — `look`).
- Bộ đầy đủ `:core:test :app:testDebugUnitTest :app:testVehicleTestUnitTest`: **10 648 test, 0 fail, 6 skip** (`scripts/count-tests.py`).

## 6. Chưa đo / còn mở

- **[CHƯA ĐO] trên máy ảo**: off-car `ControlWriteFlow.write` trả `rc = null` ⇒ `actByKind` = `false` ⇒ `finish(ok = false)` không ghi
  bảng (xe giả `DUMMY_CAR` chỉ NHẠI số đọc qua `CtlWriteTap`, không đổi kết quả ghi). Không có móc vehicleTest nào cho một lượt ghi phím
  THÀNH CÔNG; không thêm móc mới trong lượt này (ngoài phạm vi). Dựa vào bài kiểm.
- 🚗 Chốt trên xe: gán phím `ctl:trunk:flip` ⇒ đóng/mở ⇒ ô cốp + chấm cốp 3D đổi NGAY như cú chạm; nói *"bật đèn đọc"* (nếu xe có) ⇒
  điểm trên bảng Đèn sáng ngay.
- Ngoài phạm vi (ghi backlog): `RainDefrostApplier` · `RecircApplier` · nút *Thử* trong Cài đặt · cầu kiểm thử `ctl` ghi xe KHÔNG qua
  bảng — nút có số đọc vẫn đúng theo số đọc; nút KHÔNG số đọc thì ô không biết lượt ghi đó. `VoiceReadback` ghi số xe thật vào bảng
  không `announce` — hội tụ ở nhịp đọc xe kế (nó chỉ ghi khi đã có số đọc).
