# Giữ app "thoát ô" ở lại trong ô — so sánh các hướng trên máy ảo (2026-10-09)

> **Trạng thái**: Evidence · **Cập nhật**: 2026-10-09 · **Chủ**: dangkhoi · **Liên quan**: spec `docs/specs/kachi-298-plan.html` R7 ·
> `diagnostics/emu-slot-escape-app-sweep-2026-10-09.md` · `diagnostics/oncar-freeform-waze-2026-10-09.md` · `diagnostics/waze-into-slot-research-2026-09-14.md` ·
> `diagnostics/perf-298-vs-297-2026-10-09.md` · backlog `SLOT-ESCAPE-POLICY`.
> Máy ảo `emulator-5556` (AVD `kachi_play`, Android 10, 1920×1080 @240dpi), Kachi vehicleTest **2.98 (201)** đã cài sẵn (cây 116f239). Không chạm xe,
> không đổi mã trong repo. Nguyên mẫu đo (script shell + một lớp Java chạy bằng `app_process` uid 2000) chỉ nằm ở scratchpad phiên, đã gỡ khỏi máy ảo.
> Ảnh chụp có tài khoản Google của máy ảo ⇒ không đưa vào repo.

Owner 09/10: *"làm thử nhiều cách đi … thử triệt để trên emulator"* · *"nên đi từ rootcause chứ ko nên thấy sự vụ rồi lại vá lên thêm"* ·
*"làm thêm 1 lớp phủ thì lại quá heavy"*.

## 0. Gốc rễ (nhắc lại, có nguồn)

[ĐO nguồn `android-10.0.0_r47`, tải lại 09/10]:

- `ActivityStarter.setTaskFromSourceRecord` (`ActivityStarter.java:2352-2397`): app tự mở activity thứ hai vào CÙNG task ⇒
  `moveStackAllowed = … || !mStartActivity.canBeLaunchedOnDisplay(targetDisplayId)` ⇒ `sourceTask.reparent(…, "launchToSide")` — cả task sang display khác.
- `ActivityRecord.canBeLaunchedOnDisplay` (`ActivityRecord.java:1422-1425`) dùng **`launchedFromUid` = uid của app** ⇒
  `ActivityStackSupervisor.canPlaceEntityOnDisplay` (`:350`) ⇒ `isCallerAllowedToLaunchOnDisplay` (`:1067`): màn `TYPE_VIRTUAL`, chủ ≠ `SYSTEM_UID`,
  chủ ≠ uid app (`:1098`) và activity không `FLAG_ALLOW_EMBEDDED` (`:1102`) ⇒ `false`. Shell (uid 2000) qua được vì có `INTERNAL_SYSTEM_WINDOW`
  (`Shell AndroidManifest`), app thì không ⇒ lần mở đầu (Kachi, shell) vào được, lần mở thứ hai (app) bị đá.
- Sau đá: `handleNonResizableTaskIfNeeded` ghi `Failed to put TaskRecord … on display N` (`ActivityStackSupervisor.java:2436`) rồi
  `notifyActivityLaunchOnSecondaryDisplayFailed(taskInfo, N)` (`:2439`) ⇒ **mọi `ITaskStackListener` nhận đúng sự kiện "task X muốn lên màn N mà không được"**
  (`TaskChangeNotificationController.java:382-389`). Đây là tín hiệu gốc, có sẵn, không cần dò.

## 1. Bảng so sánh

| Hướng | Chạy được? | Ổn định | Thoát lại | Bàn phím | Chạm | Nhấp nháy mỗi lần thoát | Chi phí | Rủi ro xe (§4/§5) | Độ phức tạp so với R7 |
|---|---|---|---|---|---|---|---|---|---|
| **A. Dời stack về lại màn ảo ô** (nghe sự kiện, uid 2000) | ✅ 8/8 app (Waze, VietMap, Gmail, Messages, Drive, Meet, + lượt Waze/VietMap lặp) [ĐO] | 38 lần thoát được dời → 38 lần về đúng ô (35 qua bộ nghe + 3 qua script; 1 lần bản nguyên mẫu đầu bỏ qua do lỗi đọc màn, đã sửa); 0 ANR; **1 lần Waze sập** (`BadTokenException`, 1/38, không tái hiện) [ĐO] | Waze: chỉ khi mở `LocationPreviewActivity`/`TripOverviewActivity` (≈ 2–3 lần/phút khi chủ động tìm đường; 0 khi chạy dẫn đường/menu/cài đặt — đều là fragment) · VietMap (Flutter): 0 sau màn quyền, 2 khi bấm đăng nhập Google · Meet: 2 lần lúc chào [ĐO] | ✅ hiện ở display 0 (đáy màn), gõ vào app trong ô [ĐO] | ✅ kéo, chạm, hộp quyền trong ô bấm được [ĐO]; owner: *"click thử lên 3 nút nổi OK, chạy tới lui cũng ok"* [ĐO owner] | Từ `Failed to put` tới về ô 25–160 ms; thấy: thanh trạng thái hệ lóe ~0,5 s · ô đen 0,4–0,6 s lúc app vẽ lại · toast hệ *"App does not support launch on secondary displays."* ~2 s · Kachi `onPause/onResume` 1 vòng [ĐO khung hình] | 0 nhịp dò; 2 lời gọi binder/lần thoát; nghe 25 phút = 0,66 s CPU [ĐO] | `move-stack` từng treo hệ trên DiLink3 (08-01) ⇒ 🚗 phải chứng minh; không ghi gì bền ngoài hệ | **Nhỏ hơn**: bỏ được freeform, lớp che, khung, dấu bền |
| A′. Như A nhưng dò bằng `am stack list` vòng lặp | ✅ cùng kết quả | — | — | — | — | về ô 40–160 ms | `sh` ~8 % CPU liên tục [ĐO] | — | ❌ loại: dò dày |
| **B. Chặn từ lúc mở** (cờ/tuỳ chọn `am start`) | ❌ 6/6 biến thể vẫn thoát [ĐO] | — | — | — | — | — | — | — | Không làm được trên A10: cổng xét uid của **lời gọi thứ hai** (app), không xét lời gọi của Kachi |
| B′. Mở ở display 0 rồi dời vào ô khi app đã ổn | = A ở lượt đầu; activity mới sau đó vẫn thoát | — | như A | — | — | — | — | — | Không thêm gì so với A |
| **C. R7 hiện tại** (freeform đúng khung trên display 0) | ✅ 7/7 app sống (sweep 09/10) | Waze PSS 573 MB sau ~11 lượt nhận/thả, 1,1 GB sau ~32 (perf 09/10) | 0 (một khi đã nhận, app ở freeform display 0 — activity mới vẫn trong task) | display 0 như A | qua lớp che + vùng viền | nhận 2–3,5 s sau khi thoát (burst) — trong lúc đó app toàn màn | lớp che 9–10 cửa sổ, ~2,6 lệnh shell/lượt mở | **Thanh trạng thái + thanh điều hướng BYD hiện suốt khi có cửa sổ freeform** (xe 09/10 §2 mục 8) · freeform nhớ theo app trong `launch_params` (bền qua reboot) · cờ `enable_freeform_support` | Lớn |
| D. Màn ảo chủ `SYSTEM_UID` | [SUY nguồn] app vào được không cần `allowEmbedded` (`:1098` bỏ qua) | — | 0 | — | — | 0 | — | Kachi không có đường lấy màn như vậy đổ vào ô (§5) | — |
| E. PIP / chia đôi | PIP cấm (luật dự án); chia đôi bật thanh hệ thống (`DisplayPolicy` — oncar-freeform §4) | — | — | — | — | — | — | — | Loại |

## 2. Hướng A — đo chi tiết

### 2.1 Cơ chế dời

- [ĐO nguồn] `am display move-stack <stack> <vd>` = `ActivityTaskManagerService.moveStackToDisplay` (`:3395-3408`, cần `INTERNAL_SYSTEM_WINDOW` — shell có)
  → `RootActivityContainer.moveStackToDisplay` (`:937`) — **không** đi qua `canPlaceEntityOnDisplay` ⇒ cổng của §0 không chặn.
- [ĐO] A10 tạo **một stack riêng cho mỗi task** bị đá (StackId 7, 8, 9, 13, 15, 16, … — mỗi lần thoát một id mới, stack cũ trên màn ảo bị gỡ) ⇒ dời cả stack
  chỉ dời đúng task đó. Nguyên mẫu vẫn kiểm `getAllStackInfos` = 1 task trước khi dời (luật §4: không bao giờ dời mù).
- `am stack move-task` (`ATMS.moveTaskToStack` `:2551-2584`) cần một stack có sẵn trên màn ảo — sau khi bị đá màn ảo ô **không còn stack nào** ⇒ không dùng được
  nếu không giữ chỗ trước [ĐO: stack cũ bị `wm_stack_removed`].

### 2.2 Nguyên mẫu dùng để đo (không ở repo)

`app_process` uid 2000 (cùng cách daemon chạm `InputDaemonMain` đang chạy) đăng ký `ITaskStackListener` (`ATMS.registerTaskStackListener` `:3452`, cần
`MANAGE_ACTIVITY_STACKS` — shell có) và trong `onActivityLaunchOnSecondaryDisplayFailed(taskInfo, requested)`: gói = gói ô · màn yêu cầu = màn ảo của Kachi ·
stack 1 task ⇒ `moveStackToDisplay(stackId, requested)`. Ghi chú: thông tin màn `FLAG_PRIVATE` của Kachi **không đọc được từ uid 2000**
(`DisplayManagerGlobal.getDisplayInfo` trả rỗng) ⇒ bản sản phẩm phải để Kachi tự báo daemon danh sách (màn ảo, gói) được phép.

### 2.3 Số đo

| Ca | Kết quả [ĐO] |
|---|---|
| Waze nguội vào ô (trampoline → `MainActivity`) | `Failed to put … on display 5` 21:48:48.490 → `MOVED` 48.569 (79 ms); không `am_relaunch`; ô giữ app, nhịp đo Kachi không kịp kết luận `APP_ELSEWHERE` |
| Waze dùng 3 phút (tìm "gas" bằng bàn phím, xem địa điểm, chọn đường, Go, báo cáo, chia sẻ, menu, Cài đặt, Trợ giúp, Lên kế hoạch) | thoát 3 lần (`LocationPreviewActivity`, `TripOverviewActivity` ×2), về ô cả 3; dẫn đường chạy trong ô; HOME → Waze vẫn trong ô |
| Waze 21 vòng (Gần đây → `TripOverviewActivity` → quay lại) | 19 thoát (10 + 9; vòng còn lại chạm hụt) đều về ô · 0 crash/ANR · Waze PSS 273 → 403 MB sau 10 vòng → 412 MB sau 10 vòng tiếp (đi ngang, không phình theo lượt như freeform) · Kachi PSS 70,6 → 74,6 MB |
| VietMap mod nguội, **chưa** cấp vị trí | hộp quyền (`GrantPermissionsActivity`, task VietMap) đá cả task → về ô sau 51 ms ⇒ **hộp quyền hiện TRONG ô**, bấm *Allow* trong ô được (cấp thật) |
| VietMap tìm kiếm / đăng nhập | 0 thoát (Flutter một activity); *Đăng nhập Google* = 2 thoát liền (`SignInHubActivity` → GMS `SignInActivity`) — cả hai về ô (13 ms, 17 ms) |
| Gmail · Messages · Drive · Meet nguội | mỗi app 1 thoát (Meet 2) ở màn chào/đăng nhập — đều về ô, màn chào hiện trong ô |
| Waze chia sẻ qua Gmail | Waze mở `ComposeActivityGmailExternal` **`NEW_TASK`** ⇒ task Gmail riêng lên toàn màn display 0. Không phải task của ô ⇒ đúng luật không dời (như R7 ca c) |
| Bàn phím | Gboard ở display 0, nửa dưới màn (đè phần dưới ô + thanh nút); chữ gõ vào ô tìm Waze. Nguồn: `InputMethodManagerService.computeImeDisplayIdForTarget` (`:2218-2226`) — màn ảo không trang trí hệ / không chủ system ⇒ về màn mặc định |
| Khung hình 30 fps quanh một lần thoát (`screencap` thô, Waze "Drive there") | f40 (+24 ms): thanh trạng thái hệ hiện ở góc trên · f41–f46 (~0,5 s): ô đen + toast · f50 (~0,9 s): `TripOverview` vẽ xong trong ô. Không khung nào thấy Waze toàn màn display 0 |
| Sự kiện quanh một lần thoát | `am_pause_activity KachiHome` → `am_resume_activity KachiHome` (Kachi qua một vòng onPause/onResume); `am_relaunch_resume_activity` cho activity MỚI mở (đổi cấu hình 240→200 dpi), activity cũ của app không dựng lại |
| Waze sập 1 lần | `BadTokenException: Unable to add window … is your activity running?` lúc Waze tự thêm Compose `Popup` ~13 s sau một lần thoát, khi `TripOverviewActivity` tự đóng (21:49:56). [ĐOÁN] cửa sổ cha là của activity đã bị dựng lại/dừng trong lúc dời. Lặp 20 vòng sau đó không tái hiện ⇒ tần suất [CHƯA BIẾT], cần thêm số trên xe |

### 2.4 "Nó scale lại rồi" — owner thấy ô Waze co lại

[ĐO owner] owner xem trực tiếp: ô Waze "scale lại" sau một bước của phiên đo. [ĐO máy ảo, tái hiện 22:00] Bước gây ra: chạm (nhầm lúc đầu, cố ý lúc tái hiện)
vào quảng cáo *Minions & Monsters* của Waze ⇒ Waze mở trang Minions **cùng activity** (không `START`, không thoát, listener không làm gì) nhưng xin hướng **dọc**
(`AppWindowToken mOrientation=1`) ⇒ màn ảo không xoay được ⇒ hệ đóng hộp chữ cái task về `[728,0][1154,896]` (dải dọc giữa ô, hai bên đen). Đóng trang ⇒ task
về `[0,0][1882,896]`. **Không phải do `move-stack`** — là tính chất chung của mọi app trong màn ảo ô (có cả ở 2.93/2.97, không riêng hướng A); freeform của R7 thì
A10 bỏ qua yêu cầu hướng nên không thấy. Không cần sửa trong A; nếu muốn, việc riêng (`SLOT-PORTRAIT-LETTERBOX`) — [CHƯA BIẾT] Waze còn trang dọc nào khi lái.

### 2.5 §4 — bốn câu hỏi cho lệnh dời (bản sản phẩm)

1. **Display nào**: chỉ màn trong sự kiện (`requestedDisplayId`) **và** thuộc danh sách màn ảo ô Kachi báo cho daemon. Không quét.
2. **App nào**: gói gốc của task = gói đang hiện ở ô đó (allow-list do Kachi gửi). Task của gói khác (Gmail compose, hộp miễn pin của Settings) không chạm.
3. **Loại stack**: `standard`, `fullscreen`, đúng 1 task; màn đích `fullscreen` — tránh đường `AppWindowToken.shouldStartChangeTransition`
   (`AppWindowToken.java:1711-1721`: chỉ tạo snapshot/chuyển tiếp khi đổi vào/ra **freeform**) — đây đúng là chỗ NPE `TaskSnapshotController.createTaskSnapshot`
   của vụ treo 08-01 (`docs/archive/diagnostics/carplay-move-stack-npe-crash-2026-08-01.md`). Màn ảo ô là `FLAG_PRIVATE` ⇒ `DisplayWindowSettings.getWindowingModeLocked`
   (`:266-285`) trả `fullscreen` trừ khi `force_desktop_mode_on_external_displays` — 🚗 đọc trên xe. [SUY nguồn] vụ 08-01 (CarPlay → màn cụm xdja) rất có thể là
   đổi chế độ cửa sổ; chưa chứng minh ⇒ vẫn phải đo xe.
4. **Hoàn tác**: không ghi gì bền ngoài hệ (không `launch_params`, không cờ hệ) ⇒ lệnh hỏng thì task nằm ở display 0 như 2.93 (nhịp đo `APP_ELSEWHERE`, câu báo).
   Tiến trình chết ⇒ không còn ai dời, không có trạng thái treo.

## 3. Hướng B — chặn từ lúc mở

[ĐO] Waze nguội vào màn ảo bằng 6 biến thể (`--display` trống · `--windowingMode 1` · `--windowingMode 5` · `-f 0x18000000` (NEW_TASK|MULTIPLE_TASK) ·
`-f 0x10001000` (LAUNCH_ADJACENT) · `--activity-reorder-to-front`): **6/6 vẫn `Failed to put`**. [ĐO nguồn] khớp §0 — cổng xét `launchedFromUid` của lời
gọi do **app** phát; cờ của lời gọi Kachi không có mặt ở lời gọi đó. A10 `am` không có `--task`. Dời sẵn rồi chờ app ổn (B′) chỉ là A ở lượt đầu; mọi activity
mới về sau vẫn bị đá. ⇒ Không có cách chặn từ phía Kachi uid thường/shell trên A10.

## 4. Hướng D — màn ảo chủ `SYSTEM_UID` (chỉ nguồn)

- [ĐO nguồn] `:1098` bỏ qua cả nhánh `allowEmbedded` khi chủ màn là `SYSTEM_UID` ⇒ app tự mở activity vẫn ở lại. Khớp [ĐO xe 14/09] màn cụm
  `fission_bg_xdjaVirtualSurface` (chủ `com.xdja.containerservice` uid 1000) nhận app chiếu cụm.
- Đường lấy màn như vậy cho ô: `AutoContainer` opcode 35 = "create VirtualDisplay" (`kachi-capability-catalog-2026-09-10.md:565`) — tạo màn **cho cụm**, nội dung
  đi sang Qt/cụm; lời gọi chỉ mang `int`/`String` (`sendInfo`), **không có đường trao `Surface` của Kachi** ⇒ không đổ vào ô được [SUY từ AIDL đã RE].
  Kachi không ký nền tảng ⇒ không chạy uid 1000. Màn `overlay_display_devices` (chủ hệ, `TYPE_OVERLAY` không bị `:1098`) vẽ trong cửa sổ hệ nổi trên mọi thứ,
  không nhúng vào view của Kachi được, và là cờ toàn cục bền (§5) ⇒ không thử.
- ⇒ D không khả thi với Kachi hiện tại. 🚗 nếu muốn chốt hẳn: đọc AIDL `IAutoContainer` trên fw 2606 tìm hàm nhận `Surface` — [CHƯA BIẾT].

## 5. Hướng C (R7) — số đã có để so

Nhận 10–14 s (trước burst) → 2–3,5 s (burst R7 Pass 8); lớp che 9–10 cửa sổ; +2,6 lệnh shell/lượt mở; Waze phình 573 MB/11 lượt, 1,1 GB/32 lượt (LMK máy ảo giết
cả Kachi ở lượt 13); trên xe thanh trạng thái + điều hướng BYD hiện suốt khi có cửa sổ freeform, thanh tiêu đề freeform phải che; freeform nhớ theo app bền qua
reboot (phải có dấu + đường trả). Nguồn: `perf-298-vs-297-2026-10-09.md` §2.5/§4, `oncar-freeform-waze-2026-10-09.md` §2–§4, `emu-slot-escape-app-sweep-2026-10-09.md`.

## 6. Khuyến nghị

**Đi hướng A** (owner 09/10: *"hướng này khả quan hơn"*). Lý do: chữa đúng hệ quả của gốc rễ bằng chính tín hiệu gốc (`onActivityLaunchOnSecondaryDisplayFailed`),
app ở lại **trong màn ảo ô** như 2.93 (không freeform, không lớp che, không thanh hệ BYD, không trạng thái bền), chi phí ~0 khi không có gì xảy ra.

Hình dạng bản 2.98 (cần spec Design/Tasks trước khi code — CLAUDE.md §1):

1. Thêm bộ nghe `ITaskStackListener` vào **daemon uid 2000 sẵn có** (`InputDaemonMain`, không tiến trình mới); Kachi gửi qua kênh TCP đã có danh sách
   `(màn ảo, gói)` mỗi khi ô đổi. Daemon dời đúng như §2.5, báo lại Kachi (đếm thoát/phút, lỗi).
2. Bảng mã/tên hàm theo đời ROM vào `ClusterProfile` (A12/DL5: `moveRootTaskToDisplay`, `ActivityTaskSupervisor`) — chưa đo, không bật trên DL5 tới khi đo.
3. Ngắt an toàn: một task bị đá > N lần/phút hoặc lệnh dời ném lỗi ⇒ thôi dời gói đó tới hết lượt, đi đường 2.93 (`APP_ELSEWHERE` + câu báo).
4. Test `:core` cho luật chọn (đúng màn/gói/1 task/chế độ cửa sổ, ngắt an toàn) từ log thật ở §2.3.

**Phần R7 bỏ được khi A qua bước xe** (đề xuất, owner quyết): `core/…/escape/` `SlotEscapePlan` · `SlotEscapeRun` · `EscapeCoverPlan` · `EscapeFit` · `EscapeShade` ·
`EscapeMarker` · `TaskBinderCodes` (mã 59/89 chỉ dùng cho freeform) và `app/…/escape/` `SlotEscape` · `SlotEscapeHome` · `EscapeCoverOverlay` · `EscapeMarkerStore`;
`SlotLiveness.LAUNCH_BURST_MS` (burst dò chỉ để nhận nhanh — A theo sự kiện); việc Kachi tự gieo `enable_freeform_support` (`FreeformSeedPolicy`) nếu không còn ai
dùng freeform — kèm đường trả cờ + xoá `launch_params` các app R7 từng nhận trên máy anh em (§5). Giữ `KachiHomeSlotActions.onAppGone` nhánh 2.93 làm đường lùi.
Không xoá trước khi có bước 1 trên xe.

**🚗 Bước 1 trên xe trước khi nối dây (CLAUDE.md §14), xe ĐỖ:**

1. `dumpsys display | grep -E "kachi-slot|mDisplayId"` + `dumpsys window displays | grep -i windowingmode` + `settings get global force_desktop_mode_on_external_displays`
   — màn ảo ô phải `fullscreen`.
2. Mở Waze vào ô (2.97/2.98), lúc nó thoát: `am stack list` lấy stack của Waze (1 task) → `am display move-stack <stack> <vd>` **một lần**; đọc `logcat` tìm
   `NullPointerException … createTaskSnapshot` / treo. Thấy sập là dừng (memory 05/10 *thấy sập lần đầu là dừng*).
3. Qua bước 2: chạm/kéo/gõ trong ô, đếm lần thoát lại khi tìm đường; chụp màn ngay sau thoát xem toast của SystemUI BYD và thanh trạng thái BYD có lóe không [CHƯA BIẾT].
4. Đọc `pm list packages` / `dumpsys activity service` xem `onActivityLaunchOnSecondaryDisplayFailed` có bị BYD SystemUI dùng để làm gì khác không [CHƯA BIẾT].

## 7. Trạng thái máy ảo để lại

`enable_freeform_support` = 1 (đã trả); ô 1 = YouTube; dấu `kachi_slot_escape` không có; Kachi HOME trên display 0; mạng chạy (DNS 8.8.8.8, `http_proxy` null);
chế độ kiểm thử bật 21:38 (tự hết hạn sau 60 phút); nguyên mẫu `kx.EscWatch` đã dừng, tệp tạm trong `/data/local/tmp` đã xoá; VietMap đã cấp lại vị trí;
task Meet/VietMap còn đỗ trên màn ảo đỗ (ô 7) của Kachi như thường.
