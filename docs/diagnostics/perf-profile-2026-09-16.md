# Profiling + tối ưu Kachi launcher — 2026-09-16

> **Trạng thái**: Current · **Cập nhật**: 2026-09-16 (senior review Pass 1 — §9) · **Bản**: 1.66 (67) → **1.67 (68)** · **Owner**: dangkhoi
> **Mục đích**: Số ĐO trước/sau của vòng tối ưu hiệu năng (H1–H6), kèm lệnh tái lập và phần CHƯA đo được.

Spec: `docs/specs/kachi-perf-2026-09.html` · Backlog: `docs/PROJECT-BACKLOG.md` §(K) PERF.

Mức bằng chứng theo CLAUDE.md §2: **[ĐO]** = đọc từ máy thật · **[SUY]** = suy từ số đã đo · **[CHƯA BIẾT]**.

---

## 0. Số THẬT từ xe — bản 1.66, DL3, 47,2 phút lăn bánh

Nguồn: `usage-*.log` do chính app ghi ra thẻ (47,2 phút, 40 653 dòng, lọc theo pid Kachi).
⚠ Tệp log gốc nằm ngoài repo (chứa VIN) — **không** chép nội dung tệp vào doc, chỉ chép số tổng hợp.

| Chỉ số | [ĐO] 1.66 trên xe | Ghi chú |
|---|---|---|
| Dòng getter HAL (`D/BYDAuto*Device`) | **24 055 / 47,2 ph = 510/phút** | mỗi lượt đọc là một binder IPC |
| `W/AbsBYDAutoDevice` "no permission" | **10 804 = 229/phút** | đọc lại đúng thứ vừa bị từ chối, mỗi nhịp |
| `I/SimpleCast` | 1 219 = 26/phút | trong đó **305** `am stack list` + **304** `dumpsys display` (≈13 lệnh shell/phút) |
| App tự ghi log ra thẻ | 3,82 MB / 47,2 ph = **79 KB/phút** | `flush()` sau **mỗi** dòng |
| RSS Kachi | 537 MB (native heap 477 MB) | mô hình nghe fp32 266 MB + onnxruntime |
| RAM trống của máy | 56–94 MB, `memFactor=1` | |
| CPU Kachi lúc "không làm gì" (`top`) | 6,6 % trên 8 lõi | ≈ **nửa lõi liên tục** |

### 0.1 [ĐO] Nhịp NHANH trên xe chạy chậm hơn 1 Hz — bằng chứng cho "HAL là nút cổ chai"

Đếm theo số **nhịp** trong chính log ấy, không theo đồng hồ tường:

- `getSteeringWheelValue` (nhịp NHANH, 1 Hz danh nghĩa): **1 114** lượt.
- `getSunroofState` / `getPowerLevel` / `getAlarmState` (nhịp CHẬM, 10 s): **158** lượt ⇒ cửa sổ poll ≈ **1 580 s**.

⇒ chu kỳ nhịp nhanh THẬT ≈ 1 580 / 1 114 = **1,42 s**, không phải 1,00 s. Vì `delay(1 s)` chạy **sau** lượt
đọc, phần dôi ra (≈0,42 s) chính là thời gian 18 lượt đọc HAL chiếm ⇒ **≈23 ms mỗi lượt đọc**, và **≈42 % một
lõi** chỉ để nuôi vòng nhanh. Con số này khớp độc lập với `top` (6,6 % × 8 lõi ≈ 53 % một lõi). **[ĐO]**

Tổng tải poll (suy từ số nhịp đếm được): fast 1 114 × 18 + slow 158 × ~105 ≈ **36 600 lượt đọc / 26,3 phút
≈ 1 390 lượt/phút**. **[SUY từ ĐO]**

---

## 1. Máy ảo tái lập ĐÚNG lịch poll của xe

Máy ảo `emulator-5554` (Android 10, arm64), APK `:app:assembleVehicleTest`, màn chính mặc định (thanh trên 8
chip · 3 ô: nhóm *Kính* + ô trống + app YouTube · thanh nút ẩn).

Bộ đếm `KachiPerf` (mới, **ship theo app**) đếm ở đúng ba chỗ tiền mất tiền mang: mỗi lượt vào `BydHalGateway`,
mỗi lệnh rời `ShellTransport`, mỗi byte `KachiLog` ghi ra thẻ. Báo cáo một dòng/phút, chuẩn hoá theo **cửa sổ
đo được** chứ không nhân hằng số.

**[ĐO] máy ảo 1.66 = 1 404 lượt đọc HAL/phút** — khớp con số suy từ xe (≈1 390/phút) trong vòng 1 %. Tức máy
ảo tái lập **đúng lịch poll**; chỉ **giá mỗi lượt** là khác (máy ảo không có HAL BYDAuto nên rẻ hơn nhiều).
⇒ mọi thay đổi về *số lượt* đo được off-car; phần *giá mỗi lượt* phải đo lại trên xe (§6).

---

## 2. Bảng TRƯỚC / SAU trên máy ảo

| Chỉ số (máy ảo, màn chính, đứng yên) | 1.66 TRƯỚC | 1.67 SAU | Đổi |
|---|---|---|---|
| **Lượt đọc HAL / phút** | **1 404** | **48** (phút 1–2) → **0** (từ phút 3) | **−96,6 % → −100 %** |
| Bỏ vì *không hiện trên màn* / phút | 0 | 528–616 | cổng H1 |
| Bỏ vì *xe không có* / phút | 0 | 51–102 | cổng `HalAbsentCache` |
| **CPU (% một lõi, 150–210 s đứng yên)** | **1,69 %** | **0,71 %** / **0,66 %** (2 lượt) | **−58 % … −61 %** |
| Dòng log app ghi / phút | 9,6 | 3,1 | −68 % |
| Lệnh shell / phút (không cast) | 0,0 | 0,0 | đã là 0 (xem §4) |
| `am start -W` TotalTime (1 lượt, WARM) | 517 ms | 477 / 397 ms | trong nhiễu |
| PSS native heap | 352 MB | 352 MB | không đổi (H4 chưa làm) |

Vì sao SAU rơi về **0**: máy ảo không có HAL nên **mọi** datum trả `null`; sau 3 nhịp chậm, `HalAbsentCache`
cho tất cả "nguội" và chỉ thử lại theo nhịp giãn dần (60 s → ×2 → trần 10 phút). Trên xe, các datum ĐANG HIỆN
sẽ đọc ra số ⇒ **không** nguội ⇒ số ổn định kỳ vọng là "số datum đang hiện × 6 lượt/phút".

### 2.1 [SUY] Chiếu sang xe

| | 1.66 [ĐO] xe | 1.67 [SUY] | Cơ sở |
|---|---|---|---|
| Lượt đọc HAL/phút | ≈1 390 | **≈50–130** | màn mặc định cần ≈8–22 datum × 6 nhịp/phút; vòng nhanh TẮT |
| Dòng "no permission"/phút | 229 | **≈2–12** | 12 feature bị từ chối, nguội dần về ≈1 lượt/10 phút mỗi cái |
| CPU idle | 6,6 % / 8 lõi | **≈0,5–1 %** | 42 % một lõi là của vòng nhanh; nó không còn chạy ở màn mặc định |
| Log ra thẻ | 79 KB/phút | **≈5–15 KB/phút** | phần lớn 79 KB là log HAL do chính poll sinh ra |
| Lệnh shell/phút khi ĐANG cast | ≈13 | **≈6,5** (full) / **≈6,5** (split) | bỏ `dumpsys display` mỗi nhịp + gộp `am stack list` |

⚠ Đây là **[SUY]**, chưa phải [ĐO]. Playbook đo lại trên xe ở §6.

---

## 3. Đã sửa gì (theo giả thuyết)

### H1 — đọc đúng thứ đang hiện (nguồn của ~96 % mức giảm)

Bốn tầng, mỗi tầng chữa một nguyên nhân khác nhau:

1. **`CarDataDemand` (:core, thuần)** — tính tập datum màn hình thật sự bày ra từ `HomeUiState` (chip thanh
   trên + widget trong ô + nhóm khả năng + ô đọc trên thanh nút). Datum ngoài tập ⇒ không đọc, **giữ giá trị
   cũ**. Mã lạ ⇒ trả `null` = *đọc hết* (fail-open: thà hụt hiệu năng còn hơn một ô câm).
2. **Cổng nhịp NHANH** — `CarStatusReader.fastNeeded()`. Màn mặc định không bày datum nhanh nào ⇒ vòng 1 Hz
   (18 datum, **77 %** toàn bộ tải) **không chạy**, lùi về hỏi lại mỗi 10 s nên tự sống lại khi cần.
3. **`HalAbsentCache` (:core, thuần)** — 3 lần `null` liên tiếp ⇒ nguội, thử lại giãn dần 60 s → ×2 → trần 10
   phút; đọc ra giá trị ⇒ quên sạch. Cố ý **không** cấm vĩnh viễn: `null` không chứng minh *"xe không có"*, nó
   cũng có thể là *"chưa có lúc này"* (ETA sạc khi chưa cắm sạc).
4. **Nhớ tay cầm device (`BydHalGateway`)** — 1.66 chạy trọn `systemBypassContext()` + `bypass(app)` +
   `Class.forName().getMethod().invoke()` cho **mỗi** lượt đọc ⇒ ≈4 200 lượt tra reflection + ≈2 800 vật tạm
   mỗi phút. Nay nhớ tay cầm; lần **hụt** chỉ nhớ 30 s (HAL có thể lên muộn lúc nổ máy).

### H2 — gộp lệnh shell của watchdog cast

[ĐO] bác một phần giả thuyết ban đầu: watchdog `repinEscapedCastApps` **đã** thoát sớm khi không cast, nên
"13 lệnh/phút dù không cast" là **sai** — 47 phút log ấy là một phiên ĐANG cast. Cái thật sự lãng phí là:
mỗi nhịp chạy `dumpsys display` (dò lại id display cụm) **cộng** một `am stack list` **cho từng gói**.
Nay: lượt ĐỌC dùng id đã xác minh + **một** `am stack list` chia cho mọi gói; lượt ĐẶT vẫn dò TƯƠI ngay trước
khi đặt (bất biến R1 không đổi một dòng). Nhịp 4 s **giữ nguyên** — xem §5.

### H3 — ghi log theo lô, giữ nguyên dòng quan trọng

`flush()` mỗi dòng → xả **ngay** với W/E/F/A, còn D/I xả theo thời gian (≤2 s). Cửa sổ mất mát khi app chết
đột ngột: ≤2 s dòng D/I; cảnh báo/lỗi **không bao giờ** mất.
⚠ Owner muốn **giữ** log suốt phiên ⇒ không lọc tag; mức giảm KB/phút đến từ việc H1 cắt **nguồn ồn** (log HAL
do chính vòng poll sinh ra), đúng yêu cầu *"giảm NGUỒN ồn, không tắt log"*.

### H5 — không vẽ lại chip khi chữ không đổi

`TextView.setText` với chính chuỗi đang hiện vẫn dựng lại `Layout` + `requestLayout()`. Nay so chuỗi trước.

### H6 — nạp sẵn mô hình nghe hỏi RAM trước

`VoicePreloadPolicy` (thuần): bỏ qua nạp sẵn khi `lowMemory` hoặc `availMem < cỡ gói + 96 MB`. Ngưỡng theo
**cỡ gói đang chọn** chứ không phải một số cứng — gói int8 (74 MB) nạp được trong 180 MB trống, gói fp32
(266 MB) thì không; một ngưỡng cứng "còn <200 MB" sẽ chặn nhầm cả hai. Đây là quyết định **hoãn**, không phải
tắt: lần bấm mic đầu vẫn nạp như 1.65.

---

## 4. [ĐO] bác bỏ / sửa lại giả thuyết ban đầu

| Giả thuyết trong brief | Kết quả [ĐO] |
|---|---|
| *"2 poller cùng gọi `am stack list` ~4–5 s/lần **dù không cast**"* | **SAI**. `repinEscapedCastApps` thoát sớm khi state không phải Casting; `SlotLiveProbe` chỉ chạy khi có ô App **và** màn chính đang hiện. Máy ảo idle đo được **0,0 lệnh shell/phút**. 13 lệnh/phút của xe là của một phiên ĐANG cast. |
| *"mục tiêu < 4 lệnh shell/phút idle"* | Đã là **0** từ trước. Mục tiêu đúng phải là "khi đang cast", và ở đó mức giảm là ≈13 → ≈6,5/phút. |
| *"1.66 có cache denied chưa?"* | **Có nhưng chỉ cho đường GHI** (`BydHal.rejectedFeatures`, TASK 5). Đường **ĐỌC** hoàn toàn không có ⇒ đúng là nguồn của 10 804 dòng "no permission". |
| *"đọc kiểu (type) đúng ngay lần đầu thay vì thử 4 kiểu"* | **Không tái lập được**: `BydHal.readFeature` chỉ thử **một** kiểu (`Integer.TYPE`). 4 dạng dòng "no permission" khác nhau trong log là 4 **feature-id** khác nhau, không phải 4 kiểu của một id. |
| *"nhịp poll phải dừng khi `onStop`"* | **Đã đúng từ trước**: `collectHome` bọc trong `repeatOnLifecycle(STARTED)`. |

---

## 4.1 [ĐO] `sweep` vẫn đọc ĐỦ 123 datum sau bản vá

Yêu cầu: cổng H1 **không được** chạm vào lệnh chủ động. Kiểm bằng máy, không bằng đọc code:

```
adb shell am broadcast -a com.byd.launcher.TEST --es cmd sweep --es op info \
  -n com.byd.launcher/com.byd.clusternav.launcher.testbridge.KachiTestBridge
→ {"ok":true,"cmd":"sweep","ms":21,…,"info_total":123,"info_read":0,…}
```

`info_total: 123` = quét trọn bảng. (`info_read: 0` vì máy ảo không có HAL — đúng như mong đợi.)
Chính bộ đếm cũng thấy lượt quét đó: cửa sổ chứa `sweep` báo **`HAL đọc=156/phút`** (48 của vòng poll + ~108
của `sweep`), rồi trở lại **48** ở cửa sổ kế ⇒ lệnh chủ động đi qua HAL THẬT, không bị cổng nào chặn.
Lý do cơ chế: `TestBridgeSweep` **tự dựng** `HalBindingTable(BydHalGateway(app))` và duyệt
`TelemetryRegistry.ALL`, tức nó không đi qua `CarDataAdapter` ⇒ không có cổng nào để quên tắt. **[ĐO]**

---

## 5. Cố ý KHÔNG làm (và vì sao)

- **Nhịp watchdog cast 4 s → 15 s**: đây là thời gian phục hồi khi một app bị kéo khỏi cụm **lúc đang lái**
  (cần 2 nhịp liên tiếp thấy vắng ⇒ 8 s hiện tại, 30 s nếu đổi). Đổi độ trễ phục hồi của một hành vi an toàn
  là **quyết định của owner**, không phải của một vòng tối ưu (CLAUDE.md §6 + `trace-den-tan-cung.md` §2).
  ⇒ đưa vào backlog, chờ owner chốt.
- **H4 (đổi mô hình fp32 → int8 cho máy đã cài)**: 1.66 đã đặt int8 làm mặc định cho máy MỚI; đổi mô hình của
  người đang dùng là thay đổi dữ liệu người dùng ⇒ phải hỏi. Hàng Cài đặt + gợi ý tự động khi RAM thấp = việc
  UI riêng, chưa làm trong vòng này. H6 đã chặn được phần tệ nhất (nạp thêm 266 MB khi máy còn 60 MB).
- **Tách `SimpleCastCoordinator.kt`** (764 dòng > trần 500 của CLAUDE.md §4.1): **nợ có từ trước** vòng này
  (750 dòng ở 1.66). Tách nó là đụng vào đường cast đang chạy tốt ngoài hiện trường ⇒ việc riêng, có spec riêng.

---

## 6. Playbook đo lại TRÊN XE (🚗 NEEDS-ONCAR)

Bộ đếm `KachiPerf` ship sẵn nên không cần công cụ ngoài. Mở màn chính Kachi, để yên 5 phút, rồi:

```bash
# 1) Tải HAL/shell/log — một dòng mỗi phút, đọc thẳng
adb logcat -d -v time -s KachiPerf | tail -10

# 2) Số dòng HAL thô + "no permission" trong log app tự ghi (so với §0)
adb pull /sdcard/Android/data/com.byd.launcher/files/kachi-logs/ ./kachi-logs/
grep -c "D/BYDAuto"                     kachi-logs/usage-*.log
grep -c "no permission to use the"      kachi-logs/usage-*.log

# 3) CPU lúc đứng yên + RSS
adb shell top -n 3 -d 10 | grep byd.launcher
adb shell dumpsys meminfo com.byd.launcher | grep -E "TOTAL PSS|Native Heap"

# 4) Lý do bỏ nạp sẵn mô hình (H6)
adb logcat -d -v time -s KachiVoiceRec | grep "nạp sẵn"
```

**Tiêu chí đạt**: `HAL đọc` < 150/phút ở màn mặc định · `no permission` < 20/phút · CPU idle < 2 % (8 lõi) ·
log < 20 KB/phút · mọi ô đang hiện vẫn có số (KHÔNG có ô nào mới thành "—").

⚠ **Phải kiểm bằng mắt**: mở lần lượt ô *Tốc độ* (bật lại vòng nhanh), ô *Áp suất lốp*, nhóm *Kính*, và một
chip bất kỳ trong Cài đặt → thanh trên; mỗi cái phải ra số trong vòng ≤ 10 s sau khi đặt lên màn.

---

## 7. CHƯA đo được (và vì sao)

- **Giá mỗi lượt đọc HAL trên xe sau bản vá** — máy ảo không có HAL BYDAuto; con số 23 ms/lượt ở §0.1 là suy
  từ log 1.66, chưa có log 1.67. **[CHƯA BIẾT]**
- **Jank (`gfxinfo`)** — máy ảo đứng yên chỉ vẽ 19–26 khung trong 150–210 s, nên `Janky frames %` (68 %/81 %)
  là **nhiễu thống kê trên mẫu quá nhỏ**, KHÔNG so sánh được. Muốn có số thật phải cuộn/chạm trên xe.
- **Method trace** (`am profile start`) — không chạy: máy ảo sau bản vá gần như **không còn việc** ở trạng thái
  đứng yên (0 lượt đọc HAL/phút), nên một bản trace idle sẽ chỉ toàn `epoll_wait`. Trace chỉ có nghĩa khi
  chụp trên xe lúc đang lái. **[CHƯA BIẾT]**
- **Lỗ 3,1 s của đường giọng nói sau giải mã** — ngoài phạm vi vòng này (nằm ở `KachiVoiceTiming`, cần xe).
- **Nhánh BỎ QUA của H6** — máy ảo có thừa RAM nên `VoicePreloadPolicy.shouldPreload` luôn trả `true`; nhánh
  *"bỏ qua vì thiếu RAM"* chỉ chạy được trên xe. Khoá bằng test thuần (`VoicePreloadPolicyTest`, 5 bài dựng đúng
  con số 60 MB / 266 MB của xe), **chưa** [ĐO] trên máy thật.
- **PSS với mô hình int8 vs fp32** — máy ảo đang cài gói fp32; đổi gói là việc của H4.

---

## 8. Tệp đã đổi

`:core` — `KachiPerf.kt` (mới) · `CarDataDemand.kt` (mới) · `HalAbsentCache.kt` (mới) ·
`voice/VoicePreloadPolicy.kt` (mới) · `CarDataAdapter.kt` · `CarStatusRepository.kt` ·
`modules/clustercast/simplified/SimpleCastCoordinator.kt`

`:app` — `AppContainer.kt` · `launcher/BydHalGateway.kt` · `launcher/KachiHomeActivity.kt` ·
`launcher/KachiHomeWiring.kt` · `launcher/KachiLog.kt` · `launcher/KachiTopStrip.kt` ·
`launcher/voice/VoiceRecognizer.kt` · `system/ShellTransport.kt` · `build.gradle.kts` (1.67/68)

Test mới — `:core` `CarDataDemandTest` · `HalAbsentCacheTest` · `KachiPerfTest` ·
`voice/VoicePreloadPolicyTest` · (+1 bài trong `CarStatusRepositoryTest`) · `:app` `KachiLogFlushTest`

**Thêm ở senior review Pass 1 (§9)** — `:app` `launcher/voice/VoiceWiring.kt` · `launcher/VoiceDispatcher.kt`;
test mới `:app` `CarDataDemandRendererContractTest` · (+1 bài `CarDataDemandTest`, +1 bài `CarStatusRepositoryTest`).

---

## 9. Senior review Pass 1 — 2026-09-16 (Opus)

Chi tiết từng phát hiện + lý lẽ nằm ở `docs/specs/kachi-perf-2026-09.html` §10. Phần đáng ghi lại **ở đây** là
những chỗ nó làm đổi số đo hoặc đổi danh sách việc cần xe.

### 9.1 Phát hiện đắt nhất: câu hỏi bằng giọng đọc số ĐÓNG BĂNG **[P1]**

§4.3 của spec (và KDoc `CarDataDemand`) khẳng định đường giọng nói đi qua `AppContainer.telemetryText` nên
"tự nhiên nằm ngoài cổng". **Sai, đã kiểm**: `VoiceDispatcher.runRead` đọc
`TelemetryReadout.of(id, state().carStatus)` — tức chính **ảnh chụp của vòng poll**, thứ mà cổng H1 vừa lọc;
`telemetryText` chỉ có đúng một chỗ gọi là màn *Kiểm tra từng nút*.

Hậu quả nếu ship nguyên: hỏi một datum **không có trên màn** (*"tốc độ bao nhiêu"* khi màn chỉ bày pin·bụi·
nhiệt độ) thì `CarDataAdapter` đã *giữ giá trị cũ* cho nó ⇒ Kachi đọc to con số của lần cuối ô Tốc độ còn trên
màn. Một cổng **hiệu năng** đẻ ra một lỗi **chức năng** — đúng thứ §4.4 của spec nói phải tránh.

Đã vá: đường giọng nói **ghim** datum vào nhu cầu rồi đọc ngay một lượt (`Holder.withExtra` +
`CarStatusRepository.refreshNow`). Ghim là **hợp** chứ không **thay**, và `AppContainer.refreshForRead` tự trả
`null` khi ảnh chụp vốn đã tươi ⇒ phần việc thêm có trần cứng là **một** datum.

**[ĐO máy ảo]** bốn ca `Read` của E2E (t33–t36) trả đúng *"chưa đọc được"*, độ trễ **357–361 ms** — không đo
được chi phí thêm nào. Trên xe thì mỗi lượt đọc HAL ≈23 ms (§0.1) ⇒ **[SUY]** thêm ≈25–100 ms cho một câu hỏi
về datum ngoài màn, trên một đường mà người dùng vừa chờ vài giây để nói xong. Cần xác nhận bằng mắt/đồng hồ
trên xe (xem 9.3).

### 9.2 Ba phát hiện còn lại

| # | Phát hiện | Đã làm |
|---|---|---|
| **[P1-2]** | Bảng `CURATED` chép tay không có bài nào canh nó khỏi trôi khỏi bộ vẽ | Thêm `CarDataDemandRendererContractTest` (`:app`): suy `field → mã` từ `TelemetryReadout`, đi theo từng nhánh `"w_*"` của `build`/`mini` vào các hàm phụ. **Đã thử làm ĐỎ.** Bài chỉ ra `w_board` chuyền **cả cụm** `CarStatus.Tyres` ⇒ thêm 4 mã `tyre_t_*` vào bảng |
| **[P2-1]** | `HalAbsentCache.clear()` **không có chỗ gọi** (CLAUDE.md §8); trần giãn nhịp 10 phút ⇒ datum vắng lâu rồi mới có (ETA sạc lúc vừa cắm) câm tới 10 phút | `AppContainer.forgetCarDemand()` gộp *quên nhu cầu* + *quên kết luận vắng mặt* vào MỘT chỗ gọi ở `onStop`. Không tốn thêm lượt đọc nào (lượt đầu sau khi mở lại vốn đã là *đọc hết*) |
| **[P2-2]** | Tay cầm device nhớ **vĩnh viễn** dựa trên tiền đề *"getInstance là singleton"* — mà tiền đề ấy chỉ đọc được **chữ ký** trong stub SDK, thân hàm là `throw new RuntimeException("Stub!")` ⇒ mức **[SUY]**, không phải [ĐO] | `HIT_TTL_MS = 5 phút` cho lần TRÚNG. Giá ≈**2,4 lượt reflection/phút** (12 device) so với ≈4 200/phút của 1.66 ⇒ dưới mức nhiễu, đổi lấy trần phục hồi hữu hạn nếu tiền đề sai |
| **[P2-3]** | Đua dữ liệu: `HalAbsentCache.shouldRead` đọc ngoài khoá với field thường (`nextTryAt` là `Long` ⇒ đọc rách được); bản vá [P1-1] tạo luồng thứ hai thật | `@Volatile` ba field + `computeIfAbsent` thay `getOrPut` |

### 9.3 Đổi với danh sách 🚗 NEEDS-ONCAR ở §6

Thêm **ba** mục vào playbook, tất cả đều là hệ quả của các bản vá trên:

```bash
# 5) [P1-1] Hỏi bằng giọng một datum KHÔNG có trên màn — phải ra số THẬT, không phải số cũ
#    Đặt màn về mặc định (pin · bụi · nhiệt độ ngoài), lái cho xe chạy, rồi hỏi "tốc độ bao nhiêu".
#    ĐẠT = con số khớp đồng hồ xe. HỎNG = một con số đứng im giữa hai lần hỏi ở hai tốc độ khác nhau.
adb logcat -d -v time -s KachiVoice | tail -20

# 6) [P2-2] HAL lên MUỘN lúc nổ máy — tay cầm device phải bắt được
#    Tắt máy hẳn, nổ lại, mở Kachi NGAY (trong ~5 s đầu). Mọi ô phải ra số trong vòng ≤ 1 phút.

# 7) [P2-1] Datum vắng rồi mới có — cắm sạc khi app ĐANG chạy, ô "ETA sạc" phải ra số ≤ 60 s
```

**Vì sao ba mục này chỉ đo được trên xe**: máy ảo không có HAL nên *mọi* datum đều `null` ⇒ ba nhánh phục hồi
ở trên (đọc tươi ra SỐ · HAL lên muộn · datum vắng rồi có) đều không tồn tại ở đó. Off-car chỉ khoá được bằng
test thuần, đã làm. **[CHƯA BIẾT] cho tới khi có lượt xe.**

### 9.4 Số sau review

`:core` **2 006** / 0 đỏ · `:app` **988** / 0 đỏ (đếm từ XML). E2E `voice-e2e.sh --only say` **T1 69/70** —
không đổi; ca đỏ duy nhất là `t29` (*"chạy gói mở cửa + đèn đọc"* ra `Unknown`), có từ trước và không liên quan
PERF. `sweep` vẫn `info_total=123`.

### 9.5 Còn nợ, cố ý không vá ở pass này

- **Đuôi log khi app chết đột ngột** — cửa sổ ≤2 s dòng D/I đã được §3 (H3) chấp nhận, nhưng luồng
  `KachiLogCapture` chết theo tiến trình nên không có móc xả nốt bộ đệm. Nhẹ trên thực tế (dòng ngay trước một
  cú sập gần như luôn ở mức E `AndroidRuntime` ⇒ xả ngay). Ghi ra để không ai tưởng đã chặn hết.
- **`KachiHomeActivity.kt` 532 dòng > trần 500** — nợ có từ trước, +5 dòng ở vòng này; tách trong một vòng tối
  ưu hiệu năng là đúng thứ CLAUDE.md §6 cấm.
