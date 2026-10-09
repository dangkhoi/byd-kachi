# Waze "trong ô" bằng freeform — đo trên xe + máy ảo (2026-10-09)

> **Trạng thái**: Evidence · **Cập nhật**: 2026-10-09 · **Chủ**: dangkhoi · **Liên quan**: backlog `SLOT-ESCAPE-POLICY` · `H1` ·
> `diagnostics/waze-into-slot-research-2026-09-14.md` · spec 2.98 (sẽ thêm mục). Bước 1 theo CLAUDE.md §14 (lệnh thô trước, mã sau).
> Ảnh chụp trên xe có bản đồ + vị trí ⇒ **không lưu vào repo**.

## 0. Bối cảnh

Owner 09/10: *"có bật được freeform thì làm sao vẫn nhét waze vào ô"* · *"freeform không chết, trên xe đo cũng bảo OK rồi, nên ko nói mò"*.
Kết luận cũ ở backlog (27/09, "freeform CHẾT trên xe này") là **sai** — dựa trên tài liệu, không đo lại; nay đính chính bằng số đo bên dưới.

## 1. Sự thật nền

- [ĐO nguồn AOSP android10-release `ActivityTaskManagerService.java:721-770`] `mSupportsFreeformWindowManagement` = ROM khai
  `android.software.freeform_window_management` **HOẶC** `Settings.Global.enable_freeform_support != 0`; chỉ đọc trong `systemReady`
  (`ActivityManagerService.java:9023`) ⇒ cờ bật lúc đầu xe đang chạy chỉ có hiệu lực sau khi đầu xe khởi động lại thật.
- [ĐO dexdump `services.jar` fw 2606] BYD **không đổi** cổng này (cùng chuỗi `freeform_window_management` + `enable_freeform_support` +
  `force_resizable_activities` trong `retrieveSettings`).
- [ĐO xe 09/10] Kachi 2.97, fw `eng.build.20260610`, `enable_freeform_support=1`, `force_resizable_activities=1`,
  `pm has-feature …freeform_window_management` = false, uptime 9,3 h ⇒ freeform đang có hiệu lực (nhờ cờ, Kachi tự ghi mỗi lần mở).

## 2. Đo trên xe (Seal, DiLink3, fw 2606, bố cục 1 ô — khung ô trên display 0 = `[19,89][1901,985]`)

| # | Thử | Kết quả |
|---|---|---|
| 1 | `am start --windowingMode 5 -n com.waze/.FreeMapAppActivity` | ✅ task `mWindowingMode=freeform`; activity thứ hai `MainActivity` vẫn trong task freeform (không nhảy toàn màn) |
| 2 | `am task resize <task> 19 89 1901 985` | ✅ `mFrame=[19,89][1901,985]`, trùng ô |
| 3 | Lớp | ✅ cửa sổ Waze (#10) TRÊN `KachiHome` (#11) |
| 4 | Mã binder `activity_task` 59 (`getTaskBounds`, chỉ đọc) | ✅ trả đúng rect ⇒ bảng mã khớp AOSP `android-10.0.0_r47` |
| 5 | Waze toàn màn ⇒ `service call activity_task 89 i32 <task> i32 5 i32 1` (`setTaskWindowingMode` FREEFORM) + `am task resize` | ✅ vào đúng khung ô, **cùng pid** (không dựng lại app) |
| 6 | Bấm HOME | Waze bị đẩy ra sau Kachi; `am start --display 0 -n <comp>` kéo lại **đúng khung, cùng pid** |
| 7 | Thanh tiêu đề freeform (□ ✕) | ⚠️ có (owner thấy) — app uid thường không tắt được; hướng: lớp che của Kachi |
| 8 | Thanh trạng thái + thanh điều hướng BYD (`StatusBar` 0..84, `NavigationBar0` 990..1080, `BydQSBar`) | ❌ hiện suốt khi có cửa sổ freeform, kể cả `mCurrentFocus=null` sau khi chạm Kachi; khác máy ảo (chỉ hiện khi freeform có tiêu điểm) |
| 9 | `settings put global policy_control immersive.full=com.waze` | ❌ không ẩn được thanh BYD; đã trả `policy_control` về `null` |
| 10 | Dọn | Waze trả về toàn màn bằng mã 89 mode 1 (xoá chế độ freeform Android nhớ theo app), HOME; `policy_control=null` |

Ghi thêm: lúc đo thấy **hai task `KachiHome`** trên display 0 (task 4 và 12) — đúng tình huống R3 của 2.98 (hai màn chính cùng sống) xảy ra trên xe.

## 3. Đo trên máy ảo (A10, kachi_play) — bổ sung

- Freeform được Android **nhớ theo component** (`/data/system_ce/0/launch_params`, `TaskLaunchParamsModifier`), sống qua khởi động
  lại; chỉ xoá khi task hiện toàn màn một lần (mã 89 mode 1) ⇒ Kachi phải ghi dấu bền TRƯỚC khi đổi và trả lại khi thôi quản (CLAUDE.md §5).
- `am stack remove` / `removeTask` **giết tiến trình** app ⇒ "đóng" phải dùng mã 89 mode 1, không dùng remove.
- Thanh tiêu đề 42dp (64 px @240dpi) với nút phóng to / đóng; không đẩy được lên trên ô (khung bị kẹp dưới thanh trạng thái).
  Pass 8 (09/10 14:45) [ĐO]: không phải "kẹp" mà DỜI — `mStable=[0,36][1920,1080]`; `am task resize 366 19 25 1901 985` ⇒ hệ đặt
  `[19,36][1901,996]` (giữ cỡ, dời 11) — khớp AOSP Q `TaskRecord.resolveOverrideConfiguration` (`TaskRecord.java:2222-2246`; jadx fw 2606
  giống hệt). Đặt `[19,36][1901,985]` được ⇒ thanh tiêu đề 36..100, nội dung từ 100. Xe: `status_bar_height_portrait` 56dp (framework-res
  fw 2606) = 84 px ⇒ [SUY] đỉnh ổn định 84 — 🚗 chốt bằng `dumpsys window displays | grep mStable`. Spec 2.98 §4 R7 Pass 8.
- Kích thước tối thiểu 220dp.
- [CHƯA BIẾT] Khi Waze đã được nhớ freeform đúng khung ô, lúc Kachi mở Waze vào ô và Waze tự thoát, nó có rơi thẳng xuống dạng
  freeform đúng khung không — cần đo trên xe (owner đặt Waze vào ô qua Kachi).

### 3b. Máy ảo 09/10 — đo khi làm R7 (bản vehicleTest 2.98 (201), Waze thật)

- [ĐO] Mã 59 trả `Result: Parcel(0 1 l t r b)`; task không tồn tại ⇒ khung rỗng `0 0 0 0`. Mã 89 trả `void` (`Parcel(00000000)`).
- [ĐO] Stack freeform giữ khung `[0,0][1920,1080]` ở dòng `Stack id=…`; khung THẬT nằm trên dòng `taskId=…: … bounds=[19,89][1901,985]`.
- [ĐO] Trả: Home trước rồi mã 89 mode 1 **toTop 0** ⇒ Waze toàn màn DƯỚI Kachi, cùng pid (mode 1 khi app đang nổi trên ⇒ app toàn màn che màn nhà).
- [ĐO] `dumpsys input`: cửa sổ freeform khung `[19,89][1901,985]` có `touchableRegion=[0,44][1920,1030]` (viền đổi cỡ 30 dp). Một `input tap`
  ở `y=44` (thanh trên Kachi) rơi vào viền ⇒ `TaskPositioner` kẹt, cửa sổ `TYPE_DRAG` giữ tiêu điểm, mọi chạm/phím bị chặn tới khi khởi động lại
  máy ảo. ⇒ R7 che luôn 4 dải viền (gương Kachi, chạm giao lại cho Kachi).
- [ĐO soát Pass 7, 09/10 13:25] Lệnh đưa lại lên `am start --display 0 -a MAIN -c LAUNCHER -n com.waze/.FreeMapAppActivity` lên task Waze
  (base intent `{flg=0x10000000 cmp=com.waze/.FreeMapAppActivity}` — KHÔNG có MAIN/LAUNCHER, vì task do app tự mở): `logcat -b events`
  = `am_task_to_front` → **`am_create_activity` FreeMapAppActivity MỚI** (flags 0x10400000) → Waze trampoline mở `MainActivity`
  (`launchMode=2` singleTask) → `am_finish_activity … clear-task-stack` → `am_new_intent MainActivity`; kết quả 1 Hist (`MainActivity`),
  cùng pid. Tức "không dựng activity mới" **chỉ đúng với Waze** nhờ trampoline + singleTask của chính Waze; theo AOSP Q
  `ActivityStarter.setTaskFromIntentActivity` (`realActivity` khớp nhưng `!task.isSameIntentFilter(start)` ⇒ `mAddingToTask = true`),
  app có root activity `standard` sẽ bị **chồng thêm một instance** mỗi lần HOME → đưa lại lên. [SUY] chưa có app thứ hai để đo.
- [ĐO soát Pass 7] Ứng viên không dựng instance: `am task focus <id>` (A10 `ActivityManagerShellCommand.runTaskFocus` →
  `setFocusedTask`): trên máy ảo task Waze toàn màn dưới màn nhà ⇒ `Setting focus to task 322`, `visible=true`, events chỉ có
  `am_resume_activity` (không create/new_intent). **Chưa đo trên xe** (CLAUDE.md §14) ⇒ R7 giữ `am start` (đã đo xe §2 mục 6); đổi
  lệnh chỉ sau một lượt xe có log events.
- [ĐO] `ActivityManager.moveTaskToFront(task Kachi)` KHÔNG ẩn app freeform (Waze vẫn `RESUMED`, vẽ đè lên bảng Cài đặt); Home (`am start … HOME`) thì ẩn.
- [ĐO] Waze đã từng freeform (Android nhớ) vẫn thoát ra TOÀN MÀN khi Kachi mở nó vào màn ảo ô (trả lời §3 [CHƯA BIẾT] — trên máy ảo).
- [ĐO] Waze tự `ShutdownManager … Aborting VM` khi nằm sau màn nhà vài phút lúc chưa cấp vị trí — hành vi của Waze, không do Kachi.

## 4. Vì sao thanh BYD lòi ra — đọc nguồn fw 2606 (09/10)

Đọc `services.jar`/`framework.jar` fw 2606 (jadx + `dexdump` đoạn jadx dịch sai) + SystemUI 2602 (bản 2606 chưa kéo về), so AOSP `android10-release`.

- [ĐO nguồn] `DisplayPolicy.updateSystemBarsLw`: `mForceShowSystemBars` = có stack **freeform** (`isStackVisible(5)`) hoặc split
  đang hiện trên màn ⇒ `clearClearableFlagsLw()` xoá `LOW_PROFILE|HIDE_NAVIGATION|FULLSCREEN` của MỌI cửa sổ, và
  `finishPostLayoutPolicyLw` ép `setBarShowingLw(true)`. Đây là mã **AOSP gốc** (AOSP Q `DisplayPolicy.java:2565, 3280-3287, 3376-3378`),
  BYD không thêm. Không cần tiêu điểm ⇒ khớp số đo mục 8 (`mCurrentFocus=null` vẫn hiện).
- [ĐO nguồn] Vì thế `policy_control immersive.full` vô hiệu (cờ thêm vào rồi bị xoá) — khớp mục 9.
- [ĐO nguồn SystemUI 2602] Phần BYD (`setPackageNameForStatusAndNaviationBar` → `updateStatusNaviBarBackground`) chỉ đổi **màu nền**
  thanh; danh sách gói đặc biệt nằm trong tài nguyên APK, không ghi được. `setStatusAndNavigationBarAlpha` là no-op. `BydQSBar` là panel
  luôn add sẵn, view `GONE` — không phải nguyên nhân.
- **Kết luận [ĐO nguồn]**: chừng nào còn stack freeform hiện trên display 0 thì **không cờ / setting / broadcast nào ẩn được hai thanh**.
  Chỉ còn: không dùng freeform, hoặc **che** thanh.
- Khác máy ảo [SUY, chưa chốt]: trên máy ảo chạm Kachi có lẽ đẩy stack home che hẳn stack freeform ⇒ `isStackVisible(5)=false`.
- Chạy Waze trong màn ảo của Kachi thay freeform: **không được** — đã đo 14/09 (`waze-into-slot-research-2026-09-14.md`): chính Waze
  `startActivity(MainActivity)` không `allowEmbedded` lên màn ảo chủ ≠ SYSTEM ⇒ `ActivityStackSupervisor.java:1096-1113` từ chối, không quyền nào gỡ.
- Che: `TYPE_ACCESSIBILITY_OVERLAY` (2032) lớp 30 nằm TRÊN thanh điều hướng (2019, lớp 23) và thanh trạng thái (2000, lớp 17)
  [ĐO nguồn `WindowManagerPolicy.getWindowLayerFromTypeLw`]. Kachi **đã có** `NavAccessibilityService` bật sẵn (gán phím cần nó) ⇒
  không phải đổi setting hệ thống nào. Lớp phủ chết theo tiến trình ⇒ không có state bền phải trả. `TYPE_APPLICATION_OVERLAY` (lớp 12) nằm dưới thanh ⇒ không che được.
- Hình học: thanh trạng thái 0..84, thanh điều hướng 990..1080; khung ô `[19,89][1901,985]` ⇒ hai thanh nằm **đúng ngoài ô**, che thanh
  không mất phần nào của Waze.

## 5. Đo màn tắt (R6-C) 09/10

Theo dõi 60 s (11:32:27→11:33:34) mỗi 2 s: `mWakefulness=Awake`, `Display Power: state=ON`, `mScreenState=ON` suốt — màn **không tắt**
trong cửa sổ đo ⇒ R6-C trên xe vẫn [CHƯA BIẾT]; đo lại khi tắt màn thật.

## 6. Còn chặn

- **Thanh BYD lòi ra** (mục 8) — không ẩn được (mục 4) ⇒ hướng: lớp phủ trợ năng che, cần đo trên xe bằng bản thử.
- Lớp che thanh tiêu đề: cùng lớp phủ trợ năng (lớp 30 trên cửa sổ app) — che mất ~42dp đầu ô của Waze.
- Mã binder theo đời ROM (DL5 = Android 12 khác) ⇒ đặt trong `ClusterProfile` + kiểm chỉ-đọc mã 59 trước khi dùng.
