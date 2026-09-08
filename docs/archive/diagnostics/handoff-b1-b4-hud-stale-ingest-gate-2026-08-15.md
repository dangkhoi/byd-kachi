# HANDOFF — B1 (cụm ghim mũi tên cũ) + B4 (ingest chạy khi đã tắt công tắc) · 2026-08-15

> **Bản đang xét:** `versionCode 120 / 1.20`, HEAD `99dbcb2`. **CHƯA sửa dòng code nào** — đây là handoff, không phải patch.
> **Nguồn:** review đa vai 2026-08-15 → `docs/review/golive-readiness-2026-08-15.html`. B1 và B4 là 2 trong 4 khoản chặn
> nhóm **A′ (xe bạn bè)**; phán quyết chung là GO cho xe owner, chặn OTA sang xe người khác.
> **Nguyên tắc:** mọi dòng dưới đây đã tự đọc source verify lại tại HEAD. Chỗ chưa đo được thì ghi thẳng **CHƯA BIẾT**,
> không lấp bằng phỏng đoán (`.kiro/steering/no-assumptions.md`).
> **Cả hai sửa được OFF-CAR.** B1 có 1 câu hỏi cần đo trên xe nhưng câu đó **không chặn** việc sửa.

---

## 0. TL;DR

| | Vấn đề | Tệ nhất xảy ra gì | Effort |
|---|---|---|---|
| **B1** | Keep-alive HAL không có trần tuổi · đường disconnect không clear · nhịp tim chết sau tuyến 1 | Cụm ghim maneuver cũ vô hạn (mũi tên sai mà tự tin); và từ tuyến thứ 2 trở đi bug chớp ~1s của 1.15 quay lại | S |
| **B4** | `bridge.start()` + `startWazeHudSource()` chạy **trên** cổng master-switch | Tắt hết công tắc vẫn mở ~4.000 phiên dadb uid-2000/giờ trên đầu xe người khác | S |

> ⚠️ **ĐO ĐƯỢC HÔM NAY — LẬT NGƯỢC KHUYẾN NGHỊ BAN ĐẦU CỦA REVIEW.**
> Review đề xuất trần tuổi **15–20 s**. Đo trên log lái thật 62 phút: **~30 lần** khoảng cách giữa 2 lần GMaps đổi cự ly
> vượt 15 s, tập trung đúng lúc **bò trong hầm / kẹt xe**. Trần 15–20 s sẽ làm cụm **trắng ~30 lần một chuyến**, đúng
> lúc người lái cần nhất. **Đừng làm theo con số đó.** Chi tiết §1.2.

---

## 1. B1 — cụm có thể ghim frame cũ, và nhịp tim chết sau tuyến đầu

### 1.1 Ba lỗ ĐỘC LẬP, không phải một

**Lỗ 1 — policy không có trần tuổi.** `core/src/main/kotlin/com/byd/clusternav/navigation/HudKeepAlivePolicy.kt:39-41`

```kotlin
fun shouldReassert(nowMs: Long): Boolean = synchronized(lock) {
    hasFrame && nowMs - lastWriteAtMs >= intervalMs      // ← không có số hạng nào chặn trên
}
```

Class chỉ có 3 field: `lock`, `lastWriteAtMs`, `hasFrame` (`:22-24`). Và `app/src/main/java/com/byd/clusternav/NavigationHudOwner.kt:93`
gọi `keepAlive.onFrameWritten(...)` sau **mọi** lần ghi HAL thành công — kể cả lần ghi do chính keep-alive tạo ra ở
`:117-119` (`keepAliveTick` → `resubmitApplied`). ⇒ nhịp 400 ms **tự làm tươi đồng hồ của chính nó**, chạy vô hạn.

**Lỗ 2 — đường disconnect không clear.** `app/src/main/java/com/byd/clusternav/NavNotificationListener.kt:61-74`

KDoc của chính hàm ghi *"Hệ thống THẢ binding (head-unit hay làm lúc chạy)"*. Hàm clear typed source, gỡ listener,
`setPermission(UNKNOWN)`, `requestRebind` — **không hề gọi `NavRepository.stop`** ⇒ `hudOwner.stop()` không bao giờ tới.
So sánh: nhánh `onNotificationRemoved` (`:224-227`) làm **đúng** — `arrivalGuard.reset()` + `NavRepository.stop()` +
`ClusterNavLaneWidget.onNavIdle()`. Mẫu cần copy nằm ngay trong cùng file.

**Lỗ 3 — nhịp tim chết sau tuyến đầu (cái này ngược lại: làm bug 1.15 QUAY VỀ).**

`NavigationHudOwner.start()` có **đúng 2 call site**, cả hai đều không theo tuyến:
- `NavRepository.kt:111` — `createCoordinator`, chạy **một lần mỗi process**
- `NavRepository.kt:82` — nhánh `setOutputEnabled(CLUSTER_LANE, true)`

Mà `setOutputEnabled(CLUSTER_LANE, true)` chỉ được gọi từ `NavRepository.kt:44` (connect lần đầu), `MainActivity.kt:74`,
`MainActivity.kt:107` (mở app) và `BootSetupService.kt:64` (boot) — **không có đường nào theo từng tuyến**.

Trong khi `NavRepository.stop()` → `hudOwner?.stop()` (`:89`) → huỷ `keepAliveTask` (`NavigationHudOwner.kt:138`) thì
**được gọi theo từng tuyến**: `NavNotificationListener.kt:225` (gỡ noti), `:248` (đã đến nơi), `:293` (route-end).

⇒ **Tuyến 1 kết thúc = nhịp tim bị huỷ vĩnh viễn** cho tới khi mở lại app hoặc reboot. Tuyến 2 trong cùng chuyến vẫn ghi
frame bình thường (worker được bật lại trong `stop()`) nhưng **không còn nhịp tim** ⇒ đúng triệu chứng OEM chớp ~1 s trên
đoạn dài không rẽ mà 1.15 sinh ra để chữa.

### 1.2 ĐO — GMaps giãn notification tới đâu (đây là phần quan trọng nhất của handoff này)

Nguồn: `docs/diagnostics/nav-logs/commute-2026-08-14-pm.csv` — chuyến lái thật **62,4 phút**, 7.927 dòng.
Một "real push" = `rawGmaps_m` đổi giá trị (= GMaps đẩy noti mới).

Lệnh tái lập:

```bash
python3 - <<'PY'
import csv
rows=list(csv.DictReader(open('docs/diagnostics/nav-logs/commute-2026-08-14-pm.csv',encoding='utf-8')))
gaps=[];prev=None;pt=None
for r in rows:
    t=float(r['t_ms'])
    if r['rawGmaps_m']!=prev:
        if pt is not None: gaps.append(t-pt)
        prev=r['rawGmaps_m'];pt=t
gaps.sort()
for thr in (5,10,15,20,30,60,90,120,180):
    print(thr,'s:',sum(1 for g in gaps if g>thr*1000))
PY
```

Kết quả (n=390 lần đổi):

| Khoảng cách giữa 2 noti | Số lần | % |
|---|---|---|
| p50 | 2,9 s | |
| p90 | 13,0 s | |
| p99 | **60,0 s** | |
| > 15 s | **32** | 8,2 % |
| > 20 s | 22 | 5,6 % |
| > 30 s | 9 | 2,3 % |
| > 60 s | 3 | 0,8 % |
| > 120 s | 1 | 0,26 % |
| > 180 s | 1 | 0,26 % |

**Bối cảnh 4 khoảng dài nhất khi đang lăn bánh — đọc kỹ chỗ này:**

```
108,0 s | speed 2,8 m/s | raw 1200 -> 1100 | road='Hầm Nguyễn Hữu Cảnh'
 77,0 s | speed 2,2 m/s | raw 1100 -> 1000 | road='Hầm Nguyễn Hữu Cảnh'
 60,0 s | speed 3,1 m/s | raw 2200 -> 2100 | road='Nguyễn Hữu Cảnh'
 42,0 s | speed 1,4 m/s | raw 1300 -> 1200 | road='Hầm Nguyễn Hữu Cảnh'
```

Cơ chế đã rõ: GMaps chỉ đẩy noti khi **đổi bậc 100 m**. Bò 2,8 m/s thì 100 m mất ~36 s. ⇒ **khoảng cách noti dài nhất
xuất hiện đúng lúc kẹt xe / bò trong hầm** — chính là lúc tuyệt đối không được để cụm trắng.

Khoảng 1236,8 s (20,6 phút) là lúc **đỗ** (`speed 0,0`, `raw 0 -> -1`) — kết thúc phiên, không phải khoảng nav thật.

**Kết luận thiết kế rút ra từ số đo:**
1. Trần tuổi **15–20 s là SAI** — sẽ trắng cụm ~30 lần/chuyến, dồn vào lúc kẹt xe.
2. Timeout **không phải** tín hiệu tốt để phân biệt "nguồn chết" với "nguồn chậm" — kẹt xe hợp lệ tạo ra khoảng 108 s.
3. ⇒ **Tín hiệu chính phải là tín hiệu DƯƠNG** (nguồn báo hết: disconnect / gỡ noti / arrival) = **lỗ 2**.
   Timeout chỉ là **lưới đỡ cuối** (backstop), đặt **cao hơn** mọi khoảng thật đã quan sát.
4. Số đề xuất: **180 s**, khớp `STALE_MS = 180_000L` mà làn cụm đã dùng (`ClusterBroadcaster.kt:24`) — một hằng số,
   một ngữ nghĩa, và theo dữ liệu này chỉ có **1 khoảng duy nhất trong 62 phút** vượt qua, mà đó là lúc đã đỗ.
   *(Đây là đề xuất dựa trên 1 chuyến. Nếu muốn chắc hơn: chạy lại lệnh trên với CSV của chuyến kế — xem §4.)*

### 1.3 Cần sửa gì

**`HudKeepAlivePolicy.kt`** — tách "lần ghi" khỏi "lần đẩy thật":
- thêm `lastRealPushAtMs`, chỉ real push mới làm tươi; re-assert **không** được làm tươi
  (thêm tham số `realPush: Boolean = true` cho `onFrameWritten`, hoặc tách hàm `onReasserted(nowMs)` — chọn cái đọc
  tự nhiên hơn cạnh code hiện có)
- `shouldReassert(now)` = `hasFrame && sinceLastWrite >= intervalMs && sinceRealPush <= maxAgeMs`
- thêm `shouldClear(now)` = `hasFrame && sinceRealPush > maxAgeMs`
- KDoc phải ghi **con số + lý do** (§1.2), bằng giọng Việt của file hiện tại. Đây là chỗ người sau sẽ đọc để không
  siết nhầm ngưỡng lần nữa.

**`NavigationHudOwner.kt`**
- `keepAliveTick` (`:117`): gặp `shouldClear` → phát CLEAR **đúng một lần** qua worker (dùng lại nhánh `isClear` của
  delivery lambda ở `:62-70`, **đừng** gọi thẳng `BydHal` từ thread tick), reset dedup, `keepAlive.onCleared()`
- **lỗ 3**: re-arm nhịp tim từ đường delivery. `start()` đã idempotent sẵn nhờ guard `if (keepAliveTask == null)`
  (`:107`) nên gọi lại an toàn.

**`NavNotificationListener.kt:61-74`** — thêm `NavRepository.stop(applicationContext)` +
`ClusterNavLaneWidget.onNavIdle()` vào `onListenerDisconnected`, theo đúng shape/`runCatching`/log của `:224-227`.

### 1.4 Test (rule §10)

⚠️ **`HudKeepAlivePolicyTest` hiện đang KHOÁ CHÍNH HÀNH VI SAI** — phải sửa có chủ đích, không xoá:
- `assertTrue(p.shouldReassert(5_000L))  // stale lâu`
- case comment `"real push (hoặc re-assert) làm tươi lại"`

Thêm:
- sau N interval chỉ có re-assert, không real push → `shouldReassert` false, `shouldClear` true
- một real push mở lại cửa sổ
- **case hồi quy từ số đo thật**: real push, im 108 s (kẹt xe trong hầm) → vẫn `shouldReassert`, **KHÔNG** clear
- `onCleared()` vẫn dừng tất cả
- contract test: `onListenerDisconnected` có gọi `NavRepository.stop`

`./gradlew --offline :core:test --tests '*HudKeepAlive*'` rồi `:core:test` + `:app:testDebugUnitTest` đầy đủ.

### 1.5 CHƯA BIẾT — cần đo, nhưng không chặn việc sửa

- **Head-unit thả binding bao lâu một lần khi đang chạy?** Code khẳng định trong comment (*"head-unit hay làm lúc chạy"*)
  nhưng **không có phép đo nào trong repo**. Bán kính ảnh hưởng của B1 phụ thuộc hoàn toàn vào con số này.
- **Cụm OEM tự tắt sau bao lâu khi ngừng nhận frame?** Có ghi chú cũ nói ~1 s, từ thời 1.14, **chưa đo lại**. Đây là
  ranh giới giữa "CLEAR bị nuốt chỉ là lỗi thẩm mỹ" và "mũi tên cũ bị ghim thật".
- **Clear ngay khi disconnect có gây chớp không?** Nếu binding rớt rồi lên lại sau vài giây giữa tuyến, clear-rồi-vẽ-lại
  có thể khó chịu hơn giữ frame. Đường rebind có quét lại `activeNotifications` (`:90-96`) nên hồi phục nhanh — nhưng
  **chưa đo**. Người làm phải chọn và ghi lý do vào KDoc.

---

## 2. B4 — ingest khởi động TRÊN cổng master-switch

### 2.1 Cơ chế (đã chứng minh)

`app/src/main/java/com/byd/clusternav/NavNotificationListener.kt:77-85`

```kotlin
override fun onListenerConnected() {
    connected = true
    speedSignOwner.syncFromPrefs()
    val bridge = VietMapWidgetBridge.get(applicationContext)
    bridge.start(VietMapWidgetOwner.NAVIGATION)       // :81  ← chạy TRƯỚC cổng
    bridge.addListener(speedLimitPusher)              // :82  ← chạy TRƯỚC cổng
    startWazeHudSource()                              // :84  ← chạy TRƯỚC cổng
    if (!Prefs.enabled(applicationContext)) return    // :85  ← cổng nằm SAU
```

Mẫu **đúng** nằm ngay trong cùng file: `onNotificationPosted` kiểm `Prefs.enabled` **trước** `ensureBridgeStarted`.

`startWazeHudSource` (`:125-126`) chỉ có một guard duy nhất `if (wazeHudSource != null) return` — **không** kiểm
`Prefs.sourceMode`, **không** kiểm WazeMod có cài hay không.

### 2.2 Chi phí thật (đã chứng minh)

- `modules/wazehud/WazeHudSource.kt:41` — `POLL_MS = 900L`
- `:43` — `DUMP_CMD = "logcat -d -v raw -s WazeHUD:V -t $TAIL_LINES"`
- `:46` — `GRANT_CMD = "pm grant com.byd.clusternav android.permission.READ_LOGS"`
- Đường thi hành: `NavNotificationListener.kt:131-132` → `SimpleCastRuntime.coordinator(...).executeShell(cmd)`
- `modules/clustercast/simplified/SimpleCastRuntime.kt:66` (KDoc của chính nó): **"Each shell command opens a fresh
  dadb session"**, `:75` `Dadb.create("localhost", 5555, keyPair)`

⇒ **3600 / 0,9 = 4.000 lần/giờ**: TCP connect + ADB auth + fork shell uid-2000 + 1 dòng logcat của chính mình.
Cộng `VietMapWidgetBridge` tick 1 Hz trên main thread.

Người dùng cấp quyền notification rồi để **cả hai công tắc TẮT** — đúng như `docs/HUONG-DAN.md` hứa (*"mở app lên không
tự đụng gì vào xe"*) — vẫn phải trả toàn bộ chi phí này, trên đầu xe Android 10 mà CPU/RAM thừa là phần launcher OEM và
CarPlay/AA cần. Không nguy hiểm cho người lái, nhưng là tiêu thụ **liên tục, vô hình, không được đồng ý** trên phần cứng
của người khác — và là giải thích cơ học hợp lý nhất hiện có cho *"đầu xe cài ClusterNav vào thấy ì dần"*.

### 2.3 Phát hiện kèm — nửa speed-limit hiện đi vào sink RỖNG

`app/src/main/java/com/byd/clusternav/NavigationSpeedSignOwner.kt:21-22`

```kotlin
clusterPort = NoopSpeedSignPort(SpeedSignOutput.CLUSTER),
hudPort     = NoopSpeedSignPort(SpeedSignOutput.HUD),
```

`core/.../navigation/SpeedSignPorts.kt:58` — KDoc: *"accepts typed lifecycle frames, fences generations, and **performs
no I/O**"*. `publish`/`replaceWithClear` chỉ so `generation` rồi trả `ACCEPTED`.

⇒ Ở bản 1.20, dữ liệu speed-limit từ VietMap/Waze **không thể tới người lái**. Đây không phải bug do B4, nhưng nó có nghĩa
là toàn bộ chi phí ở §2.2 hiện đang trả cho một đường **không có đầu ra**.
**KHÔNG xoá tính năng** — chỉ ghi nhận, và nó làm việc gate ở §2.4 trở nên hiển nhiên đúng.

### 2.4 Cần sửa gì

1. Đưa `if (!Prefs.enabled(applicationContext)) return` (`:85`) lên **trên** khối `:80-84`.
2. Gate từng producer theo nguồn đang chọn, dùng đúng accessor có thật (`Prefs.kt`):
   - `startWazeHudSource()` ← `Prefs.sourceMode(ctx)` ∈ {`AUTO`, `PREFER_WAZE`} (hằng số ở `Prefs.kt:10-13`)
   - `bridge.start(...)` / `addListener(speedLimitPusher)` ← `Prefs.speedLimitSource(ctx)` (`Prefs.kt:36`)
3. Giữ nguyên vị trí tương đối của phần sau cổng (`SourceArbiter.clear`, `setPermission`, quét `activeNotifications`).
4. **Cân nhắc** (ghi lại quyết định, đừng làm lặng lẽ): nâng `POLL_MS`, hoặc giữ **một** phiên dadb dài cho poller thay vì
   `Dadb.create` mỗi lệnh.

### 2.5 Test

Contract test kiểu `app/src/test/java/com/byd/clusternav/NavCastUiWiringContractTest.kt` (đọc file đó, theo đúng idiom):
khoá việc cổng `Prefs.enabled` **đứng trước** `bridge.start` / `startWazeHudSource` trong `onListenerConnected`.
Loại test đọc-source này nói chung là yếu, nhưng đây **đúng là ca nó có giá trị**: lỗi thứ tự, refactor sẽ tái sinh.

`./gradlew --offline :app:testDebugUnitTest`

---

## 3. Thứ tự làm + rủi ro

| Thứ tự | Việc | Rủi ro | Vì sao thứ tự này |
|---|---|---|---|
| 1 | **B4** | Thấp — di chuyển 1 dòng + 2 điều kiện | Không đụng đường vẽ cụm. Làm trước để tách bạch: nếu cụm có đổi hành vi thì chắc chắn do B1. |
| 2 | **B1 lỗ 2** (disconnect → stop) | Thấp-vừa — có thể gây chớp khi rebind (§1.5) | Tín hiệu dương, đúng gốc vấn đề |
| 3 | **B1 lỗ 3** (re-arm nhịp tim) | Thấp | Sửa hồi quy đang tồn tại, độc lập |
| 4 | **B1 lỗ 1** (trần tuổi) | **Cao nhất** — siết nhầm là trắng cụm lúc kẹt xe | Làm cuối, sau khi đã có tín hiệu dương ở bước 2 thì trần chỉ còn là lưới đỡ |

**Rủi ro lớn nhất của cả gói:** đặt `maxAgeMs` quá chặt. §1.2 đã cho số. Nếu phân vân → chọn cao hơn.
Một cụm giữ frame hơi lâu còn cứu được bằng tín hiệu dương ở bước 2; một cụm trắng giữa hầm thì không.

**Không đụng vào:** bản thân nhịp tim 400 ms (chỗ chớp ~1 s của OEM là triệu chứng đã xác nhận trên xe),
`NavigationHudOwner` dedup, `AmapEmissionArbiter`.

---

## 4. Đo trên xe (đỗ, số P, phanh tay — gộp 1 lượt)

```bash
export VEH=<vehicle-ip>:5555   # HỎI LẠI IP, đừng đoán
ADB="$HOME/Library/Android/sdk/platform-tools/adb"; "$ADB" connect "$VEH"
```

- **P-A — cụm OEM tự tắt sau bao lâu?** (chốt §1.5, quyết định CLEAR là thẩm mỹ hay thật)
  Đang dẫn + cụm hiện "Giữa + ETA" → `"$ADB" -s "$VEH" shell am force-stop com.byd.clusternav` → **bấm đồng hồ**: bao nhiêu
  giây thì overlay giữa cụm biến mất? Hay nó **không** biến mất?
- **P-B — tần suất thả binding** (chốt bán kính B1). Chạy suốt một chuyến:
  `"$ADB" -s "$VEH" logcat -v time -s NavListener:I NavRebind:I | grep -E 'onListenerDisconnected|rebind trigger'`
  → đếm số sự kiện/giờ. **Chưa ai từng đo con số này.**
- **P-C — lấy thêm CSV để xác nhận ngưỡng 180 s** (§1.2 mới dựa trên 1 chuyến):
  `"$ADB" -s "$VEH" pull /sdcard/Android/data/com.byd.clusternav/files/ ./carlog-2026-08-15/`
  rồi chạy lại lệnh python ở §1.2 trên CSV mới. Nếu chuyến nào có khoảng > 180 s **khi đang lăn bánh** → nâng ngưỡng.

---

## 5. Tham chiếu

- Báo cáo review đầy đủ: `docs/review/golive-readiness-2026-08-15.html` (§2 Khoản chặn, §6 Chưa chốt được off-car)
- Log lái dùng để đo: `docs/diagnostics/nav-logs/commute-2026-08-14-pm.csv`
- Bối cảnh nhịp tim ra đời: `docs/specs/hud-keepalive-interp-log-1.15.html`
- Rule liên quan: `CLAUDE.md` §2 (mức bằng chứng), §5 (state ngoài process), §8 (call site), §10 (test hồi quy)

---

## 6. Trạng thái khi bàn giao

- **Chưa sửa dòng code nào.** `git status` sạch phần code; file duy nhất mới thêm bởi phiên review là
  `docs/review/golive-readiness-2026-08-15.html` và chính file này.
- Suite off-car tại HEAD: core 782 ✓ · app 754 ✓ · car-integration 28 ✓ · vehicle-contracts 22 ✓ ·
  **offcar-planner 99 / 1 FAILED** (`ExpansionTransportFenceTest`, hash pin cũ từ 1.12 — **không liên quan B1/B4**,
  xem C10 trong báo cáo review).
