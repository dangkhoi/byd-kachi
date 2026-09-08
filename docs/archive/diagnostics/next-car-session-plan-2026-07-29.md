# Kế hoạch phiên xe kế tiếp — chuẩn bị off-car 2026-07-29

Viết trong lúc off-car (không có adb/dadb reachable tới xe), theo đúng kỷ luật mới ở
`CLAUDE.md` §14: tầng 1 (shell/adb thô trên xe thật) phải xanh TRƯỚC khi đụng tới
`car-integration`/`core`/UI. Tài liệu này chỉ là **kịch bản đo**, chưa có dòng code nào
được viết cho VietMap-nav hay AA dựa trên các giả thuyết bên dưới.

> **Cập nhật chiều 2026-07-29 (§5): làm §5.1 TRƯỚC.** Một kế hoạch mở cổng render nav
> (`docs/plans/cluster-nav-render-gate.html`, viết từ 2026-06-22, có live-confirm thật trên
> chính xe test) suýt bị bỏ quên vì nằm ngoài `ClusterNav/`. Rẻ nhất, chuẩn bị sẵn nhất, và có
> thể giải quyết cả câu hỏi VietMap-nav lẫn HUD trong một buổi.

## 0. Ground truth off-device đã xác nhận hôm nay

- `./gradlew --offline clean assembleRelease testDebugUnitTest test`: **BUILD SUCCESSFUL**,
  **772/772 test xanh** (core 461, car-integration 24, app 287) — không có regression từ các
  fix round-2 tối 2026-07-28. Con số 771-772 trước đó và 768 workflow báo đã đối chiếu xong:
  772 là số thật, khớp local.properties sdk.dir đã trả về path Windows sau build.
- `scripts/vehicle/carexec.sh steps` và `scripts/vehicle/carexec.sh scenarios` chạy được
  off-car (đọc-only, không cần `CAR_HOST`) — catalog + runner `:car-integration:run` đều
  sẵn sàng, không cần sửa gì trước khi lên xe.

## 1. Sửa lại hai kết luận sai của phiên trước (bắt buộc đọc trước khi làm tiếp)

### 1.1 Vụ view-ID "đã biết của VietMap" — ĐÃ ROOT-CAUSE, xác nhận lại hôm nay

`oncar-signals-vietmap-start-20260725T074727Z/candidates.txt` có **hai định dạng dump khác
hẳn nhau** trộn trong cùng một file:

- Đầu file (khối `[...][activity]`): định dạng `android.widget.TextView{... app:id/txt_lane_status}`
  — đây là output của `dumpsys activity top` (`listen-nav-signals.sh` dòng 214), and **các
  resource-id này thuộc `activity_main.xml` của chính ClusterNav** (verify lại hôm nay bằng
  grep trực tiếp: `txt_lane_status`, `cb_lane`, `btn_cast_details` đều có trong
  `app/src/main/res/layout/activity_main.xml`). Nghi là (chưa 100% chắc) ClusterNav's MainActivity
  debug screen đã là top-activity đúng lúc snapshot đó chạy.
- Từ dòng 119 trở đi (`uiautomator dump`, `listen-nav-signals.sh` dòng 234): `package="vn.vietmap.live"`
  thật, mọi node `resource-id=""` (VietMap không gắn resource-id), dữ liệu nằm hết trong
  `content-desc`. Đây MỚI là dữ liệu VietMap thật — đã re-verify hôm nay bằng grep trực tiếp
  vào file gốc.

**Kết luận: view-ID kiểu `txt_lane_status`/`cb_lane` KHÔNG PHẢI của VietMap — đó là artifact
tooling. Bỏ hẳn khỏi mọi thiết kế.**

### 1.2 Giả thuyết AA cần T3/⊞ escalation — user đã sửa thẳng, PHẢI BỎ

`ClusterCast.kt:273` (V1, read-only reference) ghi: *"`com.byd.androidauto` → ⊞ (t3Apps). Trên
xe AA chỉ lên được ở chế độ này."* Tôi đã từng suy luận từ đúng comment này rằng V2 có thể
thiếu cơ chế tương đương cho AA — **chưa hề chạy AA thật trên xe để kiểm chứng**. User sửa
thẳng: kết luận đó sai, AA và CarPlay đều từng lên bình thường (chế độ thường, không cần T3)
trước đây; nếu giờ có vấn đề thì đó là MỘT bug khác, không liên quan T3.

**Trạng thái đúng: chưa biết AA cast hiện có hoạt động không. Không được thiết kế/code dựa
trên giả thuyết T3. Phải đo lại từ đầu.**

### 1.3 Phát hiện chưa dùng: broadcast `NAVIGATION_STATE_CHANGED`

`byd.intent.action.NAVIGATION_STATE_CHANGED` — broadcast hệ thống chung (bool/int trạng thái
nav) gửi bởi cả `AAPService.sendBroadcastNavState` (Android Auto) và
`BinderCarplayServer.sendNaviBroadcast` (CarPlay). Hiện KHÔNG có consumer nào trong ClusterNav.
Có thể hữu ích làm tín hiệu "đang dẫn đường" độc lập với UI-scraping, nhưng đây vẫn chỉ là một
quan sát từ đọc source (V1/framework) — chưa có bằng chứng shell thật là broadcast này còn tồn
tại/còn bắn trên ROM hiện tại của xe test. Xếp vào diện "nghi là", cần `am broadcast` log thật
để lên mức "đã chứng minh".

## 2. Kịch bản đo AA cast (Task #3) — chạy khi có `CAR_HOST`

Mục tiêu: trả lời "AA cast qua luồng app bình thường hiện có chạy không, và nếu chạy thì task
landed ở windowingMode/bounds nào" — KHÔNG giả định T1/T3.

Component thật đã xác nhận từ fixture dump cũ: `com.byd.androidauto/.MainActivity`
(`StackParse.kt:21`) — dùng để lọc `am stack list`, không dùng để hardcode nhánh rẽ trong code
(V2 catalog dùng heuristic `PROJECTION_HINTS` chung, xem `CastAppCatalog.kt:235`).

```bash
export CAR_HOST=<ip-xe>     # hỏi dangkhoi lúc lên xe, KHÔNG đoán/scan
adb -s "$CAR_HOST" shell dumpsys package com.byd.androidauto | grep -E 'versionCode|versionName'
adb -s "$CAR_HOST" shell pm path com.byd.androidauto

# B1: bật điện thoại kết nối AA bình thường qua app ClusterNav (bấm Chiếu trong menu bubble/app),
#     KHÔNG mô phỏng escalation ⊞/T3 gì cả — dùng đúng flow user thật sẽ bấm.
# B2: ngay sau khi app báo trạng thái (EMITTING hoặc lỗi), chụp ground truth:
adb -s "$CAR_HOST" shell am stack list > aa-stack-after-cast.txt
adb -s "$CAR_HOST" shell dumpsys window windows | grep -A18 -i androidauto > aa-windows-after-cast.txt
adb -s "$CAR_HOST" shell dumpsys display > aa-display-after-cast.txt
# B3: đối chiếu bounds/windowingMode/visible trong aa-stack-after-cast.txt với 1920x0,720 khung cụm.
# B4: NHÌN CỤM VẬT LÝ (verdictSource=HUMAN, không có observable thay thế — giống Q1 CastCatalog) —
#     ghi lại đúng những gì thấy, không suy diễn.
```

Nếu B2 cho thấy task KHÔNG lên display cụm / hoặc lên sai windowingMode → đó là bug thật, độc
lập với T3 — root-cause tiếp bằng cùng kỷ luật (đọc `CastPlanner`/`CastExecutor` cho đúng
target evidence của AA, không đoán).

## 3. Kịch bản đo lại VietMap accessibility (Task #4) — chạy khi có `CAR_HOST`

Mục tiêu: xác nhận LẠI bằng dump MỚI (không dùng lại dump 2026-07-25) rằng cấu trúc
`content-desc` của VietMap còn đúng như đã thấy, trước khi viết bất kỳ dòng code nào.

```bash
export CAR_HOST=<ip-xe>
# B1: mở VietMap, bắt đầu dẫn đường thật (có ít nhất 1 lần đổi cự ly + 1 đoạn có biển tốc độ).
# B2: dump TOÀN BỘ cây UI (không grep cắt bớt như listen-nav-signals.sh làm) — nhiều mẫu, cách nhau ~10-15s:
adb -s "$CAR_HOST" shell uiautomator dump /sdcard/vm-1.xml && adb -s "$CAR_HOST" pull /sdcard/vm-1.xml .
adb -s "$CAR_HOST" shell uiautomator dump /sdcard/vm-2.xml && adb -s "$CAR_HOST" pull /sdcard/vm-2.xml .
adb -s "$CAR_HOST" shell uiautomator dump /sdcard/vm-3.xml && adb -s "$CAR_HOST" pull /sdcard/vm-3.xml .
# B3: GUARD bắt buộc trước khi tin bất kỳ file nào — lặp lại đúng lỗi 2026-07-25 nếu bỏ qua bước này:
grep -c 'package="vn.vietmap.live"' vm-1.xml vm-2.xml vm-3.xml   # phải > 0 mỗi file, nếu 0 thì dump trật app, huỷ mẫu đó
# B4: so khớp 3 mẫu, xác nhận pattern content-desc còn đúng như 2026-07-25:
grep -o 'content-desc="[^"]*"' vm-1.xml | grep -v 'content-desc=""' | sort -u
```

Đối chiếu với các pattern đã thấy 2026-07-25 (giữ nguyên để so sánh, KHÔNG coi là bằng chứng
hiện tại cho tới khi mẫu mới khớp):
- Cự ly + tên đường: `"200m Tân Phú"`
- Tốc độ hiện tại/đơn vị/giới hạn: `"0\nkm/h\n50"` — **đây chính là câu trả lời khả dĩ cho Q2
  của `carexec` SPEED_SIGN (sign-source-vietmap)**, có thể không cần đọc HAL/CAN gì cả nếu mẫu
  mới xác nhận lại đúng field thứ 3 luôn là speed limit.
- ETA/thời lượng/khoảng cách/đích: `"15:16\n28p\n10.4km\nNhà (Park 3 - Vinhomes Central Park)"`

### 3.1 Phát hiện quan trọng khi soát code hôm nay: `NavAccessibilityService` KHÔNG đọc `contentDescription`

`NavAccessibilityService.collect()` (dòng 87-100) chỉ đọc `node.text`:

```kotlin
val t = node.text?.toString()?.trim()
```

VietMap gắn dữ liệu vào `contentDescription`, không phải `text` (mọi `text=""` trong dump thật,
xem §3 B4). Nghĩa là: dù có thêm `vn.vietmap.live` vào
`res/xml/nav_accessibility_config.xml`'s `packageNames`, service hiện tại vẫn sẽ đọc được
**0 item** cho VietMap — đây KHÔNG phải việc chỉ sửa config, mà cần sửa `collect()` để đọc cả
`node.contentDescription` (generic — dùng chung cho cả GMaps lẫn VietMap, ưu tiên trường nào
không rỗng, không rẽ nhánh theo package — đúng §7). Việc sửa này thuộc tầng `app` (bước 4 trong
CLAUDE.md §14) — chỉ làm SAU KHI §3 B1-B4 ở trên đã xanh với dump MỚI, không phải bây giờ.

## 5. RE-mining 2026-07-29 (chiều) — mở rộng candidate, ưu tiên lại

Sau khi §0-4 viết xong, chạy 5 agent song song đào `jadx-tmap` (bộ HAL Java đầy đủ nhất,
121 file), `carsettings-apk` (decompile đầy đủ `com.byd.vehiclesettings`, trước đây chưa
từng đọc hết), `jadx-amap`/`jadx-amap2`, và `dashcast-src`/`jadx-openbyd`/`jadx-openbyd24`.
44 finding, phần lớn ở mức "đã chứng minh" (đọc thấy trong code thật, trích dẫn file:line).
Toàn bộ đã được thêm vào `CarExecCatalog.kt`/`CarExecScenarios.kt` dưới dạng candidate mới —
**chưa có candidate nào trong số này chạy trên xe thật**, tất cả đứng ở mức "có cơ chế đã
chứng minh qua source, chưa đo trên xe" cho tới khi tự tay chạy.

### 5.1 ƯU TIÊN SỐ 1 — cổng render nav zin (semon) đã có kế hoạch đầy đủ TỪ TRƯỚC, chưa chạy xong

Phát hiện lớn nhất buổi chiều: **không phải finding mới**, mà là một kế hoạch đã viết xong từ
**2026-06-22** (hơn 1 tháng trước refactor V2), suýt bị quên vì nằm ngoài `ClusterNav/`:

- `docs/plans/cluster-nav-render-gate.html` — chẩn đoán đầy đủ, rút từ chính `system.img` của
  xe test qua OTA (`BYDUpdatePackage/2602.zip`), không phải suy đoán.
- `docs/diagnostics/verify-on-car.sh` — harness M1-M8 đã viết sẵn, chạy qua adb thuần.
- `apks/navopen.jar` (gốc) — tool reflection gọi thẳng HAL, đã build sẵn.

**Đã LIVE-CONFIRM trên chính xe Seal DL3 test, 2026-06-22**: broadcast
`AUTONAVI_STANDARD_BROADCAST_SEND` (`TYPE=1`, `IS_BYD_MAP=false`) khiến `AmapService` thật sự
**ghi dữ liệu vào cụm** (`mIsGAODENaving=true`, ghi CAN + `AutoContainerManager.sendInfo2(4,
FlatBuffer NaviInfo)`) — nhưng **RENDER bị chặn** bởi `semon`, một kernel security monitor bật
qua property `sys.init.navi_protect` (đọc trực tiếp từ `init.rc` trong firmware thật, không phải
đoán). `navi_protect=0` → tắt `semon` → **mở cổng**.

Lần chạy `verify-on-car.sh` ngày đó để lại `verify-runs/20260622-221938/` **RỖNG** — script
không hoàn tất (không rõ lý do: mất kết nối? Ctrl-C sớm?). **Trạng thái đúng là "chưa biết cổng
mở được không", KHÔNG PHẢI "đã thử M1-M8 và trượt hết".** Đây là việc rẻ nhất, đã chuẩn bị sẵn
nhất, và nếu ăn thì giải quyết luôn câu hỏi "làm sao đưa VietMap lên làn cụm" mà KHÔNG cần đụng
tới accessibility-scraping — **làm việc này TRƯỚC** cả AA test và VietMap accessibility ở §2/§3.

Catalog mới: `CarStep` `nav-render-gate` (8 candidate: đọc property, baseline không-mở-cổng,
M1/M2/M3 setprop, M8 qua `navopen-v2.jar`). Kịch bản: `nav.render-gate-discovery`.

```bash
export CAR_HOST=<ip-xe>
adb -s "$CAR_HOST" push apks/navopen-v2.jar /data/local/tmp/navopen.jar   # bản 2026-07-29, có thêm getraw/adas
CAR_HOST=$CAR_HOST scripts/vehicle/carexec.sh run gate.probe
CAR_HOST=$CAR_HOST scripts/vehicle/carexec.sh run gate.baseline-broadcast-only
CAR_HOST=$CAR_HOST scripts/vehicle/carexec.sh run gate.setprop-navi-protect   # M1, rẻ nhất — NHÌN cụm
# nếu trượt:
CAR_HOST=$CAR_HOST scripts/vehicle/carexec.sh run gate.navopen-open          # M8, đúng cách map zin tự mở
CAR_HOST=$CAR_HOST scripts/vehicle/carexec.sh run gate.navopen-close         # luôn đóng lại sau khi quan sát
```

### 5.2 HUD kính lái thật — chuỗi gọi đã lần ra tới tận ranh giới stub

`com.byd.vehiclesettings` (decompile lần đầu hôm nay, thư mục `carsettings-apk/`) cho thấy TOÀN
BỘ chuỗi: UI toggle → `HudSwitchModel` → `HalSetter.set(BYDAutoSettingDevice.class, id, val)` →
`BYDAutoSettingDevice.getInstance(ctx).set(int[]{id}, EventValue)` — **cùng hình dạng lệnh gọi
BydHal.kt đã dùng cho INSTRUMENT**, khác class (SETTING). Feature-id thật đọc từ firmware
(`firmware/fw-2602-diff/jadx-l3-new`), không phải file stub (mọi field trong bản decompile APK
đều = 0):

| Feature-id | Hex | Ý nghĩa |
|---|---|---|
| `SET_HUD_CONFIG` | `0x38B00015` | ĐỌC: 0=không HUD, 1=W-mode, 2=AR-mode |
| `SET_HUD_SWITCH_STATUS_FEEDBACK` | `0x38B0001C` | ĐỌC: 1=đang bật, 2=đang tắt |
| `SET_HUD_SWITCH_SET` | `0x4C10E023` | GHI: 1=bật, 2=tắt |
| `SET_DYNAMIC_NAVI_FUNCTION_STATUS_SET` | `0x4C10E03A` | GHI: bật nội dung dẫn đường trên HUD (chưa rõ có đổi NGUỒN dữ liệu không) |
| `SET_SAFE_DRIVING_ASSIST_STATUS_SET` | `0x4C10E030` | GHI: bật icon ADAS trên HUD |

**Đã mở rộng `apks/navopen-v2.jar` (giữ nguyên `apks/navopen.jar` gốc, KHÔNG ghi đè) với 2 lệnh
mới, generic, không hardcode riêng cho HUD:**
- `getraw <instr|setting|adas> <hexid>` — ĐỌC thô một feature-id, không ghi gì.
- `setraw` mở rộng thêm target `adas` (trước chỉ có `instr`/`setting`).

Nguồn: `NavOpen/src/com/byd/navopen/NavOpen.java` (ngoài `ClusterNav/`, không track git). Đã
compile + dex lại bằng chính Android SDK/d8 đã cài trên máy, xác nhận `getraw`/`resolveDevice`
có trong dex mới — **chưa chạy thật trên xe**, chỉ mới build sạch.

Catalog mới: `CarFeature.HUD_SWITCH`, `CarStep` `hud-probe` (6 candidate, thứ tự đọc trước ghi
sau: `hud.config-read` PHẢI chạy trước tiên). **Cập nhật 2026-07-29 tối: chủ xe xác nhận trực
tiếp xe test CÓ kính lái vật lý** — không còn phải hỏi "có hay không", `hud.config-read` giờ chỉ
để biết W-mode/AR-mode + xác nhận quyền HAL `set()`. Kịch bản: `hud.discover-switch`.

### 5.3 SPEED_SIGN — công tắc TSR thật (ADAS_SLA_STATE), nhưng KHÔNG PHẢI nơi ghi số km/h tuỳ ý

`BYDAutoADASDevice.setSLAState()`/`getSLAState()`, feature-id `ADAS_SLA_STATE=0x31600025`
(cross-check hex↔decimal đã verify lại bằng tay: `828375077 == 0x31600025` ✓) — đây là công tắc
BẬT/TẮT TOÀN BỘ tính năng TSR (5 giá trị enum: off/fusion/vision/nv-only/defect), KHÔNG PHẢI một
ô nhớ để ghi "50 km/h". **Không kỳ vọng dùng field này để hiện số giới hạn tốc độ tuỳ ý lên
cụm/HUD.** `BYDAutoSpeedDevice` (device khác, tưởng liên quan vì tên "Speed") xác nhận KHÔNG có
field TSR/biển báo nào — chỉ là gia tốc/phanh/tốc độ bản thân xe.

Phát hiện phụ có giá trị hơn cho mục tiêu thật (hiện số giới hạn tốc độ): `com.byd.trafficmonitor`
— service AIDL thật, **đã xác nhận SỐNG trên xe test** (đối chiếu `docs/diagnostics/runs/
20260621-141543`) — cho phép BẬT/TẮT một package cụ thể làm "nguồn TSR" qua
`IAppTrafficInterface.setRestrictByUser(pkg, restrict)`. Package cứng trong code RE ra
(`com.telenav.app.isa`) không có trên xe test — ai là provider thật (nếu có) chưa biết.

Catalog mới: `sign-inject.sla-state-probe`/`sla-state-toggle` (qua `navopen-v2.jar getraw/setraw
adas`), `probe.trafficmonitor-service`/`probe.naviserviceapi-service`/`probe.magicwindow-service`
(inventory thuần, `service list`/`dumpsys`/`pm list packages`).

### 5.4 Cluster-cast — mở rộng bảng opcode, và một xung đột CHƯA GIẢI QUYẾT

- **Opcode 2/3** (đèn cảnh báo tất cả bật/tắt) và **12/13** (cửa sổ ADAS 2D/3D hiện/ẩn) — xác
  nhận qua chính app chẩn đoán của BYD (`com.byd.clusterdebug`, dashcast-src RE lại), field-tested
  đa đời máy DL3/Di4/DL5/DL6. Catalog mới: `CarStep` `cluster-overlay-toggles`.
- **Opcode 1** ("ngắt Qt hoàn toàn, MCU tiếp quản") — dashcast-src tự ghi "KHÔNG dùng để launch
  app", display cụm biến mất khỏi `IActivityManager`. **Không thêm vào catalog** — không có
  đường phục hồi đã biết, đúng tinh thần CLAUDE.md §5.
- **XUNG ĐỘT CHƯA GIẢI QUYẾT**: `dashcast-src` (field-test thật, nhiều đời xe) label opcode
  **29/30/31 là KÍCH THƯỚC VẬT LÝ cụm theo từng đời xe** (29=8.8" Atto3/Dolphin, 30=12.3" Seal EU
  mặc định, 31=10.25" Seal U DMI) — **không phải cong/phẳng** như ClusterNav vẫn hiểu (`castSeq
  DL3 = [30,16,35]`, "30=curved/keep-km/h"). Cả hai label có thể đều đúng theo một góc nhìn khác
  nhau, hoặc một trong hai bị đọc nhầm lúc field-test cũ. Candidate mới `style.probe-screen-size-29`
  gửi opcode 29 (chưa từng gửi trên xe này) để so sánh trực tiếp — **chưa chốt, cần chạy trên xe**.
- Xác nhận thêm (không phải finding mới, chỉ đối chiếu độc lập): "switch"/hot-swap không có opcode
  riêng — chỉ `am start ... --activity-clear-task` lên lại display đang sống, đúng cách V2 hiện làm.
- Kiểm tra riêng câu hỏi AA/T3 (GOAL 2 của agent mining `dashcast-src`+`jadx-openbyd`): **grep
  toàn bộ `dashcast-src` (kể cả git log) và `jadx-openbyd`/`jadx-openbyd24` cho "carplay"/"android
  auto"/"gearhead" → 0 kết quả cả hai chiều.** Không có bằng chứng ỦNG HỘ giả thuyết T3 cũ, nhưng
  cũng không có bằng chứng PHỦ ĐỊNH — cả hai tool tham chiếu chưa từng test AA/CarPlay. Không đổi
  kết luận đã chốt ở §1.2 (bỏ giả thuyết T3), chỉ xác nhận thêm là chưa có dữ liệu nào khác đáng
  tin hơn — vẫn phải đo lại từ đầu theo §2.

## 6. Kịch bản chiều 2026-07-29 — 3 pha: đỗ 15p → chạy về nhà → tới nhà

Nguyên tắc an toàn: **pha 2 (đang lái) không gõ lệnh gì cả** — chỉ mắt nhìn cụm/HUD. Mọi lệnh
tương tác (setprop, service call, app_process) chỉ chạy khi XE ĐỖ (pha 1, pha 3). Pha 2 chỉ có
một script chạy nền tự động, không cần đụng tay.

Dùng `docs/diagnostics/artifacts/carexec-checklist.html` (đã mở sẵn, nút copy từng lệnh) làm
nguồn lệnh chính xác — danh sách dưới đây chỉ nói THỨ TỰ và NGÂN SÁCH THỜI GIAN, không lặp lại
lệnh. Ghi verdict (`carexec.sh verdict ...`) để ở PHA 3, không làm giữa pha 1 cho đỡ mất thời
gian — pha 1 chỉ cần nhớ/nói to kết quả (đạt/không đạt/note ngắn).

### Pha 0 — trước khi rời chỗ đỗ (2 phút)

```bash
adb connect <ip-xe>:5555              # hỏi lại IP nếu đổi, đừng đoán
adb -s <ip-xe>:5555 push apks/navopen-v2.jar /data/local/tmp/navopen.jar
```

### Pha 1 — xe đỗ, 15 phút (ưu tiên cứng theo thứ tự, dừng ở đâu hay đó khi hết giờ)

**Xe test CÓ kính lái vật lý — chủ xe xác nhận trực tiếp (không phải suy đoán nữa).** Vẫn chưa
biết W-mode hay AR-mode, và chưa biết ClusterNav's process có được HAL cấp quyền `set()` hay
không — hai câu đó là lý do `hud.config-read` vẫn cần chạy, không phải để hỏi "có hay không".

**Việc BẮT BUỘC (đọc-only, ~2 phút cả cụm, không rủi ro gì)** — làm hết trước, dù còn bao nhiêu
phút cũng phải xong khối này vì rẻ và thông tin ra định hướng phần còn lại:
1. `gate.probe` — 4 property cổng render
2. `hud.config-read` — W-mode hay AR-mode (đã biết có HUD, chỉ cần biết loại + quyền HAL)
3. `sign-inject.sla-state-probe` — trạng thái TSR hiện tại
4. `probe.trafficmonitor-service`, `probe.naviserviceapi-service`, `probe.vehiclesettings-installed`, `probe.magicwindow-service`, `probe.autocontainer-whitelist` — 5 lệnh inventory, chạy liền tay

**Việc CHÍNH (còn ~10-12 phút) — làm theo đúng thứ tự:**
5. `gate.baseline-broadcast-only` — bơm frame giả, xem cụm (mong đợi: KHÔNG đổi gì — đây là mốc so sánh)
6. `gate.setprop-navi-protect` (M1) — nếu cụm hiện làn nav → **THẮNG**, ghi lại rồi đi tiếp bước 8, không cần thử M2/M3
7. Nếu M1 không ăn: `gate.navopen-open` (M8) — đây là cách map zin tự làm, mạnh nhất nếu SELinux chặn setprop. Nhớ `gate.navopen-close` ngay sau khi nhìn xong.
8. `hud.switch-on` → nhìn kính lái (đã xác nhận có, đáng thử ngay trong khối chính, không phải bonus) → `hud.switch-feedback-read` → `hud.switch-off`

**Việc BONUS (nếu còn thời gian, theo thứ tự ưu tiên giảm dần):**
9. `hud.nav-content-toggle-on` / `hud.adas-content-toggle-on` — nếu bước 8 xác nhận công tắc HUD ăn lệnh, thử xem nội dung nav/ADAS có lên kính lái không
10. `style.probe-screen-size-29` — chốt xung đột cong/phẳng vs kích thước (chỉ cần 1 lần gửi + nhìn cụm)
11. `cluster-overlay-toggles`: `overlay.adas-window-show`/`hide`, `overlay.warning-lamps-on`/`off`
12. AA cast test (§2 phía trên) — CHỈ làm nếu điện thoại đã sẵn kết nối AA từ trước, không mất thời gian cắm dây/pair lúc này

### Pha 2 — lái xe về nhà (không gõ lệnh, chỉ NHÌN)

Ngay trước khi lăn bánh (vẫn trong pha 1 lúc xe còn đỗ, sau khi xong việc chính), bật sẵn:

```bash
scripts/vehicle/listen-nav-signals.sh vietmap --serial <ip-xe>:5555 --seconds 1800
```

(1800s = 30 phút, chỉnh theo quãng đường thật; script tự dừng, không cần Ctrl-C). Mở VietMap
thật trên đầu xe/điện thoại, để hiển thị suốt chuyến — KHÔNG cần cast lên cụm (chỉ cần
foreground để uiautomator dump thấy được), tránh lặp lại đúng kịch bản gây đơ RECOVERY_PENDING
hôm qua (đó là lúc VỪA cast VỪA chỉnh geometry).

Việc duy nhất cần làm trong lúc lái: **liếc cụm/HUD** —
- Nếu Pha 1 mở được cổng (bước 6/7 thắng): làn nav zin có thật sự cập nhật theo tuyến đường thật không, hay chỉ ăn với frame giả tĩnh?
- Đi qua ít nhất 1 biển giới hạn tốc độ: để ý xem VietMap có tự hiện số trên màn hình nó (dữ liệu `content-desc` "0/km/h/50" đã biết) hay không — không cần thao tác gì, script tự chụp lại.
- Nếu app đơ/cụm treo: KHÔNG thao tác gì thêm, để yên, ghi nhớ giờ phút xảy ra để đối chiếu log lúc về tới nhà.

### Pha 3 — tới nhà, xe đỗ hẳn (thời gian thoải mái hơn)

1. Dừng/kiểm tra `listen-nav-signals.sh` đã ghi xong vào `oncar-signals-vietmap-*/`.
2. Guard bắt buộc trước khi tin dữ liệu (đúng bài học 2026-07-25):
   `grep -c 'package="vn.vietmap.live"' oncar-signals-vietmap-*/*.xml 2>/dev/null` — dump nào ra 0 thì huỷ, không dùng.
3. Đối chiếu content-desc thu được với 3 pattern đã biết (khoảng cách+đường, tốc độ/giới hạn, ETA) — xem §3 phía trên.
4. Ghi verdict CHO TẤT CẢ candidate đã thử ở pha 1 (và pha 2 nếu có quan sát HUMAN):
   `scripts/vehicle/carexec.sh verdict <candidate-id> ok|fail --note "..."` — làm từng cái, không vội.
5. `scripts/vehicle/render-checklist.sh` — dựng lại trang, mở xem trạng thái mới.
6. Nếu còn thời gian: làm tiếp phần BONUS ở pha 1 chưa kịp, hoặc AA cast test nếu chưa làm.
7. Nếu RECOVERY_PENDING lặp lại tự nhiên trong pha 2 (không cố ý tạo ra): dùng màn `DiagActivity`
   trong app bấm chụp log ngay — đây là bằng chứng thật đầu tiên cho bug P0, đừng bỏ qua.

## 7. Việc còn treo, không thuộc phạm vi tài liệu này

- Root-cause bug đơ RECOVERY_PENDING khi chỉnh kích thước VietMap lúc đang lái (P0, xem
  `cast-recovery-deadend-2026-07-28.md` — lưu ý đó là dead-end KHÁC, `reconcileAbandoned` chỉ
  xử lý ca "abandoned + idle-clean", không xử lý ca "stuck trong khi target vẫn active trên
  display" đã gặp sáng nay).
- Xác nhận Stop có thật sự dispatch thành công trong đúng kịch bản kẹt sáng nay hay không (cần
  log, chưa có).

## 8. Kết quả thật trên xe — chiều 2026-07-29 (pha 1 + pha 2)

IP xe phiên này: `<vehicle-ip>:5555` (DiLink3.0). Đã ghi hết vào `verdicts.tsv` (15 candidate OK,
3 FAIL — xem `render-checklist.sh`). Tóm tắt phát hiện lớn:

### 8.1 Cụm nav zin RENDER ĐƯỢC ngay bây giờ — không cần setprop gì cả

Mốc `gate.probe` lúc bắt đầu: `navi_protect=1`, `whitelist=0`, `change_navi_auth=1` (2 cái sau đã
sẵn từ trước, KHÔNG do phiên này set), `fission_single_os=0`.

Bơm `AUTONAVI_STANDARD_BROADCAST_SEND` (TYPE=1, IS_BYD_MAP=false) **ngay lúc `navi_protect` vẫn
=1** → cụm hiện icon rẽ + tên đường NGAY LẬP TỨC. Giả thuyết "cổng đóng nên không hiện" của phiên
2026-06-22 **không còn đúng** ở trạng thái property hiện tại của xe. Khoảng cách ban đầu kẹt ở
`-1` — thêm 4 field chuỗi `_AUTO` (`SEG_REMAIN_DIS_AUTO`, `ROUTE_REMAIN_DIS_AUTO`,
`ROUTE_REMAIN_TIME_AUTO`, `ROUTE_REMAIN_TIME_STRING`) thì khoảng cách ra đúng số thật ("444 m").
Đã thêm candidate mới `gate.broadcast-full-render` vào catalog khoá lại phát hiện này.

**Ý nghĩa**: câu hỏi lớn nhất của cả dự án ("làm sao đưa nav lên làn cụm cho app bên thứ 3") coi
như đã có lời giải KHẢ THI ngay bây giờ, bằng đúng 1 broadcast — chưa cần AA/AutoContainer gì cả.
Việc còn lại: nối VietMap's dữ liệu thật (từ content-desc, xem §3) vào đúng các field broadcast
này, thay vì dữ liệu giả đã bơm tay hôm nay.

### 8.2 HUD kính lái: công tắc đọc/ghi được, nhưng nội dung nav KHÔNG lên

- Xe **có** HUD vật lý (chủ xe xác nhận trực tiếp). `hud.config-read` = 1 (W-mode).
  `hud.switch-feedback-read` = 1 (đang bật sẵn).
- `hud.nav-content-toggle-on` (`setraw setting 4C10E03A 1`, rc=0 — lệnh chạy thành công) **không
  tạo hiệu ứng gì thấy được** trên kính lái, dù đúng lúc đó cụm đang render thật (icon+444m+Ba Test
  Le Loi). Kết luận: cụm và HUD là hai đường render tách biệt thật sự — bật cờ này không tự động
  kéo nội dung cụm sang HUD. Cần tìm đường khác cho HUD (có thể qua chính broadcast/FlatBuffer path
  nếu nó có field riêng cho HUD, chưa kiểm) — SPEED_SIGN, HUD content vẫn là ẩn số.

### 8.3 Phát hiện phụ (inventory)

- `com.byd.trafficmonitor` có cài thật (khớp RE) — chưa có AIDL registered qua ServiceManager (bound service).
- `com.byd.naviserviceapi`/`BydAutoTMap`: KHÔNG có mặt trên xe này (FAIL, informative).
- `com.byd.vehiclesettings` package KHÔNG tìm thấy bằng `pm list packages` trên ROM này (FAIL) —
  nhưng không sao, HAL `BYDAutoSettingDevice` vẫn gọi thẳng được qua reflection, không cần app này.
- `magicwindow` service: có tồn tại trên DL3 (trước chỉ biết có trên DL5).
- Whitelist `AutoContainer`: chỉ `com.xdja.clusterdemo` — ClusterNav's app-layer chưa được whitelist.
- ADAS_SLA_STATE = 1 (Fusion mode) lúc đo — không phải giá trị cần quan tâm cho việc hiện số km/h,
  chỉ là trạng thái bật/tắt tính năng.

### 8.4 Pha 2 (lái về) — gap thật, cần làm lại

`listen-nav-signals.sh vietmap` chạy đủ 1800s, nhưng: **VietMap process chết ở phút thứ ~12**
(`process-history.txt`: pid=6946 lúc 09:42:39Z → `pid=none` lúc 09:55:12Z, không có gì sau đó).
`[ui]` capture cho thấy app foreground sau đó là `anddea.youtube` (một app YouTube khác) — tức là
ai đó đã chuyển màn hình sang YouTube giữa chừng, không phải do VietMap crash (không thấy dấu hiệu
crash/ANR trong logcat). Guard `package="vn.vietmap.live"` = **0 hit** trong toàn bộ capture —
đúng bài học 2026-07-25: không có dữ liệu VietMap mới nào để tin. Cần làm lại §3 (dump
accessibility) vào một chuyến khác, với kỷ luật giữ VietMap mở suốt chuyến.

### 8.5 Pha 3 (tối, về tới nhà) — Cast bị khoá toàn hệ thống, root-cause tới tận source

Sau khi về nhà, thử chiếu AA → toast "Chưa chiếu được ở trạng thái này". User xác nhận: **CarPlay
sáng nay và VietMap thử ngay lúc đó cũng bị y hệt** → không phải lỗi riêng của AA, là một khoá
toàn cục chặn mọi target. Root-cause bằng cách đọc thẳng `session.env` qua `run-as` (không đoán):

1. **Transaction CAST cho VietMap kẹt ở `RECOVERING`**, lỗi `"boot changed; re-observation
   required"`. `CastCoordinator.reconcileAbandoned()` (`CastCoordinator.kt:124-144`) chỉ đóng được
   transaction này khi `observe()` trả `Known(IDLE_CLEAN)` — nhưng suốt cả ngày `dumpsys display`
   chỉ có `displayId=0`, không có display cụm nào để quan sát → `discoverClusterDisplay()`
   (`CastDeviceParsers.kt:27-46`) luôn trả `Unknown`, transaction không bao giờ tự đóng được.
2. Display cụm được tạo bằng chuỗi lệnh `service call AutoContainer 2 i32 1000 i32 <op> …` — xác
   nhận lại hôm nay: **phải đủ CẢ BA opcode `30`, `16`, `35`**, thiếu `35` thì `dumpsys display`
   không hề xuất hiện display mới (đã thử với chỉ 2/3 opcode, không tạo được gì). Đủ cả ba →
   `mDisplayId=1` (`fission_bg_xdjaVirtualSurface`, 1920×720@320dpi) xuất hiện ngay, dù panel vật lý
   vẫn hiện đồng hồ bình thường suốt (tạo display WM-level và hiện nội dung lên panel là hai việc
   tách rời — session này chỉ làm việc đầu).
3. Có display rồi, `reconcileAbandoned()` tự đóng được transaction (xác nhận qua log thao tác của
   chính `DiagActivity`: *"closed abandoned CAST (...): cluster observed idle, no compensation
   owing"*). Nhưng `stopRequested` vẫn `true` sau đó — **đúng thiết kế**, không phải bug
   (`CastCoordinator.kt:157-168`: đóng transaction bỏ dở không tự xoá `stopRequested`). Phải sửa
   riêng field này.
4. Sửa `stop=1` → `stop=0` bằng cách ghi thẳng `session.env` qua `run-as` (đã tính đúng checksum
   SHA-256 theo đúng thuật toán ở `CastSessionStore.kt:195-210,419` — xem §9 quy trình). App đọc lại
   sạch, không crash, tự ghi lại checksum mới ở lần save kế tiếp.
5. Sau cả hai bước fix, `castEligible` (`ClusterCastActivity.kt:429`) **vẫn `false`, mọi nút "CHIẾU
   ... LÊN CỤM" vẫn xám** — kể cả app không liên quan gì (VD "Tài liệu hướng dẫn"). Lần theo tới
   `CastRuntimeUi.render` (`CastRuntimeUi.kt:57-108`) xác nhận đây là khoá cấp MODEL (mọi app), không
   phải riêng VietMap.

### 8.6 Bug MỚI, nghiêm trọng hơn cả vụ kẹt transaction: `stableSession` sống sót qua reboot, display thì không

Test cuối ngày: **reboot xe thật** để xem "sạch hẳn thì chạy được chưa" — kết quả: **tệ hơn**, màn
Cluster Cast báo *"Cần xử lý thủ công · Chưa nhận diện được trạng thái cụm"* (MANUAL_REQUIRED),
toàn bộ app xám, kể cả sau khi display cụm (đương nhiên) không còn tồn tại (`dumpsys display` chỉ
còn `displayId=0`) — trong khi `session.env` không đổi gì, vẫn còn nguyên `stable=…IDLE_VERIFIED…
display-1…` từ trước khi tắt máy.

**Root cause, đọc thẳng `CastSessionStore.kt:69-99`**: `initializeForBoot()` là nơi DUY NHẤT xử lý
chuyển boot — nó bump `durableEpoch`, xoá `bootAutomationRequest` chưa xong (archive thành
`BOOT_ROLLOVER`), giữ lại `pendingIntent` gốc USER — nhưng **cố tình không đụng vào `stableSession`
hay `transaction`** (docstring chính nó ghi: *"It never clears or replays a row"*). Việc giữ
`transaction` qua boot là ĐÚNG Ý ĐỒ (để còn chẩn đoán/phục hồi transaction dở dang — xem
`reconcileAbandoned` KDoc, sự cố gần giống 2026-07-27). Nhưng **không có cơ chế tương đương cho
`stableSession`**.

Hệ quả dây chuyền qua `CastRuntimeUi.kt:67-83` (`stableConverged`) và
`CastUiStateProjector.kt:86-93`: `stableSession != null` nên **không được coi là `coldPristine`**
(`isColdPristine()` đòi `envelope.stableSession == null`, `CastRuntimeUi.kt:125`) — nhưng cũng
**không thể verify lại `IDLE_VERIFIED`** vì display nó tham chiếu (`expectedDisplayIdentity =
"display-1"`) đã biến mất sau reboot (display này vốn dĩ chỉ tồn tại ở tầng WindowManager, không hề
sống qua reboot — xem §8.5 mục 2). Rơi đúng vào khe hở giữa hai nhánh:
`project()` (`CastUiStateProjector.kt:86-93`) chỉ có `stable(input)` (cần `stableConverged=true`)
hoặc `failClosed(input)` — không có nhánh thứ ba cho "có stableSession cũ nhưng không verify lại
được vì boot đã đổi". Và `failClosed` (MANUAL_REQUIRED) chỉ cho duy nhất hành động `OPEN_DIAGNOSTICS`
— không có nút tự phục hồi nào trong `recoveryRows` (`CastUiStateProjector.kt:257-277`) khớp case
này, vì đây không phải một `RecoverySubstate` — nó chưa từng được lập kế hoạch.

**Mức độ nghiêm trọng**: đây không phải bug riêng của VietMap/AA hôm nay — nó tái hiện ở **MỌI lần
xe tắt/mở máy sau lần đầu tiên `stableSession` từng đạt `IDLE_VERIFIED`**, cho **mọi app**, vì display
cụm luôn được tạo qua opcode WM-level (không có cách nào khiến nó sống qua reboot với cơ chế hiện
tại). Tức là: cast từng chạy được 1 lần → tắt máy → mở máy lại → cast khoá vĩnh viễn, phải vào
`run-as` sửa tay như tối nay mới gỡ ra được. Đây là bug ưu tiên [P0] cần root-cause thêm + viết spec
sửa (không tự vá đêm nay) trước khi động vào `CastSessionStore.initializeForBoot()` hay
`CastUiStateProjector.project()`.

**Hướng sửa (chưa làm, cần spec + duyệt trước khi code theo rule global §1)**: hoặc (a)
`initializeForBoot()` khi bootId đổi thì chủ động rơi `stableSession` về `null` (coi phiên ổn định cũ
là không còn tin được sau một boot mới, giữ nguyên phần xử lý `transaction`/`bootAutomationRequest`
đã đúng), hoặc (b) thêm một nhánh trong `CastUiStateProjector.project()` xử lý riêng case
"`stableSession != null` nhưng `stableConverged == false` VÀ đã sang boot mới" → coi như cần
bootstrap lại từ đầu thay vì `failClosed`. Cần test hồi quy dựng đúng từ dump thật tối nay (`stable=`
+ `stableBaseline=` đã decode ở §8.7) trước khi chọn hướng.

### 8.7 Kỹ thuật debug trực tiếp qua `run-as` — ghi lại để tái dùng, không phải dò UI

Bản release đã bật tạm `isDebuggable = true` trong `app/build.gradle.kts` (versionCode 77, cùng
chữ ký nên `adb install -r` đè lên không mất data) để mở đường `adb shell run-as
com.byd.clusternav`. Từ đó đọc/ghi thẳng được `/data/data/com.byd.clusternav/files/cast-v2/session.env`
— định dạng, thuật toán checksum, cách decode từng field `|`-delimited base64 xem
`CastSessionStore.kt:195-230,419`. Kỹ thuật này nhanh hơn và chắc hơn hẳn so với dò UI qua
screenshot+tap (xem rule mới ở CLAUDE.md §15) — **nhớ trả `isDebuggable` về `false` trước khi phát
hành bản thật**, đây chỉ là công cụ chẩn đoán tạm thời.

**Việc còn treo sau tối nay**:
- Chưa xác nhận VietMap có thật sự cast lên cụm được không SAU KHI bug §8.6 được sửa (chặn bởi bug
  đó, chưa test tới được lớp per-app).
- Khung VietMap méo `1344×440 tại (288,140)` (còn sót từ sự cố sáng nay) — chưa rõ có tự hết khi
  bootstrap lại từ `coldPristine` hay cần nút "Khôi phục" riêng.
- Dialog "anddea.youtube.music không bám cụm" gặp lúc bấm "Thử kết nối lại" — chưa rõ còn liên quan
  hay đã hết ý nghĩa sau khi app restart.
- Revert `isDebuggable = true` trong `app/build.gradle.kts` trước khi release thật.
- Chưa commit gì của phiên hôm nay lên git.
