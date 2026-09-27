# TRIP-TIME-6MIN · `TripTimeSmoother` — thời gian chuyến tiến từng phút (2.75)

> **Trạng thái**: Current · **Cập nhật**: 2026-09-27 (off-car, đo trên xe lúc 11:48–11:55 rồi làm off-car) · **Mục đích**:
> ô/chip *Thời gian chuyến* đang in `h:mm` từ một con số HAL chỉ có **bậc 6 phút** ⇒ phút là **độ chính xác giả**; owner
> duyệt *"cách 1"* (~11:55): hiển thị = mốc HAL + phút đã trôi, **kẹp dưới bậc kế**, chỉ nhảy TIẾN khi HAL đổi.
> Liên quan: `docs/PROJECT-BACKLOG.md` (**ONCAR-2026-09-27 · TRIP-TIME-6MIN**), `TelemetryRegistry.kt:153` (`trip_hours`),
> `CarDataAdapter.kt`, `TelemetryReadout.hoursToHm`.

---

## 1. Sự thật đo được

| Mốc | Lệnh / nguồn | Kết quả | Mức |
|---|---|---|---|
| 11:48:19 | cầu kiểm thử `hal get` → `BYDAutoInstrumentDevice.getCurrentJourneyDriveTime` (KHÔNG `--es args`) | `1.8000001` | [ĐO] |
| 11:49:08 → 11:54 | cùng lệnh, mẫu 20 s/lần | `1.9` đứng yên nhiều phút; `2.0` thấy đầu 11:54:35 | [ĐO] |
| — | `../jadx-tmap/sources/android/hardware/bydauto/instrument/BYDAutoInstrumentDevice.java:305` | `DRIVE_TIME_MAX = 99.9` ⇒ đơn vị GIỜ, **một chữ số lẻ** = bậc **0,1 h = 6 phút** | [ĐO nguồn] |
| — | `getCurrentJourneyDriveMileage` | `0.0` khi đậu; `DRIVE_MILEAGE_MAX = 9999.9` ⇒ bậc 0,1 km (30–60 km/h nhảy mỗi 6–12 s, gần realtime) ⇒ **không sửa** | [ĐO] |
| — | `TelemetryReadout.kt:234` → `hoursToHm` | in `h:mm` ⇒ "1:48" rồi 6 phút sau "1:54" | [ĐO code] |
| — | owner trên xe | *"thời gian trip không realtime, lâu lâu mới nhảy 1 lần, tầm 5-6 phút"* | lời khai |

`1.8000001` là float của OEM lên double — **cùng một mốc** với `1.8`, không phải một bậc ([SUY] từ dạng số; bộ làm mượt so
lệch với `1e-6 h` nên hai dạng này không bao giờ bị coi là đổi bậc).

## 2. Chẩn đoán

Không phải nhịp đọc của Kachi chậm (nhịp chậm 10 s, `CarStatusRepository.kt:52`): HAL **chỉ đổi mỗi 6 phút**. Bệnh là
`h:mm` hứa độ chính xác **phút** mà nguồn chỉ có **6 phút**. Hai cách chữa: (2) in đúng độ phân giải *"1,8 h"* — đúng nhưng
xấu; (1) **nội suy theo đồng hồ giữa hai bậc, kẹp dưới bậc kế** — owner chọn (1).

## 3. Cơ chế "cách 1" — `TripTimeSmoother` (`:core`, thuần, 78 dòng)

`core/src/main/kotlin/com/byd/clusternav/launcher/TripTimeSmoother.kt` — **một instance / một adapter** (có trạng thái).

- hiển thị = **mốc HAL cuối** + **số phút TRÒN** đã trôi kể từ lúc mốc ấy được thấy lần đầu, **kẹp ≤ mốc + 5 phút**
  (`MAX_EXTRA_MIN = 5` → 5/60 h ≈ 0,083 < `HAL_STEP_H = 0,1`; bài canh khoá quan hệ này) ⇒ cao nhất "1:53" khi HAL còn 1,8.
- HAL **tăng** ⇒ nhảy TIẾN đúng mốc mới, đặt lại đồng hồ trôi ⇒ từ bậc đổi đầu tiên **pha là chính xác** (phút kế đúng 60 s sau).
- HAL **giảm** (chuyến mới) hoặc `null` ⇒ quên hết, trả nguyên (`null` ⇒ "—", không đóng băng số cũ).
- Giữa hai lần reset **không bao giờ lùi** — kể cả đồng hồ hệ thống lùi (giữ số phút đang hiện rồi đếm tiếp).
- Lượng hoá theo **phút** (không liên tục) ⇒ `CarStatus` chỉ đổi **một lần/phút** khi đậu, không phải mỗi nhịp 10 s.

Dòng thời gian mẫu (HAL 1,8 thấy lúc T, đổi 1,9 lúc T+6′): T "1:48" · +1′ "1:49" · … · +5′ "1:53" · +6′ HAL 1,9 ⇒ "1:54" ·
+7′ "1:55" … — không có bước lùi, không có bước nhảy 6 phút.

### Sửa kèm: `hoursToHm` cắt sai ở biên phút [ĐO máy]

`(h * 60).toInt()` sai vì nhị phân: `4.1 * 60 = 245.99999999999997` ⇒ "4:05" (17/1000 mốc thô 0,1 h), và với `mốc + k/60`
của bộ làm mượt là **620/6000** ca (vd `1.9 + 1/60` ⇒ "1:54" thay "1:55"). Vá `TelemetryReadout.kt:368-369`:
`Math.floor(h * 60 + 1e-6)` — quét 0..99,9 h × 0..5 phút: 0 ca sai. Không vá thì phút mượt in lệch một phút ngẫu nhiên.

## 4. Nối dây — MỘT nguồn cho widget · chip · câu hỏi bằng giọng

| Chỗ | Việc |
|---|---|
| `CarDataAdapter.kt:36` | tham số `tripTime: TripTimeSmoother = TripTimeSmoother(clock)` — cùng `clock` với adapter ⇒ test bằng đồng hồ giả; `AppContainer.kt:86` dựng mặc định, không đổi |
| `CarDataAdapter.kt:73` | `Gate.fresh` — tách lượt đọc THẬT (qua `HalAbsentCache`) khỏi lối *"không hiện ⇒ trả prev"* |
| `CarDataAdapter.kt:89` | `Gate.dblVia(id, prev, via)` — **chỉ giá trị đọc thật** đi qua bộ làm mượt; `prev` (đã mượt) KHÔNG được đưa lại làm mốc (nếu đưa: 1,8 → 1,85 bị coi là bậc tăng ⇒ mốc tự nâng ⇒ vượt bậc kế; có bài E2E khoá) |
| `CarDataAdapter.kt:215` | `tripHours = g.dblVia("trip_hours", e.tripHours, tripTime::smooth)` — mọi consumer đọc `CarStatus.energy.tripHours` đều nhận số mượt: widget/ô đọc, chip thanh trên, câu hỏi bằng giọng (`refreshNow` → `readSlow`) |

Giá trị THÔ vẫn còn ở hai đường không qua `CarStatus`: `AppContainer.telemetryText` (màn kiểm từng nút, đọc thẳng
`HalBindingTable`) và cầu kiểm thử `hal get`. `CarStatus` không có field thô riêng ⇒ **không thêm** (ngoài scope; cần thì
thêm `tripHoursRaw` sau).

## 5. Trace nhịp — số mượt đổi mỗi phút có LÊN MÀN mỗi phút không? [ĐO code, file:line]

| # | file:line | Điều đọc được |
|---|---|---|
| 1 | `CarStatusRepository.kt:52` · `:103-104` | nhịp CHẬM `slowMs = 10_000` — `publish { reader.readSlow(it) }; delay(slowMs)` chạy liên tục khi HOME ≥ STARTED (`KachiHomeWiring.kt:431` `start()`, `finally stop()`) |
| 2 | `CarDataDemand.kt:185` | `trip_hours` nằm trong tập nhu cầu khi ô/chip đang hiện ⇒ `Gate.wanted` = true ⇒ **đọc HAL + qua bộ làm mượt mỗi 10 s** |
| 3 | `CarStatusRepository.kt:81-84` | `_status.value = next` — `MutableStateFlow` **chỉ phát khi `next != prev`** (`CarStatus` là data class). HAL đứng 6 phút thì trước đây 6 phút không phát; nay số mượt đổi **mỗi phút** ⇒ `!=` ⇒ phát, trễ ≤ 10 s sau mốc phút |
| 4 | `KachiHomeWiring.kt:433` → `HomeViewModel.kt:149` | `status.collect { viewModel.setCarStatus(it) }` → `_uiState.update { it.copy(carStatus = status) }` |
| 5 | `KachiHomeWiring.kt:423-425` → `KachiHomeActivity.kt:369` | `uiState.collect { … render(it) }` → `workspace.render(state.workspace, state.carStatus)` |
| 6 | `WorkspaceView.kt:157` · `:164` | `renderInternal` → `WorkspaceRenderPlanner.decide(…, statusChanged = status != oldStatus, …)` |
| 7 | `WorkspaceRenderPlan.kt:81` | `widgetNeedsFreshValues = statusChanged && nc is Widget && hasReadContent(nc)` ⇒ ô có mục đọc vào `PerSlot.rebuild` |
| 8 | `WorkspaceView.kt:178` → `WidgetViews.kt:156` · `:176` → `WidgetRefreshers.kt:71-73` | `refreshRead` → `WidgetRefreshers.refresh(v, data)` → `fill.fn(data)` đổ số TẠI CHỖ (không relayout) |
| 9 | `TelemetryReadout.kt:234` → `:368` | `"trip_hours" -> hoursToHm(tripHours)` ⇒ chữ mới |

**Kết luận [ĐO code]**: nhịp đọc 10 s ≤ 60 s và một `tripHours` đổi là đủ để `status != oldStatus` ⇒ chữ mới lên màn **trong
≤ 10 s sau mỗi mốc phút**, không cần sửa đường render. Nếu bộ làm mượt trả số **liên tục** thì mỗi nhịp 10 s đều phát —
đó là lý do lượng hoá theo phút (mục 3), giữ số lần `refreshRead` khi đậu = 1/phút.

Chip thanh trên + thanh nút: cùng lượt `render` — `KachiHomeActivity.kt:377-381` `if (prev.carStatus != state.carStatus …)
topStrip.refreshChips(state.carStatus, …)` + `dock.setCarStatus(…)` ⇒ chip *Thời gian chuyến* cũng đổi mỗi phút [ĐO code].

## 6. Giới hạn

- Trước bậc đổi **đầu tiên** sau khi app khởi động (hoặc sau `null`, hoặc lần đầu ô lên màn), mốc được thấy ở pha bất kỳ ⇒
  hiển thị **trễ ≤ 6 phút** so với sự thật; tự sửa ở bậc đổi đầu tiên (nhảy tiến, không lùi).
- Bậc HAL tới muộn > 6 phút ⇒ đứng ở `mốc + 5 phút` cho tới khi HAL đổi (kẹp cố ý — đứng còn hơn vượt rồi lùi).
- Đồng hồ = `System.currentTimeMillis` (:core không có `SystemClock`); lùi giờ được đỡ (giữ phút đang hiện), nhảy tiến giờ
  ⇒ nhảy thẳng tới kẹp +5 phút (vẫn dưới bậc kế).

## 7. Test — `TripTimeSmootherTest` (`:core`, 12 ca)

Bậc 1,8 → 1,9 theo phút (không chạm 1:54 trước HAL, nhảy đúng khi HAL đổi) · kẹp 60 phút · hằng kẹp < bậc · HAL lùi ⇒ reset ·
`null` ⇒ `null` + quên · nhiễu float cùng mốc · đồng hồ lùi · đơn điệu trên chuỗi xen nhịp 10 s/1′ · `hoursToHm` biên phút
(4,1 h ⇒ "4:06") · **E2E** HAL giả → `CarDataAdapter` → `TelemetryReadout` "1:48"→…→"1:53"→(HAL 1,9)"1:54" · E2E ô rời màn
giữ `prev` và KHÔNG đưa prev vào bộ làm mượt · E2E `null` ⇒ "—". Bài FULL WIRE cũ (`TelemetryReadoutTest:114`) vẫn xanh
(mốc "1" ⇒ "1:00").

## 8. 🚗 Kiểm trên xe (2.75)

1. Đậu (READY, không lăn bánh) **≥ 7 phút** với ô/chip *Thời gian chuyến* trên màn: phút phải **nhảy từng phút** (trễ ≤ 10 s),
   dừng ở `+5` nếu HAL chưa đổi.
2. Đúng lúc HAL đổi bậc (`hal get getCurrentJourneyDriveTime`, không `--es args`): chữ nhảy **tiến** tới `mốc mới`, **không lùi**,
   không nhảy vọt 6 phút (sau bậc đầu tiên).
3. Tắt/mở lại launcher giữa chuyến: chấp nhận trễ ≤ 6 phút cho tới bậc đầu tiên (mục 6).

## 9. Chưa biết [CHƯA BIẾT]

- HAL có đếm tiếp khi xe đứng máy/READY không lăn bánh không (ảnh hưởng: bậc có tới muộn > 6 phút ⇒ đứng ở +5).
- Trên xe có bao giờ trả `1.8` thay `1.8000001` cho cùng mốc không — code đã đỡ cả hai, chưa quan sát.
