# 3.1.10 · Đường sự kiện xe: sự kiện không liên quan không còn vẽ lại màn (2026-10-16)

> Owner 16/10: *"gôm luôn 3.1.10 đi em, xử hết mọi thứ có thể"* · *"đừng vá trên vá làm chậm performance"*.
> Mức bằng chứng: **[ĐO]** = đo máy ảo / bài JVM / đọc mã có trích dẫn · **[SUY]** = suy luận khớp hiện tượng · **[CHƯA BIẾT]**.
> Backlog: `EVENT-PATH-SCOPED` · `HOLD-EXPIRY-REDRAW` · `TURN-POLL-SERVED` · `CARSTATUS-PASS-COPY`.

## 0. Tóm tắt

| Hạng mục | Trước | Sau | Mức |
|---|---|---|---|
| Khung vẽ / 60 s, màn 3D QA2, một datum KHÔNG hiện đổi mỗi giây | 66 | 8 · 6 · 8 (ba lượt; lượt 3 = bản cuối sau khởi động lại máy ảo) | [ĐO máy ảo] |
| CPU tiến trình launcher / 60 s cùng ca (tick 10 ms) | 168 | 67 · 49 · 60 | [ĐO máy ảo] |
| CPU nền màn đứng yên / 30 s | 20 | 25 (cùng mức nhiễu) | [ĐO máy ảo] |
| Nhịp chạy mỗi sự kiện bụi mịn (`readEvents`) | 3 (nhanh + chậm + thân xe) | 1 (chậm) | [ĐO mã + bài] |
| Cụm `CarStatus` thay mỗi sự kiện bụi mịn | 8 | 1 (`climate`) | [ĐO bài phát lại] |
| Bề mặt đổ lại mỗi sự kiện bụi mịn (ô xe 3D + thanh nút) | 2 | 0 | [ĐO bài phát lại] |
| Cấp phát của `readEvents` / sự kiện bụi mịn | ≈ 6,7 KB | ≈ 6,1 KB | [ĐO bài phát lại, ThreadMXBean] |
| Tính lại nhu cầu (`Holder.publish`) mỗi lượt chỉ đổi trạng thái xe | 1 | 0 | [ĐO bài] |
| Đích giữ (phím/giọng nói/chạm) hết hạn ⇒ vẽ lại | không ai hẹn | 1 lượt đúng lúc hết hạn | [ĐO bài + dây] |
| HAL/phút màn 3D QA2, chế độ sự kiện, mọi device đã chứng minh | 87 (Light 25) | 75 (Light 13) khi hộp xi-nhan sống · 64 (Light 2) khi hộp báo đủ đèn | [ĐO `HalLoadBudgetTest`] |

## 1. Sự kiện không liên quan chạy một lượt đầy đủ (`EVENT-PATH-SCOPED`)

### 1.1 Hiện tượng

[ĐO logcat xe 16/10] helper đẩy `postEvent 4f600010` (bụi mịn) khoảng mỗi giây suốt buổi (`trunk-state-read-2026-10-15.md` §8.1).
Mỗi sự kiện đi `HalEventCache.onEvent` → `CarStatusRepository.onEvents` → 150 ms sau một lượt `CarDataAdapter.readEvents`.

[ĐO mã, trước sửa] `readEvents` = `readBody(readSlow(readFast(prev)))`: ba nhịp chạy dưới cổng sự kiện, và nhịp chậm dựng lại mọi cụm.
Ảnh mới khác ảnh cũ (giá trị bụi mịn đổi) ⇒ `StateFlow` phát ⇒ `collectHome` → `HomeViewModel.setCarStatus` → `render`:
- `WorkspaceView.renderInternal`: `WorkspaceRenderPlanner.decide(statusChanged = true)` đưa MỌI ô widget có nội dung đọc vào kế hoạch ⇒
  `WidgetViews.refreshRead` mỗi ô (xe 3D: `taps.fill` · `hero.fill` · `bar.fill` · đặt chữ ⇒ một khung);
- `dock.setCarStatus` (kể cả khi thanh nút ẨN);
- `CarDataDemand.Holder.publish` tính lại `of` + `controlsOf` rồi báo lại `LampSignalDemand.Watch` và `CarEventFeed.screen`.

### 1.2 Đo trước [ĐO máy ảo emulator-5556, bản vehicleTest dựng từ cây làm việc 16/10 trước sửa]

Màn: hồ sơ QA2 (một ô xe 3D, thanh nút ẩn, năm chip thanh trên), xe giả bật, đỗ (`speed 0`, `P`). Kịch bản `run.sh` (thư mục phiên, không
trong repo): mỗi giây một `am broadcast … DUMMY_CAR --ei speed 0|1` — tốc độ KHÔNG hiện trên màn này (không chip, không ô, thanh nút ẩn),
nên mọi khung vẽ ra là thừa. Khung = `dumpsys gfxinfo` *Total frames rendered* sau `reset`; CPU = `utime+stime` của `/proc/<pid>/stat`.

| Lượt | Cửa sổ | Khung | CPU (tick) |
|---|---|---|---|
| trước · đứng yên | 30 s | 3 | 20 |
| trước · đổi datum không hiện mỗi giây | 60 s | 66 | 168 |

Đường ở máy ảo là `CarStatusTap` (xe giả đè số), không qua `readEvents`; nó đo đúng phần **fan-out** (trạng thái → bề mặt). Phần
`readEvents` đo bằng bài phát lại JVM (§1.4).

### 1.3 Sửa (generic, trong các thành phần có sẵn — không lớp song song, không `if` theo datum/gói)

1. **`CarDataAdapter.readEvents`** chỉ chạy nhịp SỞ HỮU một id của lô: nhanh khi lô có `CarDataDemand.FAST_IDS` hoặc khoá đọc của một nút
   đang hiện · thân xe khi có `BODY_IDS` · chậm cho phần còn lại. Nhịp không sở hữu id nào trả nguyên `prev` dưới cổng sự kiện, nên bỏ nó
   là cùng kết quả [ĐO mã: mọi field ngoài lô ⇒ `CarReadGate.read` trả `prev`; `readTyres` ở lượt sự kiện `due = false`]. Cuối lượt
   `CarStatusDelta.share`: cụm bằng cụm cũ ⇒ chính đối tượng cũ; bằng nhau toàn phần ⇒ `prev` (repo không phát).
2. **`CarStatusDelta`** (`:core`, mới): `of(a, b)` ⇒ datum + nút khác nhau (so cụm bằng `equals` trước, chỉ đi field trong cụm khác).
   Bảng `id → field` là bản chép của adapter, **được đo**: bài cho adapter đọc đúng MỘT id rồi đòi delta = {id} (71/80 id chứng minh
   bằng gateway giả; 9 id còn lại — `motor_power`, nhiệt + màu cụm lốp — gateway giả không ra số hợp lệ, được canh bằng bài phản chiếu:
   đổi riêng mỗi field của mọi cụm ⇒ delta khác rỗng).
3. **`CarDataDemand.interestOf(ids)`** — tập MỘT bề mặt bày (datum + nút), cùng phép tra với nhu cầu đọc (`expand` · `COMPANION` ·
   `HOSTED` · nút AUTO). `null` = không biết ⇒ luôn đổ: mã lạ, hoặc widget đổ cả nguồn ngoài xe (`NON_CAR_FED` = ô nhạc, bảng tổng hợp —
   nhịp trạng thái xe là đường làm mới duy nhất của phần nhạc, giữ nguyên hành vi). Xe 3D luôn tính cả lốp (công tắc lốp không có ở bề mặt vẽ).
4. **Fan-out**: `WorkspaceView.renderInternal` bỏ ô chỉ có mặt trong kế hoạch vì trạng thái xe đổi mà delta không chạm tập của nó (không
   đổ, cũng không lùi về dựng lại); `KachiHomeRender.render` cùng luật cho thanh nút. Thanh trên đã tự so chữ trước khi vẽ (H5 · 2.88 R6) — không đổi.
5. **`CarDataDemand.Holder.publish`**: state chỉ khác `carStatus` (và cùng tập khuất) ⇒ không tính lại, không báo lại.
6. **`CARSTATUS-PASS-COPY`** (Soát Pass 10): ba nhịp đi `copy` từ ảnh cũ cho cả sáu cụm còn dựng mới (`Drivetrain` · `Climate` · `Tyres`
   · `Lights` · `Identity` · `Infotainment`) — field thêm sau cho nhịp khác giữ theo cấu trúc.

Chi phí thêm: 0 lượt HAL, 0 luồng, 0 nhịp. Mỗi lượt render: một `CarStatusDelta.of` (≤ 9 phép so cụm) + một `interestOf` mỗi ô widget / thanh nút.

### 1.4 Đo sau

[ĐO máy ảo, cùng màn, cùng kịch bản, bản vehicleTest sau sửa]

| Lượt | Cửa sổ | Khung | CPU (tick) |
|---|---|---|---|
| sau · đứng yên | 30 s · 60 s | 3 · 7 | 25 · 33 |
| sau · đổi datum không hiện mỗi giây (lượt 1 · 2 · 3 bản cuối) | 60 s | 8 · 6 · 8 | 67 · 49 · 60 |

Phần còn lại (~35–50 tick so với nền) là chính receiver xe giả (`DummyCarReceiver.onReceive` · `CarStatusTap.set` · combine) — chạy trong
tiến trình launcher [SUY: đường xe thật không có receiver này].

Hành vi giữ nguyên [ĐO máy ảo cùng phiên]: `--es doors lf` ⇒ xe 3D vẽ một đợt 19 khung (cửa mở); đóng + `--ez turn_l true` ⇒ đợt 19 khung
+ xi-nhan nháy (`blink [] -> [light_left_turn]`, mỗi mép một khung); tắt ⇒ `blink [light_left_turn] -> []`.

[ĐO bài `EventPathScopedTest`, phát lại 60 sự kiện bụi mịn qua `HalEventCache` + `EventCachedGateway` + adapter thật, màn QA2]:

| | Trước (mã cũ — thử ĐỎ) | Sau |
|---|---|---|
| ảnh phát | 60 | 60 (giá trị đổi mỗi lần — ô/chip bụi mịn cần) |
| cụm thay | 480 (8/sự kiện) | 60 (1/sự kiện) |
| bề mặt (xe 3D + thanh nút) đổ lại | 120 (mọi bề mặt mỗi ảnh — mã `render` cũ) | 0 |
| giá trị không đổi (60 sự kiện cùng số) | — | 1 ảnh (sự kiện đầu), cache không báo phần còn lại |
| sự kiện cửa trước-trái (4) | 24 cụm thay | 3 cụm (`body`), xe 3D đổ lại 3 lần |

## 2. Đích giữ hết hạn không ai vẽ (`HOLD-EXPIRY-REDRAW`)

[ĐO mã] `LampHold.remaining` (KDoc: *"để người vẽ hẹn MỘT lần vẽ lại lúc hết hạn"*) không có chỗ gọi nào trong `:app` ⇒ đích giữ của cú
chạm ô cốp/đèn, chấm 3D, bảng Đèn và lượt ghi phím/giọng nói (`ControlWriteEcho.applied`) hết hạn mà không ai vẽ lại; số đọc chỉ thắng
khi trạng thái xe tình cờ đổi. [SUY] trên xe lỗ này bị che bởi sự kiện bụi mịn ~1 Hz (mỗi giây vẽ lại tất cả) — §1 gỡ đúng cái che ấy.

Sửa: `LampHold.onPut` (báo người vẽ mỗi lần đặt đích) + `LampHold.nextExpiryIn(now)` (đích SỚM nhất, không phải dài nhất). Màn chính
(`KachiHomeRender.watchHoldExpiry`, nối trong `collectHome` lúc STARTED, gỡ lúc rời) hẹn ĐÚNG MỘT tin nhắn mang `HoldExpiryToken` trên
`handler` sẵn có của Activity: tới hạn ⇒ `resyncTiles()` (đổ lại ô hành động + mọi ô widget, không dựng view) rồi hẹn đích kế. Đích mới
thay hẹn cũ. 0 luồng, 0 vòng đếm theo nhịp.

Bài: `EventPathScopedTest.dich giu bao nguoi ve va hen het han som nhat` (đỏ khi gỡ `onPut()` khỏi `put`) · `EventPathWiringContractTest`.
Máy ảo: [CHƯA ĐO] — xe giả nhại ngay mọi lệnh có số đọc (đích được thả khi khớp, không bao giờ tới hạn) nên không dựng được ca "xe không làm
theo" trên máy ảo; kiểm trên xe ở §5.

## 3. Xi-nhan còn poll (`TURN-POLL-SERVED`)

[ĐO dòng `KachiHalEvent poll còn lại` xe 16/10, theo coordinator] xi-nhan là datum duy nhất ngoài đường sự kiện (Light 30–36 lượt/phút).
[ĐO mã] xi-nhan của màn đến bằng hộp socket `LampLive` (helper `KachiHalSignal`), `LampLive.overlay` ĐÈ số đọc trong `collectHome` ⇒ lúc
hộp sống, lượt đọc `getLightStatus(4/5)` của nhịp chậm không bao giờ tới màn. Số 30–36 gồm những gì ngoài 12 lượt xi-nhan của nhịp chậm:
[CHƯA BIẾT] (off-car chỉ dựng được phần nhịp chậm; chốt bằng `KachiPerf` + dòng `poll còn lại` ở §5).

Sửa: `LampLive.served(t)` = đúng tập datum `overlay` đè (xi-nhan luôn; đèn khác khi helper đã báo) — bài đo khớp hai bên. Nhu cầu đưa cho
`CarDataAdapter` (`AppContainer`) = nhu cầu màn − `LampLive.shared.served()`. Hộp `null` (helper vắng/đứt) ⇒ tập rỗng ⇒ nhịp chậm kế đọc
lại như cũ (đường phục hồi không gate bằng dữ liệu chỉ chính nó làm mới). Bề mặt không bày đèn ⇒ nhu cầu vốn không có (cơ chế H1).

[ĐO `HalLoadBudgetTest.3110 xi-nhan khong poll khi hop socket dang de`, màn 3D QA2, mọi device đã chứng minh]: hộp tắt 87/phút (Light 25) ·
hộp xi-nhan 75 (Light 13) · hộp đủ đèn 64 (Light 2 — chỉ còn đối soát `headlight_feedback`). Chế độ poll và "chưa chứng minh" không đổi
(550 / 676).

## 4. Bài khoá

| Bài | Khoá gì | Thử ĐỎ [ĐO 16/10] |
|---|---|---|
| `core EventPathScopedTest` (8) | bảng delta khớp adapter · mọi field có id · phát lại bụi mịn/cửa · luật bề mặt · `publish` không tính lại · đích giữ · `served` = `overlay` | bỏ chọn nhịp ⇒ 2 bài đỏ (480 cụm) · thêm datum lạ vào tập xe 3D ⇒ bài bề mặt đỏ · gỡ `onPut()` ⇒ bài đích giữ đỏ |
| `core CarStatusPassCopyTest` (2) | ba nhịp + lượt sự kiện với cổng rỗng trả NGUYÊN ảnh (mọi field, phản chiếu) · adapter không dựng `CarStatus.X(` | trả `climate` về `CarStatus.Climate(` ⇒ đỏ |
| `core HalLoadBudgetTest` (+1) | xi-nhan không poll khi hộp đè | — (số ghim 87 → 75 → 64) |
| `app EventPathWiringContractTest` (3) | dây `WorkspaceView` · thanh nút · `watchHoldExpiry` · `collectHome` · `AppContainer` | — |

Bài cũ giữ xanh: `TrunkLidStaleTest` · `StepTileSyncTest` · `HalLoadBudgetTest` · `HalEvents*` · `CarDataDemandTest` · `LampLiveContractTest`.

## 5. Kiểm trên xe (bản kế)

1. Màn 3D, đỗ, 2 phút không chạm: `KachiPerf` dòng `HAL đọc` (< 75/phút khi Light đã có sự kiện) và dòng `KachiHalEvent poll còn lại` không
   còn `light_left_turn`/`light_right_turn` khi `KachiHalSignal` đang nối.
2. `dumpsys gfxinfo com.byd.launcher` sau `reset`, 60 s không xi-nhan, không chạm: khung ≈ 0 dù `postEvent 4f600010` vẫn tới mỗi giây.
3. Phím Đảo cốp lúc xe không làm theo (hoặc chạm đèn đọc): sau 3 s / 10 s ô + 3D về số đọc mà không cần chạm gì (`HOLD-EXPIRY-REDRAW`).
