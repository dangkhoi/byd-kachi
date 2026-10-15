# Ba lỗi owner báo sau chuyến xe 15/10 — voice lần đầu, đèn ban ngày 3D, cửa chậm

> **Ngày**: 2026-10-15 · **Loại**: chẩn đoán gốc + sửa off-car (chưa commit, chưa bump) · **Xe**: Seal, fw 2606 · **Kachi**: 3.1.5 (210)
> **Nguồn**: logcat toàn hệ thống 07:45:48 → 08:23:46 · nhật ký Kachi `usage-*.log` (gồm phiên 07:42 lúc lên xe) · ba lượt quét
> `sweep-2026092x.json` · stub BYD `jadx-tmap` · tóm tắt chuyến: [`oncar-trip-2026-10-15.md`](oncar-trip-2026-10-15.md).
> Mức bằng chứng theo `CLAUDE.md` §2: **[ĐO]** = đọc log/source, **[SUY]** = suy luận khớp, **[CHƯA BIẾT]** = chưa có dữ liệu.

## 1. Voice — "lần đầu mở Kachi voice không nghe, báo không nghe rõ, lần 2 mới được"

### Dòng thời gian [ĐO logcat, tiến trình `:wake`, "Hey Kachi" đang BẬT, nhạc đang phát]

| Lúc | Sự kiện |
|---|---|
| 07:48:50.579 | `LISTEN_NOW` (phím) tới `:wake`; mô hình đã nạp sẵn (`mô hình sẵn=true`) |
| 07:48:52.857 | `sẵn sàng nghe sau 2172 ms` — phiên xin micro |
| 07:48:53.481 | `KachiVoiceMic: chắn lượt 'chinh': micro đang thuộc lượt 'wake#1' — bỏ qua` ⇒ chuỗi rỗng ⇒ *"không nghe rõ"* |
| 07:48:54.498 | luồng wake (`kachi-wake`) in `sherpa ra: "" (25600 mẫu)` rồi mới `AudioRecord stop` |
| 07:48:57.340 | lần bấm 2: `sẵn sàng nghe sau 162 ms` → `mic mở sau 267 ms` → nghe được câu lệnh |

Cùng họ lỗi còn hai lần nữa: `chắn lượt 'hoi-thoai' … 'wake#1'` lúc 07:49:16.472 và 08:14:31.706 — bộ nghe câu gọi **giành lại mic giữa
hai lượt hội thoại** (07:49:05.848 nó mở `AudioRecord` lại sau `BUSY_NAP_MS` 2 s) rồi lượt nối bị chắn.

### Gốc [ĐO mã + log]

1. Lối phím (`VoiceWakeService` nhánh `ACTION_LISTEN_NOW`) **không** nhường micro như lối câu gọi (`fireWake`): bộ nghe "Hey Kachi" vẫn giữ
   chốt `VoiceSingleFlight` + `AudioRecord`, phiên chỉ xin nó nhường ở lúc mở mic (`VoiceCapture.listen` → `VoiceMicPreempt.preempt`).
2. `VoiceMicPreempt.WAIT_MS = 600` giả định bộ nghe hỏi cờ nhường mỗi khung ~100 ms. Đúng với engine KWS, **sai** với engine ASR mặc định
   (`VoiceWakeAsr`, từ 2026-09-22): một `feed` là **một lượt giải mã cửa sổ 1,6 s**, không ngắt được. Nhịp giải mã của bộ nghe trên xe hôm đó
   [ĐO 25 cặp `sherpa ra` liền nhau]: **p50 1 212 ms · p90 1 926 ms · max 2 667 ms**. Nhạc đang phát ⇒ cửa sổ qua cổng RMS ⇒ giải mã gần như
   liên tục ⇒ cú bấm đầu gần như luôn rơi vào giữa một lượt giải mã ⇒ hết 600 ms thì rút.
3. Vì sao lần 2 "được": sau lượt giải mã, bộ nghe thấy cờ nhường còn dựng ⇒ nhả mic + nghỉ `YIELD_NAP_MS` 3 s ⇒ cú bấm thứ 2 trong 3 s ấy
   cầm mic ngay.

Không phải nạp mô hình nguội (`mô hình sẵn=true`, nạp xong từ 07:45:54) và không phải lỗi hạ nhạc 3.1 (lượt 1 chưa tới bước mở mic).

### Sửa (generic, tầng thi hành)

| Tệp | Đổi |
|---|---|
| `app/…/voice/VoiceWakeService.kt` | `holdMicForSession()` = bước (1)(2)(4) của `fireWake` (đỗ bộ nghe · mốc nhường 18 s · hẹn nghe lại vô điều kiện); `fireWake` **và** `LISTEN_NOW` (khi wake BẬT) dùng chung ⇒ bộ nghe nhả mic ngay khi lệnh tới (trước lúc phiên xin mic ~2,3 s) và không giành lại giữa các lượt hội thoại |
| `core/…/voice/VoiceMicPreempt.kt` | `WAIT_MS` 600 → **3 000 ms** (phủ max đo + `stop/release`); vẫn là hằng, chờ tới khi mic nhả (nhịp 30 ms) |
| `app/…/voice/VoiceWakeListener.kt` | hỏi cờ nhường + cổng nghe lần nữa ngay trước `kws.feed` ⇒ không mở thêm một lượt giải mã khi đã bị xin nhường |

Test: `VoiceMicPreemptTest.tran mac dinh doi duoc mot luot giai ma wake dai nhat da do tren xe` (đỏ với 600) ·
`VoiceWakeIsolationContractTest.LISTEN_NOW nhuong micro ngay khi lenh toi, truoc khi mo phien` (đỏ khi bỏ lời gọi) + ba bài `fireWake` cũ
chuyển sang khoá thân `holdMicForSession`.

Hiệu năng: 0 lượt đọc/luồng/nhịp thêm. Bộ nghe nghỉ (đỗ trong `wait()`, 0 % CPU) suốt lượt nhường thay vì giải mã song song với phiên.

## 2. Đèn ban ngày trên 3D — "tắt rồi, đèn tắt, xong trên 3D tự sáng lại, đèn xe đã tắt"

### Dòng thời gian [ĐO logcat]

| Lúc | Sự kiện |
|---|---|
| 08:21:52.549 | `Car3d: tap drl → drl TẮT` · `setDayTimeLightState [2]` rc=0 |
| 08:21:52.579 | xe phát `event_type =3ac00024, value = 2` (đèn đã tắt) |
| suốt chuyến | `KachiHalSignal: đèn Lamps(… drl=null)` — luồng sự kiện helper **không bao giờ** báo DRL |

### Gốc [ĐO]

- Datum `light_drl` đọc feature `985661476` = `LIGHT_DAY_RUNNING_LIGHT_AUTO_STATE` (`BYDAutoFeatureIds.java:1683`); trên xe nó trả **1 hoặc 2**
  (ba lượt quét: `int=2` và `int=1`).
- Thang: `DAYTIME_LIGHT_INVALID=0 · OPEN=1 · CLOSE=2 · UNDEFINED=3` (`BYDAutoLightDevice.java:8-11`).
- `HalBindingTable.readBool` = `coerceBool` = `coerceInt > 0` ⇒ **2 (TẮT) thành BẬT**.
- `LampHold` giữ đích lạc quan 3 s; số đọc "BẬT" không trùng đích "TẮT" nên hết hạn thì số đọc thắng ⇒ đèn 3D sáng lại. Đây cũng là gốc của
  việc 2026-09-24 phải gỡ `readKey` của nút `drl` (*"đọc lại không phản ánh đúng"*): số đọc đúng, phép giải sai.
- Luồng sự kiện không cứu được vì helper không nhận `onDayRunningLightStateChanged` cho sự kiện `0x3ac00024` (helper pid nhận đúng
  `postEvent … 3ac00024 value=2` nhưng không phát dòng `light.dayRunning`) — callback nào của `AbsBYDAutoLightListener` nhận mã này: [CHƯA BIẾT].

### Sửa

`HalReadTables.BOOL_ENUM` (dữ liệu: datum → (mã BẬT, mã TẮT)) + `enumBool`; `HalBindingTable.readBool` đi bảng ấy cho datum có mặt. `light_drl`:
1 ⇒ BẬT, 2 ⇒ TẮT, 0/3 ⇒ `null` ("—", không đoán). Datum bool khác không đổi.

Test: `DrlReadDecodeTest` (fixture nguyên văn `"int=2 float=- buf=-"`; chuỗi tới `CarStatus.lights.drl`). Hiệu năng: 0 lượt đọc thêm.

Chưa làm (owner chốt): nối lại `readKey = "light_drl"` cho nút `drl` để có xác nhận "xe đã làm theo" — nay số đọc đã đúng.

## 3. Cửa — "mở cửa, đóng cửa xe nhận chậm lắm, một lúc sau mới hiển thị đúng"

### Số đo [ĐO]

- 15/10 cả chuyến: Kachi đọc `getDoorState` mỗi **10 s** (153/156 khoảng cách = 10 s) — nhịp chậm của `CarStatusRepository`.
- Lúc lên xe (nhật ký Kachi, tiến trình 3.1.4): cửa trước-trái đọc MỞ lúc 07:42:10 và 07:42:20, ĐÓNG ở lượt kế 07:42:34.9 (khoảng 14,5 s).
  Lúc cửa thật đóng không có trong log ⇒ độ trễ thật [SUY] ≤ một nhịp (≤ 10–14 s, TB ~5 s).
- Không có đường sự kiện cửa: helper chỉ đăng ký `AbsBYDAutoLightListener`; `AbsBYDAutoBodyworkListener.onDoorStateChanged(area, state)` có
  trong stub (`AbsBYDAutoBodyworkListener.java:52`) nhưng **chưa có bằng chứng shell trên xe** (§14 tầng 1).
- 3.1.5 không đổi nhịp cửa (cửa vẫn ở nhịp chậm như trước); nó chỉ cắt đọc ô ẩn/thanh ẩn.

### Sửa (bước tạm có số, chờ đường sự kiện)

Nhịp **thân xe** riêng: `CarStatusReader.readBody/bodyNeeded` + vòng thứ ba `CarStatusRepository.bodyMs = 3 000`; cửa rời nhịp chậm
(`CarDataDemand.BODY_IDS`). Chỉ chạy khi màn đang bày cửa (`needsBody`), không thì 0 lượt đọc và không hỏi màn (vòng hỏi nhu cầu TRƯỚC cổng
màn — cũng bớt binder `awake()` của vòng nhanh khi không cần). `refreshNow` (câu hỏi bằng giọng) đọc cả nhịp này.

Chi phí: 4 lượt / 3 s = 80/phút thay 24/phút ⇒ **+56/phút** khi cửa đang hiện. Màn 3D trên xe [ĐO KachiPerf] ~520–534/phút ⇒ ~586, dưới
ngân sách 600 owner chấp nhận cho màn 3D. Trần trên off-car `HalLoadBudgetTest`: màn mặc định 600 → 656, QA2 474 → 530 (ghim lại
tường minh). Độ trễ cửa: ≤ 3 s (TB 1,5 s) thay ≤ 10 s.

Test: `CarStatusRepositoryTest.nhip than xe doc cua moi 3 s khi man bay cua, khong doc khi khong bay` ·
`CarDataDemandTest.BODY_IDS khop dung danh sach ma readBody doc, va readSlow khong doc cua` · `HalLoadBudgetTest` (mô phỏng thêm nhịp 3 s).

### Đường đúng về sau (0 lượt poll) — cần xe

Đo shell thô trên xe (§14 tầng 1): helper đăng ký `AbsBYDAutoBodyworkListener`, mở/đóng từng cửa, ghi lại `onDoorStateChanged(area, state)`
(area 1..4? state 0/1?). Xanh ⇒ thêm topic `body.door` vào helper + `HalSignalClient` + gộp như `LampLive`, rồi gỡ nhịp thân xe.

## 4. Kiểm trên xe

1. **Voice**: bật "Hey Kachi", mở nhạc, để yên ≥ 1 phút rồi bấm phím thoại **một lần** ⇒ phải nghe ngay. Log: không có `chắn lượt 'chinh'`;
   có `mic mở sau …`. Thử thêm một câu cần hỏi lại (hội thoại) ⇒ không có `chắn lượt 'hoi-thoai'`.
2. **DRL**: trên 3D tắt đèn ban ngày, chờ ≥ 15 s ⇒ đèn 3D vẫn tắt; bật lại ⇒ sáng và giữ.
3. **Cửa**: màn có xe 3D, mở/đóng từng cửa ⇒ hình đổi trong ≤ 3 s. `KachiPerf` ở màn 3D ≤ 600/phút.
