# Vì sao Waze không vào được ô Kachi — và đường rơi về cửa sổ tự do trên màn chính

> **Trạng thái**: Current · **Ngày**: 2026-09-14 · **Mục đích**: Chốt **cơ chế thật** (source AOSP + đo trên máy ảo) của
> triệu chứng *"Waze nhảy toàn màn khi đặt vào ô"*, và trả lời có nên làm đường **rơi về freeform trên display 0** hay không.
> Nghiên cứu + thử nghiệm, **KHÔNG sửa mã sản phẩm**. Nguồn-sự-thật: source `android-10.0.0_r47` (+ `android-12.0.0_r34`
> cho DL5) và thiết bị `emulator-5554` (Android 10, Kachi **1.49** `versionCode=50`).

**⚠ Tài liệu này BÁC BỎ quy kết cũ ghi ở `kachi-emulator-app-hosting-2026-09-10.md` Finding 4, dòng backlog `D-emu`, và
`docs/specs/kachi-open-app-correctly.html` §4.5** — xem §5.

---

## 0. Kết luận một dòng

**Waze KHÔNG hề "từ chối màn phụ".** Waze mở **thành công** lên màn ảo của ô. Cái làm nó nhảy ra là **hoạt động thứ hai
do CHÍNH Waze mở** (`FreeMapAppActivity` → `MainActivity`, gọi từ uid của Waze) — lời gọi đó bị
`isCallerAllowedToLaunchOnDisplay` chặn vì màn ảo là **TYPE_VIRTUAL do uid khác làm chủ** và `MainActivity` không khai
`android:allowEmbedded` ⇒ task bị đẩy **cả cụm** về display 0 dạng toàn màn.

---

## 1. Gate chính xác trong AOSP (mức [ĐO] — đọc source, trích `file:line`)

### 1.1 Android 10 (`android-10.0.0_r47`)

| Nơi | Nội dung |
|---|---|
| `services/core/java/com/android/server/wm/ActivityStackSupervisor.java:349-367` | `canPlaceEntityOnDisplay()` — `displayId == DEFAULT_DISPLAY` ⇒ **`return true` ngay, KHÔNG kiểm gì** (dòng 351-354). Sau đó `mSupportsMultiDisplay`, rồi `isCallerAllowedToLaunchOnDisplay`. |
| `ActivityStackSupervisor.java:1084-1091` | Nếu caller có **`INTERNAL_SYSTEM_WINDOW`** ⇒ `return true` — *"allow launch any on display"*. **Cửa thoát DUY NHẤT.** |
| `ActivityStackSupervisor.java:1096-1113` | **GATE CHÍNH**: `mDisplay.getType() == TYPE_VIRTUAL && displayOwnerUid != SYSTEM_UID && displayOwnerUid != aInfo.applicationInfo.uid` ⇒ nếu `(aInfo.flags & ActivityInfo.FLAG_ALLOW_EMBEDDED) == 0` thì **`return false`**. Nhánh này **KHÔNG có đường thoát bằng quyền** (quyền `ACTIVITY_EMBEDDING` chỉ gỡ được điều kiện thứ HAI, dòng 1107-1113). |
| `ActivityStackSupervisor.java:1115-1136` | Chỉ tới đây mới xét `isPrivate()` / chủ màn / `uidPresentOnDisplay`. ⇒ **cờ riêng-tư KHÔNG phải cái chặn Waze.** |
| `ActivityRecord.java:1422-1425` | `canBeLaunchedOnDisplay()` = gọi thẳng `canPlaceEntityOnDisplay(displayId, launchedFromPid, launchedFromUid, info)` — **uid của người MỞ**, không phải của launcher. |
| `RootActivityContainer.java:1790-1792` | `getValidLaunchStackOnDisplay()`: `!r.canBeLaunchedOnDisplay(displayId)` ⇒ `return null` ⇒ ActivityStarter đi tìm stack ở display khác ⇒ **rơi về display 0**. |
| `ActivityStackSupervisor.java:2425-2441` | `handleNonResizableTaskIfNeeded()` — khi `preferredDisplayId != actualDisplayId` in `Slog.w("Failed to put " + task + " on display " + preferredDisplayId)` + `notifyActivityLaunchOnSecondaryDisplayFailed` (⇒ **toast** mà phiên 09-10 nhìn thấy). |
| `core/java/android/content/pm/ActivityInfo.java:492` | `FLAG_ALLOW_EMBEDDED = 0x80000000` ← `android:allowEmbedded` trên `<activity>`. |

**Không hề có gate theo `resizeMode` / `supportsPictureInPicture` / "multiDisplay"** trên đường này. `ActivityInfo` Android 10
**không có** thuộc tính `supportsMultiDisplay` nào cả — khái niệm đó là suy diễn, không có trong source.

### 1.2 Cờ VirtualDisplay — có cờ nào cứu được app sideload không? **KHÔNG.**

| Cờ | Sự thật (source) |
|---|---|
| `VIRTUAL_DISPLAY_FLAG_PUBLIC` (`DisplayManager.java:132`) | **Không cần quyền** khi đi kèm `OWN_CONTENT_ONLY`: `DisplayManagerService.java:1956-1966` bật ngầm `AUTO_MIRROR`, nhưng `:1965-1967` `OWN_CONTENT_ONLY` **gỡ lại** `AUTO_MIRROR`; phép kiểm `CAPTURE_VIDEO_OUTPUT` ở `:1979-1987` treo vào **`AUTO_MIRROR`**, không phải `PUBLIC` ⇒ app thường tạo được màn ảo **công khai**. (`android.app.ActivityView:497-501` dùng đúng bộ `PUBLIC\|OWN_CONTENT_ONLY\|DESTROY_CONTENT_ON_REMOVAL`.) |
| `VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY` | Chỉ chống "gương đệ quy". Không liên quan gate. |
| `VIRTUAL_DISPLAY_FLAG_SHOULD_SHOW_SYSTEM_DECORATIONS` | `DisplayManagerService.java:2000-2006` — cần `INTERNAL_SYSTEM_WINDOW`. Sideload không có. |
| `VIRTUAL_DISPLAY_FLAG_TRUSTED` | **Không tồn tại ở Android 10.** |

⇒ **Công khai hay riêng tư đều bị chặn như nhau**, vì gate dòng 1096 xét **`getType()==TYPE_VIRTUAL` + uid chủ màn**, không
xét cờ. Cách duy nhất né gate là **chủ màn ảo == `SYSTEM_UID`** (launcher chạy uid system) hoặc **caller có
`INTERNAL_SYSTEM_WINDOW`**.

### 1.3 Android 12 (DL5) — `android-12.0.0_r34`

- `ActivityTaskSupervisor.java:1108-1123`: cùng logic, chỉ **đổi điều kiện** từ `TYPE_VIRTUAL + ownerUid` sang
  **`!display.isTrusted()`**. Phần `FLAG_ALLOW_EMBEDDED` **y nguyên**.
- `DisplayManagerService.java:2615-2620`: `VIRTUAL_DISPLAY_FLAG_TRUSTED` đòi **`ADD_TRUSTED_DISPLAY`** (signature) ⇒
  **app sideload không tạo được màn tin cậy** ⇒ DL5 chặn **giống hệt** Android 10.
- Ngoài ra `android.app.ActivityView` **bị gỡ ở Android 12** (`SlotAppHost.classAvailable()` đã lường trước).

---

## 2. Đo trên máy ảo (emulator-5554, Android 10, Kachi 1.49)

### 2.1 Phép đối chứng — màn phụ do HỆ THỐNG làm chủ

```
settings put global overlay_display_devices "1280x720/240"      # Display 1: type OVERLAY, KHÔNG FLAG_PRIVATE
am start --display 1 -n com.waze/.FreeMapAppActivity
am start --display 1 -n com.google.android.deskclock/com.android.deskclock.DeskClock
```

| App | Kết quả |
|---|---|
| **Waze** | `Stack id=8 … displayId=1`, `taskId=787 com.waze/.FreeMapAppActivity bounds=[0,0][1280,720] visible=true` ⇒ **CHẠY TỐT** |
| Clock | `Stack id=9 … displayId=1` ⇒ chạy tốt |

⇒ **[ĐO] Waze KHÔNG từ chối màn phụ.** (Đã `settings delete global overlay_display_devices` — §5 CLAUDE.md.)

### 2.2 Manifest thật (aapt2 trên APK lấy từ máy)

| App | `resizeableActivity` | `allowEmbedded` |
|---|---|---|
| **com.waze 5.23.0.2** | **`true`** (mức `<application>`) | **0 chỗ khai** |
| com.google.android.deskclock 6.1.1 | `false` ở **3 activity** | **0 chỗ khai** |

⇒ **[ĐO] Quy kết "Waze không hỗ trợ đa màn, Clock thì có" là NGƯỢC**: Waze mới là cái khai `resizeableActivity=true`;
Clock có activity khai `false`. Cả hai **đều không** có `allowEmbedded`.

### 2.3 Màn ảo THẬT của ô Kachi

Đặt Waze vào ô rồi khởi động lại Kachi ⇒ `dumpsys display`:

```
DisplayDeviceInfo{"kachi-slot-1789385222597":
  uniqueId="virtual:com.byd.launcher,10150,kachi-slot-1789385222597,0",
  1872 x 762, density 200, type VIRTUAL, owner com.byd.launcher (uid 10150),
  FLAG_PRIVATE, FLAG_NEVER_BLANK, FLAG_OWN_CONTENT_ONLY}
```

(khớp `VdAppHost.kt:65` — cờ `8 or 256` = `OWN_CONTENT_ONLY | DESTROY_CONTENT_ON_REMOVAL`, không có `PUBLIC` ⇒ riêng tư.)

**Nhật ký một lần mở Waze vào ô (display 3), nguyên văn:**

```
18:28:15.837 ActivityTaskManager: START u0 {... cmp=com.waze/.FreeMapAppActivity} from uid 2000
18:28:15.860 InputDispatcher:  Display #3 has focused window: 'Window{… com.waze/com.waze.FreeMapAppActivity}'
18:28:16.178 ActivityTaskManager: Displayed com.waze/.FreeMapAppActivity: +336ms          ← ✅ LÊN ĐƯỢC màn ảo
18:28:18.195 ActivityTaskManager: START u0 {cmp=com.waze/.MainActivity (has extras)} from uid 10144   ← Waze TỰ mở
18:28:18.198 ActivityTaskManager: Failed to put TaskRecord{… #793 A=com.waze … StackId=15} on display 3
18:28:18.230 InputDispatcher: Found window … in display 3, but it should belong to display 0
```

Sau đó: `taskId=793 com.waze/.FreeMapAppActivity bounds=[0,0][1920,1080]` trên `displayId=0` — **toàn màn**.

**⇒ Cơ chế đã chốt [ĐO]:**

| Bước | Người gọi | uid | Gate | Kết quả |
|---|---|---|---|---|
| 1. `am start --display <vd> -n com.waze/.FreeMapAppActivity` | shell (dadb) | **2000** | `INTERNAL_SYSTEM_WINDOW` = **granted** ⇒ `ASS.java:1088` `return true` | ✅ lên màn ảo, render thật (ảnh: splash Waze trong ô) |
| 2. `FreeMapAppActivity` → `startActivity(MainActivity)` | **Waze** | 10144 | `TYPE_VIRTUAL` + chủ màn 10150 ≠ SYSTEM ≠ 10144, `MainActivity` không `allowEmbedded` ⇒ `ASS.java:1101-1106` `return false` | ❌ `getValidLaunchStackOnDisplay` = null ⇒ **cả task rơi về display 0 toàn màn** |

Xác nhận quyền của shell (`dumpsys package com.android.shell`):
`INTERNAL_SYSTEM_WINDOW: granted=true` · `ACTIVITY_EMBEDDING: granted=true` · `MANAGE_ACTIVITY_STACKS: granted=true`.

**Trả lời (b) — vì sao Clock "qua được":** Clock **không có activity trung chuyển**. Chỉ có **một** lời gọi mở, và lời gọi
đó đến từ **shell uid 2000 (đặc quyền)** ⇒ không lời gọi nào xuất phát từ uid của Clock nên gate không bao giờ nổ.

Đo thêm trên đúng màn ảo riêng tư đó (display 3):

| App | Kết quả |
|---|---|
| `com.google.android.deskclock/…DeskClock` | `taskId=794 bounds=[0,0][1872,762] displayId=3 visible=true` ⇒ **Ở LẠI** |
| `com.google.android.apps.maps/…MapsActivity` (10.16.7) | `taskId=781 bounds=[0,0][1872,762] displayId=3 visible=true` ⇒ **Ở LẠI** |
| `com.waze/.FreeMapAppActivity` | lên rồi **bị đẩy ra** (bảng trên) |

⇒ **Google Maps cũng KHÔNG từ chối màn phụ** — quy kết cũ gộp Maze/Maps vào một nhóm là sai.

Thử mở thẳng `com.waze/.MainActivity` bằng shell: **không được vì lý do khác** —
`SecurityException: … not exported from uid 10144` (`ActivityStackSupervisor.java:1043 checkStartAnyActivityPermission`).
⇒ **không có cách nào "mở tắt qua activity trung chuyển" bằng shell.**

### 2.4 Freeform trên màn chính (display 0)

```
am start -a android.intent.action.MAIN -c android.intent.category.LAUNCHER \
   --display 0 --windowingMode 5 -n com.waze/.FreeMapAppActivity
am task resize 795 100 200 900 800
```

| Bước | Kết quả [ĐO] |
|---|---|
| `am start --windowingMode 5` | `Stack id=18 … displayId=0 … mWindowingMode=freeform`, `taskId=795 com.waze/… visible=true` |
| `am task resize` | `taskId=795 … bounds=[100,200][900,800]` — **đúng từng pixel**, không lỗi, không bị từ chối |
| Ảnh chụp | Waze **vẽ bản đồ sống** trong cửa sổ 800×600 tại (100,200), có **thanh caption freeform** (~36px) ở trên |
| Sau khi Waze tự mở `MainActivity` | **KHÔNG bị đẩy đi** — vì `canPlaceEntityOnDisplay` `return true` ngay ở `ASS.java:351-354` cho `DEFAULT_DISPLAY` |

⇒ **[ĐO] Đường rơi về freeform trên display 0 CHẠY ĐƯỢC với Waze trên máy ảo.**
(Máy ảo đang bật `enable_freeform_support=1`, `force_resizable_activities=1`.)

### 2.5 Hai phát hiện phụ (ngoài phạm vi câu hỏi, nên ghi lại)

- **[ĐO] Rò màn ảo**: sau vài lần Kachi khởi động lại với 2 ô App, `dumpsys display` còn **4** thiết bị `kachi-slot-*`
  (logical display 8, 9, 10, 11) trong khi chỉ có **2** ô. Nghi `VdAppHost` cũ không được `release()` khi ô được dựng lại
  (mức [NGHI] — chưa lần tới call site).
- **[ĐO] Khung đóng băng**: khi app trên màn ảo chết (`am force-stop`), `SurfaceView` của ô **giữ nguyên khung hình cuối**
  (ảnh: ô vẫn hiện "Update Google Maps to continue" dù Maps đã bị dừng). Người dùng thấy ô "còn sống" mà thật ra đã chết.

---

## 3. Vì sao đường freeform hiện KHÔNG bao giờ chạy trên xe

`KachiHomeActivity.kt:159`
```kotlin
private val embedding get() = shell != null || SlotAppHost.embeddingUsable(this)
```
`LauncherWindows.kt:105/143/159` — `reflow()`, `placeApp()`, `closeApp()` đều mở đầu bằng `if (embedding()) return`.

⇒ **Hễ dò ra kênh dadb (tức LUÔN LUÔN trên xe) thì `embedding()` = true ⇒ toàn bộ lệnh freeform bị bỏ qua**, chỉ còn
đường màn-ảo. Điều này đã được ghi ở `docs/specs/kachi-open-app-correctly.html` dòng 88 (P1). Nghĩa là **hôm nay Kachi
không có đường lùi nào** khi màn ảo thất bại — đó chính là hình dạng owner thấy.

---

## 4. Đề xuất — "rơi về cửa sổ tự do theo ĐO" (generic, không cứng tên app)

### 4.1 Nguyên tắc (CLAUDE.md §7 — đo, không hardcode gói)

Không có cách nào **đọc trước** để biết app có trung chuyển hay không (`allowEmbedded` của activity **đích** mới là cái
quyết định, mà activity đích chỉ lộ ra lúc chạy). ⇒ **Phải đo kết quả, không đoán trước.**

**Phép đo đề xuất (thuần dữ liệu, không UI):** sau khi `am start --display <vd>`, **thăm `am stack list` theo nhịp**
(vd 6 nhịp × 400 ms ≈ 2,4 s — Waze bật ra ở ~2,4 s sau START trong đo ở §2.3) và hỏi đúng một câu:

> taskId của gói này **có còn nằm trong stack thuộc `displayId == <vd>`** không?

`FreeformLaunch.parseTaskIdOnDisplay(stackList, pkg, vd)` **đã tồn tại** và làm đúng việc này — không cần parser mới.

- Còn trên VD sau cửa sổ quan sát ⇒ **giữ đường nhúng** (Clock, Maps, VietMap…).
- Rơi mất (hoặc xuất hiện trên `displayId==0`) ⇒ **đã đo được thất bại** ⇒ chuyển ô đó sang **chế độ freeform**.

Tín hiệu phụ, rẻ, có thể dùng để rút ngắn chờ: `logcat -d -s ActivityTaskManager` có dòng
`Failed to put TaskRecord{… A=<pkg> …} on display <vd>` — nhưng **không nên làm điều kiện duy nhất** (phụ thuộc chuỗi log
của ROM; CLAUDE.md §3).

### 4.2 Việc phải làm (nếu owner duyệt)

| # | Việc | Ghi chú |
|---|---|---|
| 1 | Đổi `embedding()` từ **cờ toàn cục** thành **quyết định theo TỪNG Ô**, có 3 trạng thái: `ĐANG THỬ NHÚNG` → `NHÚNG OK` / `PHẢI FREEFORM` | Đây là thay đổi kiến trúc thật, không phải vá 2 dòng — §4.4 |
| 2 | Ô ở trạng thái `PHẢI FREEFORM`: nhả `VdAppHost` (giải phóng màn ảo), gọi `LauncherWindows.placeApp` đường cũ | `ShellAppLauncher.openInSlot` + `appRect()` đã trừ sẵn `CAPTION_INSET` cho thanh caption |
| 3 | `OverlayHeads` (nút ⇄ nổi) phải bật lại **cho riêng ô đó** | Hiện `overlayUpdate` tắt toàn bộ khi `embedding()` (`LauncherWindows.kt:79`) |
| 4 | Nhớ kết quả đo theo gói (bền) để lần mở sau **đi thẳng freeform**, không phải chờ 2,4 s nữa | Kèm đường xoá khi app cập nhật (`MY_PACKAGE_REPLACED` đã có receiver) |
| 5 | Test hồi quy khoá bài học | Xem §4.5 |

### 4.3 Rủi ro trên xe — **cái đắt nhất, và là lý do đề xuất CHƯA nên ship ngay**

| Rủi ro | Mức bằng chứng |
|---|---|
| **DL5 (Android 12) đã cắt bỏ đúng các lệnh này** | **[ĐO]** `dashcast-src/CHANGELOG.md` v1.2.72-beta: `cmd activity set-task-windowing-mode` **stripped** · `cmd activity task resize` **silent no-op** (exit 0, `mBounds` không đổi) · `set-display-windowing-mode` stripped · `--activity-launch-bounds` unknown option ⇒ **đường freeform+resize của §2.4 nhiều khả năng CHẾT trên DL5** |
| DL3 có đường resize khác (`IActivityTaskManager.resizeStack(stackId, Rect)` — mức stack, không phải task) | [ĐO] cùng nguồn. ⇒ khác biệt đời xe phải nằm trong `ClusterProfile` (CLAUDE.md §7), không rải trong code |
| Freeform trên **display 0** của ROM DiLink chưa từng được đo | **[CHƯA BIẾT]** — `docs/diagnostics/oncar-playbook-kachi-1.47.md` U8(a) vẫn treo đúng câu hỏi này (ROM có hiện lại thanh trạng thái Android khi ô freeform có focus không). Công thức `--windowingMode 5` đã proven trên xe **cho màn ảo cụm** (`VEHICLE-TEST-V2.md` bậc thang D10), **không phải** cho display 0 |
| Trên xe Kachi có thể được ký nền tảng | **[SUY]** Nếu Kachi chạy **uid system** (`sharedUserId=android.uid.system` + ký platform) thì chủ màn ảo = `SYSTEM_UID` ⇒ nhánh `ASS.java:1096` **bị bỏ qua hoàn toàn**, rồi `uidPresentOnDisplay` (Waze đã có activity trên màn đó) ⇒ `ASS.java:1131` **`return true`** ⇒ **Waze vào ô được, không cần freeform**. Nếu chỉ ký platform mà **vẫn uid riêng** thì `INTERNAL_SYSTEM_WINDOW` chỉ cứu lời gọi CỦA KACHI, **không** cứu lời gọi của Waze ⇒ vẫn hỏng |

### 4.4 Chi phí

Không phải "thêm một nhánh lùi". `embedding()` hiện là **một cờ boolean toàn màn** được 5 chỗ đọc; biến nó thành trạng
thái **theo ô** đụng `WorkspaceView.makeSlot` · `LauncherWindows` (4 hàm) · `WorkspaceRenderPlanner` · `VdAppHost` vòng
đời · `OverlayHeads`. Đúng vùng vừa sửa P-bug1/P-bug2/P9-bước-3 ⇒ **bắt buộc có spec + duyệt trước khi code**
(CLAUDE.md §1, và bài học P9 "6 chỗ tự suy ra số ô").

### 4.5 Test hồi quy cần có (CLAUDE.md §10 — dựng từ dump THẬT trong tài liệu này)

1. `FreeformLaunchTest`: fixture `am stack list` **nguyên văn** lúc Waze còn trên VD và lúc đã rơi về display 0 ⇒
   `parseTaskIdOnDisplay(…, vd)` trả `789` rồi trả `null`.
2. Test thuần `:core` cho luật quyết định: *"còn trên VD sau N nhịp ⇒ giữ nhúng; mất ⇒ freeform"*, kèm ca **app khởi động
   chậm** (không được kết luận thất bại quá sớm) và ca **app chết hẳn** (không được nhảy freeform vô tận).
3. Test khoá: ô ở trạng thái `PHẢI FREEFORM` thì `OverlayHeads` **có** nút ⇄ cho đúng ô đó; ô nhúng thì không.
4. Test khoá `VdAppHost.release()` được gọi khi ô đổi trạng thái (khoá lỗi rò màn ảo ở §2.5).

---

## 5. Sửa sai các quy kết cũ (CLAUDE.md §2 — tách cơ chế và quy kết)

| Chỗ ghi | Câu cũ | Sự thật [ĐO] |
|---|---|---|
| `kachi-emulator-app-hosting-2026-09-10.md` Finding 4 | *"app không khai báo hỗ trợ đa-màn + VirtualDisplay là PRIVATE ⇒ launch bị từ chối"* | Cả hai vế **sai**. Waze khai `resizeableActivity=true`; cờ PRIVATE không nằm trên đường quyết định (`ASS.java:1096` xét **type + uid chủ**, `isPrivate()` mãi tới `:1115`). Cái chặn là **`FLAG_ALLOW_EMBEDDED` vắng mặt trên activity ĐÍCH, khi lời gọi đến từ uid CỦA APP** |
| `kachi-emulator-app-hosting-2026-09-10.md` Finding 4 | *"Nhóm từ chối: Waze, Google Maps"* | **Google Maps ở lại trên màn ảo bình thường** (§2.3). Chỉ Waze hỏng, và vì activity trung chuyển |
| backlog `D-emu` | *"Waze/Maps báo does not support launch on secondary displays"* | Chuỗi thật trong log là `Failed to put TaskRecord{…} on display N` (`ASS.java:2436`); toast do `notifyActivityLaunchOnSecondaryDisplayFailed`. Là **hệ quả**, không phải nguyên nhân |
| `kachi-open-app-correctly.html` §4.5 | *"App chỉ được mở lên màn công khai hoặc màn nó đã có activity; màn riêng tư ⇒ ném lỗi. ⇒ Xác nhận độc lập giới hạn D-emu"* | Mô tả tài liệu đó đúng cho **API `setLaunchDisplayId`**, nhưng **không phải** cái chặn ở đây, và nó đã bị dùng để "xác nhận" một quy kết sai. Màn **công khai** của `ActivityView` (`ActivityView.java:497-501` `PUBLIC\|OWN_CONTENT_ONLY`) **cũng bị chặn y hệt** |

---

## 6. Trả lời gọn ba câu hỏi của owner

**(a) Gate nào chặn?** `ActivityStackSupervisor.isCallerAllowedToLaunchOnDisplay` — `ActivityStackSupervisor.java:1096-1106`
(A12: `ActivityTaskSupervisor.java:1108-1116`). Theo **cờ display + uid chủ display + `FLAG_ALLOW_EMBEDDED` của activity
đích**, **KHÔNG** theo `resizeMode`/PIP/"multiDisplay". Và nó chỉ nổ với lời gọi từ **uid của app**, không nổ với lời gọi
từ **shell uid 2000** (shell có `INTERNAL_SYSTEM_WINDOW`).

**(b) Clock qua được nhờ gì?** Không phải nhờ "hỗ trợ đa màn" (Clock còn có activity `resizeableActivity=false`). Nhờ
**không có activity trung chuyển** ⇒ chỉ có đúng một lời gọi mở, và nó đến từ shell đặc quyền.

**(c) Có cờ VirtualDisplay nào cứu được không?** **Không.** `PUBLIC` không cần quyền (khi kèm `OWN_CONTENT_ONLY`) nhưng
không đổi kết quả; `TRUSTED` (A11+) cần `ADD_TRUSTED_DISPLAY` (signature). Chỉ có hai đường: **caller mang
`INTERNAL_SYSTEM_WINDOW`** (không giúp được vì caller hỏng là chính Waze), hoặc **màn ảo do `SYSTEM_UID` làm chủ**
(= Kachi chạy uid system trên ROM ký nền tảng).

**Có nên làm đường rơi về freeform?** **Chưa. Đo trên xe trước.** Trên máy ảo nó chạy ngon (§2.4), nhưng
[ĐO] DashCast cho thấy **DL5 đã cắt bỏ đúng các lệnh đó**, và freeform trên **display 0** của ROM DiLink **chưa từng được
đo** (U8a còn treo). Làm code trước khi có phép đo bước 1 là vi phạm CLAUDE.md §14.

---

## 7. Cần đo trên xe (đưa vào danh sách grab kế tiếp) 🚗

Chạy qua `ClusterDiag`/dadb loopback, lưu output vào `docs/diagnostics/`:

```bash
settings get global enable_freeform_support
settings get global force_resizable_activities
dumpsys package com.android.shell | grep -E 'INTERNAL_SYSTEM_WINDOW|ACTIVITY_EMBEDDING'
dumpsys package com.byd.launcher   | grep -E 'userId=|INTERNAL_SYSTEM_WINDOW'   # Kachi có uid system không?
am start -a android.intent.action.MAIN -c android.intent.category.LAUNCHER \
   --display 0 --windowingMode 5 -n <pkg>/<activity>
am stack list                       # mWindowingMode có = freeform không?
am task resize <taskId> 100 200 900 800 ; echo "exit=$?"
am stack list                       # bounds có ĐỔI THẬT không (DL5 nghi exit 0 mà vô tác dụng)
# + chụp màn: ROM có hiện lại thanh trạng thái Android khi ô freeform có focus không (U8a)
```

---

## 8. Trạng thái máy ảo sau phiên (minh bạch — §5 CLAUDE.md)

- **Đã trả lại**: `settings delete global overlay_display_devices` (⇒ `null`), mọi app thử đã `am force-stop`, Kachi ở
  tiền cảnh.
- **Còn đổi**: bố cục màn chính của hồ sơ «trip» trên emulator — trong lúc dựng phép đo, **ô 1 được gán `com.waze`** và
  bố cục bị đổi qua **1 ô** rồi tôi đặt lại **4 ô**. Bố cục cũ có thể là bố cục **tự vẽ** ⇒ **owner cần chọn lại bố cục +
  nội dung ô 1** (Kachi hiện **không có nút ✕ gỡ nội dung ô** — owner đã bỏ ✕ ngày 09-12/13, nên không gỡ lại được bằng UI).
  Trên **xe không ảnh hưởng gì**.

## Nguồn

- AOSP `android-10.0.0_r47`: `ActivityStackSupervisor.java`, `ActivityRecord.java`, `ActivityStarter.java`,
  `RootActivityContainer.java`, `ActivityInfo.java`, `ActivityView.java`, `DisplayManager.java`,
  `DisplayManagerService.java`.
- AOSP `android-12.0.0_r34`: `ActivityTaskSupervisor.java`, `DisplayManager.java`, `DisplayManagerService.java`.
- Thiết bị: `emulator-5554` (Android 10, `ro.product.model=Android SDK built for arm64`), Kachi `1.49` (`versionCode=50`).
- `../dashcast-src/CHANGELOG.md` v1.2.72-beta (bảng lệnh bị cắt trên DL5).
- Mã Kachi: `VdAppHost.kt:65`, `SlotAppHost.kt:112-121`, `WorkspaceView.kt:293-303`, `LauncherWindows.kt:79/105/143`,
  `KachiHomeActivity.kt:159`, `FreeformLaunch.kt:75-87`, `ShellAppLauncher.kt:18-35`.
